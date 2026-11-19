// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtit.gui.vm;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketBuffer;

import com.cleanroommc.modularui.network.NetworkUtils;
import com.cleanroommc.modularui.value.sync.BooleanSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.StringSyncValue;
import com.cleanroommc.modularui.value.sync.SyncHandler;
import com.github.bsideup.jabel.Desugar;
import com.miaokatze.gtit.common.machine.v2.MTENekoVendingMachineV2;
import com.miaokatze.gtit.trade.NekoPageRegistry;
import com.miaokatze.gtit.trade.TeamDataProvider;
import com.miaokatze.gtit.trade.v2.NekoFavouritesTracker;
import com.miaokatze.gtit.trade.v2.NekoHistoryManager;
import com.miaokatze.gtit.trade.v2.NekoTradeCategory;
import com.miaokatze.gtit.trade.v2.NekoTradeDatabase;
import com.miaokatze.gtit.trade.v2.NekoTradeExecutor;
import com.miaokatze.gtit.trade.v2.NekoTradeGroup;
import com.miaokatze.gtit.trade.v2.NekoTradeHistory;
import com.miaokatze.gtit.trade.v2.NekoTradeResult;

/** Per-window trade status. A click sends one row; ordinary polling publishes changes to shared state. */
public final class StatusCodec {

    static final String ROWS_CHANNEL = "nekoV2TradeStatusRows";
    private static final int STATUS_REFRESH_TICKS = 20;
    private static final int MAX_BATCH_ROWS = 256;
    private static final int MAX_BATCH_BYTES = 30_000;
    private static final int MAX_FAILURE_LENGTH = 1024;

    @Desugar
    public record TradeKey(UUID groupId, int tradeIndex) {}

    /** Cooldown -1 denotes a completed one-time redemption. */
    @Desugar
    public record TradeStatus(boolean locked, long cooldown, boolean tradeable, int maxTrades, long usedTrades,
        String failure) {

        public TradeStatus {
            if (failure.length() > MAX_FAILURE_LENGTH) {
                failure = failure.substring(0, MAX_FAILURE_LENGTH - 16) + "…请查看交易需求清单";
            }
        }
    }

    @Desugar
    private record GroupStatus(boolean locked, long cooldown, int maxTrades, long usedTrades) {}

    private final MTENekoVendingMachineV2 machine;
    private final Runnable presentationChanged;
    private BooleanSyncValue teamWalletSync;
    private volatile boolean teamWallet;
    private StringSyncValue favouriteStatusSync;
    private volatile Set<String> favourites = Collections.emptySet();
    private TradeRowsSync rowsSync;
    private final Map<TradeKey, TradeStatus> publishedRows = new LinkedHashMap<>();

    // MUI2's packet callback can run off the client thread. Publish whole rows under one lock.
    private final Map<TradeKey, TradeStatus> clientRows = new HashMap<>();
    private final Map<TradeKey, TradeStatus> pendingRows = new HashMap<>();

    public StatusCodec(MTENekoVendingMachineV2 machine, Runnable presentationChanged) {
        this.machine = machine;
        this.presentationChanged = presentationChanged;
    }

    public void registerSyncValues(PanelSyncManager syncManager, UUID playerId) {
        teamWalletSync = new BooleanSyncValue(
            () -> syncManager.isClient() ? teamWallet
                : TeamDataProvider.getData(TeamDataProvider.getTeam(playerId)) != null,
            value -> {
                if (teamWallet == value) return;
                teamWallet = value;
                presentationChanged.run();
            });
        syncManager.syncValue("nekoV2TeamWallet", teamWalletSync);

        rowsSync = new TradeRowsSync(playerId);
        syncManager.syncValue(ROWS_CHANNEL, rowsSync);

        favouriteStatusSync = new StringSyncValue(
            () -> syncManager.isClient() ? "" : buildFavouriteStatusString(playerId),
            this::receiveFavourites);
        syncManager.syncValue("nekoV2FavouriteStatus", favouriteStatusSync);
    }

    public boolean isTeamWallet() {
        return teamWalletSync != null && teamWalletSync.getBoolValue();
    }

    public synchronized TradeStatus clientStatus(TradeKey key) {
        return clientRows.get(key);
    }

    /** Called during the GUI tick. A null value removes a row after a definition reload. */
    public synchronized Map<TradeKey, TradeStatus> takeClientChanges() {
        if (pendingRows.isEmpty()) return Collections.emptyMap();
        Map<TradeKey, TradeStatus> changes = new HashMap<>(pendingRows);
        pendingRows.clear();
        return changes;
    }

    private synchronized void receiveRows(Map<TradeKey, TradeStatus> changes) {
        for (Map.Entry<TradeKey, TradeStatus> change : changes.entrySet()) {
            TradeKey key = change.getKey();
            TradeStatus status = change.getValue();
            TradeStatus previous = status == null ? clientRows.remove(key) : clientRows.put(key, status);
            if (!java.util.Objects.equals(previous, status)) pendingRows.put(key, status);
        }
    }

    public boolean isFavourite(UUID groupId, int tradeIndex) {
        return favourites.contains(groupId + ":" + tradeIndex);
    }

    public void notifyFavouritesChanged() {
        if (favouriteStatusSync != null) favouriteStatusSync.detectAndSendChanges(false);
    }

    public void requestRegularRefresh() {
        if (rowsSync != null) rowsSync.ticks = STATUS_REFRESH_TICKS;
    }

    private String buildFavouriteStatusString(UUID playerId) {
        if (playerId == null) return "";
        return NekoFavouritesTracker.INSTANCE.getFavourites(playerId)
            .stream()
            .map(favourite -> favourite.getLeft() + ":" + favourite.getRight())
            .sorted()
            .collect(Collectors.joining(","));
    }

    private void receiveFavourites(String status) {
        Set<String> received = status == null || status.isEmpty() ? Collections.emptySet()
            : new java.util.HashSet<>(Arrays.asList(status.split(",")));
        if (received.equals(favourites)) return;
        favourites = received;
        presentationChanged.run();
    }

    /** Only the requested trade is checked here, even when another row shares its group or materials. */
    public void notifyTradeStatusChanged(UUID playerId, UUID groupId, int tradeIndex) {
        if (rowsSync == null || playerId == null) return;
        NekoTradeGroup group = NekoTradeDatabase.INSTANCE.getTradeGroup(groupId);
        if (group == null || tradeIndex < 0
            || tradeIndex >= group.getTrades()
                .size())
            return;
        TradeKey key = new TradeKey(groupId, tradeIndex);
        TradeStatus status = buildRow(
            playerId,
            key,
            groupStatus(playerId, group, NekoTradeExecutor.getTeamMaxTrades(playerId)));
        if (status.equals(publishedRows.get(key))) return;
        rowsSync.sendRows(Collections.singletonMap(key, status));
        publishedRows.put(key, status);
    }

    Map<TradeKey, TradeStatus> buildRows(UUID playerId) {
        Map<TradeKey, TradeStatus> rows = new LinkedHashMap<>();
        if (playerId == null) return rows;
        int teamMaxTrades = NekoTradeExecutor.getTeamMaxTrades(playerId);
        for (NekoTradeGroup group : NekoTradeDatabase.INSTANCE.getAllTradeGroups()
            .values()) {
            GroupStatus status = groupStatus(playerId, group, teamMaxTrades);
            for (int index = 0; index < group.getTrades()
                .size(); index++) {
                TradeKey key = new TradeKey(group.getId(), index);
                rows.put(key, buildRow(playerId, key, status));
            }
        }
        return rows;
    }

    private GroupStatus groupStatus(UUID playerId, NekoTradeGroup group, int teamMaxTrades) {
        boolean locked = !group.isConditionsSatisfied(playerId);
        int cooldown = group.getCooldown();
        if (cooldown == 0) return new GroupStatus(locked, 0, 1, 0);
        NekoTradeHistory history = NekoHistoryManager.INSTANCE.getHistory(playerId, group.getId());
        if (cooldown == -1) return new GroupStatus(locked, history.getTradeCount() > 0 ? -1 : 0, 1, 0);
        return new GroupStatus(
            locked,
            history.getCooldownRemaining(cooldown),
            teamMaxTrades,
            history.getCooldownTradeCount());
    }

    private TradeStatus buildRow(UUID playerId, TradeKey key, GroupStatus group) {
        NekoTradeResult result = machine.checkTrade(playerId, key.groupId(), key.tradeIndex());
        boolean tradeable = result != null && result.isSuccess();
        return new TradeStatus(
            group.locked(),
            group.cooldown(),
            tradeable,
            group.maxTrades(),
            group.usedTrades(),
            result == null || tradeable ? "" : result.getDisplayMessage());
    }

    private static Map<TradeKey, TradeStatus> delta(Map<TradeKey, TradeStatus> previous,
        Map<TradeKey, TradeStatus> rows) {
        Map<TradeKey, TradeStatus> changes = new LinkedHashMap<>();
        for (Map.Entry<TradeKey, TradeStatus> row : rows.entrySet()) {
            if (!row.getValue()
                .equals(previous.get(row.getKey()))) changes.put(row.getKey(), row.getValue());
        }
        for (TradeKey removed : previous.keySet()) {
            if (!rows.containsKey(removed)) changes.put(removed, null);
        }
        return changes;
    }

    private final class TradeRowsSync extends SyncHandler<TradeRowsSync> {

        private final UUID playerId;
        private int ticks;
        private long registryVersion = -1;

        private TradeRowsSync(UUID playerId) {
            this.playerId = playerId;
        }

        @Override
        public void detectAndSendChanges(boolean init) {
            if (getSyncManager().isClient()) return;
            long version = NekoTradeDatabase.INSTANCE.getVersion();
            if (!init && ++ticks < STATUS_REFRESH_TICKS && registryVersion == version) return;
            Map<TradeKey, TradeStatus> rows = buildRows(playerId);
            Map<TradeKey, TradeStatus> changes = delta(publishedRows, rows);
            if (init) changes.putAll(rows);
            sendRows(changes);
            publishedRows.clear();
            publishedRows.putAll(rows);
            ticks = 0;
            registryVersion = version;
        }

        private void sendRows(Map<TradeKey, TradeStatus> changes) {
            List<Map.Entry<TradeKey, TradeStatus>> batch = new ArrayList<>();
            int bytes = 2; // Count prefix.
            for (Map.Entry<TradeKey, TradeStatus> row : changes.entrySet()) {
                TradeStatus status = row.getValue();
                int estimate = 56 + (status == null ? 0
                    : 3 * status.failure()
                        .length());
                if (batch.size() >= MAX_BATCH_ROWS || bytes + estimate > MAX_BATCH_BYTES) {
                    sendBatch(batch);
                    batch.clear();
                    bytes = 2;
                }
                batch.add(row);
                bytes += estimate;
            }
            if (!batch.isEmpty()) sendBatch(batch);
        }

        private void sendBatch(List<Map.Entry<TradeKey, TradeStatus>> batch) {
            syncToClient(0, packet -> {
                packet.writeVarIntToBuffer(batch.size());
                for (Map.Entry<TradeKey, TradeStatus> row : batch) writeRow(packet, row.getKey(), row.getValue());
            });
        }

        @Override
        public void readOnClient(int id, PacketBuffer packet) throws IOException {
            if (id != 0 || packet.readableBytes() > MAX_BATCH_BYTES) throw new IOException("Trade status packet");
            int count = packet.readVarIntFromBuffer();
            if (count < 0 || count > MAX_BATCH_ROWS) throw new IOException("Trade status row count");
            Map<TradeKey, TradeStatus> changes = new LinkedHashMap<>();
            for (int i = 0; i < count; i++) {
                TradeKey key = new TradeKey(
                    new UUID(packet.readLong(), packet.readLong()),
                    packet.readVarIntFromBuffer());
                if (key.tradeIndex() < 0 || changes.containsKey(key)) throw new IOException("Trade status identity");
                changes.put(key, packet.readBoolean() ? readStatus(packet) : null);
            }
            if (packet.isReadable()) throw new IOException("Trade status trailing bytes");
            receiveRows(changes);
        }

        @Override
        public void readOnServer(int id, PacketBuffer packet) throws IOException {
            throw new IOException("Trade status rows are server-to-client only");
        }
    }

    static void writeRow(PacketBuffer packet, TradeKey key, TradeStatus status) {
        packet.writeLong(
            key.groupId()
                .getMostSignificantBits());
        packet.writeLong(
            key.groupId()
                .getLeastSignificantBits());
        packet.writeVarIntToBuffer(key.tradeIndex());
        packet.writeBoolean(status != null);
        if (status == null) return;
        packet.writeBoolean(status.locked());
        packet.writeLong(status.cooldown());
        packet.writeBoolean(status.tradeable());
        packet.writeVarIntToBuffer(status.maxTrades());
        packet.writeLong(status.usedTrades());
        NetworkUtils.writeStringSafe(packet, status.failure());
    }

    private static TradeStatus readStatus(PacketBuffer packet) throws IOException {
        boolean locked = packet.readBoolean();
        long cooldown = packet.readLong();
        boolean tradeable = packet.readBoolean();
        int maxTrades = packet.readVarIntFromBuffer();
        long usedTrades = packet.readLong();
        String reason = NetworkUtils.readStringSafe(packet);
        if (reason == null || reason.length() > MAX_FAILURE_LENGTH) throw new IOException("Trade status reason");
        return new TradeStatus(locked, cooldown, tradeable, maxTrades, usedTrades, reason);
    }

    // ==================== 交易分类图标/名称辅助（蓝图随批携带，静态无状态） ====================

    /**
     * 获取交易分类的图标 ItemStack
     * <p>
     * 为每个分类返回一个直观的 ItemStack 图标，避免标签页空白：
     * <ul>
     * <li>FAVOURITES：下界之星（与 V1 星标收藏一致）</li>
     * <li>UNKNOWN：纸（占位）</li>
     * <li>动态标签页：从 {@link NekoPageRegistry#getPageIcon(int)} 获取配置图标</li>
     * </ul>
     *
     * @param category 交易分类
     * @return 图标 ItemStack
     */
    public static ItemStack getCategoryIcon(NekoTradeCategory category) {
        if (category == null) {
            return new ItemStack(Items.paper);
        }
        if (category.isFavourites()) {
            // 收藏分类：用下界之星（与 V1 星标收藏一致）
            return new ItemStack(Items.nether_star);
        }
        if (category.isUnknown()) {
            // 未知分类：用纸作为占位图标
            return new ItemStack(Items.paper);
        }

        // 动态标签页：从 NekoPageRegistry 获取配置图标
        ItemStack pageIcon = NekoPageRegistry.getPageIcon(category.getTabId());
        if (pageIcon != null && pageIcon.getItem() != null) {
            return pageIcon;
        }
        return new ItemStack(Items.paper);
    }

    /**
     * 获取交易分类的显示名称
     * <p>
     * 特殊分类使用固定名称，动态标签页优先使用 {@link NekoPageRegistry#getPageName(int)}。
     *
     * @param category 交易分类
     * @return 显示名称
     */
    public static String getCategoryName(NekoTradeCategory category) {
        if (category == null) {
            return "未知";
        }
        if (category.isFavourites()) {
            return "收藏";
        }
        if (category.isUnknown()) {
            return "未知";
        }

        // 动态标签页：使用 NekoPageRegistry 中的配置名称
        String pageName = NekoPageRegistry.getPageName(category.getTabId());
        if (pageName != null && !pageName.isEmpty() && !"未知".equals(pageName)) {
            return pageName;
        }
        return category.getKey();
    }
}

// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtit.gui.vm;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.network.PacketBuffer;

import com.cleanroommc.modularui.value.sync.BooleanSyncValue;
import com.cleanroommc.modularui.value.sync.ModularSyncManager;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.StringSyncValue;
import com.cleanroommc.modularui.value.sync.SyncHandler;
import com.cleanroommc.modularui.value.sync.ValueSyncHandler;
import com.gtnewhorizon.gtnhlib.teams.ITeamData;
import com.gtnewhorizon.gtnhlib.teams.Team;
import com.gtnewhorizon.gtnhlib.teams.TeamManager;
import com.miaokatze.gtit.client.gui.NekoDisplayType;
import com.miaokatze.gtit.client.gui.NekoSortMode;
import com.miaokatze.gtit.client.gui.NekoTradeItemDisplay;
import com.miaokatze.gtit.client.gui.NekoTradeItemDisplayWidget;
import com.miaokatze.gtit.client.gui.NekoTradeMainPanel;
import com.miaokatze.gtit.client.gui.NekoWalletMode;
import com.miaokatze.gtit.gui.vm.StatusCodec.TradeKey;
import com.miaokatze.gtit.gui.vm.StatusCodec.TradeStatus;
import com.miaokatze.gtit.testutil.MinecraftTestBootstrap;
import com.miaokatze.gtit.testutil.SimpleAssert;
import com.miaokatze.gtit.trade.NekoPageConfig;
import com.miaokatze.gtit.trade.NekoPageEntry;
import com.miaokatze.gtit.trade.NekoPageRegistry;
import com.miaokatze.gtit.trade.NekoPageSource;
import com.miaokatze.gtit.trade.NekoTeamData;
import com.miaokatze.gtit.trade.v2.NekoFavouritesTracker;
import com.miaokatze.gtit.trade.v2.NekoHistoryManager;
import com.miaokatze.gtit.trade.v2.NekoTrade;
import com.miaokatze.gtit.trade.v2.NekoTradeCategory;
import com.miaokatze.gtit.trade.v2.NekoTradeDatabase;
import com.miaokatze.gtit.trade.v2.NekoTradeGroup;

import io.netty.buffer.Unpooled;

/** Real logical-client construction and MUI2 packet reads, without a game window or server world. */
public final class StatusCodecSyncTest {

    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID COOLDOWN_GROUP = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID ONE_TIME_GROUP = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final String FAVOURITES_CHANNEL = "nekoV2FavouriteStatus";

    public static void main(String[] args) throws Exception {
        if (!MinecraftTestBootstrap.enter(StatusCodecSyncTest.class, args)) return;
        cpw.mods.fml.server.FMLServerHandler.instance();
        NekoTradeDatabase.INSTANCE.clear();
        try {
            walletModePacketsAndIsolation();
            addGroup(COOLDOWN_GROUP, 60);
            addGroup(ONE_TIME_GROUP, -1);
            historyRemainsUnavailable();
            clientConstruction();
            clientConditionIsolation();
            receivedStatuses();
            receivedFailureReasons();
            serverStillRequiresHistory();
            persistedFavouritesLoadOnRead();
            favouritePacketsAndScope();
            System.out.println("StatusCodecSyncTest: 8 cases passed");
        } finally {
            NekoTradeDatabase.INSTANCE.clear();
        }
    }

    private static final class WalletGui extends NekoVMGuiV2 {

        private WalletGui() {
            super(null);
        }

        @Override
        public UUID getPlayerId() {
            return PLAYER;
        }
    }

    @SuppressWarnings("unchecked")
    private static void walletModePacketsAndIsolation() throws Exception {
        Field teamsField = TeamManager.class.getDeclaredField("TEAMS");
        Field mapField = TeamManager.class.getDeclaredField("TEAM_MAP");
        Field cacheField = TeamManager.class.getDeclaredField("PLAYER_TEAM_CACHE");
        teamsField.setAccessible(true);
        mapField.setAccessible(true);
        cacheField.setAccessible(true);
        List<Team> teams = (List<Team>) teamsField.get(null);
        Map<UUID, Team> map = (Map<UUID, Team>) mapField.get(null);
        Map<UUID, Team> cache = (Map<UUID, Team>) cacheField.get(null);
        List<Team> oldTeams = new java.util.ArrayList<>(teams);
        Map<UUID, Team> oldMap = new java.util.HashMap<>(map);
        Map<UUID, Team> oldCache = new java.util.HashMap<>(cache);
        int[] queries = { 0 };
        ITeamData[] data = { new NekoTeamData() };
        Team team = new Team("Integrated server team", UUID.randomUUID(), false) {

            @Override
            public boolean isMember(UUID id) {
                queries[0]++;
                return PLAYER.equals(id);
            }

            @Override
            public ITeamData getData(String key) {
                queries[0]++;
                return NekoTeamData.ID.equals(key) ? data[0] : null;
            }
        };
        try {
            teams.clear();
            map.clear();
            cache.clear();
            teams.add(team);
            map.put(team.getTeamId(), team);
            StatusCodec client = new StatusCodec(null, () -> {});
            PanelSyncManager clientManager = manager(true);
            client.registerSyncValues(clientManager, PLAYER);
            clientManager.initialize("wallet_client");
            BooleanSyncValue clientValue = clientManager.findSyncHandler("nekoV2TeamWallet", BooleanSyncValue.class);
            SimpleAssert.that(!clientValue.isAllowC2S(), "wallet source remains S2C only");

            // Use the production GUI getter, without constructing an unrelated GregTech machine.
            Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
            Field unsafeField = unsafeClass.getDeclaredField("theUnsafe");
            unsafeField.setAccessible(true);
            WalletGui gui = (WalletGui) unsafeClass.getMethod("allocateInstance", Class.class)
                .invoke(unsafeField.get(null), WalletGui.class);
            Field codecField = NekoVMGuiV2.class.getDeclaredField("statusCodec");
            codecField.setAccessible(true);
            codecField.set(gui, client);
            for (int i = 0; i < 100; i++) {
                SimpleAssert.eq(NekoWalletMode.PERSONAL, gui.getWalletMode(), "no local-server team leak before S2C");
                clientValue.updateCacheFromSource(true);
            }
            SimpleAssert.eq(0, queries[0], "client construction, getter and source refresh never query Teams");

            StatusCodec server = new StatusCodec(null, () -> {});
            PanelSyncManager serverManager = manager(false);
            server.registerSyncValues(serverManager, PLAYER);
            BooleanSyncValue serverValue = serverManager.findSyncHandler("nekoV2TeamWallet", BooleanSyncValue.class);
            SyncHandler<?> rows = serverManager.findSyncHandler(StatusCodec.ROWS_CHANNEL, SyncHandler.class);
            SimpleAssert.that(!rows.isAllowC2S(), "trade status rows are S2C only");
            TradeKey key = new TradeKey(COOLDOWN_GROUP, 0);
            PacketBuffer forged = new PacketBuffer(Unpooled.buffer());
            try {
                forged.writeVarIntToBuffer(1);
                StatusCodec.writeRow(forged, key, failed("伪造原因"));
                serverManager
                    .receiveWidgetUpdate(PanelSyncManager.makeSyncKey(StatusCodec.ROWS_CHANNEL, 0), false, 0, forged);
                SimpleAssert.eq(null, server.clientStatus(key), "C2S cannot replace an authoritative row");
            } finally {
                forged.release();
            }
            SimpleAssert.that(server.isTeamWallet(), "server recognizes the actual shared wallet route");
            receiveWallet(clientManager, serverValue);
            int afterServerQuery = queries[0];
            for (int i = 0; i < 100; i++) {
                SimpleAssert
                    .eq(NekoWalletMode.TEAM, gui.getWalletMode(), "S2C selects team wallet in production getter");
                clientValue.updateCacheFromSource(true);
            }
            SimpleAssert.eq(afterServerQuery, queries[0], "repeated client reads use the synchronized cache");

            data[0] = null;
            serverValue.updateCacheFromSource(false);
            SimpleAssert.that(!server.isTeamWallet(), "missing GTIT team data follows personal-wallet fallback");
            SimpleAssert.eq(NekoWalletMode.TEAM, gui.getWalletMode(), "client changes only when S2C arrives");
            receiveWallet(clientManager, serverValue);
            SimpleAssert.eq(NekoWalletMode.PERSONAL, gui.getWalletMode(), "server route changes refresh client mode");
            receiveWallet(clientManager, new BooleanSyncValue(() -> true));
            StatusCodec reopened = new StatusCodec(null, () -> {});
            reopened.registerSyncValues(manager(true), PLAYER);
            SimpleAssert.that(!reopened.isTeamWallet(), "a new connection/view cannot inherit old wallet mode");
        } finally {
            teams.clear();
            teams.addAll(oldTeams);
            map.clear();
            map.putAll(oldMap);
            cache.clear();
            cache.putAll(oldCache);
        }
    }

    private static void receiveWallet(PanelSyncManager manager, BooleanSyncValue source) throws Exception {
        PacketBuffer packet = new PacketBuffer(Unpooled.buffer());
        try {
            source.write(packet);
            manager.receiveWidgetUpdate(
                PanelSyncManager.makeSyncKey("nekoV2TeamWallet", 0),
                false,
                ValueSyncHandler.SYNC_VALUE,
                packet);
            SimpleAssert.that(!packet.isReadable(), "wallet source consumes its S2C packet");
        } finally {
            packet.release();
        }
    }

    private static void addGroup(UUID id, int cooldown) {
        NekoTradeGroup group = new NekoTradeGroup(id);
        group.setCooldown(cooldown);
        group.getTrades()
            .add(new NekoTrade());
        NekoTradeDatabase.INSTANCE.addTradeGroup(group);
    }

    private static void clientConstruction() {
        // The singleton deliberately has no bound server world, as on a remote client.
        // A null machine also makes an accidental client checkTrade call fail immediately.
        StatusCodec codec = new StatusCodec(
            null,
            () -> { throw new AssertionError("Construction must not invoke the receive callback"); });
        PanelSyncManager manager = manager(true);
        codec.registerSyncValues(manager, PLAYER);
        manager.initialize("status_codec_client");
        StringSyncValue favourites = manager.findSyncHandler(FAVOURITES_CHANNEL, StringSyncValue.class);
        SimpleAssert.eq("", favourites.getValue(), "remote favourites start empty");
        SimpleAssert.that(!favourites.isAllowC2S(), "favourite status remains S2C only");
        favourites.updateCacheFromSource(true);
        SyncHandler<?> rows = manager.findSyncHandler(StatusCodec.ROWS_CHANNEL, SyncHandler.class);
        SimpleAssert.that(!rows.isAllowC2S(), "trade status rows start empty and read only");
        rows.detectAndSendChanges(false);
        SimpleAssert.that(
            codec.takeClientChanges()
                .isEmpty(),
            "a client tick never builds server rows");
        historyRemainsUnavailable();
    }

    private static void receivedFailureReasons() throws Exception {
        StatusCodec codec = new StatusCodec(null, () -> {});
        PanelSyncManager manager = manager(true);
        codec.registerSyncValues(manager, PLAYER);
        manager.initialize("failure_reasons");
        TradeKey key = new TradeKey(COOLDOWN_GROUP, 0);
        String shortageDetail = "缺少：猫猫币 × 3；材料[a:b,c] × 2（NBT 不符）";
        receiveRows(manager, Collections.singletonMap(key, failed(shortageDetail)));
        SimpleAssert.eq(
            shortageDetail,
            codec.clientStatus(key)
                .failure(),
            "rows preserve UTF8 and punctuation");
        receiveRows(manager, Collections.singletonMap(key, failed("输出总线已满")));
        SimpleAssert.eq(
            "输出总线已满",
            codec.clientStatus(key)
                .failure(),
            "a changed reason replaces the previous one");
        String longReason = String.join("", Collections.nCopies(180, "缺料"));
        for (int start = 0; start < 100; start += 25) {
            Map<TradeKey, TradeStatus> rows = new LinkedHashMap<>();
            for (int i = start; i < start + 25; i++) rows.put(new TradeKey(COOLDOWN_GROUP, i), failed(longReason));
            receiveRows(manager, rows);
        }
        SimpleAssert.eq(
            longReason,
            codec.clientStatus(new TradeKey(COOLDOWN_GROUP, 99))
                .failure(),
            "reason data beyond 32 KiB retains the last row across batches");
        receiveRows(manager, Collections.singletonMap(key, failed(String.join("", Collections.nCopies(2000, "长")))));
        SimpleAssert.that(
            codec.clientStatus(key)
                .failure()
                .length() <= 1024,
            "each reason has a bounded length");
        receiveRows(manager, Collections.singletonMap(key, null));
        SimpleAssert.eq(null, codec.clientStatus(key), "row removal clears the stale result");
        Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
        Field unsafeField = unsafeClass.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        Object gui = unsafeClass.getMethod("allocateInstance", Class.class)
            .invoke(unsafeField.get(null), WalletGui.class);
        Method message = NekoVMGuiV2.class
            .getDeclaredMethod("getTradeResultMessage", com.miaokatze.gtit.trade.v2.NekoTradeResult.class);
        message.setAccessible(true);
        String shortage = "缺少：闪烁猫猫币 × 2";
        SimpleAssert.eq(
            shortage,
            message.invoke(
                gui,
                com.miaokatze.gtit.trade.v2.NekoTradeResult
                    .fail(com.miaokatze.gtit.trade.v2.NekoTradeResult.Status.INSUFFICIENT_ITEMS, shortage)),
            "actual click-result formatter preserves the server's specific shortage");
        historyRemainsUnavailable();
    }

    private static TradeStatus failed(String reason) {
        return new TradeStatus(false, 0, false, 1, 0, reason);
    }

    static void receiveRows(PanelSyncManager manager, Map<TradeKey, TradeStatus> rows) throws Exception {
        PacketBuffer packet = new PacketBuffer(Unpooled.buffer());
        try {
            packet.writeVarIntToBuffer(rows.size());
            for (Map.Entry<TradeKey, TradeStatus> row : rows.entrySet()) {
                StatusCodec.writeRow(packet, row.getKey(), row.getValue());
            }
            manager.receiveWidgetUpdate(PanelSyncManager.makeSyncKey(StatusCodec.ROWS_CHANNEL, 0), false, 0, packet);
            SimpleAssert.that(!packet.isReadable(), "the MUI2 path consumes the full row delta");
        } finally {
            packet.release();
        }
    }

    private static void clientConditionIsolation() {
        NekoTradeGroup guarded = new NekoTradeGroup(UUID.randomUUID()) {

            @Override
            public boolean isConditionsSatisfied(UUID playerId) {
                throw new AssertionError("Client must not evaluate authoritative trade conditions");
            }
        };
        NekoTradeDatabase.INSTANCE.addTradeGroup(guarded);
        try {
            new StatusCodec(null, () -> {}).registerSyncValues(manager(true), PLAYER);
        } finally {
            NekoTradeDatabase.INSTANCE.removeTradeGroup(guarded.getId());
        }
    }

    private static void receivedStatuses() throws Exception {
        int[] refreshes = { 0 };
        StatusCodec codec = new StatusCodec(null, () -> refreshes[0]++);
        PanelSyncManager manager = manager(true);
        codec.registerSyncValues(manager, PLAYER);
        manager.initialize("status_codec_receive");
        TradeKey cooldownKey = new TradeKey(COOLDOWN_GROUP, 0);
        TradeKey oneTimeKey = new TradeKey(ONE_TIME_GROUP, 0);
        TradeStatus cooldown = new TradeStatus(true, 37, false, 4, 2, "冷却");
        TradeStatus redeemed = new TradeStatus(false, -1, false, 1, 0, "已兑换");
        Map<TradeKey, TradeStatus> initial = new LinkedHashMap<>();
        initial.put(cooldownKey, cooldown);
        initial.put(oneTimeKey, redeemed);
        receiveRows(manager, initial);
        SimpleAssert.eq(cooldown, codec.clientStatus(cooldownKey), "BQ, cooldown and team data round-trip together");
        SimpleAssert.eq(redeemed, codec.clientStatus(oneTimeKey), "one-time redemption survives decoding");
        SimpleAssert.eq(initial, codec.takeClientChanges(), "one registration produces two changed rows");
        receiveRows(manager, initial);
        SimpleAssert.that(
            codec.takeClientChanges()
                .isEmpty(),
            "replayed identical rows do not invalidate the host");

        Map<TradeKey, TradeStatus> removal = Collections.singletonMap(cooldownKey, null);
        receiveRows(manager, removal);
        SimpleAssert.eq(null, codec.clientStatus(cooldownKey), "the tombstone clears only the specified row");
        SimpleAssert.eq(redeemed, codec.clientStatus(oneTimeKey), "a separate row still has its prior state");
        SimpleAssert.eq(removal, codec.takeClientChanges(), "only the removed row is queued");
        SimpleAssert.eq(0, refreshes[0], "changed rows never request a catalogue refresh");
        historyRemainsUnavailable();
    }

    private static void persistedFavouritesLoadOnRead() throws Exception {
        NekoFavouritesTracker tracker = NekoFavouritesTracker.INSTANCE;
        Field saveDir = NekoFavouritesTracker.class.getDeclaredField("saveDir");
        saveDir.setAccessible(true);
        Object previous = saveDir.get(tracker);
        Path directory = Files.createTempDirectory("gtit-favourites-");
        Path file = directory.resolve(PLAYER + ".dat");
        try {
            tracker.clearAll();
            saveDir.set(tracker, directory.toFile());
            // Write the existing on-disk schema directly, before any add/toggle operation.
            NBTTagCompound root = new NBTTagCompound();
            NBTTagCompound entry = new NBTTagCompound();
            entry.setString("groupId", COOLDOWN_GROUP.toString());
            entry.setInteger("tradeIndex", 0);
            NBTTagList entries = new NBTTagList();
            entries.appendTag(entry);
            root.setTag("favourites", entries);
            CompressedStreamTools.safeWrite(root, file.toFile());
            SimpleAssert.eq(
                1,
                tracker.getFavourites(PLAYER)
                    .size(),
                "Opening a new session loads legacy favourites");
            tracker.clearAll();
            SimpleAssert
                .that(tracker.isFavourite(PLAYER, COOLDOWN_GROUP, 0), "Direct lookup also loads old favourites");
        } finally {
            tracker.clearAll();
            saveDir.set(tracker, previous);
            Files.deleteIfExists(file);
            Files.delete(directory);
        }
    }

    private static void favouritePacketsAndScope() throws Exception {
        List<NekoPageEntry> oldPages = NekoPageRegistry.getAllPages();
        NekoPageConfig.NekoPageData pages = new NekoPageConfig.NekoPageData();
        int[] tabIds = { -400001, 7, -100003 };
        NekoPageSource[] sources = { NekoPageSource.DEFAULT, NekoPageSource.CUSTOM, NekoPageSource.PRESET };
        NekoPageEntry[] entries = new NekoPageEntry[3];
        UUID[] groups = { UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID() };
        StatusCodec codec = new StatusCodec(null, () -> {});
        PanelSyncManager manager = manager(true);
        codec.registerSyncValues(manager, PLAYER);
        manager.initialize("favourite_receive");
        TradePage tradePage = new TradePage(null);
        Field scope = TradePage.class.getDeclaredField("tradeScope");
        scope.setAccessible(true);
        AtomicReference<Map<NekoTradeCategory, List<NekoTradeItemDisplay>>> received = new AtomicReference<>();
        NekoTradeMainPanel.PanelCallback callback = new NekoTradeMainPanel.PanelCallback() {

            @Override
            public UUID getPlayerId() {
                return PLAYER;
            }

            @Override
            public String getSearchText() {
                return "";
            }

            @Override
            public boolean isSearchBarFocused() {
                return false;
            }

            @Override
            public NekoDisplayType getDisplayType() {
                return NekoDisplayType.TILE;
            }

            @Override
            public NekoSortMode getSortMode() {
                return NekoSortMode.SMART;
            }

            @Override
            public NekoWalletMode getWalletMode() {
                return NekoWalletMode.PERSONAL;
            }

            @Override
            public int getRefreshInterval() {
                return 20;
            }

            @Override
            public boolean isClient() {
                return true;
            }

            @Override
            public long getSyncedCooldownRemaining(UUID group, int index) {
                return 0L;
            }

            @Override
            public boolean getSyncedFavourite(UUID group, int index) {
                return codec.isFavourite(group, index);
            }

            @Override
            public NekoTradeCategory getActiveCategory() {
                return NekoTradeCategory.FAVOURITES;
            }

            @Override
            public boolean isTradeCategoryVisible(NekoTradeCategory category) {
                return tradePage.isCategoryVisible(category);
            }

            @Override
            public List<NekoTradeItemDisplayWidget> getDisplayedWidgets(NekoDisplayType type,
                NekoTradeCategory category) {
                return java.util.Collections.emptyList();
            }

            @Override
            public Boolean getSyncedTradeableStatus(UUID group, int index) {
                return Boolean.TRUE;
            }

            @Override
            public void onTradeDisplayUpdated(Map<NekoTradeCategory, List<NekoTradeItemDisplay>> trades) {
                received.set(trades);
            }

            @Override
            public void onRestoreSettings() {}

            @Override
            public void onDispose() {}

            @Override
            public boolean isEditOverlayOpen() {
                return false;
            }

            @Override
            public void closeEditOverlay() {}

            @Override
            public void onMainPanelUpdate() {}
        };
        NekoTradeMainPanel panel = new NekoTradeMainPanel("favourite_scope", callback, null, manager);
        Method incremental = NekoTradeMainPanel.class.getDeclaredMethod("updateTradeInformation", Map.class);
        incremental.setAccessible(true);
        try {
            for (int i = 0; i < entries.length; i++) {
                entries[i] = new NekoPageEntry();
                entries[i].setId(tabIds[i]);
                entries[i].setName(sources[i].name());
                entries[i].setSource(sources[i]);
                NekoTradeGroup group = new NekoTradeGroup(groups[i]);
                group.setCategory(NekoTradeCategory.ofTabId(tabIds[i]));
                group.getTrades()
                    .add(new NekoTrade());
                NekoTradeDatabase.INSTANCE.addTradeGroup(group);
            }
            pages.setPages(Arrays.asList(entries));
            NekoPageRegistry.applySyncedPages(pages);

            // Server-side tracker data is not visible to a remote client before S2C arrives.
            NekoFavouritesTracker.INSTANCE.toggleFavourite(PLAYER, groups[0], 0);
            SimpleAssert.eq(
                0,
                panel.formatTrades()
                    .get(NekoTradeCategory.FAVOURITES)
                    .size(),
                "Client uses only its S2C cache");
            NekoFavouritesTracker.INSTANCE.toggleFavourite(PLAYER, groups[1], 0);
            NekoFavouritesTracker.INSTANCE.toggleFavourite(PLAYER, groups[2], 0);
            Method build = StatusCodec.class.getDeclaredMethod("buildFavouriteStatusString", UUID.class);
            build.setAccessible(true);
            String snapshot = (String) build.invoke(codec, PLAYER);
            NekoFavouritesTracker.INSTANCE.clearAll();
            receive(manager, FAVOURITES_CHANNEL, snapshot);
            for (int i = 0; i < groups.length; i++) {
                scope.setInt(tradePage, i);
                Map<NekoTradeCategory, List<NekoTradeItemDisplay>> trades = panel.formatTrades();
                List<NekoTradeItemDisplay> favourites = trades.get(NekoTradeCategory.FAVOURITES);
                SimpleAssert.eq(1, favourites.size(), "Each scope has exactly its own favourite");
                SimpleAssert.eq(
                    groups[i],
                    favourites.get(0)
                        .getGroupId(),
                    "DEFAULT/CUSTOM/PRESET isolation survives S2C");
                incremental.invoke(panel, trades);
                incremental.invoke(panel, trades);
                SimpleAssert.eq(
                    1,
                    received.get()
                        .get(NekoTradeCategory.FAVOURITES)
                        .size(),
                    "Held Ctrl refresh never duplicates old favourites");
                receive(manager, FAVOURITES_CHANNEL, "");
                incremental.invoke(panel, trades);
                SimpleAssert.eq(
                    0,
                    received.get()
                        .get(NekoTradeCategory.FAVOURITES)
                        .size(),
                    "Unfavourite applies on the first incremental refresh");
                receive(manager, FAVOURITES_CHANNEL, snapshot);
                incremental.invoke(panel, trades);
                SimpleAssert.eq(
                    1,
                    received.get()
                        .get(NekoTradeCategory.FAVOURITES)
                        .size(),
                    "Favourite applies without releasing Ctrl");
            }
        } finally {
            for (UUID group : groups) NekoTradeDatabase.INSTANCE.removeTradeGroup(group);
            NekoFavouritesTracker.INSTANCE.clearAll();
            pages.setPages(oldPages);
            NekoPageRegistry.applySyncedPages(pages);
        }
    }

    private static void receive(PanelSyncManager manager, String channel, String status) throws Exception {
        PacketBuffer packet = new PacketBuffer(Unpooled.buffer());
        try {
            new StringSyncValue(() -> status).write(packet);
            manager.receiveWidgetUpdate(
                PanelSyncManager.makeSyncKey(channel, 0),
                false,
                ValueSyncHandler.SYNC_VALUE,
                packet);
            SimpleAssert.that(!packet.isReadable(), channel + " consumes the encoded status packet");
        } finally {
            packet.release();
        }
    }

    private static void serverStillRequiresHistory() {
        try {
            new StatusCodec(null, () -> {}).buildRows(PLAYER);
            throw new AssertionError("Server polling must retain the history readiness check");
        } catch (IllegalStateException expected) {
            SimpleAssert.eq(
                "History storage is not ready for this world",
                expected.getMessage(),
                "server failure remains explicit");
        }
    }

    private static void historyRemainsUnavailable() {
        try {
            NekoHistoryManager.INSTANCE.getHistory(PLAYER, COOLDOWN_GROUP);
            throw new AssertionError("The remote-client fixture must not have a bound history store");
        } catch (IllegalStateException expected) {
            SimpleAssert.eq(
                "History storage is not ready for this world",
                expected.getMessage(),
                "unbound history stays guarded");
        }
    }

    private static PanelSyncManager manager(boolean client) {
        return new PanelSyncManager(new ModularSyncManager(client), true);
    }
}

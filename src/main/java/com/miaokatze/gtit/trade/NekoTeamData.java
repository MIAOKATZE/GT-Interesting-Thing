package com.miaokatze.gtit.trade;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.gtnewhorizon.gtnhlib.teams.ITeamData;
import com.gtnewhorizon.gtnhlib.teams.Team;
import com.gtnewhorizon.gtnhlib.teams.TeamDataTransferReason;
import com.miaokatze.gtit.trade.v2.NekoTradeHistory;

/**
 * 猫猫币团队数据
 * 实现 GTNHLib Teams API 的 ITeamData 接口
 * 存储团队共享的猫猫币钱包
 * <p>
 * 仅团队钱包模式，无个人钱包数据
 */
public class NekoTeamData implements ITeamData {

    /** 统一 logger（O2-B02 去中心化：与主类同用 "gtit" logger 名，日志过滤口径不变） */
    private static final Logger LOG = LogManager.getLogger("gtit");

    public static final String ID = "GTIT";
    /** Cross-team callbacks must not enter with either team monitor held. */
    private static final Object TRANSFER_LOCK = new Object();

    private final NekoWallet wallet = new NekoWallet();
    private final Map<UUID, NekoTradeHistory> tradeHistories = new HashMap<>();
    /** Keep stale team callbacks and new history lookups attached to the surviving owner. */
    private NekoTeamData successor;
    private boolean legacyHistoryMigrated;
    /** Receipts are persisted with history; reset retains them so migration replay cannot undo an admin reset. */
    private final Set<UUID> legacyHistoryReceipts = new HashSet<>();

    public synchronized boolean applyLegacyHistory(UUID playerId, Map<UUID, NekoTradeHistory> records) {
        return applyLegacyHistory(playerId, records, false);
    }

    public synchronized boolean consumeLegacyHistory(UUID playerId, Map<UUID, NekoTradeHistory> records) {
        return applyLegacyHistory(playerId, records, true);
    }

    private boolean applyLegacyHistory(UUID playerId, Map<UUID, NekoTradeHistory> records, boolean consume) {
        if (successor != null) return consume ? successor.consumeLegacyHistory(playerId, records)
            : successor.applyLegacyHistory(playerId, records);
        if (legacyHistoryReceipts.contains(playerId)) return false;
        for (Map.Entry<UUID, NekoTradeHistory> entry : records.entrySet()) {
            if (consume) entry.getValue()
                .transferAllTo(getTradeHistory(entry.getKey()));
            else mergeTradeHistory(entry.getKey(), entry.getValue());
        }
        legacyHistoryReceipts.add(playerId);
        return true;
    }

    /**
     * 获取团队共享钱包
     */
    public NekoWallet getWallet() {
        return wallet;
    }

    public synchronized NekoTradeHistory getTradeHistory(UUID tradeGroupId) {
        if (successor != null) return successor.getTradeHistory(tradeGroupId);
        if (tradeGroupId == null) {
            return new NekoTradeHistory();
        }
        return tradeHistories.computeIfAbsent(tradeGroupId, ignored -> new NekoTradeHistory());
    }

    public synchronized Map<UUID, NekoTradeHistory> getTradeHistoriesSnapshot() {
        if (successor != null) return successor.getTradeHistoriesSnapshot();
        Map<UUID, NekoTradeHistory> snapshot = new HashMap<>();
        for (Map.Entry<UUID, NekoTradeHistory> entry : tradeHistories.entrySet()) {
            snapshot.put(
                entry.getKey(),
                entry.getValue()
                    .copy());
        }
        return snapshot;
    }

    public synchronized void mergeTradeHistory(UUID tradeGroupId, NekoTradeHistory history) {
        if (tradeGroupId == null || history == null) {
            return;
        }
        getTradeHistory(tradeGroupId).mergeFrom(history);
    }

    public synchronized void resetAllTradeHistories() {
        if (successor != null) {
            successor.resetAllTradeHistories();
            return;
        }
        for (NekoTradeHistory history : tradeHistories.values()) {
            history.reset();
        }
    }

    public synchronized boolean isLegacyHistoryMigrated() {
        if (successor != null) return successor.isLegacyHistoryMigrated();
        return legacyHistoryMigrated;
    }

    public synchronized void setLegacyHistoryMigrated(boolean migrated) {
        if (successor != null) {
            successor.setLegacyHistoryMigrated(migrated);
            return;
        }
        legacyHistoryMigrated = migrated;
    }

    @Override
    public synchronized void writeToNBT(NBTTagCompound tag) {
        NBTTagCompound walletTag = wallet.writeToNBT();
        tag.setTag("wallet", walletTag);
        tag.setBoolean("legacyHistoryMigrated", legacyHistoryMigrated);
        NBTTagCompound receipts = new NBTTagCompound();
        for (UUID playerId : legacyHistoryReceipts) receipts.setBoolean(playerId.toString(), true);
        tag.setTag("legacyHistoryReceipts", receipts);

        NBTTagList historyList = new NBTTagList();
        for (Map.Entry<UUID, NekoTradeHistory> entry : tradeHistories.entrySet()) {
            NBTTagCompound historyTag = new NBTTagCompound();
            historyTag.setString(
                "groupId",
                entry.getKey()
                    .toString());
            historyTag.setTag(
                "history",
                entry.getValue()
                    .writeToNBT());
            historyList.appendTag(historyTag);
        }
        tag.setTag("tradeHistory", historyList);
    }

    @Override
    public synchronized void readFromNBT(NBTTagCompound tag) {
        if (successor != null) throw new IllegalStateException("Cannot reload consumed team data");
        if (tag != null && tag.hasKey("wallet") && !tag.hasKey("wallet", 10)) {
            throw new IllegalArgumentException("Invalid team wallet compound");
        }
        // Parse all restriction metadata before mutating the existing ledger. Never skip malformed
        // history entries: doing so turns a corrupt one-time record into a fresh redemption.
        Map<UUID, NekoTradeHistory> loaded = new HashMap<>();
        Set<UUID> receipts = new HashSet<>();
        if (tag != null && tag.hasKey("tradeHistory")) {
            if (!tag.hasKey("tradeHistory", 9)) throw new IllegalArgumentException("Invalid team history list");
            NBTTagList raw = (NBTTagList) tag.getTag("tradeHistory");
            NBTTagList list = tag.getTagList("tradeHistory", 10);
            if (raw.tagCount() != list.tagCount()) throw new IllegalArgumentException("Invalid team history entries");
            for (int i = 0; i < list.tagCount(); i++) {
                NBTTagCompound record = list.getCompoundTagAt(i);
                if (!record.hasKey("groupId", 8) || !record.hasKey("history", 10)) {
                    throw new IllegalArgumentException("Invalid team history entry");
                }
                UUID groupId = UUID.fromString(record.getString("groupId"));
                NekoTradeHistory history = new NekoTradeHistory();
                history.loadFromNBT(record.getCompoundTag("history"));
                if (loaded.put(groupId, history) != null) throw new IllegalArgumentException("Duplicate team history");
            }
        }
        if (tag != null && tag.hasKey("legacyHistoryReceipts")) {
            if (!tag.hasKey("legacyHistoryReceipts", 10))
                throw new IllegalArgumentException("Invalid migration receipts");
            NBTTagCompound receiptTag = tag.getCompoundTag("legacyHistoryReceipts");
            for (String key : (Set<String>) receiptTag.func_150296_c()) {
                if (!receiptTag.hasKey(key, 1) || !receiptTag.getBoolean(key)) {
                    throw new IllegalArgumentException("Invalid migration receipt");
                }
                receipts.add(UUID.fromString(key));
            }
        }
        wallet.readFromNBT(tag != null && tag.hasKey("wallet") ? tag.getCompoundTag("wallet") : null);
        tradeHistories.clear();
        tradeHistories.putAll(loaded);
        legacyHistoryReceipts.clear();
        legacyHistoryReceipts.addAll(receipts);
        legacyHistoryMigrated = tag != null && tag.getBoolean("legacyHistoryMigrated");
    }

    /**
     * 团队合并时调用
     * 将被合并团队的钱包余额合并到当前团队
     */
    @Override
    public void mergeData(Team consumed, Team surviving, ITeamData oldTeamData) {
        if (!(oldTeamData instanceof NekoTeamData) || oldTeamData == this) return;
        NekoTeamData other = (NekoTeamData) oldTeamData;
        synchronized (TRANSFER_LOCK) {
            NekoTeamData target = root();
            if (other == target) return;
            synchronized (other) {
                if (other.successor != null) return;
                synchronized (target) {
                    // A stale target names an already consumed team; do not relabel the actual root.
                    if (target == this && surviving != null) target.wallet.setTeamId(surviving.getTeamId());
                    other.wallet.transferAllTo(target.wallet);
                    for (Map.Entry<UUID, NekoTradeHistory> entry : other.tradeHistories.entrySet()) {
                        entry.getValue()
                            .transferAllTo(target.getTradeHistory(entry.getKey()));
                    }
                    // The source was consumed; replaying the merge must not duplicate redemption history.
                    other.tradeHistories.clear();
                    target.legacyHistoryReceipts.addAll(other.legacyHistoryReceipts);
                    other.legacyHistoryReceipts.clear();
                    target.legacyHistoryMigrated = target.legacyHistoryMigrated && other.legacyHistoryMigrated;
                    other.successor = target;
                }
            }
        }
        LOG.info("猫猫币团队钱包合并完成");
    }

    /**
     * 玩家转移团队时调用
     * 仅团队钱包模式，无个人数据需要转移
     */
    // [GT-compat] beta 兼容层（beta1/beta2/beta3）：正式版发布时移除本分支并切换至最新 API
    // GTNHLib 已将 copyData(TeamDataCopyReason) 改名 transferData(TeamDataTransferReason)：
    // 0.10.3 仍为 copyData 形态，0.11.9（beta-1 集实测）起仅存 transferData；beta-1/2/3 三集编译矩阵均按本签名通过
    // 0.10.3 从未随任何 beta 实例包发布（仅旧编译期 pin），三个 beta 版本运行期的调用方均为 transferData
    @Override
    public void transferData(Team prevTeam, Team newTeam, UUID playerId, ITeamData prevTeamData,
        TeamDataTransferReason reason) {
        if (!(prevTeamData instanceof NekoTeamData) || prevTeamData == this) return;
        NekoTeamData previous = (NekoTeamData) prevTeamData;
        synchronized (TRANSFER_LOCK) {
            NekoTeamData source = previous.root(), target = root();
            if (source == target) return;
            synchronized (source) {
                synchronized (target) {
                    for (Map.Entry<UUID, NekoTradeHistory> entry : source.tradeHistories.entrySet()) {
                        target.mergeTradeHistory(entry.getKey(), entry.getValue());
                    }
                    target.legacyHistoryReceipts.addAll(source.legacyHistoryReceipts);
                    target.legacyHistoryMigrated = target.legacyHistoryMigrated && source.legacyHistoryMigrated;
                }
            }
        }
        // Moving a player copies history but never takes money away from their previous team.
    }

    /** Only called under TRANSFER_LOCK; consumed links are immutable. */
    private synchronized NekoTeamData root() {
        return successor == null ? this : successor.root();
    }
}

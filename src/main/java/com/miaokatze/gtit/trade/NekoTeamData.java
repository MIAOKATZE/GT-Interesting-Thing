package com.miaokatze.gtit.trade;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.gtnewhorizon.gtnhlib.teams.ITeamData;
import com.gtnewhorizon.gtnhlib.teams.Team;
import com.gtnewhorizon.gtnhlib.teams.TeamDataCopyReason;
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

    private final NekoWallet wallet = new NekoWallet();
    private final Map<UUID, NekoTradeHistory> tradeHistories = new HashMap<>();
    private boolean legacyHistoryMigrated;

    /**
     * 获取团队共享钱包
     */
    public NekoWallet getWallet() {
        return wallet;
    }

    public synchronized NekoTradeHistory getTradeHistory(UUID tradeGroupId) {
        if (tradeGroupId == null) {
            return new NekoTradeHistory();
        }
        return tradeHistories.computeIfAbsent(tradeGroupId, ignored -> new NekoTradeHistory());
    }

    public synchronized Map<UUID, NekoTradeHistory> getTradeHistoriesSnapshot() {
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
        for (NekoTradeHistory history : tradeHistories.values()) {
            history.reset();
        }
    }

    public synchronized boolean isLegacyHistoryMigrated() {
        return legacyHistoryMigrated;
    }

    public synchronized void setLegacyHistoryMigrated(boolean migrated) {
        legacyHistoryMigrated = migrated;
    }

    @Override
    public synchronized void writeToNBT(NBTTagCompound tag) {
        NBTTagCompound walletTag = wallet.writeToNBT();
        tag.setTag("wallet", walletTag);
        tag.setBoolean("legacyHistoryMigrated", legacyHistoryMigrated);

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
        tradeHistories.clear();
        legacyHistoryMigrated = false;
        if (tag == null) {
            return;
        }
        if (tag.hasKey("wallet")) {
            wallet.readFromNBT(tag.getCompoundTag("wallet"));
        }
        legacyHistoryMigrated = tag.getBoolean("legacyHistoryMigrated");
        if (!tag.hasKey("tradeHistory")) {
            return;
        }
        NBTTagList historyList = tag.getTagList("tradeHistory", 10);
        for (int i = 0; i < historyList.tagCount(); i++) {
            NBTTagCompound historyTag = historyList.getCompoundTagAt(i);
            try {
                UUID groupId = UUID.fromString(historyTag.getString("groupId"));
                NekoTradeHistory history = new NekoTradeHistory();
                history.loadFromNBT(historyTag.getCompoundTag("history"));
                tradeHistories.put(groupId, history);
            } catch (IllegalArgumentException ignored) {
                LOG.warn("Skipping invalid Neko trade history group ID: " + historyTag.getString("groupId"));
            }
        }
    }

    /**
     * 团队合并时调用
     * 将被合并团队的钱包余额合并到当前团队
     */
    @Override
    public synchronized void mergeData(Team consumed, Team surviving, ITeamData oldTeamData) {
        if (oldTeamData instanceof NekoTeamData && oldTeamData != this) {
            NekoTeamData other = (NekoTeamData) oldTeamData;
            NekoWallet otherWallet = other.getWallet();
            // 合并所有猫猫币余额
            for (String currencyId : otherWallet.getCurrencyIds()) {
                int amount = otherWallet.getCount(currencyId);
                if (amount > 0) {
                    wallet.addCount(currencyId, amount);
                }
            }
            for (Map.Entry<UUID, NekoTradeHistory> entry : other.getTradeHistoriesSnapshot()
                .entrySet()) {
                mergeTradeHistory(entry.getKey(), entry.getValue());
            }
            // If either side still has unconsumed player files, let the next
            // access migrate all current members after the merge.
            legacyHistoryMigrated = legacyHistoryMigrated && other.isLegacyHistoryMigrated();
            LOG.info("猫猫币团队钱包合并完成");
        }
    }

    /**
     * 玩家转移团队时调用
     * 仅团队钱包模式，无个人数据需要转移
     */
    @Override
    public synchronized void copyData(Team prevTeam, Team newTeam, UUID playerId, ITeamData prevTeamData,
        TeamDataCopyReason reason) {
        if (prevTeamData instanceof NekoTeamData && prevTeamData != this) {
            NekoTeamData previous = (NekoTeamData) prevTeamData;
            for (Map.Entry<UUID, NekoTradeHistory> entry : previous.getTradeHistoriesSnapshot()
                .entrySet()) {
                mergeTradeHistory(entry.getKey(), entry.getValue());
            }
            legacyHistoryMigrated = legacyHistoryMigrated && previous.isLegacyHistoryMigrated();
        }
        // 仅团队钱包，无个人数据迁移
    }
}

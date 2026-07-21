package com.miaokatze.gtit.lottery;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

/**
 * 抽奖历史（团队共享）
 * <p>
 * 按团队键存储，记录最近 {@link #MAX_RECORDS} 条抽取记录（超出丢弃最旧）。
 * 每条记录含卡池/条目/稀有度/数量/抽取玩家名/时间戳，供 GUI「最近中奖」滚动摘要展示。
 * <p>
 * NBT 序列化：{@link #writeToNBT()}/{@link #readFromNBT(NBTTagCompound)} 供
 * {@code LotteryManager} 持久化到 {@code <world>/gtit_lottery/<teamKey>.dat}。
 */
public class LotteryHistory {

    /** 最大记录条数（超出丢弃最旧） */
    private static final int MAX_RECORDS = 200;
    /** 记录列表（时间升序，末尾最新） */
    private final List<HistoryEntry> records;

    public LotteryHistory() {
        this.records = new ArrayList<>();
    }

    /**
     * 追加一条记录（超出容量丢弃最旧）
     */
    public void addRecord(HistoryEntry entry) {
        if (entry == null) return;
        records.add(entry);
        while (records.size() > MAX_RECORDS) {
            records.remove(0);
        }
    }

    /**
     * 取最近 count 条记录（时间倒序，最新在前）
     */
    public List<HistoryEntry> getRecentRecords(int count) {
        List<HistoryEntry> result = new ArrayList<>();
        for (int i = records.size() - 1; i >= 0 && result.size() < count; i--) {
            result.add(records.get(i));
        }
        return result;
    }

    /**
     * 统计指定稀有度的历史出货次数
     */
    public int getCountByRarity(LotteryRarity rarity) {
        if (rarity == null) return 0;
        int count = 0;
        for (HistoryEntry entry : records) {
            if (rarity.name()
                .equals(entry.rarityName)) {
                count++;
            }
        }
        return count;
    }

    /**
     * 历史总条数
     */
    public int size() {
        return records.size();
    }

    // ==================== NBT 序列化 ====================

    public NBTTagCompound writeToNBT() {
        NBTTagCompound tag = new NBTTagCompound();
        NBTTagList list = new NBTTagList();
        for (HistoryEntry entry : records) {
            NBTTagCompound entryTag = new NBTTagCompound();
            entryTag.setString("pool", entry.poolId == null ? "" : entry.poolId);
            entryTag.setString("entry", entry.entryId == null ? "" : entry.entryId);
            entryTag.setString("rarity", entry.rarityName == null ? "" : entry.rarityName);
            entryTag.setInteger("amount", entry.amount);
            entryTag.setString("player", entry.playerName == null ? "" : entry.playerName);
            entryTag.setLong("time", entry.timestamp);
            list.appendTag(entryTag);
        }
        tag.setTag("records", list);
        return tag;
    }

    public void readFromNBT(NBTTagCompound nbt) {
        records.clear();
        if (nbt == null || !nbt.hasKey("records")) return;
        NBTTagList list = nbt.getTagList("records", 10); // 10 = NBTTagCompound
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound entryTag = list.getCompoundTagAt(i);
            HistoryEntry entry = new HistoryEntry();
            entry.poolId = entryTag.getString("pool");
            entry.entryId = entryTag.getString("entry");
            entry.rarityName = entryTag.getString("rarity");
            entry.amount = entryTag.getInteger("amount");
            entry.playerName = entryTag.getString("player");
            entry.timestamp = entryTag.getLong("time");
            records.add(entry);
        }
    }

    /**
     * 单条历史记录（团队共享，playerName 记录是谁抽的）
     */
    public static class HistoryEntry {

        /** 卡池 ID */
        public String poolId;
        /** 条目 ID */
        public String entryId;
        /** 稀有度名（{@link LotteryRarity#name()}） */
        public String rarityName;
        /** 实际出货数量 */
        public int amount;
        /** 抽取玩家名（团队共享历史标识来源） */
        public String playerName;
        /** 时间戳（System.currentTimeMillis） */
        public long timestamp;

        public HistoryEntry() {}

        public HistoryEntry(String poolId, String entryId, String rarityName, int amount, String playerName,
            long timestamp) {
            this.poolId = poolId;
            this.entryId = entryId;
            this.rarityName = rarityName;
            this.amount = amount;
            this.playerName = playerName;
            this.timestamp = timestamp;
        }
    }
}

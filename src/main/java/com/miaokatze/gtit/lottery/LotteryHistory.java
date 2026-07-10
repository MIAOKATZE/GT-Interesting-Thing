package com.miaokatze.gtit.lottery;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.nbt.NBTTagCompound;

/**
 * 抽奖历史
 */
public class LotteryHistory {

    private static final int MAX_RECORDS = 200;
    private final List<HistoryEntry> records;

    public LotteryHistory() {
        this.records = new ArrayList<>();
    }

    public void addRecord(HistoryEntry entry) {
        // TODO: v1.6.4 实现
    }

    public List<HistoryEntry> getRecentRecords(int count) {
        // TODO: v1.6.4 实现
        return new ArrayList<>();
    }

    public int getCountByRarity(LotteryRarity rarity) {
        // TODO: v1.6.4 实现
        return 0;
    }

    public NBTTagCompound writeToNBT() {
        // TODO: v1.6.4 实现
        return null;
    }

    public void readFromNBT(NBTTagCompound nbt) {
        // TODO: v1.6.4 实现
    }

    public static class HistoryEntry {

        public String entryId;
        public String rarityName;
        public int amount;
        public long timestamp;
    }
}

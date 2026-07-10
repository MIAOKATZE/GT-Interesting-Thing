package com.miaokatze.gtit.lottery;

/**
 * 抽奖结果
 */
public class LotteryDrawResult {

    private final LotteryEntry entry;
    private final boolean isPity;
    private final boolean isHighRarity;

    public LotteryDrawResult(LotteryEntry entry, boolean isPity, boolean isHighRarity) {
        this.entry = entry;
        this.isPity = isPity;
        this.isHighRarity = isHighRarity;
    }

    public LotteryEntry getEntry() {
        return entry;
    }

    public boolean isPity() {
        return isPity;
    }

    public boolean isHighRarity() {
        return isHighRarity;
    }
}

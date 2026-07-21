package com.miaokatze.gtit.lottery;

/**
 * 抽奖结果（单次抽取）
 * <p>
 * 服务端 {@code LotteryManager.drawLottery} 产出，经 LotteryResultPacket 同步到客户端
 * 驱动轮盘动画（{@link #slotIndex} 为停留格索引）与结果展示。
 */
public class LotteryDrawResult {

    /** 中奖条目（含物品/货币信息与数量区间） */
    private final LotteryEntry entry;
    /** 是否由硬保底强制替换产生 */
    private final boolean isPity;
    /** 是否高稀有度（≥ RARE，用于保底计数器重置判定与客户端高亮） */
    private final boolean isHighRarity;
    /** 轮盘停留格索引（条目在池 entries 列表中的下标，-1 表示保底替换无对应格） */
    private final int slotIndex;
    /** 本次实际出货数量（[minAmount, maxAmount] 内已随机） */
    private final int amount;

    public LotteryDrawResult(LotteryEntry entry, boolean isPity, boolean isHighRarity, int slotIndex, int amount) {
        this.entry = entry;
        this.isPity = isPity;
        this.isHighRarity = isHighRarity;
        this.slotIndex = slotIndex;
        this.amount = amount;
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

    public int getSlotIndex() {
        return slotIndex;
    }

    public int getAmount() {
        return amount;
    }
}

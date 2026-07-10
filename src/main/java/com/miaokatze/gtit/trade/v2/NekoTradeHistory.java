package com.miaokatze.gtit.trade.v2;

import net.minecraft.nbt.NBTTagCompound;

/**
 * 交易历史记录，记录玩家对某交易组的交易次数和冷却
 * <p>
 * 每个玩家对每个交易组维护一份历史记录，包含：
 * <ul>
 * <li>lastTradeTime：上次交易时间（系统毫秒）</li>
 * <li>tradeCount：累计交易次数</li>
 * <li>cooldownTradeCount：冷却周期内的交易次数</li>
 * </ul>
 */
public class NekoTradeHistory {

    private long lastTradeTime;
    private int tradeCount;
    private int cooldownTradeCount;

    public NekoTradeHistory() {
        // TODO: v1.6.1 实现
    }

    /**
     * 检查是否可以进行交易（冷却是否已过）
     *
     * @param cooldown 冷却时间（秒）
     * @return 可以交易返回 true
     */
    public boolean canTrade(int cooldown) {
        // TODO: v1.6.1 实现
        return true;
    }

    /**
     * 获取剩余冷却时间
     *
     * @param cooldown 冷却时间（秒）
     * @return 剩余冷却时间（秒），已过冷却返回 0
     */
    public long getCooldownRemaining(int cooldown) {
        // TODO: v1.6.1 实现
        return 0;
    }

    /**
     * 记录一次交易
     */
    public void recordTrade() {
        // TODO: v1.6.1 实现
    }

    /**
     * 序列化到 NBT
     *
     * @return NBT 标签化合物
     */
    public NBTTagCompound writeToNBT() {
        // TODO: v1.6.1 实现
        return null;
    }

    /**
     * 从 NBT 反序列化
     *
     * @param nbt NBT 标签化合物
     */
    public void loadFromNBT(NBTTagCompound nbt) {
        // TODO: v1.6.1 实现
    }

    public long getLastTradeTime() {
        return lastTradeTime;
    }

    public int getTradeCount() {
        return tradeCount;
    }

    public int getCooldownTradeCount() {
        return cooldownTradeCount;
    }
}

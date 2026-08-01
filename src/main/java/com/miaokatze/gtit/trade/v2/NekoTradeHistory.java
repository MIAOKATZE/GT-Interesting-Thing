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

    /** 上次交易时间（系统毫秒），-1 表示从未交易 */
    private long lastTradeTime = -1;
    /** 累计交易次数 */
    private long tradeCount = 0;
    /** 当前冷却周期内的交易次数 */
    private long cooldownTradeCount = 0;
    // v1.6.28: 冷却完毕播报标记，true 表示有待发出的冷却完毕通知
    private boolean notificationQueued = false;

    public NekoTradeHistory() {}

    /**
     * 检查是否可以进行交易
     *
     * @param cooldown            冷却时间（秒）
     * @param maxTradesInCooldown 冷却周期内最大交易次数
     * @param maxTrades           总最大交易次数，-1 表示不限
     * @return 可以交易返回 true
     */
    public synchronized boolean canTrade(int cooldown, int maxTradesInCooldown, int maxTrades) {
        // 检查总交易次数限制
        if (maxTrades != -1 && tradeCount >= maxTrades) {
            return false;
        }
        // 检查冷却期内的交易次数限制
        if (cooldown > 0 && lastTradeTime > 0) {
            long elapsed = (System.currentTimeMillis() - lastTradeTime) / 1000;
            if (elapsed < cooldown && cooldownTradeCount >= maxTradesInCooldown) {
                return false;
            }
        }
        return true;
    }

    /**
     * 获取剩余冷却时间
     *
     * @param cooldown 冷却时间（秒）
     * @return 剩余冷却时间（秒），已过冷却返回 0
     */
    public synchronized long getCooldownRemaining(int cooldown) {
        if (cooldown <= 0 || lastTradeTime <= 0) {
            return 0;
        }
        long elapsed = (System.currentTimeMillis() - lastTradeTime) / 1000;
        long remaining = cooldown - elapsed;
        return remaining > 0 ? remaining : 0;
    }

    /**
     * 记录一次交易
     *
     * @param cooldown 冷却时间（秒），用于判断是否需要重置冷却计数
     */
    public synchronized void recordTrade(int cooldown) {
        // 冷却已过，重置冷却期交易计数
        if (cooldown > 0 && lastTradeTime > 0) {
            long elapsed = (System.currentTimeMillis() - lastTradeTime) / 1000;
            if (elapsed >= cooldown) {
                cooldownTradeCount = 0;
            }
        }
        lastTradeTime = System.currentTimeMillis();
        tradeCount++;
        cooldownTradeCount++;
    }

    /**
     * 重置所有历史记录
     */
    public synchronized void reset() {
        lastTradeTime = -1;
        tradeCount = 0;
        cooldownTradeCount = 0;
        // v1.6.28: 重置冷却完毕通知标记
        notificationQueued = false;
    }

    /**
     * Creates a consistent deep copy of this history.
     */
    public synchronized NekoTradeHistory copy() {
        NekoTradeHistory result = new NekoTradeHistory();
        result.lastTradeTime = lastTradeTime;
        result.tradeCount = tradeCount;
        result.cooldownTradeCount = cooldownTradeCount;
        result.notificationQueued = notificationQueued;
        return result;
    }

    /**
     * Replaces this history with a consistent copy of another history.
     */
    public void copyFrom(NekoTradeHistory other) {
        if (other == this) {
            return;
        }
        if (other == null) {
            reset();
            return;
        }

        NekoTradeHistory snapshot = other.copy();
        synchronized (this) {
            lastTradeTime = snapshot.lastTradeTime;
            tradeCount = snapshot.tradeCount;
            cooldownTradeCount = snapshot.cooldownTradeCount;
            notificationQueued = snapshot.notificationQueued;
        }
    }

    /**
     * Merges another history into this one. Counts are added, the latest
     * timestamp wins, and a queued notification is retained if either
     * history has one.
     */
    public void mergeFrom(NekoTradeHistory other) {
        if (other == null || other == this) {
            return;
        }

        NekoTradeHistory snapshot = other.copy();
        synchronized (this) {
            tradeCount += snapshot.tradeCount;
            cooldownTradeCount += snapshot.cooldownTradeCount;
            if (snapshot.lastTradeTime > lastTradeTime) {
                lastTradeTime = snapshot.lastTradeTime;
            }
            notificationQueued = notificationQueued || snapshot.notificationQueued;
        }
    }

    /**
     * 序列化到 NBT
     *
     * @return NBT 标签化合物
     */
    public synchronized NBTTagCompound writeToNBT() {
        NBTTagCompound nbt = new NBTTagCompound();
        nbt.setLong("lastTradeTime", lastTradeTime);
        nbt.setLong("tradeCount", tradeCount);
        nbt.setLong("cooldownTradeCount", cooldownTradeCount);
        // v1.6.28: 持久化冷却完毕通知标记
        nbt.setBoolean("notificationQueued", notificationQueued);
        return nbt;
    }

    /**
     * 从 NBT 反序列化
     *
     * @param nbt NBT 标签化合物
     */
    public synchronized void loadFromNBT(NBTTagCompound nbt) {
        if (nbt == null) {
            return;
        }
        lastTradeTime = nbt.getLong("lastTradeTime");
        tradeCount = nbt.getLong("tradeCount");
        cooldownTradeCount = nbt.getLong("cooldownTradeCount");
        // v1.6.28: 读取冷却完毕通知标记（旧存档无此 key 时返回 false，向后兼容）
        notificationQueued = nbt.getBoolean("notificationQueued");
    }

    public synchronized long getLastTradeTime() {
        return lastTradeTime;
    }

    public synchronized long getTradeCount() {
        return tradeCount;
    }

    public synchronized long getCooldownTradeCount() {
        return cooldownTradeCount;
    }

    /** v1.6.28: 是否有待发出的冷却完毕通知 */
    public synchronized boolean isNotificationQueued() {
        return notificationQueued;
    }

    /** v1.6.28: 设置冷却完毕通知标记 */
    public synchronized void setNotificationQueued(boolean queued) {
        this.notificationQueued = queued;
    }
}

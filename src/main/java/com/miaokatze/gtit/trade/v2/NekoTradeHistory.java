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

    /** Only consume/copy merges acquire more than one history monitor. */
    private static final Object MERGE_LOCK = new Object();
    /** A completed in-flight trade still belongs to the team surviving a merge. */
    private NekoTradeHistory successor;

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
     * @param cooldown            冷却时间（秒）；-1 表示仅允许一次成功交易
     * @param maxTradesInCooldown 冷却周期内最大交易次数
     * @param maxTrades           总最大交易次数，-1 表示不限
     * @return 可以交易返回 true
     */
    public synchronized boolean canTrade(int cooldown, int maxTradesInCooldown, int maxTrades) {
        if (successor != null) return successor.canTrade(cooldown, maxTradesInCooldown, maxTrades);
        com.miaokatze.gtit.trade.NekoTradeEntry.validateCooldown(cooldown);
        // Lifetime success count is already shared/persisted by the personal or team history owner.
        if (cooldown == -1 && tradeCount > 0) return false;
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
        if (successor != null) return successor.getCooldownRemaining(cooldown);
        com.miaokatze.gtit.trade.NekoTradeEntry.validateCooldown(cooldown);
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
        if (successor != null) {
            successor.recordTrade(cooldown);
            return;
        }
        com.miaokatze.gtit.trade.NekoTradeEntry.validateCooldown(cooldown);
        // 冷却已过，重置冷却期交易计数
        if (cooldown > 0 && lastTradeTime > 0) {
            long elapsed = (System.currentTimeMillis() - lastTradeTime) / 1000;
            if (elapsed >= cooldown) {
                cooldownTradeCount = 0;
            }
        }
        lastTradeTime = System.currentTimeMillis();
        tradeCount = saturatingAdd(tradeCount, 1);
        cooldownTradeCount = saturatingAdd(cooldownTradeCount, 1);
    }

    /**
     * 重置所有历史记录
     */
    public synchronized void reset() {
        if (successor != null) {
            successor.reset();
            return;
        }
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
        if (successor != null) return successor.copy();
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
            if (successor != null) {
                successor.copyFrom(snapshot);
                return;
            }
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
        if (other == null) return;
        synchronized (MERGE_LOCK) {
            NekoTradeHistory source = other.root(), target = root();
            if (source == target) return;
            NekoTradeHistory snapshot = source.copy();
            synchronized (target) {
                target.mergeSnapshot(snapshot);
            }
        }
    }

    /** Consume once, forwarding late completions; call without either history monitor held. */
    public void transferAllTo(NekoTradeHistory destination) {
        if (destination == null) throw new IllegalArgumentException("destination");
        synchronized (MERGE_LOCK) {
            synchronized (this) {
                if (successor != null) return;
            }
            NekoTradeHistory target = destination.root();
            if (target == this) return;
            synchronized (this) {
                synchronized (target) {
                    target.mergeSnapshot(this);
                    lastTradeTime = -1;
                    tradeCount = cooldownTradeCount = 0;
                    notificationQueued = false;
                    successor = target;
                }
            }
        }
    }

    private void mergeSnapshot(NekoTradeHistory snapshot) {
        tradeCount = saturatingAdd(tradeCount, snapshot.tradeCount);
        cooldownTradeCount = saturatingAdd(cooldownTradeCount, snapshot.cooldownTradeCount);
        lastTradeTime = Math.max(lastTradeTime, snapshot.lastTradeTime);
        notificationQueued |= snapshot.notificationQueued;
    }

    private static long saturatingAdd(long a, long b) {
        return a > Long.MAX_VALUE - b ? Long.MAX_VALUE : a + b;
    }

    /** Called only while MERGE_LOCK prevents successor installation. */
    private synchronized NekoTradeHistory root() {
        return successor == null ? this : successor.root();
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
        if (successor != null) throw new IllegalStateException("Cannot reload a consumed team history");
        if (nbt == null) {
            return;
        }
        for (String key : new String[] { "lastTradeTime", "tradeCount", "cooldownTradeCount" }) {
            if (!nbt.hasKey(key, 3) && !nbt.hasKey(key, 4)) throw new IllegalArgumentException("Invalid history " + key);
        }
        long time = nbt.getLong("lastTradeTime"), count = nbt.getLong("tradeCount");
        long cooldownCount = nbt.getLong("cooldownTradeCount");
        if (time < -1 || count < 0 || cooldownCount < 0 || cooldownCount > count || (count > 0 && time < 0)) {
            throw new IllegalArgumentException("Invalid history counters");
        }
        if (nbt.hasKey("notificationQueued") && !nbt.hasKey("notificationQueued", 1)) {
            throw new IllegalArgumentException("Invalid history notification marker");
        }
        lastTradeTime = time;
        tradeCount = count;
        cooldownTradeCount = cooldownCount;
        notificationQueued = nbt.getBoolean("notificationQueued");
    }

    public synchronized long getLastTradeTime() {
        if (successor != null) return successor.getLastTradeTime();
        return lastTradeTime;
    }

    public synchronized long getTradeCount() {
        if (successor != null) return successor.getTradeCount();
        return tradeCount;
    }

    public synchronized long getCooldownTradeCount() {
        if (successor != null) return successor.getCooldownTradeCount();
        return cooldownTradeCount;
    }

    /** v1.6.28: 是否有待发出的冷却完毕通知 */
    public synchronized boolean isNotificationQueued() {
        if (successor != null) return successor.isNotificationQueued();
        return notificationQueued;
    }

    /** v1.6.28: 设置冷却完毕通知标记 */
    public synchronized void setNotificationQueued(boolean queued) {
        if (successor != null) {
            successor.setNotificationQueued(queued);
            return;
        }
        this.notificationQueued = queued;
    }
}

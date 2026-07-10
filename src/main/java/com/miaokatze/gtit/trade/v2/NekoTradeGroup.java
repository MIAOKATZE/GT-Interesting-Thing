package com.miaokatze.gtit.trade.v2;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import net.minecraft.nbt.NBTTagCompound;

/**
 * 交易组，替代 VM 的 TradeGroup
 * <p>
 * 包含一组交易（trades）及其冷却时间（cooldown）、最大交易次数（maxTrades）、
 * 前置条件（requirementSet）、分类（category）、标签页（tabId）、排序（orderId）
 * 以及 BQ 任务绑定（bqQuestId）。
 * <p>
 * 使用 CopyOnWriteArrayList 保证线程安全，适用于多线程并发读场景。
 */
public class NekoTradeGroup {

    private UUID id;
    private CopyOnWriteArrayList<NekoTrade> trades;
    private int cooldown;
    private int maxTrades;
    private NekoTradeCategory category;
    private String tabId;
    private int orderId;
    private String bqQuestId;
    private Set<NekoTradeCondition> requirementSet;

    public NekoTradeGroup() {
        // TODO: v1.6.1 实现
    }

    /**
     * 检查指定玩家是否满足所有前置条件
     *
     * @param playerId 玩家 UUID
     * @return 全部满足返回 true
     */
    public boolean isConditionsSatisfied(UUID playerId) {
        // TODO: v1.6.1 实现
        return true;
    }

    /**
     * 添加前置条件
     *
     * @param condition 交易条件
     */
    public void addCondition(NekoTradeCondition condition) {
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

    // --- Getters ---

    public UUID getId() {
        return id;
    }

    public CopyOnWriteArrayList<NekoTrade> getTrades() {
        return trades;
    }

    public int getCooldown() {
        return cooldown;
    }

    public int getMaxTrades() {
        return maxTrades;
    }

    public NekoTradeCategory getCategory() {
        return category;
    }

    public String getTabId() {
        return tabId;
    }

    public int getOrderId() {
        return orderId;
    }

    public String getBqQuestId() {
        return bqQuestId;
    }
}

package com.miaokatze.gtit.trade.v2;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 交易数据库单例，替代 VM 的 TradeDatabase
 * <p>
 * 管理所有交易组（NekoTradeGroup），支持以下查询方式：
 * <ul>
 * <li>按 UUID 查询单个交易组</li>
 * <li>按标签页（tabId）查询交易组列表</li>
 * <li>查询无条件的交易组列表</li>
 * </ul>
 * 使用 ConcurrentHashMap 保证线程安全。
 */
public class NekoTradeDatabase {

    /** 单例实例 */
    public static final NekoTradeDatabase INSTANCE = new NekoTradeDatabase();

    /** 按UUID索引的所有交易组 */
    private final ConcurrentHashMap<UUID, NekoTradeGroup> tradeGroups;
    /** 按标签页索引的交易组列表 */
    private final ConcurrentHashMap<String, CopyOnWriteArrayList<NekoTradeGroup>> tradeGroupsByTab;
    /** 无前置条件的交易组列表（快速查询缓存） */
    private final CopyOnWriteArrayList<NekoTradeGroup> noConditionTrades;

    private NekoTradeDatabase() {
        this.tradeGroups = new ConcurrentHashMap<>();
        this.tradeGroupsByTab = new ConcurrentHashMap<>();
        this.noConditionTrades = new CopyOnWriteArrayList<>();
    }

    /**
     * 添加交易组到数据库
     *
     * @param group 交易组
     */
    public void addTradeGroup(NekoTradeGroup group) {
        // TODO: v1.6.1 实现
    }

    /**
     * 按标签页查询交易组列表
     *
     * @param tabId 标签页ID
     * @return 交易组列表（可能为空，不会为 null）
     */
    public List<NekoTradeGroup> getTradeGroupsByTab(String tabId) {
        // TODO: v1.6.1 实现
        return new ArrayList<>();
    }

    /**
     * 按 UUID 查询交易组
     *
     * @param id 交易组UUID
     * @return 交易组，不存在返回 null
     */
    public NekoTradeGroup getTradeGroup(UUID id) {
        // TODO: v1.6.1 实现
        return null;
    }

    /**
     * 清空所有交易组
     */
    public void clear() {
        // TODO: v1.6.1 实现
        tradeGroups.clear();
        tradeGroupsByTab.clear();
        noConditionTrades.clear();
    }

    /**
     * 获取交易组总数
     *
     * @return 交易组数量
     */
    public int getTradeGroupCount() {
        return tradeGroups.size();
    }

    /**
     * 获取所有交易组
     *
     * @return 交易组列表（副本）
     */
    public List<NekoTradeGroup> getAllTradeGroups() {
        // TODO: v1.6.1 实现
        return new ArrayList<>(tradeGroups.values());
    }

    /**
     * 获取无前置条件的交易组列表
     *
     * @return 交易组列表（副本）
     */
    public List<NekoTradeGroup> getNoConditionTrades() {
        // TODO: v1.6.1 实现
        return new ArrayList<>(noConditionTrades);
    }
}

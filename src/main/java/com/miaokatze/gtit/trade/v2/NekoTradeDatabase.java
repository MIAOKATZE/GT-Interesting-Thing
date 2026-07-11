package com.miaokatze.gtit.trade.v2;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

    /** 按 UUID 索引的所有交易组 */
    private final ConcurrentHashMap<UUID, NekoTradeGroup> tradeGroups;
    /** 按标签页索引的交易组列表 */
    private final ConcurrentHashMap<Integer, CopyOnWriteArrayList<NekoTradeGroup>> tradeGroupsByTab;
    /** 无前置条件的交易组列表（快速查询缓存） */
    private final CopyOnWriteArrayList<NekoTradeGroup> noConditionTrades;

    private NekoTradeDatabase() {
        this.tradeGroups = new ConcurrentHashMap<>();
        this.tradeGroupsByTab = new ConcurrentHashMap<>();
        this.noConditionTrades = new CopyOnWriteArrayList<>();
    }

    /**
     * 添加交易组到数据库
     * <p>
     * 如果同 UUID 的交易组已存在，先移除旧组再添加新组，确保数据一致性。
     *
     * @param group 交易组
     */
    public void addTradeGroup(NekoTradeGroup group) {
        UUID groupId = group.getId();
        // 如果已存在同 UUID 的组，先移除旧组（从三个集合中清除）
        if (tradeGroups.containsKey(groupId)) {
            removeTradeGroup(groupId);
        }
        tradeGroups.put(groupId, group);
        // 按标签页索引
        tradeGroupsByTab.computeIfAbsent(group.getTabId(), k -> new CopyOnWriteArrayList<>())
            .add(group);
        // 无条件交易组加入快速查询缓存
        if (group.hasNoConditions()) {
            noConditionTrades.add(group);
        }
    }

    /**
     * 移除指定 UUID 的交易组
     * <p>
     * 从 tradeGroups、tradeGroupsByTab、noConditionTrades 三个集合中同步移除。
     *
     * @param groupId 交易组 UUID
     */
    public void removeTradeGroup(UUID groupId) {
        NekoTradeGroup group = tradeGroups.remove(groupId);
        if (group != null) {
            // 从标签页索引中移除
            CopyOnWriteArrayList<NekoTradeGroup> tabList = tradeGroupsByTab.get(group.getTabId());
            if (tabList != null) {
                tabList.remove(group);
                // 列表为空时移除 key，避免空集合残留
                if (tabList.isEmpty()) {
                    tradeGroupsByTab.remove(group.getTabId());
                }
            }
            // 从无条件缓存中移除
            noConditionTrades.remove(group);
        }
    }

    /**
     * 按标签页查询交易组列表
     * <p>
     * 返回按 orderId 升序排列的列表副本，对返回列表的修改不影响数据库内部状态。
     *
     * @param tabId 标签页 ID
     * @return 交易组列表（已排序，不会为 null）
     */
    public List<NekoTradeGroup> getTradeGroupsByTab(int tabId) {
        CopyOnWriteArrayList<NekoTradeGroup> list = tradeGroupsByTab.get(tabId);
        if (list == null || list.isEmpty()) {
            return new ArrayList<>();
        }
        // 创建副本并按 orderId 排序
        List<NekoTradeGroup> sorted = new ArrayList<>(list);
        Collections.sort(sorted, Comparator.comparingInt(NekoTradeGroup::getOrderId));
        return sorted;
    }

    /**
     * 按 UUID 查询交易组
     *
     * @param id 交易组 UUID
     * @return 交易组，不存在返回 null
     */
    public NekoTradeGroup getTradeGroup(UUID id) {
        return tradeGroups.get(id);
    }

    /**
     * 清空所有交易组
     */
    public void clear() {
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
     * 获取所有交易组的不可变视图
     *
     * @return UUID -> 交易组 的不可变 Map 视图
     */
    public Map<UUID, NekoTradeGroup> getAllTradeGroups() {
        return Collections.unmodifiableMap(tradeGroups);
    }

    /**
     * 获取无前置条件的交易组列表
     *
     * @return 交易组列表（副本）
     */
    public List<NekoTradeGroup> getNoConditionTrades() {
        return new ArrayList<>(noConditionTrades);
    }

    /**
     * 获取所有已使用的标签页 ID
     *
     * @return 标签页 ID 集合
     */
    public Set<Integer> getUsedTabIds() {
        return tradeGroupsByTab.keySet();
    }

    /**
     * 获取所有交易组 UUID
     *
     * @return UUID 集合
     */
    public Set<UUID> getAllTradeGroupIds() {
        return tradeGroups.keySet();
    }

    /**
     * 获取所有交易组中的交易总数
     *
     * @return 交易总数
     */
    public int getTradeCount() {
        int count = 0;
        for (NekoTradeGroup group : tradeGroups.values()) {
            count += group.getTrades()
                .size();
        }
        return count;
    }
}

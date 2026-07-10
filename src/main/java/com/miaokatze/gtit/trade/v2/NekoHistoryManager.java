package com.miaokatze.gtit.trade.v2;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 历史记录管理器单例
 * <p>
 * 管理所有玩家的交易历史，按双层 Map 组织：
 * 外层 key 为玩家 UUID，内层 key 为交易组 UUID，value 为交易历史记录。
 * 使用 ConcurrentHashMap 保证线程安全。
 */
public class NekoHistoryManager {

    /** 单例实例 */
    public static final NekoHistoryManager INSTANCE = new NekoHistoryManager();

    /** 玩家交易历史：playerId -> (tradeGroupId -> history) */
    private final ConcurrentHashMap<UUID, ConcurrentHashMap<UUID, NekoTradeHistory>> histories;

    private NekoHistoryManager() {
        this.histories = new ConcurrentHashMap<>();
    }

    /**
     * 获取指定玩家对指定交易组的历史记录
     *
     * @param playerId     玩家 UUID
     * @param tradeGroupId 交易组 UUID
     * @return 交易历史记录，不存在返回 null
     */
    public NekoTradeHistory getHistory(UUID playerId, UUID tradeGroupId) {
        // TODO: v1.6.1 实现
        return null;
    }

    /**
     * 记录一次交易
     *
     * @param playerId     玩家 UUID
     * @param tradeGroupId 交易组 UUID
     */
    public void recordTrade(UUID playerId, UUID tradeGroupId) {
        // TODO: v1.6.1 实现
    }

    /**
     * 重置指定玩家对指定交易组的历史记录
     *
     * @param playerId     玩家 UUID
     * @param tradeGroupId 交易组 UUID
     */
    public void resetHistory(UUID playerId, UUID tradeGroupId) {
        // TODO: v1.6.1 实现
    }

    /**
     * 卸载指定玩家的所有历史记录（玩家退出时调用）
     *
     * @param playerId 玩家 UUID
     */
    public void unloadPlayer(UUID playerId) {
        // TODO: v1.6.1 实现
    }

    /**
     * 清空所有历史记录
     */
    public void clearAll() {
        // TODO: v1.6.1 实现
        histories.clear();
    }
}

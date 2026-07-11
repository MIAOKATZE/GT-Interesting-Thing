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
     * <p>
     * 使用 computeIfAbsent 两层自动创建空历史记录，保证永远不会返回 null。
     *
     * @param playerId     玩家 UUID
     * @param tradeGroupId 交易组 UUID
     * @return 交易历史记录（自动创建空历史，不为 null）
     */
    public NekoTradeHistory getHistory(UUID playerId, UUID tradeGroupId) {
        // 外层：按玩家查找或创建内层 Map
        // 内层：按交易组查找或创建 NekoTradeHistory
        return histories.computeIfAbsent(playerId, k -> new ConcurrentHashMap<>())
            .computeIfAbsent(tradeGroupId, k -> new NekoTradeHistory());
    }

    /**
     * 重置指定玩家对指定交易组的历史记录
     *
     * @param playerId     玩家 UUID
     * @param tradeGroupId 交易组 UUID
     */
    public void resetHistory(UUID playerId, UUID tradeGroupId) {
        NekoTradeHistory history = getHistory(playerId, tradeGroupId);
        history.reset();
        markDirty(playerId);
    }

    /**
     * 重置指定玩家的所有交易历史记录
     * <p>
     * 遍历玩家名下的所有交易组历史，逐一调用 reset()。
     *
     * @param playerId 玩家 UUID
     */
    public void resetAllHistory(UUID playerId) {
        ConcurrentHashMap<UUID, NekoTradeHistory> playerHistories = histories.get(playerId);
        if (playerHistories != null) {
            for (NekoTradeHistory history : playerHistories.values()) {
                history.reset();
            }
        }
        markDirty(playerId);
    }

    /**
     * 标记指定玩家的历史数据为脏（需持久化）
     * <p>
     * 当前为空实现，v1.6.2 将对接 NekoTeamData 持久化系统。
     *
     * @param playerId 玩家 UUID
     */
    public void markDirty(UUID playerId) {
        // TODO: v1.6.2 对接 NekoTeamData 持久化
    }

    /**
     * 卸载指定玩家的所有历史记录（玩家退出时调用）
     * <p>
     * 先标记脏数据（触发持久化），再从内存中移除。
     *
     * @param playerId 玩家 UUID
     */
    public void unloadPlayer(UUID playerId) {
        markDirty(playerId);
        histories.remove(playerId);
    }

    /**
     * 清空所有历史记录
     */
    public void clearAll() {
        histories.clear();
    }
}

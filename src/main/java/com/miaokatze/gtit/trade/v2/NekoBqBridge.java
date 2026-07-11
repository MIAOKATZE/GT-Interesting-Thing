package com.miaokatze.gtit.trade.v2;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.miaokatze.gtit.main.GTInterestingThing;

import cpw.mods.fml.common.Optional;

/**
 * BQ 桥接器，替代 VM 的 BqAdapter
 * <p>
 * 直接对接 BetterQuesting API，带 @Optional 防护，
 * 在 BQ 未加载时安全降级（返回 true / 不执行操作）。
 * <p>
 * 通过 {@link #init()} 检测 BQ 是否加载，
 * 通过 {@link #isQuestCompleted(UUID, UUID)} 查询任务完成状态。
 * <p>
 * 内部维护两个缓存：
 * <ul>
 * <li>{@code questTriggers}：questId → 关联交易组ID集合，用于任务完成时触发交易组刷新</li>
 * <li>{@code completedQuestsCache}：playerId → 已完成questId集合，避免重复查询 BQ API</li>
 * </ul>
 */
public class NekoBqBridge {

    /** BQ 是否已加载 */
    private static boolean bqLoaded = false;

    /** questId → 关联交易组ID集合（任务完成时需刷新的交易组） */
    private static final Map<UUID, Set<UUID>> questTriggers = new ConcurrentHashMap<>();

    /** playerId → 已完成questId集合（查询缓存，避免重复调用 BQ API） */
    private static final Map<UUID, Set<UUID>> completedQuestsCache = new ConcurrentHashMap<>();

    /**
     * 检查 BQ 是否已加载
     *
     * @return 已加载返回 true
     */
    public static boolean isBqLoaded() {
        return bqLoaded;
    }

    /**
     * 检查指定玩家是否已完成指定任务
     * <p>
     * 带 @Optional 防护，BQ 未加载时方法体不会被调用。
     * 内部带 try-catch NoClassDefFoundError 防护，确保运行时安全。
     * <p>
     * 查询流程：
     * <ol>
     * <li>先检查 {@code completedQuestsCache}，命中则直接返回 true</li>
     * <li>BQ 未加载时返回 true（安全回退，不阻断交易）</li>
     * <li>调用 BQ API 查询任务状态</li>
     * <li>查询成功且任务已完成时更新缓存</li>
     * </ol>
     *
     * @param playerId 玩家 UUID
     * @param questId  任务 UUID
     * @return 已完成返回 true，未完成返回 false，BQ 未加载或异常返回 true（安全回退）
     */
    @Optional.Method(modid = "betterquesting")
    public static boolean isQuestCompleted(UUID playerId, UUID questId) {
        // 1. 先检查缓存，命中则直接返回
        Set<UUID> completed = completedQuestsCache.get(playerId);
        if (completed != null && completed.contains(questId)) {
            return true;
        }

        // 2. BQ 未加载时安全回退（不阻断交易）
        if (!bqLoaded) {
            return true;
        }

        // 3. 调用 BQ API 查询任务状态
        try {
            // 使用全限定名调用，避免类加载问题
            betterquesting.api.questing.IQuest quest = betterquesting.questing.QuestDatabase.INSTANCE.get(questId);
            if (quest == null) {
                // 任务不存在，安全回退
                return true;
            }
            boolean result = quest.isComplete(playerId);
            // 4. 查询成功且任务已完成时更新缓存
            if (result) {
                setQuestCompleted(playerId, questId);
            }
            return result;
        } catch (NoClassDefFoundError e) {
            // BQ 类缺失（版本不匹配等），安全回退
            GTInterestingThing.LOG.warn("BQ 类加载失败，任务完成检查安全回退: {}", e.getMessage());
            return true;
        } catch (Exception e) {
            // 其他异常，安全回退
            GTInterestingThing.LOG.warn("BQ 任务完成检查异常，安全回退: {}", e.getMessage());
            return true;
        }
    }

    /**
     * 注册任务触发器
     * <p>
     * 将交易组ID关联到任务ID，当任务完成时可触发对应交易组刷新。
     *
     * @param questId 任务 UUID
     * @param groupId 交易组 UUID
     */
    public static void registerQuestTrigger(UUID questId, UUID groupId) {
        questTriggers.computeIfAbsent(questId, k -> ConcurrentHashMap.newKeySet())
            .add(groupId);
    }

    /**
     * 清除所有任务触发器
     */
    public static void clearAllTriggers() {
        questTriggers.clear();
    }

    /**
     * 获取指定任务关联的交易组集合
     *
     * @param questId 任务 UUID
     * @return 关联的交易组ID集合，无关联时返回空集合
     */
    public static Set<UUID> getTriggeredGroups(UUID questId) {
        Set<UUID> groups = questTriggers.get(questId);
        if (groups == null) {
            return java.util.Collections.emptySet();
        }
        return groups;
    }

    /**
     * 标记任务为已完成（更新缓存）
     *
     * @param playerId 玩家 UUID
     * @param questId  任务 UUID
     */
    public static void setQuestCompleted(UUID playerId, UUID questId) {
        completedQuestsCache.computeIfAbsent(playerId, k -> ConcurrentHashMap.newKeySet())
            .add(questId);
    }

    /**
     * 标记任务为未完成（从缓存中移除）
     *
     * @param playerId 玩家 UUID
     * @param questId  任务 UUID
     */
    public static void setQuestUncompleted(UUID playerId, UUID questId) {
        Set<UUID> completed = completedQuestsCache.get(playerId);
        if (completed != null) {
            completed.remove(questId);
        }
    }

    /**
     * 初始化 BQ 桥接器
     * <p>
     * 检测 BQ 是否加载，通过反射检查 BQ API 类是否存在。
     */
    public static void init() {
        try {
            Class.forName("betterquesting.api.questing.IQuest");
            bqLoaded = true;
        } catch (ClassNotFoundException e) {
            bqLoaded = false;
        }
    }
}

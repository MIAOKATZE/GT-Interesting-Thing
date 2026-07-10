package com.miaokatze.gtit.trade.v2;

import java.util.UUID;

import cpw.mods.fml.common.Optional;

/**
 * BQ 桥接器，替代 VM 的 BqAdapter
 * <p>
 * 直接对接 BetterQuesting API，带 @Optional 防护，
 * 在 BQ 未加载时安全降级（返回 false / 不执行操作）。
 * <p>
 * 通过 {@link #init()} 检测 BQ 是否加载，
 * 通过 {@link #isQuestCompleted(UUID, String)} 查询任务完成状态。
 */
public class NekoBqBridge {

    /** BQ 是否已加载 */
    private static boolean bqLoaded = false;

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
     *
     * @param playerId 玩家 UUID
     * @param questId  任务 ID
     * @return 已完成返回 true，未完成或 BQ 未加载返回 false
     */
    @Optional.Method(modid = "betterquesting")
    public static boolean isQuestCompleted(UUID playerId, String questId) {
        // TODO: v1.6.1 实现，带 try-catch NoClassDefFoundError 防护
        return false;
    }

    /**
     * 注册任务触发器
     *
     * @param playerId 玩家 UUID
     * @param questId  任务 ID
     */
    public static void registerQuestTrigger(UUID playerId, String questId) {
        // TODO: v1.6.1 实现
    }

    /**
     * 初始化 BQ 桥接器
     * <p>
     * 检测 BQ 是否加载，通过反射检查 BQ API 类是否存在。
     */
    public static void init() {
        // TODO: v1.6.1 实现，检测 BQ 是否加载
        try {
            Class.forName("betterquesting.api.questing.IQuest");
            bqLoaded = true;
        } catch (ClassNotFoundException e) {
            bqLoaded = false;
        }
    }
}

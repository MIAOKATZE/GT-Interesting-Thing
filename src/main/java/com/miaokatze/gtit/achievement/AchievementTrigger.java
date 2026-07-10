package com.miaokatze.gtit.achievement;

import java.util.UUID;

/**
 * 成就触发辅助类
 * 各模块通过此类触发成就更新
 */
public class AchievementTrigger {

    private AchievementTrigger() {}

    public static void onSignIn(UUID playerId, int totalDays) {
        // TODO: v1.6.5 实现
    }

    public static void onLottery(UUID playerId, int totalCount) {
        // TODO: v1.6.5 实现
    }

    public static void onLotteryRarity(UUID playerId, String rarityName) {
        // TODO: v1.6.5 实现
    }

    public static void onTrade(UUID playerId, int totalCount) {
        // TODO: v1.6.5 实现
    }

    public static void onWalletChange(UUID playerId) {
        // TODO: v1.6.5 实现
    }

    public static void onPlayerLogin(UUID playerId, int loginCount) {
        // TODO: v1.6.5 实现
    }

    public static void updateCustom(UUID playerId, String achievementId, int value) {
        // TODO: v1.6.5 实现
    }

    public static void incrementCustom(UUID playerId, String achievementId, int increment) {
        // TODO: v1.6.5 实现
    }

    private static boolean isReady() {
        return AchievementManager.INSTANCE.isInitialized();
    }
}

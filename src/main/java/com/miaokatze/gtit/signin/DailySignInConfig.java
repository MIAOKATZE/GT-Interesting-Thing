package com.miaokatze.gtit.signin;

import java.util.ArrayList;
import java.util.List;

/**
 * 签到配置模型
 * 管理签到奖励配置的加载和查询
 */
public class DailySignInConfig {

    private static List<SignInRewardTier> rewardTiers = new ArrayList<>();
    private static int baseRewardNeko = 10;
    private static double consecutiveIncrement = 1.0;

    public static class ConfigData {

        public int baseReward;
        public double consecutiveIncrement;
        public List<TierData> tiers;
    }

    public static class TierData {

        public int days;
        public String currency;
        public int amount;
    }

    public static void init() {
        // TODO: v1.6.3 实现
    }

    public static void loadConfig() {
        // TODO: v1.6.3 实现
    }

    public static void saveConfig() {
        // TODO: v1.6.3 实现
    }

    public static void reload() {
        // TODO: v1.6.3 实现
    }

    public static int calculateBaseReward(int consecutiveDays) {
        // TODO: v1.6.3 实现
        return 0;
    }

    public static List<SignInRewardTier> getRewardTiers() {
        return rewardTiers;
    }

    public static SignInRewardTier getTriggeredTier(int consecutiveDays) {
        // TODO: v1.6.3 实现
        return null;
    }
}

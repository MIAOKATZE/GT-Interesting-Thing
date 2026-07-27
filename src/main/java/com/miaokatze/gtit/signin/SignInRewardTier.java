package com.miaokatze.gtit.signin;

/**
 * 签到奖励层级（v1.7.8 任务5+6 重构：统一奖励模型）
 * <p>
 * 达到指定天数条件时触发的奖励，奖励内容由 {@link SignInReward} 统一表达
 * （货币 + 物品列表，替代旧的单物品字段组）。
 * <p>
 * 本类同时承载两种档位：
 * <ul>
 * <li><b>连续签到阶梯</b>（{@link DailySignInConfig#getRewardTiers()}）：requiredDays = 所需连续天数，
 * 当月每档限领一次（领取记录含年月键，跨月可再领）</li>
 * <li><b>累计签到阶梯</b>（{@link DailySignInConfig#getCumulativeTiers()}）：requiredDays = 所需累计天数，
 * 永久每档限领一次（领取记录不清空）</li>
 * </ul>
 */
public class SignInRewardTier {

    /** 所需天数（连续阶梯=连续天数；累计阶梯=累计天数） */
    private final int requiredDays;
    /** 奖励内容（货币 + 物品列表，统一模型） */
    private final SignInReward reward;

    /**
     * 构造签到奖励层级
     *
     * @param requiredDays 所需天数（≥1）
     * @param reward       奖励内容（null 按 {@link SignInReward#EMPTY} 处理）
     */
    public SignInRewardTier(int requiredDays, SignInReward reward) {
        this.requiredDays = Math.max(1, requiredDays);
        this.reward = reward == null ? SignInReward.EMPTY : reward;
    }

    public int getRequiredDays() {
        return requiredDays;
    }

    /** 奖励内容（不会为 null） */
    public SignInReward getReward() {
        return reward;
    }
}

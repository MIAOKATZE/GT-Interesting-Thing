package com.miaokatze.gtit.lottery;

/**
 * 保底配置
 * <p>
 * 每个奖池可独立配置软保底与硬保底。
 * <ul>
 * <li>软保底：超过阈值后每次抽取逐步增加稀有度权重倍率，提高高稀有度出货概率（不保证）。</li>
 * <li>硬保底：累计抽取达到阈值后，下一次必定出指定稀有度及以上（强制替换）。</li>
 * </ul>
 */
public class PityConfig {

    /** 是否启用保底 */
    private boolean enabled = true;
    /** 软保底阈值（累计未出高稀有的次数） */
    private int softPityThreshold = 30;
    /** 软保底每次递增的权重倍率 */
    private double softPityIncrement = 5.0;
    /** 硬保底阈值 */
    private int hardPityThreshold = 50;
    /** 硬保底触发时保证的最低稀有度 */
    private String guaranteedRarity = "EPIC";
    /** 硬保底触发时是否强制替换为保底奖品 */
    private boolean replaceOnPity = true;

    public PityConfig() {}

    /**
     * 计算当前抽取次数下的软保底权重加成倍率
     *
     * @param currentCount 当前累计未出高稀有的抽取次数（本池）
     * @return 权重倍率（≥1.0）；未达软保底返回 1.0
     */
    public double getSoftPityBonus(int currentCount) {
        if (!enabled || softPityThreshold <= 0) return 1.0;
        if (currentCount < softPityThreshold) return 1.0;
        int over = currentCount - softPityThreshold + 1;
        return 1.0 + over * softPityIncrement;
    }

    /**
     * 硬保底是否已触发
     *
     * @param currentCount 当前累计未出高稀有的抽取次数
     * @return true 表示已达硬保底阈值
     */
    public boolean isHardPityTriggered(int currentCount) {
        return enabled && hardPityThreshold > 0 && currentCount >= hardPityThreshold;
    }

    /**
     * 获取硬保底保证的最低稀有度
     */
    public LotteryRarity getGuaranteedRarity() {
        return LotteryRarity.fromString(guaranteedRarity);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public int getSoftPityThreshold() {
        return softPityThreshold;
    }

    public double getSoftPityIncrement() {
        return softPityIncrement;
    }

    public int getHardPityThreshold() {
        return hardPityThreshold;
    }

    public boolean isReplaceOnPity() {
        return replaceOnPity;
    }

    /** 创建默认保底配置 */
    public static PityConfig createDefault() {
        return new PityConfig();
    }
}

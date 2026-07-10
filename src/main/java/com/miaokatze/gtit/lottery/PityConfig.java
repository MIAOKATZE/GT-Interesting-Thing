package com.miaokatze.gtit.lottery;

/**
 * 保底配置
 */
public class PityConfig {

    private boolean enabled = true;
    private int softPityThreshold = 30;
    private double softPityIncrement = 5.0;
    private int hardPityThreshold = 50;
    private String pityPrizeId = "pity_default";
    private boolean replaceOnPity = true;

    public PityConfig() {}

    public boolean hasSoftPity() {
        // TODO: v1.6.4 实现
        return enabled && softPityThreshold > 0;
    }

    public boolean hasHardPity() {
        // TODO: v1.6.4 实现
        return enabled && hardPityThreshold > 0;
    }

    public double getSoftPityBonus(int currentCount) {
        // TODO: v1.6.4 实现
        return 0.0;
    }

    public boolean isHardPityTriggered(int currentCount) {
        // TODO: v1.6.4 实现
        return false;
    }

    public static PityConfig createDefault() {
        return new PityConfig();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public int getSoftPityThreshold() {
        return softPityThreshold;
    }

    public int getHardPityThreshold() {
        return hardPityThreshold;
    }

    public String getPityPrizeId() {
        return pityPrizeId;
    }

    public boolean isReplaceOnPity() {
        return replaceOnPity;
    }
}

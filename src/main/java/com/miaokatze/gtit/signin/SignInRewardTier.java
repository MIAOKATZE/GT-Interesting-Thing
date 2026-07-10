package com.miaokatze.gtit.signin;

/**
 * 签到奖励层级
 * 达到指定连续天数时触发的额外奖励
 */
public class SignInRewardTier {

    private final int requiredDays;
    private final String currencyId;
    private final int currencyAmount;
    private final String itemRewardId;
    private final int itemRewardAmount;
    private final int itemRewardMeta;

    public SignInRewardTier(int requiredDays, String currencyId, int currencyAmount, String itemRewardId,
        int itemRewardAmount, int itemRewardMeta) {
        this.requiredDays = requiredDays;
        this.currencyId = currencyId;
        this.currencyAmount = currencyAmount;
        this.itemRewardId = itemRewardId;
        this.itemRewardAmount = itemRewardAmount;
        this.itemRewardMeta = itemRewardMeta;
    }

    public boolean hasItemReward() {
        return itemRewardId != null && !itemRewardId.isEmpty();
    }

    public int getRequiredDays() {
        return requiredDays;
    }

    public String getCurrencyId() {
        return currencyId;
    }

    public int getCurrencyAmount() {
        return currencyAmount;
    }

    public String getItemRewardId() {
        return itemRewardId;
    }

    public int getItemRewardAmount() {
        return itemRewardAmount;
    }

    public int getItemRewardMeta() {
        return itemRewardMeta;
    }
}

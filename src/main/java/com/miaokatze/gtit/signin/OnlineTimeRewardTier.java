package com.miaokatze.gtit.signin;

/**
 * 每日在线时间奖励档位（v1.7.6 G2③）
 * <p>
 * 玩家当日累计在线时长达到 {@link #requiredSeconds} 秒时可领取一次对应奖励
 * （货币入钱包 + 可选物品），每日 0 点跨日重置。
 * 字段与 {@link SignInRewardTier} 对齐（货币 + 可选物品），仅条件维度由「连续天数」换成「在线秒数」。
 */
public class OnlineTimeRewardTier {

    /** 领取条件：当日累计在线秒数（如 1800=30 分钟） */
    private final int requiredSeconds;
    /** 货币 ID（{@link com.miaokatze.gtit.trade.NekoCurrencyRegistrar#NEKO_ID} 等） */
    private final String currencyId;
    /** 货币数量 */
    private final int currencyAmount;
    /** 可选物品奖励 ID（"modid:name"，空串=无物品奖励） */
    private final String itemRewardId;
    /** 物品数量 */
    private final int itemRewardAmount;
    /** 物品 meta */
    private final int itemRewardMeta;

    public OnlineTimeRewardTier(int requiredSeconds, String currencyId, int currencyAmount, String itemRewardId,
        int itemRewardAmount, int itemRewardMeta) {
        this.requiredSeconds = requiredSeconds;
        this.currencyId = currencyId;
        this.currencyAmount = currencyAmount;
        this.itemRewardId = itemRewardId;
        this.itemRewardAmount = itemRewardAmount;
        this.itemRewardMeta = itemRewardMeta;
    }

    public boolean hasItemReward() {
        return itemRewardId != null && !itemRewardId.isEmpty();
    }

    public int getRequiredSeconds() {
        return requiredSeconds;
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

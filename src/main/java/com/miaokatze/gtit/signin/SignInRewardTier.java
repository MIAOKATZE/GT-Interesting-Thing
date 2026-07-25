package com.miaokatze.gtit.signin;

/**
 * 签到奖励层级
 * <p>
 * 达到指定连续天数时触发的额外奖励，包含货币奖励与可选物品奖励。
 * <p>
 * <b>v1.7.7 G5①</b>：物品奖励支持 NBT 数据（Base64 编码），字段为 {@link #itemNbt}，
 * 缺省空串表示无 NBT；发放奖励时由 {@link DailySignInManager} 解码并还原到 ItemStack。
 */
public class SignInRewardTier {

    private final int requiredDays;
    private final String currencyId;
    private final int currencyAmount;
    private final String itemRewardId;
    private final int itemRewardAmount;
    private final int itemRewardMeta;
    /** v1.7.7 G5①：物品奖励 NBT 数据（Base64 编码，空串 = 无 NBT） */
    private final String itemNbt;

    /**
     * 构造签到奖励层级（兼容旧代码：无 NBT）
     *
     * @param requiredDays     所需连续天数
     * @param currencyId       货币 ID
     * @param currencyAmount   货币数量
     * @param itemRewardId     物品奖励 ID（"modid:name"，空串 = 无物品奖励）
     * @param itemRewardAmount 物品奖励数量
     * @param itemRewardMeta   物品奖励 meta
     */
    public SignInRewardTier(int requiredDays, String currencyId, int currencyAmount, String itemRewardId,
        int itemRewardAmount, int itemRewardMeta) {
        this(requiredDays, currencyId, currencyAmount, itemRewardId, itemRewardAmount, itemRewardMeta, "");
    }

    /**
     * 构造签到奖励层级（v1.7.7 G5①：完整字段含 NBT）
     *
     * @param requiredDays     所需连续天数
     * @param currencyId       货币 ID
     * @param currencyAmount   货币数量
     * @param itemRewardId     物品奖励 ID（"modid:name"，空串 = 无物品奖励）
     * @param itemRewardAmount 物品奖励数量
     * @param itemRewardMeta   物品奖励 meta
     * @param itemNbt          物品 NBT Base64 编码（空串 = 无 NBT）
     */
    public SignInRewardTier(int requiredDays, String currencyId, int currencyAmount, String itemRewardId,
        int itemRewardAmount, int itemRewardMeta, String itemNbt) {
        this.requiredDays = requiredDays;
        this.currencyId = currencyId;
        this.currencyAmount = currencyAmount;
        this.itemRewardId = itemRewardId;
        this.itemRewardAmount = itemRewardAmount;
        this.itemRewardMeta = itemRewardMeta;
        this.itemNbt = itemNbt == null ? "" : itemNbt;
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

    /**
     * 获取物品奖励 NBT（Base64 编码）
     * <p>
     * v1.7.7 G5① 新增：空串表示该物品奖励无 NBT。
     *
     * @return Base64 编码的 NBT 字符串，不会为 null
     */
    public String getItemNbt() {
        return itemNbt;
    }
}

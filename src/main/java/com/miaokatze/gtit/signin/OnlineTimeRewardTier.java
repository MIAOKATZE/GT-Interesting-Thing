package com.miaokatze.gtit.signin;

/**
 * 每日在线时间奖励档位（v1.7.6 G2③；v1.7.7 G5② 新增物品 NBT）
 * <p>
 * 玩家当日累计在线时长达到 {@link #requiredSeconds} 秒时可领取一次对应奖励
 * （货币入钱包 + 可选物品），每日 0 点跨日重置。
 * 字段与 {@link SignInRewardTier} 对齐（货币 + 可选物品），仅条件维度由「连续天数」换成「在线秒数」。
 * <p>
 * <b>v1.7.7 G5②</b>：物品奖励支持 NBT 数据（Base64 编码），字段为 {@link #itemNbt}，
 * 缺省空串表示无 NBT；发放奖励时由 {@link DailySignInManager} 解码并还原到 ItemStack。
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
    /** v1.7.7 G5②：物品奖励 NBT 数据（Base64 编码，空串 = 无 NBT） */
    private final String itemNbt;

    /**
     * 构造在线奖励档位（兼容旧代码：无 NBT）
     */
    public OnlineTimeRewardTier(int requiredSeconds, String currencyId, int currencyAmount, String itemRewardId,
        int itemRewardAmount, int itemRewardMeta) {
        this(requiredSeconds, currencyId, currencyAmount, itemRewardId, itemRewardAmount, itemRewardMeta, "");
    }

    /**
     * 构造在线奖励档位（v1.7.7 G5②：完整字段含 NBT）
     *
     * @param requiredSeconds  所需在线秒数
     * @param currencyId       货币 ID
     * @param currencyAmount   货币数量
     * @param itemRewardId     物品奖励 ID（"modid:name"，空串 = 无物品奖励）
     * @param itemRewardAmount 物品奖励数量
     * @param itemRewardMeta   物品奖励 meta
     * @param itemNbt          物品 NBT Base64 编码（空串 = 无 NBT）
     */
    public OnlineTimeRewardTier(int requiredSeconds, String currencyId, int currencyAmount, String itemRewardId,
        int itemRewardAmount, int itemRewardMeta, String itemNbt) {
        this.requiredSeconds = requiredSeconds;
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

    /**
     * 获取物品奖励 NBT（Base64 编码）
     * <p>
     * v1.7.7 G5② 新增：空串表示该物品奖励无 NBT。
     *
     * @return Base64 编码的 NBT 字符串，不会为 null
     */
    public String getItemNbt() {
        return itemNbt;
    }
}

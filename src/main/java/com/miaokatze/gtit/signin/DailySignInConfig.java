package com.miaokatze.gtit.signin;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;
import com.miaokatze.gtit.main.GTInterestingThing;
import com.miaokatze.gtit.trade.NekoCurrencyRegistrar;

/**
 * 签到配置模型
 * <p>
 * 管理签到奖励配置的加载、保存与查询，配置文件路径: config/gtit/daily_signin.json。
 * <p>
 * 配置结构（Gson 序列化，参照 {@link com.miaokatze.gtit.config.GiftConfig} 模式）：
 * <ul>
 * <li>{@code base_reward}：每日签到基础猫猫币数量</li>
 * <li>{@code consecutive_increment}：每多连续 1 天增加的奖励系数（按 (连续天数-1)*系数 取整累加）</li>
 * <li>{@code tiers}：连续签到阶梯奖励列表（达到指定连续天数当月可领一次）</li>
 * </ul>
 */
public class DailySignInConfig {

    private static final String CONFIG_PATH = "config/gtit/daily_signin.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting()
        .disableHtmlEscaping()
        .create();

    /** 阶梯奖励列表（按 requiredDays 升序） */
    private static List<SignInRewardTier> rewardTiers = new ArrayList<>();
    /** 每日基础奖励（猫猫币数量） */
    private static int baseRewardNeko = 10;
    /** 连续天数奖励系数（每日多签 1 天的增量） */
    private static double consecutiveIncrement = 1.0;

    /** 配置根对象（对应 daily_signin.json 根） */
    public static class ConfigData {

        @SerializedName("base_reward")
        public int baseReward = 10;
        @SerializedName("consecutive_increment")
        public double consecutiveIncrement = 1.0;
        @SerializedName("tiers")
        public List<TierData> tiers = new ArrayList<>();
    }

    /** 单个阶梯奖励的 JSON 结构 */
    public static class TierData {

        @SerializedName("days")
        public int days;
        @SerializedName("currency")
        public String currency = NekoCurrencyRegistrar.NEKO_ID;
        @SerializedName("amount")
        public int amount;
        /** 可选物品奖励（"modid:name"），为空表示无物品奖励 */
        @SerializedName("item")
        public String item = "";
        @SerializedName("item_amount")
        public int itemAmount = 1;
        @SerializedName("item_meta")
        public int itemMeta = 0;
    }

    public static void init() {
        loadConfig();
    }

    /**
     * 加载配置文件；文件不存在或解析失败时使用默认配置并落盘
     */
    public static void loadConfig() {
        Path path = Paths.get(CONFIG_PATH);
        if (Files.exists(path)) {
            try {
                String json = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
                ConfigData data = GSON.fromJson(json, ConfigData.class);
                if (data != null) {
                    baseRewardNeko = Math.max(0, data.baseReward);
                    consecutiveIncrement = Math.max(0, data.consecutiveIncrement);
                    rewardTiers = toTierList(data.tiers);
                    if (rewardTiers.isEmpty()) {
                        rewardTiers = createDefaultTiers();
                    }
                    GTInterestingThing.LOG.info("签到奖励配置已加载（{} 个阶梯）", rewardTiers.size());
                    return;
                }
            } catch (Exception e) {
                GTInterestingThing.LOG.error("加载签到奖励配置失败，使用默认配置", e);
            }
        }
        // 首次运行或加载失败：使用默认配置并落盘
        baseRewardNeko = 10;
        consecutiveIncrement = 1.0;
        rewardTiers = createDefaultTiers();
        saveConfig();
    }

    /**
     * 将当前内存配置写回 JSON 文件
     */
    public static void saveConfig() {
        try {
            Path path = Paths.get(CONFIG_PATH);
            Files.createDirectories(path.getParent());
            ConfigData data = new ConfigData();
            data.baseReward = baseRewardNeko;
            data.consecutiveIncrement = consecutiveIncrement;
            for (SignInRewardTier tier : rewardTiers) {
                TierData td = new TierData();
                td.days = tier.getRequiredDays();
                td.currency = tier.getCurrencyId();
                td.amount = tier.getCurrencyAmount();
                td.item = tier.getItemRewardId() == null ? "" : tier.getItemRewardId();
                td.itemAmount = tier.getItemRewardAmount();
                td.itemMeta = tier.getItemRewardMeta();
                data.tiers.add(td);
            }
            Files.write(
                path,
                GSON.toJson(data)
                    .getBytes(StandardCharsets.UTF_8));
            GTInterestingThing.LOG.info("签到奖励配置已保存");
        } catch (Exception e) {
            GTInterestingThing.LOG.error("保存签到奖励配置失败", e);
        }
    }

    /**
     * 热重载配置（/gtit signin reload）
     */
    public static void reload() {
        loadConfig();
    }

    // ==================== 编辑模式更新（v1.7.0 目标 4，服务端权威） ====================

    /**
     * 更新全局签到参数（编辑模式）
     * <p>
     * 仅修改内存值，调用方负责随后 {@link #saveConfig()} 落盘。
     *
     * @param baseReward           每日基础奖励（猫猫币数量，≥0）
     * @param consecutiveIncrement 连续递增系数（≥0）
     */
    public static void setGlobalRewards(int baseReward, double consecutiveIncrement) {
        baseRewardNeko = Math.max(0, baseReward);
        DailySignInConfig.consecutiveIncrement = Math.max(0, consecutiveIncrement);
    }

    /**
     * 更新指定天数的阶梯奖励（编辑模式）
     * <p>
     * {@link SignInRewardTier} 字段为 final，更新以「整体替换」方式实现：
     * 在 {@link #rewardTiers} 中找到 requiredDays 匹配的阶梯并替换为新实例。
     * 仅修改内存值，调用方负责随后 {@link #saveConfig()} 落盘。
     *
     * @param days           目标阶梯的连续天数
     * @param currencyId     货币 ID（null/空回退猫猫币）
     * @param currencyAmount 货币数量（≥0）
     * @param itemId         物品奖励 ID（"modid:name"，空串表示无物品奖励）
     * @param itemAmount     物品数量（≥0）
     * @param itemMeta       物品 meta（≥0）
     * @return true 表示找到并替换；false 表示无该天数阶梯
     */
    public static boolean updateTier(int days, String currencyId, int currencyAmount, String itemId, int itemAmount,
        int itemMeta) {
        for (int i = 0; i < rewardTiers.size(); i++) {
            if (rewardTiers.get(i)
                .getRequiredDays() == days) {
                rewardTiers.set(
                    i,
                    new SignInRewardTier(
                        days,
                        currencyId == null || currencyId.isEmpty() ? NekoCurrencyRegistrar.NEKO_ID : currencyId,
                        Math.max(0, currencyAmount),
                        itemId == null ? "" : itemId,
                        Math.max(0, itemAmount),
                        Math.max(0, itemMeta)));
                return true;
            }
        }
        return false;
    }

    /**
     * 计算指定连续天数下的每日签到基础奖励
     * <p>
     * 公式：base + floor((连续天数 - 1) * 系数)，最低为 base。
     *
     * @param consecutiveDays 签到完成后的连续天数（≥1）
     * @return 应发放的猫猫币数量
     */
    public static int calculateBaseReward(int consecutiveDays) {
        if (consecutiveDays <= 1) return baseRewardNeko;
        return baseRewardNeko + (int) Math.floor((consecutiveDays - 1) * consecutiveIncrement);
    }

    public static List<SignInRewardTier> getRewardTiers() {
        return rewardTiers;
    }

    /**
     * 每日基础奖励（猫猫币数量）
     * <p>
     * v1.7.0 目标 5 新增：供配置同步包（{@link SignInSyncPacket}）读取服务端权威值。
     */
    public static int getBaseRewardNeko() {
        return baseRewardNeko;
    }

    /**
     * 连续天数奖励系数
     * <p>
     * v1.7.0 目标 5 新增：供配置同步包（{@link SignInSyncPacket}）读取服务端权威值。
     */
    public static double getConsecutiveIncrement() {
        return consecutiveIncrement;
    }

    /**
     * 获取达到指定连续天数时触发的阶梯奖励
     *
     * @param consecutiveDays 当前连续天数
     * @return 恰好要求该天数的阶梯；无则返回 null
     */
    public static SignInRewardTier getTriggeredTier(int consecutiveDays) {
        for (SignInRewardTier tier : rewardTiers) {
            if (tier.getRequiredDays() == consecutiveDays) {
                return tier;
            }
        }
        return null;
    }

    /**
     * 获取下一个尚未达到的阶梯（用于 GUI 进度展示）
     *
     * @param consecutiveDays 当前连续天数
     * @return 所需天数大于当前连续天数的最小阶梯；全部已达返回 null
     */
    public static SignInRewardTier getNextTier(int consecutiveDays) {
        SignInRewardTier next = null;
        for (SignInRewardTier tier : rewardTiers) {
            if (tier.getRequiredDays() > consecutiveDays) {
                if (next == null || tier.getRequiredDays() < next.getRequiredDays()) {
                    next = tier;
                }
            }
        }
        return next;
    }

    /** 获取最高阶梯天数（用于进度条分母） */
    public static int getMaxTierDays() {
        int max = 0;
        for (SignInRewardTier tier : rewardTiers) {
            if (tier.getRequiredDays() > max) max = tier.getRequiredDays();
        }
        return max;
    }

    // ==================== 内部辅助 ====================

    /** 将 JSON 层 TierData 列表转换为运行时 SignInRewardTier 列表（按天数升序） */
    private static List<SignInRewardTier> toTierList(List<TierData> tierDataList) {
        List<SignInRewardTier> result = new ArrayList<>();
        if (tierDataList != null) {
            for (TierData td : tierDataList) {
                if (td == null || td.days <= 0) continue;
                result.add(
                    new SignInRewardTier(
                        td.days,
                        td.currency == null || td.currency.isEmpty() ? NekoCurrencyRegistrar.NEKO_ID : td.currency,
                        Math.max(0, td.amount),
                        td.item == null ? "" : td.item,
                        Math.max(0, td.itemAmount),
                        Math.max(0, td.itemMeta)));
            }
        }
        result.sort((a, b) -> Integer.compare(a.getRequiredDays(), b.getRequiredDays()));
        return result;
    }

    /**
     * 默认阶梯奖励：7 天 50 猫猫币，14 天 150 猫猫币，30 天 5 闪烁猫猫币
     */
    private static List<SignInRewardTier> createDefaultTiers() {
        List<SignInRewardTier> tiers = new ArrayList<>();
        tiers.add(new SignInRewardTier(7, NekoCurrencyRegistrar.NEKO_ID, 50, "", 0, 0));
        tiers.add(new SignInRewardTier(14, NekoCurrencyRegistrar.NEKO_ID, 150, "", 0, 0));
        tiers.add(new SignInRewardTier(30, NekoCurrencyRegistrar.SHIMMERING_NEKO_ID, 5, "", 0, 0));
        return tiers;
    }
}

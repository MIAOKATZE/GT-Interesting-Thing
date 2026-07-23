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
 * 每日在线时间奖励配置（v1.7.6 G2③）
 * <p>
 * 管理在线奖励档位的加载、保存与查询，配置文件路径: config/gtit/online_time_config.json。
 * 结构参照 {@link DailySignInConfig}（Gson 序列化，缺省生成默认配置）：
 * <ul>
 * <li>{@code tiers}：在线奖励档位列表（当日累计在线秒数达到条件可领取一次，每日重置）</li>
 * </ul>
 * 默认档位（v1.7.6 用户确认口径）：30 分钟 / 2 小时 / 5 小时 = 5 / 20 / 50 猫猫币。
 * <p>
 * <b>双端口径</b>：本类为双端通用配置（客户端展示回退、服务端权威判定）；
 * 专用服务器环境客户端以 {@link SignInSyncPacket} 携带的档位快照为准
 * （{@link SignInClientData#getOnlineTiers()} 优先快照、未同步回退本类）。
 */
public class OnlineTimeConfig {

    private static final String CONFIG_PATH = "config/gtit/online_time_config.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting()
        .disableHtmlEscaping()
        .create();

    /** 在线奖励档位列表（按 requiredSeconds 升序） */
    private static List<OnlineTimeRewardTier> tiers = new ArrayList<>();

    /** 配置根对象（对应 online_time_config.json 根） */
    public static class ConfigData {

        @SerializedName("tiers")
        public List<TierData> tiers = new ArrayList<>();
    }

    /** 单个在线奖励档位的 JSON 结构 */
    public static class TierData {

        /** 领取条件：当日累计在线秒数 */
        @SerializedName("seconds")
        public int seconds;
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
                    tiers = toTierList(data.tiers);
                    if (tiers.isEmpty()) {
                        tiers = createDefaultTiers();
                    }
                    GTInterestingThing.LOG.info("每日在线奖励配置已加载（{} 个档位）", tiers.size());
                    return;
                }
            } catch (Exception e) {
                GTInterestingThing.LOG.error("加载每日在线奖励配置失败，使用默认配置", e);
            }
        }
        // 首次运行或加载失败：使用默认配置并落盘
        tiers = createDefaultTiers();
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
            for (OnlineTimeRewardTier tier : tiers) {
                TierData td = new TierData();
                td.seconds = tier.getRequiredSeconds();
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
            GTInterestingThing.LOG.info("每日在线奖励配置已保存");
        } catch (Exception e) {
            GTInterestingThing.LOG.error("保存每日在线奖励配置失败", e);
        }
    }

    /**
     * 热重载配置（供指令路径调用，与签到配置 reload 同模式）
     */
    public static void reload() {
        loadConfig();
    }

    /**
     * 当前生效的在线奖励档位列表（按所需秒数升序）
     */
    public static List<OnlineTimeRewardTier> getTiers() {
        return tiers;
    }

    // ==================== 内部辅助 ====================

    /** 将 JSON 层 TierData 列表转换为运行时 OnlineTimeRewardTier 列表（按秒数升序） */
    private static List<OnlineTimeRewardTier> toTierList(List<TierData> tierDataList) {
        List<OnlineTimeRewardTier> result = new ArrayList<>();
        if (tierDataList != null) {
            for (TierData td : tierDataList) {
                if (td == null || td.seconds <= 0) continue;
                result.add(
                    new OnlineTimeRewardTier(
                        td.seconds,
                        td.currency == null || td.currency.isEmpty() ? NekoCurrencyRegistrar.NEKO_ID : td.currency,
                        Math.max(0, td.amount),
                        td.item == null ? "" : td.item,
                        Math.max(0, td.itemAmount),
                        Math.max(0, td.itemMeta)));
            }
        }
        result.sort((a, b) -> Integer.compare(a.getRequiredSeconds(), b.getRequiredSeconds()));
        return result;
    }

    /**
     * 默认档位（v1.7.6 用户确认口径）：30 分钟 5 猫猫币 / 2 小时 20 猫猫币 / 5 小时 50 猫猫币
     */
    private static List<OnlineTimeRewardTier> createDefaultTiers() {
        List<OnlineTimeRewardTier> defaults = new ArrayList<>();
        defaults.add(new OnlineTimeRewardTier(30 * 60, NekoCurrencyRegistrar.NEKO_ID, 5, "", 0, 0));
        defaults.add(new OnlineTimeRewardTier(2 * 3600, NekoCurrencyRegistrar.NEKO_ID, 20, "", 0, 0));
        defaults.add(new OnlineTimeRewardTier(5 * 3600, NekoCurrencyRegistrar.NEKO_ID, 50, "", 0, 0));
        return defaults;
    }
}

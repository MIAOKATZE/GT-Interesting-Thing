package com.miaokatze.gtit.signin;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;
import com.miaokatze.gtit.config.ConfigMigrationUtil;
import com.miaokatze.gtit.trade.NekoCurrencyRegistrar;

/**
 * 每日在线时间奖励配置（v1.7.6 G2③；v1.7.7 G4 存储结构重构）
 * <p>
 * 管理在线奖励档位的加载、保存与查询，配置文件路径: {@code config/gtit/signin/online_time_config.json}。
 * <p>
 * 兼容：加载时若新路径缺失且旧文件 {@code config/gtit/online_time_config.json} 存在，
 * 则整体迁移到新路径，旧文件重命名为 {@code .bak} 保留。
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

    /** 统一 logger（O2-B02 去中心化：与主类同用 "gtit" logger 名，日志过滤口径不变） */
    private static final Logger LOG = LogManager.getLogger("gtit");

    /** 新配置文件路径（相对游戏根目录） */
    private static final String CONFIG_PATH = "config/gtit/signin/online_time_config.json";
    /** 旧配置文件路径（v1.7.7 G4 兼容迁移用） */
    private static final String LEGACY_CONFIG_PATH = "config/gtit/online_time_config.json";
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
        /** v1.7.7 G5②：物品奖励 NBT（Base64，缺省空串兼容旧 JSON） */
        @SerializedName("item_nbt")
        public String itemNbt = "";
    }

    public static void init() {
        loadConfig();
    }

    /**
     * 加载配置文件；文件不存在或解析失败时使用默认配置并落盘
     * <p>
     * v1.7.7 G4：优先读取新路径；新路径缺失且旧路径存在时，迁移旧文件到新路径，
     * 旧文件重命名为 {@code .bak} 保留。
     */
    public static void loadConfig() {
        Path path = Paths.get(CONFIG_PATH);
        if (!Files.exists(path)) {
            Path legacy = Paths.get(LEGACY_CONFIG_PATH);
            if (Files.exists(legacy)) {
                try {
                    migrateFromLegacy(legacy, path);
                    // 迁移后继续从新路径读取
                } catch (Exception e) {
                    LOG.error("每日在线奖励配置从旧路径迁移失败，回退默认配置", e);
                    tiers = createDefaultTiers();
                    saveConfig();
                    return;
                }
            }
        }

        if (Files.exists(path)) {
            try {
                String json = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
                ConfigData data = GSON.fromJson(json, ConfigData.class);
                if (data != null) {
                    tiers = toTierList(data.tiers);
                    if (tiers.isEmpty()) {
                        tiers = createDefaultTiers();
                    }
                    LOG.info("每日在线奖励配置已加载（{} 个档位）", tiers.size());
                    return;
                }
            } catch (Exception e) {
                LOG.error("加载每日在线奖励配置失败，使用默认配置", e);
            }
        }
        // 首次运行或加载失败：使用默认配置并落盘
        tiers = createDefaultTiers();
        saveConfig();
    }

    /**
     * 从旧路径迁移配置到新路径（v1.7.7 G4）
     *
     * @param legacyPath 旧配置文件路径
     * @param newPath    新配置文件路径
     */
    private static void migrateFromLegacy(Path legacyPath, Path newPath) throws Exception {
        Files.createDirectories(newPath.getParent());
        String json = new String(Files.readAllBytes(legacyPath), StandardCharsets.UTF_8);
        ConfigData data = GSON.fromJson(json, ConfigData.class);
        if (data != null) {
            Files.write(
                newPath,
                GSON.toJson(data)
                    .getBytes(StandardCharsets.UTF_8));
        }
        ConfigMigrationUtil.retireLegacyAsBak(legacyPath, newPath, "每日在线奖励配置", "每日在线奖励配置文件");
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
                td.itemNbt = tier.getItemNbt() == null ? "" : tier.getItemNbt();
                data.tiers.add(td);
            }
            Files.write(
                path,
                GSON.toJson(data)
                    .getBytes(StandardCharsets.UTF_8));
            LOG.info("每日在线奖励配置已保存");
        } catch (Exception e) {
            LOG.error("保存每日在线奖励配置失败", e);
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

    /**
     * 更新指定秒数的在线奖励档位（编辑模式）
     * <p>
     * 在 {@link #tiers} 中找到 requiredSeconds 匹配的档位并整体替换为新实例。
     * 仅修改内存值，调用方负责随后 {@link #saveConfig()} 落盘。
     * <p>
     * <b>v1.7.7 G5②</b>：新增 {@code itemNbt} 参数，支持物品奖励 NBT（Base64，null/空 = 无 NBT）。
     *
     * @param requiredSeconds 目标档位的所需秒数（定位用）
     * @param newSeconds      新的所需秒数（≥1）
     * @param currencyId      货币 ID（null/空回退猫猫币）
     * @param currencyAmount  货币数量（≥0）
     * @param itemId          物品奖励 ID（"modid:name"，空串表示无物品奖励）
     * @param itemAmount      物品数量（≥0）
     * @param itemMeta        物品 meta（≥0）
     * @param itemNbt         物品 NBT Base64 编码（null/空 = 无 NBT）
     * @return true 表示找到并替换；false 表示无该秒数档位
     */
    public static boolean updateTier(int requiredSeconds, int newSeconds, String currencyId, int currencyAmount,
        String itemId, int itemAmount, int itemMeta, String itemNbt) {
        for (int i = 0; i < tiers.size(); i++) {
            if (tiers.get(i)
                .getRequiredSeconds() == requiredSeconds) {
                tiers.set(
                    i,
                    new OnlineTimeRewardTier(
                        Math.max(1, newSeconds),
                        currencyId == null || currencyId.isEmpty() ? NekoCurrencyRegistrar.NEKO_ID : currencyId,
                        Math.max(0, currencyAmount),
                        itemId == null ? "" : itemId,
                        Math.max(0, itemAmount),
                        Math.max(0, itemMeta),
                        itemNbt == null ? "" : itemNbt));
                // 修改秒数后需要重新排序
                tiers.sort((a, b) -> Integer.compare(a.getRequiredSeconds(), b.getRequiredSeconds()));
                return true;
            }
        }
        return false;
    }

    /**
     * 新增在线奖励档位（编辑模式）
     * <p>
     * 将新档位追加到 {@link #tiers} 末尾，随后按所需秒数升序排序。
     * 仅修改内存值，调用方负责随后 {@link #saveConfig()} 落盘。
     *
     * @param seconds        所需在线秒数（≥1）
     * @param currencyId     货币 ID（null/空回退猫猫币）
     * @param currencyAmount 货币数量（≥0）
     * @param itemId         物品奖励 ID（"modid:name"，空串表示无物品奖励）
     * @param itemAmount     物品数量（≥0）
     * @param itemMeta       物品 meta（≥0）
     * @param itemNbt        物品 NBT Base64 编码（null/空 = 无 NBT）
     */
    public static void addTier(int seconds, String currencyId, int currencyAmount, String itemId, int itemAmount,
        int itemMeta, String itemNbt) {
        tiers.add(
            new OnlineTimeRewardTier(
                Math.max(1, seconds),
                currencyId == null || currencyId.isEmpty() ? NekoCurrencyRegistrar.NEKO_ID : currencyId,
                Math.max(0, currencyAmount),
                itemId == null ? "" : itemId,
                Math.max(0, itemAmount),
                Math.max(0, itemMeta),
                itemNbt == null ? "" : itemNbt));
        tiers.sort((a, b) -> Integer.compare(a.getRequiredSeconds(), b.getRequiredSeconds()));
    }

    /**
     * 删除指定秒数的在线奖励档位（编辑模式）
     *
     * @param requiredSeconds 目标档位的所需秒数
     * @return true 表示删除成功；false 表示未找到
     */
    public static boolean removeTier(int requiredSeconds) {
        for (int i = 0; i < tiers.size(); i++) {
            if (tiers.get(i)
                .getRequiredSeconds() == requiredSeconds) {
                tiers.remove(i);
                return true;
            }
        }
        return false;
    }

    /**
     * 替换整个在线奖励档位列表（编辑模式，用于服务端重置/全量更新）
     * <p>
     * 传入列表会被复制并升序排序，原列表引用不会被保留。
     *
     * @param newTiers 新的档位列表（null 视为空列表）
     */
    public static void setTiers(List<OnlineTimeRewardTier> newTiers) {
        tiers = new ArrayList<>();
        if (newTiers != null) {
            for (OnlineTimeRewardTier tier : newTiers) {
                if (tier != null && tier.getRequiredSeconds() > 0) {
                    tiers.add(tier);
                }
            }
        }
        tiers.sort((a, b) -> Integer.compare(a.getRequiredSeconds(), b.getRequiredSeconds()));
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
                        Math.max(0, td.itemMeta),
                        td.itemNbt == null ? "" : td.itemNbt));
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

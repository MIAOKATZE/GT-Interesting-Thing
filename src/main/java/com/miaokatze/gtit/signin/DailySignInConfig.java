package com.miaokatze.gtit.signin;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.annotations.SerializedName;
import com.miaokatze.gtit.config.ConfigMigrationUtil;
import com.miaokatze.gtit.currency.NekoCurrencyRegistrar;

/**
 * 签到配置模型（v1.7.8 任务5+6：配置结构 v1→v2）
 * <p>
 * 管理签到奖励配置的加载、保存与查询，配置文件路径: {@code config/gtit/signin/daily_signin.json}。
 * <p>
 * <b>v2 配置结构</b>（Gson 序列化，奖励内容统一由 {@link SignInReward} JSON 表达）：
 * <ul>
 * <li>{@code version}：结构版本号（当前=2）</li>
 * <li>{@code increment_enabled}：每日默认奖励是否随连续天数递增（v1.7.8 起默认 false）</li>
 * <li>{@code consecutive_increment}：递增系数（按 (连续天数-1)*系数 取整累加，仅作用默认奖励货币量）</li>
 * <li>{@code weekday_default} / {@code weekend_default}：每月每日默认奖励（区分工作日/周末）</li>
 * <li>{@code day_overrides}：逐日覆盖奖励（键为月内日号 1..31，覆盖天不递增）</li>
 * <li>{@code tiers}：连续签到阶梯奖励（达到指定连续天数当月可领一次）</li>
 * <li>{@code cumulative_tiers}：累计签到阶梯奖励（达到指定累计天数永久限领一次）</li>
 * </ul>
 * <p>
 * <b>v1→v2 迁移</b>：加载时检测 {@code version} 缺失或 &lt;2，先复制原文件为
 * {@code daily_signin.json.v1.bak}，再按迁移规则写出 v2：旧 {@code base_reward} 同时填入
 * 工作日/周末默认；{@code increment_enabled} 强制 false 并 LOG.warn；旧阶梯单物品字段
 * 移入 {@code items[0]}；另补默认累计档位（30=100/100=300/365=1000 猫猫币）。
 * <p>
 * 路径兼容：加载时若新路径缺失且旧文件 {@code config/gtit/daily_signin.json} 存在，
 * 则整体迁移到新路径，旧文件重命名为 {@code .bak} 保留（版本迁移随后在其上生效）。
 */
public class DailySignInConfig {

    /** 统一 logger（O2-B02 去中心化：与主类同用 "gtit" logger 名，日志过滤口径不变） */
    private static final Logger LOG = LogManager.getLogger("gtit");

    /** 新配置文件路径（相对游戏根目录） */
    private static final String CONFIG_PATH = "config/gtit/signin/daily_signin.json";
    /** 旧配置文件路径（v1.7.7 G4 兼容迁移用） */
    private static final String LEGACY_CONFIG_PATH = "config/gtit/daily_signin.json";
    /** 当前配置结构版本号 */
    private static final int CONFIG_VERSION = 2;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting()
        .disableHtmlEscaping()
        .create();

    // ==================== 运行时内存字段 ====================

    /** 连续签到阶梯列表（按 requiredDays 升序） */
    private static List<SignInRewardTier> rewardTiers = new ArrayList<>();
    /** 累计签到阶梯列表（按 requiredDays 升序，永久每档限领一次） */
    private static List<SignInRewardTier> cumulativeTiers = new ArrayList<>();
    /** 逐日覆盖奖励（键=月内日号 1..31；覆盖天不参与递增） */
    private static Map<Integer, SignInReward> dayOverrides = new HashMap<>();
    /** 工作日默认奖励 */
    private static SignInReward weekdayDefault = new SignInReward(NekoCurrencyRegistrar.NEKO_ID, 10, null);
    /** 周末默认奖励 */
    private static SignInReward weekendDefault = new SignInReward(NekoCurrencyRegistrar.NEKO_ID, 20, null);
    /** 每日默认奖励是否随连续天数递增（v1.7.8 起默认 false） */
    private static boolean incrementEnabled = false;
    /** 连续天数奖励系数（仅 incrementEnabled=true 时生效，仅作用默认奖励货币量） */
    private static double consecutiveIncrement = 1.0;

    // ==================== JSON 结构 ====================

    /** 配置根对象（对应 daily_signin.json 根，v2） */
    public static class ConfigData {

        @SerializedName("version")
        public int version = CONFIG_VERSION;
        @SerializedName("increment_enabled")
        public boolean incrementEnabled = false;
        @SerializedName("consecutive_increment")
        public double consecutiveIncrement = 1.0;
        /** 工作日默认奖励（{@link SignInReward#toJson()} 结构） */
        @SerializedName("weekday_default")
        public JsonObject weekdayDefault;
        /** 周末默认奖励（{@link SignInReward#toJson()} 结构） */
        @SerializedName("weekend_default")
        public JsonObject weekendDefault;
        /** 逐日覆盖奖励（{"日号": 奖励 JSON}） */
        @SerializedName("day_overrides")
        public JsonObject dayOverrides;
        @SerializedName("tiers")
        public List<TierData> tiers = new ArrayList<>();
        @SerializedName("cumulative_tiers")
        public List<TierData> cumulativeTiers = new ArrayList<>();
    }

    /** 单个阶梯奖励的 JSON 结构（连续/累计共用） */
    public static class TierData {

        @SerializedName("days")
        public int days;
        /** 奖励内容（{@link SignInReward#toJson()} 结构） */
        @SerializedName("reward")
        public JsonObject reward;
    }

    public static void init() {
        loadConfig();
    }

    // ==================== 加载 / 保存 / 迁移 ====================

    /**
     * 加载配置文件；文件不存在或解析失败时使用默认配置并落盘
     * <p>
     * 加载顺序：新路径缺失且旧路径存在 → 先迁移旧文件到新路径（原文复制）；
     * 随后检测结构版本，{@code version} 缺失或 &lt;2 时执行 v1→v2 迁移（备份后写出）。
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
                    LOG.error("签到奖励配置从旧路径迁移失败，回退默认配置", e);
                    applyDefaults();
                    saveConfig();
                    return;
                }
            }
        }

        if (Files.exists(path)) {
            try {
                String json = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
                JsonObject root = new JsonParser().parse(json)
                    .getAsJsonObject();
                int version = root.has("version") ? root.get("version")
                    .getAsInt() : 1;
                if (version < CONFIG_VERSION) {
                    // v1→v2 结构迁移（备份 .v1.bak 后写出 v2，并应用到内存）
                    migrateV1ToV2(path, root);
                    return;
                }
                ConfigData data = GSON.fromJson(root, ConfigData.class);
                if (data != null) {
                    applyFromData(data);
                    LOG.info(
                        "签到奖励配置已加载（连续 {} 档 / 累计 {} 档 / 逐日覆盖 {} 项）",
                        rewardTiers.size(),
                        cumulativeTiers.size(),
                        dayOverrides.size());
                    return;
                }
            } catch (Exception e) {
                LOG.error("加载签到奖励配置失败，使用默认配置", e);
            }
        }
        // 首次运行或加载失败：使用默认配置并落盘
        applyDefaults();
        saveConfig();
    }

    /** 将 v2 ConfigData 应用到内存字段（含空值兜底） */
    private static void applyFromData(ConfigData data) {
        incrementEnabled = data.incrementEnabled;
        consecutiveIncrement = Math.max(0, data.consecutiveIncrement);
        weekdayDefault = SignInReward.fromJson(data.weekdayDefault);
        if (weekdayDefault == SignInReward.EMPTY) {
            weekdayDefault = new SignInReward(NekoCurrencyRegistrar.NEKO_ID, 10, null);
        }
        weekendDefault = SignInReward.fromJson(data.weekendDefault);
        if (weekendDefault == SignInReward.EMPTY) {
            weekendDefault = new SignInReward(NekoCurrencyRegistrar.NEKO_ID, 20, null);
        }
        dayOverrides = parseDayOverrides(data.dayOverrides);
        rewardTiers = toTierList(data.tiers);
        if (rewardTiers.isEmpty()) {
            rewardTiers = createDefaultTiers();
        }
        cumulativeTiers = toTierList(data.cumulativeTiers);
        if (cumulativeTiers.isEmpty()) {
            cumulativeTiers = createDefaultCumulativeTiers();
        }
    }

    /**
     * 从旧路径迁移配置到新路径（v1.7.7 G4）
     * <p>
     * 原文复制内容到新路径（结构版本迁移由后续 {@link #migrateV1ToV2} 完成），
     * 旧文件重命名为 {@code .bak} 保留。
     *
     * @param legacyPath 旧配置文件路径
     * @param newPath    新配置文件路径
     */
    private static void migrateFromLegacy(Path legacyPath, Path newPath) throws Exception {
        Files.createDirectories(newPath.getParent());
        Files.copy(legacyPath, newPath, StandardCopyOption.REPLACE_EXISTING);
        ConfigMigrationUtil.retireLegacyAsBak(legacyPath, newPath, "签到奖励配置", "签到奖励配置文件");
    }

    /**
     * v1→v2 结构迁移（v1.7.8 任务5+6）
     * <p>
     * 迁移规则：原文件复制为 {@code .v1.bak}；旧 {@code base_reward} 同时填入工作日/周末默认；
     * {@code increment_enabled} 强制 false（LOG.warn 告知原系数保留但暂不生效）；
     * 旧阶梯单物品字段（item/item_amount/item_meta/item_nbt）移入奖励 {@code items[0]}；
     * 补充默认累计档位（30=100/100=300/365=1000 猫猫币）。迁移后立即应用到内存。
     *
     * @param path    当前配置文件路径
     * @param oldRoot 旧版 JSON 根对象
     */
    private static void migrateV1ToV2(Path path, JsonObject oldRoot) {
        try {
            // 1. 备份原文件
            Path backupPath = ConfigMigrationUtil.siblingBackupPath(path, ".v1.bak");
            Files.copy(path, backupPath, StandardCopyOption.REPLACE_EXISTING);
            LOG.info("签到配置 v1 已备份: {}", backupPath);

            // 2. 解析旧字段
            int oldBase = oldRoot.has("base_reward") ? Math.max(
                0,
                oldRoot.get("base_reward")
                    .getAsInt())
                : 10;
            double oldIncrement = oldRoot
                .has("consecutive_increment")
                    ? Math.max(
                        0,
                        oldRoot.get("consecutive_increment")
                            .getAsDouble())
                    : 1.0;
            // 递增默认关闭（用户确认口径）：原系数保留在配置中但 increment_enabled=false 使其暂不生效
            LOG.warn("签到配置 v1→v2 迁移：每日奖励递增已默认关闭（increment_enabled=false），原系数 {} 保留但暂不生效，可在编辑模式重新开启", oldIncrement);

            // 3. 构建 v2 结构
            ConfigData data = new ConfigData();
            data.incrementEnabled = false;
            data.consecutiveIncrement = oldIncrement;
            // 旧 base_reward 同时填入工作日/周末默认（迁移后管理员可再区分调整）
            data.weekdayDefault = new SignInReward(NekoCurrencyRegistrar.NEKO_ID, oldBase, null).toJson();
            data.weekendDefault = new SignInReward(NekoCurrencyRegistrar.NEKO_ID, oldBase, null).toJson();
            data.dayOverrides = new JsonObject();
            // 旧阶梯：单物品字段 → items[0]
            if (oldRoot.has("tiers") && oldRoot.get("tiers")
                .isJsonArray()) {
                for (JsonElement e : oldRoot.getAsJsonArray("tiers")) {
                    if (e == null || !e.isJsonObject()) continue;
                    JsonObject oldTier = e.getAsJsonObject();
                    int days = oldTier.has("days") ? oldTier.get("days")
                        .getAsInt() : 0;
                    if (days <= 0) continue;
                    String currency = oldTier.has("currency") ? oldTier.get("currency")
                        .getAsString() : NekoCurrencyRegistrar.NEKO_ID;
                    int amount = oldTier.has("amount") ? oldTier.get("amount")
                        .getAsInt() : 0;
                    List<RewardItem> items = new ArrayList<>();
                    String item = oldTier.has("item") ? oldTier.get("item")
                        .getAsString() : "";
                    if (!item.isEmpty()) {
                        int itemAmount = oldTier.has("item_amount") ? oldTier.get("item_amount")
                            .getAsInt() : 1;
                        int itemMeta = oldTier.has("item_meta") ? oldTier.get("item_meta")
                            .getAsInt() : 0;
                        String itemNbt = oldTier.has("item_nbt") ? oldTier.get("item_nbt")
                            .getAsString() : "";
                        items.add(new RewardItem(item, itemAmount, itemMeta, itemNbt));
                    }
                    TierData td = new TierData();
                    td.days = days;
                    td.reward = new SignInReward(currency, amount, items).toJson();
                    data.tiers.add(td);
                }
            }
            // 默认累计档位（用户确认：30天=100、100天=300、365天=1000 猫猫币）
            for (SignInRewardTier tier : createDefaultCumulativeTiers()) {
                TierData td = new TierData();
                td.days = tier.getRequiredDays();
                td.reward = tier.getReward()
                    .toJson();
                data.cumulativeTiers.add(td);
            }

            // 4. 落盘 v2 并应用到内存
            Files.createDirectories(path.getParent());
            Files.write(
                path,
                GSON.toJson(data)
                    .getBytes(StandardCharsets.UTF_8));
            applyFromData(data);
            LOG.info("签到配置已迁移到 v2（连续 {} 档 / 累计 {} 档）", rewardTiers.size(), cumulativeTiers.size());
        } catch (Exception e) {
            LOG.error("签到配置 v1→v2 迁移失败，回退默认配置", e);
            applyDefaults();
            saveConfig();
        }
    }

    /** 将内存字段恢复为默认配置值（v2 默认） */
    private static void applyDefaults() {
        incrementEnabled = false;
        consecutiveIncrement = 1.0;
        weekdayDefault = new SignInReward(NekoCurrencyRegistrar.NEKO_ID, 10, null);
        weekendDefault = new SignInReward(NekoCurrencyRegistrar.NEKO_ID, 20, null);
        dayOverrides = new HashMap<>();
        rewardTiers = createDefaultTiers();
        cumulativeTiers = createDefaultCumulativeTiers();
    }

    /**
     * 将当前内存配置写回 JSON 文件（v2 结构）
     */
    public static void saveConfig() {
        try {
            Path path = Paths.get(CONFIG_PATH);
            Files.createDirectories(path.getParent());
            ConfigData data = new ConfigData();
            data.incrementEnabled = incrementEnabled;
            data.consecutiveIncrement = consecutiveIncrement;
            data.weekdayDefault = weekdayDefault.toJson();
            data.weekendDefault = weekendDefault.toJson();
            JsonObject overrides = new JsonObject();
            for (Map.Entry<Integer, SignInReward> entry : dayOverrides.entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null) {
                    overrides.add(
                        String.valueOf(entry.getKey()),
                        entry.getValue()
                            .toJson());
                }
            }
            data.dayOverrides = overrides;
            for (SignInRewardTier tier : rewardTiers) {
                TierData td = new TierData();
                td.days = tier.getRequiredDays();
                td.reward = tier.getReward()
                    .toJson();
                data.tiers.add(td);
            }
            for (SignInRewardTier tier : cumulativeTiers) {
                TierData td = new TierData();
                td.days = tier.getRequiredDays();
                td.reward = tier.getReward()
                    .toJson();
                data.cumulativeTiers.add(td);
            }
            Files.write(
                path,
                GSON.toJson(data)
                    .getBytes(StandardCharsets.UTF_8));
            LOG.info("签到奖励配置已保存");
        } catch (Exception e) {
            LOG.error("保存签到奖励配置失败", e);
        }
    }

    /**
     * 热重载配置（/gtit signin reload）
     */
    public static void reload() {
        loadConfig();
    }

    // ==================== 编辑模式更新（服务端权威，调用方负责随后 saveConfig 落盘） ====================

    /**
     * 更新每月全局参数（编辑模式，targetId="monthly"）
     * <p>
     * 仅修改内存值，调用方负责随后 {@link #saveConfig()} 落盘。
     *
     * @param increment 是否启用每日默认奖励递增
     * @param incFactor 连续递增系数（≥0）
     * @param weekday   工作日默认奖励（null 忽略不改）
     * @param weekend   周末默认奖励（null 忽略不改）
     */
    public static void setMonthlyGlobal(boolean increment, double incFactor, SignInReward weekday,
        SignInReward weekend) {
        incrementEnabled = increment;
        consecutiveIncrement = Math.max(0, incFactor);
        if (weekday != null) weekdayDefault = weekday;
        if (weekend != null) weekendDefault = weekend;
    }

    /**
     * 更新指定天数的连续阶梯奖励（编辑模式，允许同时修改天数）
     * <p>
     * {@link SignInRewardTier} 字段为 final，更新以「整体替换」方式实现；
     * 修改天数后重新升序排序。仅修改内存值，调用方负责随后 {@link #saveConfig()} 落盘。
     *
     * @param originalDays 目标阶梯的原连续天数（定位用）
     * @param newDays      新连续天数（≥1）
     * @param reward       新奖励内容（null 按 {@link SignInReward#EMPTY} 处理）
     * @return true 表示找到并替换；false 表示无该天数阶梯
     */
    public static boolean updateTier(int originalDays, int newDays, SignInReward reward) {
        for (int i = 0; i < rewardTiers.size(); i++) {
            if (rewardTiers.get(i)
                .getRequiredDays() == originalDays) {
                rewardTiers.set(i, new SignInRewardTier(newDays, reward));
                rewardTiers.sort((a, b) -> Integer.compare(a.getRequiredDays(), b.getRequiredDays()));
                return true;
            }
        }
        return false;
    }

    /**
     * 新增连续阶梯奖励（编辑模式，追加后按天数升序）
     */
    public static void addTier(int days, SignInReward reward) {
        rewardTiers.add(new SignInRewardTier(days, reward));
        rewardTiers.sort((a, b) -> Integer.compare(a.getRequiredDays(), b.getRequiredDays()));
    }

    /**
     * 删除指定天数的连续阶梯奖励（编辑模式）
     *
     * @return true 表示删除成功；false 表示未找到
     */
    public static boolean removeTier(int days) {
        for (int i = 0; i < rewardTiers.size(); i++) {
            if (rewardTiers.get(i)
                .getRequiredDays() == days) {
                rewardTiers.remove(i);
                return true;
            }
        }
        return false;
    }

    /**
     * 更新指定天数的累计阶梯奖励（编辑模式，允许同时修改天数）
     *
     * @return true 表示找到并替换；false 表示无该天数阶梯
     */
    public static boolean updateCumulativeTier(int originalDays, int newDays, SignInReward reward) {
        for (int i = 0; i < cumulativeTiers.size(); i++) {
            if (cumulativeTiers.get(i)
                .getRequiredDays() == originalDays) {
                cumulativeTiers.set(i, new SignInRewardTier(newDays, reward));
                cumulativeTiers.sort((a, b) -> Integer.compare(a.getRequiredDays(), b.getRequiredDays()));
                return true;
            }
        }
        return false;
    }

    /**
     * 新增累计阶梯奖励（编辑模式，追加后按天数升序）
     */
    public static void addCumulativeTier(int days, SignInReward reward) {
        cumulativeTiers.add(new SignInRewardTier(days, reward));
        cumulativeTiers.sort((a, b) -> Integer.compare(a.getRequiredDays(), b.getRequiredDays()));
    }

    /**
     * 删除指定天数的累计阶梯奖励（编辑模式）
     *
     * @return true 表示删除成功；false 表示未找到
     */
    public static boolean removeCumulativeTier(int days) {
        for (int i = 0; i < cumulativeTiers.size(); i++) {
            if (cumulativeTiers.get(i)
                .getRequiredDays() == days) {
                cumulativeTiers.remove(i);
                return true;
            }
        }
        return false;
    }

    /**
     * 设置逐日覆盖奖励（编辑模式；同日日号覆盖写，null 奖励等效清除）
     *
     * @param day    月内日号（1..31，越界忽略）
     * @param reward 覆盖奖励内容
     */
    public static void setDayOverride(int day, SignInReward reward) {
        if (day < 1 || day > 31) return;
        if (reward == null) {
            dayOverrides.remove(day);
        } else {
            dayOverrides.put(day, reward);
        }
    }

    /**
     * 清除指定日号的逐日覆盖（编辑模式）
     *
     * @return true 表示存在并已清除；false 表示本无覆盖
     */
    public static boolean removeDayOverride(int day) {
        return dayOverrides.remove(day) != null;
    }

    // ==================== 查询 ====================

    /**
     * 计算指定日期签到应发放的每日货币量
     * <p>
     * 口径（用户确认）：逐日覆盖天直接取覆盖奖励货币量（<b>不递增</b>）；
     * 否则按工作日/周末默认奖励货币量，{@code incrementEnabled=true} 时按
     * {@code base + floor((连续天数-1) * 系数)} 递增。
     *
     * @param date            签到日期（yyyy-MM-dd，服务端口径）
     * @param consecutiveDays 签到完成后的连续天数（≥1）
     * @return 应发放的货币数量
     */
    public static int calculateDayCurrency(String date, int consecutiveDays) {
        int day = parseDayOfMonth(date);
        if (day > 0) {
            SignInReward override = dayOverrides.get(day);
            if (override != null) {
                // 覆盖天：不递增
                return override.getCurrencyAmount();
            }
        }
        SignInReward def = isWeekend(date) ? weekendDefault : weekdayDefault;
        int base = def.getCurrencyAmount();
        if (!incrementEnabled || consecutiveDays <= 1) return base;
        return base + (int) Math.floor((consecutiveDays - 1) * consecutiveIncrement);
    }

    /**
     * 获取指定日期生效的每日奖励（覆盖优先，否则按工作日/周末默认）
     * <p>
     * 货币量请以 {@link #calculateDayCurrency} 为准（含递增口径）；
     * 本方法主要用于取货币 ID 与物品列表。
     *
     * @param date 日期（yyyy-MM-dd）
     * @return 生效的奖励（不会为 null）
     */
    public static SignInReward getEffectiveDayReward(String date) {
        int day = parseDayOfMonth(date);
        if (day > 0) {
            SignInReward override = dayOverrides.get(day);
            if (override != null) return override;
        }
        return isWeekend(date) ? weekendDefault : weekdayDefault;
    }

    /**
     * 指定日期是否存在逐日覆盖
     *
     * @param date 日期（yyyy-MM-dd）
     * @return true 表示该日号配置了覆盖奖励
     */
    public static boolean hasDayOverride(String date) {
        int day = parseDayOfMonth(date);
        return day > 0 && dayOverrides.containsKey(day);
    }

    /**
     * 判断日期是否为周末（周六/周日）
     *
     * @param date 日期（yyyy-MM-dd）；解析失败按工作日处理
     * @return true 表示周末
     */
    public static boolean isWeekend(String date) {
        try {
            int year = Integer.parseInt(date.substring(0, 4));
            int month = Integer.parseInt(date.substring(5, 7));
            int day = Integer.parseInt(date.substring(8, 10));
            Calendar cal = Calendar.getInstance();
            cal.clear();
            cal.set(year, month - 1, day);
            int dow = cal.get(Calendar.DAY_OF_WEEK);
            return dow == Calendar.SATURDAY || dow == Calendar.SUNDAY;
        } catch (Exception e) {
            return false;
        }
    }

    public static List<SignInRewardTier> getRewardTiers() {
        return rewardTiers;
    }

    /** 累计签到阶梯列表（按天数升序） */
    public static List<SignInRewardTier> getCumulativeTiers() {
        return cumulativeTiers;
    }

    /** 逐日覆盖奖励表（键=月内日号；仅供同步包序列化读取，勿修改） */
    public static Map<Integer, SignInReward> getDayOverrides() {
        return Collections.unmodifiableMap(dayOverrides);
    }

    /** 工作日默认奖励 */
    public static SignInReward getWeekdayDefault() {
        return weekdayDefault;
    }

    /** 周末默认奖励 */
    public static SignInReward getWeekendDefault() {
        return weekendDefault;
    }

    /** 每日默认奖励是否随连续天数递增 */
    public static boolean isIncrementEnabled() {
        return incrementEnabled;
    }

    /**
     * 连续天数奖励系数
     * <p>
     * 供配置同步包（{@link SignInSyncPacket}）读取服务端权威值。
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
     * 获取下一个尚未达到的连续阶梯（用于 GUI 进度展示）
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

    /** 获取最高连续阶梯天数（用于进度条分母） */
    public static int getMaxTierDays() {
        int max = 0;
        for (SignInRewardTier tier : rewardTiers) {
            if (tier.getRequiredDays() > max) max = tier.getRequiredDays();
        }
        return max;
    }

    /**
     * 获取达到指定累计天数时触发的累计阶梯奖励
     *
     * @param totalDays 当前累计签到天数
     * @return 恰好要求该累计天数的阶梯；无则返回 null
     */
    public static SignInRewardTier getTriggeredCumulativeTier(int totalDays) {
        for (SignInRewardTier tier : cumulativeTiers) {
            if (tier.getRequiredDays() == totalDays) {
                return tier;
            }
        }
        return null;
    }

    /**
     * 获取下一个尚未达到的累计阶梯（用于 GUI 进度展示）
     *
     * @param totalDays 当前累计签到天数
     * @return 所需天数大于当前累计天数的最小阶梯；全部已达返回 null
     */
    public static SignInRewardTier getNextCumulativeTier(int totalDays) {
        SignInRewardTier next = null;
        for (SignInRewardTier tier : cumulativeTiers) {
            if (tier.getRequiredDays() > totalDays) {
                if (next == null || tier.getRequiredDays() < next.getRequiredDays()) {
                    next = tier;
                }
            }
        }
        return next;
    }

    // ==================== 内部辅助 ====================

    /** 解析 yyyy-MM-dd 的月内日号（格式异常返回 -1） */
    private static int parseDayOfMonth(String date) {
        try {
            if (date == null || date.length() < 10) return -1;
            return Integer.parseInt(date.substring(8, 10));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** 解析 day_overrides JSON 对象为 Map（键=日号，非法键值跳过） */
    private static Map<Integer, SignInReward> parseDayOverrides(JsonObject json) {
        Map<Integer, SignInReward> result = new HashMap<>();
        if (json != null) {
            for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
                try {
                    int day = Integer.parseInt(entry.getKey());
                    if (day < 1 || day > 31) continue;
                    if (entry.getValue() != null && entry.getValue()
                        .isJsonObject()) {
                        result.put(
                            day,
                            SignInReward.fromJson(
                                entry.getValue()
                                    .getAsJsonObject()));
                    }
                } catch (NumberFormatException ignored) {
                    // 跳过非法日号键
                }
            }
        }
        return result;
    }

    /** 将 JSON 层 TierData 列表转换为运行时 SignInRewardTier 列表（按天数升序） */
    private static List<SignInRewardTier> toTierList(List<TierData> tierDataList) {
        List<SignInRewardTier> result = new ArrayList<>();
        if (tierDataList != null) {
            for (TierData td : tierDataList) {
                if (td == null || td.days <= 0) continue;
                result.add(new SignInRewardTier(td.days, SignInReward.fromJson(td.reward)));
            }
        }
        result.sort((a, b) -> Integer.compare(a.getRequiredDays(), b.getRequiredDays()));
        return result;
    }

    /**
     * 默认连续阶梯奖励：7 天 50 猫猫币，14 天 150 猫猫币，30 天 5 闪烁猫猫币
     */
    private static List<SignInRewardTier> createDefaultTiers() {
        List<SignInRewardTier> tiers = new ArrayList<>();
        tiers.add(new SignInRewardTier(7, new SignInReward(NekoCurrencyRegistrar.NEKO_ID, 50, null)));
        tiers.add(new SignInRewardTier(14, new SignInReward(NekoCurrencyRegistrar.NEKO_ID, 150, null)));
        tiers.add(new SignInRewardTier(30, new SignInReward(NekoCurrencyRegistrar.SHIMMERING_NEKO_ID, 5, null)));
        return tiers;
    }

    /**
     * 默认累计阶梯奖励（用户确认）：30 天 100、100 天 300、365 天 1000 猫猫币
     */
    private static List<SignInRewardTier> createDefaultCumulativeTiers() {
        List<SignInRewardTier> tiers = new ArrayList<>();
        tiers.add(new SignInRewardTier(30, new SignInReward(NekoCurrencyRegistrar.NEKO_ID, 100, null)));
        tiers.add(new SignInRewardTier(100, new SignInReward(NekoCurrencyRegistrar.NEKO_ID, 300, null)));
        tiers.add(new SignInRewardTier(365, new SignInReward(NekoCurrencyRegistrar.NEKO_ID, 1000, null)));
        return tiers;
    }
}

package com.miaokatze.gtit.lottery;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonSerializer;
import com.miaokatze.gtit.main.GTInterestingThing;
import com.miaokatze.gtit.trade.NekoCurrencyRegistrar;

/**
 * 抽奖配置加载（JSON）
 * <p>
 * 配置文件路径：{@code config/gtit/lottery.json}（与 daily_signin.json 同目录约定）。
 * 首启写入默认配置（双卡池：猫猫币池 + 闪烁猫猫币池），支持 reload 热重载。
 * <p>
 * 配置结构（Gson 序列化，参照 {@link com.miaokatze.gtit.signin.DailySignInConfig} 模式）：
 * <ul>
 * <li>{@code pools[]}：卡池列表，每池含 id/name/costPerDraw/nekoCurrencyId</li>
 * <li>{@code pools[].entries[]}：奖品条目（item/meta/minAmount/maxAmount/weight/rarity/nekoCurrencyId/nbtBase64）</li>
 * <li>{@code pools[].pityConfig}：保底配置（软保底阈值/增量、硬保底阈值/保底稀有度）</li>
 * </ul>
 * rarity 字段大小写不敏感（{@code "epic"}/{@code "EPIC"} 均可），未知值回退 COMMON。
 */
public class LotteryConfig {

    /** 配置文件路径（相对游戏根目录） */
    private static final String CONFIG_PATH = "config/gtit/lottery.json";
    /** Gson 实例（rarity 枚举注册大小写不敏感适配器） */
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting()
        .disableHtmlEscaping()
        .registerTypeAdapter(
            LotteryRarity.class,
            (JsonSerializer<LotteryRarity>) (src, typeOfSrc, context) -> context.serialize(src.name()))
        .registerTypeAdapter(
            LotteryRarity.class,
            (JsonDeserializer<LotteryRarity>) (json, typeOfT, context) -> LotteryRarity.fromString(json.getAsString()))
        .create();

    /** 配置根对象（对应 lottery.json 根） */
    public static class LotteryConfigData {

        /** 卡池列表 */
        public List<LotteryPool> pools = new ArrayList<>();
    }

    /**
     * 初始化配置（CommonProxy.preInit 调用）
     */
    public static void init() {
        load();
    }

    /**
     * 加载配置文件；文件不存在或解析失败时使用默认配置并落盘
     *
     * @return 配置数据（永不为 null，失败回退默认）
     */
    public static LotteryConfigData load() {
        Path path = Paths.get(CONFIG_PATH);
        if (Files.exists(path)) {
            try {
                String json = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
                LotteryConfigData data = GSON.fromJson(json, LotteryConfigData.class);
                if (data != null && data.pools != null && !data.pools.isEmpty()) {
                    // 条目校验：过滤非法条目/卡池（记录日志提示配置错误位置）
                    if (validateAll(data)) {
                        GTInterestingThing.LOG.info("抽奖配置已加载（{} 个卡池）", data.pools.size());
                        return data;
                    }
                    GTInterestingThing.LOG.warn("抽奖配置存在非法卡池/条目，已按校验结果过滤后继续加载");
                    return data;
                }
            } catch (Exception e) {
                GTInterestingThing.LOG.error("加载抽奖配置失败，使用默认配置", e);
            }
        }
        // 首次运行或加载失败：使用默认配置并落盘
        LotteryConfigData data = getDefaultConfig();
        save(data);
        return data;
    }

    /**
     * 将配置数据写回 JSON 文件
     */
    public static void save(LotteryConfigData data) {
        if (data == null) return;
        try {
            Path path = Paths.get(CONFIG_PATH);
            Files.createDirectories(path.getParent());
            Files.write(
                path,
                GSON.toJson(data)
                    .getBytes(StandardCharsets.UTF_8));
            GTInterestingThing.LOG.info("抽奖配置已保存（{} 个卡池）", data.pools.size());
        } catch (Exception e) {
            GTInterestingThing.LOG.error("保存抽奖配置失败", e);
        }
    }

    /**
     * 热重载配置（供指令调用，预留目标 4/5）
     *
     * @return 最新配置数据
     */
    public static LotteryConfigData reload() {
        return load();
    }

    /**
     * 默认配置：双卡池
     * <ul>
     * <li>猫猫币池（neko）：单抽 5 猫猫币，10 格——食物/基础材料为主，含少量稀有，
     * 硬保底 50 抽必出 EPIC 及以上</li>
     * <li>闪烁猫猫币池（shimmering）：单抽 1 闪烁猫猫币，8 格——更稀有，
     * 硬保底 30 抽必出 EPIC 及以上</li>
     * </ul>
     * 默认条目全部使用原版物品 ID（"minecraft:" 前缀），避免 GT 物品 ID 变动导致默认配置失效；
     * GT 材料条目由目标 4 的可视化编辑添加。
     */
    public static LotteryConfigData getDefaultConfig() {
        LotteryConfigData data = new LotteryConfigData();
        data.pools.add(createDefaultNekoPool());
        data.pools.add(createDefaultShimmeringPool());
        return data;
    }

    /**
     * 校验全部卡池：移除条目为空/总权重为 0 的卡池，并输出警告日志
     *
     * @return true 表示全部卡池合法
     */
    public static boolean validateAll(LotteryConfigData data) {
        if (data == null || data.pools == null) return false;
        boolean allValid = true;
        List<LotteryPool> valid = new ArrayList<>();
        for (LotteryPool pool : data.pools) {
            if (pool == null) {
                allValid = false;
                continue;
            }
            if (!pool.validate()) {
                GTInterestingThing.LOG.warn("抽奖卡池 {} 校验失败（条目为空或总权重为 0），已跳过", pool.getId());
                allValid = false;
                continue;
            }
            valid.add(pool);
        }
        data.pools = valid;
        return allValid && !valid.isEmpty();
    }

    // ==================== 默认卡池构建 ====================

    /** 猫猫币池：10 格，单抽 5 猫猫币，硬保底 50 抽 EPIC */
    private static LotteryPool createDefaultNekoPool() {
        LotteryPool pool = new LotteryPool(
            "neko",
            "猫猫币池",
            NekoCurrencyRegistrar.NEKO_ID,
            5,
            PityConfig.createDefault());
        List<LotteryEntry> entries = pool.getEntries();
        // COMMON：食物/基础燃料/基础金属（高权重）
        entries.add(LotteryEntry.createItemPrize("bread", "minecraft:bread", 0, 2, 6, 100, LotteryRarity.COMMON));
        entries.add(LotteryEntry.createItemPrize("apple", "minecraft:apple", 0, 2, 4, 90, LotteryRarity.COMMON));
        entries.add(LotteryEntry.createItemPrize("coal", "minecraft:coal", 0, 4, 8, 80, LotteryRarity.COMMON));
        entries.add(LotteryEntry.createItemPrize("iron", "minecraft:iron_ingot", 0, 2, 4, 70, LotteryRarity.COMMON));
        entries.add(LotteryEntry.createItemPrize("clay", "minecraft:clay_ball", 0, 4, 8, 70, LotteryRarity.COMMON));
        // RARE：金锭/红石/回回本池货币（中权重）
        entries.add(LotteryEntry.createItemPrize("gold", "minecraft:gold_ingot", 0, 1, 2, 30, LotteryRarity.RARE));
        entries.add(LotteryEntry.createItemPrize("redstone", "minecraft:redstone", 0, 4, 8, 25, LotteryRarity.RARE));
        entries.add(
            LotteryEntry.createNekoPrize("neko_back", NekoCurrencyRegistrar.NEKO_ID, 2, 4, 20, LotteryRarity.RARE));
        // EPIC：钻石/跨池货币（低权重）
        entries.add(LotteryEntry.createItemPrize("diamond", "minecraft:diamond", 0, 1, 1, 8, LotteryRarity.EPIC));
        entries.add(
            LotteryEntry.createNekoPrize(
                "shimmer_bonus",
                NekoCurrencyRegistrar.SHIMMERING_NEKO_ID,
                1,
                1,
                5,
                LotteryRarity.EPIC));
        return pool;
    }

    /** 闪烁猫猫币池：8 格，单抽 1 闪烁猫猫币，硬保底 30 抽 EPIC */
    private static LotteryPool createDefaultShimmeringPool() {
        PityConfig pity = PityConfig.createDefault();
        LotteryPool pool = new LotteryPool("shimmering", "闪烁猫猫币池", NekoCurrencyRegistrar.SHIMMERING_NEKO_ID, 1, pity);
        List<LotteryEntry> entries = pool.getEntries();
        // COMMON：金锭/石英（相对闪烁币价值仍属普通）
        entries.add(LotteryEntry.createItemPrize("gold_x", "minecraft:gold_ingot", 0, 2, 4, 60, LotteryRarity.COMMON));
        entries.add(LotteryEntry.createItemPrize("quartz", "minecraft:quartz", 0, 4, 8, 50, LotteryRarity.COMMON));
        // RARE：钻石/绿宝石/回本货币
        entries.add(LotteryEntry.createItemPrize("diamond_x", "minecraft:diamond", 0, 1, 2, 30, LotteryRarity.RARE));
        entries.add(LotteryEntry.createItemPrize("emerald", "minecraft:emerald", 0, 1, 2, 25, LotteryRarity.RARE));
        entries.add(
            LotteryEntry.createNekoPrize(
                "shimmer_back",
                NekoCurrencyRegistrar.SHIMMERING_NEKO_ID,
                1,
                2,
                20,
                LotteryRarity.RARE));
        // EPIC：钻石组/附魔金苹果
        entries.add(LotteryEntry.createItemPrize("diamond_3", "minecraft:diamond", 0, 3, 5, 8, LotteryRarity.EPIC));
        entries
            .add(LotteryEntry.createItemPrize("gap_apple", "minecraft:golden_apple", 1, 1, 1, 6, LotteryRarity.EPIC));
        // LEGENDARY：下界之星（极低权重）
        entries.add(
            LotteryEntry.createItemPrize("nether_star", "minecraft:nether_star", 0, 1, 1, 2, LotteryRarity.LEGENDARY));
        return pool;
    }
}

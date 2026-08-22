package com.miaokatze.gtit.lottery;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializer;
import com.miaokatze.gtit.config.ConfigMigrationUtil;
import com.miaokatze.gtit.main.GTInterestingThing;
import com.miaokatze.gtit.trade.NekoCurrencyRegistrar;
import com.miaokatze.gtit.trade.v2.NekoBigItemStack;
import com.miaokatze.gtit.util.NbtBase64Util;

import cpw.mods.fml.common.registry.GameRegistry;

/**
 * 抽奖配置加载（JSON；v1.7.7 G4 存储结构重构）
 * <p>
 * 配置文件路径：{@code config/gtit/lottery/lottery.json}。
 * <p>
 * 兼容：加载时若新路径缺失且旧文件 {@code config/gtit/lottery.json} 存在，
 * 则整体迁移到新路径，旧文件重命名为 {@code .bak} 保留。
 * 首启写入默认配置（双卡池：猫猫币池 + 闪烁猫猫币池），支持 reload 热重载。
 * <p>
 * 配置结构（Gson 序列化，参照 {@link com.miaokatze.gtit.signin.DailySignInConfig} 模式）：
 * <ul>
 * <li>{@code pools[]}：卡池列表，每池含 id/name/iconItem/costItems（旧字段 costPerDraw/nekoCurrencyId 保留兼容）</li>
 * <li>{@code pools[].costItems[]}：需求物品（item/meta/amount/nbtBase64/oreDict，v1.7.6 货币解绑）</li>
 * <li>{@code pools[].entries[]}：奖品条目（item/meta/minAmount/maxAmount/weight/rarity/nekoCurrencyId/nbtBase64）</li>
 * <li>{@code pools[].pityConfig}：保底配置（软保底阈值/增量、硬保底阈值/保底稀有度）</li>
 * </ul>
 * rarity 字段大小写不敏感（{@code "epic"}/{@code "EPIC"} 均可），未知值回退 COMMON。
 * 旧池（仅 nekoCurrencyId/costPerDraw）加载时自动合成 costItems 并补缺省图标（对应猫猫币物品）。
 */
public class LotteryConfig {

    /** 新配置文件路径（相对游戏根目录） */
    private static final String CONFIG_PATH = "config/gtit/lottery/lottery.json";
    /** 旧配置文件路径（v1.7.7 G4 兼容迁移用） */
    private static final String LEGACY_CONFIG_PATH = "config/gtit/lottery.json";
    /** Gson 实例（rarity 枚举大小写不敏感适配器 + NekoBigItemStack 物品适配器） */
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting()
        .disableHtmlEscaping()
        .registerTypeAdapter(
            LotteryRarity.class,
            (JsonSerializer<LotteryRarity>) (src, typeOfSrc, context) -> context.serialize(src.name()))
        .registerTypeAdapter(
            LotteryRarity.class,
            (JsonDeserializer<LotteryRarity>) (json, typeOfT, context) -> LotteryRarity.fromString(json.getAsString()))
        .registerTypeAdapter(NekoBigItemStack.class, (JsonSerializer<NekoBigItemStack>) (src, typeOfSrc, context) -> {
            // 序列化为 {item, meta, amount, nbtBase64, oreDict}（与 NekoTradeEntry.ItemEntry 同风格）
            JsonObject obj = new JsonObject();
            ItemStack base = src.getBaseStack();
            if (base != null && base.getItem() != null) {
                obj.addProperty(
                    "item",
                    Item.itemRegistry.getNameForObject(base.getItem())
                        .toString());
                obj.addProperty("meta", base.getItemDamage());
                if (base.hasTagCompound() && base.getTagCompound() != null) {
                    obj.addProperty("nbtBase64", NbtBase64Util.nbtToBase64(base.getTagCompound()));
                }
            } else {
                obj.addProperty("item", "");
                obj.addProperty("meta", 0);
            }
            obj.addProperty("amount", src.getStackSize());
            if (src.hasOreDict()) {
                obj.addProperty("oreDict", src.getOreDict());
            }
            return obj;
        })
        .registerTypeAdapter(NekoBigItemStack.class, (JsonDeserializer<NekoBigItemStack>) (json, typeOfT, context) -> {
            JsonObject obj = json.getAsJsonObject();
            String itemId = obj.has("item") ? obj.get("item")
                .getAsString() : "";
            int meta = obj.has("meta") ? obj.get("meta")
                .getAsInt() : 0;
            int amount = obj.has("amount") ? obj.get("amount")
                .getAsInt() : 1;
            String oreDict = obj.has("oreDict") ? obj.get("oreDict")
                .getAsString() : "";
            String[] parts = itemId.split(":", 2);
            Item item = parts.length == 2 ? GameRegistry.findItem(parts[0], parts[1]) : null;
            if (item == null) return null; // 物品不存在：Gson 列表会保留 null，加载后由迁移步骤清理
            ItemStack stack = new ItemStack(item, 1, meta);
            if (obj.has("nbtBase64")) {
                NBTTagCompound nbt = NbtBase64Util.nbtFromBase64(
                    obj.get("nbtBase64")
                        .getAsString());
                if (nbt != null) stack.setTagCompound(nbt);
            }
            return new NekoBigItemStack(Math.max(1, amount), oreDict, stack);
        })
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
     * <p>
     * v1.7.7 G4：优先读取新路径；新路径缺失且旧路径存在时，迁移旧文件到新路径，
     * 旧文件重命名为 {@code .bak} 保留。
     *
     * @return 配置数据（永不为 null，失败回退默认）
     */
    public static LotteryConfigData load() {
        Path path = Paths.get(CONFIG_PATH);
        if (!Files.exists(path)) {
            Path legacy = Paths.get(LEGACY_CONFIG_PATH);
            if (Files.exists(legacy)) {
                try {
                    migrateFromLegacy(legacy, path);
                    // 迁移后继续从新路径读取
                } catch (Exception e) {
                    GTInterestingThing.LOG.error("抽奖配置从旧路径迁移失败，回退默认配置", e);
                    LotteryConfigData fallback = getDefaultConfig();
                    save(fallback);
                    return fallback;
                }
            }
        }

        if (Files.exists(path)) {
            try {
                String json = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
                LotteryConfigData data = GSON.fromJson(json, LotteryConfigData.class);
                if (data != null && data.pools != null && !data.pools.isEmpty()) {
                    // v1.7.6 迁移：旧池合成 costItems + 补缺省图标 + 清理无效需求条目
                    for (LotteryPool pool : data.pools) {
                        if (pool != null) migratePool(pool);
                    }
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
     * 从旧路径迁移配置到新路径（v1.7.7 G4）
     *
     * @param legacyPath 旧配置文件路径
     * @param newPath    新配置文件路径
     */
    private static void migrateFromLegacy(Path legacyPath, Path newPath) throws Exception {
        Files.createDirectories(newPath.getParent());
        String json = new String(Files.readAllBytes(legacyPath), StandardCharsets.UTF_8);
        LotteryConfigData data = GSON.fromJson(json, LotteryConfigData.class);
        if (data != null) {
            Files.write(
                newPath,
                GSON.toJson(data)
                    .getBytes(StandardCharsets.UTF_8));
        }
        // O2-14: 旧文件退役收尾收编 ConfigMigrationUtil.retireLegacyAsBak（语义与日志格式不变）
        ConfigMigrationUtil.retireLegacyAsBak(legacyPath, newPath, "抽奖配置", "抽奖配置文件");
    }

    /**
     * 将配置数据写回 JSON 文件
     */
    public static void save(LotteryConfigData data) {
        if (data == null) return;
        try {
            // v1.7.7 G3②：保存前再次截断，防御运行期条目被动态追加到超过上限
            if (data.pools != null) {
                for (LotteryPool pool : data.pools) {
                    if (pool != null) pool.truncateEntriesIfNeeded();
                }
            }
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
     * <li>闪烁猫猫币池（shimmering）：单抽 1 闪烁猫猫币，10 格——更稀有，
     * 硬保底 30 抽必出 EPIC 及以上（v1.7.19 由 8 格扩展至 10 格，新增青金石/钻石块）</li>
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

    /**
     * 单池迁移（加载后调用）
     * <p>
     * ① 清理 costItems 中的 null 条目（物品已卸载导致反序列化失败）；
     * ② 旧字段（nekoCurrencyId × costPerDraw）合成 costItems；
     * ③ 缺省图标回退为对应猫猫币物品；
     * ④ 截断超过 {@link LotteryPool#MAX_ENTRIES} 的条目（v1.7.7 G3②）；
     * ⑤ v1.7.20：旧版默认"闪烁猫猫币池"条目数不足 10 时，智能补齐青金石/钻石块。
     */
    private static void migratePool(LotteryPool pool) {
        // 清理反序列化失败（物品不存在）的 null 条目
        List<NekoBigItemStack> costs = pool.getCostItems();
        costs.removeIf(cost -> cost == null || cost.getBaseStack() == null || cost.getStackSize() <= 0);
        // 旧字段合成（costItems 为空时生效）
        pool.synthesizeCostItemsFromLegacy();

        // v1.7.20：对旧版默认闪烁池进行无感升级（仅当条目与旧默认完全一致时才补齐，
        // 避免覆盖玩家自定义的闪烁池内容）。
        if ("shimmering".equals(pool.getId()) && pool.getEntries() != null) {
            upgradeDefaultShimmeringPoolIfNeeded(pool.getEntries());
        }

        // v1.7.7 G3②：加载时截断超限条目，防止越界动画失效
        pool.truncateEntriesIfNeeded();
        // 缺省图标：回退对应猫猫币物品
        if (pool.getIconItem()
            .isEmpty()) {
            ItemStack currencyStack = NekoCurrencyRegistrar.getItemStack(pool.getNekoCurrencyId(), 1);
            if (currencyStack != null) {
                pool.setIconFromItemStack(currencyStack);
            }
        }
    }

    /**
     * 智能升级旧版默认闪烁猫猫币池
     * <p>
     * 若当前条目集合与 v1.7.19 之前的旧默认 8 条完全一致，
     * 则追加青金石与钻石块两个条目，使池扩展到 10 条。
     * 只要条目 ID 集合有任何差异（玩家自定义），就不做任何改动。
     *
     * @param entries 池条目列表
     */
    private static void upgradeDefaultShimmeringPoolIfNeeded(List<LotteryEntry> entries) {
        // 旧默认 8 条目的 ID（v1.7.19 之前）
        java.util.Set<String> oldDefaultIds = new java.util.HashSet<>(
            java.util.Arrays.asList(
                "gold_x",
                "quartz",
                "diamond_x",
                "emerald",
                "shimmer_back",
                "diamond_3",
                "gap_apple",
                "nether_star"));
        // 新默认 10 条目的 ID
        java.util.Set<String> newDefaultIds = new java.util.HashSet<>(
            java.util.Arrays.asList(
                "gold_x",
                "quartz",
                "diamond_x",
                "emerald",
                "lapis_lazuli",
                "shimmer_back",
                "diamond_3",
                "gap_apple",
                "diamond_block",
                "nether_star"));

        // 当前条目 ID 集合
        java.util.Set<String> currentIds = new java.util.HashSet<>();
        for (LotteryEntry entry : entries) {
            if (entry == null) continue;
            currentIds.add(entry.getId());
        }

        // 仅当当前条目与旧默认 8 条完全一致，且尚未包含新条目时，才进行升级
        if (currentIds.equals(oldDefaultIds) && !currentIds.contains("lapis_lazuli")
            && !currentIds.contains("diamond_block")) {
            entries.add(LotteryEntry.createItemPrize("lapis_lazuli", "minecraft:dye", 4, 4, 8, 22, LotteryRarity.RARE));
            entries.add(
                LotteryEntry
                    .createItemPrize("diamond_block", "minecraft:diamond_block", 0, 1, 1, 5, LotteryRarity.EPIC));
            GTInterestingThing.LOG.info("闪烁猫猫币池已由旧版默认 8 条自动升级到 10 条（新增青金石/钻石块）");
        } else if (!currentIds.equals(newDefaultIds) && !currentIds.equals(oldDefaultIds)) {
            // 既不是新默认也不是旧默认：玩家自定义池，不做任何改动
            GTInterestingThing.LOG.debug("闪烁猫猫币池为玩家自定义配置，跳过默认条目升级");
        }
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
        // 图标 + 需求物品（5 猫猫币/抽）
        ItemStack coin = NekoCurrencyRegistrar.getItemStack(NekoCurrencyRegistrar.NEKO_ID, 5);
        if (coin != null) {
            pool.setIconFromItemStack(coin);
            pool.getCostItems()
                .add(new NekoBigItemStack(coin));
        }
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

    /**
     * 闪烁猫猫币池：10 格（v1.7.19 由 8 条扩展至 10 条，新增青金石/钻石块；
     * 历史：v1.7.9 曾误加 lapis/ender_pearl 至 10，v1.7.10 移除恢复 8 条），
     * 单抽 1 闪烁猫猫币，硬保底 30 抽 EPIC
     */
    private static LotteryPool createDefaultShimmeringPool() {
        PityConfig pity = PityConfig.createDefault();
        LotteryPool pool = new LotteryPool("shimmering", "闪烁猫猫币池", NekoCurrencyRegistrar.SHIMMERING_NEKO_ID, 1, pity);
        // 图标 + 需求物品（1 闪烁猫猫币/抽）
        ItemStack coin = NekoCurrencyRegistrar.getItemStack(NekoCurrencyRegistrar.SHIMMERING_NEKO_ID, 1);
        if (coin != null) {
            pool.setIconFromItemStack(coin);
            pool.getCostItems()
                .add(new NekoBigItemStack(coin));
        }
        List<LotteryEntry> entries = pool.getEntries();
        // COMMON：金锭/石英（相对闪烁币价值仍属普通）
        entries.add(LotteryEntry.createItemPrize("gold_x", "minecraft:gold_ingot", 0, 2, 4, 60, LotteryRarity.COMMON));
        entries.add(LotteryEntry.createItemPrize("quartz", "minecraft:quartz", 0, 4, 8, 50, LotteryRarity.COMMON));
        // RARE：钻石/绿宝石/青金石/回本货币
        entries.add(LotteryEntry.createItemPrize("diamond_x", "minecraft:diamond", 0, 1, 2, 30, LotteryRarity.RARE));
        entries.add(LotteryEntry.createItemPrize("emerald", "minecraft:emerald", 0, 1, 2, 25, LotteryRarity.RARE));
        // 青金石（dye meta 4 = lapis_lazuli）：附魔/装饰常用材料，补充 RARE 档位
        entries.add(LotteryEntry.createItemPrize("lapis_lazuli", "minecraft:dye", 4, 4, 8, 22, LotteryRarity.RARE));
        entries.add(
            LotteryEntry.createNekoPrize(
                "shimmer_back",
                NekoCurrencyRegistrar.SHIMMERING_NEKO_ID,
                1,
                2,
                20,
                LotteryRarity.RARE));
        // EPIC：钻石组/附魔金苹果/钻石块
        entries.add(LotteryEntry.createItemPrize("diamond_3", "minecraft:diamond", 0, 3, 5, 8, LotteryRarity.EPIC));
        entries
            .add(LotteryEntry.createItemPrize("gap_apple", "minecraft:golden_apple", 1, 1, 1, 6, LotteryRarity.EPIC));
        // 钻石块：高价值压缩形态，单格出 1 个，补充 EPIC 档位
        entries.add(
            LotteryEntry.createItemPrize("diamond_block", "minecraft:diamond_block", 0, 1, 1, 5, LotteryRarity.EPIC));
        // LEGENDARY：下界之星（极低权重）
        entries.add(
            LotteryEntry.createItemPrize("nether_star", "minecraft:nether_star", 0, 1, 1, 2, LotteryRarity.LEGENDARY));
        return pool;
    }
}

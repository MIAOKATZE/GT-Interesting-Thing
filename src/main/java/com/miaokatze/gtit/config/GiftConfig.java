package com.miaokatze.gtit.config;

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
import com.google.gson.annotations.SerializedName;
import com.miaokatze.gtit.main.GTInterestingThing;
import com.miaokatze.gtit.util.NbtBase64Util;

import cpw.mods.fml.common.registry.GameRegistry;

/**
 * 新手宝箱配置管理
 * <p>
 * 配置文件路径: config/gtit/gift_config.json
 * <p>
 * v1.5.12+: 由手写 JSON 解析迁移到 Gson（参照 NekoPageConfig / NekoTradeConfig 模式），
 * 移除 ~200 行脆弱的手写序列化/反序列化代码（escapeJsonString / extractJsonArray /
 * parseItemEntries / extractStringValue 等）。JSON 文件格式保持向后兼容。
 */
public class GiftConfig {

    private static final String CONFIG_PATH = "config/gtit/gift_config.json";
    // 注意：不启用 serializeNulls，保持与手写序列化器一致的输出风格——
    // 无 NBT 时 nbtBase64 字段为 null，Gson 默认不输出该字段，JSON 保持简洁。
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting()
        .disableHtmlEscaping()
        .create();

    private static List<ItemEntry> guaranteedItems = new ArrayList<>();
    private static List<ItemEntry> randomItems = new ArrayList<>();
    private static int randomCount = 2;

    public static void init() {
        loadConfig();
    }

    public static List<ItemEntry> getGuaranteedItems() {
        return guaranteedItems;
    }

    public static List<ItemEntry> getRandomItems() {
        return randomItems;
    }

    public static int getRandomCount() {
        return randomCount;
    }

    public static void setRandomCount(int count) {
        randomCount = Math.max(0, count);
    }

    public static void setGuaranteedItems(List<ItemEntry> items) {
        guaranteedItems = new ArrayList<>(items);
    }

    public static void setRandomItems(List<ItemEntry> items) {
        randomItems = new ArrayList<>(items);
    }

    public static void resetToDefault() {
        guaranteedItems = createDefaultGuaranteedItems();
        randomItems = createDefaultRandomItems();
        randomCount = 2;
        saveConfig();
    }

    public static void loadConfig() {
        Path path = Paths.get(CONFIG_PATH);
        if (Files.exists(path)) {
            try {
                String json = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
                GiftConfigData data = GSON.fromJson(json, GiftConfigData.class);
                if (data != null) {
                    guaranteedItems = data.guaranteedItems != null ? data.guaranteedItems : new ArrayList<>();
                    randomItems = data.randomItems != null ? data.randomItems : new ArrayList<>();
                    randomCount = data.randomCount;
                    GTInterestingThing.LOG.info("新手宝箱配置已加载");
                    // 确保默认值（空列表回退到默认，避免宝箱为空）
                    if (guaranteedItems.isEmpty()) guaranteedItems = createDefaultGuaranteedItems();
                    if (randomItems.isEmpty()) randomItems = createDefaultRandomItems();
                    if (randomCount <= 0) randomCount = 2;
                    return;
                }
            } catch (Exception e) {
                GTInterestingThing.LOG.error("加载新手宝箱配置失败，使用默认配置", e);
            }
        }
        // 首次运行或加载失败，使用默认配置并落盘
        guaranteedItems = createDefaultGuaranteedItems();
        randomItems = createDefaultRandomItems();
        randomCount = 2;
        saveConfig();
    }

    public static void saveConfig() {
        try {
            Path path = Paths.get(CONFIG_PATH);
            Files.createDirectories(path.getParent());
            GiftConfigData data = new GiftConfigData();
            data.guaranteedItems = guaranteedItems;
            data.randomItems = randomItems;
            data.randomCount = randomCount;
            String json = GSON.toJson(data);
            Files.write(path, json.getBytes(StandardCharsets.UTF_8));
            GTInterestingThing.LOG.info("新手宝箱配置已保存");
        } catch (Exception e) {
            GTInterestingThing.LOG.error("保存新手宝箱配置失败", e);
        }
    }

    private static List<ItemEntry> createDefaultGuaranteedItems() {
        List<ItemEntry> items = new ArrayList<>();
        items.add(new ItemEntry("minecraft:bread", 16, 0));
        items.add(new ItemEntry("minecraft:torch", 64, 0));
        items.add(new ItemEntry("minecraft:bed", 1, 0));
        items.add(new ItemEntry("minecraft:chest", 8, 0));
        // 猫猫币：注册名为 gtit:neko_coin（miao_coin 仅为材质名）
        items.add(new ItemEntry("gtit:neko_coin", 1, 0));
        return items;
    }

    private static List<ItemEntry> createDefaultRandomItems() {
        List<ItemEntry> items = new ArrayList<>();
        items.add(new ItemEntry("gtit:ring_skywalk", 1, 0));
        items.add(new ItemEntry("gtit:ring_ironheart", 1, 0));
        items.add(new ItemEntry("gtit:ring_dragon_breath", 1, 0));
        items.add(new ItemEntry("gtit:ring_mountainbreaker", 1, 0));
        items.add(new ItemEntry("gtit:ring_tempest", 1, 0));
        return items;
    }

    /**
     * 顶层配置数据（对应 gift_config.json 根对象）
     * <p>
     * 字段名通过 @SerializedName 显式映射到 snake_case JSON 键，保持与历史配置文件兼容。
     */
    private static class GiftConfigData {

        @SerializedName("guaranteed_items")
        private List<ItemEntry> guaranteedItems = new ArrayList<>();

        @SerializedName("random_items")
        private List<ItemEntry> randomItems = new ArrayList<>();

        @SerializedName("random_count")
        private int randomCount = 2;
    }

    /**
     * 物品条目
     * <p>
     * 支持携带 NBT 数据：{@code nbtBase64} 用于 JSON 序列化/反序列化，{@code nbt} 用于运行时缓存。
     * 无 NBT 时 {@code nbtBase64} 为 null，Gson 默认不输出 null（因 serializeNulls 会输出，需注意）。
     * {@code nbt} 标记为 transient，Gson 默认不序列化 transient 字段。
     * <p>
     * JSON 键映射（向后兼容历史配置）：
     * <ul>
     * <li>{@code itemId} → JSON {@code "item"}</li>
     * <li>{@code amount} → JSON {@code "amount"}</li>
     * <li>{@code meta} → JSON {@code "meta"}</li>
     * <li>{@code nbtBase64} → JSON {@code "nbtBase64"}</li>
     * </ul>
     */
    public static class ItemEntry {

        @SerializedName("item")
        public final String itemId;
        public final int amount;
        public final int meta;
        /** JSON 存储：Base64 编码的 NBT 二进制数据；无 NBT 时为 null */
        @SerializedName("nbtBase64")
        private String nbtBase64;
        /**
         * 运行时缓存：反序列化后的 NBT 数据；由 {@link #toItemStack()} 按需应用。
         * transient：Gson 默认不序列化 transient 字段，确保 NBT 二进制不写入 JSON。
         */
        private transient NBTTagCompound nbt;

        /**
         * 三参数构造方法（向后兼容旧配置与默认配置）
         *
         * @param itemId 物品 ID（modid:name）
         * @param amount 数量
         * @param meta   元数据
         */
        public ItemEntry(String itemId, int amount, int meta) {
            this(itemId, amount, meta, null);
        }

        /**
         * 四参数构造方法（支持 NBT）
         *
         * @param itemId 物品 ID（modid:name）
         * @param amount 数量
         * @param meta   元数据
         * @param nbt    NBT 数据；为 null 时表示无 NBT
         */
        public ItemEntry(String itemId, int amount, int meta, NBTTagCompound nbt) {
            this.itemId = itemId;
            this.amount = amount;
            this.meta = meta;
            this.nbt = nbt;
            this.nbtBase64 = NbtBase64Util.nbtToBase64(nbt);
        }

        /**
         * 将条目转换回 ItemStack，自动应用 NBT 数据（若存在）
         *
         * @return 带 NBT 的 ItemStack；找不到物品或 ID 格式错误时返回 null
         */
        public ItemStack toItemStack() {
            String[] parts = itemId.split(":");
            if (parts.length != 2) return null;
            Item item = GameRegistry.findItem(parts[0], parts[1]);
            if (item == null) return null;
            ItemStack stack = new ItemStack(item, amount, meta);

            // 优先使用运行时缓存的 NBT；否则从 Base64 解析（应对刚反序列化、缓存未填充的情况）
            NBTTagCompound tagToApply = nbt;
            if (tagToApply == null && nbtBase64 != null && !nbtBase64.isEmpty()) {
                tagToApply = NbtBase64Util.nbtFromBase64(nbtBase64);
                nbt = tagToApply;
            }
            if (tagToApply != null) {
                stack.setTagCompound(tagToApply);
            }
            return stack;
        }

        /** 获取 NBT 的 Base64 表示（JSON 用） */
        public String getNbtBase64() {
            return nbtBase64;
        }

        /** 获取运行时 NBT 数据 */
        public NBTTagCompound getNbt() {
            return nbt;
        }
    }
}

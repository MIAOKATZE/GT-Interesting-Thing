package com.miaokatze.gtit.config;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import com.miaokatze.gtit.main.GTInterestingThing;
import com.miaokatze.gtit.util.NbtBase64Util;

import cpw.mods.fml.common.registry.GameRegistry;

/**
 * 新手宝箱配置管理
 * 配置文件路径: config/gtit/gift_config.json
 */
public class GiftConfig {

    private static final String CONFIG_DIR = "config" + File.separator + "gtit";
    private static final String CONFIG_FILE = CONFIG_DIR + File.separator + "gift_config.json";

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
        File file = new File(CONFIG_FILE);
        if (file.exists()) {
            try (FileReader reader = new FileReader(file)) {
                StringBuilder sb = new StringBuilder();
                int ch;
                while ((ch = reader.read()) != -1) {
                    sb.append((char) ch);
                }
                parseConfig(sb.toString());
                GTInterestingThing.LOG.info("新手宝箱配置已加载");
                return;
            } catch (IOException e) {
                GTInterestingThing.LOG.error("加载新手宝箱配置失败，使用默认配置", e);
            }
        }
        // 首次运行或加载失败，使用默认配置
        guaranteedItems = createDefaultGuaranteedItems();
        randomItems = createDefaultRandomItems();
        randomCount = 2;
        saveConfig();
    }

    public static void saveConfig() {
        File dir = new File(CONFIG_DIR);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        try (FileWriter writer = new FileWriter(CONFIG_FILE)) {
            writer.write(serializeConfig());
            GTInterestingThing.LOG.info("新手宝箱配置已保存");
        } catch (IOException e) {
            GTInterestingThing.LOG.error("保存新手宝箱配置失败", e);
        }
    }

    private static List<ItemEntry> createDefaultGuaranteedItems() {
        List<ItemEntry> items = new ArrayList<>();
        items.add(new ItemEntry("minecraft:bread", 16, 0));
        items.add(new ItemEntry("minecraft:torch", 64, 0));
        items.add(new ItemEntry("minecraft:bed", 1, 0));
        items.add(new ItemEntry("minecraft:chest", 8, 0));
        return items;
    }

    private static List<ItemEntry> createDefaultRandomItems() {
        List<ItemEntry> items = new ArrayList<>();
        items.add(new ItemEntry("gtit:ring_skywalk", 1, 0));
        items.add(new ItemEntry("gtit:ring_gluttony", 1, 0));
        items.add(new ItemEntry("gtit:ring_ironheart", 1, 0));
        items.add(new ItemEntry("gtit:ring_dragon_breath", 1, 0));
        items.add(new ItemEntry("gtit:ring_mountainbreaker", 1, 0));
        items.add(new ItemEntry("gtit:ring_tempest", 1, 0));
        return items;
    }

    // 简易JSON序列化（避免引入Gson依赖）
    private static String serializeConfig() {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"guaranteed_items\": [\n");
        for (int i = 0; i < guaranteedItems.size(); i++) {
            ItemEntry e = guaranteedItems.get(i);
            sb.append("    {\"item\": \"")
                .append(escapeJsonString(e.itemId))
                .append("\", \"amount\": ")
                .append(e.amount)
                .append(", \"meta\": ")
                .append(e.meta);
            // 若存在 NBT 数据，追加 Base64 字段，保持无 NBT 时 JSON 简洁
            if (e.nbtBase64 != null && !e.nbtBase64.isEmpty()) {
                sb.append(", \"nbtBase64\": \"")
                    .append(escapeJsonString(e.nbtBase64))
                    .append("\"");
            }
            sb.append("}");
            if (i < guaranteedItems.size() - 1) sb.append(",");
            sb.append("\n");
        }
        sb.append("  ],\n");
        sb.append("  \"random_items\": [\n");
        for (int i = 0; i < randomItems.size(); i++) {
            ItemEntry e = randomItems.get(i);
            sb.append("    {\"item\": \"")
                .append(escapeJsonString(e.itemId))
                .append("\", \"amount\": ")
                .append(e.amount)
                .append(", \"meta\": ")
                .append(e.meta);
            // 若存在 NBT 数据，追加 Base64 字段，保持无 NBT 时 JSON 简洁
            if (e.nbtBase64 != null && !e.nbtBase64.isEmpty()) {
                sb.append(", \"nbtBase64\": \"")
                    .append(escapeJsonString(e.nbtBase64))
                    .append("\"");
            }
            sb.append("}");
            if (i < randomItems.size() - 1) sb.append(",");
            sb.append("\n");
        }
        sb.append("  ],\n");
        sb.append("  \"random_count\": ")
            .append(randomCount)
            .append("\n");
        sb.append("}\n");
        return sb.toString();
    }

    /**
     * 转义 JSON 字符串中的特殊字符（双引号、反斜杠、控制字符等）
     *
     * @param input 原始字符串
     * @return 符合 JSON 字符串规范的转义后字符串
     */
    private static String escapeJsonString(String input) {
        if (input == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            switch (c) {
                case '"':
                    sb.append("\\\"");
                    break;
                case '\\':
                    sb.append("\\\\");
                    break;
                case '\b':
                    sb.append("\\b");
                    break;
                case '\f':
                    sb.append("\\f");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.toString();
    }

    private static void parseConfig(String json) {
        guaranteedItems.clear();
        randomItems.clear();

        // 简易JSON解析
        String trimmed = json.trim();
        if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) return;

        // 提取 guaranteed_items
        String guaranteedStr = extractJsonArray(trimmed, "guaranteed_items");
        if (guaranteedStr != null) {
            guaranteedItems = parseItemEntries(guaranteedStr);
        }

        // 提取 random_items
        String randomStr = extractJsonArray(trimmed, "random_items");
        if (randomStr != null) {
            randomItems = parseItemEntries(randomStr);
        }

        // 提取 random_count
        String countStr = extractJsonValue(trimmed, "random_count");
        if (countStr != null) {
            try {
                randomCount = Integer.parseInt(countStr.trim());
            } catch (NumberFormatException e) {
                randomCount = 2;
            }
        }

        // 确保默认值
        if (guaranteedItems.isEmpty()) guaranteedItems = createDefaultGuaranteedItems();
        if (randomItems.isEmpty()) randomItems = createDefaultRandomItems();
    }

    private static String extractJsonArray(String json, String key) {
        String searchKey = "\"" + key + "\": [";
        int start = json.indexOf(searchKey);
        if (start < 0) return null;
        start += searchKey.length();
        int bracketCount = 1;
        int end = start;
        while (end < json.length() && bracketCount > 0) {
            char c = json.charAt(end);
            if (c == '[') bracketCount++;
            else if (c == ']') bracketCount--;
            end++;
        }
        return json.substring(start, end - 1);
    }

    private static String extractJsonValue(String json, String key) {
        String searchKey = "\"" + key + "\":";
        int start = json.indexOf(searchKey);
        if (start < 0) return null;
        start += searchKey.length();
        int end = start;
        while (end < json.length() && json.charAt(end) != ',' && json.charAt(end) != '}' && json.charAt(end) != '\n') {
            end++;
        }
        return json.substring(start, end)
            .trim();
    }

    private static List<ItemEntry> parseItemEntries(String arrayStr) {
        List<ItemEntry> entries = new ArrayList<>();
        // 按花括号分割
        int i = 0;
        while (i < arrayStr.length()) {
            int objStart = arrayStr.indexOf('{', i);
            if (objStart < 0) break;
            int objEnd = arrayStr.indexOf('}', objStart);
            if (objEnd < 0) break;

            String obj = arrayStr.substring(objStart + 1, objEnd);
            String itemId = extractStringValue(obj, "item");
            String amountStr = extractStringValue(obj, "amount");
            String metaStr = extractStringValue(obj, "meta");
            // 向后兼容：旧配置可能不存在 nbtBase64 字段，缺失或为空时视为无 NBT
            String nbtBase64 = extractStringValue(obj, "nbtBase64");
            if (nbtBase64 != null && nbtBase64.isEmpty()) {
                nbtBase64 = null;
            }

            if (itemId != null) {
                int amount = amountStr != null ? Integer.parseInt(amountStr.trim()) : 1;
                int meta = metaStr != null ? Integer.parseInt(metaStr.trim()) : 0;
                NBTTagCompound nbt = NbtBase64Util.nbtFromBase64(nbtBase64);
                entries.add(new ItemEntry(itemId, amount, meta, nbt));
            }
            i = objEnd + 1;
        }
        return entries;
    }

    private static String extractStringValue(String obj, String key) {
        String searchKey = "\"" + key + "\":";
        int start = obj.indexOf(searchKey);
        if (start < 0) return null;
        start += searchKey.length();
        // 跳过空格
        while (start < obj.length() && obj.charAt(start) == ' ') start++;
        if (start >= obj.length()) return null;

        if (obj.charAt(start) == '"') {
            // 字符串值
            int end = obj.indexOf('"', start + 1);
            if (end < 0) return null;
            return obj.substring(start + 1, end);
        } else {
            // 数字值
            int end = start;
            while (end < obj.length() && obj.charAt(end) != ',' && obj.charAt(end) != '}' && obj.charAt(end) != ' ') {
                end++;
            }
            return obj.substring(start, end);
        }
    }

    /**
     * 物品条目
     * <p>
     * 支持携带 NBT 数据：{@code nbtBase64} 用于 JSON 序列化/反序列化，{@code nbt} 用于运行时缓存。
     * 无 NBT 时 {@code nbtBase64} 为 null，序列化时不输出该字段。
     */
    public static class ItemEntry {

        public final String itemId;
        public final int amount;
        public final int meta;
        /** JSON 存储：Base64 编码的 NBT 二进制数据；无 NBT 时为 null */
        private String nbtBase64;
        /** 运行时缓存：反序列化后的 NBT 数据；由 {@link #toItemStack()} 按需应用 */
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

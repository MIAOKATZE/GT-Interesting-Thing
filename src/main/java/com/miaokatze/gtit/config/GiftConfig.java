package com.miaokatze.gtit.config;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import com.miaokatze.gtit.main.GTInterestingThing;

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
                .append(e.itemId)
                .append("\", \"amount\": ")
                .append(e.amount)
                .append(", \"meta\": ")
                .append(e.meta)
                .append("}");
            if (i < guaranteedItems.size() - 1) sb.append(",");
            sb.append("\n");
        }
        sb.append("  ],\n");
        sb.append("  \"random_items\": [\n");
        for (int i = 0; i < randomItems.size(); i++) {
            ItemEntry e = randomItems.get(i);
            sb.append("    {\"item\": \"")
                .append(e.itemId)
                .append("\", \"amount\": ")
                .append(e.amount)
                .append(", \"meta\": ")
                .append(e.meta)
                .append("}");
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

            if (itemId != null) {
                int amount = amountStr != null ? Integer.parseInt(amountStr.trim()) : 1;
                int meta = metaStr != null ? Integer.parseInt(metaStr.trim()) : 0;
                entries.add(new ItemEntry(itemId, amount, meta));
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
     */
    public static class ItemEntry {

        public final String itemId;
        public final int amount;
        public final int meta;

        public ItemEntry(String itemId, int amount, int meta) {
            this.itemId = itemId;
            this.amount = amount;
            this.meta = meta;
        }

        public ItemStack toItemStack() {
            String[] parts = itemId.split(":");
            if (parts.length != 2) return null;
            Item item = GameRegistry.findItem(parts[0], parts[1]);
            if (item == null) return null;
            return new ItemStack(item, amount, meta);
        }
    }
}

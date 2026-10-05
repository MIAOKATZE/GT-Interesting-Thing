package com.miaokatze.gtit.trade.v2;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.miaokatze.gtit.trade.NekoTradeEntry;

/**
 * 编辑 ACTION 公共工具（O2-05 策略表化：自 {@link NekoEditActionHandler} 工具段逐字搬移）
 * <p>
 * 聊天回包三件（sendSuccess/sendError/sendInfo）与 ItemEntry 列表解析为全编辑域共用；
 * 各域类与 handler 经 static import 以原调用形式使用（逐字搬移约束）。
 */
final class EditActionsCommon {

    private EditActionsCommon() {
        // 静态工具类，禁止实例化
    }

    /**
     * 解析 JSON 数组为 ItemEntry 列表
     *
     * @param array JSON 数组
     * @return ItemEntry 列表
     */
    static List<NekoTradeEntry.ItemEntry> parseItemEntries(JsonArray array) {
        List<NekoTradeEntry.ItemEntry> items = new ArrayList<>();
        if (array == null) return items;

        for (int i = 0; i < array.size(); i++) {
            JsonObject itemJson = array.get(i)
                .getAsJsonObject();
            NekoTradeEntry.ItemEntry entry = new NekoTradeEntry.ItemEntry();
            if (itemJson.has("item")) entry.setItem(
                itemJson.get("item")
                    .getAsString());
            if (itemJson.has("meta")) entry.setMeta(
                itemJson.get("meta")
                    .getAsInt());
            if (itemJson.has("amount")) entry.setAmount(
                itemJson.get("amount")
                    .getAsInt());
            if (itemJson.has("nbtBase64")) entry.setNbtBase64(
                itemJson.get("nbtBase64")
                    .getAsString());
            items.add(entry);
        }
        return items;
    }

    /** Trade-only parser: reject malformed entries instead of silently losing choices. */
    static List<NekoTradeEntry.ItemEntry> parseTradeItemEntries(JsonArray array) {
        List<NekoTradeEntry.ItemEntry> result = new ArrayList<>();
        if (array == null) return result;
        if (array.size() > 16) throw new IllegalArgumentException("单侧最多 16 格");
        for (int i = 0; i < array.size(); i++) result.add(
            parseTradeChoice(
                array.get(i)
                    .getAsJsonObject(),
                false));
        return result;
    }

    private static NekoTradeEntry.ItemEntry parseTradeChoice(JsonObject json, boolean child) {
        if (!json.has("amount") || !json.get("amount")
            .getAsString()
            .matches("[0-9]+")) {
            throw new IllegalArgumentException("选项数量必须是正整数");
        }
        java.math.BigInteger amount = new java.math.BigInteger(
            json.get("amount")
                .getAsString());
        if (amount.signum() <= 0 || amount.compareTo(java.math.BigInteger.valueOf(Integer.MAX_VALUE)) > 0) {
            throw new IllegalArgumentException("选项数量超出范围");
        }
        JsonObject scalar = new JsonObject();
        for (String key : new String[] { "item", "meta", "amount", "nbtBase64", "oreDict" }) {
            if (json.has(key)) scalar.add(key, json.get(key));
        }
        NekoTradeEntry.ItemEntry entry = new com.google.gson.Gson().fromJson(scalar, NekoTradeEntry.ItemEntry.class);
        entry.setAmount(amount.intValue());
        String ore = entry.getOreDict();
        if (ore != null && !ore.isEmpty()) {
            if (ore.length() > 128 || !ore.equals(ore.trim())
                || net.minecraftforge.oredict.OreDictionary.getOres(ore)
                    .isEmpty()) {
                throw new IllegalArgumentException("矿词没有可用物品或格式非法: " + ore);
            }
        }
        if (entry.toItemStack() == null) throw new IllegalArgumentException("选项物品无效");
        List<NekoTradeEntry.ItemEntry> alternatives = new ArrayList<>();
        if (json.has("alternatives")) {
            JsonArray options = json.getAsJsonArray("alternatives");
            if ((child && options.size() > 0) || options.size() > 15)
                throw new IllegalArgumentException("选项不可嵌套，每格最多 16 个");
            for (int i = 0; i < options.size(); i++) alternatives.add(
                parseTradeChoice(
                    options.get(i)
                        .getAsJsonObject(),
                    true));
        }
        entry.setAlternatives(alternatives);
        if (NekoTradeMatcher.outputCandidates(NekoBigItemStack.fromItemEntry(entry)) == null) {
            throw new IllegalArgumentException("选项没有可用的矿词物品");
        }
        return entry;
    }

    static void sendSuccess(EntityPlayerMP player, String message) {
        player.addChatMessage(new ChatComponentText(EnumChatFormatting.GREEN + "[编辑模式] " + message));
    }

    static void sendError(EntityPlayerMP player, String message) {
        player.addChatMessage(new ChatComponentText(EnumChatFormatting.RED + "[编辑模式] " + message));
    }

    static void sendInfo(EntityPlayerMP player, String message) {
        player.addChatMessage(new ChatComponentText(EnumChatFormatting.YELLOW + "[编辑模式] " + message));
    }
}

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

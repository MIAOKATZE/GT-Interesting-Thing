package com.miaokatze.gtit.signin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import cpw.mods.fml.common.network.ByteBufUtils;
import io.netty.buffer.ByteBuf;

/**
 * 签到统一奖励模型（v1.7.8 任务5+6）
 * <p>
 * 一条奖励 = 货币（货币 ID + 数量）+ 物品列表（{@link RewardItem}，最多 4 槽）。
 * 用于：每月每日默认奖励（工作日/周末）、逐日覆盖奖励、连续签到阶梯奖励、累计签到阶梯奖励。
 * <p>
 * 货币 ID 为空串或数量为 0 表示「无货币部分」（{@link #hasCurrency()}=false）；
 * 物品列表中空条目（{@link RewardItem#isEmpty()}）在业务层过滤。
 * <p>
 * <b>序列化三件套</b>：{@link #toJson()}/{@link #fromJson(JsonObject)}（配置文件与编辑载荷，
 * 字段 currency/amount/items）与 {@link #writeToByteBuf}/{@link #readFromByteBuf}（同步包）。
 */
public class SignInReward {

    /** 空奖励常量（无货币无物品，缺省/兜底用） */
    public static final SignInReward EMPTY = new SignInReward("", 0, Collections.<RewardItem>emptyList());

    /** 货币 ID（空串 = 无货币部分） */
    private final String currencyId;
    /** 货币数量（≥0） */
    private final int currencyAmount;
    /** 物品条目列表（不为 null；可能含空条目，业务层以 {@link RewardItem#isEmpty()} 过滤） */
    private final List<RewardItem> items;

    /**
     * 构造签到奖励
     *
     * @param currencyId     货币 ID（null/空串 = 无货币部分）
     * @param currencyAmount 货币数量（&lt;0 按 0 截断）
     * @param items          物品条目列表（null 按空列表；内部做防御性拷贝）
     */
    public SignInReward(String currencyId, int currencyAmount, List<RewardItem> items) {
        this.currencyId = currencyId == null ? "" : currencyId;
        this.currencyAmount = Math.max(0, currencyAmount);
        this.items = items == null ? new ArrayList<RewardItem>() : new ArrayList<>(items);
    }

    /** 是否含货币部分（货币 ID 非空且数量 &gt; 0） */
    public boolean hasCurrency() {
        return !currencyId.isEmpty() && currencyAmount > 0;
    }

    /** 是否含有效物品条目（至少一个非空条目） */
    public boolean hasItems() {
        for (RewardItem item : items) {
            if (item != null && !item.isEmpty()) return true;
        }
        return false;
    }

    public String getCurrencyId() {
        return currencyId;
    }

    public int getCurrencyAmount() {
        return currencyAmount;
    }

    /** 物品条目列表（不为 null，调用方不应修改） */
    public List<RewardItem> getItems() {
        return items;
    }

    // ==================== JSON 序列化（配置文件 / 编辑载荷共用字段口径） ====================

    /**
     * 序列化为 JSON 对象（字段：currency/amount/items）
     */
    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("currency", currencyId);
        json.addProperty("amount", currencyAmount);
        JsonArray arr = new JsonArray();
        for (RewardItem item : items) {
            if (item != null && !item.isEmpty()) {
                arr.add(item.toJson());
            }
        }
        json.add("items", arr);
        return json;
    }

    /**
     * 从 JSON 对象反序列化（缺省字段按空/0 处理，兼容不完整载荷）
     *
     * @param json JSON 对象（为 null 时返回 {@link #EMPTY}）
     * @return 签到奖励
     */
    public static SignInReward fromJson(JsonObject json) {
        if (json == null) return EMPTY;
        String currency = json.has("currency") ? json.get("currency")
            .getAsString() : "";
        int amount = json.has("amount") ? json.get("amount")
            .getAsInt() : 0;
        List<RewardItem> items = new ArrayList<>();
        if (json.has("items") && json.get("items")
            .isJsonArray()) {
            JsonArray arr = json.getAsJsonArray("items");
            for (JsonElement e : arr) {
                if (e != null && e.isJsonObject()) {
                    RewardItem item = RewardItem.fromJson(e.getAsJsonObject());
                    if (!item.isEmpty()) {
                        items.add(item);
                    }
                }
            }
        }
        return new SignInReward(currency, amount, items);
    }

    // ==================== 网络序列化（同步包） ====================

    /**
     * 写入字节缓冲（货币 + 物品条目数 + 逐条目）
     */
    public void writeToByteBuf(ByteBuf buf) {
        ByteBufUtils.writeUTF8String(buf, currencyId);
        buf.writeInt(currencyAmount);
        // 仅写入非空条目（网络层无需槽位对齐）
        List<RewardItem> effective = new ArrayList<>();
        for (RewardItem item : items) {
            if (item != null && !item.isEmpty()) effective.add(item);
        }
        buf.writeInt(effective.size());
        for (RewardItem item : effective) {
            item.writeToByteBuf(buf);
        }
    }

    /**
     * 从字节缓冲读取（与 {@link #writeToByteBuf} 顺序一致）
     */
    public static SignInReward readFromByteBuf(ByteBuf buf) {
        String currencyId = ByteBufUtils.readUTF8String(buf);
        int currencyAmount = buf.readInt();
        int count = buf.readInt();
        List<RewardItem> items = new ArrayList<>(Math.max(0, count));
        for (int i = 0; i < count; i++) {
            items.add(RewardItem.readFromByteBuf(buf));
        }
        return new SignInReward(currencyId, currencyAmount, items);
    }
}

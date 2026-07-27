package com.miaokatze.gtit.signin;

import com.google.gson.JsonObject;

import cpw.mods.fml.common.network.ByteBufUtils;
import io.netty.buffer.ByteBuf;

/**
 * 签到奖励物品条目（v1.7.8 任务5+6 统一奖励模型）
 * <p>
 * 描述一件作为奖励发放的物品：注册名 ID（"modid:name"）、数量、meta 与可选 NBT（Base64 编码）。
 * 一条 {@link SignInReward} 可携带最多 4 个物品条目（GUI 编辑缓冲区为 4 槽）。
 * <p>
 * <b>序列化三件套</b>：
 * <ul>
 * <li>{@link #toJson()}/{@link #fromJson(JsonObject)}：配置文件与编辑载荷（字段 item/amount/meta/nbt）</li>
 * <li>{@link #writeToByteBuf(ByteBuf)}/{@link #readFromByteBuf(ByteBuf)}：同步包网络序列化</li>
 * </ul>
 * 空条目（itemId 为空串）表示「无物品」，序列化时保留以便槽位对齐，业务层以 {@link #isEmpty()} 过滤。
 */
public class RewardItem {

    /** 物品注册名（"modid:name"，空串 = 空条目） */
    private final String itemId;
    /** 物品数量（≥0） */
    private final int amount;
    /** 物品 meta（≥0） */
    private final int meta;
    /** 物品 NBT（Base64 编码，空串 = 无 NBT） */
    private final String nbtBase64;

    /**
     * 构造奖励物品条目
     *
     * @param itemId    物品注册名（"modid:name"，null/空串 = 空条目）
     * @param amount    数量（&lt;0 按 0 截断）
     * @param meta      meta（&lt;0 按 0 截断）
     * @param nbtBase64 NBT Base64 编码（null/空串 = 无 NBT）
     */
    public RewardItem(String itemId, int amount, int meta, String nbtBase64) {
        this.itemId = itemId == null ? "" : itemId;
        this.amount = Math.max(0, amount);
        this.meta = Math.max(0, meta);
        this.nbtBase64 = nbtBase64 == null ? "" : nbtBase64;
    }

    /** 是否为空条目（无物品） */
    public boolean isEmpty() {
        return itemId.isEmpty();
    }

    public String getItemId() {
        return itemId;
    }

    public int getAmount() {
        return amount;
    }

    public int getMeta() {
        return meta;
    }

    /** NBT Base64 编码（不会为 null，空串 = 无 NBT） */
    public String getNbtBase64() {
        return nbtBase64;
    }

    // ==================== JSON 序列化（配置文件 / 编辑载荷共用字段口径） ====================

    /**
     * 序列化为 JSON 对象（字段：item/amount/meta/nbt）
     */
    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("item", itemId);
        json.addProperty("amount", amount);
        json.addProperty("meta", meta);
        json.addProperty("nbt", nbtBase64);
        return json;
    }

    /**
     * 从 JSON 对象反序列化（缺省字段按空条目/0 处理，兼容不完整载荷）
     *
     * @param json JSON 对象（为 null 时返回空条目）
     * @return 奖励物品条目
     */
    public static RewardItem fromJson(JsonObject json) {
        if (json == null) return new RewardItem("", 0, 0, "");
        String item = json.has("item") ? json.get("item")
            .getAsString() : "";
        int amount = json.has("amount") ? json.get("amount")
            .getAsInt() : 0;
        int meta = json.has("meta") ? json.get("meta")
            .getAsInt() : 0;
        String nbt = json.has("nbt") ? json.get("nbt")
            .getAsString() : "";
        return new RewardItem(item, amount, meta, nbt);
    }

    // ==================== 网络序列化（同步包） ====================

    /**
     * 写入字节缓冲（字符串以 UTF8 写入）
     */
    public void writeToByteBuf(ByteBuf buf) {
        ByteBufUtils.writeUTF8String(buf, itemId);
        buf.writeInt(amount);
        buf.writeInt(meta);
        ByteBufUtils.writeUTF8String(buf, nbtBase64);
    }

    /**
     * 从字节缓冲读取（与 {@link #writeToByteBuf} 顺序一致）
     */
    public static RewardItem readFromByteBuf(ByteBuf buf) {
        String itemId = ByteBufUtils.readUTF8String(buf);
        int amount = buf.readInt();
        int meta = buf.readInt();
        String nbt = ByteBufUtils.readUTF8String(buf);
        return new RewardItem(itemId, amount, meta, nbt);
    }
}

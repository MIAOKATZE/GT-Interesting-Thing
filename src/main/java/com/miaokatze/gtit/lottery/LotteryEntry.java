package com.miaokatze.gtit.lottery;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import com.miaokatze.gtit.trade.NekoCurrencyRegistrar;
import com.miaokatze.gtit.util.NbtBase64Util;

import cpw.mods.fml.common.registry.GameRegistry;

/**
 * 抽奖奖品条目
 * <p>
 * 支持两类奖品：
 * <ul>
 * <li>物品奖品：{@link #item} + {@link #meta} + {@link #nbtBase64}（可选），出货时弹入机器出货槽</li>
 * <li>货币奖品：{@link #nekoCurrencyId} 非空，出货时直接入团队钱包</li>
 * </ul>
 * 数量在 [{@link #minAmount}, {@link #maxAmount}] 区间随机。
 */
public class LotteryEntry {

    /** 条目唯一 ID（配置内唯一，用于历史记录与结果同步） */
    private String id;
    /** 物品 ID（"modid:name"），货币奖品时为 null */
    private String item;
    /** 物品 meta */
    private int meta;
    /** 最小数量（含） */
    private int minAmount = 1;
    /** 最大数量（含） */
    private int maxAmount = 1;
    /** 物品 NBT（Base64 压缩），无 NBT 时为 null */
    private String nbtBase64;
    /** 缓存的解码 NBT（transient，不参与 Gson） */
    private transient NBTTagCompound nbt;
    /** 抽取权重 */
    private int weight = 1;
    /** 稀有度 */
    private LotteryRarity rarity = LotteryRarity.COMMON;
    /** 货币奖品 ID（"neko"/"shimmeringNeko"），物品奖品时为 null */
    private String nekoCurrencyId;

    public LotteryEntry() {}

    // ==================== 工厂方法 ====================

    /**
     * 创建物品奖品
     *
     * @param id        条目 ID
     * @param itemId    物品 ID（"modid:name"）
     * @param meta      meta
     * @param minAmount 最小数量
     * @param maxAmount 最大数量
     * @param weight    权重
     * @param rarity    稀有度
     * @return 物品奖品条目
     */
    public static LotteryEntry createItemPrize(String id, String itemId, int meta, int minAmount, int maxAmount,
        int weight, LotteryRarity rarity) {
        LotteryEntry entry = new LotteryEntry();
        entry.id = id;
        entry.item = itemId;
        entry.meta = meta;
        entry.minAmount = minAmount;
        entry.maxAmount = maxAmount;
        entry.weight = weight;
        entry.rarity = rarity == null ? LotteryRarity.COMMON : rarity;
        return entry;
    }

    /**
     * 创建货币奖品
     *
     * @param id             条目 ID
     * @param nekoCurrencyId 货币 ID
     * @param minAmount      最小数量
     * @param maxAmount      最大数量
     * @param weight         权重
     * @param rarity         稀有度
     * @return 货币奖品条目
     */
    public static LotteryEntry createNekoPrize(String id, String nekoCurrencyId, int minAmount, int maxAmount,
        int weight, LotteryRarity rarity) {
        LotteryEntry entry = new LotteryEntry();
        entry.id = id;
        entry.nekoCurrencyId = nekoCurrencyId;
        entry.minAmount = minAmount;
        entry.maxAmount = maxAmount;
        entry.weight = weight;
        entry.rarity = rarity == null ? LotteryRarity.COMMON : rarity;
        return entry;
    }

    // ==================== 物品构建 ====================

    /**
     * 构建本条目对应的物品堆（数量取 {@link #randomAmount()}）
     * <p>
     * 货币奖品返回对应猫猫币物品堆；物品 ID 无法解析时返回 null。
     *
     * @return 物品堆；无法构建时返回 null
     */
    public ItemStack toItemStack() {
        int amount = randomAmount();
        return toItemStack(amount);
    }

    /**
     * 构建指定数量的物品堆
     *
     * @param amount 数量
     * @return 物品堆；无法构建时返回 null
     */
    public ItemStack toItemStack(int amount) {
        if (isNekoPrize()) {
            return NekoCurrencyRegistrar.getItemStack(nekoCurrencyId, amount);
        }
        if (item == null || item.isEmpty()) return null;
        String[] parts = item.split(":");
        if (parts.length != 2) return null;
        Item itemObj = GameRegistry.findItem(parts[0], parts[1]);
        if (itemObj == null) return null;
        ItemStack stack = new ItemStack(itemObj, Math.max(1, amount), meta);
        NBTTagCompound tag = getNbt();
        if (tag != null) {
            stack.setTagCompound(tag);
        }
        return stack;
    }

    /**
     * 随机数量（[minAmount, maxAmount] 均匀分布）
     */
    public int randomAmount() {
        if (maxAmount <= minAmount) return Math.max(1, minAmount);
        return minAmount + new java.util.Random().nextInt(maxAmount - minAmount + 1);
    }

    /**
     * 是否为货币奖品
     */
    public boolean isNekoPrize() {
        return nekoCurrencyId != null && !nekoCurrencyId.isEmpty();
    }

    /**
     * 获取显示用物品堆（数量固定 1，用于 GUI 渲染条目图标）
     */
    public ItemStack getDisplayStack() {
        return toItemStack(1);
    }

    // ==================== NBT 序列化 ====================

    /**
     * 将本条目写入 NBT（供网络包传输到客户端）
     */
    public NBTTagCompound writeToNBT() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("id", id == null ? "" : id);
        if (item != null) tag.setString("item", item);
        tag.setInteger("meta", meta);
        tag.setInteger("min", minAmount);
        tag.setInteger("max", maxAmount);
        tag.setInteger("weight", weight);
        tag.setString("rarity", rarity.name());
        if (nekoCurrencyId != null) tag.setString("currency", nekoCurrencyId);
        if (nbtBase64 != null) tag.setString("nbt", nbtBase64);
        return tag;
    }

    /**
     * 从 NBT 读取条目（客户端反序列化用）
     */
    public static LotteryEntry fromNBT(NBTTagCompound tag) {
        if (tag == null) return null;
        LotteryEntry entry = new LotteryEntry();
        entry.id = tag.getString("id");
        if (tag.hasKey("item")) entry.item = tag.getString("item");
        entry.meta = tag.getInteger("meta");
        entry.minAmount = tag.getInteger("min");
        entry.maxAmount = tag.getInteger("max");
        entry.weight = tag.getInteger("weight");
        entry.rarity = LotteryRarity.fromString(tag.getString("rarity"));
        if (tag.hasKey("currency")) entry.nekoCurrencyId = tag.getString("currency");
        if (tag.hasKey("nbt")) entry.nbtBase64 = tag.getString("nbt");
        return entry;
    }

    // ==================== 内部辅助 ====================

    /** 解码并缓存 NBT */
    private NBTTagCompound getNbt() {
        if (nbt == null && nbtBase64 != null && !nbtBase64.isEmpty()) {
            nbt = NbtBase64Util.nbtFromBase64(nbtBase64);
        }
        return nbt;
    }

    // ==================== Getter ====================

    public String getId() {
        return id;
    }

    public String getItem() {
        return item;
    }

    public int getMeta() {
        return meta;
    }

    public int getMinAmount() {
        return minAmount;
    }

    public int getMaxAmount() {
        return maxAmount;
    }

    public int getWeight() {
        return weight;
    }

    public LotteryRarity getRarity() {
        return rarity;
    }

    public String getNekoCurrencyId() {
        return nekoCurrencyId;
    }

    public String getNbtBase64() {
        return nbtBase64;
    }

    // ==================== Setter（配置编辑/校验用） ====================

    public void setId(String id) {
        this.id = id;
    }

    public void setRarity(LotteryRarity rarity) {
        this.rarity = rarity;
    }

    public void setWeight(int weight) {
        this.weight = weight;
    }

    /**
     * 设置物品 ID（编辑模式用；转为货币奖品时传 null）
     */
    public void setItem(String item) {
        this.item = item;
    }

    /**
     * 设置物品 meta（编辑模式用）
     */
    public void setMeta(int meta) {
        this.meta = meta;
    }

    /**
     * 设置最小数量（编辑模式用，下限 1）
     */
    public void setMinAmount(int minAmount) {
        this.minAmount = Math.max(1, minAmount);
    }

    /**
     * 设置最大数量（编辑模式用，下限取 minAmount）
     */
    public void setMaxAmount(int maxAmount) {
        this.maxAmount = Math.max(this.minAmount, maxAmount);
    }

    /**
     * 设置货币奖品 ID（编辑模式用；转为物品奖品时传 null）
     */
    public void setNekoCurrencyId(String nekoCurrencyId) {
        this.nekoCurrencyId = nekoCurrencyId;
    }

    /**
     * 设置物品 NBT（Base64，编辑模式用）
     * <p>
     * 同时清理解码缓存，保证 {@link #toItemStack(int)} 使用新 NBT。
     */
    public void setNbtBase64(String nbtBase64) {
        this.nbtBase64 = nbtBase64;
        this.nbt = null;
    }
}

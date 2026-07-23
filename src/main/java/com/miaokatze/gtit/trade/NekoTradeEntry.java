package com.miaokatze.gtit.trade;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import com.miaokatze.gtit.main.GTInterestingThing;
import com.miaokatze.gtit.util.NbtBase64Util;

import cpw.mods.fml.common.registry.GameRegistry;

/**
 * 猫猫售货机交易条目数据模型
 * 用于Config文件的序列化/反序列化
 */
public class NekoTradeEntry {

    private String id;
    private int tabId; // 标签页ID：1=猫猫币，2=闪烁猫猫币，3=其他
    private int orderId; // 顺序ID（与tabId共同构成条目唯一身份）
    private NekoCurrencyCost currency;
    private List<ItemEntry> fromItems;
    private List<ItemEntry> toItems;
    private int cooldown;
    private int maxTrades;
    private String bqQuestId; // BQ任务绑定ID，空字符串=不需要绑定
    // 是否严格匹配 NBT（v1.7.6 G3⑤）：true=需求按物品+NBT 精确匹配；false=仅按物品匹配。
    // 统一默认 false（用户确认口径）：旧 JSON 无该字段时 Gson 保留默认值 false，
    // 旧带 NBT 需求的交易匹配变宽松，属行为变更（已写入 commit 说明）。
    private boolean recordNBT = false;

    public NekoTradeEntry() {
        this.id = UUID.randomUUID()
            .toString();
        this.tabId = 1;
        this.orderId = 0;
        this.fromItems = new ArrayList<>();
        this.toItems = new ArrayList<>();
        this.cooldown = 0;
        this.maxTrades = -1;
        this.bqQuestId = "";
    }

    public static NekoTradeEntry createDefault() {
        NekoTradeEntry entry = new NekoTradeEntry();
        entry.toItems.add(new ItemEntry("minecraft:iron_ingot", 0, 1));
        return entry;
    }

    // --- Getters & Setters ---

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public NekoCurrencyCost getCurrency() {
        return currency;
    }

    public void setCurrency(NekoCurrencyCost currency) {
        this.currency = currency;
    }

    public List<ItemEntry> getFromItems() {
        return fromItems;
    }

    public void setFromItems(List<ItemEntry> fromItems) {
        this.fromItems = fromItems;
    }

    public List<ItemEntry> getToItems() {
        return toItems;
    }

    public void setToItems(List<ItemEntry> toItems) {
        this.toItems = toItems;
    }

    public int getCooldown() {
        return cooldown;
    }

    public void setCooldown(int cooldown) {
        this.cooldown = cooldown;
    }

    public int getMaxTrades() {
        return maxTrades;
    }

    public void setMaxTrades(int maxTrades) {
        this.maxTrades = maxTrades;
    }

    public int getTabId() {
        return tabId;
    }

    public void setTabId(int tabId) {
        this.tabId = tabId;
    }

    public int getOrderId() {
        return orderId;
    }

    public void setOrderId(int orderId) {
        this.orderId = orderId;
    }

    public String getBqQuestId() {
        return bqQuestId;
    }

    public void setBqQuestId(String bqQuestId) {
        this.bqQuestId = bqQuestId;
    }

    /**
     * 是否严格匹配 NBT（v1.7.6 G3⑤）
     *
     * @return true = 需求物品按物品+NBT 精确匹配；false = 仅按物品匹配
     */
    public boolean isRecordNBT() {
        return recordNBT;
    }

    public void setRecordNBT(boolean recordNBT) {
        this.recordNBT = recordNBT;
    }

    // --- Inner Classes ---

    /**
     * 猫猫币花费
     */
    public static class NekoCurrencyCost {

        private String type; // "neko" 或 "shimmeringNeko"
        private int amount;

        public NekoCurrencyCost() {}

        public NekoCurrencyCost(String type, int amount) {
            this.type = type;
            this.amount = amount;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public int getAmount() {
            return amount;
        }

        public void setAmount(int amount) {
            this.amount = amount;
        }
    }

    /**
     * 物品条目
     * <p>
     * nbtBase64 用于 JSON 序列化存储（Base64 编码的 NBT 二进制数据），nbt 用于运行时。
     * Gson 序列化时使用 nbtBase64，反序列化后通过 toItemStack() 按需解析。
     */
    public static class ItemEntry {

        private String item; // modid:name 格式
        private int meta;
        private int amount;
        private String nbtBase64; // JSON 存储：Base64 编码的 NBT 二进制数据
        private transient NBTTagCompound nbt; // 运行时：NBT 数据

        public ItemEntry() {
            this.meta = 0;
            this.amount = 1;
        }

        public ItemEntry(String item, int meta, int amount) {
            this.item = item;
            this.meta = meta;
            this.amount = amount;
        }

        /**
         * 从 ItemStack 创建 ItemEntry（默认包含 NBT）
         */
        public static ItemEntry fromItemStack(ItemStack stack) {
            return fromItemStack(stack, true);
        }

        /**
         * 从 ItemStack 创建 ItemEntry
         *
         * @param stack     源物品堆
         * @param recordNbt true 时记录物品的 NBT 数据；false 时忽略 NBT
         */
        public static ItemEntry fromItemStack(ItemStack stack, boolean recordNbt) {
            if (stack == null) return null;

            Item item = stack.getItem();
            // 获取 modid:name 格式的物品ID
            String itemId = Item.itemRegistry.getNameForObject(item)
                .toString();

            ItemEntry entry = new ItemEntry();
            entry.item = itemId;
            entry.meta = stack.getItemDamage();
            entry.amount = stack.stackSize;

            // 处理 NBT：仅在 recordNbt 为 true 时序列化为 Base64
            if (recordNbt && stack.hasTagCompound() && stack.getTagCompound() != null) {
                NBTTagCompound tagCompound = stack.getTagCompound();
                entry.nbt = (NBTTagCompound) tagCompound.copy();
                entry.nbtBase64 = nbtToBase64(tagCompound);
            }

            return entry;
        }

        /**
         * 将 ItemEntry 转换回 ItemStack（包含NBT）
         * <p>
         * 使用 GameRegistry.findItem 查找物品。
         * 如果物品找不到，返回 null 并记录警告。
         */
        public ItemStack toItemStack() {
            if (item == null || item.isEmpty()) return null;

            // 解析 modid:name
            String[] parts = item.split(":", 2);
            if (parts.length < 2) {
                GTInterestingThing.LOG.warn("ItemEntry.toItemStack: 物品ID格式无效 [item={}]", item);
                return null;
            }

            String modid = parts[0];
            String name = parts[1];

            Item foundItem = GameRegistry.findItem(modid, name);
            if (foundItem == null) {
                GTInterestingThing.LOG.warn("ItemEntry.toItemStack: 找不到物品 [modid={}, name={}]", modid, name);
                return null;
            }

            ItemStack stack = new ItemStack(foundItem, amount, meta);

            // 处理 NBT：优先使用运行时 nbt，否则从 nbtBase64 解析
            NBTTagCompound tagToApply = nbt;
            if (tagToApply == null && nbtBase64 != null && !nbtBase64.isEmpty()) {
                tagToApply = nbtFromBase64(nbtBase64);
            }

            if (tagToApply != null) {
                stack.setTagCompound(tagToApply);
            }

            return stack;
        }

        // --- Getters & Setters ---

        public String getItem() {
            return item;
        }

        public void setItem(String item) {
            this.item = item;
        }

        public int getMeta() {
            return meta;
        }

        public void setMeta(int meta) {
            this.meta = meta;
        }

        public int getAmount() {
            return amount;
        }

        public void setAmount(int amount) {
            this.amount = amount;
        }

        public String getNbtBase64() {
            return nbtBase64;
        }

        public void setNbtBase64(String nbtBase64) {
            this.nbtBase64 = nbtBase64;
        }

        public NBTTagCompound getNbt() {
            return nbt;
        }

        public void setNbt(NBTTagCompound nbt) {
            this.nbt = nbt;
            if (nbt != null) {
                this.nbtBase64 = nbtToBase64(nbt);
            }
        }
    }

    // --- NBT 序列化辅助方法 ---

    /**
     * 将 NBTTagCompound 序列化为 Base64 字符串
     * <p>
     * 内部委托给 {@link NbtBase64Util}，保持各模块 NBT 编解码逻辑统一。
     *
     * @param nbt 待序列化的 NBT 数据
     * @return Base64 编码后的字符串；失败或输入 null 时返回 null
     */
    public static String nbtToBase64(NBTTagCompound nbt) {
        return NbtBase64Util.nbtToBase64(nbt);
    }

    /**
     * 从 Base64 字符串反序列化 NBTTagCompound
     * <p>
     * 内部委托给 {@link NbtBase64Util}，保持各模块 NBT 编解码逻辑统一。
     *
     * @param base64 Base64 编码的字符串
     * @return 反序列化后的 NBT 数据；失败或输入 null/空字符串时返回 null
     */
    public static NBTTagCompound nbtFromBase64(String base64) {
        return NbtBase64Util.nbtFromBase64(base64);
    }
}

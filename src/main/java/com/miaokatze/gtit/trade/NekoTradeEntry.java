package com.miaokatze.gtit.trade;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;

import com.miaokatze.gtit.main.GTInterestingThing;

import cpw.mods.fml.common.registry.GameRegistry;

/**
 * 猫猫售货机交易条目数据模型
 * 用于Config文件的序列化/反序列化
 */
public class NekoTradeEntry {

    private String id;
    private NekoCurrencyCost currency;
    private List<ItemEntry> fromItems;
    private List<ItemEntry> toItems;
    private int cooldown;
    private int maxTrades;
    private int order;

    public NekoTradeEntry() {
        this.id = UUID.randomUUID()
            .toString();
        this.fromItems = new ArrayList<>();
        this.toItems = new ArrayList<>();
        this.cooldown = -1;
        this.maxTrades = -1;
        this.order = 0;
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

    public int getOrder() {
        return order;
    }

    public void setOrder(int order) {
        this.order = order;
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
         * 从 ItemStack 创建 ItemEntry（包含NBT）
         */
        public static ItemEntry fromItemStack(ItemStack stack) {
            if (stack == null) return null;

            Item item = stack.getItem();
            // 获取 modid:name 格式的物品ID
            String itemId = Item.itemRegistry.getNameForObject(item)
                .toString();

            ItemEntry entry = new ItemEntry();
            entry.item = itemId;
            entry.meta = stack.getItemDamage();
            entry.amount = stack.stackSize;

            // 处理 NBT：序列化为 Base64
            if (stack.hasTagCompound() && stack.getTagCompound() != null) {
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
     */
    private static String nbtToBase64(NBTTagCompound nbt) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos);
            CompressedStreamTools.write(nbt, dos);
            dos.close();
            return Base64.getEncoder()
                .encodeToString(baos.toByteArray());
        } catch (Exception e) {
            GTInterestingThing.LOG.error("NekoTradeEntry: NBT序列化为Base64失败", e);
            return null;
        }
    }

    /**
     * 从 Base64 字符串反序列化 NBTTagCompound
     */
    private static NBTTagCompound nbtFromBase64(String base64) {
        try {
            byte[] bytes = Base64.getDecoder()
                .decode(base64);
            ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
            DataInputStream dis = new DataInputStream(bais);
            NBTTagCompound nbt = CompressedStreamTools.read(dis);
            dis.close();
            return nbt;
        } catch (Exception e) {
            GTInterestingThing.LOG.error("NekoTradeEntry: Base64反序列化为NBT失败", e);
            return null;
        }
    }
}

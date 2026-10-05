package com.miaokatze.gtit.trade.v2;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.oredict.OreDictionary;

import com.miaokatze.gtit.trade.NekoTradeEntry.ItemEntry;

import cpw.mods.fml.common.registry.GameRegistry;

/**
 * 大数量物品栈，替代 VM 的 BigItemStack
 * <p>
 * 支持矿物词典（oreDict）和大数量物品表示，
 * 突破原生 ItemStack 的 64 上限限制。
 */
public class NekoBigItemStack {

    private int stackSize;
    private String oreDict;
    private ItemStack baseStack;
    private List<NekoBigItemStack> alternatives = new ArrayList<>();

    /**
     * 从 ItemStack 构造，stackSize 取自原物品栈数量
     *
     * @param baseStack 基础物品栈
     */
    public NekoBigItemStack(ItemStack baseStack) {
        this.baseStack = baseStack.copy();
        this.baseStack.stackSize = 1;
        this.stackSize = baseStack.stackSize;
        this.oreDict = "";
    }

    /**
     * 完整构造器
     *
     * @param stackSize 物品数量（可超过 maxStackSize）
     * @param oreDict   矿物词典名，无则传空字符串
     * @param baseStack 基础物品栈
     */
    public NekoBigItemStack(int stackSize, String oreDict, ItemStack baseStack) {
        this.baseStack = baseStack.copy();
        this.baseStack.stackSize = 1;
        this.stackSize = stackSize;
        this.oreDict = oreDict != null ? oreDict : "";
    }

    /**
     * 检查给定的 ItemStack 是否匹配本大物品栈
     * <p>
     * 优先按矿物词典匹配，无矿物词典时按物品和 NBT 精确匹配。
     * <p>
     * 等价于 {@link #matches(ItemStack, boolean)} 且 recordNBT=true（保留旧行为，
     * 供未感知 recordNBT 的旧调用点使用）。
     *
     * @param stack 待检查的物品栈
     * @return 匹配返回 true，否则 false
     */
    public boolean matches(ItemStack stack) {
        return matches(stack, true);
    }

    /**
     * 检查给定的 ItemStack 是否匹配本大物品栈（v1.7.6 G3⑤ NBT 选框重载）
     * <p>
     * 矿物词典分支不受 recordNBT 影响（矿词匹配天然忽略 NBT）；
     * 物品分支 recordNBT=false 时仅按物品（id+meta）匹配，忽略 NBT 差异。
     *
     * @param stack     待检查的物品栈
     * @param recordNBT true = 物品+NBT 精确匹配；false = 仅物品匹配
     * @return 匹配返回 true，否则 false
     */
    public boolean matches(ItemStack stack, boolean recordNBT) {
        if (stack == null) {
            return false;
        }
        // 有矿物词典时，按矿石ID匹配
        if (oreDict != null && !oreDict.isEmpty()) {
            for (int id : OreDictionary.getOreIDs(stack)) {
                if (OreDictionary.getOreName(id)
                    .equals(oreDict)) {
                    return true;
                }
            }
            return false;
        }
        // 无矿物词典时：recordNBT=true 按物品+NBT 精确匹配；false 仅按物品匹配
        return baseStack.isItemEqual(stack) && (!recordNBT || ItemStack.areItemStackTagsEqual(baseStack, stack));
    }

    /**
     * 序列化到 NBT
     *
     * @return NBT 标签化合物
     */
    public NBTTagCompound writeToNBT() {
        NBTTagCompound nbt = new NBTTagCompound();
        // 物品注册名，不存在时用 minecraft:air
        String id = Item.itemRegistry.getNameForObject(baseStack.getItem());
        nbt.setString("id", id != null ? id : "minecraft:air");
        nbt.setInteger("Damage", baseStack.getItemDamage());
        nbt.setInteger("Count", stackSize);
        nbt.setString("OreDict", oreDict != null ? oreDict : "");
        // 保存NBT标签
        if (baseStack.stackTagCompound != null) {
            nbt.setTag("tag", baseStack.stackTagCompound);
        }
        if (!alternatives.isEmpty()) {
            NBTTagList list = new NBTTagList();
            for (NekoBigItemStack option : alternatives) list.appendTag(option.writeToNBT());
            nbt.setTag("Alternatives", list);
        }
        return nbt;
    }

    /**
     * 从 NBT 反序列化（静态工厂方法）
     *
     * @param nbt NBT 标签化合物
     * @return 反序列化后的大物品栈
     */
    public static NekoBigItemStack loadFromNBT(NBTTagCompound nbt) {
        // 解析注册名，格式为 modid:name
        String id = nbt.getString("id");
        String[] parts = id.split(":");
        Item item = null;
        if (parts.length >= 2) {
            item = GameRegistry.findItem(parts[0], parts[1]);
        }
        // 物品不存在时回退到 air
        if (item == null) {
            item = GameRegistry.findItem("minecraft", "air");
        }
        if (item == null) {
            return null;
        }

        ItemStack stack = new ItemStack(item, 1, nbt.getInteger("Damage"));
        int stackSize = nbt.getInteger("Count");
        String oreDict = nbt.getString("OreDict");
        // 恢复NBT标签
        if (nbt.hasKey("tag")) {
            stack.stackTagCompound = nbt.getCompoundTag("tag");
        }

        NekoBigItemStack result = new NekoBigItemStack(stackSize, oreDict, stack);
        NBTTagList list = nbt.getTagList("Alternatives", 10);
        if (list.tagCount() > 15) return null;
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound child = list.getCompoundTagAt(i);
            if (child.getTagList("Alternatives", 10)
                .tagCount() > 0) return null;
            NekoBigItemStack option = loadFromNBT(child);
            if (option == null) return null;
            result.alternatives.add(option);
        }
        return result;
    }

    /**
     * 获取拆分后的 ItemStack 列表（每个不超过 maxStackSize）
     *
     * @return 拆分后的物品栈列表
     */
    public List<ItemStack> getCombinedStacks() {
        List<ItemStack> list = new ArrayList<>();
        int remaining = stackSize;
        int max = baseStack.getMaxStackSize();
        while (remaining > 0) {
            int size = Math.min(remaining, max);
            ItemStack copy = baseStack.copy();
            copy.stackSize = size;
            list.add(copy);
            remaining -= size;
        }
        return list;
    }

    /**
     * 深拷贝
     *
     * @return 副本
     */
    public NekoBigItemStack copy() {
        NekoBigItemStack result = new NekoBigItemStack(stackSize, oreDict, baseStack.copy());
        for (NekoBigItemStack option : alternatives) result.alternatives.add(option.copy());
        return result;
    }

    /**
     * 是否绑定了矿物词典
     *
     * @return 有矿物词典返回 true
     */
    public boolean hasOreDict() {
        return oreDict != null && !oreDict.isEmpty();
    }

    public int getStackSize() {
        return stackSize;
    }

    /**
     * 设置物品数量
     * <p>
     * 用于交易扣减模拟时设置未满足的剩余数量。
     *
     * @param stackSize 物品数量
     */
    public void setStackSize(int stackSize) {
        this.stackSize = stackSize;
    }

    public String getOreDict() {
        return oreDict;
    }

    public void setOreDict(String oreDict) {
        this.oreDict = oreDict == null ? "" : oreDict.trim();
    }

    public List<NekoBigItemStack> getAlternatives() {
        return alternatives;
    }

    public void setAlternatives(List<NekoBigItemStack> options) {
        alternatives = options == null ? new ArrayList<>() : options;
    }

    public List<NekoBigItemStack> getOptions() {
        List<NekoBigItemStack> options = new ArrayList<>();
        options.add(this);
        options.addAll(alternatives);
        return options;
    }

    public static NekoBigItemStack fromItemEntry(ItemEntry entry) {
        if (entry == null) return null;
        if (entry.getAmount() <= 0 || entry.getAlternatives()
            .size() > 15) return null;
        ItemStack icon = entry.toItemStack();
        if (icon == null && !entry.getOreDict()
            .isEmpty()) {
            for (ItemStack candidate : OreDictionary.getOres(entry.getOreDict())) {
                if (candidate != null && candidate.getItem() != null) {
                    icon = candidate.copy();
                    break;
                }
            }
            if (icon == null) icon = new ItemStack(net.minecraft.init.Items.paper);
            if (icon.getItemDamage() == OreDictionary.WILDCARD_VALUE) icon.setItemDamage(0);
        }
        if (icon == null) return null;
        NekoBigItemStack result = new NekoBigItemStack(entry.getAmount(), entry.getOreDict(), icon);
        for (ItemEntry option : entry.getAlternatives()) {
            if (option == null || !option.getAlternatives()
                .isEmpty()) return null;
            NekoBigItemStack converted = fromItemEntry(option);
            if (converted == null) return null;
            result.alternatives.add(converted);
        }
        return result;
    }

    public ItemEntry toItemEntry(boolean recordNBT) {
        ItemEntry result = ItemEntry.fromItemStack(baseStack, recordNBT);
        result.setAmount(stackSize);
        result.setOreDict(oreDict);
        for (NekoBigItemStack option : alternatives) result.getAlternatives()
            .add(option.toItemEntry(recordNBT));
        return result;
    }

    public ItemStack getBaseStack() {
        return baseStack;
    }
}

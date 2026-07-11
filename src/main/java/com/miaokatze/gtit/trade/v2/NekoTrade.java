package com.miaokatze.gtit.trade.v2;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

/**
 * 单笔交易，替代 VM 的 Trade
 * <p>
 * 包含输入物品（fromItems）、输出物品（toItems）、猫猫币花费（currencyId/currencyCost），
 * 以及用于 GUI 显示的 displayItem。
 */
public class NekoTrade {

    /** 输入物品列表（玩家需要提供的物品） */
    private List<NekoBigItemStack> fromItems = new ArrayList<>();
    /** 输出物品列表（玩家获得的物品） */
    private List<NekoBigItemStack> toItems = new ArrayList<>();
    /** 显示物品（用于 GUI 图标，为 null 时取 toItems 首项） */
    private NekoBigItemStack displayItem;
    /** 猫猫币唯一标识 */
    private String currencyId;
    /** 猫猫币花费数量 */
    private int currencyCost;

    /**
     * 默认构造器，字段已在声明处初始化为空集合
     */
    public NekoTrade() {
        // 字段已在声明处初始化，无需额外操作
    }

    /**
     * 是否有猫猫币花费
     *
     * @return 有花费返回 true
     */
    public boolean hasCurrencyCost() {
        return currencyId != null && currencyCost > 0;
    }

    /**
     * 是否有输入物品
     *
     * @return 有输入物品返回 true
     */
    public boolean hasFromItems() {
        return !fromItems.isEmpty();
    }

    /**
     * 是否为纯猫猫币交易（有货币花费但无输入物品）
     *
     * @return 纯货币交易返回 true
     */
    public boolean isPureCurrencyTrade() {
        return hasCurrencyCost() && !hasFromItems();
    }

    /**
     * 序列化到 NBT
     * <p>
     * 可选字段（null/空/0）不写入，节省存储空间。
     *
     * @return NBT 标签化合物
     */
    public NBTTagCompound writeToNBT() {
        NBTTagCompound nbt = new NBTTagCompound();
        // 写入猫猫币信息（仅在有值时写入）
        if (currencyId != null) {
            nbt.setString("currencyId", currencyId);
        }
        if (currencyCost > 0) {
            nbt.setInteger("currencyCost", currencyCost);
        }
        // 写入显示物品
        if (displayItem != null) {
            nbt.setTag("displayItem", displayItem.writeToNBT());
        }
        // 写入输入物品列表
        if (!fromItems.isEmpty()) {
            NBTTagList list = new NBTTagList();
            for (NekoBigItemStack item : fromItems) {
                list.appendTag(item.writeToNBT());
            }
            nbt.setTag("fromItems", list);
        }
        // 写入输出物品列表
        if (!toItems.isEmpty()) {
            NBTTagList list = new NBTTagList();
            for (NekoBigItemStack item : toItems) {
                list.appendTag(item.writeToNBT());
            }
            nbt.setTag("toItems", list);
        }
        return nbt;
    }

    /**
     * 从 NBT 反序列化
     * <p>
     * 加载前先清空现有集合，确保重复加载时的幂等性。
     *
     * @param nbt NBT 标签化合物
     */
    public void loadFromNBT(NBTTagCompound nbt) {
        // 先清空集合，避免重复加载时数据残留
        fromItems.clear();
        toItems.clear();
        // 读取猫猫币信息（空串视为 null）
        String id = nbt.getString("currencyId");
        currencyId = id.isEmpty() ? null : id;
        currencyCost = nbt.getInteger("currencyCost");
        // 读取显示物品
        if (nbt.hasKey("displayItem")) {
            displayItem = NekoBigItemStack.loadFromNBT(nbt.getCompoundTag("displayItem"));
        }
        // 读取输入物品列表
        if (nbt.hasKey("fromItems")) {
            NBTTagList list = nbt.getTagList("fromItems", 10);
            for (int i = 0; i < list.tagCount(); i++) {
                NekoBigItemStack item = NekoBigItemStack.loadFromNBT(list.getCompoundTagAt(i));
                if (item != null) {
                    fromItems.add(item);
                }
            }
        }
        // 读取输出物品列表
        if (nbt.hasKey("toItems")) {
            NBTTagList list = nbt.getTagList("toItems", 10);
            for (int i = 0; i < list.tagCount(); i++) {
                NekoBigItemStack item = NekoBigItemStack.loadFromNBT(list.getCompoundTagAt(i));
                if (item != null) {
                    toItems.add(item);
                }
            }
        }
    }

    // --- Getters ---

    public List<NekoBigItemStack> getFromItems() {
        return fromItems;
    }

    public List<NekoBigItemStack> getToItems() {
        return toItems;
    }

    /**
     * 获取显示物品
     * <p>
     * 优先返回显式设置的 displayItem；未设置时取 toItems 首项；均无则返回 null。
     *
     * @return 显示物品，可能为 null
     */
    public NekoBigItemStack getDisplayItem() {
        if (displayItem != null) {
            return displayItem;
        }
        if (!toItems.isEmpty()) {
            return toItems.get(0);
        }
        return null;
    }

    public String getCurrencyId() {
        return currencyId;
    }

    public int getCurrencyCost() {
        return currencyCost;
    }

    // --- Setters ---

    public void setCurrencyId(String currencyId) {
        this.currencyId = currencyId;
    }

    public void setCurrencyCost(int currencyCost) {
        this.currencyCost = currencyCost;
    }

    public void setDisplayItem(NekoBigItemStack displayItem) {
        this.displayItem = displayItem;
    }
}

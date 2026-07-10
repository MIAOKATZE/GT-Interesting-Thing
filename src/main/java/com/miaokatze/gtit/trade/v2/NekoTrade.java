package com.miaokatze.gtit.trade.v2;

import net.minecraft.nbt.NBTTagCompound;

/**
 * 单笔交易，替代 VM 的 Trade
 * <p>
 * 包含输入物品（fromItems）、输出物品（toItems）、猫猫币花费（currencyId/currencyCost），
 * 以及用于 GUI 显示的 displayItem。
 */
public class NekoTrade {

    private NekoBigItemStack[] fromItems;
    private NekoBigItemStack[] toItems;
    private NekoBigItemStack displayItem;
    private String currencyId;
    private int currencyCost;

    public NekoTrade() {
        // TODO: v1.6.1 实现
    }

    /**
     * 是否有猫猫币花费
     *
     * @return 有花费返回 true
     */
    public boolean hasCurrencyCost() {
        // TODO: v1.6.1 实现
        return currencyId != null && currencyCost > 0;
    }

    /**
     * 是否为纯猫猫币交易（无输入物品）
     *
     * @return 纯货币交易返回 true
     */
    public boolean isPureCurrencyTrade() {
        // TODO: v1.6.1 实现
        return fromItems == null || fromItems.length == 0;
    }

    /**
     * 序列化到 NBT
     *
     * @return NBT 标签化合物
     */
    public NBTTagCompound writeToNBT() {
        // TODO: v1.6.1 实现
        return null;
    }

    /**
     * 从 NBT 反序列化
     *
     * @param nbt NBT 标签化合物
     */
    public void loadFromNBT(NBTTagCompound nbt) {
        // TODO: v1.6.1 实现
    }

    // --- Getters ---

    public NekoBigItemStack[] getFromItems() {
        return fromItems;
    }

    public NekoBigItemStack[] getToItems() {
        return toItems;
    }

    public NekoBigItemStack getDisplayItem() {
        return displayItem;
    }

    public String getCurrencyId() {
        return currencyId;
    }

    public int getCurrencyCost() {
        return currencyCost;
    }
}

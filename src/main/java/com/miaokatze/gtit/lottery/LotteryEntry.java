package com.miaokatze.gtit.lottery;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

/**
 * 抽奖奖品条目
 */
public class LotteryEntry {

    private String id;
    private String item;
    private int meta;
    private int minAmount;
    private int maxAmount;
    private String nbtBase64;
    private transient NBTTagCompound nbt;
    private int weight;
    private LotteryRarity rarity;
    private boolean isPityPrize;
    private String nekoCurrencyId;

    public LotteryEntry() {}

    public static LotteryEntry createItemPrize(String id, String itemId, int meta, int minAmount, int maxAmount,
        int weight, LotteryRarity rarity, boolean isPityPrize) {
        // TODO: v1.6.4 实现
        return null;
    }

    public static LotteryEntry createNekoPrize(String id, String nekoCurrencyId, int minAmount, int maxAmount,
        int weight, LotteryRarity rarity, boolean isPityPrize) {
        // TODO: v1.6.4 实现
        return null;
    }

    public ItemStack toItemStack() {
        // TODO: v1.6.4 实现
        return null;
    }

    public int randomAmount() {
        // TODO: v1.6.4 实现
        return 1;
    }

    public boolean isNekoPrize() {
        return nekoCurrencyId != null && !nekoCurrencyId.isEmpty();
    }

    public String getId() {
        return id;
    }

    public int getWeight() {
        return weight;
    }

    public LotteryRarity getRarity() {
        return rarity;
    }

    public boolean isPityPrize() {
        return isPityPrize;
    }

    public String getNekoCurrencyId() {
        return nekoCurrencyId;
    }
}

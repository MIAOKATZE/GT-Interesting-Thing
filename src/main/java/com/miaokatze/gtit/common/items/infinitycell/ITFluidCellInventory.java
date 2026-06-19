package com.miaokatze.gtit.common.items.infinitycell;

import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;

import appeng.api.config.FuzzyMode;
import appeng.api.storage.IMEInventory;
import appeng.api.storage.data.IAEFluidStack;

public interface ITFluidCellInventory extends IMEInventory<IAEFluidStack> {

    double getIdleDrain(ItemStack is);

    void loadCellFluids();

    ItemStack getItemStack();

    FuzzyMode getFuzzyMode();

    IInventory getConfigInventory();

    IInventory getUpgradesInventory();

    int getBytesPerType();

    boolean canHoldNewFluid();

    long getTotalBytes();

    long getFreeBytes();

    long getUsedBytes();

    long getTotalFluidTypes();

    long getStoredFluidCount();

    long getStoredFluidTypes();

    long getRemainingFluidCount();

    long getRemainingFluidTypes();

    int getUnusedFluidCount();

    int getStatusForCell();

    String getUUID();
}

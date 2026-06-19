package com.miaokatze.gtit.common.items.infinitycell;

import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;

import appeng.api.config.FuzzyMode;
import appeng.api.storage.IMEInventory;
import appeng.api.storage.data.IAEItemStack;

public interface ITCellInventory extends IMEInventory<IAEItemStack> {

    double getIdleDrain(ItemStack is);

    void loadCellItems();

    ItemStack getItemStack();

    FuzzyMode getFuzzyMode();

    IInventory getConfigInventory();

    IInventory getUpgradesInventory();

    int getBytesPerType();

    boolean canHoldNewItem(ItemStack is);

    long getTotalBytes();

    long getFreeBytes();

    long getUsedBytes();

    long getTotalItemTypes();

    long getStoredItemCount();

    long getStoredItemTypes();

    long getRemainingItemTypes();

    long getRemainingItemCount();

    int getUnusedItemCount();

    int getStatusForCell();

    String getOreFilter();

    String getUUID();

    StorageManager getStorageManager();
}

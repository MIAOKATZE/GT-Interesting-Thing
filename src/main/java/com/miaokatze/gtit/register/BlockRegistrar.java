package com.miaokatze.gtit.register;

import net.minecraft.item.ItemStack;

import com.miaokatze.gtit.common.api.enums.GTITItemList;
import com.miaokatze.gtit.common.blocks.BlockNekoVendingCasing;

/** Registration of GTIT's own multiblock casings during pre-init. */
public final class BlockRegistrar {

    public static BlockNekoVendingCasing nekoVendingCasing;

    private BlockRegistrar() {}

    public static void init() {
        nekoVendingCasing = new BlockNekoVendingCasing();
        GTITItemList.NekoVendingCasing.set(new ItemStack(nekoVendingCasing, 1, 0));
        CreativeTabManager.addItemToTab(GTITItemList.NekoVendingCasing.get(1));
    }
}

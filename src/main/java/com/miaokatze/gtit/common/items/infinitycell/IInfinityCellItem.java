package com.miaokatze.gtit.common.items.infinitycell;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

import appeng.api.exceptions.AppEngException;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.ISaveProvider;
import appeng.api.storage.StorageChannel;

public interface IInfinityCellItem {

    IMEInventoryHandler<?> getInventoryHandler(ItemStack o, ISaveProvider container, EntityPlayer player)
        throws AppEngException;

    StorageChannel getChannel();
}

package com.miaokatze.gtit.common.items.infinitycell;

import java.util.Collection;
import java.util.Collections;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

import appeng.api.exceptions.AppEngException;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.ISaveProvider;
import appeng.api.storage.StorageChannel;
import appeng.api.storage.data.IAEStackType;

public interface IInfinityCellItem {

    IMEInventoryHandler<?> getInventoryHandler(ItemStack o, ISaveProvider container, EntityPlayer player)
        throws AppEngException;

    StorageChannel getChannel();

    /**
     * 本元件能应答的通道集合。AE2 驱动器/箱子会逐通道询问，默认按 getChannel() 派生单通道，
     * 使旧两枚元件零改动继续只在自身通道生效。
     */
    default Collection<IAEStackType<?>> getSupportedStackTypes() {
        return Collections.singleton(InfinityStackTypes.of(getChannel()));
    }

    default IMEInventoryHandler<?> getInventoryHandler(ItemStack o, ISaveProvider container, EntityPlayer player,
        IAEStackType<?> type) throws AppEngException {
        return getInventoryHandler(o, container, player);
    }
}

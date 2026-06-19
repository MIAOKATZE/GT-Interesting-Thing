package com.miaokatze.gtit.common.items.infinitycell;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.IIcon;

import appeng.api.implementations.tiles.IChestOrDrive;
import appeng.api.storage.ICellHandler;
import appeng.api.storage.IMEInventory;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.ISaveProvider;
import appeng.api.storage.StorageChannel;
import appeng.client.texture.ExtraBlockTextures;
import appeng.core.sync.GuiBridge;
import appeng.util.Platform;

public class InfinityCellHandler implements ICellHandler {

    @Override
    public boolean isCell(ItemStack is) {
        return is != null && is.getItem() instanceof IInfinityCellItem;
    }

    @Override
    public IMEInventoryHandler<?> getCellInventory(ItemStack is, ISaveProvider container, StorageChannel channel) {
        try {
            if (is.getItem() instanceof IInfinityCellItem iih) {
                if (iih.getChannel() == channel) {
                    return iih.getInventoryHandler(is, container, null);
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    @Override
    public IIcon getTopTexture_Light() {
        return ExtraBlockTextures.BlockMEChestItems_Light.getIcon();
    }

    @Override
    public IIcon getTopTexture_Medium() {
        return ExtraBlockTextures.BlockMEChestItems_Medium.getIcon();
    }

    @Override
    public IIcon getTopTexture_Dark() {
        return ExtraBlockTextures.BlockMEChestItems_Dark.getIcon();
    }

    @Override
    public void openChestGui(EntityPlayer player, IChestOrDrive chest, ICellHandler cellHandler,
        IMEInventoryHandler inv, ItemStack is, StorageChannel chan) {
        if (chest instanceof TileEntity te) {
            if (chan == StorageChannel.FLUIDS) {
                // Open fluid terminal - use AE2's native GUI for fluids
                Platform.openGUI(player, te, chest.getUp(), GuiBridge.GUI_ME);
            } else {
                Platform.openGUI(player, te, chest.getUp(), GuiBridge.GUI_ME);
            }
        }
    }

    @Override
    public int getStatusForCell(final ItemStack is, final IMEInventory handler) {
        if (handler instanceof InfinityCellInventoryHandler ci) {
            return ci.getStatusForCell();
        } else if (handler instanceof InfinityFluidCellInventoryHandler ci) {
            return ci.getStatusForCell();
        }
        return 0;
    }

    @Override
    public double cellIdleDrain(final ItemStack is, final IMEInventory handler) {
        if (handler instanceof InfinityCellInventoryHandler ci) {
            return ci.getCellInv()
                .getIdleDrain(is);
        } else if (handler instanceof InfinityFluidCellInventoryHandler ci) {
            return ci.getCellInv()
                .getIdleDrain(is);
        }
        return 0;
    }
}

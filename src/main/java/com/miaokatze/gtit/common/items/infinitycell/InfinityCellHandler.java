package com.miaokatze.gtit.common.items.infinitycell;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.IIcon;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

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

    /** 统一 logger（O2-B02 去中心化：与主类同用 "gtit" logger 名，日志过滤口径不变） */
    private static final Logger LOG = LogManager.getLogger("gtit");

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
        } catch (Exception e) {
            // 不再静默吞异常：StorageManager 未初始化（客户端 tooltip、serverStarted 前）等情况
            // 会在此抛出，记录便于诊断"元件静默失效"问题
            LOG.warn("获取无限元件 InventoryHandler 失败", e);
        }
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

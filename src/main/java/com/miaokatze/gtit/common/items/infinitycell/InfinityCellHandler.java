package com.miaokatze.gtit.common.items.infinitycell;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
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
import appeng.api.storage.data.IAEStackType;
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
                    LegacyCellReminderScheduler.INSTANCE.observe(is, container);
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

    /**
     * AE2 本 fork 的通道真身是 IAEStackType，接口默认实现只把 ITEM/FLUID 桥到上面那个已废弃的
     * StorageChannel 版、其余一律返回 null —— 不自己覆写就拿不到第三方通道（源质等）。
     */
    @Override
    public IMEInventoryHandler getCellInventory(ItemStack is, ISaveProvider container, IAEStackType<?> type) {
        try {
            if (is != null && is.getItem() instanceof IInfinityCellItem iih
                && iih.getSupportedStackTypes()
                    .contains(type)) {
                LegacyCellReminderScheduler.INSTANCE.observe(is, container);
                return iih.getInventoryHandler(is, container, null, type);
            }
        } catch (Exception e) {
            LOG.warn("获取无限元件 InventoryHandler 失败（通道 {}）", type == null ? "null" : type.getId(), e);
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
            if (player instanceof EntityPlayerMP mp) {
                LegacyCellReminderScheduler.INSTANCE.observeOwner(mp, te);
            }
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
        } else if (handler instanceof InfinityTypedCellHandler<?>ci) {
            return ci.getCellStatus();
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
        } else if (handler instanceof InfinityTypedCellHandler<?>ci) {
            final InfinityTypedCellInventory<?> inv = ci.getTypedCellInv();
            return inv == null ? 0D : inv.getIdleDrain();
        }
        return 0;
    }
}

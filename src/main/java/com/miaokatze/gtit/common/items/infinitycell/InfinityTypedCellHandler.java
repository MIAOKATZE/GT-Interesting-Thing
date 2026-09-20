package com.miaokatze.gtit.common.items.infinitycell;

import appeng.api.storage.ICellCacheRegistry;
import appeng.api.storage.StorageChannel;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IAEStackType;
import appeng.me.storage.MEInventoryHandler;

/**
 * 通道中立的无限单元 handler。每个通道各持一份独立实例（ME 箱的 wrap 无去重，共用实例会重复计空闲功耗与通知）。
 * 分区/升级卡只在物品通道生效，这里承载流体与第三方通道，故不做分区。
 */
public class InfinityTypedCellHandler<T extends IAEStack<T>> extends MEInventoryHandler<T>
    implements ICellCacheRegistry {

    private final IAEStackType<T> stackType;

    public InfinityTypedCellHandler(InfinityTypedCellInventory<T> inventory, IAEStackType<T> stackType) {
        super(inventory, stackType);
        this.stackType = stackType;
    }

    public InfinityTypedCellInventory<T> getTypedCellInv() {
        final Object internal = this.getInternal();
        return internal instanceof InfinityTypedCellInventory ? (InfinityTypedCellInventory<T>) internal : null;
    }

    @Override
    public boolean canGetInv() {
        return this.getTypedCellInv() != null;
    }

    @Override
    public long getTotalBytes() {
        return Integer.MAX_VALUE;
    }

    @Override
    public long getFreeBytes() {
        return Integer.MAX_VALUE;
    }

    @Override
    public long getUsedBytes() {
        return 0L;
    }

    @Override
    public long getTotalTypes() {
        return Integer.MAX_VALUE;
    }

    @Override
    public long getFreeTypes() {
        return Integer.MAX_VALUE;
    }

    @Override
    public long getUsedTypes() {
        return this.canGetInv() ? this.getTypedCellInv()
            .getStoredTypes() : 0L;
    }

    @Override
    public int getCellStatus() {
        return this.canGetInv() ? this.getTypedCellInv()
            .getStatusForCell() : 0;
    }

    @Override
    public StorageChannel getStorageChannel() {
        return InfinityStackTypes.toChannel(this.stackType);
    }

    @Override
    public TYPE getCellType() {
        return InfinityStackTypes.toCacheType(this.stackType);
    }
}

package com.miaokatze.gtit.common.items.infinitycell;

import java.util.Objects;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import appeng.api.config.Actionable;
import appeng.api.exceptions.AppEngException;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.storage.IMEInventory;
import appeng.api.storage.ISaveProvider;
import appeng.api.storage.StorageChannel;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IAEStackType;
import appeng.api.storage.data.IItemList;
import appeng.util.Platform;

/**
 * 通道中立的无限元件存储：同一枚元件被 AE2 逐通道询问时，每个通道各自拿到一份独立实例，
 * 内容按 (uuid, 通道) 分桶存放。物品通道仍由 InfinityItemStorageCellInventory 承担（保留分区/升级卡行为）。
 */
public class InfinityTypedCellInventory<T extends IAEStack<T>> implements IMEInventory<T> {

    private static final Logger LOG = LogManager.getLogger("gtit");

    protected final ItemStack cellItem;
    protected final ISaveProvider container;
    protected final NBTTagCompound data;
    protected final DataStorage storage;
    protected final IAEStackType<T> stackType;
    protected final int perType;
    protected final double idleDrain;
    private IItemList<T> contents;

    public InfinityTypedCellInventory(ItemStack o, ISaveProvider c, IAEStackType<T> type, int perType, double idleDrain)
        throws AppEngException {
        if (o == null) {
            throw new AppEngException("ItemStack was used as a cell, but was not a cell!");
        }
        this.cellItem = o;
        this.container = c;
        this.stackType = Objects.requireNonNull(type, "type");
        this.perType = perType;
        this.idleDrain = idleDrain;
        this.data = Platform.openNbtData(o);
        // 防护：StorageManager 在 serverStarted 才初始化，客户端 tooltip 路径下为 null。
        final StorageManager manager = StorageManager.getInstance();
        if (manager == null) {
            throw new AppEngException("StorageManager 未初始化（服务器未完全启动或客户端 tooltip 路径）");
        }
        this.storage = manager.getStorage(this.cellItem, this.stackType);
    }

    private IItemList<T> getContents() {
        if (this.contents == null) {
            this.contents = this.storage.<T>getListFor();
            for (final T stack : this.contents) {
                if (stack.getStackSize() <= 0) {
                    stack.reset();
                }
            }
        }
        return this.contents;
    }

    public double getIdleDrain() {
        return this.idleDrain;
    }

    public String getUUID() {
        return this.data.hasNoTags() ? "" : this.data.getString(InfinityCellConstants.DISKUUID);
    }

    public ItemStack getItemStack() {
        return this.cellItem;
    }

    public int getStatusForCell() {
        return 1;
    }

    public long getStoredTypes() {
        long count = 0;
        for (final T stack : getContents()) {
            if (stack.getStackSize() > 0) {
                count++;
            }
        }
        return count;
    }

    @Override
    public T injectItems(T input, Actionable mode, BaseActionSource src) {
        if (input == null || input.getStackSize() == 0) {
            return null;
        }
        final T stored = getContents().findPrecise(input);
        if (stored != null) {
            if (mode == Actionable.MODULATE) {
                stored.setStackSize(stored.getStackSize() + input.getStackSize());
                this.saveChanges();
            }
            return null;
        }
        if (mode == Actionable.MODULATE) {
            getContents().add(input.copy());
            this.saveChanges();
        }
        return null;
    }

    @Override
    public T extractItems(T request, Actionable mode, BaseActionSource src) {
        if (request == null) {
            return null;
        }
        final T stored = getContents().findPrecise(request);
        if (stored == null) {
            return null;
        }
        final long size = request.getStackSize();
        final T result = stored.copy();
        if (stored.getStackSize() <= size) {
            result.setStackSize(stored.getStackSize());
            if (mode == Actionable.MODULATE) {
                stored.setStackSize(0);
                stored.reset();
                this.saveChanges();
            }
        } else {
            result.setStackSize(size);
            if (mode == Actionable.MODULATE) {
                stored.setStackSize(stored.getStackSize() - size);
                this.saveChanges();
            }
        }
        return result;
    }

    @Override
    public IItemList<T> getAvailableItems(IItemList<T> out) {
        for (final T stack : getContents()) {
            out.add(stack);
        }
        return out;
    }

    @Override
    public StorageChannel getChannel() {
        return InfinityStackTypes.toChannel(this.stackType);
    }

    @Override
    public IAEStackType<T> getStackType() {
        return this.stackType;
    }

    private void saveChanges() {
        this.data.setBoolean(InfinityCellConstants.IS_EMPTY, getContents().isEmpty());
        if (this.container != null) {
            try {
                this.container.saveChanges(this);
            } catch (Throwable t) {
                LOG.warn("无限单元 saveChanges 失败（通道 {}）", this.stackType.getId(), t);
            }
        }
        final StorageManager manager = StorageManager.getInstance();
        if (manager != null) {
            manager.postChanges(this.storage);
        }
    }
}

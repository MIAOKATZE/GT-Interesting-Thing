package com.miaokatze.gtit.common.items.infinitycell;

import java.util.UUID;

import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import appeng.api.AEApi;
import appeng.api.storage.StorageChannel;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IAEStackType;
import appeng.api.storage.data.IItemList;
import appeng.util.item.AEFluidStack;
import appeng.util.item.AEItemStack;
import appeng.util.item.AEItemStackType;

/**
 * 单个 (UUID, 通道) 的外置存储桶。物品与流体沿用各自的强类型列表与旧 NBT 键，
 * 第三方通道（源质等已注册 IAEStackType）走 type 中立的列表。
 */
@SuppressWarnings({ "rawtypes", "unchecked" })
public class DataStorage {

    private final UUID uuid;
    private final IAEStackType stackType;
    private IItemList<IAEItemStack> items;
    private IItemList<IAEFluidStack> fluids;
    private IItemList typed;

    public DataStorage(UUID uuid, StorageChannel channel) {
        this(uuid, InfinityStackTypes.of(channel));
    }

    public DataStorage(UUID uuid, IAEStackType<?> stackType) {
        this.uuid = uuid;
        this.stackType = stackType;
    }

    public IAEStackType<?> getStackType() {
        return this.stackType;
    }

    public String getTypeId() {
        return this.stackType.getId();
    }

    public boolean isItemChannel() {
        return this.stackType == AEItemStackType.ITEM_STACK_TYPE;
    }

    public boolean isFluidChannel() {
        return this.stackType == InfinityStackTypes.FLUID_STACK_TYPE;
    }

    /** 仅物品/流体两通道有意义；第三方通道没有 StorageChannel，误用直接抛而不静默拿错列表。 */
    public StorageChannel getChannel() {
        if (isItemChannel()) {
            return StorageChannel.ITEMS;
        }
        if (isFluidChannel()) {
            return StorageChannel.FLUIDS;
        }
        throw new IllegalStateException("通道 " + getTypeId() + " 没有对应的 StorageChannel");
    }

    public IItemList<IAEItemStack> getItems() {
        if (this.items == null) {
            this.items = AEApi.instance()
                .storage()
                .createItemList();
        }
        return items;
    }

    public IItemList<IAEFluidStack> getFluids() {
        if (this.fluids == null) {
            this.fluids = AEApi.instance()
                .storage()
                .createFluidList();
        }
        return fluids;
    }

    public <T extends IAEStack<T>> IItemList<T> getTypedList() {
        if (this.typed == null) {
            this.typed = this.stackType.createList();
        }
        return this.typed;
    }

    /**
     * 按通道取回该桶唯一的那份列表：物品/流体仍用旧的强类型列表，
     * 否则同一 (uuid, fluid) 会出现两份互不可见的列表。
     */
    public <T extends IAEStack<T>> IItemList<T> getListFor() {
        if (isItemChannel()) {
            return (IItemList<T>) this.getItems();
        }
        if (isFluidChannel()) {
            return (IItemList<T>) this.getFluids();
        }
        return this.getTypedList();
    }

    public boolean isEmpty() {
        if (isItemChannel()) {
            return this.getItems()
                .isEmpty();
        }
        if (isFluidChannel()) {
            return this.getFluids()
                .isEmpty();
        }
        return this.typed == null || this.typed.isEmpty();
    }

    public String getUUID() {
        return this.uuid.toString();
    }

    public UUID getRawUUID() {
        return this.uuid;
    }

    public static DataStorage readFromNBT(UUID uuid, NBTTagList data, StorageChannel channel) {
        return readFromNBT(uuid, data, InfinityStackTypes.of(channel));
    }

    public static DataStorage readFromNBT(UUID uuid, NBTTagList data, IAEStackType<?> stackType) {
        final DataStorage storage = new DataStorage(uuid, stackType);
        storage.readFromNBT(data);
        return storage;
    }

    public void readFromNBT(NBTTagList data) {
        if (data == null) {
            return;
        }
        if (isItemChannel()) {
            for (int x = 0; x < data.tagCount(); x++) {
                final IAEItemStack ais = AEItemStack.loadItemStackFromNBT(data.getCompoundTagAt(x));
                if (ais != null) {
                    this.getItems()
                        .add(ais);
                }
            }
        } else if (isFluidChannel()) {
            for (int x = 0; x < data.tagCount(); x++) {
                final IAEFluidStack afs = AEFluidStack.loadFluidStackFromNBT(data.getCompoundTagAt(x));
                if (afs != null) {
                    this.getFluids()
                        .add(afs);
                }
            }
        } else {
            final IItemList list = this.getTypedList();
            for (int x = 0; x < data.tagCount(); x++) {
                final IAEStack ais = (IAEStack) this.stackType.loadStackFromNBT(data.getCompoundTagAt(x));
                if (ais != null) {
                    list.add(ais);
                }
            }
        }
    }

    public NBTBase writeToNBT() {
        if (isItemChannel()) {
            return writeList(this.getItems());
        }
        if (isFluidChannel()) {
            return writeList(this.getFluids());
        }
        return writeList(this.typed);
    }

    private NBTTagList writeList(final Iterable<? extends IAEStack<?>> myList) {
        final NBTTagList out = new NBTTagList();
        if (myList == null) {
            return out;
        }
        for (final IAEStack<?> ais : myList) {
            if (ais != null && ais.getStackSize() > 0) {
                final NBTTagCompound compound = new NBTTagCompound();
                ais.writeToNBT(compound);
                out.appendTag(compound);
            }
        }
        return out;
    }
}

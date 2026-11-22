// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtit.common.items.infinitycell;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
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
    // AE precise identity (equals/hashCode) is independent of quantity.
    private Map<IAEStack<?>, Object> snapshotKeys;

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
        final IItemList<?> contents = initializedContents();
        if (contents != null) {
            for (final IAEStack<?> stack : contents) {
                if (InfinityCellTransfer.hasStock(stack)) return false;
            }
        }
        return true;
    }

    <T extends IAEStack<T>> T findAvailable(T request) {
        final IItemList<T> contents = (IItemList<T>) initializedContents();
        if (contents == null) return null;
        final T stored = contents.findPrecise(request);
        return InfinityCellTransfer.hasStock(stored) && stored.isSameType(request) ? stored.copy() : null;
    }

    private IItemList<?> initializedContents() {
        if (isItemChannel()) return this.items;
        if (isFluidChannel()) return this.fluids;
        return this.typed;
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
        return readFromNBT(uuid, data, stackType, null);
    }

    static DataStorage readFromNBT(UUID uuid, NBTTagList data, IAEStackType<?> stackType,
        Map<Object, NBTTagCompound> committed) {
        final DataStorage storage = new DataStorage(uuid, stackType);
        storage.readFromNBT(data, committed);
        return storage;
    }

    public void readFromNBT(NBTTagList data) {
        readFromNBT(data, null);
    }

    private void readFromNBT(NBTTagList data, Map<Object, NBTTagCompound> committed) {
        if (data == null || data.tagCount() == 0) return;
        final IItemList list = getListFor();
        for (int x = 0; x < data.tagCount(); x++) {
            final NBTTagCompound raw = data.getCompoundTagAt(x);
            // Keep the baseline separate from tags retained by an AE stack's decoder.
            final NBTTagCompound baseline = committed == null ? null : (NBTTagCompound) raw.copy();
            final IAEStack stack = readStack(raw);
            if (stack == null) continue;
            list.add(stack);
            if (committed != null) {
                final IAEStack<?> stored = list.findPrecise(stack);
                if (stored == null) continue;
                final boolean duplicate = snapshotKeys != null && snapshotKeys.containsKey(stored);
                final Object key = snapshotKey(stored);
                if (InfinityCellTransfer.hasStock(stored)) {
                    committed.put(key, duplicate ? snapshot(stored) : baseline);
                } else {
                    committed.remove(key);
                }
            }
        }
    }

    private IAEStack<?> readStack(NBTTagCompound tag) {
        if (isItemChannel()) return AEItemStack.loadItemStackFromNBT(tag);
        if (isFluidChannel()) return AEFluidStack.loadFluidStackFromNBT(tag);
        return (IAEStack<?>) stackType.loadStackFromNBT(tag);
    }

    public NBTBase writeToNBT() {
        return writeContents();
    }

    Object snapshotKey(IAEStack<?> stack) {
        if (snapshotKeys == null) snapshotKeys = new HashMap<>();
        return snapshotKeys.computeIfAbsent(stack, ignored -> new Object());
    }

    void forgetSnapshotKey(IAEStack<?> stack, Object key) {
        snapshotKeys.remove(stack, key);
    }

    Map<Object, NBTTagCompound> snapshotAll() {
        snapshotKeys = null;
        final Map<Object, NBTTagCompound> result = new LinkedHashMap<>();
        final IItemList<?> contents = initializedContents();
        if (contents != null) {
            for (final IAEStack<?> stack : contents) {
                if (InfinityCellTransfer.hasStock(stack)) result.put(snapshotKey(stack), snapshot(stack));
            }
        }
        return result;
    }

    static NBTTagCompound snapshot(IAEStack<?> stack) {
        if (!InfinityCellTransfer.hasStock(stack)) return null;
        return (NBTTagCompound) encode(stack).copy();
    }

    private static NBTTagCompound encode(IAEStack<?> stack) {
        final NBTTagCompound compound = new NBTTagCompound();
        stack.writeToNBT(compound);
        return compound;
    }

    NBTTagList writeContents() {
        final NBTTagList out = new NBTTagList();
        final IItemList<?> contents = initializedContents();
        if (contents == null) {
            return out;
        }
        for (final IAEStack<?> ais : contents) {
            if (InfinityCellTransfer.hasStock(ais)) {
                out.appendTag(encode(ais));
            }
        }
        return out;
    }
}

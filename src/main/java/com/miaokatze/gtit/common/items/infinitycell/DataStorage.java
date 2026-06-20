package com.miaokatze.gtit.common.items.infinitycell;

import java.util.UUID;

import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import appeng.api.AEApi;
import appeng.api.storage.StorageChannel;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IItemList;
import appeng.util.item.AEFluidStack;
import appeng.util.item.AEItemStack;

public class DataStorage {

    private IItemList<IAEItemStack> items;
    private IItemList<IAEFluidStack> fluids;
    private final UUID uuid;
    private final StorageChannel channel;

    public DataStorage(UUID uuid, StorageChannel channel) {
        this.uuid = uuid;
        this.channel = channel;
    }

    public StorageChannel getChannel() {
        return this.channel;
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

    public boolean isEmpty() {
        if (this.getChannel() == StorageChannel.ITEMS) {
            return this.getItems()
                .isEmpty();
        } else {
            return this.getFluids()
                .isEmpty();
        }
    }

    public String getUUID() {
        return this.uuid.toString();
    }

    public UUID getRawUUID() {
        return this.uuid;
    }

    public static DataStorage readFromNBT(UUID uuid, NBTTagList data, StorageChannel channel) {
        DataStorage storage = new DataStorage(uuid, channel);
        storage.readFromNBT(data);
        return storage;
    }

    public void readFromNBT(NBTTagList data) {
        if (this.getChannel() == StorageChannel.ITEMS) {
            for (final IAEItemStack ais : this.readItemList(data)) {
                this.getItems()
                    .add(ais);
            }
        } else {
            for (final IAEFluidStack ais : this.readFluidList(data)) {
                this.getFluids()
                    .add(ais);
            }
        }
    }

    private IItemList<IAEFluidStack> readFluidList(final NBTTagList tag) {
        final IItemList<IAEFluidStack> out = AEApi.instance()
            .storage()
            .createFluidList();
        if (tag == null) {
            return out;
        }
        for (int x = 0; x < tag.tagCount(); x++) {
            final NBTTagCompound compound = tag.getCompoundTagAt(x);
            IAEFluidStack ais = AEFluidStack.loadFluidStackFromNBT(compound);
            if (ais != null) {
                out.add(ais);
            }
        }
        return out;
    }

    private IItemList<IAEItemStack> readItemList(final NBTTagList tag) {
        final IItemList<IAEItemStack> out = AEApi.instance()
            .storage()
            .createItemList();
        if (tag == null) {
            return out;
        }
        for (int x = 0; x < tag.tagCount(); x++) {
            final IAEItemStack ais = AEItemStack.loadItemStackFromNBT(tag.getCompoundTagAt(x));
            if (ais != null) {
                out.add(ais);
            }
        }
        return out;
    }

    public NBTBase writeToNBT() {
        if (this.getChannel() == StorageChannel.ITEMS) {
            return writeItemList(this.getItems());
        } else {
            return writeFluidList(this.getFluids());
        }
    }

    private NBTTagList writeFluidList(final IItemList<IAEFluidStack> myList) {
        final NBTTagList out = new NBTTagList();
        for (final IAEFluidStack ais : myList) {
            if (ais.getStackSize() > 0) {
                final NBTTagCompound compound = new NBTTagCompound();
                ais.writeToNBT(compound);
                out.appendTag(compound);
            }
        }
        return out;
    }

    private NBTTagList writeItemList(final IItemList<IAEItemStack> myList) {
        final NBTTagList out = new NBTTagList();
        for (final IAEItemStack ais : myList) {
            if (ais.getStackSize() > 0) {
                final NBTTagCompound compound = new NBTTagCompound();
                ais.writeToNBT(compound);
                out.appendTag(compound);
            }
        }
        return out;
    }
}

package com.miaokatze.gtit.hologram;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraft.world.WorldSavedData;

/** Large editor drafts belong to server world storage, never inventory synchronization packets. */
public final class HologramDraftStorage extends WorldSavedData {

    private static final String NAME = "gtit_hologram_drafts";
    private NBTTagCompound drafts = new NBTTagCompound();

    public HologramDraftStorage(String name) {
        super(name);
    }

    static HologramDraftStorage get(World world) {
        HologramDraftStorage data = (HologramDraftStorage) world.perWorldStorage
            .loadData(HologramDraftStorage.class, NAME);
        if (data == null) {
            data = new HologramDraftStorage(NAME);
            world.perWorldStorage.setData(NAME, data);
        }
        return data;
    }

    NBTTagCompound read(String key) {
        return drafts.getCompoundTag(key);
    }

    void put(String key, NBTTagCompound draft) {
        drafts.setTag(key, draft);
        markDirty();
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        drafts = tag.getCompoundTag("drafts");
    }

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        tag.setTag("drafts", drafts);
    }
}

package com.miaokatze.gtit.common.items.terminal;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

/** Persistent address only. Reading a binding never creates NBT or loads a world. */
public final class NekoVendingTarget {

    public static final String TAG = "GTITNekoVendingTarget";
    public final int dimension;
    public final int x;
    public final int y;
    public final int z;

    public NekoVendingTarget(int dimension, int x, int y, int z) {
        this.dimension = dimension;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public static NekoVendingTarget read(ItemStack stack) {
        if (stack == null || !stack.hasTagCompound()
            || !stack.getTagCompound()
                .hasKey(TAG, 10))
            return null;
        NBTTagCompound tag = stack.getTagCompound()
            .getCompoundTag(TAG);
        if (!tag.hasKey("dimension", 3) || !tag.hasKey("x", 3) || !tag.hasKey("y", 3) || !tag.hasKey("z", 3))
            return null;
        int y = tag.getInteger("y");
        if (y < 0 || y >= 256) return null;
        return new NekoVendingTarget(tag.getInteger("dimension"), tag.getInteger("x"), y, tag.getInteger("z"));
    }

    public void write(ItemStack stack) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("dimension", dimension);
        tag.setInteger("x", x);
        tag.setInteger("y", y);
        tag.setInteger("z", z);
        if (!stack.hasTagCompound()) stack.setTagCompound(new NBTTagCompound());
        stack.getTagCompound()
            .setTag(TAG, tag);
    }

    @Override
    public boolean equals(Object value) {
        if (!(value instanceof NekoVendingTarget)) return false;
        NekoVendingTarget other = (NekoVendingTarget) value;
        return dimension == other.dimension && x == other.x && y == other.y && z == other.z;
    }

    @Override
    public int hashCode() {
        return ((dimension * 31 + x) * 31 + y) * 31 + z;
    }
}

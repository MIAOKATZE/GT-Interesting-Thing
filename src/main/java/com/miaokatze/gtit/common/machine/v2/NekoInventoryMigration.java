package com.miaokatze.gtit.common.machine.v2;

import net.minecraft.nbt.NBTTagCompound;

/** Append capacity, never let ItemStackHandler.deserializeNBT resize an old eight-slot save back down. */
public final class NekoInventoryMigration {

    private NekoInventoryMigration() {}

    public static NBTTagCompound expand(NBTTagCompound old, int minimum) {
        NBTTagCompound copy = (NBTTagCompound) old.copy();
        copy.setInteger("Size", Math.max(minimum, copy.getInteger("Size")));
        return copy;
    }

    public static int inputIndex(int external) {
        if (external >= 0 && external < 8) return external;
        if (external >= 108 && external < 116) return external - 100;
        return -1;
    }

    public static int outputIndex(int external) {
        return external >= 8 && external < 108 ? external - 8 : -1;
    }
}

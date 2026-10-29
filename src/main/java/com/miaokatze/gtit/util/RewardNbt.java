package com.miaokatze.gtit.util;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

/** Strict optional reward fields: missing legacy fields are valid; malformed existing fields are not defaults. */
public final class RewardNbt {

    private RewardNbt() {}

    public static void optionalType(NBTTagCompound nbt, String key, int type) {
        if (nbt.hasKey(key) && !nbt.hasKey(key, type))
            throw new IllegalArgumentException("Invalid reward NBT type: " + key);
    }

    public static NBTTagList list(NBTTagCompound nbt, String key, int elementType) {
        optionalType(nbt, key, 9);
        NBTTagList decoded = nbt.getTagList(key, elementType);
        // Vanilla returns a NEW EMPTY list on an element-type mismatch, even when hasKey(key, 9) is true.
        // Compare the raw count so empty native lists (whose element type is 0) remain backward compatible.
        if (nbt.hasKey(key) && ((NBTTagList) nbt.getTag(key)).tagCount() != decoded.tagCount())
            throw new IllegalArgumentException("Invalid reward NBT list element type: " + key);
        return decoded;
    }

    public static void item(NBTTagCompound item) {
        optionalType(item, "id", 2);
        optionalType(item, "Count", 1);
        optionalType(item, "Damage", 2);
        optionalType(item, "tag", 10);
    }
}

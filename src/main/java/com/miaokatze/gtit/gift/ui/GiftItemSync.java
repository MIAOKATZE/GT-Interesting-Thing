package com.miaokatze.gtit.gift.ui;

import java.io.IOException;
import java.util.function.Supplier;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;

import com.cleanroommc.modularui.value.sync.GenericSyncValue;

/** Explicit type is required because an empty gift row legitimately starts with a null item. */
final class GiftItemSync {

    // Matches GiftDefinition's validated per-entry amount limit.
    private static final int MAX_COUNT = 4096;

    private GiftItemSync() {}

    static GenericSyncValue<ItemStack, ?> readOnly(Supplier<ItemStack> getter) {
        return GenericSyncValue.builder(ItemStack.class)
            .getter(getter)
            .serializer(GiftItemSync::write)
            .deserializer(GiftItemSync::read)
            .equals(ItemStack::areItemStacksEqual)
            .copy(ItemStack::copy)
            .nullable()
            .build();
    }

    private static void write(PacketBuffer buffer, ItemStack stack) throws IOException {
        checkCount(stack.stackSize);
        ItemStack copy = stack.copy();
        copy.stackSize = 1;
        NBTTagCompound item = new NBTTagCompound();
        copy.writeToNBT(item);
        buffer.writeNBTTagCompoundToBuffer(item);
        buffer.writeInt(stack.stackSize);
    }

    private static ItemStack read(PacketBuffer buffer) throws IOException {
        NBTTagCompound item = buffer.readNBTTagCompoundFromBuffer();
        int count = buffer.readInt();
        checkCount(count);
        ItemStack stack = item == null ? null : ItemStack.loadItemStackFromNBT(item);
        if (stack == null) throw new IOException("Invalid gift preview item");
        stack.stackSize = count;
        return stack;
    }

    private static void checkCount(int count) throws IOException {
        if (count < 1 || count > MAX_COUNT) throw new IOException("Invalid gift preview item count");
    }
}

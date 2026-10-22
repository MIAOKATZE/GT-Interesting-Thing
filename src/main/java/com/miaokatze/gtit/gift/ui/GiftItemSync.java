package com.miaokatze.gtit.gift.ui;

import java.io.IOException;
import java.util.Base64;
import java.util.function.Supplier;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketBuffer;

import com.cleanroommc.modularui.value.sync.GenericSyncValue;
import com.miaokatze.gtit.gift.GiftNbt;

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

    static void write(PacketBuffer buffer, ItemStack stack) throws IOException {
        checkCount(stack.stackSize);
        // Vanilla's byte count and signed-short compressed NBT length cannot represent gift drafts.
        byte[] tag;
        try {
            String encoded = GiftNbt.encode(stack.getTagCompound());
            tag = encoded == null ? new byte[0]
                : Base64.getDecoder()
                    .decode(encoded);
        } catch (IllegalArgumentException e) {
            throw new IOException(e.getMessage(), e);
        }
        buffer.writeInt(Item.getIdFromItem(stack.getItem()));
        buffer.writeInt(stack.getItemDamage());
        buffer.writeInt(stack.stackSize);
        buffer.writeInt(tag.length);
        buffer.writeBytes(tag);
    }

    static ItemStack read(PacketBuffer buffer) throws IOException {
        if (buffer.readableBytes() < 16) throw new IOException("Truncated gift item");
        Item item = Item.getItemById(buffer.readInt());
        int meta = buffer.readInt();
        int count = buffer.readInt();
        checkCount(count);
        int length = buffer.readInt();
        if (item == null || meta < 0
            || meta > 32767
            || length < 0
            || length > GiftNbt.MAX_BYTES
            || length > buffer.readableBytes()) throw new IOException("Invalid gift item payload");
        ItemStack stack = new ItemStack(item, count, meta);
        if (length > 0) {
            byte[] tag = new byte[length];
            buffer.readBytes(tag);
            try {
                stack.setTagCompound(
                    GiftNbt.decode(
                        Base64.getEncoder()
                            .encodeToString(tag)));
            } catch (IllegalArgumentException e) {
                throw new IOException(e.getMessage(), e);
            }
        }
        return stack;
    }

    private static void checkCount(int count) throws IOException {
        if (count < 1 || count > MAX_COUNT) throw new IOException("Invalid gift preview item count");
    }
}

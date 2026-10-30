package com.miaokatze.gtit.common.items.pocket;

import java.util.UUID;

import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

/**
 * Server-created per-open token: vanilla copies retain it, a different pocket does not.
 * Opening again rotates the token so the previous panel cannot save over the new session.
 */
public final class PocketCarrierIdentity {

    private static final String KEY = "gtitPocketSession";
    private final ItemStack original;
    private final String token;

    private PocketCarrierIdentity(ItemStack original, String token) {
        this.original = original;
        this.token = token;
    }

    public static PocketCarrierIdentity capture(ItemStack carrier, boolean server) {
        NBTTagCompound tag = carrier == null ? null : carrier.getTagCompound();
        String token = tag == null ? "" : tag.getString(KEY);
        if (carrier != null && server) {
            if (tag == null) {
                tag = new NBTTagCompound();
                carrier.setTagCompound(tag);
            }
            token = UUID.randomUUID()
                .toString();
            tag.setString(KEY, token);
        }
        return new PocketCarrierIdentity(carrier, token);
    }

    public boolean matches(ItemStack candidate) {
        if (original == null || candidate == null
            || candidate.stackSize <= 0
            || candidate.getItem() != original.getItem()) {
            return false;
        }
        if (token.isEmpty()) {
            return candidate == original;
        }
        final NBTTagCompound tag = candidate.getTagCompound();
        return tag != null && token.equals(tag.getString(KEY));
    }

    /** Null means absent or ambiguous; repeating the same physical reference is not ambiguity. */
    public Resolution locate(ItemStack direct, ItemStack cursor, ItemStack[] inventory, IInventory baubles) {
        final java.util.Set<ItemStack> matches = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        if (matches(direct)) matches.add(direct);
        if (matches(cursor)) matches.add(cursor);
        if (inventory != null) {
            for (ItemStack stack : inventory) if (matches(stack)) matches.add(stack);
        }
        if (baubles != null) {
            for (int slot = 0; slot < baubles.getSizeInventory(); slot++) {
                final ItemStack stack = baubles.getStackInSlot(slot);
                if (matches(stack)) matches.add(stack);
            }
        }
        return new Resolution(
            matches.size() == 1 ? matches.iterator()
                .next() : null,
            matches.size() > 1);
    }

    /** The server ticks both the main inventory and Baubles, but not a cursor-only carrier. */
    public static boolean isHeld(ItemStack carrier, ItemStack[] inventory, IInventory baubles) {
        if (carrier == null) return false;
        if (inventory != null) {
            for (ItemStack stack : inventory) if (stack == carrier) return true;
        }
        if (baubles != null) {
            for (int slot = 0; slot < baubles.getSizeInventory(); slot++) {
                if (baubles.getStackInSlot(slot) == carrier) return true;
            }
        }
        return false;
    }

    public static final class Resolution {

        public final ItemStack carrier;
        public final boolean ambiguous;

        Resolution(ItemStack carrier, boolean ambiguous) {
            this.carrier = carrier;
            this.ambiguous = ambiguous;
        }
    }
}

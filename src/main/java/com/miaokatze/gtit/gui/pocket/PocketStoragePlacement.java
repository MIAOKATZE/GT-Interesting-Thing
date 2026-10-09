package com.miaokatze.gtit.gui.pocket;

import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;

import com.cleanroommc.modularui.widgets.slot.ModularSlot;

/** 超大存储堆只允许补入同类物品，不交换或取出原堆。 */
public final class PocketStoragePlacement {

    private PocketStoragePlacement() {}

    public static int place(ModularSlot slot, InventoryPlayer inventory, int button) {
        if (inventory == null || (button != 0 && button != 1)) return 0;
        final ItemStack held = inventory.getItemStack();
        final ItemStack stored = slot.getStack();
        if (held == null || held.stackSize <= 0
            || stored == null
            || stored.stackSize <= 64
            || stored.getItem() != held.getItem()
            || stored.getItemDamage() != held.getItemDamage()
            || !ItemStack.areItemStackTagsEqual(stored, held)) return 0;
        final int room = slot.getItemStackLimit(held) - stored.stackSize;
        if (room <= 0) return 0;
        // Native isItemValid temporarily removes and restores the stack; do not call it on an over-limit pile.
        if (!slot.isItemValid(held)) return 0;
        final int moved = Math.min(button == 1 ? 1 : held.stackSize, room);
        // Copy before mutation so ModularSlot.putStack detects the change and invokes its sync callback.
        final ItemStack merged = stored.copy();
        merged.stackSize += moved;
        held.stackSize -= moved;
        if (held.stackSize == 0) inventory.setItemStack(null);
        slot.putStack(merged);
        return moved;
    }
}

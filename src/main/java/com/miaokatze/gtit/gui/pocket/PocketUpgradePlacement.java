package com.miaokatze.gtit.gui.pocket;

import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;

import com.cleanroommc.modularui.widgets.slot.ModularSlot;

/** 放入插件与取出插件分开处理：不可取出的已占用格仍允许追加同型插件。 */
public final class PocketUpgradePlacement {

    private PocketUpgradePlacement() {}

    public static int place(ModularSlot slot, InventoryPlayer inventory, int button) {
        if (inventory == null || (button != 0 && button != 1)) return 0;
        final ItemStack held = inventory.getItemStack();
        if (held == null || held.stackSize <= 0 || !slot.isItemValid(held)) return 0;
        final ItemStack stored = slot.getStack();
        if (stored != null && (stored.getItem() != held.getItem() || stored.getItemDamage() != held.getItemDamage()
            || !ItemStack.areItemStackTagsEqual(stored, held))) return 0;
        final int existing = stored == null ? 0 : stored.stackSize;
        final int limit = Math.min(slot.getItemStackLimit(held), held.getMaxStackSize());
        final int moved = Math.min(button == 1 ? 1 : held.stackSize, limit - existing);
        if (moved <= 0) return 0;
        // A new object is essential: ModularSlot.putStack compares against the current stack.
        final ItemStack merged = (stored == null ? held : stored).copy();
        merged.stackSize = existing + moved;
        held.stackSize -= moved;
        if (held.stackSize == 0) inventory.setItemStack(null);
        slot.putStack(merged);
        return moved;
    }
}

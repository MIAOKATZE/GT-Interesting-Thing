package com.miaokatze.gtit.gui.vm.edit;

import net.minecraft.item.ItemStack;

import com.cleanroommc.modularui.widgets.slot.PhantomItemSlot;

/** A normal synchronized phantom slot explicitly authorized to receive an embedded catalog copy. */
public class NekoGhostSlot extends PhantomItemSlot implements NekoGhostTarget {

    @Override
    public boolean acceptNeiGhost(ItemStack stack, int button) {
        return stack != null && handleDragAndDrop(stack.copy(), button == 1 ? 1 : 0);
    }
}

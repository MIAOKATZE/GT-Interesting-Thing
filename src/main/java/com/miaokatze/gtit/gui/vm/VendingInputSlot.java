package com.miaokatze.gtit.gui.vm;

import java.util.function.Predicate;

import net.minecraft.item.ItemStack;

import com.cleanroommc.modularui.utils.item.IItemHandler;
import com.cleanroommc.modularui.widgets.slot.ModularSlot;
import com.miaokatze.gtit.currency.NekoCurrencyRegistrar;

/** Deposits belong to the slot's actor, never to an inventory observer. */
final class VendingInputSlot extends ModularSlot {

    private final Predicate<ItemStack> deposit;
    private final Runnable changed;
    private final int inventoryIndex;

    VendingInputSlot(IItemHandler inventory, int index, Predicate<ItemStack> deposit, Runnable changed) {
        super(inventory, index);
        this.deposit = deposit;
        this.changed = changed;
        this.inventoryIndex = index;
    }

    @Override
    public boolean isItemValid(ItemStack stack) {
        // MUI2 shift merging mutates stackSize in place. Keep existing, actorless coins as
        // shared items; new deposits use an empty slot and therefore the actor's putStack.
        // The generic SlotItemHandler validator temporarily clears/restores the real slot.
        // These plain machine handlers only need their filter and slot capacity checked.
        return stack != null && stack.stackSize > 0
            && isCanPut()
            && getFilter().test(stack)
            && getItemHandler().isItemValid(inventoryIndex, stack)
            && getItemStackLimit(stack) > 0
            && !(NekoCurrencyRegistrar.isNekoCoinItem(stack) && NekoCurrencyRegistrar.isNekoCoinItem(getStack()));
    }

    @Override
    public int getItemStackLimit(ItemStack stack) {
        return Math.min(getSlotStackLimit(), stack.getMaxStackSize());
    }

    @Override
    public void putStack(ItemStack stack) {
        ItemStack previous = getStack();
        boolean incoming = NekoCurrencyRegistrar.isNekoCoinItem(stack) && stack.stackSize > 0
            && !NekoCurrencyRegistrar.isNekoCoinItem(previous);
        if (incoming && deposit.test(stack.copy())) {
            super.putStack(null);
            // The server slot can have remained empty throughout; clear the client's prediction too.
            if (isInitialized()) getSyncHandler().forceSyncItem();
        } else super.putStack(stack);
    }

    @Override
    public void onSlotChanged() {
        // Unlike onSlotChangedReal, this hook is also invoked for actual in-place merges.
        if (changed != null) changed.run();
    }
}

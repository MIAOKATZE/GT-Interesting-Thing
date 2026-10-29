package com.miaokatze.gtit.common.items.infinitycell;

import appeng.api.config.Actionable;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IItemList;

/** Shared signed-long boundary for all infinite-cell channels; excess remains with the caller. */
final class InfinityCellTransfer {

    private InfinityCellTransfer() {}

    static <T extends IAEStack<T>> T inject(IItemList<T> contents, T input, Actionable mode, long capacity,
        Runnable save) {
        if (input == null || input.getStackSize() <= 0) {
            return input;
        }
        final T stored = contents.findPrecise(input);
        final long current = stored == null ? 0L : Math.max(0L, stored.getStackSize());
        final long accepted = Math.min(input.getStackSize(), Math.min(Math.max(0L, capacity), Long.MAX_VALUE - current));
        if (accepted <= 0) {
            return input;
        }
        // Do not derive a remainder from ItemStack: its int count cannot represent the AE request.
        final T remainder = accepted == input.getStackSize() ? null : input.copy();
        if (remainder != null) {
            remainder.setStackSize(input.getStackSize() - accepted);
        }
        if (mode == Actionable.MODULATE) {
            if (stored == null) {
                final T added = input.copy();
                added.setStackSize(accepted);
                contents.add(added);
            } else {
                stored.setStackSize(current + accepted);
            }
            save.run();
        }
        return remainder;
    }

    static <T extends IAEStack<T>> T extract(IItemList<T> contents, T request, Actionable mode, Runnable save) {
        if (request == null || request.getStackSize() <= 0) {
            return null;
        }
        final T stored = contents.findPrecise(request);
        if (stored == null || stored.getStackSize() <= 0) {
            return null;
        }
        final long removed = Math.min(request.getStackSize(), stored.getStackSize());
        final T result = stored.copy();
        result.setStackSize(removed);
        if (mode == Actionable.MODULATE) {
            stored.setStackSize(stored.getStackSize() - removed);
            save.run();
        }
        return result;
    }
}

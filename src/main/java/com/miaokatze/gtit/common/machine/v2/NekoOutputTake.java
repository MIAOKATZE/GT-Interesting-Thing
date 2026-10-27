package com.miaokatze.gtit.common.machine.v2;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.item.ItemStack;

/** One server-thread take transaction against live logical slots, never against the visual frame quantity. */
public final class NekoOutputTake {

    public interface Slots {

        ItemStack get(int slot);

        void set(int slot, ItemStack item);
    }

    public interface Sink {

        int accept(ItemStack offered);
    }

    private NekoOutputTake() {}

    public static int take(NekoOutputLedger ledger, Slots slots, Sink sink, long dropId, long generation, int mode,
        int requested) {
        if (dropId <= 0 || generation != dropId || mode < 0 || mode > 2) return 0;
        ItemStack selected = null;
        List<NekoOutputLedger.Drop> drops = ledger.drops();
        for (NekoOutputLedger.Drop drop : drops) if (drop.dropId == dropId && !drop.slots.isEmpty()) {
            selected = drop.item;
            break;
        }
        if (selected == null) return 0;
        List<Integer> matching = new ArrayList<>();
        int total = 0;
        for (NekoOutputLedger.Drop drop : drops)
            if (NekoOutputLedger.same(selected, drop.item)) for (int index : drop.slots) {
                ItemStack actual = slots.get(index);
                if (actual != null && actual.stackSize > 0 && NekoOutputLedger.same(selected, actual)) {
                    matching.add(index);
                    total += actual.stackSize;
                }
            }
        int remaining = mode == 0 ? total : mode == 1 ? (total + 1) / 2 : Math.min(total, Math.max(0, requested));
        int taken = 0;
        for (int index : matching) {
            if (remaining <= 0) break;
            ItemStack actual = slots.get(index);
            if (actual == null || !NekoOutputLedger.same(selected, actual)) continue;
            int offered = Math.min(remaining, actual.stackSize);
            ItemStack copy = actual.copy();
            copy.stackSize = offered;
            int accepted = Math.max(0, Math.min(offered, sink.accept(copy)));
            if (accepted > 0) {
                ItemStack retained = actual.copy();
                retained.stackSize -= accepted;
                slots.set(index, retained.stackSize <= 0 ? null : retained);
                taken += accepted;
                remaining -= accepted;
            }
            if (accepted < offered) break; // Player full: leave the untouched remainder in the real output slot.
        }
        return taken;
    }
}

package com.miaokatze.gtit.common.machine.v2;

import java.util.function.IntSupplier;

/** Server-thread capacity reservations, not an inventory. Paid stacks live only in the machine queue/slots. */
public final class NekoOutputCapacity {

    private final IntSupplier emptySlots, paidPending;
    private int reserved;

    public NekoOutputCapacity(IntSupplier emptySlots, IntSupplier paidPending) {
        this.emptySlots = emptySlots;
        this.paidPending = paidPending;
    }

    public int available() {
        return (int) Math.max(0L, (long) emptySlots.getAsInt() - paidPending.getAsInt() - reserved);
    }

    public boolean owns(Reservation reservation) {
        return reservation != null && reservation.owner() == this;
    }

    public Reservation reserve(int count) {
        if (count < 0 || count > available()) return null;
        reserved += count;
        return new Reservation(count);
    }

    public final class Reservation implements AutoCloseable {

        private int remaining;
        private boolean closed;

        private Reservation(int count) {
            remaining = count;
        }

        private NekoOutputCapacity owner() {
            return NekoOutputCapacity.this;
        }

        public boolean canCommit(int count) {
            return !closed && count >= 0 && count <= remaining;
        }

        /** Convert reserved capacity to real pending stacks in the same server-thread transaction. */
        public void consume(int count) {
            if (!canCommit(count)) throw new IllegalStateException("invalid output reservation");
            remaining -= count;
            reserved -= count;
        }

        @Override
        public void close() {
            if (closed) return;
            reserved -= remaining;
            remaining = 0;
            closed = true;
        }
    }
}

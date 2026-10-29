package com.miaokatze.gtit.reincarnation.core;

import java.util.function.IntConsumer;

/**
 * A contiguous delivery prefix; a failed item must not be skipped by later scheduled items.
 * This handles recoverable exceptions, not atomic persistence of player inventory and the separate mailbox file.
 */
public final class ReincarnationGrantProgress {

    private int delivered;

    public ReincarnationGrantProgress(int delivered) {
        if (delivered < 0) {
            throw new IllegalArgumentException("Negative delivery prefix");
        }
        this.delivered = delivered;
    }

    public int getDelivered() {
        return delivered;
    }

    public void checkpoint(IntConsumer persist) {
        persist.accept(delivered);
    }

    public void deliver(int index, Runnable handoff, IntConsumer persist) {
        if (index != delivered) {
            return; // A repeated item or a gap never advances the prefix.
        }
        // Retry an earlier failed checkpoint before issuing any further item.
        checkpoint(persist);
        handoff.run();
        delivered++;
        // Keep the in-session prefix advanced if persistence fails after the handoff.
        checkpoint(persist);
    }
}

package com.miaokatze.gtit.gui.vm;

import com.miaokatze.gtit.common.machine.v2.VendingServerActions;

/** Queue completion must not live inside an action that a session guard may skip. */
public final class VendingActionCompletionTest {

    public static void main(String[] args) {
        final int[] counters = new int[2];
        VendingServerActions.scheduleServerAction(() -> counters[0]++, () -> counters[1]++);
        VendingServerActions.scheduleServerAction(() -> {}, () -> counters[1]++); // rejected guard
        VendingServerActions.scheduleServerAction(
            () -> { throw new IllegalStateException("expected probe failure"); },
            () -> counters[1]++);
        VendingServerActions.scheduleServerAction(() -> counters[0]++, () -> counters[1]++);
        VendingServerActions.drainServerActions();
        if (counters[0] != 2 || counters[1] != 4) throw new AssertionError("completion/queue isolation");
        VendingServerActions.drainServerActions();
        if (counters[1] != 4) throw new AssertionError("completion replay");
        System.out.println("VendingActionCompletionTest: accepted, skipped, failed and subsequent actions passed");
    }
}

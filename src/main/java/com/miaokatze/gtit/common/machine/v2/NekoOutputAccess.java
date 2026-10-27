package com.miaokatze.gtit.common.machine.v2;

import java.util.function.Supplier;

/** Internal authority gate. The remote supplier must resolve an existing live session, not a client boolean. */
final class NekoOutputAccess {

    private NekoOutputAccess() {}

    static boolean permits(Object expectedMachine, boolean serverActivePlayer, boolean localAccess,
        Supplier<?> resolveCurrentRemoteMachine) {
        if (expectedMachine == null || !serverActivePlayer) return false;
        return localAccess || resolveCurrentRemoteMachine.get() == expectedMachine;
    }
}

// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

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

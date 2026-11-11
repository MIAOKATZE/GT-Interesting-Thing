// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtit.testlab;

import cpw.mods.fml.common.gameevent.TickEvent;

/** One acknowledgement requires a tick observation followed by a completed frame of the same target screen. */
final class TestLabUiRenderGate {

    private boolean observed;

    void reset() {
        observed = false;
    }

    void observe(boolean targetPresent) {
        observed = targetPresent;
    }

    boolean rendered(TickEvent.Phase phase, boolean targetPresent) {
        if (phase != TickEvent.Phase.END || !observed || !targetPresent) return false;
        observed = false;
        return true;
    }
}

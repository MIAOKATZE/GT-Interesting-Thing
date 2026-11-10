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

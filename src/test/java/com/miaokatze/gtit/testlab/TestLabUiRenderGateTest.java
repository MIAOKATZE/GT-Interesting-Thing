// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtit.testlab;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/** Exercises the actual gate shared by ClientTick and the lowest-priority completed-frame handler. */
public final class TestLabUiRenderGateTest {

    private TestLabUiRenderGateTest() {}

    public static void main(String[] args) throws Exception {
        TestLabUiRenderGate gate = new TestLabUiRenderGate();
        if (gate.rendered(TickEvent.Phase.END, true)) throw new AssertionError("Unobserved screen acknowledged");
        gate.observe(true);
        if (gate.rendered(TickEvent.Phase.START, true)) throw new AssertionError("Frame start acknowledged");
        if (gate.rendered(TickEvent.Phase.END, false)) throw new AssertionError("Lost target screen acknowledged");
        if (!gate.rendered(TickEvent.Phase.END, true))
            throw new AssertionError("Completed target frame not acknowledged");
        if (gate.rendered(TickEvent.Phase.END, true)) throw new AssertionError("Duplicate acknowledgement");
        gate.observe(true);
        gate.reset();
        if (gate.rendered(TickEvent.Phase.END, true)) throw new AssertionError("Previous request leaked a frame");
        gate.observe(true);
        gate.observe(false);
        if (gate.rendered(TickEvent.Phase.END, true)) throw new AssertionError("Closed screen reused old observation");
        SubscribeEvent event = TestLabUiClient.class.getMethod("render", TickEvent.RenderTickEvent.class)
            .getAnnotation(SubscribeEvent.class);
        if (event == null || event.priority() != EventPriority.LOWEST)
            throw new AssertionError("Host render capture must precede native ACK");
        System.out.println("GTIT UI acknowledgement waits for a completed target frame after host capture");
    }
}

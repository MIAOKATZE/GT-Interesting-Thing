package com.miaokatze.gtit.hologram;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/** Drives construction exclusively on the server thread. */
public final class HologramLifecycle {

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) HologramService.tick();
    }
}

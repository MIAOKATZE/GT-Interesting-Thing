package com.miaokatze.gtit.client.gui;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

import net.minecraftforge.common.MinecraftForge;

import com.cleanroommc.modularui.screen.RichTooltip;
import com.cleanroommc.modularui.screen.RichTooltipEvent;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/** Opt-in tooltip palette: never recolors NEI, other mods or an unrelated vanilla item tooltip. */
public final class NekoVendingTooltip {

    private static final Set<RichTooltip> STYLED = Collections.newSetFromMap(new WeakHashMap<>());
    private static boolean registered;

    private NekoVendingTooltip() {}

    public static RichTooltip apply(RichTooltip tooltip) {
        if (!registered) {
            MinecraftForge.EVENT_BUS.register(new NekoVendingTooltip());
            registered = true;
        }
        STYLED.add(tooltip);
        return tooltip.textColor(NekoVendingTheme.TOOLTIP_INK)
            .textShadow(false)
            .titleMargin(2);
    }

    @SubscribeEvent
    public void color(RichTooltipEvent.Color event) {
        if (!STYLED.contains(event.getTooltip())) return;
        event.setBackground(0xf71d2e26);
        event.setBorderStart(0xff9b865c);
        event.setBorderEnd(0xff727957);
    }
}

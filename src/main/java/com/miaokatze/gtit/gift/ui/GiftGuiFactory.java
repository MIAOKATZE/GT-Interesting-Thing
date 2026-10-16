package com.miaokatze.gtit.gift.ui;

import net.minecraft.entity.player.EntityPlayerMP;

import com.cleanroommc.modularui.factory.SimpleGuiFactory;

/** The same factory is registered during common initialization on both sides. */
public final class GiftGuiFactory {

    private static final SimpleGuiFactory FACTORY = new SimpleGuiFactory("gtit:gift_library", GiftPanel::build);

    private GiftGuiFactory() {}

    public static void init() {
        FACTORY.init();
    }

    public static void open(EntityPlayerMP player) {
        if (player != null) FACTORY.open(player);
    }
}

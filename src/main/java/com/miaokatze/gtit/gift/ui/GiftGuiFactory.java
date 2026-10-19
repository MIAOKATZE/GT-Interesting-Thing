package com.miaokatze.gtit.gift.ui;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.PacketBuffer;

import com.cleanroommc.modularui.factory.GuiData;
import com.cleanroommc.modularui.factory.SimpleGuiFactory;

/** The same factory is registered during common initialization on both sides. */
public final class GiftGuiFactory {

    private static final SimpleGuiFactory FACTORY = new SimpleGuiFactory("gtit:gift_library", GiftPanel::build);
    private static final SimpleGuiFactory MANAGEMENT = new SimpleGuiFactory("gtit:gift_management", GiftPanel::build) {

        @Override
        public boolean canInteractWith(EntityPlayer player, GuiData data) {
            return player == data.getPlayer()
                && (!(player instanceof EntityPlayerMP server) || GiftAccessHandler.authorized(server));
        }

        @Override
        public GuiData readGuiData(EntityPlayer player, PacketBuffer buffer) {
            if (player instanceof EntityPlayerMP) throw new IllegalArgumentException("Use the authorized gift request");
            if (buffer.isReadable()) throw new IllegalArgumentException("Unexpected gift management payload");
            return new GuiData(player);
        }
    };

    private GiftGuiFactory() {}

    public static void init() {
        FACTORY.init();
        MANAGEMENT.init();
        GiftAccessHandler.install();
    }

    public static void open(EntityPlayerMP player) {
        if (player != null) FACTORY.open(player);
    }

    /** The O key opens an administrator window; the player claim command still uses open(). */
    public static void openManagement(EntityPlayerMP player) {
        if (GiftAccessHandler.authorized(player)) MANAGEMENT.open(player);
    }
}

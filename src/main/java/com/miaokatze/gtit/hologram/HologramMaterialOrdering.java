package com.miaokatze.gtit.hologram;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;

/** Optional material ordering integration; only an explicit, validated server-side UI action submits an order. */
public final class HologramMaterialOrdering {

    public interface Provider {

        boolean available(EntityPlayerMP player);

        /** The detached request contains missing item/material rows and typed blueprint resource shortages. */
        String submit(EntityPlayerMP player, NBTTagCompound request);
    }

    private static volatile Provider provider;

    private HologramMaterialOrdering() {}

    public static void register(Provider integration) {
        provider = integration;
    }

    public static boolean available(EntityPlayerMP player) {
        Provider current = provider;
        return current != null && current.available(player);
    }

    static String submit(EntityPlayerMP player, NBTTagCompound request) {
        Provider current = provider;
        if (current == null || !current.available(player)) return "AE 缺料下单接口尚未安装实现";
        return current.submit(player, (NBTTagCompound) request.copy());
    }
}

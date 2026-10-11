package com.miaokatze.gtit.client.nei;

import com.miaokatze.gtit.testutil.MinecraftTestBootstrap;

/** Install NEI's NBT access rules before the test suite or vanilla bootstrap resolves NBT classes. */
public final class NekoTradeNeiTestBootstrap {

    public static void main(String[] args) throws Exception {
        if (NekoTradeNeiTestBootstrap.class.getClassLoader() instanceof net.minecraft.launchwrapper.LaunchClassLoader) {
            java.lang.reflect.Field side = cpw.mods.fml.relauncher.FMLRelaunchLog.class.getDeclaredField("side");
            side.setAccessible(true);
            side.set(null, cpw.mods.fml.relauncher.Side.SERVER);
            java.lang.reflect.Field home = cpw.mods.fml.relauncher.FMLRelaunchLog.class
                .getDeclaredField("minecraftHome");
            home.setAccessible(true);
            home.set(null, new java.io.File("."));
            ((net.minecraft.launchwrapper.LaunchClassLoader) NekoTradeNeiTestBootstrap.class.getClassLoader())
                .registerTransformer("com.miaokatze.gtit.client.nei.NekoTradeNeiAccessTransformer");
        }
        if (!MinecraftTestBootstrap.enter(NekoTradeNeiTestBootstrap.class, args)) return;
        NekoTradeRecipeHandlerTest.main(args);
    }
}

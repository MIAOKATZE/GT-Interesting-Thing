package com.miaokatze.gtit.testutil;

/** 实际 Forge/原版注册表初始化；子加载器与既有贸易回归相同，避免用桩件替代 NBT/物品。 */
public final class MinecraftTestBootstrap {

    private MinecraftTestBootstrap() {}

    public static boolean enter(Class<?> suite, String[] args) throws Exception {
        if (!(suite.getClassLoader() instanceof net.minecraft.launchwrapper.LaunchClassLoader)) {
            String[] paths = System.getProperty("java.class.path")
                .split(java.io.File.pathSeparator);
            java.net.URL[] urls = new java.net.URL[paths.length];
            for (int i = 0; i < paths.length; i++) urls[i] = new java.io.File(paths[i]).toURI()
                .toURL();
            net.minecraft.launchwrapper.LaunchClassLoader loader = new net.minecraft.launchwrapper.LaunchClassLoader(
                urls);
            net.minecraft.launchwrapper.Launch.classLoader = loader;
            net.minecraft.launchwrapper.Launch.minecraftHome = new java.io.File(".");
            Thread.currentThread()
                .setContextClassLoader(loader);
            Class<?> injection = loader.loadClass("cpw.mods.fml.relauncher.FMLInjectionData");
            java.lang.reflect.Method build = injection
                .getDeclaredMethod("build", java.io.File.class, net.minecraft.launchwrapper.LaunchClassLoader.class);
            build.setAccessible(true);
            build.invoke(null, new java.io.File("."), loader);
            Object[] data = (Object[]) injection.getMethod("data")
                .invoke(null);
            loader.loadClass("cpw.mods.fml.common.Loader")
                .getMethod("injectData", Object[].class)
                .invoke(null, new Object[] { data });
            loader.loadClass(suite.getName())
                .getMethod("main", String[].class)
                .invoke(null, new Object[] { args });
            return false;
        }
        java.lang.reflect.Field side = cpw.mods.fml.relauncher.FMLRelaunchLog.class.getDeclaredField("side");
        side.setAccessible(true);
        side.set(null, cpw.mods.fml.relauncher.Side.SERVER);
        java.lang.reflect.Field home = cpw.mods.fml.relauncher.FMLRelaunchLog.class.getDeclaredField("minecraftHome");
        home.setAccessible(true);
        home.set(null, new java.io.File("."));
        net.minecraft.init.Bootstrap.func_151354_b();
        return true;
    }
}

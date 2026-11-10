package com.miaokatze.gtit.main;

import java.io.File;
import java.lang.reflect.InvocationTargetException;

import net.minecraftforge.common.config.Configuration;

import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;

/** Optional laboratory boundary. Production installations never resolve laboratory implementation classes. */
public final class TestLabBootstrap {

    private static boolean enabled;

    private TestLabBootstrap() {}

    public static void initialize(FMLPreInitializationEvent event) {
        enabled = requested(event.getModConfigurationDirectory());
        if (enabled) invoke("TestLabRuntime", "initialize", FMLPreInitializationEvent.class, event);
    }

    static boolean requested(File configDirectory) {
        File file = new File(configDirectory, "gtit/testlab.cfg");
        if (!file.isFile()) return false;
        Configuration config = new Configuration(file);
        config.load();
        return config.getBoolean("enabled", "testlab", false, "");
    }

    public static void serverStarting(FMLServerStartingEvent event) {
        if (enabled) invoke("TestLabRuntime", "serverStarting", FMLServerStartingEvent.class, event);
    }

    public static void serverStopping() {
        if (enabled) invoke("TestLabRuntime", "serverStopping", null, null);
    }

    public static void installClient() {
        if (enabled) invoke("TestLabUiClient", "install", null, null);
    }

    private static void invoke(String type, String method, Class<?> parameter, Object value) {
        try {
            Class<?> implementation = Class.forName("com.miaokatze.gtit.testlab." + type);
            if (parameter == null) implementation.getMethod(method)
                .invoke(null);
            else implementation.getMethod(method, parameter)
                .invoke(null, value);
        } catch (ReflectiveOperationException | LinkageError error) {
            Throwable cause = error instanceof InvocationTargetException ? error.getCause() : error;
            throw new IllegalStateException(
                "GTIT test laboratory was explicitly enabled but its testlab variant is missing or incompatible. "
                    + "Replace ordinary GTIT and MiaoKatzeCore JARs with matching -testlab JAR variants "
                    + "(install only one JAR per mod).",
                cause);
        }
    }
}

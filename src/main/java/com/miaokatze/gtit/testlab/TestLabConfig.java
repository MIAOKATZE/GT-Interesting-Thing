package com.miaokatze.gtit.testlab;

import java.io.File;

import net.minecraftforge.common.config.Configuration;

/** All activation switches require a restart. Normal installations register no lab listeners or packets. */
public final class TestLabConfig {

    public static boolean enabled;
    public static boolean clientUiEnabled;
    public static int dimensionId = 1048580;
    public static int timeoutTicks = 1200;
    public static int cleanupPerTick = 32;
    public static int maxIterations = 100;
    static File reportDirectory;

    private TestLabConfig() {}

    public static void load(File configDirectory) {
        Configuration config = new Configuration(new File(configDirectory, "gtit/testlab.cfg"));
        enabled = config.getBoolean("enabled", "testlab", false, "Opt-in automation laboratory. Restart required.");
        clientUiEnabled = config.getBoolean(
            "clientUiEnabled",
            "testlab",
            false,
            "Only enable on a dedicated test client. Allows explicit UI probes, never teleports players.");
        dimensionId = config.getInt(
            "dimensionId",
            "testlab",
            1048580,
            1048580,
            2000000,
            "Fixed dimension/provider ID. Known sibling defaults: GTSWN 19871, GTSR 1048577, GTMB 1048578. Conflicts fail closed.");
        timeoutTicks = config.getInt("timeoutTicks", "testlab", 1200, 20, 72000, "Per-scenario tick limit.");
        cleanupPerTick = config
            .getInt("cleanupPerTick", "testlab", 32, 1, 256, "Maximum owned blocks removed per tick.");
        maxIterations = config
            .getInt("maxIterations", "testlab", 100, 1, 1000, "Maximum requested profile/stress repeats.");
        reportDirectory = new File(new File(configDirectory.getParentFile(), "logs"), "gtit-testlab");
        if (config.hasChanged()) config.save();
    }
}

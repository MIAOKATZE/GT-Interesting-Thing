package com.miaokatze.gtit.config;

import java.io.File;

import net.minecraftforge.common.config.Configuration;

/** Independent switches for bundled content; administrator-authored content stays available. */
public final class DefaultContentConfig {

    private static volatile boolean tradeGroups = true;
    private static volatile boolean lottery = true;
    private static volatile boolean gifts = true;
    private static volatile int managementOpLevel = 2;

    private DefaultContentConfig() {}

    public static void init(File configurationDirectory) {
        Configuration config = new Configuration(new File(configurationDirectory, "gtit/default_content.cfg"));
        managementOpLevel = config.getInt(
            "managementOpLevel",
            "permissions",
            2,
            2,
            4,
            "Required dedicated-server OP level for gift and trade preset management (2-4). Single-player owner rules and OP4 default-trade restoration are unchanged.");
        tradeGroups = config.getBoolean(
            "loadDefaultTradeGroups",
            "defaults",
            true,
            "Load default trade groups, including all applied MiaoTrade presets and the legacy fallback. User-authored trade tabs remain available.");
        lottery = config.getBoolean(
            "loadDefaultLottery",
            "defaults",
            true,
            "Load bundled lottery pools. Edited or administrator-created pools remain available; an empty pools list stays empty.");
        gifts = config.getBoolean(
            "loadDefaultGifts",
            "defaults",
            true,
            "Load bundled gift definitions. Custom definitions and earned gift receipts are retained.");
        if (config.hasChanged()) config.save();
    }

    public static boolean loadDefaultTradeGroups() {
        return tradeGroups;
    }

    public static boolean loadDefaultLottery() {
        return lottery;
    }

    public static boolean loadDefaultGifts() {
        return gifts;
    }

    public static int managementOpLevel() {
        return managementOpLevel;
    }
}

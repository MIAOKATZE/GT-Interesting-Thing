package com.miaokatze.gtit.gift;

/** Shared gift icon names. Contains no client classes and is safe on a dedicated server. */
public final class GiftIconCatalog {

    public static final String[] DEFAULT_KEYS = { "starter", "steam", "lv", "mv", "hv", "ev", "iv", "luv" };

    private static final String[] KEYS = { "starter", "steam", "lv", "mv", "hv", "ev", "iv", "luv", "festival", "mail",
        "team", "energy", "ae", "decor", "mining", "farming", "food", "tools", "building", "magic", "space", "ocean",
        "forest", "winter", "spring", "summer", "autumn", "birthday", "lottery", "achievement", "admin", "trade" };

    private GiftIconCatalog() {}

    public static String[] keys() {
        return KEYS.clone();
    }

    public static boolean contains(String key) {
        if (key == null) return false;
        for (String known : KEYS) {
            if (known.equals(key)) return true;
        }
        return false;
    }

    /** Old definitions used "gift"; unknown or absent keys retain the starter icon. */
    public static String normalize(String key) {
        return contains(key) ? key : "starter";
    }

    /** Minecraft item-atlas registration name for the approved 24px gift sprite. */
    public static String atlasResourcePath(String key) {
        return "gtit:gifts/" + normalize(key);
    }

    public static String labelKey(String key) {
        return "gtit.gift.icon." + normalize(key);
    }

    public static int frameCount(String key) {
        return 1;
    }
}

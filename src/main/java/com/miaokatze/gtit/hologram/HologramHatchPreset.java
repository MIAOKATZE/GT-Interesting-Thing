package com.miaokatze.gtit.hologram;

/** Shared selection rule for a role's voltage candidates after global material reservations. */
final class HologramHatchPreset {

    private HologramHatchPreset() {}

    static int choose(int[] tiers, int[] available, int requested, boolean downgrade) {
        if (tiers.length == 0 || tiers.length != available.length) return -1;
        boolean fixed = true;
        for (int tier : tiers) if (tier != tiers[0]) fixed = false;
        int fallback = -1, stocked = -1;
        for (int i = 0; i < tiers.length; i++) {
            int tier = tiers[i];
            if (!fixed && tier != requested && (!downgrade || tier > requested)) continue;
            if (fallback < 0 || tier > tiers[fallback]) fallback = i;
            if (available[i] > 0 && (stocked < 0 || tier > tiers[stocked])) stocked = i;
        }
        return stocked < 0 ? fallback : stocked;
    }
}

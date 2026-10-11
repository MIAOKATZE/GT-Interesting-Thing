package com.miaokatze.gtit.client.hologram;

import java.util.List;

/** Monotonic selection over the declared tiers, independent of GUI and GL classes. */
final class HologramTargetTiers {

    private HologramTargetTiers() {}

    static int steppedTier(List<Integer> levels, int current, int direction) {
        if (direction > 0) {
            for (int level : levels) if (level > current) return level;
        } else {
            for (int i = levels.size() - 1; i >= 0; i--) if (levels.get(i) < current) return levels.get(i);
            if (current >= 0) return -1;
        }
        return current;
    }
}

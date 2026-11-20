// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtit.trade.preset;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Set;

/** Disk and runtime namespaces for default trade groups, separate from positive user page ids. */
public final class TradePresetLayout {

    public static final String PAGES_PATH = "config/gtit/trade/preset_pages.json";
    public static final String TRADES_PATH = "config/gtit/trade/preset_tabs";
    static final String NEKO_EXCHANGE_ID = "gtit.neko-exchange";
    private static final int CUSTOM_PAGE_BASE = -500000;
    private static final int PRESET_PAGE_BAND = 10000;
    private static final int LEGACY_PAGE_BAND = 100000;

    private TradePresetLayout() {}

    public static boolean isManagedPage(int id) {
        return id <= -100001 || builtinId(id) != null;
    }

    /** The v3 migration also reclaimed the gaps in the old built-in ranges from pre-v3 custom pages. */
    static boolean wasLegacyManagedBand(int id) {
        return id <= -100001 && id > CUSTOM_PAGE_BASE;
    }

    static boolean wasLegacyBuiltin(String presetId) {
        Builtin group = Builtin.forId(presetId);
        return group != null && group.legacyBase != 0;
    }

    static int pageId(String presetId, int sourceId) {
        Builtin group = Builtin.forId(presetId);
        if (group == null || sourceId <= 0 || sourceId >= group.pageBand()) {
            throw new IllegalArgumentException("默认贸易组的源标签页 ID 不在允许范围: " + presetId + " / " + sourceId);
        }
        return -(group.pageBase + sourceId);
    }

    /** MIAO100/200/300/400/500 built-in and MIAO900 custom-preset series, independent of page/order allocation. */
    static String tradeId(String presetId, String sourceId) {
        Builtin group = Builtin.forId(presetId);
        try {
            String key = group == null ? presetId + "\u0000" + sourceId : sourceId;
            byte[] hash = MessageDigest.getInstance("SHA-256")
                .digest(key.getBytes(StandardCharsets.UTF_8));
            return "MIAO" + (group == null ? 9 : group.tradeSeries) + "00" + new BigInteger(1, hash).toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    static int customPageId(String key, Set<Integer> occupied) {
        // Keep the existing custom allocator independent of the built-in catalog.
        int range = Integer.MAX_VALUE + CUSTOM_PAGE_BASE;
        int candidate = CUSTOM_PAGE_BASE - Math.floorMod(key.hashCode(), range);
        while (occupied.contains(candidate))
            candidate = candidate == Integer.MIN_VALUE ? CUSTOM_PAGE_BASE : candidate - 1;
        return candidate;
    }

    public static String builtinId(int pageId) {
        Builtin group = Builtin.forPage(pageId);
        return group == null ? null : group.id;
    }

    /** Interpret saved pre-layout page IDs without assigning any of them to a new preset. */
    public static int canonicalPageId(int pageId) {
        Builtin group = Builtin.forPage(pageId);
        if (group == null || group.legacyBase == 0
            || group.legacyBase == group.pageBase
            || !Builtin.containsPage(pageId, group.legacyBase, LEGACY_PAGE_BAND)) return pageId;
        int sourceId = -pageId - group.legacyBase;
        // Older hand-edited overrides could use larger IDs. Their old slots stay reserved for them.
        return sourceId < PRESET_PAGE_BAND ? -(group.pageBase + sourceId) : pageId;
    }

    public static String tabPath(int id) {
        return (isManagedPage(id) ? TRADES_PATH : "config/gtit/trade/trades") + "/tab_" + id + ".json";
    }

    private enum Builtin {

        AE_BASIC("gtit-ae-basic", 1, 10000, 100000),
        AE_ADVANCED("gtit-ae-advanced", 2, 20000, 200000),
        DECORATION("gtit-base-decoration", 3, 30000, 300000),
        BASE("gtit-base", 4, 400000, 400000),
        NEKO_EXCHANGE(NEKO_EXCHANGE_ID, 5, 40000, 0);

        private static final Builtin[] GROUPS = values();
        private final String id;
        private final int tradeSeries;
        private final int pageBase;
        private final int legacyBase;

        Builtin(String id, int tradeSeries, int pageBase, int legacyBase) {
            this.id = id;
            this.tradeSeries = tradeSeries;
            this.pageBase = pageBase;
            this.legacyBase = legacyBase;
        }

        private int pageBand() {
            return this == BASE ? LEGACY_PAGE_BAND : PRESET_PAGE_BAND;
        }

        private static Builtin forId(String id) {
            for (Builtin group : GROUPS) if (group.id.equals(id)) return group;
            return null;
        }

        private static Builtin forPage(int pageId) {
            for (Builtin group : GROUPS) {
                if (containsPage(pageId, group.pageBase, group.pageBand())
                    || group.legacyBase != 0 && containsPage(pageId, group.legacyBase, LEGACY_PAGE_BAND)) return group;
            }
            return null;
        }

        private static boolean containsPage(int pageId, int base, int band) {
            return pageId < -base && pageId > -(base + band);
        }
    }
}

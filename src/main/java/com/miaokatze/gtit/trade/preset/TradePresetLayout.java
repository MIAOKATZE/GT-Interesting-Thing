// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtit.trade.preset;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/** Disk and runtime namespaces for default trade groups, separate from positive user page ids. */
public final class TradePresetLayout {

    public static final String PAGES_PATH = "config/gtit/trade/preset_pages.json";
    public static final String TRADES_PATH = "config/gtit/trade/preset_tabs";
    private static final List<String> IDS = Arrays
        .asList("gtit-ae-basic", "gtit-ae-advanced", "gtit-base-decoration", "gtit-base", "gtit-neko-exchange");
    // Custom preset pages start below the last built-in band so a new built-in series never reclaims them.
    private static final int CUSTOM_PAGE_BASE = -(IDS.size() + 1) * 100000;

    private TradePresetLayout() {}

    public static boolean isManagedPage(int id) {
        return id <= -100001;
    }

    /** True for the negative namespace reserved for built-in groups; custom presets must be renumbered out of it. */
    public static boolean isBuiltinBand(int id) {
        return id <= -100001 && id > CUSTOM_PAGE_BASE;
    }

    static int pageId(String presetId, int sourceId) {
        int series = IDS.indexOf(presetId) + 1;
        if (series == 0 || sourceId <= 0 || sourceId >= 100000) {
            throw new IllegalArgumentException("默认贸易组的源标签页 ID 须为 1–99999");
        }
        return -(series * 100000 + sourceId);
    }

    /** MIAO100/200/300/400/500 built-in and MIAO900 custom-preset series, independent of page/order allocation. */
    static String tradeId(String presetId, String sourceId) {
        int series = IDS.indexOf(presetId) + 1;
        try {
            String key = series == 0 ? presetId + "\u0000" + sourceId : sourceId;
            byte[] hash = MessageDigest.getInstance("SHA-256")
                .digest(key.getBytes(StandardCharsets.UTF_8));
            return "MIAO" + (series == 0 ? 9 : series) + "00" + new BigInteger(1, hash).toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    static int customPageId(String key, Set<Integer> occupied) {
        // Built-in ranges end one band above CUSTOM_PAGE_BASE. Persist the chosen slot so catalog ordering
        // never changes identity.
        int range = Integer.MAX_VALUE - Math.abs(CUSTOM_PAGE_BASE);
        int candidate = CUSTOM_PAGE_BASE - Math.floorMod(key.hashCode(), range);
        while (occupied.contains(candidate))
            candidate = candidate == Integer.MIN_VALUE ? CUSTOM_PAGE_BASE : candidate - 1;
        return candidate;
    }

    public static String builtinId(int pageId) {
        if (pageId > -100001 || pageId <= CUSTOM_PAGE_BASE) return null;
        int series = (-pageId) / 100000;
        return series >= 1 && series <= IDS.size() && (-pageId) % 100000 != 0 ? IDS.get(series - 1) : null;
    }

    public static String tabPath(int id) {
        return (isManagedPage(id) ? TRADES_PATH : "config/gtit/trade/trades") + "/tab_" + id + ".json";
    }
}

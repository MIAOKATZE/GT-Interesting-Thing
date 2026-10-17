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
    private static final List<String> IDS = Arrays.asList("gtit-ae-basic", "gtit-ae-advanced", "gtit-base-decoration");

    private TradePresetLayout() {}

    public static boolean isManagedPage(int id) {
        return id <= -100001;
    }

    static int pageId(String presetId, int sourceId) {
        int series = IDS.indexOf(presetId) + 1;
        if (series == 0 || sourceId <= 0 || sourceId >= 100000) {
            throw new IllegalArgumentException("默认贸易组的源标签页 ID 须为 1–99999");
        }
        return -(series * 100000 + sourceId);
    }

    /** MIAO100/200/300 built-in and MIAO900 custom-preset series, independent of page/order allocation. */
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
        // Built-in ranges end at -399999. Persist the chosen slot so catalog ordering never changes identity.
        int candidate = -400000 - Math.floorMod(key.hashCode(), Integer.MAX_VALUE - 400000);
        while (occupied.contains(candidate)) candidate = candidate == Integer.MIN_VALUE ? -400000 : candidate - 1;
        return candidate;
    }

    public static String tabPath(int id) {
        return (isManagedPage(id) ? TRADES_PATH : "config/gtit/trade/trades") + "/tab_" + id + ".json";
    }
}

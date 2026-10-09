package com.miaokatze.gtit.gui.pocket;

import java.util.Locale;

/** 仅压缩格内数量显示，库存与tooltip始终使用精确整数。 */
public final class PocketItemCountText {

    private PocketItemCountText() {}

    public static String format(int count) {
        if (count <= 1) {
            return "";
        }
        return count < 1000 ? Integer.toString(count) : String.format(Locale.ROOT, "%.1fk", count / 1000.0);
    }
}

package com.miaokatze.gtit.gui.pocket;

import java.math.BigDecimal;

/** 精确十进制量级显示；原始整数仍用于容量计算与悬浮提示。 */
public final class PocketCapacityText {

    private PocketCapacityText() {}

    public static String format(long amount) {
        final long unit;
        final String suffix;
        if (amount >= 1_000_000_000L) {
            unit = 1_000_000_000L;
            suffix = "G";
        } else if (amount >= 1_000_000L) {
            unit = 1_000_000L;
            suffix = "M";
        } else if (amount >= 1_000L) {
            unit = 1_000L;
            suffix = "k";
        } else {
            return Long.toString(amount);
        }
        return BigDecimal.valueOf(amount)
            .divide(BigDecimal.valueOf(unit))
            .stripTrailingZeros()
            .toPlainString() + suffix;
    }
}

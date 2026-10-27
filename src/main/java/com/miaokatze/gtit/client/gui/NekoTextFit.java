package com.miaokatze.gtit.client.gui;

import java.util.function.ToIntFunction;

/** Pure single-line fitting, measured by the caller's actual font; never splits a Unicode code point. */
public final class NekoTextFit {

    private NekoTextFit() {}

    public static String fit(String value, int pixelWidth, float scale, ToIntFunction<String> measure) {
        if (value == null || pixelWidth <= 0 || !(scale > 0)) return "";
        String text = value.replace('\n', ' ')
            .replace('\r', ' ')
            .replace('\t', ' ');
        int budget = Math.max(0, (int) Math.floor(pixelWidth / scale));
        if (measure.applyAsInt(text) <= budget) return text;
        String ellipsis = "…";
        if (measure.applyAsInt(ellipsis) > budget) return "";
        int low = 0, high = text.codePointCount(0, text.length());
        while (low < high) {
            int middle = (low + high + 1) >>> 1;
            String candidate = prefix(text, middle) + ellipsis;
            if (measure.applyAsInt(candidate) <= budget) low = middle;
            else high = middle - 1;
        }
        String result = prefix(text, low) + ellipsis;
        while (low > 0 && measure.applyAsInt(result) > budget) result = prefix(text, --low) + ellipsis;
        return result;
    }

    private static String prefix(String text, int codePoints) {
        int end = text.offsetByCodePoints(0, codePoints);
        // Formatting introducers must stay paired with their formatting code.
        if (end > 0 && text.charAt(end - 1) == '\u00a7') end--;
        return text.substring(0, end);
    }
}

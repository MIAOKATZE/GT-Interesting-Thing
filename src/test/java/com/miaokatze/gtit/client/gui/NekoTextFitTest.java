package com.miaokatze.gtit.client.gui;

import java.util.function.ToIntFunction;

/** Isolated fitting contract; the widget supplies Minecraft's real font at draw time. */
public final class NekoTextFitTest {

    public static void main(String[] args) {
        ToIntFunction<String> width = text -> text.codePointCount(0, text.length()) * 9;
        String name = repeat("长礼包名", 32);
        String fitted = NekoTextFit.fit(name, 184, .9f, width);
        check(width.applyAsInt(fitted) * .9f <= 184 && fitted.endsWith("…"), "128 CJK name fits one row");
        String unicode = repeat("🐈礼包𝄞", 32);
        for (int pixels = 0; pixels < 250; pixels++) {
            fitted = NekoTextFit.fit(unicode, pixels, .8f, width);
            check(width.applyAsInt(fitted) * .8f <= pixels, "bounded reward label");
            for (int i = 0; i < fitted.length(); i++) {
                char ch = fitted.charAt(i);
                if (Character.isHighSurrogate(ch)) check(
                    ++i < fitted.length() && Character.isLowSurrogate(fitted.charAt(i)),
                    "supplementary code point intact");
                else check(!Character.isLowSurrogate(ch), "no isolated low surrogate");
            }
        }
        check("a b c d".equals(NekoTextFit.fit("a\nb\rc\td", 200, 1, width)), "hard line breaks flattened");
        check("short".equals(NekoTextFit.fit("short", 200, 1, width)), "short label unchanged");
        check("".equals(NekoTextFit.fit(name, 1, 1, width)), "tiny area never overpaints ellipsis");
        check("".equals(NekoTextFit.fit(name, 200, 0, width)), "invalid scale rejected");
        System.out.println("NekoTextFitTest: CJK, supplementary Unicode, hard breaks and 250 widths passed");
    }

    private static String repeat(String text, int count) {
        StringBuilder result = new StringBuilder(text.length() * count);
        for (int i = 0; i < count; i++) result.append(text);
        return result.toString();
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}

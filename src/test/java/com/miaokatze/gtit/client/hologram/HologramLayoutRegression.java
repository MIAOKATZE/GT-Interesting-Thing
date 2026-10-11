package com.miaokatze.gtit.client.hologram;

import com.miaokatze.gtit.testutil.SimpleAssert;

/** Checks usable margins across physical display sizes and Minecraft GUI scale factors. */
public final class HologramLayoutRegression {

    private HologramLayoutRegression() {}

    public static void run() {
        int[][] displays = { { 800, 600 }, { 1280, 720 }, { 1920, 1080 }, { 2560, 1440 }, { 3840, 2160 } };
        for (int[] display : displays) {
            for (int guiScale : new int[] { 1, 2, 3, 4, 8 }) {
                int width = (display[0] + guiScale - 1) / guiScale;
                int height = (display[1] + guiScale - 1) / guiScale;
                HologramScreenLayout layout = HologramScreenLayout.fit(width, height);
                int windowWidth = Math.round(HologramScreenLayout.WIDTH * layout.scale);
                int windowHeight = Math.round(HologramScreenLayout.HEIGHT * layout.scale);
                SimpleAssert.that(layout.scale > 0 && layout.scale <= 1, "large displays never magnify all controls");
                SimpleAssert.that(layout.originX >= 0 && layout.originY >= 0, "window origin remains visible");
                SimpleAssert.that(layout.originX + windowWidth <= width, "right edge stays on screen");
                SimpleAssert.that(layout.originY + windowHeight <= height, "bottom edge stays on screen");
                SimpleAssert.that(windowWidth <= Math.ceil(width * .90), "horizontal margins remain available");
                SimpleAssert.that(windowHeight <= Math.ceil(height * .85), "vertical margins remain available");
            }
        }
        SimpleAssert.that(
            HologramScreenLayout.VIEWPORT_X + HologramScreenLayout.VIEWPORT_WIDTH < HologramScreenLayout.PANEL_X,
            "fixed properties never overlap the preview or its selectable cells");
        SimpleAssert.that(
            HologramScreenLayout.PANEL_X + HologramScreenLayout.PANEL_WIDTH <= HologramScreenLayout.WIDTH,
            "properties remain inside the window");
    }
}

package com.miaokatze.gtit.client.hologram;

/** Fixed workstation geometry and a bounded fit in Minecraft's scaled GUI coordinates. */
public final class HologramScreenLayout {

    public static final int WIDTH = 480, HEIGHT = 288;
    public static final int VIEWPORT_X = 8, VIEWPORT_Y = 32, VIEWPORT_WIDTH = 264, VIEWPORT_HEIGHT = 224;
    public static final int PANEL_X = 280, PANEL_Y = 60, PANEL_WIDTH = 180, PANEL_HEIGHT = 194;

    public final float scale;
    public final int originX, originY;

    private HologramScreenLayout(int width, int height) {
        scale = Math.min(1F, Math.min(Math.max(1, width) * .90F / WIDTH, Math.max(1, height) * .85F / HEIGHT));
        originX = (width - Math.round(WIDTH * scale)) / 2;
        originY = (height - Math.round(HEIGHT * scale)) / 2;
    }

    public static HologramScreenLayout fit(int width, int height) {
        return new HologramScreenLayout(width, height);
    }
}

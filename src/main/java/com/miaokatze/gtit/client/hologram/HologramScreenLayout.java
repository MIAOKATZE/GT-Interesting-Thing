package com.miaokatze.gtit.client.hologram;

/** Fixed workstation geometry and a bounded fit in Minecraft's scaled GUI coordinates. */
public final class HologramScreenLayout {

    public static final int WIDTH = 600, HEIGHT = 340;
    public static final int VIEWPORT_X = 34, VIEWPORT_Y = 32, VIEWPORT_WIDTH = 408, VIEWPORT_HEIGHT = 272;
    public static final int PANEL_X = 280, PANEL_Y = 60, PANEL_WIDTH = 232, PANEL_HEIGHT = 218;
    public static final int COMPACT_PANEL_X = 452, COMPACT_PANEL_WIDTH = 140;
    public static final float CONTROL_WIDTH_SCALE = .60F;

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

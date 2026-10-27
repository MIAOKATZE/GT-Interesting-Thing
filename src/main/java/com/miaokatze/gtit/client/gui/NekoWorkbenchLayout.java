package com.miaokatze.gtit.client.gui;

import java.awt.Rectangle;

/** Pure native fullscreen workbench layout. No display pixels, GL state, or GUI-scale assumptions. */
public final class NekoWorkbenchLayout {

    public static final int MIN_WIDTH = 768, MIN_HEIGHT = 432;
    public int width = MIN_WIDTH, height = MIN_HEIGHT;
    public float scale = 1;

    public boolean resize(int screenWidth, int screenHeight) {
        int sw = Math.max(1, screenWidth), sh = Math.max(1, screenHeight);
        float next = Math.min(1f, Math.min(sw / (float) MIN_WIDTH, sh / (float) MIN_HEIGHT));
        int w = Math.max(MIN_WIDTH, Math.round(sw / next));
        int h = Math.max(MIN_HEIGHT, Math.round(sh / next));
        boolean changed = w != width || h != height || next != scale;
        width = w;
        height = h;
        scale = next;
        return changed;
    }

    public Rectangle rect(int x, int y, int w, int h) {
        return new Rectangle(x, y, Math.max(1, w), Math.max(1, h));
    }

    public int catalogWidth() {
        return Math.max(226, Math.min(350, width / 3));
    }

    public int catalogX() {
        return width - catalogWidth() - 12;
    }

    public Rectangle catalog() {
        return rect(catalogX() + 8, 103, catalogWidth() - 16, height - 147);
    }

    public Rectangle editor() {
        return rect(12, 80, catalogX() - 20, height - 116);
    }
}

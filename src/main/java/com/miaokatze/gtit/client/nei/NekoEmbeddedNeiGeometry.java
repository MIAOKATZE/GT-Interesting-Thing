package com.miaokatze.gtit.client.nei;

import java.awt.Rectangle;

/** Screen-logical-pixel layout; independent of GL, item loading and the MUI transform. */
public final class NekoEmbeddedNeiGeometry {

    public static final int HEADER_HEIGHT = 18;
    public static final int SEARCH_HEIGHT = 20;
    public final Rectangle bounds;
    public final Rectangle grid;
    public final Rectangle search;
    public final Rectangle previous;
    public final Rectangle next;

    private NekoEmbeddedNeiGeometry(Rectangle bounds) {
        this.bounds = new Rectangle(bounds);
        previous = new Rectangle(bounds.x, bounds.y, 16, 16);
        next = new Rectangle(bounds.x + bounds.width - 16, bounds.y, 16, 16);
        search = new Rectangle(bounds.x, bounds.y + bounds.height - SEARCH_HEIGHT, bounds.width, SEARCH_HEIGHT);
        grid = new Rectangle(
            bounds.x,
            bounds.y + HEADER_HEIGHT,
            bounds.width,
            bounds.height - HEADER_HEIGHT - SEARCH_HEIGHT - 2);
    }

    /** Invalid/tiny/offscreen regions must not steal input or relocate the global NEI panel. */
    public static NekoEmbeddedNeiGeometry fit(Rectangle requested, int screenWidth, int screenHeight, int slotSize) {
        if (requested == null || slotSize <= 0 || screenWidth <= 0 || screenHeight <= 0) return null;
        Rectangle clipped = requested.intersection(new Rectangle(0, 0, screenWidth, screenHeight));
        if (clipped.width < Math.max(40, slotSize * 2) || clipped.height < HEADER_HEIGHT + SEARCH_HEIGHT + 2 + slotSize)
            return null;
        return new NekoEmbeddedNeiGeometry(clipped);
    }
}

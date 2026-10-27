package com.miaokatze.gtit.gui.vm;

/** Pure layout for one native canvas. Coordinates are panel-local; inventory and NEI use the same transform. */
public final class NekoVendingLayout {

    public static final int MIN_WIDTH = 768, MIN_HEIGHT = 432;

    public static final class Rect {

        public final int x, y, width, height;

        public Rect(int x, int y, int width, int height) {
            this.x = x;
            this.y = y;
            this.width = Math.max(1, width);
            this.height = Math.max(1, height);
        }

        public int right() {
            return x + width;
        }

        public int bottom() {
            return y + height;
        }
    }

    public int width = MIN_WIDTH, height = MIN_HEIGHT;
    public float scale = 1;
    public Rect header, tools, mainRail, categories, business, inventory, detail, input, scene, outputStats, editor,
        catalog, tradeEditor, tradeCatalog, detailActions, rightPane, footer;

    public NekoVendingLayout() {
        resize(MIN_WIDTH, MIN_HEIGHT);
    }

    public boolean resize(int screenWidth, int screenHeight) {
        int sw = Math.max(1, screenWidth), sh = Math.max(1, screenHeight);
        float nextScale = Math.min(1f, Math.min(sw / (float) MIN_WIDTH, sh / (float) MIN_HEIGHT));
        int w = Math.max(MIN_WIDTH, Math.round(sw / nextScale));
        int h = Math.max(MIN_HEIGHT, Math.round(sh / nextScale));
        boolean changed = w != width || h != height || nextScale != scale;
        width = w;
        height = h;
        scale = nextScale;
        int gap = 6, category = 94;
        int rightWidth = Math.max(320, Math.min(400, Math.round(w * .39f)));
        int rightX = w - rightWidth - 8, leftX = 8;
        int leftWidth = rightX - gap - leftX, bodyY = 78;
        // Four real inventory rows occupy 76 px, plus title/insets. Leave a clear footer gutter.
        int bottomY = h - 120;
        header = new Rect(0, 0, w, 32);
        tools = new Rect(leftX, 36, leftWidth, 40);
        mainRail = new Rect(182, 4, 228, 24);
        categories = new Rect(leftX, bodyY, category, bottomY - bodyY);
        business = new Rect(leftX + category + gap, bodyY, leftWidth - category - gap, bottomY - bodyY);
        // Put the familiar inventory next to the central divider, not under the far-left navigation.
        inventory = new Rect(Math.min(rightX - gap - 174, w / 2 - 87), bottomY + gap, 174, 96);
        detail = new Rect(leftX, bottomY + gap, inventory.x - gap - leftX, 96);
        int actionSpace = rightX - gap - inventory.right() - gap;
        detailActions = actionSpace >= 96
            ? new Rect(inventory.right() + gap, inventory.y, Math.min(120, actionSpace), 96)
            : new Rect(detail.x + 6, detail.bottom() - 27, 96, 20);
        rightPane = new Rect(rightX, 36, rightWidth, h - 58);
        input = new Rect(rightX + 6, 42, rightWidth - 12, 64);
        outputStats = new Rect(rightX + rightWidth - 54, 122, 48, h - 178);
        scene = new Rect(rightX + 6, 122, rightWidth - 66, h - 150);
        editor = new Rect(rightX + 4, 40, rightWidth - 8, 220);
        tradeEditor = new Rect(rightX + 4, 40, rightWidth - 8, 180);
        catalog = new Rect(rightX + 6, 266, rightWidth - 12, h - 294);
        tradeCatalog = new Rect(rightX + 6, 226, rightWidth - 12, h - 254);
        footer = new Rect(0, h - 15, w, 15);
        return changed;
    }
}

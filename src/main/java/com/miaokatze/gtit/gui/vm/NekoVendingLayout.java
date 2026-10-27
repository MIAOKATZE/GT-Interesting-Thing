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
        catalog, rightPane, footer;

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
        int gap = 6, rail = 36, category = 82;
        int rightWidth = Math.max(288, Math.min(400, Math.round(w * .39f)));
        int rightX = w - rightWidth - 8, leftX = 8 + rail + gap;
        int leftWidth = rightX - gap - leftX, bodyY = 76, bottomY = h - 104;
        header = new Rect(0, 0, w, 30);
        tools = new Rect(leftX, 34, leftWidth, 34);
        mainRail = new Rect(8, bodyY, rail, bottomY - bodyY);
        categories = new Rect(leftX, bodyY, category, bottomY - bodyY);
        business = new Rect(leftX + category + gap, bodyY, leftWidth - category - gap, bottomY - bodyY);
        inventory = new Rect(leftX, bottomY + gap, 170, 86);
        detail = new Rect(leftX + 176, bottomY + gap, leftWidth - 176, 86);
        rightPane = new Rect(rightX, 34, rightWidth, h - 54);
        input = new Rect(rightX + 5, 39, rightWidth - 10, 58);
        outputStats = new Rect(rightX + rightWidth - 54, 117, 49, h - 174);
        scene = new Rect(rightX + 5, 117, rightWidth - 64, h - 144);
        editor = new Rect(rightX + 4, 38, rightWidth - 8, 220);
        catalog = new Rect(rightX + 5, 264, rightWidth - 10, h - 289);
        footer = new Rect(0, h - 15, w, 15);
        return changed;
    }
}

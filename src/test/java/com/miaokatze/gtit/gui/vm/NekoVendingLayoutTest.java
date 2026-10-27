package com.miaokatze.gtit.gui.vm;

/** Pure GUI-scale/region contracts; not a replacement for native GL and input validation. */
public final class NekoVendingLayoutTest {

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    private static boolean overlaps(NekoVendingLayout.Rect a, NekoVendingLayout.Rect b) {
        return a.x < b.right() && b.x < a.right() && a.y < b.bottom() && b.y < a.bottom();
    }

    public static void main(String[] args) {
        int count = 0;
        for (int[] screen : new int[][] { { 1280, 720 }, { 1920, 1080 }, { 1024, 768 }, { 2560, 1440 } }) {
            for (int guiScale = 1; guiScale <= 4; guiScale++) {
                NekoVendingLayout layout = new NekoVendingLayout();
                int w = screen[0] / guiScale, h = screen[1] / guiScale;
                layout.resize(w, h);
                check(Math.abs(layout.width * layout.scale - w) <= 1, "canvas fills logical width");
                check(Math.abs(layout.height * layout.scale - h) <= 1, "canvas fills logical height");
                check(!overlaps(layout.business, layout.categories), "category rail never overlays trade rows");
                check(!overlaps(layout.business, layout.inventory), "inventory never covers business content");
                check(!overlaps(layout.detail, layout.inventory), "detail and bag are separate");
                check(!overlaps(layout.business, layout.editor), "docked editing never hides the trade list");
                check(!overlaps(layout.editor, layout.catalog), "NEI and 32-choice editor are disjoint");
                check(!overlaps(layout.scene, layout.outputStats), "statistics never obscure the scene");
                check(
                    layout.categories.height >= 25 + 11 * 16 + 10 + 1 + 14 + 1 + 18,
                    "eleven categories, pager and create button fit");
                check(
                    layout.categories.height >= 13 * 16 + 12 * 2,
                    "all twelve lottery pools and create remain accessible");
                check(layout.catalog.height * layout.scale >= 56, "scaled NEI retains header, item row and search");
                check(layout.editor.width >= 280 && layout.editor.height >= 220, "all 32 choices have room");
                check(layout.catalog.height >= 100, "NEI has paging, item rows and native search space");
                check(layout.input.width >= 8 * 19, "two rows of eight true inputs fit");
                check(layout.inventory.width >= 9 * 18 && layout.inventory.height >= 76, "all player slots fit");
                check(!layout.resize(w, h), "stable screen does not continually rebuild layout");
                count++;
            }
        }
        System.out.println("NekoVendingLayoutTest: " + count + " display/GUI-scale combinations passed");
    }
}

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
        for (int[] screen : new int[][] { { 768, 432 }, { 854, 480 }, { 1024, 768 }, { 1280, 720 }, { 1920, 1080 },
            { 2560, 1440 }, { 3440, 1440 }, { 600, 900 } }) {
            for (int guiScale = 1; guiScale <= 4; guiScale++) {
                NekoVendingLayout layout = new NekoVendingLayout();
                int w = screen[0] / guiScale, h = screen[1] / guiScale;
                layout.resize(w, h);
                check(Math.abs(layout.width * layout.scale - w) <= 1, "canvas fills logical width");
                check(Math.abs(layout.height * layout.scale - h) <= 1, "canvas fills logical height");
                check(layout.tools.bottom() <= layout.business.y, "toolbar does not cover search/categories");
                check(layout.tools.width >= 322, "inline QoL and both wallets fit");
                check(
                    (layout.business.width - 8)
                        / com.miaokatze.gtit.client.gui.NekoTradeCardLayout.columns(layout.business.width) - 4 >= 129,
                    "cards keep both coins and material overflow visible");
                check(!overlaps(layout.business, layout.categories), "category rail never overlays trade rows");
                check(!overlaps(layout.business, layout.inventory), "inventory never covers business content");
                check(!overlaps(layout.detail, layout.inventory), "detail and bag are separate");
                check(!overlaps(layout.business, layout.editor), "docked editing never hides the trade list");
                check(!overlaps(layout.editor, layout.catalog), "NEI and 32-choice editor are disjoint");
                check(!overlaps(layout.scene, layout.outputStats), "statistics never obscure the scene");
                check(
                    layout.categories.height >= 25 + 6 * 24 + 5 + 1 + 14 + 1 + 24,
                    "six comfortable category rows, pager and create button fit");
                check(
                    layout.inventory.y + 14 + 76 <= layout.inventory.bottom(),
                    "native hotbar inside inventory frame");
                check(layout.inventory.bottom() + 3 <= layout.footer.y, "inventory clear of footer");
                check(layout.inventory.right() <= layout.rightPane.x - 6, "inventory constrained to left workspace");
                check(
                    Math.abs(layout.inventory.x + 87 - layout.width / 2) <= 38,
                    "inventory centered on canvas when space permits, otherwise constrained at divider");
                check(
                    layout.mainRail.x >= 170 && layout.mainRail.right() <= layout.width - 140,
                    "horizontal main navigation cannot hit title or preset action");
                check(
                    !overlaps(layout.tradeEditor, layout.tradeCatalog),
                    "compact trade editor gives actual NEI more rows");
                check(layout.tradeCatalog.height >= layout.catalog.height + 40, "trade NEI reclaims editor whitespace");
                check(
                    layout.tradeEditor.width / 2 - 9 >= 8 * 18,
                    "both sixteen-choice groups fit without enlarged slots");
                check(
                    layout.detailActions.bottom() <= layout.inventory.bottom(),
                    "persistent purchase action above footer");
                if (w >= 320 && h >= 240) check(
                    layout.catalog.height * layout.scale >= 56,
                    "Minecraft-supported logical sizes retain NEI header, item row and search");
                check(layout.editor.width >= 280 && layout.editor.height >= 220, "all 32 choices have room");
                check(layout.catalog.height >= 100, "NEI has paging, item rows and native search space");
                check(layout.input.width >= 16 * 18, "two rows of sixteen true inputs fit");
                check(layout.inventory.width >= 9 * 18 && layout.inventory.height >= 76, "all player slots fit");
                for (int slot = 0; slot < 32; slot++) {
                    int x = (slot % 16) * (layout.input.width - 18) / 15;
                    int y = 24 + slot / 16 * 19;
                    check(
                        x >= 0 && x + 18 <= layout.input.width && y + 18 <= layout.input.height,
                        "every true input fits its two-row region");
                }
                check(!layout.resize(w, h), "stable screen does not continually rebuild layout");
                count++;
            }
        }
        System.out.println("NekoVendingLayoutTest: " + count + " display/GUI-scale combinations passed");
    }
}

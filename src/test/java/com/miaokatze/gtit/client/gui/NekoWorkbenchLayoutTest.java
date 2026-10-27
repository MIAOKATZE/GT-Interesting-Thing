package com.miaokatze.gtit.client.gui;

import java.awt.Rectangle;

import com.miaokatze.gtit.client.nei.NekoEmbeddedNeiGeometry;

/** Pure fullscreen/NEI geometry contract. Does not claim an in-game graphical or input smoke run. */
public final class NekoWorkbenchLayoutTest {

    public static void main(String[] args) {
        int count = 0;
        NekoWorkbenchLayout layout = new NekoWorkbenchLayout();
        for (int[] physical : new int[][] { { 1280, 720 }, { 1920, 1080 }, { 1024, 768 }, { 2560, 1440 },
            { 3440, 1440 }, { 800, 600 } }) {
            for (int scale = 1; scale <= 4; scale++) {
                int width = physical[0] / scale, height = physical[1] / scale;
                layout.resize(width, height);
                check(Math.abs(layout.width * layout.scale - width) <= 1, "fills native logical width");
                check(Math.abs(layout.height * layout.scale - height) <= 1, "fills native logical height");
                Rectangle editor = layout.editor(), catalog = layout.catalog();
                check(!editor.intersects(catalog), "editor and actual NEI region do not overlap");
                check(editor.width >= 480 && editor.height >= 316, "gift controls and full inventory fit");
                check(catalog.width >= 210 && catalog.height >= 285, "native catalog has real paging/search room");
                Rectangle logical = new Rectangle(
                    Math.round(catalog.x * layout.scale),
                    Math.round(catalog.y * layout.scale),
                    Math.round(catalog.width * layout.scale),
                    Math.round(catalog.height * layout.scale));
                NekoEmbeddedNeiGeometry nei = NekoEmbeddedNeiGeometry.fit(logical, width, height, 18);
                check(nei != null, "real native grid remains eligible at GUI scale " + scale);
                check(!nei.grid.intersects(nei.search), "search and native item cells remain disjoint");
                check(!layout.resize(width, height), "unchanged viewport does not rebind or rescale repeatedly");
                count++;
            }
        }
        check(layout.resize(1200, 800), "resize binds fresh native rectangles");
        check(layout.resize(320, 240), "high GUI scale can return after resize");
        System.out.println("NekoWorkbenchLayoutTest: " + count + " display/GUI-scale combinations + resize passed");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}

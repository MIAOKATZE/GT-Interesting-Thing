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
        SimpleAssert.that(
            HologramScreenLayout.WIDTH == 520 && HologramScreenLayout.HEIGHT == 312,
            "expanded workspace reserves separate type and tier rows");
        SimpleAssert.that(
            HologramScreenLayout.PANEL_Y + HologramScreenLayout.PANEL_HEIGHT < 285,
            "expanded properties end before execution footer");
        java.util.List<Integer> levels = java.util.Arrays.asList(1, 3, 8);
        SimpleAssert
            .eq(1, HologramTargetTiers.steppedTier(levels, -1, 1), "all tiers advances to lowest declared tier");
        SimpleAssert.eq(3, HologramTargetTiers.steppedTier(levels, 1, 1), "plus skips undeclared tiers monotonically");
        SimpleAssert.eq(8, HologramTargetTiers.steppedTier(levels, 8, 1), "plus never wraps highest tier");
        SimpleAssert.eq(3, HologramTargetTiers.steppedTier(levels, 8, -1), "minus uses preceding declared tier");
        SimpleAssert.eq(-1, HologramTargetTiers.steppedTier(levels, 1, -1), "minus lowest restores all tier filter");
        SimpleAssert.eq(
            -1,
            HologramTargetTiers.steppedTier(java.util.Collections.emptyList(), -1, 1),
            "empty role cannot invent a tier");
        queueRegression();
    }

    private static void queueRegression() {
        HologramState original = snapshot("session-a", 10, 20);
        HologramState reordered = snapshot("session-a", 20, 10);
        HologramEditQueue queue = new HologramEditQueue();
        SimpleAssert.that(queue.offer("editCopy", original.session, original.cells.get(0)), "copy queued");
        SimpleAssert.that(queue.offer("editPaste", original.session, original.cells.get(0)), "paste queued after copy");
        HologramEditQueue.Entry copy = queue.removeFirst(reordered);
        SimpleAssert.that("editCopy".equals(copy.action), "copy remains first before paste");
        SimpleAssert
            .eq(1, copy.selectionIn(reordered), "reordered cells resolve world coordinates instead of old index");
        SimpleAssert
            .that("editPaste".equals(queue.removeFirst(reordered).action), "paste executes after copy acknowledgment");
        for (int i = 0; i < HologramEditQueue.LIMIT; i++) SimpleAssert.that(
            queue.offer("rotateCell", original.session, original.cells.get(0)),
            "bounded queue accepts eight intents");
        SimpleAssert.that(
            !queue.offer("editPaste", original.session, original.cells.get(0)),
            "ninth intent rejected explicitly");
        SimpleAssert.that(
            queue.removeFirst(snapshot("session-b", 10, 20)) == null,
            "stale session never dispatches queued input");
        SimpleAssert.that(queue.isEmpty(), "stale session clears remaining edits");
        queue.offer("editPaste", original.session, original.cells.get(0));
        SimpleAssert.that(
            queue.removeFirst(snapshot("session-a", 20, 30)) == null,
            "missing coordinate never redirects paste to another cell");
        queue.offer("editUndo", original.session, null);
        SimpleAssert.eq(
            -1,
            queue.removeFirst(reordered)
                .selectionIn(reordered),
            "history intents require no selected cell");
        queue.offer("editCopy", original.session, original.cells.get(0));
        queue.clear();
        SimpleAssert.that(queue.isEmpty(), "close or capture cancellation clears queued edits");
    }

    private static HologramState snapshot(String session, int firstX, int secondX) {
        net.minecraft.nbt.NBTTagCompound data = new net.minecraft.nbt.NBTTagCompound();
        data.setString("session", session);
        net.minecraft.nbt.NBTTagList cells = new net.minecraft.nbt.NBTTagList();
        for (int x : new int[] { firstX, secondX }) {
            net.minecraft.nbt.NBTTagCompound cell = new net.minecraft.nbt.NBTTagCompound();
            cell.setInteger("x", x);
            cell.setInteger("y", 64);
            cell.setInteger("z", 5);
            cells.appendTag(cell);
        }
        data.setTag("cells", cells);
        return new HologramState(data);
    }
}

package com.miaokatze.gtit.client.gui;

import java.util.function.IntBinaryOperator;

import com.miaokatze.gtit.gui.vm.NekoVendingLayout;

/** Uses the same inverse-parent callbacks as the live drag path; no client, GL or Gradle required. */
public final class NekoEditDragGeometryTest {

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        int checks = 0;
        NekoVendingLayout layout = new NekoVendingLayout();
        for (int[] display : new int[][] { { 1280, 720 }, { 1920, 1080 }, { 1024, 768 }, { 2560, 1440 },
            { 853, 480 } }) {
            for (int gui = 1; gui <= 4; gui++) {
                layout.resize(display[0] / gui, display[1] / gui);
                for (int[] parentOffset : new int[][] { { 0, 0 }, { 47, 31 }, { -11, 17 } }) {
                    final double scale = layout.scale;
                    final int parentX = parentOffset[0], parentY = parentOffset[1];
                    IntBinaryOperator inverseX = (x, y) -> (int) (x / scale - parentX);
                    IntBinaryOperator inverseY = (x, y) -> (int) (y / scale - parentY);
                    int panelX = 120, panelY = 80;
                    int mouseX = (int) ((parentX + panelX + 17) * scale);
                    int mouseY = (int) ((parentY + panelY + 5) * scale);
                    NekoEditDragGeometry drag = new NekoEditDragGeometry();
                    drag.begin(panelX, panelY, mouseX, mouseY, inverseX, inverseY);
                    int grabX = inverseX.applyAsInt(mouseX, mouseY) - panelX;
                    int grabY = inverseY.applyAsInt(mouseX, mouseY) - panelY;
                    drag.move(mouseX, mouseY, inverseX, inverseY);
                    check(drag.x() == panelX && drag.y() == panelY, "press cannot jump the panel");
                    for (int[] delta : new int[][] { { 40, 24 }, { -30, -10 }, { 0, 0 }, { 71, 53 } }) {
                        int x = mouseX + delta[0], y = mouseY + delta[1];
                        drag.move(x, y, inverseX, inverseY);
                        check(drag.x() + grabX == inverseX.applyAsInt(x, y), "x grab remains under mouse");
                        check(drag.y() + grabY == inverseY.applyAsInt(x, y), "y grab remains under mouse");
                        // Rendering now uses parent * translate(new local position), not captured handle * panel.
                        double drawnGrabX = (parentX + drag.x() + grabX) * scale;
                        double drawnGrabY = (parentY + drag.y() + grabY) * scale;
                        check(
                            Math.abs(drawnGrabX - x) <= 1.01 && Math.abs(drawnGrabY - y) <= 1.01,
                            "moving drawing and pointer agree within one integer MUI pixel");
                        checks += 3;
                    }
                    // The live code queries LocatedWidget(parent) anew, including during a stationary-pointer resize.
                    double resizedScale = Math.max(.2, scale * .75);
                    IntBinaryOperator resizedX = (x, y) -> (int) (x / resizedScale - parentX - 19);
                    IntBinaryOperator resizedY = (x, y) -> (int) (y / resizedScale - parentY - 7);
                    drag.move(mouseX, mouseY, resizedX, resizedY);
                    check(drag.x() + grabX == resizedX.applyAsInt(mouseX, mouseY), "resize uses current parent x");
                    check(drag.y() + grabY == resizedY.applyAsInt(mouseX, mouseY), "resize uses current parent y");
                    checks += 3;
                }
            }
        }
        System.out.println("NekoEditDragGeometryTest: " + checks + " drag/resize/scale checks passed");
    }
}

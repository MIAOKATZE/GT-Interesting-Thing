package com.miaokatze.gtit.lottery;

import java.util.function.IntBinaryOperator;

import com.miaokatze.gtit.gui.vm.NekoVendingLayout;

/** No GL / Minecraft runtime: exercises the same production screen-space hit projection as click + tooltip. */
public final class LotteryWheelGeometryTest {

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        int checks = 0;
        NekoVendingLayout layout = new NekoVendingLayout();
        for (int[] display : new int[][] { { 1280, 720 }, { 1920, 1080 }, { 1024, 768 }, { 2560, 1440 }, { 853, 480 },
            { 3840, 2160 }, { 1280, 720 } }) {
            for (int gui = 1; gui <= 4; gui++) {
                // Reuse one layout to test changing screen, GUI scale and returning to an old size.
                layout.resize(display[0] / gui, display[1] / gui);
                int width = layout.business.width - 64;
                int height = layout.business.height - 60 - 40;
                check(width >= 132 && height >= 132, "native circular wheel fits every supported scale");
                final double scale = layout.scale;
                final int originX = layout.business.x + 8;
                final int originY = layout.business.y + 60;
                IntBinaryOperator tx = (x, y) -> (int) Math.floor((originX + x) * scale);
                IntBinaryOperator ty = (x, y) -> (int) Math.floor((originY + y) * scale);
                for (int count = 1; count <= 10; count++) {
                    for (int slot = 0; slot < count; slot++) {
                        int[] p = LotteryWheelLayout.slotTopLeft(slot, count, width, height);
                        int radius = Math.min(width, height) / 2 - 18;
                        double distance = Math.hypot(p[0] + 12 - width / 2, p[1] + 12 - height / 2);
                        check(Math.abs(distance - radius) < 1, "prizes remain on the circular rim");
                        for (int other = 0; other < slot; other++) {
                            int[] q = LotteryWheelLayout.slotTopLeft(other, count, width, height);
                            check(
                                Math.abs(p[0] - q[0]) >= 24 || Math.abs(p[1] - q[1]) >= 24,
                                "circular prizes never overlap");
                        }
                        int x0 = tx.applyAsInt(p[0], p[1]), y0 = ty.applyAsInt(p[0], p[1]);
                        int x1 = tx.applyAsInt(p[0] + 24, p[1] + 24);
                        int y1 = ty.applyAsInt(p[0] + 24, p[1] + 24);
                        for (int[] point : new int[][] { { x0 + 1, y0 + 1 }, { (x0 + x1) / 2, (y0 + y1) / 2 },
                            { x1 - 1, y1 - 1 } }) {
                            check(
                                LotteryWheelLayout.hitSlot(point[0], point[1], count, width, height, tx, ty) == slot,
                                "drawn prize must be the clicked/tooltip prize after transform");
                            checks++;
                        }
                        int right = tx.applyAsInt(p[0] + 24, p[1] + 12);
                        int midY = ty.applyAsInt(p[0] + 24, p[1] + 12);
                        check(
                            LotteryWheelLayout.hitSlot(right, midY, count, width, height, tx, ty) == -1,
                            "right edge belongs to the gap, never the preceding prize");
                        checks++;
                    }
                    check(
                        LotteryWheelLayout
                            .hitSlot(tx.applyAsInt(-1, -1), ty.applyAsInt(-1, -1), count, width, height, tx, ty) == -1,
                        "outside wheel cannot edit a prize");
                }
                // The former 170px legacy page scale must not be part of either endpoint.
                check(LotteryWheelLayout.hitSlot(0, 0, 0, width, height, tx, ty) == -1, "empty pool is not editable");
                check(layout.editor.height >= 220, "pool fields end at 180 and actions start at 192 or later");
            }
        }
        System.out.println("LotteryWheelGeometryTest: " + checks + " production hit projections passed");
    }
}

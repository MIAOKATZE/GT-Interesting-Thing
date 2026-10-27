package com.miaokatze.gtit.lottery;

import java.util.function.IntBinaryOperator;

/** 轮盘沿离散环格顺时针取槽，避免拐角处奖品重叠。 */
public final class LotteryWheelLayout {

    /** Presentation protocol limit; agrees with LotteryPool.MAX_ENTRIES. */
    public static final int MAX_SLOTS = 10;
    public static final int SLOT_SIZE = 24;

    private LotteryWheelLayout() {}

    public static int[] slotTopLeft(int index, int count, int width, int height) {
        if (count < 1 || count > MAX_SLOTS || index < 0 || index >= count) {
            throw new IllegalArgumentException("Invalid wheel slot");
        }
        int cols = count <= 1 ? 1 : count <= 4 ? 2 : count <= 6 ? 3 : 4;
        int rows = count <= 1 ? 1 : count <= 8 ? 2 : 3;
        int w = cols - 1;
        int h = rows - 1;
        int ringCells = 2 * (w + h);
        int d = ringCells == 0 ? 0 : index * ringCells / count;
        int x, y;
        if (d < w) {
            x = d;
            y = 0;
        } else if (d < w + h) {
            x = w;
            y = d - w;
        } else if (d < 2 * w + h) {
            x = 2 * w + h - d;
            y = h;
        } else {
            x = 0;
            y = ringCells - d;
        }
        return new int[] { (width - (w * 28 + 24)) / 2 + x * 28, (height - (h * 28 + 24)) / 2 + y * 28 };
    }

    /**
     * Hit tests the exact rectangles sent to drawing through the current full MUI matrix.
     * Transform callbacks are supplied by LocatedWidget, so screen and GUI scale are applied once.
     * Half-open bounds preserve the gap between prizes; tooltip and clicks call this same method.
     */
    public static int hitSlot(int mouseX, int mouseY, int count, int width, int height, IntBinaryOperator transformX,
        IntBinaryOperator transformY) {
        if (count < 1 || count > MAX_SLOTS || width <= 0 || height <= 0) return -1;
        for (int i = 0; i < count; i++) {
            int[] p = slotTopLeft(i, count, width, height);
            int x0 = transformX.applyAsInt(p[0], p[1]);
            int y0 = transformY.applyAsInt(p[0], p[1]);
            int x1 = transformX.applyAsInt(p[0] + SLOT_SIZE, p[1] + SLOT_SIZE);
            int y1 = transformY.applyAsInt(p[0] + SLOT_SIZE, p[1] + SLOT_SIZE);
            if (mouseX >= Math.min(x0, x1) && mouseX < Math.max(x0, x1)
                && mouseY >= Math.min(y0, y1)
                && mouseY < Math.max(y0, y1)) return i;
        }
        return -1;
    }
}

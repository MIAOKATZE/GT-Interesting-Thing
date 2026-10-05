package com.miaokatze.gtit.lottery;

/** 轮盘沿离散环格顺时针取槽，避免拐角处奖品重叠。 */
public final class LotteryWheelLayout {

    private LotteryWheelLayout() {}

    public static int[] slotTopLeft(int index, int count, int width, int height) {
        if (count < 1 || count > LotteryPool.MAX_ENTRIES || index < 0 || index >= count) {
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
}

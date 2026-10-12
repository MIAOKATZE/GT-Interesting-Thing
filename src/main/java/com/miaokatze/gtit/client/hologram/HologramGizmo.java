package com.miaokatze.gtit.client.hologram;

/** Blender-style axis handles with camera-aware screen hit testing and integer block snapping. */
public final class HologramGizmo {

    public static final int[] COLORS = { 0xffed6767, 0xff79dc8c, 0xff6f9ef8 };
    private final double[][] axes = new double[3][2];
    private double x, y;
    private int dragging = -1, startX, startY;
    private int delta;

    public void project(double x, double y, double yaw, double pitch) {
        this.x = x;
        this.y = y;
        axes[0][0] = Math.cos(yaw);
        axes[0][1] = Math.sin(yaw) * Math.sin(pitch);
        axes[1][0] = 0;
        axes[1][1] = -Math.cos(pitch);
        axes[2][0] = -Math.sin(yaw);
        axes[2][1] = Math.cos(yaw) * Math.sin(pitch);
    }

    public double[] start() {
        return new double[] { x, y };
    }

    public double[] end(int axis) {
        return new double[] { x + axes[axis][0] * 42, y + axes[axis][1] * 42 };
    }

    public int pick(int mx, int my) {
        int result = -1;
        double best = 6;
        for (int axis = 0; axis < 3; axis++) {
            double[] end = end(axis);
            double distance = distance(mx, my, x, y, end[0], end[1]);
            double length = Math.hypot(end[0] - x, end[1] - y);
            if (length >= 8 && Math.hypot(mx - x, my - y) >= 9 && distance < best) {
                result = axis;
                best = distance;
            }
        }
        return result;
    }

    public static double distance(double px, double py, double ax, double ay, double bx, double by) {
        double dx = bx - ax, dy = by - ay, length = dx * dx + dy * dy;
        if (length == 0) return Math.hypot(px - ax, py - ay);
        double t = Math.max(0, Math.min(1, ((px - ax) * dx + (py - ay) * dy) / length));
        return Math.hypot(px - ax - t * dx, py - ay - t * dy);
    }

    public void begin(int axis, int mx, int my) {
        dragging = axis;
        startX = mx;
        startY = my;
        delta = 0;
    }

    public int update(int mx, int my) {
        if (dragging < 0) return 0;
        double dx = axes[dragging][0], dy = axes[dragging][1];
        delta = (int) Math.round(((mx - startX) * dx + (my - startY) * dy) / Math.max(.2, dx * dx + dy * dy) / 12);
        return delta;
    }

    public int axis() {
        return dragging;
    }

    public int delta() {
        return delta;
    }

    public void cancel() {
        dragging = -1;
        delta = 0;
    }
}

package com.miaokatze.gtit.client.gui;

import java.util.function.IntBinaryOperator;

/** Parent-local drag position. Only the current parent MUI matrix maps screen input into this space. */
public final class NekoEditDragGeometry {

    private int grabX, grabY;
    private int x, y;

    public void begin(int panelX, int panelY, int screenMouseX, int screenMouseY, IntBinaryOperator inverseX,
        IntBinaryOperator inverseY) {
        x = panelX;
        y = panelY;
        grabX = inverseX.applyAsInt(screenMouseX, screenMouseY) - panelX;
        grabY = inverseY.applyAsInt(screenMouseX, screenMouseY) - panelY;
    }

    public void move(int screenMouseX, int screenMouseY, IntBinaryOperator inverseX, IntBinaryOperator inverseY) {
        x = inverseX.applyAsInt(screenMouseX, screenMouseY) - grabX;
        y = inverseY.applyAsInt(screenMouseX, screenMouseY) - grabY;
    }

    public int x() {
        return x;
    }

    public int y() {
        return y;
    }
}

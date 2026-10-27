package com.miaokatze.gtit.client.gui;

/** Pure local geometry for compact cards. Framework viewport transforms own drawing and hit testing. */
public final class NekoTradeCardLayout {

    public static final int TILE_HEIGHT = 46, TILE_ROW_HEIGHT = 50;
    public static final int LIST_HEIGHT = 38, LIST_ROW_HEIGHT = 40;
    public static final int TOKEN_SIZE = 21, TOKEN_PITCH = 23;
    public static final int OVERFLOW_WIDTH = 14, OVERFLOW_GAP = 2;

    /** Reserve overflow before allocating icons; counts include currency and material tokens together. */
    public static final class TokenStrip {

        public final int visible, hidden, badgeX, badgeWidth;

        public TokenStrip(int available, int total) {
            available = Math.max(0, available);
            total = Math.max(0, total);
            int capacity = (available + TOKEN_PITCH - TOKEN_SIZE) / TOKEN_PITCH;
            if (total <= capacity) {
                visible = total;
                hidden = 0;
                badgeX = badgeWidth = 0;
            } else {
                badgeWidth = Math.min(OVERFLOW_WIDTH, available);
                int iconBudget = Math.max(0, available - badgeWidth - OVERFLOW_GAP);
                visible = Math.min(total, (iconBudget + TOKEN_PITCH - TOKEN_SIZE) / TOKEN_PITCH);
                hidden = total - visible;
                badgeX = available - badgeWidth;
            }
        }
    }

    public final int center, rowY, inputWidth, outputX, outputWidth, statusX, statusY, statusWidth;

    public NekoTradeCardLayout(int width, int height, boolean list, int requirements) {
        center = requirements >= 3 ? width * 3 / 5 : width / 2;
        rowY = list ? 14 : 13;
        inputWidth = Math.max(0, center - 14);
        outputX = center + 9;
        outputWidth = Math.max(0, width - center - 16);
        statusX = list ? width - 110 : 2;
        statusY = list ? 2 : height - 12;
        statusWidth = list ? 108 : width - 4;
    }
}

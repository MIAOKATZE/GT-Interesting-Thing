package com.miaokatze.gtit.client.gui;

/** Pure density/overflow contract; not a substitute for native GL and mouse validation. */
public final class NekoTradeCardLayoutTest {

    public static void main(String[] args) {
        int count = 0;
        for (boolean list : new boolean[] { false, true }) {
            for (int width = list ? 280 : 129; width <= 1000; width += 7) {
                for (int requirements = 0; requirements <= 18; requirements++) {
                    int height = list ? NekoTradeCardLayout.LIST_HEIGHT : NekoTradeCardLayout.TILE_HEIGHT;
                    NekoTradeCardLayout g = new NekoTradeCardLayout(width, height, list, requirements);
                    check(g.statusX >= 0 && g.statusX + g.statusWidth <= width, "status inside card");
                    check(g.statusY >= 0 && g.statusY + 10 <= height, "status height inside card");
                    check(g.outputX + g.outputWidth <= width, "output inside card");
                    check(g.inputWidth + 6 < g.center - 4, "requirements do not hit arrow");
                    check(
                        g.rowY + NekoTradeCardLayout.TOKEN_SIZE <= (list ? height : g.statusY),
                        "status never hides cost icons");
                    check(
                        g.inputWidth / NekoTradeCardLayout.TOKEN_PITCH >= Math.min(width < 145 ? 2 : 3, requirements),
                        "compact cards retain two wallet coins; wider cards also retain material");
                    check(g.outputWidth >= NekoTradeCardLayout.TOKEN_PITCH, "at least one output stays visible");
                    for (int tokens = 0; tokens <= 34; tokens++) {
                        NekoTradeCardLayout.TokenStrip strip = new NekoTradeCardLayout.TokenStrip(g.inputWidth, tokens);
                        check(strip.visible + strip.hidden == tokens, "no requirement silently disappears");
                        int iconEnd = strip.visible == 0 ? 0
                            : (strip.visible - 1) * NekoTradeCardLayout.TOKEN_PITCH + NekoTradeCardLayout.TOKEN_SIZE;
                        check(iconEnd <= g.inputWidth, "last icon is bounded");
                        if (strip.hidden > 0) {
                            check(strip.badgeX + strip.badgeWidth <= g.inputWidth, "overflow badge is bounded");
                            check(iconEnd <= strip.badgeX, "badge never overlaps an icon");
                        }
                        if (requirements >= 3 && tokens >= 2) check(
                            strip.visible >= 2,
                            "both priority wallet currencies fit even with material overflow");
                    }
                    count++;
                }
            }
        }
        check(NekoTradeCardLayout.columns(407) == 3, "854 logical canvas uses three compact cards");
        check(NekoTradeCardLayout.columns(326) == 2, "768 logical canvas retains two readable cards");
        for (int width = 129; width <= 144; width++) {
            NekoTradeCardLayout compact = new NekoTradeCardLayout(width, NekoTradeCardLayout.TILE_HEIGHT, false, 3);
            NekoTradeCardLayout.TokenStrip costs = new NekoTradeCardLayout.TokenStrip(compact.inputWidth, 18);
            check(costs.visible >= 2 && costs.hidden > 0, "both coin costs remain above explicit material overflow");
            check(compact.outputWidth >= NekoTradeCardLayout.TOKEN_SIZE, "compact card reserves a real output icon");
        }
        check(NekoTradeCardLayout.TILE_HEIGHT < 58, "denser than feedback screenshot");
        check(NekoTradeCardLayout.LIST_HEIGHT < NekoTradeCardLayout.TILE_HEIGHT, "list remains compact");
        System.out.println("NekoTradeCardLayoutTest: " + count + " density and overlap cases passed");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}

package com.miaokatze.gtit.gui.pocket;

import com.cleanroommc.modularui.drawable.GuiDraw;

/**
 * ★R100 片 F：三组格件（{@code NekoFilterSlot} / {@code NekoPocketFluidSlot} /
 * {@code NekoEssenceGhostCell}）共用的「属性角标绘制体」——原三份逐字同形的
 * {@code private void drawBadge(String, float, int)} 收拢于此（行为零变化，逐字迁入）。
 * <p>
 * 几何（{@link PocketGhostRequest#badgeLeftX()}）、缩放（{@link PocketGhostRequest#CAP_READOUT_SCALE}）
 * 与「该不该画」的读数依旧全部取自 {@link PocketGhostRequest}（三组格件共用一份）；
 * 本类只负责"画"这一步。★调用方语义：文本为空 ⇒ 一条像素都不画（
 * {@code ghost_badge_readouts_are_empty_when_not_applicable} 钉的空文本早退随迁到本类单点）。
 */
public final class PocketBadgeDrawer {

    private PocketBadgeDrawer() {}

    /** 一个角标的绘制体（左对齐 + 内缩；★空文本 = 不画，三组格件同形）。 */
    public static void drawBadge(String text, float top, int color) {
        if (text == null || text.isEmpty()) {
            return;
        }
        GuiDraw.drawText(text, PocketGhostRequest.badgeLeftX(), top, PocketGhostRequest.CAP_READOUT_SCALE, color, true);
    }
}

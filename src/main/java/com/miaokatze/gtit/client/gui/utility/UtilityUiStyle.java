package com.miaokatze.gtit.client.gui.utility;

import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.drawable.GuiTextures;
import com.cleanroommc.modularui.drawable.Rectangle;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widget.ScrollWidget;
import com.cleanroommc.modularui.widget.scroll.HorizontalScrollData;
import com.cleanroommc.modularui.widget.scroll.VerticalScrollData;
import com.cleanroommc.modularui.widget.sizer.Unit;
import com.cleanroommc.modularui.widgets.ButtonWidget;

/** Styling for standalone utility windows. Machine and pocket themes remain independent. */
public final class UtilityUiStyle {

    public static final IDrawable PANEL = GuiTextures.MC_BACKGROUND;
    public static final IDrawable INSET = new Rectangle().color(0x18000000);
    public static final IDrawable INPUT = GuiTextures.DISPLAY;
    public static final IDrawable SLOT = GuiTextures.SLOT_ITEM;
    public static final int TEXT = 0xFF30343B;
    public static final int MUTED = 0xFF626B75;
    public static final int ACCENT = 0xFF315D87;

    private UtilityUiStyle() {}

    public static ModularPanel panel(String name, int width, int height) {
        return new ModularPanel(name).size(width, height)
            .background(PANEL);
    }

    public static ParentWidget<?> section(int x, int y, int width, int height) {
        return new ParentWidget<>().pos(x, y)
            .size(width, height)
            .background(INSET);
    }

    public static ModularPanel scrollable(String name, int width, int height, IWidget content) {
        return scrollable(panel(name, width, height), width, height, content);
    }

    /** Keep a stable server/client widget tree while a small GUI viewport scrolls the full form. */
    public static ModularPanel scrollable(ModularPanel panel, int width, int height, IWidget content) {
        panel.width(
            () -> viewportExtent(
                width,
                panel.getContext()
                    .getScreenArea().width),
            Unit.Measure.PIXEL)
            .height(
                () -> viewportExtent(
                    height,
                    panel.getContext()
                        .getScreenArea().height),
                Unit.Measure.PIXEL);
        UtilityViewport viewport = new UtilityViewport(width, height);
        viewport.getScrollArea()
            .setScrollDataX(new HorizontalScrollData(false, 4));
        viewport.name("utility_viewport")
            .pos(4, 4)
            .widthRelOffset(1f, -8)
            .heightRelOffset(1f, -8)
            .child(content);
        panel.child(viewport);
        panel.child(
            ButtonWidget.panelCloseButton()
                .right(7)
                .top(7));
        return panel;
    }

    public static int viewportExtent(int contentExtent, int screenExtent) {
        return Math.max(1, Math.min(contentExtent + 12, screenExtent - 12));
    }

    private static final class UtilityViewport extends ScrollWidget<UtilityViewport> {

        private final int contentWidth;
        private final int contentHeight;

        UtilityViewport(int contentWidth, int contentHeight) {
            super(new VerticalScrollData(false, 4));
            this.contentWidth = contentWidth;
            this.contentHeight = contentHeight;
        }

        @Override
        public void postResize() {
            super.postResize();
            // A 4px reserve fits the other scrollbar without causing an exact-fit phantom bar.
            boolean fits = contentWidth <= getArea().width && contentHeight <= getArea().height;
            getScrollArea().getScrollX()
                .setScrollSize(fits ? 0 : contentWidth);
            getScrollArea().getScrollY()
                .setScrollSize(fits ? 0 : contentHeight);
            getScrollArea().getScrollX()
                .clamp(getScrollArea());
            getScrollArea().getScrollY()
                .clamp(getScrollArea());
        }
    }
}

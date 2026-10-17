package com.miaokatze.gtit.client.gui.utility;

import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.drawable.Rectangle;
import com.cleanroommc.modularui.drawable.UITexture;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widget.ScrollWidget;
import com.cleanroommc.modularui.widget.WidgetTree;
import com.cleanroommc.modularui.widget.scroll.HorizontalScrollData;
import com.cleanroommc.modularui.widget.scroll.VerticalScrollData;
import com.cleanroommc.modularui.widget.sizer.Unit;
import com.cleanroommc.modularui.widgets.ListWidget;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.miaokatze.gtit.gui.pocket.PocketGuiTextures;

/** Styling for standalone utility windows. Machine and pocket themes remain independent. */
public final class UtilityUiStyle {

    public static final IDrawable PANEL = (context, x, y, width, height, theme) -> {
        PocketGuiTextures.CLOTH.draw(context, x, y, width, height, theme);
        PocketGuiTextures.PANEL.draw(context, x, y, width, height, theme);
        PocketGuiTextures.CORNER.draw(context, x, y, 14, 14, theme);
        PocketGuiTextures.CORNER.draw(context, x + width - 14, y, 14, 14, theme);
        PocketGuiTextures.CORNER.draw(context, x, y + height - 14, 14, 14, theme);
        PocketGuiTextures.CORNER.draw(context, x + width - 14, y + height - 14, 14, 14, theme);
    };
    public static final IDrawable INSET = IDrawable.of(
        PocketGuiTextures.CLOTH,
        new Rectangle().color(0x18000000),
        new Rectangle().color(0xFF8C643B)
            .hollow(1));
    public static final IDrawable INPUT = PocketGuiTextures.COIN_BAR;
    public static final IDrawable SLOT = PocketGuiTextures.SLOT;
    public static final IDrawable BUTTON = PocketGuiTextures.BUTTON;
    public static final IDrawable BUTTON_PRESSED = PocketGuiTextures.BUTTON_PRESSED;
    public static final IDrawable BUTTON_HOVER = texture("button_hover");
    public static final IDrawable BUTTON_SELECTED_HOVER = texture("button_selected_hover");
    public static final IDrawable BUTTON_DISABLED = texture("button_disabled");
    public static final IDrawable SCROLLBAR = UITexture.builder()
        .location("gtit", "gui/pocket/POCKET_C2_scrollbar")
        .imageSize(12, 12)
        .adaptable(1)
        .nonOpaque()
        .name("gtit_utility_c2_scrollbar")
        .build();
    public static final int TEXT = 0xFF33271B;
    public static final int MUTED = 0xFF68533C;
    public static final int ACCENT = 0xFF684017;
    public static final int BUTTON_TEXT = 0xFFFFE7B9;

    private static UITexture texture(String name) {
        return UITexture.builder()
            .location("gtit", "gui/utility/" + name)
            .imageSize(88, 18)
            .adaptable(4)
            .nonOpaque()
            .name("gtit_utility_c2_" + name)
            .build();
    }

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
        styleContent(content);
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
        viewport.getScrollArea()
            .getScrollX()
            .texture(SCROLLBAR);
        viewport.getScrollArea()
            .getScrollY()
            .texture(SCROLLBAR);
        viewport.name("utility_viewport")
            .pos(4, 4)
            .widthRelOffset(1f, -8)
            .heightRelOffset(1f, -8)
            .child(content);
        panel.child(viewport);
        panel.child(
            closeButton(panel).right(7)
                .top(7));
        return panel;
    }

    public static UtilityControls.ActionButton closeButton(ModularPanel panel) {
        return new UtilityControls.ActionButton().size(12)
            .overlay(com.cleanroommc.modularui.api.drawable.IKey.str("×"))
            .onMousePressed(mouse -> {
                if (mouse != 0 && mouse != 1) return false;
                panel.closeIfOpen();
                return true;
            });
    }

    public static void styleContent(IWidget content) {
        WidgetTree.foreachChild(content, widget -> {
            if (widget instanceof TextWidget<?>text && text.getColor() == null) {
                text.color(TEXT)
                    .shadow(false);
            }
            if (widget instanceof ListWidget<?, ?>list) {
                VerticalScrollData scroll = new VerticalScrollData(false, 4);
                scroll.texture(SCROLLBAR);
                list.getScrollArea()
                    .setScrollDataY(scroll);
            }
            if (widget instanceof com.cleanroommc.modularui.widgets.slot.ItemSlot slot) slot.background(SLOT);
            return true;
        }, true);
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

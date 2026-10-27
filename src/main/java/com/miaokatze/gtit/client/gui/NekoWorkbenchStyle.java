package com.miaokatze.gtit.client.gui;

import com.cleanroommc.modularui.api.widget.ITooltip;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widget.WidgetTree;
import com.cleanroommc.modularui.widget.scroll.VerticalScrollData;
import com.cleanroommc.modularui.widgets.ListWidget;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.cleanroommc.modularui.widgets.slot.ItemSlot;

/** Opt-in kraft styling for standalone managers; pocket and unrelated utility windows are untouched. */
public final class NekoWorkbenchStyle {

    private NekoWorkbenchStyle() {}

    public static ParentWidget<?> section(int x, int y, int width, int height) {
        return new ParentWidget<>().pos(x, y)
            .size(width, height)
            .background(NekoVendingTheme.PANEL)
            .excludeAreaInRecipeViewer();
    }

    public static void style(IWidget content) {
        WidgetTree.foreachChild(content, widget -> {
            if (widget instanceof TextWidget<?>text && text.getColor() == null) text.color(NekoVendingTheme.INK)
                .shadow(false);
            if (widget instanceof ITooltip<?>tooltip && tooltip.getTooltip() != null)
                NekoVendingTheme.tooltip(tooltip.getTooltip());
            if (widget instanceof ListWidget<?, ?>list) {
                VerticalScrollData scroll = new VerticalScrollData(false, 4);
                scroll.texture(NekoVendingTheme.BUTTON);
                list.getScrollArea()
                    .setScrollDataY(scroll);
            }
            if (widget instanceof ItemSlot slot) slot.background(NekoVendingTheme.SLOT);
            return true;
        }, true);
    }
}

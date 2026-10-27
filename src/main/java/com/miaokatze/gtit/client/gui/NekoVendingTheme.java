package com.miaokatze.gtit.client.gui;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.drawable.DrawableStack;
import com.cleanroommc.modularui.drawable.GuiDraw;
import com.cleanroommc.modularui.drawable.UITexture;
import com.cleanroommc.modularui.widgets.ButtonWidget;

/** Local copies of the approved GTMB kraft assets. No runtime dependency on GTMB. */
public final class NekoVendingTheme {

    public static final int INK = 0xff49351f, MUTED = 0xff816945, GOLD = 0xffbd8d36, CYAN = 0xff57d7dd;
    public static final UITexture PAPER = UITexture.builder()
        .location("gtit", "gui/vending/kraft/paper")
        .tiled(32, 32)
        .nonOpaque()
        .build()
        .withColorOverride(0xffffffff);
    public static final UITexture FRAME = texture("panel", 16, 3);
    public static final IDrawable PANEL = new DrawableStack(PAPER, FRAME);
    public static final UITexture INPUT = texture("input", 12, 2), SLOT = texture("slot", 12, 2);
    public static final UITexture BUTTON = texture("button", 12, 2), HOVER = texture("button-hover", 12, 2),
        PRESSED = texture("button-pressed", 12, 2), SELECTED_HOVER = texture("button-selected-hover", 12, 2),
        DISABLED = texture("button-disabled", 12, 2);

    private NekoVendingTheme() {}

    private static UITexture texture(String name, int size, int border) {
        return UITexture.builder()
            .location("gtit", "gui/vending/kraft/" + name)
            .imageSize(size, size)
            .adaptable(border)
            .nonOpaque()
            .build()
            .withColorOverride(0xffffffff);
    }

    public static IDrawable face(BooleanSupplier selected, BooleanSupplier active, boolean hover) {
        return (context, x, y, width, height, theme) -> {
            UITexture texture = !active.getAsBoolean() ? DISABLED
                : selected.getAsBoolean() ? hover ? SELECTED_HOVER : PRESSED : hover ? HOVER : BUTTON;
            texture.draw(context, x, y, width, height, theme);
        };
    }

    public static ButtonWidget<?> button(Supplier<String> text, Runnable action) {
        return button(text, action, () -> false, () -> true);
    }

    public static ButtonWidget<?> button(Supplier<String> text, Runnable action, BooleanSupplier selected,
        BooleanSupplier active) {
        return new ButtonWidget<>().height(20)
            .background(face(selected, active, false))
            .hoverBackground(face(selected, active, true))
            .overlay(
                IKey.dynamic(text)
                    .color(() -> active.getAsBoolean() ? 0xffffe7b9 : 0xffdbccb1)
                    .shadow(true))
            .onMousePressed(mouse -> {
                if (mouse != 0) return false;
                if (active.getAsBoolean()) action.run();
                return true;
            });
    }

    public static IDrawable row(BooleanSupplier selected) {
        return row(selected, false);
    }

    public static IDrawable row(BooleanSupplier selected, boolean hover) {
        return (context, x, y, width, height, theme) -> {
            GuiDraw
                .drawRect(x, y, width, height, selected.getAsBoolean() ? 0xffe3d3af : hover ? 0xfff8edce : 0xfff0e5c8);
            GuiDraw.drawRect(x, y + height - 1, width, 1, 0xffbda87f);
            if (selected.getAsBoolean()) GuiDraw.drawRect(x, y, 2, height, GOLD);
        };
    }
}

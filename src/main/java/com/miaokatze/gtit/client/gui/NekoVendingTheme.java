package com.miaokatze.gtit.client.gui;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.drawable.GuiDraw;
import com.cleanroommc.modularui.widgets.ButtonWidget;

/** Final approved soft-kraft palette, shared by the native vending surfaces (no global theme mutation). */
public final class NekoVendingTheme {

    public static final int INK = 0xff483f2f, MUTED = 0xff6a5e48, GOLD = 0xffaa8138, CYAN = 0xff00ffff;
    public static final int TOOLTIP_INK = 0xffd1d4b7, TOOLTIP_TITLE = 0xfff1dfb0, TOOLTIP_MUTED = 0xff99ab94;
    public static final int GREEN = 0xff61704d, DANGER = 0xff994f3c, LIGHT = 0xfffff2d3;
    public static final IDrawable PAPER = surface(0xffe7dbc0, 0xffc6b796);
    public static final IDrawable FRAME = surface(0xffe9dec1, 0xffc1b18d);
    public static final IDrawable PANEL = FRAME;
    public static final IDrawable INPUT = inset(0xffeee5ce, 0xffc3b28e);
    public static final IDrawable SLOT = inset(0xffdcd0b1, 0xffb8a785);
    public static final IDrawable BUTTON = surface(0xffddd0ae, 0xffbba984);
    public static final IDrawable HOVER = surface(0xffeee1bf, 0xffb29d75);
    public static final IDrawable PRESSED = surface(0xff858d67, 0xff7f8661);
    public static final IDrawable SELECTED_HOVER = surface(0xff949c77, 0xff7f8661);
    public static final IDrawable DISABLED = surface(0xffd9ceb3, 0xffc7ba9b);
    public static final IDrawable WALLET = surface(0xffeee0bc, 0xffc9b58a);
    public static final IDrawable SHIMMER_WALLET = surface(0xffe8dfe6, 0xffc5b4c0);

    private NekoVendingTheme() {}

    /** Small stepped corners and a restrained pixel bevel, rather than stretching noisy paper textures. */
    public static IDrawable surface(int fill, int border) {
        return (context, x, y, width, height, theme) -> {
            GuiDraw.drawRect(x + 1, y, Math.max(0, width - 2), height, border);
            GuiDraw.drawRect(x, y + 1, width, Math.max(0, height - 2), border);
            GuiDraw.drawRect(x + 1, y + 1, Math.max(0, width - 2), Math.max(0, height - 2), fill);
            GuiDraw.drawRect(x + 2, y + 1, Math.max(0, width - 4), 1, 0x50ffffff);
            GuiDraw.drawRect(x + 1, y + 2, 1, Math.max(0, height - 4), 0x30ffffff);
            GuiDraw.drawRect(x + 2, y + height - 2, Math.max(0, width - 4), 1, 0x205a4526);
        };
    }

    private static IDrawable inset(int fill, int border) {
        return (context, x, y, width, height, theme) -> {
            GuiDraw.drawRect(x, y, width, height, border);
            GuiDraw.drawRect(x + 1, y + 1, Math.max(0, width - 2), Math.max(0, height - 2), fill);
            GuiDraw.drawRect(x + 1, y + 1, Math.max(0, width - 2), 1, 0x305a4526);
            GuiDraw.drawRect(x + 1, y + height - 1, Math.max(0, width - 2), 1, 0xfff8edd3);
        };
    }

    public static IDrawable face(BooleanSupplier selected, BooleanSupplier active, boolean hover) {
        return (context, x, y, width, height, theme) -> {
            IDrawable face = !active.getAsBoolean() ? DISABLED
                : selected.getAsBoolean() ? hover ? SELECTED_HOVER : PRESSED : hover ? HOVER : BUTTON;
            face.draw(context, x, y, width, height, theme);
        };
    }

    /** Preserve the caller's click/sync handler while replacing vanilla blue button surfaces. */
    public static <B extends ButtonWidget<B>> B textButton(B button) {
        return button.disableThemeBackground(true)
            .disableHoverThemeBackground(true)
            .background(BUTTON)
            .hoverBackground(HOVER);
    }

    public static ButtonWidget<?> button(Supplier<String> text, Runnable action) {
        return button(text, action, () -> false, () -> true);
    }

    public static ButtonWidget<?> button(Supplier<String> text, Runnable action, BooleanSupplier selected,
        BooleanSupplier active) {
        return new ButtonWidget<>().height(20)
            .disableThemeBackground(true)
            .disableHoverThemeBackground(true)
            .background(face(selected, active, false))
            .hoverBackground(face(selected, active, true))
            .overlay(
                IKey.dynamic(text)
                    .color(() -> !active.getAsBoolean() ? 0xff9a8c72 : selected.getAsBoolean() ? LIGHT : INK)
                    .shadow(false))
            .onMousePressed(mouse -> {
                if (mouse != 0) return false;
                if (active.getAsBoolean()) action.run();
                return true;
            });
    }

    public static com.cleanroommc.modularui.screen.RichTooltip tooltip(
        com.cleanroommc.modularui.screen.RichTooltip tooltip) {
        // Builders are mirrored on the dedicated server; register client render events only client-side.
        return cpw.mods.fml.common.FMLCommonHandler.instance()
            .getEffectiveSide()
            .isClient() ? NekoVendingTooltip.apply(tooltip) : tooltip;
    }

    public static IDrawable row(BooleanSupplier selected) {
        return row(selected, false);
    }

    public static IDrawable row(BooleanSupplier selected, boolean hover) {
        IDrawable plain = surface(0xfff0e6cb, 0xffc4b696);
        IDrawable hovered = surface(0xfff6edd6, 0xffb8ad89);
        IDrawable selection = surface(0xffe7e3c5, 0xff8e9b73);
        return (context, x, y, width, height, theme) -> {
            (selected.getAsBoolean() ? selection : hover ? hovered : plain).draw(context, x, y, width, height, theme);
            if (selected.getAsBoolean()) GuiDraw.drawRect(x + 1, y + 2, 2, height - 4, 0xff8e9b73);
        };
    }
}

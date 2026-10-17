package com.miaokatze.gtit.terminal;

import java.util.function.BooleanSupplier;

import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.drawable.GuiTextures;
import com.cleanroommc.modularui.theme.WidgetTheme;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;
import com.cleanroommc.modularui.widgets.ButtonWidget;

/** Native button states for terminal actions; unavailable actions stay visible and keep their tooltip. */
final class TerminalButton extends ButtonWidget<TerminalButton> {

    private BooleanSupplier available = () -> true;
    private BooleanSupplier selected = () -> false;

    TerminalButton available(BooleanSupplier available) {
        this.available = available;
        return this;
    }

    TerminalButton selected(BooleanSupplier selected) {
        this.selected = selected;
        return this;
    }

    @Override
    public IDrawable getThemeBackground(WidgetThemeEntry<?> widgetTheme, WidgetTheme activeTheme) {
        if (!available.getAsBoolean()) return GuiTextures.MC_BUTTON_DISABLED;
        if (selected.getAsBoolean()) {
            return isHovering() ? GuiTextures.MC_BUTTON_HOVERED_PRESSED : GuiTextures.MC_BUTTON_PRESSED;
        }
        return super.getThemeBackground(widgetTheme, activeTheme);
    }

    @Override
    public Result onMousePressed(int mouseButton) {
        return available.getAsBoolean() ? super.onMousePressed(mouseButton) : Result.STOP;
    }

    @Override
    public Result onMouseTapped(int mouseButton) {
        return available.getAsBoolean() ? super.onMouseTapped(mouseButton) : Result.STOP;
    }
}

package com.miaokatze.gtit.client.gui.utility;

import java.util.function.BooleanSupplier;

import com.cleanroommc.modularui.api.ITheme;
import com.cleanroommc.modularui.api.IThemeApi;
import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.theme.SelectableTheme;
import com.cleanroommc.modularui.theme.TextFieldTheme;
import com.cleanroommc.modularui.theme.WidgetTheme;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.ItemDisplayWidget;
import com.cleanroommc.modularui.widgets.PageButton;
import com.cleanroommc.modularui.widgets.PagedWidget;
import com.cleanroommc.modularui.widgets.ToggleButton;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;

/** Scoped C2 widget entries: no global theme registration or machine theme changes. */
public final class UtilityControls {

    private static final WidgetThemeEntry<WidgetTheme> BUTTON_THEME = new WidgetThemeEntry<>(
        IThemeApi.BUTTON,
        WidgetTheme.whiteTextShadow(18, 18, UtilityUiStyle.BUTTON),
        WidgetTheme.whiteTextShadow(18, 18, UtilityUiStyle.BUTTON_HOVER));
    private static final WidgetThemeEntry<SelectableTheme> SELECTABLE_THEME = new WidgetThemeEntry<>(
        IThemeApi.TOGGLE_BUTTON,
        SelectableTheme.whiteTextShadow(18, 18, UtilityUiStyle.BUTTON, UtilityUiStyle.BUTTON_PRESSED),
        SelectableTheme.whiteTextShadow(18, 18, UtilityUiStyle.BUTTON_HOVER, UtilityUiStyle.BUTTON_SELECTED_HOVER));
    private static final WidgetThemeEntry<TextFieldTheme> FIELD_THEME = new WidgetThemeEntry<>(
        IThemeApi.TEXT_FIELD,
        new TextFieldTheme(
            56,
            18,
            UtilityUiStyle.INPUT,
            0xFFFFFFFF,
            UtilityUiStyle.TEXT,
            false,
            0xFFFFFFFF,
            0x806D8FAD,
            UtilityUiStyle.MUTED));

    private UtilityControls() {}

    public static class Button<W extends Button<W>> extends ButtonWidget<W> {

        private BooleanSupplier available = () -> true;
        private BooleanSupplier selected = () -> false;
        private boolean pressed;

        public W available(BooleanSupplier available) {
            this.available = available;
            return getThis();
        }

        public W selected(BooleanSupplier selected) {
            this.selected = selected;
            return getThis();
        }

        @Override
        public WidgetThemeEntry<?> getWidgetThemeInternal(ITheme theme) {
            return BUTTON_THEME;
        }

        @Override
        public IDrawable getThemeBackground(WidgetThemeEntry<?> entry, WidgetTheme theme) {
            if (!available.getAsBoolean()) return UtilityUiStyle.BUTTON_DISABLED;
            if (pressed || selected.getAsBoolean())
                return isHovering() ? UtilityUiStyle.BUTTON_SELECTED_HOVER : UtilityUiStyle.BUTTON_PRESSED;
            return isHovering() ? UtilityUiStyle.BUTTON_HOVER : UtilityUiStyle.BUTTON;
        }

        @Override
        public Result onMousePressed(int button) {
            if (!available.getAsBoolean()) return Result.STOP;
            pressed = button == 0;
            return super.onMousePressed(button);
        }

        @Override
        public boolean onMouseRelease(int button) {
            pressed = false;
            return super.onMouseRelease(button);
        }

        @Override
        public Result onMouseTapped(int button) {
            return available.getAsBoolean() ? super.onMouseTapped(button) : Result.STOP;
        }
    }

    public static final class ActionButton extends Button<ActionButton> {
    }

    public static final class Page extends PageButton {

        public Page(int index, PagedWidget.Controller controller) {
            super(index, controller);
        }

        @Override
        public WidgetThemeEntry<?> getWidgetThemeInternal(ITheme theme) {
            return SELECTABLE_THEME;
        }

        @Override
        public IDrawable getThemeBackground(WidgetThemeEntry<?> entry, WidgetTheme theme) {
            return isActive() ? (isHovering() ? UtilityUiStyle.BUTTON_SELECTED_HOVER : UtilityUiStyle.BUTTON_PRESSED)
                : (isHovering() ? UtilityUiStyle.BUTTON_HOVER : UtilityUiStyle.BUTTON);
        }
    }

    public static final class Toggle extends ToggleButton {

        @Override
        public WidgetThemeEntry<?> getWidgetThemeInternal(ITheme theme) {
            return SELECTABLE_THEME;
        }
    }

    public static final class Field extends TextFieldWidget {

        @Override
        public WidgetThemeEntry<?> getWidgetThemeInternal(ITheme theme) {
            return FIELD_THEME;
        }
    }

    /** Pure carrier art over a selection button; the button owns all hover/click behavior. */
    public static final class DecorativeItem extends ItemDisplayWidget {

        @Override
        public boolean canHover() {
            return false;
        }

        @Override
        public boolean canClickThrough() {
            return true;
        }
    }
}

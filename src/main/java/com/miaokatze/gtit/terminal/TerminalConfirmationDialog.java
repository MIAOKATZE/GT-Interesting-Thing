package com.miaokatze.gtit.terminal;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.Dialog;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.miaokatze.gtit.client.gui.utility.UtilityUiStyle;

/** Terminal-only confirmation layout with room for the complete administrative warning. */
final class TerminalConfirmationDialog extends Dialog<Boolean> {

    private String message = "";
    private String confirmText = "确认";
    private String cancelText = "取消";
    private Runnable action = () -> {};

    TerminalConfirmationDialog(String name) {
        super(name, ignored -> {});
        size(220, 120).background(UtilityUiStyle.PANEL);
        child(
            new TextWidget<>(IKey.dynamic(() -> message)).pos(10, 10)
                .size(200, 76)
                .textAlign(Alignment.TopLeft)
                .color(UtilityUiStyle.TEXT)
                .shadow(false));
        child(
            new ButtonWidget<>().pos(24, 94)
                .size(80, 18)
                .overlay(IKey.dynamic(() -> confirmText))
                .onMouseTapped(mouse -> {
                    if (mouse != 0) return false;
                    closeWith(true);
                    return true;
                }));
        child(
            new ButtonWidget<>().pos(116, 94)
                .size(80, 18)
                .overlay(IKey.dynamic(() -> cancelText))
                .onMouseTapped(mouse -> {
                    if (mouse != 0) return false;
                    closeWith(false);
                    return true;
                }));
        setDisablePanelsBelow(true).setDraggable(false);
    }

    void setParams(String message, Runnable action) {
        this.message = message;
        this.action = action;
    }

    void setButtonText(String confirmText, String cancelText) {
        this.confirmText = confirmText;
        this.cancelText = cancelText;
    }

    @Override
    public void closeWith(Boolean result) {
        if (Boolean.TRUE.equals(result)) action.run();
        closeIfOpen();
    }
}

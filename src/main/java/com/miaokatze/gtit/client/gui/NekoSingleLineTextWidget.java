package com.miaokatze.gtit.client.gui;

import net.minecraft.client.Minecraft;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.widgets.TextWidget;

/** Fixed-row MUI text: measured ellipsis rather than wrap; complete text remains available on hover. */
public final class NekoSingleLineTextWidget extends TextWidget<NekoSingleLineTextWidget> {

    public NekoSingleLineTextWidget(IKey key) {
        super(key);
        shadow(false);
        tooltipAutoUpdate(true);
        tooltipBuilder(t -> {
            NekoVendingTheme.tooltip(t);
            t.addLine(getKey());
        });
    }

    @Override
    protected String checkString() {
        // Runs only while drawing on the client. No FontRenderer access during mirrored server construction.
        return NekoTextFit.fit(
            getKey().getFormatted(),
            getArea().paddedWidth(),
            getScale(),
            Minecraft.getMinecraft().fontRenderer::getStringWidth);
    }
}

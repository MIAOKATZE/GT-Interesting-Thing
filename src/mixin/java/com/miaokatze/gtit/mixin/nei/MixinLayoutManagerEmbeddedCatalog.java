package com.miaokatze.gtit.mixin.nei;

import net.minecraft.client.gui.inventory.GuiContainer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.cleanroommc.modularui.screen.ClientScreenHandler;
import com.cleanroommc.modularui.screen.ModularScreen;
import com.miaokatze.gtit.client.gui.NekoTradeMainPanel;
import com.miaokatze.gtit.client.nei.NekoEmbeddedNeiSession;

import codechicken.nei.ButtonCycled;
import codechicken.nei.LayoutManager;
import codechicken.nei.VisiblityData;
import codechicken.nei.Widget;

/** Client-only late mixin for the trade screen and its editor-owned embedded catalog. */
@Mixin(targets = "codechicken.nei.LayoutManager", remap = false)
public abstract class MixinLayoutManagerEmbeddedCatalog {

    @Inject(method = "layout", at = @At("HEAD"), remap = false)
    private static void gtit$captureEmbeddedState(GuiContainer gui, CallbackInfo ci) {
        NekoEmbeddedNeiSession.beforeNativeLayout(gui);
    }

    @Inject(method = "layout", at = @At("TAIL"), remap = false)
    private static void gtit$embeddedCatalog(GuiContainer gui, CallbackInfo ci) {
        NekoEmbeddedNeiSession.afterNativeLayout(gui);
    }

    @Redirect(
        method = "updateWidgetVisiblities",
        at = @At(value = "INVOKE", target = "Lcodechicken/nei/LayoutManager;addWidget(Lcodechicken/nei/Widget;)V"),
        remap = false)
    private static void gtit$hideTradeButtons(Widget widget, GuiContainer gui, VisiblityData visibility) {
        if ((widget != LayoutManager.options && widget != LayoutManager.bookmarksButton) || !gtit$isTradeScreen(gui)) {
            LayoutManager.addWidget(widget);
        }
    }

    @Redirect(
        method = "renderObjects",
        at = @At(value = "INVOKE", target = "Lcodechicken/nei/ButtonCycled;draw(II)V"),
        remap = false)
    private void gtit$hideDisabledTradeButtons(ButtonCycled button, int mouseX, int mouseY, GuiContainer gui,
        int guiMouseX, int guiMouseY) {
        // Disabled NEI draws these two buttons directly instead of using its widget sets.
        if (!gtit$isTradeScreen(gui)) button.draw(mouseX, mouseY);
    }

    @Unique
    private static boolean gtit$isTradeScreen(GuiContainer gui) {
        ModularScreen screen = ClientScreenHandler.getMuiScreen();
        return screen != null && (Object) screen.getScreenWrapper() == gui
            && screen.getMainPanel() instanceof NekoTradeMainPanel;
    }
}

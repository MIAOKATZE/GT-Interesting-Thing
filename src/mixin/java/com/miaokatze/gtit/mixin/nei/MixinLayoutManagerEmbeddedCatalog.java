package com.miaokatze.gtit.mixin.nei;

import net.minecraft.client.gui.inventory.GuiContainer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.miaokatze.gtit.client.nei.NekoEmbeddedNeiSession;

/** Client-only late mixin: the adapter is inert outside its exact editor-owned GUI. */
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
}

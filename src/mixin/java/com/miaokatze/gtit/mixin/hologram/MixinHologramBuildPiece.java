package com.miaokatze.gtit.mixin.hologram;

import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.miaokatze.gtit.hologram.HologramCapture;

@Mixin(targets = "gregtech.api.metatileentity.implementations.MTEEnhancedMultiBlockBase", remap = false)
public abstract class MixinHologramBuildPiece {

    @Inject(
        method = "buildPiece(Ljava/lang/String;Lnet/minecraft/item/ItemStack;ZIII)Z",
        at = @At("HEAD"),
        cancellable = true,
        remap = false)
    private void gtit$capture(String name, ItemStack trigger, boolean hints, int a, int b, int c,
        CallbackInfoReturnable<Boolean> cir) {
        if (HologramCapture.piece(this, name, trigger, hints, a, b, c)) cir.setReturnValue(true);
    }
}

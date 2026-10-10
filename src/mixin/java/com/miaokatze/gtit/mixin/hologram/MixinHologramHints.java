package com.miaokatze.gtit.mixin.hologram;

import net.minecraft.block.Block;
import net.minecraft.util.IIcon;
import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.miaokatze.gtit.hologram.HologramCapture;

@Mixin(targets = "com.gtnewhorizon.structurelib.StructureLibAPI", remap = false)
public abstract class MixinHologramHints {

    @Inject(
        method = "hintParticle(Lnet/minecraft/world/World;IIILnet/minecraft/block/Block;I)V",
        at = @At("HEAD"),
        cancellable = true,
        remap = false)
    private static void gtit$block(World w, int x, int y, int z, Block b, int meta, CallbackInfo ci) {
        if (HologramCapture.hint(w, x, y, z, b, meta)) ci.cancel();
    }

    @Inject(
        method = "hintParticle(Lnet/minecraft/world/World;III[Lnet/minecraft/util/IIcon;)V",
        at = @At("HEAD"),
        cancellable = true,
        remap = false)
    private static void gtit$icons(World w, int x, int y, int z, IIcon[] icons, CallbackInfo ci) {
        if (HologramCapture.iconHint()) ci.cancel();
    }
}

package com.miaokatze.gtit.mixin.hologram;

import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.miaokatze.gtit.hologram.HologramChannelTrace;

@Mixin(targets = "com.gtnewhorizon.structurelib.alignment.constructable.ChannelDataAccessor", remap = false)
public abstract class MixinHologramChannels {

    @Inject(
        method = "withChannel(Lnet/minecraft/item/ItemStack;Ljava/lang/String;)Lnet/minecraft/item/ItemStack;",
        at = @At("RETURN"),
        remap = false)
    private static void gtit$with(ItemStack stack, String id, CallbackInfoReturnable<ItemStack> callback) {
        ItemStack returned = callback.getReturnValue();
        if (returned != null) HologramChannelTrace.record(stack, id, returned.stackSize, 1);
    }

    @Inject(
        method = "getChannelData(Lnet/minecraft/item/ItemStack;Ljava/lang/String;)I",
        at = @At("RETURN"),
        remap = false)
    private static void gtit$data(ItemStack stack, String id, CallbackInfoReturnable<Integer> callback) {
        HologramChannelTrace.record(stack, id, callback.getReturnValue(), 2);
    }

    @Inject(
        method = "hasSubChannel(Lnet/minecraft/item/ItemStack;Ljava/lang/String;)Z",
        at = @At("RETURN"),
        remap = false)
    private static void gtit$presence(ItemStack stack, String id, CallbackInfoReturnable<Boolean> callback) {
        HologramChannelTrace.record(stack, id, callback.getReturnValue() ? 1 : 0, 4);
    }
}

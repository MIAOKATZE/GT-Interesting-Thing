package com.miaokatze.gtit.mixin.hologram;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.miaokatze.gtit.hologram.HologramRecovery;

import gregtech.api.GregTechAPI;

@Mixin(targets = "gregtech.common.blocks.ItemMachines", remap = false)
public abstract class MixinHologramRecoveryMachines {

    @WrapMethod(method = "placeBlockAt", remap = false)
    private boolean gtit$placeSealed(ItemStack stack, EntityPlayer player, World world, int x, int y, int z, int side,
        float hitX, float hitY, float hitZ, int meta, Operation<Boolean> original) {
        if (!HologramRecovery.hasSeal(stack)) {
            return original.call(stack, player, world, x, y, z, side, hitX, hitY, hitZ, meta);
        }
        if (!HologramRecovery.beginPlacement(world, x, y, z, stack, GregTechAPI.sBlockMachines)) return false;
        boolean placed = false;
        boolean result;
        try {
            placed = original.call(stack, player, world, x, y, z, side, hitX, hitY, hitZ, meta);
        } catch (RuntimeException failure) {
            // Roll back partial native initialization as well as invalid recovery payloads.
        } finally {
            result = HologramRecovery.finishPlacement(world, x, y, z, stack, placed);
        }
        return result;
    }
}

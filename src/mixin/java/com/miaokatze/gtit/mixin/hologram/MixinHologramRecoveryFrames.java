package com.miaokatze.gtit.mixin.hologram;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Mixin;

import com.miaokatze.gtit.hologram.HologramRecovery;

import gregtech.common.blocks.ItemFrames;

/** Add an inherited-method override to the late-loaded GT item, without transforming vanilla ItemBlock. */
@Mixin(value = ItemFrames.class, remap = false)
public abstract class MixinHologramRecoveryFrames extends ItemBlock {

    protected MixinHologramRecoveryFrames(Block block) {
        super(block);
    }

    // ItemFrames does not declare this Forge method. Mixin merges this new override into that subclass;
    // invokespecial dispatches to its actual ItemBlock superclass. No @Overwrite is needed.
    @Override
    public boolean placeBlockAt(ItemStack stack, EntityPlayer player, World world, int x, int y, int z, int side,
        float hitX, float hitY, float hitZ, int meta) {
        if (!HologramRecovery.hasSeal(stack)) {
            return super.placeBlockAt(stack, player, world, x, y, z, side, hitX, hitY, hitZ, meta);
        }
        if (!HologramRecovery.beginPlacement(world, x, y, z, stack, field_150939_a)) return false;
        boolean placed = false;
        boolean result;
        try {
            placed = super.placeBlockAt(stack, player, world, x, y, z, side, hitX, hitY, hitZ, meta);
        } catch (RuntimeException failure) {
            // The sealed item remains unspent; finally restores the old replaceable cell without drops.
        } finally {
            result = HologramRecovery.finishPlacement(world, x, y, z, stack, placed);
        }
        return result;
    }
}

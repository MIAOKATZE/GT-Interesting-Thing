// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtit.mixin;

import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.miaokatze.gtit.common.items.pocket.PocketSessions;

/** Flush the live model before vanilla copies or serializes its carrier. */
@Mixin(EntityPlayer.class)
public abstract class MixinEntityPlayerPocketPersistence {

    @Inject(method = "writeEntityToNBT", at = @At("HEAD"), require = 1)
    private void gtit$flushPocketBeforePlayerSave(NBTTagCompound tag, CallbackInfo ci) {
        if ((Object) this instanceof EntityPlayerMP player) PocketSessions.beforePlayerSave(player);
    }

    @Inject(method = "dropOneItem", at = @At("HEAD"), cancellable = true, require = 1)
    private void gtit$flushPocketBeforeDrop(boolean entireStack, CallbackInfoReturnable<EntityItem> cir) {
        EntityPlayer player = (EntityPlayer) (Object) this;
        if (!PocketSessions.prepareCarrierMove(player, player.getHeldItem())) cir.setReturnValue(null);
    }
}

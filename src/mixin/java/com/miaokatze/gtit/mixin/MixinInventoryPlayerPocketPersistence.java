// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtit.mixin;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.miaokatze.gtit.common.items.pocket.PocketSessions;

@Mixin(InventoryPlayer.class)
public abstract class MixinInventoryPlayerPocketPersistence {

    @Shadow
    public EntityPlayer player;

    @Inject(method = "dropAllItems", at = @At("HEAD"), require = 1)
    private void gtit$flushPocketBeforeInventoryDrop(CallbackInfo ci) {
        if (player instanceof EntityPlayerMP serverPlayer) PocketSessions.beforePlayerSave(serverPlayer);
    }
}

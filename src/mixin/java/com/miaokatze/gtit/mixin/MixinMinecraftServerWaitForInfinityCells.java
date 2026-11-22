// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtit.mixin;

import net.minecraft.server.MinecraftServer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.miaokatze.gtit.common.items.infinitycell.StorageManager;

@Mixin(MinecraftServer.class)
public abstract class MixinMinecraftServerWaitForInfinityCells {

    @Inject(method = "stopServer", at = @At("RETURN"), require = 1)
    private void gtit$finishInfinityCellSaves(CallbackInfo ci) {
        StorageManager.finishWorldSaveOnStop();
    }
}

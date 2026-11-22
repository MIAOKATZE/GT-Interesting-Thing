// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtit.mixin.backup;

import java.io.IOException;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.miaokatze.gtit.common.items.infinitycell.StorageManager;

@Pseudo
@Mixin(targets = "serverutils.task.backup.BackupTask", remap = false)
public abstract class MixinBackupTaskWaitForInfinityCells {

    // ServerUtilities has suspended world saves here and has not started copying the world.
    @Inject(method = "drainQueuedWrites()V", at = @At("RETURN"), require = 1)
    private static void gtit$awaitInfinityCellCommit(CallbackInfo ci) throws IOException {
        StorageManager.flushPendingWorldSave();
    }
}

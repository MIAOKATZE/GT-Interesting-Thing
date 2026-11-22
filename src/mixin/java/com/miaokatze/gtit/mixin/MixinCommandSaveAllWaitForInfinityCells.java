// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtit.mixin;

import java.io.IOException;

import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.server.CommandSaveAll;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.miaokatze.gtit.common.items.infinitycell.StorageManager;

@Mixin(CommandSaveAll.class)
public abstract class MixinCommandSaveAllWaitForInfinityCells {

    @Inject(
        method = "processCommand",
        at = @At(value = "CONSTANT", args = "stringValue=commands.save.flushEnd"),
        require = 1)
    private void gtit$awaitInfinityCellCommit(ICommandSender sender, String[] args, CallbackInfo ci) {
        try {
            StorageManager.flushPendingWorldSave();
        } catch (IOException | RuntimeException e) {
            final CommandException failure = new CommandException("commands.save.failed", e.getMessage());
            failure.initCause(e);
            throw failure;
        }
    }
}

package com.miaokatze.gtit.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.gtnewhorizon.gtnhlib.teams.Team;
import com.gtnewhorizon.gtnhlib.teams.TeamManager;
import com.miaokatze.gtit.trade.TeamMergePreflight;

/** GTNHLib 0.11.54 has no cancellable event before member migration. */
@Mixin(value = TeamManager.class, remap = false)
public abstract class MixinTeamManagerMergePreflight {

    @Inject(method = "mergeTeams", at = @At("HEAD"), require = 1)
    private static void gtit$preflight(Team surviving, Team consumed, CallbackInfo callback) {
        TeamMergePreflight.requireUsable(surviving, consumed);
    }
}

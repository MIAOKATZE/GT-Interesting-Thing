package com.miaokatze.gtit.mixin;

import net.minecraft.client.multiplayer.PlayerControllerMP;
import net.minecraft.entity.player.EntityPlayer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.miaokatze.gtit.common.items.rings.BaseRing;
import com.miaokatze.gtit.common.items.rings.RingDistantGrasp;

/**
 * Mixin for PlayerControllerMP to extend block reach distance
 * when the player has Ring of Distant Grasp equipped
 */
@Mixin(PlayerControllerMP.class)
public class MixinPlayerControllerMP {

    @Inject(method = "getBlockReachDistance", at = @At("RETURN"), cancellable = true)
    private void onGetBlockReachDistance(CallbackInfoReturnable<Float> cir) {
        // This is a client-side mixin, we need to get the player from Minecraft
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getMinecraft();
        if (mc.thePlayer == null) return;

        EntityPlayer player = mc.thePlayer;
        int count = BaseRing.countEquippedRings(player, RingDistantGrasp.class);
        if (count > 0) {
            cir.setReturnValue(cir.getReturnValueF() + (float) (count * RingDistantGrasp.REACH_BONUS_PER_RING));
        }
    }
}

package com.miaokatze.gtit.mixin;

import net.minecraft.client.multiplayer.PlayerControllerMP;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.miaokatze.gtit.common.items.rings.RingDistantGrasp;

import baubles.api.BaublesApi;

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
        int count = countRings(player);
        if (count > 0) {
            cir.setReturnValue(cir.getReturnValueF() + count * 2.0f);
        }
    }

    private int countRings(EntityPlayer player) {
        int count = 0;
        try {
            IInventory baubles = BaublesApi.getBaubles(player);
            for (int i = 0; i < baubles.getSizeInventory(); i++) {
                ItemStack stack = baubles.getStackInSlot(i);
                if (stack != null && stack.getItem() instanceof RingDistantGrasp) {
                    count++;
                }
            }
        } catch (Exception ignored) {}
        return count;
    }
}

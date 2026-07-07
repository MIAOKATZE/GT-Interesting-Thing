package com.miaokatze.gtit.mixin;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.management.ItemInWorldManager;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.miaokatze.gtit.common.items.rings.BaseRing;
import com.miaokatze.gtit.common.items.rings.RingDistantGrasp;

/**
 * 服务端 Mixin：扩展 ItemInWorldManager 的方块交互距离
 * <p>
 * 遥握指环（Ring of Distant Grasp）的客户端 Mixin（MixinPlayerControllerMP）仅扩展了
 * PlayerControllerMP.getBlockReachDistance()，导致专用服务器上客户端认为可以放置方块，
 * 但服务端使用 ItemInWorldManager.getBlockReachDistance() 验证距离时拒绝放置，
 * 表现为"放置方块瞬间消失"。
 * <p>
 * 此 Mixin 注入服务端的 ItemInWorldManager.getBlockReachDistance()，使服务端的距离验证
 * 与客户端一致。
 */
@Mixin(ItemInWorldManager.class)
public class MixinItemInWorldManager {

    @Shadow
    public EntityPlayerMP thisPlayerMP;

    @Inject(method = "getBlockReachDistance", at = @At("RETURN"), cancellable = true, remap = false)
    private void onGetBlockReachDistance(CallbackInfoReturnable<Double> cir) {
        if (thisPlayerMP == null) return;

        int count = BaseRing.countEquippedRings(thisPlayerMP, RingDistantGrasp.class);
        if (count > 0) {
            cir.setReturnValue(cir.getReturnValueD() + count * RingDistantGrasp.REACH_BONUS_PER_RING);
        }
    }
}

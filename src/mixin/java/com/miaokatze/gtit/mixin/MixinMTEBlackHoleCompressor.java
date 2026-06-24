package com.miaokatze.gtit.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.miaokatze.gtit.config.MuteConfig;

import gregtech.common.tileentities.machines.multi.compressor.MTEBlackHoleCompressor;

/**
 * Mixin 拦截 MTEBlackHoleCompressor.playBlackHoleSounds()
 * <p>
 * 黑洞压缩机绕过了 isMuffled() 检查，直接在 playBlackHoleSounds() 中创建
 * SoundLoopAnyBlock（GT_MACHINES_BLACK_HOLE_COMPRESSOR 黑洞循环音）。
 * 此 Mixin 在配置启用时取消该方法，阻止黑洞循环音的创建。
 * <p>
 * 该方法是 @SideOnly(Side.CLIENT) 的 public 方法，仅客户端调用。
 */
@Mixin(value = MTEBlackHoleCompressor.class, priority = 1000)
public class MixinMTEBlackHoleCompressor {

    @Inject(method = "playBlackHoleSounds", at = @At("HEAD"), cancellable = true, remap = false)
    private void gtit$muteBlackHoleSounds(CallbackInfo ci) {
        if (MuteConfig.isMuteMachineWorkingSounds()) {
            ci.cancel();
        }
    }
}

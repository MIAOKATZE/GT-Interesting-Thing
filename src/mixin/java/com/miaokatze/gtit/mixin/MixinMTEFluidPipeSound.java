package com.miaokatze.gtit.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.miaokatze.gtit.config.MuteConfig;

import gregtech.api.metatileentity.implementations.MTEFluidPipe;

/**
 * Mixin 强制禁用流体管道蒸汽泄漏音效
 * <p>
 * MTEFluidPipe 重写 doSound 处理 aIndex==9 的 RANDOM_FIZZ 蒸汽泄漏音，不检查 isMuffled()，
 * 且管道无 GUI 无静音按钮。当 extra_mute 配置启用时，拦截 aIndex==9 的 doSound 调用。
 */
@Mixin(value = MTEFluidPipe.class, priority = 1000)
public class MixinMTEFluidPipeSound {

    @Inject(method = "doSound", at = @At("HEAD"), cancellable = true, remap = false)
    private void gtit$cancelPipeSteamLeakSound(byte aIndex, double aX, double aY, double aZ, CallbackInfo ci) {
        if (MuteConfig.isExtraMute() && aIndex == 9) {
            ci.cancel();
        }
    }
}

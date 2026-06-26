package com.miaokatze.gtit.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.miaokatze.gtit.config.MuteConfig;

import gregtech.common.tileentities.boilers.MTEBoiler;

/**
 * Mixin 强制禁用锅炉沸腾/加热循环音效
 * <p>
 * 锅炉无 GUI 静音按钮，updateSoundLoops 虽检查 isMuffled()，但玩家无法触发 mMuffler=true。
 * 当 extra_mute 配置启用时，直接取消 updateSoundLoops 整个方法，阻止沸腾音(GTCEU_LOOP_BOILER)
 * 与加热音(GTCEU_LOOP_FURNACE)的 GTSoundLoop 创建。
 */
@Mixin(value = MTEBoiler.class, priority = 1000)
public class MixinMTEBoilerSoundLoops {

    @Inject(method = "updateSoundLoops", at = @At("HEAD"), cancellable = true, remap = false)
    private void gtit$cancelBoilerSoundLoops(boolean playBoiling, CallbackInfo ci) {
        if (MuteConfig.isExtraMute()) {
            ci.cancel();
        }
    }
}

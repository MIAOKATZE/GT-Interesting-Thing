package com.miaokatze.gtit.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.miaokatze.gtit.config.MuteConfig;

import gregtech.common.tileentities.boilers.MTEBoiler;

/**
 * Mixin 强制禁用锅炉蒸汽满罐排放音效
 * <p>
 * 当 extra_mute 配置启用时，无论机器 GUI 静音按钮状态如何，客户端收到 SOUND_EVENT_LET_OFF_EXCESS_STEAM (1)
 * 方块事件后都不播放 fizz 音效与蒸汽云粒子。
 * <p>
 * 配置 false 时完全不作为，锅炉蒸汽满罐排放音效按原版逻辑播放（受 isMuffled() 闸门控制）。
 */
@Mixin(value = MTEBoiler.class, priority = 1000)
public class MixinMTEBoilerVentSteam {

    @Inject(method = "doSound", at = @At("HEAD"), cancellable = true, remap = false)
    private void gtit$cancelSteamVentSound(byte aIndex, double aX, double aY, double aZ, CallbackInfo ci) {
        if (MuteConfig.isExtraMute() && aIndex == MTEBoiler.SOUND_EVENT_LET_OFF_EXCESS_STEAM) {
            ci.cancel();
        }
    }
}

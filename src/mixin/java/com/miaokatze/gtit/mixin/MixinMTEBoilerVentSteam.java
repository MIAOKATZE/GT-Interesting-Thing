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
 * 当配置启用时，无论机器 GUI 静音按钮状态如何，客户端收到 SOUND_EVENT_LET_OFF_EXCESS_STEAM (1)
 * 方块事件后都不播放 fizz 音效与蒸汽云粒子。这样配置 true 时该音效被"额外禁用，无需控制"。
 * <p>
 * 配置 false 时完全不作为，锅炉蒸汽满罐排放音效按原版逻辑播放（受 GUI 静音按钮控制）。
 */
@Mixin(value = MTEBoiler.class, priority = 1000)
public class MixinMTEBoilerVentSteam {

    @Inject(method = "doSound", at = @At("HEAD"), cancellable = true, remap = false)
    private void gtit$cancelSteamVentSound(byte aIndex, double aX, double aY, double aZ, CallbackInfo ci) {
        if (MuteConfig.isMuteMachineWorkingSounds() && aIndex == MTEBoiler.SOUND_EVENT_LET_OFF_EXCESS_STEAM) {
            ci.cancel();
        }
    }
}

package com.miaokatze.gtit.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.miaokatze.gtit.config.MuteConfig;

import gregtech.api.metatileentity.BaseMetaTileEntity;

/**
 * Mixin 拦截 BaseMetaTileEntity.isMuffled()
 * <p>
 * isMuffled() 是 GT5 机器工作音效的专属闸门，工作音效路径（sendSound / sendLoopStart /
 * sendLoopEnd / updateSounds / doActivitySound）都会检查此方法。
 * 工具交互音效（sendSoundToPlayers）和工具右击音效（doSoundAtClient 直接调用）
 * 不检查此方法，因此不受影响。
 * <p>
 * 当配置启用时，强制返回 true，使所有 GT 机器工作音效被静音：
 * - 服务端：不发 DO_SOUND / START_SOUND_LOOP / STOP_SOUND_LOOP 方块事件
 * - 客户端：不创建 GTSoundLoop 循环音效
 * <p>
 * 覆盖范围：所有单方块机器（电炉、研磨机、压缩机等）、所有标准多方块机器（EBF、组装线等）、
 * 锅炉（沸腾/加热循环音 + 蒸汽排放音）。
 */
@Mixin(value = BaseMetaTileEntity.class, priority = 1000)
public class MixinBaseMetaTileEntityMuffle {

    @Inject(method = "isMuffled", at = @At("HEAD"), cancellable = true, remap = false)
    private void gtit$forceMuffled(CallbackInfoReturnable<Boolean> cir) {
        if (MuteConfig.isMuteMachineWorkingSounds()) {
            cir.setReturnValue(true);
        }
    }
}

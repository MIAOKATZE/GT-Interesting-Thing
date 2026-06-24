package com.miaokatze.gtit.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.miaokatze.gtit.config.MuteConfig;

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.common.tileentities.machines.multi.MTEBrickedBlastFurnace;

/**
 * Mixin 拦截 MTEBrickedBlastFurnace.updateSound()
 * <p>
 * 砖砌高炉（原始高炉）绕过了 isMuffled() 检查，直接在 updateSound() 中创建
 * GTSoundLoop（GTCEU_LOOP_FIRE 火焰循环音）。此 Mixin 在配置启用时取消该方法，
 * 阻止火焰循环音的创建。
 * <p>
 * 该方法是 @SideOnly(Side.CLIENT) 的 private 方法，仅客户端调用。
 */
@Mixin(value = MTEBrickedBlastFurnace.class, priority = 1000)
public class MixinMTEBrickedBlastFurnace {

    @Inject(method = "updateSound", at = @At("HEAD"), cancellable = true, remap = false)
    private void gtit$muteUpdateSound(IGregTechTileEntity aBaseMetaTileEntity, CallbackInfo ci) {
        if (MuteConfig.isMuteMachineWorkingSounds()) {
            ci.cancel();
        }
    }
}

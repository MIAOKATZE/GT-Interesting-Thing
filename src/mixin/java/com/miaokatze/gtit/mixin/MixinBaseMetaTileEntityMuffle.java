package com.miaokatze.gtit.mixin;

import net.minecraft.nbt.NBTTagCompound;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.miaokatze.gtit.config.MuteConfig;

import gregtech.api.metatileentity.BaseMetaTileEntity;

/**
 * Mixin 控制 BaseMetaTileEntity 的默认静音状态
 * <p>
 * 当配置启用时，新放置或尚未保存过 muffler 状态的机器默认进入静音状态。
 * 具体实现：在 NBT 初始化流程末尾检查，如果 NBT 中不存在 "mMuffler" 键且配置启用，
 * 则将 mMuffler 设为 true。这样 GUI 右上角的静音按钮仍然可以正常切换单台机器的状态。
 * <p>
 * 注意：
 * <ul>
 * <li>已存在且 NBT 中已保存 mMuffler 值的机器保持原状态，不会被强制修改。</li>
 * <li>配置为 false 时完全不作为，不修改 mMuffler，玩家可通过 GUI 按钮单独控制。</li>
 * <li>此 Mixin 不再在 isMuffled() 中强制返回 true，避免 GUI 按钮失效。</li>
 * </ul>
 */
@Mixin(value = BaseMetaTileEntity.class, priority = 1000)
public class MixinBaseMetaTileEntityMuffle {

    @Shadow(remap = false)
    private boolean mMuffler;

    @Inject(method = "setInitialValuesAsNBT", at = @At("TAIL"), remap = false)
    private void gtit$defaultMuffled(NBTTagCompound aNBT, short aID, CallbackInfo ci) {
        if (!MuteConfig.isMuteMachineWorkingSounds()) {
            return;
        }
        if (aNBT == null) {
            mMuffler = true;
        } else if (!aNBT.hasKey("mMuffler")) {
            mMuffler = true;
        }
    }
}

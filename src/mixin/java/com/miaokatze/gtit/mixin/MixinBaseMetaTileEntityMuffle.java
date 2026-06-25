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
 * 具体实现：在 NBT 初始化流程末尾检查，
 * - aID > 0 表示新放置机器（来自 ItemMachines.onItemUse 等），强制 mMuffler = true
 * - aID == 0 且 aNBT 不含 "mMuffler" 键（旧存档迁移），设为 true
 * - aID == 0 且 aNBT 含 "mMuffler" 键（已存在机器），保留原值不动
 * <p>
 * 这样 GUI 右上角的静音按钮仍然可以正常切换单台机器的状态，已保存的设置不会被覆盖。
 * <p>
 * 注意：
 * <ul>
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
        if (aID > 0) {
            mMuffler = true;
        } else if (aNBT == null || !aNBT.hasKey("mMuffler")) {
            mMuffler = true;
        }
    }
}

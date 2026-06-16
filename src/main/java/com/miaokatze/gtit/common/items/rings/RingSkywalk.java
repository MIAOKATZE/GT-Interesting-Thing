package com.miaokatze.gtit.common.items.rings;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

import cpw.mods.fml.common.Optional;

/**
 * 戒指·凌步 / Ring of Skywalk
 * 自动走上1格方块（类似走上半砖的平滑效果）
 * 通过增大 stepHeight 实现，无需跳跃
 */
@Optional.Interface(iface = "baubles.api.IBauble", modid = "Baubles")
public class RingSkywalk extends BaseRing {

    /**
     * 默认 stepHeight 为 0.6（无法走上1格方块）
     * 设为 1.1 即可走上1格方块（类似半砖体验）
     */
    private static final float STEP_HEIGHT = 1.1F;

    public RingSkywalk() {
        super("ring_skywalk");
    }

    @Override
    public void onWornTick(ItemStack itemstack, EntityLivingBase player) {
        if (!(player instanceof EntityPlayer entityPlayer)) return;
        // 每tick确保stepHeight正确
        if (entityPlayer.stepHeight < STEP_HEIGHT) {
            entityPlayer.stepHeight = STEP_HEIGHT;
        }
    }

    @Override
    public void onEquipped(ItemStack itemstack, EntityLivingBase player) {
        if (!(player instanceof EntityPlayer entityPlayer)) return;
        entityPlayer.stepHeight = STEP_HEIGHT;
    }

    @Override
    public void onUnequipped(ItemStack itemstack, EntityLivingBase player) {
        if (!(player instanceof EntityPlayer entityPlayer)) return;
        // 恢复默认 stepHeight
        // onUnequipped 时物品仍在槽位中，需要检查其他凌步戒指
        int count = countEquippedRings(entityPlayer, RingSkywalk.class) - 1;
        if (count <= 0) {
            entityPlayer.stepHeight = 0.6F;
        }
    }
}

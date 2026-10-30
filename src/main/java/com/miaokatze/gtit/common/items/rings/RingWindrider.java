package com.miaokatze.gtit.common.items.rings;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

import com.miaokatze.gtit.common.items.FlightSourceSupport;

import cpw.mods.fml.common.Optional;

/**
 * 戒指·御风 / Ring of Windrider
 * 获得创造飞行（无消耗）
 */
@Optional.Interface(iface = "baubles.api.IBauble", modid = "Baubles")
public class RingWindrider extends BaseRing {

    public RingWindrider() {
        super("ring_windrider");
    }

    @Override
    public boolean hasEffect(ItemStack stack, int pass) {
        return true;
    }

    // O2-09 第一步：删除与 BaseRing 逐字相同的死 getBaubleType 重写（基类已返回 BaubleType.RING）

    @Override
    public void onWornTick(ItemStack itemstack, EntityLivingBase player) {
        if (player.worldObj.isRemote) return;
        if (!(player instanceof EntityPlayer entityPlayer)) return;
        // 跨维度传送时服务端重建 EntityPlayer，allowFlying 会被重置为 false。
        // 仅在状态确实变化（false→true）时发包同步客户端，避免每 tick 重复发送（与 FloatCore 对称）。
        if (!entityPlayer.capabilities.allowFlying) {
            entityPlayer.capabilities.allowFlying = true;
            entityPlayer.sendPlayerAbilities();
        }
    }

    @Override
    public void onEquipped(ItemStack itemstack, EntityLivingBase player) {
        if (player.worldObj.isRemote) return;
        if (!(player instanceof EntityPlayer entityPlayer)) return;
        entityPlayer.capabilities.allowFlying = true;
        entityPlayer.sendPlayerAbilities();
    }

    @Override
    public void onUnequipped(ItemStack itemstack, EntityLivingBase player) {
        if (player.worldObj.isRemote) return;
        if (!(player instanceof EntityPlayer entityPlayer)) return;
        FlightSourceSupport.onUnequipped(entityPlayer, itemstack);
    }
}

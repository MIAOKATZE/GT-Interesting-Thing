package com.miaokatze.gtit.common.items.rings;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;

import cpw.mods.fml.common.Optional;

/**
 * 戒指·疾风 / Ring of Tempest
 * 每5秒赋予30秒的：速度II、跳跃提升II
 */
@Optional.Interface(iface = "baubles.api.IBauble", modid = "Baubles")
public class RingTempest extends BaseRing {

    private static final int REFRESH_INTERVAL = 100; // 5秒 * 20tick
    private static final int EFFECT_DURATION = 600;

    public RingTempest() {
        super("ring_tempest");
    }

    @Override
    public boolean hasEffect(ItemStack stack, int pass) {
        return true;
    }

    @Override
    public void onEquipped(ItemStack itemstack, EntityLivingBase player) {
        if (player.worldObj.isRemote) return;
        if (!(player instanceof EntityPlayer entityPlayer)) return;
        applyEffects(entityPlayer);
    }

    @Override
    public void onWornTick(ItemStack itemstack, EntityLivingBase player) {
        if (player.worldObj.isRemote) return;
        if (!(player instanceof EntityPlayer entityPlayer)) return;

        if (player.ticksExisted % REFRESH_INTERVAL != 0) return;

        applyEffects(entityPlayer);
    }

    private void applyEffects(EntityPlayer entityPlayer) {
        entityPlayer.addPotionEffect(new PotionEffect(Potion.moveSpeed.id, EFFECT_DURATION, 1)); // 速度II
        entityPlayer.addPotionEffect(new PotionEffect(Potion.jump.id, EFFECT_DURATION, 1)); // 跳跃提升II
    }
}

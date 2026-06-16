package com.miaokatze.gtit.common.items.rings;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;

import cpw.mods.fml.common.Optional;

/**
 * 戒指·裂山 / Ring of Mountainbreaker
 * 每20秒赋予30秒的：力量II、急迫II
 */
@Optional.Interface(iface = "baubles.api.IBauble", modid = "Baubles")
public class RingMountainbreaker extends BaseRing {

    private static final int REFRESH_INTERVAL = 400;
    private static final int EFFECT_DURATION = 600;

    public RingMountainbreaker() {
        super("ring_mountainbreaker");
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
        entityPlayer.addPotionEffect(new PotionEffect(Potion.damageBoost.id, EFFECT_DURATION, 1)); // 力量II
        entityPlayer.addPotionEffect(new PotionEffect(Potion.digSpeed.id, EFFECT_DURATION, 1)); // 急迫II
    }
}

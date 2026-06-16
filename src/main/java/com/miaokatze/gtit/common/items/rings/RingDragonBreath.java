package com.miaokatze.gtit.common.items.rings;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;

import cpw.mods.fml.common.Optional;

/**
 * 戒指·龙息 / Ring of Dragon's Breath
 * 每20秒赋予30秒的：抗火、夜视、生命恢复I、抗性提升I
 */
@Optional.Interface(iface = "baubles.api.IBauble", modid = "Baubles")
public class RingDragonBreath extends BaseRing {

    private static final int REFRESH_INTERVAL = 400; // 20秒 * 20tick
    private static final int EFFECT_DURATION = 600; // 30秒 * 20tick

    public RingDragonBreath() {
        super("ring_dragon_breath");
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

        // 每20秒刷新一次效果
        if (player.ticksExisted % REFRESH_INTERVAL != 0) return;

        applyEffects(entityPlayer);
    }

    private void applyEffects(EntityPlayer entityPlayer) {
        entityPlayer.addPotionEffect(new PotionEffect(Potion.fireResistance.id, EFFECT_DURATION, 0));
        entityPlayer.addPotionEffect(new PotionEffect(Potion.nightVision.id, EFFECT_DURATION, 0));
        entityPlayer.addPotionEffect(new PotionEffect(Potion.regeneration.id, EFFECT_DURATION, 0));
        entityPlayer.addPotionEffect(new PotionEffect(Potion.resistance.id, EFFECT_DURATION, 0));
    }
}

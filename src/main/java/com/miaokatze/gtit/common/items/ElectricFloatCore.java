package com.miaokatze.gtit.common.items;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import com.miaokatze.gtit.register.CreativeTabManager;

import baubles.api.BaubleType;
import baubles.api.IBauble;
import cpw.mods.fml.common.Optional;
import ic2.api.item.ElectricItem;
import ic2.api.item.IElectricItem;

/**
 * 电力浮空核心饰品
 * <p>
 * 可以装备到任意Baubles饰品栏，提供创造飞行能力，优先消耗电力，电力耗尽后消耗饥饿值。
 */
@Optional.Interface(iface = "baubles.api.IBauble", modid = "Baubles")
public class ElectricFloatCore extends Item implements IBauble, IElectricItem {

    private static final long MAX_CHARGE = 32000000L;
    private static final int TIER = 1; // LV
    private static final long COST_PER_TICK = 128L;

    public ElectricFloatCore() {
        super();
        setUnlocalizedName("electric_float_core");
        setTextureName("gtit:electric_float_core");
        setCreativeTab(CreativeTabManager.CREATIVE_TAB);
        setMaxStackSize(1);
    }

    // ========== Baubles饰品实现 ==========

    @Override
    @Optional.Method(modid = "Baubles")
    public BaubleType getBaubleType(ItemStack itemstack) {
        return BaubleType.UNIVERSAL;
    }

    @Override
    @Optional.Method(modid = "Baubles")
    public void onWornTick(ItemStack itemstack, EntityLivingBase player) {
        if (player.worldObj.isRemote) return;

        if (player instanceof EntityPlayer entityPlayer) {
            // 检查是否满足飞行条件：电力足够，或者电力不足但饥饿值>=3
            boolean hasPower = ElectricItem.manager.canUse(itemstack, COST_PER_TICK);
            boolean hasFood = entityPlayer.getFoodStats()
                .getFoodLevel() >= 6;
            boolean canFly = hasPower || hasFood;

            if (canFly) {
                entityPlayer.capabilities.allowFlying = true;

                // 检测是否正在飞行
                if (entityPlayer.capabilities.isFlying) {
                    // 先尝试消耗电力
                    if (ElectricItem.manager.canUse(itemstack, COST_PER_TICK)) {
                        ElectricItem.manager.use(itemstack, COST_PER_TICK, entityPlayer);
                    } else {
                        // 电力不足，消耗饥饿值
                        entityPlayer.getFoodStats()
                            .addExhaustion(0.01f);
                    }
                }
            } else {
                // 条件不满足，禁用飞行
                entityPlayer.capabilities.allowFlying = false;
                entityPlayer.capabilities.isFlying = false;
                entityPlayer.sendPlayerAbilities();
            }
        }
    }

    @Override
    @Optional.Method(modid = "Baubles")
    public void onEquipped(ItemStack itemstack, EntityLivingBase player) {
        if (player.worldObj.isRemote) return;

        if (player instanceof EntityPlayer entityPlayer) {
            entityPlayer.capabilities.allowFlying = true;
            entityPlayer.sendPlayerAbilities();
        }
    }

    @Override
    @Optional.Method(modid = "Baubles")
    public void onUnequipped(ItemStack itemstack, EntityLivingBase player) {
        if (player.worldObj.isRemote) return;

        if (player instanceof EntityPlayer entityPlayer) {
            if (!entityPlayer.capabilities.isCreativeMode) {
                entityPlayer.capabilities.allowFlying = false;
                entityPlayer.capabilities.isFlying = false;
            }
            entityPlayer.sendPlayerAbilities();
        }
    }

    @Override
    @Optional.Method(modid = "Baubles")
    public boolean canEquip(ItemStack itemstack, EntityLivingBase player) {
        return true;
    }

    @Override
    @Optional.Method(modid = "Baubles")
    public boolean canUnequip(ItemStack itemstack, EntityLivingBase player) {
        return true;
    }

    // ========== IC2电力物品实现 ==========

    @Override
    public boolean canProvideEnergy(ItemStack aStack) {
        return true;
    }

    @Override
    public double getMaxCharge(ItemStack aStack) {
        return MAX_CHARGE;
    }

    @Override
    public int getTier(ItemStack aStack) {
        return TIER;
    }

    @Override
    public double getTransferLimit(ItemStack aStack) {
        return 32L; // LV limit
    }

    @Override
    public Item getChargedItem(ItemStack aStack) {
        return this;
    }

    @Override
    public Item getEmptyItem(ItemStack aStack) {
        return this;
    }
}

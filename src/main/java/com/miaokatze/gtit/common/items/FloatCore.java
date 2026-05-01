package com.miaokatze.gtit.common.items;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import com.miaokatze.gtit.register.CreativeTabManager;

import baubles.api.BaubleType;
import baubles.api.IBauble;
import cpw.mods.fml.common.Optional;

/**
 * 浮空核心饰品
 * <p>
 * 可以装备到任意Baubles饰品栏位，提供创造飞行能力，飞行时消耗饥饿值。
 */
@Optional.Interface(iface = "baubles.api.IBauble", modid = "Baubles")
public class FloatCore extends Item implements IBauble {

    /**
     * 构造函数：初始化浮空核心的基础属性
     */
    public FloatCore() {
        super();
        setUnlocalizedName("float_core");
        setTextureName("gtit:float_core");
        setCreativeTab(CreativeTabManager.CREATIVE_TAB);
        setMaxStackSize(1);
    }

    // ========== Baubles饰品实现 ==========

    @Override
    @Optional.Method(modid = "Baubles")
    public BaubleType getBaubleType(ItemStack itemstack) {
        return BaubleType.UNIVERSAL; // 任意饰品栏都能装备
    }

    @Override
    @Optional.Method(modid = "Baubles")
    public void onWornTick(ItemStack itemstack, EntityLivingBase player) {
        if (player.worldObj.isRemote) return;

        if (player instanceof EntityPlayer entityPlayer) {
            // 检查饥饿值是否足够
            boolean canFly = entityPlayer.getFoodStats()
                .getFoodLevel() >= 6;

            if (canFly) {
                entityPlayer.capabilities.allowFlying = true;

                // 如果玩家正在飞行，消耗饥饿值
                if (entityPlayer.capabilities.isFlying) {
                    entityPlayer.getFoodStats()
                        .addExhaustion(0.02f);
                }
            } else {
                // 饥饿值不足，禁用飞行
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
            // 如果玩家不在创造模式，取消飞行能力
            if (!entityPlayer.capabilities.isCreativeMode) {
                entityPlayer.capabilities.allowFlying = false;
                entityPlayer.capabilities.isFlying = false;
            }
            entityPlayer.sendPlayerAbilities();
        }
    }

    @Override
    @Optional.Method(modid = "Baubles")
    public boolean canUnequip(ItemStack itemstack, EntityLivingBase player) {
        return true; // 允许随时卸下
    }

    @Override
    @Optional.Method(modid = "Baubles")
    public boolean canEquip(ItemStack itemstack, EntityLivingBase player) {
        return true; // 允许随时装备
    }

    // ========== 普通物品功能（没有Baubles时也能用） ==========

    @Override
    public void onUpdate(ItemStack stack, World world, net.minecraft.entity.Entity entity, int itemSlot,
        boolean isHeld) {
        // 没有Baubles时的备用实现：如果玩家手持也能飞行（可选）
        // 暂时先不实现，专注Bauble功能
    }
}

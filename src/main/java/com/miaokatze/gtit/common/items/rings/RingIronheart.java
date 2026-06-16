package com.miaokatze.gtit.common.items.rings;

import java.util.List;
import java.util.UUID;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;

import baubles.api.BaubleType;
import cpw.mods.fml.common.Optional;

/**
 * 戒指·磐躯 / Ring of Ironheart
 * 生命上限+20（10颗心），可叠加
 */
@Optional.Interface(iface = "baubles.api.IBauble", modid = "Baubles")
public class RingIronheart extends BaseRing {

    private static final UUID IRONHEALTH_UUID = UUID.fromString("d7b9a2c4-5e6f-4a8b-9c1d-2e3f4a5b6c7d");
    private static final double HEALTH_BONUS_PER_RING = 20.0;

    public RingIronheart() {
        super("ring_ironheart");
    }

    @Override
    public BaubleType getBaubleType(ItemStack itemstack) {
        return BaubleType.RING;
    }

    @Override
    public void onWornTick(ItemStack itemstack, EntityLivingBase player) {
        // 属性修饰器在装备/卸下时管理，tick中无需操作
    }

    @Override
    public void onEquipped(ItemStack itemstack, EntityLivingBase player) {
        if (player.worldObj.isRemote) return;
        if (!(player instanceof EntityPlayer entityPlayer)) return;
        // 重新计算装备数量并应用修饰器
        applyHealthModifier(entityPlayer);
    }

    @Override
    public void onUnequipped(ItemStack itemstack, EntityLivingBase player) {
        if (player.worldObj.isRemote) return;
        if (!(player instanceof EntityPlayer entityPlayer)) return;
        // onUnequipped 被调用时物品仍在槽位中，所以需要 -1
        applyHealthModifier(entityPlayer, -1);
    }

    /**
     * 计算当前装备的磐躯戒指数量并应用修饰器
     */
    private void applyHealthModifier(EntityPlayer player) {
        applyHealthModifier(player, 0);
    }

    /**
     * @param offset 修正值，onUnequipped 时传 -1（因为物品仍在槽位中）
     */
    private void applyHealthModifier(EntityPlayer player, int offset) {
        IAttributeInstance maxHealth = player.getAttributeMap()
            .getAttributeInstance(SharedMonsterAttributes.maxHealth);

        // 移除旧修饰器
        AttributeModifier existing = maxHealth.getModifier(IRONHEALTH_UUID);
        if (existing != null) {
            maxHealth.removeModifier(existing);
        }

        // 计算装备数量（onUnequipped时物品仍在槽位中，需要偏移）
        int count = countEquippedRings(player, RingIronheart.class) + offset;
        if (count > 0) {
            maxHealth.applyModifier(
                new AttributeModifier(IRONHEALTH_UUID, "gtit.ironheart", count * HEALTH_BONUS_PER_RING, 0));
        }
    }

    @Override
    protected void addStackableInfo(ItemStack stack, EntityPlayer player, List tooltip) {
        int count = countEquippedRings(player, RingIronheart.class);
        if (count > 0) {
            tooltip.add(
                EnumChatFormatting.AQUA + "+"
                    + (count * (int) HEALTH_BONUS_PER_RING)
                    + " "
                    + StatCollector.translateToLocal("gtit.tooltip.max_health"));
        }
    }
}

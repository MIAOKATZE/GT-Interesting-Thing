package com.miaokatze.gtit.common.items.rings;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;

import com.miaokatze.gtit.common.items.ElectricFloatCore;
import com.miaokatze.gtit.common.items.FloatCore;

import baubles.api.BaubleType;
import baubles.api.BaublesApi;
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
    public BaubleType getBaubleType(ItemStack itemstack) {
        return BaubleType.RING;
    }

    @Override
    public void onWornTick(ItemStack itemstack, EntityLivingBase player) {
        if (player.worldObj.isRemote) return;
        if (!(player instanceof EntityPlayer entityPlayer)) return;
        entityPlayer.capabilities.allowFlying = true;
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
        // onUnequipped 时物品仍在槽位中，所以检查数量时需要 -1
        // 只有当没有其他飞行来源时才禁用飞行
        int windriderCount = countEquippedRings(entityPlayer, RingWindrider.class) - 1;
        boolean hasOtherFlight = hasOtherFlightSource(entityPlayer);
        if (windriderCount <= 0 && !hasOtherFlight) {
            if (!entityPlayer.capabilities.isCreativeMode) {
                entityPlayer.capabilities.allowFlying = false;
                entityPlayer.capabilities.isFlying = false;
            }
            entityPlayer.sendPlayerAbilities();
        }
    }

    /**
     * 检查是否有非御风戒指的飞行来源（浮空核心/电力浮空核心）
     */
    private boolean hasOtherFlightSource(EntityPlayer player) {
        try {
            IInventory baubles = BaublesApi.getBaubles(player);
            for (int i = 0; i < baubles.getSizeInventory(); i++) {
                ItemStack stack = baubles.getStackInSlot(i);
                if (stack == null) continue;
                if (stack.getItem() instanceof FloatCore) return true;
                if (stack.getItem() instanceof ElectricFloatCore) return true;
            }
        } catch (Exception ignored) {}
        return false;
    }
}

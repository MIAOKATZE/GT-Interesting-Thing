package com.miaokatze.gtit.common.items.rings;

import java.util.List;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;

import com.miaokatze.gtit.register.CreativeTabManager;

import baubles.api.BaubleType;
import baubles.api.BaublesApi;
import baubles.api.IBauble;
import cpw.mods.fml.common.Optional;

@Optional.Interface(iface = "baubles.api.IBauble", modid = "Baubles")
public abstract class BaseRing extends Item implements IBauble {

    protected final String ringName;

    public BaseRing(String ringName) {
        super();
        this.ringName = ringName;
        setUnlocalizedName(ringName);
        setTextureName("gtit:" + ringName);
        setCreativeTab(CreativeTabManager.CREATIVE_TAB);
        setMaxStackSize(1);
    }

    @Override
    public BaubleType getBaubleType(ItemStack itemstack) {
        return BaubleType.RING;
    }

    @Override
    public abstract void onWornTick(ItemStack itemstack, EntityLivingBase player);

    @Override
    public void onEquipped(ItemStack itemstack, EntityLivingBase player) {}

    @Override
    public void onUnequipped(ItemStack itemstack, EntityLivingBase player) {}

    @Override
    public boolean canUnequip(ItemStack itemstack, EntityLivingBase player) {
        return true;
    }

    @Override
    public boolean canEquip(ItemStack itemstack, EntityLivingBase player) {
        return true;
    }

    @Override
    public void addInformation(ItemStack stack, EntityPlayer player, List tooltip, boolean showAdvanced) {
        // 逐行读取 tooltip.0, tooltip.1, tooltip.2...
        for (int i = 0;; i++) {
            String key = "item." + ringName + ".tooltip." + i;
            String line = StatCollector.translateToLocal(key);
            if (line.equals(key)) break; // 没有更多行了
            tooltip.add(EnumChatFormatting.GOLD + line);
        }
        addStackableInfo(stack, player, tooltip);
    }

    /**
     * 子类可覆盖此方法添加叠加信息（如当前叠加数量）
     */
    protected void addStackableInfo(ItemStack stack, EntityPlayer player, List tooltip) {}

    /**
     * 统计玩家装备的指定类型戒指数量
     */
    protected int countEquippedRings(EntityPlayer player, Class<? extends BaseRing> ringClass) {
        int count = 0;
        try {
            IInventory baubles = BaublesApi.getBaubles(player);
            for (int i = 0; i < baubles.getSizeInventory(); i++) {
                ItemStack stack = baubles.getStackInSlot(i);
                if (stack != null && ringClass.isInstance(stack.getItem())) {
                    count++;
                }
            }
        } catch (Exception ignored) {}
        return count;
    }

    /**
     * 检查玩家是否装备了指定类型的戒指
     */
    protected boolean hasRingEquipped(EntityPlayer player, Class<? extends BaseRing> ringClass) {
        return countEquippedRings(player, ringClass) > 0;
    }
}

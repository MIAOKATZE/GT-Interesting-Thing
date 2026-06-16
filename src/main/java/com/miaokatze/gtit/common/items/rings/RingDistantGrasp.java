package com.miaokatze.gtit.common.items.rings;

import java.util.List;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;

import cpw.mods.fml.common.Optional;

/**
 * 戒指·遥握 / Ring of Distant Grasp
 * 交互距离+2，攻击距离+2（每枚+2，可叠加）
 * 实际距离扩展通过 MixinPlayerControllerMP 实现
 */
@Optional.Interface(iface = "baubles.api.IBauble", modid = "Baubles")
public class RingDistantGrasp extends BaseRing {

    public static final double REACH_BONUS_PER_RING = 2.0;

    public RingDistantGrasp() {
        super("ring_distant_grasp");
    }

    @Override
    public void onWornTick(ItemStack itemstack, EntityLivingBase player) {
        // 距离扩展由 Mixin 处理，此处无需操作
    }

    @Override
    protected void addStackableInfo(ItemStack stack, EntityPlayer player, List tooltip) {
        int count = countEquippedRings(player, RingDistantGrasp.class);
        if (count > 0) {
            tooltip.add(
                EnumChatFormatting.AQUA + "+"
                    + (count * (int) REACH_BONUS_PER_RING)
                    + " "
                    + StatCollector.translateToLocal("gtit.tooltip.reach_distance"));
        }
    }
}

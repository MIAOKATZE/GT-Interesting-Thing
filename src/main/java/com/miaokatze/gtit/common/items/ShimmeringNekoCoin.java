package com.miaokatze.gtit.common.items;

import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;

import com.miaokatze.gtit.register.CreativeTabManager;

/**
 * 闪烁猫猫币 / Shimmering Neko Coin
 * 附魔光效，只能通过钓鱼获得（0.5%概率）
 */
public class ShimmeringNekoCoin extends Item {

    public ShimmeringNekoCoin() {
        super();
        setUnlocalizedName("shimmering_neko_coin");
        setTextureName("gtit:miao_coin");
        setCreativeTab(CreativeTabManager.CREATIVE_TAB);
        setMaxStackSize(64);
    }

    @Override
    public boolean hasEffect(ItemStack stack, int pass) {
        return true;
    }

    @Override
    public void addInformation(ItemStack stack, EntityPlayer player, List tooltip, boolean showAdvanced) {
        for (int i = 0;; i++) {
            String key = "item.shimmering_neko_coin.tooltip." + i;
            String line = StatCollector.translateToLocal(key);
            if (line.equals(key)) break;
            tooltip.add(EnumChatFormatting.AQUA + line);
        }
    }
}

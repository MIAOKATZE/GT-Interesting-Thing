package com.miaokatze.gtit.common.items;

import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;

import com.miaokatze.gtit.register.CreativeTabManager;

/**
 * 猫猫币 / Neko Coin
 * 可在村庄、神庙、地牢等箱子中找到，也可通过钓鱼获得
 * 动态旋转材质
 */
public class NekoCoin extends Item {

    public NekoCoin() {
        super();
        setUnlocalizedName("neko_coin");
        setTextureName("gtit:miao_coin");
        setCreativeTab(CreativeTabManager.CREATIVE_TAB);
        setMaxStackSize(64);
    }

    @Override
    public void addInformation(ItemStack stack, EntityPlayer player, List tooltip, boolean showAdvanced) {
        for (int i = 0;; i++) {
            String key = "item.neko_coin.tooltip." + i;
            String line = StatCollector.translateToLocal(key);
            if (line.equals(key)) break;
            tooltip.add(EnumChatFormatting.YELLOW + line);
        }
    }
}

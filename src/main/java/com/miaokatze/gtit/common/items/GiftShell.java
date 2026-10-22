package com.miaokatze.gtit.common.items;

import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;

import com.miaokatze.gtit.common.util.GTITUtils;
import com.miaokatze.gtit.gift.GiftIconCatalog;
import com.miaokatze.gtit.gift.GiftService;
import com.miaokatze.gtit.register.CreativeTabManager;

/** A real registry item with a fixed pixel-art shell, independently bindable to gift definitions. */
public class GiftShell extends Item {

    private final String shellKey;

    public GiftShell(String key) {
        if (!GiftIconCatalog.contains(key)) throw new IllegalArgumentException("Unknown gift shell");
        shellKey = key;
        setUnlocalizedName(key + "_gift");
        setTextureName(GiftIconCatalog.atlasResourcePath(key));
        setCreativeTab(CreativeTabManager.CREATIVE_TAB);
        setMaxStackSize(1);
        setHasSubtypes(false);
    }

    public final String shellKey() {
        return shellKey;
    }

    @Override
    public boolean hasEffect(ItemStack stack, int pass) {
        return true;
    }

    @Override
    public void addInformation(ItemStack stack, EntityPlayer player, List tooltip, boolean advanced) {
        for (int i = 0;; i++) {
            String key = "gtit.gift.shell.tooltip." + i;
            String line = StatCollector.translateToLocal(key);
            if (line.equals(key)) break;
            tooltip.add(EnumChatFormatting.LIGHT_PURPLE + line);
        }
        tooltip.add(
            EnumChatFormatting.GRAY + StatCollector.translateToLocal(
                "starter".equals(shellKey) ? "gtit.gift.shell.starter.tooltip" : "gtit.gift.shell.authorized.tooltip"));
        tooltip.add(GTITUtils.getAddedByLine());
    }

    @Override
    public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player) {
        if (!world.isRemote && player instanceof EntityPlayerMP serverPlayer) {
            GiftService.INSTANCE.open(serverPlayer, stack);
            // GiftService already commits and synchronizes the complete inventory transaction.
            // Returning the reward now in the hand lets vanilla's creative-mode useItem path
            // restore the package's old count (1) onto that reward.
            return stack;
        }
        return stack;
    }
}

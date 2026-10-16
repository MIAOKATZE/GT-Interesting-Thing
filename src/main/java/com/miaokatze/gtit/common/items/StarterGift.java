package com.miaokatze.gtit.common.items;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IIcon;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;

import com.miaokatze.gtit.common.util.GTITUtils;
import com.miaokatze.gtit.gift.GiftIconCatalog;
import com.miaokatze.gtit.gift.GiftService;
import com.miaokatze.gtit.register.CreativeTabManager;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Existing registry ID retained for legacy stacks; new packages carry a server-owned grant token. */
public class StarterGift extends Item {

    @SideOnly(Side.CLIENT)
    private static Map<String, IIcon> catalogIcons;

    public StarterGift() {
        setUnlocalizedName("starter_gift");
        setTextureName("gtit:starter_gift");
        setCreativeTab(CreativeTabManager.CREATIVE_TAB);
        setMaxStackSize(1);
        setHasSubtypes(false);
    }

    @Override
    public boolean hasEffect(ItemStack stack, int pass) {
        return true;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerIcons(IIconRegister register) {
        itemIcon = register.registerIcon("gtit:starter_gift");
        catalogIcons = new LinkedHashMap<>();
        for (String key : GiftIconCatalog.keys()) {
            catalogIcons.put(key, register.registerIcon(GiftIconCatalog.atlasResourcePath(key)));
        }
    }

    @SideOnly(Side.CLIENT)
    public static IIcon catalogIcon(String key) {
        return catalogIcons == null ? null : catalogIcons.get(GiftIconCatalog.normalize(key));
    }

    @SideOnly(Side.CLIENT)
    public IIcon getCatalogIcon(String key) {
        return catalogIcon(key);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIconIndex(ItemStack stack) {
        return getIcon(stack, 0);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIcon(ItemStack stack, int pass) {
        if (stack.hasTagCompound() && stack.getTagCompound()
            .hasKey(GiftService.TAG_ITEM_ICON)) {
            ItemStack custom = ItemStack.loadItemStackFromNBT(
                stack.getTagCompound()
                    .getCompoundTag(GiftService.TAG_ITEM_ICON));
            if (custom != null && custom.getItem() != this) return custom.getItem()
                .getIcon(custom, pass);
        }
        String key = stack.hasTagCompound() ? stack.getTagCompound()
            .getString(GiftService.TAG_ICON) : "starter";
        IIcon icon = catalogIcon(key);
        return icon == null ? itemIcon : icon;
    }

    @Override
    public void addInformation(ItemStack stack, EntityPlayer player, List tooltip, boolean showAdvanced) {
        for (int i = 0;; i++) {
            String key = "item.starter_gift.tooltip." + i;
            String line = StatCollector.translateToLocal(key);
            if (line.equals(key)) break;
            tooltip.add(EnumChatFormatting.LIGHT_PURPLE + line);
        }
        tooltip.add(GTITUtils.getAddedByLine());
    }

    @Override
    public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player) {
        if (!world.isRemote && player instanceof EntityPlayerMP serverPlayer) {
            GiftService.INSTANCE.open(serverPlayer, stack);
            return serverPlayer.inventory.getCurrentItem();
        }
        return stack;
    }
}

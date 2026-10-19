package com.miaokatze.gtit.common.items;

import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.util.IIcon;

import com.miaokatze.gtit.gift.GiftIconCatalog;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Existing registry ID retained for legacy stacks and owner-bound packages. */
public class StarterGift extends GiftShell {

    @SideOnly(Side.CLIENT)
    private static Map<String, IIcon> catalogIcons;

    public StarterGift() {
        super("starter");
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerIcons(IIconRegister register) {
        catalogIcons = new LinkedHashMap<>();
        for (String key : GiftIconCatalog.keys()) {
            catalogIcons.put(key, register.registerIcon(GiftIconCatalog.atlasResourcePath(key)));
        }
        itemIcon = catalogIcons.get("starter");
    }

    @SideOnly(Side.CLIENT)
    public static IIcon catalogIcon(String key) {
        return catalogIcons == null ? null : catalogIcons.get(GiftIconCatalog.normalize(key));
    }

    @SideOnly(Side.CLIENT)
    public IIcon getCatalogIcon(String key) {
        return catalogIcon(key);
    }
}

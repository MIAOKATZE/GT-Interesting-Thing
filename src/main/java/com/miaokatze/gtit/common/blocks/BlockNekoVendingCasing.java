package com.miaokatze.gtit.common.blocks;

import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;

import com.miaokatze.gtit.register.CreativeTabManager;
import com.miaokatze.gtit.register.TextureManager;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import gregtech.api.enums.Textures;
import gregtech.api.render.TextureFactory;
import gregtech.api.util.GTUtility;
import gregtech.common.blocks.BlockCasingsAbstract;
import gregtech.common.blocks.ItemCasings;
import gregtech.common.blocks.MaterialCasings;

/** Wooden casing, registered with the same machine-block behavior as the original VM casing. */
public final class BlockNekoVendingCasing extends BlockCasingsAbstract {

    // Fixed on both physical sides: ME hatches synchronize this numeric texture ID.
    private static final int TEXTURE_INDEX = 126 << 7;

    public BlockNekoVendingCasing() {
        super(ItemCasings.class, "neko_vending_casing", MaterialCasings.INSTANCE);
        setCreativeTab(CreativeTabManager.CREATIVE_TAB);
        GTUtility.addTexturePage((byte) (TEXTURE_INDEX >> 7));
        if (Textures.BlockIcons.getCasingTextureForId(TEXTURE_INDEX) != null) {
            throw new IllegalStateException("Neko vending casing texture index is already occupied: " + TEXTURE_INDEX);
        }
        Textures.BlockIcons.setCasingTextureForId(TEXTURE_INDEX, TextureFactory.of(this, 0));
    }

    @Override
    public int getTextureIndex(int meta) {
        return TEXTURE_INDEX;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIcon(int side, int meta) {
        if (side == 0) return TextureManager.NEKO_VENDING_CASING_BOTTOM.getIcon();
        if (side == 1) return TextureManager.NEKO_VENDING_CASING_TOP.getIcon();
        return TextureManager.NEKO_VENDING_CASING_SIDE.getIcon();
    }

    @Override
    public int colorMultiplier(IBlockAccess world, int x, int y, int z) {
        return 0xffffff;
    }
}

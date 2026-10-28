package com.miaokatze.gtit.common.machine.v2;

import com.cubefury.vendingmachine.blocks.MTEVendingUplinkHatch;
import com.miaokatze.gtit.register.BlockRegistrar;
import com.miaokatze.gtit.register.TextureManager;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import gregtech.api.enums.Dyes;
import gregtech.api.enums.Textures;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.render.TextureFactory;

/** Dedicated Neko ME uplink; networking, inventories and NBT remain owned by the original VM implementation. */
public final class MTENekoVendingUplinkHatch extends MTEVendingUplinkHatch {

    private ITexture[][] nekoInventoryTextures;

    public MTENekoVendingUplinkHatch(int id, String name, String regionalName) {
        super(id, name, regionalName);
    }

    private MTENekoVendingUplinkHatch(MTENekoVendingUplinkHatch prototype) {
        super(prototype);
    }

    @Override
    public ITexture getCasingTexture() {
        return Textures.BlockIcons.getCasingTextureForId(BlockRegistrar.nekoVendingCasing.getTextureIndex(0));
    }

    @Override
    @SideOnly(Side.CLIENT)
    public ITexture[][] getInventoryTextures() {
        if (nekoInventoryTextures == null) {
            // Inventory has no block neighbours: use the standalone wood, never a world-coordinate CTM texture.
            ITexture wood = TextureFactory.of(
                TextureManager.NEKO_VENDING_CASING_BOTTOM,
                TextureManager.NEKO_VENDING_CASING_TOP,
                TextureManager.NEKO_VENDING_CASING_SIDE,
                Dyes._NULL.getRGBA());
            ITexture[] casing = { wood };
            nekoInventoryTextures = new ITexture[][] { casing, casing, casing, casing, getTexturesActive(wood),
                casing };
        }
        return nekoInventoryTextures;
    }

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity tileEntity) {
        return new MTENekoVendingUplinkHatch(this);
    }
}

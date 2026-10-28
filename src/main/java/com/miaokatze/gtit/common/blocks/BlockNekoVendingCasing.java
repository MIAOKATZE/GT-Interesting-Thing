package com.miaokatze.gtit.common.blocks;

import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;
import net.minecraftforge.common.util.ForgeDirection;

import com.miaokatze.gtit.common.machine.v2.MTENekoVendingMachineV2;
import com.miaokatze.gtit.common.machine.v2.MTENekoVendingUplinkHatch;
import com.miaokatze.gtit.register.CreativeTabManager;
import com.miaokatze.gtit.register.TextureManager;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import gregtech.api.enums.GTValues;
import gregtech.api.enums.Textures;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.render.TextureFactory;
import gregtech.api.util.GTUtility;
import gregtech.common.blocks.BlockCasingsAbstract;
import gregtech.common.blocks.ItemCasings;
import gregtech.common.blocks.MaterialCasings;

/** Wooden casing, registered with the same machine-block behavior as the original VM casing. */
public final class BlockNekoVendingCasing extends BlockCasingsAbstract {

    // Fixed on both physical sides: ME hatches synchronize this numeric texture ID.
    private static final int TEXTURE_INDEX = 126 << 7;

    // Texture-space right for DOWN, UP, NORTH, SOUTH, WEST and EAST in native GT/RenderBlocks rendering.
    private static final ForgeDirection[] RIGHT = { ForgeDirection.EAST, ForgeDirection.EAST, ForgeDirection.WEST,
        ForgeDirection.EAST, ForgeDirection.SOUTH, ForgeDirection.NORTH };
    private static final IIcon[][] CONNECTED_ICONS = new IIcon[3][16];

    public BlockNekoVendingCasing() {
        super(ItemCasings.class, "neko_vending_casing", MaterialCasings.INSTANCE);
        setCreativeTab(CreativeTabManager.CREATIVE_TAB);
        GTValues.mCTMEnabledBlock.add(getClass().getName());
        GTUtility.addTexturePage((byte) (TEXTURE_INDEX >> 7));
        if (Textures.BlockIcons.getCasingTextureForId(TEXTURE_INDEX) != null) {
            throw new IllegalStateException("Neko vending casing texture index is already occupied: " + TEXTURE_INDEX);
        }
        Textures.BlockIcons.setCasingTextureForId(
            TEXTURE_INDEX,
            TextureFactory.blockBuilder()
                .setFromBlock(this, 0)
                .useWorldCoord()
                .build());
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
    @SideOnly(Side.CLIENT)
    public void registerBlockIcons(IIconRegister register) {
        String[] faces = { "bottom", "top", "side" };
        for (int face = 0; face < faces.length; face++) {
            for (int mask = 1; mask < 16; mask++) {
                CONNECTED_ICONS[face][mask] = register
                    .registerIcon("gtit:neko_vending_casing_" + faces[face] + "_" + mask);
            }
        }
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIcon(IBlockAccess world, int x, int y, int z, int side) {
        int mask = connectionMask(world, x, y, z, side);
        return mask == 0 ? getIcon(side, 0) : CONNECTED_ICONS[side < 2 ? side : 2][mask];
    }

    /** Bits are texture-space top/right/bottom/left. Only this machine's dedicated parts can join. */
    static int connectionMask(IBlockAccess world, int x, int y, int z, int side) {
        ForgeDirection up = side < 2 ? ForgeDirection.NORTH : ForgeDirection.UP;
        ForgeDirection right = RIGHT[side];
        int mask = 0;
        if (connects(world, x + up.offsetX, y + up.offsetY, z + up.offsetZ)) mask |= 1;
        if (connects(world, x + right.offsetX, y + right.offsetY, z + right.offsetZ)) mask |= 2;
        if (connects(world, x - up.offsetX, y - up.offsetY, z - up.offsetZ)) mask |= 4;
        if (connects(world, x - right.offsetX, y - right.offsetY, z - right.offsetZ)) mask |= 8;
        return mask;
    }

    private static boolean connects(IBlockAccess world, int x, int y, int z) {
        if (world.getBlock(x, y, z) instanceof BlockNekoVendingCasing) {
            return world.getBlockMetadata(x, y, z) == 0;
        }
        TileEntity tile = world.getTileEntity(x, y, z);
        if (!(tile instanceof IGregTechTileEntity)) return false;
        IMetaTileEntity machine = ((IGregTechTileEntity) tile).getMetaTileEntity();
        return machine instanceof MTENekoVendingMachineV2 || machine instanceof MTENekoVendingUplinkHatch;
    }

    @Override
    public int colorMultiplier(IBlockAccess world, int x, int y, int z) {
        return 0xffffff;
    }
}

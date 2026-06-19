package com.miaokatze.gtit.common.blocks;

import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.IIcon;
import net.minecraft.world.World;

import com.miaokatze.gtit.register.CreativeTabManager;

import appeng.client.texture.ExtraBlockTextures;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public class BlockLimitedInfiniteEnergyCell extends BlockContainer {

    public BlockLimitedInfiniteEnergyCell() {
        super(Material.iron);
        this.setBlockName("gtit.limited_infinite_energy_cell");
        this.setCreativeTab(CreativeTabManager.CREATIVE_TAB);
        this.setHardness(10.0F);
        this.setResistance(50.0F);
    }

    @Override
    public TileEntity createNewTileEntity(World world, int metadata) {
        return new TileLimitedInfiniteEnergyCell();
    }

    @Override
    public int damageDropped(int metadata) {
        return 0;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIcon(int side, int metadata) {
        return switch (metadata) {
            case 0 -> ExtraBlockTextures.MEDenseEnergyCell0.getIcon();
            case 1 -> ExtraBlockTextures.MEDenseEnergyCell1.getIcon();
            case 2 -> ExtraBlockTextures.MEDenseEnergyCell2.getIcon();
            case 3 -> ExtraBlockTextures.MEDenseEnergyCell3.getIcon();
            case 4 -> ExtraBlockTextures.MEDenseEnergyCell4.getIcon();
            case 5 -> ExtraBlockTextures.MEDenseEnergyCell5.getIcon();
            case 6 -> ExtraBlockTextures.MEDenseEnergyCell6.getIcon();
            case 7 -> ExtraBlockTextures.MEDenseEnergyCell7.getIcon();
            default -> ExtraBlockTextures.MEDenseEnergyCell0.getIcon();
        };
    }
}

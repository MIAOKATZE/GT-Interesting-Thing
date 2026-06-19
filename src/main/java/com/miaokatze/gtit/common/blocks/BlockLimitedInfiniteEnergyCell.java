package com.miaokatze.gtit.common.blocks;

import java.util.EnumSet;
import java.util.List;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;

import com.gtnewhorizon.gtnhlib.item.ItemStackNBT;

import appeng.block.AEBaseItemBlockChargeable;
import appeng.block.AEBaseTileBlock;
import appeng.client.texture.ExtraBlockTextures;
import appeng.core.features.AEFeature;
import appeng.helpers.AEGlassMaterial;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public class BlockLimitedInfiniteEnergyCell extends AEBaseTileBlock {

    public BlockLimitedInfiniteEnergyCell() {
        super(AEGlassMaterial.INSTANCE);
        this.setTileEntity(TileLimitedInfiniteEnergyCell.class);
        this.setFeature(EnumSet.of(AEFeature.Core));
    }

    @Override
    public IIcon getIcon(final int direction, final int metadata) {
        // 始终使用致密能源元件充能后的材质
        return ExtraBlockTextures.MEDenseEnergyCell7.getIcon();
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void getCheckedSubBlocks(final Item item, final CreativeTabs tabs, final List<ItemStack> itemStacks) {
        super.getCheckedSubBlocks(item, tabs, itemStacks);
        final ItemStack charged = new ItemStack(this, 1);
        ItemStackNBT.setDouble(charged, "internalCurrentPower", this.getMaxPower());
        ItemStackNBT.setDouble(charged, "internalMaxPower", this.getMaxPower());
        itemStacks.add(charged);
    }

    public double getMaxPower() {
        return TileLimitedInfiniteEnergyCell.BASE_MAX_POWER;
    }

    @Override
    public Class<? extends appeng.block.AEBaseItemBlock> getItemBlockClass() {
        return AEBaseItemBlockChargeable.class;
    }
}

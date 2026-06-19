package com.miaokatze.gtit.register;

import static com.miaokatze.gtit.common.api.enums.GTITItemList.LimitedInfiniteEnergyCell;

import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;

import com.miaokatze.gtit.common.blocks.BlockLimitedInfiniteEnergyCell;
import com.miaokatze.gtit.common.blocks.TileLimitedInfiniteEnergyCell;
import com.miaokatze.gtit.main.GTInterestingThing;

import appeng.block.AEBaseItemBlockChargeable;
import appeng.core.features.IStackSrc;
import appeng.tile.AEBaseTile;
import cpw.mods.fml.common.registry.GameRegistry;

public class BlockRegistrar {

    public static BlockLimitedInfiniteEnergyCell blockLimitedInfiniteEnergyCell;

    public static void init() {
        GTInterestingThing.LOG.info("开始注册 GTIT 方块...");

        registerLimitedInfiniteEnergyCell();

        GTInterestingThing.LOG.info("GTIT 方块注册完成。");
    }

    @SuppressWarnings("unchecked")
    private static void registerLimitedInfiniteEnergyCell() {
        blockLimitedInfiniteEnergyCell = new BlockLimitedInfiniteEnergyCell();

        // 使用 AEBaseItemBlockChargeable 作为 ItemBlock，支持 AE2 电力存储
        GameRegistry.registerBlock(
            blockLimitedInfiniteEnergyCell,
            AEBaseItemBlockChargeable.class,
            "limited_infinite_energy_cell");

        // AEBaseTileBlock 会自动注册 TileEntity，但仍需注册 TileItem
        AEBaseTile.registerTileItem(TileLimitedInfiniteEnergyCell.class, new IStackSrc() {

            @Override
            public ItemStack stack(int size) {
                return new ItemStack(blockLimitedInfiniteEnergyCell, size);
            }

            @Override
            public Item getItem() {
                return ItemBlock.getItemFromBlock(blockLimitedInfiniteEnergyCell);
            }

            @Override
            public int getDamage() {
                return 0;
            }

            @Override
            public boolean isEnabled() {
                return true;
            }
        });

        // 设置 GTITItemList 引用
        LimitedInfiniteEnergyCell.set(ItemBlock.getItemFromBlock(blockLimitedInfiniteEnergyCell));

        // 添加到创造模式标签页
        CreativeTabManager.addItemToTab(LimitedInfiniteEnergyCell.get(1));
    }
}

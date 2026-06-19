package com.miaokatze.gtit.register;

import static com.miaokatze.gtit.common.api.enums.GTITItemList.LimitedInfiniteEnergyCell;

import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;

import com.miaokatze.gtit.common.blocks.BlockLimitedInfiniteEnergyCell;
import com.miaokatze.gtit.common.blocks.TileLimitedInfiniteEnergyCell;
import com.miaokatze.gtit.main.GTInterestingThing;

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

    private static void registerLimitedInfiniteEnergyCell() {
        blockLimitedInfiniteEnergyCell = new BlockLimitedInfiniteEnergyCell();

        GameRegistry.registerBlock(blockLimitedInfiniteEnergyCell, ItemBlock.class, "limited_infinite_energy_cell");
        GameRegistry.registerTileEntity(TileLimitedInfiniteEnergyCell.class, "gtit:tile_limited_infinite_energy_cell");

        // 注册 AE2 TileItem，使 AENetworkProxy 能正确获取方块对应的 ItemStack
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

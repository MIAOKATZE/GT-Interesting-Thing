package com.miaokatze.gtit.client.hologram;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraftforge.common.util.ForgeDirection;

/** Isolated, bounded render snapshot. Never exposes a live world or a live tile entity to renderers. */
final class HologramBlockAccess implements IBlockAccess {

    private final Map<String, HologramState.Cell> cells = new HashMap<>();

    HologramBlockAccess(HologramState state) {
        for (HologramState.Cell cell : state.cells) cells.put(key(cell.x, cell.y, cell.z), cell);
    }

    private static String key(int x, int y, int z) {
        return x + ":" + y + ":" + z;
    }

    public Block getBlock(int x, int y, int z) {
        HologramState.Cell cell = cells.get(key(x, y, z));
        Block block = cell == null ? null : cell.block(false);
        return block == null ? Blocks.air : block;
    }

    public int getBlockMetadata(int x, int y, int z) {
        HologramState.Cell cell = cells.get(key(x, y, z));
        return cell == null ? 0 : cell.wantMeta;
    }

    public TileEntity getTileEntity(int x, int y, int z) {
        return null;
    }

    public int getLightBrightnessForSkyBlocks(int x, int y, int z, int minimum) {
        return 0xf000f0;
    }

    public int isBlockProvidingPowerTo(int x, int y, int z, int side) {
        return 0;
    }

    public boolean isAirBlock(int x, int y, int z) {
        return getBlock(x, y, z) == Blocks.air;
    }

    public BiomeGenBase getBiomeGenForCoords(int x, int z) {
        return BiomeGenBase.plains;
    }

    public int getHeight() {
        return 256;
    }

    public boolean extendedLevelsInChunkCache() {
        return false;
    }

    public boolean isSideSolid(int x, int y, int z, ForgeDirection side, boolean fallback) {
        return getBlock(x, y, z).isSideSolid(this, x, y, z, side);
    }
}

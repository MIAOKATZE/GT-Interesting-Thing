package com.miaokatze.gtit.testlab;

import net.minecraft.util.ChunkCoordinates;
import net.minecraft.world.WorldProvider;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.biome.WorldChunkManagerHell;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IChunkProvider;

/** Empty world; never alters shared overworld WorldInfo, weather or gamerules. */
public final class TestLabWorldProvider extends WorldProvider {

    @Override
    protected void registerWorldChunkManager() {
        worldChunkMgr = new WorldChunkManagerHell(BiomeGenBase.plains, 0);
        hasNoSky = false;
    }

    @Override
    public IChunkProvider createChunkGenerator() {
        return new TestLabChunkProvider(worldObj);
    }

    @Override
    public String getDimensionName() {
        return "GTIT Test Laboratory";
    }

    @Override
    public String getSaveFolder() {
        return "GTIT-TESTLAB";
    }

    @Override
    public boolean canRespawnHere() {
        return false;
    }

    @Override
    public boolean canDoLightning(Chunk chunk) {
        return false;
    }

    @Override
    public boolean canDoRainSnowIce(Chunk chunk) {
        return false;
    }

    @Override
    public ChunkCoordinates getSpawnPoint() {
        return new ChunkCoordinates(0, 64, 0);
    }

    @Override
    public float calculateCelestialAngle(long time, float partial) {
        return 0;
    }

    @Override
    public void updateWeather() {}
}

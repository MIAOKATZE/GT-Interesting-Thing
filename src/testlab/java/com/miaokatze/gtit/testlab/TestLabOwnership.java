package com.miaokatze.gtit.testlab;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.WorldSavedData;
import net.minecraft.world.WorldServer;

/** Persistent manifest of exact fixture blocks. Reset never scans or deletes a region or a dimension folder. */
public final class TestLabOwnership extends WorldSavedData {

    private static final String KEY = "gtit_testlab_owned_v1";
    private final Map<String, NBTTagCompound> blocks = new LinkedHashMap<>();

    public TestLabOwnership(String name) {
        super(name);
    }

    static TestLabOwnership get(WorldServer world) {
        if (!TestLabDimension.isLab(world)) throw new IllegalArgumentException("Not the laboratory world");
        TestLabOwnership data = (TestLabOwnership) world.perWorldStorage.loadData(TestLabOwnership.class, KEY);
        if (data == null) {
            data = new TestLabOwnership(KEY);
            world.perWorldStorage.setData(KEY, data);
        }
        return data;
    }

    boolean put(WorldServer world, String runId, int x, int y, int z, Block block, int meta) {
        return placeNative(world, runId, x, y, z, block, meta, () -> world.setBlock(x, y, z, block, meta, 2));
    }

    boolean placeNative(WorldServer world, String runId, int x, int y, int z, Block block, int meta,
        java.util.function.BooleanSupplier placement) {
        TestLabRuntime.pin(world, x, z);
        String key = x + "," + y + "," + z;
        NBTTagCompound old = blocks.get(key);
        if (old == null && !world.isAirBlock(x, y, z))
            throw new IllegalStateException("Refusing to overwrite unowned block at " + key);
        if (old != null && (!runId.equals(old.getString("run")) || !matches(world, old)))
            throw new IllegalStateException("Fixture ownership changed at " + key);
        NBTTagCompound entry = new NBTTagCompound();
        entry.setInteger("x", x);
        entry.setInteger("y", y);
        entry.setInteger("z", z);
        entry.setString("run", runId);
        entry.setInteger("block", Block.getIdFromBlock(block));
        entry.setInteger("meta", meta);
        // Record intent before publishing the fixture, so a normal world save persists ownership too.
        blocks.put(key, entry);
        markDirty();
        boolean result = placement.getAsBoolean();
        if (!result && !matches(world, entry)) blocks.remove(key);
        return result;
    }

    int size() {
        return blocks.size();
    }

    /** Modified fixtures are preserved; their ownership is forgotten rather than deleting someone else's block. */
    int clean(WorldServer world, int budget) {
        Iterator<NBTTagCompound> iterator = blocks.values()
            .iterator();
        int removed = 0;
        while (iterator.hasNext() && removed < budget) {
            NBTTagCompound entry = iterator.next();
            TestLabRuntime.pin(world, entry.getInteger("x"), entry.getInteger("z"));
            if (matches(world, entry)) {
                int x = entry.getInteger("x"), y = entry.getInteger("y"), z = entry.getInteger("z");
                world.removeTileEntity(x, y, z);
                world.setBlock(x, y, z, Blocks.air, 0, 2);
            }
            iterator.remove();
            removed++;
        }
        if (removed != 0) markDirty();
        return removed;
    }

    private boolean matches(WorldServer world, NBTTagCompound entry) {
        int x = entry.getInteger("x"), y = entry.getInteger("y"), z = entry.getInteger("z");
        return Block.getIdFromBlock(world.getBlock(x, y, z)) == entry.getInteger("block")
            && world.getBlockMetadata(x, y, z) == entry.getInteger("meta");
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        blocks.clear();
        NBTTagList list = tag.getTagList("blocks", 10);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound entry = list.getCompoundTagAt(i);
            int x = entry.getInteger("x"), y = entry.getInteger("y"), z = entry.getInteger("z");
            // Manifest corruption must not broaden reset beyond the two explicitly reserved canvases.
            if (TestLabBounds.contains(x, y, z)) blocks.put(x + "," + y + "," + z, entry);
        }
    }

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        NBTTagList list = new NBTTagList();
        for (NBTTagCompound entry : blocks.values()) list.appendTag(entry.copy());
        tag.setTag("blocks", list);
    }
}

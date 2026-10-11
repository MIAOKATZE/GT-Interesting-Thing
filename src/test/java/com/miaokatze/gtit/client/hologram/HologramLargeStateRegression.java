package com.miaokatze.gtit.client.hologram;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import com.miaokatze.gtit.testutil.SimpleAssert;

/** Full cell indexing and visibility at the remote end of a structure. */
public final class HologramLargeStateRegression {

    private HologramLargeStateRegression() {}

    public static void run() {
        NBTTagCompound source = new NBTTagCompound();
        source.setString("session", "large");
        NBTTagList cells = new NBTTagList();
        for (int i = 0; i < 6000; i++) {
            NBTTagCompound cell = new NBTTagCompound();
            cell.setInteger("x", i);
            cell.setInteger("y", 64);
            cell.setInteger("z", 0);
            cells.appendTag(cell);
        }
        source.setTag("cells", cells);
        HologramState state = new HologramState(source);
        SimpleAssert.eq(6000, state.cells.size(), "large snapshot keeps cells beyond the old 4096 cutoff");
        SimpleAssert.eq(5999, state.cells.get(5999).index, "editing retains complete server index order");
        SimpleAssert.that(state.distanceSquared(5999.5, 64.5, .5) == 0, "remote structure end remains visible");
        SimpleAssert.that(state.distanceSquared(6100, 64.5, .5) > 4096, "far outside structure stays culled");
    }
}

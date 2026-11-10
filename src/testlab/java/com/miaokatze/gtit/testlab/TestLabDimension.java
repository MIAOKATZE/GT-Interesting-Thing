package com.miaokatze.gtit.testlab;

import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;

import com.miaokatze.gtit.main.GTInterestingThing;

/** Never remap an existing dimension or adopt its saved data. */
public final class TestLabDimension {

    private static String problem = "disabled";

    private TestLabDimension() {}

    static void register() {
        if (!TestLabConfig.enabled) return;
        int id = TestLabConfig.dimensionId;
        if (DimensionManager.isDimensionRegistered(id)
            || !DimensionManager.registerProviderType(id, TestLabWorldProvider.class, false)) {
            problem = "dimension/provider ID " + id + " is occupied; change config/gtit/testlab.cfg and restart";
            GTInterestingThing.LOG.error("[GTIT-LAB] {}. No existing world was modified.", problem);
            return;
        }
        DimensionManager.registerDimension(id, id);
        problem = null;
    }

    public static String problem() {
        return problem;
    }

    public static boolean isLab(World world) {
        return TestLabConfig.enabled && problem == null
            && world != null
            && world.provider instanceof TestLabWorldProvider
            && world.provider.dimensionId == TestLabConfig.dimensionId;
    }

    public static WorldServer world() {
        if (problem != null) throw new IllegalStateException(problem);
        if (DimensionManager.getWorld(TestLabConfig.dimensionId) == null)
            DimensionManager.initDimension(TestLabConfig.dimensionId);
        WorldServer world = DimensionManager.getWorld(TestLabConfig.dimensionId);
        if (!isLab(world)) throw new IllegalStateException("GTIT laboratory world is unavailable");
        return world;
    }
}

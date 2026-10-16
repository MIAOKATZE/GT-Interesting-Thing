package com.miaokatze.gtit.testlab.scenario;

import java.io.ByteArrayInputStream;
import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import com.miaokatze.gtit.common.items.pocket.PocketConstants;
import com.miaokatze.gtit.common.items.pocket.PocketInventory;
import com.miaokatze.gtit.common.items.pocket.PocketUpgradeType;
import com.miaokatze.gtit.common.items.pocket.PocketUpgrades;
import com.miaokatze.gtit.testutil.MinecraftTestBootstrap;
import com.miaokatze.gtit.testutil.SimpleAssert;
import com.miaokatze.gtit.testutil.TestRunner;

/**
 * Reproductions for the first dedicated-server probe failures: current capacity and a real byte persistence boundary.
 */
public final class NativePocketFixtureTest {

    private NativePocketFixtureTest() {}

    public static void main(String[] args) throws Exception {
        if (!MinecraftTestBootstrap.enter(NativePocketFixtureTest.class, args)) return;
        Map<String, Runnable> tests = new LinkedHashMap<>();
        tests.put("native quantity stays within current capacity and full tank rejects more", () -> {
            NBTTagCompound root = new NBTTagCompound();
            PocketUpgrades.install(root, PocketUpgradeType.CAPACITY);
            PocketInventory inventory = PocketInventory.readFrom(root);
            long capacity = PocketConstants.fluidTankCapacityMl(PocketConstants.CAPACITY_UPGRADE_MAX_COUNT);
            SimpleAssert.that(capacity == 2_000_000_000L, "Current fully upgraded capacity is 2G, not historical 16G");
            long quantity = 1_500_000_123L;
            SimpleAssert.that(
                inventory.fillOwnTankL(0, FluidRegistry.WATER, quantity) == quantity,
                "Legal fixture quantity accepted without clipping");
            SimpleAssert.eq(
                91,
                inventory.tankAt(0)
                    .fill(new FluidStack(FluidRegistry.WATER, 91), false),
                "Simulation has headroom");
            SimpleAssert.that(inventory.tankTruthAt(0) == quantity, "Simulation cannot mutate truth");
            SimpleAssert.that(
                inventory.fillOwnTankL(1, FluidRegistry.WATER, Long.MAX_VALUE) == capacity,
                "Oversized long request clips to current capacity");
            SimpleAssert
                .that(inventory.fillOwnTankL(1, FluidRegistry.WATER, 1) == 0, "Already full tank rejects another unit");
        });
        tests.put("compressed byte persistence severs vanilla NBT references", () -> {
            try {
                ItemStack original = new ItemStack(Items.paper, 17);
                original.setTagCompound(new NBTTagCompound());
                original.getTagCompound()
                    .setString("payload", "猫猫\nNBT");
                original.getTagCompound()
                    .setLong("counter", 9_007_199_254_740_991L);
                PocketInventory inventory = PocketInventory.readFrom(new NBTTagCompound());
                SimpleAssert.eq(17, inventory.depositIntoStorage(original), "Real pocket item insertion");
                ItemStack carrier = new ItemStack(Items.book);
                carrier.setTagCompound(new NBTTagCompound());
                inventory.writeTo(carrier.getTagCompound());
                NBTTagCompound assembled = carrier.writeToNBT(new NBTTagCompound());
                SimpleAssert.that(
                    ItemStack.loadItemStackFromNBT(assembled)
                        .getTagCompound() == carrier.getTagCompound(),
                    "Vanilla object-tree assembly alone is not a persistence/deep-copy boundary");
                byte[] bytes = CompressedStreamTools.compress(assembled);
                ItemStack restoredCarrier = ItemStack
                    .loadItemStackFromNBT(CompressedStreamTools.readCompressed(new ByteArrayInputStream(bytes)));
                PocketInventory restored = PocketInventory.readFrom(restoredCarrier.getTagCompound());
                SimpleAssert.that(
                    NativeFixtures.sameStack(original, restored.storageStack(0)),
                    "Native pocket codec retains item count and special NBT through bytes");
                restored.storageStack(0)
                    .getTagCompound()
                    .setBoolean("onlyCopy", true);
                SimpleAssert.that(
                    !original.getTagCompound()
                        .hasKey("onlyCopy")
                        && !inventory.storageStack(0)
                            .getTagCompound()
                            .hasKey("onlyCopy"),
                    "Mutation after disk-equivalent decoding cannot alias either source or original session");
            } catch (java.io.IOException error) {
                throw new AssertionError(error);
            }
        });
        TestRunner.run(NativePocketFixtureTest.class, tests);
    }
}

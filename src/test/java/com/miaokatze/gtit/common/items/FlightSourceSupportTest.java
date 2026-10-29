package com.miaokatze.gtit.common.items;

import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import net.minecraft.inventory.InventoryBasic;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import com.miaokatze.gtit.common.items.rings.RingWindrider;
import com.miaokatze.gtit.testutil.MinecraftTestBootstrap;
import com.miaokatze.gtit.testutil.TestRunner;

import sun.misc.Unsafe;

/** Mixed-source availability must not spend resources or assume an unrelated item grants flight. */
public final class FlightSourceSupportTest {

    private FlightSourceSupportTest() {}

    public static void main(String[] args) throws Exception {
        if (!MinecraftTestBootstrap.enter(FlightSourceSupportTest.class, args)) return;
        Map<String, Runnable> cases = new LinkedHashMap<>();
        cases.put("ring survives depleted cores in either slot order", FlightSourceSupportTest::ring);
        cases.put("powered core survives depleted core", FlightSourceSupportTest::powered);
        cases.put("food remains a valid fallback for both cores", FlightSourceSupportTest::food);
        cases.put("empty and unknown sources cannot retain flight", FlightSourceSupportTest::empty);
        cases.put("unequip excludes only the exact departing stack", FlightSourceSupportTest::departing);
        TestRunner.run(FlightSourceSupportTest.class, cases);
    }

    private static void ring() {
        ItemStack food = stack(FloatCore.class), electric = stack(ElectricFloatCore.class), ring = stack(RingWindrider.class);
        require(FlightSourceSupport.hasAvailableFlight(slots(food, electric, ring), false, s -> false), "last ring ignored");
        require(FlightSourceSupport.hasAvailableFlight(slots(ring, electric, food), false, s -> {
            throw new AssertionError("ring should short-circuit energy probing");
        }), "first ring ignored");
    }

    private static void powered() {
        ItemStack empty = stack(ElectricFloatCore.class), charged = stack(ElectricFloatCore.class);
        AtomicInteger probes = new AtomicInteger();
        require(FlightSourceSupport.hasAvailableFlight(slots(stack(FloatCore.class), empty, charged), false, s -> {
            probes.incrementAndGet();
            return s == charged;
        }), "charged second core ignored");
        require(probes.get() == 2, "probe only the equipped electric cores");
        require(empty.stackSize == 1 && charged.stackSize == 1, "availability must not mutate stacks");
    }

    private static void food() {
        for (Class<? extends Item> type : java.util.Arrays.asList(FloatCore.class, ElectricFloatCore.class)) {
            require(FlightSourceSupport.hasAvailableFlight(slots(stack(type)), true, s -> {
                throw new AssertionError("food fallback does not require an energy probe");
            }), "food fallback lost");
            require(!FlightSourceSupport.hasAvailableFlight(slots(stack(type)), false, s -> false),
                "depleted sole source must not retain flight");
        }
    }

    private static void empty() {
        ItemStack removed = stack(RingWindrider.class);
        removed.stackSize = 0;
        require(!FlightSourceSupport.hasAvailableFlight(slots(removed, stack(Item.class)), true, s -> true),
            "empty/third-party items must not be classified as active GTIT flight sources");
        require(!FlightSourceSupport.hasAvailableFlight(null, true, s -> true), "absent inventory must be empty");
    }

    private static void departing() {
        ItemStack leaving = stack(RingWindrider.class);
        ItemStack identical = new ItemStack(leaving.getItem());
        require(!FlightSourceSupport.hasAvailableFlight(slots(leaving), true, s -> true, leaving),
            "pre-clear callback counted the departing source itself");
        require(FlightSourceSupport.hasAvailableFlight(slots(leaving, identical), false, s -> false, leaving),
            "another identical item is not the departing stack");
        require(FlightSourceSupport.hasAvailableFlight(slots(identical), false, s -> false, leaving),
            "post-clear callback must not subtract another remaining source");
        require(!FlightSourceSupport.hasAvailableFlight(slots(leaving, stack(FloatCore.class)), false, s -> false, leaving),
            "an unavailable remaining core cannot sustain flight");
    }

    private static InventoryBasic slots(ItemStack... stacks) {
        InventoryBasic inventory = new InventoryBasic("flight-test", true, stacks.length);
        for (int i = 0; i < stacks.length; i++) inventory.setInventorySlotContents(i, stacks[i]);
        return inventory;
    }

    private static ItemStack stack(Class<? extends Item> type) {
        try {
            // Type identity is sufficient; avoid registering items/configuration during a model test.
            Field field = Unsafe.class.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            return new ItemStack((Item) ((Unsafe) field.get(null)).allocateInstance(type));
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}

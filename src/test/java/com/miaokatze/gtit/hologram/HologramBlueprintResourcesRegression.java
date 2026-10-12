package com.miaokatze.gtit.hologram;

import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import com.miaokatze.gtit.testutil.MinecraftTestBootstrap;
import com.miaokatze.gtit.testutil.SimpleAssert;

import appeng.api.config.Actionable;
import appeng.api.storage.data.IAEFluidStack;
import appeng.util.item.AEFluidStack;

/** Resource accounting and the production fluid ledger, using a bounded monitor fixture rather than a live AE grid. */
public final class HologramBlueprintResourcesRegression {

    private static int aeAssertions;

    private HologramBlueprintResourcesRegression() {}

    public static void main(String[] args) throws Exception {
        if (!MinecraftTestBootstrap.enter(HologramBlueprintResourcesRegression.class, args)) return;
        run();
        System.out.println("Hologram blueprint resources regressions passed");
    }

    public static void run() {
        try {
            inventoryAndEnergy();
            configurationSamples();
            fluidCostsAndSelectedKeys();
            rejectionBeforePayment();
            bulkPayment();
            exactBulkContext();
        } catch (Throwable failure) {
            failure.printStackTrace(System.err);
            throw new AssertionError("blueprint resources regression", failure);
        }
    }

    /** Called by smoke after real FML/AE initialization; the monitor remains an explicit bounded fixture. */
    public static void runWithAe() {
        aeAssertions = 0;
        System.out.println("[AE-FLUID-TEST] START lifecycle=real-FML monitor=fixture");
        try {
            aeThat(
                appeng.core.AEConfig.instance != null && appeng.api.AEApi.instance() != null,
                "server AE lifecycle is initialized before data stack and fluid ledger checks");
            taggedFluidRejection();
            fluidLedger();
            System.out.println("[AE-FLUID-TEST] FINAL PASS assertCount=" + aeAssertions + " monitor=fixture");
        } catch (Throwable failure) {
            failure.printStackTrace(System.err);
            throw new AssertionError("real-FML AE fluid ledger regression", failure);
        }
    }

    private static void inventoryAndEnergy() throws Exception {
        ItemStack diamond = new ItemStack(Items.diamond, 1, 0);
        NBTTagCompound itemTag = new NBTTagCompound();
        itemTag.setString("fixtureIdentity", "precise tagged contents");
        diamond.setTagCompound(itemTag);
        NBTTagCompound row = diamond.writeToNBT(new NBTTagCompound());
        row.setInteger("IntSlot", 5);
        row.setInteger("Count", 5000);
        NBTTagList inventory = new NBTTagList();
        inventory.appendTag(row);
        NBTTagCompound payload = new NBTTagCompound();
        payload.setTag("Inventory", inventory);
        payload.setLong("mStoredEnergy", Long.MAX_VALUE);
        NBTTagCompound original = (NBTTagCompound) payload.copy();
        HologramBlueprintResources.Plan plan = HologramBlueprintResources.plan(payload);
        SimpleAssert.eq(1, plan.items.size(), "same item identity aggregates");
        SimpleAssert.eq(5000, plan.items.get(0).stackSize, "GT int Count is not truncated through vanilla byte loader");
        SimpleAssert.eq(
            "precise tagged contents",
            plan.items.get(0)
                .getTagCompound()
                .getString("fixtureIdentity"),
            "inventory payment preserves exact item tags");
        SimpleAssert.that(
            BigInteger.valueOf(Long.MAX_VALUE)
                .equals(plan.energy),
            "stored EU is exact");
        plan.merge(HologramBlueprintResources.plan(payload));
        SimpleAssert.eq(10000, plan.items.get(0).stackSize, "merged cells sum inventory requirements");
        SimpleAssert.that(
            BigInteger.valueOf(Long.MAX_VALUE)
                .multiply(BigInteger.valueOf(2))
                .equals(plan.energy),
            "aggregate energy exceeds long without overflow");
        SimpleAssert.that(payload.equals(original), "cost inspection leaves placement state untouched");
        plan.items.get(0)
            .getTagCompound()
            .setString("fixtureIdentity", "mutated");
        SimpleAssert.that(payload.equals(original), "resource prototypes are detached from payload");
        NBTTagCompound ghostRow = diamond.writeToNBT(new NBTTagCompound());
        ghostRow.setByte("Count", (byte) 0);
        NBTTagList ghost = new NBTTagList();
        ghost.appendTag(ghostRow);
        NBTTagCompound ghostPayload = new NBTTagCompound();
        ghostPayload.setTag("Inventory", ghost);
        SimpleAssert.eq(
            0,
            HologramBlueprintResources.plan(ghostPayload).items.size(),
            "zero-count GT ghost circuits need no item");
    }

    private static void fluidCostsAndSelectedKeys() throws Exception {
        Fluid fluid = fixtureFluid("gtit_blueprint_resource_fixture");
        fixtureFluid("steam");
        FluidStack first = new FluidStack(fluid, 1250);
        NBTTagCompound payload = new NBTTagCompound();
        payload.setString("id", "fixture:tile");
        payload.setTag("mFluid", first.writeToNBT(new NBTTagCompound()));
        payload.setLong("mStoredSteam", 2000);
        payload.setLong("mStoredEnergy", 5000);
        HologramBlueprintResources.Plan plan = HologramBlueprintResources.plan(payload);
        SimpleAssert.eq(2, plan.fluids.size(), "tank fluid and GT stored steam both have costs");
        long steam = 0;
        for (HologramBlueprintResources.FluidCost cost : plan.fluids) {
            if (cost.stack.getFluid()
                .getName()
                .equals("steam")) steam = cost.amount;
            else {
                SimpleAssert.that(cost.amount == 1250, "tank amount exact");
                SimpleAssert.that(cost.stack.getFluid() == fluid, "fluid payment preserves registry identity");
            }
        }
        SimpleAssert.that(steam == 4000, "GT steam EU requires two mB per stored unit");
        String encoded = HologramBlueprintNbt.encodeCaptured(payload);
        NBTTagCompound selected = HologramBlueprintNbt.forPlacement(
            encoded,
            UUID.randomUUID(),
            "player",
            10,
            20,
            30,
            true,
            false,
            Arrays.asList("mStoredEnergy"));
        HologramBlueprintResources.Plan downgraded = HologramBlueprintResources.plan(selected);
        SimpleAssert.eq(0, downgraded.fluids.size(), "cancelled fluid keys neither restore nor charge contents");
        SimpleAssert.that(
            BigInteger.valueOf(5000)
                .equals(downgraded.energy),
            "selected energy remains paid");
        SimpleAssert.that(
            HologramBlueprintNbt.decode(encoded)
                .hasKey("mFluid"),
            "temporary downgrade preserves captured blueprint");
    }

    private static void taggedFluidRejection() throws Exception {
        FluidStack tagged = new FluidStack(fixtureFluid("gtit_blueprint_resource_fixture"), 1250);
        tagged.tag = new NBTTagCompound();
        tagged.tag.setString("grade", "a");
        NBTTagCompound taggedPayload = new NBTTagCompound();
        taggedPayload.setTag("mFluid", tagged.writeToNBT(new NBTTagCompound()));
        aeThat(!HologramMaterials.supportsFluidNbt(tagged), "resolved AE2 adapter does not preserve fluid tags");
        try {
            HologramBlueprintResources.plan(taggedPayload);
            throw new AssertionError("Unsupported tagged fluid must reject before placement");
        } catch (IOException expected) {
            aeThat(
                expected.getMessage()
                    .contains("mFluid"),
                "tagged fluid rejection identifies cancellable root key");
        }
    }

    private static void configurationSamples() throws Exception {
        NBTTagCompound recipe = new NBTTagCompound();
        recipe.setTag("ghostStack", new ItemStack(Items.diamond, 64).writeToNBT(new NBTTagCompound()));
        recipe.setTag(
            "fluid",
            new FluidStack(fixtureFluid("gtit_blueprint_resource_fixture"), 1000).writeToNBT(new NBTTagCompound()));
        NBTTagCompound payload = new NBTTagCompound();
        payload.setTag("recipe", recipe);
        payload.setTag("filter", recipe.copy());
        HologramBlueprintResources.Plan plan = HologramBlueprintResources.plan(payload);
        SimpleAssert
            .eq(0, plan.items.size(), "recipe/filter ghost items remain configuration without inventory payment");
        SimpleAssert.eq(0, plan.fluids.size(), "recipe/filter fluid samples remain configuration without tank payment");
        NBTTagList inventory = new NBTTagList();
        inventory.appendTag(new ItemStack(Items.diamond, 7).writeToNBT(new NBTTagCompound()));
        NBTTagCompound coverData = new NBTTagCompound();
        coverData.setTag("Inventory", inventory);
        payload.setTag("coverData", coverData);
        SimpleAssert.eq(
            7,
            HologramBlueprintResources.plan(payload).items.get(0).stackSize,
            "actual nested cover inventory still pays its entire stored quantity");
    }

    private static void rejectionBeforePayment() throws Exception {
        NBTTagCompound negative = new NBTTagCompound();
        negative.setLong("mStoredEnergy", -1);
        reject(negative, "mStoredEnergy");
        NBTTagCompound unknown = new NBTTagCompound();
        NBTTagCompound tank = new NBTTagCompound();
        tank.setLong("capacityUsed", 500);
        unknown.setTag("customTank", tank);
        reject(unknown, "customTank");
        NBTTagCompound malformed = new NBTTagCompound();
        NBTTagList list = new NBTTagList();
        list.appendTag(new NBTTagString("opaque serialized item"));
        malformed.setTag("Items", list);
        reject(malformed, "Items");
        NBTTagCompound missing = new ItemStack(Items.diamond).writeToNBT(new NBTTagCompound());
        missing.setBoolean("gtitBlueprintMissingItem", true);
        NBTTagCompound missingPayload = new NBTTagCompound();
        NBTTagList missingList = new NBTTagList();
        missingList.appendTag(missing);
        missingPayload.setTag("Items", missingList);
        reject(missingPayload, "Items");
    }

    private static void bulkPayment() throws Exception {
        HologramBlueprintResources.Plan plan = new HologramBlueprintResources.Plan();
        plan.addItems(Arrays.asList(new ItemStack(Items.diamond, 10000)));
        plan.energy = BigInteger.valueOf(5000);
        final int[] mutations = { 0 }, taken = { 0 };
        HologramBlueprintResources.Payment insufficient = new HologramBlueprintResources.Payment() {

            @Override
            public boolean item(ItemStack stack, boolean simulate) {
                if (!simulate) mutations[0]++;
                return true;
            }

            @Override
            public boolean fluid(FluidStack stack, long amount, boolean simulate) {
                return true;
            }

            @Override
            public boolean energy(BigInteger amount, boolean simulate) {
                return false;
            }
        };
        SimpleAssert.that(!plan.reserve(insufficient), "insufficient EU refuses payment");
        SimpleAssert.eq(0, mutations[0], "all resources simulate before any resource is removed");
        SimpleAssert.that(plan.reserve(new HologramBlueprintResources.Payment() {

            @Override
            public boolean item(ItemStack stack, boolean simulate) {
                if (!simulate) {
                    mutations[0]++;
                    taken[0] += stack.stackSize;
                }
                return true;
            }

            @Override
            public boolean fluid(FluidStack stack, long amount, boolean simulate) {
                return true;
            }

            @Override
            public boolean energy(BigInteger amount, boolean simulate) {
                return BigInteger.valueOf(5000)
                    .equals(amount);
            }
        }), "paid requirements reserve successfully");
        SimpleAssert.eq(1, mutations[0], "large slots use one bulk extraction rather than ten thousand takeOne calls");
        SimpleAssert.eq(10000, taken[0], "large slots consume the entire requirement");
        ItemStack hugeRefund = new ItemStack(Items.diamond, 200_000_000);
        NBTTagCompound receipt = HologramMaterials.itemReceipt(hugeRefund);
        SimpleAssert.eq(
            200_000_000,
            HologramMaterials.recoveryItem(receipt).stackSize,
            "large external item refunds use one exact int-count durable receipt");
        SimpleAssert.eq(
            7,
            HologramMaterials.recoveryItem(new ItemStack(Items.diamond, 7).writeToNBT(new NBTTagCompound())).stackSize,
            "legacy byte-count recovery receipts remain readable");
    }

    private static void exactBulkContext() throws Exception {
        java.lang.reflect.Method playerFactory = HologramRegressionTest.class.getDeclaredMethod("materialPlayer");
        playerFactory.setAccessible(true);
        net.minecraft.entity.player.EntityPlayerMP player = (net.minecraft.entity.player.EntityPlayerMP) playerFactory
            .invoke(null);
        player.inventory.mainInventory[1].getTagCompound()
            .setInteger("materials", 8192);
        java.lang.reflect.Method contextFactory = HologramRegressionTest.class
            .getDeclaredMethod("materialContext", net.minecraft.entity.player.EntityPlayerMP.class);
        contextFactory.setAccessible(true);
        HologramMaterials.Context context = (HologramMaterials.Context) contextFactory.invoke(null, player);
        SimpleAssert.that(
            context.take(stack -> true, false, 5000)
                .isEmpty(),
            "anonymous predicate extraction retains the original construction safety budget");
        HologramMaterials.Transaction transaction = context.begin();
        SimpleAssert.that(
            transaction.takeAll(new ItemStack(net.minecraft.init.Blocks.stone, 5000), false),
            "explicit exact resource identity permits one large transaction extraction");
        transaction.commit();
        SimpleAssert.eq(
            3192,
            player.inventory.mainInventory[1].getTagCompound()
                .getInteger("materials"),
            "large exact payment physically removes all five thousand items from container");
    }

    private static void fluidLedger() {
        FluidStack fluid = new FluidStack(fixtureFluid("gtit_blueprint_resource_fixture"), 1);
        FluidFixture monitor = new FluidFixture(fluid, 10000);
        List<IAEFluidStack> ledger = new ArrayList<>();
        monitor.live = false;
        aeThat(
            !HologramMaterials.reserveFluid(monitor, fluid, 3000, ledger::add),
            "removed or unauthorized terminal cannot extract");
        aeEq(0, monitor.extractions, "lost access never reaches monitor extraction");
        monitor.live = true;
        monitor.extractionLimit = 2000;
        aeThat(
            !HologramMaterials.reserveFluid(monitor, fluid, 3000, ledger::add),
            "partial real removal refuses placement");
        aeThat(
            ledger.get(0)
                .getStackSize() == 2000,
            "ledger records actual partial extraction");
        aeThat(monitor.stored == 8000, "fixture storage removes only actual returned quantity");
        IAEFluidStack left = ledger.get(0)
            .copy();
        monitor.powerLimit = 700;
        monitor.insertionLimit = 500;
        monitor.throwCharge = true;
        try {
            HologramMaterials.refundFluid(monitor, left);
            throw new AssertionError("fixture energy charge must fail after insertion");
        } catch (IllegalStateException expected) {
            aeThat(left.getStackSize() == 1500, "inserted fluid is removed from refund before charging failure");
        }
        NBTTagCompound receipt = HologramMaterials.fluidReceipt(left);
        aeThat(receipt.getLong("gtitRecoveryAmount") == 1500, "persistent receipt retains actual unpaid remainder");
        aeThat(
            fluid.isFluidEqual(FluidStack.loadFluidStackFromNBT(receipt)),
            "durable refund keeps exact fluid registry identity");
        monitor.throwCharge = false;
        monitor.powerLimit = Long.MAX_VALUE;
        monitor.insertionLimit = Long.MAX_VALUE;
        HologramMaterials.refundFluid(monitor, left);
        aeThat(
            left.getStackSize() == 0 && monitor.stored == 10000,
            "retry returns remaining fluid once, without duplication");
        monitor.extractionLimit = Long.MAX_VALUE;
        monitor.throwCharge = true;
        ledger.clear();
        try {
            HologramMaterials.reserveFluid(monitor, fluid, 3000, ledger::add);
            throw new AssertionError("fixture charging failure should propagate to service rollback");
        } catch (IllegalStateException expected) {
            aeThat(
                ledger.get(0)
                    .getStackSize() == 3000,
                "charge failure cannot lose extraction ledger");
        }
        IAEFluidStack huge = AEFluidStack.create(fluid);
        huge.setStackSize(5_000_000_000L);
        aeThat(
            HologramMaterials.fluidReceipt(huge)
                .getLong("gtitRecoveryAmount") == 5_000_000_000L,
            "durable AE fluid quantity is long, beyond Forge FluidStack int size");
    }

    private static final class FluidFixture implements HologramMaterials.FluidAccess {

        final FluidStack identity;
        long stored;
        long powerLimit = Long.MAX_VALUE, extractionLimit = Long.MAX_VALUE, insertionLimit = Long.MAX_VALUE;
        boolean live = true, throwCharge;
        int extractions;

        FluidFixture(FluidStack identity, long stored) {
            this.identity = identity.copy();
            this.stored = stored;
        }

        @Override
        public boolean available() {
            return live;
        }

        @Override
        public long poweredCount(IAEFluidStack stack) {
            return Math.min(powerLimit, stack.getStackSize());
        }

        @Override
        public IAEFluidStack extract(IAEFluidStack stack, Actionable mode) {
            extractions++;
            aeThat(identity.isFluidEqual(stack.getFluidStack()), "AE request keeps exact fluid identity");
            long amount = Math.min(stored, stack.getStackSize());
            if (mode == Actionable.MODULATE) {
                amount = Math.min(amount, extractionLimit);
                stored -= amount;
            }
            IAEFluidStack result = stack.copy();
            result.setStackSize(amount);
            return result;
        }

        @Override
        public IAEFluidStack inject(IAEFluidStack stack) {
            long accepted = Math.min(insertionLimit, stack.getStackSize());
            stored += accepted;
            IAEFluidStack left = stack.copy();
            left.setStackSize(stack.getStackSize() - accepted);
            return left.getStackSize() == 0 ? null : left;
        }

        @Override
        public void charge(long amount, int unit) {
            if (throwCharge) throw new IllegalStateException("fixture charge failure");
        }
    }

    private static void aeThat(boolean condition, String label) {
        aeAssertions++;
        SimpleAssert.that(condition, label);
    }

    private static void aeEq(Object expected, Object actual, String label) {
        aeAssertions++;
        SimpleAssert.eq(expected, actual, label);
    }

    private static Fluid fixtureFluid(String name) {
        Fluid result = FluidRegistry.getFluid(name);
        if (result == null) {
            result = new Fluid(name);
            FluidRegistry.registerFluid(result);
        }
        return result;
    }

    private static void reject(NBTTagCompound payload, String key) throws Exception {
        try {
            HologramBlueprintResources.plan(payload);
            throw new AssertionError("Unsupported resources must reject before placement");
        } catch (IOException expected) {
            SimpleAssert.that(
                expected.getMessage()
                    .contains(key),
                "rejection identifies cancellable root key");
        }
    }
}

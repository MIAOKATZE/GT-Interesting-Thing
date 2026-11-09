package com.miaokatze.gtit.testlab.scenario;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidRegistry;

import com.miaokatze.gtit.common.items.infinitycell.InfinityStackTypes;
import com.miaokatze.gtit.common.items.pocket.PocketAeChannelOps;
import com.miaokatze.gtit.common.items.pocket.PocketCarrierIdentity;
import com.miaokatze.gtit.common.items.pocket.PocketChannelOps;
import com.miaokatze.gtit.common.items.pocket.PocketFilterConfig;
import com.miaokatze.gtit.common.items.pocket.PocketRuntimeSession;

import appeng.api.config.AccessRestriction;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.data.IAEStack;

/** Fault contracts shared by the real FML lab and the small JVM runner. No production calls enter this class. */
public final class PocketInjectionChecks {

    private PocketInjectionChecks() {}

    private static final String ITEM = InfinityStackTypes.ITEM_STACK_TYPE.getId();
    private static final String FLUID = InfinityStackTypes.FLUID_STACK_TYPE.getId();

    private static final class Fixture {

        final EntityPlayerMP player;
        final ItemStack carrier;
        final PocketRuntimeSession runtime;
        long accepted;
        int calls;

        Fixture(EntityPlayerMP player, Supplier<ItemStack> factory) {
            this.player = player;
            java.util.Arrays.fill(player.inventory.mainInventory, null);
            player.inventory.setItemStack(null);
            carrier = factory.get();
            player.inventory.mainInventory[0] = carrier;
            runtime = new PocketRuntimeSession(
                () -> player,
                () -> player.inventory.mainInventory[0],
                0,
                carrier,
                false);
        }

        PocketChannelOps.SourceSlot itemSource() {
            return new PocketAeChannelOps(player, carrier, runtime).snapshotSources()
                .get(0);
        }

        PocketAeChannelOps ops(PocketRuntimeSession active, String type, int remainder, Runnable callback,
            boolean throwAfter, boolean invalid) throws Exception {
            PocketAeChannelOps ops = new PocketAeChannelOps(player, carrier, active);
            Object handler = Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[] { IMEInventoryHandler.class },
                (proxy, method, args) -> {
                    if (method.getName()
                        .equals("getAccess")) return AccessRestriction.READ_WRITE;
                    if (method.getName()
                        .equals("canAccept")) return true;
                    if (method.getName()
                        .equals("injectItems")) {
                        calls++;
                        IAEStack<?> request = (IAEStack<?>) args[0];
                        accepted += request.getStackSize() - remainder;
                        callback.run();
                        if (throwAfter) throw new IllegalStateException("injected target failure after accepting");
                        if (remainder == 0 && !invalid) return null;
                        IAEStack<?> result = request.copy();
                        result.setStackSize(invalid ? request.getStackSize() + 1 : remainder);
                        return result;
                    }
                    if (method.getReturnType() == boolean.class) return false;
                    if (method.getReturnType() == int.class) return 0;
                    return null;
                });
            Class<?> foundType = Class.forName(PocketAeChannelOps.class.getName() + "$Found");
            Constructor<?> ctor = foundType.getDeclaredConstructors()[0];
            ctor.setAccessible(true);
            set(ops, "cachedTick", (long) player.ticksExisted);
            set(ops, "cachedFoundId", "fixture");
            set(ops, "cachedFound", ctor.newInstance(carrier, null));
            set(ops, "cachedHandlerKey", "fixture#" + type);
            set(ops, "cachedHandler", handler);
            return ops;
        }

        long items() {
            long n = 0;
            for (int i = 0; i < runtime.storageSlots(); i++) {
                ItemStack stack = runtime.storageStackAt(i);
                if (stack != null && stack.getItem() == Items.iron_ingot) n += stack.stackSize;
            }
            return n;
        }
    }

    public static void run(EntityPlayerMP player, Supplier<ItemStack> carrierFactory, BiConsumer<String, Boolean> check)
        throws Exception {
        Fixture item = new Fixture(player, carrierFactory);
        item.runtime.setStorageStackAt(0, new ItemStack(Items.iron_ingot, 64));
        PocketChannelOps.SourceSlot source = item.itemSource();
        item.ops(item.runtime, ITEM, 0, () -> {
            ItemStack remaining = item.runtime.storageStackAt(0);
            if (remaining != null) {
                item.runtime.setStorageStackAt(1, remaining);
                item.runtime.setStorageStackAt(0, null);
            }
        }, false, false)
            .inject(source, "fixture", ITEM);
        check.accept(
            "item-callback-source-move-conserves",
            item.accepted == 64 && item.items() == 0 && !item.runtime.hasPendingOutgoingTransfer());
        Fixture fluid = new Fixture(player, carrierFactory);
        fluid.runtime.depositFluidL(0, FluidRegistry.WATER, 1000);
        long[] callbackTake = { 0 };
        fluid.ops(fluid.runtime, FLUID, 0, () -> callbackTake[0] += fluid.runtime.drainOwnTankL(0, 1000), false, false)
            .inject(
                new PocketChannelOps.SourceSlot(
                    0,
                    PocketFilterConfig.fluidKey("water"),
                    1000,
                    PocketChannelOps.SourceKind.FLUID),
                "fixture",
                FLUID);
        check.accept(
            "fluid-callback-source-drain-conserves",
            fluid.accepted == 1000 && callbackTake[0] == 0
                && fluid.runtime.inventory()
                    .tankTruthAt(0) == 0
                && !fluid.runtime.hasPendingOutgoingTransfer());
        Fixture partial = new Fixture(player, carrierFactory);
        partial.runtime.setStorageStackAt(0, new ItemStack(Items.iron_ingot, 64));
        source = partial.itemSource();
        partial.ops(partial.runtime, ITEM, 24, () -> {
            player.inventory.mainInventory[1] = partial.carrier.copy();
            player.inventory.mainInventory[0] = null;
        }, false, false)
            .inject(source, "fixture", ITEM);
        check.accept(
            "partial-with-carrier-relocation",
            partial.accepted == 40 && partial.items() == 24
                && !partial.runtime.hasPendingOutgoingTransfer()
                && !partial.runtime.inventory()
                    .isDirty()
                && !player.inventory.mainInventory[1].getTagCompound()
                    .hasKey("gtitPocketOutgoingTransfer"));
        Fixture unknown = new Fixture(player, carrierFactory);
        unknown.runtime.setStorageStackAt(0, new ItemStack(Items.iron_ingot, 64));
        source = unknown.itemSource();
        unknown.ops(unknown.runtime, ITEM, 0, () -> {}, true, false)
            .inject(source, "fixture", ITEM);
        check.accept(
            "target-throw-isolates-without-refund",
            unknown.accepted == 64 && unknown.items() == 0 && unknown.runtime.hasPendingOutgoingTransfer());
        NBTTagCompound receipt = unknown.carrier.getTagCompound()
            .getCompoundTag("gtitPocketOutgoingTransfer");
        String token = receipt.getString("token");
        PocketRuntimeSession loaded = new PocketRuntimeSession(
            () -> player,
            () -> player.inventory.mainInventory[0],
            0,
            unknown.carrier,
            false);
        loaded.setStorageStackAt(0, new ItemStack(Items.iron_ingot, 16));
        unknown.ops(loaded, ITEM, 0, () -> {}, false, false)
            .inject(source, "fixture", ITEM);
        check.accept(
            "saved-unknown-rejects-replay",
            unknown.calls == 1 && loaded.storageStackAt(0).stackSize == 16
                && loaded.outgoingTransferDiagnostic()
                    .contains(token));
        boolean wrongRejected = false;
        try {
            loaded.acknowledgeOutgoingConsumed(
                java.util.UUID.randomUUID()
                    .toString(),
                "fixture");
        } catch (IllegalStateException expected) {
            wrongRejected = true;
        }
        loaded.acknowledgeOutgoingConsumed(token, "fixture-level4");
        check.accept(
            "exact-consumed-adjudication-no-refund",
            wrongRejected && !loaded.hasPendingOutgoingTransfer()
                && loaded.storageStackAt(0).stackSize == 16
                && unknown.carrier.getTagCompound()
                    .getCompoundTag("gtitPocketOutgoingTransferLastResolution")
                    .getString("token")
                    .equals(token));
        Fixture invalid = new Fixture(player, carrierFactory);
        invalid.runtime.setStorageStackAt(0, new ItemStack(Items.iron_ingot, 64));
        source = invalid.itemSource();
        invalid.ops(invalid.runtime, ITEM, 0, () -> {}, false, true)
            .inject(source, "fixture", ITEM);
        check.accept(
            "invalid-response-isolates-without-refund",
            invalid.accepted == 64 && invalid.items() == 0 && invalid.runtime.hasPendingOutgoingTransfer());
        Fixture full = new Fixture(player, carrierFactory);
        full.runtime.setStorageStackAt(0, new ItemStack(Items.iron_ingot, 64));
        source = full.itemSource();
        full.ops(full.runtime, ITEM, 24, () -> {
            for (int slot = 0; slot < full.runtime.storageSlots(); slot++)
                full.runtime.setStorageStackAt(slot, new ItemStack(Items.gold_ingot, 64));
        }, false, false)
            .inject(source, "fixture", ITEM);
        check.accept(
            "known-refund-bounded-until-room",
            full.accepted == 40 && full.items() == 0
                && full.runtime.hasPendingOutgoingTransfer()
                && full.carrier.getTagCompound()
                    .getCompoundTag("gtitPocketOutgoingTransfer")
                    .getLong("amount") == 24);
        PocketRuntimeSession refund = new PocketRuntimeSession(
            () -> player,
            () -> player.inventory.mainInventory[0],
            0,
            full.carrier,
            false);
        refund.setStorageStackAt(0, null);
        refund.persistFinal();
        check.accept(
            "known-refund-restores-once-after-reload",
            !refund.hasPendingOutgoingTransfer() && refund.storageStackAt(0).stackSize == 24);
        refund.persistFinal();
        check.accept("refund-repeat-does-not-duplicate", refund.storageStackAt(0).stackSize == 24);
        Fixture revoked = new Fixture(player, carrierFactory);
        revoked.runtime.setStorageStackAt(0, new ItemStack(Items.iron_ingot, 64));
        source = revoked.itemSource();
        revoked.ops(revoked.runtime, ITEM, 24, () -> PocketCarrierIdentity.capture(revoked.carrier, true), false, false)
            .inject(source, "fixture", ITEM);
        PocketRuntimeSession authority = new PocketRuntimeSession(
            () -> player,
            () -> player.inventory.mainInventory[0],
            0,
            revoked.carrier,
            false);
        check.accept(
            "revoked-writer-cannot-replay-or-refund-unknown",
            revoked.accepted == 40 && revoked.runtime.inventory()
                .isDirty() && authority.hasPendingOutgoingTransfer() && authority.storageStackAt(0) == null);
    }

    public static int runEssence(EntityPlayerMP player, Supplier<ItemStack> carrierFactory,
        BiConsumer<String, Boolean> check) throws Exception {
        int tested = 0;
        for (appeng.api.storage.data.IAEStackType<?> type : InfinityStackTypes.allSupportedTypes()) {
            IAEStack<?> probe = com.miaokatze.gtit.common.items.pocket.EssenceNativeChannels.nativeProbe(type, "aer");
            if (probe == null || probe.getStackSize() <= 0) continue;
            String id = type.getId();
            Fixture fixture = new Fixture(player, carrierFactory);
            fixture.runtime.essence()
                .add("aer", 64);
            long[] callbackTake = { 0 };
            fixture
                .ops(
                    fixture.runtime,
                    id,
                    0,
                    () -> callbackTake[0] += fixture.runtime.drainEssence("aer", 64),
                    false,
                    false)
                .inject(
                    new PocketChannelOps.SourceSlot(
                        -1,
                        PocketFilterConfig.essenceKey(id, "aer"),
                        64,
                        PocketChannelOps.SourceKind.ESSENCE),
                    "fixture",
                    id);
            check.accept(
                "essence-callback-conserves-" + id,
                fixture.accepted == probe.getStackSize() * 8 && callbackTake[0] == 0
                    && fixture.runtime.essence()
                        .get("aer") == 0
                    && !fixture.runtime.hasPendingOutgoingTransfer());
            Fixture unknown = new Fixture(player, carrierFactory);
            unknown.runtime.essence()
                .add("aer", 64);
            unknown.ops(unknown.runtime, id, 0, () -> {}, true, false)
                .inject(
                    new PocketChannelOps.SourceSlot(
                        -1,
                        PocketFilterConfig.essenceKey(id, "aer"),
                        64,
                        PocketChannelOps.SourceKind.ESSENCE),
                    "fixture",
                    id);
            check.accept(
                "essence-throw-isolates-" + id,
                unknown.runtime.essence()
                    .get("aer") == 0 && unknown.runtime.hasPendingOutgoingTransfer());
            tested++;
        }
        return tested;
    }

    private static void set(Object owner, String name, Object value) throws Exception {
        Field field = PocketAeChannelOps.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(owner, value);
    }
}

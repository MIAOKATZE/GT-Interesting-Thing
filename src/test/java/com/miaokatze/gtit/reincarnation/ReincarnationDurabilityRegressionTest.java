package com.miaokatze.gtit.reincarnation;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraft.world.WorldProviderSurface;
import net.minecraft.world.WorldServer;
import net.minecraft.world.storage.MapStorage;
import net.minecraft.world.storage.SaveHandler;
import net.minecraft.world.storage.WorldInfo;

import com.miaokatze.gtit.reincarnation.core.ReincarnationCycle;
import com.miaokatze.gtit.reincarnation.core.ReincarnationStore;
import com.miaokatze.gtit.reincarnation.core.ReincarnationWorldDeletion;
import com.miaokatze.gtit.reincarnation.storage.ReincarnationMigration;
import com.miaokatze.gtit.reincarnation.storage.ReincarnationWorldData;
import com.miaokatze.gtit.testutil.SimpleAssert;
import com.miaokatze.gtit.testutil.TestRunner;

/** Real Minecraft MapStorage, SaveHandler and compressed NBT; only the live world is allocated without ticking. */
public final class ReincarnationDurabilityRegressionTest {

    static final String ID = "11111111-2222-3333-4444-555555555555";

    public static void main(String[] args) {
        Map<String, Runnable> cases = new LinkedHashMap<>();
        cases.put("nativeMapStorageSwallowsIoAndClearsDirty", () -> checked(() -> nativeFailure()));
        cases.put("immediateFailureThrowsAndRetainsDirty", () -> checked(() -> immediateFailure()));
        cases.put("compressedEnvelopeReloadsAndLaterNativeSaveWorks", () -> checked(() -> envelope()));
        cases.put("failedReceiverWriteNeverConsumesLegacy", () -> checked(() -> migrationFailure()));
        cases.put("receiptWithoutConsumeDoesNotReplayIntoOtherWorld", () -> checked(() -> interruptedMigration(false)));
        cases.put("receiptWithoutConsumeFinalizesOnSameWorldRetry", () -> checked(() -> interruptedMigration(true)));
        cases.put("grantMarkerCannotDestroyUnmigratedLegacy", () -> checked(() -> unscopedGrantWrite()));
        cases.put("reservationFailureLeavesReceiverUnimported", () -> checked(() -> reservationFailure()));
        cases.put("reservationWithoutReceiptIsRetryableAfterRename", () -> checked(() -> interruptedReceiver(true)));
        cases.put(
            "reservationWithoutReceiptCannotReplayIntoOtherWorld",
            () -> checked(() -> interruptedReceiver(false)));
        cases.put("failedReceiptCanRetryInSameSession", () -> checked(() -> sameSessionReceiptRetry()));
        cases.put("temporarySymlinkCannotRedirectCheckpoint", () -> checked(() -> temporarySymlink()));
        TestRunner.run(ReincarnationDurabilityRegressionTest.class, cases);
    }

    private static void nativeFailure() throws Exception {
        withDir(base -> {
            World world = world(base, "save");
            ReincarnationWorldData data = ReincarnationWorldData.get(world);
            data.setGrantStarted(true);
            Files.createDirectory(
                world.getSaveHandler()
                    .getMapFileFromName(ReincarnationWorldData.DATA_NAME)
                    .toPath());
            world.mapStorage.saveAllData();
            SimpleAssert.that(!data.isDirty(), "native API cleared dirty despite failed write (control)");
        });
    }

    private static void immediateFailure() throws Exception {
        withDir(base -> {
            World world = world(base, "save");
            ReincarnationWorldData data = ReincarnationWorldData.get(world);
            data.setGrantStarted(true);
            Files.createDirectory(
                world.getSaveHandler()
                    .getMapFileFromName(ReincarnationWorldData.DATA_NAME)
                    .toPath());
            boolean threw = false;
            try {
                data.saveImmediately(world);
            } catch (IllegalStateException expected) {
                threw = true;
            }
            SimpleAssert.that(threw, "failed durable write must throw");
            SimpleAssert.that(data.isDirty(), "failed durable write keeps retry dirty");
        });
    }

    private static void envelope() throws Exception {
        withDir(base -> {
            World world = world(base, "save");
            ReincarnationWorldData data = ReincarnationWorldData.get(world);
            data.setGrantStarted(true);
            data.setUnlockedRows(2);
            data.saveImmediately(world);
            SimpleAssert.that(!data.isDirty(), "successful direct flush clears dirty");
            File file = world.getSaveHandler()
                .getMapFileFromName(ReincarnationWorldData.DATA_NAME);
            try (java.io.FileInputStream in = new java.io.FileInputStream(file)) {
                NBTTagCompound root = CompressedStreamTools.readCompressed(in);
                SimpleAssert.that(
                    root.getCompoundTag("data")
                        .getBoolean("GrantStarted"),
                    "native data envelope");
            }
            ReincarnationWorldData reloaded = reload(world);
            SimpleAssert.that(reloaded.isGrantStarted(), "new native MapStorage reloads receipt");
            SimpleAssert.eq(2, reloaded.getUnlockedRows(), "all progress is preserved");
            data.setUnlockedRows(3);
            world.mapStorage.saveAllData();
            SimpleAssert.eq(3, reload(world).getUnlockedRows(), "later native dirty save remains compatible");
        });
    }

    private static void migrationFailure() throws Exception {
        withDir(base -> {
            ReincarnationStore store = legacy(base, false);
            World world = world(base, "save");
            Files.createDirectory(
                world.getSaveHandler()
                    .getMapFileFromName(ReincarnationWorldData.DATA_NAME)
                    .toPath());
            try {
                ReincarnationMigration.migrateOnce(store, ReincarnationWorldData.get(world), ID, world);
            } catch (IllegalStateException expected) {}
            SimpleAssert.that(store.readLegacyV1(ID) != null, "source must survive failed receiver write");
        });
    }

    private static void interruptedMigration(boolean sameWorld) throws Exception {
        withDir(base -> {
            ReincarnationStore store = legacy(base, false);
            World world = world(base, "first");
            BlockingReceipt data = new BlockingReceipt(
                store.getFile()
                    .toPath()
                    .resolveSibling("reincarnation.dat.tmp"));
            world.mapStorage.setData(ReincarnationWorldData.DATA_NAME, data);
            try {
                ReincarnationMigration.migrateOnce(store, data, ID, world);
            } catch (IllegalStateException expected) {}
            SimpleAssert.that(reload(world).isMigrated(), "receiver receipt was saved before injected consume failure");
            SimpleAssert.that(store.readLegacyV1(ID) != null, "source remains v1 after injected consume failure");
            if (sameWorld) {
                ReincarnationWorldDeletion.deleteTree(data.blocker);
                // Fresh storage instance simulates restart, not merely a cached boolean.
                world.mapStorage = new MapStorage(world.getSaveHandler());
                ReincarnationMigration.migrateOnce(store, ReincarnationWorldData.get(world), ID, world);
                SimpleAssert.eq(null, store.readLegacyV1(ID), "same receiver finalizes source after crash");
                SimpleAssert.eq(
                    9,
                    ReincarnationWorldData.get(world)
                        .getHullProgress()[0],
                    "no duplicate merge");
            } else {
                World other = world(base, "second");
                ReincarnationWorldData otherData = ReincarnationWorldData.get(other);
                try {
                    ReincarnationMigration.migrateOnce(store, otherData, ID, other);
                } catch (IllegalStateException expected) {}
                SimpleAssert.eq(0, otherData.getHullProgress()[0], "reserved source cannot replay into second world");
            }
        });
    }

    private static void unscopedGrantWrite() throws Exception {
        withDir(base -> {
            ReincarnationStore store = legacy(base, true);
            try {
                store.markGrantInFlight(ID);
            } catch (IllegalStateException expected) {}
            SimpleAssert.that(store.readLegacyV1(ID) != null, "grant write must not discard old per-world progress");
        });
    }

    private static void reservationFailure() throws Exception {
        withDir(base -> {
            ReincarnationStore store = legacy(base, false);
            Path blocker = store.getFile()
                .toPath()
                .resolveSibling("reincarnation.dat.tmp");
            Files.createDirectory(blocker);
            Files.write(blocker.resolve("block"), new byte[] { 1 });
            World world = world(base, "save");
            ReincarnationWorldData data = ReincarnationWorldData.get(world);
            try {
                ReincarnationMigration.migrateOnce(store, data, ID, world);
            } catch (IllegalStateException expected) {}
            SimpleAssert.eq(0, data.getHullProgress()[0], "no receiver import before source reservation succeeds");
            SimpleAssert.that(!data.isMigrated(), "reservation failure cannot issue a receipt");
            SimpleAssert.that(store.readLegacyV1(ID) != null, "failed reservation leaves source intact");
        });
    }

    private static void interruptedReceiver(boolean rename) throws Exception {
        withDir(base -> {
            ReincarnationStore store = legacy(base, false);
            World first = world(base, "first");
            RejectReceipt data = new RejectReceipt();
            first.mapStorage.setData(ReincarnationWorldData.DATA_NAME, data);
            try {
                ReincarnationMigration.migrateOnce(store, data, ID, first);
            } catch (IllegalStateException expected) {}
            SimpleAssert.that(store.readLegacyV1(ID) != null, "source retained before receiver receipt");
            if (rename) {
                Files.move(base.resolve("first"), base.resolve("renamed"));
                World renamed = world(base, "renamed");
                ReincarnationMigration.migrateOnce(store, ReincarnationWorldData.get(renamed), ID, renamed);
                SimpleAssert.eq(null, store.readLegacyV1(ID), "reserved owner retries after world directory rename");
                SimpleAssert.eq(9, reload(renamed).getHullProgress()[0], "renamed receiver imports once");
            } else {
                World second = world(base, "second");
                ReincarnationWorldData other = ReincarnationWorldData.get(second);
                try {
                    ReincarnationMigration.migrateOnce(store, other, ID, second);
                } catch (IllegalStateException expected) {}
                SimpleAssert
                    .eq(0, other.getHullProgress()[0], "reservation fences another world before receipt exists");
            }
        });
    }

    private static void sameSessionReceiptRetry() throws Exception {
        withDir(base -> {
            ReincarnationStore store = legacy(base, false);
            World world = world(base, "save");
            RejectReceipt data = new RejectReceipt();
            world.mapStorage.setData(ReincarnationWorldData.DATA_NAME, data);
            try {
                ReincarnationMigration.migrateOnce(store, data, ID, world);
            } catch (IllegalStateException expected) {}
            data.reject = false;
            ReincarnationMigration.migrateOnce(store, data, ID, world);
            SimpleAssert.eq(null, store.readLegacyV1(ID), "in-memory Migrated cannot skip retrying failed checkpoint");
            SimpleAssert.eq(9, reload(world).getHullProgress()[0], "receipt really exists before source consumed");
        });
    }

    private static void temporarySymlink() throws Exception {
        withDir(base -> {
            ReincarnationStore store = new ReincarnationStore(base.toFile());
            ReincarnationCycle cycle = new ReincarnationCycle(ID);
            store.save(cycle);
            byte[] original = Files.readAllBytes(
                store.getFile()
                    .toPath());
            Path sentinel = Files.write(base.resolve("other-world-sentinel"), new byte[] { 7, 8, 9 });
            Files.createSymbolicLink(
                store.getFile()
                    .toPath()
                    .resolveSibling("reincarnation.dat.tmp"),
                sentinel);
            cycle.unlockColumn(0);
            boolean refused = false;
            try {
                store.save(cycle);
            } catch (IllegalStateException expected) {
                refused = true;
            }
            SimpleAssert.that(refused, "temporary symlink is rejected instead of followed");
            SimpleAssert.that(
                java.util.Arrays.equals(new byte[] { 7, 8, 9 }, Files.readAllBytes(sentinel)),
                "unrelated target preserved");
            SimpleAssert.that(
                java.util.Arrays.equals(
                    original,
                    Files.readAllBytes(
                        store.getFile()
                            .toPath())),
                "previous checkpoint preserved");
        });
    }

    private static final class RejectReceipt extends ReincarnationWorldData {

        boolean reject = true;

        RejectReceipt() {
            super(DATA_NAME);
        }

        @Override
        public void saveImmediately(World world) {
            if (reject && isMigrated()) throw new IllegalStateException("Injected receiver checkpoint failure");
            super.saveImmediately(world);
        }
    }

    static ReincarnationStore legacy(Path base, boolean executed) {
        int[] hull = new int[15];
        hull[0] = 9;
        ReincarnationStore.writeLegacyV1Fixture(
            base.toFile(),
            ID,
            executed ? ReincarnationCycle.CycleState.EXECUTED : ReincarnationCycle.CycleState.DEPOSITED,
            Collections.singletonList(new ReincarnationCycle.ItemRef("minecraft:diamond", 0)),
            hull,
            new boolean[15],
            2,
            Collections.<String>emptySet());
        return new ReincarnationStore(base.toFile());
    }

    private static final class BlockingReceipt extends ReincarnationWorldData {

        final Path blocker;

        BlockingReceipt(Path blocker) {
            super(DATA_NAME);
            this.blocker = blocker;
        }

        @Override
        public void saveImmediately(World world) {
            super.saveImmediately(world);
            if (isMigrated()) {
                try {
                    Files.createDirectories(blocker);
                    Files.write(blocker.resolve("block"), new byte[] { 1 });
                } catch (java.io.IOException failure) {
                    throw new IllegalStateException(failure);
                }
            }
        }
    }

    static World world(Path base, String name) throws Exception {
        return world(base, name, WorldServer.class);
    }

    static World world(Path base, String name, Class<? extends WorldServer> type) throws Exception {
        SaveHandler handler = new SaveHandler(base.toFile(), name, false);
        World world = allocate(type);
        field(World.class, "saveHandler").set(world, handler);
        world.mapStorage = new MapStorage(handler);
        field(World.class, "worldInfo").set(world, new WorldInfo(new NBTTagCompound()));
        WorldProviderSurface provider = new WorldProviderSurface();
        provider.worldObj = world;
        provider.dimensionId = 0;
        field(World.class, "provider").set(world, provider);
        return world;
    }

    static ReincarnationWorldData reload(World world) {
        return (ReincarnationWorldData) new MapStorage(world.getSaveHandler())
            .loadData(ReincarnationWorldData.class, ReincarnationWorldData.DATA_NAME);
    }

    static Field field(Class<?> owner, String name) throws Exception {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    static <T> T allocate(Class<T> type) throws Exception {
        Class<?> unsafe = Class.forName("sun.misc.Unsafe");
        Object instance = field(unsafe, "theUnsafe").get(null);
        return type.cast(
            unsafe.getMethod("allocateInstance", Class.class)
                .invoke(instance, type));
    }

    static void checked(Checked action) {
        try {
            action.run();
        } catch (Exception failure) {
            throw new RuntimeException(failure);
        }
    }

    private static void withDir(DirectoryCase action) throws Exception {
        Path path = Files.createTempDirectory("gtit-durability-");
        try {
            action.run(path);
        } finally {
            ReincarnationWorldDeletion.deleteTree(path);
        }
    }

    interface Checked {

        void run() throws Exception;
    }

    private interface DirectoryCase {

        void run(Path base) throws Exception;
    }
}

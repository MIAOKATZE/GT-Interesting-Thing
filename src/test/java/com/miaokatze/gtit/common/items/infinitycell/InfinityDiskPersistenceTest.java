// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtit.common.items.infinitycell;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import com.miaokatze.gtit.testutil.NativeAeTestBootstrap;
import com.miaokatze.gtit.testutil.SimpleAssert;

import appeng.api.config.Actionable;
import appeng.api.storage.StorageChannel;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.util.item.AEFluidStack;
import appeng.util.item.AEItemStack;

/** Native AE and Minecraft NBT, with a fresh disk directory for every run. */
public final class InfinityDiskPersistenceTest {

    private static final UUID A = UUID.fromString("12344100-0000-0000-0000-000000000001");
    private static final UUID B = UUID.fromString("12344100-0000-0000-0000-000000000002");
    private static final String MISSING_CHANNEL = "gtit-test:preserve_opaque_payload";
    private static final String EMPTY_CHANNEL = "gtit-test:preserve_empty_opaque_payload";

    public static void main(String[] args) throws Exception {
        if (!NativeAeTestBootstrap.enter(InfinityDiskPersistenceTest.class, args)) return;
        cpw.mods.fml.server.FMLServerHandler.instance();
        Thread.currentThread()
            .setName("Server thread");
        final Path directory = Files.createTempDirectory(Paths.get("."), "snapshot-");
        final String copiedSave = System.getenv("GTIT_LEGACY_WORLD_FIXTURE");
        if (copiedSave != null) verifyExistingSaveCopy(directory, Paths.get(copiedSave));
        verifyIncrementalCommit(directory);
        verifyBackgroundCommit(Files.createDirectory(directory.resolve("background")));
        verifyLargeDiskDelta(Files.createDirectory(directory.resolve("small-delta")), 32);
        final String benchmarkSize = System.getenv("GTIT_INFINITY_BENCHMARK_ENTRIES");
        verifyLargeDiskDelta(
            Files.createDirectory(directory.resolve("large-delta")),
            benchmarkSize == null ? 4096 : Integer.parseInt(benchmarkSize));
        System.out.println(
            "InfinityDiskPersistenceTest: automatic migration, dirty UUIDs, interrupted commit and retry passed");
    }

    private static void verifyBackgroundCommit(Path root) throws Exception {
        final Path old = root.resolve("GTIT_InfinityCellStorage.dat");
        writeLegacyFixture(old);
        final StorageManager manager = StorageManager.open(old);
        final Path directory = root.resolve(StorageManager.storageName());
        final Path manifest = directory.resolve("manifest.dat");
        final ExecutorService worker = Executors.newSingleThreadExecutor();
        final CountDownLatch release = new CountDownLatch(1);
        worker.submit(() -> {
            SimpleAssert
                .that(release.await(10, TimeUnit.SECONDS), "the routine save returned without joining the worker");
            return null;
        });
        manager.startBackgroundWrites(worker);
        try {
            final NBTTagCompound initial = revisions(manifest);
            itemInventory(A).injectItems(iron(3), Actionable.MODULATE, null);
            manager.requestSave();
            SimpleAssert.eq(initial, revisions(manifest), "routine save returns while the writer is blocked");

            // An unregistered channel shares a tag with the live manager's legacy view.
            final NBTTagCompound view = new NBTTagCompound();
            manager.writeToNBT(view);
            InfinityLegacyData.read(view)
                .get(A)
                .getTagList(MISSING_CHANNEL, 10)
                .getCompoundTagAt(0)
                .setLong("amount", 99);
            itemInventory(A).injectItems(iron(9), Actionable.MODULATE, null);
            manager.requestSave();
            release.countDown();
            worker.submit(() -> {})
                .get(10, TimeUnit.SECONDS);

            final NBTTagCompound first = new InfinityDiskFiles(directory).load()
                .get(A);
            SimpleAssert.eq(
                67L,
                AEItemStack.loadItemStackFromNBT(
                    first.getTagList("item", 10)
                        .getCompoundTagAt(0))
                    .getStackSize(),
                "the submitted generation has the earlier item count");
            SimpleAssert.eq(opaque(), first.getTagList(MISSING_CHANNEL, 10), "the older snapshot owns its NBT");

            manager.saveDisks();
            final NBTTagCompound secondDisk = new InfinityDiskFiles(directory).load()
                .get(A);
            SimpleAssert.eq(
                76L,
                AEItemStack.loadItemStackFromNBT(
                    secondDisk.getTagList("item", 10)
                        .getCompoundTagAt(0))
                    .getStackSize(),
                "explicit flush also commits newer changes to an in-flight UUID");
            SimpleAssert.eq(opaque(), secondDisk.getTagList(MISSING_CHANNEL, 10), "the unchanged opaque data survives");
            SimpleAssert.that(secondDisk.hasKey(EMPTY_CHANNEL, 9), "an opaque empty channel keeps its tag");
            manager.postChanges(manager.getStorage(A.toString(), StorageChannel.ITEMS));
            manager.saveDisks();
            final NBTTagCompound second = revisions(manifest);
            SimpleAssert.eq(
                99L,
                new InfinityDiskFiles(directory).load()
                    .get(A)
                    .getTagList(MISSING_CHANNEL, 10)
                    .getCompoundTagAt(0)
                    .getLong("amount"),
                "a compatibility full-refresh honors edits to the legacy view");
            SimpleAssert
                .eq(revision(initial, B), revision(second, B), "unchanged disk keeps its revision in both batches");

            final Path held = directory.resolve("test-held-manifest.dat");
            Files.move(manifest, held);
            Files.createDirectory(manifest);
            try {
                itemInventory(A).injectItems(iron(4), Actionable.MODULATE, null);
                manager.requestSave();
                itemInventory(A).injectItems(iron(1), Actionable.MODULATE, null);
                manager.saveDisks();
                throw new AssertionError("explicit flush must report the background commit failure");
            } catch (IOException expected) {
                SimpleAssert.that(manager.isDirty(), "failed background generation becomes dirty again");
            } finally {
                Files.delete(manifest);
                Files.move(held, manifest);
            }
            SimpleAssert.eq(second, revisions(manifest), "failed background write leaves the previous commit visible");
            transferToB();
            manager.requestSave();
        } finally {
            release.countDown();
            StorageManager.closeWorld();
        }
        SimpleAssert.that(worker.isShutdown(), "closing the world shuts down its worker");
        verifyContents(StorageManager.open(old), 61, 27);
        System.out
            .println("InfinityDiskPersistenceTest: detached background batches, failed flush, retry and close passed");
    }

    private static void verifyLargeDiskDelta(Path root, int size) throws Exception {
        final Path old = root.resolve("GTIT_InfinityCellStorage.dat");
        final Path directory = root.resolve(StorageManager.storageName());
        final NBTTagList entries = new NBTTagList();
        for (int i = 0; i < size; i++) {
            final NBTTagCompound tag = new NBTTagCompound();
            ironWithIdentity(i, 1).writeToNBT(tag);
            entries.appendTag(tag);
        }
        final NBTTagCompound seed = new NBTTagCompound();
        seed.setTag("item", entries);
        new InfinityDiskFiles(directory).save(Collections.singletonMap(A, seed));
        final StorageManager manager = StorageManager.open(old);
        final InfinityItemStorageCellInventory cell = itemInventory(A);
        final IAEItemStack first = ironWithIdentity(0, 1);
        cell.injectItems(first, Actionable.SIMULATE, null);
        SimpleAssert.that(
            manager.captureDirtyDisks()
                .isEmpty(),
            "simulation creates no tracked entry");
        cell.extractItems(first, Actionable.MODULATE, null);
        // A normal AE enumeration can remove the depleted record before the same type is reinserted.
        manager.getStorage(A.toString(), StorageChannel.ITEMS)
            .writeContents();
        cell.injectItems(ironWithIdentity(0, 5), Actionable.MODULATE, null);
        cell.injectItems(first, Actionable.MODULATE, null);

        final long[] times = new long[101];
        for (int i = 0; i < times.length; i++) {
            final long start = System.nanoTime();
            final InfinityDiskSnapshots.Update update = manager.captureDirtyDisks()
                .get(A);
            times[i] = System.nanoTime() - start;
            SimpleAssert.that(!update.replace && update.channels.size() == 1, "only the touched channel is captured");
            SimpleAssert.eq(
                1,
                update.channels.get("item")
                    .size(),
                "the pulse serializes one precise entry");
        }
        Arrays.sort(times);
        final long start = System.nanoTime();
        manager.saveDisks();
        final long elapsed = System.nanoTime() - start;
        final String revision = revision(revisions(directory.resolve("manifest.dat")), A);
        final long bytes = Files.size(directory.resolve(A + "." + revision + ".dat"));
        final NBTTagList written = new InfinityDiskFiles(directory).load()
            .get(A)
            .getTagList("item", 10);
        SimpleAssert.eq(size, written.tagCount(), "the rebuilt disk retains every unchanged entry with no duplicate");
        cell.extractItems(ironWithIdentity(0, 6), Actionable.MODULATE, null);
        final Map<Object, NBTTagCompound> deletion = manager.captureDirtyDisks()
            .get(A).channels.get("item");
        SimpleAssert.that(deletion.size() == 1 && deletion.containsValue(null), "zero becomes one deletion");
        manager.saveDisks();
        final StorageManager reopened = StorageManager.open(old);
        SimpleAssert.eq(
            null,
            reopened.getStorage(A.toString(), StorageChannel.ITEMS)
                .getItems()
                .findPrecise(first),
            "removed entry stays absent after restart");
        SimpleAssert.eq(
            1L,
            reopened.getStorage(A.toString(), StorageChannel.ITEMS)
                .getItems()
                .findPrecise(ironWithIdentity(1, 1))
                .getStackSize(),
            "NBT-distinct entry is untouched");
        System.out.printf(
            "Infinity disk delta: entries=%d encoded=1 captureMedianUs=%.3f captureP95Us=%.3f commitWallMs=%.3f compressedBytes=%d%n",
            size,
            times[50] / 1000.0,
            times[95] / 1000.0,
            elapsed / 1000000.0,
            bytes);
    }

    private static void verifyExistingSaveCopy(Path root, Path source) throws Exception {
        final Path old = Files.createDirectory(root.resolve("existing"))
            .resolve("GTIT_InfinityCellStorage.dat");
        Files.copy(source, old);
        final byte[] before = Files.readAllBytes(old);
        StorageManager.open(old);
        final Path manifest = old.resolveSibling(StorageManager.storageName())
            .resolve("manifest.dat");
        SimpleAssert.that(Files.isRegularFile(manifest), "copied legacy WorldSavedData commits a manifest");
        StorageManager.open(old);
        SimpleAssert.that(Files.notExists(old), "the migrated copy is retired after commit");
        SimpleAssert.that(Arrays.equals(before, Files.readAllBytes(source)), "source fixture remains untouched");
        System.out.println("InfinityDiskPersistenceTest: existing world file copy migrated and reopened");
    }

    private static void verifyIncrementalCommit(Path root) throws Exception {
        final Path old = root.resolve("GTIT_InfinityCellStorage.dat");
        final byte[] original = writeLegacyFixture(old);
        final Path directory = root.resolve(StorageManager.storageName());
        final Path manifest = directory.resolve("manifest.dat");
        final InfinityDiskFiles incompleteImport = new InfinityDiskFiles(directory);
        incompleteImport.stage(
            InfinityLegacyData.read(
                InfinityDiskFiles.read(old)
                    .getCompoundTag("data")));
        SimpleAssert.that(Files.notExists(manifest), "staging alone does not publish the migration");
        SimpleAssert.that(Arrays.equals(original, Files.readAllBytes(old)), "interrupted migration retains old data");

        StorageManager manager = StorageManager.open(old);
        verifyContents(manager, 64, 7);
        SimpleAssert.that(Files.notExists(old), "the original file is retired after a complete migration");
        final NBTTagCompound initial = revisions(manifest);
        itemInventory(A).injectItems(iron(3), Actionable.MODULATE, null);
        manager.saveDisks();
        final NBTTagCompound committed = revisions(manifest);
        SimpleAssert.that(!revision(initial, A).equals(revision(committed, A)), "changed disk receives a new revision");
        SimpleAssert.eq(revision(initial, B), revision(committed, B), "unchanged disk keeps its revision");

        transferToB();
        final NBTTagCompound pendingExport = new NBTTagCompound();
        manager.writeToNBT(pendingExport);
        final NBTTagCompound pendingA = InfinityLegacyData.read(pendingExport)
            .get(A);
        final InfinityDiskFiles interrupted = new InfinityDiskFiles(directory);
        interrupted.load();
        final Map<UUID, UUID> staged = interrupted.stage(Collections.singletonMap(A, pendingA));
        final Path uncommitted = directory.resolve(A + "." + staged.get(A) + ".dat");
        SimpleAssert.that(Files.isRegularFile(uncommitted), "one new side of transfer reached disk");
        SimpleAssert.eq(committed, revisions(manifest), "unpublished revisions are invisible");
        manager = StorageManager.open(old);
        verifyContents(manager, 67, 7);
        SimpleAssert.that(!Files.exists(uncommitted), "restart can discard an unreferenced revision");

        transferToB();
        final Path hiddenManifest = directory.resolve("test-held-manifest.dat");
        Files.move(manifest, hiddenManifest);
        Files.createDirectory(manifest);
        try {
            manager.saveDisks();
            throw new AssertionError("manifest publication should fail");
        } catch (IOException expected) {
            SimpleAssert.that(manager.isDirty(), "failed publication retains changes for retry");
        } finally {
            Files.delete(manifest);
            Files.move(hiddenManifest, manifest);
        }
        SimpleAssert.eq(committed, revisions(manifest), "failed publication leaves the old manifest");
        manager.saveDisks();
        SimpleAssert.that(!manager.isDirty(), "successful retry clears dirty state");
        manager = StorageManager.open(old);
        verifyContents(manager, 47, 27);

        final NBTTagCompound channels = new InfinityDiskFiles(directory).load()
            .get(A);
        SimpleAssert.eq(
            opaque(),
            channels.getTagList(MISSING_CHANNEL, 10),
            "opaque payload survives a rewrite of its own disk");
        SimpleAssert.that(channels.hasKey(EMPTY_CHANNEL, 9), "an opaque empty channel survives its disk rewrite");
        SimpleAssert.that(Files.notExists(old), "incremental saves keep the new format authoritative");
    }

    private static void verifyContents(StorageManager manager, long aItems, long bItems) {
        SimpleAssert.eq(
            aItems,
            manager.getStorage(A.toString(), StorageChannel.ITEMS)
                .getItems()
                .findPrecise(iron(1))
                .getStackSize(),
            "A item count");
        SimpleAssert.eq(
            bItems,
            manager.getStorage(B.toString(), StorageChannel.ITEMS)
                .getItems()
                .findPrecise(iron(1))
                .getStackSize(),
            "B item count");
        SimpleAssert.eq(
            1600L,
            manager.getStorage(A.toString(), StorageChannel.FLUIDS)
                .getFluids()
                .findPrecise(water())
                .getStackSize(),
            "A fluid count");
        SimpleAssert.eq(2, manager.unresolvedCount(), "opaque channels preserved");
    }

    private static void transferToB() throws Exception {
        final IAEItemStack moved = itemInventory(A).extractItems(iron(20), Actionable.MODULATE, null);
        SimpleAssert.eq(20L, moved.getStackSize(), "exact amount leaves A");
        SimpleAssert.eq(null, itemInventory(B).injectItems(moved, Actionable.MODULATE, null), "amount reaches B");
    }

    private static InfinityItemStorageCellInventory itemInventory(UUID uuid) throws Exception {
        final ItemStack cell = new ItemStack(new ItemInfinityStorageCell());
        final NBTTagCompound tag = new NBTTagCompound();
        tag.setString("diskuuid", uuid.toString());
        cell.setTagCompound(tag);
        return new InfinityItemStorageCellInventory(cell, null, null);
    }

    private static IAEItemStack iron(int amount) {
        return AEItemStack.create(new ItemStack(Items.iron_ingot, amount));
    }

    private static IAEItemStack ironWithIdentity(int id, int amount) {
        final ItemStack stack = new ItemStack(Items.iron_ingot, amount);
        final NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("gtitTestIdentity", id);
        stack.setTagCompound(tag);
        return AEItemStack.create(stack);
    }

    private static IAEFluidStack water() {
        return AEFluidStack.create(new FluidStack(FluidRegistry.WATER, 1600));
    }

    private static NBTTagCompound revisions(Path manifest) throws IOException {
        return InfinityDiskFiles.read(manifest)
            .getCompoundTag("disks");
    }

    private static String revision(NBTTagCompound manifest, UUID disk) {
        return manifest.getString(disk.toString());
    }

    private static byte[] writeLegacyFixture(Path path) throws IOException {
        final StorageManager old = new StorageManager("legacy-fixture");
        old.getStorage(A.toString(), StorageChannel.ITEMS)
            .getItems()
            .add(iron(64));
        old.getStorage(B.toString(), StorageChannel.ITEMS)
            .getItems()
            .add(iron(7));
        old.getStorage(A.toString(), StorageChannel.FLUIDS)
            .getFluids()
            .add(water());
        final NBTTagCompound data = new NBTTagCompound();
        old.writeToNBT(data);
        final NBTTagCompound opaqueDisk = new NBTTagCompound();
        opaqueDisk.setString("diskuuid", A.toString());
        opaqueDisk.setString("typeid", MISSING_CHANNEL);
        opaqueDisk.setTag("diskdata", opaque());
        data.getTagList("disklist_v2", 10)
            .appendTag(opaqueDisk);
        final NBTTagCompound emptyDisk = (NBTTagCompound) opaqueDisk.copy();
        emptyDisk.setString("typeid", EMPTY_CHANNEL);
        emptyDisk.setTag("diskdata", new NBTTagList());
        data.getTagList("disklist_v2", 10)
            .appendTag(emptyDisk);
        final NBTTagCompound root = new NBTTagCompound();
        root.setTag("data", data);
        try (OutputStream output = Files.newOutputStream(path)) {
            CompressedStreamTools.writeCompressed(root, output);
        }
        return Files.readAllBytes(path);
    }

    private static NBTTagList opaque() {
        final NBTTagList payload = new NBTTagList();
        final NBTTagCompound entry = new NBTTagCompound();
        entry.setLong("amount", 42L);
        payload.appendTag(entry);
        return payload;
    }
}

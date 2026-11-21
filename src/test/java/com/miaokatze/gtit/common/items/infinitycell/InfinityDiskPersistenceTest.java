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

    public static void main(String[] args) throws Exception {
        if (!NativeAeTestBootstrap.enter(InfinityDiskPersistenceTest.class, args)) return;
        cpw.mods.fml.server.FMLServerHandler.instance();
        Thread.currentThread()
            .setName("Server thread");
        final Path directory = Files.createTempDirectory(Paths.get("."), "snapshot-");
        final String copiedSave = System.getenv("GTIT_LEGACY_WORLD_FIXTURE");
        if (copiedSave != null) verifyExistingSaveCopy(directory, Paths.get(copiedSave));
        verifyIncrementalCommit(directory);
        System.out.println(
            "InfinityDiskPersistenceTest: automatic migration, dirty UUIDs, interrupted commit and retry passed");
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
        SimpleAssert.eq(1, manager.unresolvedCount(), "opaque channel preserved");
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

// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtit.testlab.scenario;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.server.MinecraftServer;

import com.miaokatze.gtit.common.api.enums.GTITItemList;
import com.miaokatze.gtit.common.items.infinitycell.InfinityCellConstants;
import com.miaokatze.gtit.common.items.infinitycell.InfinityStackTypes;
import com.miaokatze.gtit.common.items.infinitycell.StorageManager;
import com.miaokatze.gtit.testlab.ProbeContext;
import com.miaokatze.gtit.testlab.ProbeScenario;
import com.miaokatze.gtit.testlab.ProbeTask;

import appeng.api.AEApi;
import appeng.api.config.FullnessMode;
import appeng.api.config.OperationMode;
import appeng.api.config.Settings;
import appeng.api.definitions.ITileDefinition;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IAEStackType;
import appeng.tile.storage.TileDrive;
import appeng.tile.storage.TileIOPort;
import appeng.util.IterationCounter;
import cpw.mods.fml.common.registry.GameRegistry;

/** Runs the actual AE grid and port scheduler against the UUID from the legacy save fixture. */
final class IOPortDrainScenario implements ProbeScenario {

    private static final String SOURCE = "a23f1234-0000-0000-0000-000000000001";
    private static final long[] INITIAL = { 64, 1600, 64 };
    private static final int HOLD_TICKS = 120;
    private static final String BACKUP_NAME = "gtit-infinity-commit";
    private final boolean fluidLast;

    IOPortDrainScenario(boolean fluidLast) {
        this.fluidLast = fluidLast;
    }

    @Override
    public String id() {
        return fluidLast ? "native-infinity-ioport-fluid-last" : "native-infinity-ioport";
    }

    @Override
    public boolean defaultEnabled() {
        return false;
    }

    @Override
    public ProbeTask start(ProbeContext context) {
        final IAEStackType<?> essentia = InfinityStackTypes.byId("essentia");
        final File legacy = MinecraftServer.getServer()
            .getEntityWorld()
            .getSaveHandler()
            .getMapFileFromName(StorageManager.storageName());
        final File manifest = new File(legacy.getParentFile(), StorageManager.storageName() + "/manifest.dat");
        final boolean migrated = !legacy.exists() && manifest.isFile() && essentia != null;
        context.check(
            "legacy-snapshot-and-essentia-channel",
            migrated,
            "legacyRetired=" + !legacy.exists()
                + ", manifest="
                + manifest.isFile()
                + ", essentia="
                + (essentia != null));
        if (!migrated) return () -> true;
        final IAEStackType<?>[] types = { InfinityStackTypes.ITEM_STACK_TYPE, InfinityStackTypes.FLUID_STACK_TYPE,
            essentia };
        final boolean fromBackup = "true".equals(System.getenv("GTIT_TESTLAB_INFINITY_RESTORED_BACKUP"));
        final long[] expected = fromBackup ? new long[] { 0, INITIAL[1], INITIAL[2] } : INITIAL;
        final long[] loaded = counts(SOURCE, types);
        final boolean loadedStock = Arrays.equals(expected, loaded);
        context.check(
            fromBackup ? "restored-backup-source-stock" : "legacy-source-stock",
            loadedStock,
            "items/fluid/essentia=" + Arrays.toString(loaded));
        if (!loadedStock) return () -> true;

        final var definitions = AEApi.instance()
            .definitions();
        place(
            context,
            4,
            definitions.blocks()
                .energyCellCreative());
        place(
            context,
            5,
            definitions.blocks()
                .iOPort());
        place(
            context,
            6,
            definitions.blocks()
                .drive());
        final TileIOPort port = (TileIOPort) context.world()
            .getTileEntity(context.x(5), context.y(3), context.z(5));
        final TileDrive drive = (TileDrive) context.world()
            .getTileEntity(context.x(6), context.y(3), context.z(5));
        context.onCleanup(() -> {
            for (int i = 0; i < 12; i++) port.setInventorySlotContents(i, null);
            for (int i = 0; i < drive.getSizeInventory(); i++) drive.setInventorySlotContents(i, null);
        });
        drive.setInventorySlotContents(
            0,
            definitions.items()
                .cell1k()
                .maybeStack(1)
                .get());
        port.getConfigManager()
            .putSetting(Settings.OPERATION_MODE, OperationMode.EMPTY);
        port.getConfigManager()
            .putSetting(Settings.FULLNESS_MODE, FullnessMode.EMPTY);
        final ItemStack source = context.mark(GTITItemList.NekoInfinityStorageUnit.get(1));
        source.getTagCompound()
            .setString(InfinityCellConstants.DISKUUID, SOURCE);
        for (int i = 0; i < types.length; i++) {
            if (expected[i] > 0) checkSingleStackQuery(context, source, types[i], expected[i]);
        }
        return new Drain(context, port, drive, source, types, expected, manifest, fluidLast);
    }

    @SuppressWarnings("unchecked")
    private static <T extends IAEStack<T>> void checkSingleStackQuery(ProbeContext context, ItemStack source,
        IAEStackType<T> type, long expected) {
        final T request = StorageManager.getInstance()
            .getStorage(SOURCE, type)
            .<T>getListFor()
            .getFirstItem();
        final IMEInventoryHandler<T> handler = AEApi.instance()
            .registries()
            .cell()
            .getCellInventory(source, null, type);
        final T answer = request == null || handler == null ? null
            : handler.getAvailableItem(request, IterationCounter.fetchNewId());
        final long amount = answer == null ? 0 : answer.getStackSize();
        if (answer != null) answer.setStackSize(0);
        context.check(
            "single-stack-query-" + type.getId(),
            answer != null && answer != request && amount == expected && request.getStackSize() == expected,
            "available=" + amount + ", expected=" + expected);
    }

    private static void place(ProbeContext context, int x, ITileDefinition definition) {
        final int meta = definition.maybeStack(1)
            .get()
            .getItemDamage();
        if (!context.setBlock(
            x,
            3,
            5,
            definition.maybeBlock()
                .get(),
            meta)) {
            throw new IllegalStateException("Could not place AE fixture at x=" + x);
        }
    }

    private static long[] counts(String uuid, IAEStackType<?>[] types) {
        final long[] amounts = new long[types.length];
        for (int i = 0; i < types.length; i++) {
            for (IAEStack<?> stack : StorageManager.getInstance()
                .getStorage(uuid, types[i])
                .getListFor()) {
                if (stack.getStackSize() > 0) amounts[i] += stack.getStackSize();
            }
        }
        return amounts;
    }

    private static int outputCount(TileIOPort port) {
        int count = 0;
        for (int i = 6; i < 12; i++) {
            final ItemStack stack = port.getStackInSlot(i);
            if (stack == null) continue;
            if (!sameDisk(stack)) throw new IllegalStateException("Unexpected stack in IOPort output slot " + i);
            count += stack.stackSize;
        }
        return count;
    }

    private static boolean sameDisk(ItemStack stack) {
        return stack != null && stack.hasTagCompound()
            && SOURCE.equals(
                stack.getTagCompound()
                    .getString(InfinityCellConstants.DISKUUID));
    }

    private static String diskFile(NBTTagCompound index) {
        return SOURCE + "."
            + index.getCompoundTag("disks")
                .getString(SOURCE)
            + ".dat";
    }

    private static long[] savedCounts(NBTTagCompound disk, IAEStackType<?>[] types) {
        final long[] amounts = new long[types.length];
        final NBTTagCompound channels = disk.getCompoundTag("channels");
        for (int i = 0; i < types.length; i++) {
            final NBTTagList entries = channels.getTagList(types[i].getId(), 10);
            for (int j = 0; j < entries.tagCount(); j++) {
                final IAEStack<?> stack = types[i].loadStackFromNBT(entries.getCompoundTagAt(j));
                if (stack != null && stack.getStackSize() > 0) amounts[i] += stack.getStackSize();
            }
        }
        return amounts;
    }

    private static NBTTagCompound readTag(File file) throws IOException {
        try (InputStream input = Files.newInputStream(file.toPath())) {
            return CompressedStreamTools.readCompressed(input);
        }
    }

    private static NBTTagCompound readTag(ZipFile zip, String file) throws IOException {
        final String suffix = "data/" + StorageManager.storageName() + "/" + file;
        final ZipEntry entry = zip.stream()
            .filter(
                candidate -> candidate.getName()
                    .endsWith(suffix))
            .findFirst()
            .orElseThrow(() -> new IOException("Missing infinity cell backup entry " + suffix));
        try (InputStream input = zip.getInputStream(entry)) {
            return CompressedStreamTools.readCompressed(input);
        }
    }

    private static final class Drain implements ProbeTask {

        private final ProbeContext context;
        private final TileIOPort port;
        private final TileDrive drive;
        private final ItemStack source;
        private final IAEStackType<?>[] types;
        private final long[] expected;
        private final File manifest;
        private final boolean fluidLast;
        private int phase;
        private long holdFrom = -1;
        private File archive;

        Drain(ProbeContext context, TileIOPort port, TileDrive drive, ItemStack source, IAEStackType<?>[] types,
            long[] expected, File manifest, boolean fluidLast) {
            this.context = context;
            this.port = port;
            this.drive = drive;
            this.source = source;
            this.types = types;
            this.expected = expected;
            this.manifest = manifest;
            this.fluidLast = fluidLast;
        }

        @Override
        public boolean tick() throws Exception {
            final long time = context.world()
                .getTotalWorldTime();
            if (phase == 0) {
                if (!port.getProxy()
                    .isActive()
                    || !drive.getProxy()
                        .isActive())
                    return false;
                final boolean sameGrid = port.getProxy()
                    .getGrid()
                    == drive.getProxy()
                        .getGrid();
                context.check("powered-real-grid", sameGrid, "Native AE IOPort and ME Drive are active on one grid");
                if (!sameGrid) return true;
                port.setInventorySlotContents(0, source);
                phase = 1;
                return false;
            }

            final long[] stock = counts(SOURCE, types);
            final boolean input = sameDisk(port.getStackInSlot(0));
            final int output = outputCount(port);
            final String snapshot = "tick=" + time
                + ", stock="
                + Arrays.toString(stock)
                + ", input="
                + input
                + ", output="
                + output;
            final boolean empty = stock[0] == 0 && stock[1] == 0 && stock[2] == 0;
            if (!empty && (!input || output != 0)) {
                context.check("no-early-ejection", false, snapshot);
                return true;
            }
            if (phase == 1) {
                if (stock[1] != INITIAL[1] || stock[2] != INITIAL[2]) {
                    context.check("item-only-destination", false, snapshot);
                    return true;
                }
                if (stock[0] == 0 && held(time, "item-empty-retains-fluid-and-essentia", snapshot)) {
                    final String backupDirectory = System.getenv("GTIT_TESTLAB_INFINITY_BACKUP_DIRECTORY");
                    if (backupDirectory == null) {
                        connectSecondReceiver();
                    } else {
                        archive = new File(backupDirectory, BACKUP_NAME + ".zip");
                        if (archive.exists()) throw new IOException("The test archive already exists");
                        final boolean accepted = command("backup start " + BACKUP_NAME);
                        context.check("backup-command-accepted", accepted, archive.getName());
                        if (!accepted) return true;
                        phase = 4;
                    }
                }
            } else if (phase == 4) {
                if (!archive.isFile()) return false;
                try (ZipFile zip = new ZipFile(archive)) {
                    final NBTTagCompound backedUp = readTag(zip, diskFile(readTag(zip, "manifest.dat")));
                    final long[] saved = savedCounts(backedUp, types);
                    context.check(
                        "backup-includes-committed-three-channel-snapshot",
                        Arrays.equals(new long[] { 0, INITIAL[1], INITIAL[2] }, saved),
                        "items/fluid/essentia=" + Arrays.toString(saved));
                }
                connectSecondReceiver();
            } else if (phase == 2) {
                final int last = fluidLast ? 1 : 2;
                final int draining = fluidLast ? 2 : 1;
                if (stock[0] != 0 || stock[last] != INITIAL[last]) {
                    context.check("second-destination-preserves-last-channel", false, snapshot);
                    return true;
                }
                final String check = fluidLast ? "item-and-essentia-empty-retains-fluid"
                    : "item-and-fluid-empty-retains-essentia";
                if (stock[draining] == 0 && held(time, check, snapshot)) {
                    final boolean flushed = command("save-all flush");
                    final long[] saved = savedCounts(
                        readTag(new File(manifest.getParentFile(), diskFile(readTag(manifest)))),
                        types);
                    context.check(
                        "explicit-flush-commits-remainder",
                        flushed && Arrays.equals(stock, saved),
                        "items/fluid/essentia=" + Arrays.toString(saved));
                    final ItemStack receiver = fluidLast ? GTITItemList.InfinityFluidCell.get(1)
                        : GTITItemList.NekoInfinityStorageUnit.get(1);
                    drive.setInventorySlotContents(2, receiver);
                    phase = 3;
                }
            } else if (output != 0 || !input) {
                context.check("all-channels-empty-ejects-exact-uuid", empty && output == 1 && !input, snapshot);
                final long[] received = received();
                context.check(
                    "native-destinations-received-all-stock",
                    Arrays.equals(expected, received),
                    "items/fluid/essentia=" + Arrays.toString(received));
                return true;
            }
            return false;
        }

        private void connectSecondReceiver() {
            final ItemStack receiver = fluidLast
                ? GameRegistry.findItemStack("thaumicenergistics", "storage.essentia", 1)
                : GTITItemList.InfinityFluidCell.get(1);
            if (receiver == null) throw new IllegalStateException("Native essentia cell is unavailable");
            drive.setInventorySlotContents(1, receiver);
            phase = 2;
        }

        private boolean command(String command) {
            final MinecraftServer server = MinecraftServer.getServer();
            return server.getCommandManager()
                .executeCommand(server, command) > 0;
        }

        private boolean held(long tick, String check, String snapshot) {
            if (holdFrom < 0) holdFrom = tick;
            if (tick - holdFrom < HOLD_TICKS) return false;
            context.check(check, true, snapshot + ", retainedTicks=" + (tick - holdFrom));
            holdFrom = -1;
            return true;
        }

        private long[] received() throws Exception {
            final long[] amounts = new long[types.length];
            for (int i = 0; i < types.length; i++) {
                for (IAEStack<?> stack : port.getProxy()
                    .getStorage()
                    .getMEMonitor(types[i])
                    .getStorageList()) {
                    if (stack.getStackSize() > 0) amounts[i] += stack.getStackSize();
                }
            }
            return amounts;
        }
    }
}

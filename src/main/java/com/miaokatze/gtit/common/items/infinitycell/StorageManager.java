// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtit.common.items.infinitycell;

import java.io.File;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;
import net.minecraft.world.WorldSavedData;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.WorldEvent;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.miaokatze.gtit.asm.GtitBackupLateMixinLoader;

import appeng.api.storage.StorageChannel;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IAEStackType;
import appeng.util.Platform;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/** Live inventory grouped by disk and channel. The server owns snapshots and the writer owns disk commits. */
public class StorageManager extends WorldSavedData {

    private static final String DATA_NAME = "GTIT_InfinityCellStorage";
    private static final Logger LOG = LogManager.getLogger("gtit");
    private static StorageManager instance;

    private final Map<UUID, Disk> disks = new ConcurrentHashMap<>();
    private final Map<UUID, DiskChanges> dirtyDisks = new LinkedHashMap<>();
    private final InfinityDiskFiles diskFiles;
    private final InfinityDiskSnapshots snapshots;
    private World world;
    private ExecutorService writer;
    private PendingSave pendingSave;
    private PendingSave closingSave;
    private boolean sealed;
    private long nextGeneration;

    private static final class PendingSave {

        final long generation;
        final Map<UUID, DiskChanges> changes;
        final Map<UUID, InfinityDiskSnapshots.Update> frozen;
        Future<Void> result;

        PendingSave(long generation, Map<UUID, DiskChanges> changes, Map<UUID, InfinityDiskSnapshots.Update> frozen) {
            this.generation = generation;
            this.changes = changes;
            this.frozen = frozen;
        }
    }

    private static final class DiskChanges {

        boolean replace;
        final Map<DataStorage, Map<Object, IAEStack<?>>> channels = new LinkedHashMap<>();

        void replace() {
            replace = true;
            channels.clear();
        }

        void mark(DataStorage storage, IAEStack<?> stack) {
            if (!replace) channels.computeIfAbsent(storage, ignored -> new LinkedHashMap<>())
                .put(storage.snapshotKey(stack), stack);
        }

        InfinityDiskSnapshots.Update capture() {
            final Map<String, Map<Object, NBTTagCompound>> update = new LinkedHashMap<>();
            channels.forEach((storage, entries) -> {
                final Map<Object, NBTTagCompound> changes = new LinkedHashMap<>();
                entries.forEach((key, stack) -> changes.put(key, storage.snapshot(stack)));
                update.put(storage.getTypeId(), changes);
            });
            return new InfinityDiskSnapshots.Update(false, update);
        }

        void forgetCommittedDeletions(DiskChanges newer, InfinityDiskSnapshots.Update frozen) {
            if (newer != null && newer.replace) return;
            channels.forEach((storage, entries) -> {
                final Map<Object, IAEStack<?>> latest = newer == null ? Collections.emptyMap()
                    : newer.channels.getOrDefault(storage, Collections.emptyMap());
                entries.forEach((key, stack) -> {
                    if (frozen.channels.get(storage.getTypeId())
                        .get(key) == null && !latest.containsKey(key)) {
                        storage.forgetSnapshotKey(stack, key);
                    }
                });
            });
        }
    }

    public StorageManager(String name) {
        this(name, null);
        instance = this;
    }

    private StorageManager(String name, InfinityDiskFiles diskFiles) {
        super(name);
        this.diskFiles = diskFiles;
        this.snapshots = diskFiles == null ? null : new InfinityDiskSnapshots(diskFiles);
        setDirty(diskFiles == null);
    }

    public static StorageManager getInstance() {
        return instance;
    }

    public static String storageName() {
        return DATA_NAME;
    }

    public static void openWorld(World world) throws IOException {
        final File legacy = world.getSaveHandler()
            .getMapFileFromName(DATA_NAME);
        if (legacy == null) throw new IOException("Missing save path for infinity cells");
        final StorageManager manager = open(legacy.toPath());
        manager.world = world;
        if (GtitBackupLateMixinLoader.canSaveInBackground()) {
            manager.startBackgroundWrites(Executors.newSingleThreadExecutor(task -> {
                final Thread thread = new Thread(task, "GTIT Infinity Cell IO");
                thread.setDaemon(true);
                return thread;
            }));
            LOG.info("Using background infinity cell saves");
        } else {
            LOG.info(
                "Using synchronous infinity cell saves because the installed ServerUtilities backup is unverified");
        }
        MinecraftForge.EVENT_BUS.register(manager);
    }

    static StorageManager open(Path legacyFile) throws IOException {
        final Path directory = legacyFile.toAbsolutePath()
            .getParent()
            .resolve(DATA_NAME);
        final InfinityDiskFiles files = new InfinityDiskFiles(directory);
        final StorageManager manager = new StorageManager(DATA_NAME, files);
        final Map<UUID, NBTTagCompound> committed = files.load();
        if (committed != null) {
            manager.loadDisks(committed, files.isLegacyFormat(), true);
            if (files.isLegacyFormat()) manager.upgradeFormat();
            files.removeAbandonedRevisions();
            retireLegacyFile(legacyFile);
        } else if (!Files.notExists(legacyFile)) {
            final NBTTagCompound root = InfinityDiskFiles.read(legacyFile);
            if (!root.hasKey("data", 10)) throw new IOException("Missing infinity cell payload in " + legacyFile);
            final Map<UUID, NBTTagCompound> legacy = InfinityLegacyData.read(root.getCompoundTag("data"));
            manager.loadDisks(legacy, true, true);
            manager.upgradeFormat();
            files.removeAbandonedRevisions();
            retireLegacyFile(legacyFile);
        } else {
            files.rejectMissingManifest();
        }
        instance = manager;
        return manager;
    }

    private void upgradeFormat() throws IOException {
        if (dirtyDisks.isEmpty()) diskFiles.save(Collections.emptyMap());
        else saveDisks();
    }

    private static void retireLegacyFile(Path legacyFile) {
        try {
            if (Files.isRegularFile(legacyFile, LinkOption.NOFOLLOW_LINKS)) Files.deleteIfExists(legacyFile);
        } catch (IOException | SecurityException e) {
            LOG.warn(
                "Committed infinity cell disks are active, but the old file could not be removed: {}",
                legacyFile,
                e);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onWorldSave(WorldEvent.Save event) {
        if (event.world != this.world) return;
        try {
            requestSave();
        } catch (IOException | RuntimeException e) {
            LOG.error("Could not save infinity cell disks; pending changes will be retried", e);
        }
    }

    public static void flushPendingWorldSave() throws IOException {
        final StorageManager manager = instance;
        if (manager != null && manager.world != null) manager.saveDisks();
    }

    /** Called after the final world save and before the server finishes shutting down. */
    public static void finishWorldSaveOnStop() {
        final StorageManager manager = instance;
        if (manager != null && manager.world != null) {
            manager.seal();
            manager.finishBeforeClose();
        }
    }

    public static void closeWorld() {
        final StorageManager manager = instance;
        try {
            if (manager != null && manager.world != null) MinecraftForge.EVENT_BUS.unregister(manager);
            if (manager != null) manager.finishBeforeClose();
        } finally {
            if (manager != null && manager.writer != null) manager.writer.shutdown();
            instance = null;
        }
    }

    void seal() {
        if (sealed) return;
        sealed = true;
        try {
            closingSave = freezeDirtyDisks();
        } catch (RuntimeException e) {
            LOG.error(
                "Could not capture final infinity cell changes in {}; prior committed disks remain intact",
                diskFiles,
                e);
        }
    }

    private void finishBeforeClose() {
        boolean interrupted = Thread.interrupted();
        try {
            while (true) {
                try {
                    // FML may already have restored the frozen registry by SERVER_STOPPED.
                    drainFrozenSaves();
                    if (!dirtyDisks.isEmpty()) {
                        LOG.error("Closing {} with {} uncaptured infinity cell disks", diskFiles, dirtyDisks.size());
                    }
                    return;
                } catch (InterruptedIOException e) {
                    interrupted = true;
                    Thread.interrupted();
                } catch (IOException | RuntimeException e) {
                    LOG.error("Could not commit infinity cell disks before closing this world", e);
                    return;
                }
            }
        } finally {
            if (interrupted) Thread.currentThread()
                .interrupt();
        }
    }

    void startBackgroundWrites(ExecutorService executor) {
        writer = executor;
    }

    void requestSave() throws IOException {
        if (sealed) return;
        if (writer == null) {
            saveDisks();
            return;
        }
        if (pendingSave != null && pendingSave.result != null) {
            if (!pendingSave.result.isDone()) return;
            finishPendingSave();
        }
        if (pendingSave == null) pendingSave = freezeDirtyDisks();
        if (pendingSave != null) submitPendingSave();
    }

    void saveDisks() throws IOException {
        if (diskFiles == null) return;
        drainFrozenSaves();
        if (sealed) return;
        pendingSave = freezeDirtyDisks();
        finishPendingSave();
    }

    private void drainFrozenSaves() throws IOException {
        finishPendingSave();
        if (closingSave == null) return;
        pendingSave = closingSave;
        closingSave = null;
        finishPendingSave();
    }

    Map<UUID, InfinityDiskSnapshots.Update> captureDirtyDisks() {
        final Map<UUID, InfinityDiskSnapshots.Update> changed = new LinkedHashMap<>();
        dirtyDisks.forEach((uuid, update) -> {
            final Disk disk = disks.get(uuid);
            if (disk == null) {
                changed.put(uuid, new InfinityDiskSnapshots.Update(true, Collections.emptyMap()));
                return;
            }
            changed.put(uuid, update.replace ? disk.captureAll() : update.capture());
        });
        return changed;
    }

    private PendingSave freezeDirtyDisks() {
        if (dirtyDisks.isEmpty()) return null;
        final Map<UUID, InfinityDiskSnapshots.Update> changed = captureDirtyDisks();
        final PendingSave batch = new PendingSave(++nextGeneration, new LinkedHashMap<>(dirtyDisks), changed);
        dirtyDisks.clear();
        return batch;
    }

    private void submitPendingSave() throws IOException {
        final PendingSave save = pendingSave;
        try {
            save.result = writer.submit(() -> {
                snapshots.save(save.frozen);
                return null;
            });
        } catch (RuntimeException e) {
            throw saveFailure(save, e);
        }
    }

    private void finishPendingSave() throws IOException {
        final PendingSave save = pendingSave;
        if (save == null) return;
        try {
            if (writer == null) {
                snapshots.save(save.frozen);
            } else {
                if (save.result == null) submitPendingSave();
                save.result.get();
            }
        } catch (InterruptedException e) {
            Thread.currentThread()
                .interrupt();
            final InterruptedIOException failure = new InterruptedIOException(
                "Interrupted while waiting for infinity cell disks");
            failure.initCause(e);
            throw failure;
        } catch (ExecutionException e) {
            save.result = null;
            throw saveFailure(save, e.getCause());
        } catch (IOException | RuntimeException e) {
            save.result = null;
            throw saveFailure(save, e);
        }
        pendingSave = null;
        if (!sealed) {
            save.changes.forEach(
                (uuid, changes) -> changes.forgetCommittedDeletions(dirtyDisks.get(uuid), save.frozen.get(uuid)));
        }
        setDirty(!dirtyDisks.isEmpty() || closingSave != null);
    }

    private IOException saveFailure(PendingSave save, Throwable cause) {
        return new IOException(
            "Infinity cell generation " + save.generation
                + " failed for "
                + diskFiles
                + " ("
                + save.frozen.size()
                + " disks)",
            cause);
    }

    static String keyOf(String uuid, String typeId) {
        return uuid + "#" + typeId;
    }

    public DataStorage getStorage(String uuid, StorageChannel channel) {
        return getStorage(uuid, InfinityStackTypes.of(channel));
    }

    public DataStorage getStorage(String uuid, IAEStackType<?> type) {
        UUID uid;
        if (uuid == null || uuid.isEmpty()) {
            do {
                uid = UUID.randomUUID();
            } while (disks.containsKey(uid));
        } else {
            uid = UUID.fromString(uuid);
        }
        final UUID diskId = uid;
        final String typeId = type.getId();
        if (Platform.isServer()) {
            final Disk disk = disks.computeIfAbsent(diskId, ignored -> new Disk());
            final DataStorage storage = disk.getOrCreate(diskId, type);
            if (disk.resolvedChannel) {
                postChanges(storage);
                disk.resolvedChannel = false;
            }
            return storage;
        }
        final Disk disk = disks.get(diskId);
        final DataStorage existing = disk == null ? null : disk.channels.get(typeId);
        return existing != null ? existing : new DataStorage(diskId, type);
    }

    public DataStorage getStorage(ItemStack item) {
        if (item.getItem() instanceof IInfinityCellItem cellItem) {
            return this.getStorage(item, InfinityStackTypes.of(cellItem.getChannel()));
        }
        return null;
    }

    public DataStorage getStorage(ItemStack item, IAEStackType<?> type) {
        if (item.getItem() instanceof IInfinityCellItem) {
            return this.getStorage(allocateOrReadUuid(Platform.openNbtData(item), Platform.isServer()), type);
        }
        return null;
    }

    /**
     * 元件身份只在首次服务端取用时分配一次并留在 NBT 里。不这么做就会：NBT 无 diskuuid →
     * 每次构造 handler 换一个随机空桶 → 写入的内容落在孤儿桶，表现即「流体/源质存不进去」。
     * 客户端一律不写：既会与保存期 writeToNBT 遍历竞争，其临时桶也只用于只读展示。
     */
    static String allocateOrReadUuid(NBTTagCompound data, boolean mayPersist) {
        String uuid = data.getString(InfinityCellConstants.DISKUUID);
        if (!uuid.isEmpty() || !mayPersist) {
            return uuid;
        }
        uuid = UUID.randomUUID()
            .toString();
        data.setString(InfinityCellConstants.DISKUUID, uuid);
        return uuid;
    }

    public DataStorage getStorage(ItemStack item, EntityPlayer player) {
        DataStorage storage = this.getStorage(item);
        if (storage == null) return null;
        NBTTagCompound data = Platform.openNbtData(item);
        String uuid = data.getString(InfinityCellConstants.DISKUUID);
        if (uuid.isEmpty()) {
            data.setString(InfinityCellConstants.DISKUUID, storage.getUUID());
            player.inventory.setInventorySlotContents(player.inventory.currentItem, item.copy());
        }
        return storage;
    }

    @Override
    public void readFromNBT(NBTTagCompound data) {
        try {
            final Set<UUID> previous = new LinkedHashSet<>(disks.keySet());
            loadDisks(InfinityLegacyData.read(data), true, false);
            if (diskFiles != null) {
                previous.addAll(disks.keySet());
                previous.forEach(
                    uuid -> dirtyDisks.computeIfAbsent(uuid, ignored -> new DiskChanges())
                        .replace());
                setDirty(!dirtyDisks.isEmpty());
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("Invalid legacy infinity cell data", e);
        }
    }

    private void loadDisks(Map<UUID, NBTTagCompound> data, boolean legacy, boolean seedSnapshots) throws IOException {
        final Map<UUID, Disk> loaded = new LinkedHashMap<>();
        for (final Map.Entry<UUID, NBTTagCompound> record : data.entrySet()) {
            final UUID uuid = record.getKey();
            final NBTTagCompound contents = record.getValue();
            final Disk disk = new Disk();
            boolean rewrite = legacy;
            final Map<String, Map<Object, NBTTagCompound>> baseline = new LinkedHashMap<>();
            for (final String typeId : contents.func_150296_c()) {
                if (typeId.isEmpty()) throw new IOException("Empty infinity cell channel in " + uuid);
                final NBTTagList list = InfinityLegacyData.list(contents, typeId);
                final IAEStackType<?> type = resolveType(typeId);
                if (type == null) {
                    disk.unresolved.put(typeId, list);
                    if (seedSnapshots) baseline.put(typeId, Disk.copyOpaque(list));
                } else {
                    final Map<Object, NBTTagCompound> entries = new LinkedHashMap<>();
                    final DataStorage storage = new DataStorage(uuid, type);
                    rewrite |= storage.load(list, legacy, entries);
                    disk.channels.put(typeId, storage);
                    if (!entries.isEmpty()) baseline.put(typeId, entries);
                }
            }
            loaded.put(uuid, disk);
            if (seedSnapshots) {
                snapshots.seed(uuid, baseline);
                if (rewrite) dirtyDisks.computeIfAbsent(uuid, ignored -> new DiskChanges())
                    .replace();
            }
        }
        disks.clear();
        disks.putAll(loaded);
        setDirty(!dirtyDisks.isEmpty());
    }

    /** Keep temporarily unavailable channel payloads intact. */
    private static IAEStackType<?> resolveType(String typeId) {
        try {
            if (InfinityLegacyData.ITEM.equals(typeId)) return InfinityStackTypes.ITEM_STACK_TYPE;
            if (InfinityLegacyData.FLUID.equals(typeId)) return InfinityStackTypes.FLUID_STACK_TYPE;
            return InfinityStackTypes.byId(typeId);
        } catch (LinkageError | RuntimeException ignored) {
            return null;
        }
    }

    @Override
    public void writeToNBT(NBTTagCompound data) {
        final Map<UUID, NBTTagCompound> snapshot = new LinkedHashMap<>();
        disks.forEach((uuid, disk) -> snapshot.put(uuid, disk.writeChannels()));
        InfinityLegacyData.write(snapshot, data);
    }

    public void postChanges(DataStorage storage) {
        if (storage != null && diskFiles != null) {
            dirtyDisks.computeIfAbsent(storage.getRawUUID(), ignored -> new DiskChanges())
                .replace();
        }
        setDirty(true);
    }

    void postChanges(DataStorage storage, IAEStack<?> stack) {
        if (storage != null && diskFiles != null) {
            final UUID uuid = storage.getRawUUID();
            final Disk disk = disks.get(uuid);
            if (stack == null || disk == null || disk.channels.get(storage.getTypeId()) != storage) {
                // A caller with a stale or untracked handler can only request a refresh of the current disk.
                postChanges(storage);
                return;
            }
            dirtyDisks.computeIfAbsent(uuid, ignored -> new DiskChanges())
                .mark(storage, stack);
        }
        setDirty(true);
    }

    public int bucketCount() {
        return disks.values()
            .stream()
            .mapToInt(disk -> disk.channels.size())
            .sum();
    }

    int unresolvedCount() {
        return disks.values()
            .stream()
            .mapToInt(disk -> disk.unresolved.size())
            .sum();
    }

    public static boolean hasUnresolvedContents(ItemStack item) {
        final StorageManager manager = instance;
        if (manager == null || item == null || !(item.getItem() instanceof IInfinityCellItem) || !item.hasTagCompound())
            return false;
        final String id = item.getTagCompound()
            .getString(InfinityCellConstants.DISKUUID);
        if (id.isEmpty()) return false;
        try {
            final Disk disk = manager.disks.get(UUID.fromString(id));
            return disk != null && (disk.unresolved.values()
                .stream()
                .anyMatch(list -> list.tagCount() > 0)
                || disk.channels.values()
                    .stream()
                    .anyMatch(DataStorage::hasUnresolvedEntries));
        } catch (IllegalArgumentException e) {
            return true;
        }
    }

    private static final class Disk {

        final Map<String, DataStorage> channels = new ConcurrentHashMap<>();
        final Map<String, NBTTagList> unresolved = new LinkedHashMap<>();
        boolean resolvedChannel;

        DataStorage getOrCreate(UUID uuid, IAEStackType<?> type) {
            return channels.computeIfAbsent(type.getId(), id -> {
                final NBTTagList raw = unresolved.get(id);
                final DataStorage storage = new DataStorage(uuid, type);
                storage.load(raw, false, new LinkedHashMap<>());
                if (raw != null) {
                    unresolved.remove(id);
                    resolvedChannel = true;
                }
                return storage;
            });
        }

        InfinityDiskSnapshots.Update captureAll() {
            final Map<String, Map<Object, NBTTagCompound>> data = new LinkedHashMap<>();
            channels.forEach((type, storage) -> {
                final Map<Object, NBTTagCompound> entries = storage.snapshotAll();
                if (!entries.isEmpty()) data.put(type, entries);
            });
            unresolved.forEach((type, raw) -> data.put(type, copyOpaque(raw)));
            return new InfinityDiskSnapshots.Update(true, data);
        }

        private static Map<Object, NBTTagCompound> copyOpaque(NBTTagList raw) {
            final Map<Object, NBTTagCompound> entries = new LinkedHashMap<>();
            for (int i = 0; i < raw.tagCount(); i++) {
                entries.put(
                    new Object(),
                    (NBTTagCompound) raw.getCompoundTagAt(i)
                        .copy());
            }
            return entries;
        }

        NBTTagCompound writeChannels() {
            final NBTTagCompound data = new NBTTagCompound();
            channels.forEach((type, storage) -> {
                final NBTTagList contents = storage.writeContents();
                if (contents.tagCount() > 0) data.setTag(type, contents);
            });
            unresolved.forEach(data::setTag);
            return data;
        }
    }
}

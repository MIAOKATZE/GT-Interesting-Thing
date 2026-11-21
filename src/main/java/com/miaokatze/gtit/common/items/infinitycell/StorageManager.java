// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtit.common.items.infinitycell;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

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

import appeng.api.storage.StorageChannel;
import appeng.api.storage.data.IAEStackType;
import appeng.util.Platform;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/** Live inventory grouped by disk and channel. Disk commits run on the server's world-save thread. */
public class StorageManager extends WorldSavedData {

    private static final String DATA_NAME = "GTIT_InfinityCellStorage";
    private static final Logger LOG = LogManager.getLogger("gtit");
    private static StorageManager instance;

    private final Map<UUID, Disk> disks = new ConcurrentHashMap<>();
    private final Set<UUID> dirtyDisks = new LinkedHashSet<>();
    private final InfinityDiskFiles diskFiles;
    private World world;

    public StorageManager(String name) {
        this(name, null);
        instance = this;
    }

    private StorageManager(String name, InfinityDiskFiles diskFiles) {
        super(name);
        this.diskFiles = diskFiles;
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
            manager.loadDisks(committed);
            files.removeAbandonedRevisions();
            retireLegacyFile(legacyFile);
        } else if (!Files.notExists(legacyFile)) {
            final NBTTagCompound root = InfinityDiskFiles.read(legacyFile);
            if (!root.hasKey("data", 10)) throw new IOException("Missing infinity cell payload in " + legacyFile);
            final Map<UUID, NBTTagCompound> legacy = InfinityLegacyData.read(root.getCompoundTag("data"));
            manager.loadDisks(legacy);
            // Copy the serialized stacks, including opaque channels, without re-encoding them during import.
            files.save(legacy);
            files.removeAbandonedRevisions();
            retireLegacyFile(legacyFile);
        }
        instance = manager;
        return manager;
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
        if (event.world == this.world) flushWorld();
    }

    public static void closeWorld() {
        final StorageManager manager = instance;
        if (manager != null && manager.world != null) {
            MinecraftForge.EVENT_BUS.unregister(manager);
            manager.flushWorld();
        }
        instance = null;
    }

    private void flushWorld() {
        try {
            saveDisks();
        } catch (IOException | RuntimeException e) {
            LOG.error("Could not commit infinity cell disks; the previous snapshot remains active", e);
        }
    }

    void saveDisks() throws IOException {
        if (diskFiles == null || dirtyDisks.isEmpty()) return;
        final Map<UUID, NBTTagCompound> changed = new LinkedHashMap<>();
        for (final UUID uuid : dirtyDisks) {
            final Disk disk = disks.get(uuid);
            changed.put(uuid, disk == null ? new NBTTagCompound() : disk.writeChannels());
        }
        diskFiles.save(changed);
        dirtyDisks.clear();
        setDirty(false);
    }

    static String keyOf(String uuid, String typeId) {
        return uuid + "#" + typeId;
    }

    public DataStorage getStorage(String uuid, StorageChannel channel) {
        return getStorage(uuid, InfinityStackTypes.of(channel));
    }

    public DataStorage getStorage(String uuid, IAEStackType<?> type) {
        UUID uid;
        try {
            uid = UUID.fromString(uuid);
        } catch (IllegalArgumentException | NullPointerException ignored) {
            do {
                uid = UUID.randomUUID();
            } while (disks.containsKey(uid));
        }
        final UUID diskId = uid;
        final String typeId = type.getId();
        if (Platform.isServer()) {
            return disks.computeIfAbsent(diskId, ignored -> new Disk())
                .getOrCreate(diskId, type);
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
            loadDisks(InfinityLegacyData.read(data));
            if (diskFiles != null) {
                dirtyDisks.addAll(previous);
                dirtyDisks.addAll(disks.keySet());
                setDirty(!dirtyDisks.isEmpty());
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("Invalid legacy infinity cell data", e);
        }
    }

    private void loadDisks(Map<UUID, NBTTagCompound> data) throws IOException {
        final Map<UUID, Disk> loaded = new LinkedHashMap<>();
        for (final Map.Entry<UUID, NBTTagCompound> record : data.entrySet()) {
            final UUID uuid = record.getKey();
            final NBTTagCompound contents = record.getValue();
            final Disk disk = new Disk();
            for (final String typeId : contents.func_150296_c()) {
                if (typeId.isEmpty()) throw new IOException("Empty infinity cell channel in " + uuid);
                final NBTTagList list = InfinityLegacyData.list(contents, typeId);
                final IAEStackType<?> type = resolveType(typeId);
                if (type == null) {
                    disk.unresolved.put(typeId, list);
                } else {
                    disk.channels.put(typeId, DataStorage.readFromNBT(uuid, list, type));
                }
            }
            loaded.put(uuid, disk);
        }
        disks.clear();
        disks.putAll(loaded);
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
        if (storage != null && diskFiles != null) dirtyDisks.add(storage.getRawUUID());
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

    private static final class Disk {

        final Map<String, DataStorage> channels = new ConcurrentHashMap<>();
        final Map<String, NBTTagList> unresolved = new LinkedHashMap<>();

        DataStorage getOrCreate(UUID uuid, IAEStackType<?> type) {
            return channels.computeIfAbsent(type.getId(), id -> {
                final DataStorage storage = DataStorage.readFromNBT(uuid, unresolved.get(id), type);
                unresolved.remove(id);
                return storage;
            });
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

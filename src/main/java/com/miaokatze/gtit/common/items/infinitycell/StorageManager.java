package com.miaokatze.gtit.common.items.infinitycell;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.WorldSavedData;

import appeng.api.storage.StorageChannel;
import appeng.api.storage.data.IAEStackType;
import appeng.util.Platform;

/**
 * 无限元件的外置存储：按 (UUID, 通道) 复合定位，一 UUID 可同时携带物品/流体/第三方通道各自的桶。
 */
public class StorageManager extends WorldSavedData {

    private static final String DATA_NAME = "GTIT_InfinityCellStorage";
    private static final String KEY_SEPARATOR = "#";
    /** 读档时 typeId 尚未注册的桶，原样留到写档，避免在 mod 缺席期间抹掉第三方通道数据。 */
    private static final String UNRESOLVED_PREFIX = "?";

    // ConcurrentHashMap：writeToNBT 在服务器保存时遍历本 map，而 tooltip 等
    // 客户端线程路径会并发读，避免 CME 直接打崩服务器。
    private final Map<String, DataStorage> disks = new ConcurrentHashMap<>();
    private final Map<String, NBTTagList> unresolved = new ConcurrentHashMap<>();
    private static StorageManager instance;

    public StorageManager(String name) {
        super(name);
        this.setDirty(true);
        instance = this;
    }

    public static StorageManager getInstance() {
        return instance;
    }

    public static String storageName() {
        return DATA_NAME;
    }

    private static String key(UUID uuid, String typeId) {
        return uuid + KEY_SEPARATOR + typeId;
    }

    /** 包私有：供同包测试断言「同一 UUID 的不同通道落在不同桶上」，纯 JVM 下无法实例化 IAEStackType。 */
    static String keyOf(String uuid, String typeId) {
        return uuid + KEY_SEPARATOR + typeId;
    }

    private static String key(UUID uuid, IAEStackType<?> type) {
        return key(uuid, type.getId());
    }

    public DataStorage getStorage(String uuid, StorageChannel channel) {
        return getStorage(uuid, InfinityStackTypes.of(channel));
    }

    public DataStorage getStorage(String uuid, IAEStackType<?> type) {
        final String typeId = type.getId();
        UUID uid;
        try {
            uid = UUID.fromString(uuid);
        } catch (Exception ignored) {
            do {
                uid = UUID.randomUUID();
            } while (disks.get(key(uid, typeId)) != null);
        }
        final String composite = key(uid, typeId);
        DataStorage d = disks.get(composite);
        if (d == null) {
            d = new DataStorage(uid, type);
            // 仅服务器线程注册：物品 tooltip 在客户端渲染线程经 getCellInventory
            // 构造 inventory，若在此 put 会与保存期 writeToNBT 遍历竞争（历史 CME 崩溃），
            // 且无 UUID 新元件每次悬停都会泄漏随机 UUID 条目。客户端返回临时实例即可。
            if (Platform.isServer()) {
                disks.put(composite, d);
            }
        }
        return d;
    }

    public DataStorage getStorage(ItemStack item) {
        if (item.getItem() instanceof IInfinityCellItem cellItem) {
            return this.getStorage(item, InfinityStackTypes.of(cellItem.getChannel()));
        }
        return null;
    }

    public DataStorage getStorage(ItemStack item, IAEStackType<?> type) {
        if (item.getItem() instanceof IInfinityCellItem) {
            NBTTagCompound data = Platform.openNbtData(item);
            return this.getStorage(data.getString(InfinityCellConstants.DISKUUID), type);
        }
        return null;
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
        final Map<String, DataStorage> d = new HashMap<>();
        final Map<String, NBTTagList> pending = new HashMap<>();

        // 旧两表逐字兼容：物品条目内层键 diskdata，流体条目内层键**也是** fluid_disklist。
        final NBTTagList itemDisks = data.getTagList(InfinityCellConstants.DISKLIST, 10);
        if (itemDisks.tagCount() > 0) {
            readLegacy(d, itemDisks, InfinityCellConstants.DISKDATA, InfinityStackTypes.ITEM_STACK_TYPE);
        }
        final NBTTagList fluidDisks = data.getTagList(InfinityCellConstants.FLUID_DISKLIST, 10);
        if (fluidDisks.tagCount() > 0) {
            readLegacy(d, fluidDisks, InfinityCellConstants.FLUID_DISKLIST, InfinityStackTypes.FLUID_STACK_TYPE);
        }

        final NBTTagList v2 = data.getTagList(InfinityCellConstants.TYPED_DISKLIST, 10);
        for (int i = 0; i < v2.tagCount(); i++) {
            final NBTTagCompound disk = v2.getCompoundTagAt(i);
            final String uuid = disk.getString(InfinityCellConstants.DISKUUID);
            final String typeId = disk.getString(InfinityCellConstants.DISK_TYPEID);
            final NBTTagList list = disk.getTagList(InfinityCellConstants.DISKDATA, 10);
            if (uuid.isEmpty() || typeId.isEmpty()) {
                continue;
            }
            final IAEStackType<?> type = resolveType(typeId);
            if (type == null) {
                pending.put(UNRESOLVED_PREFIX + uuid + KEY_SEPARATOR + typeId, list);
                continue;
            }
            final UUID uid = UUID.fromString(uuid);
            d.put(key(uid, type), DataStorage.readFromNBT(uid, list, type));
        }

        disks.clear();
        disks.putAll(d);
        unresolved.clear();
        unresolved.putAll(pending);
    }

    /** 通道解析不了（对应 mod 被移除、其类无法加载）时按未注册处理，把桶原样留着而不是抹掉玩家数据。 */
    private static IAEStackType<?> resolveType(String typeId) {
        try {
            return InfinityStackTypes.byId(typeId);
        } catch (Throwable t) {
            return null;
        }
    }

    private void readLegacy(Map<String, DataStorage> out, NBTTagList diskList, String innerKey, IAEStackType<?> type) {
        for (int i = 0; i < diskList.tagCount(); i++) {
            final NBTTagCompound disk = diskList.getCompoundTagAt(i);
            final UUID uid = UUID.fromString(disk.getString(InfinityCellConstants.DISKUUID));
            out.put(key(uid, type), DataStorage.readFromNBT(uid, disk.getTagList(innerKey, 10), type));
        }
    }

    @Override
    public void writeToNBT(NBTTagCompound data) {
        final NBTTagList diskList = new NBTTagList();
        final NBTTagList fluidDiskList = new NBTTagList();
        final NBTTagList typedDiskList = new NBTTagList();
        final Map<String, DataStorage> snapshot = new LinkedHashMap<>(disks);

        for (Map.Entry<String, DataStorage> entry : snapshot.entrySet()) {
            final DataStorage storage = entry.getValue();
            if (storage == null || storage.isEmpty()) continue;
            final NBTTagCompound disk = new NBTTagCompound();
            disk.setString(InfinityCellConstants.DISKUUID, storage.getUUID());
            if (storage.isItemChannel()) {
                disk.setTag(InfinityCellConstants.DISKDATA, storage.writeToNBT());
                diskList.appendTag(disk);
            } else if (storage.isFluidChannel()) {
                // 历史形状：流体条目内层键与外层同名，改动会让旧存档读空。
                disk.setTag(InfinityCellConstants.FLUID_DISKLIST, storage.writeToNBT());
                fluidDiskList.appendTag(disk);
            } else {
                disk.setString(InfinityCellConstants.DISK_TYPEID, storage.getTypeId());
                disk.setTag(InfinityCellConstants.DISKDATA, storage.writeToNBT());
                typedDiskList.appendTag(disk);
            }
        }
        for (Map.Entry<String, NBTTagList> entry : new LinkedHashMap<>(unresolved).entrySet()) {
            final String composite = entry.getKey()
                .substring(UNRESOLVED_PREFIX.length());
            final int split = composite.indexOf(KEY_SEPARATOR);
            if (split <= 0) continue;
            final NBTTagCompound disk = new NBTTagCompound();
            disk.setString(InfinityCellConstants.DISKUUID, composite.substring(0, split));
            disk.setString(InfinityCellConstants.DISK_TYPEID, composite.substring(split + 1));
            disk.setTag(InfinityCellConstants.DISKDATA, entry.getValue());
            typedDiskList.appendTag(disk);
        }

        // 物品/流体继续写旧键，玩家回滚到旧 jar 仍能读回这两通道。
        data.setTag(InfinityCellConstants.DISKLIST, diskList);
        data.setTag(InfinityCellConstants.FLUID_DISKLIST, fluidDiskList);
        data.setTag(InfinityCellConstants.TYPED_DISKLIST, typedDiskList);
    }

    public void postChanges(DataStorage storage) {
        this.setDirty(true);
    }

    /** 诊断/测试用：当前登记的通道桶数量。 */
    public int bucketCount() {
        return disks.size();
    }

    /** 测试用：尚未解析出通道的第三方桶数量。 */
    int unresolvedCount() {
        return unresolved.size();
    }
}

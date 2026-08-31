package com.miaokatze.gtit.common.items.infinitycell;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.WorldSavedData;

import appeng.api.storage.StorageChannel;
import appeng.util.Platform;

public class StorageManager extends WorldSavedData {

    private static final String DATA_NAME = "GTIT_InfinityCellStorage";
    // ConcurrentHashMap：writeToNBT 在服务器保存时遍历本 map，而 tooltip 等
    // 客户端线程路径会并发读，避免 CME 直接打崩服务器。
    private final Map<UUID, DataStorage> disks = new ConcurrentHashMap<>();
    private static StorageManager instance;

    public StorageManager(String name) {
        super(name);
        this.setDirty(true);
        instance = this;
    }

    public static StorageManager getInstance() {
        return instance;
    }

    public DataStorage getStorage(String uuid, StorageChannel channel) {
        UUID uid;
        DataStorage d;
        try {
            uid = UUID.fromString(uuid);
        } catch (Exception ignored) {
            do {
                uid = UUID.randomUUID();
            } while (disks.get(uid) != null);
        }
        d = disks.get(uid);
        if (d == null) {
            d = new DataStorage(uid, channel);
            // 仅服务器线程注册：物品 tooltip 在客户端渲染线程经 getCellInventory
            // 构造 inventory，若在此 put 会与保存期 writeToNBT 遍历竞争（历史 CME 崩溃），
            // 且无 UUID 新元件每次悬停都会泄漏随机 UUID 条目。客户端返回临时实例即可。
            if (Platform.isServer()) {
                disks.put(uid, d);
            }
        }
        return d;
    }

    public DataStorage getStorage(ItemStack item) {
        if (item.getItem() instanceof IInfinityCellItem cellItem) {
            NBTTagCompound data = Platform.openNbtData(item);
            return this.getStorage(data.getString(InfinityCellConstants.DISKUUID), cellItem.getChannel());
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
        Map<UUID, DataStorage> d = new HashMap<>();
        NBTTagList diskList = data.getTagList(InfinityCellConstants.DISKLIST, 10);
        for (int i = 0; i < diskList.tagCount(); i++) {
            NBTTagCompound disk = diskList.getCompoundTagAt(i);
            UUID uid = UUID.fromString(disk.getString(InfinityCellConstants.DISKUUID));
            d.put(
                uid,
                DataStorage
                    .readFromNBT(uid, disk.getTagList(InfinityCellConstants.DISKDATA, 10), StorageChannel.ITEMS));
        }
        NBTTagList fluidDiskList = data.getTagList(InfinityCellConstants.FLUID_DISKLIST, 10);
        for (int i = 0; i < fluidDiskList.tagCount(); i++) {
            NBTTagCompound disk = fluidDiskList.getCompoundTagAt(i);
            UUID uid = UUID.fromString(disk.getString(InfinityCellConstants.DISKUUID));
            d.put(
                uid,
                DataStorage.readFromNBT(
                    uid,
                    disk.getTagList(InfinityCellConstants.FLUID_DISKLIST, 10),
                    StorageChannel.FLUIDS));
        }
        disks.clear();
        disks.putAll(d);
    }

    @Override
    public void writeToNBT(NBTTagCompound data) {
        NBTTagList diskList = new NBTTagList();
        NBTTagList fluidDiskList = new NBTTagList();
        for (Map.Entry<UUID, DataStorage> entry : disks.entrySet()) {
            if (entry.getValue()
                .isEmpty()) continue;
            NBTTagCompound disk = new NBTTagCompound();
            disk.setString(
                InfinityCellConstants.DISKUUID,
                entry.getKey()
                    .toString());
            if (entry.getValue()
                .getChannel() == StorageChannel.ITEMS) {
                disk.setTag(
                    InfinityCellConstants.DISKDATA,
                    entry.getValue()
                        .writeToNBT());
                diskList.appendTag(disk);
            } else {
                disk.setTag(
                    InfinityCellConstants.FLUID_DISKLIST,
                    entry.getValue()
                        .writeToNBT());
                fluidDiskList.appendTag(disk);
            }
        }
        data.setTag(InfinityCellConstants.DISKLIST, diskList);
        data.setTag(InfinityCellConstants.FLUID_DISKLIST, fluidDiskList);
    }

    public void postChanges(DataStorage storage) {
        this.setDirty(true);
    }
}

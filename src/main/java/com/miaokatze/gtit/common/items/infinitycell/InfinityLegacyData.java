// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtit.common.items.infinitycell;

import static com.miaokatze.gtit.common.items.infinitycell.InfinityCellConstants.DISKDATA;
import static com.miaokatze.gtit.common.items.infinitycell.InfinityCellConstants.DISKLIST;
import static com.miaokatze.gtit.common.items.infinitycell.InfinityCellConstants.DISKUUID;
import static com.miaokatze.gtit.common.items.infinitycell.InfinityCellConstants.DISK_TYPEID;
import static com.miaokatze.gtit.common.items.infinitycell.InfinityCellConstants.FLUID_DISKLIST;
import static com.miaokatze.gtit.common.items.infinitycell.InfinityCellConstants.TYPED_DISKLIST;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

/** Converts the old WorldSavedData payload without decoding any stack or third-party channel. */
final class InfinityLegacyData {

    static final String ITEM = "item";
    static final String FLUID = "fluid";

    private InfinityLegacyData() {}

    static Map<UUID, NBTTagCompound> read(NBTTagCompound in) throws IOException {
        final Map<UUID, NBTTagCompound> disks = new LinkedHashMap<>();
        readChannels(disks, list(in, DISKLIST), ITEM, DISKDATA);
        readChannels(disks, list(in, FLUID_DISKLIST), FLUID, FLUID_DISKLIST);
        readChannels(disks, list(in, TYPED_DISKLIST), null, DISKDATA);
        return disks;
    }

    private static void readChannels(Map<UUID, NBTTagCompound> disks, NBTTagList entries, String type, String contents)
        throws IOException {
        for (int i = 0; i < entries.tagCount(); i++) {
            final NBTTagCompound entry = entries.getCompoundTagAt(i);
            final String channel = type != null ? type : entry.getString(DISK_TYPEID);
            final UUID uuid;
            try {
                uuid = UUID.fromString(entry.getString(DISKUUID));
            } catch (IllegalArgumentException e) {
                throw new IOException("Invalid UUID in legacy infinity cell data", e);
            }
            final NBTTagCompound channels = disks.computeIfAbsent(uuid, ignored -> new NBTTagCompound());
            if (channel.isEmpty() || channels.hasKey(channel) || !entry.hasKey(contents, 9)) {
                throw new IOException("Invalid or duplicate legacy infinity cell channel " + uuid + "/" + channel);
            }
            channels.setTag(channel, list(entry, contents));
        }
    }

    static NBTTagList list(NBTTagCompound data, String key) throws IOException {
        if (!data.hasKey(key)) return new NBTTagList();
        if (data.getTag(key) instanceof NBTTagList list && (list.tagCount() == 0 || list.func_150303_d() == 10)) {
            return list;
        }
        throw new IOException("Invalid infinity cell NBT list: " + key);
    }

    static void write(Map<UUID, NBTTagCompound> disks, NBTTagCompound out) {
        final NBTTagList items = new NBTTagList();
        final NBTTagList fluids = new NBTTagList();
        final NBTTagList other = new NBTTagList();
        disks.forEach((uuid, channels) -> {
            for (final String channel : channels.func_150296_c()) {
                final NBTTagCompound entry = new NBTTagCompound();
                entry.setString(DISKUUID, uuid.toString());
                if (ITEM.equals(channel)) {
                    entry.setTag(DISKDATA, channels.getTag(channel));
                    items.appendTag(entry);
                } else if (FLUID.equals(channel)) {
                    entry.setTag(FLUID_DISKLIST, channels.getTag(channel));
                    fluids.appendTag(entry);
                } else {
                    entry.setString(DISK_TYPEID, channel);
                    entry.setTag(DISKDATA, channels.getTag(channel));
                    other.appendTag(entry);
                }
            }
        });
        out.setTag(DISKLIST, items);
        out.setTag(FLUID_DISKLIST, fluids);
        out.setTag(TYPED_DISKLIST, other);
    }
}

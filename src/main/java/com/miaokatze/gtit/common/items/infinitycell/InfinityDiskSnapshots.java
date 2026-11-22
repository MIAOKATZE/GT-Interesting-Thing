// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtit.common.items.infinitycell;

import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

/** Detached NBT committed by the world writer. Keys are transient and never enter the disk format. */
final class InfinityDiskSnapshots {

    static final class Update {

        final boolean replace;
        final Map<String, Map<Object, NBTTagCompound>> channels;

        Update(boolean replace, Map<String, Map<Object, NBTTagCompound>> channels) {
            this.replace = replace;
            this.channels = channels;
        }
    }

    private final InfinityDiskFiles files;
    private final Map<UUID, Map<String, Map<Object, NBTTagCompound>>> committed = new LinkedHashMap<>();

    InfinityDiskSnapshots(InfinityDiskFiles files) {
        this.files = files;
    }

    void seed(UUID uuid, Map<String, Map<Object, NBTTagCompound>> initial) {
        committed.put(uuid, initial);
    }

    void save(Map<UUID, Update> updates) throws IOException {
        final Map<UUID, Map<String, Map<Object, NBTTagCompound>>> next = new LinkedHashMap<>();
        final Map<UUID, NBTTagCompound> records = new LinkedHashMap<>();
        updates.forEach((uuid, update) -> {
            final Map<String, Map<Object, NBTTagCompound>> disk = update.replace ? update.channels
                : merge(committed.getOrDefault(uuid, Collections.emptyMap()), update.channels);
            next.put(uuid, disk);
            final NBTTagCompound record = new NBTTagCompound();
            disk.forEach((type, entries) -> {
                final NBTTagList list = new NBTTagList();
                entries.values()
                    .forEach(list::appendTag);
                record.setTag(type, list);
            });
            records.put(uuid, record);
        });
        files.save(records);
        // A failed publication must leave the base used for the next attempt unchanged.
        next.forEach((uuid, disk) -> {
            if (disk.isEmpty()) committed.remove(uuid);
            else committed.put(uuid, disk);
        });
    }

    private static Map<String, Map<Object, NBTTagCompound>> merge(Map<String, Map<Object, NBTTagCompound>> base,
        Map<String, Map<Object, NBTTagCompound>> changes) {
        final Map<String, Map<Object, NBTTagCompound>> result = new LinkedHashMap<>(base);
        changes.forEach((type, edits) -> {
            final Map<Object, NBTTagCompound> entries = new LinkedHashMap<>(
                result.getOrDefault(type, Collections.emptyMap()));
            edits.forEach((key, tag) -> {
                if (tag == null) entries.remove(key);
                else entries.put(key, tag);
            });
            if (entries.isEmpty()) result.remove(type);
            else result.put(type, entries);
        });
        return result;
    }
}

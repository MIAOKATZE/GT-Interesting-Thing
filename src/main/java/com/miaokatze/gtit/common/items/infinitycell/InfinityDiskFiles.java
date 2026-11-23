// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtit.common.items.infinitycell;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.zip.GZIPInputStream;

import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTSizeTracker;
import net.minecraft.nbt.NBTTagCompound;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.miaokatze.gtit.util.AtomicNbtFile;

/** The manifest is the commit point. Disk revisions are immutable once named. */
final class InfinityDiskFiles {

    private static final Logger LOG = LogManager.getLogger("gtit");
    private static final int FORMAT = 2;
    private static final long NBT_BUDGET = 256L * 1024 * 1024;
    private static final String FORMAT_KEY = "format";
    private static final String DISKS = "disks";
    private static final String CHANNELS = "channels";

    private final Path directory;
    private final Path manifest;
    private final Set<Path> garbage = new HashSet<>();
    private Map<UUID, UUID> published = new LinkedHashMap<>();
    private int format = FORMAT;

    InfinityDiskFiles(Path directory) {
        this.directory = directory;
        this.manifest = directory.resolve("manifest.dat");
    }

    Map<UUID, NBTTagCompound> load() throws IOException {
        if (Files.notExists(manifest)) return null;
        final NBTTagCompound document = read(manifest);
        format = document.getInteger(FORMAT_KEY);
        if (!document.hasKey(FORMAT_KEY, 3) || (format != 1 && format != FORMAT) || !document.hasKey(DISKS, 10)) {
            throw new IOException("Unknown or invalid infinity cell manifest: " + manifest);
        }
        final Map<UUID, UUID> revisions = new LinkedHashMap<>();
        final Map<UUID, NBTTagCompound> data = new LinkedHashMap<>();
        final NBTTagCompound entries = document.getCompoundTag(DISKS);
        for (final String key : entries.func_150296_c()) {
            final UUID disk = parseId(key);
            final UUID revision = parseId(entries.getString(key));
            final Path file = revisionFile(disk, revision);
            final NBTTagCompound record = read(file);
            if (!key.equals(record.getString(InfinityCellConstants.DISKUUID)) || !record.hasKey(CHANNELS, 10)) {
                throw new IOException("Invalid infinity cell disk: " + file);
            }
            revisions.put(disk, revision);
            data.put(disk, record.getCompoundTag(CHANNELS));
        }
        published = revisions;
        return data;
    }

    boolean isLegacyFormat() {
        return format == 1;
    }

    void rejectMissingManifest() throws IOException {
        if (Files.notExists(directory)) return;
        try (DirectoryStream<Path> files = Files.newDirectoryStream(directory, "*.dat")) {
            for (final Path file : files) {
                throw new IOException("Missing infinity cell manifest with disk data present: " + file);
            }
        }
    }

    void removeAbandonedRevisions() {
        // Only after a committed manifest and all its disk files have been read successfully.
        try (DirectoryStream<Path> files = Files.newDirectoryStream(directory, "*.dat")) {
            for (final Path file : files) {
                final String name = file.getFileName()
                    .toString();
                // <UUID>.<UUID>.dat. Ignore unrelated files and any unrecognized name.
                if (name.length() != 77 || name.charAt(36) != '.') continue;
                try {
                    final UUID disk = parseId(name.substring(0, 36));
                    final UUID revision = parseId(name.substring(37, 73));
                    if (!revision.equals(published.get(disk))) garbage.add(file);
                } catch (IOException ignored) {}
            }
        } catch (IOException | SecurityException e) {
            LOG.warn("Could not list old infinity cell disk revisions in {}", directory, e);
        }
        removeGarbage();
    }

    void save(Map<UUID, NBTTagCompound> changed) throws IOException {
        publish(stage(changed));
    }

    Map<UUID, UUID> stage(Map<UUID, NBTTagCompound> changed) throws IOException {
        Files.createDirectories(directory);
        final Map<UUID, UUID> next = new LinkedHashMap<>(published);
        final UUID revision = UUID.randomUUID();
        for (final Map.Entry<UUID, NBTTagCompound> entry : changed.entrySet()) {
            final UUID disk = entry.getKey();
            final UUID previous = next.remove(disk);
            if (previous != null) garbage.add(revisionFile(disk, previous));
            if (entry.getValue()
                .hasNoTags()) continue;
            final Path file = revisionFile(disk, revision);
            final NBTTagCompound record = new NBTTagCompound();
            record.setString(InfinityCellConstants.DISKUUID, disk.toString());
            record.setTag(CHANNELS, entry.getValue());
            AtomicNbtFile.writeCompressedAndSync(record, file.toFile(), InfinityDiskFiles::read);
            garbage.add(file);
            next.put(disk, revision);
        }
        return next;
    }

    void publish(Map<UUID, UUID> next) throws IOException {
        final NBTTagCompound entries = new NBTTagCompound();
        next.forEach((disk, revision) -> entries.setString(disk.toString(), revision.toString()));
        final NBTTagCompound document = new NBTTagCompound();
        document.setInteger(FORMAT_KEY, FORMAT);
        document.setTag(DISKS, entries);
        AtomicNbtFile.writeCompressedAndSync(document, manifest.toFile(), InfinityDiskFiles::read);
        published = next;
        format = FORMAT;
        removeGarbage();
    }

    private void removeGarbage() {
        // An aborted stage may have put a still-live revision in the set.
        published.forEach((disk, revision) -> garbage.remove(revisionFile(disk, revision)));
        garbage.removeIf(file -> {
            try {
                Files.deleteIfExists(file);
                return true;
            } catch (IOException | SecurityException e) {
                LOG.warn("Could not delete old infinity cell disk revision {}", file, e);
                return false;
            }
        });
    }

    private Path revisionFile(UUID disk, UUID revision) {
        return directory.resolve(disk + "." + revision + ".dat");
    }

    private static UUID parseId(String value) throws IOException {
        try {
            final UUID id = UUID.fromString(value);
            if (id.toString()
                .equals(value)) return id;
        } catch (IllegalArgumentException ignored) {}
        throw new IOException("Invalid infinity cell disk or revision ID: " + value);
    }

    static NBTTagCompound read(Path file) throws IOException {
        try (InputStream input = Files.newInputStream(file);
            DataInputStream data = new DataInputStream(new BufferedInputStream(new GZIPInputStream(input, 8192)))) {
            final NBTTagCompound result = CompressedStreamTools.func_152456_a(data, new NBTSizeTracker(NBT_BUDGET));
            if (data.read() != -1) throw new IOException("Trailing data in infinity cell NBT: " + file);
            return result;
        } catch (RuntimeException e) {
            throw new IOException("Invalid or oversized infinity cell NBT: " + file, e);
        }
    }

    @Override
    public String toString() {
        return directory.toString();
    }
}

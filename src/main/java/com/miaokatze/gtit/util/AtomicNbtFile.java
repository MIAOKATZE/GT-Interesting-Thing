// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtit.util;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;

/** NBT replacement that never deletes the previous record before the new record is installed. */
public final class AtomicNbtFile {

    private AtomicNbtFile() {}

    public static void write(NBTTagCompound value, File destination) throws IOException {
        write(value, destination, false);
    }

    public static void writeCompressedAndSync(NBTTagCompound value, File destination) throws IOException {
        write(value, destination, true);
    }

    private static void write(NBTTagCompound value, File destination, boolean compressedAndSynced) throws IOException {
        Path target = destination.toPath()
            .toAbsolutePath();
        Path temporary = Files.createTempFile(target.getParent(), destination.getName() + ".", ".tmp");
        boolean installed = false;
        try {
            if (compressedAndSynced) {
                try (OutputStream output = Files.newOutputStream(temporary)) {
                    CompressedStreamTools.writeCompressed(value, output);
                }
                try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                    channel.force(true);
                }
            } else {
                CompressedStreamTools.write(value, temporary.toFile());
            }
            // Fail closed on providers without atomic replacement; callers retain their dirty state.
            // Minecraft safeWrite instead deletes the old record and ignores renameTo(false).
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            installed = true;
        } finally {
            if (!installed) Files.deleteIfExists(temporary);
        }
    }
}

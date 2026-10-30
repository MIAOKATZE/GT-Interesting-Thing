package com.miaokatze.gtit.util;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;

/** NBT replacement that never deletes the previous record before the new record is installed. */
public final class AtomicNbtFile {

    private AtomicNbtFile() {}

    public static void write(NBTTagCompound value, File destination) throws IOException {
        Path target = destination.toPath()
            .toAbsolutePath();
        Path temporary = Files.createTempFile(target.getParent(), destination.getName() + ".", ".tmp");
        boolean installed = false;
        try {
            CompressedStreamTools.write(value, temporary.toFile());
            // Fail closed on providers without atomic replacement; callers retain their dirty state.
            // Minecraft safeWrite instead deletes the old record and ignores renameTo(false).
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            installed = true;
        } finally {
            if (!installed) Files.deleteIfExists(temporary);
        }
    }
}

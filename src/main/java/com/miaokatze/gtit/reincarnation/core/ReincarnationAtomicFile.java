package com.miaokatze.gtit.reincarnation.core;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

/** Throwing, server-thread-only checkpoint writer. Never degrades to a non-atomic replacement. */
public final class ReincarnationAtomicFile {

    private ReincarnationAtomicFile() {}

    public static void write(Path target, byte[] bytes) throws IOException {
        Path absolute = target.toAbsolutePath();
        Files.createDirectories(absolute.getParent());
        Path temporary = absolute.resolveSibling(absolute.getFileName() + ".tmp");
        boolean opened = false;
        try {
            // A leftover temporary symlink must not redirect a checkpoint into another file.
            try (FileChannel channel = FileChannel.open(
                temporary, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS)) {
                opened = true;
                ByteBuffer buffer = ByteBuffer.wrap(bytes);
                while (buffer.hasRemaining()) {
                    channel.write(buffer);
                }
                channel.force(true);
            }
            Files.move(temporary, absolute, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } finally {
            if (opened) {
                Files.deleteIfExists(temporary);
            }
        }
    }
}

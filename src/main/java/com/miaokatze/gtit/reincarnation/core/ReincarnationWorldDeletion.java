package com.miaokatze.gtit.reincarnation.core;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

/** Deletes only the selected save tree, never the targets of symbolic links inside it. */
public final class ReincarnationWorldDeletion {

    private ReincarnationWorldDeletion() {}

    public static void deleteTree(Path root) throws IOException {
        // The default walk does not FOLLOW_LINKS: linked directories, including the root,
        // are visited as files and only the link itself is removed.
        Files.walkFileTree(root, new SimpleFileVisitor<Path>() {

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path directory, IOException failure) throws IOException {
                if (failure != null) {
                    throw failure;
                }
                Files.delete(directory);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}

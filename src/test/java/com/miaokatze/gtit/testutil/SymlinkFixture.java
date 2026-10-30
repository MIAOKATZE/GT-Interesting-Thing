package com.miaokatze.gtit.testutil;

import java.io.IOException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.miaokatze.gtit.reincarnation.core.ReincarnationWorldDeletion;

/** Probe fixture permissions before registering link-only cases; production failures remain test failures. */
public final class SymlinkFixture {

    private SymlinkFixture() {}

    public static boolean available(String... caseNames) throws IOException {
        Path base = Files.createTempDirectory("gtit-symlink-probe-");
        try {
            Path target = Files.write(base.resolve("target"), new byte[] { 1 });
            try {
                Files.createSymbolicLink(base.resolve("link"), target);
            } catch (FileSystemException | UnsupportedOperationException | SecurityException unavailable) {
                for (String name : caseNames) System.out.println("SKIP " + name + ": " + unavailable);
                return false;
            }
            return true;
        } finally {
            ReincarnationWorldDeletion.deleteTree(base);
        }
    }
}

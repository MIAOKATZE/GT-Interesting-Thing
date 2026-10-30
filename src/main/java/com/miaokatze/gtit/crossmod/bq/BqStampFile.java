package com.miaokatze.gtit.crossmod.bq;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

/** Filesystem-only stamp persistence, independent of the optional BetterQuesting runtime. */
final class BqStampFile {

    private BqStampFile() {}

    /** Strict read-modify-write: only a genuinely absent file is an empty stamp. */
    static void update(File file, String stampKey, String version) throws IOException {
        update(file, stampKey, version, BqStampFile::readExisting);
    }

    @FunctionalInterface
    interface StampReader {

        JsonObject read(Path target) throws IOException;
    }

    /** Bounded read seam proves that read failure cannot publish or discard sibling pack metadata. */
    static void update(File file, String stampKey, String version, StampReader reader) throws IOException {
        JsonObject stamp = reader.read(
            file.toPath()
                .toAbsolutePath());
        stamp.addProperty(stampKey, version);
        write(file, stamp);
    }

    private static JsonObject readExisting(Path target) throws IOException {
        try (Reader reader = Files.newBufferedReader(target, StandardCharsets.UTF_8)) {
            return new JsonParser().parse(reader)
                .getAsJsonObject();
        } catch (NoSuchFileException missing) {
            // A failed stat is not evidence of absence (e.g. an inaccessible existing parent).
            if (!Files.notExists(target)) throw missing;
            return new JsonObject();
        } catch (JsonParseException | IllegalStateException malformed) {
            // Explicit policy: preserve malformed/non-object existing bytes; log and retry, never reset {}.
            throw new IOException("Refusing to replace an unreadable or malformed existing BQ stamp", malformed);
        }
    }

    /**
     * Create the fresh world's directory and publish only a fully written stamp. If atomic replacement is
     * unavailable, fail without truncating the previous stamp; the injector logs and retries next startup.
     * update() supplies the complete strictly read dual-key object; sibling keys and metadata survive.
     * Atomic visibility is provided, not a power-loss/fsync durability guarantee.
     */
    static void write(File file, JsonObject stamp) throws IOException {
        write(
            file,
            stamp,
            (temporary, target) -> Files
                .move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING));
    }

    @FunctionalInterface
    interface AtomicPublisher {

        void publish(Path temporary, Path target) throws IOException;
    }

    /** Package-local publication seam permits deterministic unsupported-atomic-move failure checks. */
    static void write(File file, JsonObject stamp, AtomicPublisher publisher) throws IOException {
        Path target = file.toPath()
            .toAbsolutePath();
        Path directory = target.getParent();
        // Also rejects a parent that exists as a regular file instead of a directory.
        Files.createDirectories(directory);
        Path temporary = Files.createTempFile(directory, file.getName() + ".", ".tmp");
        try {
            try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
                new Gson().toJson(stamp, writer);
            }
            publisher.publish(temporary, target);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}

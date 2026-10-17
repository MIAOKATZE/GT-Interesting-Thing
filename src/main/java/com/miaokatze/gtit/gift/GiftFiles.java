package com.miaokatze.gtit.gift;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/** Writes succeed before live state changes. Existing malformed files are never replaced by defaults. */
final class GiftFiles {

    static final Gson JSON = new GsonBuilder().setPrettyPrinting()
        .disableHtmlEscaping()
        .create();
    static final int MAX_BYTES = 4 * 1024 * 1024;

    private GiftFiles() {}

    static <T> T read(Path path, Class<T> type) throws IOException {
        if (Files.size(path) > MAX_BYTES) throw new IOException("Gift file exceeds limit: " + path);
        try {
            T result = JSON.fromJson(new String(Files.readAllBytes(path), StandardCharsets.UTF_8), type);
            if (result == null) throw new IOException("Empty gift file: " + path);
            return result;
        } catch (RuntimeException e) {
            throw new IOException("Invalid gift file: " + path, e);
        }
    }

    static void write(Path path, Object value) throws IOException {
        byte[] bytes = JSON.toJson(value)
            .getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_BYTES) throw new IOException("Gift file exceeds limit");
        Files.createDirectories(
            path.toAbsolutePath()
                .getParent());
        Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
        try {
            Files.write(temporary, bytes);
            Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}

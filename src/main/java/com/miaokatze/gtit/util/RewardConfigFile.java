package com.miaokatze.gtit.util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/** Small file boundary for sign-in, online-time and blessing configuration. */
public final class RewardConfigFile {

    private RewardConfigFile() {}

    public static JsonObject readObject(Path path) throws IOException {
        JsonElement parsed = new JsonParser().parse(new String(Files.readAllBytes(path), StandardCharsets.UTF_8));
        if (!parsed.isJsonObject()) throw new IOException("Reward configuration root must be an object: " + path);
        return parsed.getAsJsonObject();
    }

    public static void write(Path path, String json) throws IOException {
        Path parent = path.toAbsolutePath().getParent();
        Files.createDirectories(parent);
        Path temporary = Files.createTempFile(parent, path.getFileName().toString(), ".tmp");
        try {
            Files.write(temporary, json.getBytes(StandardCharsets.UTF_8));
            Files.move(temporary, path.toAbsolutePath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}

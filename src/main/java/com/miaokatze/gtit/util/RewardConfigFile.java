// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtit.util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

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
        Path parent = path.toAbsolutePath()
            .getParent();
        Files.createDirectories(parent);
        com.miaokatze.miaokatzecore.api.io.AtomicFilePublisher
            .publishAtomic(path, json.getBytes(StandardCharsets.UTF_8));
    }
}

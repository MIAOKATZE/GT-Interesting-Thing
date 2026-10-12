package com.miaokatze.gtit.hologram;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

/** Independent share files; client supplied names never become arbitrary filesystem paths. */
final class HologramBlueprintSharing {

    static final int MAX_BYTES = 8 * 1024 * 1024;
    private static final Path DIRECTORY = Paths.get("config", "gtit", "hologram-blueprints");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting()
        .create();

    private HologramBlueprintSharing() {}

    private static final class Share {

        int schema = 2;
        HologramPresets.Preset blueprint;
    }

    static void exportFile(String name, HologramPresets.Preset preset) throws IOException {
        write(resolve(name), preset);
    }

    static HologramPresets.Preset importFile(String name) throws IOException {
        return read(resolve(name));
    }

    static Path resolve(String name) throws IOException {
        if (name == null || !name.matches("[a-zA-Z0-9_-]{1,64}")) throw new IOException("蓝图文件名只能包含字母、数字、下划线和短横线");
        return DIRECTORY.resolve(name + ".json");
    }

    static void write(Path file, HologramPresets.Preset preset) throws IOException {
        HologramPresets.Preset copy = HologramPresets.copy(preset);
        HologramPresets.validate(copy);
        Share share = new Share();
        share.blueprint = copy;
        byte[] bytes = GSON.toJson(share)
            .getBytes(StandardCharsets.UTF_8);
        atomicWrite(file, bytes);
    }

    static void atomicWrite(Path file, byte[] bytes) throws IOException {
        if (bytes.length > MAX_BYTES) throw new IOException("蓝图分享文件超过8MiB上限");
        Path parent = file.toAbsolutePath()
            .getParent();
        Files.createDirectories(parent);
        Path temp = Files.createTempFile(parent, "hologram-blueprint-", ".tmp");
        try {
            Files.write(temp, bytes);
            Files
                .move(temp, file.toAbsolutePath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    static HologramPresets.Preset read(Path file) throws IOException {
        return decode(new String(readBytes(file), StandardCharsets.UTF_8));
    }

    static byte[] readBytes(Path file) throws IOException {
        if (Files.size(file) > MAX_BYTES) throw new IOException("蓝图文件超过8MiB上限");
        try (InputStream input = Files.newInputStream(file)) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) {
                if (count > MAX_BYTES - output.size()) throw new IOException("蓝图文件超过8MiB上限");
                output.write(buffer, 0, count);
            }
            return output.toByteArray();
        }
    }

    static void boundedJson(String json) throws IOException {
        if (json.length() > MAX_BYTES) throw new IOException("蓝图JSON超出上限");
        int depth = 0;
        boolean string = false, escaped = false;
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            if (string) {
                if (escaped) escaped = false;
                else if (c == '\\') escaped = true;
                else if (c == '"') string = false;
            } else if (c == '"') string = true;
            else if (c == '{' || c == '[') {
                if (++depth > 32) throw new IOException("蓝图JSON嵌套超出上限");
            } else if (c == '}' || c == ']') depth--;
        }
    }

    static HologramPresets.Preset decode(String json) throws IOException {
        boundedJson(json);
        try {
            JsonElement root = new JsonParser().parse(json);
            if (!root.isJsonObject() || !root.getAsJsonObject()
                .has("schema")
                || !root.getAsJsonObject()
                    .get("schema")
                    .isJsonPrimitive()
                || !root.getAsJsonObject()
                    .get("schema")
                    .getAsJsonPrimitive()
                    .isNumber()
                || !"2".equals(
                    root.getAsJsonObject()
                        .get("schema")
                        .getAsString()))
                throw new IOException("蓝图分享文件schema无效");
            validatePresetSchema(
                root.getAsJsonObject()
                    .get("blueprint"));
            Share share = GSON.fromJson(root, Share.class);
            HologramPresets.validate(share.blueprint);
            return share.blueprint;
        } catch (RuntimeException invalid) {
            throw new IOException("蓝图分享JSON无效", invalid);
        }
    }

    static void validatePresetSchema(JsonElement entry) throws IOException {
        if (entry == null || !entry.isJsonObject()) throw new IOException("蓝图条目无效");
        if (entry.getAsJsonObject()
            .has("schema")) {
            JsonElement schema = entry.getAsJsonObject()
                .get("schema");
            if (!schema.isJsonPrimitive() || !schema.getAsJsonPrimitive()
                .isNumber()
                || !schema.getAsString()
                    .matches("[12]"))
                throw new IOException("蓝图条目schema无效");
        }
        integerFields(entry, "controllerId", "controllerFacing");
        for (String rows : new String[] { "cells", "palette", "structures", "hintAreas" }) {
            JsonElement list = entry.getAsJsonObject()
                .get(rows);
            if (list == null) continue;
            if (!list.isJsonArray()) throw new IOException("蓝图条目列表格式无效");
            for (JsonElement row : list.getAsJsonArray()) {
                if (!row.isJsonObject()) throw new IOException("蓝图条目对象格式无效");
                integerFields(
                    row,
                    "x",
                    "y",
                    "z",
                    "x1",
                    "y1",
                    "z1",
                    "x2",
                    "y2",
                    "z2",
                    "damage",
                    "defaultDamage",
                    "metadata",
                    "count",
                    "facing",
                    "controllerId");
            }
        }
    }

    private static void integerFields(JsonElement entry, String... keys) throws IOException {
        for (String key : keys) {
            JsonElement value = entry.getAsJsonObject()
                .get(key);
            if (value == null || value.isJsonNull() && key.equals("controllerFacing")) continue;
            if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive()
                .isNumber()
                || !value.getAsString()
                    .matches("-?[0-9]+"))
                throw new IOException("蓝图整数字段类型无效：" + key);
            BigInteger integer = new BigInteger(value.getAsString());
            if (integer.compareTo(BigInteger.valueOf(Integer.MIN_VALUE)) < 0
                || integer.compareTo(BigInteger.valueOf(Integer.MAX_VALUE)) > 0)
                throw new IOException("蓝图整数字段超出范围：" + key);
        }
    }
}

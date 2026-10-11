package com.miaokatze.gtit.hologram;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.miaokatze.gtit.testutil.SimpleAssert;

/** Tests the production storage transaction without initializing the GT machine registry. */
final class HologramPresetPersistenceRegression {

    private HologramPresetPersistenceRegression() {}

    static void run() {
        Path directory = null;
        Object previousCache = null;
        Field cache = null;
        try {
            cache = HologramPresets.class.getDeclaredField("cached");
            cache.setAccessible(true);
            previousCache = cache.get(null);
            Method put = HologramPresets.class
                .getDeclaredMethod("put", List.class, HologramPresets.Preset.class, boolean.class);
            Method write = HologramPresets.class.getDeclaredMethod("write", Path.class, List.class);
            put.setAccessible(true);
            write.setAccessible(true);
            directory = Files.createTempDirectory("gtit-preset-storage-");
            Path file = directory.resolve("presets.json");
            List<HologramPresets.Preset> initial = new ArrayList<>();
            for (int i = 0; i < 32; i++) initial.add(preset("preset_" + i, "Original " + i));
            write.invoke(null, file, initial);
            byte[] originalBytes = Files.readAllBytes(file);

            List<HologramPresets.Preset> updated = HologramPresets.all();
            HologramPresets.Preset edit = preset("preset_5", "Updated");
            HologramPresets.Cell target = new HologramPresets.Cell();
            target.item = "minecraft:stone";
            target.x = 2;
            target.facing = 5;
            edit.cells.add(target);
            put.invoke(null, updated, edit, true);
            SimpleAssert.eq(32, updated.size(), "updating a full library does not add a row");
            SimpleAssert.eq("preset_5", updated.get(5).id, "update preserves preset ID and row");
            SimpleAssert.eq("Original 5", HologramPresets.get("preset_5").name, "staging does not change cache");
            expectIo(put, updated, preset("missing", "Missing"), true);
            expectIo(put, updated, edit, false);

            // A nonempty directory cannot be atomically replaced by the JSON file.
            Path blocked = directory.resolve("blocked");
            Files.createDirectory(blocked);
            Files.write(blocked.resolve("keep"), new byte[] { 1 });
            expectIo(write, blocked, updated);
            SimpleAssert.eq("Original 5", HologramPresets.get("preset_5").name, "failed IO leaves cache unchanged");
            SimpleAssert.that(
                java.util.Arrays.equals(originalBytes, Files.readAllBytes(file)),
                "failed IO leaves prior file unchanged");
            write.invoke(null, file, updated);
            SimpleAssert.eq("Updated", HologramPresets.get("preset_5").name, "successful update publishes cache");
            SimpleAssert
                .eq(5, HologramPresets.get("preset_5").cells.get(0).facing, "updated target facing survives storage");
            edit.name = "Caller mutation";
            HologramPresets.get("preset_5").name = "Reader mutation";
            SimpleAssert.eq("Updated", HologramPresets.get("preset_5").name, "cache owns detached snapshots");

            List<HologramPresets.Preset> copies = HologramPresets.all();
            copies.remove(31);
            HologramPresets.Preset saved = (HologramPresets.Preset) put.invoke(null, copies, edit, false);
            UUID.fromString(saved.id);
            SimpleAssert.that(!saved.id.equals(edit.id), "save copy gets a fresh UUID");
            SimpleAssert.eq("preset_5", edit.id, "staging a copy does not alter the original identity");
            write.invoke(null, file, copies);
            SimpleAssert.eq(
                32,
                HologramPresets.all()
                    .size(),
                "save copy adds exactly one row");
            SimpleAssert.eq("Updated", HologramPresets.get("preset_5").name, "save copy preserves original contents");
            Files.delete(blocked.resolve("keep"));
            Files.delete(blocked);
            Files.delete(file);
        } catch (Exception failure) {
            throw new AssertionError("preset persistence transaction regression", failure);
        } finally {
            try {
                if (cache != null) cache.set(null, previousCache);
                if (directory != null) Files.deleteIfExists(directory);
            } catch (Exception failure) {
                throw new AssertionError("preset fixture cleanup", failure);
            }
        }
    }

    private static HologramPresets.Preset preset(String id, String name) {
        HologramPresets.Preset preset = new HologramPresets.Preset();
        preset.id = id;
        preset.name = name;
        preset.controllerType = "storage-only-fixture";
        return preset;
    }

    private static void expectIo(Method method, Object... arguments) throws Exception {
        try {
            method.invoke(null, arguments);
            throw new AssertionError("Expected an IOException from " + method.getName());
        } catch (InvocationTargetException failure) {
            if (!(failure.getCause() instanceof IOException)) throw failure;
        }
    }
}

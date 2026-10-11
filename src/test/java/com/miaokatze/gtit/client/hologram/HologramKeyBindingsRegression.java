package com.miaokatze.gtit.client.hologram;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.lwjgl.input.Keyboard;

import com.miaokatze.gtit.testutil.SimpleAssert;

/** Exercises saved user bindings without initializing a window or reading native keyboard state. */
public final class HologramKeyBindingsRegression {

    private HologramKeyBindingsRegression() {}

    public static void run() {
        Path directory = null;
        try {
            directory = Files.createTempDirectory("gtit-hologram-bindings-");
            Path config = directory.resolve("keys.properties");
            HologramKeyBindings bindings = new HologramKeyBindings();
            bindings.load(config.toFile());
            SimpleAssert.eq(1, bindings.match(Keyboard.KEY_C, 1), "Ctrl C copies by default");
            SimpleAssert.eq(-1, bindings.match(Keyboard.KEY_C, 0), "plain C cannot copy accidentally");
            SimpleAssert.eq(3, bindings.match(Keyboard.KEY_DELETE, 0), "Delete restores casing");
            SimpleAssert.eq(4, bindings.match(Keyboard.KEY_R, 0), "R cycles legal block faces");
            SimpleAssert.eq(-1, bindings.match(Keyboard.KEY_R, 1), "Ctrl R has no default planner action");
            SimpleAssert.that(!bindings.bind(0, Keyboard.KEY_C, 1), "duplicate shortcut rejected");
            SimpleAssert.that(!bindings.bind(0, Keyboard.KEY_ESCAPE, 0), "Esc stays available to close dialogs");
            SimpleAssert.that(!bindings.bind(0, Keyboard.KEY_1, 0), "mode key stays reserved");
            SimpleAssert.that(!bindings.bind(0, Keyboard.KEY_R, 8), "unknown modifier bit rejected");
            SimpleAssert.that(bindings.bind(0, Keyboard.KEY_K, 3), "Ctrl Shift K can replace cut shortcut");
            SimpleAssert.that(bindings.save(), "user bindings persisted");
            HologramKeyBindings restored = new HologramKeyBindings();
            restored.load(config.toFile());
            SimpleAssert.eq(0, restored.match(Keyboard.KEY_K, 3), "custom modifiers restored");
            SimpleAssert.eq(-1, restored.match(Keyboard.KEY_X, 1), "old shortcut no longer active");
            Files.write(config, "editCut=46:1\neditCopy=46:1\n".getBytes(StandardCharsets.UTF_8));
            restored.load(config.toFile());
            SimpleAssert.eq(0, restored.match(Keyboard.KEY_X, 1), "invalid conflicting file falls back atomically");
            SimpleAssert.eq(1, restored.match(Keyboard.KEY_C, 1), "all defaults restored after invalid file");
        } catch (java.io.IOException failure) {
            throw new AssertionError(failure);
        } finally {
            if (directory != null) try {
                Files.deleteIfExists(directory.resolve("keys.properties"));
                Files.deleteIfExists(directory);
            } catch (java.io.IOException cleanupFailure) {
                throw new AssertionError(cleanupFailure);
            }
        }
    }
}

package com.miaokatze.gtit.client.hologram;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;

import org.lwjgl.input.Keyboard;

/** Client-local planner shortcuts; no world operation is bound here. */
final class HologramKeyBindings {

    static final String[] ACTIONS = { "editCut", "editCopy", "editPaste", "restoreCell", "rotateCell", "editUndo",
        "editRedo" };
    static final String[] LABELS = { "剪切目标", "复制目标", "粘贴目标", "恢复默认壳", "六面转向", "撤销规划", "重做规划" };
    private static final int[] DEFAULT_KEYS = { Keyboard.KEY_X, Keyboard.KEY_C, Keyboard.KEY_V, Keyboard.KEY_DELETE,
        Keyboard.KEY_R, Keyboard.KEY_Z, Keyboard.KEY_Y };
    private static final int[] DEFAULT_MODS = { 1, 1, 1, 0, 0, 1, 1 };
    private final int[] keys = new int[ACTIONS.length], mods = new int[ACTIONS.length];
    private File file;

    HologramKeyBindings() {
        defaults();
    }

    void defaults() {
        System.arraycopy(DEFAULT_KEYS, 0, keys, 0, keys.length);
        System.arraycopy(DEFAULT_MODS, 0, mods, 0, mods.length);
    }

    void load(File target) {
        file = target;
        defaults();
        if (!target.isFile()) return;
        Properties properties = new Properties();
        try (FileInputStream input = new FileInputStream(target)) {
            properties.load(input);
            int[] loadedKeys = new int[keys.length], loadedMods = new int[mods.length];
            for (int i = 0; i < keys.length; i++) {
                String[] value = properties.getProperty(ACTIONS[i], keys[i] + ":" + mods[i])
                    .split(":");
                loadedKeys[i] = Integer.parseInt(value[0]);
                loadedMods[i] = Integer.parseInt(value[1]);
                if (loadedKeys[i] <= 0 || loadedKeys[i] >= Keyboard.KEYBOARD_SIZE
                    || loadedKeys[i] == Keyboard.KEY_ESCAPE
                    || isModifier(loadedKeys[i])
                    || loadedMods[i] < 0
                    || loadedMods[i] > 7
                    || reserved(loadedKeys[i], loadedMods[i])) return;
                for (int j = 0; j < i; j++)
                    if (loadedKeys[j] == loadedKeys[i] && loadedMods[j] == loadedMods[i]) return;
            }
            System.arraycopy(loadedKeys, 0, keys, 0, keys.length);
            System.arraycopy(loadedMods, 0, mods, 0, mods.length);
        } catch (IOException | RuntimeException invalid) {
            defaults();
        }
    }

    boolean save() {
        if (file == null) return false;
        Properties properties = new Properties();
        for (int i = 0; i < keys.length; i++) properties.setProperty(ACTIONS[i], keys[i] + ":" + mods[i]);
        File parent = file.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) return false;
        try (FileOutputStream output = new FileOutputStream(file)) {
            properties.store(output, "GTIT hologram planner shortcuts (ctrl=1, shift=2, alt=4)");
            return true;
        } catch (IOException failed) {
            return false;
        }
    }

    boolean bind(int index, int key, int modifiers) {
        if (index < 0 || index >= keys.length || modifiers < 0 || modifiers > 7) return false;
        if (key <= 0 || key >= Keyboard.KEYBOARD_SIZE
            || key == Keyboard.KEY_ESCAPE
            || isModifier(key)
            || reserved(key, modifiers)) return false;
        for (int i = 0; i < keys.length; i++) if (i != index && keys[i] == key && mods[i] == modifiers) return false;
        keys[index] = key;
        mods[index] = modifiers;
        return true;
    }

    int match(int key, int modifiers) {
        for (int i = 0; i < keys.length; i++) if (keys[i] == key && mods[i] == modifiers) return i;
        return -1;
    }

    String label(int index) {
        return ((mods[index] & 1) != 0 ? "Ctrl+" : "") + ((mods[index] & 2) != 0 ? "Shift+" : "")
            + ((mods[index] & 4) != 0 ? "Alt+" : "")
            + Keyboard.getKeyName(keys[index]);
    }

    static int modifiers() {
        return (Keyboard.isKeyDown(Keyboard.KEY_LCONTROL) || Keyboard.isKeyDown(Keyboard.KEY_RCONTROL) ? 1 : 0)
            | (Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT) ? 2 : 0)
            | (Keyboard.isKeyDown(Keyboard.KEY_LMENU) || Keyboard.isKeyDown(Keyboard.KEY_RMENU) ? 4 : 0);
    }

    private static boolean reserved(int key, int modifiers) {
        return modifiers == 0 && (key == Keyboard.KEY_1 || key == Keyboard.KEY_2 || key == Keyboard.KEY_3);
    }

    static boolean isModifier(int key) {
        return key == Keyboard.KEY_LCONTROL || key == Keyboard.KEY_RCONTROL
            || key == Keyboard.KEY_LSHIFT
            || key == Keyboard.KEY_RSHIFT
            || key == Keyboard.KEY_LMENU
            || key == Keyboard.KEY_RMENU;
    }
}

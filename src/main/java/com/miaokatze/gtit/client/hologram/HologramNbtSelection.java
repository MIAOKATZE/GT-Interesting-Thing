package com.miaokatze.gtit.client.hologram;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;

/** Local staged root-key choices; blueprint storage and one placement each have independent instances. */
final class HologramNbtSelection {

    private boolean all = true;
    private boolean retainCovers = true;
    private final Set<String> keys = new LinkedHashSet<>();

    void load(boolean all, NBTTagList source) {
        load(all, source, true);
    }

    void load(boolean all, NBTTagList source, boolean retainCovers) {
        this.all = all;
        this.retainCovers = retainCovers;
        keys.clear();
        for (int i = 0; i < source.tagCount(); i++) keys.add(source.getStringTagAt(i));
    }

    boolean selected(String key) {
        return all || keys.contains(key);
    }

    void toggle(String key, List<String> available) {
        if (all) {
            all = false;
            keys.addAll(available);
        }
        if (!keys.remove(key)) keys.add(key);
    }

    void all(boolean value) {
        all = value;
        keys.clear();
    }

    boolean all() {
        return all;
    }

    boolean retainCovers() {
        return retainCovers;
    }

    void retainCovers(boolean value) {
        retainCovers = value;
    }

    NBTTagList keys() {
        NBTTagList list = new NBTTagList();
        for (String key : keys) list.appendTag(new NBTTagString(key));
        return list;
    }
}

package com.miaokatze.gtit.hologram;

import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.ModContainer;

/** Registry namespaces plus structure provenance retain dependencies even for vanilla-only structures. */
final class HologramBlueprintMetadata {

    private HologramBlueprintMetadata() {}

    static final class Status {

        final String mod, required, installed;
        final boolean missing;
        final int comparison;

        Status(String mod, String required, String installed) {
            this.mod = mod;
            this.required = required;
            this.installed = installed;
            this.missing = installed == null;
            this.comparison = missing ? -1 : Integer.signum(compareVersions(installed, required));
        }
    }

    static List<Status> statuses(HologramPresets.Preset preset) {
        return statuses(preset, installedVersions());
    }

    static List<Status> statuses(HologramPresets.Preset preset, Map<String, String> installed) {
        List<Status> result = new ArrayList<>();
        for (Map.Entry<String, String> entry : preset.modVersions.entrySet())
            result.add(new Status(entry.getKey(), entry.getValue(), installed.get(entry.getKey())));
        return result;
    }

    static void captureVersions(HologramPresets.Preset preset) {
        Map<String, String> installed = installedVersions();
        add(preset, installed, preset.sourceMod);
        for (HologramPresets.Structure structure : preset.structures) add(preset, installed, structure.sourceMod);
        for (HologramPresets.Cell cell : preset.cells) {
            addRegistry(preset, installed, cell.block);
            addRegistry(preset, installed, cell.item);
            addPayload(preset, installed, cell.tileNbt, true);
            addPayload(preset, installed, cell.itemNbt, false);
        }
        for (HologramPresets.Cell cell : preset.palette) {
            addRegistry(preset, installed, cell.block);
            addRegistry(preset, installed, cell.item);
            addPayload(preset, installed, cell.tileNbt, true);
            addPayload(preset, installed, cell.itemNbt, false);
        }
    }

    private static void addPayload(HologramPresets.Preset preset, Map<String, String> installed, String payload,
        boolean covers) {
        if (payload == null || payload.isEmpty()) return;
        try {
            String selected = HologramBlueprintNbt.filterCaptured(
                payload,
                preset.retainNbt ? preset.retainedNbtKeys : java.util.Collections.emptyList(),
                preset.retainCovers);
            NBTTagCompound tag = HologramBlueprintNbt.decode(selected);
            for (String registry : HologramBlueprintNbt.resourceRegistries(tag))
                addRegistry(preset, installed, registry);
            if (covers && preset.retainCovers) {
                try {
                    for (ItemStack material : HologramBlueprintCovers.materials(selected)) {
                        addRegistry(
                            preset,
                            installed,
                            String.valueOf(Item.itemRegistry.getNameForObject(material.getItem())));
                        if (material.hasTagCompound())
                            for (String registry : HologramBlueprintNbt.resourceRegistries(material.getTagCompound()))
                                addRegistry(preset, installed, registry);
                    }
                } catch (IOException | LinkageError unavailable) {
                    // Missing/unregistered covers retain their stable registry marker and are rejected at placement.
                }
            }
        } catch (IOException invalid) {
            // Storage validation reports invalid payloads; dependency collection cannot fabricate an identity.
        }
    }

    private static void addRegistry(HologramPresets.Preset preset, Map<String, String> installed, String registry) {
        if (registry != null && !registry.startsWith("unresolved:") && registry.indexOf(':') > 0)
            add(preset, installed, registry.substring(0, registry.indexOf(':')));
    }

    private static void add(HologramPresets.Preset preset, Map<String, String> installed, String mod) {
        if (mod == null || mod.isEmpty()) return;
        String version = installed.get(mod);
        if (version != null) preset.modVersions.put(mod, version);
        else if (!preset.modVersions.containsKey(mod)) preset.modVersions.put(mod, "unknown");
    }

    static List<String> warnings(HologramPresets.Preset preset) {
        return warnings(preset, installedVersions());
    }

    static List<String> warnings(HologramPresets.Preset preset, Map<String, String> installed) {
        List<String> warnings = new ArrayList<>();
        for (Status status : statuses(preset, installed)) {
            if (status.missing) warnings.add("缺少模组 " + status.mod + "（蓝图版本 " + status.required + "）");
            else if (status.comparison < 0)
                warnings.add("模组 " + status.mod + " 版本较低：" + status.installed + " < " + status.required);
            else if (status.comparison > 0)
                warnings.add("模组 " + status.mod + " 版本较新：" + status.installed + " > " + status.required);
        }
        return warnings;
    }

    private static Map<String, String> installedVersions() {
        Map<String, String> result = new LinkedHashMap<>();
        result.put("minecraft", "1.7.10");
        List<ModContainer> mods = Loader.instance()
            .getModList();
        if (mods != null) for (ModContainer mod : mods) result.put(mod.getModId(), mod.getVersion());
        return result;
    }

    static int compareVersions(String left, String right) {
        if (left == null || right == null || "unknown".equals(left) || "unknown".equals(right)) return 0;
        List<String> a = parts(left), b = parts(right);
        int size = Math.max(a.size(), b.size());
        for (int i = 0; i < size; i++) {
            String x = i < a.size() ? a.get(i) : "0", y = i < b.size() ? b.get(i) : "0";
            int order;
            if (x.matches("[0-9]+") && y.matches("[0-9]+")) order = new BigInteger(x).compareTo(new BigInteger(y));
            else if (x.matches("[0-9]+")) order = 1;
            else if (y.matches("[0-9]+")) order = -1;
            else order = x.compareToIgnoreCase(y);
            if (order != 0) return order;
        }
        return 0;
    }

    private static List<String> parts(String version) {
        List<String> result = new ArrayList<>();
        Matcher matcher = Pattern.compile("[0-9]+|[a-zA-Z]+")
            .matcher(version);
        while (matcher.find()) result.add(matcher.group());
        return result;
    }
}

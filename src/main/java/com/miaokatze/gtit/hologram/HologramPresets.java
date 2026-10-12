package com.miaokatze.gtit.hologram;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.gtnewhorizon.structurelib.alignment.enumerable.ExtendedFacing;

import gregtech.api.GregTechAPI;
import gregtech.api.metatileentity.implementations.MTEEnhancedMultiBlockBase;
import gregtech.common.blocks.ItemMachines;

/** Server-owned bounded storage. Schema 1 prefabs remain readable alongside schema 2 blueprints. */
final class HologramPresets {

    private static final Path FILE = Paths.get("config", "gtit", "hologram-presets.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting()
        .create();
    private static List<Preset> cached;

    private HologramPresets() {}

    static final class Cell {

        String item;
        String defaultItem;
        String block, tileNbt, itemNbt, prompt, structureId;
        List<String> structureIds;
        int metadata;
        boolean excluded;
        int defaultDamage;
        int damage, count = 1, x, y, z, facing = -1;
        boolean controller;

        ItemStack stack() {
            Object found = item == null ? null : Item.itemRegistry.getObject(item);
            return found instanceof Item ? new ItemStack((Item) found, 1, damage) : null;
        }

        ItemStack defaultStack() {
            Object found = defaultItem == null ? null : Item.itemRegistry.getObject(defaultItem);
            return found instanceof Item ? new ItemStack((Item) found, 1, defaultDamage) : null;
        }

        Block block() {
            Object found = block == null ? null : Block.blockRegistry.getObject(block);
            return found instanceof Block && found != Blocks.air ? (Block) found : null;
        }

        boolean missing() {
            ItemStack resolved = stack();
            return (item != null && resolved == null) || (block != null && block() == null)
                || (resolved != null && resolved.getItem() instanceof ItemMachines
                    && (damage < 0 || damage >= GregTechAPI.METATILEENTITIES.length
                        || GregTechAPI.METATILEENTITIES[damage] == null));
        }

        List<String> memberships() {
            if (structureIds != null && !structureIds.isEmpty()) return new ArrayList<>(structureIds);
            if (structureId == null || structureId.isEmpty()) return new ArrayList<>();
            return new ArrayList<>(java.util.Collections.singletonList(structureId));
        }

        boolean belongsTo(String id) {
            if (id == null || id.isEmpty()) return false;
            return structureIds != null && !structureIds.isEmpty() ? structureIds.contains(id) : id.equals(structureId);
        }
    }

    static final class HintArea {

        int x1, y1, z1, x2, y2, z2;
        String prompt;
    }

    /** Captured provenance and relative outline anchor; live formation is always recomputed by the server. */
    static final class Structure {

        String id, name, sourceMod, sourceClass;
        int x, y, z, controllerId = -1;
        boolean enabled = true;
    }

    static final class Preset {

        String id, name, controllerType;
        int schema = 1;
        boolean retainNbt = true, retainCovers = true;
        String prompt, sourceMod, sourceClass;
        Map<String, String> modVersions = new LinkedHashMap<>();
        List<HintArea> hintAreas = new ArrayList<>();
        List<Structure> structures = new ArrayList<>();
        // Absent in legacy files: null selects the complete captured NBT baseline.
        List<String> retainedNbtKeys;
        int controllerId = -1;
        // Optional in schema 1: old templates infer NORMAL/NONE from the controller cell's front.
        Integer controllerFacing;
        List<Cell> cells = new ArrayList<>();
        List<Cell> palette = new ArrayList<>();

        boolean allows(ItemStack stack) {
            if (stack == null) return false;
            String item = String.valueOf(Item.itemRegistry.getNameForObject(stack.getItem()));
            for (Cell entry : palette)
                if (item.equals(entry.item) && entry.damage == stack.getItemDamage()) return true;
            return false;
        }
    }

    private static final class FileData {

        int schema = 2;
        List<Preset> presets = new ArrayList<>();
    }

    static Cell cell(ItemStack stack, int x, int y, int z, int facing, boolean controller) {
        Cell cell = new Cell();
        cell.item = String.valueOf(Item.itemRegistry.getNameForObject(stack.getItem()));
        cell.damage = stack.getItemDamage();
        cell.x = x;
        cell.y = y;
        cell.z = z;
        cell.facing = facing;
        cell.controller = controller;
        return cell;
    }

    static synchronized List<Preset> all() throws IOException {
        if (cached != null) return copies(cached);
        List<Preset> result = new ArrayList<>();
        if (Files.exists(FILE)) {
            if (Files.size(FILE) > 8 * 1024 * 1024) throw new IOException("模板配置超过8MiB上限");
            try {
                FileData data = decode(new String(HologramBlueprintSharing.readBytes(FILE), StandardCharsets.UTF_8));
                if (data == null || (data.schema != 1 && data.schema != 2)
                    || data.presets == null
                    || data.presets.size() > 32) throw new IOException("模板配置格式或数量无效");
                Set<String> ids = new HashSet<>();
                for (Preset preset : data.presets) {
                    Preset loaded = preset != null && preset.schema == 1 ? upgradeLegacy(preset) : preset;
                    validate(loaded);
                    if (!ids.add(loaded.id)) throw new IOException("模板ID重复");
                    result.add(loaded);
                }
            } catch (RuntimeException invalid) {
                throw new IOException("模板JSON无效", invalid);
            }
        }
        cached = result;
        return copies(result);
    }

    static Preset get(String id) throws IOException {
        for (Preset preset : all()) if (preset.id.equals(id)) return copy(preset);
        return null;
    }

    static synchronized void save(Preset preset) throws IOException {
        List<Preset> list = all();
        Preset saved = put(list, preset, false);
        validate(saved);
        write(FILE, list);
        preset.id = saved.id;
    }

    static synchronized void update(Preset preset) throws IOException {
        List<Preset> list = all();
        Preset updated = put(list, preset, true);
        validate(updated);
        write(FILE, list);
    }

    private static Preset put(List<Preset> list, Preset preset, boolean update) throws IOException {
        if (preset == null) throw new IOException("模板无效");
        Preset result = copy(preset);
        if (update) {
            int index = indexOf(list, result.id);
            if (index < 0) throw new IOException("要更新的模板不存在");
            list.set(index, result);
        } else {
            if (list.size() >= 32) throw new IOException("最多保存32个模板");
            result.id = UUID.randomUUID()
                .toString();
            list.add(result);
        }
        return result;
    }

    private static int indexOf(List<Preset> list, String id) {
        for (int i = 0; i < list.size(); i++) if (list.get(i).id.equals(id)) return i;
        return -1;
    }

    static Preset copy(Preset preset) {
        return GSON.fromJson(GSON.toJson(preset), Preset.class);
    }

    /** Runtime-only migration: registry availability never determines whether a legacy template can be inspected. */
    static Preset upgradeLegacy(Preset preset) throws IOException {
        if (preset == null || preset.schema != 1
            || preset.controllerType == null
            || preset.controllerType.length() > 256
            || preset.controllerId < 0
            || preset.cells == null
            || preset.cells.isEmpty()
            || preset.cells.size() > HologramCapture.LIMIT
            || preset.palette == null
            || preset.palette.isEmpty()
            || preset.palette.size() > 4096) throw new IOException("旧模板格式无效");
        Preset upgraded = copy(preset);
        Set<String> allowed = new HashSet<>();
        for (Cell cell : upgraded.palette) {
            legacyCellFormat(cell);
            allowed.add(cell.item + ":" + cell.damage);
        }
        int controllers = 0;
        Set<String> positions = new HashSet<>();
        for (Cell cell : upgraded.cells) {
            legacyCellFormat(cell);
            if (!positions.add(cell.x + "," + cell.y + "," + cell.z) || !allowed.contains(cell.item + ":" + cell.damage)
                || cell.defaultItem != null && !allowed.contains(cell.defaultItem + ":" + cell.defaultDamage))
                throw new IOException("旧模板位置重复或目标不在候选表中");
            if (cell.controller) {
                controllers++;
                if (cell.x != 0 || cell.y != 0
                    || cell.z != 0
                    || cell.damage != upgraded.controllerId
                    || !upgraded.controllerType.endsWith(":" + upgraded.controllerId))
                    throw new IOException("旧模板控制器锚点或声明身份无效");
                if (upgraded.controllerFacing != null
                    && (upgraded.controllerFacing < 0 || upgraded.controllerFacing >= ExtendedFacing.values().length
                        || ExtendedFacing.byIndex(upgraded.controllerFacing)
                            .getDirection()
                            .ordinal() != cell.facing))
                    throw new IOException("旧模板控制器姿态与正面不一致");
                ItemStack stack = cell.stack();
                if (stack != null && !(stack.getItem() instanceof ItemMachines)) throw new IOException("旧模板控制器物品身份无效");
                if (stack != null && cell.damage < GregTechAPI.METATILEENTITIES.length
                    && GregTechAPI.METATILEENTITIES[cell.damage] != null
                    && (!(GregTechAPI.METATILEENTITIES[cell.damage] instanceof MTEEnhancedMultiBlockBase)
                        || !upgraded.controllerType.equals(
                            GregTechAPI.METATILEENTITIES[cell.damage].getClass()
                                .getName() + ":" + cell.damage)
                        || upgraded.controllerFacing != null
                            && !((MTEEnhancedMultiBlockBase<?>) GregTechAPI.METATILEENTITIES[cell.damage])
                                .getAlignmentLimits()
                                .isNewExtendedFacingValid(ExtendedFacing.byIndex(upgraded.controllerFacing))))
                    throw new IOException("旧模板控制器声明身份与已注册机器不一致");
            }
            legacyBlockIdentity(cell);
        }
        if (controllers != 1) throw new IOException("旧模板必须包含一个控制器");
        for (Cell cell : upgraded.palette) legacyBlockIdentity(cell);
        upgraded.schema = 2;
        upgraded.retainedNbtKeys = null;
        upgraded.sourceClass = upgraded.controllerType.substring(0, upgraded.controllerType.lastIndexOf(':'));
        for (Cell cell : upgraded.cells) legacyDependency(upgraded, cell.item);
        for (Cell cell : upgraded.palette) legacyDependency(upgraded, cell.item);
        return upgraded;
    }

    private static void legacyDependency(Preset preset, String registry) {
        int separator = registry.indexOf(':');
        if (separator > 0 && !preset.modVersions.containsKey(registry.substring(0, separator)))
            preset.modVersions.put(registry.substring(0, separator), "unknown");
    }

    private static void legacyCellFormat(Cell cell) throws IOException {
        if (cell == null || cell.item == null
            || cell.count != 1
            || cell.damage < 0
            || cell.defaultDamage < 0
            || cell.facing < -1
            || cell.facing > 5
            || cell.tileNbt != null
            || cell.itemNbt != null) throw new IOException("旧模板物品、数量、朝向或设备NBT无效");
        registryText(cell.item, false);
        registryText(cell.defaultItem, true);
        coordinates(cell.x, cell.y, cell.z);
        ItemStack stack = cell.stack();
        if (stack != null && (!(stack.getItem() instanceof ItemMachines)
            || cell.damage < GregTechAPI.METATILEENTITIES.length && GregTechAPI.METATILEENTITIES[cell.damage] != null))
            validateCell(cell);
    }

    private static void legacyBlockIdentity(Cell cell) {
        cell.block = null;
        cell.metadata = 0;
        ItemStack stack = cell.stack();
        if (stack == null) return;
        Block block = Block.getBlockFromItem(stack.getItem());
        if (block == null || block == Blocks.air) return;
        cell.block = String.valueOf(Block.blockRegistry.getNameForObject(block));
        cell.metadata = stack.getItem() instanceof ItemMachines ? 0
            : stack.getItem()
                .getMetadata(cell.damage);
    }

    private static List<Preset> copies(List<Preset> list) {
        List<Preset> result = new ArrayList<>();
        for (Preset preset : list) result.add(copy(preset));
        return result;
    }

    static void exportBlueprint(String fileId, Preset preset) throws IOException {
        HologramBlueprintSharing.exportFile(fileId, preset);
    }

    static Preset importBlueprint(String fileId) throws IOException {
        return HologramBlueprintSharing.importFile(fileId);
    }

    private static void write(Path file, List<Preset> list) throws IOException {
        FileData data = new FileData();
        data.presets = list;
        byte[] bytes = GSON.toJson(data)
            .getBytes(StandardCharsets.UTF_8);
        if (bytes.length > 8 * 1024 * 1024) throw new IOException("模板配置超过8MiB上限");
        Files.createDirectories(file.getParent());
        Path temp = Files.createTempFile(file.getParent(), "hologram-presets-", ".tmp");
        try {
            Files.write(temp, bytes);
            Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            cached = copies(list);
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    static synchronized NBTTagList labels() throws IOException {
        if (cached == null) all();
        NBTTagList result = new NBTTagList();
        // State packets need only immutable labels/counts, never detached copies of the device payloads.
        for (Preset preset : cached) {
            NBTTagCompound row = new NBTTagCompound();
            row.setString("id", preset.id);
            row.setString("name", preset.name);
            row.setInteger("cells", preset.cells.size());
            row.setInteger("schema", preset.schema);
            result.appendTag(row);
        }
        return result;
    }

    private static FileData decode(String json) throws IOException {
        HologramBlueprintSharing.boundedJson(json);
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
            || !root.getAsJsonObject()
                .get("schema")
                .getAsString()
                .matches("[12]"))
            throw new IOException("模板schema无效");
        if (root.isJsonObject() && root.getAsJsonObject()
            .has("presets")) {
            JsonElement presets = root.getAsJsonObject()
                .get("presets");
            if (presets.isJsonArray()) for (JsonElement entry : presets.getAsJsonArray()) {
                HologramBlueprintSharing.validatePresetSchema(entry);
                if (!entry.isJsonObject() || !entry.getAsJsonObject()
                    .has("controllerFacing")) continue;
                JsonElement facing = entry.getAsJsonObject()
                    .get("controllerFacing");
                if (!facing.isJsonNull() && (!facing.isJsonPrimitive() || !facing.getAsJsonPrimitive()
                    .isNumber()
                    || !facing.getAsString()
                        .matches("[0-9]+")))
                    throw new IOException("模板控制器姿态必须是合法整数，不接受字符串或小数");
                if (!facing.isJsonNull() && new java.math.BigInteger(facing.getAsString())
                    .compareTo(java.math.BigInteger.valueOf(ExtendedFacing.values().length)) >= 0)
                    throw new IOException("模板控制器姿态超出合法范围");
            }
        }
        return GSON.fromJson(root, FileData.class);
    }

    static void validate(Preset preset) throws IOException {
        if (preset != null && preset.schema == 2) {
            validateBlueprint(preset);
            return;
        }
        if (preset != null && preset.schema != 1) throw new IOException("模板schema无效");
        if (preset == null || preset.id == null
            || !preset.id.matches("[a-zA-Z0-9_-]{1,64}")
            || preset.name == null
            || preset.name.trim()
                .isEmpty()
            || preset.name.length() > 48
            || preset.controllerType == null
            || preset.controllerType.length() > 256
            || preset.cells == null
            || preset.cells.isEmpty()
            || preset.cells.size() > HologramCapture.LIMIT
            || preset.palette == null
            || preset.palette.isEmpty()
            || preset.palette.size() > 4096) throw new IOException("模板名称、位置或候选数量无效");
        if (preset.controllerFacing != null
            && (preset.controllerFacing < 0 || preset.controllerFacing >= ExtendedFacing.values().length))
            throw new IOException("模板控制器姿态无效");
        Set<String> positions = new HashSet<>();
        int controllers = 0;
        for (Cell cell : preset.palette) validateCell(cell);
        for (Cell cell : preset.cells) {
            validateCell(cell);
            if (!positions.add(cell.x + "," + cell.y + "," + cell.z) || !preset.allows(cell.stack()))
                throw new IOException("模板位置重复或目标不在候选表中");
            if (cell.defaultItem != null && (cell.defaultStack() == null || !preset.allows(cell.defaultStack())))
                throw new IOException("模板默认外壳不在候选表中");
            if (cell.controller && (cell.x != 0 || cell.y != 0 || cell.z != 0 || cell.damage != preset.controllerId))
                throw new IOException("模板控制器锚点无效");
            if (cell.controller && preset.controllerFacing != null) {
                ExtendedFacing facing = ExtendedFacing.byIndex(preset.controllerFacing);
                if (facing.getDirection()
                    .ordinal() != cell.facing || preset.controllerId < 0
                    || preset.controllerId >= GregTechAPI.METATILEENTITIES.length
                    || !(GregTechAPI.METATILEENTITIES[preset.controllerId] instanceof MTEEnhancedMultiBlockBase)
                    || !((MTEEnhancedMultiBlockBase<?>) GregTechAPI.METATILEENTITIES[preset.controllerId])
                        .getAlignmentLimits()
                        .isNewExtendedFacingValid(facing))
                    throw new IOException("模板控制器姿态与正面不一致或机器不允许");
            }
            if (cell.controller) controllers++;
            if (cell.controller && (!(cell.stack()
                .getItem() instanceof ItemMachines) || cell.damage < 0
                || cell.damage >= GregTechAPI.METATILEENTITIES.length
                || !(GregTechAPI.METATILEENTITIES[cell.damage] instanceof MTEEnhancedMultiBlockBase)
                || !preset.controllerType.equals(
                    GregTechAPI.METATILEENTITIES[cell.damage].getClass()
                        .getName() + ":" + cell.damage)))
                throw new IOException("模板控制器物品身份无效");
        }
        if (controllers != 1) throw new IOException("模板必须包含一个控制器");
    }

    private static void validateBlueprint(Preset preset) throws IOException {
        if (preset.id == null || !preset.id.matches("[a-zA-Z0-9_-]{1,64}")
            || preset.name == null
            || preset.name.trim()
                .isEmpty()
            || preset.name.length() > 48
            || preset.cells == null
            || preset.cells.isEmpty()
            || preset.cells.size() > HologramCapture.LIMIT
            || preset.palette == null
            || preset.palette.size() > 4096
            || preset.hintAreas == null
            || preset.hintAreas.size() > 256
            || preset.modVersions == null
            || preset.modVersions.size() > 512
            || preset.structures == null
            || preset.structures.size() > 256) throw new IOException("蓝图名称、方块或提示区域数量无效");
        if (preset.retainedNbtKeys != null) {
            if (preset.retainedNbtKeys.size() > 512) throw new IOException("蓝图NBT条目选择超出上限");
            Set<String> selected = new HashSet<>();
            for (String key : preset.retainedNbtKeys) {
                if (key == null || key.isEmpty()
                    || key.length() > 256
                    || !selected.add(key)
                    || key.chars()
                        .anyMatch(value -> value < 32))
                    throw new IOException("蓝图NBT条目选择无效");
            }
        }
        text(preset.prompt, 4096);
        text(preset.controllerType, 256);
        text(preset.sourceMod, 256);
        text(preset.sourceClass, 512);
        if (preset.controllerFacing != null
            && (preset.controllerFacing < 0 || preset.controllerFacing >= ExtendedFacing.values().length))
            throw new IOException("蓝图控制器姿态无效");
        for (Map.Entry<String, String> entry : preset.modVersions.entrySet()) {
            registryText(entry.getKey(), false);
            text(entry.getValue(), 256);
        }
        Set<String> positions = new HashSet<>();
        Set<String> structureIds = new HashSet<>();
        for (Structure structure : preset.structures) {
            if (structure == null || structure.id == null
                || !structure.id.matches("[a-zA-Z0-9_,:-]{1,64}")
                || !structureIds.add(structure.id)
                || structure.controllerId < -1) throw new IOException("蓝图结构标识无效或重复");
            text(structure.name, 256);
            text(structure.sourceMod, 256);
            text(structure.sourceClass, 512);
            coordinates(structure.x, structure.y, structure.z);
        }
        for (Cell cell : preset.cells) {
            validateBlueprintCell(cell);
            filterSelectedPayload(preset, cell);
            if (!positions.add(cell.x + "," + cell.y + "," + cell.z)) throw new IOException("蓝图位置重复");
            validateMembership(cell, structureIds);
        }
        for (Cell cell : preset.palette) {
            validateBlueprintCell(cell);
            validateMembership(cell, structureIds);
            filterSelectedPayload(preset, cell);
        }
        for (HintArea area : preset.hintAreas) {
            if (area == null || area.x1 > area.x2 || area.y1 > area.y2 || area.z1 > area.z2)
                throw new IOException("蓝图提示区域无效");
            coordinates(area.x1, area.y1, area.z1);
            coordinates(area.x2, area.y2, area.z2);
            text(area.prompt, 4096);
        }
    }

    private static void validateMembership(Cell cell, Set<String> structures) throws IOException {
        if (cell.structureId != null && !cell.structureId.isEmpty() && !structures.contains(cell.structureId))
            throw new IOException("蓝图方块引用不存在的结构");
        if (cell.structureIds != null) {
            if (cell.structureIds.size() > structures.size()) throw new IOException("蓝图方块结构归属数量无效");
            Set<String> unique = new HashSet<>();
            for (String id : cell.structureIds) {
                if (id == null || !structures.contains(id) || !unique.add(id)) throw new IOException("蓝图方块结构归属无效或重复");
            }
        }
    }

    private static void filterSelectedPayload(Preset preset, Cell cell) throws IOException {
        List<String> keys = preset.retainNbt ? preset.retainedNbtKeys : java.util.Collections.emptyList();
        cell.tileNbt = HologramBlueprintNbt.filterCaptured(cell.tileNbt, keys, preset.retainCovers);
        cell.itemNbt = HologramBlueprintNbt.filterCaptured(cell.itemNbt, keys, preset.retainCovers);
    }

    private static void validateBlueprintCell(Cell cell) throws IOException {
        if (cell == null || (cell.item == null && cell.block == null)
            || cell.count != 1
            || cell.damage < 0
            || cell.metadata < 0
            || cell.metadata > 65535
            || cell.facing < -1
            || cell.facing > 5) throw new IOException("蓝图方块身份、数量或朝向无效");
        registryText(cell.item, true);
        registryText(cell.block, true);
        registryText(cell.defaultItem, true);
        coordinates(cell.x, cell.y, cell.z);
        text(cell.prompt, 4096);
        // filterSelectedPayload validates binary NBT budgets and strips unselected entries before storage.
    }

    private static void coordinates(int x, int y, int z) throws IOException {
        if (Math.abs((long) x) > 60000000L || Math.abs((long) y) > 256L || Math.abs((long) z) > 60000000L)
            throw new IOException("蓝图坐标超出范围");
    }

    private static void registryText(String value, boolean optional) throws IOException {
        if (value == null && optional) return;
        if (value == null || !value.matches("[a-zA-Z0-9_.:/-]{1,256}")) throw new IOException("蓝图注册名无效");
    }

    private static void text(String value, int limit) throws IOException {
        if (value != null && value.length() > limit) throw new IOException("蓝图文本超出上限");
    }

    private static void validateCell(Cell cell) throws IOException {
        if (cell == null || cell.stack() == null
            || cell.tileNbt != null
            || cell.itemNbt != null
            || cell.damage < 0
            || cell.count != 1
            || Math.abs((long) cell.x) > 60000000L
            || Math.abs((long) cell.y) > 256L
            || Math.abs((long) cell.z) > 60000000L
            || cell.facing < -1
            || cell.facing > 5) throw new IOException("模板物品、坐标或朝向无效");
        ItemStack stack = cell.stack();
        if (stack.getItem() instanceof ItemMachines) {
            int id = stack.getItemDamage();
            if (id >= GregTechAPI.METATILEENTITIES.length || GregTechAPI.METATILEENTITIES[id] == null)
                throw new IOException("模板GT机器ID未注册");
        } else {
            Block block = Block.getBlockFromItem(stack.getItem());
            if (!(stack.getItem() instanceof ItemBlock) || block == Blocks.air
                || block.hasTileEntity(
                    stack.getItem()
                        .getMetadata(stack.getItemDamage())))
                throw new IOException("模板普通方块没有安全放置路径");
        }
    }
}

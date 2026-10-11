package com.miaokatze.gtit.hologram;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
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

import gregtech.api.GregTechAPI;
import gregtech.api.metatileentity.implementations.MTEEnhancedMultiBlockBase;
import gregtech.common.blocks.ItemMachines;

/** Server-owned, bounded, state-free prefab configuration. No client cell or device NBT is accepted. */
final class HologramPresets {

    private static final Path FILE = Paths.get("config", "gtit", "hologram-presets.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting()
        .create();
    private static List<Preset> cached;

    private HologramPresets() {}

    static final class Cell {

        String item;
        String defaultItem;
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
    }

    static final class Preset {

        String id, name, controllerType;
        int controllerId = -1;
        List<Cell> cells = new ArrayList<>();
        List<Cell> palette = new ArrayList<>();

        boolean allows(ItemStack stack) {
            if (stack == null) return false;
            String item = String.valueOf(Item.itemRegistry.getNameForObject(stack.getItem()));
            for (Cell entry : palette)
                if (entry.item.equals(item) && entry.damage == stack.getItemDamage()) return true;
            return false;
        }
    }

    private static final class FileData {

        int schema = 1;
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
        if (cached != null) return new ArrayList<>(cached);
        List<Preset> result = new ArrayList<>();
        if (Files.exists(FILE)) {
            if (Files.size(FILE) > 8 * 1024 * 1024) throw new IOException("模板配置超过8MiB上限");
            try {
                FileData data = GSON
                    .fromJson(new String(Files.readAllBytes(FILE), StandardCharsets.UTF_8), FileData.class);
                if (data == null || data.schema != 1 || data.presets == null || data.presets.size() > 32)
                    throw new IOException("模板配置格式或数量无效");
                Set<String> ids = new HashSet<>();
                for (Preset preset : data.presets) {
                    validate(preset);
                    if (!ids.add(preset.id)) throw new IOException("模板ID重复");
                    result.add(preset);
                }
            } catch (RuntimeException invalid) {
                throw new IOException("模板JSON无效", invalid);
            }
        }
        cached = result;
        return new ArrayList<>(result);
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

    private static Preset copy(Preset preset) {
        return GSON.fromJson(GSON.toJson(preset), Preset.class);
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
            cached = new ArrayList<>(list);
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    static NBTTagList labels() throws IOException {
        NBTTagList result = new NBTTagList();
        for (Preset preset : all()) {
            NBTTagCompound row = new NBTTagCompound();
            row.setString("id", preset.id);
            row.setString("name", preset.name);
            row.setInteger("cells", preset.cells.size());
            result.appendTag(row);
        }
        return result;
    }

    private static void validate(Preset preset) throws IOException {
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

    private static void validateCell(Cell cell) throws IOException {
        if (cell == null || cell.stack() == null
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

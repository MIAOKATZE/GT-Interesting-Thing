package com.miaokatze.gtit.hologram;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import com.gtnewhorizon.structurelib.alignment.constructable.IConstructable;

import gregtech.api.GregTechAPI;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEEnhancedMultiBlockBase;
import gregtech.common.blocks.BlockFrameBox;

/** Server-owned region selection and resumable read-only world import. */
final class HologramArea {

    boolean selecting, imported, importing;
    int[] first, second;
    int x, y, z, minX, minY, minZ, maxX, maxY, maxZ;
    HologramPresets.Preset blueprint;
    final Map<String, Structure> structures = new LinkedHashMap<>();
    final Map<String, java.util.LinkedHashSet<String>> members = new java.util.HashMap<>();
    final java.util.Set<String> paletteKeys = new java.util.HashSet<>();
    final List<Structure> discovery = new ArrayList<>();
    int discoveryIndex;
    String active = "";

    static final class Structure {

        final String id;
        final int x, y, z;
        final MTEEnhancedMultiBlockBase<?> machine;
        boolean enabled = true;
        HologramCapture capture;

        Structure(int x, int y, int z, MTEEnhancedMultiBlockBase<?> machine) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.machine = machine;
            id = x + "," + y + "," + z;
        }
    }

    void point(int index, int xx, int yy, int zz) {
        if (index == 1) first = new int[] { xx, yy, zz };
        if (index == 2) second = new int[] { xx, yy, zz };
    }

    void begin(World world) throws IOException {
        if (first == null || second == null) throw new IOException("请先选择两个区域角点");
        minX = Math.min(first[0], second[0]);
        minY = Math.min(first[1], second[1]);
        minZ = Math.min(first[2], second[2]);
        maxX = Math.max(first[0], second[0]);
        maxY = Math.max(first[1], second[1]);
        maxZ = Math.max(first[2], second[2]);
        if (minY < 0 || maxY >= world.getHeight()) throw new IOException("区域高度无效");
        if (minX < -30000000 || maxX >= 30000000 || minZ < -30000000 || maxZ >= 30000000)
            throw new IOException("区域超出世界坐标边界");
        x = minX;
        y = minY;
        z = minZ;
        blueprint = new HologramPresets.Preset();
        blueprint.schema = 2;
        blueprint.name = "施工区域";
        blueprint.controllerType = "area";
        structures.clear();
        members.clear();
        discovery.clear();
        paletteKeys.clear();
        discoveryIndex = 0;
        importing = true;
        imported = false;
    }

    /** No world mutation or material extraction occurs during import. */
    boolean tick(EntityPlayerMP player, ItemStack trigger) throws IOException {
        World world = player.worldObj;
        for (int budget = 4096; y <= maxY && budget > 0; budget--) {
            if (!world.blockExists(x, y, z)) throw new IOException("区域包含未加载区块，导入已停止");
            Block block = world.getBlock(x, y, z);
            int meta = world.getBlockMetadata(x, y, z);
            TileEntity tile = world.getTileEntity(x, y, z);
            if (block != Blocks.air) {
                ItemStack sample = serverSample(block, meta, tile);
                HologramPresets.Cell cell = sample == null ? new HologramPresets.Cell()
                    : HologramPresets.cell(sample, x - minX, y - minY, z - minZ, -1, false);
                cell.x = x - minX;
                cell.y = y - minY;
                cell.z = z - minZ;
                cell.block = String.valueOf(Block.blockRegistry.getNameForObject(block));
                cell.metadata = meta;
                if (sample != null && sample.hasTagCompound())
                    cell.itemNbt = HologramBlueprintNbt.encodeCaptured(sample.getTagCompound());
                if (tile != null) {
                    NBTTagCompound data = new NBTTagCompound();
                    tile.writeToNBT(data);
                    cell.tileNbt = HologramBlueprintNbt.encodeCaptured(data);
                }
                if (tile instanceof IGregTechTileEntity) {
                    IGregTechTileEntity gt = (IGregTechTileEntity) tile;
                    cell.facing = gt.getFrontFacing()
                        .ordinal();
                    if (gt.getMetaTileEntity() instanceof MTEEnhancedMultiBlockBase
                        && gt.getMetaTileEntity() instanceof IConstructable) {
                        cell.controller = true;
                        Structure structure = new Structure(
                            x,
                            y,
                            z,
                            (MTEEnhancedMultiBlockBase<?>) gt.getMetaTileEntity());
                        structures.put(structure.id, structure);
                        addMember(structure.id, structure.id);
                        discovery.add(structure);
                    }
                }
                blueprint.cells.add(cell);
                if (sample != null && paletteKeys.add(String.valueOf(sample.getItem()) + ":" + sample.getItemDamage()))
                    blueprint.palette.add(HologramPresets.cell(sample, 0, 0, 0, -1, false));
            }
            if (++x > maxX) {
                x = minX;
                if (++z > maxZ) {
                    z = minZ;
                    y++;
                }
            }
        }
        if (y <= maxY) return false;
        if (discoveryIndex < discovery.size()) {
            Structure structure = discovery.get(discoveryIndex);
            if (world.getTileEntity(structure.x, structure.y, structure.z) != structure.machine.getBaseMetaTileEntity())
                throw new IOException("控制器在导入期间变化，请重新选择区域");
            structure.capture = HologramCapture
                .collect(structure.machine, trigger, structure.machine.getExtendedFacing(), structure.capture);
            if (!structure.capture.pending) {
                if (!structure.capture.incomplete)
                    for (Map.Entry<String, HologramCapture.Cell> entry : structure.capture.positions.entrySet()) {
                        HologramCapture.Cell cell = entry.getValue();
                        if (cell.x >= minX && cell.x <= maxX
                            && cell.y >= minY
                            && cell.y <= maxY
                            && cell.z >= minZ
                            && cell.z <= maxZ) addMember(entry.getKey(), structure.id);
                    }
                discoveryIndex++;
            }
            return false;
        }
        importing = false;
        imported = true;
        return true;
    }

    /** Inventory identity is read without client-only pick hooks or harvesting callbacks. */
    static ItemStack serverSample(Block block, int metadata, TileEntity tile) {
        if (tile instanceof IGregTechTileEntity)
            return new ItemStack(GregTechAPI.sBlockMachines, 1, ((IGregTechTileEntity) tile).getMetaTileID());
        if (block instanceof BlockFrameBox) return ((BlockFrameBox) block).getStackForm(1, metadata);
        Item item = Item.getItemFromBlock(block);
        return item == null ? null : new ItemStack(item, 1, block.damageDropped(metadata));
    }

    String member(int xx, int yy, int zz) {
        List<String> ids = memberships(xx, yy, zz);
        return ids.isEmpty() ? "" : ids.get(0);
    }

    void addMember(String position, String id) {
        // Controllers retain their own structure; shared declared walls may belong to several.
        if (structures.containsKey(position) && !position.equals(id)) return;
        members.computeIfAbsent(position, ignored -> new java.util.LinkedHashSet<>())
            .add(id);
    }

    List<String> memberships(int xx, int yy, int zz) {
        java.util.Set<String> ids = members.get(xx + "," + yy + "," + zz);
        return ids == null ? new ArrayList<>() : new ArrayList<>(ids);
    }

    NBTTagList labels() {
        NBTTagList list = new NBTTagList();
        for (Structure structure : structures.values()) {
            NBTTagCompound row = new NBTTagCompound();
            row.setString("id", structure.id);
            row.setInteger("x", structure.x);
            row.setInteger("y", structure.y);
            row.setInteger("z", structure.z);
            row.setString("name", structure.machine.getLocalName());
            row.setBoolean("enabled", structure.enabled);
            row.setBoolean("formed", structure.machine.mMachine);
            row.setBoolean("declared", structure.capture != null && !structure.capture.incomplete);
            list.appendTag(row);
        }
        return list;
    }

    int[] points() {
        if (first == null) return second == null ? new int[0] : second.clone();
        if (second == null) return first.clone();
        return new int[] { first[0], first[1], first[2], second[0], second[1], second[2] };
    }
}

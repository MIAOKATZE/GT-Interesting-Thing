package com.miaokatze.gtit.client.hologram;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

/** Immutable client snapshot; all amounts and construction outcomes come from the server. */
final class HologramState {

    final NBTTagCompound data;
    final String session;
    final List<Cell> cells = new ArrayList<>();
    final List<Material> materials = new ArrayList<>();
    int minY = 0, maxY = 0;
    private int minWorldX, maxWorldX, minWorldY, maxWorldY, minWorldZ, maxWorldZ;

    HologramState(NBTTagCompound source) {
        data = (NBTTagCompound) source.copy();
        session = data.getString("session");
        minWorldX = maxWorldX = data.getInteger("x");
        minWorldY = maxWorldY = data.getInteger("y");
        minWorldZ = maxWorldZ = data.getInteger("z");
        NBTTagList list = data.getTagList("cells", 10);
        for (int i = 0; i < list.tagCount(); i++) {
            Cell cell = new Cell(i, list.getCompoundTagAt(i));
            cells.add(cell);
            minWorldX = Math.min(minWorldX, cell.x);
            maxWorldX = Math.max(maxWorldX, cell.x);
            minWorldY = Math.min(minWorldY, cell.y);
            maxWorldY = Math.max(maxWorldY, cell.y);
            minWorldZ = Math.min(minWorldZ, cell.z);
            maxWorldZ = Math.max(maxWorldZ, cell.z);
            if (i == 0) {
                minY = cell.dy;
                maxY = cell.dy;
            }
            minY = Math.min(minY, cell.dy);
            maxY = Math.max(maxY, cell.dy);
        }
        list = data.getTagList("materials", 10);
        for (int i = 0; i < list.tagCount(); i++) materials.add(new Material(list.getCompoundTagAt(i)));
    }

    /** Visibility follows the full structure bounds, allowing viewers near remote ends of megastructures. */
    double distanceSquared(double x, double y, double z) {
        double dx = Math.max(Math.max(minWorldX - x, x - (maxWorldX + 1.0)), 0);
        double dy = Math.max(Math.max(minWorldY - y, y - (maxWorldY + 1.0)), 0);
        double dz = Math.max(Math.max(minWorldZ - z, z - (maxWorldZ + 1.0)), 0);
        return dx * dx + dy * dy + dz * dz;
    }

    static final class Cell {

        final int index, x, y, z, dx, dy, dz, meta, wantMeta, chosenChoice, hatchFacing, actualFacing, targetFacing;
        final String id, wantId, status, pinScope, role, family, renderKind, operation;
        final String prompt, structureId, sourceMod, sourceVersion, sourceClass, sourceWarning;
        final List<String> structureIds;
        final boolean anchor, iconOnly, excluded, missingBlock, sourceMissing;
        final boolean candidateComplete;
        final String candidateBoundary;
        final ItemStack actualStack, targetStack;
        final String[] hintIcons = new String[6];
        final int hintIndex;
        final int[] hintTint;
        final List<ItemStack> candidates = new ArrayList<>();

        Cell(int index, NBTTagCompound n) {
            this.index = index;
            x = n.getInteger("x");
            y = n.getInteger("y");
            z = n.getInteger("z");
            dx = n.getInteger("dx");
            dy = n.getInteger("dy");
            dz = n.getInteger("dz");
            meta = n.getInteger("meta");
            wantMeta = n.getInteger("wantMeta");
            id = n.getString("id");
            wantId = n.getString("wantId");
            status = n.getString("status");
            chosenChoice = n.hasKey("chosenChoice") ? n.getInteger("chosenChoice") : -1;
            hatchFacing = n.hasKey("hatchFacing") ? n.getInteger("hatchFacing") : -1;
            actualFacing = n.hasKey("actualFacing") ? n.getInteger("actualFacing") : -1;
            targetFacing = n.hasKey("targetFacing") ? n.getInteger("targetFacing") : hatchFacing;
            candidateComplete = n.getBoolean("candidateComplete");
            candidateBoundary = n.getString("candidateBoundary");
            pinScope = n.getString("pinScope");
            role = n.getString("role");
            family = n.getString("family");
            renderKind = n.getString("renderKind");
            operation = n.getString("operation");
            prompt = n.getString("prompt");
            structureId = n.getString("structureId");
            List<String> memberships = new ArrayList<>();
            NBTTagList membershipTags = n.getTagList("structureIds", 8);
            for (int i = 0; i < membershipTags.tagCount(); i++) {
                String member = membershipTags.getStringTagAt(i);
                if (!member.isEmpty() && !memberships.contains(member)) memberships.add(member);
            }
            if (memberships.isEmpty() && !structureId.isEmpty()) memberships.add(structureId);
            structureIds = Collections.unmodifiableList(memberships);
            sourceMod = n.getString("sourceMod");
            sourceVersion = n.getString("sourceVersion");
            sourceClass = n.getString("sourceClass");
            sourceWarning = n.getString("sourceWarning");
            sourceMissing = n.getBoolean("sourceMissing");
            excluded = n.getBoolean("excluded");
            missingBlock = n.getBoolean("missingBlock")
                || !wantId.isEmpty() && !"minecraft:air".equals(wantId) && Block.getBlockFromName(wantId) == null;
            anchor = n.getBoolean("anchor");
            iconOnly = n.getBoolean("iconOnly");
            actualStack = ItemStack.loadItemStackFromNBT(n.getCompoundTag("actualStack"));
            targetStack = ItemStack.loadItemStackFromNBT(n.getCompoundTag("targetStack"));
            hintIndex = n.hasKey("hintIndex") ? n.getInteger("hintIndex") : -1;
            hintTint = n.getIntArray("hintTint");
            NBTTagList icons = n.getTagList("hintIcons", 8);
            for (int i = 0; i < 6; i++) hintIcons[i] = i < icons.tagCount() ? icons.getStringTagAt(i) : "";
            NBTTagList list = n.getTagList("candidates", 10);
            for (int i = 0; i < list.tagCount(); i++) {
                ItemStack stack = ItemStack.loadItemStackFromNBT(list.getCompoundTagAt(i));
                if (stack != null) candidates.add(stack);
            }
        }

        Block block(boolean current) {
            return Block.getBlockFromName(current ? id : wantId);
        }

        boolean belongsToStructure(String id) {
            return !id.isEmpty() && structureIds.contains(id);
        }

        boolean completed() {
            return status.equals("placed") || status.equals("replaced") || status.equals("removed");
        }

        int textureColor() {
            if (iconOnly && hintTint.length >= 3)
                return ((hintTint[0] & 255) << 16) | ((hintTint[1] & 255) << 8) | (hintTint[2] & 255);
            return color();
        }

        int color() {
            if (excluded) return 0x71848c;
            if (missingBlock) return 0xff52cf;
            if (sourceMissing) return 0x71848c;
            if (status.equals("protected")) return 0xe4b85a;
            if (status.equals("unsupported")) return 0xb68ede;
            if (status.equals("missing")) return 0xe6747a;
            if (status.equals("satisfied") || completed()) return 0x78d7a1;
            return 0x69d7ed;
        }
    }

    static final class Material {

        final ItemStack stack;
        final int required, available, mainAvailable, controllerAvailable;

        Material(NBTTagCompound n) {
            stack = ItemStack.loadItemStackFromNBT(n);
            required = n.getInteger("required");
            available = n.getInteger("available");
            mainAvailable = n.getInteger("mainAvailable");
            controllerAvailable = n.getInteger("controllerAvailable");
        }
    }
}

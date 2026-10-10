package com.miaokatze.gtit.client.hologram;

import java.util.ArrayList;
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

    HologramState(NBTTagCompound source) {
        data = (NBTTagCompound) source.copy();
        session = data.getString("session");
        NBTTagList list = data.getTagList("cells", 10);
        for (int i = 0; i < list.tagCount(); i++) {
            Cell cell = new Cell(i, list.getCompoundTagAt(i));
            cells.add(cell);
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

    static final class Cell {

        final int index, x, y, z, dx, dy, dz, meta, wantMeta, chosenChoice;
        final String id, wantId, status, pinScope;
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
            pinScope = n.getString("pinScope");
            NBTTagList list = n.getTagList("candidates", 10);
            for (int i = 0; i < list.tagCount(); i++) {
                ItemStack stack = ItemStack.loadItemStackFromNBT(list.getCompoundTagAt(i));
                if (stack != null) candidates.add(stack);
            }
        }

        Block block(boolean current) {
            return Block.getBlockFromName(current ? id : wantId);
        }

        boolean completed() {
            return status.equals("placed") || status.equals("replaced") || status.equals("removed");
        }

        int color() {
            if (status.equals("protected")) return 0xe4b85a;
            if (status.equals("unsupported")) return 0xb68ede;
            if (status.equals("missing")) return 0xe6747a;
            if (status.equals("satisfied") || completed()) return 0x78d7a1;
            return 0x69d7ed;
        }
    }

    static final class Material {

        final ItemStack stack;
        final int required, available;

        Material(NBTTagCompound n) {
            stack = ItemStack.loadItemStackFromNBT(n);
            required = n.getInteger("required");
            available = n.getInteger("available");
        }
    }
}

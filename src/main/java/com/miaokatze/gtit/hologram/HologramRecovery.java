package com.miaokatze.gtit.hologram;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import gregtech.api.GregTechAPI;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.BaseMetaPipeEntity;
import gregtech.api.metatileentity.BaseMetaTileEntity;
import gregtech.common.blocks.BlockFrameBox;

/** GT chunk-persistent state, sealed only by the projector; ordinary machine item NBT is untouched. */
public final class HologramRecovery {

    public static final String TAG = "gtitHologramRecovery";
    // A single placement may pass through both the native item hook and StructureLib's completion path.
    // Identity-bound weak keys avoid repeating GT onRemoval/load hooks; this is never serialized to the item.
    private static final Map<TileEntity, RestoreStamp> RESTORED = new WeakHashMap<>();
    private static final ThreadLocal<Deque<Placement>> PLACEMENTS = ThreadLocal.withInitial(ArrayDeque::new);

    private HologramRecovery() {}

    public static boolean supported(TileEntity tile) {
        return (tile instanceof BaseMetaTileEntity || tile instanceof BaseMetaPipeEntity)
            && ((IGregTechTileEntity) tile).getMetaTileID() > 0
            && ((IGregTechTileEntity) tile).getMetaTileEntity() != null;
    }

    /** No getDrops/setItemNBT: those omit inventories/tanks and can destroy cover state. */
    public static List<ItemStack> drops(World world, int x, int y, int z) {
        TileEntity tile = world.getTileEntity(x, y, z);
        if (!supported(tile)) throw new IllegalArgumentException("Unsupported recovery tile");
        Block block = world.getBlock(x, y, z);
        int meta = world.getBlockMetadata(x, y, z);
        int id = ((IGregTechTileEntity) tile).getMetaTileID();
        boolean frame = block instanceof BlockFrameBox;
        if ((!frame && block != GregTechAPI.sBlockMachines) || (frame && !(tile instanceof BaseMetaPipeEntity))) {
            throw new IllegalArgumentException("Unsupported recovery block");
        }
        if (frame && ((meta & BlockFrameBox.MTE_BIT) == 0 || id != 4096 + (meta & BlockFrameBox.MATERIAL_MASK))) {
            throw new IllegalArgumentException("GT frame material and tile ID do not match");
        }
        ItemStack stack = frame ? ((BlockFrameBox) block).getStackForm(1, meta)
            : new ItemStack(GregTechAPI.sBlockMachines, 1, id);
        NBTTagCompound saved = new NBTTagCompound();
        tile.writeToNBT(saved);
        // GT's writer catches saveNBTData exceptions internally. Repeat the pure persistence hook without
        // that catch so a broken tank/controller serializer aborts recovery before any block is removed.
        ((IGregTechTileEntity) tile).getMetaTileEntity()
            .saveNBTData(saved);
        if (saved.getInteger("mID") != id || !saved.hasKey("Inventory", 9) || !saved.hasKey("id", 8)) {
            throw new IllegalStateException("Incomplete GT persistent state");
        }
        NBTTagCompound itemTag = new NBTTagCompound();
        itemTag.setTag(TAG, sealNBT(saved, blockName(block), meta, itemName(stack), stack.getItemDamage(), id));
        stack.setTagCompound(itemTag);
        return Collections.singletonList(stack);
    }

    /** Exposed persistence policy also lets tests exercise NBT without bootstrapping GT registries. */
    public static NBTTagCompound sealNBT(NBTTagCompound tile, String block, int meta, String item, int damage, int id) {
        NBTTagCompound seal = new NBTTagCompound();
        seal.setInteger("version", 1);
        seal.setString("block", block);
        seal.setInteger("meta", meta);
        seal.setString("item", item);
        seal.setInteger("damage", damage);
        seal.setInteger("mID", id);
        seal.setTag("tile", tile.copy());
        return seal;
    }

    public static boolean matchesEnvelope(NBTTagCompound seal, String block, String item, int damage) {
        if (seal == null || seal.getInteger("version") != 1 || !seal.hasKey("tile", 10)) return false;
        NBTTagCompound tile = seal.getCompoundTag("tile");
        return !block.isEmpty() && !item.isEmpty()
            && block.equals(seal.getString("block"))
            && item.equals(seal.getString("item"))
            && damage == seal.getInteger("damage")
            && seal.getInteger("mID") > 0
            && seal.getInteger("mID") == tile.getInteger("mID")
            && tile.hasKey("id", 8)
            && tile.hasKey("Inventory", 9);
    }

    /** Copy, never rewrite the item: world/cache identities are not serialized; only position is relocated. */
    public static NBTTagCompound relocateNBT(NBTTagCompound seal, int x, int y, int z) {
        NBTTagCompound tile = (NBTTagCompound) seal.getCompoundTag("tile")
            .copy();
        tile.setInteger("x", x);
        tile.setInteger("y", y);
        tile.setInteger("z", z);
        return tile;
    }

    public static boolean hasSeal(ItemStack stack) {
        return stack != null && stack.hasTagCompound()
            && stack.getTagCompound()
                .hasKey(TAG);
    }

    public static boolean validItem(ItemStack stack, Block block) {
        return !hasSeal(stack) || matchesEnvelope(
            stack.getTagCompound()
                .getCompoundTag(TAG),
            blockName(block),
            itemName(stack),
            stack.getItemDamage());
    }

    /** Capture only projector-sealed placements; native onItemUse consumes the item only after a true return. */
    public static boolean beginPlacement(World world, int x, int y, int z, ItemStack stack, Block block) {
        if (!hasSeal(stack)) return true;
        if (!validItem(stack, block)) return false;
        try {
            PLACEMENTS.get()
                .push(new Placement(world, x, y, z, stack));
            return true;
        } catch (RuntimeException failure) {
            return false;
        }
    }

    public static boolean finishPlacement(World world, int x, int y, int z, ItemStack stack, boolean placed) {
        if (!hasSeal(stack)) return placed;
        Deque<Placement> pending = PLACEMENTS.get();
        Placement backup = pending.peek();
        if (backup == null || backup.world != world
            || backup.stack != stack
            || backup.x != x
            || backup.y != y
            || backup.z != z) return false;
        pending.pop();
        if (pending.isEmpty()) PLACEMENTS.remove();
        try {
            if (placed && restorePlaced(world, x, y, z, stack)) return true;
        } catch (RuntimeException failure) {
            // Bad/obsolete payloads must not escape item placement or leave both a loaded TE and an unspent item.
        }
        try {
            restore(world, x, y, z, backup.block, backup.meta, backup.tile);
        } catch (RuntimeException rollbackFailure) {
            // Even if the world refuses the old block, the failed new TE must never coexist with the unspent item.
            world.removeTileEntity(x, y, z);
            System.err.println("[GTIT] Sealed placement rollback failed at " + x + "," + y + "," + z);
            rollbackFailure.printStackTrace(System.err);
        }
        return false;
    }

    public static boolean restorePlaced(World world, int x, int y, int z, ItemStack stack) {
        if (!hasSeal(stack) || world.isRemote) return true;
        Block block = world.getBlock(x, y, z);
        if (!validItem(stack, block)) return false;
        NBTTagCompound seal = stack.getTagCompound()
            .getCompoundTag(TAG);
        TileEntity existing = world.getTileEntity(x, y, z);
        RestoreStamp stamp = RESTORED.get(existing);
        if (stamp != null && stamp.tick == world.getTotalWorldTime()
            && stamp.x == x
            && stamp.y == y
            && stamp.z == z
            && seal.equals(stamp.seal)) return true;
        int id = seal.getInteger("mID");
        int meta = seal.getInteger("meta");
        if (block instanceof BlockFrameBox) {
            if (id != 4096 + (meta & BlockFrameBox.MATERIAL_MASK)
                || stack.getItemDamage() != (meta & BlockFrameBox.MATERIAL_MASK)
                || (meta & BlockFrameBox.MTE_BIT) == 0) return false;
            ((BlockFrameBox) block).spawnFrameEntity(world, null, x, y, z);
        } else if (block != GregTechAPI.sBlockMachines || stack.getItemDamage() != id
            || world.getBlockMetadata(x, y, z) != meta) return false;
        TileEntity tile = world.getTileEntity(x, y, z);
        if (!supported(tile) || ((IGregTechTileEntity) tile).getMetaTileID() != id) return false;
        NBTTagCompound before = new NBTTagCompound();
        tile.writeToNBT(before);
        if (!before.getString("id")
            .equals(
                seal.getCompoundTag("tile")
                    .getString("id")))
            return false;
        // Native placement has finished ownership/facing/default modes. Recreate the MTE from persistent NBT
        // afterwards, unregistering the empty initial instance first (AE/energy/cache lifecycle).
        tile.invalidate();
        // Keep the world's existing TE identity valid while MTE load hooks query their surroundings.
        tile.validate();
        ((IGregTechTileEntity) tile).setInitialValuesAsNBT(relocateNBT(seal, x, y, z), (short) id);
        tile.markDirty();
        ((IGregTechTileEntity) tile).issueTextureUpdate();
        ((IGregTechTileEntity) tile).issueTileUpdate();
        world.markBlockForUpdate(x, y, z);
        // GT swallows loadNBTData exceptions. A persistence round trip must preserve the entire sealed payload
        // before the item can be consumed; rejecting a normalizing MTE is safer than silently losing its tanks.
        boolean restored = supported(tile) && ((IGregTechTileEntity) tile).getMetaTileID() == id
            && matchesPlaced(world, x, y, z, stack);
        if (restored) RESTORED.put(tile, new RestoreStamp(world.getTotalWorldTime(), x, y, z, seal));
        return restored;
    }

    /** Explicit sealed pins include inventory/tanks/settings, not merely the machine's meta ID. */
    public static boolean matchesPlaced(World world, int x, int y, int z, ItemStack stack) {
        if (!hasSeal(stack) || !validItem(stack, world.getBlock(x, y, z))) return false;
        TileEntity tile = world.getTileEntity(x, y, z);
        if (!supported(tile)) return false;
        NBTTagCompound seal = stack.getTagCompound()
            .getCompoundTag(TAG);
        if (world.getBlockMetadata(x, y, z) != seal.getInteger("meta")) return false;
        NBTTagCompound actual = new NBTTagCompound();
        tile.writeToNBT(actual);
        ((IGregTechTileEntity) tile).getMetaTileEntity()
            .saveNBTData(actual);
        return actual.equals(relocateNBT(seal, x, y, z));
    }

    /**
     * Keep breakBlock's structure notification, but hide the TE so it cannot eject its sealed inventory.
     * This is persistent-state relocation: deliberately skip MTE onBlockDestroyed/cover destruction hooks,
     * whose irreversible drops would defeat rollback. GT invalidate/onRemoval still tears down live networks.
     */
    public static boolean removeWithoutDrops(World world, int x, int y, int z) {
        if (!supported(world.getTileEntity(x, y, z))) return false;
        world.removeTileEntity(x, y, z); // invokes GT invalidate: AE/energy teardown and MTE onRemoval
        return world.setBlockToAir(x, y, z);
    }

    /** Transaction rollback must also hide the replacement TE from breakBlock to avoid duplicate refunds. */
    public static void restore(World world, int x, int y, int z, Block block, int meta, NBTTagCompound savedTile) {
        world.removeTileEntity(x, y, z);
        if (!world.setBlock(x, y, z, block, meta, 3)
            && (world.getBlock(x, y, z) != block || world.getBlockMetadata(x, y, z) != meta)) {
            throw new IllegalStateException("Recovery rollback block failed");
        }
        if (savedTile != null) {
            world.removeTileEntity(x, y, z);
            NBTTagCompound copy = (NBTTagCompound) savedTile.copy();
            copy.setInteger("x", x);
            copy.setInteger("y", y);
            copy.setInteger("z", z);
            TileEntity tile = TileEntity.createAndLoadEntity(copy);
            if (tile == null) throw new IllegalStateException("Recovery rollback tile failed");
            world.setTileEntity(x, y, z, tile);
            tile.markDirty();
        }
        world.markBlockForUpdate(x, y, z);
    }

    private static String blockName(Block block) {
        Object name = Block.blockRegistry.getNameForObject(block);
        return name == null ? "" : name.toString();
    }

    private static String itemName(ItemStack stack) {
        Object name = Item.itemRegistry.getNameForObject(stack.getItem());
        return name == null ? "" : name.toString();
    }

    private static final class RestoreStamp {

        final long tick;
        final int x, y, z;
        final NBTTagCompound seal;

        RestoreStamp(long tick, int x, int y, int z, NBTTagCompound seal) {
            this.tick = tick;
            this.x = x;
            this.y = y;
            this.z = z;
            this.seal = (NBTTagCompound) seal.copy();
        }
    }

    private static final class Placement {

        final World world;
        final int x, y, z, meta;
        final ItemStack stack;
        final Block block;
        final NBTTagCompound tile;

        Placement(World world, int x, int y, int z, ItemStack stack) {
            this.world = world;
            this.x = x;
            this.y = y;
            this.z = z;
            this.stack = stack;
            block = world.getBlock(x, y, z);
            meta = world.getBlockMetadata(x, y, z);
            TileEntity previous = world.getTileEntity(x, y, z);
            tile = previous == null ? null : new NBTTagCompound();
            if (previous != null) previous.writeToNBT(tile);
        }
    }
}

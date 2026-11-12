// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtit.common.loot;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import net.minecraft.block.Block;
import net.minecraft.block.BlockCocoa;
import net.minecraft.block.BlockCrops;
import net.minecraft.entity.Entity;
import net.minecraft.entity.IProjectile;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.event.world.WorldEvent;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/** Observe native harvesting; never break, replant, cancel, or generate crop drops here. */
public final class NekoCoinEvents {

    private final Map<World, Map<ChunkCoordinates, Attempt>> pending = new HashMap<>();

    private static final class Attempt {

        final EntityPlayer player;
        final Block block;
        final TileEntity tile;
        final boolean right;
        boolean harvested;

        Attempt(EntityPlayer p, Block b, TileEntity t, boolean r) {
            player = p;
            block = b;
            tile = t;
            right = r;
        }
    }

    private static final class CropSticks {

        static final Method canHarvest = lookup();

        static Method lookup() {
            try {
                return Class
                    .forName(
                        "com.gtnewhorizon.cropsnh.api.ICropStickTile",
                        false,
                        NekoCoinEvents.class.getClassLoader())
                    .getMethod("canHarvest");
            } catch (ReflectiveOperationException | LinkageError missing) {
                return null;
            }
        }

        static boolean mature(TileEntity t) {
            if (t == null || canHarvest == null
                || !canHarvest.getDeclaringClass()
                    .isInstance(t))
                return false;
            try {
                return Boolean.TRUE.equals(canHarvest.invoke(t));
            } catch (ReflectiveOperationException failure) {
                return false;
            }
        }
    }

    private static boolean mature(Block b, int meta, TileEntity tile) {
        if (b instanceof BlockCrops) return meta == 7;
        if (b == Blocks.nether_wart) return meta == 3;
        if (b == Blocks.cocoa) return BlockCocoa.func_149987_c(meta) >= 2;
        if (b == Blocks.melon_block || b == Blocks.pumpkin) return false; // a placed fruit is not proof of a grown crop
        String type = b.getClass()
            .getName();
        if (type.equals("mods.natura.blocks.crops.CropBlock")) return meta == 3 || meta == 8;
        if (type.equals("mods.natura.blocks.crops.BerryBush") || type.equals("mods.natura.blocks.crops.NetherBerryBush")
            || type.equals("tconstruct.world.blocks.OreberryBush")) return meta >= 12;
        return CropSticks.mature(tile);
    }

    private void watch(EntityPlayer p, World w, int x, int y, int z, Block b, int meta, boolean right) {
        if (!NekoCoinRewards.eligible(p) || p.capabilities.isCreativeMode) return;
        TileEntity tile = b.hasTileEntity(meta) ? w.getTileEntity(x, y, z) : null;
        if (!mature(b, meta, tile)) return;
        pending.computeIfAbsent(w, k -> new HashMap<>())
            .put(new ChunkCoordinates(x, y, z), new Attempt(p, b, tile, right));
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void right(PlayerInteractEvent e) {
        if (e.world.isRemote || e.action != PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK) return;
        watch(
            e.entityPlayer,
            e.world,
            e.x,
            e.y,
            e.z,
            e.world.getBlock(e.x, e.y, e.z),
            e.world.getBlockMetadata(e.x, e.y, e.z),
            true);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void breaking(BlockEvent.BreakEvent e) {
        if (!e.world.isRemote) watch(e.getPlayer(), e.world, e.x, e.y, e.z, e.block, e.blockMetadata, false);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void harvest(BlockEvent.HarvestDropsEvent e) {
        Map<ChunkCoordinates, Attempt> cells = pending.get(e.world);
        if (cells == null || e.dropChance <= 0) return;
        Attempt a = cells.get(new ChunkCoordinates(e.x, e.y, e.z));
        if (a == null || a.block != e.block || (a.player != e.harvester && !(a.right && e.harvester == null))) return;
        for (ItemStack stack : e.drops) if (stack != null && stack.stackSize > 0) {
            a.harvested = true;
            break;
        }
    }

    @SubscribeEvent
    public void end(TickEvent.WorldTickEvent e) {
        if (e.world.isRemote || e.phase != TickEvent.Phase.END) return;
        Map<ChunkCoordinates, Attempt> cells = pending.remove(e.world);
        if (cells == null) return;
        for (Map.Entry<ChunkCoordinates, Attempt> entry : cells.entrySet()) {
            ChunkCoordinates c = entry.getKey();
            Attempt a = entry.getValue();
            int x = c.posX, y = c.posY, z = c.posZ;
            if (a.player.worldObj != e.world || a.player.isDead || !e.world.blockExists(x, y, z)) continue;
            Block after = e.world.getBlock(x, y, z);
            boolean confirmed = a.right
                ? after == a.block && (a.tile == null || e.world.getTileEntity(x, y, z) == a.tile)
                    && !mature(after, e.world.getBlockMetadata(x, y, z), a.tile)
                : after != a.block && a.harvested;
            if (confirmed) NekoCoinRewards.award(a.player, 0.2);
        }
    }

    @SubscribeEvent
    public void unload(WorldEvent.Unload e) {
        if (!e.world.isRemote) pending.remove(e.world);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void mob(LivingDropsEvent e) {
        if (e.entityLiving.worldObj.isRemote || !(e.entityLiving instanceof IMob)
            || !(e.source.getEntity() instanceof EntityPlayer p)
            || !NekoCoinRewards.eligible(p)) return;
        Entity immediate = e.source.getSourceOfDamage();
        if ((immediate == p && "player".equals(e.source.getDamageType()))
            || (immediate instanceof IProjectile && immediate != p)) NekoCoinRewards.award(p, 0.1);
    }
}

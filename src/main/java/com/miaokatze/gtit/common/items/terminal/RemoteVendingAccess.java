package com.miaokatze.gtit.common.items.terminal;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;

import com.miaokatze.gtit.common.machine.v2.MTENekoVendingMachineV2;

import gregtech.api.metatileentity.BaseMetaTileEntity;

/** Server authority shared by opening, continuous container validation and queued actions. */
public final class RemoteVendingAccess {

    private RemoteVendingAccess() {}

    /** Never calls initDimension, provideChunk or getChunkFromChunkCoords. */
    public static BaseMetaTileEntity findLoaded(NekoVendingTarget target) {
        if (target == null) return null;
        WorldServer world = DimensionManager.getWorld(target.dimension);
        if (world == null || !world.getChunkProvider()
            .chunkExists(target.x >> 4, target.z >> 4)) return null;
        TileEntity tile = world.getTileEntity(target.x, target.y, target.z);
        return tile instanceof BaseMetaTileEntity ? (BaseMetaTileEntity) tile : null;
    }

    /** GT's isUseableByPlayer predicates, except the intentionally remote distance check. */
    public static boolean canUse(BaseMetaTileEntity tile, EntityPlayer player) {
        return tile != null && !tile.isInvalid()
            && tile.canAccessData()
            && tile.getTimer() > 1
            && tile.getMetaTileEntity() instanceof MTENekoVendingMachineV2
            && tile.getMetaTileEntity()
                .getBaseMetaTileEntity() == tile
            && tile.playerOwnsThis(player, false)
            && tile.getMetaTileEntity()
                .isAccessAllowed(player);
    }

    public static String failure(NekoVendingTarget target, EntityPlayer player) {
        if (target == null) return "gtit.terminal.unbound";
        if (!DimensionManager.isDimensionRegistered(target.dimension)) return "gtit.terminal.dimension_missing";
        WorldServer world = DimensionManager.getWorld(target.dimension);
        if (world == null) return "gtit.terminal.dimension_unloaded";
        if (!world.getChunkProvider()
            .chunkExists(target.x >> 4, target.z >> 4)) {
            return "gtit.terminal.chunk_unloaded";
        }
        BaseMetaTileEntity tile = findLoaded(target);
        if (tile == null || !(tile.getMetaTileEntity() instanceof MTENekoVendingMachineV2)) {
            return "gtit.terminal.target_missing";
        }
        return canUse(tile, player) ? null : "gtit.terminal.access_denied";
    }
}

package com.miaokatze.gtit.common.machine.v2;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.DimensionManager;

import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularContainer;
import com.miaokatze.gtit.common.items.terminal.RemoteVendingContainer;
import com.miaokatze.gtit.common.items.terminal.RemoteVendingSession;

import gregtech.api.metatileentity.BaseMetaTileEntity;

/** Resolves an authorized machine without loading chunks. Callers own their failure policy. */
public final class AuthorizedVendingMachine {

    private static final double MAX_DISTANCE_SQ = 64D;

    private AuthorizedVendingMachine() {}

    public static MTENekoVendingMachineV2 resolve(EntityPlayerMP player, int dim, int x, int y, int z) {
        if (player == null) return null;
        if (player.openContainer instanceof RemoteVendingContainer) {
            return RemoteVendingSession.resolveCurrent(player, dim, x, y, z);
        }
        World world = DimensionManager.getWorld(dim);
        if (world == null || world != player.worldObj) return null;
        if (player.getDistanceSq(x + .5D, y + .5D, z + .5D) > MAX_DISTANCE_SQ) return null;
        if (!(player.openContainer instanceof ModularContainer container)
            || !(container.getGuiData() instanceof PosGuiData gui)
            || gui.getX() != x
            || gui.getY() != y
            || gui.getZ() != z) return null;
        if (!world.getChunkProvider()
            .chunkExists(x >> 4, z >> 4)) return null;
        TileEntity tile = world.getTileEntity(x, y, z);
        if (!(tile instanceof BaseMetaTileEntity base) || !base.isUseableByPlayer(player)) return null;
        return base.getMetaTileEntity() instanceof MTENekoVendingMachineV2 machine ? machine : null;
    }
}

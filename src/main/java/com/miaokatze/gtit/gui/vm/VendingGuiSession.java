package com.miaokatze.gtit.gui.vm;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;

import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.miaokatze.gtit.common.items.terminal.RemoteVendingSession;

import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;

/** Binds delayed machine actions to the exact container that received them. */
final class VendingGuiSession {

    private VendingGuiSession() {}

    static Runnable guard(PosGuiData data, PanelSyncManager sync, IGregTechTileEntity tile, IMetaTileEntity machine,
        Runnable action) {
        if (data == null || sync == null || tile == null || machine == null) return () -> {};
        EntityPlayer player = data.getPlayer();
        Container container = sync.getContainer();
        int window = container == null ? -1 : container.windowId;
        Runnable remote = RemoteVendingSession.guard(data, sync, action);
        return () -> {
            if (player == null || player.isDead
                || player.worldObj == null
                || player.worldObj.isRemote
                || container == null
                || player.openContainer != container
                || container.windowId != window
                || tile.getMetaTileEntity() != machine) return;
            if (data instanceof RemoteVendingSession) remote.run();
            else if (player.worldObj == tile.getWorld() && tile.isUseableByPlayer(player)) action.run();
        };
    }
}

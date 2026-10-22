package com.miaokatze.gtit.common.items.terminal;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.world.World;

import com.cleanroommc.modularui.factory.PlayerInventoryGuiData;
import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.miaokatze.gtit.common.machine.v2.MTENekoVendingMachineV2;

import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.metatileentity.BaseMetaTileEntity;

/** One GUI's authority. No global distance bypass and no server-side substitute machine. */
public final class RemoteVendingSession extends PosGuiData {

    private final PlayerInventoryGuiData source;
    private final ItemStack terminal;
    private final NekoVendingTarget target;
    private final BaseMetaTileEntity tile;
    private final IMetaTileEntity machine;
    private final int playerDimension;
    private boolean closed;
    private boolean failureNotified;

    public RemoteVendingSession(PlayerInventoryGuiData source, NekoVendingTarget target, BaseMetaTileEntity tile) {
        super(source.getPlayer(), target.x, target.y, target.z);
        this.source = source;
        this.terminal = source.getUsedItemStack();
        this.target = target;
        this.tile = tile;
        this.machine = tile.getMetaTileEntity();
        this.playerDimension = source.getPlayer().dimension;
    }

    public NekoVendingTarget getTarget() {
        return target;
    }

    @Override
    public World getWorld() {
        return tile.getWorld();
    }

    @Override
    public TileEntity getTileEntity() {
        return tile;
    }

    public void close() {
        closed = true;
    }

    public static RemoteVendingSession current(EntityPlayer player) {
        return player.openContainer instanceof RemoteVendingContainer
            ? ((RemoteVendingContainer) player.openContainer).getRemoteSession()
            : null;
    }

    /** Independent packet endpoints must match the exact address of the current remote session. */
    public static MTENekoVendingMachineV2 resolveCurrent(EntityPlayer player, int dimension, int x, int y, int z) {
        RemoteVendingSession session = current(player);
        if (session == null || session.getPlayer() != player
            || !session.isValid()
            || !session.target.equals(new NekoVendingTarget(dimension, x, y, z))) return null;
        return session.tile.getMetaTileEntity() instanceof MTENekoVendingMachineV2
            ? (MTENekoVendingMachineV2) session.tile.getMetaTileEntity()
            : null;
    }

    public boolean isValid() {
        EntityPlayer player = getPlayer();
        return !closed && !player.isDead
            && player.dimension == playerDimension
            && player.inventory.currentItem == source.getSlotIndex()
            && source.getUsedItemStack() == terminal
            && player.getHeldItem() == terminal
            && terminal != null
            && terminal.stackSize == 1
            && terminal.getItem() instanceof ItemNekoVendingTerminal
            && target.equals(NekoVendingTarget.read(terminal))
            && tile.getMetaTileEntity() == machine
            && (isClient()
                || (RemoteVendingAccess.findLoaded(target) == tile && RemoteVendingAccess.canUse(tile, player)));
    }

    public boolean validateInteraction(EntityPlayer player) {
        if (player != getPlayer()) return false;
        boolean valid = isValid();
        if (!valid && !isClient() && !closed && !failureNotified) {
            failureNotified = true;
            String failure = RemoteVendingAccess.failure(target, player);
            player.addChatMessage(
                new ChatComponentTranslation(failure == null ? "gtit.terminal.connection_lost" : failure));
        }
        return valid;
    }

    /**
     * Call on the receive path, before enqueueing. The returned action cannot outlive this exact remote
     * container/window. Ordinary local GUI actions retain their existing behavior.
     */
    public static Runnable guard(PosGuiData data, PanelSyncManager syncManager, Runnable action) {
        if (!(data instanceof RemoteVendingSession)) return action;
        RemoteVendingSession session = (RemoteVendingSession) data;
        Container container = syncManager.getContainer();
        int windowId = container == null ? -1 : container.windowId;
        return () -> {
            if (container != null && session.getPlayer().openContainer == container
                && container.windowId == windowId
                && session.isValid()) action.run();
        };
    }
}

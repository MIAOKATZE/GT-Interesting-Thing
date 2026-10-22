package com.miaokatze.gtit.gui.terminal;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.WeakHashMap;

import com.cleanroommc.modularui.factory.PlayerInventoryGuiData;
import com.cleanroommc.modularui.factory.inventory.InventoryTypes;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.ModularScreen;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.miaokatze.gtit.common.items.terminal.NekoVendingTarget;
import com.miaokatze.gtit.common.items.terminal.RemoteVendingAccess;
import com.miaokatze.gtit.common.items.terminal.RemoteVendingContainer;
import com.miaokatze.gtit.common.items.terminal.RemoteVendingDisplayTile;
import com.miaokatze.gtit.common.items.terminal.RemoteVendingSession;
import com.miaokatze.gtit.common.machine.v2.MTENekoVendingMachineV2;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import gregtech.api.metatileentity.BaseMetaTileEntity;

/** Adapts the existing item factory to the machine's original GUI and sync tree. */
public final class NekoRemoteVendingGui {

    // A GuiData is per opening. Weak keys do not retain a closed player/container.
    private static final Map<PlayerInventoryGuiData, WeakReference<RemoteVendingSession>> CLIENT_SESSIONS = new WeakHashMap<>();

    private NekoRemoteVendingGui() {}

    public static ModularPanel build(PlayerInventoryGuiData data, PanelSyncManager sync, UISettings settings) {
        if (data.getInventoryType() != InventoryTypes.PLAYER || data.getUsedItemStack() != data.getPlayer()
            .getHeldItem() || data.getSlotIndex() != data.getPlayer().inventory.currentItem) {
            throw new IllegalStateException("Remote vending terminal must be held in the opening slot");
        }
        NekoVendingTarget target = NekoVendingTarget.read(data.getUsedItemStack());
        if (target == null) throw new IllegalStateException("Remote vending terminal has no valid binding");
        BaseMetaTileEntity tile;
        if (sync.isClient()) {
            tile = createDisplayModel(data, target);
        } else {
            tile = RemoteVendingAccess.findLoaded(target);
            if (!RemoteVendingAccess.canUse(tile, data.getPlayer())) {
                throw new IllegalStateException("Remote vending target is unavailable or access was denied");
            }
        }
        RemoteVendingSession session = new RemoteVendingSession(data, target, tile);
        if (sync.isClient()) CLIENT_SESSIONS.put(data, new WeakReference<>(session));
        sync.addCloseListener(player -> {
            session.close();
            if (sync.isClient()) CLIENT_SESSIONS.remove(data);
        });
        MTENekoVendingMachineV2 machine = (MTENekoVendingMachineV2) tile.getMetaTileEntity();
        ModularPanel panel = machine.buildUI(session, sync, settings);
        settings.customContainer(() -> new RemoteVendingContainer(session));
        // Override only this item-hosted container after the original GUI has configured itself.
        settings.canInteractWith(session::validateInteraction);
        return panel;
    }

    /**
     * A client-only rendering model. Never registered in a world, ticked, saved or used for server business.
     * Its world supplies the client-side flag and clock only; the address is carried by the session.
     */
    @SideOnly(Side.CLIENT)
    static BaseMetaTileEntity createDisplayModel(PlayerInventoryGuiData data, NekoVendingTarget target) {
        BaseMetaTileEntity tile = new RemoteVendingDisplayTile(target);
        tile.setWorldObj(data.getWorld());
        MTENekoVendingMachineV2 machine = new MTENekoVendingMachineV2("neko_vending_remote_display");
        machine.setBaseMetaTileEntity(tile);
        return tile;
    }

    @SideOnly(Side.CLIENT)
    public static ModularScreen createScreen(PlayerInventoryGuiData data, ModularPanel panel) {
        WeakReference<RemoteVendingSession> reference = CLIENT_SESSIONS.get(data);
        RemoteVendingSession session = reference == null ? null : reference.get();
        if (session == null) throw new IllegalStateException("Remote vending display model was not built");
        MTENekoVendingMachineV2 machine = (MTENekoVendingMachineV2) ((BaseMetaTileEntity) session.getTileEntity())
            .getMetaTileEntity();
        return machine.createScreen(session, panel);
    }
}

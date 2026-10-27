package com.miaokatze.gtit.gui.vm.scene;

import java.io.IOException;
import java.util.ArrayDeque;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;

import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.SyncHandler;
import com.miaokatze.gtit.common.machine.v2.MTENekoVendingMachineV2;
import com.miaokatze.gtit.gui.vm.IoColumnPanel;
import com.miaokatze.gtit.gui.vm.NekoVMGuiV2;

/** One named, dual-side channel. Attach only after the complete synced widget tree has been assembled. */
public final class VendingSceneBridge extends SyncHandler<VendingSceneBridge> {

    private final NekoVMGuiV2 gui;
    private final MTENekoVendingMachineV2 machine;
    private final ArrayDeque<VendingSceneFrames.Frame> incoming = new ArrayDeque<>();
    private long sent = -1;
    private int ticks, lastRequestTick = -100;
    private volatile boolean resync, requestPending;
    private boolean needsFull, resyncRequested;
    private volatile boolean closed;

    private VendingSceneBridge(NekoVMGuiV2 gui, MTENekoVendingMachineV2 machine) {
        this.gui = gui;
        this.machine = machine;
        allowC2S();
    }

    public static void attach(NekoVMGuiV2 gui, MTENekoVendingMachineV2 machine, PanelSyncManager sync,
        IoColumnPanel io) {
        VendingSceneBridge bridge = new VendingSceneBridge(gui, machine);
        sync.syncValue("nekoV2NativeOutputScene", bridge);
        if (sync.isClient()) com.miaokatze.gtit.client.vending.scene.VendingSceneWidgets.attach(bridge, io);
    }

    @Override
    public void detectAndSendChanges(boolean init) {
        if (closed) return;
        ticks++;
        if (!init && ticks % 4 != 0) return;
        if (resync) {
            sent = -1;
            resync = false;
        }
        NBTTagCompound frame = machine.getOutputSceneFrame(sent);
        long revision = frame.getLong("revision");
        if (!init && sent == revision) return;
        try {
            byte[] bytes = VendingSceneFrames.encode(frame);
            syncToClient(0, b -> {
                b.writeInt(bytes.length);
                b.writeBytes(bytes);
            });
            sent = revision;
        } catch (IOException e) {
            throw new IllegalStateException("Invalid bounded output scene", e);
        }
    }

    @Override
    public void readOnClient(int id, PacketBuffer buffer) throws IOException {
        if (id != 0 || buffer.readableBytes() < 4) throw new IOException("scene packet");
        int length = buffer.readInt();
        if (length <= 0 || length > VendingSceneFrames.MAX_BYTES || buffer.readableBytes() != length)
            throw new IOException("scene packet size");
        byte[] bytes = new byte[length];
        buffer.readBytes(bytes);
        VendingSceneFrames.Frame frame = VendingSceneFrames.decode(bytes);
        synchronized (incoming) {
            if (closed) return;
            if (frame.full) {
                incoming.clear();
                needsFull = false;
                resyncRequested = false;
            }
            if (needsFull) return;
            if (incoming.size() >= 16) {
                incoming.clear();
                needsFull = true;
                return;
            }
            incoming.add(frame);
        }
    }

    public VendingSceneFrames.Frame poll() {
        synchronized (incoming) {
            return incoming.poll();
        }
    }

    public void requestResyncIfNeeded() {
        synchronized (incoming) {
            if (needsFull && !resyncRequested) {
                syncToServer(1);
                resyncRequested = true;
            }
        }
    }

    public void take(VendingSceneFrames.Entry selected, int mode, int amount) {
        if (selected == null || closed) return;
        syncToServer(2, b -> {
            b.writeLong(selected.dropId);
            b.writeLong(selected.generation);
            b.writeByte(mode);
            b.writeInt(amount);
        });
    }

    @Override
    public void readOnServer(int id, PacketBuffer b) throws IOException {
        if (id == 1) {
            if (b.isReadable()) throw new IOException("resync payload");
            resync = true;
            return;
        }
        if (id != 2 || b.readableBytes() != 21) throw new IOException("take payload");
        long drop = b.readLong(), generation = b.readLong();
        int mode = b.readUnsignedByte(), amount = b.readInt();
        if (drop <= 0 || generation <= 0 || mode > 2 || amount < 0 || amount > 1000000)
            throw new IOException("take bounds");
        // Bound queued actions, then revalidate live session and inventory on the authoritative thread.
        synchronized (this) {
            if (requestPending || closed) return;
            requestPending = true;
        }
        gui.scheduleServerAction(() -> {
            if (closed || ticks - lastRequestTick < 2) return;
            lastRequestTick = ticks;
            machine.takeOutputSceneGroup(getSyncManager().getPlayer(), drop, generation, mode, amount);
        }, () -> requestPending = false);
    }

    @Override
    public void dispose() {
        closed = true;
        synchronized (incoming) {
            incoming.clear();
        }
        super.dispose();
    }
}

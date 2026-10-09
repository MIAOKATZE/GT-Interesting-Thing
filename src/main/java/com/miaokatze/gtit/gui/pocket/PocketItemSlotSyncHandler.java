package com.miaokatze.gtit.gui.pocket;

import java.io.IOException;

import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketBuffer;

import com.cleanroommc.modularui.utils.item.ItemHandlerHelper;
import com.cleanroommc.modularui.value.sync.ItemSlotSH;
import com.cleanroommc.modularui.widgets.slot.ModularSlot;
import com.miaokatze.gtit.common.items.pocket.PocketInventory;

/** 中栏复用原生槽交互；S2C以独立int传递数量，避免原版byte数量回绕。 */
public final class PocketItemSlotSyncHandler extends ItemSlotSH {

    private final PocketInventory inventory;
    private ItemStack lastStack;
    private boolean pendingResend;

    public PocketItemSlotSyncHandler(ModularSlot slot, PocketInventory inventory) {
        super(slot);
        this.inventory = inventory;
    }

    @Override
    public void checkUpdate() {
        detectAndSendChanges(false);
    }

    @Override
    public void detectAndSendChanges(boolean init) {
        if (!isValid() || getSyncManager().isClient()) {
            return;
        }
        final ItemStack current = getSlot().getStack();
        if (!init && !pendingResend && current == null && lastStack == null) {
            return;
        }
        final boolean same = ItemHandlerHelper.canItemStacksStack(lastStack, current);
        final boolean amountChanged = same && current != null
            && lastStack != null
            && current.stackSize != lastStack.stackSize;
        if (init || pendingResend || !same || amountChanged) {
            pendingResend = false;
            onSlotUpdate(current, amountChanged, false, init);
            lastStack = current == null ? null : current.copy();
            syncToClient(SYNC_ITEM, buffer -> writeStackUpdate(buffer, current, amountChanged, init));
        }
    }

    public void requestResync() {
        pendingResend = true;
    }

    @Override
    public void forceSyncItem() {
        // C0E mismatch may enqueue a vanilla S30 after this immediate force packet.
        // Repeat the exact count next tick, after that byte-count inventory snapshot.
        pendingResend = true;
        final ItemStack current = getSlot().getStack();
        onSlotUpdate(current, false, getSyncManager().isClient(), false);
        lastStack = current == null ? null : current.copy();
        syncToClient(SYNC_ITEM, buffer -> writeStackUpdate(buffer, current, false, false));
    }

    public static void writeStackUpdate(PacketBuffer buffer, ItemStack stack, boolean amountChanged, boolean init) {
        buffer.writeBoolean(amountChanged);
        final ItemStack icon = stack == null ? null : stack.copy();
        if (icon != null) {
            icon.stackSize = 1;
        }
        try {
            buffer.writeItemStackToBuffer(icon);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to encode pocket storage item", exception);
        }
        buffer.writeInt(stack == null ? 0 : stack.stackSize);
        buffer.writeBoolean(init);
    }

    public static ItemStack readStack(PacketBuffer buffer) {
        final ItemStack stack;
        try {
            stack = buffer.readItemStackFromBuffer();
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to decode pocket storage item", exception);
        }
        final int count = buffer.readInt();
        if (stack != null) {
            stack.stackSize = Math.max(0, count);
        }
        return stack;
    }

    @Override
    public void readOnClient(int id, PacketBuffer buffer) {
        if (id != SYNC_ITEM) {
            super.readOnClient(id, buffer);
            return;
        }
        final boolean amountChanged = buffer.readBoolean();
        lastStack = readStack(buffer);
        onSlotUpdate(lastStack, amountChanged, true, buffer.readBoolean());
        inventory.beginStorageRawRewrite();
        try {
            getSlot().putStack(lastStack);
        } finally {
            inventory.endStorageRawRewrite();
        }
    }
}

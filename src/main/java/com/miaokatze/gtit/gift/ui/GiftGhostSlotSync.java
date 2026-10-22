package com.miaokatze.gtit.gift.ui;

import java.io.IOException;

import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketBuffer;

import com.cleanroommc.modularui.utils.MouseData;
import com.cleanroommc.modularui.value.sync.PhantomItemSlotSH;
import com.cleanroommc.modularui.widgets.slot.ModularSlot;
import com.miaokatze.gtit.gift.GiftNbt;

import cpw.mods.fml.relauncher.Side;

/** Gift ghosts use the same bounded, full-count codec as gift previews in both directions. */
class GiftGhostSlotSync extends PhantomItemSlotSH {

    static final int REJECT_ITEM = 103;
    private ItemStack sent;

    GiftGhostSlotSync(ModularSlot slot) {
        super(slot);
    }

    @Override
    public void detectAndSendChanges(boolean init) {
        if (!isValid() || getSyncManager().isClient()) return;
        ItemStack current = getSlot().getStack();
        if (!init && ItemStack.areItemStacksEqual(sent, current)) return;
        boolean amountOnly = sent != null && current != null
            && sent.isItemEqual(current)
            && ItemStack.areItemStackTagsEqual(sent, current);
        sent = current == null ? null : current.copy();
        onSlotUpdate(current, amountOnly, false, init);
        syncToClient(SYNC_ITEM, buffer -> {
            writeItem(buffer, current);
            buffer.writeBoolean(init);
        });
    }

    @Override
    public void checkUpdate() {
        detectAndSendChanges(false);
    }

    @Override
    public void forceSyncItem() {
        detectAndSendChanges(true);
    }

    @Override
    public void readOnClient(int id, PacketBuffer buffer) {
        if (id != SYNC_ITEM) return;
        try {
            ItemStack stack = readItem(buffer);
            boolean init = buffer.readBoolean();
            onSlotUpdate(stack, false, true, init);
        } catch (IOException | RuntimeException e) {
            // A rejected update must leave the previous ghost intact.
        }
    }

    @Override
    public void updateFromClient(ItemStack stack, int button) {
        syncToServer(SYNC_ITEM_SIMPLE, buffer -> {
            writeItem(buffer, stack);
            buffer.writeVarIntToBuffer(button);
        });
    }

    @Override
    public void readOnServer(int id, PacketBuffer buffer) throws IOException {
        if (id == SYNC_CLICK || id == SYNC_SCROLL) {
            MouseData mouse = MouseData.readPacket(buffer);
            if (buffer.isReadable() || id == SYNC_CLICK && (mouse.mouseButton < 0 || mouse.mouseButton > 1))
                throw new IOException("Invalid ghost interaction");
            if (id == SYNC_CLICK) phantomClick(mouse);
            else phantomScroll(mouse);
        } else if (id == SYNC_ITEM_SIMPLE) {
            ItemStack stack = readItem(buffer);
            int button = buffer.readVarIntFromBuffer();
            if (buffer.isReadable() || button < 0 || button > 1) throw new IOException("Invalid ghost click");
            phantomClick(new MouseData(Side.SERVER, button, false, false, false), stack);
        }
        // Client-controlled slot enabled flags are deliberately not accepted.
    }

    @Override
    protected void phantomClick(MouseData mouse, ItemStack cursor) {
        if (cursor != null) {
            if (cursor.stackSize < 1 || cursor.stackSize > 4096
                || cursor.getItemDamage() < 0
                || cursor.getItemDamage() > 32767)
                throw new IllegalArgumentException("Invalid gift item count or metadata");
            GiftNbt.encode(cursor.getTagCompound());
        }
        super.phantomClick(mouse, cursor);
    }

    static void writeItem(PacketBuffer buffer, ItemStack stack) throws IOException {
        buffer.writeBoolean(stack == null);
        if (stack != null) GiftItemSync.write(buffer, stack);
    }

    static ItemStack readItem(PacketBuffer buffer) throws IOException {
        if (!buffer.isReadable()) throw new IOException("Truncated ghost item");
        return buffer.readBoolean() ? null : GiftItemSync.read(buffer);
    }
}

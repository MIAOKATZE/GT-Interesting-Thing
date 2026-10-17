package com.miaokatze.gtit.gift.ui;

import java.nio.file.Files;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;

import com.cleanroommc.modularui.value.sync.GenericSyncValue;
import com.cleanroommc.modularui.widgets.ItemDisplayWidget;
import com.miaokatze.gtit.config.GiftConfig.ItemEntry;
import com.miaokatze.gtit.gift.GiftDefinition;
import com.miaokatze.gtit.gift.GiftRegistry;
import com.miaokatze.gtit.testutil.MinecraftTestBootstrap;

import io.netty.buffer.Unpooled;

/** Actual item/NBT draft isolation and stale-window revision integration checks. */
public final class GiftEditorSessionTest {

    private GiftEditorSessionTest() {}

    public static void main(String[] args) throws Exception {
        if (!MinecraftTestBootstrap.enter(GiftEditorSessionTest.class, args)) return;
        nullableItemSync();
        GiftRegistry registry = GiftRegistry.INSTANCE;
        registry.init(
            Files.createTempDirectory("gtit-gift-editor-")
                .resolve("gifts.json"));
        GiftDefinition definition = new GiftDefinition();
        definition.id = "editor_test";
        definition.name = "Draft isolation";
        definition.trigger = GiftDefinition.Trigger.FIRST_LOGIN;
        definition.shellKey = "festival";
        definition.rewards.add(new ItemEntry("minecraft:diamond", 7, 0));
        GiftDefinition saved = registry.save(definition, 0);
        GiftEditorSession draft = new GiftEditorSession(null);
        draft.select(saved);
        check("festival".equals(draft.shellKey()), "Editor loads the saved physical carrier binding");
        draft.draft.shellKey = "winter";
        check("festival".equals(registry.get(saved.id).shellKey), "Carrier edits remain in the draft until save");
        long openedGeneration = draft.editGeneration;

        ItemStack cursor = new ItemStack(Items.emerald, 41);
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("marker", "cursor");
        cursor.setTagCompound(tag);
        draft.visibleItems.setStackInSlot(1, cursor);
        ItemStack ghost = draft.visibleItems.getStackInSlot(1);
        ghost.stackSize = 4096;
        ghost.getTagCompound()
            .setString("marker", "draft");
        check(
            cursor.stackSize == 41 && "cursor".equals(
                cursor.getTagCompound()
                    .getString("marker")),
            "Ghost edits must not consume or mutate the copied cursor item / NBT");
        check(
            registry.get(saved.id).rewards.get(0).amount == 7,
            "Editing the open draft must not mutate persistent gift content");

        draft.requiredPage = 31;
        draft.visibleItems.setStackInSlot(4, new ItemStack(Items.diamond, 13));
        draft.randomPage = 31;
        draft.visibleItems.setStackInSlot(8, new ItemStack(Items.emerald, 17));
        check(
            draft.items.getStackInSlot(128).stackSize == 13 && draft.items.getStackInSlot(256).stackSize == 17,
            "Last-page guaranteed and random ghosts must address independent final entries");
        draft.requiredPage = 0;
        check(
            draft.visibleItems.getStackInSlot(1).stackSize == 4096,
            "Paging through the last reward must retain the first-page ghost draft");

        GiftDefinition remote = registry.get(saved.id);
        remote.name = "Changed in another window";
        registry.save(remote, remote.revision);
        check(
            draft.expectedRevision == saved.revision,
            "An open editor must retain its original revision when another window saves");
        boolean rejected = false;
        try {
            registry.save(draft.draft, draft.expectedRevision);
        } catch (IllegalStateException expected) {
            rejected = true;
        }
        check(
            rejected && "Changed in another window".equals(registry.get(saved.id).name),
            "Stale editor save must not overwrite a newer definition");
        draft.create();
        check("starter".equals(draft.shellKey()), "New definitions start with an explicit physical carrier");
        check(
            draft.editGeneration > openedGeneration && draft.items.getStackInSlot(128) == null
                && draft.items.getStackInSlot(256) == null,
            "A new draft must invalidate earlier editor packets and clear every reward page");
        System.out.println(
            "GiftEditorSessionTest: initial-null item sync, 1/127/128/4096 counts with NBT, invalid count bounds, cursor/NBT isolation, persistent draft isolation, final reward pages, stale revisions and new-draft reset passed");
    }

    private static void nullableItemSync() throws Exception {
        ItemStack[] source = { null };
        GenericSyncValue<ItemStack, ?> sender = GiftItemSync.readOnly(() -> source[0]);
        GenericSyncValue<ItemStack, ?> receiver = GiftItemSync.readOnly(() -> null);
        new ItemDisplayWidget().item(sender);
        new ItemDisplayWidget().item(receiver);
        check(
            sender.getValue() == null && receiver.getValue() == null && sender.isValueOfType(ItemStack.class),
            "Empty rows must construct typed handlers without fake items");
        roundTrip(sender, receiver);
        check(receiver.getValue() == null, "An initially empty preview must round-trip as null");
        check(!sender.updateCacheFromSource(false), "An unchanged empty row must not request another sync");

        for (int count : new int[] { 1, 127, 128, 4096 }) {
            source[0] = new ItemStack(Items.emerald, count);
            NBTTagCompound tag = new NBTTagCompound();
            tag.setString("marker", "nullable-sync-" + count);
            source[0].setTagCompound(tag);
            check(sender.updateCacheFromSource(false), "A row must detect an arriving item or changed count");
            source[0].getTagCompound()
                .setString("marker", "source-changed");
            roundTrip(sender, receiver);
            check(
                receiver.getValue().stackSize == count && ("nullable-sync-" + count).equals(
                    receiver.getValue()
                        .getTagCompound()
                        .getString("marker")),
                "The item sync must copy and round-trip item quantity and NBT at count " + count);
            check(source[0].stackSize == count, "Writing a preview must not normalize the source stack count");
        }

        source[0] = null;
        check(sender.updateCacheFromSource(false), "A populated row must detect removal");
        roundTrip(sender, receiver);
        check(receiver.getValue() == null, "Removing a preview item must restore a real null value");
        for (int count : new int[] { 0, 4097 }) {
            GenericSyncValue<ItemStack, ?> invalid = GiftItemSync.readOnly(() -> new ItemStack(Items.emerald, count));
            PacketBuffer buffer = new PacketBuffer(Unpooled.buffer());
            try {
                boolean rejected = false;
                try {
                    invalid.write(buffer);
                } catch (java.io.IOException expected) {
                    rejected = true;
                }
                check(rejected, "Writing an out-of-range preview count must be rejected");
                buffer.clear();
                buffer.writeBoolean(false);
                NBTTagCompound item = new NBTTagCompound();
                new ItemStack(Items.emerald, 1).writeToNBT(item);
                buffer.writeNBTTagCompoundToBuffer(item);
                buffer.writeInt(count);
                rejected = false;
                try {
                    receiver.read(buffer);
                } catch (java.io.IOException expected) {
                    rejected = true;
                }
                check(rejected, "Reading an out-of-range preview count must be rejected");
            } finally {
                buffer.release();
            }
        }
    }

    private static void roundTrip(GenericSyncValue<ItemStack, ?> sender, GenericSyncValue<ItemStack, ?> receiver)
        throws Exception {
        PacketBuffer buffer = new PacketBuffer(Unpooled.buffer());
        try {
            sender.write(buffer);
            receiver.read(buffer);
            check(buffer.readableBytes() == 0, "Both sides must consume the same nullable item codec");
        } finally {
            buffer.release();
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}

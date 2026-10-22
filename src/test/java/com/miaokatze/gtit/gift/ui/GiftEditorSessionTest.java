package com.miaokatze.gtit.gift.ui;

import java.nio.file.Files;
import java.nio.file.Path;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;

import com.cleanroommc.modularui.value.sync.GenericSyncValue;
import com.cleanroommc.modularui.widgets.ItemDisplayWidget;
import com.google.gson.Gson;
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
        boundedGhostAndTextSync();
        manualClaimRules();
        GiftRegistry registry = GiftRegistry.INSTANCE;
        Path registryFile = Files.createTempDirectory("gtit-gift-editor-")
            .resolve("gifts.json");
        registry.init(registryFile);
        iconInheritanceAndPersistence(registry, registryFile);
        GiftDefinition definition = new GiftDefinition();
        definition.id = "editor_test";
        definition.name = "Draft isolation";
        definition.trigger = GiftDefinition.Trigger.FIRST_LOGIN;
        definition.shellKey = "festival";
        definition.rewards.add(new ItemEntry("minecraft:diamond", 7, 0));
        GiftDefinition saved = registry.save(definition, 0);
        GiftEditorSession draft = new GiftEditorSession(null);
        draft.select(saved);
        check(!draft.dirty, "Opening an existing definition must start with a clean draft");
        check("festival".equals(draft.shellKey()), "Editor loads the saved physical carrier binding");
        draft.draft.shellKey = "winter";
        check("festival".equals(registry.get(saved.id).shellKey), "Carrier edits remain in the draft until save");
        long openedGeneration = draft.editGeneration;

        ItemStack cursor = new ItemStack(Items.emerald, 41);
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("marker", "cursor");
        cursor.setTagCompound(tag);
        draft.visibleItems.setStackInSlot(1, cursor);
        check(draft.dirty, "Copying a ghost item marks the draft as unsaved");
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
        draft.editing = true;
        draft.discard();
        check(
            !draft.editing && !draft.dirty && "Changed in another window".equals(draft.draft.name),
            "Discarding leaves the editor and restores the latest saved definition without saving the draft");
        draft.create();
        check("starter".equals(draft.shellKey()), "New definitions start with an explicit physical carrier");
        check(
            draft.draft.icon == null && draft.items.getStackInSlot(0) == null,
            "A new gift inherits its bound gift item's list icon until an independent icon is supplied");
        check(
            draft.editGeneration > openedGeneration && draft.items.getStackInSlot(128) == null
                && draft.items.getStackInSlot(256) == null,
            "A new draft must invalidate earlier editor packets and clear every reward page");
        System.out.println(
            "GiftEditorSessionTest: null/1/16/127/128/4096 counts, bounded ghost/text codecs, binary/long NBT preservation, cursor isolation, final pages and stale revisions passed");
    }

    private static void iconInheritanceAndPersistence(GiftRegistry registry, Path registryFile) throws Exception {
        Gson gson = new Gson();
        GiftDefinition inherited = new GiftDefinition();
        inherited.id = "inherited_icon_test";
        inherited.name = "Inherited carrier icon";
        inherited.trigger = GiftDefinition.Trigger.FIRST_LOGIN;
        inherited.shellKey = "festival";
        check(
            inherited.icon == null && inherited.copy().icon == null,
            "A newly defined gift and its defensive copy inherit their physical carrier icon");
        check(
            gson.fromJson(gson.toJson(inherited), GiftDefinition.class).icon == null,
            "Omitting a null icon during JSON serialization must not recreate an independent chest icon");
        check(
            gson.fromJson("{\"icon\":null}", GiftDefinition.class).icon == null,
            "An explicit JSON null icon also means carrier inheritance");
        GiftDefinition saved = registry.save(inherited, 0);
        check(
            saved.icon == null && registry.get(saved.id).icon == null,
            "Saving and snapshotting a gift must preserve carrier inheritance");
        registry.init(registryFile);
        saved = registry.get(saved.id);
        check(
            saved.icon == null && "festival".equals(saved.shellKey),
            "Reloading persisted definitions preserves the physical carrier and inherited icon");

        NBTTagCompound customTag = new NBTTagCompound();
        customTag.setString("marker", "custom-list-icon");
        saved.icon = new ItemEntry("minecraft:emerald", 3, 0, customTag);
        GiftDefinition copy = saved.copy();
        check(
            copy.icon != saved.icon && "minecraft:emerald".equals(copy.icon.itemId) && copy.icon.amount == 3,
            "An explicitly supplied custom icon is retained as a defensive copy");
        saved = registry.save(saved, saved.revision);
        registry.init(registryFile);
        saved = registry.get(saved.id);
        GiftEditorSession editor = new GiftEditorSession(null);
        editor.select(saved);
        ItemStack customIcon = editor.items.getStackInSlot(0);
        check(
            customIcon != null && customIcon.getItem() == Items.emerald
                && customIcon.stackSize == 3
                && "custom-list-icon".equals(
                    customIcon.getTagCompound()
                        .getString("marker")),
            "A saved custom icon, its quantity and NBT survive persistence and load into the separate icon slot");
        saved.icon = null;
        saved = registry.save(saved, saved.revision);
        registry.init(registryFile);
        editor.select(registry.get(saved.id));
        check(
            editor.draft.icon == null && editor.items.getStackInSlot(0) == null && "festival".equals(editor.shellKey()),
            "Resetting a persisted custom icon restores carrier inheritance without changing the physical gift");
    }

    private static void manualClaimRules() {
        GiftDefinition definition = new GiftDefinition();
        for (GiftDefinition.Trigger trigger : GiftDefinition.Trigger.values()) {
            definition.trigger = trigger;
            for (String status : new String[] { "LOCKED", "AVAILABLE", "PENDING", "ISSUED", "OPENED", "DISABLED",
                "" }) {
                boolean expected = trigger == GiftDefinition.Trigger.BQ_UNLOCK
                    && ("AVAILABLE".equals(status) || "PENDING".equals(status));
                check(
                    GiftEditorSession.canClaim(definition, status) == expected,
                    "Manual claiming must follow the actual trigger and state: " + trigger + " / " + status);
            }
        }
        check(!GiftEditorSession.canClaim(null, "AVAILABLE"), "An empty selection cannot be claimed");
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

        for (int count : new int[] { 1, 16, 127, 128, 4096 }) {
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
                buffer.writeInt(net.minecraft.item.Item.getIdFromItem(Items.emerald));
                buffer.writeInt(0);
                buffer.writeInt(count);
                buffer.writeInt(0);
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

    private static void boundedGhostAndTextSync() throws Exception {
        GiftEditorSession draft = new GiftEditorSession(null);
        NBTTagCompound binary = new NBTTagCompound();
        binary.setByteArray("backpack", new byte[20000]);
        ItemStack backpack = new ItemStack(net.minecraft.init.Blocks.chest, 4096);
        backpack.setTagCompound(binary);
        draft.visibleItems.setStackInSlot(1, backpack);
        check(
            GiftEditorSession.PRESERVED_NBT.equals(draft.nbt(1)),
            "Binary NBT must remain intact behind a read-only text marker");
        PacketBuffer buffer = new PacketBuffer(Unpooled.buffer());
        try {
            GiftGhostSlotSync.writeItem(buffer, backpack);
            ItemStack received = GiftGhostSlotSync.readItem(buffer);
            check(
                received.stackSize == 4096 && binary.equals(received.getTagCompound()) && !buffer.isReadable(),
                "Ghost item codec preserves full counts and backpack NBT in both directions");
            GiftGhostSlotSync client = new GiftGhostSlotSync(
                new com.cleanroommc.modularui.widgets.slot.ModularSlot(draft.visibleItems, 1));
            buffer.clear();
            ItemStack replacement = new ItemStack(Items.emerald, 128);
            GiftGhostSlotSync.writeItem(buffer, replacement);
            buffer.writeBoolean(false);
            client.readOnClient(GiftGhostSlotSync.SYNC_ITEM, buffer);
            check(
                draft.visibleItems.getStackInSlot(1).stackSize == 128,
                "Actual ghost S2C handler retains counts above signed-byte range");
            buffer.clear();
            buffer.writeBoolean(false);
            buffer.writeInt(net.minecraft.item.Item.getIdFromItem(Items.emerald));
            buffer.writeInt(0);
            buffer.writeInt(16);
            buffer.writeInt(Integer.MAX_VALUE);
            client.readOnClient(GiftGhostSlotSync.SYNC_ITEM, buffer);
            check(
                draft.visibleItems.getStackInSlot(1).stackSize == 128,
                "Rejected S2C lengths leave the previous ghost unchanged");
            GiftGhostSlotSync server = new GiftGhostSlotSync(
                new com.cleanroommc.modularui.widgets.slot.ModularSlot(draft.visibleItems, 1) {

                    @Override
                    public boolean isItemValid(ItemStack stack) {
                        return true;
                    }
                }.ignoreMaxStackSize(true));
            buffer.clear();
            GiftGhostSlotSync.writeItem(buffer, backpack);
            buffer.writeVarIntToBuffer(0);
            server.readOnServer(GiftGhostSlotSync.SYNC_ITEM_SIMPLE, buffer);
            check(
                draft.visibleItems.getStackInSlot(1).stackSize == 4096 && binary.equals(
                    draft.visibleItems.getStackInSlot(1)
                        .getTagCompound()),
                "Actual ghost C2S handler retains 4096 items and backpack contents");
            buffer.clear();
            GiftGhostSlotSync.writeItem(buffer, new ItemStack(Items.emerald, 16));
            buffer.writeVarIntToBuffer(0);
            buffer.writeByte(99);
            try {
                server.readOnServer(GiftGhostSlotSync.SYNC_ITEM_SIMPLE, buffer);
                throw new AssertionError("Trailing ghost bytes accepted");
            } catch (java.io.IOException expected) {
                check(
                    binary.equals(
                        draft.visibleItems.getStackInSlot(1)
                            .getTagCompound()),
                    "Malformed C2S payloads are rejected before changing the old ghost");
            }

            draft.visibleItems.setStackInSlot(1, backpack);
            draft.dirty = false;
            NBTTagCompound oversized = new NBTTagCompound();
            oversized.setByteArray("backpack", new byte[25000]);
            ItemStack tooLarge = new ItemStack(net.minecraft.init.Blocks.chest);
            tooLarge.setTagCompound(oversized);
            try {
                draft.visibleItems.setStackInSlot(1, tooLarge);
                throw new AssertionError("Oversized cursor NBT accepted");
            } catch (IllegalArgumentException expected) {
                check(
                    !draft.dirty && binary.equals(
                        draft.visibleItems.getStackInSlot(1)
                            .getTagCompound()),
                    "Oversized cursor input preserves the previous ghost and dirty state");
            }
            NBTTagCompound longTag = new NBTTagCompound();
            longTag.setString("long", new String(new char[17000]).replace('\0', 'x'));
            backpack.setTagCompound(longTag);
            draft.visibleItems.setStackInSlot(1, backpack);
            check(
                GiftEditorSession.PRESERVED_NBT.equals(draft.nbt(1)) && longTag.equals(
                    draft.visibleItems.getStackInSlot(1)
                        .getTagCompound()),
                "Long valid NBT stays in the draft without serializing truncated text");

            String[] target = { "original" };
            GiftTextSync receiver = new GiftTextSync(() -> target[0], value -> target[0] = value, 16384);
            for (String input : new String[] { "Unicode 礼包", new String(new char[12000]).replace('\0', '中'),
                new String(new char[16385]).replace('\0', 'x') }) {
                buffer.clear();
                GiftTextSync sender = new GiftTextSync(() -> input, null, 16384);
                sender.write(buffer);
                if ("Unicode 礼包".equals(input)) {
                    receiver.read(buffer);
                    check(input.equals(target[0]), "Valid Unicode text round-trips exactly");
                } else {
                    String before = target[0];
                    try {
                        receiver.read(buffer);
                        throw new AssertionError("Oversized text accepted");
                    } catch (java.io.IOException expected) {
                        check(
                            before.equals(target[0]),
                            "Byte/character limits reject the whole edit without changing old text");
                    }
                }
            }
        } finally {
            buffer.release();
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

// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtit.common.items.pocket;

import java.lang.reflect.Field;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;

import com.miaokatze.gtit.testutil.MinecraftTestBootstrap;
import com.mojang.authlib.GameProfile;

import cpw.mods.fml.common.gameevent.PlayerEvent;

/** Behavior tests of persistence/compensation ownership, without a Panel, widgets or an action-handler fixture. */
public final class PocketRuntimeSessionTest {

    private static final class Player extends EntityPlayerMP {

        GameProfile profile;

        private Player() {
            super(null, null, null, null);
        }

        @Override
        public GameProfile getGameProfile() {
            return profile;
        }
    }

    private static final class RefusingRoot extends NBTTagCompound {

        boolean refuse;

        @Override
        public NBTBase copy() {
            if (refuse) throw new IllegalStateException("injected serialization failure");
            return super.copy();
        }

        @Override
        public NBTBase getTag(String key) {
            if (refuse && "unknown".equals(key)) throw new IllegalStateException("injected metadata encoding failure");
            return super.getTag(key);
        }
    }

    private static final class Fixture {

        final Player player;
        final RefusingRoot root = new RefusingRoot();
        final ItemStack carrier = new ItemStack(Items.stick);
        final PocketRuntimeSession session;

        Fixture() throws Exception {
            player = player(new GameProfile(UUID.randomUUID(), "pocket-runtime"));
            root.setString("unknown", "keep");
            carrier.setTagCompound(root);
            player.inventory.mainInventory[0] = carrier;
            PocketInventory initial = PocketInventory.readFrom(root);
            initial.essence()
                .add("aer", 100);
            initial.writeTo(root);
            session = new PocketRuntimeSession(
                () -> player,
                () -> player.inventory.mainInventory[0],
                0,
                carrier,
                false);
        }
    }

    public static void main(String[] args) throws Exception {
        if (!MinecraftTestBootstrap.enter(PocketRuntimeSessionTest.class, args)) return;
        Fixture clean = new Fixture();
        clean.session.drainEssence("aer", 16);
        clean.session.persistIdle();
        check(
            PocketInventory.readFrom(clean.carrier.getTagCompound())
                .essence()
                .get("aer") == 100,
            "open idle is read-only");
        clean.session.close();
        check(
            clean.carrier.getTagCompound() != clean.root && "keep".equals(clean.root.getString("unknown"))
                && PocketInventory.readFrom(clean.carrier.getTagCompound())
                    .essence()
                    .get("aer") == 84
                && !clean.session.inventory()
                    .isDirty(),
            "close publishes a fresh complete root and preserves unknown keys");
        Fixture failure = new Fixture();
        failure.session.drainEssence("aer", 16);
        failure.root.refuse = true;
        rejects(failure.session::persistFinal);
        check(
            failure.session.inventory()
                .isDirty()
                && failure.session.inventory()
                    .essenceDeltas()
                    .get("aer") == -16
                && PocketInventory.readFrom(failure.carrier.getTagCompound())
                    .essence()
                    .get("aer") == 100,
            "serialization failure retains baseline, dirty state and compensation journal");
        failure.root.refuse = false;
        failure.session.persistFinal();
        check(
            PocketInventory.readFrom(failure.carrier.getTagCompound())
                .essence()
                .get("aer") == 84,
            "failed save can retry exactly once");
        Fixture nested = new Fixture();
        nested.session.drainEssence("aer", 16);
        nested.session.setStorageStackAt(0, nested.carrier);
        nested.session.persistFinal();
        check(
            nested.session.inventory()
                .isDirty()
                && nested.session.essence()
                    .get("aer") == 84,
            "self nesting refuses serialization without destroying accepted state");
        PocketSessions.register(nested.session);
        rejects(() -> PocketSessions.flushBeforeReplacement(nested.session.playerId()));
        check(PocketSessions.peek(nested.session.playerId()) == nested.session, "unflushed session cannot be replaced");
        nested.session.setStorageStackAt(0, null);
        PocketSessions.flushBeforeReplacement(nested.session.playerId());
        PocketSessions.forget(nested.session.playerId());
        Fixture lost = new Fixture();
        lost.session.drainEssence("aer", 16);
        PocketCarrierIdentity.capture(lost.carrier, true);
        lost.session.persistFinal();
        lost.session.persistFinal();
        check(
            lost.session.essence()
                .get("aer") == 84
                && lost.session.inventory()
                    .essenceDeltas()
                    .get("aer") == -16
                && lost.session.inventory()
                    .isDirty(),
            "revoked authority retains the accepted quantity, dirty state and journal");
        Fixture duplicate = new Fixture();
        duplicate.player.inventory.mainInventory[1] = duplicate.carrier.copy();
        duplicate.session.drainEssence("aer", 16);
        duplicate.session.persistFinal();
        check(
            duplicate.session.carrierStack() == null && PocketInventory.readFrom(duplicate.root)
                .essence()
                .get("aer") == 100,
            "ambiguous token never chooses or overwrites either carrier");
        verifyCommitBoundaries();
        verifyCorruptInput();
        verifyPartialRefundReload();
        verifyRelocatedElementWrites();
        verifyRecoveryRecord();
        verifyLogoutAndReconnect();
        verifyReconnectWithoutLogout();
        verifyFailedSerializationOnLogout();
        System.out.println(
            "PocketRuntimeSessionTest: PASS (persistence, carrier authority, compensation, logout, reconnect, shutdown)");
    }

    private static void verifyCommitBoundaries() throws Exception {
        Fixture fixture = new Fixture();
        fixture.session.setStorageStackAt(0, new ItemStack(Items.gold_ingot, 7));
        fixture.session.inventory()
            .fillOwnTank(
                0,
                new net.minecraftforge.fluids.FluidStack(net.minecraftforge.fluids.FluidRegistry.WATER, 1000));
        fixture.session.persistFinal();
        fixture.session.setStorageStackAt(0, null);
        fixture.session.inventory()
            .drainOwnTank(0, 1000);
        fixture.session.drainEssence("aer", 100);
        fixture.session.persistFinal();
        NBTTagCompound empty = fixture.carrier.getTagCompound();
        check(
            !empty.hasKey(PocketConstants.ITEM_CONTENTS) && !empty.hasKey(PocketConstants.FLUID_BAR)
                && !empty.hasKey(PocketConstants.ESSENCE_CELL_ORDER),
            "empty snapshots remove old items, fluids and essence order from the carrier");
        check(
            PocketInventory.readFrom(empty)
                .storage()
                .getStackInSlot(0) == null
                && PocketInventory.readFrom(empty)
                    .tankTruthAt(0) == 0
                && PocketInventory.readFrom(empty)
                    .essence()
                    .get("aer") == 0,
            "all three channels stay empty after reopening");
        PocketElementStore elements = fixture.session.inventory()
            .elements();
        elements.add("aer", 7);
        fixture.session.setStorageStackAt(0, new ItemStack(Items.iron_ingot));
        fixture.session.persistFinal();
        elements.add("aer", 3);
        check(
            PocketElementStore.attach(fixture.carrier.getTagCompound())
                .get("aer") == 10,
            "retained element views follow the published root across multiple commits");
        fixture.session.drainEssence("aer", 0);
        fixture.session.inventory()
            .essence()
            .add("aer", 20);
        fixture.session.markDirty();
        RefusingRoot receipt = new RefusingRoot();
        receipt.refuse = true;
        Field pending = PocketRuntimeSession.class.getDeclaredField("pendingOutgoing");
        pending.setAccessible(true);
        pending.set(fixture.session, receipt);
        rejects(fixture.session::persistFinal);
        check(
            fixture.session.inventory()
                .isDirty()
                && fixture.session.inventory()
                    .essenceDeltas()
                    .get("aer") == 20
                && PocketInventory.readFrom(fixture.carrier.getTagCompound())
                    .essence()
                    .get("aer") == 0,
            "receipt encoding failure after inventory encoding preserves baseline, quantity and journal");
        pending.set(fixture.session, null);
        fixture.session.persistFinal();
        check(
            !fixture.session.inventory()
                .isDirty() && fixture.session.inventory()
                    .essenceDeltas()
                    .isEmpty()
                && PocketInventory.readFrom(fixture.carrier.getTagCompound())
                    .essence()
                    .get("aer") == 20,
            "retry acknowledges only the successfully published inventory");
        PocketSessions.register(fixture.session);
        fixture.session.setStorageStackAt(0, new ItemStack(Items.gold_ingot, 11));
        fixture.session.persistIdle();
        check(
            PocketInventory.readFrom(fixture.carrier.getTagCompound())
                .storage()
                .getStackInSlot(0).stackSize == 1,
            "ordinary driver writes are deferred within the tick");
        PocketSessions.flushTick();
        check(
            PocketInventory.readFrom(fixture.carrier.getTagCompound())
                .storage()
                .getStackInSlot(0).stackSize == 11,
            "tick end publishes the accumulated changes");
        PocketSessions.forget(fixture.session.playerId());
    }

    private static void verifyCorruptInput() throws Exception {
        Fixture fixture = new Fixture();
        NBTTagCompound malformed = (NBTTagCompound) fixture.carrier.getTagCompound()
            .copy();
        malformed.setString(PocketConstants.ITEM_CONTENTS, "unreadable");
        NBTTagCompound original = (NBTTagCompound) malformed.copy();
        rejects(() -> PocketInventory.readFrom(malformed));
        check(malformed.equals(original), "wrong tag types retain the complete original document");
        NBTTagCompound duplicate = new NBTTagCompound();
        NBTTagCompound group = new NBTTagCompound();
        net.minecraft.nbt.NBTTagList entries = new net.minecraft.nbt.NBTTagList();
        NBTTagCompound item = new ItemStack(Items.gold_ingot).writeToNBT(new NBTTagCompound());
        item.setInteger("Slot", 0);
        entries.appendTag(item);
        entries.appendTag(item.copy());
        group.setTag("Items", entries);
        duplicate.setTag(PocketConstants.ITEM_CONTENTS, group);
        rejects(() -> PocketInventory.readFrom(duplicate));
        item.setInteger("Slot", -1);
        rejects(() -> PocketInventory.readFrom(duplicate));
        NBTTagCompound unknown = new NBTTagCompound();
        unknown.setString("FluidName", "gtit_missing_test_fluid");
        unknown.setInteger("Amount", 1000);
        NBTTagCompound fluidRoot = new NBTTagCompound();
        fluidRoot.setTag(PocketConstants.FLUID_BAR, unknown);
        rejects(() -> PocketInventory.readFrom(fluidRoot));
        check(unknown.getInteger("Amount") == 1000, "unregistered fluid data remains intact");
    }

    private static void verifyRecoveryRecord() throws Exception {
        Fixture fixture = new Fixture();
        fixture.session.setStorageStackAt(0, new ItemStack(Items.gold_ingot, 13));
        fixture.session.drainEssence("aer", 16);
        NBTTagCompound record = fixture.session.recoverySnapshot();
        java.nio.file.Path directory = java.nio.file.Files.createTempDirectory("gtit-pocket-recovery-");
        java.nio.file.Path file = directory.resolve("pending.dat");
        try {
            com.miaokatze.gtit.util.AtomicNbtFile.writeCompressedAndSync(record, file.toFile(), path -> {
                try (java.io.InputStream input = java.nio.file.Files.newInputStream(path)) {
                    net.minecraft.nbt.CompressedStreamTools.readCompressed(input);
                }
            });
            NBTTagCompound saved;
            try (java.io.InputStream input = java.nio.file.Files.newInputStream(file)) {
                saved = net.minecraft.nbt.CompressedStreamTools.readCompressed(input);
            }
            PocketInventory pending = PocketInventory.readFrom(saved.getCompoundTag("pendingContents"));
            check(
                pending.storageStack(0).stackSize == 13 && pending.essence()
                    .get("aer") == 84
                    && saved.getCompoundTag("carrierBaseline")
                        .hasKey("tag"),
                "durable recovery record retains the carrier baseline and pending quantities");
            check(
                fixture.session.inventory()
                    .isDirty()
                    && fixture.session.inventory()
                        .essenceDeltas()
                        .get("aer") == -16,
                "writing recovery material never acknowledges or reverses the pending inventory");
        } finally {
            java.nio.file.Files.deleteIfExists(file);
            java.nio.file.Files.deleteIfExists(directory);
        }
    }

    private static void verifyRelocatedElementWrites() throws Exception {
        Fixture fixture = new Fixture();
        PocketElementStore retained = fixture.session.inventory()
            .elements();
        retained.add("aer", 4);
        ItemStack moved = fixture.carrier.copy();
        fixture.player.inventory.mainInventory[0] = null;
        fixture.player.inventory.mainInventory[1] = moved;
        check(fixture.session.carrierStack() == moved, "the new host resolves the copied carrier");
        PocketElementStore.attach(moved.getTagCompound())
            .add("aer", 3);
        fixture.session.setStorageStackAt(0, new ItemStack(Items.gold_ingot));
        fixture.session.persistFinal();
        check(
            retained.get("aer") == 7 && PocketElementStore.attach(moved.getTagCompound())
                .get("aer") == 7,
            "passive writes after relocation survive the next complete snapshot");
        fixture.player.inventory.mainInventory[1] = null;
        fixture.session.setStorageStackAt(0, null);
        fixture.session.persistFinal();
        check(
            fixture.session.inventory()
                .isDirty()
                && PocketInventory.readFrom(moved.getTagCompound())
                    .storageStack(0) != null,
            "a detached cached carrier cannot acknowledge an unowned write");
    }

    private static void verifyPartialRefundReload() throws Exception {
        Fixture fixture = new Fixture();
        for (int slot = 1; slot < fixture.session.storageSlots(); slot++)
            fixture.session.setStorageStackAt(slot, new ItemStack(Items.iron_ingot, 64));
        fixture.session.setStorageStackAt(0, new ItemStack(Items.gold_ingot, 64));
        PocketTransferReservation transfer = fixture.session
            .reserveOutgoingItem(0, fixture.session.storageStackAt(0), 10, "refund-reload-test");
        check(transfer != null, "outgoing reservation publishes before the target is called");
        fixture.session.setStorageStackAt(0, new ItemStack(Items.gold_ingot, 64));
        fixture.session.completeOutgoing(transfer, 10);
        PocketRuntimeSession reloaded = new PocketRuntimeSession(
            () -> fixture.player,
            () -> fixture.player.inventory.mainInventory[0],
            0,
            fixture.carrier,
            false);
        reloaded.setStorageStackAt(0, new ItemStack(Items.gold_ingot, 59));
        reloaded.persistFinal();
        check(
            fixture.carrier.getTagCompound()
                .getCompoundTag(PocketTransferReservation.NBT_KEY)
                .getLong("amount") == 5,
            "partial recovery publishes the remaining refund amount");
        PocketRuntimeSession next = new PocketRuntimeSession(
            () -> fixture.player,
            () -> fixture.player.inventory.mainInventory[0],
            0,
            fixture.carrier,
            false);
        next.setStorageStackAt(0, new ItemStack(Items.gold_ingot, 59));
        next.persistFinal();
        check(
            next.storageStackAt(0).stackSize == 64 && !fixture.carrier.getTagCompound()
                .hasKey(PocketTransferReservation.NBT_KEY),
            "a second reload returns only the remaining five items and clears the completed receipt");
    }

    private static void verifyLogoutAndReconnect() throws Exception {
        final Fixture fixture = new Fixture();
        final UUID uuid = fixture.session.playerId();
        fixture.session.setStorageStackAt(0, new ItemStack(Items.gold_ingot, 37));
        fixture.player.inventory.mainInventory[1] = fixture.carrier.copy();
        PocketSessions.register(fixture.session);
        PocketLifecycleHandler.INSTANCE.onPlayerLoggedOut(new PlayerEvent.PlayerLoggedOutEvent(fixture.player));
        check(
            PocketSessions.peek(uuid) == fixture.session && fixture.session.inventory()
                .isDirty(),
            "ambiguous carrier keeps the original dirty session without escaping the logout callback");
        rejects(() -> PocketSessions.flushBeforeReplacement(uuid));
        fixture.player.inventory.mainInventory[1] = null;
        PocketSessions.sweepOrphans();
        check(
            PocketInventory.readFrom(fixture.root)
                .storage()
                .getStackInSlot(0) == null,
            "sweeping an offline player must not write to the departed ItemStack reference");

        final Player rejoined = player(fixture.player.profile);
        final ItemStack candidate = fixture.carrier.copy();
        rejoined.inventory.mainInventory[0] = candidate;
        candidate.getTagCompound()
            .setString("unknown", "changed while offline");
        PocketLifecycleHandler.INSTANCE.onPlayerLoggedIn(new PlayerEvent.PlayerLoggedInEvent(rejoined));
        check(
            fixture.session.inventory()
                .isDirty()
                && "changed while offline".equals(
                    candidate.getTagCompound()
                        .getString("unknown")),
            "a matching token alone does not authorize overwriting a changed saved carrier");
        candidate.getTagCompound()
            .setString("unknown", "keep");
        rejoined.inventory.mainInventory[1] = candidate.copy();
        PocketLifecycleHandler.INSTANCE.onPlayerLoggedIn(new PlayerEvent.PlayerLoggedInEvent(rejoined));
        check(
            PocketSessions.peek(uuid) == fixture.session,
            "two matching carriers on reconnect keep the write pending");
        rejoined.inventory.mainInventory[1] = null;
        PocketSessions.flushBeforeOpening(rejoined);
        final ItemStack restored = PocketInventory.readFrom(candidate.getTagCompound())
            .storage()
            .getStackInSlot(0);
        check(
            PocketSessions.peek(uuid) == null && restored != null
                && restored.getItem() == Items.gold_ingot
                && restored.stackSize == 37,
            "reopening a unique unchanged carrier writes the pending items to the current player");
        check(
            PocketInventory.readFrom(fixture.root)
                .storage()
                .getStackInSlot(0) == null,
            "recovery leaves the departed player's carrier untouched");
    }

    private static void verifyReconnectWithoutLogout() throws Exception {
        final Fixture old = new Fixture();
        final UUID uuid = old.session.playerId();
        old.session.setStorageStackAt(0, new ItemStack(Items.gold_ingot, 5));
        PocketSessions.register(old.session);
        final Player rejoined = player(old.player.profile);
        final ItemStack copy = old.carrier.copy();
        rejoined.inventory.mainInventory[0] = copy;
        PocketLifecycleHandler.INSTANCE.onPlayerLoggedIn(new PlayerEvent.PlayerLoggedInEvent(rejoined));
        final ItemStack restored = PocketInventory.readFrom(copy.getTagCompound())
            .storage()
            .getStackInSlot(0);
        check(
            PocketSessions.peek(uuid) == null && restored != null
                && restored.stackSize == 5
                && PocketInventory.readFrom(old.root)
                    .storage()
                    .getStackInSlot(0) == null,
            "new login may finish the pending write without mutating the old player object");
    }

    private static void verifyFailedSerializationOnLogout() throws Exception {
        final Fixture refusing = new Fixture();
        final UUID refusingId = refusing.session.playerId();
        refusing.session.setStorageStackAt(0, new ItemStack(Items.gold_ingot));
        refusing.root.refuse = true;
        PocketSessions.register(refusing.session);
        PocketLifecycleHandler.INSTANCE.onPlayerLoggedOut(new PlayerEvent.PlayerLoggedOutEvent(refusing.player));
        check(
            PocketSessions.peek(refusingId) == refusing.session,
            "serialization exceptions keep the pending logout session");

        final Fixture other = new Fixture();
        final UUID otherId = other.session.playerId();
        other.session.drainEssence("aer", 12);
        PocketSessions.register(other.session);
        PocketSessions.flushBeforeStop();
        check(
            PocketSessions.peek(refusingId) == refusing.session && PocketSessions.peek(otherId) == null
                && PocketInventory.readFrom(other.carrier.getTagCompound())
                    .essence()
                    .get("aer") == 88,
            "a pending player must not prevent other sessions from saving at stop");
    }

    private static Player player(GameProfile profile) throws Exception {
        final Field field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        final Player player = (Player) ((sun.misc.Unsafe) field.get(null)).allocateInstance(Player.class);
        player.profile = profile;
        player.inventory = new InventoryPlayer(player);
        return player;
    }

    private static void rejects(Runnable action) {
        try {
            action.run();
        } catch (IllegalStateException expected) {
            return;
        }
        throw new AssertionError("expected rejection");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}

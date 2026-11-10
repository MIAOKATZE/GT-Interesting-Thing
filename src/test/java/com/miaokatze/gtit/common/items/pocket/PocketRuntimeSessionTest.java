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
    }

    private static final class Fixture {

        final Player player;
        final RefusingRoot root = new RefusingRoot();
        final ItemStack carrier = new ItemStack(Items.stick);
        final PocketRuntimeSession session;

        Fixture() throws Exception {
            Field field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            player = (Player) ((sun.misc.Unsafe) field.get(null)).allocateInstance(Player.class);
            player.profile = new GameProfile(UUID.randomUUID(), "pocket-runtime");
            player.inventory = new InventoryPlayer(player);
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
            PocketInventory.readFrom(clean.root)
                .essence()
                .get("aer") == 100,
            "open idle is read-only");
        clean.session.close();
        check(
            clean.carrier.getTagCompound() == clean.root && "keep".equals(clean.root.getString("unknown"))
                && PocketInventory.readFrom(clean.root)
                    .essence()
                    .get("aer") == 84
                && !clean.session.inventory()
                    .isDirty(),
            "close persists accepted state and preserves root identity/unknown keys");
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
                && PocketInventory.readFrom(failure.root)
                    .essence()
                    .get("aer") == 100,
            "serialization failure retains baseline, dirty state and compensation journal");
        failure.root.refuse = false;
        failure.session.persistFinal();
        check(
            PocketInventory.readFrom(failure.root)
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
                .get("aer") == 100 && lost.session.inventory()
                    .essenceDeltas()
                    .isEmpty()
                && lost.session.inventory()
                    .isDirty(),
            "revoked authority compensates once and preserves non-essence dirty state");
        Fixture duplicate = new Fixture();
        duplicate.player.inventory.mainInventory[1] = duplicate.carrier.copy();
        duplicate.session.drainEssence("aer", 16);
        duplicate.session.persistFinal();
        check(
            duplicate.session.carrierStack() == null && PocketInventory.readFrom(duplicate.root)
                .essence()
                .get("aer") == 100,
            "ambiguous token never chooses or overwrites either carrier");
        System.out.println(
            "PocketRuntimeSessionTest: PASS (open/close, save failure/retry, nesting, replacement, revoked/ambiguous, compensation)");
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

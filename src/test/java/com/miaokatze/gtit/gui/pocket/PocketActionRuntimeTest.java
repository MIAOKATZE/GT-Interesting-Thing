// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtit.gui.pocket;

import java.lang.reflect.Field;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import com.miaokatze.gtit.common.items.pocket.PocketRuntimeSession;
import com.miaokatze.gtit.testutil.MinecraftTestBootstrap;
import com.mojang.authlib.GameProfile;

/** Exercises the real action handler with a narrow view; no Panel, container allocation or text-position gates. */
public final class PocketActionRuntimeTest {

    private static final class Player extends EntityPlayerMP {

        GameProfile profile;

        private Player() {
            super(null, null, null, null);
        }

        @Override
        public GameProfile getGameProfile() {
            return profile;
        }

        @Override
        public UUID getUniqueID() {
            return profile.getId();
        }
    }

    private static final class View implements NekoPocketActionView {

        boolean current = true;
        ItemStack cursor;
        String receipt;

        @Override
        public boolean isCurrent() {
            return current;
        }

        @Override
        public void report(String key, int moved, int refused) {
            receipt = key;
        }

        @Override
        public void refreshGhosts() {}

        @Override
        public void magnetChanged() {}

        @Override
        public void giveAway(ItemStack stack) {
            throw new AssertionError("unexpected item delivery");
        }

        @Override
        public ItemStack cursor() {
            return cursor;
        }

        @Override
        public void setCursor(ItemStack stack) {
            cursor = stack;
        }
    }

    public static void main(String[] args) throws Exception {
        if (!MinecraftTestBootstrap.enter(PocketActionRuntimeTest.class, args)) return;
        runChecks();
        System.out.println(
            "PocketActionRuntimeTest: PASS (server/client, current/closed/ambiguous, packed bounds, empty phial)");
    }

    public static void runChecks() {
        try {
            Field field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            Player player = (Player) ((sun.misc.Unsafe) field.get(null)).allocateInstance(Player.class);
            player.profile = new GameProfile(UUID.randomUUID(), "action-runtime");
            player.inventory = new InventoryPlayer(player);
            ItemStack carrier = new ItemStack(new Item());
            player.inventory.mainInventory[0] = carrier;
            PocketRuntimeSession runtime = new PocketRuntimeSession(
                () -> player,
                () -> player.inventory.mainInventory[0],
                0,
                carrier,
                false);
            View view = new View();
            NekoPocketServerHandler handler = new NekoPocketServerHandler(runtime, view);
            check(handler.serverGuardOk(), "current valid server action admitted");
            runtime.essence()
                .add("aer", 100);
            int points = runtime.essence()
                .get("aer");
            for (int packed : new int[] { -1, 240, Integer.MAX_VALUE }) {
                handler.performEssenceOutToPhial(packed);
                check(
                    runtime.essence()
                        .get("aer") == points && view.cursor == null
                        && view.receipt == null,
                    "out-of-range payload rejected before any mutation");
            }
            handler.performEssenceOutToPhial(0);
            check(
                "gtit.pocket.essence.need_phial".equals(view.receipt) && runtime.essence()
                    .get("aer") == points && view.cursor == null,
                "empty hand never mints a phial");
            view.current = false;
            check(!handler.serverGuardOk(), "stale container action rejected");
            view.current = true;
            player.inventory.mainInventory[1] = carrier.copy();
            check(!handler.serverGuardOk(), "ambiguous carrier action rejected");
            player.inventory.mainInventory[1] = null;
            runtime.close();
            check(!handler.serverGuardOk(), "closed session cannot accept delayed actions");
            PocketRuntimeSession client = new PocketRuntimeSession(() -> player, () -> carrier, 0, carrier, true);
            check(
                !new NekoPocketServerHandler(client, view).serverGuardOk(),
                "client runtime never enters server writes");
        } catch (Exception failure) {
            throw new AssertionError(failure);
        }
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}

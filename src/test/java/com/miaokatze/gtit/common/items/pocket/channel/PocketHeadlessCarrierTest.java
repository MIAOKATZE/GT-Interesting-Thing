package com.miaokatze.gtit.common.items.pocket.channel;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import com.miaokatze.gtit.common.items.pocket.PocketCarrierIdentity;
import com.miaokatze.gtit.common.items.pocket.PocketChannelManager;
import com.miaokatze.gtit.common.items.pocket.PocketChannelState;
import com.miaokatze.gtit.common.items.pocket.PocketInventory;
import com.miaokatze.gtit.common.items.pocket.PocketSession;
import com.miaokatze.gtit.common.items.pocket.PocketSessions;
import com.miaokatze.gtit.testutil.MinecraftTestBootstrap;
import com.mojang.authlib.GameProfile;

/** Actual headless session and driver authority checks across native vanilla inventory copies. */
public final class PocketHeadlessCarrierTest {

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

    private static final class Vanilla extends net.minecraft.inventory.Container {

        Vanilla(EntityPlayer player) {
            addSlotToContainer(new Slot(player.inventory, 0, 0, 0));
            addSlotToContainer(new Slot(player.inventory, 1, 0, 0));
        }

        @Override
        public boolean canInteractWith(EntityPlayer player) {
            return true;
        }
    }

    private static <T> T allocate(Class<T> type) throws Exception {
        Field field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return type.cast(((sun.misc.Unsafe) field.get(null)).allocateInstance(type));
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) throws Exception {
        if (!MinecraftTestBootstrap.enter(PocketHeadlessCarrierTest.class, args)) return;
        PocketSessions.clearAll();
        PocketChannelManager.INSTANCE.reset();
        UUID uuid = UUID.randomUUID();
        Player player = allocate(Player.class);
        player.profile = new GameProfile(uuid, "headless-carrier-test");
        player.inventory = new InventoryPlayer(player);
        ItemStack original = new ItemStack(new Item(), 1);
        original.setTagCompound(new NBTTagCompound());
        player.inventory.mainInventory[0] = original;
        PocketInventory inventory = PocketInventory.readFrom(original.getTagCompound());
        inventory.fillOwnTank(0, new FluidStack(FluidRegistry.WATER, 1000));
        inventory.writeTo(original.getTagCompound());
        inventory.markClean();
        // Exercise the real session without requiring a launched world or a configured AE grid.
        Class<?> type = Class.forName(PocketChannelSessions.class.getName() + "$HeadlessSession");
        Constructor<?> constructor = type
            .getDeclaredConstructor(UUID.class, EntityPlayer.class, ItemStack.class, PocketInventory.class);
        constructor.setAccessible(true);
        PocketSession session = (PocketSession) constructor.newInstance(uuid, player, original, inventory);
        PocketSessions.register(session);
        try {
            PocketChannelState state = PocketChannelManager.INSTANCE.stateOf(uuid);
            state.activate(PocketChannelState.Mode.SHORT, 0, 0);
            state.attachSession(session.bindings(), session.filters(), original, false);
            int ticks = state.ticksUntilDue();
            int batches = state.remainingBatches();
            Vanilla vanilla = new Vanilla(player);
            player.openContainer = vanilla;
            vanilla.slotClick(0, 0, 0, player);
            vanilla.slotClick(1, 0, 0, player);
            ItemStack moved = player.inventory.mainInventory[1];
            check(moved != original && original.stackSize == 0, "native placement must create a carrier copy");
            PocketChannelDriver.onItemTick(moved, null, player, 1, false);
            check(
                session.carrierStack() == moved && state.sessionCarrier() == moved,
                "headless session resolves before the driver checks/retargets carrier identity");
            check(
                state.ticksUntilDue() == ticks - 1 && state.remainingBatches() == batches,
                "moving carrier resumes the existing timer; no free restart or extra batch");
            inventory.drainOwnTank(0, 400);
            session.persistIdle();
            check(
                PocketInventory.readFrom(moved.getTagCompound())
                    .tankTruthAt(0) == 600,
                "accepted state persists to the uniquely relocated headless carrier");

            ItemStack foreign = new ItemStack(original.getItem(), 1);
            player.inventory.mainInventory[2] = foreign;
            PocketChannelDriver.onItemTick(foreign, null, player, 2, false);
            check(
                state.sessionCarrier() == moved && state.ticksUntilDue() == ticks - 1,
                "another pocket must not retarget state or advance this channel's clock");
            player.inventory.mainInventory[2] = moved.copy();
            PocketChannelDriver.onItemTick(moved, null, player, 1, false);
            check(
                session.carrierStack() == null && !session.isHeldByOwner() && state.ticksUntilDue() == ticks - 1,
                "duplicate tokens fail closed before driver mutation");
            player.inventory.mainInventory[2] = null;
            PocketCarrierIdentity.capture(moved, true);
            inventory.drainOwnTank(0, 100);
            session.persistFinal();
            check(
                session.carrierStack() == null && inventory.isDirty()
                    && PocketInventory.readFrom(moved.getTagCompound())
                        .tankTruthAt(0) == 600,
                "a reopened carrier revokes the old headless writer without losing its dirty marker");
        } finally {
            PocketSessions.forget(uuid);
            PocketChannelManager.INSTANCE.reset();
        }
        System.out.println(
            "PocketHeadlessCarrierTest: PASS (native copies, timer, accepted state, foreign, ambiguous, revoked)");
    }
}

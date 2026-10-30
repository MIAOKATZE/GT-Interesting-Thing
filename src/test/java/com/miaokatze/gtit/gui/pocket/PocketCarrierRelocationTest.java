package com.miaokatze.gtit.gui.pocket;

import java.lang.reflect.Field;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import com.cleanroommc.modularui.factory.PlayerInventoryGuiData;
import com.cleanroommc.modularui.factory.inventory.InventoryType;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.miaokatze.gtit.common.items.pocket.PocketCarrierIdentity;
import com.miaokatze.gtit.common.items.pocket.PocketInventory;
import com.miaokatze.gtit.testutil.MinecraftTestBootstrap;

/** Retained closed sessions must follow native inventory copies without granting another carrier write authority. */
public final class PocketCarrierRelocationTest {

    private static final InventoryType MAIN = new InventoryType("gtit_carrier_relocation_test") {

        public ItemStack getStackInSlot(EntityPlayer player, int slot) {
            return player.inventory.mainInventory[slot];
        }

        public void setStackInSlot(EntityPlayer player, int slot, ItemStack stack) {
            player.inventory.mainInventory[slot] = stack;
        }

        public int getSlotCount(EntityPlayer player) {
            return player.inventory.mainInventory.length;
        }
    };

    private static final class Player extends EntityPlayerMP {

        private Player() {
            super(null, null, null, null);
        }
    }

    private static final class Sync extends PanelSyncManager {

        private Sync() {
            super(null, false);
        }

        @Override
        public boolean isClient() {
            return false;
        }
    }

    /** Real vanilla pickup/placement, not a fabricated replacement in the test. */
    private static final class Vanilla extends net.minecraft.inventory.Container {

        Vanilla(EntityPlayer player) {
            for (int index = 0; index < 2; index++) addSlotToContainer(new Slot(player.inventory, index, 0, 0));
        }

        @Override
        public boolean canInteractWith(EntityPlayer player) {
            return true;
        }
    }

    private static final class Fixture {

        final Player player = allocate(Player.class);
        final NekoPocketPanel panel = allocate(NekoPocketPanel.class);
        final PocketInventory inventory = PocketInventory.readFrom(new NBTTagCompound());
        final ItemStack original = new ItemStack(new Item(), 1);

        Fixture() throws Exception {
            player.inventory = new InventoryPlayer(player);
            NBTTagCompound root = new NBTTagCompound();
            inventory.fillOwnTank(0, new FluidStack(FluidRegistry.WATER, 1000));
            inventory.writeTo(root);
            inventory.markClean();
            original.setTagCompound(root);
            player.inventory.mainInventory[0] = original;
            set("data", new PlayerInventoryGuiData(player, MAIN, 0));
            set("syncManager", allocate(Sync.class));
            set("inventory", inventory);
            set("pocket", original);
            set("carrierIdentity", PocketCarrierIdentity.capture(original, true));
            set("server", new NekoPocketServerHandler(panel));
            set("closed", true); // A short channel or distillation input keeps the closed session alive.
        }

        void set(String name, Object value) throws Exception {
            Field field = NekoPocketPanel.class.getDeclaredField(name);
            field.setAccessible(true);
            field.set(panel, value);
        }

        ItemStack move() {
            Vanilla container = new Vanilla(player);
            player.openContainer = container;
            container.slotClick(0, 0, 0, player);
            container.slotClick(1, 0, 0, player);
            ItemStack moved = player.inventory.mainInventory[1];
            check(
                moved != original && original.stackSize == 0 && moved.stackSize == 1,
                "native placement must split a new carrier reference");
            return moved;
        }
    }

    private static <T> T allocate(Class<T> type) {
        try {
            Field field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            return type.cast(((sun.misc.Unsafe) field.get(null)).allocateInstance(type));
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) throws Exception {
        if (!MinecraftTestBootstrap.enter(PocketCarrierRelocationTest.class, args)) return;
        Fixture moved = new Fixture();
        moved.inventory.drainOwnTank(0, 400); // Already accepted; never restore these 400 mB on movement.
        ItemStack live = moved.move();
        check(moved.panel.carrierStack() == live, "closed session must resolve before driver's identity guard");
        check(
            moved.panel.carrierTag() == live.getTagCompound() && moved.panel.isHeldByOwner(),
            "carrier tag and orphan predicate must use the same live carrier");
        moved.panel.persistFinal();
        check(
            PocketInventory.readFrom(live.getTagCompound())
                .tankTruthAt(0) == 600,
            "accepted state persists into the moved carrier");

        Fixture foreign = new Fixture();
        foreign.move();
        foreign.player.inventory.mainInventory[1] = new ItemStack(foreign.original.getItem(), 1);
        check(
            foreign.panel.carrierStack() == null && foreign.panel.carrierTag() == null
                && !foreign.panel.isHeldByOwner(),
            "same-type foreign carrier cannot keep a closed session alive");

        Fixture ambiguous = new Fixture();
        ItemStack one = ambiguous.move();
        ambiguous.player.inventory.mainInventory[2] = one.copy();
        check(
            ambiguous.panel.carrierStack() == null && ambiguous.panel.carrierTag() == null
                && !ambiguous.panel.isHeldByOwner(),
            "duplicate token remains ambiguous instead of choosing a carrier");

        Fixture reopened = new Fixture();
        ItemStack current = reopened.move();
        PocketCarrierIdentity.capture(current, true);
        reopened.inventory.drainOwnTank(0, 400);
        check(reopened.panel.carrierStack() == null, "reopening revokes the old session's live carrier access");
        reopened.panel.persistFinal();
        check(
            PocketInventory.readFrom(current.getTagCompound())
                .tankTruthAt(0) == 1000,
            "stale retained session cannot overwrite the reopened carrier");
        System.out.println(
            "PocketCarrierRelocationTest: PASS (native copies, accepted state, foreign, ambiguity, stale reopen)");
    }
}

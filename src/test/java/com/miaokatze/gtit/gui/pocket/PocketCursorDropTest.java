// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtit.gui.pocket;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.List;

import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import com.cleanroommc.modularui.core.mixins.early.minecraft.ContainerAccessor;
import com.cleanroommc.modularui.factory.PlayerInventoryGuiData;
import com.cleanroommc.modularui.factory.inventory.InventoryType;
import com.cleanroommc.modularui.utils.item.PlayerMainInvWrapper;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widgets.slot.ModularSlot;
import com.cleanroommc.modularui.widgets.slot.SlotGroup;
import com.miaokatze.gtit.common.items.pocket.PocketCarrierIdentity;
import com.miaokatze.gtit.common.items.pocket.PocketInventory;
import com.miaokatze.gtit.common.items.pocket.PocketRuntimeSession;
import com.miaokatze.gtit.common.items.pocket.PocketUpgrades;
import com.miaokatze.gtit.testutil.MinecraftTestBootstrap;

import cpw.mods.fml.common.Loader;

/** Native MUI cursor clicks and pocket persistence using a headless session fixture. */
public final class PocketCursorDropTest {

    private static final InventoryType MAIN = new InventoryType("gtit_cursor_drop_test") {

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

        ItemStack dropped;

        private Player() {
            super(null, null, null, null);
        }

        @Override
        public EntityItem dropPlayerItemWithRandomChoice(ItemStack stack, boolean random) {
            dropped = stack;
            return null;
        }
    }

    private static final class Sync extends PanelSyncManager {

        Player player;
        boolean client;
        int corrections;

        private Sync() {
            super(null, false);
        }

        @Override
        public boolean isClient() {
            return client;
        }

        @Override
        public ItemStack getCursorItem() {
            return player.inventory.getItemStack();
        }

        @Override
        public void setCursorItem(ItemStack stack) {
            player.inventory.setItemStack(stack);
            corrections++;
        }
    }

    private static final class Container extends NekoPocketContainer {

        private List<ModularSlot> shiftTargets = Collections.emptyList();

        Container(NekoPocketPanel panel) {
            super(panel);
        }

        @Override
        public ContainerAccessor acc() {
            return () -> 0;
        }

        @Override
        public List<ModularSlot> getShiftClickSlots() {
            return shiftTargets;
        }

        void wireStorageAndPlayerSlots(PocketInventory inventory, InventoryPlayer player) {
            addSlotToContainer(
                new PocketSlots().storage(inventory, 0)
                    .slotGroup(new SlotGroup(PocketSlots.GROUP_STORAGE, 1)));
            ModularSlot destination = new ModularSlot(new PlayerMainInvWrapper(player), 1)
                .slotGroup(new SlotGroup("test_player", 1));
            addSlotToContainer(destination);
            shiftTargets = Collections.singletonList(destination);
        }

        @Override
        public void detectAndSendChanges() { /* Transport is outside this drop test. */ }
    }

    private static final class Fixture {

        final Player player = allocate(Player.class);
        final Sync sync = allocate(Sync.class);
        final NekoPocketPanel panel = allocate(NekoPocketPanel.class);
        final PocketInventory inventory = PocketInventory.readFrom(new NBTTagCompound());
        final ItemStack carrier = new ItemStack(new Item(), 1);
        final Container container;

        Fixture() throws Exception {
            player.inventory = new InventoryPlayer(player);
            sync.player = player;
            NBTTagCompound root = new NBTTagCompound();
            inventory.fillOwnTank(0, new FluidStack(FluidRegistry.WATER, 1000));
            inventory.writeTo(root);
            inventory.markClean();
            inventory.drainOwnTank(0, 400); // Already accepted: the exported 400 must not reappear on drop.
            carrier.setTagCompound(root);
            player.inventory.setItemStack(carrier);
            set("data", new PlayerInventoryGuiData(player, MAIN, 0));
            set("syncManager", sync);
            set("inventory", inventory);
            set(
                "runtime",
                new PocketRuntimeSession(
                    () -> player,
                    () -> player.inventory.mainInventory[0],
                    0,
                    carrier,
                    false,
                    true,
                    inventory));
            container = new Container(panel);
            player.openContainer = container;
        }

        void set(String field, Object value) throws Exception {
            Field f = NekoPocketPanel.class.getDeclaredField(field);
            f.setAccessible(true);
            f.set(panel, value);
        }

        void drop(int button) {
            container.slotClick(-999, button, 0, player);
        }

        void seedStorage(int count) {
            player.inventory.setItemStack(null);
            player.inventory.mainInventory[0] = carrier;
            PocketUpgrades.installStackCount(carrier, 1);
            inventory.storage()
                .setStackInSlot(0, new ItemStack(Items.iron_ingot, count));
            panel.persistFinal();
            check(!inventory.isDirty() && inventory.storageStack(0).stackSize == count, "test baseline persisted");
            container.wireStorageAndPlayerSlots(inventory, player.inventory);
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

    private static void oversizedShiftClick() throws Exception {
        // The headless Forge bootstrap skips mod discovery. MUI's optional NEA check expects a registry.
        Field namedMods = Loader.class.getDeclaredField("namedMods");
        namedMods.setAccessible(true);
        namedMods.set(Loader.instance(), Collections.emptyMap());

        for (boolean client : new boolean[] { false, true }) {
            Fixture f = new Fixture();
            f.seedStorage(1024);
            f.sync.client = client;
            ItemStack held = new ItemStack(Items.gold_ingot, 3);
            f.player.inventory.setItemStack(held);

            check(f.container.slotClick(0, 0, 1, f.player) == null, "occupied-cursor quick move is rejected");
            check(f.player.inventory.getItemStack() == held && held.stackSize == 3, "cursor retains the held item");
            check(f.player.inventory.mainInventory[1] == null, "quick-move destination was untouched");
            check(f.inventory.storageStack(0).stackSize == 1024 && !f.inventory.isDirty(), "storage remains unchanged");
            check(f.sync.corrections == (client ? 0 : 1), "only the server corrects the cursor");

            if (!client) {
                f.container.slotClick(1, 0, 0, f.player);
                check(
                    f.player.inventory.getItemStack() == null && f.player.inventory.mainInventory[1].stackSize == 3,
                    "the original held item can be placed in the player's inventory");
                f.container.onModularContainerClosed();
                ItemStack reopened = PocketInventory.readFrom(f.carrier.getTagCompound())
                    .storageStack(0);
                check(
                    reopened.getItem() == Items.iron_ingot && reopened.stackSize == 1024,
                    "reopened storage still contains only the original pile");
            }
        }

        Fixture empty = new Fixture();
        empty.seedStorage(1024);
        empty.container.slotClick(0, 0, 1, empty.player);
        check(
            empty.player.inventory.getItemStack() == null && empty.player.inventory.mainInventory[1].stackSize == 64,
            "empty-cursor Shift fills the available player slot within its natural limit");
        empty.container.onModularContainerClosed();
        ItemStack remainder = PocketInventory.readFrom(empty.carrier.getTagCompound())
            .storageStack(0);
        check(remainder.getItem() == Items.iron_ingot && remainder.stackSize == 960,
            "empty-cursor Shift saves the exact remainder on close");
    }

    public static void main(String[] args) throws Exception {
        if (!MinecraftTestBootstrap.enter(PocketCursorDropTest.class, args)) return;
        for (int button : new int[] { 0, 1 }) {
            Fixture f = new Fixture();
            f.drop(button);
            f.panel.persistFinal(); // The ordinary close/save fallback must also see the same accepted state.
            check(f.player.dropped != null && f.player.inventory.getItemStack() == null, "native drop completed");
            check(
                PocketInventory.readFrom(f.player.dropped.getTagCompound())
                    .tankTruthAt(0) == 600,
                "left/full-reference and right/split-copy drops must include the accepted withdrawal");
            check(!f.inventory.isDirty(), "successful flush is clean");
            if (button == 1)
                check(f.player.dropped != f.carrier && f.carrier.stackSize == 0, "real vanilla split occurred");
        }
        Fixture ambiguous = new Fixture();
        ambiguous.player.inventory.mainInventory[0] = ambiguous.carrier.copy();
        ambiguous.drop(1);
        check(
            ambiguous.player.dropped == null && ambiguous.player.inventory.getItemStack() == ambiguous.carrier,
            "unresolved carrier ownership cancels before native mutation");
        check(
            ambiguous.inventory.isDirty() && ambiguous.inventory.tankTruthAt(0) == 600
                && ambiguous.sync.corrections == 1,
            "failed flush retains accepted state and corrects client cursor prediction");

        Fixture foreign = new Fixture();
        ItemStack other = new ItemStack(foreign.carrier.getItem(), 1);
        foreign.player.inventory.setItemStack(other);
        foreign.drop(1);
        check(
            foreign.player.dropped != null && foreign.player.dropped.getTagCompound() == null
                && foreign.inventory.isDirty(),
            "same item but foreign carrier never receives this session's data");

        Fixture stale = new Fixture();
        PocketCarrierIdentity.capture(stale.carrier, true);
        stale.drop(1);
        check(
            stale.inventory.isDirty() && PocketInventory.readFrom(stale.player.dropped.getTagCompound())
                .tankTruthAt(0) == 1000,
            "revoked old session does not save over a reopened carrier");

        Fixture client = new Fixture();
        client.sync.client = true;
        client.set(
            "runtime",
            new PocketRuntimeSession(() -> client.player, () -> null, 0, client.carrier, true, true, client.inventory));
        client.drop(1);
        check(
            client.inventory.isDirty() && PocketInventory.readFrom(client.player.dropped.getTagCompound())
                .tankTruthAt(0) == 1000,
            "client prediction never persists authoritative inventory");
        oversizedShiftClick();
        System.out
            .println("PocketCursorDropTest: PASS (cursor drops and oversized storage Shift-click)");
    }
}

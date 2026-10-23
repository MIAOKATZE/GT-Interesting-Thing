package com.miaokatze.gtit.testlab.scenario;

import java.lang.reflect.Field;
import java.util.Collection;

import net.minecraft.init.Items;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.util.FakePlayer;

import com.cleanroommc.modularui.api.RecipeViewerSettings;
import com.cleanroommc.modularui.factory.GuiData;
import com.cleanroommc.modularui.factory.GuiFactories;
import com.cleanroommc.modularui.factory.PlayerInventoryGuiData;
import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.factory.inventory.InventoryTypes;
import com.cleanroommc.modularui.screen.ModularContainer;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.ModularSyncManager;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.StringSyncValue;
import com.cleanroommc.modularui.widget.WidgetTree;
import com.cleanroommc.modularui.widgets.slot.ModularSlot;
import com.miaokatze.gtit.client.gui.NekoTradeItemDisplay;
import com.miaokatze.gtit.common.items.terminal.NekoVendingTarget;
import com.miaokatze.gtit.common.machine.v2.MTENekoVendingMachineV2;
import com.miaokatze.gtit.common.machine.v2.VendingServerActions;
import com.miaokatze.gtit.currency.NekoCurrencyRegistrar;
import com.miaokatze.gtit.testlab.ProbeContext;
import com.miaokatze.gtit.trade.NekoWalletManager;
import com.miaokatze.gtit.trade.v2.NekoBigItemStack;
import com.miaokatze.gtit.trade.v2.NekoHistoryManager;
import com.miaokatze.gtit.trade.v2.NekoTrade;
import com.miaokatze.gtit.trade.v2.NekoTradeCategory;
import com.miaokatze.gtit.trade.v2.NekoTradeDatabase;
import com.miaokatze.gtit.trade.v2.NekoTradeGroup;

import gregtech.api.metatileentity.BaseMetaTileEntity;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;

/** Server-side two-viewer proof. FakePlayers do not prove physical client input or networking. */
final class ConcurrentVendingProbe {

    private ConcurrentVendingProbe() {}

    static void run(ProbeContext context, FakePlayer a, FakePlayer b, ItemStack terminal, NekoVendingTarget target,
        BaseMetaTileEntity base) throws Exception {
        NativeFixtures.ownIdentity(context, b.getUniqueID());
        MTENekoVendingMachineV2 machine = (MTENekoVendingMachineV2) base.getMetaTileEntity();
        double oldX = a.posX, oldY = a.posY, oldZ = a.posZ;
        a.setPosition(target.x + 0.5, target.y + 0.5, target.z + 0.5);
        b.setPosition(a.posX, a.posY, a.posZ);
        b.inventory.currentItem = 0;
        b.inventory.setInventorySlotContents(0, terminal.copy());
        boolean[][] modes = { { false, false }, { false, true }, { true, true } };
        try {
            for (boolean[] mode : modes) {
                String key = "concurrent-" + (mode[0] ? "remote" : "local") + "-" + (mode[1] ? "remote" : "local");
                try (View first = new View(a, base, mode[0]); View second = new View(b, base, mode[1])) {
                    context.check(
                        key + "-authority",
                        first.container != second.container && first.container.canInteractWith(a)
                            && second.container.canInteractWith(b),
                        "Two independently constructed production server trees share one real controller");
                    String coin = NekoCurrencyRegistrar.NEKO_ID;
                    int aBefore = NekoWalletManager.INSTANCE.getWallet(a.getUniqueID())
                        .getCount(coin);
                    int bBefore = NekoWalletManager.INSTANCE.getWallet(b.getUniqueID())
                        .getCount(coin);
                    machine.inputItems.setStackInSlot(0, context.mark(NekoCurrencyRegistrar.getItemStack(coin, 5)));
                    second.container.detectAndSendChanges();
                    first.container.detectAndSendChanges();
                    context.check(
                        key + "-observer-coins",
                        machine.inputItems.getStackInSlot(0).stackSize == 5
                            && NekoWalletManager.INSTANCE.getWallet(a.getUniqueID())
                                .getCount(coin) == aBefore
                            && NekoWalletManager.INSTANCE.getWallet(b.getUniqueID())
                                .getCount(coin) == bBefore,
                        "Reverse-order real observer detection leaves actorless coins physical and both wallets unchanged");
                    Slot slot = first.input(machine);
                    slot.decrStackSize(2);
                    slot.putStack(slot.getStack());
                    context.check(
                        key + "-partial-withdraw",
                        machine.inputItems.getStackInSlot(0).stackSize == 3
                            && NekoWalletManager.INSTANCE.getWallet(a.getUniqueID())
                                .getCount(coin) == aBefore,
                        "Taking part of a shared coin stack does not deposit its remainder");
                    machine.inputItems.setStackInSlot(0, null);
                    a.inventory.setItemStack(context.mark(NekoCurrencyRegistrar.getItemStack(coin, 3)));
                    first.container.slotClick(slot.slotNumber, 0, 0, a);
                    second.container.detectAndSendChanges();
                    first.container.detectAndSendChanges();
                    context.check(
                        key + "-actor-coins",
                        a.inventory.getItemStack() == null && machine.inputItems.getStackInSlot(0) == null
                            && NekoWalletManager.INSTANCE.getWallet(a.getUniqueID())
                                .getCount(coin) == aBefore + 3
                            && NekoWalletManager.INSTANCE.getWallet(b.getUniqueID())
                                .getCount(coin) == bBefore,
                        "Actual ModularContainer.slotClick deposits exactly once for the placing actor");
                    if (mode[0] && mode[1]) trade(context, a, b, machine, first, second);
                    first.close();
                    context.check(
                        key + "-close-independent",
                        second.container.canInteractWith(b),
                        "Closing the first server sync/session preserves the second viewer's authority");
                }
            }
        } finally {
            a.setPosition(oldX, oldY, oldZ);
            a.inventory.setItemStack(null);
            b.inventory.setItemStack(null);
            machine.inputItems.setStackInSlot(0, null);
        }
    }

    private static void trade(ProbeContext context, FakePlayer a, FakePlayer b, MTENekoVendingMachineV2 machine,
        View first, View second) throws Exception {
        NekoTradeGroup group = new NekoTradeGroup();
        group.setCategory(NekoTradeCategory.ofTabId(3));
        NekoTrade trade = new NekoTrade();
        trade.getFromItems()
            .add(new NekoBigItemStack(new ItemStack(Items.iron_ingot)));
        trade.getToItems()
            .add(new NekoBigItemStack(context.mark(new ItemStack(Items.diamond))));
        group.getTrades()
            .add(trade);
        context.onCleanup(() -> {
            if (NekoTradeDatabase.INSTANCE.getTradeGroup(group.getId()) == group)
                NekoTradeDatabase.INSTANCE.removeTradeGroup(group.getId());
        });
        NekoTradeDatabase.INSTANCE.addTradeGroup(group);
        Field formed = MTEMultiBlockBase.class.getDeclaredField("mMachine");
        formed.setAccessible(true);
        boolean oldFormed = formed.getBoolean(machine);
        try {
            // Exercise the production executor/buffer in this tick; this is not a casing-formation proof.
            formed.setBoolean(machine, true);
            machine.inputItems.setStackInSlot(0, context.mark(new ItemStack(Items.iron_ingot)));
            StringSyncValue status = second.value("nekoV2TradeableStatus");
            status.updateCacheFromSource(true);
            String ready = group.getId() + ":0:true";
            boolean bothReady = status.getStringValue()
                .contains(ready);
            NekoTradeItemDisplay display = NekoTradeItemDisplay.fromNekoTrade(trade, group, 0);
            String request = group.getId() + ":0:" + display.getDefinitionFingerprint();
            first.value("nekoV2TradeRequest")
                .setValue(request, true, false);
            second.value("nekoV2TradeRequest")
                .setValue(request, true, false);
            VendingServerActions.drainServerActions();
            status.updateCacheFromSource(false);
            context.check(
                "concurrent-last-input-history",
                bothReady && machine.inputItems.getStackInSlot(0) == null
                    && machine.getOutputBufferSize() == 1
                    && NekoHistoryManager.INSTANCE.getHistory(a.getUniqueID(), group.getId())
                        .getTradeCount() == 1
                    && NekoHistoryManager.INSTANCE.getHistory(b.getUniqueID(), group.getId())
                        .getTradeCount() == 0,
                "Two production GUI requests compete on one input: one buffer item and exactly one actor history");
            context.check(
                "concurrent-peer-status-same-tick",
                status.getStringValue()
                    .contains(group.getId() + ":0:false"),
                "The other viewer's cached readiness changes in the same tick without a twenty-tick wait");
            trade.getToItems()
                .clear();
            trade.getToItems()
                .add(new NekoBigItemStack(context.mark(new ItemStack(Items.gold_ingot))));
            machine.inputItems.setStackInSlot(0, context.mark(new ItemStack(Items.iron_ingot, 2)));
            first.value("nekoV2TradeRequest")
                .setValue(request, true, false);
            VendingServerActions.drainServerActions();
            context.check(
                "concurrent-old-display-fenced",
                machine.inputItems.getStackInSlot(0).stackSize == 2 && machine.getOutputBufferSize() == 1,
                "A request captured from the old clickable display refuses the edited definition before payment");
        } finally {
            formed.setBoolean(machine, oldFormed);
            ((Collection<?>) NativeFixtures.field(machine, "outputBuffer")).clear();
            machine.inputItems.setStackInSlot(0, null);
        }
    }

    private static final class View implements AutoCloseable {

        private final FakePlayer player;
        private final Container previous;
        private final ModularSyncManager sync = new ModularSyncManager(false);
        private final PanelSyncManager main = new PanelSyncManager(sync, true);
        private ModularContainer container;
        private boolean closed;

        View(FakePlayer player, BaseMetaTileEntity base, boolean remote) {
            this.player = player;
            this.previous = player.openContainer;
            UISettings settings = new UISettings(RecipeViewerSettings.DUMMY);
            GuiData data;
            ModularPanel panel;
            try {
                if (remote) {
                    PlayerInventoryGuiData item = new PlayerInventoryGuiData(player, InventoryTypes.PLAYER, 0);
                    settings.defaultCanInteractWith(GuiFactories.playerInventory(), item);
                    data = item;
                    panel = GuiFactories.playerInventory()
                        .createPanel(item, main, settings);
                } else {
                    PosGuiData local = new PosGuiData(player, base.xCoord, base.yCoord, base.zCoord);
                    data = local;
                    settings.canInteractWith(base::isUseableByPlayer);
                    panel = ((MTENekoVendingMachineV2) base.getMetaTileEntity()).buildUI(local, main, settings);
                }
                WidgetTree.collectSyncValues(main, panel);
                container = settings.hasCustomContainer() ? settings.createContainer() : new ModularContainer();
                container.construct(player, sync, settings, panel.getName(), data);
                container.windowId = 31; // Equal IDs on two players must remain independent.
                player.openContainer = container;
                sync.onOpen();
            } catch (RuntimeException | Error failure) {
                close();
                throw failure;
            }
        }

        Slot input(MTENekoVendingMachineV2 machine) {
            for (Object raw : container.inventorySlots) {
                if (raw instanceof ModularSlot && ((ModularSlot) raw).getItemHandler() == machine.inputItems)
                    return (Slot) raw;
            }
            throw new IllegalStateException("No production input slot");
        }

        StringSyncValue value(String key) {
            return (StringSyncValue) main.findSyncHandlerNullable(key, 0);
        }

        @Override
        public void close() {
            if (closed) return;
            closed = true;
            player.openContainer = previous;
            if (sync.isOpen()) sync.onClose();
            if (sync.isClosed()) sync.dispose();
        }
    }
}

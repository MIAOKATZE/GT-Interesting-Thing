package com.miaokatze.gtit.testlab.scenario;

import java.lang.reflect.Field;

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
import com.miaokatze.gtit.testlab.ProbeTask;
import com.miaokatze.gtit.trade.NekoWalletManager;
import com.miaokatze.gtit.trade.v2.NekoBigItemStack;
import com.miaokatze.gtit.trade.v2.NekoHistoryManager;
import com.miaokatze.gtit.trade.v2.NekoTrade;
import com.miaokatze.gtit.trade.v2.NekoTradeCategory;
import com.miaokatze.gtit.trade.v2.NekoTradeDatabase;
import com.miaokatze.gtit.trade.v2.NekoTradeGroup;
import com.miaokatze.gtit.util.PlayerLookup;
import com.miaokatze.gtit.util.ServerTaskScheduler;

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
                try (View first = new View(context, a, base, mode[0]);
                    View second = new View(context, b, base, mode[1])) {
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

    /** Uses only natural production END dispatch; never drains a queue or invokes a business callback directly. */
    static ProbeTask tradeRequests(ProbeContext context, FakePlayer a, FakePlayer b, BaseMetaTileEntity base)
        throws Exception {
        TradeBatch batch = new TradeBatch(context, a, b, base);
        context.onCleanup(batch::close);
        return batch;
    }

    private static final class TradeBatch implements ProbeTask {

        private final ProbeContext context;
        private final FakePlayer a, b;
        private final BaseMetaTileEntity base;
        private final MTENekoVendingMachineV2 machine;
        private final NekoTradeGroup group = new NekoTradeGroup();
        private final NekoTrade initial = new NekoTrade(), edited = new NekoTrade();
        private final Field formed;
        private boolean oldFormed, formationOwned;
        private Object requestQueue;
        private final java.util.Queue<ItemStack> outputs;
        private View first, second;
        private NativeProbeSessions sessions;
        private boolean submitted, prepared, cancelled, closed, observed, initiallyReady, firstSucceeded,
            freshSucceeded;
        private volatile boolean consumed;
        private int waits;
        private long executionTick = -1, expiryRevision;
        private String originalRequest, editedRequest;
        private java.util.Map<String, java.math.BigInteger> fundsA, fundsB;

        @SuppressWarnings("unchecked")
        TradeBatch(ProbeContext context, FakePlayer a, FakePlayer b, BaseMetaTileEntity base) throws Exception {
            this.context = context;
            this.a = a;
            this.b = b;
            this.base = base;
            machine = (MTENekoVendingMachineV2) base.getMetaTileEntity();
            formed = MTEMultiBlockBase.class.getDeclaredField("mMachine");
            formed.setAccessible(true);
            outputs = (java.util.Queue<ItemStack>) NativeFixtures.field(machine, "outputBuffer");
        }

        @Override
        public boolean tick() throws Exception {
            if (!submitted) {
                submitted = true;
                begin();
                context.check(
                    "concurrent-player-requests-not-inline",
                    executionTick == -1 && !consumed,
                    "Production GUI and observer requests await the registered server END consumer");
                return false;
            }
            if (!consumed && ++waits < 10) return false;
            context.check(
                "concurrent-native-request-batch-complete",
                consumed && observed,
                "Native bounded FIFO reached its final observation; consumed=" + consumed + ", observed=" + observed);
            close();
            return true;
        }

        private void begin() throws Exception {
            if (!outputs.isEmpty())
                throw new IllegalStateException("Owned machine must begin with an empty output buffer");
            for (int i = 0; i < machine.inputItems.getSlots(); i++) if (machine.inputItems.getStackInSlot(i) != null)
                throw new IllegalStateException("Nonempty fixture input");
            for (int i = 0; i < machine.outputItems.getSlots(); i++) if (machine.outputItems.getStackInSlot(i) != null)
                throw new IllegalStateException("Nonempty fixture output");
            if (NekoTradeDatabase.INSTANCE.getTradeGroup(group.getId()) != null)
                throw new IllegalStateException("Generated fixture group UUID already exists");
            prepared = true;
            group.setCategory(NekoTradeCategory.ofTabId(3));
            initial.getFromItems()
                .add(new NekoBigItemStack(new ItemStack(Items.iron_ingot)));
            initial.getToItems()
                .add(new NekoBigItemStack(context.mark(new ItemStack(Items.diamond))));
            edited.getFromItems()
                .add(new NekoBigItemStack(new ItemStack(Items.iron_ingot)));
            edited.getToItems()
                .add(new NekoBigItemStack(context.mark(new ItemStack(Items.gold_ingot))));
            group.getTrades()
                .add(initial);
            NekoTradeDatabase.INSTANCE.addTradeGroup(group);
            first = new View(context, a, base, true);
            second = new View(context, b, base, true);
            sessions = NativeProbeSessions.open(context, a, b);
            machine.inputItems.setStackInSlot(0, context.mark(new ItemStack(Items.iron_ingot)));
            fundsA = walletSnapshot(a);
            fundsB = walletSnapshot(b);
            requireEmptyNativeBudget();
            originalRequest = group.getId() + ":0:"
                + NekoTradeItemDisplay.fromNekoTrade(initial, group, 0)
                    .getDefinitionFingerprint();
            editedRequest = group.getId() + ":0:"
                + NekoTradeItemDisplay.fromNekoTrade(edited, group, 0)
                    .getDefinitionFingerprint();
            // Eleven FIFO entries fit the native 64/tick budget and 16/actor quota. No artificial tick/drain.
            // Anchor and observers bracket real GUI consumers in the SAME natural execution tick.
            step("anchor", this::anchor, false);
            request("first-original", first, originalRequest);
            step("peer-after-first", this::observePeerAfterFirst, false);
            request("second-original", second, originalRequest);
            step("competition-and-edit", this::observeCompetitionAndEdit, false);
            request("old-definition", first, originalRequest);
            step("definition-fence", this::observeOldDefinition, false);
            request("fresh-definition", first, editedRequest);
            step("fresh-token-and-expire", this::observeFreshAndExpire, false);
            request("queued-before-expiry", first, editedRequest);
            step("expired-session", this::observeExpired, true);
        }

        private void requireEmptyNativeBudget() throws ReflectiveOperationException {
            requestQueue = NativeFixtures.field(ServerTaskScheduler.class, "PLAYER_REQUESTS");
            synchronized (requestQueue) {
                if ((Integer) NativeFixtures.field(requestQueue, "pending") != 0
                    || (Integer) NativeFixtures.field(requestQueue, "globalLimit") < 11
                    || (Integer) NativeFixtures.field(requestQueue, "ownerLimit") < 7
                    || (Integer) NativeFixtures.field(ServerTaskScheduler.class, "MAX_PLAYER_REQUESTS_PER_TICK") < 11)
                    throw new IllegalStateException(
                        "Native acceptance requires an empty, adequate current request budget");
            }
        }

        private void anchor() {
            executionTick = context.world()
                .getTotalWorldTime();
            try {
                // The owned formation fixture begins INSIDE the consuming END tick, never across world ticks.
                oldFormed = formed.getBoolean(machine);
                formationOwned = true;
                formed.setBoolean(machine, true);
            } catch (IllegalAccessException failure) {
                throw new IllegalStateException("Cannot prepare owned formed fixture", failure);
            }
            String ready = group.getId() + ":0:true";
            boolean aReady = status(first).contains(ready), bReady = status(second).contains(ready);
            initiallyReady = PlayerLookup.isCurrentSession(a) && PlayerLookup.isCurrentSession(
                b) && first.container.canInteractWith(a) && second.container.canInteractWith(b) && aReady && bReady;
            context.check(
                "concurrent-live-session-ready",
                initiallyReady,
                "Both production guards/readiness snapshots are true in the consuming tick; a=" + aReady
                    + ", b="
                    + bReady);
        }

        private void request(String name, View view, String request) throws ReflectiveOperationException {
            // Admission receipt only: never inspect/execute/remove queued Runnable callbacks.
            synchronized (requestQueue) {
                int pendingBefore = (Integer) NativeFixtures.field(requestQueue, "pending");
                java.util.Map<?, ?> owners = (java.util.Map<?, ?>) NativeFixtures.field(requestQueue, "owners");
                Integer previous = (Integer) owners.get(view.player);
                int ownerBefore = previous == null ? 0 : previous;
                view.value("nekoV2TradeRequest")
                    .setValue(request, true, false);
                int pendingAfter = (Integer) NativeFixtures.field(requestQueue, "pending");
                Integer current = (Integer) owners.get(view.player);
                int ownerAfter = current == null ? 0 : current;
                boolean admitted = pendingAfter == pendingBefore + 1 && ownerAfter == ownerBefore + 1;
                context.check(
                    "concurrent-gui-admission-" + name,
                    admitted,
                    "Actual GUI setter admitted one bounded request for the exact actor; pending=" + pendingBefore
                        + "->"
                        + pendingAfter
                        + ", owner="
                        + ownerBefore
                        + "->"
                        + ownerAfter);
                if (!admitted) throw new IllegalStateException("Actual GUI request was not admitted: " + name);
            }
        }

        private void step(String name, Runnable action, boolean last) {
            boolean accepted = VendingServerActions.schedulePlayerRequest(b, () -> {
                if (cancelled) return;
                try {
                    action.run();
                } catch (RuntimeException | LinkageError failure) {
                    context.check("concurrent-native-step-" + name, false, failure.toString());
                    close(); // Expire owned sessions/containers; all pending production requests then fail closed.
                }
            }, () -> {
                // Scheduler completion is thread-safe local bookkeeping only, including rejected admission.
                if (last) consumed = true;
            });
            if (!accepted) throw new IllegalStateException("Native fixture observer admission rejected: " + name);
        }

        private void observePeerAfterFirst() {
            boolean firstCompleted = initiallyReady && sameTick()
                && inputCount() == 0
                && machine.getOutputBufferSize() == 1
                && outputCount(Items.diamond) == 1
                && history(a) == 1
                && history(b) == 0;
            context.check(
                "concurrent-peer-status-same-tick",
                firstCompleted && status(second).contains(group.getId() + ":0:false"),
                "B's cached readiness changes after A, BEFORE B's own callback can dirty the revision; " + facts());
        }

        private void observeCompetitionAndEdit() {
            String loserResult = result(second);
            firstSucceeded = initiallyReady && sameTick()
                && fundsStable()
                && inputCount() == 0
                && machine.getOutputBufferSize() == 1
                && outputCount(Items.diamond) == 1
                && history(a) == 1
                && history(b) == 0
                && loserResult.contains("输入物品不足");
            context.check(
                "concurrent-last-input-history",
                firstSucceeded,
                "Both admitted GUI callbacks consumed; B reaches insufficient-input result=" + loserResult
                    + "; "
                    + facts());
            // Only this run-owned definition and input are edited, after the competition is observed.
            group.getTrades()
                .set(0, edited);
            machine.inputItems.setStackInSlot(0, context.mark(new ItemStack(Items.iron_ingot, 2)));
        }

        private void observeOldDefinition() {
            String result = result(first);
            context.check(
                "concurrent-old-display-fenced",
                firstSucceeded && sameTick()
                    && fundsStable()
                    && PlayerLookup.isCurrentSession(a)
                    && first.container.canInteractWith(a)
                    && inputCount() == 2
                    && machine.getOutputBufferSize() == 1
                    && history(a) == 1
                    && history(b) == 0
                    && result.contains("交易内容已更新"),
                "Live GUI reached the stale-definition branch, not generic session denial; result=" + result
                    + "; "
                    + facts());
        }

        private void observeFreshAndExpire() {
            freshSucceeded = sameTick() && fundsStable()
                && inputCount() == 1
                && machine.getOutputBufferSize() == 2
                && outputCount(Items.diamond) == 1
                && outputCount(Items.gold_ingot) == 1
                && history(a) == 2
                && history(b) == 0
                && result(first).contains("交易成功");
            context.check(
                "concurrent-fresh-display-positive",
                freshSucceeded && PlayerLookup.isCurrentSession(a),
                "Fresh edited fingerprint succeeds through the same production GUI/session path; " + facts());
            expiryRevision = machine.getTradeStateRevision();
            sessions.expireChannel(a);
            context.check(
                "concurrent-channel-expired-before-dispatch",
                !PlayerLookup.isCurrentSession(a) && PlayerLookup.isCurrentSession(b)
                    && PlayerLookup.getOnlinePlayerByUuid(a.getUniqueID()) == a
                    && first.container.canInteractWith(a),
                "Only A's native channel is closed; exact actor/list/container remain, isolating the channel guard");
        }

        private void observeExpired() {
            try {
                context.check(
                    "concurrent-expired-session-rejected",
                    freshSucceeded && sameTick()
                        && fundsStable()
                        && !PlayerLookup.isCurrentSession(a)
                        && inputCount() == 1
                        && machine.getOutputBufferSize() == 2
                        && history(a) == 2
                        && history(b) == 0
                        && machine.getTradeStateRevision() == expiryRevision,
                    "Already queued fresh-token GUI action is refused after native channel expiry; " + facts());
                observed = true;
            } finally {
                close();
            }
        }

        private String status(View view) {
            StringSyncValue value = view.value("nekoV2TradeableStatus");
            value.updateCacheFromSource(false);
            return value.getStringValue();
        }

        private String result(View view) {
            StringSyncValue value = view.value("nekoV2TradeResult");
            value.updateCacheFromSource(false);
            return value.getStringValue();
        }

        private boolean sameTick() {
            return executionTick >= 0 && executionTick == context.world()
                .getTotalWorldTime();
        }

        private java.util.Map<String, java.math.BigInteger> walletSnapshot(FakePlayer player) {
            com.miaokatze.gtit.trade.NekoWallet wallet = NekoWalletManager.INSTANCE.getWallet(player.getUniqueID());
            java.util.Map<String, java.math.BigInteger> snapshot = new java.util.TreeMap<>();
            synchronized (wallet) {
                for (String currency : wallet.getCurrencyIds()) snapshot.put(currency, wallet.getExactCount(currency));
            }
            return snapshot;
        }

        private boolean fundsStable() {
            return fundsA != null && fundsB != null
                && fundsA.equals(walletSnapshot(a))
                && fundsB.equals(walletSnapshot(b));
        }

        private long history(FakePlayer player) {
            return NekoHistoryManager.INSTANCE.getHistory(player.getUniqueID(), group.getId())
                .getTradeCount();
        }

        private int inputCount() {
            ItemStack stack = machine.inputItems.getStackInSlot(0);
            return stack == null ? 0 : stack.stackSize;
        }

        private int outputCount(net.minecraft.item.Item item) {
            int count = 0;
            for (ItemStack stack : outputs) if (stack.getItem() == item) count += stack.stackSize;
            return count;
        }

        private String facts() {
            return "tick=" + context.world()
                .getTotalWorldTime()
                + "/"
                + executionTick
                + ", input="
                + inputCount()
                + ", buffer="
                + machine.getOutputBufferSize()
                + ", historyA="
                + history(a)
                + ", historyB="
                + history(b)
                + ", revision="
                + machine.getTradeStateRevision()
                + ", sessionA="
                + PlayerLookup.isCurrentSession(a)
                + ", sessionB="
                + PlayerLookup.isCurrentSession(b)
                + ", fundsStable="
                + fundsStable();
        }

        private boolean marked(ItemStack stack) {
            return stack != null && stack.hasTagCompound()
                && context.runId()
                    .equals(
                        stack.getTagCompound()
                            .getCompoundTag(ProbeContext.TEST_ITEM_TAG)
                            .getString("runId"));
        }

        @Override
        public void close() {
            if (closed) return;
            cancelled = true; // Pending owned observers remain inert even when a cleanup phase needs retry.
            RuntimeException failure = null;
            failure = attempt(failure, () -> { if (sessions != null) sessions.close(); });
            failure = attempt(failure, () -> { if (second != null) second.close(); });
            failure = attempt(failure, () -> { if (first != null) first.close(); });
            failure = attempt(failure, () -> {
                if (!formationOwned) return;
                try {
                    formed.setBoolean(machine, oldFormed);
                    formationOwned = false;
                } catch (IllegalAccessException error) {
                    throw new IllegalStateException("Cannot restore owned controller formation fixture", error);
                }
            });
            if (prepared) {
                failure = attempt(failure, () -> {
                    // Poll cancels production ledger entries; refuse to delete any unmarked output.
                    for (ItemStack stack : outputs) if (!marked(stack))
                        throw new IllegalStateException("Unowned output appeared in fixture buffer");
                    while (outputs.poll() != null) {}
                });
                for (int i = 0; i < machine.outputItems.getSlots(); i++) {
                    final int slot = i;
                    failure = attempt(failure, () -> {
                        ItemStack stack = machine.outputItems.getStackInSlot(slot);
                        if (stack == null) return;
                        if (!marked(stack))
                            throw new IllegalStateException("Unowned output appeared in fixture slot " + slot);
                        machine.outputItems.setStackInSlot(slot, null);
                    });
                }
                failure = attempt(failure, () -> {
                    ItemStack input = machine.inputItems.getStackInSlot(0);
                    if (input != null && !marked(input)) throw new IllegalStateException("Unowned fixture input");
                    machine.inputItems.setStackInSlot(0, null);
                });
            }
            // Group cleanup is independent even if channel, view, formation, or physical-item cleanup failed.
            failure = attempt(failure, () -> {
                if (!prepared) return;
                NekoTradeGroup current = NekoTradeDatabase.INSTANCE.getTradeGroup(group.getId());
                if (current == group) NekoTradeDatabase.INSTANCE.removeTradeGroup(group.getId());
                else if (current != null)
                    throw new IllegalStateException("Fixture group identity changed; not removing replacement");
                if (NekoTradeDatabase.INSTANCE.getTradeGroupsByTab(group.getTabId())
                    .contains(group)) throw new IllegalStateException("Owned fixture group remains indexed");
            });
            context.check(
                "concurrent-native-owned-cleanup",
                failure == null,
                "Independent restoration of owned sessions, views, formation, marked items and exact group; pending requests are not cleared");
            closed = failure == null;
            if (failure != null) throw failure;
        }
    }

    private static RuntimeException attempt(RuntimeException previous, Runnable action) {
        try {
            action.run();
        } catch (RuntimeException | LinkageError error) {
            if (previous == null) return new IllegalStateException("Owned native vending cleanup failed", error);
            previous.addSuppressed(error);
        }
        return previous;
    }

    private static final class View implements AutoCloseable {

        private final FakePlayer player;
        private final Container previous;
        private final ModularSyncManager sync = new ModularSyncManager(false);
        private final PanelSyncManager main = new PanelSyncManager(sync, true);
        private ModularContainer container;
        private boolean closed;

        View(ProbeContext context, FakePlayer player, BaseMetaTileEntity base, boolean remote) {
            this.player = player;
            this.previous = player.openContainer;
            // Retain a cleanup handle even if construction throws before the caller can assign this View.
            context.onCleanup(this::close);
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
                try {
                    close();
                } catch (RuntimeException | Error cleanup) {
                    failure.addSuppressed(cleanup);
                }
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
            RuntimeException failure = null;
            failure = attempt(failure, () -> {
                if (player.openContainer == container) player.openContainer = previous;
                else if (player.openContainer != previous)
                    throw new IllegalStateException("Synthetic viewer container identity changed outside its scope");
            });
            failure = attempt(failure, () -> { if (sync.isOpen()) sync.onClose(); });
            failure = attempt(failure, () -> { if (sync.isClosed()) sync.dispose(); });
            closed = failure == null;
            if (failure != null) throw failure;
        }
    }
}

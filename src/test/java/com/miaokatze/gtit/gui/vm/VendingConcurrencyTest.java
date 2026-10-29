package com.miaokatze.gtit.gui.vm;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import com.cleanroommc.modularui.screen.ModularContainer;
import com.cleanroommc.modularui.utils.item.ItemStackHandler;
import com.cleanroommc.modularui.widgets.slot.ModularSlot;
import com.cleanroommc.modularui.widgets.slot.SlotGroup;
import com.miaokatze.gtit.client.gui.NekoTradeItemDisplay;
import com.miaokatze.gtit.common.machine.v2.MTENekoVendingMachineV2;
import com.miaokatze.gtit.currency.NekoCurrencyRegistrar;
import com.miaokatze.gtit.testutil.MinecraftTestBootstrap;
import com.miaokatze.gtit.trade.TeamDataProvider;
import com.miaokatze.gtit.trade.v2.NekoBigItemStack;
import com.miaokatze.gtit.trade.v2.NekoHistoryManager;
import com.miaokatze.gtit.trade.v2.NekoTrade;
import com.miaokatze.gtit.trade.v2.NekoTradeCategory;
import com.miaokatze.gtit.trade.v2.NekoTradeDatabase;
import com.miaokatze.gtit.trade.v2.NekoTradeDefinition;
import com.miaokatze.gtit.trade.v2.NekoTradeGroup;
import com.miaokatze.gtit.trade.v2.NekoTradeResult;

/** Real MUI2 slot/transfer code and authoritative trade paths, without a running server/client. */
public final class VendingConcurrencyTest {

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) throws Exception {
        if (!MinecraftTestBootstrap.enter(VendingConcurrencyTest.class, args)) return;
        Field named = cpw.mods.fml.common.Loader.class.getDeclaredField("namedMods");
        named.setAccessible(true);
        named.set(cpw.mods.fml.common.Loader.instance(), new java.util.HashMap<>());
        cpw.mods.fml.server.FMLServerHandler.instance();
        Thread.currentThread()
            .setName("Server thread");
        field(TeamDataProvider.class, "available").set(null, Boolean.FALSE);
        field(com.miaokatze.gtit.trade.NekoWalletManager.class, "acceptingWallets")
            .setBoolean(com.miaokatze.gtit.trade.NekoWalletManager.INSTANCE, true);
        java.io.File historyDirectory = java.nio.file.Files.createTempDirectory("gtit-vending-history-").toFile();
        Method bindHistory = NekoHistoryManager.class.getDeclaredMethod("bindSaveDirectory", java.io.File.class);
        bindHistory.setAccessible(true);
        bindHistory.invoke(NekoHistoryManager.INSTANCE, historyDirectory);
        NekoCurrencyRegistrar.nekoCoinItem = Items.emerald;
        actorDeposits();
        shiftTransfers();
        sharedStateAndCompetition();
        disconnectedMeRefund();
        oldDisplayOrders();
        canonicalDefinition();
        System.out.println("VendingConcurrencyTest: 6 cases passed");
        CoinWithdrawalCapacityTest.run();
        WalletCreditCapacityTest.run();
        com.miaokatze.gtit.trade.WalletConservationTest.run();
        com.miaokatze.gtit.trade.WalletManagerPersistenceTest.run();
        com.miaokatze.gtit.trade.v2.HistoryPersistenceTest.run();
        NekoHistoryManager.INSTANCE.unloadAll();
        for (java.io.File saved : historyDirectory.listFiles()) java.nio.file.Files.delete(saved.toPath());
        java.nio.file.Files.delete(historyDirectory.toPath());
    }

    private static void actorDeposits() {
        ItemStackHandler inventory = new ItemStackHandler(2);
        int[] ledger = { 0, 0 };
        VendingInputSlot a = slot(inventory, 0, ledger, 0);
        VendingInputSlot b = slot(inventory, 0, ledger, 1);
        inventory.setStackInSlot(0, new ItemStack(Items.emerald, 7));
        b.onSlotChangedReal(b.getStack(), false, false, false);
        a.onSlotChangedReal(a.getStack(), false, false, true);
        check(ledger[0] == 0 && ledger[1] == 0, "observing/initializing shared coins never credits a viewer");
        check(!a.isItemValid(new ItemStack(Items.emerald)), "actorless coins cannot absorb a new shift merge");
        ItemStack taken = b.decrStackSize(2);
        b.putStack(b.getStack());
        check(
            taken.stackSize == 2 && inventory.getStackInSlot(0).stackSize == 5 && ledger[1] == 0,
            "partial withdrawal never deposits the remaining shared coins");
        inventory.setStackInSlot(0, null);
        a.putStack(new ItemStack(Items.emerald, 3));
        b.onSlotChangedReal(b.getStack(), false, false, false);
        b.putStack(new ItemStack(Items.emerald, 4));
        check(
            ledger[0] == 3 && ledger[1] == 4 && a.getStack() == null,
            "two actors deposit exactly once into their own ledger");
        VendingInputSlot prediction = new VendingInputSlot(inventory, 1, stack -> false, () -> {});
        prediction.putStack(new ItemStack(Items.emerald, 6));
        check(
            inventory.getStackInSlot(1).stackSize == 6,
            "client prediction or failed server settlement preserves its physical coins");
    }

    private static VendingInputSlot slot(ItemStackHandler inventory, int index, int[] ledger, int actor) {
        VendingInputSlot result = new VendingInputSlot(inventory, index, stack -> {
            ledger[actor] += stack.stackSize;
            return true;
        }, () -> {});
        result.slotGroup(new SlotGroup("target", 2));
        return result;
    }

    private static void shiftTransfers() {
        ItemStackHandler inventory = new ItemStackHandler(2);
        int[] ledger = { 0 };
        VendingInputSlot first = slot(inventory, 0, ledger, 0);
        VendingInputSlot second = slot(inventory, 1, ledger, 0);
        ModularSlot source = new ModularSlot(new ItemStackHandler(1), 0).slotGroup(new SlotGroup("source", 1));
        Transfer container = new Transfer(Arrays.asList(first, second));
        inventory.setStackInSlot(0, new ItemStack(Items.emerald, 5));
        ItemStack remainder = container.move(source, new ItemStack(Items.emerald, 9));
        check(
            (remainder == null || remainder.stackSize == 0) && ledger[0] == 9
                && first.getStack().stackSize == 5
                && second.getStack() == null,
            "real MUI2 shift code skips residual coins and deposits into an empty actor slot");
        inventory.setStackInSlot(0, new ItemStack(Items.iron_ingot, 5));
        remainder = container.move(source, new ItemStack(Items.iron_ingot, 4));
        check(
            (remainder == null || remainder.stackSize == 0) && first.getStack().stackSize == 9,
            "ordinary items retain MUI2 in-place shift merging");
        for (int i = 0; i < 2; i++) inventory.setStackInSlot(i, null);
        first.putStack(new ItemStack(Items.emerald, 2));
        second.putStack(new ItemStack(Items.emerald, 2));
        check(ledger[0] == 13, "drag-style split putStack operations each deposit only their own split");
    }

    private static MTENekoVendingMachineV2 machine() throws Exception {
        MTENekoVendingMachineV2 machine = new MTENekoVendingMachineV2("concurrency_test");
        field(machine.getClass(), "mMachine").setBoolean(machine, true);
        return machine;
    }

    private static void disconnectedMeRefund() throws Exception {
        MTENekoVendingMachineV2 machine = machine();
        // Only the captured identity matters: no hatch/AE methods may run after disconnection.
        Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
        Field singleton = unsafeClass.getDeclaredField("theUnsafe");
        singleton.setAccessible(true);
        Object hatch = unsafeClass.getMethod("allocateInstance", Class.class).invoke(
            singleton.get(null), com.cubefury.vendingmachine.blocks.MTEVendingUplinkHatch.class);
        Field liveHatch = field(machine.getClass(), "uplinkHatch");
        liveHatch.set(machine, hatch);
        com.miaokatze.gtit.trade.v2.NekoTradeExecutor.InputSlotAccessor input = machine.createInputSlotAccessor();
        liveHatch.set(machine, null);
        ItemStack receipt = new ItemStack(Items.iron_ingot, 129);
        receipt.setTagCompound(new NBTTagCompound());
        receipt.getTagCompound().setString("owner", "extracted-before-disconnect");
        input.refundME(receipt);
        check(machine.getOutputBufferSize() == 3, "disconnected ME receipt becomes three durable physical stacks");
        java.util.Queue<?> queue = (java.util.Queue<?>) field(machine.getClass(), "outputBuffer").get(machine);
        int count = 0;
        for (Object value : queue) {
            ItemStack stack = (ItemStack) value;
            check(stack.stackSize > 0 && stack.stackSize <= 64, "compensation remains vanilla-NBT safe");
            check(ItemStack.areItemStackTagsEqual(receipt, stack), "compensation keeps exact receipt tags");
            count += stack.stackSize;
        }
        check(count == 129, "a stale captured hatch cannot turn missing live destination into successful insertion");
    }

    private static NekoTradeGroup group() {
        NekoTradeGroup group = new NekoTradeGroup();
        group.setCategory(NekoTradeCategory.ofTabId(3));
        NekoTrade trade = new NekoTrade();
        trade.getFromItems()
            .add(new NekoBigItemStack(new ItemStack(Items.iron_ingot)));
        trade.getToItems()
            .add(new NekoBigItemStack(new ItemStack(Items.diamond)));
        group.getTrades()
            .add(trade);
        NekoTradeDatabase.INSTANCE.addTradeGroup(group);
        return group;
    }

    private static String status(StatusCodec codec, UUID player) throws Exception {
        Method build = StatusCodec.class.getDeclaredMethod("buildTradeableStatusString", UUID.class);
        build.setAccessible(true);
        return (String) build.invoke(codec, player);
    }

    private static void sharedStateAndCompetition() throws Exception {
        MTENekoVendingMachineV2 machine = machine();
        NekoTradeGroup group = group();
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        try {
            StatusCodec first = new StatusCodec(machine, null, () -> {});
            StatusCodec second = new StatusCodec(machine, null, () -> {});
            machine.inputItems.setStackInSlot(0, new ItemStack(Items.iron_ingot));
            check(
                status(first, a).contains(group.getId() + ":0:true")
                    && status(second, b).contains(group.getId() + ":0:true"),
                "both viewers initially see the same last input");
            long before = machine.getTradeStateRevision();
            VendingInputSlot validator = new VendingInputSlot(
                machine.inputItems,
                0,
                stack -> false,
                machine::markTradeStateChanged);
            validator.isItemValid(new ItemStack(Items.iron_ingot));
            validator.getItemStackLimit(new ItemStack(Items.iron_ingot));
            check(
                machine.getTradeStateRevision() == before,
                "pure slot validation never mutates shared inventory/revision");
            check(
                machine.processTrade(a, group.getId(), 0)
                    .isSuccess()
                    && !machine.processTrade(b, group.getId(), 0)
                        .isSuccess()
                    && machine.getOutputBufferSize() == 1
                    && machine.inputItems.getStackInSlot(0) == null,
                "two main-thread requests competing for one input produce exactly once");
            check(
                NekoHistoryManager.INSTANCE.getHistory(a, group.getId())
                    .getTradeCount() == 1
                    && NekoHistoryManager.INSTANCE.getHistory(b, group.getId())
                        .getTradeCount() == 0,
                "only the successful actor records history");
            check(
                status(second, b).contains(group.getId() + ":0:false"),
                "the other viewer's cached ready flag expires in the same tick via machine revision");
            machine = machine();
            machine.inputItems.setStackInSlot(0, new ItemStack(Items.iron_ingot, 2));
            for (int i = 0; i < MTENekoVendingMachineV2.OUTPUT_SLOTS - 1; i++)
                machine.outputItems.setStackInSlot(i, new ItemStack(Items.stick));
            check(
                machine.processTrade(a, group.getId(), 0)
                    .isSuccess()
                    && machine.processTrade(b, group.getId(), 0)
                        .getStatus() == NekoTradeResult.Status.OUTPUT_FULL
                    && machine.inputItems.getStackInSlot(0).stackSize == 1
                    && machine.getOutputBufferSize() == 1,
                "buffer reservation prevents a second actor overselling the last output slot");
        } finally {
            NekoTradeDatabase.INSTANCE.removeTradeGroup(group.getId());
        }
    }

    private static void oldDisplayOrders() throws Exception {
        MTENekoVendingMachineV2 machine = machine();
        NekoTradeGroup group = group();
        UUID player = UUID.randomUUID();
        try {
            NekoTrade trade = group.getTrades()
                .get(0);
            NekoTradeItemDisplay old = NekoTradeItemDisplay.fromNekoTrade(trade, group, 0);
            String oldToken = old.getDefinitionFingerprint();
            trade.getToItems()
                .clear();
            trade.getToItems()
                .add(new NekoBigItemStack(new ItemStack(Items.gold_ingot)));
            check(
                oldToken.equals(old.getDefinitionFingerprint())
                    && !NekoTradeDefinition.matches(group.getId(), 0, oldToken),
                "old clickable display keeps its captured definition after live registry mutation");
            NekoVMGuiV2 gui = new NekoVMGuiV2(machine);
            Method receive = NekoVMGuiV2.class.getDeclaredMethod("processTradeRequest", String.class, UUID.class);
            receive.setAccessible(true);
            machine.inputItems.setStackInSlot(0, new ItemStack(Items.iron_ingot, 2));
            receive.invoke(gui, group.getId() + ":0:" + oldToken, player);
            receive.invoke(gui, group.getId() + ":0", player);
            receive.invoke(gui, group.getId() + ":0:" + new String(new char[200]).replace('\0', 'a'), player);
            check(
                machine.inputItems.getStackInSlot(0).stackSize == 2 && machine.getOutputBufferSize() == 0,
                "production receiver rejects stale, legacy and oversized requests before payment/output");
            NekoTradeItemDisplay fresh = NekoTradeItemDisplay.fromNekoTrade(trade, group, 0);
            receive.invoke(gui, group.getId() + ":0:" + fresh.getDefinitionFingerprint(), player);
            check(
                machine.inputItems.getStackInSlot(0).stackSize == 1 && machine.getOutputBufferSize() == 1,
                "a freshly rebuilt display executes the new definition normally");
        } finally {
            NekoTradeDatabase.INSTANCE.removeTradeGroup(group.getId());
        }
    }

    private static void canonicalDefinition() {
        NekoTradeGroup group = group();
        try {
            NekoTrade trade = group.getTrades()
                .get(0);
            ItemStack left = new ItemStack(Items.diamond);
            NBTTagCompound a = new NBTTagCompound(), b = new NBTTagCompound();
            a.setInteger("Aa", 1);
            a.setInteger("BB", 2);
            b.setInteger("BB", 2);
            b.setInteger("Aa", 1);
            a.setByteArray("data", new byte[] { 1, 2 });
            b.setByteArray("data", new byte[] { 1, 2 });
            left.setTagCompound(a);
            trade.getToItems()
                .set(0, new NekoBigItemStack(left));
            String expected = NekoTradeDefinition.fingerprint(group, trade);
            ItemStack right = left.copy();
            right.setTagCompound(b);
            trade.getToItems()
                .set(0, new NekoBigItemStack(right));
            check(
                expected.equals(NekoTradeDefinition.fingerprint(group, trade)),
                "equal compound NBT with colliding key hashes has a stable canonical fingerprint");
            b.setByteArray("data", new byte[] { 1, 3 });
            trade.getToItems()
                .set(0, new NekoBigItemStack(right));
            check(
                !expected.equals(NekoTradeDefinition.fingerprint(group, trade)),
                "array payload changes invalidate old orders");
            expected = NekoTradeDefinition.fingerprint(group, trade);
            group.setCooldown(-1);
            check(
                !expected.equals(NekoTradeDefinition.fingerprint(group, trade)),
                "group redemption policy changes invalidate old orders");
        } finally {
            NekoTradeDatabase.INSTANCE.removeTradeGroup(group.getId());
        }
    }

    private static Field field(Class<?> type, String name) throws Exception {
        while (type != null) {
            try {
                Field result = type.getDeclaredField(name);
                result.setAccessible(true);
                return result;
            } catch (NoSuchFieldException missing) {
                type = type.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }

    private static final class Transfer extends ModularContainer {

        private final List<ModularSlot> targets;

        Transfer(List<ModularSlot> targets) {
            this.targets = targets;
        }

        @Override
        public List<ModularSlot> getShiftClickSlots() {
            return targets;
        }

        ItemStack move(ModularSlot source, ItemStack stack) {
            return transferItem(source, stack);
        }
    }
}

package com.miaokatze.gtit.trade.v2;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

/** Physical output and ME compensation must obey the same durable 64-item slot bound. */
public final class NekoTradePhysicalOutputTest {

    private static final java.util.UUID TEST_PLAYER = java.util.UUID.randomUUID();

    private NekoTradePhysicalOutputTest() {}

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static void run() {
        int previous = Items.iron_ingot.getItemStackLimit();
        try {
            Items.iron_ingot.setMaxStackSize(256);
            roundTripSplitStacks();
            capacityBeforePayment();
            compensationBound();
            standardStackLimits();
            Items.iron_ingot.setMaxStackSize(0);
            invalidLimitBeforePayment();
        } finally {
            Items.iron_ingot.setMaxStackSize(previous);
        }
        System.out.println(
            "NekoTradePhysicalOutputTest: PASS (durable split, capacity, compensation, ordinary limits, invalid limit)");
    }

    private static NekoBigItemStack output(int count) {
        ItemStack stack = new ItemStack(Items.iron_ingot, count);
        stack.setTagCompound(new NBTTagCompound());
        stack.getTagCompound()
            .setString("identity", "paid-output");
        return new NekoBigItemStack(stack);
    }

    private static void roundTripSplitStacks() {
        NekoBigItemStack paid = output(128);
        int restoredCount = 0;
        List<ItemStack> split = paid.getCombinedStacks();
        for (ItemStack stack : split) {
            NBTTagCompound saved = new NBTTagCompound();
            stack.writeToNBT(saved);
            ItemStack restored = ItemStack.loadItemStackFromNBT(saved);
            if (restored != null && restored.stackSize > 0) restoredCount += restored.stackSize;
            check(ItemStack.areItemStackTagsEqual(paid.getBaseStack(), restored), "exact NBT survives physical save");
        }
        check(restoredCount == 128, "all 128 paid items survive vanilla Count-byte persistence");
        check(split.size() == 2, "physical output uses two 64-item slots even for a larger item limit");
    }

    private static void standardStackLimits() {
        for (int limit : new int[] { 1, 16, 64 }) {
            Items.iron_ingot.setMaxStackSize(limit);
            List<ItemStack> split = output(65).getCombinedStacks();
            check(split.size() == (65 + limit - 1) / limit, "ordinary item limit remains respected");
            int count = 0;
            for (ItemStack stack : split) {
                check(stack.stackSize > 0 && stack.stackSize <= limit, "ordinary physical stack is valid");
                count += stack.stackSize;
            }
            check(count == 65, "ordinary output is conserved");
        }
    }

    private static final class Input implements NekoTradeExecutor.InputSlotAccessor {

        ItemStack[] slots = { new ItemStack(Items.gold_ingot) };
        int writes;

        public ItemStack[] getCopyOfInputs() {
            return new ItemStack[] { slots[0] == null ? null : slots[0].copy() };
        }

        public void setInputs(ItemStack[] inputs) {
            slots = inputs;
            writes++;
        }
    }

    private static final class Output implements NekoTradeExecutor.OutputSlotAccessor {

        final int capacity;
        final List<ItemStack> items = new ArrayList<>();

        Output(int capacity) {
            this.capacity = capacity;
        }

        public int getAvailableSlotCount() {
            return capacity - items.size();
        }

        public boolean hasSpaceFor(ItemStack stack) {
            return getAvailableSlotCount() > 0;
        }

        public void insertItem(ItemStack stack) {
            items.add(stack.copy());
        }

        public void rollback(int count) {
            while (count-- > 0) items.remove(items.size() - 1);
        }
    }

    private static NekoTradeGroup group(int count) {
        NekoTrade trade = new NekoTrade();
        trade.getFromItems()
            .add(new NekoBigItemStack(new ItemStack(Items.gold_ingot)));
        trade.getToItems()
            .add(output(count));
        NekoTradeGroup group = new NekoTradeGroup();
        group.getTrades()
            .add(trade);
        NekoTradeDatabase.INSTANCE.addTradeGroup(group);
        return group;
    }

    private static void capacityBeforePayment() {
        NekoTradeGroup group = group(128);
        try {
            Input input = new Input();
            Output full = new Output(1);
            check(
                !NekoTradeExecutor.outputsFit(Collections.singletonList(output(128)), 1),
                "rolled output needs two slots");
            check(
                NekoTradeExecutor.INSTANCE.checkTrade(TEST_PLAYER, group.getId(), 0, input, full)
                    .getStatus() == NekoTradeResult.Status.OUTPUT_FULL,
                "preflight reports physical capacity before payment");
            check(
                !NekoTradeExecutor.INSTANCE.executeTrade(TEST_PLAYER, group.getId(), 0, input, full)
                    .isSuccess(),
                "one physical slot cannot accept 128 items");
            check(
                input.writes == 0 && input.slots[0].stackSize == 1 && full.items.isEmpty(),
                "failed physical capacity check is read-only");
            Output enough = new Output(2);
            check(
                NekoTradeExecutor.INSTANCE.executeTrade(TEST_PLAYER, group.getId(), 0, input, enough)
                    .isSuccess(),
                "two physical slots can accept the trade");
            check(
                input.slots[0] == null && enough.items.size() == 2
                    && enough.items.get(0).stackSize == 64
                    && enough.items.get(1).stackSize == 64,
                "exact payment yields all items in legal stacks");
        } finally {
            NekoTradeDatabase.INSTANCE.removeTradeGroup(group.getId());
        }
    }

    private static void compensationBound() {
        NekoTradeMatcher.Supply supply = new NekoTradeMatcher.Supply(new ItemStack(Items.iron_ingot), -1, null);
        check(
            !NekoTradeExecutor.meCompensationFits(
                new NekoTradeMatcher.Plan(Collections.singletonList(supply), new int[] { 4096 * 64 + 1 })),
            "ME refund bound counts actual physical stacks, not oversized item stacks");
        check(
            NekoTradeExecutor.meCompensationFits(
                new NekoTradeMatcher.Plan(Collections.singletonList(supply), new int[] { 4096 * 64 })),
            "exact compensation boundary remains valid");
    }

    private static void invalidLimitBeforePayment() {
        NekoTradeGroup group = group(1);
        try {
            Input input = new Input();
            Output output = new Output(2);
            check(
                !NekoTradeExecutor.INSTANCE.checkTrade(TEST_PLAYER, group.getId(), 0, input, output)
                    .isSuccess(),
                "invalid mod item stack limit rejects cleanly instead of dividing by zero");
            check(
                !NekoTradeExecutor.INSTANCE.executeTrade(TEST_PLAYER, group.getId(), 0, input, output)
                    .isSuccess(),
                "invalid output cannot charge input");
            check(input.writes == 0 && input.slots[0].stackSize == 1, "invalid limit leaves input intact");
        } finally {
            NekoTradeDatabase.INSTANCE.removeTradeGroup(group.getId());
        }
    }
}

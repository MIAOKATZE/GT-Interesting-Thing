package com.miaokatze.gtit.lottery;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import com.miaokatze.gtit.util.NbtBase64Util;

/** Validated prize templates and a conservative slot bound, prepared before payment or RNG/pity changes. */
public final class LotteryDeliveryPlan {

    private final Map<LotteryEntry, ItemStack> templates = new IdentityHashMap<>();
    private int reservedSlots;

    public static LotteryDeliveryPlan prepare(List<LotteryEntry> entries, int draws) {
        if (entries == null || draws <= 0 || draws > 10) return null;
        LotteryDeliveryPlan plan = new LotteryDeliveryPlan();
        int largest = 0;
        for (LotteryEntry entry : entries) {
            if (entry == null || !entry.isDrawable()) continue;
            if (entry.getMinAmount() < 1 || entry.getMaxAmount() < entry.getMinAmount() || entry.getRarity() == null)
                return null;
            String encodedNbt = entry.getNbtBase64();
            NBTTagCompound expectedNbt = null;
            if (encodedNbt != null && !encodedNbt.isEmpty()) {
                expectedNbt = NbtBase64Util.nbtFromBase64(encodedNbt);
                if (expectedNbt == null) return null;
            }
            int maximum = entry.getMaxAmount();
            ItemStack stack = entry.toItemStack(maximum);
            // Invalid item/currency registration is a pre-payment failure, never a wallet/backpack fallback.
            if (stack == null || stack.stackSize <= 0) return null;
            // toItemStack historically ignores failed NBT decoding. Never pay for a substituted plain prize.
            if (expectedNbt != null && !expectedNbt.equals(stack.getTagCompound())) return null;
            int limit = Math.max(1, Math.min(64, stack.getMaxStackSize()));
            long slots = ((long) maximum + limit - 1) / limit;
            if (slots * draws > 100) return null;
            largest = Math.max(largest, (int) slots);
            plan.templates.put(entry, stack.copy());
        }
        if (largest == 0) return null;
        plan.reservedSlots = largest * draws;
        return plan;
    }

    public int reservedSlots() {
        return reservedSlots;
    }

    public ItemStack prize(LotteryDrawResult result) {
        if (result == null) throw new IllegalArgumentException("missing draw result");
        ItemStack template = templates.get(result.getEntry());
        if (template == null || result.getAmount() <= 0 || result.getAmount() > template.stackSize)
            throw new IllegalArgumentException("result outside validated prize bound");
        ItemStack stack = template.copy();
        stack.stackSize = result.getAmount();
        return stack;
    }

    /** Final owned stack copies are made before charging; commit must not query mod stack limits again. */
    public List<ItemStack> splitPrize(LotteryDrawResult result) {
        ItemStack prize = prize(result);
        int limit = Math.max(1, Math.min(64, prize.getMaxStackSize()));
        List<ItemStack> stacks = new ArrayList<>();
        int remaining = prize.stackSize;
        while (remaining > 0) {
            ItemStack stack = prize.copy();
            stack.stackSize = Math.min(remaining, limit);
            stacks.add(stack);
            remaining -= stack.stackSize;
        }
        return stacks;
    }
}

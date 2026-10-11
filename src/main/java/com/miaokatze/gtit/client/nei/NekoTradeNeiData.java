package com.miaokatze.gtit.client.nei;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.OreDictionary;

import com.miaokatze.gtit.trade.v2.NekoBigItemStack;
import com.miaokatze.gtit.trade.v2.NekoTrade;
import com.miaokatze.gtit.trade.v2.NekoTradeDatabase;
import com.miaokatze.gtit.trade.v2.NekoTradeGroup;
import com.miaokatze.gtit.trade.v2.NekoTradeMatcher;

/** Reads the synchronized database; never reads local JSON or the VM recipe cache. */
public final class NekoTradeNeiData {

    private static long version = Long.MIN_VALUE;
    private static List<NekoTradeGroup> groups = Collections.emptyList();

    private NekoTradeNeiData() {}

    public static synchronized List<NekoTradeGroup> groups() {
        long current = NekoTradeDatabase.INSTANCE.getVersion();
        if (version != current) {
            List<NekoTradeGroup> snapshot = new ArrayList<>(
                NekoTradeDatabase.INSTANCE.getAllTradeGroups()
                    .values());
            snapshot.removeIf(
                group -> group.getTrades()
                    .isEmpty());
            snapshot.sort(
                Comparator.comparingInt(NekoTradeGroup::getTabId)
                    .thenComparingInt(NekoTradeGroup::getOrderId)
                    .thenComparing(
                        group -> group.getId()
                            .toString()));
            groups = Collections.unmodifiableList(snapshot);
            version = current;
        }
        return groups;
    }

    public static boolean matches(NekoTrade trade, ItemStack query, boolean output) {
        for (NekoBigItemStack slot : output ? trade.getToItems() : trade.getFromItems()) {
            if (matchesSlot(slot, query, output, trade.isRecordNBT())) return true;
        }
        return false;
    }

    private static boolean matchesSlot(NekoBigItemStack slot, ItemStack query, boolean output, boolean recordNBT) {
        if (output) {
            List<NekoBigItemStack> candidates = NekoTradeMatcher.outputCandidates(slot);
            if (candidates != null) for (NekoBigItemStack candidate : candidates) {
                if (candidate.matches(query, true)) return true;
            }
        } else {
            for (NekoBigItemStack option : slot.getOptions()) {
                if (option.matches(query, recordNBT)) return true;
            }
        }
        return false;
    }

    /** Copy candidates: neither NEI permutation generation nor amount overlays may mutate the ore registry. */
    public static List<ItemStack> displayStacks(NekoBigItemStack slot, boolean output) {
        List<ItemStack> stacks = new ArrayList<>();
        if (output) {
            List<NekoBigItemStack> candidates = NekoTradeMatcher.outputCandidates(slot);
            if (candidates != null) for (NekoBigItemStack candidate : candidates) {
                add(stacks, candidate.getBaseStack(), candidate.getStackSize());
            }
        } else {
            for (NekoBigItemStack option : slot.getOptions()) {
                if (option.hasOreDict()) {
                    for (ItemStack stack : OreDictionary.getOres(option.getOreDict())) {
                        if (stack != null && stack.getItem() != null) add(stacks, stack, option.getStackSize());
                    }
                } else {
                    add(stacks, option.getBaseStack(), option.getStackSize());
                }
            }
        }
        // A missing ore registration still has an inspectable placeholder, but query matching stays fail-closed.
        if (stacks.isEmpty()) add(stacks, slot.getBaseStack(), slot.getStackSize());
        return stacks;
    }

    private static void add(List<ItemStack> stacks, ItemStack base, int amount) {
        if (base == null || base.getItem() == null) return;
        ItemStack copy = base.copy();
        copy.stackSize = amount;
        stacks.add(copy);
    }

    /** First page containing the actual matching slot; reusable without loading NEI or LWJGL. */
    public static int queryPage(NekoTradeGroup group, List<NekoTradePages.Page> pages, ItemStack query,
        boolean output) {
        if (query == null) return 0;
        for (int index = 0; index < pages.size(); index++) {
            NekoTradePages.Page page = pages.get(index);
            NekoTrade trade = group.getTrades()
                .get(page.tradeIndex);
            List<NekoBigItemStack> side = output ? trade.getToItems() : trade.getFromItems();
            for (int slot = page.slotOffset; slot
                < Math.min(side.size(), page.slotOffset + NekoTradePages.SLOTS_PER_SIDE); slot++) {
                if (matchesSlot(side.get(slot), query, output, trade.isRecordNBT())) return index;
            }
        }
        return 0;
    }
}

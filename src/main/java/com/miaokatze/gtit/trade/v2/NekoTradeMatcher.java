// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtit.trade.v2;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.OreDictionary;

import com.miaokatze.gtit.currency.NekoCurrencyRegistrar;
import com.miaokatze.gtit.trade.NekoWallet;

/** Select one complete option per slot, then solve shared stock allocation as a flow network. */
public final class NekoTradeMatcher {

    private NekoTradeMatcher() {}

    public static final class Supply {

        public final ItemStack stack;
        public final int localIndex;
        public final String currency;

        public Supply(ItemStack stack, int localIndex, String currency) {
            this.stack = stack.copy();
            this.localIndex = localIndex;
            this.currency = currency;
        }
    }

    public static final class Plan {

        public final List<Supply> supplies;
        public final int[] consumed;

        Plan(List<Supply> supplies, int[] consumed) {
            this.supplies = supplies;
            this.consumed = consumed;
        }
    }

    public static final class Check {

        public final Plan plan;
        public final String failure;

        Check(Plan plan, String failure) {
            this.plan = plan;
            this.failure = failure;
        }
    }

    public static Plan plan(NekoTrade trade, NekoWallet wallet, NekoTradeExecutor.InputSlotAccessor input) {
        return check(trade, wallet, input).plan;
    }

    /** One authoritative snapshot supplies both allocation and the explanation of a failed allocation. */
    public static Check check(NekoTrade trade, NekoWallet wallet, NekoTradeExecutor.InputSlotAccessor input) {
        List<Supply> supplies = new ArrayList<>();
        List<NekoBigItemStack> required = new ArrayList<>(trade.getFromItems());
        // Legacy currency fields are only a defensive fallback; normal registry loading migrates these.
        if (trade.getLegacyCurrencyId() != null && trade.getLegacyCurrencyCost() > 0) {
            ItemStack coin = NekoCurrencyRegistrar
                .getItemStack(trade.getLegacyCurrencyId(), trade.getLegacyCurrencyCost());
            if (coin == null) return new Check(null, "交易所需货币未注册");
            required.add(new NekoBigItemStack(coin));
        }
        java.util.Set<String> currencies = new java.util.LinkedHashSet<>();
        for (NekoBigItemStack slot : required) for (NekoBigItemStack option : slot.getOptions()) {
            String id = option.hasOreDict() ? null : NekoCurrencyRegistrar.getNekoCurrencyId(option.getBaseStack());
            if (id != null) currencies.add(id);
        }
        if (wallet != null) for (String id : currencies) {
            int count = wallet.getCount(id);
            ItemStack coin = NekoCurrencyRegistrar.getItemStack(id, count);
            if (coin != null && count > 0) supplies.add(new Supply(coin, -1, id));
        }
        ItemStack[] local = input.getCopyOfInputs();
        for (int i = 0; i < local.length; i++)
            if (local[i] != null && local[i].stackSize > 0) supplies.add(new Supply(local[i], i, null));
        // Retain same-item NBT variants for diagnostics; the flow still applies the exact configured rule.
        for (ItemStack stack : input.getMEItems(required, false))
            if (stack != null && stack.stackSize > 0) supplies.add(new Supply(stack, -1, null));
        for (NekoBigItemStack slot : required) if (slot.getOptions()
            .size() > 16) return new Check(null, "交易的备选材料过多");
        supplies.removeIf(supply -> {
            for (NekoBigItemStack slot : required)
                for (NekoBigItemStack option : slot.getOptions()) if (option.matches(supply.stack, false)) return false;
            return true;
        });
        List<NekoBigItemStack> fixed = new ArrayList<>();
        List<NekoBigItemStack> branching = new ArrayList<>();
        List<Supply> matchingSupplies = new ArrayList<>(supplies);
        matchingSupplies.removeIf(supply -> {
            for (NekoBigItemStack slot : required) for (NekoBigItemStack option : slot.getOptions())
                if (option.matches(supply.stack, trade.isRecordNBT())) return false;
            return true;
        });
        for (NekoBigItemStack slot : required) {
            if (slot.getStackSize() <= 0) return new Check(null, "交易所需材料数量无效");
            if (slot.getAlternatives()
                .isEmpty()) fixed.add(slot);
            else branching.add(slot);
        }
        // Legacy exact trades have no branching search and retain their unrestricted slot count.
        if (branching.size() > 16) return new Check(null, "交易的备选材料过多");
        long[] budget = { 10000, 2000000 };
        Plan plan = branching.isEmpty() ? flow(fixed, matchingSupplies, trade.isRecordNBT())
            : choose(branching, fixed, 0, matchingSupplies, trade.isRecordNBT(), budget);
        if (plan != null) return new Check(plan, null);
        if (budget[0] < 0 || budget[1] < 0) return new Check(null, "备选材料组合过多，请简化交易配置");
        Deficit best = new Deficit();
        diagnose(branching, fixed, 0, matchingSupplies, trade.isRecordNBT(), budget, best);
        if (best.chosen == null) return new Check(null, "无法确认所需材料，请检查交易配置");
        StringBuilder message = new StringBuilder(branching.isEmpty() ? "缺少：" : "可选方案还缺少：");
        for (int i = 0; i < best.missing.length; i++) {
            if (best.missing[i] <= 0) continue;
            NekoBigItemStack option = best.chosen.get(i);
            if (message.charAt(message.length() - 1) != '：') message.append("；");
            String currency = option.hasOreDict() ? null
                : NekoCurrencyRegistrar.getNekoCurrencyId(option.getBaseStack());
            message
                .append(
                    option.hasOreDict() ? "矿词 [" + option.getOreDict() + "]"
                        : currency != null ? NekoCurrencyRegistrar.getDisplayName(currency)
                            : option.getBaseStack()
                                .getDisplayName())
                .append(" × ")
                .append(best.missing[i]);
            if (trade.isRecordNBT() && !option.hasOreDict()) {
                for (Supply supply : supplies) {
                    if (option.matches(supply.stack, false) && !option.matches(supply.stack, true)) {
                        message.append("（已有同类物品的附加数据 NBT 不符）");
                        break;
                    }
                }
            }
            if (message.length() > 768) {
                message.append("；更多需求请查看交易清单");
                break;
            }
        }
        return new Check(null, message.toString());
    }

    private static final class Deficit {

        List<NekoBigItemStack> chosen;
        int[] missing;
        long total = Long.MAX_VALUE;
    }

    /** A bounded complete-choice search: report a supplement that really completes one allocation. */
    private static void diagnose(List<NekoBigItemStack> branching, List<NekoBigItemStack> chosen, int index,
        List<Supply> supplies, boolean strict, long[] budget, Deficit best) {
        if (--budget[0] < 0) return;
        budget[1] -= (long) Math.max(1, chosen.size()) * Math.max(1, supplies.size());
        if (budget[1] < 0) return;
        if (index < branching.size()) {
            for (NekoBigItemStack option : branching.get(index)
                .getOptions()) {
                if (budget[0] < 0 || budget[1] < 0) break;
                if (option.getStackSize() <= 0) continue;
                chosen.add(option);
                diagnose(branching, chosen, index + 1, supplies, strict, budget, best);
                chosen.remove(chosen.size() - 1);
            }
            return;
        }
        int[] missing = new int[chosen.size()];
        flow(chosen, supplies, strict, missing);
        long total = 0;
        for (int count : missing) total += count;
        if (total < best.total) {
            best.total = total;
            best.chosen = new ArrayList<>(chosen);
            best.missing = missing;
        }
    }

    private static Plan choose(List<NekoBigItemStack> required, List<NekoBigItemStack> chosen, int index,
        List<Supply> supplies, boolean strict, long[] budget) {
        if (--budget[0] < 0) return null;
        budget[1] -= (long) Math.max(1, chosen.size()) * Math.max(1, supplies.size());
        if (budget[1] < 0) return null;
        Plan partial = flow(chosen, supplies, strict);
        if (partial == null) return null;
        if (index == required.size()) return partial;
        for (NekoBigItemStack option : required.get(index)
            .getOptions()) {
            if (option.getStackSize() <= 0) continue;
            chosen.add(option);
            Plan result = choose(required, chosen, index + 1, supplies, strict, budget);
            chosen.remove(chosen.size() - 1);
            if (result != null) return result;
        }
        return null;
    }

    private static final class Edge {

        final int to;
        final int reverse;
        long capacity;

        Edge(int to, int reverse, long capacity) {
            this.to = to;
            this.reverse = reverse;
            this.capacity = capacity;
        }
    }

    private static void connect(List<List<Edge>> graph, int from, int to, long capacity) {
        Edge forward = new Edge(
            to,
            graph.get(to)
                .size(),
            capacity);
        Edge reverse = new Edge(
            from,
            graph.get(from)
                .size(),
            0);
        graph.get(from)
            .add(forward);
        graph.get(to)
            .add(reverse);
    }

    private static Plan flow(List<NekoBigItemStack> chosen, List<Supply> supplies, boolean strict) {
        return flow(chosen, supplies, strict, null);
    }

    private static Plan flow(List<NekoBigItemStack> chosen, List<Supply> supplies, boolean strict, int[] missing) {
        int n = chosen.size(), m = supplies.size(), sink = n + m + 1;
        List<List<Edge>> graph = new ArrayList<>();
        for (int i = 0; i <= sink; i++) graph.add(new ArrayList<>());
        long total = 0;
        for (int i = 0; i < n; i++) {
            NekoBigItemStack option = chosen.get(i);
            connect(graph, 0, 1 + i, option.getStackSize());
            total += option.getStackSize();
            for (int j = 0; j < m; j++) {
                Supply supply = supplies.get(j);
                if (supply.currency != null && (option.hasOreDict()
                    || !supply.currency.equals(NekoCurrencyRegistrar.getNekoCurrencyId(option.getBaseStack()))))
                    continue;
                if (option.matches(supply.stack, strict)) connect(graph, 1 + i, 1 + n + j, option.getStackSize());
            }
        }
        for (int j = 0; j < m; j++) connect(graph, 1 + n + j, sink, supplies.get(j).stack.stackSize);
        long delivered = 0;
        while (delivered < total) {
            int[] parent = new int[sink + 1], edgeIndex = new int[sink + 1];
            java.util.Arrays.fill(parent, -1);
            parent[0] = 0;
            int[] queue = new int[sink + 1];
            int head = 0, tail = 1;
            while (head < tail && parent[sink] == -1) {
                int v = queue[head++];
                List<Edge> edges = graph.get(v);
                for (int i = 0; i < edges.size(); i++) {
                    Edge edge = edges.get(i);
                    if (parent[edge.to] == -1 && edge.capacity > 0) {
                        parent[edge.to] = v;
                        edgeIndex[edge.to] = i;
                        queue[tail++] = edge.to;
                    }
                }
            }
            if (parent[sink] == -1) {
                if (missing != null) for (int i = 0; i < n; i++) missing[i] = (int) graph.get(0)
                    .get(i).capacity;
                return null;
            }
            long amount = Long.MAX_VALUE;
            for (int v = sink; v != 0; v = parent[v]) amount = Math.min(
                amount,
                graph.get(parent[v])
                    .get(edgeIndex[v]).capacity);
            for (int v = sink; v != 0; v = parent[v]) {
                Edge edge = graph.get(parent[v])
                    .get(edgeIndex[v]);
                edge.capacity -= amount;
                graph.get(v)
                    .get(edge.reverse).capacity += amount;
            }
            delivered += amount;
        }
        int[] consumed = new int[m];
        for (int j = 0; j < m; j++) consumed[j] = (int) graph.get(sink)
            .get(j).capacity;
        return new Plan(supplies, consumed);
    }

    /** Empty ore registrations fail closed; the display icon never becomes an output candidate. */
    public static List<NekoBigItemStack> outputCandidates(NekoBigItemStack slot) {
        List<NekoBigItemStack> result = new ArrayList<>();
        for (NekoBigItemStack option : slot.getOptions()) {
            if (option.getStackSize() <= 0) return null;
            if (!option.hasOreDict()) {
                result.add(new NekoBigItemStack(option.getStackSize(), "", option.getBaseStack()));
                continue;
            }
            List<ItemStack> candidates = OreDictionary.getOres(option.getOreDict());
            int previous = result.size();
            for (ItemStack stack : candidates) {
                if (stack == null || stack.getItem() == null) continue;
                stack = stack.copy();
                if (stack.getItemDamage() == OreDictionary.WILDCARD_VALUE) stack.setItemDamage(0);
                if (!option.matches(stack, false)) continue;
                NekoBigItemStack concrete = new NekoBigItemStack(option.getStackSize(), "", stack);
                boolean duplicate = false;
                for (int i = previous; i < result.size(); i++) if (result.get(i)
                    .matches(stack, true)) {
                        duplicate = true;
                        break;
                    }
                if (!duplicate) result.add(concrete);
            }
            if (result.size() == previous) return null;
        }
        return result;
    }

    public static List<NekoBigItemStack> rollOutputs(NekoTrade trade, Random random) {
        List<NekoBigItemStack> result = new ArrayList<>();
        for (NekoBigItemStack slot : trade.getToItems()) {
            // Uniform choice of configured option, then uniform choice of its concrete ore items.
            List<NekoBigItemStack> options = slot.getOptions();
            for (NekoBigItemStack option : options) if (outputCandidates(optionOnly(option)) == null) return null;
            NekoBigItemStack chosen = options.get(random.nextInt(options.size()));
            List<NekoBigItemStack> candidates = outputCandidates(optionOnly(chosen));
            result.add(candidates.get(random.nextInt(candidates.size())));
        }
        return result;
    }

    private static NekoBigItemStack optionOnly(NekoBigItemStack option) {
        return new NekoBigItemStack(option.getStackSize(), option.getOreDict(), option.getBaseStack());
    }
}

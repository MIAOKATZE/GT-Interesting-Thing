package com.miaokatze.gtit.trade.v2;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.oredict.OreDictionary;

import com.google.gson.Gson;
import com.miaokatze.gtit.trade.NekoTradeEntry.ItemEntry;

public final class NekoTradeMatchingTest {

    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    private static NekoBigItemStack iron(int count) {
        return new NekoBigItemStack(new ItemStack(Items.iron_ingot, count));
    }

    private static NekoBigItemStack gold(int count) {
        return new NekoBigItemStack(new ItemStack(Items.gold_ingot, count));
    }

    private static class Input implements NekoTradeExecutor.InputSlotAccessor {

        ItemStack[] slots;
        List<ItemStack> me = new ArrayList<>();

        Input(ItemStack... slots) {
            this.slots = slots;
        }

        public ItemStack[] getCopyOfInputs() {
            ItemStack[] result = new ItemStack[slots.length];
            for (int i = 0; i < result.length; i++) result[i] = slots[i] == null ? null : slots[i].copy();
            return result;
        }

        public void setInputs(ItemStack[] inputs) {
            slots = inputs;
        }

        public List<ItemStack> getMEItems() {
            return me;
        }
    }

    private static final class Output implements NekoTradeExecutor.OutputSlotAccessor {

        List<ItemStack> items = new ArrayList<>();
        int checks;
        int failAfter = Integer.MAX_VALUE;

        public boolean hasSpaceFor(ItemStack stack) {
            return ++checks <= failAfter;
        }

        public int getAvailableSlotCount() {
            return 16;
        }

        public void insertItem(ItemStack stack) {
            items.add(stack.copy());
        }

        public void rollback(int count) {
            while (count-- > 0) items.remove(items.size() - 1);
        }
    }

    private static void executeAndCompensate() {
        NekoTrade trade = new NekoTrade();
        trade.getFromItems()
            .add(iron(2));
        trade.getFromItems()
            .add(gold(3));
        trade.getToItems()
            .add(new NekoBigItemStack(new ItemStack(Items.diamond)));
        NekoTradeGroup group = new NekoTradeGroup();
        group.getTrades()
            .add(trade);
        NekoTradeDatabase.INSTANCE.addTradeGroup(group);
        final int[] extracted = { 0 }, refunded = { 0 };
        Input partial = new Input() {

            public ItemStack extractExactFromME(ItemStack request) {
                ItemStack result = request.copy();
                if (request.getItem() == Items.gold_ingot) result.stackSize = 1;
                extracted[0] += result.stackSize;
                return result;
            }

            public void refundME(ItemStack stack) {
                refunded[0] += stack.stackSize;
            }
        };
        partial.me.add(new ItemStack(Items.iron_ingot, 2));
        partial.me.add(new ItemStack(Items.gold_ingot, 3));
        Output output = new Output();
        check(
            !NekoTradeExecutor.INSTANCE.executeTrade(null, group.getId(), 0, partial, output)
                .isSuccess(),
            "partial ME extraction fails transaction");
        check(
            extracted[0] == 3 && refunded[0] == 3 && output.items.isEmpty(),
            "every exact and partial ME extraction is compensated");
        Input full = new Input() {

            public ItemStack extractExactFromME(ItemStack request) {
                extracted[0] += request.stackSize;
                return request.copy();
            }

            public void refundME(ItemStack stack) {
                refunded[0] += stack.stackSize;
            }
        };
        full.me.addAll(partial.me);
        extracted[0] = 0;
        refunded[0] = 0;
        Output failedOutput = new Output();
        failedOutput.failAfter = 1;
        check(
            !NekoTradeExecutor.INSTANCE.executeTrade(null, group.getId(), 0, full, failedOutput)
                .isSuccess(),
            "output changes after preflight fail transaction");
        check(extracted[0] == 5 && refunded[0] == 5 && failedOutput.items.isEmpty(), "output failure refunds ME");
        Input success = new Input(new ItemStack(Items.iron_ingot, 2), new ItemStack(Items.gold_ingot, 3));
        Output delivered = new Output();
        check(
            NekoTradeExecutor.INSTANCE.executeTrade(null, group.getId(), 0, success, delivered)
                .isSuccess(),
            "local trade succeeds");
        check(
            success.slots[0] == null && success.slots[1] == null && delivered.items.size() == 1,
            "actual consumption and actual output agree");
        NekoTradeDatabase.INSTANCE.removeTradeGroup(group.getId());
    }

    private static void parserAndWallet() {
        com.google.gson.Gson gson = new com.google.gson.Gson();
        NekoBigItemStack slot = new NekoBigItemStack(2, "gtitTestMetal", new ItemStack(Items.diamond));
        slot.getAlternatives()
            .add(gold(3));
        com.google.gson.JsonArray valid = new com.google.gson.JsonArray();
        valid.add(new com.google.gson.JsonParser().parse(gson.toJson(slot.toItemEntry(true))));
        List<ItemEntry> parsed = EditActionsCommon.parseTradeItemEntries(valid);
        check(
            parsed.get(0)
                .getOreDict()
                .equals("gtitTestMetal")
                && parsed.get(0)
                    .getAlternatives()
                    .get(0)
                    .getAmount() == 3,
            "actual editor parser preserves options");
        for (String quantity : new String[] { "0", "-1", "1.5", "2147483648" }) {
            com.google.gson.JsonArray invalid = new com.google.gson.JsonParser().parse(gson.toJson(valid))
                .getAsJsonArray();
            invalid.get(0)
                .getAsJsonObject()
                .addProperty("amount", quantity);
            try {
                EditActionsCommon.parseTradeItemEntries(invalid);
                throw new AssertionError("invalid quantity accepted");
            } catch (IllegalArgumentException expected) {}
        }
        com.google.gson.JsonArray nested = new com.google.gson.JsonParser().parse(gson.toJson(valid))
            .getAsJsonArray();
        nested.get(0)
            .getAsJsonObject()
            .getAsJsonArray("alternatives")
            .get(0)
            .getAsJsonObject()
            .add("alternatives", valid);
        try {
            EditActionsCommon.parseTradeItemEntries(nested);
            throw new AssertionError("nested options accepted");
        } catch (IllegalArgumentException expected) {}
        com.miaokatze.gtit.currency.NekoCurrencyRegistrar.nekoCoinItem = Items.emerald;
        com.miaokatze.gtit.trade.NekoWallet wallet = new com.miaokatze.gtit.trade.NekoWallet();
        wallet.addCount("neko", 4);
        NekoTrade trade = new NekoTrade();
        NekoBigItemStack choice = new NekoBigItemStack(new ItemStack(Items.emerald, 3));
        choice.getAlternatives()
            .add(iron(2));
        trade.getFromItems()
            .add(choice);
        trade.getFromItems()
            .add(new NekoBigItemStack(new ItemStack(Items.emerald, 3)));
        NekoTradeMatcher.Plan plan = NekoTradeMatcher
            .plan(trade, wallet, new Input(new ItemStack(Items.iron_ingot, 2)));
        check(plan != null, "OR must select physical alternative to leave shared wallet credits for exact coin slot");
        int walletUsed = 0;
        for (int i = 0; i < plan.supplies.size(); i++)
            if (plan.supplies.get(i).currency != null) walletUsed += plan.consumed[i];
        check(walletUsed == 3 && wallet.getCount("neko") == 4, "wallet capacity is shared and planning is read only");
        OreDictionary.registerOre("gtitTestCoinOre", new ItemStack(Items.emerald));
        trade.getFromItems()
            .clear();
        trade.getFromItems()
            .add(new NekoBigItemStack(1, "gtitTestCoinOre", new ItemStack(Items.emerald)));
        check(NekoTradeMatcher.plan(trade, wallet, new Input()) == null, "ore coin icon never consumes wallet credits");
        check(
            NekoTradeMatcher.plan(trade, wallet, new Input(new ItemStack(Items.emerald))) != null,
            "ore coin requires actual physical candidate");
    }

    public static void main(String[] args) throws Exception {
        if (!(NekoTradeMatchingTest.class.getClassLoader() instanceof net.minecraft.launchwrapper.LaunchClassLoader)) {
            String[] paths = System.getProperty("java.class.path")
                .split(java.io.File.pathSeparator);
            java.net.URL[] urls = new java.net.URL[paths.length];
            for (int i = 0; i < paths.length; i++) urls[i] = new java.io.File(paths[i]).toURI()
                .toURL();
            net.minecraft.launchwrapper.LaunchClassLoader loader = new net.minecraft.launchwrapper.LaunchClassLoader(
                urls);
            net.minecraft.launchwrapper.Launch.classLoader = loader;
            net.minecraft.launchwrapper.Launch.minecraftHome = new java.io.File(".");
            Thread.currentThread()
                .setContextClassLoader(loader);
            Class<?> injection = loader.loadClass("cpw.mods.fml.relauncher.FMLInjectionData");
            java.lang.reflect.Method build = injection
                .getDeclaredMethod("build", java.io.File.class, net.minecraft.launchwrapper.LaunchClassLoader.class);
            build.setAccessible(true);
            build.invoke(null, new java.io.File("."), loader);
            Class<?> fmlLoader = loader.loadClass("cpw.mods.fml.common.Loader");
            Object[] data = (Object[]) injection.getMethod("data")
                .invoke(null);
            fmlLoader.getMethod("injectData", Object[].class)
                .invoke(null, new Object[] { data });
            loader.loadClass(NekoTradeMatchingTest.class.getName())
                .getMethod("main", String[].class)
                .invoke(null, new Object[] { args });
            return;
        }
        java.lang.reflect.Field side = cpw.mods.fml.relauncher.FMLRelaunchLog.class.getDeclaredField("side");
        side.setAccessible(true);
        side.set(null, cpw.mods.fml.relauncher.Side.SERVER);
        java.lang.reflect.Field home = cpw.mods.fml.relauncher.FMLRelaunchLog.class.getDeclaredField("minecraftHome");
        home.setAccessible(true);
        home.set(null, new java.io.File("."));
        Bootstrap.func_151354_b();
        OreDictionary.registerOre("gtitTestMetal", new ItemStack(Items.iron_ingot));
        OreDictionary.registerOre("gtitTestMetal", new ItemStack(Items.gold_ingot));
        NekoTrade trade = new NekoTrade();
        NekoBigItemStack broad = new NekoBigItemStack(1, "gtitTestMetal", new ItemStack(Items.diamond));
        trade.getFromItems()
            .add(broad);
        trade.getFromItems()
            .add(iron(1));
        Input input = new Input(new ItemStack(Items.iron_ingot), new ItemStack(Items.gold_ingot));
        check(NekoTradeMatcher.plan(trade, null, input) != null, "overlap must reroute broad requirement");
        check(input.slots[0].stackSize == 1 && input.slots[1].stackSize == 1, "plan must be read only");
        NekoTrade legacyLarge = new NekoTrade();
        for (int i = 0; i < 17; i++) legacyLarge.getFromItems()
            .add(iron(1));
        check(
            NekoTradeMatcher.plan(legacyLarge, null, new Input(new ItemStack(Items.iron_ingot, 17))) != null,
            "legacy fixed trades retain more than sixteen requirements");
        check(
            NekoTradeMatcher.plan(trade, null, new Input(new ItemStack(Items.iron_ingot))) == null,
            "shared stock cannot be counted twice");
        trade.getFromItems()
            .clear();
        NekoBigItemStack choice = iron(2);
        choice.getAlternatives()
            .add(gold(3));
        trade.getFromItems()
            .add(choice);
        check(
            NekoTradeMatcher
                .plan(trade, null, new Input(new ItemStack(Items.iron_ingot, 1), new ItemStack(Items.gold_ingot, 2)))
                == null,
            "alternatives cannot mix partial quantities");
        check(
            NekoTradeMatcher.plan(trade, null, new Input(new ItemStack(Items.gold_ingot, 3))) != null,
            "complete secondary option uses its own quantity");
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("quality", "a");
        choice.getBaseStack()
            .setTagCompound(tag);
        trade.setRecordNBT(true);
        check(
            NekoTradeMatcher.plan(trade, null, new Input(new ItemStack(Items.iron_ingot, 2))) == null,
            "strict NBT needs actual candidate tag");
        trade.setRecordNBT(false);
        check(
            NekoTradeMatcher.plan(trade, null, new Input(new ItemStack(Items.iron_ingot, 2))) != null,
            "relaxed NBT accepts different tag");
        Input me = new Input();
        me.me.add(new ItemStack(Items.gold_ingot, 3));
        for (int i = 0; i < 5000; i++) me.me.add(new ItemStack(Items.diamond));
        NekoTradeMatcher.Plan mePlan = NekoTradeMatcher.plan(trade, null, me);
        check(mePlan != null && mePlan.supplies.size() == 1, "irrelevant ME stock is filtered");
        NekoTrade hugeME = new NekoTrade();
        hugeME.getFromItems()
            .add(iron(Integer.MAX_VALUE));
        Input hugeStock = new Input();
        hugeStock.me.add(new ItemStack(Items.iron_ingot, Integer.MAX_VALUE));
        check(
            !NekoTradeExecutor.meCompensationFits(NekoTradeMatcher.plan(hugeME, null, hugeStock)),
            "huge ME deduction is rejected before it can require millions of refund stacks");
        check(
            NekoTradeExecutor.meCompensationFits(
                NekoTradeMatcher.plan(hugeME, null, new Input(new ItemStack(Items.iron_ingot, Integer.MAX_VALUE)))),
            "large purely local deduction does not require ME compensation storage");
        NekoBigItemStack restored = NekoBigItemStack.loadFromNBT(choice.writeToNBT());
        check(
            restored.getAlternatives()
                .size() == 1
                && restored.getAlternatives()
                    .get(0)
                    .getStackSize() == 3,
            "NBT alternatives round trip");
        check(ItemStack.areItemStackTagsEqual(choice.getBaseStack(), restored.getBaseStack()), "NBT tags round trip");
        broad.getAlternatives()
            .add(gold(3));
        ItemEntry entry = broad.toItemEntry(true);
        Gson gson = new Gson();
        ItemEntry json = gson.fromJson(gson.toJson(entry), ItemEntry.class);
        NekoBigItemStack fromJson = NekoBigItemStack.fromItemEntry(json);
        check(
            fromJson != null && fromJson.getOreDict()
                .equals("gtitTestMetal")
                && fromJson.getAlternatives()
                    .get(0)
                    .getStackSize() == 3,
            "JSON semantics round trip");
        ItemEntry missingIcon = new ItemEntry("missing:item", 0, 4);
        missingIcon.setOreDict("gtitTestMetal");
        check(NekoBigItemStack.fromItemEntry(missingIcon) != null, "ore semantics survive missing icon");
        NekoTrade mixedOre = new NekoTrade();
        mixedOre.getFromItems()
            .add(new NekoBigItemStack(3, "gtitTestMetal", new ItemStack(Items.diamond)));
        check(
            NekoTradeMatcher
                .plan(mixedOre, null, new Input(new ItemStack(Items.iron_ingot, 1), new ItemStack(Items.gold_ingot, 2)))
                != null,
            "one ore option can combine concrete stacks");
        trade.setRecordNBT(true);
        Input strictME = new Input();
        strictME.me.add(new ItemStack(Items.iron_ingot, 2));
        check(NekoTradeMatcher.plan(trade, null, strictME) == null, "ME strict NBT uses actual stack tag");
        strictME.me.get(0)
            .setTagCompound((NBTTagCompound) tag.copy());
        check(NekoTradeMatcher.plan(trade, null, strictME) != null, "ME exact NBT is accepted");
        NekoTrade output = new NekoTrade();
        output.getToItems()
            .add(choice);
        Random random = new Random(123);
        boolean sawIron = false, sawGold = false;
        for (int i = 0; i < 50; i++) {
            NekoBigItemStack rolled = NekoTradeMatcher.rollOutputs(output, random)
                .get(0);
            if (rolled.getBaseStack()
                .getItem() == Items.iron_ingot) {
                sawIron = true;
                check(rolled.getStackSize() == 2, "iron amount");
            } else {
                sawGold = true;
                check(rolled.getStackSize() == 3, "gold amount");
            }
        }
        check(sawIron && sawGold, "both outputs rolled");
        check(
            !NekoTradeExecutor.outputsFit(java.util.Collections.singletonList(iron(Integer.MAX_VALUE)), 16),
            "huge rolled output fails before stack expansion");
        check(
            !NekoTradeExecutor
                .outputsFit(java.util.Collections.singletonList(iron(Integer.MAX_VALUE)), Integer.MAX_VALUE),
            "default unbounded accessor still cannot allocate millions of outputs");
        check(
            NekoTradeExecutor.outputsFit(java.util.Collections.singletonList(iron(1)), 1),
            "small rolled alternative uses its actual capacity");
        output.getToItems()
            .clear();
        output.getToItems()
            .add(broad);
        List<NekoBigItemStack> candidates = NekoTradeMatcher.outputCandidates(broad);
        check(
            candidates.get(0)
                .getBaseStack()
                .getItem() != Items.diamond,
            "ore icon is never a candidate");
        output.getToItems()
            .clear();
        output.getToItems()
            .add(new NekoBigItemStack(5, "gtitTestNoSuchOre", new ItemStack(Items.diamond)));
        check(NekoTradeMatcher.rollOutputs(output, random) == null, "empty ore rejects before charging");
        OreDictionary.registerOre("gtitTestWildcard", new ItemStack(Items.coal, 1, OreDictionary.WILDCARD_VALUE));
        output.getToItems()
            .clear();
        output.getToItems()
            .add(new NekoBigItemStack(5, "gtitTestWildcard", new ItemStack(Items.diamond)));
        NekoBigItemStack rolled = NekoTradeMatcher.rollOutputs(output, random)
            .get(0);
        check(
            rolled.getBaseStack()
                .getItemDamage() == 0 && rolled.getStackSize() == 5,
            "wildcard outputs concrete meta zero");
        executeAndCompensate();
        parserAndWallet();
        com.miaokatze.gtit.trade.NekoTradeJson.validateDepth("{\"trades\":[{\"alternatives\":[{\"amount\":1}]}]}");
        StringBuilder deep = new StringBuilder();
        for (int i = 0; i < 10000; i++) deep.append("{\"alternatives\":[");
        deep.append("{}");
        for (int i = 0; i < 10000; i++) deep.append("]}");
        boolean depthRejected = false;
        try {
            com.miaokatze.gtit.trade.NekoTradeJson.validateDepth(deep.toString());
        } catch (com.google.gson.JsonParseException expected) {
            depthRejected = true;
        }
        check(depthRejected, "deep JSON is rejected iteratively before Gson recursion");
        NBTTagCompound nested = iron(1).writeToNBT();
        net.minecraft.nbt.NBTTagList nestedOptions = new net.minecraft.nbt.NBTTagList();
        nestedOptions.appendTag(choice.writeToNBT());
        nested.setTag("Alternatives", nestedOptions);
        check(NekoBigItemStack.loadFromNBT(nested) == null, "nested NBT alternatives fail closed before recursion");
        System.out.println(
            "NekoTradeMatchingTest: PASS (overlap, OR quantities, NBT, JSON, ME, random, empty and wildcard ore)");
    }
}

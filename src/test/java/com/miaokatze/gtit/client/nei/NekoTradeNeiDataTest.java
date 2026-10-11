package com.miaokatze.gtit.client.nei;

import java.util.List;

import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.oredict.OreDictionary;

import com.miaokatze.gtit.trade.v2.NekoBigItemStack;
import com.miaokatze.gtit.trade.v2.NekoTrade;
import com.miaokatze.gtit.trade.v2.NekoTradeDatabase;
import com.miaokatze.gtit.trade.v2.NekoTradeGroup;

/** Headless model checks: intentionally never references a NEI handler, Minecraft client or LWJGL. */
public final class NekoTradeNeiDataTest {

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    private static NekoBigItemStack item(ItemStack stack) {
        return new NekoBigItemStack(stack);
    }

    public static void run() {
        ItemStack tagged = new ItemStack(Items.iron_ingot);
        tagged.stackTagCompound = new NBTTagCompound();
        tagged.stackTagCompound.setString("nei_test", "exact");
        NekoTrade nbt = new NekoTrade();
        nbt.getFromItems()
            .add(item(tagged));
        check(NekoTradeNeiData.matches(nbt, new ItemStack(Items.iron_ingot), false), "fuzzy inputs ignore NBT");
        nbt.setRecordNBT(true);
        check(!NekoTradeNeiData.matches(nbt, new ItemStack(Items.iron_ingot), false), "strict inputs preserve NBT");
        check(NekoTradeNeiData.matches(nbt, tagged, false), "strict matching NBT is searchable");

        OreDictionary.registerOre("gtitNeiRegressionMetal", new ItemStack(Items.iron_ingot));
        OreDictionary.registerOre("gtitNeiRegressionMetal", new ItemStack(Items.gold_ingot));
        NekoBigItemStack choices = new NekoBigItemStack(7, "gtitNeiRegressionMetal", new ItemStack(Items.paper));
        choices.getAlternatives()
            .add(item(new ItemStack(Items.diamond, 9)));
        NekoTrade options = new NekoTrade();
        options.getFromItems()
            .add(choices);
        options.getToItems()
            .add(choices.copy());
        check(NekoTradeNeiData.matches(options, new ItemStack(Items.gold_ingot), false), "ore input is searchable");
        check(NekoTradeNeiData.matches(options, new ItemStack(Items.diamond), false), "OR input is searchable");
        check(!NekoTradeNeiData.matches(options, new ItemStack(Items.paper), false), "display icon is not an input");
        for (ItemStack query : new ItemStack[] { new ItemStack(Items.iron_ingot), new ItemStack(Items.gold_ingot),
            new ItemStack(Items.diamond) }) {
            check(NekoTradeNeiData.matches(options, query, true), "every actual output candidate is searchable");
        }
        check(!NekoTradeNeiData.matches(options, new ItemStack(Items.paper), true), "display icon is not an output");
        List<ItemStack> display = NekoTradeNeiData.displayStacks(choices, false);
        check(display.size() == 3, "ore and OR candidates share one display slot");
        check(display.get(0).stackSize == 7 && display.get(2).stackSize == 9, "per-option counts retained");
        display.get(0).stackSize = 123;
        check(
            OreDictionary.getOres("gtitNeiRegressionMetal")
                .get(0).stackSize == 1,
            "display permutation never mutates ore registry");
        check(choices.getBaseStack().stackSize == 1, "display does not mutate base stack");
        check(
            NekoTradeNeiData.displayStacks(choices, true)
                .size() == 3,
            "all output permutations are displayed");

        NekoBigItemStack absent = new NekoBigItemStack(1, "gtitNeiRegressionAbsent", new ItemStack(Items.paper));
        NekoTrade invalid = new NekoTrade();
        invalid.getToItems()
            .add(absent);
        check(
            !NekoTradeNeiData.matches(invalid, new ItemStack(Items.paper), true),
            "missing ore fails output search closed");

        NekoTradeGroup group = new NekoTradeGroup();
        group.getTrades()
            .add(nbt);
        NekoTrade oversized = new NekoTrade();
        for (int slot = 0; slot < 13; slot++) oversized.getToItems()
            .add(item(new ItemStack(Items.gold_ingot)));
        oversized.getToItems()
            .add(item(new ItemStack(Items.emerald)));
        group.getTrades()
            .add(oversized);
        List<NekoTradePages.Page> pages = NekoTradePages.create(new int[] { 1, 0 }, new int[] { 0, 14 });
        check(
            NekoTradeNeiData.queryPage(group, pages, new ItemStack(Items.emerald), true) == 2,
            "output search opens matching exchange overflow page");
        check(
            NekoTradeNeiData.queryPage(group, pages, tagged, false) == 0,
            "input search opens matching exchange page");

        NekoTradeDatabase.INSTANCE.addTradeGroup(group);
        check(
            NekoTradeNeiData.groups()
                .contains(group),
            "database addition invalidates cache");
        NekoTradeDatabase.INSTANCE.removeTradeGroup(group.getId());
        check(
            !NekoTradeNeiData.groups()
                .contains(group),
            "database removal invalidates cache");
    }

    public static void main(String[] args) {
        Bootstrap.func_151354_b();
        run();
        System.out.println("NekoTradeNeiDataTest passed");
    }
}

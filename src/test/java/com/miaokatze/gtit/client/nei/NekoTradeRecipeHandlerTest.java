package com.miaokatze.gtit.client.nei;

import java.util.ArrayList;
import java.util.Collections;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import com.miaokatze.gtit.testutil.MinecraftTestBootstrap;
import com.miaokatze.gtit.trade.v2.NekoBigItemStack;
import com.miaokatze.gtit.trade.v2.NekoTrade;
import com.miaokatze.gtit.trade.v2.NekoTradeDatabase;
import com.miaokatze.gtit.trade.v2.NekoTradeGroup;

import codechicken.nei.recipe.FurnaceRecipeHandler;
import codechicken.nei.recipe.RecipeCatalysts;

/** Exercises NEI's production handler query entry points without opening a game or rendering a GUI. */
public final class NekoTradeRecipeHandlerTest {

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    private static NekoTradeGroup group(ItemStack input, ItemStack output) {
        NekoTrade trade = new NekoTrade();
        trade.getFromItems()
            .add(new NekoBigItemStack(input));
        trade.getToItems()
            .add(new NekoBigItemStack(output));
        NekoTradeGroup group = new NekoTradeGroup();
        group.getTrades()
            .add(trade);
        NekoTradeDatabase.INSTANCE.addTradeGroup(group);
        return group;
    }

    public static void main(String[] args) throws Exception {
        if (!MinecraftTestBootstrap.enter(NekoTradeRecipeHandlerTest.class, args)) return;
        // TemplateRecipeHandler.newInstance checks the global furnace cache, unrelated to trade queries.
        // A native vanilla fuel fixture avoids the client's global item scan in this headless JVM.
        ArrayList<FurnaceRecipeHandler.FuelPair> fuels = FurnaceRecipeHandler.afuels;
        FurnaceRecipeHandler.afuels = new ArrayList<>();
        FurnaceRecipeHandler.afuels.add(new FurnaceRecipeHandler.FuelPair(new ItemStack(Items.coal), 1600));
        NekoTradeGroup first = group(new ItemStack(Items.iron_ingot), new ItemStack(Items.gold_ingot));
        NekoTradeGroup second = group(new ItemStack(Items.diamond), new ItemStack(Items.emerald));
        try {
            NekoTradeRecipeHandler registered = new NekoTradeRecipeHandler();
            check(
                registered.getAllRecipeHandler()
                    .numRecipes() == 2,
                "NEI all-recipes query exposes both groups");
            check(
                registered.getRecipeHandler("item", new ItemStack(Items.gold_ingot))
                    .numRecipes() == 1,
                "NEI crafting item query filters outputs");
            check(
                registered.getUsageHandler("item", new ItemStack(Items.iron_ingot))
                    .numRecipes() == 1,
                "NEI usage item query filters inputs");
            ItemStack inventoryInput = new ItemStack(Items.iron_ingot);
            inventoryInput.stackTagCompound = new NBTTagCompound();
            inventoryInput.stackTagCompound.setString("inventory_data", "extra");
            check(
                registered.getUsageHandler("item", inventoryInput)
                    .numRecipes() == 1,
                "fuzzy input lookup accepts inventory NBT");
            first.getTrades()
                .get(0)
                .setRecordNBT(true);
            check(
                registered.getUsageHandler("item", inventoryInput)
                    .numRecipes() == 0,
                "strict input lookup still rejects extra inventory NBT");
            first.getTrades()
                .get(0)
                .setRecordNBT(false);
            ItemStack inventoryOutput = new ItemStack(Items.gold_ingot);
            inventoryOutput.stackTagCompound = (NBTTagCompound) inventoryInput.stackTagCompound.copy();
            check(
                registered.getRecipeHandler("item", inventoryOutput)
                    .numRecipes() == 0,
                "exact output lookup retains output NBT semantics");
            check(
                registered.getRecipeHandler("item", new ItemStack(Items.paper))
                    .numRecipes() == 0,
                "unrelated crafting item query stays empty");
            check(
                registered.getUsageAndCatalystHandler("item", new ItemStack(Items.diamond))
                    .numRecipes() == 1,
                "non-catalyst usage query preserves input filtering");

            // Install a real NEI catalyst fixture; no GregTech machine registration is needed in this JVM.
            RecipeCatalysts
                .putRecipeCatalysts(NekoTradeRecipeHandler.ID, Collections.singletonList(new ItemStack(Items.feather)));
            check(
                RecipeCatalysts.containsCatalyst(registered, new ItemStack(Items.feather)),
                "native NEI catalyst registry recognizes fixture");
            check(
                registered.getUsageAndCatalystHandler("item", new ItemStack(Items.feather))
                    .numRecipes() == 2,
                "catalyst U opens every group rather than filtering trade inputs");
            check(
                registered.getUsageHandler("item", new ItemStack(Items.feather))
                    .numRecipes() == 2,
                "plain usage route also opens catalyst groups with Ctrl or catalyst style disabled");
            check(registered.numRecipes() == 0, "queries do not mutate registered prototype");
            NekoTradeNeiDataTest.run();
            NekoTradePagesTest.main(new String[0]);
        } finally {
            FurnaceRecipeHandler.afuels = fuels;
            NekoTradeDatabase.INSTANCE.removeTradeGroup(first.getId());
            NekoTradeDatabase.INSTANCE.removeTradeGroup(second.getId());
            RecipeCatalysts.putRecipeCatalysts(NekoTradeRecipeHandler.ID, Collections.<ItemStack>emptyList());
        }
        System.out.println("NekoTradeRecipeHandlerTest passed");
    }
}

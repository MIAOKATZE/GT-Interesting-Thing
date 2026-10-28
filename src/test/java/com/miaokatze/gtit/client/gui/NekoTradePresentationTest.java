package com.miaokatze.gtit.client.gui;

import java.util.UUID;

import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import com.miaokatze.gtit.currency.NekoCurrencyRegistrar;
import com.miaokatze.gtit.testutil.MinecraftTestBootstrap;
import com.miaokatze.gtit.trade.v2.NekoBigItemStack;
import com.miaokatze.gtit.trade.v2.NekoTrade;
import com.miaokatze.gtit.trade.v2.NekoTradeDefinition;
import com.miaokatze.gtit.trade.v2.NekoTradeGroup;

/** Display regression: wallet payment is rendered, never converted back into a physical input requirement. */
public final class NekoTradePresentationTest {

    public static void main(String[] args) throws Exception {
        if (!MinecraftTestBootstrap.enter(NekoTradePresentationTest.class, args)) return;
        Item oldOrdinary = NekoCurrencyRegistrar.nekoCoinItem;
        Item oldShiny = NekoCurrencyRegistrar.shimmeringNekoCoinItem;
        try {
            NekoCurrencyRegistrar.nekoCoinItem = Items.emerald;
            NekoCurrencyRegistrar.shimmeringNekoCoinItem = Items.diamond;
            NekoTrade trade = new NekoTrade();
            trade.getFromItems()
                .add(new NekoBigItemStack(new ItemStack(Items.emerald, 12)));
            trade.getToItems()
                .add(new NekoBigItemStack(new ItemStack(Items.iron_ingot, 2)));
            NekoTradeGroup group = new NekoTradeGroup(UUID.randomUUID());
            NekoTradeItemDisplay ordinary = NekoTradeItemDisplay.fromNekoTrade(trade, group, 0);
            check(
                !ordinary.hasInputs() && ordinary.getDisplayedRequirementCount() == 1,
                "coin-only is not no requirement");
            check(
                ordinary.getCurrencyCosts()
                    .get(NekoCurrencyRegistrar.NEKO_ID) == 12L,
                "ordinary amount is exact");
            check(ordinary.satisfiesSearch("猫猫币"), "displayed coin requirement is searchable");
            check(
                trade.getFromItems()
                    .size() == 1,
                "display never mutates physical definition");

            trade.getFromItems()
                .add(new NekoBigItemStack(new ItemStack(Items.diamond, 3)));
            trade.getFromItems()
                .add(new NekoBigItemStack(new ItemStack(Items.emerald, 5)));
            NekoBigItemStack iron = new NekoBigItemStack(new ItemStack(Items.iron_ingot, 8));
            iron.getAlternatives()
                .add(new NekoBigItemStack(new ItemStack(Items.gold_ingot, 4)));
            trade.getFromItems()
                .add(iron);
            trade.setRecordNBT(true);
            NekoTradeItemDisplay mixed = NekoTradeItemDisplay.fromNekoTrade(trade, group, 2);
            check(mixed.isRecordNBT() && !ordinary.isRecordNBT(), "receipt retains the actual NBT matching snapshot");
            check(
                mixed.getCurrencyCosts()
                    .size() == 2,
                "both wallet currencies visible");
            check(
                mixed.getCurrencyCosts()
                    .get(NekoCurrencyRegistrar.NEKO_ID) == 17L,
                "same currency aggregates");
            check(
                mixed.getCurrencyCosts()
                    .get(NekoCurrencyRegistrar.SHIMMERING_NEKO_ID) == 3L,
                "shiny amount retained");
            check(mixed.getDisplayedRequirementCount() == 3, "two coin tokens plus one item choice");
            check(
                mixed.getInputs()
                    .size() == 1
                    && mixed.getInputs()
                        .get(0) == iron,
                "candidate input identity unchanged");
            check(
                mixed.getGroupId()
                    .equals(group.getId()) && mixed.getTradeIndex() == 2,
                "request identity unchanged");
            check(
                mixed.getDefinitionFingerprint()
                    .equals(NekoTradeDefinition.fingerprint(group, trade)),
                "stale request guard retained");
            trade.setRecordNBT(false);
            check(mixed.isRecordNBT(), "changing the definition does not mutate an existing receipt");
            try {
                mixed.getCurrencyCosts()
                    .put("neko", 0L);
                throw new AssertionError("wallet display snapshot must be read-only");
            } catch (UnsupportedOperationException expected) {}
            check(
                ordinary.getCurrencyCosts()
                    .size() == 1,
                "older display remains a snapshot");
            for (long amount : new long[] { 1, 999, 1000, 999499, 999950, Integer.MAX_VALUE, 3_000_000_000L,
                Long.MAX_VALUE }) {
                String label = NekoTradeItemDisplayWidget.compactQuantity(amount);
                check(
                    !label.startsWith("-") && label.length() <= 5,
                    "long compact amount fits a coin token: " + amount);
            }
            mixed.setBqLocked(true);
            check(
                NekoTradeItemDisplayWidget.nativeStatus(mixed)
                    .startsWith("LOCKED"),
                "BQ lock is explicit");
            mixed.setBqLocked(false);
            mixed.setCooldownRemaining(28800);
            check(
                NekoTradeItemDisplayWidget.nativeStatus(mixed)
                    .contains("8h 0m"),
                "cooldown remains seconds, not ticks");
            mixed.setCooldownRemaining(0);
            mixed.setTradeable(false);
            check(
                NekoTradeItemDisplayWidget.nativeStatus(mixed)
                    .equals("条件未满足"),
                "missing requirement is explicit");
            System.out
                .println("NekoTradePresentationTest: ordinary/shiny/mixed costs, snapshot identity and status passed");
        } finally {
            NekoCurrencyRegistrar.nekoCoinItem = oldOrdinary;
            NekoCurrencyRegistrar.shimmeringNekoCoinItem = oldShiny;
        }
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}

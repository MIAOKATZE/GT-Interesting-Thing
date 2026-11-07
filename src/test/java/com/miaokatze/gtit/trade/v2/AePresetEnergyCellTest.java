package com.miaokatze.gtit.trade.v2;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.miaokatze.gtit.testutil.NativeAeTestBootstrap;
import com.miaokatze.gtit.trade.NekoTradeEntry;

import appeng.block.AEBaseItemBlockChargeable;
import appeng.block.networking.BlockEnergyCell;

/** Bundled AE requirements accept native energy NBT while explicitly strict trades remain strict. */
public final class AePresetEnergyCellTest {

    public static void main(String[] args) throws Exception {
        if (!NativeAeTestBootstrap.enter(AePresetEnergyCellTest.class, args)) return;
        BlockEnergyCell block = new BlockEnergyCell();
        java.lang.reflect.Field controllerField = cpw.mods.fml.common.Loader.class.getDeclaredField("modController");
        controllerField.setAccessible(true);
        Object controller = controllerField.get(cpw.mods.fml.common.Loader.instance());
        java.lang.reflect.Field owner = cpw.mods.fml.common.LoadController.class.getDeclaredField("activeContainer");
        owner.setAccessible(true);
        Object previousOwner = owner.get(controller);
        try {
            owner.set(
                controller,
                cpw.mods.fml.common.Loader.instance()
                    .getIndexedModList()
                    .get("appliedenergistics2"));
            cpw.mods.fml.common.registry.GameRegistry
                .registerBlock(block, AEBaseItemBlockChargeable.class, "tile.BlockEnergyCell");
        } finally {
            owner.set(controller, previousOwner);
        }
        AEBaseItemBlockChargeable item = (AEBaseItemBlockChargeable) Item.getItemFromBlock(block);
        for (String id : new String[] { "gtit-ae-basic", "gtit-ae-advanced" }) {
            NekoTradeEntry entry = energyUpgrade(id);
            NekoTradeEntry.ItemEntry input = entry.getFromItems()
                .get(0);
            check(input.getMeta() == 0 && input.getAmount() == 2, "native energy cell requirement retains meta/count");
            ItemStack plain = new ItemStack(item, input.getAmount(), input.getMeta());
            NekoBigItemStack required = NekoBigItemStack.fromItemEntry(input);
            check(
                required != null && required.getBaseStack()
                    .getItem() == item,
                "resource resolves to actual registered AE energy cell");
            ItemStack charged = plain.copy();
            item.injectAEPower(charged, 500);
            check(
                charged.hasTagCompound() && item.getAECurrentPower(charged) == 500,
                "native charging adds energy NBT");
            check(!required.matches(charged, true), "old strict-null requirement rejects native charged cell");
            check(required.matches(charged, entry.isRecordNBT()), "bundled upgrade accepts charged native cell");
            check(!hasPlan(required, true, charged), "old strict upgrade has no allocation for a charged cell");
            check(hasPlan(required, entry.isRecordNBT(), charged), "bundled charged input has a complete allocation");
            ItemStack drained = charged.copy();
            item.extractAEPower(drained, 500);
            check(
                drained.hasTagCompound() && item.getAECurrentPower(drained) == 0,
                "native empty cell retains energy NBT");
            check(!required.matches(drained, true), "old strict-null requirement also rejects emptied cell");
            check(required.matches(drained, entry.isRecordNBT()), "bundled upgrade accepts emptied native cell");
            check(hasPlan(required, entry.isRecordNBT(), drained), "bundled drained input has a complete allocation");
            check(required.matches(plain, entry.isRecordNBT()), "fresh cell remains accepted");

            NekoBigItemStack intentionallyCharged = new NekoBigItemStack(charged);
            check(intentionallyCharged.matches(charged.copy(), true), "explicit exact NBT still matches");
            check(
                !intentionallyCharged.matches(drained, true),
                "explicit exact NBT still distinguishes remaining energy");
            check(!intentionallyCharged.matches(plain, true), "explicit exact NBT still rejects absent tags");
            check(
                hasPlan(intentionallyCharged, true, charged) && !hasPlan(intentionallyCharged, true, drained),
                "intentional strict-NBT allocation still respects the exact energy tag");
            check(!required.matches(new ItemStack(item, 2, 1), entry.isRecordNBT()), "item metadata remains exact");
        }
        System.out.println(
            "AePresetEnergyCellTest: both AE presets, native charged/drained cells and strict NBT isolation passed");
    }

    private static boolean hasPlan(NekoBigItemStack requirement, boolean strict, ItemStack supplied) {
        NekoTrade trade = new NekoTrade();
        trade.setRecordNBT(strict);
        trade.getFromItems()
            .add(requirement);
        NekoTradeExecutor.InputSlotAccessor inventory = new NekoTradeExecutor.InputSlotAccessor() {

            @Override
            public ItemStack[] getCopyOfInputs() {
                return new ItemStack[] { supplied.copy() };
            }

            @Override
            public void setInputs(ItemStack[] slots) {
                throw new AssertionError("Matching must not consume inputs");
            }
        };
        return NekoTradeMatcher.plan(trade, null, inventory) != null;
    }

    private static NekoTradeEntry energyUpgrade(String id) throws Exception {
        try (InputStreamReader reader = new InputStreamReader(
            AePresetEnergyCellTest.class.getResourceAsStream("/assets/gtit/trade_presets/" + id + ".json"),
            StandardCharsets.UTF_8)) {
            JsonObject preset = new JsonParser().parse(reader)
                .getAsJsonObject();
            NekoTradeEntry upgrade = null;
            int trades = 0;
            for (JsonElement tab : preset.getAsJsonArray("tabs")) {
                for (JsonElement value : tab.getAsJsonObject()
                    .getAsJsonArray("trades")) {
                    JsonObject trade = value.getAsJsonObject();
                    trades++;
                    // The shipped AE library has no explicit input NBT. Output NBT is independent
                    // of input matching and must never justify strict requirements.
                    for (JsonElement input : trade.getAsJsonArray("fromItems")) {
                        JsonElement nbt = input.getAsJsonObject()
                            .get("nbtBase64");
                        check(
                            nbt == null || nbt.isJsonNull()
                                || nbt.getAsString()
                                    .isEmpty(),
                            "review changed input NBT before applying this fixture");
                    }
                    check(
                        !trade.get("recordNBT")
                            .getAsBoolean(),
                        "plain bundled requirements do not demand absent NBT");
                    JsonArray outputs = trade.getAsJsonArray("toItems");
                    if (outputs.size() == 1 && "appliedenergistics2:tile.BlockDenseEnergyCell".equals(
                        outputs.get(0)
                            .getAsJsonObject()
                            .get("item")
                            .getAsString())) {
                        upgrade = new Gson().fromJson(trade, NekoTradeEntry.class);
                    }
                }
            }
            check(trades == ("gtit-ae-basic".equals(id) ? 98 : 194), "preset inventory remains unchanged");
            check(
                upgrade != null && upgrade.getFromItems()
                    .size() == 1,
                "energy upgrade retains one item requirement");
            check(
                "appliedenergistics2:tile.BlockEnergyCell".equals(
                    upgrade.getFromItems()
                        .get(0)
                        .getItem()),
                "correct native energy-cell registry ID");
            check(
                upgrade.getCurrency()
                    .getAmount() == 4,
                "upgrade keeps its four-coin price");
            return upgrade;
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}

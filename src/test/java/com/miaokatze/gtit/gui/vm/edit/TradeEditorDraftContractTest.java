package com.miaokatze.gtit.gui.vm.edit;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import com.miaokatze.gtit.testutil.MinecraftTestBootstrap;
import com.miaokatze.gtit.trade.v2.NekoBigItemStack;

/** Regression for all 32 local draft slots; the catalog adapter must not replace candidate/matcher semantics. */
public final class TradeEditorDraftContractTest {

    public static void main(String[] args) throws Exception {
        if (!MinecraftTestBootstrap.enter(TradeEditorDraftContractTest.class, args)) return;
        TradeEditor editor = new TradeEditor(null, () -> {}, () -> {});
        for (int index = 0; index < 32; index++) {
            ItemStack held = new ItemStack(Items.iron_ingot, index + 1);
            editor.putChoice(index, held);
            held.stackSize = 100;
            check(
                editor.choice(index)
                    .getStackSize() == index + 1,
                "copied stack count " + index);
            editor.toggleAppend(index);
            editor.putChoice(index, new ItemStack(Items.gold_ingot, 3));
            NekoBigItemStack draft = editor.choice(index);
            check(
                draft.getBaseStack()
                    .getItem() == Items.iron_ingot,
                "primary unchanged in append mode " + index);
            check(
                draft.getAlternatives()
                    .size() == 1,
                "one appended candidate " + index);
            for (int candidate = 1; candidate < 15; candidate++) {
                editor.putChoice(index, new ItemStack(Items.gold_ingot, candidate + 1));
            }
            check(
                draft.getAlternatives()
                    .size() == 15,
                "full 16-candidate contract " + index);
            editor.applyOre(index, "", 7);
            check(
                draft.getAlternatives()
                    .get(14)
                    .getStackSize() == 7,
                "count edits newest candidate " + index);
            editor.removeChoice(index, false);
            check(
                draft.getAlternatives()
                    .size() == 14 && editor.choice(index) == draft,
                "remove candidate not root " + index);
            editor.removeChoice(index, true);
            check(editor.choice(index) == null && !editor.isAppend(index), "clear slot and mode " + index);
            editor.putChoice(index, new ItemStack(Items.diamond));
            editor.putChoice(index, new ItemStack(Items.emerald));
            check(
                editor.choice(index)
                    .getBaseStack()
                    .getItem() == Items.emerald && editor.choice(index)
                        .getAlternatives()
                        .isEmpty(),
                "replace mode after clear " + index);
        }
        ItemStack catalog = new ItemStack(Items.diamond, Integer.MAX_VALUE);
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("catalog", "中文目录");
        catalog.setTagCompound(tag);
        editor.putChoice(0, catalog);
        editor.putChoice(31, catalog);
        check(
            editor.choice(0)
                .getStackSize() == Integer.MAX_VALUE
                && editor.choice(31)
                    .getStackSize() == Integer.MAX_VALUE,
            "repeated catalog configuration preserves int quantity");
        catalog.stackSize = 1;
        catalog.getTagCompound()
            .setString("catalog", "changed");
        check(
            editor.choice(0)
                .getStackSize() == Integer.MAX_VALUE
                && editor.choice(31)
                    .getStackSize() == Integer.MAX_VALUE,
            "configured slots do not share source count");
        check(
            "中文目录".equals(
                editor.choice(0)
                    .getBaseStack()
                    .getTagCompound()
                    .getString("catalog"))
                && "中文目录".equals(
                    editor.choice(31)
                        .getBaseStack()
                        .getTagCompound()
                        .getString("catalog")),
            "repeated configuration does not share source NBT");
        editor.putChoice(0, new ItemStack(Items.diamond));
        editor.putChoice(31, new ItemStack(Items.emerald));
        editor.clearTradeEditState();
        for (int index = 0; index < 32; index++) check(editor.choice(index) == null, "close reset " + index);
        System.out.println(
            "TradeEditorDraftContractTest: 32 slots, copied quantities, append/remove/ore count and clear passed");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}

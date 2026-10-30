package com.miaokatze.gtit.trade.v2;

import java.util.Collections;
import java.util.UUID;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import com.miaokatze.gtit.testutil.MinecraftTestBootstrap;
import com.miaokatze.gtit.trade.NekoTradeConfig;
import com.miaokatze.gtit.trade.NekoTradeEntry;

/** Exact config JSON -> runtime conversion must preserve valid edit tokens and reject stale definitions. */
public final class TradeEditPreconditionTest {

    public static void main(String[] args) throws Exception {
        if (!MinecraftTestBootstrap.enter(TradeEditPreconditionTest.class, args)) return;
        checkRoundTrip(false);
        checkRoundTrip(true);
        System.out.println(
            "TradeEditPreconditionTest: normal/default sync roundtrip, metadata, NBT and stale/missing tokens passed");
    }

    private static void checkRoundTrip(boolean defaults) {
        NekoTradeEntry entry = new NekoTradeEntry();
        entry.setId(
            defaults ? "MIAO77"
                : UUID.randomUUID()
                    .toString());
        entry.setDefaultEntry(defaults);
        entry.setTabId(3);
        entry.setOrderId(11);
        entry.setCooldown(30);
        entry.setRecordNBT(true);
        ItemStack output = new ItemStack(Items.diamond, 3);
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("label", "original");
        output.setTagCompound(tag);
        entry.setToItems(Collections.singletonList(NekoTradeEntry.ItemEntry.fromItemStack(output, true)));
        NekoTradeConfig.NekoTradeData data = new NekoTradeConfig.NekoTradeData();
        data.setTrades(Collections.singletonList(entry));
        NekoTradeRegistryV2.applySyncedTrades(data);
        UUID id = NekoTradeRegistryV2.parseTradeGroupId(entry.getId());
        String expected = NekoTradeDefinition.editFingerprint(NekoTradeDatabase.INSTANCE.getTradeGroup(id));
        check(expected.length() == 64, "registered original " + defaults);
        NekoTradeRegistryV2.applySyncedTrades(NekoTradeConfig.fromJson(NekoTradeConfig.toJson(data)));
        NekoTradeGroup copy = NekoTradeDatabase.INSTANCE.getTradeGroup(id);
        check(copy != null && copy.isDefaultEntry() == defaults, "source flag survives actual config sync");
        check(NekoTradeDefinition.matchesEdit(id, 0, expected), "valid copied draft accepted " + defaults);
        check(
            !NekoTradeDefinition.matchesEdit(id, -1, expected) && !NekoTradeDefinition.matchesEdit(id, 1, expected),
            "invalid index rejected");
        check(
            !NekoTradeDefinition.matchesEdit(id, 0, null) && !NekoTradeDefinition.matchesEdit(id, 0, "")
                && !NekoTradeDefinition.matchesEdit(id, 0, "not-a-fingerprint"),
            "old/malformed context rejected");
        copy.setOrderId(12);
        check(!NekoTradeDefinition.matchesEdit(id, 0, expected), "reordered entry rejected");
        copy.setOrderId(11);
        copy.setTabId(4);
        check(!NekoTradeDefinition.matchesEdit(id, 0, expected), "moved entry rejected");
        copy.setTabId(3);
        copy.setDefaultEntry(!defaults);
        check(!NekoTradeDefinition.matchesEdit(id, 0, expected), "changed source rejected");
        copy.setDefaultEntry(defaults);
        copy.getTrades()
            .get(0)
            .getToItems()
            .get(0)
            .getBaseStack()
            .getTagCompound()
            .setString("label", "changed");
        check(!NekoTradeDefinition.matchesEdit(id, 0, expected), "changed item NBT rejected");
        NekoTradeDatabase.INSTANCE.removeTradeGroup(id);
        check(!NekoTradeDefinition.matchesEdit(id, 0, expected), "deleted entry rejected");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}

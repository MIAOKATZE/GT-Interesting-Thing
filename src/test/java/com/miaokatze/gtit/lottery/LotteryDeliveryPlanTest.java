package com.miaokatze.gtit.lottery;

import java.util.Arrays;
import java.util.List;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import com.miaokatze.gtit.util.NbtBase64Util;

/** Headless coverage of pre-charge bounds; no player, network, world, wallet, or machine instance. */
public final class LotteryDeliveryPlanTest {

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static LotteryEntry item(String name, int min, int max) {
        return LotteryEntry.createItemPrize(name, "minecraft:" + name, 0, min, max, 1, LotteryRarity.COMMON);
    }

    public static void main(String[] args) throws Exception {
        if (!com.miaokatze.gtit.testutil.MinecraftTestBootstrap.enter(LotteryDeliveryPlanTest.class, args)) return;
        LotteryEntry iron = item("iron_ingot", 1, 65);
        LotteryDeliveryPlan plan = LotteryDeliveryPlan.prepare(Arrays.asList(iron), 10);
        check(plan != null && plan.reservedSlots() == 20, "ten draws reserve maximum split-stack result before RNG");
        ItemStack prize = plan.prize(new LotteryDrawResult(iron, false, false, 0, 3));
        check(prize.getItem() == Items.iron_ingot && prize.stackSize == 3, "actual output uses exact sampled quantity");
        prize.stackSize = 99;
        check(
            plan.prize(new LotteryDrawResult(iron, false, false, 0, 3)).stackSize == 3,
            "templates cannot be mutated by outputs");
        LotteryEntry sword = item("iron_sword", 1, 10);
        check(
            LotteryDeliveryPlan.prepare(Arrays.asList(sword), 10)
                .reservedSlots() == 100,
            "unstackable items reserve each real slot");
        check(
            LotteryDeliveryPlan.prepare(Arrays.asList(item("iron_sword", 1, 11)), 10) == null,
            "oversized worst-case reward refuses before charging");
        check(
            LotteryDeliveryPlan.prepare(Arrays.asList(item("iron_ingot", 1, Integer.MAX_VALUE)), 10) == null,
            "large configured quantity cannot overflow slot arithmetic");
        check(
            LotteryDeliveryPlan.prepare(Arrays.asList(item("missing_reward", 1, 1)), 1) == null,
            "missing registered reward rejected rather than lost after charging");
        check(
            LotteryDeliveryPlan.prepare(Arrays.asList(item("iron_ingot", -1, 2)), 1) == null,
            "malformed random range rejected before charging");
        check(
            LotteryDeliveryPlan.prepare(Arrays.asList(iron), 0) == null
                && LotteryDeliveryPlan.prepare(Arrays.asList(iron), 11) == null,
            "invalid draw counts rejected");
        LotteryEntry malformedNbt = item("iron_sword", 1, 1);
        malformedNbt.setNbtBase64("%%%");
        check(
            LotteryDeliveryPlan.prepare(Arrays.asList(malformedNbt), 1) == null,
            "malformed Base64 cannot silently turn an NBT prize into a plain item");
        malformedNbt.setNbtBase64("bm90LW5idA==");
        check(
            LotteryDeliveryPlan.prepare(Arrays.asList(malformedNbt), 1) == null,
            "valid Base64 with non-NBT content is also rejected before payment");
        NBTTagCompound exactTag = new NBTTagCompound();
        exactTag.setString("rewardOwner", "exact-tag");
        exactTag.setInteger("variant", 31);
        iron.setNbtBase64(NbtBase64Util.nbtToBase64(exactTag));
        LotteryDeliveryPlan tagged = LotteryDeliveryPlan.prepare(Arrays.asList(iron), 10);
        check(tagged != null, "valid configured NBT accepted");
        List<ItemStack> split = tagged.splitPrize(new LotteryDrawResult(iron, false, false, 0, 65));
        check(
            split.size() == 2 && split.get(0).stackSize == 64 && split.get(1).stackSize == 1,
            "final stack copies split once before charge");
        check(
            exactTag.equals(
                split.get(0)
                    .getTagCompound())
                && exactTag.equals(
                    split.get(1)
                        .getTagCompound()),
            "every prepared output preserves exact configured NBT");
        split.get(0)
            .getTagCompound()
            .setInteger("variant", -1);
        check(
            split.get(1)
                .getTagCompound()
                .getInteger("variant") == 31,
            "split output tags are independent owned copies");
        System.out.println("LotteryDeliveryPlanTest PASS");
    }
}

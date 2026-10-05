package com.miaokatze.gtit.lottery;

import java.util.LinkedHashMap;
import java.util.Map;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.miaokatze.gtit.testutil.SimpleAssert;
import com.miaokatze.gtit.testutil.TestRunner;

/** 真实编辑规则、抽取模型及离散布局的纯 JVM 回归。 */
public class LotterySlotsTest {

    public static void main(String[] args) {
        Map<String, Runnable> cases = new LinkedHashMap<>();
        cases.put("countContract", LotterySlotsTest::countContract);
        cases.put("resizePreservesPrizes", LotterySlotsTest::resizePreservesPrizes);
        cases.put("draftSurvivesValidation", LotterySlotsTest::draftSurvivesValidation);
        cases.put("hardPitySkipsEmptyAndLargeWeights", LotterySlotsTest::hardPitySkipsEmptyAndLargeWeights);
        cases.put("wheelSlotsNeverOverlap", LotterySlotsTest::wheelSlotsNeverOverlap);
        cases.put("animationNormalizesPreviousPool", LotterySlotsTest::animationNormalizesPreviousPool);
        cases.put("draftRoundTripAndManager", LotterySlotsTest::draftRoundTripAndManager);
        cases.put("softPitySkipsEmpty", LotterySlotsTest::softPitySkipsEmpty);
        TestRunner.run(LotterySlotsTest.class, cases);
        com.miaokatze.gtit.lottery.api.LotteryPoolGroupDefTest.main(args);
    }

    private static LotteryPool pool() {
        return new LotteryPool("p", "p", "", 0, PityConfig.createDefault());
    }

    private static JsonObject json(String input) {
        return new JsonParser().parse(input)
            .getAsJsonObject();
    }

    private static void rejects(Runnable operation) {
        try {
            operation.run();
        } catch (IllegalArgumentException expected) {
            return;
        }
        throw new AssertionError("Expected rejection");
    }

    private static void countContract() {
        SimpleAssert.eq(10, LotteryPoolSlots.requestedCount(json("{}"), 0, true), "new default ten");
        SimpleAssert.eq(1, LotteryPoolSlots.requestedCount(json("{}"), 1, false), "legacy edit preserves count");
        for (int n = 4; n <= 10; n++) SimpleAssert
            .eq(n, LotteryPoolSlots.requestedCount(json("{\"entryCount\":" + n + "}"), 0, true), "new boundary");
        for (String value : new String[] { "0", "3", "11", "4.5", "2147483648", "\"4\"", "null", "true" }) {
            rejects(() -> LotteryPoolSlots.requestedCount(json("{\"entryCount\":" + value + "}"), 0, true));
        }
        for (int n = 1; n <= 3; n++) SimpleAssert.eq(
            n,
            LotteryPoolSlots.requestedCount(json("{\"entryCount\":" + n + "}"), n, false),
            "legacy small pool saves");
    }

    private static void resizePreservesPrizes() {
        LotteryPool pool = pool();
        LotteryEntry original = LotteryEntry
            .createItemPrize("entry_2", "minecraft:apple", 3, 2, 4, 100, LotteryRarity.EPIC);
        original.setNbtBase64("preserved");
        pool.getEntries()
            .add(original);
        LotteryPoolSlots.resize(pool, 10);
        SimpleAssert.that(
            pool.getEntries()
                .get(0) == original,
            "original object preserved");
        SimpleAssert.eq("preserved", original.getNbtBase64(), "NBT preserved");
        SimpleAssert.eq(
            "entry_1",
            pool.getEntries()
                .get(1)
                .getId(),
            "first free id");
        java.util.Set<String> ids = new java.util.HashSet<>();
        for (LotteryEntry entry : pool.getEntries()) SimpleAssert.that(ids.add(entry.getId()), "unique ids");
        LotteryEntry tail = pool.getEntries()
            .get(9);
        tail.setItem("minecraft:diamond");
        rejects(() -> LotteryPoolSlots.resize(pool, 4));
        SimpleAssert.eq(
            10,
            pool.getEntries()
                .size(),
            "refused resize is atomic");
        tail.setItem(null);
        LotteryPoolSlots.resize(pool, 4);
        SimpleAssert.eq(
            4,
            pool.getEntries()
                .size(),
            "empty tail shrinks");
        SimpleAssert.eq(100, original.getWeight(), "weight preserved");
        SimpleAssert.eq(LotteryRarity.EPIC, original.getRarity(), "rarity preserved");
    }

    private static void draftSurvivesValidation() {
        LotteryPool pool = pool();
        LotteryPoolSlots.resize(pool, 10);
        SimpleAssert.that(!pool.validate(), "all-zero draft cannot draw");
        LotteryConfig.LotteryConfigData data = new LotteryConfig.LotteryConfigData();
        data.pools = new java.util.ArrayList<>();
        data.pools.add(pool);
        SimpleAssert.that(LotteryConfig.validateAll(data), "draft structure retained");
        SimpleAssert.eq(1, data.pools.size(), "draft survives config validation");
        for (LotteryEntry entry : pool.getEntries())
            SimpleAssert.that(entry.isEmptyPlaceholder(), "zero-weight empty placeholder");
    }

    private static void hardPitySkipsEmptyAndLargeWeights() {
        LotteryPool pool = pool();
        LotteryPoolSlots.resize(pool, 10);
        SimpleAssert.that(pool.getPityPrizeEntry() == null, "zero draft has no pity result");
        LotteryEntry common = pool.getEntries()
            .get(0);
        common.setItem("minecraft:apple");
        common.setWeight(Integer.MAX_VALUE);
        LotteryEntry common2 = pool.getEntries()
            .get(1);
        common2.setItem("minecraft:bread");
        common2.setWeight(Integer.MAX_VALUE);
        SimpleAssert.that(pool.validate(), "large weight total does not overflow");
        for (int i = 0; i < 200; i++) SimpleAssert.that(
            pool.getPityPrizeEntry()
                .isDrawable(),
            "fallback excludes zero slots");
        pool.getPityConfig()
            .setHardPityThreshold(1);
        java.util.UUID team = java.util.UUID.randomUUID();
        LotteryManager.INSTANCE.drawSingle(team, pool);
        LotteryDrawResult fallback = LotteryManager.INSTANCE.drawSingle(team, pool);
        SimpleAssert.that(
            fallback.getEntry()
                .isDrawable() && !fallback.isPity(),
            "missing guaranteed candidate is ordinary weighted result");
        common2.setRarity(LotteryRarity.EPIC);
        LotteryDrawResult guaranteed = LotteryManager.INSTANCE.drawSingle(team, pool);
        SimpleAssert.that(
            guaranteed.isPity() && guaranteed.getEntry() == common2,
            "real guaranteed candidate marks pity success");
        for (int i = 0; i < 200; i++)
            SimpleAssert.that(pool.getPityPrizeEntry() == common2, "only positive guaranteed prize selected");
    }

    private static void wheelSlotsNeverOverlap() {
        for (int n = 1; n <= 10; n++) for (int i = 0; i < n; i++) for (int j = i + 1; j < n; j++) {
            int[] a = LotteryWheelLayout.slotTopLeft(i, n, 170, 80), b = LotteryWheelLayout.slotTopLeft(j, n, 170, 80);
            SimpleAssert.that(Math.abs(a[0] - b[0]) >= 24 || Math.abs(a[1] - b[1]) >= 24, "no overlap n=" + n);
        }
        for (int n = 1; n <= 10; n++) for (int i = 0; i < n; i++) {
            int[] position = LotteryWheelLayout.slotTopLeft(i, n, 170, 80);
            SimpleAssert.that(
                position[0] >= 0 && position[1] >= 0 && position[0] + 24 <= 170 && position[1] + 24 <= 80,
                "within actual wheel bounds");
        }
        int[][] ring = { { 46, 10 }, { 74, 10 }, { 102, 10 }, { 130, 10 }, { 130, 38 }, { 130, 66 }, { 102, 66 },
            { 74, 66 }, { 46, 66 }, { 46, 38 } };
        for (int i = 0; i < 10; i++) SimpleAssert.that(
            java.util.Arrays.equals(ring[i], LotteryWheelLayout.slotTopLeft(i, 10, 200, 100)),
            "ten layout preserved");
    }

    private static void draftRoundTripAndManager() {
        SimpleAssert
            .that(Boolean.getBoolean("gtit.lottery.testScratch"), "must run in isolated Gradle working directory");
        LotteryPool draft = pool();
        LotteryPoolSlots.resize(draft, 10);
        LotteryConfig.LotteryConfigData data = new LotteryConfig.LotteryConfigData();
        data.pools.add(draft);
        LotteryConfig.save(data);
        SimpleAssert.eq(
            10,
            LotteryConfig.load().pools.get(0)
                .getEntries()
                .size(),
            "draft save/load retains all slots");
        LotteryManager.INSTANCE.loadConfig();
        SimpleAssert.that(LotteryManager.INSTANCE.getPool("p") != null, "draft remains accessible to editor");
        SimpleAssert.that(
            LotteryManager.INSTANCE.drawLottery(java.util.UUID.randomUUID(), "p", 1, null)
                .isEmpty(),
            "draft draw rejected before any cost or game API");
    }

    private static void softPitySkipsEmpty() {
        LotteryPool pool = pool();
        LotteryPoolSlots.resize(pool, 10);
        SimpleAssert.that(
            LotteryManager.INSTANCE.selectByWeight(pool.getEntries(), 20) == null,
            "all-zero soft pity yields no prize");
        LotteryEntry prize = pool.getEntries()
            .get(4);
        prize.setItem("minecraft:apple");
        prize.setWeight(10);
        prize.setRarity(LotteryRarity.EPIC);
        for (int i = 0; i < 200; i++) SimpleAssert.that(
            LotteryManager.INSTANCE.selectByWeight(pool.getEntries(), 20) == prize,
            "soft pity excludes empty slots");
    }

    private static void animationNormalizesPreviousPool() {
        LotteryAnimationController animation = LotteryAnimationController.getInstance();
        animation.startAnimation("large", 9, 10, false);
        animation.startAnimation("small", 0, 4, false);
        try {
            java.lang.reflect.Field field = LotteryAnimationController.class.getDeclaredField("startSlot");
            field.setAccessible(true);
            SimpleAssert.eq(1, field.getInt(animation), "previous target modulo new count");
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }
}

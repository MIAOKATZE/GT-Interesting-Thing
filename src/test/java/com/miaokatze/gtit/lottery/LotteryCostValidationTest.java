package com.miaokatze.gtit.lottery;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import com.miaokatze.gtit.currency.NekoCurrencyRegistrar;
import com.miaokatze.gtit.testutil.MinecraftTestBootstrap;
import com.miaokatze.gtit.trade.v2.NekoBigItemStack;
import com.miaokatze.gtit.util.NbtBase64Util;

/** Run in an isolated working directory: exercises actual config IO and editor parsing. */
public final class LotteryCostValidationTest {

    private static final Path CONFIG = Paths.get("config/gtit/lottery/lottery.json");
    private static final String ENTRY = "\"entries\":[{\"id\":\"prize\",\"item\":\"minecraft:iron_ingot\",\"minAmount\":1,\"maxAmount\":1,\"weight\":1,\"rarity\":\"COMMON\"}]";

    public static void main(String[] args) throws Exception {
        if (!Boolean.getBoolean("gtit.rewards.testScratch")) throw new IllegalStateException(
            "Run only in an isolated scratch working directory with -Dgtit.rewards.testScratch=true");
        if (!MinecraftTestBootstrap.enter(LotteryCostValidationTest.class, args)) return;
        int failed = 0;
        for (String name : new String[] { "missingPaymentNeverBecomesFree", "missingPaymentNeverPartiallyDiscounts",
            "invalidNbtNeverBecomesPlainPayment", "amountNeverClampsOrWraps", "nullPaymentNeverDisappears",
            "missingLegacyCurrencyNeverBecomesFree", "legacyAmountNeverWraps", "freeAndLegacyPoolsRemainValid",
            "validTaggedPaymentRoundTrips", "editorRejectsAnyMalformedPayment", "runtimeMutationCannotSkipPayment",
            "malformedExistingConfigIsPreserved", "editorCannotOverwriteUnreadableConfig",
            "missingPoolsIsNotEditableEmpty", "unsynthesizedLegacyPaymentCannotDrawFree" }) {
            try {
                LotteryCostValidationTest.class.getDeclaredMethod(name)
                    .invoke(null);
                System.out.println("PASS " + name);
            } catch (ReflectiveOperationException failure) {
                failed++;
                System.err.println("FAIL " + name + ": " + failure.getCause());
            }
        }
        if (failed > 0) throw new AssertionError(failed + " lottery cost regressions failed");
    }

    private static String config(String costs, String extra) {
        return "{\"pools\":[{\"id\":\"test\",\"name\":\"test\"," + ENTRY
            + (costs == null ? "" : ",\"costItems\":" + costs)
            + extra
            + "}]}";
    }

    private static void write(String json) throws Exception {
        Files.createDirectories(CONFIG.getParent());
        Files.write(CONFIG, json.getBytes(StandardCharsets.UTF_8));
    }

    private static void rejected(String json) throws Exception {
        write(json);
        check(
            LotteryConfig.load().pools.isEmpty(),
            "Malformed payment must disable loading, not weaken its requirements");
        check(
            json.equals(new String(Files.readAllBytes(CONFIG), StandardCharsets.UTF_8)),
            "Original config bytes retained");
    }

    private static void missingPaymentNeverBecomesFree() throws Exception {
        rejected(config("[{\"item\":\"missingmod:expensive_ticket\",\"amount\":99}]", ""));
    }

    private static void missingPaymentNeverPartiallyDiscounts() throws Exception {
        rejected(
            config(
                "[{\"item\":\"minecraft:iron_ingot\",\"amount\":1},{\"item\":\"missingmod:expensive_ticket\",\"amount\":99}]",
                ""));
    }

    private static void invalidNbtNeverBecomesPlainPayment() throws Exception {
        for (String nbt : new String[] { "%%%", "bm90LW5idA==" })
            rejected(config("[{\"item\":\"minecraft:iron_ingot\",\"nbtBase64\":\"" + nbt + "\"}]", ""));
    }

    private static void amountNeverClampsOrWraps() throws Exception {
        for (String amount : new String[] { "0", "-1", "4294967297", "1.5" })
            rejected(config("[{\"item\":\"minecraft:iron_ingot\",\"amount\":" + amount + "}]", ""));
    }

    private static void nullPaymentNeverDisappears() throws Exception {
        rejected(config("[null]", ""));
        rejected(config("null", ""));
    }

    private static void missingLegacyCurrencyNeverBecomesFree() throws Exception {
        rejected(config(null, ",\"nekoCurrencyId\":\"missing_currency\",\"costPerDraw\":5"));
    }

    private static void legacyAmountNeverWraps() throws Exception {
        rejected(config(null, ",\"nekoCurrencyId\":\"neko\",\"costPerDraw\":4294967297"));
    }

    private static void freeAndLegacyPoolsRemainValid() throws Exception {
        write(config("[]", ""));
        List<LotteryPool> free = LotteryConfig.load().pools;
        check(
            free.size() == 1 && free.get(0)
                .getCostItems()
                .isEmpty()
                && free.get(0)
                    .validate(),
            "Intentional free pool remains usable");
        net.minecraft.item.Item previous = NekoCurrencyRegistrar.nekoCoinItem;
        try {
            // The headless bootstrap initializes vanilla registries, not GTIT's postInit coin binding.
            NekoCurrencyRegistrar.nekoCoinItem = Items.gold_nugget;
            write(config(null, ",\"nekoCurrencyId\":\"neko\",\"costPerDraw\":5"));
            List<LotteryPool> legacy = LotteryConfig.load().pools;
            check(
                legacy.size() == 1 && legacy.get(0)
                    .getCostItems()
                    .size() == 1
                    && legacy.get(0)
                        .getCostItems()
                        .get(0)
                        .getStackSize() == 5,
                "Valid legacy payment is preserved");
        } finally {
            NekoCurrencyRegistrar.nekoCoinItem = previous;
        }
    }

    private static void validTaggedPaymentRoundTrips() throws Exception {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("ticket", "paid-exact");
        String nbt = NbtBase64Util.nbtToBase64(tag);
        write(config("[{\"item\":\"minecraft:iron_ingot\",\"amount\":7,\"nbtBase64\":\"" + nbt + "\"}]", ""));
        LotteryConfig.LotteryConfigData loaded = LotteryConfig.load();
        LotteryConfig.save(loaded);
        NekoBigItemStack cost = LotteryConfig.load().pools.get(0)
            .getCostItems()
            .get(0);
        check(
            cost.getStackSize() == 7 && tag.equals(
                cost.getBaseStack()
                    .getTagCompound()),
            "Exact tagged payment survives save/reload");
        check(parseEditor("[]").isEmpty(), "Editor can intentionally make pool free");
        cost = parseEditor("[{\"item\":\"minecraft:iron_ingot\",\"amount\":7,\"nbtBase64\":\"" + nbt + "\"}]").get(0);
        check(
            cost.getStackSize() == 7 && tag.equals(
                cost.getBaseStack()
                    .getTagCompound()),
            "Editor uses exact same tagged payment");
    }

    @SuppressWarnings("unchecked")
    private static List<NekoBigItemStack> parseEditor(String json) throws Exception {
        Class<?> editor = Class.forName("com.miaokatze.gtit.trade.v2.LotteryEditActions");
        Method parse = editor.getDeclaredMethod("parsePoolCostItems", JsonArray.class);
        parse.setAccessible(true);
        return (List<NekoBigItemStack>) parse.invoke(
            null,
            new JsonParser().parse(json)
                .getAsJsonArray());
    }

    private static void editorRejectsAnyMalformedPayment() throws Exception {
        for (String cost : new String[] { "{\"item\":\"missing:ticket\"}",
            "{\"item\":\"minecraft:iron_ingot\",\"nbtBase64\":\"%%%\"}",
            "{\"item\":\"minecraft:iron_ingot\",\"amount\":4294967297}", "null" }) {
            boolean failed = false;
            try {
                parseEditor("[{\"item\":\"minecraft:iron_ingot\"}," + cost + "]");
            } catch (java.lang.reflect.InvocationTargetException expected) {
                failed = true;
            }
            check(failed, "Editor must reject whole payment, not save discounted subset: " + cost);
        }
    }

    private static void runtimeMutationCannotSkipPayment() throws Exception {
        java.lang.reflect.Field ready = LotteryManager.class.getDeclaredField("sessionUnavailable");
        ready.setAccessible(true);
        ready.setBoolean(LotteryManager.INSTANCE, false);
        LotteryPool pool = new LotteryPool("test", "test", "", 0, PityConfig.createDefault());
        pool.getEntries()
            .add(LotteryEntry.createItemPrize("prize", "minecraft:iron_ingot", 0, 1, 1, 1, LotteryRarity.COMMON));
        pool.getCostItems()
            .add(null);
        check(!pool.validate(), "Null runtime payment disables drawing");
        check(
            !LotteryManager.INSTANCE.canAfford(UUID.randomUUID(), pool, 1, null),
            "Null runtime payment is not affordable for free");
        pool.setCostItems(Arrays.asList(new NekoBigItemStack(0, "", new ItemStack(Items.iron_ingot))));
        check(!pool.validate(), "Zero runtime payment must not be skipped");
        check(!LotteryManager.INSTANCE.canAfford(UUID.randomUUID(), pool, 1, null), "Zero runtime payment is not free");
    }

    private static void malformedExistingConfigIsPreserved() throws Exception {
        rejected("{ definitely broken }");
    }

    private static void unsynthesizedLegacyPaymentCannotDrawFree() {
        LotteryPool pool = new LotteryPool("legacy", "legacy", "missing_currency", 5, PityConfig.createDefault());
        pool.getEntries()
            .add(LotteryEntry.createItemPrize("prize", "minecraft:iron_ingot", 0, 1, 1, 1, LotteryRarity.COMMON));
        check(!pool.validate(), "An unresolved legacy price is not an intentionally free first-run/programmatic pool");
    }

    private static void editorCannotOverwriteUnreadableConfig() throws Exception {
        assertEditorPreserves("{ definitely broken }");
    }

    private static void missingPoolsIsNotEditableEmpty() throws Exception {
        for (String json : new String[] { "{}", "null", "{\"pools\":null}" }) assertEditorPreserves(json);
    }

    private static void assertEditorPreserves(String broken) throws Exception {
        write(broken);
        Class<?> editor = Class.forName("com.miaokatze.gtit.trade.v2.LotteryEditActions");
        Method create = editor
            .getDeclaredMethod("createLotteryPool", net.minecraft.entity.player.EntityPlayerMP.class, String.class);
        create.setAccessible(true);
        try {
            create.invoke(null, null, "{\"id\":\"new_pool\",\"entryCount\":4,\"costItems\":[]}");
        } catch (java.lang.reflect.InvocationTargetException failure) {
            // No live recipient is needed for this file boundary test; native feedback dereferences null.
            if (!(failure.getCause() instanceof NullPointerException)) throw failure;
        }
        check(
            broken.equals(new String(Files.readAllBytes(CONFIG), StandardCharsets.UTF_8)),
            "Editor must not replace unreadable file with a new pool");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}

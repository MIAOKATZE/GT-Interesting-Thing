package com.miaokatze.gtit.crossmod.bq;

import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

import net.minecraft.nbt.NBTTagCompound;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.miaokatze.gtit.testutil.MinecraftTestBootstrap;
import com.miaokatze.gtit.testutil.TestRunner;

import betterquesting.api.utils.NBTConverter;

/** Pack preflight is read-only and must reject incomplete resources before the injector can mutate BQ. */
public final class BqQuestPackTest {

    private BqQuestPackTest() {}

    public static void main(String[] args) throws Exception {
        if (!MinecraftTestBootstrap.enter(BqQuestPackTest.class, args)) return;
        Map<String, Runnable> cases = new LinkedHashMap<>();
        cases.put("front-row identifiers match shipped full UUID high bits", BqQuestPackTest::frontRowIdentifiers);
        cases.put("complete pack caches each resource once", BqQuestPackTest::completePack);
        cases.put("missing line fails closed", () -> missing("line"));
        cases.put("missing quest fails closed", () -> missing("quest"));
        cases.put("missing entry fails closed", () -> missing("entry"));
        cases.put("invalid later manifest entry fails closed", BqQuestPackTest::invalidEntry);
        cases.put("explicit empty pack remains valid", BqQuestPackTest::emptyPack);
        cases.put("missing quest identity cannot silently become zero",
            () -> invalidNbt("quest", n -> n.removeTag("questIDLow")));
        cases.put("wrong-typed quest identity cannot silently become zero",
            () -> invalidNbt("quest", n -> n.setString("questIDLow", "1")));
        cases.put("foreign quest cannot overwrite another pack",
            () -> invalidNbt("quest", n -> n.setLong("questIDHigh", 12345)));
        cases.put("line resource must agree with manifest identity",
            () -> invalidNbt("line", n -> n.setLong("questLineIDLow", 2)));
        cases.put("entry resource must agree with quest identity",
            () -> invalidNbt("entry", n -> n.setLong("questIDLow", 2)));
        cases.put("conflicting UUID definitions are rejected", BqQuestPackTest::duplicateQuest);
        cases.put("duplicate manifest lines are rejected", BqQuestPackTest::duplicateLine);
        cases.put("fractional and overflowing manifest IDs are rejected", BqQuestPackTest::invalidNumber);
        cases.put("fractional and overflowing JSON resource IDs are rejected before NBT conversion",
            BqQuestPackTest::invalidResourceNumber);
        cases.put("all shipped pack identities and intentional empty pack pass preflight", BqQuestPackTest::shippedPacks);
        cases.put("unrecognized pack roots retain arbitrary UUID namespaces", BqQuestPackTest::customPack);
        TestRunner.run(BqQuestPackTest.class, cases);
    }

    private static void frontRowIdentifiers() {
        try (InputStreamReader reader = new InputStreamReader(
            BqQuestPackTest.class.getClassLoader().getResourceAsStream("assets/gtit/bqquests/index.json"),
            StandardCharsets.UTF_8)) {
            JsonObject index = new JsonParser().parse(reader).getAsJsonObject();
            long shippedHigh = index.getAsJsonArray("questLines").get(0).getAsJsonObject().get("idHigh").getAsLong();
            Field field = BqQuestInjector.class.getDeclaredField("FRONT_ROW_LINE_HIGH");
            field.setAccessible(true);
            long[] actual = (long[]) field.get(null);
            require(Arrays.equals(actual, new long[] { 0x4754535200000000L, 0x4754574E00000000L, shippedHigh }),
                "front-row identifiers must use the same UUID high construction as the shipped manifests");
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private static void completePack() {
        Map<String, Integer> calls = new HashMap<>();
        JsonArray lines = lines();
        // A quest can belong to two lines, but its shared definition is read only once.
        JsonObject second = new JsonParser().parse(lines.get(0).toString()).getAsJsonObject();
        second.addProperty("idLow", 2);
        second.addProperty("lineFile", "line2");
        lines.add(second);
        Map<String, NBTTagCompound> resources = BqQuestInjector.prepareResources(lines, path -> {
            calls.put(path, calls.getOrDefault(path, 0) + 1);
            NBTTagCompound tag = tag();
            if ("line2".equals(path)) tag.setLong("questLineIDLow", 2);
            return tag;
        });
        require(resources.size() == 4 && calls.size() == 4, "all resource types must be preflighted");
        for (int count : calls.values()) require(count == 1, "resource was read twice");
    }

    private static void missing(String missing) {
        boolean rejected = false;
        try {
            BqQuestInjector.prepareResources(lines(), path -> missing.equals(path) ? null : tag());
        } catch (IllegalStateException expected) {
            rejected = true;
        }
        require(rejected, "missing " + missing + " must not become a partial prune input");
    }

    private static void invalidEntry() {
        JsonArray lines = lines();
        lines.get(0).getAsJsonObject().getAsJsonArray("entries").add(new JsonObject());
        boolean rejected = false;
        try {
            BqQuestInjector.prepareResources(lines, path -> tag());
        } catch (RuntimeException expected) {
            rejected = true;
        }
        require(rejected, "malformed later manifest entry must not reach live definition replacement");
    }

    private static void emptyPack() {
        require(BqQuestInjector.prepareResources(new JsonArray(), path -> {
            throw new AssertionError("empty pack must not request resources");
        }).isEmpty(), "empty manifest is the intentional removal sentinel");
    }

    private static void invalidNbt(String target, Consumer<NBTTagCompound> corrupt) {
        rejected(() -> BqQuestInjector.prepareResources(lines(), path -> {
            NBTTagCompound tag = tag();
            if (target.equals(path)) corrupt.accept(tag);
            return tag;
        }, 0x4754495400000000L));
    }

    private static void duplicateQuest() {
        JsonArray lines = lines();
        JsonObject entry = new JsonObject();
        entry.addProperty("questFile", "another-definition");
        entry.addProperty("entryFile", "entry");
        lines.get(0).getAsJsonObject().getAsJsonArray("entries").add(entry);
        rejected(() -> BqQuestInjector.prepareResources(lines, path -> tag()));
    }

    private static void duplicateLine() {
        JsonArray lines = lines();
        lines.add(lines.get(0));
        rejected(() -> BqQuestInjector.prepareResources(lines, path -> tag()));
    }

    private static void invalidNumber() {
        for (String number : Arrays.asList("1.5", "18446744073709551617")) {
            JsonArray lines = lines();
            lines.get(0).getAsJsonObject().add("idLow", new JsonParser().parse(number));
            rejected(() -> BqQuestInjector.prepareResources(lines, path -> tag()));
        }
    }

    private static void invalidResourceNumber() {
        for (String number : Arrays.asList("1.5", "18446744073709551617", "\"1\"")) {
            JsonObject resource = new JsonParser().parse("{\"questIDLow:4\":" + number + "}").getAsJsonObject();
            rejected(() -> BqQuestInjector.validateResourceIds(resource));
        }
        BqQuestInjector.validateResourceIds(new JsonParser()
            .parse("{\"questIDHigh:4\":9223372036854775807,\"questIDLow:4\":-9223372036854775808}")
            .getAsJsonObject());
    }

    private static void shippedPacks() {
        for (String root : Arrays.asList("assets/gtit/bqquests/", "assets/gtit/bqquests/miao/")) {
            JsonObject index = resource(root + "index.json");
            Map<String, NBTTagCompound> prepared = BqQuestInjector.prepareResources(
                index.getAsJsonArray("questLines"),
                path -> {
                    JsonObject json = resource(path);
                    BqQuestInjector.validateResourceIds(json);
                    return NBTConverter.JSONtoNBT_Object(json, new NBTTagCompound(), true);
                },
                root.contains("/miao/") ? 5569054221778550784L : 5139813699862659072L);
            require(prepared.isEmpty() == root.contains("/miao/"), "shipped nonempty/empty pack contract changed");
        }
    }

    private static JsonObject resource(String path) {
        try (InputStreamReader reader = new InputStreamReader(
            BqQuestPackTest.class.getClassLoader().getResourceAsStream(path), StandardCharsets.UTF_8)) {
            return new JsonParser().parse(reader).getAsJsonObject();
        } catch (Exception e) {
            throw new AssertionError(path, e);
        }
    }

    private static void customPack() {
        require(BqQuestInjector.prepareResources(lines(), path -> {
            NBTTagCompound tag = tag();
            if (!"line".equals(path)) tag.setLong("questIDHigh", 12345);
            return tag;
        }).size() == 3, "unknown pack roots must not inherit GTIT/MIAO ownership restrictions");
    }

    private static void rejected(Runnable action) {
        try {
            action.run();
        } catch (RuntimeException expected) {
            return;
        }
        throw new AssertionError("invalid pack identity was accepted");
    }

    private static JsonArray lines() {
        return new JsonParser().parse("[{\"idHigh\":5139813699862659072,\"idLow\":1,\"orderIndex\":902,"
            + "\"lineFile\":\"line\",\"entries\":[{\"questFile\":\"quest\",\"entryFile\":\"entry\"}]}]")
            .getAsJsonArray();
    }

    private static NBTTagCompound tag() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setLong("questIDHigh", 0x4754495400000000L);
        tag.setLong("questIDLow", 1);
        tag.setLong("questLineIDHigh", 0x4754495400000000L);
        tag.setLong("questLineIDLow", 1);
        return tag;
    }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}

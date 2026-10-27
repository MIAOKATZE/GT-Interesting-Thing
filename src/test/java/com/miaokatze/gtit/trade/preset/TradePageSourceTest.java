package com.miaokatze.gtit.trade.preset;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.miaokatze.gtit.testutil.MinecraftTestBootstrap;
import com.miaokatze.gtit.testutil.SimpleAssert;
import com.miaokatze.gtit.trade.NekoPageConfig;
import com.miaokatze.gtit.trade.NekoPageEntry;
import com.miaokatze.gtit.trade.NekoPageSource;
import com.miaokatze.gtit.trade.NekoTradeEntry;

/** Real Gson/filesystem regression; does not read or replace any live game configuration. */
public final class TradePageSourceTest {

    public static void main(String[] args) throws Exception {
        if (!MinecraftTestBootstrap.enter(TradePageSourceTest.class, args)) return;
        if (args.length == 1 && "runtime".equals(args[0])) {
            runtimeRoundtrip();
            return;
        }
        Path fixture = Files.createTempDirectory("gtit-page-source-")
            .toAbsolutePath()
            .normalize();
        try {
            modelAndSync();
            migration(fixture.resolve("migration"));
            defaultAdditions(fixture.resolve("defaults"));
            presetLifecycle(fixture.resolve("lifecycle"));
            launchRuntime(fixture.resolve("runtime"));
            System.out.println(
                "TradePageSourceTest: model, migration, default addition/reset, preset lifecycle, runtime roundtrip passed");
        } finally {
            try (java.util.stream.Stream<Path> paths = Files.walk(fixture)) {
                for (Path path : paths.sorted(java.util.Comparator.reverseOrder())
                    .collect(java.util.stream.Collectors.toList())) Files.deleteIfExists(path);
            }
        }
    }

    private static void modelAndSync() {
        NekoPageEntry base = page(-400001, "gtit-base");
        NekoPageEntry optional = page(-100001, "gtit-ae-basic");
        NekoPageEntry customPreset = page(-600001, "local-library");
        NekoPageEntry detached = page(1000, "local-library");
        SimpleAssert.eq(NekoPageSource.DEFAULT, base.getSource(), "base with nonempty preset id is DEFAULT");
        SimpleAssert.eq(NekoPageSource.PRESET, optional.getSource(), "optional builtin is PRESET");
        SimpleAssert.eq(NekoPageSource.PRESET, customPreset.getSource(), "installed custom preset is PRESET");
        SimpleAssert.eq(NekoPageSource.CUSTOM, detached.getSource(), "detached historical attribution is CUSTOM");
        SimpleAssert.that(base.canCreateTrade() && detached.canCreateTrade(), "default/custom creation enabled");
        SimpleAssert.that(!optional.canCreateTrade() && !customPreset.canCreateTrade(), "preset creation disabled");
        NekoPageConfig.NekoPageData data = new NekoPageConfig.NekoPageData();
        for (NekoPageEntry page : Arrays.asList(base, optional, customPreset, detached)) {
            page.ensureSource();
            data.getPages()
                .add(page);
        }
        NekoPageConfig.NekoPageData synced = NekoPageConfig.fromJson(NekoPageConfig.toJson(data));
        for (int index = 0; index < data.getPages()
            .size(); index++) {
            SimpleAssert.eq(
                data.getPages()
                    .get(index)
                    .getId(),
                synced.getPages()
                    .get(index)
                    .getId(),
                "sync real page id");
            SimpleAssert.eq(
                data.getPages()
                    .get(index)
                    .getSource(),
                synced.getPages()
                    .get(index)
                    .getSource(),
                "sync identity");
        }
        NekoPageEntry future = TradePresetStore.GSON
            .fromJson("{\"id\":55,\"source\":\"FUTURE_SOURCE\"}", NekoPageEntry.class);
        future.ensureSource();
        SimpleAssert.that(!future.canCreateTrade(), "future source does not silently acquire creation permission");
        SimpleAssert.that(
            TradePresetStore.GSON.toJson(future)
                .contains("FUTURE_SOURCE"),
            "future identity roundtrip");
    }

    private static void migration(Path root) throws Exception {
        String userPath = "config/gtit/trade/pages.json";
        String managedPath = TradePresetLayout.PAGES_PATH;
        String statePath = "config/gtit/trade/preset_state.json";
        String originalUser = "{\"version\":77,\"futureEnvelope\":{\"keep\":1},\"pages\":["
            + "{\"id\":7,\"name\":\"detached\",\"sourcePresetId\":\"former\",\"futurePage\":9},"
            + "{\"id\":8,\"name\":\"owned positive\",\"presetBuiltin\":false},"
            + "{\"id\":9,\"source\":\"FUTURE_SOURCE\"}]}";
        write(root, userPath, originalUser);
        write(
            root,
            managedPath,
            "{\"pages\":[{\"id\":-400001,\"sourcePresetId\":\"gtit-base\"},"
                + "{\"id\":-100001,\"sourcePresetId\":\"gtit-ae-basic\"},"
                + "{\"id\":-600001,\"sourcePresetId\":\"local\",\"presetBuiltin\":false},"
                + "{\"id\":-600002,\"sourcePresetId\":\"local\",\"source\":\"CUSTOM\"}]}");
        write(
            root,
            statePath,
            "{\"futureState\":123,\"ownedTabs\":[{\"tabId\":8,\"presetId\":\"local\","
                + "\"page\":{\"id\":8,\"name\":\"owned positive\",\"presetBuiltin\":false},\"trades\":{\"trades\":[]}}]}");
        String override = "config/gtit/trade/default_overrides/gtit-base.json";
        write(
            root,
            override,
            "{\"id\":\"gtit-base\",\"futureOverride\":5,\"tabs\":[{\"page\":{\"id\":-400001},"
                + "\"trades\":[{\"id\":\"addition\",\"defaultEntry\":false,\"futureTrade\":42}]}]}");
        String untouched = "config/gtit/trade/preset_tabs/tab_-600001.json";
        write(root, untouched, "{\"future\":true,\"trades\":[]}");
        byte[] tradeBefore = Files.readAllBytes(root.resolve(untouched));
        PageSourceIdentity.migrate(root);
        JsonObject users = read(root, userPath);
        SimpleAssert.eq(
            77,
            users.get("version")
                .getAsInt(),
            "no schema downgrade");
        SimpleAssert.that(users.has("futureEnvelope"), "unknown envelope retained");
        SimpleAssert.eq("CUSTOM", source(users, 0), "user path beats stale attribution");
        SimpleAssert.eq("PRESET", source(users, 1), "ownedTabs authoritative for positive legacy id");
        SimpleAssert.eq("FUTURE_SOURCE", source(users, 2), "unknown explicit source retained");
        SimpleAssert.eq(
            9,
            users.getAsJsonArray("pages")
                .get(0)
                .getAsJsonObject()
                .get("futurePage")
                .getAsInt(),
            "unknown page retained");
        JsonObject managed = read(root, managedPath);
        SimpleAssert.eq("DEFAULT", source(managed, 0), "base managed legacy mapping");
        SimpleAssert.eq("PRESET", source(managed, 1), "optional managed legacy mapping");
        SimpleAssert.eq("PRESET", source(managed, 2), "custom preset mapping despite builtin false");
        SimpleAssert.eq("CUSTOM", source(managed, 3), "explicit identity wins");
        JsonObject owned = read(root, statePath).getAsJsonArray("ownedTabs")
            .get(0)
            .getAsJsonObject();
        SimpleAssert.eq(
            users.getAsJsonArray("pages")
                .get(1),
            owned.get("page"),
            "ownership snapshot remains equal after migration");
        SimpleAssert.eq(
            123,
            read(root, statePath).get("futureState")
                .getAsInt(),
            "unknown state retained");
        JsonObject overridden = read(root, override);
        SimpleAssert.eq(
            "DEFAULT",
            overridden.getAsJsonArray("tabs")
                .get(0)
                .getAsJsonObject()
                .getAsJsonObject("page")
                .get("source")
                .getAsString(),
            "override path source");
        SimpleAssert.that(
            !overridden.getAsJsonArray("tabs")
                .get(0)
                .getAsJsonObject()
                .getAsJsonArray("trades")
                .get(0)
                .getAsJsonObject()
                .get("defaultEntry")
                .getAsBoolean(),
            "migration never changes defaultEntry");
        SimpleAssert
            .that(Arrays.equals(tradeBefore, Files.readAllBytes(root.resolve(untouched))), "trade files untouched");
        String backup = "config/gtit/trade/default_migration_backup/source_identity_" + userPath.replace('/', '_');
        SimpleAssert.eq(
            originalUser,
            new String(Files.readAllBytes(root.resolve(backup)), StandardCharsets.UTF_8),
            "exact original backup");
        Map<Path, String> before = snapshot(root);
        PageSourceIdentity.migrate(root);
        SimpleAssert.eq(before, snapshot(root), "migration idempotent including backups");
    }

    private static void defaultAdditions(Path root) throws Exception {
        Files.createDirectories(root);
        TradePresetDefinition fingerprintDefinition = DefaultTradeSources.bundled(DefaultTradeSources.BASE_ID);
        JsonObject oldDefinition = TradePresetStore.GSON.toJsonTree(fingerprintDefinition)
            .getAsJsonObject();
        for (com.google.gson.JsonElement tab : oldDefinition.getAsJsonArray("tabs")) tab.getAsJsonObject()
            .getAsJsonObject("page")
            .remove("source");
        String legacyFingerprint = java.util.Base64.getEncoder()
            .encodeToString(
                java.security.MessageDigest.getInstance("SHA-256")
                    .digest(DefaultTradeSources.bytes(oldDefinition)));
        SimpleAssert.eq(
            legacyFingerprint,
            TradePresetStore.definitionFingerprint(fingerprintDefinition),
            "additive source does not trigger destructive default refresh");
        fingerprintDefinition.tabs.get(0).page.setSource(NekoPageSource.DEFAULT);
        SimpleAssert.eq(
            legacyFingerprint,
            TradePresetStore.definitionFingerprint(fingerprintDefinition),
            "page identity is independent of content fingerprint");
        List<NekoTradeEntry> entries = new ArrayList<>(DefaultTradeSources.trades(root));
        SimpleAssert.that(!entries.isEmpty(), "bundled baseline exists");
        NekoTradeEntry stock = entries.get(0);
        String stockId = stock.getId();
        SimpleAssert.that(stock.isDefaultEntry(), "stock flag starts true");
        int tabId = stock.getTabId();
        NekoTradeEntry addition = new NekoTradeEntry();
        addition.setTabId(tabId);
        addition.setOrderId(9999);
        addition.setDefaultEntry(false);
        addition.getToItems()
            .add(new NekoTradeEntry.ItemEntry("minecraft:stone", 0, 3));
        String addedId = addition.getId();
        entries.add(addition);
        DefaultTradeSources.saveTrades(root, entries);
        SimpleAssert.that(!addition.isDefaultEntry(), "save never promotes default-page addition");
        NekoTradeEntry loaded = find(DefaultTradeSources.trades(root), addedId);
        SimpleAssert.eq(tabId, loaded.getTabId(), "real negative page remains unchanged");
        SimpleAssert.that(!loaded.isDefaultEntry(), "false survives override reload");
        SimpleAssert
            .that(find(DefaultTradeSources.trades(root), stockId).isDefaultEntry(), "stock override flag survives");
        DefaultTradeSources.saveTrades(root, DefaultTradeSources.trades(root));
        SimpleAssert
            .that(!find(DefaultTradeSources.trades(root), addedId).isDefaultEntry(), "repeat save preserves false");
        TradePresetDefinition installed = DefaultTradeSources.installed(DefaultTradeSources.BASE_ID, root);
        SimpleAssert.eq(
            NekoPageSource.DEFAULT,
            installed.tabs.get(0).page.getSource(),
            "default page identity independent of additions");
        SimpleAssert.eq(
            NekoPageSource.PRESET,
            DefaultTradeSources.installed("gtit-ae-basic", root).tabs.get(0).page.getSource(),
            "builtin optional identity PRESET");
        String overridePath = "config/gtit/trade/default_overrides/gtit-base.json";
        JsonObject futureOverride = read(root, overridePath);
        futureOverride.getAsJsonArray("tabs")
            .get(0)
            .getAsJsonObject()
            .getAsJsonObject("page")
            .addProperty("source", "FUTURE_SOURCE");
        write(root, overridePath, futureOverride.toString());
        TradePresetDefinition futureLoaded = DefaultTradeSources.installed(DefaultTradeSources.BASE_ID, root);
        SimpleAssert.that(
            TradePresetStore.GSON.toJson(futureLoaded.tabs.get(0).page)
                .contains("FUTURE_SOURCE"),
            "override preserves future source identity");
        SimpleAssert
            .that(!futureLoaded.tabs.get(0).page.canCreateTrade(), "future override source no implicit creation");
        DefaultTradeSources.reset(root);
        SimpleAssert.that(
            DefaultTradeSources.trades(root)
                .stream()
                .noneMatch(entry -> addedId.equals(entry.getId())),
            "explicit reset restores jar baseline");
        SimpleAssert.that(find(DefaultTradeSources.trades(root), stockId).isDefaultEntry(), "reset keeps stock flag");
    }

    private static void presetLifecycle(Path root) throws Exception {
        write(root, "config/gtit/trade/pages.json", "{\"pages\":[{\"id\":7,\"name\":\"user\"}]}");
        NekoTradeEntry trade = new NekoTradeEntry();
        trade.setTabId(7);
        trade.getToItems()
            .add(new NekoTradeEntry.ItemEntry("minecraft:stone", 0, 1));
        com.miaokatze.gtit.trade.NekoTradeConfig.TabFileData data = new com.miaokatze.gtit.trade.NekoTradeConfig.TabFileData();
        data.setTabId(7);
        data.getTrades()
            .add(trade);
        write(root, TradePresetLayout.tabPath(7), TradePresetStore.GSON.toJson(data));
        TradePresetStore store = new TradePresetStore(root);
        TradePresetDefinition definition = new TradePresetDefinition();
        definition.id = "source-test";
        definition.name = "Installed library";
        store.save(store.revision(), definition, Arrays.asList(7), false, ignored -> {});
        store.select(store.revision(), Arrays.asList(definition.id), ignored -> {});
        int installed = store.installedTabs(definition.id)
            .get(0);
        NekoPageEntry page = store.pages()
            .stream()
            .filter(value -> value.getId() == installed)
            .findFirst()
            .get();
        SimpleAssert.eq(NekoPageSource.PRESET, page.getSource(), "installed custom preset is not CUSTOM");
        SimpleAssert.eq(definition.id, page.getSourcePresetId(), "preset badge identity retained");
        store = new TradePresetStore(root);
        store.select(store.revision(), new ArrayList<>(), ignored -> {});
        SimpleAssert.that(
            !Files.exists(root.resolve(TradePresetLayout.tabPath(installed))),
            "unchanged uninstall remains safe after source migration");
        store.select(store.revision(), Arrays.asList(definition.id), ignored -> {});
        JsonObject edited = read(root, TradePresetLayout.tabPath(installed));
        edited.addProperty("userCustomization", "keep");
        write(root, TradePresetLayout.tabPath(installed), edited.toString());
        store.select(store.revision(), new ArrayList<>(), ignored -> {});
        NekoPageEntry detached = store.pages()
            .stream()
            .filter(value -> definition.id.equals(value.getSourcePresetId()))
            .findFirst()
            .get();
        SimpleAssert.that(detached.getId() > 0, "edited uninstall preserves real detached page");
        SimpleAssert.eq(NekoPageSource.CUSTOM, detached.getSource(), "detached page explicitly CUSTOM");
        SimpleAssert.that(detached.canCreateTrade(), "detached page creation allowed");
        SimpleAssert.eq(
            "keep",
            read(root, TradePresetLayout.tabPath(detached.getId())).get("userCustomization")
                .getAsString(),
            "customized trade file preserved");
        PageSourceIdentity.migrate(root);
        SimpleAssert.eq(
            NekoPageSource.CUSTOM,
            new TradePresetStore(root).pages()
                .stream()
                .filter(value -> value.getId() == detached.getId())
                .findFirst()
                .get()
                .getSource(),
            "detach stays CUSTOM after restart");
    }

    private static void launchRuntime(Path root) throws Exception {
        Files.createDirectories(root);
        Files.write(root.resolve("source-runtime-fixture.marker"), new byte[0]);
        String classpath = Arrays.stream(
            System.getProperty("java.class.path")
                .split(java.io.File.pathSeparator))
            .map(
                path -> java.nio.file.Paths.get(path)
                    .toAbsolutePath()
                    .toString())
            .collect(java.util.stream.Collectors.joining(java.io.File.pathSeparator));
        Process child = new ProcessBuilder(
            java.nio.file.Paths.get(System.getProperty("java.home"), "bin", "java")
                .toString(),
            "-cp",
            classpath,
            TradePageSourceTest.class.getName(),
            "runtime").directory(root.toFile())
                .inheritIO()
                .start();
        SimpleAssert.eq(0, child.waitFor(), "isolated full config pipeline");
    }

    private static void runtimeRoundtrip() throws Exception {
        Path root = java.nio.file.Paths.get("")
            .toAbsolutePath()
            .normalize();
        if (!Files.exists(root.resolve("source-runtime-fixture.marker")))
            throw new IllegalStateException("Full config test requires isolated temporary working directory");
        com.miaokatze.gtit.trade.NekoTradeConfig.setDefaultTradesSuppressed(true);
        write(
            root,
            "config/gtit/trade/pages.json",
            "{\"version\":77,\"futureEnvelope\":true,\"pages\":["
                + "{\"id\":77,\"name\":\"custom\",\"futurePage\":42,\"source\":\"CUSTOM\"}]}");
        NekoPageConfig.NekoPageData pages = NekoPageConfig.load();
        NekoPageEntry custom = pages.getPages()
            .stream()
            .filter(page -> page.getId() == 77)
            .findFirst()
            .get();
        custom.setName("edited");
        NekoPageConfig.save(pages);
        JsonObject saved = read(root, "config/gtit/trade/pages.json");
        SimpleAssert.eq(
            77,
            saved.get("version")
                .getAsInt(),
            "page save retains future version");
        SimpleAssert.that(saved.has("futureEnvelope"), "page save retains future envelope");
        SimpleAssert.eq(
            42,
            saved.getAsJsonArray("pages")
                .get(0)
                .getAsJsonObject()
                .get("futurePage")
                .getAsInt(),
            "page save retains unknown fields");
        NekoPageEntry positivePreset = page(88, "installed-positive");
        positivePreset.setSource(NekoPageSource.PRESET);
        pages.getPages()
            .add(positivePreset);
        com.miaokatze.gtit.trade.NekoPageRegistry.applySyncedPages(pages);
        com.miaokatze.gtit.trade.NekoPageRegistry.addPage(88, "renamed", null);
        SimpleAssert.eq(
            NekoPageSource.PRESET,
            com.miaokatze.gtit.trade.NekoPageRegistry.getPage(88)
                .getSource(),
            "legacy positive page rename keeps source");
        SimpleAssert.eq(
            "installed-positive",
            com.miaokatze.gtit.trade.NekoPageRegistry.getPage(88)
                .getSourcePresetId(),
            "legacy positive rename keeps attribution");
        NekoPageEntry base = NekoPageConfig.load()
            .getPages()
            .stream()
            .filter(page -> page.getSource() == NekoPageSource.DEFAULT)
            .findFirst()
            .get();
        SimpleAssert.that(base.getId() < 0 && base.canCreateTrade(), "real default negative page supports creation");
        com.miaokatze.gtit.trade.NekoTradeConfig.NekoTradeData trades = com.miaokatze.gtit.trade.NekoTradeConfig.load();
        NekoTradeEntry addition = new NekoTradeEntry();
        addition.setTabId(base.getId());
        addition.setOrderId(com.miaokatze.gtit.trade.NekoTradeConfig.allocateOrderId(base.getId(), 999));
        addition.getToItems()
            .add(new NekoTradeEntry.ItemEntry("minecraft:stone", 0, 2));
        trades.getTrades()
            .add(addition);
        com.miaokatze.gtit.trade.NekoTradeConfig.save(trades);
        NekoTradeEntry reloaded = find(
            com.miaokatze.gtit.trade.NekoTradeConfig.load()
                .getTrades(),
            addition.getId());
        SimpleAssert.that(!reloaded.isDefaultEntry(), "full config save/reload retains false addition");
        SimpleAssert.eq(base.getId(), reloaded.getTabId(), "full config preserves target negative page");
        NekoPageConfig.NekoPageData synced = NekoPageConfig.fromJson(NekoPageConfig.toJson(NekoPageConfig.load()));
        SimpleAssert.eq(
            NekoPageSource.DEFAULT,
            synced.getPages()
                .stream()
                .filter(page -> page.getId() == base.getId())
                .findFirst()
                .get()
                .getSource(),
            "full source survives sync");
        com.miaokatze.gtit.trade.NekoTradeConfig.save(com.miaokatze.gtit.trade.NekoTradeConfig.load());
        SimpleAssert.that(
            !find(
                com.miaokatze.gtit.trade.NekoTradeConfig.load()
                    .getTrades(),
                addition.getId()).isDefaultEntry(),
            "full repeat save retains false addition");
        System.out.println("PASS full page/trade config save-load-sync roundtrip");
    }

    private static NekoPageEntry page(int id, String preset) {
        NekoPageEntry page = new NekoPageEntry(id, "page", "minecraft:stone", 0, false);
        page.setSourcePresetId(preset);
        return page;
    }

    private static String source(JsonObject file, int index) {
        return file.getAsJsonArray("pages")
            .get(index)
            .getAsJsonObject()
            .get("source")
            .getAsString();
    }

    private static NekoTradeEntry find(List<NekoTradeEntry> trades, String id) {
        return trades.stream()
            .filter(entry -> id.equals(entry.getId()))
            .findFirst()
            .get();
    }

    private static void write(Path root, String relative, String json) throws Exception {
        Path path = root.resolve(relative);
        Files.createDirectories(path.getParent());
        Files.write(path, json.getBytes(StandardCharsets.UTF_8));
    }

    private static JsonObject read(Path root, String relative) throws Exception {
        return new JsonParser().parse(new String(Files.readAllBytes(root.resolve(relative)), StandardCharsets.UTF_8))
            .getAsJsonObject();
    }

    private static Map<Path, String> snapshot(Path root) throws Exception {
        Map<Path, String> result = new LinkedHashMap<>();
        try (java.util.stream.Stream<Path> paths = Files.walk(root)) {
            for (Path path : paths.filter(Files::isRegularFile)
                .sorted()
                .collect(java.util.stream.Collectors.toList()))
                result.put(
                    root.relativize(path),
                    java.util.Base64.getEncoder()
                        .encodeToString(Files.readAllBytes(path)));
        }
        return result;
    }
}

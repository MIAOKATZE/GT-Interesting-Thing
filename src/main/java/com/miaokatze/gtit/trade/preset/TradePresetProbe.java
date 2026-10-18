package com.miaokatze.gtit.trade.preset;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.miaokatze.gtit.trade.NekoPageEntry;
import com.miaokatze.gtit.trade.NekoTradeConfig;
import com.miaokatze.gtit.trade.NekoTradeEntry;
import com.miaokatze.gtit.trade.v2.NekoBqBridge;
import com.miaokatze.gtit.trade.v2.NekoBqCondition;

/** Isolated native-testlab fixture. Never swaps the live manager or reloads production registries. */
public final class TradePresetProbe {

    private TradePresetProbe() {}

    public static Map<String, Boolean> run(Path fixtureRoot, UUID contextId) throws Exception {
        Path root = fixtureRoot.toAbsolutePath()
            .normalize();
        Files.createDirectories(root);
        try (java.util.stream.Stream<Path> children = Files.list(root)) {
            if (children.findAny()
                .isPresent()) throw new IllegalArgumentException("预设探针要求空的隔离目录");
        }
        Map<String, Boolean> checks = new LinkedHashMap<>();
        Path pages = root.resolve("config/gtit/trade/pages.json");
        Files.createDirectories(pages.getParent());
        write(
            pages,
            "{\"version\":1,\"pages\":[{\"id\":7,\"name\":\"User\",\"iconItem\":\"minecraft:stone\",\"iconMeta\":0,\"iconNbt\":\"\",\"isDefault\":false,\"presetBuiltin\":false}]}");
        NekoTradeEntry entry = new NekoTradeEntry();
        entry.setId(contextId.toString());
        entry.setTabId(7);
        entry.getFromItems()
            .add(new NekoTradeEntry.ItemEntry("minecraft:stone", 0, 4));
        entry.getToItems()
            .add(new NekoTradeEntry.ItemEntry("minecraft:stonebrick", 0, 4));
        NekoTradeConfig.TabFileData userTab = new NekoTradeConfig.TabFileData();
        userTab.setTabId(7);
        userTab.getTrades()
            .add(entry);
        Path originalTab = root.resolve("config/gtit/trade/trades/tab_7.json");
        Files.createDirectories(originalTab.getParent());
        write(originalTab, TradePresetStore.GSON.toJson(userTab));
        byte[] userBefore = Files.readAllBytes(originalTab);

        TradePresetStore store = new TradePresetStore(root);
        TradePresetDefinition first = definition("probe-" + contextId, "First");
        store.save(store.revision(), first, Arrays.asList(7), false, ignored -> {});
        store.select(store.revision(), Arrays.asList(first.id), ignored -> {});
        int installed = store.installedTabs(first.id)
            .get(0);
        Path installedPath = root.resolve(TradePresetLayout.tabPath(installed));
        checks.put(
            "fresh_apply",
            Files.exists(installedPath) && Arrays.equals(userBefore, Files.readAllBytes(originalTab)));
        TradePresetStore readback = new TradePresetStore(root);
        checks.put("readback", readback.selected(first.id) && readback.revision() == store.revision());
        long beforeRepeat = store.revision();
        store.select(beforeRepeat - 1, Arrays.asList(first.id), ignored -> {});
        checks.put("idempotent", beforeRepeat == store.revision());

        TradePresetDefinition second = definition("probe-second-" + contextId, "Second");
        second.mutuallyExclusiveIds.add(first.id); // One-sided declaration is enough to reject both.
        store.save(store.revision(), second, Arrays.asList(7), false, ignored -> {});
        long beforeConflict = store.revision();
        boolean conflict = rejected(
            () -> store.select(beforeConflict, Arrays.asList(first.id, second.id), ignored -> {}));
        checks.put("exclusivity", conflict && beforeConflict == store.revision() && store.selected(first.id));
        checks.put(
            "revision",
            rejected(() -> store.select(beforeConflict - 1, new ArrayList<>(), ignored -> {}))
                && beforeConflict == store.revision());
        checks
            .put("path_traversal", rejected(() -> new PresetFileTransaction(root).checked("MiaoTrade/../escape.json")));

        TradePresetDefinition duplicate = definition(first.id, "Collision");
        checks.put(
            "id_collision",
            rejected(() -> store.save(store.revision(), duplicate, Arrays.asList(7), false, ignored -> {})));
        TradePresetDefinition exported = store.definitions()
            .stream()
            .filter(value -> value.id.equals(first.id))
            .findFirst()
            .get();
        checks.put(
            "export_roundtrip",
            exported.tabs.size() == 1 && exported.tabs.get(0).trades.size() == 1
                && exported.tabs.get(0).trades.get(0)
                    .getToItems()
                    .get(0)
                    .getAmount() == 4
                && exported.tabs.get(0).trades.get(0)
                    .getFromItems()
                    .get(0)
                    .getItem()
                    .equals("minecraft:stone"));

        TradePresetDefinition editedUnselected = definition(second.id, "Renamed without replacing content");
        editedUnselected.mutuallyExclusiveIds.add(first.id);
        store.save(store.revision(), editedUnselected, new ArrayList<>(), true, second.id, false, ignored -> {});
        checks.put(
            "metadata_edit_preserves_unselected",
            !store.selected(second.id) && editedUnselected.tabs.size() == 1
                && editedUnselected.tabs.get(0).trades.size() == 1
                && editedUnselected.tabs.get(0).trades.get(0)
                    .getToItems()
                    .get(0)
                    .getAmount() == 4);
        TradePresetDefinition copiedUnselected = definition("probe-copy-" + contextId, "Copy unused preset");
        store.save(store.revision(), copiedUnselected, new ArrayList<>(), false, second.id, false, ignored -> {});
        checks.put(
            "copy_preserves_unselected",
            copiedUnselected.tabs.size() == 1 && copiedUnselected.tabs.get(0).trades.get(0)
                .getFromItems()
                .get(0)
                .getItem()
                .equals("minecraft:stone"));

        TradePresetDefinition emptyCopy = definition("probe-empty-" + contextId, "Copy empty builtin");
        store.save(store.revision(), emptyCopy, new ArrayList<>(), false, "gtit-ae-basic", false, ignored -> {});
        checks.put("copy_empty_template", emptyCopy.tabs.isEmpty() && !store.selected(emptyCopy.id));
        TradePresetDefinition replacement = definition(emptyCopy.id, "Explicitly replace empty content");
        store.save(store.revision(), replacement, Arrays.asList(7), true, "gtit-ae-basic", true, ignored -> {});
        checks.put(
            "explicit_tabs_replace",
            replacement.tabs.size() == 1 && replacement.tabs.get(0).trades.size() == 1
                && Arrays.equals(userBefore, Files.readAllBytes(originalTab)));

        JsonObject changed = new JsonParser()
            .parse(new String(Files.readAllBytes(installedPath), StandardCharsets.UTF_8))
            .getAsJsonObject();
        changed.getAsJsonArray("trades")
            .get(0)
            .getAsJsonObject()
            .addProperty("cooldown", 321);
        write(installedPath, TradePresetStore.GSON.toJson(changed));
        String detachedId = changed.getAsJsonArray("trades")
            .get(0)
            .getAsJsonObject()
            .get("id")
            .getAsString();
        store.select(store.revision(), new ArrayList<>(), ignored -> {});
        Path detachedPath = root.resolve(TradePresetLayout.tabPath(1000));
        checks.put(
            "customedited_retained",
            !Files.exists(installedPath) && Files.exists(detachedPath)
                && new String(Files.readAllBytes(detachedPath), StandardCharsets.UTF_8).contains("321")
                && store.installedTabs(first.id)
                    .isEmpty());
        store.select(store.revision(), Arrays.asList(first.id), ignored -> {});
        int reinstalled = store.installedTabs(first.id)
            .get(0);
        String reinstalledJson = new String(
            Files.readAllBytes(root.resolve(TradePresetLayout.tabPath(reinstalled))),
            StandardCharsets.UTF_8);
        checks.put(
            "retained_id_isolation",
            reinstalled == installed && reinstalledJson.contains(detachedId)
                && !read(detachedPath).contains(detachedId));
        store.select(store.revision(), new ArrayList<>(), ignored -> {});
        checks.put(
            "unmodified_removed",
            !Files.exists(root.resolve(TradePresetLayout.tabPath(reinstalled))) && Files.exists(detachedPath)
                && Arrays.equals(userBefore, Files.readAllBytes(originalTab)));

        Path firstDefinitionPath = root.resolve("MiaoTrade/" + first.id + ".json");
        store.select(store.revision(), Arrays.asList(first.id), ignored -> {});
        Path beforeReloadPath = root.resolve(
            TradePresetLayout.tabPath(
                store.installedTabs(first.id)
                    .get(0)));
        byte[] beforeReloadTrades = Files.readAllBytes(beforeReloadPath);
        JsonObject refreshed = new JsonParser()
            .parse(new String(Files.readAllBytes(firstDefinitionPath), StandardCharsets.UTF_8))
            .getAsJsonObject();
        refreshed.getAsJsonArray("tabs")
            .get(0)
            .getAsJsonObject()
            .getAsJsonArray("trades")
            .get(0)
            .getAsJsonObject()
            .addProperty("cooldown", 451);
        write(firstDefinitionPath, refreshed.toString());
        long oldCatalogRevision = store.revision();
        store.reload();
        checks.put(
            "catalog_reload_preserves_applied_files_and_count",
            store.selectedIds()
                .equals(Arrays.asList(first.id)) && store.installedTradeCount() == 1
                && Arrays.equals(beforeReloadTrades, Files.readAllBytes(beforeReloadPath)));
        checks.put(
            "catalog_reload_revision",
            store.revision() > oldCatalogRevision
                && rejected(() -> store.select(oldCatalogRevision, Arrays.asList(first.id), ignored -> {})));
        store.select(store.revision(), Arrays.asList(first.id), ignored -> {});
        int changedSourceTab = store.installedTabs(first.id)
            .get(0);
        String changedSourceJson = new String(
            Files.readAllBytes(root.resolve(TradePresetLayout.tabPath(changedSourceTab))),
            StandardCharsets.UTF_8);
        checks.put("apply_changed_source", changedSourceJson.contains("451"));
        store.select(store.revision(), new ArrayList<>(), ignored -> {});

        UUID missingQuest = UUID
            .nameUUIDFromBytes(("gtit:missing-preset-probe:" + contextId).getBytes(StandardCharsets.UTF_8));
        TradePresetDefinition missingBq = definition("probe-bq-" + contextId, "Missing quest");
        TradePresetDefinition.Tab bqTab = new TradePresetDefinition.Tab();
        bqTab.page = new NekoPageEntry(1, "BQ", "minecraft:stone", 0, false);
        NekoTradeEntry gated = new NekoTradeEntry();
        gated.setBqQuestId(missingQuest.toString());
        gated.getToItems()
            .add(new NekoTradeEntry.ItemEntry("minecraft:stone", 0, 1));
        bqTab.trades.add(gated);
        missingBq.tabs.add(bqTab);
        boolean missingRejected = rejected(() -> TradePresetManager.validateAvailable(missingBq));
        checks.put(
            "missing_bq_failclosed",
            missingRejected && !NekoBqBridge.questExistsStrict(missingQuest)
                && !new NekoBqCondition(missingQuest, true).isSatisfied(contextId));

        // Simulate a process dying after writing one target but before deleting the rollback journal.
        Path recoveryTarget = root.resolve("MiaoTrade/probe-recovery.json");
        write(recoveryTarget, "new");
        Path newlyCreatedTarget = root.resolve("MiaoTrade/probe-new-after-crash.json");
        write(newlyCreatedTarget, "must disappear on recovery");
        JsonObject journal = new JsonObject();
        JsonObject before = new JsonObject();
        before.addProperty(
            "MiaoTrade/probe-recovery.json",
            Base64.getEncoder()
                .encodeToString("old".getBytes(StandardCharsets.UTF_8)));
        before.add("MiaoTrade/probe-new-after-crash.json", com.google.gson.JsonNull.INSTANCE);
        journal.add("before", before);
        Path journalPath = root.resolve("config/gtit/trade/preset_transaction.json");
        write(journalPath, journal.toString());
        new PresetFileTransaction(root).recover();
        checks.put(
            "transaction_recovery",
            "old".equals(new String(Files.readAllBytes(recoveryTarget), StandardCharsets.UTF_8))
                && !Files.exists(journalPath)
                && !Files.exists(newlyCreatedTarget));

        Path failedRoot = root.resolve("failed-transaction-fixture");
        Files.createDirectories(failedRoot.resolve("config/gtit/trade"));
        write(failedRoot.resolve("config/gtit/trade/trades"), "blocked parent");
        Map<String, byte[]> failedChanges = new LinkedHashMap<>();
        failedChanges.put("MiaoTrade/probe_rollback.json", "first write".getBytes(StandardCharsets.UTF_8));
        failedChanges.put("config/gtit/trade/trades/tab_1.json", "must fail".getBytes(StandardCharsets.UTF_8));
        checks.put(
            "transaction_failure_atomic",
            rejected(() -> new PresetFileTransaction(failedRoot).commit(failedChanges))
                && !Files.exists(failedRoot.resolve("MiaoTrade/probe_rollback.json"))
                && !Files.exists(failedRoot.resolve("config/gtit/trade/preset_transaction.json")));
        checkDefaultLayout(root.resolve("default-layout-fixture"), checks, entry);
        checkLegacyLayout(root.resolve("legacy-layout-fixture"), checks, entry, false);
        checkLegacyLayout(root.resolve("legacy-edited-fixture"), checks, entry, true);
        checkOrphanFiles(root.resolve("orphan-overlay-fixture"), checks, entry);
        return checks;
    }

    private static void checkOrphanFiles(Path root, Map<String, Boolean> checks, NekoTradeEntry source)
        throws Exception {
        prepareDefaultFixture(root, source);
        Path builtinOrphan = root.resolve(TradePresetLayout.tabPath(-100007));
        Files.createDirectories(builtinOrphan.getParent());
        write(builtinOrphan, "{\"tabId\":-100007,\"trades\":[],\"manualNote\":\"keep\"}");
        byte[] before = Files.readAllBytes(builtinOrphan);
        TradePresetStore store = new TradePresetStore(root);
        checks.put(
            "orphan_builtin_overlay_not_overwritten",
            rejected(() -> store.select(store.revision(), Arrays.asList("gtit-ae-basic"), ignored -> {}))
                && Arrays.equals(before, Files.readAllBytes(builtinOrphan))
                && !store.selected("gtit-ae-basic"));
        TradePresetDefinition custom = definition("orphan-slot-preset", "Custom slot");
        store.save(store.revision(), custom, Arrays.asList(1000), false, ignored -> {});
        int candidate = TradePresetLayout.customPageId(custom.id + ":1", new java.util.HashSet<>());
        Path customOrphan = root.resolve(TradePresetLayout.tabPath(candidate));
        write(customOrphan, "{\"tabId\":" + candidate + ",\"trades\":[],\"manualNote\":\"custom-keep\"}");
        before = Files.readAllBytes(customOrphan);
        store.select(store.revision(), Arrays.asList(custom.id), ignored -> {});
        checks.put(
            "orphan_custom_overlay_allocator_skips",
            store.installedTabs(custom.id)
                .get(0) != candidate && Arrays.equals(before, Files.readAllBytes(customOrphan)));
    }

    private static void checkDefaultLayout(Path root, Map<String, Boolean> checks, NekoTradeEntry source)
        throws Exception {
        prepareDefaultFixture(root, source);
        Path user = root.resolve(TradePresetLayout.tabPath(1000));
        byte[] before = Files.readAllBytes(user);
        TradePresetStore store = new TradePresetStore(root);
        store.select(store.revision(), Arrays.asList("gtit-ae-basic"), ignored -> {});
        int page = store.installedTabs("gtit-ae-basic")
            .get(0);
        Path file = root.resolve(TradePresetLayout.tabPath(page));
        NekoTradeConfig.TabFileData basic = TradePresetStore.GSON
            .fromJson(read(file), NekoTradeConfig.TabFileData.class);
        String stable = basic.getTrades()
            .get(0)
            .getId();
        checks.put(
            "default_namespace_no_user_id",
            page == -100007 && Files.exists(file)
                && !Files.exists(root.resolve(TradePresetLayout.tabPath(1001)))
                && Arrays.equals(before, Files.readAllBytes(user))
                && !read(root.resolve("config/gtit/trade/pages.json")).contains("-100007"));
        checks.put(
            "default_series_and_gate",
            stable.startsWith("MIAO100") && stable.length() <= 128
                && basic.getTrades()
                    .get(0)
                    .isDefaultEntry()
                && basic.getTrades()
                    .get(0)
                    .isPresetBuiltin()
                && NekoTradeConfig.isBuiltInDefaultEntry(
                    basic.getTrades()
                        .get(0)));
        checks.put(
            "negative_page_category_visible",
            com.miaokatze.gtit.trade.v2.NekoTradeCategory.ofTabId(page)
                .getTabId() == page
                && !com.miaokatze.gtit.trade.v2.NekoTradeCategory.ofTabId(page)
                    .isUnknown()
                && com.miaokatze.gtit.trade.v2.NekoTradeCategory.ofTabId(-1)
                    .isFavourites());
        store.select(store.revision(), Arrays.asList("gtit-ae-advanced"), ignored -> {});
        int advanced = store.installedTabs("gtit-ae-advanced")
            .get(0);
        checks.put(
            "default_switch_replaces_managed_files",
            advanced == -200007 && !Files.exists(file)
                && read(root.resolve(TradePresetLayout.tabPath(advanced))).contains("MIAO200")
                && Arrays.equals(before, Files.readAllBytes(user)));
        store.select(store.revision(), Arrays.asList("gtit-ae-basic"), ignored -> {});
        NekoTradeConfig.TabFileData again = TradePresetStore.GSON
            .fromJson(read(file), NekoTradeConfig.TabFileData.class);
        checks.put(
            "default_identity_stable_across_switch",
            stable.equals(
                again.getTrades()
                    .get(0)
                    .getId()));

        // A saved active definition applies on the next selection while keeping its fixed series identity.
        Path definition = root.resolve("MiaoTrade/gtit-ae-basic.json");
        TradePresetDefinition changed = TradePresetStore.GSON.fromJson(read(definition), TradePresetDefinition.class);
        changed.tabs.get(0).trades.get(0)
            .setCooldown(731);
        write(definition, TradePresetStore.GSON.toJson(changed));
        store.reload();
        store.select(store.revision(), Arrays.asList("gtit-ae-basic"), ignored -> {});
        again = TradePresetStore.GSON.fromJson(read(file), NekoTradeConfig.TabFileData.class);
        checks.put(
            "default_source_refresh_stable_id",
            again.getTrades()
                .get(0)
                .getCooldown() == 731 && stable.equals(
                    again.getTrades()
                        .get(0)
                        .getId()));

        again.getTrades()
            .get(0)
            .setCooldown(732);
        write(file, TradePresetStore.GSON.toJson(again));
        store.select(store.revision(), new ArrayList<>(), ignored -> {});
        Path detachedFile = root.resolve(TradePresetLayout.tabPath(1001));
        NekoTradeConfig.TabFileData detached = TradePresetStore.GSON
            .fromJson(read(detachedFile), NekoTradeConfig.TabFileData.class);
        NekoTradeEntry retained = detached.getTrades()
            .get(0);
        checks.put(
            "edited_default_detaches_to_user",
            !Files.exists(file) && retained.getCooldown() == 732
                && retained.getTabId() == 1001
                && !retained.isDefaultEntry()
                && !retained.isPresetBuiltin()
                && !retained.getId()
                    .startsWith("MIAO")
                && !NekoTradeConfig.isBuiltInDefaultEntry(retained)
                && Arrays.equals(before, Files.readAllBytes(user)));
    }

    private static void checkLegacyLayout(Path root, Map<String, Boolean> checks, NekoTradeEntry source, boolean edited)
        throws Exception {
        prepareDefaultFixture(root, source);
        NekoPageEntry page = new NekoPageEntry(1001, "Old default", "minecraft:stone", 0, false);
        page.setPresetBuiltin(true);
        JsonObject pages = new JsonParser().parse(read(root.resolve("config/gtit/trade/pages.json")))
            .getAsJsonObject();
        pages.getAsJsonArray("pages")
            .add(TradePresetStore.GSON.toJsonTree(page));
        write(root.resolve("config/gtit/trade/pages.json"), pages.toString());
        NekoTradeConfig.TabFileData old = new NekoTradeConfig.TabFileData();
        old.setTabId(1001);
        NekoTradeEntry entry = TradePresetStore.GSON
            .fromJson(TradePresetStore.GSON.toJson(source), NekoTradeEntry.class);
        entry.setTabId(1001);
        entry.setPresetBuiltin(true);
        old.getTrades()
            .add(entry);
        JsonObject owned = new JsonObject();
        owned.addProperty("presetId", "gtit-ae-basic");
        owned.addProperty("tabId", 1001);
        owned.add("page", TradePresetStore.GSON.toJsonTree(page));
        owned.add("trades", TradePresetStore.GSON.toJsonTree(old));
        JsonObject state = new JsonObject();
        state.addProperty("revision", 17);
        state.add("selectedIds", TradePresetStore.GSON.toJsonTree(Arrays.asList("gtit-ae-basic")));
        state.add("ownedTabs", TradePresetStore.GSON.toJsonTree(Arrays.asList(owned)));
        state.add("appliedFingerprints", new JsonObject());
        write(root.resolve("config/gtit/trade/preset_state.json"), state.toString());
        if (edited) entry.setCooldown(941);
        Path legacy = root.resolve(TradePresetLayout.tabPath(1001));
        write(legacy, TradePresetStore.GSON.toJson(old));
        TradePresetStore store = new TradePresetStore(root);
        checks.put(
            edited ? "legacy_edited_preserved" : "legacy_unmodified_migrated",
            store.selected("gtit-ae-basic") && store.installedTabs("gtit-ae-basic")
                .equals(Arrays.asList(-100007))
                && Files.exists(root.resolve(TradePresetLayout.tabPath(-100007)))
                && (edited ? read(legacy).contains("941") && !read(legacy).contains("\"presetBuiltin\": true")
                    : !Files.exists(legacy)));
    }

    private static void prepareDefaultFixture(Path root, NekoTradeEntry source) throws Exception {
        Files.createDirectories(root.resolve("config/gtit/trade/trades"));
        Files.createDirectories(root.resolve("MiaoTrade"));
        write(
            root.resolve("config/gtit/trade/pages.json"),
            "{\"version\":1,\"pages\":[{\"id\":1000,\"name\":\"Existing user page\"}]}");
        NekoTradeConfig.TabFileData user = new NekoTradeConfig.TabFileData();
        user.setTabId(1000);
        user.getTrades()
            .add(source);
        write(root.resolve(TradePresetLayout.tabPath(1000)), TradePresetStore.GSON.toJson(user));
        for (String id : TradePresetStore.BUILTIN_IDS) {
            TradePresetDefinition definition = definition(id, id);
            TradePresetDefinition.Tab tab = new TradePresetDefinition.Tab();
            tab.page = new NekoPageEntry(7, "Default", "minecraft:stone", 0, false);
            tab.trades.add(source);
            definition.tabs.add(tab);
            write(root.resolve("MiaoTrade/" + id + ".json"), TradePresetStore.GSON.toJson(definition));
        }
    }

    private static String read(Path path) throws Exception {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    private static TradePresetDefinition definition(String id, String name) {
        TradePresetDefinition preset = new TradePresetDefinition();
        preset.id = id;
        preset.name = name;
        return preset;
    }

    private static void write(Path path, String text) throws Exception {
        Files.write(path, text.getBytes(StandardCharsets.UTF_8));
    }

    private static boolean rejected(CheckedOperation operation) {
        try {
            operation.run();
            return false;
        } catch (Exception expected) {
            return true;
        }
    }

    private interface CheckedOperation {

        void run() throws Exception;
    }
}

package com.miaokatze.gtit.trade.preset;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.google.gson.JsonElement;
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
        PresetFileTransaction paths = new PresetFileTransaction(root);
        String updateDirectory = "MiaoTrade/builtin_updates/" + UUID.nameUUIDFromBytes(
            contextId.toString()
                .getBytes(StandardCharsets.UTF_8))
            + "/";
        boolean builtinPathsAllowed = true;
        for (String id : TradePresetStore.BUILTIN_IDS)
            builtinPathsAllowed &= paths.checked(updateDirectory + id + ".json")
                .equals(root.resolve(updateDirectory + id + ".json"));
        checks.put(
            "builtin_update_paths_scoped",
            builtinPathsAllowed
                && rejected(() -> paths.checked("MiaoTrade/builtin_updates/not-a-uuid/gtit-ae-basic.json"))
                && rejected(() -> paths.checked(updateDirectory + "custom-preset.json"))
                && rejected(() -> paths.checked(updateDirectory + "nested/gtit-ae-basic.json"))
                && rejected(() -> paths.checked(updateDirectory + "../gtit-ae-basic.json")));

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

        TradePresetDefinition emptyCopy = definition("probe-builtin-copy-" + contextId, "Copy builtin contents");
        store.save(store.revision(), emptyCopy, new ArrayList<>(), false, "gtit-ae-basic", false, ignored -> {});
        checks.put(
            "copy_bundled_template",
            emptyCopy.tabs.size() == 7 && emptyCopy.tabs.stream()
                .mapToInt(tab -> tab.trades.size())
                .sum() == 123 && !store.selected(emptyCopy.id));
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
        checkBuiltinUpdates(root.resolve("builtin-update-fixture"), checks);
        return checks;
    }

    private static void checkBuiltinUpdates(Path root, Map<String, Boolean> checks) throws Exception {
        Files.createDirectories(root.resolve("MiaoTrade"));
        int[] counts = { 123, 239, 64 };
        int[] pageCounts = { 7, 12, 2 };
        int[] budgets = { 330, 1130, 98 };
        for (int index = 0; index < TradePresetStore.BUILTIN_IDS.size(); index++) {
            String id = TradePresetStore.BUILTIN_IDS.get(index);
            JsonElement bundled = new JsonParser()
                .parse(new String(TradePresetStore.bundledBytes(id, false), StandardCharsets.UTF_8));
            TradePresetDefinition definition = TradePresetStore.GSON.fromJson(bundled, TradePresetDefinition.class);
            TradePresetStore.validate(definition);
            checks.put(
                id + "_bundled_content_roundtrip",
                definition.tabs.size() == pageCounts[index] && definition.tabs.stream()
                    .mapToInt(tab -> tab.trades.size())
                    .sum() == counts[index]
                    && definition.tabs.stream()
                        .flatMap(tab -> tab.trades.stream())
                        .mapToInt(
                            trade -> trade.getCurrency()
                                .getAmount())
                        .sum() == budgets[index]
                    && containsFields(bundled, TradePresetStore.GSON.toJsonTree(definition)));
            checks.put(
                id + "_available_without_quest_unlock",
                definition.tabs.stream()
                    .flatMap(tab -> tab.trades.stream())
                    .allMatch(trade -> "".equals(trade.getBqQuestId())));
            Files.write(root.resolve("MiaoTrade/" + id + ".json"), TradePresetStore.bundledBytes(id, true));
        }
        // A v1 state with an already selected empty builtin must still require an explicit application.
        Files.createDirectories(root.resolve("config/gtit/trade"));
        write(root.resolve("config/gtit/trade/pages.json"), "{\"version\":1,\"pages\":[]}");
        write(
            root.resolve("config/gtit/trade/preset_state.json"),
            "{\"revision\":7,\"selectedIds\":[\"gtit-ae-basic\"]}");
        TradePresetStore upgraded = new TradePresetStore(root);
        checks.put(
            "original_empty_templates_upgraded_without_auto_apply",
            upgraded.definitions()
                .stream()
                .filter(definition -> TradePresetStore.BUILTIN_IDS.contains(definition.id))
                .mapToInt(
                    definition -> definition.tabs.stream()
                        .mapToInt(tab -> tab.trades.size())
                        .sum())
                .sum() == 426 && upgraded.selected("gtit-ae-basic")
                && upgraded.installedTradeCount() == 0
                && upgraded.builtinNotices()
                    .size() == 3);
        byte[] before = Files.readAllBytes(root.resolve("MiaoTrade/gtit-ae-basic.json"));
        long revision = upgraded.revision();
        TradePresetStore restarted = new TradePresetStore(root);
        checks.put(
            "builtin_upgrade_restart_idempotent",
            restarted.revision() == revision && restarted.builtinNotices()
                .isEmpty()
                && restarted.installedTradeCount() == 0
                && Arrays.equals(before, Files.readAllBytes(root.resolve("MiaoTrade/gtit-ae-basic.json"))));
        restarted.select(restarted.revision(), Arrays.asList("gtit-ae-basic"), ignored -> {});
        checks.put(
            "upgraded_selected_template_installs_only_on_apply",
            restarted.installedTradeCount() == 123 && restarted.installedTabs("gtit-ae-basic")
                .size() == 7);

        Path customized = root.resolve("customized");
        Files.createDirectories(customized.resolve("MiaoTrade"));
        Files.createDirectories(customized.resolve("config/gtit/trade"));
        write(customized.resolve("config/gtit/trade/pages.json"), "{\"version\":1,\"pages\":[]}");
        JsonObject changed = new JsonParser()
            .parse(new String(TradePresetStore.bundledBytes("gtit-ae-basic", true), StandardCharsets.UTF_8))
            .getAsJsonObject();
        changed.addProperty("name", "User renamed builtin");
        changed.addProperty("manualNote", "Preserve unknown user fields too");
        Path customPath = customized.resolve("MiaoTrade/gtit-ae-basic.json");
        write(customPath, changed.toString());
        byte[] userBefore = Files.readAllBytes(customPath);
        TradePresetStore custom = new TradePresetStore(customized);
        checks.put(
            "customized_builtin_preserved_and_update_offered",
            Arrays.equals(userBefore, Files.readAllBytes(customPath)) && custom.definitions()
                .stream()
                .anyMatch(definition -> definition.id.equals("gtit-ae-basic") && definition.tabs.isEmpty())
                && custom.builtinNotices()
                    .size() == 1
                && Files.exists(
                    customized.resolve(
                        "MiaoTrade/builtin_updates/"
                            + UUID.nameUUIDFromBytes(TradePresetStore.bundledBytes("gtit-ae-basic", false))
                            + "/gtit-ae-basic.json")));
        new TradePresetStore(customized);
        try (java.util.stream.Stream<Path> candidates = Files.walk(customized.resolve("MiaoTrade/builtin_updates"))) {
            checks.put(
                "customized_builtin_restart_preserves_bytes",
                Arrays.equals(userBefore, Files.readAllBytes(customPath)) && candidates.filter(Files::isRegularFile)
                    .count() == 1);
        }

        Path oldIcons = root.resolve("original-old-icons");
        Files.createDirectories(oldIcons.resolve("MiaoTrade"));
        Files.createDirectories(oldIcons.resolve("config/gtit/trade"));
        write(oldIcons.resolve("config/gtit/trade/pages.json"), "{\"version\":1,\"pages\":[]}");
        for (String id : Arrays.asList("gtit-ae-basic", "gtit-ae-advanced")) {
            JsonObject original = new JsonParser()
                .parse(new String(TradePresetStore.bundledBytes(id, true), StandardCharsets.UTF_8))
                .getAsJsonObject();
            original.getAsJsonObject("icon")
                .addProperty("item", id.equals("gtit-ae-basic") ? "minecraft:redstone" : "minecraft:comparator");
            write(oldIcons.resolve("MiaoTrade/" + id + ".json"), original.toString());
        }
        TradePresetStore legacy = new TradePresetStore(oldIcons);
        checks.put(
            "known_original_icon_versions_upgraded",
            legacy.builtinNotices()
                .size() == 2
                && legacy.definitions()
                    .stream()
                    .mapToInt(
                        definition -> definition.tabs.stream()
                            .mapToInt(tab -> tab.trades.size())
                            .sum())
                    .sum() == 426);
        checkBuiltinQuestRemoval(root.resolve("quest-removal"), checks);
    }

    private static void checkBuiltinQuestRemoval(Path root, Map<String, Boolean> checks) throws Exception {
        Files.createDirectories(root.resolve("config/gtit/trade"));
        write(root.resolve("config/gtit/trade/pages.json"), "{\"version\":1,\"pages\":[]}");
        TradePresetStore before = new TradePresetStore(root);
        Map<String, JsonObject> expected = new LinkedHashMap<>();
        for (String id : TradePresetStore.BUILTIN_IDS) {
            JsonObject old = new JsonParser()
                .parse(new String(TradePresetStore.bundledBytes(id, false), StandardCharsets.UTF_8))
                .getAsJsonObject();
            old.addProperty("manualNote", "Preserve unknown root fields");
            JsonObject firstTab = old.getAsJsonArray("tabs")
                .get(0)
                .getAsJsonObject();
            firstTab.addProperty("manualTabNote", "Preserve unknown tab fields");
            JsonObject first = firstTab.getAsJsonArray("trades")
                .get(0)
                .getAsJsonObject();
            first.addProperty("manualTradeNote", "Preserve unknown trade fields");
            first.getAsJsonObject("currency")
                .addProperty("amount", 13);
            first.getAsJsonArray("toItems")
                .get(0)
                .getAsJsonObject()
                .addProperty("amount", 37);
            for (JsonElement tab : old.getAsJsonArray("tabs")) for (JsonElement trade : tab.getAsJsonObject()
                .getAsJsonArray("trades"))
                trade.getAsJsonObject()
                    .addProperty("bqQuestId", "00000000-0000-0000-0000-0000000000b0");
            // Exercise Windows CRLF input rather than comparing exact file bytes to a shipped hash.
            write(
                root.resolve("MiaoTrade/" + id + ".json"),
                TradePresetStore.GSON.toJson(old)
                    .replace("\n", "\r\n"));
            expected.put(id, old);
        }
        JsonObject custom = new JsonParser().parse(
            expected.get("gtit-ae-basic")
                .toString())
            .getAsJsonObject();
        custom.addProperty("id", "user-quest-preserved");
        Path customPath = root.resolve("MiaoTrade/user-quest-preserved.json");
        write(customPath, custom.toString());
        byte[] customBefore = Files.readAllBytes(customPath);
        before.reload();
        List<String> selected = Arrays.asList("gtit-ae-basic", "gtit-base-decoration");
        before.select(before.revision(), selected, ignored -> {});
        Map<Path, byte[]> installedBefore = new LinkedHashMap<>();
        boolean hadInstalledGates = true;
        for (String id : selected) for (Integer tab : before.installedTabs(id)) {
            Path file = root.resolve(TradePresetLayout.tabPath(tab));
            installedBefore.put(file, Files.readAllBytes(file));
            NekoTradeConfig.TabFileData installed = TradePresetStore.GSON
                .fromJson(read(file), NekoTradeConfig.TabFileData.class);
            hadInstalledGates &= installed.getTrades()
                .stream()
                .allMatch(
                    trade -> !trade.getBqQuestId()
                        .isEmpty());
        }
        for (JsonObject definition : expected.values())
            for (JsonElement tab : definition.getAsJsonArray("tabs")) for (JsonElement trade : tab.getAsJsonObject()
                .getAsJsonArray("trades"))
                trade.getAsJsonObject()
                    .addProperty("bqQuestId", "");

        TradePresetStore migrated = new TradePresetStore(root);
        boolean fieldsPreserved = true;
        Map<Path, byte[]> migratedFiles = new LinkedHashMap<>();
        for (Map.Entry<String, JsonObject> entry : expected.entrySet()) {
            Path file = root.resolve("MiaoTrade/" + entry.getKey() + ".json");
            fieldsPreserved &= entry.getValue()
                .equals(new JsonParser().parse(read(file)));
            migratedFiles.put(file, Files.readAllBytes(file));
        }
        boolean installedUntouched = true;
        for (Map.Entry<Path, byte[]> entry : installedBefore.entrySet())
            installedUntouched &= Arrays.equals(entry.getValue(), Files.readAllBytes(entry.getKey()));
        checks.put(
            "builtin_quest_removal_preserves_other_fields_and_installed_pages",
            fieldsPreserved && hadInstalledGates
                && installedUntouched
                && migrated.selectedIds()
                    .equals(selected)
                && migrated.installedTradeCount() == 187);
        checks
            .put("custom_preset_quest_binding_untouched", Arrays.equals(customBefore, Files.readAllBytes(customPath)));

        long revision = migrated.revision();
        TradePresetStore restarted = new TradePresetStore(root);
        boolean unchanged = restarted.revision() == revision;
        for (Map.Entry<Path, byte[]> entry : migratedFiles.entrySet())
            unchanged &= Arrays.equals(entry.getValue(), Files.readAllBytes(entry.getKey()));
        checks.put("builtin_quest_removal_restart_idempotent", unchanged);
        restarted.select(restarted.revision(), selected, ignored -> {});
        boolean installedUngated = true;
        for (Path file : installedBefore.keySet()) {
            NekoTradeConfig.TabFileData installed = TradePresetStore.GSON
                .fromJson(read(file), NekoTradeConfig.TabFileData.class);
            installedUngated &= installed.getTrades()
                .stream()
                .allMatch(trade -> "".equals(trade.getBqQuestId()));
        }
        checks.put(
            "builtin_quest_removal_applied_explicitly",
            installedUngated && restarted.selectedIds()
                .equals(selected)
                && restarted.installedTradeCount() == 187
                && Arrays.equals(customBefore, Files.readAllBytes(customPath)));
    }

    /** Every source field must survive the actual DTO round trip; additional default flags are allowed. */
    private static boolean containsFields(JsonElement source, JsonElement actual) {
        if (source.isJsonObject()) {
            if (!actual.isJsonObject()) return false;
            for (Map.Entry<String, JsonElement> field : source.getAsJsonObject()
                .entrySet()) {
                if (!actual.getAsJsonObject()
                    .has(field.getKey())
                    || !containsFields(
                        field.getValue(),
                        actual.getAsJsonObject()
                            .get(field.getKey())))
                    return false;
            }
            return true;
        }
        if (source.isJsonArray()) {
            if (!actual.isJsonArray() || source.getAsJsonArray()
                .size()
                != actual.getAsJsonArray()
                    .size())
                return false;
            for (int index = 0; index < source.getAsJsonArray()
                .size(); index++)
                if (!containsFields(
                    source.getAsJsonArray()
                        .get(index),
                    actual.getAsJsonArray()
                        .get(index)))
                    return false;
            return true;
        }
        return source.equals(actual);
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

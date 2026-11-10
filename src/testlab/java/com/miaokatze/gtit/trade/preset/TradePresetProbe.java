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
        for (String id : TradePresetStore.BUILTIN_IDS.subList(0, 3))
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
            emptyCopy.tabs.size() == DefaultTradeSources.bundled("gtit-ae-basic").tabs.size() && emptyCopy.tabs.stream()
                .mapToInt(tab -> tab.trades.size())
                .sum()
                == DefaultTradeSources.bundled("gtit-ae-basic").tabs.stream()
                    .mapToInt(tab -> tab.trades.size())
                    .sum()
                && !store.selected(emptyCopy.id));
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
                .equals(Arrays.asList(DefaultTradeSources.BASE_ID, first.id))
                && store.installedTradeCount() == 1 + DefaultTradeSources.trades(root)
                    .size()
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
        checkDefaultStorage(root.resolve("jar-default-fixture"), checks, entry);
        return checks;
    }

    private static void checkDefaultStorage(Path root, Map<String, Boolean> checks, NekoTradeEntry source)
        throws Exception {
        Files.createDirectories(root.resolve("config/gtit/trade/trades"));
        Files.createDirectories(root.resolve("MiaoTrade"));
        Path userPath = root.resolve("config/gtit/trade/trades/tab_1.json");
        JsonObject mixed = new JsonObject();
        mixed.addProperty("version", 1);
        mixed.addProperty("tabId", 1);
        mixed.addProperty("customTopLevel", "preserve exactly");
        com.google.gson.JsonArray trades = new com.google.gson.JsonArray();
        JsonObject custom = TradePresetStore.GSON.toJsonTree(source)
            .getAsJsonObject();
        custom.addProperty("tabId", 1);
        custom.addProperty("customUnknownField", "preserve this too");
        trades.add(custom);
        JsonObject unusualCustom = new JsonParser().parse(custom.toString())
            .getAsJsonObject();
        unusualCustom.addProperty("id", "MIAO987654321");
        trades.add(unusualCustom);
        JsonObject legacyDefault = TradePresetStore.GSON.toJsonTree(source)
            .getAsJsonObject();
        legacyDefault.addProperty("id", "MIAO1");
        legacyDefault.addProperty("tabId", 1);
        trades.add(legacyDefault);
        mixed.add("trades", trades);
        write(userPath, mixed.toString());
        byte[] old = Files.readAllBytes(userPath);
        write(
            root.resolve("config/gtit/trade/pages.json"),
            "{\"pages\":[{\"id\":1,\"name\":\"custom mixed page\",\"isDefault\":true}]}");
        for (String id : TradePresetStore.BUILTIN_IDS.subList(0, 3)) write(
            root.resolve("MiaoTrade/" + id + ".json"),
            new String(TradePresetStore.bundledBytes(id, false), StandardCharsets.UTF_8));
        Path imported = root.resolve("MiaoTrade/imported-copy.json");
        TradePresetDefinition definition = DefaultTradeSources.bundled("gtit-ae-basic");
        definition.id = "imported-copy";
        write(imported, TradePresetStore.GSON.toJson(definition));
        byte[] importedBefore = Files.readAllBytes(imported);
        TradePresetStore store = new TradePresetStore(root);
        JsonObject migrated = new JsonParser().parse(read(userPath))
            .getAsJsonObject();
        checks.put(
            "migration_mixed_tab_preserves_custom_path_and_unknown_fields",
            migrated.getAsJsonArray("trades")
                .size() == 2 && custom.equals(
                    migrated.getAsJsonArray("trades")
                        .get(0))
                && migrated.get("customTopLevel")
                    .equals(mixed.get("customTopLevel")));
        checks.put(
            "migration_does_not_sweep_arbitrary_miao_custom_ids",
            unusualCustom.equals(
                migrated.getAsJsonArray("trades")
                    .get(1)));
        checks.put(
            "migration_backup_and_marker",
            Arrays.equals(
                old,
                Files.readAllBytes(
                    root.resolve("config/gtit/trade/default_migration_backup/config_gtit_trade_trades_tab_1.json")))
                && Files.exists(root.resolve("config/gtit/trade/default_storage_v3.json")));
        checks.put(
            "migration_only_builtin_catalog_removed",
            !Files.exists(root.resolve("MiaoTrade/gtit-ae-basic.json"))
                && Arrays.equals(importedBefore, Files.readAllBytes(imported)));
        checks.put(
            "base_fixed_special_namespace",
            store.selected(DefaultTradeSources.BASE_ID) && store.installedTabs(DefaultTradeSources.BASE_ID)
                .contains(-400001)
                && DefaultTradeSources.trades(root)
                    .stream()
                    .allMatch(
                        trade -> trade.getId()
                            .startsWith("MIAO400")));
        store.select(store.revision(), Arrays.asList("gtit-ae-basic"), ignored -> {});
        List<NekoTradeEntry> defaults = DefaultTradeSources.trades(root);
        DefaultTradeSources.saveTrades(root, defaults);
        checks.put(
            "unchanged_defaults_do_not_materialize",
            !Files.exists(root.resolve("config/gtit/trade/default_overrides/gtit-base.json"))
                && !Files.exists(root.resolve("config/gtit/trade/default_overrides/gtit-ae-basic.json"))
                && !Files.exists(root.resolve(TradePresetLayout.tabPath(-100007))));
        NekoTradeEntry changed = defaults.stream()
            .filter(
                trade -> TradePresetLayout.builtinId(trade.getTabId())
                    .equals("gtit-ae-basic"))
            .findFirst()
            .get();
        String changedId = changed.getId();
        changed.setCooldown(823);
        DefaultTradeSources.saveTrades(root, defaults);
        Path override = root.resolve("config/gtit/trade/default_overrides/gtit-ae-basic.json");
        checks.put(
            "edit_only_target_default_group_written",
            Files.exists(override)
                && !Files.exists(root.resolve("config/gtit/trade/default_overrides/gtit-base.json")));
        defaults = DefaultTradeSources.trades(root);
        defaults.removeIf(
            trade -> trade.getId()
                .equals(changedId));
        DefaultTradeSources.saveTrades(root, defaults);
        checks.put(
            "deleted_default_trade_persists",
            DefaultTradeSources.trades(root)
                .stream()
                .noneMatch(
                    trade -> trade.getId()
                        .equals(changedId)));
        List<NekoPageEntry> pages = DefaultTradeSources.pages(root);
        pages.stream()
            .filter(page -> page.getId() == -100007)
            .findFirst()
            .get()
            .setName("Edited default tab");
        DefaultTradeSources.savePages(root, pages);
        checks.put(
            "renamed_default_page_persists_with_source",
            DefaultTradeSources.pages(root)
                .stream()
                .anyMatch(
                    page -> page.getId() == -100007 && page.getName()
                        .equals("Edited default tab")
                        && page.getSourcePresetId()
                            .equals("gtit-ae-basic")
                        && !page.getSourcePresetIconItem()
                            .isEmpty()));
        byte[] overrideBefore = Files.readAllBytes(override);
        new TradePresetStore(root);
        checks.put(
            "migration_restart_preserves_new_overrides",
            Arrays.equals(overrideBefore, Files.readAllBytes(override))
                && Arrays.equals(importedBefore, Files.readAllBytes(imported)));
        store.select(store.revision(), Arrays.asList("gtit-base-decoration"), ignored -> {});
        store.select(store.revision(), Arrays.asList("gtit-ae-basic"), ignored -> {});
        checks.put(
            "selection_changes_preserve_other_group_edits",
            Arrays.equals(overrideBefore, Files.readAllBytes(override)));
        store.select(store.revision(), Arrays.asList("imported-copy", "gtit-ae-basic"), ignored -> {});
        int customPage = store.installedTabs("imported-copy")
            .get(0);
        NekoPageEntry importedPage = store.pages()
            .stream()
            .filter(page -> page.getId() == customPage)
            .findFirst()
            .get();
        checks.put(
            "imported_preset_is_custom_with_synced_source",
            customPage <= -500000 && !importedPage.isPresetBuiltin()
                && importedPage.getSourcePresetId()
                    .equals("imported-copy")
                && !importedPage.getSourcePresetName()
                    .isEmpty()
                && !importedPage.getSourcePresetIconItem()
                    .isEmpty());
        byte[] customInstalled = Files.readAllBytes(root.resolve(TradePresetLayout.tabPath(customPage)));
        store.resetDefaults();
        checks.put(
            "reset_defaults_preserves_custom_and_selection",
            !Files.exists(override)
                && Arrays
                    .equals(customInstalled, Files.readAllBytes(root.resolve(TradePresetLayout.tabPath(customPage))))
                && Arrays.equals(importedBefore, Files.readAllBytes(imported))
                && store.selected("imported-copy")
                && store.selected("gtit-ae-basic")
                && DefaultTradeSources.trades(root)
                    .stream()
                    .anyMatch(
                        trade -> trade.getId()
                            .equals(changedId)));
        List<NekoTradeEntry> beforeUpdate = DefaultTradeSources.trades(root);
        beforeUpdate.stream()
            .filter(trade -> trade.getTabId() == -400001)
            .findFirst()
            .get()
            .setCooldown(555);
        beforeUpdate.stream()
            .filter(trade -> trade.getTabId() == -100007)
            .findFirst()
            .get()
            .setCooldown(556);
        DefaultTradeSources.saveTrades(root, beforeUpdate);
        Path baseOverride = root.resolve("config/gtit/trade/default_overrides/gtit-base.json");
        byte[] baseOverrideBefore = Files.readAllBytes(baseOverride);
        Path stateFile = root.resolve("config/gtit/trade/preset_state.json");
        JsonObject staleState = new JsonParser().parse(read(stateFile))
            .getAsJsonObject();
        staleState.getAsJsonObject("appliedFingerprints")
            .addProperty("gtit-ae-basic", "previous jar revision");
        write(stateFile, staleState.toString());
        TradePresetStore updated = new TradePresetStore(root);
        updated.select(updated.revision(), updated.selectedIds(), ignored -> {});
        checks.put(
            "apply_jar_update_clears_only_changed_selected_default_group",
            !Files.exists(override) && Arrays.equals(baseOverrideBefore, Files.readAllBytes(baseOverride))
                && Arrays
                    .equals(customInstalled, Files.readAllBytes(root.resolve(TradePresetLayout.tabPath(customPage)))));
        store = updated;
        store.resetDefaults();
        checks.put(
            "ordinary_custom_trade_byte_content_preserved_after_reset",
            read(userPath).equals(TradePresetStore.GSON.toJson(migrated)));
        List<NekoPageEntry> removedPages = DefaultTradeSources.pages(root);
        removedPages.removeIf(page -> page.getId() == -100007);
        DefaultTradeSources.savePages(root, removedPages);
        checks.put(
            "removed_default_page_and_trades_remain_deleted_until_reset",
            DefaultTradeSources.pages(root)
                .stream()
                .noneMatch(page -> page.getId() == -100007)
                && DefaultTradeSources.trades(root)
                    .stream()
                    .noneMatch(trade -> trade.getTabId() == -100007));
        store.resetDefaults();
        List<NekoTradeEntry> movedTrades = DefaultTradeSources.trades(root);
        NekoTradeEntry moved = movedTrades.stream()
            .filter(trade -> trade.getTabId() == -400001)
            .findFirst()
            .get();
        String originalMovedId = moved.getId();
        moved.setTabId(1);
        DefaultTradeSources.saveTrades(root, movedTrades);
        checks.put(
            "moved_default_becomes_independent_custom_identity",
            !originalMovedId.equals(moved.getId()) && !moved.isDefaultEntry()
                && !moved.isPresetBuiltin()
                && DefaultTradeSources.trades(root)
                    .stream()
                    .noneMatch(
                        trade -> trade.getId()
                            .equals(originalMovedId)));
        store.resetDefaults();
        checks.put(
            "reset_restores_original_without_colliding_with_moved_custom",
            DefaultTradeSources.trades(root)
                .stream()
                .anyMatch(
                    trade -> trade.getId()
                        .equals(originalMovedId))
                && !originalMovedId.equals(moved.getId()));
        Path legacyRoot = root.resolve("old-single-file-fixture");
        Files.createDirectories(legacyRoot.resolve("config/gtit"));
        JsonObject legacyData = new JsonObject();
        legacyData.add("trades", trades);
        byte[] legacyBytes = DefaultTradeSources.bytes(legacyData);
        Files.write(legacyRoot.resolve("config/gtit/nekovm_trades.json"), legacyBytes);
        write(
            legacyRoot.resolve("config/gtit/nekovm_pages.json"),
            "{\"pages\":[{\"id\":1,\"name\":\"legacy custom page\"}]}");
        new TradePresetStore(legacyRoot);
        JsonObject split = new JsonParser().parse(read(legacyRoot.resolve("config/gtit/trade/trades/tab_1.json")))
            .getAsJsonObject();
        checks.put(
            "single_file_migration_keeps_custom_and_retires_old_defaults",
            split.getAsJsonArray("trades")
                .size() == 2 && custom.equals(
                    split.getAsJsonArray("trades")
                        .get(0))
                && !Files.exists(legacyRoot.resolve("config/gtit/nekovm_trades.json"))
                && Arrays.equals(
                    legacyBytes,
                    Files.readAllBytes(
                        legacyRoot
                            .resolve("config/gtit/trade/default_migration_backup/config_gtit_nekovm_trades.json"))));
        Path mixedRoot = root.resolve("legacy-with-default-split-fixture");
        Files.createDirectories(mixedRoot.resolve("config/gtit/trade/trades"));
        Files.write(mixedRoot.resolve("config/gtit/nekovm_trades.json"), legacyBytes);
        JsonObject pureDefaults = new JsonObject();
        pureDefaults.addProperty("tabId", 1);
        pureDefaults.add("trades", TradePresetStore.GSON.toJsonTree(Arrays.asList(legacyDefault)));
        write(mixedRoot.resolve("config/gtit/trade/trades/tab_1.json"), pureDefaults.toString());
        new TradePresetStore(mixedRoot);
        checks.put(
            "legacy_cannot_reimport_after_pure_default_split_cleanup",
            !Files.exists(mixedRoot.resolve("config/gtit/nekovm_trades.json"))
                && !Files.exists(mixedRoot.resolve("config/gtit/trade/trades/tab_1.json"))
                && Arrays.equals(
                    legacyBytes,
                    Files.readAllBytes(
                        mixedRoot
                            .resolve("config/gtit/trade/default_migration_backup/config_gtit_nekovm_trades.json"))));
        Path freshRoot = root.resolve("fresh-empty-custom-fixture");
        TradePresetStore fresh = new TradePresetStore(freshRoot);
        checks.put(
            "fresh_library_does_not_create_custom_fallback_pages",
            fresh.pages()
                .stream()
                .allMatch(NekoPageEntry::isPresetBuiltin)
                && new JsonParser().parse(read(freshRoot.resolve("config/gtit/trade/pages.json")))
                    .getAsJsonObject()
                    .getAsJsonArray("pages")
                    .size() == 0);
        checkOldOwnedLayout(root.resolve("old-owned-fixture"), checks, source);
    }

    private static void checkOldOwnedLayout(Path root, Map<String, Boolean> checks, NekoTradeEntry source)
        throws Exception {
        Files.createDirectories(root.resolve("config/gtit/trade/trades"));
        Files.createDirectories(root.resolve(TradePresetLayout.TRADES_PATH));
        Files.createDirectories(root.resolve("MiaoTrade"));
        TradePresetDefinition customDefinition = definition("legacy-custom", "Legacy custom group");
        TradePresetDefinition.Tab customTab = new TradePresetDefinition.Tab();
        customTab.page = new NekoPageEntry(1, "Local", "minecraft:stone", 0, false);
        customTab.trades.add(source);
        customDefinition.tabs.add(customTab);
        write(root.resolve("MiaoTrade/legacy-custom.json"), TradePresetStore.GSON.toJson(customDefinition));
        JsonObject defaultEntry = TradePresetStore.GSON.toJsonTree(source)
            .getAsJsonObject();
        defaultEntry.addProperty(
            "id",
            UUID.randomUUID()
                .toString());
        defaultEntry.addProperty("tabId", 11);
        JsonObject extra = TradePresetStore.GSON.toJsonTree(source)
            .getAsJsonObject();
        extra.addProperty("tabId", 11);
        extra.addProperty("unknownCustom", "preserved");
        JsonObject oldDefaultTrades = new JsonObject();
        oldDefaultTrades.addProperty("tabId", 11);
        oldDefaultTrades.add("trades", TradePresetStore.GSON.toJsonTree(Arrays.asList(defaultEntry)));
        JsonObject actualTrades = new JsonParser().parse(oldDefaultTrades.toString())
            .getAsJsonObject();
        actualTrades.getAsJsonArray("trades")
            .add(extra);
        write(root.resolve(TradePresetLayout.tabPath(11)), actualTrades.toString());
        JsonObject oldCustomTrade = TradePresetStore.GSON.toJsonTree(source)
            .getAsJsonObject();
        oldCustomTrade.addProperty("id", "MIAO900123456789");
        oldCustomTrade.addProperty("tabId", -400001);
        oldCustomTrade.addProperty("presetBuiltin", true);
        oldCustomTrade.addProperty("defaultEntry", true);
        JsonObject customTrades = new JsonObject();
        customTrades.addProperty("tabId", -400001);
        customTrades.add("trades", TradePresetStore.GSON.toJsonTree(Arrays.asList(oldCustomTrade)));
        write(root.resolve(TradePresetLayout.tabPath(-400001)), customTrades.toString());
        NekoPageEntry oldDefaultPage = new NekoPageEntry(11, "Old default with custom", "minecraft:stone", 0, false);
        NekoPageEntry oldCustomPage = new NekoPageEntry(-400001, "Old custom", "minecraft:stone", 0, false);
        oldCustomPage.setPresetBuiltin(true);
        JsonObject pages = new JsonObject();
        pages.add("pages", TradePresetStore.GSON.toJsonTree(Arrays.asList(oldDefaultPage)));
        write(root.resolve("config/gtit/trade/pages.json"), pages.toString());
        pages.add("pages", TradePresetStore.GSON.toJsonTree(Arrays.asList(oldCustomPage)));
        JsonObject orphanPage = TradePresetStore.GSON
            .toJsonTree(new NekoPageEntry(-200001, "orphan custom in default range", "minecraft:stone", 0, false))
            .getAsJsonObject();
        orphanPage.addProperty("unknownPageField", "keep page metadata");
        pages.getAsJsonArray("pages")
            .add(orphanPage);
        write(root.resolve(TradePresetLayout.PAGES_PATH), pages.toString());
        JsonObject orphanTrades = new JsonObject();
        orphanTrades.addProperty("tabId", -200001);
        orphanTrades.addProperty("unknownTop", "keep top metadata");
        JsonObject orphanEntry = new JsonParser().parse(extra.toString())
            .getAsJsonObject();
        orphanEntry.addProperty("tabId", -200001);
        orphanTrades.add("trades", TradePresetStore.GSON.toJsonTree(Arrays.asList(orphanEntry)));
        write(root.resolve(TradePresetLayout.tabPath(-200001)), orphanTrades.toString());
        List<JsonObject> owned = new ArrayList<>();
        for (int index = 0; index < 2; index++) {
            JsonObject owner = new JsonObject();
            owner.addProperty("presetId", index == 0 ? "gtit-ae-basic" : "legacy-custom");
            owner.addProperty("tabId", index == 0 ? 11 : -400001);
            owner.add("page", TradePresetStore.GSON.toJsonTree(index == 0 ? oldDefaultPage : oldCustomPage));
            owner.add("trades", index == 0 ? oldDefaultTrades : customTrades);
            owned.add(owner);
        }
        JsonObject state = new JsonObject();
        state.add("selectedIds", TradePresetStore.GSON.toJsonTree(Arrays.asList("gtit-ae-basic", "legacy-custom")));
        state.add("ownedTabs", TradePresetStore.GSON.toJsonTree(owned));
        JsonObject mapping = new JsonObject();
        mapping.addProperty("legacy-custom:1", -400001);
        state.add("pageIds", mapping);
        write(root.resolve("config/gtit/trade/preset_state.json"), state.toString());
        TradePresetStore migrated = new TradePresetStore(root);
        JsonObject preserved = new JsonParser().parse(read(root.resolve(TradePresetLayout.tabPath(11))))
            .getAsJsonObject();
        checks.put(
            "legacy_owned_default_keeps_new_custom_trade_and_positive_page",
            preserved.getAsJsonArray("trades")
                .size() == 1 && extra.equals(
                    preserved.getAsJsonArray("trades")
                        .get(0))
                && migrated.pages()
                    .stream()
                    .anyMatch(page -> page.getId() == 11 && !page.isPresetBuiltin()));
        int customPage = migrated.installedTabs("legacy-custom")
            .get(0);
        NekoTradeConfig.TabFileData installed = TradePresetStore.GSON
            .fromJson(read(root.resolve(TradePresetLayout.tabPath(customPage))), NekoTradeConfig.TabFileData.class);
        checks.put(
            "legacy_custom_namespace_migrates_away_from_base_and_clears_wrong_default_flags",
            customPage <= -500000 && installed.getTrades()
                .get(0)
                .getId()
                .equals("MIAO900123456789")
                && !installed.getTrades()
                    .get(0)
                    .isDefaultEntry()
                && !NekoTradeConfig.isBuiltInDefaultEntry(
                    installed.getTrades()
                        .get(0))
                && migrated.pages()
                    .stream()
                    .anyMatch(
                        page -> page.getId() == customPage && !page.isPresetBuiltin()
                            && page.getSourcePresetId()
                                .equals("legacy-custom")));
        JsonObject detached = new JsonParser().parse(read(root.resolve(TradePresetLayout.tabPath(1000))))
            .getAsJsonObject();
        checks.put(
            "legacy_default_range_custom_detach_preserves_unknown_raw_fields",
            detached.get("unknownTop")
                .getAsString()
                .equals("keep top metadata")
                && detached.getAsJsonArray("trades")
                    .get(0)
                    .getAsJsonObject()
                    .get("unknownCustom")
                    .getAsString()
                    .equals("preserved")
                && new JsonParser().parse(read(root.resolve("config/gtit/trade/pages.json")))
                    .getAsJsonObject()
                    .getAsJsonArray("pages")
                    .toString()
                    .contains("unknownPageField"));
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

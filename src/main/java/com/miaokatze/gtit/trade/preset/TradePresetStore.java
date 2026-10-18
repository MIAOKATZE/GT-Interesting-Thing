package com.miaokatze.gtit.trade.preset;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.miaokatze.gtit.trade.NekoPageEntry;
import com.miaokatze.gtit.trade.NekoTradeConfig;
import com.miaokatze.gtit.trade.NekoTradeEntry;
import com.miaokatze.gtit.trade.NekoTradeJson;

/** Server-side filesystem model. No player, network or client filesystem access lives here. */
final class TradePresetStore {

    static final List<String> BUILTIN_IDS = Arrays.asList("gtit-ae-basic", "gtit-ae-advanced", "gtit-base-decoration");
    static final Gson GSON = new GsonBuilder().setPrettyPrinting()
        .serializeNulls()
        .disableHtmlEscaping()
        .create();
    private static final String STATE_PATH = "config/gtit/trade/preset_state.json";
    private final PresetFileTransaction files;
    private final Map<String, TradePresetDefinition> presets = new LinkedHashMap<>();
    private final List<String> errors = new ArrayList<>();
    private final List<String> builtinNotices = new ArrayList<>();
    private final Set<String> deferredApplyIds = new HashSet<>();
    private State state;

    TradePresetStore(Path gameRoot) throws IOException {
        files = new PresetFileTransaction(gameRoot);
        files.recover();
        Path statePath = files.checked(STATE_PATH);
        state = Files.exists(statePath) ? GSON.fromJson(read(statePath), State.class) : new State();
        if (state == null || state.selectedIds == null
            || state.ownedTabs == null
            || state.appliedFingerprints == null
            || state.pageIds == null
            || state.revision < 0) {
            throw new IOException("预设状态文件损坏，已停止写入以保护已有配置");
        }
        bootstrap();
        reload();
        if (state.layoutVersion < 2) {
            Set<String> migrate = new HashSet<>(state.selectedIds);
            migrate.retainAll(presets.keySet());
            // Directory-only builtin updates still await explicit Apply during legacy layout migration.
            migrate.removeAll(deferredApplyIds);
            commitSelection(new LinkedHashSet<>(state.selectedIds), migrate, new LinkedHashMap<>());
        }
    }

    void reload() throws IOException {
        Map<String, TradePresetDefinition> previousCatalog = new LinkedHashMap<>(presets);
        List<String> previousErrors = new ArrayList<>(errors);
        try {
            files.recover();
            presets.clear();
            errors.clear();
            Path directory = files.checked("MiaoTrade/gtit-ae-basic.json")
                .getParent();
            List<Path> paths = new ArrayList<>();
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory, "*.json")) {
                for (Path path : stream) paths.add(path);
            }
            paths.sort(
                Comparator.comparing(
                    path -> path.getFileName()
                        .toString()));
            for (Path path : paths) {
                try {
                    Path checked = files.checked("MiaoTrade/" + path.getFileName());
                    JsonObject source = new JsonParser().parse(read(checked))
                        .getAsJsonObject();
                    validateRawIds(source);
                    TradePresetDefinition preset = GSON.fromJson(source, TradePresetDefinition.class);
                    validate(preset);
                    if (!path.getFileName()
                        .toString()
                        .equals(preset.id + ".json")) {
                        throw new IllegalArgumentException("文件名必须与 id 一致");
                    }
                    if (presets.putIfAbsent(preset.id, preset) != null) {
                        throw new IllegalArgumentException("重复预设 id: " + preset.id);
                    }
                } catch (IOException | RuntimeException invalid) {
                    errors.add(path.getFileName() + ": " + invalid.getMessage());
                }
            }
            for (String selected : state.selectedIds) {
                if (!presets.containsKey(selected)) errors.add("已启用预设文件缺失或无效: " + selected + "（已有配置已保留）");
            }
            String fingerprint = fingerprint();
            if (!fingerprint.equals(state.catalogFingerprint)) {
                State next = GSON.fromJson(GSON.toJson(state), State.class);
                next.catalogFingerprint = fingerprint;
                next.revision++;
                Map<String, byte[]> changes = new LinkedHashMap<>();
                changes.put(STATE_PATH, bytes(next));
                files.commit(changes);
                state = next;
            }
        } catch (IOException | RuntimeException failure) {
            presets.clear();
            presets.putAll(previousCatalog);
            errors.clear();
            errors.addAll(previousErrors);
            throw failure;
        }
    }

    long revision() {
        return state.revision;
    }

    Collection<TradePresetDefinition> definitions() {
        return presets.values();
    }

    List<String> errors() {
        List<String> result = new ArrayList<>(errors);
        result.addAll(builtinNotices);
        return result;
    }

    List<String> builtinNotices() {
        return new ArrayList<>(builtinNotices);
    }

    boolean selected(String id) {
        return state.selectedIds.contains(id);
    }

    List<String> selectedIds() {
        return new ArrayList<>(state.selectedIds);
    }

    int installedTradeCount() throws IOException {
        int count = 0;
        for (OwnedTab owned : state.ownedTabs) {
            Path path = files.checked(tabPath(owned.tabId));
            if (!Files.exists(path)) continue;
            NekoTradeConfig.NekoTradeData data = GSON.fromJson(read(path), NekoTradeConfig.NekoTradeData.class);
            if (data != null && data.getTrades() != null) count += data.getTrades()
                .size();
        }
        return count;
    }

    List<Integer> installedTabs(String id) {
        List<Integer> ids = new ArrayList<>();
        for (OwnedTab owned : state.ownedTabs) if (id.equals(owned.presetId)) ids.add(owned.tabId);
        return ids;
    }

    List<NekoPageEntry> pages() throws IOException {
        JsonArray pages = readPages().getAsJsonArray("pages");
        List<NekoPageEntry> result = new ArrayList<>();
        for (JsonElement page : pages) result.add(GSON.fromJson(page, NekoPageEntry.class));
        return result;
    }

    void select(long expectedRevision, List<String> ids, DefinitionValidator validator) throws IOException {
        LinkedHashSet<String> requested = validateSelection(ids, presets, validator);
        Set<String> refresh = new HashSet<>();
        for (String id : requested) {
            if (state.selectedIds.contains(id)
                && !definitionFingerprint(presets.get(id)).equals(state.appliedFingerprints.get(id))) {
                refresh.add(id);
            }
        }
        if (new HashSet<>(state.selectedIds).equals(requested) && refresh.isEmpty()) return;
        checkRevision(expectedRevision);
        commitSelection(requested, refresh, new LinkedHashMap<>());
    }

    void save(long expectedRevision, TradePresetDefinition definition, List<Integer> tabIds, boolean replace,
        DefinitionValidator validator) throws IOException {
        save(expectedRevision, definition, tabIds, replace, null, true, validator);
    }

    void save(long expectedRevision, TradePresetDefinition definition, List<Integer> tabIds, boolean replace,
        String sourceId, boolean replaceTabs, DefinitionValidator validator) throws IOException {
        checkRevision(expectedRevision);
        validId(definition.id);
        if (BUILTIN_IDS.contains(definition.id)) throw new IllegalArgumentException("内置模板请使用新 id 另存");
        if (Files.exists(files.checked("MiaoTrade/" + definition.id + ".json")) != replace
            || (replace && !presets.containsKey(definition.id))) {
            throw new IllegalArgumentException(replace ? "待编辑预设不存在" : "预设 id 已存在，请使用其他 id");
        }
        if (replaceTabs) {
            definition.tabs = exportTabs(tabIds);
        } else {
            validId(sourceId);
            TradePresetDefinition source = presets.get(sourceId);
            if (source == null) throw new IllegalArgumentException("源预设不存在或未正确读取: " + sourceId);
            definition.tabs = GSON.fromJson(GSON.toJson(source), TradePresetDefinition.class).tabs;
            for (TradePresetDefinition.Tab tab : definition.tabs) {
                tab.page.setDefault(false);
                tab.page.setPresetBuiltin(false);
                for (NekoTradeEntry trade : tab.trades) {
                    trade.setDefaultEntry(false);
                    trade.setPresetBuiltin(false);
                }
            }
        }
        validate(definition);
        // An export may intentionally contain dependencies unavailable on this server; selection checks them.
        TradePresetDefinition previous = presets.put(definition.id, definition);
        try {
            Map<String, byte[]> changes = new LinkedHashMap<>();
            changes.put("MiaoTrade/" + definition.id + ".json", bytes(definition));
            LinkedHashSet<String> requested = new LinkedHashSet<>(state.selectedIds);
            if (requested.contains(definition.id)) validateSelection(new ArrayList<>(requested), presets, validator);
            commitSelection(requested, new HashSet<>(Arrays.asList(definition.id)), changes);
        } catch (IOException | RuntimeException failure) {
            if (previous == null) presets.remove(definition.id);
            else presets.put(definition.id, previous);
            throw failure;
        }
    }

    private List<TradePresetDefinition.Tab> exportTabs(List<Integer> tabIds) throws IOException {
        if (tabIds == null || tabIds.isEmpty() || tabIds.size() > 128) {
            throw new IllegalArgumentException("请选择 1–128 个现有标签页");
        }
        List<TradePresetDefinition.Tab> result = new ArrayList<>();
        Set<Integer> seen = new HashSet<>();
        Map<Integer, NekoPageEntry> available = new LinkedHashMap<>();
        for (NekoPageEntry page : pages()) available.put(page.getId(), page);
        for (Integer tabId : tabIds) {
            if (tabId == null || !seen.add(tabId) || !available.containsKey(tabId)) {
                throw new IllegalArgumentException("标签页不存在或重复: " + tabId);
            }
            TradePresetDefinition.Tab tab = new TradePresetDefinition.Tab();
            tab.page = GSON.fromJson(GSON.toJson(available.get(tabId)), NekoPageEntry.class);
            tab.page.setId(result.size() + 1);
            tab.page.setDefault(false);
            tab.page.setPresetBuiltin(false);
            Path source = files.checked(tabPath(tabId));
            if (Files.exists(source)) {
                NekoTradeConfig.TabFileData data = GSON.fromJson(read(source), NekoTradeConfig.TabFileData.class);
                if (data == null || data.getTrades() == null) throw new IOException("待导出标签页交易文件损坏");
                tab.trades = data.getTrades();
            }
            for (NekoTradeEntry trade : tab.trades) {
                trade.setDefaultEntry(false);
                trade.setPresetBuiltin(false);
            }
            result.add(tab);
        }
        return result;
    }

    private void commitSelection(LinkedHashSet<String> selected, Set<String> refresh, Map<String, byte[]> changes)
        throws IOException {
        State next = GSON.fromJson(GSON.toJson(state), State.class);
        next.layoutVersion = 2;
        JsonObject pages = readPages();
        JsonArray pageList = pages.getAsJsonArray("pages");
        Set<Integer> used = new HashSet<>();
        for (JsonElement page : pageList) used.add(
            page.getAsJsonObject()
                .get("id")
                .getAsInt());
        // Include orphan user files, which must never be overwritten when edited presets detach.
        Path tradesDirectory = files.checked(tabPath(1))
            .getParent();
        if (Files.exists(tradesDirectory)) {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(tradesDirectory, "tab_*.json")) {
                for (Path path : stream) {
                    String name = path.getFileName()
                        .toString();
                    try {
                        used.add(Integer.parseInt(name.substring(4, name.length() - 5)));
                    } catch (NumberFormatException ignored) {
                        // Unrelated malformed filenames are never changed.
                    }
                }
            }
        }
        // Hand-written or orphan overlay files have no ownership record; never overwrite them implicitly.
        Path managedDirectory = files.checked(TradePresetLayout.tabPath(-100001))
            .getParent();
        if (Files.exists(managedDirectory)) {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(managedDirectory, "tab_*.json")) {
                for (Path path : stream) {
                    String name = path.getFileName()
                        .toString();
                    try {
                        used.add(Integer.parseInt(name.substring(4, name.length() - 5)));
                    } catch (NumberFormatException ignored) {
                        // Malformed unrelated filenames are left alone.
                    }
                }
            }
        }
        List<OwnedTab> retained = new ArrayList<>();
        Set<String> installed = new HashSet<>(state.selectedIds);
        for (OwnedTab owned : state.ownedTabs) {
            if (selected.contains(owned.presetId) && !refresh.contains(owned.presetId)) {
                retained.add(owned);
                continue;
            }
            // Only remove a tab when both its page metadata and its complete trade file are unchanged.
            // An edited tab is detached from ownership and remains a normal user tab forever.
            JsonElement currentPage = findPage(pageList, owned.tabId);
            Path tradeFile = files.checked(tabPath(owned.tabId));
            JsonElement currentTrades = Files.exists(tradeFile) ? new JsonParser().parse(read(tradeFile)) : null;
            if (owned.page.equals(currentPage) && owned.trades.equals(currentTrades)) {
                removePage(pageList, owned.tabId);
                changes.put(tabPath(owned.tabId), null);
                if (TradePresetLayout.isManagedPage(owned.tabId)) used.remove(owned.tabId);
            } else {
                // Detach customized defaults into the positive user namespace before removing the overlay.
                int detachedId = owned.tabId;
                if (TradePresetLayout.isManagedPage(owned.tabId)) {
                    detachedId = allocateUserId(used);
                    used.add(detachedId);
                    removePage(pageList, owned.tabId);
                    changes.put(tabPath(owned.tabId), null);
                    used.remove(owned.tabId);
                    if (currentPage != null) {
                        currentPage.getAsJsonObject()
                            .addProperty("id", detachedId);
                        pageList.add(currentPage);
                    }
                }
                if (currentPage != null) {
                    currentPage.getAsJsonObject()
                        .addProperty("presetBuiltin", false);
                    currentPage.getAsJsonObject()
                        .addProperty("isDefault", false);
                }
                if (currentTrades != null && currentTrades.isJsonObject()
                    && currentTrades.getAsJsonObject()
                        .has("trades")) {
                    currentTrades.getAsJsonObject()
                        .addProperty("tabId", detachedId);
                    for (JsonElement entry : currentTrades.getAsJsonObject()
                        .getAsJsonArray("trades")) {
                        JsonObject trade = entry.getAsJsonObject();
                        trade.addProperty("presetBuiltin", false);
                        trade.addProperty("defaultEntry", false);
                        trade.addProperty("tabId", detachedId);
                        if (TradePresetLayout.isManagedPage(owned.tabId)) trade.addProperty(
                            "id",
                            UUID.randomUUID()
                                .toString());
                    }
                    changes.put(tabPath(detachedId), bytes(currentTrades));
                }
            }
        }
        next.ownedTabs = retained;
        for (String id : selected) {
            if (installed.contains(id) && !refresh.contains(id)) continue;
            TradePresetDefinition preset = presets.get(id);
            if (preset == null) throw new IllegalArgumentException("预设不存在: " + id);
            for (TradePresetDefinition.Tab sourceTab : preset.tabs) {
                String pageKey = id + ":" + sourceTab.page.getId();
                Set<Integer> reserved = new HashSet<>(next.pageIds.values());
                reserved.addAll(used);
                int tabId = BUILTIN_IDS.contains(id) ? TradePresetLayout.pageId(id, sourceTab.page.getId())
                    : next.pageIds
                        .computeIfAbsent(pageKey, ignored -> TradePresetLayout.customPageId(pageKey, reserved));
                if (used.contains(tabId)) throw new IllegalArgumentException("默认贸易组标签页被非受管内容占用: " + tabId);
                used.add(tabId);
                NekoPageEntry page = GSON.fromJson(GSON.toJson(sourceTab.page), NekoPageEntry.class);
                page.setId(tabId);
                page.setDefault(false);
                page.setPresetBuiltin(true);
                NekoTradeConfig.TabFileData trades = new NekoTradeConfig.TabFileData();
                trades.setTabId(tabId);
                List<NekoTradeEntry> entries = new ArrayList<>();
                for (int index = 0; index < sourceTab.trades.size(); index++) {
                    NekoTradeEntry entry = GSON
                        .fromJson(GSON.toJson(sourceTab.trades.get(index)), NekoTradeEntry.class);
                    entry.setId(TradePresetLayout.tradeId(id, entry.getId()));
                    entry.setTabId(tabId);
                    entry.setDefaultEntry(true);
                    entry.setPresetBuiltin(true);
                    entry.setStrictBq(true);
                    entries.add(entry);
                }
                trades.setTrades(entries);
                OwnedTab owned = new OwnedTab();
                owned.presetId = id;
                owned.tabId = tabId;
                owned.page = GSON.toJsonTree(page);
                owned.trades = GSON.toJsonTree(trades);
                next.ownedTabs.add(owned);
                pageList.add(owned.page);
                changes.put(tabPath(tabId), bytes(trades));
            }
        }
        next.selectedIds = new ArrayList<>(selected);
        next.appliedFingerprints.keySet()
            .retainAll(selected);
        for (String id : selected) {
            if (presets.containsKey(id) && (!installed.contains(id) || refresh.contains(id))) {
                next.appliedFingerprints.put(id, definitionFingerprint(presets.get(id)));
            }
        }
        next.catalogFingerprint = fingerprint();
        next.revision++;
        JsonObject userPages = GSON.fromJson(GSON.toJson(pages), JsonObject.class);
        JsonObject managedPages = new JsonObject();
        userPages.addProperty("version", 1);
        managedPages.addProperty("version", 1);
        JsonArray userList = new JsonArray();
        JsonArray managedList = new JsonArray();
        for (JsonElement page : pageList) {
            (TradePresetLayout.isManagedPage(
                page.getAsJsonObject()
                    .get("id")
                    .getAsInt()) ? managedList : userList).add(page);
        }
        userPages.add("pages", userList);
        managedPages.add("pages", managedList);
        changes.put("config/gtit/trade/pages.json", bytes(userPages));
        changes.put(TradePresetLayout.PAGES_PATH, bytes(managedPages));
        changes.put(STATE_PATH, bytes(next));
        files.commit(changes);
        state = next;
    }

    private JsonObject readPages() throws IOException {
        Path path = files.checked("config/gtit/trade/pages.json");
        if (!Files.exists(path)) throw new IOException("标签页配置尚未初始化");
        JsonObject data = new JsonParser().parse(read(path))
            .getAsJsonObject();
        if (!data.has("pages") || !data.get("pages")
            .isJsonArray()) throw new IOException("标签页配置损坏");
        Path managedPath = files.checked(TradePresetLayout.PAGES_PATH);
        if (Files.exists(managedPath)) {
            JsonObject managed = new JsonParser().parse(read(managedPath))
                .getAsJsonObject();
            for (JsonElement page : managed.getAsJsonArray("pages")) {
                if (!TradePresetLayout.isManagedPage(
                    page.getAsJsonObject()
                        .get("id")
                        .getAsInt())) {
                    throw new IOException("默认贸易组页签 ID 越界");
                }
                data.getAsJsonArray("pages")
                    .add(page);
            }
        }
        Set<Integer> seen = new HashSet<>();
        for (JsonElement page : data.getAsJsonArray("pages")) {
            int id = page.getAsJsonObject()
                .get("id")
                .getAsInt();
            if ((id <= 0 && !TradePresetLayout.isManagedPage(id)) || !seen.add(id))
                throw new IOException("标签页配置含非法或重复 id");
        }
        return data;
    }

    private static JsonElement findPage(JsonArray pages, int id) {
        for (JsonElement page : pages) if (page.getAsJsonObject()
            .get("id")
            .getAsInt() == id) return page;
        return null;
    }

    private static void removePage(JsonArray pages, int id) {
        java.util.Iterator<JsonElement> iterator = pages.iterator();
        while (iterator.hasNext()) {
            if (iterator.next()
                .getAsJsonObject()
                .get("id")
                .getAsInt() == id) iterator.remove();
        }
    }

    static void validate(TradePresetDefinition preset) {
        if (preset == null || preset.schemaVersion != 1) throw new IllegalArgumentException("预设 schemaVersion 必须为 1");
        validId(preset.id);
        if (preset.name == null || preset.name.trim()
            .isEmpty() || preset.name.length() > 128) {
            throw new IllegalArgumentException("预设名称须为 1–128 字符");
        }
        if (preset.description == null || preset.description.length() > 2048)
            throw new IllegalArgumentException("预设说明过长");
        if (preset.icon == null || preset.icon.item == null
            || preset.icon.item.length() > 256
            || preset.icon.nbtBase64 == null
            || preset.icon.nbtBase64.length() > 32768) {
            throw new IllegalArgumentException("预设图标数据无效或过大");
        }
        if (preset.mutuallyExclusiveIds == null || preset.mutuallyExclusiveIds.size() > 128) {
            throw new IllegalArgumentException("预设互斥列表无效");
        }
        Set<String> exclusions = new HashSet<>();
        for (String id : preset.mutuallyExclusiveIds) {
            validId(id);
            if (id.equals(preset.id) || !exclusions.add(id)) throw new IllegalArgumentException("互斥 id 重复或指向自身");
        }
        if (preset.tabs == null || preset.tabs.size() > 128) throw new IllegalArgumentException("预设标签页数量超过 128");
        Set<Integer> pages = new HashSet<>();
        Set<String> entries = new HashSet<>();
        int count = 0;
        for (TradePresetDefinition.Tab tab : preset.tabs) {
            if (tab == null || tab.page == null
                || tab.page.getId() <= 0
                || !pages.add(tab.page.getId())
                || tab.page.getName() == null
                || tab.page.getName()
                    .length() > 128
                || tab.trades == null) {
                throw new IllegalArgumentException("预设标签页无效或 id 重复");
            }
            for (NekoTradeEntry entry : tab.trades) {
                if (++count > 8192) throw new IllegalArgumentException("单个预设交易条目超过 8192");
                if (entry == null || entry.getId() == null
                    || entry.getId()
                        .isEmpty()
                    || entry.getId()
                        .length() > 128
                    || !entries.add(entry.getId())) throw new IllegalArgumentException("预设交易 id 无效或重复");
                if (entry.getToItems() == null || entry.getToItems()
                    .isEmpty()) {
                    throw new IllegalArgumentException("预设交易产出不能为空: " + entry.getId());
                }
                if (entry.getRequiresMods() != null && entry.getRequiresMods().length > 128) {
                    throw new IllegalArgumentException("预设 Mod 依赖数量过多");
                }
            }
        }
    }

    static LinkedHashSet<String> validateSelection(List<String> ids, Map<String, TradePresetDefinition> definitions,
        DefinitionValidator validator) {
        if (ids == null || ids.size() > 128) throw new IllegalArgumentException("最多选择 128 个预设");
        LinkedHashSet<String> selected = new LinkedHashSet<>();
        for (String id : ids) {
            validId(id);
            if (!selected.add(id)) throw new IllegalArgumentException("重复选择预设: " + id);
            TradePresetDefinition definition = definitions.get(id);
            if (definition == null) throw new IllegalArgumentException("预设不存在或文件无效: " + id);
            validator.validate(definition);
        }
        for (String id : selected) {
            for (String other : definitions.get(id).mutuallyExclusiveIds) {
                if (selected.contains(other)) throw new IllegalArgumentException("互斥预设不能同时启用: " + id + " / " + other);
            }
        }
        return selected;
    }

    static void validId(String id) {
        if (id == null || !id.matches("[a-z0-9_-]{1,64}")) {
            throw new IllegalArgumentException("预设 id 仅允许 1–64 个小写字母、数字、下划线及连字符");
        }
    }

    private static void validateRawIds(JsonObject source) {
        if (!source.has("tabs") || !source.get("tabs")
            .isJsonArray()) {
            throw new IllegalArgumentException("预设 tabs 必须为数组");
        }
        for (JsonElement tab : source.getAsJsonArray("tabs")) {
            JsonArray trades = tab.getAsJsonObject()
                .getAsJsonArray("trades");
            if (trades == null) throw new IllegalArgumentException("每个标签页必须声明 trades 数组");
            for (JsonElement trade : trades) {
                if (!trade.getAsJsonObject()
                    .has("id") || trade.getAsJsonObject()
                        .get("id")
                        .isJsonNull()) {
                    throw new IllegalArgumentException("每个交易条目必须有明确且稳定的 id");
                }
            }
        }
    }

    private void checkRevision(long expected) {
        if (expected != state.revision) throw new IllegalArgumentException("预设配置已变化，请刷新后重试");
    }

    private void bootstrap() throws IOException {
        Map<String, byte[]> changes = new LinkedHashMap<>();
        for (String id : BUILTIN_IDS) {
            String relative = "MiaoTrade/" + id + ".json";
            Path path = files.checked(relative);
            byte[] bundled = bundledBytes(id, false);
            JsonElement current = new JsonParser().parse(new String(bundled, StandardCharsets.UTF_8));
            validate(GSON.fromJson(current, TradePresetDefinition.class));
            if (!Files.exists(path)) {
                changes.put(relative, bundled);
                continue;
            }
            JsonElement existing = null;
            try {
                existing = new JsonParser().parse(read(path));
            } catch (IOException | RuntimeException invalid) {
                // A broken or unfamiliar file is user data too; never replace it by builtin ID alone.
            }
            boolean clearedQuests = false;
            if (existing != null && existing.isJsonObject()) {
                JsonObject source = existing.getAsJsonObject();
                if (source.has("id") && source.get("id")
                    .isJsonPrimitive()
                    && id.equals(
                        source.get("id")
                            .getAsString())
                    && source.has("tabs")
                    && source.get("tabs")
                        .isJsonArray()) {
                    for (JsonElement tab : source.getAsJsonArray("tabs")) {
                        if (!tab.isJsonObject() || !tab.getAsJsonObject()
                            .has("trades")
                            || !tab.getAsJsonObject()
                                .get("trades")
                                .isJsonArray())
                            continue;
                        for (JsonElement value : tab.getAsJsonObject()
                            .getAsJsonArray("trades")) {
                            if (!value.isJsonObject()) continue;
                            JsonObject trade = value.getAsJsonObject();
                            JsonElement quest = trade.get("bqQuestId");
                            if (quest != null && quest.isJsonPrimitive()
                                && quest.getAsJsonPrimitive()
                                    .isString()
                                && !quest.getAsString()
                                    .isEmpty()) {
                                trade.addProperty("bqQuestId", "");
                                clearedQuests = true;
                            }
                        }
                    }
                }
            }
            if (clearedQuests) {
                changes.put(relative, bytes(existing));
                deferredApplyIds.add(id);
            }
            if (current.equals(existing)) {
                if (clearedQuests) builtinNotices.add("内置预设 " + id + " 任务限制已移除，应用后生效。");
                continue;
            }
            if (originalEmpty(id, existing)) {
                changes.put(relative, bundled);
                deferredApplyIds.add(id);
                builtinNotices.add("内置预设 " + id + " 的原始空模板已升级为完整目录。已应用贸易保持不变，请应用选择以安装新内容。");
            } else {
                String update = "MiaoTrade/builtin_updates/" + UUID.nameUUIDFromBytes(bundled) + "/" + id + ".json";
                Path candidate = files.checked(update);
                if (!Files.exists(candidate)) changes.put(update, bundled);
                builtinNotices.add(
                    clearedQuests ? "内置预设 " + id + " 任务限制已移除，应用后生效；其他配置保留。新版副本：" + update
                        : "内置预设 " + id + " 已由用户修改，保留原文件；新版副本位于 " + update + "，请另存新 ID 或手动合并后应用。");
            }
        }
        if (!changes.isEmpty()) files.commit(changes);
    }

    private static boolean originalEmpty(String id, JsonElement existing) throws IOException {
        if (existing == null) return false;
        JsonObject original = new JsonParser().parse(new String(bundledBytes(id, true), StandardCharsets.UTF_8))
            .getAsJsonObject();
        if (original.equals(existing)) return true;
        // These are the only other shipped originals, before v1.8.97 switched to AE item icons.
        if ("gtit-ae-basic".equals(id)) original.getAsJsonObject("icon")
            .addProperty("item", "minecraft:redstone");
        else if ("gtit-ae-advanced".equals(id)) original.getAsJsonObject("icon")
            .addProperty("item", "minecraft:comparator");
        else return false;
        return original.equals(existing);
    }

    static byte[] bundledBytes(String id, boolean legacyEmpty) throws IOException {
        String resource = "assets/gtit/trade_presets/" + (legacyEmpty ? "legacy_empty/" : "") + id + ".json";
        try (InputStream stream = TradePresetStore.class.getClassLoader()
            .getResourceAsStream(resource)) {
            if (stream == null) throw new IOException("缺失内置预设模板: " + resource);
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int count;
            while ((count = stream.read(buffer)) != -1) bytes.write(buffer, 0, count);
            return bytes.toByteArray();
        }
    }

    private static String read(Path path) throws IOException {
        if (Files.size(path) > 16L * 1024 * 1024) throw new IOException("预设相关文件超过 16 MiB 限制");
        String json = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
        NekoTradeJson.validateDepth(json);
        return json;
    }

    private static byte[] bytes(Object object) {
        byte[] result = GSON.toJson(object)
            .getBytes(StandardCharsets.UTF_8);
        if (result.length > 16L * 1024 * 1024) throw new IllegalArgumentException("预设相关数据超过 16 MiB 限制");
        return result;
    }

    private String fingerprint() {
        return hash(bytes(new java.util.TreeMap<>(presets)));
    }

    private static String definitionFingerprint(TradePresetDefinition definition) {
        return hash(bytes(definition));
    }

    private static String hash(byte[] bytes) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                .digest(bytes);
            return java.util.Base64.getEncoder()
                .encodeToString(hash);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static String tabPath(int id) {
        return TradePresetLayout.tabPath(id);
    }

    private static int allocateUserId(Set<Integer> used) {
        int id = 1000;
        while (used.contains(id)) {
            if (id == Integer.MAX_VALUE) throw new IllegalArgumentException("标签页 id 已用尽");
            id++;
        }
        return id;
    }

    interface DefinitionValidator {

        void validate(TradePresetDefinition definition);
    }

    private static final class State {

        int layoutVersion;
        long revision;
        String catalogFingerprint;
        Map<String, String> appliedFingerprints = new LinkedHashMap<>();
        Map<String, Integer> pageIds = new LinkedHashMap<>();
        List<String> selectedIds = new ArrayList<>();
        List<OwnedTab> ownedTabs = new ArrayList<>();
    }

    private static final class OwnedTab {

        String presetId;
        int tabId;
        JsonElement page;
        JsonElement trades;
    }
}

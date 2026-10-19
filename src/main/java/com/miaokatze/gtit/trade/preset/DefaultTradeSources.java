package com.miaokatze.gtit.trade.preset;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.miaokatze.gtit.config.DefaultContentConfig;
import com.miaokatze.gtit.trade.NekoPageConfig;
import com.miaokatze.gtit.trade.NekoPageEntry;
import com.miaokatze.gtit.trade.NekoTradeConfig;
import com.miaokatze.gtit.trade.NekoTradeEntry;
import com.miaokatze.gtit.trade.TradeConfigPaths;

/** Jar-backed defaults. Disk files contain only explicit edits, never a second installed default library. */
public final class DefaultTradeSources {

    public static final String BASE_ID = "gtit-base";
    private static final String OVERRIDES = "config/gtit/trade/default_overrides/";

    private DefaultTradeSources() {}

    static TradePresetDefinition bundled(String id) throws IOException {
        if (!BASE_ID.equals(id)) return TradePresetStore.GSON.fromJson(
            new String(TradePresetStore.bundledBytes(id, false), StandardCharsets.UTF_8),
            TradePresetDefinition.class);
        TradePresetDefinition definition = new TradePresetDefinition();
        definition.id = BASE_ID;
        definition.name = "基础默认贸易";
        definition.description = "随模组提供的基础贸易";
        definition.icon.item = "gtit:neko_coin";
        NekoPageConfig.NekoPageData pages = TradePresetStore.GSON
            .fromJson(resource("pages_base.json"), NekoPageConfig.NekoPageData.class);
        NekoTradeConfig.NekoTradeData trades = TradePresetStore.GSON
            .fromJson(resource("base_trades.json"), NekoTradeConfig.NekoTradeData.class);
        for (NekoPageEntry page : pages.getPages()) {
            TradePresetDefinition.Tab tab = new TradePresetDefinition.Tab();
            tab.page = page;
            for (NekoTradeEntry entry : trades.getTrades()) if (entry.getTabId() == page.getId()) tab.trades.add(entry);
            definition.tabs.add(tab);
        }
        return definition;
    }

    private static String resource(String file) throws IOException {
        try (java.io.InputStream input = DefaultTradeSources.class.getClassLoader()
            .getResourceAsStream("assets/gtit/bqtrades/" + file)) {
            if (input == null) throw new IOException("缺少基础默认贸易资源: " + file);
            java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    static void source(NekoPageEntry page, TradePresetDefinition definition, boolean builtin) {
        page.setPresetBuiltin(builtin);
        page.setSourcePresetId(definition.id);
        page.setSourcePresetName(definition.name);
        String item = definition.icon == null ? null : definition.icon.item;
        boolean supplied = item != null && item.matches("[^\\s:]+:[^\\s:]+");
        // Migration runs before mod item registration; registry misses are handled by the display fallback.
        page.setSourcePresetIconItem(supplied ? item : "minecraft:book");
        page.setSourcePresetIconMeta(supplied ? definition.icon.meta : 0);
        page.setSourcePresetIconNbt(supplied && definition.icon.nbtBase64 != null ? definition.icon.nbtBase64 : "");
    }

    static TradePresetDefinition installed(String id, Path root) throws IOException {
        TradePresetDefinition result = bundled(id);
        for (TradePresetDefinition.Tab tab : result.tabs) {
            tab.page.setId(TradePresetLayout.pageId(id, tab.page.getId()));
            tab.page.setDefault(false);
            source(tab.page, result, true);
            for (NekoTradeEntry entry : tab.trades) {
                entry.setId(TradePresetLayout.tradeId(id, entry.getId()));
                entry.setTabId(tab.page.getId());
                entry.setDefaultEntry(true);
                entry.setPresetBuiltin(true);
                entry.setStrictBq(!BASE_ID.equals(id));
            }
        }
        Path override = new PresetFileTransaction(root).checked(OVERRIDES + id + ".json");
        if (Files.exists(override)) {
            TradePresetDefinition edited = TradePresetStore.GSON.fromJson(read(override), TradePresetDefinition.class);
            if (edited == null || !id.equals(edited.id) || edited.tabs == null)
                throw new IOException("默认贸易覆盖配置损坏: " + id);
            java.util.Set<Integer> seen = new java.util.HashSet<>();
            for (TradePresetDefinition.Tab tab : edited.tabs) {
                if (tab == null || tab.page == null
                    || tab.trades == null
                    || !id.equals(TradePresetLayout.builtinId(tab.page.getId()))
                    || !seen.add(tab.page.getId())) throw new IOException("默认贸易覆盖页签来源不匹配: " + id);
                source(tab.page, result, true);
                for (NekoTradeEntry entry : tab.trades) {
                    if (entry == null) throw new IOException("默认贸易覆盖含空交易: " + id);
                    entry.setTabId(tab.page.getId());
                    entry.setDefaultEntry(true);
                    entry.setPresetBuiltin(true);
                }
            }
            result.tabs = edited.tabs;
        }
        return result;
    }

    static List<String> selected(Path root) throws IOException {
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        ids.add(BASE_ID);
        Path state = new PresetFileTransaction(root).checked("config/gtit/trade/preset_state.json");
        if (Files.exists(state)) {
            JsonObject object = new JsonParser().parse(read(state))
                .getAsJsonObject();
            if (object.has("selectedIds")) for (JsonElement id : object.getAsJsonArray("selectedIds")) {
                if (TradePresetStore.BUILTIN_IDS.contains(id.getAsString())) ids.add(id.getAsString());
            }
        }
        return new ArrayList<>(ids);
    }

    public static List<NekoPageEntry> pages() throws IOException {
        return pages(TradeConfigPaths.gameRoot());
    }

    static List<NekoPageEntry> pages(Path root) throws IOException {
        List<NekoPageEntry> pages = new ArrayList<>();
        if (DefaultContentConfig.loadDefaultTradeGroups()) for (String id : selected(root)) {
            for (TradePresetDefinition.Tab tab : installed(id, root).tabs) pages.add(tab.page);
        }
        return pages;
    }

    public static List<NekoTradeEntry> trades() throws IOException {
        return trades(TradeConfigPaths.gameRoot());
    }

    static List<NekoTradeEntry> trades(Path root) throws IOException {
        List<NekoTradeEntry> trades = new ArrayList<>();
        if (DefaultContentConfig.loadDefaultTradeGroups()) for (String id : selected(root)) {
            for (TradePresetDefinition.Tab tab : installed(id, root).tabs) trades.addAll(tab.trades);
        }
        return trades;
    }

    /** Capture full edited groups so deleted trades also persist, while skipping byte-equivalent jar data. */
    public static void saveTrades(List<NekoTradeEntry> trades) throws IOException {
        saveTrades(TradeConfigPaths.gameRoot(), trades);
    }

    static void saveTrades(Path root, List<NekoTradeEntry> trades) throws IOException {
        if (!DefaultContentConfig.loadDefaultTradeGroups()) return;
        Map<String, String> originalOwners = new LinkedHashMap<>();
        for (String id : TradePresetStore.BUILTIN_IDS) for (TradePresetDefinition.Tab tab : bundled(id).tabs) {
            for (NekoTradeEntry entry : tab.trades)
                originalOwners.put(TradePresetLayout.tradeId(id, entry.getId()), id);
        }
        for (NekoTradeEntry entry : trades) {
            String destination = TradePresetLayout.builtinId(entry.getTabId());
            String original = originalOwners.get(entry.getId());
            // A moved stock trade becomes an independent edit; restoring its old group cannot create duplicate IDs.
            if (original != null && !original.equals(destination)) entry.setId(
                java.util.UUID.randomUUID()
                    .toString());
            if (destination != null || original != null || entry.isDefaultEntry() || entry.isPresetBuiltin()) {
                entry.setDefaultEntry(destination != null);
                entry.setPresetBuiltin(destination != null);
            }
        }
        Map<String, byte[]> changes = new LinkedHashMap<>();
        for (String id : selected(root)) {
            TradePresetDefinition edited = installed(id, root);
            for (TradePresetDefinition.Tab tab : edited.tabs) {
                tab.trades = new ArrayList<>();
                for (NekoTradeEntry entry : trades) if (entry.getTabId() == tab.page.getId()) tab.trades.add(entry);
            }
            changedOverride(id, edited, root, changes);
        }
        if (!changes.isEmpty()) new PresetFileTransaction(root).commit(changes);
    }

    public static void savePages(List<NekoPageEntry> pages) throws IOException {
        savePages(TradeConfigPaths.gameRoot(), pages);
    }

    static void savePages(Path root, List<NekoPageEntry> pages) throws IOException {
        if (!DefaultContentConfig.loadDefaultTradeGroups()) return;
        Map<String, byte[]> changes = new LinkedHashMap<>();
        for (String id : selected(root)) {
            TradePresetDefinition edited = installed(id, root);
            for (TradePresetDefinition.Tab tab : edited.tabs) {
                for (NekoPageEntry page : pages) if (page.getId() == tab.page.getId()) {
                    tab.page = page;
                    break;
                }
            }
            edited.tabs.removeIf(
                tab -> pages.stream()
                    .noneMatch(page -> page.getId() == tab.page.getId()));
            changedOverride(id, edited, root, changes);
        }
        if (!changes.isEmpty()) new PresetFileTransaction(root).commit(changes);
    }

    private static void changedOverride(String id, TradePresetDefinition edited, Path root, Map<String, byte[]> changes)
        throws IOException {
        // Generate the jar baseline without reading the override, using its local source identities.
        TradePresetDefinition baseline = bundled(id);
        for (TradePresetDefinition.Tab tab : baseline.tabs) {
            tab.page.setId(TradePresetLayout.pageId(id, tab.page.getId()));
            tab.page.setDefault(false);
            source(tab.page, baseline, true);
            for (NekoTradeEntry trade : tab.trades) {
                trade.setId(TradePresetLayout.tradeId(id, trade.getId()));
                trade.setTabId(tab.page.getId());
                trade.setDefaultEntry(true);
                trade.setPresetBuiltin(true);
                trade.setStrictBq(!BASE_ID.equals(id));
            }
        }
        String path = OVERRIDES + id + ".json";
        byte[] value = TradePresetStore.GSON.toJsonTree(baseline)
            .equals(TradePresetStore.GSON.toJsonTree(edited)) ? null : bytes(edited);
        Path target = new PresetFileTransaction(root).checked(path);
        if (value == null ? Files.exists(target)
            : !Files.exists(target) || !Arrays.equals(Files.readAllBytes(target), value)) changes.put(path, value);
    }

    public static void reset() throws IOException {
        reset(TradeConfigPaths.gameRoot());
    }

    public static void resetGroup(String id) throws IOException {
        if (!TradePresetStore.BUILTIN_IDS.contains(id)) throw new IllegalArgumentException("未知默认贸易组");
        reset(TradeConfigPaths.gameRoot(), java.util.Collections.singletonList(id));
    }

    static void reset(Path root) throws IOException {
        reset(root, TradePresetStore.BUILTIN_IDS);
    }

    private static void reset(Path root, List<String> ids) throws IOException {
        Map<String, byte[]> changes = new LinkedHashMap<>();
        PresetFileTransaction files = new PresetFileTransaction(root);
        for (String id : ids)
            if (Files.exists(files.checked(OVERRIDES + id + ".json"))) changes.put(OVERRIDES + id + ".json", null);
        if (!changes.isEmpty()) files.commit(changes);
    }

    static String read(Path path) throws IOException {
        if (Files.size(path) > 16L * 1024 * 1024) throw new IOException("贸易配置超过16MiB");
        String value = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
        com.miaokatze.gtit.trade.NekoTradeJson.validateDepth(value);
        return value;
    }

    static byte[] bytes(Object value) {
        return TradePresetStore.GSON.toJson(value)
            .getBytes(StandardCharsets.UTF_8);
    }
}

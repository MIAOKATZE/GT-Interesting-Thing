package com.miaokatze.gtit.trade.preset;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.miaokatze.gtit.trade.NekoPageEntry;
import com.miaokatze.gtit.trade.NekoPageSource;

/** Additive, journaled source migration. Never rekeys pages, moves trades, or removes source files. */
public final class PageSourceIdentity {

    private static final String STATE = "config/gtit/trade/preset_state.json";
    private static final String USER_PAGES = "config/gtit/trade/pages.json";

    private PageSourceIdentity() {}

    public static NekoPageSource presetSource(String presetId) {
        return DefaultTradeSources.BASE_ID.equals(presetId) ? NekoPageSource.DEFAULT : NekoPageSource.PRESET;
    }

    /** Conservative fallback for old wire payloads; disk loading supplies authoritative ownership first. */
    public static NekoPageSource legacySource(NekoPageEntry page) {
        String builtin = TradePresetLayout.builtinId(page.getId());
        if (builtin != null) return presetSource(builtin);
        if (TradePresetLayout.isManagedPage(page.getId()) && !page.getSourcePresetId()
            .isEmpty()) return NekoPageSource.PRESET;
        return NekoPageSource.CUSTOM;
    }

    /**
     * Decision precedence: explicit source (including unknown future values), ownedTabs, actual default
     * override path, managed namespace plus preset identity, ordinary user path. A retained historical
     * sourcePresetId on a detached positive user page is attribution, not installed ownership.
     */
    static NekoPageSource derive(NekoPageEntry page, String storage, String owner) {
        if (owner != null && !owner.isEmpty()) return presetSource(owner);
        if (storage.startsWith("config/gtit/trade/default_overrides/")) {
            String id = storage.substring(storage.lastIndexOf('/') + 1, storage.length() - 5);
            return presetSource(id);
        }
        if (TradePresetLayout.PAGES_PATH.equals(storage)) return legacySource(page);
        return NekoPageSource.CUSTOM;
    }

    /** Idempotent and field-preserving, including ownership snapshots used for safe uninstall comparison. */
    public static synchronized void migrate(Path root) throws IOException {
        PresetFileTransaction files = new PresetFileTransaction(root);
        files.recover();
        Map<String, byte[]> changes = new LinkedHashMap<>();
        Map<Integer, String> owners = new LinkedHashMap<>();
        Path statePath = files.checked(STATE);
        JsonObject state = Files.exists(statePath) ? read(statePath) : null;
        if (state != null && state.has("ownedTabs")
            && state.get("ownedTabs")
                .isJsonArray()) {
            for (JsonElement raw : state.getAsJsonArray("ownedTabs")) {
                JsonObject owned = raw.getAsJsonObject();
                owners.put(
                    owned.get("tabId")
                        .getAsInt(),
                    owned.get("presetId")
                        .getAsString());
            }
        }
        for (String path : new String[] { USER_PAGES, TradePresetLayout.PAGES_PATH, "config/gtit/nekovm_pages.json" }) {
            Path actual = files.checked(path);
            if (!Files.exists(actual)) continue;
            JsonObject data = read(actual);
            boolean changed = false;
            if (!data.has("pages") || !data.get("pages")
                .isJsonArray()) throw new IOException("来源迁移停止，标签页文件损坏: " + path);
            for (JsonElement raw : data.getAsJsonArray("pages")) {
                JsonObject page = raw.getAsJsonObject();
                changed |= annotate(
                    page,
                    path,
                    owners.get(
                        page.get("id")
                            .getAsInt()));
            }
            if (changed) changes.put(path, DefaultTradeSources.bytes(data));
        }
        for (String id : TradePresetStore.BUILTIN_IDS) {
            String path = "config/gtit/trade/default_overrides/" + id + ".json";
            Path actual = files.checked(path);
            if (!Files.exists(actual)) continue;
            JsonObject data = read(actual);
            boolean changed = false;
            if (!data.has("tabs") || !data.get("tabs")
                .isJsonArray()) throw new IOException("来源迁移停止，默认覆盖文件损坏: " + path);
            for (JsonElement raw : data.getAsJsonArray("tabs")) changed |= annotate(
                raw.getAsJsonObject()
                    .getAsJsonObject("page"),
                path,
                null);
            if (changed) changes.put(path, DefaultTradeSources.bytes(data));
        }
        if (state != null && state.has("ownedTabs")
            && state.get("ownedTabs")
                .isJsonArray()) {
            boolean changed = false;
            for (JsonElement raw : state.getAsJsonArray("ownedTabs")) {
                JsonObject owned = raw.getAsJsonObject();
                JsonElement snapshot = owned.get("page");
                if (snapshot != null && snapshot.isJsonObject()) changed |= annotate(
                    snapshot.getAsJsonObject(),
                    TradePresetLayout.PAGES_PATH,
                    owned.get("presetId")
                        .getAsString());
            }
            if (changed) changes.put(STATE, DefaultTradeSources.bytes(state));
        }
        // Keep exact originals; repeating this migration never replaces the first backup.
        Map<String, byte[]> backups = new LinkedHashMap<>();
        for (String path : changes.keySet()) {
            String backup = "config/gtit/trade/default_migration_backup/source_identity_" + path.replace('/', '_');
            if (!Files.exists(files.checked(backup))) backups.put(backup, Files.readAllBytes(files.checked(path)));
        }
        changes.putAll(backups);
        if (!changes.isEmpty()) files.commit(changes);
    }

    private static boolean annotate(JsonObject page, String storage, String owner) {
        if (page.has("source") && !page.get("source")
            .isJsonNull()) return false;
        NekoPageEntry entry = TradePresetStore.GSON.fromJson(page, NekoPageEntry.class);
        page.addProperty("source", derive(entry, storage, owner).name());
        return true;
    }

    private static JsonObject read(Path path) throws IOException {
        return new JsonParser().parse(DefaultTradeSources.read(path))
            .getAsJsonObject();
    }
}

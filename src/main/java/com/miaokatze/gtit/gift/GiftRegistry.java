package com.miaokatze.gtit.gift;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.miaokatze.gtit.config.DefaultContentConfig;

/** Trusted server API. Network callers must enforce operator permission and payload bounds. */
public final class GiftRegistry {

    public static final GiftRegistry INSTANCE = new GiftRegistry();
    public static final int MAX_DEFINITIONS = 128;
    public static final int MAX_DEFINITION_BYTES = 128 * 1024;

    public static final class Snapshot {

        public long revision;
        public boolean defaultsEnabled = true;
        public boolean defaultsInitialized;
        public String updatePrompt = "";
        public List<GiftDefinition> definitions = new ArrayList<>();
        public int defaultVersion;
        public int evaluatedDefaultVersion;
        public int forcedVersion;
        public int skippedVersion;
        public boolean updateNotice = true;
        public boolean forcedNotificationPending;
        public List<String> pendingDefaultIds = new ArrayList<>();
    }

    private Path file;
    private Snapshot state;

    public synchronized void init(Path path) throws IOException {
        file = path;
        state = null;
        Snapshot loaded = Files.exists(path) ? GiftFiles.read(path, Snapshot.class) : new Snapshot();
        validate(loaded);
        boolean changed = !Files.exists(path);
        if (changed) {
            if (DefaultContentConfig.loadDefaultGifts()) {
                for (GiftDefinition gift : GiftDefaults.createMigratingLegacy()) {
                    gift.revision = 1;
                    loaded.definitions.add(gift);
                }
                loaded.defaultsInitialized = true;
            }
            loaded.defaultVersion = GiftDefaults.VERSION;
            loaded.evaluatedDefaultVersion = GiftDefaults.VERSION;
            loaded.forcedVersion = GiftDefaults.FORCED_VERSION;
        } else if (loaded.defaultsEnabled && DefaultContentConfig.loadDefaultGifts() && !loaded.defaultsInitialized) {
            replaceDefaults(loaded);
            loaded.defaultsInitialized = true;
            loaded.defaultVersion = GiftDefaults.VERSION;
            loaded.evaluatedDefaultVersion = GiftDefaults.VERSION;
            loaded.forcedVersion = GiftDefaults.FORCED_VERSION;
            changed = true;
        } else if (loaded.defaultsEnabled && DefaultContentConfig.loadDefaultGifts()
            && loaded.forcedVersion < GiftDefaults.FORCED_VERSION) {
                replaceDefaults(loaded);
                loaded.forcedVersion = GiftDefaults.FORCED_VERSION;
                loaded.defaultVersion = GiftDefaults.VERSION;
                loaded.evaluatedDefaultVersion = GiftDefaults.VERSION;
                loaded.pendingDefaultIds.clear();
                loaded.forcedNotificationPending = true;
                changed = true;
            } else if (loaded.evaluatedDefaultVersion != GiftDefaults.VERSION && loaded.defaultsEnabled
                && DefaultContentConfig.loadDefaultGifts()) {
                    evaluateDefaultUpgrade(loaded, GiftDefaults.create(), GiftDefaults.VERSION);
                    changed = true;
                }
        // Add only the new carrier identity. Rewards, edits, deletions and default-update decisions survive.
        for (GiftDefinition gift : loaded.definitions) {
            if (gift.shellKey == null) {
                gift.shellKey = GiftShellCatalog.effectiveKey(gift);
                gift.revision = loaded.revision + 1;
                changed = true;
            }
        }
        refreshPrompt(loaded);
        if (changed) {
            loaded.revision++;
            validate(loaded);
            GiftFiles.write(file, loaded);
        }
        state = loaded;
        syncLegacyFacade();
    }

    public synchronized boolean isReady() {
        return state != null;
    }

    public synchronized Snapshot snapshot() {
        requireReady();
        return copy(state);
    }

    public synchronized GiftDefinition get(String id) {
        requireReady();
        GiftDefinition gift = find(state, id);
        return gift == null ? null : gift.copy();
    }

    public synchronized boolean isEnabled(GiftDefinition gift) {
        requireReady();
        return gift != null && gift.enabled
            && (!gift.builtIn || (state.defaultsEnabled && DefaultContentConfig.loadDefaultGifts()));
    }

    public synchronized GiftDefinition save(GiftDefinition definition, long expectedRevision) {
        requireReady();
        if (definition == null) throw new IllegalArgumentException("Missing gift definition");
        GiftDefinition next = definition.copy();
        if (next.shellKey == null) next.shellKey = GiftShellCatalog.effectiveKey(next);
        next.validate();
        validateResolvedItems(next);
        if (GiftFiles.JSON.toJson(next)
            .getBytes(StandardCharsets.UTF_8).length > MAX_DEFINITION_BYTES) {
            throw new IllegalArgumentException("Gift definition exceeds payload limit");
        }
        GiftDefinition old = find(state, next.id);
        checkRevision(old == null ? 0 : old.revision, expectedRevision);
        if (old == null && GiftDefaults.isBuiltIn(next.id)) throw new IllegalArgumentException("Reserved gift ID");
        if (old == null && state.definitions.size() >= MAX_DEFINITIONS)
            throw new IllegalArgumentException("Too many gifts");
        next.builtIn = old != null && old.builtIn;
        next.revision = state.revision + 1;
        Snapshot updated = copy(state);
        updated.definitions.removeIf(g -> g.id.equals(next.id));
        updated.definitions.add(next);
        persist(updated);
        return next.copy();
    }

    public synchronized void delete(String id, long expectedRevision) {
        requireReady();
        GiftDefinition old = find(state, id);
        if (old == null) throw new IllegalArgumentException("Gift does not exist");
        checkRevision(old.revision, expectedRevision);
        Snapshot updated = copy(state);
        updated.definitions.removeIf(g -> g.id.equals(id));
        persist(updated);
    }

    /** Multiple definitions may share a carrier; token-bearing packages identify their exact snapshot. */
    public synchronized List<GiftDefinition> boundDefinitions(String shellKey) {
        requireReady();
        List<GiftDefinition> result = new ArrayList<>();
        for (GiftDefinition definition : state.definitions) {
            if (isEnabled(definition) && shellKey.equals(GiftShellCatalog.effectiveKey(definition))) {
                result.add(definition.copy());
            }
        }
        return result;
    }

    public synchronized void setDefaultsEnabled(boolean enabled, long expectedRevision) {
        requireReady();
        checkRevision(state.revision, expectedRevision);
        Snapshot updated = copy(state);
        updated.defaultsEnabled = enabled;
        if (enabled && DefaultContentConfig.loadDefaultGifts() && !updated.defaultsInitialized) {
            replaceDefaults(updated);
            updated.defaultsInitialized = true;
            updated.defaultVersion = GiftDefaults.VERSION;
            updated.evaluatedDefaultVersion = GiftDefaults.VERSION;
            updated.forcedVersion = GiftDefaults.FORCED_VERSION;
        }
        refreshPrompt(updated);
        persist(updated);
    }

    public synchronized void resolveDefaultUpdate(String decision, long expectedRevision) {
        requireReady();
        checkRevision(state.revision, expectedRevision);
        Snapshot updated = copy(state);
        switch (decision) {
            case "APPLY":
                if (!updated.defaultsEnabled || !DefaultContentConfig.loadDefaultGifts()) {
                    throw new IllegalStateException("Default gifts are disabled");
                }
                replaceDefaults(updated);
                updated.defaultsInitialized = true;
                updated.defaultVersion = GiftDefaults.VERSION;
                updated.evaluatedDefaultVersion = GiftDefaults.VERSION;
                updated.pendingDefaultIds.clear();
                updated.forcedNotificationPending = false;
                break;
            case "SKIP":
                updated.skippedVersion = GiftDefaults.VERSION;
                updated.pendingDefaultIds.clear();
                break;
            case "DISABLE_NOTICE":
                updated.updateNotice = false;
                break;
            case "ACK":
                updated.forcedNotificationPending = false;
                break;
            default:
                throw new IllegalArgumentException("Invalid update decision");
        }
        refreshPrompt(updated);
        persist(updated);
    }

    /** Legacy /gtit gift configuration commands explicitly update the migrated starter definition. */
    public synchronized void updateLegacyStarter(List<com.miaokatze.gtit.config.GiftConfig.ItemEntry> guaranteed,
        List<com.miaokatze.gtit.config.GiftConfig.ItemEntry> random, int count) {
        if (state == null) return;
        GiftDefinition starter = get("starter");
        if (starter == null) return;
        starter.rewards = new ArrayList<>(guaranteed);
        starter.randomRewards = new ArrayList<>(random);
        starter.randomCount = Math.min(Math.max(0, count), random.size());
        save(starter, starter.revision);
    }

    private void persist(Snapshot updated) {
        updated.revision++;
        try {
            validate(updated);
            GiftFiles.write(file, updated);
            state = updated;
            syncLegacyFacade();
        } catch (IOException e) {
            throw new IllegalStateException("Unable to save gift configuration", e);
        }
    }

    private void syncLegacyFacade() {
        GiftDefinition starter = find(state, "starter");
        if (starter != null) com.miaokatze.gtit.config.GiftConfig
            .acceptStarterDefinition(starter.rewards, starter.randomRewards, starter.randomCount);
    }

    private static void replaceDefaults(Snapshot data) {
        List<GiftDefinition> defaults = GiftDefaults.create();
        long customCount = data.definitions.stream()
            .filter(g -> !g.builtIn)
            .count();
        if (customCount + defaults.size() > MAX_DEFINITIONS) {
            throw new IllegalStateException("Too many custom gifts to load bundled defaults");
        }
        for (GiftDefinition gift : defaults) {
            GiftDefinition old = find(data, gift.id);
            gift.revision = Math.max(data.revision, old == null ? 0 : old.revision) + 1;
        }
        data.definitions.removeIf(g -> g.builtIn);
        data.definitions.addAll(defaults);
    }

    /** Incoming policy is evaluated only once per authored version, never from a user's edited policy. */
    static void evaluateDefaultUpgrade(Snapshot data, List<GiftDefinition> authoredDefaults, int version) {
        if (data.evaluatedDefaultVersion == version) return;
        long additions = authoredDefaults.stream()
            .filter(g -> g.updatePolicy == GiftDefinition.UpdatePolicy.FORCED && find(data, g.id) == null)
            .count();
        if (data.definitions.size() + additions > MAX_DEFINITIONS) {
            throw new IllegalStateException("Too many gifts to apply forced default update");
        }
        data.pendingDefaultIds.clear();
        for (GiftDefinition authored : authoredDefaults) {
            GiftDefinition current = find(data, authored.id);
            if (sameContent(current, authored)) continue;
            if (authored.updatePolicy == GiftDefinition.UpdatePolicy.FORCED) {
                GiftDefinition replacement = authored.copy();
                replacement.revision = Math.max(data.revision, current == null ? 0 : current.revision) + 1;
                data.definitions.removeIf(g -> g.id.equals(replacement.id));
                data.definitions.add(replacement);
                data.forcedNotificationPending = true;
            } else data.pendingDefaultIds.add(authored.id);
        }
        data.evaluatedDefaultVersion = version;
        if (data.pendingDefaultIds.isEmpty()) data.defaultVersion = version;
    }

    private static boolean sameContent(GiftDefinition current, GiftDefinition authored) {
        if (current == null) return false;
        GiftDefinition normalized = current.copy();
        normalized.revision = 0;
        GiftDefinition normalizedAuthored = authored.copy();
        normalizedAuthored.revision = 0;
        return GiftFiles.JSON.toJson(normalized)
            .equals(GiftFiles.JSON.toJson(normalizedAuthored));
    }

    private static void refreshPrompt(Snapshot data) {
        if (!data.defaultsEnabled || !DefaultContentConfig.loadDefaultGifts()) data.updatePrompt = "";
        else if (data.forcedNotificationPending) data.updatePrompt = "FORCE";
        else if (!data.pendingDefaultIds.isEmpty() && data.updateNotice
            && data.skippedVersion != GiftDefaults.VERSION) {
                data.updatePrompt = "ASK";
            } else data.updatePrompt = "";
    }

    private static void validate(Snapshot data) throws IOException {
        if (data.revision < 0 || data.definitions == null
            || data.definitions.size() > MAX_DEFINITIONS
            || data.pendingDefaultIds == null
            || data.pendingDefaultIds.size() > GiftDefaults.IDS.length) {
            throw new IOException("Invalid gift registry");
        }
        Set<String> ids = new HashSet<>();
        try {
            for (GiftDefinition gift : data.definitions) {
                gift.validate();
                if (gift.revision < 1 || gift.revision > data.revision
                    || !ids.add(gift.id)
                    || gift.builtIn != GiftDefaults.isBuiltIn(gift.id)) {
                    throw new IOException("Invalid gift identity");
                }
            }
        } catch (RuntimeException e) {
            throw new IOException("Invalid gift definition", e);
        }
    }

    private static void validateResolvedItems(GiftDefinition gift) {
        List<com.miaokatze.gtit.config.GiftConfig.ItemEntry> entries = new ArrayList<>(gift.rewards);
        entries.addAll(gift.randomRewards);
        if (gift.icon != null) entries.add(gift.icon);
        for (var entry : entries) {
            if (entry.toItemStack() == null)
                throw new IllegalArgumentException("Gift item is unavailable: " + entry.itemId);
        }
        int stacks = 0;
        for (var entry : gift.rewards) stacks += stackCount(entry);
        List<Integer> randomCounts = new ArrayList<>();
        for (var entry : gift.randomRewards) randomCounts.add(stackCount(entry));
        randomCounts.sort(java.util.Comparator.reverseOrder());
        for (int i = 0; i < gift.randomCount; i++) stacks += randomCounts.get(i);
        if (stacks > 4096) throw new IllegalArgumentException("Gift exceeds 4096 reward stacks");
    }

    private static int stackCount(com.miaokatze.gtit.config.GiftConfig.ItemEntry entry) {
        int maximum = Math.min(
            64,
            entry.toItemStack()
                .getMaxStackSize());
        if (maximum < 1) throw new IllegalArgumentException("Invalid gift stack size");
        return (entry.amount + maximum - 1) / maximum;
    }

    private static Snapshot copy(Snapshot value) {
        return GiftFiles.JSON.fromJson(GiftFiles.JSON.toJson(value), Snapshot.class);
    }

    private static GiftDefinition find(Snapshot data, String id) {
        for (GiftDefinition gift : data.definitions) if (gift.id.equals(id)) return gift;
        return null;
    }

    private static void checkRevision(long actual, long expected) {
        if (actual != expected) throw new IllegalStateException("Gift changed; refresh before saving");
    }

    private void requireReady() {
        if (state == null) throw new IllegalStateException("Gift registry is unavailable");
    }
}

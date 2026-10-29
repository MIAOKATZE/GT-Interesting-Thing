package com.miaokatze.gtit.trade.v2;

import java.io.File;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.gtnewhorizon.gtnhlib.teams.Team;
import com.miaokatze.gtit.trade.NekoTeamData;
import com.miaokatze.gtit.trade.TeamDataProvider;
import com.miaokatze.gtit.util.AtomicNbtFile;

/** Server-authoritative histories; corrupt or unsaved state never becomes a fresh redemption ledger. */
public class NekoHistoryManager {

    private static final Logger LOG = LogManager.getLogger("gtit");
    public static final NekoHistoryManager INSTANCE = new NekoHistoryManager();
    private static final String LEGACY_CONSUMED_KEY = "legacyHistoryConsumed";
    private static final String FORMAT_KEY = "historyFormatVersion";
    private static final String JOURNAL_NAME = "legacy-migrations.dat";

    private final ConcurrentHashMap<UUID, ConcurrentHashMap<UUID, NekoTradeHistory>> histories =
        new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Boolean> legacyHistoryConsumed = new ConcurrentHashMap<>();
    /** Durable, immutable migration intents, retained so a lagging team save can be replayed. */
    private final Map<UUID, LegacyMigration> migrations = new HashMap<>();
    private final Set<UUID> pendingMigrationMarkers = new HashSet<>();
    private File saveDir;
    private boolean acceptingHistories;

    private static final class LegacyMigration {
        final UUID teamId;
        final Map<UUID, NekoTradeHistory> records;

        LegacyMigration(UUID teamId, Map<UUID, NekoTradeHistory> records) {
            this.teamId = teamId;
            this.records = records;
        }
    }

    private NekoHistoryManager() {}

    public void init(World world) {
        if (world == null) throw new IllegalArgumentException("world");
        bindSaveDirectory(new File(world.getSaveHandler().getWorldDirectory(), "gtit_neko_histories"));
    }

    private synchronized void bindSaveDirectory(File directory) {
        acceptingHistories = false;
        try {
            if (!saveAllInternal()) throw new IllegalStateException("Previous world's histories remain unsaved: " + saveDir);
            File target = directory.getCanonicalFile();
            Files.createDirectories(target.toPath());
            Map<UUID, LegacyMigration> loaded = readMigrations(new File(target, JOURNAL_NAME));
            histories.clear();
            pendingMigrationMarkers.clear();
            legacyHistoryConsumed.clear();
            migrations.clear();
            migrations.putAll(loaded);
            saveDir = target;
            acceptingHistories = true;
            LOG.info("Neko trade history directory: {}", saveDir.getAbsolutePath());
        } catch (Exception failure) {
            throw new IllegalStateException("History storage is not ready for this world", failure);
        }
    }

    private void requireReady() {
        if (!acceptingHistories) throw new IllegalStateException("History storage is not ready for this world");
    }

    /** Resolve before acquiring a history monitor. Completion saves only after releasing that monitor. */
    public synchronized NekoTradeHistory getHistory(UUID playerId, UUID tradeGroupId) {
        requireReady();
        if (playerId == null || tradeGroupId == null) throw new IllegalArgumentException("history identity");
        Team team = TeamDataProvider.getTeam(playerId);
        NekoTeamData data = TeamDataProvider.getData(team);
        if (data != null) {
            migrateLegacyHistory(team, data);
            return data.getTradeHistory(tradeGroupId);
        }
        return getPersonalHistoryMap(playerId).computeIfAbsent(tradeGroupId, ignored -> new NekoTradeHistory());
    }

    public synchronized void resetHistory(UUID playerId, UUID tradeGroupId) {
        getHistory(playerId, tradeGroupId).reset();
        markDirty(playerId);
    }

    public synchronized void resetAllHistory(UUID playerId) {
        requireReady();
        if (playerId == null) return;
        Team team = TeamDataProvider.getTeam(playerId);
        NekoTeamData data = TeamDataProvider.getData(team);
        if (data != null) {
            migrateLegacyHistory(team, data);
            data.resetAllTradeHistories(); // Migration receipts deliberately survive an explicit admin reset.
            TeamDataProvider.markTeamDirty(team);
        } else {
            for (NekoTradeHistory history : getPersonalHistoryMap(playerId).values()) history.reset();
            saveHistory(playerId);
        }
    }

    public synchronized void markDirty(UUID playerId) {
        if (playerId == null) return;
        if (acceptingHistories) {
            Team team = TeamDataProvider.getTeam(playerId);
            if (TeamDataProvider.getData(team) != null) TeamDataProvider.markTeamDirty(team);
        }
        // Also retain/save frozen personal fallback records after a membership change.
        saveHistory(playerId);
    }

    /** Keep canonical references until the world ends, including on a failed logout save. */
    public synchronized void unloadPlayer(UUID playerId) {
        markDirty(playerId);
    }

    public synchronized void saveAll() {
        saveAllInternal();
    }

    private boolean saveAllInternal() {
        boolean success = true;
        for (UUID playerId : histories.keySet()) success &= saveHistory(playerId);
        return success;
    }

    /** Save before eviction; never erase the only copy of an unsuccessful write. */
    public synchronized void clearAll() {
        if (!saveAllInternal()) throw new IllegalStateException("Cannot clear unsaved trade histories");
        histories.clear();
        pendingMigrationMarkers.clear();
        legacyHistoryConsumed.clear();
    }

    /** Stops access immediately; pending data remains bound to the old directory until a successful retry. */
    public synchronized void unloadAll() {
        acceptingHistories = false;
        if (!saveAllInternal()) {
            LOG.error("Trade history save failed; retaining the old world binding and refusing new access");
            return;
        }
        histories.clear();
        pendingMigrationMarkers.clear();
        legacyHistoryConsumed.clear();
        migrations.clear();
        saveDir = null;
    }

    private ConcurrentHashMap<UUID, NekoTradeHistory> getPersonalHistoryMap(UUID playerId) {
        return histories.computeIfAbsent(playerId, this::loadHistory);
    }

    /**
     * Read/validate every source first. Persist intent before changing either ledger. Each team stores
     * a receipt alongside its merged history, so a crash before team-save replays exactly once.
     * Do not use the old team-wide migrated bit to skip new members or pending intents.
     */
    private void migrateLegacyHistory(Team team, NekoTeamData data) {
        migrateLegacyHistory(team.getTeamId(), new HashSet<>(team.getMembers()), data,
            () -> TeamDataProvider.markTeamDirty(team));
    }

    /** Package-private transaction seam; the public entry resolves actual GTNHLib membership above. */
    synchronized void migrateLegacyHistory(UUID teamId, Set<UUID> members, NekoTeamData data, Runnable markDirty) {
        requireReady();
        Map<UUID, LegacyMigration> staged = new HashMap<>(migrations);
        Map<UUID, Map<UUID, NekoTradeHistory>> liveSources = new HashMap<>();
        for (UUID playerId : members) {
            if (playerId == null || migrations.containsKey(playerId)) continue;
            Map<UUID, NekoTradeHistory> source = getPersonalHistoryMap(playerId);
            if (!legacyHistoryConsumed.containsKey(playerId)) {
                staged.put(playerId, new LegacyMigration(teamId, copyHistories(source)));
                liveSources.put(playerId, source);
            }
        }
        if (staged.size() != migrations.size()) {
            try {
                writeMigrations(staged);
            } catch (Exception failure) {
                throw new IllegalStateException("Cannot commit legacy history migration intent", failure);
            }
            // The durable journal now owns the legacy records, even if a marker write fails below.
            migrations.clear();
            migrations.putAll(staged);
            for (Map.Entry<UUID, Map<UUID, NekoTradeHistory>> entry : liveSources.entrySet()) {
                UUID playerId = entry.getKey();
                // Consume live records rather than merely copying them: late completions through
                // an already captured reference must follow the same surviving team owner.
                if (data.consumeLegacyHistory(playerId, entry.getValue())) markDirty.run();
                histories.put(playerId, new ConcurrentHashMap<>());
                legacyHistoryConsumed.put(playerId, Boolean.TRUE);
                pendingMigrationMarkers.add(playerId);
            }
        }

        for (Map.Entry<UUID, LegacyMigration> entry : migrations.entrySet()) {
            UUID playerId = entry.getKey();
            LegacyMigration intent = entry.getValue();
            if (intent.teamId.equals(teamId) || members.contains(playerId)) {
                if (data.applyLegacyHistory(playerId, intent.records)) markDirty.run();
                if (members.contains(playerId)) {
                    // Always retry the marker, including intents recovered before the first new-world lookup.
                    getPersonalHistoryMap(playerId);
                    legacyHistoryConsumed.put(playerId, Boolean.TRUE);
                    if (pendingMigrationMarkers.contains(playerId)) saveHistory(playerId);
                }
            }
        }
        if (!data.isLegacyHistoryMigrated()) {
            data.setLegacyHistoryMigrated(true);
            markDirty.run();
        }
    }

    private Map<UUID, NekoTradeHistory> copyHistories(Map<UUID, NekoTradeHistory> source) {
        Map<UUID, NekoTradeHistory> result = new HashMap<>();
        if (source != null) for (Map.Entry<UUID, NekoTradeHistory> entry : source.entrySet()) {
            result.put(entry.getKey(), entry.getValue().copy());
        }
        return result;
    }

    private boolean saveHistory(UUID playerId) {
        Map<UUID, NekoTradeHistory> current = histories.get(playerId);
        if (current == null) return true;
        if (saveDir == null) return false;
        try {
            NBTTagCompound root = new NBTTagCompound();
            root.setInteger(FORMAT_KEY, 1);
            root.setTag("histories", writeHistoryList(copyHistories(current)));
            root.setBoolean(LEGACY_CONSUMED_KEY, legacyHistoryConsumed.containsKey(playerId));
            AtomicNbtFile.write(root, new File(saveDir, playerId + ".dat"));
            pendingMigrationMarkers.remove(playerId);
            return true;
        } catch (Exception failure) {
            LOG.error("Unable to save Neko trade history: " + playerId, failure);
            return false;
        }
    }

    private ConcurrentHashMap<UUID, NekoTradeHistory> loadHistory(UUID playerId) {
        if (saveDir == null) throw new IllegalStateException("No history directory");
        File file = new File(saveDir, playerId + ".dat");
        if (Files.notExists(file.toPath())) {
            if (migrations.containsKey(playerId)) {
                legacyHistoryConsumed.put(playerId, Boolean.TRUE);
                pendingMigrationMarkers.add(playerId);
            }
            return new ConcurrentHashMap<>();
        }
        try {
            NBTTagCompound root = CompressedStreamTools.read(file);
            if (root == null || !root.hasKey("histories", 9)) throw new IllegalArgumentException("Missing histories list");
            boolean versioned = root.hasKey(FORMAT_KEY);
            if (versioned && (!root.hasKey(FORMAT_KEY, 3) || root.getInteger(FORMAT_KEY) != 1)) {
                throw new IllegalArgumentException("Unsupported history format");
            }
            if (root.hasKey(LEGACY_CONSUMED_KEY) && !root.hasKey(LEGACY_CONSUMED_KEY, 1)) {
                throw new IllegalArgumentException("Invalid legacy consumed marker");
            }
            ConcurrentHashMap<UUID, NekoTradeHistory> loaded = readHistoryList(root, "histories");
            boolean consumed = root.getBoolean(LEGACY_CONSUMED_KEY);
            if (consumed || migrations.containsKey(playerId)) legacyHistoryConsumed.put(playerId, Boolean.TRUE);
            if (migrations.containsKey(playerId) && (!versioned || !consumed)) pendingMigrationMarkers.add(playerId);
            // Old consumed records are archival input, not fresh fallback activity. A journal also
            // supersedes an old/unmarked source if the crash occurred before its marker was installed.
            if ((!versioned && consumed) || (!consumed && migrations.containsKey(playerId))) loaded.clear();
            return loaded;
        } catch (Exception failure) {
            LOG.error("Unable to load Neko history; preserving the original file: " + playerId, failure);
            throw new IllegalStateException("Cannot load trade history " + playerId, failure);
        }
    }

    private static NBTTagList writeHistoryList(Map<UUID, NekoTradeHistory> source) {
        NBTTagList list = new NBTTagList();
        for (Map.Entry<UUID, NekoTradeHistory> entry : source.entrySet()) {
            NBTTagCompound record = new NBTTagCompound();
            record.setString("groupId", entry.getKey().toString());
            record.setTag("history", entry.getValue().writeToNBT());
            list.appendTag(record);
        }
        return list;
    }

    private static ConcurrentHashMap<UUID, NekoTradeHistory> readHistoryList(NBTTagCompound root, String key) {
        if (!root.hasKey(key, 9)) throw new IllegalArgumentException("Missing history list");
        // getTagList silently replaces a list with the wrong element type. Inspect the raw list first.
        NBTTagList raw = (NBTTagList) root.getTag(key);
        NBTTagList list = root.getTagList(key, 10);
        if (raw.tagCount() != list.tagCount()) throw new IllegalArgumentException("Invalid history list type");
        ConcurrentHashMap<UUID, NekoTradeHistory> result = new ConcurrentHashMap<>();
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound record = list.getCompoundTagAt(i);
            if (!record.hasKey("groupId", 8) || !record.hasKey("history", 10)) {
                throw new IllegalArgumentException("Invalid history entry");
            }
            UUID groupId = UUID.fromString(record.getString("groupId"));
            NekoTradeHistory history = new NekoTradeHistory();
            history.loadFromNBT(record.getCompoundTag("history"));
            if (result.put(groupId, history) != null) throw new IllegalArgumentException("Duplicate history group");
        }
        return result;
    }

    private void writeMigrations(Map<UUID, LegacyMigration> source) throws java.io.IOException {
        NBTTagCompound root = new NBTTagCompound();
        root.setInteger("version", 1);
        NBTTagList list = new NBTTagList();
        for (Map.Entry<UUID, LegacyMigration> entry : source.entrySet()) {
            NBTTagCompound record = new NBTTagCompound();
            record.setString("playerId", entry.getKey().toString());
            record.setString("teamId", entry.getValue().teamId.toString());
            record.setTag("histories", writeHistoryList(entry.getValue().records));
            list.appendTag(record);
        }
        root.setTag("migrations", list);
        AtomicNbtFile.write(root, new File(saveDir, JOURNAL_NAME));
    }

    private Map<UUID, LegacyMigration> readMigrations(File file) throws java.io.IOException {
        Map<UUID, LegacyMigration> result = new HashMap<>();
        if (Files.notExists(file.toPath())) return result;
        NBTTagCompound root = CompressedStreamTools.read(file);
        if (root == null || !root.hasKey("version", 3) || root.getInteger("version") != 1
            || !root.hasKey("migrations", 9)) throw new IllegalArgumentException("Invalid legacy migration journal");
        NBTTagList raw = (NBTTagList) root.getTag("migrations"), list = root.getTagList("migrations", 10);
        if (raw.tagCount() != list.tagCount()) throw new IllegalArgumentException("Invalid migration list type");
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound record = list.getCompoundTagAt(i);
            UUID playerId = UUID.fromString(record.getString("playerId"));
            LegacyMigration migration = new LegacyMigration(
                UUID.fromString(record.getString("teamId")), readHistoryList(record, "histories"));
            if (result.put(playerId, migration) != null) throw new IllegalArgumentException("Duplicate migration identity");
        }
        return result;
    }
}

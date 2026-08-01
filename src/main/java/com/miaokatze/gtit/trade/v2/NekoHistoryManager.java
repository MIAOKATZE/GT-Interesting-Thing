package com.miaokatze.gtit.trade.v2;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;

import com.gtnewhorizon.gtnhlib.teams.ITeamData;
import com.gtnewhorizon.gtnhlib.teams.Team;
import com.gtnewhorizon.gtnhlib.teams.TeamManager;
import com.miaokatze.gtit.main.GTInterestingThing;
import com.miaokatze.gtit.trade.NekoTeamData;

/**
 * Server-authoritative trade history manager.
 *
 * <p>
 * Team histories live in GTNHLib team data. The old per-player files are
 * retained only as a one-time migration source and are marked consumed after
 * migration so leaving and rejoining a team cannot duplicate consumption.
 * </p>
 */
public class NekoHistoryManager {

    public static final NekoHistoryManager INSTANCE = new NekoHistoryManager();

    private static final String LEGACY_CONSUMED_KEY = "legacyHistoryConsumed";

    /** Personal fallback histories used when no usable team data exists. */
    private final ConcurrentHashMap<UUID, ConcurrentHashMap<UUID, NekoTradeHistory>> histories;
    /** Player files whose legacy records have already been consumed by a team. */
    private final ConcurrentHashMap<UUID, Boolean> legacyHistoryConsumed;

    private File saveDir;

    private NekoHistoryManager() {
        histories = new ConcurrentHashMap<>();
        legacyHistoryConsumed = new ConcurrentHashMap<>();
    }

    public synchronized void init(World world) {
        if (world == null) {
            return;
        }
        saveDir = new File(
            world.getSaveHandler()
                .getWorldDirectory(),
            "gtit_neko_histories");
        if (!saveDir.exists()) {
            saveDir.mkdirs();
        }
        histories.clear();
        legacyHistoryConsumed.clear();
        GTInterestingThing.LOG.info("Neko trade history directory: {}", saveDir.getAbsolutePath());
    }

    /** Returns the shared team record when possible, otherwise the personal record. */
    public NekoTradeHistory getHistory(UUID playerId, UUID tradeGroupId) {
        if (playerId == null || tradeGroupId == null) {
            return new NekoTradeHistory();
        }

        Team team = findTeam(playerId);
        NekoTeamData teamData = getTeamData(team);
        if (teamData != null) {
            migrateLegacyHistory(team, teamData);
            return teamData.getTradeHistory(tradeGroupId);
        }
        return getPersonalHistory(playerId, tradeGroupId);
    }

    public void resetHistory(UUID playerId, UUID tradeGroupId) {
        if (playerId == null || tradeGroupId == null) {
            return;
        }
        NekoTradeHistory history = getHistory(playerId, tradeGroupId);
        history.reset();
        markDirty(playerId);
    }

    /** Resets all materialized shared histories for a team or all personal histories. */
    public void resetAllHistory(UUID playerId) {
        if (playerId == null) {
            return;
        }

        Team team = findTeam(playerId);
        NekoTeamData teamData = getTeamData(team);
        if (teamData != null) {
            migrateLegacyHistory(team, teamData);
            teamData.resetAllTradeHistories();
            markTeamDirty(team);
            return;
        }

        ConcurrentHashMap<UUID, NekoTradeHistory> playerHistories = getPersonalHistoryMap(playerId);
        for (NekoTradeHistory history : playerHistories.values()) {
            history.reset();
        }
        markDirty(playerId);
    }

    /** Persists team data when shared, or the personal fallback file otherwise. */
    public void markDirty(UUID playerId) {
        if (playerId == null) {
            return;
        }
        Team team = findTeam(playerId);
        if (getTeamData(team) != null) {
            markTeamDirty(team);
            return;
        }
        saveHistory(playerId);
    }

    public void unloadPlayer(UUID playerId) {
        if (playerId == null) {
            return;
        }

        Team team = findTeam(playerId);
        NekoTeamData teamData = getTeamData(team);
        if (teamData != null) {
            // Capture a solo history that was loaded before the player joined the team.
            migrateLegacyHistory(team, teamData);
            histories.remove(playerId);
            return;
        }

        markDirty(playerId);
        histories.remove(playerId);
    }

    public void saveAll() {
        for (UUID playerId : histories.keySet()) {
            saveHistory(playerId);
        }
    }

    public void clearAll() {
        histories.clear();
        legacyHistoryConsumed.clear();
    }

    private NekoTradeHistory getPersonalHistory(UUID playerId, UUID tradeGroupId) {
        ConcurrentHashMap<UUID, NekoTradeHistory> playerHistories = getPersonalHistoryMap(playerId);
        return playerHistories.computeIfAbsent(tradeGroupId, ignored -> new NekoTradeHistory());
    }

    private ConcurrentHashMap<UUID, NekoTradeHistory> getPersonalHistoryMap(UUID playerId) {
        ConcurrentHashMap<UUID, NekoTradeHistory> playerHistories = histories.get(playerId);
        if (playerHistories != null) {
            return playerHistories;
        }

        playerHistories = loadHistory(playerId);
        if (playerHistories == null) {
            playerHistories = new ConcurrentHashMap<>();
        }
        ConcurrentHashMap<UUID, NekoTradeHistory> existing = histories.putIfAbsent(playerId, playerHistories);
        return existing == null ? playerHistories : existing;
    }

    private Team findTeam(UUID playerId) {
        if (playerId == null) {
            return null;
        }
        try {
            return TeamManager.getTeamByPlayer(playerId);
        } catch (NoClassDefFoundError ignored) {
            return null;
        } catch (Exception e) {
            GTInterestingThing.LOG.warn("Unable to resolve team for trade history: " + playerId, e);
            return null;
        }
    }

    private NekoTeamData getTeamData(Team team) {
        if (team == null) {
            return null;
        }
        try {
            ITeamData data = team.getData(NekoTeamData.ID);
            return data instanceof NekoTeamData ? (NekoTeamData) data : null;
        } catch (NoClassDefFoundError ignored) {
            return null;
        } catch (Exception e) {
            GTInterestingThing.LOG.warn("Unable to resolve Neko team data", e);
            return null;
        }
    }

    /** Performs the migration while the team data monitor is held. */
    private void migrateLegacyHistory(Team team, NekoTeamData teamData) {
        if (team == null || teamData == null) {
            return;
        }
        synchronized (teamData) {
            if (teamData.isLegacyHistoryMigrated()) {
                return;
            }

            for (UUID memberId : team.getMembers()) {
                if (memberId == null) {
                    continue;
                }
                Map<UUID, NekoTradeHistory> legacy = loadLegacyHistorySnapshot(memberId);
                for (Map.Entry<UUID, NekoTradeHistory> entry : legacy.entrySet()) {
                    teamData.mergeTradeHistory(entry.getKey(), entry.getValue());
                }
                markLegacyHistoryConsumed(memberId, legacy);
            }

            teamData.setLegacyHistoryMigrated(true);
            markTeamDirty(team);
        }
    }

    /** Reads current in-memory data first, then the old personal file. */
    private Map<UUID, NekoTradeHistory> loadLegacyHistorySnapshot(UUID playerId) {
        Map<UUID, NekoTradeHistory> snapshot = new HashMap<>();
        if (legacyHistoryConsumed.containsKey(playerId)) {
            return snapshot;
        }

        ConcurrentHashMap<UUID, NekoTradeHistory> current = histories.get(playerId);
        if (current != null) {
            for (Map.Entry<UUID, NekoTradeHistory> entry : current.entrySet()) {
                snapshot.put(
                    entry.getKey(),
                    entry.getValue()
                        .copy());
            }
            return snapshot;
        }

        ConcurrentHashMap<UUID, NekoTradeHistory> loaded = loadHistory(playerId);
        if (loaded != null) {
            for (Map.Entry<UUID, NekoTradeHistory> entry : loaded.entrySet()) {
                snapshot.put(
                    entry.getKey(),
                    entry.getValue()
                        .copy());
            }
        }
        return snapshot;
    }

    private void markLegacyHistoryConsumed(UUID playerId, Map<UUID, NekoTradeHistory> snapshot) {
        legacyHistoryConsumed.put(playerId, Boolean.TRUE);
        if (saveDir == null) {
            return;
        }

        ConcurrentHashMap<UUID, NekoTradeHistory> current = histories.get(playerId);
        Map<UUID, NekoTradeHistory> source = current == null ? snapshot : copyHistories(current);
        writeHistoryFile(playerId, source, true);
    }

    private Map<UUID, NekoTradeHistory> copyHistories(Map<UUID, NekoTradeHistory> source) {
        Map<UUID, NekoTradeHistory> result = new HashMap<>();
        if (source == null) {
            return result;
        }
        for (Map.Entry<UUID, NekoTradeHistory> entry : source.entrySet()) {
            result.put(
                entry.getKey(),
                entry.getValue()
                    .copy());
        }
        return result;
    }

    private void saveHistory(UUID playerId) {
        if (playerId == null || saveDir == null) {
            return;
        }
        ConcurrentHashMap<UUID, NekoTradeHistory> playerHistories = histories.get(playerId);
        boolean consumed = legacyHistoryConsumed.containsKey(playerId);
        if ((playerHistories == null || playerHistories.isEmpty()) && !consumed) {
            return;
        }
        writeHistoryFile(playerId, copyHistories(playerHistories), consumed);
    }

    private void writeHistoryFile(UUID playerId, Map<UUID, NekoTradeHistory> source, boolean consumed) {
        File file = new File(saveDir, playerId.toString() + ".dat");
        try {
            NBTTagCompound root = new NBTTagCompound();
            NBTTagList historyList = new NBTTagList();
            for (Map.Entry<UUID, NekoTradeHistory> entry : source.entrySet()) {
                NBTTagCompound historyNbt = new NBTTagCompound();
                historyNbt.setString(
                    "groupId",
                    entry.getKey()
                        .toString());
                historyNbt.setTag(
                    "history",
                    entry.getValue()
                        .writeToNBT());
                historyList.appendTag(historyNbt);
            }
            root.setTag("histories", historyList);
            root.setBoolean(LEGACY_CONSUMED_KEY, consumed);
            CompressedStreamTools.safeWrite(root, file);
        } catch (Exception e) {
            GTInterestingThing.LOG.error("Unable to save Neko trade history: " + playerId, e);
        }
    }

    private ConcurrentHashMap<UUID, NekoTradeHistory> loadHistory(UUID playerId) {
        if (saveDir == null || playerId == null) {
            return null;
        }
        File file = new File(saveDir, playerId.toString() + ".dat");
        if (!file.exists()) {
            return null;
        }
        try {
            NBTTagCompound root = CompressedStreamTools.read(file);
            if (root == null) {
                return null;
            }
            if (root.getBoolean(LEGACY_CONSUMED_KEY)) {
                legacyHistoryConsumed.put(playerId, Boolean.TRUE);
                return new ConcurrentHashMap<>();
            }
            if (!root.hasKey("histories")) {
                return new ConcurrentHashMap<>();
            }
            return readHistoryList(root.getTagList("histories", 10));
        } catch (Exception e) {
            GTInterestingThing.LOG.error("Unable to load Neko trade history: " + playerId, e);
            return null;
        }
    }

    private ConcurrentHashMap<UUID, NekoTradeHistory> readHistoryList(NBTTagList historyList) {
        ConcurrentHashMap<UUID, NekoTradeHistory> result = new ConcurrentHashMap<>();
        for (int i = 0; i < historyList.tagCount(); i++) {
            NBTTagCompound historyNbt = historyList.getCompoundTagAt(i);
            try {
                UUID groupId = UUID.fromString(historyNbt.getString("groupId"));
                NekoTradeHistory history = new NekoTradeHistory();
                history.loadFromNBT(historyNbt.getCompoundTag("history"));
                result.put(groupId, history);
            } catch (IllegalArgumentException e) {
                GTInterestingThing.LOG.warn("Skipping invalid Neko trade group ID: " + historyNbt.getString("groupId"));
            }
        }
        return result;
    }

    private void markTeamDirty(Team team) {
        if (team == null) {
            return;
        }
        try {
            team.markDirty();
        } catch (NoClassDefFoundError ignored) {
            // Team data is unavailable; the caller will use personal persistence.
        } catch (Exception e) {
            GTInterestingThing.LOG.warn("Unable to mark Neko team data dirty", e);
        }
    }
}

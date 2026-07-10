package com.miaokatze.gtit.lottery;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.world.World;

/**
 * 抽奖管理器单例
 */
public class LotteryManager {

    public static final LotteryManager INSTANCE = new LotteryManager();

    private final ConcurrentHashMap<String, LotteryPool> pools;
    private final ConcurrentHashMap<UUID, Integer> pityCounters;
    private final ConcurrentHashMap<UUID, LotteryHistory> histories;
    private File saveDir;

    private LotteryManager() {
        this.pools = new ConcurrentHashMap<>();
        this.pityCounters = new ConcurrentHashMap<>();
        this.histories = new ConcurrentHashMap<>();
    }

    public void init(World world) {
        // TODO: v1.6.4 实现
    }

    public void loadConfig() {
        // TODO: v1.6.4 实现
    }

    public List<LotteryDrawResult> drawLottery(UUID playerId, String poolId, int count) {
        // TODO: v1.6.4 实现
        return new ArrayList<>();
    }

    public LotteryDrawResult drawSingle(UUID playerId, LotteryPool pool) {
        // TODO: v1.6.4 实现
        return null;
    }

    public LotteryEntry selectByWeight(List<LotteryEntry> entries, int pityBonus) {
        // TODO: v1.6.4 实现
        return null;
    }

    public void dispatchPrize(UUID playerId, LotteryEntry entry) {
        // TODO: v1.6.4 实现
    }

    public void recordHistory(UUID playerId, LotteryEntry entry, boolean isPity) {
        // TODO: v1.6.4 实现
    }

    public List<LotteryDrawResult> testDraw(String poolId, int count) {
        // TODO: v1.6.4 实现
        return new ArrayList<>();
    }

    public int getPityCounter(UUID playerId, String poolId) {
        // TODO: v1.6.4 实现
        return 0;
    }

    public LotteryHistory getHistory(UUID playerId) {
        // TODO: v1.6.4 实现
        return histories.get(playerId);
    }

    public void savePityData(UUID playerId) {
        // TODO: v1.6.4 实现
    }

    public void loadPityData(UUID playerId) {
        // TODO: v1.6.4 实现
    }

    public void unloadPlayer(UUID playerId) {
        // TODO: v1.6.4 实现
    }

    public void saveAll() {
        // TODO: v1.6.4 实现
    }

    public void reload() {
        // TODO: v1.6.4 实现
    }

    public LotteryPool getPool(String poolId) {
        return pools.get(poolId);
    }
}

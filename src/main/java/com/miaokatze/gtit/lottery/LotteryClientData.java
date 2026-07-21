package com.miaokatze.gtit.lottery;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 客户端抽奖数据缓存
 * <p>
 * 由 {@link LotterySyncPacket}（全量刷新）与 {@link LotteryResultPacket}（抽取结果）的
 * 客户端处理器写入，{@link LotteryGui} 读取——与 {@code SignInClientData} 同属
 * 「S→C 全量同步 + 客户端静态缓存 + 动态绑定」范式。
 * <p>
 * 服务端侧不会收到同步包，本类在服务端始终保持空数据（GUI 在服务端构建时读到默认值，
 * 不影响服务端逻辑——抽奖判定完全由 {@link LotteryManager} 在服务端权威执行）。
 * <p>
 * <b>缓存内容</b>：
 * <ul>
 * <li>卡池摘要（条目/价格/保底配置，供轮盘与按钮渲染）</li>
 * <li>团队保底计数（poolId → 连续未出高稀有次数）</li>
 * <li>最近抽奖历史（时间倒序，最新在前）</li>
 * <li>最近一次抽取结果列表（驱动轮盘动画与结果展示）</li>
 * </ul>
 */
public final class LotteryClientData {

    // ==================== 抽取结果码 ====================

    /** 无结果（普通状态刷新） */
    public static final int RESULT_NONE = 0;
    /** 抽取成功 */
    public static final int RESULT_SUCCESS = 1;
    /** 余额不足 */
    public static final int RESULT_INSUFFICIENT = 2;
    /** 卡池不存在或无有效条目 */
    public static final int RESULT_POOL_MISSING = 3;
    /** 其他失败（机器无效等） */
    public static final int RESULT_ERROR = 4;

    /** 历史摘要缓存条数（与同步包携带条数一致） */
    public static final int HISTORY_DISPLAY_LIMIT = 20;

    // ==================== 同步状态字段 ====================

    /** 卡池摘要表（poolId → 摘要，LinkedHashMap 保持服务端下发顺序） */
    private static volatile Map<String, PoolSummary> pools = new LinkedHashMap<>();
    /** 团队保底计数（poolId → 连续未出高稀有次数） */
    private static volatile Map<String, Integer> pityCounters = new LinkedHashMap<>();
    /** 团队钱包余额（currencyId → 数量，仅含卡池消耗币种，随同步包刷新） */
    private static volatile Map<String, Integer> balances = new LinkedHashMap<>();
    /** 最近抽奖历史（时间倒序，最新在前，最多 {@link #HISTORY_DISPLAY_LIMIT} 条） */
    private static volatile List<LotteryHistory.HistoryEntry> recentHistory = new ArrayList<>();
    /** 当前选中的卡池 ID（客户端本地状态，跨打开保持） */
    private static volatile String selectedPoolId = "";

    // ==================== 最近一次抽取结果（驱动轮盘动画） ====================

    /** 最近一次抽取的结果列表（1 连 1 条，10 连 10 条） */
    private static volatile List<DrawResult> lastResults = new ArrayList<>();
    /** 最近一次抽取所属卡池 ID（动画按该池槽位布局停格） */
    private static volatile String lastResultPoolId = "";
    /** 最近一次抽取的结果码（{@link #RESULT_NONE} 表示无新结果） */
    private static volatile int lastResultCode = RESULT_NONE;
    /** 结果产生时间（System.currentTimeMillis），动画触发与提示条限时用 */
    private static volatile long lastResultTimeMs = 0L;
    /** 已被动画消费的结果时间戳（防止同一结果重复触发动画） */
    private static volatile long consumedResultTimeMs = 0L;

    private LotteryClientData() {}

    // ==================== 卡池摘要 ====================

    /**
     * 卡池客户端摘要（同步包反序列化产物，供 GUI 渲染）
     */
    public static class PoolSummary {

        /** 卡池 ID */
        public String id = "";
        /** 显示名称 */
        public String name = "";
        /** 消耗货币 ID（neko / shimmeringNeko） */
        public String currencyId = "";
        /** 单抽价格 */
        public int costPerDraw;
        /** 条目列表（顺序与轮盘槽位一一对应） */
        public List<LotteryEntry> entries = new ArrayList<>();
        /** 保底是否启用 */
        public boolean pityEnabled;
        /** 硬保底阈值（0 = 无硬保底） */
        public int hardPityThreshold;
        /** 硬保底保证的最低稀有度名 */
        public String guaranteedRarity = "EPIC";

        /** 单抽总价（count 连抽） */
        public int totalCost(int count) {
            return costPerDraw * Math.max(1, count);
        }
    }

    /**
     * 单次抽取结果（客户端展示/动画用）
     */
    public static class DrawResult {

        /** 条目 ID */
        public String entryId = "";
        /** 稀有度名（{@link LotteryRarity#name()}） */
        public String rarityName = "";
        /** 实际出货数量 */
        public int amount;
        /** 是否保底出货 */
        public boolean isPity;
        /** 是否高稀有度（≥RARE） */
        public boolean isHighRarity;
        /** 轮盘停留格索引（-1 表示无对应格，容错用） */
        public int slotIndex;

        /** 稀有度枚举（容错解析，未知 → COMMON） */
        public LotteryRarity rarity() {
            return LotteryRarity.fromString(rarityName);
        }
    }

    // ==================== 写入（网络包处理器调用） ====================

    /**
     * 全量刷新卡池摘要/保底计数/历史（{@link LotterySyncPacket} 到达时调用）
     *
     * @param newPools    卡池摘要（可为 null 表示不变）
     * @param newPity     保底计数
     * @param newHistory  最近历史（时间倒序）
     * @param newBalances 团队钱包余额（currencyId → 数量）
     */
    public static synchronized void updatePools(List<PoolSummary> newPools, Map<String, Integer> newPity,
        List<LotteryHistory.HistoryEntry> newHistory, Map<String, Integer> newBalances) {
        if (newPools != null) {
            Map<String, PoolSummary> map = new LinkedHashMap<>();
            for (PoolSummary pool : newPools) {
                if (pool != null && pool.id != null) {
                    map.put(pool.id, pool);
                }
            }
            pools = map;
            // 当前选中池被移除时回退到第一个池
            if (!map.containsKey(selectedPoolId)) {
                selectedPoolId = map.isEmpty() ? ""
                    : map.keySet()
                        .iterator()
                        .next();
            }
        }
        if (newPity != null) {
            pityCounters = new LinkedHashMap<>(newPity);
        }
        if (newHistory != null) {
            recentHistory = new ArrayList<>(newHistory);
        }
        if (newBalances != null) {
            balances = new LinkedHashMap<>(newBalances);
        }
    }

    /**
     * 写入最近一次抽取结果（{@link LotteryResultPacket} 到达时调用）
     *
     * @param poolId     抽取卡池
     * @param results    结果列表
     * @param resultCode 结果码（{@link #RESULT_SUCCESS} 等）
     */
    public static synchronized void updateDrawResult(String poolId, List<DrawResult> results, int resultCode) {
        lastResultPoolId = poolId == null ? "" : poolId;
        lastResults = results == null ? new ArrayList<>() : new ArrayList<>(results);
        lastResultCode = resultCode;
        lastResultTimeMs = System.currentTimeMillis();
    }

    /**
     * 标记当前结果已被动画消费（轮盘启动动画后调用，防止重复触发）
     */
    public static synchronized void consumeDrawResult() {
        consumedResultTimeMs = lastResultTimeMs;
    }

    // ==================== 读取（GUI 调用） ====================

    /**
     * 全部卡池摘要（按下发顺序）
     */
    public static List<PoolSummary> getPools() {
        return new ArrayList<>(pools.values());
    }

    /**
     * 按 ID 取卡池摘要（不存在返回 null）
     */
    public static PoolSummary getPool(String poolId) {
        if (poolId == null) return null;
        return pools.get(poolId);
    }

    /**
     * 当前选中卡池 ID（未同步前为空串）
     */
    public static String getSelectedPoolId() {
        return selectedPoolId;
    }

    /**
     * 设置当前选中卡池（卡池切换按钮调用，仅客户端本地状态）
     */
    public static synchronized void setSelectedPoolId(String poolId) {
        if (poolId != null && pools.containsKey(poolId)) {
            selectedPoolId = poolId;
        }
    }

    /**
     * 当前选中卡池摘要（无则 null）
     */
    public static PoolSummary getSelectedPool() {
        return pools.get(selectedPoolId);
    }

    /**
     * 指定池的团队保底计数（连续未出高稀有次数）
     */
    public static int getPityCounter(String poolId) {
        if (poolId == null) return 0;
        Integer count = pityCounters.get(poolId);
        return count == null ? 0 : count;
    }

    /**
     * 指定货币的团队钱包余额（随同步包刷新；未同步前为 0）
     */
    public static int getBalance(String currencyId) {
        if (currencyId == null) return 0;
        Integer amount = balances.get(currencyId);
        return amount == null ? 0 : amount;
    }

    /**
     * 最近抽奖历史（时间倒序，最新在前）
     */
    public static List<LotteryHistory.HistoryEntry> getRecentHistory() {
        return recentHistory;
    }

    /**
     * 最近一次抽取结果列表
     */
    public static List<DrawResult> getLastResults() {
        return lastResults;
    }

    /**
     * 最近一次抽取的卡池 ID
     */
    public static String getLastResultPoolId() {
        return lastResultPoolId;
    }

    /**
     * 最近一次抽取结果码
     */
    public static int getLastResultCode() {
        return lastResultCode;
    }

    /**
     * 最近结果产生时间（毫秒）
     */
    public static long getLastResultTimeMs() {
        return lastResultTimeMs;
    }

    /**
     * 是否有未被动画消费的新抽取结果（轮盘据此启动动画）
     */
    public static boolean hasUnconsumedResult() {
        return lastResultCode == RESULT_SUCCESS && !lastResults.isEmpty() && lastResultTimeMs > consumedResultTimeMs;
    }
}

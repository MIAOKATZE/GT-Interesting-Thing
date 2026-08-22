package com.miaokatze.gtit.lottery;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import com.miaokatze.gtit.trade.v2.NekoBigItemStack;
import com.miaokatze.gtit.util.NbtBase64Util;

import cpw.mods.fml.common.registry.GameRegistry;

/**
 * 客户端抽奖数据缓存
 * <p>
 * 由 {@link LotterySyncPacket}（全量刷新）与
 * {@link LotteryResultPacket}（抽取结果）的客户端处理器写入，{@link LotteryGui} 读取——
 * 与 {@code SignInClientData} 同属
 * 「S→C 全量同步 + 客户端静态缓存 + 动态绑定」范式。
 * <p>
 * 服务端侧不会收到同步包，本类在服务端始终保持空数据（GUI 在服务端构建时读到默认值，
 * 不影响服务端逻辑——抽奖判定完全由 {@link LotteryManager} 在服务端权威执行）。
 * <p>
 * <b>缓存内容</b>：
 * <ul>
 * <li>卡池摘要（条目/价格/保底配置，供轮盘与按钮渲染）</li>
 * <li>团队保底计数（poolId → 连续未出高稀有次数）</li>
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

    // ==================== 同步状态字段 ====================

    /** 卡池摘要表（poolId → 摘要，LinkedHashMap 保持服务端下发顺序） */
    private static volatile Map<String, PoolSummary> pools = new LinkedHashMap<>();
    /** 团队保底计数（poolId → 连续未出高稀有次数） */
    private static volatile Map<String, Integer> pityCounters = new LinkedHashMap<>();
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
        /** 消耗货币 ID（neko / shimmeringNeko）——旧字段，图标/消耗展示的兼容回退 */
        public String currencyId = "";
        /** 单抽价格——旧字段，兼容回退 */
        public int costPerDraw;
        /** page 图标物品 ID（modid:name），空串回退货币图标 */
        public String iconItem = "";
        /** page 图标物品 meta */
        public int iconMeta;
        /** page 图标物品 NBT（Base64，可选） */
        public String iconNbt = "";
        /** 单抽需求物品列表（v1.7.6 货币解绑；猫猫币条目=钱包扣款，其余=输入槽扣除） */
        public List<NekoBigItemStack> costItems = new ArrayList<>();
        /** 条目列表（顺序与轮盘槽位一一对应） */
        public List<LotteryEntry> entries = new ArrayList<>();
        /** 保底是否启用 */
        public boolean pityEnabled;
        /** 软保底阈值（v1.7.6 池编辑面板填充用） */
        public int softPityThreshold = 30;
        /** 软保底每次递增的权重倍率（v1.7.6 池编辑面板填充用） */
        public double softPityIncrement = 5.0;
        /** 硬保底阈值（0 = 无硬保底） */
        public int hardPityThreshold;
        /** 硬保底保证的最低稀有度名 */
        public String guaranteedRarity = "EPIC";

        /** 单抽总价（count 连抽）——旧字段口径，仅货币展示兼容用 */
        public int totalCost(int count) {
            return costPerDraw * Math.max(1, count);
        }

        /**
         * 图标物品堆（page 标签按钮渲染用）
         *
         * @return 图标堆；未设置或物品无法解析时返回 null（调用方回退货币图标）
         */
        public ItemStack toIconItemStack() {
            if (iconItem == null || iconItem.isEmpty()) return null;
            String[] parts = iconItem.split(":", 2);
            if (parts.length < 2) return null;
            Item item = GameRegistry.findItem(parts[0], parts[1]);
            if (item == null) return null;
            ItemStack stack = new ItemStack(item, 1, iconMeta);
            if (iconNbt != null && !iconNbt.isEmpty()) {
                NBTTagCompound nbt = NbtBase64Util.nbtFromBase64(iconNbt);
                if (nbt != null) {
                    stack.setTagCompound(nbt);
                }
            }
            return stack;
        }

        /**
         * 单抽指定币种货币消耗（costItems 中识别为猫猫币的条目合计）
         *
         * @param currencyId 货币 ID
         * @param count      连抽次数
         * @return 总货币消耗；无该币种条目返回 0
         */
        public int currencyCost(String currencyId, int count) {
            if (currencyId == null) return 0;
            int total = 0;
            for (NekoBigItemStack cost : costItems) {
                if (cost == null || cost.getBaseStack() == null) continue;
                String cid = com.miaokatze.gtit.currency.NekoCurrencyRegistrar.getNekoCurrencyId(cost.getBaseStack());
                if (currencyId.equals(cid)) {
                    total += cost.getStackSize();
                }
            }
            return total * Math.max(1, count);
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
     * 全量刷新卡池摘要/保底计数（{@link LotterySyncPacket} 到达时调用；
     * 余额维度自 O2-B01 起由 trade 域 {@code NekoClientBalances} 承载，
     * 同步包处理器另行写入该缓存）
     *
     * @param newPools 卡池摘要（可为 null 表示不变）
     * @param newPity  保底计数
     */
    public static synchronized void updatePools(List<PoolSummary> newPools, Map<String, Integer> newPity) {
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

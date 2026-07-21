package com.miaokatze.gtit.lottery;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 奖池
 * <p>
 * 一个卡池对应一种货币消耗（{@link #nekoCurrencyId} × {@link #costPerDraw} 每抽），
 * 包含若干奖品条目（{@link #entries}，按权重随机）与保底配置（{@link #pityConfig}）。
 * <p>
 * 字段与 Gson 直接映射（lottery.json 的 pools[] 元素），无需手写序列化。
 */
public class LotteryPool {

    /** 卡池 ID（如 "neko"/"shimmering"） */
    private String id;
    /** 显示名称 */
    private String name;
    /** 消耗货币 ID（neko / shimmeringNeko） */
    private String nekoCurrencyId;
    /** 单次抽取消耗数量 */
    private int costPerDraw;
    /** 奖品条目列表 */
    private List<LotteryEntry> entries;
    /** 保底配置 */
    private PityConfig pityConfig;
    /** 权重随机数生成器（transient，不参与 Gson） */
    private transient final Random random = new Random();

    public LotteryPool() {
        this.entries = new ArrayList<>();
    }

    public LotteryPool(String id, String name, String nekoCurrencyId, int costPerDraw, PityConfig pityConfig) {
        this();
        this.id = id;
        this.name = name;
        this.nekoCurrencyId = nekoCurrencyId;
        this.costPerDraw = costPerDraw;
        this.pityConfig = pityConfig;
    }

    /**
     * 全部条目的权重总和（软保底加成后的动态权重不在此计算）
     */
    public int getTotalWeight() {
        int total = 0;
        for (LotteryEntry entry : entries) {
            if (entry != null) total += Math.max(0, entry.getWeight());
        }
        return total;
    }

    /**
     * 从本池中随机选取一个「保底稀有度及以上」的条目（硬保底强制替换用）
     * <p>
     * 在满足 {@code rarity ≥ pityConfig.guaranteedRarity} 的条目子集中按权重随机；
     * 无满足条目时回退到全池随机（配置错误的兜底，避免抽奖死锁）。
     *
     * @return 保底条目；池为空时返回 null
     */
    public LotteryEntry getPityPrizeEntry() {
        if (entries.isEmpty()) return null;
        LotteryRarity guaranteed = pityConfig != null ? pityConfig.getGuaranteedRarity() : LotteryRarity.EPIC;
        List<LotteryEntry> candidates = new ArrayList<>();
        int totalWeight = 0;
        for (LotteryEntry entry : entries) {
            if (entry != null && entry.getRarity()
                .isAtLeast(guaranteed) && entry.getWeight() > 0) {
                candidates.add(entry);
                totalWeight += entry.getWeight();
            }
        }
        // 无保底稀有度条目：回退全池（配置容错）
        if (candidates.isEmpty()) {
            return entries.get(random.nextInt(entries.size()));
        }
        int roll = random.nextInt(totalWeight);
        for (LotteryEntry entry : candidates) {
            roll -= entry.getWeight();
            if (roll < 0) return entry;
        }
        return candidates.get(candidates.size() - 1);
    }

    /**
     * 校验本池是否可抽取：条目非空且总权重 > 0
     */
    public boolean validate() {
        return entries != null && !entries.isEmpty() && getTotalWeight() > 0;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getNekoCurrencyId() {
        return nekoCurrencyId;
    }

    public int getCostPerDraw() {
        return costPerDraw;
    }

    public List<LotteryEntry> getEntries() {
        if (entries == null) entries = new ArrayList<>();
        return entries;
    }

    public PityConfig getPityConfig() {
        if (pityConfig == null) pityConfig = PityConfig.createDefault();
        return pityConfig;
    }

    /** 按条目 ID 查找条目（历史/结果同步展示用） */
    public LotteryEntry getEntryById(String entryId) {
        if (entryId == null) return null;
        for (LotteryEntry entry : getEntries()) {
            if (entry != null && entryId.equals(entry.getId())) return entry;
        }
        return null;
    }
}

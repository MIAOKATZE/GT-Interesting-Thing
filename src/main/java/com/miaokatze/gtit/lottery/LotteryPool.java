package com.miaokatze.gtit.lottery;

import java.util.ArrayList;
import java.util.List;

/**
 * 奖池
 */
public class LotteryPool {

    private String id;
    private String name;
    private String nekoCurrencyId;
    private int costPerDraw;
    private List<LotteryEntry> entries;
    private PityConfig pityConfig;

    public LotteryPool() {
        this.entries = new ArrayList<>();
    }

    public int getTotalWeight() {
        // TODO: v1.6.4 实现
        return 0;
    }

    public LotteryEntry getPityPrizeEntry() {
        // TODO: v1.6.4 实现
        return null;
    }

    public boolean validate() {
        // TODO: v1.6.4 实现
        return false;
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
        return entries;
    }

    public PityConfig getPityConfig() {
        return pityConfig;
    }
}

package com.miaokatze.gtit.lottery;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * 抽奖配置加载
 */
public class LotteryConfig {

    private static final String CONFIG_SUB_PATH = "config/gtit/lottery_pools.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting()
        .disableHtmlEscaping()
        .create();

    public static class LotteryConfigData {

        public java.util.List<LotteryPool> pools;
    }

    public static void init() {
        // TODO: v1.6.4 实现
    }

    public static LotteryConfigData load() {
        // TODO: v1.6.4 实现
        return null;
    }

    public static void save(LotteryConfigData data) {
        // TODO: v1.6.4 实现
    }

    public static LotteryConfigData getDefaultConfig() {
        // TODO: v1.6.4 实现
        return null;
    }

    public static boolean validateAll(LotteryConfigData data) {
        // TODO: v1.6.4 实现
        return false;
    }
}

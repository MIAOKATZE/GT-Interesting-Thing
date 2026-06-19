package com.miaokatze.gtit.trade;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.miaokatze.gtit.main.GTInterestingThing;

/**
 * 猫猫售货机交易配置管理
 * 负责读写 config/gtit/nekovm_trades.json
 */
public class NekoTradeConfig {

    private static final String CONFIG_SUB_PATH = "config/gtit/nekovm_trades.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting()
        .serializeNulls()
        .disableHtmlEscaping()
        .create();

    // --- 内部数据类 ---

    /**
     * 交易配置数据
     */
    public static class NekoTradeData {

        private int version = 1;
        private List<NekoTradeEntry> trades = new ArrayList<>();

        public int getVersion() {
            return version;
        }

        public void setVersion(int version) {
            this.version = version;
        }

        public List<NekoTradeEntry> getTrades() {
            return trades;
        }

        public void setTrades(List<NekoTradeEntry> trades) {
            this.trades = trades;
        }
    }

    // --- 核心方法 ---

    /**
     * 初始化配置，如果配置文件不存在则生成默认配置
     */
    public static synchronized void init() {
        try {
            Path path = getConfigPath();
            if (!Files.exists(path)) {
                Files.createDirectories(path.getParent());
                save(getDefaultTrades());
                GTInterestingThing.LOG.info("猫猫售货机交易配置已生成默认文件: {}", path);
            }
        } catch (Exception e) {
            GTInterestingThing.LOG.error("猫猫售货机交易配置初始化失败", e);
        }
    }

    /**
     * 从文件加载交易数据，文件不存在时返回默认数据
     */
    public static synchronized NekoTradeData load() {
        try {
            Path path = getConfigPath();
            if (!Files.exists(path)) {
                GTInterestingThing.LOG.info("猫猫售货机交易配置文件不存在，返回默认数据");
                return getDefaultTrades();
            }
            String json = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
            NekoTradeData data = GSON.fromJson(json, NekoTradeData.class);
            if (data == null) {
                GTInterestingThing.LOG.warn("猫猫售货机交易配置文件为空，返回默认数据");
                return getDefaultTrades();
            }
            GTInterestingThing.LOG.info("猫猫售货机交易配置已加载");
            return data;
        } catch (Exception e) {
            GTInterestingThing.LOG.error("猫猫售货机交易配置加载失败，返回默认数据", e);
            return getDefaultTrades();
        }
    }

    /**
     * 保存交易数据到文件
     */
    public static synchronized void save(NekoTradeData data) {
        try {
            Path path = getConfigPath();
            Files.createDirectories(path.getParent());
            String json = GSON.toJson(data);
            Files.write(path, json.getBytes(StandardCharsets.UTF_8));
            GTInterestingThing.LOG.info("猫猫售货机交易配置已保存");
        } catch (Exception e) {
            GTInterestingThing.LOG.error("猫猫售货机交易配置保存失败", e);
        }
    }

    /**
     * 生成默认交易数据
     */
    public static NekoTradeData getDefaultTrades() {
        NekoTradeData data = new NekoTradeData();
        data.setTrades(new ArrayList<NekoTradeEntry>());
        return data;
    }

    // --- 辅助方法 ---

    /**
     * 获取配置文件路径
     * <p>
     * Minecraft 服务器的工作目录即为服务器根目录，
     * 因此使用相对路径 "config/gtit/nekovm_trades.json" 即可正确定位。
     */
    private static Path getConfigPath() {
        return Paths.get(CONFIG_SUB_PATH);
    }
}

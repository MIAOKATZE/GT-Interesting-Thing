package com.miaokatze.gtit.trade;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.miaokatze.gtit.config.ConfigMigrationUtil;
import com.miaokatze.gtit.trade.preset.TradePresetLayout;

/**
 * 猫猫售货机标签页配置管理（v1.7.7 G4 存储结构重构）
 * <p>
 * 新路径：{@code config/gtit/trade/pages.json}；旧路径 {@code config/gtit/nekovm_pages.json}
 * 存在时自动迁移，旧文件重命名为 {@code .bak} 保留。
 */
public class NekoPageConfig {

    /** 统一 logger（O2-B02 去中心化：与主类同用 "gtit" logger 名，日志过滤口径不变） */
    private static final Logger LOG = LogManager.getLogger("gtit");

    /** 新路径 */
    private static final String CONFIG_PATH = "config/gtit/trade/pages.json";
    /** 旧路径（兼容迁移用） */
    private static final String LEGACY_CONFIG_PATH = "config/gtit/nekovm_pages.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting()
        .serializeNulls()
        .disableHtmlEscaping()
        .create();

    // --- 内部数据类 ---

    public static class NekoPageData {

        private int version = 1;
        private List<NekoPageEntry> pages = new ArrayList<>();

        public int getVersion() {
            return version;
        }

        public void setVersion(int version) {
            this.version = version;
        }

        public List<NekoPageEntry> getPages() {
            return pages;
        }

        public void setPages(List<NekoPageEntry> pages) {
            this.pages = pages;
        }
    }

    // --- 核心方法 ---

    /**
     * 初始化配置：新路径缺失时优先迁移旧文件，否则生成默认配置
     */
    public static synchronized void init() {
        try {
            Path path = getConfigPath();
            if (!Files.exists(path)) {
                Path legacy = TradeConfigPaths.resolve(LEGACY_CONFIG_PATH);
                if (Files.exists(legacy)) {
                    load(); // load 内部完成迁移
                } else {
                    Files.createDirectories(path.getParent());
                    save(withPresetPages(getDefaultPages()));
                    LOG.info("猫猫售货机标签页配置已生成默认文件: {}", path);
                }
            }
        } catch (Exception e) {
            LOG.error("猫猫售货机标签页配置初始化失败", e);
        }
    }

    /**
     * 从文件加载标签页数据，文件不存在时尝试迁移旧文件，均失败返回默认数据
     */
    public static synchronized NekoPageData load() {
        try {
            new com.miaokatze.gtit.trade.preset.PresetFileTransaction(TradeConfigPaths.gameRoot()).recover();
        } catch (java.io.IOException failure) {
            throw new IllegalStateException("Unresolved trade journal", failure);
        }
        try {
            com.miaokatze.gtit.trade.preset.PageSourceIdentity.migrate(TradeConfigPaths.gameRoot());
        } catch (java.io.IOException | RuntimeException failure) {
            LOG.error("标签页来源迁移失败；保留原文件并使用只读来源推导", failure);
        }
        Path path = getConfigPath();
        if (!Files.exists(path)) {
            Path legacy = TradeConfigPaths.resolve(LEGACY_CONFIG_PATH);
            if (Files.exists(legacy)) {
                try {
                    String json = new String(Files.readAllBytes(legacy), StandardCharsets.UTF_8);
                    NekoPageData data = GSON.fromJson(json, NekoPageData.class);
                    if (data != null && data.getPages() != null
                        && !data.getPages()
                            .isEmpty()) {
                        data = withPresetPages(data);
                        save(data);
                        ConfigMigrationUtil.retireLegacyAsBak(legacy, path, "猫猫售货机标签页配置", "标签页配置文件");
                        return data;
                    }
                } catch (Exception e) {
                    LOG.error("猫猫售货机标签页配置迁移失败，回退默认数据", e);
                }
            }
            LOG.info("猫猫售货机标签页配置文件不存在，返回默认数据");
            return withPresetPagesSafely(getDefaultPages());
        }
        try {
            String json = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
            NekoPageData data = GSON.fromJson(json, NekoPageData.class);
            if (data == null || data.getPages() == null
                || data.getPages()
                    .isEmpty()) {
                LOG.warn("猫猫售货机标签页配置文件为空，返回默认数据");
                return withPresetPages(getDefaultPages());
            }
            return withPresetPages(data);
        } catch (Exception e) {
            LOG.error("猫猫售货机标签页配置加载失败，返回默认数据", e);
            return withPresetPagesSafely(getDefaultPages());
        }
    }

    /**
     * 保存标签页数据到新路径
     */
    public static synchronized void save(NekoPageData data) {
        try {
            com.miaokatze.gtit.trade.preset.PresetFileTransaction files = new com.miaokatze.gtit.trade.preset.PresetFileTransaction(
                TradeConfigPaths.gameRoot());
            files.recover();
            java.util.Map<String, byte[]> changes = new java.util.LinkedHashMap<>();
            planChanges(data, changes);
            files.commit(changes);
        } catch (java.io.IOException failure) {
            throw new IllegalStateException("Page configuration could not be committed", failure);
        }
    }

    static synchronized void planChanges(NekoPageData data, java.util.Map<String, byte[]> changes)
        throws java.io.IOException {
        if (data == null || data.getPages() == null) throw new IllegalArgumentException("page data");
        Path path = getConfigPath();
        NekoPageData user = new NekoPageData();
        NekoPageData managed = new NekoPageData();
        for (NekoPageEntry page : data.getPages()) {
            if (page == null) throw new IllegalArgumentException("null page");
            page.ensureSource();
            if (TradePresetLayout.builtinId(page.getId()) != null) continue;
            (TradePresetLayout.isManagedPage(page.getId()) ? managed : user).getPages()
                .add(page);
        }
        com.miaokatze.gtit.trade.preset.DefaultTradeSources.planPageChanges(data.getPages(), changes);
        changes.put(CONFIG_PATH, pageFileJson(path, user).getBytes(StandardCharsets.UTF_8));
        Path presetPath = TradeConfigPaths.resolve(TradePresetLayout.PAGES_PATH);
        if (Files.exists(presetPath) || !managed.getPages()
            .isEmpty())
            changes
                .put(TradePresetLayout.PAGES_PATH, pageFileJson(presetPath, managed).getBytes(StandardCharsets.UTF_8));
    }

    /** Delete/move operations publish page metadata, source overrides and relocated trades in one journal. */
    public static synchronized void saveWithTrades(NekoPageData pages, NekoTradeConfig.NekoTradeData trades) {
        saveWithTrades(pages, trades, java.util.Collections.emptyMap(), java.util.Collections.emptyMap());
    }

    /** Integration ownership receipts and watermarks share the same commit as their page/trade content. */
    public static synchronized void saveWithTrades(NekoPageData pages, NekoTradeConfig.NekoTradeData trades,
        java.util.Map<Integer, Integer> watermarks, java.util.Map<String, byte[]> metadata) {
        try {
            com.miaokatze.gtit.trade.preset.PresetFileTransaction files = new com.miaokatze.gtit.trade.preset.PresetFileTransaction(
                TradeConfigPaths.gameRoot());
            files.recover();
            java.util.Map<String, byte[]> changes = NekoTradeConfig.planChanges(trades);
            planChanges(pages, changes);
            NekoTradeConfig.planOrderWatermarks(watermarks, changes);
            changes.putAll(metadata);
            files.commit(changes);
        } catch (java.io.IOException failure) {
            throw new IllegalStateException("Page/trade configuration could not be committed", failure);
        }
    }

    /** Preserve unknown schema fields on both the envelope and retained pages when editing known fields. */
    private static String pageFileJson(Path path, NekoPageData data) throws java.io.IOException {
        JsonObject result = Files.exists(path)
            ? new JsonParser().parse(new String(Files.readAllBytes(path), StandardCharsets.UTF_8))
                .getAsJsonObject()
            : new JsonObject();
        java.util.Map<Integer, JsonObject> previous = new java.util.LinkedHashMap<>();
        if (result.has("pages") && result.get("pages")
            .isJsonArray()) {
            for (JsonElement raw : result.getAsJsonArray("pages")) {
                if (raw.isJsonObject() && raw.getAsJsonObject()
                    .has("id")) {
                    JsonObject page = raw.getAsJsonObject();
                    previous.put(
                        page.get("id")
                            .getAsInt(),
                        page);
                }
            }
        }
        JsonArray pages = new JsonArray();
        for (NekoPageEntry page : data.getPages()) {
            JsonObject merged = previous.get(page.getId());
            if (merged == null) merged = new JsonObject();
            for (java.util.Map.Entry<String, JsonElement> field : GSON.toJsonTree(page)
                .getAsJsonObject()
                .entrySet()) merged.add(field.getKey(), field.getValue());
            pages.add(merged);
        }
        result.add("pages", pages);
        int version = result.has("version") ? result.get("version")
            .getAsInt() : 1;
        result.addProperty("version", Math.max(version, data.getVersion()));
        return GSON.toJson(result);
    }

    private static NekoPageData withPresetPages(NekoPageData data) throws java.io.IOException {
        Path path = TradeConfigPaths.resolve(TradePresetLayout.PAGES_PATH);
        if (!Files.exists(path)) {
            data.getPages()
                .addAll(com.miaokatze.gtit.trade.preset.DefaultTradeSources.pages());
            return data;
        }
        String json = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
        NekoTradeJson.validateDepth(json);
        NekoPageData managed = GSON.fromJson(json, NekoPageData.class);
        if (managed == null || managed.getPages() == null) throw new java.io.IOException("默认贸易组页签文件损坏");
        for (NekoPageEntry page : managed.getPages()) {
            if (page == null || !TradePresetLayout.isManagedPage(page.getId())) {
                throw new java.io.IOException("默认贸易组页签 ID 越界");
            }
            data.getPages()
                .add(page);
        }
        data.getPages()
            .addAll(com.miaokatze.gtit.trade.preset.DefaultTradeSources.pages());
        return data;
    }

    private static NekoPageData withPresetPagesSafely(NekoPageData data) {
        try {
            return withPresetPages(data);
        } catch (java.io.IOException | RuntimeException failure) {
            LOG.error("默认贸易组页签加载失败；保留原文件", failure);
            return data;
        }
    }

    /**
     * 将标签页数据序列化为 JSON 字符串（v1.7.0 目标 5：配置同步包载荷用）
     *
     * @param data 标签页数据
     * @return JSON 字符串；data 为 null 时返回空对象串
     */
    public static synchronized String toJson(NekoPageData data) {
        return GSON.toJson(data == null ? new NekoPageData() : data);
    }

    /**
     * 从 JSON 字符串反序列化标签页数据（v1.7.0 目标 5：客户端接收同步包后解析用）
     *
     * @param json JSON 字符串
     * @return 标签页数据；解析失败或为空时回退默认数据（不写盘）
     */
    public static synchronized NekoPageData fromJson(String json) {
        if (json != null && !json.isEmpty()) {
            try {
                NekoPageData data = GSON.fromJson(json, NekoPageData.class);
                if (data != null && data.getPages() != null
                    && !data.getPages()
                        .isEmpty()) {
                    return data;
                }
            } catch (Exception e) {
                LOG.error("反序列化同步标签页配置失败，回退默认数据", e);
            }
        }
        return getDefaultPages();
    }

    /**
     * 生成默认标签页数据
     */
    public static NekoPageData getDefaultPages() {
        return new NekoPageData();
    }

    private static Path getConfigPath() {
        return TradeConfigPaths.resolve(CONFIG_PATH);
    }
}

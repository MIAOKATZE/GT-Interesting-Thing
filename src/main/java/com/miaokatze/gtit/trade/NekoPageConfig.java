package com.miaokatze.gtit.trade;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.miaokatze.gtit.main.GTInterestingThing;

/**
 * 猫猫售货机标签页配置管理（v1.7.7 G4 存储结构重构）
 * <p>
 * 新路径：{@code config/gtit/trade/pages.json}；旧路径 {@code config/gtit/nekovm_pages.json}
 * 存在时自动迁移，旧文件重命名为 {@code .bak} 保留。
 */
public class NekoPageConfig {

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
                Path legacy = Paths.get(LEGACY_CONFIG_PATH);
                if (Files.exists(legacy)) {
                    load(); // load 内部完成迁移
                } else {
                    Files.createDirectories(path.getParent());
                    save(getDefaultPages());
                    GTInterestingThing.LOG.info("猫猫售货机标签页配置已生成默认文件: {}", path);
                }
            }
        } catch (Exception e) {
            GTInterestingThing.LOG.error("猫猫售货机标签页配置初始化失败", e);
        }
    }

    /**
     * 从文件加载标签页数据，文件不存在时尝试迁移旧文件，均失败返回默认数据
     */
    public static synchronized NekoPageData load() {
        Path path = getConfigPath();
        if (!Files.exists(path)) {
            Path legacy = Paths.get(LEGACY_CONFIG_PATH);
            if (Files.exists(legacy)) {
                try {
                    String json = new String(Files.readAllBytes(legacy), StandardCharsets.UTF_8);
                    NekoPageData data = GSON.fromJson(json, NekoPageData.class);
                    if (data != null && data.getPages() != null
                        && !data.getPages()
                            .isEmpty()) {
                        save(data);
                        Path backup = legacy.resolveSibling(
                            legacy.getFileName()
                                .toString() + ".bak");
                        Files.move(legacy, backup, StandardCopyOption.REPLACE_EXISTING);
                        GTInterestingThing.LOG.info("猫猫售货机标签页配置已从旧路径迁移: {} -> {}", legacy, path);
                        GTInterestingThing.LOG.info("旧标签页配置文件已重命名保留: {}", backup);
                        return data;
                    }
                } catch (Exception e) {
                    GTInterestingThing.LOG.error("猫猫售货机标签页配置迁移失败，回退默认数据", e);
                }
            }
            GTInterestingThing.LOG.info("猫猫售货机标签页配置文件不存在，返回默认数据");
            return getDefaultPages();
        }
        try {
            String json = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
            NekoPageData data = GSON.fromJson(json, NekoPageData.class);
            if (data == null || data.getPages() == null
                || data.getPages()
                    .isEmpty()) {
                GTInterestingThing.LOG.warn("猫猫售货机标签页配置文件为空，返回默认数据");
                return getDefaultPages();
            }
            return data;
        } catch (Exception e) {
            GTInterestingThing.LOG.error("猫猫售货机标签页配置加载失败，返回默认数据", e);
            return getDefaultPages();
        }
    }

    /**
     * 保存标签页数据到新路径
     */
    public static synchronized void save(NekoPageData data) {
        try {
            Path path = getConfigPath();
            Files.createDirectories(path.getParent());
            String json = GSON.toJson(data);
            Files.write(path, json.getBytes(StandardCharsets.UTF_8));
            GTInterestingThing.LOG.info("猫猫售货机标签页配置已保存");
        } catch (Exception e) {
            GTInterestingThing.LOG.error("猫猫售货机标签页配置保存失败", e);
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
                GTInterestingThing.LOG.error("反序列化同步标签页配置失败，回退默认数据", e);
            }
        }
        return getDefaultPages();
    }

    /**
     * 生成默认标签页数据
     */
    public static NekoPageData getDefaultPages() {
        NekoPageData data = new NekoPageData();
        List<NekoPageEntry> pages = new ArrayList<>();

        // 标签页1：猫猫币
        NekoPageEntry page1 = new NekoPageEntry(1, "猫猫币", "gtit:neko_coin", 0, true);
        pages.add(page1);

        // 标签页2：闪烁猫猫币
        NekoPageEntry page2 = new NekoPageEntry(2, "闪烁猫猫币", "gtit:shimmering_neko_coin", 0, true);
        pages.add(page2);

        // 标签页3：GTIT
        NekoPageEntry page3 = new NekoPageEntry(3, "GTIT", "gtit:ring_distant_grasp", 0, true);
        pages.add(page3);

        // 标签页4：周期领取
        NekoPageEntry page4 = new NekoPageEntry(4, "周期领取", "minecraft:clock", 0, false);
        pages.add(page4);

        // 标签页5：基础
        NekoPageEntry page5 = new NekoPageEntry(5, "基础", "minecraft:bed", 0, false);
        pages.add(page5);

        data.setPages(pages);
        return data;
    }

    private static Path getConfigPath() {
        return Paths.get(CONFIG_PATH);
    }
}

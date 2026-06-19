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
 * 猫猫售货机标签页配置管理
 * 负责读写 config/gtit/nekovm_pages.json
 */
public class NekoPageConfig {

    private static final String CONFIG_SUB_PATH = "config/gtit/nekovm_pages.json";
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
     * 初始化配置，如果配置文件不存在则生成默认配置
     */
    public static synchronized void init() {
        try {
            Path path = getConfigPath();
            if (!Files.exists(path)) {
                Files.createDirectories(path.getParent());
                save(getDefaultPages());
                GTInterestingThing.LOG.info("猫猫售货机标签页配置已生成默认文件: {}", path);
            }
        } catch (Exception e) {
            GTInterestingThing.LOG.error("猫猫售货机标签页配置初始化失败", e);
        }
    }

    /**
     * 从文件加载标签页数据，文件不存在时返回默认数据
     */
    public static synchronized NekoPageData load() {
        try {
            Path path = getConfigPath();
            if (!Files.exists(path)) {
                GTInterestingThing.LOG.info("猫猫售货机标签页配置文件不存在，返回默认数据");
                return getDefaultPages();
            }
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
     * 保存标签页数据到文件
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

        // 标签页3：其他
        NekoPageEntry page3 = new NekoPageEntry(3, "其他", "minecraft:written_book", 0, true);
        pages.add(page3);

        data.setPages(pages);
        return data;
    }

    private static Path getConfigPath() {
        return Paths.get(CONFIG_SUB_PATH);
    }
}

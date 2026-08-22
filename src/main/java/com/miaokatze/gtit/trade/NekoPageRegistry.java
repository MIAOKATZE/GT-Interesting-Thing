package com.miaokatze.gtit.trade;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.item.ItemStack;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.cubefury.vendingmachine.trade.TradeCategory;
import com.miaokatze.gtit.currency.NekoCurrencyRegistrar;
import com.miaokatze.gtit.trade.v2.NekoTradeRegistryV2;

/**
 * 标签页注册表
 * <p>
 * 运行时管理猫猫售货机的标签页。
 * 标签页数据从 NekoPageConfig 加载，支持动态增删。
 * <p>
 * 默认标签页（ID 1-3）不可删除。
 */
public class NekoPageRegistry {

    /** 统一 logger（O2-B02 去中心化：与主类同用 "gtit" logger 名，日志过滤口径不变） */
    private static final Logger LOG = LogManager.getLogger("gtit");

    /** 标签页ID → 标签页条目，保持插入顺序 */
    private static final Map<Integer, NekoPageEntry> PAGES = new LinkedHashMap<>();

    /** TradeCategory 枚举池，用于 GUI 标签页映射 */
    private static final TradeCategory[] CATEGORY_POOL = { TradeCategory.MISC, TradeCategory.MAGIC,
        TradeCategory.COMPONENTS, TradeCategory.RAW, TradeCategory.FARMING, TradeCategory.CHEMISTRY, TradeCategory.BEES,
        TradeCategory.UNKNOWN, TradeCategory.FAVOURITES };

    /**
     * 初始化标签页注册表
     * 在 CommonProxy.serverStarted() 中调用
     */
    public static void initialize() {
        LOG.info("开始注册猫猫售货机标签页...");
        NekoPageConfig.init();
        loadPages();
        LOG.info("猫猫售货机标签页注册完成，共 {} 个标签页", PAGES.size());
    }

    /**
     * 从配置加载标签页
     */
    public static void loadPages() {
        PAGES.clear();
        NekoPageConfig.NekoPageData data = NekoPageConfig.load();
        for (NekoPageEntry entry : data.getPages()) {
            PAGES.put(entry.getId(), entry);
        }
    }

    /**
     * 保存当前标签页到配置
     */
    public static void savePages() {
        NekoPageConfig.NekoPageData data = new NekoPageConfig.NekoPageData();
        data.setPages(new ArrayList<>(PAGES.values()));
        NekoPageConfig.save(data);
    }

    /**
     * 添加或覆盖标签页
     *
     * @param id        标签页ID
     * @param name      标签页名称
     * @param iconStack 图标ItemStack
     * @return 操作结果消息
     */
    public static String addPage(int id, String name, ItemStack iconStack) {
        if (id < 1) {
            return "标签页ID必须为正整数";
        }

        boolean isOverwrite = PAGES.containsKey(id);
        NekoPageEntry entry = new NekoPageEntry();
        entry.setId(id);
        entry.setName(name);
        entry.setDefault(id <= 3); // ID 1-3 为默认标签页
        entry.setIconFromItemStack(iconStack);

        PAGES.put(id, entry);
        savePages();

        if (isOverwrite) {
            return "已覆盖标签页 #" + id + " (" + name + ")";
        } else {
            return "已添加标签页 #" + id + " (" + name + ")";
        }
    }

    /**
     * 删除标签页
     *
     * @param id 标签页ID
     * @return 操作结果消息
     */
    public static String deletePage(int id) {
        if (id < 1) {
            return "标签页ID必须为正整数";
        }

        NekoPageEntry existing = PAGES.get(id);
        if (existing == null) {
            return "标签页 #" + id + " 不存在";
        }

        if (existing.isDefault()) {
            return "默认标签页（ID 1-3）不可删除";
        }

        PAGES.remove(id);
        savePages();

        // 将该标签页的交易移动到标签页3（其他）
        NekoTradeConfig.NekoTradeData tradeData = NekoTradeConfig.load();
        for (NekoTradeEntry trade : tradeData.getTrades()) {
            if (trade.getTabId() == id) {
                trade.setTabId(3);
            }
        }
        NekoTradeConfig.save(tradeData);
        // V1 的 NekoTradeRegistry.reload() 已移除，改为调用 V2 的 reload
        // V2 从 NekoTradeConfig 加载配置并注册到 NekoTradeDatabase（V2 独立数据库）
        NekoTradeRegistryV2.reload();

        return "已删除标签页 #" + id + " (" + existing.getName() + ")，其交易已移至\"其他\"标签页";
    }

    /**
     * 获取标签页名称
     */
    public static String getPageName(int id) {
        NekoPageEntry entry = PAGES.get(id);
        return entry != null ? entry.getName() : "未知";
    }

    /**
     * 获取标签页图标ItemStack
     */
    public static ItemStack getPageIcon(int id) {
        NekoPageEntry entry = PAGES.get(id);
        if (entry == null) return null;

        // 默认标签页1和2优先使用猫猫币物品作为图标
        if (id == 1) {
            ItemStack nekoStack = NekoCurrencyRegistrar.getItemStack("neko", 1);
            if (nekoStack != null) return nekoStack;
        }
        if (id == 2) {
            ItemStack shimmeringStack = NekoCurrencyRegistrar.getItemStack("shimmeringNeko", 1);
            if (shimmeringStack != null) return shimmeringStack;
        }

        return entry.toIconItemStack();
    }

    /**
     * 获取标签页对应的 TradeCategory
     * <p>
     * 使用 CATEGORY_POOL 循环分配，确保每个标签页有不同的枚举值
     * （VM框架通过 TradeCategory 区分标签页）
     */
    public static TradeCategory getTradeCategory(int pageIndex) {
        if (pageIndex >= 0 && pageIndex < CATEGORY_POOL.length) {
            return CATEGORY_POOL[pageIndex];
        }
        return TradeCategory.MISC;
    }

    /**
     * 获取所有标签页条目（按ID排序）
     */
    public static List<NekoPageEntry> getAllPages() {
        return new ArrayList<>(PAGES.values());
    }

    /**
     * 获取标签页数量
     */
    public static int getPageCount() {
        return PAGES.size();
    }

    /**
     * 判断标签页是否存在
     */
    public static boolean hasPage(int id) {
        return PAGES.containsKey(id);
    }

    /**
     * 获取所有标签页ID
     */
    public static List<Integer> getPageIds() {
        return new ArrayList<>(PAGES.keySet());
    }

    /**
     * 获取标签页条目
     */
    public static NekoPageEntry getPage(int id) {
        return PAGES.get(id);
    }

    /**
     * 应用服务端同步的标签页配置（v1.7.0 目标 5，客户端专用）
     * <p>
     * 用同步包携带的标签页数据整体替换内存注册表：<b>只改内存，不写盘</b>
     * （配置修改默认只在服务端进行，客户端永不在同步路径写配置文件）。
     * 单人存档下不会被调用（同步包处理器已跳过）——集成服务端与客户端共享
     * 本静态注册表，服务端侧的重载已直接刷新同一份数据。
     *
     * @param data 服务端同步的标签页配置（{@link NekoPageConfig#fromJson} 产物）
     */
    public static void applySyncedPages(NekoPageConfig.NekoPageData data) {
        if (data == null || data.getPages() == null) return;
        PAGES.clear();
        for (NekoPageEntry entry : data.getPages()) {
            if (entry != null) {
                PAGES.put(entry.getId(), entry);
            }
        }
        LOG.info("[NekoSync] 客户端已应用同步标签页配置，共 {} 个标签页", PAGES.size());
    }

    /**
     * 热重载标签页配置
     */
    public static boolean reload() {
        try {
            loadPages();
            LOG.info("猫猫售货机标签页热重载完成，共 {} 个标签页", PAGES.size());
            return true;
        } catch (Exception e) {
            LOG.error("猫猫售货机标签页热重载失败!", e);
            return false;
        }
    }
}

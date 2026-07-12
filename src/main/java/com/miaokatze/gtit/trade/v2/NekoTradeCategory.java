package com.miaokatze.gtit.trade.v2;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 交易分类，替代 VM 的 TradeCategory。
 * <p>
 * V2 改为动态分类模型：每个标签页（tabId）对应一个分类实例，
 * 与 {@link com.miaokatze.gtit.trade.NekoPageRegistry} 中的标签页配置保持一致。
 * <p>
 * 保留两个特殊分类：
 * <ul>
 * <li>{@link #UNKNOWN}：tabId=0，用于无法识别/未配置的交易</li>
 * <li>{@link #FAVOURITES}：tabId=-1，虚拟收藏分类</li>
 * </ul>
 * <p>
 * 其他分类通过 {@link #ofTabId(int)} 按 tabId 动态创建或复用，
 * 因此配置中的自定义标签页会自动在 GUI 中生成对应标签页。
 */
public final class NekoTradeCategory implements Comparable<NekoTradeCategory> {

    /** 未知分类（tabId=0） */
    public static final NekoTradeCategory UNKNOWN = new NekoTradeCategory(0, "unknown", "gtit.category.unknown");

    /** 收藏分类（tabId=-1，虚拟分类，包含所有被收藏的交易） */
    public static final NekoTradeCategory FAVOURITES = new NekoTradeCategory(
        -1,
        "favourites",
        "gtit.category.favourites");

    /** key -> 分类实例 的快速查找映射 */
    private static final Map<String, NekoTradeCategory> KEY_MAP = new HashMap<>();

    /** tabId -> 分类实例 的快速查找映射 */
    private static final Map<Integer, NekoTradeCategory> TAB_ID_MAP = new HashMap<>();

    /** 所有已注册分类的有序列表（包含特殊分类） */
    private static final List<NekoTradeCategory> VALUES = new ArrayList<>();

    static {
        register(UNKNOWN);
        register(FAVOURITES);
    }

    /** 标签页 ID，作为分类的唯一标识 */
    private final int tabId;

    /** 分类键名，用于序列化和字符串匹配 */
    private final String key;

    /** 本地化名称键，用于国际化显示 */
    private final String unlocalizedName;

    /**
     * 私有构造器，禁止外部直接创建。
     * 普通分类请使用 {@link #ofTabId(int)}，以保证全局唯一实例。
     *
     * @param tabId           标签页 ID
     * @param key             分类键名
     * @param unlocalizedName 本地化名称键
     */
    private NekoTradeCategory(int tabId, String key, String unlocalizedName) {
        this.tabId = tabId;
        this.key = key;
        this.unlocalizedName = unlocalizedName;
    }

    /**
     * 注册分类到全局查找表。
     *
     * @param category 要注册的分类
     */
    private static synchronized void register(NekoTradeCategory category) {
        KEY_MAP.put(category.key, category);
        TAB_ID_MAP.put(category.tabId, category);
        VALUES.add(category);
    }

    /**
     * 根据标签页 ID 获取分类。
     * <p>
     * 已存在的分类直接复用；不存在时动态创建并注册。
     * tabId<=0 时返回 {@link #UNKNOWN} 或 {@link #FAVOURITES}。
     *
     * @param tabId 标签页 ID
     * @return 对应的分类实例
     */
    public static synchronized NekoTradeCategory ofTabId(int tabId) {
        if (tabId <= 0) {
            return tabId == -1 ? FAVOURITES : UNKNOWN;
        }
        NekoTradeCategory existing = TAB_ID_MAP.get(tabId);
        if (existing != null) {
            return existing;
        }
        NekoTradeCategory category = new NekoTradeCategory(tabId, "tab_" + tabId, "gtit.category.tab_" + tabId);
        register(category);
        return category;
    }

    /**
     * 从字符串解析分类。
     * <p>
     * 支持新版动态分类键（"tab_1"、"tab_2" ...）和旧版枚举键的向后兼容映射：
     * <ul>
     * <li>"neko" -> tabId 1</li>
     * <li>"shimmeringNeko" / "shimmering_neko" -> tabId 2</li>
     * <li>"magic" / "misc" / "all" -> tabId 3（归入默认"其他"标签页）</li>
     * <li>"unknown" -> {@link #UNKNOWN}</li>
     * <li>"favourites" -> {@link #FAVOURITES}</li>
     * </ul>
     *
     * @param key 分类键名
     * @return 对应的分类实例，无法匹配时返回 {@link #UNKNOWN}
     */
    public static NekoTradeCategory ofString(String key) {
        if (key == null || key.isEmpty()) {
            return UNKNOWN;
        }
        NekoTradeCategory category = KEY_MAP.get(key);
        if (category != null) {
            return category;
        }

        // 向后兼容：旧版枚举键映射到 tabId
        Integer legacyTabId = mapLegacyKey(key);
        if (legacyTabId != null) {
            return ofTabId(legacyTabId);
        }

        // 尝试解析 "tab_<id>" 格式
        if (key.startsWith("tab_")) {
            try {
                return ofTabId(Integer.parseInt(key.substring(4)));
            } catch (NumberFormatException ignored) {}
        }

        return UNKNOWN;
    }

    /**
     * 旧版分类键到 tabId 的映射。
     *
     * @param key 旧版分类键
     * @return 对应的 tabId，无法映射时返回 null
     */
    private static Integer mapLegacyKey(String key) {
        switch (key) {
            case "neko":
                return 1;
            case "shimmeringNeko":
            case "shimmering_neko":
                return 2;
            case "magic":
            case "misc":
            case "all":
                return 3;
            default:
                return null;
        }
    }

    /**
     * 获取所有已注册分类的数组副本。
     * <p>
     * 保留与旧版枚举 {@code values()} 相似的访问方式，
     * 返回数组的顺序按 tabId 升序排列（FAVOURITES=-1 在最前，UNKNOWN=0 其次）。
     *
     * @return 所有分类的数组
     */
    public static NekoTradeCategory[] values() {
        synchronized (VALUES) {
            return VALUES.toArray(new NekoTradeCategory[0]);
        }
    }

    /**
     * 获取所有已注册分类的列表副本。
     *
     * @return 所有分类的列表
     */
    public static List<NekoTradeCategory> getAllCategories() {
        synchronized (VALUES) {
            return new ArrayList<>(VALUES);
        }
    }

    /**
     * 获取标签页 ID。
     *
     * @return 标签页 ID
     */
    public int getTabId() {
        return tabId;
    }

    /**
     * 获取分类键名。
     *
     * @return 键名字符串
     */
    public String getKey() {
        return key;
    }

    /**
     * 获取本地化名称键。
     *
     * @return 本地化键名
     */
    public String getUnlocalizedName() {
        return unlocalizedName;
    }

    /**
     * 判断是否为收藏分类。
     *
     * @return true 表示收藏分类
     */
    public boolean isFavourites() {
        return this == FAVOURITES;
    }

    /**
     * 判断是否为未知分类。
     *
     * @return true 表示未知分类
     */
    public boolean isUnknown() {
        return this == UNKNOWN;
    }

    @Override
    public int compareTo(NekoTradeCategory other) {
        return Integer.compare(this.tabId, other.tabId);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof NekoTradeCategory)) return false;
        NekoTradeCategory other = (NekoTradeCategory) obj;
        return this.tabId == other.tabId;
    }

    @Override
    public int hashCode() {
        return Objects.hash(tabId);
    }

    @Override
    public String toString() {
        return key;
    }
}

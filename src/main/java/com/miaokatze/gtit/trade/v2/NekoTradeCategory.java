package com.miaokatze.gtit.trade.v2;

import java.util.HashMap;
import java.util.Map;

/**
 * 交易分类枚举，替代 VM 的 TradeCategory（简化版，无 UITexture）
 * <p>
 * 用于对交易进行分类管理，支持按类别过滤和标签页划分。
 */
public enum NekoTradeCategory {

    /** 未知分类 */
    UNKNOWN("unknown", "gtit.category.unknown"),
    /** 全部分类 */
    ALL("all", "gtit.category.all"),
    /** 收藏分类（虚拟分类，包含所有被收藏的交易） */
    FAVOURITES("favourites", "gtit.category.favourites"),
    /** 猫猫币交易 */
    NEKO("neko", "gtit.category.neko"),
    /** 闪烁猫猫币交易 */
    SHIMMERING_NEKO("shimmeringNeko", "gtit.category.shimmeringNeko"),
    /** 魔法相关交易 */
    MAGIC("magic", "gtit.category.magic"),
    /** 杂项交易 */
    MISC("misc", "gtit.category.misc");

    /** 分类键名，用于序列化和字符串匹配 */
    private final String key;

    /** 本地化名称键，用于国际化显示 */
    private final String unlocalizedName;

    /** key -> 枚举值 的快速查找映射 */
    private static final Map<String, NekoTradeCategory> ENUM_MAP;

    static {
        ENUM_MAP = new HashMap<>();
        for (NekoTradeCategory category : values()) {
            ENUM_MAP.put(category.key, category);
        }
    }

    NekoTradeCategory(String key, String unlocalizedName) {
        this.key = key;
        this.unlocalizedName = unlocalizedName;
    }

    /**
     * 获取分类键名
     *
     * @return 键名字符串
     */
    public String getKey() {
        return key;
    }

    /**
     * 获取本地化名称键
     *
     * @return 本地化键名
     */
    public String getUnlocalizedName() {
        return unlocalizedName;
    }

    /**
     * 从字符串解析分类
     *
     * @param key 分类键名
     * @return 对应的枚举值，无法匹配时返回 UNKNOWN
     */
    public static NekoTradeCategory ofString(String key) {
        if (key == null || key.isEmpty()) {
            return UNKNOWN;
        }
        NekoTradeCategory category = ENUM_MAP.get(key);
        return category != null ? category : UNKNOWN;
    }
}

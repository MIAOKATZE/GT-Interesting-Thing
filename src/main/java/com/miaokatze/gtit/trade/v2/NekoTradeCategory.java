package com.miaokatze.gtit.trade.v2;

/**
 * 交易分类枚举，替代 VM 的 TradeCategory（简化版，无 UITexture）
 * <p>
 * 用于对交易进行分类管理，支持按类别过滤和标签页划分。
 */
public enum NekoTradeCategory {

    /** 未知分类 */
    UNKNOWN,
    /** 全部分类 */
    ALL,
    /** 猫猫币交易 */
    NEKO,
    /** 闪烁猫猫币交易 */
    SHIMMERING_NEKO,
    /** 魔法相关交易 */
    MAGIC,
    /** 杂项交易 */
    MISC;

    /**
     * 从字符串解析分类
     *
     * @param name 分类名称
     * @return 对应的枚举值，无法匹配时返回 UNKNOWN
     */
    public static NekoTradeCategory ofString(String name) {
        // TODO: v1.6.1 实现
        return UNKNOWN;
    }
}

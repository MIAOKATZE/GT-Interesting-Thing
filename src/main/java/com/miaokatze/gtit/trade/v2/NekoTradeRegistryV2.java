package com.miaokatze.gtit.trade.v2;

/**
 * 新版注册表，管理 NekoTradeDatabase
 * <p>
 * 从 NekoTradeConfig（v1 旧版配置）加载交易数据，
 * 转换为 v2 数据结构后注册到 {@link NekoTradeDatabase}。
 * 提供初始化和热重载能力。
 */
public class NekoTradeRegistryV2 {

    /**
     * 初始化注册表
     * <p>
     * 在模组加载阶段调用，加载交易配置并注册到数据库。
     */
    public static void initialize() {
        // TODO: v1.6.1 实现
    }

    /**
     * 热重载交易配置
     * <p>
     * 清空数据库后重新加载，支持运行时更新交易配置。
     */
    public static void reload() {
        // TODO: v1.6.1 实现
    }

    /**
     * 加载交易配置并注册到数据库
     * <p>
     * 读取配置文件，将每条交易配置转换为 NekoTradeGroup，
     * 注册到 {@link NekoTradeDatabase#INSTANCE}。
     */
    public static void loadAndRegisterTrades() {
        // TODO: v1.6.1 实现
    }
}

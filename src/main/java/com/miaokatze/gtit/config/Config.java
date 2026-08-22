package com.miaokatze.gtit.config;

import java.io.File;

import net.minecraftforge.common.config.Configuration;

/**
 * 模组配置管理类
 * 负责读取和保存模组的配置文件 (config/gtit.cfg)
 */
public class Config {

    // GregTech 元机器实体 (MTE) ID 分配的偏移量。
    // 注意：基准值 (BASE) 已在 MetaTileEntityID.java 中硬编码为 14600，以便按类型分段管理 ID。
    // 此配置仅用于在基准值基础上进行微调。
    public static int metaIdOffset = 0;

    // O2-01: 成就系统骨架总开关（v1.6.5 未完成——网络消息未注册、Trigger 钩子零调用）。
    // 默认 false：关闭成就网络包/BQ 桥接/成就管理器三处初始化，消除启动误导日志与误接线面；
    // 为 LongPlan M4 决策（补全或移除成就系统）留护栏。
    public static boolean enableAchievements = false;

    /**
     * 同步配置文件
     * 从磁盘读取配置并更新静态变量，如果配置有变动则自动保存
     * 
     * @param configFile 配置文件对象
     */
    public static void synchronizeConfiguration(File configFile) {
        Configuration configuration = new Configuration(configFile);

        metaIdOffset = configuration.getInt(
            "metaIdOffset",
            Configuration.CATEGORY_GENERAL,
            metaIdOffset,
            -5000,
            5000,
            "应用于 MTE ID 基准值的偏移量 (用于预留 ID 区间)");

        enableAchievements = configuration.getBoolean(
            "enableAchievements",
            Configuration.CATEGORY_GENERAL,
            false,
            "是否启用成就系统骨架 (v1.6.5 未完成, 默认关闭; 关闭时跳过成就网络包/BQ 桥接/成就管理器初始化)");

        if (configuration.hasChanged()) {
            configuration.save();
        }
    }
}

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

    // E4a: MIAO-GTNH 综合任务包总开关。
    // 控制：assets/gtit/bqquests/miao/ 任务包注入、登录双语提示（音效+聊天）、
    // MIAO 综合贸易组（miao_trades/pages）注册。需 betterquesting+gtsr+gtswn 齐备才实际生效。
    public static boolean miaogtnhQuestsEnabled = true;

    // E4a: 内置基础贸易组开关。
    // true 且 jar 内 assets/gtit/bqtrades/ 基础组资产可用时，注册内置基础贸易组
    // 并抑制旧 42 条默认交易的注入（getDefaultTrades 保留为资产缺失时的兜底）；
    // false 或资产缺失时沿用旧默认交易。
    public static boolean enhancedDefaultTrades = true;

    // 钓鱼附赠：玩家每次成功钓到鱼后，服务端掷概率额外发放猫猫币（见 MixinEntityFishHook）。
    // 量纲：百分比 0-100，0=禁用；两项独立配置。
    // 语义：每次成功钓鱼先掷闪烁猫猫币概率，未中再掷普通猫猫币概率。
    public static double fishingNekoCoinChance = 10.0;
    public static double fishingShimmeringNekoCoinChance = 2.0;

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

        miaogtnhQuestsEnabled = configuration.getBoolean(
            "miaogtnhQuestsEnabled",
            Configuration.CATEGORY_GENERAL,
            true,
            "是否启用 MIAO-GTNH 综合任务包 (需 betterquesting+gtsr+gtswn 齐备; 控制综合任务包注入/登录提示/MIAO 贸易组)");

        enhancedDefaultTrades = configuration.getBoolean(
            "enhancedDefaultTrades",
            Configuration.CATEGORY_GENERAL,
            true,
            "是否用内置基础贸易组替换旧 42 条默认交易 (资产缺失时自动回落旧默认)");

        fishingNekoCoinChance = configuration
            .get(
                Configuration.CATEGORY_GENERAL,
                "fishingNekoCoinChance",
                fishingNekoCoinChance,
                "每次成功钓到鱼后服务端额外附赠普通猫猫币的概率 (百分比 0-100, 0=禁用; 每次成功钓鱼先掷闪烁猫猫币, 未中再掷普通猫猫币)",
                0.0D,
                100.0D)
            .getDouble(fishingNekoCoinChance);

        fishingShimmeringNekoCoinChance = configuration
            .get(
                Configuration.CATEGORY_GENERAL,
                "fishingShimmeringNekoCoinChance",
                fishingShimmeringNekoCoinChance,
                "每次成功钓到鱼后服务端额外附赠闪烁猫猫币的概率 (百分比 0-100, 0=禁用; 每次成功钓鱼先掷闪烁猫猫币, 未中再掷普通猫猫币)",
                0.0D,
                100.0D)
            .getDouble(fishingShimmeringNekoCoinChance);

        if (configuration.hasChanged()) {
            configuration.save();
        }
    }
}

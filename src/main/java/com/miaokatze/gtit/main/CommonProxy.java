package com.miaokatze.gtit.main;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.World;

import com.miaokatze.gtit.Tags;
import com.miaokatze.gtit.client.gui.NekoWidgetThemes;
import com.miaokatze.gtit.command.GTITGiftCommand;
import com.miaokatze.gtit.common.items.infinitycell.InfinityCellHandler;
import com.miaokatze.gtit.common.items.infinitycell.StorageManager;
import com.miaokatze.gtit.common.loot.LootRegistrar;
import com.miaokatze.gtit.config.Config;
import com.miaokatze.gtit.config.GiftConfig;
import com.miaokatze.gtit.config.MuteConfig;
import com.miaokatze.gtit.crossmod.bq.BqCompat;
import com.miaokatze.gtit.crossmod.miaogtnh.MiaoGtnhHost;
import com.miaokatze.gtit.currency.NekoCurrencyRegistrar;
import com.miaokatze.gtit.event.PlayerLoginHandler;
import com.miaokatze.gtit.loader.ItemLoader;
import com.miaokatze.gtit.loader.MachineLoader;
import com.miaokatze.gtit.recipe.GTITRecipes;
import com.miaokatze.gtit.register.CreativeTabManager;
import com.miaokatze.gtit.register.TextureManager;
import com.miaokatze.gtit.trade.NekoPageRegistry;
import com.miaokatze.gtit.trade.NekoWalletManager;
import com.miaokatze.gtit.trade.TeamDataProvider;
import com.miaokatze.gtit.trade.api.BundledTradeGroups;
import com.miaokatze.gtit.trade.api.NekoTradeIntegrationAPI;
import com.miaokatze.gtit.trade.v2.NekoTradeRegistryV2;

import appeng.api.AEApi;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLInterModComms;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartedEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import cpw.mods.fml.common.event.FMLServerStoppedEvent;
import cpw.mods.fml.common.event.FMLServerStoppingEvent;
import gregtech.api.GregTechAPI;

/**
 * 通用代理类
 * 处理服务端和客户端共有的逻辑，如配置加载、机器注册、创造模式物品栏初始化等。
 */
public class CommonProxy {

    /**
     * 预初始化阶段 (PreInit)
     * 在此阶段读取配置文件，扩展Baubles戒指栏，并将机器注册任务添加到 GregTech 的处理队列中。
     */
    public void preInit(FMLPreInitializationEvent event) {
        Config.synchronizeConfiguration(event.getSuggestedConfigurationFile());

        // E4a: BQ 存在性反射探测（BqCompat 无任何 BQ 类型引用，缺席时静默降级）
        BqCompat.detect();

        GTInterestingThing.LOG.info("GTInterestingThing 开始初始化 (版本: " + Tags.VERSION + ")");

        // 强制加载 TextureManager，确保自定义材质在纹理缝合前注册到 GregTechAPI.sGTBlockIconload
        try {
            Class.forName(TextureManager.class.getName());
            GTInterestingThing.LOG.info("[0/3] TextureManager 已加载，自定义材质图标已注册");
        } catch (ClassNotFoundException e) {
            GTInterestingThing.LOG.error("[0/3] TextureManager 加载失败", e);
        }

        // 强制加载 NekoWidgetThemes，确保 WidgetThemeKey 在 DefaultTheme.initialize() 懒加载前注册
        // 否则 V2 GUI 首次打开时才触发 NekoWidgetThemes.<clinit>，此时 DefaultTheme 已初始化完成，
        // 新注册的 key 不会被纳入已存在的主题映射，导致 getWidgetTheme(key) 返回 null，widgetTheme 为 null，
        // Widget.getActiveWidgetTheme() 抛 NPE 崩溃。
        try {
            Class.forName(NekoWidgetThemes.class.getName());
            GTInterestingThing.LOG.info("[0/3] NekoWidgetThemes 已加载，Widget 主题已注册");
        } catch (ClassNotFoundException e) {
            GTInterestingThing.LOG.error("[0/3] NekoWidgetThemes 加载失败", e);
        }

        // 扩展 Baubles 戒指栏到 10 个
        expandBaublesRingSlots();

        // 注册物品
        GTInterestingThing.LOG.info("[0/3] 开始注册物品...");
        try {
            ItemLoader.initItems();
            GTInterestingThing.LOG.info("[0/3] 物品注册完成。");
        } catch (Throwable t) {
            GTInterestingThing.LOG.error("[0/3] 物品注册过程中发生严重错误，请检查日志", t);
        }

        // 初始化新手宝箱配置
        // B2-13：对齐邻侧 DailySignInConfig 的防御写法——吞异常后 GiftConfig 静态集合
        // 走声明期默认值，不应让配置解析失败中断整个 preInit
        try {
            GiftConfig.init();
        } catch (Throwable t) {
            GTInterestingThing.LOG.error("[0/3] 新手宝箱配置初始化失败", t);
        }

        // v1.7.0：初始化签到奖励配置（DailySignInConfig.CONFIG_PATH = config/gtit/signin/daily_signin.json，缺失时生成默认）
        try {
            com.miaokatze.gtit.signin.DailySignInConfig.init();
            GTInterestingThing.LOG.info("[0/3] 签到奖励配置已初始化");
        } catch (Throwable t) {
            GTInterestingThing.LOG.error("[0/3] 签到奖励配置初始化失败", t);
        }

        // v1.7.6 G2③：初始化在线时长奖励配置
        // （OnlineTimeConfig.CONFIG_PATH = config/gtit/signin/online_time_config.json，缺省 30min/2h/5h）
        try {
            com.miaokatze.gtit.signin.OnlineTimeConfig.init();
            GTInterestingThing.LOG.info("[0/3] 在线时长奖励配置已初始化");
        } catch (Throwable t) {
            GTInterestingThing.LOG.error("[0/3] 在线时长奖励配置初始化失败", t);
        }

        // v1.7.6 G5：初始化自动祝福邮件配置（BlessingConfig.CONFIG_PATH = config/gtit/mail/blessing_config.json，缺失时生成默认节日表+生日模板）
        // 注：默认配置仅写字币 ID 字符串，物品解析在发送时进行，故 preInit 阶段调用安全
        try {
            com.miaokatze.gtit.mail.BlessingConfig.init();
            GTInterestingThing.LOG.info("[0/3] 自动祝福邮件配置已初始化");
        } catch (Throwable t) {
            GTInterestingThing.LOG.error("[0/3] 自动祝福邮件配置初始化失败", t);
        }

        // 初始化机器工作音效静音配置
        // B2-13：同上防御——MuteConfig 静态布尔走默认值，不中断 preInit
        try {
            MuteConfig.init();
        } catch (Throwable t) {
            GTInterestingThing.LOG.error("[0/3] 机器工作音效静音配置初始化失败", t);
        }

        // 注册事件处理器
        // B2-13：注册失败仅损失登录礼包+指环刷新（降级可接受），不中断 preInit
        try {
            FMLCommonHandler.instance()
                .bus()
                .register(new PlayerLoginHandler());
        } catch (Throwable t) {
            GTInterestingThing.LOG.error("[0/3] 玩家登录事件处理器注册失败（首登礼包/指环刷新将不可用）", t);
        }

        // O2-03：注册服务器主线程任务调度器（网络线程→主线程任务统一队列，
        // 邮件/抽奖/签到/交易编辑四链路网络包处理器共用，收编三份同构队列）
        try {
            FMLCommonHandler.instance()
                .bus()
                .register(com.miaokatze.gtit.util.ServerTaskScheduler.INSTANCE);
            GTInterestingThing.LOG.info("[0/3] 服务器主线程任务调度器已注册");
        } catch (Throwable t) {
            GTInterestingThing.LOG.error("[0/3] 服务器主线程任务调度器注册失败", t);
        }

        // === v1.6.0 骨架：签到事件监听器注册 ===
        // TODO: v1.6.3 启用
        try {
            FMLCommonHandler.instance()
                .bus()
                .register(new com.miaokatze.gtit.signin.DailySignInHandler());
            GTInterestingThing.LOG.info("[0/3] 签到事件监听器已注册");
        } catch (Throwable t) {
            GTInterestingThing.LOG.error("[0/3] 签到事件监听器注册失败", t);
        }

        // v1.6.28: 注册冷却完毕通知调度器（服务端 tick 事件，检查冷却结束并团队播报）
        try {
            FMLCommonHandler.instance()
                .bus()
                .register(com.miaokatze.gtit.trade.v2.NekoNotificationScheduler.INSTANCE);
            GTInterestingThing.LOG.info("[NekoNotify] 冷却完毕通知调度器已注册");
        } catch (Throwable t) {
            GTInterestingThing.LOG.error("[NekoNotify] 冷却完毕通知调度器注册失败", t);
        }

        // v1.7.1: 注册抽奖事件监听器（登录预载/登出落盘/抽奖任务队列/周期保存）
        try {
            FMLCommonHandler.instance()
                .bus()
                .register(new com.miaokatze.gtit.lottery.LotteryHandler());
            GTInterestingThing.LOG.info("[0/3] 抽奖事件监听器已注册");
        } catch (Throwable t) {
            GTInterestingThing.LOG.error("[0/3] 抽奖事件监听器注册失败", t);
        }

        // v1.7.2: 注册邮件事件监听器（登录投递奖励/登出落盘/操作任务队列/周期保存）
        try {
            FMLCommonHandler.instance()
                .bus()
                .register(new com.miaokatze.gtit.mail.MailHandler());
            GTInterestingThing.LOG.info("[0/3] 邮件事件监听器已注册");
        } catch (Throwable t) {
            GTInterestingThing.LOG.error("[0/3] 邮件事件监听器注册失败", t);
        }

        // BUG B1 修复：注册猫猫币钱包事件监听器（登出卸载/脏钱包周期落盘/余额推送节流冲刷）
        try {
            FMLCommonHandler.instance()
                .bus()
                .register(new com.miaokatze.gtit.trade.NekoWalletHandler());
            GTInterestingThing.LOG.info("[0/3] 猫猫币钱包事件监听器已注册");
        } catch (Throwable t) {
            GTInterestingThing.LOG.error("[0/3] 猫猫币钱包事件监听器注册失败", t);
        }

        // v1.7.0 目标 5: 注册交易配置同步监听器（登录时向客户端推送服务端交易/标签页配置）
        try {
            FMLCommonHandler.instance()
                .bus()
                .register(new com.miaokatze.gtit.trade.v2.NekoTradeSyncHandler());
            GTInterestingThing.LOG.info("[0/3] 交易配置同步监听器已注册");
        } catch (Throwable t) {
            GTInterestingThing.LOG.error("[0/3] 交易配置同步监听器注册失败", t);
        }

        // E4a: 注册 MIAO-GTNH 宿主登录监听器（综合包注入成功后延时双语提示；注册范式同上）
        try {
            FMLCommonHandler.instance()
                .bus()
                .register(new MiaoGtnhHost());
            GTInterestingThing.LOG.info("[0/3] MIAO-GTNH 宿主登录监听器已注册");
        } catch (Throwable t) {
            GTInterestingThing.LOG.error("[0/3] MIAO-GTNH 宿主登录监听器注册失败", t);
        }

        // 定义机器注册任务
        Runnable registerRunnable = () -> {
            GTInterestingThing.LOG.info("[1/3] 开始执行机器注册流程...");
            try {
                MachineLoader.initMachines();
                GTInterestingThing.LOG.info("[1/3] 机器注册流程执行完毕。");
            } catch (Throwable t) {
                GTInterestingThing.LOG.error("[1/3] 机器注册过程中发生严重错误，请检查日志", t);
            }
        };

        // v1.6.34 方案 B3：将机器注册任务添加到 GregTech 的 sAfterGTPreload 队列
        // sAfterGTPreload 在 GregTechAPI 中静态初始化为 ArrayList，永不为 null，无需 null 检查
        //
        // 方案 B3 决策（v1.6.34，对齐 GTSR v1.7.22 / GTSWN v1.5.19）：
        // @Mod 改为 required-before:gregtech，确保 GTIT preInit 在 GT preInit 之前执行
        // 机器注册 Runnable 在 GTIT preInit 时加入 sAfterGTPreload 队列
        // GT preInit 末尾（GTMod.java:342-344）消费队列，执行 MachineLoader.initMachines()
        //
        // 时序分析（三队列可行性，含 v1.6.33 方案 D 懒加载后的重新评估）：
        // - sAfterGTPreload（PreInit 末尾）：✓ 构造函数检查通过（sPostloadStarted=false）
        // 懒加载后 <clinit> 不再访问 casingBlock → 本方案（v1.6.34）
        // MTE 注册时机早于 GT5U Init 阶段，GT5U Init 的 registerUnificationEntries 等会正常处理 GTIT MTE
        // - sAfterGTLoad（Init 末尾，v1.6.33 方案 D）：构造函数检查通过，懒加载 <clinit> 安全
        // 但 MTE 错过 GT5U Init 阶段的 registerUnificationEntries / GTItemIterator 等统一处理
        // 导致其他 mod 依赖 OreDict 统一的配方极个别丢失 → v1.6.34 弃用
        // - sAfterGTPostload（PostInit 末尾）：构造函数检查失败（sPostloadStarted=true）→ 不可用
        //
        // 懒加载关键（v1.6.33 引入，v1.6.34 保留）：MTENekoVendingMachineV2.NEKO_STRUCTURE_DEFINITION
        // 不再 static final 直接初始化，改为 getStructureDefinition() 首次调用时构建（运行时，casingBlock 必然就绪）
        // VM mod 的 after:vendingmachine 依赖确保 VM preInit（casingBlock 初始化）在 GTIT preInit 之前完成
        try {
            int before = GregTechAPI.sAfterGTPreload.size();
            GregTechAPI.sAfterGTPreload.add(registerRunnable);
            int after = GregTechAPI.sAfterGTPreload.size();
            GTInterestingThing.LOG.info("[1/3] 已将机器注册任务加入 GregTech PreInit 队列 (队列大小: " + before + " -> " + after + ")");
        } catch (Throwable t) {
            GTInterestingThing.LOG.error("无法将注册任务添加到 GregTech 队列", t);
        }
    }

    /**
     * 扩展 Baubles 戒指栏到 10 个
     * 使用 Baubles-Expanded 的 API，在 PREINIT 阶段调用
     */
    private void expandBaublesRingSlots() {
        try {
            boolean result = baubles.api.expanded.BaubleExpandedSlots.tryAssignSlotsUpToMinimum("ring", 10);
            if (result) {
                GTInterestingThing.LOG.info("Baubles 戒指栏已扩展到 10 个槽位");
            } else {
                GTInterestingThing.LOG.warn("Baubles 戒指栏扩展失败，尝试 INIT 阶段覆盖");
            }
        } catch (NoClassDefFoundError e) {
            GTInterestingThing.LOG.warn("Baubles-Expanded API 不可用，戒指栏扩展已跳过");
        } catch (Exception e) {
            GTInterestingThing.LOG.error("扩展 Baubles 戒指栏时发生错误", e);
        }
    }

    /**
     * INIT 阶段：使用 overrideSlots 确保正确的槽位顺序
     * 保留所有原有槽位类型，只将 ring 扩展到 10 个并连续排列
     */
    private void ensureBaublesRingSlots() {
        try {
            java.util.List<String> newSlots = new java.util.ArrayList<>();
            boolean ringsAdded = false;

            // 遍历当前槽位，保留非ring类型，ring位置扩展到10个
            for (int i = 0; i < baubles.api.expanded.BaubleExpandedSlots.slotsCurrentlyUsed(); i++) {
                String type = baubles.api.expanded.BaubleExpandedSlots.getSlotType(i);
                if ("ring".equals(type) && !ringsAdded) {
                    // 在第一个ring位置插入10个ring槽位
                    for (int j = 0; j < 10; j++) {
                        newSlots.add("ring");
                    }
                    ringsAdded = true;
                } else if (!"ring".equals(type)) {
                    newSlots.add(type);
                }
                // 后续的ring槽位跳过（已被10个替换）
            }

            if (!ringsAdded) {
                // 如果没有ring槽位，在amulet后添加
                int amuletIndex = newSlots.indexOf("amulet");
                if (amuletIndex >= 0) {
                    for (int j = 0; j < 10; j++) {
                        newSlots.add(amuletIndex + 1 + j, "ring");
                    }
                } else {
                    newSlots.add(0, "amulet");
                    for (int j = 0; j < 10; j++) {
                        newSlots.add("ring");
                    }
                    newSlots.add("belt");
                }
            }

            baubles.api.expanded.BaubleExpandedSlots.overrideSlots(newSlots.toArray(new String[0]));
            GTInterestingThing.LOG.info("Baubles 槽位已设置: " + newSlots);
        } catch (NoClassDefFoundError e) {
            GTInterestingThing.LOG.warn("Baubles-Expanded API 不可用，戒指栏扩展已跳过");
        } catch (Exception e) {
            GTInterestingThing.LOG.error("确保戒指栏扩展时发生错误", e);
        }
    }

    /**
     * 初始化阶段 (Init)
     */
    @SuppressWarnings({ "unused" })
    public void init(FMLInitializationEvent event) {
        // 确保 Baubles 戒指栏扩展（防止被 BaublesConfig 覆盖）
        ensureBaublesRingSlots();

        // 注册猫猫币团队数据到 GTNHLib Teams
        // O2-04：Teams 探测/注册/降级日志统一走 TeamDataProvider 门面（不可用时统一回退个人模式）
        TeamDataProvider.registerTeamData();

        // 注册战利品（箱子/钓鱼）
        LootRegistrar.init();

        GTInterestingThing.LOG.info("[2/3] 开始初始化创造模式物品栏...");

        CreativeTabManager.initCreativeTab();
        GTInterestingThing.LOG.info(
            "[2/3] 创造模式物品栏初始化完成，当前包含 " + CreativeTabManager.getItemsToAdd()
                .size() + " 个物品。");

        // === v1.6.0 骨架：网络包初始化 ===
        // TODO: v1.6.3~v1.6.5 启用
        try {
            com.miaokatze.gtit.signin.SignInNetworkManager.init();
            GTInterestingThing.LOG.info("[2/3] 签到网络包已初始化");
        } catch (Throwable t) {
            GTInterestingThing.LOG.error("[2/3] 签到网络包初始化失败", t);
        }
        // v1.7.1: 抽奖网络包（同步/请求/结果三通道）
        try {
            com.miaokatze.gtit.lottery.LotteryNetworkManager.init();
            GTInterestingThing.LOG.info("[2/3] 抽奖网络包已初始化");
        } catch (Throwable t) {
            GTInterestingThing.LOG.error("[2/3] 抽奖网络包初始化失败", t);
        }
        // O2-B01: 钱包余额通道自立（trade 域专用通道 gtit_wallet，余额推送不再借道抽奖通道）
        try {
            com.miaokatze.gtit.trade.WalletNetworkManager.init();
            GTInterestingThing.LOG.info("[2/3] 钱包余额网络包已初始化");
        } catch (Throwable t) {
            GTInterestingThing.LOG.error("[2/3] 钱包余额网络包初始化失败", t);
        }
        // O2-01: 成就骨架配置开关（默认 false）——关闭时跳过初始化，不再打印误导性"已初始化"日志
        if (Config.enableAchievements) {
            try {
                com.miaokatze.gtit.achievement.AchievementNetwork.init();
                GTInterestingThing.LOG.info("[2/3] 成就网络包已初始化");
            } catch (Throwable t) {
                GTInterestingThing.LOG.error("[2/3] 成就网络包初始化失败", t);
            }
        } else {
            GTInterestingThing.LOG.info("[2/3] 成就网络包已跳过（enableAchievements=false，未启用）");
        }
        // v1.7.2: 邮件网络包（同步/操作双通道）
        try {
            com.miaokatze.gtit.mail.MailNetworkManager.init();
            GTInterestingThing.LOG.info("[2/3] 邮件网络包已初始化");
        } catch (Throwable t) {
            GTInterestingThing.LOG.error("[2/3] 邮件网络包初始化失败", t);
        }
        // v1.7.0 目标 4: 编辑模式网络包（C→S 编辑操作）+ 编辑模式管理器（玩家登出自动清理）
        try {
            com.miaokatze.gtit.trade.v2.NekoEditNetworkManager.init();
            com.miaokatze.gtit.trade.v2.NekoEditModeManager.init();
            GTInterestingThing.LOG.info("[2/3] 编辑模式网络包与管理器已初始化");
        } catch (Throwable t) {
            GTInterestingThing.LOG.error("[2/3] 编辑模式网络包初始化失败", t);
        }
        // v1.7.0 目标 5: 交易配置网络包（S→C 交易/标签页配置全量同步）
        try {
            com.miaokatze.gtit.trade.v2.NekoTradeNetworkManager.init();
            GTInterestingThing.LOG.info("[2/3] 交易配置同步网络包已初始化");
        } catch (Throwable t) {
            GTInterestingThing.LOG.error("[2/3] 交易配置同步网络包初始化失败", t);
        }
    }

    /**
     * 后初始化阶段 (PostInit)
     */
    @SuppressWarnings({ "unused" })
    public void postInit(FMLPostInitializationEvent event) {
        GTInterestingThing.LOG.info("[3/3] 开始注册配方...");
        try {
            GTITRecipes.init();
            GTInterestingThing.LOG.info("[3/3] 配方注册完成。");
        } catch (Throwable t) {
            GTInterestingThing.LOG.error("[3/3] 配方注册过程中发生错误", t);
        }

        // 初始化猫猫币货币注册表（物品在此阶段已全部注册完成）
        // 原因：之前因 getTooltip() NPE 崩溃而临时禁用，现 @SkipGenerateDescription 已修复根因
        try {
            NekoCurrencyRegistrar.init();
        } catch (Throwable t) {
            GTInterestingThing.LOG.error("[3/3] 猫猫币注册失败", t);
        }

        // E4a: 内置基础贸易组预探测——base 资产可用时抑制旧 42 条默认注入。
        // 必须先于 NekoPageRegistry/NekoTradeRegistryV2 的客户端初始化执行：
        // 单人存档下 postInit 的 initializeClient 会触发 NekoTradeConfig.init() 的
        // 默认文件生成（早于 serverStarted），抑制标志在此前置设置才来得及生效。
        try {
            BundledTradeGroups.prepareDefaultSuppression();
        } catch (Throwable t) {
            GTInterestingThing.LOG.error("[3/3] 内置基础贸易组预探测失败（回落旧默认交易）", t);
        }

        // 初始化标签页注册表
        // 原因：必须在 postInit 阶段初始化，确保专用服务器客户端也能加载
        // （FMLServerStartedEvent 不会在专用服务器客户端触发）
        // NekoPageRegistry: 加载标签页配置（V2 引用，安全）
        // V1 的 NekoTradeRegistry.initializeClient() 已移除（V1 反射注入 VM TradeDatabase 的逻辑不再需要）
        try {
            NekoPageRegistry.initialize();
        } catch (Throwable t) {
            GTInterestingThing.LOG.error("[3/3] 猫猫售货机标签页初始化失败", t);
        }

        // 初始化 V2 交易注册表（客户端）
        // V2 交易系统是完全独立的实现，为新版猫猫售货机 V2 提供交易数据
        // 在 postInit 阶段调用，确保专用服务器客户端也能加载交易配置
        // V1 的 NekoTradeRegistry 和 BqEventBridge 已移除，V2 使用自己的 NekoBqBridge
        try {
            NekoTradeRegistryV2.initializeClient();
        } catch (Throwable t) {
            GTInterestingThing.LOG.error("[3/3] V2 猫猫售货机交易映射客户端初始化失败", t);
        }

        // 注册 V2 BQ 桥接器（V2 独立于 VM 的 BqAdapter，直接对接 BQ API）
        // 解决两个问题：
        // 1. NekoBqBridge.init() 从未被调用导致 bqLoaded 永远为 false，所有 BQ 检查走安全回退返回 true（不锁定）
        // 2. V2 缺少 BQ 事件监听器导致无任务完成事件同步和玩家登录跨会话状态同步
        try {
            com.miaokatze.gtit.trade.v2.NekoBqBridge.register();
        } catch (Throwable t) {
            GTInterestingThing.LOG.error("[3/3] V2 NekoBqBridge 注册失败", t);
        }

        // 注册 AE2 Infinity Cell Handler
        try {
            AEApi.instance()
                .registries()
                .cell()
                .addCellHandler(new InfinityCellHandler());
            GTInterestingThing.LOG.info("[3/3] AE2 Infinity Cell Handler 注册完成。");
        } catch (Throwable t) {
            GTInterestingThing.LOG.error("[3/3] AE2 Infinity Cell Handler 注册失败", t);
        }

        // === v1.6.0 骨架：BQ 成就对接桥接器注册 ===
        // TODO: v1.6.5 启用
        // O2-01: 成就骨架配置开关（默认 false）——关闭时跳过注册
        if (Config.enableAchievements) {
            try {
                com.miaokatze.gtit.achievement.BqAchievementBridge.register();
            } catch (Throwable t) {
                GTInterestingThing.LOG.error("[3/3] BqAchievementBridge 注册失败", t);
            }
        } else {
            GTInterestingThing.LOG.info("[3/3] BqAchievementBridge 已跳过（enableAchievements=false，未启用）");
        }
    }

    /**
     * 服务器启动阶段
     */
    @SuppressWarnings({ "unused" })
    public void serverStarting(FMLServerStartingEvent event) {
        event.registerServerCommand(new GTITGiftCommand());

        // E4a: BQ 任务注入（GTIT 自身包 + MIAO-GTNH 综合包判定注入）。
        // after:betterquesting 排序约束保证 BQ default load 已完成；
        // BQ 缺席/资产未就位/判定不满足时注入器静默返回，零副作用。
        try {
            MiaoGtnhHost.onServerStarting();
        } catch (Throwable t) {
            GTInterestingThing.LOG.error("BQ 任务包注入失败（不影响服务器启动）", t);
        }
    }

    /**
     * 服务器启动完成阶段
     * 在此初始化猫猫币钱包管理器（需要 World 对象）
     */
    @SuppressWarnings({ "unused" })
    public void serverStarted(FMLServerStartedEvent event) {
        // 初始化猫猫币钱包管理器（需要 World 对象）
        // NekoPageRegistry 已在 postInit() 中初始化
        // V1 的 NekoTradeRegistry.initialize()（反射注入 VM TradeDatabase）已移除
        // V2 使用独立的 NekoTradeDatabase，不依赖 VM 的 TradeDatabase
        try {
            MinecraftServer server = MinecraftServer.getServer();
            if (server != null && server.getEntityWorld() != null) {
                NekoWalletManager.INSTANCE.init(server.getEntityWorld());

                // 初始化 V2 交易注册表（服务端）
                // V2 交易系统是完全独立的实现，为新版猫猫售货机 V2 提供交易数据
                // 在 serverStarted 阶段调用，确保配置文件已就绪
                NekoTradeRegistryV2.initialize();

                // E4a: 磁盘权威装载完成后，应用内置贸易组（base / miao 替换）
                // 与外部注册队列（IMC/直连），记账门控保证玩家文件零破坏
                try {
                    BundledTradeGroups.applyBundledGroups();
                    NekoTradeIntegrationAPI.applyQueuedGroups();
                } catch (Throwable t2) {
                    GTInterestingThing.LOG.error("内置/外部贸易组应用失败（不影响后续模块初始化）", t2);
                }

                // 初始化 V2 交易历史管理器（需要 World 对象）
                // 负责持久化玩家交易历史到 <world>/gtit_neko_histories/<player_uuid>.dat
                com.miaokatze.gtit.trade.v2.NekoHistoryManager.INSTANCE.init(server.getEntityWorld());

                // 初始化 V2 收藏追踪器（需要 World 对象）
                // 负责持久化玩家收藏到 <world>/gtit_neko_favourites/<player_uuid>.dat
                com.miaokatze.gtit.trade.v2.NekoFavouritesTracker.INSTANCE.init(server.getEntityWorld());

                // === v1.6.0 骨架：各模块 Manager 初始化 ===
                // TODO: v1.6.3~v1.6.5 启用
                try {
                    com.miaokatze.gtit.signin.DailySignInManager.INSTANCE.init(server.getEntityWorld());
                    GTInterestingThing.LOG.info("签到系统已初始化");
                } catch (Throwable t2) {
                    GTInterestingThing.LOG.error("签到系统初始化失败", t2);
                }
                try {
                    com.miaokatze.gtit.lottery.LotteryManager.INSTANCE.init(server.getEntityWorld());
                    GTInterestingThing.LOG.info("抽奖系统已初始化");
                } catch (Throwable t2) {
                    GTInterestingThing.LOG.error("抽奖系统初始化失败", t2);
                }
                // E4b: 应用外部抽奖池组注册队列（IMC/jar 资产/直连，记账门控保证玩家文件零破坏）。
                // 时序依赖：必须在 LotteryManager.init（内部 loadConfig 装载 lottery.json）之后，
                // 否则抽奖配置装载会把刚合并的池覆盖回磁盘旧态
                try {
                    com.miaokatze.gtit.lottery.api.LotteryIntegrationAPI.applyQueuedPools();
                } catch (Throwable t2) {
                    GTInterestingThing.LOG.error("外部抽奖池组应用失败（不影响后续模块初始化）", t2);
                }
                // O2-01: 成就骨架配置开关（默认 false）——关闭时跳过初始化
                if (Config.enableAchievements) {
                    try {
                        com.miaokatze.gtit.achievement.AchievementManager.INSTANCE.init(server.getEntityWorld());
                        GTInterestingThing.LOG.info("成就系统已初始化");
                    } catch (Throwable t2) {
                        GTInterestingThing.LOG.error("成就系统初始化失败", t2);
                    }
                } else {
                    GTInterestingThing.LOG.info("成就系统已跳过（enableAchievements=false，未启用）");
                }
                // v1.7.2: 邮件系统（个人维度，<world>/gtit_mail/）
                try {
                    com.miaokatze.gtit.mail.MailManager.INSTANCE.init(server.getEntityWorld());
                    GTInterestingThing.LOG.info("邮件系统已初始化");
                } catch (Throwable t2) {
                    GTInterestingThing.LOG.error("邮件系统初始化失败", t2);
                }
            }
        } catch (Throwable t) {
            GTInterestingThing.LOG.error("猫猫币钱包/交易初始化失败", t);
        }

        // 初始化 Infinity Cell StorageManager (WorldSavedData)
        try {
            World world = MinecraftServer.getServer()
                .getEntityWorld();
            if (world != null) {
                StorageManager manager = (StorageManager) world.mapStorage
                    .loadData(StorageManager.class, "GTIT_InfinityCellStorage");
                if (manager == null) {
                    manager = new StorageManager("GTIT_InfinityCellStorage");
                    world.mapStorage.setData("GTIT_InfinityCellStorage", manager);
                }
                GTInterestingThing.LOG.info("Infinity Cell StorageManager 初始化完成。");
            }
        } catch (Throwable t) {
            GTInterestingThing.LOG.error("Infinity Cell StorageManager 初始化失败", t);
        }
    }

    /**
     * 模组加载完成阶段
     */
    public void loadComplete(cpw.mods.fml.common.event.FMLLoadCompleteEvent event) {}

    /**
     * IMC 消息分发阶段（E4a：外部贸易组注册通道之一；E4b：抽奖池组同构接入）
     * <p>
     * 消息路由与载荷解析见 {@code NekoTradeIntegrationAPI#handleImcMessages}
     * （含 {@code gtit:registerTradeGroup} 与 {@code gtit:registerTradeAsset} 两 key）
     * 与 {@code LotteryIntegrationAPI#handleImcMessages}（{@code gtit:registerLotteryPool}）；
     * 此时服务器未启动，组定义入队等待 serverStarted 统一应用。
     */
    public void processIMC(FMLInterModComms.IMCEvent event) {
        try {
            NekoTradeIntegrationAPI.handleImcMessages(event.getMessages());
        } catch (Throwable t) {
            GTInterestingThing.LOG.error("IMC 贸易组消息处理失败（不影响 GTIT 主功能）", t);
        }
        try {
            com.miaokatze.gtit.lottery.api.LotteryIntegrationAPI.handleImcMessages(event.getMessages());
        } catch (Throwable t) {
            GTInterestingThing.LOG.error("IMC 抽奖池组消息处理失败（不影响 GTIT 主功能）", t);
        }
    }

    /**
     * 服务器停止阶段（BUG B1 修复：钱包全量落盘钩子）
     * <p>
     * 全量保存所有个人钱包，确保关服前最后一批未经过显式 saveWallet 覆盖的
     * 余额变动不丢账（此前 saveAll 全项目零调用，关服无批量落盘）。
     * 团队钱包由 GTNHLib Teams 随世界存档托管。
     */
    @SuppressWarnings({ "unused" })
    public void serverStopping(FMLServerStoppingEvent event) {
        try {
            NekoWalletManager.INSTANCE.saveAll();
        } catch (Throwable t) {
            GTInterestingThing.LOG.error("关服保存猫猫币钱包失败", t);
        }
        // O2-03：清空主线程任务队列（FMLServerStoppingEvent 不投递给事件总线监听器，
        // 由本生命周期钩子代清——防止单机连续开新世界时残留任务跨世界执行）
        com.miaokatze.gtit.util.ServerTaskScheduler.clear();
    }

    /**
     * 服务器已停止阶段（BUG B1 修复：落盘收尾 + 清空钱包内存缓存）
     * <p>
     * 再次全量落盘（幂等兜底）后清空个人钱包缓存与脏标记，防止单机连续切换
     * 世界时旧世界的钱包对象驻留内存遮蔽新世界的 .dat 文件。
     */
    @SuppressWarnings({ "unused" })
    public void serverStopped(FMLServerStoppedEvent event) {
        try {
            NekoWalletManager.INSTANCE.unloadAll();
        } catch (Throwable t) {
            GTInterestingThing.LOG.error("停服收尾猫猫币钱包失败", t);
        }
    }
}

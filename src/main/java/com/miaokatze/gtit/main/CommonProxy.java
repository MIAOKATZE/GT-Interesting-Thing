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
import com.miaokatze.gtit.event.PlayerLoginHandler;
import com.miaokatze.gtit.loader.ItemLoader;
import com.miaokatze.gtit.loader.MachineLoader;
import com.miaokatze.gtit.recipe.GTITRecipes;
import com.miaokatze.gtit.recipe.TestMachineRecipes;
import com.miaokatze.gtit.register.BlockRegistrar;
import com.miaokatze.gtit.register.CreativeTabManager;
import com.miaokatze.gtit.register.TextureManager;
import com.miaokatze.gtit.trade.BqEventBridge;
import com.miaokatze.gtit.trade.NekoCurrencyRegistrar;
import com.miaokatze.gtit.trade.NekoPageRegistry;
import com.miaokatze.gtit.trade.NekoTeamData;
import com.miaokatze.gtit.trade.NekoTradeRegistry;
import com.miaokatze.gtit.trade.NekoWalletManager;
import com.miaokatze.gtit.trade.v2.NekoTradeRegistryV2;

import appeng.api.AEApi;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartedEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
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

        // 注册方块
        try {
            BlockRegistrar.init();
        } catch (Throwable t) {
            GTInterestingThing.LOG.error("方块注册过程中发生严重错误，请检查日志", t);
        }

        // 初始化新手宝箱配置
        GiftConfig.init();

        // 初始化机器工作音效静音配置
        MuteConfig.init();

        // 注册事件处理器
        FMLCommonHandler.instance()
            .bus()
            .register(new PlayerLoginHandler());

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

        // 将注册任务添加到 GregTech 的 sAfterGTLoad 队列
        try {
            if (GregTechAPI.sAfterGTLoad == null) {
                GTInterestingThing.LOG.warn("警告: GregTechAPI.sAfterGTLoad 为空，无法添加注册任务。");
            } else {
                int before = GregTechAPI.sAfterGTLoad.size();
                GregTechAPI.sAfterGTLoad.add(registerRunnable);
                int after = GregTechAPI.sAfterGTLoad.size();
                GTInterestingThing.LOG.info("[1/3] 已将机器注册任务加入 GregTech 加载队列 (队列大小: " + before + " -> " + after + ")");
            }
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
        try {
            com.gtnewhorizon.gtnhlib.teams.TeamDataRegistry.register(NekoTeamData.ID, NekoTeamData::new);
            GTInterestingThing.LOG.info("[2/3] 猫猫币团队数据已注册到 GTNHLib Teams");
        } catch (NoClassDefFoundError e) {
            GTInterestingThing.LOG.warn("[2/3] GTNHLib Teams API 不可用，猫猫币钱包将回退到个人模式");
        } catch (Exception e) {
            GTInterestingThing.LOG.error("[2/3] 注册猫猫币团队数据失败", e);
        }

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
        try {
            com.miaokatze.gtit.achievement.AchievementNetwork.init();
            GTInterestingThing.LOG.info("[2/3] 成就网络包已初始化");
        } catch (Throwable t) {
            GTInterestingThing.LOG.error("[2/3] 成就网络包初始化失败", t);
        }
    }

    /**
     * 后初始化阶段 (PostInit)
     */
    @SuppressWarnings({ "unused" })
    public void postInit(FMLPostInitializationEvent event) {
        GTInterestingThing.LOG.info("[3/3] 开始注册配方...");
        try {
            TestMachineRecipes.init();
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

        // 初始化标签页注册表和交易映射
        // 原因：必须在 postInit 阶段初始化，确保专用服务器客户端也能加载
        // （FMLServerStartedEvent 不会在专用服务器客户端触发）
        // NekoPageRegistry: 加载标签页配置（安全，不依赖 TradeDatabase）
        // NekoTradeRegistry.initializeClient(): 仅填充静态映射，不注入 TradeDatabase
        // （TradeDatabase 会被 VM 在 serverStarting 阶段清空重载，注入会被覆盖）
        try {
            NekoPageRegistry.initialize();
            NekoTradeRegistry.initializeClient();
        } catch (Throwable t) {
            GTInterestingThing.LOG.error("[3/3] 猫猫售货机标签页/交易映射初始化失败", t);
        }

        // 初始化 V2 交易注册表（客户端）
        // V2 交易系统独立于 V1，为新版猫猫售货机 V2 提供交易数据
        // 在 postInit 阶段调用，确保专用服务器客户端也能加载交易配置
        try {
            NekoTradeRegistryV2.initializeClient();
        } catch (Throwable t) {
            GTInterestingThing.LOG.error("[3/3] V2 猫猫售货机交易映射客户端初始化失败", t);
        }

        // 注册 BQ 任务事件桥接器
        // VM 的 BqAdapter.setQuestFinished() 从未被调用，需要 BqEventBridge 监听 QuestEvent 来更新缓存
        try {
            BqEventBridge.register();
        } catch (Throwable t) {
            GTInterestingThing.LOG.error("[3/3] BqEventBridge 注册失败", t);
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
        try {
            com.miaokatze.gtit.achievement.BqAchievementBridge.register();
        } catch (Throwable t) {
            GTInterestingThing.LOG.error("[3/3] BqAchievementBridge 注册失败", t);
        }
    }

    /**
     * 服务器启动阶段
     */
    @SuppressWarnings({ "unused" })
    public void serverStarting(FMLServerStartingEvent event) {
        event.registerServerCommand(new GTITGiftCommand());
    }

    /**
     * 服务器启动完成阶段
     * 在此初始化猫猫币钱包管理器（需要 World 对象）
     */
    @SuppressWarnings({ "unused" })
    public void serverStarted(FMLServerStartedEvent event) {
        // 初始化猫猫币钱包管理器（需要 World 对象）
        // NekoPageRegistry 已在 postInit() 中初始化
        // NekoTradeRegistry.initializeClient() 已在 postInit() 中调用（填充映射）
        // NekoTradeRegistry.initialize() 在此调用（注入 TradeDatabase，必须在 VM serverStarting 之后）
        try {
            MinecraftServer server = MinecraftServer.getServer();
            if (server != null && server.getEntityWorld() != null) {
                NekoWalletManager.INSTANCE.init(server.getEntityWorld());
                // 注入猫猫币交易到 TradeDatabase（VM 在 serverStarting 阶段已加载 TradeDatabase）
                NekoTradeRegistry.initialize();

                // 初始化 V2 交易注册表（服务端）
                // V2 交易系统独立于 V1，为新版猫猫售货机 V2 提供交易数据
                // 在 serverStarted 阶段调用，确保配置文件已就绪
                NekoTradeRegistryV2.initialize();

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
                try {
                    com.miaokatze.gtit.achievement.AchievementManager.INSTANCE.init(server.getEntityWorld());
                    GTInterestingThing.LOG.info("成就系统已初始化");
                } catch (Throwable t2) {
                    GTInterestingThing.LOG.error("成就系统初始化失败", t2);
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
}

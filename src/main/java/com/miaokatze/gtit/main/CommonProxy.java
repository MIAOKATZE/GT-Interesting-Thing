package com.miaokatze.gtit.main;

import net.minecraft.server.MinecraftServer;

import com.miaokatze.gtit.Tags;
import com.miaokatze.gtit.command.GTITGiftCommand;
import com.miaokatze.gtit.common.loot.LootRegistrar;
import com.miaokatze.gtit.config.Config;
import com.miaokatze.gtit.config.GiftConfig;
import com.miaokatze.gtit.event.PlayerLoginHandler;
import com.miaokatze.gtit.loader.ItemLoader;
import com.miaokatze.gtit.loader.MachineLoader;
import com.miaokatze.gtit.recipe.GTITRecipes;
import com.miaokatze.gtit.recipe.TestMachineRecipes;
import com.miaokatze.gtit.register.CreativeTabManager;
import com.miaokatze.gtit.register.TextureManager;
import com.miaokatze.gtit.trade.NekoCurrencyRegistrar;
import com.miaokatze.gtit.trade.NekoTradeRegistry;
import com.miaokatze.gtit.trade.NekoWalletManager;

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
        GiftConfig.init();

        // 注册事件处理器
        FMLCommonHandler.instance()
            .bus()
            .register(new PlayerLoginHandler());

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

        // 注册战利品（箱子/钓鱼）
        LootRegistrar.init();

        GTInterestingThing.LOG.info("[2/3] 开始初始化创造模式物品栏...");

        CreativeTabManager.initCreativeTab();
        GTInterestingThing.LOG.info(
            "[2/3] 创造模式物品栏初始化完成，当前包含 " + CreativeTabManager.getItemsToAdd()
                .size() + " 个物品。");
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
        // 初始化猫猫币钱包管理器和交易注册
        // 原因：之前因 getTooltip() NPE 崩溃而临时禁用，现 @SkipGenerateDescription 已修复根因
        try {
            MinecraftServer server = MinecraftServer.getServer();
            if (server != null && server.getEntityWorld() != null) {
                NekoWalletManager.INSTANCE.init(server.getEntityWorld());
                // 初始化猫猫币交易注册（在钱包管理器初始化之后）
                NekoTradeRegistry.initialize();
            }
        } catch (Throwable t) {
            GTInterestingThing.LOG.error("猫猫币钱包/交易初始化失败", t);
        }
    }

    /**
     * 模组加载完成阶段
     */
    public void loadComplete(cpw.mods.fml.common.event.FMLLoadCompleteEvent event) {}
}

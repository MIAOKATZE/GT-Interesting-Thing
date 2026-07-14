package com.miaokatze.gtit.main;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.miaokatze.gtit.Tags;
import com.miaokatze.gtit.loader.MachineLoader;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLLoadCompleteEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartedEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import gregtech.api.GregTechAPI;

/**
 * GTInterestingThing - GTNH的趣味小物品模组
 * <p>
 * 包含：
 * - 浮空核心（Baubles饰品）
 * - 电力浮空核心（Baubles饰品+GT电源）
 * - 念力共振探矿核心（VisualProspecting集成）
 */
@Mod(
    modid = GTInterestingThing.MODID,
    name = "GTInterestingThing",
    version = Tags.VERSION,
    dependencies = "required-after:gregtech;after:NotEnoughItems;after:Baubles;after:VisualProspecting;after:vendingmachine")
public class GTInterestingThing {

    // 模组唯一标识符 (Mod ID)
    public static final String MODID = "gtit";

    // 日志记录器，用于输出模组运行信息
    public static final Logger LOG = LogManager.getLogger(MODID);

    // 代理类实例，用于处理客户端和服务端的差异化逻辑
    @SidedProxy(clientSide = "com.miaokatze.gtit.main.ClientProxy", serverSide = "com.miaokatze.gtit.main.CommonProxy")
    public static CommonProxy proxy;

    /**
     * 模组实例，作为 Mod 注解的实例持有者。
     */
    @Mod.Instance(MODID)
    public static GTInterestingThing instance;

    /**
     * 模组构造函数
     * <p>
     * v1.6.31 根因 B 修复（方案 B3-修正1）：在此处将机器注册任务加入 sAfterGTPreload 队列。
     * <p>
     * 必须在构造函数而非 preInit 中添加，因为 GTIT @Mod dependencies = "required-after:gregtech"，
     * gregtech 的 PreInit（含 sAfterGTPreload 队列遍历，GTMod.java 行 342-344）在 GTIT preInit 之前完成。
     * 构造函数早于所有 mod 的 PreInit，故任务能被 gregtech PreInit 末尾正确执行。
     * 仿照 DetravScannerMod 模式（GT5U 源码 DetravScannerMod.java 行 36-42）。
     * <p>
     * 时序对比：
     * - 原 B1 方案（sAfterGTLoad）：gregtech Init 末尾执行（GTMod.java 行 402-404），时机过晚
     * - B3 直接替换（sAfterGTLoad→sAfterGTPreload 在 preInit 添加）：任务永不执行（时序矛盾）
     * - B3-修正1（本方案，构造函数添加 sAfterGTPreload）：gregtech PreInit 末尾执行，时机最早且安全
     */
    public GTInterestingThing() {
        // 将机器注册任务加入 gregtech 的 sAfterGTPreload 队列
        // 该队列在 gregtech PreInit 末尾执行（GTMod.java 行 342-344），紧接 LoaderMetaTileEntities.run 之后
        // 此时 gregtech 自身机器已注册完毕，METATILEENTITIES 数组已就绪，可安全注册 GTIT 机器
        GregTechAPI.sAfterGTPreload.add(() -> {
            GTInterestingThing.LOG.info("[1/3] 开始执行机器注册流程...");
            try {
                MachineLoader.initMachines();
                GTInterestingThing.LOG.info("[1/3] 机器注册流程执行完毕。");
            } catch (Throwable t) {
                GTInterestingThing.LOG.error("[1/3] 机器注册过程中发生严重错误，请检查日志", t);
            }
        });
        GTInterestingThing.LOG.info("[1/3] 已将机器注册任务加入 GregTech sAfterGTPreload 队列");
    }

    /**
     * 预初始化阶段 (PreInit)
     *
     * @param event FML预初始化事件
     */
    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        proxy.preInit(event);
    }

    /**
     * 初始化阶段 (Init)
     *
     * @param event FML初始化事件
     */
    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        proxy.init(event);
    }

    /**
     * 后初始化阶段 (PostInit)
     *
     * @param event FML后初始化事件
     */
    @Mod.EventHandler
    public void postInit(FMLPostInitializationEvent event) {
        proxy.postInit(event);
    }

    /**
     * 服务器启动阶段
     *
     * @param event 服务器启动事件
     */
    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        proxy.serverStarting(event);
    }

    /**
     * 服务器启动完成阶段
     *
     * @param event 服务器启动完成事件
     */
    @Mod.EventHandler
    public void serverStarted(FMLServerStartedEvent event) {
        proxy.serverStarted(event);
    }

    /**
     * 模组加载完成阶段
     *
     * @param event 加载完成事件
     */
    @Mod.EventHandler
    public void loadComplete(FMLLoadCompleteEvent event) {
        proxy.loadComplete(event);
    }
}

package com.miaokatze.gtit.main;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.miaokatze.gtit.Tags;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLLoadCompleteEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartedEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import cpw.mods.fml.common.event.FMLServerStoppedEvent;
import cpw.mods.fml.common.event.FMLServerStoppingEvent;

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
    // v1.6.34 方案 B3：required-before:gregtech 确保 GTIT 的 preInit 在 GT preInit 之前执行
    // 这样 sAfterGTPreload 队列在 GT preInit 末尾消费时，GTIT 的机器注册 Runnable 已入队
    // 与 GTSR/GTSWN 对齐，避免方案 D（sAfterGTLoad）导致 MTE 错过 GT5U Init 阶段统一处理
    // E4a: after:betterquesting 为纯排序约束（不要求 BQ 存在）——确保 BQ 的 default load
    // 在 GTIT serverStarting 任务注入之前同步完成，注入为幂等追加不会被清库
    dependencies = "required-before:gregtech;after:NotEnoughItems;after:Baubles;after:VisualProspecting;after:vendingmachine;after:betterquesting")
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

    /**
     * IMC 消息分发阶段（E4a：外部贸易组注册通道之一）
     *
     * @param event FML IMC 事件（消息路由见 NekoTradeIntegrationAPI）
     */
    @Mod.EventHandler
    public void processIMC(cpw.mods.fml.common.event.FMLInterModComms.IMCEvent event) {
        proxy.processIMC(event);
    }

    /**
     * 服务器停止阶段（BUG B1 修复：钱包全量落盘钩子）
     *
     * @param event 服务器停止事件
     */
    @Mod.EventHandler
    public void serverStopping(FMLServerStoppingEvent event) {
        proxy.serverStopping(event);
    }

    /**
     * 服务器已停止阶段（BUG B1 修复：钱包落盘收尾 + 清空内存缓存）
     *
     * @param event 服务器已停止事件
     */
    @Mod.EventHandler
    public void serverStopped(FMLServerStoppedEvent event) {
        proxy.serverStopped(event);
    }
}

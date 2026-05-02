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
import cpw.mods.fml.common.event.FMLServerStartingEvent;

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
    dependencies = "required-after:gregtech;after:NotEnoughItems;after:Baubles;after:VisualProspecting")
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
     * 模组加载完成阶段
     *
     * @param event 加载完成事件
     */
    @Mod.EventHandler
    public void loadComplete(FMLLoadCompleteEvent event) {
        proxy.loadComplete(event);
    }
}

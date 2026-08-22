package com.miaokatze.gtit.achievement;

import java.util.UUID;

import net.minecraftforge.common.MinecraftForge;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import betterquesting.api.events.QuestEvent;
import cpw.mods.fml.common.Optional;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/**
 * BQ成就桥接器
 * 监听BQ任务事件，触发相关成就更新
 * 直接引用QuestEvent类型，用@Optional.Method保护
 */
public class BqAchievementBridge {

    /** 统一 logger（O2-B02 去中心化：与主类同用 "gtit" logger 名，日志过滤口径不变） */
    private static final Logger LOG = LogManager.getLogger("gtit");

    private static boolean bqLoaded = false;

    public static void register() {
        // TODO: v1.6.5 实现，检测 BQ 是否加载
        try {
            Class.forName("betterquesting.api.questing.IQuest");
            bqLoaded = true;
            BqAchievementBridge bridge = new BqAchievementBridge();
            MinecraftForge.EVENT_BUS.register(bridge);
            LOG.info("BqAchievementBridge 已注册，监听 BQ 任务事件");
        } catch (ClassNotFoundException e) {
            bqLoaded = false;
            LOG.info("BetterQuesting 未加载，跳过 BqAchievementBridge 注册");
        }
    }

    public static boolean isBqLoaded() {
        return bqLoaded;
    }

    @SubscribeEvent
    @Optional.Method(modid = "betterquesting")
    public void onQuestEvent(QuestEvent event) {
        // TODO: v1.6.5 实现
        // 监听任务完成事件，触发 BQ_LINKED 类型成就
    }

    public static void triggerBqQuestOnCompletion(UUID playerId, Achievement ach) {
        // TODO: v1.6.5 实现，带 try-catch NoClassDefFoundError 防护
    }

    @Optional.Method(modid = "betterquesting")
    public static void syncOnLogin(UUID playerId) {
        // TODO: v1.6.5 实现
    }
}

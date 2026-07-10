package com.miaokatze.gtit.signin;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * 签到事件处理器
 * 监听玩家登录/登出/服务器tick事件
 */
public class DailySignInHandler {

    private int tickCounter = 0;
    private static final int TICK_CHECK_INTERVAL = 100;

    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        // TODO: v1.6.3 实现
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        // TODO: v1.6.3 实现
    }

    @SubscribeEvent
    public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        // TODO: v1.6.3 实现
    }
}

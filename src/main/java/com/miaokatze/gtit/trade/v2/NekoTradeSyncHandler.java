package com.miaokatze.gtit.trade.v2;

import net.minecraft.entity.player.EntityPlayerMP;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;

/**
 * 交易配置同步事件处理器（v1.7.0 目标 5）
 * <p>
 * 监听玩家登录事件：玩家进入服务器后立即推送服务端权威的交易 + 标签页配置
 * （{@link NekoTradeNetworkManager#sendSyncToClient}），
 * 使专用服务器客户端的猫猫售货机 GUI 展示与服务端配置一致。
 * <p>
 * 单人存档下集成服务端同样会向自己推送，客户端包处理器识别单人存档后跳过应用
 * （共享静态注册表，服务端侧重载已直接刷新同一份数据），无副作用。
 * <p>
 * 在 {@code CommonProxy.preInit()} 中注册到 FML 事件总线（参照 {@code DailySignInHandler} 的注册模式）。
 */
public class NekoTradeSyncHandler {

    /**
     * 玩家登录：推送服务端交易/标签页配置全量同步
     */
    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.player instanceof EntityPlayerMP)) return;
        NekoTradeNetworkManager.sendSyncToClient((EntityPlayerMP) event.player);
    }
}

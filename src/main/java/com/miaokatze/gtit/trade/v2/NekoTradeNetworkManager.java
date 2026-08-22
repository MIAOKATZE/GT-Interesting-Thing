package com.miaokatze.gtit.trade.v2;

import net.minecraft.entity.player.EntityPlayerMP;

import com.miaokatze.gtit.trade.NekoPageConfig;
import com.miaokatze.gtit.trade.NekoTradeConfig;
import com.miaokatze.gtit.util.PlayerLookup;

import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.relauncher.Side;

/**
 * 交易配置网络包管理器（v1.7.0 目标 5）
 * <p>
 * 管理交易/标签页配置的服务端→客户端同步（FML SimpleNetworkWrapper 模式，
 * 与 {@code SignInNetworkManager} / {@code LotteryNetworkManager} 同范式）：
 * <ul>
 * <li>{@link NekoTradeSyncPacket}（id=0，S→C）：交易配置 + 标签页配置全量推送</li>
 * </ul>
 * 推送时机：玩家登录（{@link NekoTradeSyncHandler}）、编辑模式保存、
 * /gtit nekovm reload、/gtit nekovm sync。
 * <p>
 * 载荷直接取自服务端配置文件（{@link NekoTradeConfig#load()} /
 * {@link NekoPageConfig#load()}），保证推送内容始终与服务端磁盘权威配置一致。
 * 在 {@code CommonProxy.init()} 中调用 {@link #init()} 注册（双端都会执行，按 Side 注册方向）。
 */
public class NekoTradeNetworkManager {

    private static SimpleNetworkWrapper channel;
    private static final String CHANNEL_NAME = "gtit_nekotrade";
    private static boolean initialized = false;

    /** 包 ID：配置全量同步（服务端→客户端） */
    private static final int ID_SYNC = 0;

    /**
     * 注册网络通道与消息（幂等）
     */
    public static void init() {
        if (initialized) return;
        channel = NetworkRegistry.INSTANCE.newSimpleChannel(CHANNEL_NAME);
        channel.registerMessage(NekoTradeSyncPacket.Handler.class, NekoTradeSyncPacket.class, ID_SYNC, Side.CLIENT);
        initialized = true;
    }

    // ==================== 服务端发送 ====================

    /**
     * 服务端：向指定玩家推送交易 + 标签页配置全量同步
     *
     * @param player 目标玩家
     */
    public static void sendSyncToClient(EntityPlayerMP player) {
        if (!initialized || channel == null || player == null) return;
        channel.sendTo(buildSyncPacket(), player);
    }

    /**
     * 服务端：向全体在线玩家广播交易 + 标签页配置全量同步
     * <p>
     * 配置全服一致，载荷只构建一次后逐玩家发送。
     * 配置热重载（/gtit nekovm reload）与编辑模式保存后调用。
     */
    public static void sendSyncToAll() {
        if (!initialized || channel == null) return;
        // O2-12：PlayerLookup 统一遍历（载荷只构建一次，服务器未启动时静默跳过）
        NekoTradeSyncPacket packet = buildSyncPacket();
        PlayerLookup.forEachOnlinePlayer(player -> channel.sendTo(packet, player));
    }

    /**
     * 构建全量同步包（载荷取自服务端磁盘权威配置）
     */
    private static NekoTradeSyncPacket buildSyncPacket() {
        String tradesJson = NekoTradeConfig.toJson(NekoTradeConfig.load());
        String pagesJson = NekoPageConfig.toJson(NekoPageConfig.load());
        return new NekoTradeSyncPacket(tradesJson, pagesJson);
    }

    public static boolean isInitialized() {
        return initialized;
    }

    public static SimpleNetworkWrapper getChannel() {
        return channel;
    }
}

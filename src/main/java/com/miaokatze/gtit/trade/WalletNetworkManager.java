package com.miaokatze.gtit.trade;

import java.util.Map;

import net.minecraft.entity.player.EntityPlayerMP;

import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.relauncher.Side;

/**
 * 钱包余额网络包管理器（O2-B01 通道自立）
 * <p>
 * 管理钱包余额的服务端→客户端推送（FML SimpleNetworkWrapper 模式，
 * 与 {@code LotteryNetworkManager} / {@code SignInNetworkManager} 同范式）：
 * <ul>
 * <li>{@link WalletBalancePacket}（id=0，S→C）：钱包余额轻量推送（只带余额，
 * 钱包余额变化经脏标记节流合帧后由 {@link NekoWalletManager} 冲刷下发；
 * 登录时全量补推一次，防 GUI 先于任何余额变更打开的首包竞态）</li>
 * </ul>
 * 原先余额推送寄居抽奖通道（{@code LotteryNetworkManager.sendBalanceToClient}），
 * 形成 trade→lottery 的域间反向；自立后 lottery 通道回归纯抽奖。
 * 在 {@code CommonProxy.init()} 中调用 {@link #init()} 注册（双端都会执行，按 Side 注册方向）。
 */
public class WalletNetworkManager {

    private static SimpleNetworkWrapper channel;
    private static final String CHANNEL_NAME = "gtit_wallet";
    private static boolean initialized = false;

    /** 包 ID：钱包余额轻量推送（服务端→客户端） */
    private static final int ID_BALANCE = 0;

    /**
     * 注册网络通道与消息（幂等）
     */
    public static void init() {
        if (initialized) return;
        channel = NetworkRegistry.INSTANCE.newSimpleChannel(CHANNEL_NAME);
        channel.registerMessage(WalletBalancePacket.Handler.class, WalletBalancePacket.class, ID_BALANCE, Side.CLIENT);
        initialized = true;
    }

    public static boolean isInitialized() {
        return initialized && channel != null;
    }

    // ==================== 服务端发送 ====================

    /**
     * 服务端：向指定玩家推送钱包余额轻量包（只带余额，不带其他维度）
     *
     * @param player   目标玩家
     * @param balances 钱包余额（currencyId → 数量）
     */
    public static void sendBalanceToClient(EntityPlayerMP player, Map<String, Integer> balances) {
        if (!initialized || channel == null || player == null) return;
        channel.sendTo(new WalletBalancePacket(balances), player);
    }
}

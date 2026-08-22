package com.miaokatze.gtit.lottery;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.relauncher.Side;

/**
 * 抽奖网络包管理器
 * <p>
 * 管理抽奖相关的客户端-服务端通信（FML SimpleNetworkWrapper 模式，
 * 与 {@code SignInNetworkManager} 同范式）：
 * <ul>
 * <li>{@link LotterySyncPacket}（id=0，S→C）：登录/打开抽奖页/抽奖完成后的全量数据推送
 * （卡池摘要 + 团队保底计数）</li>
 * <li>{@link LotteryRequestPacket}（id=1，C→S）：客户端点击「抽 1 次 / 抽 10 次」发起请求
 * （携带卡池 ID、连抽数、触发机器坐标）</li>
 * <li>{@link LotteryResultPacket}（id=2，S→C）：本次抽取结果下发（停格索引/稀有度/保底标记，
 * 驱动客户端轮盘动画）</li>
 * <li>{@link LotteryBalancePacket}（id=3，S→C）：钱包余额轻量推送（只带余额，优化建议 三.1；
 * 钱包余额变化经脏标记节流合帧后由 NekoWalletManager 冲刷下发）</li>
 * </ul>
 * 在 {@code CommonProxy.init()} 中调用 {@link #init()} 注册（双端都会执行，按 Side 注册方向）。
 */
public class LotteryNetworkManager {

    private static SimpleNetworkWrapper channel;
    private static final String CHANNEL_NAME = "gtit_lottery";
    private static boolean initialized = false;

    /** 包 ID：全量同步（服务端→客户端） */
    private static final int ID_SYNC = 0;
    /** 包 ID：抽奖请求（客户端→服务端） */
    private static final int ID_REQUEST = 1;
    /** 包 ID：抽取结果（服务端→客户端） */
    private static final int ID_RESULT = 2;
    /** 包 ID：钱包余额轻量推送（服务端→客户端，优化建议 三.1） */
    private static final int ID_BALANCE = 3;

    /**
     * 注册网络通道与消息（幂等）
     */
    public static void init() {
        if (initialized) return;
        channel = NetworkRegistry.INSTANCE.newSimpleChannel(CHANNEL_NAME);
        channel.registerMessage(LotterySyncPacket.Handler.class, LotterySyncPacket.class, ID_SYNC, Side.CLIENT);
        channel
            .registerMessage(LotteryRequestPacket.Handler.class, LotteryRequestPacket.class, ID_REQUEST, Side.SERVER);
        channel.registerMessage(LotteryResultPacket.Handler.class, LotteryResultPacket.class, ID_RESULT, Side.CLIENT);
        channel
            .registerMessage(LotteryBalancePacket.Handler.class, LotteryBalancePacket.class, ID_BALANCE, Side.CLIENT);
        initialized = true;
    }

    // ==================== 客户端发送 ====================

    /**
     * 客户端：向服务端发送抽奖请求（「抽 1 次 / 抽 10 次」按钮点击时调用）
     * <p>
     * 内部做侧检查，服务端构建 GUI 时误触不会发包。
     *
     * @param poolId 卡池 ID
     * @param count  连抽次数（1 或 10）
     * @param x      触发机器 X 坐标（物品奖品出货槽定位）
     * @param y      触发机器 Y 坐标
     * @param z      触发机器 Z 坐标
     * @param dim    触发机器维度 ID
     */
    public static void sendLotteryRequest(String poolId, int count, int x, int y, int z, int dim) {
        if (!initialized || channel == null) return;
        if (FMLCommonHandler.instance()
            .getEffectiveSide() != Side.CLIENT) return;
        channel.sendToServer(new LotteryRequestPacket(poolId, count, x, y, z, dim));
    }

    // ==================== 服务端发送 ====================

    /**
     * 服务端：向指定玩家推送抽奖全量数据（纯状态刷新）
     * <p>
     * 携带：卡池摘要列表 + 该玩家团队的保底计数快照
     * + 卡池消耗币种的团队钱包余额。
     *
     * @param player 目标玩家
     */
    public static void sendSyncToClient(EntityPlayerMP player) {
        if (!initialized || channel == null || player == null) return;
        UUID playerId = player.getUniqueID();
        UUID teamKey = LotteryManager.resolveTeamKey(playerId);

        LotteryManager manager = LotteryManager.INSTANCE;
        List<LotteryPool> pools = manager.getAllPools();
        // 保底计数按团队维度取（v1.7.9 起不再携带中奖历史）
        java.util.Map<String, Integer> pityCounters = manager.getPityCounters(teamKey);

        // 团队钱包余额（各卡池消耗币种：v1.7.6 起从 costItems 实时识别猫猫币条目，
        // 旧字段 nekoCurrencyId 一并兜底；NekoWalletManager 内部优先团队钱包，与扣费同源）
        java.util.Map<String, Integer> balances = new java.util.HashMap<>();
        com.miaokatze.gtit.trade.NekoWallet wallet = com.miaokatze.gtit.trade.NekoWalletManager.INSTANCE
            .getWallet(playerId);
        if (wallet != null) {
            for (LotteryPool pool : pools) {
                if (pool == null) continue;
                // 旧字段兜底（兼容未迁移场景）
                String legacyCid = pool.getNekoCurrencyId();
                if (legacyCid != null && !legacyCid.isEmpty() && !balances.containsKey(legacyCid)) {
                    balances.put(legacyCid, wallet.getCount(legacyCid));
                }
                // costItems 中的猫猫币条目
                for (com.miaokatze.gtit.trade.v2.NekoBigItemStack cost : pool.getCostItems()) {
                    if (cost == null || cost.getBaseStack() == null) continue;
                    String cid = com.miaokatze.gtit.trade.NekoCurrencyRegistrar.getNekoCurrencyId(cost.getBaseStack());
                    if (cid != null && !balances.containsKey(cid)) {
                        balances.put(cid, wallet.getCount(cid));
                    }
                }
            }
        }

        channel.sendTo(new LotterySyncPacket(pools, pityCounters, balances), player);
    }

    /**
     * 服务端：向指定玩家推送钱包余额轻量包（只带余额，不带卡池摘要/保底计数）。
     * <p>
     * 优化建议 三.1：钱包余额变化不再走全量 {@link #sendSyncToClient}，由
     * {@code NekoWalletManager} 脏标记节流（约 100ms 合帧）后调用本方法，消除
     * 批量投币/连抽场景对全队在线成员的 m×n 全量包风暴。
     *
     * @param player   目标玩家
     * @param balances 钱包余额（currencyId → 数量）
     */
    public static void sendBalanceToClient(EntityPlayerMP player, Map<String, Integer> balances) {
        if (!initialized || channel == null || player == null) return;
        channel.sendTo(new LotteryBalancePacket(balances), player);
    }

    /**
     * 服务端：向全体在线玩家广播抽奖全量同步（v1.7.0 目标 5）
     * <p>
     * 卡池配置全服一致，但保底计数/团队钱包余额为团队维度，
     * 载荷无法复用，故逐玩家各自构建并推送（{@link #sendSyncToClient}）。
     * 配置编辑保存与 /gtit nekovm sync all 后调用，保证全服客户端轮盘配置即时刷新。
     */
    public static void sendSyncToAll() {
        if (!initialized || channel == null) return;
        MinecraftServer server = MinecraftServer.getServer();
        if (server == null) return;
        for (EntityPlayerMP player : server.getConfigurationManager().playerEntityList) {
            sendSyncToClient(player);
        }
    }

    /**
     * 服务端：向指定玩家下发本次抽取结果（驱动客户端轮盘动画与结果展示）
     *
     * @param player     目标玩家
     * @param poolId     抽取卡池 ID
     * @param results    抽取结果列表（{@link LotteryManager#drawLottery} 产出）
     * @param resultCode 结果码（{@link LotteryClientData#RESULT_SUCCESS} 等）
     */
    public static void sendResultToClient(EntityPlayerMP player, String poolId, List<LotteryDrawResult> results,
        int resultCode) {
        if (!initialized || channel == null || player == null) return;
        channel.sendTo(new LotteryResultPacket(poolId, results, resultCode), player);
    }

    public static boolean isInitialized() {
        return initialized;
    }

    public static SimpleNetworkWrapper getChannel() {
        return channel;
    }
}

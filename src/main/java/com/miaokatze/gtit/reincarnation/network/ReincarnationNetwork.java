package com.miaokatze.gtit.reincarnation.network;

import java.util.List;

import net.minecraft.entity.player.EntityPlayerMP;

import com.miaokatze.gtit.main.GTInterestingThing;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.relauncher.Side;

/**
 * 周目系统网络包管理器（v1.9.0，FML SimpleNetworkWrapper 模式，与
 * {@code LotteryNetworkManager} 同范式，本通道全部为服务端→客户端单向包）
 * <ul>
 * <li>{@link GrantEffectPacket}（id=0，S→C）：轮回奖励发放清单下发，触发客户端
 * 「头顶螺旋降下+发光」演出（实际发放在服务端另行完成，本包只驱动 FX）</li>
 * <li>{@link AscensionStartPacket}（id=1，S→C）：飞升演出开始信号</li>
 * <li>{@link CountdownPacket}（id=2，S→C）：轮回倒计时截止时刻（绝对墙钟毫秒）</li>
 * <li>{@link ReincarnationSyncPacket}（id=3，S→C）：周目只读状态快照（GUI 展示用）</li>
 * </ul>
 * <p>
 * 侧门控（与周目物品/配方门控同口径）：物理专用服务器（{@code FMLCommonHandler.getSide() == Side.SERVER}）
 * 上周目系统整体拒绝注册，本方法直接跳过（内联判定，不建共享 gate 类）。
 * <p>
 * 注册分两步：通道本体在 {@code CommonProxy.init()} 中经 {@link #init()} 创建（双端物理侧判定）；
 * 消息 handler 在 {@code ClientProxy.init()} 中经 {@link #registerClientHandlers()} 注册——
 * 因此 reincarnation.network 包内 server 公共路径零 net.minecraft.client 引用
 * （handler 仅经 ClientProxy 注册路径可达）。
 */
public class ReincarnationNetwork {

    private static SimpleNetworkWrapper channel;
    private static final String CHANNEL_NAME = "gtit_reincarnation";
    private static boolean initialized = false;
    private static boolean clientHandlersRegistered = false;

    /** 包 ID：发放演出（服务端→客户端） */
    private static final int ID_GRANT_EFFECT = 0;
    /** 包 ID：飞升演出开始（服务端→客户端） */
    private static final int ID_ASCENSION_START = 1;
    /** 包 ID：轮回倒计时截止（服务端→客户端） */
    private static final int ID_COUNTDOWN = 2;
    /** 包 ID：周目状态快照（服务端→客户端） */
    private static final int ID_SYNC = 3;

    /**
     * 创建网络通道（幂等）
     * <p>
     * 物理专用服务器上周目系统整体拒绝注册（内联侧门控），此时返回 false 且不建通道；
     * 后续所有发包辅助方法因 {@code channel == null} 静默无操作。
     *
     * @return true = 通道已就绪（含此前已初始化）；false = 物理专用服务器门控跳过
     */
    public static boolean init() {
        if (initialized) return true;
        // 物理侧门控（与 S1 物品/配方门控同款内联判定写法，不建共享 gate 类）：
        // 物理专用服务器上周目系统整体不注册，周目内容不进专用服
        if (FMLCommonHandler.instance()
            .getSide() == Side.SERVER) {
            GTInterestingThing.LOG.info("[reincarnation] 物理专用服务器：周目系统拒绝注册（网络）");
            return false;
        }
        channel = NetworkRegistry.INSTANCE.newSimpleChannel(CHANNEL_NAME);
        // 注意：此处只创建通道，不注册消息 handler——
        // handler 注册在 ClientProxy.init() 经 registerClientHandlers() 完成
        initialized = true;
        return true;
    }

    /**
     * 注册 4 个 S→C 包的客户端 handler（幂等）
     * <p>
     * 仅由 {@code ClientProxy.init()} 调用（物理 CLIENT 路径必然可达）；
     * 物理专用服务器上 {@link #init()} 已门控跳过，本方法因通道为 null 直接返回。
     * 包内 packet/handler 类不引用任何 net.minecraft.client 类型，
     * 客户端载荷统一写入 {@link ClientReincarnationFxState}（真实 FX 渲染由消费方读取该 holder）。
     */
    public static void registerClientHandlers() {
        if (!initialized || channel == null) return;
        if (clientHandlersRegistered) return;
        channel.registerMessage(GrantEffectPacket.Handler.class, GrantEffectPacket.class, ID_GRANT_EFFECT, Side.CLIENT);
        channel.registerMessage(
            AscensionStartPacket.Handler.class,
            AscensionStartPacket.class,
            ID_ASCENSION_START,
            Side.CLIENT);
        channel.registerMessage(CountdownPacket.Handler.class, CountdownPacket.class, ID_COUNTDOWN, Side.CLIENT);
        channel.registerMessage(
            ReincarnationSyncPacket.Handler.class,
            ReincarnationSyncPacket.class,
            ID_SYNC,
            Side.CLIENT);
        clientHandlersRegistered = true;
    }

    // ==================== 服务端发送 ====================

    /**
     * 服务端：向指定玩家下发轮回奖励发放演出包（实际发放在服务端另行完成，本包只驱动客户端 FX）
     *
     * @param player 目标玩家
     * @param items  发放物品清单（id + meta，仅用于演出展示）
     */
    public static void sendGrantEffectToClient(EntityPlayerMP player, List<GrantEffectPacket.ItemRef> items) {
        if (!initialized || channel == null || player == null) return;
        channel.sendTo(new GrantEffectPacket(items), player);
    }

    /**
     * 服务端：向指定玩家下发飞升演出开始包
     *
     * @param player         目标玩家
     * @param targetEntityId 飞升实体 ID（一般为玩家自身 {@code getEntityId()}，未知传 -1）
     */
    public static void sendAscensionStartToClient(EntityPlayerMP player, int targetEntityId) {
        if (!initialized || channel == null || player == null) return;
        channel.sendTo(new AscensionStartPacket(targetEntityId), player);
    }

    /**
     * 服务端：向指定玩家下发轮回倒计时截止时刻
     *
     * @param player         目标玩家
     * @param deadlineMillis 截止时刻（绝对墙钟毫秒，{@link System#currentTimeMillis()} 口径）
     */
    public static void sendCountdownToClient(EntityPlayerMP player, long deadlineMillis) {
        if (!initialized || channel == null || player == null) return;
        channel.sendTo(new CountdownPacket(deadlineMillis), player);
    }

    /**
     * 服务端：向指定玩家下发周目只读状态快照（GUI 展示用）
     *
     * @param player            目标玩家
     * @param cycleStateOrdinal 周目状态枚举 ordinal
     * @param unlockedRows      已解锁行数
     * @param hullProgress      船体进度（约定长度 15）
     * @param unlockedColumns   已解锁列标记（约定长度 15）
     * @param pendingItemsCount 待领取发放物品数
     * @param mutextLock        是否存在未领取轮回（互斥锁位）
     */
    public static void sendSyncToClient(EntityPlayerMP player, int cycleStateOrdinal, int unlockedRows,
        int[] hullProgress, boolean[] unlockedColumns, int pendingItemsCount, boolean mutextLock) {
        if (!initialized || channel == null || player == null) return;
        channel.sendTo(
            new ReincarnationSyncPacket(
                cycleStateOrdinal,
                unlockedRows,
                hullProgress,
                unlockedColumns,
                pendingItemsCount,
                mutextLock),
            player);
    }

    public static boolean isInitialized() {
        return initialized;
    }

    public static boolean isClientHandlersRegistered() {
        return clientHandlersRegistered;
    }

    public static SimpleNetworkWrapper getChannel() {
        return channel;
    }
}

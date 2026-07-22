package com.miaokatze.gtit.signin;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.relauncher.Side;

/**
 * 签到网络包管理器
 * <p>
 * 管理签到相关的客户端-服务端通信（FML SimpleNetworkWrapper 模式）：
 * <ul>
 * <li>{@link SignInSyncPacket}（id=0，S→C）：登录/签到/跨日/管理员修改后的数据推送</li>
 * <li>{@link SignInRequestPacket}（id=1，C→S）：客户端点击签到按钮发起请求</li>
 * </ul>
 * 在 {@code CommonProxy.init()} 中调用 {@link #init()} 注册（双端都会执行，按 Side 注册方向）。
 */
public class SignInNetworkManager {

    private static SimpleNetworkWrapper channel;
    private static final String CHANNEL_NAME = "gtit_signin";
    private static boolean initialized = false;

    /** 包 ID：同步包（服务端→客户端） */
    private static final int ID_SYNC = 0;
    /** 包 ID：签到请求（客户端→服务端） */
    private static final int ID_REQUEST = 1;

    /**
     * 注册网络通道与消息（幂等）
     */
    public static void init() {
        if (initialized) return;
        channel = NetworkRegistry.INSTANCE.newSimpleChannel(CHANNEL_NAME);
        channel.registerMessage(SignInSyncPacket.Handler.class, SignInSyncPacket.class, ID_SYNC, Side.CLIENT);
        channel.registerMessage(SignInRequestPacket.Handler.class, SignInRequestPacket.class, ID_REQUEST, Side.SERVER);
        initialized = true;
    }

    /**
     * 客户端：向服务端发送签到请求（签到按钮点击时调用）
     * <p>
     * 内部做侧检查，服务端构建 GUI 时误触不会发包。
     */
    public static void sendSignInRequest() {
        if (!initialized || channel == null) return;
        if (FMLCommonHandler.instance()
            .getEffectiveSide() != Side.CLIENT) return;
        channel.sendToServer(new SignInRequestPacket());
    }

    /**
     * 服务端：向指定玩家推送签到数据（纯状态刷新，无签到结果反馈）
     */
    public static void sendSyncToClient(EntityPlayerMP player, DailySignInData data) {
        sendSyncToClient(player, data, SignInClientData.RESULT_NONE, 0, 0);
    }

    /**
     * 服务端：向指定玩家推送签到数据（可附带签到结果反馈）
     *
     * @param player     目标玩家
     * @param data       最新签到数据
     * @param result     签到结果码（{@link SignInClientData#RESULT_NONE} 表示纯刷新）
     * @param baseReward 本次基础奖励数量
     * @param tierDays   本次触发的阶梯天数（0=未触发）
     */
    public static void sendSyncToClient(EntityPlayerMP player, DailySignInData data, int result, int baseReward,
        int tierDays) {
        if (!initialized || channel == null || player == null) return;
        channel.sendTo(new SignInSyncPacket(data, DailySignInManager.getToday(), result, baseReward, tierDays), player);
    }

    /**
     * 服务端：向全体在线玩家广播签到数据 + 配置快照（v1.7.0 目标 5）
     * <p>
     * 签到数据为玩家个人维度，逐玩家取各自数据分别推送；
     * 配置快照由 {@link SignInSyncPacket} 构造时自动附带（全服一致的服务端权威值）。
     * 配置热重载（/gtit signin reload、/gtit nekovm reload）与编辑模式保存后调用。
     */
    public static void sendSyncToAll() {
        if (!initialized || channel == null) return;
        MinecraftServer server = MinecraftServer.getServer();
        if (server == null) return;
        for (EntityPlayerMP player : server.getConfigurationManager().playerEntityList) {
            DailySignInData data = DailySignInManager.INSTANCE.getSignInData(player.getUniqueID());
            sendSyncToClient(player, data);
        }
    }

    public static boolean isInitialized() {
        return initialized;
    }

    public static SimpleNetworkWrapper getChannel() {
        return channel;
    }
}

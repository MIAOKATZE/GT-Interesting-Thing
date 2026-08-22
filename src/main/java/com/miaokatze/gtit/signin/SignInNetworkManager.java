package com.miaokatze.gtit.signin;

import net.minecraft.entity.player.EntityPlayerMP;

import com.miaokatze.gtit.util.PlayerLookup;

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

    // ==================== v1.7.6 G2③：活跃页客户端请求 ====================

    /**
     * 客户端：领取在线时长奖励（每日在线页领取按钮）
     *
     * @param tierIndex 档位索引（{@link SignInClientData#getOnlineRewardTiers()} 列表序）
     */
    public static void sendClaimOnline(int tierIndex) {
        sendToServer(SignInRequestPacket.claimOnline(tierIndex));
    }

    /**
     * 客户端：设置生日（纪念日页保存按钮）
     *
     * @param monthDay 生日（MM-dd 格式，服务端权威校验）
     */
    public static void sendSetBirthday(String monthDay) {
        sendToServer(SignInRequestPacket.setBirthday(monthDay));
    }

    /**
     * 客户端：添加自定义纪念日（纪念日页添加按钮）
     *
     * @param name     纪念日名称
     * @param monthDay 日期（MM-dd 格式）
     * @param year     年份（0 表示不记年份）
     */
    public static void sendAddAnniversary(String name, String monthDay, int year) {
        sendToServer(SignInRequestPacket.addAnniversary(name, monthDay, year));
    }

    /**
     * 客户端：删除自定义纪念日（纪念日页删除按钮）
     *
     * @param index 列表索引（{@link SignInClientData#getAnniversaries()} 列表序）
     */
    public static void sendRemoveAnniversary(int index) {
        sendToServer(SignInRequestPacket.removeAnniversary(index));
    }

    /** 客户端发包统一入口（侧检查 + 空守卫；packet 为 null 时静默忽略） */
    private static void sendToServer(SignInRequestPacket packet) {
        if (packet == null || !initialized || channel == null) return;
        if (FMLCommonHandler.instance()
            .getEffectiveSide() != Side.CLIENT) return;
        channel.sendToServer(packet);
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
        // O2-12：PlayerLookup 统一遍历（服务器未启动时静默跳过）
        PlayerLookup.forEachOnlinePlayer(player -> {
            DailySignInData data = DailySignInManager.INSTANCE.getSignInData(player.getUniqueID());
            sendSyncToClient(player, data);
        });
    }

    public static boolean isInitialized() {
        return initialized;
    }

    public static SimpleNetworkWrapper getChannel() {
        return channel;
    }
}

package com.miaokatze.gtit.mail;

import net.minecraft.entity.player.EntityPlayerMP;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.relauncher.Side;

/**
 * 邮件网络包管理器
 * <p>
 * 管理邮件相关的客户端-服务端通信（FML SimpleNetworkWrapper 模式）：
 * <ul>
 * <li>{@link MailSyncPacket}（id=0，S→C）：登录/投递/已读/领取/删除后的全量数据推送</li>
 * <li>{@link MailActionPacket}（id=1，C→S）：客户端已读/领取附件/删除邮件请求</li>
 * </ul>
 * 在 {@code CommonProxy.init()} 中调用 {@link #init()} 注册（双端都会执行，按 Side 注册方向）。
 * <p>
 * 参照 {@code SignInNetworkManager} 的通道注册模式。
 */
public class MailNetworkManager {

    private static SimpleNetworkWrapper channel;
    private static final String CHANNEL_NAME = "gtit_mail";
    private static boolean initialized = false;

    /** 包 ID：同步包（服务端→客户端） */
    private static final int ID_SYNC = 0;
    /** 包 ID：操作请求（客户端→服务端） */
    private static final int ID_ACTION = 1;

    /**
     * 注册网络通道与消息（幂等）
     */
    public static void init() {
        if (initialized) return;
        channel = NetworkRegistry.INSTANCE.newSimpleChannel(CHANNEL_NAME);
        channel.registerMessage(MailSyncPacket.Handler.class, MailSyncPacket.class, ID_SYNC, Side.CLIENT);
        channel.registerMessage(MailActionPacket.Handler.class, MailActionPacket.class, ID_ACTION, Side.SERVER);
        initialized = true;
    }

    /**
     * 客户端：向服务端发送邮件操作请求（GUI 已读/领取/删除按钮点击时调用）
     * <p>
     * 内部做侧检查，服务端构建 GUI 时误触不会发包。
     *
     * @param action 操作类型（{@link MailActionPacket#ACTION_READ} 等）
     * @param mailId 目标邮件 ID
     */
    public static void sendActionToServer(int action, String mailId) {
        if (!initialized || channel == null) return;
        if (FMLCommonHandler.instance()
            .getEffectiveSide() != Side.CLIENT) return;
        if (mailId == null || mailId.isEmpty()) return;
        channel.sendToServer(new MailActionPacket(action, mailId));
    }

    /**
     * 服务端：向指定玩家推送邮箱数据（全量刷新）
     *
     * @param player 目标玩家
     * @param data   最新邮箱数据
     */
    public static void sendSyncToClient(EntityPlayerMP player, MailData data) {
        if (!initialized || channel == null || player == null) return;
        channel.sendTo(new MailSyncPacket(data), player);
    }

    public static boolean isInitialized() {
        return initialized;
    }

    public static SimpleNetworkWrapper getChannel() {
        return channel;
    }
}

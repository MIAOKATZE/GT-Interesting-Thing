package com.miaokatze.gtit.terminal;

import java.util.List;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;

import com.miaokatze.gtit.util.PlayerLookup;

import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.relauncher.Side;

/**
 * 管理终端网络包管理器
 * <p>
 * 管理终端相关客户端-服务端通信（FML SimpleNetworkWrapper 模式）：
 * <ul>
 * <li>{@link TerminalOpenPacket}（id=0，S→C）：打开终端 + 在线玩家名快照</li>
 * <li>{@link TerminalActionPacket}（id=1，C→S）：页面按钮动作请求</li>
 * <li>{@link TerminalActionResultPacket}（id=2，S→C）：动作结果回显</li>
 * <li>{@link TerminalDataPacket}（id=3，S→C）：页面数据推送</li>
 * </ul>
 * 注册范式照抄 {@code signin/SignInNetworkManager}（CHANNEL_NAME + initialized 守卫 +
 * newSimpleChannel + registerMessage）；在 {@code CommonProxy.init()} 中以同款
 * try/catch 调用 {@link #init()}（双端执行，按 Side 注册方向）。
 */
public class TerminalNetworkManager {

    private static SimpleNetworkWrapper channel;
    private static final String CHANNEL_NAME = "gtit_terminal";
    private static boolean initialized = false;

    /** 包 ID：打开包（服务端→客户端） */
    private static final int ID_OPEN = 0;
    /** 包 ID：动作请求（客户端→服务端） */
    private static final int ID_ACTION = 1;
    /** 包 ID：动作结果（服务端→客户端） */
    private static final int ID_RESULT = 2;
    /** 包 ID：数据推送（服务端→客户端） */
    private static final int ID_DATA = 3;

    /**
     * 注册网络通道与消息（幂等）
     */
    public static void init() {
        if (initialized) return;
        channel = NetworkRegistry.INSTANCE.newSimpleChannel(CHANNEL_NAME);
        channel.registerMessage(TerminalOpenPacket.Handler.class, TerminalOpenPacket.class, ID_OPEN, Side.CLIENT);
        channel.registerMessage(TerminalActionPacket.Handler.class, TerminalActionPacket.class, ID_ACTION, Side.SERVER);
        channel.registerMessage(
            TerminalActionResultPacket.Handler.class,
            TerminalActionResultPacket.class,
            ID_RESULT,
            Side.CLIENT);
        channel.registerMessage(TerminalDataPacket.Handler.class, TerminalDataPacket.class, ID_DATA, Side.CLIENT);
        initialized = true;
    }

    /**
     * 服务端：向 OP2 玩家发送打开包（含在线玩家名快照）
     * <p>
     * 仅由 {@code /gtit terminal} 的玩家分支调用（控制台不触发 S2C）；
     * 玩家有效性由 {@link TerminalActionHandler} 动作链路复核，本方法只做空守卫。
     */
    public static void sendOpen(EntityPlayerMP player) {
        if (!initialized || channel == null || player == null) return;
        List<String> names = PlayerLookup.getOnlineNames();
        channel.sendTo(new TerminalOpenPacket(names), player);
    }

    /**
     * 服务端：回发动作结果（结果回显）
     * <p>
     * 消息经 sanitize：去除颜色码与换行（防目标名等客户端输入污染单行回显区）、截断至结果包上限。
     */
    public static void sendResult(EntityPlayerMP player, int action, int status, String message) {
        if (!initialized || channel == null || player == null) return;
        channel.sendTo(new TerminalActionResultPacket(action, status, sanitizeMessage(message)), player);
    }

    /** 结果消息净化：null→""、去 § 颜色码、换行转空格、截断 500 字符 */
    private static String sanitizeMessage(String message) {
        if (message == null) return "";
        String cleaned = message.replace("\u00a7", "")
            .replace('\n', ' ');
        return cleaned.length() <= TerminalActionResultPacket.MAX_MESSAGE_LENGTH ? cleaned
            : cleaned.substring(0, TerminalActionResultPacket.MAX_MESSAGE_LENGTH);
    }

    /**
     * 服务端：推送页面数据（查询类动作完成后）
     */
    public static void sendData(EntityPlayerMP player, int dataType, NBTTagCompound payload) {
        if (!initialized || channel == null || player == null) return;
        channel.sendTo(new TerminalDataPacket(dataType, payload), player);
    }

    /**
     * 客户端：发送终端动作请求（页面按钮触发）
     * <p>
     * 权限与参数由服务端 {@link TerminalActionHandler} 五步校验链权威校验，
     * 客户端不做任何信任假设。
     */
    public static void sendAction(int action, String targetPlayer, int argInt, String text1, String text2,
        String text3) {
        if (!initialized || channel == null) return;
        channel.sendToServer(new TerminalActionPacket(action, targetPlayer, argInt, text1, text2, text3));
    }

    public static boolean isInitialized() {
        return initialized;
    }
}

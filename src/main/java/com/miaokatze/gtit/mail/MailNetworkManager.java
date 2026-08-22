package com.miaokatze.gtit.mail;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

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
 * <li>{@link MailSyncPacket}（id=0，S→C）：登录/投递/已读/领取/删除/互寄后的全量数据推送</li>
 * <li>{@link MailActionPacket}（id=1，C→S）：客户端已读/领取附件/删除/写邮件（compose）请求</li>
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
     * <p>
     * B2-05 防网络放大：入口处做 per-player 冷却限速（{@link #tryAcquireSyncSlot}），
     * 同一玩家 250ms 内的重复全量推送直接跳过——单次操作请求仅数十字节，而全量同步包
     * 为 50 封邮件全文+附件 NBT，高频重放可放大为 MB/s 级上行。
     *
     * @param player 目标玩家
     * @param data   最新邮箱数据
     */
    public static void sendSyncToClient(EntityPlayerMP player, MailData data) {
        if (!initialized || channel == null || player == null) return;
        if (!tryAcquireSyncSlot(player.getUniqueID())) return;
        channel.sendTo(new MailSyncPacket(data), player);
    }

    // ==================== B2-05 全量同步冷却 ====================

    /** per-player 全量同步冷却窗口（毫秒）：窗口内重复全量推送跳过（防高频重放放大） */
    private static final long SYNC_COOLDOWN_MS = 250L;

    /** 玩家 → 最近一次全量同步时间戳（条目数=历史活跃玩家数，量级可忽略） */
    private static final Map<UUID, Long> lastFullSyncMs = new ConcurrentHashMap<>();

    /**
     * B2-05：同一玩家 {@link #SYNC_COOLDOWN_MS} 内只放行一次全量同步。
     * <p>
     * 冷却窗从上一次<b>实际放行</b>的同步起算——成功操作后的首次调用不拦，
     * 只拦其后的高频重复；登录首推与正常单次操作均在冷却窗外，不受影响。
     */
    private static boolean tryAcquireSyncSlot(UUID playerId) {
        long now = System.currentTimeMillis();
        Long last = lastFullSyncMs.get(playerId);
        if (last != null && now - last < SYNC_COOLDOWN_MS) return false;
        lastFullSyncMs.put(playerId, now);
        return true;
    }

    /**
     * 客户端：向服务端发送写邮件请求（v1.7.6 G2② 写邮件页面发送按钮点击时调用）
     * <p>
     * 内部做侧检查，服务端构建 GUI 时误触不会发包。
     * 字段长度由服务端二次限长兜底（{@link MailActionPacket#MAX_RECIPIENT_LENGTH} 等），
     * 此处仅做 null 安全处理。
     *
     * @param recipientName 收件人名
     * @param title         标题（可为空，服务端有默认标题兜底）
     * @param content       正文（可含 \n 换行）
     * @param x/y/z/dim     触发机器坐标与维度（附件=机器输入槽物品定位）
     */
    public static void sendComposeToServer(String recipientName, String title, String content, int x, int y, int z,
        int dim) {
        if (!initialized || channel == null) return;
        if (FMLCommonHandler.instance()
            .getEffectiveSide() != Side.CLIENT) return;
        channel.sendToServer(new MailActionPacket(recipientName, title, content, x, y, z, dim));
    }

    public static boolean isInitialized() {
        return initialized;
    }

    public static SimpleNetworkWrapper getChannel() {
        return channel;
    }
}

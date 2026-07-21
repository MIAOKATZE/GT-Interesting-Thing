package com.miaokatze.gtit.mail;

import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/**
 * 邮件操作请求包（客户端→服务端）
 * <p>
 * 携带操作类型（{@link #ACTION_READ} / {@link #ACTION_CLAIM} / {@link #ACTION_DELETE}）
 * 与目标邮件 ID。服务端收到后经 {@link MailHandler#scheduleServerTask} 投递到服务器
 * 主线程执行（1.7.10 的包处理器运行在 Netty 线程，不能直接操作背包/文件）。
 * <p>
 * 操作完成后服务端回发 {@link MailSyncPacket} 全量刷新客户端邮箱。
 */
public class MailActionPacket implements IMessage {

    /** 操作：标记已读 */
    public static final int ACTION_READ = 0;
    /** 操作：领取附件 */
    public static final int ACTION_CLAIM = 1;
    /** 操作：删除邮件 */
    public static final int ACTION_DELETE = 2;

    /** 操作类型（{@link #ACTION_READ}/{@link #ACTION_CLAIM}/{@link #ACTION_DELETE}） */
    private int action;
    /** 目标邮件 ID */
    private String mailId = "";

    public MailActionPacket() {
        // 反序列化需要无参构造
    }

    public MailActionPacket(int action, String mailId) {
        this.action = action;
        this.mailId = mailId == null ? "" : mailId;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.action = buf.readInt();
        this.mailId = ByteBufUtils.readUTF8String(buf);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(this.action);
        ByteBufUtils.writeUTF8String(buf, this.mailId == null ? "" : this.mailId);
    }

    public int getAction() {
        return action;
    }

    public String getMailId() {
        return mailId;
    }

    public static class Handler implements IMessageHandler<MailActionPacket, IMessage> {

        @Override
        public IMessage onMessage(MailActionPacket message, MessageContext ctx) {
            final EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            if (player == null) return null;
            // 切到服务器主线程执行（涉及背包写入、NBT 持久化）
            MailHandler.scheduleServerTask(() -> processAction(player, message));
            return null;
        }

        /**
         * 服务器主线程：执行邮件操作、聊天反馈、回发最新数据
         */
        private void processAction(EntityPlayerMP player, MailActionPacket message) {
            UUID playerId = player.getUniqueID();
            MailManager manager = MailManager.INSTANCE;
            String mailId = message.getMailId();

            switch (message.getAction()) {
                case ACTION_READ -> {
                    // 已读为静默操作（点开详情即触发），不打扰聊天栏
                    manager.markRead(playerId, mailId);
                }
                case ACTION_CLAIM -> {
                    int granted = manager.claimAttachments(playerId, mailId, player);
                    if (granted > 0) {
                        player.addChatMessage(
                            new ChatComponentText(
                                EnumChatFormatting.GREEN + "已领取 " + granted + " 组邮件附件"));
                    } else {
                        player.addChatMessage(
                            new ChatComponentText(EnumChatFormatting.GRAY + "没有可领取的附件"));
                    }
                }
                case ACTION_DELETE -> {
                    int result = manager.deleteMail(playerId, mailId);
                    switch (result) {
                        case 0 -> player.addChatMessage(
                            new ChatComponentText(EnumChatFormatting.GRAY + "邮件已删除"));
                        case 2 -> player.addChatMessage(
                            new ChatComponentText(
                                EnumChatFormatting.YELLOW + "该邮件还有未领取的附件，无法删除"));
                        default -> {
                            // 邮件不存在（可能刚被删除），静默处理
                        }
                    }
                }
                default -> {
                    // 未知操作类型，忽略
                    return;
                }
            }

            // 回发最新数据（GUI 据此刷新列表与详情）
            MailNetworkManager.sendSyncToClient(player, manager.getMailData(playerId));
        }
    }
}

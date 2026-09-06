package com.miaokatze.gtit.terminal;

import net.minecraft.client.Minecraft;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;

/**
 * 管理终端动作结果包（服务端→客户端）
 * <p>
 * {@link TerminalActionHandler} 五步校验/Ops 处理后的结果回显：动作 + status + 消息。
 * 客户端切回主线程写 {@link TerminalClientData#setLastResult}，
 * 供 {@code TerminalGui} 顶部状态回显区（IKey.dynamic）渲染。
 */
public class TerminalActionResultPacket implements IMessage {

    /** 结果消息长度上限（与服务端发送侧 clamp 一致） */
    public static final int MAX_MESSAGE_LENGTH = 500;

    /** 回执对应的动作常量（{@link TerminalActionHandler} ACTION_*） */
    private int action;
    /** 处理结果 status（{@link TerminalActionHandler} STATUS_*） */
    private int status;
    /** 结果消息（中文，≤500 字符） */
    private String message;

    public TerminalActionResultPacket() {
        // 反序列化需要无参构造
        this.message = "";
    }

    public TerminalActionResultPacket(int action, int status, String message) {
        this.action = action;
        this.status = status;
        this.message = message == null ? ""
            : (message.length() <= MAX_MESSAGE_LENGTH ? message : message.substring(0, MAX_MESSAGE_LENGTH));
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(this.action);
        buf.writeInt(this.status);
        ByteBufUtils.writeUTF8String(
            buf,
            this.message == null ? ""
                : (this.message.length() <= MAX_MESSAGE_LENGTH ? this.message
                    : this.message.substring(0, MAX_MESSAGE_LENGTH)));
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.action = buf.readInt();
        this.status = buf.readInt();
        String msg = ByteBufUtils.readUTF8String(buf);
        this.message = msg == null ? ""
            : (msg.length() <= MAX_MESSAGE_LENGTH ? msg : msg.substring(0, MAX_MESSAGE_LENGTH));
    }

    public int getAction() {
        return action;
    }

    public int getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }

    public static class Handler implements IMessageHandler<TerminalActionResultPacket, IMessage> {

        @Override
        public IMessage onMessage(TerminalActionResultPacket message, MessageContext ctx) {
            // 本包只发往客户端；Netty 线程切回客户端主线程写缓存（GUI 渲染在主线程读取）
            if (ctx.side == Side.CLIENT) {
                handleClient(message);
            }
            return null;
        }

        @SideOnly(Side.CLIENT)
        private void handleClient(final TerminalActionResultPacket message) {
            Minecraft.getMinecraft()
                .func_152344_a(
                    () -> TerminalClientData
                        .setLastResult(message.getAction(), message.getStatus(), message.getMessage()));
        }
    }
}

package com.miaokatze.gtit.terminal;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;

import com.cleanroommc.modularui.factory.ClientGUI;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;

/**
 * 管理终端打开包（服务端→客户端）
 * <p>
 * OP2 玩家执行 {@code /gtit terminal} 后由服务端主线程发送，携带在线玩家名快照。
 * 客户端 handler 切回客户端主线程（{@code Minecraft#func_152344_a}）后：
 * 写入 {@link TerminalClientData#setOnlinePlayers} → {@code ClientGUI.open} 打开
 * {@link TerminalGui}（纯客户端 MUI2 面板，无 Container、无同步值）。
 * <p>
 * 客户端模板照抄 {@code mail/MailSyncPacket}（S2C + 主线程切换 + @SideOnly 剥离安全）。
 */
public class TerminalOpenPacket implements IMessage {

    /** 在线玩家名快照（打开时刻；目标玩家选择控件数据源） */
    private List<String> onlinePlayers;

    public TerminalOpenPacket() {
        // 反序列化需要无参构造
        this.onlinePlayers = new ArrayList<>();
    }

    public TerminalOpenPacket(List<String> onlinePlayers) {
        this.onlinePlayers = onlinePlayers == null ? new ArrayList<>() : new ArrayList<>(onlinePlayers);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        List<String> names = this.onlinePlayers == null ? new ArrayList<>() : this.onlinePlayers;
        buf.writeInt(names.size());
        for (String name : names) {
            ByteBufUtils.writeUTF8String(buf, name == null ? "" : name);
        }
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        int size = buf.readInt();
        // 防恶意巨型包：数量按在线玩家上限合理值硬截断
        if (size < 0 || size > 1000) size = 0;
        this.onlinePlayers = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            String name = ByteBufUtils.readUTF8String(buf);
            this.onlinePlayers.add(name == null ? "" : name);
        }
    }

    public List<String> getOnlinePlayers() {
        return onlinePlayers;
    }

    public static class Handler implements IMessageHandler<TerminalOpenPacket, IMessage> {

        @Override
        public IMessage onMessage(TerminalOpenPacket message, MessageContext ctx) {
            // 本包只发往客户端；1.7.10 的 onMessage 运行在 Netty 线程，
            // 需切回客户端主线程再写缓存/开 GUI（GUI 生命周期在主线程）
            if (ctx.side == Side.CLIENT) {
                handleClient(message);
            }
            return null;
        }

        @SideOnly(Side.CLIENT)
        private void handleClient(final TerminalOpenPacket message) {
            Minecraft.getMinecraft()
                .func_152344_a(() -> {
                    TerminalClientData.setOnlinePlayers(message.getOnlinePlayers());
                    ClientGUI.open(new TerminalGui());
                });
        }
    }
}

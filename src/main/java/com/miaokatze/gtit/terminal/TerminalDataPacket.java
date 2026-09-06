package com.miaokatze.gtit.terminal;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.NBTTagCompound;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;

/**
 * 管理终端数据推送包（服务端→客户端）
 * <p>
 * 查询类动作（首登模板/签到摘要/礼包列表）处理完成后由服务端推送页面数据，
 * 客户端切回主线程经 {@link TerminalClientData#applyData} 落地对应数据容器。
 * NBT 序列化参照 {@code mail/MailSyncPacket}（{@code ByteBufUtils.writeTag}）。
 */
public class TerminalDataPacket implements IMessage {

    /** 推送数据类型（{@link TerminalClientData} DATA_TYPE_*） */
    private int dataType;
    /** 数据载荷（结构由各 dataType 约定） */
    private NBTTagCompound payload;

    public TerminalDataPacket() {
        // 反序列化需要无参构造
        this.payload = new NBTTagCompound();
    }

    public TerminalDataPacket(int dataType, NBTTagCompound payload) {
        this.dataType = dataType;
        this.payload = payload != null ? payload : new NBTTagCompound();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(this.dataType);
        ByteBufUtils.writeTag(buf, this.payload == null ? new NBTTagCompound() : this.payload);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.dataType = buf.readInt();
        NBTTagCompound tag = ByteBufUtils.readTag(buf);
        this.payload = tag != null ? tag : new NBTTagCompound();
    }

    public int getDataType() {
        return dataType;
    }

    public NBTTagCompound getPayload() {
        return payload;
    }

    public static class Handler implements IMessageHandler<TerminalDataPacket, IMessage> {

        @Override
        public IMessage onMessage(TerminalDataPacket message, MessageContext ctx) {
            // 本包只发往客户端；Netty 线程切回客户端主线程写缓存（GUI 渲染在主线程读取）
            if (ctx.side == Side.CLIENT) {
                handleClient(message);
            }
            return null;
        }

        @SideOnly(Side.CLIENT)
        private void handleClient(final TerminalDataPacket message) {
            Minecraft.getMinecraft()
                .func_152344_a(() -> TerminalClientData.applyData(message.getDataType(), message.getPayload()));
        }
    }
}

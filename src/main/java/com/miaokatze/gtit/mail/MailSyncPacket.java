package com.miaokatze.gtit.mail;

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
 * 邮件数据同步包（服务端→客户端）
 * <p>
 * 携带完整的 {@link MailData}（NBT 序列化）。登录、指令投递、已读/领取/删除
 * 状态变更后均由服务端主动推送，客户端写入 {@link MailClientData} 缓存
 * 供 {@code MailGui} 渲染。
 * <p>
 * 参照 {@code SignInSyncPacket} 的 NBT 传输模式。
 */
public class MailSyncPacket implements IMessage {

    /** 邮箱数据 NBT（{@link MailData#writeToNBT()} 产物） */
    private NBTTagCompound dataTag;

    public MailSyncPacket() {
        // 反序列化需要无参构造
    }

    public MailSyncPacket(MailData data) {
        this.dataTag = data != null ? data.writeToNBT() : new NBTTagCompound();
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.dataTag = ByteBufUtils.readTag(buf);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        ByteBufUtils.writeTag(buf, this.dataTag == null ? new NBTTagCompound() : this.dataTag);
    }

    public NBTTagCompound getDataTag() {
        return dataTag;
    }

    public static class Handler implements IMessageHandler<MailSyncPacket, IMessage> {

        @Override
        public IMessage onMessage(MailSyncPacket message, MessageContext ctx) {
            // 本包只发往客户端；1.7.10 的 onMessage 运行在 Netty 线程，
            // 需切回客户端主线程再写缓存（GUI 在主线程读取）
            if (ctx.side == Side.CLIENT) {
                handleClient(message);
            }
            return null;
        }

        @SideOnly(Side.CLIENT)
        private void handleClient(final MailSyncPacket message) {
            Minecraft.getMinecraft()
                .func_152344_a(() -> {
                    MailData data = new MailData();
                    data.readFromNBT(message.getDataTag());
                    MailClientData.update(data);
                });
        }
    }
}

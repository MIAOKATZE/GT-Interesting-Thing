package com.miaokatze.gtit.reincarnation.network;

import com.miaokatze.gtit.main.GTInterestingThing;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import io.netty.buffer.ByteBuf;

/**
 * 飞升演出开始包（服务端→客户端，discriminator=1）
 * <p>
 * 服务端判定玩家进入飞升（周目结算过场）后下发；客户端置
 * {@link ClientReincarnationFxState#setAscensionActive(boolean)} = true 启动演出，
 * 演出结束条件由消费方（S8）调用同款 setter 置回 false。
 * <p>
 * 携带 {@code targetEntityId}（飞升实体 ID，一般为玩家自身；未知传 -1），
 * 供消费方定位演出锚点实体。本类（含 Handler）不引用任何 net.minecraft.client 类型，
 * 仅经 ClientProxy 注册路径可达。
 */
public class AscensionStartPacket implements IMessage {

    private int targetEntityId = -1;

    public AscensionStartPacket() {
        // 反序列化需要无参构造
    }

    /**
     * 构建飞升开始包（服务端）
     *
     * @param targetEntityId 飞升实体 ID（{@code EntityPlayer#getEntityId()}；未知传 -1）
     */
    public AscensionStartPacket(int targetEntityId) {
        this.targetEntityId = targetEntityId;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.targetEntityId = buf.readInt();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(this.targetEntityId);
    }

    public int getTargetEntityId() {
        return targetEntityId;
    }

    public static class Handler implements IMessageHandler<AscensionStartPacket, IMessage> {

        @Override
        public IMessage onMessage(AscensionStartPacket message, MessageContext ctx) {
            // 本包只发往客户端；handler 在 Netty 线程执行，仅写 volatile holder（渲染方主线程读取）
            if (ctx.side == Side.CLIENT) {
                ClientReincarnationFxState.setAscensionTargetEntityId(message.targetEntityId);
                ClientReincarnationFxState.setAscensionActive(true);
                GTInterestingThing.LOG
                    .info("[reincarnation] 收到飞升开始包：targetEntityId=" + message.targetEntityId + "，ascensionActive=true");
            }
            return null;
        }
    }
}

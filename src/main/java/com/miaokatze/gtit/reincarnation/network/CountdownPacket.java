package com.miaokatze.gtit.reincarnation.network;

import com.miaokatze.gtit.main.GTInterestingThing;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import io.netty.buffer.ByteBuf;

/**
 * 轮回倒计时包（服务端→客户端，discriminator=2）
 * <p>
 * 字段选择说明（二选一）：采用 <b>{@code long deadlineMillis}</b>（绝对墙钟截止时刻，
 * {@link System#currentTimeMillis()} 口径），弃用 {@code seconds int} 剩余秒数方案——
 * 绝对时刻对网络延迟/客户端掉线重连不敏感（重连后重发同包即可对表），
 * 客户端渲染剩余时间时自行与本地时钟求差，服务端无需周期重推。
 * <p>
 * 客户端 handler 将截止时刻落入 {@link ClientReincarnationFxState}；
 * 倒计时 UI/演出由消费方（S8）读取该 holder 驱动。
 * 本类（含 Handler）不引用任何 net.minecraft.client 类型，仅经 ClientProxy 注册路径可达。
 */
public class CountdownPacket implements IMessage {

    /** 截止时刻（绝对墙钟毫秒）；0 表示无进行中的倒计时 */
    private long deadlineMillis = 0L;

    public CountdownPacket() {
        // 反序列化需要无参构造
    }

    /**
     * 构建倒计时包（服务端）
     *
     * @param deadlineMillis 截止时刻（{@link System#currentTimeMillis()} 口径；0 = 无倒计时）
     */
    public CountdownPacket(long deadlineMillis) {
        this.deadlineMillis = deadlineMillis;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.deadlineMillis = buf.readLong();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeLong(this.deadlineMillis);
    }

    public long getDeadlineMillis() {
        return deadlineMillis;
    }

    public static class Handler implements IMessageHandler<CountdownPacket, IMessage> {

        @Override
        public IMessage onMessage(CountdownPacket message, MessageContext ctx) {
            // 本包只发往客户端；handler 在 Netty 线程执行，仅写 volatile holder（渲染方主线程读取）
            if (ctx.side == Side.CLIENT) {
                ClientReincarnationFxState.setCountdownDeadlineMillis(message.deadlineMillis);
                GTInterestingThing.LOG.info("[reincarnation] 收到倒计时包：deadlineMillis=" + message.deadlineMillis);
            }
            return null;
        }
    }
}

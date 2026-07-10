package com.miaokatze.gtit.lottery;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/**
 * 抽奖请求包（客户端→服务端）
 */
public class LotteryRequestPacket implements IMessage {

    private String poolId;
    private int count;

    public LotteryRequestPacket() {}

    @Override
    public void fromBytes(ByteBuf buf) {
        // TODO: v1.6.4 实现
    }

    @Override
    public void toBytes(ByteBuf buf) {
        // TODO: v1.6.4 实现
    }

    public String getPoolId() {
        return poolId;
    }

    public int getCount() {
        return count;
    }

    public static class Handler implements IMessageHandler<LotteryRequestPacket, IMessage> {

        @Override
        public IMessage onMessage(LotteryRequestPacket message, MessageContext ctx) {
            // TODO: v1.6.4 实现，调用 LotteryManager.INSTANCE.drawLottery()
            return null;
        }
    }
}

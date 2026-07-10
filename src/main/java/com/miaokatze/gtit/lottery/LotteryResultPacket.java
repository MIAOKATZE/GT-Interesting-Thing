package com.miaokatze.gtit.lottery;

import java.util.ArrayList;
import java.util.List;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/**
 * 抽奖结果包（服务端→客户端）
 */
public class LotteryResultPacket implements IMessage {

    private List<ResultData> results;

    public LotteryResultPacket() {
        this.results = new ArrayList<>();
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        // TODO: v1.6.4 实现
    }

    @Override
    public void toBytes(ByteBuf buf) {
        // TODO: v1.6.4 实现
    }

    public List<ResultData> getResults() {
        return results;
    }

    public static class ResultData {

        public String entryId;
        public String rarityName;
        public int amount;
        public boolean isPity;
    }

    public static class Handler implements IMessageHandler<LotteryResultPacket, IMessage> {

        @Override
        public IMessage onMessage(LotteryResultPacket message, MessageContext ctx) {
            // TODO: v1.6.4 实现，客户端显示抽奖结果
            return null;
        }
    }
}

package com.miaokatze.gtit.signin;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/**
 * 签到数据同步包（服务端→客户端）
 */
public class SignInSyncPacket implements IMessage {

    private int totalDays;
    private int consecutiveDays;
    private String lastSignInDate;
    private int monthlyCount;

    public SignInSyncPacket() {
        // 反序列化需要无参构造
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        // TODO: v1.6.3 实现
    }

    @Override
    public void toBytes(ByteBuf buf) {
        // TODO: v1.6.3 实现
    }

    public int getTotalDays() {
        return totalDays;
    }

    public int getConsecutiveDays() {
        return consecutiveDays;
    }

    public String getLastSignInDate() {
        return lastSignInDate;
    }

    public int getMonthlyCount() {
        return monthlyCount;
    }

    public static class Handler implements IMessageHandler<SignInSyncPacket, IMessage> {

        @Override
        public IMessage onMessage(SignInSyncPacket message, MessageContext ctx) {
            // TODO: v1.6.3 实现
            return null;
        }
    }
}

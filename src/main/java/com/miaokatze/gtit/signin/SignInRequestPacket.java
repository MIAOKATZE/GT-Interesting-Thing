package com.miaokatze.gtit.signin;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/**
 * 签到请求包（客户端→服务端）
 */
public class SignInRequestPacket implements IMessage {

    public SignInRequestPacket() {}

    @Override
    public void fromBytes(ByteBuf buf) {
        // TODO: v1.6.3 实现
    }

    @Override
    public void toBytes(ByteBuf buf) {
        // TODO: v1.6.3 实现
    }

    public static class Handler implements IMessageHandler<SignInRequestPacket, IMessage> {

        @Override
        public IMessage onMessage(SignInRequestPacket message, MessageContext ctx) {
            // TODO: v1.6.3 实现，调用 DailySignInManager.INSTANCE.signIn()
            return null;
        }
    }
}

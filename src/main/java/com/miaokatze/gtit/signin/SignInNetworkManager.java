package com.miaokatze.gtit.signin;

import net.minecraft.entity.player.EntityPlayerMP;

import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;

/**
 * 签到网络包管理器
 * 管理签到相关的客户端-服务端通信
 */
public class SignInNetworkManager {

    private static SimpleNetworkWrapper channel;
    private static final String CHANNEL_NAME = "gtit_signin";

    public static void init() {
        channel = NetworkRegistry.INSTANCE.newSimpleChannel(CHANNEL_NAME);
        // TODO: v1.6.3 注册消息
        // channel.registerMessage(SignInSyncPacket.Handler.class, SignInSyncPacket.class, 0, Side.CLIENT);
        // channel.registerMessage(SignInRequestPacket.Handler.class, SignInRequestPacket.class, 1, Side.SERVER);
    }

    public static void sendSignInRequest() {
        // TODO: v1.6.3 实现
    }

    public static void sendSyncToClient(EntityPlayerMP player, DailySignInData data) {
        // TODO: v1.6.3 实现
    }

    public static SimpleNetworkWrapper getChannel() {
        return channel;
    }
}

package com.miaokatze.gtit.achievement;

import java.util.Map;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;

import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;

/**
 * 成就网络包管理器
 */
public class AchievementNetwork {

    private static SimpleNetworkWrapper wrapper;
    private static boolean initialized = false;

    private static final int PACKET_PROGRESS_SYNC = 0;
    private static final int PACKET_CLAIM_REQUEST = 1;
    private static final int PACKET_NOTIFICATION = 2;

    public static void init() {
        wrapper = NetworkRegistry.INSTANCE.newSimpleChannel("gtit_achievement");
        // TODO: v1.6.5 注册消息
        // wrapper.registerMessage(AchievementSyncPacket.Handler.class, AchievementSyncPacket.class, 0, Side.CLIENT);
        // wrapper.registerMessage(AchievementClaimPacket.Handler.class, AchievementClaimPacket.class, 1, Side.SERVER);
        // wrapper.registerMessage(AchievementNotificationPacket.Handler.class, AchievementNotificationPacket.class, 2,
        // Side.CLIENT);
        initialized = true;
    }

    public static boolean isInitialized() {
        return initialized;
    }

    public static void sendProgressSync(EntityPlayerMP player, Map<String, AchievementProgress> progressMap) {
        // TODO: v1.6.5 实现
    }

    public static void sendCompletionNotification(UUID playerId, Achievement ach) {
        // TODO: v1.6.5 实现
    }

    public static SimpleNetworkWrapper getWrapper() {
        return wrapper;
    }
}

package com.miaokatze.gtit.achievement;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/**
 * 成就奖励领取请求包（客户端→服务端）
 */
public class AchievementClaimPacket implements IMessage {

    private String achievementId;

    public AchievementClaimPacket() {}

    public AchievementClaimPacket(String achievementId) {
        this.achievementId = achievementId;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        // TODO: v1.6.5 实现
    }

    @Override
    public void toBytes(ByteBuf buf) {
        // TODO: v1.6.5 实现
    }

    public String getAchievementId() {
        return achievementId;
    }

    public static class Handler implements IMessageHandler<AchievementClaimPacket, IMessage> {

        @Override
        public IMessage onMessage(AchievementClaimPacket message, MessageContext ctx) {
            // TODO: v1.6.5 实现，调用 AchievementManager.INSTANCE.claimReward()
            return null;
        }
    }
}

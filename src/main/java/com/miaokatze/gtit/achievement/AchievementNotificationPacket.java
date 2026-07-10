package com.miaokatze.gtit.achievement;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/**
 * 成就完成通知包（服务端→客户端）
 */
public class AchievementNotificationPacket implements IMessage {

    private String achievementId;
    private String name;
    private String description;
    private String rewardCurrency;
    private int rewardAmount;

    public AchievementNotificationPacket() {}

    public AchievementNotificationPacket(String achievementId, String name, String description, String rewardCurrency,
        int rewardAmount) {
        this.achievementId = achievementId;
        this.name = name;
        this.description = description;
        this.rewardCurrency = rewardCurrency;
        this.rewardAmount = rewardAmount;
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

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getRewardCurrency() {
        return rewardCurrency;
    }

    public int getRewardAmount() {
        return rewardAmount;
    }

    public static class Handler implements IMessageHandler<AchievementNotificationPacket, IMessage> {

        @Override
        public IMessage onMessage(AchievementNotificationPacket message, MessageContext ctx) {
            // TODO: v1.6.5 实现，客户端显示成就完成通知
            return null;
        }
    }
}

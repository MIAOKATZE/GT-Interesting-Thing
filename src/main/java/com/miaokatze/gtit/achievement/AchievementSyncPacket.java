package com.miaokatze.gtit.achievement;

import java.util.Map;

import net.minecraft.nbt.NBTTagCompound;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/**
 * 成就进度同步包（服务端→客户端）
 */
public class AchievementSyncPacket implements IMessage {

    private NBTTagCompound progressNbt;

    public AchievementSyncPacket() {}

    public AchievementSyncPacket(Map<String, AchievementProgress> progressMap) {
        // TODO: v1.6.5 实现
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        // TODO: v1.6.5 实现
    }

    @Override
    public void toBytes(ByteBuf buf) {
        // TODO: v1.6.5 实现
    }

    public NBTTagCompound getProgressNbt() {
        return progressNbt;
    }

    public static class Handler implements IMessageHandler<AchievementSyncPacket, IMessage> {

        @Override
        public IMessage onMessage(AchievementSyncPacket message, MessageContext ctx) {
            // TODO: v1.6.5 实现
            return null;
        }
    }
}

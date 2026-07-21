package com.miaokatze.gtit.signin;

import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;

import com.miaokatze.gtit.signin.DailySignInManager.SignInResult;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/**
 * 签到请求包（客户端→服务端）
 * <p>
 * 无负载：签到操作仅需玩家身份（取自发包连接）。
 * 服务端收到后经 {@link DailySignInHandler#scheduleServerTask} 投递到服务器主线程执行
 * （1.7.10 的包处理器运行在 Netty 线程，不能直接操作钱包/背包/文件）。
 */
public class SignInRequestPacket implements IMessage {

    public SignInRequestPacket() {}

    @Override
    public void fromBytes(ByteBuf buf) {
        // 无负载
    }

    @Override
    public void toBytes(ByteBuf buf) {
        // 无负载
    }

    public static class Handler implements IMessageHandler<SignInRequestPacket, IMessage> {

        @Override
        public IMessage onMessage(SignInRequestPacket message, MessageContext ctx) {
            final EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            if (player == null) return null;
            // 切到服务器主线程执行签到逻辑（涉及钱包写入、物品发放、NBT 持久化）
            DailySignInHandler.scheduleServerTask(() -> processSignIn(player));
            return null;
        }

        /**
         * 服务器主线程：执行签到、聊天反馈、回发最新数据
         */
        private void processSignIn(EntityPlayerMP player) {
            UUID playerId = player.getUniqueID();
            DailySignInManager manager = DailySignInManager.INSTANCE;
            SignInResult result = manager.signIn(playerId);

            int resultCode;
            switch (result.getStatus()) {
                case SUCCESS -> {
                    resultCode = SignInClientData.RESULT_SUCCESS;
                    StringBuilder sb = new StringBuilder();
                    sb.append(EnumChatFormatting.GREEN)
                        .append("签到成功！")
                        .append(EnumChatFormatting.YELLOW)
                        .append(" +")
                        .append(result.getBaseReward())
                        .append(" 猫猫币")
                        .append(EnumChatFormatting.GRAY)
                        .append("（连续 ")
                        .append(result.getConsecutiveDays())
                        .append(" 天）");
                    if (result.getTierReward() != null) {
                        sb.append(EnumChatFormatting.GOLD)
                            .append(" [达成")
                            .append(
                                result.getTierReward()
                                    .getRequiredDays())
                            .append("天阶梯奖励]");
                    }
                    player.addChatMessage(new ChatComponentText(sb.toString()));
                }
                case ALREADY_SIGNED -> {
                    resultCode = SignInClientData.RESULT_ALREADY_SIGNED;
                    player.addChatMessage(new ChatComponentText(EnumChatFormatting.GRAY + "今天已经签过到了"));
                }
                default -> {
                    resultCode = SignInClientData.RESULT_ERROR;
                    player.addChatMessage(new ChatComponentText(EnumChatFormatting.RED + "签到失败，请稍后再试"));
                }
            }

            // 回发最新数据（含结果反馈，GUI 据此刷新日历与提示条）
            DailySignInData data = manager.getSignInData(playerId);
            int tierDays = result.getTierReward() != null ? result.getTierReward()
                .getRequiredDays() : 0;
            SignInNetworkManager.sendSyncToClient(player, data, resultCode, result.getBaseReward(), tierDays);
        }
    }
}

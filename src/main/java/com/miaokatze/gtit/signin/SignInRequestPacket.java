package com.miaokatze.gtit.signin;

import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;

import com.miaokatze.gtit.signin.DailySignInManager.SignInResult;
import com.miaokatze.gtit.util.ServerTaskScheduler;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/**
 * 签到请求包（客户端→服务端）
 * <p>
 * v1.7.6 G2③ 由「无负载签到请求」扩展为多动作请求包（对应计划中的
 * type="sign_in"/"claim_online"/"set_birthday"/"set_anniversary"，实现上采用 int 动作码，
 * 与 {@code MailActionPacket} 同风格）：
 * <ul>
 * <li>{@link #ACTION_SIGN_IN}：每日签到（无载荷，等价旧版请求）</li>
 * <li>{@link #ACTION_CLAIM_ONLINE}：领取每日在线奖励（{@link #arg}=档位索引）</li>
 * <li>{@link #ACTION_SET_BIRTHDAY}：设置生日（{@link #payload1}="MM-dd"，空串=清除）</li>
 * <li>{@link #ACTION_ADD_ANNIVERSARY}：添加纪念日（{@link #payload1}=名称、{@link #payload2}="MM-dd"、
 * {@link #arg}=年份，0=不记年份）</li>
 * <li>{@link #ACTION_REMOVE_ANNIVERSARY}：删除纪念日（{@link #arg}=列表索引）</li>
 * </ul>
 * 服务端收到后经 {@link com.miaokatze.gtit.util.ServerTaskScheduler#scheduleServerTask} 投递到服务器主线程执行
 * （1.7.10 的包处理器运行在 Netty 线程，不能直接操作钱包/背包/文件）。
 * 所有写操作均由 {@link DailySignInManager} 服务端权威校验执行，完成后回发全量同步。
 */
public class SignInRequestPacket implements IMessage {

    /** 动作：每日签到（无载荷） */
    public static final int ACTION_SIGN_IN = 0;
    /** 动作：领取每日在线奖励（arg=档位索引） */
    public static final int ACTION_CLAIM_ONLINE = 1;
    /** 动作：设置生日（payload1="MM-dd"，空串=清除） */
    public static final int ACTION_SET_BIRTHDAY = 2;
    /** 动作：添加自定义纪念日（payload1=名称，payload2="MM-dd"，arg=年份 0=不记年份） */
    public static final int ACTION_ADD_ANNIVERSARY = 3;
    /** 动作：删除自定义纪念日（arg=列表索引） */
    public static final int ACTION_REMOVE_ANNIVERSARY = 4;

    /** 动作类型（{@link #ACTION_SIGN_IN} 等） */
    private int action = ACTION_SIGN_IN;
    /** 整型载荷（CLAIM_ONLINE=档位索引；ADD_ANNIVERSARY=年份；REMOVE_ANNIVERSARY=列表索引） */
    private int arg;
    /** 字符串载荷 1（SET_BIRTHDAY="MM-dd"；ADD_ANNIVERSARY=名称） */
    private String payload1 = "";
    /** 字符串载荷 2（ADD_ANNIVERSARY="MM-dd"） */
    private String payload2 = "";

    /**
     * 构造每日签到请求（无载荷，等价 v1.7.5 及以前的行为）
     */
    public SignInRequestPacket() {
        this.action = ACTION_SIGN_IN;
    }

    /** 私有全参构造（各动作经静态工厂创建，语义清晰） */
    private SignInRequestPacket(int action, int arg, String payload1, String payload2) {
        this.action = action;
        this.arg = arg;
        this.payload1 = payload1 == null ? "" : payload1;
        this.payload2 = payload2 == null ? "" : payload2;
    }

    /** 领取每日在线奖励请求（tierIndex=档位索引） */
    public static SignInRequestPacket claimOnline(int tierIndex) {
        return new SignInRequestPacket(ACTION_CLAIM_ONLINE, tierIndex, "", "");
    }

    /** 设置生日请求（monthDay="MM-dd"，空串=清除设置） */
    public static SignInRequestPacket setBirthday(String monthDay) {
        return new SignInRequestPacket(ACTION_SET_BIRTHDAY, 0, monthDay, "");
    }

    /** 添加纪念日请求（name=名称，monthDay="MM-dd"，year=年份 0=不记年份） */
    public static SignInRequestPacket addAnniversary(String name, String monthDay, int year) {
        return new SignInRequestPacket(ACTION_ADD_ANNIVERSARY, year, name, monthDay);
    }

    /** 删除纪念日请求（index=列表索引，客户端列表为服务端全量同步镜像） */
    public static SignInRequestPacket removeAnniversary(int index) {
        return new SignInRequestPacket(ACTION_REMOVE_ANNIVERSARY, index, "", "");
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.action = buf.readInt();
        this.arg = buf.readInt();
        this.payload1 = ByteBufUtils.readUTF8String(buf);
        this.payload2 = ByteBufUtils.readUTF8String(buf);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(this.action);
        buf.writeInt(this.arg);
        ByteBufUtils.writeUTF8String(buf, this.payload1 == null ? "" : this.payload1);
        ByteBufUtils.writeUTF8String(buf, this.payload2 == null ? "" : this.payload2);
    }

    public static class Handler implements IMessageHandler<SignInRequestPacket, IMessage> {

        @Override
        public IMessage onMessage(SignInRequestPacket message, MessageContext ctx) {
            final EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            if (player == null) return null;
            // 切到服务器主线程执行（涉及钱包写入、物品发放、NBT 持久化）
            ServerTaskScheduler.scheduleServerTask(() -> processRequest(player, message));
            return null;
        }

        /**
         * 服务器主线程：按动作类型分发处理，统一回发最新数据
         */
        private void processRequest(EntityPlayerMP player, SignInRequestPacket message) {
            switch (message.action) {
                case ACTION_SIGN_IN -> processSignIn(player);
                case ACTION_CLAIM_ONLINE -> processClaimOnline(player, message.arg);
                case ACTION_SET_BIRTHDAY -> processSetBirthday(player, message.payload1);
                case ACTION_ADD_ANNIVERSARY -> processAddAnniversary(player, message);
                case ACTION_REMOVE_ANNIVERSARY -> processRemoveAnniversary(player, message.arg);
                default -> {
                    // 未知动作类型，忽略（防伪造包）
                }
            }
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
                    // v1.7.8 任务5：累计签到阶梯达成反馈（永久每档限领一次）
                    if (result.getCumulativeTierReward() != null) {
                        sb.append(EnumChatFormatting.LIGHT_PURPLE)
                            .append(" [累计签到")
                            .append(
                                result.getCumulativeTierReward()
                                    .getRequiredDays())
                            .append("天奖励]");
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

        /**
         * 服务器主线程：领取每日在线奖励（v1.7.6 G2③）
         * <p>
         * 领取结果转聊天提示反馈玩家，并回发全量同步刷新 GUI 按钮/进度状态。
         */
        private void processClaimOnline(EntityPlayerMP player, int tierIndex) {
            UUID playerId = player.getUniqueID();
            DailySignInManager manager = DailySignInManager.INSTANCE;
            int result = manager.claimOnlineReward(playerId, tierIndex);
            switch (result) {
                case DailySignInManager.CLAIM_ONLINE_SUCCESS -> player
                    .addChatMessage(new ChatComponentText(EnumChatFormatting.GREEN + "在线奖励领取成功！"));
                case DailySignInManager.CLAIM_ONLINE_NOT_REACHED -> player
                    .addChatMessage(new ChatComponentText(EnumChatFormatting.YELLOW + "在线时长还未达到该档位"));
                case DailySignInManager.CLAIM_ONLINE_ALREADY_CLAIMED -> player
                    .addChatMessage(new ChatComponentText(EnumChatFormatting.GRAY + "该档位今日已领取过了"));
                default -> player.addChatMessage(new ChatComponentText(EnumChatFormatting.RED + "领取失败，请稍后再试"));
            }
            SignInNetworkManager.sendSyncToClient(player, manager.getSignInData(playerId));
        }

        /**
         * 服务器主线程：设置生日（v1.7.6 G2③；空串=清除设置）
         */
        private void processSetBirthday(EntityPlayerMP player, String monthDay) {
            UUID playerId = player.getUniqueID();
            DailySignInManager manager = DailySignInManager.INSTANCE;
            boolean ok = manager.setBirthday(playerId, monthDay);
            if (ok) {
                String normalized = monthDay == null ? "" : monthDay.trim();
                if (normalized.isEmpty()) {
                    player.addChatMessage(new ChatComponentText(EnumChatFormatting.GRAY + "已清除生日设置"));
                } else {
                    player.addChatMessage(
                        new ChatComponentText(EnumChatFormatting.GREEN + "生日已设置为 " + normalized + "，当天会有惊喜哦"));
                }
            } else {
                player.addChatMessage(new ChatComponentText(EnumChatFormatting.RED + "生日格式不正确（应为 月-日，如 07-23）"));
            }
            SignInNetworkManager.sendSyncToClient(player, manager.getSignInData(playerId));
        }

        /**
         * 服务器主线程：添加自定义纪念日（v1.7.6 G2③）
         */
        private void processAddAnniversary(EntityPlayerMP player, SignInRequestPacket message) {
            UUID playerId = player.getUniqueID();
            DailySignInManager manager = DailySignInManager.INSTANCE;
            int result = manager.addAnniversary(playerId, message.payload1, message.payload2, message.arg);
            switch (result) {
                case 0 -> player.addChatMessage(
                    new ChatComponentText(
                        EnumChatFormatting.GREEN + "已添加纪念日「"
                            + message.payload1.trim()
                            + "」（"
                            + message.payload2
                            + "）"));
                case 2 -> player.addChatMessage(
                    new ChatComponentText(
                        EnumChatFormatting.YELLOW + "纪念日数量已达上限（" + DailySignInManager.MAX_ANNIVERSARIES + " 条）"));
                default -> player
                    .addChatMessage(new ChatComponentText(EnumChatFormatting.RED + "纪念日设置失败：名称或日期格式不正确（日期如 07-23）"));
            }
            SignInNetworkManager.sendSyncToClient(player, manager.getSignInData(playerId));
        }

        /**
         * 服务器主线程：删除自定义纪念日（v1.7.6 G2③）
         */
        private void processRemoveAnniversary(EntityPlayerMP player, int index) {
            UUID playerId = player.getUniqueID();
            DailySignInManager manager = DailySignInManager.INSTANCE;
            if (manager.removeAnniversary(playerId, index)) {
                player.addChatMessage(new ChatComponentText(EnumChatFormatting.GRAY + "纪念日已删除"));
            }
            // 索引越界（列表已被其他端改动）时静默处理，仅回发同步刷新
            SignInNetworkManager.sendSyncToClient(player, manager.getSignInData(playerId));
        }
    }
}

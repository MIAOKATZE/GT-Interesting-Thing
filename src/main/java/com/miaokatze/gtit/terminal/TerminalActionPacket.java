package com.miaokatze.gtit.terminal;

import net.minecraft.entity.player.EntityPlayerMP;

import com.miaokatze.gtit.util.ServerTaskScheduler;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/**
 * 管理终端动作请求包（客户端→服务端）
 * <p>
 * 客户端按钮触发，携带动作 int 常量 + 目标玩家名 + 数值参数 + 三段文本参数。
 * 安全约定：
 * <ul>
 * <li>包内不传类名/反射/命令字符串，动作只走 {@link TerminalActionHandler} 的 int 常量</li>
 * <li>所有字符串 null 归一为 ""，且 toBytes/fromBytes 双侧按业务硬上限 clamp，
 * 拦截恶意超长载荷（第二道闸；服务端分发前仍有 {@link TerminalActionHandler} 限长复核）</li>
 * <li>Handler 在 Netty 线程只做 playerEntity null 守卫并投递服务器主线程
 * （模板照抄 {@code signin/SignInRequestPacket}），全部校验与业务在主线程执行</li>
 * </ul>
 */
public class TerminalActionPacket implements IMessage {

    // ==================== 参数业务硬上限（字符） ====================

    /** targetPlayer 上限（MC 玩家名 ≤16） */
    public static final int MAX_TARGET_PLAYER_LENGTH = 16;
    /** text1（标题/名）上限 */
    public static final int MAX_TEXT1_LENGTH = 60;
    /** text2（正文）上限 */
    public static final int MAX_TEXT2_LENGTH = 500;
    /** text3（奖励 ID）上限 */
    public static final int MAX_TEXT3_LENGTH = 64;

    /** 动作常量（见 {@link TerminalActionHandler} ACTION_*） */
    private int action;
    /** 目标玩家名（空串=无目标动作，如 reload/查询自身类） */
    private String targetPlayer;
    /** 数值参数（天数/档位等；语义由各动作定义） */
    private int argInt;
    /** 文本参数 1：标题/名 */
    private String text1;
    /** 文本参数 2：正文 */
    private String text2;
    /** 文本参数 3：奖励 ID */
    private String text3;

    public TerminalActionPacket() {
        // 反序列化需要无参构造
        this.targetPlayer = "";
        this.text1 = "";
        this.text2 = "";
        this.text3 = "";
    }

    public TerminalActionPacket(int action, String targetPlayer, int argInt, String text1, String text2, String text3) {
        this.action = action;
        this.targetPlayer = normalize(targetPlayer, MAX_TARGET_PLAYER_LENGTH);
        this.argInt = argInt;
        this.text1 = normalize(text1, MAX_TEXT1_LENGTH);
        this.text2 = normalize(text2, MAX_TEXT2_LENGTH);
        this.text3 = normalize(text3, MAX_TEXT3_LENGTH);
    }

    /** null 归一 "" + 超长硬截断（防恶意客户端绕过构造期约束） */
    private static String normalize(String value, int maxLength) {
        if (value == null) return "";
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(this.action);
        ByteBufUtils.writeUTF8String(buf, normalize(this.targetPlayer, MAX_TARGET_PLAYER_LENGTH));
        buf.writeInt(this.argInt);
        ByteBufUtils.writeUTF8String(buf, normalize(this.text1, MAX_TEXT1_LENGTH));
        ByteBufUtils.writeUTF8String(buf, normalize(this.text2, MAX_TEXT2_LENGTH));
        ByteBufUtils.writeUTF8String(buf, normalize(this.text3, MAX_TEXT3_LENGTH));
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.action = buf.readInt();
        this.targetPlayer = normalize(ByteBufUtils.readUTF8String(buf), MAX_TARGET_PLAYER_LENGTH);
        this.argInt = buf.readInt();
        this.text1 = normalize(ByteBufUtils.readUTF8String(buf), MAX_TEXT1_LENGTH);
        this.text2 = normalize(ByteBufUtils.readUTF8String(buf), MAX_TEXT2_LENGTH);
        this.text3 = normalize(ByteBufUtils.readUTF8String(buf), MAX_TEXT3_LENGTH);
    }

    public int getAction() {
        return action;
    }

    public String getTargetPlayer() {
        return targetPlayer;
    }

    public int getArgInt() {
        return argInt;
    }

    public String getText1() {
        return text1;
    }

    public String getText2() {
        return text2;
    }

    public String getText3() {
        return text3;
    }

    public static class Handler implements IMessageHandler<TerminalActionPacket, IMessage> {

        @Override
        public IMessage onMessage(TerminalActionPacket message, MessageContext ctx) {
            // Netty 线程：仅取 playerEntity + null 守卫，业务全部投主线程
            final EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            if (player == null) return null;
            // 切到服务器主线程执行（涉及 NBT 持久化/配置重载/邮件投递）
            ServerTaskScheduler.scheduleServerTask(() -> TerminalActionHandler.processAction(player, message));
            return null;
        }
    }
}

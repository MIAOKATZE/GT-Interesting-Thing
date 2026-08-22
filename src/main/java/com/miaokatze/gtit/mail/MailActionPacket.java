package com.miaokatze.gtit.mail;

import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.world.World;

import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularContainer;
import com.miaokatze.gtit.common.machine.v2.MTENekoVendingMachineV2;
import com.miaokatze.gtit.main.GTInterestingThing;
import com.miaokatze.gtit.util.ServerTaskScheduler;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import io.netty.buffer.ByteBuf;

/**
 * 邮件操作请求包（客户端→服务端）
 * <p>
 * 携带操作类型（{@link #ACTION_READ} / {@link #ACTION_CLAIM} / {@link #ACTION_DELETE} /
 * {@link #ACTION_COMPOSE}）与目标邮件 ID；compose 动作额外携带收件人名/标题/正文与
 * 触发机器坐标（附件来源=机器输入槽，坐标定位参照 {@code LotteryRequestPacket} 模式）。
 * 服务端收到后经 {@link com.miaokatze.gtit.util.ServerTaskScheduler#scheduleServerTask} 投递到服务器
 * 主线程执行（1.7.10 的包处理器运行在 Netty 线程，不能直接操作背包/文件）。
 * <p>
 * 操作完成后服务端回发 {@link MailSyncPacket} 全量刷新客户端邮箱。
 */
public class MailActionPacket implements IMessage {

    /** 操作：标记已读 */
    public static final int ACTION_READ = 0;
    /** 操作：领取附件 */
    public static final int ACTION_CLAIM = 1;
    /** 操作：删除邮件 */
    public static final int ACTION_DELETE = 2;
    /** 操作：玩家互寄写邮件（v1.7.6 G2②；收件人名/标题/正文 + 机器坐标） */
    public static final int ACTION_COMPOSE = 3;

    // ==================== compose 字段长度上限（防恶意包刷爆存储/网络） ====================

    /** 收件人名上限（MC 玩家名最长 16） */
    public static final int MAX_RECIPIENT_LENGTH = 16;
    /** 标题上限（指令路径标题为单行短文本，compose 对齐放宽到 60） */
    public static final int MAX_TITLE_LENGTH = 60;
    /** 正文上限（多行文本总量，超出截断） */
    public static final int MAX_CONTENT_LENGTH = 500;

    /** 操作类型（{@link #ACTION_READ}/{@link #ACTION_CLAIM}/{@link #ACTION_DELETE}/{@link #ACTION_COMPOSE}） */
    private int action;
    /** 目标邮件 ID（compose 动作不使用） */
    private String mailId = "";

    // ==================== compose 载荷（仅 ACTION_COMPOSE 有意义） ====================

    /** 收件人名 */
    private String recipientName = "";
    /** 标题 */
    private String title = "";
    /** 正文（可含 \n 换行） */
    private String content = "";
    /** 触发机器坐标（附件来源=机器输入槽定位） */
    private int machineX;
    private int machineY;
    private int machineZ;
    /** 触发机器维度 ID */
    private int machineDim;

    // ==================== compose 附件来源机器校验（IT-BUG-03 防盗取） ====================

    /**
     * compose 附件来源机器与发送者的最大距离平方（8 格）。
     * <p>
     * 对齐 MUI2 {@code TileEntityGuiFactory.canInteractWith} 的交互距离规则
     * （{@code getSquaredDistance(player) <= 64}）与 SWN 包校验范式。
     */
    private static final double MAX_MACHINE_DISTANCE_SQ = 64.0D;

    public MailActionPacket() {
        // 反序列化需要无参构造
    }

    public MailActionPacket(int action, String mailId) {
        this.action = action;
        this.mailId = mailId == null ? "" : mailId;
    }

    /**
     * 构建 compose 请求（写邮件页面发送按钮）
     *
     * @param recipientName 收件人名
     * @param title         标题
     * @param content       正文
     * @param x/y/z/dim     触发机器坐标与维度（附件来源输入槽定位）
     */
    public MailActionPacket(String recipientName, String title, String content, int x, int y, int z, int dim) {
        this.action = ACTION_COMPOSE;
        this.recipientName = recipientName == null ? "" : recipientName;
        this.title = title == null ? "" : title;
        this.content = content == null ? "" : content;
        this.machineX = x;
        this.machineY = y;
        this.machineZ = z;
        this.machineDim = dim;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.action = buf.readInt();
        this.mailId = ByteBufUtils.readUTF8String(buf);
        this.recipientName = ByteBufUtils.readUTF8String(buf);
        this.title = ByteBufUtils.readUTF8String(buf);
        this.content = ByteBufUtils.readUTF8String(buf);
        this.machineX = buf.readInt();
        this.machineY = buf.readInt();
        this.machineZ = buf.readInt();
        this.machineDim = buf.readInt();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(this.action);
        ByteBufUtils.writeUTF8String(buf, this.mailId == null ? "" : this.mailId);
        ByteBufUtils.writeUTF8String(buf, this.recipientName == null ? "" : this.recipientName);
        ByteBufUtils.writeUTF8String(buf, this.title == null ? "" : this.title);
        ByteBufUtils.writeUTF8String(buf, this.content == null ? "" : this.content);
        buf.writeInt(this.machineX);
        buf.writeInt(this.machineY);
        buf.writeInt(this.machineZ);
        buf.writeInt(this.machineDim);
    }

    public int getAction() {
        return action;
    }

    public String getMailId() {
        return mailId;
    }

    public static class Handler implements IMessageHandler<MailActionPacket, IMessage> {

        @Override
        public IMessage onMessage(MailActionPacket message, MessageContext ctx) {
            final EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            if (player == null) return null;
            // 切到服务器主线程执行（涉及背包写入、NBT 持久化）
            ServerTaskScheduler.scheduleServerTask(() -> processAction(player, message));
            return null;
        }

        /**
         * 服务器主线程：执行邮件操作、聊天反馈、回发最新数据
         */
        private void processAction(EntityPlayerMP player, MailActionPacket message) {
            UUID playerId = player.getUniqueID();
            MailManager manager = MailManager.INSTANCE;
            String mailId = message.getMailId();
            // B2-05：记录本次操作是否实际改变了邮箱数据——幂等重放（重复已读/领取已领/
            // 删除不存在）不回发全量同步，压缩"小请求→50 封邮件全文+附件 NBT"的网络放大面
            boolean changed = false;

            switch (message.getAction()) {
                case ACTION_READ -> {
                    // 已读为静默操作（点开详情即触发），不打扰聊天栏
                    changed = manager.markRead(playerId, mailId);
                }
                case ACTION_CLAIM -> {
                    int granted = manager.claimAttachments(playerId, mailId, player);
                    changed = granted > 0;
                    if (granted > 0) {
                        player.addChatMessage(
                            new ChatComponentText(EnumChatFormatting.GREEN + "已领取 " + granted + " 组邮件附件"));
                    } else {
                        player.addChatMessage(new ChatComponentText(EnumChatFormatting.GRAY + "没有可领取的附件"));
                    }
                }
                case ACTION_DELETE -> {
                    int result = manager.deleteMail(playerId, mailId);
                    changed = result == 0;
                    switch (result) {
                        case 0 -> player.addChatMessage(new ChatComponentText(EnumChatFormatting.GRAY + "邮件已删除"));
                        case 2 -> player
                            .addChatMessage(new ChatComponentText(EnumChatFormatting.YELLOW + "该邮件还有未领取的附件，无法删除"));
                        default -> {
                            // 邮件不存在（可能刚被删除），静默处理
                        }
                    }
                }
                case ACTION_COMPOSE -> {
                    processCompose(player, message, manager);
                    // compose 后仍全量刷新（写邮件页复位依赖同步包）
                    changed = true;
                }
                default -> {
                    // 未知操作类型，忽略
                    return;
                }
            }

            // 回发最新数据（GUI 据此刷新列表与详情）；B2-05：无实际变更不回发
            if (changed) {
                MailNetworkManager.sendSyncToClient(player, manager.getMailData(playerId));
            }
        }

        /**
         * 服务器主线程：玩家互寄写邮件（v1.7.6 G2② compose 动作）
         * <p>
         * 字段限长后定位触发机器（附件=机器输入槽物品），委托
         * {@link MailManager#sendPlayerMail} 权威投递；结果码转聊天提示反馈发件人。
         * 收件人在线时由 sendMail 内部向其推送邮箱同步，无需在此处理。
         */
        private void processCompose(EntityPlayerMP player, MailActionPacket message, MailManager manager) {
            // 字段限长（防恶意包；trim 收件人名去除误输入空格）
            String recipient = clamp(message.recipientName, MailActionPacket.MAX_RECIPIENT_LENGTH).trim();
            String title = clamp(message.title, MailActionPacket.MAX_TITLE_LENGTH).trim();
            String content = clamp(message.content, MailActionPacket.MAX_CONTENT_LENGTH);
            // 标题兜底：留空时使用默认标题（与指令路径体验一致，避免空白邮件标题）
            if (title.isEmpty()) {
                title = "来自 " + player.getCommandSenderName() + " 的邮件";
            }

            MTENekoVendingMachineV2 machine = findMachine(player, message);
            int result = manager.sendPlayerMail(player, machine, recipient, title, content);
            switch (result) {
                case MailManager.COMPOSE_SUCCESS -> player
                    .addChatMessage(new ChatComponentText(EnumChatFormatting.GREEN + "邮件已寄出给 " + recipient));
                case MailManager.COMPOSE_RECIPIENT_NOT_FOUND -> player.addChatMessage(
                    new ChatComponentText(EnumChatFormatting.RED + "收件人「" + recipient + "」不存在（从未登录过本服务器）"));
                case MailManager.COMPOSE_MAILBOX_FULL -> player
                    .addChatMessage(new ChatComponentText(EnumChatFormatting.RED + "收件人邮箱已满，无法投递"));
                case MailManager.COMPOSE_EMPTY -> player
                    .addChatMessage(new ChatComponentText(EnumChatFormatting.YELLOW + "正文与附件均为空，邮件未发送"));
                case MailManager.COMPOSE_NO_RECIPIENT -> player
                    .addChatMessage(new ChatComponentText(EnumChatFormatting.YELLOW + "请填写收件人"));
                default -> player.addChatMessage(new ChatComponentText(EnumChatFormatting.RED + "邮件发送失败，请稍后再试"));
            }
        }

        /**
         * 按请求包坐标定位猫猫售货机 V2（compose 附件来源输入槽）
         * <p>
         * 坐标无效/机器不存在时返回 null（{@link MailManager#sendPlayerMail} 按无附件处理，
         * 不会扣取任何物品）。实现参照 {@code LotteryRequestPacket.Handler#findMachine}。
         * <p>
         * <b>防盗取校验（IT-BUG-03）</b>：坐标由客户端提供，命中机器前须依次通过：
         * <ol>
         * <li>同维度：请求维度必须与发送者当前世界一致（拒绝跨维度取物）</li>
         * <li>距离上限：发送者与机器距离 ≤ 8 格
         * （{@link #MAX_MACHINE_DISTANCE_SQ}，对齐 MUI2 容器交互距离与 SWN 校验范式）</li>
         * <li>GUI 会话绑定：发送者当前打开的 MUI2 容器必须是这台机器的 GUI
         * （{@link #isMachineGuiOpen}，坐标一致才视为有效会话）</li>
         * </ol>
         * 任一校验失败即返回 null（邮件按无附件发送，机器输入槽不动）。
         *
         * @return 机器实例；未找到或校验失败返回 null
         */
        private MTENekoVendingMachineV2 findMachine(EntityPlayerMP player, MailActionPacket message) {
            try {
                MinecraftServer server = MinecraftServer.getServer();
                if (server == null) return null;
                World world = server.worldServerForDimension(message.machineDim);
                if (world == null || world != player.worldObj) return null;
                // 距离上限（防恶意包隔空/跨维度指定他人机器取物）
                if (player.getDistanceSq(message.machineX + 0.5D, message.machineY + 0.5D, message.machineZ + 0.5D)
                    > MAX_MACHINE_DISTANCE_SQ) return null;
                // GUI 会话绑定（发送者必须正打开这台机器的 GUI，仅持坐标不允许取物）
                if (!isMachineGuiOpen(player, message)) return null;
                TileEntity te = world.getTileEntity(message.machineX, message.machineY, message.machineZ);
                if (te instanceof IGregTechTileEntity) {
                    if (((IGregTechTileEntity) te).getMetaTileEntity() instanceof MTENekoVendingMachineV2) {
                        return (MTENekoVendingMachineV2) ((IGregTechTileEntity) te).getMetaTileEntity();
                    }
                }
            } catch (Exception e) {
                GTInterestingThing.LOG.error("定位写邮件触发机器失败", e);
            }
            return null;
        }

        /**
         * GUI 会话绑定校验：发送者当前打开的容器是否为请求坐标机器的 MUI2 GUI
         * <p>
         * 机器 GUI 由 GT5U {@code MetaTileEntityGuiHandler.open} 以控制器 baseTE 坐标构建
         * {@code PosGuiData} 打开，{@code MailGui} 写邮件页发送按钮携带的正是同一坐标；
         * 因此服务端比对 {@code player.openContainer} 的 {@code PosGuiData} 与请求坐标，
         * 即可确认"玩家真的开着这台机器的 GUI"，未开 GUI/开着其他 GUI/坐标不符一律拒绝。
         */
        private static boolean isMachineGuiOpen(EntityPlayerMP player, MailActionPacket message) {
            if (!(player.openContainer instanceof ModularContainer container)) return false;
            if (!(container.getGuiData() instanceof PosGuiData guiData)) return false;
            return guiData.getX() == message.machineX && guiData.getY() == message.machineY
                && guiData.getZ() == message.machineZ;
        }

        /** 字符串限长截断（null 安全；maxLength 内原样返回） */
        private static String clamp(String text, int maxLength) {
            if (text == null) return "";
            return text.length() <= maxLength ? text : text.substring(0, maxLength);
        }
    }
}

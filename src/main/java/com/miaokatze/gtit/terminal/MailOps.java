package com.miaokatze.gtit.terminal;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import com.miaokatze.gtit.mail.Mail;
import com.miaokatze.gtit.mail.MailManager;
import com.miaokatze.gtit.util.PlayerResolver;

/**
 * 管理终端-邮件页服务端 processor（T2 实现）
 * <p>
 * 承接 {@link TerminalActionHandler} 分发的邮件域五个动作，语义对齐
 * {@code /gtit mail} 命令行路径（{@code GTITGiftCommand#handleMail} 簇）：
 * <ul>
 * <li>{@code ACTION_MAIL_SEND}：解析目标 UUID → 构造 TYPE_ADMIN 邮件 →
 * {@link MailManager#sendMail}（离线玩家经 usercache 解析，投递落盘）</li>
 * <li>{@code ACTION_MAIL_FIRST_SET}：覆盖首登奖励模板（对齐 {@code mail first}）</li>
 * <li>{@code ACTION_MAIL_FIRST_CLEAR}：清除首登奖励模板（对齐 {@code mail firstclear}）</li>
 * <li>{@code ACTION_MAIL_ONCE}：发布全服一次性奖励（对齐 {@code mail once}，
 * 奖励 ID 唯一防重）</li>
 * <li>{@code ACTION_MAIL_FIRST_QUERY}：查询首登模板 + {@code sendData} 推送
 * {@link TerminalClientData#DATA_TYPE_MAIL_TEMPLATE}</li>
 * </ul>
 * <b>附件信任链</b>：附件一律在服务端读管理员执行者手持物品（{@code held.copy()}
 * 深拷贝不消耗，参照 {@code GTITGiftCommand#heldAttachment}），客户端不传任何物品数据。
 * <p>
 * <b>异常约定</b>：本类不吞异常——任何 Manager 抛出的 Throwable 由
 * {@link TerminalActionHandler#processAction} 外层兜底回 INTERNAL_FAILURE（fail-closed），
 * 每个正常分支末尾恰好一次 {@link TerminalNetworkManager#sendResult}。
 * <p>
 * <b>线程</b>：仅服务器主线程执行（{@code ServerTaskScheduler} 投递保证）；
 * 权限复核/动作白名单/参数限长已由 Handler 五步校验链完成，本类不重复。
 */
public final class MailOps {

    /** 管理员邮件发件人显示名（终端路径固定「系统」，对齐模板类邮件口径） */
    private static final String SENDER_SYSTEM = "系统";

    private MailOps() {
        // 静态工具类，禁止实例化
    }

    /**
     * 邮件域动作统一入口（服务器主线程，已过 Handler 五步校验）
     *
     * @param player  发起玩家（管理员执行者，附件取其服务端手持）
     * @param message 动作请求包
     */
    public static void process(EntityPlayerMP player, TerminalActionPacket message) {
        switch (message.getAction()) {
            case TerminalActionHandler.ACTION_MAIL_SEND -> handleSend(player, message);
            case TerminalActionHandler.ACTION_MAIL_FIRST_SET -> handleFirstSet(player, message);
            case TerminalActionHandler.ACTION_MAIL_FIRST_CLEAR -> handleFirstClear(player, message);
            case TerminalActionHandler.ACTION_MAIL_ONCE -> handleOnce(player, message);
            case TerminalActionHandler.ACTION_MAIL_FIRST_QUERY -> handleFirstQuery(player, message);
            default -> TerminalNetworkManager.sendResult(
                player,
                message.getAction(),
                TerminalActionHandler.STATUS_INVALID_REQUEST,
                TerminalText.MSG_INVALID_REQUEST);
        }
    }

    // ==================== ACTION_MAIL_SEND：发送邮件 ====================

    /**
     * 发送邮件（对齐 {@code /gtit mail send}）：
     * 目标玩家名 → UUID（在线优先，离线查 usercache）→ TYPE_ADMIN 邮件投递。
     * 附件 = 执行者服务端手持（深拷贝不消耗）；邮箱满拒投。
     */
    private static void handleSend(EntityPlayerMP player, TerminalActionPacket message) {
        int action = message.getAction();
        String targetName = message.getTargetPlayer()
            .trim();
        String title = message.getText1()
            .trim();

        UUID targetId = PlayerResolver.resolvePlayerUuid(targetName);
        if (targetId == null) {
            TerminalNetworkManager.sendResult(player, action, TerminalActionHandler.STATUS_TARGET_NOT_FOUND, "目标玩家不存在");
            return;
        }
        if (title.isEmpty()) {
            TerminalNetworkManager.sendResult(player, action, TerminalActionHandler.STATUS_INVALID_REQUEST, "标题不能为空");
            return;
        }

        Mail mail = new Mail(
            title,
            normalizeContent(message.getText2()),
            SENDER_SYSTEM,
            heldAttachment(player),
            Mail.TYPE_ADMIN);
        boolean ok = MailManager.INSTANCE.sendMail(targetId, mail);
        if (ok) {
            TerminalNetworkManager
                .sendResult(player, action, TerminalActionHandler.STATUS_SUCCESS, "邮件已发送至 " + targetName);
        } else {
            TerminalNetworkManager.sendResult(
                player,
                action,
                TerminalActionHandler.STATUS_BUSINESS_FAILURE,
                "目标邮箱已满(" + MailManager.MAX_MAILS + ")");
        }
    }

    // ==================== ACTION_MAIL_FIRST_SET：设置首登奖励模板 ====================

    /**
     * 设置/覆盖首登奖励模板（对齐 {@code /gtit mail first}，MailManager:230）。
     * 附件 = 执行者服务端手持；新玩家首次登录自动投递（按玩家标记防重）。
     */
    private static void handleFirstSet(EntityPlayerMP player, TerminalActionPacket message) {
        int action = message.getAction();
        String title = message.getText1()
            .trim();
        if (title.isEmpty()) {
            TerminalNetworkManager.sendResult(player, action, TerminalActionHandler.STATUS_INVALID_REQUEST, "标题不能为空");
            return;
        }

        Mail template = new Mail(title, normalizeContent(message.getText2()), SENDER_SYSTEM, heldAttachment(player));
        MailManager.INSTANCE.setFirstRewardTemplate(template);
        TerminalNetworkManager.sendResult(player, action, TerminalActionHandler.STATUS_SUCCESS, "首登奖励模板已设置");
    }

    // ==================== ACTION_MAIL_FIRST_CLEAR：清除首登奖励模板 ====================

    /**
     * 清除首登奖励模板（对齐 {@code /gtit mail firstclear}，MailManager:240）。
     * 返回值区分「曾有模板已清除」与「原本就没有」两种成功口径。
     */
    private static void handleFirstClear(EntityPlayerMP player, TerminalActionPacket message) {
        boolean had = MailManager.INSTANCE.clearFirstRewardTemplate();
        TerminalNetworkManager.sendResult(
            player,
            message.getAction(),
            TerminalActionHandler.STATUS_SUCCESS,
            had ? "已清除首登奖励模板" : "原本没有首登奖励模板");
    }

    // ==================== ACTION_MAIL_ONCE：发布全服一次性奖励 ====================

    /**
     * 发布全服一次性奖励（对齐 {@code /gtit mail once}，MailManager:265）。
     * 奖励 ID 唯一防重；发布后在线玩家立即投递、离线玩家登录补投。
     */
    private static void handleOnce(EntityPlayerMP player, TerminalActionPacket message) {
        int action = message.getAction();
        String rewardId = message.getText3()
            .trim();
        if (rewardId.isEmpty()) {
            TerminalNetworkManager.sendResult(player, action, TerminalActionHandler.STATUS_INVALID_REQUEST, "奖励ID不能为空");
            return;
        }
        String title = message.getText1()
            .trim();
        if (title.isEmpty()) {
            TerminalNetworkManager.sendResult(player, action, TerminalActionHandler.STATUS_INVALID_REQUEST, "标题不能为空");
            return;
        }

        Mail template = new Mail(title, normalizeContent(message.getText2()), SENDER_SYSTEM, heldAttachment(player));
        boolean ok = MailManager.INSTANCE.publishOnceReward(rewardId, template);
        if (ok) {
            TerminalNetworkManager
                .sendResult(player, action, TerminalActionHandler.STATUS_SUCCESS, "全服一次性奖励已发布：" + rewardId);
        } else {
            TerminalNetworkManager
                .sendResult(player, action, TerminalActionHandler.STATUS_BUSINESS_FAILURE, "奖励ID已存在，发布失败");
        }
    }

    // ==================== ACTION_MAIL_FIRST_QUERY：查询首登奖励模板 ====================

    /**
     * 查询首登奖励模板：无论有无模板均先 {@code sendData} 推送
     * {@link TerminalClientData#DATA_TYPE_MAIL_TEMPLATE}（title/body/hasAttachment），
     * 再回 SUCCESS 结果（无模板 = 成功查询到「暂无」状态）。
     */
    private static void handleFirstQuery(EntityPlayerMP player, TerminalActionPacket message) {
        Mail template = MailManager.INSTANCE.getFirstRewardTemplate();

        NBTTagCompound nbt = new NBTTagCompound();
        if (template == null) {
            nbt.setString("title", "");
            nbt.setString("body", "");
            nbt.setBoolean("hasAttachment", false);
            TerminalNetworkManager.sendData(player, TerminalClientData.DATA_TYPE_MAIL_TEMPLATE, nbt);
            TerminalNetworkManager
                .sendResult(player, message.getAction(), TerminalActionHandler.STATUS_SUCCESS, "暂无首登奖励模板");
            return;
        }

        nbt.setString("title", clampText(template.getTitle(), TerminalActionPacket.MAX_TEXT1_LENGTH));
        nbt.setString("body", clampText(template.getContent(), TerminalActionPacket.MAX_TEXT2_LENGTH));
        nbt.setBoolean("hasAttachment", template.hasAttachments());
        TerminalNetworkManager.sendData(player, TerminalClientData.DATA_TYPE_MAIL_TEMPLATE, nbt);
        TerminalNetworkManager
            .sendResult(player, message.getAction(), TerminalActionHandler.STATUS_SUCCESS, "首登奖励模板已刷新");
    }

    // ==================== 内部辅助 ====================

    /**
     * 正文换行转义：单行输入中的字面 "\n" 转为真实换行（对齐命令版 joinMailContent 口径）
     */
    private static String normalizeContent(String content) {
        return content == null ? "" : content.replace("\\n", "\n");
    }

    /** 载荷文本 clamp（模板标题/正文推送上限，与动作包限长对齐） */
    private static String clampText(String text, int maxLength) {
        if (text == null) return "";
        return text.length() <= maxLength ? text : text.substring(0, maxLength);
    }

    /**
     * 取执行者服务端手持物品作为附件列表（空手返回空列表；深拷贝不消耗手中物品，
     * 参照 {@code GTITGiftCommand#heldAttachment} 同款范式）
     */
    private static List<ItemStack> heldAttachment(EntityPlayerMP player) {
        List<ItemStack> attachments = new ArrayList<>();
        ItemStack held = player.getHeldItem();
        if (held != null && held.getItem() != null) {
            attachments.add(held.copy());
        }
        return attachments;
    }
}

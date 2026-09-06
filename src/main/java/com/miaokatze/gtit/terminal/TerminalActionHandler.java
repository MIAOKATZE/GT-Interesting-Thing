package com.miaokatze.gtit.terminal;

import net.minecraft.entity.player.EntityPlayerMP;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * 管理终端动作处理器——核心安全类（T1 骨架）
 * <p>
 * {@link TerminalActionPacket} 经 {@code ServerTaskScheduler} 投递到服务器主线程后
 * 统一进入 {@link #processAction} 五步校验链，任一步失败即拒绝回执：
 * <ol>
 * <li>实体有效性：player null / {@code playerNetServerHandler} null（已断线）→ 静默丢弃</li>
 * <li>实时权限复核：{@code canCommandSenderUseCommand(2, "gtit")}——OP 被降权后立即
 * 失去终端能力，不复用打开时刻的权限；复核自身异常也按拒绝（fail-closed）</li>
 * <li>action 白名单：只接受本类 14 个 ACTION 常量（防伪造包探测）</li>
 * <li>参数限长复核：targetPlayer/text1/text2/text3 超上限即拒绝
 * （包序列化层已 clamp，此处为第二道闸，拦截绕过归一逻辑的恶意端）</li>
 * <li>分发：switch 全 case 分发到 {@link MailOps}/{@link SignInOps}/{@link TradeOps}/
 * {@link GiftOps}，default 一律 INVALID_REQUEST</li>
 * </ol>
 * 整体 try/catch(Throwable)：任何异常一律回 INTERNAL_FAILURE，绝不判成功（fail-closed，
 * 细节参照 {@code trade/v2/NekoEditActionHandler#validateEditRequest} 的四道闸范式）。
 * <p>
 * <b>线程安全</b>：仅服务器主线程执行（由 {@code scheduleServerTask} 保证）。
 */
public class TerminalActionHandler {

    private static final Logger LOG = LogManager.getLogger("gtit");

    private TerminalActionHandler() {
        // 静态工具类，禁止实例化
    }

    // ==================== 权限与校验常量 ====================

    /** 终端动作要求的实时权限等级（与 /gtit 的 getRequiredPermissionLevel=2 对齐） */
    public static final int REQUIRED_PERMISSION_LEVEL = 2;

    /** 复核使用的命令权限节点（与 /gtit 主命令名一致） */
    public static final String PERMISSION_NODE = "gtit";

    // ==================== 动作常量（包内只传 int，不传类名/反射/命令字符串） ====================

    /** 邮件：发送邮件 */
    public static final int ACTION_MAIL_SEND = 10;
    /** 邮件：设置首登奖励模板 */
    public static final int ACTION_MAIL_FIRST_SET = 11;
    /** 邮件：清除首登奖励模板 */
    public static final int ACTION_MAIL_FIRST_CLEAR = 12;
    /** 邮件：发布全服一次性奖励 */
    public static final int ACTION_MAIL_ONCE = 13;
    /** 邮件：查询首登奖励模板 */
    public static final int ACTION_MAIL_FIRST_QUERY = 14;

    /** 签到：查询玩家签到摘要 */
    public static final int ACTION_SIGNIN_QUERY = 20;
    /** 签到：设置连续签到天数 */
    public static final int ACTION_SIGNIN_SET_DAYS = 21;
    /** 签到：重置玩家签到数据 */
    public static final int ACTION_SIGNIN_RESET = 22;
    /** 签到：热重载签到配置 */
    public static final int ACTION_SIGNIN_RELOAD_CONFIGS = 23;

    /** 交易：热重载交易配置 */
    public static final int ACTION_TRADE_RELOAD = 30;
    /** 抽奖：热重载抽奖配置 */
    public static final int ACTION_LOTTERY_RELOAD = 31;
    /** 交易：重置交易历史与冷却 */
    public static final int ACTION_TRADE_TIME_RESET = 32;

    /** 礼包：查询已领取玩家列表 */
    public static final int ACTION_GIFT_CLAIM_LIST = 40;
    /** 礼包：重置领取状态 */
    public static final int ACTION_GIFT_CLAIM_RESET = 41;

    // ==================== status 常量 ====================

    /** 成功 */
    public static final int STATUS_SUCCESS = 0;
    /** 拒绝（权限不足/复核异常） */
    public static final int STATUS_DENIED = 1;
    /** 请求无效（未知动作/参数超限） */
    public static final int STATUS_INVALID_REQUEST = 2;
    /** 目标玩家不存在或不在线 */
    public static final int STATUS_TARGET_NOT_FOUND = 3;
    /** 业务执行失败（具体原因见 message） */
    public static final int STATUS_BUSINESS_FAILURE = 4;
    /** 内部错误（服务端已记日志；任何 Throwable 一律本值，不判成功） */
    public static final int STATUS_INTERNAL_FAILURE = 5;

    // ==================== 统一入口（服务器主线程） ====================

    /**
     * 五步校验链统一入口（服务器主线程执行）
     *
     * @param player  发起玩家（非 null 由投递方保证，此处仍复核）
     * @param message 动作请求包
     */
    public static void processAction(EntityPlayerMP player, TerminalActionPacket message) {
        try {
            // ① 实体有效性：null 或网络处理器已断开 → 静默丢弃（不回执，防断线窗口写操作）
            if (player == null || player.playerNetServerHandler == null) {
                return;
            }

            // ② 实时权限复核：OP2 被降权后立即失去终端能力；复核自身异常按拒绝（fail-closed）
            try {
                if (!player.canCommandSenderUseCommand(REQUIRED_PERMISSION_LEVEL, PERMISSION_NODE)) {
                    LOG.warn(
                        "[Terminal] 拒绝终端请求：玩家 {} 实时权限不足（需要等级 {}）",
                        player.getCommandSenderName(),
                        REQUIRED_PERMISSION_LEVEL);
                    TerminalNetworkManager
                        .sendResult(player, message.getAction(), STATUS_DENIED, TerminalText.MSG_DENIED);
                    return;
                }
            } catch (Throwable t) {
                LOG.error("[Terminal] 权限复核异常，拒绝终端请求", t);
                TerminalNetworkManager.sendResult(player, message.getAction(), STATUS_DENIED, TerminalText.MSG_DENIED);
                return;
            }

            // ③ action 白名单：只接受本类已定义的 14 个动作常量
            int action = message.getAction();
            if (!isKnownAction(action)) {
                LOG.warn("[Terminal] 拒绝未知终端动作: {}（玩家 {}）", action, player.getCommandSenderName());
                TerminalNetworkManager
                    .sendResult(player, action, STATUS_INVALID_REQUEST, TerminalText.MSG_INVALID_REQUEST);
                return;
            }

            // ④ 参数限长复核（第二道闸，详见类注释）
            if (!validateParamLengths(message)) {
                LOG.warn("[Terminal] 拒绝超长终端参数（玩家 {}，action {}）", player.getCommandSenderName(), action);
                TerminalNetworkManager
                    .sendResult(player, action, STATUS_INVALID_REQUEST, TerminalText.MSG_INVALID_REQUEST);
                return;
            }

            // ⑤ 分发到各域 Ops（全 case 齐全，default 一律 INVALID_REQUEST）
            switch (action) {
                case ACTION_MAIL_SEND, ACTION_MAIL_FIRST_SET, ACTION_MAIL_FIRST_CLEAR, ACTION_MAIL_ONCE, ACTION_MAIL_FIRST_QUERY -> MailOps
                    .process(player, message);
                case ACTION_SIGNIN_QUERY, ACTION_SIGNIN_SET_DAYS, ACTION_SIGNIN_RESET, ACTION_SIGNIN_RELOAD_CONFIGS -> SignInOps
                    .process(player, message);
                case ACTION_TRADE_RELOAD, ACTION_LOTTERY_RELOAD, ACTION_TRADE_TIME_RESET -> TradeOps
                    .process(player, message);
                case ACTION_GIFT_CLAIM_LIST, ACTION_GIFT_CLAIM_RESET -> GiftOps.process(player, message);
                default -> TerminalNetworkManager
                    .sendResult(player, action, STATUS_INVALID_REQUEST, TerminalText.MSG_INVALID_REQUEST);
            }
        } catch (Throwable t) {
            // 任何 Throwable 一律 INTERNAL_FAILURE，绝不判成功（fail-closed）
            LOG.error(
                "[Terminal] 终端动作处理异常（action {}，玩家 {}）",
                message == null ? -1 : message.getAction(),
                player == null ? "unknown" : player.getCommandSenderName(),
                t);
            TerminalNetworkManager.sendResult(
                player,
                message == null ? -1 : message.getAction(),
                STATUS_INTERNAL_FAILURE,
                TerminalText.MSG_INTERNAL_FAILURE);
        }
    }

    // ==================== 校验辅助 ====================

    /** action 白名单判定（新增动作须同步登记本方法与分发 switch） */
    private static boolean isKnownAction(int action) {
        return switch (action) {
            case ACTION_MAIL_SEND, ACTION_MAIL_FIRST_SET, ACTION_MAIL_FIRST_CLEAR, ACTION_MAIL_ONCE, ACTION_MAIL_FIRST_QUERY, ACTION_SIGNIN_QUERY, ACTION_SIGNIN_SET_DAYS, ACTION_SIGNIN_RESET, ACTION_SIGNIN_RELOAD_CONFIGS, ACTION_TRADE_RELOAD, ACTION_LOTTERY_RELOAD, ACTION_TRADE_TIME_RESET, ACTION_GIFT_CLAIM_LIST, ACTION_GIFT_CLAIM_RESET -> true;
            default -> false;
        };
    }

    /**
     * 参数限长复核（服务器权威侧）：四段字符串按业务上限校验
     */
    private static boolean validateParamLengths(TerminalActionPacket message) {
        if (message == null) return false;
        return lengthOk(message.getTargetPlayer(), TerminalActionPacket.MAX_TARGET_PLAYER_LENGTH)
            && lengthOk(message.getText1(), TerminalActionPacket.MAX_TEXT1_LENGTH)
            && lengthOk(message.getText2(), TerminalActionPacket.MAX_TEXT2_LENGTH)
            && lengthOk(message.getText3(), TerminalActionPacket.MAX_TEXT3_LENGTH);
    }

    private static boolean lengthOk(String value, int maxLength) {
        return value != null && value.length() <= maxLength;
    }

    // ==================== 展示辅助（客户端 TerminalClientData 回显引用） ====================

    /** action → 动作名（未知动作回"未知动作"；客户端结果行显示用） */
    public static String actionName(int action) {
        return switch (action) {
            case ACTION_MAIL_SEND -> TerminalText.NAME_MAIL_SEND;
            case ACTION_MAIL_FIRST_SET -> TerminalText.NAME_MAIL_FIRST_SET;
            case ACTION_MAIL_FIRST_CLEAR -> TerminalText.NAME_MAIL_FIRST_CLEAR;
            case ACTION_MAIL_ONCE -> TerminalText.NAME_MAIL_ONCE;
            case ACTION_MAIL_FIRST_QUERY -> TerminalText.NAME_MAIL_FIRST_QUERY;
            case ACTION_SIGNIN_QUERY -> TerminalText.NAME_SIGNIN_QUERY;
            case ACTION_SIGNIN_SET_DAYS -> TerminalText.NAME_SIGNIN_SET_DAYS;
            case ACTION_SIGNIN_RESET -> TerminalText.NAME_SIGNIN_RESET;
            case ACTION_SIGNIN_RELOAD_CONFIGS -> TerminalText.NAME_SIGNIN_RELOAD_CONFIGS;
            case ACTION_TRADE_RELOAD -> TerminalText.NAME_TRADE_RELOAD;
            case ACTION_LOTTERY_RELOAD -> TerminalText.NAME_LOTTERY_RELOAD;
            case ACTION_TRADE_TIME_RESET -> TerminalText.NAME_TRADE_TIME_RESET;
            case ACTION_GIFT_CLAIM_LIST -> TerminalText.NAME_GIFT_CLAIM_LIST;
            case ACTION_GIFT_CLAIM_RESET -> TerminalText.NAME_GIFT_CLAIM_RESET;
            default -> "未知动作";
        };
    }
}

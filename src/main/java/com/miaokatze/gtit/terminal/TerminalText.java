package com.miaokatze.gtit.terminal;

/**
 * 管理终端硬编码中文文案常量（T1 骨架）
 * <p>
 * 与项目 GUI 既有 148 处 {@code IKey.str} 中文硬编码先例保持一致，
 * 不走语言文件。全部为 {@code public static final String}，供命令入口、
 * 服务端处理器与客户端页面共同引用。
 * <p>
 * 后续切片新增动作时须同步登记：动作名常量 + 不可逆动作二次确认文案。
 */
public final class TerminalText {

    // ==================== 命令入口提示 ====================

    /** /gtit terminal 在控制台执行时的拒绝提示（控制台不触发 S2C 打开链路） */
    public static final String CONSOLE_UNAVAILABLE = "该命令仅限游戏内 OP2 玩家使用，控制台无法打开管理终端";

    // ==================== 通用 status 消息模板 ====================

    /** STATUS_SUCCESS 缺省回执文案 */
    public static final String MSG_SUCCESS = "操作成功";

    /** STATUS_DENIED 通用文案（权限不足） */
    public static final String MSG_DENIED = "动作被拒绝：权限不足";

    /** STATUS_INVALID_REQUEST 通用文案（未知动作/参数超限） */
    public static final String MSG_INVALID_REQUEST = "请求无效";

    /** STATUS_TARGET_NOT_FOUND 通用文案 */
    public static final String MSG_TARGET_NOT_FOUND = "目标玩家不存在或不在线";

    /** STATUS_BUSINESS_FAILURE 通用文案前缀（后接具体业务原因） */
    public static final String MSG_BUSINESS_FAILURE_PREFIX = "业务执行失败：";

    /** STATUS_INTERNAL_FAILURE 通用文案（服务端已记日志） */
    public static final String MSG_INTERNAL_FAILURE = "内部错误，详见服务端日志";

    /** T1 骨架阶段各 Ops 占位统一回执（后续切片替换为真实业务） */
    public static final String MSG_NOT_IMPLEMENTED = "该动作尚未实装";

    // ==================== 14 个动作名常量（结果回显/二次确认引用） ====================

    /** 邮件-发送邮件 */
    public static final String NAME_MAIL_SEND = "发送邮件";
    /** 邮件-设置首登奖励模板 */
    public static final String NAME_MAIL_FIRST_SET = "设置首登奖励模板";
    /** 邮件-清除首登奖励模板 */
    public static final String NAME_MAIL_FIRST_CLEAR = "清除首登奖励模板";
    /** 邮件-发布全服一次性奖励 */
    public static final String NAME_MAIL_ONCE = "发布全服一次性奖励";
    /** 邮件-查询首登模板 */
    public static final String NAME_MAIL_FIRST_QUERY = "查询首登奖励模板";

    /** 签到-查询玩家签到摘要 */
    public static final String NAME_SIGNIN_QUERY = "查询签到数据";
    /** 签到-设置连续签到天数 */
    public static final String NAME_SIGNIN_SET_DAYS = "设置连续签到天数";
    /** 签到-重置玩家签到数据 */
    public static final String NAME_SIGNIN_RESET = "重置签到数据";
    /** 签到-热重载签到配置 */
    public static final String NAME_SIGNIN_RELOAD_CONFIGS = "热重载签到配置";

    /** 交易-热重载交易配置 */
    public static final String NAME_TRADE_RELOAD = "热重载交易配置";
    /** 抽奖-热重载抽奖配置 */
    public static final String NAME_LOTTERY_RELOAD = "热重载抽奖配置";
    /** 交易-重置交易历史与冷却 */
    public static final String NAME_TRADE_TIME_RESET = "重置交易冷却";

    /** 礼包-查询已领取玩家列表 */
    public static final String NAME_GIFT_CLAIM_LIST = "查询新手礼包领取列表";
    /** 礼包-重置领取状态 */
    public static final String NAME_GIFT_CLAIM_RESET = "重置新手礼包领取状态";

    // ==================== 不可逆动作二次确认文案（含目标范围与不可逆提示） ====================

    /** MAIL_FIRST_CLEAR 二次确认 */
    public static final String CONFIRM_MAIL_FIRST_CLEAR = "确认清除首登奖励模板？清除后新玩家首次登录将不再自动收到奖励邮件（可重新设置，已发出的不撤回）。";

    /** MAIL_ONCE 二次确认 */
    public static final String CONFIRM_MAIL_ONCE = "确认发布全服一次性奖励？发布后不可撤回，全服玩家仅可领取一次，奖励ID不可重复使用。";

    /** SIGNIN_RESET 二次确认 */
    public static final String CONFIRM_SIGNIN_RESET = "确认重置目标玩家的签到数据？将清空目标玩家的连续/累计签到记录，操作不可撤销。";

    /** TRADE_TIME_RESET 二次确认 */
    public static final String CONFIRM_TRADE_TIME_RESET = "确认重置交易历史与冷却？将影响目标玩家所在整个队伍的全部交易冷却与历史。";

    /** GIFT_CLAIM_RESET 二次确认 */
    public static final String CONFIRM_GIFT_CLAIM_RESET = "确认重置目标玩家的新手礼包领取状态？重置后该玩家可重新领取新手礼包。";

    private TerminalText() {
        // 静态常量类，禁止实例化
    }
}

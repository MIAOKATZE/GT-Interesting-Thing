package com.miaokatze.gtit.terminal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumChatFormatting;

/**
 * 管理终端客户端静态缓存（T1 骨架，仅客户端写入/读取）
 * <p>
 * 三个数据来源：
 * <ul>
 * <li>{@link TerminalOpenPacket}（S2C）打开时写入在线玩家名快照</li>
 * <li>{@link TerminalActionResultPacket}（S2C）动作结果回显</li>
 * <li>{@link TerminalDataPacket}（S2C）各页数据推送（经 {@link #applyData}）</li>
 * </ul>
 * 全部字段 {@code volatile} + 不可变副本：网络线程/主线程写入、渲染线程经
 * {@code IKey.dynamic} 读取均无锁安全（与 {@code SignInClientData} 的读写约定一致）。
 * <p>
 * 供 {@code TerminalGui} 各页 {@code IKey.dynamic} 绑定读取；T1 阶段四页为占位，
 * 数据容器字段先建好供后续切片填充消费。
 */
public final class TerminalClientData {

    // ==================== 数据推送类型（与 TerminalDataPacket.dataType 对应） ====================

    /** 数据推送类型：邮件页-首登奖励模板（title/body/hasAttachment） */
    public static final int DATA_TYPE_MAIL_TEMPLATE = 0;
    /** 数据推送类型：签到页-目标玩家签到摘要（lines，'\n' 连接的多行文本） */
    public static final int DATA_TYPE_SIGNIN_SUMMARY = 1;
    /** 数据推送类型：礼包页-已领取玩家列表（lines + total） */
    public static final int DATA_TYPE_GIFT_LIST = 2;

    // ==================== 打开快照 ====================

    /** 打开时在线玩家名快照（不可变副本） */
    private static volatile List<String> onlinePlayers = Collections.emptyList();

    // ==================== 最近一次动作结果（顶部状态回显区数据源） ====================

    /** 最近一次动作常量（{@link TerminalActionHandler} ACTION_*；-1=尚无结果） */
    private static volatile int lastAction = -1;
    /** 最近一次动作 status（{@link TerminalActionHandler} STATUS_*；-1=尚无结果） */
    private static volatile int lastStatus = -1;
    /** 最近一次动作结果消息（空串=尚无结果） */
    private static volatile String lastResultMessage = "";
    /** 最近一次动作结果时间戳（毫秒；0=尚无结果），供结果行新鲜度判断 */
    private static volatile long lastResultAt = 0L;

    // ==================== 邮件页数据容器 ====================

    /** 首登奖励模板-标题（未同步为空串） */
    private static volatile String mailTemplateTitle = "";
    /** 首登奖励模板-正文（未同步为空串） */
    private static volatile String mailTemplateBody = "";
    /** 首登奖励模板-是否带附件 */
    private static volatile boolean mailTemplateHasAttachment = false;

    // ==================== 签到页数据容器 ====================

    /** 签到摘要行（不可变副本，未同步为空列表） */
    private static volatile List<String> signInSummaryLines = Collections.emptyList();

    // ==================== 礼包页数据容器 ====================

    /** 已领取新手礼包玩家名行（不可变副本，未同步为空列表） */
    private static volatile List<String> giftListLines = Collections.emptyList();
    /** 已领取总数（含离线） */
    private static volatile int giftTotalCount = 0;

    private TerminalClientData() {
        // 静态缓存类，禁止实例化
    }

    // ==================== 写入（网络包侧） ====================

    /** 打开快照写入（不可变副本；null 视为空） */
    public static void setOnlinePlayers(List<String> names) {
        onlinePlayers = copyImmutable(names);
    }

    /** 动作结果写入（顶部状态回显区数据源） */
    public static void setLastResult(int action, int status, String message) {
        lastAction = action;
        lastStatus = status;
        lastResultMessage = message == null ? "" : message;
        lastResultAt = System.currentTimeMillis();
    }

    /**
     * 数据推送落地（客户端主线程执行；未知类型静默忽略）
     * <p>
     * T1 约定：多行文本以 '\n' 连接存入单个 NBT 字符串（{@code lines} 键），
     * 由本方法拆行；后续切片扩展新 dataType 时同步登记常量与解析分支。
     */
    public static void applyData(int dataType, NBTTagCompound tag) {
        if (tag == null) return;
        switch (dataType) {
            case DATA_TYPE_MAIL_TEMPLATE -> {
                mailTemplateTitle = tag.getString("title");
                mailTemplateBody = tag.getString("body");
                mailTemplateHasAttachment = tag.getBoolean("hasAttachment");
            }
            case DATA_TYPE_SIGNIN_SUMMARY -> signInSummaryLines = splitLines(tag.getString("lines"));
            case DATA_TYPE_GIFT_LIST -> {
                giftListLines = splitLines(tag.getString("lines"));
                giftTotalCount = tag.getInteger("total");
            }
            default -> {
                // 未知数据类型，忽略（防伪造包）
            }
        }
    }

    // ==================== 读取（GUI IKey.dynamic 绑定） ====================

    /** 在线玩家名快照（不可变） */
    public static List<String> getOnlinePlayers() {
        return onlinePlayers;
    }

    public static int getLastAction() {
        return lastAction;
    }

    public static int getLastStatus() {
        return lastStatus;
    }

    public static String getLastResultMessage() {
        return lastResultMessage;
    }

    public static long getLastResultAt() {
        return lastResultAt;
    }

    /**
     * 顶部状态回显区单行文本（IKey.dynamic 数据源）：
     * 尚无结果时显示待命提示；有结果时按 status 着色（成功绿/失败红/其他黄）。
     */
    public static String getResultDisplayLine() {
        if (lastResultAt == 0L) {
            return EnumChatFormatting.YELLOW + "管理终端就绪";
        }
        return statusColor(lastStatus) + TerminalActionHandler.actionName(lastAction) + ": " + lastResultMessage;
    }

    public static String getMailTemplateTitle() {
        return mailTemplateTitle;
    }

    public static String getMailTemplateBody() {
        return mailTemplateBody;
    }

    public static boolean isMailTemplateHasAttachment() {
        return mailTemplateHasAttachment;
    }

    public static List<String> getSignInSummaryLines() {
        return signInSummaryLines;
    }

    public static List<String> getGiftListLines() {
        return giftListLines;
    }

    public static int getGiftTotalCount() {
        return giftTotalCount;
    }

    // ==================== 内部工具 ====================

    /** status → 回显颜色（成功绿 / 失败红 / 其他黄） */
    private static EnumChatFormatting statusColor(int status) {
        if (status == TerminalActionHandler.STATUS_SUCCESS) return EnumChatFormatting.GREEN;
        if (status == TerminalActionHandler.STATUS_INTERNAL_FAILURE || status == TerminalActionHandler.STATUS_DENIED
            || status == TerminalActionHandler.STATUS_INVALID_REQUEST
            || status == TerminalActionHandler.STATUS_TARGET_NOT_FOUND
            || status == TerminalActionHandler.STATUS_BUSINESS_FAILURE) return EnumChatFormatting.RED;
        return EnumChatFormatting.YELLOW;
    }

    /** null 安全不可变拷贝 */
    private static List<String> copyImmutable(List<String> source) {
        if (source == null || source.isEmpty()) return Collections.emptyList();
        return Collections.unmodifiableList(new ArrayList<>(source));
    }

    /** '\n' 连接文本拆行（空文本返回空列表；保留空行） */
    private static List<String> splitLines(String joined) {
        if (joined == null || joined.isEmpty()) return Collections.emptyList();
        List<String> lines = new ArrayList<>();
        for (String line : joined.split("\n", -1)) {
            lines.add(line);
        }
        return Collections.unmodifiableList(lines);
    }
}

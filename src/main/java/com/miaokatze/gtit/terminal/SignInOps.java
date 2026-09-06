package com.miaokatze.gtit.terminal;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;

import com.miaokatze.gtit.signin.DailySignInConfig;
import com.miaokatze.gtit.signin.DailySignInData;
import com.miaokatze.gtit.signin.DailySignInManager;
import com.miaokatze.gtit.signin.OnlineTimeConfig;
import com.miaokatze.gtit.util.PlayerLookup;

/**
 * 管理终端-签到页服务端 processor（T3 实装）
 * <p>
 * 承接 {@link TerminalActionHandler} 分发的签到域动作：
 * <ul>
 * <li>{@code ACTION_SIGNIN_QUERY}：查在线目标签到摘要 → sendData 推送 + SUCCESS 回执</li>
 * <li>{@code ACTION_SIGNIN_SET_DAYS}：设置在线目标连续签到天数（对齐
 * {@code /gtit signin admin set}，经 {@code DailySignInManager#adminSetConsecutiveDays}
 * 内部保存并向在线目标重发同步）</li>
 * <li>{@code ACTION_SIGNIN_RESET}：重置在线目标签到数据（对齐
 * {@code /gtit signin admin reset}，整份换新）</li>
 * <li>{@code ACTION_SIGNIN_RELOAD_CONFIGS}：热重载签到与每日在线配置
 * （对齐 {@code /gtit signin reload}）</li>
 * </ul>
 * <b>目标限在线</b>：与命令行 {@code func_152612_a} 在线按名查找语义一致（本页目标
 * 仅支持在线玩家），统一走 {@link PlayerLookup#getOnlinePlayerByName}；
 * 不在线回 {@code STATUS_TARGET_NOT_FOUND}。
 * <p>
 * 约定：仅服务器主线程执行（由 {@code TerminalActionPacket.Handler} 投递保证）；
 * 每个分支最后必发 {@link TerminalNetworkManager#sendResult}；异常不吞——
 * 直接上抛由 {@link TerminalActionHandler#processAction} 统一回
 * {@code STATUS_INTERNAL_FAILURE}，绝不判成功（fail-closed）。
 */
public final class SignInOps {

    /** QUERY：未指定目标玩家 */
    private static final String MSG_TARGET_REQUIRED = "请指定目标玩家";
    /** QUERY/SET_DAYS/RESET：目标不在线（本页目标限在线，语义同命令行"仅支持在线玩家"） */
    private static final String MSG_TARGET_OFFLINE = "目标玩家不在线";
    /** SET_DAYS：天数越界（0..9999） */
    private static final String MSG_DAYS_RANGE = "天数需在 0-9999 之间";
    /** SET_DAYS：数据缺失兜底（正常不可达：在线玩家 getSignInData 保证非 null） */
    private static final String MSG_DATA_MISSING = "签到数据获取失败";

    /** 天数下限 */
    private static final int DAYS_MIN = 0;
    /** 天数上限（4 位输入框上限） */
    private static final int DAYS_MAX = 9999;

    private SignInOps() {
        // 静态工具类，禁止实例化
    }

    /**
     * 签到域动作统一入口（服务器主线程，已过 Handler 五步校验链）
     *
     * @param player  发起玩家（服务器主线程）
     * @param message 动作请求包
     */
    public static void process(EntityPlayerMP player, TerminalActionPacket message) {
        switch (message.getAction()) {
            case TerminalActionHandler.ACTION_SIGNIN_QUERY -> handleQuery(player, message);
            case TerminalActionHandler.ACTION_SIGNIN_SET_DAYS -> handleSetDays(player, message);
            case TerminalActionHandler.ACTION_SIGNIN_RESET -> handleReset(player, message);
            case TerminalActionHandler.ACTION_SIGNIN_RELOAD_CONFIGS -> handleReloadConfigs(player, message);
            default -> TerminalNetworkManager.sendResult(
                player,
                message.getAction(),
                TerminalActionHandler.STATUS_INVALID_REQUEST,
                TerminalText.MSG_INVALID_REQUEST);
        }
    }

    /**
     * 查询目标签到摘要：在线查找 → 组摘要行 → sendData(DATA_TYPE_SIGNIN_SUMMARY) + SUCCESS
     */
    private static void handleQuery(EntityPlayerMP player, TerminalActionPacket message) {
        String targetName = message.getTargetPlayer();
        if (targetName == null || targetName.isEmpty()) {
            TerminalNetworkManager.sendResult(
                player,
                message.getAction(),
                TerminalActionHandler.STATUS_INVALID_REQUEST,
                MSG_TARGET_REQUIRED);
            return;
        }
        EntityPlayerMP target = PlayerLookup.getOnlinePlayerByName(targetName);
        if (target == null) {
            TerminalNetworkManager.sendResult(
                player,
                message.getAction(),
                TerminalActionHandler.STATUS_TARGET_NOT_FOUND,
                MSG_TARGET_OFFLINE);
            return;
        }
        DailySignInData data = DailySignInManager.INSTANCE.getSignInData(target.getUniqueID());
        if (data == null) {
            TerminalNetworkManager.sendResult(
                player,
                message.getAction(),
                TerminalActionHandler.STATUS_BUSINESS_FAILURE,
                MSG_DATA_MISSING);
            return;
        }

        String name = target.getCommandSenderName();
        List<String> lines = new ArrayList<>();
        lines.add("===== " + name + " 的签到摘要 =====");
        lines.add("累计签到: " + data.getTotalSignInDays() + " 天");
        lines.add("连续签到: " + data.getConsecutiveDays() + " 天");
        lines.add(
            "当月已签: " + data.getMonthlySignInDates()
                .size() + " 天");
        String last = data.getLastSignInDate();
        lines.add("上次签到: " + (last == null || last.isEmpty() ? "从未" : last));
        lines.add("今日状态: " + (data.hasSignedToday(DailySignInManager.getToday()) ? "已签到" : "未签到"));
        lines.add("今日在线: " + formatDuration(data.getOnlineSecondsToday()));
        String firstJoin = data.getFirstJoinDate();
        lines.add("首次入服: " + (firstJoin == null || firstJoin.isEmpty() ? "未知" : firstJoin));

        NBTTagCompound payload = new NBTTagCompound();
        payload.setString("lines", String.join("\n", lines));
        TerminalNetworkManager.sendData(player, TerminalClientData.DATA_TYPE_SIGNIN_SUMMARY, payload);
        TerminalNetworkManager
            .sendResult(player, message.getAction(), TerminalActionHandler.STATUS_SUCCESS, "已刷新 " + name + " 的签到摘要");
    }

    /**
     * 设置目标连续签到天数：在线查找 → 天数校验（0..9999）→ adminSetConsecutiveDays
     * （内部保存 + 向在线目标重发同步）→ SUCCESS
     */
    private static void handleSetDays(EntityPlayerMP player, TerminalActionPacket message) {
        String targetName = message.getTargetPlayer();
        if (targetName == null || targetName.isEmpty()) {
            TerminalNetworkManager.sendResult(
                player,
                message.getAction(),
                TerminalActionHandler.STATUS_INVALID_REQUEST,
                MSG_TARGET_REQUIRED);
            return;
        }
        EntityPlayerMP target = PlayerLookup.getOnlinePlayerByName(targetName);
        if (target == null) {
            TerminalNetworkManager.sendResult(
                player,
                message.getAction(),
                TerminalActionHandler.STATUS_TARGET_NOT_FOUND,
                MSG_TARGET_OFFLINE);
            return;
        }
        int days = message.getArgInt();
        if (days < DAYS_MIN || days > DAYS_MAX) {
            TerminalNetworkManager
                .sendResult(player, message.getAction(), TerminalActionHandler.STATUS_INVALID_REQUEST, MSG_DAYS_RANGE);
            return;
        }
        UUID targetId = target.getUniqueID();
        DailySignInManager.INSTANCE.adminSetConsecutiveDays(targetId, days);
        TerminalNetworkManager.sendResult(
            player,
            message.getAction(),
            TerminalActionHandler.STATUS_SUCCESS,
            "已将 " + target.getCommandSenderName() + " 的连续签到天数设为 " + days);
    }

    /**
     * 重置目标签到数据：在线查找 → adminResetData（整份换新，内部保存 + 在线重发同步）→ SUCCESS
     */
    private static void handleReset(EntityPlayerMP player, TerminalActionPacket message) {
        String targetName = message.getTargetPlayer();
        if (targetName == null || targetName.isEmpty()) {
            TerminalNetworkManager.sendResult(
                player,
                message.getAction(),
                TerminalActionHandler.STATUS_INVALID_REQUEST,
                MSG_TARGET_REQUIRED);
            return;
        }
        EntityPlayerMP target = PlayerLookup.getOnlinePlayerByName(targetName);
        if (target == null) {
            TerminalNetworkManager.sendResult(
                player,
                message.getAction(),
                TerminalActionHandler.STATUS_TARGET_NOT_FOUND,
                MSG_TARGET_OFFLINE);
            return;
        }
        DailySignInManager.INSTANCE.adminResetData(target.getUniqueID());
        TerminalNetworkManager.sendResult(
            player,
            message.getAction(),
            TerminalActionHandler.STATUS_SUCCESS,
            "已重置 " + target.getCommandSenderName() + " 的签到数据");
    }

    /**
     * 热重载签到与每日在线配置：DailySignInConfig.reload + OnlineTimeConfig.reload
     * <p>
     * 已知缺口：配置重载无客户端推送机制，已打开的玩家签到界面不自动刷新，
     * 需重新打开后生效（回执文案已提示）。
     */
    private static void handleReloadConfigs(EntityPlayerMP player, TerminalActionPacket message) {
        DailySignInConfig.reload();
        OnlineTimeConfig.reload();
        TerminalNetworkManager.sendResult(
            player,
            message.getAction(),
            TerminalActionHandler.STATUS_SUCCESS,
            "签到与在线时长配置已重载，已打开的玩家界面需重新打开后生效");
    }

    /** 秒数时长的人性化文本（口径同 {@code SignInCalendarGui#formatDuration}） */
    private static String formatDuration(int seconds) {
        if (seconds <= 0) return "0 分钟";
        int hours = seconds / 3600;
        int minutes = seconds % 3600 / 60;
        if (hours <= 0) return minutes + " 分钟";
        if (minutes == 0) return hours + " 小时";
        return hours + " 小时 " + minutes + " 分钟";
    }
}

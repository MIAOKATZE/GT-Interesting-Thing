package com.miaokatze.gtit.mail;

import java.util.List;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;

import com.miaokatze.gtit.main.GTInterestingThing;
import com.miaokatze.gtit.signin.AnniversaryEntry;
import com.miaokatze.gtit.signin.DailySignInData;
import com.miaokatze.gtit.signin.DailySignInManager;
import com.miaokatze.gtit.util.PlayerLookup;

/**
 * 自动祝福调度器（v1.7.6 G5）
 * <p>
 * 按「登录检测为主 + 跨日 tick 兜底」双挂载点触发祝福邮件：
 * <ul>
 * <li>登录：{@code MailHandler.onPlayerLoggedIn} 调用 {@link #checkAndSend(EntityPlayerMP)}</li>
 * <li>跨日：签到跨日 tick 检测到日期变化时调用 {@link #checkAllOnlinePlayers()}（覆盖挂机跨零点）</li>
 * </ul>
 * <p>
 * <b>触发条件</b>（当日满足任一即投递对应祝福）：
 * <ol>
 * <li>当日 = 玩家自配生日（{@link DailySignInData#getBirthday()}，"MM-dd"）→ 生日模板（蛋糕附件）</li>
 * <li>当日 = 玩家某自定义纪念日（{@link DailySignInData#getAnniversaries()} 的 monthDay；
 * 条目带 year 时正文附「第 N 周年」）→ 纯文本祝福（无配置附件）</li>
 * <li>当日 = 节日表日期（{@link BlessingConfig#getFestivals()} 的 month_day）→ 节日模板（食物 + 猫猫币附件）</li>
 * </ol>
 * <p>
 * <b>防重</b>：投递成功后在 {@link MailData#getClaimedBlessings()} 记录防重键——
 * 生日/节日 = {@code "类型_YYYY-MM-dd"}（按日唯一），纪念日 = {@code "anniversary_<序号>_YYYY"}（同年一次）。
 * 防重键写入邮箱 NBT（祝福属邮件域，不污染签到数据）。
 * <p>
 * <b>持久化依赖（IT-BUG-09，已知限制）</b>：本类无自身持久化（无 File/WorldSavedData），
 * "已发"防重键的落盘完全依赖 MailData——投递成功后立即
 * {@link MailManager#saveMailData}（见 {@link #trySend}），并由 MailHandler 的周期
 * saveAll/登出保存兜底；登录检测与跨日 tick 每次重查重建内存状态，幂等。
 * 崩溃窗口：若邮件已投递但 saveMailData 前服务器崩溃，重启后可能重发祝福邮件
 * （低概率重复奖励），此为当前设计取舍，不引入独立落盘。
 * <p>
 * <b>不补发口径（v1.7.6 用户确认）</b>：生日/节日当天未上线则当年无祝福，次年再来；
 * 避免长期离线玩家上线时邮箱被历年祝福刷屏。
 * <p>
 * <b>邮箱满</b>：投递失败时不写防重键，下次检测（重登/跨日）重试。
 * <p>
 * <b>线程</b>：全部方法须在服务器主线程调用（登录事件/tick 天然主线程）。
 */
public class BlessingManager {

    public static final BlessingManager INSTANCE = new BlessingManager();

    private BlessingManager() {}

    /**
     * 对单个在线玩家执行当日祝福检测并投递（登录/跨日 tick 共用入口）
     *
     * @param player 在线玩家
     * @return true 表示本次检测至少投递了一封祝福邮件（调用方可据此提示）
     */
    public boolean checkAndSend(EntityPlayerMP player) {
        if (player == null) return false;
        UUID playerId = player.getUniqueID();
        String today = DailySignInManager.getToday(); // yyyy-MM-dd
        if (today == null || today.length() < 10) return false;
        String monthDay = today.substring(5); // MM-dd
        String year = today.substring(0, 4); // yyyy

        DailySignInData signInData = DailySignInManager.INSTANCE.getSignInData(playerId);
        MailData mailData = MailManager.INSTANCE.getMailData(playerId);
        if (signInData == null || mailData == null) return false;

        boolean sentAny = false;
        String sender = BlessingConfig.getSender();

        // ---- 1. 生日（玩家自配 "MM-dd"）----
        String birthday = signInData.getBirthday();
        if (birthday != null && !birthday.isEmpty() && birthday.equals(monthDay)) {
            BlessingConfig.BirthdayBlessing template = BlessingConfig.getBirthday();
            sentAny |= trySend(
                playerId,
                mailData,
                "birthday_" + today,
                template.title,
                template.content,
                template.buildAttachments(),
                sender);
        }

        // ---- 2. 自定义纪念日（每年 MM-dd；条目带 year 时正文附「第 N 周年」）----
        int currentYear;
        try {
            currentYear = Integer.parseInt(year);
        } catch (NumberFormatException e) {
            currentYear = 0;
        }
        for (AnniversaryEntry entry : signInData.getAnniversaries()) {
            if (entry == null) continue;
            if (entry.getMonthDay() == null || entry.getMonthDay()
                .isEmpty()
                || !entry.getMonthDay()
                    .equals(monthDay)) {
                continue;
            }
            // B2-14：防重键改用 名称@月日（稳定标识）——原列表序号会因增删纪念日漂移，
            // 删除首条后其余条目序号前移，跨日 tick/重登即对同年纪念日再发一次。
            // 同名同日两条合并为一年一封（AnniversaryEntry 无稳定 id，此为最小语义取舍）
            String key = "anniversary_" + entry.getName() + "@" + entry.getMonthDay() + "_" + year;
            int yearsPassed = entry.getYear() > 0 ? currentYear - entry.getYear() : 0;
            String title = "纪念日：" + entry.getName();
            String content = yearsPassed >= 1 ? "今天是「" + entry.getName() + "」的第 " + yearsPassed + " 周年，猫猫售货机祝你纪念日快乐！"
                : "今天是「" + entry.getName() + "」，猫猫售货机祝你纪念日快乐！";
            // 纪念日无配置模板，纯文本祝福（无附件）
            sentAny |= trySend(playerId, mailData, key, title, content, null, sender);
        }

        // ---- 3. 节日表（配置固定公历日期）----
        for (BlessingConfig.FestivalBlessing festival : BlessingConfig.getFestivals()) {
            if (festival == null || festival.monthDay == null || festival.monthDay.isEmpty()) continue;
            if (!festival.monthDay.equals(monthDay)) continue;
            sentAny |= trySend(
                playerId,
                mailData,
                "festival_" + festival.name + "_" + today,
                festival.title,
                festival.content,
                festival.buildAttachments(),
                sender);
        }

        return sentAny;
    }

    /**
     * 对全部在线玩家执行当日祝福检测（跨日 tick 兜底：覆盖挂机跨零点的玩家）
     * <p>
     * 登录时已投递的祝福由防重键拦截，不会重复投递。
     */
    public void checkAllOnlinePlayers() {
        // O2-12：PlayerLookup 统一遍历，单个玩家异常不影响其余玩家
        PlayerLookup.forEachOnlinePlayer(player -> {
            try {
                checkAndSend(player);
            } catch (Throwable t) {
                GTInterestingThing.LOG.error("跨日祝福检测失败: " + player.getCommandSenderName(), t);
            }
        });
    }

    // ==================== 内部辅助 ====================

    /**
     * 构建并投递一封祝福邮件（带防重）
     * <p>
     * 防重键未记录时：构建 {@link Mail#TYPE_SYSTEM} 邮件（附件=配置物品深拷贝，
     * 猫猫币以附件物品形式）→ 走 {@link MailManager#sendMail} 现有路径
     * （在线玩家推送同步；本调度器仅对在线玩家调用，离线不投递）。
     * 投递成功后写防重键并落盘；邮箱满投递失败则不写防重键（下次检测重试）。
     *
     * @param playerId    目标玩家 UUID
     * @param mailData    目标玩家邮箱数据（已加载）
     * @param key         防重键（见类注释）
     * @param title       邮件标题
     * @param content     邮件正文
     * @param attachments 附件物品（可为 null/空 = 纯文本祝福）
     * @param sender      发件人显示名
     * @return true 表示本次实际投递了一封邮件
     */
    private boolean trySend(UUID playerId, MailData mailData, String key, String title, String content,
        List<ItemStack> attachments, String sender) {
        if (mailData.hasClaimedBlessing(key)) return false;
        Mail mail = new Mail(title, content, sender, attachments, Mail.TYPE_SYSTEM);
        if (!MailManager.INSTANCE.sendMail(playerId, mail)) {
            GTInterestingThing.LOG.warn("祝福邮件投递失败（邮箱已满）: player={}, key={}", playerId, key);
            return false;
        }
        mailData.markClaimedBlessing(key);
        // 唯一持久化联动点（IT-BUG-09）：防重键落盘依赖 MailData，崩溃窗口语义见类 javadoc
        MailManager.INSTANCE.saveMailData(playerId);
        GTInterestingThing.LOG.info("祝福邮件已投递: player={}, key={}", playerId, key);
        return true;
    }
}

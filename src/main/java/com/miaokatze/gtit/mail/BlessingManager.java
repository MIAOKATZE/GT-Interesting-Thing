package com.miaokatze.gtit.mail;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.miaokatze.gtit.mail.lunar.LunarCalendar;
import com.miaokatze.gtit.mail.lunar.SolarTerms;
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
 * <li>当日 = 节日表日期（{@link BlessingConfig#getFestivals()}：lunar 字段非空时
 * 经 {@code lunar.LunarCalendar} 换算今天农历比对（"12-L" = 腊月最后一天，闰月不触发），
 * 否则按 month_day 公历比对，旧配置缺 lunar 字段完全兼容）→ 节日模板（食物 + 猫猫币附件）</li>
 * <li>当日恰为 11 个关键节气之一（立春/春分/清明/谷雨/立夏/夏至/立秋/秋分/霜降/立冬/冬至，
 * 经 {@code lunar.SolarTerms} 寿星公式判定）→ 时令作物邮件（作物按季）</li>
 * </ol>
 * <p>
 * <b>防重</b>：投递成功后在 {@link MailData#getClaimedBlessings()} 记录防重键——
 * 生日/节日 = {@code "类型_YYYY-MM-dd"}（按日唯一），纪念日 = {@code "anniversary_<序号>_YYYY"}（同年一次），
 * 节气 = {@code "solarterm_<节气名>_YYYY"}（同年一次）。
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

    /** 统一 logger（O2-B02 去中心化：与主类同用 "gtit" logger 名，日志过滤口径不变） */
    private static final Logger LOG = LogManager.getLogger("gtit");

    public static final BlessingManager INSTANCE = new BlessingManager();

    /**
     * 发时令作物邮件的关键节气（v1.7.8 用户确认口径：24 节气全计算，
     * 仅这 11 个投递时令邮件；春 4 + 夏 2 + 秋 3 + 冬 2）
     */
    private static final Set<String> SOLAR_TERM_MAIL_TERMS = new LinkedHashSet<>(
        Arrays.asList(
            "立春",
            "春分",
            "清明",
            "谷雨", // 春
            "立夏",
            "夏至", // 夏
            "立秋",
            "秋分",
            "霜降", // 秋
            "立冬",
            "冬至" // 冬
        ));

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

        // ---- 3. 节日表（lunar 非空 = 农历换算比对；否则按 month_day 公历比对，旧配置兼容）----
        LunarCalendar.LunarDate todayLunar = null; // 懒换算（同一天只算一次）
        for (BlessingConfig.FestivalBlessing festival : BlessingConfig.getFestivals()) {
            if (festival == null) continue;
            if (festival.lunar != null && !festival.lunar.isEmpty()) {
                // 农历节日：先把今天公历换算农历再比对
                if (todayLunar == null) todayLunar = toLunarToday(year, monthDay);
                if (todayLunar == null) continue;
                if (!matchesLunarDate(festival.lunar, todayLunar)) continue;
            } else {
                if (festival.monthDay == null || festival.monthDay.isEmpty()) continue;
                if (!festival.monthDay.equals(monthDay)) continue;
            }
            sentAny |= trySend(
                playerId,
                mailData,
                "festival_" + festival.name + "_" + today,
                festival.title,
                festival.content,
                festival.buildAttachments(),
                sender);
        }

        // ---- 4. 节气时令邮件（11 个关键节气，防重键按公历年）----
        sentAny |= trySendSolarTermMail(playerId, mailData, year, monthDay, sender);

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
                LOG.error("跨日祝福检测失败: " + player.getCommandSenderName(), t);
            }
        });
    }

    // ==================== 内部辅助 ====================

    /**
     * 今天公历（yyyy 与 MM-dd）换算农历；格式异常或超出支持范围返回 null（调用方跳过农历节日）
     */
    private LunarCalendar.LunarDate toLunarToday(String year, String monthDay) {
        try {
            int y = Integer.parseInt(year);
            String[] md = monthDay.split("-", 2);
            return LunarCalendar.solarToLunar(y, Integer.parseInt(md[0]), Integer.parseInt(md[1]));
        } catch (RuntimeException e) {
            LOG.warn("祝福农历换算失败: year={}, monthDay={}", year, monthDay);
            return null;
        }
    }

    /**
     * 农历触发规格（"月-日"；日位 {@code L} = 该农历月最后一天）与今天农历比对
     * <p>
     * 闰月日一律不触发（如闰六月的初六不算端午/七夕等正常月节日）。
     */
    private boolean matchesLunarDate(String lunarSpec, LunarCalendar.LunarDate today) {
        String[] parts = lunarSpec.split("-", 2);
        if (parts.length != 2) return false;
        if (today.leapMonth) return false;
        int month;
        try {
            month = Integer.parseInt(parts[0].trim());
        } catch (NumberFormatException e) {
            LOG.warn("祝福农历规格非法: {}", lunarSpec);
            return false;
        }
        if (month < 1 || month > 12 || month != today.month) return false;
        String daySpec = parts[1].trim();
        if ("L".equalsIgnoreCase(daySpec)) {
            // 岁末日（除夕 = 腊月最后一天）：天数控表取该月天数
            return today.day == LunarCalendar.monthDays(today.year, today.month);
        }
        try {
            return Integer.parseInt(daySpec) == today.day;
        } catch (NumberFormatException e) {
            LOG.warn("祝福农历规格非法: {}", lunarSpec);
            return false;
        }
    }

    /**
     * 今天恰为 {@link #SOLAR_TERM_MAIL_TERMS} 之一时投递一封时令作物邮件
     * <p>
     * 作物按季：春=小麦+胡萝卜，夏=西瓜片+甘蔗，秋=南瓜+苹果，冬=马铃薯+烤马铃薯
     * （全部 Vanilla 物品/方块）。防重键 {@code solarterm_<节气名>_<年份>}——
     * 同一节气每公历年只发一次（key 复用 {@link MailData#getClaimedBlessings()} 现有机制）。
     */
    private boolean trySendSolarTermMail(UUID playerId, MailData mailData, String year, String monthDay,
        String sender) {
        int y, m, d;
        try {
            y = Integer.parseInt(year);
            String[] md = monthDay.split("-", 2);
            m = Integer.parseInt(md[0]);
            d = Integer.parseInt(md[1]);
        } catch (RuntimeException e) {
            LOG.warn("节气判定日期解析失败: year={}, monthDay={}", year, monthDay);
            return false;
        }
        String term = SolarTerms.getTermName(y, m, d);
        if (term == null || !SOLAR_TERM_MAIL_TERMS.contains(term)) return false;

        // 按季取作物 + 文案
        List<ItemStack> crops;
        String content;
        switch (term) {
            case "立春":
            case "春分":
            case "清明":
            case "谷雨":
                crops = Arrays.asList(new ItemStack(Items.wheat, 2), new ItemStack(Items.carrot, 2));
                content = "今日" + term + "，万物生长！猫猫售货机送上应季的小麦和胡萝卜，快去播种春天的希望吧！";
                break;
            case "立夏":
            case "夏至":
                crops = Arrays.asList(new ItemStack(Items.melon, 2), new ItemStack(Items.reeds, 2));
                content = "今日" + term + "，暑气渐盛！猫猫售货机送上清甜的西瓜和甘蔗，记得消暑补水哦！";
                break;
            case "立秋":
            case "秋分":
            case "霜降":
                crops = Arrays.asList(new ItemStack(Blocks.pumpkin, 2), new ItemStack(Items.apple, 2));
                content = "今日" + term + "，秋高气爽！猫猫售货机送上金黄的南瓜和苹果，祝你收获满满！";
                break;
            default: // 立冬 / 冬至
                crops = Arrays.asList(new ItemStack(Items.potato, 2), new ItemStack(Items.baked_potato, 2));
                content = "今日" + term + "，天寒地冻！猫猫售货机送上热乎乎的马铃薯和烤马铃薯，注意保暖多吃饭！";
                break;
        }
        return trySend(playerId, mailData, "solarterm_" + term + "_" + year, "今日" + term, content, crops, sender);
    }

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
            LOG.warn("祝福邮件投递失败（邮箱已满）: player={}, key={}", playerId, key);
            return false;
        }
        mailData.markClaimedBlessing(key);
        // 唯一持久化联动点（IT-BUG-09）：防重键落盘依赖 MailData，崩溃窗口语义见类 javadoc
        MailManager.INSTANCE.saveMailData(playerId);
        LOG.info("祝福邮件已投递: player={}, key={}", playerId, key);
        return true;
    }
}

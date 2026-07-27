package com.miaokatze.gtit.signin;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 客户端签到数据缓存
 * <p>
 * 由 {@link SignInSyncPacket} 的客户端处理器写入，{@link SignInCalendarGui} 读取。
 * 服务端侧不会收到同步包，因此本类在服务端始终保持空数据（GUI 在服务端构建时读到的都是默认值，
 * 不影响服务端逻辑——签到判定完全由 {@link DailySignInManager} 在服务端权威执行）。
 * <p>
 * <b>日期口径</b>：「今天」以服务端同步包携带的 {@link #serverToday} 为准，
 * 避免客户端时区/系统时间错误导致日历错位；未收到同步前回退到客户端本地日期。
 * <p>
 * <b>配置口径（v1.7.8 任务5+6）</b>：每月块（递增开关/系数、工作日/周末默认、逐日覆盖）与
 * 连续/累计阶梯奖励以服务端同步包携带的配置快照为准（{@link #updateConfig} 写入）；
 * 未收到同步前回退到本地 {@link DailySignInConfig} 静态配置（单人存档下与服务端同源，
 * 专用服务器客户端则为本地默认值，登录同步后即被服务端权威值覆盖）。
 */
public final class SignInClientData {

    // ==================== 签到结果常量 ====================

    /** 无结果（普通状态刷新：登录同步/跨日同步） */
    public static final int RESULT_NONE = 0;
    /** 签到成功 */
    public static final int RESULT_SUCCESS = 1;
    /** 今日已签到（重复请求） */
    public static final int RESULT_ALREADY_SIGNED = 2;
    /** 签到失败（数据异常） */
    public static final int RESULT_ERROR = 3;

    // ==================== 同步状态字段 ====================

    /** 累计签到总天数 */
    private static volatile int totalDays;
    /** 当前连续签到天数 */
    private static volatile int consecutiveDays;
    /** 最后一次签到日期（yyyy-MM-dd） */
    private static volatile String lastSignInDate = "";
    /** 服务端「今天」（yyyy-MM-dd，同步包携带） */
    private static volatile String serverToday = "";
    /** 当月已签到日期集合（yyyy-MM-dd） */
    private static volatile Set<String> monthlyDates = new HashSet<>();
    /** 当月已领取的阶梯奖励天数集合 */
    private static volatile Set<Integer> claimedTierDays = new HashSet<>();
    /** 已领取的累计签到阶梯天数集合（v1.7.8 任务5；永久不清空） */
    private static volatile Set<Integer> claimedCumulativeTierDays = new HashSet<>();

    // ==================== 最近一次签到结果（供 GUI 提示条显示） ====================

    private static volatile int lastResult = RESULT_NONE;
    private static volatile int lastResultReward = 0;
    private static volatile int lastResultTierDays = 0;
    /** 结果产生时间（System.currentTimeMillis），供 GUI 做限时显示 */
    private static volatile long lastResultTimeMs = 0L;

    // ==================== 服务端配置快照（同步包携带；v1.7.8 任务5+6 重构为统一奖励模型） ====================

    /**
     * 服务端同步的连续阶梯奖励列表；null 表示尚未收到配置同步（回退本地 {@link DailySignInConfig}）
     * <p>
     * 本字段同时作为「每月块配置快照是否已同步」的标记：非 null 时 cfgIncrementEnabled/cfgWeekday 等
     * 同批字段均为服务端权威值。
     */
    private static volatile List<SignInRewardTier> cfgTiers = null;
    /** 服务端同步的累计签到阶梯奖励列表（v1.7.8 任务5；{@link #cfgTiers} 同批有效） */
    private static volatile List<SignInRewardTier> cfgCumulativeTiers = new ArrayList<>();
    /** 服务端同步的每日默认奖励递增开关（v1.7.8 任务6） */
    private static volatile boolean cfgIncrementEnabled;
    /** 服务端同步的连续天数奖励系数（仅递增开关开启时生效） */
    private static volatile double cfgIncrement;
    /** 服务端同步的工作日默认奖励（v1.7.8 任务6） */
    private static volatile SignInReward cfgWeekday = SignInReward.EMPTY;
    /** 服务端同步的周末默认奖励（v1.7.8 任务6） */
    private static volatile SignInReward cfgWeekend = SignInReward.EMPTY;
    /** 服务端同步的逐日覆盖奖励表（键=月内日号，v1.7.8 任务6） */
    private static volatile Map<Integer, SignInReward> cfgDayOverrides = new HashMap<>();

    // ==================== v1.7.6 G2③：在线时长 / 纪念日 ====================

    /** 今日已在线秒数（服务端 tick 累计同步） */
    private static volatile int onlineSecondsToday;
    /** 今日已领取的在线奖励档位（requiredSeconds） */
    private static volatile Set<Integer> claimedOnlineTiers = new HashSet<>();
    /** 生日（MM-dd，未设置为空） */
    private static volatile String birthday = "";
    /** 首次入服日期（yyyy-MM-dd） */
    private static volatile String firstJoinDate = "";
    /** 自定义纪念日列表 */
    private static volatile List<AnniversaryEntry> anniversaries = new ArrayList<>();

    /** 服务端同步的在线时长奖励档位；null 表示尚未收到同步（回退本地 {@link OnlineTimeConfig}） */
    private static volatile List<OnlineTimeRewardTier> cfgOnlineTiers = null;

    private SignInClientData() {}

    // ==================== 写入（网络包处理器调用） ====================

    /**
     * 用服务端同步的数据全量刷新客户端缓存
     *
     * @param data       服务端签到数据（同步包已反序列化）
     * @param today      服务端今天日期（yyyy-MM-dd）
     * @param result     本次同步附带的签到结果（{@link #RESULT_NONE} 表示纯状态刷新）
     * @param baseReward 本次签到发放的基础奖励（result 为 SUCCESS 时有效）
     * @param tierDays   本次触发的阶梯奖励天数（0 表示未触发）
     */
    public static synchronized void update(DailySignInData data, String today, int result, int baseReward,
        int tierDays) {
        if (data != null) {
            totalDays = data.getTotalSignInDays();
            consecutiveDays = data.getConsecutiveDays();
            lastSignInDate = data.getLastSignInDate() == null ? "" : data.getLastSignInDate();
            monthlyDates = new HashSet<>(data.getMonthlySignInDates());
            // 解析已领取阶梯记录（格式 "tier_<days>_<yyyy-MM>"），仅保留当月
            Set<Integer> claimed = new HashSet<>();
            String yearMonth = getYearMonth(today);
            for (String record : data.getClaimedTierRewards()) {
                if (record != null && record.endsWith(yearMonth) && record.startsWith("tier_")) {
                    try {
                        String daysPart = record.substring(5, record.length() - yearMonth.length() - 1);
                        claimed.add(Integer.parseInt(daysPart));
                    } catch (NumberFormatException ignored) {
                        // 跳过格式异常的记录
                    }
                }
            }
            claimedTierDays = claimed;
            // v1.7.8 任务5：解析已领取累计阶梯记录（格式 "cum_<days>"，永久不清空）
            Set<Integer> cumClaimed = new HashSet<>();
            for (String record : data.getClaimedCumulativeTiers()) {
                if (record != null && record.startsWith("cum_")) {
                    try {
                        cumClaimed.add(Integer.parseInt(record.substring(4)));
                    } catch (NumberFormatException ignored) {
                        // 跳过格式异常的记录
                    }
                }
            }
            claimedCumulativeTierDays = cumClaimed;
            // ===== v1.7.6 G2③：在线时长 / 生日 / 首登 / 纪念日 =====
            onlineSecondsToday = data.getOnlineSecondsToday();
            // 解析今日已领取的在线档位（记录含历史日期，仅保留 today 条目）
            Set<Integer> onlineClaimed = new HashSet<>();
            for (String record : data.getClaimedOnlineTiers()) {
                if (record != null && record.startsWith(getTodayPrefix(today))) {
                    try {
                        onlineClaimed.add(Integer.parseInt(record.substring(getTodayPrefix(today).length())));
                    } catch (NumberFormatException ignored) {
                        // 跳过格式异常的记录
                    }
                }
            }
            claimedOnlineTiers = onlineClaimed;
            birthday = data.getBirthday() == null ? "" : data.getBirthday();
            firstJoinDate = data.getFirstJoinDate() == null ? "" : data.getFirstJoinDate();
            anniversaries = new ArrayList<>(data.getAnniversaries());
        }
        if (today != null && !today.isEmpty()) {
            serverToday = today;
        }
        if (result != RESULT_NONE) {
            lastResult = result;
            lastResultReward = baseReward;
            lastResultTierDays = tierDays;
            lastResultTimeMs = System.currentTimeMillis();
        }
    }

    /** 清除结果提示（GUI 消费后调用，避免重复展示） */
    public static synchronized void clearResult() {
        lastResult = RESULT_NONE;
        lastResultReward = 0;
        lastResultTierDays = 0;
    }

    /**
     * 写入服务端配置快照（v1.7.8 任务5+6：每月块 + 连续/累计阶梯，统一奖励模型）
     * <p>
     * 由 {@link SignInSyncPacket} 客户端处理器在收到携带配置快照的同步包时调用。
     * 防御性拷贝列表/映射，避免调用方后续修改影响缓存。
     *
     * @param incrementEnabled 服务端每日默认奖励递增开关
     * @param increment        服务端连续天数奖励系数
     * @param weekday          服务端工作日默认奖励（null 按 {@link SignInReward#EMPTY}）
     * @param weekend          服务端周末默认奖励（null 按 {@link SignInReward#EMPTY}）
     * @param dayOverrides     服务端逐日覆盖奖励表（null 按空表）
     * @param tiers            服务端连续阶梯奖励列表（null 时清空快照回退本地配置）
     * @param cumulativeTiers  服务端累计阶梯奖励列表（null 按空表）
     */
    public static synchronized void updateConfig(boolean incrementEnabled, double increment, SignInReward weekday,
        SignInReward weekend, Map<Integer, SignInReward> dayOverrides, List<SignInRewardTier> tiers,
        List<SignInRewardTier> cumulativeTiers) {
        cfgIncrementEnabled = incrementEnabled;
        cfgIncrement = Math.max(0, increment);
        cfgWeekday = weekday == null ? SignInReward.EMPTY : weekday;
        cfgWeekend = weekend == null ? SignInReward.EMPTY : weekend;
        cfgDayOverrides = dayOverrides == null ? new HashMap<>() : new HashMap<>(dayOverrides);
        cfgTiers = tiers == null ? null : new ArrayList<>(tiers);
        cfgCumulativeTiers = cumulativeTiers == null ? new ArrayList<>() : new ArrayList<>(cumulativeTiers);
    }

    /**
     * 写入服务端在线时长配置快照（v1.7.6 G2③）
     * <p>
     * 由 {@link SignInSyncPacket} 客户端处理器调用；null 时清空快照回退本地 {@link OnlineTimeConfig}。
     */
    public static synchronized void updateOnlineConfig(List<OnlineTimeRewardTier> tiers) {
        cfgOnlineTiers = tiers == null ? null : new ArrayList<>(tiers);
    }

    /**
     * 当前生效的在线时长奖励档位
     * <p>
     * 优先返回服务端同步快照；未同步时回退本地 {@link OnlineTimeConfig#getTiers()}。
     */
    public static List<OnlineTimeRewardTier> getOnlineRewardTiers() {
        List<OnlineTimeRewardTier> tiers = cfgOnlineTiers;
        return tiers != null ? tiers : OnlineTimeConfig.getTiers();
    }

    // ==================== 配置读取（GUI 调用，优先服务端快照，回退本地配置） ====================

    /**
     * 当前生效的阶梯奖励列表
     * <p>
     * 优先返回服务端同步快照；未同步时回退本地 {@link DailySignInConfig#getRewardTiers()}。
     */
    public static List<SignInRewardTier> getRewardTiers() {
        List<SignInRewardTier> tiers = cfgTiers;
        return tiers != null ? tiers : DailySignInConfig.getRewardTiers();
    }

    /**
     * 获取下一个尚未达到的阶梯（服务端快照口径，逻辑同 {@link DailySignInConfig#getNextTier}）
     *
     * @param consecutiveDays 当前连续天数
     * @return 所需天数大于当前连续天数的最小阶梯；全部已达返回 null
     */
    public static SignInRewardTier getNextTier(int consecutiveDays) {
        SignInRewardTier next = null;
        for (SignInRewardTier tier : getRewardTiers()) {
            if (tier.getRequiredDays() > consecutiveDays) {
                if (next == null || tier.getRequiredDays() < next.getRequiredDays()) {
                    next = tier;
                }
            }
        }
        return next;
    }

    /**
     * 获取达到指定连续天数时触发的阶梯（服务端快照口径，逻辑同 {@link DailySignInConfig#getTriggeredTier}）
     *
     * @param consecutiveDays 当前连续天数
     * @return 恰好要求该天数的阶梯；无则返回 null
     */
    public static SignInRewardTier getTriggeredTier(int consecutiveDays) {
        for (SignInRewardTier tier : getRewardTiers()) {
            if (tier.getRequiredDays() == consecutiveDays) {
                return tier;
            }
        }
        return null;
    }

    /**
     * 当前生效的累计签到阶梯列表（v1.7.8 任务5）
     * <p>
     * 优先返回服务端同步快照；未同步时回退本地 {@link DailySignInConfig#getCumulativeTiers()}。
     */
    public static List<SignInRewardTier> getCumulativeTiers() {
        List<SignInRewardTier> tiers = cfgTiers != null ? cfgCumulativeTiers : null;
        return tiers != null ? tiers : DailySignInConfig.getCumulativeTiers();
    }

    /**
     * 获取达到指定累计天数时触发的累计阶梯（服务端快照口径，逻辑同 {@link DailySignInConfig#getTriggeredCumulativeTier}）
     *
     * @param totalDays 累计签到天数
     * @return 恰好要求该累计天数的阶梯；无则返回 null
     */
    public static SignInRewardTier getTriggeredCumulativeTier(int totalDays) {
        for (SignInRewardTier tier : getCumulativeTiers()) {
            if (tier.getRequiredDays() == totalDays) {
                return tier;
            }
        }
        return null;
    }

    /**
     * 获取下一个尚未达到的累计阶梯（服务端快照口径，逻辑同 {@link DailySignInConfig#getNextCumulativeTier}）
     *
     * @param totalDays 当前累计签到天数
     * @return 所需天数大于当前累计天数的最小阶梯；全部已达返回 null
     */
    public static SignInRewardTier getNextCumulativeTier(int totalDays) {
        SignInRewardTier next = null;
        for (SignInRewardTier tier : getCumulativeTiers()) {
            if (tier.getRequiredDays() > totalDays) {
                if (next == null || tier.getRequiredDays() < next.getRequiredDays()) {
                    next = tier;
                }
            }
        }
        return next;
    }

    /** 指定累计阶梯天数是否已领取（永久限领一次） */
    public static boolean hasClaimedCumulativeTier(int days) {
        return claimedCumulativeTierDays.contains(days);
    }

    // ==================== 每月块配置读取（v1.7.8 任务6；优先服务端快照，回退本地配置） ====================

    /** 每日默认奖励是否随连续天数递增 */
    public static boolean isIncrementEnabled() {
        return cfgTiers != null ? cfgIncrementEnabled : DailySignInConfig.isIncrementEnabled();
    }

    /** 连续天数奖励系数（仅递增开关开启时生效） */
    public static double getConsecutiveIncrement() {
        return cfgTiers != null ? cfgIncrement : DailySignInConfig.getConsecutiveIncrement();
    }

    /** 工作日默认奖励 */
    public static SignInReward getWeekdayDefault() {
        return cfgTiers != null ? cfgWeekday : DailySignInConfig.getWeekdayDefault();
    }

    /** 周末默认奖励 */
    public static SignInReward getWeekendDefault() {
        return cfgTiers != null ? cfgWeekend : DailySignInConfig.getWeekendDefault();
    }

    /** 逐日覆盖奖励表（键=月内日号；防御性拷贝） */
    public static Map<Integer, SignInReward> getDayOverrides() {
        return cfgTiers != null ? new HashMap<>(cfgDayOverrides) : new HashMap<>(DailySignInConfig.getDayOverrides());
    }

    /**
     * 获取指定日期生效的每日奖励（覆盖优先，否则按工作日/周末默认；服务端快照口径）
     * <p>
     * 货币量请以 {@link #calculateDayCurrency} 为准（含递增口径）；本方法主要用于取货币 ID 与物品列表。
     *
     * @param date 日期（yyyy-MM-dd）
     * @return 生效的奖励（不会为 null）
     */
    public static SignInReward getEffectiveDayReward(String date) {
        if (cfgTiers != null) {
            int day = parseDayOfMonth(date);
            if (day > 0) {
                SignInReward override = cfgDayOverrides.get(day);
                if (override != null) return override;
            }
            return DailySignInConfig.isWeekend(date) ? cfgWeekend : cfgWeekday;
        }
        return DailySignInConfig.getEffectiveDayReward(date);
    }

    /** 指定日期是否存在逐日覆盖（服务端快照口径） */
    public static boolean hasDayOverride(String date) {
        if (cfgTiers != null) {
            int day = parseDayOfMonth(date);
            return day > 0 && cfgDayOverrides.containsKey(day);
        }
        return DailySignInConfig.hasDayOverride(date);
    }

    /**
     * 计算指定日期签到的每日货币量预览（服务端快照口径，公式同 {@link DailySignInConfig#calculateDayCurrency}）
     * <p>
     * 口径：逐日覆盖天直接取覆盖奖励货币量（<b>不递增</b>）；否则按工作日/周末默认奖励货币量，
     * 递增开关开启时按 {@code base + floor((连续天数-1) * 系数)} 递增。
     *
     * @param date            日期（yyyy-MM-dd）
     * @param consecutiveDays 签到完成后的连续天数（≥1）
     * @return 应发放的货币数量（预览值，实际以服务端发放为准）
     */
    public static int calculateDayCurrency(String date, int consecutiveDays) {
        if (cfgTiers != null) {
            int day = parseDayOfMonth(date);
            if (day > 0) {
                SignInReward override = cfgDayOverrides.get(day);
                if (override != null) {
                    // 覆盖天：不递增
                    return override.getCurrencyAmount();
                }
            }
            SignInReward def = DailySignInConfig.isWeekend(date) ? cfgWeekend : cfgWeekday;
            int base = def.getCurrencyAmount();
            if (!cfgIncrementEnabled || consecutiveDays <= 1) return base;
            return base + (int) Math.floor((consecutiveDays - 1) * cfgIncrement);
        }
        return DailySignInConfig.calculateDayCurrency(date, consecutiveDays);
    }

    // ==================== 读取（GUI 调用） ====================

    public static int getTotalDays() {
        return totalDays;
    }

    public static int getConsecutiveDays() {
        return consecutiveDays;
    }

    public static String getLastSignInDate() {
        return lastSignInDate;
    }

    /**
     * 「今天」日期（yyyy-MM-dd）
     * <p>
     * 优先服务端日期；未同步时回退客户端本地日期。
     */
    public static String getToday() {
        return serverToday.isEmpty() ? clientLocalToday() : serverToday;
    }

    /** 当前年月（yyyy-MM，基于「今天」口径） */
    public static String getYearMonth() {
        return getYearMonth(getToday());
    }

    /** 今日是否已签到 */
    public static boolean hasSignedToday() {
        return getToday().equals(lastSignInDate);
    }

    /** 指定日期是否已签到（当月日期格渲染用） */
    public static boolean hasSigned(String date) {
        return monthlyDates.contains(date);
    }

    /** 指定阶梯天数当月是否已领取 */
    public static boolean hasClaimedTier(int days) {
        return claimedTierDays.contains(days);
    }

    public static int getLastResult() {
        return lastResult;
    }

    public static int getLastResultReward() {
        return lastResultReward;
    }

    public static int getLastResultTierDays() {
        return lastResultTierDays;
    }

    public static long getLastResultTimeMs() {
        return lastResultTimeMs;
    }

    // ==================== v1.7.6 G2③：在线时长 / 纪念日读取 ====================

    /** 今日已在线秒数 */
    public static int getOnlineSecondsToday() {
        return onlineSecondsToday;
    }

    /** 指定档位的在线奖励今日是否已领取 */
    public static boolean hasClaimedOnlineTier(int requiredSeconds) {
        return claimedOnlineTiers.contains(requiredSeconds);
    }

    /** 生日（MM-dd，未设置返回空串） */
    public static String getBirthday() {
        return birthday;
    }

    /** 首次入服日期（yyyy-MM-dd，未知返回空串） */
    public static String getFirstJoinDate() {
        return firstJoinDate;
    }

    /** 自定义纪念日列表（防御性拷贝） */
    public static List<AnniversaryEntry> getAnniversaries() {
        return new ArrayList<>(anniversaries);
    }

    // ==================== 内部辅助 ====================

    /** 从 yyyy-MM-dd 截取 yyyy-MM（容错：长度不足时原样返回） */
    private static String getYearMonth(String date) {
        if (date == null || date.length() < 7) return date == null ? "" : date;
        return date.substring(0, 7);
    }

    /** 解析 yyyy-MM-dd 的月内日号（格式异常返回 -1） */
    private static int parseDayOfMonth(String date) {
        try {
            if (date == null || date.length() < 10) return -1;
            return Integer.parseInt(date.substring(8, 10));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** 在线档位已领取记录的今日前缀（"yyyy-MM-dd_"，null 安全） */
    private static String getTodayPrefix(String today) {
        return (today == null ? "" : today) + "_";
    }

    /** 客户端本地今天（未收到服务端同步前的回退值） */
    private static String clientLocalToday() {
        Calendar cal = Calendar.getInstance();
        return String.format(
            "%04d-%02d-%02d",
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH) + 1,
            cal.get(Calendar.DAY_OF_MONTH));
    }
}

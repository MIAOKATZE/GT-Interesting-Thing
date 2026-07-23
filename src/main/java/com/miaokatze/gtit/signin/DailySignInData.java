package com.miaokatze.gtit.signin;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;

/**
 * 玩家每日签到数据
 * <p>
 * 记录玩家的签到历史、连续天数、累计天数、当月签到列表、已领取阶梯奖励等信息。
 * 通过 NBT 序列化/反序列化实现持久化，存储到 {@code <world>/gtit_signin/<uuid>.dat}。
 * <p>
 * 参照 {@link com.miaokatze.gtit.trade.NekoWallet} 的 NBT 模式。
 *
 * @see DailySignInManager
 */
public class DailySignInData {

    // ==================== 字段定义 ====================

    /** 累计签到总天数（不随月度重置，永久累计） */
    private int totalSignInDays;

    /** 当前连续签到天数（断签后重置为 0） */
    private int consecutiveDays;

    /** 最后一次签到的日期字符串，格式 yyyy-MM-dd */
    private String lastSignInDate = "";

    /** 当月已签到的日期列表（每月 1 号重置），元素为 "yyyy-MM-dd" */
    private List<String> monthlySignInDates = new ArrayList<>();

    /** 历史已领取的阶梯奖励记录（防止重复领取），格式 "tier_<days>_<yyyy-MM>" */
    private List<String> claimedTierRewards = new ArrayList<>();

    // ==================== v1.7.6 G2③：每日在线 / 纪念日 ====================

    /** 今日累计在线秒数（跨日重置；每分钟 +60） */
    private int onlineSecondsToday;

    /** 最近一次在线累计对应的日期（yyyy-MM-dd；与今天不同则触发在线数据跨日重置） */
    private String lastOnlineTickDate = "";

    /** 已领取的每日在线奖励记录（防重复领取），格式 "yyyy-MM-dd_<requiredSeconds>"（跨日清空） */
    private List<String> claimedOnlineTiers = new ArrayList<>();

    /** 玩家自配生日（"MM-dd"，空串=未设置） */
    private String birthday = "";

    /** 首次进入服务器日期（"yyyy-MM-dd"，首次登录时服务端自动写入，空串=旧存档未知） */
    private String firstJoinDate = "";

    /** 自定义纪念日列表（玩家自配增删） */
    private List<AnniversaryEntry> anniversaries = new ArrayList<>();

    // ==================== NBT 序列化 ====================

    /**
     * 将签到数据写入 NBT 标签
     *
     * @return 包含所有签到数据的 NBTTagCompound
     */
    public NBTTagCompound writeToNBT() {
        NBTTagCompound nbt = new NBTTagCompound();
        nbt.setInteger("totalSignInDays", totalSignInDays);
        nbt.setInteger("consecutiveDays", consecutiveDays);
        nbt.setString("lastSignInDate", lastSignInDate == null ? "" : lastSignInDate);

        // 当月签到日期列表
        NBTTagList monthlyList = new NBTTagList();
        for (String date : monthlySignInDates) {
            monthlyList.appendTag(new NBTTagString(date));
        }
        nbt.setTag("monthlySignInDates", monthlyList);

        // 已领取阶梯奖励记录
        NBTTagList claimedList = new NBTTagList();
        for (String record : claimedTierRewards) {
            claimedList.appendTag(new NBTTagString(record));
        }
        nbt.setTag("claimedTierRewards", claimedList);

        // v1.7.6 G2③：每日在线 / 纪念日
        nbt.setInteger("onlineSecondsToday", onlineSecondsToday);
        nbt.setString("lastOnlineTickDate", lastOnlineTickDate == null ? "" : lastOnlineTickDate);
        nbt.setString("birthday", birthday == null ? "" : birthday);
        nbt.setString("firstJoinDate", firstJoinDate == null ? "" : firstJoinDate);

        // 已领取在线奖励记录
        NBTTagList claimedOnlineList = new NBTTagList();
        for (String record : claimedOnlineTiers) {
            claimedOnlineList.appendTag(new NBTTagString(record));
        }
        nbt.setTag("claimedOnlineTiers", claimedOnlineList);

        // 自定义纪念日列表（复合标签列表）
        NBTTagList anniversaryList = new NBTTagList();
        for (AnniversaryEntry entry : anniversaries) {
            if (entry != null) {
                anniversaryList.appendTag(entry.writeToNBT());
            }
        }
        nbt.setTag("anniversaries", anniversaryList);

        return nbt;
    }

    /**
     * 从 NBT 标签读取签到数据
     *
     * @param nbt 包含签到数据的 NBTTagCompound
     */
    public void readFromNBT(NBTTagCompound nbt) {
        if (nbt == null) return;
        this.totalSignInDays = nbt.getInteger("totalSignInDays");
        this.consecutiveDays = nbt.getInteger("consecutiveDays");
        this.lastSignInDate = nbt.getString("lastSignInDate");

        // 读取当月签到日期列表（8 = NBTTagString）
        this.monthlySignInDates.clear();
        NBTTagList monthlyList = nbt.getTagList("monthlySignInDates", 8);
        for (int i = 0; i < monthlyList.tagCount(); i++) {
            this.monthlySignInDates.add(monthlyList.getStringTagAt(i));
        }

        // 读取已领取阶梯奖励记录
        this.claimedTierRewards.clear();
        NBTTagList claimedList = nbt.getTagList("claimedTierRewards", 8);
        for (int i = 0; i < claimedList.tagCount(); i++) {
            this.claimedTierRewards.add(claimedList.getStringTagAt(i));
        }

        // v1.7.6 G2③：每日在线 / 纪念日（全部缺省兼容：旧档无键时读出 0/空串/空表，无损加载）
        this.onlineSecondsToday = Math.max(0, nbt.getInteger("onlineSecondsToday"));
        this.lastOnlineTickDate = nbt.getString("lastOnlineTickDate");
        this.birthday = nbt.getString("birthday");
        this.firstJoinDate = nbt.getString("firstJoinDate");

        // 读取已领取在线奖励记录（8 = NBTTagString）
        this.claimedOnlineTiers.clear();
        NBTTagList claimedOnlineList = nbt.getTagList("claimedOnlineTiers", 8);
        for (int i = 0; i < claimedOnlineList.tagCount(); i++) {
            this.claimedOnlineTiers.add(claimedOnlineList.getStringTagAt(i));
        }

        // 读取自定义纪念日列表（10 = NBTTagCompound）
        this.anniversaries.clear();
        NBTTagList anniversaryList = nbt.getTagList("anniversaries", 10);
        for (int i = 0; i < anniversaryList.tagCount(); i++) {
            AnniversaryEntry entry = new AnniversaryEntry();
            entry.readFromNBT(anniversaryList.getCompoundTagAt(i));
            this.anniversaries.add(entry);
        }
    }

    // ==================== 业务方法 ====================

    /**
     * 判断今日是否已签到
     *
     * @param today 今日日期字符串（yyyy-MM-dd）
     * @return true 表示今日已签到
     */
    public boolean hasSignedToday(String today) {
        return today != null && today.equals(lastSignInDate);
    }

    /**
     * 记录一次签到
     * <p>
     * 更新累计天数、连续天数、最后签到日期、当月签到列表。
     * 连续天数计算由 {@link DailySignInManager#calculateNewConsecutive} 完成，
     * 本方法只负责写入。
     *
     * @param today          今日日期（yyyy-MM-dd）
     * @param yesterday      昨日日期（yyyy-MM-dd，未使用，保留参数对齐 Manager 调用）
     * @param newConsecutive 新的连续天数
     */
    public void recordSignIn(String today, String yesterday, int newConsecutive) {
        this.totalSignInDays++;
        this.consecutiveDays = newConsecutive;
        this.lastSignInDate = today;
        // 加入当月签到列表（防重复）
        if (!this.monthlySignInDates.contains(today)) {
            this.monthlySignInDates.add(today);
        }
    }

    /**
     * 月度重置：清空当月签到日期列表
     * <p>
     * 每月 1 号调用，累计天数和连续天数不受影响。
     */
    public void resetMonthly() {
        this.monthlySignInDates.clear();
    }

    /**
     * 断签重置：连续天数归零
     */
    public void resetConsecutive() {
        this.consecutiveDays = 0;
    }

    /**
     * 记录已领取的阶梯奖励
     *
     * @param tierDays  阶梯天数（如 7、14、30）
     * @param yearMonth 年月标识（如 "2026-07"）
     */
    public void claimTierReward(int tierDays, String yearMonth) {
        String record = "tier_" + tierDays + "_" + yearMonth;
        if (!claimedTierRewards.contains(record)) {
            claimedTierRewards.add(record);
        }
    }

    /**
     * 检查阶梯奖励是否已领取
     *
     * @param tierDays  阶梯天数
     * @param yearMonth 年月标识
     * @return true 表示已领取
     */
    public boolean hasClaimedTier(int tierDays, String yearMonth) {
        return claimedTierRewards.contains("tier_" + tierDays + "_" + yearMonth);
    }

    // ==================== Getter / Setter ====================

    public int getTotalSignInDays() {
        return totalSignInDays;
    }

    public int getConsecutiveDays() {
        return consecutiveDays;
    }

    public String getLastSignInDate() {
        return lastSignInDate;
    }

    public List<String> getMonthlySignInDates() {
        return monthlySignInDates;
    }

    /**
     * 已领取阶梯奖励记录（格式 "tier_<days>_<yyyy-MM>"，只读视图由调用方自行拷贝）
     */
    public List<String> getClaimedTierRewards() {
        return claimedTierRewards;
    }

    /**
     * 管理员设置连续天数（仅用于 /gtit signin admin set 指令）
     */
    public void setConsecutiveDays(int days) {
        this.consecutiveDays = Math.max(0, days);
    }

    /**
     * 管理员设置累计天数（仅用于指令）
     */
    public void setTotalSignInDays(int days) {
        this.totalSignInDays = Math.max(0, days);
    }

    // ==================== v1.7.6 G2③：每日在线 ====================

    /**
     * 累计在线时长（服务端 tick 每分钟调用一次）
     * <p>
     * 内部处理跨日：{@link #lastOnlineTickDate} 与传入的今天不同时，
     * 先清零今日在线秒数与已领取在线奖励记录，再累计。
     * 本方法只改内存值，落盘由调用方按「登录/登出/跨日」节奏控制（v1.7.6 用户确认口径）。
     *
     * @param today   今日日期（yyyy-MM-dd，服务端口径）
     * @param seconds 本次累计的秒数（正常为 60；传 0 可仅触发跨日重置检查）
     */
    public void addOnlineSeconds(String today, int seconds) {
        if (today == null || today.isEmpty()) return;
        if (!today.equals(lastOnlineTickDate)) {
            // 跨日重置：在线秒数清零、领取记录清空（记录键含日期，前日记录已失效）
            this.onlineSecondsToday = 0;
            this.claimedOnlineTiers.clear();
            this.lastOnlineTickDate = today;
        }
        if (seconds > 0) {
            this.onlineSecondsToday += seconds;
        }
    }

    /**
     * 记录已领取的每日在线奖励
     *
     * @param today           今日日期（yyyy-MM-dd）
     * @param requiredSeconds 档位所需秒数（作为档位标识，配置调序后仍稳定）
     */
    public void claimOnlineTier(String today, int requiredSeconds) {
        String record = today + "_" + requiredSeconds;
        if (!claimedOnlineTiers.contains(record)) {
            claimedOnlineTiers.add(record);
        }
    }

    /**
     * 检查指定档位的每日在线奖励今日是否已领取
     *
     * @param today           今日日期（yyyy-MM-dd）
     * @param requiredSeconds 档位所需秒数
     * @return true 表示今日已领取
     */
    public boolean hasClaimedOnlineTier(String today, int requiredSeconds) {
        return claimedOnlineTiers.contains(today + "_" + requiredSeconds);
    }

    public int getOnlineSecondsToday() {
        return onlineSecondsToday;
    }

    public String getLastOnlineTickDate() {
        return lastOnlineTickDate;
    }

    /**
     * 已领取在线奖励记录（格式 "yyyy-MM-dd_<requiredSeconds>"，只读视图由调用方自行拷贝）
     */
    public List<String> getClaimedOnlineTiers() {
        return claimedOnlineTiers;
    }

    // ==================== v1.7.6 G2③：生日 / 首登 / 纪念日 ====================

    /** 玩家自配生日（"MM-dd"，空串=未设置） */
    public String getBirthday() {
        return birthday;
    }

    public void setBirthday(String birthday) {
        this.birthday = birthday == null ? "" : birthday;
    }

    /** 首次进入服务器日期（"yyyy-MM-dd"，空串=旧存档未知） */
    public String getFirstJoinDate() {
        return firstJoinDate;
    }

    public void setFirstJoinDate(String firstJoinDate) {
        this.firstJoinDate = firstJoinDate == null ? "" : firstJoinDate;
    }

    /**
     * 自定义纪念日列表（玩家 UUID 维度；增删由 {@link DailySignInManager} 权威执行）
     */
    public List<AnniversaryEntry> getAnniversaries() {
        return anniversaries;
    }
}

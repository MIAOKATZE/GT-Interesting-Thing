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
}

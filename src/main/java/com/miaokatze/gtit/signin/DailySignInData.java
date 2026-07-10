package com.miaokatze.gtit.signin;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.nbt.NBTTagCompound;

/**
 * 玩家签到数据
 * 记录总签到天数、连续天数、签到日期等
 */
public class DailySignInData {

    private int totalSignInDays;
    private int consecutiveDays;
    private String lastSignInDate;
    private List<String> monthlySignInDates;
    private List<String> claimedTierRewards;

    public DailySignInData() {
        this.monthlySignInDates = new ArrayList<>();
        this.claimedTierRewards = new ArrayList<>();
    }

    public boolean hasSignedToday(String today) {
        // TODO: v1.6.3 实现
        return today != null && today.equals(lastSignInDate);
    }

    public void recordSignIn(String today, String yesterday, int newConsecutive) {
        // TODO: v1.6.3 实现
    }

    public void resetMonthly() {
        // TODO: v1.6.3 实现
        monthlySignInDates.clear();
    }

    public void claimTierReward(int tierDays, String yearMonth) {
        // TODO: v1.6.3 实现
    }

    public NBTTagCompound writeToNBT() {
        // TODO: v1.6.3 实现
        return null;
    }

    public void readFromNBT(NBTTagCompound nbt) {
        // TODO: v1.6.3 实现
    }

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
}

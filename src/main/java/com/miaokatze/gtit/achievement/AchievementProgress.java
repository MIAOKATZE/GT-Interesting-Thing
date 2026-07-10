package com.miaokatze.gtit.achievement;

import net.minecraft.nbt.NBTTagCompound;

/**
 * 玩家成就进度
 */
public class AchievementProgress {

    private String achievementId;
    private int currentValue;
    private boolean completed;
    private boolean rewardClaimed;
    private long completedTime;

    public AchievementProgress() {}

    public AchievementProgress(String achievementId) {
        this.achievementId = achievementId;
        this.currentValue = 0;
        this.completed = false;
        this.rewardClaimed = false;
        this.completedTime = 0;
    }

    public boolean canClaimReward() {
        // TODO: v1.6.5 实现
        return completed && !rewardClaimed;
    }

    public NBTTagCompound writeToNBT() {
        // TODO: v1.6.5 实现
        return null;
    }

    public void readFromNBT(NBTTagCompound nbt) {
        // TODO: v1.6.5 实现
    }

    public String getAchievementId() {
        return achievementId;
    }

    public int getCurrentValue() {
        return currentValue;
    }

    public void setCurrentValue(int currentValue) {
        this.currentValue = currentValue;
    }

    public boolean isCompleted() {
        return completed;
    }

    public void setCompleted(boolean completed) {
        this.completed = completed;
    }

    public boolean isRewardClaimed() {
        return rewardClaimed;
    }

    public void setRewardClaimed(boolean rewardClaimed) {
        this.rewardClaimed = rewardClaimed;
    }

    public long getCompletedTime() {
        return completedTime;
    }

    public void setCompletedTime(long completedTime) {
        this.completedTime = completedTime;
    }
}

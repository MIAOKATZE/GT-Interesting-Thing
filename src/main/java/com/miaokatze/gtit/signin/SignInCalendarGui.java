package com.miaokatze.gtit.signin;

import net.minecraft.entity.player.EntityPlayer;

import com.cleanroommc.modularui.screen.ModularPanel;

/**
 * 签到日历GUI
 * 显示月历、签到状态、连续天数、奖励预览
 */
public class SignInCalendarGui {

    private final EntityPlayer player;
    private final DailySignInData signInData;

    public SignInCalendarGui(EntityPlayer player, DailySignInData signInData) {
        this.player = player;
        this.signInData = signInData;
    }

    public ModularPanel build() {
        // TODO: v1.6.3 实现
        return null;
    }

    private void buildCalendarGrid() {
        // TODO: v1.6.3 实现
    }

    private void buildStatusInfo() {
        // TODO: v1.6.3 实现
    }

    private void buildProgressBar() {
        // TODO: v1.6.3 实现
    }

    private void buildTierRewardsPreview() {
        // TODO: v1.6.3 实现
    }

    private void buildSignInButton() {
        // TODO: v1.6.3 实现
    }
}

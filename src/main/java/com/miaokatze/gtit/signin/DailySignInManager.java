package com.miaokatze.gtit.signin;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.world.World;

/**
 * 签到管理器单例
 * 管理所有玩家的签到数据，处理签到逻辑和奖励发放
 */
public class DailySignInManager {

    public static final DailySignInManager INSTANCE = new DailySignInManager();

    private final ConcurrentHashMap<UUID, DailySignInData> signInDataMap;
    private File saveDir;

    private DailySignInManager() {
        this.signInDataMap = new ConcurrentHashMap<>();
    }

    public void init(World world) {
        // TODO: v1.6.3 实现
    }

    public boolean canSignIn(UUID playerId) {
        // TODO: v1.6.3 实现
        return false;
    }

    public boolean signIn(UUID playerId) {
        // TODO: v1.6.3 实现
        return false;
    }

    public int calculateNewConsecutive(String lastDate, String today) {
        // TODO: v1.6.3 实现
        return 1;
    }

    public void grantTierReward(UUID playerId, SignInRewardTier tier) {
        // TODO: v1.6.3 实现
    }

    public void checkDailyReset() {
        // TODO: v1.6.3 实现
    }

    public void performMonthlyReset() {
        // TODO: v1.6.3 实现
    }

    public DailySignInData getSignInData(UUID playerId) {
        // TODO: v1.6.3 实现
        return signInDataMap.get(playerId);
    }

    public void saveSignInData(UUID playerId) {
        // TODO: v1.6.3 实现
    }

    public void loadSignInData(UUID playerId) {
        // TODO: v1.6.3 实现
    }

    public void unloadSignInData(UUID playerId) {
        // TODO: v1.6.3 实现
    }

    public void saveAll() {
        // TODO: v1.6.3 实现
    }

    public static String getToday() {
        return new SimpleDateFormat("yyyy-MM-dd").format(new Date());
    }

    public static String getYesterday() {
        // TODO: v1.6.3 实现
        return null;
    }

    public static String getYearMonth() {
        return new SimpleDateFormat("yyyy-MM").format(new Date());
    }

    public void adminSetConsecutiveDays(UUID playerId, int days) {
        // TODO: v1.6.3 实现
    }
}

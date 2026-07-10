package com.miaokatze.gtit.achievement;

/**
 * 成就分类枚举
 */
public enum AchievementCategory {

    SIGN_IN("签到", 0),
    LOTTERY("抽奖", 1),
    TRADE("交易", 2),
    SPECIAL("特殊", 3);

    private final String displayName;
    private final int tabId;

    AchievementCategory(String displayName, int tabId) {
        this.displayName = displayName;
        this.tabId = tabId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getTabId() {
        return tabId;
    }

    public static AchievementCategory fromString(String name) {
        // TODO: v1.6.5 实现
        return SPECIAL;
    }

    public static AchievementCategory fromTabId(int tabId) {
        // TODO: v1.6.5 实现
        return SPECIAL;
    }
}

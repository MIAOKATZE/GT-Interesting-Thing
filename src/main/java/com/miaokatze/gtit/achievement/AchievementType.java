package com.miaokatze.gtit.achievement;

/**
 * 成就类型枚举
 */
public enum AchievementType {

    COUNTER,
    MILESTONE,
    ONE_SHOT,
    BQ_LINKED;

    public static AchievementType fromString(String name) {
        // TODO: v1.6.5 实现
        return COUNTER;
    }

    public byte toId() {
        return (byte) ordinal();
    }

    public static AchievementType fromId(byte id) {
        // TODO: v1.6.5 实现
        return COUNTER;
    }
}

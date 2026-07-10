package com.miaokatze.gtit.lottery;

import net.minecraft.util.EnumChatFormatting;

/**
 * 抽奖稀有度枚举
 */
public enum LotteryRarity {

    COMMON("普通", EnumChatFormatting.WHITE, 0xFFFFFF, 1.0),
    RARE("稀有", EnumChatFormatting.AQUA, 0x00AFFF, 1.2),
    EPIC("史诗", EnumChatFormatting.LIGHT_PURPLE, 0xAA00FF, 1.5),
    LEGENDARY("传说", EnumChatFormatting.GOLD, 0xFFAA00, 2.0);

    private final String displayName;
    private final EnumChatFormatting color;
    private final int particleColor;
    private final double pityWeight;

    LotteryRarity(String displayName, EnumChatFormatting color, int particleColor, double pityWeight) {
        this.displayName = displayName;
        this.color = color;
        this.particleColor = particleColor;
        this.pityWeight = pityWeight;
    }

    public String getDisplayName() {
        return displayName;
    }

    public EnumChatFormatting getColor() {
        return color;
    }

    public int getParticleColor() {
        return particleColor;
    }

    public double getPityWeight() {
        return pityWeight;
    }

    public static LotteryRarity fromString(String name) {
        // TODO: v1.6.4 实现
        return COMMON;
    }
}

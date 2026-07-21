package com.miaokatze.gtit.lottery;

import net.minecraft.util.EnumChatFormatting;

/**
 * 抽奖稀有度枚举
 * <p>
 * ordinal 顺序即稀有度高低（COMMON 最低 → LEGENDARY 最高），
 * 保底判定「该稀有度及以上」直接基于 ordinal 比较（{@link #isAtLeast(LotteryRarity)}）。
 * <p>
 * 与素材的语义对齐（plan/image/20260721-111956-3）：
 * <ul>
 * <li>COMMON：灰框槽位（slot_normal），无角标</li>
 * <li>RARE：灰框槽位 + 蓝角标（corner_blue）</li>
 * <li>EPIC：金框槽位（slot_rare）+ 紫角标（corner_purple）</li>
 * <li>LEGENDARY：闪角槽位（slot_epic，金=最高稀有度）+ 紫角标</li>
 * </ul>
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

    /**
     * 判断本稀有度是否达到（≥）指定稀有度
     * <p>
     * 保底「该稀有度及以上」判定用（{@code EPIC.isAtLeast(RARE) == true}）。
     *
     * @param other 目标稀有度；为 null 时视为恒真
     * @return true 表示本稀有度 ≥ 目标稀有度
     */
    public boolean isAtLeast(LotteryRarity other) {
        if (other == null) return true;
        return this.ordinal() >= other.ordinal();
    }

    /**
     * 按名称解析稀有度（大小写不敏感，容错：未知/空 → COMMON）
     * <p>
     * 供 JSON 配置反序列化使用（{@code "rarity": "epic"} → {@link #EPIC}）。
     */
    public static LotteryRarity fromString(String name) {
        if (name == null || name.isEmpty()) return COMMON;
        for (LotteryRarity rarity : values()) {
            if (rarity.name()
                .equalsIgnoreCase(name)) {
                return rarity;
            }
        }
        return COMMON;
    }
}

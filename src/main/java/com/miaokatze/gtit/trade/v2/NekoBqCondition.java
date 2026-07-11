package com.miaokatze.gtit.trade.v2;

import java.util.Objects;
import java.util.UUID;

import net.minecraft.nbt.NBTTagCompound;

/**
 * BQ 任务条件，替代 VM 的 BqCondition
 * <p>
 * 检查玩家是否完成指定 BetterQuesting 任务。
 * 实现 {@link NekoTradeCondition} 接口，通过 {@link NekoBqBridge} 查询任务状态。
 * <p>
 * NBT 序列化格式：
 * <ul>
 * <li>{@code name}：条件名称（"betterquesting"）</li>
 * <li>{@code questMsb}：UUID 最高有效位</li>
 * <li>{@code questLsb}：UUID 最低有效位</li>
 * </ul>
 */
public class NekoBqCondition implements NekoTradeCondition {

    /** 条件名称，用于工厂创建和 NBT 标识 */
    public static final String CONDITION_NAME = "betterquesting";

    /** 关联的 BQ 任务 UUID */
    private UUID questId;

    /**
     * 无参构造器，用于 NBT 反序列化
     */
    public NekoBqCondition() {}

    /**
     * 有参构造器，指定任务 UUID
     *
     * @param questId BQ 任务 UUID
     */
    public NekoBqCondition(UUID questId) {
        this.questId = questId;
    }

    @Override
    public String getConditionName() {
        return CONDITION_NAME;
    }

    /**
     * 检查指定玩家是否满足此条件
     * <p>
     * 安全回退策略：questId 或 playerId 为 null 时返回 true（不阻断交易），
     * BQ 未加载时返回 true，查询异常时返回 true。
     *
     * @param playerId 玩家 UUID
     * @return 任务已完成返回 true，未完成返回 false，异常情况返回 true（安全回退）
     */
    @Override
    public boolean isSatisfied(UUID playerId) {
        // questId 或 playerId 为 null 时安全回退
        if (questId == null || playerId == null) {
            return true;
        }
        // BQ 未加载时安全回退
        if (!NekoBqBridge.isBqLoaded()) {
            return true;
        }
        try {
            return NekoBqBridge.isQuestCompleted(playerId, questId);
        } catch (Exception e) {
            // 查询异常时安全回退
            return true;
        }
    }

    /**
     * 序列化到 NBT
     *
     * @return 包含条件名称和任务 UUID 的 NBT 标签
     */
    @Override
    public NBTTagCompound writeToNBT() {
        NBTTagCompound nbt = new NBTTagCompound();
        nbt.setString("name", CONDITION_NAME);
        nbt.setLong("questMsb", questId.getMostSignificantBits());
        nbt.setLong("questLsb", questId.getLeastSignificantBits());
        return nbt;
    }

    /**
     * 从 NBT 反序列化
     *
     * @param nbt NBT 标签
     */
    @Override
    public void loadFromNBT(NBTTagCompound nbt) {
        long msb = nbt.getLong("questMsb");
        long lsb = nbt.getLong("questLsb");
        this.questId = new UUID(msb, lsb);
    }

    /**
     * 获取任务 UUID
     *
     * @return BQ 任务 UUID
     */
    public UUID getQuestId() {
        return questId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        NekoBqCondition that = (NekoBqCondition) o;
        return Objects.equals(questId, that.questId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(questId);
    }
}

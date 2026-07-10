package com.miaokatze.gtit.trade.v2;

import java.util.UUID;

import net.minecraft.nbt.NBTTagCompound;

/**
 * BQ 任务条件，替代 VM 的 BqCondition
 * <p>
 * 检查玩家是否完成指定 BetterQuesting 任务。
 * 实现 {@link NekoTradeCondition} 接口，通过 {@link NekoBqBridge} 查询任务状态。
 */
public class NekoBqCondition implements NekoTradeCondition {

    private String questId;

    public NekoBqCondition() {
        // TODO: v1.6.1 实现
    }

    public NekoBqCondition(String questId) {
        // TODO: v1.6.1 实现
        this.questId = questId;
    }

    @Override
    public String getConditionName() {
        return "bq_quest";
    }

    @Override
    public boolean isSatisfied(UUID playerId) {
        // TODO: v1.6.1 实现，调用 NekoBqBridge.isQuestCompleted
        return false;
    }

    @Override
    public NBTTagCompound writeToNBT() {
        // TODO: v1.6.1 实现
        return null;
    }

    @Override
    public void loadFromNBT(NBTTagCompound nbt) {
        // TODO: v1.6.1 实现
    }

    public String getQuestId() {
        return questId;
    }
}

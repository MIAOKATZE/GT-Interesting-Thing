package com.miaokatze.gtit.trade.v2;

import java.util.UUID;

import net.minecraft.nbt.NBTTagCompound;

/**
 * 交易条件接口，替代 VM 的 ICondition
 * <p>
 * 定义交易前置条件的统一接口，支持 NBT 序列化。
 * 实现类如 {@link NekoBqCondition} 用于检查 BQ 任务完成状态。
 */
public interface NekoTradeCondition {

    /**
     * 获取条件名称（用于标识和序列化）
     *
     * @return 条件名称字符串
     */
    String getConditionName();

    /**
     * 检查指定玩家是否满足此条件
     *
     * @param playerId 玩家 UUID
     * @return 满足返回 true
     */
    boolean isSatisfied(UUID playerId);

    /**
     * 序列化到 NBT
     *
     * @return NBT 标签化合物
     */
    NBTTagCompound writeToNBT();

    /**
     * 从 NBT 反序列化
     *
     * @param nbt NBT 标签化合物
     */
    void loadFromNBT(NBTTagCompound nbt);
}

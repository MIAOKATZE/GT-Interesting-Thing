package com.miaokatze.gtit.trade.v2;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * 交易条件工厂，根据条件名称创建对应条件实例。
 * <p>
 * 用于 {@link NekoTradeGroup} 的 NBT 反序列化，根据 NBT 中存储的 {@code name} 字段
 * 创建对应的 {@link NekoTradeCondition} 实现类实例。
 * <p>
 * 支持的条件类型：
 * <ul>
 * <li>{@code betterquesting} → {@link NekoBqCondition}</li>
 * </ul>
 */
public class NekoConditionFactory {

    /** 统一 logger（O2-B02 去中心化：与主类同用 "gtit" logger 名，日志过滤口径不变） */
    private static final Logger LOG = LogManager.getLogger("gtit");

    /** BQ 任务条件类型名称 */
    public static final String CONDITION_BQ = "betterquesting";

    /**
     * 私有构造器，工具类不应实例化
     */
    private NekoConditionFactory() {}

    /**
     * 根据条件名称创建条件实例
     * <p>
     * 返回的实例为空构造（未设置具体参数），调用方需随后调用
     * {@link NekoTradeCondition#loadFromNBT(NBTTagCompound)} 填充数据。
     *
     * @param conditionName 条件名称（对应 {@link NekoTradeCondition#getConditionName()}）
     * @return 条件实例，未知类型返回 null
     */
    public static NekoTradeCondition createCondition(String conditionName) {
        if (conditionName == null || conditionName.isEmpty()) {
            return null;
        }
        switch (conditionName) {
            case CONDITION_BQ:
                return new NekoBqCondition();
            default:
                LOG.warn("未知的交易条件类型: {}", conditionName);
                return null;
        }
    }
}

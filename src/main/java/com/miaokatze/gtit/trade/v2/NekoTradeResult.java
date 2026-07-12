package com.miaokatze.gtit.trade.v2;

/**
 * 交易执行结果
 * <p>
 * 封装交易执行后的状态和可选的附加消息。
 * 通过工厂方法创建实例，避免外部直接构造。
 */
public class NekoTradeResult {

    /**
     * 交易结果状态枚举
     */
    public enum Status {
        /** 交易成功 */
        SUCCESS,
        /** 交易组未找到 */
        TRADE_GROUP_NOT_FOUND,
        /** 交易索引越界 */
        TRADE_INDEX_OUT_OF_BOUNDS,
        /** 冷却中 */
        ON_COOLDOWN,
        /** 已达最大交易次数 */
        MAX_TRADES_REACHED,
        /** 前置条件未满足 */
        CONDITION_NOT_SATISFIED,
        /** 猫猫币不足 */
        INSUFFICIENT_CURRENCY,
        /** 输入物品不足 */
        INSUFFICIENT_ITEMS,
        /** 输出槽已满 */
        OUTPUT_FULL,
        /** 机器未成型 */
        NOT_FORMED
    }

    private final Status status;
    private final String message;

    private NekoTradeResult(Status status, String message) {
        this.status = status;
        this.message = message;
    }

    /**
     * 创建成功结果
     *
     * @return 成功结果实例
     */
    public static NekoTradeResult success() {
        return new NekoTradeResult(Status.SUCCESS, null);
    }

    /**
     * 创建失败结果（无附加消息）
     *
     * @param status 失败状态
     * @return 失败结果实例
     */
    public static NekoTradeResult fail(Status status) {
        return new NekoTradeResult(status, null);
    }

    /**
     * 创建失败结果（带附加消息）
     *
     * @param status  失败状态
     * @param message 附加消息
     * @return 失败结果实例
     */
    public static NekoTradeResult fail(Status status, String message) {
        return new NekoTradeResult(status, message);
    }

    /**
     * 是否交易成功
     *
     * @return 成功返回 true
     */
    public boolean isSuccess() {
        return status == Status.SUCCESS;
    }

    public Status getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }
}

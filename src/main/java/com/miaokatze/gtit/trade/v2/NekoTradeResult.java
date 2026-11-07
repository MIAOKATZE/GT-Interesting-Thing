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
        NOT_FORMED,
        /** 冷却 -1 的一次性兑换已成功领取 */
        ONE_TIME_REDEEMED
    }

    private final Status status;
    private final String message;

    private NekoTradeResult(Status status, String message) {
        this.status = status;
        this.message = message != null && message.length() > 1024 ? message.substring(0, 1000) + "…请查看交易需求清单" : message;
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

    /** Shared server-side explanation for the status snapshot and the actual click result. */
    public String getDisplayMessage() {
        if (message != null && !message.isEmpty()) return message;
        switch (status) {
            case SUCCESS:
                return "交易成功";
            case TRADE_GROUP_NOT_FOUND:
                return "交易组不存在";
            case TRADE_INDEX_OUT_OF_BOUNDS:
                return "交易索引越界";
            case ON_COOLDOWN:
                return "冷却中，请稍后再试";
            case ONE_TIME_REDEEMED:
                return "已兑换（仅限一次）";
            case MAX_TRADES_REACHED:
                return "已达到最大交易次数";
            case CONDITION_NOT_SATISFIED:
                return "前置条件未满足";
            case INSUFFICIENT_CURRENCY:
                return "猫猫币不足";
            case INSUFFICIENT_ITEMS:
                return "输入物品不足";
            case OUTPUT_FULL:
                return "输出总线已满";
            case NOT_FORMED:
                return "机器未成型";
            default:
                return "交易失败";
        }
    }
}

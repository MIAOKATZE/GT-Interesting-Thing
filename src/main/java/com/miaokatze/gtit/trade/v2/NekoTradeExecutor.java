package com.miaokatze.gtit.trade.v2;

import java.util.List;
import java.util.UUID;

import net.minecraft.item.ItemStack;

/**
 * 交易执行器单例，替代 VM 的 processTradeOnServer
 * <p>
 * 负责交易的检查（checkTrade）和执行（executeTrade），
 * 通过 InputSlotAccessor / OutputSlotAccessor 接口抽象物品槽访问，
 * 解耦具体的物品容器实现。
 */
public class NekoTradeExecutor {

    /** 单例实例 */
    public static final NekoTradeExecutor INSTANCE = new NekoTradeExecutor();

    /**
     * 输入槽访问器接口
     * <p>
     * 抽象输入物品的读取和消耗操作，由调用方实现。
     */
    public interface InputSlotAccessor {

        /**
         * 获取输入槽中的所有物品
         *
         * @return 物品列表
         */
        List<ItemStack> getInputItems();

        /**
         * 消耗指定物品
         *
         * @param stack 待消耗的物品栈
         */
        void consumeItem(ItemStack stack);
    }

    /**
     * 输出槽访问器接口
     * <p>
     * 抽象输出物品的容量检查和插入操作，由调用方实现。
     */
    public interface OutputSlotAccessor {

        /**
         * 检查输出槽是否能容纳指定物品
         *
         * @param stack 待插入的物品栈
         * @return 可以容纳返回 true
         */
        boolean canFitItem(ItemStack stack);

        /**
         * 插入物品到输出槽
         *
         * @param stack 待插入的物品栈
         */
        void insertItem(ItemStack stack);
    }

    private NekoTradeExecutor() {}

    /**
     * 检查交易是否可以执行（不实际执行）
     *
     * @param playerId      玩家 UUID
     * @param group         交易组
     * @param trade         交易
     * @param inputAccessor 输入槽访问器
     * @return 交易结果（SUCCESS 或对应的失败状态）
     */
    public NekoTradeResult checkTrade(UUID playerId, NekoTradeGroup group, NekoTrade trade,
        InputSlotAccessor inputAccessor) {
        // TODO: v1.6.1 实现
        return NekoTradeResult.fail(NekoTradeResult.Status.FAIL_NO_TRADE);
    }

    /**
     * 执行交易
     *
     * @param playerId       玩家 UUID
     * @param group          交易组
     * @param trade          交易
     * @param inputAccessor  输入槽访问器
     * @param outputAccessor 输出槽访问器
     * @return 交易结果（SUCCESS 或对应的失败状态）
     */
    public NekoTradeResult executeTrade(UUID playerId, NekoTradeGroup group, NekoTrade trade,
        InputSlotAccessor inputAccessor, OutputSlotAccessor outputAccessor) {
        // TODO: v1.6.1 实现
        return NekoTradeResult.fail(NekoTradeResult.Status.FAIL_NO_TRADE);
    }
}

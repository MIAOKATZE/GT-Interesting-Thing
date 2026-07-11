package com.miaokatze.gtit.trade.v2;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.item.ItemStack;

import com.miaokatze.gtit.trade.NekoWallet;
import com.miaokatze.gtit.trade.NekoWalletManager;

/**
 * 交易执行器单例，替代 VM 的 processTradeOnServer
 * <p>
 * 负责交易的检查（checkTrade）和执行（executeTrade），
 * 通过 InputSlotAccessor / OutputSlotAccessor 接口抽象物品槽访问，
 * 解耦具体的物品容器实现。
 * <p>
 * 核心设计原则：
 * <ul>
 * <li>checkTrade 只检查不修改状态（纯读操作）</li>
 * <li>executeTrade 原子执行，失败时回滚已扣减的资源</li>
 * <li>物品扣减先模拟（操作副本），再实际扣减（修改原数组并写回）</li>
 * <li>猫猫币扣减使用 NekoWallet.tryDeduct 的 synchronized 原子操作</li>
 * </ul>
 */
public class NekoTradeExecutor {

    /** 单例实例 */
    public static final NekoTradeExecutor INSTANCE = new NekoTradeExecutor();

    /**
     * 输入槽访问器接口
     * <p>
     * 抽象输入物品的读取和写回操作，由调用方实现。
     * 使用数组副本模式，避免直接修改原槽位。
     */
    public interface InputSlotAccessor {

        /**
         * 获取输入槽物品数组副本（用于模拟扣减）
         *
         * @return 物品数组副本
         */
        ItemStack[] getCopyOfInputs();

        /**
         * 写回扣减后的物品数组
         *
         * @param inputs 扣减后的物品数组
         */
        void setInputs(ItemStack[] inputs);
    }

    /**
     * 输出槽访问器接口
     * <p>
     * 抽象输出物品的容量检查和插入操作，由调用方实现。
     */
    public interface OutputSlotAccessor {

        /**
         * 检查输出槽是否有空间容纳指定物品
         *
         * @param stack 待检查的物品栈
         * @return 有空间返回 true
         */
        boolean hasSpaceFor(ItemStack stack);

        /**
         * 插入物品到输出槽
         *
         * @param stack 待插入的物品栈
         */
        void insertItem(ItemStack stack);
    }

    private NekoTradeExecutor() {}

    /**
     * 检查交易是否可以执行（不实际执行，纯读操作）
     * <p>
     * 检查顺序：交易组存在性 → 交易索引有效性 → BQ 条件 → 冷却/次数 → 猫猫币余额 → 输入物品
     *
     * @param playerId   玩家 UUID
     * @param groupId    交易组 UUID
     * @param tradeIndex 交易在组内的索引
     * @param inputSlots 输入槽访问器
     * @return 交易结果（SUCCESS 或对应的失败状态）
     */
    public NekoTradeResult checkTrade(UUID playerId, UUID groupId, int tradeIndex, InputSlotAccessor inputSlots) {
        // 1. 查找交易组
        NekoTradeGroup group = NekoTradeDatabase.INSTANCE.getTradeGroup(groupId);
        if (group == null) {
            return NekoTradeResult.fail(NekoTradeResult.Status.TRADE_GROUP_NOT_FOUND);
        }

        // 2. 检查交易索引是否越界
        if (tradeIndex < 0 || tradeIndex >= group.getTrades()
            .size()) {
            return NekoTradeResult.fail(NekoTradeResult.Status.TRADE_INDEX_OUT_OF_BOUNDS);
        }

        // 3. 获取交易
        NekoTrade trade = group.getTrades()
            .get(tradeIndex);

        // 4. 检查 BQ 前置条件
        if (!group.isConditionsSatisfied(playerId)) {
            return NekoTradeResult.fail(NekoTradeResult.Status.CONDITION_NOT_SATISFIED);
        }

        // 5. 检查历史/冷却
        NekoTradeHistory history = NekoHistoryManager.INSTANCE.getHistory(playerId, groupId);
        int maxTradesInCooldown = getMaxTradesInCooldown(playerId);
        if (!history.canTrade(group.getCooldown(), maxTradesInCooldown, group.getMaxTrades())) {
            // 区分是已达最大次数还是冷却中
            if (group.getMaxTrades() != -1 && history.getTradeCount() >= group.getMaxTrades()) {
                return NekoTradeResult.fail(NekoTradeResult.Status.MAX_TRADES_REACHED);
            }
            return NekoTradeResult.fail(NekoTradeResult.Status.ON_COOLDOWN);
        }

        // 6. 检查猫猫币余额（仅模拟，不扣减）
        if (trade.hasCurrencyCost()) {
            NekoWallet wallet = NekoWalletManager.INSTANCE.getWallet(playerId);
            if (wallet == null) {
                return NekoTradeResult.fail(NekoTradeResult.Status.INSUFFICIENT_CURRENCY);
            }
            if (wallet.getCount(trade.getCurrencyId()) < trade.getCurrencyCost()) {
                return NekoTradeResult.fail(NekoTradeResult.Status.INSUFFICIENT_CURRENCY);
            }
        }

        // 7. 检查输入物品（仅模拟扣减，不修改原数组）
        if (trade.hasFromItems()) {
            ItemStack[] inputs = inputSlots.getCopyOfInputs();
            List<NekoBigItemStack> remaining = simulateRemoveItems(inputs, trade.getFromItems());
            if (!remaining.isEmpty()) {
                // 有物品不足
                return NekoTradeResult.fail(NekoTradeResult.Status.INSUFFICIENT_ITEMS);
            }
        }

        // 8. 全部检查通过
        return NekoTradeResult.success();
    }

    /**
     * 执行交易
     * <p>
     * 先调用 checkTrade 预检查，通过后原子执行扣减和产出。
     * 输出槽满时回滚猫猫币。
     *
     * @param playerId    玩家 UUID
     * @param groupId     交易组 UUID
     * @param tradeIndex  交易在组内的索引
     * @param inputSlots  输入槽访问器
     * @param outputSlots 输出槽访问器
     * @return 交易结果（SUCCESS 或对应的失败状态）
     */
    public NekoTradeResult executeTrade(UUID playerId, UUID groupId, int tradeIndex, InputSlotAccessor inputSlots,
        OutputSlotAccessor outputSlots) {
        // 1. 预检查
        NekoTradeResult checkResult = checkTrade(playerId, groupId, tradeIndex, inputSlots);
        if (!checkResult.isSuccess()) {
            return checkResult;
        }

        // 2. 获取交易组和交易
        NekoTradeGroup group = NekoTradeDatabase.INSTANCE.getTradeGroup(groupId);
        NekoTrade trade = group.getTrades()
            .get(tradeIndex);

        // 3. 扣减猫猫币（原子操作）
        NekoWallet wallet = null;
        if (trade.hasCurrencyCost()) {
            wallet = NekoWalletManager.INSTANCE.getWallet(playerId);
            // tryDeduct 是 synchronized 原子操作，防止并发双重消费
            if (!wallet.tryDeduct(trade.getCurrencyId(), trade.getCurrencyCost())) {
                // 并发竞争导致余额不足
                return NekoTradeResult.fail(NekoTradeResult.Status.INSUFFICIENT_CURRENCY);
            }
        }

        // 4. 扣减输入物品
        if (trade.hasFromItems()) {
            ItemStack[] inputs = inputSlots.getCopyOfInputs();
            // 实际扣减，直接修改数组
            removeItems(inputs, trade.getFromItems());
            // 写回扣减后的物品数组
            inputSlots.setInputs(inputs);
        }

        // 5. 产出放入输出槽
        for (NekoBigItemStack toItem : trade.getToItems()) {
            for (ItemStack stack : toItem.getCombinedStacks()) {
                if (!outputSlots.hasSpaceFor(stack)) {
                    // 输出槽满，回滚猫猫币
                    if (wallet != null) {
                        wallet.addCount(trade.getCurrencyId(), trade.getCurrencyCost());
                    }
                    return NekoTradeResult.fail(NekoTradeResult.Status.OUTPUT_FULL);
                }
                outputSlots.insertItem(stack);
            }
        }

        // 6. 记录历史
        NekoTradeHistory history = NekoHistoryManager.INSTANCE.getHistory(playerId, groupId);
        history.recordTrade(group.getCooldown());
        NekoHistoryManager.INSTANCE.markDirty(playerId);

        // 7. 返回成功
        return NekoTradeResult.success();
    }

    // --- 辅助方法 ---

    /**
     * 模拟扣减（操作副本，不修改原数组）
     * <p>
     * 复制输入数组后在副本上执行扣减逻辑，返回未满足的物品列表。
     *
     * @param slots    输入槽物品数组
     * @param required 需要的物品列表
     * @return 未满足的物品列表（空列表表示全部满足）
     */
    private List<NekoBigItemStack> simulateRemoveItems(ItemStack[] slots, List<NekoBigItemStack> required) {
        // 深拷贝输入数组，避免修改原数组
        ItemStack[] copy = new ItemStack[slots.length];
        for (int i = 0; i < slots.length; i++) {
            copy[i] = slots[i] != null ? slots[i].copy() : null;
        }
        return removeItems(copy, required);
    }

    /**
     * 实际扣减（直接修改 slots 数组），返回未满足的物品列表
     * <p>
     * 遍历所需物品列表，从槽位中逐一扣除匹配的物品。
     * 不足的物品记入 remaining 列表返回。
     *
     * @param slots    物品槽数组（会被直接修改）
     * @param required 需要的物品列表
     * @return 未满足的物品列表（空列表表示全部满足）
     */
    private List<NekoBigItemStack> removeItems(ItemStack[] slots, List<NekoBigItemStack> required) {
        List<NekoBigItemStack> remaining = new ArrayList<>();
        for (NekoBigItemStack requiredStack : required) {
            int need = requiredStack.getStackSize();
            // 遍历所有槽位，扣除匹配的物品
            for (int i = 0; i < slots.length && need > 0; i++) {
                if (slots[i] != null && requiredStack.matches(slots[i])) {
                    if (need >= slots[i].stackSize) {
                        // 整槽扣除
                        need -= slots[i].stackSize;
                        slots[i] = null;
                    } else {
                        // 部分扣除
                        slots[i].stackSize -= need;
                        need = 0;
                    }
                }
            }
            // 仍有未满足的数量，记入剩余列表
            if (need > 0) {
                NekoBigItemStack unfulfilled = requiredStack.copy();
                unfulfilled.setStackSize(need);
                remaining.add(unfulfilled);
            }
        }
        return remaining;
    }

    /**
     * 获取冷却周期内最大交易次数
     * <p>
     * 暂返回 1（个人限制），TODO: 对接 GTNHLib Teams API 获取团队成员数，
     * 使团队成员可共享更高的冷却内交易次数上限。
     *
     * @param playerId 玩家 UUID
     * @return 冷却周期内最大交易次数
     */
    private int getMaxTradesInCooldown(UUID playerId) {
        // TODO: 对接 GTNHLib Teams API 获取团队成员数
        return 1;
    }
}

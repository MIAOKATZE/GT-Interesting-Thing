package com.miaokatze.gtit.trade.v2;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.item.ItemStack;

import com.miaokatze.gtit.trade.NekoWallet;
import com.miaokatze.gtit.trade.NekoWalletManager;
import com.miaokatze.gtit.trade.TeamDataProvider;

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
     * <p>
     * 通过 Java 8 default method 提供 ME 网络提取能力的可选扩展点：
     * 未连接 uplink hatch 的实现类无需任何改动即默认返回"无 ME 能力"，
     * 连接了 {@link com.cubefury.vendingmachine.blocks.MTEVendingUplinkHatch} 的实现类
     * 覆盖这些方法以启用 ME 网络物品/货币提取。
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

        default List<ItemStack> getMEItems() {
            return java.util.Collections.emptyList();
        }

        default List<ItemStack> getMEItems(List<NekoBigItemStack> required, boolean strict) {
            return getMEItems();
        }

        default ItemStack extractExactFromME(ItemStack stack) {
            return null;
        }

        default void refundME(ItemStack stack) {
            throw new IllegalStateException("Exact ME extraction requires compensation support");
        }

        /**
         * 检查 ME 网络中是否有足够物品（模拟提取，不实际消耗）
         * <p>
         * 默认返回 false 表示无 uplink 时无 ME 能力。
         * 由连接了 uplink hatch 的实现类覆盖。
         *
         * @param stack 待检查的物品栈（含数量）
         * @return ME 中有足够物品返回 true
         */
        default boolean canExtractFromME(ItemStack stack) {
            return false;
        }

        /**
         * 实际从 ME 网络提取物品
         * <p>
         * 默认返回 false 表示无 uplink 时无 ME 能力。
         * 由连接了 uplink hatch 的实现类覆盖。
         *
         * @param stack 待提取的物品栈（含数量）
         * @return 成功提取返回 true
         */
        default boolean extractFromME(ItemStack stack) {
            return false;
        }

        /**
         * 查询 ME 网络中指定货币 ID 的余额
         * <p>
         * 默认返回 0 表示无 uplink 时无 ME 能力。
         * 由连接了 uplink hatch 的实现类覆盖。
         * <p>
         * V2 的货币 ID（如 "neko"、"shimmeringNeko"）与 VM 的 CurrencyType
         * （dreamcraft 硬币系统）不互通，因此通过 uplink hatch 的物品提取接口
         * 反推 ME 中猫猫币物品的数量。
         *
         * @param currencyId 货币 ID
         * @return ME 网络中该货币对应的物品总数量
         */
        default int getMECurrencyAmount(String currencyId) {
            return 0;
        }

        /**
         * 从 ME 网络提取指定数量的货币
         * <p>
         * 默认返回 false 表示无 uplink 时无 ME 能力。
         * 由连接了 uplink hatch 的实现类覆盖。
         *
         * @param currencyId 货币 ID
         * @param amount     提取数量（正数）
         * @return 成功提取返回 true
         */
        default boolean tryDeductMECurrency(String currencyId, int amount) {
            return false;
        }
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

        /**
         * 回滚指定数量的已插入物品
         * <p>
         * 当交易在产出循环中途失败（OUTPUT_FULL）时，需移除本轮已通过 insertItem
         * 加入缓冲队列的物品，防止队列残留。
         *
         * @param count 要回滚的物品数量
         */
        void rollback(int count);

        /**
         * 获取当前可用输出槽位数（v1.7.8 A2 输出空间预检）
         * <p>
         * 供 checkTrade 在扣款前预检普通产物所需槽数，避免 executeTrade
         * 插入中途才发现空间不足才回滚。实现方应扣除缓冲队列已占用的虚拟槽位（防超卖）。
         * <p>
         * 默认返回 {@link Integer#MAX_VALUE} 表示"不统计/视为充足"，
         * 未覆盖的实现方保持旧行为（由 executeTrade 中途回滚兜底）。
         *
         * @return 可用槽位数（已扣除缓冲队列占位）
         */
        default int getAvailableSlotCount() {
            return Integer.MAX_VALUE;
        }

        // v1.6.28: 批次标记接口，用于控制下落时序分档

        /**
         * 标记批次开始，告知实现方本批次共将投放 count 个物品
         * <p>
         * 由 NekoTradeExecutor.executeTrade 在产出循环之前调用，
         * 实现方据此设置分档延迟（1个无间隔 / 2个6-10tick / 3-4个2-6tick / ≥5个2-6tick且每次1-2个）。
         * 默认空实现，不支持的实现方无需改动。
         *
         * @param count 本批次物品总数
         */
        default void startBatch(int count) {}

        /**
         * 标记批次结束，清理批次状态
         * <p>
         * 仅在交易失败回滚时由 executeTrade 调用以清理残留状态；
         * 成功路径下批次状态由机器的 dispenseItems 在所有物品投放完成后清理。
         * 默认空实现。
         */
        default void endBatch() {}
    }

    private NekoTradeExecutor() {}

    /**
     * 检查交易是否可以执行（不实际执行，纯读操作）
     * <p>
     * 检查顺序：交易组存在性 → 交易索引有效性 → BQ 条件 → 冷却/次数 → 猫猫币余额
     * → 输入物品 → 输出槽空间（v1.7.8 A2）
     *
     * @param playerId    玩家 UUID
     * @param groupId     交易组 UUID
     * @param tradeIndex  交易在组内的索引
     * @param inputSlots  输入槽访问器
     * @param outputSlots 输出槽访问器（v1.7.8 A2：扣款前预检输出空间）
     * @return 交易结果（SUCCESS 或对应的失败状态）
     */
    public NekoTradeResult checkTrade(UUID playerId, UUID groupId, int tradeIndex, InputSlotAccessor inputSlots,
        OutputSlotAccessor outputSlots) {
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

        NekoWallet wallet = NekoWalletManager.INSTANCE.getWallet(playerId);
        if (NekoTradeMatcher.plan(trade, wallet, inputSlots) == null) {
            return NekoTradeResult.fail(
                trade.isPureCurrencyTrade() ? NekoTradeResult.Status.INSUFFICIENT_CURRENCY
                    : NekoTradeResult.Status.INSUFFICIENT_ITEMS);
        }
        long slotsNeeded = 0;
        for (NekoBigItemStack slot : trade.getToItems()) {
            List<NekoBigItemStack> candidates = NekoTradeMatcher.outputCandidates(slot);
            if (candidates == null || candidates.isEmpty())
                return NekoTradeResult.fail(NekoTradeResult.Status.INSUFFICIENT_ITEMS);
            long minimum = Long.MAX_VALUE;
            for (NekoBigItemStack item : candidates) {
                long count = ((long) item.getStackSize() + item.getBaseStack()
                    .getMaxStackSize() - 1) / item.getBaseStack()
                        .getMaxStackSize();
                minimum = Math.min(minimum, count);
            }
            slotsNeeded += minimum;
        }
        return slotsNeeded > outputSlots.getAvailableSlotCount()
            ? NekoTradeResult.fail(NekoTradeResult.Status.OUTPUT_FULL)
            : NekoTradeResult.success();
    }

    /**
     * 执行交易
     * <p>
     * 先调用 checkTrade 预检查（v1.7.8 A2 起含输出空间预检），通过后原子执行扣减和产出。
     * 输出槽满时回滚猫猫币（预检后仅剩并发/队列堆积场景会走到中途回滚）。
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
        NekoTradeGroup group = NekoTradeDatabase.INSTANCE.getTradeGroup(groupId);
        if (group == null || tradeIndex < 0
            || tradeIndex >= group.getTrades()
                .size()) {
            return checkTrade(playerId, groupId, tradeIndex, inputSlots, outputSlots);
        }
        NekoTradeHistory history = NekoHistoryManager.INSTANCE.getHistory(playerId, groupId);
        synchronized (history) {
            // 1. 预检查（v1.7.8 A2：传入输出槽访问器，扣款前预检输出空间）
            NekoTradeResult checkResult = checkTrade(playerId, groupId, tradeIndex, inputSlots, outputSlots);
            if (!checkResult.isSuccess()) {
                return checkResult;
            }

            // 2. 获取交易组和交易
            NekoTrade trade = group.getTrades()
                .get(tradeIndex);

            List<NekoBigItemStack> rolled = NekoTradeMatcher.rollOutputs(trade, new java.util.Random());
            if (rolled == null) return NekoTradeResult.fail(NekoTradeResult.Status.INSUFFICIENT_ITEMS);
            if (!outputsFit(rolled, outputSlots.getAvailableSlotCount()))
                return NekoTradeResult.fail(NekoTradeResult.Status.OUTPUT_FULL);
            List<ItemStack> outputs = new ArrayList<>();
            for (NekoBigItemStack item : rolled) outputs.addAll(item.getCombinedStacks());
            if (outputs.size() > outputSlots.getAvailableSlotCount())
                return NekoTradeResult.fail(NekoTradeResult.Status.OUTPUT_FULL);
            for (ItemStack stack : outputs)
                if (!outputSlots.hasSpaceFor(stack)) return NekoTradeResult.fail(NekoTradeResult.Status.OUTPUT_FULL);
            NekoWallet wallet = NekoWalletManager.INSTANCE.getWallet(playerId);
            NekoTradeMatcher.Plan plan = NekoTradeMatcher.plan(trade, wallet, inputSlots);
            if (plan == null) return NekoTradeResult.fail(NekoTradeResult.Status.INSUFFICIENT_ITEMS);
            if (!meCompensationFits(plan)) return NekoTradeResult.fail(NekoTradeResult.Status.INSUFFICIENT_ITEMS);
            Map<String, Integer> walletDeducted = new LinkedHashMap<>();
            List<ItemStack> meExtracted = new ArrayList<>();
            ItemStack[] originalInputs = inputSlots.getCopyOfInputs();
            ItemStack[] inputs = inputSlots.getCopyOfInputs();
            for (int i = 0; i < plan.supplies.size(); i++) {
                int count = plan.consumed[i];
                if (count == 0) continue;
                NekoTradeMatcher.Supply supply = plan.supplies.get(i);
                boolean success = true;
                if (supply.currency != null) {
                    success = wallet != null && wallet.tryDeduct(supply.currency, count);
                    if (success) walletDeducted.put(supply.currency, count);
                } else if (supply.localIndex >= 0) {
                    ItemStack local = inputs[supply.localIndex];
                    success = local != null && local.stackSize >= count
                        && local.isItemEqual(supply.stack)
                        && ItemStack.areItemStackTagsEqual(local, supply.stack);
                    if (success) {
                        local.stackSize -= count;
                        if (local.stackSize == 0) inputs[supply.localIndex] = null;
                    }
                } else {
                    ItemStack request = supply.stack.copy();
                    request.stackSize = count;
                    ItemStack extracted = inputSlots.extractExactFromME(request);
                    if (extracted != null && extracted.stackSize > 0) meExtracted.add(extracted);
                    success = extracted != null && extracted.stackSize == count
                        && request.isItemEqual(extracted)
                        && ItemStack.areItemStackTagsEqual(request, extracted);
                }
                if (!success) {
                    rollbackWalletCurrency(wallet, walletDeducted);
                    for (ItemStack stack : meExtracted) inputSlots.refundME(stack);
                    return NekoTradeResult.fail(NekoTradeResult.Status.INSUFFICIENT_ITEMS);
                }
            }
            inputSlots.setInputs(inputs);
            if (!outputs.isEmpty()) outputSlots.startBatch(outputs.size());
            int insertedCount = 0;
            for (ItemStack stack : outputs) {
                if (!outputSlots.hasSpaceFor(stack)) {
                    rollbackWalletCurrency(wallet, walletDeducted);
                    inputSlots.setInputs(originalInputs);
                    if (insertedCount > 0) outputSlots.rollback(insertedCount);
                    for (ItemStack extracted : meExtracted) inputSlots.refundME(extracted);
                    outputSlots.endBatch();
                    return NekoTradeResult.fail(NekoTradeResult.Status.OUTPUT_FULL);
                }
                outputSlots.insertItem(stack);
                insertedCount++;
            }

            // 6. 记录历史
            history.recordTrade(group.getCooldown());
            // v1.6.28: 若有冷却，标记需要播报冷却完毕通知（由 NekoNotificationScheduler 定时检查并播报）
            if (group.getCooldown() > 0) {
                history.setNotificationQueued(true);
            }
            NekoHistoryManager.INSTANCE.markDirty(playerId);

            // O2-17 钱包落盘口径统一：写路径只管改余额（第 3 步扣款走 tryDeduct/addCount，
            // 余额变化即登记脏标记），落盘一律由周期（5 分钟）+ 登出 + 停服三重兜底承担。
            // 团队钱包由余额冲刷链（flushTeamBalance，~100ms）team.markDirty() 托管，
            // 覆盖 GTNHLib TeamDataSaver.onWorldSave 仅落 DIRTY 团队的约束；
            // 原 v1.7.6 G6⑤ 显式 saveWallet 撤除，与投币/弹出/抽奖扣费路径口径一致。

            // 7. 返回成功
            return NekoTradeResult.success();
        }
    }

    // --- 辅助方法 ---

    /** Every extracted ME stack must fit the durable compensation buffer if reinsertion fails. */
    static boolean meCompensationFits(NekoTradeMatcher.Plan plan) {
        long count = 0;
        for (int i = 0; i < plan.supplies.size(); i++) {
            NekoTradeMatcher.Supply supply = plan.supplies.get(i);
            if (supply.currency != null || supply.localIndex >= 0 || plan.consumed[i] <= 0) continue;
            int maximum = supply.stack.getMaxStackSize();
            if (maximum <= 0) return false;
            count += ((long) plan.consumed[i] + maximum - 1) / maximum;
            if (count > 4096) return false;
        }
        return true;
    }

    /** Bound concrete output expansion before allocating any ItemStacks. */
    static boolean outputsFit(List<NekoBigItemStack> rolled, int availableSlots) {
        long count = 0;
        long limit = Math.min(4096, availableSlots);
        for (NekoBigItemStack item : rolled) {
            int maximum = item.getBaseStack()
                .getMaxStackSize();
            if (maximum <= 0 || item.getStackSize() <= 0) return false;
            count += ((long) item.getStackSize() + maximum - 1) / maximum;
            if (count > limit) return false;
        }
        return true;
    }

    /**
     * 模拟扣减（操作副本，不修改原数组）
     * <p>
     * 复制输入数组后在副本上执行扣减逻辑，返回未满足的物品列表。
     *
     * @param slots     输入槽物品数组
     * @param required  需要的物品列表
     * @param recordNBT v1.7.6 G3⑤：true = 物品+NBT 精确匹配；false = 仅按物品匹配
     * @return 未满足的物品列表（空列表表示全部满足）
     */
    private List<NekoBigItemStack> simulateRemoveItems(ItemStack[] slots, List<NekoBigItemStack> required,
        boolean recordNBT) {
        // 深拷贝输入数组，避免修改原数组
        ItemStack[] copy = new ItemStack[slots.length];
        for (int i = 0; i < slots.length; i++) {
            copy[i] = slots[i] != null ? slots[i].copy() : null;
        }
        return removeItems(copy, required, recordNBT);
    }

    /**
     * 实际扣减（直接修改 slots 数组），返回未满足的物品列表
     * <p>
     * 等价于 {@link #removeItems(ItemStack[], List, boolean)} 且 recordNBT=true（保留旧行为，
     * 供未感知 recordNBT 的调用点使用，如 LotteryManager 抽奖物品消耗）。
     *
     * @param slots    物品槽数组（会被直接修改）
     * @param required 需要的物品列表
     * @return 未满足的物品列表（空列表表示全部满足）
     */
    public List<NekoBigItemStack> removeItems(ItemStack[] slots, List<NekoBigItemStack> required) {
        return removeItems(slots, required, true);
    }

    /**
     * 实际扣减（直接修改 slots 数组），返回未满足的物品列表
     * <p>
     * 遍历所需物品列表，从槽位中逐一扣除匹配的物品。
     * 不足的物品记入 remaining 列表返回。
     * <p>
     * <b>通用工具</b>：v1.7.6 起公开——交易输入扣减与抽奖物品消耗（LotteryManager 扣费分流）
     * 共用本方法；典型用法为「getCopyOfInputs → removeItems 校验 → setInputs 写回」的原子模式。
     *
     * @param slots     物品槽数组（会被直接修改）
     * @param required  需要的物品列表
     * @param recordNBT v1.7.6 G3⑤：true = 物品+NBT 精确匹配；false = 仅按物品匹配（忽略 NBT 差异）
     * @return 未满足的物品列表（空列表表示全部满足）
     */
    public List<NekoBigItemStack> removeItems(ItemStack[] slots, List<NekoBigItemStack> required, boolean recordNBT) {
        List<NekoBigItemStack> remaining = new ArrayList<>();
        for (NekoBigItemStack requiredStack : required) {
            int need = requiredStack.getStackSize();
            // 遍历所有槽位，扣除匹配的物品
            for (int i = 0; i < slots.length && need > 0; i++) {
                if (slots[i] != null && requiredStack.matches(slots[i], recordNBT)) {
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

    /** Restore wallet credits; ME compensation is handled by the exact extraction ledger separately. */
    private void rollbackWalletCurrency(NekoWallet wallet, Map<String, Integer> walletDeducted) {
        if (wallet == null) {
            return;
        }
        for (Map.Entry<String, Integer> entry : walletDeducted.entrySet()) {
            if (entry.getValue() != null && entry.getValue() > 0) {
                wallet.addCount(entry.getKey(), entry.getValue());
            }
        }
    }

    /**
     * 获取冷却周期内最大交易次数
     * <p>
     * 对接 GTNHLib Teams API 获取团队成员数，团队成员可共享更高的冷却内交易次数上限。
     * 若玩家无团队或 GTNHLib 不可用，则回退到个人限制（返回 1）。
     *
     * @param playerId 玩家 UUID
     * @return 冷却周期内最大交易次数（团队成员数，至少为 1）
     */
    private int getMaxTradesInCooldown(UUID playerId) {
        if (playerId == null) return 1;
        // O2-04：Teams 探测/降级统一走 TeamDataProvider 门面（不可用/无团队 → null → 回退 1）
        com.gtnewhorizon.gtnhlib.teams.Team team = TeamDataProvider.getTeam(playerId);
        if (team == null) return 1;
        int memberCount = team.getMembers()
            .size();
        return Math.max(1, memberCount);
    }

    /**
     * 查询指定玩家在冷却周期内的最大交易次数（团队缩放值）
     * <p>
     * 静态方法，供 GUI 层（如 NekoVMGuiV2 构建同步值时）查询团队缩放信息。
     * 内部委托给单例实例的 {@link #getMaxTradesInCooldown} 方法。
     * <p>
     * <b>调用方注意事项</b>：
     * <ul>
     * <li>此方法只能在服务端调用（GTNHLib Teams API 是服务端专属）</li>
     * <li>客户端需要通过同步值获取该值，不能直接调用此方法</li>
     * <li>经 TeamDataProvider 门面（含可用性探测），GTNHLib 不可用时安全降级返回 1</li>
     * </ul>
     *
     * @param playerId 玩家 UUID，为 null 时返回 1（个人限制）
     * @return 冷却周期内最大交易次数（团队成员数，至少为 1）
     */
    public static int getTeamMaxTrades(UUID playerId) {
        return INSTANCE.getMaxTradesInCooldown(playerId);
    }
}

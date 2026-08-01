package com.miaokatze.gtit.trade.v2;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

        // 6. 检查猫猫币余额（仅模拟，不扣减）
        // 策略：本地钱包 + ME 网络余额之和需 >= 消耗量
        // v1.7.6 G3② 货币解绑：货币需求来自 fromItems 中的猫猫币条目（getCurrencyCosts 按 ID 汇总，
        // 支持需求格混放多种货币），逐种货币独立校验
        Map<String, Integer> currencyCosts = trade.getCurrencyCosts();
        if (!currencyCosts.isEmpty()) {
            NekoWallet wallet = NekoWalletManager.INSTANCE.getWallet(playerId);
            if (wallet == null) {
                return NekoTradeResult.fail(NekoTradeResult.Status.INSUFFICIENT_CURRENCY);
            }
            for (Map.Entry<String, Integer> costEntry : currencyCosts.entrySet()) {
                int walletBalance = wallet.getCount(costEntry.getKey());
                int meBalance = inputSlots.getMECurrencyAmount(costEntry.getKey());
                if (walletBalance + meBalance < costEntry.getValue()) {
                    return NekoTradeResult.fail(NekoTradeResult.Status.INSUFFICIENT_CURRENCY);
                }
            }
        }

        // 7. 检查输入物品（本地先扣，不足部分检查 ME 是否可补足）
        // v1.7.6 G3②：仅匹配普通需求条目——猫猫币条目走第 6 步钱包/ME 货币路径，
        // 防止被当普通物品从输入槽匹配；G3⑤：匹配严格度按交易 recordNBT
        List<NekoBigItemStack> requiredItems = trade.getNonCurrencyFromItems();
        if (!requiredItems.isEmpty()) {
            ItemStack[] inputs = inputSlots.getCopyOfInputs();
            List<NekoBigItemStack> remaining = simulateRemoveItems(inputs, requiredItems, trade.isRecordNBT());
            if (!remaining.isEmpty()) {
                // 本地不足，检查 ME 网络是否能补足每个未满足的物品
                for (NekoBigItemStack unfulfilled : remaining) {
                    for (ItemStack meStack : unfulfilled.getCombinedStacks()) {
                        if (!inputSlots.canExtractFromME(meStack)) {
                            return NekoTradeResult.fail(NekoTradeResult.Status.INSUFFICIENT_ITEMS);
                        }
                    }
                }
            }
        }

        // 8. v1.7.8 A2：预检输出槽空间（纯读操作，在扣款前失败）
        // 每个产物栈固定占一个空槽（outputIntoSlot 写第一个空槽、不合并），
        // 所需槽数 = 产物栈数；v1.7.10 起猫猫币产物也落入输出槽（恢复 1.6.* 行为），
        // 预检口径为全部产物（含货币产物）
        int requiredOutputSlots = 0;
        for (NekoBigItemStack toItem : trade.getToItems()) {
            for (ItemStack stack : toItem.getCombinedStacks()) {
                if (stack != null) {
                    requiredOutputSlots++;
                }
            }
        }
        if (requiredOutputSlots > outputSlots.getAvailableSlotCount()) {
            return NekoTradeResult.fail(NekoTradeResult.Status.OUTPUT_FULL);
        }

        // 9. 全部检查通过
        return NekoTradeResult.success();
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

            // 3. 扣减猫猫币（钱包优先，不足部分从 ME 提取）
            // v1.7.6 G3② 货币解绑：货币需求来自 fromItems 猫猫币条目（getCurrencyCosts 按 ID 汇总），
            // 逐种货币独立扣款；记录每种货币的钱包/ME 扣款额，
            // 回滚时只能还原钱包部分（ME 推入下一阶段实现，已知限制）
            NekoWallet wallet = null;
            Map<String, Integer> walletDeducted = new LinkedHashMap<>();
            Map<String, Integer> meCurrencyDeducted = new LinkedHashMap<>();
            Map<String, Integer> currencyCosts = trade.getCurrencyCosts();
            if (!currencyCosts.isEmpty()) {
                wallet = NekoWalletManager.INSTANCE.getWallet(playerId);
                for (Map.Entry<String, Integer> costEntry : currencyCosts.entrySet()) {
                    String cid = costEntry.getKey();
                    int cost = costEntry.getValue();
                    int walletBalance = wallet.getCount(cid);

                    if (walletBalance >= cost) {
                        // 钱包余额充足，直接扣（synchronized 原子操作，防并发双重消费）
                        if (!wallet.tryDeduct(cid, cost)) {
                            rollbackWalletCurrency(wallet, walletDeducted);
                            return NekoTradeResult.fail(NekoTradeResult.Status.INSUFFICIENT_CURRENCY);
                        }
                        walletDeducted.put(cid, cost);
                    } else {
                        // 钱包不足，需 ME 补足。checkTrade 已经验证过总额足够，此处再读一次防并发
                        int meBalance = inputSlots.getMECurrencyAmount(cid);
                        if (walletBalance + meBalance < cost) {
                            rollbackWalletCurrency(wallet, walletDeducted);
                            return NekoTradeResult.fail(NekoTradeResult.Status.INSUFFICIENT_CURRENCY);
                        }
                        // 先扣完钱包余额
                        if (walletBalance > 0 && !wallet.tryDeduct(cid, walletBalance)) {
                            rollbackWalletCurrency(wallet, walletDeducted);
                            return NekoTradeResult.fail(NekoTradeResult.Status.INSUFFICIENT_CURRENCY);
                        }
                        // 再从 ME 提取剩余部分
                        int meNeed = cost - walletBalance;
                        if (!inputSlots.tryDeductMECurrency(cid, meNeed)) {
                            // ME 提取失败，还原本种货币钱包扣减 + 此前已扣的其他货币
                            if (walletBalance > 0) {
                                wallet.addCount(cid, walletBalance);
                            }
                            rollbackWalletCurrency(wallet, walletDeducted);
                            return NekoTradeResult.fail(NekoTradeResult.Status.INSUFFICIENT_CURRENCY);
                        }
                        if (walletBalance > 0) {
                            walletDeducted.put(cid, walletBalance);
                        }
                        meCurrencyDeducted.put(cid, meNeed);
                    }
                }
            }

            // 4. 扣减输入物品（本地先扣，不足部分从 ME 提取）
            // v1.7.6 G3②：仅扣普通需求条目——猫猫币条目已在第 3 步走货币路径，
            // 防止被当普通物品从输入槽扣除；G3⑤：匹配严格度按交易 recordNBT
            ItemStack[] originalInputs = null;
            List<NekoBigItemStack> requiredItems = trade.getNonCurrencyFromItems();
            if (!requiredItems.isEmpty()) {
                originalInputs = inputSlots.getCopyOfInputs();
                ItemStack[] inputs = inputSlots.getCopyOfInputs();
                // 本地扣减，返回未满足的物品列表
                List<NekoBigItemStack> remaining = removeItems(inputs, requiredItems, trade.isRecordNBT());
                // 写回本地扣减后的物品数组
                inputSlots.setInputs(inputs);
                // 从 ME 提取 remaining 部分
                if (!remaining.isEmpty()) {
                    for (NekoBigItemStack unfulfilled : remaining) {
                        for (ItemStack meStack : unfulfilled.getCombinedStacks()) {
                            if (!inputSlots.extractFromME(meStack)) {
                                // ME 提取失败，回滚本地扣减并还原货币（钱包部分）
                                inputSlots.setInputs(originalInputs);
                                rollbackWalletCurrency(wallet, walletDeducted);
                                // 注意：ME 扣减的货币部分无法回滚（injectItems 下一阶段实现）
                                return NekoTradeResult.fail(NekoTradeResult.Status.INSUFFICIENT_ITEMS);
                            }
                        }
                    }
                }
            }

            // v1.6.28: 计算本批次总产出物品数，通知 MTE 进入批次模式控制下落时序
            // 分档规则：1个无间隔 / 2个6-10tick / 3-4个2-6tick / ≥5个2-6tick且每次1-2个
            // v1.7.10：猫猫币产物恢复落入输出槽（1.6.* 观感），批次统计全部产物（含货币产物）
            List<NekoBigItemStack> allOutputs = trade.getToItems();
            int totalOutputCount = 0;
            for (NekoBigItemStack toItem : allOutputs) {
                for (ItemStack stack : toItem.getCombinedStacks()) {
                    totalOutputCount++;
                }
            }
            if (totalOutputCount > 0) {
                outputSlots.startBatch(totalOutputCount);
            }

            // 5. 产出放入输出槽（记录本轮插入数量以便回滚；v1.7.10 起含猫猫币产物）
            int insertedCount = 0;
            for (NekoBigItemStack toItem : allOutputs) {
                for (ItemStack stack : toItem.getCombinedStacks()) {
                    if (!outputSlots.hasSpaceFor(stack)) {
                        // 输出槽满，回滚已扣减的资源
                        // (a) 回滚猫猫币：仅还原钱包扣减部分
                        // ME 扣减部分无法回滚（injectItems 推入 ME 是下一阶段实现），
                        // 此为已知限制；但 checkTrade 已通过意味着产出空间足够，
                        // OUTPUT_FULL 仅在并发或队列堆积时发生，影响范围有限
                        rollbackWalletCurrency(wallet, walletDeducted);
                        // (b) 还原已扣减的输入物品：本地部分可还原
                        // ME 扣减的输入物品同样无法回滚，已知限制
                        if (originalInputs != null) {
                            inputSlots.setInputs(originalInputs);
                        }
                        // (c) 移除本轮已 insertItem 到 outputBuffer 的物品
                        if (insertedCount > 0) {
                            outputSlots.rollback(insertedCount);
                        }
                        // v1.6.28: 交易失败回滚，清理批次状态避免残留
                        outputSlots.endBatch();
                        return NekoTradeResult.fail(NekoTradeResult.Status.OUTPUT_FULL);
                    }
                    outputSlots.insertItem(stack);
                    insertedCount++;
                }
            }
            // v1.6.28: 批次插入完成，不调用 endBatch —— 批次状态需保留供 dispenseItems 分档控制投放时序，
            // 由 MTENekoVendingMachineV2.dispenseItems 在所有物品投放完成后调用 endBatch 清理

            // 6. 记录历史
            history.recordTrade(group.getCooldown());
            // v1.6.28: 若有冷却，标记需要播报冷却完毕通知（由 NekoNotificationScheduler 定时检查并播报）
            if (group.getCooldown() > 0) {
                history.setNotificationQueued(true);
            }
            NekoHistoryManager.INSTANCE.markDirty(playerId);

            // v1.7.6 G6⑤ 钱包落盘一致性：交易引起的货币扣减后显式持久化。
            // wallet 非空即本次交易动过钱包（第 3 步扣款；v1.7.10 起货币产物落输出槽不入钱包）；
            // 个人钱包写 gtit_neko_wallets/*.dat，团队钱包 markDirty 供 GTNHLib 世界保存落盘
            // （GTNHLib TeamDataSaver.onWorldSave 仅落 DIRTY 团队，不标脏则崩溃丢账）。
            // 与投币/弹出/抽奖扣费路径的 saveWallet 口径一致；纯物品交易 wallet==null 不触发。
            if (wallet != null) {
                NekoWalletManager.INSTANCE.saveWallet(playerId);
            }

            // 7. 返回成功
            return NekoTradeResult.success();
        }
    }

    // --- 辅助方法 ---

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

    /**
     * 回滚钱包货币扣款（v1.7.6 G3② 货币解绑）
     * <p>
     * 逐种货币还原已从钱包扣减的数量；ME 网络扣减部分无法回滚
     * （injectItems 推入 ME 是下一阶段实现，已知限制）。
     *
     * @param wallet         玩家钱包（为 null 时不操作）
     * @param walletDeducted 每种货币已从钱包扣减的数量
     */
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
        try {
            com.gtnewhorizon.gtnhlib.teams.Team team = com.gtnewhorizon.gtnhlib.teams.TeamManager
                .getTeamByPlayer(playerId);
            if (team == null) return 1;
            int memberCount = team.getMembers()
                .size();
            return Math.max(1, memberCount);
        } catch (NoClassDefFoundError e) {
            // GTNHLib Teams API 不可用，回退到个人限制
            return 1;
        } catch (Exception e) {
            com.miaokatze.gtit.main.GTInterestingThing.LOG.error("[NekoTradeExecutor] getMaxTradesInCooldown 异常", e);
            return 1;
        }
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
     * <li>带 NoClassDefFoundError 防护，GTNHLib 不可用时安全降级返回 1</li>
     * </ul>
     *
     * @param playerId 玩家 UUID，为 null 时返回 1（个人限制）
     * @return 冷却周期内最大交易次数（团队成员数，至少为 1）
     */
    public static int getTeamMaxTrades(UUID playerId) {
        return INSTANCE.getMaxTradesInCooldown(playerId);
    }
}

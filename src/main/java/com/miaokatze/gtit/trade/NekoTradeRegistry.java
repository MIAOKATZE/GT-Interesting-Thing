package com.miaokatze.gtit.trade;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.item.ItemStack;

import com.cubefury.vendingmachine.VendingMachine;
import com.cubefury.vendingmachine.integration.betterquesting.BqAdapter;
import com.cubefury.vendingmachine.integration.betterquesting.BqCondition;
import com.cubefury.vendingmachine.trade.Trade;
import com.cubefury.vendingmachine.trade.TradeCategory;
import com.cubefury.vendingmachine.trade.TradeDatabase;
import com.cubefury.vendingmachine.trade.TradeGroup;
import com.cubefury.vendingmachine.util.BigItemStack;
import com.miaokatze.gtit.main.GTInterestingThing;

/**
 * 猫猫币交易注册表
 * <p>
 * 通过反射将猫猫币交易注入 VM 的 TradeDatabase。
 * 猫猫币花费存储在 Trade.fromItems 中（因为 CurrencyItem 不支持猫猫币），
 * checkTrade 中对猫猫币物品走 NekoWallet 逻辑而非输入槽逻辑。
 * <p>
 * 交易数据从 NekoTradeConfig 加载，支持热重载。
 */
public class NekoTradeRegistry {

    // 猫猫币交易组 UUID → 猫猫币花费信息
    public static final Map<UUID, NekoTradeInfo> NEKO_TRADES = new HashMap<>();

    // 猫猫币交易组的 ID 集合（用于快速判断）
    private static final Set<UUID> NEKO_TRADE_GROUP_IDS = new HashSet<>();

    // 反射缓存：TradeDatabase.tradeGroups
    private static Field tradeGroupsField;
    // 反射缓存：TradeDatabase.tradeCategories
    private static Field tradeCategoriesField;
    // 反射缓存：TradeDatabase.noConditionTrades
    private static Field noConditionTradesField;
    // 反射缓存：TradeGroup.id
    private static Field tradeGroupIdField;
    // 反射缓存：TradeGroup.category
    private static Field tradeGroupCategoryField;

    /**
     * 猫猫币交易信息
     */
    public static class NekoTradeInfo {

        public final String currencyId; // "neko" 或 "shimmeringNeko"，无猫猫币花费时为 null
        public final int cost; // 花费数量
        public final String entryId; // 对应的 NekoTradeEntry.id
        public final int orderId; // 顺序ID（用于Smart排序）
        public final int tabId; // 标签页ID
        public final String bqQuestId; // BetterQuesting 任务绑定ID，无绑定时为空字符串

        public NekoTradeInfo(String currencyId, int cost, String entryId, int orderId, int tabId, String bqQuestId) {
            this.currencyId = currencyId;
            this.cost = cost;
            this.entryId = entryId;
            this.orderId = orderId;
            this.tabId = tabId;
            this.bqQuestId = bqQuestId != null ? bqQuestId : "";
        }
    }

    /**
     * 初始化反射字段缓存
     */
    private static void initReflection() throws Exception {
        if (tradeGroupsField != null) return;

        tradeGroupsField = TradeDatabase.class.getDeclaredField("tradeGroups");
        tradeGroupsField.setAccessible(true);

        tradeCategoriesField = TradeDatabase.class.getDeclaredField("tradeCategories");
        tradeCategoriesField.setAccessible(true);

        noConditionTradesField = TradeDatabase.class.getDeclaredField("noConditionTrades");
        noConditionTradesField.setAccessible(true);

        tradeGroupIdField = TradeGroup.class.getDeclaredField("id");
        tradeGroupIdField.setAccessible(true);

        tradeGroupCategoryField = TradeGroup.class.getDeclaredField("category");
        tradeGroupCategoryField.setAccessible(true);
    }

    /**
     * 初始化猫猫币交易
     * <p>
     * 在 CommonProxy.serverStarted() 中调用，NekoWalletManager.init() 之后。
     * 此时物品已注册完成，NekoCurrencyRegistrar 的 Item 引用可用。
     */
    public static void initialize() {
        GTInterestingThing.LOG.info("开始注册猫猫币交易...");

        try {
            initReflection();
        } catch (Exception e) {
            GTInterestingThing.LOG.error("猫猫币交易反射初始化失败!", e);
            return;
        }

        // 确保配置文件存在
        NekoTradeConfig.init();

        // 从配置加载并注册交易
        loadAndRegisterTrades();

        GTInterestingThing.LOG.info("猫猫币交易注册完成，共 {} 个交易组", NEKO_TRADES.size());
    }

    /**
     * 从 NekoTradeConfig 加载交易数据并注册
     */
    private static void loadAndRegisterTrades() {
        NekoTradeConfig.NekoTradeData data = NekoTradeConfig.load();
        List<NekoTradeEntry> trades = data.getTrades();

        int successCount = 0;
        for (NekoTradeEntry entry : trades) {
            if (registerTradeFromEntry(entry)) {
                successCount++;
            }
        }

        GTInterestingThing.LOG.info("从配置加载猫猫币交易: 共 {} 条，成功注册 {} 条", trades.size(), successCount);
    }

    /**
     * 根据 NekoTradeEntry 注册单个交易
     *
     * @return 是否注册成功
     */
    private static boolean registerTradeFromEntry(NekoTradeEntry entry) {
        try {
            // 解析交易组 ID
            UUID tradeGroupId;
            try {
                tradeGroupId = UUID.fromString(entry.getId());
            } catch (IllegalArgumentException e) {
                tradeGroupId = UUID.randomUUID();
                GTInterestingThing.LOG.warn("交易条目 id 无效 [id={}]，已生成随机 UUID: {}", entry.getId(), tradeGroupId);
            }

            // 确定 currencyId 和 cost
            String currencyId = null;
            int cost = 0;
            if (entry.getCurrency() != null) {
                currencyId = entry.getCurrency()
                    .getType();
                cost = entry.getCurrency()
                    .getAmount();
            }

            // 确定分类
            TradeCategory category = determineCategory(entry, currencyId);

            // 创建 TradeGroup
            TradeGroup tradeGroup = new TradeGroup();
            tradeGroupIdField.set(tradeGroup, tradeGroupId);
            tradeGroupCategoryField.set(tradeGroup, category);
            tradeGroup.cooldown = entry.getCooldown();
            tradeGroup.maxTrades = entry.getMaxTrades();

            // 绑定 BetterQuesting 任务条件
            // bqQuestId 支持两种格式：
            // 1. UUID 格式: "xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"
            // 2. BQ high:low 格式: "questIDHigh:questIDLow" (两个 long 值)
            if (entry.getBqQuestId() != null && !entry.getBqQuestId()
                .isEmpty() && VendingMachine.isBqLoaded) {
                UUID questId = parseBqQuestId(entry.getBqQuestId());
                if (questId != null) {
                    BqCondition bqCondition = new BqCondition(questId);
                    tradeGroup.requirementSet.add(bqCondition);
                    BqAdapter.INSTANCE.addQuestTrigger(questId, tradeGroup);
                    GTInterestingThing.LOG.info("猫猫币交易绑定任务: questId={}", questId);
                } else {
                    GTInterestingThing.LOG.warn("bqQuestId 格式无效: {}", entry.getBqQuestId());
                }
            }

            // 创建 Trade
            Trade trade = new Trade();

            // 猫猫币不再放入 trade.fromItems，只记录在 NekoTradeInfo 中
            // 原因：checkTrade 会尝试从输入槽扣除 fromItems 中的物品，
            // 但猫猫币在 NekoWallet 中不在输入槽，导致交易失败
            // 猫猫币的检查和扣减在 checkTrade 覆盖和 Mixin 中处理

            // 需求物品放入 fromItems
            if (entry.getFromItems() != null) {
                for (NekoTradeEntry.ItemEntry itemEntry : entry.getFromItems()) {
                    ItemStack fromStack = itemEntry.toItemStack();
                    if (fromStack == null) {
                        GTInterestingThing.LOG
                            .warn("交易条目 fromItems 物品转换失败，跳过 [item={}, entryId={}]", itemEntry.getItem(), entry.getId());
                        continue;
                    }
                    BigItemStack fromBigStack = new BigItemStack(fromStack);
                    trade.fromItems.add(fromBigStack);
                }
            }

            // 产物放入 toItems
            if (entry.getToItems() != null) {
                for (NekoTradeEntry.ItemEntry itemEntry : entry.getToItems()) {
                    ItemStack toStack = itemEntry.toItemStack();
                    if (toStack == null) {
                        GTInterestingThing.LOG
                            .warn("交易条目 toItems 物品转换失败，跳过 [item={}, entryId={}]", itemEntry.getItem(), entry.getId());
                        continue;
                    }
                    BigItemStack toBigStack = new BigItemStack(toStack);
                    trade.toItems.add(toBigStack);
                }
            }

            // 检查 toItems 是否为空
            if (trade.toItems.isEmpty()) {
                GTInterestingThing.LOG.error("注册猫猫币交易失败: toItems 为空 [entryId={}]", entry.getId());
                return false;
            }

            // 显示物品取 toItems[0].copy()
            trade.displayItem = trade.toItems.get(0)
                .copy();

            // 将 Trade 添加到 TradeGroup
            tradeGroup.getTrades()
                .add(trade);

            // 调试日志：确认猫猫币不在 fromItems 中
            GTInterestingThing.LOG.info(
                "[NEKO] 注册交易: id={}, fromItems.size={}, toItems.size={}, currencyId={}, cost={}",
                entry.getId(),
                trade.fromItems.size(),
                trade.toItems.size(),
                currencyId,
                cost);
            for (BigItemStack fromItem : trade.fromItems) {
                GTInterestingThing.LOG
                    .info("[NEKO]   fromItem: item={}, stackSize={}", fromItem.getBaseStack(), fromItem.stackSize);
            }

            // 注入到 TradeDatabase
            injectTradeGroup(tradeGroup, category);

            // 注入后立即验证 Trade 对象
            TradeGroup injectedTg = TradeDatabase.INSTANCE.getTradeGroupFromId(tradeGroupId);
            if (injectedTg != null && !injectedTg.getTrades()
                .isEmpty()) {
                Trade injectedTrade = injectedTg.getTrades()
                    .get(0);
                GTInterestingThing.LOG.info(
                    "[NEKO] 注入后验证: fromItems.size={}, toItems.size={}",
                    injectedTrade.fromItems.size(),
                    injectedTrade.toItems.size());
            }

            // 记录猫猫币交易信息
            NEKO_TRADE_GROUP_IDS.add(tradeGroupId);
            NEKO_TRADES.put(
                tradeGroupId,
                new NekoTradeInfo(
                    currencyId,
                    cost,
                    entry.getId(),
                    entry.getOrderId(),
                    entry.getTabId(),
                    entry.getBqQuestId()));

            GTInterestingThing.LOG.info(
                "注册猫猫币交易: {} {} → {} [分类: {}, entryId={}]",
                cost > 0 ? cost + " " + NekoCurrencyRegistrar.getDisplayName(currencyId) : "物品交换",
                "",
                trade.toItems.stream()
                    .map(
                        b -> b.stackSize + "x "
                            + b.getBaseStack()
                                .getDisplayName())
                    .reduce((a, b) -> a + ", " + b)
                    .orElse("无"),
                category.name(),
                entry.getId());
            return true;
        } catch (Exception e) {
            GTInterestingThing.LOG.error("注册猫猫币交易失败 [entryId={}]!", entry.getId(), e);
            return false;
        }
    }

    /**
     * 解析 bqQuestId 为 UUID
     * <p>
     * 支持两种格式：
     * 1. UUID 格式: "xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"
     * 2. BQ high:low 格式: "questIDHigh:questIDLow" (两个 long 值，对应 BQ 存档中的 questIDHigh/questIDLow)
     */
    public static UUID parseBqQuestIdPublic(String bqQuestId) {
        return parseBqQuestId(bqQuestId);
    }

    private static UUID parseBqQuestId(String bqQuestId) {
        if (bqQuestId == null || bqQuestId.isEmpty()) return null;

        // 尝试 BQ high:low 格式
        if (bqQuestId.contains(":")) {
            String[] parts = bqQuestId.split(":");
            if (parts.length == 2) {
                try {
                    long msb = Long.parseLong(parts[0]);
                    long lsb = Long.parseLong(parts[1]);
                    return new UUID(msb, lsb);
                } catch (NumberFormatException e) {
                    return null;
                }
            }
        }

        // 尝试 UUID 格式
        try {
            return UUID.fromString(bqQuestId);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * 确定交易的 TradeCategory
     * <p>
     * 分类规则：
     * - 有 currency 且 type="neko" → TradeCategory.MISC
     * - 有 currency 且 type="shimmeringNeko" → TradeCategory.MAGIC
     * - 无 currency 但 fromItems 含猫猫币 → 对应分类
     * - 其他 → TradeCategory.MISC
     */
    private static TradeCategory determineCategory(NekoTradeEntry entry, String currencyId) {
        if (currencyId != null) {
            if ("neko".equals(currencyId)) return TradeCategory.MISC;
            if ("shimmeringNeko".equals(currencyId)) return TradeCategory.MAGIC;
        }

        // 无 currency，检查 fromItems 中是否含猫猫币
        if (entry.getFromItems() != null) {
            for (NekoTradeEntry.ItemEntry itemEntry : entry.getFromItems()) {
                ItemStack stack = itemEntry.toItemStack();
                if (stack != null) {
                    String nekoId = NekoCurrencyRegistrar.getNekoCurrencyId(stack);
                    if (nekoId != null) {
                        if ("neko".equals(nekoId)) return TradeCategory.MISC;
                        if ("shimmeringNeko".equals(nekoId)) return TradeCategory.MAGIC;
                    }
                }
            }
        }

        return TradeCategory.MISC;
    }

    /**
     * 通过反射注入 TradeGroup 到 TradeDatabase
     * <p>
     * 注意：只有无条件交易才加入 noConditionTrades（对所有玩家全局可见）。
     * 有 BQ 条件的交易不加入 noConditionTrades，而是通过 BqAdapter 的条件满足机制
     * （addSatisfiedCondition → updateAvailableTrades → satisfiesTrade）进入 availableTrades，
     * 仅对已完成任务的玩家可见。这符合 VM 原版行为：未完成任务的交易不显示。
     * <p>
     * 猫猫机通过 NekoVendingMachineGui.updateTradeDisplay() 手动添加猫猫币交易。
     */
    @SuppressWarnings("unchecked")
    private static void injectTradeGroup(TradeGroup tradeGroup, TradeCategory category) throws Exception {
        Map<UUID, TradeGroup> tradeGroups = (Map<UUID, TradeGroup>) tradeGroupsField.get(TradeDatabase.INSTANCE);
        Map<TradeCategory, Set<UUID>> tradeCategories = (Map<TradeCategory, Set<UUID>>) tradeCategoriesField
            .get(TradeDatabase.INSTANCE);
        List<TradeGroup> noConditionTrades = (List<TradeGroup>) noConditionTradesField.get(TradeDatabase.INSTANCE);

        // 添加到 tradeGroups
        tradeGroups.put(tradeGroup.getId(), tradeGroup);

        // 添加到 tradeCategories
        tradeCategories.computeIfAbsent(category, k -> new HashSet<>());
        tradeCategories.get(category)
            .add(tradeGroup.getId());

        // 只有无条件交易才加入 noConditionTrades
        // 有 BQ 条件的交易通过 BqAdapter 条件满足机制进入 availableTrades
        if (tradeGroup.hasNoConditions()) {
            noConditionTrades.add(tradeGroup);
        }
    }

    /**
     * 热重载猫猫币交易
     * <p>
     * 先移除旧的猫猫币交易，再从配置重新加载。
     *
     * @return 是否重载成功
     */
    public static boolean reload() {
        GTInterestingThing.LOG.info("开始热重载猫猫币交易...");

        try {
            initReflection();
        } catch (Exception e) {
            GTInterestingThing.LOG.error("猫猫币交易反射初始化失败!", e);
            return false;
        }

        try {
            // 同时重载标签页配置
            NekoPageRegistry.reload();
            unregisterAllNekoTrades();
            loadAndRegisterTrades();
            GTInterestingThing.LOG.info("猫猫币交易热重载完成，共 {} 个交易组", NEKO_TRADES.size());
            return true;
        } catch (Exception e) {
            GTInterestingThing.LOG.error("猫猫币交易热重载失败!", e);
            return false;
        }
    }

    /**
     * 检查玩家是否完成了指定的 BQ 任务
     * <p>
     * 使用反射调用 BqAdapter，避免 Mixin 中直接引用 BQ 类导致类加载崩溃。
     *
     * @param bqQuestId 任务ID（支持 UUID 或 high:low 格式）
     * @param playerId  玩家 UUID
     * @return true 如果任务已完成或 BQ 未加载或检查失败（安全回退）
     */
    public static boolean checkBqQuestCompleted(String bqQuestId, UUID playerId) {
        if (!VendingMachine.isBqLoaded) return true; // BQ 未加载，不做限制
        UUID questId = parseBqQuestId(bqQuestId);
        if (questId == null) return true; // 格式无效，不做限制
        try {
            return BqAdapter.INSTANCE.checkPlayerCompletedQuest(playerId, questId);
        } catch (Exception e) {
            GTInterestingThing.LOG.warn("检查BQ任务完成状态失败: questId={}, player={}", questId, playerId, e);
            return true; // 出错时安全回退
        }
    }

    /**
     * 移除所有猫猫币交易
     */
    @SuppressWarnings("unchecked")
    private static void unregisterAllNekoTrades() throws Exception {
        Map<UUID, TradeGroup> tradeGroups = (Map<UUID, TradeGroup>) tradeGroupsField.get(TradeDatabase.INSTANCE);
        Map<TradeCategory, Set<UUID>> tradeCategories = (Map<TradeCategory, Set<UUID>>) tradeCategoriesField
            .get(TradeDatabase.INSTANCE);

        // 从 tradeGroups 移除
        for (UUID id : NEKO_TRADE_GROUP_IDS) {
            tradeGroups.remove(id);
        }

        // 从 tradeCategories 移除
        for (Map.Entry<TradeCategory, Set<UUID>> catEntry : tradeCategories.entrySet()) {
            catEntry.getValue()
                .removeAll(NEKO_TRADE_GROUP_IDS);
        }

        // 从 noConditionTrades 移除
        TradeDatabase.INSTANCE.noConditionTrades.removeIf(tg -> NEKO_TRADE_GROUP_IDS.contains(tg.getId()));

        // 清空本地记录
        NEKO_TRADES.clear();
        NEKO_TRADE_GROUP_IDS.clear();

        GTInterestingThing.LOG.info("已移除所有猫猫币交易");
    }

    /**
     * 判断 TradeGroup 是否为猫猫币交易
     */
    public static boolean isNekoTradeGroup(UUID tradeGroupId) {
        return NEKO_TRADE_GROUP_IDS.contains(tradeGroupId);
    }

    /**
     * 获取所有猫猫币交易组的 UUID 集合
     */
    public static Set<UUID> getNekoTradeGroupIds() {
        return Collections.unmodifiableSet(NEKO_TRADE_GROUP_IDS);
    }

    /**
     * 获取 TradeDatabase 中所有交易组的 UUID 集合
     * <p>
     * 用于 /gtit nekovm timereset 命令遍历所有交易组重置冷却
     */
    @SuppressWarnings("unchecked")
    public static Set<UUID> getAllTradeGroupIds() {
        try {
            initReflection();
            Map<UUID, TradeGroup> tradeGroups = (Map<UUID, TradeGroup>) tradeGroupsField.get(TradeDatabase.INSTANCE);
            return new HashSet<>(tradeGroups.keySet());
        } catch (Exception e) {
            GTInterestingThing.LOG.warn("获取所有交易组ID失败", e);
            return new HashSet<>();
        }
    }

    /**
     * 获取猫猫币交易信息
     */
    public static NekoTradeInfo getNekoTradeInfo(UUID tradeGroupId) {
        return NEKO_TRADES.get(tradeGroupId);
    }

    /**
     * 获取交易组对应的标签页ID
     */
    public static int getTabIdForTradeGroup(UUID tradeGroupId) {
        NekoTradeInfo info = NEKO_TRADES.get(tradeGroupId);
        return info != null ? info.tabId : 3; // 默认归入"GTIT"
    }

    /**
     * 获取当前所有猫猫币交易的文本描述列表
     * <p>
     * 用于 /gtit nekovm list 命令
     *
     * @return 交易描述列表，每条格式：[货币类型] 花费 → 产物 x数量
     */
    public static List<String> getTradeList() {
        List<String> result = new ArrayList<>();
        for (Map.Entry<UUID, NekoTradeInfo> entry : NEKO_TRADES.entrySet()) {
            NekoTradeInfo info = entry.getValue();
            TradeGroup tg = TradeDatabase.INSTANCE.getTradeGroupFromId(entry.getKey());
            if (tg == null) continue;

            String currencyLabel;
            if (info.currencyId != null) {
                currencyLabel = NekoCurrencyRegistrar.getDisplayName(info.currencyId);
            } else {
                currencyLabel = "物品交换";
            }

            for (Trade trade : tg.getTrades()) {
                // 构建花费描述
                String costDesc;
                if (info.currencyId != null && info.cost > 0) {
                    costDesc = info.cost + " " + currencyLabel;
                } else {
                    // 从 fromItems 构建
                    StringBuilder sb = new StringBuilder();
                    for (BigItemStack fromItem : trade.fromItems) {
                        if (sb.length() > 0) sb.append(", ");
                        sb.append(fromItem.stackSize)
                            .append("x ")
                            .append(
                                fromItem.getBaseStack()
                                    .getDisplayName());
                    }
                    costDesc = sb.length() > 0 ? sb.toString() : "免费";
                }

                // 构建产物描述
                StringBuilder toDesc = new StringBuilder();
                for (BigItemStack toItem : trade.toItems) {
                    if (toDesc.length() > 0) toDesc.append(", ");
                    toDesc.append(toItem.stackSize)
                        .append("x ")
                        .append(
                            toItem.getBaseStack()
                                .getDisplayName());
                }

                result.add(String.format("[%s] %s → %s", currencyLabel, costDesc, toDesc.toString()));
            }
        }
        return result;
    }
}

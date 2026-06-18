package com.miaokatze.gtit.trade;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

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
 */
public class NekoTradeRegistry {

    // 猫猫币交易组 UUID → 猫猫币花费信息
    private static final Map<UUID, NekoTradeInfo> NEKO_TRADES = new HashMap<>();

    // 猫猫币交易组的 ID 集合（用于快速判断）
    private static final Set<UUID> NEKO_TRADE_GROUP_IDS = new HashSet<>();

    // 反射缓存：TradeDatabase.tradeGroups
    private static Field tradeGroupsField;
    // 反射缓存：TradeDatabase.tradeCategories
    private static Field tradeCategoriesField;
    // 反射缓存：TradeGroup.id
    private static Field tradeGroupIdField;
    // 反射缓存：TradeGroup.category
    private static Field tradeGroupCategoryField;

    /**
     * 猫猫币交易信息
     */
    public static class NekoTradeInfo {

        public final String currencyId; // "neko" 或 "shimmeringNeko"
        public final int cost; // 花费数量

        public NekoTradeInfo(String currencyId, int cost) {
            this.currencyId = currencyId;
            this.cost = cost;
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

        // 注册猫猫币交易
        registerNekoTrades();

        GTInterestingThing.LOG.info("猫猫币交易注册完成，共 {} 个交易组", NEKO_TRADES.size());
    }

    /**
     * 注册猫猫币交易
     */
    private static void registerNekoTrades() {
        // 猫猫币交易（使用 MISC 分类）
        // 交易1: 10 猫猫币 → 16x 铁锭
        registerTrade("neko", 10, createBigItemStack(Items.iron_ingot, 16), TradeCategory.MISC);
        // 交易2: 30 猫猫币 → 8x 金锭
        registerTrade("neko", 30, createBigItemStack(Items.gold_ingot, 8), TradeCategory.MISC);
        // 交易3: 50 猫猫币 → 4x 钻石
        registerTrade("neko", 50, createBigItemStack(Items.diamond, 4), TradeCategory.RAW);
        // 交易4: 100 猫猫币 → 1x 下界之星
        registerTrade("neko", 100, createBigItemStack(Items.nether_star, 1), TradeCategory.RAW);

        // 闪烁猫猫币交易
        // 交易5: 5 闪烁猫猫币 → 64x 铁锭
        registerTrade("shimmeringNeko", 5, createBigItemStack(Items.iron_ingot, 64), TradeCategory.MISC);
        // 交易6: 10 闪烁猫猫币 → 32x 金锭
        registerTrade("shimmeringNeko", 10, createBigItemStack(Items.gold_ingot, 32), TradeCategory.MISC);
        // 交易7: 20 闪烁猫猫币 → 16x 钻石
        registerTrade("shimmeringNeko", 20, createBigItemStack(Items.diamond, 16), TradeCategory.RAW);
        // 交易8: 50 闪烁猫猫币 → 4x 下界之星
        registerTrade("shimmeringNeko", 50, createBigItemStack(Items.nether_star, 4), TradeCategory.RAW);
    }

    /**
     * 注册单个猫猫币交易
     * <p>
     * 创建 TradeGroup（含1个Trade），注入 TradeDatabase。
     * 猫猫币花费放入 Trade.fromItems（因为 fromCurrency 不支持猫猫币）。
     */
    private static void registerTrade(String currencyId, int cost, BigItemStack outputItem, TradeCategory category) {
        try {
            // 获取猫猫币 Item 对象
            ItemStack coinStack = NekoCurrencyRegistrar.getItemStack(currencyId, 1);
            if (coinStack == null) {
                GTInterestingThing.LOG.error("注册猫猫币交易失败: 无法获取猫猫币物品 [currencyId={}]", currencyId);
                return;
            }
            Item coinItem = coinStack.getItem();

            // 创建 TradeGroup
            TradeGroup tradeGroup = new TradeGroup();
            UUID tradeGroupId = UUID.randomUUID();
            tradeGroupIdField.set(tradeGroup, tradeGroupId);
            tradeGroupCategoryField.set(tradeGroup, category);

            // 创建 Trade
            Trade trade = new Trade();
            // 猫猫币花费放入 fromItems（因为 fromCurrency 不支持猫猫币）
            BigItemStack coinCost = new BigItemStack(coinItem, cost);
            trade.fromItems.add(coinCost);
            // 输出物品
            trade.toItems.add(outputItem);
            // 显示物品默认取 toItems[0]
            trade.displayItem = outputItem.copy();

            // 将 Trade 添加到 TradeGroup
            tradeGroup.getTrades()
                .add(trade);

            // 注入到 TradeDatabase
            injectTradeGroup(tradeGroup, category);

            // 记录猫猫币交易信息
            NEKO_TRADE_GROUP_IDS.add(tradeGroupId);
            NEKO_TRADES.put(tradeGroupId, new NekoTradeInfo(currencyId, cost));

            GTInterestingThing.LOG.info(
                "注册猫猫币交易: {} {} → {}x {} [分类: {}]",
                cost,
                NekoCurrencyRegistrar.getDisplayName(currencyId),
                outputItem.stackSize,
                outputItem.getBaseStack()
                    .getDisplayName(),
                category.name());
        } catch (Exception e) {
            GTInterestingThing.LOG.error("注册猫猫币交易失败!", e);
        }
    }

    /**
     * 通过反射注入 TradeGroup 到 TradeDatabase
     */
    @SuppressWarnings("unchecked")
    private static void injectTradeGroup(TradeGroup tradeGroup, TradeCategory category) throws Exception {
        Map<UUID, TradeGroup> tradeGroups = (Map<UUID, TradeGroup>) tradeGroupsField.get(TradeDatabase.INSTANCE);
        Map<TradeCategory, Set<UUID>> tradeCategories = (Map<TradeCategory, Set<UUID>>) tradeCategoriesField
            .get(TradeDatabase.INSTANCE);

        // 添加到 tradeGroups
        tradeGroups.put(tradeGroup.getId(), tradeGroup);

        // 添加到 tradeCategories
        tradeCategories.computeIfAbsent(category, k -> new HashSet<>());
        tradeCategories.get(category)
            .add(tradeGroup.getId());

        // 如果没有条件，添加到 noConditionTrades
        if (tradeGroup.hasNoConditions()) {
            TradeDatabase.INSTANCE.noConditionTrades.add(tradeGroup);
        }
    }

    /**
     * 判断 TradeGroup 是否为猫猫币交易
     */
    public static boolean isNekoTradeGroup(UUID tradeGroupId) {
        return NEKO_TRADE_GROUP_IDS.contains(tradeGroupId);
    }

    /**
     * 获取猫猫币交易信息
     */
    public static NekoTradeInfo getNekoTradeInfo(UUID tradeGroupId) {
        return NEKO_TRADES.get(tradeGroupId);
    }

    /**
     * 获取所有猫猫币交易信息（用于诊断）
     */
    public static Collection<NekoTradeInfo> getAllTradeInfos() {
        return NEKO_TRADES.values();
    }

    /**
     * 获取所有猫猫币交易组 ID（用于诊断）
     */
    public static Set<UUID> getAllTradeGroupIds() {
        return NEKO_TRADES.keySet();
    }

    /**
     * 创建 BigItemStack 辅助方法
     */
    private static BigItemStack createBigItemStack(Item item, int amount) {
        return new BigItemStack(item, amount);
    }
}

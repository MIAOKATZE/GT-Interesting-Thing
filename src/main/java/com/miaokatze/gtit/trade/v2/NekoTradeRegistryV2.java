package com.miaokatze.gtit.trade.v2;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.item.ItemStack;

import com.miaokatze.gtit.main.GTInterestingThing;
import com.miaokatze.gtit.trade.NekoCurrencyRegistrar;
import com.miaokatze.gtit.trade.NekoPageRegistry;
import com.miaokatze.gtit.trade.NekoTradeConfig;
import com.miaokatze.gtit.trade.NekoTradeEntry;

/**
 * 新版注册表，管理 NekoTradeDatabase
 * <p>
 * 从 NekoTradeConfig（v1 旧版配置）加载交易数据，
 * 转换为 v2 数据结构后注册到 {@link NekoTradeDatabase}。
 * 提供初始化和热重载能力。
 */
public class NekoTradeRegistryV2 {

    /**
     * 初始化注册表
     * <p>
     * 在模组加载阶段调用，加载交易配置并注册到数据库。
     */
    public static void initialize() {
        try {
            // 确保配置文件存在
            NekoTradeConfig.init();
            loadAndRegisterTrades();
            GTInterestingThing.LOG.info(
                "猫猫币交易注册完成 (V2)，共 {} 个交易组，{} 笔交易",
                NekoTradeDatabase.INSTANCE.getTradeGroupCount(),
                NekoTradeDatabase.INSTANCE.getTradeCount());
        } catch (Exception e) {
            GTInterestingThing.LOG.error("V2 交易系统初始化失败", e);
        }
    }

    /**
     * 客户端初始化（专用服务器客户端用）
     * <p>
     * 客户端同样需要加载交易配置用于 GUI 显示。
     */
    public static void initializeClient() {
        try {
            NekoTradeConfig.init();
            loadAndRegisterTrades();
            GTInterestingThing.LOG.info("V2 交易系统客户端初始化完成");
        } catch (Exception e) {
            GTInterestingThing.LOG.error("V2 交易系统客户端初始化失败", e);
        }
    }

    /**
     * 加载交易配置并注册到数据库
     * <p>
     * 读取配置文件，将每条交易配置转换为 NekoTradeGroup，
     * 注册到 {@link NekoTradeDatabase#INSTANCE}。
     */
    private static void loadAndRegisterTrades() {
        NekoTradeConfig.NekoTradeData data = NekoTradeConfig.load();
        if (data == null || data.getTrades() == null) {
            GTInterestingThing.LOG.warn("V2 交易配置为空");
            return;
        }
        int successCount = 0;
        for (NekoTradeEntry entry : data.getTrades()) {
            if (registerTradeFromEntry(entry)) {
                successCount++;
            }
        }
        GTInterestingThing.LOG.info(
            "V2 交易加载完成：{}/{} 个交易组注册成功",
            successCount,
            data.getTrades()
                .size());
    }

    /**
     * 将单个 NekoTradeEntry 转换并注册为 NekoTradeGroup
     *
     * @param entry 交易配置条目
     * @return 注册成功返回 true，失败返回 false
     */
    private static boolean registerTradeFromEntry(NekoTradeEntry entry) {
        try {
            // 1. 解析交易组ID（无效时生成随机 UUID）
            UUID groupId;
            try {
                groupId = UUID.fromString(entry.getId());
            } catch (IllegalArgumentException e) {
                groupId = UUID.randomUUID();
            }

            // 2. 解析猫猫币花费
            String currencyId = null;
            int currencyCost = 0;
            if (entry.getCurrency() != null) {
                currencyId = entry.getCurrency()
                    .getType();
                currencyCost = entry.getCurrency()
                    .getAmount();
            }

            // 3. 确定分类
            NekoTradeCategory category = determineCategory(entry, currencyId);

            // 4. 构建 NekoTrade
            NekoTrade trade = new NekoTrade();
            if (currencyId != null) {
                trade.setCurrencyId(currencyId);
                trade.setCurrencyCost(currencyCost);
            }

            // 遍历 fromItems（普通物品，不含猫猫币）
            if (entry.getFromItems() != null) {
                for (NekoTradeEntry.ItemEntry itemEntry : entry.getFromItems()) {
                    ItemStack stack = itemEntry.toItemStack();
                    if (stack != null) {
                        trade.getFromItems()
                            .add(new NekoBigItemStack(stack));
                    }
                }
            }

            // 遍历 toItems
            if (entry.getToItems() != null) {
                for (NekoTradeEntry.ItemEntry itemEntry : entry.getToItems()) {
                    ItemStack stack = itemEntry.toItemStack();
                    if (stack != null) {
                        trade.getToItems()
                            .add(new NekoBigItemStack(stack));
                    }
                }
            }

            // 检查 toItems 不为空
            if (trade.getToItems()
                .isEmpty()) {
                GTInterestingThing.LOG.warn("交易 {} 的 toItems 为空，跳过", entry.getId());
                return false;
            }

            // displayItem 取 toItems[0].copy()
            trade.setDisplayItem(
                trade.getToItems()
                    .get(0)
                    .copy());

            // 5. 构建 NekoTradeGroup
            NekoTradeGroup group = new NekoTradeGroup(groupId);
            group.setCategory(category);
            group.setCooldown(entry.getCooldown());
            group.setMaxTrades(entry.getMaxTrades());
            group.setTabId(entry.getTabId());
            group.setOrderId(entry.getOrderId());

            // BQ 绑定
            String bqQuestId = entry.getBqQuestId();
            if (bqQuestId != null && !bqQuestId.isEmpty()) {
                group.setBqQuestId(bqQuestId);
                UUID questId = NekoBqQuestIdParser.parse(bqQuestId);
                if (questId != null) {
                    // 创建 BQ 条件并添加到交易组
                    NekoBqCondition bqCondition = new NekoBqCondition(questId);
                    group.addCondition(bqCondition);
                    // 注册任务触发器（任务完成时刷新关联交易组）
                    NekoBqBridge.registerQuestTrigger(questId, groupId);
                } else {
                    GTInterestingThing.LOG.warn("交易 {} 的 BQ 任务ID格式无效: {}", entry.getId(), bqQuestId);
                }
            }

            // 添加交易到组
            group.getTrades()
                .add(trade);

            // 6. 注册到数据库
            NekoTradeDatabase.INSTANCE.addTradeGroup(group);
            return true;

        } catch (Exception e) {
            GTInterestingThing.LOG.error("注册交易失败: {}", entry.getId(), e);
            return false;
        }
    }

    /**
     * 根据货币类型确定交易分类
     *
     * @param entry      交易配置条目
     * @param currencyId 货币 ID（可能为 null）
     * @return 交易分类
     */
    private static NekoTradeCategory determineCategory(NekoTradeEntry entry, String currencyId) {
        if (NekoCurrencyRegistrar.NEKO_ID.equals(currencyId)) {
            return NekoTradeCategory.NEKO;
        } else if (NekoCurrencyRegistrar.SHIMMERING_NEKO_ID.equals(currencyId)) {
            return NekoTradeCategory.SHIMMERING_NEKO;
        }
        return NekoTradeCategory.MISC;
    }

    /**
     * 热重载交易配置
     * <p>
     * 清空数据库后重新加载，支持运行时更新交易配置。
     *
     * @return 重载成功返回 true，失败返回 false
     */
    public static boolean reload() {
        try {
            // 热重载标签页
            NekoPageRegistry.reload();
            // 清空 BQ 触发器
            NekoBqBridge.clearAllTriggers();
            // 清空数据库
            NekoTradeDatabase.INSTANCE.clear();
            // 重新加载
            loadAndRegisterTrades();
            GTInterestingThing.LOG.info("V2 交易系统热重载完成");
            return true;
        } catch (Exception e) {
            GTInterestingThing.LOG.error("V2 交易系统热重载失败", e);
            return false;
        }
    }

    /**
     * 获取所有交易的调试列表
     * <p>
     * 用于管理员命令查看当前注册的所有交易组信息。
     *
     * @return 交易信息字符串列表
     */
    public static List<String> getTradeList() {
        List<String> list = new ArrayList<>();
        for (UUID groupId : NekoTradeDatabase.INSTANCE.getAllTradeGroupIds()) {
            NekoTradeGroup group = NekoTradeDatabase.INSTANCE.getTradeGroup(groupId);
            if (group != null) {
                StringBuilder sb = new StringBuilder();
                sb.append(
                    String.format(
                        "组ID:%s tab:%d order:%d trades:%d",
                        group.getId(),
                        group.getTabId(),
                        group.getOrderId(),
                        group.getTrades()
                            .size()));
                if (group.getBqQuestId() != null && !group.getBqQuestId()
                    .isEmpty()) {
                    sb.append(" BQ:")
                        .append(group.getBqQuestId());
                }
                list.add(sb.toString());
            }
        }
        return list;
    }
}

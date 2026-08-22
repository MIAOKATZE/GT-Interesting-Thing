package com.miaokatze.gtit.trade.v2;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.item.ItemStack;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.miaokatze.gtit.currency.NekoCurrencyRegistrar;
import com.miaokatze.gtit.trade.NekoPageRegistry;
import com.miaokatze.gtit.trade.NekoTradeConfig;
import com.miaokatze.gtit.trade.NekoTradeEntry;

/**
 * 新版注册表，管理 NekoTradeDatabase（v1.7.7 G4 适配 tab 拆分结构）
 * <p>
 * 从 {@link NekoTradeConfig#load()} 读取已合并的交易数据（v1.7.7 G4 将交易按 tab
 * 拆分为 {@code config/gtit/trade/trades/tab_<id>.json}，每文件单 tab；
 * {@link NekoTradeConfig} 加载时扫描目录并合并为完整 {@link NekoTradeConfig.NekoTradeData}），
 * 转换为 v2 数据结构后注册到 {@link NekoTradeDatabase}。
 * 提供初始化和热重载能力。
 */
public class NekoTradeRegistryV2 {

    /** 统一 logger（O2-B02 去中心化：与主类同用 "gtit" logger 名，日志过滤口径不变） */
    private static final Logger LOG = LogManager.getLogger("gtit");

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
            LOG.info(
                "猫猫币交易注册完成 (V2)，共 {} 个交易组，{} 笔交易",
                NekoTradeDatabase.INSTANCE.getTradeGroupCount(),
                NekoTradeDatabase.INSTANCE.getTradeCount());
        } catch (Exception e) {
            LOG.error("V2 交易系统初始化失败", e);
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
            LOG.info("V2 交易系统客户端初始化完成");
        } catch (Exception e) {
            LOG.error("V2 交易系统客户端初始化失败", e);
        }
    }

    /**
     * 加载交易配置并注册到数据库
     * <p>
     * 调用 {@link NekoTradeConfig#load()} 获取合并后的交易数据（v1.7.7 G4 已扫描
     * {@code config/gtit/trade/trades/tab_<id>.json} 并合并），将每条交易配置转换为
     * NekoTradeGroup，注册到 {@link NekoTradeDatabase#INSTANCE}。
     */
    private static void loadAndRegisterTrades() {
        // 清空数据库，防止 initialize/initializeClient 被多次调用时重复注册交易组
        NekoTradeDatabase.INSTANCE.clear();
        registerTradesFromData(NekoTradeConfig.load());
    }

    /**
     * 将配置数据中的全部交易注册到数据库
     * <p>
     * 磁盘加载（initialize/reload）与同步包应用（applySyncedTrades）两条路径共用的注册逻辑。
     *
     * @param data 交易配置数据（null 或 trades 为 null 时仅告警）
     */
    private static void registerTradesFromData(NekoTradeConfig.NekoTradeData data) {
        if (data == null || data.getTrades() == null) {
            LOG.warn("V2 交易配置为空");
            return;
        }
        int successCount = 0;
        for (NekoTradeEntry entry : data.getTrades()) {
            if (registerTradeFromEntry(entry)) {
                successCount++;
            }
        }
        LOG.info(
            "V2 交易加载完成：{}/{} 个交易组注册成功",
            successCount,
            data.getTrades()
                .size());
    }

    /**
     * 应用服务端同步的交易配置（v1.7.0 目标 5，客户端专用）
     * <p>
     * 用同步包携带的配置数据重建内存注册表：<b>只改内存，不写盘</b>
     * （配置修改默认只在服务端进行，客户端永不在同步路径写配置文件）。
     * 清理范围与 {@link #reload()} 一致（清 BQ 触发器 + 清库），再从同步数据重建。
     * <p>
     * 单人存档下不会被调用（同步包处理器已跳过）——集成服务端与客户端共享
     * 本静态注册表，服务端侧的 initialize/reload 已直接刷新同一份数据。
     *
     * @param data 服务端同步的交易配置（{@link NekoTradeConfig#fromJson} 产物）
     */
    public static void applySyncedTrades(NekoTradeConfig.NekoTradeData data) {
        try {
            NekoBqBridge.clearAllTriggers();
            NekoTradeDatabase.INSTANCE.clear();
            registerTradesFromData(data);
            LOG.info(
                "[NekoSync] 客户端已应用同步交易配置，共 {} 个交易组，{} 笔交易",
                NekoTradeDatabase.INSTANCE.getTradeGroupCount(),
                NekoTradeDatabase.INSTANCE.getTradeCount());
        } catch (Exception e) {
            LOG.error("[NekoSync] 客户端应用同步交易配置失败", e);
        }
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

            // 2. 解析猫猫币花费（旧 JSON currency 字段，仅用于分类兜底与下方合成）
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
            // v1.7.6 G3⑤ NBT 选框：传递 recordNBT（旧 JSON 无字段时 entry 缺省 false）
            trade.setRecordNBT(entry.isRecordNBT());

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

            // v1.7.6 G3② 货币解绑：旧 JSON currency 字段加载时合成为 fromItems 货币条目
            // （追加在普通物品之后，保持普通物品相对顺序），不再写入 trade 的旧 currency 字段，
            // 统一「货币=需求格猫猫币物品」单一路径（执行器实时识别分流）
            if (currencyId != null && currencyCost > 0) {
                ItemStack currencyStack = NekoCurrencyRegistrar.getItemStack(currencyId, currencyCost);
                if (currencyStack != null) {
                    trade.getFromItems()
                        .add(new NekoBigItemStack(currencyStack));
                } else {
                    LOG.warn("交易 {} 的货币类型 {} 无法合成物品条目，已跳过", entry.getId(), currencyId);
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
                LOG.warn(
                    "交易 {} 的 toItems 为空（fromItems={}），跳过注册",
                    entry.getId(),
                    trade.getFromItems()
                        .size());
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
                    LOG.warn("交易 {} 的 BQ 任务ID格式无效: {}", entry.getId(), bqQuestId);
                }
            }

            // 添加交易到组
            group.getTrades()
                .add(trade);

            // 6. 注册到数据库
            NekoTradeDatabase.INSTANCE.addTradeGroup(group);
            return true;

        } catch (Exception e) {
            LOG.error("注册交易失败: {}", entry.getId(), e);
            return false;
        }
    }

    /**
     * 根据标签页 ID 确定交易分类
     * <p>
     * 优先使用 {@link NekoTradeEntry#getTabId()} 映射到动态分类，
     * 与 {@link com.miaokatze.gtit.trade.NekoPageRegistry} 中的标签页配置保持一致。
     * 若 entry 无有效 tabId（<=0），则按货币类型兜底映射到默认标签页：
     * neko -> tabId 1，shimmeringNeko -> tabId 2，其他 -> tabId 3。
     *
     * @param entry      交易配置条目
     * @param currencyId 货币 ID（可能为 null）
     * @return 交易分类
     */
    private static NekoTradeCategory determineCategory(NekoTradeEntry entry, String currencyId) {
        int tabId = entry.getTabId();
        if (tabId > 0) {
            return NekoTradeCategory.ofTabId(tabId);
        }

        // 无有效 tabId 时按货币类型兜底
        if (NekoCurrencyRegistrar.NEKO_ID.equals(currencyId)) {
            return NekoTradeCategory.ofTabId(1);
        } else if (NekoCurrencyRegistrar.SHIMMERING_NEKO_ID.equals(currencyId)) {
            return NekoTradeCategory.ofTabId(2);
        }
        return NekoTradeCategory.ofTabId(3);
    }

    /**
     * 热重载交易配置（v1.7.7 G4 适配 tab 拆分结构）
     * <p>
     * 清空数据库后重新调用 {@link NekoTradeConfig#load()} 扫描
     * {@code config/gtit/trade/trades/tab_<id>.json} 并合并加载，支持运行时更新交易配置。
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
            LOG.info("V2 交易系统热重载完成");
            return true;
        } catch (Exception e) {
            LOG.error("V2 交易系统热重载失败", e);
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

package com.miaokatze.gtit.gui.vm;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.StringSyncValue;
import com.miaokatze.gtit.common.machine.v2.MTENekoVendingMachineV2;
import com.miaokatze.gtit.trade.NekoPageRegistry;
import com.miaokatze.gtit.trade.v2.NekoHistoryManager;
import com.miaokatze.gtit.trade.v2.NekoTrade;
import com.miaokatze.gtit.trade.v2.NekoTradeCategory;
import com.miaokatze.gtit.trade.v2.NekoTradeDatabase;
import com.miaokatze.gtit.trade.v2.NekoTradeExecutor;
import com.miaokatze.gtit.trade.v2.NekoTradeGroup;
import com.miaokatze.gtit.trade.v2.NekoTradeHistory;
import com.miaokatze.gtit.trade.v2.NekoTradeResult;

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;

/**
 * S2C 状态字符串编解码器（A01 蓝图 G5 抽取自 NekoVMGuiV2，方法体逐字搬移）
 * <p>
 * 持有四类 S2C 状态通道（BQ 锁定/冷却/可交易/团队缩放）的同步值、客户端缓存 Map
 * 与服务端重建缓存（脏标记 + tick 窗口），build/parse 八个方法的双端编码格式不变。
 * 宿主与 {@code processTradeRequest} 等交易编排经本类的标记/通知 API 触发重算，
 * 不再直摸通道字段。
 * <p>
 * 按蓝图同批携带交易分类图标/名称辅助（{@link #getCategoryIcon}/{@link #getCategoryName}，
 * 无实例状态，静态供标签列消费）。
 * <p>
 * <b>双端镜像构建</b>：本类在服务端同样实例化与调用（v1.7.17），类内不得有客户端专属 API。
 */
public final class StatusCodec {

    /** 机器引用（checkTrade 综合判定） */
    private final MTENekoVendingMachineV2 multiblock;
    /** 基础 TE 引用（世界 tick 取时） */
    private final IGregTechTileEntity baseMetaTileEntity;
    /** 客户端收到可交易状态后请求宿主通知主面板刷新货币（mainPanel.notifyCurrencyUpdate 委托） */
    private final Runnable requestCurrencyNotify;

    /** BQ 锁定状态字符串（S2C：服务端构建 "groupId:true/false,..." 发送到客户端） */
    private StringSyncValue bqLockStatusSync;
    /** 冷却状态字符串（S2C：服务端构建 "groupId:tradeIndex:seconds,..." 发送到客户端） */
    private StringSyncValue cooldownStatusSync;
    /** 可交易状态字符串（S2C：服务端构建 "groupId:tradeIndex:true/false,..." 发送到客户端） */
    private StringSyncValue tradeableStatusSync;
    /**
     * 团队缩放状态字符串（S2C：服务端构建 "groupId:maxTrades:usedTrades,..." 发送到客户端）
     * <p>
     * 用于 tooltip 显示"冷却: X/Y 次（团队缩放）"信息。
     * 仅对有冷却（{@code group.getCooldown() > 0}）的交易组输出。
     * 客户端无法直接调用 GTNHLib Teams API，必须通过此同步值获取缩放信息。
     * </p>
     */
    private StringSyncValue teamScaleSync;

    /** BQ 锁定状态映射：groupId → 是否锁定（true=锁定） */
    private final Map<UUID, Boolean> bqLockStatusMap = new HashMap<>();
    /** 冷却状态映射："groupId:tradeIndex" → 剩余秒数 */
    private final Map<String, Long> cooldownStatusMap = new HashMap<>();
    /** 可交易状态映射："groupId:tradeIndex" → 是否可交易（true=可交易） */
    private final Map<String, Boolean> tradeableStatusMap = new HashMap<>();

    /**
     * 团队缩放状态映射：groupId → [maxTrades, usedTrades]
     * <p>
     * 由服务端通过团队缩放同步值同步到客户端。
     * 存储每个有冷却的交易组的团队缩放信息（冷却内最大次数和已用次数），
     * 用于 tooltip 显示"冷却: X/Y 次（团队缩放）"。
     * </p>
     */
    private final Map<UUID, long[]> teamScaleMap = new HashMap<>();

    /** 可交易状态字符串缓存（服务端，减少频繁调用 checkTrade） */
    private String cachedTradeableStatusString = "";
    /** 可交易状态是否需要重新计算（服务端，依赖项变化时置 true） */
    private boolean tradeableStatusDirty = true;
    /** 上次重建可交易状态字符串时的世界 tick */
    private long lastTradeableStatusRebuildTick = -1;
    /** 可交易状态缓存有效 tick 数（20 tick = 1 秒） */
    private static final long TRADEABLE_STATUS_CACHE_TICKS = 20;

    public StatusCodec(MTENekoVendingMachineV2 multiblock, IGregTechTileEntity baseMetaTileEntity,
        Runnable requestCurrencyNotify) {
        this.multiblock = multiblock;
        this.baseMetaTileEntity = baseMetaTileEntity;
        this.requestCurrencyNotify = requestCurrencyNotify;
    }

    /**
     * 注册四类 S2C 状态通道（A01 蓝图 G5 分域下沉，注册体逐字搬移）
     *
     * @param syncManager 面板同步管理器
     * @param playerId    玩家 UUID（服务端查询用）
     */
    public void registerSyncValues(PanelSyncManager syncManager, UUID playerId) {
        // --- BQ 锁定状态（S2C，同步所有交易组的状态）---
        bqLockStatusSync = new StringSyncValue(
            () -> buildBqLockStatusString(playerId),
            val -> { parseBqLockStatus(val); });
        syncManager.syncValue("nekoV2BqLockStatus", bqLockStatusSync);

        // --- 冷却状态（S2C，同步所有交易组的状态）---
        cooldownStatusSync = new StringSyncValue(
            () -> buildCooldownStatusString(playerId),
            val -> { parseCooldownStatus(val); });
        syncManager.syncValue("nekoV2CooldownStatus", cooldownStatusSync);

        // --- 可交易状态（S2C，服务端综合 BQ/冷却/钱包/输入物品计算）---
        tradeableStatusSync = new StringSyncValue(() -> buildTradeableStatusString(playerId), val -> {
            parseTradeableStatus(val);
            // 服务端同步值到达后通知主面板刷新，确保客户端立即应用新的可交易状态
            requestCurrencyNotify.run();
        });
        syncManager.syncValue("nekoV2TradeableStatus", tradeableStatusSync);

        // --- 团队缩放状态（S2C，同步冷却内最大次数和已用次数，用于 tooltip 展示）---
        // 客户端无法直接调用 GTNHLib Teams API，必须通过此同步值获取缩放信息
        teamScaleSync = new StringSyncValue(
            () -> buildTeamScaleString(playerId),
            val -> { parseTeamScaleString(val); });
        syncManager.syncValue("nekoV2TeamScale", teamScaleSync);

        // BQ 锁定/冷却状态变化时也需要重新计算可交易状态
        if (!syncManager.isClient()) {
            Runnable tradeableStatusDirtyMarker = () -> {
                markTradeableStatusDirtyAndNotify();
                // 冷却状态变化意味着交易已执行或冷却已重置，团队缩放信息（已用次数）也需更新
                if (teamScaleSync != null) {
                    teamScaleSync.notifyUpdate();
                }
            };
            bqLockStatusSync.setChangeListener(tradeableStatusDirtyMarker);
            cooldownStatusSync.setChangeListener(tradeableStatusDirtyMarker);
        }
    }

    /**
     * 交易编排钩子：标记可交易状态为脏并立即触发重同步
     * <p>
     * 收编宿主重复出现的「tradeableStatusDirty = true; tradeableStatusSync.notifyUpdate()」两行式。
     */
    public void markTradeableStatusDirtyAndNotify() {
        tradeableStatusDirty = true;
        if (tradeableStatusSync != null) {
            tradeableStatusSync.notifyUpdate();
        }
    }

    /**
     * 交易编排钩子：交易成功后的状态通道级联刷新
     * <p>
     * 可交易置脏 + 可交易/冷却/团队缩放三通道 notifyUpdate（原 processTradeRequest 成功段级联收编）。
     */
    public void notifyTradeStatusChanged() {
        markTradeableStatusDirtyAndNotify();
        if (cooldownStatusSync != null) {
            cooldownStatusSync.notifyUpdate();
        }
        // 交易成功后冷却内已用次数发生变化，需同步团队缩放信息
        if (teamScaleSync != null) {
            teamScaleSync.notifyUpdate();
        }
    }

    /** 客户端读：BQ 锁定状态（缺省 false=未锁定） */
    public boolean getBqLocked(UUID groupId) {
        return bqLockStatusMap.getOrDefault(groupId, false);
    }

    /** 客户端读：冷却剩余秒数（"groupId:tradeIndex" 键） */
    public long getCooldownRemaining(String key) {
        Long remaining = cooldownStatusMap.get(key);
        return remaining != null && remaining > 0L ? remaining : 0L;
    }

    /** 客户端读：可交易状态（"groupId:tradeIndex" 键，null=未同步） */
    public Boolean getTradeable(String key) {
        return tradeableStatusMap.get(key);
    }

    /** 客户端读：团队缩放 [maxTrades, usedTrades]（null=未同步） */
    public long[] getTeamScale(UUID groupId) {
        return teamScaleMap.get(groupId);
    }

    // ==================== BQ 锁定状态编解码 ====================

    /**
     * 构建 BQ 锁定状态字符串（服务端）
     * <p>
     * 遍历所有交易组，检查前置条件是否满足。
     *
     * @param playerId 玩家 UUID
     * @return BQ 锁定状态字符串
     */
    private String buildBqLockStatusString(UUID playerId) {
        if (playerId == null) return "";
        StringBuilder sb = new StringBuilder();
        for (NekoTradeGroup group : NekoTradeDatabase.INSTANCE.getAllTradeGroups()
            .values()) {
            if (sb.length() > 0) sb.append(",");
            sb.append(
                group.getId()
                    .toString())
                .append(":")
                .append(!group.isConditionsSatisfied(playerId));
        }
        return sb.toString();
    }

    /**
     * 解析 BQ 锁定状态字符串（客户端）
     *
     * @param status BQ 锁定状态字符串
     */
    private void parseBqLockStatus(String status) {
        bqLockStatusMap.clear();
        if (status == null || status.isEmpty()) return;
        String[] entries = status.split(",");
        for (String entry : entries) {
            String[] parts = entry.split(":");
            if (parts.length == 2) {
                try {
                    UUID groupId = UUID.fromString(parts[0]);
                    boolean locked = Boolean.parseBoolean(parts[1]);
                    bqLockStatusMap.put(groupId, locked);
                } catch (Exception ignored) {}
            }
        }
    }

    // ==================== 冷却状态编解码 ====================

    /**
     * 构建冷却状态字符串（服务端）
     * <p>
     * 遍历所有交易组，获取冷却剩余时间。
     *
     * @param playerId 玩家 UUID
     * @return 冷却状态字符串
     */
    private String buildCooldownStatusString(UUID playerId) {
        if (playerId == null) return "";
        StringBuilder sb = new StringBuilder();
        for (NekoTradeGroup group : NekoTradeDatabase.INSTANCE.getAllTradeGroups()
            .values()) {
            int cooldown = group.getCooldown();
            if (cooldown <= 0) continue;

            NekoTradeHistory history = NekoHistoryManager.INSTANCE.getHistory(playerId, group.getId());
            long remaining = history.getCooldownRemaining(cooldown);

            if (remaining > 0) {
                for (int i = 0; i < group.getTrades()
                    .size(); i++) {
                    if (sb.length() > 0) sb.append(",");
                    sb.append(
                        group.getId()
                            .toString())
                        .append(":")
                        .append(i)
                        .append(":")
                        .append(remaining);
                }
            }
        }
        return sb.toString();
    }

    /**
     * 解析冷却状态字符串（客户端）
     *
     * @param status 冷却状态字符串
     */
    private void parseCooldownStatus(String status) {
        cooldownStatusMap.clear();
        if (status == null || status.isEmpty()) return;
        String[] entries = status.split(",");
        for (String entry : entries) {
            String[] parts = entry.split(":");
            if (parts.length == 3) {
                try {
                    String key = parts[0] + ":" + parts[1];
                    long seconds = Long.parseLong(parts[2]);
                    cooldownStatusMap.put(key, seconds);
                } catch (Exception ignored) {}
            }
        }
    }

    // ==================== 可交易状态编解码（服务端综合计算 BQ/冷却/钱包/输入物品） ====================

    /**
     * 构建可交易状态字符串（服务端）
     * <p>
     * 遍历所有交易组及其交易，调用 {@link MTENekoVendingMachineV2#checkTrade} 综合判断
     * BQ 锁定、冷却、钱包余额和输入物品是否满足，生成格式字符串：
     * {@code "groupId:tradeIndex:true,groupId:tradeIndex:false,..."}。
     * <p>
     * 为降低性能开销，使用缓存策略：仅在脏标记为 true
     * 或距离上次重建超过 {@link #TRADEABLE_STATUS_CACHE_TICKS} tick 时重新计算。
     *
     * @param playerId 玩家 UUID
     * @return 可交易状态字符串
     */
    private String buildTradeableStatusString(UUID playerId) {
        if (playerId == null) return "";

        long currentTick = (baseMetaTileEntity != null && baseMetaTileEntity.getWorld() != null)
            ? baseMetaTileEntity.getWorld()
                .getTotalWorldTime()
            : lastTradeableStatusRebuildTick;

        // 未脏且仍在缓存有效期内，直接返回缓存值
        if (!tradeableStatusDirty && currentTick - lastTradeableStatusRebuildTick < TRADEABLE_STATUS_CACHE_TICKS) {
            return cachedTradeableStatusString;
        }

        StringBuilder sb = new StringBuilder();
        Map<UUID, NekoTradeGroup> groups = NekoTradeDatabase.INSTANCE.getAllTradeGroups();
        if (groups != null) {
            for (NekoTradeGroup group : groups.values()) {
                if (group == null) continue;
                List<NekoTrade> trades = group.getTrades();
                if (trades == null) continue;
                for (int i = 0; i < trades.size(); i++) {
                    NekoTradeResult result = multiblock.checkTrade(playerId, group.getId(), i);
                    boolean tradeable = result != null && result.isSuccess();
                    if (sb.length() > 0) sb.append(",");
                    sb.append(
                        group.getId()
                            .toString())
                        .append(":")
                        .append(i)
                        .append(":")
                        .append(tradeable);
                }
            }
        }

        cachedTradeableStatusString = sb.toString();
        tradeableStatusDirty = false;
        lastTradeableStatusRebuildTick = currentTick;
        return cachedTradeableStatusString;
    }

    /**
     * 解析可交易状态字符串（客户端）
     * <p>
     * 将 {@code "groupId:tradeIndex:true/false,..."} 解析为
     * 可交易状态 Map："groupId:tradeIndex" → boolean。
     *
     * @param status 可交易状态字符串
     */
    private void parseTradeableStatus(String status) {
        tradeableStatusMap.clear();
        if (status == null || status.isEmpty()) return;
        String[] entries = status.split(",");
        for (String entry : entries) {
            String[] parts = entry.split(":");
            if (parts.length == 3) {
                try {
                    String key = parts[0] + ":" + parts[1];
                    boolean tradeable = Boolean.parseBoolean(parts[2]);
                    tradeableStatusMap.put(key, tradeable);
                } catch (Exception ignored) {}
            }
        }
    }

    // ==================== 团队缩放状态编解码（S2C，用于 tooltip 冷却缩放展示） ====================

    /**
     * 构建团队缩放状态字符串（服务端）
     * <p>
     * 遍历所有有冷却（{@code cooldown > 0}）的交易组，通过
     * {@link NekoTradeExecutor#getTeamMaxTrades(UUID)} 获取团队缩放值（冷却内最大次数），
     * 通过 {@link NekoTradeHistory#getCooldownTradeCount()} 获取当前冷却周期内已用次数。
     * <p>
     * 字符串格式：{@code "groupId:maxTrades:usedTrades,groupId:maxTrades:usedTrades,..."}
     * <p>
     * <b>设计说明</b>：客户端无法直接调用 GTNHLib Teams API（服务端专属），
     * 因此通过此同步值将缩放信息传递到客户端，用于 tooltip 显示。
     *
     * @param playerId 玩家 UUID
     * @return 团队缩放状态字符串，无冷却交易组时返回空字符串
     */
    private String buildTeamScaleString(UUID playerId) {
        if (playerId == null) return "";
        // 获取团队缩放值（团队成员数 = 冷却内最大交易次数）
        // 同一玩家的所有交易组共享相同的缩放值，只需查询一次
        int maxTrades = NekoTradeExecutor.getTeamMaxTrades(playerId);

        StringBuilder sb = new StringBuilder();
        Map<UUID, NekoTradeGroup> groups = NekoTradeDatabase.INSTANCE.getAllTradeGroups();
        if (groups != null) {
            for (NekoTradeGroup group : groups.values()) {
                if (group == null) continue;
                // 仅对有冷却的交易组输出缩放信息
                if (group.getCooldown() <= 0) continue;

                // 查询该玩家对此交易组的冷却内已用次数
                NekoTradeHistory history = NekoHistoryManager.INSTANCE.getHistory(playerId, group.getId());
                long usedTrades = history != null ? history.getCooldownTradeCount() : 0;

                if (sb.length() > 0) sb.append(",");
                sb.append(
                    group.getId()
                        .toString())
                    .append(":")
                    .append(maxTrades)
                    .append(":")
                    .append(usedTrades);
            }
        }
        return sb.toString();
    }

    /**
     * 解析团队缩放状态字符串（客户端）
     * <p>
     * 将 {@code "groupId:maxTrades:usedTrades,..."} 解析为
     * 团队缩放 Map：groupId → [maxTrades, usedTrades]。
     *
     * @param status 团队缩放状态字符串
     */
    private void parseTeamScaleString(String status) {
        teamScaleMap.clear();
        if (status == null || status.isEmpty()) return;
        String[] entries = status.split(",");
        for (String entry : entries) {
            String[] parts = entry.split(":");
            if (parts.length == 3) {
                try {
                    UUID groupId = UUID.fromString(parts[0]);
                    long maxTrades = Long.parseLong(parts[1]);
                    long usedTrades = Long.parseLong(parts[2]);
                    teamScaleMap.put(groupId, new long[] { maxTrades, usedTrades });
                } catch (Exception ignored) {}
            }
        }
    }

    // ==================== 交易分类图标/名称辅助（蓝图随批携带，静态无状态） ====================

    /**
     * 获取交易分类的图标 ItemStack
     * <p>
     * 为每个分类返回一个直观的 ItemStack 图标，避免标签页空白：
     * <ul>
     * <li>FAVOURITES：下界之星（与 V1 星标收藏一致）</li>
     * <li>UNKNOWN：纸（占位）</li>
     * <li>动态标签页：从 {@link NekoPageRegistry#getPageIcon(int)} 获取配置图标</li>
     * </ul>
     *
     * @param category 交易分类
     * @return 图标 ItemStack
     */
    public static ItemStack getCategoryIcon(NekoTradeCategory category) {
        if (category == null) {
            return new ItemStack(Items.paper);
        }
        if (category.isFavourites()) {
            // 收藏分类：用下界之星（与 V1 星标收藏一致）
            return new ItemStack(Items.nether_star);
        }
        if (category.isUnknown()) {
            // 未知分类：用纸作为占位图标
            return new ItemStack(Items.paper);
        }

        // 动态标签页：从 NekoPageRegistry 获取配置图标
        ItemStack pageIcon = NekoPageRegistry.getPageIcon(category.getTabId());
        if (pageIcon != null && pageIcon.getItem() != null) {
            return pageIcon;
        }
        return new ItemStack(Items.paper);
    }

    /**
     * 获取交易分类的显示名称
     * <p>
     * 特殊分类使用固定名称，动态标签页优先使用 {@link NekoPageRegistry#getPageName(int)}。
     *
     * @param category 交易分类
     * @return 显示名称
     */
    public static String getCategoryName(NekoTradeCategory category) {
        if (category == null) {
            return "未知";
        }
        if (category.isFavourites()) {
            return "收藏";
        }
        if (category.isUnknown()) {
            return "未知";
        }

        // 动态标签页：使用 NekoPageRegistry 中的配置名称
        String pageName = NekoPageRegistry.getPageName(category.getTabId());
        if (pageName != null && !pageName.isEmpty() && !"未知".equals(pageName)) {
            return pageName;
        }
        return category.getKey();
    }
}

package com.miaokatze.gtit.client.gui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import org.lwjgl.input.Keyboard;

import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.ModularScreen;
import com.cleanroommc.modularui.screen.viewport.LocatedWidget;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;
import com.miaokatze.gtit.main.GTInterestingThing;
import com.miaokatze.gtit.trade.v2.NekoFavouritesTracker;
import com.miaokatze.gtit.trade.v2.NekoHistoryManager;
import com.miaokatze.gtit.trade.v2.NekoTrade;
import com.miaokatze.gtit.trade.v2.NekoTradeCategory;
import com.miaokatze.gtit.trade.v2.NekoTradeDatabase;
import com.miaokatze.gtit.trade.v2.NekoTradeGroup;
import com.miaokatze.gtit.trade.v2.NekoTradeHistory;

/**
 * V2 猫猫机 GUI 核心容器面板
 * <p>
 * 全量复刻 VM mod 的 {@code com.cubefury.vendingmachine.blocks.gui.TradeMainPanel}，
 * 但脱离 VM 依赖，使用 GTIT 本地组件和 V2 交易系统。
 * <p>
 * 核心职责（与 VM TradeMainPanel 一一对应）：
 * <ul>
 * <li>按键事件处理 - Shift/Ctrl 追踪，Ctrl 释放时强制刷新</li>
 * <li>{@code updateGui()} - 根据 Shift/Ctrl 状态选择增量更新或全量刷新</li>
 * <li>{@code formatTrades()} - 按类别分组、搜索过滤、排序（SMART/ALPHABET）</li>
 * <li>{@code updateTradeInformation()} - 增量更新现有显示数据的状态</li>
 * <li>{@code onUpdate()} - 周期性刷新、鼠标悬停项追踪</li>
 * <li>{@code dispose()} - 清理当前用户</li>
 * <li>{@code onOpen()} - 恢复之前的设置</li>
 * </ul>
 * <p>
 * <b>解耦设计</b>：通过 {@link PanelCallback} 回调接口与 GUI 控制器通信，
 * 而非直接引用 NekoVMGuiV2。这样在步骤11重构主 GUI 时可以方便集成，
 * 也允许其他 GUI 控制器复用此面板。
 * <p>
 * <b>VM 依赖适配</b>：
 * <ul>
 * <li>VM {@code TradeManager.INSTANCE.tradeData} → GTIT {@link NekoTradeDatabase#getAllTradeGroups()} 遍历</li>
 * <li>VM {@code TradeDatabase.INSTANCE.getTradeGroupFromId} → GTIT {@link NekoTradeDatabase#getTradeGroup}</li>
 * <li>VM {@code MTEVendingMachineGui} → GTIT {@link PanelCallback} 回调接口</li>
 * <li>VM {@code NetResetVMUser} → 不需要，通过 {@link PanelCallback#onDispose} 回调处理</li>
 * <li>VM {@code VMConfig} → 通过 {@link PanelCallback} 各 getter 方法提供</li>
 * </ul>
 *
 * @see NekoTradeItemDisplay
 * @see NekoTradeItemDisplayWidget
 * @see NekoTradeDatabase
 * @see NekoFavouritesTracker
 * @see PanelCallback
 */
public class NekoTradeMainPanel extends ModularPanel {

    // ==================== 回调接口 ====================

    /**
     * 面板回调接口
     * <p>
     * 由 GUI 控制器（如 NekoVMGuiV2）实现并注入到面板中，用于：
     * <ul>
     * <li>获取 GUI 状态（玩家 ID、搜索文本、显示模式、排序模式等）</li>
     * <li>获取显示中的交易 Widget 列表（用于鼠标悬停追踪）</li>
     * <li>接收事件通知（数据更新、设置恢复、用户清理）</li>
     * </ul>
     * <p>
     * 使用接口而非直接引用 GUI 类，实现面板与 GUI 控制器的解耦。
     */
    public interface PanelCallback {

        /**
         * 获取当前玩家 UUID
         * <p>
         * 用于查询收藏状态、BQ 前置条件、冷却历史等。
         *
         * @return 玩家 UUID，未初始化时返回 null
         */
        UUID getPlayerId();

        /**
         * 获取搜索栏文本
         * <p>
         * 用于交易列表的搜索过滤。
         *
         * @return 搜索文本（空字符串表示无搜索）
         */
        String getSearchText();

        /**
         * 检查搜索栏是否聚焦
         * <p>
         * 搜索栏聚焦时，拦截可打印字符的按键事件，
         * 防止触发 Shift/Ctrl 追踪逻辑。
         *
         * @return true 表示搜索栏当前聚焦
         */
        boolean isSearchBarFocused();

        /**
         * 获取当前显示模式
         *
         * @return TILE 或 LIST
         */
        NekoDisplayType getDisplayType();

        /**
         * 获取当前排序模式
         *
         * @return SMART 或 ALPHABET
         */
        NekoSortMode getSortMode();

        /**
         * 获取钱包模式
         *
         * @return PERSONAL 或 TEAM
         */
        NekoWalletMode getWalletMode();

        /**
         * 获取 GUI 刷新间隔
         * <p>
         * 每 N tick 进行一次定期刷新。对应 VM 的
         * {@code VMConfig.vendingMachineSettings.gui_refresh_interval}。
         *
         * @return 刷新间隔（tick），<=0 时使用默认值 20
         */
        int getRefreshInterval();

        /**
         * 是否为客户端侧
         *
         * @return true 表示客户端
         */
        boolean isClient();

        /**
         * 获取当前激活的交易分类
         * <p>
         * 对应 VM 的 {@code gui.getActiveTradeCategory()}，
         * 用于鼠标悬停追踪时确定在哪个分类的 Widget 列表中查找。
         *
         * @return 当前激活的交易分类
         */
        NekoTradeCategory getActiveCategory();

        /**
         * 获取指定显示模式和分类下的交易显示 Widget 列表
         * <p>
         * 用于 onUpdate() 中的鼠标悬停追踪（检测 currentSelected）。
         * 对应 VM 的 {@code gui.displayedTradesTiles} / {@code gui.displayedTradesList}。
         *
         * @param type     显示模式（TILE 或 LIST）
         * @param category 交易分类
         * @return Widget 列表（可为空列表，不应为 null）
         */
        List<NekoTradeItemDisplayWidget> getDisplayedWidgets(NekoDisplayType type, NekoTradeCategory category);

        /**
         * 获取指定交易的服务端同步可交易状态
         * <p>
         * 由服务端通过 {@code tradeableStatusSync} 同步到客户端，
         * 若该交易尚未同步则返回 null，调用方应回退到本地 BQ+冷却 逻辑。
         *
         * @param groupId    交易组 UUID
         * @param tradeIndex 交易在组内的索引
         * @return 服务端同步的可交易状态，未同步返回 null
         */
        Boolean getSyncedTradeableStatus(UUID groupId, int tradeIndex);

        /**
         * 通知交易显示数据已更新
         * <p>
         * GUI 控制器应在此方法中更新 Widget 的显示数据。
         * 对应 VM 的 {@code gui.updateTradeDisplay(trades)}。
         *
         * @param trades 按分类组织的交易显示数据
         */
        void onTradeDisplayUpdated(Map<NekoTradeCategory, List<NekoTradeItemDisplay>> trades);

        /**
         * 通知需要恢复之前的设置
         * <p>
         * 对应 VM 的 {@code gui.restorePreviousSettings()}，
         * 如恢复标签页位置、搜索文本等。
         */
        void onRestoreSettings();

        /**
         * 通知面板销毁，GUI 控制器应清理当前用户
         * <p>
         * 对应 VM 的 {@code base.resetCurrentUser(player)} +
         * {@code NetResetVMUser.sendReset(base)}。
         */
        void onDispose();

        /**
         * 检查是否有编辑覆盖层处于打开状态（v1.7.27）
         *
         * @return true 表示任意编辑器面板正在显示
         */
        boolean isEditOverlayOpen();

        /**
         * 关闭当前编辑覆盖层（v1.7.27）
         * <p>
         * 由主面板在 Esc/E 键按下时调用，统一处理编辑器关闭逻辑。
         */
        void closeEditOverlay();
    }

    // ==================== 字段 ====================

    /** Shift 键是否按下（public 供外部读取，与 VM 一致） */
    public boolean shiftHeld = false;

    /** Ctrl 键是否按下（public 供外部读取，与 VM 一致） */
    public boolean ctrlHeld = false;

    /** 回调接口实例 */
    private final PanelCallback callback;

    /** 同步管理器（用于检测初始化状态和获取玩家） */
    private final PanelSyncManager syncManager;

    /** GUI 位置数据 */
    private final PosGuiData guiData;

    /** 当前玩家 UUID（客户端延迟初始化，等同步管理器就绪后获取） */
    private UUID playerId = null;

    /** GUI 打开后的 tick 计数器，用于定期刷新判断 */
    private int ticksOpen = 0;

    /** 当前鼠标悬停的交易组 UUID（供外部读取，与 VM 一致） */
    public UUID currentSelected = null;

    /** 强制刷新标志（下次 onUpdate 时触发 updateGui） */
    private boolean forceRefresh = false;

    /** 货币更新标志（外部通知有货币变化时设置，触发强制刷新） */
    private boolean hasCurrencyUpdate = false;

    // ==================== 构造器 ====================

    /**
     * 构造猫猫机交易主面板
     * <p>
     * 对应 VM 的 {@code TradeMainPanel(name, gui, guiData, syncManager)}，
     * 将 gui 参数替换为 PanelCallback 回调接口。
     *
     * @param name        面板名称（传给 ModularPanel 父类）
     * @param callback    回调接口实例
     * @param guiData     GUI 位置数据
     * @param syncManager 同步管理器
     */
    public NekoTradeMainPanel(String name, PanelCallback callback, PosGuiData guiData, PanelSyncManager syncManager) {
        super(name);
        this.callback = callback;
        this.guiData = guiData;
        this.syncManager = syncManager;
    }

    // ==================== 按键事件处理 ====================

    /**
     * 按键按下事件
     * <p>
     * 完美复刻 VM 的 onKeyPressed 逻辑：
     * <ol>
     * <li>搜索栏聚焦时，拦截可打印字符（ASCII 32~126，排除 Delete=127），
     * 防止在搜索框输入时意外触发 Shift/Ctrl 追踪</li>
     * <li>追踪 Shift 键按下（LSHIFT/RSHIFT）</li>
     * <li>追踪 Ctrl 键按下（LCONTROL/RCONTROL）</li>
     * </ol>
     *
     * @param typedChar 输入的字符
     * @param keyCode   按键代码
     * @return true 表示事件已处理
     */
    @Override
    public boolean onKeyPressed(char typedChar, int keyCode) {
        // v1.7.27：编辑覆盖层打开时，Esc 无条件关闭，E 在无文本框聚焦时关闭。
        // 放在最前面，确保编辑器优先消费这两个按键，避免同时触发 GUI 关闭或搜索。
        if (callback.isEditOverlayOpen()) {
            if (keyCode == Keyboard.KEY_ESCAPE) {
                callback.closeEditOverlay();
                return true;
            }
            if (keyCode == Keyboard.KEY_E && !isTextFieldFocused()) {
                callback.closeEditOverlay();
                return true;
            }
        }

        // 搜索栏聚焦时拦截可打印字符，避免影响 Shift/Ctrl 状态追踪
        if (callback.isSearchBarFocused() && typedChar >= 32 && typedChar != 127) {
            return true;
        }
        // 追踪 Shift 按下
        if (keyCode == Keyboard.KEY_LSHIFT || keyCode == Keyboard.KEY_RSHIFT) {
            shiftHeld = true;
        }
        // 追踪 Ctrl 按下
        if (keyCode == Keyboard.KEY_LCONTROL || keyCode == Keyboard.KEY_RCONTROL) {
            ctrlHeld = true;
        }
        return super.onKeyPressed(typedChar, keyCode);
    }

    /**
     * 检查当前是否有文本输入框处于聚焦状态（v1.7.27）
     * <p>
     * 用于判断按 E 键时是否应该关闭编辑器；若玩家正在输入框中输入，
     * 则不应响应 E 键，避免无法输入字母 e。
     *
     * @return true 表示有文本框聚焦
     */
    private boolean isTextFieldFocused() {
        if (callback.isSearchBarFocused()) {
            return true;
        }
        LocatedWidget focused = getContext().getFocusedWidget();
        return focused != null && focused.getElement() instanceof TextFieldWidget;
    }

    /**
     * 按键释放事件
     * <p>
     * 完美复刻 VM 的 onKeyRelease 逻辑：
     * <ol>
     * <li>Shift 释放时清除 shiftHeld 标志</li>
     * <li>Ctrl 释放时清除 ctrlHeld 标志，并触发强制刷新</li>
     * </ol>
     * <p>
     * <b>为什么 Ctrl 释放要强制刷新</b>：Ctrl+Click 用于切换收藏，
     * 释放 Ctrl 后收藏状态可能已变化，需要全量刷新以更新收藏分类和排序。
     *
     * @param typedChar 输入的字符
     * @param keyCode   按键代码
     * @return true 表示事件已处理
     */
    @Override
    public boolean onKeyRelease(char typedChar, int keyCode) {
        // Shift 释放
        if (keyCode == Keyboard.KEY_LSHIFT || keyCode == Keyboard.KEY_RSHIFT) {
            shiftHeld = false;
        }
        // Ctrl 释放 - 强制刷新（收藏状态可能已变化）
        if (keyCode == Keyboard.KEY_LCONTROL || keyCode == Keyboard.KEY_RCONTROL) {
            ctrlHeld = false;
            setForceRefresh();
        }
        return super.onKeyRelease(typedChar, keyCode);
    }

    // ==================== GUI 更新 ====================

    /**
     * 更新 GUI
     * <p>
     * 完美复刻 VM 的 updateGui 逻辑，根据 Shift/Ctrl 状态选择更新策略：
     * <ul>
     * <li><b>Shift 或 Ctrl 按下时</b>：增量更新（updateTradeInformation），
     * 仅刷新现有显示数据的状态字段，不重新分组/过滤/排序</li>
     * <li><b>两者都未按下时</b>：全量刷新（formatTrades），
     * 重新从数据库构建、分组、过滤、排序</li>
     * </ul>
     * <p>
     * <b>为什么需要增量更新</b>：Shift+Click 触发交易、Ctrl+Click 切换收藏时，
     * 用户按键期间如果全量刷新会导致列表跳动（重新排序），影响用户体验。
     * 按键期间仅更新状态（如可交易→冷却中），按键释放后才全量刷新。
     */
    public void updateGui() {
        if (shiftHeld || ctrlHeld) {
            // 增量更新：仅刷新现有显示数据的状态
            updateTradeInformation(getCurrentTradeDisplayData());
        } else {
            // 全量刷新：重新从数据库构建交易显示数据
            Map<NekoTradeCategory, List<NekoTradeItemDisplay>> trades = formatTrades();
            callback.onTradeDisplayUpdated(trades);
        }
    }

    /**
     * 增量更新交易信息
     * <p>
     * 完美复刻 VM 的 updateTradeInformation 逻辑，但适配 GTIT V2 系统：
     * <ol>
     * <li>从所有分类的已收藏交易中筛选，更新 FAVOURITES 分类</li>
     * <li>遍历每个分类的交易显示数据，从数据库重新查询最新状态</li>
     * <li>更新 BQ 锁定状态、冷却剩余、可交易状态、收藏状态</li>
     * </ol>
     * <p>
     * <b>VM 适配</b>：VM 从 {@code TradeManager.INSTANCE.tradeData} 获取最新状态，
     * GTIT 没有 TradeManager，改为从 {@link NekoTradeDatabase} 和
     * {@link NekoHistoryManager} 重新查询。
     *
     * @param currentData 当前显示数据（按分类组织，从 Widget 中提取）
     */
    private void updateTradeInformation(Map<NekoTradeCategory, List<NekoTradeItemDisplay>> currentData) {
        if (currentData == null || currentData.isEmpty()) {
            return;
        }

        // 更新收藏分类（从所有分类的已收藏交易中筛选）
        List<NekoTradeItemDisplay> allTrades = new ArrayList<>();
        for (List<NekoTradeItemDisplay> list : currentData.values()) {
            if (list != null) {
                allTrades.addAll(list);
            }
        }
        currentData.put(NekoTradeCategory.FAVOURITES, filterFavouritedTrades(allTrades));

        // 遍历每个分类，更新交易状态
        for (Map.Entry<NekoTradeCategory, List<NekoTradeItemDisplay>> entry : currentData.entrySet()) {
            for (NekoTradeItemDisplay display : entry.getValue()) {
                if (display == null) {
                    continue;
                }

                // 从数据库重新查询交易组状态
                NekoTradeGroup group = NekoTradeDatabase.INSTANCE.getTradeGroup(display.getGroupId());
                if (group == null) {
                    // 交易组已不存在，标记为不可交易
                    display.setTradeable(false);
                    display.setBqLocked(true);
                    continue;
                }

                // 更新 BQ 锁定状态（前置任务是否未满足）
                boolean bqLocked = playerId != null && !group.isConditionsSatisfied(playerId);
                display.setBqLocked(bqLocked);

                // 更新冷却剩余时间
                long cooldownRemaining = 0;
                if (group.getCooldown() > 0 && playerId != null) {
                    NekoTradeHistory history = NekoHistoryManager.INSTANCE.getHistory(playerId, display.getGroupId());
                    cooldownRemaining = history.getCooldownRemaining(group.getCooldown());
                }
                display.setCooldownRemaining(cooldownRemaining);

                // 更新可交易状态（优先使用服务端同步值，未同步则回退 BQ+冷却）
                Boolean syncedTradeable = callback
                    .getSyncedTradeableStatus(display.getGroupId(), display.getTradeIndex());
                if (syncedTradeable != null) {
                    display.setTradeable(syncedTradeable);
                } else {
                    display.setTradeable(!bqLocked && cooldownRemaining <= 0);
                }

                // 更新收藏状态
                if (playerId != null) {
                    display.setFavourite(
                        NekoFavouritesTracker.INSTANCE
                            .isFavourite(playerId, display.getGroupId(), display.getTradeIndex()));
                }
            }
        }
    }

    // ==================== 交易格式化 ====================

    /**
     * 格式化交易数据
     * <p>
     * 完美复刻 VM 的 formatTrades 逻辑，但适配 GTIT V2 系统。
     * 全量从交易数据库构建交易显示数据，流程：
     * <ol>
     * <li>遍历所有交易组（{@link NekoTradeDatabase#getAllTradeGroups}）及其交易，
     * 创建 {@link NekoTradeItemDisplay}</li>
     * <li>设置收藏、BQ锁定、冷却、可交易状态</li>
     * <li>按交易分类分组（基于交易组的 {@link NekoTradeGroup#getCategory()}）</li>
     * <li>按搜索文本过滤（{@link NekoTradeItemDisplay#satisfiesSearch}）</li>
     * <li>按排序模式排序（SMART 或 ALPHABET）</li>
     * <li>构建 FAVOURITES 收藏分类（从所有分类的已收藏交易中筛选）</li>
     * </ol>
     * <p>
     * <b>VM 适配</b>：VM 从 {@code TradeManager.INSTANCE.tradeData} 遍历，
     * GTIT 从 {@link NekoTradeDatabase#getAllTradeGroups} 遍历交易组，
     * 再遍历组内交易构建显示数据。
     * <p>
     * <b>VM NEI 搜索适配</b>：VM 使用 NEI 的 {@code SearchField.getFilter()} 进行高级搜索，
     * GTIT 使用 {@link NekoTradeItemDisplay#satisfiesSearch} 的简单子串匹配。
     *
     * @return 按分类组织的交易显示数据
     */
    public Map<NekoTradeCategory, List<NekoTradeItemDisplay>> formatTrades() {
        Map<NekoTradeCategory, List<NekoTradeItemDisplay>> trades = new HashMap<>();

        NekoSortMode sortMode = callback.getSortMode();
        String searchString = callback.getSearchText();
        String searchLower = searchString != null ? searchString.toLowerCase() : "";

        try {
            // 遍历所有交易组，构建交易显示数据
            for (NekoTradeGroup group : NekoTradeDatabase.INSTANCE.getAllTradeGroups()
                .values()) {
                if (group == null) {
                    continue;
                }

                // 获取交易分类，null 时使用 UNKNOWN
                NekoTradeCategory category = group.getCategory();
                if (category == null) {
                    category = NekoTradeCategory.UNKNOWN;
                }
                trades.putIfAbsent(category, new ArrayList<>());

                // 遍历组内所有交易
                List<NekoTrade> tradeList = group.getTrades();
                for (int i = 0; i < tradeList.size(); i++) {
                    NekoTrade trade = tradeList.get(i);
                    if (trade == null) {
                        continue;
                    }

                    // 从 V2 交易数据构建显示对象
                    NekoTradeItemDisplay display = NekoTradeItemDisplay.fromNekoTrade(trade, group, i);

                    // 设置收藏状态
                    if (playerId != null) {
                        display.setFavourite(NekoFavouritesTracker.INSTANCE.isFavourite(playerId, group.getId(), i));
                    }

                    // 设置 BQ 锁定状态
                    boolean bqLocked = playerId != null && !group.isConditionsSatisfied(playerId);
                    display.setBqLocked(bqLocked);

                    // 设置冷却状态
                    long cooldownRemaining = 0;
                    if (group.getCooldown() > 0 && playerId != null) {
                        NekoTradeHistory history = NekoHistoryManager.INSTANCE.getHistory(playerId, group.getId());
                        cooldownRemaining = history.getCooldownRemaining(group.getCooldown());
                    }
                    display.setCooldownRemaining(cooldownRemaining);

                    // 设置可交易状态（优先使用服务端同步值，未同步则回退 BQ+冷却）
                    Boolean syncedTradeable = callback.getSyncedTradeableStatus(group.getId(), i);
                    if (syncedTradeable != null) {
                        display.setTradeable(syncedTradeable);
                    } else {
                        display.setTradeable(!bqLocked && cooldownRemaining <= 0);
                    }

                    // 添加到对应分类
                    trades.get(category)
                        .add(display);
                }
            }
        } catch (Throwable t) {
            GTInterestingThing.LOG.error("[NekoTradeMainPanel] formatTrades() 异常!", t);
        }

        // 按搜索文本过滤每个分类
        for (NekoTradeCategory category : trades.keySet()) {
            List<NekoTradeItemDisplay> filtered = trades.get(category)
                .stream()
                .filter(display -> display.satisfiesSearch(searchLower))
                .collect(Collectors.toList());
            trades.put(category, filtered);
        }

        // 按排序模式排序每个分类
        NekoWalletMode walletMode = callback.getWalletMode();
        for (NekoTradeCategory category : trades.keySet()) {
            List<NekoTradeItemDisplay> list = trades.get(category);
            list.sort((a, b) -> compareTrades(a, b, sortMode, walletMode));
        }

        // 构建 FAVOURITES 收藏分类（从所有分类的已收藏交易中筛选）
        List<NekoTradeItemDisplay> allTrades = new ArrayList<>();
        for (List<NekoTradeItemDisplay> list : trades.values()) {
            allTrades.addAll(list);
        }
        trades.put(NekoTradeCategory.FAVOURITES, filterFavouritedTrades(allTrades));

        return trades;
    }

    /**
     * 比较两个交易的排序顺序
     * <p>
     * 完美复刻 VM 的排序逻辑，适配 GTIT 数据结构。
     * <p>
     * 排序规则：
     * <ul>
     * <li><b>通用</b>：null 排最后；收藏的排前面</li>
     * <li><b>ALPHABET 模式</b>：按显示名称字母顺序（不区分大小写）</li>
     * <li><b>SMART 模式</b>（多级排序）：
     * <ol>
     * <li>按可交易状态排序（rank 越小越靠前）</li>
     * <li>按冷却时间排序（冷却长的靠前，方便用户看到需等待的交易）</li>
     * <li>按物品 ID 排序</li>
     * <li>按物品损伤值排序</li>
     * <li>按交易组内顺序排序</li>
     * </ol>
     * </li>
     * </ul>
     *
     * @param a          交易 A
     * @param b          交易 B
     * @param sortMode   排序模式
     * @param walletMode 钱包模式（V2 暂未使用，保留参数兼容 VM 逻辑）
     * @return 负数 A 靠前，正数 B 靠前，0 相等
     */
    private int compareTrades(NekoTradeItemDisplay a, NekoTradeItemDisplay b, NekoSortMode sortMode,
        NekoWalletMode walletMode) {
        // null 处理（null 排最后）
        if (a == null && b == null) return 0;
        if (a == null) return 1;
        if (b == null) return -1;

        // 收藏优先（收藏的排在前面）
        if (a.isFavourite() != b.isFavourite()) {
            return Boolean.compare(b.isFavourite(), a.isFavourite());
        }

        if (sortMode == NekoSortMode.ALPHABET) {
            // 字母排序：按显示名称
            return a.getDisplayItemName()
                .compareToIgnoreCase(b.getDisplayItemName());
        } else {
            // SMART 排序：多级排序

            // 1. 按可交易状态排序（rank 越小越靠前）
            int rankA = getRank(a, walletMode);
            int rankB = getRank(b, walletMode);
            if (rankA != rankB) {
                return Integer.compare(rankA, rankB);
            }

            // 2. 按冷却时间排序（冷却长的靠前）
            int cooldownCmp = Long.compare(b.getCooldownRemaining(), a.getCooldownRemaining());
            if (cooldownCmp != 0) return cooldownCmp;

            // v1.7.29 SMART 排序加入 orderId 升序
            int orderIdCmp = Integer.compare(a.getOrderId(), b.getOrderId());
            if (orderIdCmp != 0) return orderIdCmp;

            // 3. 按物品 ID 排序
            ItemStack stackA = a.getDisplayStack();
            ItemStack stackB = b.getDisplayStack();
            if (stackA != null && stackB != null) {
                int idA = Item.getIdFromItem(stackA.getItem());
                int idB = Item.getIdFromItem(stackB.getItem());
                if (idA != idB) return Integer.compare(idA, idB);

                // 4. 按物品损伤值排序
                int dmgCmp = Integer.compare(stackA.getItemDamage(), stackB.getItemDamage());
                if (dmgCmp != 0) return dmgCmp;
            }

            // 5. 按交易组内顺序排序
            return Integer.compare(a.getTradeIndex(), b.getTradeIndex());
        }
    }

    /**
     * 获取交易的排序等级
     * <p>
     * 完美复刻 VM 的 getRank 逻辑，适配 GTIT 数据结构。
     * 等级越低，排序越靠前：
     * <ul>
     * <li>1 - 可交易（无 BQ 锁定，无冷却）</li>
     * <li>3 - 冷却中（不可交易但非 BQ 锁定）</li>
     * <li>4 - 其他不可交易状态</li>
     * <li>5 - BQ 锁定（前置任务未完成，最低优先级）</li>
     * </ul>
     * <p>
     * <b>VM 适配</b>：VM 有 4 个等级（区分个人/团队可交易状态 + hasCooldown），
     * GTIT 简化为 3 个主要等级（V2 暂不支持团队钱包，tradeable 字段已综合判断）。
     * walletMode 参数保留但不使用，待 TEAM 模式启用后扩展。
     *
     * @param t          交易显示数据
     * @param walletMode 钱包模式（V2 暂未使用，保留参数兼容）
     * @return 排序等级（1=最高优先级，5=最低优先级）
     */
    private static int getRank(NekoTradeItemDisplay t, NekoWalletMode walletMode) {
        // BQ 锁定 → 最低优先级（等价于 VM 的 enabled=false）
        if (t.isBqLocked()) {
            return 5;
        }
        // 冷却中 → 次低优先级
        if (t.getCooldownRemaining() > 0) {
            return 3;
        }
        // 可交易 → 最高优先级
        if (t.isTradeable()) {
            return 1;
        }
        // 其他不可交易状态 → 中等优先级
        return 4;
    }

    // ==================== 收藏过滤 ====================

    /**
     * 从交易列表中筛选已收藏的交易
     * <p>
     * 完美复刻 VM 的 FavouritesTracker.INSTANCE.filterTrades() 逻辑。
     * VM 通过 TGID + tradeGroupOrder 匹配，
     * GTIT 通过 NekoTradeItemDisplay.isFavourite() 字段过滤。
     *
     * @param allTrades 所有交易列表
     * @return 已收藏的交易列表（不会为 null）
     */
    private List<NekoTradeItemDisplay> filterFavouritedTrades(List<NekoTradeItemDisplay> allTrades) {
        if (allTrades == null || allTrades.isEmpty()) {
            return new ArrayList<>();
        }
        return allTrades.stream()
            .filter(NekoTradeItemDisplay::isFavourite)
            .collect(Collectors.toList());
    }

    // ==================== 周期更新 ====================

    /**
     * 每帧更新
     * <p>
     * 完美复刻 VM 的 onUpdate 逻辑，适配 GTIT V2 系统。
     * 仅在客户端执行，负责：
     * <ol>
     * <li>延迟初始化玩家 UUID（等同步管理器就绪后获取）</li>
     * <li>检测货币更新并触发强制刷新</li>
     * <li>按刷新间隔或强制刷新标志触发 updateGui()</li>
     * <li>追踪鼠标悬停的交易（更新 currentSelected）</li>
     * <li>递增 tick 计数器</li>
     * </ol>
     * <p>
     * <b>VM 适配</b>：VM 中 walletMode 同步逻辑（shouldSyncWalletMode）由 GUI 控制器处理，
     * 此面板不涉及钱包模式同步。
     */
    @Override
    public void onUpdate() {
        super.onUpdate();

        // 仅客户端执行（所有 UI 刷新逻辑只在客户端运行）
        if (!callback.isClient()) {
            return;
        }

        // 延迟初始化玩家 UUID（等同步管理器就绪后从回调获取）
        if (this.playerId == null && this.syncManager.isInitialised()) {
            this.playerId = callback.getPlayerId();
        }

        // 货币更新检测（外部通过 notifyCurrencyUpdate() 通知）
        if (hasCurrencyUpdate) {
            setForceRefresh();
        }

        // 刷新判断：强制刷新 或 定期刷新
        int refreshInterval = callback.getRefreshInterval();
        if (refreshInterval <= 0) {
            refreshInterval = 20; // 默认 20 tick（1秒）
        }

        if (forceRefresh || (this.ticksOpen % refreshInterval == 0 && this.playerId != null)) {
            updateGui();
            resetForceRefresh();
            hasCurrencyUpdate = false;
        }

        // 鼠标悬停追踪：检测当前鼠标悬停在哪个交易 Widget 上
        NekoTradeCategory activeCategory = callback.getActiveCategory();
        NekoDisplayType displayType = callback.getDisplayType();
        List<NekoTradeItemDisplayWidget> widgets = callback.getDisplayedWidgets(displayType, activeCategory);

        this.currentSelected = null;
        if (widgets != null) {
            for (NekoTradeItemDisplayWidget widget : widgets) {
                if (widget.isBelowMouse() && widget.getDisplay() != null) {
                    // 记录鼠标悬停的交易组 UUID（供外部使用，如 tooltip 显示）
                    this.currentSelected = widget.getDisplay()
                        .getGroupId();
                    break;
                }
            }
        }

        // 递增 tick 计数器
        this.ticksOpen += 1;
    }

    // ==================== 生命周期 ====================

    /**
     * 面板销毁
     * <p>
     * 完美复刻 VM 的 dispose 逻辑，适配 GTIT V2 系统。
     * <p>
     * <b>VM 适配</b>：VM 调用 {@code base.resetCurrentUser(player)} 清理服务端用户引用，
     * 并发送 {@code NetResetVMUser} 网络包同步重置。
     * GTIT 通过 {@link PanelCallback#onDispose()} 回调通知 GUI 控制器，
     * 由 GUI 控制器决定具体的清理逻辑（可能不需要网络包，因为 V2 架构不同）。
     */
    @Override
    public void dispose() {
        // 通知 GUI 控制器清理当前用户
        callback.onDispose();
        super.dispose();
    }

    /**
     * 面板打开
     * <p>
     * 完美复刻 VM 的 onOpen 逻辑。
     * 通知 GUI 控制器恢复之前的设置（如标签页位置、搜索文本等）。
     * <p>
     * 对应 VM 的 {@code gui.restorePreviousSettings()}。
     *
     * @param screen 模块化屏幕
     */
    @Override
    public void onOpen(ModularScreen screen) {
        super.onOpen(screen);
        // 通知 GUI 控制器恢复之前的设置
        callback.onRestoreSettings();
    }

    // ==================== 公共方法 ====================

    /**
     * 设置强制刷新标志
     * <p>
     * 下一次 onUpdate() 时会触发 updateGui()。
     * 对应 VM 的 {@code MTEVendingMachineGui.setForceRefresh()}。
     */
    public void setForceRefresh() {
        forceRefresh = true;
    }

    /**
     * 重置强制刷新标志
     * <p>
     * 对应 VM 的 {@code MTEVendingMachineGui.resetForceRefresh()}。
     */
    public void resetForceRefresh() {
        forceRefresh = false;
    }

    /**
     * 通知货币数据已更新
     * <p>
     * 外部（如交易执行后）调用此方法，下次 onUpdate() 会触发强制刷新。
     * 对应 VM 的 {@code TradeManager.INSTANCE.hasCurrencyUpdate}。
     */
    public void notifyCurrencyUpdate() {
        hasCurrencyUpdate = true;
    }

    /**
     * 获取当前钱包模式
     * <p>
     * 对应 VM 的 {@code getWalletMode()}。
     *
     * @return 钱包模式
     */
    public NekoWalletMode getWalletMode() {
        return callback.getWalletMode();
    }

    // ==================== 内部辅助方法 ====================

    /**
     * 获取当前显示数据
     * <p>
     * 完美复刻 VM 的 getCurrentTradeDisplayData 逻辑，适配 GTIT V2 系统。
     * <p>
     * 从当前显示的 Widget 列表中提取 NekoTradeItemDisplay 数据，
     * 按分类组织返回。用于增量更新（updateTradeInformation）时
     * 获取当前正在显示的交易数据。
     * <p>
     * <b>VM 适配</b>：VM 同时从 displayedTradesTiles 和 displayedTradesList 提取数据
     * （两者共享相同的 TradeItemDisplay 对象）。
     * GTIT 仅从当前显示模式对应的 Widget 列表提取，避免重复。
     *
     * @return 当前显示数据（按分类组织）
     */
    private Map<NekoTradeCategory, List<NekoTradeItemDisplay>> getCurrentTradeDisplayData() {
        Map<NekoTradeCategory, List<NekoTradeItemDisplay>> currentData = new HashMap<>();

        NekoDisplayType displayType = callback.getDisplayType();

        // 遍历所有分类，从 Widget 列表提取显示数据
        for (NekoTradeCategory category : NekoTradeCategory.values()) {
            List<NekoTradeItemDisplayWidget> widgets = callback.getDisplayedWidgets(displayType, category);
            if (widgets != null) {
                List<NekoTradeItemDisplay> displays = widgets.stream()
                    .map(NekoTradeItemDisplayWidget::getDisplay)
                    .filter(d -> d != null)
                    .collect(Collectors.toList());
                currentData.put(category, displays);
            } else {
                currentData.put(category, new ArrayList<>());
            }
        }

        return currentData;
    }
}

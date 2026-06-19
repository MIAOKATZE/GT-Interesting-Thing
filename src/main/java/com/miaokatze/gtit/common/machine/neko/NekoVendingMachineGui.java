package com.miaokatze.gtit.common.machine.neko;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;

import com.cleanroommc.modularui.api.IPanelHandler;
import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.value.IIntValue;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.api.widget.Interactable;
import com.cleanroommc.modularui.drawable.DynamicDrawable;
import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.RichTooltip;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.value.IntValue;
import com.cleanroommc.modularui.value.sync.BooleanSyncValue;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.InteractionSyncHandler;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.SyncHandler;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widget.SingleChildWidget;
import com.cleanroommc.modularui.widget.Widget;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.CycleButtonWidget;
import com.cleanroommc.modularui.widgets.ItemDisplayWidget;
import com.cleanroommc.modularui.widgets.ListWidget;
import com.cleanroommc.modularui.widgets.PagedWidget;
import com.cleanroommc.modularui.widgets.SlotGroupWidget;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.cleanroommc.modularui.widgets.ToggleButton;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.cleanroommc.modularui.widgets.layout.Grid;
import com.cleanroommc.modularui.widgets.slot.ItemSlot;
import com.cleanroommc.modularui.widgets.slot.ModularSlot;
import com.cubefury.vendingmachine.VMConfig;
import com.cubefury.vendingmachine.VendingMachine;
import com.cubefury.vendingmachine.blocks.MTEVendingMachine;
import com.cubefury.vendingmachine.blocks.gui.DisplayType;
import com.cubefury.vendingmachine.blocks.gui.MTEVendingMachineGui;
import com.cubefury.vendingmachine.blocks.gui.MusicTrack;
import com.cubefury.vendingmachine.blocks.gui.SearchBar;
import com.cubefury.vendingmachine.blocks.gui.SortMode;
import com.cubefury.vendingmachine.blocks.gui.TradeItemDisplay;
import com.cubefury.vendingmachine.blocks.gui.TradeItemDisplayWidget;
import com.cubefury.vendingmachine.blocks.gui.TradeMainPanel;
import com.cubefury.vendingmachine.blocks.gui.TradeRow;
import com.cubefury.vendingmachine.blocks.gui.VolumeControlGui;
import com.cubefury.vendingmachine.blocks.gui.WalletMode;
import com.cubefury.vendingmachine.blocks.gui.fallingitem.FallingItemSlotFactory;
import com.cubefury.vendingmachine.gui.GuiTextures;
import com.cubefury.vendingmachine.gui.WidgetThemes;
import com.cubefury.vendingmachine.storage.NameCache;
import com.cubefury.vendingmachine.trade.FavouritesTracker;
import com.cubefury.vendingmachine.trade.TradeCategory;
import com.cubefury.vendingmachine.trade.TradeDatabase;
import com.cubefury.vendingmachine.trade.TradeGroup;
import com.cubefury.vendingmachine.util.Translator;
import com.gtnewhorizon.gtnhlib.config.ConfigurationManager;
import com.miaokatze.gtit.main.GTInterestingThing;
import com.miaokatze.gtit.trade.NekoCurrencyRegistrar;
import com.miaokatze.gtit.trade.NekoTradeRegistry;
import com.miaokatze.gtit.trade.NekoWallet;
import com.miaokatze.gtit.trade.NekoWalletManager;

import gregtech.api.modularui2.GTWidgetThemes;

/**
 * 猫猫售货机 GUI
 * <p>
 * 完全覆盖父类的 build() 方法，不调用 super.build()。
 * 自己用 VM 的 public 组件类重新组装 UI，实现以下定制：
 * <ul>
 * <li>只显示 2 个猫猫币图标（neko + shimmeringNeko），不显示原版 14 个 CoinDisplay</li>
 * <li>猫猫币放入输入槽自动进入 NekoWallet</li>
 * <li>"弹出硬币"按钮替换为弹出所有猫猫币</li>
 * <li>不播放原版 BGM</li>
 * <li>保留原版交易列表、输入槽、输出槽、动画效果</li>
 * </ul>
 */
public class NekoVendingMachineGui extends MTEVendingMachineGui {

    /**
     * 猫猫售货机 GUI 是否打开（客户端）
     * <p>
     * 供 NekoMusicEventHandler 检测 GUI 状态，在 GUI 打开时播放 BGM，关闭时停止。
     */
    public static boolean isNekoGuiOpen = false;

    // 猫猫币专用字段（影子字段，替代父类的 private 字段）
    private PosGuiData nekoGuiData;
    private final PagedWidget.Controller nekoTabController;
    private final List<ListWidget> nekoTradeLists;
    /** 猫猫币页面专用的 TILE 模式 widget 列表（独立于父类的 displayedTradesTiles） */
    private List<TradeItemDisplayWidget> nekoSpecificTiles;
    /** 闪烁猫猫币页面专用的 TILE 模式 widget 列表 */
    private List<TradeItemDisplayWidget> shimmeringSpecificTiles;
    /** 猫猫币页面专用的 LIST 模式 widget 列表 */
    private List<TradeItemDisplayWidget> nekoSpecificList;
    /** 闪烁猫猫币页面专用的 LIST 模式 widget 列表 */
    private List<TradeItemDisplayWidget> shimmeringSpecificList;
    /** 其他页面专用的 TILE 模式 widget 列表 */
    private List<TradeItemDisplayWidget> otherSpecificTiles;
    /** 其他页面专用的 LIST 模式 widget 列表 */
    private List<TradeItemDisplayWidget> otherSpecificList;
    private final SearchBar nekoSearchBar;

    // 反射缓存：父类 MTEVendingMachineGui.guiData
    private static java.lang.reflect.Field parentGuiDataField;

    /**
     * 通过反射设置父类的 private guiData 字段
     * 父类的 registerSyncValues() 和其他方法依赖 this.guiData，
     * 但该字段是 private 的，子类无法直接访问。
     */
    private void setParentGuiData(PosGuiData guiData) {
        try {
            if (parentGuiDataField == null) {
                parentGuiDataField = MTEVendingMachineGui.class.getDeclaredField("guiData");
                parentGuiDataField.setAccessible(true);
            }
            parentGuiDataField.set(this, guiData);
        } catch (Exception e) {
            GTInterestingThing.LOG.error("[NEKO] 反射设置父类 guiData 失败!", e);
        }
    }

    private CycleButtonWidget nekoVolumeButton;
    private IPanelHandler nekoVolumePanel;
    private final List<ItemSlot> nekoInputSlots = new ArrayList<>();
    private final List<TradeCategory> nekoTradeCategories = new ArrayList<>();

    // 猫猫币同步状态字段
    private boolean nekoEjectItems = false;
    private boolean nekoEjectAllCoins = false;
    private final Map<String, Boolean> nekoEjectSingleCoin = new HashMap<>();

    /**
     * 构造猫猫售货机 GUI
     *
     * @param base 猫猫售货机机器实例
     */
    public NekoVendingMachineGui(MTEVendingMachine base) {
        super(base);
        // 初始化猫猫币弹出状态
        for (String currencyId : NekoCurrencyRegistrar.getNekoCurrencyIds()) {
            this.nekoEjectSingleCoin.put(currencyId, false);
        }
        // 初始化交易分类（猫猫币、闪烁猫猫币、其他）
        // 猫猫币复用 MISC 枚举，闪烁猫猫币复用 MAGIC 枚举，其他复用 COMPONENTS 枚举
        // 实际交易数据始终从 ALL 读取，过滤通过 isNekoCurrencyTrade 实现
        this.nekoTradeCategories.add(TradeCategory.MISC); // 代表猫猫币
        this.nekoTradeCategories.add(TradeCategory.MAGIC); // 代表闪烁猫猫币
        this.nekoTradeCategories.add(TradeCategory.COMPONENTS); // 代表"其他"
        // 初始化交易列表
        this.nekoTradeLists = new ArrayList<>();
        // 初始化猫猫币/闪烁猫猫币/其他专用 widget 列表（客户端）
        if (VendingMachine.proxy.isClient()) {
            this.nekoSpecificTiles = new ArrayList<>();
            this.shimmeringSpecificTiles = new ArrayList<>();
            this.otherSpecificTiles = new ArrayList<>();
            this.nekoSpecificList = new ArrayList<>();
            this.shimmeringSpecificList = new ArrayList<>();
            this.otherSpecificList = new ArrayList<>();
            for (int i = 0; i < 300; i++) {
                this.nekoSpecificTiles.add(new TradeItemDisplayWidget(null, this.getBase(), DisplayType.TILE));
                this.shimmeringSpecificTiles.add(new TradeItemDisplayWidget(null, this.getBase(), DisplayType.TILE));
                this.otherSpecificTiles.add(new TradeItemDisplayWidget(null, this.getBase(), DisplayType.TILE));
                this.nekoSpecificList.add(new TradeItemDisplayWidget(null, this.getBase(), DisplayType.LIST));
                this.shimmeringSpecificList.add(new TradeItemDisplayWidget(null, this.getBase(), DisplayType.LIST));
                this.otherSpecificList.add(new TradeItemDisplayWidget(null, this.getBase(), DisplayType.LIST));
            }
        }
        // 初始化标签控制器和搜索栏（客户端）
        this.nekoTabController = VendingMachine.proxy.isClient() ? new PagedWidget.Controller() : null;
        this.nekoSearchBar = VendingMachine.proxy.isClient() ? this.createNekoSearchBar() : null;
    }

    /**
     * 完全覆盖 build() 方法，不调用 super.build()
     */
    @Override
    public ModularPanel build(PosGuiData guiData, PanelSyncManager syncManager, UISettings uiSettings) {
        GTInterestingThing.LOG.info(
            "[NEKO] NekoVendingMachineGui.build() 开始: isClient={}, baseActive={}",
            syncManager.isClient(),
            this.getBase() != null && this.getBase()
                .getActive());

        // 服务端日志：诊断原版VM交易是否在 TradeDatabase 中
        if (!syncManager.isClient()) {
            GTInterestingThing.LOG.info(
                "[NEKO] TradeDatabase 状态: tradeGroups={}, noConditionTrades={}, totalTrades={}",
                TradeDatabase.INSTANCE.getTradeGroupCount(),
                TradeDatabase.INSTANCE.noConditionTrades.size(),
                TradeDatabase.INSTANCE.getTradeCount());
            // 检查 noConditionTrades 中有多少是猫猫币交易
            int nekoNoCondition = 0;
            int vmNoCondition = 0;
            for (TradeGroup tg : TradeDatabase.INSTANCE.noConditionTrades) {
                if (NekoTradeRegistry.isNekoTradeGroup(tg.getId())) {
                    nekoNoCondition++;
                } else {
                    vmNoCondition++;
                }
            }
            GTInterestingThing.LOG.info("[NEKO] noConditionTrades 分类: neko={}, vm={}", nekoNoCondition, vmNoCondition);
        }
        try {
            this.nekoGuiData = guiData;
            // 必须先设置父类的 guiData，因为 super.registerSyncValues() 依赖它
            this.setParentGuiData(guiData);
            this.registerSyncValues(syncManager);

            // 创建主面板
            ModularPanel panel = (ModularPanel) ((ModularPanel) new TradeMainPanel(
                "MTEMultiBlockBase",
                this,
                guiData,
                syncManager).size(178, 320)).padding(4);

            if (syncManager.isClient()) {
                // 标记猫猫售货机 GUI 已打开（供 BGM 系统使用）
                isNekoGuiOpen = true;
                panel.onCloseAction(() -> {
                    FavouritesTracker.INSTANCE.saveFavourites();
                    // 标记猫猫售货机 GUI 已关闭
                    isNekoGuiOpen = false;
                    // 通知 NekoMusicEventHandler GUI 已关闭
                    NekoMusicEventHandler.onGuiClosed();
                });
                // 通知 NekoMusicEventHandler GUI 已打开
                NekoMusicEventHandler.onGuiOpened();
            }

            // 分类标签（左侧）
            panel.child(this.createNekoCategoryTabs(this.nekoTabController));

            // 主列
            Flow mainColumn = (Flow) Flow.column()
                .width(170);
            if (syncManager.isClient()) {
                panel.child(this.createNekoQolButtonColumn());
                ((Flow) ((Flow) mainColumn.child(
                    this.createNekoTitleTextStyle(
                        IKey.lang("gt.blockmachines.multimachine.vendingmachine.name.gui")
                            .style(IKey.DARK_GRAY)
                            .get()))).child((IWidget) this.nekoSearchBar))
                                .child(this.createNekoTradeUI((TradeMainPanel) panel, this.nekoTabController));
                // 猫猫币显示行（替代原版 CoinInventoryRow）
                mainColumn.child(this.createNekoCoinInventoryRow((TradeMainPanel) panel, syncManager));
            }

            // 音量面板（保留原版功能，但不播放 BGM）
            this.nekoVolumePanel = syncManager.syncedPanel(
                "volume",
                true,
                (syncManager1, syncHandler) -> new VolumeControlGui()
                    .createPanel(syncManager1, (IWidget) this.nekoVolumeButton));

            // 玩家背包栏
            mainColumn.child(this.createNekoInventoryRow());

            panel.child((IWidget) mainColumn);
            // 右侧空列
            panel.child(
                (IWidget) ((Flow) Flow.column()
                    .size(20)).right(5));
            // IO 列（猫猫币版本）
            panel.child(this.createNekoIOColumn());

            return panel;
        } catch (Throwable t) {
            GTInterestingThing.LOG.error("[NEKO] NekoVendingMachineGui.build() 异常!", t);
            throw t;
        }
    }

    /**
     * 覆盖注册同步值方法
     * <p>
     * 调用 super.registerSyncValues() 获取所有同步值，但需要先通过反射初始化
     * 父类的 walletButtons（private Flow），否则 hasTeam 同步值的回调会 NPE。
     * <p>
     * 原版货币的同步值（coinAmount_*, ejectCoin_*, ejectCoins）虽然被注册，
     * 但由于不创建对应的按钮，不会被用户触发。
     */
    @Override
    protected void registerSyncValues(PanelSyncManager syncManager) {
        // 预初始化父类的 walletButtons 为空 Flow，防止 hasTeam 回调 NPE
        try {
            java.lang.reflect.Field walletButtonsField = MTEVendingMachineGui.class.getDeclaredField("walletButtons");
            walletButtonsField.setAccessible(true);
            walletButtonsField.set(this, Flow.row());
        } catch (Exception e) {
            GTInterestingThing.LOG.error("[NEKO] 反射初始化 walletButtons 失败!", e);
        }

        // 调用父类 registerSyncValues（包含 GT5U 基础 + VM 货币同步值）
        super.registerSyncValues(syncManager);

        // 猫猫币专用同步值
        UUID playerId = NameCache.INSTANCE.getUUIDFromPlayer(
            this.getBase()
                .getCurrentUser());

        for (String currencyId : NekoCurrencyRegistrar.getNekoCurrencyIds()) {
            final String cid = currencyId;
            IntSyncValue coinAmountSyncer = new IntSyncValue(() -> {
                if (this.nekoGuiData == null || this.nekoGuiData.isClient()) {
                    return 0;
                }
                NekoWallet wallet = NekoWalletManager.INSTANCE.getWallet(playerId);
                return wallet == null ? 0 : wallet.getCount(cid);
            });
            syncManager.syncValue("nekoCoinAmount_" + currencyId, (SyncHandler) coinAmountSyncer);

            BooleanSyncValue ejectCoinSyncer = (BooleanSyncValue) new BooleanSyncValue(
                () -> this.nekoEjectSingleCoin.get(cid),
                val -> {
                    this.nekoEjectSingleCoin.put(cid, val);
                    if (val) {
                        this.doNekoEjectCoin(cid);
                    }
                }).allowC2S();
            syncManager.syncValue("nekoEjectCoin_" + currencyId, (SyncHandler) ejectCoinSyncer);
        }

        BooleanSyncValue ejectAllCoinsSyncer = (BooleanSyncValue) new BooleanSyncValue(
            () -> this.nekoEjectAllCoins,
            val -> {
                this.nekoEjectAllCoins = val;
                if (this.nekoEjectAllCoins) {
                    this.doNekoEjectAllCoins();
                }
            }).allowC2S();
        syncManager.syncValue("nekoEjectAllCoins", (SyncHandler) ejectAllCoinsSyncer);
    }

    // ==================== 覆盖父类方法 ====================

    @Override
    public TradeCategory getActiveTradeCategory() {
        // 所有标签页都使用 ALL 分类（过滤通过 isNekoCurrencyTrade 实现）
        return TradeCategory.ALL;
    }

    @Override
    public void restorePreviousSettings() {
        if (this.nekoTabController.isInitialised()) {
            // 猫猫机只有3个标签页(0-2)，而 lastPage 是静态字段，
            // 可能保留原版VM的值(如8)，需要 clamp 到有效范围
            int maxPage = this.nekoTradeCategories.size() - 1;
            int page = Math.min(lastPage, maxPage);
            this.nekoTabController.setPage(page);
        }
        this.nekoSearchBar.setText(lastSearch);
    }

    @Override
    public void updateTradeDisplay(Map<TradeCategory, List<TradeItemDisplay>> trades) {
        // 更新父类的 widget 列表（全部页面）
        this.updateTradeDisplayNeko(trades, this.displayedTradesTiles);
        this.updateTradeDisplayNeko(trades, this.displayedTradesList);

        // 手动构建猫猫币交易的 TradeItemDisplay
        // 因为猫猫币交易不在 noConditionTrades 中，NetTradeDisplaySync 不会发送它们
        // 需要从 TradeDatabase 直接获取猫猫币 TradeGroup 并构建 TradeItemDisplay
        // 注意：猫猫机的三个标签页只显示猫猫币交易，不显示原版VM交易
        List<TradeItemDisplay> nekoTrades = new ArrayList<>();
        List<TradeItemDisplay> shimmeringTrades = new ArrayList<>();
        List<TradeItemDisplay> otherTrades = new ArrayList<>();

        // 从 TradeDatabase 获取猫猫币 TradeGroup，手动构建 TradeItemDisplay
        for (UUID tgId : NekoTradeRegistry.getNekoTradeGroupIds()) {
            TradeGroup tg = TradeDatabase.INSTANCE.getTradeGroupFromId(tgId);
            if (tg == null) continue;

            NekoTradeRegistry.NekoTradeInfo info = NekoTradeRegistry.getNekoTradeInfo(tgId);
            String currencyId = info != null ? info.currencyId : null;

            for (int i = 0; i < tg.getTrades()
                .size(); i++) {
                com.cubefury.vendingmachine.trade.Trade trade = tg.getTrades()
                    .get(i);

                // 修复问题1：display item 的 stackSize 设为产物总数量
                ItemStack displayStack = trade.getDisplayItem();
                if (displayStack != null && !trade.toItems.isEmpty()) {
                    displayStack = displayStack.copy();
                    // 使用第一个产物的 stackSize 作为显示数量
                    displayStack.stackSize = trade.toItems.get(0).stackSize;
                }

                // 修复问题3：冷却时间
                boolean hasCooldown = tg.cooldown != -1 && tg.cooldown > 0;
                long cooldownValue = hasCooldown ? tg.cooldown : -1L;
                String cooldownText = hasCooldown ? String.valueOf(tg.cooldown) : "";
                int cdTradeCount = tg.maxTrades > 0 ? tg.maxTrades : 0;

                // 修复问题1：tradeableNow 需要实际检查
                // 只在服务端调用 checkTrade 模拟检查
                boolean tradeableNowPersonal = true;
                boolean tradeableNowTeam = true;
                if (this.nekoGuiData != null && !this.nekoGuiData.isClient()
                    && this.getBase() != null
                    && this.getBase()
                        .getActive()) {
                    // 服务端：调用 checkTrade 模拟
                    UUID currentPlayerId = NameCache.INSTANCE.getUUIDFromPlayer(
                        this.getBase()
                            .getCurrentUser());
                    if (currentPlayerId != null) {
                        tradeableNowPersonal = this.getBase()
                            .checkTrade(trade, currentPlayerId, WalletMode.PERSONAL, true);
                        tradeableNowTeam = this.getBase()
                            .checkTrade(trade, currentPlayerId, WalletMode.TEAM, true);
                    }
                }

                TradeItemDisplay display = new TradeItemDisplay(
                    trade.fromCurrency,
                    trade.fromItems,
                    trade.nonConsumedItems,
                    trade.toItems,
                    displayStack,
                    tgId,
                    i,
                    cooldownValue,
                    cooldownText,
                    hasCooldown,
                    true, // enabled
                    tradeableNowPersonal,
                    tradeableNowTeam,
                    cdTradeCount);

                if ("neko".equals(currencyId)) {
                    nekoTrades.add(display);
                } else if ("shimmeringNeko".equals(currencyId)) {
                    shimmeringTrades.add(display);
                } else {
                    otherTrades.add(display);
                }
            }
        }

        // 修复问题5：排序
        // Smart 排序 = 按 orderId 排序
        // A-Z 排序 = 按产物名排序
        SortMode currentSort = VMConfig.gui.sort_mode;
        if (currentSort == SortMode.SMART) {
            sortByOrderId(nekoTrades);
            sortByOrderId(shimmeringTrades);
            sortByOrderId(otherTrades);
        } else if (currentSort == SortMode.ALPHABET) {
            sortByDisplayName(nekoTrades);
            sortByDisplayName(shimmeringTrades);
            sortByDisplayName(otherTrades);
        }

        // 修复问题5：搜索过滤
        String searchText = this.nekoSearchBar != null ? this.nekoSearchBar.getText() : "";
        if (!searchText.isEmpty()) {
            nekoTrades = filterBySearch(nekoTrades, searchText);
            shimmeringTrades = filterBySearch(shimmeringTrades, searchText);
            otherTrades = filterBySearch(otherTrades, searchText);
        }

        GTInterestingThing.LOG.info(
            "[NEKO] updateTradeDisplay: neko={}, shimmering={}, other={}, sort={}, search='{}'",
            nekoTrades.size(),
            shimmeringTrades.size(),
            otherTrades.size(),
            currentSort,
            searchText);

        updateFilteredWidgetList(this.nekoSpecificTiles, nekoTrades);
        updateFilteredWidgetList(this.shimmeringSpecificTiles, shimmeringTrades);
        updateFilteredWidgetList(this.otherSpecificTiles, otherTrades);
        updateFilteredWidgetList(this.nekoSpecificList, nekoTrades);
        updateFilteredWidgetList(this.shimmeringSpecificList, shimmeringTrades);
        updateFilteredWidgetList(this.otherSpecificList, otherTrades);

        this.updateTabHighlightingNeko(trades);
    }

    /**
     * 按 orderId 排序（Smart排序模式）
     * <p>
     * 通过 NekoTradeInfo.orderId 获取排序键
     */
    private void sortByOrderId(List<TradeItemDisplay> displays) {
        displays.sort((a, b) -> {
            NekoTradeRegistry.NekoTradeInfo infoA = NekoTradeRegistry.getNekoTradeInfo(a.tgID);
            NekoTradeRegistry.NekoTradeInfo infoB = NekoTradeRegistry.getNekoTradeInfo(b.tgID);
            int orderA = infoA != null ? infoA.orderId : Integer.MAX_VALUE;
            int orderB = infoB != null ? infoB.orderId : Integer.MAX_VALUE;
            return Integer.compare(orderA, orderB);
        });
    }

    /**
     * 按产物名排序（A-Z排序模式）
     */
    private void sortByDisplayName(List<TradeItemDisplay> displays) {
        displays.sort((a, b) -> {
            String nameA = a.display != null ? a.display.getDisplayName() : "";
            String nameB = b.display != null ? b.display.getDisplayName() : "";
            return nameA.compareToIgnoreCase(nameB);
        });
    }

    /**
     * 搜索过滤
     * <p>
     * 匹配产物名或需求物品名
     */
    private List<TradeItemDisplay> filterBySearch(List<TradeItemDisplay> displays, String searchText) {
        String searchLower = searchText.toLowerCase();
        List<TradeItemDisplay> result = new ArrayList<>();
        for (TradeItemDisplay display : displays) {
            // 检查产物名
            if (display.display != null && display.display.getDisplayName()
                .toLowerCase()
                .contains(searchLower)) {
                result.add(display);
                continue;
            }
            // 检查 toItems 名称
            boolean found = false;
            for (com.cubefury.vendingmachine.util.BigItemStack toItem : display.toItems) {
                if (toItem.getBaseStack()
                    .getDisplayName()
                    .toLowerCase()
                    .contains(searchLower)) {
                    found = true;
                    break;
                }
            }
            if (found) {
                result.add(display);
                continue;
            }
            // 检查 fromItems 名称
            for (com.cubefury.vendingmachine.util.BigItemStack fromItem : display.fromItems) {
                if (fromItem.getBaseStack()
                    .getDisplayName()
                    .toLowerCase()
                    .contains(searchLower)) {
                    found = true;
                    break;
                }
            }
            if (found) {
                result.add(display);
                continue;
            }
            // 检查猫猫币名称
            NekoTradeRegistry.NekoTradeInfo info = NekoTradeRegistry.getNekoTradeInfo(display.tgID);
            if (info != null && info.currencyId != null) {
                String currencyName = NekoCurrencyRegistrar.getDisplayName(info.currencyId);
                if (currencyName.toLowerCase()
                    .contains(searchLower)) {
                    result.add(display);
                }
            }
        }
        return result;
    }

    /**
     * 更新专用 widget 列表的数据（只包含过滤后的交易）
     */
    private void updateFilteredWidgetList(List<TradeItemDisplayWidget> widgets, List<TradeItemDisplay> filteredTrades) {
        synchronized (widgets) {
            for (int i = 0; i < 300; i++) {
                if (i < filteredTrades.size()) {
                    widgets.get(i)
                        .setDisplay(filteredTrades.get(i));
                } else {
                    widgets.get(i)
                        .setDisplay(null);
                }
            }
        }
    }

    @Override
    public void resetTradeDisplayScroll() {
        this.nekoTradeLists.forEach(
            list -> list.getScrollArea()
                .getScrollY()
                .scrollTo(list.getScrollArea(), 0));
    }

    @Override
    public SearchBar getSearchBar() {
        return this.nekoSearchBar;
    }

    @Override
    public String getSearchBarText() {
        return this.nekoSearchBar.getText();
    }

    // ==================== 猫猫币专用方法 ====================

    /**
     * 弹出单种猫猫币
     */
    private void doNekoEjectCoin(String currencyId) {
        if (this.nekoGuiData.isClient() || !this.getBase()
            .getActive()) {
            this.nekoEjectSingleCoin.put(currencyId, false);
            return;
        }
        UUID playerId = NameCache.INSTANCE.getUUIDFromPlayer(
            this.getBase()
                .getCurrentUser());
        NekoWallet wallet = NekoWalletManager.INSTANCE.getWallet(playerId);
        if (wallet == null || wallet.getCount(currencyId) <= 0) {
            this.nekoEjectSingleCoin.put(currencyId, false);
            return;
        }
        List<ItemStack> ejectables = new ArrayList<>();
        int count = wallet.getCount(currencyId);
        while (count > 0) {
            int stackSize = Math.min(count, 64);
            ItemStack stack = NekoCurrencyRegistrar.getItemStack(currencyId, stackSize);
            if (stack != null) {
                ejectables.add(stack);
            }
            count -= stackSize;
        }
        this.getBase()
            .dispenseItemStacks(ejectables);
        wallet.resetCount(currencyId);
        NekoWalletManager.INSTANCE.saveWallet(playerId);
        this.nekoEjectSingleCoin.put(currencyId, false);
    }

    /**
     * 弹出所有猫猫币
     */
    private void doNekoEjectAllCoins() {
        if (this.nekoGuiData.isClient() || !this.getBase()
            .getActive()) {
            this.nekoEjectAllCoins = false;
            return;
        }
        UUID playerId = NameCache.INSTANCE.getUUIDFromPlayer(
            this.getBase()
                .getCurrentUser());
        NekoWallet wallet = NekoWalletManager.INSTANCE.getWallet(playerId);
        if (wallet == null) {
            this.nekoEjectAllCoins = false;
            return;
        }
        List<ItemStack> ejectables = new ArrayList<>();
        for (String currencyId : NekoCurrencyRegistrar.getNekoCurrencyIds()) {
            int count = wallet.getCount(currencyId);
            while (count > 0) {
                int stackSize = Math.min(count, 64);
                ItemStack stack = NekoCurrencyRegistrar.getItemStack(currencyId, stackSize);
                if (stack != null) {
                    ejectables.add(stack);
                }
                count -= stackSize;
            }
            wallet.resetCount(currencyId);
        }
        this.getBase()
            .dispenseItemStacks(ejectables);
        NekoWalletManager.INSTANCE.saveWallet(playerId);
        this.nekoEjectAllCoins = false;
    }

    /**
     * 弹出输入槽中的物品
     */
    private void doNekoEjectItems() {
        if (this.nekoGuiData.isClient()) {
            return;
        }
        if (!this.getBase()
            .getActive()) {
            this.nekoEjectItems = false;
            return;
        }
        this.copyInputItemsIntoOutputBuffer();
        this.clearAllInputItems();
        this.nekoEjectItems = false;
    }

    private void copyInputItemsIntoOutputBuffer() {
        List<ItemStack> ejectables = IntStream.range(0, 8)
            .mapToObj(i -> this.getBase().inputItems.getStackInSlot(i))
            .filter(Objects::nonNull)
            .map(ItemStack::copy)
            .collect(Collectors.toList());
        this.getBase()
            .dispenseItemStacks(ejectables);
    }

    private void clearAllInputItems() {
        for (int i = 0; i < 8; ++i) {
            ItemStack stack = this.getBase().inputItems.getStackInSlot(i);
            if (stack == null) continue;
            this.getBase().inputItems.setStackInSlot(i, null);
        }
    }

    // ==================== UI 组件创建方法 ====================

    /**
     * 创建分类标签（猫猫币、闪烁猫猫币、其他）
     * <p>
     * 使用 NekoPageButton 显示 ItemStack 图标，
     * 替代 VendingPageButton 的 TradeCategory 纹理图标。
     */
    private IWidget createNekoCategoryTabs(PagedWidget.Controller tabController) {
        Flow tabColumn = (Flow) ((Flow) ((Flow) ((Flow) ((Flow) ((Flow) Flow.column()
            .excludeAreaInRecipeViewer()).width(40)).height(300)).left(-29)).top(40)).coverChildren();

        // 标签页图标 ItemStack
        ItemStack[] tabIcons = new ItemStack[] { NekoCurrencyRegistrar.getItemStack("neko", 1),
            NekoCurrencyRegistrar.getItemStack("shimmeringNeko", 1), new ItemStack(Items.written_book) };
        String[] tabNames = { "猫猫币", "闪烁猫猫币", "其他" };

        // 调试日志：确认图标 ItemStack 是否有效
        for (int i = 0; i < tabIcons.length; i++) {
            GTInterestingThing.LOG.info("[NEKO] Tab[{}] icon: {} (null={})", i, tabIcons[i], tabIcons[i] == null);
        }

        for (int i = 0; i < this.nekoTradeCategories.size(); ++i) {
            final int index = i;
            final String tabName = tabNames[i];
            final ItemStack iconStack = tabIcons[i];
            tabColumn.child(
                (IWidget) new NekoPageButton(
                    i,
                    tabController,
                    this.nekoTradeCategories.get(i),
                    this.highlightedTabs,
                    iconStack).tab(GuiTextures.TAB_LEFT, -1)
                        .tooltipBuilder(builder -> {
                            builder.clearText();
                            builder.addLine(IKey.str(tabName));
                        }));
        }
        return tabColumn;
    }

    /**
     * 创建 QoL 按钮列（BGM 切换、音量、显示模式、排序模式）
     * <p>
     * BGM 切换按钮控制猫猫售货机自定义 BGM：
     * - 点击：开启/关闭 BGM
     * - Shift+点击：打开音量调节面板
     */
    private IWidget createNekoQolButtonColumn() {
        return ((Grid) ((Grid) ((Grid) ((Grid) new Grid().left(-33)).excludeAreaInRecipeViewer()).top(1))
            .minElementMargin(1)
            .coverChildren()).grid(
                Arrays.asList(
                    Arrays.asList(
                        this.nekoVolumeButton = (CycleButtonWidget) ((CycleButtonWidget) ((CycleButtonWidget) ((CycleButtonWidget) new CycleButtonWidget()
                            .size(14)).overlay(new IDrawable[] { new DynamicDrawable(() -> {
                                NekoMusicEventHandler handler = NekoMusicEventHandler.getInstance();
                                boolean isPlaying = handler != null && handler.isPlaying();
                                return (isPlaying ? MusicTrack.LUNCH_BREAK.getTexture() : MusicTrack.NONE.getTexture())
                                    .size(14);
                            }) })).stateCount(2)
                                .value((IIntValue) new IntValue.Dynamic(() -> {
                                    NekoMusicEventHandler handler = NekoMusicEventHandler.getInstance();
                                    return handler != null && handler.isPlaying() ? 1 : 0;
                                }, val -> {
                                    if (Interactable.hasShiftDown()) {
                                        this.nekoVolumePanel.togglePanel();
                                    } else {
                                        NekoMusicEventHandler handler = NekoMusicEventHandler.getInstance();
                                        if (handler != null) {
                                            if (val == 1) {
                                                handler.forceStartBGM();
                                            } else {
                                                handler.forceStopBGM();
                                            }
                                        }
                                    }
                                }))
                                .tooltipDynamic(builder -> {
                                    builder.clearText();
                                    NekoMusicEventHandler handler = NekoMusicEventHandler.getInstance();
                                    boolean isPlaying = handler != null && handler.isPlaying();
                                    builder.addLine(IKey.str(isPlaying ? "BGM: 开启" : "BGM: 关闭"));
                                    builder.addLine(
                                        (IDrawable) IKey
                                            .lang(
                                                "vendingmachine.gui.volume.tooltip_volume_display",
                                                new Object[] { VolumeControlGui.getVolumeAsString() })
                                            .style(EnumChatFormatting.GRAY));
                                    builder.addLine(
                                        (IDrawable) IKey.lang("vendingmachine.gui.volume.tooltip_open_panel")
                                            .style(EnumChatFormatting.GRAY));
                                })).tooltipAutoUpdate(true),
                        (CycleButtonWidget) ((CycleButtonWidget) ((CycleButtonWidget) ((CycleButtonWidget) new CycleButtonWidget()
                            .size(14)).overlay(
                                new IDrawable[] { new DynamicDrawable(
                                    () -> VMConfig.gui.display_type.getTexture()
                                        .size(14)) })).stateCount(DisplayType.values().length)
                                            .value(
                                                (IIntValue) new IntValue.Dynamic(
                                                    () -> VMConfig.gui.display_type.ordinal(),
                                                    val -> {
                                                        VMConfig.gui.display_type = DisplayType.values()[val];
                                                        ConfigurationManager
                                                            .save((Class[]) new Class[] { VMConfig.class });
                                                    }))
                                            .tooltipDynamic(builder -> {
                                                builder.clearText();
                                                builder.addLine(
                                                    IKey.lang("vendingmachine.gui.display_mode") + " "
                                                        + VMConfig.gui.display_type.getLocalizedName());
                                            })).tooltipAutoUpdate(true)),
                    Arrays.asList(
                        null,
                        (CycleButtonWidget) ((CycleButtonWidget) ((CycleButtonWidget) ((CycleButtonWidget) new CycleButtonWidget()
                            .size(14)).overlay(
                                new IDrawable[] { new DynamicDrawable(
                                    () -> VMConfig.gui.sort_mode.getTexture()
                                        .size(14)) })).stateCount(SortMode.values().length)
                                            .value(
                                                (IIntValue) new IntValue.Dynamic(
                                                    () -> VMConfig.gui.sort_mode.ordinal(),
                                                    val -> {
                                                        VMConfig.gui.sort_mode = SortMode.values()[val];
                                                        ConfigurationManager
                                                            .save((Class[]) new Class[] { VMConfig.class });
                                                    }))
                                            .tooltipDynamic(builder -> {
                                                builder.clearText();
                                                builder.addLine(
                                                    IKey.lang("vendingmachine.gui.display_sort") + " "
                                                        + VMConfig.gui.sort_mode.getLocalizedName());
                                                MTEVendingMachineGui.setForceRefresh();
                                            })).tooltipAutoUpdate(true))));
    }

    /**
     * 创建标题文本样式（复制自父类 private 方法）
     */
    private IWidget createNekoTitleTextStyle(String title) {
        return ((SingleChildWidget) ((SingleChildWidget) ((SingleChildWidget) ((SingleChildWidget) new SingleChildWidget()
            .coverChildren()).topRel(0.0f, -4, 1.0f)).leftRel(0.0f, -4, 0.0f))
                .widgetTheme(GTWidgetThemes.BACKGROUND_TITLE)).child(
                    (IWidget) ((TextWidget) ((TextWidget) ((TextWidget) ((TextWidget) IKey.str(title)
                        .asWidget()
                        .textAlign(Alignment.Center)
                        .widgetTheme(GTWidgetThemes.TEXT_TITLE)).marginLeft(5)).marginRight(5)).marginTop(5))
                            .marginBottom(1));
    }

    /**
     * 创建搜索栏（复制自父类 private 方法）
     */
    private SearchBar createNekoSearchBar() {
        return (SearchBar) ((SearchBar) ((SearchBar) ((SearchBar) new SearchBar(this).width(162)).left(3)).top(5))
            .height(14);
    }

    /**
     * 创建交易 UI（3个标签页：猫猫币、闪烁猫猫币、其他）
     * <p>
     * 所有页面都使用 ALL 分类的交易数据，各标签页
     * 通过交易过滤只显示对应货币的交易。
     */
    private IWidget createNekoTradeUI(TradeMainPanel rootPanel, PagedWidget.Controller tabController) {
        PagedWidget paged = (PagedWidget) ((PagedWidget) ((PagedWidget) ((PagedWidget) new PagedWidget().name("paged"))
            .width(162)).controller(tabController)
                .background(new IDrawable[] { GuiTextures.TEXT_FIELD_BACKGROUND })).height(146);

        // 页面0：猫猫币交易
        ListWidget nekoList = createTradeListPage(rootPanel, TradeCategory.ALL, "neko");
        this.nekoTradeLists.add(nekoList);
        paged.addPage((IWidget) nekoList);

        // 页面1：闪烁猫猫币交易
        ListWidget shimmeringList = createTradeListPage(rootPanel, TradeCategory.ALL, "shimmeringNeko");
        this.nekoTradeLists.add(shimmeringList);
        paged.addPage((IWidget) shimmeringList);

        // 页面2：其他交易（非猫猫币且非闪烁猫猫币）
        ListWidget otherList = createTradeListPage(rootPanel, TradeCategory.ALL, "other");
        this.nekoTradeLists.add(otherList);
        paged.addPage((IWidget) otherList);

        return (IWidget) ((Flow) ((Flow) Flow.row()
            .child((IWidget) paged.top(0))).left(3)).top(24);
    }

    /**
     * 获取指定货币过滤和显示模式的 widget 列表
     * <p>
     * 猫猫币/闪烁猫猫币/其他页面使用独立的 widget 列表，避免共享实例。
     */
    private List<TradeItemDisplayWidget> getWidgetList(String currencyFilter, DisplayType displayType) {
        if (displayType == DisplayType.TILE) {
            if ("neko".equals(currencyFilter)) return this.nekoSpecificTiles;
            if ("shimmeringNeko".equals(currencyFilter)) return this.shimmeringSpecificTiles;
            if ("other".equals(currencyFilter)) return this.otherSpecificTiles;
            return this.displayedTradesTiles.get(TradeCategory.ALL);
        } else {
            if ("neko".equals(currencyFilter)) return this.nekoSpecificList;
            if ("shimmeringNeko".equals(currencyFilter)) return this.shimmeringSpecificList;
            if ("other".equals(currencyFilter)) return this.otherSpecificList;
            return this.displayedTradesList.get(TradeCategory.ALL);
        }
    }

    /**
     * 创建单个交易列表页面
     *
     * @param rootPanel      根面板
     * @param category       交易分类
     * @param currencyFilter 货币过滤（"neko"=只显示猫猫币交易，"shimmeringNeko"=只显示闪烁猫猫币交易，"other"=非猫猫币且非闪烁猫猫币的交易）
     */
    private ListWidget createTradeListPage(TradeMainPanel rootPanel, TradeCategory category, String currencyFilter) {
        List<TradeItemDisplayWidget> tileWidgets = getWidgetList(currencyFilter, DisplayType.TILE);
        List<TradeItemDisplayWidget> listWidgets = getWidgetList(currencyFilter, DisplayType.LIST);

        ListWidget tradeList = ((ListWidget) ((ListWidget) ((ListWidget) ((ListWidget) new ListWidget().name("items"))
            .width(161)).top(1)).height(144)).collapseDisabledChild(true);
        tradeList.child(
            (IWidget) Flow.row()
                .height(2));
        Flow statusRow = (Flow) ((Flow) ((Flow) ((Flow) ((Flow) Flow.row()
            .height(10)).width(154)).marginLeft(2))
                .child((IWidget) new TextWidget(IKey.lang("vendingmachine.gui.error.incomplete_structure"))));
        statusRow.setEnabledIf(
            slot -> !this.getBase()
                .getActive());
        tradeList.child((IWidget) statusRow);
        Flow row = (Flow) ((Flow) ((Flow) new TradeRow().height(29)).width(154)).marginLeft(2);
        for (int i = 0; i < 300; ++i) {
            final int index = i;
            tileWidgets.get(i)
                .setRootPanel(rootPanel);
            row.child(
                (IWidget) ((ItemDisplayWidget) ((ItemDisplayWidget) ((ItemDisplayWidget) tileWidgets.get(i)
                    .tooltipDynamic(builder -> {
                        builder.clearText();
                        synchronized (tileWidgets) {
                            if (index < tileWidgets.size()) {
                                this.constructTradeTooltipNeko(
                                    (RichTooltip) builder,
                                    tileWidgets.get(index)
                                        .getDisplay());
                            }
                        }
                    })).tooltipAutoUpdate(true)).setEnabledIf(slot -> {
                        if (!this.getBase()
                            .getActive()) {
                            return false;
                        }
                        TradeItemDisplayWidget display = (TradeItemDisplayWidget) (Object) slot;
                        if (display.getDisplay() == null) {
                            return false;
                        }
                        // 检查显示类型
                        if (VMConfig.gui.display_type != display.displayType) {
                            return false;
                        }
                        // 货币过滤
                        if (currencyFilter != null) {
                            return isNekoCurrencyTrade(display.getDisplay(), currencyFilter);
                        }
                        return true;
                    })).margin(2));
            if (i % 3 != 2) continue;
            tradeList.child((IWidget) row);
            row = (Flow) ((Flow) ((Flow) new TradeRow().height(29)).width(154)).marginLeft(2);
        }
        if (row.hasChildren()) {
            tradeList.child((IWidget) row);
        }
        row = (Flow) ((Flow) ((Flow) new TradeRow().height(14)).width(154)).marginLeft(2);
        for (int i = 0; i < 300; ++i) {
            final int index = i;
            listWidgets.get(i)
                .setRootPanel(rootPanel);
            row.child(
                (IWidget) ((ItemDisplayWidget) ((ItemDisplayWidget) listWidgets.get(i)
                    .tooltipDynamic(builder -> {
                        builder.clearText();
                        synchronized (listWidgets) {
                            if (index < listWidgets.size()) {
                                this.constructTradeTooltipNeko(
                                    (RichTooltip) builder,
                                    listWidgets.get(index)
                                        .getDisplay());
                            }
                        }
                    })).tooltipAutoUpdate(true)).setEnabledIf(slot -> {
                        if (!this.getBase()
                            .getActive()) {
                            return false;
                        }
                        TradeItemDisplayWidget display = (TradeItemDisplayWidget) (Object) slot;
                        if (VMConfig.gui.display_type != display.displayType || display.getDisplay() == null) {
                            return false;
                        }
                        // 货币过滤
                        if (currencyFilter != null) {
                            return isNekoCurrencyTrade(display.getDisplay(), currencyFilter);
                        }
                        return true;
                    }));
            tradeList.child((IWidget) row);
            row = (Flow) ((Flow) ((Flow) new TradeRow().height(14)).width(154)).marginLeft(2);
        }
        tradeList.child(
            (IWidget) Flow.row()
                .height(2));
        return tradeList;
    }

    /**
     * 检查交易是否使用指定猫猫币
     * <p>
     * 双重检测：先通过 NekoTradeRegistry 的 tgID 映射判断，
     * 如果没有匹配，再通过 fromItems 中的物品判断。
     * "other" 过滤：不是 neko 也不是 shimmeringNeko 的交易。
     */
    private boolean isNekoCurrencyTrade(TradeItemDisplay display, String currencyId) {
        if (display == null) return false;
        // "other" 过滤：不是 neko 也不是 shimmeringNeko
        if ("other".equals(currencyId)) {
            return !isNekoCurrencyTrade(display, "neko") && !isNekoCurrencyTrade(display, "shimmeringNeko");
        }
        // 方法1：通过 tgID 映射
        NekoTradeRegistry.NekoTradeInfo info = NekoTradeRegistry.getNekoTradeInfo(display.tgID);
        if (info != null) {
            return currencyId.equals(info.currencyId);
        }
        // 方法2：通过 fromItems 物品判断
        for (com.cubefury.vendingmachine.util.BigItemStack fromItem : display.fromItems) {
            String id = NekoCurrencyRegistrar.getNekoCurrencyId(fromItem.getBaseStack());
            if (currencyId.equals(id)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 构建交易 tooltip（复制自父类 private 方法）
     */
    private void constructTradeTooltipNeko(RichTooltip builder, TradeItemDisplay cur) {
        if (cur != null) {
            com.cubefury.vendingmachine.trade.TradeGroup tg;
            for (com.cubefury.vendingmachine.util.BigItemStack toItem : cur.toItems) {
                net.minecraft.nbt.NBTTagCompound nbt;
                StringBuilder nameLine = new StringBuilder();
                if (toItem.stackSize > 1) {
                    nameLine.append(
                        IKey.str(toItem.stackSize + " ")
                            .style(IKey.AQUA));
                }
                nameLine.append(IKey.RESET);
                ItemStack baseStack = toItem.getBaseStack();
                boolean hasCustomName = false;
                if (baseStack.stackTagCompound != null && baseStack.stackTagCompound.hasKey("display", 10)
                    && (nbt = baseStack.stackTagCompound.getCompoundTag("display")).hasKey("Name", 8)) {
                    nameLine.append(
                        IKey.str(nbt.getString("Name") + " ")
                            .style(new EnumChatFormatting[] { IKey.AQUA, IKey.ITALIC }));
                    nameLine.append(IKey.RESET);
                    hasCustomName = true;
                }
                if (hasCustomName) {
                    nameLine.append(
                        IKey.str(
                            "(" + baseStack.getItem()
                                .getItemStackDisplayName(baseStack) + ")")
                            .style(IKey.AQUA));
                } else {
                    nameLine.append(
                        IKey.str(
                            baseStack.getItem()
                                .getItemStackDisplayName(baseStack))
                            .style(IKey.AQUA));
                }
                builder.addLine(nameLine.toString());
            }
            builder.emptyLine();
            // 修复问题4：需求列表需要包含猫猫币信息
            // 因为猫猫币不在 fromItems 中，需要从 NekoTradeInfo 获取
            NekoTradeRegistry.NekoTradeInfo nekoInfo = NekoTradeRegistry.getNekoTradeInfo(cur.tgID);
            boolean hasNekoCurrency = nekoInfo != null && nekoInfo.currencyId != null && nekoInfo.cost > 0;
            if (!cur.fromCurrency.isEmpty() || !cur.fromItems.isEmpty() || hasNekoCurrency) {
                builder.addLine(
                    (IDrawable) IKey.lang("vendingmachine.gui.required_inputs")
                        .style(new EnumChatFormatting[] { IKey.DARK_GREEN, IKey.ITALIC }));
                // 猫猫币需求（从 NekoTradeInfo 获取）
                if (hasNekoCurrency) {
                    builder.addLine(
                        (IDrawable) IKey
                            .str(nekoInfo.cost + " " + NekoCurrencyRegistrar.getDisplayName(nekoInfo.currencyId))
                            .style(IKey.DARK_GREEN));
                }
                for (com.cubefury.vendingmachine.trade.CurrencyItem currencyItem : cur.fromCurrency) {
                    builder.addLine(
                        (IDrawable) IKey.str(currencyItem.value + " " + currencyItem.type.getLocalizedName())
                            .style(IKey.DARK_GREEN));
                }
                for (com.cubefury.vendingmachine.util.BigItemStack fromItem : cur.fromItems) {
                    // 非猫猫币物品需求
                    builder.addLine(
                        (IDrawable) IKey.str(
                            fromItem.stackSize + " "
                                + fromItem.getBaseStack()
                                    .getDisplayName()
                                + (fromItem.hasOreDict()
                                    ? " (" + IKey.lang("vendingmachine.gui.alternative_oredict")
                                        + " "
                                        + fromItem.getOreDict()
                                        + ")"
                                    : ""))
                            .style(IKey.DARK_GREEN));
                }
                builder.emptyLine();
            }
            if (!cur.ncItems.isEmpty()) {
                builder.addLine(
                    (IDrawable) IKey.lang("vendingmachine.gui.nc_inputs")
                        .style(new EnumChatFormatting[] { IKey.DARK_GREEN, IKey.ITALIC }));
                for (com.cubefury.vendingmachine.util.BigItemStack fromItem : cur.ncItems) {
                    builder.addLine(
                        (IDrawable) IKey.str(
                            fromItem.stackSize + " "
                                + fromItem.getBaseStack()
                                    .getDisplayName())
                            .style(IKey.DARK_GREEN));
                }
                builder.emptyLine();
            }
            if ((tg = TradeDatabase.INSTANCE.getTradeGroupFromId(cur.tgID)) != null && tg.getTrades()
                .size() > 1) {
                builder
                    .addLine(
                        (IDrawable) IKey
                            .str(
                                Translator.translate(
                                    "vendingmachine.gui.shared_trades_tooltip",
                                    tg.getTrades()
                                        .size() - 1))
                            .style(new EnumChatFormatting[] { IKey.AQUA, IKey.ITALIC }));
                builder.emptyLine();
            }
            if (tg.cooldown != -1) {
                builder.addLine(
                    (IDrawable) IKey
                        .str(
                            Translator.translate(
                                "vendingmachine.gui.cooldown_remaining_tooltip",
                                teamSize - cur.cdTradeCount,
                                teamSize))
                        .style(IKey.DARK_AQUA));
                builder.addLine(
                    (IDrawable) IKey
                        .str(
                            Translator.translate(
                                cur.cooldown > 0L ? "vendingmachine.gui.cooldown_started_tooltip"
                                    : "vendingmachine.gui.cooldown_idle_tooltip",
                                cur.cooldownText))
                        .style(IKey.DARK_AQUA));
                builder.addLine(
                    (IDrawable) IKey.str(Translator.translate("vendingmachine.gui.trades_scale_tooltip", new Object[0]))
                        .style(IKey.DARK_AQUA));
                builder.emptyLine();
            }
            builder.addLine(
                (IDrawable) IKey.str(Translator.translate("vendingmachine.gui.trade_hint", new Object[0]))
                    .style(IKey.GRAY));
            builder.addLine(
                (IDrawable) IKey.str(Translator.translate("vendingmachine.gui.favourite_hint", new Object[0]))
                    .style(IKey.GRAY));
        }
    }

    /**
     * 更新交易显示（复制自父类 private 方法）
     */
    private void updateTradeDisplayNeko(Map<TradeCategory, List<TradeItemDisplay>> trades,
        Map<TradeCategory, List<TradeItemDisplayWidget>> display) {
        Map<TradeCategory, List<TradeItemDisplayWidget>> map = display;
        synchronized (map) {
            for (Map.Entry<TradeCategory, List<TradeItemDisplayWidget>> entry : display.entrySet()) {
                int displayedSize = trades.get(entry.getKey()) == null ? 0
                    : trades.get(entry.getKey())
                        .size();
                for (int i = 0; i < 300; ++i) {
                    if (i < displayedSize) {
                        entry.getValue()
                            .get(i)
                            .setDisplay(
                                trades.get(entry.getKey())
                                    .get(i));
                        continue;
                    }
                    entry.getValue()
                        .get(i)
                        .setDisplay(null);
                }
            }
        }
    }

    /**
     * 更新标签高亮（复制自父类 private 方法）
     */
    private void updateTabHighlightingNeko(Map<TradeCategory, List<TradeItemDisplay>> trades) {
        this.highlightedTabs.clear();
        if (this.nekoSearchBar.getText()
            .equals("")) {
            return;
        }
        for (Map.Entry<TradeCategory, List<TradeItemDisplay>> entry : trades.entrySet()) {
            if (entry.getValue()
                .isEmpty()) continue;
            this.highlightedTabs.add(entry.getKey());
        }
    }

    /**
     * 创建猫猫币显示行（替代原版 createCoinInventoryRow）
     * <p>
     * 无容器边框，两种猫猫币在同一行。
     * 布局：猫猫币靠左(left=3)，闪烁猫猫币图标居中。
     * 不创建钱包模式按钮（统一使用团队钱包）。
     */
    private IWidget createNekoCoinInventoryRow(TradeMainPanel panel, PanelSyncManager syncManager) {
        // 无容器的行布局，高度22（匹配图标大小）
        Flow row = (Flow) ((Flow) Flow.row()
            .height(22)).top(172);

        // 猫猫币（左对齐）
        String nekoId = "neko";
        row.child(
            (IWidget) new NekoCoinDisplay(syncManager, nekoId, NekoCurrencyRegistrar.getDisplayName(nekoId)).left(3));

        // 闪烁猫猫币（右移15px：原left=54 → left=69）
        String shimmeringId = "shimmeringNeko";
        row.child(
            (IWidget) new NekoCoinDisplay(syncManager, shimmeringId, NekoCurrencyRegistrar.getDisplayName(shimmeringId))
                .left(69));

        return row;
    }

    /**
     * 创建玩家背包栏（复制自父类 private 方法）
     */
    private IWidget createNekoInventoryRow() {
        return (IWidget) ((Flow) ((Flow) ((Flow) ((Flow) ((Flow) Flow.row()
            .widthRel(1.0f)).height(76)).leftRel(0.0f)).anchorLeft(0.0f)).bottom(5)).childIf(
                this.getBase()
                    .doesBindPlayerInventory(),
                () -> (IWidget) SlotGroupWidget.playerInventory(false)
                    .marginLeft(4));
    }

    /**
     * 创建 IO 列（猫猫币版本）
     * <p>
     * 关键修改：
     * - 输入槽的 changeListener：检测猫猫币 → 自动导入 NekoWallet
     * - "弹出硬币"按钮：绑定 nekoEjectAllCoins syncHandler，弹出所有猫猫币
     * - 保留"弹出物品"按钮、输出槽、动画效果
     */
    private IWidget createNekoIOColumn() {
        return (IWidget) ((ParentWidget) ((ParentWidget) ((ParentWidget) ((ParentWidget) ((ParentWidget) ((ParentWidget) new ParentWidget()
            .excludeAreaInRecipeViewer()).width(50)).height(214)).right(-48)).top(40))
                .widgetTheme(WidgetThemes.BACKGROUND_SIDEPANEL)).child(
                    (IWidget) ((Flow) ((Flow) ((Flow) ((Flow) ((Flow) Flow.column()
                        .child(
                            (IWidget) ((Widget) ((Widget) ((Widget) GuiTextures.INPUT_SPRITE.asWidget()
                                .leftRel(0.5f)).top(8)).width(30)).height(20))).child(
                                    (IWidget) new TextWidget(IKey.lang("vendingmachine.gui.in"))
                                        .textAlign(Alignment.CENTER)
                                        .top(8)
                                        .widthRel(1.0f))).child(
                                            (IWidget) ((Flow) ((Flow) Flow.row()
                                                .child(
                                                    (IWidget) this.createNekoInputSlots()
                                                        .center())).top(20)).height(72))).child(
                                                            (IWidget) ((Flow) ((Flow) ((Flow) Flow.row()
                                                                .child(
                                                                    (IWidget) ((ToggleButton) ((ToggleButton) ((ToggleButton) new ToggleButton()
                                                                        .overlay(
                                                                            new IDrawable[] { GuiTextures.EJECT_SLOTS
                                                                                .asIcon()
                                                                                .size(16) })).tooltipBuilder(
                                                                                    t -> t.addLine(
                                                                                        (IDrawable) IKey.lang(
                                                                                            "vendingmachine.gui.item_eject"))))
                                                                                                .syncHandler(
                                                                                                    "ejectItems"))
                                                                                                        .right(6)))
                                                                                                            .child(
                                                                                                                (IWidget) ((ToggleButton) ((ToggleButton) ((ToggleButton) ((ToggleButton) new ToggleButton()
                                                                                                                    .overlay(
                                                                                                                        new IDrawable[] {
                                                                                                                            GuiTextures.EJECT_COINS
                                                                                                                                .asIcon()
                                                                                                                                .size(
                                                                                                                                    16) }))
                                                                                                                                        .tooltipBuilder(
                                                                                                                                            t -> t
                                                                                                                                                .addLine(
                                                                                                                                                    (IDrawable) IKey
                                                                                                                                                        .str(
                                                                                                                                                            "弹出所有猫猫币"))))
                                                                                                                                                                .playClickSound(
                                                                                                                                                                    false))
                                                                                                                                                                        .syncHandler(
                                                                                                                                                                            "nekoEjectAllCoins"))
                                                                                                                                                                                .left(
                                                                                                                                                                                    6))).top(
                                                                                                                                                                                        98)).height(
                                                                                                                                                                                            18)))
                                                                                                                                                                                                .child(
                                                                                                                                                                                                    (IWidget) ((Flow) ((Flow) Flow
                                                                                                                                                                                                        .row()
                                                                                                                                                                                                        .child(
                                                                                                                                                                                                            this.createNekoDispenserChute()))
                                                                                                                                                                                                                .bottom(
                                                                                                                                                                                                                    6)).height(
                                                                                                                                                                                                                        90)))
                                                                                                                                                                                                                            .right(
                                                                                                                                                                                                                                1));
    }

    /**
     * 创建输入槽组（猫猫币版本）
     * <p>
     * 使用 makeNekoInterceptingSlot 替代原版 makeInterceptingSlot，
     * 检测猫猫币并自动导入 NekoWallet。
     */
    private SlotGroupWidget createNekoInputSlots() {
        UUID playerId = NameCache.INSTANCE.getUUIDFromPlayer(
            this.getBase()
                .getCurrentUser());
        this.nekoInputSlots.clear();
        return SlotGroupWidget.builder()
            .matrix(new String[] { "II", "II", "II", "II" })
            .key('I', index -> {
                ItemSlot slot = this.makeNekoInterceptingSlot(index, playerId);
                this.nekoInputSlots.add(slot);
                return slot;
            })
            .build();
    }

    /**
     * 创建猫猫币拦截槽位
     * <p>
     * 当猫猫币放入输入槽时，自动清除槽位并导入 NekoWallet。
     * 非猫猫币物品保留在槽中。
     */
    private ItemSlot makeNekoInterceptingSlot(int index, UUID playerId) {
        ModularSlot slot = new ModularSlot(this.getBase().inputItems, index);
        return new ItemSlot().slot(
            slot.slotGroup("inputSlotGroup")
                .changeListener((newItem, onlyAmountChanged, client, init) -> {
                    if (!this.getBase()
                        .getActive()) {
                        return;
                    }
                    // 检测猫猫币
                    String nekoCurrencyId = NekoCurrencyRegistrar.getNekoCurrencyId(newItem);
                    if (nekoCurrencyId != null) {
                        // 是猫猫币，清除槽位
                        slot.putStack(null);
                    }
                    if (client) {
                        return;
                    }
                    this.getBase().syncTrades = true;
                    if (nekoCurrencyId != null) {
                        // 猫猫币自动导入 NekoWallet
                        NekoWallet wallet = NekoWalletManager.INSTANCE.getWallet(playerId);
                        if (wallet != null) {
                            wallet.addCount(nekoCurrencyId, newItem.stackSize);
                            NekoWalletManager.INSTANCE.saveWallet(playerId);
                            this.getBase()
                                .playSoundEffect("vendingmachine:coin_insert");
                        }
                        this.forceSyncInputSlots();
                    }
                    // 非猫猫币：不处理（留在槽中）
                }));
    }

    /**
     * 强制同步输入槽
     */
    private void forceSyncInputSlots() {
        this.nekoInputSlots.forEach(
            slot -> slot.getSyncHandler()
                .forceSyncItem());
    }

    /**
     * 创建出货槽（复制自父类 private 方法，保留掉落动画）
     */
    private IWidget createNekoDispenserChute() {
        ParentWidget parentWidget = (ParentWidget) ((ParentWidget) ((ParentWidget) ((ParentWidget) ((ParentWidget) ((ParentWidget) new ParentWidget()
            .fullHeight()).fullWidth()).marginLeft(5)).marginRight(4))
                .background(new IDrawable[] { GuiTextures.DISPENSER_BACKGROUND }))
                    .child(this.getNekoFillPlayerInventoryButton());
        this.addAllItemSlotsAsChildrenNeko(parentWidget);
        return (IWidget) parentWidget.child(NekoVendingMachineGui.getNekoDispenserOverhang());
    }

    /**
     * 添加所有物品槽为子组件（复制自父类 private 方法）
     */
    private void addAllItemSlotsAsChildrenNeko(ParentWidget<?> parentWidget) {
        FallingItemSlotFactory fallingItemSlotFactory = new FallingItemSlotFactory(this.getBase().outputItems, 72);
        IntStream.range(0, 100)
            .forEach(index -> parentWidget.child((IWidget) fallingItemSlotFactory.getFallingItemSlot(index)));
    }

    /**
     * 获取填充玩家背包按钮（复制自父类 private 方法）
     */
    private IWidget getNekoFillPlayerInventoryButton() {
        return ((ButtonWidget) ((ButtonWidget) ((ButtonWidget) new ButtonWidget().fullHeight()).fullWidth())
            .invisible()).playClickSound(false)
                .syncHandler(new InteractionSyncHandler().setOnMousePressed(mousePressed -> {
                    if (mousePressed.mouseButton == 0 && mousePressed.shift) {
                        this.getBase()
                            .fillPlayerInventoryWithDispensedItems();
                    }
                }));
    }

    /**
     * 获取出货槽悬垂（复制自父类 private static 方法）
     */
    private static Widget<?> getNekoDispenserOverhang() {
        return (Widget) ((Widget) IDrawable.of(new IDrawable[] { GuiTextures.DISPENSER_OVERHANG })
            .asWidget()
            .top(0)).widthRel(1.0f);
    }
}

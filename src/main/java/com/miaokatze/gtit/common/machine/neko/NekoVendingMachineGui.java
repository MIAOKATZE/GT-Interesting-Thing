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

import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;

import com.cleanroommc.modularui.api.IPanelHandler;
import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.value.IBoolValue;
import com.cleanroommc.modularui.api.value.IIntValue;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.api.widget.Interactable;
import com.cleanroommc.modularui.drawable.DynamicDrawable;
import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.RichTooltip;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.value.BoolValue;
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
import com.cubefury.vendingmachine.blocks.gui.VendingPageButton;
import com.cubefury.vendingmachine.blocks.gui.VolumeControlGui;
import com.cubefury.vendingmachine.blocks.gui.WalletMode;
import com.cubefury.vendingmachine.blocks.gui.fallingitem.FallingItemSlotFactory;
import com.cubefury.vendingmachine.gui.GuiTextures;
import com.cubefury.vendingmachine.gui.WidgetThemes;
import com.cubefury.vendingmachine.storage.NameCache;
import com.cubefury.vendingmachine.trade.FavouritesTracker;
import com.cubefury.vendingmachine.trade.TradeCategory;
import com.cubefury.vendingmachine.trade.TradeDatabase;
import com.cubefury.vendingmachine.util.Translator;
import com.gtnewhorizon.gtnhlib.config.ConfigurationManager;
import com.miaokatze.gtit.trade.NekoCurrencyRegistrar;
import com.miaokatze.gtit.trade.NekoWallet;
import com.miaokatze.gtit.trade.NekoWalletManager;

import gregtech.api.modularui2.GTWidgetThemes;
import gregtech.common.modularui2.widget.SelectButton;

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
    private final SearchBar nekoSearchBar;
    private Flow nekoWalletButtons;
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
        // 初始化交易分类（与父类相同）
        this.nekoTradeCategories.add(TradeCategory.FAVOURITES);
        this.nekoTradeCategories.add(TradeCategory.ALL);
        this.nekoTradeCategories.addAll(TradeDatabase.INSTANCE.getTradeCategories());
        // 初始化交易列表
        this.nekoTradeLists = new ArrayList<>();
        // 初始化标签控制器和搜索栏（客户端）
        this.nekoTabController = VendingMachine.proxy.isClient() ? new PagedWidget.Controller() : null;
        this.nekoSearchBar = VendingMachine.proxy.isClient() ? this.createNekoSearchBar() : null;
    }

    /**
     * 完全覆盖 build() 方法，不调用 super.build()
     */
    @Override
    public ModularPanel build(PosGuiData guiData, PanelSyncManager syncManager, UISettings uiSettings) {
        this.nekoGuiData = guiData;
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
                // 不调用 VMMusicManager.stopVendingMachineMusic()
            });
            // 不调用 VMMusicManager.startVendingMachineMusic(true)
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
    }

    /**
     * 覆盖注册同步值方法
     * <p>
     * 调用 super.registerSyncValues() 获取基础同步值（包括 slotGroup、ejectItems 等），
     * 然后注册猫猫币专用同步值。原版的 coinAmount_*、ejectCoin_*、ejectCoins 虽然被注册，
     * 但由于不创建对应的按钮，不会被触发。
     */
    @Override
    protected void registerSyncValues(PanelSyncManager syncManager) {
        super.registerSyncValues(syncManager);

        UUID playerId = NameCache.INSTANCE.getUUIDFromPlayer(
            this.getBase()
                .getCurrentUser());

        // 注册猫猫币余额同步值（S2C）
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

            // 注册单种猫猫币弹出同步值（C2S）
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

        // 注册弹出所有猫猫币同步值（C2S）
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
        return this.nekoTradeCategories.get(this.nekoTabController.getActivePageIndex());
    }

    @Override
    public void restorePreviousSettings() {
        if (this.nekoTabController.isInitialised()) {
            this.nekoTabController.setPage(lastPage);
        }
        this.nekoSearchBar.setText(lastSearch);
    }

    @Override
    public void updateTradeDisplay(Map<TradeCategory, List<TradeItemDisplay>> trades) {
        if (this.favouritesTabWidget != null) {
            this.favouritesTabWidget.setEnabled(
                !trades.get(TradeCategory.FAVOURITES)
                    .isEmpty());
            if (trades.get(TradeCategory.FAVOURITES)
                .isEmpty() && this.nekoTabController.getActivePageIndex() == 0) {
                this.nekoTabController.setPage(1);
            }
        }
        this.updateTradeDisplayNeko(trades, this.displayedTradesTiles);
        this.updateTradeDisplayNeko(trades, this.displayedTradesList);
        this.updateTabHighlightingNeko(trades);
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
     * 创建分类标签（使用猫猫币专用 tradeCategories）
     */
    private IWidget createNekoCategoryTabs(PagedWidget.Controller tabController) {
        Flow tabColumn = (Flow) ((Flow) ((Flow) ((Flow) ((Flow) ((Flow) Flow.column()
            .excludeAreaInRecipeViewer()).width(40)).height(300)).left(-29)).top(40)).coverChildren();
        for (int i = 0; i < this.nekoTradeCategories.size(); ++i) {
            int index = i;
            tabColumn.child(
                (IWidget) new VendingPageButton(i, tabController, this.nekoTradeCategories, this.highlightedTabs)
                    .tab(GuiTextures.TAB_LEFT, -1)
                    .tooltipBuilder(builder -> {
                        builder.clearText();
                        builder.addLine(
                            Translator.translate(
                                this.nekoTradeCategories.get(index)
                                    .getUnlocalized_name(),
                                new Object[0]));
                    }));
            if (this.nekoTradeCategories.get(i) != TradeCategory.FAVOURITES) continue;
            this.favouritesTabWidget = (IWidget) tabColumn.getChildren()
                .get(
                    tabColumn.getChildren()
                        .size() - 1);
        }
        return tabColumn;
    }

    /**
     * 创建 QoL 按钮列（音量、显示模式、排序模式）
     * <p>
     * 移除了 BGM 播放逻辑，保留音量面板和显示/排序切换。
     */
    private IWidget createNekoQolButtonColumn() {
        return ((Grid) ((Grid) ((Grid) ((Grid) new Grid().left(-33)).excludeAreaInRecipeViewer()).top(1))
            .minElementMargin(1)
            .coverChildren()).grid(
                Arrays.asList(
                    Arrays.asList(
                        this.nekoVolumeButton = (CycleButtonWidget) ((CycleButtonWidget) ((CycleButtonWidget) ((CycleButtonWidget) new CycleButtonWidget()
                            .size(14)).overlay(
                                new IDrawable[] { new DynamicDrawable(
                                    () -> VMConfig.music.current_track.getTexture()
                                        .size(14)) })).stateCount(MusicTrack.values().length)
                                            .value(
                                                (IIntValue) new IntValue.Dynamic(
                                                    () -> VMConfig.music.current_track.ordinal(),
                                                    val -> {
                                                        if (Interactable.hasShiftDown()) {
                                                            this.nekoVolumePanel.togglePanel();
                                                        } else {
                                                            VMConfig.music.current_track = MusicTrack.values()[val];
                                                            // 不调用 VMMusicManager
                                                            ConfigurationManager
                                                                .save((Class[]) new Class[] { VMConfig.class });
                                                        }
                                                    }))
                                            .tooltipDynamic(builder -> {
                                                builder.clearText();
                                                builder.addLine(
                                                    IKey.lang("vendingmachine.gui.display_track") + " "
                                                        + VMConfig.music.current_track.getLocalizedName());
                                                builder.addLine(
                                                    (IDrawable) IKey
                                                        .lang(
                                                            "vendingmachine.gui.volume.tooltip_volume_display",
                                                            new Object[] { VolumeControlGui.getVolumeAsString() })
                                                        .style(EnumChatFormatting.GRAY));
                                                builder.addLine(
                                                    (IDrawable) IKey
                                                        .lang("vendingmachine.gui.volume.tooltip_open_panel")
                                                        .style(EnumChatFormatting.GRAY));
                                                MTEVendingMachineGui.setForceRefresh();
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
     * 创建交易 UI（复制自父类 private 方法，使用猫猫币专用字段）
     */
    private IWidget createNekoTradeUI(TradeMainPanel rootPanel, PagedWidget.Controller tabController) {
        PagedWidget paged = (PagedWidget) ((PagedWidget) ((PagedWidget) ((PagedWidget) new PagedWidget().name("paged"))
            .width(162)).controller(tabController)
                .background(new IDrawable[] { GuiTextures.TEXT_FIELD_BACKGROUND })).height(146);
        for (TradeCategory category : this.nekoTradeCategories) {
            int i;
            ListWidget tradeList = ((ListWidget) ((ListWidget) ((ListWidget) ((ListWidget) new ListWidget()
                .name("items")).width(161)).top(1)).height(144)).collapseDisabledChild(true);
            this.nekoTradeLists.add(tradeList);
            tradeList.child(
                (IWidget) Flow.row()
                    .height(2));
            Flow statusRow = (Flow) ((Flow) ((Flow) ((Flow) ((Flow) Flow.row()
                .height(10)).width(154)).marginLeft(2))
                    .child((IWidget) new TextWidget(IKey.lang("vendingmachine.gui.error.incomplete_structure"))))
                        .setEnabledIf(
                            slot -> !this.getBase()
                                .getActive());
            tradeList.child((IWidget) statusRow);
            Flow row = (Flow) ((Flow) ((Flow) new TradeRow().height(29)).width(154)).marginLeft(2);
            for (i = 0; i < 300; ++i) {
                final int index = i;
                this.displayedTradesTiles.get(category)
                    .get(i)
                    .setRootPanel(rootPanel);
                row.child(
                    (IWidget) ((ItemDisplayWidget) ((ItemDisplayWidget) ((ItemDisplayWidget) this.displayedTradesTiles
                        .get(category)
                        .get(i)
                        .tooltipDynamic(builder -> {
                            builder.clearText();
                            Map<TradeCategory, List<TradeItemDisplayWidget>> map = this.displayedTradesTiles;
                            synchronized (map) {
                                if (index < this.displayedTradesTiles.get(category)
                                    .size()) {
                                    this.constructTradeTooltipNeko(
                                        (RichTooltip) builder,
                                        this.displayedTradesTiles.get(category)
                                            .get(index)
                                            .getDisplay());
                                }
                            }
                        })).tooltipAutoUpdate(true)).setEnabledIf(slot -> {
                            if (!this.getBase()
                                .getActive()) {
                                return false;
                            }
                            TradeItemDisplayWidget display = (TradeItemDisplayWidget) (Object) slot;
                            return VMConfig.gui.display_type == display.displayType && display.getDisplay() != null;
                        })).margin(2));
                if (i % 3 != 2) continue;
                tradeList.child((IWidget) row);
                row = (Flow) ((Flow) ((Flow) new TradeRow().height(29)).width(154)).marginLeft(2);
            }
            if (row.hasChildren()) {
                tradeList.child((IWidget) row);
            }
            row = (Flow) ((Flow) ((Flow) new TradeRow().height(14)).width(154)).marginLeft(2);
            for (i = 0; i < 300; ++i) {
                final int index = i;
                this.displayedTradesList.get(category)
                    .get(i)
                    .setRootPanel(rootPanel);
                row.child(
                    (IWidget) ((ItemDisplayWidget) ((ItemDisplayWidget) this.displayedTradesList.get(category)
                        .get(i)
                        .tooltipDynamic(builder -> {
                            builder.clearText();
                            Map<TradeCategory, List<TradeItemDisplayWidget>> map = this.displayedTradesList;
                            synchronized (map) {
                                if (index < this.displayedTradesList.get(category)
                                    .size()) {
                                    this.constructTradeTooltipNeko(
                                        (RichTooltip) builder,
                                        this.displayedTradesList.get(category)
                                            .get(index)
                                            .getDisplay());
                                }
                            }
                        })).tooltipAutoUpdate(true)).setEnabledIf(slot -> {
                            if (!this.getBase()
                                .getActive()) {
                                return false;
                            }
                            TradeItemDisplayWidget display = (TradeItemDisplayWidget) (Object) slot;
                            return VMConfig.gui.display_type == display.displayType && display.getDisplay() != null;
                        }));
                tradeList.child((IWidget) row);
                row = (Flow) ((Flow) ((Flow) new TradeRow().height(14)).width(154)).marginLeft(2);
            }
            tradeList.child(
                (IWidget) Flow.row()
                    .height(2));
            paged.addPage((IWidget) tradeList);
        }
        return (IWidget) ((Flow) ((Flow) Flow.row()
            .child((IWidget) paged.top(0))).left(3)).top(24);
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
            if (!cur.fromCurrency.isEmpty() || !cur.fromItems.isEmpty()) {
                builder.addLine(
                    (IDrawable) IKey.lang("vendingmachine.gui.required_inputs")
                        .style(new EnumChatFormatting[] { IKey.DARK_GREEN, IKey.ITALIC }));
                for (com.cubefury.vendingmachine.trade.CurrencyItem currencyItem : cur.fromCurrency) {
                    builder.addLine(
                        (IDrawable) IKey.str(currencyItem.value + " " + currencyItem.type.getLocalizedName())
                            .style(IKey.DARK_GREEN));
                }
                for (com.cubefury.vendingmachine.util.BigItemStack fromItem : cur.fromItems) {
                    // 检查是否为猫猫币
                    String nekoCurrencyId = NekoCurrencyRegistrar.getNekoCurrencyId(fromItem.getBaseStack());
                    if (nekoCurrencyId != null) {
                        // 猫猫币显示为货币样式
                        builder.addLine(
                            (IDrawable) IKey
                                .str(fromItem.stackSize + " " + NekoCurrencyRegistrar.getDisplayName(nekoCurrencyId))
                                .style(IKey.DARK_GREEN));
                    } else {
                        // 非猫猫币：原版显示
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
     * 只创建 2 个 NekoCoinDisplay（neko + shimmeringNeko），不显示原版 14 个 CoinDisplay。
     */
    private IWidget createNekoCoinInventoryRow(TradeMainPanel panel, PanelSyncManager syncManager) {
        Flow parent = (Flow) ((Flow) ((Flow) ((Flow) Flow.row()
            .width(162)).height(36)).top(172)).left(3);

        // 创建猫猫币显示列
        Flow coinColumn = (Flow) Flow.column()
            .width(120);
        for (String currencyId : NekoCurrencyRegistrar.getNekoCurrencyIds()) {
            String displayName = NekoCurrencyRegistrar.getDisplayName(currencyId);
            coinColumn.child((IWidget) new NekoCoinDisplay(syncManager, currencyId, displayName));
        }
        parent.child((IWidget) coinColumn.left(3));

        // 钱包模式按钮（个人/团队）
        this.nekoWalletButtons = (Flow) ((Flow) ((Flow) ((Flow) ((Flow) Flow.row()
            .childPadding(2)
            .coverChildren()).marginTop(5)).rightRel(1.0f, 2, 1.0f))
                .child((IWidget) this.createNekoWalletButton(WalletMode.PERSONAL)))
                    .child((IWidget) this.createNekoWalletButton(WalletMode.TEAM));
        parent.child((IWidget) this.nekoWalletButtons);

        return parent;
    }

    /**
     * 创建钱包模式按钮（复制自父类 private 方法）
     */
    private ToggleButton createNekoWalletButton(WalletMode mode) {
        return (ToggleButton) ((ToggleButton) ((ToggleButton) ((ToggleButton) new SelectButton()
            .value((IBoolValue) new BoolValue.Dynamic(() -> this.walletMode == mode, value -> {
                VMConfig.gui.wallet_mode = this.walletMode = mode;
                this.shouldSyncWalletMode = true;
                MTEVendingMachineGui.setForceRefresh();
            }))
            .size(20)).padding(1)).overlay(new IDrawable[] { mode.getTexture() }))
                .tooltip(richTooltip -> richTooltip.add((IDrawable) IKey.lang(mode.getLocalizedName())));
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

package com.miaokatze.gtit.common.machine.v2;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.world.World;

import com.cleanroommc.modularui.api.IPanelHandler;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.api.widget.Interactable;
import com.cleanroommc.modularui.drawable.DynamicDrawable;
import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.value.sync.BooleanSyncValue;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.StringSyncValue;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.ListWidget;
import com.cleanroommc.modularui.widgets.PagedWidget;
import com.cleanroommc.modularui.widgets.SlotGroupWidget;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.cleanroommc.modularui.widgets.layout.Grid;
import com.cleanroommc.modularui.widgets.slot.ItemSlot;
import com.cleanroommc.modularui.widgets.slot.ModularSlot;
import com.miaokatze.gtit.client.gui.NekoCoinDisplayV2;
import com.miaokatze.gtit.client.gui.NekoDisplayType;
import com.miaokatze.gtit.client.gui.NekoFallingItemSlotFactory;
import com.miaokatze.gtit.client.gui.NekoGuiTextures;
import com.miaokatze.gtit.client.gui.NekoMusicTrack;
import com.miaokatze.gtit.client.gui.NekoPageButtonV2;
import com.miaokatze.gtit.client.gui.NekoSearchBar;
import com.miaokatze.gtit.client.gui.NekoSortMode;
import com.miaokatze.gtit.client.gui.NekoTradeItemDisplay;
import com.miaokatze.gtit.client.gui.NekoTradeItemDisplayWidget;
import com.miaokatze.gtit.client.gui.NekoTradeMainPanel;
import com.miaokatze.gtit.client.gui.NekoTradeRow;
import com.miaokatze.gtit.client.gui.NekoVolumeControlGui;
import com.miaokatze.gtit.client.gui.NekoWalletMode;
import com.miaokatze.gtit.common.machine.neko.NekoMusicEventHandler;
import com.miaokatze.gtit.config.NekoMusicConfig;
import com.miaokatze.gtit.main.GTInterestingThing;
import com.miaokatze.gtit.trade.NekoCurrencyRegistrar;
import com.miaokatze.gtit.trade.NekoPageEntry;
import com.miaokatze.gtit.trade.NekoPageRegistry;
import com.miaokatze.gtit.trade.NekoWallet;
import com.miaokatze.gtit.trade.NekoWalletManager;
import com.miaokatze.gtit.trade.v2.NekoFavouritesTracker;
import com.miaokatze.gtit.trade.v2.NekoHistoryManager;
import com.miaokatze.gtit.trade.v2.NekoTrade;
import com.miaokatze.gtit.trade.v2.NekoTradeCategory;
import com.miaokatze.gtit.trade.v2.NekoTradeDatabase;
import com.miaokatze.gtit.trade.v2.NekoTradeGroup;
import com.miaokatze.gtit.trade.v2.NekoTradeHistory;
import com.miaokatze.gtit.trade.v2.NekoTradeResult;

import gregtech.common.gui.modularui.multiblock.base.MTEMultiBlockBaseGui;

/**
 * 猫猫售货机 V2 GUI（全量复刻版）
 * <p>
 * 继承 GT5U 的 {@link MTEMultiBlockBaseGui}，复用标准多方块 GUI 框架，
 * 同时集成步骤3-10创建的所有本地组件，实现全量复刻 VM 猫猫机 UI 的目标。
 * <p>
 * <b>核心架构</b>：
 * <ul>
 * <li>主面板使用 {@link NekoTradeMainPanel}，实现其 {@link NekoTradeMainPanel.PanelCallback} 接口</li>
 * <li>交易列表使用 {@link PagedWidget} + 预分配 300 个 {@link NekoTradeItemDisplayWidget}</li>
 * <li>搜索栏使用 {@link NekoSearchBar}，通过 SearchListener 回调解耦</li>
 * <li>硬币显示使用 {@link NekoCoinDisplayV2}，含 serp 缓动动画</li>
 * <li>标签页使用 {@link NekoPageButtonV2}，含高亮覆盖层和悬停动画</li>
 * <li>BGM 使用 {@link NekoMusicEventHandler}，支持淡入淡出和音量控制</li>
 * <li>音量控制使用 {@link NekoVolumeControlGui}，绑定 {@link NekoMusicConfig#music_volume}</li>
 * </ul>
 * <p>
 * <b>保留的同步值系统</b>：所有原有同步值（currentTabSync、searchTextSync、sortModeSync、
 * tradeRequestSync、tradeResultSync、bqLockStatusSync、cooldownStatusSync、coinAmountSyncs、
 * ejectAllCoinsSync、importCoinsSync）均保留，新增 displayTypeSync、favouriteToggleSync 和
 * tradeableStatusSync（服务端综合计算可交易状态并同步到客户端）。
 * <p>
 * 不依赖任何 VM mod 类（com.cubefury.vendingmachine.*）。
 *
 * @see NekoTradeMainPanel
 * @see NekoTradeItemDisplayWidget
 * @see NekoSearchBar
 * @see NekoCoinDisplayV2
 * @see NekoPageButtonV2
 */
public class NekoVMGuiV2 extends MTEMultiBlockBaseGui<MTENekoVendingMachineV2>
    implements NekoTradeMainPanel.PanelCallback, NekoTradeItemDisplayWidget.TradeActionCallback {

    // ==================== 常量 ====================

    /** 面板宽度 */
    private static final int PANEL_WIDTH = 178;
    /** 面板高度（与 V1 的 size(178, 320) 保持一致） */
    private static final int PANEL_HEIGHT = 320;
    /** 每种显示模式预分配的 Widget 数量（与 VM 的 MAX_TRADES 一致） */
    private static final int MAX_TRADES = 300;
    /** TILE 模式每行的 Widget 数量 */
    private static final int TILE_ITEMS_PER_ROW = 3;
    /** TILE 模式 Widget 宽度 */
    private static final int TILE_ITEM_WIDTH = NekoTradeItemDisplayWidget.TILE_ITEM_WIDTH;
    /** TILE 模式 Widget 高度 */
    private static final int TILE_ITEM_HEIGHT = NekoTradeItemDisplayWidget.TILE_ITEM_HEIGHT;
    /** LIST 模式 Widget 宽度 */
    private static final int LIST_ITEM_WIDTH = NekoTradeItemDisplayWidget.LIST_ITEM_WIDTH;
    /** LIST 模式 Widget 高度 */
    private static final int LIST_ITEM_HEIGHT = NekoTradeItemDisplayWidget.LIST_ITEM_HEIGHT;
    /** 交易行宽度 */
    private static final int TRADE_ROW_WIDTH = 154;
    /** 交易列表最小高度 */
    private static final int TRADE_LIST_MIN_HEIGHT = 50;

    // ==================== 同步值字段 ====================

    /** 当前标签页索引（C2S：客户端切换标签时发送到服务端） */
    private IntSyncValue currentTabSync;
    /** 搜索文本（C2S：客户端输入时发送到服务端） */
    private StringSyncValue searchTextSync;
    /** 排序模式（C2S：0=SMART, 1=ALPHABET） */
    private IntSyncValue sortModeSync;
    /** 显示模式（C2S：0=TILE, 1=LIST） */
    private IntSyncValue displayTypeSync;
    /** 交易请求（C2S：Shift+Click 时发送 "groupId:tradeIndex"） */
    private StringSyncValue tradeRequestSync;
    /** 交易结果消息（S2C：服务端处理完交易后发送结果文本） */
    private StringSyncValue tradeResultSync;
    /** BQ 锁定状态字符串（S2C：服务端构建 "groupId:true/false,..." 发送到客户端） */
    private StringSyncValue bqLockStatusSync;
    /** 冷却状态字符串（S2C：服务端构建 "groupId:tradeIndex:seconds,..." 发送到客户端） */
    private StringSyncValue cooldownStatusSync;
    /** 可交易状态字符串（S2C：服务端构建 "groupId:tradeIndex:true/false,..." 发送到客户端） */
    private StringSyncValue tradeableStatusSync;
    /** 收藏切换请求（C2S：Ctrl+Click 时发送 "groupId:tradeIndex"） */
    private StringSyncValue favouriteToggleSync;
    /** 弹出所有猫猫币（C2S：按钮点击时发送） */
    private BooleanSyncValue ejectAllCoinsSync;
    /** 导入猫猫币（C2S：按钮点击时发送） */
    private BooleanSyncValue importCoinsSync;
    /** 货币显示开关（C2S） */
    private BooleanSyncValue showCoinsSync;
    /** 弹出物品（清空输出槽）开关（C2S） */
    private BooleanSyncValue ejectItemsSync;
    /** 填充玩家背包开关（C2S，Shift+左键出货槽触发） */
    private BooleanSyncValue fillPlayerInventorySync;
    /** 是否显示猫猫币余额行 */
    private boolean showCoins = true;
    /** 各货币余额同步值映射 */
    private final Map<String, IntSyncValue> coinAmountSyncs = new HashMap<>();

    // ==================== UI 状态镜像（客户端+服务端共享） ====================

    /** 当前标签页索引（默认 0=FAVOURITES 分类） */
    private int currentTabId = 0;
    /** 搜索文本 */
    private String searchText = "";
    /** 排序模式：0=SMART, 1=ALPHABET */
    private int sortMode = 0;
    /** 显示模式：0=TILE, 1=LIST */
    private int displayType = 0;

    // ==================== 客户端状态（S2C 同步填充） ====================

    /** BQ 锁定状态映射：groupId → 是否锁定（true=锁定） */
    private final Map<UUID, Boolean> bqLockStatusMap = new HashMap<>();
    /** 冷却状态映射："groupId:tradeIndex" → 剩余秒数 */
    private final Map<String, Long> cooldownStatusMap = new HashMap<>();
    /** 可交易状态映射："groupId:tradeIndex" → 是否可交易（true=可交易） */
    private final Map<String, Boolean> tradeableStatusMap = new HashMap<>();

    // ==================== 服务端状态 ====================

    /** 可交易状态字符串缓存（服务端，减少频繁调用 checkTrade） */
    private String cachedTradeableStatusString = "";
    /** 可交易状态是否需要重新计算（服务端，依赖项变化时置 true） */
    private boolean tradeableStatusDirty = true;
    /** 上次重建可交易状态字符串时的世界 tick */
    private long lastTradeableStatusRebuildTick = -1;
    /** 可交易状态缓存有效 tick 数（20 tick = 1 秒） */
    private static final long TRADEABLE_STATUS_CACHE_TICKS = 20;

    /** 弹出所有猫猫币标志（服务端处理后重置为 false） */
    private boolean nekoEjectAllCoins = false;
    /** 导入猫猫币标志（服务端处理后重置为 false） */
    private boolean nekoImportCoins = false;
    /** 弹出物品（清空输出槽）标志（服务端处理后重置为 false） */
    private boolean nekoEjectItems = false;
    /** 填充玩家背包标志（服务端处理后重置为 false） */
    private boolean nekoFillPlayerInventory = false;
    /** 弹出单种猫猫币标志映射 */
    private final Map<String, Boolean> nekoEjectSingleCoin = new HashMap<>();
    /** 交易结果消息（服务端设置，通过 tradeResultSync 同步到客户端） */
    private String tradeResultMessage = "";

    // ==================== 预分配 Widget ====================

    /** TILE 模式预分配 Widget：分类 → Widget 列表（每分类 300 个） */
    private final Map<NekoTradeCategory, List<NekoTradeItemDisplayWidget>> displayedTradesTiles = new HashMap<>();
    /** LIST 模式预分配 Widget：分类 → Widget 列表（每分类 300 个） */
    private final Map<NekoTradeCategory, List<NekoTradeItemDisplayWidget>> displayedTradesList = new HashMap<>();
    /** 交易分类列表（决定标签页顺序：FAVOURITES, ALL, ...其他） */
    private final List<NekoTradeCategory> tradeCategories = new ArrayList<>();
    /** 高亮标签页集合（搜索匹配时高亮对应标签） */
    private final Set<NekoTradeCategory> highlightedTabs = new HashSet<>();

    // ==================== 其他字段 ====================

    /** V2 GUI 是否打开（客户端，供 NekoMusicEventHandler 检测 GUI 状态） */
    public static boolean isV2GuiOpen = false;

    /** GUI 位置数据引用（build 时设置，供同步值 getter 使用） */
    private PosGuiData guiData;
    /** 同步管理器引用（供 isClient() 判断和 PanelCallback 使用） */
    private PanelSyncManager syncManagerRef;
    /** 分页控制器（客户端，管理标签页切换） */
    private PagedWidget.Controller tabController;
    /** 搜索栏组件（客户端） */
    private NekoSearchBar searchBar;
    /** 音量面板处理器（客户端，打开/关闭音量控制面板） */
    private IPanelHandler volumePanel;
    /** 主面板引用（用于回调和方法调用） */
    private NekoTradeMainPanel mainPanel;
    /** 输入槽 Widget 引用（服务端在弹出/入账后强制同步槽位状态到客户端） */
    private final List<ItemSlot> inputSlotRefs = new ArrayList<>();

    // ==================== 构造器 ====================

    /**
     * 构造猫猫售货机 V2 GUI
     *
     * @param machine 关联的猫猫售货机 V2 机器实例
     */
    public NekoVMGuiV2(MTENekoVendingMachineV2 machine) {
        super(machine);
        // 初始化各货币的弹出标志
        for (String currencyId : NekoCurrencyRegistrar.getNekoCurrencyIds()) {
            nekoEjectSingleCoin.put(currencyId, false);
        }
        // 初始化交易分类列表：FAVOURITES 始终第一位，其余按 NekoPageRegistry 动态生成
        tradeCategories.add(NekoTradeCategory.FAVOURITES);

        List<NekoPageEntry> pages = NekoPageRegistry.getAllPages();
        if (pages != null && !pages.isEmpty()) {
            // 从 NekoPageRegistry 加载标签页配置，与 V1 保持一致的标签页顺序
            for (NekoPageEntry page : pages) {
                if (page != null) {
                    tradeCategories.add(NekoTradeCategory.ofTabId(page.getId()));
                }
            }
        } else {
            // 向后兼容：若 NekoPageRegistry 未初始化，则扫描交易数据库使用到的分类
            Set<NekoTradeCategory> usedCategories = new TreeSet<>();
            for (NekoTradeGroup group : NekoTradeDatabase.INSTANCE.getAllTradeGroups()
                .values()) {
                NekoTradeCategory cat = group.getCategory();
                if (cat != null && !cat.isFavourites() && !cat.isUnknown()) {
                    usedCategories.add(cat);
                }
            }
            tradeCategories.addAll(usedCategories);
            // 至少保留 UNKNOWN 兜底
            if (tradeCategories.size() <= 1) {
                tradeCategories.add(NekoTradeCategory.UNKNOWN);
            }
        }
    }

    // ==================== build 方法 ====================

    /**
     * 构建 GUI 面板
     * <p>
     * 创建 {@link NekoTradeMainPanel} 作为主面板容器，
     * 注册同步值，添加标签列、QoL 按钮列、主内容列和 IO 列。
     *
     * @param guiData     GUI 位置数据
     * @param syncManager 面板同步管理器
     * @param uiSettings  UI 设置
     * @return ModularPanel 实例
     */
    @Override
    public ModularPanel build(PosGuiData guiData, PanelSyncManager syncManager, UISettings uiSettings) {
        this.guiData = guiData;
        this.syncManagerRef = syncManager;

        // 注册所有同步值（包含 GT5U 标准 + 猫猫机自定义）
        registerSyncValues(syncManager);

        // 客户端：初始化预分配 Widget 和分页控制器
        if (syncManager.isClient()) {
            initPreAllocatedWidgets();
            tabController = new PagedWidget.Controller();
        }

        // 创建 NekoTradeMainPanel 作为主面板（实现 PanelCallback 回调）
        NekoTradeMainPanel panel = new NekoTradeMainPanel("MTEMultiBlockBase", this, guiData, syncManager);
        panel.size(PANEL_WIDTH, PANEL_HEIGHT);
        panel.padding(4);
        this.mainPanel = panel;

        // 客户端：启动 BGM、注册关闭回调、设置 GUI 状态
        if (syncManager.isClient()) {
            isV2GuiOpen = true;
            // 通知 NekoMusicEventHandler GUI 已打开
            NekoMusicEventHandler.onGuiOpened();
            panel.onCloseAction(() -> {
                // 关闭 BGM 并清理 GUI 打开标志
                closeNekoGuiMusic();
            });
        }

        // 左侧标签列
        if (syncManager.isClient()) {
            panel.child(createTabColumn());
            panel.child(createQolButtonColumn());
        }

        // 主内容列
        panel.child(createMainColumn(syncManager));

        // 右侧 IO 列
        panel.child(createIOColumn());

        return panel;
    }

    // ==================== 同步值注册 ====================

    /**
     * 注册同步值
     * <p>
     * 先调用 super.registerSyncValues() 注册 GT5U 标准同步值，
     * 然后注册猫猫机 V2 的自定义同步值。
     *
     * @param syncManager 面板同步管理器
     */
    @Override
    protected void registerSyncValues(PanelSyncManager syncManager) {
        // 注册 GT5U 标准同步值
        super.registerSyncValues(syncManager);

        // 注册槽位分组，启用 shift 快速转移（与 V1/VM 原版一致）
        // 参数：组名、行长度(2列)、是否允许从玩家背包 shift 转移到此组
        // 输入槽：允许 shift 转入；输出槽：禁止 shift 转入（但允许 shift 转出到背包）
        syncManager.registerSlotGroup("inputSlotGroup", 2, true);
        syncManager.registerSlotGroup("outputSlotGroup", 2, false);

        // 获取玩家 UUID（服务端用于查询钱包余额、BQ 状态等）
        final UUID playerId = getPlayerId();

        // --- 当前标签页索引（C2S）---
        currentTabSync = new IntSyncValue(() -> currentTabId, val -> { currentTabId = val; });
        currentTabSync.allowC2S();
        syncManager.syncValue("nekoV2CurrentTab", currentTabSync);

        // --- 搜索文本（C2S）---
        searchTextSync = new StringSyncValue(() -> searchText, val -> { searchText = val == null ? "" : val; });
        searchTextSync.allowC2S();
        syncManager.syncValue("nekoV2SearchText", searchTextSync);

        // --- 排序模式（C2S：0=SMART, 1=ALPHABET）---
        sortModeSync = new IntSyncValue(() -> sortMode, val -> { sortMode = val; });
        sortModeSync.allowC2S();
        syncManager.syncValue("nekoV2SortMode", sortModeSync);

        // --- 显示模式（C2S：0=TILE, 1=LIST）---
        displayTypeSync = new IntSyncValue(() -> displayType, val -> { displayType = val; });
        displayTypeSync.allowC2S();
        syncManager.syncValue("nekoV2DisplayType", displayTypeSync);

        // --- 交易请求（C2S，Shift+Click 触发）---
        tradeRequestSync = new StringSyncValue(() -> "", val -> {
            if (val != null && !val.isEmpty()) {
                processTradeRequest(val, playerId);
            }
        });
        tradeRequestSync.allowC2S();
        syncManager.syncValue("nekoV2TradeRequest", tradeRequestSync);

        // --- 收藏切换请求（C2S，Ctrl+Click 触发）---
        favouriteToggleSync = new StringSyncValue(() -> "", val -> {
            if (val != null && !val.isEmpty()) {
                processFavouriteToggle(val, playerId);
            }
        });
        favouriteToggleSync.allowC2S();
        syncManager.syncValue("nekoV2FavouriteToggle", favouriteToggleSync);

        // --- 交易结果消息（S2C）---
        tradeResultSync = new StringSyncValue(
            () -> tradeResultMessage,
            val -> tradeResultMessage = val == null ? "" : val);
        syncManager.syncValue("nekoV2TradeResult", tradeResultSync);

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
            if (mainPanel != null) {
                mainPanel.notifyCurrencyUpdate();
            }
        });
        syncManager.syncValue("nekoV2TradeableStatus", tradeableStatusSync);

        // --- 各货币余额（S2C）---
        // 注意：syncHandler 名称必须与 NekoCoinDisplayV2 期望的一致
        for (String currencyId : NekoCurrencyRegistrar.getNekoCurrencyIds()) {
            final String cid = currencyId;
            IntSyncValue coinAmountSyncer = new IntSyncValue(() -> {
                if (syncManager.isClient() || playerId == null) return 0;
                NekoWallet wallet = NekoWalletManager.INSTANCE.getWallet(playerId);
                return wallet == null ? 0 : wallet.getCount(cid);
            });
            // 货币余额变化时标记可交易状态为脏并立即触发重新同步
            if (!syncManager.isClient()) {
                coinAmountSyncer.setChangeListener(() -> {
                    tradeableStatusDirty = true;
                    if (tradeableStatusSync != null) {
                        tradeableStatusSync.notifyUpdate();
                    }
                });
            }
            syncManager.syncValue("nekoCoinAmount_" + currencyId, coinAmountSyncer);
            coinAmountSyncs.put(currencyId, coinAmountSyncer);
        }

        // BQ 锁定/冷却状态变化时也需要重新计算可交易状态
        if (!syncManager.isClient()) {
            Runnable tradeableStatusDirtyMarker = () -> {
                tradeableStatusDirty = true;
                if (tradeableStatusSync != null) {
                    tradeableStatusSync.notifyUpdate();
                }
            };
            bqLockStatusSync.setChangeListener(tradeableStatusDirtyMarker);
            cooldownStatusSync.setChangeListener(tradeableStatusDirtyMarker);
        }

        // --- 弹出单种猫猫币（C2S）---
        // 注意：syncHandler 名称必须与 NekoCoinDisplayV2 期望的一致
        for (String currencyId : NekoCurrencyRegistrar.getNekoCurrencyIds()) {
            final String cid = currencyId;
            BooleanSyncValue ejectCoinSyncer = new BooleanSyncValue(
                () -> nekoEjectSingleCoin.getOrDefault(cid, false),
                val -> {
                    nekoEjectSingleCoin.put(cid, val);
                    if (val) {
                        doNekoEjectCoin(cid, playerId);
                    }
                });
            ejectCoinSyncer.allowC2S();
            syncManager.syncValue("nekoEjectCoin_" + currencyId, ejectCoinSyncer);
        }

        // --- 弹出所有猫猫币（C2S）---
        ejectAllCoinsSync = new BooleanSyncValue(() -> nekoEjectAllCoins, val -> {
            nekoEjectAllCoins = val;
            if (val) {
                doNekoEjectAllCoins(playerId);
            }
        });
        ejectAllCoinsSync.allowC2S();
        syncManager.syncValue("nekoV2EjectAllCoins", ejectAllCoinsSync);

        // --- 导入猫猫币（C2S）---
        importCoinsSync = new BooleanSyncValue(() -> nekoImportCoins, val -> {
            nekoImportCoins = val;
            if (val) {
                doNekoImportCoins(playerId);
            }
        });
        importCoinsSync.allowC2S();
        syncManager.syncValue("nekoV2ImportCoins", importCoinsSync);

        // --- 弹出物品（清空输出槽并掉落到机器旁，C2S）---
        ejectItemsSync = new BooleanSyncValue(() -> nekoEjectItems, val -> {
            nekoEjectItems = val;
            if (val) {
                doNekoEjectItems();
            }
        });
        ejectItemsSync.allowC2S();
        syncManager.syncValue("nekoV2EjectItems", ejectItemsSync);

        // --- 填充玩家背包（C2S，Shift+左键出货槽时触发）---
        fillPlayerInventorySync = new BooleanSyncValue(() -> nekoFillPlayerInventory, val -> {
            nekoFillPlayerInventory = val;
            if (val) {
                doNekoFillPlayerInventory(playerId);
            }
        });
        fillPlayerInventorySync.allowC2S();
        syncManager.syncValue("nekoV2FillPlayerInventory", fillPlayerInventorySync);

        // --- 货币显示开关（C2S）---
        showCoinsSync = new BooleanSyncValue(() -> showCoins, val -> { showCoins = val; });
        showCoinsSync.allowC2S();
        syncManager.syncValue("nekoV2ShowCoins", showCoinsSync);
    }

    // ==================== PanelCallback 接口实现 ====================

    @Override
    public UUID getPlayerId() {
        if (guiData == null) return null;
        try {
            EntityPlayer player = guiData.getPlayer();
            return player != null ? player.getUniqueID() : null;
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public String getSearchText() {
        return searchText;
    }

    @Override
    public boolean isSearchBarFocused() {
        return searchBar != null && searchBar.isFocused();
    }

    @Override
    public NekoDisplayType getDisplayType() {
        return displayType == 0 ? NekoDisplayType.TILE : NekoDisplayType.LIST;
    }

    @Override
    public NekoSortMode getSortMode() {
        return sortMode == 0 ? NekoSortMode.SMART : NekoSortMode.ALPHABET;
    }

    @Override
    public NekoWalletMode getWalletMode() {
        // V2 阶段仅支持个人钱包
        return NekoWalletMode.PERSONAL;
    }

    @Override
    public int getRefreshInterval() {
        // 默认 20 tick（1秒）刷新一次
        return 20;
    }

    @Override
    public boolean isClient() {
        return syncManagerRef != null && syncManagerRef.isClient();
    }

    @Override
    public NekoTradeCategory getActiveCategory() {
        if (currentTabId >= 0 && currentTabId < tradeCategories.size()) {
            return tradeCategories.get(currentTabId);
        }
        return NekoTradeCategory.UNKNOWN;
    }

    @Override
    public Boolean getSyncedTradeableStatus(UUID groupId, int tradeIndex) {
        if (groupId == null) return null;
        String key = groupId.toString() + ":" + tradeIndex;
        return tradeableStatusMap.get(key);
    }

    @Override
    public List<NekoTradeItemDisplayWidget> getDisplayedWidgets(NekoDisplayType type, NekoTradeCategory category) {
        Map<NekoTradeCategory, List<NekoTradeItemDisplayWidget>> map = (type == NekoDisplayType.TILE)
            ? displayedTradesTiles
            : displayedTradesList;
        List<NekoTradeItemDisplayWidget> widgets = map.get(category);
        return widgets != null ? widgets : new ArrayList<>();
    }

    /**
     * 交易显示数据更新回调
     * <p>
     * 由 {@link NekoTradeMainPanel#updateGui()} 调用，
     * 接收格式化后的交易数据并更新预分配的 Widget。
     * <p>
     * 同时使用同步值中的 BQ/冷却/可交易状态覆盖 formatTrades() 生成的基础状态，
     * 确保多人游戏下客户端显示正确的锁定、冷却和可交易信息。
     * 可交易状态优先使用 {@link #tradeableStatusSync} 的服务端计算值；
     * 若该交易尚无同步值，则回退到本地 BQ+冷却 逻辑。
     *
     * @param trades 按分类组织的交易显示数据
     */
    @Override
    public void onTradeDisplayUpdated(Map<NekoTradeCategory, List<NekoTradeItemDisplay>> trades) {
        if (trades == null) return;

        // 使用同步值中的 BQ/冷却/可交易状态覆盖显示数据
        for (List<NekoTradeItemDisplay> displayList : trades.values()) {
            if (displayList == null) continue;
            for (NekoTradeItemDisplay display : displayList) {
                if (display == null) continue;

                // 覆盖 BQ 锁定状态（来自 bqLockStatusSync）
                boolean bqLocked = bqLockStatusMap.getOrDefault(display.getGroupId(), false);
                display.setBqLocked(bqLocked);

                // 覆盖冷却状态（来自 cooldownStatusSync）
                String cdKey = display.getGroupId()
                    .toString() + ":"
                    + display.getTradeIndex();
                long cooldown = cooldownStatusMap.getOrDefault(cdKey, 0L);
                display.setCooldownRemaining(cooldown);

                // 更新可交易状态（优先使用服务端同步值，无则回退 BQ+冷却）
                String tradeableKey = cdKey;
                Boolean syncedTradeable = tradeableStatusMap.get(tradeableKey);
                if (syncedTradeable != null) {
                    display.setTradeable(syncedTradeable);
                } else {
                    display.setTradeable(!bqLocked && cooldown <= 0);
                }
            }
        }

        // 更新预分配 Widget 的显示数据
        updatePreAllocatedWidgets(trades);

        // 更新标签高亮（搜索匹配时高亮对应标签）
        updateTabHighlighting(trades);
    }

    @Override
    public void onRestoreSettings() {
        // 恢复上次的标签页位置
        if (tabController != null) {
            int maxPage = tradeCategories.size();
            int page = Math.min(NekoPageButtonV2.lastPage, maxPage - 1);
            if (page < 0) page = 0; // 默认 FAVOURITES 分类
            tabController.setPage(page);
            currentTabId = page;
        }
        // 恢复搜索文本
        if (searchBar != null && !searchText.isEmpty()) {
            searchBar.setText(searchText);
        }
    }

    @Override
    public void onDispose() {
        // 作为 panel.onCloseAction 的兜底保险：
        // 当 ModularUI 真正关闭/释放屏幕时，确保 BGM 能正常触发淡出
        closeNekoGuiMusic();
    }

    /**
     * 关闭猫猫售货机 GUI 的 BGM
     * <p>
     * 幂等方法：仅当 GUI 仍标记为打开时才执行清理，避免 onCloseAction 与 onDispose 重复调用
     * 导致淡出被反复重置、BGM 微弱未止的问题。
     */
    private void closeNekoGuiMusic() {
        if (!isV2GuiOpen) return;
        isV2GuiOpen = false;
        // 通知 NekoMusicEventHandler GUI 已关闭，触发淡出
        NekoMusicEventHandler.onGuiClosed();
    }

    // ==================== TradeActionCallback 接口实现 ====================

    /**
     * 交易请求回调（Shift+左键点击交易时触发）
     * <p>
     * 通过 tradeRequestSync 发送 "groupId:tradeIndex" 到服务端。
     *
     * @param display 被点击的交易显示数据
     */
    @Override
    public void onTradeRequested(NekoTradeItemDisplay display) {
        if (display == null) return;
        String request = display.getGroupId()
            .toString() + ":"
            + display.getTradeIndex();
        tradeRequestSync.setValue(request);
    }

    /**
     * 收藏切换回调（Ctrl+左键点击交易时触发）
     * <p>
     * 通过 favouriteToggleSync 发送 "groupId:tradeIndex" 到服务端。
     *
     * @param display 被点击的交易显示数据
     */
    @Override
    public void onFavouriteToggled(NekoTradeItemDisplay display) {
        if (display == null) return;
        String request = display.getGroupId()
            .toString() + ":"
            + display.getTradeIndex();
        favouriteToggleSync.setValue(request);
    }

    // ==================== 预分配 Widget 初始化 ====================

    /**
     * 初始化预分配 Widget
     * <p>
     * 为每个交易分类预分配 300 个 TILE 模式和 300 个 LIST 模式的
     * {@link NekoTradeItemDisplayWidget}，并设置 TradeActionCallback。
     */
    private void initPreAllocatedWidgets() {
        for (NekoTradeCategory category : tradeCategories) {
            List<NekoTradeItemDisplayWidget> tiles = new ArrayList<>(MAX_TRADES);
            List<NekoTradeItemDisplayWidget> lists = new ArrayList<>(MAX_TRADES);
            for (int i = 0; i < MAX_TRADES; i++) {
                NekoTradeItemDisplayWidget tile = new NekoTradeItemDisplayWidget(null, NekoDisplayType.TILE);
                tile.setCallback(this);
                tiles.add(tile);

                NekoTradeItemDisplayWidget list = new NekoTradeItemDisplayWidget(null, NekoDisplayType.LIST);
                list.setCallback(this);
                lists.add(list);
            }
            displayedTradesTiles.put(category, tiles);
            displayedTradesList.put(category, lists);
        }
    }

    /**
     * 更新预分配 Widget 的显示数据
     * <p>
     * 对每个分类，将格式化后的交易数据按顺序设置到预分配的 Widget 上。
     * 超出数据范围的 Widget 设置为 null（禁用显示）。
     *
     * @param trades 按分类组织的交易显示数据
     */
    private void updatePreAllocatedWidgets(Map<NekoTradeCategory, List<NekoTradeItemDisplay>> trades) {
        // 更新 TILE 模式 Widget
        updateWidgetList(trades, displayedTradesTiles);
        // 更新 LIST 模式 Widget
        updateWidgetList(trades, displayedTradesList);
    }

    /**
     * 更新单个显示模式的 Widget 列表
     *
     * @param trades 交易显示数据
     * @param map    Widget 映射（分类 → Widget 列表）
     */
    private void updateWidgetList(Map<NekoTradeCategory, List<NekoTradeItemDisplay>> trades,
        Map<NekoTradeCategory, List<NekoTradeItemDisplayWidget>> map) {
        for (Map.Entry<NekoTradeCategory, List<NekoTradeItemDisplayWidget>> entry : map.entrySet()) {
            NekoTradeCategory category = entry.getKey();
            List<NekoTradeItemDisplayWidget> widgets = entry.getValue();
            List<NekoTradeItemDisplay> displayList = trades.get(category);
            int displaySize = displayList != null ? displayList.size() : 0;

            for (int i = 0; i < widgets.size(); i++) {
                if (i < displaySize) {
                    widgets.get(i)
                        .setDisplay(displayList.get(i));
                } else {
                    widgets.get(i)
                        .setDisplay(null);
                }
            }
        }
    }

    /**
     * 更新标签高亮
     * <p>
     * 当搜索文本非空时，高亮所有包含匹配交易的分类标签。
     *
     * @param trades 按分类组织的交易显示数据
     */
    private void updateTabHighlighting(Map<NekoTradeCategory, List<NekoTradeItemDisplay>> trades) {
        highlightedTabs.clear();
        if (searchText == null || searchText.isEmpty()) {
            return;
        }
        for (Map.Entry<NekoTradeCategory, List<NekoTradeItemDisplay>> entry : trades.entrySet()) {
            if (entry.getValue() != null && !entry.getValue()
                .isEmpty()) {
                highlightedTabs.add(entry.getKey());
            }
        }
    }

    // ==================== UI 组件创建 ====================

    /**
     * 创建左侧标签列
     * <p>
     * 为每个交易分类创建一个 {@link NekoPageButtonV2} 按钮，
     * 使用物品图标作为标签页标识。
     *
     * @return 标签列 Widget
     */
    private IWidget createTabColumn() {
        Flow tabColumn = Flow.column()
            .coverChildren()
            .left(-29)
            .top(40)
            .childPadding(2);

        for (int i = 0; i < tradeCategories.size(); i++) {
            final int index = i;
            NekoTradeCategory category = tradeCategories.get(i);
            ItemStack icon = getCategoryIcon(category);
            String name = getCategoryName(category);

            NekoPageButtonV2 tabButton = new NekoPageButtonV2(index, tabController, category, highlightedTabs, icon);
            tabButton.tab(NekoGuiTextures.TAB_LEFT, -1);
            tabButton.tooltipBuilder(t -> { t.addLine(IKey.str(name)); });

            tabColumn.child(tabButton);
        }

        // 在 NEI/HEI 中排除标签列区域，避免配方查看器遮挡标签页
        return tabColumn.excludeAreaInRecipeViewer();
    }

    /**
     * 创建 QoL 按钮列（BGM/音量、显示模式、货币显示开关、排序模式）
     * <p>
     * 位于标签列左侧，使用 2x2 Grid 布局提供快速操作按钮。
     * <p>
     * 按钮顺序完全模仿 V1（VM 的 MTEVendingMachineGui.createQolButtonColumn）：
     * <ul>
     * <li>左上：音量/BGM 按钮。单击切换 BGM 曲目播放/停止；Shift+点击打开音量控制面板。</li>
     * <li>右上：显示模式切换按钮（TILE ↔ LIST）。</li>
     * <li>左下：货币余额行显示/隐藏开关。</li>
     * <li>右下：排序模式切换按钮（SMART ↔ ALPHABET）。</li>
     * </ul>
     * 同时在此方法内创建 {@link #volumePanel}，将本按钮作为 parent 传给
     * {@link NekoVolumeControlGui#createPanel}，修复旧版传 null 导致 NPE 的问题。
     *
     * @return QoL 按钮列 Widget
     */
    private IWidget createQolButtonColumn() {
        // --- 音量/BGM 按钮（左上）---
        // 单击切换 BGM 播放/停止；Shift+点击打开音量面板
        final ButtonWidget<?> volumeButton = new ButtonWidget<>().size(14, 14)
            .overlay(new DynamicDrawable(() -> {
                NekoMusicEventHandler handler = NekoMusicEventHandler.getInstance();
                boolean isPlaying = handler != null && handler.isPlaying();
                return (isPlaying ? NekoMusicTrack.LUNCH_BREAK.getTexture() : NekoMusicTrack.NONE.getTexture())
                    .size(14);
            }))
            .onMousePressed(btn -> {
                if (Interactable.hasShiftDown()) {
                    // Shift+点击：打开/关闭音量控制面板
                    if (volumePanel != null) {
                        volumePanel.togglePanel();
                    }
                } else {
                    // 单击：切换 BGM 播放
                    NekoMusicEventHandler handler = NekoMusicEventHandler.getInstance();
                    if (handler != null) {
                        if (handler.isPlaying()) {
                            handler.forceStopBGM();
                        } else {
                            handler.forceStartBGM();
                        }
                    }
                }
                return true;
            })
            .tooltipBuilder(t -> {
                NekoMusicEventHandler handler = NekoMusicEventHandler.getInstance();
                boolean isPlaying = handler != null && handler.isPlaying();
                t.addLine(IKey.str(isPlaying ? "BGM: 开启" : "BGM: 关闭"));
                t.addLine(IKey.str("音量: " + NekoVolumeControlGui.getVolumeAsString() + "%"));
                t.addLine(IKey.str("Shift+点击 打开音量控制"));
            });
        volumeButton.tooltipAutoUpdate(true);

        // --- 显示模式切换按钮（右上）---
        ButtonWidget<?> displayModeButton = new ButtonWidget<>().size(14, 14)
            .overlay(
                new DynamicDrawable(
                    () -> getDisplayType().getTexture()
                        .size(14)))
            .onMousePressed(btn -> {
                // 切换显示模式：TILE ↔ LIST
                displayTypeSync.setValue(displayType == 0 ? 1 : 0);
                return true;
            })
            .tooltipBuilder(t -> {
                t.addLine(IKey.str("当前: " + getDisplayType().getLocalizedName()));
                t.addLine(IKey.str("点击切换显示模式"));
            });
        displayModeButton.tooltipAutoUpdate(true);

        // --- 货币显示开关按钮（左下）---
        ButtonWidget<?> showCoinsButton = new ButtonWidget<>().size(14, 14)
            .overlay(
                new DynamicDrawable(
                    () -> (showCoins ? NekoGuiTextures.SHOW_COINS : NekoGuiTextures.HIDE_COINS).asIcon()
                        .size(14)))
            .onMousePressed(btn -> {
                // 切换货币余额行的显示/隐藏
                showCoinsSync.setValue(!showCoins);
                return true;
            })
            .tooltipBuilder(t -> { t.addLine(IKey.str(showCoins ? "隐藏猫猫币" : "显示猫猫币")); });
        showCoinsButton.tooltipAutoUpdate(true);

        // --- 排序模式切换按钮（右下）---
        ButtonWidget<?> sortModeButton = new ButtonWidget<>().size(14, 14)
            .overlay(
                new DynamicDrawable(
                    () -> getSortMode().getTexture()
                        .size(14)))
            .onMousePressed(btn -> {
                // 切换排序模式：SMART ↔ ALPHABET
                sortModeSync.setValue(sortMode == 0 ? 1 : 0);
                return true;
            })
            .tooltipBuilder(t -> {
                t.addLine(IKey.str("当前: " + getSortMode().getLocalizedName()));
                t.addLine(IKey.str("点击切换排序模式"));
            });
        sortModeButton.tooltipAutoUpdate(true);

        // 客户端：创建音量控制面板（以 volumeButton 为 parent，修复旧版 parent=null 的 NPE）
        if (syncManagerRef != null && syncManagerRef.isClient()) {
            volumePanel = syncManagerRef.syncedPanel(
                "nekoV2Volume",
                true,
                (sm, sh) -> new NekoVolumeControlGui().createPanel(sm, volumeButton));
        }

        // 2x2 网格：左上音量、右上显示模式、左下显示硬币、右下排序
        // 在 NEI/HEI 中排除 QoL 按钮列区域，避免配方查看器遮挡快捷按钮
        return new Grid().left(-33)
            .top(1)
            .minElementMargin(1, 1)
            .coverChildren()
            .grid(
                Arrays.asList(
                    Arrays.asList(volumeButton, displayModeButton),
                    Arrays.asList(showCoinsButton, sortModeButton)))
            .excludeAreaInRecipeViewer();
    }

    /**
     * 创建主内容列
     * <p>
     * 布局从上到下：标题、搜索栏、交易列表（PagedWidget）、猫猫币显示、交易结果、玩家背包。
     * <p>
     * 输入槽和输出槽已迁移到 {@link #createIOColumn()}（与 V1 的 IO 列布局一致），
     * 主列不再承载 IO 元素，以保持 UI 与 V1 一致。
     *
     * @param syncManager 面板同步管理器
     * @return 主列 Widget
     */
    private IWidget createMainColumn(PanelSyncManager syncManager) {
        Flow mainColumn = Flow.column()
            .width(PANEL_WIDTH - 8);

        // --- 标题 ---
        mainColumn.child(
            IKey.str(EnumChatFormatting.DARK_GRAY + "猫猫售货机")
                .asWidget()
                .height(12)
                .fullWidth()
                .marginBottom(2));

        if (syncManager.isClient()) {
            // --- 搜索栏 ---
            searchBar = new NekoSearchBar("搜索...");
            searchBar.size(PANEL_WIDTH - 12, 14)
                .fullWidth()
                .marginBottom(2);
            searchBar.setSearchListener(newText -> { searchTextSync.setValue(newText); });
            mainColumn.child(searchBar);

            // --- 交易列表（PagedWidget + 预分配 Widget）---
            mainColumn.child(createTradePagedWidget());

            // 注：volumePanel 已在 createQolButtonColumn 内创建（以 volumeButton 为 parent）
        }

        // --- 猫猫币余额显示行 ---
        mainColumn.child(createCoinDisplayRow(syncManager));

        // --- 交易结果消息 ---
        mainColumn.child(
            IKey.dynamic(() -> tradeResultMessage.isEmpty() ? "" : EnumChatFormatting.YELLOW + tradeResultMessage)
                .asWidget()
                .height(12)
                .fullWidth()
                .marginBottom(2));

        // --- 玩家背包栏 ---
        mainColumn.child(
            SlotGroupWidget.playerInventory(false)
                .marginLeft(4));

        return mainColumn;
    }

    /**
     * 创建交易列表分页 Widget
     * <p>
     * 使用 {@link PagedWidget} 为每个交易分类创建一个页面，
     * 每页包含一个 {@link ListWidget}，内含预分配的 TILE 和 LIST 模式 Widget。
     * <p>
     * 通过 {@link PagedWidget.Controller} 控制页面切换，
     * 与 {@link NekoPageButtonV2} 标签按钮联动。
     *
     * @return 交易列表 Widget
     */
    private IWidget createTradePagedWidget() {
        PagedWidget<?> paged = new PagedWidget<>().name("nekoV2Paged")
            .width(PANEL_WIDTH - 12)
            .controller(tabController)
            .background(NekoGuiTextures.TRADE_BACKGROUND);
        // 交易列表高度与 V1 保持一致
        paged.height(146);

        // 为每个分类创建一个页面
        for (NekoTradeCategory category : tradeCategories) {
            paged.addPage(createTradeListPage(category));
        }

        // 如果没有页面，添加占位页面防止崩溃
        if (tradeCategories.isEmpty()) {
            paged.addPage(
                IKey.str("无可用交易")
                    .asWidget());
        }

        return paged;
    }

    /**
     * 创建单个分类的交易列表页面
     * <p>
     * 每页包含一个 {@link ListWidget}，内含：
     * <ol>
     * <li>顶部间距</li>
     * <li>结构不完整提示行（机器未激活时显示）</li>
     * <li>TILE 模式行（3个 Widget 一行，共 100 行 = 300 个）</li>
     * <li>LIST 模式行（1个 Widget 一行，共 300 行）</li>
     * <li>底部间距</li>
     * </ol>
     * <p>
     * TILE 和 LIST 模式的 Widget 同时存在于列表中，
     * 通过 setEnabledIf 根据当前显示模式控制可见性。
     *
     * @param category 交易分类
     * @return 列表页面 Widget
     */
    private IWidget createTradeListPage(NekoTradeCategory category) {
        ListWidget<IWidget, ?> tradeList = new ListWidget<>().name("items_" + category.getKey())
            .width(PANEL_WIDTH - 14)
            .top(1)
            .collapseDisabledChild(true);
        // 交易列表页面高度与外层 PagedWidget 保持一致
        tradeList.height(146);

        // 顶部间距
        tradeList.child(
            Flow.row()
                .height(2));

        // 结构不完整提示行
        Flow statusRow = Flow.row()
            .height(10)
            .width(TRADE_ROW_WIDTH)
            .marginLeft(2)
            .child(
                IKey.str(EnumChatFormatting.RED + "结构不完整")
                    .asWidget());
        statusRow.setEnabledIf(w -> !baseMetaTileEntity.isActive());
        tradeList.child(statusRow);

        // --- TILE 模式行（3个 Widget 一行）---
        List<NekoTradeItemDisplayWidget> tileWidgets = displayedTradesTiles.get(category);
        if (tileWidgets != null) {
            NekoTradeRow row = new NekoTradeRow();
            row.height(TILE_ITEM_HEIGHT + 4);
            row.width(TRADE_ROW_WIDTH);
            row.marginLeft(2);

            for (int i = 0; i < MAX_TRADES; i++) {
                final int index = i;
                NekoTradeItemDisplayWidget widget = tileWidgets.get(i);
                widget.margin(2);
                widget.setEnabledIf(w -> {
                    if (!baseMetaTileEntity.isActive()) return false;
                    return getDisplayType() == NekoDisplayType.TILE && widget.getDisplay() != null;
                });
                row.child(widget);

                // 每 TILE_ITEMS_PER_ROW 个换行
                if (i % TILE_ITEMS_PER_ROW == TILE_ITEMS_PER_ROW - 1) {
                    tradeList.child(row);
                    row = new NekoTradeRow();
                    row.height(TILE_ITEM_HEIGHT + 4);
                    row.width(TRADE_ROW_WIDTH);
                    row.marginLeft(2);
                }
            }
            // 添加最后一行（如果有剩余）
            if (row.getChildren() != null && !row.getChildren()
                .isEmpty()) {
                tradeList.child(row);
            }
        }

        // --- LIST 模式行（1个 Widget 一行）---
        List<NekoTradeItemDisplayWidget> listWidgets = displayedTradesList.get(category);
        if (listWidgets != null) {
            for (int i = 0; i < MAX_TRADES; i++) {
                NekoTradeItemDisplayWidget widget = listWidgets.get(i);
                widget.setEnabledIf(w -> {
                    if (!baseMetaTileEntity.isActive()) return false;
                    return getDisplayType() == NekoDisplayType.LIST && widget.getDisplay() != null;
                });

                NekoTradeRow row = new NekoTradeRow();
                row.height(LIST_ITEM_HEIGHT);
                row.width(TRADE_ROW_WIDTH);
                row.marginLeft(2);
                row.child(widget);
                tradeList.child(row);
            }
        }

        // 底部间距
        tradeList.child(
            Flow.row()
                .height(2));

        return tradeList;
    }

    /**
     * 创建猫猫币余额显示行
     * <p>
     * 为每种猫猫币创建一个 {@link NekoCoinDisplayV2} 组件，
     * 含 serp 缓动动画和弹出按钮。
     *
     * @param syncManager 面板同步管理器
     * @return 猫猫币显示行 Widget
     */
    private IWidget createCoinDisplayRow(PanelSyncManager syncManager) {
        Flow row = Flow.row()
            .height(22)
            .fullWidth()
            .marginBottom(2);

        int offset = 0;
        for (String currencyId : NekoCurrencyRegistrar.getNekoCurrencyIds()) {
            String displayName = NekoCurrencyRegistrar.getDisplayName(currencyId);
            NekoCoinDisplayV2 coinDisplay = new NekoCoinDisplayV2(syncManager, currencyId, displayName);
            coinDisplay.left(offset);
            row.child(coinDisplay);
            offset += 65;
        }

        // 根据货币显示开关控制余额行的显示/隐藏
        // 使用 collapseDisabledChild(true) 在 showCoins=false 时整行折叠，而非仅变灰
        row.setEnabledIf(w -> showCoins)
            .collapseDisabledChild(true);
        return row;
    }

    /**
     * 创建右侧 IO 列
     * <p>
     * 完全模仿 V1（VM 的 {@code MTEVendingMachineGui.createIOColumn}）的布局：
     * <ul>
     * <li>顶部：INPUT_SPRITE 图标 + "IN" 文字</li>
     * <li>2x4 输入槽（带自动导入猫猫币 changeListener）</li>
     * <li>物品弹射按钮（EJECT_SLOTS）+ 货币弹射按钮（EJECT_COINS）</li>
     * <li>底部：出货槽（带 DISPENSER_BACKGROUND/OVERHANG + 100 个掉落动画槽）</li>
     * </ul>
     * 不再放置电源开关、结构更新、音量按钮，这些功能分别由 GT5U 标准交互
     * （扳手右键等）与 QoL 按钮列承担，与 V1 行为一致。
     *
     * @return IO 列 Widget
     */
    private IWidget createIOColumn() {
        ParentWidget<?> ioColumn = new ParentWidget<>().size(50, 214)
            .right(-48)
            .top(40)
            .background(NekoGuiTextures.SIDE_PANEL_BACKGROUND);

        // --- 顶部：INPUT_SPRITE 图标 + "IN" 文字 ---
        ioColumn.child(
            NekoGuiTextures.INPUT_SPRITE.asWidget()
                .leftRel(0.5f)
                .top(8)
                .width(30)
                .height(20));
        ioColumn.child(
            (IWidget) new TextWidget(IKey.str("IN")).textAlign(Alignment.CENTER)
                .top(8)
                .widthRel(1.0f));

        // --- 输入槽（2x4，带自动导入猫猫币 changeListener）---
        SlotGroupWidget inputSlots = SlotGroupWidget.builder()
            .matrix("II", "II", "II", "II")
            .key('I', index -> {
                ModularSlot slot = new ModularSlot(multiblock.inputItems, index).slotGroup("inputSlotGroup");
                // 持有 ItemSlot 引用，便于服务端在入账后强制同步槽位状态到客户端
                final ItemSlot itemSlot = new ItemSlot().slot(slot);
                // 自动导入猫猫币：识别到猫猫币后放入玩家钱包并立即同步客户端
                slot.changeListener((newItem, onlyAmountChanged, client, init) -> {
                    if (init || newItem == null) return;
                    String currencyId = NekoCurrencyRegistrar.getNekoCurrencyId(newItem);
                    if (currencyId == null) return;
                    // 客户端：立即视觉清槽，真实数据以服务端同步为准
                    if (client) {
                        slot.putStack(null);
                        return;
                    }
                    UUID playerId = getPlayerId();
                    if (playerId == null) return;
                    NekoWallet wallet = NekoWalletManager.INSTANCE.getWallet(playerId);
                    if (wallet == null) return;
                    // 先入账并持久化，再清槽，避免异常导致丢币
                    wallet.addCount(currencyId, newItem.stackSize);
                    NekoWalletManager.INSTANCE.saveWallet(playerId);
                    slot.putStack(null);
                    // 强制同步槽位到客户端，使玩家立即看到槽位清空
                    itemSlot.getSyncHandler()
                        .forceSyncItem();
                    // 强制刷新对应货币余额同步值，使余额显示立即更新
                    IntSyncValue coinSync = coinAmountSyncs.get(currencyId);
                    if (coinSync != null) {
                        coinSync.setValue(wallet.getCount(currencyId));
                    }
                    // 服务端播放投币音效，会自动广播给附近客户端
                    if (baseMetaTileEntity != null) {
                        World world = baseMetaTileEntity.getWorld();
                        if (world != null && !world.isRemote) {
                            world.playSoundEffect(
                                baseMetaTileEntity.getXCoord() + 0.5,
                                baseMetaTileEntity.getYCoord() + 0.5,
                                baseMetaTileEntity.getZCoord() + 0.5,
                                "vendingmachine:coin_insert",
                                1.0f,
                                1.0f);
                        }
                    }
                });
                // 收集输入槽引用，供服务端在弹出物品等操作后强制同步
                inputSlotRefs.add(itemSlot);
                return itemSlot;
            })
            .build();
        ioColumn.child(
            Flow.row()
                .child(inputSlots.center())
                .top(20)
                .height(18 * 4));

        // --- 弹射按钮行：物品弹射 + 货币弹射 ---
        ButtonWidget<?> ejectItemsButton = new ButtonWidget<>().size(16, 16)
            .overlay(
                NekoGuiTextures.EJECT_SLOTS.asIcon()
                    .size(16))
            .onMousePressed(btn -> {
                ejectItemsSync.setValue(true);
                return true;
            })
            .tooltipBuilder(t -> t.addLine(IKey.str("弹出物品")));
        ButtonWidget<?> ejectCoinsButton = new ButtonWidget<>().size(16, 16)
            .overlay(
                NekoGuiTextures.EJECT_COINS.asIcon()
                    .size(16))
            .onMousePressed(btn -> {
                ejectAllCoinsSync.setValue(true);
                return true;
            })
            .tooltipBuilder(t -> t.addLine(IKey.str("弹出所有猫猫币")));
        ioColumn.child(
            Flow.row()
                .child(ejectItemsButton.right(6))
                .child(ejectCoinsButton.left(6))
                .top(98)
                .height(18));

        // --- 底部：出货槽（带 dispenser 背景与悬垂 + 100 个掉落动画槽）---
        ParentWidget<?> dispenserChute = new ParentWidget<>().fullHeight()
            .fullWidth()
            .marginLeft(5)
            .marginRight(4)
            .background(NekoGuiTextures.DISPENSER_BACKGROUND)
            .child(getFillPlayerInventoryButton());
        // 100 个掉落动画槽（fallDistance=72 与 V1 一致，4 行高度）
        NekoFallingItemSlotFactory fallingFactory = new NekoFallingItemSlotFactory(
            multiblock.outputItems,
            18 * 4,
            MTENekoVendingMachineV2.OUTPUT_SLOTS);
        for (int i = 0; i < MTENekoVendingMachineV2.OUTPUT_SLOTS; i++) {
            dispenserChute.child(fallingFactory.getFallingItemSlot(i));
        }
        // 顶部悬垂装饰：必须最后添加，覆盖掉落槽起始位置（顶部），形成"物品从悬垂后面掉落"的图层效果
        // 与 VM 原版 MteVendingMachineGui.createDispenserChute 顺序一致（掉落槽 → OVERHANG 最后）
        dispenserChute.child(
            NekoGuiTextures.DISPENSER_OVERHANG.asWidget()
                .top(0)
                .fullWidth());
        ioColumn.child(
            Flow.row()
                .child(dispenserChute)
                .bottom(6)
                .height(18 * 5));

        // 在 NEI/HEI 中排除右侧 IO 列区域，避免配方查看器遮挡输入/输出槽
        return ioColumn.excludeAreaInRecipeViewer();
    }

    /**
     * 获取"填充玩家背包"按钮
     * <p>
     * 复刻 V1 的 getNekoFillPlayerInventoryButton：
     * 使用一个铺满出货槽区域的不可见 ButtonWidget，
     * Shift+左键点击时通过 fillPlayerInventorySync 触发服务端方法，
     * 将出货槽的物品快速移到玩家背包。
     *
     * @return 不可见的满覆盖按钮 Widget
     */
    private IWidget getFillPlayerInventoryButton() {
        return new ButtonWidget<>().fullHeight()
            .fullWidth()
            .invisible()
            .playClickSound(false)
            .onMousePressed(btn -> {
                // 复刻 V1：仅 Shift+左键触发
                if (Interactable.hasShiftDown()) {
                    fillPlayerInventorySync.setValue(true);
                }
                return true;
            });
    }

    // ==================== 猫猫币操作（保留原有逻辑） ====================

    /**
     * 弹出单种猫猫币
     * <p>
     * 从钱包中取出指定货币的全部余额，在机器旁弹出。
     * 前提：机器必须已成型且处于 active 状态；否则直接重置标志并返回。
     *
     * @param currencyId 货币 ID
     * @param playerId   玩家 UUID
     */
    private void doNekoEjectCoin(String currencyId, UUID playerId) {
        // 客户端不执行服务端逻辑（匹配 V1 的 isClient() 守卫）
        if (isClient() || baseMetaTileEntity == null || !baseMetaTileEntity.isActive()) {
            nekoEjectSingleCoin.put(currencyId, false);
            return;
        }
        try {
            if (playerId == null) {
                nekoEjectSingleCoin.put(currencyId, false);
                return;
            }
            NekoWallet wallet = NekoWalletManager.INSTANCE.getWallet(playerId);
            if (wallet == null || wallet.getCount(currencyId) <= 0) {
                nekoEjectSingleCoin.put(currencyId, false);
                return;
            }

            int count = wallet.getCount(currencyId);
            java.util.List<ItemStack> toDispense = new java.util.ArrayList<>();
            while (count > 0) {
                int stackSize = Math.min(count, 64);
                ItemStack stack = NekoCurrencyRegistrar.getItemStack(currencyId, stackSize);
                if (stack != null) {
                    toDispense.add(stack);
                }
                count -= stackSize;
            }

            if (!toDispense.isEmpty()) {
                // 投放前检查：输出槽是否还有空位（考虑 outputBuffer 已堆积的情况）
                // 可用空位数 = 当前空槽数 - 队列已占用的虚拟槽位数
                int emptySlots = multiblock.getOutputEmptySlotCount();
                int queuedItems = multiblock.getOutputBufferSize();
                if (emptySlots - queuedItems <= 0) {
                    // 输出槽已满（含队列堆积），不扣钱，提示玩家
                    GTInterestingThing.LOG.warn("[NekoVMV2] doNekoEjectCoin 输出槽已满，取消弹出货币 {}", currencyId);
                    return;
                }
                multiblock.dispenseItemStacks(toDispense);
                wallet.resetCount(currencyId);
                NekoWalletManager.INSTANCE.saveWallet(playerId);
                playCoinDropSound();
            }
        } catch (Throwable t) {
            GTInterestingThing.LOG.error("[NekoVMV2] doNekoEjectCoin 异常!", t);
        } finally {
            nekoEjectSingleCoin.put(currencyId, false);
        }
    }

    /**
     * 弹出所有猫猫币
     * <p>
     * 前提：机器必须已成型且处于 active 状态；否则直接重置标志并返回。
     *
     * @param playerId 玩家 UUID
     */
    private void doNekoEjectAllCoins(UUID playerId) {
        // 客户端不执行服务端逻辑（匹配 V1 的 isClient() 守卫）
        // 重置标志位避免客户端 UI 卡在 true 状态
        if (isClient() || baseMetaTileEntity == null || !baseMetaTileEntity.isActive()) {
            nekoEjectAllCoins = false;
            return;
        }
        try {
            if (playerId == null) {
                nekoEjectAllCoins = false;
                return;
            }
            NekoWallet wallet = NekoWalletManager.INSTANCE.getWallet(playerId);
            if (wallet == null) {
                nekoEjectAllCoins = false;
                return;
            }

            java.util.List<ItemStack> toDispense = new java.util.ArrayList<>();
            for (String currencyId : NekoCurrencyRegistrar.getNekoCurrencyIds()) {
                int count = wallet.getCount(currencyId);
                while (count > 0) {
                    int stackSize = Math.min(count, 64);
                    ItemStack stack = NekoCurrencyRegistrar.getItemStack(currencyId, stackSize);
                    if (stack != null) {
                        toDispense.add(stack);
                    }
                    count -= stackSize;
                }
                wallet.resetCount(currencyId);
            }

            if (!toDispense.isEmpty()) {
                multiblock.dispenseItemStacks(toDispense);
                NekoWalletManager.INSTANCE.saveWallet(playerId);
                playCoinDropSound();
            }
        } catch (Throwable t) {
            GTInterestingThing.LOG.error("[NekoVMV2] doNekoEjectAllCoins 异常!", t);
        } finally {
            nekoEjectAllCoins = false;
        }
    }

    /**
     * 弹出物品
     * <p>
     * 扫描内置输入槽（{@code inputItems}），将所有非空物品复制后优先写入出货槽
     * （{@code outputItems}）以触发 {@link NekoFallingItemSlotFactory} 掉落动画；
     * 出货槽空间不足时，剩余物品掉落到机器旁。最后清空输入槽并强制同步到客户端。
     * <p>
     * 与 V1 的 {@code ejectItems} 行为一致：弹出的是输入槽中的物品（猫猫币通常
     * 已被 changeListener 自动导入钱包，不会滞留在此处）。
     */
    private void doNekoEjectItems() {
        // 客户端不执行服务端逻辑（匹配 V1 的 isClient() 守卫）
        if (isClient() || baseMetaTileEntity == null || !baseMetaTileEntity.isActive()) {
            nekoEjectItems = false;
            return;
        }
        try {
            java.util.List<ItemStack> toDispense = new java.util.ArrayList<>();
            for (int i = 0; i < MTENekoVendingMachineV2.INPUT_SLOTS; i++) {
                ItemStack stack = multiblock.inputItems.getStackInSlot(i);
                if (stack == null || stack.stackSize <= 0) continue;
                toDispense.add(stack.copy());
                multiblock.inputItems.setStackInSlot(i, null);
            }

            if (!toDispense.isEmpty()) {
                multiblock.dispenseItemStacks(toDispense);
                forceSyncInputSlots();
                playItemDropSound();
            }
        } catch (Throwable t) {
            GTInterestingThing.LOG.error("[NekoVMV2] doNekoEjectItems 异常!", t);
        } finally {
            nekoEjectItems = false;
        }
    }

    /**
     * 填充玩家背包
     * <p>
     * 复刻 V1/VM 父类的 fillPlayerInventoryWithDispensedItems：
     * 遍历出货槽，将物品移到玩家背包；无法放入的物品保留在槽中。
     * 前提：机器必须已成型且处于 active 状态。
     *
     * @param playerId 玩家 UUID
     */
    private void doNekoFillPlayerInventory(UUID playerId) {
        // 客户端不执行服务端逻辑（匹配 V1 的 isClient() 守卫）
        if (isClient() || baseMetaTileEntity == null || !baseMetaTileEntity.isActive()) {
            nekoFillPlayerInventory = false;
            return;
        }
        try {
            if (playerId == null) {
                nekoFillPlayerInventory = false;
                return;
            }
            EntityPlayer player = guiData.getPlayer();
            if (player == null) {
                nekoFillPlayerInventory = false;
                return;
            }
            multiblock.fillPlayerInventoryWithDispensedItems(player);
            forceSyncInputSlots();
        } catch (Throwable t) {
            GTInterestingThing.LOG.error("[NekoVMV2] doNekoFillPlayerInventory 异常!", t);
        } finally {
            nekoFillPlayerInventory = false;
        }
    }

    /**
     * 导入猫猫币
     * <p>
     * 扫描内置输入槽中的物品，将猫猫币导入到玩家钱包。
     *
     * @param playerId 玩家 UUID
     */
    private void doNekoImportCoins(UUID playerId) {
        // 客户端不执行服务端逻辑（匹配 V1 的 isClient() 守卫）
        if (isClient()) {
            nekoImportCoins = false;
            return;
        }
        try {
            if (playerId == null) {
                nekoImportCoins = false;
                return;
            }
            NekoWallet wallet = NekoWalletManager.INSTANCE.getWallet(playerId);
            if (wallet == null) {
                nekoImportCoins = false;
                return;
            }

            int totalImported = 0;
            for (int i = 0; i < MTENekoVendingMachineV2.INPUT_SLOTS; i++) {
                ItemStack stack = multiblock.inputItems.getStackInSlot(i);
                if (stack == null) continue;
                String currencyId = NekoCurrencyRegistrar.getNekoCurrencyId(stack);
                if (currencyId != null) {
                    wallet.addCount(currencyId, stack.stackSize);
                    totalImported += stack.stackSize;
                    multiblock.inputItems.setStackInSlot(i, null);
                }
            }

            if (totalImported > 0) {
                NekoWalletManager.INSTANCE.saveWallet(playerId);
                tradeResultMessage = "成功导入 " + totalImported + " 个猫猫币";
            } else {
                tradeResultMessage = "输入槽中未找到猫猫币";
            }
        } catch (Throwable t) {
            GTInterestingThing.LOG.error("[NekoVMV2] doNekoImportCoins 异常!", t);
            tradeResultMessage = "导入猫猫币失败";
        } finally {
            nekoImportCoins = false;
        }
    }

    /**
     * 强制同步输入槽到客户端
     * <p>
     * 在输入槽内容发生变化后（如弹出物品、自动导入猫猫币）调用，
     * 确保客户端立即看到最新槽位状态。
     */
    private void forceSyncInputSlots() {
        for (ItemSlot slot : inputSlotRefs) {
            if (slot != null && slot.getSyncHandler() != null) {
                slot.getSyncHandler()
                    .forceSyncItem();
            }
        }
    }

    /**
     * 在机器旁弹出物品
     *
     * @param stack 要弹出的物品栈
     */
    private void dropItemsNearMachine(ItemStack stack) {
        if (stack == null || baseMetaTileEntity == null) return;
        World world = baseMetaTileEntity.getWorld();
        if (world == null || world.isRemote) return;

        double x = baseMetaTileEntity.getXCoord() + 0.5;
        double y = baseMetaTileEntity.getYCoord() + 0.5;
        double z = baseMetaTileEntity.getZCoord() + 0.5;

        EntityItem entity = new EntityItem(world, x, y, z, stack);
        entity.motionX = world.rand.nextDouble() * 0.2 - 0.1;
        entity.motionY = 0.2;
        entity.motionZ = world.rand.nextDouble() * 0.2 - 0.1;
        world.spawnEntityInWorld(entity);
    }

    /**
     * 播放 coin_drop 音效
     * <p>
     * 在服务端机器位置播放 VM mod 的 {@code vendingmachine:coin_drop} 音效，
     * 会自动广播给附近所有玩家。
     */
    private void playCoinDropSound() {
        if (baseMetaTileEntity == null) return;
        World world = baseMetaTileEntity.getWorld();
        if (world == null || world.isRemote) return;

        world.playSoundEffect(
            baseMetaTileEntity.getXCoord() + 0.5,
            baseMetaTileEntity.getYCoord() + 0.5,
            baseMetaTileEntity.getZCoord() + 0.5,
            "vendingmachine:coin_drop",
            1.0f,
            1.0f);
    }

    /**
     * 播放 item_drop 音效
     * <p>
     * 在服务端机器位置播放 VM mod 的 {@code vendingmachine:item_drop} 音效，
     * 会自动广播给附近所有玩家。
     */
    private void playItemDropSound() {
        if (baseMetaTileEntity == null) return;
        World world = baseMetaTileEntity.getWorld();
        if (world == null || world.isRemote) return;

        world.playSoundEffect(
            baseMetaTileEntity.getXCoord() + 0.5,
            baseMetaTileEntity.getYCoord() + 0.5,
            baseMetaTileEntity.getZCoord() + 0.5,
            "vendingmachine:item_drop",
            1.0f,
            1.0f);
    }

    /**
     * 播放交易成功音效
     * <p>
     * 在服务端机器位置播放本项目内置的 {@code gtit:trade_success} 音效（3 变体随机），
     * 会自动广播给附近所有玩家。音效资源复制自 VM mod 的 coin_insert，内置到 gtit 命名空间以减小外部依赖。
     */
    private void playTradeSuccessSound() {
        if (baseMetaTileEntity == null) return;
        World world = baseMetaTileEntity.getWorld();
        if (world == null || world.isRemote) return;

        world.playSoundEffect(
            baseMetaTileEntity.getXCoord() + 0.5,
            baseMetaTileEntity.getYCoord() + 0.5,
            baseMetaTileEntity.getZCoord() + 0.5,
            "gtit:trade_success",
            1.0f,
            1.0f);
    }

    // ==================== 交易请求处理（保留原有逻辑） ====================

    /**
     * 处理交易请求
     * <p>
     * 解析 "groupId:tradeIndex" 格式的请求字符串，
     * 调用机器的 processTrade 方法执行交易。
     *
     * @param request  请求字符串 "groupId:tradeIndex"
     * @param playerId 玩家 UUID
     */
    private void processTradeRequest(String request, UUID playerId) {
        // C2S change listener 在客户端也会触发，但交易逻辑只在服务端执行。
        // 客户端调用 S2C 同步值的 notifyUpdate() 会抛 SecurityException，
        // 且 multiblock.processTrade() 是服务端专属操作。
        if (isClient()) return;
        try {
            if (playerId == null) {
                tradeResultMessage = "无法确定玩家身份";
                return;
            }

            String[] parts = request.split(":");
            if (parts.length != 2) {
                tradeResultMessage = "交易请求格式错误";
                return;
            }

            UUID groupId;
            int tradeIndex;
            try {
                groupId = UUID.fromString(parts[0]);
                tradeIndex = Integer.parseInt(parts[1]);
            } catch (Exception e) {
                tradeResultMessage = "交易请求参数解析失败";
                return;
            }

            NekoTradeResult result = multiblock.processTrade(playerId, groupId, tradeIndex);

            if (result.isSuccess()) {
                tradeResultMessage = EnumChatFormatting.GREEN + "交易成功!";
                playTradeSuccessSound();
                // 复刻 VM 父类 sendTradeUpdate：交易成功后显式触发同步，
                // 让客户端立即收到最新的可交易状态、冷却状态和交易结果。
                tradeableStatusDirty = true;
                if (tradeableStatusSync != null) {
                    tradeableStatusSync.notifyUpdate();
                }
                if (cooldownStatusSync != null) {
                    cooldownStatusSync.notifyUpdate();
                }
                if (tradeResultSync != null) {
                    tradeResultSync.notifyUpdate();
                }
            } else {
                tradeResultMessage = EnumChatFormatting.RED + getTradeResultMessage(result);
                if (tradeResultSync != null) {
                    tradeResultSync.notifyUpdate();
                }
            }
        } catch (Throwable t) {
            GTInterestingThing.LOG.error("[NekoVMV2] processTradeRequest 异常!", t);
            tradeResultMessage = "交易处理异常";
            if (tradeResultSync != null) {
                tradeResultSync.notifyUpdate();
            }
        }
    }

    /**
     * 处理收藏切换请求
     * <p>
     * 解析 "groupId:tradeIndex" 格式的请求字符串，
     * 调用 {@link NekoFavouritesTracker#toggleFavourite} 切换收藏状态。
     *
     * @param request  请求字符串 "groupId:tradeIndex"
     * @param playerId 玩家 UUID
     */
    private void processFavouriteToggle(String request, UUID playerId) {
        // C2S change listener 在客户端也会触发，收藏切换只在服务端执行
        if (isClient()) return;
        try {
            if (playerId == null) return;

            String[] parts = request.split(":");
            if (parts.length != 2) return;

            UUID groupId;
            int tradeIndex;
            try {
                groupId = UUID.fromString(parts[0]);
                tradeIndex = Integer.parseInt(parts[1]);
            } catch (Exception e) {
                return;
            }

            NekoFavouritesTracker.INSTANCE.toggleFavourite(playerId, groupId, tradeIndex);
        } catch (Throwable t) {
            GTInterestingThing.LOG.error("[NekoVMV2] processFavouriteToggle 异常!", t);
        }
    }

    /**
     * 获取交易结果的本地化消息
     *
     * @param result 交易结果
     * @return 消息文本
     */
    private String getTradeResultMessage(NekoTradeResult result) {
        switch (result.getStatus()) {
            case SUCCESS:
                return "交易成功";
            case TRADE_GROUP_NOT_FOUND:
                return "交易组不存在";
            case TRADE_INDEX_OUT_OF_BOUNDS:
                return "交易索引越界";
            case ON_COOLDOWN:
                return "冷却中，请稍后再试";
            case MAX_TRADES_REACHED:
                return "已达到最大交易次数";
            case CONDITION_NOT_SATISFIED:
                return "前置条件未满足";
            case INSUFFICIENT_CURRENCY:
                return "猫猫币不足";
            case INSUFFICIENT_ITEMS:
                return "输入物品不足";
            case OUTPUT_FULL:
                return "输出总线已满";
            default:
                return result.getMessage() != null ? result.getMessage() : "交易失败";
        }
    }

    // ==================== BQ 锁定状态同步（保留原有逻辑，改为同步所有交易组） ====================

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

    // ==================== 冷却状态同步（保留原有逻辑，改为同步所有交易组） ====================

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

    // ==================== 可交易状态同步（服务端综合计算 BQ/冷却/钱包/输入物品） ====================

    /**
     * 构建可交易状态字符串（服务端）
     * <p>
     * 遍历所有交易组及其交易，调用 {@link MTENekoVendingMachineV2#checkTrade} 综合判断
     * BQ 锁定、冷却、钱包余额和输入物品是否满足，生成格式字符串：
     * {@code "groupId:tradeIndex:true,groupId:tradeIndex:false,..."}。
     * <p>
     * 为降低性能开销，使用缓存策略：仅在 {@link #tradeableStatusDirty} 为 true
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
     * {@link #tradeableStatusMap}："groupId:tradeIndex" → boolean。
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

    // ==================== 辅助方法 ====================

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
    private ItemStack getCategoryIcon(NekoTradeCategory category) {
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
    private String getCategoryName(NekoTradeCategory category) {
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

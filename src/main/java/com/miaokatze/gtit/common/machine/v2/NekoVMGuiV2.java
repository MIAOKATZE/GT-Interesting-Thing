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
import com.cleanroommc.modularui.utils.item.ItemStackHandler;
import com.cleanroommc.modularui.value.StringValue;
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
import com.cleanroommc.modularui.widgets.slot.PhantomItemSlot;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;
import com.miaokatze.gtit.client.gui.NekoCoinDisplayV2;
import com.miaokatze.gtit.client.gui.NekoConfirmationDialog;
import com.miaokatze.gtit.client.gui.NekoDisplayType;
import com.miaokatze.gtit.client.gui.NekoFallingItemSlotFactory;
import com.miaokatze.gtit.client.gui.NekoGuiTextures;
import com.miaokatze.gtit.client.gui.NekoMainTabButton;
import com.miaokatze.gtit.client.gui.NekoMeTransferParticleWidget;
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
import com.miaokatze.gtit.lottery.LotteryClientData;
import com.miaokatze.gtit.lottery.LotteryEntry;
import com.miaokatze.gtit.lottery.LotteryGui;
import com.miaokatze.gtit.lottery.LotteryRarity;
import com.miaokatze.gtit.mail.MailGui;
import com.miaokatze.gtit.main.GTInterestingThing;
import com.miaokatze.gtit.signin.SignInCalendarGui;
import com.miaokatze.gtit.trade.NekoCurrencyRegistrar;
import com.miaokatze.gtit.trade.NekoPageEntry;
import com.miaokatze.gtit.trade.NekoPageRegistry;
import com.miaokatze.gtit.trade.NekoWallet;
import com.miaokatze.gtit.trade.NekoWalletManager;
import com.miaokatze.gtit.trade.v2.NekoBigItemStack;
import com.miaokatze.gtit.trade.v2.NekoFavouritesTracker;
import com.miaokatze.gtit.trade.v2.NekoHistoryManager;
import com.miaokatze.gtit.trade.v2.NekoTrade;
import com.miaokatze.gtit.trade.v2.NekoTradeCategory;
import com.miaokatze.gtit.trade.v2.NekoTradeDatabase;
import com.miaokatze.gtit.trade.v2.NekoTradeExecutor;
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

    // ==================== v1.7.0 主标签常量 ====================

    /** 主标签-贸易（默认） */
    public static final int MAIN_TAB_TRADE = 0;
    /** 主标签-签到 */
    public static final int MAIN_TAB_SIGNIN = 1;
    /** 主标签-抽奖 */
    public static final int MAIN_TAB_LOTTERY = 2;
    /** 主标签-邮件 */
    public static final int MAIN_TAB_MAIL = 3;
    /** 主标签总数 */
    public static final int MAIN_TAB_COUNT = 4;

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
    /**
     * 团队缩放状态字符串（S2C：服务端构建 "groupId:maxTrades:usedTrades,..." 发送到客户端）
     * <p>
     * 用于 tooltip 显示"冷却: X/Y 次（团队缩放）"信息。
     * 仅对有冷却（{@code group.getCooldown() > 0}）的交易组输出。
     * 客户端无法直接调用 GTNHLib Teams API，必须通过此同步值获取缩放信息。
     * </p>
     */
    private StringSyncValue teamScaleSync;
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
    /** v1.7.0 主标签索引（C2S：客户端切换主标签时发送到服务端） */
    private IntSyncValue mainTabSync;
    /** 是否显示猫猫币余额行 */
    private boolean showCoins = true;
    /** 各货币余额同步值映射 */
    private final Map<String, IntSyncValue> coinAmountSyncs = new HashMap<>();
    /** 客户端缓存的 ME 网络货币余额（currencyId → amount，阶段 6） */
    private final Map<String, Integer> meCoinAmounts = new HashMap<>();
    /** ME 货币余额同步值映射（供 tooltip 查询和刷新，阶段 6） */
    private final Map<String, IntSyncValue> meCoinAmountSyncs = new HashMap<>();
    /** ME 货币导入请求标志（currencyId → flag，阶段 6） */
    private final Map<String, Boolean> nekoImportMeCoin = new HashMap<>();
    /** ME 货币导入同步值映射（供按钮调用 setValue，阶段 6） */
    private final Map<String, BooleanSyncValue> nekoImportMeCoinSyncs = new HashMap<>();

    // ==================== UI 状态镜像（客户端+服务端共享） ====================

    /** 当前标签页索引（默认 0=FAVOURITES 分类） */
    private int currentTabId = 0;
    /** 搜索文本 */
    private String searchText = "";
    /** 排序模式：0=SMART, 1=ALPHABET */
    private int sortMode = 0;
    /** 显示模式：0=TILE, 1=LIST */
    private int displayType = 0;
    /** v1.7.0 当前主标签索引（默认 0=贸易） */
    private int mainTabId = MAIN_TAB_TRADE;

    // ==================== 客户端状态（S2C 同步填充） ====================

    /** BQ 锁定状态映射：groupId → 是否锁定（true=锁定） */
    private final Map<UUID, Boolean> bqLockStatusMap = new HashMap<>();
    /** 冷却状态映射："groupId:tradeIndex" → 剩余秒数 */
    private final Map<String, Long> cooldownStatusMap = new HashMap<>();
    /** 可交易状态映射："groupId:tradeIndex" → 是否可交易（true=可交易） */
    private final Map<String, Boolean> tradeableStatusMap = new HashMap<>();

    /**
     * 团队缩放状态映射：groupId → [maxTrades, usedTrades]
     * <p>
     * 由服务端通过 {@link #teamScaleSync} 同步到客户端。
     * 存储每个有冷却的交易组的团队缩放信息（冷却内最大次数和已用次数），
     * 用于 tooltip 显示"冷却: X/Y 次（团队缩放）"。
     * </p>
     */
    private final Map<UUID, long[]> teamScaleMap = new HashMap<>();

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
    /** 弹出一组（64个）单种猫猫币标志映射（v1.6.22：Ctrl+点击弹出 1 组） */
    private final Map<String, Boolean> nekoEjectCoinStack = new HashMap<>();
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
    /** v1.7.0 主标签分页控制器（客户端，管理贸易/签到/抽奖/邮件切换） */
    private PagedWidget.Controller mainTabController;
    /** 搜索栏组件（客户端） */
    private NekoSearchBar searchBar;
    /** 音量/BGM 按钮（客户端，作为音量面板的 parent；v1.6.24 提升为类字段以供 build() 在 client 块外注册 syncedPanel） */
    private ButtonWidget<?> volumeButton;
    /** 音量面板处理器（客户端，打开/关闭音量控制面板） */
    private IPanelHandler volumePanel;
    /** 主面板引用（用于回调和方法调用） */
    private NekoTradeMainPanel mainPanel;
    /** 输入槽 Widget 引用（服务端在弹出/入账后强制同步槽位状态到客户端） */
    private final List<ItemSlot> inputSlotRefs = new ArrayList<>();
    /** ME 输出模式同步值（C2S+S2C：客户端切换发送到服务端，服务端状态同步到客户端） */
    private BooleanSyncValue meOutputModeSync;
    /** Uplink 连接状态同步值（S2C：控制 ME 模式按钮可见性） */
    private BooleanSyncValue hasUplinkSync;
    /** ME 模式切换确认弹框（客户端） */
    private NekoConfirmationDialog meModeConfirmDialog;
    /** ME 模式切换确认面板 handler（客户端） */
    private IPanelHandler meModeConfirmPanel;
    /** ME 传输队列同步值（S2C：服务端序列化队列发到客户端，用于粒子动画渲染） */
    private StringSyncValue meTransferQueueSync;
    /** 取回 ME 传输队列物品请求（C2S：客户端点击取回时发送 true） */
    private BooleanSyncValue retrieveMeItemSync;
    /** 客户端缓存的 ME 传输队列（从同步值解析，供粒子 Widget 渲染） */
    private final java.util.List<MTENekoVendingMachineV2.MeTransferEntry> clientMeTransferQueue = new java.util.ArrayList<>();

    // ==================== 编辑模式（v1.7.0 目标 4） ====================

    /** 编辑模式状态同步值（S2C：服务端权威，同步到客户端控制 GUI 行为） */
    private BooleanSyncValue editModeSync;
    /** 编辑面板处理器（客户端，打开/关闭交易编辑面板） */
    private IPanelHandler editPanelHandler;
    /** 当前正在编辑的交易显示数据（客户端，打开编辑面板时设置） */
    private NekoTradeItemDisplay editingDisplay;

    // --- 编辑面板本地状态（客户端，保存时序列化为 JSON 发送到服务端） ---
    /** 编辑：猫猫币类型（"neko" / "shimmeringNeko" / 空=无） */
    private String editCurrencyType = "";
    /** 编辑：猫猫币数量 */
    private int editCurrencyAmount = 0;
    /** 编辑：冷却时间（秒） */
    private int editCooldown = 0;
    /** 编辑：最大交易次数（-1=无限制） */
    private int editMaxTrades = -1;
    /** 编辑：BQ 任务绑定 ID */
    private String editBqQuestId = "";
    /** 编辑：标签页 ID */
    private int editTabId = 1;
    /** 编辑：顺序 ID */
    private int editOrderId = 0;

    /** 编辑目标同步值（C2S：客户端设置 "groupId:tradeIndex"，服务端据此加载交易数据到编辑缓冲区） */
    private StringSyncValue editTargetSync;
    /** 编辑物品缓冲区（双端共享：slot 0-3=需求物品，slot 4-7=产物物品） */
    private final ItemStackHandler editItemHandler = new ItemStackHandler(8);

    // --- 签到编辑（v1.7.0 目标 4：阶梯宝箱/全局配置编辑） ---
    /** 签到编辑面板处理器（客户端，打开/关闭签到编辑面板） */
    private IPanelHandler signInEditPanelHandler;
    /** 签到编辑目标同步值（C2S："tier:<days>"，服务端据此加载阶梯物品奖励到编辑缓冲区） */
    private StringSyncValue editSignInTargetSync;
    /** 签到编辑物品缓冲区（双端共享：slot 0=阶梯物品奖励） */
    private final ItemStackHandler editSignInItemHandler = new ItemStackHandler(1);
    /** 签到编辑：目标阶梯天数（>0=阶梯编辑模式，-1=全局配置编辑模式） */
    private int editSignInTierDays = -1;
    /** 签到编辑：货币 ID（阶梯模式） */
    private String editSignInCurrency = "neko";
    /** 签到编辑：货币数量（阶梯模式） */
    private int editSignInAmount = 0;
    /** 签到编辑：每日基础奖励（全局模式） */
    private int editSignInBaseReward = 0;
    /** 签到编辑：连续递增系数（全局模式，字符串暂存，保存时解析） */
    private String editSignInIncrement = "1.0";

    // --- 抽奖编辑（v1.7.0 目标 4：轮盘条目编辑） ---
    /** 抽奖编辑面板处理器（客户端，打开/关闭抽奖条目编辑面板） */
    private IPanelHandler lotteryEditPanelHandler;
    /** 抽奖编辑目标同步值（C2S："<poolId>:<entryId>"，服务端据此加载条目物品到编辑缓冲区） */
    private StringSyncValue editLotteryTargetSync;
    /** 抽奖编辑物品缓冲区（双端共享：slot 0=物品奖品） */
    private final ItemStackHandler editLotteryItemHandler = new ItemStackHandler(1);
    /** 抽奖编辑：条目标识（"<poolId>:<entryId>"，保存时原样发回服务端定位） */
    private String editLotteryEntryKey = "";
    /** 抽奖编辑：货币 ID（非空 = 货币奖品，保存时忽略物品槽） */
    private String editLotteryCurrency = "";
    /** 抽奖编辑：最小数量 */
    private int editLotteryMinAmount = 1;
    /** 抽奖编辑：最大数量 */
    private int editLotteryMaxAmount = 1;
    /** 抽奖编辑：权重（0 = 永不中出） */
    private int editLotteryWeight = 1;
    /** 抽奖编辑：稀有度名（COMMON/RARE/EPIC/LEGENDARY） */
    private String editLotteryRarity = "COMMON";

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
            // v1.7.0 主标签控制器（贸易/签到/抽奖/邮件）
            mainTabController = new PagedWidget.Controller();
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
            // 初始化 ME 模式切换确认弹框（仅客户端）
            meModeConfirmDialog = new NekoConfirmationDialog("nekoV2:me_mode_confirm");
            meModeConfirmPanel = IPanelHandler.simple(panel, (parent, player) -> meModeConfirmDialog, true);
        }

        // v1.7.0 主标签列（贸易/签到/抽奖/邮件），位于贸易分类列的更左边
        if (syncManager.isClient()) {
            panel.child(createMainTabColumn());
        }

        // 左侧贸易分类标签列（仅主标签为贸易时显示）
        if (syncManager.isClient()) {
            panel.child(createTabColumn());
            panel.child(createQolButtonColumn());
        }

        // v1.6.24: 音量面板在 client 块外部注册（与 VM 原版 MTEVendingMachineGui 一致），
        // 确保服务端也注册同步通道，否则客户端 togglePanel() 静默失败导致面板无法弹出。
        // volumeButton 在 createQolButtonColumn 内被赋值（仅客户端），但 syncedPanel 第二参数 true
        // 表示仅在客户端创建面板，回调中的 volumeButton 引用仅在客户端被使用，服务端不触发回调。
        volumePanel = syncManager
            .syncedPanel("nekoV2Volume", true, (sm, sh) -> new NekoVolumeControlGui().createPanel(sm, volumeButton));

        // v1.7.0 目标 4：交易编辑面板（编辑模式下点击交易条目弹出）
        editPanelHandler = syncManager.syncedPanel("nekoV2TradeEdit", true, (sm, sh) -> buildTradeEditPanel(sm));

        // v1.7.0 目标 4：签到编辑面板（编辑模式下点击阶梯宝箱/「全局配置」按钮弹出）
        signInEditPanelHandler = syncManager
            .syncedPanel("nekoV2SignInEdit", true, (sm, sh) -> buildSignInEditPanel(sm));

        // v1.7.0 目标 4：抽奖编辑面板（编辑模式下点击轮盘槽位弹出）
        lotteryEditPanelHandler = syncManager
            .syncedPanel("nekoV2LotteryEdit", true, (sm, sh) -> buildLotteryEditPanel(sm));

        // v1.7.0 主内容区（PagedWidget 切换贸易/签到/抽奖/邮件）
        // v1.7.5 修复：仅客户端创建——mainTabController/tabController 仅在客户端初始化（上方 isClient 块），
        // 服务端执行到 .controller(null) 会 NPE，createPanel 中断导致右击无反应。
        // 4 个页面均无槽位、无 net.minecraft.client 引用，仅客户端创建安全（与 VM 原版一致）。
        if (syncManager.isClient()) {
            panel.child(createMainContentPagedWidget(syncManager));
        }

        // --- 玩家背包栏（v1.7.5 从贸易列拆出，双端挂 panel）---
        // 双端创建：服务端必须注册背包槽，否则容器缺槽、shift 转移失效（与 VM 原版 createInventoryRow 一致）。
        // setEnabledIf 仅阻止 draw 不影响槽同步（v1.6.24 已确认），保持仅贸易页可见的现有视觉。
        panel.child(
            Flow.row()
                .fullWidth()
                .height(76)
                .bottom(5)
                .setEnabledIf(w -> mainTabId == MAIN_TAB_TRADE)
                .child(
                    SlotGroupWidget.playerInventory(false)
                        .marginLeft(4)));

        // 右侧 IO 列（仅主标签为贸易时显示）
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

        // --- v1.7.0 主标签索引（C2S）---
        mainTabSync = new IntSyncValue(() -> mainTabId, val -> { mainTabId = val; });
        mainTabSync.allowC2S();
        syncManager.syncValue("nekoV2MainTab", mainTabSync);

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

        // --- 团队缩放状态（S2C，同步冷却内最大次数和已用次数，用于 tooltip 展示）---
        // 客户端无法直接调用 GTNHLib Teams API，必须通过此同步值获取缩放信息
        teamScaleSync = new StringSyncValue(
            () -> buildTeamScaleString(playerId),
            val -> { parseTeamScaleString(val); });
        syncManager.syncValue("nekoV2TeamScale", teamScaleSync);

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

        // --- ME 网络货币余额（S2C，阶段 6）---
        // 仅在 hasUplink 时有意义，客户端缓存到 meCoinAmounts 供 tooltip 和导入按钮显示
        for (String currencyId : NekoCurrencyRegistrar.getNekoCurrencyIds()) {
            final String cid = currencyId;
            IntSyncValue meCoinSync = new IntSyncValue(() -> {
                if (syncManager.isClient() || playerId == null || multiblock == null) return 0;
                return multiblock.getUplinkCurrencyAmount(cid);
            }, val -> {
                if (syncManager != null && syncManager.isClient()) {
                    meCoinAmounts.put(cid, val);
                }
            });
            syncManager.syncValue("nekoMeCoinAmount_" + currencyId, meCoinSync);
            meCoinAmountSyncs.put(currencyId, meCoinSync);
        }

        // --- ME 货币导入请求（C2S，阶段 6）---
        // 客户端点击导入按钮时 setValue(true)，服务端处理提取并加到钱包
        for (String currencyId : NekoCurrencyRegistrar.getNekoCurrencyIds()) {
            final String cid = currencyId;
            nekoImportMeCoin.put(cid, false);
            BooleanSyncValue importSync = new BooleanSyncValue(() -> nekoImportMeCoin.getOrDefault(cid, false), val -> {
                if (val && syncManager != null && !syncManager.isClient()) {
                    doNekoImportMeCoin(cid);
                }
                nekoImportMeCoin.put(cid, false);
            });
            importSync.allowC2S();
            syncManager.syncValue("nekoV2ImportMeCoin_" + currencyId, importSync);
            nekoImportMeCoinSyncs.put(currencyId, importSync);
        }

        // BQ 锁定/冷却状态变化时也需要重新计算可交易状态
        if (!syncManager.isClient()) {
            Runnable tradeableStatusDirtyMarker = () -> {
                tradeableStatusDirty = true;
                if (tradeableStatusSync != null) {
                    tradeableStatusSync.notifyUpdate();
                }
                // 冷却状态变化意味着交易已执行或冷却已重置，团队缩放信息（已用次数）也需更新
                if (teamScaleSync != null) {
                    teamScaleSync.notifyUpdate();
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

        // --- 弹出一组单种猫猫币（C2S：v1.6.22 新增，Ctrl+点击触发）---
        // 与 nekoEjectCoin_ 区别：仅弹出 1 组（64 个，不足则全部），而非全部余额
        for (String currencyId : NekoCurrencyRegistrar.getNekoCurrencyIds()) {
            final String cid = currencyId;
            BooleanSyncValue ejectCoinStackSyncer = new BooleanSyncValue(
                () -> nekoEjectCoinStack.getOrDefault(cid, false),
                val -> {
                    nekoEjectCoinStack.put(cid, val);
                    if (val) {
                        doNekoEjectCoinStack(cid, playerId);
                    }
                });
            ejectCoinStackSyncer.allowC2S();
            syncManager.syncValue("nekoEjectCoinStack_" + currencyId, ejectCoinStackSyncer);
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

        // --- ME 输出模式（C2S+S2C）---
        // 客户端可发起切换（allowC2S），服务端持久化并同步状态
        meOutputModeSync = new BooleanSyncValue(() -> multiblock != null && multiblock.isMeOutputMode(), val -> {
            if (syncManager != null && !syncManager.isClient() && multiblock != null) {
                multiblock.setMeOutputMode(val);
                // ME 模式切换后通知队列同步值刷新，让客户端立即获知当前队列状态
                if (meTransferQueueSync != null) {
                    meTransferQueueSync.notifyUpdate();
                }
            }
        });
        meOutputModeSync.allowC2S();
        syncManager.syncValue("nekoV2MeOutputMode", meOutputModeSync);

        // --- Uplink 连接状态（S2C）---
        hasUplinkSync = new BooleanSyncValue(() -> multiblock != null && multiblock.hasUplink(), val -> {});
        syncManager.syncValue("nekoV2HasUplink", hasUplinkSync);

        // --- ME 传输队列（S2C：阶段 4）---
        // 服务端序列化 meTransferQueue 发到客户端，客户端解析后用于粒子动画渲染
        meTransferQueueSync = new StringSyncValue(
            () -> multiblock != null ? multiblock.serializeMeTransferQueue() : "",
            val -> {
                if (syncManager != null && syncManager.isClient()) {
                    parseMeTransferQueue(val);
                }
            });
        syncManager.syncValue("nekoV2MeTransferQueue", meTransferQueueSync);

        // --- ME 传输队列大小心跳（S2C：v1.6.22 补丁）---
        // 利用 getRefreshInterval() 的 20 tick 周期性刷新机制，
        // 当队列大小变化时触发 meTransferQueueSync 刷新，确保客户端粒子动画及时更新。
        // 这解决了机器端 onPostTick 中 processMeTransferQueue 自动移除到期条目时
        // 客户端无法及时获知的问题。
        IntSyncValue meQueueSizeSync = new IntSyncValue(
            () -> multiblock != null ? multiblock.getMeTransferQueueSize() : 0,
            val -> {});
        meQueueSizeSync.setChangeListener(() -> {
            // 仅在服务端触发 meTransferQueueSync 刷新（v1.6.22 修复 SecurityException）
            // changeListener 在客户端也会触发（收到 S2C 更新时），但 S2C 同步值在客户端
            // 调用 notifyUpdate() 会尝试发送 packet 到服务端，导致 SecurityException
            if (meTransferQueueSync != null && syncManager != null && !syncManager.isClient()) {
                meTransferQueueSync.notifyUpdate();
            }
        });
        syncManager.syncValue("nekoV2MeQueueSize", meQueueSizeSync);

        // --- 取回 ME 传输队列物品（C2S：阶段 4）---
        // 客户端点击取回按钮时发送 true，服务端调用 retrieveEarliestMeTransferItem
        retrieveMeItemSync = new BooleanSyncValue(() -> false, val -> {
            if (val && syncManager != null && !syncManager.isClient() && multiblock != null) {
                boolean ok = multiblock.retrieveEarliestMeTransferItem();
                if (ok) {
                    tradeResultMessage = "已取回 ME 传输队列中的物品";
                    if (tradeResultSync != null) {
                        tradeResultSync.setValue(tradeResultMessage);
                    }
                    // 通知队列同步值刷新，让客户端立即看到队列变化
                    if (meTransferQueueSync != null) {
                        meTransferQueueSync.notifyUpdate();
                    }
                }
            }
        });
        retrieveMeItemSync.allowC2S();
        syncManager.syncValue("nekoV2RetrieveMeItem", retrieveMeItemSync);

        // --- 货币显示开关（C2S）---
        showCoinsSync = new BooleanSyncValue(() -> showCoins, val -> { showCoins = val; });
        showCoinsSync.allowC2S();
        syncManager.syncValue("nekoV2ShowCoins", showCoinsSync);

        // --- 编辑模式状态（S2C，v1.7.0 目标 4）---
        // 服务端权威：查询 NekoEditModeManager 判断玩家是否处于编辑模式
        // 客户端收到后更新所有交易 Widget 的编辑模式标志
        editModeSync = new BooleanSyncValue(
            () -> playerId != null && com.miaokatze.gtit.trade.v2.NekoEditModeManager.INSTANCE.isInEditMode(playerId),
            val -> {
                // 客户端：传播编辑模式状态到所有交易 Widget
                propagateEditModeToWidgets(val);
            });
        syncManager.syncValue("nekoV2EditMode", editModeSync);

        // --- 编辑目标（C2S：客户端设置交易位置，服务端加载到编辑缓冲区）---
        editTargetSync = new StringSyncValue(() -> "", val -> {
            if (syncManager != null && !syncManager.isClient() && val != null && !val.isEmpty()) {
                loadTradeIntoEditBuffer(val);
            }
        });
        editTargetSync.allowC2S();
        syncManager.syncValue("nekoV2EditTarget", editTargetSync);

        // --- 签到编辑目标（C2S：客户端设置 "tier:<days>"，服务端加载阶梯物品奖励到编辑缓冲区）---
        editSignInTargetSync = new StringSyncValue(() -> "", val -> {
            if (syncManager != null && !syncManager.isClient() && val != null && !val.isEmpty()) {
                loadSignInTierIntoEditBuffer(val);
            }
        });
        editSignInTargetSync.allowC2S();
        syncManager.syncValue("nekoV2EditSignInTarget", editSignInTargetSync);

        // --- 抽奖编辑目标（C2S：客户端设置 "<poolId>:<entryId>"，服务端加载条目物品到编辑缓冲区）---
        editLotteryTargetSync = new StringSyncValue(() -> "", val -> {
            if (syncManager != null && !syncManager.isClient() && val != null && !val.isEmpty()) {
                loadLotteryEntryIntoEditBuffer(val);
            }
        });
        editLotteryTargetSync.allowC2S();
        syncManager.syncValue("nekoV2EditLotteryTarget", editLotteryTargetSync);
    }

    /**
     * 传播编辑模式状态到所有预分配的交易 Widget
     * <p>
     * 编辑模式下，Widget 的点击行为从「Shift+交易 / Ctrl+收藏」
     * 变为「左键打开编辑面板」。
     *
     * @param editMode true 表示处于编辑模式
     */
    private void propagateEditModeToWidgets(boolean editMode) {
        for (List<NekoTradeItemDisplayWidget> widgets : displayedTradesTiles.values()) {
            for (NekoTradeItemDisplayWidget widget : widgets) {
                widget.setEditMode(editMode);
            }
        }
        for (List<NekoTradeItemDisplayWidget> widgets : displayedTradesList.values()) {
            for (NekoTradeItemDisplayWidget widget : widgets) {
                widget.setEditMode(editMode);
            }
        }
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
        // 检测当前玩家是否在团队中，如果在团队中则返回 TEAM 模式
        // NekoWalletManager.getWallet() 会自动路由到团队钱包（通过 GTNHLib Teams API），
        // 因此钱包模式需要显式反映当前实际使用的钱包来源
        UUID playerId = getPlayerId();
        if (playerId == null) {
            return NekoWalletMode.PERSONAL;
        }
        try {
            // 通过 GTNHLib Teams API 检查玩家是否在团队中
            com.gtnewhorizon.gtnhlib.teams.Team team = com.gtnewhorizon.gtnhlib.teams.TeamManager
                .getTeamByPlayer(playerId);
            if (team != null) {
                // 玩家在团队中，NekoWalletManager 会自动使用团队共享钱包
                return NekoWalletMode.TEAM;
            }
        } catch (NoClassDefFoundError e) {
            // GTNHLib Teams API 不可用，回退到个人钱包模式
            return NekoWalletMode.PERSONAL;
        } catch (Exception e) {
            GTInterestingThing.LOG.error("[NekoVMGuiV2] getWalletMode 检测团队状态异常", e);
        }
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

                // 设置团队缩放信息（来自 teamScaleSync）
                // 客户端通过同步值获取冷却内最大次数和已用次数，用于 tooltip 展示
                long[] scaleInfo = teamScaleMap.get(display.getGroupId());
                if (scaleInfo != null && scaleInfo.length >= 2) {
                    display.setMaxTradesInCooldown((int) scaleInfo[0]);
                    display.setUsedTradesInCooldown(scaleInfo[1]);
                } else {
                    // 未同步到缩放信息时使用默认值（个人限制，无已用次数）
                    display.setMaxTradesInCooldown(1);
                    display.setUsedTradesInCooldown(0);
                }

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
        // 恢复上次的主标签位置（v1.7.0）
        if (mainTabController != null) {
            int page = Math.min(NekoMainTabButton.lastMainTab, MAIN_TAB_COUNT - 1);
            if (page < 0) page = MAIN_TAB_TRADE;
            mainTabController.setPage(page);
            mainTabId = page;
            // v1.7.5 修复：同步 mainTabSync 使服务端 mainTabId 与客户端一致
            // （否则服务端恒 0，背包行 enabled 状态双端可能不一致）
            if (mainTabSync != null) mainTabSync.setValue(page);
        }
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

    /**
     * 编辑请求回调（编辑模式下左键点击交易时触发）
     * <p>
     * 打开交易编辑面板，显示当前交易数据供编辑。
     * 先通过 editTargetSync 告知服务端加载交易数据到编辑缓冲区，
     * 再打开编辑面板（PhantomItemSlot 自动同步缓冲区内容到客户端）。
     *
     * @param display 被点击的交易显示数据
     */
    @Override
    public void onEditRequested(NekoTradeItemDisplay display) {
        if (display == null) return;
        this.editingDisplay = display;
        // 填充编辑参数（从显示数据中提取）
        populateEditFields(display);
        // 通知服务端加载交易数据到编辑缓冲区
        if (editTargetSync != null) {
            editTargetSync.setValue(
                display.getGroupId()
                    .toString() + ":"
                    + display.getTradeIndex());
        }
        // 打开编辑面板
        if (editPanelHandler != null) {
            editPanelHandler.openPanel();
        }
    }

    /**
     * 检查当前是否处于编辑模式
     * <p>
     * 通过 {@link #editModeSync} 同步值判断（服务端权威，S2C 同步到客户端）。
     *
     * @return true 表示处于编辑模式
     */
    private boolean isEditModeActive() {
        return editModeSync != null && editModeSync.getValue();
    }

    // ==================== 编辑模式：数据加载与面板构建 ====================

    /**
     * 从交易显示数据填充编辑字段（客户端）
     * <p>
     * 将 {@link NekoTradeItemDisplay} 中的数据提取到编辑面板本地字段，
     * 供编辑面板的 TextFieldWidget 显示和编辑。
     *
     * @param display 交易显示数据
     */
    private void populateEditFields(NekoTradeItemDisplay display) {
        // 猫猫币信息
        String currencyId = display.getCurrencyId();
        editCurrencyType = currencyId != null ? currencyId : "";
        editCurrencyAmount = (int) display.getCost();

        // 从 NekoTradeDatabase 获取额外配置信息（冷却、BQ 绑定等）
        NekoTradeGroup group = NekoTradeDatabase.INSTANCE.getTradeGroup(display.getGroupId());
        if (group != null) {
            editCooldown = group.getCooldown();
            editMaxTrades = group.getMaxTrades();
            editBqQuestId = group.getBqQuestId() != null ? group.getBqQuestId() : "";
            editTabId = group.getTabId();
            editOrderId = group.getOrderId();
        }
    }

    /**
     * 服务端：加载交易数据到编辑缓冲区
     * <p>
     * 解析 "groupId:tradeIndex" 格式的目标标识，从 {@link NekoTradeDatabase}
     * 查找交易，将需求物品加载到 editItemHandler 的 slot 0-3，
     * 产物物品加载到 slot 4-7。
     *
     * @param target "groupId:tradeIndex" 格式的目标标识
     */
    private void loadTradeIntoEditBuffer(String target) {
        try {
            String[] parts = target.split(":");
            if (parts.length != 2) return;
            UUID groupId = UUID.fromString(parts[0]);
            int tradeIndex = Integer.parseInt(parts[1]);

            NekoTradeGroup group = NekoTradeDatabase.INSTANCE.getTradeGroup(groupId);
            if (group == null || tradeIndex < 0
                || tradeIndex >= group.getTrades()
                    .size()) {
                return;
            }

            NekoTrade trade = group.getTrades()
                .get(tradeIndex);

            // 清空缓冲区
            for (int i = 0; i < editItemHandler.getSlots(); i++) {
                editItemHandler.setStackInSlot(i, null);
            }

            // 加载需求物品到 slot 0-3
            List<NekoBigItemStack> fromItems = trade.getFromItems();
            for (int i = 0; i < Math.min(fromItems.size(), 4); i++) {
                NekoBigItemStack bigStack = fromItems.get(i);
                if (bigStack != null && bigStack.getBaseStack() != null) {
                    ItemStack stack = bigStack.getBaseStack()
                        .copy();
                    stack.stackSize = bigStack.getStackSize();
                    editItemHandler.setStackInSlot(i, stack);
                }
            }

            // 加载产物物品到 slot 4-7
            List<NekoBigItemStack> toItems = trade.getToItems();
            for (int i = 0; i < Math.min(toItems.size(), 4); i++) {
                NekoBigItemStack bigStack = toItems.get(i);
                if (bigStack != null && bigStack.getBaseStack() != null) {
                    ItemStack stack = bigStack.getBaseStack()
                        .copy();
                    stack.stackSize = bigStack.getStackSize();
                    editItemHandler.setStackInSlot(4 + i, stack);
                }
            }

            GTInterestingThing.LOG.info("[NekoEdit] 已加载交易到编辑缓冲区: {}", target);
        } catch (Exception e) {
            GTInterestingThing.LOG.error("[NekoEdit] 加载交易到编辑缓冲区失败: {}", target, e);
        }
    }

    /**
     * 构建交易编辑面板
     * <p>
     * 创建包含 PhantomItemSlot（物品拖放配置）和 TextFieldWidget（参数编辑）
     * 的编辑面板。slot 0-3 为需求物品，slot 4-7 为产物物品。
     *
     * @param sm 面板同步管理器
     * @return 编辑面板
     */
    private ModularPanel buildTradeEditPanel(PanelSyncManager sm) {
        ModularPanel editPanel = new ModularPanel("nekoV2TradeEdit");
        editPanel.size(200, 180);

        // 标题
        editPanel.child(
            new TextWidget<>(IKey.str(EnumChatFormatting.GOLD + "编辑交易")).top(5)
                .horizontalCenter());

        // --- 需求物品行（slot 0-3）---
        editPanel.child(
            IKey.str("需求:")
                .asWidget()
                .left(8)
                .top(22));
        for (int i = 0; i < 4; i++) {
            editPanel.child(
                new PhantomItemSlot().slot(new ModularSlot(editItemHandler, i))
                    .left(40 + i * 20)
                    .top(20));
        }

        // --- 产物物品行（slot 4-7）---
        editPanel.child(
            IKey.str("产物:")
                .asWidget()
                .left(8)
                .top(42));
        for (int i = 0; i < 4; i++) {
            editPanel.child(
                new PhantomItemSlot().slot(new ModularSlot(editItemHandler, 4 + i))
                    .left(40 + i * 20)
                    .top(40));
        }

        // --- 参数编辑区 ---
        int fieldY = 65;
        int fieldHeight = 14;
        int labelWidth = 65;
        int fieldWidth = 120;
        int spacing = 17;

        // 猫猫币类型
        editPanel.child(
            IKey.str("猫猫币类型:")
                .asWidget()
                .left(8)
                .top(fieldY + 2));
        editPanel.child(
            new TextFieldWidget().value(new StringValue.Dynamic(() -> editCurrencyType, val -> editCurrencyType = val))
                .setMaxLength(30)
                .left(labelWidth)
                .top(fieldY)
                .size(fieldWidth, fieldHeight));

        // 猫猫币数量
        fieldY += spacing;
        editPanel.child(
            IKey.str("猫猫币数量:")
                .asWidget()
                .left(8)
                .top(fieldY + 2));
        editPanel.child(
            new TextFieldWidget().value(new StringValue.Dynamic(() -> String.valueOf(editCurrencyAmount), val -> {
                try {
                    editCurrencyAmount = Integer.parseInt(val);
                } catch (NumberFormatException ignored) {}
            }))
                .setNumbers(0, Integer.MAX_VALUE)
                .left(labelWidth)
                .top(fieldY)
                .size(fieldWidth, fieldHeight));

        // 冷却时间
        fieldY += spacing;
        editPanel.child(
            IKey.str("冷却(秒):")
                .asWidget()
                .left(8)
                .top(fieldY + 2));
        editPanel.child(new TextFieldWidget().value(new StringValue.Dynamic(() -> String.valueOf(editCooldown), val -> {
            try {
                editCooldown = Integer.parseInt(val);
            } catch (NumberFormatException ignored) {}
        }))
            .setNumbers(-1, Integer.MAX_VALUE)
            .left(labelWidth)
            .top(fieldY)
            .size(fieldWidth, fieldHeight));

        // BQ 绑定 ID
        fieldY += spacing;
        editPanel.child(
            IKey.str("BQ绑定ID:")
                .asWidget()
                .left(8)
                .top(fieldY + 2));
        editPanel.child(
            new TextFieldWidget().value(new StringValue.Dynamic(() -> editBqQuestId, val -> editBqQuestId = val))
                .setMaxLength(60)
                .left(labelWidth)
                .top(fieldY)
                .size(fieldWidth, fieldHeight));

        // --- 保存 / 取消按钮 ---
        editPanel.child(
            new ButtonWidget<>().size(50, 16)
                .left(30)
                .bottom(8)
                .overlay(IKey.str("保存"))
                .onMouseTapped(mouse -> {
                    saveTradeEdit();
                    if (editPanelHandler != null) {
                        editPanelHandler.closePanel();
                    }
                    return true;
                }));
        editPanel.child(
            new ButtonWidget<>().size(50, 16)
                .right(30)
                .bottom(8)
                .overlay(IKey.str("取消"))
                .onMouseTapped(mouse -> {
                    if (editPanelHandler != null) {
                        editPanelHandler.closePanel();
                    }
                    return true;
                }));

        return editPanel;
    }

    /**
     * 保存交易编辑（客户端 → 服务端）
     * <p>
     * 将编辑面板的物品缓冲区内容和参数字段序列化为 JSON，
     * 通过 {@link NekoEditNetworkManager#sendSaveTrade} 发送到服务端。
     */
    private void saveTradeEdit() {
        if (editingDisplay == null) return;

        try {
            com.google.gson.JsonObject json = new com.google.gson.JsonObject();

            // 基础参数
            json.addProperty("tabId", editTabId);
            json.addProperty("orderId", editOrderId);
            json.addProperty("cooldown", editCooldown);
            json.addProperty("maxTrades", editMaxTrades);
            json.addProperty("bqQuestId", editBqQuestId);
            json.addProperty("currencyType", editCurrencyType);
            json.addProperty("currencyAmount", editCurrencyAmount);

            // 需求物品（slot 0-3）
            com.google.gson.JsonArray fromItems = new com.google.gson.JsonArray();
            for (int i = 0; i < 4; i++) {
                ItemStack stack = editItemHandler.getStackInSlot(i);
                if (stack != null) {
                    fromItems.add(itemStackToEditJson(stack));
                }
            }
            json.add("fromItems", fromItems);

            // 产物物品（slot 4-7）
            com.google.gson.JsonArray toItems = new com.google.gson.JsonArray();
            for (int i = 4; i < 8; i++) {
                ItemStack stack = editItemHandler.getStackInSlot(i);
                if (stack != null) {
                    toItems.add(itemStackToEditJson(stack));
                }
            }
            json.add("toItems", toItems);

            // 发送到服务端
            com.miaokatze.gtit.trade.v2.NekoEditNetworkManager.sendSaveTrade(
                editingDisplay.getGroupId()
                    .toString(),
                editingDisplay.getTradeIndex(),
                json.toString());

        } catch (Exception e) {
            GTInterestingThing.LOG.error("[NekoEdit] 保存交易编辑失败", e);
        }
    }

    /**
     * 将 ItemStack 序列化为编辑用 JSON（含 NBT）
     * <p>
     * 编辑面板的 PhantomItemSlot 可能放入带 NBT 的物品，
     * NBT 以 Base64 编码随 JSON 发送到服务端保存。
     *
     * @param stack 物品堆
     * @return JSON 对象 {item, meta, amount, nbtBase64?}
     */
    private static com.google.gson.JsonObject itemStackToEditJson(ItemStack stack) {
        com.google.gson.JsonObject item = new com.google.gson.JsonObject();
        item.addProperty("item", net.minecraft.item.Item.itemRegistry.getNameForObject(stack.getItem()));
        item.addProperty("meta", stack.getItemDamage());
        item.addProperty("amount", stack.stackSize);
        if (stack.hasTagCompound() && stack.getTagCompound() != null) {
            item.addProperty("nbtBase64", com.miaokatze.gtit.util.NbtBase64Util.nbtToBase64(stack.getTagCompound()));
        }
        return item;
    }

    // ==================== 编辑模式：签到编辑（v1.7.0 目标 4） ====================

    /**
     * 创建签到页编辑模式回调（供 {@link SignInCalendarGui#createSignInPage} 使用）
     * <p>
     * isEditMode 读取 {@link #editModeSync}（双端安全：服务端构建时返回服务端权威值，
     * 按钮交互仅在客户端触发）。
     *
     * @return 签到编辑回调实例
     */
    private SignInCalendarGui.SignInEditCallback createSignInEditCallback() {
        return new SignInCalendarGui.SignInEditCallback() {

            @Override
            public boolean isEditMode() {
                return isEditModeActive();
            }

            @Override
            public void onEditTierRequested(com.miaokatze.gtit.signin.SignInRewardTier tier) {
                openSignInTierEditor(tier);
            }

            @Override
            public void onEditGlobalRequested() {
                openSignInGlobalEditor();
            }
        };
    }

    /**
     * 打开签到阶梯编辑面板（客户端，编辑模式下点击阶梯宝箱触发）
     * <p>
     * 从本地 {@code DailySignInConfig} 填充货币字段（预览口径，与签到页展示一致），
     * 并通过 {@link #editSignInTargetSync} 通知服务端把阶梯物品奖励加载到编辑缓冲区
     * （PhantomItemSlot 自动同步回客户端显示）。
     *
     * @param tier 被点击的阶梯奖励
     */
    private void openSignInTierEditor(com.miaokatze.gtit.signin.SignInRewardTier tier) {
        if (tier == null) return;
        // 阶梯模式：记录目标天数并填充货币字段
        editSignInTierDays = tier.getRequiredDays();
        editSignInCurrency = tier.getCurrencyId() != null ? tier.getCurrencyId() : "neko";
        editSignInAmount = tier.getCurrencyAmount();
        // 通知服务端加载该阶梯的物品奖励到编辑缓冲区
        if (editSignInTargetSync != null) {
            editSignInTargetSync.setValue("tier:" + editSignInTierDays);
        }
        if (signInEditPanelHandler != null) {
            signInEditPanelHandler.openPanel();
        }
    }

    /**
     * 打开签到全局配置编辑面板（客户端，编辑模式下点击「全局配置」按钮触发）
     * <p>
     * 从本地 {@code DailySignInConfig} 读取当前基础奖励与递增系数填充字段。
     * 全局模式无物品奖励，清空编辑缓冲区避免残留。
     */
    private void openSignInGlobalEditor() {
        editSignInTierDays = -1; // -1 = 全局配置模式
        editSignInBaseReward = com.miaokatze.gtit.signin.DailySignInConfig.calculateBaseReward(1);
        // 递增系数无 getter，经 calculateBaseReward(2) 反推：reward(2) = base + floor(increment)
        // 直接读取不便，故以字符串字段由用户确认/修改；初值经反推填充
        int base = editSignInBaseReward;
        int next = com.miaokatze.gtit.signin.DailySignInConfig.calculateBaseReward(2);
        editSignInIncrement = String.valueOf(Math.max(0, next - base));
        // 全局模式无物品槽：清空缓冲区（双端一致，客户端清空后经 PhantomItemSlot 同步到服务端）
        editSignInItemHandler.setStackInSlot(0, null);
        if (signInEditPanelHandler != null) {
            signInEditPanelHandler.openPanel();
        }
    }

    /**
     * 服务端：加载签到阶梯物品奖励到编辑缓冲区
     * <p>
     * 解析 "tier:&lt;days&gt;" 目标标识，从 {@code DailySignInConfig} 查找阶梯，
     * 有物品奖励则构建 ItemStack 放入 slot 0，否则清空。
     *
     * @param target "tier:&lt;days&gt;" 格式的目标标识
     */
    private void loadSignInTierIntoEditBuffer(String target) {
        try {
            if (!target.startsWith("tier:")) return;
            int days = Integer.parseInt(target.substring("tier:".length()));
            editSignInItemHandler.setStackInSlot(0, null);
            for (com.miaokatze.gtit.signin.SignInRewardTier tier : com.miaokatze.gtit.signin.DailySignInConfig
                .getRewardTiers()) {
                if (tier.getRequiredDays() != days) continue;
                if (tier.hasItemReward()) {
                    String[] parts = tier.getItemRewardId()
                        .split(":");
                    if (parts.length == 2) {
                        net.minecraft.item.Item itemObj = cpw.mods.fml.common.registry.GameRegistry
                            .findItem(parts[0], parts[1]);
                        if (itemObj != null) {
                            editSignInItemHandler.setStackInSlot(
                                0,
                                new ItemStack(
                                    itemObj,
                                    Math.max(1, tier.getItemRewardAmount()),
                                    tier.getItemRewardMeta()));
                        }
                    }
                }
                break;
            }
            GTInterestingThing.LOG.info("[NekoEdit] 已加载签到阶梯到编辑缓冲区: {}", target);
        } catch (Exception e) {
            GTInterestingThing.LOG.error("[NekoEdit] 加载签到阶梯到编辑缓冲区失败: {}", target, e);
        }
    }

    /**
     * 构建签到编辑面板
     * <p>
     * 单面板双模式（按 {@link #editSignInTierDays} 切换可见区）：
     * <ul>
     * <li>阶梯模式（&gt;0）：货币类型/数量 + 物品奖励 PhantomItemSlot</li>
     * <li>全局模式（-1）：每日基础奖励 + 连续递增系数</li>
     * </ul>
     *
     * @param sm 面板同步管理器
     * @return 编辑面板
     */
    private ModularPanel buildSignInEditPanel(PanelSyncManager sm) {
        ModularPanel editPanel = new ModularPanel("nekoV2SignInEdit");
        editPanel.size(200, 130);

        // 标题（随模式切换）
        editPanel.child(
            new TextWidget<>(
                IKey.dynamic(
                    () -> editSignInTierDays > 0 ? EnumChatFormatting.GOLD + "编辑签到阶梯（连续 " + editSignInTierDays + " 天）"
                        : EnumChatFormatting.GOLD + "编辑签到全局配置")).top(5)
                            .horizontalCenter());

        int fieldY = 24;
        int fieldHeight = 14;
        int labelWidth = 65;
        int fieldWidth = 120;
        int spacing = 17;

        // ---- 阶梯模式区（货币类型/数量 + 物品奖励槽）----
        TextWidget<?> currencyLabel = new TextWidget<>(IKey.str("货币类型:"));
        currencyLabel.left(8)
            .top(fieldY + 2);
        currencyLabel.setEnabledIf(w -> editSignInTierDays > 0);
        editPanel.child(currencyLabel);

        TextFieldWidget currencyField = new TextFieldWidget()
            .value(new StringValue.Dynamic(() -> editSignInCurrency, val -> editSignInCurrency = val))
            .setMaxLength(30);
        currencyField.left(labelWidth)
            .top(fieldY)
            .size(fieldWidth, fieldHeight);
        currencyField.setEnabledIf(w -> editSignInTierDays > 0);
        editPanel.child(currencyField);

        fieldY += spacing;
        TextWidget<?> amountLabel = new TextWidget<>(IKey.str("货币数量:"));
        amountLabel.left(8)
            .top(fieldY + 2);
        amountLabel.setEnabledIf(w -> editSignInTierDays > 0);
        editPanel.child(amountLabel);

        TextFieldWidget amountField = new TextFieldWidget()
            .value(new StringValue.Dynamic(() -> String.valueOf(editSignInAmount), val -> {
                try {
                    editSignInAmount = Integer.parseInt(val);
                } catch (NumberFormatException ignored) {}
            }))
            .setNumbers(0, Integer.MAX_VALUE);
        amountField.left(labelWidth)
            .top(fieldY)
            .size(fieldWidth, fieldHeight);
        amountField.setEnabledIf(w -> editSignInTierDays > 0);
        editPanel.child(amountField);

        // 物品奖励槽（PhantomItemSlot 拖入配置；留空 = 无物品奖励）
        fieldY += spacing;
        TextWidget<?> itemLabel = new TextWidget<>(IKey.str("物品奖励:"));
        itemLabel.left(8)
            .top(fieldY + 2);
        itemLabel.setEnabledIf(w -> editSignInTierDays > 0);
        editPanel.child(itemLabel);

        PhantomItemSlot itemSlot = new PhantomItemSlot().slot(new ModularSlot(editSignInItemHandler, 0));
        itemSlot.left(labelWidth)
            .top(fieldY - 2);
        itemSlot.setEnabledIf(w -> editSignInTierDays > 0);
        editPanel.child(itemSlot);

        // ---- 全局模式区（基础奖励 + 递增系数）----
        TextWidget<?> baseLabel = new TextWidget<>(IKey.str("基础奖励:"));
        baseLabel.left(8)
            .top(26);
        baseLabel.setEnabledIf(w -> editSignInTierDays <= 0);
        editPanel.child(baseLabel);

        TextFieldWidget baseField = new TextFieldWidget()
            .value(new StringValue.Dynamic(() -> String.valueOf(editSignInBaseReward), val -> {
                try {
                    editSignInBaseReward = Integer.parseInt(val);
                } catch (NumberFormatException ignored) {}
            }))
            .setNumbers(0, Integer.MAX_VALUE);
        baseField.left(labelWidth)
            .top(24)
            .size(fieldWidth, fieldHeight);
        baseField.setEnabledIf(w -> editSignInTierDays <= 0);
        editPanel.child(baseField);

        TextWidget<?> incLabel = new TextWidget<>(IKey.str("递增系数:"));
        incLabel.left(8)
            .top(43);
        incLabel.setEnabledIf(w -> editSignInTierDays <= 0);
        editPanel.child(incLabel);

        TextFieldWidget incField = new TextFieldWidget()
            .value(new StringValue.Dynamic(() -> editSignInIncrement, val -> editSignInIncrement = val))
            .setMaxLength(12);
        incField.left(labelWidth)
            .top(41)
            .size(fieldWidth, fieldHeight);
        incField.setEnabledIf(w -> editSignInTierDays <= 0);
        editPanel.child(incField);

        // ---- 保存 / 取消按钮 ----
        editPanel.child(
            new ButtonWidget<>().size(50, 16)
                .left(30)
                .bottom(8)
                .overlay(IKey.str("保存"))
                .onMouseTapped(mouse -> {
                    saveSignInEdit();
                    if (signInEditPanelHandler != null) {
                        signInEditPanelHandler.closePanel();
                    }
                    return true;
                }));
        editPanel.child(
            new ButtonWidget<>().size(50, 16)
                .right(30)
                .bottom(8)
                .overlay(IKey.str("取消"))
                .onMouseTapped(mouse -> {
                    if (signInEditPanelHandler != null) {
                        signInEditPanelHandler.closePanel();
                    }
                    return true;
                }));

        return editPanel;
    }

    /**
     * 保存签到编辑（客户端 → 服务端）
     * <p>
     * 阶梯模式序列化 {@code {currency, amount, item, itemAmount, itemMeta}}（物品取自 PhantomItemSlot，
     * 无物品发空串）；全局模式序列化 {@code {baseReward, consecutiveIncrement}}。
     * 经 {@link com.miaokatze.gtit.trade.v2.NekoEditNetworkManager#sendSaveSignInReward} 发送。
     */
    private void saveSignInEdit() {
        try {
            com.google.gson.JsonObject json = new com.google.gson.JsonObject();
            if (editSignInTierDays > 0) {
                // 阶梯模式：货币 + 可选物品奖励
                json.addProperty("currency", editSignInCurrency);
                json.addProperty("amount", editSignInAmount);
                ItemStack stack = editSignInItemHandler.getStackInSlot(0);
                if (stack != null && stack.getItem() != null) {
                    json.addProperty("item", net.minecraft.item.Item.itemRegistry.getNameForObject(stack.getItem()));
                    json.addProperty("itemAmount", stack.stackSize);
                    json.addProperty("itemMeta", stack.getItemDamage());
                } else {
                    json.addProperty("item", "");
                    json.addProperty("itemAmount", 0);
                    json.addProperty("itemMeta", 0);
                }
                com.miaokatze.gtit.trade.v2.NekoEditNetworkManager
                    .sendSaveSignInReward("tier:" + editSignInTierDays, json.toString());
            } else {
                // 全局模式：基础奖励 + 递增系数（字符串解析，非法回退 0）
                double increment;
                try {
                    increment = Double.parseDouble(editSignInIncrement);
                } catch (NumberFormatException e) {
                    increment = 0.0;
                }
                json.addProperty("baseReward", editSignInBaseReward);
                json.addProperty("consecutiveIncrement", increment);
                com.miaokatze.gtit.trade.v2.NekoEditNetworkManager.sendSaveSignInReward("global", json.toString());
            }
        } catch (Exception e) {
            GTInterestingThing.LOG.error("[NekoEdit] 保存签到编辑失败", e);
        }
    }

    // ==================== 编辑模式：抽奖编辑（v1.7.0 目标 4） ====================

    /**
     * 创建抽奖页编辑模式回调（供 {@link LotteryGui#createLotteryPage} 使用）
     * <p>
     * isEditMode 读取 {@link #editModeSync}（双端安全：服务端构建时返回服务端权威值，
     * 轮盘点击交互仅在客户端触发）。
     *
     * @return 抽奖编辑回调实例
     */
    private LotteryGui.LotteryEditCallback createLotteryEditCallback() {
        return new LotteryGui.LotteryEditCallback() {

            @Override
            public boolean isEditMode() {
                return isEditModeActive();
            }

            @Override
            public void onEditEntryRequested(LotteryClientData.PoolSummary pool, LotteryEntry entry, int slotIndex) {
                openLotteryEntryEditor(pool, entry, slotIndex);
            }
        };
    }

    /**
     * 打开抽奖条目编辑面板（客户端，编辑模式下点击轮盘槽位触发）
     * <p>
     * 数值字段（货币/数量区间/权重/稀有度）从客户端缓存 {@link LotteryClientData} 的条目
     * 直接填充（与轮盘展示同源）；物品奖品经 {@link #editLotteryTargetSync} 通知服务端
     * 从权威配置加载到编辑缓冲区（PhantomItemSlot 自动同步回客户端显示，含 NBT）。
     *
     * @param pool      条目所属卡池摘要
     * @param entry     被点击的抽奖条目
     * @param slotIndex 轮盘槽位序号（仅日志/展示用，定位靠 poolId+entryId）
     */
    private void openLotteryEntryEditor(LotteryClientData.PoolSummary pool, LotteryEntry entry, int slotIndex) {
        if (pool == null || entry == null || entry.getId() == null) return;
        // 记录条目标识（保存时原样发回服务端定位）
        editLotteryEntryKey = pool.id + ":" + entry.getId();
        // 数值字段从客户端缓存条目填充
        editLotteryCurrency = entry.getNekoCurrencyId() != null ? entry.getNekoCurrencyId() : "";
        editLotteryMinAmount = entry.getMinAmount();
        editLotteryMaxAmount = entry.getMaxAmount();
        editLotteryWeight = entry.getWeight();
        editLotteryRarity = entry.getRarity() != null ? entry.getRarity()
            .name() : "COMMON";
        // 通知服务端加载该条目的物品奖品到编辑缓冲区
        if (editLotteryTargetSync != null) {
            editLotteryTargetSync.setValue(editLotteryEntryKey);
        }
        if (lotteryEditPanelHandler != null) {
            lotteryEditPanelHandler.openPanel();
        }
    }

    /**
     * 服务端：加载抽奖条目物品奖品到编辑缓冲区
     * <p>
     * 解析 "&lt;poolId&gt;:&lt;entryId&gt;" 目标标识，从 {@code LotteryManager}（服务端权威配置）
     * 查找条目；物品奖品构建 ItemStack（数量固定 1，含 NBT）放入 slot 0，
     * 货币奖品或查找失败则清空。
     *
     * @param target "&lt;poolId&gt;:&lt;entryId&gt;" 格式的目标标识
     */
    private void loadLotteryEntryIntoEditBuffer(String target) {
        try {
            int sep = target.indexOf(':');
            if (sep <= 0 || sep >= target.length() - 1) return;
            String poolId = target.substring(0, sep);
            String entryId = target.substring(sep + 1);
            editLotteryItemHandler.setStackInSlot(0, null);
            com.miaokatze.gtit.lottery.LotteryPool pool = com.miaokatze.gtit.lottery.LotteryManager.INSTANCE
                .getPool(poolId);
            if (pool == null) return;
            LotteryEntry entry = pool.getEntryById(entryId);
            if (entry == null || entry.isNekoPrize()) return;
            // 数量固定 1（展示用）；实际出货数量由 minAmount/maxAmount 字段决定
            ItemStack stack = entry.toItemStack(1);
            if (stack != null) {
                editLotteryItemHandler.setStackInSlot(0, stack);
            }
            GTInterestingThing.LOG.info("[NekoEdit] 已加载抽奖条目到编辑缓冲区: {}", target);
        } catch (Exception e) {
            GTInterestingThing.LOG.error("[NekoEdit] 加载抽奖条目到编辑缓冲区失败: {}", target, e);
        }
    }

    /**
     * 构建抽奖条目编辑面板
     * <p>
     * 字段布局：货币 ID（非空 = 货币奖品）→ 物品 PhantomItemSlot（货币 ID 留空时生效）
     * → 最小/最大数量 → 权重 → 稀有度（点击循环切换）。
     * 保存时经 {@link com.miaokatze.gtit.trade.v2.NekoEditNetworkManager#sendSaveLotteryEntry}
     * 发送 JSON 到服务端（{@code NekoEditActionHandler#saveLotteryEntry} 落盘 + 热重载 +
     * 推送 {@code LotterySyncPacket} 刷新轮盘）。
     *
     * @param sm 面板同步管理器
     * @return 编辑面板
     */
    private ModularPanel buildLotteryEditPanel(PanelSyncManager sm) {
        ModularPanel editPanel = new ModularPanel("nekoV2LotteryEdit");
        editPanel.size(200, 160);

        // 标题（显示条目标识 "<poolId>:<entryId>"）
        editPanel.child(
            new TextWidget<>(IKey.dynamic(() -> EnumChatFormatting.GOLD + "编辑抽奖条目（" + editLotteryEntryKey + "）")).top(5)
                .horizontalCenter());

        int fieldY = 24;
        int fieldHeight = 14;
        int labelWidth = 65;
        int fieldWidth = 120;

        // ---- 货币 ID（非空 = 货币奖品，保存时忽略物品槽）----
        editPanel.child(
            new TextWidget<>(IKey.str("货币ID:")).left(8)
                .top(fieldY + 2));

        TextFieldWidget currencyField = new TextFieldWidget()
            .value(new StringValue.Dynamic(() -> editLotteryCurrency, val -> editLotteryCurrency = val.trim()))
            .setMaxLength(30);
        currencyField.left(labelWidth)
            .top(fieldY)
            .size(fieldWidth, fieldHeight);
        currencyField.tooltipBuilder(t -> {
            t.addLine(IKey.str("货币 ID（如 neko / shimmeringNeko）"));
            t.addLine(IKey.str(EnumChatFormatting.YELLOW + "非空 = 货币奖品，保存时忽略下方物品"));
            t.addLine(IKey.str(EnumChatFormatting.GRAY + "留空 = 物品奖品（需放入物品）"));
        });
        currencyField.tooltipAutoUpdate(true);
        editPanel.child(currencyField);

        // ---- 物品奖品（PhantomItemSlot 拖入配置，支持 NBT）----
        fieldY += 17;
        editPanel.child(
            new TextWidget<>(IKey.str("物品:")).left(8)
                .top(fieldY + 2));

        PhantomItemSlot itemSlot = new PhantomItemSlot().slot(new ModularSlot(editLotteryItemHandler, 0));
        itemSlot.left(labelWidth)
            .top(fieldY - 2);
        itemSlot.tooltipBuilder(t -> {
            t.addLine(IKey.str("拖入物品作为奖品（支持 NBT）"));
            t.addLine(IKey.str(EnumChatFormatting.GRAY + "货币 ID 非空时本项被忽略"));
        });
        itemSlot.tooltipAutoUpdate(true);
        editPanel.child(itemSlot);

        // 物品槽旁状态提示（随货币 ID 是否填写动态切换）
        editPanel.child(
            new TextWidget<>(
                IKey.dynamic(
                    () -> editLotteryCurrency.isEmpty() ? EnumChatFormatting.GREEN + "物品奖品"
                        : EnumChatFormatting.YELLOW + "已忽略（货币奖品）")).left(labelWidth + 22)
                            .top(fieldY + 2));

        // ---- 最小数量 ----
        fieldY += 21; // 物品槽高 18，多留间距
        editPanel.child(
            new TextWidget<>(IKey.str("最小数量:")).left(8)
                .top(fieldY + 2));

        TextFieldWidget minField = new TextFieldWidget()
            .value(new StringValue.Dynamic(() -> String.valueOf(editLotteryMinAmount), val -> {
                try {
                    editLotteryMinAmount = Integer.parseInt(val);
                } catch (NumberFormatException ignored) {}
            }))
            .setNumbers(0, Integer.MAX_VALUE);
        minField.left(labelWidth)
            .top(fieldY)
            .size(fieldWidth, fieldHeight);
        editPanel.child(minField);

        // ---- 最大数量 ----
        fieldY += 17;
        editPanel.child(
            new TextWidget<>(IKey.str("最大数量:")).left(8)
                .top(fieldY + 2));

        TextFieldWidget maxField = new TextFieldWidget()
            .value(new StringValue.Dynamic(() -> String.valueOf(editLotteryMaxAmount), val -> {
                try {
                    editLotteryMaxAmount = Integer.parseInt(val);
                } catch (NumberFormatException ignored) {}
            }))
            .setNumbers(0, Integer.MAX_VALUE);
        maxField.left(labelWidth)
            .top(fieldY)
            .size(fieldWidth, fieldHeight);
        editPanel.child(maxField);

        // ---- 权重 ----
        fieldY += 17;
        editPanel.child(
            new TextWidget<>(IKey.str("权重:")).left(8)
                .top(fieldY + 2));

        TextFieldWidget weightField = new TextFieldWidget()
            .value(new StringValue.Dynamic(() -> String.valueOf(editLotteryWeight), val -> {
                try {
                    editLotteryWeight = Integer.parseInt(val);
                } catch (NumberFormatException ignored) {}
            }))
            .setNumbers(0, Integer.MAX_VALUE);
        weightField.left(labelWidth)
            .top(fieldY)
            .size(fieldWidth, fieldHeight);
        weightField.tooltipBuilder(t -> {
            t.addLine(IKey.str("抽取权重（相对值）"));
            t.addLine(IKey.str(EnumChatFormatting.GRAY + "0 = 永不中出"));
        });
        weightField.tooltipAutoUpdate(true);
        editPanel.child(weightField);

        // ---- 稀有度（点击循环切换：普通→稀有→史诗→传说）----
        fieldY += 17;
        editPanel.child(
            new TextWidget<>(IKey.str("稀有度:")).left(8)
                .top(fieldY + 2));

        ButtonWidget<?> rarityButton = new ButtonWidget<>().left(labelWidth)
            .top(fieldY)
            .size(fieldWidth, fieldHeight)
            .background(NekoGuiTextures.TEXT_FIELD_BACKGROUND)
            .overlay(IKey.dynamic(this::lotteryRarityDisplay))
            .tooltipBuilder(t -> t.addLine(IKey.str("点击循环切换稀有度（普通→稀有→史诗→传说）")))
            .onMouseTapped(mouse -> {
                cycleLotteryRarity();
                return true;
            });
        rarityButton.tooltipAutoUpdate(true);
        editPanel.child(rarityButton);

        // ---- 保存 / 取消按钮 ----
        editPanel.child(
            new ButtonWidget<>().size(50, 16)
                .left(30)
                .bottom(8)
                .overlay(IKey.str("保存"))
                .onMouseTapped(mouse -> {
                    saveLotteryEdit();
                    if (lotteryEditPanelHandler != null) {
                        lotteryEditPanelHandler.closePanel();
                    }
                    return true;
                }));
        editPanel.child(
            new ButtonWidget<>().size(50, 16)
                .right(30)
                .bottom(8)
                .overlay(IKey.str("取消"))
                .onMouseTapped(mouse -> {
                    if (lotteryEditPanelHandler != null) {
                        lotteryEditPanelHandler.closePanel();
                    }
                    return true;
                }));

        return editPanel;
    }

    /**
     * 稀有度循环切换（COMMON→RARE→EPIC→LEGENDARY→COMMON）
     */
    private void cycleLotteryRarity() {
        LotteryRarity[] values = LotteryRarity.values();
        LotteryRarity current = LotteryRarity.fromString(editLotteryRarity);
        editLotteryRarity = values[(current.ordinal() + 1) % values.length].name();
    }

    /**
     * 稀有度按钮显示文本（带稀有度颜色）
     */
    private String lotteryRarityDisplay() {
        LotteryRarity rarity = LotteryRarity.fromString(editLotteryRarity);
        return rarity.getColor() + rarity.getDisplayName() + EnumChatFormatting.GRAY + "（" + rarity.name() + "）";
    }

    /**
     * 保存抽奖编辑（客户端 → 服务端）
     * <p>
     * 序列化 {@code {nekoCurrencyId, item, meta, nbtBase64?, minAmount, maxAmount, weight, rarity}}
     * （物品取自 PhantomItemSlot，物品 ID 无法解析时发空串交由服务端按货币/校验处理）。
     * 经 {@link com.miaokatze.gtit.trade.v2.NekoEditNetworkManager#sendSaveLotteryEntry} 发送。
     */
    private void saveLotteryEdit() {
        try {
            if (editLotteryEntryKey.isEmpty()) return;
            com.google.gson.JsonObject json = new com.google.gson.JsonObject();
            json.addProperty("nekoCurrencyId", editLotteryCurrency);
            // 物品奖品字段：取自 PhantomItemSlot（货币 ID 非空时服务端忽略并清空）
            ItemStack stack = editLotteryItemHandler.getStackInSlot(0);
            if (stack != null && stack.getItem() != null) {
                com.google.gson.JsonObject itemJson = itemStackToEditJson(stack);
                json.addProperty(
                    "item",
                    itemJson.get("item")
                        .getAsString());
                json.addProperty(
                    "meta",
                    itemJson.get("meta")
                        .getAsInt());
                if (itemJson.has("nbtBase64")) {
                    json.addProperty(
                        "nbtBase64",
                        itemJson.get("nbtBase64")
                            .getAsString());
                }
            } else {
                json.addProperty("item", "");
                json.addProperty("meta", 0);
            }
            // 数量区间（出货数量在 [min, max] 均匀随机；服务端会再做下限钳制）
            json.addProperty("minAmount", editLotteryMinAmount);
            json.addProperty("maxAmount", editLotteryMaxAmount);
            json.addProperty("weight", editLotteryWeight);
            json.addProperty("rarity", editLotteryRarity);
            com.miaokatze.gtit.trade.v2.NekoEditNetworkManager
                .sendSaveLotteryEntry(editLotteryEntryKey, json.toString());
        } catch (Exception e) {
            GTInterestingThing.LOG.error("[NekoEdit] 保存抽奖编辑失败", e);
        }
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
     * v1.7.0 创建主标签列（贸易/签到/抽奖/邮件）
     * <p>
     * 位于贸易分类标签列的更左边（left(-57)，贸易分类列在 left(-29)），
     * 使用 {@link NekoMainTabButton} + {@link #mainTabController} 切换主内容面板。
     * <p>
     * 切换主标签时同时通过 {@link #mainTabSync} 同步到服务端，
     * 便于服务端感知当前玩家查看的主标签（后续用于编辑模式权限控制）。
     *
     * @return 主标签列 Widget
     */
    private IWidget createMainTabColumn() {
        Flow mainTabColumn = Flow.column()
            .coverChildren()
            .left(-57)
            .top(40)
            .childPadding(2);

        // 主标签定义：index → (图标, 名称)
        Object[][] mainTabs = new Object[][] { { MAIN_TAB_TRADE, NekoGuiTextures.MAIN_TAB_TRADE, "贸易" },
            { MAIN_TAB_SIGNIN, NekoGuiTextures.MAIN_TAB_SIGNIN, "签到" },
            { MAIN_TAB_LOTTERY, NekoGuiTextures.MAIN_TAB_LOTTERY, "抽奖" },
            { MAIN_TAB_MAIL, NekoGuiTextures.MAIN_TAB_MAIL, "邮件" }, };

        for (Object[] tabDef : mainTabs) {
            final int index = (Integer) tabDef[0];
            final com.cleanroommc.modularui.drawable.UITexture icon = (com.cleanroommc.modularui.drawable.UITexture) tabDef[1];
            final String name = (String) tabDef[2];

            NekoMainTabButton tabButton = new NekoMainTabButton(index, mainTabController, icon);
            tabButton.tab(NekoGuiTextures.TAB_LEFT, -1);
            tabButton.tooltipBuilder(t -> { t.addLine(IKey.str(name)); });
            // 点击时同步主标签索引到服务端（使用 onSelected 钩子，避免与 PageButton.onMousePressed 冲突）
            tabButton.onSelected(() -> {
                if (mainTabSync != null) {
                    mainTabSync.setValue(index);
                }
            });

            mainTabColumn.child(tabButton);
        }

        return mainTabColumn.excludeAreaInRecipeViewer();
    }

    /**
     * 创建左侧标签列
     * <p>
     * 为每个交易分类创建一个 {@link NekoPageButtonV2} 按钮，
     * 使用物品图标作为标签页标识。
     * <p>
     * v1.7.0：仅在主标签为贸易时显示（{@link #mainTabId} == {@link #MAIN_TAB_TRADE}）。
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

        // v1.7.0：仅主标签为贸易时显示
        tabColumn.setEnabledIf(w -> mainTabId == MAIN_TAB_TRADE);

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
        // v1.6.24: volumeButton 改为赋值类字段（移除 final 局部变量），以便 build() 在 client 块外部注册 syncedPanel
        volumeButton = new ButtonWidget<>().size(14, 14)
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

        // v1.6.24: volumePanel 注册已移至 build() 的 client 块外部（与 VM 原版一致），
        // 确保服务端也注册同步通道，否则客户端 togglePanel() 静默失败

        // 2x2 网格：左上音量、右上显示模式、左下显示硬币、右下排序
        // 在 NEI/HEI 中排除 QoL 按钮列区域，避免配方查看器遮挡快捷按钮
        Grid qolGrid = new Grid().left(-33)
            .top(1)
            .minElementMargin(1, 1)
            .coverChildren()
            .grid(
                Arrays.asList(
                    Arrays.asList(volumeButton, displayModeButton),
                    Arrays.asList(showCoinsButton, sortModeButton)));
        // v1.7.0：仅主标签为贸易时显示（BGM 按钮在所有标签都可用，但保持简单——先只在贸易标签显示）
        qolGrid.setEnabledIf(w -> mainTabId == MAIN_TAB_TRADE);
        return qolGrid.excludeAreaInRecipeViewer();
    }

    /**
     * v1.7.0 创建主内容区（PagedWidget 切换贸易/签到/抽奖/邮件）
     * <p>
     * 通过 {@link #mainTabController} 控制页面切换：
     * <ul>
     * <li>页 0（贸易）：现有的贸易主列（标题、搜索、交易列表、钱包、结果、背包）</li>
     * <li>页 1（签到）：签到日历 GUI（v1.7.0 目标 D 实现，先占位）</li>
     * <li>页 2（抽奖）：抽奖轮盘 GUI（v1.7.0 目标 2 实现，先占位）</li>
     * <li>页 3（邮件）：邮件 GUI（v1.7.0 目标 3 实现，先占位）</li>
     * </ul>
     *
     * @param syncManager 面板同步管理器
     * @return 主内容区 PagedWidget
     */
    private IWidget createMainContentPagedWidget(PanelSyncManager syncManager) {
        PagedWidget<?> mainPaged = new PagedWidget<>().name("nekoV2MainPaged")
            .size(PANEL_WIDTH - 8, PANEL_HEIGHT - 8)
            .controller(mainTabController);

        // 页 0：贸易（现有 createMainColumn 的内容）
        mainPaged.addPage(createTradeMainColumn(syncManager));

        // 页 1：签到（任务 D 实现；v1.7.0 目标 4 传入编辑模式回调）
        mainPaged.addPage(SignInCalendarGui.createSignInPage(createSignInEditCallback()));

        // 页 2：抽奖（v1.7.1 目标 2 实现，轮盘 GUI；出货槽定位依赖机器坐标；
        // v1.7.0 目标 4 传入编辑模式回调：编辑模式下点击轮盘槽位弹出条目编辑面板）
        mainPaged.addPage(LotteryGui.createLotteryPage(baseMetaTileEntity, createLotteryEditCallback()));

        // 页 3：邮件（v1.7.2 目标 3 实现，列表+详情+附件领取）
        mainPaged.addPage(MailGui.createMailPage());

        return mainPaged;
    }

    /**
     * 创建贸易主内容列（v1.7.0 前为 createMainColumn）
     * <p>
     * 布局从上到下：标题、搜索栏、交易列表（PagedWidget）、猫猫币显示、交易结果、玩家背包。
     * <p>
     * 输入槽和输出槽已迁移到 {@link #createIOColumn()}（与 V1 的 IO 列布局一致），
     * 主列不再承载 IO 元素，以保持 UI 与 V1 一致。
     *
     * @param syncManager 面板同步管理器
     * @return 主列 Widget
     */
    private IWidget createTradeMainColumn(PanelSyncManager syncManager) {
        Flow mainColumn = Flow.column()
            .width(PANEL_WIDTH - 8);

        // --- 标题（编辑模式下附加红色「编辑模式」标识，v1.7.0 目标 4 视觉标识）---
        mainColumn.child(
            IKey.dynamic(
                () -> isEditModeActive() ? EnumChatFormatting.DARK_GRAY + "猫猫售货机 " + EnumChatFormatting.RED + "[编辑模式]"
                    : EnumChatFormatting.DARK_GRAY + "猫猫售货机")
                .asWidget()
                .height(12)
                .fullWidth()
                .marginBottom(2));

        // --- 团队钱包标识 + ME 输出模式切换按钮（同一行平行显示）---
        // [团队钱包]在左侧（仅 TEAM 模式时显示内容），[自动输入ME: 开/关]在右侧（仅 uplink 在线时显示）
        // 两者平行排列在标题下方同一行，互不影响显示
        mainColumn.child(
            Flow.row()
                .height(12)
                .fullWidth()
                .marginBottom(2)
                // [团队钱包]动态文本（仅 TEAM 模式时显示，否则折叠隐藏）
                .child(IKey.dynamic(() -> {
                    NekoWalletMode mode = getWalletMode();
                    if (mode == NekoWalletMode.TEAM) {
                        return EnumChatFormatting.AQUA + "[团队钱包]";
                    }
                    return "";
                })
                    .asWidget()
                    .height(10)
                    .left(0)
                    .setEnabledIf(w -> getWalletMode() == NekoWalletMode.TEAM))
                // [自动输入ME: 开/关]切换按钮（仅 uplink 在线时显示）
                .child(
                    new ButtonWidget<>().size(120, 12)
                        .overlay(IKey.dynamic(() -> {
                            boolean mode = meOutputModeSync != null && meOutputModeSync.getValue();
                            return mode ? EnumChatFormatting.LIGHT_PURPLE + "[自动输入ME: 开]"
                                : EnumChatFormatting.GRAY + "[自动输入ME: 关]";
                        }))
                        .tooltipBuilder(t -> {
                            t.addLine(IKey.str("切换产出路径：本地出货槽 ↔ ME 网络"));
                            if (hasUplinkSync != null && hasUplinkSync.getValue()) {
                                t.addLine(IKey.str(EnumChatFormatting.GRAY + "点击弹出确认对话框"));
                            }
                        })
                        .tooltipAutoUpdate(true)
                        .onMouseTapped(mouse -> {
                            if (meModeConfirmDialog != null && meModeConfirmPanel != null) {
                                boolean currentMode = meOutputModeSync != null && meOutputModeSync.getValue();
                                String message = currentMode ? "确认关闭 ME 自动输入模式？新产出将走本地出货槽"
                                    : "确认开启 ME 自动输入模式？新产出将通过 Uplink 发送到 ME 网络";
                                meModeConfirmDialog.setParams(message, () -> {
                                    if (meOutputModeSync != null) {
                                        meOutputModeSync.setValue(!currentMode);
                                    }
                                });
                                meModeConfirmPanel.openPanel();
                            }
                            return true;
                        })
                        .right(0)
                        .setEnabledIf(w -> hasUplinkSync != null && hasUplinkSync.getValue())));

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

            // 注：volumePanel 已在 build() 的 client 块外部创建（v1.6.24 修复，与 VM 原版一致）
        }

        // --- 猫猫币余额显示行 ---
        mainColumn.child(createCoinDisplayRow(syncManager));

        // --- 交易结果消息（v1.6.23：背包栏已底部锚定，消息高度不影响布局）---
        mainColumn.child(
            IKey.dynamic(() -> tradeResultMessage.isEmpty() ? "" : EnumChatFormatting.YELLOW + tradeResultMessage)
                .asWidget()
                .height(12)
                .fullWidth()
                .marginBottom(2)
                .setEnabledIf(w -> !tradeResultMessage.isEmpty()));

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
                    // 编辑模式：绕过结构完整性检查，显示所有有条目的 Widget
                    if (isEditModeActive()) {
                        return getDisplayType() == NekoDisplayType.TILE && widget.getDisplay() != null;
                    }
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
                    // 编辑模式：绕过结构完整性检查，显示所有有条目的 Widget
                    if (isEditModeActive()) {
                        return getDisplayType() == NekoDisplayType.LIST && widget.getDisplay() != null;
                    }
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
        // v1.6.22：改造为单行布局，ME 导入按钮移入 NekoCoinDisplayV2 与弹出按钮同行
        Flow column = Flow.column()
            .fullWidth()
            .marginBottom(2);

        // === 余额行（含 ME 导入按钮）===
        Flow row = Flow.row()
            .height(22)
            .fullWidth()
            .marginBottom(2);

        int offset = 0;
        for (String currencyId : NekoCurrencyRegistrar.getNekoCurrencyIds()) {
            final String cid = currencyId;
            String displayName = NekoCurrencyRegistrar.getDisplayName(currencyId);
            NekoCoinDisplayV2 coinDisplay = new NekoCoinDisplayV2(syncManager, currencyId, displayName);
            // 注入 ME 余额查询器，使弹出按钮 tooltip 显示 ME 网络余额
            coinDisplay.setMeAmountSupplier(() -> meCoinAmounts.getOrDefault(cid, 0));
            // v1.6.22：注入 ME 导入配置（替代原 importRow 独立行）
            coinDisplay.setMeImportConfig(
                () -> meCoinAmounts.getOrDefault(cid, 0),
                () -> hasUplinkSync != null && hasUplinkSync.getValue(),
                () -> {
                    BooleanSyncValue sync = nekoImportMeCoinSyncs.get(cid);
                    if (sync != null) {
                        sync.setValue(true);
                    }
                });
            coinDisplay.left(offset);
            row.child(coinDisplay);
            offset += 79; // 组件宽度 76 + 间距 3 = 79
        }

        // 根据货币显示开关控制余额行的显示/隐藏
        row.setEnabledIf(w -> showCoins)
            .collapseDisabledChild(true);
        column.child(row);

        return column;
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

        // v1.7.0：仅主标签为贸易时显示 IO 列
        ioColumn.setEnabledIf(w -> mainTabId == MAIN_TAB_TRADE);

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
        // v1.6.23: ME 传输粒子动画 Widget（围绕出货槽中的物品渲染粒子）
        // 传入 fallingFactory 供粒子定位槽位坐标；点击穿透到出货槽（不拦截鼠标）
        // v1.7.5 修复：仅客户端创建——NekoMeTransferParticleWidget 带 @SideOnly(Side.CLIENT)，
        // 专用服务器类被剥离，双端执行会 NoClassDefFoundError（createIOColumn 双端调用）。
        if (syncManagerRef.isClient()) {
            NekoMeTransferParticleWidget particleWidget = new NekoMeTransferParticleWidget(
                clientMeTransferQueue,
                fallingFactory).onRetrieve(() -> {
                    if (retrieveMeItemSync != null) {
                        retrieveMeItemSync.setValue(true);
                    }
                });
            particleWidget.fullWidth()
                .fullHeight();
            // v1.6.24: 移除 setEnabledIf（ModularUI2 此版本中 setEnabledIf(false) 会阻止 widget 的 draw() 被调用，
            // 导致 clientMeTransferQueue 同步到达后粒子仍不渲染）。draw() 方法内已有空队列守卫
            // (if (queueRef.isEmpty()) return;)，无需额外控制可见性。
            dispenserChute.child(particleWidget);
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

    // ==================== ME 传输队列解析（阶段 4） ====================

    /**
     * 解析服务端发来的 ME 传输队列字符串，更新客户端缓存
     * <p>
     * 格式：{@code creationTimeMs:stackSize:itemNBTBase64;creationTimeMs:stackSize:itemNBTBase64;...}
     * <p>
     * 客户端解析后存入 {@link #clientMeTransferQueue}，供粒子 Widget 渲染
     * （显示剩余传输时间和物品图标）。NBT 解码复用 {@link com.miaokatze.gtit.util.NbtBase64Util}。
     *
     * @param data 序列化字符串
     */
    private void parseMeTransferQueue(String data) {
        clientMeTransferQueue.clear();
        if (data == null || data.isEmpty()) {
            // v1.6.24 临时日志：确认空数据到达客户端
            System.out.println("[NekoParticle] parseMeTransferQueue: empty data");
            return;
        }
        try {
            String[] entries = data.split(";");
            // v1.6.24 临时日志：确认非空数据到达客户端
            System.out.println(
                "[NekoParticle] parseMeTransferQueue: dataLen=" + data.length() + ", entries=" + entries.length);
            for (String entryStr : entries) {
                // v1.6.23: 新格式 4 段 (creationTime:stackSize:slotIndex:base64)
                // 旧格式 3 段 (creationTime:stackSize:base64)，通过 split limit 4 兼容
                String[] parts = entryStr.split(":", 4);
                if (parts.length < 3) continue;
                long creationTime = Long.parseLong(parts[0]);
                int stackSize = Integer.parseInt(parts[1]);
                int slotIndex = -1; // 默认 -1（旧格式兼容）
                String base64Part;
                if (parts.length >= 4) {
                    // 新格式：parts[2]=slotIndex, parts[3]=base64
                    slotIndex = Integer.parseInt(parts[2]);
                    base64Part = parts[3];
                } else {
                    // 旧格式：parts[2]=base64
                    base64Part = parts[2];
                }
                if (base64Part.isEmpty()) continue;
                // base64 解码 NBT（复用项目工具类）
                net.minecraft.nbt.NBTTagCompound tag = com.miaokatze.gtit.util.NbtBase64Util.nbtFromBase64(base64Part);
                if (tag == null) continue;
                ItemStack stack = ItemStack.loadItemStackFromNBT(tag);
                if (stack != null && stack.stackSize > 0) {
                    // 同步 stackSize（base64 中已含，但显式设置以防解码差异）
                    stack.stackSize = stackSize;
                    clientMeTransferQueue
                        .add(new MTENekoVendingMachineV2.MeTransferEntry(stack, creationTime, slotIndex));
                }
            }
            // v1.6.24 临时日志：确认解析后队列大小
            System.out.println("[NekoParticle] parseMeTransferQueue: parsedQueueSize=" + clientMeTransferQueue.size());
        } catch (Exception e) {
            GTInterestingThing.LOG.error("[NekoVMV2] parseMeTransferQueue 解析失败", e);
        }
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
     * 弹出一组（64个）单种猫猫币
     * <p>
     * 与 {@link #doNekoEjectCoin} 类似，但仅弹出 1 组（64 个，不足则弹出全部）。
     * 由 Ctrl+点击弹出按钮触发（v1.6.22 新增）。
     *
     * @param currencyId 货币 ID
     * @param playerId   玩家 UUID
     */
    private void doNekoEjectCoinStack(String currencyId, UUID playerId) {
        // 客户端不执行服务端逻辑
        if (isClient() || baseMetaTileEntity == null || !baseMetaTileEntity.isActive()) {
            nekoEjectCoinStack.put(currencyId, false);
            return;
        }
        try {
            if (playerId == null) {
                nekoEjectCoinStack.put(currencyId, false);
                return;
            }
            NekoWallet wallet = NekoWalletManager.INSTANCE.getWallet(playerId);
            if (wallet == null || wallet.getCount(currencyId) <= 0) {
                nekoEjectCoinStack.put(currencyId, false);
                return;
            }

            // 仅弹出 1 组（64 个，不足则全部）
            int walletBalance = wallet.getCount(currencyId);
            int count = Math.min(walletBalance, 64);
            java.util.List<ItemStack> toDispense = new java.util.ArrayList<>();
            ItemStack stack = NekoCurrencyRegistrar.getItemStack(currencyId, count);
            if (stack != null) {
                toDispense.add(stack);
            }

            if (!toDispense.isEmpty()) {
                // 投放前检查输出槽容量
                int emptySlots = multiblock.getOutputEmptySlotCount();
                int queuedItems = multiblock.getOutputBufferSize();
                if (emptySlots - queuedItems <= 0) {
                    GTInterestingThing.LOG.warn("[NekoVMV2] doNekoEjectCoinStack 输出槽已满，取消弹出货币 {}", currencyId);
                    return;
                }
                multiblock.dispenseItemStacks(toDispense);
                // 扣减钱包余额（而非 resetCount）
                wallet.addCount(currencyId, -count);
                NekoWalletManager.INSTANCE.saveWallet(playerId);
                playCoinDropSound();
                // 通知余额同步值刷新
                IntSyncValue coinSync = coinAmountSyncs.get(currencyId);
                if (coinSync != null) {
                    coinSync.setValue(wallet.getCount(currencyId));
                }
                // 交易状态可能受影响，标记为脏
                tradeableStatusDirty = true;
                if (tradeableStatusSync != null) {
                    tradeableStatusSync.notifyUpdate();
                }
            }
        } catch (Throwable t) {
            GTInterestingThing.LOG.error("[NekoVMV2] doNekoEjectCoinStack 异常!", t);
        } finally {
            nekoEjectCoinStack.put(currencyId, false);
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
     * 从 ME 网络导入指定货币到玩家钱包（阶段 6）
     * <p>
     * 由 GUI 导入按钮触发（通过 nekoV2ImportMeCoin_ 同步值 C2S）。
     * 查询 ME 余额，提取全部，加到玩家钱包，并刷新货币余额同步值。
     *
     * @param currencyId 货币 ID（如 "neko"、"shimmeringNeko"）
     */
    private void doNekoImportMeCoin(String currencyId) {
        // 客户端不执行服务端逻辑（匹配 V1 的 isClient() 守卫）
        if (isClient()) {
            nekoImportMeCoin.put(currencyId, false);
            return;
        }
        try {
            UUID playerId = getPlayerId();
            if (playerId == null || multiblock == null || !multiblock.hasUplink()) {
                tradeResultMessage = "未连接 Uplink，无法从 ME 导入";
                if (tradeResultSync != null) tradeResultSync.setValue(tradeResultMessage);
                return;
            }
            // 查询 ME 余额
            int meAmount = multiblock.getUplinkCurrencyAmount(currencyId);
            if (meAmount <= 0) {
                tradeResultMessage = "ME 网络中无" + NekoCurrencyRegistrar.getDisplayName(currencyId);
                if (tradeResultSync != null) tradeResultSync.setValue(tradeResultMessage);
                return;
            }
            // 从 ME 提取（extractFromUplink 返回未满足的剩余数量）
            ItemStack coinStack = NekoCurrencyRegistrar.getItemStack(currencyId, meAmount);
            if (coinStack == null) {
                tradeResultMessage = "货币物品栈创建失败";
                if (tradeResultSync != null) tradeResultSync.setValue(tradeResultMessage);
                return;
            }
            int remain = multiblock.extractFromUplink(coinStack);
            int extracted = meAmount - remain;
            if (extracted > 0) {
                // 加到玩家钱包（使用 addCount，与 doNekoImportCoins 一致）
                NekoWallet wallet = NekoWalletManager.INSTANCE.getWallet(playerId);
                if (wallet != null) {
                    wallet.addCount(currencyId, extracted);
                    NekoWalletManager.INSTANCE.saveWallet(playerId);
                    // 刷新货币余额同步值，使余额显示立即更新
                    IntSyncValue coinSync = coinAmountSyncs.get(currencyId);
                    if (coinSync != null) {
                        coinSync.setValue(wallet.getCount(currencyId));
                    }
                    // 刷新 ME 货币余额同步值，使导入按钮显示立即更新
                    IntSyncValue meSync = meCoinAmountSyncs.get(currencyId);
                    if (meSync != null) {
                        meSync.notifyUpdate();
                    }
                    // 标记可交易状态为脏（钱包余额变化影响可交易性）
                    tradeableStatusDirty = true;
                    if (tradeableStatusSync != null) {
                        tradeableStatusSync.notifyUpdate();
                    }
                    tradeResultMessage = "从 ME 导入 " + extracted
                        + " 个"
                        + NekoCurrencyRegistrar.getDisplayName(currencyId);
                }
            } else {
                tradeResultMessage = "ME 提取失败";
            }
            if (tradeResultSync != null) tradeResultSync.setValue(tradeResultMessage);
        } catch (Throwable t) {
            GTInterestingThing.LOG.error("[NekoVMV2] doNekoImportMeCoin 异常!", t);
            tradeResultMessage = "ME 货币导入失败";
            if (tradeResultSync != null) tradeResultSync.setValue(tradeResultMessage);
        } finally {
            nekoImportMeCoin.put(currencyId, false);
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
                // 交易成功后冷却内已用次数发生变化，需同步团队缩放信息
                if (teamScaleSync != null) {
                    teamScaleSync.notifyUpdate();
                }
                if (tradeResultSync != null) {
                    tradeResultSync.notifyUpdate();
                }
                // 交易成功后物品可能进入 ME 传输队列，通知客户端刷新粒子动画
                if (meTransferQueueSync != null) {
                    meTransferQueueSync.notifyUpdate();
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

    // ==================== 团队缩放状态同步（S2C，用于 tooltip 冷却缩放展示） ====================

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
     * {@link #teamScaleMap}：groupId → [maxTrades, usedTrades]。
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

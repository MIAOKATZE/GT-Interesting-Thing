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
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.world.World;

import com.cleanroommc.modularui.api.IPanelHandler;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.api.widget.Interactable;
import com.cleanroommc.modularui.drawable.DynamicDrawable;
import com.cleanroommc.modularui.drawable.GuiTextures;
import com.cleanroommc.modularui.drawable.ItemDrawable;
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
import com.miaokatze.gtit.client.gui.NekoDraggableEditPanel;
import com.miaokatze.gtit.client.gui.NekoFallingItemSlotFactory;
import com.miaokatze.gtit.client.gui.NekoGuiTextures;
import com.miaokatze.gtit.client.gui.NekoMainTabButton;
import com.miaokatze.gtit.client.gui.NekoMeTransferParticleWidget;
import com.miaokatze.gtit.client.gui.NekoMusicTrack;
import com.miaokatze.gtit.client.gui.NekoPageButtonV2;
import com.miaokatze.gtit.client.gui.NekoPagedWidget;
import com.miaokatze.gtit.client.gui.NekoSearchBar;
import com.miaokatze.gtit.client.gui.NekoSortMode;
import com.miaokatze.gtit.client.gui.NekoSubTabButton;
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
import com.miaokatze.gtit.mail.MailClientData;
import com.miaokatze.gtit.mail.MailGui;
import com.miaokatze.gtit.main.GTInterestingThing;
import com.miaokatze.gtit.signin.RewardItem;
import com.miaokatze.gtit.signin.SignInCalendarGui;
import com.miaokatze.gtit.signin.SignInClientData;
import com.miaokatze.gtit.signin.SignInReward;
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
    /** v1.7.6 G1 签到「活跃」sub-page 分页控制器（客户端；G1 仅创建供标签按钮绑定，G2③ 建对应 PagedWidget 页面后接管切换） */
    private PagedWidget.Controller signInPageController;
    /** v1.7.6 G1 抽奖池 sub-page 分页控制器（客户端；G1 仅创建供标签按钮绑定，G2① 建对应 PagedWidget 页面后接管切换） */
    private PagedWidget.Controller lotteryPageController;
    /** v1.7.6 G1 邮件 sub-page 分页控制器（客户端；G1 仅创建供标签按钮绑定，G2② 建对应 PagedWidget 页面后接管切换） */
    private PagedWidget.Controller mailPageController;
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
    /** v1.7.8 B：输出槽 Widget 引用（输出槽仅有增量同步，服务端在 GUI 打开/交易成功时强制同步兜底） */
    private final List<ItemSlot> outputSlotRefs = new ArrayList<>();
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

    /** v1.7.7 G2① 当前打开的编辑覆盖层类型（NONE=未打开） */
    private enum EditOverlayType {
        NONE,
        TRADE,
        SIGNIN,
        /** v1.7.8 任务6：逐日覆盖编辑（每月签到日期格） */
        SIGNIN_DAY,
        ONLINE_TIER,
        LOTTERY,
        LOTTERY_POOL,
        PAGE,
        BLESSING
    }

    /** v1.7.7 G2① 当前打开的编辑覆盖层类型（客户端状态，控制覆盖层显隐） */
    private EditOverlayType currentEditOverlay = EditOverlayType.NONE;
    /** v1.7.7 G2① 编辑覆盖层根节点（全屏透明拦截层 + 各编辑面板） */
    private ParentWidget<?> editOverlayRoot;
    /** 当前正在编辑的交易显示数据（客户端，打开编辑面板时设置） */
    private NekoTradeItemDisplay editingDisplay;

    // --- 编辑面板本地状态（客户端，保存时序列化为 JSON 发送到服务端） ---
    // v1.7.6 G3② 货币解绑：原「猫猫币类型/数量」字段已删除——货币需求/产出统一由
    // 需求/产物格中的猫猫币物品条目表达（执行器实时识别分流），面板不再设独立货币输入框
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
    /** 编辑：是否严格匹配 NBT（v1.7.6 G3⑤，统一默认 false=仅按物品匹配） */
    private boolean editRecordNBT = false;
    /** 编辑：是否新建交易模式（v1.7.6 G3④，true=保存时走 createTrade，false=走 saveTrade） */
    private boolean editTradeIsNew = false;

    /** 编辑目标同步值特殊标记：新建交易模式（v1.7.6 G3④，服务端仅清空编辑缓冲区） */
    private static final String TRADE_TARGET_NEW = "@new";
    /** 编辑目标同步值（C2S：客户端设置 "groupId:tradeIndex" 或 {@link #TRADE_TARGET_NEW}，服务端据此加载交易数据到编辑缓冲区） */
    private StringSyncValue editTargetSync;
    /** 编辑物品缓冲区（双端共享：slot 0-15=需求物品两行，slot 16-31=产物物品两行，v1.7.6 G3① 8→32） */
    private final ItemStackHandler editItemHandler = new ItemStackHandler(32);
    /** 交易编辑 PhantomItemSlot 引用（服务端加载同一条目时强制回传槽位，避免客户端取消后的空缓存残留） */
    private final List<ItemSlot> editTradeSlotRefs = new ArrayList<>();

    // --- 标签页 page 编辑（v1.7.6 G3④：shift+点击 page 标签 / 列尾「+」新建 page） ---
    /** page 编辑目标同步值特殊标记：新建 page 模式（服务端仅清空编辑缓冲区） */
    private static final String PAGE_TARGET_NEW = "@new";
    /** page 编辑目标同步值（C2S：pageId 字符串或 {@link #PAGE_TARGET_NEW}，服务端据此加载图标到编辑缓冲区） */
    private StringSyncValue editPageTargetSync;
    /** page 编辑物品缓冲区（双端共享：slot 0=page 图标） */
    private final ItemStackHandler editPageItemHandler = new ItemStackHandler(1);
    /** page 编辑：目标 pageId（新建模式下为 -1，保存时由服务端分配 id≥4） */
    private int editPageId = -1;
    /** page 编辑：是否新建模式（true=保存时走 createPage，false=走 savePage） */
    private boolean editPageIsNew = false;
    /** page 编辑：显示名称 */
    private String editPageName = "";

    // --- 签到编辑（v1.7.8 任务5+6：连续/累计阶梯增删改 + 逐日覆盖 + 每月全局配置） ---
    /** 签到编辑目标同步值（C2S："tier:<days>"/"cumtier:<days>"，服务端据此加载阶梯物品奖励到编辑缓冲区） */
    private StringSyncValue editSignInTargetSync;
    /** 签到编辑物品缓冲区（双端共享：v1.7.8 任务6 由 1 槽扩为 4 槽，对应每奖励最多 4 个物品；SIGNIN 面板阶梯/每月模式共用） */
    private final ItemStackHandler editSignInItemHandler = new ItemStackHandler(4);
    /** 逐日覆盖编辑物品缓冲区（双端共享：4 槽；与 SIGNIN 面板缓冲区独立，避免同一 handler 槽位重复注册） */
    private final ItemStackHandler editSignInDayItemHandler = new ItemStackHandler(4);
    /** 签到编辑模式："tier"=连续阶梯 / "cumtier"=累计阶梯 / "monthly"=每月全局配置（逐日覆盖走 SIGNIN_DAY 覆盖层） */
    private String editSignInMode = "tier";
    /** 签到编辑：原阶梯天数（定位用；>0=编辑已有阶梯，-1=新增模式） */
    private int editSignInOriginalDays = -1;
    /** 签到编辑：当前编辑的阶梯天数（update 允许改天数；add 时即新阶梯天数） */
    private int editSignInDays = 7;
    /** 签到编辑：货币 ID（阶梯/逐日模式） */
    private String editSignInCurrency = "neko";
    /** 签到编辑：货币数量（阶梯/逐日模式） */
    private int editSignInAmount = 0;
    /** 签到编辑：逐日覆盖目标日期（"yyyy-MM-dd"，SIGNIN_DAY 覆盖层；日号即覆盖键） */
    private String editSignInDayDate = "";
    /** 签到编辑：逐日覆盖目标日号（1..31） */
    private int editSignInDay = 1;
    /** 签到编辑：每月全局-递增开关（v1.7.8 起默认 false=不递增） */
    private boolean editSignInIncrementEnabled = false;
    /** 签到编辑：每月全局-连续递增系数（字符串暂存，保存时解析） */
    private String editSignInIncrement = "1.0";
    /** 签到编辑：每月全局-工作日默认货币 ID */
    private String editSignInWeekdayCurrency = "neko";
    /** 签到编辑：每月全局-工作日默认货币数量 */
    private int editSignInWeekdayAmount = 10;
    /** 签到编辑：每月全局-周末默认货币 ID */
    private String editSignInWeekendCurrency = "neko";
    /** 签到编辑：每月全局-周末默认货币数量 */
    private int editSignInWeekendAmount = 20;
    /** 签到编辑：每月全局-物品子模式（false=编辑工作日默认奖励 / true=编辑周末默认奖励，共用 4 物品槽） */
    private boolean editSignInMonthlyWeekend = false;
    /** 签到编辑：每月全局-非激活子模式的物品暂存（4 槽；激活子模式直接读写编辑缓冲区） */
    private final ItemStack[] editSignInMonthlyStash = new ItemStack[4];

    // --- 每日在线档位编辑（v1.7.7 G5②） ---
    /** 在线档位编辑目标同步值（C2S："<seconds>"，服务端据此加载档位物品奖励到编辑缓冲区） */
    private StringSyncValue editOnlineTargetSync;
    /** 在线档位编辑物品缓冲区（双端共享：slot 0=档位物品奖励） */
    private final ItemStackHandler editOnlineItemHandler = new ItemStackHandler(1);
    /** 在线档位编辑：原档位所需秒数（定位用，0=新建模式） */
    private int editOnlineOriginalSeconds = 0;
    /** 在线档位编辑：当前编辑的所需秒数 */
    private int editOnlineSeconds = 1800;
    /** 在线档位编辑：货币 ID */
    private String editOnlineCurrency = "neko";
    /** 在线档位编辑：货币数量 */
    private int editOnlineAmount = 0;

    // --- 祝福预设编辑（v1.7.6 G5：节日表 + 生日模板 + 发件人编辑） ---
    /** 祝福编辑附件槽位数（与邮件附件上限一致；猫猫币不入槽，由货币字段表达） */
    private static final int BLESSING_ITEM_SLOTS = 5;
    /** 祝福编辑目标同步值（C2S："festival:<index>"/"birthday"，服务端据此加载附件物品到编辑缓冲区） */
    private StringSyncValue editBlessingTargetSync;
    /** 祝福编辑物品缓冲区（双端共享：slot 0-4=附件物品） */
    private final ItemStackHandler editBlessingItemHandler = new ItemStackHandler(BLESSING_ITEM_SLOTS);
    /** 祝福编辑：当前目标标识（"birthday" / "festival:<index>"） */
    private String editBlessingTarget = "birthday";
    /** 祝福编辑：发件人显示名（面板内随任意保存一并提交） */
    private String editBlessingSender = "猫猫售货机";
    /** 祝福编辑：节日名称（仅节日目标） */
    private String editBlessingName = "";
    /** 祝福编辑：触发日期 "MM-dd"（仅节日目标） */
    private String editBlessingMonthDay = "";
    /** 祝福编辑：邮件标题 */
    private String editBlessingTitle = "";
    /** 祝福编辑：邮件正文 */
    private String editBlessingContent = "";
    /** 祝福编辑：猫猫币 ID（空串 = 无币附件） */
    private String editBlessingCurrency = "";
    /** 祝福编辑：猫猫币数量（作为附件物品发放） */
    private int editBlessingCurrencyAmount = 0;

    // --- 抽奖编辑（v1.7.0 目标 4：轮盘条目编辑） ---
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

    // --- 抽奖卡池编辑（v1.7.6 G2①：池级编辑面板 + 动态池标签列） ---
    /** 池标签列预分配按钮上限（池数量超出后不渲染；显隐由 setEnabledIf + collapseDisabledChild 每帧驱动） */
    private static final int MAX_POOL_TABS = 12;
    /** 池编辑消耗需求槽位数（图标槽除外） */
    private static final int POOL_COST_SLOTS = 4;
    /** 池编辑目标同步值特殊标记：新建池模式（服务端清空编辑缓冲区） */
    private static final String POOL_TARGET_NEW = "@new";
    /** 池编辑目标同步值（C2S：池 id 或 {@link #POOL_TARGET_NEW}，服务端据此加载图标/消耗物品到编辑缓冲区） */
    private StringSyncValue editPoolTargetSync;
    /** 池编辑物品缓冲区（双端共享：slot 0=page 图标，slot 1-4=消耗需求物品） */
    private final ItemStackHandler editPoolItemHandler = new ItemStackHandler(1 + POOL_COST_SLOTS);
    /** 池编辑：卡池 ID（新建模式下可编辑，保存时作新池 id；现有池仅展示不可改） */
    private String editPoolId = "";
    /** 池编辑：是否新建模式（true=保存时创建新池，false=保存时更新现有池） */
    private boolean editPoolIsNew = false;
    /** 池编辑：显示名称 */
    private String editPoolName = "";
    /** 池编辑：保底是否启用 */
    private boolean editPoolPityEnabled = true;
    /** 池编辑：软保底阈值 */
    private int editPoolSoftPity = 30;
    /** 池编辑：硬保底阈值 */
    private int editPoolHardPity = 50;
    /** 池编辑：硬保底保证稀有度名（COMMON/RARE/EPIC/LEGENDARY） */
    private String editPoolGuaranteedRarity = "EPIC";

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

        // v1.7.17 双端镜像构建：5 个分页控制器双端初始化（PagedWidget.Controller 无客户端依赖，
        // 服务端创建后不调用 setPage，仅用于 widget 树结构一致性，使 collectSyncValues 双端分配相同 auto_sync ID）
        // initPreAllocatedWidgets 保守保留 isClient 块内（含客户端 widget 引用初始化）
        if (syncManager.isClient()) {
            initPreAllocatedWidgets();
        }
        tabController = new PagedWidget.Controller();
        // v1.7.0 主标签控制器（贸易/签到/抽奖/邮件）
        mainTabController = new PagedWidget.Controller();
        // v1.7.6 G1 三页 sub-page 控制器（v1.7.17 改为双端创建；G1 阶段不绑定 PagedWidget，
        // 标签按钮靠 NekoSubTabButton 防崩守卫兜底，G2 建对应页面后绑定接管）
        signInPageController = new PagedWidget.Controller();
        lotteryPageController = new PagedWidget.Controller();
        mailPageController = new PagedWidget.Controller();

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

        // ==================== 双端共有子树（必须先于所有仅客户端子树添加）====================
        // 【v1.7.9 关键修复】ModularUI2 自动同步（WidgetTree.collectSyncValues）在双端各自
        // 按「BFS 遍历 widget 树的顺序」为 isSynced()=true 的 widget handler 分配 int ID，
        // 双端凭 (panelName, id) 配对收发。因此双端共有的槽位（背包/输入/输出）必须在
        // 双端 widget 树中以「相同相对顺序」出现，且不能排在任何仅客户端 ISynced 子树之后——
        // 否则客户端 BFS 会先遇到仅客户端的 PhantomItemSlot/TextFieldWidget（编辑面板/搜索框），
        // 使背包/输入/输出槽的 ID 整体偏移，服务端输出槽的同步包被客户端错误的 handler 接收：
        // 输出槽永收不到数据 → changeListener 不触发 → 掉落动画不动 → 槽停在初始 y=-1 屏幕外
        // （即 v1.7.7/1.7.8 实测「产物无掉落动画、输出槽不可见」的真根因；
        // v1.7.7 将编辑面板从独立 syncedPanel 改为内嵌 editOverlayRoot 后引入该错位，
        // v1.7.8 的 forceSyncOutputSlots 同样按错位 ID 发包，故未命中根因）。
        // 【v1.7.17 根本修复：双端镜像构建】v1.7.9 的"重排 panel.child 顺序"无效——BFS 按层级遍历，
        // editOverlayRoot 内 PhantomItemSlot 在 L3，输出槽因 TransformWidget 包裹在 L5，
        // 跨层偏移无法通过 child 顺序解决。v1.7.16 用 NekoPhantomItemSlot 覆写 isSynced()=false
        // 禁用 auto_sync，但导致 syncHandler 不 init → onUpdate setEnabled 崩溃。
        // v1.7.17 改为双端镜像构建：editOverlayRoot + 8 个 buildXxxEditPanel +
        // createMainContentPagedWidget 全部双端构建，双端 widget 树一致，auto_sync ID 不偏移，
        // syncHandler 正常 init。回退 NekoPhantomItemSlot，恢复原版 PhantomItemSlot。

        // --- 玩家背包栏（v1.7.5 从贸易列拆出，双端挂 panel）---
        // 双端创建：服务端必须注册背包槽，否则容器缺槽、shift 转移失效（与 VM 原版 createInventoryRow 一致）。
        // v1.7.6 G1：四页恒显示（三页物品交互需要背包，用户已确认）——移除原「仅贸易页」的 setEnabledIf；
        // 三页底部内容暂时被背包行遮挡属已裁决的过渡态（G2 重写三页布局时根除，不发布中间态）。
        panel.child(
            Flow.row()
                .fullWidth()
                .height(76)
                .bottom(5)
                .child(
                    SlotGroupWidget.playerInventory(false)
                        .marginLeft(4)));

        // 右侧 IO 列（v1.7.6 G1：四页恒显示）
        // 含全部输入/输出槽（非 phantom，ISynced），双端创建且必须先于下方仅客户端子树添加
        panel.child(createIOColumn());

        // ==================== 以下均为仅客户端子树（不得含双端需要同步的槽位）====================

        // v1.7.0 主标签列（贸易/签到/抽奖/邮件），位于贸易分类列的更左边
        if (syncManager.isClient()) {
            panel.child(createMainTabColumn());
        }

        // 左侧贸易分类标签列（仅主标签为贸易时显示）
        if (syncManager.isClient()) {
            panel.child(createTabColumn());
            panel.child(createQolButtonColumn());
            // v1.7.6 G1：签到/抽奖/邮件三页各自的 sub-page 标签列（与贸易分类列同位 left(-29)，
            // 按主标签互斥显示；纯按钮无槽位，仅客户端创建，与 createTabColumn 挂载路径一致）
            panel.child(createSubTabColumn(MAIN_TAB_SIGNIN));
            panel.child(createSubTabColumn(MAIN_TAB_LOTTERY));
            panel.child(createSubTabColumn(MAIN_TAB_MAIL));
        }

        // v1.6.24: 音量面板在 client 块外部注册（与 VM 原版 MTEVendingMachineGui 一致），
        // 确保服务端也注册同步通道，否则客户端 togglePanel() 静默失败导致面板无法弹出。
        // volumeButton 在 createQolButtonColumn 内被赋值（仅客户端），但 syncedPanel 第二参数 true
        // 表示仅在客户端创建面板，回调中的 volumeButton 引用仅在客户端被使用，服务端不触发回调。
        // 注：syncedPanel 走显式命名注册（PanelSyncHandler），不进主 panel widget 树，与上述 BFS 顺序无关。
        volumePanel = syncManager
            .syncedPanel("nekoV2Volume", true, (sm, sh) -> new NekoVolumeControlGui().createPanel(sm, volumeButton));

        // v1.7.7 G2① 编辑面板统一改为 NekoTradeMainPanel 内嵌覆盖层，
        // 不再使用独立 syncedPanel 子窗口，避免窗口坐标系错位/被 NEI 或背包行遮挡。
        // 【v1.7.17 根本修复：双端镜像构建】editOverlayRoot 及其 8 个 buildXxxEditPanel
        // 改为双端构建，双端 widget 树完全一致，collectSyncValues 分配相同 auto_sync ID，
        // 无偏移，syncHandler 正常 init。彻底解决 v1.7.16 NekoPhantomItemSlot.isSynced()=false
        // 导致 syncHandler 不 init → onUpdate setEnabled 崩溃的问题，以及 v1.7.15 遗留的
        // 物品无下落动画、点击偏移、物品放不回物品栏等所有 ID 偏移相关问题。
        // 8 个 buildXxxEditPanel 内无客户端 API（Grep 全文件确认），双端构建安全；
        // overlayInterceptor 的 onMousePressed 回调仅在客户端鼠标点击时触发，服务端不调用。
        editOverlayRoot = new ParentWidget<>();
        editOverlayRoot.relativeToScreen()
            .full();
        editOverlayRoot.setEnabledIf(w -> currentEditOverlay != EditOverlayType.NONE);

        // v1.7.27 修复：移除全屏透明拦截层。原 overlayInterceptor 会在点击编辑器外部
        // 任意位置时调用 closeEditOverlay() 并消费事件，导致编辑器意外关闭，并拦截
        // 背包栏/NEI 的鼠标交互。现在仅 Esc、E、保存/取消按钮可关闭编辑器。

        editOverlayRoot.child(buildTradeEditPanel());
        editOverlayRoot.child(buildSignInEditPanel());
        // v1.7.8 任务6：逐日覆盖编辑面板（SIGNIN_DAY 覆盖层）
        editOverlayRoot.child(buildSignInDayEditPanel());
        editOverlayRoot.child(buildOnlineTierEditPanel());
        editOverlayRoot.child(buildLotteryEditPanel());
        editOverlayRoot.child(buildLotteryPoolEditPanel());
        editOverlayRoot.child(buildPageEditPanel());
        editOverlayRoot.child(buildBlessingEditPanel());

        // v1.7.0 主内容区（PagedWidget 切换贸易/签到/抽奖/邮件）
        // v1.7.17 双端镜像构建：mainTabController 等控制器已双端初始化（上方），
        // createMainContentPagedWidget 改为双端构建。4 个页面（createTradeMainColumn/
        // SignInCalendarGui/LotteryGui/MailGui）均无客户端 API、无 ISynced widget，
        // 类注释明确声明"双端安全"，双端构建无副作用。
        panel.child(createMainContentPagedWidget(syncManager));

        // v1.7.20 修复：editOverlayRoot 必须在主内容 PagedWidget 之后添加。
        // ModularUI2 渲染顺序由 children 列表决定，后添加的 widget 在上层。
        // 若 editOverlayRoot 在主内容之前添加，编辑面板会被主内容覆盖，
        // 导致配方编辑界面低于各类按钮、抽卡等主内容控件。
        panel.child(editOverlayRoot);

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

        // --- 签到编辑目标（C2S：客户端设置 "tier:<days>"/"cumtier:<days>"，服务端加载连续/累计阶梯物品奖励到编辑缓冲区）---
        editSignInTargetSync = new StringSyncValue(() -> "", val -> {
            if (syncManager != null && !syncManager.isClient() && val != null && !val.isEmpty()) {
                loadSignInTierIntoEditBuffer(val);
            }
        });
        editSignInTargetSync.allowC2S();
        syncManager.syncValue("nekoV2EditSignInTarget", editSignInTargetSync);

        // --- 在线档位编辑目标（C2S：客户端设置 "<seconds>"，服务端加载档位物品奖励到编辑缓冲区，v1.7.7 G5②）---
        editOnlineTargetSync = new StringSyncValue(() -> "", val -> {
            if (syncManager != null && !syncManager.isClient() && val != null && !val.isEmpty()) {
                loadOnlineTierIntoEditBuffer(val);
            }
        });
        editOnlineTargetSync.allowC2S();
        syncManager.syncValue("nekoV2EditOnlineTarget", editOnlineTargetSync);

        // --- 抽奖编辑目标（C2S：客户端设置 "<poolId>:<entryId>"，服务端加载条目物品到编辑缓冲区）---
        editLotteryTargetSync = new StringSyncValue(() -> "", val -> {
            if (syncManager != null && !syncManager.isClient() && val != null && !val.isEmpty()) {
                loadLotteryEntryIntoEditBuffer(val);
            }
        });
        editLotteryTargetSync.allowC2S();
        syncManager.syncValue("nekoV2EditLotteryTarget", editLotteryTargetSync);

        // --- 池编辑目标（C2S：客户端设置池 id 或 "@new"，服务端加载图标/消耗需求物品到编辑缓冲区）---
        editPoolTargetSync = new StringSyncValue(() -> "", val -> {
            if (syncManager != null && !syncManager.isClient() && val != null && !val.isEmpty()) {
                loadPoolIntoEditBuffer(val);
            }
        });
        editPoolTargetSync.allowC2S();
        syncManager.syncValue("nekoV2EditPoolTarget", editPoolTargetSync);

        // --- page 编辑目标（C2S：客户端设置 pageId 或 "@new"，服务端加载 page 图标到编辑缓冲区，v1.7.6 G3④）---
        editPageTargetSync = new StringSyncValue(() -> "", val -> {
            if (syncManager != null && !syncManager.isClient() && val != null && !val.isEmpty()) {
                loadPageIntoEditBuffer(val);
            }
        });
        editPageTargetSync.allowC2S();
        syncManager.syncValue("nekoV2EditPageTarget", editPageTargetSync);

        // --- 祝福编辑目标（C2S：客户端设置 "festival:<index>"/"birthday"，服务端加载附件物品到编辑缓冲区，v1.7.6 G5）---
        editBlessingTargetSync = new StringSyncValue(() -> "", val -> {
            if (syncManager != null && !syncManager.isClient() && val != null && !val.isEmpty()) {
                loadBlessingIntoEditBuffer(val);
            }
        });
        editBlessingTargetSync.allowC2S();
        syncManager.syncValue("nekoV2EditBlessingTarget", editBlessingTargetSync);

        // v1.7.13：移除 addOpenListener 中的 forceSyncOutputSlots 调用。
        // 根因：forceSync（init=false + forceSync=true）会在 GUI 打开时对已有物品的输出槽
        // 触发 changeListener(init=false)，导致 MutableObjectAnimator.resume() 立即将
        // fallingPosition 插值到起点 (x,-1) 隐藏区 → 物品隐形 + 点击偏移 + 无法放回物品栏。
        // v1.7.12 的 lastItem 判断虽可抑制同物品的动画，但增加了不必要的复杂度且存在边界问题。
        // 正确做法（回归 VM 原版）：不对输出槽使用 forceSync，依赖原版同步机制：
        // Container.addSlotToContainer 将 inventoryItemStacks 初始化为 null，
        // 首次 detectAndSendChanges 检测到 null≠当前物品 → 发送 S2FPacketSetSlot →
        // 客户端 putStack 设置物品。无需 forceSync 兜底。
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
    public long getSyncedCooldownRemaining(UUID groupId, int tradeIndex) {
        if (groupId == null || tradeIndex < 0) {
            return 0L;
        }
        String key = groupId.toString() + ":" + tradeIndex;
        Long remaining = cooldownStatusMap.get(key);
        return remaining != null && remaining > 0L ? remaining : 0L;
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
        this.editTradeIsNew = false;
        // v1.7.6 G3③ 格子残留修复（重置点①）：客户端立即清空 32 槽+重置全部编辑字段，
        // 防止连续切换编辑不同条目时 PhantomItemSlot 客户端缓存残留上一条内容
        // （服务端缓冲区随后由 loadTradeIntoEditBuffer 重置点②覆盖并同步回客户端）
        clearTradeEditState();
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
        openEditOverlay(EditOverlayType.TRADE);
    }

    /**
     * 打开新建交易编辑面板（客户端，编辑模式下点击交易列表尾「新建交易条目」按钮触发，v1.7.6 G3④）
     * <p>
     * 字段全部置默认值（{@link #clearTradeEditState}），editTabId 固定为当前所在标签页
     * （新建条目挂到该页，orderId 由服务端取页内最大+1）；
     * 通知服务端清空编辑缓冲区（{@link #TRADE_TARGET_NEW} 标记）。
     * 保存时走 {@code NekoEditNetworkManager.sendCreateTrade}（服务端分配 UUID 追加到该 page）。
     *
     * @param tabId 当前标签页 ID（新建条目挂到该页）
     */
    private void openNewTradeEditor(int tabId) {
        this.editingDisplay = null;
        this.editTradeIsNew = true;
        // 重置点①同款：客户端立即清空 32 槽+重置全部编辑字段
        clearTradeEditState();
        this.editTabId = tabId;
        // 通知服务端清空编辑缓冲区（新建模式无既有物品可加载）
        if (editTargetSync != null) {
            editTargetSync.setValue(TRADE_TARGET_NEW);
        }
        openEditOverlay(EditOverlayType.TRADE);
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

    /**
     * 打开指定类型的编辑覆盖层（v1.7.7 G2①）
     * <p>
     * 覆盖层作为主面板的直接 child，统一坐标系；
     * 打开时底层透明拦截层会吞掉对主内容的点击。
     *
     * @param type 要打开的编辑覆盖层类型
     */
    private void openEditOverlay(EditOverlayType type) {
        this.currentEditOverlay = type;
    }

    /**
     * 检查当前是否有编辑覆盖层处于打开状态（v1.7.27）
     *
     * @return true 表示任意编辑器面板正在显示
     */
    @Override
    public boolean isEditOverlayOpen() {
        return this.currentEditOverlay != EditOverlayType.NONE;
    }

    /**
     * 关闭当前编辑覆盖层（v1.7.7 G2①，v1.7.27 提升为 public 并加入 PanelCallback）
     * <p>
     * 关闭后恢复主内容交互，并清空交易编辑面板的客户端残留状态。
     */
    @Override
    public void closeEditOverlay() {
        EditOverlayType previous = this.currentEditOverlay;
        this.currentEditOverlay = EditOverlayType.NONE;
        if (previous == EditOverlayType.TRADE) {
            clearTradeEditState();
        }
    }

    // ==================== 编辑模式：数据加载与面板构建 ====================

    /**
     * 从交易显示数据填充编辑字段（客户端）
     * <p>
     * 将 {@link NekoTradeItemDisplay} 中的数据提取到编辑面板本地字段，
     * 供编辑面板的 TextFieldWidget 显示和编辑。
     * <p>
     * v1.7.6 G3② 货币解绑：不再提取货币类型/数量到独立字段（货币由需求格物品条目表达）；
     * v1.7.6 G3⑤：从交易读取 recordNBT 填充「严格匹配NBT」开关。
     *
     * @param display 交易显示数据
     */
    private void populateEditFields(NekoTradeItemDisplay display) {
        // 从 NekoTradeDatabase 获取配置信息（冷却、BQ 绑定、NBT 匹配开关等）
        NekoTradeGroup group = NekoTradeDatabase.INSTANCE.getTradeGroup(display.getGroupId());
        if (group != null) {
            editCooldown = group.getCooldown();
            editMaxTrades = group.getMaxTrades();
            editBqQuestId = group.getBqQuestId() != null ? group.getBqQuestId() : "";
            editTabId = group.getTabId();
            editOrderId = group.getOrderId();
            // v1.7.6 G3⑤：recordNBT 为交易级字段（非组级），按索引取当前交易
            int tradeIndex = display.getTradeIndex();
            if (tradeIndex >= 0 && tradeIndex < group.getTrades()
                .size()) {
                editRecordNBT = group.getTrades()
                    .get(tradeIndex)
                    .isRecordNBT();
            }
        }
    }

    /**
     * 服务端：加载交易数据到编辑缓冲区（v1.7.6 G3① 扩 32 槽）
     * <p>
     * 解析 "groupId:tradeIndex" 格式的目标标识，从 {@link NekoTradeDatabase}
     * 查找交易，将需求物品加载到 editItemHandler 的 slot 0-15（两行），
     * 产物物品加载到 slot 16-31（两行）。
     * <p>
     * v1.7.6 G3③ 格子残留修复（重置点②）：每次加载前先整体清空 32 槽；
     * {@link #TRADE_TARGET_NEW}（新建模式，G3④）或查找失败时仅清空不加载。
     *
     * @param target "groupId:tradeIndex" 格式的目标标识，或 {@link #TRADE_TARGET_NEW}
     */
    private void loadTradeIntoEditBuffer(String target) {
        try {
            // 先整体清空 32 槽（重置点②：防上一条编辑内容残留）
            for (int i = 0; i < editItemHandler.getSlots(); i++) {
                editItemHandler.setStackInSlot(i, null);
            }
            // 新建模式：无既有交易可加载，保持空缓冲区
            if (TRADE_TARGET_NEW.equals(target)) {
                forceSyncTradeEditSlots();
                return;
            }

            String[] parts = target.split(":");
            if (parts.length != 2) {
                forceSyncTradeEditSlots();
                return;
            }
            UUID groupId = UUID.fromString(parts[0]);
            int tradeIndex = Integer.parseInt(parts[1]);

            NekoTradeGroup group = NekoTradeDatabase.INSTANCE.getTradeGroup(groupId);
            if (group == null || tradeIndex < 0
                || tradeIndex >= group.getTrades()
                    .size()) {
                forceSyncTradeEditSlots();
                return;
            }

            NekoTrade trade = group.getTrades()
                .get(tradeIndex);

            // 加载需求物品到 slot 0-15（v1.7.6 G3① 上限 4→16）
            List<NekoBigItemStack> fromItems = trade.getFromItems();
            for (int i = 0; i < Math.min(fromItems.size(), 16); i++) {
                NekoBigItemStack bigStack = fromItems.get(i);
                if (bigStack != null && bigStack.getBaseStack() != null) {
                    ItemStack stack = bigStack.getBaseStack()
                        .copy();
                    stack.stackSize = bigStack.getStackSize();
                    editItemHandler.setStackInSlot(i, stack);
                }
            }

            // 加载产物物品到 slot 16-31（v1.7.6 G3① 上限 4→16，槽位偏移 4+i→16+i）
            List<NekoBigItemStack> toItems = trade.getToItems();
            for (int i = 0; i < Math.min(toItems.size(), 16); i++) {
                NekoBigItemStack bigStack = toItems.get(i);
                if (bigStack != null && bigStack.getBaseStack() != null) {
                    ItemStack stack = bigStack.getBaseStack()
                        .copy();
                    stack.stackSize = bigStack.getStackSize();
                    editItemHandler.setStackInSlot(16 + i, stack);
                }
            }

            // ItemSlotSH 仅在检测到服务端槽位内容变化时自动发包。
            // 取消编辑会先清空客户端缓存；再次打开同一条目时服务端缓冲区可能未变化，
            // 因而不会自动回传，表现为物品/猫猫币消失。每次加载后强制回传 32 槽，
            // 让客户端缓存与服务端编辑缓冲区重新对齐。
            forceSyncTradeEditSlots();

            GTInterestingThing.LOG.info("[NekoEdit] 已加载交易到编辑缓冲区: {}", target);
        } catch (Exception e) {
            GTInterestingThing.LOG.error("[NekoEdit] 加载交易到编辑缓冲区失败: {}", target, e);
        }
    }

    /** 强制同步交易编辑 32 个 PhantomItemSlot（仅服务端加载路径调用）。 */
    private void forceSyncTradeEditSlots() {
        for (ItemSlot slot : editTradeSlotRefs) {
            if (slot != null && slot.getSyncHandler() != null) {
                slot.getSyncHandler()
                    .forceSyncItem();
            }
        }
    }

    /**
     * 构建交易编辑面板（v1.7.6 G3① 重构）
     * <p>
     * 创建包含 PhantomItemSlot（物品拖放配置）和 TextFieldWidget（参数编辑）
     * 的编辑面板。slot 0-15 为需求物品（两行×8），slot 16-31 为产物物品（两行×8）。
     * <p>
     * v1.7.6 G3② 货币解绑：面板不再设「猫猫币类型/数量」输入框——货币需求=需求格中的
     * 猫猫币物品条目（购买时扣钱包），货币产出=产物格中的猫猫币物品条目（购买后入钱包）。
     * v1.7.6 G3⑤：新增「严格匹配NBT」开关（recordNBT，统一默认不勾=仅按物品匹配）。
     * v1.7.6 G3④：新建模式（{@link #editTradeIsNew}）下标题切换为「新建交易」，保存走 createTrade。
     *
     * @return 编辑覆盖层面板（{@link ParentWidget}）
     */
    private NekoDraggableEditPanel buildTradeEditPanel() {
        NekoDraggableEditPanel editPanel = new NekoDraggableEditPanel();
        editPanel.size(250, 190);
        // v1.7.7 G2 迁移为主面板内嵌 ParentWidget 覆盖层后无默认背景，需手动补上 MC 风格背景
        editPanel.background(GuiTextures.MC_BACKGROUND);
        editPanel.leftRel(0.5f)
            .topRel(0.5f)
            .anchorLeft(0.5f)
            .anchorTop(0.5f);
        editPanel.setEnabledIf(w -> currentEditOverlay == EditOverlayType.TRADE);

        // 标题（新建 / 编辑动态切换，v1.7.6 G3④）
        editPanel.child(
            new TextWidget<>(IKey.dynamic(() -> EnumChatFormatting.GOLD + (editTradeIsNew ? "新建交易" : "编辑交易"))).top(5)
                .horizontalCenter());

        // --- 需求物品区（slot 0-15，两行×8；v1.7.6 G3①）---
        // 货币解绑提示：需求格放猫猫币物品 = 货币需求（购买时扣钱包）
        editPanel.child(
            IKey.str(EnumChatFormatting.WHITE + "需求:")
                .asWidget()
                .left(8)
                .top(20));
        for (int i = 0; i < 16; i++) {
            ItemSlot slot = new PhantomItemSlot().slot(new ModularSlot(editItemHandler, i))
                .left(40 + (i % 8) * 20)
                .top(18 + (i / 8) * 20);
            editTradeSlotRefs.add(slot);
            editPanel.child(slot);
        }

        // --- 产物物品区（slot 16-31，两行×8；v1.7.6 G3①）---
        // 货币解绑提示：产物格放猫猫币物品 = 货币产出（购买后入钱包）
        editPanel.child(
            IKey.str(EnumChatFormatting.WHITE + "产物:")
                .asWidget()
                .left(8)
                .top(62));
        for (int i = 0; i < 16; i++) {
            ItemSlot slot = new PhantomItemSlot().slot(new ModularSlot(editItemHandler, 16 + i))
                .left(40 + (i % 8) * 20)
                .top(60 + (i / 8) * 20);
            editTradeSlotRefs.add(slot);
            editPanel.child(slot);
        }

        // --- 参数编辑区（v1.7.6 G3②：原「猫猫币类型/数量」两行已删除）---
        int fieldY = 105;
        int fieldHeight = 14;
        int labelWidth = 70;
        int fieldWidth = 160;
        int spacing = 17;

        // 冷却时间
        editPanel.child(
            IKey.str(EnumChatFormatting.WHITE + "冷却(秒):")
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
            IKey.str(EnumChatFormatting.WHITE + "BQ绑定ID:")
                .asWidget()
                .left(8)
                .top(fieldY + 2));
        editPanel.child(
            new TextFieldWidget().value(new StringValue.Dynamic(() -> editBqQuestId, val -> editBqQuestId = val))
                .setMaxLength(60)
                .left(labelWidth)
                .top(fieldY)
                .size(fieldWidth, fieldHeight));

        // 严格匹配 NBT（v1.7.6 G3⑤，点击切换；统一默认不勾=仅按物品匹配）
        fieldY += spacing;
        editPanel.child(
            IKey.str(EnumChatFormatting.WHITE + "严格匹配NBT:")
                .asWidget()
                .left(8)
                .top(fieldY + 2));
        ButtonWidget<?> recordNbtToggle = new ButtonWidget<>().left(labelWidth)
            .top(fieldY)
            .size(fieldWidth, fieldHeight)
            .background(NekoGuiTextures.TEXT_FIELD_BACKGROUND)
            .overlay(
                IKey.dynamic(() -> editRecordNBT ? EnumChatFormatting.GREEN + "启用" : EnumChatFormatting.RED + "停用"))
            .onMouseTapped(mouse -> {
                editRecordNBT = !editRecordNBT;
                return true;
            });
        recordNbtToggle.tooltipBuilder(t -> {
            t.addLine(IKey.str("点击切换需求物品的 NBT 匹配严格度"));
            t.addLine(IKey.str(EnumChatFormatting.GRAY + "启用：需求物品按物品+NBT 精确匹配"));
            t.addLine(IKey.str(EnumChatFormatting.GRAY + "停用：仅按物品匹配（忽略 NBT 差异）"));
        });
        recordNbtToggle.tooltipAutoUpdate(true);
        editPanel.child(recordNbtToggle);

        // --- 保存 / 取消按钮 ---
        editPanel.child(
            new ButtonWidget<>().size(50, 16)
                .left(30)
                .bottom(8)
                .overlay(IKey.str("保存"))
                .onMouseTapped(mouse -> {
                    saveTradeEdit();
                    closeEditOverlay();
                    return true;
                }));
        editPanel.child(
            new ButtonWidget<>().size(50, 16)
                .right(30)
                .bottom(8)
                .overlay(IKey.str("取消"))
                .onMouseTapped(mouse -> {
                    closeEditOverlay();
                    return true;
                }));

        return editPanel;
    }

    /**
     * 清空交易编辑面板客户端状态（v1.7.6 G3③ 格子残留修复）
     * <p>
     * 清空客户端编辑物品缓冲区 32 槽并重置全部编辑字段为默认值。
     * 在打开编辑面板（{@link #onEditRequested}/{@link #openNewTradeEditor}，重置点①）
     * 与面板关闭回调（重置点③）中调用，防止连续编辑不同条目时
     * PhantomItemSlot 客户端缓存残留上一条内容。
     * <p>
     * 仅客户端调用：直接改客户端 handler 不发包，服务端缓冲区随后由
     * {@link #loadTradeIntoEditBuffer}（重置点②）覆盖并经 widget 层同步回客户端。
     */
    private void clearTradeEditState() {
        for (int i = 0; i < editItemHandler.getSlots(); i++) {
            editItemHandler.setStackInSlot(i, null);
        }
        editCooldown = 0;
        editMaxTrades = -1;
        editBqQuestId = "";
        editTabId = 1;
        editOrderId = 0;
        editRecordNBT = false;
    }

    /**
     * 保存交易编辑（客户端 → 服务端）
     * <p>
     * 将编辑面板的物品缓冲区内容（需求 slot 0-15 / 产物 slot 16-31，v1.7.6 G3①）和
     * 参数字段序列化为 JSON，发送到服务端。
     * <p>
     * v1.7.6 G3② 货币解绑：不再发送 currencyType/currencyAmount——货币需求/产出由
     * fromItems/toItems 中的猫猫币物品条目表达（服务端保存时无条件清除旧 currency 字段）。
     * v1.7.6 G3④：新建模式（{@link #editTradeIsNew}）走
     * {@link com.miaokatze.gtit.trade.v2.NekoEditNetworkManager#sendCreateTrade}（tabId 定位），
     * 编辑现有交易走 {@link com.miaokatze.gtit.trade.v2.NekoEditNetworkManager#sendSaveTrade}。
     */
    private void saveTradeEdit() {
        // 新建模式无 editingDisplay（无现有交易可定位）；编辑模式必须有
        if (!editTradeIsNew && editingDisplay == null) return;

        try {
            com.google.gson.JsonObject json = new com.google.gson.JsonObject();

            // 基础参数
            json.addProperty("tabId", editTabId);
            json.addProperty("orderId", editOrderId);
            json.addProperty("cooldown", editCooldown);
            json.addProperty("maxTrades", editMaxTrades);
            json.addProperty("bqQuestId", editBqQuestId);
            // v1.7.6 G3⑤ NBT 选框
            json.addProperty("recordNBT", editRecordNBT);

            // 需求物品（slot 0-15，跳过空槽；猫猫币条目 = 货币需求，G3②）
            com.google.gson.JsonArray fromItems = new com.google.gson.JsonArray();
            for (int i = 0; i < 16; i++) {
                ItemStack stack = editItemHandler.getStackInSlot(i);
                if (stack != null) {
                    fromItems.add(itemStackToEditJson(stack));
                }
            }
            json.add("fromItems", fromItems);

            // 产物物品（slot 16-31，跳过空槽；猫猫币条目 = 产出入钱包，G3②）
            com.google.gson.JsonArray toItems = new com.google.gson.JsonArray();
            for (int i = 16; i < 32; i++) {
                ItemStack stack = editItemHandler.getStackInSlot(i);
                if (stack != null) {
                    toItems.add(itemStackToEditJson(stack));
                }
            }
            json.add("toItems", toItems);

            // 客户端防御性校验：编辑现有交易时，若 toItems 为空则阻止发送
            // 原因：toItems 为空的交易会被服务端跳过注册，导致交易"消失"
            // （v1.7.33 修复交易条目保存丢失：客户端不发送会导致交易丢失的空数据）
            if (!editTradeIsNew && toItems.size() == 0) {
                GTInterestingThing.LOG
                    .warn("[NekoEdit] 客户端阻止保存：编辑模式下 toItems 为空（fromItems={}），疑似物品同步未完成，跳过发送", fromItems.size());
                // 向玩家显示提示（客户端本地聊天消息）
                try {
                    net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getMinecraft();
                    if (mc.thePlayer != null) {
                        mc.thePlayer.addChatMessage(
                            new net.minecraft.util.ChatComponentText(
                                net.minecraft.util.EnumChatFormatting.RED + "[编辑模式] 保存失败：产物数据为空，请等待物品显示后再试"));
                    }
                } catch (Exception ignored) {
                    // 客户端环境异常时不阻塞
                }
                return;
            }

            // 发送到服务端（新建 / 编辑分流，v1.7.6 G3④）
            if (editTradeIsNew) {
                com.miaokatze.gtit.trade.v2.NekoEditNetworkManager
                    .sendCreateTrade(String.valueOf(editTabId), json.toString());
            } else {
                com.miaokatze.gtit.trade.v2.NekoEditNetworkManager.sendSaveTrade(
                    editingDisplay.getGroupId()
                        .toString(),
                    editingDisplay.getTradeIndex(),
                    json.toString());
            }

            // 发送成功后强制刷新主面板，确保客户端显示与最新配置同步
            if (mainPanel != null) {
                mainPanel.setForceRefresh();
            }
            GTInterestingThing.LOG.info(
                "[NekoEdit] 客户端发送保存: group={}, index={}, new={}, fromItems={}, toItems={}",
                editTradeIsNew ? String.valueOf(editTabId)
                    : editingDisplay.getGroupId()
                        .toString(),
                editTradeIsNew ? -1 : editingDisplay.getTradeIndex(),
                editTradeIsNew,
                fromItems.size(),
                toItems.size());

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
                openSignInTierEditor(tier, false);
            }

            @Override
            public void onEditCumulativeTierRequested(com.miaokatze.gtit.signin.SignInRewardTier tier) {
                openSignInTierEditor(tier, true);
            }

            @Override
            public void onEditDayRewardRequested(String date) {
                openSignInDayEditor(date);
            }

            @Override
            public void onEditGlobalRequested() {
                openSignInGlobalEditor();
            }

            @Override
            public void onEditOnlineTierRequested(com.miaokatze.gtit.signin.OnlineTimeRewardTier tier) {
                openOnlineTierEditor(tier);
            }
        };
    }

    /**
     * 打开签到阶梯编辑面板（客户端，编辑模式下点击连续/累计阶梯槽触发）
     * <p>
     * 从传入阶梯（{@link SignInClientData} 同步快照口径）填充货币字段，并通过
     * {@link #editSignInTargetSync} 通知服务端把阶梯物品奖励加载到编辑缓冲区
     * （4 槽，PhantomItemSlot 自动同步回客户端显示）。
     * tier 为 null 时进入新增模式（点击「+」空槽）：清空字段与物品缓冲区，保存时走 add。
     *
     * @param tier       被点击的阶梯奖励；null=新增模式
     * @param cumulative true=累计阶梯 / false=连续阶梯
     */
    private void openSignInTierEditor(com.miaokatze.gtit.signin.SignInRewardTier tier, boolean cumulative) {
        editSignInMode = cumulative ? "cumtier" : "tier";
        if (tier != null) {
            // 编辑模式：记录原天数（定位用）并填充货币字段
            editSignInOriginalDays = tier.getRequiredDays();
            editSignInDays = tier.getRequiredDays();
            SignInReward reward = tier.getReward();
            editSignInCurrency = reward != null && !reward.getCurrencyId()
                .isEmpty() ? reward.getCurrencyId() : "neko";
            editSignInAmount = reward != null ? reward.getCurrencyAmount() : 0;
            // 通知服务端加载该阶梯的物品奖励到编辑缓冲区
            if (editSignInTargetSync != null) {
                editSignInTargetSync.setValue(editSignInMode + ":" + editSignInOriginalDays);
            }
        } else {
            // 新增模式：清空字段与物品缓冲区（客户端清空后经 PhantomItemSlot 同步到服务端）
            editSignInOriginalDays = -1;
            editSignInDays = 7;
            editSignInCurrency = "neko";
            editSignInAmount = 0;
            clearSignInItemBuffer(editSignInItemHandler);
        }
        openEditOverlay(EditOverlayType.SIGNIN);
    }

    /**
     * 打开逐日覆盖编辑面板（客户端，编辑模式下点击每月签到日期格触发，v1.7.8 任务6）
     * <p>
     * 预填生效奖励（覆盖优先，否则工作日/周末默认；读 {@link SignInClientData} 同步快照），
     * 物品槽由客户端本地预填（快照含完整物品 ID/meta/NBT，无需服务端往返）；
     * 保存后服务端写入 day_overrides（按每月日号生效）。
     *
     * @param date 被点击的日期（"yyyy-MM-dd"，当月有效日期）
     */
    private void openSignInDayEditor(String date) {
        int day;
        try {
            day = Integer.parseInt(date.substring(8));
        } catch (Exception e) {
            GTInterestingThing.LOG.warn("[NekoEdit] 逐日覆盖日期非法: {}", date);
            return;
        }
        editSignInDayDate = date;
        editSignInDay = day;
        SignInReward effective = SignInClientData.getEffectiveDayReward(date);
        editSignInCurrency = effective != null && !effective.getCurrencyId()
            .isEmpty() ? effective.getCurrencyId() : "neko";
        editSignInAmount = effective != null ? effective.getCurrencyAmount() : 0;
        fillSignInItemBuffer(editSignInDayItemHandler, effective);
        openEditOverlay(EditOverlayType.SIGNIN_DAY);
    }

    /**
     * 打开每月签到全局配置编辑面板（客户端，编辑模式下点击「全局配置」按钮触发，v1.7.8 任务6）
     * <p>
     * 从 {@link SignInClientData} 同步快照读取递增开关/系数与工作日/周末默认奖励填充字段；
     * 物品子模式默认工作日——周末默认物品进暂存、工作日默认物品进编辑缓冲区（客户端本地填充，
     * 快照含完整物品 ID/meta/NBT，无需服务端往返）。
     */
    private void openSignInGlobalEditor() {
        editSignInMode = "monthly";
        editSignInIncrementEnabled = SignInClientData.isIncrementEnabled();
        editSignInIncrement = String.valueOf(SignInClientData.getConsecutiveIncrement());
        SignInReward weekday = SignInClientData.getWeekdayDefault();
        SignInReward weekend = SignInClientData.getWeekendDefault();
        editSignInWeekdayCurrency = weekday != null && !weekday.getCurrencyId()
            .isEmpty() ? weekday.getCurrencyId() : "neko";
        editSignInWeekdayAmount = weekday != null ? weekday.getCurrencyAmount() : 0;
        editSignInWeekendCurrency = weekend != null && !weekend.getCurrencyId()
            .isEmpty() ? weekend.getCurrencyId() : "neko";
        editSignInWeekendAmount = weekend != null ? weekend.getCurrencyAmount() : 0;
        // 物品子模式默认工作日：周末物品进暂存，工作日物品进编辑缓冲区
        editSignInMonthlyWeekend = false;
        stashSignInMonthlyItems(weekend);
        fillSignInItemBuffer(editSignInItemHandler, weekday);
        openEditOverlay(EditOverlayType.SIGNIN);
    }

    /**
     * 服务端：加载签到阶梯物品奖励到编辑缓冲区（4 槽）
     * <p>
     * 解析 "tier:&lt;days&gt;" / "cumtier:&lt;days&gt;" 目标标识，从 {@code DailySignInConfig}
     * 查找连续/累计阶梯，将其物品奖励（含 NBT）按序放入编辑缓冲区，未命中则清空。
     *
     * @param target "tier:&lt;days&gt;" 或 "cumtier:&lt;days&gt;" 格式的目标标识
     */
    private void loadSignInTierIntoEditBuffer(String target) {
        try {
            boolean cumulative;
            String daysStr;
            if (target.startsWith("tier:")) {
                cumulative = false;
                daysStr = target.substring("tier:".length());
            } else if (target.startsWith("cumtier:")) {
                cumulative = true;
                daysStr = target.substring("cumtier:".length());
            } else {
                return;
            }
            int days = Integer.parseInt(daysStr);
            List<com.miaokatze.gtit.signin.SignInRewardTier> tiers = cumulative
                ? com.miaokatze.gtit.signin.DailySignInConfig.getCumulativeTiers()
                : com.miaokatze.gtit.signin.DailySignInConfig.getRewardTiers();
            SignInReward reward = null;
            for (com.miaokatze.gtit.signin.SignInRewardTier tier : tiers) {
                if (tier.getRequiredDays() == days) {
                    reward = tier.getReward();
                    break;
                }
            }
            fillSignInItemBuffer(editSignInItemHandler, reward);
            GTInterestingThing.LOG.info("[NekoEdit] 已加载签到{}阶梯到编辑缓冲区: {}", cumulative ? "累计" : "连续", target);
        } catch (Exception e) {
            GTInterestingThing.LOG.error("[NekoEdit] 加载签到阶梯到编辑缓冲区失败: {}", target, e);
        }
    }

    // ==================== 签到编辑：物品缓冲区辅助（v1.7.8 任务5+6） ====================

    /** 清空签到编辑物品缓冲区（4 槽；客户端清空经 PhantomItemSlot 同步到服务端） */
    private void clearSignInItemBuffer(ItemStackHandler handler) {
        for (int i = 0; i < 4; i++) {
            handler.setStackInSlot(i, null);
        }
    }

    /**
     * 将统一奖励模型中的物品（最多 4 个）填入指定签到编辑物品缓冲区
     * <p>
     * 先清空缓冲区，再逐条解析 {@link RewardItem}（物品 ID + meta + NBT）按序填充，
     * 空条目/解析失败跳过。双端共用（客户端预填 / 服务端加载均走本方法）。
     *
     * @param handler 目标物品缓冲区（4 槽）
     * @param reward  统一奖励模型（null 时仅清空缓冲区）
     */
    private void fillSignInItemBuffer(ItemStackHandler handler, SignInReward reward) {
        clearSignInItemBuffer(handler);
        if (reward == null) return;
        int slot = 0;
        for (RewardItem item : reward.getItems()) {
            if (slot >= 4) break;
            if (item == null || item.isEmpty()) continue;
            ItemStack stack = resolveSignInRewardStack(item);
            if (stack != null) {
                handler.setStackInSlot(slot, stack);
                slot++;
            }
        }
    }

    /**
     * 将统一奖励模型中的物品（最多 4 个）解析进每月全局「非激活子模式」暂存
     *
     * @param reward 统一奖励模型（null 时清空暂存）
     */
    private void stashSignInMonthlyItems(SignInReward reward) {
        for (int i = 0; i < 4; i++) {
            editSignInMonthlyStash[i] = null;
        }
        if (reward == null) return;
        int slot = 0;
        for (RewardItem item : reward.getItems()) {
            if (slot >= 4) break;
            if (item == null || item.isEmpty()) continue;
            ItemStack stack = resolveSignInRewardStack(item);
            if (stack != null) {
                editSignInMonthlyStash[slot] = stack;
                slot++;
            }
        }
    }

    /**
     * 切换每月全局物品子模式（工作日 ↔ 周末）
     * <p>
     * 编辑缓冲区（激活子模式物品）与暂存（非激活子模式物品）整体互换；
     * 货币字段经动态绑定自动跟随 {@link #editSignInMonthlyWeekend}。
     *
     * @param weekend true=切换到周末默认奖励 / false=切换到工作日默认奖励
     */
    private void switchSignInMonthlySubMode(boolean weekend) {
        if (editSignInMonthlyWeekend == weekend) return;
        for (int i = 0; i < 4; i++) {
            ItemStack bufferStack = editSignInItemHandler.getStackInSlot(i);
            editSignInItemHandler.setStackInSlot(i, editSignInMonthlyStash[i]);
            editSignInMonthlyStash[i] = bufferStack;
        }
        editSignInMonthlyWeekend = weekend;
    }

    /**
     * 读取签到编辑缓冲区 4 槽快照（供每月全局保存时序列化激活子模式物品）
     *
     * @return 4 槽物品数组（空槽为 null）
     */
    private ItemStack[] signInBufferSnapshot() {
        ItemStack[] stacks = new ItemStack[4];
        for (int i = 0; i < 4; i++) {
            stacks[i] = editSignInItemHandler.getStackInSlot(i);
        }
        return stacks;
    }

    /**
     * 将奖励物品条目解析为 ItemStack（物品 ID + meta + NBT；v1.7.7 G5① NBT 还原口径）
     *
     * @param rewardItem 奖励物品条目
     * @return 物品栈（含 NBT），解析失败返回 null
     */
    private static ItemStack resolveSignInRewardStack(RewardItem rewardItem) {
        String[] parts = rewardItem.getItemId()
            .split(":");
        if (parts.length != 2) return null;
        Item itemObj = cpw.mods.fml.common.registry.GameRegistry.findItem(parts[0], parts[1]);
        if (itemObj == null) return null;
        ItemStack stack = new ItemStack(itemObj, Math.max(1, rewardItem.getAmount()), rewardItem.getMeta());
        net.minecraft.nbt.NBTTagCompound nbt = com.miaokatze.gtit.util.NbtBase64Util
            .nbtFromBase64(rewardItem.getNbtBase64());
        if (nbt != null) {
            // copy() 返回 NBTBase，需强转为 NBTTagCompound 再写入物品
            stack.setTagCompound((net.minecraft.nbt.NBTTagCompound) nbt.copy());
        }
        return stack;
    }

    /**
     * 构建签到编辑面板（v1.7.8 任务5+6 重构：单面板三模式）
     * <p>
     * 按 {@link #editSignInMode} 切换可见区：
     * <ul>
     * <li>{@code "tier"}/{@code "cumtier"} 阶梯模式：所需天数 + 货币类型/数量 + 4 物品槽，
     * 底部 保存/删除/取消/新增 四按钮（增删改三件套，照搬在线档位面板）；
     * 新增模式（{@link #editSignInOriginalDays}=-1，点击「+」空槽进入）无原档可定位，
     * 隐藏 保存/删除 仅留 取消/新增</li>
     * <li>{@code "monthly"} 每月全局模式：递增开关 + 编辑目标（工作日/周末子模式切换）+
     * 递增系数，货币字段随子模式动态绑定，物品槽经
     * {@link #switchSignInMonthlySubMode} 与暂存整体互换，底部 保存/取消</li>
     * </ul>
     * 货币/物品区双模式共用同一批组件：货币字段经 {@link StringValue.Dynamic} 按模式分支绑定，
     * 4 物品槽始终绑定 {@link #editSignInItemHandler}（每月模式的周末物品在暂存中切换进出）。
     *
     * @return 编辑覆盖层面板（{@link ParentWidget}）
     */
    private NekoDraggableEditPanel buildSignInEditPanel() {
        NekoDraggableEditPanel editPanel = new NekoDraggableEditPanel();
        editPanel.size(200, 180);
        // v1.7.7 G2 迁移为主面板内嵌 ParentWidget 覆盖层后无默认背景，需手动补上 MC 风格背景
        editPanel.background(GuiTextures.MC_BACKGROUND);
        editPanel.leftRel(0.5f)
            .topRel(0.5f)
            .anchorLeft(0.5f)
            .anchorTop(0.5f);
        editPanel.setEnabledIf(w -> currentEditOverlay == EditOverlayType.SIGNIN);

        int fieldHeight = 14;
        int labelWidth = 65;
        int fieldWidth = 120;

        // 标题（随模式/新增状态切换）
        editPanel.child(new TextWidget<>(IKey.dynamic(() -> {
            if ("monthly".equals(editSignInMode)) {
                return EnumChatFormatting.GOLD + "编辑每月签到全局配置";
            }
            String label = "cumtier".equals(editSignInMode) ? "累计" : "连续";
            return editSignInOriginalDays > 0
                ? EnumChatFormatting.GOLD + "编辑" + label + "阶梯（" + editSignInOriginalDays + " 天）"
                : EnumChatFormatting.GOLD + "新增" + label + "阶梯";
        })).top(5)
            .horizontalCenter());

        // ==================== 阶梯模式专属：所需天数 ====================

        TextWidget<?> daysLabel = new TextWidget<>(IKey.str(EnumChatFormatting.WHITE + "所需天数:"));
        daysLabel.left(8)
            .top(26);
        daysLabel.setEnabledIf(w -> !"monthly".equals(editSignInMode));
        editPanel.child(daysLabel);

        TextFieldWidget daysField = new TextFieldWidget()
            .value(new StringValue.Dynamic(() -> String.valueOf(editSignInDays), val -> {
                try {
                    editSignInDays = Integer.parseInt(val);
                } catch (NumberFormatException ignored) {}
            }))
            .setNumbers(1, Integer.MAX_VALUE);
        daysField.left(labelWidth)
            .top(24)
            .size(fieldWidth, fieldHeight);
        daysField.setEnabledIf(w -> !"monthly".equals(editSignInMode));
        // v1.7.19：TextFieldWidget 延迟到 PhantomItemSlot 之后添加（z-index 置顶，避免被物品槽覆盖）

        // ==================== 每月全局模式专属：递增开关 / 编辑目标 / 递增系数 ====================

        // 递增开关（v1.7.8 起默认 false=不递增；递增仅作用工作日/周末默认货币量，逐日覆盖天不递增）
        TextWidget<?> incToggleLabel = new TextWidget<>(IKey.str(EnumChatFormatting.WHITE + "递增开关:"));
        incToggleLabel.left(8)
            .top(26);
        incToggleLabel.setEnabledIf(w -> "monthly".equals(editSignInMode));
        editPanel.child(incToggleLabel);

        ButtonWidget<?> incToggle = new ButtonWidget<>().left(labelWidth)
            .top(24)
            .size(fieldWidth, fieldHeight)
            .background(NekoGuiTextures.TEXT_FIELD_BACKGROUND)
            .overlay(
                IKey.dynamic(
                    () -> editSignInIncrementEnabled ? EnumChatFormatting.GREEN + "启用" : EnumChatFormatting.RED + "停用"))
            .tooltipBuilder(t -> {
                t.addLine(IKey.str("点击切换每日默认奖励是否随连续天数递增"));
                t.addLine(IKey.str(EnumChatFormatting.GRAY + "递增仅作用工作日/周末默认货币量（逐日覆盖天不递增）"));
            })
            .tooltipAutoUpdate(true)
            .onMouseTapped(mouse -> {
                editSignInIncrementEnabled = !editSignInIncrementEnabled;
                return true;
            });
        incToggle.setEnabledIf(w -> "monthly".equals(editSignInMode));
        editPanel.child(incToggle);

        // 编辑目标（工作日/周末默认奖励子模式切换；货币字段与 4 物品槽随之整体切换）
        TextWidget<?> subModeLabel = new TextWidget<>(IKey.str(EnumChatFormatting.WHITE + "编辑目标:"));
        subModeLabel.left(8)
            .top(44);
        subModeLabel.setEnabledIf(w -> "monthly".equals(editSignInMode));
        editPanel.child(subModeLabel);

        ButtonWidget<?> subModeToggle = new ButtonWidget<>().left(labelWidth)
            .top(42)
            .size(fieldWidth, fieldHeight)
            .background(NekoGuiTextures.TEXT_FIELD_BACKGROUND)
            .overlay(
                IKey.dynamic(
                    () -> editSignInMonthlyWeekend ? EnumChatFormatting.AQUA + "周末默认奖励"
                        : EnumChatFormatting.YELLOW + "工作日默认奖励"))
            .tooltipBuilder(t -> {
                t.addLine(IKey.str("点击切换编辑工作日/周末默认奖励"));
                t.addLine(IKey.str(EnumChatFormatting.GRAY + "货币字段与 4 物品槽整体切换（切换不丢失内容）"));
            })
            .tooltipAutoUpdate(true)
            .onMouseTapped(mouse -> {
                switchSignInMonthlySubMode(!editSignInMonthlyWeekend);
                return true;
            });
        subModeToggle.setEnabledIf(w -> "monthly".equals(editSignInMode));
        editPanel.child(subModeToggle);

        // 递增系数（字符串暂存，保存时解析，非法回退 0）
        TextWidget<?> incLabel = new TextWidget<>(IKey.str(EnumChatFormatting.WHITE + "递增系数:"));
        incLabel.left(8)
            .top(62);
        incLabel.setEnabledIf(w -> "monthly".equals(editSignInMode));
        editPanel.child(incLabel);

        TextFieldWidget incField = new TextFieldWidget()
            .value(new StringValue.Dynamic(() -> editSignInIncrement, val -> editSignInIncrement = val))
            .setMaxLength(12);
        incField.left(labelWidth)
            .top(60)
            .size(fieldWidth, fieldHeight);
        incField.setEnabledIf(w -> "monthly".equals(editSignInMode));
        // v1.7.19：TextFieldWidget 延迟到 PhantomItemSlot 之后添加（z-index 置顶）

        // ==================== 公共区：货币 + 4 物品槽（每月模式随子模式分支绑定） ====================

        // 货币类型
        editPanel.child(
            new TextWidget<>(IKey.str(EnumChatFormatting.WHITE + "货币类型:")).left(8)
                .top(80));
        TextFieldWidget currencyField = new TextFieldWidget().value(new StringValue.Dynamic(() -> {
            if ("monthly".equals(editSignInMode)) {
                return editSignInMonthlyWeekend ? editSignInWeekendCurrency : editSignInWeekdayCurrency;
            }
            return editSignInCurrency;
        }, val -> {
            if ("monthly".equals(editSignInMode)) {
                if (editSignInMonthlyWeekend) {
                    editSignInWeekendCurrency = val;
                } else {
                    editSignInWeekdayCurrency = val;
                }
            } else {
                editSignInCurrency = val;
            }
        }))
            .setMaxLength(30);
        currencyField.left(labelWidth)
            .top(78)
            .size(fieldWidth, fieldHeight);
        // v1.7.19：TextFieldWidget 延迟到 PhantomItemSlot 之后添加（z-index 置顶）

        // 货币数量
        editPanel.child(
            new TextWidget<>(IKey.str(EnumChatFormatting.WHITE + "货币数量:")).left(8)
                .top(98));
        TextFieldWidget amountField = new TextFieldWidget().value(new StringValue.Dynamic(() -> {
            if ("monthly".equals(editSignInMode)) {
                return String.valueOf(editSignInMonthlyWeekend ? editSignInWeekendAmount : editSignInWeekdayAmount);
            }
            return String.valueOf(editSignInAmount);
        }, val -> {
            try {
                int parsed = Integer.parseInt(val);
                if ("monthly".equals(editSignInMode)) {
                    if (editSignInMonthlyWeekend) {
                        editSignInWeekendAmount = parsed;
                    } else {
                        editSignInWeekdayAmount = parsed;
                    }
                } else {
                    editSignInAmount = parsed;
                }
            } catch (NumberFormatException ignored) {}
        }))
            .setNumbers(0, Integer.MAX_VALUE);
        amountField.left(labelWidth)
            .top(96)
            .size(fieldWidth, fieldHeight);
        // v1.7.19：TextFieldWidget 延迟到 PhantomItemSlot 之后添加（z-index 置顶）

        // 物品奖励（4 槽，PhantomItemSlot 拖入配置；留空 = 无物品奖励）
        editPanel.child(
            new TextWidget<>(IKey.str(EnumChatFormatting.WHITE + "物品奖励:")).left(8)
                .top(118));
        for (int i = 0; i < 4; i++) {
            final int slotIndex = i;
            PhantomItemSlot slot = new PhantomItemSlot().slot(new ModularSlot(editSignInItemHandler, slotIndex));
            slot.left(labelWidth + slotIndex * 20)
                .top(114);
            editPanel.child(slot);
        }

        // v1.7.19：延迟添加的 TextFieldWidget（在所有 PhantomItemSlot 之后，确保渲染层位于物品槽之上）
        editPanel.child(daysField);
        editPanel.child(incField);
        editPanel.child(currencyField);
        editPanel.child(amountField);

        // ---- 阶梯模式：保存 / 删除 / 取消 / 新增（增删改三件套，照搬在线档位面板） ----
        ButtonWidget<?> saveBtn = new ButtonWidget<>().size(40, 16)
            .left(14)
            .bottom(8)
            .overlay(IKey.str("保存"))
            .onMouseTapped(mouse -> {
                saveSignInEdit("update");
                closeEditOverlay();
                return true;
            });
        // 新增模式（originalDays=-1）无原档可定位，隐藏保存/删除
        saveBtn.setEnabledIf(w -> !"monthly".equals(editSignInMode) && editSignInOriginalDays > 0);
        editPanel.child(saveBtn);

        ButtonWidget<?> deleteBtn = new ButtonWidget<>().size(40, 16)
            .left(62)
            .bottom(8)
            .overlay(IKey.str(EnumChatFormatting.RED + "删除"))
            .onMouseTapped(mouse -> {
                saveSignInEdit("remove");
                closeEditOverlay();
                return true;
            });
        deleteBtn.setEnabledIf(w -> !"monthly".equals(editSignInMode) && editSignInOriginalDays > 0);
        editPanel.child(deleteBtn);

        ButtonWidget<?> tierCancelBtn = new ButtonWidget<>().size(40, 16)
            .left(110)
            .bottom(8)
            .overlay(IKey.str("取消"))
            .onMouseTapped(mouse -> {
                closeEditOverlay();
                return true;
            });
        tierCancelBtn.setEnabledIf(w -> !"monthly".equals(editSignInMode));
        editPanel.child(tierCancelBtn);

        ButtonWidget<?> addBtn = new ButtonWidget<>().size(40, 16)
            .left(158)
            .bottom(8)
            .overlay(IKey.str(EnumChatFormatting.GREEN + "新增"))
            .tooltipBuilder(t -> t.addLine(IKey.str(EnumChatFormatting.GRAY + "以当前天数/奖励追加为新阶梯")))
            .tooltipAutoUpdate(true)
            .onMouseTapped(mouse -> {
                saveSignInEdit("add");
                closeEditOverlay();
                return true;
            });
        addBtn.setEnabledIf(w -> !"monthly".equals(editSignInMode));
        editPanel.child(addBtn);

        // ---- 每月全局模式：保存 / 取消 ----
        ButtonWidget<?> monthlySaveBtn = new ButtonWidget<>().size(50, 16)
            .left(30)
            .bottom(8)
            .overlay(IKey.str("保存"))
            .onMouseTapped(mouse -> {
                saveSignInEdit("update");
                closeEditOverlay();
                return true;
            });
        monthlySaveBtn.setEnabledIf(w -> "monthly".equals(editSignInMode));
        editPanel.child(monthlySaveBtn);

        ButtonWidget<?> monthlyCancelBtn = new ButtonWidget<>().size(50, 16)
            .right(30)
            .bottom(8)
            .overlay(IKey.str("取消"))
            .onMouseTapped(mouse -> {
                closeEditOverlay();
                return true;
            });
        monthlyCancelBtn.setEnabledIf(w -> "monthly".equals(editSignInMode));
        editPanel.child(monthlyCancelBtn);

        return editPanel;
    }

    /**
     * 保存签到编辑（客户端 → 服务端，v1.7.8 任务5+6 重构）
     * <p>
     * 按 {@link #editSignInMode} 分流：
     * <ul>
     * <li>阶梯模式（"tier"/"cumtier"）：序列化 {@code {operation, days, reward}}，
     * targetId = 模式 + ":" + 原天数（定位用；新增模式原天数=-1，服务端 add 忽略）；
     * operation=update 允许同时改天数，add 追加新档，remove 删除原档</li>
     * <li>每月全局模式（"monthly"）：序列化 {@code {incrementEnabled, consecutiveIncrement,
     * weekday, weekend}}，targetId="monthly"；激活子模式物品取编辑缓冲区快照，
     * 非激活子模式取暂存（{@link #editSignInMonthlyStash}）</li>
     * </ul>
     * 统一奖励模型由 {@link #buildSignInRewardJson} 序列化（与 {@link SignInReward#toJson()} 同构）。
     * 经 {@link com.miaokatze.gtit.trade.v2.NekoEditNetworkManager#sendSaveSignInReward} 发送。
     *
     * @param operation 操作类型：update / add / remove（monthly 模式忽略）
     */
    private void saveSignInEdit(String operation) {
        try {
            com.google.gson.JsonObject json = new com.google.gson.JsonObject();
            if ("monthly".equals(editSignInMode)) {
                // 每月全局：递增参数 + 工作日/周末默认奖励（字符串解析，非法回退 0）
                double increment;
                try {
                    increment = Double.parseDouble(editSignInIncrement);
                } catch (NumberFormatException e) {
                    increment = 0.0;
                }
                json.addProperty("incrementEnabled", editSignInIncrementEnabled);
                json.addProperty("consecutiveIncrement", increment);
                // 激活子模式物品在编辑缓冲区，非激活子模式物品在暂存
                ItemStack[] bufferSnap = signInBufferSnapshot();
                ItemStack[] weekdayStacks = editSignInMonthlyWeekend ? editSignInMonthlyStash : bufferSnap;
                ItemStack[] weekendStacks = editSignInMonthlyWeekend ? bufferSnap : editSignInMonthlyStash;
                json.add(
                    "weekday",
                    buildSignInRewardJson(editSignInWeekdayCurrency, editSignInWeekdayAmount, weekdayStacks));
                json.add(
                    "weekend",
                    buildSignInRewardJson(editSignInWeekendCurrency, editSignInWeekendAmount, weekendStacks));
                com.miaokatze.gtit.trade.v2.NekoEditNetworkManager.sendSaveSignInReward("monthly", json.toString());
            } else {
                // 连续/累计阶梯：增删改（days 字段携带新天数，targetId 用原天数定位）
                json.addProperty("operation", operation);
                json.addProperty("days", editSignInDays);
                json.add("reward", buildSignInRewardJson(editSignInCurrency, editSignInAmount, signInBufferSnapshot()));
                com.miaokatze.gtit.trade.v2.NekoEditNetworkManager
                    .sendSaveSignInReward(editSignInMode + ":" + editSignInOriginalDays, json.toString());
            }
        } catch (Exception e) {
            GTInterestingThing.LOG.error("[NekoEdit] 保存签到编辑失败", e);
        }
    }

    /**
     * 将货币 + 物品槽数组序列化为统一奖励模型 JSON（与 {@link SignInReward#toJson()} 同构：
     * {@code {currency, amount, items:[{item, amount, meta, nbt}]}}；空槽跳过，NBT 经 Base64 编码）
     *
     * @param currency 货币 ID（null 按空串）
     * @param amount   货币数量（&lt;0 按 0）
     * @param stacks   物品槽数组（null 元素/空槽跳过）
     * @return 奖励 JSON 对象
     */
    private static com.google.gson.JsonObject buildSignInRewardJson(String currency, int amount, ItemStack[] stacks) {
        com.google.gson.JsonObject reward = new com.google.gson.JsonObject();
        reward.addProperty("currency", currency == null ? "" : currency);
        reward.addProperty("amount", Math.max(0, amount));
        com.google.gson.JsonArray items = new com.google.gson.JsonArray();
        if (stacks != null) {
            for (ItemStack stack : stacks) {
                if (stack == null || stack.getItem() == null) continue;
                com.google.gson.JsonObject itemJson = new com.google.gson.JsonObject();
                itemJson.addProperty("item", net.minecraft.item.Item.itemRegistry.getNameForObject(stack.getItem()));
                itemJson.addProperty("amount", stack.stackSize);
                itemJson.addProperty("meta", stack.getItemDamage());
                // nbtToBase64 对 null 返回 null，统一归一空串防止 Gson 写出 JsonNull 导致解析异常
                String nbt = com.miaokatze.gtit.util.NbtBase64Util.nbtToBase64(stack.getTagCompound());
                itemJson.addProperty("nbt", nbt == null ? "" : nbt);
                items.add(itemJson);
            }
        }
        reward.add("items", items);
        return reward;
    }

    // ==================== 签到编辑：逐日覆盖面板（v1.7.8 任务6，SIGNIN_DAY 覆盖层） ====================

    /**
     * 构建逐日覆盖编辑面板（{@link EditOverlayType#SIGNIN_DAY} 覆盖层）
     * <p>
     * 编辑每月某一日号的覆盖奖励：货币类型/数量 + 4 物品槽（独立缓冲区
     * {@link #editSignInDayItemHandler}，{@link #openSignInDayEditor} 打开时按生效奖励本地预填）。
     * 底部 保存/清除覆盖/取消：清除覆盖即删除该日号覆盖，回退工作日/周末默认。
     * 覆盖按月内日号生效（每月该日均覆盖），覆盖天不参与递增。
     *
     * @return 编辑覆盖层面板（{@link ParentWidget}）
     */
    private NekoDraggableEditPanel buildSignInDayEditPanel() {
        NekoDraggableEditPanel editPanel = new NekoDraggableEditPanel();
        editPanel.size(200, 132);
        // v1.7.7 G2 迁移为主面板内嵌 ParentWidget 覆盖层后无默认背景，需手动补上 MC 风格背景
        editPanel.background(GuiTextures.MC_BACKGROUND);
        editPanel.leftRel(0.5f)
            .topRel(0.5f)
            .anchorLeft(0.5f)
            .anchorTop(0.5f);
        editPanel.setEnabledIf(w -> currentEditOverlay == EditOverlayType.SIGNIN_DAY);

        int fieldHeight = 14;
        int labelWidth = 65;
        int fieldWidth = 120;

        // 标题（目标日号；覆盖按月内日号生效）
        editPanel.child(
            new TextWidget<>(IKey.dynamic(() -> EnumChatFormatting.GOLD + "编辑每日奖励（每月 " + editSignInDay + " 日）")).top(5)
                .horizontalCenter());

        // 货币类型
        editPanel.child(
            new TextWidget<>(IKey.str(EnumChatFormatting.WHITE + "货币类型:")).left(8)
                .top(26));
        TextFieldWidget currencyField = new TextFieldWidget()
            .value(new StringValue.Dynamic(() -> editSignInCurrency, val -> editSignInCurrency = val))
            .setMaxLength(30);
        currencyField.left(labelWidth)
            .top(24)
            .size(fieldWidth, fieldHeight);
        // v1.7.19：TextFieldWidget 延迟到 PhantomItemSlot 之后添加（z-index 置顶）

        // 货币数量
        editPanel.child(
            new TextWidget<>(IKey.str(EnumChatFormatting.WHITE + "货币数量:")).left(8)
                .top(44));
        TextFieldWidget amountField = new TextFieldWidget()
            .value(new StringValue.Dynamic(() -> String.valueOf(editSignInAmount), val -> {
                try {
                    editSignInAmount = Integer.parseInt(val);
                } catch (NumberFormatException ignored) {}
            }))
            .setNumbers(0, Integer.MAX_VALUE);
        amountField.left(labelWidth)
            .top(42)
            .size(fieldWidth, fieldHeight);
        // v1.7.19：TextFieldWidget 延迟到 PhantomItemSlot 之后添加（z-index 置顶）

        // 物品奖励（4 槽，独立缓冲区；留空 = 无物品奖励）
        editPanel.child(
            new TextWidget<>(IKey.str(EnumChatFormatting.WHITE + "物品奖励:")).left(8)
                .top(64));
        for (int i = 0; i < 4; i++) {
            final int slotIndex = i;
            PhantomItemSlot slot = new PhantomItemSlot().slot(new ModularSlot(editSignInDayItemHandler, slotIndex));
            slot.left(labelWidth + slotIndex * 20)
                .top(60);
            editPanel.child(slot);
        }

        // v1.7.19：延迟添加的 TextFieldWidget（在所有 PhantomItemSlot 之后，确保渲染层位于物品槽之上）
        editPanel.child(currencyField);
        editPanel.child(amountField);

        // ---- 保存 / 清除覆盖 / 取消 ----
        editPanel.child(
            new ButtonWidget<>().size(50, 16)
                .left(14)
                .bottom(8)
                .overlay(IKey.str("保存"))
                .onMouseTapped(mouse -> {
                    saveSignInDayEdit("update");
                    closeEditOverlay();
                    return true;
                }));
        editPanel.child(
            new ButtonWidget<>().size(58, 16)
                .left(71)
                .bottom(8)
                .overlay(IKey.str(EnumChatFormatting.RED + "清除覆盖"))
                .tooltipBuilder(t -> t.addLine(IKey.str(EnumChatFormatting.GRAY + "删除该日号覆盖，回退工作日/周末默认")))
                .tooltipAutoUpdate(true)
                .onMouseTapped(mouse -> {
                    saveSignInDayEdit("remove");
                    closeEditOverlay();
                    return true;
                }));
        editPanel.child(
            new ButtonWidget<>().size(50, 16)
                .right(14)
                .bottom(8)
                .overlay(IKey.str("取消"))
                .onMouseTapped(mouse -> {
                    closeEditOverlay();
                    return true;
                }));

        return editPanel;
    }

    /**
     * 保存逐日覆盖编辑（客户端 → 服务端）
     * <p>
     * 序列化 {@code {operation, reward}}，targetId = "day:" + 月内日号；
     * operation=remove 时不携带 reward（服务端清除该日号覆盖，回退工作日/周末默认）。
     * 经 {@link com.miaokatze.gtit.trade.v2.NekoEditNetworkManager#sendSaveSignInReward} 发送。
     *
     * @param operation 操作类型：update / remove
     */
    private void saveSignInDayEdit(String operation) {
        try {
            com.google.gson.JsonObject json = new com.google.gson.JsonObject();
            json.addProperty("operation", operation);
            if (!"remove".equals(operation)) {
                ItemStack[] stacks = new ItemStack[4];
                for (int i = 0; i < 4; i++) {
                    stacks[i] = editSignInDayItemHandler.getStackInSlot(i);
                }
                json.add("reward", buildSignInRewardJson(editSignInCurrency, editSignInAmount, stacks));
            }
            com.miaokatze.gtit.trade.v2.NekoEditNetworkManager
                .sendSaveSignInReward("day:" + editSignInDay, json.toString());
        } catch (Exception e) {
            GTInterestingThing.LOG.error("[NekoEdit] 保存逐日覆盖编辑失败", e);
        }
    }

    // ==================== 编辑模式：每日在线档位编辑（v1.7.7 G5②） ====================

    /**
     * 打开在线档位编辑面板（客户端，编辑模式下点击在线档位行触发）
     * <p>
     * 从本地 {@code OnlineTimeConfig} 填充秒数/货币字段（预览口径，与在线页展示一致），
     * 并通过 {@link #editOnlineTargetSync} 通知服务端把档位物品奖励加载到编辑缓冲区
     * （PhantomItemSlot 自动同步回客户端显示）。
     *
     * @param tier 被点击的在线奖励档位
     */
    private void openOnlineTierEditor(com.miaokatze.gtit.signin.OnlineTimeRewardTier tier) {
        if (tier == null) return;
        editOnlineOriginalSeconds = tier.getRequiredSeconds();
        editOnlineSeconds = tier.getRequiredSeconds();
        editOnlineCurrency = tier.getCurrencyId() != null ? tier.getCurrencyId() : "neko";
        editOnlineAmount = tier.getCurrencyAmount();
        // 通知服务端加载该档位的物品奖励到编辑缓冲区
        if (editOnlineTargetSync != null) {
            editOnlineTargetSync.setValue(String.valueOf(editOnlineOriginalSeconds));
        }
        openEditOverlay(EditOverlayType.ONLINE_TIER);
    }

    /**
     * 服务端：加载在线档位物品奖励到编辑缓冲区（v1.7.7 G5②）
     * <p>
     * 解析目标秒数，从 {@code OnlineTimeConfig} 查找档位，
     * 有物品奖励则构建 ItemStack（含 NBT）放入 slot 0，否则清空。
     *
     * @param target 秒数字符串
     */
    private void loadOnlineTierIntoEditBuffer(String target) {
        try {
            int seconds = Integer.parseInt(target);
            editOnlineItemHandler.setStackInSlot(0, null);
            for (com.miaokatze.gtit.signin.OnlineTimeRewardTier tier : com.miaokatze.gtit.signin.OnlineTimeConfig
                .getTiers()) {
                if (tier.getRequiredSeconds() != seconds) continue;
                if (tier.hasItemReward()) {
                    String[] parts = tier.getItemRewardId()
                        .split(":");
                    if (parts.length == 2) {
                        net.minecraft.item.Item itemObj = cpw.mods.fml.common.registry.GameRegistry
                            .findItem(parts[0], parts[1]);
                        if (itemObj != null) {
                            ItemStack stack = new ItemStack(
                                itemObj,
                                Math.max(1, tier.getItemRewardAmount()),
                                tier.getItemRewardMeta());
                            net.minecraft.nbt.NBTTagCompound nbt = com.miaokatze.gtit.util.NbtBase64Util
                                .nbtFromBase64(tier.getItemNbt());
                            if (nbt != null) {
                                // v1.7.7 G5②：copy() 返回 NBTBase，需强转为 NBTTagCompound 再写入物品
                                stack.setTagCompound((net.minecraft.nbt.NBTTagCompound) nbt.copy());
                            }
                            editOnlineItemHandler.setStackInSlot(0, stack);
                        }
                    }
                }
                break;
            }
            GTInterestingThing.LOG.info("[NekoEdit] 已加载在线档位到编辑缓冲区: {}s", seconds);
        } catch (Exception e) {
            GTInterestingThing.LOG.error("[NekoEdit] 加载在线档位到编辑缓冲区失败: {}", target, e);
        }
    }

    /**
     * 构建在线档位编辑面板（v1.7.7 G5②）
     * <p>
     * 字段布局：所需秒数、货币 ID、货币数量、物品奖励 PhantomItemSlot。
     * 保存时支持更新/新增/删除三种操作。
     *
     * @return 编辑覆盖层面板（{@link ParentWidget}）
     */
    private NekoDraggableEditPanel buildOnlineTierEditPanel() {
        NekoDraggableEditPanel editPanel = new NekoDraggableEditPanel();
        editPanel.size(220, 150);
        // v1.7.7 G2 迁移为主面板内嵌 ParentWidget 覆盖层后无默认背景，需手动补上 MC 风格背景
        editPanel.background(GuiTextures.MC_BACKGROUND);
        editPanel.leftRel(0.5f)
            .topRel(0.5f)
            .anchorLeft(0.5f)
            .anchorTop(0.5f);
        editPanel.setEnabledIf(w -> currentEditOverlay == EditOverlayType.ONLINE_TIER);

        // 标题
        editPanel.child(
            new TextWidget<>(IKey.str(EnumChatFormatting.GOLD + "编辑在线档位")).top(5)
                .horizontalCenter());

        int fieldY = 24;
        int fieldHeight = 14;
        int labelWidth = 75;
        int fieldWidth = 120;
        int spacing = 18;

        // 所需秒数
        TextWidget<?> secondsLabel = new TextWidget<>(IKey.str(EnumChatFormatting.WHITE + "所需秒数:"));
        secondsLabel.left(8)
            .top(fieldY + 2);
        editPanel.child(secondsLabel);

        TextFieldWidget secondsField = new TextFieldWidget()
            .value(new StringValue.Dynamic(() -> String.valueOf(editOnlineSeconds), val -> {
                try {
                    editOnlineSeconds = Integer.parseInt(val);
                } catch (NumberFormatException ignored) {}
            }))
            .setNumbers(1, Integer.MAX_VALUE);
        secondsField.left(labelWidth)
            .top(fieldY)
            .size(fieldWidth, fieldHeight);
        // v1.7.19：TextFieldWidget 延迟到 PhantomItemSlot 之后添加（z-index 置顶）

        // 货币类型
        fieldY += spacing;
        TextWidget<?> currencyLabel = new TextWidget<>(IKey.str(EnumChatFormatting.WHITE + "货币类型:"));
        currencyLabel.left(8)
            .top(fieldY + 2);
        editPanel.child(currencyLabel);

        TextFieldWidget currencyField = new TextFieldWidget()
            .value(new StringValue.Dynamic(() -> editOnlineCurrency, val -> editOnlineCurrency = val))
            .setMaxLength(30);
        currencyField.left(labelWidth)
            .top(fieldY)
            .size(fieldWidth, fieldHeight);
        // v1.7.19：TextFieldWidget 延迟到 PhantomItemSlot 之后添加（z-index 置顶）

        // 货币数量
        fieldY += spacing;
        TextWidget<?> amountLabel = new TextWidget<>(IKey.str(EnumChatFormatting.WHITE + "货币数量:"));
        amountLabel.left(8)
            .top(fieldY + 2);
        editPanel.child(amountLabel);

        TextFieldWidget amountField = new TextFieldWidget()
            .value(new StringValue.Dynamic(() -> String.valueOf(editOnlineAmount), val -> {
                try {
                    editOnlineAmount = Integer.parseInt(val);
                } catch (NumberFormatException ignored) {}
            }))
            .setNumbers(0, Integer.MAX_VALUE);
        amountField.left(labelWidth)
            .top(fieldY)
            .size(fieldWidth, fieldHeight);
        // v1.7.19：TextFieldWidget 延迟到 PhantomItemSlot 之后添加（z-index 置顶）

        // 物品奖励槽（PhantomItemSlot 拖入配置；留空 = 无物品奖励）
        fieldY += spacing;
        TextWidget<?> itemLabel = new TextWidget<>(IKey.str(EnumChatFormatting.WHITE + "物品奖励:"));
        itemLabel.left(8)
            .top(fieldY + 2);
        editPanel.child(itemLabel);

        PhantomItemSlot itemSlot = new PhantomItemSlot().slot(new ModularSlot(editOnlineItemHandler, 0));
        itemSlot.left(labelWidth)
            .top(fieldY - 2);
        editPanel.child(itemSlot);

        // v1.7.19：延迟添加的 TextFieldWidget（在所有 PhantomItemSlot 之后，确保渲染层位于物品槽之上）
        editPanel.child(secondsField);
        editPanel.child(currencyField);
        editPanel.child(amountField);

        // 保存 / 删除 / 取消 / 新增按钮
        editPanel.child(
            new ButtonWidget<>().size(40, 16)
                .left(14)
                .bottom(8)
                .overlay(IKey.str("保存"))
                .onMouseTapped(mouse -> {
                    saveOnlineTierEdit("update");
                    closeEditOverlay();
                    return true;
                }));
        editPanel.child(
            new ButtonWidget<>().size(40, 16)
                .left(62)
                .bottom(8)
                .overlay(IKey.str(EnumChatFormatting.RED + "删除"))
                .onMouseTapped(mouse -> {
                    saveOnlineTierEdit("remove");
                    closeEditOverlay();
                    return true;
                }));
        editPanel.child(
            new ButtonWidget<>().size(40, 16)
                .left(110)
                .bottom(8)
                .overlay(IKey.str("取消"))
                .onMouseTapped(mouse -> {
                    closeEditOverlay();
                    return true;
                }));
        editPanel.child(
            new ButtonWidget<>().size(40, 16)
                .left(158)
                .bottom(8)
                .overlay(IKey.str(EnumChatFormatting.GREEN + "新增"))
                .onMouseTapped(mouse -> {
                    saveOnlineTierEdit("add");
                    closeEditOverlay();
                    return true;
                }));

        return editPanel;
    }

    /**
     * 保存在线档位编辑（客户端 → 服务端，v1.7.7 G5②）
     * <p>
     * 序列化 {@code {operation, seconds, currency, amount, item, itemAmount, itemMeta, itemNbt}}
     * 经 {@link com.miaokatze.gtit.trade.v2.NekoEditNetworkManager#sendSaveOnlineTier} 发送。
     * <p>
     * <b>v1.7.7 G5①</b>：保存物品奖励时一并序列化 NBT。
     *
     * @param operation 操作类型：update / add / remove
     */
    private void saveOnlineTierEdit(String operation) {
        try {
            com.google.gson.JsonObject json = new com.google.gson.JsonObject();
            json.addProperty("operation", operation);
            json.addProperty("seconds", editOnlineSeconds);

            ItemStack stack = editOnlineItemHandler.getStackInSlot(0);
            if (stack != null && stack.getItem() != null) {
                json.addProperty("item", net.minecraft.item.Item.itemRegistry.getNameForObject(stack.getItem()));
                json.addProperty("itemAmount", stack.stackSize);
                json.addProperty("itemMeta", stack.getItemDamage());
                json.addProperty("itemNbt", com.miaokatze.gtit.util.NbtBase64Util.nbtToBase64(stack.getTagCompound()));
            } else {
                json.addProperty("item", "");
                json.addProperty("itemAmount", 0);
                json.addProperty("itemMeta", 0);
                json.addProperty("itemNbt", "");
            }

            json.addProperty("currency", editOnlineCurrency);
            json.addProperty("amount", editOnlineAmount);

            com.miaokatze.gtit.trade.v2.NekoEditNetworkManager
                .sendSaveOnlineTier(String.valueOf(editOnlineOriginalSeconds), json.toString());
        } catch (Exception e) {
            GTInterestingThing.LOG.error("[NekoEdit] 保存在线档位编辑失败", e);
        }
    }

    // ==================== 编辑模式：祝福预设编辑（v1.7.6 G5） ====================

    /**
     * 打开祝福预设编辑面板（客户端，编辑模式下点击邮件页 sub-tab 列尾「祝福预设」按钮触发）
     * <p>
     * 默认打开生日模板目标；面板内「< / >」按钮可循环切换
     * 生日 → 节日 1..N → 生日。字段从本地 {@code BlessingConfig} 填充
     * （预览口径，与签到编辑一致），附件物品经 {@link #editBlessingTargetSync}
     * 通知服务端加载到编辑缓冲区（PhantomItemSlot 自动同步回客户端显示）。
     *
     * @param target 初始目标标识（"birthday" / "festival:&lt;index&gt;"）
     */
    private void openBlessingEditor(String target) {
        fillBlessingFieldsFromConfig(target);
        // 通知服务端加载该目标的附件物品到编辑缓冲区（用归一化后的目标，索引越界时已回退 birthday）
        if (editBlessingTargetSync != null) {
            editBlessingTargetSync.setValue(editBlessingTarget);
        }
        openEditOverlay(EditOverlayType.BLESSING);
    }

    /**
     * 循环切换祝福编辑目标（客户端，面板内「< / >」按钮）
     * <p>
     * 目标序列：birthday → festival:0 → festival:1 → … → festival:N-1 → birthday。
     * 切换不会自动保存当前修改（与池/page 切换口径一致），tooltip 已提示先保存。
     *
     * @param delta +1 = 下一个，-1 = 上一个
     */
    private void cycleBlessingTarget(int delta) {
        java.util.List<String> targets = new java.util.ArrayList<>();
        targets.add("birthday");
        int festivalCount = com.miaokatze.gtit.mail.BlessingConfig.getFestivals()
            .size();
        for (int i = 0; i < festivalCount; i++) {
            targets.add("festival:" + i);
        }
        int index = targets.indexOf(editBlessingTarget);
        if (index < 0) index = 0;
        index = Math.floorMod(index + delta, targets.size());
        openBlessingEditor(targets.get(index));
    }

    /**
     * 从本地 {@code BlessingConfig} 填充祝福编辑字段（客户端预览口径）
     * <p>
     * 发件人字段每次从配置重读；节日目标额外填充名称/触发日期。
     * 目标非法时回退生日模板，防配置热改后索引越界。
     *
     * @param target 目标标识（"birthday" / "festival:&lt;index&gt;"）
     */
    private void fillBlessingFieldsFromConfig(String target) {
        editBlessingSender = com.miaokatze.gtit.mail.BlessingConfig.getSender();
        if (target != null && target.startsWith("festival:")) {
            int index;
            try {
                index = Integer.parseInt(target.substring("festival:".length()));
            } catch (NumberFormatException e) {
                index = -1;
            }
            java.util.List<com.miaokatze.gtit.mail.BlessingConfig.FestivalBlessing> festivals = com.miaokatze.gtit.mail.BlessingConfig
                .getFestivals();
            if (index >= 0 && index < festivals.size()) {
                com.miaokatze.gtit.mail.BlessingConfig.FestivalBlessing festival = festivals.get(index);
                editBlessingTarget = target;
                editBlessingName = festival.name;
                editBlessingMonthDay = festival.monthDay;
                editBlessingTitle = festival.title;
                editBlessingContent = festival.content;
                editBlessingCurrency = festival.currency;
                editBlessingCurrencyAmount = festival.currencyAmount;
                return;
            }
            // 索引越界（配置被外部热改）：回退生日模板
            target = "birthday";
        }
        com.miaokatze.gtit.mail.BlessingConfig.BirthdayBlessing birthday = com.miaokatze.gtit.mail.BlessingConfig
            .getBirthday();
        editBlessingTarget = "birthday";
        editBlessingName = "";
        editBlessingMonthDay = "";
        editBlessingTitle = birthday.title;
        editBlessingContent = birthday.content;
        editBlessingCurrency = birthday.currency;
        editBlessingCurrencyAmount = birthday.currencyAmount;
    }

    /**
     * 服务端：加载祝福目标的附件物品到编辑缓冲区
     * <p>
     * 解析 "birthday" / "festival:&lt;index&gt;" 目标标识，从 {@code BlessingConfig}
     * 取配置附件（不含猫猫币——币由货币字段表达）放入 slot 0-4，多余槽位清空。
     *
     * @param target 目标标识
     */
    private void loadBlessingIntoEditBuffer(String target) {
        try {
            // 先清空全部槽位（目标切换/物品减少时防残留）
            for (int i = 0; i < BLESSING_ITEM_SLOTS; i++) {
                editBlessingItemHandler.setStackInSlot(i, null);
            }
            java.util.List<com.miaokatze.gtit.mail.BlessingConfig.BlessingItem> specs;
            if ("birthday".equals(target)) {
                specs = com.miaokatze.gtit.mail.BlessingConfig.getBirthday().items;
            } else if (target != null && target.startsWith("festival:")) {
                int index = Integer.parseInt(target.substring("festival:".length()));
                java.util.List<com.miaokatze.gtit.mail.BlessingConfig.FestivalBlessing> festivals = com.miaokatze.gtit.mail.BlessingConfig
                    .getFestivals();
                if (index < 0 || index >= festivals.size()) return;
                specs = festivals.get(index).items;
            } else {
                return;
            }
            int slot = 0;
            for (com.miaokatze.gtit.mail.BlessingConfig.BlessingItem spec : specs) {
                if (spec == null || slot >= BLESSING_ITEM_SLOTS) continue;
                ItemStack stack = spec.toItemStack();
                if (stack != null) {
                    editBlessingItemHandler.setStackInSlot(slot++, stack);
                }
            }
            GTInterestingThing.LOG.info("[NekoEdit] 已加载祝福预设到编辑缓冲区: {}", target);
        } catch (Exception e) {
            GTInterestingThing.LOG.error("[NekoEdit] 加载祝福预设到编辑缓冲区失败: {}", target, e);
        }
    }

    /**
     * 当前祝福目标的展示名（面板标题/切换行用）
     */
    private String blessingTargetDisplayName() {
        if (editBlessingTarget != null && editBlessingTarget.startsWith("festival:")) {
            return "节日：" + editBlessingName + "（" + editBlessingMonthDay + "）";
        }
        return "生日模板";
    }

    /**
     * 构建祝福预设编辑面板
     * <p>
     * 单面板结构：标题（含当前目标名）→ 目标切换行（< 目标 >）→ 发件人 →
     * 名称/触发日期（仅节日目标可见）→ 标题 → 正文 → 货币类型/数量 →
     * 附件物品槽（{@value #BLESSING_ITEM_SLOTS} 格 PhantomItemSlot）→ 保存/取消。
     *
     * @return 编辑覆盖层面板（{@link ParentWidget}）
     */
    private NekoDraggableEditPanel buildBlessingEditPanel() {
        NekoDraggableEditPanel editPanel = new NekoDraggableEditPanel();
        editPanel.size(210, 205);
        // v1.7.7 G2 迁移为主面板内嵌 ParentWidget 覆盖层后无默认背景，需手动补上 MC 风格背景
        editPanel.background(GuiTextures.MC_BACKGROUND);
        editPanel.leftRel(0.5f)
            .topRel(0.5f)
            .anchorLeft(0.5f)
            .anchorTop(0.5f);
        editPanel.setEnabledIf(w -> currentEditOverlay == EditOverlayType.BLESSING);

        // 标题（随目标切换）
        editPanel.child(
            new TextWidget<>(IKey.dynamic(() -> EnumChatFormatting.GOLD + "编辑祝福预设 - " + blessingTargetDisplayName()))
                .top(5)
                .horizontalCenter());

        // ---- 目标切换行：< 目标名 > ----
        editPanel.child(
            new ButtonWidget<>().size(16, 14)
                .left(30)
                .top(16)
                .overlay(IKey.str("<"))
                .tooltipBuilder(t -> {
                    t.addLine(IKey.str("上一个预设"));
                    t.addLine(IKey.str(EnumChatFormatting.GRAY + "切换前请先保存当前修改"));
                })
                .tooltipAutoUpdate(true)
                .onMouseTapped(mouse -> {
                    if (mouse == 0) {
                        cycleBlessingTarget(-1);
                        return true;
                    }
                    return false;
                }));
        editPanel.child(
            new TextWidget<>(IKey.dynamic(this::blessingTargetDisplayName)).left(50)
                .top(19)
                .size(110, 9)
                .textAlign(com.cleanroommc.modularui.utils.Alignment.Center)
                .scale(0.8f)
                .shadow(false));
        editPanel.child(
            new ButtonWidget<>().size(16, 14)
                .left(164)
                .top(16)
                .overlay(IKey.str(">"))
                .tooltipBuilder(t -> {
                    t.addLine(IKey.str("下一个预设"));
                    t.addLine(IKey.str(EnumChatFormatting.GRAY + "切换前请先保存当前修改"));
                })
                .tooltipAutoUpdate(true)
                .onMouseTapped(mouse -> {
                    if (mouse == 0) {
                        cycleBlessingTarget(1);
                        return true;
                    }
                    return false;
                }));

        int labelWidth = 58;
        int fieldWidth = 138;
        int fieldHeight = 14;
        int fieldY = 34;
        int spacing = 17;

        // ---- 发件人（全目标共用，随保存一并提交）----
        editPanel.child(
            new TextWidget<>(IKey.str("发件人:")).left(8)
                .top(fieldY + 2));
        TextFieldWidget senderField = new TextFieldWidget()
            .value(new StringValue.Dynamic(() -> editBlessingSender, val -> editBlessingSender = val))
            .setMaxLength(30);
        senderField.left(labelWidth)
            .top(fieldY)
            .size(fieldWidth, fieldHeight);
        // v1.7.19：TextFieldWidget 延迟到 PhantomItemSlot 之后添加（z-index 置顶）

        // ---- 节日名称（仅节日目标可见）----
        fieldY += spacing;
        TextWidget<?> nameLabel = new TextWidget<>(IKey.str("节日名称:"));
        nameLabel.left(8)
            .top(fieldY + 2);
        nameLabel.setEnabledIf(w -> editBlessingTarget != null && editBlessingTarget.startsWith("festival:"));
        editPanel.child(nameLabel);
        TextFieldWidget nameField = new TextFieldWidget()
            .value(new StringValue.Dynamic(() -> editBlessingName, val -> editBlessingName = val))
            .setMaxLength(20);
        nameField.left(labelWidth)
            .top(fieldY)
            .size(fieldWidth, fieldHeight);
        nameField.setEnabledIf(w -> editBlessingTarget != null && editBlessingTarget.startsWith("festival:"));
        // v1.7.19：TextFieldWidget 延迟到 PhantomItemSlot 之后添加（z-index 置顶）

        // ---- 触发日期 MM-dd（仅节日目标可见）----
        fieldY += spacing;
        TextWidget<?> dateLabel = new TextWidget<>(IKey.str("触发日期:"));
        dateLabel.left(8)
            .top(fieldY + 2);
        dateLabel.setEnabledIf(w -> editBlessingTarget != null && editBlessingTarget.startsWith("festival:"));
        editPanel.child(dateLabel);
        TextFieldWidget dateField = new TextFieldWidget()
            .value(new StringValue.Dynamic(() -> editBlessingMonthDay, val -> editBlessingMonthDay = val))
            .setMaxLength(5);
        dateField.left(labelWidth)
            .top(fieldY)
            .size(fieldWidth, fieldHeight);
        dateField.tooltipBuilder(t -> {
            t.addLine(IKey.str("固定公历日期，格式 MM-dd（如 01-01）"));
            t.addLine(IKey.str(EnumChatFormatting.GRAY + "农历节日请按当年农历自行换算"));
        });
        dateField.tooltipAutoUpdate(true);
        dateField.setEnabledIf(w -> editBlessingTarget != null && editBlessingTarget.startsWith("festival:"));
        // v1.7.19：TextFieldWidget 延迟到 PhantomItemSlot 之后添加（z-index 置顶）

        // ---- 邮件标题 ----
        fieldY += spacing;
        editPanel.child(
            new TextWidget<>(IKey.str("邮件标题:")).left(8)
                .top(fieldY + 2));
        TextFieldWidget titleField = new TextFieldWidget()
            .value(new StringValue.Dynamic(() -> editBlessingTitle, val -> editBlessingTitle = val))
            .setMaxLength(60);
        titleField.left(labelWidth)
            .top(fieldY)
            .size(fieldWidth, fieldHeight);
        // v1.7.19：TextFieldWidget 延迟到 PhantomItemSlot 之后添加（z-index 置顶）

        // ---- 邮件正文 ----
        fieldY += spacing;
        editPanel.child(
            new TextWidget<>(IKey.str("邮件正文:")).left(8)
                .top(fieldY + 2));
        TextFieldWidget contentField = new TextFieldWidget()
            .value(new StringValue.Dynamic(() -> editBlessingContent, val -> editBlessingContent = val))
            .setMaxLength(200);
        contentField.left(labelWidth)
            .top(fieldY)
            .size(fieldWidth, fieldHeight);
        // v1.7.19：TextFieldWidget 延迟到 PhantomItemSlot 之后添加（z-index 置顶）

        // ---- 猫猫币类型/数量 ----
        fieldY += spacing;
        editPanel.child(
            new TextWidget<>(IKey.str("猫猫币:")).left(8)
                .top(fieldY + 2));
        TextFieldWidget currencyField = new TextFieldWidget()
            .value(new StringValue.Dynamic(() -> editBlessingCurrency, val -> editBlessingCurrency = val))
            .setMaxLength(30);
        currencyField.left(labelWidth)
            .top(fieldY)
            .size(86, fieldHeight);
        currencyField.tooltipBuilder(t -> {
            t.addLine(IKey.str("货币 ID：neko / shimmeringNeko"));
            t.addLine(IKey.str(EnumChatFormatting.GRAY + "留空 = 无猫猫币附件"));
        });
        currencyField.tooltipAutoUpdate(true);
        // v1.7.19：TextFieldWidget 延迟到 PhantomItemSlot 之后添加（z-index 置顶）
        TextFieldWidget currencyAmountField = new TextFieldWidget()
            .value(new StringValue.Dynamic(() -> String.valueOf(editBlessingCurrencyAmount), val -> {
                try {
                    editBlessingCurrencyAmount = Integer.parseInt(val);
                } catch (NumberFormatException ignored) {}
            }))
            .setNumbers(0, Integer.MAX_VALUE);
        currencyAmountField.left(labelWidth + 90)
            .top(fieldY)
            .size(48, fieldHeight);
        currencyAmountField.tooltipBuilder(t -> t.addLine(IKey.str("数量（作为附件物品发放）")));
        currencyAmountField.tooltipAutoUpdate(true);
        // v1.7.19：TextFieldWidget 延迟到 PhantomItemSlot 之后添加（z-index 置顶）

        // ---- 附件物品槽（PhantomItemSlot 拖入配置；留空 = 无物品附件）----
        fieldY += spacing + 2;
        editPanel.child(
            new TextWidget<>(IKey.str("附件物品:")).left(8)
                .top(fieldY + 2));
        for (int i = 0; i < BLESSING_ITEM_SLOTS; i++) {
            PhantomItemSlot slot = new PhantomItemSlot().slot(new ModularSlot(editBlessingItemHandler, i));
            slot.left(labelWidth + i * 18)
                .top(fieldY - 2);
            editPanel.child(slot);
        }

        // v1.7.19：延迟添加的 TextFieldWidget（在所有 PhantomItemSlot 之后，确保渲染层位于物品槽之上）
        editPanel.child(senderField);
        editPanel.child(nameField);
        editPanel.child(dateField);
        editPanel.child(titleField);
        editPanel.child(contentField);
        editPanel.child(currencyField);
        editPanel.child(currencyAmountField);

        // ---- 保存 / 取消按钮 ----
        editPanel.child(
            new ButtonWidget<>().size(50, 16)
                .left(40)
                .bottom(8)
                .overlay(IKey.str("保存"))
                .onMouseTapped(mouse -> {
                    saveBlessingEdit();
                    closeEditOverlay();
                    return true;
                }));
        editPanel.child(
            new ButtonWidget<>().size(50, 16)
                .right(40)
                .bottom(8)
                .overlay(IKey.str("取消"))
                .onMouseTapped(mouse -> {
                    closeEditOverlay();
                    return true;
                }));

        return editPanel;
    }

    /**
     * 保存祝福预设编辑（客户端 → 服务端）
     * <p>
     * 先提交发件人（"sender" 目标），再提交当前祝福目标（"birthday"/"festival:&lt;index&gt;"）：
     * 附件物品取自 PhantomItemSlot 缓冲区序列化为 items 数组
     * {@code [{"item":"modid:name","meta":0,"amount":1}]}；空槽跳过。
     * 经 {@link com.miaokatze.gtit.trade.v2.NekoEditNetworkManager#sendSaveBlessing} 发送。
     */
    private void saveBlessingEdit() {
        try {
            // ---- 1. 发件人（随任意保存一并提交）----
            com.google.gson.JsonObject senderJson = new com.google.gson.JsonObject();
            senderJson.addProperty("sender", editBlessingSender);
            com.miaokatze.gtit.trade.v2.NekoEditNetworkManager.sendSaveBlessing("sender", senderJson.toString());

            // ---- 2. 当前祝福目标 ----
            com.google.gson.JsonObject json = new com.google.gson.JsonObject();
            if (editBlessingTarget != null && editBlessingTarget.startsWith("festival:")) {
                json.addProperty("name", editBlessingName);
                json.addProperty("monthDay", editBlessingMonthDay);
            }
            json.addProperty("title", editBlessingTitle);
            json.addProperty("content", editBlessingContent);
            json.addProperty("currency", editBlessingCurrency);
            json.addProperty("currencyAmount", editBlessingCurrencyAmount);
            com.google.gson.JsonArray items = new com.google.gson.JsonArray();
            for (int i = 0; i < BLESSING_ITEM_SLOTS; i++) {
                ItemStack stack = editBlessingItemHandler.getStackInSlot(i);
                if (stack == null || stack.getItem() == null) continue;
                com.google.gson.JsonObject itemJson = new com.google.gson.JsonObject();
                itemJson.addProperty("item", net.minecraft.item.Item.itemRegistry.getNameForObject(stack.getItem()));
                itemJson.addProperty("meta", stack.getItemDamage());
                itemJson.addProperty("amount", stack.stackSize);
                items.add(itemJson);
            }
            json.add("items", items);
            com.miaokatze.gtit.trade.v2.NekoEditNetworkManager
                .sendSaveBlessing(editBlessingTarget == null ? "birthday" : editBlessingTarget, json.toString());
        } catch (Exception e) {
            GTInterestingThing.LOG.error("[NekoEdit] 保存祝福预设编辑失败", e);
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
        openEditOverlay(EditOverlayType.LOTTERY);
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
     * @return 编辑覆盖层面板（{@link ParentWidget}）
     */
    private NekoDraggableEditPanel buildLotteryEditPanel() {
        NekoDraggableEditPanel editPanel = new NekoDraggableEditPanel();
        editPanel.size(200, 160);
        // v1.7.7 G2 迁移为主面板内嵌 ParentWidget 覆盖层后无默认背景，需手动补上 MC 风格背景
        editPanel.background(GuiTextures.MC_BACKGROUND);
        editPanel.leftRel(0.5f)
            .topRel(0.5f)
            .anchorLeft(0.5f)
            .anchorTop(0.5f);
        editPanel.setEnabledIf(w -> currentEditOverlay == EditOverlayType.LOTTERY);

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
            new TextWidget<>(IKey.str(EnumChatFormatting.WHITE + "货币ID:")).left(8)
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
        // v1.7.19：TextFieldWidget 延迟到 PhantomItemSlot 之后添加（z-index 置顶）

        // ---- 物品奖品（PhantomItemSlot 拖入配置，支持 NBT）----
        fieldY += 17;
        editPanel.child(
            new TextWidget<>(IKey.str(EnumChatFormatting.WHITE + "物品:")).left(8)
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

        // v1.7.19：延迟添加的 TextFieldWidget（在所有 PhantomItemSlot 之后，确保渲染层位于物品槽之上）
        editPanel.child(currencyField);

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
            new TextWidget<>(IKey.str(EnumChatFormatting.WHITE + "最小数量:")).left(8)
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
            new TextWidget<>(IKey.str(EnumChatFormatting.WHITE + "最大数量:")).left(8)
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
            new TextWidget<>(IKey.str(EnumChatFormatting.WHITE + "权重:")).left(8)
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
            new TextWidget<>(IKey.str(EnumChatFormatting.WHITE + "稀有度:")).left(8)
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
                    closeEditOverlay();
                    return true;
                }));
        editPanel.child(
            new ButtonWidget<>().size(50, 16)
                .right(30)
                .bottom(8)
                .overlay(IKey.str("取消"))
                .onMouseTapped(mouse -> {
                    closeEditOverlay();
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

    // ==================== 抽奖卡池编辑（v1.7.6 G2①） ====================

    /**
     * 按索引取客户端缓存的卡池摘要（越界返回 null）
     * <p>
     * 仅供池标签列按钮每帧动态求值（图标/选中态/显隐），纯客户端轻量读。
     *
     * @param index 池在 {@link LotteryClientData#getPools()} 中的序号
     * @return 池摘要，不存在返回 null
     */
    private LotteryClientData.PoolSummary poolAt(int index) {
        List<LotteryClientData.PoolSummary> pools = LotteryClientData.getPools();
        return index >= 0 && index < pools.size() ? pools.get(index) : null;
    }

    /**
     * 打开抽奖卡池编辑面板（客户端，编辑模式下 Shift+点击池标签触发）
     * <p>
     * 数值字段（名字/保底）从客户端缓存 {@link LotteryClientData} 的池摘要直接填充
     * （与同步包同源）；图标与消耗需求物品经 {@link #editPoolTargetSync} 通知服务端
     * 从权威配置加载到编辑缓冲区（PhantomItemSlot 自动同步回客户端显示，含 NBT）。
     *
     * @param pool 被点击的卡池摘要
     */
    private void openLotteryPoolEditor(LotteryClientData.PoolSummary pool) {
        if (pool == null || pool.id == null || pool.id.isEmpty()) return;
        editPoolIsNew = false;
        // 卡池 ID 仅定位用，编辑面板禁止修改（防保底记录/中奖历史悬空，见计划风险点）
        editPoolId = pool.id;
        editPoolName = pool.name != null ? pool.name : "";
        editPoolPityEnabled = pool.pityEnabled;
        editPoolSoftPity = pool.softPityThreshold;
        editPoolHardPity = pool.hardPityThreshold;
        editPoolGuaranteedRarity = pool.guaranteedRarity != null && !pool.guaranteedRarity.isEmpty()
            ? pool.guaranteedRarity
            : "EPIC";
        // 通知服务端加载该池的图标/消耗需求物品到编辑缓冲区
        if (editPoolTargetSync != null) {
            editPoolTargetSync.setValue(pool.id);
        }
        openEditOverlay(EditOverlayType.LOTTERY_POOL);
    }

    /**
     * 打开新建卡池编辑面板（客户端，编辑模式下点击池标签列尾「+」按钮触发）
     * <p>
     * 字段全部置默认值（保底启用、软保底 30、硬保底 50、EPIC 保证）；
     * 通知服务端清空编辑缓冲区（{@link #POOL_TARGET_NEW} 标记）。
     * 保存时走 {@code NekoEditNetworkManager.sendCreateLotteryPool}（服务端校验 id 合法性/唯一性）。
     */
    private void openNewLotteryPoolEditor() {
        editPoolIsNew = true;
        editPoolId = "";
        editPoolName = "";
        editPoolPityEnabled = true;
        editPoolSoftPity = 30;
        editPoolHardPity = 50;
        editPoolGuaranteedRarity = "EPIC";
        // 通知服务端清空编辑缓冲区（新建模式无既有物品可加载）
        if (editPoolTargetSync != null) {
            editPoolTargetSync.setValue(POOL_TARGET_NEW);
        }
        openEditOverlay(EditOverlayType.LOTTERY_POOL);
    }

    /**
     * 服务端：加载卡池图标/消耗需求物品到编辑缓冲区
     * <p>
     * slot 0 = page 图标（数量固定 1，含 NBT），slot 1-4 = 消耗需求物品（stackSize=单次抽取消耗量）。
     * {@link #POOL_TARGET_NEW}（新建模式）或查找失败时仅清空缓冲区。
     *
     * @param target 池 id 或 {@link #POOL_TARGET_NEW}
     */
    private void loadPoolIntoEditBuffer(String target) {
        try {
            // 先整体清空（slot 0=图标，slot 1-4=消耗需求）
            for (int i = 0; i < editPoolItemHandler.getSlots(); i++) {
                editPoolItemHandler.setStackInSlot(i, null);
            }
            if (POOL_TARGET_NEW.equals(target)) return;
            com.miaokatze.gtit.lottery.LotteryPool pool = com.miaokatze.gtit.lottery.LotteryManager.INSTANCE
                .getPool(target);
            if (pool == null) return;
            // page 图标（数量固定 1，展示用）
            ItemStack icon = pool.toIconItemStack();
            if (icon != null) {
                editPoolItemHandler.setStackInSlot(0, icon);
            }
            // 消耗需求物品（stackSize=单次抽取消耗量；槽位有限，超出截断）
            int slot = 1;
            for (NekoBigItemStack cost : pool.getCostItems()) {
                if (slot > POOL_COST_SLOTS) break;
                if (cost == null || cost.getBaseStack() == null || cost.getStackSize() <= 0) continue;
                ItemStack stack = cost.getBaseStack()
                    .copy();
                stack.stackSize = cost.getStackSize();
                editPoolItemHandler.setStackInSlot(slot++, stack);
            }
            GTInterestingThing.LOG.info("[NekoEdit] 已加载抽奖卡池到编辑缓冲区: {}", target);
        } catch (Exception e) {
            GTInterestingThing.LOG.error("[NekoEdit] 加载抽奖卡池到编辑缓冲区失败: {}", target, e);
        }
    }

    /**
     * 构建抽奖卡池编辑面板（v1.7.6 G2①）
     * <p>
     * 字段布局：卡池 ID（新建可编辑/现有只读）→ 名称 → page 图标 PhantomItemSlot
     * → 消耗需求 PhantomItemSlot×4（货币物品=团队钱包扣、普通物品=机器输入槽扣）
     * → 保底启用开关 → 软/硬保底阈值 → 保证稀有度（点击循环）。
     * 保存时按 {@link #editPoolIsNew} 分流：新建走
     * {@link com.miaokatze.gtit.trade.v2.NekoEditNetworkManager#sendCreateLotteryPool}，
     * 现有池走 {@link com.miaokatze.gtit.trade.v2.NekoEditNetworkManager#sendSaveLotteryPool}
     * （服务端 {@code NekoEditActionHandler} 落盘 + 热重载 + 广播 {@code LotterySyncPacket} 刷新池标签列）。
     *
     * @return 编辑覆盖层面板（{@link ParentWidget}）
     */
    private NekoDraggableEditPanel buildLotteryPoolEditPanel() {
        NekoDraggableEditPanel editPanel = new NekoDraggableEditPanel();
        editPanel.size(210, 205);
        // v1.7.7 G2 迁移为主面板内嵌 ParentWidget 覆盖层后无默认背景，需手动补上 MC 风格背景
        editPanel.background(GuiTextures.MC_BACKGROUND);
        editPanel.leftRel(0.5f)
            .topRel(0.5f)
            .anchorLeft(0.5f)
            .anchorTop(0.5f);
        editPanel.setEnabledIf(w -> currentEditOverlay == EditOverlayType.LOTTERY_POOL);

        // 标题（新建 / 编辑 + 池 id 动态切换）
        editPanel.child(
            new TextWidget<>(
                IKey.dynamic(() -> EnumChatFormatting.GOLD + (editPoolIsNew ? "新建抽奖卡池" : "编辑抽奖卡池（" + editPoolId + "）")))
                    .top(5)
                    .horizontalCenter());

        int fieldY = 22;
        int fieldHeight = 14;
        int labelWidth = 62;
        int fieldWidth = 132;

        // ---- 卡池 ID（新建模式可编辑；现有池只读展示，防保底记录/中奖历史悬空）----
        editPanel.child(
            new TextWidget<>(IKey.str("卡池ID:")).left(8)
                .top(fieldY + 2));

        TextFieldWidget idField = new TextFieldWidget()
            .value(new StringValue.Dynamic(() -> editPoolId, val -> editPoolId = val.trim()))
            .setMaxLength(30);
        idField.left(labelWidth)
            .top(fieldY)
            .size(fieldWidth, fieldHeight);
        idField.tooltipBuilder(t -> {
            t.addLine(IKey.str("卡池唯一标识（仅字母/数字/下划线/连字符）"));
            t.addLine(IKey.str(EnumChatFormatting.GRAY + "保存后不可修改"));
        });
        idField.tooltipAutoUpdate(true);
        // 仅新建模式显示输入框（现有池改走下方只读文本）
        idField.setEnabledIf(w -> editPoolIsNew);
        // v1.7.19：TextFieldWidget 延迟到 PhantomItemSlot 之后添加（z-index 置顶）
        // 现有池：只读文本展示 id（与输入框互斥显示）
        editPanel.child(
            new TextWidget<>(IKey.dynamic(() -> EnumChatFormatting.GRAY + editPoolId)).left(labelWidth)
                .top(fieldY + 2)
                .setEnabledIf(w -> !editPoolIsNew));

        // ---- 名称 ----
        fieldY += 17;
        editPanel.child(
            new TextWidget<>(IKey.str("名称:")).left(8)
                .top(fieldY + 2));

        TextFieldWidget nameField = new TextFieldWidget()
            .value(new StringValue.Dynamic(() -> editPoolName, val -> editPoolName = val))
            .setMaxLength(40);
        nameField.left(labelWidth)
            .top(fieldY)
            .size(fieldWidth, fieldHeight);
        nameField.tooltipBuilder(t -> t.addLine(IKey.str("卡池显示名称（留空则保持原名）")));
        nameField.tooltipAutoUpdate(true);
        // v1.7.19：TextFieldWidget 延迟到 PhantomItemSlot 之后添加（z-index 置顶）

        // ---- page 图标（PhantomItemSlot 拖入配置，支持 NBT；空槽 = 回退货币图标）----
        fieldY += 17;
        editPanel.child(
            new TextWidget<>(IKey.str("图标:")).left(8)
                .top(fieldY + 2));

        PhantomItemSlot iconSlot = new PhantomItemSlot().slot(new ModularSlot(editPoolItemHandler, 0));
        iconSlot.left(labelWidth)
            .top(fieldY - 2);
        iconSlot.tooltipBuilder(t -> {
            t.addLine(IKey.str("拖入物品作为池标签图标（支持 NBT）"));
            t.addLine(IKey.str(EnumChatFormatting.GRAY + "留空 = 按池消耗货币显示缺省币图标"));
        });
        iconSlot.tooltipAutoUpdate(true);
        editPanel.child(iconSlot);

        // ---- 消耗需求（PhantomItemSlot×4：货币物品扣团队钱包、普通物品扣机器输入槽）----
        fieldY += 21; // 图标槽高 18，多留间距
        editPanel.child(
            new TextWidget<>(IKey.str("消耗需求:")).left(8)
                .top(fieldY + 2));

        for (int i = 0; i < POOL_COST_SLOTS; i++) {
            PhantomItemSlot costSlot = new PhantomItemSlot().slot(new ModularSlot(editPoolItemHandler, 1 + i));
            costSlot.left(labelWidth + i * 20)
                .top(fieldY - 2);
            costSlot.tooltipBuilder(t -> {
                t.addLine(IKey.str("单次抽取消耗（数量=槽内堆叠数）"));
                t.addLine(IKey.str(EnumChatFormatting.GRAY + "猫猫币物品 → 团队钱包扣除"));
                t.addLine(IKey.str(EnumChatFormatting.GRAY + "普通物品 → 机器输入槽扣除"));
                t.addLine(IKey.str(EnumChatFormatting.DARK_GRAY + "留空全部 = 免费"));
            });
            costSlot.tooltipAutoUpdate(true);
            editPanel.child(costSlot);
        }

        // v1.7.19：延迟添加的 TextFieldWidget（在所有 PhantomItemSlot 之后，确保渲染层位于物品槽之上）
        editPanel.child(idField);
        editPanel.child(nameField);

        // ---- 保底启用（点击切换）----
        fieldY += 21; // 消耗槽高 18，多留间距
        editPanel.child(
            new TextWidget<>(IKey.str("保底启用:")).left(8)
                .top(fieldY + 2));

        ButtonWidget<?> pityToggle = new ButtonWidget<>().left(labelWidth)
            .top(fieldY)
            .size(fieldWidth, fieldHeight)
            .background(NekoGuiTextures.TEXT_FIELD_BACKGROUND)
            .overlay(
                IKey.dynamic(
                    () -> editPoolPityEnabled ? EnumChatFormatting.GREEN + "启用" : EnumChatFormatting.RED + "停用"))
            .tooltipBuilder(t -> t.addLine(IKey.str("点击切换保底机制启用/停用")))
            .onMouseTapped(mouse -> {
                editPoolPityEnabled = !editPoolPityEnabled;
                return true;
            });
        pityToggle.tooltipAutoUpdate(true);
        editPanel.child(pityToggle);

        // ---- 软保底阈值 ----
        fieldY += 17;
        editPanel.child(
            new TextWidget<>(IKey.str("软保底:")).left(8)
                .top(fieldY + 2));

        TextFieldWidget softPityField = new TextFieldWidget()
            .value(new StringValue.Dynamic(() -> String.valueOf(editPoolSoftPity), val -> {
                try {
                    editPoolSoftPity = Integer.parseInt(val);
                } catch (NumberFormatException ignored) {}
            }))
            .setNumbers(0, Integer.MAX_VALUE);
        softPityField.left(labelWidth)
            .top(fieldY)
            .size(fieldWidth, fieldHeight);
        softPityField.tooltipBuilder(t -> t.addLine(IKey.str("软保底起始抽数（此后高稀有概率逐抽递增）")));
        softPityField.tooltipAutoUpdate(true);
        editPanel.child(softPityField);

        // ---- 硬保底阈值 ----
        fieldY += 17;
        editPanel.child(
            new TextWidget<>(IKey.str("硬保底:")).left(8)
                .top(fieldY + 2));

        TextFieldWidget hardPityField = new TextFieldWidget()
            .value(new StringValue.Dynamic(() -> String.valueOf(editPoolHardPity), val -> {
                try {
                    editPoolHardPity = Integer.parseInt(val);
                } catch (NumberFormatException ignored) {}
            }))
            .setNumbers(0, Integer.MAX_VALUE);
        hardPityField.left(labelWidth)
            .top(fieldY)
            .size(fieldWidth, fieldHeight);
        hardPityField.tooltipBuilder(t -> t.addLine(IKey.str("硬保底抽数（达到后必出保证稀有度，0=关闭）")));
        hardPityField.tooltipAutoUpdate(true);
        editPanel.child(hardPityField);

        // ---- 保证稀有度（点击循环切换：普通→稀有→史诗→传说）----
        fieldY += 17;
        editPanel.child(
            new TextWidget<>(IKey.str("保证稀有度:")).left(8)
                .top(fieldY + 2));

        ButtonWidget<?> rarityButton = new ButtonWidget<>().left(labelWidth)
            .top(fieldY)
            .size(fieldWidth, fieldHeight)
            .background(NekoGuiTextures.TEXT_FIELD_BACKGROUND)
            .overlay(IKey.dynamic(this::poolGuaranteedRarityDisplay))
            .tooltipBuilder(t -> t.addLine(IKey.str("硬保底触发时保证的稀有度（点击循环切换）")))
            .onMouseTapped(mouse -> {
                cyclePoolGuaranteedRarity();
                return true;
            });
        rarityButton.tooltipAutoUpdate(true);
        editPanel.child(rarityButton);

        // ---- 保存 / 删除 / 取消按钮 ----
        editPanel.child(
            new ButtonWidget<>().size(50, 16)
                .left(14)
                .bottom(8)
                .overlay(IKey.str("保存"))
                .onMouseTapped(mouse -> {
                    saveLotteryPoolEdit();
                    closeEditOverlay();
                    return true;
                }));
        // 删除按钮：仅编辑现有池时显示（新建模式无池可删）
        ButtonWidget<?> deleteButton = new ButtonWidget<>().size(50, 16)
            .left(80)
            .bottom(8)
            .overlay(IKey.str(EnumChatFormatting.RED + "删除"))
            .onMouseTapped(mouse -> {
                deleteLotteryPoolEdit();
                closeEditOverlay();
                return true;
            });
        deleteButton.tooltipBuilder(t -> {
            t.addLine(IKey.str(EnumChatFormatting.RED + "删除本卡池"));
            t.addLine(IKey.str(EnumChatFormatting.GRAY + "至少保留一个卡池（服务端校验）"));
        });
        deleteButton.tooltipAutoUpdate(true);
        deleteButton.setEnabledIf(w -> !editPoolIsNew);
        editPanel.child(deleteButton);
        editPanel.child(
            new ButtonWidget<>().size(50, 16)
                .right(14)
                .bottom(8)
                .overlay(IKey.str("取消"))
                .onMouseTapped(mouse -> {
                    closeEditOverlay();
                    return true;
                }));

        return editPanel;
    }

    /**
     * 保证稀有度循环切换（COMMON→RARE→EPIC→LEGENDARY→COMMON）
     */
    private void cyclePoolGuaranteedRarity() {
        LotteryRarity[] values = LotteryRarity.values();
        LotteryRarity current = LotteryRarity.fromString(editPoolGuaranteedRarity);
        editPoolGuaranteedRarity = values[(current.ordinal() + 1) % values.length].name();
    }

    /**
     * 保证稀有度按钮显示文本（带稀有度颜色）
     */
    private String poolGuaranteedRarityDisplay() {
        LotteryRarity rarity = LotteryRarity.fromString(editPoolGuaranteedRarity);
        return rarity.getColor() + rarity.getDisplayName() + EnumChatFormatting.GRAY + "（" + rarity.name() + "）";
    }

    /**
     * 保存抽奖卡池编辑（客户端 → 服务端）
     * <p>
     * 序列化 {@code {id?, name, icon?, costItems[], pityEnabled, softPityThreshold,
     * hardPityThreshold, guaranteedRarity}}（图标/消耗需求取自 PhantomItemSlot，
     * 格式与服务端 {@code NekoEditActionHandler#applyPoolEditJson} 对应）。
     * 新建模式附 id 走 create，现有池走 save（id 仅定位，不可改）。
     */
    private void saveLotteryPoolEdit() {
        try {
            com.google.gson.JsonObject json = new com.google.gson.JsonObject();
            if (editPoolIsNew) {
                String id = editPoolId.trim();
                // 客户端提前拦截空 id（合法性/唯一性由服务端最终校验）
                if (id.isEmpty()) return;
                json.addProperty("id", id);
            } else if (editPoolId.isEmpty()) {
                return;
            }
            json.addProperty("name", editPoolName);
            // page 图标（slot 0；空槽不发 icon 键 = 服务端清空图标，GUI 回退货币图标）
            ItemStack iconStack = editPoolItemHandler.getStackInSlot(0);
            if (iconStack != null && iconStack.getItem() != null) {
                json.add("icon", itemStackToEditJson(iconStack));
            }
            // 消耗需求（slot 1-4，跳过空槽）
            com.google.gson.JsonArray costArray = new com.google.gson.JsonArray();
            for (int i = 1; i <= POOL_COST_SLOTS; i++) {
                ItemStack stack = editPoolItemHandler.getStackInSlot(i);
                if (stack != null && stack.getItem() != null && stack.stackSize > 0) {
                    costArray.add(itemStackToEditJson(stack));
                }
            }
            json.add("costItems", costArray);
            // 保底字段
            json.addProperty("pityEnabled", editPoolPityEnabled);
            json.addProperty("softPityThreshold", editPoolSoftPity);
            json.addProperty("hardPityThreshold", editPoolHardPity);
            json.addProperty("guaranteedRarity", editPoolGuaranteedRarity);
            if (editPoolIsNew) {
                com.miaokatze.gtit.trade.v2.NekoEditNetworkManager.sendCreateLotteryPool(json.toString());
            } else {
                com.miaokatze.gtit.trade.v2.NekoEditNetworkManager.sendSaveLotteryPool(editPoolId, json.toString());
            }
        } catch (Exception e) {
            GTInterestingThing.LOG.error("[NekoEdit] 保存抽奖卡池编辑失败", e);
        }
    }

    /**
     * 删除抽奖卡池（客户端 → 服务端）
     * <p>
     * 仅编辑现有池时可触发（新建模式删除按钮已隐藏）；「至少保留一池」由服务端校验。
     */
    private void deleteLotteryPoolEdit() {
        if (editPoolIsNew || editPoolId.isEmpty()) return;
        com.miaokatze.gtit.trade.v2.NekoEditNetworkManager.sendDeleteLotteryPool(editPoolId);
    }

    // ==================== 标签页 page 编辑（v1.7.6 G3④） ====================

    /**
     * 打开 page 编辑面板（客户端，编辑模式下 Shift+点击 page 标签触发）
     * <p>
     * 名称从 {@link NekoPageRegistry} 读取（与同步包同源）；图标经 {@link #editPageTargetSync}
     * 通知服务端从权威配置加载到编辑缓冲区（PhantomItemSlot 自动同步回客户端显示，含 NBT）。
     *
     * @param pageId 被点击的标签页 ID
     */
    private void openPageEditor(int pageId) {
        NekoPageEntry page = NekoPageRegistry.getPage(pageId);
        if (page == null) return;
        editPageIsNew = false;
        editPageId = pageId;
        editPageName = page.getName() != null ? page.getName() : "";
        // 客户端先清空图标槽（防上一页残留；服务端随后加载权威图标并同步回客户端）
        editPageItemHandler.setStackInSlot(0, null);
        if (editPageTargetSync != null) {
            editPageTargetSync.setValue(String.valueOf(pageId));
        }
        openEditOverlay(EditOverlayType.PAGE);
    }

    /**
     * 打开新建 page 编辑面板（客户端，编辑模式下点击标签列尾「+」按钮触发）
     * <p>
     * 字段全部置默认值；通知服务端清空编辑缓冲区（{@link #PAGE_TARGET_NEW} 标记）。
     * 保存时走 {@code NekoEditNetworkManager.sendCreatePage}（服务端分配 id≥4）。
     */
    private void openNewPageEditor() {
        editPageIsNew = true;
        editPageId = -1;
        editPageName = "";
        editPageItemHandler.setStackInSlot(0, null);
        if (editPageTargetSync != null) {
            editPageTargetSync.setValue(PAGE_TARGET_NEW);
        }
        openEditOverlay(EditOverlayType.PAGE);
    }

    /**
     * 服务端：加载 page 图标到编辑缓冲区（slot 0）
     * <p>
     * 每次加载前先清空（防上一页残留）；{@link #PAGE_TARGET_NEW}（新建模式）或
     * 查找失败时仅清空不加载。图标按条目配置的 iconItem 转换（不含默认页回退图标——
     * 编辑的是配置字段本身）。
     *
     * @param target pageId 字符串或 {@link #PAGE_TARGET_NEW}
     */
    private void loadPageIntoEditBuffer(String target) {
        try {
            editPageItemHandler.setStackInSlot(0, null);
            if (PAGE_TARGET_NEW.equals(target)) return;
            int pageId = Integer.parseInt(target);
            NekoPageEntry page = NekoPageRegistry.getPage(pageId);
            if (page == null) return;
            ItemStack icon = page.toIconItemStack();
            if (icon != null) {
                editPageItemHandler.setStackInSlot(0, icon);
            }
            GTInterestingThing.LOG.info("[NekoEdit] 已加载标签页到编辑缓冲区: {}", target);
        } catch (Exception e) {
            GTInterestingThing.LOG.error("[NekoEdit] 加载标签页到编辑缓冲区失败: {}", target, e);
        }
    }

    /**
     * 构建标签页 page 编辑面板（v1.7.6 G3④）
     * <p>
     * 字段布局：page ID（新建=分配提示 / 现有=只读展示）→ 名称 → 图标 PhantomItemSlot×1。
     * 保存按 {@link #editPageIsNew} 分流：新建走
     * {@link com.miaokatze.gtit.trade.v2.NekoEditNetworkManager#sendCreatePage}（服务端分配 id≥4），
     * 现有页走 {@link com.miaokatze.gtit.trade.v2.NekoEditNetworkManager#sendSavePage}；
     * 删除按钮仅编辑现有页时显示（默认页 1-3 由服务端拦截）。
     *
     * @return 编辑覆盖层面板（{@link ParentWidget}）
     */
    private NekoDraggableEditPanel buildPageEditPanel() {
        NekoDraggableEditPanel editPanel = new NekoDraggableEditPanel();
        editPanel.size(210, 130);
        // v1.7.7 G2 迁移为主面板内嵌 ParentWidget 覆盖层后无默认背景，需手动补上 MC 风格背景
        editPanel.background(GuiTextures.MC_BACKGROUND);
        editPanel.leftRel(0.5f)
            .topRel(0.5f)
            .anchorLeft(0.5f)
            .anchorTop(0.5f);
        editPanel.setEnabledIf(w -> currentEditOverlay == EditOverlayType.PAGE);

        // 标题（新建 / 编辑 + pageId 动态切换）
        editPanel.child(
            new TextWidget<>(
                IKey.dynamic(() -> EnumChatFormatting.GOLD + (editPageIsNew ? "新建标签页" : "编辑标签页（#" + editPageId + "）")))
                    .top(5)
                    .horizontalCenter());

        int fieldY = 24;
        int fieldHeight = 14;
        int labelWidth = 62;
        int fieldWidth = 132;

        // ---- page ID（新建：服务端分配提示；现有：只读展示，id 不可改）----
        editPanel.child(
            new TextWidget<>(IKey.str("ID:")).left(8)
                .top(fieldY + 2));
        editPanel.child(
            new TextWidget<>(
                IKey.dynamic(
                    () -> EnumChatFormatting.GRAY + (editPageIsNew ? "（保存时自动分配 ≥4）" : String.valueOf(editPageId))))
                        .left(labelWidth)
                        .top(fieldY + 2));

        // ---- 名称 ----
        fieldY += 17;
        editPanel.child(
            new TextWidget<>(IKey.str("名称:")).left(8)
                .top(fieldY + 2));
        TextFieldWidget nameField = new TextFieldWidget()
            .value(new StringValue.Dynamic(() -> editPageName, val -> editPageName = val))
            .setMaxLength(40);
        nameField.left(labelWidth)
            .top(fieldY)
            .size(fieldWidth, fieldHeight);
        nameField.tooltipBuilder(t -> t.addLine(IKey.str("标签页显示名称（留空则保持原名）")));
        nameField.tooltipAutoUpdate(true);
        // v1.7.19：TextFieldWidget 延迟到 PhantomItemSlot 之后添加（z-index 置顶）

        // ---- 图标（PhantomItemSlot 拖入配置，支持 NBT；空槽 = 清空图标回退默认）----
        fieldY += 17;
        editPanel.child(
            new TextWidget<>(IKey.str("图标:")).left(8)
                .top(fieldY + 2));
        PhantomItemSlot iconSlot = new PhantomItemSlot().slot(new ModularSlot(editPageItemHandler, 0));
        iconSlot.left(labelWidth)
            .top(fieldY - 2);
        iconSlot.tooltipBuilder(t -> {
            t.addLine(IKey.str("拖入物品作为标签页图标（支持 NBT）"));
            t.addLine(IKey.str(EnumChatFormatting.GRAY + "留空 = 清空图标（默认页回退默认图标）"));
        });
        iconSlot.tooltipAutoUpdate(true);
        editPanel.child(iconSlot);

        // v1.7.19：延迟添加的 TextFieldWidget（在所有 PhantomItemSlot 之后，确保渲染层位于物品槽之上）
        editPanel.child(nameField);

        // ---- 保存 / 删除 / 取消按钮 ----
        editPanel.child(
            new ButtonWidget<>().size(50, 16)
                .left(14)
                .bottom(8)
                .overlay(IKey.str("保存"))
                .onMouseTapped(mouse -> {
                    savePageEdit();
                    closeEditOverlay();
                    return true;
                }));
        // 删除按钮：仅编辑现有页时显示（新建模式无页可删；默认页 1-3 由服务端拦截）
        ButtonWidget<?> deleteButton = new ButtonWidget<>().size(50, 16)
            .left(80)
            .bottom(8)
            .overlay(IKey.str(EnumChatFormatting.RED + "删除"))
            .onMouseTapped(mouse -> {
                deletePageEdit();
                closeEditOverlay();
                return true;
            });
        deleteButton.tooltipBuilder(t -> {
            t.addLine(IKey.str(EnumChatFormatting.RED + "删除本标签页"));
            t.addLine(IKey.str(EnumChatFormatting.GRAY + "页内交易移至「其他」页"));
            t.addLine(IKey.str(EnumChatFormatting.GRAY + "默认页（ID 1-3）不可删除"));
        });
        deleteButton.tooltipAutoUpdate(true);
        deleteButton.setEnabledIf(w -> !editPageIsNew);
        editPanel.child(deleteButton);
        editPanel.child(
            new ButtonWidget<>().size(50, 16)
                .right(14)
                .bottom(8)
                .overlay(IKey.str("取消"))
                .onMouseTapped(mouse -> {
                    closeEditOverlay();
                    return true;
                }));

        return editPanel;
    }

    /**
     * 保存 page 编辑（客户端 → 服务端）
     * <p>
     * 序列化 {@code {name, icon?}}（图标取自 PhantomItemSlot，空槽不发 icon 键 = 服务端清空图标，
     * 格式与服务端 {@code NekoEditActionHandler#applyPageEditJson} 对应）。
     * 新建模式走 create（服务端分配 id≥4），现有页走 save（pageId 仅定位，不可改）。
     */
    private void savePageEdit() {
        try {
            com.google.gson.JsonObject json = new com.google.gson.JsonObject();
            json.addProperty("name", editPageName);
            // page 图标（slot 0；空槽不发 icon 键 = 服务端清空图标）
            ItemStack iconStack = editPageItemHandler.getStackInSlot(0);
            if (iconStack != null && iconStack.getItem() != null) {
                json.add("icon", itemStackToEditJson(iconStack));
            }
            if (editPageIsNew) {
                com.miaokatze.gtit.trade.v2.NekoEditNetworkManager.sendCreatePage(json.toString());
            } else {
                if (editPageId < 0) return;
                com.miaokatze.gtit.trade.v2.NekoEditNetworkManager
                    .sendSavePage(String.valueOf(editPageId), json.toString());
            }
        } catch (Exception e) {
            GTInterestingThing.LOG.error("[NekoEdit] 保存标签页编辑失败", e);
        }
    }

    /**
     * 删除 page（客户端 → 服务端）
     * <p>
     * 仅编辑现有页时可触发（新建模式删除按钮已隐藏）；「默认页 1-3 不可删」由服务端校验。
     */
    private void deletePageEdit() {
        if (editPageIsNew || editPageId < 0) return;
        com.miaokatze.gtit.trade.v2.NekoEditNetworkManager.sendDeletePage(String.valueOf(editPageId));
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
     * v1.7.7 G2③：仅在主标签为贸易时显示（{@link #mainTabController} 当前页 == {@link #MAIN_TAB_TRADE}）。
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

            // v1.7.6 G3④：匿名子类包一层点击拦截——编辑模式下 Shift+点击 page 标签
            // 打开 page 编辑面板（不切页、不更新 lastPage）；普通点击维持原切页逻辑。
            // 收藏（tabId=-1）/未知（tabId=0）为虚拟分类，无对应 page 条目，不拦截。
            NekoPageButtonV2 tabButton = new NekoPageButtonV2(index, tabController, category, highlightedTabs, icon) {

                @Override
                public Interactable.Result onMousePressed(int mouseButton) {
                    if (isEditModeActive() && Interactable.hasShiftDown() && category.getTabId() > 0) {
                        openPageEditor(category.getTabId());
                        return Interactable.Result.SUCCESS;
                    }
                    return super.onMousePressed(mouseButton);
                }
            };
            tabButton.tab(NekoGuiTextures.TAB_LEFT, -1);
            tabButton.tooltipBuilder(t -> {
                t.addLine(IKey.str(name));
                // 编辑模式下追加操作提示（随编辑模式动态刷新）
                if (isEditModeActive() && category.getTabId() > 0) {
                    t.addLine(IKey.str(EnumChatFormatting.YELLOW + "Shift+点击 编辑标签页"));
                }
            });
            tabButton.tooltipAutoUpdate(true);

            tabColumn.child(tabButton);
        }

        // v1.7.6 G3④：「新建 page」按钮——仅编辑模式显示（列尾），点击打开空白 page 编辑面板。
        // 复用 NekoSubTabButton 的 externalMode（永不选中、点击走 onSelected 不切页），
        // index 取列尾位置（externalMode 下不参与切页，仅作标识）。
        NekoSubTabButton newPageButton = new NekoSubTabButton(
            tradeCategories.size(),
            tabController,
            IKey.str(EnumChatFormatting.GREEN + "+"));
        newPageButton.tab(NekoGuiTextures.TAB_LEFT, -1);
        newPageButton.externalMode(() -> false);
        newPageButton.onSelected(this::openNewPageEditor);
        newPageButton.tooltipBuilder(t -> {
            t.addLine(IKey.str("新建标签页"));
            t.addLine(IKey.str(EnumChatFormatting.GRAY + "创建空白标签页（保存时自动分配 ID ≥ 4）"));
        });
        newPageButton.setEnabledIf(w -> isEditModeActive());
        tabColumn.child(newPageButton);
        // 非编辑模式时「新建 page」按钮不占位（列自动收紧）
        tabColumn.collapseDisabledChild(true);

        // v1.7.7 G2③：读取处以 controller 当前页为准，避免 mainTabId 滞后
        tabColumn
            .setEnabledIf(w -> mainTabController != null && mainTabController.getActivePageIndex() == MAIN_TAB_TRADE);

        // 在 NEI/HEI 中排除标签列区域，避免配方查看器遮挡标签页
        return tabColumn.excludeAreaInRecipeViewer();
    }

    /**
     * v1.7.6 G1 创建指定主标签页的 sub-page 标签列（签到/抽奖/邮件）
     * <p>
     * 位置与贸易分类列 {@link #createTabColumn()} 相同（left(-29)、top(40)、childPadding(2)），
     * 通过 {@code setEnabledIf} 按主标签互斥显示，不与贸易分类列/QoL 列叠放。
     * <p>
     * 按钮内容（v1.7.6）：
     * <ul>
     * <li>签到「活跃」4 按钮：每月签到/连续签到/每日在线/纪念日</li>
     * <li>邮件 5 按钮：全部/系统/玩家/管理员/写邮件</li>
     * <li>抽奖（G2①）：动态池按钮列——预分配 {@link #MAX_POOL_TABS} 个按钮，数据源
     * {@link LotteryClientData#getPools()} 每帧驱动图标/显隐（池自配图标优先，缺省按货币回退币图标），
     * 外部模式点击写 {@code selectedPoolId}（抽奖页单页动态读选中池）；编辑模式 Shift+点击开池编辑面板，
     * 列尾「+」按钮（仅编辑模式）开新建池面板；抽奖页顶部旧双池切换按钮行已随 G2① 移除</li>
     * </ul>
     * 按钮统一使用 {@link NekoSubTabButton}——G1 阶段三个 sub-page Controller 尚未绑定 PagedWidget，
     * 按钮内置防崩守卫（未绑定时点击直接忽略），G2 建对应 PagedWidget 页面后自动接管切换。
     * <p>
     * 双端安全：纯按钮无槽位，仅客户端创建（与 createTabColumn 一致）。
     *
     * @param mainTab 主标签索引（{@link #MAIN_TAB_SIGNIN}/{@link #MAIN_TAB_LOTTERY}/{@link #MAIN_TAB_MAIL}）
     * @return sub-page 标签列 Widget
     */
    private IWidget createSubTabColumn(final int mainTab) {
        Flow subTabColumn = Flow.column()
            .coverChildren()
            .left(-29)
            .top(40)
            .childPadding(2);

        if (mainTab == MAIN_TAB_LOTTERY) {
            // --- 抽奖页：动态池按钮列（v1.7.6 G2①，每池一按钮）---
            // 预分配 MAX_POOL_TABS 个按钮：图标/选中态/显隐全部每帧动态求值（数据源
            // LotteryClientData.getPools()），池同步包晚于 GUI 打开到达时按钮随缓存更新自动出现，
            // 解决 G1 过渡态「列在 build 时一次性生成、同步晚到则空列」的问题。
            // 隐藏按钮由列尾 collapseDisabledChild 压缩占位；纯按钮无槽位，setEnabledIf 只挡绘制足够。
            for (int i = 0; i < MAX_POOL_TABS; i++) {
                final int index = i;
                // 图标动态求值：池自配图标（iconItem）优先 → 缺省按池货币回退币图标
                DynamicDrawable icon = new DynamicDrawable(() -> {
                    LotteryClientData.PoolSummary pool = poolAt(index);
                    if (pool != null) {
                        ItemStack iconStack = pool.toIconItemStack();
                        if (iconStack != null) {
                            return new ItemDrawable(iconStack);
                        }
                        return NekoCurrencyRegistrar.SHIMMERING_NEKO_ID.equals(pool.currencyId)
                            ? NekoGuiTextures.LOTTERY_COIN_SHIMMER
                            : NekoGuiTextures.LOTTERY_COIN_NEKO;
                    }
                    // 隐藏态返回值（不绘制，仅占位防空指针）
                    return NekoGuiTextures.LOTTERY_COIN_NEKO;
                });
                NekoSubTabButton poolButton = new NekoSubTabButton(index, lotteryPageController, icon);
                poolButton.tab(NekoGuiTextures.TAB_LEFT, -1);
                // 外部模式：选中态=当前选中池（LotteryClientData.selectedPoolId），
                // 点击=切换选中池（抽奖页单页动态读 getSelectedPool，无需每池一页 PagedWidget）；
                // 编辑模式下 Shift+点击=打开池编辑面板
                poolButton.externalMode(() -> {
                    LotteryClientData.PoolSummary pool = poolAt(index);
                    return pool != null && pool.id != null && pool.id.equals(LotteryClientData.getSelectedPoolId());
                });
                poolButton.onSelected(() -> {
                    LotteryClientData.PoolSummary pool = poolAt(index);
                    if (pool == null) return;
                    if (isEditModeActive() && Interactable.hasShiftDown()) {
                        openLotteryPoolEditor(pool);
                    } else {
                        LotteryClientData.setSelectedPoolId(pool.id);
                    }
                });
                poolButton.tooltipBuilder(t -> {
                    LotteryClientData.PoolSummary pool = poolAt(index);
                    if (pool != null) {
                        t.addLine(IKey.str(pool.name));
                        if (isEditModeActive()) {
                            t.addLine(IKey.str(EnumChatFormatting.YELLOW + "Shift+点击 编辑卡池"));
                        }
                    }
                });
                poolButton.tooltipAutoUpdate(true);
                // 池数量不足时隐藏本按钮（不占位，列尾 collapseDisabledChild 压缩）
                poolButton.setEnabledIf(w -> poolAt(index) != null);
                subTabColumn.child(poolButton);
            }
            // --- 「新建池」按钮：仅编辑模式显示（列尾），点击打开空白池编辑面板 ---
            NekoSubTabButton newPoolButton = new NekoSubTabButton(
                MAX_POOL_TABS,
                lotteryPageController,
                IKey.str(EnumChatFormatting.GREEN + "+"));
            newPoolButton.tab(NekoGuiTextures.TAB_LEFT, -1);
            // 外部模式：永不选中；点击直接打开新建池编辑面板
            newPoolButton.externalMode(() -> false);
            newPoolButton.onSelected(this::openNewLotteryPoolEditor);
            newPoolButton.tooltipBuilder(t -> {
                t.addLine(IKey.str("新建卡池"));
                t.addLine(IKey.str(EnumChatFormatting.GRAY + "创建空白卡池（含 1 条种子奖品）"));
            });
            newPoolButton.setEnabledIf(w -> isEditModeActive());
            subTabColumn.child(newPoolButton);
            // 隐藏按钮不占位（池数量 < MAX_POOL_TABS / 非编辑模式时列自动收紧）
            subTabColumn.collapseDisabledChild(true);
        } else {
            // --- 签到/邮件页：静态按钮定义（index → 图标, 中文名）---
            // 图标选用 NekoGuiTextures 已注册材质中语义最接近者，不完全贴合的图标见 G1 报告的补做清单
            final Object[][] subTabs;
            final PagedWidget.Controller controller;
            if (mainTab == MAIN_TAB_SIGNIN) {
                // 签到「活跃」4 页（与 G2③ 页面顺序一致）：每月签到/连续签到/每日在线/纪念日
                controller = signInPageController;
                subTabs = new Object[][] { { 0, NekoGuiTextures.SIGNIN_CELL_SIGNED, "每月签到" },
                    { 1, NekoGuiTextures.SIGNIN_CHEST_30, "连续签到" }, { 2, NekoGuiTextures.SIGNIN_CELL_REWARD, "每日在线" },
                    { 3, NekoGuiTextures.FAVOURITE_SPRITE, "纪念日" }, };
            } else {
                // 邮件 5 页（与 G2② 页面顺序一致）：全部/系统/玩家/管理员 + 写邮件入口
                controller = mailPageController;
                subTabs = new Object[][] { { 0, NekoGuiTextures.MAIL_ICON_READ, "全部" },
                    { 1, NekoGuiTextures.MAIL_ICON_UNREAD, "系统" }, { 2, NekoGuiTextures.WALLET_PERSONAL, "玩家" },
                    { 3, NekoGuiTextures.MAIN_TAB_EDIT, "管理员" }, { 4, NekoGuiTextures.MAIL_WRITE, "写邮件" }, };
            }
            for (Object[] tabDef : subTabs) {
                final int index = (Integer) tabDef[0];
                final com.cleanroommc.modularui.drawable.UITexture icon = (com.cleanroommc.modularui.drawable.UITexture) tabDef[1];
                final String name = (String) tabDef[2];
                NekoSubTabButton subTabButton = new NekoSubTabButton(index, controller, icon);
                subTabButton.tab(NekoGuiTextures.TAB_LEFT, -1);
                subTabButton.tooltipBuilder(t -> { t.addLine(IKey.str(name)); });
                // v1.7.6 G2②：邮件页切换 sub-tab（全部/系统/玩家/管理员）时重置列表页码——
                // 各类型过滤后总页数不同，不重置则翻页行可能短暂显示「当前页 > 总页数」。
                // 写邮件页（index 4）无列表，同回调重置无副作用；签到页（G2③）如需同逻辑自行挂接。
                if (mainTab == MAIN_TAB_MAIL) {
                    subTabButton.onSelected(() -> MailClientData.setListPage(0));
                }
                subTabColumn.child(subTabButton);
            }
            // --- v1.7.6 G5：「祝福预设」入口按钮——仅邮件页 + 编辑模式显示（列尾）---
            // 外部模式：永不选中（入口按钮不切换 sub-page，点击直接弹出祝福预设编辑面板）
            if (mainTab == MAIN_TAB_MAIL) {
                NekoSubTabButton blessingButton = new NekoSubTabButton(5, controller, NekoGuiTextures.MAIL_PAPER);
                blessingButton.tab(NekoGuiTextures.TAB_LEFT, -1);
                blessingButton.externalMode(() -> false);
                blessingButton.onSelected(() -> openBlessingEditor("birthday"));
                blessingButton.tooltipBuilder(t -> {
                    t.addLine(IKey.str("祝福预设"));
                    t.addLine(IKey.str(EnumChatFormatting.GRAY + "查看/编辑自动祝福邮件模板"));
                });
                blessingButton.tooltipAutoUpdate(true);
                blessingButton.setEnabledIf(w -> isEditModeActive());
                subTabColumn.child(blessingButton);
                // 非编辑模式时入口按钮不占位（列自动收紧）
                subTabColumn.collapseDisabledChild(true);
            }
        }

        // v1.7.7 G2③：以 controller 当前页为权威，避免与 mainTabId 不同步
        subTabColumn.setEnabledIf(w -> mainTabController != null && mainTabController.getActivePageIndex() == mainTab);

        // 在 NEI/HEI 中排除标签列区域，避免配方查看器遮挡标签页
        return subTabColumn.excludeAreaInRecipeViewer();
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
        // v1.7.7 G2③：以 controller 当前页为权威
        qolGrid
            .setEnabledIf(w -> mainTabController != null && mainTabController.getActivePageIndex() == MAIN_TAB_TRADE);
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
        // v1.7.18：使用 NekoPagedWidget 替代 PagedWidget，覆写 canHover()=false 避免拦截背包栏的 hover 检测
        NekoPagedWidget<?> mainPaged = new NekoPagedWidget<>().name("nekoV2MainPaged")
            .size(PANEL_WIDTH - 8, PANEL_HEIGHT - 8)
            .controller(mainTabController);

        // 页 0：贸易（现有 createMainColumn 的内容）
        mainPaged.addPage(createTradeMainColumn(syncManager));

        // 页 1：签到「活跃」大页（v1.7.6 G2③ 重构：内层 4 sub-page——每月签到/连续签到/每日在线/纪念日；
        // v1.7.0 目标 4 传入编辑模式回调；signInPageController 绑定内层 PagedWidget，左侧 sub-tab 标签列同控制器接管切换）
        mainPaged.addPage(SignInCalendarGui.createSignInPage(createSignInEditCallback(), signInPageController));

        // 页 2：抽奖（v1.7.1 目标 2 实现，轮盘 GUI；出货槽定位依赖机器坐标；
        // v1.7.0 目标 4 传入编辑模式回调：编辑模式下点击轮盘槽位弹出条目编辑面板）
        mainPaged.addPage(LotteryGui.createLotteryPage(baseMetaTileEntity, createLotteryEditCallback()));

        // 页 3：邮件（v1.7.6 G2② 重构：类型分页 + 写邮件；传入机器定位=写邮件附件输入槽来源，
        // mailPageController 绑定内层 5 sub-page，左侧 sub-tab 标签列同控制器接管切换）
        mainPaged.addPage(MailGui.createMailPage(baseMetaTileEntity, mailPageController));

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

        // --- 「新建交易条目」按钮行：仅编辑模式显示（列表尾），点击打开空白交易编辑面板（v1.7.6 G3④）---
        // 收藏（tabId=-1）/未知（tabId=0）为虚拟分类，新建条目无处可挂，不显示按钮；
        // 非编辑模式时由 tradeList 的 collapseDisabledChild 压缩占位。
        NekoTradeRow newTradeRow = new NekoTradeRow();
        newTradeRow.height(LIST_ITEM_HEIGHT);
        newTradeRow.width(TRADE_ROW_WIDTH);
        newTradeRow.marginLeft(2);
        ButtonWidget<?> newTradeButton = new ButtonWidget<>().size(TRADE_ROW_WIDTH - 4, LIST_ITEM_HEIGHT - 2)
            .overlay(IKey.str(EnumChatFormatting.GREEN + "+ 新建交易条目"))
            .onMouseTapped(mouse -> {
                openNewTradeEditor(category.getTabId());
                return true;
            });
        newTradeButton.tooltipBuilder(t -> {
            t.addLine(IKey.str("新建交易条目"));
            t.addLine(IKey.str(EnumChatFormatting.GRAY + "在本标签页尾部追加一条空白交易"));
        });
        newTradeRow.child(newTradeButton);
        newTradeRow.setEnabledIf(w -> isEditModeActive() && category.getTabId() > 0);
        tradeList.child(newTradeRow);

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

        // v1.7.6 G1：IO 列四页恒显示（输入槽投币/取物、出货槽在四页均可用）——
        // 移除原 v1.7.0「仅贸易页」的 setEnabledIf；槽位双端注册本就不受 setEnabledIf 影响，此改动仅扩大可见范围

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
                    // [NekoInput] 诊断日志：lambda 入口，输出关键参数与线程信息
                    System.out.println(
                        "[NekoInput] changeListener 入口: slotIdx=" + index
                            + " thread="
                            + Thread.currentThread()
                                .getName()
                            + " client="
                            + client
                            + " init="
                            + init
                            + " onlyAmountChanged="
                            + onlyAmountChanged
                            + " newItem="
                            + (newItem == null ? "null" : newItem.getDisplayName())
                            + " stackSize="
                            + (newItem == null ? 0 : newItem.stackSize));
                    if (init || newItem == null) {
                        System.out.println("[NekoInput] 提前返回: init=" + init + " newItemNull=" + (newItem == null));
                        return;
                    }
                    String currencyId = NekoCurrencyRegistrar.getNekoCurrencyId(newItem);
                    if (currencyId == null) {
                        System.out.println("[NekoInput] 非猫猫币，跳过: slotIdx=" + index);
                        return;
                    }
                    System.out.println("[NekoInput] 识别猫猫币: slotIdx=" + index + " currencyId=" + currencyId);
                    // 客户端：立即视觉清槽，真实数据以服务端同步为准
                    if (client) {
                        System.out.println("[NekoInput] 客户端分支: 清槽前 slotIdx=" + index);
                        slot.putStack(null);
                        System.out.println("[NekoInput] 客户端分支: 清槽后 slotIdx=" + index);
                        return;
                    }
                    UUID playerId = getPlayerId();
                    if (playerId == null) {
                        System.out.println("[NekoInput] 服务端分支: playerId 为 null, 跳过");
                        return;
                    }
                    NekoWallet wallet = NekoWalletManager.INSTANCE.getWallet(playerId);
                    if (wallet == null) {
                        System.out.println("[NekoInput] 服务端分支: wallet 为 null, 跳过");
                        return;
                    }
                    // 先入账并持久化，再清槽，避免异常导致丢币
                    wallet.addCount(currencyId, newItem.stackSize);
                    int newCount = wallet.getCount(currencyId);
                    System.out.println(
                        "[NekoInput] 服务端分支: addCount 完成 currencyId=" + currencyId
                            + " added="
                            + newItem.stackSize
                            + " newCount="
                            + newCount);
                    NekoWalletManager.INSTANCE.saveWallet(playerId);
                    System.out.println("[NekoInput] 服务端分支: 清槽前 slotIdx=" + index);
                    slot.putStack(null);
                    System.out.println("[NekoInput] 服务端分支: 清槽后 slotIdx=" + index);
                    // 强制同步槽位到客户端，使玩家立即看到槽位清空
                    itemSlot.getSyncHandler()
                        .forceSyncItem();
                    System.out.println("[NekoInput] 服务端分支: forceSyncItem 已调用 slotIdx=" + index);
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
        // v1.7.14：移除 outputSlotRefs.addAll(getCreatedItemSlots())——NekoFallingItemSlotFactory
        // 已移除 createdItemSlots（forceSyncOutputSlots 在 v1.7.13 已不再调用，列表无用途）。
        // outputSlotRefs 字段与 forceSyncOutputSlots() 方法保留备查（方法无调用点，遍历空列表无害）。
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
        // [NekoImportCoins] 诊断日志：方法入口
        System.out.println(
            "[NekoImportCoins] 方法入口: playerId=" + playerId
                + " thread="
                + Thread.currentThread()
                    .getName());
        // 客户端不执行服务端逻辑（匹配 V1 的 isClient() 守卫）
        if (isClient()) {
            System.out.println("[NekoImportCoins] 客户端分支，提前返回");
            nekoImportCoins = false;
            return;
        }
        try {
            if (playerId == null) {
                System.out.println("[NekoImportCoins] playerId 为 null, 提前返回");
                nekoImportCoins = false;
                return;
            }
            NekoWallet wallet = NekoWalletManager.INSTANCE.getWallet(playerId);
            if (wallet == null) {
                System.out.println("[NekoImportCoins] wallet 为 null, 提前返回");
                nekoImportCoins = false;
                return;
            }

            int totalImported = 0;
            for (int i = 0; i < MTENekoVendingMachineV2.INPUT_SLOTS; i++) {
                ItemStack stack = multiblock.inputItems.getStackInSlot(i);
                if (stack == null) {
                    System.out.println("[NekoImportCoins] slotIdx=" + i + " stack=null, 跳过");
                    continue;
                }
                String currencyId = NekoCurrencyRegistrar.getNekoCurrencyId(stack);
                System.out.println(
                    "[NekoImportCoins] slotIdx=" + i
                        + " stack="
                        + stack.getDisplayName()
                        + " stackSize="
                        + stack.stackSize
                        + " currencyId="
                        + currencyId);
                if (currencyId != null) {
                    wallet.addCount(currencyId, stack.stackSize);
                    int newCount = wallet.getCount(currencyId);
                    System.out.println(
                        "[NekoImportCoins] addCount 完成: slotIdx=" + i
                            + " currencyId="
                            + currencyId
                            + " added="
                            + stack.stackSize
                            + " newCount="
                            + newCount);
                    totalImported += stack.stackSize;
                    multiblock.inputItems.setStackInSlot(i, null);
                    System.out.println("[NekoImportCoins] setStackInSlot(null) 完成: slotIdx=" + i);
                }
            }

            if (totalImported > 0) {
                NekoWalletManager.INSTANCE.saveWallet(playerId);
                tradeResultMessage = "成功导入 " + totalImported + " 个猫猫币";
                System.out.println("[NekoImportCoins] 总计导入: totalImported=" + totalImported);
            } else {
                tradeResultMessage = "输入槽中未找到猫猫币";
                System.out.println("[NekoImportCoins] 总计导入: totalImported=0（未找到猫猫币）");
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
        // [NekoForceSync] 诊断日志：方法入口
        System.out.println(
            "[NekoForceSync] 方法入口: inputSlotRefs.size()=" + inputSlotRefs.size()
                + " thread="
                + Thread.currentThread()
                    .getName());
        for (int i = 0; i < inputSlotRefs.size(); i++) {
            ItemSlot slot = inputSlotRefs.get(i);
            boolean hasSyncHandler = (slot != null && slot.getSyncHandler() != null);
            System.out.println(
                "[NekoForceSync] slotIdx=" + i + " slotNull=" + (slot == null) + " hasSyncHandler=" + hasSyncHandler);
            if (hasSyncHandler) {
                slot.getSyncHandler()
                    .forceSyncItem();
                System.out.println("[NekoForceSync] forceSyncItem 已调用: slotIdx=" + i);
            }
        }
    }

    /**
     * v1.7.8 B：强制同步输出槽到客户端（v1.7.13 起不再调用，保留备查）
     * <p>
     * v1.7.13 根因分析：forceSync（init=false + forceSync=true）会触发 changeListener(init=false)，
     * 导致 MutableObjectAnimator.resume() 立即将 fallingPosition 插值到起点 (x,-1) 隐藏区，
     * 造成物品隐形 + 点击偏移 + 无法放回物品栏。
     * 原版同步（Container.addSlotToContainer 将 inventoryItemStacks 初始化为 null →
     * 首次 detectAndSendChanges 发送 S2FPacketSetSlot → 客户端 putStack）已能正确同步物品，
     * 无需 forceSync 兜底。回归 VM 原版：不对输出槽使用 forceSync。
     * <p>
     * 方法保留但无调用点，outputSlotRefs 仍由 createIOColumn 收集（备查）。
     */
    private void forceSyncOutputSlots() {
        for (ItemSlot slot : outputSlotRefs) {
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
                // v1.7.10：猫猫币产物恢复落入输出槽（1.6.* 观感），不再直入钱包，
                // 产物经掉落动画可见，原 v1.7.8 A1 钱包入账提示不再需要
                tradeResultMessage = EnumChatFormatting.GREEN + "交易成功!";
                playTradeSuccessSound();
                // v1.7.12：移除此处的 forceSyncOutputSlots——交易成功时物品尚未落槽（仍在 outputBuffer），
                // 同步空槽无意义。物品落槽依赖 ModularContainer.detectAndSendChanges 原生同步
                // （ItemSlotSH.checkUpdate 检测到变化后发 SYNC_ITEM，客户端 changeListener 触发下落动画）。
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

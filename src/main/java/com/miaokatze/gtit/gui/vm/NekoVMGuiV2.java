package com.miaokatze.gtit.gui.vm;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.cleanroommc.modularui.api.IPanelHandler;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.BooleanSyncValue;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.StringSyncValue;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.PagedWidget;
import com.cleanroommc.modularui.widgets.SlotGroupWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.miaokatze.gtit.client.gui.NekoCoinDisplayV2;
import com.miaokatze.gtit.client.gui.NekoConfirmationDialog;
import com.miaokatze.gtit.client.gui.NekoDisplayType;
import com.miaokatze.gtit.client.gui.NekoPageButtonV2;
import com.miaokatze.gtit.client.gui.NekoPagedWidget;
import com.miaokatze.gtit.client.gui.NekoSearchBar;
import com.miaokatze.gtit.client.gui.NekoSortMode;
import com.miaokatze.gtit.client.gui.NekoTradeItemDisplay;
import com.miaokatze.gtit.client.gui.NekoTradeItemDisplayWidget;
import com.miaokatze.gtit.client.gui.NekoTradeMainPanel;
import com.miaokatze.gtit.client.gui.NekoVolumeControlGui;
import com.miaokatze.gtit.client.gui.NekoWalletMode;
import com.miaokatze.gtit.common.machine.neko.NekoMusicEventHandler;
import com.miaokatze.gtit.common.machine.v2.MTENekoVendingMachineV2;
import com.miaokatze.gtit.common.machine.v2.MeTransferEntry;
import com.miaokatze.gtit.config.NekoMusicConfig;
import com.miaokatze.gtit.currency.NekoCurrencyRegistrar;
import com.miaokatze.gtit.gui.vm.edit.BlessingEditor;
import com.miaokatze.gtit.gui.vm.edit.EditOverlayController;
import com.miaokatze.gtit.gui.vm.edit.EditOverlayController.EditOverlayType;
import com.miaokatze.gtit.gui.vm.edit.LotteryEntryEditor;
import com.miaokatze.gtit.gui.vm.edit.LotteryPoolEditor;
import com.miaokatze.gtit.gui.vm.edit.OnlineTierEditor;
import com.miaokatze.gtit.gui.vm.edit.PageEditor;
import com.miaokatze.gtit.gui.vm.edit.SignInEditor;
import com.miaokatze.gtit.gui.vm.edit.TradeEditor;
import com.miaokatze.gtit.lottery.LotteryClientData;
import com.miaokatze.gtit.lottery.LotteryEntry;
import com.miaokatze.gtit.lottery.LotteryGui;
import com.miaokatze.gtit.mail.MailGui;
import com.miaokatze.gtit.signin.SignInCalendarGui;
import com.miaokatze.gtit.trade.NekoWallet;
import com.miaokatze.gtit.trade.NekoWalletManager;
import com.miaokatze.gtit.trade.v2.NekoFavouritesTracker;
import com.miaokatze.gtit.trade.v2.NekoTradeCategory;
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

    /** 统一 logger（O2-B02 去中心化：与主类同用 "gtit" logger 名，日志过滤口径不变） */
    private static final Logger LOG = LogManager.getLogger("gtit");

    // ==================== 常量 ====================

    /** 面板宽度 */
    static final int PANEL_WIDTH = 178;
    /** 面板高度（与 V1 的 size(178, 320) 保持一致） */
    static final int PANEL_HEIGHT = 320;
    /** 每种显示模式预分配的 Widget 数量（与 VM 的 MAX_TRADES 一致） */
    static final int MAX_TRADES = 300;
    /** TILE 模式每行的 Widget 数量 */
    static final int TILE_ITEMS_PER_ROW = 3;
    /** TILE 模式 Widget 宽度 */
    static final int TILE_ITEM_WIDTH = NekoTradeItemDisplayWidget.TILE_ITEM_WIDTH;
    /** TILE 模式 Widget 高度 */
    static final int TILE_ITEM_HEIGHT = NekoTradeItemDisplayWidget.TILE_ITEM_HEIGHT;
    /** LIST 模式 Widget 宽度 */
    static final int LIST_ITEM_WIDTH = NekoTradeItemDisplayWidget.LIST_ITEM_WIDTH;
    /** LIST 模式 Widget 高度 */
    static final int LIST_ITEM_HEIGHT = NekoTradeItemDisplayWidget.LIST_ITEM_HEIGHT;
    /** 交易行宽度 */
    static final int TRADE_ROW_WIDTH = 154;
    /** 交易列表最小高度 */
    private static final int TRADE_LIST_MIN_HEIGHT = 50;

    // ==================== 同步值字段 ====================

    /** 交易请求（C2S：Shift+Click 时发送 "groupId:tradeIndex"） */
    private StringSyncValue tradeRequestSync;
    /** 交易结果消息（S2C：服务端处理完交易后发送结果文本） */
    private StringSyncValue tradeResultSync;
    /** 收藏切换请求（C2S：Ctrl+Click 时发送 "groupId:tradeIndex"） */
    private StringSyncValue favouriteToggleSync;
    /** 货币显示开关（C2S） */
    BooleanSyncValue showCoinsSync;
    /** v1.7.0 主标签索引（C2S：客户端切换主标签时发送到服务端） */
    private IntSyncValue mainTabSync;
    /** 是否显示猫猫币余额行 */
    boolean showCoins = true;
    /** 各货币余额同步值映射 */
    final Map<String, IntSyncValue> coinAmountSyncs = new HashMap<>();
    /** 客户端缓存的 ME 网络货币余额（currencyId → amount，阶段 6） */
    final Map<String, Integer> meCoinAmounts = new HashMap<>();
    /** ME 货币余额同步值映射（供 tooltip 查询和刷新，阶段 6） */
    final Map<String, IntSyncValue> meCoinAmountSyncs = new HashMap<>();

    // ==================== A01 蓝图 G5 域控制器（纯服务端域，不涉 Widget 树） ====================

    /** S2C 状态字符串编解码器（BQ 锁定/冷却/可交易/团队缩放四对通道 + 分类图标/名称辅助） */
    private final StatusCodec statusCodec = new StatusCodec(multiblock, baseMetaTileEntity, () -> {
        if (this.mainPanel != null) {
            this.mainPanel.notifyCurrencyUpdate();
        }
    });
    /**
     * 货币/物品操作控制器（doNeko 动作族 + 输入槽强刷/世界掉落/服务端音效 + eject/import/fill C2S 通道注册）
     */
    final CoinOpsController coinOps = new CoinOpsController(
        multiblock,
        baseMetaTileEntity,
        this::isClient,
        this::getPlayerId,
        msg -> this.tradeResultMessage = msg,
        msg -> {
            this.tradeResultMessage = msg;
            if (this.tradeResultSync != null) this.tradeResultSync.setValue(this.tradeResultMessage);
        },
        coinAmountSyncs,
        meCoinAmountSyncs,
        statusCodec::markTradeableStatusDirtyAndNotify,
        () -> this.guiData != null ? this.guiData.getPlayer() : null);
    /** BGM 开关联动（接管 isV2GuiOpen，NekoMusicEventHandler 消费点随迁） */
    private final GuiMusicController musicController = new GuiMusicController();

    /** 交易结果消息（服务端设置，通过 tradeResultSync 同步到客户端） */
    String tradeResultMessage = "";

    // ==================== 预分配 Widget ====================

    /** TILE 模式预分配 Widget：分类 → Widget 列表（每分类 300 个） */
    final Map<NekoTradeCategory, List<NekoTradeItemDisplayWidget>> displayedTradesTiles = new HashMap<>();
    /** LIST 模式预分配 Widget：分类 → Widget 列表（每分类 300 个） */
    final Map<NekoTradeCategory, List<NekoTradeItemDisplayWidget>> displayedTradesList = new HashMap<>();
    /** 高亮标签页集合（搜索匹配时高亮对应标签） */

    // ==================== 其他字段 ====================

    // ==================== B2-02：C2S 动作 Netty→主线程投递 ====================

    /**
     * Netty IO 线程 → 服务器主线程的 C2S 动作队列（同 {@code MailHandler}/{@code LotteryHandler} 范式）。
     * <p>
     * MUI2 2.3.70 网络层 C2S 同步值的 changeListener 在 Netty IO 线程直跑
     * （{@code ModularNetworkSide.receivePacket} 无线程切换），而交易/投币链涉及
     * 共享机器槽读写（NekoTradeExecutor 快照→扣减→整槽写回）、HashMap 标志写与
     * meTransferQueue 写——与主线程 checkTrade（detectAndSendChanges 驱动）/
     * onPostTick 交叉访问存在竞态。服务端动作主体整体 offer 到本队列，
     * 由 {@link MTENekoVendingMachineV2#onPostTick} 服务端分支逐 tick 消费
     * （操作延迟 ≤1 tick，玩家无感）。1 tick 后 GUI 可能已关：闭包内引用的
     * multiblock/baseMetaTileEntity 生命周期独立于 GUI，各动作方法自带存活守卫。
     */
    private static final Queue<Runnable> SERVER_ACTIONS = new ConcurrentLinkedQueue<>();

    /** GUI 位置数据引用（build 时设置，供同步值 getter 使用） */
    private PosGuiData guiData;
    /** 同步管理器引用（供 isClient() 判断和 PanelCallback 使用） */
    private PanelSyncManager syncManagerRef;
    /** 音量/BGM 按钮（客户端，作为音量面板的 parent；v1.6.24 提升为类字段以供 build() 在 client 块外注册 syncedPanel） */
    ButtonWidget<?> volumeButton;
    /** 音量面板处理器（客户端，打开/关闭音量控制面板） */
    IPanelHandler volumePanel;
    /** 主面板引用（用于回调和方法调用） */
    private NekoTradeMainPanel mainPanel;
    /** ME 输出模式同步值（C2S+S2C：客户端切换发送到服务端，服务端状态同步到客户端） */
    BooleanSyncValue meOutputModeSync;
    /** Uplink 连接状态同步值（S2C：控制 ME 模式按钮可见性） */
    BooleanSyncValue hasUplinkSync;
    /** ME 模式切换确认弹框（客户端） */
    NekoConfirmationDialog meModeConfirmDialog;
    /** ME 模式切换确认面板 handler（客户端） */
    IPanelHandler meModeConfirmPanel;
    /** 交易条目删除确认弹框（客户端，v1.7.7 编辑模式删除交易，按钮文本"是/否"） */
    NekoConfirmationDialog deleteTradeConfirmDialog;
    /** 交易条目删除确认面板 handler（客户端） */
    IPanelHandler deleteTradeConfirmPanel;
    /** ME 传输队列同步值（S2C：服务端序列化队列发到客户端，用于粒子动画渲染） */
    private StringSyncValue meTransferQueueSync;
    /** 取回 ME 传输队列物品请求（C2S：客户端点击取回时发送 true） */
    BooleanSyncValue retrieveMeItemSync;
    /** 客户端缓存的 ME 传输队列（从同步值解析，供粒子 Widget 渲染；与 IoColumnPanel 共享实例） */
    private final java.util.List<MeTransferEntry> clientMeTransferQueue = new java.util.ArrayList<>();

    // ==================== A01 蓝图 G6 页族（TradePage/IoColumnPanel） ====================

    /** 贸易主标签页（页面状态/标签列四件套/贸易内容列/五控制器接线，G6 分域下沉） */
    private final TradePage tradePage = new TradePage(this);
    /** 右侧 IO 列面板（双端构建，挂载位点与顺序不变；输入/掉落槽 auto_sync 不迁移） */
    private final IoColumnPanel ioColumnPanel = new IoColumnPanel(
        this,
        multiblock,
        baseMetaTileEntity,
        clientMeTransferQueue);

    // ==================== 编辑模式（v1.7.0 目标 4） ====================

    /** 编辑模式状态同步值（S2C：服务端权威，同步到客户端控制 GUI 行为） */
    private BooleanSyncValue editModeSync;

    /** v1.7.7 G2① 编辑覆盖层控制器（类型枚举/显隐状态/8 面板注册表；A01 蓝图 G1 抽取至 edit 子包） */
    private final EditOverlayController editOverlayController = new EditOverlayController();
    /**
     * 交易条目编辑器（A01 蓝图 G2 抽取至 edit 子包：32 槽缓冲/参数字段/编辑目标同步值）
     * 关闭请求走本类 closeEditOverlay（保留 TRADE 型残留清理钩子），保存成功后回调主面板强制刷新
     */
    final TradeEditor tradeEditor = new TradeEditor(editOverlayController, this::closeEditOverlay, () -> {
        if (mainPanel != null) {
            mainPanel.setForceRefresh();
        }
    });
    /** 标签页编辑器（A01 蓝图 G2 抽取至 edit 子包：名称/图标缓冲/编辑目标同步值） */
    final PageEditor pageEditor = new PageEditor(editOverlayController, this::closeEditOverlay);

    /** 签到编辑器（A01 蓝图 G3 抽取至 edit 子包：tier/cumtier 阶梯 + monthly 每月全局 + 逐日覆盖子面板） */
    private final SignInEditor signInEditor = new SignInEditor(editOverlayController, this::closeEditOverlay);
    /** 每日在线档位编辑器（A01 蓝图 G3 抽取至 edit 子包：add/update/remove 三态） */
    private final OnlineTierEditor onlineTierEditor = new OnlineTierEditor(
        editOverlayController,
        this::closeEditOverlay);

    /** 祝福预设编辑器（A01 蓝图 G4 抽取至 edit 子包：节日表/生日模板/发件人三分派） */
    final BlessingEditor blessingEditor = new BlessingEditor(editOverlayController, this::closeEditOverlay);
    /** 轮盘条目编辑器（A01 蓝图 G4 抽取至 edit 子包：物品奖品/货币/权重/稀有度） */
    final LotteryEntryEditor lotteryEntryEditor = new LotteryEntryEditor(editOverlayController, this::closeEditOverlay);

    // --- 抽奖卡池编辑（v1.7.6 G2①：池级编辑面板 + 动态池标签列） ---
    /** 抽奖卡池编辑器（A01 蓝图 G4 抽取至 edit 子包：新建/更新/删除三态 + 图标/消耗槽） */
    final LotteryPoolEditor lotteryPoolEditor = new LotteryPoolEditor(editOverlayController, this::closeEditOverlay);

    // ==================== 构造器 ====================

    /**
     * 构造猫猫售货机 V2 GUI
     *
     * @param machine 关联的猫猫售货机 V2 机器实例
     */
    public NekoVMGuiV2(MTENekoVendingMachineV2 machine) {
        super(machine);
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
        // 五个分页控制器（A01 蓝图 G6 随页下沉 TradePage，创建位点与时序不变）
        tradePage.createControllers();

        // 创建 NekoTradeMainPanel 作为主面板（实现 PanelCallback 回调）
        NekoTradeMainPanel panel = new NekoTradeMainPanel("MTEMultiBlockBase", this, guiData, syncManager);
        panel.size(PANEL_WIDTH, PANEL_HEIGHT);
        panel.padding(4);
        this.mainPanel = panel;

        // 客户端：启动 BGM、注册关闭回调、设置 GUI 状态
        if (syncManager.isClient()) {
            // BGM 开关联动（A01 蓝图 G5：置打开标志 + 通知 NekoMusicEventHandler）
            musicController.onGuiOpened();
            panel.onCloseAction(() -> {
                // 关闭 BGM 并清理 GUI 打开标志
                musicController.close();
            });
            // 初始化 ME 模式切换确认弹框（仅客户端）
            meModeConfirmDialog = new NekoConfirmationDialog("nekoV2:me_mode_confirm");
            meModeConfirmPanel = IPanelHandler.simple(panel, (parent, player) -> meModeConfirmDialog, true);
            // 初始化交易条目删除确认弹框（仅客户端），并注入 TradeEditor 供删除按钮二次确认
            deleteTradeConfirmDialog = new NekoConfirmationDialog("nekoV2:delete_trade_confirm");
            deleteTradeConfirmPanel = IPanelHandler.simple(panel, (parent, player) -> deleteTradeConfirmDialog, true);
            tradeEditor.setDeleteConfirm(deleteTradeConfirmDialog, deleteTradeConfirmPanel);
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
        panel.child(ioColumnPanel.createIOColumn());

        // ==================== 以下均为仅客户端子树（不得含双端需要同步的槽位）====================

        // v1.7.0 主标签列（贸易/签到/抽奖/邮件），位于贸易分类列的更左边
        if (syncManager.isClient()) {
            panel.child(tradePage.createMainTabColumn());
        }

        // 左侧贸易分类标签列（仅主标签为贸易时显示）
        if (syncManager.isClient()) {
            panel.child(tradePage.createTabColumn());
            panel.child(tradePage.createQolButtonColumn());
            // v1.7.6 G1：签到/抽奖/邮件三页各自的 sub-page 标签列（与贸易分类列同位 left(-29)，
            // 按主标签互斥显示；纯按钮无槽位，仅客户端创建，与 createTabColumn 挂载路径一致）
            panel.child(tradePage.createSubTabColumn(TradePage.MAIN_TAB_SIGNIN));
            panel.child(tradePage.createSubTabColumn(TradePage.MAIN_TAB_LOTTERY));
            panel.child(tradePage.createSubTabColumn(TradePage.MAIN_TAB_MAIL));
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
        // v1.7.27 修复：移除全屏透明拦截层。原 overlayInterceptor 会在点击编辑器外部
        // 任意位置时调用 closeEditOverlay() 并消费事件，导致编辑器意外关闭，并拦截
        // 背包栏/NEI 的鼠标交互。现在仅 Esc、E、保存/取消按钮可关闭编辑器。
        //
        // 【A01 蓝图 G1】覆盖层基础设施（根节点/显隐状态/类型枚举）已抽取至
        // edit/EditOverlayController；8 个面板本体仍在 NekoVMGuiV2（G2-G4 分域搬出）。
        // 注册顺序冻结：TRADE → SIGNIN → SIGNIN_DAY → ONLINE_TIER → LOTTERY
        // → LOTTERY_POOL → PAGE → BLESSING（registerPanel 重复注册防御在位）。
        editOverlayController.registerPanel(EditOverlayType.TRADE, tradeEditor::buildEditPanel);
        editOverlayController.registerPanel(EditOverlayType.SIGNIN, signInEditor::buildEditPanel);
        // v1.7.8 任务6：逐日覆盖编辑面板（SIGNIN_DAY 覆盖层）
        editOverlayController.registerPanel(EditOverlayType.SIGNIN_DAY, signInEditor::buildDayEditPanel);
        editOverlayController.registerPanel(EditOverlayType.ONLINE_TIER, onlineTierEditor::buildEditPanel);
        editOverlayController.registerPanel(EditOverlayType.LOTTERY, lotteryEntryEditor::buildEditPanel);
        editOverlayController.registerPanel(EditOverlayType.LOTTERY_POOL, lotteryPoolEditor::buildEditPanel);
        editOverlayController.registerPanel(EditOverlayType.PAGE, pageEditor::buildEditPanel);
        editOverlayController.registerPanel(EditOverlayType.BLESSING, blessingEditor::buildEditPanel);

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
        panel.child(editOverlayController.getRoot());

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

        // --- 页面状态五通道（当前/主标签/搜索/排序/显示模式，A01 蓝图 G6 下沉 TradePage）---
        tradePage.registerSyncValues(syncManager, playerId);

        // --- 交易请求（C2S，Shift+Click 触发）---
        tradeRequestSync = new StringSyncValue(() -> "", val -> {
            if (val != null && !val.isEmpty()) {
                if (syncManager != null && !syncManager.isClient()) {
                    // B2-02：服务端 C2S 回调在 Netty IO 线程直跑（MUI2 2.3.70 无线程切换），
                    // 交易链整体投递服务器主线程，下一 tick 消费（客户端本地触发不投递）
                    final String request = val;
                    scheduleServerAction(() -> processTradeRequest(request, playerId));
                } else {
                    // 客户端本地触发：processTradeRequest 内部 isClient() 守卫直接返回
                    processTradeRequest(val, playerId);
                }
            }
        });
        tradeRequestSync.allowC2S();
        syncManager.syncValue("nekoV2TradeRequest", tradeRequestSync);

        // --- 收藏切换请求（C2S，Ctrl+Click 触发）---
        favouriteToggleSync = new StringSyncValue(() -> "", val -> {
            if (val != null && !val.isEmpty()) {
                if (syncManager != null && !syncManager.isClient()) {
                    // B2-02：服务端 C2S 回调投递服务器主线程，避免 Netty 线程直改收藏状态
                    final String request = val;
                    scheduleServerAction(() -> processFavouriteToggle(request, playerId));
                } else {
                    // 客户端本地触发：processFavouriteToggle 内部 isClient() 守卫直接返回
                    processFavouriteToggle(val, playerId);
                }
            }
        });
        favouriteToggleSync.allowC2S();
        syncManager.syncValue("nekoV2FavouriteToggle", favouriteToggleSync);

        // --- 交易结果消息（S2C）---
        tradeResultSync = new StringSyncValue(
            () -> tradeResultMessage,
            val -> tradeResultMessage = val == null ? "" : val);
        syncManager.syncValue("nekoV2TradeResult", tradeResultSync);

        // --- 四类 S2C 状态通道（BQ 锁定/冷却/可交易/团队缩放，A01 蓝图 G5 下沉 StatusCodec）---
        statusCodec.registerSyncValues(syncManager, playerId);

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
                coinAmountSyncer.setChangeListener(statusCodec::markTradeableStatusDirtyAndNotify);
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

        // --- 货币/物品操作 C2S 通道（import/eject/fill 动作类，A01 蓝图 G5 下沉 CoinOpsController）---
        coinOps.registerSyncValues(syncManager, playerId);
        // --- ME 输出模式（C2S+S2C）---
        // 客户端可发起切换（allowC2S），服务端持久化并同步状态
        meOutputModeSync = new BooleanSyncValue(() -> multiblock != null && multiblock.isMeOutputMode(), val -> {
            if (syncManager != null && !syncManager.isClient()) {
                // B2-02：服务端 C2S 回调投递服务器主线程，避免 Netty 线程直改 ME 模式
                scheduleServerAction(() -> {
                    if (multiblock == null) return;
                    multiblock.setMeOutputMode(val);
                    // ME 模式切换后通知队列同步值刷新，让客户端立即获知当前队列状态
                    if (meTransferQueueSync != null) {
                        meTransferQueueSync.notifyUpdate();
                    }
                });
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
            if (val && syncManager != null && !syncManager.isClient()) {
                // B2-02：取回动作（meTransferQueue remove(0)）投递服务器主线程，
                // 与 onPostTick 的 processMeTransferQueue 串行化（B2-04 闭合）
                scheduleServerAction(() -> {
                    if (multiblock == null) return;
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
                });
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

        // --- 编辑目标（C2S：交易编辑，A01 蓝图 G2 下沉至 TradeEditor）---
        tradeEditor.registerSyncValues(syncManager);

        // --- 签到编辑目标（C2S：签到编辑，A01 蓝图 G3 下沉至 SignInEditor）---
        signInEditor.registerSyncValues(syncManager);

        // --- 在线档位编辑目标（C2S：在线档位编辑，A01 蓝图 G3 下沉至 OnlineTierEditor）---
        onlineTierEditor.registerSyncValues(syncManager);

        // --- 抽奖编辑目标（C2S：轮盘条目编辑，A01 蓝图 G4 下沉至 LotteryEntryEditor）---
        lotteryEntryEditor.registerSyncValues(syncManager);

        // --- 池编辑目标（C2S：卡池编辑，A01 蓝图 G4 下沉至 LotteryPoolEditor）---
        lotteryPoolEditor.registerSyncValues(syncManager);

        // --- page 编辑目标（C2S：标签页编辑，A01 蓝图 G2 下沉至 PageEditor）---
        pageEditor.registerSyncValues(syncManager);

        // --- 祝福编辑目标（C2S：祝福预设编辑，A01 蓝图 G4 下沉至 BlessingEditor）---
        blessingEditor.registerSyncValues(syncManager);

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
        return tradePage.getSearchText();
    }

    @Override
    public boolean isSearchBarFocused() {
        return tradePage.isSearchBarFocused();
    }

    @Override
    public NekoDisplayType getDisplayType() {
        return tradePage.getDisplayType();
    }

    @Override
    public NekoSortMode getSortMode() {
        return tradePage.getSortMode();
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
            LOG.error("[NekoVMGuiV2] getWalletMode 检测团队状态异常", e);
        }
        return NekoWalletMode.PERSONAL;
    }

    @Override
    public int getRefreshInterval() {
        // 默认 20 tick（1秒）刷新一次
        return 20;
    }

    /**
     * B2-02：将 C2S 同步值的服务端动作主体投递到服务器主线程（Netty 线程调用安全）。
     * <p>
     * 仅服务端侧调用；客户端侧的 changeListener 保持原语义直跑（无服务端动作）。
     */
    static void scheduleServerAction(Runnable action) {
        if (action != null) {
            SERVER_ACTIONS.offer(action);
        }
    }

    /**
     * B2-02：服务器主线程逐 tick 消费投递的 C2S 动作。
     * <p>
     * 由 {@link MTENekoVendingMachineV2#onPostTick} 服务端分支调用；单任务异常仅记日志，
     * 不中断同批其余任务（对齐 MailHandler 消费循环）。
     */
    public static void drainServerActions() {
        Runnable action;
        while ((action = SERVER_ACTIONS.poll()) != null) {
            try {
                action.run();
            } catch (Throwable t) {
                LOG.error("[NekoVMV2] 执行投递的 C2S 动作失败", t);
            }
        }
    }

    @Override
    public boolean isClient() {
        return syncManagerRef != null && syncManagerRef.isClient();
    }

    /** 机器是否处于 active 状态（页族构建访问器，原 createTradeListPage 直引 baseMetaTileEntity） */
    boolean isMachineActive() {
        return baseMetaTileEntity != null && baseMetaTileEntity.isActive();
    }

    @Override
    public long getSyncedCooldownRemaining(UUID groupId, int tradeIndex) {
        if (groupId == null || tradeIndex < 0) {
            return 0L;
        }
        String key = groupId.toString() + ":" + tradeIndex;
        return statusCodec.getCooldownRemaining(key);
    }

    @Override
    public NekoTradeCategory getActiveCategory() {
        return tradePage.getActiveCategory();
    }

    @Override
    public Boolean getSyncedTradeableStatus(UUID groupId, int tradeIndex) {
        if (groupId == null) return null;
        String key = groupId.toString() + ":" + tradeIndex;
        return statusCodec.getTradeable(key);
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
                boolean bqLocked = statusCodec.getBqLocked(display.getGroupId());
                display.setBqLocked(bqLocked);

                // 覆盖冷却状态（来自 cooldownStatusSync）
                String cdKey = display.getGroupId()
                    .toString() + ":"
                    + display.getTradeIndex();
                long cooldown = statusCodec.getCooldownRemaining(cdKey);
                display.setCooldownRemaining(cooldown);

                // 设置团队缩放信息（来自 teamScaleSync）
                // 客户端通过同步值获取冷却内最大次数和已用次数，用于 tooltip 展示
                long[] scaleInfo = statusCodec.getTeamScale(display.getGroupId());
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
                Boolean syncedTradeable = statusCodec.getTradeable(tradeableKey);
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
        tradePage.updateTabHighlighting(trades);
    }

    @Override
    public void onRestoreSettings() {
        tradePage.restoreSettings();
    }

    @Override
    public void onDispose() {
        // 作为 panel.onCloseAction 的兜底保险：
        // 当 ModularUI 真正关闭/释放屏幕时，确保 BGM 能正常触发淡出
        musicController.close();
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
        tradeEditor.beginEdit(display);
    }

    /**
     * 检查当前是否处于编辑模式
     * <p>
     * 通过 {@link #editModeSync} 同步值判断（服务端权威，S2C 同步到客户端）。
     *
     * @return true 表示处于编辑模式
     */
    boolean isEditModeActive() {
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
        editOverlayController.open(type);
    }

    /**
     * 检查当前是否有编辑覆盖层处于打开状态（v1.7.27）
     *
     * @return true 表示任意编辑器面板正在显示
     */
    @Override
    public boolean isEditOverlayOpen() {
        return editOverlayController.isOpen();
    }

    /**
     * 关闭当前编辑覆盖层（v1.7.7 G2①，v1.7.27 提升为 public 并加入 PanelCallback）
     * <p>
     * 关闭后恢复主内容交互，并清空交易编辑面板的客户端残留状态。
     */
    @Override
    public void closeEditOverlay() {
        EditOverlayType previous = editOverlayController.getCurrent();
        editOverlayController.close();
        if (previous == EditOverlayType.TRADE) {
            tradeEditor.clearTradeEditState();
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
    /**
     * 将 ItemStack 序列化为编辑用 JSON（含 NBT）
     * <p>
     * 编辑面板的 PhantomItemSlot 可能放入带 NBT 的物品，
     * NBT 以 Base64 编码随 JSON 发送到服务端保存。
     *
     * @param stack 物品堆
     * @return JSON 对象 {item, meta, amount, nbtBase64?}
     */
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
                signInEditor.beginTier(tier, false);
            }

            @Override
            public void onEditCumulativeTierRequested(com.miaokatze.gtit.signin.SignInRewardTier tier) {
                signInEditor.beginTier(tier, true);
            }

            @Override
            public void onEditDayRewardRequested(String date) {
                signInEditor.beginDay(date);
            }

            @Override
            public void onEditGlobalRequested() {
                signInEditor.beginGlobal();
            }

            @Override
            public void onEditOnlineTierRequested(com.miaokatze.gtit.signin.OnlineTimeRewardTier tier) {
                onlineTierEditor.beginTier(tier);
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
    /**
     * 打开逐日覆盖编辑面板（客户端，编辑模式下点击每月签到日期格触发，v1.7.8 任务6）
     * <p>
     * 预填生效奖励（覆盖优先，否则工作日/周末默认；读 {@link SignInClientData} 同步快照），
     * 物品槽由客户端本地预填（快照含完整物品 ID/meta/NBT，无需服务端往返）；
     * 保存后服务端写入 day_overrides（按每月日号生效）。
     *
     * @param date 被点击的日期（"yyyy-MM-dd"，当月有效日期）
     */
    /**
     * 打开每月签到全局配置编辑面板（客户端，编辑模式下点击「全局配置」按钮触发，v1.7.8 任务6）
     * <p>
     * 从 {@link SignInClientData} 同步快照读取递增开关/系数与工作日/周末默认奖励填充字段；
     * 物品子模式默认工作日——周末默认物品进暂存、工作日默认物品进编辑缓冲区（客户端本地填充，
     * 快照含完整物品 ID/meta/NBT，无需服务端往返）。
     */
    /**
     * 服务端：加载签到阶梯物品奖励到编辑缓冲区（4 槽）
     * <p>
     * 解析 "tier:&lt;days&gt;" / "cumtier:&lt;days&gt;" 目标标识，从 {@code DailySignInConfig}
     * 查找连续/累计阶梯，将其物品奖励（含 NBT）按序放入编辑缓冲区，未命中则清空。
     *
     * @param target "tier:&lt;days&gt;" 或 "cumtier:&lt;days&gt;" 格式的目标标识
     */
    // ==================== 签到编辑：物品缓冲区辅助（v1.7.8 任务5+6） ====================

    /**
     * 将统一奖励模型中的物品（最多 4 个）填入指定签到编辑物品缓冲区
     * <p>
     * 先清空缓冲区，再逐条解析 {@link RewardItem}（物品 ID + meta + NBT）按序填充，
     * 空条目/解析失败跳过。双端共用（客户端预填 / 服务端加载均走本方法）。
     *
     * @param handler 目标物品缓冲区（4 槽）
     * @param reward  统一奖励模型（null 时仅清空缓冲区）
     */
    /**
     * 将统一奖励模型中的物品（最多 4 个）解析进每月全局「非激活子模式」暂存
     *
     * @param reward 统一奖励模型（null 时清空暂存）
     */
    /**
     * 切换每月全局物品子模式（工作日 ↔ 周末）
     * <p>
     * 编辑缓冲区（激活子模式物品）与暂存（非激活子模式物品）整体互换；
     * 货币字段经动态绑定自动跟随 {@link #editSignInMonthlyWeekend}。
     *
     * @param weekend true=切换到周末默认奖励 / false=切换到工作日默认奖励
     */
    /**
     * 读取签到编辑缓冲区 4 槽快照（供每月全局保存时序列化激活子模式物品）
     *
     * @return 4 槽物品数组（空槽为 null）
     */
    /**
     * 将奖励物品条目解析为 ItemStack（物品 ID + meta + NBT；v1.7.7 G5① NBT 还原口径）
     *
     * @param rewardItem 奖励物品条目
     * @return 物品栈（含 NBT），解析失败返回 null
     */
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
    /**
     * 将货币 + 物品槽数组序列化为统一奖励模型 JSON（与 {@link SignInReward#toJson()} 同构：
     * {@code {currency, amount, items:[{item, amount, meta, nbt}]}}；空槽跳过，NBT 经 Base64 编码）
     *
     * @param currency 货币 ID（null 按空串）
     * @param amount   货币数量（&lt;0 按 0）
     * @param stacks   物品槽数组（null 元素/空槽跳过）
     * @return 奖励 JSON 对象
     */
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
    /**
     * 保存逐日覆盖编辑（客户端 → 服务端）
     * <p>
     * 序列化 {@code {operation, reward}}，targetId = "day:" + 月内日号；
     * operation=remove 时不携带 reward（服务端清除该日号覆盖，回退工作日/周末默认）。
     * 经 {@link com.miaokatze.gtit.trade.v2.NekoEditNetworkManager#sendSaveSignInReward} 发送。
     *
     * @param operation 操作类型：update / remove
     */
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
    /**
     * 服务端：加载在线档位物品奖励到编辑缓冲区（v1.7.7 G5②）
     * <p>
     * 解析目标秒数，从 {@code OnlineTimeConfig} 查找档位，
     * 有物品奖励则构建 ItemStack（含 NBT）放入 slot 0，否则清空。
     *
     * @param target 秒数字符串
     */
    /**
     * 构建在线档位编辑面板（v1.7.7 G5②）
     * <p>
     * 字段布局：所需秒数、货币 ID、货币数量、物品奖励 PhantomItemSlot。
     * 保存时支持更新/新增/删除三种操作。
     *
     * @return 编辑覆盖层面板（{@link ParentWidget}）
     */
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
    /**
     * 循环切换祝福编辑目标（客户端，面板内「< / >」按钮）
     * <p>
     * 目标序列：birthday → festival:0 → festival:1 → … → festival:N-1 → birthday。
     * 切换不会自动保存当前修改（与池/page 切换口径一致），tooltip 已提示先保存。
     *
     * @param delta +1 = 下一个，-1 = 上一个
     */
    /**
     * 从本地 {@code BlessingConfig} 填充祝福编辑字段（客户端预览口径）
     * <p>
     * 发件人字段每次从配置重读；节日目标额外填充名称/触发日期。
     * 目标非法时回退生日模板，防配置热改后索引越界。
     *
     * @param target 目标标识（"birthday" / "festival:&lt;index&gt;"）
     */
    /**
     * 服务端：加载祝福目标的附件物品到编辑缓冲区
     * <p>
     * 解析 "birthday" / "festival:&lt;index&gt;" 目标标识，从 {@code BlessingConfig}
     * 取配置附件（不含猫猫币——币由货币字段表达）放入 slot 0-4，多余槽位清空。
     *
     * @param target 目标标识
     */
    /**
     * 当前祝福目标的展示名（面板标题/切换行用）
     */
    /**
     * 构建祝福预设编辑面板
     * <p>
     * 单面板结构：标题（含当前目标名）→ 目标切换行（< 目标 >）→ 发件人 →
     * 名称/触发日期（仅节日目标可见）→ 标题 → 正文 → 货币类型/数量 →
     * 附件物品槽（{@value #BLESSING_ITEM_SLOTS} 格 PhantomItemSlot）→ 保存/取消。
     *
     * @return 编辑覆盖层面板（{@link ParentWidget}）
     */
    /**
     * 保存祝福预设编辑（客户端 → 服务端）
     * <p>
     * 先提交发件人（"sender" 目标），再提交当前祝福目标（"birthday"/"festival:&lt;index&gt;"）：
     * 附件物品取自 PhantomItemSlot 缓冲区序列化为 items 数组
     * {@code [{"item":"modid:name","meta":0,"amount":1}]}；空槽跳过。
     * 经 {@link com.miaokatze.gtit.trade.v2.NekoEditNetworkManager#sendSaveBlessing} 发送。
     */
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
                lotteryEntryEditor.beginEntry(pool, entry, slotIndex);
            }
        };
    }

    private void initPreAllocatedWidgets() {
        for (NekoTradeCategory category : tradePage.getTradeCategories()) {
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
            .controller(tradePage.getMainTabController());

        // 页 0：贸易（现有 createMainColumn 的内容）
        mainPaged.addPage(tradePage.createTradeMainColumn(syncManager));

        // 页 1：签到「活跃」大页（v1.7.6 G2③ 重构：内层 4 sub-page——每月签到/连续签到/每日在线/纪念日；
        // v1.7.0 目标 4 传入编辑模式回调；signInPageController 绑定内层 PagedWidget，左侧 sub-tab 标签列同控制器接管切换）
        mainPaged.addPage(
            SignInCalendarGui.createSignInPage(createSignInEditCallback(), tradePage.getSignInPageController()));

        // 页 2：抽奖（v1.7.1 目标 2 实现，轮盘 GUI；出货槽定位依赖机器坐标；
        // v1.7.0 目标 4 传入编辑模式回调：编辑模式下点击轮盘槽位弹出条目编辑面板）
        mainPaged.addPage(LotteryGui.createLotteryPage(baseMetaTileEntity, createLotteryEditCallback()));

        // 页 3：邮件（v1.7.6 G2② 重构：类型分页 + 写邮件；传入机器定位=写邮件附件输入槽来源，
        // mailPageController 绑定内层 5 sub-page，左侧 sub-tab 标签列同控制器接管切换）
        mainPaged.addPage(MailGui.createMailPage(baseMetaTileEntity, tradePage.getMailPageController()));

        return mainPaged;
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
            LOG.debug("[NekoParticle] parseMeTransferQueue: empty data");
            return;
        }
        try {
            String[] entries = data.split(";");
            // v1.6.24 临时日志：确认非空数据到达客户端
            LOG.debug("[NekoParticle] parseMeTransferQueue: dataLen=" + data.length() + ", entries=" + entries.length);
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
                    clientMeTransferQueue.add(new MeTransferEntry(stack, creationTime, slotIndex));
                }
            }
            // v1.6.24 临时日志：确认解析后队列大小
            LOG.debug("[NekoParticle] parseMeTransferQueue: parsedQueueSize=" + clientMeTransferQueue.size());
        } catch (Exception e) {
            LOG.error("[NekoVMV2] parseMeTransferQueue 解析失败", e);
        }
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
                coinOps.playTradeSuccessSound();
                // v1.7.12：移除此处的 forceSyncOutputSlots——交易成功时物品尚未落槽（仍在 outputBuffer），
                // 同步空槽无意义。物品落槽依赖 ModularContainer.detectAndSendChanges 原生同步
                // （ItemSlotSH.checkUpdate 检测到变化后发 SYNC_ITEM，客户端 changeListener 触发下落动画）。
                // 复刻 VM 父类 sendTradeUpdate：交易成功后显式触发同步，
                // 让客户端立即收到最新的可交易状态、冷却状态和交易结果。
                // 可交易置脏 + 可交易/冷却/团队缩放三通道级联（A01 蓝图 G5 收编 StatusCodec）
                statusCodec.notifyTradeStatusChanged();
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
            LOG.error("[NekoVMV2] processTradeRequest 异常!", t);
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
            LOG.error("[NekoVMV2] processFavouriteToggle 异常!", t);
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
}

package com.miaokatze.gtit.client.gui;

import java.io.File;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ConcurrentLinkedQueue;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;

import com.cleanroommc.modularui.api.IGuiHolder;
import com.cleanroommc.modularui.api.IPanelHandler;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.factory.GuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.utils.item.ItemStackHandler;
import com.cleanroommc.modularui.value.sync.BooleanSyncValue;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.StringSyncValue;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.SlotGroupWidget;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.cleanroommc.modularui.widgets.slot.ItemSlot;
import com.cleanroommc.modularui.widgets.slot.ModularSlot;
import com.miaokatze.gtit.common.api.enums.GTITItemList;
import com.miaokatze.gtit.reincarnation.ReincarnationConfirmHook;
import com.miaokatze.gtit.reincarnation.core.ReincarnationCycle;
import com.miaokatze.gtit.reincarnation.core.ReincarnationStore;

/**
 * 周目轮回 GUI（v1.9.0 C批 S7，MUI2 2.3.88）
 * <p>
 * 右击轮回水晶打开（打开链路见 {@link ReincarnationGuiOpener}）。
 * <b>全状态持久化在 {@link ReincarnationStore}（所有水晶共享同一界面，状态不在物品 NBT）</b>；
 * GUI 打开时服务端 {@code store.load(uuid)} 建立会话，每次模型变更即 {@code store.save}。
 * <p>
 * <b>同步机制选型（任务包二选一裁决）</b>：选择 <b>MUI2 {@code PanelSyncManager}</b>
 * （{@code IntSyncValue}/{@code BooleanSyncValue}/{@code StringSyncValue} + 槽位同步），
 * 而非 {@code ReincarnationSyncPacket} 快照包——理由：本 GUI 是双向交互界面（物品放入/取出、
 * 外壳消耗、解锁判定、确认轮回），快照包仅覆盖只读展示且其 holder
 * （{@code ClientReincarnationFxState}）为仅客户端类，不能被双端构建的 panel 引用。
 * 快照包继续服务 S8 演出切片，与本切片无关。
 * <p>
 * <b>双端镜像构建纪律</b>（先例 {@code NekoVMGuiV2} v1.7.17）：{@code buildUI} 双端各跑一次，
 * widget 树两侧完全一致（61 个槽位 + 背包行，无仅客户端子树），auto_sync 槽位 ID 不偏移；
 * 确认弹窗（{@link NekoConfirmationDialog}）不在 widget 树内（IPanelHandler 注册），无 ID 影响。
 * 动态 Supplier 双端安全：读同步值（服务端=源，客户端=缓存）与 {@link StatCollector}（common 类）。
 * <p>
 * <b>交互语义</b>（与 S4 契约对齐）：
 * <ul>
 * <li>外壳累计槽（每列 1 格）：放入匹配外壳（{@link ReincarnationHullMatcher}）即消耗 1 件并
 * {@code recordHullConsumption(column,1)}，达 {@link #HULL_TARGET}=16 自动
 * {@code unlockColumn}（槽位清空只留计数，UI 显示 n/16）；</li>
 * <li>物品格（每列 3 格 × 全局行解锁）：放入 = 记录 {@code ItemRef(id+meta, 忽略 NBT)}；
 * 取出 = {@code withdraw}（S4 契约，落地前经 {@link #withdrawBridge} 桥接）；
 * 每格最大堆叠强制 1；首件放入且状态为 IDLE 时经 {@code deposit} 过渡
 * IDLE→DEPOSITED（确认按钮「DEPOSITED 且非空」契约的前置）；</li>
 * <li>猫猫币支付格：只接受 {@code GTITItemList.NekoCoin / ShimmeringNekoCoin}（可堆叠），
 * 关闭 GUI 时未消耗的币退回玩家（服务端 close listener）；</li>
 * <li>解锁行按钮 ×2：服务端扣 1 币 → {@link Random} 判定
 * （闪烁 1/1000=0.1%，普通 1/100000=0.001%）→ 成功 {@code setUnlockedRows+1}；
 * unplaced 行满 3 或存在未解锁列时按钮禁用置灰，全解锁后永久禁用（"不再需要猫猫币"）；</li>
 * <li>确认轮回按钮：{@code cycleState==DEPOSITED 且 pendingItems 非空 且钩子已注入}
 * 才可用；点击经 {@link NekoConfirmationDialog} 一层二次确认后走 C2S synced action，
 * 服务端回调 {@link ReincarnationConfirmHook#onConfirmRequested}（S4 实现）。EXECUTED
 * 状态（存在未领取轮回，即快照包语义的 mutextLock）下全部槽位与按钮禁用、顶部横幅提示
 * 当前轮回状态——<b>口径说明</b>：任务包中「mutextLock 时全部禁用」与「DEPOSITED 时确认
 * 按钮可用」互斥，本切片把只读锁解释为 EXECUTED（DEPOSITED 仍需调整物品并确认），
 * 若需更严口径由 S4 收束时统一调整。</li>
 * </ul>
 * <p>
 * <b>布局决策（javadoc 记录，任务包要求）</b>：15 列取<b>单排横排</b>（列距 20px = 18px 槽 + 2px 间隙，
 * 网格总宽 298px，面板宽 {@value #PANEL_WIDTH}px），不做 8+7 双排或横向滚动——MUI2 面板宽度
 * 无 176px 限制（{@code ModularPanel.defaultPanel(name, w, h)} 任意尺寸，先例 GT 大型机器 GUI），
 * 单排在常规 16:9 视口（GUI 缩放 2 时约 480x270 虚拟像素）内完整可见，且列序与
 * {@code ReincarnationCycle} 的 0..14 下标线性对应，代码与视觉均无双排映射复杂度。
 * 贴图全部复用 MUI2 默认槽位渲染与 {@link NekoGuiTextures#TEXT_FIELD_BACKGROUND}（不新做贴图），
 * 金色点缀用 {@link EnumChatFormatting#GOLD}/{@code §e} 主题色值。
 * <p>
 * <b>侧与加载（import 自查标注）</b>：本类零 {@code net.minecraft.client} 引用，物理集成服务端
 * 可加载（MUI2 双端构建所需）；物理专用服务器上周目系统整体门控，本类不可达。
 * 不进入任何 common 静态构造路径——唯一外部入口 {@link ReincarnationGuiOpener#openFor} 仅由
 * S4 在 {@code ReincarnationCrystal.onItemRightClick} 的服务端分支调用。
 */
public class ReincarnationCycleGui implements IGuiHolder<GuiData> {

    // ==================== 单例（SimpleGuiFactory 持有） ====================

    /** 单例 holder（工厂注册用；无实例状态，会话对象按次构建） */
    public static final ReincarnationCycleGui INSTANCE = new ReincarnationCycleGui();

    private ReincarnationCycleGui() {}

    // ==================== 布局常量 ====================

    /** 面板宽（15 列 × 20px 列距 + 两侧余量） */
    public static final int PANEL_WIDTH = 320;
    /** 面板高（网格区 + 动作区 + 背包行） */
    public static final int PANEL_HEIGHT = 258;

    /** 外壳解锁阈值：每列累计消耗 16 个外壳后解锁该列 */
    public static final int HULL_TARGET = 16;

    /** 闪烁猫猫币解锁成功率分母：1/1000 = 0.1%（java.util.Random 判定，nextMod==0 即成功） */
    public static final int SHIMMER_UNLOCK_ONE_IN = 1000;
    /** 普通猫猫币解锁成功率分母：1/100000 = 0.001% */
    public static final int NORMAL_UNLOCK_ONE_IN = 100000;

    /** 列距（18px 槽 + 2px 间隙） */
    private static final int SLOT_PITCH = 20;
    /** 槽位边长（MUI2 ItemSlot.SIZE） */
    private static final int SLOT_SIZE = 18;
    /** 网格左缘（(320 - 15*20 + 2) / 2 = 11，网格总宽 298） */
    private static final int GRID_X = 11;

    /** 顶部只读/状态横幅 Y */
    private static final int BANNER_Y = 2;
    /** 标题 Y */
    private static final int TITLE_Y = 13;
    /** 外壳累计槽行 Y */
    private static final int HULL_Y = 26;
    /** 外壳进度 n/16 标记行 Y */
    private static final int PROGRESS_Y = 45;
    /** 物品格首行 Y（3 行，行距 {@link #SLOT_PITCH}） */
    private static final int ITEMS_Y = 53;
    /** 列名标签行 Y */
    private static final int LABELS_Y = 114;
    /** 动作行 Y（猫猫币格 + 2 解锁按钮） */
    private static final int ACTION_Y = 124;
    /** 面板消息行 Y */
    private static final int MESSAGE_Y = 144;
    /** 确认轮回按钮 Y */
    private static final int CONFIRM_Y = 154;
    /** 底部提示行 Y（忽略 NBT / 混淆加密） */
    private static final int FOOTER_Y = 172;
    /** 背包行 Y */
    private static final int INVENTORY_Y = 181;

    // ==================== 同步/动作通道名 ====================

    /** 状态枚举 ordinal（S→C） */
    private static final String SYNC_STATE = "gtitReincarnation:state";
    /** 全局已解锁行数（S→C） */
    private static final String SYNC_ROWS = "gtitReincarnation:rows";
    /** 待领取（已寄存）物品数（S→C） */
    private static final String SYNC_PENDING = "gtitReincarnation:pending";
    /** 面板消息（key+args 载荷，S→C，客户端本地格式化保证双语） */
    private static final String SYNC_MESSAGE = "gtitReincarnation:message";
    /** 尝试解锁（闪烁猫猫币，C2S） */
    private static final String ACTION_UNLOCK_SHIMMER = "gtitReincarnation:unlockShimmer";
    /** 尝试解锁（普通猫猫币，C2S） */
    private static final String ACTION_UNLOCK_NORMAL = "gtitReincarnation:unlockNormal";
    /** 确认轮回（C2S，经二次确认弹窗后触发） */
    private static final String ACTION_CONFIRM = "gtitReincarnation:confirm";

    /** 物品格槽组名（注册 shift 转移，行长度 = 列数） */
    private static final String ITEM_SLOT_GROUP = "gtitReincarnation:items";

    /** 周目状态 ordinal：EXECUTED（只读锁，见类 javadoc 口径说明） */
    private static final int STATE_EXECUTED_ORDINAL = ReincarnationCycle.CycleState.EXECUTED.ordinal();

    // ==================== lang 键 ====================

    private static final String KEY_PREFIX = "gtit.reincarnation.";
    private static final String KEY_TITLE = KEY_PREFIX + "gui.title";
    private static final String KEY_BANNER_IDLE = KEY_PREFIX + "gui.banner.idle";
    private static final String KEY_BANNER_DEPOSITED = KEY_PREFIX + "gui.banner.deposited";
    private static final String KEY_BANNER_EXECUTED = KEY_PREFIX + "gui.banner.executed";
    private static final String KEY_PROGRESS = KEY_PREFIX + "gui.progress";
    private static final String KEY_HULL_TOOLTIP = KEY_PREFIX + "gui.hull.tooltip";
    private static final String KEY_HULL_TOOLTIP_DONE = KEY_PREFIX + "gui.hull.tooltip.done";
    private static final String KEY_ITEM_TOOLTIP = KEY_PREFIX + "gui.item.tooltip";
    private static final String KEY_IGNORE_NBT = KEY_PREFIX + "gui.ignore_nbt";
    private static final String KEY_ENCRYPTED = KEY_PREFIX + "gui.encrypted";
    private static final String KEY_COIN_TOOLTIP = KEY_PREFIX + "gui.coin.tooltip";
    private static final String KEY_UNLOCK_SHIMMER = KEY_PREFIX + "gui.unlock.shimmer";
    private static final String KEY_UNLOCK_NORMAL = KEY_PREFIX + "gui.unlock.normal";
    private static final String KEY_UNLOCK_TOOLTIP_LINE = KEY_PREFIX + "gui.unlock.tooltip.line";
    private static final String KEY_UNLOCK_TOOLTIP_SHIMMER = KEY_PREFIX + "gui.unlock.tooltip.shimmer";
    private static final String KEY_UNLOCK_TOOLTIP_NORMAL = KEY_PREFIX + "gui.unlock.tooltip.normal";
    private static final String KEY_UNLOCK_TOOLTIP_COLUMNS = KEY_PREFIX + "gui.unlock.tooltip.columns";
    private static final String KEY_UNLOCK_TOOLTIP_DONE = KEY_PREFIX + "gui.unlock.tooltip.done";
    private static final String KEY_CONFIRM_BUTTON = KEY_PREFIX + "gui.confirm.button";
    private static final String KEY_CONFIRM_TOOLTIP_STATE = KEY_PREFIX + "gui.confirm.tooltip.state";
    private static final String KEY_CONFIRM_TOOLTIP_EMPTY = KEY_PREFIX + "gui.confirm.tooltip.empty";
    private static final String KEY_CONFIRM_TOOLTIP_NOHOOK = KEY_PREFIX + "gui.confirm.tooltip.nohook";
    private static final String KEY_CONFIRM_DIALOG = KEY_PREFIX + "gui.confirm.dialog";
    private static final String KEY_COLUMN_PREFIX = KEY_PREFIX + "column.";
    private static final String KEY_CHAT_COLUMN_UNLOCKED = KEY_PREFIX + "chat.column_unlocked";
    private static final String KEY_CHAT_HULL_CONSUMED = KEY_PREFIX + "chat.hull.consumed";
    private static final String KEY_CHAT_UNLOCK_SUCCESS = KEY_PREFIX + "chat.unlock.success";
    private static final String KEY_CHAT_UNLOCK_FAIL = KEY_PREFIX + "chat.unlock.fail";
    private static final String KEY_CHAT_UNLOCK_NO_COIN = KEY_PREFIX + "chat.unlock.no_coin";
    private static final String KEY_CHAT_READONLY = KEY_PREFIX + "chat.readonly";
    private static final String KEY_CHAT_DEPOSIT_FAILED = KEY_PREFIX + "chat.deposit.failed";
    private static final String KEY_CHAT_WITHDRAW_FAILED = KEY_PREFIX + "chat.withdraw.failed";

    /** 消息载荷分隔符（key + args 经 StringSyncValue 传输，客户端本地 StatCollector 格式化） */
    private static final char MESSAGE_ARG_SEPARATOR = '￁';

    /** 解锁判定 RNG（任务包冻结：java.util.Random；仅服务端逻辑线程使用） */
    private static final Random UNLOCK_RNG = new Random();

    // ==================== IGuiHolder ====================

    /**
     * 构建 GUI 面板（双端各执行一次）
     * <p>
     * 服务端建立 {@link Session}（load 周目记录 + 槽位缓冲填充）；客户端建立镜像会话
     * （空记录，展示走同步值）。widget 树双端一致。
     */
    @Override
    public ModularPanel buildUI(GuiData guiData, PanelSyncManager syncManager, UISettings uiSettings) {
        Session session = new Session(guiData.getPlayer(), !syncManager.isClient());
        if (!session.client) {
            // 服务端：加载持久化周目记录（最后仁慈路径兜底空记录，见 ReincarnationStore）
            session.store = new ReincarnationStore(new File("."));
            session.cycle = session.store.load(
                session.player.getUniqueID()
                    .toString());
            // 未消耗的猫猫币退回玩家 + 收束保存（容器关闭回调，服务端语义）
            syncManager.addCloseListener(player -> onCloseSession(session, player));
        } else {
            session.cycle = new ReincarnationCycle(
                session.player.getUniqueID()
                    .toString());
        }

        registerSyncValues(session, syncManager);

        ModularPanel panel = ModularPanel.defaultPanel("gtit.reincarnation.main", PANEL_WIDTH, PANEL_HEIGHT);
        panel.child(createBanner(session));
        panel.child(createTitle());
        panel.child(createGrid(session, syncManager));
        panel.child(createActionRow(session, syncManager));
        panel.child(createMessageLine(session));
        panel.child(createConfirmButton(session, syncManager, panel));
        panel.child(createFooter());
        panel.child(
            SlotGroupWidget.playerInventory(false)
                .pos((PANEL_WIDTH - 9 * SLOT_SIZE) / 2, INVENTORY_Y));

        if (!session.client) {
            registerServerActions(session, syncManager);
            fillItemSlotsFromCycle(session);
        }
        return panel;
    }

    // ==================== 同步值注册 ====================

    /**
     * 注册全部 S→C 同步值（显式命名，与 BFS auto_sync 无关；双端同名配对）
     */
    private void registerSyncValues(Session session, PanelSyncManager syncManager) {
        syncManager.syncValue(SYNC_STATE, session.stateSync);
        syncManager.syncValue(SYNC_ROWS, session.rowsSync);
        syncManager.syncValue(SYNC_PENDING, session.pendingSync);
        syncManager.syncValue(SYNC_MESSAGE, session.messageSync);
        for (int i = 0; i < ReincarnationCycle.COLUMN_COUNT; i++) {
            syncManager.syncValue(SYNC_STATE + ":hull" + i, session.hullSync[i]);
            syncManager.syncValue(SYNC_STATE + ":col" + i, session.columnSync[i]);
        }
    }

    // ==================== 会话 ====================

    /**
     * 单次 GUI 打开的会话（双端各一实例）
     * <p>
     * 服务端：{@code cycle} 为权威模型、{@code store} 持久化、槽位缓冲为交互缓冲；
     * 客户端：{@code cycle} 恒为空记录（禁止写），展示走同步值与槽位同步。
     */
    private static final class Session {

        /** 触发玩家 */
        final EntityPlayer player;
        /** true = 客户端镜像会话 */
        final boolean client;
        /** 权威周目模型（服务端）/空记录（客户端） */
        ReincarnationCycle cycle;
        /** 持久化仓库（仅服务端；baseDir = MC 运行目录） */
        ReincarnationStore store;

        /** 外壳累计槽缓冲（每列 1 格，放入即消耗清空） */
        final FixedOneStackHandler hullHandler = new FixedOneStackHandler(ReincarnationCycle.COLUMN_COUNT);
        /** 物品格缓冲（15 列 × 3 行，index = col*3+row） */
        final FixedOneStackHandler itemHandler = new FixedOneStackHandler(
            ReincarnationCycle.COLUMN_COUNT * ReincarnationCycle.MAX_UNLOCKED_ROWS);
        /** 猫猫币支付格缓冲（可堆叠；关闭时退回） */
        final ItemStackHandler coinHandler = new ItemStackHandler(1);
        /** 物品格槽位对应的权威 ItemRef 快照（服务端；取出时定位 withdraw 目标） */
        final ReincarnationCycle.ItemRef[] itemSlotRefs = new ReincarnationCycle.ItemRef[itemHandler.getSlots()];

        /** 周目状态 ordinal（S→C） */
        final IntSyncValue stateSync;
        /** 已解锁行数（S→C） */
        final IntSyncValue rowsSync;
        /** 待领取物品数（S→C） */
        final IntSyncValue pendingSync;
        /** 15 列外壳进度（S→C） */
        final IntSyncValue[] hullSync;
        /** 15 列解锁标记（S→C） */
        final BooleanSyncValue[] columnSync;
        /** 面板消息（key+args 载荷，S→C） */
        final StringSyncValue messageSync;
        /** 消息载荷（key￁arg1￁arg2…；"" = 无消息） */
        volatile String messagePayload = "";
        /** 确认弹窗面板句柄（仅客户端赋值） */
        IPanelHandler confirmPanel;
        /**
         * 服务端主线程任务队列（先例 NekoVMGuiV2 SERVER_ACTIONS 同款模式）：
         * C2S 槽位同步/synced action 在 Netty 线程到达，模型变更与保存统一投递本队列，
         * 由 {@code syncManager.onServerTick}（MUI2 Container onUpdate → tickListener，
         * 服务端主线程逐 tick）排空执行。
         */
        final ConcurrentLinkedQueue<Runnable> serverTasks = new ConcurrentLinkedQueue<>();

        Session(EntityPlayer player, boolean client) {
            this.player = player;
            this.client = client;
            this.stateSync = new IntSyncValue(
                () -> cycle.getCycleState()
                    .ordinal());
            this.rowsSync = new IntSyncValue(cycle::getUnlockedRows);
            this.pendingSync = new IntSyncValue(
                () -> cycle.getPendingItems()
                    .size());
            this.hullSync = new IntSyncValue[ReincarnationCycle.COLUMN_COUNT];
            this.columnSync = new BooleanSyncValue[ReincarnationCycle.COLUMN_COUNT];
            for (int i = 0; i < ReincarnationCycle.COLUMN_COUNT; i++) {
                final int column = i;
                this.hullSync[i] = new IntSyncValue(() -> cycle.getHullProgress()[column]);
                this.columnSync[i] = new BooleanSyncValue(() -> cycle.isColumnUnlocked(column));
            }
            this.messageSync = new StringSyncValue(
                () -> messagePayload,
                value -> messagePayload = value == null ? "" : value);
        }
    }

    /**
     * 单格单件缓冲（getSlotLimit/getStackLimit 恒 1，强制「每格最大堆叠数 1」）
     */
    private static final class FixedOneStackHandler extends ItemStackHandler {

        FixedOneStackHandler(int size) {
            super(size);
        }

        @Override
        public int getSlotLimit(int slot) {
            return 1;
        }

        @Override
        protected int getStackLimit(int slot, ItemStack stack) {
            return 1;
        }
    }

    // ==================== widget 构建 ====================

    /**
     * lang → 本地化 IKey（统一 {@code StatCollector.translateToLocalFormatted} 消费口径）。
     * <p>
     * <b>仓规对齐</b>：含字面 {@code %} 的 lang 值一律写作 {@code %%}（如 {@code 0.1%%}），
     * 本 helper 走格式化路径渲染为 {@code %}；纯文本键经同一 helper 无副作用。
     * （不使用 {@code IKey.lang}：其无参重载的 translateToLocal/translateFormatted 路径
     * 在 2.3.88 内未逐字节确认，统一走显式口径避免 {@code %%} 双写渲染差异。）
     */
    private static IKey langKey(String key, Object... args) {
        return IKey.dynamic(() -> StatCollector.translateToLocalFormatted(key, args));
    }

    /** 顶部横幅：当前轮回状态（EXECUTED = 只读锁提示） */
    private static IWidget createBanner(Session session) {
        return new TextWidget<>(IKey.dynamic(() -> bannerText(session))).pos(0, BANNER_Y)
            .size(PANEL_WIDTH, 10)
            .textAlign(Alignment.Center)
            .scale(0.8f)
            .shadow(false);
    }

    /** 横幅文本（双端安全：同步值 + StatCollector） */
    private static String bannerText(Session session) {
        int state = session.stateSync.getIntValue();
        if (state == ReincarnationCycle.CycleState.DEPOSITED.ordinal()) {
            return EnumChatFormatting.GOLD + StatCollector.translateToLocal(KEY_BANNER_DEPOSITED);
        }
        if (state == STATE_EXECUTED_ORDINAL) {
            return EnumChatFormatting.RED + StatCollector.translateToLocal(KEY_BANNER_EXECUTED);
        }
        return EnumChatFormatting.GRAY + StatCollector.translateToLocal(KEY_BANNER_IDLE);
    }

    /** 标题（金色） */
    private static IWidget createTitle() {
        return new TextWidget<>(langKey(KEY_TITLE)).pos(0, TITLE_Y)
            .size(PANEL_WIDTH, 10)
            .textAlign(Alignment.Center)
            .shadow(false);
    }

    /**
     * 15 列网格：每列 = 上方外壳累计槽 + n/16 进度 + 下方 3 个物品格 + 列名
     */
    private static IWidget createGrid(Session session, PanelSyncManager syncManager) {
        ParentWidget<?> grid = new ParentWidget<>().pos(0, 0)
            .size(PANEL_WIDTH, LABELS_Y + 10);
        // 物品格槽组（双端注册；行长度 = 列数，允许从背包 shift 转入，逐槽 filter 兜底）
        syncManager.registerSlotGroup(ITEM_SLOT_GROUP, ReincarnationCycle.COLUMN_COUNT, true);

        for (int i = 0; i < ReincarnationCycle.COLUMN_COUNT; i++) {
            final int column = i;
            int x = GRID_X + column * SLOT_PITCH;

            // 外壳累计槽（放入即消耗；列已解锁或只读时禁用）
            grid.child(
                createHullSlot(session, column).pos(x, HULL_Y)
                    .size(SLOT_SIZE, SLOT_SIZE));

            // 进度标记 n/16
            grid.child(
                new TextWidget<>(IKey.dynamic(() -> progressText(session, column))).pos(x - 1, PROGRESS_Y)
                    .size(SLOT_PITCH, 7)
                    .textAlign(Alignment.Center)
                    .scale(0.5f)
                    .shadow(false));

            // 物品格 ×3（竖列，受全局行解锁约束）
            for (int row = 0; row < ReincarnationCycle.MAX_UNLOCKED_ROWS; row++) {
                grid.child(
                    createItemSlot(session, column, row).pos(x, ITEMS_Y + row * SLOT_PITCH)
                        .size(SLOT_SIZE, SLOT_SIZE));
            }

            // 列名（已解锁列金色，未解锁深灰）
            grid.child(
                new TextWidget<>(IKey.dynamic(() -> columnText(session, column))).pos(x - 1, LABELS_Y)
                    .size(SLOT_PITCH, 8)
                    .textAlign(Alignment.Center)
                    .scale(0.5f)
                    .shadow(false));
        }
        return grid;
    }

    /**
     * 外壳累计槽：filter = 对应档位外壳（蒸汽列 = 镀铜砖块），放入即服务端消耗 1 件计 1 进度
     */
    private static ItemSlot createHullSlot(Session session, final int column) {
        ModularSlot slot = new ModularSlot(session.hullHandler, column).singletonSlotGroup()
            .filter(
                stack -> !isReadonly(session) && !session.columnSync[column].getBoolValue()
                    && ReincarnationHullMatcher.matchesHull(stack, column))
            .accessibility(true, true)
            .changeListener((newItem, onlyAmountChanged, client, init) -> {
                if (client || init) return;
                if (newItem != null) {
                    // 放入外壳 → 投递服务端主线程消耗（C2S 在 Netty 线程到达）
                    final ItemStack placed = newItem.copy();
                    enqueueSessionTask(session, () -> consumeHull(session, column, placed));
                }
            });
        ItemSlot widget = new ItemSlot().slot(slot);
        widget.tooltipBuilder(t -> {
            String columnName = StatCollector.translateToLocal(KEY_COLUMN_PREFIX + column);
            if (session.columnSync[column].getBoolValue()) {
                t.addLine(
                    langKey(
                        KEY_HULL_TOOLTIP_DONE,
                        StatCollector.translateToLocalFormatted(KEY_PROGRESS, session.hullSync[column].getIntValue())));
            } else {
                t.addLine(langKey(KEY_HULL_TOOLTIP, columnName, String.valueOf(HULL_TARGET)));
            }
            t.addLine(langKey(KEY_COLUMN_PREFIX + column));
        });
        widget.tooltipAutoUpdate(true);
        widget.setEnabledIf(w -> !isReadonly(session));
        return widget;
    }

    /**
     * 物品格：仅已解锁列的已解锁行可放入；每格最大 1 件；放入/取出经服务端同步到权威模型
     */
    private static ItemSlot createItemSlot(Session session, final int column, final int row) {
        final int index = itemSlotIndex(column, row);
        ModularSlot slot = new ModularSlot(session.itemHandler, index).slotGroup(ITEM_SLOT_GROUP)
            .filter(
                stack -> !isReadonly(session) && session.columnSync[column].getBoolValue()
                    && row < session.rowsSync.getIntValue())
            .accessibility(true, true)
            .changeListener((newItem, onlyAmountChanged, client, init) -> {
                if (client) return;
                if (init) {
                    // 开屏同步基线：记录槽位 ↔ ItemRef 对应（不触发模型变更）
                    session.itemSlotRefs[index] = refOf(newItem);
                    return;
                }
                if (newItem != null) {
                    final ItemStack placed = newItem.copy();
                    enqueueSessionTask(session, () -> placeItem(session, index, placed));
                } else if (!onlyAmountChanged) {
                    enqueueSessionTask(session, () -> takeItem(session, index));
                }
            });
        ItemSlot widget = new ItemSlot().slot(slot);
        widget.tooltipBuilder(t -> {
            t.addLine(langKey(KEY_ITEM_TOOLTIP, row + 1));
            t.addLine(langKey(KEY_IGNORE_NBT));
        });
        widget.tooltipAutoUpdate(true);
        widget.setEnabledIf(
            w -> !isReadonly(session) && session.columnSync[column].getBoolValue()
                && row < session.rowsSync.getIntValue());
        return widget;
    }

    /**
     * 动作行：猫猫币支付格 + 2 个解锁行按钮
     */
    private static IWidget createActionRow(Session session, PanelSyncManager syncManager) {
        ParentWidget<?> row = new ParentWidget<>().pos(0, ACTION_Y)
            .size(PANEL_WIDTH, SLOT_SIZE + 2);

        // 猫猫币支付格（只接受 NekoCoin / ShimmeringNekoCoin；可堆叠；关闭时退回）
        ModularSlot coinSlot = new ModularSlot(session.coinHandler, 0).singletonSlotGroup()
            .filter(ReincarnationCycleGui::isNekoCoin)
            .accessibility(true, true);
        row.child(
            new ItemSlot().slot(coinSlot)
                .pos(GRID_X, 0)
                .size(SLOT_SIZE, SLOT_SIZE)
                .tooltipBuilder(t -> t.addLine(langKey(KEY_COIN_TOOLTIP)))
                .tooltipAutoUpdate(true)
                .setEnabledIf(w -> !isReadonly(session)));

        // 解锁行按钮 ×2（闪烁 0.1% / 普通 0.001%）
        row.child(
            createUnlockButton(
                session,
                syncManager,
                GRID_X + SLOT_SIZE + 2,
                ACTION_UNLOCK_SHIMMER,
                KEY_UNLOCK_SHIMMER,
                KEY_UNLOCK_TOOLTIP_SHIMMER,
                GTITItemList.ShimmeringNekoCoin));
        row.child(
            createUnlockButton(
                session,
                syncManager,
                GRID_X + SLOT_SIZE + 4 + 136,
                ACTION_UNLOCK_NORMAL,
                KEY_UNLOCK_NORMAL,
                KEY_UNLOCK_TOOLTIP_NORMAL,
                GTITItemList.NekoCoin));
        return row;
    }

    /**
     * 解锁行按钮：unplaced 行满 3 或存在未解锁列 → 禁用置灰；全解锁 → 永久禁用（不再需要猫猫币）
     */
    private static ButtonWidget<?> createUnlockButton(Session session, PanelSyncManager syncManager, int x,
        String action, String labelKey, String chanceKey, GTITItemList coinType) {
        return new ButtonWidget<>().pos(x, 0)
            .size(136, SLOT_SIZE)
            .background(NekoGuiTextures.TEXT_FIELD_BACKGROUND)
            .overlay(langKey(labelKey))
            .tooltipBuilder(t -> {
                t.addLine(langKey(KEY_UNLOCK_TOOLTIP_LINE));
                t.addLine(langKey(chanceKey));
                if (session.rowsSync.getIntValue() >= ReincarnationCycle.MAX_UNLOCKED_ROWS) {
                    t.addLine(langKey(KEY_UNLOCK_TOOLTIP_DONE));
                } else if (!allColumnsUnlocked(session)) {
                    t.addLine(langKey(KEY_UNLOCK_TOOLTIP_COLUMNS, String.valueOf(HULL_TARGET)));
                } else if (!hasCoin(session, coinType)) {
                    t.addLine(langKey(KEY_CHAT_UNLOCK_NO_COIN));
                }
            })
            .tooltipAutoUpdate(true)
            .setEnabledIf(w -> unlockAvailable(session))
            .onMouseTapped(mouse -> {
                if (mouse != 0) return false;
                // C2S：服务端扣币 + java.util.Random 判定
                syncManager.callSyncedAction(action);
                return true;
            });
    }

    /** 面板消息行（服务端发 key+args，客户端本地格式化） */
    private static IWidget createMessageLine(Session session) {
        return new TextWidget<>(IKey.dynamic(() -> formatMessagePayload(session.messagePayload))).pos(0, MESSAGE_Y)
            .size(PANEL_WIDTH, 9)
            .textAlign(Alignment.Center)
            .scale(0.7f)
            .shadow(false);
    }

    /** 确认轮回按钮（二次确认弹窗 → C2S → ReincarnationConfirmHook） */
    private static IWidget createConfirmButton(Session session, PanelSyncManager syncManager, ModularPanel panel) {
        // 确认弹窗（仅客户端创建；不在 widget 树内，不影响槽位 auto_sync ID）
        if (session.client) {
            NekoConfirmationDialog dialog = new NekoConfirmationDialog("gtitReincarnationConfirmDialog");
            dialog.setParams(
                StatCollector.translateToLocal(KEY_CONFIRM_DIALOG),
                () -> syncManager.callSyncedAction(ACTION_CONFIRM));
            session.confirmPanel = IPanelHandler.simple(panel, (parent, player) -> dialog, true);
        }
        return new ButtonWidget<>().pos((PANEL_WIDTH - 150) / 2, CONFIRM_Y)
            .size(150, 16)
            .background(NekoGuiTextures.TEXT_FIELD_BACKGROUND)
            .overlay(langKey(KEY_CONFIRM_BUTTON))
            .tooltipBuilder(t -> {
                if (isReadonly(session)) {
                    t.addLine(langKey(KEY_CHAT_READONLY));
                } else if (session.stateSync.getIntValue() != ReincarnationCycle.CycleState.DEPOSITED.ordinal()) {
                    t.addLine(langKey(KEY_CONFIRM_TOOLTIP_STATE));
                } else if (session.pendingSync.getIntValue() <= 0) {
                    t.addLine(langKey(KEY_CONFIRM_TOOLTIP_EMPTY));
                } else if (ReincarnationConfirmHook.Holder.getHook() == null) {
                    t.addLine(langKey(KEY_CONFIRM_TOOLTIP_NOHOOK));
                }
            })
            .tooltipAutoUpdate(true)
            .setEnabledIf(w -> confirmAvailable(session))
            .onMouseTapped(mouse -> {
                if (mouse != 0) return false;
                if (session.confirmPanel != null) {
                    // 一层二次确认（MUI2 先例 NekoConfirmationDialog）；确认回调里发 C2S
                    session.confirmPanel.openPanel();
                }
                return true;
            });
    }

    /** 底部提示行：忽略 NBT（左）· 混淆加密·绑定玩家（右） */
    private static IWidget createFooter() {
        ParentWidget<?> footer = new ParentWidget<>().pos(0, FOOTER_Y)
            .size(PANEL_WIDTH, 7);
        footer.child(
            new TextWidget<>(langKey(KEY_IGNORE_NBT)).pos(4, 0)
                .size(150, 7)
                .textAlign(Alignment.CenterLeft)
                .scale(0.55f)
                .color(0xFF777788)
                .shadow(false));
        footer.child(
            new TextWidget<>(langKey(KEY_ENCRYPTED)).pos(PANEL_WIDTH - 154, 0)
                .size(150, 7)
                .textAlign(Alignment.CenterRight)
                .scale(0.55f)
                .color(0xFF777788)
                .shadow(false));
        return footer;
    }

    // ==================== 客户端动态文本 ====================

    /** 是否只读（EXECUTED = 存在未领取轮回，见类 javadoc 口径说明） */
    private static boolean isReadonly(Session session) {
        return session.stateSync.getIntValue() == STATE_EXECUTED_ORDINAL;
    }

    /** 已解锁列是否全满（行解锁前置条件） */
    private static boolean allColumnsUnlocked(Session session) {
        for (int i = 0; i < ReincarnationCycle.COLUMN_COUNT; i++) {
            if (!session.columnSync[i].getBoolValue()) return false;
        }
        return true;
    }

    /** 解锁行按钮可用：非只读、行未满 3、15 列全部解锁 */
    private static boolean unlockAvailable(Session session) {
        return !isReadonly(session) && session.rowsSync.getIntValue() < ReincarnationCycle.MAX_UNLOCKED_ROWS
            && allColumnsUnlocked(session);
    }

    /** 确认按钮可用：DEPOSITED + 非空 + 钩子已注入（S4） */
    private static boolean confirmAvailable(Session session) {
        return !isReadonly(session)
            && session.stateSync.getIntValue() == ReincarnationCycle.CycleState.DEPOSITED.ordinal()
            && session.pendingSync.getIntValue() > 0
            && ReincarnationConfirmHook.Holder.getHook() != null;
    }

    /** 币格中是否有指定猫猫币 */
    private static boolean hasCoin(Session session, GTITItemList coinType) {
        ItemStack coin = session.coinHandler.getStackInSlot(0);
        return coin != null && isNekoCoin(coin, coinType);
    }

    /** 猫猫币判定（支付格 filter：两种猫猫币均接受） */
    private static boolean isNekoCoin(ItemStack stack) {
        return isNekoCoin(stack, GTITItemList.NekoCoin) || isNekoCoin(stack, GTITItemList.ShimmeringNekoCoin);
    }

    /** 指定猫猫币判定（null 安全；经 IItemContainer.isStackEqual 口径） */
    private static boolean isNekoCoin(ItemStack stack, GTITItemList coinType) {
        return stack != null && stack.getItem() != null && coinType.isStackEqual(stack);
    }

    /** 进度标记文本（n/16，进度金色） */
    private static String progressText(Session session, int column) {
        return StatCollector.translateToLocalFormatted(KEY_PROGRESS, session.hullSync[column].getIntValue());
    }

    /** 列名文本（已解锁金色 / 未解锁深灰） */
    private static String columnText(Session session, int column) {
        EnumChatFormatting color = session.columnSync[column].getBoolValue() ? EnumChatFormatting.GOLD
            : EnumChatFormatting.DARK_GRAY;
        return color + StatCollector.translateToLocal(KEY_COLUMN_PREFIX + column);
    }

    /**
     * 消息载荷 → 本地化文本（key￁arg1￁arg2；key 为空返回 ""）。
     * 客户端格式化保证双语（服务端只传输键与参数）。
     */
    private static String formatMessagePayload(String payload) {
        if (payload == null || payload.isEmpty()) return "";
        String[] parts = payload.split(String.valueOf(MESSAGE_ARG_SEPARATOR));
        if (parts.length == 0 || parts[0].isEmpty()) return "";
        String[] args = new String[parts.length - 1];
        System.arraycopy(parts, 1, args, 0, args.length);
        return StatCollector.translateToLocalFormatted(parts[0], (Object[]) args);
    }

    /** 发面板消息（服务端）：设置载荷，下一 detectAndSendChanges 推送客户端 */
    private static void setMessage(Session session, String key, Object... args) {
        StringBuilder payload = new StringBuilder(key);
        for (Object arg : args) {
            payload.append(MESSAGE_ARG_SEPARATOR)
                .append(arg);
        }
        session.messagePayload = payload.toString();
    }

    // ==================== 服务端逻辑（主线程经 enqueueSessionTask 投递） ====================

    /** 物品格 index：col * 行数 + row */
    private static int itemSlotIndex(int column, int row) {
        return column * ReincarnationCycle.MAX_UNLOCKED_ROWS + row;
    }

    /** ItemRef → 展示/退回用物品堆（1 件；registry 名缺失返回 null） */
    private static ItemStack freshStack(ReincarnationCycle.ItemRef ref) {
        Item item = (Item) Item.itemRegistry.getObject(ref.getId());
        return item == null ? null : new ItemStack(item, 1, ref.getMeta());
    }

    /** ItemStack → ItemRef（registry 名 + meta，忽略 NBT；名缺失返回 null） */
    private static ReincarnationCycle.ItemRef refOf(ItemStack stack) {
        if (stack == null || stack.getItem() == null) return null;
        Object name = Item.itemRegistry.getNameForObject(stack.getItem());
        return name == null ? null : new ReincarnationCycle.ItemRef(name.toString(), stack.getItemDamage());
    }

    /** GUI 打开时：按「列优先、行内自上而下」把 pendingItems 回填到已解锁格（超出的留在模型不展示） */
    private static void fillItemSlotsFromCycle(Session session) {
        int cursor = 0;
        List<ReincarnationCycle.ItemRef> pending = session.cycle.getPendingItems();
        for (int column = 0; column < ReincarnationCycle.COLUMN_COUNT; column++) {
            if (!session.cycle.isColumnUnlocked(column)) continue;
            for (int row = 0; row < session.cycle.getUnlockedRows(); row++) {
                if (cursor >= pending.size()) return;
                ReincarnationCycle.ItemRef ref = pending.get(cursor++);
                ItemStack display = freshStack(ref);
                if (display == null) continue; // registry 名失效（环境变化）：记录保留，格位留空
                int index = itemSlotIndex(column, row);
                session.itemHandler.setStackInSlot(index, display);
                session.itemSlotRefs[index] = ref;
            }
        }
    }

    /**
     * 外壳消耗（服务端主线程）：消耗 1 件 → 进度 +1 → 满 {@value #HULL_TARGET} 解锁列 → 保存
     */
    private static void consumeHull(Session session, int column, ItemStack placed) {
        // 双重校验（filter 已挡，防竞态/专用服异常路径）
        if (isReadonly(session) || session.columnSync[column].getBoolValue()
            || !ReincarnationHullMatcher.matchesHull(placed, column)) {
            giveBack(session, placed); // 原样退回
            return;
        }
        session.hullHandler.setStackInSlot(column, null); // 槽位清空只留计数
        session.cycle.recordHullConsumption(column, 1);
        int progress = session.cycle.getHullProgress()[column];
        if (progress >= HULL_TARGET && !session.cycle.isColumnUnlocked(column)) {
            session.cycle.unlockColumn(column);
            String columnName = StatCollector.translateToLocal(KEY_COLUMN_PREFIX + column);
            setMessage(session, KEY_CHAT_COLUMN_UNLOCKED, columnName, progress + "/" + HULL_TARGET);
            chat(session.player, KEY_CHAT_COLUMN_UNLOCKED, columnName, progress + "/" + HULL_TARGET);
        } else {
            String columnName = StatCollector.translateToLocal(KEY_COLUMN_PREFIX + column);
            setMessage(session, KEY_CHAT_HULL_CONSUMED, columnName, progress + "/" + HULL_TARGET);
        }
        saveQuietly(session);
    }

    /**
     * 物品放入（服务端主线程）：记录 ItemRef（id+meta，忽略 NBT）。
     * <p>
     * 首件放入且状态 IDLE 时经 {@code deposit} 过渡 IDLE→DEPOSITED（确认按钮契约前置）；
     * 其余情况 {@code addPendingItem}（S4 契约：IDLE/DEPOSITED 均允许）。模型拒绝时物品原样退回。
     */
    private static void placeItem(Session session, int index, ItemStack placed) {
        if (isReadonly(session)) {
            session.itemHandler.setStackInSlot(index, null);
            giveBack(session, placed);
            chat(session.player, KEY_CHAT_READONLY);
            return;
        }
        ReincarnationCycle.ItemRef ref = refOf(placed);
        if (ref == null) {
            session.itemHandler.setStackInSlot(index, null);
            giveBack(session, placed);
            chat(session.player, KEY_CHAT_DEPOSIT_FAILED, placed.getDisplayName());
            return;
        }
        try {
            if (session.cycle.getPendingItems()
                .isEmpty() && session.cycle.canDeposit()) {
                // IDLE → DEPOSITED（首件寄存；deposit=整体替换，单件清单语义等价 addPendingItem+过渡）
                session.cycle.deposit(Collections.singletonList(ref));
            } else {
                session.cycle.addPendingItem(ref);
            }
            session.itemSlotRefs[index] = ref;
            saveQuietly(session);
        } catch (IllegalStateException | IllegalArgumentException e) {
            // 模型拒绝（EXECUTED/契约未放宽等）：槽位清空 + 物品退回
            session.itemHandler.setStackInSlot(index, null);
            session.itemSlotRefs[index] = null;
            giveBack(session, placed);
            chat(session.player, KEY_CHAT_DEPOSIT_FAILED, placed.getDisplayName());
        }
    }

    /**
     * 物品取出（服务端主线程）：withdraw（S4 契约）移除一条记录；失败时物品按 ItemRef 重建退回格位
     */
    private static void takeItem(Session session, int index) {
        ReincarnationCycle.ItemRef ref = session.itemSlotRefs[index];
        session.itemSlotRefs[index] = null;
        if (ref == null) return;
        if (isReadonly(session)) {
            // 只读态拒绝取出：重建格位展示（记录保留）
            restoreItemSlot(session, index, ref);
            chat(session.player, KEY_CHAT_READONLY);
            return;
        }
        if (withdrawBridge(session.cycle, ref)) {
            saveQuietly(session);
        } else {
            // 记录移除失败（S4 未落地且剩余为空等）：格位重建，物品不丢
            restoreItemSlot(session, index, ref);
            chat(session.player, KEY_CHAT_WITHDRAW_FAILED);
        }
    }

    /** 重建格位展示（ItemRef → 展示堆；registry 失效时留空） */
    private static void restoreItemSlot(Session session, int index, ReincarnationCycle.ItemRef ref) {
        ItemStack display = freshStack(ref);
        if (display != null) {
            session.itemHandler.setStackInSlot(index, display);
            session.itemSlotRefs[index] = ref;
        }
    }

    /**
     * S4 契约桥：{@code ReincarnationCycle#withdraw(ItemRef)}（S4 交付，IDLE/DEPOSITED 移除一件）。
     * <p>
     * 本切片与 S4 并行开发，此处经反射探测直连（权威语义）；方法未落地时退化为
     * {@code deposit(remaining)}（S2 既有：整体替换 pendingItems，等价移除一件）。
     * 剩余为空时 S2 deposit 拒绝空清单 → 返回 false（调用方退回物品）。
     * <b>S4 合流后建议替换为直连调用</b>（待独立 reviewer 核对）。
     */
    private static boolean withdrawBridge(ReincarnationCycle cycle, ReincarnationCycle.ItemRef ref) {
        try {
            Method withdraw = ReincarnationCycle.class.getMethod("withdraw", ReincarnationCycle.ItemRef.class);
            Object result = withdraw.invoke(cycle, ref);
            return result instanceof Boolean && (Boolean) result;
        } catch (NoSuchMethodException notDeliveredYet) {
            // S4 未落地：deposit(remaining) 等价路径
            List<ReincarnationCycle.ItemRef> remaining = new ArrayList<>(cycle.getPendingItems());
            remaining.remove(ref);
            if (remaining.isEmpty()) {
                return false;
            }
            cycle.deposit(remaining);
            return true;
        } catch (ReflectiveOperationException e) {
            return false;
        }
    }

    /**
     * 确认轮回（服务端主线程，C2S）：二次确认已过 → 回调 S4 钩子（未注入则提示）
     */
    private static void doConfirm(Session session) {
        if (isReadonly(session)) {
            chat(session.player, KEY_CHAT_READONLY);
            return;
        }
        if (session.cycle.getCycleState() != ReincarnationCycle.CycleState.DEPOSITED || session.cycle.getPendingItems()
            .isEmpty()) {
            return;
        }
        ReincarnationConfirmHook hook = ReincarnationConfirmHook.Holder.getHook();
        if (hook == null) {
            chat(session.player, KEY_CONFIRM_TOOLTIP_NOHOOK);
            return;
        }
        // S4 契约：由钩子完成 deposit 收束 → confirmReincarnation(seed) → 演出/奖励
        hook.onConfirmRequested(session.player);
    }

    /**
     * 解锁行尝试（服务端主线程，C2S）：币格扣 1 币 → {@link Random} 判定 → 成功行数 +1 → 保存
     * <p>
     * RNG 冻结口径：{@code UNLOCK_RNG.nextInt(分母) == 0}（闪烁 1/1000=0.1%，普通 1/100000=0.001%）。
     */
    private static void tryUnlock(Session session, boolean shimmer, GTITItemList coinType) {
        if (!unlockAvailable(session)) {
            chat(session.player, KEY_CHAT_READONLY);
            return;
        }
        ItemStack coin = session.coinHandler.getStackInSlot(0);
        if (!isNekoCoin(coin, coinType)) {
            chat(session.player, KEY_CHAT_UNLOCK_NO_COIN);
            return;
        }
        // 扣 1 币（可堆叠：数量 -1，空则清格）
        if (coin.stackSize <= 1) {
            session.coinHandler.setStackInSlot(0, null);
        } else {
            ItemStack rest = coin.copy();
            rest.stackSize--;
            session.coinHandler.setStackInSlot(0, rest);
        }
        boolean success = UNLOCK_RNG.nextInt(shimmer ? SHIMMER_UNLOCK_ONE_IN : NORMAL_UNLOCK_ONE_IN) == 0;
        if (success) {
            session.cycle.setUnlockedRows(session.cycle.getUnlockedRows() + 1);
            saveQuietly(session);
            chat(
                session.player,
                KEY_CHAT_UNLOCK_SUCCESS,
                session.cycle.getUnlockedRows() + "/" + ReincarnationCycle.MAX_UNLOCKED_ROWS);
            setMessage(
                session,
                KEY_CHAT_UNLOCK_SUCCESS,
                session.cycle.getUnlockedRows() + "/" + ReincarnationCycle.MAX_UNLOCKED_ROWS);
        } else {
            String chance = shimmer ? "0.1%" : "0.001%";
            chat(session.player, KEY_CHAT_UNLOCK_FAIL, chance);
            setMessage(session, KEY_CHAT_UNLOCK_FAIL, chance);
        }
    }

    /** GUI 关闭（服务端）：币格余币退回玩家 + 收束保存（槽位内物品已记录进模型，不退回） */
    private static void onCloseSession(Session session, EntityPlayer player) {
        ItemStack coin = session.coinHandler.getStackInSlot(0);
        if (coin != null) {
            session.coinHandler.setStackInSlot(0, null);
            giveBack(session, coin);
        }
        saveQuietly(session);
    }

    // ==================== 基础设施 ====================

    /** 保存（服务端）；磁盘异常不阻断 GUI（记录日志语义由 store 异常消息承载） */
    private static void saveQuietly(Session session) {
        if (session.store == null) return;
        try {
            session.store.save(session.cycle);
        } catch (IllegalStateException ignored) {
            // 写入失败（磁盘等）：保持内存态，下次变更重试；不向玩家刷屏
        }
    }

    /** 物品退回玩家（背包满则原地掉落） */
    private static void giveBack(Session session, ItemStack stack) {
        if (stack == null || !session.player.inventory.addItemStackToInventory(stack)) {
            if (stack != null) {
                session.player.dropPlayerItemWithRandomChoice(stack, false);
            }
        }
    }

    /** 服务端聊天反馈（ChatComponentTranslation：客户端本地化，双语由 lang 文件承载） */
    private static void chat(EntityPlayer player, String key, Object... args) {
        player.addChatMessage(new ChatComponentTranslation(key, args));
    }

    // ==================== C2S 动作注册 ====================

    /** C2S 动作注册入口（服务端）：动作本体 + 槽位变更均经主线程队列执行 */
    void registerServerActions(Session session, PanelSyncManager syncManager) {
        syncManager.onServerTick(() -> drainSessionTasks(session));
        syncManager.registerSyncedAction(
            ACTION_CONFIRM,
            false,
            true,
            buffer -> enqueueSessionTask(session, () -> doConfirm(session)));
        syncManager.registerSyncedAction(
            ACTION_UNLOCK_SHIMMER,
            false,
            true,
            buffer -> enqueueSessionTask(session, () -> tryUnlock(session, true, GTITItemList.ShimmeringNekoCoin)));
        syncManager.registerSyncedAction(
            ACTION_UNLOCK_NORMAL,
            false,
            true,
            buffer -> enqueueSessionTask(session, () -> tryUnlock(session, false, GTITItemList.NekoCoin)));
    }

    /** 投递服务端主线程任务（Netty 线程调用安全；无锁并发队列） */
    private static void enqueueSessionTask(Session session, Runnable action) {
        if (action != null) {
            session.serverTasks.offer(action);
        }
    }

    /** 排空主线程任务队列（每 tick 由 MUI2 tickListener 调用；单任务异常不中断同批其余任务） */
    private static void drainSessionTasks(Session session) {
        Runnable action;
        while ((action = session.serverTasks.poll()) != null) {
            try {
                action.run();
            } catch (RuntimeException e) {
                // 与 MailHandler 消费循环同口径：记日志不抛出（防 GUI 主线程任务拖垮 tick）
                System.out.println("[gtit:reincarnation] 服务端会话任务执行失败: " + e);
            }
        }
    }
}

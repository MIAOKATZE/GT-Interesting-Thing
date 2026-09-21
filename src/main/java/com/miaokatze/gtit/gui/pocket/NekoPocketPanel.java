package com.miaokatze.gtit.gui.pocket;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.StatCollector;
import net.minecraftforge.fluids.FluidStack;

import com.cleanroommc.modularui.api.widget.Interactable;
import com.cleanroommc.modularui.factory.PlayerInventoryGuiData;
import com.cleanroommc.modularui.screen.ModularContainer;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.DoubleSyncValue;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.StringSyncValue;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widgets.slot.PlayerSlotGroup;
import com.cleanroommc.modularui.widgets.slot.SlotGroup;
import com.miaokatze.gtit.common.items.pocket.ItemNekoDimensionPocket;
import com.miaokatze.gtit.common.items.pocket.PocketAeChannelOps;
import com.miaokatze.gtit.common.items.pocket.PocketCellBindings;
import com.miaokatze.gtit.common.items.pocket.PocketCellProbe;
import com.miaokatze.gtit.common.items.pocket.PocketChannelManager;
import com.miaokatze.gtit.common.items.pocket.PocketChannelOps;
import com.miaokatze.gtit.common.items.pocket.PocketChannelRunner;
import com.miaokatze.gtit.common.items.pocket.PocketChannelState;
import com.miaokatze.gtit.common.items.pocket.PocketConstants;
import com.miaokatze.gtit.common.items.pocket.PocketEssenceStore;
import com.miaokatze.gtit.common.items.pocket.PocketFilterConfig;
import com.miaokatze.gtit.common.items.pocket.PocketSession;
import com.miaokatze.gtit.common.items.pocket.PocketSessions;
import com.miaokatze.gtit.common.items.pocket.distill.PocketDistillDriver;
import com.miaokatze.gtit.crossmod.taum.TaumCompat;
import com.miaokatze.gtit.currency.NekoCurrencyRegistrar;
import com.miaokatze.gtit.main.GTInterestingThing;
import com.miaokatze.gtit.trade.NekoWallet;
import com.miaokatze.gtit.trade.NekoWalletManager;
import com.miaokatze.gtit.util.ServerTaskScheduler;

/**
 * 猫猫次元口袋主面板（<b>416×360</b>，<b>不显示玩家背包</b> = L2 档）。
 * <p>
 * <b>R32（本任务最高风险）的结构性处置</b>：{@link #assemble()} 是<b>一条线性装配</b>——双端同调用点、
 * 同一批列类、同一顺序 add，没有任何远程分支改变子节点的顺序或数量（同步键全部显式命名）。
 * 槽位布局由各列类的 {@code SlotGroupWidget.matrix(String...)} 字面量描述（R41a）⇒
 * 「双端 widget 树失序」不是靠纪律避免，而是结构上不可能发生。
 * <p>
 * <b>R75 宽度闭合（逐字照加总表，无富余）</b>：
 * {@code 6 + 108(流体 6 列) + 4 + 180(中栏 10 列) + 4 + 108(源质 6 列) + 6 = 416} ⇒
 * 三列 root 固定 {@code x/宽 = 6/108 · 118/180 · 302/108}，不得再给任何列加宽。
 * <b>高度闭合</b>：{@code 6 + 270(15 行) + 6 + 72(底部带) + 6 = 360}，而 360 正是
 * <b>1080p / GUI Scale 3 的逻辑高度上限</b>（R75"为什么是 15 行"的全部理由）⇒
 * <b>任何加高方案都必须先重算这条账</b>，纵向已经没有一格余量。
 * <p>
 * <b>Container 口径 = 恰好 {@value PocketSlots#TOTAL_REAL_SLOTS}</b>（R75，覆盖 §14.3/R43b 的 149
 * 与 R74 一度算出的 185）：中栏 150 + 流体交互 12（6 列 × 输入/输出）+ 蒸馏 12 + 绑定 1；
 * 48 源质格、6 个流体槽本体与全部 ghost 走显示侧，<b>不进 Container</b>（R35）。
 * 为此预注册一个<b>空的</b> {@code PlayerSlotGroup}，让框架的
 * {@code ModularSyncManager#construct} 跳过它默认的 36 格玩家绑定（{@code ISyncRegistrar#bindPlayerInventory}
 * 的"已注册即跳过"分支是该入口的既有语义，不是绕行）。两条理由：
 * <ol>
 * <li>175 必须可机检——多 36 格就断言不出"有区域被重复接入"；</li>
 * <li>L2 档不显示背包，但那 36 格仍会被 vanilla 每 tick 做 {@code ItemStack} 相等比较
 * （<b>含整份 NBT 深比较</b>，见 {@code Container#detectAndSendChanges} 与
 * {@code ItemStack#isItemStackEqual}），内容一变就整枚口袋连 150 格一起重发——正是 R53c 点名的包放大面。</li>
 * </ol>
 * 跳过后 {@code open}/{@code work} 位不再随栈同步到客户端 ⇒ 由 {@code network/PocketStateNetwork}
 * 定向推送同一份状态。
 * <p>
 * <b>关屏写状态</b>：{@link NekoPocketContainer#onModularContainerClosed()}（R35，NEI 顶屏不误触发）。
 * <p>
 * <b>绑定信息的可见面（R74②/R75）</b>：第四列退役后，全部绑定条目由<b>绑定按钮的 tooltip</b>
 * 承载（{@link #bindTooltipText()}，超出 {@code NekoPocketBottomBand.TOOLTIP_ROWS} 条时
 * <b>显式</b>提示还有几条没列），解绑入口改为该按钮的<b>右键 = 解绑最后一条</b> /
 * <b>Shift 右键 = 清空全部</b>（{@link #dispatchBindButtonClick(int)}）。
 * <p>
 * <b>本类同时是 S6/S7 的活会话</b>（{@link PocketSession}）：通道一拍与蒸馏节拍的宿主是
 * {@code Item.onUpdate}，它读的就是这里持有的那一份内存对象（R53c 的"一次读一次写"因此不被打破）。
 * 服务端在 {@link #assemble()} 里把自己登记进 {@link PocketSessions}，关屏只把 {@link #closed}
 * 置真而<b>不摘登记</b>（R57c①：30 秒通道要活过关屏）；摘除点是
 * {@code PocketChannelDriver}（无活可干时回收）与 {@code PocketLifecycleHandler}（离线/停服）。
 * 客户端那份实例<b>从不登记</b>（登记动作整个包在 {@code !syncManager.isClient()} 里）。
 */
public final class NekoPocketPanel implements PocketSession {

    /** 面板名（同步键前缀也用它，双端同串）。 */
    public static final String PANEL_NAME = "gtit.pocket.main";

    // ------------------------------------------------------------------ R75 几何单源（各列都从这里取，别处不得再写 6/18/4）
    /** 面板外边距。 */
    public static final int MARGIN = 6;
    /** 一个格子的边长（MC GUI 栅格）。 */
    public static final int GRID = 18;
    /** 列间距（R75 加总表里唯一的"机动量"，加宽任何一列都会把它挤没）。 */
    public static final int COLUMN_GAP = 4;
    /** 底部带高（R75 钉死 72）。 */
    public static final int BAND_HEIGHT = NekoPocketBottomBand.HEIGHT;
    /** R75：面板宽 = 6+108+4+180+4+108+6 = 416（由三列常量派生，不是手抄）。 */
    public static final int WIDTH = MARGIN + NekoPocketLeftColumn.WIDTH
        + COLUMN_GAP
        + NekoPocketStorageColumn.WIDTH
        + COLUMN_GAP
        + NekoPocketEssenceColumn.WIDTH
        + MARGIN;
    /** R75：面板高 = 6+270+6+72+6 = 360（★360 = 1080p / GUI Scale 3 的逻辑高度上限）。 */
    public static final int HEIGHT = MARGIN + NekoPocketStorageColumn.HEIGHT + MARGIN + BAND_HEIGHT + MARGIN;

    // ------------------------------------------------------------------ 同步键
    // S2C：格数与行数恒定 ⇒ 键数恒定，不随 addon / 绑定数漂移
    private static final String SYNC_ESSENCE = "pocket.essence.points";
    private static final String SYNC_BIND_ROWS = "pocket.bind.rows";
    private static final String SYNC_MODE = "pocket.mode";
    private static final String SYNC_REMAIN = "pocket.remain";
    private static final String SYNC_PROGRESS = "pocket.distill.progress";
    /** S2C：ghost 声明视图（{@code kind:slotIndex:载荷键}，';' 分隔）⇒ 客户端据此<b>原位</b>虚化格子。 */
    private static final String SYNC_GHOST = "pocket.ghost.slots";
    /** S2C：上一次通道/绑定动作的回执（{@code langKey|数量}），客户端只做本地化格式化。 */
    private static final String SYNC_RECEIPT = "pocket.receipt";
    /** C2S：ghost 就地转换请求（{@code SET|slot|载荷键} / {@code CLR|slot|区域字母}，文法见 {@code PocketGhostRequest}）。 */
    private static final String SYNC_GHOST_REQUEST = "pocket.ghost.request";
    /** C2S：所有按钮/选中动作走这一个键，值 = {@code code * ACTION_ARG_BASE + arg}（单包原子，无两值竞态）。 */
    private static final String SYNC_ACTION = "pocket.action";
    /** 动作参数基数（当前最大 arg = 149 格 / 95 格+Shift 位）。 */
    private static final int ACTION_ARG_BASE = 1024;

    /** C2S 动作码（<b>互不相同</b>：R64c 判据「各自绑定的动作码不同」）。 */
    private static final int ACTION_TAKE_OUT = 1;
    private static final int ACTION_SORT = 2;
    private static final int ACTION_CHANNEL_BURST = 3;
    private static final int ACTION_CHANNEL_SHORT = 4;
    private static final int ACTION_BIND = 5;
    /**
     * 解绑<b>最后一条</b>（R74：第四列退役后"选中行"这个概念不存在了）。
     * <p>
     * ★码值 {@code 6} 沿用旧 {@code ACTION_UNBIND}，但<b>语义与 arg 都变了</b>：
     * 旧实现是 {@code arg = 选中行 + 1}，新实现<b>不带 arg</b>（恒 0）。
     * 把旧的 arg 解读法（{@code performUnbind(arg - 1)}）留下来，"右键按钮"就会等于
     * "解绑第 -1 行 ⇒ 永远只发一条回执"，需求 5 的另一半仍静默失效。
     */
    private static final int ACTION_UNBIND_LAST = 6;
    private static final int ACTION_ESSENCE_OUT = 7;
    /** 解绑<b>全部</b>（绑定按钮 Shift + 右键，R74）。 */
    private static final int ACTION_UNBIND_ALL = 9;

    private final PlayerInventoryGuiData data;
    private final PanelSyncManager syncManager;
    private final UISettings settings;
    private final PocketSlots slots;
    private final PocketInventory inventory;
    /** 打开瞬间的口袋栈引用（NBT 写回目标；关屏重定位见 {@link #relocateCarrier()}）。 */
    private ItemStack pocket;
    private final int carrierSlotIndex;
    /**
     * 中栏 150 个槽位 widget 的双端登记表（下标 = 槽号）。
     * <p>
     * ghost 是"原位改属性"（R41b/R46d），所以必须能按槽号找到<b>那一个</b> widget 实例；
     * 装配时由 {@link NekoPocketStorageColumn} 逐格登记，长度与格序恒定 ⇒ 不引入任何数据驱动的树变化。
     */
    private final NekoFilterSlot[] itemSlots = new NekoFilterSlot[PocketInventory.STORAGE_SLOTS];

    /**
     * 6 个流体槽与 48 源质格的登记表（与 {@link #itemSlots} 同一机制，S-E 补的两个拖入入口）。
     * <p>
     * 长度恒定（{@code GHOST_FLUID_SLOT_LIMIT} 与 {@code GHOST_ESSENCE_SLOT_LIMIT}）、装配序固定
     * ⇒ ghost 声明再多也不会改变 widget 树（R41b/R32）；{@link #applyGhosts()} 按
     * {@code (kind, slot)} 取到那一个实例原位切属性。R75① 之前这里只有一个 {@code fluidBar}
     * 字段（"流体侧只有一格"的特例），现在数组下标 = 列号 = tank 号 = 该列的 ghost 槽号。
     */
    private final NekoPocketFluidSlot[] fluidSlots = new NekoPocketFluidSlot[PocketConstants.GHOST_FLUID_SLOT_LIMIT];
    private final NekoEssenceGhostCell[] essenceCells = new NekoEssenceGhostCell[PocketConstants.GHOST_ESSENCE_SLOT_LIMIT];

    // ---- 客户端显示缓存（S2C 写入；服务端不读）----
    private final int[] essenceCache = new int[TaumCompat.DISPLAY_CELLS];
    private final String[] essenceTags = TaumCompat.aspectOrder();
    private String bindRowsBlob = "";
    private boolean pullMode;
    private int filterCount;
    private int instantRemainSeconds;
    private int timedRemainSeconds;
    private double clientDistillProgress;
    /** 服务端下发的 ghost 声明 blob（客户端据此原位虚化；<b>不</b>自行推断，R39b/R19）。 */
    private String ghostBlob = "";
    /** 上一次动作的回执键与两个计数（{@code key == null} = 还没有回执）。 */
    private String receiptKey;
    private int receiptMoved;
    private int receiptRefused;
    /** 关屏幂等闩。 */
    private boolean closed;

    private NekoPocketPanel(PlayerInventoryGuiData data, PanelSyncManager syncManager, UISettings settings) {
        this.data = data;
        this.syncManager = syncManager;
        this.settings = settings;
        this.slots = new PocketSlots();
        this.pocket = data.getUsedItemStack();
        this.carrierSlotIndex = data.getSlotIndex();
        this.inventory = PocketInventory.readFrom(this.pocket == null ? null : this.pocket.getTagCompound());
    }

    /**
     * 唯一入口（{@code ItemNekoDimensionPocket.buildUI} 只调这一行）。
     * 双端各调一次：服务端在 {@code GuiManager.open}，客户端在 {@code OpenGuiPacket} 处理里。
     */
    public static ModularPanel build(PlayerInventoryGuiData data, PanelSyncManager syncManager, UISettings settings) {
        return new NekoPocketPanel(data, syncManager, settings).assemble();
    }

    // ------------------------------------------------------------------ 装配（线性、双端同树）

    private ModularPanel assemble() {
        final ModularPanel panel = ModularPanel.defaultPanel(PANEL_NAME, WIDTH, HEIGHT);

        // 1) 槽组注册：矩阵侧与 Container 侧同名，否则 MUI2 直接 IllegalArgumentException（R41d）。
        // allowShiftTransfer 一律 false —— L2 档没有玩家背包 ⇒ 没有 shift 落点（§6 第 11 条）
        syncManager
            .registerSlotGroup(new SlotGroup(PocketSlots.GROUP_STORAGE, NekoPocketStorageColumn.COLUMNS, 100, false));
        syncManager.registerSlotGroup(new SlotGroup(PocketSlots.GROUP_FLUID, PocketSlots.FLUID_COLUMNS, 100, false));
        syncManager.registerSlotGroup(
            new SlotGroup(PocketSlots.GROUP_DISTILL, NekoPocketEssenceColumn.DISTILL_COLUMNS, 100, false));
        syncManager.registerSlotGroup(new SlotGroup(PocketSlots.GROUP_BIND, 1, 100, false));
        syncManager.registerSlotGroup(new PlayerSlotGroup(PlayerSlotGroup.NAME));

        // 2) 同步值（显式键，双端同一顺序注册）
        registerSyncValues();

        // 2b) 装饰层（C2）：★必须是第一个 child（MUI2 按 child 顺序绘制 ⇒ 底材在最下）；
        // 位置在槽组/同步值之后、三列之前，且双端同一顺序（R32）
        panel.child(NekoPocketDecoration.build());

        // 3) 三列 + 底部带（R74② 删掉第四列；顺序固定 = 双端同树）
        panel.child(NekoPocketLeftColumn.build(this));
        panel.child(NekoPocketStorageColumn.build(this));
        panel.child(NekoPocketEssenceColumn.build(this));
        // 底部带两块（币值/通道按钮 与 绑定块）按"左块、右块"的固定顺序加入
        for (ParentWidget<?> band : NekoPocketBottomBand.build(this)) {
            panel.child(band);
        }

        // 4) 槽数口径断言（R75：==175；多 = 重复接入，少 = 漏接，双端同抛）
        slots.assertTotalRealSlots();

        // 4b) ghost 虚化：客户端先按自己从 NBT 读到的那份声明表原位刷一遍（服务端那份是权威，
        // 之后每次变更都由 SYNC_GHOST 覆盖）。放在装配末尾 ⇒ 此时 itemSlots 已全部登记。
        if (syncManager.isClient()) {
            applyGhostView(ghostBlobOf(inventory.filters()));
        }

        // 5) 关屏写状态的宿主 + 承载格自动关（R35 防御②③）
        settings.customContainer(() -> new NekoPocketContainer(this));
        settings.canInteractWith(this::carrierStillPresent);

        // 6) open 位：只在服务端写（E1 §2 实测 addOpenListener 只跑客户端 ⇒ 不用 open listener）
        if (!syncManager.isClient()) {
            ItemNekoDimensionPocket.setOpenFlag(pocket, true);
            // 7) 活会话登记（S6/S7 的宿主读的就是这一份内存对象；关屏不摘，见类 javadoc）
            PocketSessions.register(this);
        }
        return panel;
    }

    private void registerSyncValues() {
        syncManager.syncValue(SYNC_ESSENCE, new StringSyncValue(this::composeEssenceBlob, this::applyEssenceBlob));
        syncManager.syncValue(SYNC_BIND_ROWS, new StringSyncValue(this::composeBindRows, this::applyBindRows));
        syncManager.syncValue(SYNC_MODE, new StringSyncValue(this::composeModeState, this::applyModeState));
        syncManager.syncValue(SYNC_REMAIN, new StringSyncValue(this::composeRemain, this::applyRemainState));
        syncManager.syncValue(SYNC_GHOST, new StringSyncValue(this::composeGhostBlob, this::applyGhostBlob));
        syncManager.syncValue(SYNC_RECEIPT, new StringSyncValue(this::composeReceipt, this::applyReceipt));
        syncManager.syncValue(SYNC_PROGRESS, new DoubleSyncValue(this::serverDistillProgress, value -> {
            if (syncManager.isClient()) {
                clientDistillProgress = value;
            }
        }));
        syncManager.syncValue(SYNC_ACTION, new IntSyncValue(() -> 0, this::receiveServerAction).allowC2S());
        // ghost 拖入/解绑的载荷是一串键（itemId+meta+base64NBT 可以很长），装不进上面那个 int 通道，
        // 因此单开一根字符串 C2S；**执行体在服务端**（R18/R19），客户端那份 setter 从不被调用。
        syncManager.syncValue(SYNC_GHOST_REQUEST, new StringSyncValue(() -> "", this::receiveGhostRequest).allowC2S());
    }

    /**
     * ★两根 C2S 通道的<b>入口</b>：只做"换线程"这一件事，判定与落档仍在
     * {@link #onServerAction(int)} / {@link #onServerGhostRequest(String)}。
     * <p>
     * <b>为什么必须投递（R71，逐字节码，不是推测）</b>：MUI2 的 GUI 包走 Forge
     * {@code SimpleNetworkWrapper}（{@code NetworkHandler.C2SHandler} → {@code IPacket.executeServer}
     * → {@code ModularNetwork$Server.receivePacket}，四段链路里 {@code queue|executor|schedule|defer}
     * 成员数为 0），而 Forge 的 {@code SimpleChannelHandlerWrapper} 是 netty
     * {@code SimpleChannelInboundHandler}，其 {@code channelRead0} <b>直调</b>
     * {@code IMessageHandler.onMessage} ⇒ setter 运行在 <b>Netty IO 线程</b>。
     * 原版那条"非 priority 包入队、主线程 {@code NetworkManager#processReceivedPackets} 消费"
     * （{@code NetworkManager.java:120-131} + {@code NetworkSystem.java:182}）对 FML 包<b>不成立</b>：
     * {@code NetworkDispatcher.handleServerSideCustomPacket} 的 CONNECTED 分支把包裹成
     * {@code FMLProxyPacket} 后 {@code ctx.fireChannelRead} 再 {@code return true}，
     * 包根本到不了 {@code NetworkManager}；Forge universal jar 内
     * {@code MainThread|ServerThread|ScheduledTask|ThreadListener} 全量筛选 <b>0 命中</b>。
     * <p>
     * ⇒ 凡改背包/NBT 或发 S2C 广播的服务端执行体一律投递到 {@link ServerTaskScheduler}
     * （{@code ConcurrentLinkedQueue}，ServerTickEvent END 消费，延迟 ≤1 tick），
     * 与本仓邮件/抽奖/签到/交易编辑/终端五条既有链路同形。投递只吃<b>不可变载荷</b>
     * （int / String），不捕获可变的同步载体。
     */
    private void receiveServerAction(int packed) {
        ServerTaskScheduler.scheduleServerTask(() -> onServerAction(packed));
    }

    /** 见 {@link #receiveServerAction(int)} 的同一条线程前提。 */
    private void receiveGhostRequest(String request) {
        ServerTaskScheduler.scheduleServerTask(() -> onServerGhostRequest(request));
    }

    // ------------------------------------------------------------------ 访问器（列类共用）

    PocketInventory inventory() {
        return inventory;
    }

    PocketSlots slots() {
        return slots;
    }

    PanelSyncManager syncManager() {
        return syncManager;
    }

    /** 面板宿主玩家（{@link PocketSession#player()} 的实现；driver 只在服务端调）。 */
    @Override
    public EntityPlayer player() {
        return data.getPlayer();
    }

    ItemStack pocketStack() {
        return pocket;
    }

    /** 源质列是否可用（TC 缺席 ⇒ 整栏<b>灰显不隐藏</b>，R31）。 */
    boolean essenceAvailable() {
        return TaumCompat.isAvailable();
    }

    /** 源质表（S7 接管的同一实例）。 */
    PocketEssenceStore essenceStore() {
        return inventory.essence();
    }

    /** 派生出的 aspect 序（双端同序，客户端格式化用）。 */
    String[] essenceOrder() {
        return essenceTags;
    }

    // ------------------------------------------------------------------ C2S 请求（客户端只发码）

    /** Shift + 左键点中栏 = 语义②「口袋 → 玩家背包」。 */
    boolean requestTakeOut() {
        return sendAction(ACTION_TAKE_OUT, 0);
    }

    /** 语义①「整理中栏 150 格」。 */
    boolean requestSort() {
        return sendAction(ACTION_SORT, 0);
    }

    /** 需求 3 的两个通道按钮（R64c：动作码各自独立；客户端不算钱、不判模式）。 */
    boolean requestChannel(ChannelRequest request) {
        return sendAction(request == ChannelRequest.BURST ? ACTION_CHANNEL_BURST : ACTION_CHANNEL_SHORT, 0);
    }

    /**
     * 通道请求码（列类只带这个枚举，<b>不把服务端的状态机枚举塞进 GUI</b>）。
     * 两者最终在服务端各自落到 {@code PocketChannelState.Mode.BURST} / {@code .SHORT}（R64c 判据）。
     */
    public enum ChannelRequest {
        /** 瞬时：一次穿完，动画显示 5 秒，10 秒墙钟冷却。 */
        BURST,
        /** 短效：每秒一批、共 30 批。 */
        SHORT
    }

    /** 需求 5 的绑定键（服务端落点归 S6，见 {@link #performBind()}）。 */
    boolean requestBind() {
        return sendAction(ACTION_BIND, 0);
    }

    /**
     * 解绑<b>最后一条</b>（R74 的新入口：绑定按钮右键）。
     * <p>
     * ★不带 arg：旧口径的 arg 是"列表选中行"，而那一列已经随 R74② 删掉 ⇒
     * 任何仍要求"先选中"的解绑实现都等于没有解绑入口（静默失效）。
     */
    boolean requestUnbindLast() {
        return sendAction(ACTION_UNBIND_LAST, 0);
    }

    /** 解绑<b>全部</b>（R74 的新入口：绑定按钮 Shift + 右键）。 */
    boolean requestUnbindAll() {
        return sendAction(ACTION_UNBIND_ALL, 0);
    }

    /**
     * 绑定按钮的按键分发（<b>单点</b>读 Shift，避免列文件与面板各判一次）。
     * <p>
     * 左键 = 绑定；右键 = 解绑最后一条；Shift + 右键 = 清空全部（R74 裁定）。
     * ★不认识的按键一律返回 {@code true} 把点击吃掉：{@code ButtonWidget} 的
     * {@code onMousePressed} 返回 false 也是 ACCEPT，点击会穿透到下层槽（本仓踩过）。
     */
    boolean dispatchBindButtonClick(int button) {
        if (button == 0) {
            return requestBind();
        }
        if (button != 1) {
            return true;
        }
        return Interactable.hasShiftDown() ? requestUnbindAll() : requestUnbindLast();
    }

    /**
     * 源质格点击取出（需求 2 末句）。参数位：{@code arg = cell} 或 {@code cell + ESSENCE_OUT_SHIFT_FLAG}
     * （Shift = 一次取满一整堆晶，R44e③）。走 {@code SYNC_ACTION} 单通道 ⇒ 客户端只发码，
     * 扣点/物化/落点判定全在服务端 {@link #performEssenceOut}（R18/R19）。
     */
    boolean requestEssenceOut(int cell, String tag, boolean shift) {
        if (tag == null) {
            return false;
        }
        return sendAction(ACTION_ESSENCE_OUT, shift ? cell + PocketConstants.ESSENCE_OUT_SHIFT_FLAG : cell);
    }

    private boolean sendAction(int code, int arg) {
        if (syncManager.isClient()) {
            syncManager.findSyncHandler(SYNC_ACTION, IntSyncValue.class)
                .setValue(code * ACTION_ARG_BASE + arg);
        } else {
            onServerAction(code * ACTION_ARG_BASE + arg);
        }
        return true;
    }

    /**
     * 服务端动作分发。★<b>只在服务器主线程被调用</b>：C2S 包入口 {@link #receiveServerAction(int)}
     * 已按 R71 的字节码证据把执行体投递过 {@link ServerTaskScheduler}（setter 本身在 Netty IO 线程），
     * 本方法内的背包/钱包/通道操作因此不需要再各自防线程。
     */
    private void onServerAction(int packed) {
        if (syncManager.isClient()) {
            return;
        }
        final int code = packed / ACTION_ARG_BASE;
        final int arg = packed % ACTION_ARG_BASE;
        switch (code) {
            case ACTION_TAKE_OUT:
                performTakeOut();
                break;
            case ACTION_SORT:
                performSort();
                break;
            case ACTION_CHANNEL_BURST:
                performChannelRequest(PocketChannelState.Mode.BURST);
                break;
            case ACTION_CHANNEL_SHORT:
                performChannelRequest(PocketChannelState.Mode.SHORT);
                break;
            case ACTION_BIND:
                performBind();
                break;
            case ACTION_UNBIND_LAST:
                performUnbindLast();
                break;
            case ACTION_UNBIND_ALL:
                performUnbindAll();
                break;
            case ACTION_ESSENCE_OUT:
                performEssenceOut(arg);
                break;
            default:
                break;
        }
    }

    // ------------------------------------------------------------------ 服务端动作实现

    /** 语义②「口袋 → 玩家背包」：逐格尝试入包，装不下就留在原地（不放地上，避免误丢）。 */
    private void performTakeOut() {
        final EntityPlayer player = player();
        if (player == null) {
            return;
        }
        boolean moved = false;
        for (int index = 0; index < inventory.storage()
            .getSlots(); index++) {
            final ItemStack stack = inventory.storage()
                .getStackInSlot(index);
            if (stack == null) {
                continue;
            }
            if (player.inventory.addItemStackToInventory(stack.copy())) {
                inventory.storage()
                    .setStackInSlot(index, null);
                moved = true;
            }
        }
        if (moved) {
            inventory.markDirty();
        }
    }

    /** 语义①「整理中栏 150 格」：合并同类 + 前移紧凑。 */
    private void performSort() {
        final int size = inventory.storage()
            .getSlots();
        final ItemStack[] snapshot = new ItemStack[size];
        for (int index = 0; index < size; index++) {
            final ItemStack stack = inventory.storage()
                .getStackInSlot(index);
            if (stack != null) {
                snapshot[index] = stack.copy();
                inventory.storage()
                    .setStackInSlot(index, null);
            }
        }
        final Map<String, ItemStack> merged = new LinkedHashMap<>();
        for (ItemStack stack : snapshot) {
            if (stack == null) {
                continue;
            }
            final String key = mergeKey(stack);
            final ItemStack existing = merged.get(key);
            if (existing == null) {
                merged.put(key, stack);
                continue;
            }
            final int free = existing.getMaxStackSize() - existing.stackSize;
            final int take = Math.min(free, stack.stackSize);
            if (take > 0) {
                existing.stackSize += take;
                stack.stackSize -= take;
            }
            if (stack.stackSize > 0) {
                merged.put(key + "#" + merged.size(), stack);
            }
        }
        // ★ghost 格不参与整理，也<b>不是</b>落点（R38 第 2 条"产物不能进自己"）：
        // 判据读服务端的 PocketFilterConfig，不读 widget 状态（widget 双端各一份，把显示层当真相即 bug）。
        // ghost 槽在转换时已被搬空（evictAndMakeGhost），因此"非 ghost 槽数 ≥ 合并后条目数"恒成立；
        // 仍保留兜底：万一存量档里 ghost 槽带着东西进来，走玩家背包而不是 break 掉（整理绝不吃件）。
        int cursor = 0;
        for (ItemStack stack : merged.values()) {
            cursor = nextRealSlot(cursor);
            if (cursor >= size) {
                giveToPlayer(stack);
                continue;
            }
            inventory.storage()
                .setStackInSlot(cursor++, stack);
        }
        inventory.markDirty();
    }

    /** 从 {@code from} 起的第一个<b>非 ghost</b> 中栏槽号（越界返回 {@code slots} 本身）。 */
    private int nextRealSlot(int from) {
        final int size = inventory.storage()
            .getSlots();
        int index = Math.max(0, from);
        while (index < size && inventory.isGhostItemSlot(index)) {
            index++;
        }
        return index;
    }

    /** 塞进玩家背包，装不下就掉在脚下（R40a 的非消耗退回口径；整理/搬空都不该凭空吞物品）。 */
    private void giveToPlayer(ItemStack stack) {
        final EntityPlayer target = player();
        if (target == null || stack == null) {
            return;
        }
        if (!target.inventory.addItemStackToInventory(stack)) {
            target.entityDropItem(stack, 0);
        }
    }

    private static String mergeKey(ItemStack stack) {
        final NBTTagCompound tag = stack.getTagCompound();
        return Item.getIdFromItem(stack.getItem()) + "@"
            + stack.getItemDamage()
            + "#"
            + (tag == null ? "" : tag.toString());
    }

    /**
     * 通道按钮的服务端入口（S6 / R64c + R14 扣费顺序）。
     * <p>
     * <b>顺序是判据，不是风格</b>（R14）：点检（识别 → 冷却 → 余额）→ {@code tryDeduct} → 传输；
     * 任一点检不过就直接返回，<b>一分都不扣</b>。扣完之后传输全失败 ⇒ 同回执退款；
     * 部分失败不退（"搬走的东西已经搬走了"，退款等于白送一次）。
     * <p>
     * ⚠ 调用 {@code tryDeduct} <b>之前</b>必须断言 {@code cost > 0}：
     * {@code NekoWallet.tryDeduct} 的 {@code if (amount <= 0) return true;} 会让"零成本扣费"
     * 静默成功（R58b 点名的陷阱）。本方法里两个成本都来自 {@code PocketConstants}，
     * 一旦有人把它们改成 0，这条断言就是唯一的止损线。
     */
    private void performChannelRequest(PocketChannelState.Mode mode) {
        if (!serverGuardOk()) {
            return;
        }
        final UUID uuid = playerId();
        final EntityPlayer target = player();
        if (uuid == null || target == null) {
            return;
        }
        final PocketCellBindings bindings = inventory.bindings();
        final PocketChannelOps ops = new PocketAeChannelOps(target, pocket, this);
        // 点检 1：识别（R6/R64c）—— 至少一枚绑定元件当前"在带电驱动器/ME 箱里且有可用通道"。
        // getCellArray 全空 ⇒ 拒绝开道并回独立回执码，客户端那侧的置灰只是体验层。
        if (bindings.isEmpty() || !anyRecognised(bindings, ops)) {
            putReceipt("gtit.pocket.receipt.unrecognised", 0);
            return;
        }
        final boolean burst = mode == PocketChannelState.Mode.BURST;
        final int cost = burst ? PocketConstants.BURST_COST_NEKO : PocketConstants.SHORT_COST_SHIMMERING_NEKO;
        final String currency = burst ? NekoCurrencyRegistrar.NEKO_ID : NekoCurrencyRegistrar.SHIMMERING_NEKO_ID;
        if (cost <= 0) {
            // ★tryDeduct 的 amount<=0 会直接 return true ⇒ 宁可不开道也不做"零成本扣费"
            GTInterestingThing.LOG.warn("[pocket] 通道成本非正（mode={}, cost={}），拒绝开道", mode, cost);
            putReceipt("gtit.pocket.receipt.nothing_to_do", 0);
            return;
        }
        // 点检 2：冷却（R16 双维墙钟；只有瞬时通道有冷却）
        final NBTTagCompound root = pocket == null ? null : pocket.getTagCompound();
        final long cooldown = burst
            ? PocketChannelManager.burstRemaining(PocketChannelManager.INSTANCE.peek(uuid), root, ops.nowMs())
            : 0L;
        if (cooldown > 0L) {
            putReceipt("gtit.pocket.receipt.cooldown", (int) cooldown);
            return;
        }
        // 点检 3：余额（团队钱包由 NekoWalletManager 内部路由，这里只认"付钱主体是这个人"）
        final NekoWallet wallet = NekoWalletManager.INSTANCE.getWallet(uuid);
        if (wallet == null || wallet.getCount(currency) < cost) {
            putReceipt("gtit.pocket.receipt.no_funds", cost);
            return;
        }
        if (!wallet.tryDeduct(currency, cost)) {
            putReceipt("gtit.pocket.receipt.no_funds", cost);
            return;
        }
        // 传输
        final boolean accepted = PocketChannelManager.INSTANCE.openChannel(
            uuid,
            mode,
            bindings,
            inventory.filters(),
            root,
            ops,
            PocketChannelManager.pairsPerBatchFromConfig());
        if (!accepted) {
            refund(wallet, currency, cost);
            putReceipt(burst ? "gtit.pocket.receipt.cooldown" : "gtit.pocket.receipt.nothing_to_do", 0);
            return;
        }
        if (burst) {
            final PocketChannelState state = PocketChannelManager.INSTANCE.peek(uuid);
            final PocketChannelRunner.Report report = state == null ? null : state.lastReport();
            if (report == null || (report.transferred <= 0 && report.failures() <= 0)) {
                // 全失败（一件都没动、也没有可解释的失败）⇒ 退款（R14"全失败同回执退"）
                refund(wallet, currency, cost);
                putReceipt("gtit.pocket.receipt.nothing_to_do", 0);
            } else {
                putReceipt(receiptOfReport(report), report.transferred, report.failures());
            }
            // burst 的 work 位与显示窗一起开（5 秒 = BURST_SHOW_TICKS tick，R37/R62：动画走倒计时）
            ItemNekoDimensionPocket
                .startWorkAnimation(pocket, PocketConstants.BURST_SHOW_TICKS, PocketConstants.BURST_SHOW_TICKS);
        }
        // 短效通道：剩余秒数由 composeRemain 走 NBT 倒计时回显，无需即时回执（模式行同时刷新）
    }

    /** 至少一枚绑定元件"被识别"（R6 的 getCellArray 非空口径，不缓存结论、不看 isPowered）。 */
    private static boolean anyRecognised(PocketCellBindings bindings, PocketChannelOps ops) {
        for (String id : bindings.cells()) {
            if (ops.isCellLost(id)) {
                continue;
            }
            final List<String> channels = ops.channelIdsOf(id);
            if (channels != null && !channels.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /**
     * 一批的统计 → 回执键（R10：分区拒收与元件满是两条键，混用会被玩家读成丢件）。
     * <p>
     * public static 的理由：这张"统计 → 玩家可见文案"的映射表是本需求里最容易静默退化的一处
     * （把 {@code FILTER_REJECTED} 与 {@code FULL} 并成一条，编译与运行都不报错，只有玩家会以为丢件），
     * 所以留给零依赖回归套件直接钉住（{@code partition_whitelist_receipt_differs_from_cell_full}）。
     */
    public static String receiptOfReport(PocketChannelRunner.Report report) {
        if (report.transferred <= 0) {
            if (report.lost > 0) {
                return "gtit.pocket.receipt.unrecognised";
            }
            if (report.targetFull > 0) {
                return "gtit.pocket.receipt.target_full";
            }
            if (report.filterRejected > 0) {
                return "gtit.pocket.receipt.partition_denied";
            }
            if (report.full > 0) {
                return "gtit.pocket.receipt.cell_full";
            }
            return "gtit.pocket.receipt.nothing_to_do";
        }
        return report.failures() > 0 ? "gtit.pocket.receipt.partial" : "gtit.pocket.receipt.ok";
    }

    private static void refund(NekoWallet wallet, String currency, int cost) {
        if (wallet != null && cost > 0) {
            wallet.addCount(currency, cost);
        }
    }

    /**
     * 绑定键（R40a/R43a：只读 {@code diskuuid}，原栈<b>非消耗</b>地放回玩家处）。
     * <p>
     * 绑定时元件在手里 ⇒ 此刻必然尚未识别 ⇒ 位置五键一律写 {@link PocketConstants#UNLOCATED}
     * （R40b 的正常态，文案 {@code bind.unlocated} 不得写成失败语气）。位置快照此后只由
     * {@code PocketCellProbe} 经 {@link PocketCellBindings#recordLocation} 回填
     * （S1fix 风险⑥：误用 7 参 {@code bind(...)} 回填会把已有定位抹掉）。
     */
    private void performBind() {
        if (!serverGuardOk()) {
            return;
        }
        final ItemStack candidate = inventory.bindSlot()
            .getStackInSlot(0);
        final String uuid = PocketCellProbe.cellUuid(candidate);
        if (uuid == null) {
            // 格子里不是元件（或没放东西）：什么都不做，原栈留在格内由玩家自己拿走
            putReceipt("gtit.pocket.bind.slot_hint", 0);
            return;
        }
        final PocketCellBindings bindings = inventory.bindings();
        if (!bindings.hasRoom() && !bindings.contains(uuid)) {
            putReceipt("gtit.pocket.bind.full", PocketConstants.MAX_BOUND_CELLS);
            return;
        }
        bindings.bind(uuid, PocketConstants.MODE_DISK_UUID);
        inventory.markDirty();
        returnToPlayerFromBindSlot(candidate);
        // 绑定成功不发回执：按钮 tooltip 的行本来就是 SYNC_BIND_ROWS 推的，读数变了即是反馈
        // （R40b：绑定时元件在手里，位置五键必然是未定位 —— bind.unlocated 是正常态不是失败，
        // 故意不复用它当回执，免得玩家读成"绑定失败了"）。added=false 是同身份重绑，只覆盖位置快照。
    }

    /** R40a：绑定格里的元件原样退回（背包满则掉脚下），绝不"吃掉"元件。 */
    private void returnToPlayerFromBindSlot(ItemStack stack) {
        inventory.bindSlot()
            .setStackInSlot(0, null);
        if (stack != null) {
            giveToPlayer(stack);
        }
    }

    /**
     * 解绑<b>最后一条</b>（R74 裁定：绑定按钮右键）。与 ghost 格的右键解绑仍是两套实现
     * （前者摘绑定表的元件身份、后者撤一格 ghost 声明），<b>不得合并</b>。
     * <p>
     * 只从绑定表摘除：元件本身一直在 AE 网络里，解绑不动它的内容，也不动 {@code PocketCellProbe}
     * 的观测表。空表时发"尚未绑定元件"回执（玩家侧必须知道这一下点空了，R10）。
     */
    private void performUnbindLast() {
        if (!serverGuardOk()) {
            return;
        }
        final PocketCellBindings bindings = inventory.bindings();
        if (!bindings.unbindLast()) {
            putReceipt("gtit.pocket.bind.none", 0);
            return;
        }
        inventory.markDirty();
    }

    /**
     * 解绑<b>全部</b>（R74 裁定：绑定按钮 Shift + 右键）。
     * <p>
     * 与 {@link #performUnbindLast()} 共用同一条"只摘表、不动元件、不动观测表"的口径；
     * 空表同样给回执，免得玩家以为"没反应 = 清空失败"。
     */
    private void performUnbindAll() {
        if (!serverGuardOk()) {
            return;
        }
        final PocketCellBindings bindings = inventory.bindings();
        if (bindings.isEmpty()) {
            putReceipt("gtit.pocket.bind.none", 0);
            return;
        }
        final int cleared = bindings.size();
        bindings.clear();
        inventory.markDirty();
        putReceipt("gtit.pocket.bind.cleared", cleared);
    }

    /**
     * 源质格取出（需求 2 末句「要素栏里的要素拿出来自动变晶化源质」，R15 的第三条独立路径）。
     * <p>
     * 换算 1 点 = 1 晶、Shift 一次取满一格（{@link PocketConstants#ESSENCE_OUT_SHIFT_POINTS}，
     * R44e③）。<b>先扣点、后物化、放不下就退点</b>：三步任何一步失败都不会凭空造晶，也不会
     * 把点数值吞掉（TC 缺席 ⇒ {@code newCrystalStack} 返回 null ⇒ 点数原样退回）。
     */
    private void performEssenceOut(int packedArg) {
        if (!serverGuardOk()) {
            return;
        }
        final boolean shift = packedArg >= PocketConstants.ESSENCE_OUT_SHIFT_FLAG;
        final int cell = shift ? packedArg - PocketConstants.ESSENCE_OUT_SHIFT_FLAG : packedArg;
        final String[] order = TaumCompat.aspectOrder();
        if (cell < 0 || cell >= order.length) {
            return;
        }
        final String tag = order[cell];
        final PocketEssenceStore store = inventory.essence();
        final int wanted = shift ? PocketConstants.ESSENCE_OUT_SHIFT_POINTS : PocketConstants.ESSENCE_OUT_UNIT_POINTS;
        final int points = store.extract(tag, wanted);
        if (points <= 0) {
            putReceipt("gtit.pocket.still.idle", 0);
            return;
        }
        final ItemStack crystals = TaumCompat.newCrystalStack(tag, points);
        final int moved = crystals == null ? 0 : depositItem(crystals);
        if (moved < points) {
            // 物化失败（TC 缺席/该 tag 不可物化）或落点装不下：点数退回原格，绝不销毁价值
            store.add(tag, points - moved);
        }
        inventory.markDirty();
        putReceipt(moved > 0 ? "gtit.pocket.receipt.ok" : "gtit.pocket.receipt.target_full", moved);
    }

    // ------------------------------------------------------------------ S5 · ghost 就地转换（NEI 拖入 / 右键解绑）

    /**
     * 中栏槽位 widget 的登记（装配期由 {@link NekoPocketStorageColumn} 逐格调用，双端各 150 次）。
     * <p>
     * ghost 切换是"原位改属性"（R41b/R46d），所以必须能按槽号取到<b>那一个</b>实例；
     * 登记表长度与格序恒定 ⇒ 不引入任何数据驱动的 widget 树变化，128 格的
     * {@code SlotGroupWidget.matrix} 字面量也不因 ghost 改变。
     */
    void trackItemSlot(int index, NekoFilterSlot widget) {
        if (index >= 0 && index < itemSlots.length) {
            itemSlots[index] = widget;
        }
    }

    /**
     * 某个流体槽的登记（装配期由 {@link NekoPocketLeftColumn} 逐列调，双端各 6 次）。
     * <p>
     * 下标 = 列号 = {@code Kind.FLUID} 的 ghost 槽号 = tank 号（R75① 的三位一体），
     * 所以这里必须是数组而不是单个字段。
     */
    void trackFluidSlot(int index, NekoPocketFluidSlot widget) {
        if (index >= 0 && index < fluidSlots.length) {
            fluidSlots[index] = widget;
        }
    }

    /** 源质格的登记（装配期逐格调，双端各 48 次；下标 = {@code Kind.ESSENCE} 的槽号）。 */
    void trackEssenceCell(int index, NekoEssenceGhostCell widget) {
        if (index >= 0 && index < essenceCells.length) {
            essenceCells[index] = widget;
        }
    }

    /**
     * 客户端 → 服务端的 ghost 请求（本方法在客户端执行，<b>只发请求</b>）。
     *
     * @param slotIndex  被就地转换的槽号（区域由 {@code payloadKey} 的前缀自己说明）
     * @param payloadKey 载荷键（{@code PocketFilterConfig} 的 {@code itemKey}/{@code fluidKey}/{@code essenceKey}）
     */
    boolean requestGhost(int slotIndex, String payloadKey) {
        return sendGhostRequest(PocketGhostRequest.setRequest(slotIndex, payloadKey));
    }

    /**
     * 客户端 → 服务端的 ghost <b>解绑</b>请求（右键；★必须带区域，见 {@link PocketGhostRequest} 的文法说明）。
     *
     * @param kind      被解绑的区域（{@code ITEM} = 中栏格、{@code FLUID} = 流体条、{@code ESSENCE} = 源质格）
     * @param slotIndex 该区域内的槽号
     */
    boolean requestGhostClear(PocketFilterConfig.Kind kind, int slotIndex) {
        return sendGhostRequest(PocketGhostRequest.clearRequest(kind, slotIndex));
    }

    private boolean sendGhostRequest(String request) {
        if (syncManager.isClient()) {
            syncManager.findSyncHandler(SYNC_GHOST_REQUEST, StringSyncValue.class)
                .setValue(request);
        } else {
            onServerGhostRequest(request);
        }
        return true;
    }

    /**
     * ★ghost 的<b>唯一</b>执行体（服务端；由 {@link #receiveGhostRequest(String)} 经
     * {@link ServerTaskScheduler} 投递后在<b>服务器主线程</b>调用，R71）。
     * <p>
     * R18/R19 的口径：客户端的 {@code handleDragAndDrop} 与 {@code onMousePressed} 都不做任何
     * 清空/落档动作，只把"请求"发上来；判定、落档、槽属性、虚化广播全在这里。
     * 文法解析、分区域越界判定与"按载荷类型分派 kind"全在 {@link PocketGhostRequest#apply}
     * （纯函数 ⇒ 由 {@code runPocketTest} 端到端钉住），本方法只剩守卫 + 写档 + 刷虚化三步。
     * 客户端传来的字符串一律不可信 —— 伪造包最多只能往自己口袋里写声明，改不到别人的口袋
     * （会话守卫 {@link #serverGuardOk()} 保证 {@link #pocket} 就是该玩家背包里那一枚）。
     */
    private void onServerGhostRequest(String request) {
        if (syncManager.isClient() || !serverGuardOk()) {
            return;
        }
        final PocketGhostRequest.Decision decision = PocketGhostRequest.apply(request, inventory.filters());
        if (!decision.changed()) {
            // REJECTED（文法不合法 / 越出该区域白名单 / 载荷解不出）与 UNCHANGED（重复拖同一载荷、
            // 解绑本来就没声明的格）都不写档、都不刷虚化 —— 脏标记一旦为真就要在关屏时序列化整份 NBT
            return;
        }
        inventory.markDirty();
        applyGhosts();
    }

    /**
     * 客户端专用：把服务端下发的 ghost blob 换进自己那份 {@link PocketFilterConfig}，再应用渲染。
     * <p>
     * 客户端的存储表在此处只是<b>显示镜像</b>（它从不写档，序列化只在服务端关屏钩子），
     * 因此覆盖它不会造成两处真相；真正决定行为的 {@code accessibility} 在服务端那份上生效。
     */
    private void applyGhostView(String blob) {
        if (!syncManager.isClient()) {
            return;
        }
        inventory.replaceFilters(parseGhostBlob(blob));
        applyGhosts();
    }

    /**
     * 解析 {@link #ghostBlobOf} 的机读形状；解不出的条目直接丢弃（外来/陈旧 blob 不得让面板炸）。
     * <p>
     * public static 的理由见 {@link #ghostBlobOf}。
     */
    public static PocketFilterConfig parseGhostBlob(String blob) {
        final PocketFilterConfig parsed = new PocketFilterConfig();
        if (blob == null || blob.isEmpty()) {
            return parsed;
        }
        for (String record : blob.split(";")) {
            final String[] parts = record.split("\\|", 3);
            if (parts.length < 3) {
                continue;
            }
            final PocketFilterConfig.Kind kind;
            final int slotIndex;
            try {
                kind = PocketFilterConfig.Kind.valueOf(parts[0]);
                slotIndex = Integer.parseInt(parts[1]);
            } catch (RuntimeException ignored) {
                continue;
            }
            final PocketFilterConfig.Filter payload = PocketFilterConfig.parseKey(parts[2]);
            if (payload == null || payload.kind() != kind) {
                continue;
            }
            // ★rebuild 走 PocketGhostRequest 那一份（服务端写入口与这里必须同一段代码，否则两处迟早漂移）
            parsed.add(slotIndex, PocketGhostRequest.rebuildAt(kind, slotIndex, payload));
        }
        return parsed;
    }

    /**
     * 把 ghost 声明<b>原位</b>应用到三个区域的格件上（双端各自应用自己那一份树，读的都是本地
     * {@code inventory.filters()} ⇒ 服务端权威值经 {@link #applyGhostView} 落到客户端镜像）。
     * <p>
     * 服务端这一份对中栏的作用是 {@code ModularSlot.accessibility(false, false)} ——
     * 那才是"禁放置禁取出"的执法点（vanilla {@code slotClick} 在服务端读 {@code isItemValid}
     * /{@code canTakeStack}，见 {@code ModularSlot.java:73-80}）；客户端那一份负责虚化渲染。
     * 流体条与源质格<b>不进 Container</b>（R35），它们在服务端那份只更新自身状态，
     * 但走的<b>是同一条代码路径</b>（不分叉 ⇒ 两端不会因"只有客户端应用"而漂移）。
     * 转 ghost 前先把中栏格内物品搬走（R38 第 2 条：产物不能进自己）。
     */
    private void applyGhosts() {
        applyItemGhosts();
        applyFluidGhosts();
        applyEssenceGhosts();
    }

    /** 中栏 150 格（{@code Kind.ITEM}）：唯一需要"搬空"的一支。 */
    private void applyItemGhosts() {
        for (int index = 0; index < itemSlots.length; index++) {
            final NekoFilterSlot widget = itemSlots[index];
            if (widget == null) {
                continue;
            }
            final PocketFilterConfig.Filter declared = inventory.filters()
                .at(PocketFilterConfig.Kind.ITEM, index);
            final ItemStack sample = declared == null ? null
                : PocketAeChannelOps.stackFromContentKey(declared.key(), 1);
            if (widget.isGhost() == (declared != null) && sameSample(widget.ghostSample(), sample)) {
                continue;
            }
            if (declared != null && !syncManager.isClient()) {
                // 只在服务端搬空：客户端那份 handler 改了也不会生效，反而与稍后到达的同步值分叉
                evictFromSlot(index);
            }
            widget.setGhost(declared != null, sample);
        }
    }

    /**
     * 6 个流体槽（{@code Kind.FLUID}，索引空间 = 列号）。
     * <p>
     * 每槽只切"本列声明了哪种流体"这一个显示状态：各自的 tank、{@code alwaysShowFull}、
     * 两格同权的灌排一个字都不动（流体 ghost 不搬空任何东西，声明本身就是"要拉这一种"）。
     * 循环上界取登记表长度（= {@code GHOST_FLUID_SLOT_LIMIT}）⇒ 与白名单同源，不会漂移。
     */
    private void applyFluidGhosts() {
        for (int index = 0; index < fluidSlots.length; index++) {
            final NekoPocketFluidSlot widget = fluidSlots[index];
            if (widget == null) {
                continue;
            }
            final PocketFilterConfig.Filter declared = inventory.filters()
                .at(PocketFilterConfig.Kind.FLUID, index);
            widget.setGhost(
                declared instanceof PocketFilterConfig.FluidFilter,
                declared instanceof PocketFilterConfig.FluidFilter fluid ? fluid.fluidName : "");
        }
    }

    /** 右栏 48 格（{@code Kind.ESSENCE}）：格位归属由 {@code aspectOrder()[index]} 钉死 ⇒ 只切开关。 */
    private void applyEssenceGhosts() {
        for (int index = 0; index < essenceCells.length; index++) {
            final NekoEssenceGhostCell cell = essenceCells[index];
            if (cell == null) {
                continue;
            }
            cell.setGhost(
                inventory.filters()
                    .at(PocketFilterConfig.Kind.ESSENCE, index) != null);
        }
    }

    /** 把某一格的内容并进别的格或塞回玩家背包（ghost 格必须空着，否则"产物进自己"）。 */
    private void evictFromSlot(int index) {
        final ItemStack stack = inventory.storage()
            .getStackInSlot(index);
        if (stack == null) {
            return;
        }
        inventory.storage()
            .setStackInSlot(index, null);
        final int moved = inventory.depositIntoStorage(stack.copy());
        final int left = stack.stackSize - moved;
        if (left > 0) {
            final ItemStack rest = stack.copy();
            rest.stackSize = left;
            giveToPlayer(rest);
        }
        inventory.markDirty();
    }

    private static boolean sameSample(ItemStack left, ItemStack right) {
        if (left == null || right == null) {
            return left == right;
        }
        return left.getItem() == right.getItem() && left.getItemDamage() == right.getItemDamage();
    }

    // ------------------------------------------------------------------ 防伪守卫（R19 三层）

    /**
     * L1 会话绑定：包体到达服务端后，重新核"这个玩家此刻开的就是这个面板、面板认的仍是那一枚口袋"。
     * <p>
     * 三层各自的落点（照 {@code decision-ledger.md} R19 的现成件口径）：
     * <ul>
     * <li><b>L1 会话绑定</b> = 本方法（{@code openContainer} 是 MUI2 的 {@code ModularContainer}
     * + {@code getGuiData()} 是 {@code PlayerInventoryGuiData} + 承载格上的栈仍是<b>同一枚</b>口袋）；</li>
     * <li>{@code L2 主线程} = <b>靠投递换来</b>，不是天然的（R71 更正本条旧口径）：MUI2 的 GUI 包
     * 走 Forge {@code SimpleNetworkWrapper}，其 {@code channelRead0} 在 <b>Netty IO 线程</b>直调 setter
     * （逐字节码依据见 {@link #receiveServerAction(int)}）⇒ 本方法的调用方
     * {@link #onServerAction(int)} 已由 {@link ServerTaskScheduler} 投递到服务器 tick END。
     * 这一跳只把动作推迟 ≤1 tick，扣费与传输仍在<b>同一次</b>执行体内完成，不构成两处真相；</li>
     * <li><b>L3 冷却闩 + cost&gt;0 断言</b> = {@link #performChannelRequest} 里的点检 2 与那段
     * {@code cost <= 0} 早退（规避 {@code NekoWallet.tryDeduct} 的 {@code amount<=0 return true}）。</li>
     * </ul>
     */
    private boolean serverGuardOk() {
        if (syncManager.isClient()) {
            return false;
        }
        final EntityPlayer target = player();
        if (target == null || pocket == null) {
            return false;
        }
        if (!(target.openContainer instanceof ModularContainer container)) {
            return false;
        }
        if (!(container.getGuiData() instanceof PlayerInventoryGuiData guiData)) {
            return false;
        }
        // 承载格上的栈必须还是这一枚（身份比较，不是 isItemEqual：换一枚就得重新开界面）
        final ItemStack atCarrier = guiData.getUsedItemStack();
        return atCarrier == pocket;
    }

    // ------------------------------------------------------------------ 回执（R39b/R10：模式与结果都只由服务端下发）

    /**
     * 服务端记一次回执（键 + 已搬运数 + 被拒数）。
     * <p>
     * 只发"键 + 两个数"，<b>不在服务端拼本地化文本</b>：客户端才是有 lang 表的那一端
     * （服务端拼出来的字符串到了客户端就再也翻不成玩家语言）。多带的第二个实参对
     * 只有一个 {@code %d} 的键是无害的（{@code String.format} 允许多余参数），
     * 而 {@code receipt.partial} 这种两占位的键正好共用同一份载荷。
     */
    private void putReceipt(String key, int moved) {
        putReceipt(key, moved, 0);
    }

    private void putReceipt(String key, int moved, int refused) {
        if (syncManager.isClient()) {
            return;
        }
        receiptKey = key;
        receiptMoved = moved;
        receiptRefused = refused;
    }

    private String composeReceipt() {
        return receiptKey == null ? ""
            : receiptKey + PocketConstants.GHOST_REQUEST_SEPARATOR
                + receiptMoved
                + PocketConstants.GHOST_REQUEST_SEPARATOR
                + receiptRefused;
    }

    private void applyReceipt(String text) {
        if (!syncManager.isClient() || text == null) {
            return;
        }
        if (text.isEmpty()) {
            receiptKey = null;
            receiptMoved = 0;
            receiptRefused = 0;
            return;
        }
        final String[] parts = text.split("\\|", 3);
        receiptKey = parts[0];
        receiptMoved = parts.length > 1 ? parseIntOrZero(parts[1]) : 0;
        receiptRefused = parts.length > 2 ? parseIntOrZero(parts[2]) : 0;
    }

    private static int parseIntOrZero(String text) {
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    /**
     * 最近一次动作的回执文本（左栏状态行渲染；空 = 还没有回执）。
     * <p>
     * 回执是<b>粘性</b>的：一直显示到下一次动作覆盖它。这样"分区拒收"这类必须让玩家看见的
     * 失败原因不会一闪就没（R10 的初衷就是别让失败静默）。
     */
    String receiptText() {
        if (receiptKey == null) {
            return "";
        }
        final String translated = StatCollector.translateToLocal(receiptKey);
        return translated.contains("%d") ? String.format(translated, receiptMoved, receiptRefused) : translated;
    }

    // ------------------------------------------------------------------ ghost 视图同步（S2C）

    private String composeGhostBlob() {
        return ghostBlobOf(inventory.filters());
    }

    /**
     * {@code kind|slot|payloadKey} 记录，{@code ';'} 分隔（分隔符见 {@code PocketConstants}）。
     * <p>
     * public static 的理由同上（编解码两端各一份实现就必须钉住，否则 ghost 会在同步中静默变形）。
     */
    public static String ghostBlobOf(PocketFilterConfig filters) {
        final StringBuilder builder = new StringBuilder();
        for (PocketFilterConfig.Filter filter : filters.filters()) {
            if (builder.length() > 0) {
                builder.append(';');
            }
            builder.append(filter.kind())
                .append(PocketConstants.GHOST_REQUEST_SEPARATOR)
                .append(filter.slotIndex())
                .append(PocketConstants.GHOST_REQUEST_SEPARATOR)
                .append(filter.key());
        }
        return builder.toString();
    }

    private void applyGhostBlob(String blob) {
        if (!syncManager.isClient() || blob == null || blob.equals(ghostBlob)) {
            return;
        }
        ghostBlob = blob;
        applyGhostView(blob);
    }

    /** 48 格点数 blob（逗号分隔，顺序 = {@link TaumCompat#aspectOrder()}）。 */
    private String composeEssenceBlob() {
        if (syncManager.isClient()) {
            return cachedEssenceBlob;
        }
        final PocketEssenceStore store = inventory.essence();
        final StringBuilder builder = new StringBuilder();
        for (int index = 0; index < essenceCache.length; index++) {
            if (index > 0) {
                builder.append(',');
            }
            builder.append(index < essenceTags.length ? store.get(essenceTags[index]) : 0);
        }
        cachedEssenceBlob = builder.toString();
        return cachedEssenceBlob;
    }

    private String cachedEssenceBlob = "";

    private void applyEssenceBlob(String blob) {
        if (!syncManager.isClient() || blob == null) {
            return;
        }
        final String[] parts = blob.split(",");
        for (int index = 0; index < essenceCache.length; index++) {
            int value = 0;
            if (index < parts.length) {
                try {
                    value = Integer.parseInt(parts[index]);
                } catch (NumberFormatException ignored) {
                    value = 0;
                }
            }
            essenceCache[index] = value;
        }
    }

    /**
     * 绑定行 blob：{@code id|status|dim|x|y|z|slot}，以 ';' 分隔。
     * <p>
     * <b>位置读探针而不是读绑定表内缓存</b>（§14.1，避免两处真相）；{@code N} = 未定位
     * （R40b 的<b>正常态</b>，绑定时元件在手里故必然尚未识别），{@code L} = 已定位，
     * {@code S} = 曾有位置而探针判失效（R39c：不得自动全图重扫）。
     */
    private String composeBindRows() {
        if (syncManager.isClient()) {
            return bindRowsBlob;
        }
        final PocketCellBindings bindings = inventory.bindings();
        final StringBuilder builder = new StringBuilder();
        for (PocketCellBindings.Entry entry : bindings.entries()) {
            if (builder.length() > 0) {
                builder.append(';');
            }
            builder.append(entry.id)
                .append('|');
            final PocketCellProbe.Located located = PocketCellProbe.INSTANCE.locationOf(entry.id);
            if (located == null) {
                builder.append(entry.located() ? 'S' : 'N')
                    .append('|')
                    .append(PocketConstants.UNLOCATED)
                    .append('|')
                    .append(PocketConstants.UNLOCATED)
                    .append('|')
                    .append(PocketConstants.UNLOCATED)
                    .append('|')
                    .append(PocketConstants.UNLOCATED)
                    .append('|')
                    .append(PocketConstants.UNLOCATED);
                continue;
            }
            builder.append('L')
                .append('|')
                .append(located.dim)
                .append('|')
                .append(located.x)
                .append('|')
                .append(located.y)
                .append('|')
                .append(located.z)
                .append('|')
                .append(located.slot);
        }
        return builder.toString();
    }

    private void applyBindRows(String blob) {
        if (syncManager.isClient()) {
            bindRowsBlob = blob == null ? "" : blob;
        }
    }

    /**
     * 模式行（R39b）。
     * <p>
     * ★两条轨道，但只有一个真相源：
     * <ul>
     * <li><b>有通道在跑</b> ⇒ 读 {@link PocketChannelState#pullMode()}，也就是 S6 激活时算的那一次。
     * 运行中途玩家新增/删掉 ghost 声明<b>不能</b>改变本次运行的轨道（否则界面显示"拉取中"而实际在推送，
     * 就是 R39b 要消灭的那类分叉）；</li>
     * <li><b>没有通道在跑</b> ⇒ 这是"下一次会怎么走"的预览，仍由<b>服务端</b>按 ghost 条数算，
     * 算完照样只发一个字符给客户端。</li>
     * </ul>
     * 客户端任何情况下都只格式化 {@code 'P'/'p'} 这一个字符，<b>不得</b>按本地 ghost 表推断（R19）。
     */
    private String composeModeState() {
        if (syncManager.isClient()) {
            return (pullMode ? 'P' : 'p') + "|" + filterCount;
        }
        final UUID uuid = playerId();
        final PocketChannelState state = uuid == null ? null : PocketChannelManager.INSTANCE.peek(uuid);
        final boolean running = state != null && !state.idle();
        final int filters = inventory.filters()
            .size();
        if (running) {
            return (state.pullMode() ? 'P' : 'p') + "|" + filters;
        }
        return (filters > 0 ? 'P' : 'p') + "|" + filters;
    }

    private void applyModeState(String text) {
        if (!syncManager.isClient() || text == null || text.length() < 2) {
            return;
        }
        pullMode = text.charAt(0) == 'P';
        try {
            filterCount = Integer.parseInt(text.substring(2));
        } catch (NumberFormatException ignored) {
            filterCount = 0;
        }
    }

    /** 冷却与剩余秒数：瞬时走 R16 双维墙钟冷却，短效走 R24/R62 的 NBT 倒计时。 */
    private String composeRemain() {
        if (syncManager.isClient()) {
            return instantRemainSeconds + "|" + timedRemainSeconds;
        }
        final NBTTagCompound root = pocket == null ? null : pocket.getTagCompound();
        final long nowMs = System.currentTimeMillis();
        final UUID uuid = playerId();
        final PocketChannelState state = uuid == null ? null : PocketChannelManager.INSTANCE.peek(uuid);
        final long instant = PocketChannelState.burstCooldownRemaining(
            PocketChannelState.readDeviceLastBurstAtMs(root),
            state == null ? 0L : state.lastBurstAtMs(),
            nowMs,
            PocketConstants.BURST_COOLDOWN_SECONDS);
        final int workTicks = root == null ? 0 : root.getInteger(PocketConstants.UI_WORK_TICKS);
        return instant + "|" + (workTicks + 19) / 20;
    }

    private void applyRemainState(String text) {
        if (!syncManager.isClient() || text == null) {
            return;
        }
        final int split = text.indexOf('|');
        if (split < 0) {
            instantRemainSeconds = 0;
            timedRemainSeconds = 0;
            return;
        }
        try {
            instantRemainSeconds = Integer.parseInt(text.substring(0, split));
            timedRemainSeconds = Integer.parseInt(text.substring(split + 1));
        } catch (NumberFormatException ignored) {
            instantRemainSeconds = 0;
            timedRemainSeconds = 0;
        }
    }

    /**
     * 蒸馏进度读数（S2C 单向：分子/分母都在服务端算）。
     * <p>
     * 推进本体在 {@code PocketDistillDriver.onItemTick}（宿主同样是 {@code Item.onUpdate}，R57c⑤），
     * 节拍权威是 {@code TaumDistillRules.DISTILL_INTERVAL_TICKS} ⇒ 本方法里<b>没有</b>第二个 {@code 100}
     * （计划 §17.2 第 4 条）。源质格装不下时 driver 回报 1.0 并<b>停在那里不重跑</b>（R29 全有全无），
     * 这条读数因此可以直译成"进度条满着但东西没少"。
     */
    private double serverDistillProgress() {
        if (syncManager.isClient()) {
            return clientDistillProgress;
        }
        return PocketDistillDriver.progressOf(playerId());
    }

    /** 蒸馏状态位（左栏/进度条 tooltip 用；文案走 {@code still.*}，不常驻渲染）。 */
    PocketDistillDriver.Status distillStatus() {
        return PocketDistillDriver.statusOf(playerId());
    }

    /** 距下一轮蒸馏还剩几秒（{@code still.progress} 的 {@code %d}）。 */
    int distillSecondsToNext() {
        return PocketDistillDriver.secondsToNextBatch(playerId());
    }

    @Override
    public UUID playerId() {
        final EntityPlayer target = player();
        return target == null ? null
            : target.getGameProfile()
                .getId();
    }

    // ------------------------------------------------------------------ PocketSession 实现（S6/S7 的活会话）
    //
    // 客户端那份实例<b>从不</b>登记进 PocketSessions（登记包在 assemble 的 !isClient 分支里），
    // 因此下面这批方法只在服务端被 driver 调用。

    @Override
    public ItemStack carrierStack() {
        return pocket;
    }

    @Override
    public NBTTagCompound carrierTag() {
        return pocket == null ? null : pocket.getTagCompound();
    }

    @Override
    public boolean isOpen() {
        return !closed;
    }

    /**
     * 承载口袋是否还在玩家主背包 36 格里（= {@code Item.onUpdate} 的覆盖范围，R57c⑥）。
     * <p>
     * 被塞进箱子/掉在地上/换成一枚别的口袋 ⇒ 本方法转 false ⇒ 会话由离线/停服挂钩兜底回收
     * （driver 再也等不到它的那一拍）。
     */
    @Override
    public boolean isHeldByOwner() {
        final EntityPlayer target = player();
        if (target == null || target.inventory == null || pocket == null) {
            return false;
        }
        for (ItemStack held : target.inventory.mainInventory) {
            if (held == pocket) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void markDirty() {
        inventory.markDirty();
    }

    @Override
    public void persistIdle() {
        if (!closed) {
            // 界面还开着 ⇒ 写权属面板的关屏钩子（R35），此处一个字节都不动
            return;
        }
        writeSessionToCarrier();
    }

    @Override
    public void persistFinal() {
        writeSessionToCarrier();
    }

    /**
     * 会话内存 → 承载栈 NBT（脏标记才序列化，R53c）。
     * <p>
     * 与关屏落盘共用同一段代码 ⇒ "谁在写档"只有一个答案；找不到承载栈时只 warn 不落盘
     * （写到别的栈上才是事故，R35 防御③）。
     */
    private void writeSessionToCarrier() {
        if (!inventory.isDirty()) {
            return;
        }
        final ItemStack carrier = closed ? relocateCarrier() : pocket;
        if (carrier == null) {
            GTInterestingThing.LOG.warn("[pocket] 落盘时找不到承载口袋的槽位（原格 {}），本次内容暂不落档", carrierSlotIndex);
            return;
        }
        pocket = carrier;
        NBTTagCompound root = carrier.getTagCompound();
        if (root == null) {
            root = new NBTTagCompound();
            carrier.setTagCompound(root);
        }
        inventory.writeTo(root);
        inventory.markClean();
    }

    @Override
    public PocketCellBindings bindings() {
        return inventory.bindings();
    }

    @Override
    public PocketFilterConfig filters() {
        return inventory.filters();
    }

    @Override
    public PocketEssenceStore essence() {
        return inventory.essence();
    }

    @Override
    public ItemStack distillInputStack(int index) {
        return index < 0 || index >= inventory.distillInput()
            .getSlots() ? null
                : inventory.distillInput()
                    .getStackInSlot(index);
    }

    @Override
    public int distillInputSlots() {
        return inventory.distillInput()
            .getSlots();
    }

    /** 一轮蒸馏对一格只做"减 1"（R28：5 秒是节拍不是产量，一次消耗一件）。 */
    @Override
    public void consumeOneDistillInput(int index) {
        final ItemStack at = distillInputStack(index);
        if (at == null) {
            return;
        }
        if (at.stackSize <= 1) {
            inventory.distillInput()
                .setStackInSlot(index, null);
        } else {
            final ItemStack rest = at.copy();
            rest.stackSize = at.stackSize - 1;
            inventory.distillInput()
                .setStackInSlot(index, rest);
        }
        inventory.markDirty();
    }

    /** 先口袋中栏（跳过 ghost 格）、再玩家背包兜底；<b>不</b>掉地下（拉取是自动行为）。 */
    @Override
    public int depositItem(ItemStack stack) {
        if (stack == null || stack.stackSize <= 0) {
            return 0;
        }
        final int want = stack.stackSize;
        int moved = inventory.depositIntoStorage(stack);
        int left = want - moved;
        final EntityPlayer target = player();
        while (left > 0 && target != null) {
            final ItemStack chunk = stack.copy();
            chunk.stackSize = Math.min(left, chunk.getMaxStackSize());
            if (!target.inventory.addItemStackToInventory(chunk)) {
                break;
            }
            left -= chunk.stackSize;
            moved += chunk.stackSize;
        }
        return moved;
    }

    @Override
    public int fluidBarRoom(int tank, FluidStack probe) {
        return inventory.fluidBarRoom(tank, probe);
    }

    @Override
    public int depositFluid(int tank, FluidStack fluid) {
        return inventory.depositFluidIntoBar(tank, fluid);
    }

    // ------------------------------------------------------------------ 关屏（R35 四道防御）

    /**
     * 承载格是否仍可交互（R35 防御②）。★<b>每 tick 被调一次，返回 false 即服务端当场关容器</b>：
     * {@code UISettings#canPlayerInteractWithUI} → {@code ModularContainer#canInteractWith}
     * → {@code EntityPlayerMP.java:249}（{@code ForgeHooks.canInteractWith} 为假就 {@code closeContainer}），
     * 客户端随后用 {@code ReopenGui} 重开 ⇒ 玩家侧只看到"右键没反应"的开关死循环。
     * <p>
     * ★<b>判据不得用对象身份</b>：{@code PlayerInventoryGuiData#getUsedItemStack()} 是
     * {@code InventoryType#getStackInSlot} 的<b>活查表</b>（每次现读背包），而堆叠合并、
     * 跨维重建 {@code EntityPlayerMP} 都会让"同一枚口袋"变成<b>另一个引用 / 另一个 player 对象</b>
     * ⇒ 用 {@code ==} 比引用或比 player 引用会把合法持有者也判成丢失。
     * 这里改判"承载格里仍是一枚口袋物品"，并把内容写权重新钉到那一枚上（关屏与 driver 都走
     * {@link #relocateCarrier()} 重定位，不会写到别的栈上）。
     */
    private boolean carrierStillPresent(EntityPlayer testPlayer) {
        if (closed || testPlayer == null || pocket == null) {
            return false;
        }
        final EntityPlayer owner = player();
        if (owner != null && (owner.getUniqueID() == null || !owner.getUniqueID()
            .equals(testPlayer.getUniqueID()))) {
            return false;
        }
        final ItemStack atCarrier = data.getUsedItemStack();
        if (atCarrier == null || atCarrier.getItem() == null
            || pocket.getItem() == null
            || atCarrier.getItem() != pocket.getItem()) {
            return false;
        }
        if (atCarrier != pocket) {
            pocket = atCarrier;
        }
        return true;
    }

    /**
     * 关屏落点（{@link NekoPocketContainer} 调用，<b>只在服务端</b>）：
     * 脏标记才序列化（R53c）+ {@code open} 位清零（R37）。
     * <p>
     * ★<b>不摘 {@code PocketSessions} 登记</b>（R57c①）：30 秒通道与"物品留在格里继续蒸"都要活过关屏，
     * 写权因此从本方法转交给 driver（{@link #persistIdle()}）。摘除点只有两处，且都先落盘再摘：
     * {@code PocketChannelDriver}（无活可干即回收）与 {@code PocketLifecycleHandler}（离线/停服）。
     */
    void onContainerClosed() {
        if (closed) {
            return;
        }
        GTInterestingThing.LOG.info("[pocket][diag] 关屏钩子触发（若紧跟在 createScreen 之后 ⇒ 界面是被立刻关掉，不是没建）");
        final ItemStack carrier = relocateCarrier();
        if (carrier == null) {
            // R35 防御③：重定位失败 ⇒ 只 warn 不落盘（写到别的栈上才是事故）
            GTInterestingThing.LOG.warn("[pocket] 关屏时找不到承载口袋的槽位（原格 {}），本次会话内容不落盘", carrierSlotIndex);
            closed = true;
            return;
        }
        ItemNekoDimensionPocket.setOpenFlag(carrier, false);
        closed = true;
        writeSessionToCarrier();
    }

    /**
     * 找到承载本会话的那一枚口袋。三级判据，<b>对象身份优先</b>：
     * <ol>
     * <li>开界面那一格上的栈还是同一枚对象 ⇒ 用它；</li>
     * <li>主背包 36 格里扫同一对象（玩家在界面里搬动过口袋）；</li>
     * <li>最后才用 {@code open} 标记兜底（口径照 {@code ItemGTToolbox.java:391-425}）。</li>
     * </ol>
     * ★为什么不把 {@code open} 位当第一判据（本批修正）：关屏时该位已被清零，而 driver 在关屏之后
     * 还要继续落盘（上面那条），按标记找就永远找不着 ⇒ 只剩一条 warn、内容留在内存里等丢。
     * 按对象身份找则不受清零影响；兜底那一档仍然按标记认人，覆盖"重载后对象身份已断"的情形。
     */
    private ItemStack relocateCarrier() {
        final ItemStack atCarrier = data.getUsedItemStack();
        if (atCarrier != null && atCarrier == pocket) {
            return pocket;
        }
        final EntityPlayer target = player();
        if (target == null || target.inventory == null) {
            return null;
        }
        if (pocket != null) {
            for (ItemStack held : target.inventory.mainInventory) {
                if (held == pocket) {
                    return held;
                }
            }
        }
        for (ItemStack held : target.inventory.mainInventory) {
            if (held != null && held.getItem() instanceof ItemNekoDimensionPocket
                && ItemNekoDimensionPocket.isOpenFlag(held)) {
                pocket = held;
                return held;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ 客户端显示出口

    /** 源质格数量浮层文本（空 = 不画）。 */
    String essenceAmountText(int cell) {
        final int amount = cell >= 0 && cell < essenceCache.length ? essenceCache[cell] : 0;
        return amount > 0 ? String.valueOf(amount) : "";
    }

    /** 源质格 tooltip 明细（{@code aspect.cell} = {@code %s：%d/%d}，上限取常量，R58b/契约 §7-5）。 */
    String essenceAmountDetail(String tag) {
        return String.format(
            StatCollector.translateToLocal("gtit.pocket.aspect.cell"),
            TaumCompat.nameOf(tag),
            syncManager.isClient() ? essenceAmountByTag(tag)
                : inventory.essence()
                    .get(tag),
            PocketConstants.ESSENCE_CAP_PER_TAG);
    }

    private int essenceAmountByTag(String tag) {
        if (tag == null) {
            return 0;
        }
        for (int index = 0; index < essenceTags.length && index < essenceCache.length; index++) {
            if (tag.equals(essenceTags[index])) {
                return essenceCache[index];
            }
        }
        return 0;
    }

    /** 派生表是否被截断（第 49 项起只存不显，R26）。 */
    boolean essenceOverflow() {
        return essenceTags.length > PocketConstants.ESSENCE_DISPLAY_GRID;
    }

    /**
     * 绑定按钮的 tooltip 正文（R74② 之后绑定信息的<b>唯一</b>可见面）。
     * <p>
     * 形状 = 标题 + 至多 {@code NekoPocketBottomBand.TOOLTIP_ROWS} 条
     * "{@code bind.entry} 换行 {@code bind.located|unlocated|stale}"，
     * ★条目多于上限时<b>必须</b>补一行 {@code bind.truncated}（含未列出的条数）——
     * {@code MAX_BOUND_CELLS} 是 64 而 tooltip 不能滚，"只显示前 N 条却不说明"就是静默删信息
     * （R36 的"宽度不够就加 tooltip，不删信息"在这里的唯一合法形态）。
     */
    String bindTooltipText() {
        final java.util.List<NekoPocketBottomBand.Row> rows = bindRows();
        final StringBuilder builder = new StringBuilder();
        builder.append(StatCollector.translateToLocal("gtit.pocket.bind.title"))
            .append("\n");
        if (rows.isEmpty()) {
            builder.append(StatCollector.translateToLocal("gtit.pocket.bind.none"));
            return builder.toString();
        }
        final int shown = Math.min(rows.size(), NekoPocketBottomBand.TOOLTIP_ROWS);
        for (int index = 0; index < shown; index++) {
            builder.append(bindRowLine(index))
                .append("\n");
        }
        if (rows.size() > shown) {
            builder.append(
                String.format(StatCollector.translateToLocal("gtit.pocket.bind.truncated"), rows.size() - shown));
        }
        return builder.toString();
    }

    /** 绑定块第一行的常驻读数（"已绑定 n/上限"，数字全部由服务端同步的行数与常量给出）。 */
    String bindSummaryText() {
        return String.format(
            StatCollector.translateToLocal("gtit.pocket.bind.summary"),
            bindRows().size(),
            PocketConstants.MAX_BOUND_CELLS);
    }

    /** 一条绑定行的完整文本（短码 ID + 位置或状态，与旧第四列的两行渲染同一条算式）。 */
    private String bindRowLine(int index) {
        final NekoPocketBottomBand.Row row = rowAt(index);
        if (row == null) {
            return "";
        }
        final String title = String.format(StatCollector.translateToLocal("gtit.pocket.bind.entry"), row.shortId());
        final String location;
        if (row.located()) {
            location = String.format(
                StatCollector.translateToLocal("gtit.pocket.bind.located"),
                row.dim,
                row.x,
                row.y,
                row.z,
                row.slot);
        } else {
            location = StatCollector
                .translateToLocal(row.stale() ? "gtit.pocket.bind.stale" : "gtit.pocket.bind.unlocated");
        }
        return title + " " + location;
    }

    /** 当前绑定行（客户端只解析服务端 blob，绝不按内存表推断，R39b/R19）。 */
    private java.util.List<NekoPocketBottomBand.Row> bindRows() {
        if (bindRowsBlob.isEmpty()) {
            return java.util.Collections.emptyList();
        }
        return NekoPocketBottomBand.Row.parse(bindRowsBlob);
    }

    private NekoPocketBottomBand.Row rowAt(int index) {
        final java.util.List<NekoPocketBottomBand.Row> rows = bindRows();
        return index < 0 || index >= rows.size() ? null : rows.get(index);
    }

    /**
     * 说明摘要的完整文本（R74②：原第四列的常驻说明改 tooltip，这里就是那份 tooltip）。
     * <p>
     * 只把<b>已经在别处有权威</b>的句子拼在一起（成本常量、模式行、ghost 用法、
     * "每格声明吃掉一格真实容量"），不新造第二条口径。
     */
    String notesText() {
        final StringBuilder builder = new StringBuilder();
        builder.append(StatCollector.translateToLocal("gtit.pocket.note.title"))
            .append('\n');
        builder.append(StatCollector.translateToLocal("gtit.pocket.note.summary"))
            .append('\n');
        builder
            .append(
                String.format(
                    StatCollector.translateToLocal("gtit.pocket.note.cost"),
                    PocketConstants.BURST_COST_NEKO,
                    PocketConstants.SHORT_COST_SHIMMERING_NEKO,
                    PocketConstants.SHORT_CHANNEL_SECONDS))
            .append('\n');
        builder.append(StatCollector.translateToLocal("gtit.pocket.ghost.capacity_note"));
        return builder.toString();
    }

    /**
     * 交互格的 tooltip 补行：说出"这一格属于第几列、那一列现在存的是什么"。
     * <p>
     * 列号 = {@link PocketInventory#tankOfInteractionSlot(int)} 的单源映射（客户端只读同步过来的
     * 流体状态，不自行推断别的口径）。这里<b>不</b>报容量数字：容量已经由
     * {@code gtit.pocket.fluid.capacity} 在列上常驻显示一次，两处都写就是两处真相。
     */
    String tankHintText(int interactionIndex) {
        return String.format(
            StatCollector.translateToLocal("gtit.pocket.legend.tank_of"),
            PocketInventory.tankOfInteractionSlot(interactionIndex) + 1);
    }

    /** 模式行文本（R39b：不允许玩家自己猜）。 */
    String modeText() {
        return pullMode ? String.format(StatCollector.translateToLocal("gtit.pocket.mode.pull"), filterCount)
            : StatCollector.translateToLocal("gtit.pocket.mode.push");
    }

    /**
     * 冷却/剩余行文本（两条互斥：短效在跑显示剩余，否则显示瞬时冷却），
     * 并把<b>最近一次动作的回执</b>顶在前面（R10/R39b：失败原因必须玩家看得见）。
     * <p>
     * 回执是粘性的（下一次动作覆盖），所以"分区拒收"不会一闪就没；
     * 72px 装不下完整文本时走 {@code NekoPocketLeftColumn#statusLines} 的 tooltip（R36：不删信息）。
     */
    String channelStatusText() {
        final String receipt = receiptText();
        final String status;
        if (timedRemainSeconds > 0) {
            status = String
                .format(StatCollector.translateToLocal("gtit.pocket.channel.timed.remain"), timedRemainSeconds);
        } else if (instantRemainSeconds > 0) {
            status = String
                .format(StatCollector.translateToLocal("gtit.pocket.channel.instant.remain"), instantRemainSeconds);
        } else {
            status = "";
        }
        if (receipt.isEmpty()) {
            return status;
        }
        return status.isEmpty() ? receipt : receipt + " " + status;
    }

    /** 状态行的 tooltip（同一条文本的完整版，72px 截断时不丢信息，R36）。 */
    String statusHintText() {
        final String receipt = receiptText();
        final String mode = modeText();
        final String status = channelStatusText();
        final StringBuilder builder = new StringBuilder();
        if (!mode.isEmpty()) {
            builder.append(mode);
        }
        if (!status.isEmpty()) {
            if (builder.length() > 0) {
                builder.append('\n');
            }
            builder.append(status);
        }
        if (!receipt.isEmpty() && !status.contains(receipt)) {
            if (builder.length() > 0) {
                builder.append('\n');
            }
            builder.append(receipt);
        }
        return builder.append('\n')
            .append(StatCollector.translateToLocal("gtit.pocket.held.note"))
            .toString();
    }

    /** 进度条的同步值（{@code ProgressWidget.value}）。 */
    DoubleSyncValue distillProgressValue() {
        return new DoubleSyncValue(this::serverDistillProgress, value -> {
            if (syncManager.isClient()) {
                clientDistillProgress = value;
            }
        });
    }
}

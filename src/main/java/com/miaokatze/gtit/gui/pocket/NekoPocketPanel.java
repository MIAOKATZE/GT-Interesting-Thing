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
import com.cleanroommc.modularui.widgets.slot.SlotGroup;
import com.miaokatze.gtit.common.items.pocket.ItemNekoDimensionPocket;
import com.miaokatze.gtit.common.items.pocket.PocketAeChannelOps;
import com.miaokatze.gtit.common.items.pocket.PocketBindFlow;
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
 * 猫猫次元口袋主面板（<b>398×360</b>，★R81④ 由 416 收到与主区实占同宽；<b>★R78① 起带玩家背包</b> =
 * 底部带中间段 9×4）。
 * <p>
 * <b>R32（本任务最高风险）的结构性处置</b>：{@link #assemble()} 是<b>一条线性装配</b>——双端同调用点、
 * 同一批列类、同一顺序 add，没有任何远程分支改变子节点的顺序或数量（同步键全部显式命名）。
 * 槽位布局由各列类的 {@code SlotGroupWidget.matrix(String...)} 字面量描述（R41a）⇒
 * 「双端 widget 树失序」不是靠纪律避免，而是结构上不可能发生。
 * ★R78③ 的"格序按首次入账顺序"确实是一次<b>数据驱动的显示变化</b>，它的落点被严格限制成
 * "恒定 72 个 widget 的内容层原位换图标"（{@link NekoEssenceGhostCell#setCellContent(String, int)}），
 * 映射由服务端算、随现有源质 blob 同步 ⇒ 两端各算各的这件事在结构上就不成立。
 * <p>
 * <b>宽度闭合（★R81④ 定稿，逐字照加总表，无富余）</b>：
 * {@code 6 + 108(流体 6 列) + 4 + 162(中栏 9 列) + 4 + 108(源质 6 列) + 6 = 398 = 面板宽} ⇒
 * 三列 root 固定 {@code x/宽 = 6/108 · 118/162 · 284/108}，不得再给任何列加宽
 * （R75 那版是 10 列中栏 ⇒ 实占与面板宽都更大，中栏收窄到 9 列后<b>面板宽跟着收</b>，
 * 见 {@link #WIDTH} 的 javadoc：右侧不留无主空白）。
 * <b>高度闭合</b>：{@code 6 + 270(15 行) + 6 + 72(底部带) + 6 = 360}，而 360 正是
 * <b>1080p / GUI Scale 3 的逻辑高度上限</b>（R75"为什么是 15 行"的全部理由）⇒
 * <b>任何加高方案都必须先重算这条账</b>，纵向已经没有一格余量。R78① 要的背包因此
 * <b>只能</b>横向挤（底部带拆三段），中栏 15 行一行不删。
 * <p>
 * <b>Container 口径 = 恰好 {@value PocketSlots#TOTAL_REAL_SLOTS}</b>（R78，覆盖 R75 的 175、
 * R74 的 185、§14.3/R43b 的 149）：中栏 <b>135</b> + 流体交互 <b>36</b>（3 组 × 6 列 × 进/出）
 * + 蒸馏 12 + 绑定 1 + <b>玩家背包 36</b>；72 源质格、18 个流体槽本体与全部 ghost 走显示侧，
 * <b>不进 Container</b>（R35）。
 * <p>
 * <b>★玩家背包那 36 格（R78① 撤销 R69-D2，代价 = E4 包放大风险回归，不得静默）</b>：
 * 旧实现预注册一个<b>空</b> {@code PlayerSlotGroup}，让框架的
 * {@code ModularSyncManager#construct} 跳过它默认的 36 格绑定（{@code ISyncRegistrar#bindPlayerInventory}
 * 的"已注册即跳过"分支）⇒ 面板不显示背包、Container 恰好等于本工厂产出。R78① 按用户裁决
 * <b>撤销</b>那一招：背包真实显示、可交互、可 shift。
 * 换回来的代价（README 第 5 条与本处同点名）：
 * <ol>
 * <li><b>首开同步 36 格</b>；</li>
 * <li>vanilla {@code Container#detectAndSendChanges} <b>每 tick</b> 对这 36 格做 {@code ItemStack}
 * 相等比较（<b>含整份 NBT 深比较</b>，见 {@code ItemStack#isItemStackEqual}），内容一变就把整枚
 * 口袋连同 135 格一起重发 —— 这正是 R53c 点名的包放大面（E4）。</li>
 * </ol>
 * 这条代价是<b>用户为"要玩家背包"明确换回来的</b>，不是实现走形；若日后要收回背包，
 * 收回的就是这两条。
 * <p>
 * <b>关屏写状态</b>：{@link NekoPocketContainer#onModularContainerClosed()}（R35，NEI 顶屏不误触发）。
 * <p>
 * <b>绑定信息的可见面（R74②/R75/R78 D-2）</b>：全部绑定条目由<b>绑定按钮的 tooltip</b>
 * 承载（{@link #bindTooltipText()}，超出 {@code NekoPocketBottomBand.TOOLTIP_ROWS} 条时
 * <b>显式</b>提示还有几条没列），解绑入口改为该按钮的<b>右键 = 解绑最后一条</b> /
 * <b>Shift 右键 = 清空全部</b>（{@link #dispatchBindButtonClick(int)}）。
 * 左栏那 7 段常驻说明同样撤进 tooltip（{@link #notesText()} 与其两个可见入口：
 * 左栏末行的 tooltip 与底部带右段的帮助按钮）。
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
    /**
     * R75：三列实占 {@code 6+108+4+180+4+108+6}；R80 中栏收到 9 列后同一式子给
     * {@code 6+108+4+162+4+108+6 =} <b>398</b>（★派生自各列常量，不手抄）。
     */
    public static final int MAIN_OCCUPIED_WIDTH = MARGIN + NekoPocketLeftColumn.WIDTH
        + COLUMN_GAP
        + NekoPocketStorageColumn.WIDTH
        + COLUMN_GAP
        + NekoPocketEssenceColumn.WIDTH
        + MARGIN;
    /**
     * ★<b>R81④：面板宽 = 主区实占 = 398 —— 上一版保留的 416 与那 18px 无主空白一起收掉</b>。
     * <p>
     * R80① 当时把面板宽留在 416，并给"主区右沿与面板右沿之间"那 18px 取了个具名叫
     * "让位量"常量（★该具名常量已随本次裁定<b>整体删除</b>，不是置 0，也不是改个数）。
     * 留下它的后果是：
     * 源质列右沿（392）到面板右沿（410）之间<b>没有任何 widget</b>，装饰层的布纹/木框照样画到 416 ⇒
     * 玩家读到一条贴在右侧的空带。本仓纪律是<b>不许留无主空白</b>，而把这 18px 拿去塞新内容
     * 属于"发明功能"，不是设计 ⇒ 唯一正确的收法是让 {@code WIDTH} 等于实占。
     * <p>
     * 连带账（★三段之和与横向闭合都在 {@link NekoPocketBottomBand} 的装配期断言里）：
     * {@code 6 + 112(左) + 162(背包) + 112(右) + 6 = 398}，右段由 130 收到 112
     * （= {@code 6 × 18 + 4}：一格绳缝 + 六格栅格）。背包段仍与中栏<b>同 x 同宽（118..280）</b>。
     * 纵向一格未动（{@code 6+270+6+72+6 = 360} = 1080p / GUI Scale 3 的逻辑高度上限）。
     */
    public static final int WIDTH = MAIN_OCCUPIED_WIDTH;
    /** R75：面板高 = 6+270+6+72+6 = 360（★360 = 1080p / GUI Scale 3 的逻辑高度上限）。 */
    public static final int HEIGHT = MARGIN + NekoPocketStorageColumn.HEIGHT + MARGIN + BAND_HEIGHT + MARGIN;

    static {
        // ★R81④ 判据：面板宽必须<b>逐字等于</b>"外边距 + 三列 + 两个列间距 + 外边距"这条加算式。
        // 写成字面量而不是只看派生式，是因为派生式在"某一列又改了宽"时会跟着漂而永不红；
        // 这里的字面量一红，就是在要求改动方回来看 R80/R81 那两张加总表（不留无主空白的同一纪律）。
        final int sum = 6 + 108 + 4 + 162 + 4 + 108 + 6;
        if (WIDTH != MAIN_OCCUPIED_WIDTH || WIDTH != sum) {
            throw new IllegalStateException(
                "[pocket] 面板宽与主区实占分叉: WIDTH=" + WIDTH
                    + " 实占="
                    + MAIN_OCCUPIED_WIDTH
                    + " 加算式="
                    + sum
                    + "（★R81④ 定稿 398 = 6+108+4+162+4+108+6，面板不留无主空白）");
        }
        if (HEIGHT != 360) {
            throw new IllegalStateException("[pocket] 面板高不等于 360（GUI Scale 3 逻辑高度上限）: " + HEIGHT);
        }
    }

    // ------------------------------------------------------------------ 同步键
    // S2C：格数与行数恒定 ⇒ 键数恒定，不随 addon / 绑定数漂移
    private static final String SYNC_ESSENCE = "pocket.essence.points";
    private static final String SYNC_BIND_ROWS = "pocket.bind.rows";
    private static final String SYNC_MODE = "pocket.mode";
    private static final String SYNC_REMAIN = "pocket.remain";
    static final String SYNC_PROGRESS = "pocket.distill.progress";
    /** S2C：ghost 声明视图（{@code kind:slotIndex:载荷键}，';' 分隔）⇒ 客户端据此<b>原位</b>虚化格子。 */
    private static final String SYNC_GHOST = "pocket.ghost.slots";
    /** S2C：上一次通道/绑定动作的回执（{@code langKey|数量}），客户端只做本地化格式化。 */
    private static final String SYNC_RECEIPT = "pocket.receipt";
    /** C2S：ghost 就地转换请求（{@code SET|slot|载荷键} / {@code CLR|slot|区域字母}，文法见 {@code PocketGhostRequest}）。 */
    private static final String SYNC_GHOST_REQUEST = "pocket.ghost.request";
    /** C2S：所有按钮/选中动作走这一个键，值 = {@code code * ACTION_ARG_BASE + arg}（单包原子，无两值竞态）。 */
    private static final String SYNC_ACTION = "pocket.action";
    /** 动作参数基数（当前最大 arg = 134 格 / 71 格+Shift 位）。 */
    private static final int ACTION_ARG_BASE = 1024;

    /**
     * 源质 blob 的<b>段</b>间分隔符（{@code ';'}，与绑定行/ghost blob 同字符但只在源质那一根通道上用）。
     * ★不能与 {@link #BLOB_CELL_SEPARATOR} 同字符，否则"无主格的空串"会把段切歪。
     */
    private static final char BLOB_SECTION_SEPARATOR = ';';
    /** 源质 blob 的<b>格</b>间分隔符（{@code ','}）。 */
    private static final String BLOB_CELL_SEPARATOR = ",";
    /** {@link #BLOB_CELL_SEPARATOR} 的正则形态（{@code String.split} 用；逗号不是元字符，无需转义）。 */
    private static final String BLOB_CELL_SEPARATOR_REGEX = ",";

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
     * 中栏 {@code STORAGE_SLOTS} 个槽位 widget 的双端登记表（下标 = 槽号）。
     * <p>
     * ghost 是"原位改属性"（R41b/R46d），所以必须能按槽号找到<b>那一个</b> widget 实例；
     * 装配时由 {@link NekoPocketStorageColumn} 逐格登记，长度与格序恒定 ⇒ 不引入任何数据驱动的树变化。
     */
    private final NekoFilterSlot[] itemSlots = new NekoFilterSlot[PocketInventory.STORAGE_SLOTS];

    /**
     * 18 个流体槽与 72 源质格的登记表（与 {@link #itemSlots} 同一机制，S-E 补的两个拖入入口）。
     * <p>
     * 长度恒定（{@code GHOST_FLUID_SLOT_LIMIT}=18 与 {@code GHOST_ESSENCE_SLOT_LIMIT}=72）、装配序固定
     * ⇒ ghost 声明与"格位归属换了 tag"再多也不会改变 widget 树（R41b/R32）；{@link #applyGhosts()} 按
     * {@code (kind, slot)} 取到那一个实例原位切属性，{@link #applyEssenceBlob(String)} 按同一套下标
     * 原位换内容层。R75① 之前这里只有一个 {@code fluidBar} 字段（"流体侧只有一格"的特例），
     * 现在数组下标 = tank 号 = 该流体列的 ghost 槽号（组内列号由 {@code PocketInventory} 单源映射）。
     */
    private final NekoPocketFluidSlot[] fluidSlots = new NekoPocketFluidSlot[PocketConstants.GHOST_FLUID_SLOT_LIMIT];
    private final NekoEssenceGhostCell[] essenceCells = new NekoEssenceGhostCell[PocketConstants.GHOST_ESSENCE_SLOT_LIMIT];

    // ---- 客户端显示缓存（S2C 写入；服务端不读）----
    //
    // ★R78③：这两张表的下标都是<b>格号</b>（0…71），不再是 aspectOrder() 的位置。
    // 服务端权威值住在 PocketEssenceStore 的格位归属表里（并落 NBT essCellOrder），
    // 客户端这两张表只是它的<b>同步镜像</b>——两端不各算各的格序，这是 R32 的头号风险的落点。
    private final int[] essenceCache = new int[PocketConstants.ESSENCE_DISPLAY_GRID];
    private final String[] essenceCellTags = new String[PocketConstants.ESSENCE_DISPLAY_GRID];
    /**
     * 有货但没有格位的 tag 数（★同步 blob 的第三段；驱动 {@code aspect.overflow_note}）。
     * <p>
     * 必须是服务端算出来的读数而不是客户端"注册数 &gt; 格数"的推断：格位归属是数据驱动的，
     * 客户端无从知道服务端一共见过几个 tag（R19/R39b 的"客户端不得推断"口径）。
     */
    private int essenceUnplaced;
    private String bindRowsBlob = "";
    private boolean pullMode;
    private int filterCount;
    private int instantRemainSeconds;
    private int timedRemainSeconds;
    private double clientDistillProgress;
    /** 服务端下发的 ghost 声明 blob（客户端据此原位虚化；<b>不</b>自行推断，R39b/R19）。 */
    private String ghostBlob = "";
    /**
     * ★R85 N3：最近一次 blob 里<b>真正解析成功</b>的声明条数（客户端专用；服务端恒 0，但服务端不出图）。
     * 存在的唯一理由是 {@link #ghostNotSyncedCount()} 需要它当减数——blob 被长度预算截断时，
     * "客户端看到的条数"会小于"服务端声明的条数"，差额必须说出来（不许静默少画）。
     */
    private int ghostSyncedCount;
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
        // ★R78③：把"格位归属 + 现有点数"当作两份镜像的<b>起点</b>。双端读的都是同一份口袋 NBT
        // （客户端那一份是 vanilla 同步过来的物品 tag），所以起点天然一致；之后的每一次变化
        // 都由服务端 composeEssenceBlob 覆盖客户端那份 ⇒ 不存在"两端各算各的格序"。
        for (int cell = 0; cell < essenceCellTags.length; cell++) {
            final String tag = inventory.essence()
                .tagAtCell(cell);
            essenceCellTags[cell] = tag;
            essenceCache[cell] = tag == null ? 0
                : inventory.essence()
                    .get(tag);
        }
        essenceUnplaced = inventory.essence()
            .unplacedTagCount();
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
        // allowShiftTransfer 对<b>本仓四组</b>仍为 false：shift 的落点是玩家背包那一组，
        // 而那一组由框架自己注册（rowSize 9、allowShiftTransfer=true）。
        // ★R83：中栏这一组第 4 参开成 true = 玩家背包↔中栏的 shift 收存（需求 5「箱子属性」里唯一能由
        // 开关给到的那半；中键/R 键整理是自家手势，不是通用箱子整理）。蒸馏/流体/绑定三组仍关 ——
        // 四组全开会出现"同一次 shift 被两组各抢一次"的分叉。
        // ghost 格不因此被灌：放置判据已钉在服务端 handler 的 isItemValid 上（R83 B1）。
        syncManager
            .registerSlotGroup(new SlotGroup(PocketSlots.GROUP_STORAGE, NekoPocketStorageColumn.COLUMNS, 100, true));
        syncManager.registerSlotGroup(new SlotGroup(PocketSlots.GROUP_FLUID, PocketSlots.FLUID_COLUMNS, 100, false));
        syncManager.registerSlotGroup(
            new SlotGroup(PocketSlots.GROUP_DISTILL, NekoPocketEssenceColumn.DISTILL_COLUMNS, 100, false));
        syncManager.registerSlotGroup(new SlotGroup(PocketSlots.GROUP_BIND, 1, 100, false));
        // ★R78① 撤销 R69-D2：<b>不再</b>预注册空 PlayerSlotGroup。
        // 旧那一行（syncManager.registerSlotGroup(new PlayerSlotGroup(PlayerSlotGroup.NAME))）的作用是
        // 让 ModularSyncManager#construct 的"已注册即跳过"分支生效，从而不绑那 36 格背包。
        // 撤掉之后框架自己会注册 player_inventory 组与 36 个 handler（键 "player:0"…"player:35"），
        // 由 NekoPocketBottomBand 的中间段画出来。连带代价 = E4 包放大风险回归，
        // 逐字写在类 javadoc 与 README 第 5 条（用户为"要背包"明确换回来的）。

        // 2) 同步值（显式键，双端同一顺序注册）
        registerSyncValues();

        // 2b) 装饰层（C2）：★必须是第一个 child（MUI2 按 child 顺序绘制 ⇒ 底材在最下）；
        // 位置在槽组/同步值之后、三列之前，且双端同一顺序（R32）
        panel.child(NekoPocketDecoration.build());

        // 3) 三列 + 底部带三段（R74② 删掉第四列；R78① 底部带横向拆成"币值｜背包｜绑定"三段；
        // 顺序固定 = 双端同树）
        panel.child(NekoPocketLeftColumn.build(this));
        panel.child(NekoPocketStorageColumn.build(this));
        panel.child(NekoPocketEssenceColumn.build(this));
        for (ParentWidget<?> band : NekoPocketBottomBand.build(this, this::cellInfoLine)) {
            panel.child(band);
        }

        // 4) 槽数口径断言（R80：工厂产出 184 + 框架背包 36 = 220；多 = 重复接入，少 = 漏接，双端同抛）
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
        // ★R85 N1：allowC2S 的 setter <b>双端都会被调</b>（上游 setValue(v) 默认 setSource=true）⇒
        // 两个 receiver 各自带一道 isClient 早退，见 receiveServerAction / receiveGhostRequest 的 javadoc。
        syncManager.syncValue(SYNC_ACTION, new IntSyncValue(() -> 0, this::receiveServerAction).allowC2S());
        // ghost 拖入/解绑的载荷是一串键（itemId+meta+base64NBT 可以很长），装不进上面那个 int 通道，
        // 因此单开一根字符串 C2S；**执行体在服务端**（R18/R19），客户端那份 setter 由入口守卫挡掉
        // （★旧注释"从不被调用"是错的：setValue 的 setSource 默认 true ⇒ 客户端确实会被调一次）。
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
     * <p>
     * ★<b>R85 N1：本方法是 {@code SYNC_ACTION} 的 setter，而 setter 双端都会被调</b>——上游
     * {@code IValueSyncHandler.setValue(T)} 的默认重载写死 {@code setSource = true}
     * （{@code value/sync/IValueSyncHandler.java:21-24}），{@code IntSyncValue.setDoubleValue/setSource}
     * 一路把那个 true 传到 setter ⇒ <b>客户端</b>调 {@code setValue(v)}（{@code sendAction} 那一跳）时
     * 本地就先把本方法跑了一遍。守卫必须落在<b>这里</b>（入口）而不是队列消费端，理由有两条：
     * <ol>
     * <li>消费端在 {@code ServerTickEvent} 上跑，天然只在服务端 ⇒ 客户端那颗 lambda 根本不会被"消费"，
     * 它只是<b>永久滞留在 static 队列里</b>并连着捕获本面板（→ {@code PocketInventory} 与物品栈副本），
     * 每点一次泄漏两个、关屏也清不掉 ⇒ 专用服客户端无界涨；</li>
     * <li>同一根通道还要把服务端的回显（getter 恒 0）打回客户端，那一跳同样进本方法 ⇒
     * 在入口一次挡住<b>两跳</b>，比逐处改 {@code setValue(v, false, true)} 稳（后者只挡得住自己那一跳）。</li>
     * </ol>
     * 守卫<b>不改变已投递路径的服务端语义</b>：服务端分支一个字节都不动，仍然 ≤1 tick 后在
     * {@code ServerTaskScheduler} 里跑 {@link #onServerAction(int)}。
     */
    private void receiveServerAction(int packed) {
        if (syncManager.isClient()) {
            // ★R85 N1：客户端本地回投（setter 的 setSource 默认 true）⇒ 一个字节都不做，
            // 免得往"只在 ServerTickEvent 消费"的 static 队列里投捕获面板的 lambda（专用服必泄漏）。
            return;
        }
        ServerTaskScheduler.scheduleServerTask(() -> onServerAction(packed));
    }

    /**
     * 见 {@link #receiveServerAction(int)} 的同一条线程前提，以及★同一条 <b>R85 N1 客户端守卫</b>：
     * {@code SYNC_GHOST_REQUEST} 也是 {@code allowC2S()} 的值，客户端 {@code setValue(request)} 会就地
     * 跑本方法 ⇒ 不守卫就是"每次拖入/右键/滚轮都往服务端队列塞一颗永不消费的 lambda"。
     * 载荷（String）本身已被本方法丢弃，服务端那一跳仍按原样执行 {@link #onServerGhostRequest(String)}。
     */
    private void receiveGhostRequest(String request) {
        if (syncManager.isClient()) {
            return;
        }
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

    /**
     * 派生出的 aspect 序（双端同 mod 集 ⇒ 同序）。
     * <p>
     * ★R78③ 之后这<b>不再是格序</b>：格序改由服务端算的格位归属表决定（见
     * {@link #essenceTagAtCell(int)}）。本序只剩一个用处——{@code TaumCompat} 那边的
     * 注册数日志与"这个包一共认识几个 aspect"的参考，GUI 不得再拿它排格子。
     */
    String[] essenceOrder() {
        return TaumCompat.aspectOrder();
    }

    // ------------------------------------------------------------------ C2S 请求（客户端只发码）

    /** Shift + 左键点中栏 = 语义②「口袋 → 玩家背包」。 */
    boolean requestTakeOut() {
        return sendAction(ACTION_TAKE_OUT, 0);
    }

    /** 语义①「整理中栏 135 格」。 */
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
     * （★R86：左键＝把该组拿到游标上，Shift＝该格整份进背包；旧口径"Shift 取满一整堆"已作废）。
     * 走 {@code SYNC_ACTION} 单通道 ⇒ 客户端只发码，
     * 扣点/物化/落点判定全在服务端 {@link #performEssenceOut}（R18/R19）。
     */
    boolean requestEssenceOut(int cell, String tag, boolean shift) {
        // ★R83：不再在客户端拿 tag 判空后静默早退 —— 服务端 performEssenceOut 本就会按格号反查归属并
        // 给"无事发生"回执（R18/R19：客户端字符串一律不可信）。两处各判一次就是两处真相，且客户端这
        // 一处连回执都不发，玩家看到的是"点了一下，什么都没发生"。
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

    /** 语义①「整理中栏 135 格」：合并同类 + 前移紧凑。 */
    private void performSort() {
        final int size = inventory.storage()
            .getSlots();
        final ItemStack[] snapshot = new ItemStack[size];
        for (int index = 0; index < size; index++) {
            // ★R85 A5：声明格<b>整格不参与整理</b>——它现在就是那条需求的落点（R84），里面的东西是"已经补到的
            // 产物"。旧写法把 135 格全扫进合并池、回写时又跳过 ghost 格 ⇒ 玩家点一次"整理"就把产物搬去别处、
            // 需求格重新变空，而且下面那条"非 ghost 槽数 ≥ 条目数"的不变量在声明格带货时根本不成立
            // （溢出走 giveToPlayer ⇒ 会把物品喷到脚下）。产物留在原格，其余照旧合并前移。
            if (inventory.isGhostItemSlot(index)) {
                continue;
            }
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
        // ★ghost 格不参与整理，也<b>不是</b>落点池（R85 A5 起更进一步：连快照都不进，产物原地不动）：
        // 判据读服务端的 PocketFilterConfig，不读 widget 状态（widget 双端各一份，把显示层当真相即 bug）。
        // 因为声明格的货不再进合并池，"非 ghost 槽数 ≥ 池内条目数"这条不变量重新成立；
        // 仍保留兜底：万一存量档里 ghost 槽带着东西进来、或池内条目确实多于非 ghost 槽，走玩家背包而不是 break 掉（整理绝不吃件）。
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
        return inventory.nextSortableStorageSlot(from);
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
            // ★R85 小项 4：这里<b>不</b>再把秒数当实参传下去（旧写法传了，而两份 lang 的
            // {@code gtit.pocket.receipt.cooldown} 里根本没有占位 ⇒ 那个数字算出来、下发到客户端、
            // 永远不显示，是一条纯粹的假读数）。
            // 选"删实参"而不是"文案加 %d"的理由是口径唯一性：本行的回执是<b>粘性</b>的（一直显示到
            // 下一次动作覆盖），而冷却秒数已经有一条每拍刷新的权威显示面（{@link #composeRemain()} →
            // {@code applyRemainState} → 状态行的 {@code channel.instant.remain}）。把秒数塞进粘性回执
            // 等于给同一个数造第二个读数，而且第二个会<b>冻在点击那一刻</b>（"还剩 9 秒"挂满 10 秒），
            // 与旁边那条倒计时互相打脸 ⇒ 冷却"有"这件事走本行文案，冷却"还剩几秒"只走状态行那一处。
            putReceipt("gtit.pocket.receipt.cooldown", 0);
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
            if (report.noChannel > 0) {
                // ★R85 A3：R84 把"没有该通道"从 LOST 改判成 NO_CHANNEL 后，这个计数全仓零读者 ⇒ 玩家只会
                // 看到"通道开了却没动静"。它不停批（同批别的声明还能跑），但必须说出来。
                return "gtit.pocket.receipt.no_channel";
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
     * <p>
     * ★★<b>R81①②：「绑定只能绑定一个」的修复点就在这一小段</b>。旧实现把
     * {@code PocketCellProbe.cellUuid()} 的 {@code null} 当成一件事读（"格子里没放元件"），
     * 而 {@code diskuuid} 是<b>服务端首次取用时惰性分配</b>的 ⇒ 一枚从未进过驱动器的新元件
     * 身份读出来也是 {@code null}，于是被发回一句「把元件放入此格」（格子里明明躺着元件）——
     * 反向误导，玩家换个位置再试还是失败。现在：
     * <ol>
     * <li>四态判定与 {@code bind()} 的<b>返回值</b>（旧代码丢掉的就是它）一起收在
     * {@link PocketBindFlow#bind} 里，每种结果各回一条自己的 lang 回执（含"同身份重绑"，
     * 旧口径完全静默）；</li>
     * <li>身份为空时由 {@link PocketCellProbe#bindIdentity} 在<b>服务端</b>调一次现成公开 API
     * {@code StorageManager.getStorage(ItemStack)} 就地物化身份后<b>重读</b>（★不改
     * {@code allocateOrReadUuid} 的可见性、不新增分配逻辑、客户端一个字节都不写）；</li>
     * <li>只有真的写进表（{@code ADDED} / {@code DUPLICATE} 刷新了位置快照）才 {@code markDirty}
     * 并把元件退回玩家；表满或无身份时<b>原栈留在格内</b>由玩家自己拿走（R40a 的"绝不吃元件"）。</li>
     * </ol>
     */
    private void performBind() {
        if (!serverGuardOk()) {
            return;
        }
        // serverGuardOk() 已经挡掉客户端（第一句就是 isClient），这里再取一次显式值，
        // 好让"物化只在服务端"这一跳在代码上读得出来，而不是靠调用点的注释。
        final boolean server = !syncManager.isClient();
        final ItemStack candidate = inventory.bindSlot()
            .getStackInSlot(0);
        final PocketBindFlow.Result result = PocketBindFlow.bind(
            inventory.bindings(),
            PocketCellProbe.isCell(candidate),
            PocketCellProbe.bindIdentity(candidate, server),
            server);
        putReceipt(
            PocketBindFlow.langKeyOf(result),
            inventory.bindings()
                .size());
        if (result != PocketBindFlow.Result.ADDED && result != PocketBindFlow.Result.DUPLICATE) {
            // 没写进表（非元件 / 无身份 / 表满）：什么都不动，元件留在绑定格里由玩家自己拿走
            return;
        }
        inventory.markDirty();
        returnToPlayerFromBindSlot(candidate);
        // ★R81①：成功也发回执（bind.added）。旧口径"读数变了即是反馈"在缺陷现场不成立——
        // 玩家就是因为看不出条数有没有在涨才报"只能绑一个"的，每一次新增都必须能读到一个词。
        // 同身份重绑走 bind.dup（旧代码丢掉 bind() 返回值 ⇒ 完全静默，R81 点名的次因）。
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
     * ★<b>R86 改口径</b>（实机 6 条之 1，向玩家对物品槽的信念对齐）：<b>左键＝把「该组」拿到鼠标游标上</b>
     * （一组至多 {@link PocketConstants#ESSENCE_OUT_MAX_POINTS_PER_ACTION} 点，游标上已有东西则整笔不动
     * 并给回执）；<b>Shift+左键＝该格整份一次进背包</b>（至多单格上限，背包优先、余量落中栏）。
     * 旧口径是"左键 1 点到背包、Shift 64 点到背包"，两边都往背包塞，玩家拿不到手上那一叠。
     * <p>
     * 换算 1 点 = 1 晶、1 晶 = 1 件（{@code TaumBridge.CRYSTAL_STACK_LIMIT}）。<b>先扣点、后物化、
     * 放不下就退点</b>：三步任何一步失败都不会凭空造晶，也不会把点数值吞掉（TC 缺席 ⇒
     * {@code newCrystalStack} 返回 null ⇒ 点数原样退回）。
     * <p>
     * ★R78③：arg 是<b>格号</b>，tag 由服务端的格位归属表（{@code PocketEssenceStore#tagAtCell}）
     * 反查——<b>不吃</b>客户端可能送来的 tag（R18/R19：客户端字符串一律不可信），也不再是
     * {@code aspectOrder()[格号]} 那个固定派生序。空格位（该格从未被占过）一律不动并给"无事发生"回执。
     */
    private void performEssenceOut(int packedArg) {
        if (!serverGuardOk()) {
            return;
        }
        final boolean shift = packedArg >= PocketConstants.ESSENCE_OUT_SHIFT_FLAG;
        final int cell = shift ? packedArg - PocketConstants.ESSENCE_OUT_SHIFT_FLAG : packedArg;
        final PocketEssenceStore store = inventory.essence();
        final String tag = store.tagAtCell(cell);
        if (tag == null) {
            // 该格没有归属（从未入过账）⇒ 没东西可取；给一条可读回执而不是静默（R10）
            putReceipt("gtit.pocket.still.idle", 0);
            return;
        }
        if (!shift && syncManager.getCursorItem() != null) {
            // ★R86：游标已被占用 ⇒ vanilla 也是"什么都不发生"，但这里必须说话，否则又回到"点了一下没动静"
            putReceipt("gtit.pocket.essence.cursor_busy", 0);
            return;
        }
        final int stock = store.get(tag);
        final int wanted = shift ? stock : Math.min(stock, PocketConstants.ESSENCE_OUT_MAX_POINTS_PER_ACTION);
        final int points = store.extract(tag, wanted);
        if (points <= 0) {
            putReceipt("gtit.pocket.still.idle", 0);
            return;
        }
        final int moved = shift ? depositCrystals(tag, points) : handCrystalsToCursor(tag, points);
        if (moved < points) {
            // 物化失败（TC 缺席/该 tag 不可物化）或落点装不下：点数退回原格，绝不销毁价值
            store.add(tag, points - moved);
        }
        inventory.markDirty();
        putReceipt(moved > 0 ? "gtit.pocket.receipt.ok" : "gtit.pocket.receipt.target_full", moved);
    }

    /**
     * ★R86 左键支：把刚物化出的那一堆晶放上游标（{@code PanelSyncManager#setCursorItem}，
     * 上游实现即 {@code player.inventory.setItemStack} + {@code CursorSlotSyncHandler#sync}，
     * 故零新 C2S 键）。入参 {@code points} 已由调用方收在一堆之内，不再切块。
     */
    private int handCrystalsToCursor(String tag, int points) {
        final ItemStack crystals = TaumCompat.newCrystalStack(tag, points);
        if (crystals == null || crystals.stackSize <= 0) {
            return 0;
        }
        syncManager.setCursorItem(crystals);
        return crystals.stackSize;
    }

    /**
     * ★R86 Shift 支：该格整份一次进背包，按单堆上限切块循环。
     * <p>
     * ★切块是<b>必需</b>的而非省事：单堆晶化源质上限 64，而一格至多 256 点 ⇒ 一次物化 256 件会造出
     * 超堆叠的栈（{@code PocketConstants#FILTER_CAP_CEILING_ESSENCE} 那条 javadoc 记录的"净吞 192 点"
     * 静默销毁就是同一个坑的另一面）。循环<b>只往前走</b>：每圈 {@code rest} 至少减掉一整块，且
     * 落点一件都收不下时立刻 break ⇒ 不会重演 R83 那种"进度恒 0 ⇒ 服务器主线程死循环"（R84 已证死）。
     */
    private int depositCrystals(String tag, int points) {
        int moved = 0;
        for (int rest = points; rest > 0;) {
            final int chunk = Math.min(rest, PocketConstants.ESSENCE_OUT_MAX_POINTS_PER_ACTION);
            final ItemStack crystals = TaumCompat.newCrystalStack(tag, chunk);
            if (crystals == null) {
                break;
            }
            final int got = depositToPlayerFirst(crystals);
            if (got <= 0) {
                break;
            }
            moved += got;
            rest -= chunk;
        }
        return moved;
    }

    // ------------------------------------------------------------------ S5 · ghost 就地转换（NEI 拖入 / 右键解绑）

    /**
     * 中栏槽位 widget 的登记（装配期由 {@link NekoPocketStorageColumn} 逐格调用，双端各 135 次）。
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

    /** 源质格的登记（装配期逐格调，双端各 72 次；下标 = {@code Kind.ESSENCE} 的<b>格号</b>，R78③）。 */
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
        final PocketFilterConfig parsed = parseGhostBlob(blob);
        // ★R85 N3：换镜像<b>之前</b>先记下"这一份 blob 里到底解析到了几条"。它是 ghost.not_synced 读数的
        // 减数一侧；被数出来的必须是<b>解析成功</b>的条数（不是 split 的段数），否则一条坏记录也会被算成
        // "已同步"，读数反而会吞掉真正的差额。
        ghostSyncedCount = parsed.size();
        inventory.replaceFilters(parsed);
        applyGhosts();
    }

    /**
     * ★<b>R85 N3</b>：尾部<b>没有同步到客户端显示</b>的声明条数（只影响虚化渲染，<b>不</b>影响服务端执法）。
     * <p>
     * <b>读数为什么是"权威总数 − 本端解析到的条数"</b>：
     * <ul>
     * <li>权威总数走<b>已有</b>的 {@code SYNC_MODE}（{@link #composeModeState()} 把服务端
     * {@code inventory.filters().size()} 作为第二列发给客户端，落在 {@link #filterCount}）
     * ⇒ ★不需要新增同步键；</li>
     * <li>减数是 {@link #applyGhostView} 现记的 {@link #ghostSyncedCount}。这里刻意<b>不</b>用
     * {@code inventory.filters().size()} 当减数或被减数：客户端那份 {@code inventory.filters()} 正是被
     * 这个 blob {@code replaceFilters} 出来的镜像，它的 {@code size()} 与 {@code ghostSyncedCount}
     * 恒等 ⇒ 差恒为 0 ⇒ 超限永远没有读数（取证档案给的式子在盘上是空转的，见本片回执）。</li>
     * </ul>
     * 两根键各自独立下发，服务端变更时同一次 {@code detectAndSendChanges} 里一起刷新 ⇒ 最坏情况是
     * ≤1 tick 的读数滞后，不会长期错。<b>服务端</b>调用本方法时 {@link #filterCount} 从未被
     * {@link #applyModeState} 写过（那里有 {@code isClient} 早退），但服务端不出图，读数无人读。
     */
    int ghostNotSyncedCount() {
        return Math.max(0, filterCount - ghostSyncedCount);
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
            final String[] parts = record.split("\\|", 4);
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
            // ★第 4 段缺失 = 外来/陈旧三段档 ⇒ 未设（由 resolveCap 回落现全局量），不得臆造成 0
            int cap = PocketConstants.FILTER_CAP_UNSET;
            if (parts.length > 3 && !parts[3].isEmpty()) {
                try {
                    cap = Integer.parseInt(parts[3]);
                } catch (RuntimeException ignored) {
                    cap = PocketConstants.FILTER_CAP_UNSET;
                }
            }
            // ★rebuild 走 PocketGhostRequest 那一份（服务端写入口与这里必须同一段代码，否则两处迟早漂移）
            parsed.add(slotIndex, PocketGhostRequest.rebuildAt(kind, slotIndex, payload, cap));
        }
        return parsed;
    }

    /**
     * 把 ghost 声明<b>原位</b>应用到三个区域的格件上（双端各自应用自己那一份树，读的都是本地
     * {@code inventory.filters()} ⇒ 服务端权威值经 {@link #applyGhostView} 落到客户端镜像）。
     * <p>
     * 服务端这一份对中栏的作用是 {@code ModularSlot.accessibility(false, true)} ——（★R84：禁放置、可取出）
     * 那才是"禁放置禁取出"的执法点（vanilla {@code slotClick} 在服务端读 {@code isItemValid}
     * /{@code canTakeStack}，见 {@code ModularSlot.java:73-80}）；客户端那一份负责虚化渲染。
     * 流体槽与源质格<b>不进 Container</b>（R35），它们在服务端那份只更新自身状态，
     * 但走的<b>是同一条代码路径</b>（不分叉 ⇒ 两端不会因"只有客户端应用"而漂移）。
     * 转 ghost 前先把中栏格内物品搬走（R38 第 2 条：产物不能进自己）。
     */
    private void applyGhosts() {
        applyItemGhosts();
        applyFluidGhosts();
        applyEssenceGhosts();
    }

    /** 中栏 135 格（{@code Kind.ITEM}）：唯一需要"搬空"的一支。 */
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
            // ★上限读数必须走在下面那条"没变就跳过"之前：只调过 cap 而 ghost/样本都没变时，跳过判据会把
            // 新读数整条吞掉 ⇒ 玩家滚了数字、格上不动。setter 自身同值即返回，每拍调不产生额外脏标记。
            widget.setDeclaredCap(declared == null ? PocketConstants.FILTER_CAP_UNSET : declared.cap());
            // ★R84②：声明格现在<b>就是</b>该条需求的抽取落点，格内的同种内容就是"已经补到的产物"，
            // 所以只在内容不是声明那一种时才搬空（旧口径"声明即无条件搬空"会把刚补进来的产物又赶走）。
            // 同 setDeclaredCap 的理由：这一步不能挂在下面那条"没变即跳过"之后，否则服务端已塞进错内容时
            // 永远不会被清理（R83 横向审计的 S3）。
            if (declared != null && !syncManager.isClient()) {
                final ItemStack held = inventory.storage()
                    .getStackInSlot(index);
                if (held != null && !PocketAeChannelOps.contentKey(held)
                    .equals(declared.key())) {
                    evictFromSlot(index);
                }
            }
            if (widget.isGhost() == (declared != null) && sameSample(widget.ghostSample(), sample)) {
                continue;
            }
            widget.setGhost(declared != null, sample);
        }
    }

    /**
     * 18 个流体槽（{@code Kind.FLUID}，索引空间 = tank 号 = 组内列号派生，R78②）。
     * <p>
     * 每槽只切"本 tank 声明了哪种流体"这一个显示状态：各自的 tank、{@code alwaysShowFull}、
     * 两格同权的灌排一个字都不动（流体 ghost 不搬空任何东西，声明本身就是"要拉这一种"）。
     * 循环上界取登记表长度（= {@code GHOST_FLUID_SLOT_LIMIT} = 18）⇒ 与白名单同源，不会漂移。
     */
    private void applyFluidGhosts() {
        for (int index = 0; index < fluidSlots.length; index++) {
            final NekoPocketFluidSlot widget = fluidSlots[index];
            if (widget == null) {
                continue;
            }
            final PocketFilterConfig.Filter declared = inventory.filters()
                .at(PocketFilterConfig.Kind.FLUID, index);
            widget.setDeclaredCap(declared == null ? PocketConstants.FILTER_CAP_UNSET : declared.cap());
            widget.setGhost(
                declared instanceof PocketFilterConfig.FluidFilter,
                declared instanceof PocketFilterConfig.FluidFilter fluid ? fluid.fluidName : "");
        }
    }

    /**
     * 右栏 72 格（{@code Kind.ESSENCE}）：格位归属由<b>服务端的格位表</b>钉死（★R86 起会随清零回收，
     * 见 {@code PocketEssenceStore#extract}；不再是 {@code aspectOrder()[index]}）⇒ 本方法只切 ghost
     * 开关，内容层由 {@link #applyEssenceBlob(String)} 走另一条原位通道。
     * <p>
     * ★<b>R86：遮罩按 tag 归位，不按声明当初的格号</b>。声明里的 {@code slotIndex} 只是"玩家当时拖在
     * 哪一格"的历史坐标，而 R86 之后那一格的归属会变（清零腾格 ⇒ 新 tag 来占最小空位）。抽取侧本来就
     * 读声明自带的 {@code tag}（不吃格号 ⇒ 不会拉错源质），所以把<b>显示</b>也钉在 tag 上，两处才同源。
     * 该 tag 当前没有格位（货已被取空、或还没蒸出来）时，仍画在声明那一格上 ⇒ 玩家看得到"我声明过它"。
     * <p>
     * ★已披露代价：同一 tag 声明在两格上时两格归属相同 ⇒ 遮罩只会出现一次（后一条的 cap 生效）。
     */
    private void applyEssenceGhosts() {
        final PocketFilterConfig.Filter[] home = new PocketFilterConfig.Filter[essenceCells.length];
        for (int index = 0; index < essenceCells.length; index++) {
            final PocketFilterConfig.Filter declared = inventory.filters()
                .at(PocketFilterConfig.Kind.ESSENCE, index);
            if (declared == null) {
                continue;
            }
            final String tag = declared instanceof PocketFilterConfig.EssenceFilter essence ? essence.tag : null;
            // ★R86（审查 B2）：走双源 accessor —— 直接读 inventory.essence() 在客户端拿到的是
            // 开屏时的 NBT 快照，遮罩会照旧错位（改判只活服务端）
            final int placed = essenceCellOfTag(tag);
            home[placed >= 0 && placed < home.length ? placed : index] = declared;
        }
        for (int index = 0; index < essenceCells.length; index++) {
            final NekoEssenceGhostCell cell = essenceCells[index];
            if (cell == null) {
                continue;
            }
            final PocketFilterConfig.Filter declared = home[index];
            // ★同 applyItemGhosts：cap 的推送不能挂在 setGhost 的"没变即返回"之后
            cell.setDeclaredCap(declared == null ? PocketConstants.FILTER_CAP_UNSET : declared.cap());
            cell.setGhost(declared != null);
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
     * {@code kind|slot|payloadKey|cap} 记录，{@code ';'} 分隔（分隔符见 {@code PocketConstants}）。
     * <p>
     * ★第 4 段是 R83 C2 的组上限原始值（{@code -1} = 未设）：它必须随本 blob 下发，否则服务端存着的
     * 玩家调值到不了客户端，虚像右上角永远显示天花板。载荷键永不含 {@code '|'}（base64 字母表 +
     * {@code f:}/{@code e:} 前缀），故第 4 段可以安全地按同一条分隔符切。
     * <p>
     * public static 的理由同上（编解码两端各一份实现就必须钉住，否则 ghost 会在同步中静默变形）。
     */
    public static String ghostBlobOf(PocketFilterConfig filters) {
        return ghostBlobOf(filters, PocketConstants.GHOST_BLOB_MAX_CHARS);
    }

    /**
     * ★<b>R85 N3</b>：带<b>长度预算</b>的 ghost blob 拼装。
     * <p>
     * 上游 {@code StringSyncValue.serialize} → {@code NetworkUtils.writeStringSafe} 对超过
     * {@code Short.MAX_VALUE - 74}（= 32,693 字节）的串<b>静默截断、只 WARN</b>，而一条 {@code ITEM}
     * 声明的载荷键自带整段 gzip+base64 的 NBT（长度无上限）⇒ 不自己收口就是"尾部声明在客户端凭空消失，
     * 且没有任何玩家可见线索"。预算值见 {@link PocketConstants#GHOST_BLOB_MAX_CHARS}（ASCII 单字节 ⇒
     * 字符数就是字节数）。
     * <p>
     * 三条硬口径：
     * <ol>
     * <li><b>只停尾部、不跳中间</b>：写不下的那一条连同它的 {@code ';'} 一起回退 ⇒ 已写部分与"没有预算
     * 的旧实现"<b>逐字节相同</b>，{@link #parseGhostBlob} 一个字都不用改（旧档/旧客户端天然兼容）；</li>
     * <li><b>不静默</b>：被丢的条数由 {@link #ghostNotSyncedCount()} 现算成一条玩家可见读数
     * （{@code gtit.pocket.ghost.not_synced}），★措辞点名"服务端执法不受影响"——这些声明在服务端照常生效，
     * 少的只是客户端那一层虚化渲染；</li>
     * <li><b>不新增同步键</b>：总数走已经存在的 {@code SYNC_MODE}（{@link #composeModeState()} 里那一列
     * {@code filters().size()}），本端只需记"blob 里解析到了几条"。</li>
     * </ol>
     *
     * @param maxChars 累计字符预算；{@code <= 0} 表示一条都不写（★测试用它构造"尾部被停"的形状）
     */
    public static String ghostBlobOf(PocketFilterConfig filters, int maxChars) {
        final StringBuilder builder = new StringBuilder();
        if (filters == null || maxChars <= 0) {
            return builder.toString();
        }
        for (PocketFilterConfig.Filter filter : filters.filters()) {
            final int recordStart = builder.length();
            if (recordStart > 0) {
                builder.append(';');
            }
            builder.append(filter.kind())
                .append(PocketConstants.GHOST_REQUEST_SEPARATOR)
                .append(filter.slotIndex())
                .append(PocketConstants.GHOST_REQUEST_SEPARATOR)
                .append(filter.key())
                .append(PocketConstants.GHOST_REQUEST_SEPARATOR)
                .append(filter.cap());
            if (builder.length() > maxChars) {
                // 回退这一条（含它的分隔符）并停手：已写部分与无预算实现逐字节相同，尾部整条不写。
                builder.setLength(recordStart);
                break;
            }
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

    /**
     * 源质 blob 的<b>三段</b>机读形状（★R78③；段间 {@code ';'}，段内 {@code ','}）：
     * 
     * <pre>
     *   72 个点数（下标 = 格号，无主格写 0） ';' 72 个 tag（无主格写空串） ';' 有货但无格位的 tag 数
     * </pre>
     * 
     * 单函数编解码的理由与 {@link #ghostBlobOf} 同一条：编解码各一份实现就会在同步里静默变形；
     * ★这里同时是"格序由服务端算、随现有 blob 同步"这条裁定的<b>唯一</b>落点
     * （R32 双端同树：格数恒定 72，变的只是每格归属哪个 tag）。
     */
    public static String encodeEssenceBlob(int[] pointsByCell, String[] tagsByCell, int unplaced) {
        final StringBuilder builder = new StringBuilder();
        final int cells = pointsByCell == null ? 0 : pointsByCell.length;
        for (int cell = 0; cell < cells; cell++) {
            if (cell > 0) {
                builder.append(BLOB_CELL_SEPARATOR);
            }
            builder.append(pointsByCell[cell]);
        }
        builder.append(BLOB_SECTION_SEPARATOR);
        final int tagCount = tagsByCell == null ? 0 : tagsByCell.length;
        for (int cell = 0; cell < tagCount; cell++) {
            if (cell > 0) {
                builder.append(BLOB_CELL_SEPARATOR);
            }
            builder.append(tagsByCell[cell] == null ? "" : tagsByCell[cell]);
        }
        builder.append(BLOB_SECTION_SEPARATOR)
            .append(unplaced);
        return builder.toString();
    }

    /**
     * {@link #encodeEssenceBlob} 的逆操作（★容错：段数不足、条数不足、非数字一律按"该格为 0/无主"
     * 回落，外来或陈旧 blob 不得让面板炸）。
     */
    public static EssenceView decodeEssenceBlob(String blob, int cells) {
        final EssenceView view = new EssenceView(cells);
        if (blob == null || blob.isEmpty()) {
            return view;
        }
        final String[] sections = blob.split(String.valueOf(BLOB_SECTION_SEPARATOR), -1);
        final String[] points = sections.length > 0 ? sections[0].split(BLOB_CELL_SEPARATOR_REGEX, -1) : new String[0];
        final String[] tags = sections.length > 1 ? sections[1].split(BLOB_CELL_SEPARATOR_REGEX, -1) : new String[0];
        for (int cell = 0; cell < cells; cell++) {
            if (cell < points.length) {
                try {
                    view.points[cell] = Integer.parseInt(points[cell]);
                } catch (NumberFormatException ignored) {
                    view.points[cell] = 0;
                }
            }
            if (cell < tags.length && !tags[cell].isEmpty()) {
                view.tags[cell] = tags[cell];
            }
        }
        if (sections.length > 2) {
            try {
                view.unplaced = Integer.parseInt(sections[2]);
            } catch (NumberFormatException ignored) {
                view.unplaced = 0;
            }
        }
        return view;
    }

    /** {@link #decodeEssenceBlob} 的结果（★纯数据件，回归套件直接驱动）。 */
    public static final class EssenceView {

        /** 格号 → 点数（无主格为 0）。 */
        public final int[] points;
        /** 格号 → 归属 tag（无主格为 {@code null}）。 */
        public final String[] tags;
        /** 有货但没有格位的 tag 数（溢出兜底文案的驱动量）。 */
        public int unplaced;

        EssenceView(int cells) {
            this.points = new int[cells];
            this.tags = new String[cells];
        }
    }

    /**
     * ★<b>R85 P2</b>：源质 blob 的<b>短路</b>版 compose（旧写法每次都被打全量重算，缓存只当返回值用）。
     * <p>
     * <b>为什么必须短路</b>：{@code AbstractGenericSyncValue} 的 getter 在<b>每一次</b>
     * {@code detectAndSendChanges} 都被无条件调一次（vanilla 每拍 1 次 + 每次点击 1 次 + MUI2 自调 1 次），
     * 而本串里那一个 {@code unplacedTagCount()} 是 {@code O(有货 tag × 72)} 的线性扫
     * （用户包内实测 69 个 aspect ⇒ 约 4,968 次 {@code String.equals} / 拍 / 每个开屏玩家）。
     * 旧代码下面那行 {@code cachedEssenceBlob} <b>只是返回值</b>，每次都重新算完整串再覆盖 ⇒ 一点都没省。
     * <p>
     * <b>短路条件</b>（两条同时成立才复用，见 {@code PocketEssenceStore#contentVersion} 的口径）：
     * <ol>
     * <li>{@link #cachedEssenceStore} <b>还是同一个 store 实例</b>（整表被换掉 ⇒ 实例不同 ⇒ 必重算）；</li>
     * <li>{@code store.contentVersion()} 与上次算时记下的 {@link #cachedEssenceVersion} <b>相等</b>。</li>
     * </ol>
     * <b>什么输入会让它重新算</b>：任何一次 {@code add}/{@code putAll}（蒸馏入账、容器注入）、
     * 任何一次 {@code extract}（玩家取晶、通道取出）、任何一次<b>真的占到新格位</b>的 {@code assignCell}、
     * {@code clear}、以及<b>换 store 实例</b>（读档走 {@code PocketEssenceStore.readFrom} 的新对象）。
     * 版本号由本仓自己在这几处递增 ⇒ 不存在"内容变了而版本没变"的漏算；反之"版本变了而内容没变"
     * 只多算一次，方向是安全的（宁多算不算错）。
     * <p>
     * ★服务端专用：客户端那一支仍返回它自己那份镜像（{@code applyEssenceBlob} 写进来的），
     * 与改动前逐字相同。
     */
    private String composeEssenceBlob() {
        if (syncManager.isClient()) {
            return cachedEssenceBlob;
        }
        final PocketEssenceStore store = inventory.essence();
        final long version = store.contentVersion();
        if (store == cachedEssenceStore && version == cachedEssenceVersion) {
            // 内容一字未变 ⇒ 连 unplacedTagCount() 那趟线性扫一起跳过（★它的答案已在这份缓存串里）
            return cachedEssenceBlob;
        }
        final int[] points = new int[essenceCache.length];
        final String[] tags = new String[essenceCache.length];
        for (int cell = 0; cell < essenceCache.length; cell++) {
            final String tag = store.tagAtCell(cell);
            tags[cell] = tag;
            points[cell] = tag == null ? 0 : store.get(tag);
        }
        cachedEssenceBlob = encodeEssenceBlob(points, tags, store.unplacedTagCount());
        cachedEssenceStore = store;
        cachedEssenceVersion = version;
        return cachedEssenceBlob;
    }

    private String cachedEssenceBlob = "";
    /** ★R85 P2：短路判据的一侧（store 实例身份；换实例即整表读档，必须重算）。 */
    private PocketEssenceStore cachedEssenceStore;
    /** ★R85 P2：短路判据的另一侧（上一次算串时的内容版本号）。 */
    private long cachedEssenceVersion = Long.MIN_VALUE;

    private void applyEssenceBlob(String blob) {
        if (!syncManager.isClient() || blob == null) {
            return;
        }
        final EssenceView view = decodeEssenceBlob(blob, essenceCache.length);
        System.arraycopy(view.points, 0, essenceCache, 0, essenceCache.length);
        System.arraycopy(view.tags, 0, essenceCellTags, 0, essenceCellTags.length);
        essenceUnplaced = view.unplaced;
        // ★内容层原位刷新（R78 D-1）：格数与 widget 树一个字都不动，只换"这一格画什么"
        for (int cell = 0; cell < essenceCells.length; cell++) {
            final NekoEssenceGhostCell widget = essenceCells[cell];
            if (widget != null) {
                widget.setCellContent(essenceCellTags[cell], essenceCache[cell]);
            }
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
        return instant + "|" + PocketConstants.ticksToSecondsCeil(workTicks);
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

    /** 一轮蒸馏对<b>每个非空格</b>各做一次"减 1"（★R84：对象是格不是组，同物多格并行各扣 1 件；R28：5 秒是节拍不是产量）。 */
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

    /**
     * 手动取出的落点：★先玩家背包，装不下的余量才走 {@link #depositItem(ItemStack)}（中栏 → 背包兜底）。
     * <p>
     * 与通道自动拉取那条口径<b>刻意相反</b>：拉取是"往口袋里填"，中栏优先；而玩家点一下取晶却把东西
     * 整进中栏，看到的就是"我点了一下，东西跑到别的地方去了"（R83 缺陷 2②）。两处都装不下时返回
     * 小于请求量的数 ⇒ 调用方按差额退点，既不销毁价值也不掉地下。
     */
    private int depositToPlayerFirst(ItemStack stack) {
        if (stack == null || stack.stackSize <= 0) {
            return 0;
        }
        final int moved = moveToPlayer(stack.copy());
        if (moved >= stack.stackSize) {
            return stack.stackSize;
        }
        final ItemStack rest = stack.copy();
        rest.stackSize = stack.stackSize - moved;
        return moved + depositItem(rest);
    }

    /** 先口袋中栏（跳过 ghost 格）、再玩家背包兜底；<b>不</b>掉地下（拉取是自动行为）。 */
    @Override
    public int depositItem(ItemStack stack) {
        if (stack == null || stack.stackSize <= 0) {
            return 0;
        }
        final int want = stack.stackSize;
        final int moved = inventory.depositIntoStorage(stack);
        final int left = want - moved;
        return left <= 0 ? moved : moved + moveToPlayer(sizedCopy(stack, left));
    }

    /**
     * 单程入包：★一次调用而不是逐 maxStackSize 切块循环。原版 {@code InventoryPlayer#addItemStackToInventory}
     * 会在内部将同一物品摊到多个槽（{@code InventoryPlayer.java:394-464}），且契约是
     * <b>返回 {@code true} 当且仅当入参 {@code stackSize} 已被改写为 0</b>；R83 的循环拿"入参的 stackSize"
     * 当成交数累加，成功一圈就被置 0 一次 ⇒ 进度恒 0 ⇒ 服务器主线程死循环。
     * <p>
     * ★R85：成交数的算术提出来放在 {@link #movedByVanillaContract(boolean, int, int)} 那一行，本方法只保留
     * "读 want → 调一次原版 → 拿回余量"这三步（提纯的动机与可测面见那个方法）。
     *
     * @return 真正进入玩家背包的件数（余量仍留在 {@code toPlayer} 那份副本上，不影响调用方账目）
     */
    private int moveToPlayer(ItemStack toPlayer) {
        final EntityPlayer target = player();
        if (target == null || toPlayer == null || toPlayer.stackSize <= 0) {
            return 0;
        }
        final int want = toPlayer.stackSize;
        // ★整个方法里 addItemStackToInventory 只出现<b>一次</b>（R83 那版 while(剩余>0) 的循环就是死循环本体）
        final boolean accepted = target.inventory.addItemStackToInventory(toPlayer);
        return movedByVanillaContract(accepted, want, toPlayer.stackSize);
    }

    /**
     * ★<b>R85（把 R84 死循环修复的本体算术提成可机检的纯函数）</b>：一次
     * {@code InventoryPlayer#addItemStackToInventory} 之后到底成交了几件。
     * <p>
     * 三条判据，各自对应一个真实失败面：
     * <ul>
     * <li><b>没有循环、没有第二次调用</b>：R83 的 bug 形态是"拿循环计数当成交数"，而原版成功那一刻
     * 就把入参 {@code stackSize} 改写掉 ⇒ 进度恒 0、服务器主线程死循环（R84 已修，但<b>零用例</b>）。
     * 本函数只吃三个<b>已读出的整数</b>，结构上表达不出"再投一次"；</li>
     * <li><b>{@code accepted} 一支返回 {@code want}（契约：返回 true ⇔ 入参已被置 0，两者等值）</b>：
     * ★这里刻意<b>不</b>统一按 {@code want - rest} 算，因为原版有一支不满足"置 0"这半条契约 ——
     * {@code InventoryPlayer.java:394-464} 的<b>可 damaged 物品</b>分支是"把入参对象整个塞进空槽后
     * {@code return true}"，那件东西的 {@code stackSize} <b>留在原值</b>（独立审查档案 §三-1 点名的那条）。
     * 按余量算会把那一支报成 0 件成交 ⇒ 调用方（{@link #depositToPlayerFirst(ItemStack)} 的余量兜底、
     * {@link #performEssenceOut} 的退点）会<b>再落一次同一件</b> = 复制。两支各按各的口径，一字不改 R84 语义；</li>
     * <li><b>{@code false} 一支按余量现读并双向钳非负</b>：原版失败时会把并进已有堆的那部分留在入参上
     * （余量 &lt; 请求数），这就是净成交数；余量反而大于请求数（外来/异常入参）只能得 0，
     * 不许造出负成交数污染账目，也不许报出比请求数更大的数。</li>
     * </ul>
     * public static 的理由：零依赖套件拿不到 {@code EntityPlayer}/{@code InventoryPlayer}，而这条算术
     * 本身可以纯 JVM 钉住（用例 {@code move_to_player_counts_by_vanilla_contract}）。
     *
     * @param accepted      原版返回值
     * @param want          调用前那份副本的件数
     * @param restAfterCall 原版调用<b>之后</b>同一份副本上的件数（余量）
     */
    public static int movedByVanillaContract(boolean accepted, int want, int restAfterCall) {
        if (want <= 0) {
            return 0;
        }
        if (accepted) {
            return want;
        }
        return Math.max(0, want - Math.max(0, restAfterCall));
    }

    /** 只改件数的浅拷贝副本（原版会在入参上就地改写 {@code stackSize}，故任何"探路"都必须先断开别名）。 */
    private static ItemStack sizedCopy(ItemStack stack, int size) {
        final ItemStack copy = stack.copy();
        copy.stackSize = size;
        return copy;
    }

    @Override
    public int fluidBarRoom(int tank, FluidStack probe) {
        return inventory.fluidBarRoom(tank, probe);
    }

    @Override
    public int depositFluid(int tank, FluidStack fluid) {
        return inventory.depositFluidIntoBar(tank, fluid);
    }

    // ------------------------------------------- ★R86 缺陷 3：口袋 → 元件的推送向来源面（服务端会话实现）

    @Override
    public int fluidTankCount() {
        return PocketInventory.tankCount();
    }

    @Override
    public FluidStack fluidInTank(int tank) {
        return inventory.ownTankFluid(tank);
    }

    @Override
    public int drainOwnTank(int tank, int milliBuckets) {
        return inventory.drainOwnTank(tank, milliBuckets);
    }

    @Override
    public Map<String, Integer> essenceStock() {
        return inventory.essence()
            .snapshot();
    }

    @Override
    public int drainEssence(String tag, int points) {
        return inventory.essence()
            .extract(tag, points);
    }

    // ------------------------------------------------------------ R84：中栏既是注入来源也是抽取落点

    @Override
    public int storageSlots() {
        return inventory.storage()
            .getSlots();
    }

    @Override
    public ItemStack storageStackAt(int slot) {
        final int size = storageSize();
        return slot < 0 || slot >= size ? null
            : inventory.storage()
                .getStackInSlot(slot);
    }

    @Override
    public void setStorageStackAt(int slot, ItemStack stack) {
        if (slot < 0 || slot >= storageSize()) {
            return;
        }
        inventory.storage()
            .setStackInSlot(slot, stack);
        inventory.markDirty();
    }

    @Override
    public boolean isStorageGhostDeclared(int slot) {
        return inventory.isGhostItemSlot(slot);
    }

    private int storageSize() {
        return inventory.storage()
            .getSlots();
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

    /**
     * 第 {@code cell} 格归属的 tag（★下标是<b>格号</b>，不是 aspectOrder 的位置；R78③）。
     * <p>
     * 服务端读权威表（{@code PocketEssenceStore#tagAtCell}），客户端读同步镜像
     * {@link #essenceCellTags}；两份的起点都是同一枚 NBT（构造期），之后的变化只由服务端推。
     */
    String essenceTagAtCell(int cell) {
        if (cell < 0 || cell >= essenceCellTags.length) {
            return null;
        }
        return syncManager.isClient() ? essenceCellTags[cell]
            : inventory.essence()
                .tagAtCell(cell);
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
        for (int index = 0; index < essenceCellTags.length; index++) {
            if (tag.equals(essenceCellTags[index])) {
                return essenceCache[index];
            }
        }
        return 0;
    }

    /**
     * ★R86（审查 B2）：某 tag <b>当前</b>落在哪一格（源质遮罩归位用）。
     * <p>
     * 双源与 {@link #essenceTagAtCell(int)} 严格同形：服务端读权威表，客户端读同步镜像
     * {@link #essenceCellTags}（每份 view 由 {@code applyEssenceBlob} 整体覆盖，见其
     * {@code System.arraycopy}）。★<b>不得</b>在客户端读 {@code inventory.essence()} —— 那一份只是
     * 开屏时从承载栈 NBT 解出的<b>起点快照</b>，之后的腾格/占格它一概不知道；拿它算归位就等于
     * "R86 的改判只活服务端、真正出图的客户端照旧错位"。
     */
    int essenceCellOfTag(String tag) {
        if (tag == null || tag.isEmpty()) {
            return -1;
        }
        if (!syncManager.isClient()) {
            return inventory.essence()
                .cellOf(tag);
        }
        for (int index = 0; index < essenceCellTags.length; index++) {
            if (tag.equals(essenceCellTags[index])) {
                return index;
            }
        }
        return -1;
    }

    /**
     * 是否存在"有货但没格位"的源质（R26 的只存不显；R78③ 后判据换成服务端算出来的
     * {@code unplacedTagCount}，不再是"注册数 &gt; 格数"的客户端推断）。
     * <p>
     * 72 格 ≥ 实测注册数 69 ⇒ 常态为 false，但 {@code aspect.overflow_note} 的代码路径<b>保留</b>
     * （addon 追加 aspect 时仍可能超出）。
     */
    boolean essenceOverflow() {
        return syncManager.isClient() ? essenceUnplaced > 0
            : inventory.essence()
                .unplacedTagCount() > 0;
    }

    /**
     * 绑定按钮的 tooltip 正文（R74② 之后绑定信息的<b>唯一</b>可见面）。
     * <p>
     * 形状 = 标题 + 至多 {@code NekoPocketBottomBand.TOOLTIP_ROWS} 条
     * "{@code bind.entry} 换行 {@code bind.located|unlocated|stale}"，
     * ★条目多于上限时<b>必须</b>补一行 {@code bind.truncated}（含未列出的条数）——
     * {@code 绑定表的数据上限仍是 64}，但 ★R84 起玩家可用的是 {@code ALLOWED_BOUND_CELLS = 1}；tooltip 不能滚，
     * "只显示前 N 条却不说明"就是静默删信息
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

    /**
     * ★R84：底部带左段那块 36px「常驻元件信息」的两行正文（缝由 {@code NekoPocketBottomBand.CellInfoText} 定）。
     * <p>
     * 数据只取<b>已同步的绑定行</b>（服务端 {@code composeBindRows} 现算 → {@code SYNC_BIND_ROWS} blob →
     * 两端读同一份），★绝不按内存里的绑定表推断（R19/R39b：客户端那份可能落后，读它就是第二处真相）。
     * <p>
     * 第二行的用途：本轮起玩家可用口径只有 1 枚，而旧档真可能残留多条（本轮刻意不在读档时收缩，
     * 见台账 §八十四⑤），那些多出来的条目<b>不再被服务</b> ⇒ 必须在这里说清楚，否则玩家以为多枚都在跑。
     * 元件"类型 / 条目数"要新增一条服务端同步值才拿得到（且新单元在上游恒返 MAX/0），本轮未接 ⇒ 列进交付说明。
     * <p>
     * ★<b>R85 小项 3：这里的"1 枚"必须走 {@link PocketConstants#ALLOWED_BOUND_CELLS} 而不是字面量</b>。
     * 旧写法把"只有第 1 枚被服务"写成 {@code row == 0} 与 {@code rows.size() - 1} 两处字面量，而其余
     * 五处消费方（{@code PocketBindFlow} / {@code PocketChannelRunner} ×2 / {@code NekoPocketBottomBand} ×2 /
     * {@link #bindSummaryText()}）都走符号 ⇒ 常量一旦回到 2，这一行会独自继续报"另有 1 条"（取证档案 D3）。
     */
    private String cellInfoLine(int row) {
        final java.util.List<NekoPocketBottomBand.Row> rows = bindRows();
        if (row < 0 || row >= NekoPocketBottomBand.CELL_INFO_ROWS) {
            return "";
        }
        if (rows.isEmpty()) {
            return row == 0 ? StatCollector.translateToLocal("gtit.pocket.bind.none") : "";
        }
        // 第一行永远是"被服务的那一枚"（绑定序前 ALLOWED_BOUND_CELLS 枚里取第 row+1 条）
        if (row < PocketConstants.ALLOWED_BOUND_CELLS) {
            return bindRowLine(row);
        }
        final int inert = rows.size() - PocketConstants.ALLOWED_BOUND_CELLS;
        return inert <= 0 ? "" : String.format(StatCollector.translateToLocal("gtit.pocket.bind.inert"), inert);
    }

    /**
     * 绑定块第一行的常驻读数（"已绑定 n/上限"，数字全部由服务端同步的行数与常量给出）。
     * <p>
     * ★R84：分母取 {@code ALLOWED_BOUND_CELLS}（玩家可用口径＝1）而不是 {@code MAX_BOUND_CELLS}（数据层
     * 仍 64），否则读数会喊"还能再绑 63 枚"而入口面一律拒收。旧档真残留多枚时这里就显示"3 / 1"——
     * ★刻意不夹成 1/1：多出来的条目仍然在绑定行 tooltip 里逐条列着，读数不得替玩家删信息（R36 口径）。
     */
    String bindSummaryText() {
        return String.format(
            StatCollector.translateToLocal("gtit.pocket.bind.summary"),
            bindRows().size(),
            PocketConstants.ALLOWED_BOUND_CELLS);
    }

    /** 一条绑定行的完整文本（短码 ID + 位置或状态，与旧第四列的两行渲染同一条算式）。 */
    private String bindRowLine(int index) {
        final NekoPocketBottomBand.Row row = rowAt(index);
        if (row == null) {
            return "";
        }
        final String title = bindRowTitle(index);
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

    /**
     * 一条绑定行的<b>标题段</b>（只有短码 ID，不带位置五键）——常驻行与 tooltip 首段共用这一条算式，
     * ★避免"两处对同一条绑定各写一种样子"。
     */
    private String bindRowTitle(int index) {
        final NekoPocketBottomBand.Row row = rowAt(index);
        return row == null ? ""
            : String.format(StatCollector.translateToLocal("gtit.pocket.bind.entry"), row.shortId());
    }

    /**
     * ★R81③：底部带右段的<b>常驻</b>绑定行文本（第 {@code slot} 个行位，0 起，行位数 =
     * {@link NekoPocketBottomBand#PERSISTENT_ROWS}）。
     * <p>
     * 修的是取证记录里那条"面缺失"：常驻行数从前是 <b>0</b>（右段只有一行读数「已绑定 n / 上限」，
     * 条目全在按钮 tooltip 里），玩家不悬停就永远读不到"到底绑了几条、绑的是谁"，
     * 与身份门禁叠起来就是"绑定只能绑定一个"的观感。
     * <p>
     * 规则（★条目多于常驻行位时，<b>最后一个行位让给截断提示</b>，不得静默只显示前 N 条）：
     * <ul>
     * <li>空表 ⇒ 行位 0 = {@code bind.none}（"尚未绑定元件"），其余行位空串（widget 常驻，几何不空转）；</li>
     * <li>{@code size ≤ PERSISTENT_ROWS} ⇒ 逐位一条 {@code bind.entry}；</li>
     * <li>{@code size > PERSISTENT_ROWS} ⇒ 前 {@code PERSISTENT_ROWS − 1} 位给条目，末位给
     * {@code bind.rows_more}（含<b>未列出</b>的条数，文案点名"悬停绑定按钮"）。完整条目与位置五键照旧在按钮 tooltip
     * （{@link #bindTooltipText()}，上限 {@code TOOLTIP_ROWS}，那里另用 {@code bind.truncated}），
     * ★常驻面变短不等于删信息。</li>
     * </ul>
     * ★<b>两个键不得合成一个</b>：条目多于 {@code TOOLTIP_ROWS} 时常驻面与 tooltip 各自截断、
     * <b>数字不同</b>（常驻面按 {@code size − 1}、tooltip 按 {@code size − 10}），
     * 同一句「未在此列出」配两个读数会让玩家以为少了一批。键按<b>面</b>分，不按句式分。
     * 只读客户端那份同步 blob（{@link #bindRows()}），<b>绝不</b>按内存表推断（R19/R39b）。
     */
    String bindPersistentText(int slot) {
        final java.util.List<NekoPocketBottomBand.Row> rows = bindRows();
        final int size = rows.size();
        if (size == 0) {
            return slot == 0 ? StatCollector.translateToLocal("gtit.pocket.bind.none") : "";
        }
        final int shown = size > NekoPocketBottomBand.PERSISTENT_ROWS ? NekoPocketBottomBand.PERSISTENT_ROWS - 1 : size;
        if (slot < shown) {
            return bindRowTitle(slot);
        }
        return size > shown ? String.format(StatCollector.translateToLocal("gtit.pocket.bind.rows_more"), size - shown)
            : "";
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
     * 说明摘要的完整文本（R74②：原第四列的常驻说明改 tooltip；R78 D-2 又把左栏那 7 段里的
     * "主手限制"并进来 ⇒ 这里就是<b>唯一</b>一份完整说明，两个可见入口是底部带的帮助按钮与
     * 左栏末行的 tooltip）。
     * <p>
     * 只把<b>已经在别处有权威</b>的句子拼在一起（成本常量、ghost 用法、
     * "每格声明吃掉一格真实容量"的代价、R60/R62b 要求可观测的主手边界），不新造第二条口径。
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
        builder.append(StatCollector.translateToLocal("gtit.pocket.ghost.capacity_note"))
            .append('\n');
        builder.append(StatCollector.translateToLocal("gtit.pocket.note.channel"))
            .append('\n');
        // ★R83 D-4：中栏的"箱子属性"是自家手势 + 自家算法，必须在游戏内说清它不是通用箱子整理
        builder.append(StatCollector.translateToLocal("gtit.pocket.storage.sort_gesture"))
            .append('\n');
        builder.append(StatCollector.translateToLocal("gtit.pocket.held.note"))
            .append('\n');
        // ★背包那一条带 %d：规格数字由常量填（lang 不得写死），漏填就会把占位原样印给玩家
        builder.append(
            String.format(
                StatCollector.translateToLocal("gtit.pocket.backpack.cost_note"),
                PocketConstants.PLAYER_BACKPACK_SLOTS));
        return builder.toString();
    }

    /**
     * 交互格的 tooltip 补行：说出"这一格属于第几组第几列、那一列的槽号是多少"。
     * <p>
     * 组号与 tank 号都由 {@link PocketInventory} 的单源映射算（客户端只读同步过来的流体状态，
     * 不自行推断别的口径）。这里<b>不</b>报容量数字：容量在槽本体自己的 tooltip 里说一次
     * （{@link NekoPocketFluidSlot#addToolTip}），两处都写就是两处真相。
     */
    String tankHintText(int interactionIndex) {
        return String.format(
            StatCollector.translateToLocal("gtit.pocket.legend.tank_of"),
            PocketInventory.groupOfInteractionSlot(interactionIndex) + 1,
            interactionIndex % PocketConstants.FLUID_COLUMN_COUNT + 1,
            PocketInventory.tankOfInteractionSlot(interactionIndex) + 1);
    }

    /**
     * <b>流体槽本体</b>的"第几组第几列（=第几个槽）"读数（★R80② 的新落点）。
     * <p>
     * 存在的理由：上一轮展示稿给 18 个流体槽各画了一条"列标题边条"来挂这句组号，用户裁定
     * <b>撤</b>（"我觉得要撤"）⇒ 撤下来的信息必须有落点（R36「不删信息」），于是它进<b>本槽自己的
     * tooltip</b>（见 {@code NekoPocketFluidSlot#addToolTip}）。
     * <p>
     * ★与 {@link #tankHintText(int)} 是<b>同一个 lang 键、两个索引空间</b>：那句按"交互格号"反解
     * tank，这句直接按 tank 号算组/列；两者对同一个 tank 必须给出同一串文本（回归用例
     * {@code pocket_fluid_tank_label_single_source} 钉的就是这条，否则两处文案会各自漂）。
     */
    String tankOwnLabelText(int tank) {
        return String.format(
            StatCollector.translateToLocal("gtit.pocket.legend.tank_of"),
            tank / PocketConstants.FLUID_COLUMN_COUNT + 1,
            tank % PocketConstants.FLUID_COLUMN_COUNT + 1,
            tank + 1);
    }

    /**
     * 每槽容量读数（★规格外自立项的玩家可见面之一，另两处是物品 tooltip 的 {@code tooltip.9}
     * 与 README；数字全部由 {@code PocketConstants} 填，lang 里不得写死，见契约 §7 第 5 条）。
     * <p>
     * R78 D-2 后它<b>不再</b>常驻在左栏（那一行只剩状态回显），改由流体槽 tooltip 与
     * 左栏末行的 tooltip 承载。
     */
    String capacityReadoutText() {
        return String.format(
            StatCollector.translateToLocal("gtit.pocket.fluid.capacity"),
            PocketConstants.FLUID_BAR_CAPACITY_ML,
            PocketConstants.FLUID_TANK_TOTAL,
            PocketConstants.FLUID_TOTAL_CAPACITY_ML);
    }

    /**
     * 左栏末行的<b>一行状态回显</b>（★R78 D-2 允许保留的那一行；为什么它算状态不算说明，
     * 判据写在 {@code NekoPocketLeftColumn} 的类 javadoc）。
     * <p>
     * 内容 = 模式（R39b 服务端算）+ 冷却/剩余与最近一次回执（R16/R24/R10），两者都是
     * <b>运行期事实</b>；宽度装不下时走 tooltip（{@link #statusHintText()} + {@link #notesText()}），
     * 不删信息（R36）。
     */
    String fluidStatusLine() {
        final String mode = modeText();
        final String status = channelStatusText();
        return status.isEmpty() ? mode : mode + " · " + status;
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

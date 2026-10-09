package com.miaokatze.gtit.gui.pocket;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.StatCollector;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;

import com.cleanroommc.modularui.api.IPanelHandler;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.api.widget.Interactable;
import com.cleanroommc.modularui.factory.PlayerInventoryGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.DoubleSyncValue;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.LongSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.StringSyncValue;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widgets.slot.SlotGroup;
import com.miaokatze.gtit.common.items.pocket.ItemNekoDimensionPocket;
import com.miaokatze.gtit.common.items.pocket.PocketAeChannelOps;
import com.miaokatze.gtit.common.items.pocket.PocketCellBindings;
import com.miaokatze.gtit.common.items.pocket.PocketCellProbe;
import com.miaokatze.gtit.common.items.pocket.PocketChannelManager;
import com.miaokatze.gtit.common.items.pocket.PocketChannelRunner;
import com.miaokatze.gtit.common.items.pocket.PocketChannelState;
import com.miaokatze.gtit.common.items.pocket.PocketConstants;
import com.miaokatze.gtit.common.items.pocket.PocketElementStore;
import com.miaokatze.gtit.common.items.pocket.PocketEssenceIntake;
import com.miaokatze.gtit.common.items.pocket.PocketEssenceStore;
import com.miaokatze.gtit.common.items.pocket.PocketFilterConfig;
import com.miaokatze.gtit.common.items.pocket.PocketInventory;
import com.miaokatze.gtit.common.items.pocket.PocketMagnetFilter;
import com.miaokatze.gtit.common.items.pocket.PocketSession;
import com.miaokatze.gtit.common.items.pocket.PocketSessions;
import com.miaokatze.gtit.common.items.pocket.PocketUpgradeSwitches;
import com.miaokatze.gtit.common.items.pocket.PocketUpgradeType;
import com.miaokatze.gtit.common.items.pocket.PocketUpgrades;
import com.miaokatze.gtit.common.items.pocket.PocketWornTapHandler;
import com.miaokatze.gtit.common.items.pocket.channel.PocketChannelSessions;
import com.miaokatze.gtit.common.items.pocket.distill.EssenceGate;
import com.miaokatze.gtit.common.items.pocket.distill.PocketDistillDriver;
import com.miaokatze.gtit.crossmod.taum.TaumCompat;
import com.miaokatze.gtit.crossmod.taum.TaumDistillRules;
import com.miaokatze.gtit.main.GTInterestingThing;
import com.miaokatze.gtit.util.ServerTaskScheduler;

/**
 * 当前面板为 416×288：三栏分别预留 6px 滚动条，物品 207 格、流体 30 槽、源质 120 格。
 * 下述 R75–R81 尺寸描述为历史背景；当前几何以本类及三栏常量为准。
 * <p>
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
        final int sum = 6 + 114 + 4 + 168 + 4 + 114 + 6;
        if (WIDTH != MAIN_OCCUPIED_WIDTH || WIDTH != sum) {
            throw new IllegalStateException(
                "[pocket] 面板宽与主区实占分叉: WIDTH=" + WIDTH
                    + " 实占="
                    + MAIN_OCCUPIED_WIDTH
                    + " 加算式="
                    + sum
                    + "（滚动布局 416 = 6+114+4+168+4+114+6）");
        }
        if (HEIGHT != 288) {
            throw new IllegalStateException("[pocket] 面板高不等于 288（缩短四行后的高度）: " + HEIGHT);
        }
    }

    // ------------------------------------------------------------------ 同步键
    // S2C：格数与行数恒定 ⇒ 键数恒定，不随 addon / 绑定数漂移
    private static final String SYNC_ESSENCE = "pocket.essence.points";
    private static final String SYNC_BIND_ROWS = "pocket.bind.rows";
    private static final String SYNC_MODE = "pocket.mode";
    private static final String SYNC_REMAIN = "pocket.remain";
    static final String SYNC_PROGRESS = "pocket.distill.progress";
    /** S2C：蒸馏状态行原文（still.* + 秒数 + 放弃读数；★R87-h 缺口 #10：多人客户端旧实现直读恒 IDLE/0）。 */
    private static final String SYNC_STATE_LINE = "pocket.distill.state";
    /** S2C：ghost 声明视图（{@code kind:slotIndex:载荷键}，';' 分隔）⇒ 客户端据此<b>原位</b>虚化格子。 */
    private static final String SYNC_GHOST = "pocket.ghost.slots";
    /** 固定索引分片；修改或删除声明不会改变其余条目的同步分片。 */
    public static final int GHOST_SLOTS_PER_PART = 16;
    public static final int GHOST_PART_COUNT = (PocketConstants.GHOST_ITEM_SLOT_LIMIT
        + PocketConstants.GHOST_FLUID_SLOT_LIMIT
        + PocketConstants.GHOST_ESSENCE_SLOT_LIMIT
        + GHOST_SLOTS_PER_PART
        - 1) / GHOST_SLOTS_PER_PART;
    private final String[] clientGhostParts = new String[GHOST_PART_COUNT];
    /**
     * ★★<b>R91-a 裁定 (b)</b>：S2C 的<b>属性层</b>（每格的 {@code attr ∈ {NONE,BIND,MEMORY}} 与正交位
     * {@code P}）= <b>另一枚</b> {@link StringSyncValue}，与 {@link #SYNC_GHOST} 各写各的半。
     * <p>
     * 三条理由（全部来自裁定原文）：① 方案 α 被选中的<b>全部</b>理由就是"零编解码风险、不连坐既有用例"，
     * 把 attr 焊回 ghost blob 的编解码等于自己废掉它（那要按"改写"处理三条 blob 往返/预算用例）；
     * ② 本仓 S2C 的既有正解形态就是 {@code StringSyncValue} + {@code onWidgetValueChanged} <b>双端对偶</b>
     * （上面那六枚同形先例）；③ 换来的差别玩家看不见，成本却是一整轮迭代。
     * ★R86 铁律仍然生效：这一层是"服务端算好的显示事实"⇒ 客户端<b>只</b>读
     * {@link #ghostAttrAt} / {@link #ghostUploadBlockedAt} 那对<b>双源 accessor</b>，
     * ★<b>不许</b>直读 {@code inventory.filters()}（那份只是开屏快照 + ghost blob 的镜像）。
     */
    private static final String SYNC_GHOST_FLAGS = "pocket.ghost.flags";
    /** S2C：上一次通道/绑定动作的回执（{@code langKey|数量}），客户端只做本地化格式化。 */
    private static final String SYNC_RECEIPT = "pocket.receipt";
    /** C2S：ghost 就地转换请求（{@code SET|slot|载荷键} / {@code CLR|slot|区域字母}，文法见 {@code PocketGhostRequest}）。 */
    private static final String SYNC_GHOST_REQUEST = "pocket.ghost.request";
    /**
     * ★R96 S7b：磁力名单的<b>录入</b>请求 C2S（文法只有一条 {@code ADD|<身份键>}）。
     * <p>
     * ★<b>为什么这条不走 {@link #SYNC_ACTION} 那根 int 通道</b>：动作通道的打包式样是
     * {@code code * ACTION_ARG_BASE(1024) + arg} ⇒ {@code arg < 1024}，而 {@code itemId} 在 GTNH 的注册表里
     * 常态就过千 ⇒ 装不进去（越界的 arg 会被 {@code onServerAction} 解成<b>另一个码</b>，
     * 那正是 {@code PocketGhostRequest} 类注释里 CAP 那一段点名过的"落进 default: break 静默失效"）。
     * ★同一条理由也决定了不复用 {@link #SYNC_GHOST_REQUEST}：那根通道的文法是 ghost 声明域
     * （{@code SET/CLR/CAP/FLG}），把磁力名单塞进去就是让一条通道服务两个域、两套真相。
     * ⇒ 单开一根字符串 C2S，形状逐字照 {@link #SYNC_GHOST_REQUEST}（含★客户端 setter 就地被调那一条
     * R85 N1 守卫）。载荷只有 {@code i:itemId:meta:} 那枚身份键，★一个字 NBT 都不上网络（前提 P-11）。
     */
    private static final String SYNC_MAGNET_REQUEST = "pocket.magnet.request";
    /**
     * ★R101：持续化通道<b>频率秒值</b>的提交 C2S（文法只有一条 {@code FREQ|<秒数>}；域 [1,60] 的
     * 钳制两端各跑一遍、判域单源在 {@code PocketConstants}）。
     * <p>
     * ★<b>为什么是一条独立的字符串 C2S</b>（同 {@link #SYNC_MAGNET_REQUEST} 的裁定原文）：
     * 动作通道的打包式样是 {@code code * ACTION_ARG_BASE(1024) + arg} ⇒ {@code arg < 1024} ——
     * 秒值本身装得下（≤60），但旧 {@code ACTION_CHANNEL_FREQ} 那条 int 链随"更快/更慢"两枚按钮
     * 一起退场（R101 拍板：档位 UI 改输入框、11 档封闭表不再约束玩家），单开一条形状逐字照
     * {@link #SYNC_GHOST_REQUEST} 的字符串通道换来"载荷与钳制判域同源、无打包上限"，也避免
     * 复用 {@code SYNC_GHOST_REQUEST}（那根通道的文法是 ghost 声明域，两域一线就是两套真相）。
     */
    private static final String SYNC_FREQ_REQUEST = "pocket.freq.request";
    /**
     * ★R96 S7b：磁力三态名单的<b>配置面</b> S2C 载体（一枚 {@code StringSyncValue}，文法见
     * {@link #encodeMagnetBlob}）。
     * <p>
     * ★用一枚而不是七十二枚：S7a 的预算算术（{@code PocketMagnetFilter} 类注释）给出满档
     * 864–1800 B，距 {@code StringSyncValue} 的 32693 字节墙（{@code Short.MAX_VALUE-74}）有 ≥18 倍余量
     * ⇒ 不撞墙，也就不需要 {@code GHOST_BLOB_MAX_CHARS} 那条"未同步条数"预算（★仍然★不嵌 base64 NBT）。
     * ★也不用 vanilla 槽同步：名单格是 phantom（不进 Container），走的是本仓既有的
     * {@code StringSyncValue + apply*} 双端对偶形态（与 {@link #SYNC_ESSENCE} 同一条通道形状）。
     */
    private static final String SYNC_MAGNET = "pocket.magnet.filter";
    /**
     * ★★<b>R97 R6</b>：五型升级插件「当前生效」位图的 S2C 载体（一枚 {@code IntSyncValue}，
     * bit = {@code PocketUpgradeType#ordinal()}，值 = <b>installed 位图 ∧ ¬off-mask</b>）。
     * <p>
     * 为什么需要它：客户端的 STACK 探针旧读 {@code carrierStackLive()} = vanilla 槽同步<b>滞后</b>的
     * 载体 NBT ⇒ 会话中装上堆叠插件后客户端按旧档预测放入量 = 数量级预测差 = 游标数量幽灵（R2 族）。
     * <b>服务端真值单源不动</b>（{@code PocketUpgradeSwitches#isActive} 现读活载体，执法链零改），
     * 这枚同步值只是把<b>客户端读侧</b>换成服务端算好的镜像；消费点 =
     * {@link #clientUpgradeActiveBits}（经 {@code PocketInventory#setClientStackMirror} 接到
     * {@code storageStackUpgraded()} 的读侧）。不扩 {@code SYNC_MODE}、不开 C2S（客户端只读）。
     */
    private static final String SYNC_UPGRADE_ACTIVE = "pocket.upgrade.active";
    /**
     * ★★<b>R106 D2</b>：魔法使三条<b>子模式</b>位图的 S2C 载体（一枚 {@link IntSyncValue}，值 =
     * {@code PocketElementStore#modeMask()}，缺键读数 = {@code MAGE_MODES_DEFAULT}）。
     * <p>
     * 为什么需要它：穿戴态（B 键开屏）下容器<b>不含饰品格</b> ⇒ 载体栈的 vanilla 槽同步永远不推
     * 饰品格里的那一份 ⇒ 模式行的旧读腿（{@code PocketConfigPanel#modeState} 直读
     * {@code carrierStackLive()}）在穿戴态读到的是<b>陈旧镜像</b>：每次重开都「重置」回旧档，
     * 点击还会按陈旧读数把服务端真值写反。服务端真值单源不动（{@link #liveMageModeBits()} 现读
     * 活查表载体，写点 {@code performUpgradeModeToggle} 同一活查表 ⇒ 写后差分必推新值）；
     * 本枚同步值只是把<b>客户端读侧</b>换成服务端算好的镜像。与 {@link #SYNC_UPGRADE_ACTIVE}
     * 同形（逐字照 R97 R6 先例）：不扩 {@link #SYNC_MODE}（那是通道拉取模式，语义不同——
     * 键名带 {@code mage} 段防误读）、不开 C2S（写仍走动作码 13 服务端链，零改）。
     */
    private static final String SYNC_MAGE_MODES = "pocket.mage.modes";
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

    /**
     * C2S 动作码（<b>互不相同</b>：R64c 判据「各自绑定的动作码不同」）。
     * <p>
     * ★★<b>R93-①：{@code ACTION_TAKE_OUT = 1} 已随"整栏一键取出"一并删除</b>（它的服务端执行体
     * 扫的是整个槽组 ⇒ 用户报的"一键把整栏全拿出来"；快捷移动交回原版 {@code transferStackInSlot}
     * 单格语义，不需要本仓动作码）。★编号<b>不重排</b>：动作码是双端同树的常量，留一个洞比
     * 全体平移更安全（平移一旦与旧客户端/存档里的裸 int 撞上就是静默错派）。
     */
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
    /**
     * ★R86：左键 = 把该组拿到游标上、Shift = 该格整份进背包。<b>arg 里带 shift 位</b>。
     * ★★R91-④ 起它<b>不再是一条独立的"造瓶"支</b>：{@code NekoPocketServerHandler#performEssenceOut}
     * 原样转发到取出单点 {@link #performEssenceOutToPhial(int)} ⇒ 没有空瓶在手就<b>什么都不产</b>。
     */
    private static final int ACTION_ESSENCE_OUT = 7;
    /** 解绑<b>全部</b>（绑定按钮 Shift + 右键，R74）。 */
    private static final int ACTION_UNBIND_ALL = 9;
    /**
     * ★R87-d：左键持源质容器点 72 格 = 点击入槽（arg = 手势锚点格号；★<b>R92-④ 起服务端会读它</b>，但★只给"放置定档"用——记忆闸仍现读 {@code cellOf(tag)}，
     * 这条分离由用例 {@code essence_memory_gate_reads_landing_cell} 钉住；★R88 载体见 {@code requestEssenceIntake} 的门禁；★R91-④
     * 溶掉源质后按档退回空瓶）。
     */
    private static final int ACTION_ESSENCE_INTAKE = 10;
    /**
     * ★R90 E3（D1「持瓶取不出」）：左键持<b>空瓶</b>点 72 格 = 格→瓶取出（arg = 手势锚点格号，
     * 服务端按格位归属表反查 tag——与 {@link #ACTION_ESSENCE_OUT} 同一条 R18/R19 纪律，不吃客户端 tag）。
     * ★★<b>R91-④ 起这一条是「盘 → 玩家」取出的唯一动作码语义</b>：一次点击把游标上的空瓶灌满
     * （一瓶 = {@link PocketConstants#ESSENCE_OUT_UNIT_POINTS} 点、向下取整、余数留盘 = C1），
     * 权威复验 / 扣点 / 灌装 / 游标结算全在服务端 {@link #performEssenceOutToPhial(int)}；
     * 空手与持非容器点格 ⇒ 零产出（{@code gtit.pocket.essence.need_phial} 面板回执）。
     * 与之竞争的"空游标凭空造瓶"支已随 {@link #ACTION_ESSENCE_OUT} 一并改派到同一单点。
     */
    private static final int ACTION_ESSENCE_OUT_TO_PHIAL = 11;
    /**
     * ★R96 S2（P-2/P-3）：配置面板里那一枚开关的<b>唯一</b>出口——arg 的编解码单源在
     * {@link PocketConfigPanel#encode}（{@code ordinal * 2 + offBit}），本处只登记码值。
     * <p>
     * ★<b>左键点插件格不发这条</b>：开面板是纯客户端手势（次级面板由 {@link IPanelHandler#openPanel()}
     * 在客户端建），不发码 ⇒ 不会因为"看一眼配置"就往服务端队列里投一颗 lambda。写档只发生在
     * <b>面板里那枚开关按钮</b>上，且执行体在服务端。
     */
    private static final int ACTION_UPGRADE_SWITCH = 12;
    /**
     * ★R96 S7b：磁力名单的四条<b>无载荷</b>写腿（★都只发"我点了一次"，真值全在服务端算）。
     * <p>
     * ★为什么不把"加一条"也放进来：加条的载荷是那枚 {@code i:itemId:meta:} 身份键，越不出
     * {@code arg < 1024} 的形状 ⇒ 走 {@link #SYNC_MAGNET_REQUEST}（理由逐字写在那一枚常量的注释里）。
     * ★编号接续 14…17，不重排既有码值（动作码是双端同树的常量，留洞比平移安全，同 R93-① 那条）。
     * ★★TP-S7c 合并改口：S7b 当时写的是"接续 13…16"，而 master 侧 S9b 已把 <b>13 判给了
     * {@link #ACTION_UPGRADE_MODE}</b>（两侧各自都对，合成即撞码 = 同一 case 标签编译不过）。
     * 让位的是<b>未验证、未交付</b>的 S7b 四腿（平移 14→15、15→16、16→17、13→14）：S7b 从未以
     * 任何码值出过包，双端同树 ⇒ 平移零兼容成本；S9b 的 13 已随 master 收口并被用例
     * （mage_config_panel_mode_codec_and_commit_leg 的"与开关码不同值"那条）钉死 ⇒ 不动。
     */
    /** 三态循环（{@code NONE → WHITELIST → BLACKLIST → NONE}，无 arg）。 */
    private static final int ACTION_MAGNET_MODE_CYCLE = 14;
    /** 吸取目标两档循环（{@code POCKET → PLAYER → POCKET}，无 arg）。 */
    private static final int ACTION_MAGNET_TARGET_CYCLE = 15;
    /** 清空名单（★只抹条目、★不动三态，与"切到无限制"是两件事；无 arg）。 */
    private static final int ACTION_MAGNET_CLEAR = 16;
    /** 按格号摘一条（arg = 格号 0…71，★服务端用现读的那条键，不吃客户端抄上来的键）。 */
    private static final int ACTION_MAGNET_REMOVE_AT = 17;

    /**
     * ★R96 S9b（P-3 的续）：配置面板<b>魔法使挂载框</b>里那三行模式控件的唯一出口——arg 的编解码单源在
     * {@link PocketConfigPanel#encodeMode}（{@code row * 2 + onBit}），本处只登记码值。
     * <p>
     * ★<b>不复用 {@link #ACTION_UPGRADE_SWITCH} 的码空间</b>：那条的 arg 是「型 ordinal × 2 + <b>off</b> 位」，
     * 本条是「行号 × 2 + <b>on</b> 位」。并码会造出两个静默错读：{@code ordinal} 与 {@code row} 同为 0..2
     * 的整数（CAPACITY=0 / STACK=1 / MAGNET=2 与 结晶=0 / 猫猫币=1 / 源质转换=2），一旦合流，
     * "关掉结晶"会被解成"把容量升级切到关"；而 {@code off} 与 {@code on} 两个位义相反，
     * 复用码位就等于把某一条的语义整体取反。★分派表在 {@link #onServerAction} 的 switch 里，两条各一行。
     * <p>
     * ★左键点插件格仍然<b>不发这条</b>（开面板是纯客户端手势，同 {@link #ACTION_UPGRADE_SWITCH} 的裁定）。
     */
    private static final int ACTION_UPGRADE_MODE = 13;

    /**
     * ★★<b>R101：{@code ACTION_CHANNEL_FREQ = 18} 已随"更快/更慢"两枚升降按钮一并删除</b>（频率档位
     * 改为秒值输入框，提交走新的字符串通道 {@link #SYNC_FREQ_REQUEST}）。★编号<b>不重排</b>：动作码是
     * 双端同树的常量，留一个洞比全体平移更安全（平移一旦与旧客户端/存档里的裸 int 撞上就是静默错派，
     * 同 R93-① 那条）。档位表本体与旧 NBT 键名也一字未动（前者还是手动道的节拍源，后者是兼容读面）。
     */

    /**
     * ★R96 S2 → ★R97 S5：升级配置面板（主面板之上的次级面板）的句柄，<b>一型一枚、共五枚</b>。
     * <p>
     * ★只在客户端有值（{@code IPanelHandler.simple} 要求宿主 {@code ModularPanel} 已挂树，服务端没有
     * 屏幕对象），服务端恒 {@code null} ⇒ 写腿不可能被这条通道碰到（写腿另有 {@code isClient} 早退兜底）。
     * <p>
     * ★★R97 S5（P-3 显式翻案）：{@code SecondaryPanel} 的面板缓存是<b>handler 实例级</b>的（字节码实证
     * {@code panel != null} 即跳过 buildPanel）⇒ R96 那个"单字段 + 五行常驻"的形状里，第二型拿到的是第一型
     * 的缓存面板。开<b>五枚</b> handler = 五块互不串型的独立面板（仓内多 handler 先例：
     * {@code TerminalGiftPage:301-313} 宿主身份比对范式，这里数组化：一型一槽、逐槽各自比对宿主）。
     * 同型重开仍拿本枚的缓存面板（想要的），异型重开拿到的是<b>另一枚</b>的面板。
     */
    private final IPanelHandler[] configPanels = new IPanelHandler[PocketUpgradeType.values().length];
    /** {@link #configPanels} 各槽当时挂的宿主面板（身份比对：换实例即重建，防跨屏打开）。 */
    private final ModularPanel[] configPanelHosts = new ModularPanel[PocketUpgradeType.values().length];
    /** {@link #assemble()} 产出的主面板实例（次级面板要挂到它上面）。 */
    private ModularPanel mainPanel;

    private final PlayerInventoryGuiData data;
    private final PanelSyncManager syncManager;
    private final UISettings settings;
    private final PocketSlots slots;
    private final PocketInventory inventory;
    /**
     * ★R90 T2：服务端执行体宿主（动作实现 / ghost 执行体 / 防伪守卫 / PocketSession 驱动面实现，
     * 见 {@link NekoPocketServerHandler} 的类 javadoc）。本类降为<b>装配壳 + 委托</b>；
     * 仅离线套件源码机检钉住位置的少数执行体（{@code onServerAction} 分发 switch、
     * ★R91-④ 之后取出侧只剩 {@code performEssenceOutToPhial} 一条单点、{@code writeSessionToCarrier}
     * 终态回滚簇、{@code moveToPlayer}/{@code drainEssence}）按原位留在本类，经同包直调，不构成耦合回退。
     */
    private final NekoPocketServerHandler server;
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
    /**
     * ★R96 S7b：磁力名单 72 格（12×6）的格件登记表（★phantom 侧，<b>不进 Container</b> ⇒ 守恒 225 一字不动）。
     * <p>
     * 与上面三组同一机制：长度恒定为 {@code MAGNET_FILTER_SLOTS}、装配序固定 ⇒ 数据变化只原位换内容层
     * （R41b/R32），不会因为"名单多了三条"而在 widget 树上长出或缩掉节点。
     * ★这些实例由<b>次级</b>配置面板装配（{@code PocketConfigPanel#magnetGrid}），而次级面板在纯 JVM 里造不出来，
     * 所以登记表可能一直是空的 ⇒ {@link #applyMagnetCells()} 必须容忍 {@code null} 项（★不允许 NPE，
     * 也不允许"没登记就抛"把主面板装配带崩）。
     */
    private final NekoMagnetGhostCell[] magnetCells = new NekoMagnetGhostCell[PocketConstants.MAGNET_FILTER_SLOTS];

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
    /**
     * ★R95 S5：18 个流体槽 <b>long 真值</b>的客户端镜像（每 tank 一根 {@code LongSyncValue}，
     * 服务端 getter 读 {@code PocketInventory#tankTruthAt}；自绘读数（{@code NekoPocketFluidSlot}）
     * 只读镜像，不在客户端读 inventory——那份只是开屏快照，同 {@link #essenceCache} 的口径）。
     */
    private final long[] clientTankAmounts = new long[PocketConstants.FLUID_TANK_TOTAL];
    /** ★R95 S5：long 真值同步键前缀（实际键 = 前缀 + tank 号；每 tank 一根）。 */
    private static final String SYNC_TANK_TRUTH_PREFIX = "pocket.tank.truth.";
    /**
     * ★★<b>R97 R6：五型「当前生效」位图的客户端镜像</b>（bit = {@code PocketUpgradeType#ordinal()}，
     * 写者只有 {@code SYNC_UPGRADE_ACTIVE} 的客户端 setter；开屏以 vanilla 已同步的载体档播种
     * ——与旧读法的开屏值同源，首包到达后即由服务端真值接管）。
     * 读侧只经 {@link #clientUpgradeActive(PocketUpgradeType)} 这一条小 accessor，不散写位运算。
     */
    private int clientUpgradeActiveBits;
    private int clientUpgradeInstalledBits;
    private int clientCapacityUpgradeCount;
    private int clientStackUpgradeCount;
    /**
     * ★★<b>R106 D2</b>：魔法使三条子模式位图的客户端镜像（值 = 服务端 {@link #liveMageModeBits()}；
     * 写者只有 {@link #SYNC_MAGE_MODES} 的客户端 setter，开屏以 vanilla 已同步的载体档播种——与
     * {@link #clientUpgradeActiveBits} 同款「首帧不回退」，随后由服务端真值 ≤1 tick 推平）。
     * 读侧只经 {@link #mageModeOnNow(int)} 这一条双源 accessor 的客户端支，不散写位运算。
     */
    private int clientMageModeBits;
    private String bindRowsBlob = "";
    private boolean pullMode;
    private int filterCount;
    private int instantRemainSeconds;
    private int timedRemainSeconds;
    private double clientDistillProgress;
    /** ★R87-h：服务端下发的蒸馏状态行原文（客户端唯一读数；服务端不出图，恒空串）。 */
    private String clientDistillStateLine = "";
    /** 服务端下发的 ghost 声明 blob（客户端据此原位虚化；<b>不</b>自行推断，R39b/R19）。 */
    private String ghostBlob = "";
    /**
     * ★★<b>R91-a（S2C 载体形状裁定）</b>：属性层（{@code attr} + {@code P}）走<b>另一枚</b>
     * {@link StringSyncValue}（{@link #SYNC_GHOST_FLAGS}），客户端这一份是它的<b>镜像</b>。
     * <p>
     * ★为什么是独立的一份而不是写回 {@code inventory.filters()}：ghost blob 每来一次就
     * {@code replaceFilters} <b>整体换实例</b>，属性若住在那个实例里就会被另一根通道的刷新抹掉
     * （且 R91-a 明文"不把属性层焊回它的编解码"）。本字段<b>只</b>由 {@link #applyGhostFlagsView(String)}
     * 写、只经 {@link #ghostAttrAt} / {@link #ghostUploadBlockedAt} 读 ⇒ 一根通道一个所有者。
     * ★载荷表在这里恒空（本实例只用来装位表）。
     */
    private final PocketFilterConfig clientGhostFlags = new PocketFilterConfig();
    /** 属性 blob 的上行原文（与 {@link #ghostBlob} 同一条"变了才应用"去重口径）。 */
    private String ghostFlagsBlob = "";
    // ---- ★R96 S7b：磁力三态名单的两份轨（客户端镜像 + 服务端快照；★一根通道一个所有者）----
    //
    // ★为什么客户端要一份镜像而不是现读 inventory：名单住在<b>载体栈的根 NBT</b>（键 magnetFilter），
    // 而 {@code inventory} 那份只是开屏快照 + 关屏写回 —— 拿它当读数就是 R39b/R19 明令的"客户端推断服务端
    // 事实"。镜像只由 {@link #applyMagnetBlob(String)} 写、只经 {@link #magnetMode()} 那一组访问器读，
    // 与 {@link #clientGhostFlags} 完全同形（一根通道一个所有者）。
    /** 客户端镜像：三态（★空档 = {@code NONE} = "没配过"那一态）。 */
    private PocketMagnetFilter.Mode clientMagnetMode = PocketMagnetFilter.Mode.NONE;
    /** 客户端镜像：吸取目标两档（★空档 = {@code POCKET} = 现状行为）。 */
    private PocketMagnetFilter.Target clientMagnetTarget = PocketMagnetFilter.Target.POCKET;
    /**
     * 客户端镜像：72 格的身份键，★下标 = 格号 = 服务端 {@code LinkedHashSet} 的插入序位置
     * （排序由服务端算并随 {@link #SYNC_MAGNET} 下发，★客户端绝不按字符串自己排一遍 —— 那就是 R32 的
     * "两端各算各的序"）。空位是 {@code null}。
     */
    private final String[] clientMagnetKeys = new String[PocketConstants.MAGNET_FILTER_SLOTS];
    /** 客户端镜像：名单条数（★读数是服务端算好带下来的，不是 {@code clientMagnetKeys} 的非空计数）。 */
    private int clientMagnetCount;
    /** 客户端镜像的上行原文（★与 {@link #ghostBlob} 同一条"变了才应用"去重口径，也是同步 getter 的客户端回值）。 */
    private String clientMagnetBlob = "";
    /**
     * ★服务端侧的<b>上次算好的串</b>（同步 getter 的返回值）。名单的唯一写点在
     * {@code NekoPocketServerHandler}，所以这里由 {@link #refreshMagnetSnapshot()} 在<b>变更后</b>重算，
     * 而不是每 tick 解一遍 NBT —— 与 {@code composeEssenceBlob} 的 R85 P2 缓存同一动机。
     * ★另带载体身份比对做兜底（{@link #magnetSnapshotCarrier}）：换栈即重算，不留陈旧串。
     */
    private String serverMagnetBlob = "";
    private ItemStack magnetSnapshotCarrier;
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
    /**
     * ★R88 B3：承载栈被塞进自家存储格（NBT 自环档）的<b>一次性</b>告警闩——只报一次，
     * 免得 driver 每拍重试重定位时把日志刷满（同 R62 的"每事件一条而不是每 tick 一条"口径）。
     */
    private boolean nestedCarrierWarned;

    private NekoPocketPanel(PlayerInventoryGuiData data, PanelSyncManager syncManager, UISettings settings) {
        this.data = data;
        this.syncManager = syncManager;
        this.settings = settings;
        this.slots = new PocketSlots();
        this.pocket = data.getUsedItemStack();
        this.carrierSlotIndex = data.getSlotIndex();
        // ★R100（免开背包）：解析走 PocketChannelSessions 那个<b>单一公共入口</b>（面板装配与
        // driver 的 headless 复原腿共用同一份"取根 + readFrom"式子，禁第二份真相）；
        // 下面那两条活查探针<b>留在本类</b>（门禁按"注入点恰 1 + lambda 恰 2"钉着）。
        this.inventory = PocketChannelSessions.parseCarrierInventory(this.pocket);
        // ★R95 S5：升级位探针注入（活查载体栈版）——readFrom 已用档内位图自播种，这里换活查表版：
        // 覆盖"口袋原本无 NBT、会话期内才第一次固化升级"的那一支（install 写的是栈上现 NBT，可能不在
        // readFrom 捕获的那份根上）。注入点约定见 PocketInventory#setUpgradeProbes 的 javadoc。
        // ★★R96 S2 收口：这两条 lambda 从 hasUpgrade 换成组合谓词 isActive。这不是风格问题——
        // 注入会<b>覆盖</b> readFrom 的自播种，留成 hasUpgrade 就等于把整场面板会话里的开关旁路掉：
        // 玩家在配置面板上关掉容量/堆叠，tank 天花板与单格上限照旧走升级档（"切了开关但行为不变"，
        // 本轮 C3 的同型事故）。读法与 PocketInventory#readFrom 那两条逐字同形。
        this.inventory.setUpgradeProbes(
            this::capacityUpgradeActiveNow,
            () -> PocketUpgradeSwitches.isActive(carrierStackLive(), PocketUpgradeType.STACK));
        this.inventory.setStackCountProbe(this::storageStackUpgradeCount);
        this.inventory.setCapacityCountProbe(this::capacityUpgradeCountNow);
        // ★★<b>R97 R6：客户端 STACK 读侧换同步镜像</b>（服务端保持上面那条活查载体探针 = 执法链零改）。
        // 播种值与旧读法的开屏值<b>同源</b>（vanilla 已同步到客户端的载体档）⇒ 开屏首帧到
        // SYNC_UPGRADE_ACTIVE 首包之间读数不回退；之后每次升级位/开关位变化由服务端真值 ≤1 tick 推平。
        // ★刻意走 setClientStackMirror 而不是二次 setUpgradeProbes（探针注入点是"恰 1 处"的单点口径）。
        if (syncManager.isClient()) {
            clientUpgradeActiveBits = liveUpgradeActiveBits();
            clientUpgradeInstalledBits = liveUpgradeInstalledBits();
            clientCapacityUpgradeCount = PocketUpgrades.capacityUpgradeCount(carrierStackLive());
            clientStackUpgradeCount = PocketUpgrades.stackUpgradeCount(carrierStackLive());
            // ★R106 D2：模式位镜像同款播种（vanilla 已同步的载体档）⇒ 首帧不闪变；穿戴态的
            // 「陈旧窗口」从『永远』缩到 SYNC_MAGE_MODES 首包 ≤1 tick（B 键/主手两路开屏都经这里）。
            clientMageModeBits = liveMageModeBits();
            this.inventory.setClientStackMirror(() -> clientUpgradeActive(PocketUpgradeType.STACK));
        }
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
        // ★R96 S7b：磁力名单的两份轨从<b>同一枚载体 NBT</b>播种（服务端那份是权威、客户端那份是 vanilla
        // 同步过来的镜像，开屏时二者逐字节相同 ⇒ 起点一致；之后的每一次变化只由服务端推，见 composeMagnetBlob）。
        seedMagnetTracks();
        // ★R87-f：声明保格谓词（查询式直查现役声明表）：源质清零时声明中的 tag 保留格位；null = 与 R86 逐字一致。
        inventory.essence()
            .setDeclaredTagProbe(tag -> PocketEssenceIntake.isDeclaredEssenceTag(filters(), tag));
        this.server = new NekoPocketServerHandler(this);
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
        // allowShiftTransfer 对<b>本仓五组</b>（★R95 S4 起含升级格）仍为 false：shift 的落点是玩家背包那一组，
        // 而那一组由框架自己注册（rowSize 9、allowShiftTransfer=true）。
        // ★R83：中栏这一组第 4 参开成 true = 玩家背包↔中栏的 shift 收存（需求 5「箱子属性」里唯一能由
        // 开关给到的那半；中键/R 键整理是自家手势，不是通用箱子整理）。蒸馏/流体/绑定/升级四组仍关 ——
        // 五组全开会出现"同一次 shift 被两组各抢一次"的分叉（★升级格另有"固化不可逆，不收快捷移入"
        // 那条理由，见下方 GROUP_UPGRADE 注册处）。
        // ghost 格不因此被灌：放置判据已钉在服务端 handler 的 isItemValid 上（R83 B1）。
        syncManager
            .registerSlotGroup(new SlotGroup(PocketSlots.GROUP_STORAGE, NekoPocketStorageColumn.COLUMNS, 100, true));
        syncManager.registerSlotGroup(new SlotGroup(PocketSlots.GROUP_FLUID, PocketSlots.FLUID_COLUMNS, 100, false));
        syncManager.registerSlotGroup(
            new SlotGroup(PocketSlots.GROUP_DISTILL, NekoPocketEssenceColumn.DISTILL_COLUMNS, 100, false));
        syncManager.registerSlotGroup(new SlotGroup(PocketSlots.GROUP_BIND, 1, 100, false));
        // ★R95 S4：升级插件格组（行 3 右位一行 5 格 ⇒ rowSize = UPGRADE_SLOTS；矩阵侧与 Container 侧
        // 同名 GROUP_UPGRADE）。shift 仍关（与绑定格同一条纪律）：固化不可逆，快捷移入的一次误触
        // 就会永久吃掉一枚插件——放入只留"看着灰化图案点进对应格"这一条有意手势。
        syncManager
            .registerSlotGroup(new SlotGroup(PocketSlots.GROUP_UPGRADE, PocketInventory.UPGRADE_SLOTS, 100, false));
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
        for (ParentWidget<?> band : NekoPocketBottomBand.build(this, this::statusBlockText)) {
            panel.child(band);
        }

        // 4) 槽数口径断言（R95：工厂产出 189（含★S4 接进底带的升级格 5）+ 框架背包 36 = 225；
        // 多 = 重复接入，少 = 漏接，双端同抛）
        slots.assertTotalRealSlots();

        // 4b) ghost 虚化：客户端先按自己从 NBT 读到的那份声明表原位刷一遍（服务端那份是权威，
        // 之后每次变更都由 SYNC_GHOST 覆盖）。放在装配末尾 ⇒ 此时 itemSlots 已全部登记。
        if (syncManager.isClient()) {
            applyGhostView(composeGhostBlob());
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

    /**
     * ★R88 C3：面板内回执的<b>服务端写入口登记表</b>。
     * <p>
     * 槽件侧（{@code PocketSlots}）手里只有 {@code ModularSlot → SyncHandler → PanelSyncManager} 这一条链，
     * 拿不到面板对象，而裁定 C3 要求蒸馏注入与流体搬运的操作性回执一律不进聊天框 ⇒ 以「现役
     * {@code PanelSyncManager} 实例」为键登记本面板，取回时再做一次同一性校验：对不上就丢弃这条回执，
     * 绝不跨玩家误投。只在服务端登记，摘牌点是 {@link #onContainerClosed()}。
     */
    private static final Map<PanelSyncManager, NekoPocketPanel> RECEIPT_HOSTS = new IdentityHashMap<>();

    /** 槽件侧写一条面板回执；无现役面板（未装配完／已关屏）⇒ 静默丢弃，不抛、不落聊天框。 */
    static void recordSlotReceipt(PanelSyncManager host, String key, int moved, int refused) {
        final NekoPocketPanel panel = host == null ? null : RECEIPT_HOSTS.get(host);
        if (panel != null && panel.syncManager == host) {
            panel.putReceipt(key, moved, refused);
        }
    }

    private void registerSyncValues() {
        syncManager.syncValue(
            "pocket.upgrade.stack.count",
            new IntSyncValue(() -> PocketUpgrades.stackUpgradeCount(carrierStackLive()), value -> {
                if (syncManager.isClient()) {
                    clientStackUpgradeCount = Math.max(0, Math.min(PocketConstants.STACK_UPGRADE_MAX_COUNT, value));
                }
            }));
        if (!syncManager.isClient()) {
            RECEIPT_HOSTS.put(syncManager, this);
        }
        syncManager.syncValue(SYNC_ESSENCE, new StringSyncValue(this::composeEssenceBlob, this::applyEssenceBlob));
        syncManager.syncValue(SYNC_BIND_ROWS, new StringSyncValue(this::composeBindRows, this::applyBindRows));
        syncManager.syncValue(SYNC_MODE, new StringSyncValue(this::composeModeState, this::applyModeState));
        syncManager.syncValue(SYNC_REMAIN, new StringSyncValue(this::composeRemain, this::applyRemainState));
        for (int part = 0; part < GHOST_PART_COUNT; part++) {
            final int partIndex = part;
            syncManager.syncValue(
                SYNC_GHOST + "." + partIndex,
                new StringSyncValue(
                    () -> ghostBlobPartOf(inventory.filters(), partIndex),
                    blob -> applyGhostPart(partIndex, blob)));
        }
        // ★R91-a：属性层单独一枚（<b>不</b>扩 SYNC_GHOST 的段数、<b>不</b>动它的编解码）。
        // ★键数仍恒定：这一枚不随格数 / 属性数增加而增加（服务端一次整串下发，与 ghost blob 同口径）。
        syncManager
            .syncValue(SYNC_GHOST_FLAGS, new StringSyncValue(this::composeGhostFlags, this::applyGhostFlagsView));
        syncManager.syncValue(SYNC_RECEIPT, new StringSyncValue(this::composeReceipt, this::applyReceipt));
        syncManager.syncValue(SYNC_PROGRESS, new DoubleSyncValue(this::serverDistillProgress, value -> {
            if (syncManager.isClient()) {
                clientDistillProgress = value;
            }
        }));
        syncManager
            .syncValue(SYNC_STATE_LINE, new StringSyncValue(this::distillStateLineText, this::applyDistillStateLine));
        // ★R85 N1：allowC2S 的 setter <b>双端都会被调</b>（上游 setValue(v) 默认 setSource=true）⇒
        // 两个 receiver 各自带一道 isClient 早退，见 receiveServerAction / receiveGhostRequest 的 javadoc。
        syncManager.syncValue(SYNC_ACTION, new IntSyncValue(() -> 0, this::receiveServerAction).allowC2S());
        // ★R95 S5：18 个流体槽的 long 真值（每 tank 一根，S2C；16G 不进 int，头层 FluidStack 同步
        // 只带得到 min(真值, int 顶)）。★只在变化时上包（ValueSyncHandler 的 cache 比对），与
        // FluidSlotSyncHandler 的头层同步互不替代：头管"哪种流体 + 液面比例"，这里管"到底有多少 mB"。
        for (int tank = 0; tank < clientTankAmounts.length; tank++) {
            final int tankIndex = tank;
            syncManager.syncValue(
                SYNC_TANK_TRUTH_PREFIX + tank,
                new LongSyncValue(() -> inventory.tankTruthAt(tankIndex), value -> {
                    if (syncManager.isClient()) {
                        clientTankAmounts[tankIndex] = value;
                    }
                }));
        }
        // ghost 拖入/解绑的载荷是一串键（itemId+meta+base64NBT 可以很长），装不进上面那个 int 通道，
        // 因此单开一根字符串 C2S；**执行体在服务端**（R18/R19），客户端那份 setter 由入口守卫挡掉
        // （★旧注释"从不被调用"是错的：setValue 的 setSource 默认 true ⇒ 客户端确实会被调一次）。
        syncManager.syncValue(SYNC_GHOST_REQUEST, new StringSyncValue(() -> "", this::receiveGhostRequest).allowC2S());
        // ★R96 S7b：磁力名单的配置面轨（S2C 一枚 + C2S 一枚，形状逐字照上面 SYNC_GHOST / SYNC_GHOST_REQUEST）。
        // ★枚数刻意是"名单整体一枚"而不是"每格一枚"：S7a 的预算算术给出满档 ≤1800 B（距 32693 墙 ≥18 倍），
        // 而每格一枚会长出 72 个同步键、72 次 cache 比对 ⇒ 同一件事的两条轨道里只留一条。
        syncManager.syncValue(SYNC_MAGNET, new StringSyncValue(this::composeMagnetBlob, this::applyMagnetBlob));
        syncManager
            .syncValue(SYNC_MAGNET_REQUEST, new StringSyncValue(() -> "", this::receiveMagnetRequest).allowC2S());
        // ★R101：频率秒值提交的 C2S 一枚（形状逐字照 SYNC_MAGNET_REQUEST：getter 恒空、allowC2S、
        // 服务端执行体在 handler；客户端 setter 的就地回投由 receiveFreqRequest 的 R85 N1 守卫挡掉）。
        syncManager.syncValue(SYNC_FREQ_REQUEST, new StringSyncValue(() -> "", this::receiveFreqRequest).allowC2S());
        // ★R97 R6：五型「当前生效」位图（installed ∧ ¬off）单枚 S2C——服务端 getter 现读活载体
        // （真值单源 PocketUpgradeSwitches），客户端 setter 只写镜像。与 SYNC_PROGRESS 同形：
        // setter 带 isClient 守卫（上游 setValue 的 setSource 默认 true，双端都可能被调一次）。
        syncManager.syncValue("pocket.upgrade.installed", new IntSyncValue(this::liveUpgradeInstalledBits, value -> {
            if (syncManager.isClient()) {
                clientUpgradeInstalledBits = value;
            }
        }));
        syncManager.syncValue(
            "pocket.upgrade.capacity.count",
            new IntSyncValue(() -> PocketUpgrades.capacityUpgradeCount(carrierStackLive()), value -> {
                if (syncManager.isClient()) {
                    clientCapacityUpgradeCount = value;
                }
            }));
        syncManager.syncValue(SYNC_UPGRADE_ACTIVE, new IntSyncValue(this::liveUpgradeActiveBits, value -> {
            if (syncManager.isClient()) {
                clientUpgradeActiveBits = value;
            }
        }));
        // ★R106 D2：魔法使三条子模式位图单枚 S2C（形状逐字照上面 SYNC_UPGRADE_ACTIVE：服务端 getter
        // 现读活载体真值、客户端 setter 只写镜像并带 isClient 守卫——上游 setValue 的 setSource 默认
        // true，双端都可能被调一次）。写点零改：动作码 13 服务端链改档后 liveMageModeBits 差分变化 ⇒
        // ValueSyncHandler cache 比对推新值 ⇒ 穿戴态的模式按钮首次真正可达。
        syncManager.syncValue(SYNC_MAGE_MODES, new IntSyncValue(this::liveMageModeBits, value -> {
            if (syncManager.isClient()) {
                clientMageModeBits = value;
            }
        }));
    }

    /**
     * ★两根 C2S 通道的<b>入口</b>：只做"换线程"这一件事，判定与落档仍在
     * {@link #onServerAction(int)}（分发）与 {@link NekoPocketServerHandler#onServerGhostRequest(String)}
     * （ghost 执行体，★R90 T2 起住在 handler）。
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
     * 载荷（String）本身已被本方法丢弃，服务端那一跳仍按原样执行
     * {@link NekoPocketServerHandler#onServerGhostRequest(String)}。
     */
    private void receiveGhostRequest(String request) {
        if (syncManager.isClient()) {
            return;
        }
        ServerTaskScheduler.scheduleServerTask(() -> server.onServerGhostRequest(request));
    }

    // ======================================================================================
    // ★R96 S7b · 磁力三态名单的<b>配置面轨</b>（同步载体 / 编解码 / 客户端镜像 / 请求腿 / 格件登记）
    //
    // 分工逐字照 ghost 那一族（★同一形状不留第二份）：<b>判定与落档全在
    // {@link NekoPocketServerHandler} 的 {@code performMagnet*} 里</b>（S7a 已交付执行体），
    // 本段只做三件事：① 把服务端那一份名单编码成一枚字符串下发（{@link #SYNC_MAGNET}）；
    // ② 把客户端那一份镜像交给格件与读数（★客户端一个字节都不写）；③ 把玩家手势换成一条请求。
    // ======================================================================================

    // ★R101.3 文法单源：同步串头部三件套（头长 / 分隔位 / 分隔字符）只在这里出现一份，
    // 编码侧拼头、解码侧验头共读。教训（实锤病史）：R96 S7b（dc7eb39，提交信息自带"未验证"）让
    // encode 发三字符头、decode 却按两字符头验（charAt(1)=='|' 且 charAt(1)∈{P,K}）——两处口径漂移
    // ⇒ 不存在任何串能通过，客户端磁力镜像自引入起从未成功解析（实机 R101.3 判「NEI 拖入无效」的正身）。
    /** 头部长度恒 3：{@code <态字母><目标字母><分隔竖线>}，键段从下标 3 起。 */
    private static final int MAGNET_BLOB_HEADER_LENGTH = 3;
    /** 分隔竖线恒在下标 2（★从头长派生，两处永不漂移）。 */
    private static final int MAGNET_BLOB_SEPARATOR_INDEX = MAGNET_BLOB_HEADER_LENGTH - 1;
    /** 分段竖线（★键里不出竖线与逗号 ⇒ 分段无歧义，论证见 {@link #encodeMagnetBlob} 文法段）。 */
    private static final char MAGNET_BLOB_SEPARATOR = '|';

    /** ★头部文法唯一判定：{@code null} / 长度不足 / 分隔位不对 ⇒ {@code false}（解码据此折成畸形视图）。 */
    private static boolean magnetBlobHeaderOk(String blob) {
        return blob != null && blob.length() >= MAGNET_BLOB_HEADER_LENGTH
            && blob.charAt(MAGNET_BLOB_SEPARATOR_INDEX) == MAGNET_BLOB_SEPARATOR;
    }

    /**
     * 名单 → 同步串（★单枚 {@code StringSyncValue}，预算见 {@code PocketMagnetFilter} 类注释：
     * 满档 ≤1800 B，距 32693 字节墙 ≥18 倍余量）。
     * <p>
     * 文法：{@code <态字母><目标字母>|<键0>,<键1>,…}，键就是 {@link PocketMagnetFilter#itemKey(int, int)}
     * 那枚 {@code i:itemId:meta:}（★逗号与竖线都不出现在键里 ⇒ 分段无歧义；同
     * {@code PocketGhostRequest} 对载荷键的那条论证）。
     * <ul>
     * <li>态字母：{@code N}=无限制 / {@code W}=白名单 / {@code B}=黑名单；</li>
     * <li>目标字母：{@code K}=口袋 135 格栏 / {@code P}=玩家主背包 36 格；</li>
     * <li>★键的<b>顺序</b>就是 {@code PocketMagnetFilter} 里 {@code LinkedHashSet} 的插入序 ⇒ 面板的格序
     * 由服务端定，客户端不重排（R32）。空名单 ⇒ 竖线后一段都没有。</li>
     * </ul>
     */
    public static String encodeMagnetBlob(PocketMagnetFilter filter) {
        final StringBuilder builder = new StringBuilder(64 + PocketConstants.MAGNET_FILTER_SLOTS * 16);
        builder.append(magnetModeLetter(filter == null ? PocketMagnetFilter.Mode.NONE : filter.mode()));
        builder.append(magnetTargetLetter(filter == null ? PocketMagnetFilter.Target.POCKET : filter.target()));
        builder.append(MAGNET_BLOB_SEPARATOR);
        if (filter != null) {
            boolean first = true;
            for (String key : filter.entryKeys()) {
                if (!first) {
                    builder.append(',');
                }
                builder.append(key);
                first = false;
            }
        }
        return builder.toString();
    }

    /** 三态 → 字母（★单源：编码与解码都走这里，不留两份对照表）。 */
    public static char magnetModeLetter(PocketMagnetFilter.Mode mode) {
        return mode == PocketMagnetFilter.Mode.WHITELIST ? 'W' : mode == PocketMagnetFilter.Mode.BLACKLIST ? 'B' : 'N';
    }

    /** 吸取目标 → 字母（同上，单源）。 */
    public static char magnetTargetLetter(PocketMagnetFilter.Target target) {
        return target == PocketMagnetFilter.Target.PLAYER ? 'P' : 'K';
    }

    /** {@link #encodeMagnetBlob} 的产物（★字段直读，用例据此断言"畸形串不会把读数抹成空"）。 */
    public static final class MagnetView {

        public final PocketMagnetFilter.Mode mode;
        public final PocketMagnetFilter.Target target;
        /** 长度恒 = {@code cells}（★格数不随数据伸缩 ⇒ widget 树恒定）；空位是 {@code null}。 */
        public final String[] keys;
        public final int count;
        /** ★头部不合法 / 缺竖线 ⇒ {@code true}（调用方必须<b>保留上一份镜像</b>并说话，不许抹成空）。 */
        public final boolean malformed;

        MagnetView(PocketMagnetFilter.Mode mode, PocketMagnetFilter.Target target, String[] keys, int count,
            boolean malformed) {
            this.mode = mode;
            this.target = target;
            this.keys = keys;
            this.count = count;
            this.malformed = malformed;
        }

        static MagnetView malformed(int cells) {
            return new MagnetView(
                PocketMagnetFilter.Mode.NONE,
                PocketMagnetFilter.Target.POCKET,
                new String[cells],
                0,
                true);
        }
    }

    /**
     * 同步串 → 视图。★越界/畸形一律给 {@link MagnetView#malformed}，<b>不抛</b>（外来包不该炸掉界面），
     * 也<b>不静默当成空名单</b>（{@code applyMagnetBlob} 据此保留上一份并打日志）。
     * 超出 {@code cells} 的尾部键被丢弃并计入 {@code count} ⇒ 读数是"服务端一共几条"，与盘面格数分离。
     * <p>
     * ★★病史（R101.3 实锤，钉在这里防复发）：头判定在 R96 S7b（dc7eb39，提交信息自带"未验证"）被写成
     * {@code charAt(1)=='|'} 且目标字母也取 {@code charAt(1)}——而 {@link #encodeMagnetBlob} 发的是三字符头
     * {@code <态字母><目标字母>|}，竖线在下标 2 ⇒ 两条件互斥，<b>不存在任何串能通过本函数</b>。
     * 后果链：服务端写链完好、客户端镜像自 R96 S7b 起从未刷新（计数恒 0/72、72 格恒无图标、三态/目标恒默认档），
     * 同一件第二次拖入触发 DUPLICATE 回执（"这一件已经在名单里了"），实机被读成「NEI 拖入无效」；
     * 且全仓唯一覆盖是文本域门（compose 区含 {@code encodeMagnetBlob(}），无任何往返行为用例——
     * 「文法漂移无人抓」。R101.3 修复 = 头判定与编码同一口径（{@link #magnetBlobHeaderOk} 三字符头、
     * 键段从下标 3 起），模式/目标字母表也收进两对单源函数（反查）；往返用例
     * {@code magnet_blob_roundtrip_grammar_and_malformed} 钉住四类输入。
     */
    public static MagnetView decodeMagnetBlob(String blob, int cells) {
        if (!magnetBlobHeaderOk(blob)) {
            return MagnetView.malformed(cells);
        }
        final PocketMagnetFilter.Mode mode = magnetModeOfBlobLetter(blob.charAt(0));
        final PocketMagnetFilter.Target target = magnetTargetOfBlobLetter(blob.charAt(1));
        if (mode == null || target == null) {
            return MagnetView.malformed(cells);
        }
        final String[] keys = new String[cells];
        final String tail = blob.substring(MAGNET_BLOB_HEADER_LENGTH);
        int count = 0;
        if (!tail.isEmpty()) {
            final String[] parts = tail.split(",");
            for (String part : parts) {
                if (PocketMagnetFilter.parseEntry(part) == null) {
                    // ★单条解不出 = 整串不认识（自家编码器不会产出这种东西）⇒ 按畸形处理，不半收半丢
                    return MagnetView.malformed(cells);
                }
                if (count < cells) {
                    keys[count] = part;
                }
                count++;
            }
        }
        return new MagnetView(mode, target, keys, count, false);
    }

    /**
     * 字母 → 三态（不认识 ⇒ {@code null}，由 {@link #decodeMagnetBlob} 折成畸形视图）。
     * ★R101.3 单源强化：合法字母表从 {@link #magnetModeLetter} 那一份<b>反查</b>，不留第二份对照
     * （两份对照漂移的病史见 {@link #decodeMagnetBlob}）。
     */
    public static PocketMagnetFilter.Mode magnetModeOfBlobLetter(char letter) {
        for (final PocketMagnetFilter.Mode mode : PocketMagnetFilter.Mode.values()) {
            if (magnetModeLetter(mode) == letter) {
                return mode;
            }
        }
        return null;
    }

    /** 字母 → 吸取目标两档（同上，反查单源）。 */
    public static PocketMagnetFilter.Target magnetTargetOfBlobLetter(char letter) {
        for (final PocketMagnetFilter.Target target : PocketMagnetFilter.Target.values()) {
            if (magnetTargetLetter(target) == letter) {
                return target;
            }
        }
        return null;
    }

    /**
     * 同步 getter（★双端都会被调，同 {@link #composeEssenceBlob} 那一条 R85 P2 口径）：
     * 服务端<b>不每 tick 解 NBT</b> —— 名单的唯一写点在 handler，写完后调
     * {@link #invalidateMagnetSnapshot()}，于是这一格只在"真变过 / 换了载体"时重算一次。
     */
    private String composeMagnetBlob() {
        if (syncManager.isClient()) {
            return clientMagnetBlob;
        }
        final ItemStack carrier = carrierStackLive();
        if (carrier != magnetSnapshotCarrier || serverMagnetBlob == null) {
            magnetSnapshotCarrier = carrier;
            serverMagnetBlob = encodeMagnetBlob(
                PocketMagnetFilter.readFrom(carrier == null ? null : carrier.getTagCompound()));
        }
        return serverMagnetBlob;
    }

    /** 服务端：让快照失效（★名单唯一的写点 {@code commitMagnetFilter} 之后必须调，漏调 = 客户端永远看不到变化）。 */
    void invalidateMagnetSnapshot() {
        magnetSnapshotCarrier = null;
        serverMagnetBlob = null;
    }

    /**
     * 客户端：把服务端下发的名单换进镜像，再<b>原位</b>刷 72 格内容层（★格数与树都不变，R41b/R32）。
     * <p>
     * ★畸形支<b>保留上一份</b>并打一条 debug：把读数抹成"名单空了"是谎报，而留着旧值至少还是
     * 上一次的真话（同 {@code PocketMagnetFilter#readFrom} 那条"不静默、不炸档"的裁定）。
     */
    private void applyMagnetBlob(String blob) {
        if (!syncManager.isClient()) {
            return;
        }
        final MagnetView view = decodeMagnetBlob(blob, clientMagnetKeys.length);
        if (view.malformed) {
            GTInterestingThing.LOG.debug("[pocket] 磁力名单同步串不认识，保留上一份镜像：{}", blob);
            return;
        }
        clientMagnetBlob = blob == null ? "" : blob;
        clientMagnetMode = view.mode;
        clientMagnetTarget = view.target;
        clientMagnetCount = view.count;
        System.arraycopy(view.keys, 0, clientMagnetKeys, 0, clientMagnetKeys.length);
        applyMagnetCells();
    }

    /** ★开屏播种：双端都从<b>同一枚载体 NBT</b>出发（之后每一次变化都由服务端推 ⇒ 不存在两端各算各的）。 */
    private void seedMagnetTracks() {
        final PocketMagnetFilter seeded = PocketMagnetFilter.readFrom(pocket == null ? null : pocket.getTagCompound());
        final String blob = encodeMagnetBlob(seeded);
        serverMagnetBlob = blob;
        magnetSnapshotCarrier = pocket;
        final MagnetView view = decodeMagnetBlob(blob, clientMagnetKeys.length);
        clientMagnetBlob = blob;
        clientMagnetMode = view.mode;
        clientMagnetTarget = view.target;
        clientMagnetCount = Math.min(view.count, clientMagnetKeys.length);
        System.arraycopy(view.keys, 0, clientMagnetKeys, 0, clientMagnetKeys.length);
    }

    /** 72 格内容层的原位刷新（登记表可能为空：次级面板尚未装配 ⇒ 逐项判空，★不抛）。 */
    void applyMagnetCells() {
        for (int index = 0; index < magnetCells.length; index++) {
            final NekoMagnetGhostCell widget = magnetCells[index];
            if (widget != null) {
                widget.setEntryKey(clientMagnetKeys[index]);
            }
        }
    }

    /** 名单格件的登记（次级面板装配期逐格调，★只在客户端发生；下标 = 格号 = 插入序位置）。 */
    void trackMagnetCell(int index, NekoMagnetGhostCell widget) {
        if (index >= 0 && index < magnetCells.length) {
            magnetCells[index] = widget;
        }
    }

    /**
     * 第 {@code index} 格的身份键（★双端：服务端现读档、客户端读镜像；越界 ⇒ {@code null}）。
     * <p>
     * 与 {@link #tankAmountTruth(int)} 同一条双源纪律 —— ★不在客户端读 {@code inventory}（那份只是开屏快照）。
     */
    String magnetEntryKeyAt(int index) {
        if (index < 0 || index >= clientMagnetKeys.length) {
            return null;
        }
        if (syncManager.isClient()) {
            return clientMagnetKeys[index];
        }
        final java.util.List<String> keys = magnetFilterNow().entryKeys();
        return index < keys.size() ? keys.get(index) : null;
    }

    /** 三态读数（双源，同 {@link #magnetEntryKeyAt(int)}）。 */
    PocketMagnetFilter.Mode magnetMode() {
        return syncManager.isClient() ? clientMagnetMode : magnetFilterNow().mode();
    }

    /** 吸取目标两档读数（双源）。 */
    PocketMagnetFilter.Target magnetTarget() {
        return syncManager.isClient() ? clientMagnetTarget : magnetFilterNow().target();
    }

    /** 名单条数读数（★服务端那条是权威条数，不是客户端非空格的计数）。 */
    int magnetEntryCount() {
        return syncManager.isClient() ? clientMagnetCount : magnetFilterNow().size();
    }

    /** 服务端现读那一份名单（★只在服务端出图路径之外被问：本方法解一次 NBT，客户端永不调它）。 */
    private PocketMagnetFilter magnetFilterNow() {
        final ItemStack carrier = carrierStackLive();
        return PocketMagnetFilter.readFrom(carrier == null ? null : carrier.getTagCompound());
    }

    /** 玩家游标栈（★只读；磁力那条腿<b>绝不</b>清它，理由见 {@link NekoMagnetGhostCell} 类注释★★段 2）。 */
    ItemStack magnetCursorStack() {
        return syncManager.getCursorItem();
    }

    /**
     * 磁力栏是否可编辑（★未固化 ⇒ 灰显且不收任何手势，与 R31"整栏灰显不隐藏"同一条口径）。
     * <p>
     * ★这里刻意<b>不</b>用"开关是否开着"当判据：开关关掉时名单仍然应当能配（玩家就是不想吸的时候来改名单），
     * 而"有没有这一型"才是"这块面板有没有这一栏"的判据 —— 读的是 S2 那条三态读数口（同一份真相）。
     */
    boolean magnetEditable() {
        return upgradeSwitchState(PocketUpgradeType.MAGNET) != PocketConfigPanel.SwitchState.ABSENT;
    }

    // ------------------------------------------- 客户端请求腿（★一个字节都不写档，只发码/发键）

    /** ★三态循环按钮的唯一出口（发码；目标态由服务端读现态 {@code next()} 推 ⇒ 伪造包跳不到指定态）。 */
    boolean requestMagnetModeCycle() {
        return sendAction(ACTION_MAGNET_MODE_CYCLE, 0);
    }

    /** ★吸取目标两档按钮的唯一出口（发码，同上）。 */
    boolean requestMagnetTargetCycle() {
        return sendAction(ACTION_MAGNET_TARGET_CYCLE, 0);
    }

    /** ★清空名单按钮的唯一出口（发码；★只抹条目、不动三态）。 */
    boolean requestMagnetClear() {
        return sendAction(ACTION_MAGNET_CLEAR, 0);
    }

    /** ★按格号摘一条（arg = 格号；★服务端用它自己那份键，不吃客户端抄上来的键，R18/R19）。 */
    boolean requestMagnetEntryRemoveAt(int index) {
        return index >= 0 && index < PocketConstants.MAGNET_FILTER_SLOTS && sendAction(ACTION_MAGNET_REMOVE_AT, index);
    }

    /**
     * ★加一条名单（NEI 拖入 / 游标持物点格两条手势共用的唯一出口）。
     * <p>
     * ★客户端在这里<b>不</b>判"名单满不满"、也<b>不</b>判"这条在不在里面"（那是服务端
     * {@code PocketMagnetFilter#addEntry} 的活），本方法的判据只有"键解不解得出"，
     * 而那个判据住在 {@link NekoMagnetGhostCell#identityKeyOf} 那一条纯函数里（两条手势共读一份）。
     * ★★发完<b>不动游标、不动原件件数</b>（本片验收 2 的两条相反语义，逐字写在格件类注释）。
     */
    boolean requestMagnetEntryAdd(String key) {
        if (key == null || key.isEmpty()) {
            return false;
        }
        final String request = magnetAddRequest(key);
        if (syncManager.isClient()) {
            syncManager.findSyncHandler(SYNC_MAGNET_REQUEST, StringSyncValue.class)
                .setValue(request);
        } else {
            server.onServerMagnetRequest(request);
        }
        return true;
    }

    /** 请求文法的拼装（★单源：客户端发与服务端解读的是同一条式子，同 {@code PocketGhostRequest.setRequest}）。 */
    public static String magnetAddRequest(String key) {
        return MAGNET_REQUEST_ADD + '|' + key;
    }

    /** 请求文法里"加一条"的操作码。 */
    private static final String MAGNET_REQUEST_ADD = "ADD";

    /**
     * ★服务端：解 {@link #MAGNET_REQUEST_ADD} 那条请求的键段。
     * 不认识（缺操作码 / 段数不对 / 键解不出）⇒ 空串，调用方丢弃这条包并回一条说法（★不静默）。
     */
    public static String magnetAddKeyOf(String request) {
        if (request == null) {
            return "";
        }
        final int bar = request.indexOf('|');
        if (bar <= 0 || !request.substring(0, bar)
            .equals(MAGNET_REQUEST_ADD)) {
            return "";
        }
        final String key = request.substring(bar + 1);
        return PocketMagnetFilter.parseEntry(key) == null ? "" : key;
    }

    /** 见 {@link #receiveGhostRequest(String)} 的同一条 R85 N1 客户端守卫：allowC2S 的 setter 双端都被调。 */
    private void receiveMagnetRequest(String request) {
        if (syncManager.isClient()) {
            return;
        }
        ServerTaskScheduler.scheduleServerTask(() -> server.onServerMagnetRequest(request));
    }

    /** 见 {@link #receiveGhostRequest(String)} 的同一条 R85 N1 客户端守卫：allowC2S 的 setter 双端都被调。 */
    private void receiveFreqRequest(String request) {
        if (syncManager.isClient()) {
            return;
        }
        ServerTaskScheduler.scheduleServerTask(() -> server.onServerFreqRequest(request));
    }

    // ------------------------------------------------------------------ ★R101 频率秒值请求的文法（拼装与解读共用同一条式子）

    /** 请求文法里"提交频率秒值"的操作码（★单段载荷 = 正整数秒，域钳制在 PocketConstants）。 */
    private static final String FREQ_REQUEST_SET = "FREQ";

    /**
     * 请求文法的拼装（★单源：客户端发与服务端解读的是同一条式子，同 {@code magnetAddRequest}）。
     * {@code FREQ|<秒数>}；非法秒值（越域）也在拼装侧拒绝（回空串，调用方不发）。
     */
    public static String freqSecondsRequest(int seconds) {
        if (PocketConstants.channelFreqSecondsClamp(seconds) != seconds) {
            return "";
        }
        return FREQ_REQUEST_SET + '|' + seconds;
    }

    /**
     * ★服务端：解 {@link #FREQ_REQUEST_SET} 那条请求的秒值段。
     * 不认识（缺操作码 / 段数不对 / 非数字 / 越域）⇒ −1，调用方丢弃这条包（★静默：伪造包不买到回执，
     * 口径同 {@code performUpgradeModeToggle} 的解越支）。
     */
    public static int freqSecondsOf(String request) {
        if (request == null) {
            return -1;
        }
        final int bar = request.indexOf('|');
        if (bar <= 0 || !request.substring(0, bar)
            .equals(FREQ_REQUEST_SET)) {
            return -1;
        }
        final int seconds = PocketConfigPanel.parseFreqSecondsInput(request.substring(bar + 1));
        return seconds > 0 ? seconds : -1;
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

    /**
     * ★R95 S4：承载口袋的<b>活查表</b>（{@code PlayerInventoryGuiData#getUsedItemStack()} 现读背包，
     * 与构造器取 {@code this.pocket} 同一条路——见 {@link #carrierStillPresent} 头部"判据不得用对象
     * 身份"那条：堆叠合并 / 跨维重建会让缓存引用变陈旧）。升级格固化写点
     * （{@code NekoPocketBottomBand#upgradeCellWidget} 的 changeListener）与 CHANNEL_PERSIST 的
     * 客户端读数都从这里取栈：服务端读到的是权威栈本体（install 原地写它的 NBT），客户端读到的是
     * vanilla 槽同步过来的那份镜像（{@code ItemStack} 相等比较含整份 NBT ⇒ 位图变化 ≤1 tick 内到达）。
     */
    ItemStack carrierStackLive() {
        return data.getUsedItemStack();
    }

    /**
     * ★R95 S4：载体上的「通道持续化」<b>当前生效没有</b>（★R96 S2 起判据是组合谓词
     * {@link PocketUpgradeSwitches#isActive} = 位图 ∧ 未关闭；双端各读自己那份载体栈：服务端权威、
     * 客户端 vanilla 同步镜像）。两个消费面：通道按钮的客户端禁用腿（早退不发包 + tooltip 注记）
     * 与说明块的倒计时替代文案（{@link #channelStatusText}）。
     * <p>
     * ★这条腿是 R96 S2 收口的六处旁路之一：留成位图直读 ⇒ 玩家在配置面板里把常开关掉，
     * 按钮依旧"已在常开态"早退，而 {@code PocketChannelDriver} 那一侧已经按开关停了回满 ⇒
     * <b>通道既不续批也不能手开</b>，两头都不通（比"开关无效"更坏）。
     * <p>
     * ★★★<b>R96 S5 补第二问（验收 B4：假读数门）</b>：这一判现在除位图外<b>再问一次"有活通道在场"</b>
     * （唯一判据落在 {@link ItemNekoDimensionPocket#isChannelWorkLive}，读的是 driver 每批边界续写的
     * {@code UI_WORK_TICKS} —— 那条同步到客户端的真读数；为什么不问 {@code PocketChannelManager#peek}
     * 的理由原文写在那里）。★一次改口三处同时闭：状态行的"通道常开中"、通道按钮 tooltip 的注记、
     * 以及客户端吞点击那一腿，全都要么一起真、要么一起假，不留第二处口径。
     * ★副作用是<b>期望的</b>：位在场而通道没起来（从没开过界面 / 一枚元件都没绑）时按钮不再被吃掉，
     * 玩家那一按会走到服务端常开支，由同一个激活口把道起起来（★仍不扣费、仍不进冷却）。
     */
    boolean channelPersistActive() {
        return PocketUpgradeSwitches.isActive(carrierStackLive(), PocketUpgradeType.CHANNEL_PERSIST)
            && ItemNekoDimensionPocket.isChannelWorkLive(carrierStackLive());
    }

    // ------------------ ★R95 S5：升级位的面板读口（双端各读自己那份载体：服务端权威、客户端 vanilla 镜像；
    // ★R96 S2 起三个读口统一走 PocketUpgradeSwitches.isActive，位图直读在 GUI 侧归零）

    /** CAPACITY 位是否<b>生效</b>（流体条 20M/2G；流体格件的步进/天花板读它）。 */
    private int liveUpgradeInstalledBits() {
        int bits = 0;
        for (PocketUpgradeType type : PocketUpgradeType.values()) {
            if (PocketUpgrades.hasUpgrade(carrierStackLive(), type)) {
                bits |= 1 << type.ordinal();
            }
        }
        return bits;
    }

    public boolean upgradeInstalledNow(PocketUpgradeType type) {
        return syncManager.isClient() ? (clientUpgradeInstalledBits & (1 << type.ordinal())) != 0
            : PocketUpgrades.hasUpgrade(carrierStackLive(), type);
    }

    public PocketConfigPanel.SwitchState upgradeSwitchStateNow(PocketUpgradeType type) {
        if (!upgradeInstalledNow(type)) {
            return PocketConfigPanel.SwitchState.ABSENT;
        }
        final boolean active = syncManager.isClient() ? clientUpgradeActive(type)
            : PocketUpgradeSwitches.isActive(carrierStackLive(), type);
        return active ? PocketConfigPanel.SwitchState.ON : PocketConfigPanel.SwitchState.OFF;
    }

    public int installedCapacityUpgradeCount() {
        return syncManager.isClient() ? clientCapacityUpgradeCount
            : PocketUpgrades.capacityUpgradeCount(carrierStackLive());
    }

    public int capacityUpgradeCountNow() {
        return capacityUpgradeActiveNow() ? installedCapacityUpgradeCount() : 0;
    }

    boolean capacityUpgradeActiveNow() {
        return syncManager.isClient() ? clientUpgradeActive(PocketUpgradeType.CAPACITY)
            : PocketUpgradeSwitches.isActive(carrierStackLive(), PocketUpgradeType.CAPACITY);
    }

    /**
     * ★★<b>R97 R6：五型「当前生效」位图 = installed ∧ ¬off</b>（逐型问 {@code PocketUpgradeSwitches}
     * 那一<b>条</b>组合谓词真值，不抄第二份位运算）。
     * <p>
     * 三个读者：{@code SYNC_UPGRADE_ACTIVE} 的服务端 getter（每拍差分比对，变了才推）、
     * 客户端镜像的<b>开屏播种</b>（与旧读法的开屏值同源 ⇒ 首帧不回退）、以及
     * {@code IntSyncValue} 构造期的 cache 初始化（客户端那一跳经本方法读到的就是播种值）。
     */
    int liveUpgradeActiveBits() {
        int bits = 0;
        for (PocketUpgradeType type : PocketUpgradeType.values()) {
            if (PocketUpgradeSwitches.isActive(carrierStackLive(), type)) {
                bits |= 1 << type.ordinal();
            }
        }
        return bits;
    }

    /**
     * ★R97 R6：客户端镜像的位读取口（{@code clientUpgradeActiveBits} 的唯一读法；服务端没有写者，
     * 服务端读升级态一律走 {@code PocketUpgradeSwitches.isActive} 现读活载体）。
     */
    boolean clientUpgradeActive(PocketUpgradeType type) {
        return (clientUpgradeActiveBits & (1 << type.ordinal())) != 0;
    }

    /**
     * ★★<b>R106 D2</b>：魔法使三条子模式位图的<b>服务端真值</b>（bit 域 =
     * {@code PocketConstants#MAGE_MODE_BITS}，值 = 活查表载体的 {@code PocketElementStore#modeMask()}）。
     * <p>
     * 三个读者与 {@link #liveUpgradeActiveBits} 同族：{@code SYNC_MAGE_MODES} 的服务端 getter
     * （每拍差分比对，变了才推）、客户端镜像的开屏播种（与旧读腿 {@code PocketConfigPanel#modeState}
     * 的开屏值同源 ⇒ 首帧不闪变）、以及双源 accessor {@link #mageModeOnNow(int)} 的服务端支。
     * 缺键 ⇒ {@code MAGE_MODES_DEFAULT}（与旧读腿「缺键默认读数」的显示语义同值）；载体缺席 ⇒ 0
     * 全关（对齐旧 {@code modeState(null) == false}）。
     */
    int liveMageModeBits() {
        final ItemStack carrier = carrierStackLive();
        if (carrier == null) {
            return 0;
        }
        return PocketElementStore.attach(carrier.getTagCompound())
            .modeMask();
    }

    /**
     * ★R106 D2：某一模式行<b>当前</b>开不开——双源 accessor（R86 铁律）：客户端读
     * {@link #clientMageModeBits} 镜像（穿戴态唯一真读数）、服务端读 {@link #liveMageModeBits()} 真值。
     * 行→位经 {@code PocketConfigPanel#modeBit} 单源；非法行 ⇒ false。
     */
    boolean mageModeOnNow(int row) {
        final int bit = PocketConfigPanel.modeBit(row);
        if (bit == 0) {
            return false;
        }
        final int mask = syncManager.isClient() ? clientMageModeBits : liveMageModeBits();
        return (mask & bit) != 0;
    }

    /**
     * ★R106-②：MAGE 主开关<b>当前生效没有</b>——双源 accessor（客户端读 {@link #SYNC_UPGRADE_ACTIVE}
     * 镜像 {@link #clientUpgradeActive}，服务端读活载体真值）。模式行的「未生效」第三态与 tooltip
     * 追加行都读它，不在 {@code PocketConfigPanel} 里直点名这一型（门 F / 「型 == MAGE」两张既有门
     * 都钉着本件不养第二份型清单）。
     */
    boolean mageMasterActiveNow() {
        if (syncManager.isClient()) {
            return clientUpgradeActive(PocketUpgradeType.MAGE);
        }
        return PocketUpgradeSwitches.isActive(carrierStackLive(), PocketUpgradeType.MAGE);
    }

    /** 单 tank 当前容量（mB，long；CAPACITY 位现读 ⇒ 会话期内固化即换档）。 */
    long fluidTankCapacityNow() {
        return PocketConstants.fluidTankCapacityMl(capacityUpgradeCountNow());
    }

    /**
     * ★R96 S2：某一型当前的开关三态（配置面板与升级格 tooltip 的<b>共用读数口</b>）。
     * <p>
     * 服务端读取权威载体；客户端读取已安装/生效同步镜像，穿戴态开屏也能及时更新。
     * 三态而不是两态：没装的型不许显示成"已关闭"（那是"还能开回来"的错误暗示）。
     */
    PocketConfigPanel.SwitchState upgradeSwitchState(PocketUpgradeType type) {
        return upgradeSwitchStateNow(type);
    }

    /**
     * 第 {@code tank} 号流体槽的 <b>long 真值</b>（mB）：服务端读权威 inventory，客户端读
     * {@link #clientTankAmounts} 镜像（每 tank 一根 LongSyncValue 推来的；★不在客户端读
     * inventory——那份只是开屏快照，与 {@code essenceTagAtCell} 的双源纪律同一条）。
     */
    long tankAmountTruth(int tank) {
        if (tank < 0 || tank >= clientTankAmounts.length) {
            return 0L;
        }
        return syncManager.isClient() ? clientTankAmounts[tank] : inventory.tankTruthAt(tank);
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

    /** 需求 5 的绑定键（服务端落点归 S6，见 {@link NekoPocketServerHandler#performBind()}）。 */
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
     * 扣点/物化/落点判定全在服务端 {@link NekoPocketServerHandler#performEssenceOut}（R18/R19）。
     */
    boolean requestEssenceOut(int cell, String tag, boolean shift) {
        // ★R83：不再在客户端拿 tag 判空后静默早退 —— 服务端 performEssenceOut 本就会按格号反查归属并
        // 给"无事发生"回执（R18/R19：客户端字符串一律不可信）。两处各判一次就是两处真相，且客户端这
        // 一处连回执都不发，玩家看到的是"点了一下，什么都没发生"。
        return sendAction(ACTION_ESSENCE_OUT, shift ? cell + PocketConstants.ESSENCE_OUT_SHIFT_FLAG : cell);
    }

    /**
     * ★R90 E3（D1 手势三分）的<b>分流口</b>：左键点源质格时按<b>游标持物</b>分派——
     * <ol>
     * <li><b>空瓶</b>（{@link PocketEssenceIntake#isEmptyPhialCarrier}，判据单源）⇒ 新「格→瓶取出」
     * {@link #requestEssenceOutToPhial(int, boolean)}；</li>
     * <li><b>满瓶 / 晶</b> ⇒ 既有 {@link #requestEssenceIntake(int)}（其容量档位预筛原样不动）；</li>
     * <li><b>空游标 / 其他东西</b> ⇒ {@code false} 交回 {@code super} ⇒ 装配侧的
     * {@code requestEssenceOut}（★R91-④ 后那条动作码经 {@code performEssenceOut} 转发到<b>同一条</b>取出
     * 单点 ⇒ 手上没有空瓶就是"零产出 + {@code essence.need_phial} 回执"，不再有凭空造瓶的第二支）。</li>
     * </ol>
     * 「容器内容非空」预筛就落在这条分流上（任务口径：入槽支只收内容非空的载体；空瓶是其中
     * 唯一的瓶档 ⇒ 单独改派取出），客户端这一道仍只是<b>预筛</b>，服务端
     * {@code performEssenceOutToPhial} / {@code performEssenceIntake} 各自复验，两侧读同一条谓词。
     * ★R91-e 已把 shift 位接上（见本方法下方那一段 javadoc 与 {@code essenceOutShiftBitIsCarried} 用例）：
     * 分流口读 {@code Interactable.hasShiftDown()} 并折进 {@code packedArg}，★批量支因此可达。
     * <p>
     * ★L1（[PocketR89]，D1/D3 裁决检查点）：入口读数（持瓶类型 / 游标 meta / 命中格）在此打——
     * debug 级每次都记（不构成刷屏面），首个分流样本升 INFO 一次（终验可直接在常规 jar 里读到）。
     */
    /**
     * ★★<b>R91-e（裁 S2 交回的第一处）+ R91-⑤⑧b 的连带缺陷修复</b>：取出量按<b>shift 二分</b>——
     * <b>左键 = 消耗 1 只空瓶、装 1 只</b>；<b>shift+左键 = 装到既有单动作上限</b>
     * （{@code ESSENCE_OUT_MAX_POINTS_PER_ACTION}，★不新增常量、不新增第二套算式）。
     * <p>
     * ★★<b>本片补的就是"shift 位"这一位</b>：S2 的取出单点 {@link #performEssenceOutToPhial(int)}
     * <b>早已</b>按 {@code packedArg} 自己解析 shift（两条动作码共用同一个单点），缺的只是这里
     * 把 {@link Interactable#hasShiftDown()} 折进 arg ⇒ 补上之前<b>批量支永不可达</b>
     * （手持一叠空瓶 + Shift 仍按"一次动作上界"结算，S2 已在 {@code r91-s2-panel-todo.md} §2.1 显式登记）。
     * ★服务端<b>一字不用改</b>：算式只在 {@code PocketEssenceIntake#phialsToFill} 那一处。
     * <p>
     * ★同时把"哪些左键手势走哪条支"的注释口径改到与 R91-⑤ 一致：<b>alt 系三个手势
     * （中键 / alt+左 / alt+右）都归属性层</b>（{@code NekoEssenceGhostCell#onMousePressed} 里排在
     * 本分流<b>之前</b>），因此本方法只在<b>非 alt</b> 的左键上被调；alt+左 从此不再是"绑本格存量"
     * （那个语义已迁到<b>中键 = BIND</b>），而是挂记忆 {@code L}。
     */
    boolean dispatchEssenceCellPress(int cell) {
        return dispatchEssenceCellPress(cell, false);
    }

    /**
     * ★★<b>R92-④（D4）：多带一个 {@code declared}（本格是否已有声明）</b> —— 旧形状在格件那里用
     * {@code !ghost} 把已声明格的<b>整个</b>左键分流关掉，于是"放满瓶进去配置记忆档"这一条连入口都没有。
     * 本号把闸从"整条分流"收窄到"<b>只关取出向</b>"：入槽向（游标持内容非空的载体）在已声明格上放开。
     * ★取出向在 {@code declared} 时返回 false ⇒ 交回 {@code super}，与 R91-④ 的现状<b>逐字同行为</b>
     * （本号不新增"从已声明格直接掏瓶"这条面）。
     */
    boolean dispatchEssenceCellPress(int cell, boolean declared) {
        final ItemStack carried = syncManager.getCursorItem();
        if (carried == null || carried.stackSize <= 0) {
            return false;
        }
        // ★R91-e：shift 位在这一行折进 arg（旧写法把它丢了 ⇒ 批量支不可达）
        final boolean shift = Interactable.hasShiftDown();
        final String branch;
        final boolean handled;
        if (PocketEssenceIntake.isEmptyPhialCarrier(carried, EssenceGate.TAUM)) {
            if (declared) {
                return false;
            }
            branch = "out-to-phial";
            handled = requestEssenceOutToPhial(cell, shift);
        } else {
            branch = PocketEssenceIntake.carriesEssence(carried, EssenceGate.TAUM) ? "intake" : "intake-empty-content";
            handled = requestEssenceIntake(cell);
        }
        logOnce(
            "L1-cell-press:" + branch,
            "[PocketR89] L1 源质格左键分流：格 {}（tag={}）游标 {}x{} meta={} shift={} ⇒ {} 支（本分支首例升 INFO，后续 debug）",
            cell,
            essenceTagAtCell(cell),
            carried.stackSize,
            carried.getUnlocalizedName(),
            carried.getItemDamage(),
            Boolean.valueOf(shift),
            branch);
        GTInterestingThing.LOG.debug(
            "[PocketR89] L1 源质格左键分流：格 {}（tag={}）游标 {}x{} meta={} shift={} ⇒ {}",
            cell,
            essenceTagAtCell(cell),
            carried.stackSize,
            carried.getUnlocalizedName(),
            carried.getItemDamage(),
            Boolean.valueOf(shift),
            branch);
        return handled;
    }

    /**
     * ★R90 E3（D1）：客户端入口——「格→瓶取出」只发码；扣点 / 物化 / 游标结算全在服务端
     * {@link #performEssenceOutToPhial(int)}（R18/R19：客户端一律不算真值；与 {@link #requestEssenceOut}
     * 同形，不在客户端预判 stock——服务端会按格号反查归属并给"凑不满一瓶"回执）。
     * <p>
     * ★R91-e：{@code shift} 折进 arg 的 {@link PocketConstants#ESSENCE_OUT_SHIFT_FLAG} 位 ⇒
     * 单点里 <b>左键 = 1 只、shift = 既有单动作上界</b> 两条支走<b>同一段</b>代码（★不开第二支）。
     */
    boolean requestEssenceOutToPhial(int cell, boolean shift) {
        return sendAction(ACTION_ESSENCE_OUT_TO_PHIAL, shift ? cell + PocketConstants.ESSENCE_OUT_SHIFT_FLAG : cell);
    }

    /**
     * ★R87-d 客户端入口（★R90 E3 起由 {@link #dispatchEssenceCellPress} 分派抵达）：游标栈<b>是可点进
     * 源质盘的载体</b>才发码（TC 缺席不拦截）；判定、入账与清游标全在服务端，客户端不自改游标
     * （★R88 B1：清游标的唯一正解是服务端 {@code syncManager.setCursorItem(null)}，旧注释"原版 cursor
     * 同步送达"已被上游行号证伪）。
     * <p>
     * ★<b>R88 门禁换档</b>：不再自己写"== 某一档"的字面判据，而是<b>逐字复用 E1 落地的载体谓词</b>
     * {@link PocketEssenceIntake#isAcceptedCarrierCapacity(int)}——它收<b>瓶</b>（现役载体）与<b>旧晶</b>
     * （自立口径 C2 的只读支：仍进得了账，但本仓不再产出）两档，且第三方罐（{@code CAPACITY_UNKNOWN}）
     * 刻意不收。客户端这一道只是<b>预筛</b>（省一次无谓 C2S），服务端 {@code intake} 仍是唯一执法者，
     * 两边读的是同一条谓词 ⇒ 不会出现"客户端放行 / 服务端拒"或反向的两处真相。
     * 预筛不过时把事件交回 {@code super} ⇒ 走既有取出支，服务端会用"游标已被占用"回执说话，不静默。
     * <b>空瓶不再走到这里</b>（分流层已改派格→瓶取出）；无 NBT 裸晶仍会发码并在服务端收
     * {@code no_owner} 粘性回执（既有反馈面，不静默）。
     */
    boolean requestEssenceIntake(int cell) {
        final ItemStack carried = syncManager.getCursorItem();
        // ★L2（[PocketR89]）：预筛判据读数——持物档位 / 内容是否非空 / 是否真的发了码（D1 的"空瓶空转"裁决样本）
        GTInterestingThing.LOG.debug(
            "[PocketR89] L2 入槽预筛：格 {} 游标 {} meta={} 档位={} 内容非空={} ⇒ 发码={}",
            cell,
            carried == null ? "空" : carried.getUnlocalizedName() + "x" + carried.stackSize,
            carried == null ? -1 : carried.getItemDamage(),
            carried == null ? TaumDistillRules.CAPACITY_NOT_A_CONTAINER : EssenceGate.TAUM.capacityOf(carried),
            PocketEssenceIntake.carriesEssence(carried, EssenceGate.TAUM),
            carried != null && carried.stackSize > 0
                && PocketEssenceIntake.isAcceptedCarrierCapacity(EssenceGate.TAUM.capacityOf(carried)));
        return carried != null && carried.stackSize > 0
            && PocketEssenceIntake.isAcceptedCarrierCapacity(EssenceGate.TAUM.capacityOf(carried))
            && sendAction(ACTION_ESSENCE_INTAKE, cell);
    }

    /**
     * ★R96 S2：配置面板里那枚开关的唯一出口（<b>客户端只发码，一个字节都不写本地 NBT</b>）。
     * <p>
     * 发的是<b>目标值</b>（{@code wantOff}）而不是"翻一下"：重复包打到同一目标 ⇒ 服务端走
     * {@code NO_CHANGE} 支零写入，不会把一次点击的包重放成两次翻转。arg 的编解码单源在
     * {@link PocketConfigPanel#encode}，本处不重写第二份。
     * <p>
     * ★不做客户端预筛（不查守卫、不查这一型装没装）：守卫的输入是服务端内存里的 tank 真值与中栏栈，
     * 客户端那份只是开屏快照 + 单独的显示镜像，拿它预筛就是给同一个判据造第二处读数（R39b/R19 的
     * "客户端不得推断服务端事实"同一条纪律）。拒绝与成交都由服务端的粘性回执行告诉玩家。
     */
    boolean requestUpgradeSwitch(PocketUpgradeType type, boolean wantOff) {
        final int arg = PocketConfigPanel.encode(type, wantOff);
        return arg >= 0 && sendAction(ACTION_UPGRADE_SWITCH, arg);
    }

    /**
     * ★R96 S9b：配置面板里那一行<b>模式控件</b>的唯一出口（客户端只发码，★一个字节都不写本地 NBT）。
     * <p>
     * 发的是<b>目标值</b>（{@code wantOn}）而不是"翻一下"，理由与 {@link #requestUpgradeSwitch} 逐字相同：
     * 重复包打到同一目标 ⇒ 服务端 {@code NO_CHANGE} 支零写入。★也不做客户端预筛（不判"魔法使装没装"）——
     * 那两个判据的输入是服务端那份档，客户端预筛就是给同一个判据造第二处读数。
     */
    boolean requestUpgradeMode(int row, boolean wantOn) {
        final int arg = PocketConfigPanel.encodeMode(row, wantOn);
        return arg >= 0 && sendAction(ACTION_UPGRADE_MODE, arg);
    }

    /**
     * ★R101：配置面频率<b>秒值输入框</b>的唯一出口（客户端只发请求，★一个字节都不写本地 NBT；
     * 写腿在服务端 {@code NekoPocketServerHandler#onServerFreqRequest}）。调用方（
     * {@code PocketConfigPanel} 的输入框提交 lambda）只传解析过的合法秒值；服务端仍再钳一遍域
     * （伪造包不买到任何东西）。
     */
    boolean requestChannelFreqSeconds(int seconds) {
        return sendFreqRequest(freqSecondsRequest(seconds));
    }

    private boolean sendFreqRequest(String request) {
        if (syncManager.isClient()) {
            syncManager.findSyncHandler(SYNC_FREQ_REQUEST, StringSyncValue.class)
                .setValue(request);
        } else {
            server.onServerFreqRequest(request);
        }
        return true;
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
     * 第 {@code index} 个升级格的左键：<b>打开这一型的配置面板</b>（★纯客户端手势，不发码、不写档）。
     * <p>
     * ★不在这里判"这一型装没装"以外的任何事，也★不在这里判守卫：开面板不需要服务端同意
     * （写档才需要，那一趟走 {@link #requestUpgradeSwitch}）。
     * <p>
     * ★★R97 S5（P-3 显式翻案）：{@code index}（= 槽号 = 型 ordinal，三个空间同一个数）现在<b>参与面板选择</b>
     * —— 按槽选 {@link #configPanels} 里那一枚 handler，各型各开各的面。签名与调用点零改
     * （{@code UpgradeCellSlot} 传进来的就是格号）。
     * <p>
     * ★★<b>R98 S3（需求 2-a）：五块插件面板之间单开互斥</b> —— 用户口径「同时只能打开一个插件 UI，再打开会关掉旧的」。
     * 互斥范围是<b>这五块插件面板</b>（★不是"整个口袋屏单实例"：主面板由 {@code GuiManager.openFromClient} 覆盖
     * {@code mc.currentScreen}、服务端会话按玩家 UUID 一份，本来就不会叠两层口袋屏）。落点见方法体里那段循环：
     * 开本枚之前，把<b>别枚</b>里真开着的那几枚关掉。三条实现约束（都有库侧出处）：
     * <ol>
     * <li>★<b>不能先全关再开</b>：{@code SecondaryPanel.openPanel()} 首行就是 {@code if (this.open) return;}，
     * 而 {@code open} 只在 {@code closePanelInternal()} 里清（{@code ModularPanel.onClose} 触发，NEA 在场时
     * 关闭还是<b>异步动画</b>）⇒ 同帧"全关 + 开本枚"会把紧随的开启一起吞掉，玩家读到"点了没反应"。
     * 这一支因此严格排除 {@code index}，本枚一个字段都不碰；</li>
     * <li>只关 {@code isPanelOpen()} 为真的那几枚 ⇒ 对"没开的关成 no-op"这条库侧行为免疫，也不依赖关闭是否异步；</li>
     * <li>{@code isPanelOpen()} 是 {@link IPanelHandler} 自带的读数 ⇒ ★不新增"哪枚开着"的镜像状态
     * （那份镜像一旦与真值分叉就会出现关不掉的面板，{@link #closeUpgradeConfig()} 的 javadoc 点名的就是这个风险）。</li>
     * </ol>
     * ★主面板关闭行为一字未动（仍走 {@code MCHelper.popScreen}，本仓从不设 {@code openParentOnClose}）；
     * 插件面板不是 {@code GuiScreen} ⇒ 本改动不涉及 {@code displayGuiScreen}、{@code closeContainer} 与包注销。
     *
     * @param anchor 点击来自哪个槽件（次级面板要挂到它所在的宿主面板上；★装配期取不到，
     *               {@code getPanel()} 那时还是 null，所以由点击现场传进来）
     */
    boolean openUpgradeConfig(int index, IWidget anchor) {
        if (!upgradeCellFilled(index)) {
            // 空格的左键归 vanilla 的"放置"语义（R95 的放入即固化手势走的就是这一支）⇒
            // 这条腿必须把点击交回去，不然"装插件"这个既有动作会被吃掉。
            return false;
        }
        final ModularPanel host = anchor == null ? null : anchor.getPanel();
        if (host == null || index < 0 || index >= configPanels.length) {
            return false;
        }
        final PocketUpgradeType type = PocketUpgradeType.values()[index];
        if (configPanels[index] == null || configPanelHosts[index] != host) {
            configPanels[index] = IPanelHandler
                .simple(host, (parent, player) -> PocketConfigPanel.build(this, type), true);
            configPanelHosts[index] = host;
        }
        // ★★R98 S3（需求 2-a）：<b>五块插件面板之间单开互斥</b> ⇒ 开本枚之前关掉<b>别枚</b>里开着的那几枚。
        // 三条形状约束（理由见本方法 javadoc）：① ★严格排除 {@code index}（先全关会把本枚的 openPanel 一起吞掉
        // —— {@code SecondaryPanel.openPanel()} 首行 {@code if (this.open) return;}，而 {@code open} 只在
        // {@code closePanelInternal()} 里清，NEA 在场时关闭还是异步动画）；② 只关 {@code isPanelOpen()} 为真的
        // 那几枚 ⇒ "没开的关成 no-op"这条库侧行为与是否异步都无所谓；③ 用接口自带的 {@code isPanelOpen()} 读数
        // ⇒ 不在此长出"哪枚开着"的镜像状态（那正是 {@link #closeUpgradeConfig()} 的 javadoc 点名的分叉风险）。
        // ★这一支必须排在两个早退支（空格让位、宿主/型别解算）<b>之后</b>：空格点击不该顺手关掉已经开着的面板。
        for (int i = 0; i < configPanels.length; i++) {
            if (i != index && configPanels[i] != null && configPanels[i].isPanelOpen()) {
                configPanels[i].closePanel();
            }
        }
        configPanels[index].openPanel();
        return true;
    }

    /**
     * 关闭配置面板（★只关面板；关闭不写任何状态，配置面板本身无待提交内容）。
     * <p>
     * ★R97 S5 起有五枚句柄 ⇒ 这里<b>全关</b>而不是记"当前开的那枚"：{@code SecondaryPanel#closePanel}
     * 自带 {@code open} 早退（字节码实证：没开的关成 no-op），逐枚关零副作用，也不需要一份
     * "哪枚开着"的镜像状态（那份镜像一旦与真值分叉，就会出现"关不掉的面板"）。
     */
    boolean closeUpgradeConfig() {
        for (final IPanelHandler handler : configPanels) {
            if (handler != null) {
                handler.closePanel();
            }
        }
        return true;
    }

    /**
     * ★R96 S2：升级格的<b>占用判据</b>（第 {@code index} 格里有没有插件）。
     * <p>
     * 为什么用它而不是再问一次位图：R95 的"放入即固化、不可取出"（{@code PocketSlots#upgradeCell} 的
     * {@code accessibility(true, false)}）保证<b>格里有货 ⇔ 这一型已经固化</b>，而这一判据双端都拿得到
     * （槽内容由 {@code SYNC_UPGRADE} 同步），不需要在 GUI 侧长出第三次 {@code hasUpgrade} 直读
     * （门禁 E 段把全仓总点数钉成 2，见 {@code PocketConfigPanel#switchState} 的说明）。
     */
    boolean upgradeCellFilled(int index) {
        return inventory.upgradeGroup()
            .getStackInSlot(index) != null;
    }

    /**
     * 服务端动作分发。★<b>只在服务器主线程被调用</b>：C2S 包入口 {@link #receiveServerAction(int)}
     * 已按 R71 的字节码证据把执行体投递过 {@link ServerTaskScheduler}（setter 本身在 Netty IO 线程），
     * 本方法内的背包/钱包/通道操作因此不需要再各自防线程。
     * <p>
     * ★R90 T2：除 {@link #performEssenceOutToPhial(int)}（离线套件源码机检锚定方法体位置，留在本类）
     * 之外，各 {@code perform*} 执行体已迁 {@link NekoPocketServerHandler}，本方法只剩"解包 + 分派"。
     */
    private void onServerAction(int packed) {
        if (syncManager.isClient()) {
            return;
        }
        final int code = packed / ACTION_ARG_BASE;
        final int arg = packed % ACTION_ARG_BASE;
        switch (code) {
            case ACTION_SORT:
                server.performSort();
                break;
            case ACTION_CHANNEL_BURST:
                server.performChannelRequest(PocketChannelState.Mode.BURST);
                break;
            case ACTION_CHANNEL_SHORT:
                server.performChannelRequest(PocketChannelState.Mode.SHORT);
                break;
            case ACTION_BIND:
                server.performBind();
                break;
            case ACTION_UNBIND_LAST:
                server.performUnbindLast();
                break;
            case ACTION_UNBIND_ALL:
                server.performUnbindAll();
                break;
            case ACTION_ESSENCE_OUT:
                server.performEssenceOut(arg);
                break;
            case ACTION_ESSENCE_INTAKE:
                // ★R92-④：arg（玩家<b>实点的那一格</b>）不再被丢掉 —— 入槽的 L 执法与"放瓶即定档"都要按
                // 点击格判（R91-q 收口）。★不新增动作码：客户端 requestEssenceIntake(cell) 本来就在发它。
                server.performEssenceIntake(arg);
                break;
            case ACTION_ESSENCE_OUT_TO_PHIAL:
                performEssenceOutToPhial(arg);
                break;
            case ACTION_UPGRADE_SWITCH:
                // ★R96 S2：开关的唯一服务端落点（arg 的解越归 PocketConfigPanel，判据归 handler）。
                // 本 case 是"静态可达链"的中间一跳，两侧都不能省：客户端只发码，服务端才写档。
                server.performUpgradeSwitchToggle(arg);
                break;
            case ACTION_UPGRADE_MODE:
                // ★R96 S9b：模式位的唯一服务端落点，形状与上面那条开关腿逐字同构（★同一条可达链纪律：
                // 客户端只发码 ⇒ 服务端才写档；解越归 PocketConfigPanel.modeRowOfArg，判据归 handler）。
                server.performUpgradeModeToggle(arg);
                break;
            case ACTION_MAGNET_MODE_CYCLE:
                // ★R96 S7b：三态循环按钮。本 case 与下面三条都是那条"静态可达链"的<b>中间一跳</b>：
                // 上面（格件/按钮的 onMousePressed）与下面（handler 的 performMagnet* 写口）任一侧断链，
                // 玩家点到的就是一块画出来的死控件（R57/C3 那一族"全绿但什么都没发生"）。
                server.performMagnetModeCycle();
                break;
            case ACTION_MAGNET_TARGET_CYCLE:
                server.performMagnetTargetCycle();
                break;
            case ACTION_MAGNET_CLEAR:
                server.performMagnetClearEntries();
                break;
            case ACTION_MAGNET_REMOVE_AT:
                // ★arg = 玩家<b>实点的那一格</b>；键由服务端按自己那份插入序取（R18/R19：不吃客户端抄上来的键）
                server.performMagnetEntryRemoveAt(arg);
                break;
            default:
                break;
        }
    }

    // ------------------------------------------------------------------ 服务端动作实现
    //
    // ★R90 T2：本段的 performSort（★R93-① 起 performTakeOut 已删，见 {@code NekoPocketStorageColumn#build}） /
    // performChannelRequest / performBind /
    // performUnbindLast / performUnbindAll / performEssenceOut / performEssenceIntake 及其私有辅助
    // （nextRealSlot / mergeKey / anyRecognised / refund / returnToPlayerFromBindSlot）已整体迁往
    // {@link NekoPocketServerHandler}（含各自 javadoc）。留在本类的只有下面这一簇：
    // ★★<b>R91-④（瓶往返改判）后本簇只剩「取出 = 灌玩家自己那只瓶」的一条单点</b>
    // {@link #performEssenceOutToPhial}（离线套件锚定：方法体守卫 token 与单源声明）与它闭包在周围的
    // {@link #depositToPlayerFirst}（满瓶的落点结算，服务端 handler 经同包调用）。
    // ★同一条 R91-④ 撤掉了 R90 D1 的<b>自造瓶族</b>（{@code newPhialStack} / {@code handPhialsToCursor} /
    // {@code depositPhialsToPlayer}）：那三条是"无中生有造一叠满瓶"的物化口，正是用户报的
    // 「点击源质槽的源质会凭空生成安瓿瓶」的形状本身 ⇒ 符号整体删除（不留 {@code _unused}、不留注释尸），
    // 生产调用方由 {@code plan/_taskpack/verify-pocket.sh} 的 {@code R91-b} 段穷举钉成 0。

    /**
     * 塞进玩家背包，装不下就掉在脚下（R40a 的非消耗退回口径；整理/搬空都不该凭空吞物品）。
     * <p>
     * ★★<b>R96 S4b（R-1 收口）：本方法是「离开口袋进入原版世界」唯一的漏斗，拆堆上移到这里</b>。
     * S4 把整理溢出那一条改走了 {@code NekoPocketServerHandler#giveAwayInNaturalChunks}，但
     * {@link #evictFromSlot} 交进本方法的那份余量仍是<b>整堆</b>直递 —— 那一条当时不在
     * S4 的允许清单内，只能钉成「恰 1 计数门」挂账（README 代价 37）。本片按裁定把尺子收到本方法：
     * <b>任何</b>离开口袋中栏进入玩家原版背包 / 掉落的路径，都先按该件的天然 {@code maxStackSize}
     * 拆成普通栈再逐块交付。两条独立的破坏面（取证 r96-ret7 §3.3 / §3.4）：
     * ① NEI 的 {@code isItemInfinite} 是裸数值判据 {@code stackSize > 100}，每客户端 tick 扫
     * {@code InventoryPlayer} 命中即改写成 111；② {@code EntityItem}／vanilla {@code ItemStack} 的
     * {@code Count} 走<b>有符号 byte</b>，&gt;127 掉出世界即坏。每块 ≤ 天然满量（≤ 64）⇒ 两条同时躲开。
     * <p>
     * ★尺子是 {@code getMaxStackSize()}，★不是 {@code effectiveStorageLimit}：出包后的世界是原版背包，
     * 那里的尺子就是天然满量，把口袋那把放大尺带出去正是上面两条的成因（与 S4 那条拆堆口同一口径）。
     * ★天然 ≤ 一块的栈（绝大多数手势与全部工具类）走不到循环里 ⇒ <b>行为逐字不变</b>。
     */
    void giveToPlayer(ItemStack stack) {
        final EntityPlayer target = player();
        if (target == null || stack == null || stack.stackSize <= 0) {
            return;
        }
        final int natural = Math.max(1, stack.getMaxStackSize());
        // ★★循环条件读的是<b>本体剩余</b>（每轮先 {@code splitStack} 把它减掉 natural），既不是原版的
        // 返回值、也不是被递出去那一块的 stackSize（原版成功那一刻就把入参置 0）——R83 那版「拿入参
        // stackSize 当进度」的写法成功一圈就被清零 ⇒ 进度恒 0 ⇒ 服务器主线程死循环（同文件
        // {@link #moveToPlayer(ItemStack)} 的 javadoc 记的就是这一条）。每一轮必然递减 ⇒ 必收敛。
        while (stack.stackSize > natural) {
            handNaturalChunkToPlayer(target, stack.splitStack(natural));
        }
        if (stack.stackSize > 0) {
            handNaturalChunkToPlayer(target, stack);
        }
    }

    /**
     * 单块交付（★一件"进背包 → 装不下掉脚下"的非消耗退回，R40a 口径原样保留）。
     * <p>
     * ★拆成独立方法只为让上面那条循环里 <b>{@code addItemStackToInventory} 与 {@code entityDropItem}
     * 各只出现一次</b>（门禁门 D 按落点穷举计数：口袋目录内进原版背包恰 3 处、掉脚下恰 2 处）；
     * ★<b>不许</b>在这里再套一层"投完再投"的循环 —— 一格里能装的件数由原版自己摊，摊不下就是掉脚下。
     */
    private void handNaturalChunkToPlayer(EntityPlayer target, ItemStack chunk) {
        if (!target.inventory.addItemStackToInventory(chunk)) {
            target.entityDropItem(chunk, 0);
        }
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

    /**
     * ★★<b>R91-④（瓶往返改判）：「源质盘 → 玩家」的唯一出瓶口——必须手持空安瓿瓶，且只灌玩家自己那只瓶。</b>
     * <p>
     * 用户原话是「点击源质槽的源质，会凭空生成安瓿瓶，应该是要拿安瓿瓶去装」。R90 D1 为此留的三条
     * <b>自造瓶口</b>（{@code newPhialStack} / 空游标 {@code handPhialsToCursor} / Shift
     * {@code depositPhialsToPlayer}）已随本裁定<b>整体删除</b>，于是本方法是两条动作码
     * （{@code ACTION_ESSENCE_OUT_TO_PHIAL} 与 {@code ACTION_ESSENCE_OUT}——后者由
     * {@link NekoPocketServerHandler#performEssenceOut} 原样转发 packed arg，shift 位在里）
     * 共同的、也是唯一的落点。五道权威复验（R18/R19：客户端一律不算真值）：
     * <ol>
     * <li>★<b>R91-j</b>：packed arg 解出的格号必须落在 {@code [0, GHOST_ESSENCE_SLOT_LIMIT)}——
     * 越界（含负数与 ≥ 2×FLAG 的编码域外值）⇒ <b>整条拒绝，不截断、不取模回一个"看着合法"的格号</b>；</li>
     * <li>游标是<b>空瓶</b>（{@link PocketEssenceIntake#isEmptyPhialCarrier} 白名单单源）。空手 / 持非容器 /
     * 持满瓶 / 持晶 ⇒ <b>不产出任何物品、不扣一点数</b>，只给 {@code gtit.pocket.essence.need_phial}
     * 的<b>面板内</b>回执（★R88 C3：源质域一律不进聊天框）；</li>
     * <li>格号经格位归属表反查 tag（不吃客户端送来的 tag，与 {@code performEssenceOut} 同形）；</li>
     * <li>本次动作的量与 C1 的向下取整全在 {@link PocketEssenceIntake#phialsToFill} 一条算式里
     * （★面板不复写 {@code floorToPhialUnits}，也不写第二个"一次几只"的字面量）：
     * Shift = 该格整份、非 Shift = 一次动作上界，再被<b>游标上的空瓶只数</b>夹住 ⇒ 余数原地留盘；</li>
     * <li>灌装走 TC 容器 helper 那<b>一份</b>真相——{@link TaumCompat#addEssentia}（它完成
     * meta 0 → meta 1 与 {@code Aspects} NBT，★不在游标栈外另造一个瓶）。返回值不足一瓶 ⇒
     * <b>整笔不动</b>。</li>
     * </ol>
     * <b>先扣点、后灌装、失败退点</b>（与 {@code performEssenceOut} 旧支同纪律）：三条退出支各自
     * {@code markDirty}（★SC5 穷举的那张"改库存入口必显式标脏"表里本方法是其中一行）。
     * 游标结算只走 {@code syncManager.setCursorItem(...)}（★R88 B1：裸写 {@code inventory.setItemStack}
     * 到不了客户端 = 凭空复制）：游标上只有那一只瓶 ⇒ 就地换成满瓶回游标；一叠瓶 ⇒ 消耗掉真正落地的那几只、
     * 满瓶进背包（{@link #depositToPlayerFirst}：背包优先、中栏兜底），一件都投不进 ⇒ 整笔回滚不动游标，
     * 投进一部分 ⇒ <b>按真正落地的只数扣点</b>、差额原样退回盘（既不销毁价值也不多给瓶）。
     */
    void performEssenceOutToPhial(int packedArg) {
        if (!server.serverGuardOk()) {
            return;
        }
        final boolean shift = packedArg >= PocketConstants.ESSENCE_OUT_SHIFT_FLAG;
        final int cell = shift ? packedArg - PocketConstants.ESSENCE_OUT_SHIFT_FLAG : packedArg;
        // ★★<b>R91-j（越界 arg ⇒ 显式拒绝，不截断、不取模）</b>：shift 位与格号共用这一枚 int 通道。
        // 接受该形状的理由：编码域是两段互不重叠的区间（非 shift [0, FLAG)、shift [FLAG, 2×FLAG)，
        // 格号上界 71 < FLAG=72 ⇒ packed 最大 143，远小于动作通道的 arg 基数 ACTION_ARG_BASE=1024，
        // 与同通道里最大格号 135（中栏 134 + 一位）同一形状，装得下）。但解出来的格号一旦越出
        // [0, GHOST_ESSENCE_SLOT_LIMIT)，就是伪造/写歪的 arg ⇒ 整条不动（不扣点、不动游标、不标脏），
        // ★也绝不"顺手 % 回一个合法格号"——截断会把"越界"读成"玩家点了那一格"（R70 防伪同族口径）。
        // 位移区间不重叠 + 越界拒绝两条由用例 essence_out_packed_arg_bounds_rejected 钉住。
        if (packedArg < 0 || cell < 0 || cell >= PocketConstants.GHOST_ESSENCE_SLOT_LIMIT) {
            GTInterestingThing.LOG.debug(
                "[PocketR89] L4 格→瓶取出拒收（R91-j 越界）：arg={} 解出格号 {} 不在 [{}, {}) ⇒ 拒绝，不截断",
                packedArg,
                cell,
                0,
                PocketConstants.GHOST_ESSENCE_SLOT_LIMIT);
            return;
        }
        final ItemStack carried = syncManager.getCursorItem();
        if (!PocketEssenceIntake.isEmptyPhialCarrier(carried, EssenceGate.TAUM)) {
            // ★R91-④ 的正身：没有空瓶就没有任何东西可灌 ⇒ 零产出（旧形状在这里"无中生有"造一叠满瓶）
            putReceipt("gtit.pocket.essence.need_phial", 0);
            GTInterestingThing.LOG.debug("[PocketR89] L4 格→瓶取出拒收（R91-④ 零产出）：格 {} 游标不是空安瓿瓶 ⇒ 不产出任何物品、不扣一点数", cell);
            return;
        }
        final PocketEssenceStore store = inventory.essence();
        final String tag = store.tagAtCell(cell);
        if (tag == null) {
            // 该格没有归属 ⇒ 没东西可取（与 performEssenceOut 的空格回执同一条）
            putReceipt("gtit.pocket.still.idle", 0);
            return;
        }
        final int stock = store.get(tag);
        final int bottles = PocketEssenceIntake.phialsToFill(carried.stackSize, stock, shift);
        if (bottles <= 0) {
            // 凑不满一瓶：一支都不产、一分都不扣，余数（=全部存量）留盘并说话
            putReceipt("gtit.pocket.essence.not_enough_phial", stock);
            GTInterestingThing.LOG
                .debug("[PocketR89] L4 格→瓶取出拒收：格 {} tag={} stock={} shift={}（不足一瓶 ⇒ 零扣点）", cell, tag, stock, shift);
            return;
        }
        final int points = store.extract(tag, bottles * PocketConstants.ESSENCE_OUT_UNIT_POINTS);
        if (points <= 0) {
            putReceipt("gtit.pocket.still.idle", 0);
            return;
        }
        inventory.recordEssenceDelta(tag, -points);
        final int attempts = points / PocketConstants.ESSENCE_OUT_UNIT_POINTS;
        // ★灌装的是"玩家那几只空瓶的副本"：一叠载体共享一份 AspectList（TC 的形状），所以一次 addEssentia
        // 就是把这一叠的<b>单件</b>内容写成整瓶（与入槽侧 scaledByStackSize 那条乘法正好互逆）。
        final ItemStack filled = carried.copy();
        filled.stackSize = attempts;
        final int stored = TaumCompat.addEssentia(filled, tag, PocketConstants.ESSENCE_OUT_UNIT_POINTS);
        if (stored < PocketConstants.ESSENCE_OUT_UNIT_POINTS) {
            // 灌不进（TC 缺席 / 该 tag 不可物化 / 容器不吃这一档）：点数退回，绝不销毁价值，也不发一只空瓶
            store.add(tag, points);
            inventory.recordEssenceDelta(tag, points);
            inventory.markDirty();
            putReceipt("gtit.pocket.still.idle", 0);
            logOnce(
                "L4-phial-refund",
                "[PocketR89] L4 格→瓶取出退点（U3 裁决样本，本形态首例升 INFO；R91-④ 改走 addEssentia）：tag={} 扣 {} 点、只回 {} 点 ⇒ 已原样退回",
                tag,
                points,
                stored);
            return;
        }
        final int landed;
        if (carried.stackSize <= 1) {
            // 单只空瓶 ⇒ 就地换成满瓶回游标（最常见的手感，与 R90 E3 交付的那一支逐字同形）
            syncManager.setCursorItem(filled);
            landed = attempts;
        } else {
            final int got = depositToPlayerFirst(filled);
            if (got <= 0) {
                // 背包/中栏一件都收不下：整笔回滚（点数退回、游标不动、满瓶不产出）
                store.add(tag, points);
                inventory.recordEssenceDelta(tag, points);
                inventory.markDirty();
                // ★R88 债②（R90 T2 拆键）+ ★R91-g 收敛：换瓶语义的"落点满"——独立于通道 Report 的
                // target_full；旧"取出支"的 target_full_take 随自造瓶支失去调用方，两份 lang 已删干净
                // （不留注释尸）。文案明说"点数已退回源质盘"（无占位：整笔回滚，全额留盘）。
                putReceipt("gtit.pocket.receipt.target_full_put", 0);
                return;
            }
            landed = got;
            final int left = carried.stackSize - got;
            if (left > 0) {
                final ItemStack rest = carried.copy();
                rest.stackSize = left;
                syncManager.setCursorItem(rest);
            } else {
                syncManager.setCursorItem(null);
            }
        }
        // ★只按<b>真正落地</b>的只数记账：多扣的那部分原样退回盘（landed ≤ attempts ⇒ spent ≤ points）。
        final int spent = landed * PocketConstants.ESSENCE_OUT_UNIT_POINTS;
        if (spent < points) {
            final int returned = points - spent;
            store.add(tag, returned);
            inventory.recordEssenceDelta(tag, returned);
        }
        inventory.markDirty();
        final int leftover = PocketEssenceIntake.leftoverOnFloor(stock, shift);
        if (leftover > 0) {
            putReceipt("gtit.pocket.essence.partial_leftover", spent, leftover);
        } else {
            putReceipt("gtit.pocket.receipt.ok", spent);
        }
        GTInterestingThing.LOG.debug(
            "[PocketR89] L4 格→瓶取出：格 {} tag={} stock={} shift={} 请求 {} 只 ⇒ 落地 {} 只（扣 {} 点、游标原 {} 只、留盘余数 {} 点）",
            cell,
            tag,
            stock,
            shift,
            attempts,
            landed,
            spent,
            carried.stackSize,
            leftover);
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
            server.onServerGhostRequest(request);
        }
        return true;
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
        // ★★<b>R97 R1（正门）：并入 {@link #clientGhostFlags} 的 attr/P 位表</b>。ghost blob 只携带载荷
        // （{@code kind|slot|payloadKey|cap}），重建出的实例位表全空 ⇒ 旧形状 {@code replaceFilters}
        // 每换一次实例就把客户端 {@code inventory.filters()} 的位表抹成 NONE，而客户端预测
        // （{@code PocketInventory#isItemValid} → {@code allowsPlayerPlacement} → {@code attrAt}）读的
        // 恰是这份被抹掉的表 ⇒ 服务端按 MEMORY 放行、客户端按 NONE 预测拒绝 = 满栈幽灵游标的正身。
        // 并入走<b>既有编解码单源</b>（{@code flagsBlobOf → applyFlagsBlob}，与角标镜像同一对函数），
        // 面板自身零份位表写法；{@code parsed} 是新实例（位表本来就空），apply 的"先清后写"在此是 no-op。
        PocketGhostRequest.applyFlagsBlob(PocketGhostRequest.flagsBlobOf(clientGhostFlags), parsed);
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
    // NOTE（R90 T2）：本方法由 {@link NekoPocketServerHandler#onServerGhostRequest} 经同包调用
    // （写档后刷虚化），可见性随迁放宽为包内。
    void applyGhosts() {
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
            // ★R91-⑤：attr / P 与 cap 同一条推送纪律（★读的是<b>双源 accessor</b>，客户端那份是
            // SYNC_GHOST_FLAGS 的镜像。<b>★R97 R1 之后</b> inventory.filters() 在客户端也带着并入的
            // 位表（预测读它），但角标读<b>仍钉在 accessor</b>——一根通道一个所有者，显示读口不因
            // 预测侧的同源化而分叉出第二条）。
            widget.setGhostAttr(ghostAttrAt(PocketFilterConfig.Kind.ITEM, index));
            widget.setUploadBlocked(ghostUploadBlockedAt(PocketFilterConfig.Kind.ITEM, index));
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
            // ★R91-⑤ 流体支：三组格件<b>逐个</b>都要收到 attr / P（同 applyItemGhosts 的推送纪律）
            widget.setGhostAttr(ghostAttrAt(PocketFilterConfig.Kind.FLUID, index));
            widget.setUploadBlocked(ghostUploadBlockedAt(PocketFilterConfig.Kind.FLUID, index));
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
            // ★R91-⑤ 源质支：attr 落在<b>本格</b>（Kind.ESSENCE 的索引空间 = 格号），与上面 home[] 的
            // "遮罩按 tag 归位"是两件事 —— 载荷声明按 tag 归位（R86），<b>属性</b>按格挂（手势就点在这一格上）。
            cell.setGhostAttr(ghostAttrAt(PocketFilterConfig.Kind.ESSENCE, index));
            cell.setUploadBlocked(ghostUploadBlockedAt(PocketFilterConfig.Kind.ESSENCE, index));
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

    // ------------------------------------------------------------------ 回执（R39b/R10：模式与结果都只由服务端下发）

    /**
     * ★R90 E3 日志检查点的<b>首例升级闩</b>：同一 key 只把<b>第一次</b>命中升到 INFO
     * （终验在常规 jar 里可直读），后续同名事件全走调用方自己的 debug 行 ⇒ 防刷屏。
     * 与 {@code PocketEssenceChannelOps} L12 的 {@code Set.add} 闩同一形态（R89"一次性 INFO"口径）。
     */
    private static final java.util.Set<String> LOG_ONCE = java.util.Collections
        .newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());

    /**
     * 见 {@link #LOG_ONCE}：key 首例 ⇒ INFO（SLF4J 占位格式），其余静默（重复事件由 debug 行覆盖）。
     * ★R90 T2：包内可见——{@link NekoPocketServerHandler} 的 L4 退点读数与本类客户端分流（L1）
     * 必须共享<b>同一张</b>首例闩（各自一份会让同 key 的 INFO 打两遍）。
     */
    static void logOnce(String key, String pattern, Object... args) {
        if (LOG_ONCE.add(key)) {
            GTInterestingThing.LOG.info(pattern, args);
        }
    }

    /**
     * 服务端记一次回执（键 + 已搬运数 + 被拒数）。
     * <p>
     * 只发"键 + 两个数"，<b>不在服务端拼本地化文本</b>：客户端才是有 lang 表的那一端
     * （服务端拼出来的字符串到了客户端就再也翻不成玩家语言）。多带的第二个实参对
     * 只有一个 {@code %d} 的键是无害的（{@code String.format} 允许多余参数），
     * 而 {@code receipt.partial} 这种两占位的键正好共用同一份载荷。
     * ★R90 T2：包内可见——服务端执行体已迁 {@link NekoPocketServerHandler}，回执状态仍住在本类
     * （compose/apply 与 SYNC_RECEIPT 同侧，不搬）。
     */
    void putReceipt(String key, int moved) {
        putReceipt(key, moved, 0);
    }

    void putReceipt(String key, int moved, int refused) {
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
        final String[] parts = new String[GHOST_PART_COUNT];
        for (int part = 0; part < parts.length; part++) {
            parts[part] = ghostBlobPartOf(inventory.filters(), part);
        }
        return joinGhostParts(parts);
    }

    /** 每个索引范围独立限长；仍复用既有记录文法与单条超限提示。 */
    public static String ghostBlobPartOf(PocketFilterConfig filters, int part) {
        if (filters == null || part < 0 || part >= GHOST_PART_COUNT) {
            return "";
        }
        final PocketFilterConfig selected = new PocketFilterConfig();
        for (PocketFilterConfig.Filter filter : filters.filters()) {
            int globalIndex = filter.slotIndex();
            if (filter.kind() == PocketFilterConfig.Kind.FLUID) {
                globalIndex += PocketConstants.GHOST_ITEM_SLOT_LIMIT;
            } else if (filter.kind() == PocketFilterConfig.Kind.ESSENCE) {
                globalIndex += PocketConstants.GHOST_ITEM_SLOT_LIMIT + PocketConstants.GHOST_FLUID_SLOT_LIMIT;
            }
            if (globalIndex / GHOST_SLOTS_PER_PART == part) {
                selected.add(filter.slotIndex(), filter);
            }
        }
        return ghostBlobOf(selected);
    }

    /** 空片也参与覆盖，保证删除最后一条声明后客户端不会残留旧内容。 */
    public static String joinGhostParts(String[] parts) {
        final StringBuilder joined = new StringBuilder();
        for (String part : parts) {
            if (part != null && !part.isEmpty()) {
                if (joined.length() > 0) {
                    joined.append(';');
                }
                joined.append(part);
            }
        }
        return joined.toString();
    }

    private void applyGhostPart(int part, String blob) {
        if (syncManager.isClient()) {
            clientGhostParts[part] = blob == null ? "" : blob;
            applyGhostBlob(joinGhostParts(clientGhostParts));
        }
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

    // -------------------------------------------------- ★R91-a 属性层（第二枚 StringSyncValue，双端对偶）

    /**
     * 服务端侧的属性 blob（编码器 = {@link PocketGhostRequest#flagsBlobOf}，★与解码器同族单函数纪律）。
     * <p>
     * ★读的是<b>权威位表</b>（{@code inventory.filters()} 上的 attr / P），不是客户端那份镜像；
     * 一格属性都没挂 ⇒ 整串是空串 ⇒ {@link #applyGhostFlagsView} 把镜像清干净（撤属性必须真的落回客户端，
     * 否则"撤销手势"只活服务端 = R86 那类"改判只活一半"的复现）。
     */
    private String composeGhostFlags() {
        return PocketGhostRequest.flagsBlobOf(inventory.filters());
    }

    /**
     * 客户端侧：把属性 blob 换进 {@link #clientGhostFlags} 那份镜像，再<b>原位</b>刷三组格件的属性。
     * <p>
     * ★{@code applyGhosts()} 是<b>既有</b>的那一个刷新点（ghost 声明 → 三组格件），本方法与
     * {@link #applyGhostView} 共用它 ⇒ 遮罩/角标/cap 读数永远一次刷齐，不会出现"属性刷了、虚化没刷"。
     * 镜像与 ghost blob 的镜像一样是<b>纯显示侧</b>：它从不写档，执法读的是服务端那一份（R18/R19）。
     */
    private void applyGhostFlagsView(String blob) {
        if (!syncManager.isClient() || blob == null || blob.equals(ghostFlagsBlob)) {
            return;
        }
        ghostFlagsBlob = blob;
        PocketGhostRequest.applyFlagsBlob(blob, clientGhostFlags);
        // ★★<b>R97 R1 的对偶半条</b>：属性镜像更新时把<b>预测读的那一份</b>（{@code inventory.filters()}，
        // 也就是 {@code replaceFilters} 换出来的镜像）同步推进——applyFlagsBlob 自带"先清后写"，
        // 服务端<b>撤掉</b>的属性会真的从预测那份掉下来（只 ADD 不清的形状撤属性只活角标）。
        // 若无此半条：blob 先到 / 属性包后到的交错序里，预测表会停留在旧位表直到下一次 blob 换实例。
        PocketGhostRequest.applyFlagsBlob(PocketGhostRequest.flagsBlobOf(clientGhostFlags), inventory.filters());
        applyGhosts();
    }

    /**
     * ★★<b>R86 铁律的兑现点</b>：某格 {@code attr} 的<b>双源 accessor</b>（与
     * {@link #essenceTagAtCell(int)} / {@link #essenceCellOfTag(String)} 严格同形）。
     * <p>
     * 服务端读权威位表；客户端读 {@link #clientGhostFlags} 那份由 {@code SYNC_GHOST_FLAGS} 整体覆盖的镜像。
     * ★<b>角标/显示读口不得在客户端读 {@code inventory.filters()}</b>——显示读口只认这一条 accessor
     * （一根通道一个所有者）。★<b>R97 R1 之后</b>那份 filters 的位表已由 {@link #applyGhostView} /
     * {@link #applyGhostFlagsView} 并入同源（供 {@code isItemValid} 预测链读），但那是<b>预测读侧</b>
     * 的同源化，不构成显示侧的第二条读法。
     */
    int ghostAttrAt(PocketFilterConfig.Kind kind, int slotIndex) {
        return (syncManager.isClient() ? clientGhostFlags : inventory.filters()).attrAt(kind, slotIndex);
    }

    /** ★同 {@link #ghostAttrAt}：正交位 {@code P} 的双源 accessor（客户端不许直读 {@code inventory}）。 */
    boolean ghostUploadBlockedAt(PocketFilterConfig.Kind kind, int slotIndex) {
        return (syncManager.isClient() ? clientGhostFlags : inventory.filters()).uploadBlockedAt(kind, slotIndex);
    }

    /**
     * ★★<b>R91-⑤ 三个手势的唯一 C2S 出口</b>（中栏 / 流体槽 / 源质格三组格件共用这一条）。
     * <p>
     * 与 {@link #requestGhost} / {@link #requestGhostClear} 同一条 {@code SYNC_GHOST_REQUEST} 通道
     * （R91-⑥：不新造动作码、不开第三条通道），★客户端只报表征手势的一个字母：attr 与 P 的<b>迁移真值
     * 在服务端</b>算（{@code PocketGhostRequest#applyFlag}），本地一个字节都不改 —— 与 R18/R19 对
     * ghost 的全部纪律同形。
     *
     * @param gesture {@link PocketConstants#GHOST_FLAG_BIND}（中键）/
     *                {@link PocketConstants#GHOST_FLAG_MEMORY}（alt+左）/
     *                {@link PocketConstants#GHOST_FLAG_UPLOAD_BLOCK}（alt+右）
     */
    boolean requestGhostFlag(PocketFilterConfig.Kind kind, int slotIndex, String gesture) {
        if (slotIndex < 0 || !PocketFilterConfig.isAllowedSlotIndex(kind, slotIndex)) {
            return false;
        }
        return sendGhostRequest(PocketGhostRequest.flagRequest(slotIndex, kind, gesture));
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
     * 任何一次 {@code extract}（玩家取瓶、通道取出）、任何一次<b>真的占到新格位</b>的 {@code assignCell}、
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

    /**
     * 蒸馏状态行（★R87-h 双源 accessor）：客户端读同步镜像（服务端 static {@code CLOCKS} 在专用服客户端
     * 恒空 ⇒ 直读恒 IDLE/0）；服务端现算，拼串单源在 {@link NekoPocketEssenceColumn}。
     */
    String distillStateLineText() {
        if (syncManager.isClient()) {
            return clientDistillStateLine;
        }
        return NekoPocketEssenceColumn.composeDistillStateLine(
            PocketDistillDriver.statusOf(playerId()),
            PocketDistillDriver.secondsToNextBatch(playerId()),
            PocketDistillDriver.discardedGroupsOf(playerId()),
            PocketDistillDriver.discardedPointsOf(playerId()));
    }

    private void applyDistillStateLine(String text) {
        if (syncManager.isClient()) {
            clientDistillStateLine = text == null ? "" : text;
        }
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
    // ★R90 T2：本类仍 implements PocketSession（PocketSessions.register(this) 语义不动），接口身份
    // 不变；实现体逐条一行委托 {@link NekoPocketServerHandler}（"装配壳 + 委托"）。例外是带<b>实现体</b>
    // 留在本类的少数几条——它们被离线套件的源码机检按方法体锚定（drainEssence 的置脏守卫）或闭包在
    // 被锚定的私有方法周围（persistIdle/persistFinal → writeSessionToCarrier、depositItem → moveToPlayer、
    // isOpen ↔ closed 闩），位置不动即判据不红。

    @Override
    public ItemStack carrierStack() {
        return server.carrierStack();
    }

    @Override
    public NBTTagCompound carrierTag() {
        return server.carrierTag();
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
        return server.isHeldByOwner();
    }

    @Override
    public void markDirty() {
        server.markDirty();
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
     * 与关屏落盘共用同一段代码 ⇒ "谁在写档"只有一个答案。
     * <p>
     * ★★<b>R88 B3（关屏不落盘 ⇒ 扣点整批丢弃）的兜底</b>：旧实现在重定位失败时<b>只 warn、一个字都不写</b>，
     * 于是"扣点只在内存、序列化只在落盘时发生"这条链一断，本轮所有扣点/入账就永久丢失（而玩家手里的
     * 容器已经发出去了 = 复制）。现在补两档兜底，顺序固定：
     * <ol>
     * <li><b>按对象身份写回内存认得的那一枚</b>（{@code pocket}）——这<b>不是</b>"写到别的栈上"
     * （R35 防御③真正防的是那个）：NBT 跟着 ItemStack 对象走，只要那一枚还活在任意未被扫到的位置
     * （游标 / 护甲 / Baubles / 交易给对方 / 快捷栏外），写它的 tagCompound 就随它落档；</li>
     * <li><b>自嵌档拒绝写</b>：承载栈此刻就在<b>本会话自己的存储格</b>里（复现手势：把口袋自己
     * shift 点进自家非声明格）⇒ 写它必然形成 {@code root → storage → 该栈 → root} 的 NBT 自环，
     * 序列化当场 StackOverflow。这一档<b>不写</b>、<b>不清 dirty</b>（让 driver 的
     * {@link #persistIdle()} 每拍重试重定位），并只告警一次。★<b>本档不在本片闭合</b>：正解在入口面
     * （禁止口袋进它自己的存储格），落点 {@code PocketInventory#isItemValid} 与
     * {@code PocketSlots} 的 storage 格 filter，两者都在本片的禁写清单外 ⇒ 已上报主代理排片。</li>
     * </ol>
     */
    private void writeSessionToCarrier() {
        if (!inventory.isDirty()) {
            return;
        }
        ItemStack carrier = closed ? relocateCarrier() : pocket;
        if (carrier == null) {
            // ★R88 B3 兜底一档：重定位三级全落空 ⇒ 退回"本会话认得的那一枚对象"
            carrier = pocket;
            if (carrier != null) {
                GTInterestingThing.LOG.warn("[pocket] 承载格重定位失败（原格 {}），改按对象身份写回会话认得的承载栈", carrierSlotIndex);
            }
        }
        if (carrier == null) {
            // ★R90 S2（终态回滚）：三级重定位与对象身份兜底<b>全部</b>落空 ⇒ 本轮源质增减再也等不到
            // 落盘点。对增量日志逐 tag 逆施（−x ⇒ 回补 +x、+x ⇒ 回扣 −x），让内存源质回到
            // 「最后一次成功落盘」的基线 —— 不留"AE2 侧已收瓶、口袋档仍有点数"的半提交态（D2 的刷源质面）。
            // ★回滚后<b>不</b>主动清 dirty（保守约束）：dirty 还承载着物品/流体等其它未落盘状态，
            // 在这里清掉等于把它们一并吞掉；增量日志已清空 ⇒ 后续即便重定位成功、重写发生，写出的
            // 也是回滚后的基线态，不会把已逆施的账再写回去（回滚幂等：日志空 ⇒ 后续重试零动作）。
            rollbackEssenceToPersistedBaseline();
            GTInterestingThing.LOG.warn("[pocket] 落盘时找不到承载口袋的槽位（原格 {}），本次内容暂不落档", carrierSlotIndex);
            return;
        }
        if (isNestedInOwnStorage(carrier)) {
            // ★R88 B3 兜底二档：自嵌 ⇒ 见方法 javadoc 的第 2 条，不写也不清 dirty
            if (!nestedCarrierWarned) {
                nestedCarrierWarned = true;
                GTInterestingThing.LOG.warn(
                    "[pocket] 承载口袋被放进了它自己的存储格（原格 {}）⇒ 写档会形成 NBT 自环，本次不落档；" + "脏标记保留，等它被移回可寻址的格子后由 driver 重试",
                    carrierSlotIndex);
            }
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

    /**
     * ★R90 S2：把源质增量日志逐 tag <b>逆施</b>回「最后一次成功落盘」的基线并清空日志（终态兜底，
     * 仅由 {@link #writeSessionToCarrier} 的"找不到承载栈"分支调用）。
     * <p>
     * 逆施用 {@code inventory.essence()} 的对称 API（{@code add} / {@code extract}）：日志里的 −x
     * 回补 +x、+x 回扣 −x，回滚后内存源质 ≡ 上一次 {@code PocketInventory#writeTo} 成功那一刻的快照。
     * 逆施<b>不</b>走 {@code recordEssenceDelta}（那是登记侧的打点口，回滚是消费侧；基点由
     * {@code clearEssenceDeltas} 直接重置，同一笔不会回灌进新日志）。
     * <p>
     * ★日志为空 ⇒ 零动作（幂等）：本分支会被 driver 每拍重试，回滚与 [PocketR89] WARN 都只在
     * 「真有未落盘增减」的那一次发生，不会刷屏。
     */
    private void rollbackEssenceToPersistedBaseline() {
        final Map<String, Integer> deltas = inventory.essenceDeltas();
        if (deltas.isEmpty()) {
            return;
        }
        int restored = 0;
        int reclaimed = 0;
        final StringBuilder perTag = new StringBuilder();
        for (final Map.Entry<String, Integer> entry : deltas.entrySet()) {
            final int delta = entry.getValue();
            if (delta == 0) {
                continue;
            }
            if (delta < 0) {
                inventory.essence()
                    .add(entry.getKey(), -delta);
                restored += -delta;
            } else {
                inventory.essence()
                    .extract(entry.getKey(), delta);
                reclaimed += delta;
            }
            if (perTag.length() > 0) {
                perTag.append(", ");
            }
            perTag.append(entry.getKey())
                .append(delta > 0 ? "+" : "")
                .append(delta);
        }
        inventory.clearEssenceDeltas();
        // L11：逆施回滚 = 守恒类事件 ⇒ WARN（一次性：日志清空后重试分支不再进入这里）
        GTInterestingThing.LOG.warn(
            "[PocketR89] 承载栈终态丢失 ⇒ 源质增量日志已逐 tag 逆施回滚（{}），共回补 {} 点、回扣 {} 点；" + "内存源质回到最后落盘基线，日志已清空（dirty 位按保守约束保留）",
            perTag,
            restored,
            reclaimed);
    }

    /**
     * 这一枚栈是否正躺在<b>本会话自己的</b>中栏存储格里（★R88 B3 的自环判据）。
     * <p>
     * 只按<b>对象身份</b>认（{@code ==}）：会话的 {@code PocketInventory.storage()} 与承载栈的
     * {@code tagCompound} 若互为祖先前代，序列化就会自环；按 {@code isItemEqual} 认会把"另一枚同款
     * 口袋"误判成自嵌，故不得放宽。
     */
    private boolean isNestedInOwnStorage(final ItemStack candidate) {
        if (candidate == null || inventory == null) {
            return false;
        }
        final com.cleanroommc.modularui.utils.item.ItemStackHandler storage = inventory.storage();
        for (int index = 0, slots = storage.getSlots(); index < slots; index++) {
            if (storage.getStackInSlot(index) == candidate) {
                return true;
            }
        }
        return false;
    }

    @Override
    public PocketCellBindings bindings() {
        return server.bindings();
    }

    @Override
    public PocketFilterConfig filters() {
        return server.filters();
    }

    @Override
    public PocketEssenceStore essence() {
        return server.essence();
    }

    @Override
    public ItemStack distillInputStack(int index) {
        return server.distillInputStack(index);
    }

    @Override
    public int distillInputSlots() {
        return server.distillInputSlots();
    }

    /** 一轮蒸馏对<b>每个非空格</b>各做一次"减 1"（★R84：对象是格不是组，同物多格并行各扣 1 件；R28：5 秒是节拍不是产量）。 */
    @Override
    public void consumeOneDistillInput(int index) {
        server.consumeOneDistillInput(index);
    }

    /**
     * 手动取出的落点：★先玩家背包，装不下的余量才走 {@link #depositItem(ItemStack)}（中栏 → 背包兜底）。
     * <p>
     * 与通道自动拉取那条口径<b>刻意相反</b>：拉取是"往口袋里填"，中栏优先；而玩家点一下取瓶却把东西
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
     * {@link NekoPocketServerHandler#performEssenceOut} 的退点）会<b>再落一次同一件</b> = 复制。两支各按各的口径，一字不改 R84 语义；</li>
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
        return server.fluidBarRoom(tank, probe);
    }

    @Override
    public int depositFluid(int tank, FluidStack fluid) {
        return server.depositFluid(tank, fluid);
    }

    // ------------------ ★R95 S5：16G 双轨的 long 面 + STACK 位查询（PocketSession 扩面，实现照 int 版同一条转发）

    @Override
    public long fluidBarRoomL(int tank, FluidStack probe) {
        return server.fluidBarRoomL(tank, probe);
    }

    @Override
    public long depositFluidL(int tank, Fluid fluid, long amount) {
        return server.depositFluidL(tank, fluid, amount);
    }

    @Override
    public long drainOwnTankL(int tank, long amount) {
        return server.drainOwnTankL(tank, amount);
    }

    @Override
    public boolean storageStackUpgraded() {
        // ★直读载体活查表（不经 server 转发回自己 ⇒ 无自环）：与服务端权威同一条真相。
        // ★R96 S2：判据换成组合谓词 isActive ⇒ 关掉堆叠开关以后，本方法的中栏/滚轮/ghost 上限读数
        // 与 PocketInventory#storageStackUpgraded（getSlotLimit / getStackLimit 两条执法点）同一个说法。
        // ★★<b>R97 R6：客户端读腿换成 {@code SYNC_UPGRADE_ACTIVE} 的位图镜像</b>（格件的显示回落与
        // 滚轮读它；vanilla 载体档镜像在会话中装插件后会滞后 ⇒ 显示与预测在旧读法下各说各话）。
        if (syncManager.isClient()) {
            return clientUpgradeActive(PocketUpgradeType.STACK);
        }
        return PocketUpgradeSwitches.isActive(carrierStackLive(), PocketUpgradeType.STACK);
    }

    @Override
    public int storageStackUpgradeCount() {
        return storageStackUpgraded() ? installedStackUpgradeCount() : 0;
    }

    public int installedStackUpgradeCount() {
        return syncManager.isClient() ? clientStackUpgradeCount : PocketUpgrades.stackUpgradeCount(carrierStackLive());
    }

    // ------------------------------------------- ★R86 缺陷 3：口袋 → 元件的推送向来源面（服务端会话实现）

    @Override
    public int fluidTankCount() {
        return server.fluidTankCount();
    }

    @Override
    public FluidStack fluidInTank(int tank) {
        return server.fluidInTank(tank);
    }

    @Override
    public int drainOwnTank(int tank, int milliBuckets) {
        return server.drainOwnTank(tank, milliBuckets);
    }

    @Override
    public Map<String, Integer> essenceStock() {
        return server.essenceStock();
    }

    // ★本方法带实现体留在本类：离线套件按方法体锚定（extract → removed>0 守卫 → recordEssenceDelta
    // → markDirty 的行序机检），委托会搬走判据本体 ⇒ 红。实现仍是 inventory 的薄包装。

    @Override
    public int drainEssence(String tag, int points) {
        final int removed = inventory.essence()
            .extract(tag, points);
        if (removed > 0) {
            // ★R90 S1（D2「上传变瓶+刷源质」的缺陷本体）：扣点只改内存、不置脏 ⇒ writeSessionToCarrier
            // 的 isDirty 门禁放行早退 ⇒ 序列化永远不发生 ⇒ 承载栈 NBT 里点数原样、AE2 侧瓶已到手 = 净复制。
            // 登记进增量日志（S2 终态回滚的输入）并置脏（与 performEssenceOut 的 markDirty 同口径）。
            inventory.recordEssenceDelta(tag, -removed);
            inventory.markDirty();
        }
        return removed;
    }

    // ------------------------------------------------------------ R84：中栏既是注入来源也是抽取落点

    @Override
    public int storageSlots() {
        return server.storageSlots();
    }

    @Override
    public ItemStack storageStackAt(int slot) {
        return server.storageStackAt(slot);
    }

    @Override
    public void setStorageStackAt(int slot, ItemStack stack) {
        server.setStorageStackAt(slot, stack);
    }

    @Override
    public boolean isStorageGhostDeclared(int slot) {
        return server.isStorageGhostDeclared(slot);
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
     * ★R90 T2：包内可见——{@link NekoPocketServerHandler#serverGuardOk} 的回落判据与它同源。
     */
    boolean carrierStillPresent(EntityPlayer testPlayer) {
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
            // ★R96 S11：读不到承载格 ≠ 口袋不见了。穿戴态开屏时 data 的 InventoryType 是 BAUBLES
            // （本来就读 mainInventory），而会话中途把口袋从手上拖进饰品栏时 data 还钉在 PLAYER 那一格
            // ⇒ 这里补一条 bauble 腿，找到就把写权钉过去；★找不到仍照原样返回 false
            // （失效判定本身一个字没放宽）。
            return repinFromBaubles();
        }
        if (atCarrier != pocket) {
            pocket = atCarrier;
        }
        return true;
    }

    /**
     * ★R96 S11：{@link #carrierStillPresent(EntityPlayer)} 的 bauble 腿（★判据与
     * {@link #relocateCarrier()} 的第四级同源 —— 同物品身份即认，不另立第二套认人标准）。
     * ★两道判空都是必需的：owner/baubles 拿不到 ⇒ 按"没穿戴"处理，绝不让 Baubles 侧的漂移变成崩溃。
     */
    private boolean repinFromBaubles() {
        if (pocket == null || pocket.getItem() == null) {
            return false;
        }
        final IInventory baubles = PocketWornTapHandler.safeBaubles(player());
        if (baubles == null) {
            return false;
        }
        for (int i = 0; i < baubles.getSizeInventory(); i++) {
            final ItemStack at = baubles.getStackInSlot(i);
            if (at == null || at.getItem() == null) {
                continue;
            }
            if (at == pocket || at.getItem() == pocket.getItem()) {
                pocket = at;
                return true;
            }
        }
        return false;
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
        // ★R88 C3：摘掉本面板的回执登记，槽件侧此后写来的回执一律丢弃（不再有现役面板可投）
        RECEIPT_HOSTS.remove(syncManager);
        final ItemStack carrier = relocateCarrier();
        if (carrier == null) {
            // ★R88 B3：旧写法在这里直接 return ⇒ "只 warn、一个字都不写"，而扣点只在内存里、
            // 序列化只在本方法发生 ⇒ 本轮所有扣点/入账整批丢弃（玩家手里的容器已发出 = 复制）。
            // 现在仍然走一次 writeSessionToCarrier()：它自带两档兜底（按对象身份写回会话认得的栈；
            // 自嵌则拒绝并保留 dirty 等 driver 重试），只有两档都不成立时才剩下那条 warn。
            GTInterestingThing.LOG.warn("[pocket] 关屏时找不到承载口袋的槽位（原格 {}），改走兜底落盘", carrierSlotIndex);
            closed = true;
            writeSessionToCarrier();
            return;
        }
        ItemNekoDimensionPocket.setOpenFlag(carrier, false);
        closed = true;
        writeSessionToCarrier();
    }

    /**
     * 找到承载本会话的那一枚口袋。★R96 S11 起是<b>四级</b>判据，<b>对象身份优先</b>：
     * <ol>
     * <li>开界面那一格上的栈还是同一枚对象 ⇒ 用它；</li>
     * <li>主背包 36 格里扫同一对象（玩家在界面里搬动过口袋）；</li>
     * <li>主背包里按 {@code open} 标记兜底（口径照 {@code ItemGTToolbox.java:391-425}）；</li>
     * <li>★<b>bauble 栏</b>：先按对象身份、再按 {@code open} 标记 —— 穿戴态开屏时前三级的视野里
     * 根本没有那一格（{@code data.getUsedItemStack()} 走的是 MUI2 的 {@code InventoryTypes.BAUBLES}，
     * 而 {@code target.inventory.mainInventory} 永远不含饰品格），少了这一腿 ⇒ 关屏找不到承载栈 ⇒
     * 只 WARN 不落盘 = 静默丢件（R88 B3 那一族）。</li>
     * </ol>
     * ★为什么不把 {@code open} 位当第一判据（本批修正）：关屏时该位已被清零，而 driver 在关屏之后
     * 还要继续落盘（上面那条），按标记找就永远找不着 ⇒ 只剩一条 warn、内容留在内存里等丢。
     * 按对象身份找则不受清零影响；兜底那一档仍然按标记认人，覆盖"重载后对象身份已断"的情形。
     * <p>
     * ★<b>R88：为什么不再往"自家存储格"里加第四级</b>（复现手势是"把口袋自己 shift 点进自家非声明格"）：
     * 那一格里躺着的正是承载本会话的那一枚 ⇒ 把它的 tagCompound 当写出目标就是
     * {@code root → storage → 该栈 → root} 的自环，序列化当场 StackOverflow。所以这一档由
     * {@link #isNestedInOwnStorage(ItemStack)} 判出后<b>拒写并保留 dirty</b>（driver 每拍重试，等它被
     * 移回可寻址的格子），闭合点只能在入口面（不许口袋进自己的存储格），见 {@link #writeSessionToCarrier()}
     * 的第 2 条兜底与交付报告里的"需 E1/主代理"项。
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
        // ★★R96 S11 第四级 = bauble 栏那一腿（穿戴态开屏时上面三级的视野里根本没有这一格）：
        // 先按对象身份（开着面板时把口袋从手上拖进饰品栏），再按 open 位兜底（重载后身份已断）。
        // ★判空守卫是必需的，不是防御性冗余：Baubles 缺席或它内部漂移时 safeBaubles 返回 null，
        // 少了这道守卫关屏落点就 NPE —— 而关屏落点 NPE 的结局是"内容留在内存里等丢"（R88 B3 同族）。
        final IInventory baubles = PocketWornTapHandler.safeBaubles(target);
        if (baubles != null) {
            if (pocket != null) {
                for (int i = 0; i < baubles.getSizeInventory(); i++) {
                    final ItemStack at = baubles.getStackInSlot(i);
                    if (at == null) {
                        continue;
                    }
                    if (at == pocket) {
                        return at;
                    }
                }
            }
            for (int i = 0; i < baubles.getSizeInventory(); i++) {
                final ItemStack at = baubles.getStackInSlot(i);
                if (at == null) {
                    continue;
                }
                if (at.getItem() instanceof ItemNekoDimensionPocket && ItemNekoDimensionPocket.isOpenFlag(at)) {
                    pocket = at;
                    return at;
                }
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
            // ★R95 S5：分母跟 STACK 位走（256/4096），与存储执法同一把尺（探针读载体活查表）
            PocketConstants.essenceCapPerTag(storageStackUpgraded()));
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
        // ★★R93-③（B 项）：旧档里"不在服务口径"那条说明原先常驻在底部带左段（{@code cellInfoLine} 第 2 行），
        // 那块本轮改成说明文字专用位 ⇒ 它必须有新落点（R36「撤形状成对」）。常驻行只有一行宽，
        // 装不下这句 30 字的话（像素账见用例 {@code resident_text_pixel_budget}），而这条读的本来就是
        // "要不要去解绑"这种动作前决策 ⇒ 落在全量面（tooltip）比落在常驻面更合适。
        final int inert = rows.size() - PocketConstants.ALLOWED_BOUND_CELLS;
        if (inert > 0) {
            if (rows.size() > shown) {
                builder.append('\n');
            }
            builder.append(String.format(StatCollector.translateToLocal("gtit.pocket.bind.inert"), inert));
        }
        return builder.toString();
    }

    /*
     * ★★R93-③：这里原先是 {@code cellInfoLine(int)}（底部带左段那两行绑定信息），本轮<b>删除</b>。
     * 用户裁定"元件维度放到右边去，右边本身就有维度了"⇒ 那块腾空，改给装不下的说明文字
     * （{@link #statusBlockText()}）。搬移逐条有落点，不是删除：
     * 旧行 1（{@code bindRowLine(0)} = 元件短码 + 维度/坐标/槽位）→ 右栏常驻行 0
     * （{@link #bindPersistentText(int)}，同一条算式同一个源）；旧行 2（{@code bind.inert}）→
     * 绑定按钮 tooltip（{@link #bindTooltipText()}）；未绑定那枚时的 {@code bind.none} →
     * 右栏常驻行 0 的空表分支（本来就有）。★不留转发性占位：占位会让"两处都还活着"这种半改查不出来。
     */

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
     * <li>★<b>R93-③：被服务的那几枚（前 {@link PocketConstants#ALLOWED_BOUND_CELLS} 枚）逐位给
     * {@link #bindRowLine(int)} = 完整位置行</b>（元件短码 + 维度/坐标/槽位）—— 这一族从底部带左段
     * 搬进来（旧 {@code cellInfoLine}，本轮删除），行 0 的整幅 108 宽就是为这条算式留的；</li>
     * <li>其余行位：只有短码 {@code bind.entry}（旧口径，本轮未动）；{@code size > PERSISTENT_ROWS} 时
     * 末位让给 {@code bind.rows_more}（含<b>未列出</b>的条数，文案点名"悬停绑定按钮"）。完整条目与位置五键照旧在按钮 tooltip
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
        // ★★R93-③：被服务的那几枚（前 ALLOWED_BOUND_CELLS 枚）给<b>整条位置行</b>（元件短码 + 维度/坐标/
        // 槽位），不再是只有短码的 bindRowTitle —— 用户裁定"元件维度放到右边去，右边本身就有维度了"，
        // 于是底部带左段那两行搬进这里。行 0 是整幅 108 宽，两行折行装得下（像素账见用例）。
        if (slot < PocketConstants.ALLOWED_BOUND_CELLS) {
            return bindRowLine(slot);
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
        // ★R88 自立口径 C1+C2 的玩家可见面（项目纪律：自立口径要"游戏内文案 + 交付说明"双处声明）：
        // 载体是 TC 安瓿瓶、一瓶固定几点由粒度单源填（lang 只留 %d）、取出向下取整到整瓶、余数留盘；
        // 晶化源质只读不产。数字全部来自常量（lang 契约 §7-5：lang 里不得写死规格数字）。
        builder
            .append(
                String.format(
                    StatCollector.translateToLocal("gtit.pocket.note.phial"),
                    PocketConstants.ESSENCE_OUT_UNIT_POINTS))
            .append('\n');
        builder.append(StatCollector.translateToLocal("gtit.pocket.note.channel"))
            .append('\n');
        // ★R83 D-4：中栏的"箱子属性"是自家手势 + 自家算法，必须在游戏内说清它不是通用箱子整理。
        // ★★<b>R91-⑧b（中键让位）改述</b>：整理<b>只剩 R 键</b>一个手势（中键已让给"请求绑定"），
        // 而三条属性手势各自一条键 ⇒ 四条并列。★原来那句"中栏箱子属性（中键整理）"的说明如果留着不改，
        // 就是"javadoc/文案声称了代码不做的事"（本仓 D-1 型不实）⇒ 与两份 lang 一起由 S5 落文（键名在此引用）。
        builder.append(StatCollector.translateToLocal("gtit.pocket.storage.sort_gesture"))
            .append('\n');
        builder.append(StatCollector.translateToLocal("gtit.pocket.storage.bind_gesture"))
            .append('\n');
        builder.append(StatCollector.translateToLocal("gtit.pocket.storage.memory_gesture"))
            .append('\n');
        builder.append(StatCollector.translateToLocal("gtit.pocket.storage.upload_block_gesture"))
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
     * 主面板量级读数（每槽/合计容量）——消费宿主 = 流体格件 tooltip（{@code NekoPocketFluidSlot}）
     * 与底部带说明块 tooltip（{@code NekoPocketBottomBand}）两处，数字读数在这两处仍然成立。
     * <p>
     * ★R101 改判分工：容量<b>配置面</b>那一行改读关闭规则说明（键 = 配置面板容量段独有的
     * {@code PocketConfigPanel#READOUT_CAPACITY_KEY}，{@code gtit.pocket.config.capacity.rule}，
     * 由配置面自己消费）——本方法<b>不再</b>被配置面消费，量级格式源仍然只有这一份，不另写第二份。
     * <p>
     * ★★旧口径点名的两处宿主<b>都不存在，别照号去找</b>：① 物品 tooltip 的 {@code tooltip.9}
     * 自 R98 起整行撤销（现行权威见 README 代价 55，不要为此把容量行加回 tooltip）；
     * ②「左栏末行的 tooltip」自 R94-① 起随末行整行撤销，今天挂着那条容量 tooltip 的是底部带
     * 左段那块 112×60 说明文字。R78 D-2 那句「不再常驻左栏」仍然成立。
     */
    String capacityReadoutText() {
        // ★R95 S5：容量读数按 CAPACITY 位动态（16M/16G；合计 288M/288G）——Long 喂 %d（lang 不改键、
        // 不写死数字），未升级喂 Integer（渲染与旧值同字面）。升级位双端各读自己那份载体（同本类
        // channelPersistActive 的口径）。
        return String.format(
            StatCollector.translateToLocal("gtit.pocket.fluid.capacity"),
            Long.valueOf(fluidTankCapacityNow()),
            PocketConstants.FLUID_TANK_TOTAL,
            Long.valueOf(PocketConstants.fluidTotalCapacityMl(capacityUpgradeCountNow())));
    }

    /**
     * ★★<b>R93-③ 立、★R94-① 换尺寸：底部带左段上半那一整块 112×60「说明文字」的正文</b>。
     * <p>
     * <b>三代形状史</b>：① 同一条串画在左列末行那个 88×18 的窄条里（方法名旧称
     * {@code fluidStatusLine}；那 88 还是 R83 撤「整理」按钮后没人收的死账）⇒ 最长串按 0.6 折五行
     * = 30px &gt; 18 <b>必然顶穿</b>，就是用户报的"说明文字…会超出"；② R93-③ 把元件短码与维度行搬到
     * 右栏常驻行后，底部带左段腾出连续 36px、字号另立 0.65；③ ★R94-① 用户看了形状再说一遍
     * "按钮上下都有文字…按钮移到最下、上面都放说明文字，这样文字就可以放大了"⇒ 左列末行那 18px
     * 也收进来，块变 <b>112×60</b>、字号抬到 0.8，币栏压到段底。
     * <p>
     * 内容 = 模式（R39b 服务端算）+ 冷却/剩余与最近一次回执（R16/R24/R10），两者都是<b>运行期事实</b>
     * （为什么这类文字允许常驻、而图例与用法摘要只许进 tooltip，判据写在
     * {@code NekoPocketLeftColumn} 的类 javadoc）。
     * ★★R94-① 之后<b>模式串只出现一次</b>（就在本正文的第一段）：R93-③ 那轮"左列末行仍留一处模式串"
     * 的重复读点随末行一起撤销 ⇒ 上一版为它写的"两处同一个源、重复的是字不是真相"那条论证
     * <b>不再需要</b>（不是被推翻，是宿主没了）。
     */
    String statusBlockText() {
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
     * 回执是粘性的（下一次动作覆盖），所以"分区拒收"不会一闪就没；★R93-③ 起这段文本画在
     * 底部带左段那块里（{@link #statusBlockText()}，★R94-① 起那块是 112×60），
     * 不再挤左列末行那个 18px 窄条（★那一行本轮已整行撤销）。
     * <p>
     * ★R95 S4：<b>通道持续化位在 ⇒ 倒计时段整体换成「通道常开」文案</b>（{@code always_on}）——
     * 常开态下没有"剩余秒数"可读（driver 批边界自动续批），仍显倒计时就是给玩家报一个不存在的
     * 终点。判据走 {@link #channelPersistActive()}（单源组合谓词 {@code PocketUpgradeSwitches#isActive}），
     * 客户端读 vanilla 同步过来的载体镜像。
     */
    String channelStatusText() {
        final String receipt = receiptText();
        final String status;
        if (channelPersistActive()) {
            status = StatCollector.translateToLocal("gtit.pocket.channel.always_on");
        } else if (timedRemainSeconds > 0) {
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

    /**
     * "模式 + 状态 + 主手限制"的<b>完整读法</b>（★R94-①：它的宿主从"左列末行那一行"换成了
     * 底部带那块说明文字——末行撤销时三条 tooltip 原样搬家，见
     * {@code NekoPocketBottomBand#statusBlock}）。★它一直是<b>额外</b>一层而不是截断补偿：
     * 正文在块里画得全，这里多给一份带主手限制的读法。
     */
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

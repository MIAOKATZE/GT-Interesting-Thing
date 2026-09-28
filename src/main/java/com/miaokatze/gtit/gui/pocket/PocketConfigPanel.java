package com.miaokatze.gtit.gui.pocket;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.miaokatze.gtit.common.items.pocket.PocketConstants;
import com.miaokatze.gtit.common.items.pocket.PocketInventory;
import com.miaokatze.gtit.common.items.pocket.PocketMageModes;
import com.miaokatze.gtit.common.items.pocket.PocketMagnetFilter;
import com.miaokatze.gtit.common.items.pocket.PocketUpgradeGuards;
import com.miaokatze.gtit.common.items.pocket.PocketUpgradeSwitches;
import com.miaokatze.gtit.common.items.pocket.PocketUpgradeType;

/**
 * 升级<b>配置面板</b>：主面板之上的次级面板，<b>五型各开各的一面、各排各的版</b>（★R97 S5，显式翻案
 * R96 台账 §R96 ①-3 / P-3 —— 用户 2026-09-27 明令「五种不同 UI」）。
 *
 * <h2>★R97 S5：为什么能从「一个面板」改成「五面」</h2>
 * R96 P-3 把五格左键都开<b>同一个</b>面板实例（"五行常驻一行一型"），当时的硬约束是 MUI2 2.3.88 起
 * {@code SecondaryPanel} 的面板缓存是<b>handler 实例级</b>的、且没有 widget 可见性 API ⇒ 一块缓存面板
 * 结构上不可能按"点击的型"变形。R97 对 2.3.91 的字节码复核给出另一条路：{@code IPanelHandler} 接口
 * 含 {@code deleteCachedPanel()}，而<b>开 N 个 handler 实例 = N 块互不串型的独立面板</b>（仓内先例：
 * {@code TerminalGiftPage:301-313} 的宿主身份比对 + 每宿主重建范式）。于是宿主侧（{@code NekoPocketPanel}）
 * 挂五枚 handler、每枚只建一型，本件把 {@link #build} 参数化：<b>一个面板类、五块面板实例</b>，
 * 不是五套 GUI 类。"五行常驻"随五 handler 消失——每面只画自己那一型的控件区。
 *
 * <h2>★按型分派的单源：sectionsOf + 五型五面</h2>
 * 每一型画什么，仍由 {@link #sectionsOf(PocketUpgradeType)} 这张<b>唯一分派表</b>决定（追加第 6 型必须在
 * 表里表态，不能靠 {@code default} 把新形状变成默认形状）。五型的内容段互不相同：
 * 容量 = 开关 + 容量读数一行（{@link Section#READOUT_CAPACITY}）；堆叠 = 开关 + 单格上限读数一行
 * （{@link Section#READOUT_STACK}）；通道持续化 = 开关 + 常开状态一行（{@link Section#READOUT_PERSIST}）；
 * 磁力 = 开关 + 72 格名单盘与控制块（{@link Section#MOUNT_MAGNET}）；
 * 魔法使 = 开关 + 挂载框（三行模式控件 + 框内元素容量读数）（{@link Section#MOUNT_MAGE}）。
 * ★R98 S4（DP-7）把三个「只有开/关」的简单型收到<b>只剩开关 + 一行读数</b>：堆叠面的「同尺说明」与通道面
 * 的「按钮禁用 / 帧带常亮」两条静态说明连同键一起撤（用户 2026-09-28 明令「只要开关的就要小巧一些」，并
 * 明确接受这两个事实从面板消失 ⇒ 它们唯一的玩家可见面回到 {@code gtit.pocket.config.switch.hint} 那条
 * tooltip 与主面板既有读数面）。
 * <b>面板尺寸也派生自这张表</b>（{@link #panelWidthOf} / {@link #panelHeightOf} 读各内容段的高宽预算），
 * ⇒ 改分派表 = 改面板几何，不会出现"表改了、几何还停在三型开关"的漂移。
 * "五型五面"由用例 {@code config_panel_dispatch_is_not_five_identical} 与
 * {@code config_panel_geometry_within_secondary_caps}（逐型像素账）双面钉住。
 *
 * <h2>三块职责（装配面与数据面分开，数据面必须能在纯 JVM 真跑）</h2>
 * <ol>
 * <li>{@link #build} = 纯装配：只画控件、只发码，★一个字节都不写档；</li>
 * <li>{@link #encode} / {@link #typeOfArg} / {@link #offOfArg}（开关）与 {@link #encodeMode} /
 * {@link #modeRowOfArg} / {@link #modeOnOfArg}（模式）= 动作码 arg 的编解码（双端同一条式子，
 * 越界一律丢弃；★R97 S5 对这两族<b>零改动</b>）；</li>
 * <li>{@link #commitSwitch} / {@link #commitMode} = ★<b>服务端仅有的两条写腿</b>（★R97 S5 零改动）。
 * 它们之所以住在本类而不是 {@link NekoPocketServerHandler}：<b>handler 需要 {@code NekoPocketPanel}，
 * 而面板在纯 JVM 里造不出来</b> ⇒ 判据留在 handler 里就只能"钉文本"，正是 R57/C3 那族"用例全绿、
 * 实机什么都没发生"。抽成静态纯函数后，三种拒绝与两种成交都能在 JVM 跑出来
 * （用例 {@code switch_write_refuses_three_cases}）。</li>
 * </ol>
 *
 * <h2>★客户端读的是什么、以及为什么没有第二条通道</h2>
 * 面上的读数（开关三态 / 容量 / 堆叠档 / 常开状态）来自 {@code NekoPocketPanel} 的既有读口 —— 它们读
 * <b>载体栈的 NBT 镜像或既有 SYNC 族同步值</b>（容量走 vanilla 槽同步、堆叠走 R97 R6 的
 * {@code SYNC_UPGRADE_ACTIVE} 位图镜像、常开走 {@code isChannelWorkLive} 那条既有腿）。本轮<b>不</b>为
 * 配置面板另开任何同步键：{@code registerSyncValues} 一处未加（EVA-1 方向丙 DP2「零新通道」）。
 *
 * <h2>几何账（★硬顶，装配期自断言，逐型各自成立）</h2>
 * 每型面板 = 边距 + 开关行（型名 + 开关）+ 该型的内容段 + 一条底部带（回执行 + 关闭钮并带，★R99 P2）：
 * 容量 {@code 214×80}、堆叠 {@code 214×76}、通道持续化 {@code 250×76}、磁力 {@code 236×294}、
 * 魔法使 {@code 200×154}（★派生自分派表，不手抄；★R99 整改后的现值：底带并带 −18、磁力面控制块
 * 单列化收窄至 236，R98 期的 214×98 / 214×94 / 250×94 / 356×312 / 200×172 全部作废）。
 * 逐型断言：≤ {@link #MAX_WIDTH}×{@link #MAX_HEIGHT}
 * （380×340）且<b>严格小于</b>主面板 {@code 398×360}（盖满主面板的次级面板读起来就是一块新屏，且非主面板
 * 恒可拖 —— {@code DraggablePanelWrapper} 按可视面余量做除算，贴边即除零/负数）；几何闭合；行带可用宽
 * 扛得住<b>本型</b>型名 + 三态的英文最坏串（逐型的像素账由用例拿两份 lang 真量，本件只钉
 * {@link #WORST_LABEL_WIDTH}=168 那条「最宽一型不许被整体缩水」的下界）。
 * <p>
 * <h2>★R99 P1：风格对齐 = 装饰层真的上五面（T3 档，对 R98 S4 那条"不上装饰"的改判）</h2>
 * R98 写在这里的「{@code NekoPocketDecoration} 是 package-private 且尺寸写死，复用即画成巨底」
 * **两条理由一真一假**：尺寸写死是真的（已由 {@code build(int,int)} 参数化解决），package-private
 * **不是障碍**——两个类同在 {@code gui.pocket} 包。用户 2026-09-28 实机判"裸浅灰底不合格"后拍板 T3：
 * 五面第一 child = {@code NekoPocketDecoration.build(w,h)}（cloth + PANEL 9-slice + 4 包角，
 * 不画铆钉与绳缝——次级面板没有可压的列缝，绳缝坐标是主面板底部带专属），零参 {@code build()}
 * 与主面板调用点一字不动。★可见边框只有约 7px（10px 边距里有 3px 平色），内容仍从 {@code MARGIN=6}
 * 起排——与主面板同口径，不是新缺陷。
 * <p>
 * <h2>★R98 S4 度量统一（历史档：字号与配色本来已同源，四处常数沿用）</h2>
 * 落地的四处常数：{@link #ROW_HEIGHT} 20→18、{@link #READOUT_HEIGHT} 20→18、{@link #CLOSE_HEIGHT}
 * 16→18、{@link #COLUMN_GAP} 6→4（= {@code NekoPocketPanel.COLUMN_GAP}）。派生量跟着走：
 * {@link #CONTENT_TOP} = 6+18+2 = 26；★{@link #BOTTOM_STACK} 自 R99 P2 起 = 4+22+6 = <b>32</b>
 * （回执行与关闭钮并成一条底带，R98 期公式 4+22+18+6 = 50 作废）。
 * <p>
 * ★对齐口径也一并定死（不是"看情况"）：面板级的<b>横贯读数</b>一律 {@code CenterLeft}（型名、回执、
 * 三面读数、磁力计数、魔法使容量行与容量块）；<b>框内件</b>与<b>多行说明块</b>保持 {@code TopLeft}
 * （两枚挂载框标题、磁力那句两行的不对称说明、魔法使的模式行）—— 多行块从顶排起才不会把首行推到框外。
 * 取证表把这一维列成"混用"，本轮收掉的是<b>没有理由的那一处</b>（容量块的 {@code TopLeft}），留下的是
 * 两种各有理由的对齐。
 * <p>
 * ★紧凑三型（容量 / 堆叠 / 通道持续化）的回执行按<b>两行</b>给高（{@link #RECEIPT_HEIGHT}=22）：它们的面板
 * 窄，英文最坏回执（守卫拒绝那两长句，逻辑宽 ≈ 417 ⇒ 0.6 档 ≈ 251px）在一百多 px 的盒里一行放不下；
 * 磁力面（★R99 P3 收窄后回执盒 166px）也放不下一行 —— 同一把尺，免得逐型分叉出第二份回执高度账。
 *
 * <h2>不进 Container、不进同步值</h2>
 * 面板里<b>不</b>放 {@code ItemSlot}/{@code SlotGroupWidget}，也不用任何 {@code syncValue}：
 * MUI2 的 {@code SecondaryPanel.openPanel()} 对"树里有同步值、面板本身没被同步"直接抛
 * {@code IllegalArgumentException}（库实测形体）⇒ 本件零同步值，从形状上避开那条抛，而不是靠"运行期
 * 别再点一次"。它因此也不占 {@code PocketSlots.TOTAL_REAL_SLOTS} 那条真实槽账（225 一字未动，
 * ★不变量 G1：本件不新增 {@code registerFactory}）。磁力 72 格名单盘走 {@link NekoMagnetGhostCell}
 * （phantom 侧），名单的同步载体是<b>主面板</b>上那枚 {@code StringSyncValue}（{@code SYNC_MAGNET}），
 * 本面板控件只读双源访问器 ⇒ 那条 {@code IllegalArgumentException} 结构性不可达。
 */
public final class PocketConfigPanel {

    /** 次级面板名前缀（MUI2 的面板标识与调试树；<b>不是</b>同步键，本件零同步值）。逐型后缀见 {@link #panelNameOf}。 */
    public static final String PANEL_NAME = "gtit.pocket.config";

    /** 外边距（与主面板同一档，两把尺子才对得上）。 */
    public static final int MARGIN = 6;
    /**
     * 开关行的高（★R98 S4 度量统一 20 → <b>18</b>）：这一行放的控件本身就是 {@link #SWITCH_HEIGHT} 18，
     * 主面板每一行也是 18 —— 旧值多出的那 2px 是「子面板自成一套第二把尺」的一部分。行与行之间的留白
     * 从此由 {@link #READOUT_GAP} <b>显式</b>给，不再藏在行高里（藏在行高里 ⇒ 每加一行就多送 2px，
     * 五面与主面板的栅格永远咬不合）。
     */
    public static final int ROW_HEIGHT = 18;
    /** 开关按钮的宽（"关闭 / 打开 / 未装"三态都要放得下）。 */
    public static final int SWITCH_WIDTH = 66;
    /** 开关按钮的高。 */
    public static final int SWITCH_HEIGHT = 18;
    /**
     * 回执行的高（★R97 S5 起 22 = 两行 × 10px 的账）：紧凑三型的面板窄，英文最坏回执一行放不下
     * （见类 javadoc 几何账那段），给两行；用例按 {@code 行数 × 10 ≤ 本常数} 逐型量。
     */
    public static final int RECEIPT_HEIGHT = 22;
    /**
     * 关闭按钮的高（★R98 S4 度量统一 16 → <b>18</b>）：主面板上每一枚按钮（通道钮 88×18、绑定钮 106×18、
     * 币栏条 88×18）都是 18 高，本件其它控件也是 18；同一张 {@code BUTTON}（88×18，N=4）被拉成 16 与 18
     * 两种高，是取证 {@code 02-gui.md} §4 点名的「同一套 UI 里长出两种按钮」那一维。连带
     * {@link #BOTTOM_STACK} 的派生式自动跟（48 → 50）。
     */
    public static final int CLOSE_HEIGHT = 18;
    /** 关闭按钮的宽。 */
    public static final int CLOSE_WIDTH = 56;
    /**
     * 一条<b>单行</b>读数的高（★R98 S4 度量统一 20 → <b>18</b>，与 {@link #ROW_HEIGHT} 同一把尺；
     * 10px 文字之外的留白由 {@link #READOUT_GAP} 给）。
     */
    public static final int READOUT_HEIGHT = 18;
    /** 读数行之间的缝（也用于开关行与首个内容段之间）。 */
    public static final int READOUT_GAP = 2;
    /**
     * 列间距（磁力面：控制块与 72 格盘之间）。★R98 S4 度量统一 6 → <b>4</b> =
     * {@code NekoPocketPanel.COLUMN_GAP}（主面板三列之间也是这一档，两把尺子才咬得上）。
     */
    public static final int COLUMN_GAP = 4;
    /** 各型内容段的起始 y = 边距 + 开关行 + 一条缝（★单源，装配侧与断言侧同一个数）。 */
    public static final int CONTENT_TOP = MARGIN + ROW_HEIGHT + READOUT_GAP;
    /**
     * 底部带的纵向预算（★R99 P2 并带改判：回执行与关闭钮<b>同带并排</b>，不再上下两行）：
     * 回执行之上的缝 + 回执行 + 边距。R98 期公式 {@code 4 + RECEIPT_HEIGHT + CLOSE_HEIGHT + MARGIN}
     * （= 50）作废——旧形状里关闭钮独占一行，"右下角那枚离所有东西都远"就是它给的。
     */
    public static final int BOTTOM_STACK = 4 + RECEIPT_HEIGHT + MARGIN;
    /** 次级面板的宽度硬顶（见类 javadoc 的几何账）。 */
    public static final int MAX_WIDTH = 380;
    /** 次级面板的高度硬顶。 */
    public static final int MAX_HEIGHT = 340;
    /**
     * 英文最坏型名串（"Channel Persistence Upgrade Module：Not installed"，逻辑宽 280）在 0.6 档要的
     * 像素宽。★逐型行盒按 {@link #labelWidthOf} 给（短型给短盒，这正是五面差异化的一部分），本常数只钉
     * 「五型里最宽的那一盒不许被整体缩到 168 以下」——缩了，通道持续化那一行的英文就折行顶穿行盒。
     */
    public static final int WORST_LABEL_WIDTH = 168;
    /**
     * 逐型的型名行盒宽（像素预算表，下标 = ordinal；真串真宽由用例拿两份 lang 逐型量，这里只给盒子）。
     * 五型各自的英文最坏串（型名 + "：" + 三态里最长的"未装"）在 0.6 档分别要 ≈131/131/123/168/116px，
     * 本表逐型给盒（+3 上取整余量）。★这是<b>数据表不是分派表</b>：装什么内容仍只由 {@link #sectionsOf} 说。
     */
    private static final int[] LABEL_BOX_WIDTHS = { 134, 134, 126, 170, 120 };

    // ==== ★R97 S5：磁力面的几何（★12×6 = 108×216 是硬形状，仍是五面里唯一带格盘的那面） ====

    /** 名单格盘的单格边长（★与主面板三栏同一把尺，不另立 16/20 的第二档）。 */
    public static final int CELL = NekoPocketPanel.GRID;
    /** 名单格盘的列数（★单源取数据层常量，不在 GUI 侧抄第二份 6）。 */
    public static final int MAGNET_COLUMNS = PocketConstants.MAGNET_FILTER_COLUMNS;
    /** 名单格盘的行数（★同上；朝向 = 12 行 × 6 列，与源质格同形但★不引它那两个常量）。 */
    public static final int MAGNET_ROWS = PocketConstants.MAGNET_FILTER_ROWS;
    /** 盘面宽 = {@code 6 × 18 = 108}。 */
    public static final int MAGNET_GRID_WIDTH = MAGNET_COLUMNS * CELL;
    /** 盘面高 = {@code 12 × 18 = 216}。 */
    public static final int MAGNET_GRID_HEIGHT = MAGNET_ROWS * CELL;
    /** 磁力挂载框的宽（盘面 + 左右各 1px 框边）。 */
    public static final int MAGNET_FRAME_WIDTH = MAGNET_GRID_WIDTH + 2;
    /** 磁力挂载框的高（标题行 + 盘面 + 上下各 1px 框边）。 */
    public static final int MAGNET_FRAME_HEIGHT = ROW_HEIGHT + MAGNET_GRID_HEIGHT + 2;
    /** 磁力挂载框的 y（★内容段顶：开关行之下第一条）。 */
    public static final int MAGNET_FRAME_Y = CONTENT_TOP;
    /** 控制块（三态 / 目标 / 清空 / 计数 / 说明）的 x —— 排左列（格盘在右）。 */
    public static final int MAGNET_CONTROL_X = MARGIN;
    /** 控制块的 y 起点 = 内容段顶。 */
    public static final int MAGNET_CONTROL_Y = CONTENT_TOP;
    /** 控制块里一行的按钮宽。 */
    public static final int MAGNET_BUTTON_WIDTH = 110;
    /** 控制块里一行的按钮高。 */
    public static final int MAGNET_BUTTON_HEIGHT = SWITCH_HEIGHT;
    /** 控制块行间距。 */
    public static final int MAGNET_ROW_GAP = 4;
    /**
     * 控制块整列的宽（★R99 P3 M-1 单列化：三钮 + 计数 + 说明<b>竖排一列</b>，宽 = 一枚按钮；
     * R98 期「两只按钮并排 + 缝」的 230 作废——那正是磁力面左半成片空洞的结构成因，取证
     * {@code 02-ui-style-layout.md} §3-2）。说明行同宽 −2。
     */
    public static final int MAGNET_CONTROL_WIDTH = MAGNET_BUTTON_WIDTH;
    /** 计数读数行的宽（★R99 P3 起它在左列第 4 段，与按钮同一把尺；旧"第二列"口径作废）。 */
    public static final int MAGNET_COUNT_WIDTH = MAGNET_BUTTON_WIDTH;
    /**
     * ★三态不对称说明行的高（★R99 P3 起 <b>30 = 三行 × 10px</b>）：说明盒从 228 窄到 108 之后，
     * 英文那句按保守前进量要折三行，24 装不下；逐型像素账由 {@code magnet_panel_text_fits_its_boxes}
     * 拿两份 lang 真量（★R99 P0 补的判据，此前这条串不在任何折行账里）。
     */
    public static final int MAGNET_NOTE_HEIGHT = 30;

    // ==== ★R97 S5：魔法使面的几何（挂载框 + 三行模式控件 + ★R98 S4 起框内再收一行元素容量读数） ====

    /** 挂载框标题占的高（= {@link #ROW_HEIGHT}，标题与开关行同一把尺）。 */
    public static final int MOUNT_TITLE_HEIGHT = ROW_HEIGHT;
    /** 魔法使挂载框里<b>一行模式控件</b>的高（= 一枚按钮的高，与 {@link #SWITCH_HEIGHT} 同档）。 */
    public static final int MODE_ROW_HEIGHT = 18;
    /** 两行模式控件之间的缝。 */
    public static final int MODE_ROW_GAP = 1;
    /** 模式控件那枚小按钮的宽（只装"关掉 / 打开"两枚二字串，★不装三态 —— 三态在行首读数里）。 */
    public static final int MODE_BUTTON_WIDTH = 30;
    /** 魔法使挂载框的 x（★左起：这一面的主体就是这一框）。 */
    public static final int MAGE_FRAME_X = MARGIN;
    /** 魔法使挂载框的 y（内容段顶）。 */
    public static final int MAGE_FRAME_Y = CONTENT_TOP;
    /**
     * 魔法使挂载框的宽（★R98 S4 144 → <b>170</b>）：容量行搬进框内以后，框必须装得下它那一句英文最坏串
     * （{@code Element capacity: 500 each, 3000 across all six} 在 0.6 档 ≈ 157px）+ 两道框边；
     * 170 ≤ 本型行带宽 188 ⇒ <b>面板宽一分不长</b>（{@link #panelWidthOf} 取的是两者里的 max）。
     * 旧值 144 是「只装标题 + 三行模式」的账，那条账在容量行还在框外时成立。
     */
    public static final int MAGE_FRAME_WIDTH = 170;
    /** 框内模式行的纵向预算 = 行数 × 行高 + 行间缝。 */
    public static final int MAGE_MODE_STACK_HEIGHT = modeRowCount() * MODE_ROW_HEIGHT
        + (modeRowCount() - 1) * MODE_ROW_GAP;
    /**
     * 魔法使挂载框的高 = 上下框边 + 标题 + 模式行栈 + ★框内的元素容量行（含它上面那条缝）。
     * R98 S4 之前这一面是「框 + 框下一条悬空读数」（取证 {@code 02-gui.md} §4-5：框的语义"这里面是一组"
     * 与磁力框"这里面是整盘"不一致），那一行现在进框，框语义统一。
     */
    public static final int MAGE_FRAME_HEIGHT = 2 + MOUNT_TITLE_HEIGHT
        + MAGE_MODE_STACK_HEIGHT
        + READOUT_GAP
        + READOUT_HEIGHT;
    /** 模式行里<b>标签</b>的宽（派生：框宽 − 2px 缝 − 那枚小按钮 − 2px 缝，★不手抄）。 */
    public static final int MODE_LABEL_WIDTH = MAGE_FRAME_WIDTH - 2 - 2 - MODE_BUTTON_WIDTH;
    /**
     * 容量读数块的高（★两行盒 = {@link #RECEIPT_HEIGHT}）：这一句用的是主面板那把口径
     * （{@code gtit.pocket.fluid.capacity}，双轨 20M/2G + 合计），英文最坏串在 202px 的盒里要折两行 ——
     * 用例 {@code config_panel_geometry_within_secondary_caps} 拿两份 lang 逐型量，量到两行就必须留两行
     * 的高（★R98 逐型几何目标给的 94 是「一行装得下」那一支，实测吃不下 ⇒ 本型留在 98，不削盒）。
     */
    public static final int CAPACITY_READOUT_HEIGHT = RECEIPT_HEIGHT;

    /**
     * 一行（或一面）的<b>内容段</b>（★按型分派，"五型五面"的正身）。{@link #SWITCH} 是公共段
     * （五面顶上都那一行），其余五段<b>每型恰一段</b>：三个读数段（紧凑面）与两个挂载段（磁力 / 魔法使）。
     */
    public enum Section {
        /** 开 / 关切换（五型都有，恒为第一段）。 */
        SWITCH,
        /** 容量面的读数段：流体条双轨口径（20M/2G per tank，合计 18 倍）。 */
        READOUT_CAPACITY,
        /** 堆叠面的读数段：单格上限两档（64 → 1024）一行。★R98 S4 DP-7 撤掉同尺说明那一行。 */
        READOUT_STACK,
        /** 通道持续化面的读数段：常开现状一行。★R98 S4 DP-7 撤掉按钮禁用与帧带常亮两条说明。 */
        READOUT_PERSIST,
        /** 磁力面的挂载段：72 格 phantom 名单盘 + 三态 / 目标 / 清空 / 计数 / 说明。 */
        MOUNT_MAGNET,
        /** 魔法使面的挂载段：三行模式控件 + 元素容量读数。 */
        MOUNT_MAGE
    }

    /** 一型的开关现状（★三态而不是两态："没装"与"关着"必须分得开）。 */
    public enum SwitchState {
        /** 在档上且开着。 */
        ON,
        /** 在档上且被关着。 */
        OFF,
        /** 这一型没在档上 ⇒ 没有开关可切。 */
        ABSENT
    }

    /** {@link #commitSwitch} 的结论（★枚举而不是布尔：拒绝有<b>三种</b>不同说法，并成一条就是假读数）。 */
    public enum Outcome {
        /** 关掉了一型。 */
        TURNED_OFF,
        /** 打开了一型。 */
        TURNED_ON,
        /** 目标值就是现值 ⇒ 零写入、零同步包（连点不刷包那一条）。 */
        NO_CHANGE,
        /** 这一型没在档上（既没生效也没被关着）。 */
        NOT_ON_RECORD,
        /** 容量守卫拒绝：关掉以后现有流体装不下。 */
        REFUSE_CAPACITY,
        /** 堆叠守卫拒绝：关掉以后现有的堆装不下。 */
        REFUSE_STACK
    }

    private PocketConfigPanel() {}

    static {
        // ★装配期自断言（形状照 NekoPocketPanel 的 static 块：判据一红就要求改动方回来看账，
        // 而不是留一条"派生式跟着漂、永不红"的活口）。★本块只碰整数与字符串，不碰纹理表
        // （{@code PocketGuiTextures} 的初始化会连带拉 fastutil，零依赖回归套件的类路径上没有它 ——
        // 同 {@code NekoPocketBottomBand#upgradeCellBackground} 记过的实测）。
        if (LABEL_BOX_WIDTHS.length != PocketUpgradeType.values().length) {
            throw new IllegalStateException(
                "[pocket] 型名行盒表长度 " + LABEL_BOX_WIDTHS.length
                    + " ≠ 型数 "
                    + PocketUpgradeType.values().length
                    + "（★追加第 6 型必须同步给盒宽与 sectionsOf 表态，两处一起改）");
        }
        int widestLabel = 0;
        for (final PocketUpgradeType type : PocketUpgradeType.values()) {
            final int width = panelWidthOf(type);
            final int height = panelHeightOf(type);
            if (width > MAX_WIDTH || height > MAX_HEIGHT) {
                throw new IllegalStateException(
                    "[pocket] " + type
                        + " 的配置面板越过次级面板硬顶: "
                        + width
                        + "x"
                        + height
                        + " 顶="
                        + MAX_WIDTH
                        + "x"
                        + MAX_HEIGHT
                        + "（★非主面板恒可拖，DraggablePanelWrapper 按可视面余量做除算 ⇒ 贴边即除零/负数）");
            }
            if (width >= NekoPocketPanel.WIDTH || height >= NekoPocketPanel.HEIGHT) {
                throw new IllegalStateException(
                    "[pocket] " + type
                        + " 的配置面板不再严格小于主面板: "
                        + width
                        + "x"
                        + height
                        + "，主面板="
                        + NekoPocketPanel.WIDTH
                        + "x"
                        + NekoPocketPanel.HEIGHT
                        + "（盖满主面板的次级面板读起来就是一块新屏，且拖动余量归零）");
            }
            // ★几何闭合（派生式防手改）：行带 + 两个边距必须恰好收进面板宽；内容段下沿必须恰好落在
            // 底部两件之上。两条都是「派生式 ⇒ 恒等」，有人手抄面板尺寸时会在这里先红。
            if (MARGIN + switchRowWidthOf(type) + MARGIN > width) {
                throw new IllegalStateException("[pocket] " + type + " 行带横向不闭合: " + switchRowWidthOf(type));
            }
            for (final Section section : sectionsOf(type)) {
                if (CONTENT_TOP + sectionContentHeight(section) > height - BOTTOM_STACK) {
                    throw new IllegalStateException(
                        "[pocket] " + type
                            + " 的内容段纵向不闭合: "
                            + section
                            + " 下沿="
                            + (CONTENT_TOP + sectionContentHeight(section))
                            + " 面板高="
                            + height);
                }
            }
            widestLabel = Math.max(widestLabel, labelWidthOf(type));
        }
        // ★型名行可用宽的下界（★这里只钉"最宽一型扛得住英文最坏串"这件事被写死在常数上，逐型真串真宽
        // 由用例 config_panel_geometry_within_secondary_caps 拿两份 lang 逐型算 —— 短型的短盒是本片
        // 排版差异化的一部分，不在这条下界的管辖里）。
        if (widestLabel < WORST_LABEL_WIDTH) {
            throw new IllegalStateException(
                "[pocket] 型名行盒被整体缩水: 最宽一型 " + widestLabel + " < " + WORST_LABEL_WIDTH + "（0.6 档的英文最坏串会折行顶穿 20px 行盒）");
        }
        // ★磁力格盘的算术（★本面整个宽账就是被 108×216 这块硬形状逼出来的）。
        if (MAGNET_COLUMNS * CELL != MAGNET_GRID_WIDTH || MAGNET_ROWS * CELL != MAGNET_GRID_HEIGHT) {
            throw new IllegalStateException("[pocket] 栅格乘式与盘面尺寸不符（★格数不得是裸字面量）");
        }
        if (MAGNET_ROWS * MAGNET_COLUMNS != PocketConstants.MAGNET_FILTER_SLOTS) {
            throw new IllegalStateException("[pocket] 磁力格盘行列乘积不等于数据层的条目预算");
        }
        if (MAGNET_GRID_WIDTH > MAGNET_FRAME_WIDTH - 2 || MAGNET_GRID_HEIGHT > MAGNET_FRAME_HEIGHT - ROW_HEIGHT - 2) {
            throw new IllegalStateException(
                "[pocket] 72 格盘放不下自己的框: 盘面=" + MAGNET_GRID_WIDTH
                    + "x"
                    + MAGNET_GRID_HEIGHT
                    + " 框内="
                    + (MAGNET_FRAME_WIDTH - 2)
                    + "x"
                    + (MAGNET_FRAME_HEIGHT - ROW_HEIGHT - 2));
        }
        if (MAGNET_CONTROL_X + MAGNET_CONTROL_WIDTH + COLUMN_GAP + MAGNET_FRAME_WIDTH + MARGIN
            != panelWidthOf(magnetType())) {
            throw new IllegalStateException(
                "[pocket] 磁力面横向不闭合（控制块 + 缝 + 格盘 + 边距 ≠ 面板宽）: " + panelWidthOf(magnetType()));
        }
        if (magnetNoteY() + MAGNET_NOTE_HEIGHT > panelHeightOf(magnetType()) - BOTTOM_STACK) {
            throw new IllegalStateException(
                "[pocket] 磁力控制块说明行纵向不闭合: " + (magnetNoteY() + MAGNET_NOTE_HEIGHT)
                    + " > "
                    + (panelHeightOf(magnetType()) - BOTTOM_STACK));
        }
        if (MAGNET_COUNT_WIDTH <= 0) {
            throw new IllegalStateException("[pocket] 磁力计数读数行没有宽度可占");
        }
        // ★魔法使框里的模式行必须落在框内（框高进面板高的纵向预算 ⇒ 这条红的同时会带走上面那条
        // 「内容段纵向不闭合」，所以单独写一条，报的是"行多了"而不是"面板高了"）。
        if (MOUNT_TITLE_HEIGHT + modeRowCount() * (MODE_ROW_HEIGHT + MODE_ROW_GAP) > MAGE_FRAME_HEIGHT) {
            throw new IllegalStateException(
                "[pocket] 魔法使挂载框放不下 " + modeRowCount()
                    + " 行模式控件: 框高="
                    + MAGE_FRAME_HEIGHT
                    + "，需要="
                    + (MOUNT_TITLE_HEIGHT + modeRowCount() * (MODE_ROW_HEIGHT + MODE_ROW_GAP)));
        }
        if (MODE_LABEL_WIDTH < 40 || MODE_LABEL_WIDTH + MODE_BUTTON_WIDTH + 2 > MAGE_FRAME_WIDTH - 2) {
            throw new IllegalStateException(
                "[pocket] 模式行横向不闭合（标签 + 按钮顶出挂载框）: 标签=" + MODE_LABEL_WIDTH + "，按钮=" + MODE_BUTTON_WIDTH);
        }
        // ★R98 S4：元素容量行搬进框内以后的两条闭合（框语义 = "这里面是一组"，行不许再悬在框外）。
        // 纵向：行的下沿必须落在框底（含 1px 框边）之内；横向：行的盒必须收得进框内可用宽。
        // ★文字真宽由用例逐型像素账量（这里只钉盒子，同 WORST_LABEL_WIDTH 那条例的分工）。
        if (mageCapacityY() + READOUT_HEIGHT > MAGE_FRAME_Y + MAGE_FRAME_HEIGHT - 1) {
            throw new IllegalStateException(
                "[pocket] 魔法使框装不下框内的元素容量行: 行下沿=" + (mageCapacityY() + READOUT_HEIGHT)
                    + " 框底="
                    + (MAGE_FRAME_Y + MAGE_FRAME_HEIGHT - 1));
        }
        if (mageCapacityWidth() < 40 || MAGE_FRAME_X + 1 + mageCapacityWidth() > MAGE_FRAME_X + MAGE_FRAME_WIDTH - 1) {
            throw new IllegalStateException("[pocket] 魔法使容量行的盒顶出挂载框: 盒宽=" + mageCapacityWidth());
        }
        // ★分派表与内容段族必须对得上：五个值逐个点名 ⇒ 追加第 6 型时 sectionsOf 不表态就先在这里红。
        int secondSections = 0;
        for (final PocketUpgradeType type : PocketUpgradeType.values()) {
            final List<Section> sections = sectionsOf(type);
            if (sections.size() != 2 || sections.get(0) != Section.SWITCH) {
                throw new IllegalStateException(
                    "[pocket] " + type
                        + " 的内容段族不是「开关 + 恰一段专属内容」: "
                        + sections
                        + "（★五型五面：每面 = 公共开关行 + 本型自己的那一段，不多画也不少画）");
            }
            secondSections++;
        }
        if (secondSections != PocketUpgradeType.values().length) {
            throw new IllegalStateException("[pocket] 分派表点名数 " + secondSections + " ≠ 型数（表与枚举不齐）");
        }
    }

    // ================================================================== 数据面（纯 JVM 可跑）

    /**
     * ★唯一分派表：这一型的面板由哪些内容段组成。
     * <p>
     * 五个值<b>逐个点名</b>（不写成 {@code default} 一把梭）就是这条判据的全部内容：容量 = 开关 + 容量读数，
     * 堆叠 = 开关 + 单格上限读数，通道持续化 = 开关 + 常开状态与说明，磁力 = 开关 + 名单盘挂载位，
     * 魔法使 = 开关 + 模式挂载位。若这里退化成"五型同形"，五面就塌回一张脸 —— 这条退化<b>不会</b>让
     * 编译或任何既有读数变红，所以它由用例 {@code config_panel_dispatch_is_not_five_identical} 钉。
     */
    public static List<Section> sectionsOf(PocketUpgradeType type) {
        if (type == null) {
            return Collections.singletonList(Section.SWITCH);
        }
        switch (type) {
            case CAPACITY:
                return Arrays.asList(Section.SWITCH, Section.READOUT_CAPACITY);
            case STACK:
                return Arrays.asList(Section.SWITCH, Section.READOUT_STACK);
            case MAGNET:
                return Arrays.asList(Section.SWITCH, Section.MOUNT_MAGNET);
            case CHANNEL_PERSIST:
                return Arrays.asList(Section.SWITCH, Section.READOUT_PERSIST);
            case MAGE:
                return Arrays.asList(Section.SWITCH, Section.MOUNT_MAGE);
            default:
                // 走不到（上面已穷举五个值）。留这一支的意义：将来追加第 6 型而忘了在上面表态时，
                // 这里给的是"与开关同形"的最小安全回落，同时 static 块的段族对账会先一步炸红。
                return Collections.singletonList(Section.SWITCH);
        }
    }

    /** 这一型是否有挂载位（★由 {@link #sectionsOf} 派生，别处不得再写一遍型清单）。 */
    public static boolean hasMount(PocketUpgradeType type) {
        final List<Section> sections = sectionsOf(type);
        return sections.contains(Section.MOUNT_MAGNET) || sections.contains(Section.MOUNT_MAGE);
    }

    /** 挂载框的标题键；这一型没有挂载位 ⇒ {@code null}（★调用方不许把 null 读成空串继续画框）。 */
    public static String mountTitleKey(PocketUpgradeType type) {
        final List<Section> sections = sectionsOf(type);
        if (sections.contains(Section.MOUNT_MAGNET)) {
            return "gtit.pocket.config.mount.magnet";
        }
        if (sections.contains(Section.MOUNT_MAGE)) {
            return "gtit.pocket.config.mount.mage";
        }
        return null;
    }

    // ==================================================== ★R98 S4（DP-4 正判据②）三个简单型各自的读数键
    //
    // ★R98 把 CAPACITY / STACK / CHANNEL_PERSIST 三面收到「开关 + 一行读数」（DP-7）之后，"五面不是同一张脸"
    // 这件事不能再靠三面的<b>高度差</b>来证（三面本来就快一样高了）。这一族常数把那"一行"逐型点名：每型恰
    // 一条<b>本型独有</b>的读数键，装配侧与用例读的是同一张表 ⇒ 任何两型共用同一条（＝ 把三型并成一张脸）
    // 会在用例 config_panel_geometry_within_secondary_caps 的判据②上直接红。

    /** 容量面那一行读的键（★与主面板同一句、同一个格式源 {@code NekoPocketPanel#capacityReadoutText}，本件只点名不重写第二份）。 */
    public static final String READOUT_CAPACITY_KEY = "gtit.pocket.fluid.capacity";
    /** 堆叠面那一行读的键（★本型独有：单格上限 64 ↔ 1024）。 */
    public static final String READOUT_STACK_KEY = "gtit.pocket.config.stack.limit";
    /** 通道持续化面那一行<b>未常开</b>支的键（★本型独有；常开支复用的是主面板那条 {@code channel.always_on}）。 */
    public static final String READOUT_PERSIST_KEY = "gtit.pocket.config.persist.idle";

    /**
     * 这一型读数段<b>本型独有</b>的那条 lang 键（★DP-4 正判据② 的单源：判据经 {@link #sectionsOf} 的第二段
     * 走，不在这里抄第二份型清单）。两个挂载型回 {@code null} —— 它们的读数住在挂载段里（磁力的计数与不对称
     * 说明、魔法使的框内容量行），不占这一族。
     */
    public static String readoutKeyOf(PocketUpgradeType type) {
        final List<Section> sections = sectionsOf(type);
        if (sections.size() < 2) {
            return null;
        }
        switch (sections.get(1)) {
            case READOUT_CAPACITY:
                return READOUT_CAPACITY_KEY;
            case READOUT_STACK:
                return READOUT_STACK_KEY;
            case READOUT_PERSIST:
                return READOUT_PERSIST_KEY;
            default:
                return null;
        }
    }

    // ========================================================== 魔法使挂载框里的「四模式」数据面
    //
    // ★★四模式 = ①「魔法使」主开关（本件第 {@link Section#SWITCH} 段，S1/S2 已定稿，一条码都没加）
    // ②结晶模式 ③猫猫币充能 ④源质转换。后三条住在魔法使那一面，位序单源在
    // {@link PocketConstants#MAGE_MODE_BITS}（★本件不写第二份位清单，见 {@link #modeBit(int)}）。

    /** 模式行的<b>标签</b>键（★按 {@link PocketConstants#MAGE_MODE_BITS} 的行序，两个下标空间同一个数）。 */
    private static final String[] MODE_LABEL_KEYS = { "gtit.pocket.config.mode.crystal", "gtit.pocket.config.mode.coin",
        "gtit.pocket.config.mode.transmute" };
    /** 模式行的 tooltip 键（★三行共用一条：讲的是"这一行的开关意味着什么"，与具体哪条模式无关）。 */
    public static final String MODE_HINT_KEY = "gtit.pocket.config.mode.hint";

    /** 模式行数 = 位的<b>唯一</b>来源（★追加第四种模式时本件不加控件、只加标签键，几何自断言会红）。 */
    public static int modeRowCount() {
        return PocketConstants.MAGE_MODE_BITS.length;
    }

    /** 第 {@code row} 行的位（越界 ⇒ {@code 0}，★0 不是任何已定义位 ⇒ 下游一律判非法，不会误写）。 */
    public static int modeBit(int row) {
        if (row < 0 || row >= PocketConstants.MAGE_MODE_BITS.length) {
            return 0;
        }
        return PocketConstants.MAGE_MODE_BITS[row];
    }

    /** 位 → 行号（★找不到 ⇒ −1；本件与动作码都经它，别处不得再抄一份"哪位第几行"）。 */
    public static int modeRowOf(int bit) {
        for (int row = 0; row < PocketConstants.MAGE_MODE_BITS.length; row++) {
            if (PocketConstants.MAGE_MODE_BITS[row] == bit) {
                return row;
            }
        }
        return -1;
    }

    /** 模式行的标签键；非法行 ⇒ {@code null}（★调用方不许把 null 读成空串继续画行）。 */
    public static String modeLabelKey(int row) {
        if (row < 0 || row >= MODE_LABEL_KEYS.length || row >= PocketConstants.MAGE_MODE_BITS.length) {
            return null;
        }
        return MODE_LABEL_KEYS[row];
    }

    /** 模式动作码 arg 的编码：{@code row * 2 + onBit}（★与开关那条 {@code ordinal*2+offBit} <b>同形而不同表</b>）。 */
    public static int encodeMode(int row, boolean on) {
        if (modeLabelKey(row) == null) {
            return -1;
        }
        return row * 2 + (on ? 1 : 0);
    }

    /** 模式 arg → 行号；越界（含 −1）⇒ −1，调用方必须丢弃这条包而不是猜一行。 */
    public static int modeRowOfArg(int arg) {
        if (arg < 0 || arg >= modeRowCount() * 2) {
            return -1;
        }
        return arg / 2;
    }

    /** 模式 arg → 玩家请求的目标（{@code true} = 要<b>开</b>；★与开关那条的"off 位"方向相反，见 {@link #encodeMode}）。 */
    public static boolean modeOnOfArg(int arg) {
        return (arg & 1) != 0;
    }

    /**
     * 某一行的模式位当前开不开（★读<b>载体栈 NBT</b>，与 {@link #switchState} 同一条通道、同一个理由）。
     * <p>
     * ★本方法<b>不</b>判"魔法使在不在档上"：那一位的读数已经在自己那一行上显示过一次了，
     * 在模式行上再显示一次会把"没装这个升级"读成"三条模式都关着"——那是假读数（口径同
     * {@link #switchLabelKey} 那句"未装不许显示成已关闭"）。提交腿才判它（{@link #commitMode}）。
     */
    public static boolean modeState(ItemStack carrier, int row) {
        if (carrier == null) {
            return false;
        }
        final int bit = modeBit(row);
        return bit != 0 && PocketMageModes.on(carrier.getTagCompound(), bit);
    }

    /** 下一次点击应请求的目标（★现读：面板是缓存件、行是常驻件，与 {@link #nextOff} 同一条纪律）。 */
    public static boolean nextModeOn(ItemStack carrier, int row) {
        return !modeState(carrier, row);
    }

    /**
     * ★服务端唯一的<b>模式</b>写腿（与 {@link #commitSwitch} 并列的第二条写腿，★两条不共用一个入口）。
     * <p>
     * 三判里这里只跑两判：①"魔法使这一型在不在档上"（★判据经 {@link PocketMageModes#masterOnRecord}，
     * <b>不</b>在本件直读位图 —— R96-S1b 门 F 把本件对 {@code PocketUpgradeSwitches.} 的引用钉成恰 4 处，
     * 多一处就是长出第二条不点名的读腿）；②落档，返回"本次是否真的改变了"。
     * ★<b>没有</b>第三判（容量/堆叠那两类关闭守卫）：模式位只闸"这条被动动不动手"，
     * 关掉它不会让任何东西缩尺，拿守卫拒它是把两码事并成一条。
     * <p>
     * <b>目标值语义</b>（与开关同一条）：请求的是"开/关"而不是"翻一下" ⇒ 同值重复包走
     * {@link Outcome#NO_CHANGE}，零写入、零同步包。
     * <p>
     * <b>无档不建档</b>（R53c）：{@code carrier} 为 null 或没有 NBT 根 ⇒ 判
     * {@link Outcome#NOT_ON_RECORD}（连"未固化"都算不上，就是一句都没问）。
     */
    public static Outcome commitMode(ItemStack carrier, int row, boolean wantOn) {
        final int bit = modeBit(row);
        if (bit == 0) {
            // 非法行：零写入。★真正的"丢包"在 handler 的解码腿（modeRowOfArg 返 −1 就 return），
            // 走到这里只可能是调用方自己传错了行号 ⇒ 报 NO_CHANGE 而不是造一条新说法。
            return Outcome.NO_CHANGE;
        }
        if (!PocketMageModes.masterOnRecord(carrier)) {
            return Outcome.NOT_ON_RECORD;
        }
        final net.minecraft.nbt.NBTTagCompound root = carrier.getTagCompound();
        if (root == null) {
            return Outcome.NOT_ON_RECORD;
        }
        if (!PocketMageModes.write(root, bit, wantOn)) {
            return Outcome.NO_CHANGE;
        }
        return wantOn ? Outcome.TURNED_ON : Outcome.TURNED_OFF;
    }

    /** 动作码 arg 的编码：{@code ordinal * 2 + offBit}（★一条式子，双端同读；非法入参 ⇒ −1）。 */
    public static int encode(PocketUpgradeType type, boolean off) {
        if (type == null || type.ordinal() >= PocketConstants.UPGRADE_SLOTS) {
            return -1;
        }
        return type.ordinal() * 2 + (off ? 1 : 0);
    }

    /** arg → 型；越界（含 −1）⇒ {@code null}，调用方必须丢弃这条包而不是猜一个型。 */
    public static PocketUpgradeType typeOfArg(int arg) {
        final PocketUpgradeType[] values = PocketUpgradeType.values();
        if (arg < 0 || arg >= values.length * 2) {
            return null;
        }
        return values[arg / 2];
    }

    /** arg → 玩家请求的目标（{@code true} = 要关掉）。 */
    public static boolean offOfArg(int arg) {
        return (arg & 1) != 0;
    }

    /**
     * 这一型当前的开关现状（★读<b>载体栈 NBT</b>：服务端权威、客户端 vanilla 镜像，见类 javadoc）。
     * <p>
     * "在不在档上"用 {@code isActive ∨ isOff} 而<b>不是</b>第三次直读效果位图。为什么这不是近似式：
     * {@code isActive = 位图 ∧ ¬关闭}、{@code isOff = 关闭位}；关闭位只有
     * {@link PocketUpgradeSwitches#setOff} 写得出去，而 {@code setOff} 的类外调用点被门禁钉成
     * "恰 1 且点名"（唯一名点就是 {@link #commitSwitch}），本方法又只在两位皆 0 时<b>拒写</b>
     * ⇒ <b>归纳基</b>是没有关闭位的干净档，<b>归纳步</b>是"只给已在档上的型写关闭位"，
     * 于是"关闭位在场"必然意味着"位图那一位在场"，{@code isActive ∨ isOff} 与"位图有位"
     * 在<b>可达状态集</b>上等值。★直接问 {@code PocketUpgrades.hasUpgrade} 也成立，但那是门禁 E 段
     * 明令归零的第三条直调腿（R96 计划 §5 S2 验收 6：全仓总点数 8 → 2），拿它换掉上面这段论证不合算。
     */
    public static SwitchState switchState(ItemStack carrier, PocketUpgradeType type) {
        if (carrier == null || type == null) {
            return SwitchState.ABSENT;
        }
        if (PocketUpgradeSwitches.isActive(carrier, type)) {
            return SwitchState.ON;
        }
        return PocketUpgradeSwitches.isOff(carrier, type) ? SwitchState.OFF : SwitchState.ABSENT;
    }

    /** 下一次点击应请求的目标（★现读，不是装配期快照：面板是缓存件、行是常驻件）。 */
    public static boolean nextOff(ItemStack carrier, PocketUpgradeType type) {
        return !PocketUpgradeSwitches.isOff(carrier, type);
    }

    /**
     * ★服务端唯一的开关写腿（三判中的两判 + 落档）。
     * <p>
     * <b>判序是判据的一部分</b>：先问"这一型在不在档上"，再（只在要关的时候）跑守卫，最后才写。
     * 反过来就会给一型从未买过的插件写进关闭位 ⇒ 那一行显示"已关闭"，而它对应的是"没有这个升级"，
     * 既是假读数，也是"还能开回来"的错误暗示。
     * <p>
     * <b>关的方向跑守卫、开的方向不跑</b>：守卫问的是"关掉以后装不装得下"，开着不存在缩尺问题。
     * <p>
     * <b>请求的是目标值而不是"翻一下"</b>：同一目标重复到达 ⇒ 走 {@link Outcome#NO_CHANGE} 支，
     * 零写入、零同步包（重复包不会被重放成两次翻转，也满足"同值不刷 NBT"那条既有纪律）。
     * <p>
     * 线程：只在服务器主线程被调（{@code NekoPocketServerHandler} 的入口已按 R71 经
     * {@code ServerTaskScheduler} 投递过）。本方法<b>不</b>做持有者 / 载体身份判定 —— 那两个判据要读
     * {@code Container} 与 {@code EntityPlayer}，留在 handler 一侧（{@code serverGuardOk}）。
     *
     * @param carrier 载体口袋栈（★它的 NBT 根就是写入目标；{@code null} 或无根 ⇒ 判"不在档上"，
     *                <b>不建档</b>，口径同 {@code PocketUpgradeSwitches} 的读路径纪律 R53c）
     * @param wantOff 玩家请求的目标：{@code true} = 关掉
     * @param inv     当前面板会话的数据面（守卫的 long 真值与中栏扫描面），可为 {@code null}
     * @param cursor  玩家游标栈（堆叠守卫的扫描面必须含它，理由见 {@code PocketUpgradeGuards} 类 javadoc）
     */
    public static Outcome commitSwitch(ItemStack carrier, PocketUpgradeType type, boolean wantOff, PocketInventory inv,
        ItemStack cursor) {
        final SwitchState state = switchState(carrier, type);
        if (state == SwitchState.ABSENT) {
            return Outcome.NOT_ON_RECORD;
        }
        final boolean currentlyOff = state == SwitchState.OFF;
        if (currentlyOff == wantOff) {
            // ★目标 == 现值 ⇒ 一个字节都不动（连 setOff 都不必进：零写入、零同步包）。
            return Outcome.NO_CHANGE;
        }
        if (wantOff) {
            final PocketUpgradeGuards.OffVerdict verdict = PocketUpgradeGuards.canTurnOff(type, inv, carrier, cursor);
            if (!verdict.allowed()) {
                return verdict.reason() == PocketUpgradeGuards.Reason.CAPACITY_OVER_OFF_LIMIT ? Outcome.REFUSE_CAPACITY
                    : Outcome.REFUSE_STACK;
            }
        }
        // 守卫过了还要认 setOff 自己的读数：它才是"本次是否真的改变了"的唯一裁判
        // （★不拿上面的 currentlyOff 反推写入结果，两处判断一旦分叉，回执就在说谎）。
        final net.minecraft.nbt.NBTTagCompound root = carrier == null ? null : carrier.getTagCompound();
        if (!PocketUpgradeSwitches.setOff(root, type, wantOff)) {
            return Outcome.NO_CHANGE;
        }
        return wantOff ? Outcome.TURNED_OFF : Outcome.TURNED_ON;
    }

    /**
     * 结论 → 玩家可见回执键（★粘性回执；{@link Outcome#NOT_ON_RECORD} 与两种守卫拒绝各一条键，不许并成
     * "关闭失败"：玩家要能分辨"罐还没腾""堆还没拆""这型根本没装"，并成一条就是让玩家自己试。
     * 口径同 {@code NekoPocketPanel#receiptOfReport} 把分区拒收与元件满分成两条键）。
     */
    public static String receiptKey(Outcome outcome) {
        switch (outcome) {
            case TURNED_OFF:
                return "gtit.pocket.receipt.upgrade.off";
            case TURNED_ON:
                return "gtit.pocket.receipt.upgrade.on";
            case NOT_ON_RECORD:
                return "gtit.pocket.receipt.upgrade.not_on_record";
            case REFUSE_CAPACITY:
                return "gtit.pocket.receipt.upgrade.refuse_capacity";
            case REFUSE_STACK:
                return "gtit.pocket.receipt.upgrade.refuse_stack";
            default:
                return "gtit.pocket.receipt.upgrade.no_change";
        }
    }

    /** 持有者 / 载体身份那一判的回执键（判据在 handler 侧，键与上面六条同族 ⇒ 列在本件做单源）。 */
    public static String identityReceiptKey() {
        return "gtit.pocket.receipt.upgrade.not_owner";
    }

    /** 开关按钮上的文字（三态各一条键；★"未装"不许显示成"已关闭"，那暗示着"还能开回来"）。 */
    public static String switchLabelKey(SwitchState state) {
        switch (state) {
            case ON:
                return "gtit.pocket.config.switch.turn_off";
            case OFF:
                return "gtit.pocket.config.switch.turn_on";
            default:
                return "gtit.pocket.config.switch.none";
        }
    }

    /** 行首"这一型现在开着没有"的读数键（三态，与 {@link #switchLabelKey} 共用同一个 {@link SwitchState}）。 */
    public static String stateKey(SwitchState state) {
        switch (state) {
            case ON:
                return "gtit.pocket.config.state.on";
            case OFF:
                return "gtit.pocket.config.state.off";
            default:
                return "gtit.pocket.config.state.absent";
        }
    }

    /**
     * 升级格 tooltip 的<b>关闭态读数</b>键：只在"这一型已被装上且被关掉"时追加一行
     * （开着与没装都不追加 —— 玩家不需要在两种正常态上读同一句话）。
     * <p>
     * ★这是"格 → 面板"之外唯一的开关可见面：光泽只有一个布尔位（S1 定案），逐型状态的读数就落在
     * 这一行与配置面板上。
     */
    public static String cellOffReadoutKeyOf(SwitchState state) {
        return state == SwitchState.OFF ? "gtit.pocket.upgrade.cell.off" : null;
    }

    /** 本件的全部 lang 键（★字面量清单，给两份 lang 的对账与用例点名用；不参与装配）。 */
    public static List<String> langKeys() {
        final List<String> keys = new ArrayList<>(
            Arrays.asList(
                "gtit.pocket.config.close",
                "gtit.pocket.config.state.on",
                "gtit.pocket.config.state.off",
                "gtit.pocket.config.state.absent",
                "gtit.pocket.config.switch.turn_on",
                "gtit.pocket.config.switch.turn_off",
                "gtit.pocket.config.switch.none",
                "gtit.pocket.config.switch.hint",
                "gtit.pocket.config.mount.magnet",
                "gtit.pocket.config.mount.mage",
                // ★R97 S5：五面各自的读数/说明键（紧凑三型 + 魔法使容量行）。★R96 S2 那条
                // gtit.pocket.config.mount.pending 随「五行常驻一面」一起退场（五型五面后不存在
                // 「有框没内容」的挂载位 ⇒ 占位文案失去消费者，键与文本件一并撤销）。
                // ★R98 S4 DP-7：紧凑三面收到「开关 + 一行读数」，三条<b>静态说明</b>键随各自的文本件
                // 一起撤 —— gtit.pocket.config.stack.note（同尺说明）、gtit.pocket.config.persist.button
                // （常开期间按钮禁用）、gtit.pocket.config.persist.frame（帧带常亮）。★这里撤键必须与
                // 装配侧、两份 lang 同批：留一半就是"键还在但没人读"的第二份僵尸（R96 S5 的先例）。
                READOUT_STACK_KEY,
                READOUT_PERSIST_KEY,
                "gtit.pocket.config.mage.capacity",
                "gtit.pocket.config.mode.crystal",
                "gtit.pocket.config.mode.coin",
                "gtit.pocket.config.mode.transmute",
                MODE_HINT_KEY,
                "gtit.pocket.upgrade.cell.off",
                identityReceiptKey()));
        for (final Outcome outcome : Outcome.values()) {
            keys.add(receiptKey(outcome));
        }
        // ★磁力配置面的键（三态标签 ×3 + 三态不对称说明 ×3 + 目标两档标签 ×2 + 三枚提示 +
        // 计数读数 + 清空按钮两行 + 格件四行 + 格件的四条拒收支）。★逐条点名进这张表 ⇒ 两份 lang 的
        // 对账用例（config_panel_dispatch_is_not_five_identical 的④面）会自动把它们一起量。
        for (final PocketMagnetFilter.Mode mode : PocketMagnetFilter.Mode.values()) {
            keys.add(modeLabelKey(mode));
            keys.add(noteKeyOf(mode));
        }
        for (final PocketMagnetFilter.Target target : PocketMagnetFilter.Target.values()) {
            keys.add(targetLabelKey(target));
        }
        keys.addAll(
            Arrays.asList(
                "gtit.pocket.magnet.mode.hint",
                "gtit.pocket.magnet.target.hint",
                "gtit.pocket.magnet.count",
                "gtit.pocket.magnet.clear",
                "gtit.pocket.magnet.clear.hint",
                "gtit.pocket.magnet.cell.empty",
                "gtit.pocket.magnet.cell.remove",
                "gtit.pocket.magnet.cell.number",
                "gtit.pocket.magnet.gesture",
                NekoMagnetGhostCell.REFUSE_NO_ITEM,
                NekoMagnetGhostCell.REFUSE_WRONG_BUTTON,
                NekoMagnetGhostCell.REFUSE_LOCKED,
                NekoMagnetGhostCell.REFUSE_FULL));
        keys.addAll(Arrays.asList(UPGRADE_NAME_KEYS));
        return keys;
    }

    // ================================================================== 逐型几何（派生自分派表）

    /** 这一型的型名行盒宽（★数据表 {@link #LABEL_BOX_WIDTHS} 的唯一读口；越界 ⇒ 0，调用方停）。 */
    public static int labelWidthOf(PocketUpgradeType type) {
        if (type == null || type.ordinal() >= LABEL_BOX_WIDTHS.length) {
            return 0;
        }
        return LABEL_BOX_WIDTHS[type.ordinal()];
    }

    /** 开关行整行的宽 = 型名盒 + 2px 缝 + 开关（★派生式，不手抄）。 */
    public static int switchRowWidthOf(PocketUpgradeType type) {
        return labelWidthOf(type) + 2 + SWITCH_WIDTH;
    }

    /** 这一段内容块的宽（像素预算）；读数段横贯整面（回 0），挂载段自带硬宽。 */
    static int sectionContentWidth(Section section) {
        switch (section) {
            case MOUNT_MAGNET:
                return MAGNET_CONTROL_WIDTH + COLUMN_GAP + MAGNET_FRAME_WIDTH;
            case MOUNT_MAGE:
                return MAGE_FRAME_WIDTH;
            default:
                // SWITCH 行带宽度按型给（switchRowWidthOf），读数段不自带宽度约束（像素账由用例量）。
                return 0;
        }
    }

    /** 这一段内容块的高（像素预算；{@link Section#SWITCH} 的高已计入 {@link #CONTENT_TOP}）。★公开给用例的逐型闭合账。 */
    public static int sectionContentHeight(Section section) {
        switch (section) {
            case READOUT_CAPACITY:
                // ★一行读数，但给的是两行盒：那句英文最坏串在 202px 的盒里折两行（由用例逐型像素账量到）。
                return CAPACITY_READOUT_HEIGHT;
            case READOUT_STACK:
                // ★R98 S4 DP-7：42（两行 + 缝）→ 18（只剩单格上限那一行；同尺说明连键一起撤）。
                return READOUT_HEIGHT;
            case READOUT_PERSIST:
                // ★R98 S4 DP-7：64（三行 + 两缝）→ 18（只剩常开状态那一行；两条静态说明连键一起撤）。
                return READOUT_HEIGHT;
            case MOUNT_MAGNET:
                return MAGNET_FRAME_HEIGHT;
            case MOUNT_MAGE:
                // ★R98 S4：容量行已在框内 ⇒ 框高就是整段高，不再往框下另加一条行 + 缝。
                return MAGE_FRAME_HEIGHT;
            default:
                return 0;
        }
    }

    /** 这一型面板的宽 = 边距 + max(行带宽, 各内容段宽) + 边距（★派生自分派表，不手抄）。 */
    public static int panelWidthOf(PocketUpgradeType type) {
        int content = switchRowWidthOf(type);
        for (final Section section : sectionsOf(type)) {
            content = Math.max(content, sectionContentWidth(section));
        }
        return MARGIN + content + MARGIN;
    }

    /** 这一型面板的高 = 内容顶 + max(各内容段高) + 底部两件（★派生自分派表，不手抄）。 */
    public static int panelHeightOf(PocketUpgradeType type) {
        int content = 0;
        for (final Section section : sectionsOf(type)) {
            content = Math.max(content, sectionContentHeight(section));
        }
        return CONTENT_TOP + content + BOTTOM_STACK;
    }

    /** 这一型的面板名（调试树与 MUI2 面板标识；前缀单源 {@link #PANEL_NAME}，后缀 = 型 token）。 */
    public static String panelNameOf(PocketUpgradeType type) {
        return PANEL_NAME + '.'
            + (type == null ? "none"
                : type.name()
                    .toLowerCase(Locale.ROOT));
    }

    /** 磁力挂载框在这一型面板里的 x（★右贴边：右缘压着 {@code 面板宽 − 边距}）。 */
    public static int magnetFrameXOf(PocketUpgradeType type) {
        return panelWidthOf(type) - MARGIN - MAGNET_FRAME_WIDTH;
    }

    /** 盘面原点的 x（框内 1px 边）。 */
    public static int magnetGridXOf(PocketUpgradeType type) {
        return magnetFrameXOf(type) + 1;
    }

    /** 盘面原点的 y（框边 + 标题行）。 */
    public static int magnetGridY() {
        return MAGNET_FRAME_Y + ROW_HEIGHT;
    }

    /** 分派表里带 {@link Section#MOUNT_MAGNET} 的那一型（★几何断言用；当前五型表里恰一枚 = MAGNET）。 */
    private static PocketUpgradeType magnetType() {
        for (final PocketUpgradeType type : PocketUpgradeType.values()) {
            if (sectionsOf(type).contains(Section.MOUNT_MAGNET)) {
                return type;
            }
        }
        throw new IllegalStateException("[pocket] 分派表里没有任何一型挂磁力盘（72 格名单盘失去了宿主）");
    }

    // ================================================================== 装配面（客户端控件树）

    /**
     * 构建这一型的次级面板（★只画控件与发码，不写档、不跑守卫）。
     * <p>
     * <b>★R97 S5 参数化</b>：一块面板 = 一型。画什么由 {@link #sectionsOf} 决定（公共的开关行 + 本型
     * 专属的那一段），多一段少一段都过不了 static 块的段族对账。宿主侧为五型各挂一枚
     * {@code IPanelHandler}（{@code NekoPocketPanel#openUpgradeConfig} 按 index 选），同一 handler 的
     * 首建缓存仍生效 ⇒ 同型重开拿缓存面板（正是想要的），异型重开拿的是<b>另一枚 handler 的</b>面板
     * —— P-3 那条"第二型拿到的还是第一型那一套"的库限制在五 handler 形状下结构性不存在。
     * <p>
     * <b>装配期不执行点击谓词</b>：{@code onMousePressed} 在这里只是登记 lambda，服务端那次
     * {@code assemble()} 调用不会因此跑任何开关判定，也不会多出一条写腿。
     */
    public static ModularPanel build(NekoPocketPanel ui, PocketUpgradeType type) {
        if (ui == null || type == null || type.ordinal() >= PocketUpgradeType.values().length) {
            throw new IllegalArgumentException("[pocket] build 需要有效的面板宿主与升级型: ui=" + ui + " type=" + type);
        }
        final ModularPanel panel = ModularPanel
            .defaultPanel(panelNameOf(type), panelWidthOf(type), panelHeightOf(type));
        // ★R99 P1（T3 档）：五面第一 child = 与主面板同源的 C2 装饰底（cloth + 木框 9-slice + 4 包角）。
        // MUI2 按 child 序绘制 ⇒ 底材在最下，控件画在框面上；这也是「MUI2 面板主题默认底
        // GuiTextures.MC_BACKGROUND（浅灰板）」被盖掉的那一层——R98 判不合格的"裸浅灰底"就是缺它。
        panel.child(NekoPocketDecoration.build(panelWidthOf(type), panelHeightOf(type)));
        // ★R98 S4 的 child 序：<b>各段先、底部两件后</b>。MUI2 按 child 序绘制 ⇒ 旧写法（回执 + 关闭钮
        // 抢在内容段之前）会让磁力框（y 26..262）与魔法使框（26..122）画在回执行与关闭钮<b>之上</b>：
        // 当前几何不重叠所以看不出来，但取证 02-gui.md §4-6 点的正是这颗雷 —— 任何一次往下扩段的排版
        // 都会把底部两件盖掉。顺序换过来以后，"贴底的两件永远压得住内容段"是结构性的，不靠行号运气。
        for (final Section section : sectionsOf(type)) {
            switch (section) {
                case SWITCH:
                    panel.child(typeLabel(ui, type));
                    panel.child(switchButton(ui, type));
                    break;
                case READOUT_CAPACITY:
                    panel.child(capacityReadoutBlock(ui, type));
                    break;
                case READOUT_STACK:
                    // ★R98 S4 DP-7：只剩单格上限那一行（"中栏物品格与源质格同用这把尺"那条说明连键一起撤）。
                    panel.child(stackLimitLine(ui, type));
                    break;
                case READOUT_PERSIST:
                    // ★R98 S4 DP-7：只剩常开现状那一行（按钮禁用 / 帧带常亮两条说明连键一起撤）。
                    panel.child(persistStateLine(ui, type));
                    break;
                case MOUNT_MAGNET:
                    mountMagnet(ui, panel, type);
                    break;
                case MOUNT_MAGE:
                    mountMage(ui, panel, type);
                    break;
                default:
                    // 走不到（Section 已穷举）。留这一支的意义：将来追加段族而忘了画法时先在这里炸，
                    // 而不是静默画出一块空白面。
                    throw new IllegalStateException("[pocket] 未知内容段: " + section);
            }
        }
        panel.child(receiptLine(ui, type));
        panel.child(closeButton(ui, type));
        return panel;
    }

    /**
     * 型名 + 现状读数（★动态：型名与状态同一句里，读的是同一个 {@link SwitchState}）。
     * <p>
     * ★可用宽度 = {@link #labelWidthOf}（★逐型给盒：短型的英文最坏串本来就短，五面差异化的一部分；
     * 真串真宽由 {@code config_panel_geometry_within_secondary_caps} 拿两份 lang 逐型量 ——
     * 用「全宽 − 边距 − 开关」那种近似式会在紧凑面上虚高，恒绿的假像素账正是它要防的形状）。
     */
    private static IWidget typeLabel(NekoPocketPanel ui, PocketUpgradeType type) {
        final String nameKey = UPGRADE_NAME_KEYS[type.ordinal()];
        return (IWidget) new TextWidget(IKey.dynamic(() -> {
            final SwitchState state = switchState(ui.carrierStackLive(), type);
            return StatCollector.translateToLocal(nameKey) + "：" + StatCollector.translateToLocal(stateKey(state));
        })).textAlign(Alignment.CenterLeft)
            .scale(PocketGhostRequest.RESIDENT_TEXT_SCALE)
            .color(PocketGhostRequest.readoutTextColor())
            .shadow(Boolean.TRUE)
            .name("pocket_config_label_" + type.ordinal())
            .pos(MARGIN, MARGIN)
            .size(labelWidthOf(type), ROW_HEIGHT);
    }

    /** 开关（★唯一出口是发码，本地零写入；★排在本行右端，与型名同一横带）。 */
    private static IWidget switchButton(NekoPocketPanel ui, PocketUpgradeType type) {
        return new ButtonWidget<>().pos(MARGIN + labelWidthOf(type) + 2, MARGIN)
            .size(SWITCH_WIDTH, SWITCH_HEIGHT)
            .name("pocket_config_switch_" + type.ordinal())
            .background(PocketGuiTextures.BUTTON)
            .hoverBackground(PocketGuiTextures.BUTTON_PRESSED)
            .overlay(
                IKey.dynamic(
                    () -> StatCollector.translateToLocal(switchLabelKey(switchState(ui.carrierStackLive(), type)))))
            .tooltipDynamic(tooltip -> tooltip.addLine(IKey.lang("gtit.pocket.config.switch.hint")))
            .tooltipAutoUpdate(true)
            .playClickSound(true)
            // ★只有左键生效（同 R83 B2(5) 的口径：丢掉 button 形参 = 右键也切一次）；
            // ★★这里一个字节都不写档 —— 客户端私写 off-mask 是门禁 G12 / 门 D 钉死的 FAIL 形状。
            .onMousePressed(
                button -> button == 0 && ui.requestUpgradeSwitch(type, nextOff(ui.carrierStackLive(), type)));
    }

    /** 回执行：最近一次切换成了什么 / 为什么被拒（★走既有粘性回执通道，不新建通道；★两行盒）。 */
    private static IWidget receiptLine(NekoPocketPanel ui, PocketUpgradeType type) {
        return (IWidget) new TextWidget(IKey.dynamic(ui::upgradeConfigReceiptText)).textAlign(Alignment.CenterLeft)
            .scale(PocketGhostRequest.RESIDENT_TEXT_SCALE)
            .color(PocketGhostRequest.hintTextColor())
            .shadow(Boolean.TRUE)
            .name("pocket_config_receipt")
            .pos(MARGIN, panelHeightOf(type) - MARGIN - RECEIPT_HEIGHT)
            .size(panelWidthOf(type) - 2 * MARGIN - CLOSE_WIDTH - 2, RECEIPT_HEIGHT);
    }

    /**
     * 关闭本面的按钮（★只关面板，不动任何数据：本面无"待提交"状态，每次点开关都是即时单发）。
     * ★R99 P2 并带：纵向落在回执行带内居中（带高 22 − 钮高 18 = 上下各 2px），横向仍是右端——
     * 旧形状"独占一行、右下角离一切最远"从几何上消失。
     */
    private static IWidget closeButton(NekoPocketPanel ui, PocketUpgradeType type) {
        return new ButtonWidget<>()
            .pos(
                panelWidthOf(type) - MARGIN - CLOSE_WIDTH,
                panelHeightOf(type) - MARGIN - RECEIPT_HEIGHT + (RECEIPT_HEIGHT - CLOSE_HEIGHT) / 2)
            .size(CLOSE_WIDTH, CLOSE_HEIGHT)
            .name("pocket_config_close")
            .background(PocketGuiTextures.BUTTON)
            .hoverBackground(PocketGuiTextures.BUTTON_PRESSED)
            .overlay(IKey.lang("gtit.pocket.config.close"))
            .playClickSound(true)
            .onMousePressed(button -> button == 0 && ui.closeUpgradeConfig());
    }

    // ================================================================== ★R98 S4 紧凑三面的读数段（各剩一行）

    /**
     * 容量读数块（★两行盒：复用主面板 {@code capacityReadoutText} 的同一句话与同一个格式源，
     * ★键单源 {@link #READOUT_CAPACITY_KEY}）。
     * <p>
     * ★R98 S4 把对齐从 {@code TopLeft} 换成 {@code CenterLeft}：本面其余读数件（型名、回执）全是
     * {@code CenterLeft}，同一面里两种对齐是取证 {@code 02-gui.md} §4-1 点名那一处。两行盒居中后
     * 上下各余 1px（20 ≤ 22），不顶穿。
     */
    private static IWidget capacityReadoutBlock(NekoPocketPanel ui, PocketUpgradeType type) {
        return (IWidget) new TextWidget(IKey.dynamic(ui::capacityReadoutText)).textAlign(Alignment.CenterLeft)
            .scale(PocketGhostRequest.RESIDENT_TEXT_SCALE)
            .color(PocketGhostRequest.readoutTextColor())
            .shadow(Boolean.TRUE)
            .name("pocket_config_capacity_readout")
            .pos(MARGIN, CONTENT_TOP)
            .size(panelWidthOf(type) - 2 * MARGIN, CAPACITY_READOUT_HEIGHT);
    }

    /** 堆叠面那一行：单格上限的<b>现档</b>读数（64 ↔ 1024，随开关翻转；读 R97 R6 的同步镜像）。 */
    private static IWidget stackLimitLine(NekoPocketPanel ui, PocketUpgradeType type) {
        return (IWidget) new TextWidget(
            IKey.dynamic(
                () -> String.format(
                    StatCollector.translateToLocal(READOUT_STACK_KEY),
                    ui.storageStackUpgraded() ? Integer.valueOf(PocketConstants.STORAGE_SLOT_LIMIT_UPGRADED)
                        : Integer.valueOf(PocketConstants.STORAGE_SLOT_LIMIT_BASE)))).textAlign(Alignment.CenterLeft)
                            .scale(PocketGhostRequest.RESIDENT_TEXT_SCALE)
                            .color(PocketGhostRequest.readoutTextColor())
                            .shadow(Boolean.TRUE)
                            .name("pocket_config_stack_limit")
                            .pos(MARGIN, CONTENT_TOP)
                            .size(panelWidthOf(type) - 2 * MARGIN, READOUT_HEIGHT);
    }

    /**
     * 通道持续化面那一行：常开<b>现状</b>（常开支复用主面板 {@code channel.always_on} 那条键；未常开给
     * ★本型独有的 {@link #READOUT_PERSIST_KEY}，★不给假"常开中"）。
     * <p>
     * ★R98 S4 DP-7：这一面原有三行，其中两行是<b>静态说明</b>（"常开生效期间通道按钮被禁用"、"口袋图标
     * 保持工作中常亮"），取证 {@code 02-gui.md} §5.3 量到那两行占满面高 140 的 46% ⇒ 用户明令撤。
     * 那两条事实的玩家可见面从此只剩 {@code gtit.pocket.config.switch.hint} 那条 tooltip 与主面板上
     * 本来就有的通道按钮 / 图标状态读面（★这是有意的信息面收缩，用户 2026-09-28 明确接受）。
     */
    private static IWidget persistStateLine(NekoPocketPanel ui, PocketUpgradeType type) {
        return (IWidget) new TextWidget(
            IKey.dynamic(
                () -> StatCollector.translateToLocal(
                    ui.channelPersistActive() ? "gtit.pocket.channel.always_on" : READOUT_PERSIST_KEY)))
                        .textAlign(Alignment.CenterLeft)
                        .scale(PocketGhostRequest.RESIDENT_TEXT_SCALE)
                        .color(PocketGhostRequest.readoutTextColor())
                        .shadow(Boolean.TRUE)
                        .name("pocket_config_persist_state")
                        .pos(MARGIN, CONTENT_TOP)
                        .size(panelWidthOf(type) - 2 * MARGIN, READOUT_HEIGHT);
    }

    // ================================================================== ★R96 S7b 磁力面（五面里的整面排版）

    /**
     * 磁力面的整面装配（★R99 P3 M-1 重排）：右列 = 12×6 名单盘挂载框（顶到内容段顶，仍是这一面唯一的
     * 高度驱动者），左列 = 三态 / 目标 / 清空 / 计数 / 不对称说明<b>竖排一列</b>（五段各占一行，
     * R98 期"两列并排"的 230 宽控制块作废——它留下 234×172 的成片空洞，是实机判不合格的排布缺陷）。
     * ★「72 格盘常量只属于磁力面」的结构正身：本方法只经
     * {@link Section#MOUNT_MAGNET} 那一分派支可达，五型里没有第二型的面板画得出格盘。
     */
    private static void mountMagnet(NekoPocketPanel ui, ModularPanel panel, PocketUpgradeType type) {
        final int x = magnetFrameXOf(type);
        final int y = MAGNET_FRAME_Y;
        final ParentWidget<?> box = new ParentWidget<>().background(PocketGuiTextures.PANEL)
            .name("pocket_config_mount_magnet")
            .pos(x, y)
            .size(MAGNET_FRAME_WIDTH, MAGNET_FRAME_HEIGHT);
        box.child(
            (IWidget) new TextWidget(IKey.lang("gtit.pocket.config.mount.magnet")).textAlign(Alignment.TopLeft)
                .scale(PocketGhostRequest.RESIDENT_TEXT_SCALE)
                .color(PocketGhostRequest.readoutTextColor())
                .shadow(Boolean.TRUE)
                .name("pocket_config_mount_title_magnet")
                .pos(1, 1)
                .size(MAGNET_FRAME_WIDTH - 2, ROW_HEIGHT));
        box.child(magnetGrid(ui, x, y));
        panel.child(box);
        // ★控制块（三态循环 / 吸取目标两档 / 清空 / 计数 / 不对称读数）排在<b>左列</b>。
        // ★常驻树形：这五枚件不随名单条数增删（R32/R41b），名单变化只进各件的 IKey.dynamic / 内容层。
        // ★整块只发码或发键，一个字节都不写本地 NBT。
        panel.child(magnetModeButton(ui));
        panel.child(magnetTargetButton(ui));
        panel.child(magnetClearButton(ui));
        panel.child(magnetCountLine(ui));
        panel.child(magnetNoteLine(ui));
    }

    /**
     * 72 格名单盘（★12 行 × 6 列 = 108×216，与源质格<b>同形但★不共用那两个常量</b>）。
     * <p>
     * <b>★每格是 {@link NekoMagnetGhostCell}（phantom 侧的 {@code ButtonWidget}），★不是真槽</b>：
     * 本方法一次 {@code new ItemSlot} 都不做、也不走 {@code SlotGroupWidget} ⇒ 守恒 225 那条
     * （{@code PocketSlots#assertTotalRealSlots} 与 {@code slot_math_225_*} 两条既有钉）
     * <b>逐字不会红</b>。★也刻意不复用 {@code NekoFilterSlot}——它是真实槽的双态件，套进来就会撑开那条账。
     * <p>
     * <b>格序 = 服务端 {@code LinkedHashSet} 的插入序</b>（{@code PocketMagnetFilter#entryKeys()} 那份
     * 顺序，随 {@code SYNC_MAGNET} 的串落到客户端镜像）。★客户端不按字符串排序、不重排、不裁剪，
     * 那是 R32"两端各算各的"的落点。
     * <p>
     * <b>★树形恒定</b>：72 个格件与格序不随名单条数增删（R41b/R32）；数据变化只由
     * {@code NekoPocketPanel#applyMagnetCells()} 原位换内容层。
     *
     * @param frameX 挂载框在面板里的 x（★盘面的面板坐标 = 框 x + 1 边距，见 {@link #magnetGridXOf}）
     * @param frameY 挂载框在面板里的 y
     */
    private static IWidget magnetGrid(NekoPocketPanel ui, int frameX, int frameY) {
        // 盘面作为一个 ParentWidget 挂在框内（子格坐标 = 相对盘面原点，18px 栅格）。盘面原点的<b>面板</b>坐标
        // 单源在 {@link #magnetGridXOf} / {@link #magnetGridY()}，框内相对坐标 = 面板坐标 − 框原点
        // ⇒ 这里不抄第二份「1px 边 + 标题行」。
        final ParentWidget<?> grid = new ParentWidget<>()
            .pos(magnetGridXOf(magnetType()) - frameX, magnetGridY() - frameY)
            .size(MAGNET_GRID_WIDTH, MAGNET_GRID_HEIGHT)
            .name("pocket_magnet_grid");
        for (int index = 0; index < MAGNET_COLUMNS * MAGNET_ROWS; index++) {
            grid.child(magnetCell(ui, index));
        }
        return grid;
    }

    /**
     * 单个名单格：凹槽底（★恒画）+ 物品图标内容层（★本格有条目才画）+ tooltip（★每帧重建，
     * 含四条拒收支的说法）+ 三条手势（左键持物录入 / 右键摘本格 / NEI 拖入录入）。
     * <p>
     * ★格件登记进面板的 {@code magnetCells}（{@code NekoPocketPanel#trackMagnetCell}），于是服务端推来的
     * 名单能<b>原位</b>刷这一格，而装配期捕获的键不会留在屏上（R78③ 那一族"晚到的数据"同一口径）。
     */
    private static IWidget magnetCell(NekoPocketPanel ui, int index) {
        final int column = index % MAGNET_COLUMNS;
        final int row = index / MAGNET_COLUMNS;
        final NekoMagnetGhostCell cell = new NekoMagnetGhostCell().bindCell(ui, index)
            .pos(column * CELL, row * CELL)
            .size(CELL, CELL)
            .name("pocket_magnet_cell_" + index)
            .background(PocketGuiTextures.SLOT)
            .playClickSound(true);
        ui.trackMagnetCell(index, cell);
        cell.tooltipDynamic(tooltip -> {
            final int synced = ui.magnetEntryCount();
            final String key = cell.entryKey();
            final boolean empty = key == null || key.isEmpty();
            if (empty) {
                tooltip.addLine(IKey.lang("gtit.pocket.magnet.cell.empty"));
                // ★拒收支②（按键读法）：空格上右键确实无事发生 ⇒ 把"只有左键记、右键是摘"念出来，
                // ★不许让玩家自己去试（{@code NekoMagnetGhostCell#onMousePressed} 的右枝对空格就是不发请求的）
                tooltip.addLine(IKey.lang(NekoMagnetGhostCell.REFUSE_WRONG_BUTTON));
            } else {
                tooltip.addLine(IKey.str(entryTitleOf(key)));
                tooltip.addLine(IKey.lang("gtit.pocket.magnet.cell.remove"));
            }
            tooltip.addLine(IKey.lang("gtit.pocket.magnet.cell.number", index + 1));
            // ★四条拒收支的说法都在 tooltip 里（ghost 那族"静默吞点击"的坑就在这：不写出来就等于没说法）
            tooltip.addLine(IKey.lang("gtit.pocket.magnet.gesture"));
            if (!ui.magnetEditable()) {
                tooltip.addLine(IKey.lang(NekoMagnetGhostCell.REFUSE_LOCKED));
            } else if (synced >= PocketConstants.MAGNET_FILTER_SLOTS) {
                tooltip.addLine(IKey.lang(NekoMagnetGhostCell.REFUSE_FULL));
            } else if (empty && isEmptyCursor(ui.magnetCursorStack())) {
                // ★拒收支①：手上没东西 ⇒ 左键点这一格记不了任何件（游标读数是客户端只读口，★不写）
                tooltip.addLine(IKey.lang(NekoMagnetGhostCell.REFUSE_NO_ITEM));
            }
            // ★三态不对称的第二处可见面（第一处 = {@link #magnetNoteLine} 那行常驻读数）：
            // "无限制"档下名单仍在场、只是不生效 —— 这条不许只靠实现隐含（验收 4 点名要两处都可见）
            tooltip.addLine(IKey.dynamic(() -> StatCollector.translateToLocal(noteKeyOf(ui.magnetMode()))));
        });
        // ★整块（含格盘与三个按钮）在"这一型没固化"时灰显（R31 同一条：灰显不隐藏，画面形状不双分支）
        cell.setEnabledIf(widget -> ui.magnetEditable());
        return cell;
    }

    /**
     * 游标是否"没有可记的东西"（★判据不抄第二份：身份键那一段直接回读 {@code NekoMagnetGhostCell#applyDrop}
     * 用的同一条 {@link NekoMagnetGhostCell#identityKeyOf}；件数那一段与 applyDrop 的
     * {@code carried.stackSize <= 0} 同一读法 ⇒ tooltip 说的与格件做的是<b>同一句话</b>）。
     * ★只读判定，不碰游标本身。
     */
    public static boolean isEmptyCursor(final net.minecraft.item.ItemStack carried) {
        return carried == null || carried.stackSize <= 0
            || NekoMagnetGhostCell.identityKeyOf(carried)
                .isEmpty();
    }

    /** ★三态循环按钮（{@code NONE → WHITELIST → BLACKLIST → NONE}，★只有左键，★客户端零写入）。 */
    private static IWidget magnetModeButton(NekoPocketPanel ui) {
        return new ButtonWidget<>().pos(MAGNET_CONTROL_X, MAGNET_CONTROL_Y)
            .size(MAGNET_BUTTON_WIDTH, MAGNET_BUTTON_HEIGHT)
            .name("pocket_magnet_mode_button")
            .background(PocketGuiTextures.BUTTON)
            .hoverBackground(PocketGuiTextures.BUTTON_PRESSED)
            .overlay(IKey.dynamic(() -> StatCollector.translateToLocal(modeLabelKey(ui.magnetMode()))))
            .tooltipDynamic(tooltip -> {
                tooltip.addLine(IKey.lang("gtit.pocket.magnet.mode.hint"));
                // ★同一条不对称说明（验收 4 的"两处都可见"里的 tooltip 那一处）
                tooltip.addLine(IKey.dynamic(() -> StatCollector.translateToLocal(noteKeyOf(ui.magnetMode()))));
            })
            .tooltipAutoUpdate(true)
            .playClickSound(true)
            .onMousePressed(button -> button == 0 && ui.requestMagnetModeCycle());
    }

    /** ★吸取目标两档按钮（{@code POCKET → PLAYER}；★目标执法腿的归属逐字写在 {@code Target} 的注释里）。 */
    private static IWidget magnetTargetButton(NekoPocketPanel ui) {
        return new ButtonWidget<>().pos(MAGNET_CONTROL_X, MAGNET_CONTROL_Y + MAGNET_BUTTON_HEIGHT + MAGNET_ROW_GAP)
            .size(MAGNET_BUTTON_WIDTH, MAGNET_BUTTON_HEIGHT)
            .name("pocket_magnet_target_button")
            .background(PocketGuiTextures.BUTTON)
            .hoverBackground(PocketGuiTextures.BUTTON_PRESSED)
            .overlay(IKey.dynamic(() -> StatCollector.translateToLocal(targetLabelKey(ui.magnetTarget()))))
            .tooltipDynamic(tooltip -> tooltip.addLine(IKey.lang("gtit.pocket.magnet.target.hint")))
            .tooltipAutoUpdate(true)
            .playClickSound(true)
            .onMousePressed(button -> button == 0 && ui.requestMagnetTargetCycle());
    }

    /** ★清空名单按钮（★只抹条目、不动三态；与"切到无限制"是两件事，见 {@code PocketMagnetFilter} 三态读法）。 */
    private static IWidget magnetClearButton(NekoPocketPanel ui) {
        return new ButtonWidget<>()
            .pos(MAGNET_CONTROL_X, MAGNET_CONTROL_Y + 2 * (MAGNET_BUTTON_HEIGHT + MAGNET_ROW_GAP))
            .size(MAGNET_BUTTON_WIDTH, MAGNET_BUTTON_HEIGHT)
            .name("pocket_magnet_clear_button")
            .background(PocketGuiTextures.BUTTON)
            .hoverBackground(PocketGuiTextures.BUTTON_PRESSED)
            .overlay(IKey.lang("gtit.pocket.magnet.clear"))
            .tooltipDynamic(tooltip -> tooltip.addLine(IKey.lang("gtit.pocket.magnet.clear.hint")))
            .tooltipAutoUpdate(true)
            .playClickSound(true)
            .onMousePressed(button -> button == 0 && ui.requestMagnetClear());
    }

    /** 名单计数读数（★条数是服务端算好带下来的权威数，不是客户端"非空格"的计数）。 */
    private static IWidget magnetCountLine(NekoPocketPanel ui) {
        return (IWidget) new TextWidget(
            IKey.dynamic(
                () -> String.format(
                    StatCollector.translateToLocal("gtit.pocket.magnet.count"),
                    ui.magnetEntryCount(),
                    PocketConstants.MAGNET_FILTER_SLOTS))).textAlign(Alignment.CenterLeft)
                        .scale(PocketGhostRequest.RESIDENT_TEXT_SCALE)
                        .color(PocketGhostRequest.readoutTextColor())
                        .shadow(Boolean.TRUE)
                        .name("pocket_magnet_count")
                        .pos(MAGNET_CONTROL_X, MAGNET_CONTROL_Y + 3 * (MAGNET_BUTTON_HEIGHT + MAGNET_ROW_GAP))
                        .size(MAGNET_COUNT_WIDTH, MAGNET_BUTTON_HEIGHT);
    }

    /**
     * ★三态不对称的<b>常驻读数行</b>（验收 4 点名要"UI 读数与 tooltip 两处都可见"，这里就是读数那一处；
     * 另一处 = {@link #magnetModeButton} / {@link #magnetCell} 的 tooltip）。
     * <p>
     * ★不许只靠实现隐含：{@code NONE} 档的这句话念的就是"名单还在这 n 条，只是当前一律放行"，
     * 玩家切到无限制时看到的是一个<b>还在的清单 + 一句不生效</b>，而不是"清单没了"。
     */
    private static IWidget magnetNoteLine(NekoPocketPanel ui) {
        return (IWidget) new TextWidget(IKey.dynamic(() -> StatCollector.translateToLocal(noteKeyOf(ui.magnetMode()))))
            .textAlign(Alignment.TopLeft)
            .scale(PocketGhostRequest.RESIDENT_TEXT_SCALE)
            .color(PocketGhostRequest.hintTextColor())
            .shadow(Boolean.TRUE)
            .name("pocket_magnet_note")
            .pos(MAGNET_CONTROL_X, magnetNoteY())
            .size(MAGNET_CONTROL_WIDTH - 2, MAGNET_NOTE_HEIGHT);
    }

    /** 读数行的 y（★R99 P3 M-1 单列化：说明块在四段控件之下的第 5 段；单源，纵向闭合由 static 块与用例一起钉）。 */
    public static int magnetNoteY() {
        return MAGNET_CONTROL_Y + 4 * (MAGNET_BUTTON_HEIGHT + MAGNET_ROW_GAP);
    }

    /** 三态 → 按钮文字键（★单源：装配侧与用例读的是同一张表，★不是 switch 里各写一遍）。 */
    public static String modeLabelKey(PocketMagnetFilter.Mode mode) {
        if (mode == PocketMagnetFilter.Mode.WHITELIST) {
            return "gtit.pocket.magnet.mode.white";
        }
        return mode == PocketMagnetFilter.Mode.BLACKLIST ? "gtit.pocket.magnet.mode.black"
            : "gtit.pocket.magnet.mode.none";
    }

    /** 吸取目标 → 按钮文字键。 */
    public static String targetLabelKey(PocketMagnetFilter.Target target) {
        return target == PocketMagnetFilter.Target.PLAYER ? "gtit.pocket.magnet.target.player"
            : "gtit.pocket.magnet.target.pocket";
    }

    /**
     * 三态 → <b>不对称说明</b>键（★三态各一条，因为三句话各不相同：NONE 是"名单在但不生效"、
     * WHITELIST 是"只吸这些"、BLACKLIST 是"这些一律不吸"。并成一句就是假读数，同 S2 那六条回执的理由）。
     */
    public static String noteKeyOf(PocketMagnetFilter.Mode mode) {
        if (mode == PocketMagnetFilter.Mode.WHITELIST) {
            return "gtit.pocket.magnet.note.white";
        }
        return mode == PocketMagnetFilter.Mode.BLACKLIST ? "gtit.pocket.magnet.note.black"
            : "gtit.pocket.magnet.note.none";
    }

    /**
     * 身份键 → 玩家可读的物品名（★读不到物品时念原始键，★不静默变成"这一格什么都没有"）。
     * <p>
     * ★本方法是<b>绘制侧</b>的换算（要真注册表）；判定侧的键换算只有一条
     * {@code PocketMagnetFilter.itemKey/parseEntry}，这里不重写第三份式子。
     */
    public static String entryTitleOf(String key) {
        final net.minecraft.item.Item item = NekoMagnetGhostCell.itemOf(key);
        if (item == null) {
            return key;
        }
        final net.minecraft.item.ItemStack sample = new net.minecraft.item.ItemStack(
            item,
            1,
            NekoMagnetGhostCell.metaOf(key));
        return sample.getDisplayName();
    }

    // ================================================================== ★R96 S9b 魔法使面（挂载框 + 三行模式 + 容量行）

    /**
     * 魔法使面的整面装配：一枚挂载框，框内 = 标题 + 三行模式控件 + 元素容量读数行。
     * <p>
     * ★R98 S4 的排版改判：容量行原本悬在<b>框外</b>（取证 {@code 02-gui.md} §4-5 —— "框 = 这里面是一组"
     * 这条语义在魔法使面只兑现了一半，磁力面框住的是整盘）。现在它和模式行同框，两面的框语义一致；
     * 框宽因此从 144 抬到 {@link #MAGE_FRAME_WIDTH}，而 144 → 170 仍在行带宽 188 之内 ⇒ 面板宽不变。
     * <p>
     * <b>树形恒定</b>（R32 纪律）：行数 = {@link #modeRowCount()}（派生自
     * {@link PocketConstants#MAGE_MODE_BITS}），★不随"哪条模式开着"变化 ⇒ 同型重开拿到的缓存面板
     * 不会因为状态改而拿到一棵旧树。
     * <p>
     * <b>零同步值、零本地写</b>：与开关行同一条通道（读载体栈 NBT 的 vanilla 镜像、写只发码）。
     * <p>
     * ★形参 {@code type} 是两支挂载装配的同形签名（磁力那一支真的按型算框的 x）；本支自 R98 起几何全部
     * 走 {@link #MAGE_FRAME_X}/{@link #MAGE_FRAME_WIDTH} 这套框常数，不再从 {@code type} 取宽 —— 签名由
     * 用例 {@code mage_config_panel_mode_codec_and_commit_leg} 锚住，★不改。
     */
    private static void mountMage(NekoPocketPanel ui, ModularPanel panel, PocketUpgradeType type) {
        final ParentWidget<?> box = new ParentWidget<>().background(PocketGuiTextures.PANEL)
            .name("pocket_config_mount_mage")
            .pos(MAGE_FRAME_X, MAGE_FRAME_Y)
            .size(MAGE_FRAME_WIDTH, MAGE_FRAME_HEIGHT);
        box.child(
            (IWidget) new TextWidget(IKey.lang("gtit.pocket.config.mount.mage")).textAlign(Alignment.TopLeft)
                .scale(PocketGhostRequest.RESIDENT_TEXT_SCALE)
                .color(PocketGhostRequest.readoutTextColor())
                .shadow(Boolean.TRUE)
                .name("pocket_config_mount_title_mage")
                .pos(1, 1)
                .size(MAGE_FRAME_WIDTH - 2, ROW_HEIGHT));
        panel.child(box);
        // ★模式控件是<b>面板级</b>的孩子（坐标走 {@link #mageContentX()} / {@link #mageContentY()} 的单源式），
        // 挂在框之<b>后</b>（MUI2 的 child 序 = 绘制序 ⇒ 控件画在框面上）——与磁力框的盘面是框内孩子
        // 相反，两支的挂载点不同不是同一条纪律的两个写法。
        mageModeRows(ui, panel);
        // ★R98 S4：元素容量行也进框（它是"这一组"的一部分，不是框外的一条游离读数 ⇒ 框语义与磁力框对齐）。
        panel.child(mageCapacityLine());
    }

    /**
     * 魔法使挂载框里的<b>三行模式控件</b>（第四枚控件是面板顶上那一行的开关，不在这里重复画）。
     * <p>
     * <b>标签 = 模式名 + 现状同一句</b>（{@link #stateKey} 复用的就是开关行那两条既有键，
     * ★模式与开关在玩家侧读起来是同一件事："这一条动还是不动"）。按钮上的字复用
     * {@link #switchLabelKey} 那两条 —— 只是模式没有"未固化"这一态（那一判在 {@link #commitMode} 里
     * 拒写并回一条既有回执，不在按钮上骗人）。
     * <p>
     * ★每行按钮仍各挂同一条 {@link #MODE_HINT_KEY} tooltip（R98 计划提过"合并成一条框级 tooltip"，
     * <b>本片没做</b>，理由记在这儿而不是留给下一个人重新发现）：MUI2 的 hover 链在
     * {@code ModularGuiContext#getHoveredWidgets} 里遇到第一枚 {@code canHoverThrough()==false} 的件就
     * {@code break}，而 {@code ButtonWidget} 走的是那个默认值 ⇒ 框级 tooltip 在<b>正好压在按钮上</b>
     * 的那块区域收不到 hover，等于把唯一的说明面挪到玩家不点的地方。悬停文本也不是用户那句「常驻啰嗦」
     * 的射程（用户原话讲的是面上摆着的东西），⇒ 登记为待办，动它要先在实机证一次可达性。
     */
    private static void mageModeRows(NekoPocketPanel ui, ModularPanel panel) {
        final int rows = modeRowCount();
        for (int index = 0; index < rows; index++) {
            final int row = index;
            final String labelKey = modeLabelKey(row);
            if (labelKey == null) {
                throw new IllegalStateException("[pocket] 模式行数与标签键表不齐: 第 " + row + " 行没有键（两份清单必须同序）");
            }
            panel.child(
                (IWidget) new TextWidget(
                    IKey.dynamic(
                        () -> StatCollector.translateToLocal(labelKey) + "："
                            + StatCollector.translateToLocal(
                                stateKey(modeState(ui.carrierStackLive(), row) ? SwitchState.ON : SwitchState.OFF))))
                                    .textAlign(Alignment.TopLeft)
                                    .scale(PocketGhostRequest.RESIDENT_TEXT_SCALE)
                                    .color(PocketGhostRequest.readoutTextColor())
                                    .shadow(Boolean.TRUE)
                                    .name("pocket_config_mode_label_" + row)
                                    .pos(mageContentX(), mageContentY() + modeRowY(row))
                                    .size(MODE_LABEL_WIDTH, MODE_ROW_HEIGHT));
            panel.child(
                new ButtonWidget<>()
                    .pos(mageContentX() + MAGE_FRAME_WIDTH - 2 - MODE_BUTTON_WIDTH, mageContentY() + modeRowY(row))
                    .size(MODE_BUTTON_WIDTH, MODE_ROW_HEIGHT)
                    .name("pocket_config_mode_switch_" + row)
                    .background(PocketGuiTextures.BUTTON)
                    .hoverBackground(PocketGuiTextures.BUTTON_PRESSED)
                    .overlay(
                        IKey.dynamic(
                            () -> StatCollector.translateToLocal(
                                switchLabelKey(
                                    modeState(ui.carrierStackLive(), row) ? SwitchState.ON : SwitchState.OFF))))
                    .tooltipDynamic(tooltip -> tooltip.addLine(IKey.lang(MODE_HINT_KEY)))
                    .tooltipAutoUpdate(true)
                    .playClickSound(true)
                    // ★同样只有左键生效，且★这里一个字节都不写档（写腿在服务端 commitMode）
                    .onMousePressed(
                        button -> button == 0 && ui.requestUpgradeMode(row, nextModeOn(ui.carrierStackLive(), row))));
        }
    }

    /**
     * 魔法使面的元素容量读数行（P-7 的「各 500」口径：常量单源 {@code PocketConstants}，lang 不写数字）。
     * ★R98 S4 起它住在挂载框<b>内</b>：纵向走 {@link #mageCapacityY()}、横向走 {@link #mageCapacityWidth()}，
     * 两个数都只由框的常数派生 ⇒ 本件不再吃型参（旧写法读的是"横贯整面"的 {@code panelWidthOf}）。
     */
    private static IWidget mageCapacityLine() {
        return (IWidget) new TextWidget(
            IKey.dynamic(
                () -> String.format(
                    StatCollector.translateToLocal("gtit.pocket.config.mage.capacity"),
                    PocketConstants.ELEMENT_CAP_PER_TAG,
                    PocketConstants.ELEMENT_TOTAL_CAP))).textAlign(Alignment.CenterLeft)
                        .scale(PocketGhostRequest.RESIDENT_TEXT_SCALE)
                        .color(PocketGhostRequest.readoutTextColor())
                        .shadow(Boolean.TRUE)
                        .name("pocket_config_mage_capacity")
                        .pos(mageContentX(), mageCapacityY())
                        .size(mageCapacityWidth(), READOUT_HEIGHT);
    }

    /** 挂载框内容区的 x（★框左沿 + 1px，与标题用的那个 1 同一个数）。 */
    public static int mageContentX() {
        return MAGE_FRAME_X + 1;
    }

    /** 挂载框内容区的 y（★框顶 + 标题高 ⇒ 内容段与框之间不留第二份偏移表）。 */
    public static int mageContentY() {
        return MAGE_FRAME_Y + MOUNT_TITLE_HEIGHT;
    }

    /** 第 {@code row} 行模式控件的 y（★挂载框<b>内</b>坐标系，标题之下第一行；单源，调用点不写第二次）。 */
    public static int modeRowY(int row) {
        return row * (MODE_ROW_HEIGHT + MODE_ROW_GAP);
    }

    /**
     * 元素容量行的 y（★R98 S4 起 = 标题 + 模式行栈 + 一条缝，落在挂载框<b>内</b>；旧式子是"框底 + 缝"
     * ⇒ 那一行永远悬在框外）。单源，调用点与 static 对账读的是同一个数。
     */
    public static int mageCapacityY() {
        return MAGE_FRAME_Y + MOUNT_TITLE_HEIGHT + MAGE_MODE_STACK_HEIGHT + READOUT_GAP;
    }

    /** 元素容量行的盒宽（★框内可用宽 = 框宽 − 两道 1px 框边；单源给装配侧与用例的逐型像素账）。 */
    public static int mageCapacityWidth() {
        return MAGE_FRAME_WIDTH - 2;
    }

    /**
     * 型名键表（★整键字面量表，与 {@code NekoPocketBottomBand#UPGRADE_ITEM_NAME_KEYS} 同一组键的第二个
     * 消费面；下标 = ordinal = 位图位 = 槽号，三个空间同一个数）。
     */
    private static final String[] UPGRADE_NAME_KEYS = { "item.neko_pocket_upgrade_capacity.name",
        "item.neko_pocket_upgrade_stack.name", "item.neko_pocket_upgrade_magnet.name",
        "item.neko_pocket_upgrade_channel_persist.name", "item.neko_pocket_upgrade_mage.name" };
}

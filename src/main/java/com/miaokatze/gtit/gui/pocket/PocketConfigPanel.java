package com.miaokatze.gtit.gui.pocket;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;

import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.value.StringValue;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;
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
 * 磁力 = 开关 + 72 格名单盘（★R101.3 起显示朝向横盘 12 列 × 6 行）+ 盘上方按钮行 + 盘下计数行
 * （{@link Section#MOUNT_MAGNET}）；魔法使 = 开关 + 三列模式控件（{@link Section#MOUNT_MAGE}）。
 * ★R101.3 去挂载框：两支挂载段的控件<b>直接挂面板</b>——框底、框标题与魔法使容量行随重排整批退场
 * （用户判「内框丑」，且框住的东西一面只有一组时框不承载信息）。
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
 * 容量 {@code 266×85}、堆叠 {@code 266×81}、通道持续化 {@code 318×102}（★R100 片 C 后含频率段）、
 * 磁力 {@code 258×213}、魔法使 {@code 248×104}（★派生自分派表，不手抄；★R101.3 横盘重排现值：
 * 磁力格盘显示朝向对调成 12 列 × 6 行 = 216×108、三枚控制钮横排移到盘上方、计数行贴盘下——
 * 钮行 3×78 + 2×4 = 242 = 本型行带宽 ⇒ 面宽缩到 258，旧 266 是旧左列 128px 控制列撑出来的）；
 * 魔法使撤挂载框 / 标题 / 容量行 ⇒ R100 期的 266×299 / 248×145 作废；更早的 R99 期
 * 214×80 / 214×76 / 250×76 / 236×294 / 200×154 与 R101 期 248×145 同批作废）。
 * 逐型断言：≤ {@link #MAX_WIDTH}×{@link #MAX_HEIGHT}
 * （380×340）且<b>严格小于</b>主面板 {@code 398×360}（盖满主面板的次级面板读起来就是一块新屏，且非主面板
 * 恒可拖 —— {@code DraggablePanelWrapper} 按可视面余量做除算，贴边即除零/负数）；几何闭合；行带可用宽
 * 扛得住<b>本型</b>型名 + 三态的英文最坏串（逐型的像素账由用例拿两份 lang 真量，本件只钉
 * {@link #WORST_LABEL_WIDTH}=224 那条「最宽一型不许被整体缩水」的下界）。
 * <p>
 * <h2>★R99 P1：风格对齐 = 装饰层真的上五面（T3 档，对 R98 S4 那条"不上装饰"的改判）</h2>
 * R98 写在这里的「{@code NekoPocketDecoration} 是 package-private 且尺寸写死，复用即画成巨底」
 * **两条理由一真一假**：尺寸写死是真的（已由 {@code appendTo(panel,w,h)} 参数化解决），package-private
 * **不是障碍**——两个类同在 {@code gui.pocket} 包。用户 2026-09-28 实机判"裸浅灰底不合格"后拍板 T3：
 * 五面最前一组直接 child = {@code NekoPocketDecoration.appendTo(panel,w,h)}（cloth + PANEL 9-slice +
 * 4 包角，★R101.1 起摊平为直接 child、不再包装饰根子树——两波绘制盖按钮底的根因，见该方法 javadoc；
 * 不画铆钉与绳缝——次级面板没有可压的列缝，绳缝坐标是主面板底部带专属），零参 {@code build()}
 * 与主面板调用点一字不动。★可见边框只有约 7px（10px 边距里有 3px 平色），★R100 起内容从
 * {@code MARGIN=8} 起排（旧值 6 压框线，用户判「过于贴边」的那条正身）。
 * <p>
 * <h2>★R98 S4 度量统一（历史档：字号与配色本来已同源，四处常数沿用）</h2>
 * 落地的四处常数：{@link #ROW_HEIGHT} 20→18、{@link #READOUT_HEIGHT} 20→18、{@link #CLOSE_HEIGHT}
 * 16→18、{@link #COLUMN_GAP} 6→4（= {@code NekoPocketPanel.COLUMN_GAP}）。派生量跟着走：
 * {@link #CONTENT_TOP} 自 R99 期为 6+18+2 = 26，★R100 起随边距与行缝 = 8+18+3 = <b>29</b>；
 * ★{@link #BOTTOM_STACK} 自 R99 P2 起并带（回执行与关闭钮同带并排），★R100 起随边距 = 4+22+8 = <b>34</b>
 * （R98 期公式 4+22+18+6 = 50 作废）。
 * <p>
 * ★对齐口径也一并定死（不是"看情况"）：面板级的<b>横贯读数</b>一律 {@code CenterLeft}（型名、回执、
 * 三面读数、磁力计数）；<b>多行说明块</b>保持 {@code TopLeft}（魔法使三列的名单行 —— 多行盒从顶排起
 * 才不会把首行推到盒外）。★两枚挂载框标题与磁力说明行都已退场（R100 迁 tooltip、R101.3 去框），
 * 「框内件」这一对齐类不再有在册成员。
 * <p>
 * ★紧凑三型（容量 / 堆叠 / 通道持续化）的回执行按<b>两行</b>给高（{@link #RECEIPT_HEIGHT}=22）：它们的面板
 * 窄，英文最坏回执（守卫拒绝那两长句，逻辑宽 ≈ 417 ⇒ ★R100 的 0.8 档 ≈ 334px）在一两百 px 的盒里一行放不下；
 * 磁力面（★R100 后回执盒 192px）也放不下一行 —— 同一把尺，免得逐型分叉出第二份回执高度账。
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

    /**
     * 外边距（★R100 UI 整改 6 → <b>8</b>）：R99 P1 上装饰层后可见框线约 <b>7px</b>（10px 边距里 3px 平色），
     * 旧值 6 让内容压在框线上 ⇒ 抬到 8，内容起排点落在框线内侧。主面板仍用它的 6（那边有列缝与铆钉的
     * 专用账），两边不再「同一档」是本件有意的一次分离：次级面板的排版基准 = 自己的装饰框。
     */
    public static final int MARGIN = 8;
    /**
     * ★R100 UI 整改：本面板全部文字的缩放档 = <b>0.8</b>（对齐主面板 {@code PocketGhostRequest.STATUS_TEXT_SCALE}
     * 那条 0.8 先例；0.6 档 ≈ 5.4px 的字在次级面板上被用户判「过小」）。
     * <p>
     * ★<b>为什么是面板局部常量而不是抬 {@code RESIDENT_TEXT_SCALE}</b>：那把 0.6 的尺还服务主面板八处窄条
     * （币值条、常驻绑定行…，其像素账的下界卡点都在窄条上）⇒ 抬它就是越出本面板的顺手改；本件五面的
     * 文字全部换到这把新尺，像素账（行盒/按钮/回执折行）已按 0.8 档重算（用例拿两份 lang 真量）。
     */
    public static final float TEXT_SCALE = 0.8f;
    /**
     * 开关行的高（★R98 S4 度量统一 20 → <b>18</b>）：这一行放的控件本身就是 {@link #SWITCH_HEIGHT} 18，
     * 主面板每一行也是 18 —— 旧值多出的那 2px 是「子面板自成一套第二把尺」的一部分。行与行之间的留白
     * 从此由 {@link #READOUT_GAP} <b>显式</b>给，不再藏在行高里（藏在行高里 ⇒ 每加一行就多送 2px，
     * 五面与主面板的栅格永远咬不合）。
     */
    public static final int ROW_HEIGHT = 18;
    /**
     * 开关按钮的宽（"开启 / 关闭 / 未装"三态都要放得下；★R100 0.8 档 66 → <b>72</b>：
     * 英文最坏态 "Not installed" 要 60px，66 里 9-slice 两道边各吃 4px 只剩 58 ⇒ 加宽到 72。
     * ★R101.3 起钮上文案改状态式（"开启 / 关闭"），英文两枚更短 ⇒ 最坏态仍是 "Not installed"，宽度不动）。
     */
    public static final int SWITCH_WIDTH = 72;
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
    /**
     * 读数行之间的缝（也用于开关行与首个内容段之间；★R100 UI 整改 2 → <b>3</b>：0.8 档字高 8px 下
     * 旧值 2 的缝在实机上读成「两行贴在一起」，抬到 3 行间可辨且不过疏——4 也试过，通道持续化那面
     * （三行内容 + 三条缝）的空白率会顶穿紧凑面 15% 的上限，3 是两判据的交集，与 {@link #MODE_ROW_GAP} 同一档）。
     */
    public static final int READOUT_GAP = 3;
    /**
     * 列间距（★R98 S4 度量统一 6 → <b>4</b> = {@code NekoPocketPanel.COLUMN_GAP}）。★R101.3 磁力面
     * 横排重排后本面板不再直接消费它（磁力横排缝改用同档的 {@link #MAGNET_BUTTON_GAP}），
     * 这条历史档说明保留在这里是因为它记录了两把尺对齐的那次裁定。
     */
    public static final int COLUMN_GAP = NekoPocketPanel.COLUMN_GAP;
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
     * 英文最坏型名串（"Channel Persistence Upgrade Module：Not installed"，逻辑宽 280）在 ★R100 的
     * {@link #TEXT_SCALE}=0.8 档要的像素宽（280 × 0.8 = 224）。★逐型行盒按 {@link #labelWidthOf} 给
     * （短型给短盒，这正是五面差异化的一部分），本常数只钉「五型里最宽的那一盒不许被整体缩到 224 以下」
     * ——缩了，通道持续化那一行的英文就折行顶穿行盒。
     */
    public static final int WORST_LABEL_WIDTH = 224;
    /**
     * 逐型的型名行盒宽（像素预算表，下标 = ordinal；真串真宽由用例拿两份 lang 逐型量，这里只给盒子）。
     * 五型各自的英文最坏串（型名 + "：" + 三态里最长的"未装"）在 ★R100 的 0.8 档分别要
     * ≈173/173/164/224/154px，本表逐型给盒（各 +3~5 上取整余量）。★这是<b>数据表不是分派表</b>：
     * 装什么内容仍只由 {@link #sectionsOf} 说。
     */
    private static final int[] LABEL_BOX_WIDTHS = { 176, 176, 168, 228, 158 };

    // ==== ★R101.3：磁力面的几何（★显示朝向横盘 12 列 × 6 行 = 216×108，仍是五面里唯一带格盘的那面） ====

    /** 名单格盘的单格边长（★与主面板三栏同一把尺，不另立 16/20 的第二档）。 */
    public static final int CELL = NekoPocketPanel.GRID;
    /**
     * 名单格盘的<b>显示</b>列数（★R101.3 朝向对调：竖盘 6 列 × 12 行改横盘 <b>12 列 × 6 行</b>，
     * 用户判竖盘太瘦长）。★单源仍取数据层常量：新列数 = 数据层的行数
     * {@link PocketConstants#MAGNET_FILTER_ROWS}。<b>数据插入序不变、服务端 index 语义不变</b>——
     * index 仍是名单的插入序，格序只是「每行摆几个」从 6 个变 12 个；
     * {@link PocketConstants} 那两个常量一字不动（★对调只发生在 GUI 显示层）。
     */
    public static final int MAGNET_COLUMNS = PocketConstants.MAGNET_FILTER_ROWS;
    /** 名单格盘的<b>显示</b>行数（★R101.3 同上：新行数 = 数据层的列数 {@link PocketConstants#MAGNET_FILTER_COLUMNS}）。 */
    public static final int MAGNET_ROWS = PocketConstants.MAGNET_FILTER_COLUMNS;
    /** 盘面宽 = {@code 12 × 18 = 216}。 */
    public static final int MAGNET_GRID_WIDTH = MAGNET_COLUMNS * CELL;
    /** 盘面高 = {@code 6 × 18 = 108}。 */
    public static final int MAGNET_GRID_HEIGHT = MAGNET_ROWS * CELL;
    /**
     * 按钮行的 y = 内容段顶（★R101.3 横排重排：三枚控制钮从「左列竖排」移到<b>格盘上方</b>，
     * 用户判「按钮放名单上面」）。
     */
    public static final int MAGNET_BUTTON_ROW_Y = CONTENT_TOP;
    /**
     * 磁力控制钮的宽（★R101.3 横排 128 → <b>78</b>）：三钮横排等宽，
     * {@code 3×78 + 2×4 = 242} 恰 = 本型行带宽（面板 258 − 两个边距）⇒ 按钮行横向零空白。
     * 英文最坏串按 0.8 档一行放得下（目标两档英文缩成 "To player"/"To pocket"，用例逐钮量）。
     */
    public static final int MAGNET_BUTTON_WIDTH = 78;
    /** 磁力控制钮的高（与开关钮同一把尺）。 */
    public static final int MAGNET_BUTTON_HEIGHT = SWITCH_HEIGHT;
    /** 横排三钮之间的缝（★与 {@code NekoPocketPanel#COLUMN_GAP} 同一档 4，两把尺子咬得上）。 */
    public static final int MAGNET_BUTTON_GAP = NekoPocketPanel.COLUMN_GAP;
    /** 按钮行整行的宽 = 三钮 + 两缝（= 本型行带宽 242；横向闭合由 static 块与用例双钉）。 */
    public static final int MAGNET_BUTTON_ROW_WIDTH = 3 * MAGNET_BUTTON_WIDTH + 2 * MAGNET_BUTTON_GAP;
    /**
     * 格盘的 y = 按钮行之下（按钮行 + 一行高 + 一条缝；★派生式不手抄）。本面纵向的自上而下：
     * 开关行（{@link #CONTENT_TOP} 止）→ 按钮行 → 格盘 → 计数行 → 底部带。
     */
    public static final int MAGNET_GRID_Y = MAGNET_BUTTON_ROW_Y + MAGNET_BUTTON_HEIGHT + READOUT_GAP;
    /** 计数行的 y = 盘底之下一条缝（★R101.3：计数行贴盘下，不再住在旧左列的第 4 段）。 */
    public static final int MAGNET_COUNT_Y = MAGNET_GRID_Y + MAGNET_GRID_HEIGHT + READOUT_GAP;
    /**
     * 磁力内容段的高 = 按钮行 + 缝 + 盘 + 缝 + 计数行（150 ⇒ 面高 29 + 150 + 34 = <b>213</b>；
     * 计数行下沿恰好落在底部两件之上，纵向闭合由 static 块钉）。
     */
    public static final int MAGNET_SECTION_HEIGHT = MAGNET_COUNT_Y + READOUT_HEIGHT - CONTENT_TOP;
    /** 计数读数行的宽（★R101.3 起横贯按钮行带 = 面板可用宽 242，与按钮行同一把尺）。 */
    public static final int MAGNET_COUNT_WIDTH = MAGNET_BUTTON_ROW_WIDTH;

    // ==== ★R101.3：魔法使面的几何（去挂载框：三列模式控件直接放面板，容量行整行退场） ====

    /** 魔法使面里<b>一行模式控件</b>的高（= 一枚按钮的高，与 {@link #SWITCH_HEIGHT} 同档）。 */
    public static final int MODE_ROW_HEIGHT = 18;
    /**
     * 模式按钮行与名单文字行之间的缝（★R100 起 3 与 {@link #READOUT_GAP} 同一档；R101 横排后
     * 这条缝是「上钮下名两层之间」的层间缝，R101.3 去框后语义不变、坐标从框内改到面板）。
     */
    public static final int MODE_ROW_GAP = 3;
    /** 名单三列之间的缝（列缝取窄档：三列等分后每列还要装最坏模式名）。 */
    public static final int MODE_COLUMN_GAP = 2;
    /**
     * 模式控件那枚小按钮的宽（只装状态两字 "On"/"Off"，★不装三态 —— 那在按钮的动态 overlay 里）。
     * ★R100 几何修正 30 → <b>46</b>：{@code BUTTON} 贴图是 88×18 的 9-slice（N=4），30px 宽时
     * 中带只剩 22px = 源 80px 的 27%，高光带被压成竖条（用户点名的「小钮变形」正身）；46px 时中带
     * 38px = 源的 46%。主面板最窄的按钮本来就是 88，次级面板所有用这张贴图的钮一律 ≥ 46。
     * ★R101.2 起本面的钮用这张贴图（见 {@code applyConfigButtonStyle}），★R101.3 起钮上的字是
     * 状态（"开启 / 关闭" / "On / Off"）而不是行为（"打开 / 关掉" / "Turn on / Turn off"）——
     * 文案变短不回削宽度：46 那条 9-slice 下界与像素账继续钉着。
     */
    public static final int MODE_BUTTON_WIDTH = 46;
    /**
     * 魔法使内容段的可用宽（★R101.3 去框后 = 面板宽 − 两个边距 = <b>232</b>）。★恰等于本型行带宽
     * （型名盒 158 + 缝 2 + 开关 72）——这是旧 {@code MAGE_FRAME_WIDTH} 那条「框宽 = 行带宽」判据的
     * 去框正身，由用例 {@code config_panel_text_scale_and_layout_rework} 的⑤钉住。★写成字面量而不是
     * {@code switchRowWidthOf(MAGE)} 式派生：本件的源码门禁钉着「不许出现第二份型清单」
     * （用例对 {@code PocketUpgradeType.MAGE} 字面计数 = 0），「段宽 == 行带宽」这条派生关系由用例钉。
     */
    public static final int MAGE_CONTENT_WIDTH = 232;
    /**
     * 名单<b>列</b>宽（派生式）：内容段可用宽按模式行数等分、列间留 {@link #MODE_COLUMN_GAP} 缝
     * ⇒ (232 − 2×2) / 3 = <b>76</b>。★名单文字按列与按钮对齐（文字盒 76×{@link #MODE_LABEL_HEIGHT}
     * 两行盒；英文最坏模式名按 0.8 档折两行装得下，用例逐列量）。
     */
    public static final int MODE_COLUMN_WIDTH = (MAGE_CONTENT_WIDTH - (modeRowCount() - 1) * MODE_COLUMN_GAP)
        / modeRowCount();
    /**
     * 名单文字行的盒高（★<b>两行盒</b> = 20px）：英文最坏模式名（"Essence transmuting"，逻辑宽 111）
     * 在 76px 的列里按 0.8 档要 89px ⇒ 折两行（10px 行高 × 2）；中文最坏 40px 一行装得下。
     * ★三列横排装不下「模式名 + ：+ 状态」（en 最坏 111px ×0.8 = 89 ×3 列 > 232）⇒ 状态读数收进
     * 按钮的动态 overlay（"开启 / 关闭"本来就是状态位），名单行只念模式名 —— 信息一项不减。
     */
    public static final int MODE_LABEL_HEIGHT = 20;
    /** 魔法使内容段的纵向预算 = 按钮行 + 层间缝 + 名单行（41；⇒ 面高 29 + 41 + 34 = <b>104</b>）。 */
    public static final int MAGE_MODE_STACK_HEIGHT = MODE_ROW_HEIGHT + MODE_ROW_GAP + MODE_LABEL_HEIGHT;
    /**
     * 容量读数块的高（★两行盒 = {@link #RECEIPT_HEIGHT}）：R101 起这一句是配置面独有的关闭规则说明
     * （{@code gtit.pocket.config.capacity.rule}），英文最坏串在容量面横贯盒（★R100 后 250px）里按
     * 0.8 档仍要折两行 —— 用例 {@code config_panel_geometry_within_secondary_caps} 拿两份 lang 逐型量，
     * 量到两行就必须留两行的高（★R98 逐型几何目标给的 94 是「一行装得下」那一支，实测吃不下 ⇒
     * 一直留在两行盒，不削盒）。
     */
    public static final int CAPACITY_READOUT_HEIGHT = RECEIPT_HEIGHT;

    // ==== ★R100：通道持续化面的频率段几何（该面第二行专属内容，只此一型画） ====

    /**
     * 频率段的 y = 常开读数那一行之下（内容段顶 + 一行读数 + 一条缝，★派生式不手抄）。
     */
    public static final int FREQ_ROW_Y = CONTENT_TOP + READOUT_HEIGHT + READOUT_GAP;
    /**
     * 频率段的<b>读数标签</b>宽（★R100 0.8 档定为「填满行带」的账：标签盒 + 一条缝 + 输入框 =
     * 本型行带宽 302 ⇒ 本行横向零空白）。
     */
    public static final int FREQ_LABEL_WIDTH = 206;
    /**
     * ★R101：频率<b>秒值输入框</b>的宽（★R100 期的「更快/更慢」两枚 46px 小钮随档位表一起退场，
     * 原位置改为一枚 MUI2 {@code TextFieldWidget}：显示当前秒数、仅收 1–60 的正整数、
     * 回车或失焦提交、非法输入回显现值）。派生：行带 302 − 标签 206 − 一条缝 2 = <b>94</b>，
     * 行带与面板宽（318）一个字不动。
     */
    public static final int FREQ_FIELD_WIDTH = 94;
    /** 标签与输入框之间的缝（与行内其余缝同档；沿用旧小钮行的缝常数名）。 */
    public static final int FREQ_BUTTON_GAP = 2;

    /**
     * 一行（或一面）的<b>内容段</b>（★按型分派，"五型五面"的正身）。{@link #SWITCH} 是公共段
     * （五面顶上都那一行），其余五段<b>每型恰一段</b>：三个读数段（紧凑面）与两个挂载段（磁力 / 魔法使）。
     * ★R100 起通道持续化面<b>多一段</b> {@link #FREQUENCY}（频率档位行）——五型里唯一的
     * 「开关 + 两段专属内容」形状（段族对账在 static 块里对它单列特判，其余四型仍恒两段）。
     * <p>
     * ★R100 D6③ 三 switch 收敛：本枚举<b>自带两份段元数据</b>——{@link #readoutKey}（本段独有的读数键；
     * 挂载段与开关段为 {@code null}）与 {@link #contentWidth()}/{@link #contentHeight()}（段的宽高预算）。
     * 收敛前 {@link #readoutKeyOf} / {@link #sectionContentWidth} / {@link #sectionContentHeight} 三处
     * 各写一遍对同一枚举的 switch（追加段族要改四处才齐）；收敛后三处都读这里，被钉的方法名只剩一行转发。
     * ★构造参数只许<b>字面量</b>（构造期读外部常量会在嵌套类递归初始化里拿到半初始化值——见
     * {@code MODE_COLUMN_WIDTH} 那族派生式全在方法体里取数的原因）。
     */
    public enum Section {

        /** 开 / 关切换（五型都有，恒为第一段）。行带宽度按型给（{@code switchRowWidthOf}），不自带宽高。 */
        SWITCH(null) {

            @Override
            public int contentWidth() {
                return 0;
            }

            @Override
            public int contentHeight() {
                return 0;
            }
        },
        /**
         * 容量面的读数段：★R101 改判——量级口径（20M/2G per tank + 合计）换成<b>关闭规则说明</b>。
         * 读数横贯整面（不自带宽）。★键从主面板的 {@code gtit.pocket.fluid.capacity} 改为本型独有的
         * {@code gtit.pocket.config.capacity.rule}：主面板那句还有流体格件 tooltip 与底部带说明块
         * tooltip 两个消费面（数字读数在那里仍然成立），不能跟着本面一起改义。
         */
        READOUT_CAPACITY("gtit.pocket.config.capacity.rule") {

            @Override
            public int contentWidth() {
                return 0;
            }

            @Override
            public int contentHeight() {
                // ★两行盒：那句英文最坏串在横贯盒里折两行（用例逐型像素账量到）。
                return CAPACITY_READOUT_HEIGHT;
            }
        },
        /** 堆叠面的读数段：单格上限两档（64 → 1024）一行。★R98 S4 DP-7 撤掉同尺说明那一行。 */
        READOUT_STACK("gtit.pocket.config.stack.limit") {

            @Override
            public int contentWidth() {
                return 0;
            }

            @Override
            public int contentHeight() {
                // ★R98 S4 DP-7：42（两行 + 缝）→ 18（只剩单格上限那一行）。
                return READOUT_HEIGHT;
            }
        },
        /** 通道持续化面的读数段：常开现状一行。★R98 S4 DP-7 撤掉按钮禁用与帧带常亮两条说明。 */
        READOUT_PERSIST("gtit.pocket.config.persist.idle") {

            @Override
            public int contentWidth() {
                return 0;
            }

            @Override
            public int contentHeight() {
                // ★R98 S4 DP-7：64（三行 + 两缝）→ 18（只剩常开状态那一行）。
                return READOUT_HEIGHT;
            }
        },
        /** ★R100：通道持续化面的<b>频率段</b>——档位读数一行 + ★R101 起秒值输入框（只此一型有）。 */
        FREQUENCY(null) {

            @Override
            public int contentWidth() {
                return FREQ_LABEL_WIDTH + FREQ_BUTTON_GAP + FREQ_FIELD_WIDTH;
            }

            @Override
            public int contentHeight() {
                // ★本段住在常开读数行<b>之下</b> ⇒ 段高从内容段顶起算含上面那一行（行 + 缝 + 行），
                // 与 MOUNT_* 的「框高即段高」同一口径。
                return READOUT_HEIGHT + READOUT_GAP + ROW_HEIGHT;
            }
        },
        /** 磁力面的挂载段：72 格 phantom 名单盘（★R101.3 横盘 12×6）+ 盘上方按钮行 + 盘下计数行（★R100 说明行迁 tooltip）。 */
        MOUNT_MAGNET(null) {

            @Override
            public int contentWidth() {
                return MAGNET_BUTTON_ROW_WIDTH;
            }

            @Override
            public int contentHeight() {
                return MAGNET_SECTION_HEIGHT;
            }
        },
        /** 魔法使面的挂载段：★R101.3 去框（按钮行在上 + 名单行在下的三列模式控件直接放面板；容量行退场）。 */
        MOUNT_MAGE(null) {

            @Override
            public int contentWidth() {
                return MAGE_CONTENT_WIDTH;
            }

            @Override
            public int contentHeight() {
                return MAGE_MODE_STACK_HEIGHT;
            }
        };

        /** 本段<b>独有</b>的读数键（读数段之外一律 {@code null}；三段的字面量与外部 *_KEY 常量同源）。 */
        public final String readoutKey;

        Section(String readoutKey) {
            this.readoutKey = readoutKey;
        }

        /** 这一段内容块的宽（像素预算）；读数段横贯整面（0），挂载段与频率档位行自带硬宽。 */
        public abstract int contentWidth();

        /** 这一段内容块的高（像素预算；{@link #SWITCH} 的高已计入 {@link #CONTENT_TOP}）。 */
        public abstract int contentHeight();
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
                "[pocket] 型名行盒被整体缩水: 最宽一型 " + widestLabel + " < " + WORST_LABEL_WIDTH + "（0.8 档的英文最坏串会折行顶穿 18px 行盒）");
        }
        // ★磁力格盘的算术（★本面整个宽账就是被 216×108 这块硬形状逼出来的）。
        if (MAGNET_COLUMNS * CELL != MAGNET_GRID_WIDTH || MAGNET_ROWS * CELL != MAGNET_GRID_HEIGHT) {
            throw new IllegalStateException("[pocket] 栅格乘式与盘面尺寸不符（★格数不得是裸字面量）");
        }
        if (MAGNET_ROWS * MAGNET_COLUMNS != PocketConstants.MAGNET_FILTER_SLOTS) {
            throw new IllegalStateException("[pocket] 磁力格盘行列乘积不等于数据层的条目预算");
        }
        // ★R101.3 横盘的横向闭合：按钮行（三钮两缝）+ 两个边距必须恰好 = 面板宽（本面最宽的内容就是这一行）。
        if (MARGIN + MAGNET_BUTTON_ROW_WIDTH + MARGIN != panelWidthOf(magnetType())) {
            throw new IllegalStateException("[pocket] 磁力面横向不闭合（按钮行 + 两边距 ≠ 面板宽）: " + panelWidthOf(magnetType()));
        }
        // ★R101.3 横盘居中：盘面两侧的余量必须相等（面板宽 − 盘宽是偶数，盘 x = 余量的一半）。
        if ((panelWidthOf(magnetType()) - MAGNET_GRID_WIDTH) % 2 != 0
            || 2 * magnetGridXOf(magnetType()) + MAGNET_GRID_WIDTH != panelWidthOf(magnetType())) {
            throw new IllegalStateException(
                "[pocket] 磁力横盘不居中: 盘 x=" + magnetGridXOf(magnetType()) + " 面板宽=" + panelWidthOf(magnetType()));
        }
        // ★R101.3 横盘的纵向闭合：自上而下 = 开关行 → 按钮行 → 格盘 → 计数行，计数行下沿必须恰好落在
        // 底部两件之上（有人把某一段加高/挪位时，面高派生式与行位派生式在这里对不上账就先红）。
        if (MAGNET_BUTTON_ROW_Y + MAGNET_BUTTON_HEIGHT != MAGNET_GRID_Y - READOUT_GAP
            || MAGNET_GRID_Y + MAGNET_GRID_HEIGHT != MAGNET_COUNT_Y - READOUT_GAP
            || MAGNET_COUNT_Y + READOUT_HEIGHT != panelHeightOf(magnetType()) - BOTTOM_STACK) {
            throw new IllegalStateException(
                "[pocket] 磁力面纵向不闭合（按钮行/格盘/计数行的派生式与面板高对不上）: 面板高=" + panelHeightOf(magnetType()));
        }
        if (MAGNET_COUNT_WIDTH <= 0) {
            throw new IllegalStateException("[pocket] 磁力计数读数行没有宽度可占");
        }
        // ★魔法使段的两条闭合（★R101.3 去框后 = 面板级的三列布局）：纵向 = 按钮行 + 缝 + 名单行的段高
        // 进面板高的派生式；横向 = 名单三列（列宽等分）+ 列间缝必须收得进内容段可用宽。
        // ★文字真宽由用例逐型像素账量（这里只钉盒子，同 WORST_LABEL_WIDTH 那条例的分工）。
        if (CONTENT_TOP + MAGE_MODE_STACK_HEIGHT > panelHeightOf(mageType()) - BOTTOM_STACK) {
            throw new IllegalStateException(
                "[pocket] 魔法使模式段纵向不闭合: 段高=" + MAGE_MODE_STACK_HEIGHT + " 面板高=" + panelHeightOf(mageType()));
        }
        if (MODE_COLUMN_WIDTH < 40
            || modeRowCount() * MODE_COLUMN_WIDTH + (modeRowCount() - 1) * MODE_COLUMN_GAP > MAGE_CONTENT_WIDTH) {
            throw new IllegalStateException(
                "[pocket] 魔法使名单三列不闭合: 列宽=" + MODE_COLUMN_WIDTH
                    + "，列数="
                    + modeRowCount()
                    + "，可用宽="
                    + MAGE_CONTENT_WIDTH);
        }
        // ★分派表与内容段族必须对得上：六个值逐个点名 ⇒ 追加第 6 型时 sectionsOf 不表态就先在这里红。
        // ★R100：段族从「每型恰两段」放宽成「四型恰两段 + 通道持续化恰三段（开关 + 常开读数 + 频率档）」
        // ——不是把口子放开：第三段的存在被<b>逐字点名</b>成 [SWITCH, READOUT_PERSIST, FREQUENCY] 那一个
        // 形状，任何别的型多出一段、或本型多出的不是 FREQUENCY，都在这里先红。
        int secondSections = 0;
        for (final PocketUpgradeType type : PocketUpgradeType.values()) {
            final List<Section> sections = sectionsOf(type);
            final boolean plainPair = sections.size() == 2 && sections.get(0) == Section.SWITCH;
            final boolean persistTriple = type == PocketUpgradeType.CHANNEL_PERSIST
                && sections.equals(Arrays.asList(Section.SWITCH, Section.READOUT_PERSIST, Section.FREQUENCY));
            if (!plainPair && !persistTriple) {
                throw new IllegalStateException(
                    "[pocket] " + type
                        + " 的内容段族不在册（四型 = 开关 + 恰一段；通道持续化 = 开关 + 常开读数 + 频率档）: "
                        + sections
                        + "（★五型五面：每面 = 公共开关行 + 本型自己的内容段，不多画也不少画）");
            }
            secondSections++;
        }
        if (secondSections != PocketUpgradeType.values().length) {
            throw new IllegalStateException("[pocket] 分派表点名数 " + secondSections + " ≠ 型数（表与枚举不齐）");
        }
        // ★R101：频率段必须收得进本型行带（面板宽 − 两个边距）：标签 + 缝 + 秒值输入框。
        if (MARGIN + FREQ_LABEL_WIDTH + FREQ_BUTTON_GAP + FREQ_FIELD_WIDTH + MARGIN
            > panelWidthOf(PocketUpgradeType.CHANNEL_PERSIST)) {
            throw new IllegalStateException("[pocket] 频率段横向不闭合（标签 + 输入框顶出面板宽）");
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
                // ★R100：本型独有「开关 + 常开读数 + 频率档」三段（频率档位只作用于持续化通道，
                // 手动付费短效道 1s×30 批不提供调节面，R100 用户裁决）。
                return Arrays.asList(Section.SWITCH, Section.READOUT_PERSIST, Section.FREQUENCY);
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

    // ★★R101.3：挂载框标题族整批退场（旧 mountTitleKey(MAGNET/MAGE) 与两键
    // gtit.pocket.config.mount.magnet / mount.mage 随两块挂载框一起撤——去框后没有框就没有框标题，
    // 键与消费件同批撤，不留"键还在但没人读"的第二份僵尸，R96 S5 先例）。

    // ==================================================== ★R98 S4（DP-4 正判据②）三个简单型各自的读数键
    //
    // ★R98 把 CAPACITY / STACK / CHANNEL_PERSIST 三面收到「开关 + 一行读数」（DP-7）之后，"五面不是同一张脸"
    // 这件事不能再靠三面的<b>高度差</b>来证（三面本来就快一样高了）。这一族常数把那"一行"逐型点名：每型恰
    // 一条<b>本型独有</b>的读数键，装配侧与用例读的是同一张表 ⇒ 任何两型共用同一条（＝ 把三型并成一张脸）
    // 会在用例 config_panel_geometry_within_secondary_caps 的判据②上直接红。

    /**
     * 容量面那一行读的键（★R101 改判：配置面改读<b>关闭规则说明</b>，键本型独有；主面板量级句
     * {@code gtit.pocket.fluid.capacity} 与其格式源 {@code NekoPocketPanel#capacityReadoutText} 不跟改——
     * 那句仍由流体格件 tooltip 与底部带说明块 tooltip 消费，两句话两份职责；字面量单源在
     * {@link Section#READOUT_CAPACITY}）。
     */
    public static final String READOUT_CAPACITY_KEY = Section.READOUT_CAPACITY.readoutKey;
    /** 堆叠面那一行读的键（★本型独有：单格上限 64 ↔ 1024；字面量单源在 {@link Section#READOUT_STACK}）。 */
    public static final String READOUT_STACK_KEY = Section.READOUT_STACK.readoutKey;
    /** 通道持续化面那一行<b>未常开</b>支的键（★本型独有；常开支复用的是主面板那条 {@code channel.always_on}；字面量单源在 {@link Section#READOUT_PERSIST}）。 */
    public static final String READOUT_PERSIST_KEY = Section.READOUT_PERSIST.readoutKey;

    // ==================================================== ★R100 → ★R101：频率段的数据面（秒值域单源在 PocketConstants）

    /** ★R100：频率段的读数键（占位 {@code %d} = 秒/批；★同键复用为写腿的粘性回执，不另立第二条）。 */
    public static final String READOUT_FREQ_KEY = "gtit.pocket.config.persist.freq";
    /**
     * ★R101：频率秒值输入框的 tooltip 键（旧"更快/更慢"两枚按钮的 tooltip 键原键改义：讲的是
     * "这一框怎么填、何时生效"）。★撤键必须与装配侧、两份 lang 同批（R96 S5 先例）。
     */
    public static final String READOUT_FREQ_HINT_KEY = "gtit.pocket.config.persist.freq.hint";

    /**
     * ★R101：频率秒值输入的<b>解析纯函数</b>（装配侧输入框与服务端提交侧共读同一份判域）：
     * 去首尾空白、全数字、值域 {@code [1,60]}（{@code PocketConstants#channelFreqSecondsClamp} 的域）
     * ⇒ 回秒值；其余（空串 / 非数字 / 越域 / 溢出）一律回 <b>0</b>，调用方必须把文本回显现值，
     * ★不许猜一个"最近合法值"替玩家做主。
     */
    public static int parseFreqSecondsInput(String input) {
        if (input == null) {
            return 0;
        }
        final String trimmed = input.trim();
        if (trimmed.isEmpty()) {
            return 0;
        }
        for (int i = 0; i < trimmed.length(); i++) {
            final char c = trimmed.charAt(i);
            if (c < '0' || c > '9') {
                return 0;
            }
        }
        final int seconds;
        try {
            seconds = Integer.parseInt(trimmed);
        } catch (NumberFormatException ignored) {
            return 0;
        }
        return PocketConstants.channelFreqSecondsClamp(seconds) == seconds ? seconds : 0;
    }

    /**
     * ★R101：服务端唯一的<b>频率秒值</b>写腿（旧 {@code commitFreqTier} 的位置与三步形状逐字继承，
     * 目标从"档位下标"换成"秒/批"；域 [1,60] 的钳制与旧键映射单源在 {@code PocketConstants}）。
     * <p>
     * 三步与开关腿同构：① 这一型在不在档上（{@link #switchState} == {@code ABSENT} ⇒ 不写——给没装
     * 持久化的口袋写频率是假读数的种子）；② 越域/同值重复到达 ⇒ 零写入（连点不刷整栈同步）；
     * ③ 落档走 {@code PocketConstants#writeChannelFreqSeconds}（新键 {@code channelFreqSeconds}；
     * 旧档位键 {@code channelFreqTier} 一字不动，读取侧按"新键优先"回落映射）。
     * <p>
     * ★不判持有者 / 载体身份（那两判在 handler 侧的 {@code serverGuardOk}，同 {@link #commitSwitch} 的分工）；
     * ★不碰通道状态（在跑的道换节拍由 handler 在写腿成功后调 {@code PocketChannelManager#retimePersistentChannel}，
     * 那是一条幂等的运行期腿，不该长在纯函数里）。
     *
     * @return 本次是否真的改变（{@code false} = 越域 / 不在档上 / 同值，全部<b>零写入</b>）
     */
    public static boolean commitChannelFreqSeconds(ItemStack carrier, int seconds) {
        if (PocketConstants.channelFreqSecondsClamp(seconds) != seconds) {
            return false;
        }
        if (switchState(carrier, PocketUpgradeType.CHANNEL_PERSIST) == SwitchState.ABSENT) {
            return false;
        }
        final net.minecraft.nbt.NBTTagCompound root = carrier == null ? null : carrier.getTagCompound();
        if (root == null) {
            // 无档 = 还没固化过这一型：不建档（R53c），口径同 commitMode 的 NOT_ON_RECORD 支
            return false;
        }
        return PocketConstants.writeChannelFreqSeconds(root, seconds);
    }

    /**
     * 这一型读数段<b>本型独有</b>的那条 lang 键（★DP-4 正判据② 的单源：判据经 {@link #sectionsOf} 的第二段
     * 走，不在这里抄第二份型清单）。两个挂载型回 {@code null} —— 磁力的读数（名单计数）住在挂载段里；
     * 魔法使的框内容量行已随 R101.3 去框整行退场。不占这一族。★R100 D6③ 收敛后本方法只剩一行转发：
     * 键的真相在 {@link Section#readoutKey}（每个读数段自带），这里不再养第二份对同一枚举的 switch。
     */
    public static String readoutKeyOf(PocketUpgradeType type) {
        final List<Section> sections = sectionsOf(type);
        if (sections.size() < 2) {
            return null;
        }
        return sections.get(1).readoutKey;
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

    /**
     * 开关按钮上的文字（三态各一条键；★"未装"不许显示成"已关闭"，那暗示着"还能开回来"）。
     * ★R101.3 改判：按钮显示的是<b>状态</b>而不是行为（旧 turn_off/turn_on 的"关掉 / 打开"是
     * 行为描述，用户判应显示"开启 / 关闭"）——ON 态念 {@code switch.state_on}、OFF 态念
     * {@code switch.state_off}，两键随旧两键同批撤换（键总数不变）。魔法使三模式钮共用此映射自动跟随。
     */
    public static String switchLabelKey(SwitchState state) {
        switch (state) {
            case ON:
                return "gtit.pocket.config.switch.state_on";
            case OFF:
                return "gtit.pocket.config.switch.state_off";
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
                // ★R101.3：开关钮改<b>状态</b>文案——旧 switch.turn_on/turn_off（"打开 / 关掉"，行为式）
                // 两键撤，新 switch.state_on/state_off（"开启 / 关闭"，状态式）两键增，键总数不变。
                "gtit.pocket.config.switch.state_on",
                "gtit.pocket.config.switch.state_off",
                "gtit.pocket.config.switch.none",
                "gtit.pocket.config.switch.hint",
                // ★R101.3：两枚挂载框标题键（mount.magnet / mount.mage）随两块框一起撤（去框 = 无框标题）。
                // ★R97 S5：五面各自的读数/说明键（紧凑三型）。★R96 S2 那条
                // gtit.pocket.config.mount.pending 随「五行常驻一面」一起退场（五型五面后不存在
                // 「有框没内容」的挂载位 ⇒ 占位文案失去消费者，键与文本件一并撤销）。
                // ★R98 S4 DP-7：紧凑三面收到「开关 + 一行读数」，三条<b>静态说明</b>键随各自的文本件
                // 一起撤 —— gtit.pocket.config.stack.note（同尺说明）、gtit.pocket.config.persist.button
                // （常开期间按钮禁用）、gtit.pocket.config.persist.frame（帧带常亮）。★这里撤键必须与
                // 装配侧、两份 lang 同批：留一半就是"键还在但没人读"的第二份僵尸（R96 S5 的先例）。
                READOUT_CAPACITY_KEY,
                READOUT_STACK_KEY,
                READOUT_PERSIST_KEY,
                // ★R100：频率段两键（读数 + ★R101 输入框 tooltip；只随 CHANNEL_PERSIST 那一面消费）。
                // ★R101 撤「更快/更慢」两枚按钮 ⇒ faster/slower 两键随消费件一起退场（撤键与两份 lang 同批）。
                // ★R101.3：gtit.pocket.config.mage.capacity（魔法使元素容量行）随该行一起退场。
                READOUT_FREQ_KEY,
                READOUT_FREQ_HINT_KEY,
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
        // 计数读数 + 清空按钮两行）。★R101 撤逐条描述：格件 tooltip 收到只剩物品显示名 ⇒
        // 格件四行（empty/remove/number/gesture）与四条拒收支键随消费件一起退场（撤键与两份 lang 同批）；
        // 三态不对称说明仍由三态钮的 tooltip 消费（键不删只减消费面）。
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
                "gtit.pocket.magnet.clear.hint"));
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

    /**
     * 这一段内容块的宽（像素预算）。★R100 D6③ 收敛后本方法只是 {@link Section#contentWidth()} 的
     * 一行转发（被钉的调用形态不变）：段的宽预算与段本体住在同一处，追加段族不再要「枚举 + 两处 switch」
     * 三地同改。读数段横贯整面（回 0），挂载段与频率档位行自带硬宽。
     */
    static int sectionContentWidth(Section section) {
        return section.contentWidth();
    }

    /**
     * 这一段内容块的高（像素预算；{@link Section#SWITCH} 的高已计入 {@link #CONTENT_TOP}）。
     * ★公开给用例的逐型闭合账；★R100 D6③ 收敛后同上，只是 {@link Section#contentHeight()} 的转发。
     */
    public static int sectionContentHeight(Section section) {
        return section.contentHeight();
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

    /**
     * 盘面原点的 x（★R101.3 横盘居中：{@code (面板宽 − 盘宽) / 2}；本面宽 258、盘宽 216 ⇒ <b>21</b>。
     * 旧「右贴边的挂载框 + 框内 inset」两段式随去框一起作废）。
     */
    public static int magnetGridXOf(PocketUpgradeType type) {
        return (panelWidthOf(type) - MAGNET_GRID_WIDTH) / 2;
    }

    /** 盘面原点的 y（★按钮行之下：常量单源 {@link #MAGNET_GRID_Y}，这里只是一行转发）。 */
    public static int magnetGridY() {
        return MAGNET_GRID_Y;
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

    /**
     * 分派表里带 {@link Section#MOUNT_MAGE} 的那一型（★几何断言用；找法与 {@link #magnetType()} 同一条
     * ——经分派表而不是「型 == MAGE」的字面清单，本件对 {@code PocketUpgradeType.MAGE} 的字面计数必须 = 0，
     * 用例 {@code mage_config_panel_mode_codec_and_commit_leg} 钉着）。
     */
    private static PocketUpgradeType mageType() {
        for (final PocketUpgradeType type : PocketUpgradeType.values()) {
            if (sectionsOf(type).contains(Section.MOUNT_MAGE)) {
                return type;
            }
        }
        throw new IllegalStateException("[pocket] 分派表里没有任何一型挂魔法使模式段（三列模式控件失去了宿主）");
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
        // ★R99 P1（T3 档）：五面最前的一组直接 child = 与主面板同源的 C2 装饰底（cloth + 木框 9-slice
        // + 4 包角）。MUI2 按 child 序绘制 ⇒ 底材在最下，控件画在框面上；这也是「MUI2 面板主题默认底
        // GuiTextures.MC_BACKGROUND（浅灰板）」被盖掉的那一层——R98 判不合格的"裸浅灰底"就是缺它。
        // ★★R101.1 摊平（appendTo，不再是装饰根子树）：MUI2 多 child 面板两波绘制——先画各顶层 child
        // 自己的 background、再逐个子树画 draw/overlay 与子树内背景；装饰子树的贴图第二波才铺，
        // 会把第一波先画的按钮背景盖掉（实机判"按钮没按钮的样式"的根因，R101.1 前按钮底从未显形）。
        NekoPocketDecoration.appendTo(panel, panelWidthOf(type), panelHeightOf(type));
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
                case FREQUENCY:
                    // ★R100：频率档位段（档位读数一行 + 更快/更慢两枚按钮），只随 CHANNEL_PERSIST 那一分派支可达。
                    frequencyRow(ui, panel, type);
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

    // ------------------------------------------------------------------ ★R101.2 五面按钮继承主面板按钮样式

    /**
     * ★R101.2 UI 整改（对 R101 纯色三态的实机翻案）：五面的按钮<b>直接继承主面板通道按钮</b>的既有
     * 样式——{@code background} = {@link PocketGuiTextures#BUTTON}（88×18，N=4 九宫）、
     * {@code hoverBackground} = {@link PocketGuiTextures#BUTTON_PRESSED}（主面板"启动次元通道"
     * 同款同槽位，NekoPocketBottomBand 装配式照抄），{@code overlay} 只挂内容件。尺寸与内容一个字不动。
     * <p>
     * 为什么 R101 的纯色三态会"生效却难看"、而 R100 的 BUTTON 贴图底从未显形：两波绘制盖底（根因
     * 见 {@link NekoPocketDecoration#appendTo} 的 javadoc）——贴图底当时根本没画出来，纯色底画出来
     * 了但与主面板风格不合，用户 2026-09-29 实机判"太丑，直接继承主面板的按钮"。
     * <p>
     * 悬浮/按压语义也与主面板对齐：hover 整层换 BUTTON_PRESSED，不再有第三态压暗层
     * （主按钮没有，继承就不自己发明）。传 {@code null} = 无文字钮（overlay 槽留空）。
     */
    private static void applyConfigButtonStyle(ButtonWidget<?> button, IDrawable content) {
        button.background(PocketGuiTextures.BUTTON);
        button.hoverBackground(PocketGuiTextures.BUTTON_PRESSED);
        if (content != null) {
            button.overlay(content);
        }
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
            .scale(TEXT_SCALE)
            .color(PocketGhostRequest.readoutTextColor())
            .shadow(Boolean.TRUE)
            .name("pocket_config_label_" + type.ordinal())
            .pos(MARGIN, MARGIN)
            .size(labelWidthOf(type), ROW_HEIGHT);
    }

    /**
     * 开关（★唯一出口是发码，本地零写入；★排在本行右端，与型名同一横带）。
     * ★R101.2：底换主面板同款 BUTTON 贴图（见 {@link #applyConfigButtonStyle}）；逐条语句设定取
     * {@code NekoPocketBottomBand#persistentBindRow} 的同一条先例（链式拿不到 self 型时不再硬拧）。
     */
    private static IWidget switchButton(NekoPocketPanel ui, PocketUpgradeType type) {
        final ButtonWidget<?> button = new ButtonWidget<>();
        button.pos(MARGIN + labelWidthOf(type) + 2, MARGIN)
            .size(SWITCH_WIDTH, SWITCH_HEIGHT)
            .name("pocket_config_switch_" + type.ordinal())
            .tooltipDynamic(tooltip -> tooltip.addLine(IKey.lang("gtit.pocket.config.switch.hint")))
            .tooltipAutoUpdate(true)
            .playClickSound(true)
            // ★只有左键生效（同 R83 B2(5) 的口径：丢掉 button 形参 = 右键也切一次）；
            // ★★这里一个字节都不写档 —— 客户端私写 off-mask 是门禁 G12 / 门 D 钉死的 FAIL 形状。
            .onMousePressed(press -> press == 0 && ui.requestUpgradeSwitch(type, nextOff(ui.carrierStackLive(), type)));
        applyConfigButtonStyle(
            button,
            IKey.dynamic(
                () -> StatCollector.translateToLocal(switchLabelKey(switchState(ui.carrierStackLive(), type)))));
        return button;
    }

    /** 回执行：最近一次切换成了什么 / 为什么被拒（★走既有粘性回执通道，不新建通道；★两行盒）。 */
    private static IWidget receiptLine(NekoPocketPanel ui, PocketUpgradeType type) {
        return (IWidget) new TextWidget(IKey.dynamic(ui::upgradeConfigReceiptText)).textAlign(Alignment.CenterLeft)
            .scale(TEXT_SCALE)
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
        final ButtonWidget<?> button = new ButtonWidget<>();
        button
            .pos(
                panelWidthOf(type) - MARGIN - CLOSE_WIDTH,
                panelHeightOf(type) - MARGIN - RECEIPT_HEIGHT + (RECEIPT_HEIGHT - CLOSE_HEIGHT) / 2)
            .size(CLOSE_WIDTH, CLOSE_HEIGHT)
            .name("pocket_config_close")
            .playClickSound(true)
            .onMousePressed(press -> press == 0 && ui.closeUpgradeConfig());
        // ★R101：底改纯色三态（同开关钮那条）
        applyConfigButtonStyle(button, IKey.lang("gtit.pocket.config.close"));
        return button;
    }

    // ================================================================== ★R98 S4 紧凑三面的读数段（各剩一行）

    /**
     * 容量读数块（★两行盒：R101 起读本型独有的<b>关闭规则说明</b>（{@link #READOUT_CAPACITY_KEY}，
     * 不再复用主面板量级句），规则文案静态一条 ⇒ {@code IKey.lang} 直读，不走动态供串）。
     * <p>
     * ★R98 S4 把对齐从 {@code TopLeft} 换成 {@code CenterLeft}：本面其余读数件（型名、回执）全是
     * {@code CenterLeft}，同一面里两种对齐是取证 {@code 02-gui.md} §4-1 点名那一处。两行盒居中后
     * 上下各余 1px（20 ≤ 22），不顶穿。
     */
    private static IWidget capacityReadoutBlock(NekoPocketPanel ui, PocketUpgradeType type) {
        return (IWidget) new TextWidget(IKey.lang(READOUT_CAPACITY_KEY)).textAlign(Alignment.CenterLeft)
            .scale(TEXT_SCALE)
            .color(PocketGhostRequest.readoutTextColor())
            .shadow(Boolean.TRUE)
            .name("pocket_config_capacity_readout")
            .pos(MARGIN, CONTENT_TOP)
            .size(panelWidthOf(type) - 2 * MARGIN, CAPACITY_READOUT_HEIGHT);
    }

    /**
     * 堆叠面那一行：★R101 改判——量级读数（64 ↔ 1024）换成<b>关闭规则说明</b>（"仅在未超出当前
     * 堆叠上限时，才可以关闭"；数字面与守卫执法面都在服务端，本行不再代念量级）。键不变
     * （{@link #READOUT_STACK_KEY}），lang 文本与格式参数同批翻新。
     */
    private static IWidget stackLimitLine(NekoPocketPanel ui, PocketUpgradeType type) {
        return (IWidget) new TextWidget(IKey.lang(READOUT_STACK_KEY)).textAlign(Alignment.CenterLeft)
            .scale(TEXT_SCALE)
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
                        .scale(TEXT_SCALE)
                        .color(PocketGhostRequest.readoutTextColor())
                        .shadow(Boolean.TRUE)
                        .name("pocket_config_persist_state")
                        .pos(MARGIN, CONTENT_TOP)
                        .size(panelWidthOf(type) - 2 * MARGIN, READOUT_HEIGHT);
    }

    // ================================================================== ★R100 → ★R101 频率段（本型第二行专属内容）

    /**
     * 频率段的整行装配：读数标签（现读载体秒值，vanilla 镜像 ≤1+ tick 跟真值）+ ★R101 起一枚
     * <b>秒值输入框</b>（旧"更快/更慢"两枚升降钮随 11 档封闭表一起退场）。
     * <p>
     * ★输入框行为（任务拍板）：显示当前秒数、仅收正整数（打字层 {@code [0-9]*} 过滤 + 提交层
     * {@link #parseFreqSecondsInput} 判域）、回车或失焦提交（MUI2 的 {@code TextFieldWidget}
     * 两条路都汇到 removeFocus ⇒ setter）、非法输入不发包——底值不变，未聚焦时的 onUpdate 会把
     * 文本刷回现值（"回显原值"）。服务端仍是唯一写腿（{@code commitChannelFreqSeconds}），域钳制
     * 两端各跑一遍但判域同源。
     * <p>
     * ★树形恒定（R32 纪律）：一行两件不随秒值变化，值只进内容层（标签的 {@code IKey.dynamic} 与
     * 输入框的 {@code StringValue.Dynamic} getter）⇒ 同型重开拿到的缓存面板不会因为值改了而拿到一棵旧树。
     * ★读数不另开同步键：走载体栈 NBT 的 vanilla 槽同步（与 {@link #switchState}/{@link #modeState}
     * 同一条通道）。★本行一个字节都不写本地 NBT（写腿在服务端）。
     */
    private static void frequencyRow(NekoPocketPanel ui, ModularPanel panel, PocketUpgradeType type) {
        panel.child(
            (IWidget) new TextWidget(
                IKey.dynamic(
                    () -> String
                        .format(StatCollector.translateToLocal(READOUT_FREQ_KEY), Integer.valueOf(freqSecondsOf(ui))))) // ★%d
                                                                                                                        // =
                                                                                                                        // 当前秒/批（域
                                                                                                                        // [1,60]，旧档位键映射兼容）
                            .textAlign(Alignment.CenterLeft)
                            .scale(TEXT_SCALE)
                            .color(PocketGhostRequest.readoutTextColor())
                            .shadow(Boolean.TRUE)
                            .name("pocket_config_freq_label")
                            .pos(MARGIN, FREQ_ROW_Y)
                            .size(FREQ_LABEL_WIDTH, ROW_HEIGHT));
        final TextFieldWidget field = new TextFieldWidget().value(
            new StringValue.Dynamic(
                () -> String.valueOf(freqSecondsOf(ui)),
                // 提交层（回车 / 失焦）：合法才发码；非法 ⇒ 底值未动，文本由 onUpdate 刷回现值
                input -> {
                    final int seconds = parseFreqSecondsInput(input);
                    if (seconds > 0) {
                        ui.requestChannelFreqSeconds(seconds);
                    }
                }))
            .setMaxLength(3)
            // 打字层只放数字进缓冲（"仅允许正整数"的第一道；域判定在提交层）
            .setPattern(Pattern.compile("[0-9]*"));
        field.pos(MARGIN + FREQ_LABEL_WIDTH + FREQ_BUTTON_GAP, FREQ_ROW_Y)
            .size(FREQ_FIELD_WIDTH, ROW_HEIGHT)
            .name("pocket_config_freq_field");
        field.tooltipDynamic(tooltip -> tooltip.addLine(IKey.lang(READOUT_FREQ_HINT_KEY)));
        field.tooltipAutoUpdate(true);
        panel.child(field);
    }

    /** 载体档上当前的频率秒值（无键 = 默认 5s；旧档位键在场时按档位表映射成秒，读法单源在 PocketConstants）。 */
    private static int freqSecondsOf(NekoPocketPanel ui) {
        final ItemStack carrier = ui.carrierStackLive();
        return PocketConstants.readChannelFreqSeconds(carrier == null ? null : carrier.getTagCompound());
    }

    // ================================================================== ★R96 S7b 磁力面（五面里的整面排版）

    /**
     * 磁力面的整面装配（★R101.3 横盘重排）：自上而下 = 按钮行（三态循环 / 吸取目标 / 清空<b>三钮横排</b>，
     * 用户判「按钮放名单上面」）→ 12×6 横盘 → 计数行。★挂载框与框标题整批退场（用户判内框丑——框住
     * 的东西一面只有一组时框不承载信息），格盘与四控件<b>直接挂 panel</b>。★三态不对称说明行
     * （R99 起的第 5 段）仍迁在面板常驻之外，进三态钮的 tooltip（键不删只改用途，见 {@link #noteKeyOf}）。
     * ★「72 格盘常量只属于磁力面」的结构正身：本方法只经
     * {@link Section#MOUNT_MAGNET} 那一分派支可达，五型里没有第二型的面板画得出格盘。
     */
    private static void mountMagnet(NekoPocketPanel ui, ModularPanel panel, PocketUpgradeType type) {
        panel.child(magnetGrid(ui, type));
        // ★三枚控制钮横排在格盘上方（x 走 magnetButtonXOf 的单源式），计数行贴盘下。
        // ★常驻树形：这四枚件不随名单条数增删（R32/R41b），名单变化只进各件的 IKey.dynamic / 内容层。
        // ★整块只发码或发键，一个字节都不写本地 NBT。
        panel.child(magnetModeButton(ui));
        panel.child(magnetTargetButton(ui));
        panel.child(magnetClearButton(ui));
        panel.child(magnetCountLine(ui));
    }

    /** 横排第 {@code index} 枚控制钮的 x（★单源：左边距 + {@code index} × (钮宽 + 缝)；调用点不写第二次）。 */
    private static int magnetButtonXOf(int index) {
        return MARGIN + index * (MAGNET_BUTTON_WIDTH + MAGNET_BUTTON_GAP);
    }

    /**
     * 72 格名单盘（★R101.3 显示朝向横盘：<b>12 列 × 6 行 = 216×108</b>；数据插入序不变、服务端 index
     * 语义不变——见 {@link #MAGNET_COLUMNS} 的说明）。
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
     * @param type 本面挂的型（★盘面 x 由面板宽居中派生，见 {@link #magnetGridXOf}；y 单源 {@link #MAGNET_GRID_Y}）
     */
    private static IWidget magnetGrid(NekoPocketPanel ui, PocketUpgradeType type) {
        // ★R101.3：去框后盘面直接以<b>面板绝对坐标</b>挂 panel（旧「框内相对坐标 = 面板坐标 − 框原点」
        // 的两段式换算随框一起作废）。
        final ParentWidget<?> grid = new ParentWidget<>().pos(magnetGridXOf(type), MAGNET_GRID_Y)
            .size(MAGNET_GRID_WIDTH, MAGNET_GRID_HEIGHT)
            .name("pocket_magnet_grid");
        for (int index = 0; index < MAGNET_COLUMNS * MAGNET_ROWS; index++) {
            grid.child(magnetCell(ui, index));
        }
        return grid;
    }

    /**
     * 单个名单格：凹槽底（★恒画）+ 物品图标内容层（★本格有条目才画）+ tooltip（★R101 收口：
     * <b>只保留物品显示名</b>，空格不给 tooltip —— 逐条描述族（空格说明 / remove / 格号 / 手势 /
     * 四条拒收支）随任务拍板整批退场，键与两份 lang 同批撤）。
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
        // ★R101：tooltip = 物品显示名一条（键解不出物品时念原始键，不静默）；空格 = 没有 tooltip。
        cell.tooltipDynamic(tooltip -> {
            final String key = cell.entryKey();
            if (key != null && !key.isEmpty()) {
                tooltip.addLine(IKey.str(entryTitleOf(key)));
            }
        });
        cell.tooltipAutoUpdate(true);
        // ★整块（含格盘与三个按钮）在"这一型没固化"时灰显（R31 同一条：灰显不隐藏，画面形状不双分支）
        cell.setEnabledIf(widget -> ui.magnetEditable());
        return cell;
    }

    /** ★三态循环按钮（{@code NONE → WHITELIST → BLACKLIST → NONE}，★只有左键，★客户端零写入；横排第 1 枚）。 */
    private static IWidget magnetModeButton(NekoPocketPanel ui) {
        final ButtonWidget<?> button = new ButtonWidget<>();
        button.pos(magnetButtonXOf(0), MAGNET_BUTTON_ROW_Y)
            .size(MAGNET_BUTTON_WIDTH, MAGNET_BUTTON_HEIGHT)
            .name("pocket_magnet_mode_button")
            .tooltipDynamic(tooltip -> {
                tooltip.addLine(IKey.lang("gtit.pocket.magnet.mode.hint"));
                // ★同一条不对称说明（★R101 起说明键的唯一 tooltip 消费面：本钮。格件 tooltip 已收口）
                tooltip.addLine(IKey.dynamic(() -> StatCollector.translateToLocal(noteKeyOf(ui.magnetMode()))));
            })
            .tooltipAutoUpdate(true)
            .playClickSound(true)
            .onMousePressed(press -> press == 0 && ui.requestMagnetModeCycle());
        applyConfigButtonStyle(
            button,
            IKey.dynamic(() -> StatCollector.translateToLocal(modeLabelKey(ui.magnetMode()))));
        return button;
    }

    /** ★吸取目标两档按钮（{@code POCKET → PLAYER}；横排第 2 枚；★目标执法腿的归属逐字写在 {@code Target} 的注释里）。 */
    private static IWidget magnetTargetButton(NekoPocketPanel ui) {
        final ButtonWidget<?> button = new ButtonWidget<>();
        button.pos(magnetButtonXOf(1), MAGNET_BUTTON_ROW_Y)
            .size(MAGNET_BUTTON_WIDTH, MAGNET_BUTTON_HEIGHT)
            .name("pocket_magnet_target_button")
            .tooltipDynamic(tooltip -> tooltip.addLine(IKey.lang("gtit.pocket.magnet.target.hint")))
            .tooltipAutoUpdate(true)
            .playClickSound(true)
            .onMousePressed(press -> press == 0 && ui.requestMagnetTargetCycle());
        applyConfigButtonStyle(
            button,
            IKey.dynamic(() -> StatCollector.translateToLocal(targetLabelKey(ui.magnetTarget()))));
        return button;
    }

    /** ★清空名单按钮（横排第 3 枚；★只抹条目、不动三态；与"切到无限制"是两件事，见 {@code PocketMagnetFilter} 三态读法）。 */
    private static IWidget magnetClearButton(NekoPocketPanel ui) {
        final ButtonWidget<?> button = new ButtonWidget<>();
        button.pos(magnetButtonXOf(2), MAGNET_BUTTON_ROW_Y)
            .size(MAGNET_BUTTON_WIDTH, MAGNET_BUTTON_HEIGHT)
            .name("pocket_magnet_clear_button")
            .tooltipDynamic(tooltip -> tooltip.addLine(IKey.lang("gtit.pocket.magnet.clear.hint")))
            .tooltipAutoUpdate(true)
            .playClickSound(true)
            .onMousePressed(press -> press == 0 && ui.requestMagnetClear());
        applyConfigButtonStyle(button, IKey.lang("gtit.pocket.magnet.clear"));
        return button;
    }

    /** 名单计数读数（★条数是服务端算好带下来的权威数，不是客户端"非空格"的计数；★R101.3 起贴盘下横贯行带）。 */
    private static IWidget magnetCountLine(NekoPocketPanel ui) {
        return (IWidget) new TextWidget(
            IKey.dynamic(
                () -> String.format(
                    StatCollector.translateToLocal("gtit.pocket.magnet.count"),
                    ui.magnetEntryCount(),
                    PocketConstants.MAGNET_FILTER_SLOTS))).textAlign(Alignment.CenterLeft)
                        .scale(TEXT_SCALE)
                        .color(PocketGhostRequest.readoutTextColor())
                        .shadow(Boolean.TRUE)
                        .name("pocket_magnet_count")
                        .pos(MARGIN, MAGNET_COUNT_Y)
                        .size(MAGNET_COUNT_WIDTH, READOUT_HEIGHT);
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

    // ================================================================== ★R96 S9b 魔法使面（★R101.3 去框：三列模式控件直接放面板）

    /**
     * 魔法使面的整面装配：★R101.3 起<b>没有挂载框</b>——框、框标题与元素容量行整批退场
     * （用户判内框丑；框住的东西一面只有一组时框不承载信息），三列模式控件（按钮行在上、名单行在下）
     * 直接挂面板，x 起点 = 左边距、y = 内容段顶。
     * <p>
     * <b>树形恒定</b>（R32 纪律）：行数 = {@link #modeRowCount()}（派生自
     * {@link PocketConstants#MAGE_MODE_BITS}），★不随"哪条模式开着"变化 ⇒ 同型重开拿到的缓存面板
     * 不会因为状态改而拿到一棵旧树。
     * <p>
     * <b>零同步值、零本地写</b>：与开关行同一条通道（读载体栈 NBT 的 vanilla 镜像、写只发码）。
     * <p>
     * ★形参 {@code type} 是两支挂载装配的同形签名；本支几何全部走 {@link #MAGE_CONTENT_WIDTH} 与
     * {@link #mageContentX()}/{@link #mageContentY()} 这套面板级常数，不再从 {@code type} 取宽 —— 签名由
     * 用例 {@code mage_config_panel_mode_codec_and_commit_leg} 锚住，★不改。
     */
    private static void mountMage(NekoPocketPanel ui, ModularPanel panel, PocketUpgradeType type) {
        mageModeRows(ui, panel);
    }

    /**
     * 魔法使面的模式控件（★R101 横排、★R101.3 去框后直接放面板：<b>按钮行在上、名单文字行在下、按列对齐</b>，
     * 第四枚控件是面板顶上那一行的开关，不在这里重复画）。
     * <p>
     * ★三列布局：每列 = 一枚 {@link #MODE_BUTTON_WIDTH} 宽的按钮（行在 {@link #modeButtonRowY()}）
     * + 列宽 {@link #MODE_COLUMN_WIDTH} 的名单文字盒（行在 {@link #modeLabelRowY()}），按钮与文字
     * 左对齐同一列 x。三列在 232px 的内容段可用宽里等分（3×76 + 2×2 = 232），面板宽 248 不动。
     * <p>
     * ★<b>名单文字只念模式名</b>（旧"模式名：状态"的行首读数收进按钮的动态 overlay）：竖排时代
     * 每行盒 182px 装得下全句，三列横排在 0.8 档装不下（en 最坏 "Essence transmuting：Off" ≈ 111px，
     * 三列 > 232）⇒ 状态读数回到按钮上（"开启 / 关闭"本来就是状态位，竖排时代它与行首读数是同一
     * 事实的两处读点）；名单行只念模式名，信息一项不减、重复读点收掉。
     * <p>
     * ★每列按钮仍各挂同一条 {@link #MODE_HINT_KEY} tooltip（R98 计划提过"合并成一条面板级 tooltip"，
     * <b>本片没做</b>，理由记在这儿而不是留给下一个人重新发现）：MUI2 的 hover 链在
     * {@code ModularGuiContext#getHoveredWidgets} 里遇到第一枚 {@code canHoverThrough()==false} 的件就
     * {@code break}，而 {@code ButtonWidget} 走的是那个默认值 ⇒ 面板级 tooltip 在<b>正好压在按钮上</b>
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
            final int columnX = mageContentX() + columnXOf(row);
            // ★名单行只念模式名（静态文本：键按行定死，不随状态变；状态读数在正上方按钮的 overlay 里）
            panel.child(
                (IWidget) new TextWidget(IKey.lang(labelKey)).textAlign(Alignment.TopLeft)
                    .scale(TEXT_SCALE)
                    .color(PocketGhostRequest.readoutTextColor())
                    .shadow(Boolean.TRUE)
                    .name("pocket_config_mode_label_" + row)
                    .pos(columnX, mageContentY() + modeLabelRowY())
                    .size(MODE_COLUMN_WIDTH, MODE_LABEL_HEIGHT));
            final ButtonWidget<?> button = new ButtonWidget<>();
            button.pos(columnX, mageContentY() + modeButtonRowY())
                .size(MODE_BUTTON_WIDTH, MODE_ROW_HEIGHT)
                .name("pocket_config_mode_switch_" + row)
                .tooltipDynamic(tooltip -> tooltip.addLine(IKey.lang(MODE_HINT_KEY)))
                .tooltipAutoUpdate(true)
                .playClickSound(true)
                // ★同样只有左键生效，且★这里一个字节都不写档（写腿在服务端 commitMode）
                .onMousePressed(
                    press -> press == 0 && ui.requestUpgradeMode(row, nextModeOn(ui.carrierStackLive(), row)));
            // ★按钮上的字 = 当前状态（"开启 / 关闭"，与开关行共用同一对状态键；模式没有"未固化"这一态，
            // 那一判在 commitMode 里拒写并回一条既有回执，不在按钮上骗人）
            applyConfigButtonStyle(
                button,
                IKey.dynamic(
                    () -> StatCollector.translateToLocal(
                        switchLabelKey(modeState(ui.carrierStackLive(), row) ? SwitchState.ON : SwitchState.OFF))));
            panel.child(button);
        }
    }

    /**
     * 第 {@code column} 列的 x（★挂载框内容区内坐标系：列宽等分 + 列间缝；单源，调用点不写第二次）。
     */
    public static int columnXOf(int column) {
        return column * (MODE_COLUMN_WIDTH + MODE_COLUMN_GAP);
    }

    /** 模式<b>按钮行</b>的 y（★挂载框内容区坐标系，标题之下第一层；单源，调用点与对账读同一个数）。 */
    public static int modeButtonRowY() {
        return 0;
    }

    /** 模式<b>名单文字行</b>的 y（= 按钮行 + 一行按钮高 + 层间缝；单源同上）。 */
    public static int modeLabelRowY() {
        return MODE_ROW_HEIGHT + MODE_ROW_GAP;
    }

    /** 模式段内容的 x（★R101.3 去框后 = 面板左边距；旧「框左沿 + 1px」随框作废）。 */
    public static int mageContentX() {
        return MARGIN;
    }

    /** 模式段内容的 y（★内容段顶 = 常量单源 {@link #CONTENT_TOP}；旧「框顶 + 标题高」随框作废）。 */
    public static int mageContentY() {
        return CONTENT_TOP;
    }

    /**
     * 型名键表（★整键字面量表，与 {@code NekoPocketBottomBand#UPGRADE_ITEM_NAME_KEYS} 同一组键的第二个
     * 消费面；下标 = ordinal = 位图位 = 槽号，三个空间同一个数）。
     */
    private static final String[] UPGRADE_NAME_KEYS = { "item.neko_pocket_upgrade_capacity.name",
        "item.neko_pocket_upgrade_stack.name", "item.neko_pocket_upgrade_magnet.name",
        "item.neko_pocket_upgrade_channel_persist.name", "item.neko_pocket_upgrade_mage.name" };
}

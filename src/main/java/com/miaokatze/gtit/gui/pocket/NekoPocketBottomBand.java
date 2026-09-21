package com.miaokatze.gtit.gui.pocket;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.item.ItemStack;

import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.drawable.UITexture;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.ItemDisplayWidget;
import com.cleanroommc.modularui.widgets.SlotGroupWidget;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.cleanroommc.modularui.widgets.slot.ItemSlot;
import com.miaokatze.gtit.client.gui.NekoGuiTextures;
import com.miaokatze.gtit.common.items.pocket.PocketConstants;
import com.miaokatze.gtit.currency.NekoCurrencyRegistrar;
import com.miaokatze.gtit.trade.NekoClientBalances;

/**
 * 底部带 = <b>横向三段</b>（R78① 定形状、★R81④ 定宽度）：
 * <b>左段 112</b>（两条币值条 + 两个通道按钮，纵向 4×18 = 72）
 * ｜ <b>中间【玩家背包 9 列 × 4 行 = 162×72，★与中栏同 x 同宽】</b>
 * ｜ <b>右段 112</b>（★R81④ 由 130 收到 112 = {@code 6×18 + 4}：一格绳缝 + 六格栅格 ⇒
 * 内容区与源质列<b>同 x 同宽</b>；绑按钮 + 绑定格 + 读数 + <b>★R81③ 两条常驻绑定行</b> + 帮助按钮）。
 * <p>
 * <b>几何（R78 定 y / 高，R81④ 定三段宽）</b>：带 {@code y = 6+270+6 = 282}、高 72；背包 4 行 = 72
 * <b>正好等于带高</b> ⇒ 中栏 15 行<b>一行不删</b>、面板总高仍 {@code 282+72+6 = 360} = 1080p /
 * GUI Scale 3 的逻辑高度上限（R75 的硬天花板，R80/R81 一格都没动）。
 * <p>
 * 横向加总（★R81④ 定稿，与 {@link NekoPocketPanel#WIDTH} 同源）：
 * {@code 6 + 112(左) + 162(背包) + 112(右) + 6 = 398 = 面板宽}，左右外边距都是 6、
 * 段间紧挨（{@link #BIND_SLACK} 恒 0 ⇒ 一旦出现非 0 就是"有人又往段间塞空白"），
 * <b>横向与纵向都没有未认领的像素</b>。右段内部再分一次：
 * {@code 4(绳缝) + 108(内容) = 112}，那 108 <b>恰等于源质列宽</b>且左沿 x 相同（装配期断言）。
 * ★三段宽与"背包段与中栏同 x 同宽""右段内容区与源质列同 x 同宽"这三条都由本类的 {@code static}
 * 块现场断言，不靠注释。
 * <p>
 * <b>右段纵向（★R81③：常驻绑定行落在这里）</b>：四行 × 18 = 72 = 带高，★<b>零富余</b>：
 * <ol>
 * <li>行 0（y=0）：绑定按钮（原生 106 宽，在 108 内容区里<b>左右各余 1px</b> 居中）；</li>
 * <li>行 1（y=18）：绑定格 18 + 4 + 读数「已绑定 n / 上限」86；</li>
 * <li>行 2（y=36）：<b>常驻绑定行 0</b>（整幅 108）；</li>
 * <li>行 3（y=54）：帮助按钮 18 + 4 + <b>常驻绑定行 1</b>（86，条目多于常驻行位时这一位让给
 * {@code bind.rows_more}「另有 n 条，悬停可见全部」）。</li>
 * </ol>
 * 常驻行位 = {@link #PERSISTENT_ROWS} = 行数 − 按钮行 − 绑定格行 = {@code 4 − 2} = 2；
 * 修的就是取证记录点名的"常驻行数 = 0"（旧右段只有 1 行读数，条目<b>全</b>在按钮 tooltip 里，
 * 玩家不悬停就读不到"到底绑了几条"）。完整条目与位置五键照旧在 tooltip ⇒ ★常驻面变短不等于删信息。
 * <p>
 * <b>★R78①：玩家背包是"加回来"的，代价是 E4 风险回归（不得静默）</b>。旧实现（R69-D2）预注册
 * 一个<b>空</b> {@code PlayerSlotGroup} 让 MUI2 的默认分支跳过 36 格绑定（见
 * {@link PocketSlots} 类 javadoc），R78 按用户裁决<b>撤销</b>那一招：
 * <ul>
 * <li>背包 36 格<b>真实显示并可交互</b>（那 36 格由框架造，不经本仓槽工厂，见
 * {@link PocketSlots#PLAYER_BACKPACK_SLOTS}）；</li>
 * <li>连带代价 = <b>E4</b>：首开要同步 36 格，且 vanilla {@code Container#detectAndSendChanges}
 * 每 tick 对这 36 格做 {@code ItemStack} 相等比较（<b>含整份 NBT 深比较</b>），
 * 玩家背包内容一变就把整枚口袋连同 184 格一起重发（R53c 点名的包放大面）；</li>
 * <li>这条代价是用户为"要玩家背包"<b>明确换回来</b>的，README 第 5 条与本注释同处点名。</li>
 * </ul>
 * ★本文件<b>不</b>自造那 36 个 {@code ModularSlot}：只把 widget 绑到框架注册的同步键上
 * （{@code "player"} + 槽号，与库内 {@code SlotGroupWidget.playerInventory(…)} 同一绑法），
 * 否则就会出现"两个 handler 指向同一个背包格"的两处真相。
 * <p>
 * <b>★币值区照猫猫机形态（R78④，用户第二张图）</b>：不是"猫猫币：179"那种文本行，而是
 * <b>币物品图标 + 数量 + 两枚快捷小图标</b>。现成实现在
 * {@code client/gui/NekoCoinDisplayV2.java}（售货机 V2 面板用，装配点
 * {@code gui/vm/TradePage.java:1174-1206}），本块<b>借它的控件形态</b>：
 * <ul>
 * <li>图标 = {@code ItemDisplayWidget().item(NekoCurrencyRegistrar.getItemStack(id,1))}
 * （源：{@code NekoCoinDisplayV2.java:107-116}；本处 16×16 以塞进 18 高的币值条）；</li>
 * <li>数量 = {@code IKey.dynamic(...)} + 可读串（源：{@code NekoCoinDisplayV2.java:118-125}
 * 与 {@code :336-344} 的 {@code getReadableString}，10000→10K、1000000→1M）；</li>
 * <li>两枚 12×12 的快捷小图标 = {@code ButtonWidget.size(12)} +
 * {@code disableThemeBackground(true)} + {@code overlay(纹理.asIcon().size(12))}
 * （源：{@code NekoCoinDisplayV2.java:127-158} 的弹出键与 {@code :179-203} 的 ME 导入键）。</li>
 * </ul>
 * ★<b>只借形态、不借语义</b>（用户原话的对应关系由本回执说明）：售货机那两枚是"取币 / 从 ME 存币"，
 * 口袋面板没有这两个动作。这里两枚各对应"花掉这一枚币的那条通道"：
 * <b>第一枚</b> = 用该币激活它自己的通道（猫猫币 → 瞬时、闪烁币 → 短效；与下方那条通道按钮
 * <b>同一条动作码</b>，不新增服务端语义）；<b>第二枚</b> = <b>只读</b>的成本/冷却明细（无写操作、
 * 不新造动作码）。余额读数仍走 {@link NekoClientBalances}（R64c：客户端不得自行读钱包）。
 * <p>
 * <b>★为什么绑定入口必须长这样</b>（R74 拦下的"不可达"）：删掉第四列 = 删掉"已绑定元件"的
 * 唯一可见面<b>与</b>解绑的选中面——旧 {@code ACTION_UNBIND(arg)} 的 arg 来自"列表选中行"，
 * 那套 UI 一旦消失，解绑就没有任何入口（整条需求 5 的一半会<b>静默</b>失效且不报错）。
 * 因此本块按裁定的三条一起落地，缺一条都算没闭合：
 * <ol>
 * <li><b>左键 = 绑定</b>（原语义，读绑定格里的元件）；</li>
 * <li><b>右键 = 解绑最后一条</b>（{@code ACTION_UNBIND_LAST}，不再需要"选中行"这个概念）；</li>
 * <li><b>Shift + 右键 = 清空全部</b>（{@code ACTION_UNBIND_ALL}）。</li>
 * </ol>
 * 三者都<b>只发请求</b>，判定与写档在服务端（R18/R19 + R71 的投递），并且都过
 * {@link NekoPocketPanel#serverGuardOk()} 那一道"一个玩家一枚口袋"的会话守卫。
 * <p>
 * <b>绑定信息的可见面 = 常驻行 + 按钮 tooltip 两处</b>（R74 裁定 tooltip 那份、★R81③ 补常驻那份）：
 * tooltip 每条含维度 / x,y,z / 状态位，上限 {@link #TOOLTIP_ROWS} 条，<b>超出即显式截断提示</b>
 * （{@code bind.truncated}）——
 * {@link PocketConstants#MAX_BOUND_CELLS} 是 64，而 tooltip 不能滚，
 * 所以"不删信息"在这里的唯一合法形态就是"截断必须说出来"，不得静默只显示前 N 条。
 * 常驻行（{@link #PERSISTENT_ROWS} 条）只给<b>短码身份</b>或"另有 n 条"，★它不是 tooltip 的替代，
 * 修复前的问题恰恰是"只有 tooltip"：玩家不悬停就读不到条数，于是把身份门禁那次失败
 * 读成"绑定只能绑定一个"（R81）。
 * 列表行的机读形状（{@code id|status|dim|x|y|z|slot}，{@code ';'} 分隔）与解析器
 * {@link Row} 一起从第四列搬进来 ⇒ 双端仍只有一份编解码。
 * <p>
 * <b>★R78 D-2 的补偿落点 = {@link #helpButton}</b>：左栏撤下来的 7 段说明里，"用法摘要 /
 * 通道成本 / 主手限制 / ghost 用法"这一族需要一个玩家找得到的可见入口，因此在右段行 3 的左位放一个
 * 帮助按钮（tooltip = {@code notesText} 全量 + 绑定/解绑说明）。它不新增任何信息，只是把
 * 原本常驻的文字换成"点得到 / 悬停看得到"。
 * <p>
 * <b>本类不含任何槽工厂</b>：绑定格（1 格，{@code GROUP_BIND}）仍由 {@link PocketSlots} 单点构造；
 * 背包那 36 格由框架构造。这里只放它们各自的 {@code SlotGroupWidget}。
 */
public final class NekoPocketBottomBand {

    /** 带起点 y（= 面板外边距 + 主区高 + 一个外边距）。 */
    public static final int Y = NekoPocketPanel.MARGIN + NekoPocketStorageColumn.HEIGHT + NekoPocketPanel.MARGIN;
    /** R75/R78 带高 72（= 背包 4 行 × 18 ⇒ 纵向零富余，中栏一行都不能再加）。 */
    public static final int HEIGHT = 72;

    /** 背包可视行数（★单源取常量，与框架那 36 格同源；R78①）。 */
    public static final int BACKPACK_ROWS = PocketConstants.PLAYER_BACKPACK_ROWS;
    /** 背包可视列数（= {@code SlotGroup("player_inventory")} 的 rowSize = 9）。 */
    public static final int BACKPACK_COLUMNS = PocketConstants.PLAYER_BACKPACK_COLUMNS;

    /** 左段 x（= 面板外边距）。 */
    public static final int COIN_X = NekoPocketPanel.MARGIN;
    /**
     * ★R80①：左段宽 <b>112</b>（R78 的 100 → 112）。
     * <p>
     * 派生式不是"随手 +12"，而是<b>由"背包段必须与中栏同 x"倒推</b>：背包段左沿被钉成
     * {@link NekoPocketStorageColumn#X}（= 6+108+4 = 118），左段就只能是 {@code 118 − 6 = 112}
     * （R80 后三段之间<b>不再留 4px 列间距</b>，间距并进了段宽 ⇒ 三段恰好铺满"面板宽 − 2×外边距"，
     * ★R81④ 定稿下那个可用宽是 {@code 398 − 12 = 386}，由本类 {@code static} 块与 {@link #BIND_SLACK}
     * 一起对账，不留无主空白）。
     */
    public static final int COIN_WIDTH = NekoPocketStorageColumn.X - COIN_X;
    /** 单条币值条宽（{@code POCKET_C2_coinbar} 的原生宽度，取自契约表；几何不读贴图类）。 */
    private static final int COIN_BAR_WIDTH = PocketGuiTextureContract.widthOf("POCKET_C2_coinbar");
    /** 币值条/按钮的原生高度（{@code POCKET_C2_coinbar} 与 {@code POCKET_C2_btn} 同为 18）。 */
    private static final int COIN_BAR_HEIGHT = PocketGuiTextureContract.heightOf("POCKET_C2_coinbar");
    /** 通道按钮宽（{@code POCKET_C2_btn} 的原生宽度，取自契约表）。 */
    private static final int BUTTON_WIDTH = PocketGuiTextureContract.widthOf("POCKET_C2_btn");
    /** 币值条在左段宽内的左偏移（{@code (段宽 - 88)/2}，纯派生：R80① 后段宽 112 ⇒ 左偏移 12）。 */
    private static final int COIN_BAR_X = (COIN_WIDTH - COIN_BAR_WIDTH) / 2;
    /** ★币图标的边长（VM 用 22，本处必须塞进 18 高的币值条 ⇒ 取 16，其余比例照旧）。 */
    private static final int COIN_ICON_SIZE = 16;
    /** 快捷小图标的边长（与 {@code NekoCoinDisplayV2} 的两枚 12×12 同值）。 */
    private static final int QUICK_ICON_SIZE = 12;

    /**
     * 中间段 x（★R80① 的<b>硬判据</b>之一：与中栏同 x。
     * 它由 {@link #COIN_X} + {@link #COIN_WIDTH} 得出，而左段宽本身就是"中栏左沿 − 外边距"的倒推，
     * 故同 x 是<b>构造保证</b>的；{@code BACKPACK_X == NekoPocketStorageColumn.X} 另有装配期断言
     * 与 {@code NekoPocketModelTest} 双重机检，不靠这句注释）。
     */
    public static final int BACKPACK_X = COIN_X + COIN_WIDTH;
    /**
     * 背包宽（★本段的权威是"框架那 36 格的列数 × 栅格"，<b>不是</b>抄中栏宽：
     * {@code SlotGroup("player_inventory")} 的 rowSize 固定是 9，中栏列数再变也不该改背包宽。
     * 与 {@link NekoPocketStorageColumn#WIDTH} 的相等关系是<b>判据</b>而非定义，见 {@code static} 块）。
     */
    public static final int BACKPACK_WIDTH = BACKPACK_COLUMNS * NekoPocketPanel.GRID;
    /** 背包高（4 行 × 18 = 72 = 带高，R78① 的"正好等于"；R80 未动）。 */
    public static final int BACKPACK_HEIGHT = BACKPACK_ROWS * NekoPocketPanel.GRID;
    /**
     * ★R81④：右段宽 <b>112</b>（R80① 的 130 → 112，三段定稿 {@code 左 112 | 背包 162 | 右 112}）。
     * <p>
     * 面板宽收到 398（{@link NekoPocketPanel#WIDTH} = 主区实占）后，右段<b>只能</b>是
     * {@code 398 − 2×6 − 112 − 162 = 112}；这一式子由 {@link #BIND_SLACK} 承担，本值写成
     * <b>具名加算式</b> {@code 6 × 18 + 4}（六格栅格 + 一条列间距宽的绳缝）并在 {@code static}
     * 块里与"面板宽减前两段"对账 ⇒ 两条式子必须给出同一个 112，否则当场红。
     * 旧注释里"写定稿字面量才留得住判据力"的顾虑由 {@link #BIND_CONTENT_WIDTH} 那条
     * "与源质列同 x 同宽"的断言接管：★不再靠一个字面量看账。
     */
    public static final int BIND_WIDTH = 6 * NekoPocketPanel.GRID + NekoPocketPanel.COLUMN_GAP;
    /**
     * R78 时代"三段之间各留 4px 列间距 ⇒ 余 14px 塞在背包与右段之间"的余量（R80① 后必须为 0）。
     * <p>
     * ★R81④ 后它<b>仍然恒为 0</b>（三段紧挨），保留这个具名量的理由是：右段宽现在写成
     * {@code 6×18+4} 的加算式，一旦面板宽或前两段被改而右段没跟着改，"面板宽 − 三段宽"这笔账
     * 就从这里冒出来 ⇒ 装配期断言当场红，不留无主空白。
     */
    public static final int BIND_SLACK = NekoPocketPanel.WIDTH - 2 * NekoPocketPanel.MARGIN
        - COIN_WIDTH
        - BACKPACK_WIDTH
        - BIND_WIDTH;
    /** 右段 x（左段右边 + 背包段宽 + 余量 ⇒ R81④ 定稿下 = {@code 6+112+162 = 280}）。 */
    public static final int BIND_X = COIN_X + COIN_WIDTH + BACKPACK_WIDTH + BIND_SLACK;
    /**
     * ★R81④：右段内部的<b>绳缝位</b>宽（= 一条 {@link NekoPocketPanel#COLUMN_GAP}）。
     * <p>
     * R80 把三段改紧挨之后，装饰层的 rope 虚线只能画在 {@code BIND_X − 4}，也就是<b>压在背包段
     * 最后一列的右 4px 上</b>（MUI2 按 child 顺序绘制，装饰是第一棵 ⇒ 不遮交互，但那 4px 从此
     * 同时属于两个功能区）。现在把这条缝<b>请进右段自己的账</b>：右段 = {@code 4(绳) + 108(内容)}，
     * 绳缝归 {@code NekoPocketDecoration} 画，内容区从 {@link #BIND_CONTENT_X} 起。
     */
    public static final int BIND_ROPE_WIDTH = NekoPocketPanel.COLUMN_GAP;
    /** 右段内容区 x（段内局部，= 绳缝右边界 ⇒ 全局 x = {@code BIND_X + 4} = 284）。 */
    public static final int BIND_CONTENT_X = BIND_ROPE_WIDTH;
    /**
     * 右段内容区宽（= {@code 112 − 4} = <b>108</b>）。
     * <p>
     * ★108 同时也是源质列宽（{@link NekoPocketEssenceColumn#WIDTH}），且内容区左沿
     * {@code BIND_X + BIND_CONTENT_X = 284} 恰等于 {@link NekoPocketEssenceColumn#X} ⇒
     * <b>绑定内容与它上面那一列源质盘同 x 同宽</b>（与 R80① 给背包段立的同一条硬判据，只是换到右段；
     * 见 {@code static} 块，这条不靠注释）。
     */
    public static final int BIND_CONTENT_WIDTH = BIND_WIDTH - BIND_CONTENT_X;
    /** 绑定按钮的可视宽（= {@code POCKET_C2_bindbtn} 原生宽 106；★比内容区 108 窄 2px）。 */
    private static final int BIND_BUTTON_WIDTH = PocketGuiTextureContract.widthOf("POCKET_C2_bindbtn");
    /**
     * 绑定按钮 x（段内局部）：在 {@link #BIND_CONTENT_WIDTH} 的内容区里<b>居中</b>
     * （{@code 4 + (108 − 106)/2 = 5}，左右各余 1px，与左段币值条的居中口径同形；
     * ★那 1px + 1px 由 {@code static} 块断言"两边相等"，不是随手偏移）。
     */
    public static final int BIND_BUTTON_X = BIND_CONTENT_X + (BIND_CONTENT_WIDTH - BIND_BUTTON_WIDTH) / 2;
    /** 右段纵向<b>行位</b>数（★4 × 18 = 72 = 带高，零富余；行位从哪几行看 {@code static} 块的加总）。 */
    public static final int BIND_ROWS = HEIGHT / NekoPocketPanel.GRID;
    /**
     * ★R81③：<b>常驻</b>绑定行的行位数 = {@code BIND_ROWS − 2} = <b>2</b>（减掉的是"按钮行"与
     * "绑定格 + 读数的行"）。修复前这个数是 <b>0</b> —— 条目全在按钮 tooltip 里，玩家不悬停就
     * 读不到"到底绑了几条"，与身份门禁叠成就成了"绑定只能绑定一个"的观感（取证记录 §3 的"面缺失"）。
     * <p>
     * 多于这 {@code 2} 位的部分：最后一位让给 {@code bind.rows_more} 的"另有 n 条，悬停可见"提示，
     * 完整条目与位置五键仍在 tooltip（上限 {@link #TOOLTIP_ROWS}）——★常驻面变短不等于删信息。
     */
    public static final int PERSISTENT_ROWS = BIND_ROWS - 2;
    /** 绑定格 x（段内局部，★R81④ 起与内容区左沿对齐 = 绳缝右侧第一格；★R81③ 起 public 供回归套件核加总）。 */
    public static final int BIND_SLOT_X = BIND_CONTENT_X;
    /** 绑定格 y（★行 1：y = 18；行 0 整行给绑定按钮，两者上下相邻不重叠）。 */
    public static final int BIND_SLOT_Y = NekoPocketPanel.GRID;
    /** 读数 / 常驻绑定行（与 18 宽控件并排时）的 x（= {@code 4 + 18 + 4} = 26）。 */
    public static final int BIND_TEXT_X = BIND_CONTENT_X + NekoPocketPanel.GRID + NekoPocketPanel.COLUMN_GAP;
    /** 与控件并排时的那段文字宽（= {@code 112 − 26} = <b>86</b>）。 */
    public static final int BIND_TEXT_WIDTH = BIND_WIDTH - BIND_TEXT_X;
    /** 整幅常驻绑定行的宽（行 2 = 内容区全幅 108，★只有它享受不到 18+4 的那次让位）。 */
    public static final int BIND_ROW_WIDTH = BIND_CONTENT_WIDTH;
    /**
     * 帮助按钮 x（段内局部，与绑定格同列 ⇒ 纵向读成一列控件；★R81③ 起 public 供回归套件核加总）。
     */
    public static final int BIND_HELP_X = BIND_CONTENT_X;
    /** 帮助按钮 y（★行 3 的左位，右边那 86px 给常驻绑定行 1）。 */
    public static final int BIND_HELP_Y = 3 * NekoPocketPanel.GRID;

    /** 绑定条目的 tooltip 最多列几条（超出必须显式提示，见类 javadoc）。 */
    public static final int TOOLTIP_ROWS = 10;

    /** 第 {@code slot} 个常驻绑定行的 y（行 2 起，逐行往下）。 */
    public static int persistentRowY(int slot) {
        return (2 + slot) * NekoPocketPanel.GRID;
    }

    /**
     * 框架给的玩家背包同步键前缀（★不是本仓自造的键）。
     * <p>
     * 依据（dev jar 字节码，两处互相印证）：{@code ISyncRegistrar#bindPlayerInventory} 用
     * {@code itemSlot("player", i, slot)} 逐格注册（i = 0…35），而库内现成的
     * {@code SlotGroupWidget#playerInventory} 助手用 {@code ItemSlot.syncHandler("player", i)}
     * 绑同一批 handler ⇒ 本仓自绘的背包格必须走<b>同一个键式样</b>，否则会出现
     * "格画出来了但读不到内容"的哑洞。
     */
    private static final String PLAYER_SLOT_SYNC_NAME = "player";

    /** 绑定格的布局字面量（1 格；与中栏/流体/蒸馏同一矩阵机制，同步键独立避免 id 覆盖）。 */
    private static final String[] BIND_MATRIX = { "B" };

    /**
     * 背包的布局字面量：{@code 4 行 × 9 列}（R78①）。★整块<b>只用一个</b>布局字符 {@code 'P'}
     * （两个字符会把索引空间劈成两条重叠的 0…n，同 {@code NekoPocketLeftColumn} 记实的那条库行为）。
     */
    private static final String[] BACKPACK_MATRIX = { "PPPPPPPPP", "PPPPPPPPP", "PPPPPPPPP", "PPPPPPPPP" };

    private NekoPocketBottomBand() {}

    static {
        if (layoutSlotCount() != PocketInventory.BIND_SLOTS) {
            throw new IllegalStateException("[pocket] 绑定格矩阵产出 " + layoutSlotCount() + " 格，与 handler 格数不符");
        }
        if (backpackLayoutSlotCount() != PocketSlots.PLAYER_BACKPACK_SLOTS) {
            throw new IllegalStateException(
                "[pocket] 背包矩阵产出 " + backpackLayoutSlotCount()
                    + " 格，与框架绑定的 "
                    + PocketSlots.PLAYER_BACKPACK_SLOTS
                    + " 格不符（★不等就会有一部分背包格只存在于 Container 而看不见）");
        }
        if (BACKPACK_HEIGHT != HEIGHT) {
            throw new IllegalStateException("[pocket] 背包高度不等于带高（R78① 的 4×18 = 72 被破坏）");
        }
        // ★R80① 新增的两条硬判据：背包段与中栏**同 x 同宽**（用户在 §1.1 点名"这是本轮新增的硬判据"）。
        // 同 x 由 COIN_WIDTH 的倒推式给出、同宽由两侧各自的权威算出 ⇒ 这里只是把它们变成会炸的断言：
        // 不等 = 中栏与背包在面板上读成"两块没对齐的格子"，而 MUI2 两端都不会报任何错。
        if (BACKPACK_X != NekoPocketStorageColumn.X) {
            throw new IllegalStateException("[pocket] 背包段与中栏不同 x: " + BACKPACK_X + " != " + NekoPocketStorageColumn.X);
        }
        if (BACKPACK_WIDTH != NekoPocketStorageColumn.WIDTH) {
            throw new IllegalStateException(
                "[pocket] 背包段与中栏不同宽: " + BACKPACK_WIDTH + " != " + NekoPocketStorageColumn.WIDTH);
        }
        // ★R81④ 新增的两条硬判据：面板宽 = 主区实占 ⇒ 三段加总必须跟着变 112|162|112。
        // 右段宽写成加算式（6×18+4），这里把它与"面板宽 − 前两段"对账 ⇒ 两条式子给出不同的数就红。
        if (BIND_WIDTH != 6 * NekoPocketPanel.GRID + NekoPocketPanel.COLUMN_GAP) {
            throw new IllegalStateException("[pocket] 右段宽不是 6×18+4=112（★一格绳缝 + 六格栅格）: " + BIND_WIDTH);
        }
        if (COIN_WIDTH + BACKPACK_WIDTH + BIND_WIDTH + 2 * NekoPocketPanel.MARGIN != NekoPocketPanel.WIDTH) {
            throw new IllegalStateException(
                "[pocket] 底部带三段与面板宽不闭合: 112+162+112+12 != " + NekoPocketPanel.WIDTH + "（R81④ 定稿 398）");
        }
        // ★R80①：三段之间不再塞列间距 ⇒ 余量必须恰为 0（不是 0 就是"有一块没人认领的空白"）。
        if (BIND_SLACK != 0) {
            throw new IllegalStateException(
                "[pocket] 底部带三段没铺满可用宽，余量 " + BIND_SLACK + "（R81④ 定稿：112+162+112 = 386 = 398-12）");
        }
        if (BIND_X + BIND_WIDTH != NekoPocketPanel.WIDTH - NekoPocketPanel.MARGIN) {
            throw new IllegalStateException(
                "[pocket] 底部带横向不闭合: " + (BIND_X + BIND_WIDTH) + " != " + (NekoPocketPanel.WIDTH - 6));
        }
        // ★R81④：右段内容区必须与源质列**同 x 同宽**（同一条纪律从背包段搬到右段；
        // 同时绳缝位被请进右段自己的账 ⇒ 装饰层的 rope 不再压背包最后一列）。
        if (BIND_CONTENT_X + BIND_CONTENT_WIDTH != BIND_WIDTH) {
            throw new IllegalStateException("[pocket] 右段绳缝 + 内容不等于段宽: " + BIND_CONTENT_X + "+" + BIND_CONTENT_WIDTH);
        }
        if (BIND_X + BIND_CONTENT_X != NekoPocketEssenceColumn.X) {
            throw new IllegalStateException(
                "[pocket] 右段内容区与源质列不同 x: " + (BIND_X + BIND_CONTENT_X) + " != " + NekoPocketEssenceColumn.X);
        }
        if (BIND_CONTENT_WIDTH != NekoPocketEssenceColumn.WIDTH) {
            throw new IllegalStateException(
                "[pocket] 右段内容区与源质列不同宽: " + BIND_CONTENT_WIDTH + " != " + NekoPocketEssenceColumn.WIDTH);
        }
        // ★R81③ 纵向加总：右段四行 × 18 必须<b>恰好</b>等于带高（零富余 ⇒ 不会出现无主空白，
        // 也不会把绑定行挤出带子）。
        if (BIND_ROWS * NekoPocketPanel.GRID != HEIGHT) {
            throw new IllegalStateException(
                "[pocket] 右段纵向加总不闭合: " + BIND_ROWS + "×18 != 带高 " + HEIGHT + "（R81③：四行零富余）");
        }
        if (PERSISTENT_ROWS != BIND_ROWS - 2 || PERSISTENT_ROWS < 1) {
            throw new IllegalStateException(
                "[pocket] 常驻绑定行位数 = " + PERSISTENT_ROWS + "（★必须 = 行数 − 按钮行 − 绑定格行 且 ≥ 1，" + "取证记录 §3 点名的缺陷就是它等于 0）");
        }
        if (persistentRowY(PERSISTENT_ROWS - 1) + NekoPocketPanel.GRID != HEIGHT) {
            throw new IllegalStateException(
                "[pocket] 最后一条常驻绑定行的下沿不等于带高（★纵向出现无主空白或越界）: " + persistentRowY(PERSISTENT_ROWS - 1));
        }
        // 「按钮行与绑定格行不重叠」（R81 取证 §5）此前只靠"行位都是 18 的整数倍"隐式成立；
        // 控件高来自材质契约（coinbar 88×18），那张图一旦改高四行就会互相压上一格，而且不抛错、不打日志。
        if (COIN_BAR_HEIGHT != NekoPocketPanel.GRID) {
            throw new IllegalStateException(
                "[pocket] 控件高 " + COIN_BAR_HEIGHT + " != 行高 " + NekoPocketPanel.GRID + "（★右段四行会重叠）");
        }
        // 三条横向加总：每一行的像素都必须有归属（按钮行 / 控件+文字行 / 整幅行）
        if (BIND_BUTTON_X - BIND_CONTENT_X != BIND_CONTENT_X + BIND_CONTENT_WIDTH - BIND_BUTTON_X - BIND_BUTTON_WIDTH) {
            throw new IllegalStateException("[pocket] 绑定按钮在内容区里不是居中（左右余量不等）: 左 " + (BIND_BUTTON_X - BIND_CONTENT_X));
        }
        if (BIND_TEXT_X + BIND_TEXT_WIDTH != BIND_WIDTH) {
            throw new IllegalStateException(
                "[pocket] 「18 控件 + 4 + 文字」三段不等于右段宽: " + BIND_TEXT_X + "+" + BIND_TEXT_WIDTH);
        }
        if (BIND_ROW_WIDTH + BIND_CONTENT_X != BIND_WIDTH) {
            throw new IllegalStateException("[pocket] 整幅常驻绑定行没有铺到段宽右沿: " + BIND_ROW_WIDTH);
        }
    }

    /**
     * 绑定格布局字符的出现总数（机检 + 装配期断言的输入；理由同各列类的同名方法）。
     */
    public static int layoutSlotCount() {
        int total = 0;
        for (String row : BIND_MATRIX) {
            for (int index = 0; index < row.length(); index++) {
                if (row.charAt(index) == 'B') {
                    total++;
                }
            }
        }
        return total;
    }

    /** 背包矩阵产出的格数（机检用；★必须等于框架注册的 36）。 */
    public static int backpackLayoutSlotCount() {
        int total = 0;
        for (String row : BACKPACK_MATRIX) {
            for (int index = 0; index < row.length(); index++) {
                if (row.charAt(index) == 'P') {
                    total++;
                }
            }
        }
        return total;
    }

    /**
     * 布局序号 → 框架那 36 格里的<b>槽号</b>（★唯一的背包格序映射，双端同一段代码）。
     * <p>
     * 视觉行 0…2 放主背包第 9…35 格、视觉行 3（最下面一行）放<b>快捷栏</b> 0…8 格 ——
     * 与原版箱/熔炉界面里"快捷栏在最下行"的读法一致（库内 {@code SlotGroupWidget#playerInventory}
     * 也是把 0…8 画在最底那一行）。行主序 {@code 0…26} 先铺完上面三行，因此
     * {@code layout < 27 → layout + 9}，否则 {@code layout - 27}。
     */
    public static int backpackPlayerSlotAt(int layoutIndex) {
        final int mainRows = BACKPACK_ROWS - 1;
        return layoutIndex < BACKPACK_COLUMNS * mainRows ? layoutIndex + BACKPACK_COLUMNS
            : layoutIndex - BACKPACK_COLUMNS * mainRows;
    }

    /** 装配底部带（三块各一次 {@code excludeAreaInRecipeViewer()}，R36/§16 每块一处）。 */
    public static List<ParentWidget<?>> build(NekoPocketPanel ui) {
        final List<ParentWidget<?>> blocks = new ArrayList<>(3);
        blocks.add(coinBlock(ui));
        blocks.add(backpackBlock(ui));
        blocks.add(bindBlock(ui));
        return blocks;
    }

    // ------------------------------------------------------------------ 左段：币值（猫猫机形态）+ 两个通道按钮

    /**
     * 左段（112×72）：两条币值条（各 88×18）+ 两个通道按钮（各 88×18），纵向 4×18 = 72。
     * <p>
     * R78① 的"收窄"：旧口径这一段是 180 宽（两条币值条并排 + 两个通道按钮并排 + 一段说明文字），
     * 现在两条并排改纵向，且★说明文字（{@code note.channel}）撤进各自 tooltip（R74② / R78 D-2）。
     */
    private static ParentWidget<?> coinBlock(NekoPocketPanel ui) {
        return new ParentWidget<>().pos(COIN_X, Y)
            .size(COIN_WIDTH, HEIGHT)
            .name("pocket_coin_block")
            .child(coinRow(ui, NekoCurrencyRegistrar.NEKO_ID, 0, "pocket_balance_neko"))
            .child(coinRow(ui, NekoCurrencyRegistrar.SHIMMERING_NEKO_ID, 1, "pocket_balance_shimmering"))
            .child(channelButtons(ui));
    }

    /**
     * 一行币值（猫猫机形态：图标 + 数量 + 两枚快捷小图标）。
     *
     * @param currencyId 币种（本仓的两种：猫猫币 / 闪烁猫猫币）
     * @param row        第几行（0 = 猫猫币 ⇒ 对应瞬时通道；1 = 闪烁币 ⇒ 对应短效通道）
     */
    private static IWidget coinRow(NekoPocketPanel ui, String currencyId, int row, String name) {
        final String displayName = NekoCurrencyRegistrar.getDisplayName(currencyId);
        final ItemStack iconStack = currencyIcon(currencyId);
        final IKey balanceKey = IKey.lang(
            row == 0 ? "gtit.pocket.balance.neko" : "gtit.pocket.balance.shimmering",
            () -> new Object[] { NekoClientBalances.getBalance(currencyId) });
        final NekoPocketPanel.ChannelRequest channel = row == 0 ? NekoPocketPanel.ChannelRequest.BURST
            : NekoPocketPanel.ChannelRequest.SHORT;
        final IKey costKey = IKey.lang(
            row == 0 ? "gtit.pocket.channel.instant" : "gtit.pocket.channel.timed",
            row == 0 ? new Object[] { PocketConstants.BURST_COST_NEKO }
                : new Object[] { PocketConstants.SHORT_COST_SHIMMERING_NEKO, PocketConstants.SHORT_CHANNEL_SECONDS });
        final ParentWidget<?> bar = new ParentWidget<>().pos(COIN_BAR_X, row * COIN_BAR_HEIGHT)
            .size(COIN_BAR_WIDTH, COIN_BAR_HEIGHT)
            .name(name)
            .background(PocketGuiTextures.COIN_BAR)
            // 图标（形态源 NekoCoinDisplayV2.java:107-116，尺寸按 18 高的条收小）
            .child(
                new ItemDisplayWidget().item(iconStack)
                    .displayAmount(false)
                    .disableThemeBackground(true)
                    .pos(1, 1)
                    .size(COIN_ICON_SIZE, COIN_ICON_SIZE)
                    .name(name + "_icon"))
            // 数量（形态源 NekoCoinDisplayV2.java:118-125 + :336-344 的可读串）
            .child(
                (IWidget) new TextWidget(IKey.dynamic(() -> readableAmount(NekoClientBalances.getBalance(currencyId))))
                    .textAlign(Alignment.CenterLeft)
                    .scale(0.5f)
                    .pos(COIN_ICON_SIZE + 3, 0)
                    .size(30, COIN_BAR_HEIGHT))
            // 第一枚快捷键：借 NekoCoinDisplayV2.java:127-158 的形态；语义 = 用该币激活它自己的通道
            .child(
                quickIcon(
                    NekoGuiTextures.EJECT_COINS,
                    COIN_ICON_SIZE + 3 + 32,
                    row,
                    name + "_quick_channel",
                    costKey,
                    () -> ui.requestChannel(channel)))
            // 第二枚快捷键：借 NekoCoinDisplayV2.java:179-203 的形态；★只读明细，不借"存币"语义
            .child(
                quickIcon(
                    NekoGuiTextures.WALLET_PERSONAL,
                    COIN_ICON_SIZE + 3 + 32 + QUICK_ICON_SIZE + 2,
                    row,
                    name + "_quick_info",
                    balanceKey,
                    null));
        bar.tooltip(tooltip -> tooltip.addLine(balanceKey));
        return bar;
    }

    /**
     * 一枚 12×12 的快捷小图标（形态照 {@code NekoCoinDisplayV2}：主题底关掉、只画纹理图标）。
     *
     * @param action 点击动作；{@code null} = <b>只读</b>件（只带 tooltip，不发任何请求）
     */
    private static IWidget quickIcon(UITexture texture, int x, int row, String name, IKey tip, Runnable action) {
        final ButtonWidget<?> button = new ButtonWidget<>().pos(x, 3)
            .size(QUICK_ICON_SIZE, QUICK_ICON_SIZE)
            .name(name)
            .disableThemeBackground(true)
            .disableHoverThemeBackground(true)
            .overlay(
                new IDrawable[] { texture.asIcon()
                    .size(QUICK_ICON_SIZE) })
            .playClickSound(false)
            .tooltip(tooltip -> tooltip.addLine(tip));
        if (action == null) {
            // ★只读件：把点击吃掉但不发任何请求（返回 true = SUCCESS，点击不会穿到下面的币值条）
            return button.onMousePressed(mouseButton -> true);
        }
        return button.onMousePressed(mouseButton -> {
            if (mouseButton != 0) {
                return true;
            }
            action.run();
            return true;
        });
    }

    /**
     * 需求 3 的两个通道按钮（R78① 后纵向排在左段下半）：各 {@code POCKET_C2_btn} 88×18，
     * 起点 {@code y = 2×18 = 36} 与 {@code y = 54}。
     * <p>
     * 按钮<b>只发"请求激活"</b>（R64c 末段 + R39b）：动作码各自独立（{@code BURST} / {@code SHORT}），
     * 扣费与推送/拉取模式判定都在服务端，客户端<b>不得</b>按 ghost 表自行推断。
     * 完整文案（含 {@code -%d} 成本占位，数字由 {@code PocketConstants} 填入，R58b/契约 §7 第 5 条）
     * 同时进缩略标签与 tooltip（R36：宽度不够就加 tooltip，不删信息）。
     */
    private static IWidget channelButtons(NekoPocketPanel ui) {
        final IKey instant = IKey
            .lang("gtit.pocket.channel.instant", () -> new Object[] { PocketConstants.BURST_COST_NEKO });
        final IKey timed = IKey.lang(
            "gtit.pocket.channel.timed",
            () -> new Object[] { PocketConstants.SHORT_COST_SHIMMERING_NEKO, PocketConstants.SHORT_CHANNEL_SECONDS });
        return new ParentWidget<>().pos(0, 2 * COIN_BAR_HEIGHT)
            .size(COIN_WIDTH, 2 * COIN_BAR_HEIGHT)
            .name("pocket_channel_buttons")
            .child(
                new ButtonWidget<>().pos(COIN_BAR_X, 0)
                    .size(BUTTON_WIDTH, COIN_BAR_HEIGHT)
                    .name("pocket_button_instant")
                    .background(PocketGuiTextures.BUTTON)
                    .hoverBackground(PocketGuiTextures.BUTTON_PRESSED)
                    .child(
                        (IWidget) new TextWidget(instant).scale(0.5f)
                            .textAlign(Alignment.Center))
                    .tooltip(tooltip -> tooltip.addLine(instant))
                    .onMousePressed(button -> button == 0 && ui.requestChannel(NekoPocketPanel.ChannelRequest.BURST)))
            .child(
                new ButtonWidget<>().pos(COIN_BAR_X, COIN_BAR_HEIGHT)
                    .size(BUTTON_WIDTH, COIN_BAR_HEIGHT)
                    .name("pocket_button_timed")
                    .background(PocketGuiTextures.BUTTON)
                    .hoverBackground(PocketGuiTextures.BUTTON_PRESSED)
                    .child(
                        (IWidget) new TextWidget(timed).scale(0.5f)
                            .textAlign(Alignment.Center))
                    .tooltip(tooltip -> tooltip.addLine(timed))
                    .onMousePressed(button -> button == 0 && ui.requestChannel(NekoPocketPanel.ChannelRequest.SHORT)));
    }

    /**
     * 币的物品栈（形态源 {@code NekoCoinDisplayV2.java:109-113}，含同一份"未注册货币"兜底）。
     * <p>
     * ★兜底与那边逐字同形（木炭 meta 1）：这不是抄来好看的——币种未注册时 {@code getItemStack}
     * 返回 null，图标位留空会让玩家以为"这一行坏了"，占位图标至少说明"这里该有一枚币"。
     */
    private static ItemStack currencyIcon(String currencyId) {
        final ItemStack stack = NekoCurrencyRegistrar.getItemStack(currencyId, 1);
        return stack != null ? stack : new ItemStack(net.minecraft.init.Items.coal, 1, 1);
    }

    /**
     * 可读数量串（★形态照 {@code NekoCoinDisplayV2.java:336-344} 的 {@code getReadableString}：
     * 那是"为什么 179 显示成 179 而不是 179.0 / 1,000 显示成 1K"的现成答案）。
     * <p>
     * 那边是 private static ⇒ 本仓不跨包改它（禁改 {@code gui/vm/**} 与 {@code client/gui/**}
     * 不在本包边界内），故在这里重述同一条算式；改阈值时两处都要改，已在本注释点名同源。
     */
    static String readableAmount(int amount) {
        if (amount < 10_000) {
            return Integer.toString(amount);
        }
        if (amount < 1_000_000) {
            return amount / 1_000 + "K";
        }
        return amount / 1_000_000 + "M";
    }

    // ------------------------------------------------------------------ 中间段：玩家背包 9×4（R78① 回归）

    /**
     * 玩家背包块（162×72，正好填满带的中间段）。
     * <p>
     * ★这里的每个 {@link ItemSlot} <b>不</b>经 {@link PocketSlots} 工厂、也<b>不</b>自造
     * {@code ModularSlot}：只用 {@code syncHandler(PLAYER_SLOT_SYNC_NAME, 槽号)} 绑到框架
     * {@code bindPlayerInventory} 注册好的那 36 个 handler 上（同一绑法见库内
     * {@code SlotGroupWidget#playerInventory}）。因此：
     * <ul>
     * <li>不调 {@code .synced(...)}：那 36 格有自己的同步通道（vanilla 槽包），组级同步键反而会把
     * 它们二次注册；</li>
     * <li>不调 {@code .slotGroup(...)}：那 36 格已经属于框架的 {@code player_inventory} 组
     * （rowSize 9、{@code allowShiftTransfer=true}），重新贴组名会让 shift 落点算错；</li>
     * <li>布局序号→槽号的映射只有 {@link #backpackPlayerSlotAt(int)} 这一处（快捷栏画在最下行）。</li>
     * </ul>
     */
    private static ParentWidget<?> backpackBlock(NekoPocketPanel ui) {
        final SlotGroupWidget group = SlotGroupWidget.builder()
            .matrix(BACKPACK_MATRIX)
            .key(
                'P',
                index -> new ItemSlot().syncHandler(PLAYER_SLOT_SYNC_NAME, backpackPlayerSlotAt(index))
                    .name("pocket_backpack_" + index)
                    .background(PocketGuiTextures.SLOT))
            .build();
        group.pos(0, 0);
        final ParentWidget<?> root = new ParentWidget<>().pos(BACKPACK_X, Y)
            .size(BACKPACK_WIDTH, BACKPACK_HEIGHT)
            .name("pocket_backpack_block")
            .child(group)
            .tooltip(tooltip -> tooltip.addLine(IKey.lang("gtit.pocket.backpack.note")));
        return root.excludeAreaInRecipeViewer();
    }

    // ------------------------------------------------------------------ 右段：绑定 / 解绑 + 帮助

    /**
     * ★R81④ 后右段 = <b>112×72 = 绳缝 4 + 内容 108</b>，纵向四行 × 18 <b>零富余</b>：
     * <ol>
     * <li>行 0：绑定按钮（左键绑定 / 右键解绑末条 / Shift 右键清空，R74 三条语义一字未改）；</li>
     * <li>行 1：绑定格 18 + 4 + 读数「已绑定 n / 上限」；</li>
     * <li>行 2：<b>常驻绑定行 0</b>（整幅 108，★修复前这一位根本不存在）；</li>
     * <li>行 3：帮助按钮 18 + 4 + <b>常驻绑定行 1</b>（86；条目多于常驻行位时这一位是
     * {@code bind.rows_more}「另有 n 条，悬停可见全部」）。</li>
     * </ol>
     * 按钮 y0..18 与绑定格 y18..36 <b>上下相邻但不重叠</b>（R81 取证 §5 用它排除"点击穿透到绑定槽"，
     * 现在仍然成立 ⇒ 那条判据不许被后续改动破坏）。
     */
    private static ParentWidget<?> bindBlock(NekoPocketPanel ui) {
        final SlotGroupWidget bindGroup = SlotGroupWidget.builder()
            .matrix(BIND_MATRIX)
            .key('B', index -> {
                final ItemSlot slot = new ItemSlot();
                slot.slot(
                    ui.slots()
                        .bind(ui.inventory()));
                slot.name("bind_" + index);
                slot.background(PocketGuiTextures.SLOT);
                slot.tooltip(tooltip -> tooltip.addLine(IKey.lang("gtit.pocket.bind.slot_hint")));
                return slot;
            })
            .synced(PocketSlots.SYNC_BIND)
            .slotGroup(PocketSlots.GROUP_BIND)
            .build();
        bindGroup.pos(BIND_SLOT_X, BIND_SLOT_Y);

        final IKey summary = IKey.dynamic(ui::bindSummaryText);
        final ParentWidget<?> root = new ParentWidget<>().pos(BIND_X, Y)
            .size(BIND_WIDTH, HEIGHT)
            .name("pocket_bind_block")
            .child(
                new ButtonWidget<>().pos(BIND_BUTTON_X, 0)
                    .size(BIND_BUTTON_WIDTH, COIN_BAR_HEIGHT)
                    .name("pocket_bind_button")
                    .background(PocketGuiTextures.BIND_BUTTON)
                    .child(
                        (IWidget) new TextWidget(IKey.lang("gtit.pocket.bind.button")).scale(0.5f)
                            .textAlign(Alignment.Center))
                    // ★绑定信息的完整可见面（R74）：全部条目 + 位置五键 + 超出的显式截断提示。
                    // R81③ 之后它不再是<b>唯一</b>可见面（右段多了常驻行），但仍是<b>全</b>信息面 ⇒ 不得删。
                    .tooltip(tooltip -> tooltip.addLine(IKey.dynamic(ui::bindTooltipText)))
                    // 左键绑定；右键解绑最后一条；Shift+右键清空全部（R74 裁定，语义与 onServerAction 同步改）
                    .onMousePressed(button -> ui.dispatchBindButtonClick(button)))
            .child(bindGroup)
            .child(
                (IWidget) new TextWidget(summary).textAlign(Alignment.CenterLeft)
                    .scale(0.5f)
                    .pos(BIND_TEXT_X, BIND_SLOT_Y)
                    .size(BIND_TEXT_WIDTH, COIN_BAR_HEIGHT));
        // ★常驻绑定行的<b>数量</b>只由 PERSISTENT_ROWS 决定（装配期断言它 = 2 且纵向恰闭合）：
        // 写死两次 .child(...) 会让"改了行位却少画一行"变成一条查不出来的错。
        for (int slot = 0; slot < PERSISTENT_ROWS; slot++) {
            root.child(persistentBindRow(ui, slot));
        }
        root.child(helpButton(ui));
        return root.excludeAreaInRecipeViewer();
    }

    /**
     * ★R81③：右段的一条<b>常驻</b>绑定行（不悬停就能看见）。
     * <p>
     * 行 0 整幅（108），行 1 起让出左边的 18+4 给帮助按钮 ⇒ 86。内容只有<b>短码身份</b>
     * （{@code bind.entry}）或"另有 n 条，悬停可见"（{@code bind.rows_more}）：位置五键的完整行照旧在
     * 按钮 tooltip（{@link NekoPocketPanel#bindTooltipText()}）里，★这里短一分都不是删信息，
     * 而是把"有几条 / 绑的是谁"从悬停面搬到常驻面 —— 取证记录 §3 的"常驻行数 = 0"就是本缺陷的加重项。
     * <p>
     * 文本一律 {@code scale(0.5f)}（与读数同口径）：{@code 86 ÷ 0.5 = 172} 逻辑像素，装得下
     * 「元件 + 8 位短码」与「……另有 n 条绑定未在此列出」，★装不下的完整位置行本来就不进常驻面。
     */
    private static IWidget persistentBindRow(NekoPocketPanel ui, int slot) {
        final boolean fullWidth = slot == 0;
        final TextWidget row = new TextWidget(IKey.dynamic(() -> ui.bindPersistentText(slot)));
        // ★逐条语句设定，不做链式：TextWidget 的 pos/size 继承自 IPositioned，链式下来拿到的是接口，
        // 上面那个 name(...) 就找不到符号（compileJava 直接红，比留一棵没名字的 widget 树好）
        row.textAlign(Alignment.CenterLeft);
        row.scale(0.5f);
        row.pos(fullWidth ? BIND_CONTENT_X : BIND_TEXT_X, persistentRowY(slot));
        row.size(fullWidth ? BIND_ROW_WIDTH : BIND_TEXT_WIDTH, COIN_BAR_HEIGHT);
        row.name("pocket_bind_row_" + slot);
        return row;
    }

    /**
     * 帮助按钮（★R78 D-2 的补偿落点）：tooltip = {@code notesText()}（用法摘要 / 通道成本 /
     * 主手限制 / ghost 用法与"每格声明吃掉一格真实容量"的代价）+ 绑定与解绑的两条说明。
     * <p>
     * 它不新增任何信息，只是把原本常驻在左栏的文字换成"悬停看得到"；★R81③ 后它占右段<b>行 3 的
     * 左位</b>（{@code x = 4}，与绑定格同列），右边那 86px 让给常驻绑定行 1 —— 行位不再靠"留白行"
     * 存在，纵向四行 × 18 = 72 一格都不富余。
     */
    private static IWidget helpButton(NekoPocketPanel ui) {
        return new ButtonWidget<>().pos(BIND_HELP_X, BIND_HELP_Y)
            .size(NekoPocketPanel.GRID, NekoPocketPanel.GRID)
            .name("pocket_help_button")
            .background(PocketGuiTextures.BUTTON)
            .child(
                (IWidget) new TextWidget(IKey.str("?")).scale(0.8f)
                    .textAlign(Alignment.Center))
            .playClickSound(false)
            .tooltip(tooltip -> {
                tooltip.addLine(IKey.dynamic(ui::notesText));
                tooltip.addLine(IKey.lang("gtit.pocket.bind.unbind_hint"));
                tooltip.addLine(IKey.lang("gtit.pocket.bind.stale_hint"));
            })
            .onMousePressed(mouseButton -> true);
    }

    // ------------------------------------------------------------------ 绑定行的机读形状（自第四列搬入）

    /**
     * 服务端行的解析结果（客户端只负责格式化，<b>不按 ghost 表或内存表推断</b>，R39b/R19）。
     * 行的机读形状见 {@link NekoPocketPanel#composeBindRows()}。
     */
    public static final class Row {

        /** 未定位（正常态，R40b）。★R80④ 连同下面两个状态码一起放开可见性，回归套件才能构造/核对绑定行 blob。 */
        public static final char STATUS_UNLOCATED = 'N';
        /** 已定位。 */
        public static final char STATUS_LOCATED = 'L';
        /** 位置已失效（需重新绑定）。 */
        public static final char STATUS_STALE = 'S';

        public final String id;
        public final char status;
        public final int dim;
        public final int x;
        public final int y;
        public final int z;
        public final int slot;

        public Row(String id, char status, int dim, int x, int y, int z, int slot) {
            this.id = id;
            this.status = status;
            this.dim = dim;
            this.x = x;
            this.y = y;
            this.z = z;
            this.slot = slot;
        }

        static Row blank() {
            return new Row(
                "",
                STATUS_UNLOCATED,
                PocketConstants.UNLOCATED,
                PocketConstants.UNLOCATED,
                PocketConstants.UNLOCATED,
                PocketConstants.UNLOCATED,
                PocketConstants.UNLOCATED);
        }

        /** 行的短码显示（ID 太长会溢出 tooltip 宽度 ⇒ 取前 8 位，完整 ID 见 {@link #id}）。 */
        String shortId() {
            return id.length() > 8 ? id.substring(0, 8) : id;
        }

        boolean located() {
            return status == STATUS_LOCATED;
        }

        boolean stale() {
            return status == STATUS_STALE;
        }

        /**
         * 解析服务端行。
         * <p>
         * 分隔符是 {@code '|'} 与 {@code ';'}，都来自 {@link #compose(List)} 的自有形状
         * （<b>不是</b> NBT 键名，故不落 {@code PocketConstants}），解析失败一律回落空行。
         */
        public static List<Row> parse(String blob) {
            final List<Row> rows = new ArrayList<>();
            if (blob == null || blob.isEmpty()) {
                return rows;
            }
            for (String line : blob.split(";")) {
                final String[] parts = line.split("\\|");
                if (parts.length < 7) {
                    continue;
                }
                try {
                    rows.add(
                        new Row(
                            parts[0],
                            parts[1].charAt(0),
                            Integer.parseInt(parts[2]),
                            Integer.parseInt(parts[3]),
                            Integer.parseInt(parts[4]),
                            Integer.parseInt(parts[5]),
                            Integer.parseInt(parts[6])));
                } catch (NumberFormatException ignored) {
                    // 外来/陈旧行：跳过而不抛，面板不得因一条坏行整屏崩
                }
            }
            return rows;
        }

        /** 与 {@link #parse} 对称的写出（服务端侧使用）。 */
        public static String compose(List<Row> rows) {
            final StringBuilder builder = new StringBuilder();
            for (Row row : rows) {
                if (builder.length() > 0) {
                    builder.append(';');
                }
                builder.append(row.id)
                    .append('|')
                    .append(row.status)
                    .append('|')
                    .append(row.dim)
                    .append('|')
                    .append(row.x)
                    .append('|')
                    .append(row.y)
                    .append('|')
                    .append(row.z)
                    .append('|')
                    .append(row.slot);
            }
            return builder.toString();
        }
    }

    /** 绑定格里当前的栈（供 S6 的绑定动作与 {@link PocketSlots} 复用）。 */
    static ItemStack stackInBindSlot(NekoPocketPanel ui) {
        return ui.inventory()
            .bindSlot()
            .getStackInSlot(0);
    }
}

package com.miaokatze.gtit.gui.pocket;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.item.ItemStack;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.api.widget.Interactable;
import com.cleanroommc.modularui.drawable.UITexture;
import com.cleanroommc.modularui.screen.RichTooltip;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.ItemDisplayWidget;
import com.cleanroommc.modularui.widgets.SlotGroupWidget;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.cleanroommc.modularui.widgets.slot.ItemSlot;
import com.cleanroommc.modularui.widgets.slot.ModularSlot;
import com.miaokatze.gtit.common.items.pocket.PocketConstants;
import com.miaokatze.gtit.common.items.pocket.PocketInventory;
import com.miaokatze.gtit.common.items.pocket.PocketUpgradeType;
import com.miaokatze.gtit.common.items.pocket.PocketUpgrades;
import com.miaokatze.gtit.currency.NekoCurrencyRegistrar;
import com.miaokatze.gtit.trade.NekoClientBalances;

/**
 * 底部带 = <b>横向三段</b>（R78① 定形状、★R81④ 定宽度）：
 * <b>左段 112×<u>96</u></b>（★★R94-①：本段<b>不再等于带的一部分</b>——它从 {@code y = 258} 起、
 * 比另两段高 18，因为左列末行那 18px 收回来了）。段内上下两半：<b>上 60 = 一整块说明文字</b>
 * （模式 + 回执 + 冷却，字号 {@link PocketGhostRequest#STATUS_TEXT_SCALE} = 0.8）、
 * <b>下 36 = 两行币栏</b>「币图标 + 数量 + 它自己的那一枚通道按钮」⇒ ★按钮压到面板最底、
 * 上面整块是文字；三段<b>底对齐</b>由 {@code static} 块钉住（★不是靠这段注释）
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
 * <b>右段纵向（★R81③：常驻绑定行落在这里；★R95 S4：行 3 让给升级插件格）</b>：四行 × 18 = 72 = 带高，
 * ★<b>零富余</b>：
 * <ol>
 * <li>行 0（y=0）：绑定按钮（原生 106 宽，在 108 内容区里<b>左右各余 1px</b> 居中）；</li>
 * <li>行 1（y=18）：绑定格 18 + 4 + 读数「已绑定 n / 上限」86；</li>
 * <li>行 2（y=36）：<b>常驻绑定行 0</b>（整幅 108；★R93-③ 起它是<b>整条位置行</b>——元件短码 +
 * 维度/坐标/槽位，那一族从原左段信息块搬来，用户："元件和维度放到右边去，右边本身就有维度了"）；</li>
 * <li>行 3（y=54）：帮助按钮 18（段内 x=4，原位不动）+ <b>★R95 五个升级插件格</b>
 * （段内 x=22 起，5×18=90 恰满内容区右沿 ⇒ 22+90=112=段宽）。</li>
 * </ol>
 * 常驻行位 = {@link #PERSISTENT_ROWS} = 行数 − 按钮行 − 绑定格行 − 升级格行 = {@code 4 − 3 = 1}；
 * ★R95 S4 起行 3 的常驻绑定行 1（86px）撤除，其信息保留路径 = 行 2 常驻行（被服务那枚的整条位置行）
 * + 绑定按钮 tooltip（全量条目 + 截断提示，R36 口径不删信息）⇒「另有 n 条」（{@code bind.rows_more}）
 * 的常驻落点随之消失，<b>显式</b>改由 tooltip 面与行 1 读数（「已绑定 n / 上限」）承担——落点裁定
 * 已进装配期断言的报错文案（见 {@code static} 块 ★R95 那条满幅判据），不是静默删面。
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
 * <b>★币值区照猫猫机形态（R78④，用户第二张图；★R83 C1 撤掉那两枚快捷图标）</b>：不是"猫猫币：179"
 * 那种文本行，而是<b>币物品图标 + 数量</b>，紧跟<b>这一枚币自己的那一枚通道按钮</b>。现成实现在
 * {@code client/gui/NekoCoinDisplayV2.java}（售货机 V2 面板用，装配点
 * {@code gui/vm/TradePage.java:1174-1206}），本块<b>借它的控件形态</b>：
 * <ul>
 * <li>图标 = {@code ItemDisplayWidget().item(NekoCurrencyRegistrar.getItemStack(id,1))}
 * （源：{@code NekoCoinDisplayV2.java:107-116}；本处 16×16 以塞进 18 高的币值条）；</li>
 * <li>数量 = {@code IKey.dynamic(...)} + 可读串（源：{@code NekoCoinDisplayV2.java:118-125}
 * 与 {@code :336-344} 的 {@code getReadableString}，10000→10K、1000000→1M）；</li>
 * <li>★<b>不再</b>借 {@code NekoCoinDisplayV2.java:127-158} 的弹出键与 {@code :179-203} 的 ME
 * 导入键那两枚 12×12 图标（R83 C1 按用户实机反馈撤除：它们把同一条通道动作摆了两份，
 * 且是"左下拥挤"的直接来源）。撤除<b>零信息损失</b>：第一枚的 tooltip 文案（成本/秒数）就是
 * 通道按钮的 tooltip（★R84 起按钮标签换成短文案，完整成本账只剩这里），第二枚的 tooltip 文案（余额）
 * 就是币值条自己的 tooltip（{@code coinRow} 末段），两者都仍带 {@code -%d} 成本占位、
 * 数字照旧由 {@code PocketConstants} 填。</li>
 * </ul>
 * 余额读数仍走 {@link NekoClientBalances}（R64c：客户端不得自行读钱包）。
 * <p>
 * <b>★R84：按钮搬进同一行</b>（用户原话「物品栏左侧猫猫币栏…改成两行即可：猫猫币图标+数量+启动按钮 /
 * 闪烁猫猫币图标+数量+启动按钮」）。R83 C1 那次判定"同行装不下"算的是<b>按钮沿用原生 88 宽</b>
 * （撤图标后条内只剩 {@code 88 − (16+3+30)} = 39px 自由带）⇒ 结论成立但前提可以换：★这次把
 * <b>币值条本身</b>从 88 收到 49（只包住「内缩 + 图标 + 缝 + 数量」），按钮吃剩下那 61px ⇒
 * 一行三段 {@code 49 + 2 + 61 = 112 = 段宽}。两件都靠 {@code POCKET_C2_coinbar} / {@code POCKET_C2_btn}
 * 的 <b>9-slice N=4</b> 才收得住（★{@code static} 块把"两张仍是 9-slice""收窄后仍留得下边距"
 * "只收不放"钉成断言，贴图片那侧一改就红）。
 * <b>唯一的真实代价 = 标签</b>：61px 装不下原文案（{@code channel.timed} 在 {@code scale 0.5} 下约
 * 82px）⇒ 印短文案 {@code gtit.pocket.channel.start}「启动」（★预算 20px，见
 * {@link #CHANNEL_LABEL_WIDTH_BUDGET}；「停止」态本轮<b>没有</b>落地，理由见 {@link #channelButton} 的
 * 边界），完整成本文案照旧全量留在 tooltip（R36：宽度不够就加 tooltip，不删信息）。
 * <p>
 * <b>★R84：收成两行省出的 36px —— 当年给的是"常驻元件信息块"，★R93-③ 改道给"一整块说明文字"，
 * ★★R94-① 那块再长高 24px（36 → 60）并把币栏压到段底</b>（用户看了 v1.8.37 的形状："按钮上下都有文字…
 * 我希望按钮移到最下、上面都放说明文字，这样文字就可以放大了"）。
 * <p>
 * R84 的裁定落点是「短文案「启动」+ 36px 给常驻元件信息块」，形状是左段行 2 / 行 3 各 112×18，
 * 显示<b>已绑定的那一枚</b>元件。★★R93-③ 用户实机改判："元件维度放到右边去，右边本身就有维度了…
 * 这样可以把说明的文字加大一些"⇒ 那两行的内容全部搬到<b>右段常驻行</b>（元件短码 + 维度/坐标/槽位 →
 * 常驻行 0；"另有 n 条不在服务口径" → 按钮 tooltip），左段这 36px 腾空，改成<b>一整块</b>装
 * {@code NekoPocketPanel#statusBlockText()}（模式 + 回执 + 冷却，字号另立
 * {@link PocketGhostRequest#STATUS_TEXT_SCALE}）。★<b>不再切两半</b>是这条改判的要点：一条正文要的
 * 是<b>连续</b>纵向预算，切成两个 18px 件会让最长那条在第一个件里折五行顶穿、第二个件空着。
 * ★★R94-① 正是同一条理由把这块从 36 长到 <b>60</b>（字号因此 0.65 → 0.8）。
 * <p>
 * ★<b>命名债已在 R94-① 偿清</b>：R93-③ 登记的是"块里装的已经不是元件信息、名字却仍叫
 * {@code cellInfo*} / {@code CELL_INFO_*}"，当时不改的理由是<b>判据不断链</b>（那组名字被十四条装配期
 * 断言与回归套件同时引用，改名等于把它们换成"新名字之间自洽"的空转对账）。本轮行位账整体作废
 * （块高 60 不是 18 的倍数 ⇒ "行位数"这个概念在本块消失）⇒ 那些断言本来就要重写 ⇒ <b>改名不再有
 * 风险，一次改到位</b>：几何常量现在是 {@code STATUS_BLOCK_X / Y / WIDTH / HEIGHT}，件本体是
 * {@link #statusBlock}。★<b>仍挂在账上的一条</b>：注入缝还叫 {@link CellInfoText} 与
 * {@link #EMPTY_CELL_INFO}（它只管"给一块正文"，与内容无关；改它要连面板接线一起动，收益只有好看）。
 * ★"块里必须只有一件"仍由 {@code coinBlock} 的件数对账钉住（{@link #STATUS_BLOCK_WIDGETS}）。
 * <p>
 * ★只按一枚排版的口径不变（{@link PocketConstants#ALLOWED_BOUND_CELLS} = <b>1</b>；
 * {@link PocketConstants#MAX_BOUND_CELLS} 仍是<b>数据层</b>的 64，分层理由见
 * {@code plan/_taskpack/decision-ledger.md} §八十四⑤），行位与口径的对账本轮从"信息块行位数"换成
 * {@link #PERSISTENT_ROWS}（★严格大于，末位必须留得下来给"另有 n 条"），{@code static} 块照旧钉住。
 * <b>数据为什么必须从调用点注入</b>：客户端这边读不到元件真值（绑定表只有身份与位置快照，本体在某个
 * ME 系统的驱动器里，读它要触达 AE2 与方块实体），而 {@code InfinityTypedCellHandler} 对自家单元的
 * 字节 / 类型账恒 {@code MAX} / {@code 0} ⇒ ★占用比<b>不是</b>可用信息。故正文走 {@link CellInfoText}
 * 这个最小缝（默认 {@link #EMPTY_CELL_INFO} = 空串 ⇒ 不传也编译、也运行），本类<b>不</b>按内存表或
 * ghost 表自行推断（R19/R39b）。
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
 * <b>绑定信息的可见面 = 右段常驻行 + 按钮 tooltip 两处</b>（R74 裁定 tooltip 那份、★R81③ 补常驻那份、
 * ★R84 曾再加左段那块常驻元件信息、★★R93-③ 那第三处<b>撤销</b>并搬到右段常驻行）：
 * tooltip 每条含维度 / x,y,z / 状态位，上限 {@link #TOOLTIP_ROWS} 条，
 * <b>超出即显式截断提示</b>（{@code bind.truncated}），★R93-③ 起另有一条 {@code bind.inert}
 * （"另有 n 条不在服务口径内"，它原先常驻在左段那块、随行位一起搬来这里）——
 * ★R84 口径：玩家可用的是 {@link PocketConstants#ALLOWED_BOUND_CELLS} = <b>1</b>，而数据层
 * {@link PocketConstants#MAX_BOUND_CELLS} 仍是 64 ⇒ <b>旧档里真的可能留着多条</b>（本轮刻意不在读档时收缩，
 * 见 {@code decision-ledger.md} §八十四⑤），所以上面那两条提示<b>今天就可能触发</b>，不是死字。
 * 而 tooltip 不能滚，所以"不删信息"的唯一合法形态就是"截断必须说出来"，不得静默只显示前 N 条；
 * ★R95 S4 起行位只剩 1（恰等于玩家口径 1 枚）⇒ 常驻面永远给"被服务的那一枚"整条位置行，
 * 「另有 n 条」不再有常驻行位，条数读「已绑定 n / 上限」那一行、明细进绑定按钮 tooltip（static 块
 * ★R95 满幅判据的报错文案里显式声明了这次落点改道，不许静默删面）。
 * 常驻行（{@link #PERSISTENT_ROWS} 条）：★R93-③ 起<b>被服务的那一枚给整条位置行</b>（元件短码 +
 * 维度/坐标/槽位）；★它仍不是 tooltip 的替代，
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
     * ★★<b>R94-①：左段自己的 y 起点 = 左列下沿</b>（{@code 6 + 252 = 258}）。
     * <p>
     * 旧形状里左段就是"带的一部分"（{@code y = 282}、高 72，与背包段/绑定段同一条带）。用户看
     * v1.8.37 后要求"按钮移到最下、上面都放说明文字，这样文字就可以放大"⇒ 左列末行那 18px
     * 收回给本段（{@link NekoPocketLeftColumn#HEIGHT} 从 270 变 252），本段因此<b>向上长</b>。
     * ★中栏仍 270（15 行一行不删）、背包段与绑定段仍 {@code y = 282 高 72} ⇒ <b>三段底对齐</b>
     * 这条由 {@code static} 块钉，不靠注释。
     */
    public static final int LEFT_BLOCK_Y = NekoPocketPanel.MARGIN + NekoPocketLeftColumn.HEIGHT;
    /**
     * ★R94-①：左段高 = 面板下沿内缩位 − 本段起点（{@code 354 − 258 = 96}）。
     * <p>
     * ★写成派生式而不是 96：面板总高 360 是 1080p / GUI Scale 3 的硬天花板（R75 起未动），
     * 任何人改总高或改左列高，本值跟着变，而下面那条"与其它两段底对齐"的断言会立刻红 ——
     * ★这才是不留无主空白的做法（写死 96 就只剩"今天对得上"）。
     */
    public static final int LEFT_BLOCK_HEIGHT = NekoPocketPanel.HEIGHT - NekoPocketPanel.MARGIN - LEFT_BLOCK_Y;
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
    /** 币值条<b>原生</b>宽（{@code POCKET_C2_coinbar} 的契约宽度；★只用于"只收不放"的对账，绘制宽是 {@link #COIN_BAR_WIDTH}）。 */
    private static final int COIN_BAR_NATIVE_WIDTH = PocketGuiTextureContract.widthOf("POCKET_C2_coinbar");
    /** 币值条/按钮的原生高度（{@code POCKET_C2_coinbar} 与 {@code POCKET_C2_btn} 同为 18 ⇒ ★与行高同一格）。 */
    private static final int COIN_BAR_HEIGHT = PocketGuiTextureContract.heightOf("POCKET_C2_coinbar");
    /** 通道按钮<b>原生</b>宽（{@code POCKET_C2_btn} 的契约宽度；★绘制宽是 {@link #CHANNEL_BUTTON_WIDTH}）。 */
    private static final int BUTTON_NATIVE_WIDTH = PocketGuiTextureContract.widthOf("POCKET_C2_btn");
    /** 币值条的 9-slice 边距（契约表 N=4；★收窄后能不能保住描边就看它，见 {@code static} 块那三条材质断言）。 */
    private static final int COIN_BAR_SLICE_MARGIN = PocketGuiTextureContract.sliceMarginOf("POCKET_C2_coinbar");
    /** 通道按钮的 9-slice 边距（同上）。 */
    private static final int BUTTON_SLICE_MARGIN = PocketGuiTextureContract.sliceMarginOf("POCKET_C2_btn");
    /**
     * 币值条 x（★R84 起 <b>0</b>，不再是 {@code (段宽-88)/2} 的居中值：一行的三段必须逐像素铺满
     * 112，居中省下的那 12px 现在是按钮的位）。
     */
    private static final int COIN_BAR_X = 0;
    /** ★币图标的边长（VM 用 22，本处必须塞进 18 高的币值条 ⇒ 取 16，其余比例照旧）。 */
    private static final int COIN_ICON_SIZE = 16;
    /** 图标在币值条里的横向内缩（★1px：不让 16 的图标压到 9-slice 那 4px 描边上）。 */
    private static final int COIN_ICON_INSET = 1;
    /** 图标与数量之间的缝（★取 2 ⇒ 数量的 x 与 R83 时代逐字相同：{@code 1+16+2 = 19}）。 */
    private static final int COIN_ICON_GAP = 2;
    /** 图标在条内的 y（{@code (18-16)/2 = 1}，纯派生，与 R78④ 的手写值同形）。 */
    private static final int COIN_ICON_Y = (COIN_BAR_HEIGHT - COIN_ICON_SIZE) / 2;
    /** 数量文本框 x（条内局部，= 图标右沿 + 缝）。 */
    private static final int COIN_AMOUNT_X = COIN_ICON_INSET + COIN_ICON_SIZE + COIN_ICON_GAP;
    /**
     * 数量文本框宽（★沿 R78④ 的 30px 不动：{@code readableAmount} 最坏输出 5 字（"2147M"）× 6 逻辑
     * px × {@code scale 0.5} = 15px，留一倍余量）。
     */
    private static final int COIN_AMOUNT_WIDTH = 30;
    /** {@link #readableAmount} 正常输出的最长字符数（★int 上限走 M 口径 = "2147M" ⇒ 5 字）。 */
    private static final int AMOUNT_MAX_CHARS = 5;
    /** 默认字体里数字的定宽（逻辑像素；vanilla 数字 = 6）。 */
    private static final int DIGIT_LOGICAL_WIDTH = 6;
    /**
     * ★★R92-⑥：渲染宽需求 = {@code ceil(5 × 6 × RESIDENT_TEXT_SCALE)}。旧式写死 {@code ÷ 2}（= 0.5 档），
     * 统一缩放抬到 0.6 之后那条装配期断言会<b>偏松</b>地放行一个其实装不下的串 ⇒ 改成按同一个常量派生。
     * 与 {@link #COIN_AMOUNT_WIDTH} 的差就是余量；★余额被同步成负数时串会更长（"-2147483648"），
     * 那种值是服务端钱包账目错乱的形状，由 {@code NekoClientBalances} 那边拦，不在这里加宽框。
     */
    private static final int AMOUNT_WIDTH_NEED = (int) Math
        .ceil(AMOUNT_MAX_CHARS * DIGIT_LOGICAL_WIDTH * PocketGhostRequest.RESIDENT_TEXT_SCALE);
    /**
     * 币值条<b>实占</b>宽（★R84 由原生 88 收到 {@code 1+16+2+30 = 49}：条只包住"图标 + 数量"这一组，
     * 省下的横向位给同一行的按钮。收窄靠的是 9-slice N=4 ⇒ 吃掉的全是中间的平坦金属带）。
     */
    private static final int COIN_BAR_WIDTH = COIN_AMOUNT_X + COIN_AMOUNT_WIDTH;
    /** 币值条与通道按钮之间的缝。 */
    private static final int CHANNEL_BUTTON_GAP = 2;
    /** 通道按钮 x（= 币值条右沿 + 缝 ⇒ 51）。 */
    private static final int CHANNEL_BUTTON_X = COIN_BAR_X + COIN_BAR_WIDTH + CHANNEL_BUTTON_GAP;
    /**
     * 通道按钮<b>实占</b>宽（★= {@code 112 − 51 = 61}，比原生 88 窄 27px；同样是 9-slice N=4 才收得住）。
     * <p>
     * 这 61px 就是"标签必须换短文案"的全部理由：原文案 {@code channel.timed}「激活短效次元通道
     * （-3 闪烁猫猫币，30 秒）」在常驻小字的缩放下约 98px &gt; 61 ⇒ 见 {@link #CHANNEL_LABEL_WIDTH_BUDGET}。
     */
    private static final int CHANNEL_BUTTON_WIDTH = COIN_WIDTH - CHANNEL_BUTTON_X;
    /** 全角字在 1.7.10 默认字体里的最宽前进量（逻辑像素；★保守取 10，实测 CJK 8~10）。 */
    private static final int FULLWIDTH_CHAR_LOGICAL_WIDTH = 10;
    /** 按钮短文案的字数上限（「启动」/「停止」都只有 2 个全角字 ⇒ 账留到 4 字）。 */
    private static final int CHANNEL_LABEL_MAX_CHARS = 4;
    /**
     * ★★R92-⑥：短文案的渲染宽预算 = {@code ceil(4 × 10 × RESIDENT_TEXT_SCALE)}。旧式写死 {@code ÷ 2}
     * （= 0.5 档）⇒ 换档后本式必须跟着派生，否则「预算够」这条装配期断言是偏松的假绿。与数量 /
     * 读数 / 常驻绑定行同一口径）。任务给的口径是「≤30px」，本式严一档 ⇒ 5 字以上才会顶到边。
     * <p>
     * ★这条是"给按钮留的位够不够"的账，<b>不是</b>折行开关：MUI2 的 {@code TextRenderer#draw(String)}
     * 只在 {@code maxWidth > 0}（件被给了显式宽）时才 hardWrap，故标签件<b>故意不给宽</b>，走
     * {@code drawSimple} ⇒ 真要超长也只是横向顶出，不会折成两行压到下一行。
     */
    private static final int CHANNEL_LABEL_WIDTH_BUDGET = (int) Math
        .ceil(CHANNEL_LABEL_MAX_CHARS * FULLWIDTH_CHAR_LOGICAL_WIDTH * PocketGhostRequest.RESIDENT_TEXT_SCALE);
    /**
     * ★R83 C1：左段的<b>币种数</b>（猫猫币 / 闪烁猫猫币 ⇒ 与 {@link NekoCurrencyRegistrar} 的两种
     * 一一对应，也是 {@link #coinBlock} 每轮发出的那一对件的序号源）。
     */
    private static final int CURRENCY_KINDS = 2;
    /**
     * ★R84：<b>每种币占的行数</b> = <b>1</b>（R83 C1 的「一条币值条 + 它自己的那一枚通道按钮」两行，
     * 按用户裁定收成<b>一行三段</b>「图标 + 数量 + 启动按钮」）。
     * <p>
     * R83 C1 当年不肯按"一币一行"排版，理由是同一段放不下 88 宽的按钮（撤掉那两枚快捷图标后条内
     * 只剩 {@code 88 − (16+3+30)} = 39px 自由带）。★R84 换了做法：把<b>币值条本身</b>从原生 88 收到
     * 49（{@code POCKET_C2_coinbar} 是 9-slice N=4 ⇒ 收掉的全是中间的平坦金属带）⇒ 同一行给按钮
     * 腾出 61px，代价是标签必须换短文案（见 {@link #CHANNEL_LABEL_WIDTH_BUDGET}）、完整文案进 tooltip。
     * 省下来的 36px <b>不是白留</b>：R84 当年整块给"元件信息块"（★R93-③ 起装说明文字，
     * ★R94-① 起这块高 60 且与币栏一起住进加高后的左段）⇒ ★左段纵向仍然
     * 逐像素闭合（见 {@code static} 块的游标推演），R83 C1 立的"撤件必须同时收行位"那条纪律照旧成立，
     * 只是这次的"收"换成了"改道"。
     */
    private static final int ROWS_PER_CURRENCY = 1;
    /**
     * ★左段<b>币栏</b>行位数 = {@code CURRENCY_KINDS × ROWS_PER_CURRENCY} = <b>2</b>。
     * ★★R94-①：与左段高的对账既不用它也不再用"行位加总"（说明块高 60 不是 18 的倍数 ⇒ 行位概念
     * 在本段作废），改钉 {@code STATUS_BLOCK_HEIGHT + COIN_BAND_HEIGHT == LEFT_BLOCK_HEIGHT}。
     */
    public static final int COIN_ROWS = CURRENCY_KINDS * ROWS_PER_CURRENCY;
    /**
     * ★R84：一行币栏<b>画出几件</b>（币值条 1 + 通道按钮 1）。
     * <p>
     * R83 时代它是 1 ⇒ {@link #coinBlock} 只数 {@link #COIN_ROWS} 就够；现在一行两件 ⇒ 漏画一枚按钮
     * 不再等于"件数与行数对不上"，必须按这个乘积对账，否则少掉的那一枚只是"那一行右边空着"，
     * 两端都不抛错、不打日志。
     */
    private static final int WIDGETS_PER_COIN_ROW = 2;
    /**
     * ★★R93-③ 立、★R94-① 换尺寸：左段<b>上半</b>画出的件数 = <b>恰 1</b>（一整块 112×60 的说明文字）。
     * <p>
     * 旧形态是"两行、各 112×18 共两件"（那时那块还叫 {@code CELL_INFO_ROWS} 行位）。写成具名量而不是把 {@code + 1} 塞进
     * {@code coinBlock} 的判据，是为了让"逐行件又长回来了"这种形状在<b>报错文案里就看得见</b>：
     * 那条对账读的是「币栏两件一行 + 下段一件」，任何一边数目被改都当场说出是哪一段。
     */
    private static final int STATUS_BLOCK_WIDGETS = 1;
    /** 币栏占的纵向高（= {@code COIN_ROWS × 18} = 36；★R94-① 之后它住在左段<b>下沿</b>）。 */
    public static final int COIN_BAND_HEIGHT = COIN_ROWS * COIN_BAR_HEIGHT;
    /**
     * ★★<b>R94-①：说明块高 = 左段高 − 币栏高（{@code 96 − 36 = 60}）</b>，★纯派生不写死。
     * <p>
     * 这一族常量的旧名是 {@code CELL_INFO_*}（R84 起那块装的是元件信息，R93-③ 换成说明文字后
     * 名字成了登记的<b>命名债</b>：当时它被十四条装配期断言引用，改名等于让那些断言空转）。
     * ★本轮行位账整体作废（60 不是 18 的整数倍 ⇒ "行位数"这个概念在本块消失）⇒ 那些断言本来
     * 就要重写 ⇒ <b>改名不再有风险，一次改到位</b>（债在此偿清，台账 R94 节记由）。
     * <p>
     * ★为什么这块能"让字变大"：纵向预算从 36 变 60 ⇒ 同一条最坏串（663 逻辑像素）按 0.8 折
     * 5 行 = 40px ≤ 60（0.65 时只能塞进 36px 的盒，那是上一轮的上限 0.70）。★字号单源
     * {@link PocketGhostRequest#STATUS_TEXT_SCALE}，逐点账见用例 {@code resident_text_pixel_budget}。
     */
    public static final int STATUS_BLOCK_HEIGHT = LEFT_BLOCK_HEIGHT - COIN_BAND_HEIGHT;
    /** 说明块 y（段内局部：★R94-① 起它在<b>上</b>，币栏压到最下）。 */
    public static final int STATUS_BLOCK_Y = 0;
    /** 说明块 x（★整幅贴左段：段内没有第四件要排位 ⇒ 横向不必再切）。 */
    public static final int STATUS_BLOCK_X = 0;
    /** 说明块宽（= 段宽 112；★横向预算 = {@code 112 ÷ 0.8 = 140} 逻辑像素一行）。 */
    public static final int STATUS_BLOCK_WIDTH = COIN_WIDTH - STATUS_BLOCK_X;

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
    public static final int BACKPACK_WIDTH = NekoPocketStorageColumn.WIDTH;
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
    public static final int BIND_WIDTH = NekoPocketEssenceColumn.WIDTH + NekoPocketPanel.COLUMN_GAP;
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
     * ★R81③ 立、★R95 S4 收窄：<b>常驻</b>绑定行的行位数 = {@code BIND_ROWS − 3} = <b>1</b>
     * （减掉的是"按钮行"、"绑定格 + 读数的行"与★R95 的"帮助 + 升级格行"——行 3 不再装常驻绑定行）。
     * 修复前这个数是 <b>0</b>（R81③ 治的那次）；R95 S4 起它恰等于玩家可绑定口径
     * {@link PocketConstants#ALLOWED_BOUND_CELLS}（1 枚）⇒ 常驻面永远给"被服务的那一枚"整条位置行，
     * 「另有 n 条」的常驻落点显式改道 tooltip 与读数行（R36 不删信息；static 块 ★R95 满幅判据声明）。
     */
    public static final int PERSISTENT_ROWS = BIND_ROWS - 3;
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
    /** 帮助按钮 y（★行 3 的左位；★R95 S4 起行 3 右侧那 90px 是五个升级插件格，不再是常驻绑定行 1）。 */
    public static final int BIND_HELP_Y = 3 * NekoPocketPanel.GRID;

    /**
     * ★R95 S4：升级插件格行的 x（段内局部 = 帮助按钮右沿 ⇒ {@code 4 + 18 = 22}，面板全局
     * {@code BIND_X + 22 = 302}）。行内 5 格 × 18 = 90 恰满内容区右沿（22 + 90 = 112 = 段宽，
     * 装配期断言）。
     */
    public static final int UPGRADE_ROW_GAP = PocketScrollWidget.SCROLLBAR_WIDTH;
    public static final int UPGRADE_ROW_X = BIND_CONTENT_X + NekoPocketPanel.GRID + UPGRADE_ROW_GAP;
    /**
     * 升级插件格的布局字面量（★R95：一行 5 格，单一布局字符 {@code 'U'}，仿蒸馏盘 {@code "DDDDDD"}
     * 的矩阵风格；槽号 = 布局序 = {@code PocketUpgradeType#ordinal()}，三空间同下标）。
     */
    private static final String[] UPGRADE_MATRIX = { "UUUUU" };
    /**
     * 升级格 tooltip 的插件名键表（★<b>整键字面量表</b>，不硬编码文字、不前缀拼接；下标 =
     * {@code PocketUpgradeType#ordinal()}，与 {@code ItemPocketUpgrade#TOKENS} 的 token 同源耦合，
     * 两表长度由 {@code static} 块对账）。
     */
    private static final String[] UPGRADE_ITEM_NAME_KEYS = { "item.neko_pocket_upgrade_capacity.name",
        "item.neko_pocket_upgrade_stack.name", "item.neko_pocket_upgrade_magnet.name",
        "item.neko_pocket_upgrade_channel_persist.name", "item.neko_pocket_upgrade_mage.name" };
    /**
     * 升级格 tooltip 的效果说明键表（与 {@code ItemPocketUpgrade#TOOLTIP_KEYS} 同一组键的 GUI 消费面；
     * 空格显灰化图案 + 本 tooltip，插件放入后物品自己的图标与 tooltip 自然遮盖）。
     */
    private static final String[] UPGRADE_EFFECT_KEYS = { "gtit.pocket.upgrade.capacity.tooltip",
        "gtit.pocket.upgrade.stack.tooltip", "gtit.pocket.upgrade.magnet.tooltip",
        "gtit.pocket.upgrade.channel_persist.tooltip", "gtit.pocket.upgrade.mage.tooltip" };

    /**
     * 升级格的灰化图案取用（★R95 S1 的五张 18×18 {@code POCKET_C2_upg_*.png}；空格显图案指认
     * "这一格收哪一型"，插件放入后物品图标自然遮盖——下标口径同上两张表）。
     * <p>
     * ★写成方法而不是静态数组：<b>不得在类初始化期触碰 {@code PocketGuiTextures}</b>——那会连带
     * 初始化 MUI2 的纹理表（fastutil 依赖），而回归套件的 JVM 类路径上没有它 ⇒ {@code <clinit>}
     * 当场 {@code NoClassDefFoundError}（S4 实测：方法体内引用才是惰性的，静态字段初始化不是）。
     */
    private static UITexture upgradeCellBackground(int index) {
        switch (index) {
            case 0:
                return PocketGuiTextures.UPGRADE_CAPACITY;
            case 1:
                return PocketGuiTextures.UPGRADE_STACK;
            case 2:
                return PocketGuiTextures.UPGRADE_MAGNET;
            case 3:
                return PocketGuiTextures.UPGRADE_CHANNEL;
            default:
                return PocketGuiTextures.UPGRADE_DISTILL;
        }
    }

    /** 绑定条目的 tooltip 最多列几条（超出必须显式提示，见类 javadoc）。 */
    public static final int TOOLTIP_ROWS = 10;

    /**
     * ★R83 C1：左段<b>币栏</b>第 {@code row} 行的 y（★行位单源 —— 币值条与通道按钮都从这里取，
     * 杜绝手写 y；行位数与带高的加总由 {@code static} 块的游标推演对账）。
     */
    public static int coinRowY(int row) {
        // ★★R94-①：币栏压到左段<b>下沿</b> ⇒ 起点不再是 0，而是说明块的下边界（两半逐像素相接）
        return STATUS_BLOCK_HEIGHT + row * COIN_BAR_HEIGHT;
    }

    /*
     * ★★R94-①：这里原先是 {@code cellInfoRowY(int)} 与 {@code leftBandRowY(int)} 两条"行位推演"。
     * 它们存在的唯一理由是<b>那块的高度是 18 的整数倍</b>（36 = 两格），于是可以拿"逐格走一遍 y"
     * 当纵向闭合的机检。本轮说明块高 = 60（不是 18 的倍数）⇒ 行位这个概念在本块不成立，
     * 两条推演一起删除，纵向账换成三条<b>高度</b>对账（见 static 块）：
     * ① 说明块高 + 币栏高 = 左段高；② 币栏第一行的 y = 说明块下沿（不许有缝）；
     * ③ 左段起点 + 左段高 = 带起点 + 带高（★三段底对齐，中栏与背包段一格未动）。
     */

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
        // ★R95 S4：升级格矩阵与三张下标表（插件名键 / 效果键 / 灰化图案）必须同时与 UPGRADE_SLOTS
        // 对齐——任何一张被加删条目都会在这里炸，而不是留一个越界下标给装配期。
        if (upgradeLayoutSlotCount() != PocketInventory.UPGRADE_SLOTS) {
            throw new IllegalStateException(
                "[pocket] 升级格矩阵产出 " + upgradeLayoutSlotCount()
                    + " 格，与 handler 格数 "
                    + PocketInventory.UPGRADE_SLOTS
                    + " 不符");
        }
        if (UPGRADE_ITEM_NAME_KEYS.length != PocketInventory.UPGRADE_SLOTS
            || UPGRADE_EFFECT_KEYS.length != PocketInventory.UPGRADE_SLOTS) {
            throw new IllegalStateException(
                "[pocket] 升级格的插件名/效果两张表长度 != " + PocketInventory.UPGRADE_SLOTS + "（下标口径 = ordinal）");
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
        if (BIND_WIDTH != NekoPocketEssenceColumn.WIDTH + NekoPocketPanel.COLUMN_GAP) {
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
        // ★R95 S4：常驻行位派生式换成「− 3」（减掉按钮行、绑定格行与帮助+升级格行），且仍 ≥ 1
        // （取证记录 §3 点名的缺陷就是它等于 0——常驻面不许整面消失）。
        if (PERSISTENT_ROWS != BIND_ROWS - 3 || PERSISTENT_ROWS < 1) {
            throw new IllegalStateException(
                "[pocket] 常驻绑定行位数 = " + PERSISTENT_ROWS
                    + "（★必须 = 行数 − 按钮行 − 绑定格行 − 帮助+升级格行 且 ≥ 1，R95 S4 起行 3 不再装常驻绑定行）");
        }
        // ★R95 S4：行位账换判据——旧的那条「最后一条常驻行的下沿 = 带高」随行 3 改装升级格一起作废
        // （常驻行只到行 2）；纵向闭合现在钉两截：①常驻行下沿 = 行 3 上沿（无缝、不重叠）；
        // ②行 3（帮助 + 升级格）的下沿 = 带高（见下一条）。
        if (persistentRowY(PERSISTENT_ROWS - 1) + NekoPocketPanel.GRID != BIND_HELP_Y) {
            throw new IllegalStateException(
                "[pocket] 最后一条常驻绑定行的下沿不等于行 3 上沿（★常驻行与帮助+升级格行之间出现缝隙或重叠）: " + persistentRowY(PERSISTENT_ROWS - 1));
        }
        if (BIND_HELP_Y + NekoPocketPanel.GRID != HEIGHT) {
            throw new IllegalStateException("[pocket] 行 3（帮助 + 升级格）的下沿不等于带高（★纵向出现无主空白或越界）: " + BIND_HELP_Y);
        }
        // 「按钮行与绑定格行不重叠」（R81 取证 §5）此前只靠"行位都是 18 的整数倍"隐式成立；
        // 控件高来自材质契约（coinbar 原生 88×18，★R84 左段把绘制宽收到 49 但高仍是 18），那张图一旦
        // 改高，两段的行就会互相压上一格，而且不抛错、不打日志。
        if (COIN_BAR_HEIGHT != NekoPocketPanel.GRID) {
            throw new IllegalStateException(
                "[pocket] 控件高 " + COIN_BAR_HEIGHT + " != 行高 " + NekoPocketPanel.GRID + "（★左段与右段的行都会互相重叠）");
        }
        // ★★R94-①：左段不再是"带的三等分之一"，它自己从 {@link #LEFT_BLOCK_Y} 起、高
        // {@link #LEFT_BLOCK_HEIGHT}（= 96 = 上 60 说明块 + 下 36 币栏）。旧的"逐格走行位"那套账
        // 随 60 这个非整倍数一起作废 ⇒ 换成三条<b>高度</b>对账 + 一条<b>三段底对齐</b>判据。
        // ① 两半各有归属：说明块 + 币栏 = 左段高（★不整除的余数、漏掉的一截都是无主空白）。
        if (STATUS_BLOCK_HEIGHT + COIN_BAND_HEIGHT != LEFT_BLOCK_HEIGHT) {
            throw new IllegalStateException(
                "[pocket] 左段纵向不闭合: 说明块 " + STATUS_BLOCK_HEIGHT
                    + " + 币栏 "
                    + COIN_BAND_HEIGHT
                    + " != 左段高 "
                    + LEFT_BLOCK_HEIGHT
                    + "（★R94-①：上说明下按钮，中间不许有缝、上下不许有富余）");
        }
        // ② 说明块在<b>上</b>、币栏压在<b>最下</b>：币栏第一行的 y 必须恰等于说明块下沿。
        // ★这条就是用户那句"按钮移到最下"的机检形态 —— 谁把两者换回来（或塞一条分隔），当场红。
        if (coinRowY(0) != STATUS_BLOCK_HEIGHT || STATUS_BLOCK_Y != 0) {
            throw new IllegalStateException(
                "[pocket] 左段上下顺序不对：币栏第一行 y = " + coinRowY(0)
                    + "、说明块 y = "
                    + STATUS_BLOCK_Y
                    + "（★期望 coinRowY(0) == STATUS_BLOCK_HEIGHT 且说明块贴段顶 ⇒ 说明在上、按钮压底）");
        }
        // ③ 币栏最后一行的下沿必须恰落在左段下沿（★不留尾缝、不越界）。
        if (coinRowY(COIN_ROWS - 1) + COIN_BAR_HEIGHT != LEFT_BLOCK_HEIGHT) {
            throw new IllegalStateException(
                "[pocket] 币栏最后一行的下沿不等于左段下沿: " + (coinRowY(COIN_ROWS - 1) + COIN_BAR_HEIGHT)
                    + " != "
                    + LEFT_BLOCK_HEIGHT);
        }
        // ④ ★三段<b>底对齐</b>：左段（258…354）与背包段/绑定段（282…354）必须共用同一条下沿，
        // 且左段起点必须恰等于左列下沿（★无缝：那 18px 是收回来的，不是新造的空隙）。
        // 面板总高 360 与中栏 15 行由各自的权威钉住，本轮一个字没动。
        if (LEFT_BLOCK_Y + LEFT_BLOCK_HEIGHT != Y + HEIGHT) {
            throw new IllegalStateException(
                "[pocket] 左段与背包/绑定段不再底对齐: " + (LEFT_BLOCK_Y + LEFT_BLOCK_HEIGHT)
                    + " != "
                    + (Y + HEIGHT)
                    + "（★面板下沿出现两截不等高的空白）");
        }
        if (LEFT_BLOCK_Y != NekoPocketPanel.MARGIN + NekoPocketLeftColumn.HEIGHT) {
            throw new IllegalStateException(
                "[pocket] 左段起点不等于左列下沿: " + LEFT_BLOCK_Y
                    + " != "
                    + (NekoPocketPanel.MARGIN + NekoPocketLeftColumn.HEIGHT));
        }
        // ⑤ 说明块必须是<b>整幅</b>（段内没有第四件要排位 ⇒ 横向不必再切）。
        if (STATUS_BLOCK_X != 0 || STATUS_BLOCK_WIDTH != COIN_WIDTH) {
            throw new IllegalStateException(
                "[pocket] 说明块不再整幅贴左段: x " + STATUS_BLOCK_X + " 宽 " + STATUS_BLOCK_WIDTH + " / 段宽 " + COIN_WIDTH);
        }
        // ★★R95 S4（B 项收口）：行 3 从「帮助 + 常驻绑定行 1」改成「帮助 + 五个升级插件格」⇒ 旧的
        // PERSISTENT_ROWS > ALLOWED_BOUND_CELLS（"末位留给「另有 n 条」"）对账随之作废：行位只剩 1、
        // 恰等于玩家可绑定口径 ⇒ 常驻面永远给"被服务的那一枚"整条位置行，「另有 n 条」（bind.rows_more）
        // 不再有常驻落点，★显式改道：条数读行 1 的「已绑定 n / 上限」、明细与截断提示读绑定按钮 tooltip
        // （R36 不删信息，只换面；PERSISTENT_ROWS ≥ 1 由上面那条断言钉）。新对账 = 行 3 恰满幅：
        // 帮助 18 + 5×18 = 108 恰等于内容区宽，且升级格右沿铺到内容区右沿（与行 0 按钮居中、行 2 整幅
        // 同一条「零无主空白」纪律）。
        if (NekoPocketPanel.GRID + UPGRADE_ROW_GAP + PocketInventory.UPGRADE_SLOTS * NekoPocketPanel.GRID
            != BIND_CONTENT_WIDTH
            || UPGRADE_ROW_X + PocketInventory.UPGRADE_SLOTS * NekoPocketPanel.GRID
                != BIND_CONTENT_X + BIND_CONTENT_WIDTH) {
            throw new IllegalStateException(
                "[pocket] 行 3「帮助 + 升级格」不满幅: 18 + " + PocketInventory.UPGRADE_SLOTS
                    + "×18 != 内容区 "
                    + BIND_CONTENT_WIDTH
                    + "（★要么爬出右段，要么留无主空白；★rows_more 的常驻落点已显式改道 tooltip 与读数行，"
                    + "本条被删时必须连那处声明一起动）");
        }
        // ★R84 左段横向闭合：一行三段「币值条 49 + 缝 2 + 按钮 61」必须恰等于段宽 112。
        // 三段里前两段钉成字面量、COIN_WIDTH 另有独立权威（= 中栏左沿 − 外边距），所以这条加总<b>不是</b>
        // 恒真：任何人挪中栏 ⇒ 段宽变 ⇒ 当场红，而不是让按钮爬到背包段第一列上（R80①"同 x 同宽"的
        // 视觉与点击面，MUI2 两端都不报）。
        if (COIN_BAR_X != 0 || COIN_BAR_WIDTH != 49
            || CHANNEL_BUTTON_GAP != 2
            || CHANNEL_BUTTON_X != 51
            || CHANNEL_BUTTON_WIDTH != 67) {
            throw new IllegalStateException(
                "[pocket] 左段一行的横向账不再是「0 + 49(条) + 2(缝) + 61(钮)」: 条 " + COIN_BAR_WIDTH
                    + " 缝 "
                    + CHANNEL_BUTTON_GAP
                    + " 钮 "
                    + CHANNEL_BUTTON_WIDTH
                    + "（★改任何一段都要连整条账与下面那三条材质断言一起重算）");
        }
        if (COIN_BAR_X + COIN_BAR_WIDTH + CHANNEL_BUTTON_GAP + CHANNEL_BUTTON_WIDTH != COIN_WIDTH) {
            throw new IllegalStateException(
                "[pocket] 左段一行三段不等于段宽: 0+49+2+61 != " + COIN_WIDTH + "（★要么爬到背包段上，要么留下无主空白）");
        }
        if (CHANNEL_BUTTON_X + CHANNEL_BUTTON_WIDTH != COIN_WIDTH) {
            throw new IllegalStateException(
                "[pocket] 通道按钮的右沿没铺到段宽右沿: " + (CHANNEL_BUTTON_X + CHANNEL_BUTTON_WIDTH) + " != " + COIN_WIDTH);
        }
        // ★收窄的两件材质前提（R84 新增，★这才是"把 88 收到 49 / 61"真正的安全边界）：
        // 1) 两张都必须仍是 9-slice —— 非 9-slice 的图一收就是整体横向压缩，中间的平坦带变斜纹，
        // 那是画崩坏而不是留白；2) 收完必须还留得下两侧边距（否则两条边距互相吃掉，描边消失）；
        // 3) 只许收不许放（放大 = 同一个 9-slice 中心被拉开，比收窄更容易看出伪影，且说明有人改了契约表）。
        // 三条的输入都来自契约表（独立权威），本文件改不动它们 ⇒ 贴图片若把 coinbar/btn 改成非 9-slice，
        // 这条会替全面板拦住一次静默退化。
        if (!PocketGuiTextureContract.isNineSlice("POCKET_C2_coinbar")
            || !PocketGuiTextureContract.isNineSlice("POCKET_C2_btn")) {
            throw new IllegalStateException("[pocket] 左段要收窄币值条与通道按钮（88 → 49 / 61），但其中一张不再是 9-slice ⇒ ★收窄会压糊整张图");
        }
        if (COIN_BAR_WIDTH <= 2 * COIN_BAR_SLICE_MARGIN || CHANNEL_BUTTON_WIDTH <= 2 * BUTTON_SLICE_MARGIN) {
            throw new IllegalStateException(
                "[pocket] 左段收窄后放不下两侧的 9-slice 边距: 条 " + COIN_BAR_WIDTH
                    + " ≤ "
                    + 2 * COIN_BAR_SLICE_MARGIN
                    + " / 钮 "
                    + CHANNEL_BUTTON_WIDTH
                    + " ≤ "
                    + 2 * BUTTON_SLICE_MARGIN);
        }
        if (COIN_BAR_WIDTH > COIN_BAR_NATIVE_WIDTH || CHANNEL_BUTTON_WIDTH > BUTTON_NATIVE_WIDTH) {
            throw new IllegalStateException(
                "[pocket] 左段把件放得比原生还宽（条 " + COIN_BAR_WIDTH
                    + "/"
                    + COIN_BAR_NATIVE_WIDTH
                    + " 钮 "
                    + CHANNEL_BUTTON_WIDTH
                    + "/"
                    + BUTTON_NATIVE_WIDTH
                    + "）⇒ R84 的账只收不放");
        }
        // ★文案位对账（用户裁定的"短文案「启动」"能落地就靠这一条）：短文案预算 20px + 左右各一条
        // 9-slice 边距必须装得进 61px 的按钮；装不进去就不是"折行"而是裁字，而 MUI2 不报。
        if (CHANNEL_LABEL_WIDTH_BUDGET + 2 * BUTTON_SLICE_MARGIN > CHANNEL_BUTTON_WIDTH) {
            throw new IllegalStateException(
                "[pocket] 通道按钮的短文案位不够: 预算 " + CHANNEL_LABEL_WIDTH_BUDGET
                    + " + 2×"
                    + BUTTON_SLICE_MARGIN
                    + " > 按钮宽 "
                    + CHANNEL_BUTTON_WIDTH
                    + "（★此时必须换更短的文案，而不是把按钮往段外借位）");
        }
        // 数量框的账同理：{@link #readableAmount} 的最坏输出必须留在框内（框给了显式宽 ⇒ 超长会被
        // hardWrap 折成两行，那一行只有 18 高，第二行开始压到下一格）。
        if (AMOUNT_WIDTH_NEED > COIN_AMOUNT_WIDTH) {
            throw new IllegalStateException("[pocket] 数量框装不下最坏输出: " + AMOUNT_WIDTH_NEED + " > " + COIN_AMOUNT_WIDTH);
        }
        // 图标塞得进条高（★R78④ 那句"VM 用 22、本处取 16"的账现在才有机检：22 + 2×1 = 24 > 18 会当场红）
        if (COIN_ICON_SIZE + 2 * COIN_ICON_INSET > COIN_BAR_HEIGHT) {
            throw new IllegalStateException(
                "[pocket] 币图标塞不进 " + COIN_BAR_HEIGHT + " 高的币值条: " + COIN_ICON_SIZE + " + 2×" + COIN_ICON_INSET);
        }
        if (2 * COIN_ICON_Y + COIN_ICON_SIZE != COIN_BAR_HEIGHT) {
            throw new IllegalStateException("[pocket] 币图标在条内不是上下等距: 上 " + COIN_ICON_Y + " 图标 " + COIN_ICON_SIZE);
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

    /** 升级插件格矩阵产出的格数（★R95 机检用；必须等于 {@link PocketInventory#UPGRADE_SLOTS} = 5）。 */
    public static int upgradeLayoutSlotCount() {
        int total = 0;
        for (String row : UPGRADE_MATRIX) {
            for (int index = 0; index < row.length(); index++) {
                if (row.charAt(index) == 'U') {
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

    /** 装配底部带（★带槽位的两块各一次 {@code excludeAreaInRecipeViewer()}：背包段与右段；左段无槽 ⇒ 不遮 JEI 叠加区）。 */
    public static List<ParentWidget<?>> build(NekoPocketPanel ui) {
        return build(ui, EMPTY_CELL_INFO);
    }

    /**
     * ★R84：带<b>元件信息供给者</b>的装配（左段下那 36px 的正文只从这里进，本类不自行推断）。
     *
     * @param cellInfo ★R93-③：<b>下段那一整块说明文字</b>的正文供给者（形参名沿用旧称，见类 javadoc
     *                 的命名债）；{@code null} 与不传等价（回落 {@link #EMPTY_CELL_INFO} ⇒ 那块读不到
     *                 东西，但几何与行位照旧闭合）
     */
    public static List<ParentWidget<?>> build(NekoPocketPanel ui, CellInfoText cellInfo) {
        final List<ParentWidget<?>> blocks = new ArrayList<>(3);
        blocks.add(coinBlock(ui, cellInfo == null ? EMPTY_CELL_INFO : cellInfo));
        blocks.add(backpackBlock(ui));
        blocks.add(bindBlock(ui));
        return blocks;
    }

    // ---------------- 左段（112×96）：上一整块说明文字 + 下两行「图标 + 数量 + 启动按钮」（★R84 重排、R93-③ 换内容、R94-① 换尺寸与上下顺序）

    /**
     * ★★<b>R94-①（需求 4 与 5 的形状再改一次）左段（112×96）= 上 60 + 下 36</b>：
     * <ol>
     * <li><b>y=0…60</b>：一整块说明文字（连续 112×60，正文走 {@link CellInfoText}；★它现在在<b>最上</b>，
     * 不再在段底）；</li>
     * <li><b>y=60（行 0）</b>：猫猫币「图标 16 + 数量 30」<b>同行</b> {@code 瞬时}通道按钮（61 宽）；</li>
     * <li><b>y=78（行 1）</b>：闪烁币「图标 + 数量」 同行 {@code 短效}通道按钮 ⇒ ★<b>按钮压到段底</b>。</li>
     * </ol>
     * ★★<b>旧行位清单（"112×72 = 四行 × 18、行 2/行 3 是说明文字"）本轮作废</b>：那是 R93-③ 的形状，
     * 描述的正是用户投诉的"文字在下、按钮在上"。★为什么单独强调：类级 javadoc 本轮已改对，
     * 方法级这段漏改就会<b>自相矛盾</b>，而六门与 G9/G10 都不扫代码内 javadoc ⇒ 下一轮照它搬回去无人报警。
     * ★现在的顺序由装配期断言钉：{@code coinRowY(0) == STATUS_BLOCK_HEIGHT} 且说明块贴段顶。
     * <b>改了什么</b>：R83 C1 的「一币两行（条 + 它自己的按钮）」按用户原话收成<b>一币一行</b>
     * （「物品栏左侧猫猫币栏…改成两行即可：猫猫币图标+数量+启动按钮 / 闪烁猫猫币图标+数量+启动按钮」），
     * 省下的 36px 没有变成空白，R84 当年整块改道给<b>常驻元件信息块</b>（用户裁定的落点：
     * 「短文案「启动」+ 36px 给常驻元件信息块」）；★★R93-③ 同一块 36px <b>再改道一次</b>给说明文字
     * （用户："元件和维度放到右边去…这样可以把说明的文字加大一些"）。两次改道都不许留空白，
     * 纵向账由 {@code static} 块的三条<b>高度</b>对账（★R94-①：行位账作废）与下面的件数对账一起钉住。
     * <p>
     * <b>★一行为什么现在装得下三件</b>（R83 C1 曾判定装不下）：那次算的是"按钮沿用原生 88 宽"，
     * 而条内撤图标后只剩 39px 自由带 ⇒ 结论是不许挤。R84 换了腾法：<b>币值条本身</b>从原生 88 收到
     * 49（只包住「内缩 1 + 图标 16 + 缝 2 + 数量 30」），按钮吃剩下的 61 ⇒
     * {@code 49 + 2 + 61 = 112 = 段宽}，★横向与纵向都逐像素闭合（{@code static} 块里既钉了这段字面量账，
     * 也钉了"两张材质必须仍是 9-slice N=4""收窄后仍留得下边距""只收不放"三条前提）。
     * <b>代价只有一条</b>：按钮标签放不下原文案（{@code channel.timed} 在 {@code scale 0.5} 下约 82px
     * &gt; 61px）⇒ 换成短文案「启动」（{@code gtit.pocket.channel.start}，预算 20px，见
     * {@link #CHANNEL_LABEL_WIDTH_BUDGET}），含成本与秒数的<b>完整文案照旧进 tooltip</b>
     * （R36：宽度不够就加 tooltip，不删信息）。
     * <p>
     * R78④ 的"币值区照猫猫机形态"与 R83 C1 撤掉的那两枚 12×12 快捷图标<b>结论不变</b>
     * （同一动作只剩一份、成本与余额两个 tooltip 文案各另有常驻落点）；R78① 的"收窄"也仍然成立：
     * 旧口径这一段是 180 宽（并排 + 一段说明文字），现在并排改纵向再改回一行三段，且说明文字
     * （{@code note.channel}）仍只在自己的 tooltip 里（R74② / R78 D-2）。
     */
    private static ParentWidget<?> coinBlock(NekoPocketPanel ui, CellInfoText cellInfo) {
        // ★★R94-①：本段的 y 与高<b>不再等于带的 y 与高</b>（左列末行那 18px 收回来了 ⇒ 段从 258 起、高 96）。
        // 背包段与绑定段仍是 pos(..., Y).size(..., HEIGHT)，两者与本段<b>底对齐</b>（static 块第 ④ 条）。
        final ParentWidget<?> block = new ParentWidget<>().pos(COIN_X, LEFT_BLOCK_Y)
            .size(COIN_WIDTH, LEFT_BLOCK_HEIGHT)
            .name("pocket_coin_block");
        // ★行位由同一个游标按「币值条 → 同一行的通道按钮」成对发放 ⇒ 两件叠在同一行不可能；
        // 漏画任一件则由下面那条件数对账当场抛（R84 起一行两件，★只数行数会漏掉"那一行右边空着"，
        // 故计数是 COIN_ROWS × WIDGETS_PER_COIN_ROW + 说明块那一件，与右段常驻行同一条纪律）。
        // ★★R94-①：先挂说明块（贴上沿）、再挂两行币栏（压到下沿）——顺序本身不决定位置
        // （两件各自 pos），但<b>读树的人</b>按这个顺序就能看出"上面是文字、下面是按钮"，
        // 而真正的顺序保证在 static 块第 ② 条（coinRowY(0) == STATUS_BLOCK_HEIGHT）。
        block.child(statusBlock(ui, cellInfo));
        int row = 0;
        for (int currency = 0; currency < CURRENCY_KINDS; currency++) {
            block.child(coinRow(currency, row));
            block.child(channelButton(ui, currency, row));
            row++;
        }
        if (row != COIN_ROWS) {
            throw new IllegalStateException("[pocket] 左段币栏发出 " + row + " 行，与行位数 " + COIN_ROWS + " 不符（★币种数或每币种行数被改）");
        }
        // ★★<b>R93-③ 把这块从"两行元件信息"改成"一整块说明文字"；★R94-① 把它搬到段顶并长到 112×60</b>。
        // 元件短码与位置五键已迁到<b>右栏常驻行</b>（用户："元件和维度移到右侧栏位去"），这块地方腾出来
        // 给那条装不下的说明文字（用户："说明文字…会超出…就有更多位置了，应该能容纳"）。
        // ★不按 18px 切件：一条说明文字要的是<b>连续纵向预算</b>（R93-③ 是 36px、★R94-① 是 60px）——
        // 切成 18px 件会让最坏那条正文（模式 + 最长回执 + 剩余 = 663 逻辑像素，见用例
        // resident_text_pixel_budget）在第一个件里折四行 = 26px > 18 顶穿、第二个件空着。★纵向闭合现在由 static 块那三条高度对账守
        // （说明块 + 币栏 = 段高 / 币栏起点 = 说明块下沿 / 段底与带底对齐），"漏画一行"的旧对账换成"漏画这一块"。
        if (block.getChildren()
            .size() != COIN_ROWS * WIDGETS_PER_COIN_ROW + STATUS_BLOCK_WIDGETS) {
            throw new IllegalStateException(
                "[pocket] 左段画出 " + block.getChildren()
                    .size()
                    + " 件，与「"
                    + COIN_ROWS
                    + " 行币栏 × "
                    + WIDGETS_PER_COIN_ROW
                    + " 件 + "
                    + STATUS_BLOCK_WIDGETS
                    + " 件下段说明块」不符（★要么币栏某一行的右边没被认领，要么两件叠在同一行；"
                    + "★R93-③：说明块<b>只画一整件</b>，多一件就是旧的逐行件没删干净）");
        }
        return block;
    }

    /**
     * 一行币值（猫猫机形态：图标 + 数量）。★R83 C1 起条内<b>不再</b>有快捷图标；★R84 起条宽收到 49
     * （只包住这两件），同一行右边那 61px 是 {@link #channelButton} 的位（理由见 {@link #coinBlock}）。
     *
     * @param currency 币种序号（0 = 猫猫币；1 = 闪烁猫猫币 ⇒ 与 {@link #channelButton} 同一序号成对）
     * @param row      左段行位（★y 只由 {@link #coinRowY(int)} 给出，不手写）
     */
    private static IWidget coinRow(int currency, int row) {
        final boolean neko = currency == 0;
        final String currencyId = neko ? NekoCurrencyRegistrar.NEKO_ID : NekoCurrencyRegistrar.SHIMMERING_NEKO_ID;
        final String name = neko ? "pocket_balance_neko" : "pocket_balance_shimmering";
        final ItemStack iconStack = currencyIcon(currencyId);
        final IKey balanceKey = IKey.lang(
            neko ? "gtit.pocket.balance.neko" : "gtit.pocket.balance.shimmering",
            () -> new Object[] { NekoClientBalances.getBalance(currencyId) });
        final ParentWidget<?> bar = new ParentWidget<>().pos(COIN_BAR_X, coinRowY(row))
            .size(COIN_BAR_WIDTH, COIN_BAR_HEIGHT)
            .name(name)
            .background(PocketGuiTextures.COIN_BAR)
            // 图标（形态源 NekoCoinDisplayV2.java:107-116，尺寸按 18 高的条收小；★两枚币同贴图，
            // 靠物品自己的附魔光带区分 ⇒ 沿用 ItemDisplayWidget，不引客户端贴图表）
            .child(
                new ItemDisplayWidget().item(iconStack)
                    .displayAmount(false)
                    .disableThemeBackground(true)
                    .pos(COIN_ICON_INSET, COIN_ICON_Y)
                    .size(COIN_ICON_SIZE, COIN_ICON_SIZE)
                    .name(name + "_icon"))
            // 数量（形态源 NekoCoinDisplayV2.java:118-125 + :336-344 的可读串；★x 与 R83 逐字同值 = 19）
            .child(
                (IWidget) new TextWidget(IKey.dynamic(() -> readableAmount(NekoClientBalances.getBalance(currencyId))))
                    .textAlign(Alignment.CenterLeft)
                    // ★★R92-⑥：币值是<b>数据读数</b> ⇒ 白字 + 阴影（与源质格存量同一口径），缩放走统一档
                    .scale(PocketGhostRequest.RESIDENT_TEXT_SCALE)
                    .color(PocketGhostRequest.readoutTextColor())
                    .shadow(Boolean.TRUE)
                    .pos(COIN_AMOUNT_X, 0)
                    .size(COIN_AMOUNT_WIDTH, COIN_BAR_HEIGHT));
        // ★余额的常驻可见面：撤掉那枚"只读明细"图标后，这条 tooltip 就是它唯一的落点（R36 不删信息）。
        bar.tooltip(tooltip -> tooltip.addLine(balanceKey));
        return bar;
    }

    /**
     * ★R84：一枚通道启动按钮，与 {@link #coinRow} <b>同一行</b>的右位（宽 {@code 61}、高 18 ⇒
     * 比原生 88 窄 27px，靠 {@code POCKET_C2_btn} 的 9-slice N=4 保住描边与按下态；★底与按下态照旧）。
     * <p>
     * 按钮<b>只发"请求激活"</b>（R64c 末段 + R39b）：动作码各自独立（{@code BURST} / {@code SHORT}），
     * 扣费与推送/拉取模式判定都在服务端，客户端<b>不得</b>按 ghost 表自行推断。
     * <p>
     * ★标签换<b>短文案</b> {@code gtit.pocket.channel.start}（「启动」）：61px 的按钮放不下原文案
     * （{@code channel.timed} 在 {@code scale 0.5} 下约 82px），而完整文案（含 {@code -%d} 成本与秒数
     * 占位，数字由 {@code PocketConstants} 填入，R58b/契约 §7 第 5 条）★一字不减地留在 tooltip
     * （R36：宽度不够就加 tooltip，不删信息）。两枚按钮共用同一枚短文案键 —— 分辨"这行花在哪个通道"
     * 靠的是<b>同一行左边那枚币</b>（图标 + 余额 + 币种 tooltip），不是按钮字。
     * 标签件<b>不给</b>显式宽（走 {@code drawSimple} ⇒ 不会被 hardWrap 折成两行压到下一行）。
     *
     * @param currency 币种序号（与同一行的币值条同序号 ⇒ "这枚币花在哪个通道"读得出来）
     * @param row      左段行位（★y 与那一行的币值条同源，两件永远并排不叠）
     */
    private static IWidget channelButton(NekoPocketPanel ui, int currency, int row) {
        final boolean instant = currency == 0;
        final IKey fullLabel;
        final NekoPocketPanel.ChannelRequest request;
        if (instant) {
            fullLabel = IKey
                .lang("gtit.pocket.channel.instant", () -> new Object[] { PocketConstants.BURST_COST_NEKO });
            request = NekoPocketPanel.ChannelRequest.BURST;
        } else {
            fullLabel = IKey.lang(
                "gtit.pocket.channel.timed",
                () -> new Object[] { PocketConstants.SHORT_COST_SHIMMERING_NEKO,
                    PocketConstants.SHORT_CHANNEL_SECONDS });
            request = NekoPocketPanel.ChannelRequest.SHORT;
        }
        final IKey label = IKey.lang("gtit.pocket.channel.start");
        return new ButtonWidget<>().pos(CHANNEL_BUTTON_X, coinRowY(row))
            .size(CHANNEL_BUTTON_WIDTH, COIN_BAR_HEIGHT)
            .name(instant ? "pocket_button_instant" : "pocket_button_timed")
            .background(PocketGuiTextures.BUTTON)
            .hoverBackground(PocketGuiTextures.BUTTON_PRESSED)
            .child(
                // ★★R92-⑥ 两件事一起收：① 缩放走统一档 + 提示色；② <b>修标签贴左上</b> ——
                // ButtonWidget 走 SingleChildWidget，它<b>不替子件摆位</b>（SingleChildWidget.java:30-40），
                // 于是 textAlign(Center) 从来没生效过（截图里"绑定/启动"都顶着左上角）。
                // ★居中量必须由盒子产生 ⇒ 显式给子件 pos(0,0) + 与按钮同宽同高，★父盒一个字不改。
                (IWidget) new TextWidget(label).scale(PocketGhostRequest.RESIDENT_TEXT_SCALE)
                    .color(PocketGhostRequest.hintTextColor())
                    // ★R101：提示色改白字（色阶单源处改判）⇒ 白字必须配深色阴影才压得住布纹底
                    .shadow(Boolean.TRUE)
                    .textAlign(Alignment.Center)
                    .pos(0, 0)
                    .size(CHANNEL_BUTTON_WIDTH, COIN_BAR_HEIGHT))
            // ★常驻可见的完整成本账：短文案省下的那部分信息全部落在这里（不得删）。
            // ★R95 S4：载体已固化 CHANNEL_PERSIST 时<b>追加</b>「通道常开」注记——tooltipDynamic +
            // autoUpdate ⇒ 每次打开都重估（与蒸馏盘那条动态 tooltip 同一先例），未固化时不多占一行。
            .tooltipDynamic(tooltip -> {
                tooltip.addLine(fullLabel);
                if (ui.channelPersistActive()) {
                    tooltip.addLine(IKey.lang("gtit.pocket.channel.always_on"));
                }
            })
            .tooltipAutoUpdate(true)
            // ★R95 S4 通道按钮禁用客户端腿：载体已固化 CHANNEL_PERSIST ⇒ 通道常开（driver 批边界
            // 自动续批），点击早退<b>不发包</b>（返回 true 把点击吃掉防穿透，同 dispatchBindButtonClick
            // 的先例；服务端 performChannelRequest 本就有同判据的 always_on 早退回执，两侧读同一条
            // 单源判据 NekoPocketPanel#channelPersistActive，不构成第二处真相）。
            // ★视觉禁用<b>不走</b> setEnabledIf：本仓实测该 API 在此 MUI2 版本会连底图一起不画
            // （gui/vm/IoColumnPanel.java:252 记实）⇒ 61px 只剩一个黑洞；改用「点击无动作 + tooltip 注记」。
            // ★★★R96 S5（验收 B3）：这一腿从"两枚按钮一起吃"改成<b>只吃短效那一枚</b>。R95 的写法
            // 不分 mode ⇒ 位一置起，连"一次穿完"的瞬时通道也在客户端就被吞掉了，而 README 代价 33
            // 从没承诺过禁瞬时（取证 r96-ret1 §2.5 派生事实 1）⇒ 本轮裁定<b>瞬时通道恢复可用</b>，
            // 服务端那一支同步分 mode（识别→冷却→扣费三重点检照旧 ⇒ 恢复可用 ≠ 免费）。
            // ★短效那一腿读的是面板那条<b>已经补了"在场"第二问</b>的单源判据（B4 同一次改口）：
            // 位在场而道没起来（从没开过界面 / 一枚元件都没绑）时放这一按过去，服务端那个幂等激活口
            // 会把道起起来 —— 仍不扣费、仍不进冷却。
            .onMousePressed(button -> {
                if (!instant && ui.channelPersistActive()) {
                    return true;
                }
                return button == 0 && ui.requestChannel(request);
            });
    }

    /**
     * ★★<b>R93-③ 立、★R94-① 长高：左段<b>上半</b>那一整块
     * （{@link #STATUS_BLOCK_WIDTH} × {@link #STATUS_BLOCK_HEIGHT} = 112×60）的说明文字件</b>
     * ——模式 / 回执 / 冷却的完整可读体。
     * <p>
     * <b>三代的形状史</b>：① 左栏末行 88×18（最长串按 0.6 折五行 = 30px &gt; 18 ⇒ 必然溢出，就是用户
     * 实机报的"会超出"）；② R93-③ 换到底部带左段下沿 112×36、字号 0.65（该点上界 0.70）；
     * ③ ★R94-① 把左列末行那 18px 也收进来 ⇒ 块变 112×<b>60</b>、字号抬到 0.8，同时<b>币栏压到段底</b>
     * ⇒ 用户要的"按钮在最下、上面整块是文字"（★顺序由 static 块第 ② 条钉，不靠注释）。
     * <p>
     * 本方法<b>不产生任何信息</b>，只把 {@link CellInfoText} 给到的正文原样摆上去（★含 null 兜底：
     * 供给者给了 null 就当空，不许让整屏 NPE）。数据为什么只能从外面注入，见 {@link CellInfoText}。
     * ★它同时是左列末行原先那三条 tooltip 的<b>新宿主</b>（见方法体末段）——撤形状成对，R36。
     */
    private static IWidget statusBlock(NekoPocketPanel ui, CellInfoText cellInfo) {
        final TextWidget body = new TextWidget(IKey.dynamic(() -> {
            final String text = cellInfo.text();
            return text == null ? "" : text;
        }));
        // ★逐条语句设定，不做链式：TextWidget 的 pos/size 继承自 IPositioned，链式下来拿到的是接口，
        // 上面那个 name(...) 就找不到符号（同 {@link #persistentBindRow} 记实的那条）
        body.textAlign(Alignment.CenterLeft);
        // ★★R93-③：这块是<b>说明文字</b>（不是数据读数）⇒ 走提示色 + 说明文字专用档
        body.scale(PocketGhostRequest.STATUS_TEXT_SCALE);
        body.color(PocketGhostRequest.hintTextColor());
        // ★R101：提示色改白字 ⇒ 深色阴影跟着（白字无影压不住布纹底）
        body.shadow(Boolean.TRUE);
        body.pos(STATUS_BLOCK_X, STATUS_BLOCK_Y);
        body.size(STATUS_BLOCK_WIDTH, STATUS_BLOCK_HEIGHT);
        body.name("pocket_status_block");
        // ★★R94-①：左列末行被收回时，它原本挂的三条 tooltip <b>原样搬到这里</b>（撤形状成对，R36）：
        // statusHintText（模式 + 状态 + 主手限制的完整读法）/ capacityReadoutText（每槽容量与总容量）/
        // notesText（用法摘要、通道成本、ghost 代价那一份全量说明）。★一条不删、一条不双写：
        // 宿主从"那一行文字"换成"装着这段文字的那块"。
        // ★写成"先攒一个 RichTooltip 再交出去"而不是链式 lambda：{@code TextWidget} 是自指泛型
        // （{@code TextWidget<W extends TextWidget<W>>}），本方法按仓内既有写法用裸类型建件（下面
        // pos/size 那几条语句同理），裸接受者上 lambda 的参数会被擦成 Object ⇒ addLine 找不到符号。
        // ★★非阻断 ②（审查指出）：库内 lambda 那一支是 `new RichTooltip().parent(this)`
        // （`Widget.java:321`），而裸构造器的构造器预置是 `parent(Area.ZERO)`（`RichTooltip.java:53-55`）
        // ⇒ 不接 parent 就等于强制"跟鼠标"，玩家把 tooltip 锚定档改掉时<b>只有这一块</b>不跟别人一致。
        final RichTooltip tips = new RichTooltip().parent(body);
        tips.addLine(IKey.dynamic(ui::statusHintText));
        tips.addLine(IKey.dynamic(ui::capacityReadoutText));
        tips.addLine(IKey.dynamic(ui::notesText));
        body.tooltip(tips);
        return body;
    }

    /**
     * ★R84 立、★R93-③ 换内容、★R94-① 换尺寸：左段<b>上半那一整块说明文字</b>的正文供给者
     * （连续 112×60；旧形态是"两行、各 18px 高"，R93-③ 起 {@code row} 这个参数整个消失）。
     * <p>
     * <b>为什么是一个缝而不是一段自算的码</b>（R84 立的那条理由<b>原样成立</b>，只是要显示的东西换了）：
     * 这块要读的都是<b>服务端算出来的运行期事实</b>（模式 / 回执 / 冷却），客户端自己按内存表或 ghost 表
     * 推断就是第二处真相（R19/R39b）⇒ 只接调用点给到的<b>已同步</b>文本（与右段那两条常驻绑定行同一条纪律）。
     * ★元件的"类型 / 条目数"从未真的接上（要新增一条服务端同步值，且新单元在上游恒返 {@code MAX}/{@code 0}
     * ⇒ 占用比不是可用信息），本轮那块改道给说明文字之后，这条待办<b>不再</b>有常驻落点 ⇒ 列进交付说明。
     * <p>
     * <b>调用点约定</b>（★实现方 = {@code NekoPocketPanel#statusBlockText()}）：
     * <ul>
     * <li>返回值是<b>整块</b>成品文本（可以含 {@code '\n'}，MUI2 会按行各自折行）；无信息给 {@code ""}，
     * 返回 {@code null} 本类兜底成空文本；</li>
     * <li>★预算：块给了显式宽 ⇒ MUI2 按宽度 hardWrap。逐点账住在用例 {@code resident_text_pixel_budget}
     * 里（最坏串 = 模式 + <b>最长回执</b> + 剩余秒数 = 663 逻辑像素，盒 112×60、字号 0.8 ⇒ 5 行 40px ≤ 60），
     * ★回执族新增/加长任何键都会自动进这本账，不靠人记得改测试；</li>
     * <li>★<b>不要在这里塞绑定信息</b>：元件短码与维度/坐标/槽位住右栏常驻行（{@code bindPersistentText}），
     * "另有 n 条不在服务口径"住绑定按钮 tooltip（{@code bindTooltipText}）——同一条事实只许有一个面；</li>
     * <li>未绑定那一枚时不需要在这里说：那块读的是运行期状态，与绑了几条无关（★旧形态"行 0 给
     * {@code bind.none}"已经作废，那条读数现在由右栏常驻行 0 的空表分支承担）。</li>
     * </ul>
     */
    public interface CellInfoText {

        /**
         * 整块正文（★R93-③：旧形态是"第 row 行"，因为那块被切成两个 18px 件）；
         * ★无信息给空串，不给 {@code null}（给了也只当空行）。
         */
        String text();
    }

    /**
     * 默认空实现（★"不传也能编译运行"那条兜底）：整块给空串 ⇒ 几何照旧闭合，只是读不到东西。
     * <p>
     * ★这只该出现在<b>还没接线</b>的中间态：接线完成后调用点必须走
     * {@link #build(NekoPocketPanel, CellInfoText)}，否则左段下那 36px 就是一块永远空着的位
     * （★R93-③：那块现在的正文是 {@code NekoPocketPanel#statusBlockText()}；R84 那次改道与本轮这次
     * 改道都不许留下空白，空着不算闭合）。
     */
    public static final CellInfoText EMPTY_CELL_INFO = () -> "";

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
     * <li>行 3（★R95 S4 重排）：帮助按钮 18（原位不动）+ <b>五个升级插件格</b>（5×18 = 90 恰满
     * 内容区右沿；原常驻绑定行 1 撤除，其信息保留路径见 {@link #PERSISTENT_ROWS} 的 javadoc）。</li>
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

        // ★R95 S4：第 5 组 = 五个升级插件格（行 3 右位）。槽件本体由 {@link PocketSlots#upgradeCell}
        // 工厂造（filter 单源准入 + accessibility(true,false) 放入即固化不可取出），这里只做三件事：
        // 灰化图案底、格 tooltip、以及★固化写点——服务端 changeListener（非 init 装；先例
        // fluidInteraction，见 {@code PocketInventory#newUpgradeGroup} javadoc 的落点设计）。
        final SlotGroupWidget upgradeGroup = SlotGroupWidget.builder()
            .matrix(UPGRADE_MATRIX)
            .key('U', index -> upgradeCellWidget(ui, index))
            .synced(PocketSlots.SYNC_UPGRADE)
            .slotGroup(PocketSlots.GROUP_UPGRADE)
            .build();
        upgradeGroup.pos(UPGRADE_ROW_X, BIND_HELP_Y);

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
                        // ★★R92-⑥：同"启动/停止"那条 —— 提示色 + 统一缩放 + <b>显式摆位</b>
                        // （★ButtonWidget 不替子件摆位 ⇒ 只给 textAlign 从来没居中过）
                        (IWidget) new TextWidget(IKey.lang("gtit.pocket.bind.button"))
                            .scale(PocketGhostRequest.RESIDENT_TEXT_SCALE)
                            .color(PocketGhostRequest.hintTextColor())
                            // ★R101：提示色改白字 ⇒ 深色阴影跟着（同"启动/停止"标签那条）
                            .shadow(Boolean.TRUE)
                            .textAlign(Alignment.Center)
                            .pos(0, 0)
                            .size(BIND_BUTTON_WIDTH, COIN_BAR_HEIGHT))
                    // ★绑定信息的完整可见面（R74）：全部条目 + 位置五键 + 超出的显式截断提示。
                    // R81③ 之后它不再是<b>唯一</b>可见面（右段多了常驻行），但仍是<b>全</b>信息面 ⇒ 不得删。
                    .tooltip(tooltip -> tooltip.addLine(IKey.dynamic(ui::bindTooltipText)))
                    // 左键绑定；右键解绑最后一条；Shift+右键清空全部（R74 裁定，语义与 onServerAction 同步改）
                    .onMousePressed(button -> ui.dispatchBindButtonClick(button)))
            .child(bindGroup)
            .child(upgradeGroup)
            .child(
                (IWidget) new TextWidget(summary).textAlign(Alignment.CenterLeft)
                    // ★★R92-⑥：「已绑定元件 n / m」是<b>数据读数</b>
                    .scale(PocketGhostRequest.RESIDENT_TEXT_SCALE)
                    .color(PocketGhostRequest.readoutTextColor())
                    .shadow(Boolean.TRUE)
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
     * ★R95 S4：一个升级插件格的槽件（行 3 右位；{@code index} = 槽号 = 位图位 =
     * {@code PocketUpgradeType#ordinal()}，三空间同下标）。
     * <p>
     * 槽件本体由 {@link PocketSlots#upgradeCell} 工厂造（filter 单源准入 + {@code accessibility(true,false)}
     * 放入即固化不可取出）；本方法只补 GUI 三件：
     * <ol>
     * <li>灰化图案底（{@link #upgradeCellBackground}：空格显图案指认"这一格收哪一型"，插件放入后
     * 物品图标自然遮盖）；</li>
     * <li>格 tooltip：格说明 + 对应插件名 + 效果（lang 动态键，整键字面量表，不硬编码文字）；</li>
     * <li>★<b>固化写点</b>（install 的唯一调用处）：服务端 changeListener——签名与早退口径照
     * {@code PocketSlots#fluidInteraction} 的先例（{@code (newItem, onlyAmountChanged, client, init)}，
     * 客户端帧与 init 帧一律早退，⇒ 读档回灌那一拍不重装），载体栈照
     * {@code PocketInventory#newUpgradeGroup} javadoc 的设计经活查表取，<b>新栈非空</b>即
     * {@code PocketUpgrades.install}（只置不清，天然不可逆；一格一型 ⇒ 放对格才进得来，准入判据先挡）。
     * ★<b>W3（R95 审查加固）</b>：install 前加一道<b>载体身份校验</b>——活查结果必须与
     * {@code session} 的载体栈（{@link NekoPocketPanel#carrierStack()}）<b>是同一个对象</b>，
     * 不等即跳过：载体在会话期内被换出（掉落重捡 / 堆叠合并 / 跨维重建造新栈）时，
     * "把不可逆的位图写进错误的口袋"比"少固化一次"重得多（插件仍躺在格内，重开面板即补上）。</li>
     * </ol>
     */
    private static ItemSlot upgradeCellWidget(NekoPocketPanel ui, int index) {
        final ModularSlot modular = ui.slots()
            .upgradeCell(ui.inventory(), index);
        modular.changeListener((newItem, onlyAmountChanged, client, init) -> {
            if (client || init || newItem == null) {
                return;
            }
            // ★W3：载体换位漂移门（身份判据，理由见方法 javadoc 第 3 条）
            final ItemStack carrier = ui.carrierStackLive();
            if (carrier == null || carrier != ui.carrierStack()) {
                return;
            }
            if (PocketUpgradeType.values()[index] == PocketUpgradeType.STACK) {
                PocketUpgrades.installStackCount(carrier, newItem.stackSize);
            } else {
                PocketUpgrades.install(carrier, PocketUpgradeType.values()[index]);
            }
        });
        final ItemSlot slot = new UpgradeCellSlot(ui, index);
        slot.slot(modular);
        slot.name("pocket_upgrade_" + index);
        slot.background(upgradeCellBackground(index));
        // ★R96 S2：注册形态从 tooltip(...) 换成 tooltipDynamic(...) + autoUpdate —— 本处新增的
        // "已关闭"那一行是<b>运行期状态</b>（玩家在配置面板里切一下就该消失），而一次性通道
        // （ITooltip:64-66「Only called once」）会在装配期把它冻住（同 R83 B2(1) 记过的那条）。
        slot.tooltipDynamic(tooltip -> {
            tooltip.addLine(IKey.lang("gtit.pocket.upgrade.slot.tooltip"));
            tooltip.addLine(IKey.lang(UPGRADE_ITEM_NAME_KEYS[index]));
            tooltip.addLine(IKey.lang(UPGRADE_EFFECT_KEYS[index]));
            // ★关闭态读数（逐型状态的可见面之一；光泽只有一个布尔位，见 S1 的定案）：
            // 判据与配置面板行首读数同源（PocketConfigPanel 的 SwitchState），不在此重写第二次。
            final String offKey = PocketConfigPanel
                .cellOffReadoutKeyOf(ui.upgradeSwitchState(PocketUpgradeType.values()[index]));
            if (offKey != null) {
                tooltip.addLine(IKey.lang(offKey));
            }
        });
        slot.tooltipAutoUpdate(true);
        return slot;
    }

    /**
     * ★R96 S2：升级插件格的槽件 = R95 的"放入即固化"槽件 + <b>左键开配置面板</b>（P-2/P-3）。
     * <p>
     * <b>为什么要自己开一个子类而不是挂 lambda</b>：{@code ItemSlot} 没有 {@code onMousePressed} 的
     * setter 形状（只有 {@link ItemSlot#onMousePressed(int)} 这个覆写点），而本格的手势是
     * <b>条件让位</b>——空格 / 手上有货时必须把点击交回 vanilla 的放置语义，否则 R95 那条"放进格子里
     * 即固化"的既有动作会被吃掉。这正好是 {@code NekoFilterSlot#onMousePressed} 那套按键矩阵的形状。
     * <p>
     * <b>吞击条件（三条同时成立才吞）</b>：① 左键；② 这一格里<b>已经有</b>插件（= 这一型已固化，
     * 判据单源在 {@code NekoPocketPanel#upgradeCellFilled}，★不在这里再问一次位图）；③ 游标<b>空</b>
     * （手上拿着东西的左键是"放上去"的意图，包括往别型格里误放）。三者齐 ⇒ 开配置面板并
     * {@code return SUCCESS} 吞击（先例：{@code NekoPocketBottomBand} 通道按钮那条 {@code return true}
     * 与 {@code NekoFilterSlot} 的中键支——★不调 {@code super}，否则 vanilla 会把它读成拿起/放置）。
     * <p>
     * <b>★这里一个字节都不写</b>：开面板是纯客户端手势；写档只发生在面板里那枚开关的
     * {@code onMousePressed} → 动作码 → 服务端这一趟（静态可达链由用例
     * {@code config_panel_action_reaches_guard} 逐跳钉）。
     */
    private static final class UpgradeCellSlot extends ItemSlot {

        private final NekoPocketPanel ui;
        private final int cellIndex;

        UpgradeCellSlot(NekoPocketPanel ui, int cellIndex) {
            this.ui = ui;
            this.cellIndex = cellIndex;
        }

        @Override
        public Interactable.Result onMousePressed(int mouseButton) {
            if (mouseButton == 0 && ui.upgradeCellFilled(cellIndex) && cursorIsEmpty()) {
                if (ui.openUpgradeConfig(cellIndex, this)) {
                    return Interactable.Result.SUCCESS;
                }
            }
            return super.onMousePressed(mouseButton);
        }

        /** 游标是否为空（★非空 = 玩家正拿着东西，那一次左键是放置意图，必须让位给 R95 的固化手势）。 */
        private boolean cursorIsEmpty() {
            final ItemStack carried = ui.syncManager()
                .getCursorItem();
            return carried == null || carried.stackSize <= 0;
        }
    }

    /**
     * ★R81③：右段的一条<b>常驻</b>绑定行（不悬停就能看见）。
     * <p>
     * 行 0 整幅（108）。★R95 S4 起 {@link #PERSISTENT_ROWS} = 1 ⇒ <b>只有行 0 被画</b>；"行 1 起让出
     * 左边 18+4 ⇒ 86"那条让位分支随行 3 改装升级格而不再被走到（保留在代码里：行位一旦回调，
     * 同一段循环不用重写）。行 0 的内容从"只有短码身份"升成<b>整条位置行</b>（R93-③）；
     * "另有 n 条不在服务口径内"（{@code bind.inert}）在<b>绑定按钮 tooltip</b>（逐点账见用例
     * {@code resident_text_pixel_budget}）。
     * ★这里短一分都不是删信息，而是把"有几条 / 绑的是谁"从悬停面搬到常驻面 —— 取证记录 §3 的
     * "常驻行数 = 0"就是本缺陷的加重项。
     * <p>
     * ★★R92-⑥：文本一律 {@code scale(RESIDENT_TEXT_SCALE)}（与读数同口径）。★R93-③ 重算：行 0 的盒
     * 是 108×18、正文是整条位置行（最坏 216 逻辑像素）⇒ 0.6 折两行 = 12px ≤ 18；行 1 的盒仍是 86×18、
     * 正文仍是"另有 n 条"（164 逻辑像素 ⇒ 两行 12px）。★两个数都由用例逐点核，不靠这句注释。
     */
    private static IWidget persistentBindRow(NekoPocketPanel ui, int slot) {
        final boolean fullWidth = slot == 0;
        final TextWidget row = new TextWidget(IKey.dynamic(() -> ui.bindPersistentText(slot)));
        // ★逐条语句设定，不做链式：TextWidget 的 pos/size 继承自 IPositioned，链式下来拿到的是接口，
        // 上面那个 name(...) 就找不到符号（compileJava 直接红，比留一棵没名字的 widget 树好）
        row.textAlign(Alignment.CenterLeft);
        // ★★R92-⑥：常驻绑定行是<b>数据读数</b>（元件短码 + 位置）
        row.scale(PocketGhostRequest.RESIDENT_TEXT_SCALE);
        row.color(PocketGhostRequest.readoutTextColor());
        row.shadow(Boolean.TRUE);
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
     * 左位</b>（{@code x = 4}，与绑定格同列），★R95 S4 起右边那 90px 是五个升级插件格
     * （原常驻绑定行 1 撤除）—— 行位不再靠"留白行"存在，纵向四行 × 18 = 72 一格都不富余。
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

package com.miaokatze.gtit.gui.pocket;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

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
 * 升级<b>配置面板</b>：主面板之上的次级面板，五个插件格的左键都开<b>这一个</b>（R96 S2，P-3）。
 *
 * <h2>为什么是一个面板而不是五套</h2>
 * P-3 的字面是"五个已安装插件格左键一律打开一个配置面板"。本件因此把<b>结构</b>定成"一行一型、五行常驻"，
 * 左键只决定"开哪一面"（同一面），不决定"造哪一面"。MUI2 2.3.88 的 {@code SecondaryPanel} 会<b>缓存</b>
 * 首次构建的面板（{@code openPanel()} 里 {@code panel != null} 就直接开），而同一版本<b>没有 widget
 * 可见性 API</b>（{@code NekoVMGuiV2:997-1000} 已实测记过这条）⇒ 结构一旦按"点击的型"变形，第二次点别的格
 * 拿到的还是第一型那一套。"五行常驻"把这条库限制一次消化掉：面板不需要按型重建。
 *
 * <h2>★按型分派不被写死成五型同形</h2>
 * 每一行的内容由 {@link #sectionsOf(PocketUpgradeType)} 这张<b>唯一分派表</b>决定：五型都有开关段；
 * 只有磁力与魔法使各带一段<b>挂载位</b>（{@link Section#MOUNT_MAGNET} / {@link Section#MOUNT_MAGE} ——
 * S7 / S9 的完整配置内容就填进那两个框，本轮把位画出来并钉住型）。容量 / 堆叠 / 通道持续化三型
 * <b>只有开关</b> ⇒ 面板给它们的就是那一枚开关，不给空框。
 * 分派表对 {@link PocketUpgradeType} 的五个值<b>逐个点名</b> ⇒ "追加第 6 型"时必须在这里表态，
 * 不能靠一条 {@code default} 把"只有开关"变成新形状的默认形状。
 * 这条是"五型同形"的唯一反证面，由用例 {@code config_panel_dispatch_is_not_five_identical} 钉。
 *
 * <h2>三块职责（装配面与数据面分开，数据面必须能在纯 JVM 真跑）</h2>
 * <ol>
 * <li>{@link #build} = 纯装配：只画控件、只发码，★一个字节都不写档；</li>
 * <li>{@link #encode} / {@link #typeOfArg} / {@link #offOfArg} = 动作码 arg 的编解码（双端同一条式子，
 * 越界一律丢弃）；</li>
 * <li>{@link #commitSwitch} = ★<b>服务端唯一的开关写腿</b>：三判里的"该型在档上"与"关闭守卫"两判
 * 加落档全在这一条腿里。它之所以住在本类而不是 {@link NekoPocketServerHandler}：
 * <b>handler 需要 {@code NekoPocketPanel}，而面板在纯 JVM 里造不出来</b> ⇒ 判据留在 handler 里就只能
 * "钉文本"，正是 R57/C3 那族"用例全绿、实机什么都没发生"。抽成静态纯函数后，三种拒绝与两种成交
 * 都能在 JVM 跑出来（用例 {@code switch_write_refuses_three_cases}）。
 * 第三判"持有者本人 + 载体身份"仍归 handler（它要读 {@code Container} 与 {@code EntityPlayer}）。</li>
 * </ol>
 *
 * <h2>★客户端读的是什么、以及为什么没有第二条通道</h2>
 * 行上的读数来自 {@code NekoPocketPanel#carrierStackLive()} —— <b>载体栈的 NBT</b>：服务端读权威栈、
 * 客户端读 vanilla 槽同步过来的那份镜像（与 R95 S4 的 {@code channelPersistActive} 客户端腿<b>同一条通道</b>，
 * off-mask 本身就住在这个根里）。本轮<b>不</b>为开关另开同步键：给同一个布尔造第二个读数面，就是
 * EVA-1 方向丙 DP2「零新通道」要避免的那件事（{@code registerSyncValues} 因此一处未加）。
 * ★代价照实登记：镜像是否 ≤1 tick 到达属实机项（V-2 / V-8）；点完按钮<b>立刻</b>能看到的反馈是回执
 * （走既有 {@code SYNC_RECEIPT}），那条不依赖镜像 ⇒ "切了开关但什么都不动"这一族被回执腿挡住。
 *
 * <h2>几何账（★硬顶，装配期自断言）</h2>
 * ★R96 S7b 起是 {@code 370 × 312}（旧 {@code 348 × 308} 随布局重排作废）：宽 =
 * {@code 6 + 行带 242 + 6 + 磁力格盘 110 + 6}，高 = 左右两列里更高那一列 + 4 + 回执行 + 关闭钮 + 边距。
 * 两个方向都<b>严格小于</b>主面板的 {@code 398 × 360}，也小于次级面板自己的判据顶
 * （宽 ≤ {@link #MAX_WIDTH}=380、高 ≤ {@link #MAX_HEIGHT}=340）。
 * <p>
 * <b>★为什么行带不再是全宽、格盘为什么必须与它并排</b>（一条算术，不是口味）：磁力名单盘是
 * {@code 12 行 × 18 = 216} 高的硬形状（本片验收 5 就钉"行数×18 ≤ 可用高、列数×18 ≤ 可用宽"），
 * 而顶只有 340；五行带 {@code 5×20=100} + 盘面 216 + 框标题 20 + 回执行 12 + 关闭 16 + 两个边距 12
 * = <b>376 高</b> ⇒ ★竖着叠<b>结构性放不下</b>，只能横着并排（横向换纵向）。并排之后行带只占左列，
 * 型名可用宽 = {@link #LABEL_WIDTH}=174（英文最坏串在 0.6 档要 168：static 块钉下界、用例逐型量真值）。
 * <p>
 * ★两个挂载框在 S7b 之后<b>各走各的</b>：磁力框在右列顶到面板上边（高 = 标题 + 216 盘面），
 * 魔法使框仍在左列的挂载框栈里（{@link #mountY(int)}）。纵向预算因此由 {@link #HEIGHT} 那条
 * {@code max(两列)} 给，★不再用"框数 × MOUNT_HEIGHT"那条乘式（两片既不同高又不同列时，那条乘式就是假账）。
 * <p>
 * ★为什么必须留余量而不是贴边：非主面板在 MUI2 里<b>恒可拖</b>（{@code ModularPanel.isDraggable()}
 * 的实现就是"不是主面板就可拖"），拖动走 {@code DraggablePanelWrapper:49-50}，那里按"可视面减面板"的
 * 余量做除算 ⇒ 面板一旦等于可视面就是除零/负数。主面板那条 {@code HEIGHT != 360} 的断言一字未动
 * （本件的顶是<b>另立</b>的常数，不搬它的账）。
 *
 * <h2>不进 Container、不进同步值</h2>
 * 面板里<b>不</b>放 {@code ItemSlot}/{@code SlotGroupWidget}，也不用任何 {@code syncValue}：
 * MUI2 的 {@code SecondaryPanel.openPanel()} 对"树里有同步值、面板本身没被同步"直接抛
 * {@code IllegalArgumentException}（库实测形体）⇒ 本件零同步值，从形状上避开那条抛，而不是靠"运行期
 * 别再点一次"。它因此也不占 {@code PocketSlots.TOTAL_REAL_SLOTS} 那条真实槽账（225 一字未动，
 * ★不变量 G1：本件不新增 {@code registerFactory}）。
 * <p>
 * <b>★R96 S7b 的名单格盘如何继续守住这两条</b>：72 格用的是 {@link NekoMagnetGhostCell}
 * （{@code extends ButtonWidget} 并实现 {@code RecipeViewerGhostIngredientSlot}），★显示侧不进 Container ⇒
 * 守恒 225 零冲突；名单的同步载体是<b>主面板</b>上的一枚 {@code StringSyncValue}
 * （{@code NekoPocketPanel#SYNC_MAGNET}），本面板里的控件只读面板那侧的<b>双源访问器</b>
 * （{@code magnetEntryKeyAt/magnetMode/magnetTarget/magnetEntryCount}），★一个同步值都不往本树里挂 ⇒
 * 那条 {@code IllegalArgumentException} 仍然结构性不可达。写一律发码/发键到服务端，★客户端零写入。
 */
public final class PocketConfigPanel {

    /** 次级面板名（MUI2 的面板标识与调试树；<b>不是</b>同步键，本件零同步值）。 */
    public static final String PANEL_NAME = "gtit.pocket.config";

    /** 外边距（与主面板同一档，两把尺子才对得上）。 */
    public static final int MARGIN = 6;
    /** 行高：一行 = 一个 18px 控件 + 2px 缝。 */
    public static final int ROW_HEIGHT = 20;
    /** 开关按钮的宽（"关闭 / 打开 / 未装"三态在常驻档下都要放得下）。 */
    public static final int SWITCH_WIDTH = 66;
    /** 开关按钮的高。 */
    public static final int SWITCH_HEIGHT = 18;
    /**
     * 左列宽 —— ★R96 S7b 起它是<b>开关行带</b>的宽度（型名 + 缝 + 开关），不再是"挂载列的 x 偏移来源"。
     * <p>
     * ★为什么必须从 156 加宽到这里（这不是口味，是一条算术）：磁力的 72 格盘是
     * {@code 12 行 × 18 = 216} 高的硬形状，而次级面板的高顶是 {@link #MAX_HEIGHT}=340。五行带占
     * {@code 5×20=100}，回执行 + 关闭按钮 + 边距占 {@code 12+16+12=40}，加起来
     * {@code 100 + 216 + 40 = 356} <b>已经越过 340</b>，再加挂载框的标题行只会更糟
     * ⇒ ★72 格盘<b>结构上不可能</b>排在五行带<b>之下</b>，只能与它<b>并排</b>（横向换纵向）。
     * 并排之后行带只剩左列可用，而左列必须容得下"型名 + 开关"：英文最坏串（"Channel Persistence
     * Upgrade Module：Not installed"）的逻辑宽 280 在 0.6 档要 168px，加 2px 缝与 66px 开关 ⇒
     * 本常数的下界就是 {@code 168 + 2 + 66}。★那个 168 不是这里自证的（本件只钉下界），
     * 真串真宽由用例 {@code config_panel_geometry_within_secondary_caps} 拿两份 lang 逐型算。
     */
    public static final int SWITCH_COLUMN = 242;
    /** 型名行的可用宽（★单源：装配侧与像素账用例读的是同一个数，★不是"面板宽减两个边距"那种近似式）。 */
    public static final int LABEL_WIDTH = SWITCH_COLUMN - 2 - SWITCH_WIDTH;
    /**
     * 挂载框的宽（★R96 S7b 起它是<b>左列</b>那一格挂载框的宽 —— 魔法使的 S9 内容落在这里）。
     * 它必须不大于 {@link #SWITCH_COLUMN}，否则左列放不下（static 块对账）。
     */
    public static final int MOUNT_COLUMN = 174;
    /** 列间距。 */
    public static final int COLUMN_GAP = 6;
    /** 一个挂载框的高。 */
    public static final int MOUNT_HEIGHT = 80;
    /** 两个挂载框之间的缝。 */
    public static final int MOUNT_GAP = 4;
    /** 回执行的高。 */
    public static final int RECEIPT_HEIGHT = 12;
    /**
     * ★R96 S9b：魔法使挂载框里<b>一行模式控件</b>的高（= 一枚按钮的高，与 {@link #SWITCH_HEIGHT} 同档）。
     */
    public static final int MODE_ROW_HEIGHT = 18;
    /** ★R96 S9b：两行模式控件之间的缝。 */
    public static final int MODE_ROW_GAP = 1;
    /** ★R96 S9b：模式控件那枚小按钮的宽（只装"关掉 / 打开"两枚二字串，★不装三态 —— 三态在行首读数里）。 */
    public static final int MODE_BUTTON_WIDTH = 30;
    /** ★R96 S9b：挂载框标题占的高（= {@link #ROW_HEIGHT}，标题与开关列同一把尺）。 */
    public static final int MOUNT_TITLE_HEIGHT = ROW_HEIGHT;
    /**
     * ★R96 S9b：模式行里<b>标签</b>的宽（派生：挂载框可用宽 − 2px 缝 − 那枚小按钮，★不手抄）。
     */
    public static final int MODE_LABEL_WIDTH = MOUNT_COLUMN - 2 - 2 - MODE_BUTTON_WIDTH;
    /** 关闭按钮的高与宽。 */
    public static final int CLOSE_HEIGHT = 16;
    /** 关闭按钮的宽。 */
    public static final int CLOSE_WIDTH = 56;
    /** 挂载框的个数 = 分派表里有内容段的型数（本轮两条：S7 磁力、S9 魔法使）。★声明在 {@link #HEIGHT} 之前：纵向预算要读它。 */
    public static final int MOUNT_FRAMES = 2;

    // ==== ★R96 S7b：磁力 72 格盘 + 三态/目标控件的几何（★12×6 = 108×216 是硬形状，逼出上面的并排布局） ====

    /** 名单格盘的单格边长（★与主面板三栏同一把尺，不另立 16/20 的第二档）。 */
    public static final int CELL = NekoPocketPanel.GRID;
    /** 名单格盘的列数（★单源取数据层常量，不在 GUI 侧抄第二份 6）。 */
    public static final int MAGNET_COLUMNS = PocketConstants.MAGNET_FILTER_COLUMNS;
    /** 名单格盘的行数（★同上；朝向 = 12 行 × 6 列，与源质格同形但★不引它那两个常量）。 */
    public static final int MAGNET_ROWS = PocketConstants.MAGNET_FILTER_ROWS;
    /** 盘面宽 = {@code 6 × 18 = 108}。 */
    public static final int MAGNET_GRID_WIDTH = MAGNET_COLUMNS * CELL;
    /** 盘面高 = {@code 12 × 18 = 216}（★本片整个布局重排就是被这一个数逼出来的，见 SWITCH_COLUMN 那段算术）。 */
    public static final int MAGNET_GRID_HEIGHT = MAGNET_ROWS * CELL;
    /** 磁力挂载框的宽（盘面 + 左右各 1px 框边）。 */
    public static final int MAGNET_FRAME_WIDTH = MAGNET_GRID_WIDTH + 2;
    /** 磁力挂载框的高（标题行 + 盘面 + 上下各 1px 框边）。 */
    public static final int MAGNET_FRAME_HEIGHT = ROW_HEIGHT + MAGNET_GRID_HEIGHT + 2;
    /** 磁力挂载框的 x（★右列：与五行带<b>并排</b>，不是排在它下面）。 */
    public static final int MAGNET_FRAME_X = MARGIN + SWITCH_COLUMN + COLUMN_GAP;
    /** 磁力挂载框的 y（★顶到面板上边，因为行带已经让给左列了）。 */
    public static final int MAGNET_FRAME_Y = MARGIN;
    /** 盘面原点的 x（框内 1px 边）。 */
    public static final int MAGNET_GRID_X = MAGNET_FRAME_X + 1;
    /** 盘面原点的 y（框边 + 标题行）。 */
    public static final int MAGNET_GRID_Y = MAGNET_FRAME_Y + ROW_HEIGHT;
    /** 磁力控制块（三态按钮 / 目标按钮 / 清空 / 读数）的 x —— 排左列。 */
    public static final int MAGNET_CONTROL_X = MARGIN;
    /** 磁力控制块的 y 起点 = 五行带之下。 */
    public static final int MAGNET_CONTROL_Y = MARGIN + PocketUpgradeType.values().length * ROW_HEIGHT + 4;
    /** 控制块里一行的按钮宽（两只并排 + 10px 缝 ≤ {@link #SWITCH_COLUMN}）。 */
    public static final int MAGNET_BUTTON_WIDTH = 110;
    /** 控制块里一行的按钮高。 */
    public static final int MAGNET_BUTTON_HEIGHT = SWITCH_HEIGHT;
    /** 控制块行间距。 */
    public static final int MAGNET_ROW_GAP = 4;
    /** 计数读数行的宽（控制块第一行的第二列）。 */
    public static final int MAGNET_COUNT_WIDTH = SWITCH_COLUMN - MAGNET_BUTTON_WIDTH - 10;
    /** ★三态不对称说明行的高（24 = 两行 × 10px 的账，被 {@code magnet_panel_geometry_closes} 那条用例量）。 */
    public static final int MAGNET_NOTE_HEIGHT = 24;

    /** 面板宽 = {@code 6 + 242 + 6 + 110 + 6} = <b>370</b>（★派生自列宽，不手抄）。 */
    public static final int WIDTH = MARGIN + SWITCH_COLUMN + COLUMN_GAP + MAGNET_FRAME_WIDTH + MARGIN;
    /**
     * 面板高 = {@code max(左列栈下沿, 磁力格盘下沿) + 4 + 回执 12 + 关闭 16 + 边距 6} = <b>312</b>。
     * <p>
     * ★两列各算各的：左列 = 五行带 + 磁力控制块 + 最后一个挂载框（S9 的魔法使），右列 = 磁力框。
     * 谁高听谁（★不再用"两个挂载框 × MOUNT_HEIGHT"那条旧加总式 —— S7b 之后两个框既不同高也不同列，
     * 拿一条乘式当账就是假账）。
     */
    public static final int HEIGHT = Math.max(
        mountY(MOUNT_FRAMES - 1) + MOUNT_HEIGHT,
        MAGNET_FRAME_Y + MAGNET_FRAME_HEIGHT) + 4 + RECEIPT_HEIGHT + CLOSE_HEIGHT + MARGIN;

    /** 次级面板的宽度硬顶（见类 javadoc 的几何账）。 */
    public static final int MAX_WIDTH = 380;
    /** 次级面板的高度硬顶。 */
    public static final int MAX_HEIGHT = 340;

    /**
     * 一行的<b>内容段</b>（★按型分派，"五型同形"的反证面）。{@link #SWITCH} 是公共段，
     * 两段 {@code MOUNT_*} 只有磁力与魔法使有。
     */
    public enum Section {
        /** 开 / 关切换（五型都有）。 */
        SWITCH,
        /** 磁力的配置内容挂载位（S7：吸取目标 / 三态名单 / 12×6 格）。 */
        MOUNT_MAGNET,
        /** 魔法使的配置内容挂载位（S9：四模式 / 元素容量 / 充能读数）。 */
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
        if (WIDTH > MAX_WIDTH || HEIGHT > MAX_HEIGHT) {
            throw new IllegalStateException(
                "[pocket] 配置面板越过次级面板硬顶: " + WIDTH + "x" + HEIGHT
                    + " 顶=" + MAX_WIDTH + "x" + MAX_HEIGHT
                    + "（★非主面板恒可拖，DraggablePanelWrapper 按可视面余量做除算 ⇒ 贴边即除零/负数）");
        }
        if (WIDTH >= NekoPocketPanel.WIDTH || HEIGHT >= NekoPocketPanel.HEIGHT) {
            throw new IllegalStateException(
                "[pocket] 配置面板不再严格小于主面板: " + WIDTH + "x" + HEIGHT + "，主面板="
                    + NekoPocketPanel.WIDTH + "x" + NekoPocketPanel.HEIGHT
                    + "（盖满主面板的次级面板读起来就是一块新屏，且拖动余量归零）");
        }
        if (SWITCH_COLUMN < SWITCH_WIDTH + 18) {
            throw new IllegalStateException("[pocket] 左列放不下开关（行带的 x 偏移会压到按钮上）: " + SWITCH_COLUMN);
        }
        // ★型名可用宽的下界（★这里只钉"放得下最坏串的下界"这件事被写死在常数上，真串真宽由用例逐型算；
        // 上一手把它写成"全宽 − 两边距 − 开关"的近似式，S7b 把行带收窄到左列之后那条近似式会<b>虚高</b>
        // ⇒ 用例改读 {@link #LABEL_WIDTH}，本条钉它不许被谁悄悄缩回去）。
        if (LABEL_WIDTH < 168) {
            throw new IllegalStateException(
                "[pocket] 型名行可用宽顶不住英文最坏串（0.6 档要 168px）: LABEL_WIDTH=" + LABEL_WIDTH
                    + "，SWITCH_COLUMN=" + SWITCH_COLUMN);
        }
        if (LABEL_WIDTH + 2 + SWITCH_WIDTH > SWITCH_COLUMN) {
            throw new IllegalStateException(
                "[pocket] 行带横向不闭合（型名 + 缝 + 开关顶出左列）: " + SWITCH_COLUMN);
        }
        // ★横向闭合（与下面那条纵向闭合同一条纪律：{@link #WIDTH} 是派生式，有人手改成字面量时先在这里红，
        // 而不是等到屏幕上挂载框被裁掉）。★S7b 起右列是磁力格盘、左列是行带 + 挂载框栈。
        if (MAGNET_FRAME_X + MAGNET_FRAME_WIDTH > WIDTH - MARGIN) {
            throw new IllegalStateException(
                "[pocket] 配置面板横向不闭合（磁力格盘顶出面板）: " + WIDTH + "，格盘右沿="
                    + (MAGNET_FRAME_X + MAGNET_FRAME_WIDTH));
        }
        if (MOUNT_COLUMN > SWITCH_COLUMN) {
            throw new IllegalStateException(
                "[pocket] 挂载框宽顶出行带列: MOUNT_COLUMN=" + MOUNT_COLUMN + " > SWITCH_COLUMN=" + SWITCH_COLUMN);
        }
        if (mountY(MOUNT_FRAMES - 1) + MOUNT_HEIGHT > HEIGHT - MARGIN) {
            throw new IllegalStateException("[pocket] 配置面板纵向不闭合（左列挂载框顶出面板）: " + HEIGHT);
        }
        // ★磁力格盘的纵向闭合 + <b>盘面本身必须真装得下 12×6 的 18px 栅格</b>（★本片验收 5 的算术落点：
        // 列数×18 ≤ 可用宽、行数×18 ≤ 可用高，两个方向都留 1px 框边）。
        if (MAGNET_FRAME_Y + MAGNET_FRAME_HEIGHT > HEIGHT - MARGIN) {
            throw new IllegalStateException("[pocket] 磁力格盘纵向不闭合（顶出面板）: " + HEIGHT);
        }
        if (MAGNET_GRID_WIDTH > MAGNET_FRAME_WIDTH - 2 || MAGNET_GRID_HEIGHT > MAGNET_FRAME_HEIGHT - ROW_HEIGHT - 2) {
            throw new IllegalStateException(
                "[pocket] 72 格盘放不下自己的框: 盘面=" + MAGNET_GRID_WIDTH + "x" + MAGNET_GRID_HEIGHT
                    + " 框内=" + (MAGNET_FRAME_WIDTH - 2) + "x" + (MAGNET_FRAME_HEIGHT - ROW_HEIGHT - 2));
        }
        if (MAGNET_COLUMNS * CELL != MAGNET_GRID_WIDTH || MAGNET_ROWS * CELL != MAGNET_GRID_HEIGHT) {
            throw new IllegalStateException("[pocket] 栅格乘式与盘面尺寸不符（★格数不得是裸字面量）");
        }
        if (MAGNET_ROWS * MAGNET_COLUMNS != PocketConstants.MAGNET_FILTER_SLOTS) {
            throw new IllegalStateException("[pocket] 磁力格盘行列乘积不等于数据层的条目预算");
        }
        // ★磁力控制块（三态 / 目标 / 清空 / 不对称读数）整块必须排在行带之下、最后一个挂载框之上：
        // 反过来（叠到挂载框上）在纯 JVM 里不会崩、只会在屏幕上叠字，所以这条只能钉成装配期断言。
        if (MAGNET_CONTROL_Y < MARGIN + PocketUpgradeType.values().length * ROW_HEIGHT) {
            throw new IllegalStateException("[pocket] 磁力控制块压在五行带上: " + MAGNET_CONTROL_Y);
        }
        if (magnetNoteY() + MAGNET_NOTE_HEIGHT > mountY(1)) {
            throw new IllegalStateException(
                "[pocket] 磁力控制块顶进魔法使挂载框: 控制块下沿=" + (magnetNoteY() + MAGNET_NOTE_HEIGHT)
                    + "，挂载框 y=" + mountY(1));
        }
        if (MAGNET_CONTROL_X + 2 * MAGNET_BUTTON_WIDTH + 10 > MARGIN + SWITCH_COLUMN) {
            throw new IllegalStateException(
                "[pocket] 控制块两只按钮顶出行带列: " + (2 * MAGNET_BUTTON_WIDTH + 10) + " > " + SWITCH_COLUMN);
        }
        if (PocketUpgradeType.values().length * ROW_HEIGHT > HEIGHT - MARGIN) {
            throw new IllegalStateException("[pocket] 开关列放不下五行: " + HEIGHT);
        }
        // ★R96 S9b：魔法使挂载框里的<b>模式行</b>必须落在框内（框高由 {@link #MOUNT_HEIGHT} 给，
        // 而框高又进 {@link #HEIGHT} 的纵向预算 ⇒ 这一条红的同时会带走上面那条"挂载列顶出面板"，
        // 所以单独写一条，报的是"行多了"而不是"面板高了"）。
        if (MOUNT_TITLE_HEIGHT + modeRowCount() * (MODE_ROW_HEIGHT + MODE_ROW_GAP) > MOUNT_HEIGHT) {
            throw new IllegalStateException("[pocket] 魔法使挂载框放不下 " + modeRowCount() + " 行模式控件: 框高="
                + MOUNT_HEIGHT + "，需要=" + (MOUNT_TITLE_HEIGHT + modeRowCount() * (MODE_ROW_HEIGHT + MODE_ROW_GAP)));
        }
        if (MODE_LABEL_WIDTH < 40 || MODE_LABEL_WIDTH + MODE_BUTTON_WIDTH + 2 > MOUNT_COLUMN - 2) {
            throw new IllegalStateException(
                "[pocket] 模式行横向不闭合（标签 + 按钮顶出挂载框）: 标签=" + MODE_LABEL_WIDTH + "，按钮="
                    + MODE_BUTTON_WIDTH);
        }
        // ★分派表与挂载框数必须对得上：对不上就是"有人改了分派表而没改纵向预算"。
        int mounts = 0;
        for (final PocketUpgradeType type : PocketUpgradeType.values()) {
            if (hasMount(type)) {
                mounts++;
            }
        }
        if (mounts != MOUNT_FRAMES) {
            throw new IllegalStateException(
                "[pocket] 分派表里有挂载位的型数 = " + mounts + "，纵向预算按 " + MOUNT_FRAMES + " 个框算");
        }
    }

    // ================================================================== 数据面（纯 JVM 可跑）

    /**
     * ★唯一分派表：这一型的面板由哪些内容段组成。
     * <p>
     * 五个值<b>逐个点名</b>（不写成 {@code default} 一把梭）就是这条判据的全部内容：容量 / 堆叠 /
     * 通道持续化 = 只开关（P-3 的字面"只需开关的项其面板里就只放开关"），磁力 = 开关 + 名单挂载位，
     * 魔法使 = 开关 + 元素挂载位。若这里退化成"五型同形"，S7/S9 的完整配置就失去了宿主，
     * 而这条退化<b>不会</b>让编译或任何既有读数变红 —— 所以它由用例钉住而不是由注释自证。
     */
    public static List<Section> sectionsOf(PocketUpgradeType type) {
        if (type == null) {
            return Collections.singletonList(Section.SWITCH);
        }
        switch (type) {
            case CAPACITY:
                return Collections.singletonList(Section.SWITCH);
            case STACK:
                return Collections.singletonList(Section.SWITCH);
            case MAGNET:
                return Arrays.asList(Section.SWITCH, Section.MOUNT_MAGNET);
            case CHANNEL_PERSIST:
                return Collections.singletonList(Section.SWITCH);
            case MAGE:
                return Arrays.asList(Section.SWITCH, Section.MOUNT_MAGE);
            default:
                // 走不到（上面已穷举五个值）。留这一支的意义：将来追加第 6 型而忘了在上面表态时，
                // 这里给的是"与开关同形"的最小安全回落，同时 static 块的 mounts 对账会先一步炸红。
                return Collections.singletonList(Section.SWITCH);
        }
    }

    /** 这一型是否有挂载位（★由 {@link #sectionsOf} 派生，别处不得再写一遍型清单）。 */
    public static boolean hasMount(PocketUpgradeType type) {
        return sectionsOf(type).size() > 1;
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

    // ========================================================== ★R96 S9b：魔法使挂载框里的「四模式」数据面
    //
    // ★★四模式 = ①「魔法使」主开关（本件第 {@link Section#SWITCH} 段，S1/S2 已定稿，一条码都没加）
    // ②结晶模式 ③猫猫币充能 ④源质转换。后三条住在本段，位序单源在
    // {@link PocketConstants#MAGE_MODE_BITS}（★本件不写第二份位清单，见 {@link #modeBit(int)}）。
    //
    // ★为什么不与磁力那段共用一套硬编码：磁力的内容段（S7b 会填名单格与三态）与这里的"三条 on/off 行"
    // 只有"都装在 MOUNT_* 框里"这一件事相同，判据、动作码空间与几何全不同 ⇒ 本件把<b>框</b>留给
    // {@link #mountFrame}，把<b>框里的内容</b>按 {@link Section} 分派到 {@link #mountContent}
    // （磁力 ⇒ 既有的 pending 文本件；魔法使 ⇒ {@link #mageModeRows}）。S7b 落地时换掉的是它自己那一支。

    /** 模式行的<b>标签</b>键（★按 {@link PocketConstants#MAGE_MODE_BITS} 的行序，两个下标空间同一个数）。 */
    private static final String[] MODE_LABEL_KEYS = { "gtit.pocket.config.mode.crystal",
        "gtit.pocket.config.mode.coin", "gtit.pocket.config.mode.transmute" };
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

    /** 第 {@code row} 行的 y（★挂载框<b>内</b>坐标系，标题之下第一行；单源，调用点不写第二次）。 */
    public static int modeRowY(int row) {
        return row * (MODE_ROW_HEIGHT + MODE_ROW_GAP);
    }

    /** 挂载框内容区的 x（★框左沿 + 1px，与 {@link #mountFrame} 里标题用的那个 1 同一个数）。 */
    public static int mountContentX() {
        return MARGIN + SWITCH_COLUMN + COLUMN_GAP + 1;
    }

    /** 第 {@code frame} 个挂载框内容区的 y（★框顶 + 标题高 ⇒ 内容段与框之间不留第二份偏移表）。 */
    public static int mountContentY(int frame) {
        return mountY(frame) + MOUNT_TITLE_HEIGHT;
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
    public static Outcome commitSwitch(ItemStack carrier, PocketUpgradeType type, boolean wantOff,
        PocketInventory inv, ItemStack cursor) {
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
                return verdict.reason() == PocketUpgradeGuards.Reason.CAPACITY_OVER_OFF_LIMIT
                    ? Outcome.REFUSE_CAPACITY
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
        final List<String> keys = new ArrayList<>(Arrays.asList(
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
            "gtit.pocket.config.mount.pending",
            // ★R96 S9b：魔法使挂载框里那三行模式控件的文案（三条标签 + 一条共用 tooltip）。
            // ★开关按钮与行首读数★不★新增键：复用上面那四条既有的 turn_on / turn_off / state.on / state.off
            // ——模式与开关在玩家侧读起来就是同一件事（"这一条动还是不动"），两套字面量才是第二份真相。
            "gtit.pocket.config.mode.crystal",
            "gtit.pocket.config.mode.coin",
            "gtit.pocket.config.mode.transmute",
            MODE_HINT_KEY,
            "gtit.pocket.upgrade.cell.off",
            identityReceiptKey()));
        for (final Outcome outcome : Outcome.values()) {
            keys.add(receiptKey(outcome));
        }
        // ★R96 S7b：磁力配置面的键（三态标签 ×3 + 三态不对称说明 ×3 + 目标两档标签 ×2 + 三枚提示 +
        // 计数读数 + 清空按钮两行 + 格件四行 + 格件的四条拒收支）。★逐条点名进这张表 ⇒ 两份 lang 的
        // 对账用例（config_panel_dispatch_is_not_five_identical 的④面）会自动把它们一起量，
        // 不需要谁再手工补一遍。
        for (final PocketMagnetFilter.Mode mode : PocketMagnetFilter.Mode.values()) {
            keys.add(modeLabelKey(mode));
            keys.add(noteKeyOf(mode));
        }
        for (final PocketMagnetFilter.Target target : PocketMagnetFilter.Target.values()) {
            keys.add(targetLabelKey(target));
        }
        keys.addAll(Arrays.asList(
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

    // ================================================================== 装配面（客户端控件树）

    /**
     * 构建次级面板（★只画控件与发码，不写档、不跑守卫）。
     * <p>
     * <b>树形恒定</b>（R32 同一条纪律的次级面板版）：行数 = {@code PocketUpgradeType.values().length}、
     * 挂载框数 = {@link #MOUNT_FRAMES}、回执行与关闭按钮各一枚，全部<b>不随</b>"哪一型开着"变化；
     * 状态只进 {@code IKey.dynamic} 的内容层 ⇒ 原位换字，不重排、不增删节点。
     * <p>
     * <b>装配期不执行点击谓词</b>：{@code onMousePressed} 在这里只是登记 lambda，服务端那次
     * {@code assemble()} 调用不会因此跑任何开关判定，也不会多出一条写腿。
     */
    public static ModularPanel build(NekoPocketPanel ui) {
        final ModularPanel panel = ModularPanel.defaultPanel(PANEL_NAME, WIDTH, HEIGHT);
        panel.child(receiptLine(ui));
        panel.child(closeButton(ui));
        final PocketUpgradeType[] types = PocketUpgradeType.values();
        for (int row = 0; row < types.length; row++) {
            panel.child(typeLabel(ui, types[row], row));
            panel.child(switchButton(ui, types[row], row));
        }
        // ★R96 S7b：磁力控制块（三态循环 / 吸取目标两档 / 清空 / 计数 / 不对称读数）排在<b>左列</b>行带之下，
        // 72 格盘在<b>右列</b>的挂载框里（下面那个循环画）。★常驻树形：这五枚件不随名单条数增删（R32/R41b），
        // 名单变化只进各件的 IKey.dynamic / 内容层。★整块只发码或发键，一个字节都不写本地 NBT。
        panel.child(magnetModeButton(ui));
        panel.child(magnetTargetButton(ui));
        panel.child(magnetClearButton(ui));
        panel.child(magnetCountLine(ui));
        panel.child(magnetNoteLine(ui));
        int frame = 0;
        for (final PocketUpgradeType type : types) {
            // ★挂载框只给"有挂载位"的型画（判据来自分派表，本处不重写第二份型清单）
            if (hasMount(type)) {
                panel.child(mountFrame(ui, type, frame));
                // ★R96 S9b：魔法使的内容段排在框之<b>后</b>（MUI2 的 child 序 = 绘制序 ⇒ 控件画在框面上）。
                // 为什么不在 mountFrame 内部挂：模式控件是<b>面板级</b>的孩子（坐标全走
                // {@link #mountContentX()} / {@link #mountContentY(int)} 的单源式），不是框的 child 树；
                // 而磁力框（S7b）的内容<b>是</b>框的孩子（盘面原点随框走）⇒ 两支的挂载点天然不同，
                // 不是同一条纪律的两个写法。判据仍然只有一张表：下面这个 contains(Section.MOUNT_MAGE)
                // 与 mountFrame 里那一支读的是同一个 sectionsOf，没有出现第二份型清单。
                if (sectionsOf(type).contains(Section.MOUNT_MAGE)) {
                    mageModeRows(ui, panel, frame);
                }
                frame++;
            }
        }
        if (frame != MOUNT_FRAMES) {
            throw new IllegalStateException(
                "[pocket] 分派表画出的挂载框数与几何账不符: " + frame + " vs " + MOUNT_FRAMES);
        }
        return panel;
    }

    /**
     * 第 {@code row} 行的型名 + 现状读数（★动态：型名与状态同一句里，读的是同一个 {@link SwitchState}）。
     * <p>
     * ★★<b>R96 S7b 改布局</b>：行带从"横贯面板的全宽"收成<b>左列</b>那一条。原因是一条算术，逐字写在
     * {@link #SWITCH_COLUMN} 的注释里：磁力 72 格盘的 {@code 12×18=216} 高与五行带 {@code 5×20=100}
     * <b>无法同时</b>塞进次级面板的 340 高顶（还要留标题行、回执行、关闭钮与两个边距）⇒ 只能把格盘
     * 挪到<b>右列、与行带并排</b>，行带因此只占左列。贴左排字，右端留给本行的开关
     * （{@link #switchButton}），两者之间留 2px。
     * <p>
     * ★可用宽度 = {@link #LABEL_WIDTH}（★单源：装配侧与像素账用例读的是同一个数。旧写法是
     * "面板宽 − 两个边距 − 一枚开关"的近似式，行带收窄到左列之后那条式子会<b>虚高</b> ⇒ 用例照它算
     * 永远一行放得下、屏幕上却折出第二行顶穿 20px 行盒 —— 那正是"用例恒绿而画面不对"的形状）。
     * 英文最坏串（"Channel Persistence Upgrade Module：Not installed" 逻辑宽 280）在 0.6 档要 168px，
     * 本件给到 174（真串真宽由 {@code config_panel_geometry_within_secondary_caps} 逐型量）。
     */
    private static IWidget typeLabel(NekoPocketPanel ui, PocketUpgradeType type, int row) {
        final String nameKey = UPGRADE_NAME_KEYS[type.ordinal()];
        return (IWidget) new TextWidget(IKey.dynamic(() -> {
            final SwitchState state = switchState(ui.carrierStackLive(), type);
            return StatCollector.translateToLocal(nameKey) + "："
                + StatCollector.translateToLocal(stateKey(state));
        })).textAlign(Alignment.CenterLeft)
            .scale(PocketGhostRequest.RESIDENT_TEXT_SCALE)
            .color(PocketGhostRequest.readoutTextColor())
            .shadow(Boolean.TRUE)
            .name("pocket_config_label_" + type.ordinal())
            .pos(MARGIN, rowY(row))
            // ★R96 S7b：可用宽单源取 {@link #LABEL_WIDTH}（旧写法是"全宽 − 两边距 − 开关"的近似式，
            // 行带收窄到左列之后那条近似式会虚高 ⇒ 用例与装配侧改读同一个数，见 SWITCH_COLUMN 的算术）。
            .size(LABEL_WIDTH, ROW_HEIGHT);
    }

    /** 第 {@code row} 行的开关（★唯一出口是发码，本地零写入；★排在本行右端，与型名同一横带）。 */
    private static IWidget switchButton(NekoPocketPanel ui, PocketUpgradeType type, int row) {
        // ★x = 左列右端（不是"面板宽 − 边距 − 开关"）：S7b 之后行带只占左列，右列整块给了磁力格盘
        return new ButtonWidget<>().pos(MARGIN + LABEL_WIDTH + 2, rowY(row))
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
            .onMousePressed(button -> button == 0
                && ui.requestUpgradeSwitch(type, nextOff(ui.carrierStackLive(), type)));
    }

    /** 回执行：最近一次切换成了什么 / 为什么被拒（★走既有粘性回执通道，不新建通道）。 */
    private static IWidget receiptLine(NekoPocketPanel ui) {
        return (IWidget) new TextWidget(IKey.dynamic(ui::upgradeConfigReceiptText)).textAlign(Alignment.CenterLeft)
            .scale(PocketGhostRequest.RESIDENT_TEXT_SCALE)
            .color(PocketGhostRequest.hintTextColor())
            .shadow(Boolean.TRUE)
            .name("pocket_config_receipt")
            .pos(MARGIN, HEIGHT - MARGIN - CLOSE_HEIGHT - RECEIPT_HEIGHT)
            .size(WIDTH - 2 * MARGIN - CLOSE_WIDTH - 2, RECEIPT_HEIGHT);
    }

    /** 关闭本面的按钮（★只关面板，不动任何数据：本面无"待提交"状态，每次点开关都是即时单发）。 */
    private static IWidget closeButton(NekoPocketPanel ui) {
        return new ButtonWidget<>().pos(WIDTH - MARGIN - CLOSE_WIDTH, HEIGHT - MARGIN - CLOSE_HEIGHT)
            .size(CLOSE_WIDTH, CLOSE_HEIGHT)
            .name("pocket_config_close")
            .background(PocketGuiTextures.BUTTON)
            .hoverBackground(PocketGuiTextures.BUTTON_PRESSED)
            .overlay(IKey.lang("gtit.pocket.config.close"))
            .playClickSound(true)
            .onMousePressed(button -> button == 0 && ui.closeUpgradeConfig());
    }

    /**
     * 挂载位框（★S7 / S9 的内容落在这里）。★框本身几段共用（标题 + 一块框），<b>框里的内容按
     * {@link Section} 分派</b>。★合并后的三分支（S7b × S9b 各自在旧基线上都对，两侧语义都保留）：
     * <ul>
     * <li>{@link Section#MOUNT_MAGNET} —— ★R96 S7b 已落地：框里是 12×6 = 72 格的名单盘
     * （{@link #magnetGrid}），画完盘立刻 return ⇒ pending 那行<b>结构上到不了</b>磁力框；</li>
     * <li>{@link Section#MOUNT_MAGE} —— ★R96 S9b 已落地：三行模式控件（{@link #mageModeRows}，由
     * {@link #build} 挂在框之<b>后</b>的面板上），加主开关一共四枚控件，正是需求那句"四模式"
     * ⇒ 这一格也<b>不</b>画 pending；</li>
     * <li>其余挂载位（分派表说"有框"但内容还没落地的型）—— 才画既有那行 pending 文本件。
     * ★合并后的五型表里已不存在这样的型 ⇒ 这一支是给未来新挂载位兜底的，不是屏上的占位。</li>
     * </ul>
     * ★分派判据读的是 {@link #sectionsOf}（同一张唯一分派表），★不是"型 == MAGE"那种第二份型清单。
     * <p>
     * <b>★两个框的几何各走各的（S7b 之后它们既不同列、也不同高）</b>：磁力框在<b>右列</b>顶到面板上边、
     * 高 = 标题 + 216 格盘；其余挂载框仍在<b>左列</b>的挂载框栈里（{@link #mountY(int)}）。
     * <p>
     * ★反证归用例：{@code magnet_pending_removed_from_magnet_mount}（S7b 半，钉"格盘在位 + pending
     * 到不了磁力框"+ 合并后补的"pending 也到不了魔法使框"）与
     * {@code mage_config_panel_mode_codec_and_commit_leg}（S9b 半，钉"模式控件装配恰一处 + 占位分支
     * 的判据是裸的『非魔法使』"）两把检法各盯一侧，合成后互不遮蔽。
     */
    private static IWidget mountFrame(NekoPocketPanel ui, PocketUpgradeType type, int frame) {
        final boolean magnet = sectionsOf(type)
            .contains(Section.MOUNT_MAGNET);
        final int x = magnet ? MAGNET_FRAME_X : MARGIN;
        final int y = magnet ? MAGNET_FRAME_Y : mountY(frame);
        final int width = magnet ? MAGNET_FRAME_WIDTH : MOUNT_COLUMN;
        final int height = magnet ? MAGNET_FRAME_HEIGHT : MOUNT_HEIGHT;
        final ParentWidget<?> box = new ParentWidget<>().background(PocketGuiTextures.PANEL)
            .name("pocket_config_mount_" + type.ordinal())
            .pos(x, y)
            .size(width, height);
        final String titleKey = mountTitleKey(type);
        if (titleKey == null) {
            throw new IllegalStateException("[pocket] 分派表说这一型有挂载位，却没有标题键: " + type);
        }
        box.child(
            (IWidget) new TextWidget(IKey.lang(titleKey)).textAlign(Alignment.TopLeft)
                .scale(PocketGhostRequest.RESIDENT_TEXT_SCALE)
                .color(PocketGhostRequest.readoutTextColor())
                .shadow(Boolean.TRUE)
                .name("pocket_config_mount_title_" + type.ordinal())
                .pos(1, 1)
                .size(width - 2, ROW_HEIGHT));
        if (magnet) {
            // ★S7b：磁力框的内容 = 72 格名单盘（盘面原点在框内 1px 边 + 标题行之下，见 MAGNET_GRID_X/Y 的单源式）。
            // ★这一支画完盘立刻 return ⇒ pending 结构上到不了磁力框（magnet_pending_removed_from_magnet_mount 的半①）。
            box.child(magnetGrid(ui, x, y));
            return box;
        }
        final java.util.List<Section> sections = sectionsOf(type);
        // ★R96 S9b（合并后口径）：魔法使框的内容（三行模式控件）不长在本框的 child 树里，而是由
        // {@link #build} 挂在框之后的面板上（{@link #mageModeRows}）⇒ 这一支也<b>不</b>给魔法使画 pending
        // （画了 = 拿占位文案冒充没接上的控件，正是这条检法要防的形状）。仍拿 pending 文本件的是「分派表说
        // 有框、但内容还没落地」的挂载位；本件五型表里已不存在这样的型 ⇒ 它是给未来新挂载位兜底的，
        // 不是屏上的占位。撤不撤该键的推导史写在用例 magnet_pending_removed_from_magnet_mount 的注释里。
        // ★框与内容的坐标全经 {@link #mountContentX()} / {@link #mountContentY(int)}
        // 这两个单源，没有第二份偏移表。
        if (!sections.contains(Section.MOUNT_MAGE)) {
            box.child(
                (IWidget) new TextWidget(IKey.lang("gtit.pocket.config.mount.pending")).textAlign(Alignment.TopLeft)
                    .scale(PocketGhostRequest.RESIDENT_TEXT_SCALE)
                    .color(PocketGhostRequest.hintTextColor())
                    .shadow(Boolean.TRUE)
                    .name("pocket_config_mount_pending_" + type.ordinal())
                    .pos(1, ROW_HEIGHT)
                    .size(width - 2, height - ROW_HEIGHT));
        }
        return box;
    }


    /**
     * ★R96 S9b：魔法使挂载框里的<b>三行模式控件</b>（第四枚控件是主面板那一行的开关，不在这里重复画）。
     * <p>
     * <b>树形恒定</b>（本件类注释那条 R32 纪律在模式行上的延续）：行数 = {@link #modeRowCount()}，
     * 而它派生自 {@link PocketConstants#MAGE_MODE_BITS}，★不随"哪条模式开着"变化 ⇒ 面板那套
     * "首次构建即缓存"的形体不会因为状态改而拿到一棵旧树。
     * <p>
     * <b>零同步值、零本地写</b>：与开关列同一条通道（读载体栈 NBT 的 vanilla 镜像、写只发码），
     * {@code registerSyncValues} 一处未加 ⇒ 给同一个布尔造第二个读数面这件事在模式上也成立。
     * <p>
     * <b>标签 = 模式名 + 现状同一句</b>（{@link #stateKey} 复用的就是开关列那两条既有键，
     * ★模式与开关在玩家侧读起来是同一件事："这一条动还是不动"）。按钮上的字复用
     * {@link #switchLabelKey} 那两条 —— 只是模式没有"未固化"这一态（那一判在 {@link #commitMode} 里
     * 拒写并回一条既有回执，不在按钮上骗人）。
     */
    private static void mageModeRows(NekoPocketPanel ui, ModularPanel panel, int frame) {
        final int rows = modeRowCount();
        for (int index = 0; index < rows; index++) {
            final int row = index;
            final String labelKey = modeLabelKey(row);
            if (labelKey == null) {
                throw new IllegalStateException(
                    "[pocket] 模式行数与标签键表不齐: 第 " + row + " 行没有键（两份清单必须同序）");
            }
            panel.child(
                (IWidget) new TextWidget(IKey.dynamic(
                    () -> StatCollector.translateToLocal(labelKey) + "：" + StatCollector.translateToLocal(
                        stateKey(modeState(ui.carrierStackLive(), row) ? SwitchState.ON : SwitchState.OFF))))
                            .textAlign(Alignment.TopLeft)
                            .scale(PocketGhostRequest.RESIDENT_TEXT_SCALE)
                            .color(PocketGhostRequest.readoutTextColor())
                            .shadow(Boolean.TRUE)
                            .name("pocket_config_mode_label_" + row)
                            .pos(mountContentX(), mountContentY(frame) + modeRowY(row))
                            .size(MODE_LABEL_WIDTH, MODE_ROW_HEIGHT));
            panel.child(
                new ButtonWidget<>().pos(mountContentX() + MOUNT_COLUMN - 2 - MODE_BUTTON_WIDTH,
                    mountContentY(frame) + modeRowY(row))
                    .size(MODE_BUTTON_WIDTH, MODE_ROW_HEIGHT)
                    .name("pocket_config_mode_switch_" + row)
                    .background(PocketGuiTextures.BUTTON)
                    .hoverBackground(PocketGuiTextures.BUTTON_PRESSED)
                    .overlay(IKey.dynamic(() -> StatCollector.translateToLocal(switchLabelKey(
                        modeState(ui.carrierStackLive(), row) ? SwitchState.ON : SwitchState.OFF))))
                    .tooltipDynamic(tooltip -> tooltip.addLine(IKey.lang(MODE_HINT_KEY)))
                    .tooltipAutoUpdate(true)
                    .playClickSound(true)
                    // ★同样只有左键生效，且★这里一个字节都不写档（写腿在服务端 commitMode）
                    .onMousePressed(button -> button == 0
                        && ui.requestUpgradeMode(row, nextModeOn(ui.carrierStackLive(), row))));
        }
    }


    // ================================================================== ★R96 S7b 磁力配置面（格盘 + 三态 / 目标 / 清空 / 读数）

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
     * @param frameX 挂载框在面板里的 x（★盘面的面板坐标 = 框 x + 1 边距，见 {@link #MAGNET_GRID_X}）
     * @param frameY 挂载框在面板里的 y
     */
    private static IWidget magnetGrid(NekoPocketPanel ui, int frameX, int frameY) {
        // 盘面作为一个 ParentWidget 挂在框内（子格坐标 = 相对盘面原点，18px 栅格）
        final ParentWidget<?> grid = new ParentWidget<>().pos(MAGNET_GRID_X - frameX, MAGNET_GRID_Y - frameY)
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
     * {@code carried.stackSize <= 0} 同一读法 ⇒  tooltip 说的与格件做的是<b>同一句话</b>）。
     * ★只读判定，不碰游标本身。
     */
    public static boolean isEmptyCursor(final net.minecraft.item.ItemStack carried) {
        return carried == null || carried.stackSize <= 0 || NekoMagnetGhostCell.identityKeyOf(carried)
            .isEmpty();
    }

    /** ★三态循环按钮（{@code NONE → WHITELIST → BLACKLIST → NONE}，★只有左键，★客户端零写入）。 */
    private static IWidget magnetModeButton(NekoPocketPanel ui) {
        return new ButtonWidget<>().pos(MAGNET_CONTROL_X, MAGNET_CONTROL_Y)
            .size(MAGNET_BUTTON_WIDTH, MAGNET_BUTTON_HEIGHT)
            .name("pocket_magnet_mode_button")
            .background(PocketGuiTextures.BUTTON)
            .hoverBackground(PocketGuiTextures.BUTTON_PRESSED)
            .overlay(IKey.dynamic(
                () -> StatCollector.translateToLocal(modeLabelKey(ui.magnetMode()))))
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
            .overlay(IKey.dynamic(
                () -> StatCollector.translateToLocal(targetLabelKey(ui.magnetTarget()))))
            .tooltipDynamic(tooltip -> tooltip.addLine(IKey.lang("gtit.pocket.magnet.target.hint")))
            .tooltipAutoUpdate(true)
            .playClickSound(true)
            .onMousePressed(button -> button == 0 && ui.requestMagnetTargetCycle());
    }

    /** ★清空名单按钮（★只抹条目、不动三态；与"切到无限制"是两件事，见 {@code PocketMagnetFilter} 三态读法）。 */
    private static IWidget magnetClearButton(NekoPocketPanel ui) {
        return new ButtonWidget<>().pos(
            MAGNET_CONTROL_X + MAGNET_BUTTON_WIDTH + 10,
            MAGNET_CONTROL_Y + MAGNET_BUTTON_HEIGHT + MAGNET_ROW_GAP)
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
        return (IWidget) new TextWidget(IKey.dynamic(() -> String
            .format(
                StatCollector.translateToLocal("gtit.pocket.magnet.count"),
                ui.magnetEntryCount(),
                PocketConstants.MAGNET_FILTER_SLOTS))).textAlign(Alignment.CenterLeft)
            .scale(PocketGhostRequest.RESIDENT_TEXT_SCALE)
            .color(PocketGhostRequest.readoutTextColor())
            .shadow(Boolean.TRUE)
            .name("pocket_magnet_count")
            .pos(MAGNET_CONTROL_X + MAGNET_BUTTON_WIDTH + 10, MAGNET_CONTROL_Y)
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
        return (IWidget) new TextWidget(IKey.dynamic(
            () -> StatCollector.translateToLocal(noteKeyOf(ui.magnetMode())))).textAlign(Alignment.TopLeft)
            .scale(PocketGhostRequest.RESIDENT_TEXT_SCALE)
            .color(PocketGhostRequest.hintTextColor())
            .shadow(Boolean.TRUE)
            .name("pocket_magnet_note")
            .pos(MAGNET_CONTROL_X, magnetNoteY())
            .size(SWITCH_COLUMN - 2, MAGNET_NOTE_HEIGHT);
    }

    /** 读数行的 y（★单源：三行各一枚，纵向闭合由 static 块与用例一起钉）。 */
    public static int magnetNoteY() {
        return MAGNET_CONTROL_Y + 2 * (MAGNET_BUTTON_HEIGHT + MAGNET_ROW_GAP);
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
        final net.minecraft.item.ItemStack sample = new net.minecraft.item.ItemStack(item, 1, NekoMagnetGhostCell.metaOf(key));
        return sample.getDisplayName();
    }

    /** 第 {@code row} 行的 y（★单源：行高改了这里跟着走，调用点不写第二次）。 */
    public static int rowY(int row) {
        return MARGIN + row * ROW_HEIGHT;
    }

    /** 第 {@code frame} 个挂载框的 y（★排在开关列之下，与纵向预算同源）。 */
    public static int mountY(int frame) {
        return MARGIN + PocketUpgradeType.values().length * ROW_HEIGHT + 4 + frame * (MOUNT_HEIGHT + MOUNT_GAP);
    }

    /**
     * 型名键表（★整键字面量表，与 {@code NekoPocketBottomBand#UPGRADE_ITEM_NAME_KEYS} 同一组键的第二个
     * 消费面；下标 = ordinal = 位图位 = 槽号，三个空间同一个数）。
     */
    private static final String[] UPGRADE_NAME_KEYS = { "item.neko_pocket_upgrade_capacity.name",
        "item.neko_pocket_upgrade_stack.name", "item.neko_pocket_upgrade_magnet.name",
        "item.neko_pocket_upgrade_channel_persist.name", "item.neko_pocket_upgrade_mage.name" };
}

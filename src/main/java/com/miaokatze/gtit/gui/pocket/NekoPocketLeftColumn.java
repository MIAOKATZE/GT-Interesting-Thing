package com.miaokatze.gtit.gui.pocket;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.utils.fluid.FluidStackTank;
import com.cleanroommc.modularui.value.sync.FluidSlotSyncHandler;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.SlotGroupWidget;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.cleanroommc.modularui.widgets.slot.FluidSlot;
import com.miaokatze.gtit.client.gui.NekoGuiTextures;
import com.miaokatze.gtit.common.items.pocket.PocketConstants;

/**
 * 左列 = <b>3 组 × 6 列</b>流体块（R78②；每组纵向 = 输入行 / <b>拉长的流体槽</b> / 输出行）
 * + 末行「整理按钮 + 一行状态回显」。
 * <p>
 * <b>本文件名的历史</b>：它曾经是"竖贯 18×288 流体条 + 8 个同权交互格"的<b>左栏</b>；R74①/R75①
 * 把流体侧改成 6 列（每列纵向 输入格 / 流体槽 / 输出格），R74② 又把最右的说明列删掉、
 * 说明改 tooltip ⇒ 本列同时接了原第四列的一部分文案落点。文件名保留 {@code LeftColumn}
 * 只为不改面板装配顺序（R32 的线性装配），<b>不代表</b>它还是"只有一根条"。
 * <p>
 * <b>几何（R78② 钉死）</b>：列 root {@code x=6 宽 108 高 270}（{@code 6×18=108}、{@code 15×18=270}），
 * 面板宽的 {@code 6+108+4+180+4+108+6 = 416} 里那一段就是本列。列内纵向分段：
 *
 * <pre>
 *   0   第 1 组：输入行 18 + 流体槽 36 + 输出行 18              高 72
 *  72   组间距（空一行）                                        高 18
 *  90   第 2 组                                                 高 72
 * 162   组间距                                                  高 18
 * 180   第 3 组                                                 高 72
 * 252   整理按钮 + 一行状态回显                                 高 18
 * </pre>
 *
 * 即 {@code 3×72 + 2×18 = 252} 给流体块、余 {@code 270-252 = 18} 给末行（R78 的加总表原式）。
 * ★每组中间的流体槽<b>拉长为 18×36</b>（用户："流体槽应该拉长一点"）⇒ 矩阵里那两行空行
 * 就是它的位置（空格只推进坐标、不产出 widget，{@code SlotGroupWidget.java:243-245}）。
 * <p>
 * <b>★R78 D-2（上一片交付不实，本片必修）：本列不再常驻任何"说明文字"</b>。R74② 要求"说明改
 * tooltip"，旧实现却仍留 7 段（上/中/下三行图例、推送方向、主手限制、每槽容量、用法摘要）。
 * 现在的落点：{@code legend.input}/{@code legend.tank}/{@code legend.output} 与
 * {@code fluid.capacity} 进<b>对应格件的 tooltip</b>；{@code held.note}（主手限制）、
 * {@code note.title}/{@code note.summary}（用法摘要）、{@code note.cost}、
 * {@code ghost.capacity_note} 进 {@link NekoPocketPanel#notesText()} 那一份完整文本
 * （由底部带右段的帮助按钮与状态行的 tooltip 承载）。<b>不删信息</b>（R36），只是不再常驻。
 * <p>
 * <b>★末行为什么还留一行文本（R78 D-2 的"算状态不算说明"判据）</b>：那一行的内容是
 * {@code modeText}（推送 / 拉取，含 ghost 条数）与冷却/剩余/回执，二者都是<b>服务端每拍算出来的
 * 运行期事实</b>（R39b 的两条轨道 + R16/R24 的倒计时 + R10 的"失败必须分开说"），玩家无法从静态
 * 外观推出，撤进 tooltip 就等于"当前到底在推还是拉"没有可见面。与之相反，图例与用法摘要讲的都是
 * "这格怎么用"这类<b>不随状态变的说明书文字</b> ⇒ 必须撤进 tooltip。
 * <p>
 * <b>36 个交互格两格同权（R39a 原样保留，R75/R78 只换落点）</b>：每列的"输入格"与"输出格"行为
 * <b>完全相同</b>，方向由<b>放入的容器当前有无流体</b>决定（有流体 → 抽进<b>本列的 tank</b>；
 * 为空 → 从本列的 tank 灌满）。搬运本体在 {@link PocketSlots#fluidInteraction} 的服务端
 * changeListener 里，本文件只装配 Widget；"同权"这一层由 {@code tooltip.6} 与
 * {@code gtit.pocket.legend.in_out_same} 对玩家显式声明，<b>不得</b>把两行做成"只能进 / 只能出"
 * （那会让 R39a 与检查表 2.5 同时失效）。
 * <p>
 * <b>18 个流体槽（R30/R46c/L7 + R78②）</b>：一律用 MUI2 原生 {@link FluidSlot} 的子类
 * {@link NekoPocketFluidSlot} + {@link FluidSlotSyncHandler}（<b>不开 phantom</b>，理由见该方法）：
 * 手持储罐点槽的按键组合与 Tooltip 因此与 GT5U 逐字一致（{@code modularui2.fluid.click_combined} /
 * {@code _to_fill} / {@code _to_empty} / {@code modularui2.tooltip.shift}）；
 * <b>不自写按键分支、不自造文案键</b>。{@code alwaysShowFull(false)} + 只报真实容量的
 * {@link FluidStackTank}（未开 overflow ⇒ {@code getCapacity()} 即真容量）⇒ 部分填充可见。
 * 每槽一个 handler ⇒ 18 份流体状态各自同步，且上游 {@code needsSync()} 走
 * "流体相等 + 数量比较"（dev jar 字节码实证）⇒ <b>不会</b>每 tick 重发 {@code FluidStack} 的 NBT。
 * ★拉长后的<b>液面比例</b>（36 高里流体怎么填）属库内绘制行为，纯 JVM 测不到 ⇒ 列为实机核验项。
 */
public final class NekoPocketLeftColumn {

    /** R75/R78：左列 x 起点（= 面板外边距）。 */
    public static final int X = NekoPocketPanel.MARGIN;
    /** R75/R78：与中栏同一 y 起点。 */
    public static final int Y = NekoPocketPanel.MARGIN;
    /** 左列总宽（<b>每组</b> 6 列 × 18；★组是纵向排的，不改宽度）。 */
    public static final int WIDTH = PocketConstants.FLUID_COLUMN_COUNT * NekoPocketPanel.GRID;
    /** R75/R78：与中栏等高（15 行 × 18）。 */
    public static final int HEIGHT = NekoPocketStorageColumn.HEIGHT;

    /** 单个交互格（输入行 / 输出行）的边长。 */
    public static final int CELL = NekoPocketPanel.GRID;
    /** ★一组里"流体槽本体"的高度 = {@link #CELL} 的两倍（R78②："流体槽应该拉长一点"）。 */
    public static final int TANK_HEIGHT = 2 * CELL;
    /** 一组的总高（输入行 18 + 拉长流体槽 36 + 输出行 18 = 72）。 */
    public static final int GROUP_HEIGHT = CELL + TANK_HEIGHT + CELL;
    /** 组间距（空一行 = 18；R78 加总表里的那两个 18）。 */
    public static final int GROUP_GAP = CELL;
    /** 流体块总高（R78：{@code 3×72 + 2×18 = 252}；★派生，别处不得写 252）。 */
    public static final int FLUID_AREA_HEIGHT = PocketConstants.FLUID_GROUP_COUNT * GROUP_HEIGHT
        + (PocketConstants.FLUID_GROUP_COUNT - 1) * GROUP_GAP;
    /** 末行（整理按钮 + 状态回显）起点。 */
    public static final int STATUS_Y = FLUID_AREA_HEIGHT;
    /** 末行高度 = 列高减去流体块 ⇒ 永远闭合（★恒为 18，见类 javadoc 的算式）。 */
    public static final int STATUS_HEIGHT = HEIGHT - STATUS_Y;

    /**
     * 36 个交互格的<b>布局字面量</b>（与中栏同一机制，R41a）：14 行 × 6 列，
     * 每组的形状是「输入行 / 空 / 空 / 输出行」（中间两行空 = 那一个 18×36 的拉长流体槽），
     * 组与组之间再空一行。★<b>整块只允许一个布局字符</b>（{@code 'L'}，理由见
     * {@link #layoutSlotCount()}），空格行只推进 y 不产出 widget。
     * <p>
     * 由此 {@code 'L'} 的出现序号恰好是 handler 索引：组 0 的进格 {@code 0…5}、出格 {@code 6…11}、
     * 组 1 的进格 {@code 12…17}…⇒ {@link PocketInventory#tankOfInteractionSlot(int)} 的
     * "组号 × 列数 + 组内列号"映射成立，不需要任何手工偏移或第二条常量。
     */
    private static final String[] INTERACTION_MATRIX = { "LLLLLL", "      ", "      ", "LLLLLL", "      ", "LLLLLL",
        "      ", "      ", "LLLLLL", "      ", "LLLLLL", "      ", "      ", "LLLLLL" };

    /**
     * 流体交互格的布局字符（★<b>只允许一个</b>，见 {@link #layoutSlotCount()}）。
     */
    private static final char INTERACTION_KEY = 'L';

    /** 每组的矩阵行数（进 1 + 空 2 + 出 1 = 4 行 = 72px；组间距另算一行）。 */
    private static final int ROWS_PER_GROUP = 4;

    /**
     * 布局字符的出现总数 = 本矩阵产出的真实槽数（机检用，也是装配期断言的输入）。
     * <p>
     * ★<b>为什么必须只有一个布局字符</b>（R77 实测拿到的库行为）：
     * {@code SlotGroupWidget$Builder.build()} 用的是 {@code Char2IntOpenHashMap} 做计数器
     * （字节码实证：{@code it/unimi/dsi/fastutil/chars/Char2IntOpenHashMap.<init>} +
     * {@code Char2IntMap.get/put}）——<b>每个字符各自从 0 起计</b>。因此把输入位与输出位
     * 写成两个字符（{@code 'I'} / {@code 'O'}）会让两行都拿到索引 0…5：
     * 只产出 18 个槽、且两行写同一段 handler ——{@code assertTotalRealSlots()} 会在首次开屏
     * 当场报 181 != 199（双端同抛，不会静默，但整块流体区不能用）。
     * 单字符 + 行主序才能让"组 0 第一行 0…5、组 0 第二行 6…11、组 1 第一行 12…17"成立。
     */
    public static int layoutSlotCount() {
        int total = 0;
        for (String row : INTERACTION_MATRIX) {
            for (int index = 0; index < row.length(); index++) {
                if (row.charAt(index) == INTERACTION_KEY) {
                    total++;
                }
            }
        }
        return total;
    }

    /** 本矩阵的行数（机检用：R78 的加总表要求 {@code 组数×4 + (组数−1) = 14}）。 */
    public static int layoutRowCount() {
        return INTERACTION_MATRIX.length;
    }

    /** 某一组在列内的 y 起点（组与组之间留 {@link #GROUP_GAP}）。 */
    public static int groupTop(int group) {
        return group * (GROUP_HEIGHT + GROUP_GAP);
    }

    static {
        if (layoutSlotCount() != PocketInventory.FLUID_INTERACTION_SLOTS) {
            throw new IllegalStateException(
                "[pocket] 流体交互格矩阵产出 " + layoutSlotCount()
                    + " 格，与 handler 格数 "
                    + PocketInventory.FLUID_INTERACTION_SLOTS
                    + " 不符");
        }
        if (layoutRowCount()
            != PocketConstants.FLUID_GROUP_COUNT * ROWS_PER_GROUP + (PocketConstants.FLUID_GROUP_COUNT - 1)) {
            throw new IllegalStateException("[pocket] 流体矩阵行数与" + PocketConstants.FLUID_GROUP_COUNT + " 组的排法不符");
        }
        if (FLUID_AREA_HEIGHT + CELL > HEIGHT) {
            throw new IllegalStateException("[pocket] 流体块加末行超出列高（R78 的 252 + 18 = 270 被破坏）");
        }
        for (String row : INTERACTION_MATRIX) {
            for (int index = 0; index < row.length(); index++) {
                final char c = row.charAt(index);
                if (c != ' ' && c != INTERACTION_KEY) {
                    throw new IllegalStateException("[pocket] 流体矩阵不得出现第二个布局字符: " + c);
                }
            }
        }
    }

    private NekoPocketLeftColumn() {}

    /** 装配左列（列 root 各一次 {@code excludeAreaInRecipeViewer()}，R36/§16）。 */
    public static ParentWidget<?> build(NekoPocketPanel ui) {
        final SlotGroupWidget interaction = SlotGroupWidget.builder()
            .matrix(INTERACTION_MATRIX)
            .key(INTERACTION_KEY, index -> interactionSlot(ui, index))
            .synced(PocketSlots.SYNC_FLUID)
            .slotGroup(PocketSlots.GROUP_FLUID)
            .build();
        interaction.pos(0, 0);

        final ParentWidget<?> root = new ParentWidget<>().pos(X, Y)
            .size(WIDTH, HEIGHT)
            .name("pocket_fluid_column")
            .child(interaction)
            .child(fluidSlots(ui))
            // R78 D-2：图例段与说明摘要段整体撤出常驻渲染，只留末行"按钮 + 一行状态回显"
            .child(sortButton(ui))
            .child(statusLine(ui));
        return root.excludeAreaInRecipeViewer();
    }

    /**
     * 一个交互格（输入位/输出位共用同一件——两格同权，见类 javadoc）。
     * <p>
     * 名字里的 {@code fluid_} 前缀沿用旧口径（handler 索引 = 名字后缀），
     * ghost 与同步都不看这个名字，只影响调试树可读性。
     * <p>
     * ★R78 D-2：这一格的 tooltip 现在同时承担"上/下行图例"（{@code legend.input} /
     * {@code legend.output}，按它落在本组的哪一行选键）——那两行原本常驻在列里。
     */
    private static IWidget interactionSlot(NekoPocketPanel ui, int index) {
        return new com.cleanroommc.modularui.widgets.slot.ItemSlot().slot(
            ui.slots()
                .fluidInteraction(ui.inventory(), index))
            .name("fluid_" + index)
            .background(PocketGuiTextures.SLOT)
            .tooltip(tooltip -> {
                tooltip.addLine(
                    IKey.lang(
                        PocketInventory.isLowerInteractionRow(index) ? "gtit.pocket.legend.output"
                            : "gtit.pocket.legend.input"));
                tooltip.addLine(IKey.lang("gtit.pocket.legend.in_out_same"));
                tooltip.addLine(IKey.dynamic(() -> ui.tankHintText(index)));
            });
    }

    /**
     * 18 个流体槽本体（每组 6 个，位于该组"输入行"之下、纵向跨两行 ⇒ {@code 18×36}）。
     * <p>
     * <b>需求 4 的流体入口已实装</b>（S-E，R70 + R75/R78 的索引扩展）：格件是
     * {@link NekoPocketFluidSlot} ——覆写了 {@code handleDragAndDrop} 的 {@code FluidSlot} 子类，
     * NEI 左键把满容器（水桶 / 岩浆桶 / 其它注册过的罐瓶，或流体方块本身）拖到某槽上 ⇒
     * 就地声明 {@code (Kind.FLUID, 本 tank 号)} 那条 ghost；右键发 {@code CLR|<本 tank 号>|F} 解绑。
     * 判定与落档都在服务端（{@code NekoPocketPanel#onServerGhostRequest} → {@code PocketGhostRequest#apply}）。
     * <p>
     * ★<b>两条不许动</b>（R70 实测口径，十八个槽每一个都适用）：① <b>不</b>调
     * {@code super.handleDragAndDrop}——库内那一条在 {@code isPhantom()} 门之前就返回 false，
     * 真实槽上恒拒；② <b>不</b>把 handler 设成 {@code phantom(true)}——那会让服务端那一支把流体
     * 直接写进真实 tank，而需求 2 的「从手上/仓里灌排流体、两格同权」依赖它是<b>真实槽</b>。
     * <p>
     * ★R78 D-2：{@code legend.tank}（"这一格是本列的流体槽"）与每槽容量读数改由<b>本槽的
     * tooltip</b> 承载（见 {@link NekoPocketFluidSlot#addToolTip}），不再常驻。
     */
    private static IWidget fluidSlots(NekoPocketPanel ui) {
        final ParentWidget<?> field = new ParentWidget<>().pos(0, 0)
            .size(WIDTH, FLUID_AREA_HEIGHT)
            .name("pocket_fluid_slots");
        for (int tank = 0; tank < PocketConstants.FLUID_TANK_TOTAL; tank++) {
            final int group = tank / PocketConstants.FLUID_COLUMN_COUNT;
            final int column = tank % PocketConstants.FLUID_COLUMN_COUNT;
            final FluidStackTank target = ui.inventory()
                .tankAt(tank);
            final NekoPocketFluidSlot slot = new NekoPocketFluidSlot().bindBar(ui, tank);
            slot.background(PocketGuiTextures.SLOT_TALL);
            ui.trackFluidSlot(tank, slot);
            field.child(
                (IWidget) slot.pos(column * CELL, groupTop(group) + CELL)
                    .size(CELL, TANK_HEIGHT)
                    .alwaysShowFull(false)
                    .name("pocket_fluid_slot_" + tank)
                    .syncHandler(new FluidSlotSyncHandler(target)));
        }
        return field;
    }

    /**
     * 一键整理中栏 150 格（计划 §6 第 4 条的<b>语义①</b>）——R78 后落在末行左侧：流体块吃满了列高，
     * 原来那条"按钮 + 容量读数"行已经不存在（容量读数进 tooltip，按钮进末行）。
     * <p>
     * <b>为什么自造按钮</b>（R41c 的后备分支）：R41c 让优先复用
     * {@code SlotGroupWidget.placeSortButtonsTopRightVertical()}，但实测本版本（2.3.88）里
     * 那一族方法连同 {@code SortButtons} 类<b>都不存在</b>——{@code SlotGroupWidget.java:63-144}
     * 整段被注释掉，{@code widgets/} 目录下也没有 {@code SortButtons.java} ⇒ 复用不可能。
     * 不自绘同步面：按钮只发一次请求，排序本体在 {@link NekoPocketPanel#performSort()}（服务端）。
     */
    private static IWidget sortButton(NekoPocketPanel ui) {
        return new ButtonWidget<>().pos(0, STATUS_Y)
            .size(CELL, CELL)
            .name("pocket_sort_button")
            .background(PocketGuiTextures.BUTTON)
            .child(
                NekoGuiTextures.SORT_SMART.asWidget()
                    .pos(1, 1)
                    .size(12, 12))
            .playClickSound(false)
            .tooltip(tooltip -> tooltip.addLine(IKey.lang("gtit.pocket.sort.button")))
            .onMousePressed(button -> ui.requestSort());
    }

    /**
     * 一行状态回显（★R78 D-2 允许保留的那"一行短文本"，判据见类 javadoc：它印的是服务端算出来的
     * 运行期事实——推送/拉取方向、冷却与剩余秒数、最近一次动作的回执——不是"这格怎么用"的说明）。
     * <p>
     * 文本由 {@link NekoPocketPanel} 的同步缓存格式化 ⇒ 客户端不读服务端内存表、不按 ghost 表推断
     * （R39b/R19）。末行只有 {@code 108-18-2 = 88}px 宽，装不下完整回执（R36 判过的宽度现实）
     * ⇒ 同一份完整文本连同撤下来的说明进本行的 tooltip（<b>不删信息</b>）。
     */
    private static IWidget statusLine(NekoPocketPanel ui) {
        return new ParentWidget<>().pos(CELL + 2, STATUS_Y)
            .size(WIDTH - CELL - 2, STATUS_HEIGHT)
            .name("pocket_status_lines")
            .child(
                (IWidget) new TextWidget(IKey.dynamic(ui::fluidStatusLine)).textAlign(Alignment.CenterLeft)
                    .scale(0.5f)
                    .pos(0, 0)
                    .size(WIDTH - CELL - 2, STATUS_HEIGHT))
            .tooltip(tooltip -> {
                tooltip.addLine(IKey.dynamic(ui::statusHintText));
                tooltip.addLine(IKey.dynamic(ui::capacityReadoutText));
                tooltip.addLine(IKey.dynamic(ui::notesText));
            });
    }
}

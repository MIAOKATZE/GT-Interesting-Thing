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
 * 左列 = <b>6 个独立流体列</b>（R75① 的排法 a）+ 其下的图例 / 状态回显 / 一键整理 / 说明摘要。
 * <p>
 * <b>本文件名的历史</b>：它曾经是"竖贯 18×288 流体条 + 8 个同权交互格"的<b>左栏</b>；R74①/R75①
 * 把流体侧改成 6 列（每列纵向 输入格 / 流体槽 / 输出格），R74② 又把最右的说明列删掉、
 * 说明改 tooltip ⇒ 本列同时接了原第四列的一部分文案落点。文件名保留 {@code LeftColumn}
 * 只为不改面板装配顺序（R32 的线性装配），<b>不代表</b>它还是"只有一根条"。
 * <p>
 * <b>几何（R75 钉死）</b>：列 root {@code x=6 宽 108 高 270}（{@code 6×18=108}、{@code 15×18=270}），
 * 面板宽的 {@code 6+108+4+180+4+108+6 = 416} 里那一段就是本列。列内纵向分段：
 * 
 * <pre>
 *   0   ┤ 6 列 × 3 行的流体块（输入格 / 流体槽 / 输出格）      高 54
 *  54   ┤ 三色角色图例（输入 / 流体槽 / 输出）                  高 36
 *  90   ┤ 状态回显 3 行（模式 / 冷却或剩余 / 仅主手脚注）        高 54
 * 144   ┤ 一键整理按钮 + 单槽容量读数                           高 18
 * 162   ┤ 说明摘要（原第四列文案的常驻部分 + 完整 tooltip）      高 108
 * </pre>
 * 
 * 段与段之间不重叠（这是 R75 唯一没有现成核算表的部分，按 mockup 的 (a) 排法把
 * 15 行带来的 18px 缩减全部落在最后的"说明摘要"段上 ⇒ 上面四段尺寸与提案逐字相同）。
 * <p>
 * <b>12 个交互格两格同权（R39a 原样保留，R75 只换落点）</b>：每列的"输入格"与"输出格"行为
 * <b>完全相同</b>，方向由<b>放入的容器当前有无流体</b>决定（有流体 → 抽进<b>本列的 tank</b>；
 * 为空 → 从本列的 tank 灌满）。搬运本体在 {@link PocketSlots#fluidInteraction} 的服务端
 * changeListener 里，本文件只装配 Widget；"同权"这一层由 {@code tooltip.6} 与
 * {@code gtit.pocket.legend.*} 对玩家显式声明，<b>不得</b>把两行做成"只能进 / 只能出"
 * （那会让 R39a 与检查表 2.5 同时失效）。
 * <p>
 * <b>6 个流体槽（R30/R46c/L7）</b>：一律用 MUI2 原生 {@link FluidSlot} 的子类
 * {@link NekoPocketFluidSlot} + {@link FluidSlotSyncHandler}（<b>不开 phantom</b>，理由见该方法）：
 * 手持储罐点槽的按键组合与 Tooltip 因此与 GT5U 逐字一致（{@code modularui2.fluid.click_combined} /
 * {@code _to_fill} / {@code _to_empty} / {@code modularui2.tooltip.shift}）；
 * <b>不自写按键分支、不自造文案键</b>。{@code alwaysShowFull(false)} + 只报真实容量的
 * {@link FluidStackTank}（未开 overflow ⇒ {@code getCapacity()} 即真容量）⇒ 部分填充可见。
 * 每槽一个 handler ⇒ 6 份流体状态各自同步，且上游 {@code needsSync()} 走
 * "流体相等 + 数量比较"（dev jar 字节码实证）⇒ <b>不会</b>每 tick 重发 {@code FluidStack} 的 NBT。
 */
public final class NekoPocketLeftColumn {

    /** R75：左列 x 起点（= 面板外边距）。 */
    public static final int X = NekoPocketPanel.MARGIN;
    /** R75：与中栏同一 y 起点。 */
    public static final int Y = NekoPocketPanel.MARGIN;
    /** R75：左列总宽（6 列 × 18）。 */
    public static final int WIDTH = PocketConstants.FLUID_COLUMN_COUNT * NekoPocketPanel.GRID;
    /** R75：与中栏等高（15 行 × 18）。 */
    public static final int HEIGHT = NekoPocketStorageColumn.HEIGHT;

    /** 单个流体格（输入格 / 流体槽 / 输出格）的边长。 */
    public static final int CELL = NekoPocketPanel.GRID;
    /** 流体块高度（3 行：输入 / 流体槽 / 输出）。 */
    public static final int GRID_HEIGHT = 3 * CELL;
    /** 图例段起点（紧接流体块）。 */
    public static final int LEGEND_Y = GRID_HEIGHT;
    /** 图例段高度（3 行 × 12）。 */
    public static final int LEGEND_HEIGHT = 36;
    /** 状态回显段起点。 */
    public static final int STATUS_Y = LEGEND_Y + LEGEND_HEIGHT;
    /** 状态回显段高度（3 行 + 余量）。 */
    public static final int STATUS_HEIGHT = 54;
    /** 按钮行起点（整理按钮 + 容量读数）。 */
    public static final int BUTTON_Y = STATUS_Y + STATUS_HEIGHT;
    /** 按钮行高度。 */
    public static final int BUTTON_HEIGHT = CELL;
    /** 说明摘要段起点。 */
    public static final int NOTE_Y = BUTTON_Y + BUTTON_HEIGHT;
    /** 说明摘要段高度（列高减去以上所有段 ⇒ 永远闭合，不会出现"段重叠"）。 */
    public static final int NOTE_HEIGHT = HEIGHT - NOTE_Y;

    /** 状态段与说明段的行高（{@code scale(0.5)} 的一行）。 */
    private static final int LINE = 11;

    /**
     * 12 个交互格的<b>布局字面量</b>（与中栏同一机制，R41a）：3 行 × 6 列，
     * 第 1 行是 6 个"输入位"、第 2 行<b>留空</b>（那一行是 6 个流体槽本体，不是真实槽）、
     * 第 3 行是 6 个"输出位"；三行共用<b>同一个</b>布局字符（{@link #layoutSlotCount()} 的存在理由）。
     * <p>
     * 空格只推进坐标、不产出 widget（{@code SlotGroupWidget.java:243-245}），所以
     * {@code 'I'} 的出现序号恰好是 handler 索引 {@code 0..5}、{@code 'O'} 是 {@code 6..11}
     * ⇒ {@code index % 6} 就是列号（{@link PocketInventory#tankOfInteractionSlot(int)}），
     * 不需要任何手工偏移或第二条常量。
     */
    private static final String[] INTERACTION_MATRIX = { "LLLLLL", "      ", "LLLLLL" };

    /**
     * 流体交互格的布局字符（★<b>只允许一个</b>，见 {@link #layoutSlotCount()}）。
     */
    private static final char INTERACTION_KEY = 'L';

    /**
     * 布局字符的出现总数 = 本矩阵产出的真实槽数（机检用，也是装配期断言的输入）。
     * <p>
     * ★<b>为什么必须只有一个布局字符</b>（本批实测拿到的库行为）：
     * {@code SlotGroupWidget$Builder.build()} 用的是 {@code Char2IntOpenHashMap} 做计数器
     * （字节码实证：{@code it/unimi/dsi/fastutil/chars/Char2IntOpenHashMap.<init>} +
     * {@code Char2IntMap.get/put}）——<b>每个字符各自从 0 起计</b>。因此把输入位与输出位
     * 写成两个字符（{@code 'I'} / {@code 'O'}）会让两行都拿到索引 0…5：
     * 只产出 6 个槽、且两行写同一段 handler ——{@code assertTotalRealSlots()} 会在首次开屏
     * 当场报 169 != 175（双端同抛，不会静默，但整块流体区不能用）。
     * 单字符 + 行主序才能让"第一行 0…5、第三行 6…11"成立，从而
     * {@link PocketInventory#tankOfInteractionSlot(int)} 的取模映射正确。
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

    static {
        if (layoutSlotCount() != PocketInventory.FLUID_INTERACTION_SLOTS) {
            throw new IllegalStateException(
                "[pocket] 流体交互格矩阵产出 " + layoutSlotCount()
                    + " 格，与 handler 格数 "
                    + PocketInventory.FLUID_INTERACTION_SLOTS
                    + " 不符");
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
            .child(legend(ui))
            .child(statusLines(ui))
            .child(sortButton(ui))
            .child(capacityReadout(ui))
            .child(notes(ui));
        return root.excludeAreaInRecipeViewer();
    }

    /**
     * 一个交互格（输入位/输出位共用同一件——两格同权，见类 javadoc）。
     * <p>
     * 名字里的 {@code fluid_} 前缀沿用旧口径（handler 索引 = 名字后缀），
     * ghost 与同步都不看这个名字，只影响调试树可读性。
     */
    private static IWidget interactionSlot(NekoPocketPanel ui, int index) {
        return new com.cleanroommc.modularui.widgets.slot.ItemSlot().slot(
            ui.slots()
                .fluidInteraction(ui.inventory(), index))
            .name("fluid_" + index)
            .background(PocketGuiTextures.SLOT)
            .tooltip(tooltip -> {
                tooltip.addLine(IKey.lang("gtit.pocket.legend.in_out_same"));
                tooltip.addLine(IKey.dynamic(() -> ui.tankHintText(index)));
            });
    }

    /**
     * 6 个流体槽本体（每列一个，位于流体块的第 2 行）。
     * <p>
     * <b>需求 4 的流体入口已实装</b>（S-E，R70 + R75 的索引扩展）：格件是
     * {@link NekoPocketFluidSlot} ——覆写了 {@code handleDragAndDrop} 的 {@code FluidSlot} 子类，
     * NEI 左键把满容器（水桶 / 岩浆桶 / 其它注册过的罐瓶，或流体方块本身）拖到某槽上 ⇒
     * 就地声明 {@code (Kind.FLUID, 本列号)} 那条 ghost；右键发 {@code CLR|<本列号>|F} 解绑。
     * 判定与落档都在服务端（{@code NekoPocketPanel#onServerGhostRequest} → {@code PocketGhostRequest#apply}）。
     * <p>
     * ★<b>两条不许动</b>（R70 实测口径，六个槽每一个都适用）：① <b>不</b>调
     * {@code super.handleDragAndDrop}——库内那一条在 {@code isPhantom()} 门之前就返回 false，
     * 真实槽上恒拒；② <b>不</b>把 handler 设成 {@code phantom(true)}——那会让服务端那一支把流体
     * 直接写进真实 tank，而需求 2 的「从手上/仓里灌排流体、两格同权」依赖它是<b>真实槽</b>。
     */
    private static IWidget fluidSlots(NekoPocketPanel ui) {
        final ParentWidget<?> row = new ParentWidget<>().pos(0, CELL)
            .size(WIDTH, CELL)
            .name("pocket_fluid_slots");
        for (int column = 0; column < PocketConstants.FLUID_COLUMN_COUNT; column++) {
            final int tank = column;
            final FluidStackTank target = ui.inventory()
                .tankAt(tank);
            final NekoPocketFluidSlot slot = new NekoPocketFluidSlot().bindBar(ui, tank);
            slot.background(PocketGuiTextures.SLOT_TALL);
            ui.trackFluidSlot(tank, slot);
            row.child(
                (IWidget) slot.pos(tank * CELL, 0)
                    .size(CELL, CELL)
                    .alwaysShowFull(false)
                    .name("pocket_fluid_slot_" + tank)
                    .syncHandler(new FluidSlotSyncHandler(target)));
        }
        return row;
    }

    /**
     * 三色角色图例（R75：6 列每列三格的读法必须常驻可见，否则"哪一格是输入"只能靠猜）。
     * <p>
     * 三行各带一条 tooltip 补全（R36：宽度不够就加 tooltip，不删信息）；
     * "两格同权"这条最容易读错的事实由 {@code legend.in_out_same} 同时出现在图例与交互格 tooltip。
     */
    private static IWidget legend(NekoPocketPanel ui) {
        return new ParentWidget<>().pos(0, LEGEND_Y)
            .size(WIDTH, LEGEND_HEIGHT)
            .name("pocket_fluid_legend")
            .child(legendLine("gtit.pocket.legend.input", 0))
            .child(legendLine("gtit.pocket.legend.tank", 1))
            .child(legendLine("gtit.pocket.legend.output", 2));
    }

    private static IWidget legendLine(String key, int row) {
        final TextWidget line = new TextWidget(IKey.lang(key));
        line.textAlign(Alignment.TopLeft);
        line.scale(0.5f);
        line.pos(0, row * LINE);
        line.size(WIDTH, LINE);
        return line;
    }

    /**
     * 状态回显三行：模式（R39b 服务端算）、冷却/剩余秒数 + 最近一次动作的回执（R24 倒计时 +
     * R16 墙钟冷却 + R10 的"失败必须分开说"）、R60/R62b 要求可观测的"仅主背包内运作"边界脚注。
     * <p>
     * 三行文本都由 {@link NekoPocketPanel} 的同步缓存格式化 ⇒ 客户端不读服务端内存表、不按 ghost 表推断。
     * 108px 装不下完整回执（R36 判定过的宽度现实）⇒ 同一份完整文本进本块的 tooltip，<b>不删信息</b>。
     */
    private static IWidget statusLines(NekoPocketPanel ui) {
        return new ParentWidget<>().pos(0, STATUS_Y)
            .size(WIDTH, STATUS_HEIGHT)
            .name("pocket_status_lines")
            .child(
                (IWidget) new TextWidget(IKey.dynamic(ui::modeText)).textAlign(Alignment.TopLeft)
                    .scale(0.5f)
                    .pos(0, 0)
                    .size(WIDTH, LINE))
            .child(
                (IWidget) new TextWidget(IKey.dynamic(ui::channelStatusText)).textAlign(Alignment.TopLeft)
                    .scale(0.5f)
                    .pos(0, LINE)
                    .size(WIDTH, 2 * LINE))
            .child(
                (IWidget) new TextWidget(IKey.lang("gtit.pocket.held.note")).textAlign(Alignment.TopLeft)
                    .scale(0.5f)
                    .pos(0, 3 * LINE)
                    .size(WIDTH, LINE))
            .tooltip(tooltip -> tooltip.addLine(IKey.dynamic(ui::statusHintText)));
    }

    /**
     * 一键整理中栏 150 格（计划 §6 第 4 条的<b>语义①</b>）。
     * <p>
     * <b>为什么自造按钮</b>（R41c 的后备分支）：R41c 让优先复用
     * {@code SlotGroupWidget.placeSortButtonsTopRightVertical()}，但实测本版本（2.3.88）里
     * 那一族方法连同 {@code SortButtons} 类<b>都不存在</b>——{@code SlotGroupWidget.java:63-144}
     * 整段被注释掉，{@code widgets/} 目录下也没有 {@code SortButtons.java} ⇒ 复用不可能。
     * 不自绘同步面：按钮只发一次请求，排序本体在 {@link NekoPocketPanel#performSort()}（服务端）。
     */
    private static IWidget sortButton(NekoPocketPanel ui) {
        return new ButtonWidget<>().pos(0, BUTTON_Y)
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
     * 单槽容量读数（R75①：每槽 {@link PocketConstants#FLUID_BAR_CAPACITY_ML} mB）。
     * <p>
     * ★<b>规格外自立项</b>（R75 明文要求双处声明）：16,000,000 这个数字来自用户那句
     * 「流体槽容量 16M」而<b>不是</b>需求原文，因此除本处读数外，物品 tooltip 里也有一行
     * （{@code tooltip.9}）；lang 侧只留 {@code %d} 占位，数字由常量填（契约 §7 第 5 条）。
     */
    private static IWidget capacityReadout(NekoPocketPanel ui) {
        final IKey capacity = IKey
            .lang("gtit.pocket.fluid.capacity", () -> new Object[] { PocketConstants.FLUID_BAR_CAPACITY_ML });
        return new ParentWidget<>().pos(CELL + 2, BUTTON_Y)
            .size(WIDTH - CELL - 2, BUTTON_HEIGHT)
            .name("pocket_fluid_capacity")
            .child(
                (IWidget) new TextWidget(capacity).textAlign(Alignment.CenterLeft)
                    .scale(0.5f)
                    .pos(0, 0)
                    .size(WIDTH - CELL - 2, BUTTON_HEIGHT))
            .tooltip(tooltip -> tooltip.addLine(capacity));
    }

    /**
     * 说明摘要（R74②：原第四列的常驻说明改 tooltip；这里只留"一眼看得见的三行"）。
     * <p>
     * 三行都指向同一份完整文本 {@link NekoPocketPanel#notesText()}（含通道成本、识别条件、
     * ghost 用法与"每格声明吃掉一格真实容量"的代价），<b>不删信息</b>（R36）。
     */
    private static IWidget notes(NekoPocketPanel ui) {
        return new ParentWidget<>().pos(0, NOTE_Y)
            .size(WIDTH, NOTE_HEIGHT)
            .name("pocket_notes")
            .child(
                (IWidget) new TextWidget(IKey.lang("gtit.pocket.note.title")).textAlign(Alignment.TopLeft)
                    .scale(0.5f)
                    .pos(0, 0)
                    .size(WIDTH, LINE))
            .child(
                (IWidget) new TextWidget(IKey.lang("gtit.pocket.note.summary")).textAlign(Alignment.TopLeft)
                    .scale(0.5f)
                    .pos(0, LINE)
                    .size(WIDTH, 3 * LINE))
            .tooltip(tooltip -> tooltip.addLine(IKey.dynamic(ui::notesText)));
    }
}

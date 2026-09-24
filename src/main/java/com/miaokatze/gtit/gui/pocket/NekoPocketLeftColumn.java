package com.miaokatze.gtit.gui.pocket;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.utils.fluid.FluidStackTank;
import com.cleanroommc.modularui.value.sync.FluidSlotSyncHandler;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widgets.SlotGroupWidget;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.cleanroommc.modularui.widgets.slot.FluidSlot;
import com.miaokatze.gtit.common.items.pocket.PocketConstants;
import com.miaokatze.gtit.common.items.pocket.PocketFilterConfig;
import com.miaokatze.gtit.common.items.pocket.PocketInventory;

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
 * 面板宽的 {@code 6+108+4+162+4+108+6 = 398 = 面板宽}（★R81④：面板宽收到主区实占）里那一段就是本列。列内纵向分段：
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
 * 即 {@code 3×72 + 2×18 = 252} 给流体块、余 {@code 270-252 = 18} 给末行（R78 的加总表原式，
 * ★R80② 撤边条后<b>逐字不变</b>，见下一段）。
 * ★每组中间的流体槽<b>拉长为 18×36</b>（用户："流体槽应该拉长一点"）⇒ 矩阵里那两行空行
 * 就是它的位置（空格只推进坐标、不产出 widget，{@code SlotGroupWidget.java:243-245}）。
 * <p>
 * <b>★R80②：上一轮展示稿那 18 条"流体列标题边条"已撤，本列不实现它们</b>。
 * 展示稿（{@code plan/assest/pocket-ui-live-2.html} 的 {@code chead} / {@code TANK_HEAD_H}）给
 * 每组 6 个流体槽各画了一条 9px 高的标题条（{@code 3 组 × 6 列 = 18} 条），用来把
 * {@code legend.tank_of}（第几组第几列）与 {@code ghost.fluid_bar} 挂成真实悬停区；用户裁定
 * 「我觉得要撤」⇒ 本列的纵向分段<b>保持 R78 的原式</b>：
 * 
 * <pre>
 *   每组 = 输入行 18 + 拉长流体槽 36 + 输出行 18 = 72   （★不出现 72 + 9 = 81）
 *   三组 + 两个 18 组间距 = 252 ;  末行(整理按钮 + 状态回显) = 18 ;  合计 = 270 = 列高
 * </pre>
 * 
 * ⇒ <b>零富余、零无主空白</b>（那条 9px 是画在流体槽<b>上半部之上的覆盖件</b>，本来就不占纵向，
 * 所以撤掉它不会留下缝；本类 {@code static} 块把 {@code 252 + 18 == 270} 变成装配期断言，
 * 一旦有人给组内加高度就会红）。撤下来的<b>信息</b>另有落点（R36「不删信息」）：
 * 组号/列号进 {@link NekoPocketFluidSlot} 自己的 tooltip（{@code NekoPocketPanel#tankOwnLabelText}），
 * 图例与"两格同权"仍在 36 个交互格的 tooltip 里（{@link #interactionSlot}），
 * {@code ghost.fluid_bar} 仍由 ghost 态的同一条加行（{@code NekoPocketFluidSlot#addToolTip}）。
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
 * {@code modeText}（推送 / 拉取，含 ghost 条数）——它<b>是</b>服务端每拍算出来的运行期事实
 * （R39b 的两条轨道），玩家无法从静态外观推出，撤进 tooltip 就等于"当前到底在推还是拉"没有可见面。
 * 与之相反，图例与用法摘要讲的都是"这格怎么用"这类<b>不随状态变的说明书文字</b> ⇒ 必须撤进 tooltip。
 * ★★<b>R93-③：同一行原本还拼着冷却/剩余与最近一次回执，那段本轮搬进底部带左段那块 112×36</b>
 * （{@link NekoPocketPanel#statusBlockText()}）。搬的理由不是判据变了，而是<b>装不下</b>：
 * 88×18 的盒放整条状态要折五行（用户实机"会超出"），而模式串单独一行永远装得下 ⇒
 * 留下模式、把长正文搬到有 36px 高的地方。回执/冷却仍是"算状态"那一家，只是换了落点。
 * <p>
 * <b>36 个交互格（★R83 D-2 覆盖 R39a 的"两格同权"）</b>：方向仍由<b>放入的容器当前有无流体</b>决定
 * （有流体 → 抽进<b>本列的 tank</b>；为空 → 从本列的 tank 灌满），但<b>落位不再同格</b>：处理完的容器
 * 进本列<b>出格</b>、未处理完的余量留本列<b>进格</b>，两格都放不下就整笔不搬也不吞件。搬运本体在
 * {@link PocketSlots#fluidInteraction} 的服务端 changeListener 里，本文件只装配 Widget。
 * ★★<b>R93-② 撤销本句旧口径</b>（原文："R39a 的'同权'现在只对输入侧成立 ⇒ 出格仍不得做成'只能出'的
 * 单向门（那会让检查表 2.5 失效）"）：用户实机明确"输出格也能提取和放置流体，这是不对的，应该只有
 * 输入格才可以"⇒ 本轮<b>就是</b>把出格做成"只能被系统写入 + 玩家可取出"的单向门。
 * ★代价如实登记：检查表 2.5「下行原地抽干」与 9.6「余量留在下行」那两条验收面随裁定作废，
 * {@code PocketFluidTransfer#restCellOf} 的第一支与 {@code canPlacePair} 的 {@code replaceable} 同支
 * 因此成为<b>玩家路径不可达</b>的支——★按 R91-i 通则不删、但由新用例 {@code fluid_output_row_read_only}
 * 钉住"处理永不从出格发起"这条<b>正向</b>判据，而不是留一条"推演为不可达"当结论。
 * ★流体<b>槽本体</b>（18 个 tank）不受影响，仍可用储罐直接交互（用户明写）。
 * 玩家可见口径由 {@code gtit.pocket.legend.in_out_same}（★R93-② 改述）与 {@code legend.input}/
 * {@code legend.output} 声明。
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
     * 当场报 166 != 184（双端同抛，不会静默，但整块流体区不能用）。
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
        if (FLUID_AREA_HEIGHT + STATUS_HEIGHT != HEIGHT) {
            throw new IllegalStateException(
                "[pocket] 左栏纵向加总不闭合: 流体块 " + FLUID_AREA_HEIGHT
                    + " + 末行 "
                    + STATUS_HEIGHT
                    + " != 列高 "
                    + HEIGHT
                    + "（R78 的 252 + 18 = 270；★R80② 撤边条后必须逐字不变，富余/缺口都算无主空白）");
        }
        if (STATUS_Y != FLUID_AREA_HEIGHT) {
            throw new IllegalStateException("[pocket] 末行起点不等于流体块底部（纵向出现缝）");
        }
        if (TANK_HEIGHT != 2 * CELL) {
            throw new IllegalStateException("[pocket] 流体槽拉长倍数被改（R78② 是 2 倍格高 = 36）");
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
     * ★★<b>R78 D-2</b>：{@code legend.tank}（"这一格是本列的流体槽"）与每槽容量读数改由<b>本槽的
     * tooltip</b> 承载（见 {@link NekoPocketFluidSlot#addToolTip}），不再常驻。
     * <p>
     * ★★<b>R91-p（L 的执法腿·流体支，入口二）</b>：handler 只多挂库自带的 {@code filter} 谓词——
     * {@code FluidSlotSyncHandler#fillFluid}（上游 :352，<b>非 phantom 的点击灌入唯一落点</b>）在动任何
     * 流体之前先 {@code filter.test(heldFluid)}，不过就整支静默返回（与库内"条内是别的流体"那一拒同形）。
     * 谓词体只做一件事：把"这一列的记忆允不允许这种流体"<b>问给</b>
     * {@code PocketFilterConfig#memoryAllowsFluid}（★判据单源，本文件不含第二份 attr 读法；读的是
     * 服务端位表，fillFluid 只在 {@code readOnServer} 一支被调 ⇒ 执法面恒在服务端）。
     * ★这条谓词<b>不</b>碰 phantom / canFill / canDrain 三个开关，也<b>不</b>参与抽取方向 ⇒
     * 需求 2 的"两格同权灌排"与 GT5U 按键组合照旧，被拦下的只有"往记忆列灌它没记的那种"。
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
            final int tankIndex = tank;
            final NekoPocketFluidSlot slot = new NekoPocketFluidSlot().bindBar(ui, tank);
            slot.background(PocketGuiTextures.SLOT_TALL);
            ui.trackFluidSlot(tank, slot);
            field.child(
                (IWidget) slot.pos(column * CELL, groupTop(group) + CELL)
                    .size(CELL, TANK_HEIGHT)
                    .alwaysShowFull(false)
                    .name("pocket_fluid_slot_" + tank)
                    .syncHandler(
                        new FluidSlotSyncHandler(target).filter(
                            fluid -> ui.inventory()
                                .filters()
                                .memoryAllowsFluid(
                                    PocketFilterConfig.Kind.FLUID,
                                    tankIndex,
                                    fluid == null || fluid.getFluid() == null ? null
                                        : fluid.getFluid()
                                            .getName()))));
        }
        return field;
    }

    /**
     * 一行状态回显（★R78 D-2 允许保留的那"一行短文本"，判据见类 javadoc：它印的是服务端算出来的
     * 运行期事实——推送/拉取方向、冷却与剩余秒数、最近一次动作的回执——不是"这格怎么用"的说明）。
     * <p>
     * 文本由 {@link NekoPocketPanel} 的同步缓存格式化 ⇒ 客户端不读服务端内存表、不按 ghost 表推断
     * （R39b/R19）。★R83：撤掉自造「整理」按钮后本行占满整条末行（{@code 108}px，原 88px）；
     * 完整回执仍连同撤下来的说明进本行 tooltip（<b>不删信息</b>，R36 的宽度现实只是缓解不是解除）。
     */
    private static IWidget statusLine(NekoPocketPanel ui) {
        return new ParentWidget<>().pos(0, STATUS_Y)
            .size(WIDTH, STATUS_HEIGHT)
            .name("pocket_status_lines")
            .child(
                // ★★R92-⑥（D6）：统一缩放到 RESIDENT_TEXT_SCALE，并按"提示深色 / 读数白色"的分工
                // 显式设色（旧形状跟随主题 fallback = 0xFF404040，压在暖沙色底板上对比度不足）。
                // ★本行是全轮像素账的<b>下界卡点</b>（88×18 的盒 + mode.pull 拼回执可长到三行），
                // 理由与算式见 PocketGhostRequest#RESIDENT_TEXT_SCALE 与用例 pocket_resident_text_pixel_budget。
                (IWidget) new TextWidget(IKey.dynamic(ui::fluidStatusLine)).textAlign(Alignment.CenterLeft)
                    .scale(PocketGhostRequest.RESIDENT_TEXT_SCALE)
                    .color(PocketGhostRequest.hintTextColor())
                    .pos(0, 0)
                    .size(WIDTH - CELL - 2, STATUS_HEIGHT))
            .tooltip(tooltip -> {
                tooltip.addLine(IKey.dynamic(ui::statusHintText));
                tooltip.addLine(IKey.dynamic(ui::capacityReadoutText));
                tooltip.addLine(IKey.dynamic(ui::notesText));
            });
    }
}

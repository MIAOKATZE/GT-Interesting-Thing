package com.miaokatze.gtit.gui.pocket;

import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.api.widget.Interactable;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widgets.ProgressWidget;
import com.cleanroommc.modularui.widgets.SlotGroupWidget;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.miaokatze.gtit.common.items.pocket.PocketConstants;
import com.miaokatze.gtit.common.items.pocket.distill.PocketDistillDriver;
import com.miaokatze.gtit.crossmod.taum.TaumCompat;

/**
 * 右列 = <b>72 格源质显示盘（6 列 × 12 行）</b> + <b>一行"无槽行"</b>（唯一的一根进度条住在这里）
 * + 12 格「蒸馏 / 注入」双用输入区（<b>6 列 × 2 行</b>）（需求 2 右半）。
 * <p>
 * <b>几何（R78② 钉死，逐字照加总表）</b>：列 root {@code x = 6+108+4+180+4 = 302 宽 108 高 270}
 * （与流体块<b>同为 6 列</b> ⇒ R74② 要的"规整"由列数对齐达成）；
 * 源质盘 {@code 6 × 12 = 72} 格 = {@code 108×216} 于 {@code y=0}；
 * ★其下<b>空一行</b>（18）于 {@code y=216}；蒸馏输入 2 行 × 6 列 = {@code 108×36} 于 {@code y=234}
 * ⇒ {@code 216 + 18 + 36 = 270} <b>与中栏同高</b>（R78 的"12 行 + 空 1 行 + 蒸馏 2 行 = 15 行"）。
 * <p>
 * <b>★那一个"空行"里为什么还画了进度条（本片如实记的读法）</b>：R78 的加总表把这一行记作
 * <b>空行</b>，判据是"它不产任何槽"（15 行 / 220 槽两条加总都只数槽）；进度条同样<b>不产槽</b>，
 * 放进这一行不破任何一条数字。反之若把进度条撤掉，蒸馏的可见进度就没有任何落点
 * （R77 已裁定它不挂 C2 贴图、只走主题底，"腾不出位置"不是撤它的理由）。
 * ⇒ 裁定取"槽位意义上的空行 + 进度条住这里"；常驻的<b>蒸馏状态文字</b>则按 R74②/R78 D-2
 * 撤进 tooltip（旧那 72px 摘要段随 12 行盘一起退场）。若主代理要的是"字面全空"，
 * 唯一出路是把进度条也撤成 tooltip-only —— 那是产品决定，本文件不改数、只把这个分叉写清。
 * <p>
 * <b>72 格是纯显示件</b>（R35：源质格全部 phantom 侧，<b>不进 Container</b>，不计入 220）：
 * 每格 = {@code UITexture(location=TaumCompat.aspectTexturePath(tag)).fullImage().nonOpaque()}
 * + {@code colorOverride=colorOf(tag)}（R30：<b>{@code nonOpaque} 必给</b>，否则
 * {@code withBlend=false} 走 {@code disableBlend}，把带 alpha 的 aspect 图标画成<b>黑块</b>）。
 * <p>
 * <b>★格序不再是 {@code TaumCompat.aspectOrder()} 的固定派生序</b>（R78③）：格序 =
 * <b>该 tag 首次入账的顺序</b>，映射由<b>服务端</b>算（{@code PocketEssenceStore} 的格位归属表，
 * 落 NBT {@code essCellOrder}）并随现有源质 blob 同步过来 ⇒ 两端不各算各的（R32 的头号风险）。
 * 变的只有"哪个 tag 落在第几格"：<b>格数恒定 72、widget 树恒定</b>（内容层与归属 tag 走
 * {@link NekoEssenceGhostCell#setCellContent(String, int)} 原位换，R41b），
 * 因此数据驱动不会把双端树拉歪。溢出兜底（{@code aspect.overflow_note}）在 72 格下常态不触发，
 * 但<b>代码路径保留</b>（addon 追加 aspect 时仍可能超出 ⇒ 多于 72 的 tag 只存不显）。
 * <p>
 * <b>★空态口径 = 留格、不画内容</b>（R73② + R78 D-1）：72 格<b>固定存在且槽位底始终绘制</b>
 * （每格挂 {@link PocketGuiTextures#SLOT} 做金属凹槽底），只是"该 tag 无货"时
 * <b>不画 aspect 图标与数量文本</b>——这一条现在由 {@link NekoEssenceGhostCell#drawsContentLayer}
 * 单点决定并被 JVM 用例钉住（旧实现无条件画图标，注释却写"只撤掉图标与文本"⇒ 注释声明了
 * 代码没做的事，本片修的就是这一处）。因此本盘<b>不得</b>做成随内容伸缩的条目列表，也<b>不得</b>
 * 把空态画成"整片无格/隐藏槽位底"；格数恒定这一层同时是 R32 双端同树的前提。
 * <p>
 * <b>TC 缺席 = 整栏灰显不隐藏</b>（R31）：隐藏会让面板宽度与 NEI 避让矩形出现双分支。
 * 灰显走 {@code setEnabledIf}（仓内先例 {@code client/gui/NekoFallingItemSlotFactory.java:191-193}），
 * <b>不得</b>靠可见性开关把整栏藏掉。
 * <p>
 * <b>12 格双用（R63b）</b>：分流在 {@link PocketSlots#classifyIncoming} 单点完成——普通物品 →
 * 蒸馏判定；{@code IEssentiaContainerItem} 且有内容 → 注入支（{@code TaumCompat.drainAll →
 * canAcceptAll → putAll}，全有全无 R29，容器按 R40a 同法<b>非消耗</b>地以排空状态退回）。
 * ⇒ R44c 要关的危害照旧关闭（<b>容器根本不抵达蒸馏判定路径</b>），而需求 2 末句
 * 「源质罐子可取/放」也有了落点（R63a：不加格子 ⇒ 220 口径零冲击）。
 */
public final class NekoPocketEssenceColumn {

    /** R75/R78：右列 x 起点 = 左列 + 中栏 + 两个列间距。 */
    public static final int X = NekoPocketStorageColumn.X + NekoPocketStorageColumn.WIDTH + NekoPocketPanel.COLUMN_GAP;
    /** R75/R78：与中栏同一 y 起点。 */
    public static final int Y = NekoPocketPanel.MARGIN;
    /** R78②：源质盘<b>列数 = 6</b>（与流体块同列数；单源取 {@code ESSENCE_GRID_COLUMNS}）。 */
    public static final int ESSENCE_COLUMNS = PocketConstants.ESSENCE_GRID_COLUMNS;
    /** R78②：源质盘<b>行数 = 12</b>（6×12 = 72 = {@code ESSENCE_DISPLAY_GRID}；旧 6×8=48 作废）。 */
    public static final int ESSENCE_ROWS = PocketConstants.ESSENCE_GRID_ROWS;
    /** 蒸馏输入的列数（与源质盘同列数 ⇒ 6）。 */
    public static final int DISTILL_COLUMNS = ESSENCE_COLUMNS;
    /** R75：蒸馏输入 <b>2 行</b> × 6 列 = 12 格（格数与 §14.3 一致；R78 只把它整块下移）。 */
    public static final int DISTILL_ROWS = PocketInventory.DISTILL_INPUT_SLOTS / DISTILL_COLUMNS;
    /** R75/R78：蒸馏输入 2 行 × 6 列 = 36。 */
    public static final int DISTILL_HEIGHT = DISTILL_ROWS * NekoPocketPanel.GRID;
    /** R75/R78：右列总宽（6 列 × 18）。 */
    public static final int WIDTH = ESSENCE_COLUMNS * NekoPocketPanel.GRID;
    /** R75/R78：270（与中栏等高）。 */
    public static final int HEIGHT = NekoPocketStorageColumn.HEIGHT;

    /** 源质盘高度（R78：12 × 18 = 216）。 */
    public static final int ESSENCE_HEIGHT = ESSENCE_ROWS * NekoPocketPanel.GRID;
    /**
     * ★R78 的"空 1 行"高度（一个格高、<b>零槽</b>）：它把 72 格盘与蒸馏盘隔开，
     * 并且是唯一进度条的落点（读法与理由见类 javadoc 的那一段★）。
     */
    public static final int SEPARATOR_HEIGHT = NekoPocketPanel.GRID;
    /** 蒸馏盘的 y 起点 = 盘面高 + 空行。 */
    public static final int DISTILL_Y = ESSENCE_HEIGHT + SEPARATOR_HEIGHT;

    /** 蒸馏输入的布局字面量（2 行 × 6 列 = 12，与 {@code distillInput()} 的 handler 索引同序）。 */
    private static final String[] DISTILL_MATRIX = { "DDDDDD", "DDDDDD" };

    private NekoPocketEssenceColumn() {}

    /**
     * 布局字符的出现总数 = 本矩阵产出的真实槽数（机检 + 装配期断言的输入）。
     * <p>
     * ★存在的理由（R77 实测拿到的库行为）：{@code SlotGroupWidget$Builder.build()} 用
     * {@code Char2IntOpenHashMap} 计数 ⇒ <b>每个布局字符各自从 0 起</b>。因此
     * <b>一块矩阵只允许一个布局字符</b>：出现第二个就把一段索引空间劈成两条重叠的 0…n，
     * 表现为"槽数少一半 + 两行写同一段 handler"。首开即被 {@code assertTotalRealSlots()} 拦下
     * （双端同抛，不静默），但整块区域不可用 ⇒ 在这里当场炸，并让回归套件能直接读这个数。
     */
    public static int layoutSlotCount() {
        int total = 0;
        for (String row : DISTILL_MATRIX) {
            for (int index = 0; index < row.length(); index++) {
                if (row.charAt(index) == 'D') {
                    total++;
                }
            }
        }
        return total;
    }

    static {
        if (layoutSlotCount() != PocketInventory.DISTILL_INPUT_SLOTS) {
            throw new IllegalStateException("[pocket] 蒸馏输入矩阵产出 " + layoutSlotCount() + " 格，与 handler 格数不符");
        }
        // R78 的三段闭合：12 行盘 + 空 1 行 + 2 行蒸馏 = 15 行 = 与中栏同高（★不留负段、不重叠）
        if (ESSENCE_HEIGHT + SEPARATOR_HEIGHT + DISTILL_HEIGHT != HEIGHT) {
            throw new IllegalStateException(
                "[pocket] 右列三段高度不闭合: " + (ESSENCE_HEIGHT + SEPARATOR_HEIGHT + DISTILL_HEIGHT) + " != " + HEIGHT);
        }
        if (ESSENCE_COLUMNS * ESSENCE_ROWS != PocketConstants.ESSENCE_DISPLAY_GRID) {
            throw new IllegalStateException("[pocket] 源质盘行列乘积不等于格数");
        }
    }

    /** 装配右列。 */
    public static ParentWidget<?> build(NekoPocketPanel ui) {
        final SlotGroupWidget distill = SlotGroupWidget.builder()
            .matrix(DISTILL_MATRIX)
            .key('D', index -> {
                final com.cleanroommc.modularui.widgets.slot.ItemSlot slot = new com.cleanroommc.modularui.widgets.slot.ItemSlot();
                slot.slot(
                    ui.slots()
                        .distillInput(ui.inventory(), index));
                slot.name("distill_" + index);
                slot.background(PocketGuiTextures.SLOT);
                // R63b 的双用口径必须让玩家读得到（still.in 已从"待蒸馏物品"改成"输入（物品或容器）"）
                slot.tooltip(tooltip -> {
                    tooltip.addLine(IKey.lang("gtit.pocket.still.in"));
                    tooltip.addLine(
                        IKey.lang(
                            "gtit.pocket.still.dual_use_note",
                            () -> new Object[] { PocketInventory.DISTILL_INPUT_SLOTS }));
                });
                return slot;
            })
            .synced(PocketSlots.SYNC_DISTILL)
            .slotGroup(PocketSlots.GROUP_DISTILL)
            .build();
        distill.pos(0, DISTILL_Y);

        final ParentWidget<?> root = new ParentWidget<>().pos(X, Y)
            .size(WIDTH, HEIGHT)
            .name("pocket_essence_column")
            .child(essenceGrid(ui))
            .child(progressBar(ui))
            .child(distill);
        // R31：TC 缺席整栏灰显（不隐藏 ⇒ 面板宽度与 NEI 避让不出现双分支）
        root.setEnabledIf(widget -> ui.essenceAvailable());
        return root.excludeAreaInRecipeViewer();
    }

    /**
     * 72 格源质显示盘（<b>6 列 × 12 行</b>，R78②）。
     * <p>
     * 装配期只按<b>恒定格数</b>铺 72 个 {@link NekoEssenceGhostCell}（行主序：
     * {@code column = index % 6}、{@code row = index / 6}）；每格归属哪个 tag 由
     * {@link NekoPocketPanel#essenceTagAtCell(int)} 现读（★服务端算好、随源质 blob 同步过来，
     * 见 {@code PocketEssenceStore} 的格位归属表与 R78③）。格数与格序都不随内容伸缩 ⇒
     * widget 树不因数据变化（R32）。
     * <p>
     * <b>需求 4 的源质入口已实装</b>（S-E，R70）：每格是 {@link NekoEssenceGhostCell}
     * （{@code extends ButtonWidget} <b>并</b>实现 {@code RecipeViewerGhostIngredientSlot}，
     * 原来那版裸 {@code ButtonWidget} 不实现该接口 ⇒ 面板的 hover+instanceof 分发看不到它 ⇒
     * 游戏内零入口）。NEI 左键拖入一个<b>确实含本格 tag</b>的物品或容器 ⇒ 就地声明
     * {@code (Kind.ESSENCE, 本格格号)}；不含该 tag ⇒ 返回 false、不吃栈。右键在声明态发
     * {@code CLR|<格号>|E}。索引空间上界 = {@code PocketConstants.GHOST_ESSENCE_SLOT_LIMIT}（72），
     * 与中栏 0…134、流体 tank 0…17 各自独立（R59b 偏离④的复合键）。
     * ★<b>ghost 声明按格号索引，所以"撤空不回收格位"是它正确性的前提</b>（R78③）：一旦回收，
     * 已声明的格就会指向别的 tag 并拉错东西。
     * <p>
     * 需求 2 的"要素栏取出 → 晶化源质"是另一条独立路径（R15），点击/Shift 走 {@code ESSENCE_OUT}
     * 动作码（★arg 里的格号在服务端经格位归属表反查 tag，不吃客户端送来的 tag，R18/R19）。
     */
    private static IWidget essenceGrid(NekoPocketPanel ui) {
        final ParentWidget<?> grid = new ParentWidget<>().pos(0, 0)
            .size(WIDTH, ESSENCE_HEIGHT)
            .name("pocket_essence_grid");
        for (int cell = 0; cell < ESSENCE_COLUMNS * ESSENCE_ROWS; cell++) {
            grid.child(essenceCell(ui, cell));
        }
        return grid;
    }

    /**
     * 单格：金属凹槽底（<b>恒画</b>，R73②）+ 内容层（aspect 图标 + 数量文本，
     * <b>★按库存开关</b>，R78 D-1）+ tooltip；点击=取出晶化源质，左键拖入=声明 ghost（落点归本列）。
     * <p>
     * ★无货时<b>只撤掉图标与文本</b>，底与格子本体都在——这就是 R73② 与"槽位贴图边距 ≤ 3"
     * （{@link PocketGuiTextures#MAX_SLOT_SLICE_MARGIN}）两条裁定的共同落点。<b>这段描述现在与
     * 代码一致</b>：撤的动作由 {@link NekoEssenceGhostCell#setCellContent(String, int)} 真的做
     * （{@code overlay()} 清空 + 数量文本空串），并由 JVM 用例
     * {@code essence_cell_content_layer_follows_stock} 钉住（旧实现只在注释里说撤、代码无条件画）。
     */
    private static IWidget essenceCell(NekoPocketPanel ui, int index) {
        final int column = index % ESSENCE_COLUMNS;
        final int row = index / ESSENCE_COLUMNS;
        final NekoEssenceGhostCell cell = new NekoEssenceGhostCell().bindCell(ui, index, ui.essenceTagAtCell(index))
            .pos(column * NekoPocketPanel.GRID, row * NekoPocketPanel.GRID)
            .size(NekoPocketPanel.GRID, NekoPocketPanel.GRID)
            .name("pocket_essence_cell_" + index)
            .background(PocketGuiTextures.SLOT)
            .playClickSound(false)
            // ★tag 现读（读的是同步镜像里"这一格当前的归属 tag"）：R78③ 后格位归属会变，
            // 装配期捕获的 tag 到点击时可能已经不是这一格的了
            .onMousePressed(
                button -> ui.requestEssenceOut(index, ui.essenceTagAtCell(index), Interactable.hasShiftDown()));
        ui.trackEssenceCell(index, cell);
        cell.child(
            (IWidget) new TextWidget(IKey.dynamic(cell::stockText)).textAlign(Alignment.BottomRight)
                .scale(0.5f)
                .pos(0, 8)
                .size(17, 9));
        cell.tooltip(tooltip -> {
            final String tag = cell.aspectTag();
            if (tag == null) {
                // 空格位：没有归属 tag ⇒ 只给"这一格还空着/TC 不在场"的读法（R31 的整栏灰显另有 tooltip）
                tooltip.addLine(
                    IKey.lang(ui.essenceAvailable() ? "gtit.pocket.aspect.empty" : "gtit.pocket.still.unavailable"));
                if (ui.essenceOverflow()) {
                    tooltip.addLine(
                        IKey.lang(
                            "gtit.pocket.aspect.overflow_note",
                            () -> new Object[] { PocketConstants.ESSENCE_DISPLAY_GRID }));
                }
                return;
            }
            tooltip.addLine(IKey.str(EnumChatFormatting.WHITE + TaumCompat.nameOf(tag)));
            tooltip.addLine(IKey.dynamic(() -> ui.essenceAmountDetail(tag)));
            if (ui.essenceOverflow()) {
                tooltip.addLine(
                    IKey.lang(
                        "gtit.pocket.aspect.overflow_note",
                        () -> new Object[] { PocketConstants.ESSENCE_DISPLAY_GRID }));
            }
            if (cell.isGhost()) {
                // 声明态补一行"配置格：<tag>（右键取消）"；本格仍可点取，故不复用 ghost.locked
                tooltip.addLine(IKey.lang("gtit.pocket.ghost.on", TaumCompat.nameOf(tag)));
            }
        });
        return cell;
    }

    /**
     * 唯一的一根进度条（R78：住在 12 行盘与蒸馏盘之间那一个<b>零槽</b>的"空行"里，
     * {@code y=216}、高 18；读法与理由见类 javadoc 的★段）。
     * <p>
     * 值走 {@link NekoPocketPanel#distillProgressValue()} 的同步通道：<b>GUI 只显示、绝不推进</b>
     * （推进是 {@code Item.onUpdate} 服务端分支里 {@code PocketDistillDriver} 的节拍，
     * 单一权威 {@code TaumDistillRules.DISTILL_INTERVAL_TICKS}，计划 §17.2 第 4 条）。
     * <p>
     * ★R78 D-2：旧那 72px 的常驻"状态摘要段"（{@code still.title} + 状态行）随 12 行盘退场，
     * 四条状态文案（{@code still.progress} / {@code still.idle} / {@code still.no_aspect} /
     * {@code still.full}）现在<b>只</b>走本条与蒸馏格的 tooltip；都是 {@code IKey.dynamic}，
     * 每次悬停现读服务端同步过来的状态位，不在客户端复算状态机。
     * 进度条自身就是"跑到哪了"的可见状态回显 ⇒ 撤走的是文字、不是可见面。
     */
    private static IWidget progressBar(NekoPocketPanel ui) {
        return new ProgressWidget().pos(0, ESSENCE_HEIGHT)
            .size(WIDTH, SEPARATOR_HEIGHT)
            .name("pocket_distill_progress")
            // ★不挂 C2 贴图：契约表（HTML 355–366 行）没有进度条专用件，
            // 借 coinbar/btn 会把"铜牌 + 两铆钉"画成进度槽。这里保持主题默认底，
            // 已在 R77 作为"契约缺件"项登记（贴图齐备后若要进度条皮肤，得先补契约行）。
            .direction(ProgressWidget.Direction.RIGHT)
            .value(ui.distillProgressValue())
            .tooltip(tooltip -> {
                tooltip.addLine(IKey.lang("gtit.pocket.still.title"));
                tooltip.addLine(
                    IKey.lang(
                        "gtit.pocket.still.dual_use_note",
                        () -> new Object[] { PocketInventory.DISTILL_INPUT_SLOTS }));
                tooltip.addLine(IKey.dynamic(() -> distillStateLine(ui)));
            });
    }

    /** 蒸馏状态行：按 {@code PocketDistillDriver} 回报的状态位挑对应的 {@code still.*} 键（不新造键）。 */
    private static String distillStateLine(NekoPocketPanel ui) {
        final PocketDistillDriver.Status status = ui.distillStatus();
        switch (status) {
            case STORE_FULL:
                return StatCollector.translateToLocal("gtit.pocket.still.full");
            case NO_ASPECT:
                return StatCollector.translateToLocal("gtit.pocket.still.no_aspect");
            case RUNNING:
                return String
                    .format(StatCollector.translateToLocal("gtit.pocket.still.progress"), ui.distillSecondsToNext());
            case IDLE:
            default:
                return StatCollector.translateToLocal("gtit.pocket.still.idle");
        }
    }
}

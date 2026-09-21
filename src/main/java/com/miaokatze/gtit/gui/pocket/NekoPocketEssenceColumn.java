package com.miaokatze.gtit.gui.pocket;

import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.api.widget.Interactable;
import com.cleanroommc.modularui.drawable.UITexture;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widgets.ProgressWidget;
import com.cleanroommc.modularui.widgets.SlotGroupWidget;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.miaokatze.gtit.common.items.pocket.PocketConstants;
import com.miaokatze.gtit.common.items.pocket.distill.PocketDistillDriver;
import com.miaokatze.gtit.crossmod.taum.TaumCompat;

/**
 * 右列 = 48 格源质显示盘（<b>6 列 × 8 行</b>）+ 12 格「蒸馏 / 注入」双用输入区（<b>6 列 × 2 行</b>）
 * + <b>一根</b>进度条 + 蒸馏状态摘要（需求 2 右半）。
 * <p>
 * <b>几何（R75 钉死）</b>：列 root {@code x = 6+108+4+180+4 = 302 宽 108 高 270}
 * （与流体块<b>同为 6 列</b> ⇒ R74② 要的"规整"由列数对齐达成）；
 * 源质 <b>6 列 × 8 行 = 48</b>（R75：只换排布，<b>总数不变</b> ⇒ {@link PocketConstants#ESSENCE_DISPLAY_GRID}
 * 与 {@link PocketConstants#GHOST_ESSENCE_SLOT_LIMIT} 都不动）= {@code 108×144} 于 {@code y=0}；
 * 蒸馏输入 2 行 × 6 列 = {@code 108×36} 于 {@code y=144}；进度条 18 于 {@code y=180}；
 * 剩 {@code 270-198 = 72} 给状态摘要 ⇒
 * <b>蒸馏状态文案（{@code gtit.pocket.still.*}）仍一律走 tooltip，不得常驻渲染</b>（同一算式的硬推论）。
 * <p>
 * <b>48 格是纯显示件</b>（R35：源质格全部 phantom 侧，<b>不进 Container</b>，不计入 175）：
 * 每格 = {@code UITexture(location=TaumCompat.aspectTexturePath(tag)).fullImage().nonOpaque()}
 * + {@code colorOverride=colorOf(tag)}（R30：<b>{@code nonOpaque} 必给</b>，否则
 * {@code withBlend=false} 走 {@code disableBlend}，把带 alpha 的 aspect 图标画成<b>黑块</b>）。
 * 行序与颜色<b>运行时派生</b>（R26/R1'：GT5U 实测 {@code TCAspects} 有 59 项，48 不是恒值，
 * 反编译转储的颜色字面量会被写坏）⇒ 第 49 项起<b>只存不显</b>（{@code aspect.overflow_note}）。
 * <p>
 * <b>★空态口径 = 留格、不画内容</b>（R73②，用户原话「空格子不显示源质，但是格子本身要显示，
 * 不能没任何格子」）：48 格<b>固定存在且槽位底始终绘制</b>（每格挂
 * {@link PocketGuiTextures#SLOT} 做金属凹槽底），只是"该 tag 无货"时<b>不画 aspect 图标与数量文本</b>。
 * 因此本盘<b>不得</b>做成随内容伸缩的条目列表，也<b>不得</b>把空态画成"整片无格/隐藏槽位底"；
 * 格数恒定这一层同时是 R32 双端同树的前提。
 * <p>
 * <b>TC 缺席 = 整栏灰显不隐藏</b>（R31）：隐藏会让面板宽度与 NEI 避让矩形出现双分支。
 * 灰显走 {@code setEnabledIf}（仓内先例 {@code client/gui/NekoFallingItemSlotFactory.java:191-193}），
 * <b>不得</b>靠可见性开关把整栏藏掉。
 * <p>
 * <b>12 格双用（R63b，改写任务包约束 8 里那句「蒸馏输入槽必须拒绝源质容器」）</b>：
 * 分流在 {@link PocketSlots#classifyIncoming} 单点完成——普通物品 → 蒸馏判定；
 * {@code IEssentiaContainerItem} 且有内容 → 注入支（{@code TaumCompat.drainAll →
 * canAcceptAll → putAll}，全有全无 R29，容器按 R40a 同法<b>非消耗</b>地以排空状态退回）。
 * ⇒ R44c 要关的危害照旧关闭（<b>容器根本不抵达蒸馏判定路径</b>），而需求 2 末句
 * 「源质罐子可取/放」也有了落点（R63a：不加格子 ⇒ 175 口径零冲击）。
 */
public final class NekoPocketEssenceColumn {

    /** R75：右列 x 起点 = 左列 + 中栏 + 两个列间距。 */
    public static final int X = NekoPocketStorageColumn.X + NekoPocketStorageColumn.WIDTH + NekoPocketPanel.COLUMN_GAP;
    /** R75：与中栏同一 y 起点。 */
    public static final int Y = NekoPocketPanel.MARGIN;
    /** R75：源质盘<b>列数 = 6</b>（与流体块同列数；旧口径 4 列 × 12 行作废）。 */
    public static final int ESSENCE_COLUMNS = PocketConstants.FLUID_COLUMN_COUNT;
    /** R75：源质盘<b>行数 = 8</b>（6×8 = 48 = {@code TaumCompat.DISPLAY_CELLS}，总数不变 ⇒ 只换排布）。 */
    public static final int ESSENCE_ROWS = PocketConstants.ESSENCE_DISPLAY_GRID / ESSENCE_COLUMNS;
    /** 蒸馏输入的列数（与源质盘同列数 ⇒ 6）。 */
    public static final int DISTILL_COLUMNS = ESSENCE_COLUMNS;
    /** R75：蒸馏输入 <b>2 行</b> × 6 列 = 12 格（格数与 §14.3 一致，只换排布）。 */
    public static final int DISTILL_ROWS = PocketInventory.DISTILL_INPUT_SLOTS / DISTILL_COLUMNS;
    /** R75：蒸馏输入 2 行 × 6 列 = 36。 */
    public static final int DISTILL_HEIGHT = DISTILL_ROWS * NekoPocketPanel.GRID;
    /** R75：右列总宽（6 列 × 18）。 */
    public static final int WIDTH = ESSENCE_COLUMNS * NekoPocketPanel.GRID;
    /** R75：270（与中栏等高）。 */
    public static final int HEIGHT = NekoPocketStorageColumn.HEIGHT;

    /** 源质盘高度（8 × 18 = 144）。 */
    public static final int ESSENCE_HEIGHT = ESSENCE_ROWS * NekoPocketPanel.GRID;
    /** 状态摘要段高度 = 列高减去上面三段（永远是正数，见类 javadoc 的算式）。 */
    public static final int NOTE_HEIGHT = HEIGHT - ESSENCE_HEIGHT - DISTILL_HEIGHT - NekoPocketPanel.GRID;
    /** 状态摘要段的行高。 */
    private static final int LINE = 11;

    /** 蒸馏输入的布局字面量（2 行 × 6 列 = 12，与 {@code distillInput()} 的 handler 索引同序）。 */
    private static final String[] DISTILL_MATRIX = { "DDDDDD", "DDDDDD" };

    private NekoPocketEssenceColumn() {}

    /**
     * 布局字符的出现总数 = 本矩阵产出的真实槽数（机检 + 装配期断言的输入）。
     * <p>
     * ★存在的理由（本批实测拿到的库行为）：{@code SlotGroupWidget$Builder.build()} 用
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
        distill.pos(0, ESSENCE_HEIGHT);

        final ParentWidget<?> root = new ParentWidget<>().pos(X, Y)
            .size(WIDTH, HEIGHT)
            .name("pocket_essence_column")
            .child(essenceGrid(ui))
            .child(distill)
            .child(progressBar(ui))
            .child(statusNote(ui));
        // R31：TC 缺席整栏灰显（不隐藏 ⇒ 面板宽度与 NEI 避让不出现双分支）
        root.setEnabledIf(widget -> ui.essenceAvailable());
        return root.excludeAreaInRecipeViewer();
    }

    /**
     * 48 格源质显示盘（<b>6 列 × 8 行</b>，R75 只换排布）。
     * <p>
     * 格序 = {@code TaumCompat.aspectOrder()} 的运行时派生序（双端同 mod 集 ⇒ 同序，无需 baked 表），
     * 行主序按"先行后列"铺（{@code column = index % 6}、{@code row = index / 6}）；
     * 超出 48 的项<b>只存不显</b>（R26），由 {@code aspect.overflow_note} 说明。
     * <p>
     * <b>需求 4 的源质入口已实装</b>（S-E，R70）：每格是 {@link NekoEssenceGhostCell}（
     * {@code extends ButtonWidget} <b>并</b>实现 {@code RecipeViewerGhostIngredientSlot}，
     * 原来那版裸 {@code ButtonWidget} 不实现该接口 ⇒ 面板的 hover+instanceof 分发看不到它 ⇒
     * 游戏内零入口）。NEI 左键拖入一个<b>确实含本格 tag</b>的物品或容器 ⇒ 就地声明
     * {@code (Kind.ESSENCE, 本格格号)}；不含该 tag ⇒ 返回 false、不吃栈。右键在声明态发
     * {@code CLR|<格号>|E}。索引空间上界 = {@code PocketConstants.GHOST_ESSENCE_SLOT_LIMIT}，
     * 与中栏 0…149、流体槽 0…5 各自独立（R59b 偏离④的复合键）。
     * 需求 2 的"要素栏取出 → 晶化源质"是另一条独立路径（R15），点击/Shift 走 {@code ESSENCE_OUT}
     * 动作码，本批<b>未改</b>其语义；6×8 布局一格不加不减（R41b/R75）。
     */
    private static IWidget essenceGrid(NekoPocketPanel ui) {
        final ParentWidget<?> grid = new ParentWidget<>().pos(0, 0)
            .size(WIDTH, ESSENCE_HEIGHT)
            .name("pocket_essence_grid");
        final String[] order = TaumCompat.aspectOrder();
        for (int cell = 0; cell < ESSENCE_COLUMNS * ESSENCE_ROWS; cell++) {
            final int index = cell;
            final String tag = index < order.length ? order[index] : null;
            grid.child(essenceCell(ui, index, tag));
        }
        return grid;
    }

    /**
     * 单格：金属凹槽底（<b>恒画</b>，R73②）+ aspect 图标（着色）+ 数量浮层 + tooltip；
     * 点击=取出晶化源质，左键拖入=声明 ghost（落点归本列）。
     * <p>
     * ★无货时<b>只撤掉图标与文本</b>，底与格子本体都在——这就是 R73② 与"槽位贴图边距 ≤ 3"
     * （{@link PocketGuiTextures#MAX_SLOT_SLICE_MARGIN}）两条裁定的共同落点。
     */
    private static IWidget essenceCell(NekoPocketPanel ui, int index, String tag) {
        final int column = index % ESSENCE_COLUMNS;
        final int row = index / ESSENCE_COLUMNS;
        final NekoEssenceGhostCell cell = new NekoEssenceGhostCell().bindCell(ui, index, tag)
            .pos(column * NekoPocketPanel.GRID, row * NekoPocketPanel.GRID)
            .size(NekoPocketPanel.GRID, NekoPocketPanel.GRID)
            .name("pocket_essence_cell_" + index)
            .background(PocketGuiTextures.SLOT)
            .playClickSound(false)
            .onMousePressed(button -> ui.requestEssenceOut(index, tag, Interactable.hasShiftDown()));
        ui.trackEssenceCell(index, cell);
        if (tag == null) {
            // 派生表不足 48 项（addon 缺失或 TC 不在场）：格子仍占位（不隐藏），只是没有内容可画
            return cell.tooltip(tooltip -> tooltip.addLine(IKey.lang("gtit.pocket.still.unavailable")));
        }
        final UITexture icon = UITexture.builder()
            .location(TaumCompat.aspectTexturePath(tag))
            .fullImage()
            .nonOpaque()
            .build();
        final int color = TaumCompat.colorOf(tag);
        // ★图标进 overlay 而不是 background：background 已被"槽位底"占用，两者叠反会让凹槽消失
        cell.overlay(color == TaumCompat.COLOR_UNKNOWN ? icon : icon.withColorOverride(color));
        cell.child(
            (IWidget) new TextWidget(IKey.dynamic(() -> ui.essenceAmountText(index))).scale(0.5f)
                .textAlign(Alignment.BottomRight)
                .pos(0, 8)
                .size(17, 9));
        cell.tooltip(tooltip -> {
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
     * 唯一的一根进度条（R75 算式：{@code 144+36 = 180} ⇒ 条在 {@code y=180}，高 18）。
     * <p>
     * 值走 {@link NekoPocketPanel#distillProgressValue()} 的同步通道：<b>GUI 只显示、绝不推进</b>
     * （推进是 {@code Item.onUpdate} 服务端分支里 {@code PocketDistillDriver} 的节拍，
     * 单一权威 {@code TaumDistillRules.DISTILL_INTERVAL_TICKS}，计划 §17.2 第 4 条）。
     * <p>
     * 状态文案（{@code still.progress} / {@code still.idle} / {@code still.no_aspect} /
     * {@code still.full}）走本条与 {@link #statusNote} 的 tooltip，<b>不常驻渲染</b>。
     * 四条都是 {@code IKey.dynamic}，每次悬停现读服务端的同步值，不在客户端复算状态机。
     */
    private static IWidget progressBar(NekoPocketPanel ui) {
        return new ProgressWidget().pos(0, ESSENCE_HEIGHT + DISTILL_HEIGHT)
            .size(WIDTH, NekoPocketPanel.GRID)
            .name("pocket_distill_progress")
            // ★不挂 C2 贴图：契约表（HTML 355–366 行）没有进度条专用件，
            // 借 coinbar/btn 会把"铜牌 + 两铆钉"画成进度槽。这里保持主题默认底，
            // 已在回执里作为"契约缺件"项上报（贴图齐备后若要进度条皮肤，得先补契约行）。
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

    /**
     * 蒸馏/注入状态摘要（R75：源质列腾出的 72px 段）。
     * <p>
     * 常驻只有两行标题级文本，完整状态走 tooltip（同 {@link #progressBar}）；
     * 这一段的存在本身就是"删掉第四列"留下的落点回收，不放任何只有这里才有的新信息。
     */
    private static IWidget statusNote(NekoPocketPanel ui) {
        return new ParentWidget<>().pos(0, ESSENCE_HEIGHT + DISTILL_HEIGHT + NekoPocketPanel.GRID)
            .size(WIDTH, NOTE_HEIGHT)
            .name("pocket_essence_notes")
            .child(
                (IWidget) new TextWidget(IKey.lang("gtit.pocket.still.title")).textAlign(Alignment.TopLeft)
                    .scale(0.5f)
                    .pos(0, 0)
                    .size(WIDTH, LINE))
            .child(
                (IWidget) new TextWidget(IKey.dynamic(() -> distillStateLine(ui))).textAlign(Alignment.TopLeft)
                    .scale(0.5f)
                    .pos(0, LINE)
                    .size(WIDTH, 2 * LINE))
            .tooltip(tooltip -> tooltip.addLine(IKey.dynamic(() -> distillStateLine(ui))));
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

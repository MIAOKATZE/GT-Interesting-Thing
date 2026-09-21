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
import com.miaokatze.gtit.common.items.pocket.distill.PocketDistillDriver;
import com.miaokatze.gtit.crossmod.taum.TaumCompat;

/**
 * 右栏 = 48 格源质显示盘 + 12 格「蒸馏 / 注入」双用输入区 + <b>一根</b>进度条（需求 2 右半）。
 * <p>
 * <b>几何（§16.1/§16.2 + R33）</b>：列 root {@code x=252 宽 72 高 288}；
 * 源质 <b>4 列 × 12 行</b>（R33 字面口径，<b>不是</b> E1 早先排的 8 列）= {@code 72×216} 于 {@code y=0}；
 * 蒸馏输入 3 行 × 4 列 = {@code 72×54} 于 {@code y=216}；
 * {@code 216+54=270} ⇒ 只剩 {@code 18px} ⇒ <b>只能放一根进度条</b>，且
 * <b>蒸馏状态文案（{@code gtit.pocket.still.*}）一律走 tooltip，不得常驻渲染</b>（§16.2 硬推论）。
 * <p>
 * <b>48 格是纯显示件</b>（R35：源质格全部 phantom 侧，<b>不进 Container</b>，不计入 149）：
 * 每格 = {@code UITexture(location=TaumCompat.aspectTexturePath(tag)).fullImage().nonOpaque()}
 * + {@code colorOverride=colorOf(tag)}（R30：<b>{@code nonOpaque} 必给</b>，否则
 * {@code withBlend=false} 走 {@code disableBlend}，把带 alpha 的 aspect 图标画成<b>黑块</b>）。
 * 行序与颜色<b>运行时派生</b>（R26/R1'：GT5U 实测 {@code TCAspects} 有 59 项，48 不是恒值，
 * 反编译转储的颜色字面量会被写坏）⇒ 第 49 项起<b>只存不显</b>（{@code aspect.overflow_note}）。
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
 * 「源质罐子可取/放」也有了落点（R63a：不加格子 ⇒ §16 闭合与 149 口径零冲击）。
 */
public final class NekoPocketEssenceColumn {

    /** §16.1：右栏 x 起点。 */
    public static final int X = 252;
    /** §16.2：与中栏同一 y 起点。 */
    public static final int Y = 6;
    /** §16.1：右栏总宽（4 列 × 18）。 */
    public static final int WIDTH = 72;
    /** §16.2：288。 */
    public static final int HEIGHT = 288;

    /** R33：源质盘<b>列数 = 4</b>（不是 8）。 */
    public static final int ESSENCE_COLUMNS = 4;
    /** R33：源质盘<b>行数 = 12</b>（4×12 = 48 = {@code TaumCompat.DISPLAY_CELLS}）。 */
    public static final int ESSENCE_ROWS = 12;
    /** 源质盘高度（12 × 18 = 216）。 */
    public static final int ESSENCE_HEIGHT = ESSENCE_ROWS * 18;
    /** §16.2：蒸馏输入 3 行 × 4 列 = 54。 */
    public static final int DISTILL_HEIGHT = 3 * 18;
    /** §16.2：只剩 18px ⇒ 一根进度条，高度钉死 18。 */
    public static final int PROGRESS_HEIGHT = HEIGHT - ESSENCE_HEIGHT - DISTILL_HEIGHT;

    /** 蒸馏输入的布局字面量（3 行 × 4 列 = 12，与 {@code distillInput()} 的 handler 索引同序）。 */
    private static final String[] DISTILL_MATRIX = { "DDDD", "DDDD", "DDDD" };

    private NekoPocketEssenceColumn() {}

    /** 装配右栏。 */
    public static ParentWidget<?> build(NekoPocketPanel ui) {
        final SlotGroupWidget distill = SlotGroupWidget.builder()
            .matrix(DISTILL_MATRIX)
            .key('D', index -> {
                final com.cleanroommc.modularui.widgets.slot.ItemSlot slot = new com.cleanroommc.modularui.widgets.slot.ItemSlot();
                slot.slot(
                    ui.slots()
                        .distillInput(ui.inventory(), index));
                slot.name("distill_" + index);
                // R63b 的双用口径必须让玩家读得到（still.in 已从"待蒸馏物品"改成"输入（物品或容器）"）
                slot.tooltip(tooltip -> {
                    tooltip.addLine(IKey.lang("gtit.pocket.still.in"));
                    tooltip.addLine(IKey.lang("gtit.pocket.still.dual_use_note"));
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
            .child(progressBar(ui));
        // R31：TC 缺席整栏灰显（不隐藏 ⇒ 面板宽度与 NEI 避让不出现双分支）
        root.setEnabledIf(widget -> ui.essenceAvailable());
        return root.excludeAreaInRecipeViewer();
    }

    /**
     * 48 格源质显示盘（4 列 × 12 行）。
     * <p>
     * 格序 = {@code TaumCompat.aspectOrder()} 的运行时派生序（双端同 mod 集 ⇒ 同序，无需 baked 表）；
     * 超出 48 的项<b>只存不显</b>（R26），由 {@code aspect.overflow_note} 说明。
     * <p>
     * <b>需求 4 的源质入口已实装</b>（S-E，R70）：每格是 {@link NekoEssenceGhostCell}（
     * {@code extends ButtonWidget} <b>并</b>实现 {@code RecipeViewerGhostIngredientSlot}，
     * 原来那版裸 {@code ButtonWidget} 不实现该接口 ⇒ 面板的 hover+instanceof 分发看不到它 ⇒
     * 游戏内零入口）。NEI 左键拖入一个<b>确实含本格 tag</b>的物品或容器 ⇒ 就地声明
     * {@code (Kind.ESSENCE, 本格格号)}；不含该 tag ⇒ 返回 false、不吃栈。右键在声明态发
     * {@code CLR|<格号>|E}。索引空间上界 = {@code PocketConstants.GHOST_ESSENCE_SLOT_LIMIT}，
     * 与中栏 0…127、流体条 0…0 各自独立（R59b 偏离④的复合键）。
     * 需求 2 的"要素栏取出 → 晶化源质"是另一条独立路径（R15），点击/Shift 走 {@code ESSENCE_OUT}
     * 动作码，本批<b>未改</b>其语义；4×12 布局一格不加不减（R41b/R33）。
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

    /** 单格：aspect 图标（着色）+ 数量浮层 + tooltip；点击=取出晶化源质，左键拖入=声明 ghost（落点归本列）。 */
    private static IWidget essenceCell(NekoPocketPanel ui, int index, String tag) {
        final int column = index % ESSENCE_COLUMNS;
        final int row = index / ESSENCE_COLUMNS;
        final NekoEssenceGhostCell cell = new NekoEssenceGhostCell().bindCell(ui, index, tag)
            .pos(column * 18, row * 18)
            .size(18, 18)
            .name("pocket_essence_cell_" + index)
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
        cell.background(color == TaumCompat.COLOR_UNKNOWN ? icon : icon.withColorOverride(color));
        cell.child(
            (IWidget) new TextWidget(IKey.dynamic(() -> ui.essenceAmountText(index))).scale(0.5f)
                .textAlign(Alignment.BottomRight)
                .pos(0, 8)
                .size(17, 9));
        cell.tooltip(tooltip -> {
            tooltip.addLine(IKey.str(EnumChatFormatting.WHITE + TaumCompat.nameOf(tag)));
            tooltip.addLine(IKey.dynamic(() -> ui.essenceAmountDetail(tag)));
            if (ui.essenceOverflow()) {
                tooltip.addLine(IKey.lang("gtit.pocket.aspect.overflow_note"));
            }
            if (cell.isGhost()) {
                // 声明态补一行"配置格：<tag>（右键取消）"；本格仍可点取，故不复用 ghost.locked
                tooltip.addLine(IKey.lang("gtit.pocket.ghost.on", TaumCompat.nameOf(tag)));
            }
        });
        return cell;
    }

    /**
     * 唯一的一根进度条（§16.2：216+54=270 ⇒ 只剩 18px）。
     * <p>
     * 值走 {@link NekoPocketPanel#distillProgressValue()} 的同步通道：<b>GUI 只显示、绝不推进</b>
     * （推进是 {@code Item.onUpdate} 服务端分支里 {@code PocketDistillDriver} 的节拍，
     * 单一权威 {@code TaumDistillRules.DISTILL_INTERVAL_TICKS}，计划 §17.2 第 4 条）。
     * <p>
     * 状态文案（{@code still.progress} / {@code still.idle} / {@code still.no_aspect} /
     * {@code still.full}）走本条的 tooltip，<b>不常驻渲染</b>——§16.2 只剩 18px 是算术结论。
     * 四条都是 {@code IKey.dynamic}，每次悬停现读服务端的同步值，不在客户端复算状态机。
     */
    private static IWidget progressBar(NekoPocketPanel ui) {
        return new ProgressWidget().pos(0, ESSENCE_HEIGHT + DISTILL_HEIGHT)
            .size(WIDTH, PROGRESS_HEIGHT)
            .name("pocket_distill_progress")
            .direction(ProgressWidget.Direction.RIGHT)
            .value(ui.distillProgressValue())
            .tooltip(tooltip -> {
                tooltip.addLine(IKey.lang("gtit.pocket.still.title"));
                tooltip.addLine(IKey.lang("gtit.pocket.still.dual_use_note"));
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

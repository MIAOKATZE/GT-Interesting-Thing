package com.miaokatze.gtit.gui.pocket;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.item.ItemStack;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widget.ScrollWidget;
import com.cleanroommc.modularui.widget.scroll.VerticalScrollData;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.SlotGroupWidget;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.miaokatze.gtit.common.items.pocket.PocketConstants;

/**
 * 第四列 = 需求 3 的<b>右</b>配置栏（元件属性 + 所在位置「维度+xyz」）+ 需求 5 的绑定入口。
 * <p>
 * <b>几何（§16.1/§16.3 + R43d）</b>：列 root {@code x=328 宽 66 高 288}；
 * 绑定条目按 R43d <b>拆两行</b>渲染 ⇒ 每条 36px ⇒ 一屏只见约 8 条，而
 * {@code MAX_BOUND_CELLS=64} ⇒ §16.2 判定为容量冲突，裁定用 {@link ScrollWidget} 滚
 * <b>纯文本列表</b>（不涉槽位同步，故 R36 的"滚真实槽无先例"高风险不适用），
 * <b>不通过下调上限来绕</b>（R45c：64 是护栏）。
 * <p>
 * <b>但列表的"行数"必须是常量</b>：R32/R41b 要求双端同树且不得随数据重建 widget。绑定数在会话内
 * 会因 S6 的绑定动作而增长，若子节点数量随数据变化就是原风险回归 ⇒ 本盘<b>固定铺满 64 行</b>，
 * 未占用的行渲染空串。滚动只改视口，不改树。
 * <p>
 * 绑定格置于本列底部（§16.3：全局 {@code y≈276} 带 ⇒ 局部 270），绑定/解绑键紧随其上，
 * 与需求 5 的「右下角」一致。解绑入口 = <b>列表行选中 + 解绑按钮</b>（R40d），
 * 与 ghost 格的"右键解绑"（需求 4）是两套互不相干的实现，<b>不得合并</b>。
 */
public final class NekoPocketChannelColumn {

    /** §16.1：第四列 x 起点。 */
    public static final int X = 328;
    /** §16.2：与中栏同一 y 起点。 */
    public static final int Y = 6;
    /** §16.1：第四列总宽。 */
    public static final int WIDTH = 66;
    /** §16.2：288。 */
    public static final int HEIGHT = 288;

    /** R43d：每条绑定两行文本 ⇒ 单条高度 36px。 */
    public static final int ROW_HEIGHT = 36;
    /** 绑定行总数 = 上限护栏（R45d 已认可 64），<b>常量</b>而非数据驱动。 */
    public static final int ROWS = PocketConstants.MAX_BOUND_CELLS;

    /** 列表视口顶部（标题 12px + 2px 间距）。 */
    private static final int LIST_Y = 14;
    /** 列表视口高度：让到底部键位区上方为止。 */
    private static final int LIST_HEIGHT = 214;
    /** 绑定/解绑键区（各 16px 高，紧贴绑定格上方）。 */
    private static final int BUTTONS_Y = LIST_Y + LIST_HEIGHT + 2;
    /** §16.3：绑定格带（局部 y=270 ⇒ 全局 276）。 */
    private static final int BIND_SLOT_Y = 270;

    /** 绑定格的布局字面量（1 格；与中栏/左栏/蒸馏同一矩阵机制，同步键独立避免 id 覆盖）。 */
    private static final String[] BIND_MATRIX = { "B" };

    private NekoPocketChannelColumn() {}

    /** 装配第四列。 */
    public static ParentWidget<?> build(NekoPocketPanel ui) {
        final SlotGroupWidget bindGroup = SlotGroupWidget.builder()
            .matrix(BIND_MATRIX)
            .key(
                'B',
                index -> new com.cleanroommc.modularui.widgets.slot.ItemSlot().slot(
                    ui.slots()
                        .bind(ui.inventory()))
                    .name("bind_" + index))
            .synced(PocketSlots.SYNC_BIND)
            .slotGroup(PocketSlots.GROUP_BIND)
            .build();
        bindGroup.pos((WIDTH - 18) / 2, BIND_SLOT_Y);

        final ParentWidget<?> root = new ParentWidget<>().pos(X, Y)
            .size(WIDTH, HEIGHT)
            .name("pocket_channel_column")
            .child(
                (IWidget) new TextWidget(IKey.lang("gtit.pocket.bind.title")).textAlign(Alignment.TopCenter)
                    .scale(0.5f)
                    .pos(0, 0)
                    .size(WIDTH, 12))
            .child(bindList(ui))
            .child(
                new ButtonWidget<>().pos(0, BUTTONS_Y)
                    .size(WIDTH, 16)
                    .name("pocket_bind_button")
                    .child(new TextWidget(IKey.lang("gtit.pocket.bind.button")).textAlign(Alignment.Center))
                    .tooltip(tooltip -> tooltip.addLine(IKey.lang("gtit.pocket.bind.slot_hint")))
                    .onMousePressed(button -> ui.requestBind()))
            .child(
                new ButtonWidget<>().pos(0, BUTTONS_Y + 18)
                    .size(WIDTH, 16)
                    .name("pocket_unbind_button")
                    .child(
                        new TextWidget(IKey.lang("gtit.pocket.bind.unbind_button")).scale(0.5f)
                            .textAlign(Alignment.Center))
                    .tooltip(tooltip -> tooltip.addLine(IKey.dynamic(ui::selectedRowHint)))
                    .onMousePressed(button -> ui.requestUnbind()))
            .child(bindGroup);
        return root.excludeAreaInRecipeViewer();
    }

    /**
     * 固定 64 行的绑定列表（纯文本，滚在 {@link ScrollWidget} 里）。
     * <p>
     * 每行两条文本（R43d 的拆两行）：第一行 = {@code bind.entry}（元件 ID 短码），
     * 第二行 = {@code bind.located}（5 个 {@code %d}，顺序 = 维/xyz/槽）或
     * {@code bind.unlocated}（<b>正常态</b>，绑定时元件在手里故必然尚未识别，R40b）或
     * {@code bind.stale}（位置失效，需重绑）。
     */
    private static IWidget bindList(NekoPocketPanel ui) {
        final VerticalScrollData scrollData = new VerticalScrollData(false, 4);
        final ParentWidget<?> rows = new ParentWidget<>().pos(0, 0)
            .size(WIDTH, ROWS * ROW_HEIGHT)
            .name("pocket_bind_rows");
        for (int row = 0; row < ROWS; row++) {
            final int index = row;
            rows.child(
                new ButtonWidget<>().pos(0, index * ROW_HEIGHT)
                    .size(WIDTH, ROW_HEIGHT)
                    .playClickSound(false)
                    .name("pocket_bind_row_" + index)
                    .child(
                        (IWidget) new TextWidget(IKey.dynamic(() -> ui.bindRowTitle(index))).scale(0.5f)
                            .textAlign(Alignment.TopLeft)
                            .pos(1, 1)
                            .size(WIDTH - 2, 10))
                    .child(
                        (IWidget) new TextWidget(IKey.dynamic(() -> ui.bindRowLocation(index))).scale(0.5f)
                            .textAlign(Alignment.TopLeft)
                            .pos(1, 12)
                            .size(WIDTH - 2, 22))
                    .tooltip(tooltip -> tooltip.addLine(IKey.dynamic(() -> ui.bindRowHint(index))))
                    .onMousePressed(button -> ui.selectBindRow(index)));
        }
        return new ScrollWidget<>(scrollData).pos(0, LIST_Y)
            .size(WIDTH, LIST_HEIGHT)
            .name("pocket_bind_list")
            .child(rows);
    }

    /**
     * 服务端行的解析结果（客户端只负责格式化，<b>不按 ghost 表或内存表推断</b>，R39b/R19）。
     * 行的机读形状见 {@link NekoPocketPanel#composeBindRows()}。
     */
    static final class Row {

        /** 未定位（正常态，R40b）。 */
        static final char STATUS_UNLOCATED = 'N';
        /** 已定位。 */
        static final char STATUS_LOCATED = 'L';
        /** 位置已失效（需重新绑定）。 */
        static final char STATUS_STALE = 'S';

        final String id;
        final char status;
        final int dim;
        final int x;
        final int y;
        final int z;
        final int slot;

        Row(String id, char status, int dim, int x, int y, int z, int slot) {
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

        /** 行的短码显示（ID 太长会溢出 66px ⇒ 取前 8 位）。 */
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
         * 分隔符是 {@code '|'} 与 {@code ';'}，都来自 {@link #compose(Row)} 的自有形状
         * （<b>不是</b> NBT 键名，故不落 {@code PocketConstants}），解析失败一律回落空行。
         */
        static List<Row> parse(String blob) {
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
        static String compose(List<Row> rows) {
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

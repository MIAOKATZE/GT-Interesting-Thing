package com.miaokatze.gtit.gui.pocket;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.item.ItemStack;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.SlotGroupWidget;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.miaokatze.gtit.common.items.pocket.PocketConstants;
import com.miaokatze.gtit.currency.NekoCurrencyRegistrar;
import com.miaokatze.gtit.trade.NekoClientBalances;

/**
 * 底部带 = <b>左：币值区 + 两个通道按钮（180×72）</b> ｜ <b>右：绑定块（220×72）</b>
 * （R74②/R75：原第四列 66×288 整列退役后，它承载的两件事各找新落点）。
 * <p>
 * <b>几何（R75 钉死）</b>：带 {@code y = 6+270+6 = 282}、高 72（再往下只剩 6px 外边距 ⇒
 * 总高 {@code 282+72+6 = 360} = 1080p / GUI Scale 3 的逻辑高度上限，一格都不能再加）；
 * 横向 {@code 6 + 180 + 4 + 220 = 410}，右外边距 6 ⇒ 416 闭合（{@code 180+4+220 = 404 = 416−12}）。
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
 * <b>绑定信息的可见面 = 按钮 tooltip</b>（R74 裁定）：每条含维度 / x,y,z / 状态位，
 * 上限 {@link #TOOLTIP_ROWS} 条，<b>超出即显式截断提示</b>（{@code bind.truncated}）——
 * {@link PocketConstants#MAX_BOUND_CELLS} 是 64，而 tooltip 不能滚，
 * 所以"不删信息"在这里的唯一合法形态就是"截断必须说出来"，不得静默只显示前 N 条。
 * 列表行的机读形状（{@code id|status|dim|x|y|z|slot}，{@code ';'} 分隔）与解析器
 * {@link Row} 一起从第四列搬进来 ⇒ 双端仍只有一份编解码。
 * <p>
 * <b>本类不含任何槽工厂</b>：绑定格（1 格，{@code GROUP_BIND}）仍由 {@link PocketSlots} 单点构造，
 * 这里只放它的 {@code SlotGroupWidget}。
 */
public final class NekoPocketBottomBand {

    /** 带起点 y（= 面板外边距 + 主区高 + 一个外边距）。 */
    public static final int Y = NekoPocketPanel.MARGIN + NekoPocketStorageColumn.HEIGHT + NekoPocketPanel.MARGIN;
    /** 带高（R75 钉死 72）。 */
    public static final int HEIGHT = 72;
    /** 行高（{@code scale(0.5)} 的一行文本）。 */
    private static final int LINE = 11;

    /** 币值块 x（= 面板外边距）。 */
    public static final int COIN_X = NekoPocketPanel.MARGIN;
    /** 币值块宽（与中栏同宽，R75：180）。 */
    public static final int COIN_WIDTH = NekoPocketStorageColumn.WIDTH;
    /** 单条币值条宽（{@code POCKET_C2_coinbar} 的原生宽度，取自契约表；几何不读贴图类）。 */
    private static final int COIN_BAR_WIDTH = PocketGuiTextureContract.widthOf("POCKET_C2_coinbar");
    /** 币值条/按钮的原生高度（{@code POCKET_C2_coinbar} 与 {@code POCKET_C2_btn} 同为 18）。 */
    private static final int COIN_BAR_HEIGHT = PocketGuiTextureContract.heightOf("POCKET_C2_coinbar");
    /** 通道按钮宽（{@code POCKET_C2_btn} 的原生宽度，取自契约表）。 */
    private static final int BUTTON_WIDTH = PocketGuiTextureContract.widthOf("POCKET_C2_btn");

    /** 绑定块 x（= 币值块右边 + 列间距）。 */
    public static final int BIND_X = COIN_X + COIN_WIDTH + NekoPocketPanel.COLUMN_GAP;
    /** 绑定块宽 = 面板宽 − 两块外边距 − 币值块 − 列间距。 */
    public static final int BIND_WIDTH = NekoPocketPanel.WIDTH - 2 * NekoPocketPanel.MARGIN
        - COIN_WIDTH
        - NekoPocketPanel.COLUMN_GAP;
    /** 绑定按钮的可视宽（= {@code POCKET_C2_bindbtn} 原生宽 106；★小于块宽，剩下给绑定格与文本）。 */
    private static final int BIND_BUTTON_WIDTH = PocketGuiTextureContract.widthOf("POCKET_C2_bindbtn");
    /** 绑定格 x（块内局部）。 */
    private static final int BIND_SLOT_X = BIND_BUTTON_WIDTH + 4;

    /** 绑定条目的 tooltip 最多列几条（超出必须显式提示，见类 javadoc）。 */
    public static final int TOOLTIP_ROWS = 10;

    /** 绑定格的布局字面量（1 格；与中栏/流体/蒸馏同一矩阵机制，同步键独立避免 id 覆盖）。 */
    private static final String[] BIND_MATRIX = { "B" };

    private NekoPocketBottomBand() {}

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
        for (String row : BIND_MATRIX) {
            for (int index = 0; index < row.length(); index++) {
                if (row.charAt(index) == 'B') {
                    total++;
                }
            }
        }
        return total;
    }

    static {
        if (layoutSlotCount() != PocketInventory.BIND_SLOTS) {
            throw new IllegalStateException("[pocket] 绑定格矩阵产出 " + layoutSlotCount() + " 格，与 handler 格数不符");
        }
    }

    /** 装配底部带（两块各一次 {@code excludeAreaInRecipeViewer()}，R36/§16 每块一处）。 */
    public static List<ParentWidget<?>> build(NekoPocketPanel ui) {
        final List<ParentWidget<?>> blocks = new ArrayList<>(2);
        blocks.add(coinBlock(ui));
        blocks.add(bindBlock(ui));
        return blocks;
    }

    // ------------------------------------------------------------------ 左块：币值 + 两个通道按钮

    /**
     * 币值块（180×72）：两行余额（{@link NekoClientBalances}，R64c：<b>不得</b>在客户端自行读钱包）
     * + 两个通道按钮（R64b/R64c：动作码各自独立，客户端不算钱、不判模式）。
     */
    private static ParentWidget<?> coinBlock(NekoPocketPanel ui) {
        return new ParentWidget<>().pos(COIN_X, Y)
            .size(COIN_WIDTH, HEIGHT)
            .name("pocket_coin_block")
            .child(balanceRows(ui))
            .child(channelButtons(ui))
            .child(coinNote(ui));
    }

    /** 余额两行（各占一条 {@code POCKET_C2_coinbar} 88×18，铜牌底）。 */
    private static IWidget balanceRows(NekoPocketPanel ui) {
        final IKey neko = IKey.lang(
            "gtit.pocket.balance.neko",
            () -> new Object[] { NekoClientBalances.getBalance(NekoCurrencyRegistrar.NEKO_ID) });
        final IKey shimmering = IKey.lang(
            "gtit.pocket.balance.shimmering",
            () -> new Object[] { NekoClientBalances.getBalance(NekoCurrencyRegistrar.SHIMMERING_NEKO_ID) });
        return new ParentWidget<>().pos(0, 0)
            .size(COIN_WIDTH, COIN_BAR_HEIGHT)
            .name("pocket_balance_rows")
            .child(coinBar(neko, 0, "pocket_balance_neko"))
            .child(coinBar(shimmering, COIN_BAR_WIDTH + NekoPocketPanel.COLUMN_GAP, "pocket_balance_shimmering"));
    }

    private static IWidget coinBar(IKey key, int x, String name) {
        // 包一层 ParentWidget 再挂 name/background/tooltip：TextWidget 是泛型自回类型，
        // 裸类型上挂 tooltip(...) 会把 lambda 形参擦成 Object（编译期"找不到符号 addLine(IKey)"），
        // 而 ParentWidget<> 的链式返回在本仓一直是可用的。
        return new ParentWidget<>().pos(x, 0)
            .size(COIN_BAR_WIDTH, COIN_BAR_HEIGHT)
            .name(name)
            .child(
                (IWidget) new TextWidget(key).textAlign(Alignment.Center)
                    .scale(0.5f)
                    .pos(0, 0)
                    .size(COIN_BAR_WIDTH, COIN_BAR_HEIGHT))
            .background(PocketGuiTextures.COIN_BAR)
            .tooltip(tooltip -> tooltip.addLine(key));
    }

    /**
     * 需求 3 的两个通道按钮：各 {@code POCKET_C2_btn} 88×18，起点 {@code y = 18 + 4}。
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
        return new ParentWidget<>().pos(0, 22)
            .size(COIN_WIDTH, 18)
            .name("pocket_channel_buttons")
            .child(
                new ButtonWidget<>().pos(0, 0)
                    .size(BUTTON_WIDTH, 18)
                    .name("pocket_button_instant")
                    .background(PocketGuiTextures.BUTTON)
                    .hoverBackground(PocketGuiTextures.BUTTON_PRESSED)
                    .child(
                        (IWidget) new TextWidget(instant).scale(0.5f)
                            .textAlign(Alignment.Center))
                    .tooltip(tooltip -> tooltip.addLine(instant))
                    .onMousePressed(button -> button == 0 && ui.requestChannel(NekoPocketPanel.ChannelRequest.BURST)))
            .child(
                new ButtonWidget<>().pos(BUTTON_WIDTH + NekoPocketPanel.COLUMN_GAP, 0)
                    .size(BUTTON_WIDTH, 18)
                    .name("pocket_button_timed")
                    .background(PocketGuiTextures.BUTTON)
                    .hoverBackground(PocketGuiTextures.BUTTON_PRESSED)
                    .child(
                        (IWidget) new TextWidget(timed).scale(0.5f)
                            .textAlign(Alignment.Center))
                    .tooltip(tooltip -> tooltip.addLine(timed))
                    .onMousePressed(button -> button == 0 && ui.requestChannel(NekoPocketPanel.ChannelRequest.SHORT)));
    }

    /** 币值块剩下的那条空段：一行常驻说明 + 完整说明的 tooltip（与左列同一条文本，不另立文案）。 */
    private static IWidget coinNote(NekoPocketPanel ui) {
        return new ParentWidget<>().pos(0, 44)
            .size(COIN_WIDTH, 3 * LINE)
            .name("pocket_coin_note")
            .child(
                (IWidget) new TextWidget(IKey.lang("gtit.pocket.note.channel")).textAlign(Alignment.TopLeft)
                    .scale(0.5f)
                    .pos(0, 0)
                    .size(COIN_WIDTH, 3 * LINE))
            .tooltip(tooltip -> tooltip.addLine(IKey.dynamic(ui::notesText)));
    }

    // ------------------------------------------------------------------ 右块：绑定 / 解绑

    /** 绑定块（220×72）：绑定按钮（左键绑定 / 右键解绑末条 / Shift 右键清空）+ 绑定格 + 三行说明。 */
    private static ParentWidget<?> bindBlock(NekoPocketPanel ui) {
        final SlotGroupWidget bindGroup = SlotGroupWidget.builder()
            .matrix(BIND_MATRIX)
            .key('B', index -> {
                final com.cleanroommc.modularui.widgets.slot.ItemSlot slot = new com.cleanroommc.modularui.widgets.slot.ItemSlot();
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
        bindGroup.pos(BIND_SLOT_X, 0);

        final IKey summary = IKey.dynamic(ui::bindSummaryText);
        final ParentWidget<?> root = new ParentWidget<>().pos(BIND_X, Y)
            .size(BIND_WIDTH, HEIGHT)
            .name("pocket_bind_block")
            .child(
                new ButtonWidget<>().pos(0, 0)
                    .size(BIND_BUTTON_WIDTH, 18)
                    .name("pocket_bind_button")
                    .background(PocketGuiTextures.BIND_BUTTON)
                    .child(
                        (IWidget) new TextWidget(IKey.lang("gtit.pocket.bind.button")).scale(0.5f)
                            .textAlign(Alignment.Center))
                    // ★绑定信息的唯一可见面（R74）：全部绑定 + 超出的显式截断提示
                    .tooltip(tooltip -> tooltip.addLine(IKey.dynamic(ui::bindTooltipText)))
                    // 左键绑定；右键解绑最后一条；Shift+右键清空全部（R74 裁定，语义与 onServerAction 同步改）
                    .onMousePressed(button -> ui.dispatchBindButtonClick(button)))
            .child(bindGroup)
            .child(
                (IWidget) new TextWidget(summary).textAlign(Alignment.TopLeft)
                    .scale(0.5f)
                    .pos(0, 22)
                    .size(BIND_WIDTH, LINE))
            .child(
                (IWidget) new TextWidget(IKey.lang("gtit.pocket.bind.unbind_hint")).textAlign(Alignment.TopLeft)
                    .scale(0.5f)
                    .pos(0, 33)
                    .size(BIND_WIDTH, 2 * LINE))
            .child(
                (IWidget) new TextWidget(IKey.lang("gtit.pocket.bind.stale_hint")).textAlign(Alignment.TopLeft)
                    .scale(0.5f)
                    .pos(0, 55)
                    .size(BIND_WIDTH, LINE));
        return root.excludeAreaInRecipeViewer();
    }

    // ------------------------------------------------------------------ 绑定行的机读形状（自第四列搬入）

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

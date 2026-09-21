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
import com.miaokatze.gtit.currency.NekoCurrencyRegistrar;
import com.miaokatze.gtit.trade.NekoClientBalances;

/**
 * 左栏 = 竖贯流体条 + 8 个同权流体交互格 + <b>需求 3 的左配置栏</b>（余额 2 行 + 2 个通道按钮 +
 * 模式/剩余回显 + 一键整理 + R60 边界脚注）。
 * <p>
 * <b>几何（§16.1 + R64b，逐字照核算表）</b>：列 root {@code x=6 宽 94 高 288}；内部 =
 * 流体条 18px（局部 {@code x=0}，竖贯整列）+ 4 列格段 {@code x=22 宽 72}（{@code 18+4+72=94} 闭合）。
 * R64b 定死配置段起点：全局 {@code x=6+18+4=28}、{@code y} 从 {@code 6+36+8=50} 起向下排布
 * ⇒ 本列 root 内局部 {@code x=22, y=44}。那 72px 段在 8 格下方还剩 {@code 288-36=252px}，
 * 配置段只需 ≈66px ⇒ <b>不开第五列</b>（会把 400 撑破并连锁改 149/槽号白名单）。
 * <p>
 * <b>8 格两排同权（R39a，推翻 R34）</b>：上 4 + 下 4 行为<b>完全相同</b>，方向由<b>放入的容器当前有无
 * 流体</b>决定（有流体 → 抽进流体条；为空 → 从流体条灌满），<b>不是</b>「上排注入/下排接取」。
 * 搬运本体在 {@link PocketSlots#fluidInteraction} 的服务端 changeListener 里，本文件只装配 Widget；
 * 「同权 = 并发交互位数量 8」这一层由 {@code tooltip.6} 对玩家显式声明。
 * <p>
 * <b>流体条（R30/R46c/L7）</b>：一律用 MUI2 原生 {@link FluidSlot} 的子类 {@link NekoPocketFluidSlot}
 * + {@link FluidSlotSyncHandler}（<b>不开 phantom</b>，理由见 {@link #fluidBar}），手持储罐点条的
 * 按键组合与 Tooltip 因此与 GT5U 逐字一致（{@code modularui2.fluid.click_combined} /
 * {@code _to_fill} / {@code _to_empty} / {@code modularui2.tooltip.shift}）；
 * <b>不自写按键分支、不自造文案键</b>。{@code alwaysShowFull(false)} + 只报真实容量的
 * {@link FluidStackTank}（未开 overflow ⇒ {@code getCapacity()} 即真容量）⇒ 部分填充可见。
 */
public final class NekoPocketLeftColumn {

    /** §16.1：左栏 x 起点。 */
    public static final int X = 6;
    /** §16.2：与中栏同一 y 起点。 */
    public static final int Y = 6;
    /** §16.1：左栏总宽（18 流体条 + 4 间距 + 72 四列格）。 */
    public static final int WIDTH = 94;
    /** §16.2：288。 */
    public static final int HEIGHT = 288;

    /** 流体条宽度（1 格）。 */
    public static final int BAR_WIDTH = 18;
    /** 流体条高度（竖贯整列）。 */
    public static final int BAR_HEIGHT = 288;
    /** 格段与配置段的公共局部 x：{@code 18 + 4}。 */
    public static final int SECTION_X = 22;
    /** 格段与配置段的公共宽度（4 列 × 18）。 */
    public static final int SECTION_WIDTH = 72;
    /** R64b：配置段起点（局部 y）= 全局 50 − 列起点 6。 */
    public static final int CONFIG_Y = 44;

    /**
     * 8 格的<b>布局字面量</b>（与中栏同一机制，R41a）：前两行各 4 格（上 4 + 下 4），其余 14 行留空
     * ⇒ 组高仍是 288，与流体条严格等高。
     * <p>
     * 空格只推进坐标、不产出 widget（{@code SlotGroupWidget.java:243-245}），所以 {@code 'L'} 的
     * 出现序号恰好等于 handler 索引 {@code 0..7} ⇒ 不需要任何手工偏移或第二条常量。
     */
    private static final String[] INTERACTION_MATRIX = { "LLLL", "LLLL", "    ", "    ", "    ", "    ", "    ", "    ",
        "    ", "    ", "    ", "    ", "    ", "    ", "    ", "    " };

    private NekoPocketLeftColumn() {}

    /** 装配左栏（列 root 各一次 {@code excludeAreaInRecipeViewer()}，R36/§16）。 */
    public static ParentWidget<?> build(NekoPocketPanel ui) {
        final SlotGroupWidget interaction = SlotGroupWidget.builder()
            .matrix(INTERACTION_MATRIX)
            .key(
                'L',
                index -> new com.cleanroommc.modularui.widgets.slot.ItemSlot().slot(
                    ui.slots()
                        .fluidInteraction(ui.inventory(), index))
                    .name("fluid_" + index))
            .synced(PocketSlots.SYNC_FLUID)
            .slotGroup(PocketSlots.GROUP_FLUID)
            .build();
        interaction.pos(SECTION_X, 0);

        final ParentWidget<?> root = new ParentWidget<>().pos(X, Y)
            .size(WIDTH, HEIGHT)
            .name("pocket_left_column")
            .child(fluidBar(ui))
            .child(interaction)
            .child(balanceRows(ui))
            .child(channelButtons(ui))
            .child(sortButton(ui))
            .child(statusLines(ui));
        return root.excludeAreaInRecipeViewer();
    }

    /**
     * 竖置流体条（18×288）。
     * <p>
     * <b>需求 4 的流体入口已实装</b>（S-E，R70）：格件是 {@link NekoPocketFluidSlot} ——
     * 一个覆写了 {@code handleDragAndDrop} 的 {@code FluidSlot} 子类，NEI 左键把满容器（水桶 /
     * 岩浆桶 / 其它注册过的罐瓶，或流体方块本身）拖到条上 ⇒ 就地声明
     * {@code (Kind.FLUID, 0)} 那条 ghost；右键发 {@code CLR|0|F} 解绑。判定与落档都在服务端
     * （{@code NekoPocketPanel#onServerGhostRequest} → {@code PocketGhostRequest#apply}）。
     * <p>
     * ★<b>两条不许动</b>（R70 实测口径）：① <b>不</b>调 {@code super.handleDragAndDrop}——库内那一条
     * 在 {@code isPhantom()} 门之前就返回 false，真实条上恒拒；② <b>不</b>把下面这个 handler 设成
     * {@code phantom(true)}——那会让服务端那一支把流体直接写进真实 tank，而需求 2 的
     * 「从手上/仓里灌排流体条、两排同权」依赖它是<b>真实条</b>。
     * 于是 18×288 几何与 {@code alwaysShowFull(false)} 的部分填充原样保留，搬运语义一个字都没改。
     */
    private static IWidget fluidBar(NekoPocketPanel ui) {
        final FluidStackTank tank = ui.inventory()
            .barTank();
        final NekoPocketFluidSlot bar = new NekoPocketFluidSlot().bindBar(ui);
        ui.trackFluidBar(bar);
        return bar.pos(0, 0)
            .size(BAR_WIDTH, BAR_HEIGHT)
            .alwaysShowFull(false)
            .name("pocket_fluid_bar")
            .syncHandler(new FluidSlotSyncHandler(tank));
    }

    /** 余额两行（R64c：读 {@link NekoClientBalances}，<b>不得</b>在客户端自行读钱包）。 */
    private static IWidget balanceRows(NekoPocketPanel ui) {
        return new ParentWidget<>().pos(SECTION_X, CONFIG_Y)
            .size(SECTION_WIDTH, 22)
            .name("pocket_balance_rows")
            .child(
                (IWidget) new TextWidget(
                    IKey.lang(
                        "gtit.pocket.balance.neko",
                        () -> new Object[] { NekoClientBalances.getBalance(NekoCurrencyRegistrar.NEKO_ID) }))
                            .textAlign(Alignment.TopLeft)
                            .scale(0.5f)
                            .pos(0, 0)
                            .size(SECTION_WIDTH, 11))
            .child(
                (IWidget) new TextWidget(
                    IKey.lang(
                        "gtit.pocket.balance.shimmering",
                        () -> new Object[] { NekoClientBalances.getBalance(NekoCurrencyRegistrar.SHIMMERING_NEKO_ID) }))
                            .textAlign(Alignment.TopLeft)
                            .scale(0.5f)
                            .pos(0, 11)
                            .size(SECTION_WIDTH, 11));
    }

    /**
     * 需求 3 的两个通道按钮（R64b/R64c）：局部 {@code x=22 宽 72}、各 17px 高，起点
     * {@code y = CONFIG_Y + 28}（= 全局 78，在余额两行下方）。
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
        return new ParentWidget<>().pos(SECTION_X, CONFIG_Y + 28)
            .size(SECTION_WIDTH, 38)
            .name("pocket_channel_buttons")
            .child(
                new ButtonWidget<>().pos(0, 0)
                    .size(SECTION_WIDTH, 17)
                    .name("pocket_button_instant")
                    .child(
                        (IWidget) new TextWidget(instant).scale(0.5f)
                            .textAlign(Alignment.Center))
                    .tooltip(tooltip -> tooltip.addLine(instant))
                    .onMousePressed(button -> ui.requestChannel(NekoPocketPanel.ChannelRequest.BURST)))
            .child(
                new ButtonWidget<>().pos(0, 19)
                    .size(SECTION_WIDTH, 17)
                    .name("pocket_button_timed")
                    .child(
                        (IWidget) new TextWidget(timed).scale(0.5f)
                            .textAlign(Alignment.Center))
                    .tooltip(tooltip -> tooltip.addLine(timed))
                    .onMousePressed(button -> ui.requestChannel(NekoPocketPanel.ChannelRequest.SHORT)));
    }

    /**
     * 一键整理中栏 128 格（计划 §6 第 4 条的<b>语义①</b>）。
     * <p>
     * <b>为什么自造按钮</b>（R41c 的后备分支）：R41c 让优先复用
     * {@code SlotGroupWidget.placeSortButtonsTopRightVertical()}，但实测本版本（2.3.88）里
     * 那一族方法连同 {@code SortButtons} 类<b>都不存在</b>——{@code SlotGroupWidget.java:63-144}
     * 整段被注释掉，{@code widgets/} 目录下也没有 {@code SortButtons.java} ⇒ 复用不可能。
     * 不自绘同步面：按钮只发一次请求，排序本体在 {@link NekoPocketPanel#performSort()}（服务端）。
     */
    private static IWidget sortButton(NekoPocketPanel ui) {
        return new ButtonWidget<>().pos(SECTION_X, CONFIG_Y + 68)
            .size(14, 14)
            .name("pocket_sort_button")
            .child(
                NekoGuiTextures.SORT_SMART.asWidget()
                    .pos(1, 1)
                    .size(12, 12))
            .playClickSound(false)
            .onMousePressed(button -> ui.requestSort());
    }

    /**
     * 状态回显三行：模式（R39b 服务端算）、冷却/剩余秒数 + 最近一次动作的回执（R24 倒计时 +
     * R16 墙钟冷却 + R10 的"失败必须分开说"）、R60/R62b 要求可观测的"仅主背包内运作"边界脚注。
     * <p>
     * 三行文本都由 {@link NekoPocketPanel} 的同步缓存格式化 ⇒ 客户端不读服务端内存表、不按 ghost 表推断。
     * 72px 装不下完整回执（R36 判定过的宽度现实）⇒ 同一份完整文本进本块的 tooltip，<b>不删信息</b>。
     */
    private static IWidget statusLines(NekoPocketPanel ui) {
        return new ParentWidget<>().pos(SECTION_X, CONFIG_Y + 88)
            .size(SECTION_WIDTH, 60)
            .name("pocket_status_lines")
            .child(
                (IWidget) new TextWidget(IKey.dynamic(ui::modeText)).textAlign(Alignment.TopLeft)
                    .scale(0.5f)
                    .pos(0, 0)
                    .size(SECTION_WIDTH, 11))
            .child(
                (IWidget) new TextWidget(IKey.dynamic(ui::channelStatusText)).textAlign(Alignment.TopLeft)
                    .scale(0.5f)
                    .pos(0, 11)
                    .size(SECTION_WIDTH, 11))
            .child(
                (IWidget) new TextWidget(IKey.lang("gtit.pocket.held.note")).textAlign(Alignment.TopLeft)
                    .scale(0.5f)
                    .pos(0, 22)
                    .size(SECTION_WIDTH, 11))
            .tooltip(tooltip -> tooltip.addLine(IKey.dynamic(ui::statusHintText)));
    }
}

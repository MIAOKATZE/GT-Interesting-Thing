package com.miaokatze.gtit.gui.pocket;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.item.ItemStack;

import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.drawable.UITexture;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.ItemDisplayWidget;
import com.cleanroommc.modularui.widgets.SlotGroupWidget;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.cleanroommc.modularui.widgets.slot.ItemSlot;
import com.miaokatze.gtit.client.gui.NekoGuiTextures;
import com.miaokatze.gtit.common.items.pocket.PocketConstants;
import com.miaokatze.gtit.currency.NekoCurrencyRegistrar;
import com.miaokatze.gtit.trade.NekoClientBalances;

/**
 * 底部带 = <b>横向三段</b>（R78①，用户给的摆法）：
 * <b>左段 100</b>（两条币值条 + 两个通道按钮，纵向 4×18 = 72）
 * ｜ <b>中间【玩家背包 9 列 × 4 行 = 162×72】</b>
 * ｜ <b>右段 120</b>（绑定条 + 绑定格 + 状态行 + 帮助按钮）。
 * <p>
 * <b>几何（R78 钉死）</b>：带 {@code y = 6+270+6 = 282}、高 72；背包 4 行 = 72 <b>正好等于带高</b>
 * ⇒ 中栏 15 行<b>一行不删</b>、面板总高仍 {@code 282+72+6 = 360} = 1080p / GUI Scale 3 的
 * 逻辑高度上限（R75 的硬天花板，一格都不能再加）。
 * 横向加总 {@code 6 + 100 + 4 + 162 + 4 + 120 + 6 = 402 ≤ 416}（★余 14）⇒ 那 14px 全部落在
 * "背包与右段之间"（{@link #BIND_SLACK}），于是右段右边正好贴到 {@code 416-6}，
 * 带的左右外边距仍都是 6。三段宽度与两个 4 的间距是 R78 加总表里的数，<b>不得</b>自己挪。
 * <p>
 * <b>★R78①：玩家背包是"加回来"的，代价是 E4 风险回归（不得静默）</b>。旧实现（R69-D2）预注册
 * 一个<b>空</b> {@code PlayerSlotGroup} 让 MUI2 的默认分支跳过 36 格绑定（见
 * {@link PocketSlots} 类 javadoc），R78 按用户裁决<b>撤销</b>那一招：
 * <ul>
 * <li>背包 36 格<b>真实显示并可交互</b>（那 36 格由框架造，不经本仓槽工厂，见
 * {@link PocketSlots#PLAYER_BACKPACK_SLOTS}）；</li>
 * <li>连带代价 = <b>E4</b>：首开要同步 36 格，且 vanilla {@code Container#detectAndSendChanges}
 * 每 tick 对这 36 格做 {@code ItemStack} 相等比较（<b>含整份 NBT 深比较</b>），
 * 玩家背包内容一变就把整枚口袋连同 199 格一起重发（R53c 点名的包放大面）；</li>
 * <li>这条代价是用户为"要玩家背包"<b>明确换回来</b>的，README 第 5 条与本注释同处点名。</li>
 * </ul>
 * ★本文件<b>不</b>自造那 36 个 {@code ModularSlot}：只把 widget 绑到框架注册的同步键上
 * （{@code "player"} + 槽号，与库内 {@code SlotGroupWidget.playerInventory(…)} 同一绑法），
 * 否则就会出现"两个 handler 指向同一个背包格"的两处真相。
 * <p>
 * <b>★币值区照猫猫机形态（R78④，用户第二张图）</b>：不是"猫猫币：179"那种文本行，而是
 * <b>币物品图标 + 数量 + 两枚快捷小图标</b>。现成实现在
 * {@code client/gui/NekoCoinDisplayV2.java}（售货机 V2 面板用，装配点
 * {@code gui/vm/TradePage.java:1174-1206}），本块<b>借它的控件形态</b>：
 * <ul>
 * <li>图标 = {@code ItemDisplayWidget().item(NekoCurrencyRegistrar.getItemStack(id,1))}
 * （源：{@code NekoCoinDisplayV2.java:107-116}；本处 16×16 以塞进 18 高的币值条）；</li>
 * <li>数量 = {@code IKey.dynamic(...)} + 可读串（源：{@code NekoCoinDisplayV2.java:118-125}
 * 与 {@code :336-344} 的 {@code getReadableString}，10000→10K、1000000→1M）；</li>
 * <li>两枚 12×12 的快捷小图标 = {@code ButtonWidget.size(12)} +
 * {@code disableThemeBackground(true)} + {@code overlay(纹理.asIcon().size(12))}
 * （源：{@code NekoCoinDisplayV2.java:127-158} 的弹出键与 {@code :179-203} 的 ME 导入键）。</li>
 * </ul>
 * ★<b>只借形态、不借语义</b>（用户原话的对应关系由本回执说明）：售货机那两枚是"取币 / 从 ME 存币"，
 * 口袋面板没有这两个动作。这里两枚各对应"花掉这一枚币的那条通道"：
 * <b>第一枚</b> = 用该币激活它自己的通道（猫猫币 → 瞬时、闪烁币 → 短效；与下方那条通道按钮
 * <b>同一条动作码</b>，不新增服务端语义）；<b>第二枚</b> = <b>只读</b>的成本/冷却明细（无写操作、
 * 不新造动作码）。余额读数仍走 {@link NekoClientBalances}（R64c：客户端不得自行读钱包）。
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
 * <b>★R78 D-2 的补偿落点 = {@link #helpButton}</b>：左栏撤下来的 7 段说明里，"用法摘要 /
 * 通道成本 / 主手限制 / ghost 用法"这一族需要一个玩家找得到的可见入口，因此在右段留白行放一个
 * 帮助按钮（tooltip = {@code notesText} 全量 + 绑定/解绑说明）。它不新增任何信息，只是把
 * 原本常驻的文字换成"点得到 / 悬停看得到"。
 * <p>
 * <b>本类不含任何槽工厂</b>：绑定格（1 格，{@code GROUP_BIND}）仍由 {@link PocketSlots} 单点构造；
 * 背包那 36 格由框架构造。这里只放它们各自的 {@code SlotGroupWidget}。
 */
public final class NekoPocketBottomBand {

    /** 带起点 y（= 面板外边距 + 主区高 + 一个外边距）。 */
    public static final int Y = NekoPocketPanel.MARGIN + NekoPocketStorageColumn.HEIGHT + NekoPocketPanel.MARGIN;
    /** R75/R78 带高 72（= 背包 4 行 × 18 ⇒ 纵向零富余，中栏一行都不能再加）。 */
    public static final int HEIGHT = 72;

    /** 背包可视行数（★单源取常量，与框架那 36 格同源；R78①）。 */
    public static final int BACKPACK_ROWS = PocketConstants.PLAYER_BACKPACK_ROWS;
    /** 背包可视列数（= {@code SlotGroup("player_inventory")} 的 rowSize = 9）。 */
    public static final int BACKPACK_COLUMNS = PocketConstants.PLAYER_BACKPACK_COLUMNS;

    /** 左段 x（= 面板外边距）。 */
    public static final int COIN_X = NekoPocketPanel.MARGIN;
    /** ★R78①：左段宽由 180 <b>收窄为 100</b>（腾出来的横向空间给中间段的背包）。 */
    public static final int COIN_WIDTH = 100;
    /** 单条币值条宽（{@code POCKET_C2_coinbar} 的原生宽度，取自契约表；几何不读贴图类）。 */
    private static final int COIN_BAR_WIDTH = PocketGuiTextureContract.widthOf("POCKET_C2_coinbar");
    /** 币值条/按钮的原生高度（{@code POCKET_C2_coinbar} 与 {@code POCKET_C2_btn} 同为 18）。 */
    private static final int COIN_BAR_HEIGHT = PocketGuiTextureContract.heightOf("POCKET_C2_coinbar");
    /** 通道按钮宽（{@code POCKET_C2_btn} 的原生宽度，取自契约表）。 */
    private static final int BUTTON_WIDTH = PocketGuiTextureContract.widthOf("POCKET_C2_btn");
    /** 币值条在 100 宽段内的左偏移（{@code (100-88)/2}，纯派生）。 */
    private static final int COIN_BAR_X = (COIN_WIDTH - COIN_BAR_WIDTH) / 2;
    /** ★币图标的边长（VM 用 22，本处必须塞进 18 高的币值条 ⇒ 取 16，其余比例照旧）。 */
    private static final int COIN_ICON_SIZE = 16;
    /** 快捷小图标的边长（与 {@code NekoCoinDisplayV2} 的两枚 12×12 同值）。 */
    private static final int QUICK_ICON_SIZE = 12;

    /** 中间段 x（背包左沿 = 左段右边 + 列间距）。 */
    public static final int BACKPACK_X = COIN_X + COIN_WIDTH + NekoPocketPanel.COLUMN_GAP;
    /** 背包宽（9 列 × 18 = 162）。 */
    public static final int BACKPACK_WIDTH = BACKPACK_COLUMNS * NekoPocketPanel.GRID;
    /** 背包高（4 行 × 18 = 72 = 带高，R78① 的"正好等于"）。 */
    public static final int BACKPACK_HEIGHT = BACKPACK_ROWS * NekoPocketPanel.GRID;
    /** ★R78①：右段宽由 220 <b>收窄为 120</b>（绑定条 106 + 绑定格与状态行）。 */
    public static final int BIND_WIDTH = 120;
    /**
     * R78 加总表剩下的余量（{@code 416 - 12 - (100+4+162+4+120) = 14}）。
     * <p>
     * ★本常量的存在就是那条"余 14"的账：<b>三段宽度与两个 4 间距都是钉死的数</b>，
     * 唯一可自由安放余量的地方是"背包与右段之间"；把它放这里，右段的右边就正好贴到
     * {@code 面板宽 - 外边距}（左右外边距都是 6，见 {@link #BIND_X} 的闭合断言）。
     */
    public static final int BIND_SLACK = NekoPocketPanel.WIDTH - 2 * NekoPocketPanel.MARGIN
        - COIN_WIDTH
        - NekoPocketPanel.COLUMN_GAP
        - BACKPACK_WIDTH
        - NekoPocketPanel.COLUMN_GAP
        - BIND_WIDTH;
    /** 右段 x（背包右边 + 列间距 + 余量）。 */
    public static final int BIND_X = BACKPACK_X + BACKPACK_WIDTH + NekoPocketPanel.COLUMN_GAP + BIND_SLACK;
    /** 绑定按钮的可视宽（= {@code POCKET_C2_bindbtn} 原生宽 106；★小于段宽 120）。 */
    private static final int BIND_BUTTON_WIDTH = PocketGuiTextureContract.widthOf("POCKET_C2_bindbtn");
    /** 绑定格 x（段内局部；★106 + 4 + 18 = 128 &gt; 120 ⇒ 绑定格挪到第二行，不再与按钮并排）。 */
    private static final int BIND_SLOT_X = 0;
    /** 绑定格 y（第二行）。 */
    private static final int BIND_SLOT_Y = 20;

    /** 绑定条目的 tooltip 最多列几条（超出必须显式提示，见类 javadoc）。 */
    public static final int TOOLTIP_ROWS = 10;

    /**
     * 框架给的玩家背包同步键前缀（★不是本仓自造的键）。
     * <p>
     * 依据（dev jar 字节码，两处互相印证）：{@code ISyncRegistrar#bindPlayerInventory} 用
     * {@code itemSlot("player", i, slot)} 逐格注册（i = 0…35），而库内现成的
     * {@code SlotGroupWidget#playerInventory} 助手用 {@code ItemSlot.syncHandler("player", i)}
     * 绑同一批 handler ⇒ 本仓自绘的背包格必须走<b>同一个键式样</b>，否则会出现
     * "格画出来了但读不到内容"的哑洞。
     */
    private static final String PLAYER_SLOT_SYNC_NAME = "player";

    /** 绑定格的布局字面量（1 格；与中栏/流体/蒸馏同一矩阵机制，同步键独立避免 id 覆盖）。 */
    private static final String[] BIND_MATRIX = { "B" };

    /**
     * 背包的布局字面量：{@code 4 行 × 9 列}（R78①）。★整块<b>只用一个</b>布局字符 {@code 'P'}
     * （两个字符会把索引空间劈成两条重叠的 0…n，同 {@code NekoPocketLeftColumn} 记实的那条库行为）。
     */
    private static final String[] BACKPACK_MATRIX = { "PPPPPPPPP", "PPPPPPPPP", "PPPPPPPPP", "PPPPPPPPP" };

    private NekoPocketBottomBand() {}

    static {
        if (layoutSlotCount() != PocketInventory.BIND_SLOTS) {
            throw new IllegalStateException("[pocket] 绑定格矩阵产出 " + layoutSlotCount() + " 格，与 handler 格数不符");
        }
        if (backpackLayoutSlotCount() != PocketSlots.PLAYER_BACKPACK_SLOTS) {
            throw new IllegalStateException(
                "[pocket] 背包矩阵产出 " + backpackLayoutSlotCount()
                    + " 格，与框架绑定的 "
                    + PocketSlots.PLAYER_BACKPACK_SLOTS
                    + " 格不符（★不等就会有一部分背包格只存在于 Container 而看不见）");
        }
        if (BACKPACK_HEIGHT != HEIGHT) {
            throw new IllegalStateException("[pocket] 背包高度不等于带高（R78① 的 4×18 = 72 被破坏）");
        }
        if (BIND_X + BIND_WIDTH != NekoPocketPanel.WIDTH - NekoPocketPanel.MARGIN) {
            throw new IllegalStateException(
                "[pocket] 底部带横向不闭合: " + (BIND_X + BIND_WIDTH) + " != " + (NekoPocketPanel.WIDTH - 6));
        }
    }

    /**
     * 绑定格布局字符的出现总数（机检 + 装配期断言的输入；理由同各列类的同名方法）。
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

    /** 背包矩阵产出的格数（机检用；★必须等于框架注册的 36）。 */
    public static int backpackLayoutSlotCount() {
        int total = 0;
        for (String row : BACKPACK_MATRIX) {
            for (int index = 0; index < row.length(); index++) {
                if (row.charAt(index) == 'P') {
                    total++;
                }
            }
        }
        return total;
    }

    /**
     * 布局序号 → 框架那 36 格里的<b>槽号</b>（★唯一的背包格序映射，双端同一段代码）。
     * <p>
     * 视觉行 0…2 放主背包第 9…35 格、视觉行 3（最下面一行）放<b>快捷栏</b> 0…8 格 ——
     * 与原版箱/熔炉界面里"快捷栏在最下行"的读法一致（库内 {@code SlotGroupWidget#playerInventory}
     * 也是把 0…8 画在最底那一行）。行主序 {@code 0…26} 先铺完上面三行，因此
     * {@code layout < 27 → layout + 9}，否则 {@code layout - 27}。
     */
    public static int backpackPlayerSlotAt(int layoutIndex) {
        final int mainRows = BACKPACK_ROWS - 1;
        return layoutIndex < BACKPACK_COLUMNS * mainRows ? layoutIndex + BACKPACK_COLUMNS
            : layoutIndex - BACKPACK_COLUMNS * mainRows;
    }

    /** 装配底部带（三块各一次 {@code excludeAreaInRecipeViewer()}，R36/§16 每块一处）。 */
    public static List<ParentWidget<?>> build(NekoPocketPanel ui) {
        final List<ParentWidget<?>> blocks = new ArrayList<>(3);
        blocks.add(coinBlock(ui));
        blocks.add(backpackBlock(ui));
        blocks.add(bindBlock(ui));
        return blocks;
    }

    // ------------------------------------------------------------------ 左段：币值（猫猫机形态）+ 两个通道按钮

    /**
     * 左段（100×72）：两条币值条（各 88×18）+ 两个通道按钮（各 88×18），纵向 4×18 = 72。
     * <p>
     * R78① 的"收窄"：旧口径这一段是 180 宽（两条币值条并排 + 两个通道按钮并排 + 一段说明文字），
     * 现在两条并排改纵向，且★说明文字（{@code note.channel}）撤进各自 tooltip（R74② / R78 D-2）。
     */
    private static ParentWidget<?> coinBlock(NekoPocketPanel ui) {
        return new ParentWidget<>().pos(COIN_X, Y)
            .size(COIN_WIDTH, HEIGHT)
            .name("pocket_coin_block")
            .child(coinRow(ui, NekoCurrencyRegistrar.NEKO_ID, 0, "pocket_balance_neko"))
            .child(coinRow(ui, NekoCurrencyRegistrar.SHIMMERING_NEKO_ID, 1, "pocket_balance_shimmering"))
            .child(channelButtons(ui));
    }

    /**
     * 一行币值（猫猫机形态：图标 + 数量 + 两枚快捷小图标）。
     *
     * @param currencyId 币种（本仓的两种：猫猫币 / 闪烁猫猫币）
     * @param row        第几行（0 = 猫猫币 ⇒ 对应瞬时通道；1 = 闪烁币 ⇒ 对应短效通道）
     */
    private static IWidget coinRow(NekoPocketPanel ui, String currencyId, int row, String name) {
        final String displayName = NekoCurrencyRegistrar.getDisplayName(currencyId);
        final ItemStack iconStack = currencyIcon(currencyId);
        final IKey balanceKey = IKey.lang(
            row == 0 ? "gtit.pocket.balance.neko" : "gtit.pocket.balance.shimmering",
            () -> new Object[] { NekoClientBalances.getBalance(currencyId) });
        final NekoPocketPanel.ChannelRequest channel = row == 0 ? NekoPocketPanel.ChannelRequest.BURST
            : NekoPocketPanel.ChannelRequest.SHORT;
        final IKey costKey = IKey.lang(
            row == 0 ? "gtit.pocket.channel.instant" : "gtit.pocket.channel.timed",
            row == 0 ? new Object[] { PocketConstants.BURST_COST_NEKO }
                : new Object[] { PocketConstants.SHORT_COST_SHIMMERING_NEKO, PocketConstants.SHORT_CHANNEL_SECONDS });
        final ParentWidget<?> bar = new ParentWidget<>().pos(COIN_BAR_X, row * COIN_BAR_HEIGHT)
            .size(COIN_BAR_WIDTH, COIN_BAR_HEIGHT)
            .name(name)
            .background(PocketGuiTextures.COIN_BAR)
            // 图标（形态源 NekoCoinDisplayV2.java:107-116，尺寸按 18 高的条收小）
            .child(
                new ItemDisplayWidget().item(iconStack)
                    .displayAmount(false)
                    .disableThemeBackground(true)
                    .pos(1, 1)
                    .size(COIN_ICON_SIZE, COIN_ICON_SIZE)
                    .name(name + "_icon"))
            // 数量（形态源 NekoCoinDisplayV2.java:118-125 + :336-344 的可读串）
            .child(
                (IWidget) new TextWidget(IKey.dynamic(() -> readableAmount(NekoClientBalances.getBalance(currencyId))))
                    .textAlign(Alignment.CenterLeft)
                    .scale(0.5f)
                    .pos(COIN_ICON_SIZE + 3, 0)
                    .size(30, COIN_BAR_HEIGHT))
            // 第一枚快捷键：借 NekoCoinDisplayV2.java:127-158 的形态；语义 = 用该币激活它自己的通道
            .child(
                quickIcon(
                    NekoGuiTextures.EJECT_COINS,
                    COIN_ICON_SIZE + 3 + 32,
                    row,
                    name + "_quick_channel",
                    costKey,
                    () -> ui.requestChannel(channel)))
            // 第二枚快捷键：借 NekoCoinDisplayV2.java:179-203 的形态；★只读明细，不借"存币"语义
            .child(
                quickIcon(
                    NekoGuiTextures.WALLET_PERSONAL,
                    COIN_ICON_SIZE + 3 + 32 + QUICK_ICON_SIZE + 2,
                    row,
                    name + "_quick_info",
                    balanceKey,
                    null));
        bar.tooltip(tooltip -> tooltip.addLine(balanceKey));
        return bar;
    }

    /**
     * 一枚 12×12 的快捷小图标（形态照 {@code NekoCoinDisplayV2}：主题底关掉、只画纹理图标）。
     *
     * @param action 点击动作；{@code null} = <b>只读</b>件（只带 tooltip，不发任何请求）
     */
    private static IWidget quickIcon(UITexture texture, int x, int row, String name, IKey tip, Runnable action) {
        final ButtonWidget<?> button = new ButtonWidget<>().pos(x, 3)
            .size(QUICK_ICON_SIZE, QUICK_ICON_SIZE)
            .name(name)
            .disableThemeBackground(true)
            .disableHoverThemeBackground(true)
            .overlay(
                new IDrawable[] { texture.asIcon()
                    .size(QUICK_ICON_SIZE) })
            .playClickSound(false)
            .tooltip(tooltip -> tooltip.addLine(tip));
        if (action == null) {
            // ★只读件：把点击吃掉但不发任何请求（返回 true = SUCCESS，点击不会穿到下面的币值条）
            return button.onMousePressed(mouseButton -> true);
        }
        return button.onMousePressed(mouseButton -> {
            if (mouseButton != 0) {
                return true;
            }
            action.run();
            return true;
        });
    }

    /**
     * 需求 3 的两个通道按钮（R78① 后纵向排在左段下半）：各 {@code POCKET_C2_btn} 88×18，
     * 起点 {@code y = 2×18 = 36} 与 {@code y = 54}。
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
        return new ParentWidget<>().pos(0, 2 * COIN_BAR_HEIGHT)
            .size(COIN_WIDTH, 2 * COIN_BAR_HEIGHT)
            .name("pocket_channel_buttons")
            .child(
                new ButtonWidget<>().pos(COIN_BAR_X, 0)
                    .size(BUTTON_WIDTH, COIN_BAR_HEIGHT)
                    .name("pocket_button_instant")
                    .background(PocketGuiTextures.BUTTON)
                    .hoverBackground(PocketGuiTextures.BUTTON_PRESSED)
                    .child(
                        (IWidget) new TextWidget(instant).scale(0.5f)
                            .textAlign(Alignment.Center))
                    .tooltip(tooltip -> tooltip.addLine(instant))
                    .onMousePressed(button -> button == 0 && ui.requestChannel(NekoPocketPanel.ChannelRequest.BURST)))
            .child(
                new ButtonWidget<>().pos(COIN_BAR_X, COIN_BAR_HEIGHT)
                    .size(BUTTON_WIDTH, COIN_BAR_HEIGHT)
                    .name("pocket_button_timed")
                    .background(PocketGuiTextures.BUTTON)
                    .hoverBackground(PocketGuiTextures.BUTTON_PRESSED)
                    .child(
                        (IWidget) new TextWidget(timed).scale(0.5f)
                            .textAlign(Alignment.Center))
                    .tooltip(tooltip -> tooltip.addLine(timed))
                    .onMousePressed(button -> button == 0 && ui.requestChannel(NekoPocketPanel.ChannelRequest.SHORT)));
    }

    /**
     * 币的物品栈（形态源 {@code NekoCoinDisplayV2.java:109-113}，含同一份"未注册货币"兜底）。
     * <p>
     * ★兜底与那边逐字同形（木炭 meta 1）：这不是抄来好看的——币种未注册时 {@code getItemStack}
     * 返回 null，图标位留空会让玩家以为"这一行坏了"，占位图标至少说明"这里该有一枚币"。
     */
    private static ItemStack currencyIcon(String currencyId) {
        final ItemStack stack = NekoCurrencyRegistrar.getItemStack(currencyId, 1);
        return stack != null ? stack : new ItemStack(net.minecraft.init.Items.coal, 1, 1);
    }

    /**
     * 可读数量串（★形态照 {@code NekoCoinDisplayV2.java:336-344} 的 {@code getReadableString}：
     * 那是"为什么 179 显示成 179 而不是 179.0 / 1,000 显示成 1K"的现成答案）。
     * <p>
     * 那边是 private static ⇒ 本仓不跨包改它（禁改 {@code gui/vm/**} 与 {@code client/gui/**}
     * 不在本包边界内），故在这里重述同一条算式；改阈值时两处都要改，已在本注释点名同源。
     */
    static String readableAmount(int amount) {
        if (amount < 10_000) {
            return Integer.toString(amount);
        }
        if (amount < 1_000_000) {
            return amount / 1_000 + "K";
        }
        return amount / 1_000_000 + "M";
    }

    // ------------------------------------------------------------------ 中间段：玩家背包 9×4（R78① 回归）

    /**
     * 玩家背包块（162×72，正好填满带的中间段）。
     * <p>
     * ★这里的每个 {@link ItemSlot} <b>不</b>经 {@link PocketSlots} 工厂、也<b>不</b>自造
     * {@code ModularSlot}：只用 {@code syncHandler(PLAYER_SLOT_SYNC_NAME, 槽号)} 绑到框架
     * {@code bindPlayerInventory} 注册好的那 36 个 handler 上（同一绑法见库内
     * {@code SlotGroupWidget#playerInventory}）。因此：
     * <ul>
     * <li>不调 {@code .synced(...)}：那 36 格有自己的同步通道（vanilla 槽包），组级同步键反而会把
     * 它们二次注册；</li>
     * <li>不调 {@code .slotGroup(...)}：那 36 格已经属于框架的 {@code player_inventory} 组
     * （rowSize 9、{@code allowShiftTransfer=true}），重新贴组名会让 shift 落点算错；</li>
     * <li>布局序号→槽号的映射只有 {@link #backpackPlayerSlotAt(int)} 这一处（快捷栏画在最下行）。</li>
     * </ul>
     */
    private static ParentWidget<?> backpackBlock(NekoPocketPanel ui) {
        final SlotGroupWidget group = SlotGroupWidget.builder()
            .matrix(BACKPACK_MATRIX)
            .key(
                'P',
                index -> new ItemSlot().syncHandler(PLAYER_SLOT_SYNC_NAME, backpackPlayerSlotAt(index))
                    .name("pocket_backpack_" + index)
                    .background(PocketGuiTextures.SLOT))
            .build();
        group.pos(0, 0);
        final ParentWidget<?> root = new ParentWidget<>().pos(BACKPACK_X, Y)
            .size(BACKPACK_WIDTH, BACKPACK_HEIGHT)
            .name("pocket_backpack_block")
            .child(group)
            .tooltip(tooltip -> tooltip.addLine(IKey.lang("gtit.pocket.backpack.note")));
        return root.excludeAreaInRecipeViewer();
    }

    // ------------------------------------------------------------------ 右段：绑定 / 解绑 + 帮助

    /**
     * 绑定段（120×72）：绑定按钮（左键绑定 / 右键解绑末条 / Shift 右键清空）+ 绑定格 +
     * 一行状态 + 帮助按钮（★R78 D-2 撤下来的说明的可见入口）。
     */
    private static ParentWidget<?> bindBlock(NekoPocketPanel ui) {
        final SlotGroupWidget bindGroup = SlotGroupWidget.builder()
            .matrix(BIND_MATRIX)
            .key('B', index -> {
                final ItemSlot slot = new ItemSlot();
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
        bindGroup.pos(BIND_SLOT_X, BIND_SLOT_Y);

        final IKey summary = IKey.dynamic(ui::bindSummaryText);
        final ParentWidget<?> root = new ParentWidget<>().pos(BIND_X, Y)
            .size(BIND_WIDTH, HEIGHT)
            .name("pocket_bind_block")
            .child(
                new ButtonWidget<>().pos(0, 0)
                    .size(BIND_BUTTON_WIDTH, COIN_BAR_HEIGHT)
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
                (IWidget) new TextWidget(summary).textAlign(Alignment.CenterLeft)
                    .scale(0.5f)
                    .pos(BIND_SLOT_X + NekoPocketPanel.GRID + 4, BIND_SLOT_Y)
                    .size(BIND_WIDTH - BIND_SLOT_X - NekoPocketPanel.GRID - 4, COIN_BAR_HEIGHT))
            .child(helpButton(ui));
        return root.excludeAreaInRecipeViewer();
    }

    /**
     * 帮助按钮（★R78 D-2 的补偿落点）：tooltip = {@code notesText()}（用法摘要 / 通道成本 /
     * 主手限制 / ghost 用法与"每格声明吃掉一格真实容量"的代价）+ 绑定与解绑的两条说明。
     * <p>
     * 它不新增任何信息，只是把原本常驻在左栏的文字换成"悬停看得到"；放在右段第三行的留白位
     * （背包 4 行占满纵向 ⇒ 那里本来就没有别的落点）。
     */
    private static IWidget helpButton(NekoPocketPanel ui) {
        return new ButtonWidget<>().pos(0, BIND_SLOT_Y + COIN_BAR_HEIGHT + 4)
            .size(NekoPocketPanel.GRID, NekoPocketPanel.GRID)
            .name("pocket_help_button")
            .background(PocketGuiTextures.BUTTON)
            .child(
                (IWidget) new TextWidget(IKey.str("?")).scale(0.8f)
                    .textAlign(Alignment.Center))
            .playClickSound(false)
            .tooltip(tooltip -> {
                tooltip.addLine(IKey.dynamic(ui::notesText));
                tooltip.addLine(IKey.lang("gtit.pocket.bind.unbind_hint"));
                tooltip.addLine(IKey.lang("gtit.pocket.bind.stale_hint"));
            })
            .onMousePressed(mouseButton -> true);
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

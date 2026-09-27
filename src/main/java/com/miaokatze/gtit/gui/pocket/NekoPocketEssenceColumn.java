package com.miaokatze.gtit.gui.pocket;

import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.api.widget.Interactable;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.utils.Color;
import com.cleanroommc.modularui.value.sync.DoubleSyncValue;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widgets.ProgressWidget;
import com.cleanroommc.modularui.widgets.SlotGroupWidget;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.miaokatze.gtit.common.items.pocket.PocketConstants;
import com.miaokatze.gtit.common.items.pocket.PocketInventory;
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
 * （★R83 D-8 起它挂的是契约第 13 个 token {@code POCKET_C2_progress}；R77 当年"契约没有进度件 ⇒
 * 只走主题底"的裁定是被补全的，不是被推翻的，"腾不出位置"从来不是撤它的理由）。
 * ⇒ 裁定取"槽位意义上的空行 + 进度条住这里"；常驻的<b>蒸馏状态文字</b>则按 R74②/R78 D-2
 * 撤进 tooltip（旧那 72px 摘要段随 12 行盘一起退场）。若主代理要的是"字面全空"，
 * 唯一出路是把进度条也撤成 tooltip-only —— 那是产品决定，本文件不改数、只把这个分叉写清。
 * <p>
 * <b>72 格是纯显示件</b>（R35：源质格全部 phantom 侧，<b>不进 Container</b>，不计入 220）：
 * 每格 = {@code UITexture(location=TaumCompat.imageLocationOf(tag)).fullImage().nonOpaque()}
 * + {@code colorOverride=colorOf(tag)}（R30：<b>{@code nonOpaque} 必给</b>，否则
 * {@code withBlend=false} 走 {@code disableBlend}，把带 alpha 的 aspect 图标画成<b>黑块</b>）。
 * ★R97 S4：location 走桥读 {@code Aspect.getImage()} 真资源域（附属 aspect 不再落
 * thaumcraft 域 missing）；无桥/未知/异常回落 {@code TaumDistillRules.aspectTexturePath} 公式串。
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

    /**
     * ★进度值的同步键（R83 D-8）。<b>本列唯一一处第二份真相</b>，理由写在下面：
     * {@code ProgressWidget} 必须吃<b>已注册进 sync manager 的那个</b> {@code DoubleSyncValue}
     * 实例（{@code NekoPocketPanel#registerSyncValues} 的 SYNC_PROGRESS 行）——现场 new 出来的实例
     * 没人喂值：{@code DoubleSyncValue} 的 cache 只在构造时取一次，之后只由注册表驱动
     * （{@code PanelSyncManager#detectAndSendChanges}），widget 也不会自我登记。
     * ★R83 批 D：键名不再是字面量，直接引 Panel 的那一份（{@code NekoPocketPanel#SYNC_PROGRESS} 已放行成
     * package-private）⇒ 注册侧与查找侧不可能再漂移。失败方式仍是"查找返回 null ⇒ 退回旧行为（条不动）"，
     * 不是崩溃也不是错档。
     */
    private static final String SYNC_PROGRESS_KEY = NekoPocketPanel.SYNC_PROGRESS;

    /**
     * 进度条材质的<b>契约 token</b>（★必须与 {@code PocketGuiTextureContract} 第 13 行的名字逐字相同；
     * 这条"必须相同"不是靠自觉：静态块里拿 {@code widthOf(本串)} 与列宽对账，改名不同步就装配期当场抛，
     * 而不是留一根悄悄不画的条）。
     */
    private static final String PROGRESS_TOKEN = "POCKET_C2_progress";

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
        // ★材质 token 与契约行必须逐字同步（`widthOf` 查不到名字返回 0）：改名不同步就在这里炸，
        // 而不是留一根画不出东西的进度条。图宽 == 列宽 == 条长 ⇒ 满条支 1:1，不横向拉伸。
        // ★这里只准碰契约表（纯字符串/整数）：`PocketGuiTextures` 的类初始化会建 `UITexture`，
        // 零依赖回归套件的 JVM 里拿不到 fastutil ⇒ 静态块一旦引用它，整套用例直接炸。
        if (PocketGuiTextureContract.widthOf(PROGRESS_TOKEN) != WIDTH) {
            throw new IllegalStateException(
                "[pocket] 进度条材质 " + PROGRESS_TOKEN
                    + " 的契约宽与右列宽不符: "
                    + PocketGuiTextureContract.widthOf(PROGRESS_TOKEN)
                    + " != "
                    + WIDTH);
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
                // ★R83 B2：本列不留"只在装配期跑一次"的 tooltip 形态（`ITooltip.tooltip(Consumer)` =
                // `tooltipStatic`，方法体就一句 accept ⇒ 行集永久固化）。这两行文案本身不随状态变，
                // 但 `tooltipDynamic` 少了 `tooltipAutoUpdate(true)` 会<b>一个字都不画</b>
                // （`RichTooltip.draw` 先 `if (autoUpdate) markDirty()`，再 `isEmpty()` 才重建），
                // 所以两条必须成对。
                slot.tooltipDynamic(tooltip -> {
                    tooltip.addLine(IKey.lang("gtit.pocket.still.in"));
                    tooltip.addLine(
                        IKey.lang(
                            "gtit.pocket.still.dual_use_note",
                            () -> new Object[] { PocketInventory.DISTILL_INPUT_SLOTS }));
                })
                    .tooltipAutoUpdate(true);
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
     * ★<b>R86 改判（作废 R78③）</b>：声明虽然仍按<b>格号</b>登记，但"回收格位会拉错东西"这一句被取证
     * 证伪——抽取侧读的是声明<b>自带</b>的 {@code tag/typeId}（{@code PocketAeChannelOps#extractEssence}），
     * 从不按 {@code slotIndex} 反查内容。所以清零照旧腾格，遮罩则改由 {@code NekoPocketPanel#applyEssenceGhosts}
     * 按 tag 现读归位；<b>本格</b>的声明入口判据仍是"拖来的东西确实含本格的 tag"，不变。
     * <p>
     * 需求 2 的"要素栏取出 → 装满的源质瓶"是另一条独立路径（R15；★R88 载体改判，旧口径"→ 晶化源质"
     * 已退役为只读），★<b>左键</b>与 Shift+左键走 {@code ESSENCE_OUT}
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
     * <b>★按库存开关</b>，R78 D-1）+ tooltip（★每次重画都重建，见方法体★注释）；
     * <b>左键</b>=把该组源质物化成<b>安瓿瓶</b>拿到游标上（★R88：一瓶固定
     * {@code PocketConstants.ESSENCE_OUT_UNIT_POINTS} 点，一组至多
     * {@code PocketConstants.ESSENCE_OUT_MAX_PHIALS_PER_ACTION} 只；凑不满一瓶的余数<b>留盘</b>并给
     * 面板回执；游标已被占用则整笔不动并给回执），
     * <b>Shift+左键</b>=该格整份按瓶一次进背包（背包优先、余量落中栏），NEI 左键拖入=声明 ghost（落点归本列），
     * 右键在声明态=解绑（{@link NekoEssenceGhostCell#onMousePressed(int)}）；其余按键不做取出。
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
            // ★R83 B2 (5)：取出要有反馈音（旧实现连着把它关了 ⇒ "点了没动静"是 3e 观感的一半）
            .playClickSound(true)
            // ★tag 现读（读的是同步镜像里"这一格当前的归属 tag"）：R78③ 后格位归属会变，
            // 装配期捕获的 tag 到点击时可能已经不是这一格的了
            // ★R83 B2 (5)：<b>只有左键</b>取出。旧写法把 `button` 形参整个丢掉 ⇒ 右键/中键也各出 1 点，
            // 右键还与 ghost 格的"右键解绑"撞成两种读法（{@code NekoEssenceGhostCell#onMousePressed}）。
            // 谓词返 false = {@code Result.ACCEPT}（{@code ButtonWidget.java:70-81}）⇒ 不做动作，
            // 但事件继续按 MUI2 的常规派发走，不额外造第二套断链。
            .onMousePressed(
                button -> button == 0
                    && ui.requestEssenceOut(index, ui.essenceTagAtCell(index), Interactable.hasShiftDown()));
        ui.trackEssenceCell(index, cell);
        cell.child(
            (IWidget) new TextWidget(IKey.dynamic(cell::stockText)).textAlign(Alignment.BottomRight)
                .scale(PocketGhostRequest.RESIDENT_TEXT_SCALE)
                // ★R83 B2 (4)：数量标注的可读性——暗主题 {@code vanilla_dark.json} 的 textShadow
                // 是 false，4px 高的数字直接压在带 alpha 的彩色 aspect 图标上会糊成一片；
                // 这里不跟随主题（白字 + 阴影），与下面 tooltip 名字行同一个口径。
                .color(Color.WHITE.main)
                .shadow(Boolean.TRUE)
                .pos(0, 8)
                .size(17, 9));
        // ★R83 B2 (1)：注册形态必须是 `tooltipDynamic`。旧写法走的是一次性通道
        // （{@code ITooltip.java:64-66} → {@code :70-80}「Only called once」）⇒ 下面这三行分支
        // （空/有 tag、ghost）在<b>装配期</b>就被固化，本格后来蒸出源质也不会补出名字与数量行；
        // {@code NekoEssenceGhostCell} 构造器里的 `setAutoUpdate(true)` 与两处 `markTooltipDirty()`
        // 在 {@code tooltipBuilder == null} 时是空转（{@code RichTooltip.java:92-99}）。
        // 换成动态注册后：装配顺序（ghost 位晚于本列装配）与"tag 晚到"都不再需要额外触发点。
        cell.tooltipDynamic(tooltip -> {
            final String tag = cell.aspectTag();
            // ★R85 N3：ghost blob 被长度预算收口时，尾部声明只少了客户端这一层虚化渲染（服务端执法照常），
            // 但<b>必须</b>有读数，否则玩家的读法是"我声明过的格子怎么自己变回了普通格"。
            // 落点选在本文件：三类 ghost 格<b>各自</b>的 tooltip 代码全在禁写面（NekoFilterSlot /
            // NekoPocketFluidSlot / NekoEssenceGhostCell），本列的 tooltipDynamic 是白名单内唯一一处
            // "ghost 格的 tooltip" ⇒ 读数挂在这里，★两条分支（空位 / 有 tag）都得加，否则"声明全是物品"
            // 那种最常见的形态恰好永远看不到它（R81 的"面缺失"同族）。
            final int ghostNotSynced = ui.ghostNotSyncedCount();
            if (tag == null) {
                // 空格位：没有归属 tag ⇒ 只给"这一格还空着/TC 不在场"的读法（R31 的整栏灰显另有 tooltip）
                tooltip.addLine(
                    IKey.lang(ui.essenceAvailable() ? "gtit.pocket.aspect.empty" : "gtit.pocket.still.unavailable"));
                // ★R86（缺陷 4 乙）→ ★R90 E3（D3）改判：空格的拖入入口已放开到「拖入物自带可读 tag」
                // （满瓶 ⇒ 建档声明 + assignCell 占格，判据 NekoEssenceGhostCell#tagOfCarrier），本行
                // 因此从"解释为什么不收"变成"指名要哪种拖拽物"——文案（gtit.pocket.essence.need_stock）
                // 随 S5 撤回改写（lang 两份归主代理），无 NBT 裸栈/裸晶的拒收面不变。
                if (ui.essenceAvailable()) {
                    tooltip.addLine(IKey.lang("gtit.pocket.essence.need_stock"));
                }
                if (ui.essenceOverflow()) {
                    tooltip.addLine(
                        IKey.lang(
                            "gtit.pocket.aspect.overflow_note",
                            () -> new Object[] { PocketConstants.ESSENCE_DISPLAY_GRID }));
                }
                if (ghostNotSynced > 0) {
                    tooltip.addLine(IKey.lang("gtit.pocket.ghost.not_synced", ghostNotSynced));
                }
                // ★★R92-⑤（D5）：空格位补两样 —— 这一格是盘上第几格（此前<b>只有 tag 名</b>、没有格身份）
                // 与三个功能键的作用。★放在本支末尾而不是开头：TC 不在场那一行仍是玩家第一眼要看到的。
                PocketCellIdentity.addEssenceIdentity(tooltip, cell.cellIndex());
                PocketCellIdentity.addKeyHints(tooltip);
                return;
            }
            tooltip.addLine(IKey.str(EnumChatFormatting.WHITE + TaumCompat.nameOf(tag)));
            tooltip.addLine(IKey.dynamic(() -> ui.essenceAmountDetail(tag)));
            // ★R88 自立口径 C1 的<b>格级</b>可见面：取出按整瓶向下取整、余数留盘 —— 只写在帮助块里
            // 玩家点不到（帮助块在底部带），而这条恰恰是"点了怎么少给一点"的直接答案，故挂在本格 tooltip。
            tooltip.addLine(
                IKey.lang(
                    "gtit.pocket.essence.phial_note",
                    () -> new Object[] { PocketConstants.ESSENCE_OUT_UNIT_POINTS }));
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
            if (ghostNotSynced > 0) {
                tooltip.addLine(IKey.lang("gtit.pocket.ghost.not_synced", ghostNotSynced));
            }
        });
        return cell;
    }

    /**
     * 唯一的一根进度条（R78：住在 12 行盘与蒸馏盘之间那一个<b>零槽</b>的"空行"里，
     * {@code y=216}、高 18；读法与理由见类 javadoc 的★段）。方向 {@code Direction.RIGHT} =
     * <b>横向</b>（用户要的"类似炼金炉但横向"里"横向"这一维一直是在的）。
     * <p>
     * ★R83 D-8 把两处"必然不可见"一起闭上：
     * <ol>
     * <li><b>没材质 ⇒ 零像素</b>：{@code ProgressWidget.draw()} 的填充支整块写在
     * {@code if (fullTexture[0] != null && progress > 0)} 里，本件过去从未调 {@code texture(...)}
     * ⇒ 一根像素都不画（只剩主题底）。现在挂契约第 13 个 token {@code POCKET_C2_progress}：
     * 传的是<b>整张堆叠图</b>，widget 自己按 {@code ProgressWidget.texture(单张堆叠图, imageSize)}
     * 的约定折半（上半空槽、下半满条），故这里不再手拆 UV。</li>
     * <li><b>值恒 0</b>：过去接的是 {@link NekoPocketPanel#distillProgressValue()} <b>现场 new 的未注册
     * 实例</b>（注册的是另一个），cache 冻结在装配那一刻的 0 ⇒ 见 {@link #distillProgressValue}。</li>
     * </ol>
     * 值仍走同步通道：<b>GUI 只显示、绝不推进</b>（推进是 {@code Item.onUpdate} 服务端分支里
     * {@code PocketDistillDriver} 的节拍，单一权威 {@code TaumDistillRules.DISTILL_INTERVAL_TICKS}）。
     * <p>
     * ★"动画"这一词的口径：MUI2 的 {@code ProgressWidget} 只有"按值裁切"（平滑或按像素步进，
     * 由 {@code ModularUIConfig.smoothProgressBar} 决定），<b>没有帧带</b>；本仓这 13 张 C2 贴图
     * 也全是静态件 ⇒ 用户看到的"动画"= 每 tick 连续推进的填充条，与炼金炉同构（炼金炉本身也只是
     * 按 tick 缩放高度的竖条）。真要帧带得另开资产面。
     * <p>
     * ★R78 D-2：常驻的"状态摘要段"随 12 行盘退场，四条状态文案现在只走本条与蒸馏格的 tooltip；
     * 进度条自身就是"跑到哪了"的可见状态回显 ⇒ 撤走的是文字、不是可见面。
     */
    private static IWidget progressBar(NekoPocketPanel ui) {
        return new ProgressWidget().pos(0, ESSENCE_HEIGHT)
            .size(WIDTH, SEPARATOR_HEIGHT)
            .name("pocket_distill_progress")
            .texture(PocketGuiTextures.PROGRESS, PocketGuiTextureContract.widthOf(PROGRESS_TOKEN))
            .direction(ProgressWidget.Direction.RIGHT)
            .value(distillProgressValue(ui))
            .tooltipAutoUpdate(true)
            .tooltipDynamic(tooltip -> {
                tooltip.addLine(IKey.lang("gtit.pocket.still.title"));
                tooltip.addLine(
                    IKey.lang(
                        "gtit.pocket.still.dual_use_note",
                        () -> new Object[] { PocketInventory.DISTILL_INPUT_SLOTS }));
                // 状态行每帧现读（每次重建都重新取值）⇒ 不在客户端复算状态机
                tooltip.addLine(IKey.dynamic(() -> distillStateLine(ui)));
            });
    }

    /**
     * 进度值的<b>单点取用</b>：优先拿<b>已注册</b>的那份 {@code DoubleSyncValue}（键见
     * {@link #SYNC_PROGRESS_KEY} 的注释），注册表里查不到才退回 {@code ui.distillProgressValue()}
     * 那个未注册实例（= 旧行为：条不动，但不炸、不静默错档）。
     * <p>
     * ★<b>接线入口</b>：批 D 若把 Panel 的键常量放行或加 {@code distillProgressSync()} 转发，
     * 只需把本方法体换成那一次调用，本文件其余部分不用动。
     */
    private static DoubleSyncValue distillProgressValue(NekoPocketPanel ui) {
        final DoubleSyncValue registered = ui.syncManager()
            .findSyncHandlerNullable(SYNC_PROGRESS_KEY, DoubleSyncValue.class);
        return registered != null ? registered : ui.distillProgressValue();
    }

    /**
     * 蒸馏状态行的<b>单源拼串</b>（★R87-h）：raw 读数进、本地化行出——服务端同步 getter
     * （{@code NekoPocketPanel#distillStateLineText}）与客户端 tooltip 消费同一段，不复制两份真相。
     * <p>
     * 挑键规则（不新造键，A2 的 {@code OVER_CAP} ⇒ {@code gtit.pocket.still.over_cap} 除外）：
     * <ul>
     * <li>{@code STORE_FULL}：{@code still.full}；带 {@code skipped > 0} 再补 {@code still.partial_skip}
     * （这一档里被计数的格是"只是这轮没塞下"，下一轮真的可能被收下）；</li>
     * <li>{@code NO_ASPECT}：{@code still.no_aspect}；</li>
     * <li>{@code OVER_CAP}：{@code still.full} 或 {@code still.over_cap}（"超出单格上限"是真话）；</li>
     * <li>{@code RUNNING}：{@code still.progress}（距下一轮秒数）；带 {@code skipped > 0} 再补
     * {@code still.over_cap}（这一档里被计数的格必然是"永久超上限"那类，播"下一轮重试"是谎报）；</li>
     * <li>{@code IDLE}/default：{@code still.idle}。</li>
     * </ul>
     * {@code skipped == 0} 时两条带读数的补充行一律不播（0 格没塞下 / 0 格超上限是当场可笑的谎）。
     */
    public static String composeDistillStateLine(PocketDistillDriver.Status status, int secondsToNext, int skipped,
        int discardedPoints) {
        switch (status) {
            case STORE_FULL: {
                final String full = StatCollector.translateToLocal("gtit.pocket.still.full");
                return skipped <= 0 ? full
                    : full + "\n"
                        + String.format(
                            StatCollector.translateToLocal("gtit.pocket.still.partial_skip"),
                            skipped,
                            discardedPoints);
            }
            case NO_ASPECT:
                return StatCollector.translateToLocal("gtit.pocket.still.no_aspect");
            case OVER_CAP:
                // 两个读数是 A2 新开的只读转发；★R84 起单位是<b>格</b>（放弃了几个格、共几点），
                // 旧口径"几个组"作废
                return skipped <= 0 ? StatCollector.translateToLocal("gtit.pocket.still.full")
                    : String
                        .format(StatCollector.translateToLocal("gtit.pocket.still.over_cap"), skipped, discardedPoints);
            case RUNNING: {
                final String progress = String
                    .format(StatCollector.translateToLocal("gtit.pocket.still.progress"), secondsToNext);
                // ★R84（补 R84 蒸馏片的自报缺口）：只要有<b>一格</b>建效，状态就走 RUNNING，
                // 而"这一轮仍有 N 格没塞下"在旧代码里就此消失 ⇒ 用户看到的还是"蒸了几格，其余无声没掉"。
                // ★R85 小项 3：这一档里被计数的格必然是"永久超上限"那类（needsRoom 为真就落 STORE_FULL
                // 了），所以播 over_cap 那句真话，不再播"下一轮继续重试"。
                return skipped <= 0 ? progress
                    : progress + "\n"
                        + String.format(
                            StatCollector.translateToLocal("gtit.pocket.still.over_cap"),
                            skipped,
                            discardedPoints);
            }
            case IDLE:
            default:
                return StatCollector.translateToLocal("gtit.pocket.still.idle");
        }
    }

    /**
     * 状态行读取口（tooltip 每帧重建）：★R87-h 起双端都走 {@code ui.distillStateLineText()} 的
     * 双源 accessor——客户端读同步镜像（专用服客户端 JVM 无服务端 static {@code CLOCKS}，旧实现
     * 直读恒 IDLE/0，多人恒 IDLE 即审计缺口 #10），服务端现算；拼串单源在
     * {@link #composeDistillStateLine}。
     */
    private static String distillStateLine(NekoPocketPanel ui) {
        return ui.distillStateLineText();
    }
}

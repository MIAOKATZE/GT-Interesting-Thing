package com.miaokatze.gtit.gui.pocket;

import net.minecraft.item.ItemStack;

import com.cleanroommc.modularui.api.UpOrDown;
import com.cleanroommc.modularui.api.widget.Interactable;
import com.cleanroommc.modularui.drawable.GuiDraw;
import com.cleanroommc.modularui.drawable.UITexture;
import com.cleanroommc.modularui.integration.recipeviewer.RecipeViewerGhostIngredientSlot;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.miaokatze.gtit.common.items.infinitycell.InfinityStackTypes;
import com.miaokatze.gtit.common.items.pocket.PocketConstants;
import com.miaokatze.gtit.common.items.pocket.PocketFilterConfig;
import com.miaokatze.gtit.common.items.pocket.distill.EssenceGate;
import com.miaokatze.gtit.crossmod.taum.TaumAspectAmounts;
import com.miaokatze.gtit.crossmod.taum.TaumCompat;

import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IAEStackType;

/**
 * 右栏 72 格源质盘的单格（需求 4 的源质入口）：显示格 + 可被 NEI 拖入的 ghost 声明位。
 * <p>
 * <b>为什么必须换类而不是加东西</b>（R70）：原来这些格是裸 {@link ButtonWidget}，
 * 它不实现 {@link RecipeViewerGhostIngredientSlot} ⇒ 面板的拖入分发（按 hover 列表 +
 * {@code instanceof} 判定）根本看不到它 ⇒ 「源质格拖入」在游戏内零入口。本类
 * {@code extends ButtonWidget<NekoEssenceGhostCell>} <b>并</b>实现该接口：
 * 既有的点击取晶、着色、数量浮层、tooltip 与 6×12 布局<b>一格不加不减</b>（R41b/R78②）。
 * <p>
 * <b>★内容层按库存开关（R78 D-1，本片修的上一片交付不实）</b>：旧实现把 aspect 图标
 * <b>无条件</b> {@code overlay(icon)} ⇒ 空格也画图标，与"无货不画内容"的裁定相反，
 * 而本类旧 javadoc 又写着"无货时只撤掉图标与文本"⇒ <b>注释声明了代码没做的事</b>。
 * 现在唯一写入口是 {@link #setCellContent(String, int)}：
 * <ul>
 * <li>{@code tag != null && 库存 > 0} ⇒ overlay = 该 tag 的图标（着色）、数量文本 = 点数；</li>
 * <li>否则 ⇒ {@code overlay()} 清空（MUI2 的 {@code IDrawable.of(无元素)} 返回 {@code null}，
 * {@link #hasContentLayer()} 读的就是这个 widget 真值而不是自报标记）、数量文本清空。</li>
 * </ul>
 * ★<b>槽位底与格位本体恒在</b>（R73②「空格子不显示源质，但是格子本身要显示」）：撤的是
 * <b>内容层</b>，不是格子。
 * <p>
 * <b>★声明语义 = 「确认把本格对应的 tag 声明为 ghost」</b>：格位归属由服务端算好并随
 * 同步 blob 落到本类（R78③：格序 = 该 tag 首次入账的顺序，且撤空不回收），所以拖进来的
 * 东西不需要"是什么"，但<b>必须确实含本格的 tag</b>（{@link #carriesTag}：蒸馏产出或容器内容
 * 任一命中）。不含 ⇒ 返回 false，NEI 那边继续拖着、不吃栈 ⇒ 玩家拖来的任意东西不会被当成源质声明。
 * <p>
 * <b>★ghost 只原位改属性</b>（R41b）：实例从装配到关屏不换，声明态只影响遮罩与 tooltip；
 * 内容层同样走 {@link #setCellContent(String, int)} 原位切换 ⇒ 格数恒定 72、widget 树不因
 * 数据变化（R32 双端同树的前提）。72 格本就是显示侧、<b>不进 Container</b>（R35），
 * 因此既不占 220 也不动槽号。
 * <p>
 * <b>TC 不在场时一律不收</b>（R31 的整栏灰显由 {@code setEnabledIf} 给出，本类用
 * {@code IWidget#areAncestorsEnabled()} 读它，与 {@link NekoFilterSlot} 同一口径）。
 */
public class NekoEssenceGhostCell extends ButtonWidget<NekoEssenceGhostCell>
    implements RecipeViewerGhostIngredientSlot<ItemStack> {

    /** 虚化遮罩色（与 {@link NekoFilterSlot}、{@link NekoPocketFluidSlot} 同一 alpha 口径，R18）。 */
    private static final int GHOST_MASK = 0x80FFFFFF;

    private NekoPocketPanel owner;
    /** 本格在 {@code Kind.ESSENCE} 索引空间里的槽号（= 格位，0…{@code ESSENCE_DISPLAY_GRID}−1）。 */
    private int cellIndex = -1;
    /**
     * 本格归属的 aspect tag（★<b>可变</b>：R78③ 后由服务端的格位归属表算出并随同步 blob 落到这里，
     * 不再是装配期一次定死的 {@code aspectOrder()[index]}；{@code null} = 该格从未被占过）。
     */
    private String tag;
    /** 本格当前的点数（★内容层的开关依据，只由 {@link #setCellContent(String, int)} 写入）。 */
    private int stock;
    /**
     * 数量浮层的当前文本（由 {@link ContentSink#showAmount(String)} 写；★空态是空串，
     * 与图标的撤除出自同一条 {@link #applyContentLayer} ⇒ 不会出现"撤了图标却留着旧数字"）。
     */
    private String amountText = "";
    private boolean ghost;
    /**
     * ★R83 C2：本格声明的组上限<b>原始值</b>（{@link PocketConstants#FILTER_CAP_UNSET} = 没人滚过轮）。
     * 权威值在服务端，经 ghost blob 落回本字段；本字段只用于显示与"下一次步进从哪走"。
     */
    private int declaredCap = PocketConstants.FILTER_CAP_UNSET;

    public NekoEssenceGhostCell() {
        super();
        // ghost 声明与内容层都随时可增删 ⇒ tooltip 每次重画都重建（否则解绑后"右键取消"那行会留在屏上）。
        // ★R83 B2：这一句过去是<b>空转</b> —— {@code RichTooltip.buildTooltip()} 只在
        // {@code tooltipBuilder != null} 时才重建文本，而装配侧当年走的是 {@code tooltip(Consumer)}
        // （一次性通道），于是 {@code markTooltipDirty()} 只翻了脏位、行集永远不变。
        // 现在装配侧改走 {@code tooltipDynamic}（{@code NekoPocketEssenceColumn#essenceCell}），
        // 本 flag 与下面两处 {@code markTooltipDirty()} 才真的有读者，二者必须成对存在。
        tooltip().setAutoUpdate(true);
    }

    /**
     * 装配期绑定「哪一格属于哪个会话」（双端各一次，格数与格序恒定 ⇒ 不引入数据驱动的
     * widget 树变化，与 {@link NekoFilterSlot#bindGhost} 同形）。归属 tag 与点数不在这一定死，
     * 由 {@link #setCellContent(String, int)} 原位写入。
     */
    NekoEssenceGhostCell bindCell(NekoPocketPanel panel, int index, String aspectTag) {
        this.owner = panel;
        this.cellIndex = index;
        return setCellContent(aspectTag, 0);
    }

    /**
     * 内容层的<b>绘制目标</b>（★薄到能被零依赖套件用记录型实现驱动 ⇒ 用例跑的就是<b>生产那条分支</b>，
     * 不是把判据抄第二遍）。
     * <p>
     * 为什么要有这一层：本 JVM 里构造任何 MUI2 widget 都会抛
     * {@code NoClassDefFoundError: it/unimi/dsi/fastutil/objects/Object2ObjectOpenHashMap}
     * （实测，与 {@link PocketGuiTextures} 的类注释同一条限制）⇒ "库存 0 时到底撤没撤图标"
     * 这件事没法用真 widget 断言。把"撤/画"的选择收进 {@link #applyContentLayer} 这一条静态序列，
     * 就能在纯 JVM 里钉住它；widget 侧（{@link #setCellContent}）只剩把三个动作翻译成
     * {@code overlay(...)} / {@code overlay()} / 文本，那一段属实机项。
     */
    public interface ContentSink {

        /** 画该 tag 的图标（生产实现 = {@code overlay(icon)}）。 */
        void showIcon(String aspectTag);

        /** ★撤图标（生产实现 = {@code overlay()}，MUI2 的 {@code IDrawable.of(无元素)} 返回 null）。 */
        void clearIcon();

        /** 数量文本（★空态是空串，不是"不调用"——漏调就是把旧数字留在屏上）。 */
        void showAmount(String text);
    }

    /**
     * ★R78 D-1 的单点：内容层到底画什么（图标 + 数量文本，或两者都撤）。
     * <p>
     * 判据是 {@link #drawsContentLayer}，两条出口必须<b>成对</b>：空态同时撤图标与文本，
     * 有货同时给图标与文本。回归用例
     * {@code essence_cell_content_layer_follows_stock} 驱动的就是本方法。
     */
    public static void applyContentLayer(String aspectTag, int points, ContentSink sink) {
        if (sink == null) {
            return;
        }
        if (drawsContentLayer(aspectTag, points)) {
            sink.showIcon(aspectTag);
            sink.showAmount(String.valueOf(points));
        } else {
            sink.clearIcon();
            sink.showAmount("");
        }
    }

    /**
     * ★R78 D-1 的唯一写入口：按「本格归属哪个 tag + 该 tag 现在有多少点」原位刷新内容层。
     * <p>
     * 无货（或该格从未被占过）⇒ 只撤图标与数量文本，<b>槽位底与格位本体都还在</b>（R73②）。
     * 选择逻辑在 {@link #applyContentLayer}（生产与测试共用同一条）。
     *
     * @param aspectTag 本格归属的 tag（{@code null} = 空格）
     * @param points    该 tag 当前的点数
     */
    public NekoEssenceGhostCell setCellContent(String aspectTag, int points) {
        this.tag = aspectTag;
        this.stock = Math.max(0, points);
        applyContentLayer(this.tag, this.stock, new ContentSink() {

            @Override
            public void showIcon(String aspectTag) {
                final UITexture icon = UITexture.builder()
                    .location(TaumCompat.aspectTexturePath(aspectTag))
                    .fullImage()
                    .nonOpaque()
                    .build();
                final int color = TaumCompat.colorOf(aspectTag);
                // ★图标进 overlay 而不是 background：background 已被"槽位底"占用，两者叠反会让凹槽消失
                NekoEssenceGhostCell.this
                    .overlay(color == TaumCompat.COLOR_UNKNOWN ? icon : icon.withColorOverride(color));
            }

            @Override
            public void clearIcon() {
                // IDrawable.of(无元素) 返回 null ⇒ getOverlay() 变 null，内容层真的没东西可画
                NekoEssenceGhostCell.this.overlay();
            }

            @Override
            public void showAmount(String text) {
                NekoEssenceGhostCell.this.amountText = text == null ? "" : text;
            }
        });
        markTooltipDirty();
        return this;
    }

    /**
     * 内容层判据（★单点，装配、同步与回归套件读的都是这一条）：只有「本格有归属 tag」且
     * 「该 tag 有点数」才画图标与数量文本。
     * <p>
     * 单独成静态方法而不是埋在绘制里，是为了让 R78 通则"凡注释声称某状态下不绘制 X，
     * 必须有一条 JVM 用例钉住该状态下的内容层为空"真的可执行。
     */
    public static boolean drawsContentLayer(String aspectTag, int points) {
        return aspectTag != null && !aspectTag.isEmpty() && points > 0;
    }

    /**
     * ★机检点（钉 D-1，实机侧）：内容层当前是否在场 = 图标 drawable 是否在场。
     * <p>
     * 读的是 {@link #getOverlay()} 这个 widget 真值而不是自报标记；本 JVM 造不出 widget，
     * 所以零依赖套件走 {@link #applyContentLayer} 的记录型断言，本方法留给游戏内与调试树。
     */
    public boolean hasContentLayer() {
        return getOverlay() != null;
    }

    /** 本格当前的数量文本（★空态一律空串，与 {@link #hasContentLayer()} 同一开关）。 */
    public String stockText() {
        return amountText;
    }

    /** 本格归属的 tag（{@code null} = 该格从未被占过 ⇒ 无内容可画、也无东西可声明）。 */
    public String aspectTag() {
        return tag;
    }

    /** 本格当前的点数（客户端只读同步来的值）。 */
    public int essenceStock() {
        return stock;
    }

    /** 当前是否处于 ghost（已声明要拉取本格源质）态。 */
    public boolean isGhost() {
        return ghost;
    }

    /**
     * 原位切换 ghost 态（{@link NekoPocketPanel#applyGhosts()} 的唯一写入口，双端各一份）。
     * <p>
     * 不重建 widget、不改 6×12 布局、不动内容层（R41b：ghost 遮罩与"有没有货"是两件事）。
     */
    NekoEssenceGhostCell setGhost(boolean ghost) {
        if (this.ghost == ghost) {
            return this;
        }
        this.ghost = ghost;
        if (!ghost) {
            // 解绑后本格不再归属任何声明 ⇒ 上限读数必须复位，否则下一条声明沿用上一条的数字
            this.declaredCap = PocketConstants.FILTER_CAP_UNSET;
        }
        markTooltipDirty();
        return this;
    }

    /** ★R83 C2：服务端 ghost blob 落回本格组上限的唯一写入口（与另两支同名同语义）。 */
    NekoEssenceGhostCell setDeclaredCap(int cap) {
        if (this.declaredCap == cap) {
            return this;
        }
        this.declaredCap = cap;
        markTooltipDirty();
        return this;
    }

    /** 本格声明真正生效的组上限（没滚过轮 = {@code ESSENCE_CAP_PER_TAG}，与旧档逐字同行为）。 */
    public int ghostCap() {
        return PocketFilterConfig.resolveRawCap(PocketFilterConfig.Kind.ESSENCE, declaredCap, 0);
    }

    /**
     * ★R83 C2：alt+左键 = 对<b>本格已经有的那种源质</b>直接声明需求（用户原话"还可以采用对已有物品按下 alt"）。
     * <p>
     * 走的与 NEI 拖入<b>同一条</b> {@code owner.requestGhost} ⇒ 零新动作码、零新同步键；载荷键只走
     * {@link PocketFilterConfig#essenceKey(String, String)}（不自造第四种键格式），通道 id 只走
     * {@link #channelTypeId(String)}（与消费端 {@code InfinityStackTypes.byId} 同一探针）。
     * 无 tag、无存量、栏灰显（TC 缺席）、解不出通道、已经是声明格 ⇒ 一律 {@code false} 交回 {@code super}，
     * 不写一条无处可抽的声明再静默空转（{@link #ghostKeyFor} 里同一条理由）。
     */
    private boolean requestBindFromStock() {
        if (owner == null || cellIndex < 0 || ghost || !areAncestorsEnabled()) {
            return false;
        }
        if (tag == null || tag.isEmpty() || stock <= 0) {
            return false;
        }
        final String typeId = channelTypeId(tag);
        if (typeId == null || typeId.isEmpty()) {
            return false;
        }
        return owner.requestGhost(cellIndex, PocketFilterConfig.essenceKey(typeId, tag));
    }

    /**
     * ★R83 C2（判据 4）：alt+滚轮 = 调本格声明的组上限；alt+ctrl 把<b>步进</b>放大 10 倍（D-6 裁定）。
     * <p>
     * 步进表、夹取、"未设从天花板起走"全在 {@link PocketGhostRequest#nextCap} 这一条纯函数里 ⇒ 步进只有一份真值；
     * 本方法只负责"这一格是不是声明格 + 修饰键读数"。不是声明格时把事件原样交回 {@code super}
     * （{@link ButtonWidget} 没有 {@code onMouseScroll} 覆写，默认 {@code false} ⇒ 不消费也不挡下层）。
     */
    @Override
    public boolean onMouseScroll(UpOrDown scrollDirection, int amount) {
        if (!ghost || owner == null || cellIndex < 0 || !areAncestorsEnabled() || !Interactable.hasAltDown()) {
            return super.onMouseScroll(scrollDirection, amount);
        }
        final int next = PocketGhostRequest
            .nextCap(PocketFilterConfig.Kind.ESSENCE, declaredCap, 0, scrollDirection, Interactable.hasControlDown());
        if (!owner.requestGhost(cellIndex, PocketGhostRequest.capDirective(PocketFilterConfig.Kind.ESSENCE, next))) {
            return false;
        }
        // ★本地即时回显：不回显的话"滚了数字不动"会被读成功能没生效；回显值与服务端算的是同一条 nextCap
        setDeclaredCap(next);
        return true;
    }

    // ------------------------------------------------------------------ NEI 拖入 / 右键解绑

    /**
     * ★拖入入口：<b>只发请求</b>，本地不落档（R18/R19）；不收的一切情形都不吃栈。
     * <p>
     * 分发条件是库内的「hover + {@code instanceof RecipeViewerGhostIngredientSlot}」，
     * 与本列调过 {@code excludeAreaInRecipeViewer()} 无关（R70 实测）⇒ 与中栏物品拖入同一机制。
     */
    @Override
    public boolean handleDragAndDrop(ItemStack draggedStack, int button) {
        if (owner == null || draggedStack == null || cellIndex < 0) {
            return false;
        }
        final String typeId = channelTypeId(tag);
        final String key = ghostKeyFor(
            button,
            areAncestorsEnabled(),
            carriesTag(draggedStack, tag, EssenceGate.TAUM),
            tag,
            typeId);
        if (key.isEmpty()) {
            return false;
        }
        draggedStack.stackSize = 0;
        return owner.requestGhost(cellIndex, key);
    }

    /**
     * ghost 态下的右键 = 解绑（★只发 {@code CLR|<格号>|E}，判定与执行在服务端）。
     * <p>
     * 其余按键（含非 ghost 态的右键）一律交回 {@code super} ⇒ 既有的「点击取晶 / Shift 取一整堆」
     * 行为逐字不变。
     */
    @Override
    public Result onMousePressed(int mouseButton) {
        if (ghost && mouseButton == 1 && owner != null && cellIndex >= 0) {
            owner.requestGhostClear(PocketFilterConfig.Kind.ESSENCE, cellIndex);
            return Result.SUCCESS;
        }
        if (mouseButton == 0 && ghost && Interactable.hasAltDown()) {
            // 已经是声明格的格子上没有"要绑的东西"，但也不能让原版把它读成正常路径 ⇒ 明确停住
            return Result.SUCCESS;
        }
        if (mouseButton == 0 && Interactable.hasAltDown() && requestBindFromStock()) {
            return Result.SUCCESS;
        }
        return super.onMousePressed(mouseButton);
    }

    // ------------------------------------------------------------------ 纯判定（回归套件驱动这两段）

    /**
     * 拖入物是否<b>含本格的 tag</b>：蒸馏产出（{@code TaumCompat.distill} 口径）或容器内容
     * （{@code TaumCompat.readContainer} 口径）任一命中即算含。
     * <p>
     * ★走 {@link EssenceGate} 而不是直调 {@link TaumCompat}：TC 缺席时三条读数<b>恒</b>为空，
     * 拿生产实现跑零依赖测试只会得到"永远不匹配"的假绿（同 R59b 偏离①、
     * {@code PocketSlots#classifyIncoming(ItemStack, EssenceGate)} 的既有口径）。
     * 本仓不引第二个探针接口，故复用 {@link EssenceGate}。
     */
    public static boolean carriesTag(ItemStack draggedStack, String cellTag, EssenceGate gate) {
        if (draggedStack == null || gate == null || cellTag == null || cellTag.isEmpty()) {
            return false;
        }
        final TaumAspectAmounts distilled = gate.aspectsOf(draggedStack);
        if (distilled != null && distilled.getAmount(cellTag) > 0) {
            return true;
        }
        final TaumAspectAmounts container = gate.readContainer(draggedStack);
        return container != null && container.getAmount(cellTag) > 0;
    }

    /**
     * 拖入是否构成一条源质声明：左键 + 该栏可用（灰显一律不收，R31）+ 本格有 tag +
     * 拖入物确实含该 tag + 解出了通道 id ⇒ 载荷键 {@code e:<typeId>:<tag>}；否则 {@code ""}。
     * <p>
     * 键的生成只走 {@link PocketFilterConfig#essenceKey(String, String)}（<b>不自造第四种键格式</b>）。
     */
    public static String ghostKeyFor(int button, boolean regionEnabled, boolean tagMatched, String cellTag,
        String typeId) {
        if (button != 0 || !regionEnabled || !tagMatched || cellTag == null || cellTag.isEmpty()) {
            return "";
        }
        if (typeId == null || typeId.isEmpty()) {
            // 没有任何已注册的第三方通道能吃下这一 tag ⇒ 声明无处可抽，不收（不写死声明再静默空转）
            return "";
        }
        return PocketFilterConfig.essenceKey(typeId, cellTag);
    }

    /**
     * 本格源质所在的<b>通道 id</b>（{@code IAEStackType.getId()} 字符串）。
     * <p>
     * ★<b>与消费端口径逐字对齐</b>，不猜：消费点是
     * {@code PocketAeChannelOps#extractEssence} 的 {@code InfinityStackTypes.byId(filter.typeId)}
     * （{@code PocketAeChannelOps.java:423}），而它判定"该通道能不能物化成晶化源质"用的探针是
     * {@code type.convertStackFromItem(TaumCompat.newCrystalStack(tag, 1))} 且要求
     * {@code getStackSize() > 0}（{@code PocketAeChannelOps.java:438-443}）。本方法用<b>同一条探针</b>
     * 在{@code InfinityStackTypes.allSupportedTypes()}（物品→流体→运行时注册的第三方通道）上取第一个
     * 命中者的 {@code getId()} ⇒ 写进声明的 id 与 {@code byId} 能解析出的 id 天然是同一个，
     * 不会出现两处真相。物品/流体两个内建通道<b>排除</b>在外（源质不可能落在那两条上）。
     * <p>
     * 边界如实声明：AE2 第三方通道的 {@code convertStackFromItem} 需要真实注册表，纯 JVM 里
     * 拿不到（与 {@code extract_essence_branch_yields_crystal} 同一批未验面，实验 E3），
     * 因此本方法属<b>实机项</b>；判据（含不含 tag、要不要发 SET、落到哪个索引空间）全在
     * {@link #carriesTag} 与 {@link #ghostKeyFor} 这两段纯函数里，由回归套件驱动。
     */
    static String channelTypeId(String cellTag) {
        if (cellTag == null || cellTag.isEmpty()) {
            return "";
        }
        final ItemStack probeStack = TaumCompat.newCrystalStack(cellTag, 1);
        if (probeStack == null) {
            return "";
        }
        for (IAEStackType<?> type : InfinityStackTypes.allSupportedTypes()) {
            if (type == null || type == InfinityStackTypes.ITEM_STACK_TYPE
                || type == InfinityStackTypes.FLUID_STACK_TYPE) {
                continue;
            }
            final IAEStack<?> converted;
            try {
                converted = type.convertStackFromItem(probeStack);
            } catch (Throwable t) {
                // 第三方通道的实现不得成为 GUI 崩溃源；问下一家
                continue;
            }
            if (converted != null && converted.getStackSize() > 0L) {
                return type.getId();
            }
        }
        return "";
    }

    // ------------------------------------------------------------------ ghost 态的渲染

    /** 声明态叠一层与中栏、流体条同色的弱遮罩（4×12 几何与着色/数量浮层一个字都不动）。 */
    @Override
    public void drawOverlay(ModularGuiContext context, WidgetThemeEntry<?> widgetTheme) {
        super.drawOverlay(context, widgetTheme);
        if (ghost) {
            GuiDraw.drawRect(1, 1, getArea().w() - 2, getArea().h() - 2, GHOST_MASK);
            drawCapReadout();
        }
    }

    /**
     * ★R83 C2（判据 7）：声明格右上角的橙色组上限读数。
     * <p>
     * 色、缩放、右对齐算式与缩写全部取自 {@link PocketGhostRequest}（三类共用一份），本方法只负责"画"。
     */
    private void drawCapReadout() {
        final String text = ghost ? PocketGhostRequest.capReadout(ghostCap()) : "";
        if (text.isEmpty()) {
            return;
        }
        final float x = PocketGhostRequest.capReadoutX(getArea().w(), PocketGhostRequest.capReadoutWidth(text));
        // ★shadow=true：遮罩是接近白的浅色，不带阴影的橙字压上去就是糊成一片
        GuiDraw.drawText(
            text,
            x,
            PocketGhostRequest.CAP_READOUT_TOP,
            PocketGhostRequest.CAP_READOUT_SCALE,
            PocketGhostRequest.capReadoutColor(),
            true);
    }
}

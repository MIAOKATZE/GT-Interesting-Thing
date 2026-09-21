package com.miaokatze.gtit.gui.pocket;

import net.minecraft.item.ItemStack;

import com.cleanroommc.modularui.drawable.GuiDraw;
import com.cleanroommc.modularui.integration.recipeviewer.RecipeViewerGhostIngredientSlot;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.miaokatze.gtit.common.items.infinitycell.InfinityStackTypes;
import com.miaokatze.gtit.common.items.pocket.PocketFilterConfig;
import com.miaokatze.gtit.common.items.pocket.distill.EssenceGate;
import com.miaokatze.gtit.crossmod.taum.TaumAspectAmounts;
import com.miaokatze.gtit.crossmod.taum.TaumCompat;

import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IAEStackType;

/**
 * 右栏 48 格源质盘的单格（需求 4 的源质入口）：显示格 + 可被 NEI 拖入的 ghost 声明位。
 * <p>
 * <b>为什么必须换类而不是加东西</b>（R70）：原来这 48 格是裸 {@link ButtonWidget}，
 * 它不实现 {@link RecipeViewerGhostIngredientSlot} ⇒ 面板的拖入分发（按 hover 列表 +
 * {@code instanceof} 判定）根本看不到它 ⇒ 「源质格拖入」在游戏内零入口。本类
 * {@code extends ButtonWidget<NekoEssenceGhostCell>} <b>并</b>实现该接口：
 * 既有的点击取晶、着色、数量浮层、tooltip 与 4×12 布局<b>一格不加不减</b>（R41b/R33）。
 * <p>
 * <b>声明语义 = 「确认把本格对应的 tag 声明为 ghost」</b>：格位归属由
 * {@code TaumCompat.aspectOrder()[index]} 在装配期钉死，所以拖进来的东西不需要"是什么"，
 * 但<b>必须确实含本格的 tag</b>（{@link #carriesTag}：蒸馏产出或容器内容任一命中）。
 * 不含 ⇒ 返回 false，NEI 那边继续拖着、不吃栈 ⇒ 玩家拖来的任意东西不会被当成源质声明。
 * <p>
 * <b>★ghost 只原位改属性</b>（R41b）：实例从装配到关屏不换，声明态只影响遮罩与 tooltip；
 * 48 格本就是显示侧、<b>不进 Container</b>（R35），因此既不占 149 也不动槽号。
 * <p>
 * <b>TC 不在场时一律不收</b>（R31 的整栏灰显由 {@code setEnabledIf} 给出，本类用
 * {@code IWidget#areAncestorsEnabled()} 读它，与 {@link NekoFilterSlot} 同一口径）。
 */
public class NekoEssenceGhostCell extends ButtonWidget<NekoEssenceGhostCell>
    implements RecipeViewerGhostIngredientSlot<ItemStack> {

    /** 虚化遮罩色（与 {@link NekoFilterSlot}、{@link NekoPocketFluidSlot} 同一 alpha 口径，R18）。 */
    private static final int GHOST_MASK = 0x80FFFFFF;

    private NekoPocketPanel owner;
    /** 本格在 {@code Kind.ESSENCE} 索引空间里的槽号（= 装配序，0…47）。 */
    private int cellIndex = -1;
    /** 本格归属的 aspect tag（派生表短于 48 时可为 {@code null} ⇒ 无内容可声明）。 */
    private String tag;
    private boolean ghost;

    public NekoEssenceGhostCell() {
        super();
        // ghost 声明随时可增删 ⇒ tooltip 每次重画都重建（否则解绑后"右键取消"那行会留在屏上）
        tooltip().setAutoUpdate(true);
    }

    /**
     * 装配期绑定「哪一格属于哪个会话、归属哪个 tag」（双端各一次，格序与长度恒定 ⇒ 不引入
     * 数据驱动的 widget 树变化，与 {@link NekoFilterSlot#bindGhost} 同形）。
     */
    NekoEssenceGhostCell bindCell(NekoPocketPanel panel, int index, String aspectTag) {
        this.owner = panel;
        this.cellIndex = index;
        this.tag = aspectTag;
        return this;
    }

    /** 本格归属的 tag（{@code null} = 派生表里没有这一格）。 */
    public String aspectTag() {
        return tag;
    }

    /** 当前是否处于 ghost（已声明要拉取本格源质）态。 */
    public boolean isGhost() {
        return ghost;
    }

    /**
     * 原位切换 ghost 态（{@link NekoPocketPanel#applyGhosts()} 的唯一写入口，双端各一份）。
     * <p>
     * 不重建 widget、不改 4×12 布局、不动数量浮层与着色（R41b）。
     */
    NekoEssenceGhostCell setGhost(boolean ghost) {
        if (this.ghost == ghost) {
            return this;
        }
        this.ghost = ghost;
        markTooltipDirty();
        return this;
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
        }
    }
}

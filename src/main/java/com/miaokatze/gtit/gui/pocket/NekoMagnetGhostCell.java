package com.miaokatze.gtit.gui.pocket;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import com.cleanroommc.modularui.drawable.ItemDrawable;
import com.cleanroommc.modularui.integration.recipeviewer.RecipeViewerGhostIngredientSlot;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.miaokatze.gtit.common.items.pocket.PocketConstants;
import com.miaokatze.gtit.common.items.pocket.PocketMagnetFilter;

/**
 * 磁力三态名单（★R96 S7b）72 格盘的单格<b>名单格件</b>：显示「这一格记的是哪件物品」+ 两条录入口
 * （NEI 拖入 / 游标持物点格）+ 右键摘除本格。
 * <p>
 * <b>★为什么照 {@link NekoEssenceGhostCell} 那一支而不是照 {@code NekoFilterSlot}</b>：
 * <ul>
 * <li>{@code NekoFilterSlot} 是<b>真实槽</b>的双态（它挂在 {@code SlotGroupWidget} 上、占 225 那条槽账，
 * 且 {@code owner} 是具体类 {@code NekoPocketPanel} 的既存形状）⇒ 拿它造 72 格会把守恒 225 撑开，
 * 正是本片明令禁止的两条之一；</li>
 * <li>{@link NekoEssenceGhostCell} 的形状恰好是本题要的：{@code extends ButtonWidget} <b>并</b>实现
 * {@link RecipeViewerGhostIngredientSlot} ⇒ 面板的 NEI 拖入分发（库侧 {@code ModularPanel} 按 hover 列表 +
 * {@code instanceof} 派发，★2.3.91 的行号已从 wiki 记的位置漂移）看得见它；★显示侧<b>不进 Container</b>
 * ⇒ 与 225 零冲突。本类一格不加不减地复用那一支的<b>形状</b>，但<b>语义完全不同</b>（下面两段）。</li>
 * </ul>
 * <p>
 * <b>★★语义差别 1：拖入「不吃不放、只记录」</b>（本片验收 2 的正身）。源质那一支声明 ghost 时做的是
 * {@code draggedStack.stackSize = 0;}（{@link NekoEssenceGhostCell#handleDragAndDrop}）——它<b>吃</b>栈；
 * 磁力名单只是"这条身份以后别吸 / 只吸"，玩家拖来的东西一件都不该少。⇒ 本类的
 * {@link #applyDrop} 与 {@link #handleDragAndDrop} <b>全程不写</b> {@code draggedStack} 的任何字段，
 * 只把 {@link PocketMagnetFilter#itemKey(int, int)} 那枚身份键交给服务端写口。
 * 这条差别由用例 {@code magnet_drag_records_without_consuming} 用<b>真对象</b>钉（★不是文本门）：
 * 同一条生产分支 {@code applyDrop} 跑完，栈的 {@code stackSize} 必须一字不变。
 * <p>
 * <b>★★语义差别 2：游标持物点格「不清游标」</b>（与先例<b>相反</b>，这是有意的）。
 * {@code NekoPocketPanel#dispatchEssenceCellPress}（R87-d/R90 E3 那一族）走的入槽会把游标那件<b>溶掉</b>
 * （服务端 {@code syncManager.setCursorItem(null)}），因为源质入槽是<b>消耗</b>动作。
 * 磁力名单的"持物点格 = 把这件记进名单"<b>不是</b>消耗动作 ⇒ 玩家可以拿着一叠样本逐个点过去，
 * 把一整套矿记进黑名单而一件都不掉。★本类的游标支（{@link #applyCursorPress}）与
 * {@code NekoPocketPanel#requestMagnetEntryAdd} 一条 {@code setCursorItem} 都不写；
 * 用例除了正向断言"跑完 {@code carried.stackSize} 不变"，还带一条<b>反向</b>机检：磁力那几条腿里
 * {@code setCursorItem} 命中数恰 0，而同一条检法在源质入槽那条腿上必须读到 ≥1（否则那个 0 是空转）。
 * <p>
 * <b>★R101 收口：拒收不再自带说法</b>（对 R100 那代"每个拒收分支一条 tooltip 键"设计的显式翻案，
 * 任务拍板"磁力逐条描述收掉、tooltip 仅保留物品显示名"）：{@link DragResult} 只报"成没成"
 * （{@code accepted()}），四条拒收支（无栈 / 非左键 / 本栏不可编辑 / 名单已满）的 lang 键族随
 * 装配侧的格件 tooltip 一起整批退场。拒收的手感 = 点击无事发生（灰显面仍有 R31 的形状语义）。
 * <p>
 * <b>★纯判定与绘制的分层</b>（同 {@link NekoEssenceGhostCell} 的 {@code applyContentLayer} 纪律）：
 * 本 JVM 里构造任何 MUI2 widget 都会抛 fastutil 缺类（{@link PocketGuiTextures} 的类注释记过这条实测），
 * 所以"到底吃不吃栈、拒收给不给说法"收进 {@link #applyDrop} / {@link #applyCursorPress} /
 * {@link #applyEntryLayer} 三条静态序列 ⇒ 用例跑的就是生产那条分支；widget 侧只剩把
 * {@code recordEntry} 翻译成一次 C2S 请求、把 {@code showEntry} 翻译成一次 {@code overlay(...)}，
 * 那两段属实机项。
 */
public class NekoMagnetGhostCell extends ButtonWidget<NekoMagnetGhostCell>
    implements RecipeViewerGhostIngredientSlot<ItemStack> {

    /** 左键号（★只有左键录条目；与 {@code NekoEssenceGhostCell} 同一件 LWJGL2 固定事实，刻意各写一份）。 */
    private static final int MOUSE_BUTTON_LEFT = 0;
    /** 右键号（★只有右键摘本格）。 */
    private static final int MOUSE_BUTTON_RIGHT = 1;
    /** 中键号：★本栏不做任何事，但必须把它"吃掉"（{@code ButtonWidget} 的默认谓词只认 0/1）。 */
    private static final int MOUSE_BUTTON_MIDDLE = 2;

    private NekoPocketPanel owner;
    /** 本格在名单里的格号（0…{@code MAGNET_FILTER_SLOTS}−1；★未绑定 = −1）。 */
    private int cellIndex = -1;
    /**
     * 本格当前的身份键（★{@code null}/空 = 空格）。数据源 = {@code SYNC_MAGNET} 那枚
     * {@link com.cleanroommc.modularui.value.sync.StringSyncValue} 落进面板镜像的<b>插入序</b>第
     * {@code cellIndex} 条（★排序是服务端算的，客户端不重排 —— R32 双端同序那条）。
     */
    private String entryKey;

    public NekoMagnetGhostCell() {
        super();
        // tooltip 每帧重建（★必须与装配侧的 tooltipDynamic 成对存在，见 NekoEssenceGhostCell 里 R83 B2 那段：
        // 只设 flag 不走 dynamic 通道 = 空转；只走 dynamic 不设 flag = 一个字都不画）
        tooltip().setAutoUpdate(true);
    }

    /**
     * 装配期绑定「哪一格属于哪个会话」，★格数与格序恒定 ⇒ 不因数据变化增删 widget（R32/R41b）。
     * 归属键不在这一定死，由 {@link #setEntryKey(String)} 原位写入。
     */
    NekoMagnetGhostCell bindCell(NekoPocketPanel panel, int index) {
        this.owner = panel;
        this.cellIndex = index;
        return setEntryKey(index < 0 ? null : panel.magnetEntryKeyAt(index));
    }

    // ------------------------------------------------------------------ 内容层（★与源质格同一分层：判据在静态方法，widget 只翻译）

    /** {@link #applyEntryLayer} 的绘制目标（薄到能被记录型实现驱动 ⇒ 用例跑生产那条分支）。 */
    public interface EntrySink {

        /** 画该身份键对应的物品图标（生产实现 = {@code overlay(new ItemDrawable(item, meta))}）。 */
        void showEntry(String key);

        /** ★撤图标（生产实现 = {@code overlay()}；MUI2 的 {@code IDrawable.of(无元素)} 返回 {@code null}）。 */
        void clearEntry();
    }

    /** 内容层判据（单点）：本格有身份键才画东西；空格只留槽位底与格位本体（同 R73② 的口径）。 */
    public static boolean drawsEntryLayer(String key) {
        return key != null && !key.isEmpty();
    }

    /** ★R78 D-1 同一条纪律：唯一写入口 + 选择逻辑单源。 */
    public static void applyEntryLayer(String key, EntrySink sink) {
        if (sink == null) {
            return;
        }
        if (drawsEntryLayer(key)) {
            sink.showEntry(key);
        } else {
            sink.clearEntry();
        }
    }

    /**
     * 唯一的内容层写入口：原位换图标（★不重建 widget、不改 12×6 布局）。
     * <p>
     * ★键的解析（{@code itemId + meta} → {@link Item}）走 {@link #itemOf(String)}，解不出物品
     * （外来档 / 注册表变更）⇒ 只撤图标，tooltip 那一支仍然把原始键念出来（不静默）。
     */
    public NekoMagnetGhostCell setEntryKey(String key) {
        this.entryKey = key;
        applyEntryLayer(key, new EntrySink() {

            @Override
            public void showEntry(String entryKey) {
                final Item item = itemOf(entryKey);
                if (item == null) {
                    // ★解不出物品：不画图标（画不出东西的 overlay 会让 MUI2 去取一个 null 的 drawable），
                    // 但键本身留在 tooltip（NekoMagnetGhostCell 的 tooltip 那一条念 raw key）
                    NekoMagnetGhostCell.this.overlay();
                    return;
                }
                NekoMagnetGhostCell.this.overlay(new ItemDrawable(item, metaOf(entryKey)));
            }

            @Override
            public void clearEntry() {
                NekoMagnetGhostCell.this.overlay();
            }
        });
        markTooltipDirty();
        return this;
    }

    /** 本格当前的身份键（{@code null}/空 = 空格）。 */
    public String entryKey() {
        return entryKey;
    }

    /** ★机检点（实机侧）：内容层当前是否在场 = overlay drawable 真值（与源质格同一条读法）。 */
    public boolean hasEntryLayer() {
        return getOverlay() != null;
    }

    /** 本格在盘上的格号（★内部 0 起索引；换算成玩家可见的 1 起口径只在 {@code PocketCellIdentity} 那一处）。 */
    public int cellIndex() {
        return cellIndex;
    }

    // ------------------------------------------------------------------ 键 → 物品（★只在绘制路径上被问；判定腿不碰注册表）

    /** 身份键里的 {@code itemId}（解不出 ⇒ −1，★调用方据此不画东西）。 */
    public static int itemIdOf(String key) {
        final long[] parsed = PocketMagnetFilter.parseEntry(key);
        return parsed == null ? -1 : (int) parsed[0];
    }

    /** 身份键里的 {@code meta}（解不出 ⇒ 0）。 */
    public static int metaOf(String key) {
        final long[] parsed = PocketMagnetFilter.parseEntry(key);
        return parsed == null ? 0 : (int) parsed[1];
    }

    /**
     * 键 → 物品（★只服务图标绘制：{@code Item.getItemById} 需要真注册表，纯 JVM 里对未注册的假物品
     * 拿到的是构造期登记的那份或 {@code null}）。解不出 ⇒ {@code null} ⇒ 本格只念键、不画图标。
     */
    public static Item itemOf(String key) {
        final int id = itemIdOf(key);
        return id < 0 ? null : Item.getItemById(id);
    }

    /**
     * 栈 → 身份键（★只记录身份，★一件都不动）：入 {@code PocketMagnetFilter.itemKey} 的那枚
     * {@code i:itemId:meta:}。空栈 / 无物品 ⇒ 空串（调用方走拒收支）。
     */
    public static String identityKeyOf(ItemStack stack) {
        if (stack == null || stack.getItem() == null) {
            return "";
        }
        return PocketMagnetFilter.itemKey(Item.getIdFromItem(stack.getItem()), stack.getItemDamage());
    }

    // ------------------------------------------------------------------ 两条录入腿（★生产分支，用例真跑这两条）

    /** 一次录入尝试的结论：成交（{@link #entryKey} 非空）或静默拒收（★R101 收口：不给说法）。 */
    public static final class DragResult {

        /** 要写进名单的键（★空串 = 一条都没写）。 */
        public final String entryKey;
        /** 名单已满时还差几条（给读数用；★不是字面量 72 抄第二遍）。 */
        public final int capacity;

        DragResult(String entryKey, int capacity) {
            this.entryKey = entryKey == null ? "" : entryKey;
            this.capacity = capacity;
        }

        public boolean accepted() {
            return !this.entryKey.isEmpty();
        }
    }

    /** 录入腿的落地口（★只有"记录"这一个动作 —— 没有"吃栈"、没有"清游标"，那两件事结构性不在本接口上）。 */
    public interface RecordSink {

        /** 发一条 C2S 写请求（生产实现 = {@code owner.requestMagnetEntryAdd(key)}；本地零写入）。 */
        void recordEntry(String key);
    }

    /**
     * ★★<b>拖入腿的本体（NEI 与游标两条手势共用的那一条判定序列）</b>：
     * 顺序 = ① 本栏可编辑？ → ② 按键是左键？ → ③ 手里真有东西？ → ④ 名单没满？ → ⑤ 成交。
     * <p>
     * ★④ 排在 ⑤ 之前是判据的一部分：满了就静默拒收、★不发那条注定被服务端拒的请求（服务端
     * {@code PocketMagnetFilter#addEntry} 自己也再挡一次 ⇒ 两处都挡：客户端这一挡省一次无谓 C2S，
     * 服务端那一挡是"守数据"，两份职责不同，★不是同一处真相抄两遍）。
     * <p>
     * ★★★<b>本方法任何一支都不写 {@code carried.stackSize}</b> —— 那一句写在
     * {@link NekoEssenceGhostCell#handleDragAndDrop} 里是源质声明的"吃栈"语义，
     * 在磁力名单这里是数据破坏。用例 {@code magnet_drag_records_without_consuming} 就是拿真对象
     * 跑本方法前后各读一次件数。
     *
     * @param carried  拖来的 / 游标上拿的那件（★只读）
     * @param button   按键号（NEI 拖入的 button 与鼠标按键号同源）
     * @param editable 本栏是否可编辑（磁力未固化 ⇒ 灰显 ⇒ 一律不收）
     * @param entries  名单当前条数（客户端镜像读数）
     */
    public static DragResult applyDrop(ItemStack carried, int button, boolean editable, int entries, RecordSink sink) {
        final int capacity = PocketConstants.MAGNET_FILTER_SLOTS;
        if (!editable) {
            return new DragResult("", capacity);
        }
        if (button != MOUSE_BUTTON_LEFT) {
            return new DragResult("", capacity);
        }
        final String key = identityKeyOf(carried);
        if (key.isEmpty()) {
            return new DragResult("", capacity);
        }
        if (carried != null && carried.stackSize <= 0) {
            // ★件数为 0 的那件不是"手里的东西"（外来入参）：不记、更不写回件数
            return new DragResult("", capacity);
        }
        if (entries >= capacity) {
            return new DragResult("", capacity);
        }
        if (sink != null) {
            sink.recordEntry(key);
        }
        return new DragResult(key, capacity);
    }

    /**
     * ★<b>游标持物点格腿</b>：与 {@link #applyDrop} 同一条判定序列（★不留第二份读法），差别只在
     * 手势入口与"手里的东西 = 游标栈"这一条。★本方法与它的调用点<b>都不清游标</b>（类注释★★段），
     * 也★不吃 shift / 批量 —— 名单的一条记录就是"这一件"，没有第二种数量读法。
     */
    public static DragResult applyCursorPress(ItemStack carried, boolean editable, int entries, RecordSink sink) {
        return applyDrop(carried, MOUSE_BUTTON_LEFT, editable, entries, sink);
    }

    // ------------------------------------------------------------------ 手势入口（widget 侧，实机项）

    /** 本栏是否可编辑（★磁力未固化 ⇒ 装配侧 {@code setEnabledIf} 灰显 ⇒ 这里读祖先链，与三条 ghost 格同口径）。 */
    private boolean editable() {
        return owner != null && cellIndex >= 0 && areAncestorsEnabled();
    }

    /**
     * ★名单格的按键矩阵：<b>左键</b> = 游标持物则记本件（★不吃游标）／空游标则不做动作（点击仍被吞掉，
     * 不给它第二种解释）；<b>右键</b> = 摘掉本格，空格 ⇒ 不发那条注定被拒的请求，说法在 tooltip 那一行
     * （装配侧 {@code PocketConfigPanel#magnetCell} 对空格恒写"本格是空格，右键无条目可摘"）；
     * <b>其余按键</b>（含中键）= ★吃掉但不做动作（{@code ButtonWidget} 的默认谓词只认 0/1，
     * 不早退就是"事件被 ACCEPT 穿透、无事发生"那一族手感；先例见
     * {@code NekoPocketPanel#dispatchBindButtonClick} 的同一注释）。
     */
    @Override
    public Result onMousePressed(int mouseButton) {
        if (mouseButton == MOUSE_BUTTON_LEFT) {
            final ItemStack carried = owner == null ? null : owner.magnetCursorStack();
            if (carried != null && carried.stackSize > 0) {
                applyCursorPress(carried, editable(), owner == null ? 0 : owner.magnetEntryCount(), new RecordSink() {

                    @Override
                    public void recordEntry(String key) {
                        owner.requestMagnetEntryAdd(key);
                    }
                });
            }
            return Result.SUCCESS;
        }
        if (mouseButton == MOUSE_BUTTON_RIGHT) {
            if (editable() && drawsEntryLayer(entryKey)) {
                owner.requestMagnetEntryRemoveAt(cellIndex);
            }
            return Result.SUCCESS;
        }
        if (mouseButton == MOUSE_BUTTON_MIDDLE) {
            return Result.SUCCESS;
        }
        return super.onMousePressed(mouseButton);
    }

    /**
     * ★NEI 拖入口（库侧按 hover + {@code instanceof RecipeViewerGhostIngredientSlot} 派发）。
     * <p>
     * 返回 {@code false} = "本格不吃这次拖拽" ⇒ NEI 那边继续拖着（源质那一支的
     * "一次机会 + 静默吞点击"坑就出在这里：成交与否必须由 {@link DragResult} 说，★不能靠返回值猜）。
     * 返回 {@code true} = 已记录（★件数一件没动）。
     */
    @Override
    public boolean handleDragAndDrop(ItemStack draggedStack, int button) {
        final DragResult result = applyDrop(
            draggedStack,
            button,
            editable(),
            owner == null ? 0 : owner.magnetEntryCount(),
            new RecordSink() {

                @Override
                public void recordEntry(String key) {
                    owner.requestMagnetEntryAdd(key);
                }
            });
        return result.accepted();
    }

    /** ★拖入是否构成"吃掉这一件"——恒 {@code false}，本方法是判据的可见面（用例钉它，装配侧不引它做分支）。 */
    public static boolean consumesDraggedStack() {
        return false;
    }
}

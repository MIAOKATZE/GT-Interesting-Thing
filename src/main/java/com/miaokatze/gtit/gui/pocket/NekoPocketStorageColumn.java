package com.miaokatze.gtit.gui.pocket;

import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.api.widget.Interactable;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.SlotGroupWidget;
import com.miaokatze.gtit.common.items.pocket.PocketConstants;

/**
 * 中栏 = 128 格存储（需求 2「一共16行*8列物品栏」）。
 * <p>
 * <b>几何（§16.1/§16.2，逐字照核算表，不得改宽）</b>：{@code x=104 宽 144 高 288}，
 * 16 行 × 8 列 ⇒ {@code y} 起点只能是 6（{@code 6+288+6=300}）⇒
 * <b>本区域内不得再塞标题行或分隔线</b>（§16.2 的硬推论；标题只能横向挤进左栏或第四列）。
 * <p>
 * <b>布局单源</b>：整块由<b>一份</b> {@link SlotGroupWidget.Builder#matrix(String...)} 字面量描述，
 * 双端读同一段常量（R41/R41a，slice-s3s4 任务包约束 6）⇒「双端 widget 树失序」（R32，本任务最高风险）
 * 从"靠纪律避免"变成<b>结构上不可能</b>：这里没有分支、没有循环里的条件装配。
 * <b>不得因 ghost 态改变本矩阵</b>（R41b/§17.2 第 3 条）——ghost 只是 {@link NekoFilterSlot} 的内部状态。
 * <p>
 * 槽号 = 行主序 {@code 0..127}，与 {@code PocketInventory.storage()} 的 handler 索引天然一致
 * （{@code Builder.build()} 按字符出现次序回调 {@code IntFunction}，见 {@code SlotGroupWidget.java:228-257}）。
 */
public final class NekoPocketStorageColumn {

    /** §16.1：中栏 x 起点。 */
    public static final int X = 104;
    /** §16.2：16 行 = 288 ⇒ y 起点只能是 6。 */
    public static final int Y = 6;
    /** §16.1：中栏宽（8 列 × 18）。 */
    public static final int WIDTH = 144;
    /** §16.2：16 行 × 18。 */
    public static final int HEIGHT = 288;
    /** 列数（与 {@code SlotGroup} 的 rowSize 同源，排序与 shift 落点都按它算）。 */
    public static final int COLUMNS = PocketSlots.STORAGE_COLUMNS;
    /** 行数（128 / 8）。 */
    public static final int ROWS = PocketInventory.STORAGE_SLOTS / COLUMNS;

    /**
     * <b>唯一的布局字面量</b>：16 条行串、每条 8 字符（R41a 的双端同树保证）。
     * 空格是唯一"留空"字符（{@code SlotGroupWidget} 内部把未绑定的 char 当占位推进坐标）。
     */
    private static final String[] STORAGE_MATRIX = { "SSSSSSSS", "SSSSSSSS", "SSSSSSSS", "SSSSSSSS", "SSSSSSSS",
        "SSSSSSSS", "SSSSSSSS", "SSSSSSSS", "SSSSSSSS", "SSSSSSSS", "SSSSSSSS", "SSSSSSSS", "SSSSSSSS", "SSSSSSSS",
        "SSSSSSSS", "SSSSSSSS" };

    private NekoPocketStorageColumn() {}

    /**
     * 装配中栏。
     *
     * @param ui 面板会话上下文（内存数组、槽工厂、同步值）
     * @return 已 {@code excludeAreaInRecipeViewer()} 的列 root（R36/§16 每列一处）
     */
    public static ParentWidget<?> build(NekoPocketPanel ui) {
        final SlotGroupWidget grid = SlotGroupWidget.builder()
            .matrix(STORAGE_MATRIX)
            .key('S', index -> {
                // statement lambda（不是链式返回）：`.slot()`/`.name()` 的静态返回类型是 ItemSlot，
                // 拿不到 NekoFilterSlot ⇒ 分三步并把 (面板, 槽号) 绑给格子自身（ghost 复合键需要槽号）
                final NekoFilterSlot widget = new NekoFilterSlot();
                widget.slot(
                    ui.slots()
                        .storage(ui.inventory(), index));
                widget.name("storage_" + index);
                widget.bindGhost(ui, index);
                ui.trackItemSlot(index, widget);
                return widget;
            })
            .synced(PocketSlots.SYNC_STORAGE)
            .slotGroup(PocketSlots.GROUP_STORAGE)
            .build();
        grid.pos(0, 0);

        final ParentWidget<?> root = new ParentWidget<>().pos(X, Y)
            .size(WIDTH, HEIGHT)
            .name("pocket_storage_column")
            .child(grid)
            // 语义②「口袋 → 玩家背包」：L2 档不显示背包 ⇒ shift-click 无落点，这是唯一替代出口
            .child(takeOutOverlay(ui));
        return root.excludeAreaInRecipeViewer();
    }

    /**
     * 覆盖整块中栏的<b>隐形满覆盖</b>转移件（范式照仓内 {@code gui/vm/IoColumnPanel.java:283-295}）。
     * <p>
     * 只在 <b>Shift + 左键</b>时消费点击并 {@code setValue(true)} 触发服务端搬运；其余情况返回
     * {@code false} ⇒ {@code Interactable.Result.IGNORE} ⇒ 底下真实的 128 格照常收到点击
     * （{@code Result} 的 {@code stops} 语义见 {@code Interactable.java:172-197}）。
     * 同步值本体与搬运逻辑在 {@link NekoPocketPanel}（服务端），本方法不含任何搬运判定。
     */
    private static IWidget takeOutOverlay(NekoPocketPanel ui) {
        return new ButtonWidget<>().pos(0, 0)
            .size(WIDTH, HEIGHT)
            .invisible()
            .playClickSound(false)
            .name("pocket_take_out_overlay")
            .onMousePressed(button -> button == 0 && Interactable.hasShiftDown() && ui.requestTakeOut());
    }

    /** ghost 可占索引白名单的上界（中栏 = 0..127，R38 第 4 条）。 */
    public static int ghostSlotLimit() {
        return PocketConstants.GHOST_ITEM_SLOT_LIMIT;
    }
}

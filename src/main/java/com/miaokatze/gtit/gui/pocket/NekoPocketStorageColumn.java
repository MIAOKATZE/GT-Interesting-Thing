package com.miaokatze.gtit.gui.pocket;

import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.api.widget.Interactable;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.SlotGroupWidget;
import com.miaokatze.gtit.common.items.pocket.PocketConstants;

/**
 * 中栏 = <b>150 格</b>存储（需求 2「一共16行*8列物品栏」经 R74②/R75 选项 A 定为 <b>15 行 × 10 列</b>）。
 * <p>
 * <b>几何（R75 钉死，逐字照加总表，不得改宽改高）</b>：面板 416×360 =
 * {@code 6 + 108(流体 6 列) + 4 + 180(中栏 10 列) + 4 + 108(源质 6 列) + 6}，
 * 故本列 {@code x = 6+108+4 = 118}、{@code 宽 = 10×18 = 180}；
 * 高 {@code = 15×18 = 270} ⇒ {@code y} 起点只能是 6（{@code 6+270+6+72+6 = 360}，
 * 360 是 1080p / GUI Scale 3 的<b>逻辑高度硬天花板</b>，R75）。
 * <p>
 * <b>本区域内不得再塞标题行或分隔线</b>（旧的 §16.2 硬推论在 15 行下同样成立：上下各只剩 6px 外边距）。
 * <p>
 * <b>布局单源</b>：整块由<b>一份</b> {@link SlotGroupWidget.Builder#matrix(String...)} 字面量描述，
 * 双端读同一段常量（R41/R41a）⇒「双端 widget 树失序」（R32，本任务最高风险）
 * 从"靠纪律避免"变成<b>结构上不可能</b>：这里没有分支、没有循环里的条件装配。
 * <b>不得因 ghost 态改变本矩阵</b>（R41b/§17.2 第 3 条）——ghost 只是 {@link NekoFilterSlot} 的内部状态。
 * <p>
 * 槽号 = 行主序 {@code 0..149}，与 {@code PocketInventory.storage()} 的 handler 索引天然一致
 * （{@code Builder.build()} 按字符出现次序回调 {@code IntFunction}，见 {@code SlotGroupWidget.java:228-257}）。
 * 行列乘积与格数的关系由 {@link PocketSlots} 的静态断言与
 * {@code NekoPocketModelTest#slot_math_175_and_row_column_products} 双向钉住。
 */
public final class NekoPocketStorageColumn {

    /** R75：中栏 x 起点 = 左外边距 + 流体块宽 + 列间距。 */
    public static final int X = NekoPocketLeftColumn.X + NekoPocketLeftColumn.WIDTH + NekoPocketPanel.COLUMN_GAP;
    /** 15 行 ⇒ y 起点只能是 6。 */
    public static final int Y = NekoPocketPanel.MARGIN;
    /** R75：中栏宽（10 列 × 18）。 */
    public static final int WIDTH = PocketSlots.STORAGE_COLUMNS * NekoPocketPanel.GRID;
    /** R75：15 行 × 18。 */
    public static final int HEIGHT = PocketSlots.STORAGE_ROWS * NekoPocketPanel.GRID;
    /** 列数（与 {@code SlotGroup} 的 rowSize 同源，排序与 shift 落点都按它算）。 */
    public static final int COLUMNS = PocketSlots.STORAGE_COLUMNS;
    /** 行数（150 / 10）。 */
    public static final int ROWS = PocketInventory.STORAGE_SLOTS / COLUMNS;

    /**
     * <b>唯一的布局字面量</b>：15 条行串、每条 10 字符（R41a 的双端同树保证）。
     * 空格是唯一"留空"字符（{@code SlotGroupWidget} 内部把未绑定的 char 当占位推进坐标）。
     * <p>
     * ★<b>行数与 {@link #ROWS} 必须一致</b>——这一眼能看出来，但"改了这里没改
     * {@code PocketConstants.GHOST_ITEM_SLOT_LIMIT}"是 R75 点名的半改形态，
     * 故由 {@link PocketSlots} 的静态乘积断言与 {@link #ROWS} 的派生式共同把守。
     */
    private static final String[] STORAGE_MATRIX = { "SSSSSSSSSS", "SSSSSSSSSS", "SSSSSSSSSS", "SSSSSSSSSS",
        "SSSSSSSSSS", "SSSSSSSSSS", "SSSSSSSSSS", "SSSSSSSSSS", "SSSSSSSSSS", "SSSSSSSSSS", "SSSSSSSSSS", "SSSSSSSSSS",
        "SSSSSSSSSS", "SSSSSSSSSS", "SSSSSSSSSS" };

    private NekoPocketStorageColumn() {}

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
        for (String row : STORAGE_MATRIX) {
            for (int index = 0; index < row.length(); index++) {
                if (row.charAt(index) == 'S') {
                    total++;
                }
            }
        }
        return total;
    }

    static {
        if (layoutSlotCount() != PocketInventory.STORAGE_SLOTS) {
            throw new IllegalStateException("[pocket] 中栏矩阵产出 " + layoutSlotCount() + " 格，与 handler 格数不符");
        }
    }

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
                widget.background(PocketGuiTextures.SLOT);
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
     * {@code false} ⇒ {@code Interactable.Result.IGNORE} ⇒ 底下真实的 150 格照常收到点击
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

    /** ghost 可占索引白名单的上界（中栏 = 0..149，R38 第 4 条 + R75）。 */
    public static int ghostSlotLimit() {
        return PocketConstants.GHOST_ITEM_SLOT_LIMIT;
    }
}

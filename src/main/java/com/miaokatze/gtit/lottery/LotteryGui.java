package com.miaokatze.gtit.lottery;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;

import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.drawable.DynamicDrawable;
import com.cleanroommc.modularui.drawable.GuiDraw;
import com.cleanroommc.modularui.drawable.ItemDrawable;
import com.cleanroommc.modularui.drawable.UITexture;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.miaokatze.gtit.client.gui.NekoGuiTextures;
import com.miaokatze.gtit.trade.NekoCurrencyRegistrar;

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;

/**
 * 抽奖轮盘 GUI（v1.7.1 目标 E）
 * <p>
 * 作为 {@code NekoVMGuiV2} 主内容 {@code PagedWidget} 的「抽奖」页嵌入（页索引 2），
 * 替换原 {@code createLotteryPagePlaceholder()} 占位。
 * 采用绝对布局（{@link ParentWidget} + pos/size），全部数据读取自客户端缓存
 * {@link LotteryClientData}（服务端通过 {@link LotterySyncPacket} / {@link LotteryResultPacket}
 * 推送刷新）——与签到页同属「S→C 全量同步 + 客户端静态缓存 + 动态绑定」范式：
 * <ul>
 * <li>顶部：卡池切换按钮（猫猫币池/闪烁池，各显示价格与团队钱包余额）</li>
 * <li>中部：4 列环形边框轮盘（格子绕圈点亮 → cubic ease-out 减速 → 停在结果格，
 * 由 {@link LotteryAnimationController} 纯客户端时间驱动）</li>
 * <li>底部：抽 1 次 / 抽 10 次按钮（余额不足显示总价+提示）、保底进度、最近中奖滚动摘要</li>
 * </ul>
 * <p>
 * <b>双端安全</b>：所有动态 Supplier 仅在客户端渲染时求值；服务端构建时读到
 * {@link LotteryClientData} 默认值（空池/0 余额），不渲染不影响服务端逻辑。
 * 抽奖判定完全由 {@link LotteryManager} 服务端权威执行，本页仅表现。
 */
public class LotteryGui {

    // ==================== 编辑模式回调（v1.7.0 目标 4） ====================

    /**
     * 抽奖页编辑模式回调接口
     * <p>
     * 由 {@code NekoVMGuiV2} 实现并传入，用于：
     * <ul>
     * <li>查询当前是否处于编辑模式（服务端权威，经同步值到客户端）</li>
     * <li>编辑模式下点击轮盘槽位 → 打开条目编辑面板</li>
     * </ul>
     * 编辑模式下抽奖按钮的抽奖交互被拦截（禁止常规操作）。
     */
    public interface LotteryEditCallback {

        /**
         * 当前是否处于编辑模式
         *
         * @return true 表示处于编辑模式
         */
        boolean isEditMode();

        /**
         * 编辑模式下点击轮盘槽位时触发
         *
         * @param pool      条目所属卡池摘要
         * @param entry     被点击的抽奖条目
         * @param slotIndex 轮盘槽位序号
         */
        void onEditEntryRequested(LotteryClientData.PoolSummary pool, LotteryEntry entry, int slotIndex);
    }

    // ==================== 布局常量 ====================

    /** 页面宽度（主内容区 = PANEL_WIDTH - 8） */
    private static final int PAGE_WIDTH = 170;
    /** 页面高度（主内容区 = PANEL_HEIGHT - 8） */
    private static final int PAGE_HEIGHT = 312;

    /** 卡池切换按钮宽 */
    private static final int POOL_BTN_W = 84;
    /** 卡池切换按钮高 */
    private static final int POOL_BTN_H = 18;
    /** 卡池切换按钮间距 */
    private static final int POOL_BTN_GAP = 2;
    /** 卡池切换按钮 Y */
    private static final int POOL_BTN_Y = 16;

    /** 轮盘槽位边长（与 slot_*.png 素材一致） */
    private static final int SLOT_SIZE = 24;
    /** 轮盘槽位间距 */
    private static final int SLOT_GAP = 4;
    /** 轮盘列数（固定 4 列环形边框布局） */
    private static final int WHEEL_COLS = 4;
    /** 轮盘最大行数（12 格 = 4列×3行） */
    private static final int WHEEL_MAX_ROWS = 3;
    /** 轮盘最大格数（环形边框路径长度 = 2×(4+3) - 4） */
    private static final int MAX_SLOTS = 2 * (WHEEL_COLS + WHEEL_MAX_ROWS) - 4;
    /** 轮盘区左上角 Y */
    private static final int WHEEL_Y = 40;
    /** 轮盘区高度（3 行 × 28 = 84） */
    private static final int WHEEL_H = WHEEL_MAX_ROWS * (SLOT_SIZE + SLOT_GAP) - SLOT_GAP;

    /** 结果提示条 Y */
    private static final int RESULT_Y = 136;
    /** 结果提示显示时长（毫秒） */
    private static final long RESULT_DISPLAY_MS = 6000L;

    /** 10 连结果列表 Y */
    private static final int LIST_Y = 152;
    /** 10 连结果列表单格边长 */
    private static final int LIST_CELL = 18;
    /** 10 连结果列表间距 */
    private static final int LIST_GAP = 2;
    /** 10 连结果列表高度（两行，每格 18 + 间距 2） */
    private static final int LIST_H = 2 * LIST_CELL + LIST_GAP;

    /** 保底进度文本 Y */
    private static final int PITY_Y = 200;
    /** 抽奖按钮 Y */
    private static final int DRAW_BTN_Y = 214;
    /** 抽奖按钮宽（与 btn_draw*.png 素材一致） */
    private static final int DRAW_BTN_W = 64;
    /** 抽奖按钮高 */
    private static final int DRAW_BTN_H = 24;
    /** 抽奖按钮间距（两按钮总宽 132，居中后左钮 X=19） */
    private static final int DRAW_BTN_GAP = 4;

    /** 最近中奖摘要 Y */
    private static final int HISTORY_Y = 246;
    /** 最近中奖摘要条数 */
    private static final int HISTORY_LINES = 3;
    /** 摘要行高 */
    private static final int HISTORY_LINE_H = 10;

    // ==================== 颜色常量（ARGB） ====================

    /** 点亮格高亮框色（金色，叠加在槽位底上） */
    private static final int COLOR_LIT_FRAME = 0xCCFFC84A;
    /** 停格后高亮框色（不透明金，闪烁相位切换） */
    private static final int COLOR_FINISH_FRAME = 0xFFFFD24A;
    /** 保底进度条底色 */
    private static final int COLOR_PITY_BG = 0xFF1E1E2E;
    /** 保底进度条填充（紫，呼应 EPIC 保底稀有度） */
    private static final int COLOR_PITY_FILL = 0xFFAA66FF;

    /** 历史时间戳格式（HH:mm） */
    private static final SimpleDateFormat TIME_FORMAT = new SimpleDateFormat("HH:mm", Locale.ROOT);

    private LotteryGui() {}

    // ==================== 页面构建入口 ====================

    /**
     * 构建抽奖页（供 {@code NekoVMGuiV2} 主内容 PagedWidget 添加为页 2）
     *
     * @param machine      触发 GUI 的猫猫售货机 V2 机器（出货槽定位，抽奖请求包携带其坐标）
     * @param editCallback 编辑模式回调（v1.7.0 目标 4）；null 表示不支持编辑模式
     * @return 抽奖页根 Widget（170x312，绝对布局）
     */
    public static IWidget createLotteryPage(IGregTechTileEntity machine, LotteryEditCallback editCallback) {
        ParentWidget<?> page = new ParentWidget<>().size(PAGE_WIDTH, PAGE_HEIGHT);

        page.child(createTitle(editCallback)); // 标题（编辑模式附加标识）
        page.child(createPoolSelector()); // 卡池切换（含价格+余额）
        page.child(createWheelArea(editCallback)); // 轮盘区（环形槽位 + 物品 + 角标 + 点亮框 + 指针；编辑模式可点击）
        page.child(createResultMessage()); // 单抽结果 / 错误提示（限时）
        page.child(createMultiResultList()); // 10 连结果格子列表
        page.child(createPityProgress()); // 保底进度（文本 + 进度条）
        page.child(createDrawButtons(machine, editCallback)); // 抽 1 次 / 抽 10 次（编辑模式拦截）
        page.child(createHistoryDisplay()); // 最近中奖滚动摘要

        return page;
    }

    // ==================== 标题与卡池切换 ====================

    /** 标题行：「猫猫扭蛋」（金色，居中；编辑模式附加红色标识） */
    private static IWidget createTitle(LotteryEditCallback editCallback) {
        return new TextWidget<>(IKey.dynamic(() -> {
            String text = EnumChatFormatting.GOLD + "猫猫扭蛋";
            if (editCallback != null && editCallback.isEditMode()) {
                text += EnumChatFormatting.RED + " [编辑]";
            }
            return text;
        })).pos(0, 2)
            .size(PAGE_WIDTH, 12)
            .textAlign(Alignment.Center)
            .shadow(false);
    }

    /**
     * 卡池切换按钮行：每池一个按钮（文本显示「池名 价格x币 ｜ 余额y」）。
     * <p>
     * 点击切换 {@link LotteryClientData#setSelectedPoolId}（仅客户端本地状态，
     * 不牵涉服务端）；当前选中池文字金色高亮，未选中灰色。
     * 池数多于按钮位时按下发顺序取前几个（默认配置恰为 2 池）。
     */
    private static IWidget createPoolSelector() {
        ParentWidget<?> row = new ParentWidget<>().pos(0, POOL_BTN_Y)
            .size(PAGE_WIDTH, POOL_BTN_H);
        // 按钮位按 2 池布局（(170 - 2×2)/2 = 83 起位）；池数量动态由 Supplier 求值
        for (int i = 0; i < 2; i++) {
            final int index = i;
            int x = (PAGE_WIDTH - 2 * POOL_BTN_W - POOL_BTN_GAP) / 2 + index * (POOL_BTN_W + POOL_BTN_GAP);
            row.child(
                new ButtonWidget<>().pos(x, 0)
                    .size(POOL_BTN_W, POOL_BTN_H)
                    .background(NekoGuiTextures.TEXT_FIELD_BACKGROUND)
                    .overlay(IKey.dynamic(() -> poolButtonText(index)))
                    .tooltipBuilder(t -> {
                        LotteryClientData.PoolSummary pool = poolAt(index);
                        if (pool != null) {
                            t.addLine(IKey.str(EnumChatFormatting.YELLOW + pool.name));
                            t.addLine(
                                IKey.str(
                                    EnumChatFormatting.GRAY + "单抽消耗："
                                        + pool.costPerDraw
                                        + " "
                                        + currencyName(pool.currencyId)));
                            t.addLine(
                                IKey.str(
                                    EnumChatFormatting.GRAY + "团队余额：" + LotteryClientData.getBalance(pool.currencyId)));
                            if (LotteryClientData.getSelectedPoolId()
                                .equals(pool.id)) {
                                t.addLine(IKey.str(EnumChatFormatting.GREEN + "当前选中"));
                            } else {
                                t.addLine(IKey.str(EnumChatFormatting.AQUA + "点击切换到此卡池"));
                            }
                        }
                    })
                    .tooltipAutoUpdate(true)
                    .onMouseTapped(mouse -> {
                        LotteryClientData.PoolSummary pool = poolAt(index);
                        if (mouse == 0 && pool != null) {
                            LotteryClientData.setSelectedPoolId(pool.id);
                            // 切换卡池时重置动画（防止上个池的停格残留到新池轮盘）
                            LotteryAnimationController.getInstance()
                                .reset();
                            return true;
                        }
                        return false;
                    }));
        }
        return row;
    }

    /** 卡池按钮文本：选中池金色，未选中灰色；无该序号池时显示占位 */
    private static String poolButtonText(int index) {
        LotteryClientData.PoolSummary pool = poolAt(index);
        if (pool == null) return EnumChatFormatting.DARK_GRAY + "——";
        boolean selected = LotteryClientData.getSelectedPoolId()
            .equals(pool.id);
        EnumChatFormatting color = selected ? EnumChatFormatting.GOLD : EnumChatFormatting.GRAY;
        // 压缩显示：池名 + 价格（余额在 tooltip，按钮宽度有限）
        return color + pool.name + " " + pool.costPerDraw + "币";
    }

    /** 按序号取卡池摘要（越界返回 null） */
    private static LotteryClientData.PoolSummary poolAt(int index) {
        List<LotteryClientData.PoolSummary> pools = LotteryClientData.getPools();
        return index >= 0 && index < pools.size() ? pools.get(index) : null;
    }

    /** 货币显示名（neko → 猫猫币，shimmeringNeko → 闪烁猫猫币） */
    private static String currencyName(String currencyId) {
        if (currencyId == null) return "?";
        String name = NekoCurrencyRegistrar.getDisplayName(currencyId);
        return name == null || name.isEmpty() ? currencyId : name;
    }

    // ==================== 轮盘区 ====================

    /**
     * 轮盘区：4 列环形边框布局（槽位数 = 当前池条目数，≤{@link #MAX_SLOTS}）。
     * <p>
     * 每格四层绘制（按添加顺序）：槽位底（按稀有度）→ 物品图标 → 稀有度角标 →
     * 点亮高亮框（动画 Supplier 求值）。中央空区放轮盘指针（跟随点亮格旋转指示）。
     * <p>
     * 点亮格由 {@link LotteryAnimationController#getCurrentLitSlot()} 决定；
     * 本区每个动态 Supplier 求值前先 {@link #tickAnimation()} 推进动画状态并
     * 消费未处理结果（启动动画），保证同一结果只触发一次。
     * <p>
     * <b>编辑模式</b>（v1.7.0 目标 4）：每格追加透明点击层（仅编辑模式启用），
     * 点击通过 {@link LotteryEditCallback#onEditEntryRequested} 打开条目编辑面板。
     *
     * @param editCallback 编辑模式回调；null 时不添加点击层
     */
    private static IWidget createWheelArea(LotteryEditCallback editCallback) {
        ParentWidget<?> wheel = new ParentWidget<>().pos(0, WHEEL_Y)
            .size(PAGE_WIDTH, WHEEL_H);
        for (int i = 0; i < MAX_SLOTS; i++) {
            final int index = i;
            int[] pos = ringPos(index);
            if (pos == null) continue; // 中央空区不布格
            int x = wheelX() + pos[0];
            int y = pos[1];

            // 第 1 层：槽位底（按条目稀有度选纹理；格超出条目数时不渲染）
            wheel.child(
                new IDrawable.DrawableWidget(new DynamicDrawable(() -> slotTexture(index))).pos(x, y)
                    .size(SLOT_SIZE, SLOT_SIZE));

            // 第 2 层：物品图标（16x16 居中；条目无法构建物品时为空）
            wheel.child(
                new IDrawable.DrawableWidget(new DynamicDrawable(() -> slotItem(index))).pos(x + 4, y + 4)
                    .size(16, 16)
                    .tooltipBuilder(t -> slotTooltip(t, index))
                    .tooltipAutoUpdate(true));

            // 第 3 层：稀有度角标（8x8 右上角；COMMON 无角标）
            wheel.child(
                new IDrawable.DrawableWidget(new DynamicDrawable(() -> cornerTexture(index))).pos(x + 16, y)
                    .size(8, 8));

            // 第 4 层：点亮高亮框（动画点亮格 / 停格闪烁）
            wheel.child(new IDrawable.DrawableWidget((context, dx, dy, w, h, theme) -> {
                tickAnimation();
                int lit = LotteryAnimationController.getInstance()
                    .getCurrentLitSlot();
                if (lit != index) return;
                LotteryAnimationController anim = LotteryAnimationController.getInstance();
                // 停格后按 500ms 方波闪烁；旋转中恒亮
                if (anim.isFinished() && !anim.isFinishBlinkOn()) return;
                int color = anim.isFinished() ? COLOR_FINISH_FRAME : COLOR_LIT_FRAME;
                drawFrame(dx, dy, w, h, color);
            }).pos(x - 1, y - 1)
                .size(SLOT_SIZE + 2, SLOT_SIZE + 2));

            // 第 5 层：编辑模式透明点击层（仅编辑模式且该格有条目时启用）
            if (editCallback != null) {
                ButtonWidget<?> editClickLayer = new ButtonWidget<>().pos(x, y)
                    .size(SLOT_SIZE, SLOT_SIZE)
                    .tooltipBuilder(t -> {
                        LotteryEntry entry = entryAt(index);
                        if (entry != null) {
                            t.addLine(IKey.str(EnumChatFormatting.YELLOW + "[编辑模式] 点击编辑此条目（" + entry.getId() + "）"));
                        }
                    })
                    .tooltipAutoUpdate(true)
                    .onMouseTapped(mouse -> {
                        if (mouse == 0 && editCallback.isEditMode()) {
                            LotteryClientData.PoolSummary pool = LotteryClientData.getSelectedPool();
                            LotteryEntry entry = entryAt(index);
                            if (pool != null && entry != null) {
                                editCallback.onEditEntryRequested(pool, entry, index);
                            }
                            return true;
                        }
                        return false;
                    });
                // 仅编辑模式且该格有条目时启用（非编辑模式完全透明不拦截）
                editClickLayer.setEnabledIf(w -> editCallback.isEditMode() && entryAt(index) != null);
                wheel.child(editClickLayer);
            }
        }

        // 中央指针（跟随点亮格方向；仅动画期间显示）
        wheel.child(new IDrawable.DrawableWidget((context, dx, dy, w, h, theme) -> {
            tickAnimation();
            LotteryAnimationController anim = LotteryAnimationController.getInstance();
            int lit = anim.getCurrentLitSlot();
            if (lit < 0) return;
            int[] litPos = ringPos(lit);
            if (litPos == null) return;
            // 指针绘制在点亮格中心朝向轮盘中心的一侧（偏移 4px 内缩）
            int litCx = wheelX() + litPos[0] + SLOT_SIZE / 2;
            int litCy = litPos[1] + SLOT_SIZE / 2;
            int centerCx = PAGE_WIDTH / 2;
            int centerCy = WHEEL_H / 2;
            int px = litCx + (centerCx - litCx) / 4 - 8;
            int py = litCy + (centerCy - litCy) / 4 - 8;
            NekoGuiTextures.LOTTERY_POINTER.draw(context, px - dx, py - dy, 16, 16, theme);
        }).pos(0, 0)
            .size(PAGE_WIDTH, WHEEL_H));
        return wheel;
    }

    /** 轮盘左上角 X（按 4 列总宽水平居中） */
    private static int wheelX() {
        int gridW = WHEEL_COLS * (SLOT_SIZE + SLOT_GAP) - SLOT_GAP;
        return (PAGE_WIDTH - gridW) / 2;
    }

    /**
     * 环形边框路径坐标（槽位序号 → 网格内像素偏移）
     * <p>
     * 路径顺序：上行左→右 → 右列上→下（去角重复）→ 下行右→左 → 左列下→上（去角重复），
     * 与 {@link LotteryAnimationController} 的步进方向一致（索引递增 = 顺时针绕圈）。
     *
     * @param index 槽位序号（0..{@link #MAX_SLOTS}-1）
     * @return {x, y} 像素偏移；非边框位置返回 null
     */
    private static int[] ringPos(int index) {
        int stride = SLOT_SIZE + SLOT_GAP; // 28
        int cols = WHEEL_COLS; // 4
        int rows = WHEEL_MAX_ROWS; // 3
        // 上行：index 0..3 → (col, 0)
        if (index < cols) {
            return new int[] { index * stride, 0 };
        }
        // 右列（去上角）：index 4..5 → (3, row)
        int rightStart = cols;
        int rightCount = rows - 1;
        if (index < rightStart + rightCount) {
            int row = index - rightStart + 1;
            return new int[] { (cols - 1) * stride, row * stride };
        }
        // 下行（去右角，右→左）：index 6..8 → (col, 2)
        int bottomStart = rightStart + rightCount;
        int bottomCount = cols - 1;
        if (index < bottomStart + bottomCount) {
            int col = (cols - 2) - (index - bottomStart);
            return new int[] { col * stride, (rows - 1) * stride };
        }
        // 左列（去下角与上角，下→上）：index 9 → (0, 1)
        int leftStart = bottomStart + bottomCount;
        int leftCount = rows - 2;
        if (index < leftStart + leftCount) {
            int row = (rows - 2) - (index - leftStart);
            return new int[] { 0, row * stride };
        }
        return null;
    }

    /** 槽位底纹理：按条目稀有度（COMMON/RARE→灰框，EPIC→金框，LEGENDARY→闪角框） */
    private static IDrawable slotTexture(int index) {
        LotteryEntry entry = entryAt(index);
        if (entry == null) return IDrawable.EMPTY;
        switch (entry.getRarity()) {
            case LEGENDARY:
                return NekoGuiTextures.LOTTERY_SLOT_EPIC;
            case EPIC:
                return NekoGuiTextures.LOTTERY_SLOT_RARE;
            default:
                return NekoGuiTextures.LOTTERY_SLOT_NORMAL;
        }
    }

    /** 槽位物品图标（条目展示物品堆；货币奖品显示对应猫猫币） */
    private static IDrawable slotItem(int index) {
        LotteryEntry entry = entryAt(index);
        if (entry == null) return IDrawable.EMPTY;
        ItemStack stack = entry.getDisplayStack();
        if (stack == null) return IDrawable.EMPTY;
        return new ItemDrawable(stack);
    }

    /** 稀有度角标纹理（RARE→蓝，EPIC/LEGENDARY→紫，COMMON→空） */
    private static IDrawable cornerTexture(int index) {
        LotteryEntry entry = entryAt(index);
        if (entry == null) return IDrawable.EMPTY;
        switch (entry.getRarity()) {
            case RARE:
                return NekoGuiTextures.LOTTERY_CORNER_BLUE;
            case EPIC:
            case LEGENDARY:
                return NekoGuiTextures.LOTTERY_CORNER_PURPLE;
            default:
                return IDrawable.EMPTY;
        }
    }

    /** 槽位 tooltip：条目物品名 + 稀有度 + 数量区间 + 权重 */
    private static void slotTooltip(com.cleanroommc.modularui.screen.RichTooltip t, int index) {
        LotteryEntry entry = entryAt(index);
        if (entry == null) return;
        ItemStack stack = entry.getDisplayStack();
        String name = stack != null ? stack.getDisplayName() : entry.getId();
        LotteryRarity rarity = entry.getRarity();
        t.addLine(IKey.str(rarity.getColor() + name));
        t.addLine(IKey.str(EnumChatFormatting.GRAY + "稀有度：" + rarity.getColor() + rarity.getDisplayName()));
        if (entry.getMinAmount() != entry.getMaxAmount()) {
            t.addLine(IKey.str(EnumChatFormatting.GRAY + "数量：" + entry.getMinAmount() + " ~ " + entry.getMaxAmount()));
        } else {
            t.addLine(IKey.str(EnumChatFormatting.GRAY + "数量：" + entry.getMinAmount()));
        }
        LotteryClientData.PoolSummary pool = LotteryClientData.getSelectedPool();
        if (pool != null && pool.entries.size() > 0) {
            int totalWeight = 0;
            for (LotteryEntry e : pool.entries) {
                if (e != null) totalWeight += Math.max(0, e.getWeight());
            }
            if (totalWeight > 0) {
                String pct = String.format(Locale.ROOT, "%.2f", 100.0 * entry.getWeight() / totalWeight);
                t.addLine(IKey.str(EnumChatFormatting.DARK_GRAY + "概率：" + pct + "%"));
            }
        }
    }

    /** 按槽位序号取当前选中池条目（越界/无池返回 null） */
    private static LotteryEntry entryAt(int index) {
        LotteryClientData.PoolSummary pool = LotteryClientData.getSelectedPool();
        if (pool == null || index < 0 || index >= pool.entries.size()) return null;
        return pool.entries.get(index);
    }

    /** 绘制 1px 高亮边框（点亮格用，GuiDraw 自绘四边） */
    private static void drawFrame(int x, int y, int w, int h, int color) {
        GuiDraw.drawRect(x, y, w, 1, color); // 上
        GuiDraw.drawRect(x, y + h - 1, w, 1, color); // 下
        GuiDraw.drawRect(x, y, 1, h, color); // 左
        GuiDraw.drawRect(x + w - 1, y, 1, h, color); // 右
    }

    // ==================== 动画驱动 ====================

    /**
     * 动画推进（每个动态 Supplier 求值前调用，幂等）：
     * <ol>
     * <li>若 {@link LotteryClientData#hasUnconsumedResult()} 有新结果 → 消费并启动动画</li>
     * <li>推进 {@link LotteryAnimationController#onUpdate()}（SPINNING 超时 → FINISHED）</li>
     * </ol>
     * 10 连停格取结果中最高稀有度条目的槽位索引（快速闪过其余，聚焦最高奖）。
     */
    private static void tickAnimation() {
        LotteryAnimationController anim = LotteryAnimationController.getInstance();
        if (LotteryClientData.hasUnconsumedResult()) {
            String poolId = LotteryClientData.getLastResultPoolId();
            List<LotteryClientData.DrawResult> results = LotteryClientData.getLastResults();
            LotteryClientData.PoolSummary pool = LotteryClientData.getPool(poolId);
            int slotCount = pool != null ? pool.entries.size() : 0;
            boolean quick = results.size() > 1;
            int target = quick ? highestRaritySlot(results) : (results.isEmpty() ? -1 : results.get(0).slotIndex);
            LotteryClientData.consumeDrawResult();
            // 结果池与当前选中池一致才播放动画（跨池结果仅刷新数据，不错投轮盘）
            if (slotCount > 0 && poolId.equals(LotteryClientData.getSelectedPoolId())) {
                anim.startAnimation(poolId, target, slotCount, quick);
            }
        }
        anim.onUpdate();
    }

    /** 10 连结果中最高稀有度条目的槽位索引（同级取先出者；无有效槽位取 0） */
    private static int highestRaritySlot(List<LotteryClientData.DrawResult> results) {
        int bestSlot = 0;
        int bestOrdinal = -1;
        for (LotteryClientData.DrawResult r : results) {
            if (r == null) continue;
            int ordinal = r.rarity()
                .ordinal();
            if (ordinal > bestOrdinal && r.slotIndex >= 0) {
                bestOrdinal = ordinal;
                bestSlot = r.slotIndex;
            }
        }
        return bestSlot;
    }

    // ==================== 结果提示与 10 连列表 ====================

    /**
     * 单抽结果 / 错误提示条（限时 {@link #RESULT_DISPLAY_MS} 毫秒）。
     * <p>
     * 单抽动画停格后显示中奖条目（稀有度色 + 物品名 ×数量，保底附加标注）；
     * 失败结果码（余额不足/池缺失/错误）立即显示红色原因；
     * 10 连期间由 {@link #createMultiResultList()} 列表展示，本条显示汇总。
     */
    private static IWidget createResultMessage() {
        return new TextWidget<>(IKey.dynamic(() -> {
            int code = LotteryClientData.getLastResultCode();
            if (code == LotteryClientData.RESULT_NONE) return "";
            if (System.currentTimeMillis() - LotteryClientData.getLastResultTimeMs() > RESULT_DISPLAY_MS) return "";
            switch (code) {
                case LotteryClientData.RESULT_INSUFFICIENT:
                    return EnumChatFormatting.RED + "猫猫币余额不足，无法抽取";
                case LotteryClientData.RESULT_POOL_MISSING:
                    return EnumChatFormatting.RED + "卡池不存在或暂不可用";
                case LotteryClientData.RESULT_ERROR:
                    return EnumChatFormatting.RED + "抽奖失败，请稍后再试";
                case LotteryClientData.RESULT_SUCCESS:
                    return successSummary();
                default:
                    return "";
            }
        })).pos(0, RESULT_Y)
            .size(PAGE_WIDTH, 12)
            .textAlign(Alignment.Center)
            .shadow(false);
    }

    /** 成功结果汇总文本（单抽=中奖条目，10 连=最高稀有度条目） */
    private static String successSummary() {
        List<LotteryClientData.DrawResult> results = LotteryClientData.getLastResults();
        if (results.isEmpty()) return "";
        LotteryAnimationController anim = LotteryAnimationController.getInstance();
        if (results.size() > 1) {
            // 10 连：动画停格后提示最高奖（列表区已展示全部）
            if (!anim.isFinished()) return EnumChatFormatting.YELLOW + "抽取中...";
            LotteryClientData.DrawResult best = bestResult(results);
            return resultText(best, EnumChatFormatting.GOLD + "10 连最高奖：");
        }
        // 单抽：停格后展示
        if (!anim.isFinished()) return EnumChatFormatting.YELLOW + "抽取中...";
        return resultText(results.get(0), "");
    }

    /** 单条结果文本（稀有度色 + 条目名 ×数量，保底标注） */
    private static String resultText(LotteryClientData.DrawResult result, String prefix) {
        if (result == null) return "";
        LotteryRarity rarity = result.rarity();
        String name = resultEntryName(result);
        String text = prefix + rarity.getColor() + name + EnumChatFormatting.WHITE + " ×" + result.amount;
        if (result.isPity) {
            text += EnumChatFormatting.LIGHT_PURPLE + "（保底）";
        }
        return text;
    }

    /** 结果条目显示名（按条目 ID 查池配置取物品名，查不到回退条目 ID） */
    private static String resultEntryName(LotteryClientData.DrawResult result) {
        LotteryClientData.PoolSummary pool = LotteryClientData.getPool(LotteryClientData.getLastResultPoolId());
        if (pool != null) {
            for (LotteryEntry entry : pool.entries) {
                if (entry != null && result.entryId.equals(entry.getId())) {
                    ItemStack stack = entry.getDisplayStack();
                    return stack != null ? stack.getDisplayName() : entry.getId();
                }
            }
        }
        return result.entryId;
    }

    /** 结果列表中最高稀有度条目（同级取先出者） */
    private static LotteryClientData.DrawResult bestResult(List<LotteryClientData.DrawResult> results) {
        LotteryClientData.DrawResult best = null;
        for (LotteryClientData.DrawResult r : results) {
            if (r == null) continue;
            if (best == null || r.rarity()
                .ordinal()
                > best.rarity()
                    .ordinal()) {
                best = r;
            }
        }
        return best;
    }

    /**
     * 10 连结果格子列表（2 行 × 5 列，每格 18x18：槽位底 + 物品 + 角标）。
     * <p>
     * 仅当最近结果为 10 连（结果数 > 1）且未超时显示；动画停格后逐格点亮
     * 高稀有度（≥EPIC）结果格，聚焦大奖。
     */
    private static IWidget createMultiResultList() {
        ParentWidget<?> list = new ParentWidget<>().pos(0, LIST_Y)
            .size(PAGE_WIDTH, LIST_H);
        int cols = 5;
        int gridW = cols * (LIST_CELL + LIST_GAP) - LIST_GAP;
        int startX = (PAGE_WIDTH - gridW) / 2;
        for (int i = 0; i < 10; i++) {
            final int index = i;
            int col = index % cols;
            int row = index / cols;
            int x = startX + col * (LIST_CELL + LIST_GAP);
            int y = row * (LIST_CELL + LIST_GAP);

            // 槽位底（按结果稀有度；无该序号结果或非 10 连时为空）
            list.child(
                new IDrawable.DrawableWidget(new DynamicDrawable(() -> multiSlotTexture(index))).pos(x, y)
                    .size(LIST_CELL, LIST_CELL));

            // 物品图标（14x14 居中）
            list.child(
                new IDrawable.DrawableWidget(new DynamicDrawable(() -> multiSlotItem(index))).pos(x + 2, y + 2)
                    .size(14, 14)
                    .tooltipBuilder(t -> multiSlotTooltip(t, index))
                    .tooltipAutoUpdate(true));

            // 高稀有度高亮框（停格后 ≥EPIC 格金色描边）
            list.child(new IDrawable.DrawableWidget((context, dx, dy, w, h, theme) -> {
                LotteryClientData.DrawResult result = multiResultAt(index);
                if (result == null || !result.isHighRarity) return;
                if (!LotteryAnimationController.getInstance()
                    .isFinished()) return;
                drawFrame(dx, dy, w, h, COLOR_FINISH_FRAME);
            }).pos(x - 1, y - 1)
                .size(LIST_CELL + 2, LIST_CELL + 2));
        }
        return list;
    }

    /** 取第 index 条 10 连结果（非 10 连/越界/超时返回 null） */
    private static LotteryClientData.DrawResult multiResultAt(int index) {
        if (LotteryClientData.getLastResultCode() != LotteryClientData.RESULT_SUCCESS) return null;
        if (System.currentTimeMillis() - LotteryClientData.getLastResultTimeMs() > RESULT_DISPLAY_MS) return null;
        List<LotteryClientData.DrawResult> results = LotteryClientData.getLastResults();
        if (results.size() <= 1) return null; // 单抽不走列表
        return index >= 0 && index < results.size() ? results.get(index) : null;
    }

    /** 10 连列表槽位底纹理（按结果稀有度） */
    private static IDrawable multiSlotTexture(int index) {
        LotteryClientData.DrawResult result = multiResultAt(index);
        if (result == null) return IDrawable.EMPTY;
        switch (result.rarity()) {
            case LEGENDARY:
                return NekoGuiTextures.LOTTERY_SLOT_EPIC;
            case EPIC:
                return NekoGuiTextures.LOTTERY_SLOT_RARE;
            default:
                return NekoGuiTextures.LOTTERY_SLOT_NORMAL;
        }
    }

    /** 10 连列表物品图标（按结果条目 ID 查池配置） */
    private static IDrawable multiSlotItem(int index) {
        LotteryClientData.DrawResult result = multiResultAt(index);
        if (result == null) return IDrawable.EMPTY;
        LotteryEntry entry = resultEntry(result);
        if (entry == null) return IDrawable.EMPTY;
        ItemStack stack = entry.getDisplayStack();
        return stack != null ? new ItemDrawable(stack) : IDrawable.EMPTY;
    }

    /** 10 连列表 tooltip（物品名 + 稀有度 + 数量 + 保底标注） */
    private static void multiSlotTooltip(com.cleanroommc.modularui.screen.RichTooltip t, int index) {
        LotteryClientData.DrawResult result = multiResultAt(index);
        if (result == null) return;
        LotteryRarity rarity = result.rarity();
        t.addLine(
            IKey.str(rarity.getColor() + resultEntryName(result) + EnumChatFormatting.WHITE + " ×" + result.amount));
        t.addLine(IKey.str(EnumChatFormatting.GRAY + "稀有度：" + rarity.getColor() + rarity.getDisplayName()));
        if (result.isPity) {
            t.addLine(IKey.str(EnumChatFormatting.LIGHT_PURPLE + "保底出货"));
        }
    }

    /** 按结果查池配置条目（供取物品堆） */
    private static LotteryEntry resultEntry(LotteryClientData.DrawResult result) {
        LotteryClientData.PoolSummary pool = LotteryClientData.getPool(LotteryClientData.getLastResultPoolId());
        if (pool == null) return null;
        for (LotteryEntry entry : pool.entries) {
            if (entry != null && result.entryId.equals(entry.getId())) return entry;
        }
        return null;
    }

    // ==================== 保底进度 ====================

    /**
     * 保底进度：文本（距保底 x/y）+ 进度条（紫填充）。
     * <p>
     * 保底未启用或阈值为 0 时显示「本池无保底」；计数读取
     * {@link LotteryClientData#getPityCounter}（团队共享，随同步包刷新）。
     */
    private static IWidget createPityProgress() {
        ParentWidget<?> box = new ParentWidget<>().pos(0, PITY_Y)
            .size(PAGE_WIDTH, 12);

        // 文本行
        box.child(new TextWidget<>(IKey.dynamic(() -> {
            LotteryClientData.PoolSummary pool = LotteryClientData.getSelectedPool();
            if (pool == null) return "";
            if (!pool.pityEnabled || pool.hardPityThreshold <= 0) {
                return EnumChatFormatting.DARK_GRAY + "本池无保底";
            }
            int pity = LotteryClientData.getPityCounter(pool.id);
            LotteryRarity guaranteed = LotteryRarity.fromString(pool.guaranteedRarity);
            return EnumChatFormatting.GRAY + "距保底 "
                + guaranteed.getColor()
                + pity
                + "/"
                + pool.hardPityThreshold
                + EnumChatFormatting.GRAY
                + "（必出"
                + guaranteed.getDisplayName()
                + "+）";
        })).pos(0, 0)
            .size(PAGE_WIDTH, 8)
            .textAlign(Alignment.Center)
            .scale(0.75f)
            .shadow(false));

        // 进度条（宽 120 居中）
        box.child(new IDrawable.DrawableWidget((context, x, y, w, h, theme) -> {
            LotteryClientData.PoolSummary pool = LotteryClientData.getSelectedPool();
            GuiDraw.drawRect(x, y, w, h, COLOR_PITY_BG);
            if (pool == null || !pool.pityEnabled || pool.hardPityThreshold <= 0) return;
            float progress = Math.min(1f, LotteryClientData.getPityCounter(pool.id) / (float) pool.hardPityThreshold);
            int fillW = Math.round((w - 2) * progress);
            if (fillW > 0) {
                GuiDraw.drawRect(x + 1, y + 1, fillW, h - 2, COLOR_PITY_FILL);
            }
        }).pos((PAGE_WIDTH - 120) / 2, 9)
            .size(120, 5));
        return box;
    }

    // ==================== 抽奖按钮 ====================

    /**
     * 抽 1 次 / 抽 10 次按钮。
     * <p>
     * 点击通过 {@link LotteryNetworkManager#sendLotteryRequest} 向服务端发起请求
     * （携带机器坐标定位出货槽）；余额不足时文字变红、点击仅提示不发包
     * （服务端仍兜底二次校验，双击/竞态安全）；动画旋转期间点击无效防连发。
     * <p>
     * <b>编辑模式</b>（v1.7.0 目标 4）：点击被拦截（禁止常规抽奖交互），
     * tooltip 提示处于编辑模式。
     *
     * @param machine      触发 GUI 的机器
     * @param editCallback 编辑模式回调；null 表示不支持编辑模式
     */
    private static IWidget createDrawButtons(IGregTechTileEntity machine, LotteryEditCallback editCallback) {
        ParentWidget<?> row = new ParentWidget<>().pos(0, DRAW_BTN_Y)
            .size(PAGE_WIDTH, DRAW_BTN_H);
        int totalW = 2 * DRAW_BTN_W + DRAW_BTN_GAP;
        int startX = (PAGE_WIDTH - totalW) / 2;

        row.child(createDrawButton(machine, startX, 1, NekoGuiTextures.LOTTERY_BTN_DRAW, "抽 1 次", editCallback));
        row.child(
            createDrawButton(
                machine,
                startX + DRAW_BTN_W + DRAW_BTN_GAP,
                10,
                NekoGuiTextures.LOTTERY_BTN_DRAW10,
                "抽 10 次",
                editCallback));
        return row;
    }

    /** 单个抽奖按钮（count=1 或 10；编辑模式拦截点击） */
    private static IWidget createDrawButton(IGregTechTileEntity machine, int x, int count, UITexture texture,
        String label, LotteryEditCallback editCallback) {
        return new ButtonWidget<>().pos(x, 0)
            .size(DRAW_BTN_W, DRAW_BTN_H)
            .background(texture)
            .overlay(IKey.dynamic(() -> drawButtonText(label, count)))
            .tooltipBuilder(t -> {
                // 编辑模式：仅提示，不展示价格/余额
                if (editCallback != null && editCallback.isEditMode()) {
                    t.addLine(IKey.str(EnumChatFormatting.RED + "[编辑模式] 抽奖交互已禁用"));
                    t.addLine(IKey.str(EnumChatFormatting.GRAY + "点击轮盘槽位编辑条目"));
                    return;
                }
                LotteryClientData.PoolSummary pool = LotteryClientData.getSelectedPool();
                if (pool == null) {
                    t.addLine(IKey.str(EnumChatFormatting.RED + "暂无可用卡池"));
                    return;
                }
                int total = pool.totalCost(count);
                int balance = LotteryClientData.getBalance(pool.currencyId);
                t.addLine(
                    IKey.str(EnumChatFormatting.YELLOW + label + "：" + total + " " + currencyName(pool.currencyId)));
                if (balance < total) {
                    t.addLine(IKey.str(EnumChatFormatting.RED + "团队余额不足（" + balance + "/" + total + "）"));
                } else {
                    t.addLine(IKey.str(EnumChatFormatting.GRAY + "团队余额：" + balance));
                    t.addLine(IKey.str(EnumChatFormatting.GREEN + "点击抽取，奖品入出货槽"));
                }
                if (pool.pityEnabled && pool.hardPityThreshold > 0) {
                    int pity = LotteryClientData.getPityCounter(pool.id);
                    t.addLine(
                        IKey.str(EnumChatFormatting.LIGHT_PURPLE + "保底进度：" + pity + "/" + pool.hardPityThreshold));
                }
            })
            .tooltipAutoUpdate(true)
            .onMouseTapped(mouse -> {
                if (mouse != 0) return false;
                // 编辑模式：拦截抽奖交互（禁止常规操作）
                if (editCallback != null && editCallback.isEditMode()) {
                    return true;
                }
                LotteryClientData.PoolSummary pool = LotteryClientData.getSelectedPool();
                if (pool == null) return false;
                // 动画旋转期间禁止连发（服务端也有幂等兜底）
                if (LotteryAnimationController.getInstance()
                    .isSpinning()) {
                    return true;
                }
                int total = pool.totalCost(count);
                if (LotteryClientData.getBalance(pool.currencyId) < total) {
                    // 余额不足：本地拦截（服务端二次校验兜底）
                    return true;
                }
                if (machine != null) {
                    int dim = machine.getWorld() != null ? machine.getWorld().provider.dimensionId : 0;
                    LotteryNetworkManager.sendLotteryRequest(
                        pool.id,
                        count,
                        machine.getXCoord(),
                        machine.getYCoord(),
                        machine.getZCoord(),
                        dim);
                }
                return true;
            });
    }

    /** 抽奖按钮文本（选中池无数据时置灰；余额不足时红色警示） */
    private static String drawButtonText(String label, int count) {
        LotteryClientData.PoolSummary pool = LotteryClientData.getSelectedPool();
        if (pool == null) return EnumChatFormatting.DARK_GRAY + label;
        int total = pool.totalCost(count);
        int balance = LotteryClientData.getBalance(pool.currencyId);
        EnumChatFormatting color = balance < total ? EnumChatFormatting.RED : EnumChatFormatting.WHITE;
        return color + label + " " + total;
    }

    // ==================== 最近中奖摘要 ====================

    /**
     * 最近中奖滚动摘要（最新 {@link #HISTORY_LINES} 条，团队共享历史）。
     * <p>
     * 每条格式：「HH:mm 玩家名 获得 物品名 ×数量」（稀有度色物品名）。
     * 历史随同步包刷新（登录/打开页/每次抽取后）。
     */
    private static IWidget createHistoryDisplay() {
        ParentWidget<?> box = new ParentWidget<>().pos(0, HISTORY_Y)
            .size(PAGE_WIDTH, 10 + HISTORY_LINES * HISTORY_LINE_H);

        box.child(
            new TextWidget<>(IKey.str(EnumChatFormatting.DARK_GRAY + "—— 最近中奖 ——")).pos(0, 0)
                .size(PAGE_WIDTH, 8)
                .textAlign(Alignment.Center)
                .scale(0.7f)
                .shadow(false));

        for (int i = 0; i < HISTORY_LINES; i++) {
            final int index = i;
            box.child(
                new TextWidget<>(IKey.dynamic(() -> historyLine(index))).pos(0, 10 + i * HISTORY_LINE_H)
                    .size(PAGE_WIDTH, HISTORY_LINE_H)
                    .textAlign(Alignment.Center)
                    .scale(0.7f)
                    .shadow(false));
        }
        return box;
    }

    /** 单条历史摘要文本（越界返回空串） */
    private static String historyLine(int index) {
        List<LotteryHistory.HistoryEntry> history = LotteryClientData.getRecentHistory();
        if (index < 0 || index >= history.size()) return "";
        LotteryHistory.HistoryEntry entry = history.get(index);
        String time = TIME_FORMAT.format(new Date(entry.timestamp));
        LotteryRarity rarity = LotteryRarity.fromString(entry.rarityName);
        return EnumChatFormatting.DARK_GRAY + time
            + " "
            + EnumChatFormatting.GRAY
            + entry.playerName
            + " 获得 "
            + rarity.getColor()
            + historyEntryName(entry)
            + EnumChatFormatting.WHITE
            + " ×"
            + entry.amount;
    }

    /** 历史条目显示名（按卡池+条目 ID 查配置取物品名，查不到回退条目 ID） */
    private static String historyEntryName(LotteryHistory.HistoryEntry entry) {
        LotteryClientData.PoolSummary pool = LotteryClientData.getPool(entry.poolId);
        if (pool != null) {
            for (LotteryEntry e : pool.entries) {
                if (e != null && entry.entryId.equals(e.getId())) {
                    ItemStack stack = e.getDisplayStack();
                    return stack != null ? stack.getDisplayName() : e.getId();
                }
            }
        }
        return entry.entryId;
    }
}

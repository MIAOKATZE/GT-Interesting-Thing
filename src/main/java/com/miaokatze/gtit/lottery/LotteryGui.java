package com.miaokatze.gtit.lottery;

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
import com.cleanroommc.modularui.screen.viewport.GuiContext;
import com.cleanroommc.modularui.theme.WidgetTheme;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.miaokatze.gtit.client.gui.NekoGuiTextures;
import com.miaokatze.gtit.currency.NekoCurrencyRegistrar;
import com.miaokatze.gtit.trade.NekoClientBalances;

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
 * <li>顶部：卡池标题（v1.7.6 起池切换迁移至左侧 sub-page 标签列，页内不再设按钮行）</li>
 * <li>中部：4 列环形边框轮盘（格子绕圈点亮 → cubic ease-out 减速 → 停在结果格，
 * 由 {@link LotteryAnimationController} 纯客户端时间驱动）</li>
 * <li>底部：抽 1 次 / 抽 10 次按钮（余额不足显示总价+提示）、保底进度</li>
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

    /** 轮盘槽位边长（与 slot_*.png 素材一致） */
    private static final int SLOT_SIZE = 24;
    /** 轮盘槽位间距 */
    private static final int SLOT_GAP = 4;
    /** 轮盘最大格数（槽位数 = 条目数，上限与 {@link LotteryPool#MAX_ENTRIES} 保持一致） */
    private static final int MAX_SLOTS = LotteryPool.MAX_ENTRIES;
    /** 轮盘区左上角 Y（v1.7.7 G3①：40→24，为背包行让出空间） */
    private static final int WHEEL_Y = 24;
    /** 轮盘区高度（最大 3 行 × 28 = 84；动态布局在区内水平/垂直居中） */
    private static final int WHEEL_H = 3 * (SLOT_SIZE + SLOT_GAP) - SLOT_GAP;
    /**
     * 指针底边越过点亮格上边后的下移像素（v1.7.9 起固定朝下直绘：
     * 指针下沿对齐格子上边中点后再往下偏一点点，观感更贴合；可调。
     * v1.7.10 自 2 加至 4——用户反馈指针贴格上边仍偏高，再下移一点）
     */
    private static final int POINTER_DROP_PX = 4;

    /** 结果提示条 Y（v1.7.7 G3①：136→114） */
    private static final int RESULT_Y = 114;
    /** 结果提示显示时长（毫秒；成功结果自揭示时刻起算，见 {@link #resultRevealedAtMs}） */
    private static final long RESULT_DISPLAY_MS = 6000L;

    /** 10 连结果列表 Y（v1.7.7 G3①：152→128） */
    private static final int LIST_Y = 128;
    /** 10 连结果列表单格边长 */
    private static final int LIST_CELL = 18;
    /** 10 连结果列表间距 */
    private static final int LIST_GAP = 2;
    /** 10 连结果列表高度（两行，每格 18 + 间距 2） */
    private static final int LIST_H = 2 * LIST_CELL + LIST_GAP;

    /** 保底进度文本 Y（v1.7.7 G3①：200→172） */
    private static final int PITY_Y = 172;
    /** 抽奖按钮 Y（v1.7.7 G3①：214→190；按钮底 = 190 + 24 = 214 ≤ 229） */
    private static final int DRAW_BTN_Y = 190;
    /** 抽奖按钮宽（v1.7.6 G4 自 64 加宽至 72：容纳「按钮文案+价格」防溢出；素材 fullImage 轻微拉伸） */
    private static final int DRAW_BTN_W = 72;
    /** 抽奖按钮高 */
    private static final int DRAW_BTN_H = 24;
    /** 抽奖按钮间距（v1.7.6 G4 自 4 加宽至 8：两按钮总宽 152，居中后左钮 X=9，右缘 161） */
    private static final int DRAW_BTN_GAP = 8;
    /** 按钮文本显示列预算（全角计 2 列/半角计 1 列，72px 宽扣内边距约 14 列） */
    private static final int DRAW_BTN_TEXT_BUDGET = 14;

    // ==================== 颜色常量（ARGB） ====================

    /** 点亮格高亮框色（金色，叠加在槽位底上） */
    private static final int COLOR_LIT_FRAME = 0xCCFFC84A;
    /** 停格后高亮框色（不透明金，闪烁相位切换） */
    private static final int COLOR_FINISH_FRAME = 0xFFFFD24A;
    /** 保底进度条底色（v1.7.6 G4 自 0xFF1E1E2E 加深，与亮紫填充拉开对比度） */
    private static final int COLOR_PITY_BG = 0xFF101020;
    /** 保底进度条填充（亮紫，呼应 EPIC 保底稀有度；G4 自 0xFFAA66FF 调亮提升对比度） */
    private static final int COLOR_PITY_FILL = 0xFFC47FFF;

    // ==================== 结果揭示状态（v1.7.8 客户端门控） ====================

    /**
     * 已揭示结果对应的结果时间戳（{@link LotteryClientData#getLastResultTimeMs()}）。
     * <p>
     * 揭示 = 动画停格对应当前结果后消费（或跨池等无动画路径直接消费）的上升沿，
     * 由 {@link #tickAnimation()} 在消费点经 {@link #markResultRevealed(long)} 记录。
     * 新结果到达后时间戳自动失配 → 未揭示，无需显式重置。
     */
    private static volatile long revealedResultTimeMs = 0L;
    /** 揭示时刻（墙钟；{@link #RESULT_DISPLAY_MS} 展示窗口由此起算） */
    private static volatile long resultRevealedAtMs = 0L;

    private LotteryGui() {}

    /**
     * 记录结果揭示（上升沿）：动画停格对应当前结果后消费、或无动画路径直接消费时，
     * 由 {@link #tickAnimation()} 调用。
     * <p>
     * 幂等：同一结果时间戳重复调用不刷新揭示时刻，防止展示窗口被续期。
     *
     * @param resultTimeMs 被揭示结果的时间戳（{@link LotteryClientData#getLastResultTimeMs()}）
     */
    private static void markResultRevealed(long resultTimeMs) {
        if (resultTimeMs <= 0 || revealedResultTimeMs == resultTimeMs) return;
        revealedResultTimeMs = resultTimeMs;
        resultRevealedAtMs = System.currentTimeMillis();
    }

    /**
     * 当前抽取结果是否已揭示（v1.7.8 客户端门控：动画播完才允许展示结果，防剧透）。
     * <p>
     * 揭示条件（满足其一）：
     * <ul>
     * <li>动画已停格（FINISHED）且绑定的时间戳 = 当前结果时间戳（正常路径）</li>
     * <li>结果已被消费（无未消费结果）且动画不在旋转中（跨池/槽位无效等
     * 无动画直接消费路径）</li>
     * </ul>
     * 仅对 {@link LotteryClientData#RESULT_SUCCESS} 有意义；失败结果码不启动动画、
     * 无剧透风险，其提示条不经过本门控（窗口自结果到达时刻起算）。
     *
     * @return true 表示当前成功结果可展示
     */
    private static boolean isResultRevealed() {
        if (LotteryClientData.getLastResultCode() != LotteryClientData.RESULT_SUCCESS) return false;
        long resultTimeMs = LotteryClientData.getLastResultTimeMs();
        if (resultTimeMs <= 0) return false;
        LotteryAnimationController anim = LotteryAnimationController.getInstance();
        // 正常路径：动画停格且停格对应的就是当前结果
        if (anim.isFinished() && anim.getAnimatingResultTimeMs() == resultTimeMs) return true;
        // 无动画路径：结果已消费且不在旋转（跨池/无效槽位直接消费）
        return !LotteryClientData.hasUnconsumedResult() && !anim.isSpinning();
    }

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
        // v1.7.6：顶部卡池切换按钮行已移除——池切换由左侧 sub-page 标签列承担
        // （NekoVMGuiV2.createSubTabColumn(MAIN_TAB_LOTTERY)，数据源 LotteryClientData.getPools()）
        page.child(createWheelArea(editCallback)); // 轮盘区（环形槽位 + 物品 + 角标 + 点亮框 + 指针；编辑模式可点击）
        page.child(createResultMessage()); // 单抽结果 / 错误提示（限时）
        page.child(createMultiResultList()); // 10 连结果格子列表
        page.child(createPityProgress()); // 保底进度（文本 + 进度条）
        page.child(createDrawButtons(machine, editCallback)); // 抽 1 次 / 抽 10 次（编辑模式拦截）
        // v1.7.9：「最近中奖」滚动摘要已随中奖记录功能一并移除

        return page;
    }

    // ==================== 标题 ====================

    /** 标题行：「猫猫扭蛋 - 池名」（金色，居中；编辑模式附加红色标识） */
    private static IWidget createTitle(LotteryEditCallback editCallback) {
        return new TextWidget<>(IKey.dynamic(() -> {
            String text = EnumChatFormatting.GOLD + "猫猫扭蛋";
            // v1.7.6：池切换迁至左侧标签列，标题附加当前选中池名作指示
            LotteryClientData.PoolSummary pool = LotteryClientData.getSelectedPool();
            if (pool != null && pool.name != null && !pool.name.isEmpty()) {
                text += EnumChatFormatting.YELLOW + " - " + pool.name;
            }
            if (editCallback != null && editCallback.isEditMode()) {
                text += EnumChatFormatting.RED + " [编辑]";
            }
            return text;
        })).pos(0, 2)
            .size(PAGE_WIDTH, 12)
            .textAlign(Alignment.Center)
            .shadow(false);
    }

    /** 货币显示名（neko → 猫猫币，shimmeringNeko → 闪烁猫猫币） */
    private static String currencyName(String currencyId) {
        if (currencyId == null) return "?";
        String name = NekoCurrencyRegistrar.getDisplayName(currencyId);
        return name == null || name.isEmpty() ? currencyId : name;
    }

    // ==================== 消耗展示（v1.7.6 costItems 口径） ====================

    /**
     * 客户端可校验的货币充足性（物品消耗由服务端 canAfford 二次校验，客户端不预检输入槽）
     *
     * @param pool  卡池摘要
     * @param count 连抽次数
     * @return true 表示货币需求全部满足（或无货币需求）
     */
    private static boolean currencyAffordable(LotteryClientData.PoolSummary pool, int count) {
        if (pool == null) return false;
        for (com.miaokatze.gtit.trade.v2.NekoBigItemStack cost : pool.costItems) {
            if (cost == null || cost.getBaseStack() == null) continue;
            String cid = NekoCurrencyRegistrar.getNekoCurrencyId(cost.getBaseStack());
            if (cid != null && NekoClientBalances.getBalance(cid) < cost.getStackSize() * count) {
                return false;
            }
        }
        return true;
    }

    /**
     * 消耗摘要文本（按钮面用，如「5币+2面包」；无消耗显示「免费」）
     * <p>
     * v1.7.6 G4：货币名改用单字缩写（猫猫币→币、闪烁猫猫币→闪币）防按钮文本溢出，
     * 完整货币名见按钮 tooltip 明细。
     */
    private static String costSummary(LotteryClientData.PoolSummary pool, int count) {
        if (pool == null || pool.costItems.isEmpty()) return "免费";
        StringBuilder sb = new StringBuilder();
        for (com.miaokatze.gtit.trade.v2.NekoBigItemStack cost : pool.costItems) {
            if (cost == null || cost.getBaseStack() == null || cost.getStackSize() <= 0) continue;
            if (sb.length() > 0) sb.append("+");
            int total = cost.getStackSize() * count;
            String cid = NekoCurrencyRegistrar.getNekoCurrencyId(cost.getBaseStack());
            if (cid != null) {
                sb.append(total)
                    .append(shortCurrencyName(cid));
            } else {
                sb.append(total)
                    .append(
                        cost.getBaseStack()
                            .getDisplayName());
            }
        }
        return sb.length() == 0 ? "免费" : sb.toString();
    }

    /**
     * 货币按钮面缩写（G4：防按钮文本溢出；tooltip 明细仍用 {@link #currencyName} 全名）
     * <ul>
     * <li>猫猫币 → 币</li>
     * <li>闪烁猫猫币 → 闪币</li>
     * <li>其他 → 全名（兜底）</li>
     * </ul>
     */
    private static String shortCurrencyName(String currencyId) {
        if (NekoCurrencyRegistrar.NEKO_ID.equals(currencyId)) return "币";
        if (NekoCurrencyRegistrar.SHIMMERING_NEKO_ID.equals(currencyId)) return "闪币";
        return currencyName(currencyId);
    }

    // ==================== 轮盘区（v1.7.8 动态布局） ====================

    /**
     * 轮盘区：槽位数 = 当前池条目数（1..{@link #MAX_SLOTS}），矩形环周长等弧长采样布局。
     * <p>
     * 布局规格（N=条目数 → 列×行）：N=1 单格居中；N=2 2x2 对顶角；N=3/4 2x2；
     * N=5/6 3x2；N=7/8 4x2；N=9/10 4x3（N=10 满环，与原固定布局逐格重合零回归）。
     * 整环在轮盘区（{@link #PAGE_WIDTH} × {@link #WHEEL_H}）水平/垂直居中。
     * <p>
     * 绘制结构（v1.7.8 起整区自绘，替代原 per-slot widget 循环——槽位数随条目数
     * 动态变化，ModularUI2 运行期改 pos 不可靠）：
     * <ul>
     * <li>整区自绘层：每格「槽底（按稀有度）→ 物品图标 → 稀有度角标」同帧绘制，
     * 其上叠点亮高亮框（动画 Supplier 求值）与指针（固定朝下倒三角，
     * 底边贴点亮格上边中点，见 {@link #drawPointer}）</li>
     * <li>整区隐形交互层：一个覆盖全区的 {@link ButtonWidget}，tooltip 与编辑点击
     * 统一经 {@link #hitSlot(IWidget)} 命中测试定位槽位（绝对鼠标坐标 − 区域绝对坐标）</li>
     * </ul>
     * 点亮格由 {@link LotteryAnimationController#getCurrentLitSlot()} 决定；
     * 自绘层每帧求值前先 {@link #tickAnimation()} 推进动画状态并消费未处理结果
     * （启动动画），保证同一结果只触发一次。
     *
     * @param editCallback 编辑模式回调；null 时交互层不提供编辑点击
     */
    private static IWidget createWheelArea(LotteryEditCallback editCallback) {
        ParentWidget<?> wheel = new ParentWidget<>().pos(0, WHEEL_Y)
            .size(PAGE_WIDTH, WHEEL_H);

        // 整区自绘层：槽底 + 物品 + 角标 + 点亮框 + 指针（同帧一次绘完）
        wheel.child(new IDrawable.DrawableWidget((context, dx, dy, w, h, theme) -> {
            tickAnimation();
            int n = wheelSlotCount();
            if (n <= 0) return; // 空池不渲染
            // 1) 逐格：槽位底 → 物品图标 → 稀有度角标
            for (int i = 0; i < n; i++) {
                int[] tl = slotTopLeft(i, n);
                slotTexture(i).draw(context, tl[0], tl[1], SLOT_SIZE, SLOT_SIZE, theme);
                slotItem(i).draw(context, tl[0] + 4, tl[1] + 4, 16, 16, theme);
                cornerTexture(i).draw(context, tl[0] + 16, tl[1], 8, 8, theme);
            }
            // 2) 点亮高亮框（旋转中恒亮；停格后按 500ms 方波闪烁）
            LotteryAnimationController anim = LotteryAnimationController.getInstance();
            int lit = anim.getCurrentLitSlot();
            if (lit < 0 || lit >= n) return;
            if (anim.isFinished() && !anim.isFinishBlinkOn()) return;
            int[] litTl = slotTopLeft(lit, n);
            int color = anim.isFinished() ? COLOR_FINISH_FRAME : COLOR_LIT_FRAME;
            drawFrame(litTl[0] - 1, litTl[1] - 1, SLOT_SIZE + 2, SLOT_SIZE + 2, color);
            // 3) 指针（固定朝下倒三角，底边贴点亮格上边中点再下移 POINTER_DROP_PX）
            drawPointer(context, theme, litTl);
        }).pos(0, 0)
            .size(PAGE_WIDTH, WHEEL_H));

        // 整区隐形交互层：tooltip（悬停槽位）+ 编辑点击（编辑模式命中槽位）
        // 无背景/覆盖物 → 完全透明；空 tooltip 不渲染（RichTooltip.isEmpty 直接跳过）
        ButtonWidget<?> hitLayer = new ButtonWidget<>().pos(0, 0)
            .size(PAGE_WIDTH, WHEEL_H);
        hitLayer.tooltipBuilder(t -> {
            int index = hitSlot(hitLayer);
            if (index < 0) return; // 未命中任何槽位：空 tooltip 不显示
            slotTooltip(t, index);
            LotteryEntry entry = entryAt(index);
            if (entry != null && editCallback != null && editCallback.isEditMode()) {
                t.addLine(IKey.str(EnumChatFormatting.YELLOW + "[编辑模式] 点击编辑此条目（" + entry.getId() + "）"));
            }
        })
            .tooltipAutoUpdate(true);
        hitLayer.onMouseTapped(mouse -> {
            if (mouse != 0 || editCallback == null || !editCallback.isEditMode()) return false;
            int index = hitSlot(hitLayer);
            if (index < 0) return false; // 命中间隙：不消费，点击穿透
            LotteryClientData.PoolSummary pool = LotteryClientData.getSelectedPool();
            LotteryEntry entry = entryAt(index);
            if (pool != null && entry != null) {
                editCallback.onEditEntryRequested(pool, entry, index);
            }
            return true;
        });
        wheel.child(hitLayer);
        return wheel;
    }

    /** 当前选中池条目数（= 轮盘槽位数，钳制到 {@link #MAX_SLOTS}；空池返回 0） */
    private static int wheelSlotCount() {
        LotteryClientData.PoolSummary pool = LotteryClientData.getSelectedPool();
        if (pool == null || pool.entries == null) return 0;
        return Math.min(pool.entries.size(), MAX_SLOTS);
    }

    /**
     * 动态布局列数（N=条目数 1..10 查表）：
     * N=1→1；N=2/3/4→2（2x2）；N=5/6→3（3x2）；N=7..10→4（4x2 / 4x3）
     */
    private static int layoutCols(int n) {
        if (n <= 1) return 1;
        if (n <= 4) return 2;
        if (n <= 6) return 3;
        return 4;
    }

    /** 动态布局行数（与 {@link #layoutCols} 配套的查表）：N=1→1；N=2..8→2；N=9/10→3 */
    private static int layoutRows(int n) {
        if (n <= 1) return 1;
        if (n <= 8) return 2;
        return 3;
    }

    /**
     * 槽位左上角坐标（轮盘区相对像素）：矩形环周长等弧长采样。
     * <p>
     * 环宽 w=(cols-1)×28、环高 h=(rows-1)×28，周长 P=2×(w+h)；
     * 第 index 槽弧长 d=index×P/n，从矩形左上角沿边框顺时针走 d 像素定位
     * （上边左→右 → 右边上→下 → 下边右→左 → 左边下→上），
     * 与 {@link LotteryAnimationController} 步进方向一致（索引递增 = 顺时针绕圈）。
     * 整个环在轮盘区（PAGE_WIDTH × WHEEL_H）水平/垂直居中。
     * <p>
     * N=10（4x3 满环）时步长恰为 28px，逐格坐标与原固定 4 列环形布局重合（零回归）。
     *
     * @param index 槽位序号 [0, n)
     * @param n     槽位总数（= 条目数）
     * @return {x, y} 槽位左上角的轮盘区相对坐标
     */
    private static int[] slotTopLeft(int index, int n) {
        int stride = SLOT_SIZE + SLOT_GAP; // 28
        int cols = layoutCols(n);
        int rows = layoutRows(n);
        int w = (cols - 1) * stride; // 环宽（左右角槽左上角的横向距离）
        int h = (rows - 1) * stride; // 环高
        int perimeter = 2 * (w + h); // 环周长
        // 等弧长采样：第 index 槽的弧长位置（N=1 时周长 0，d=0 落原点即居中格）
        double d = perimeter == 0 ? 0 : (double) index * perimeter / n;
        double x;
        double y;
        if (d < w) {
            // 上边：左 → 右
            x = d;
            y = 0;
        } else if (d < w + h) {
            // 右边：上 → 下
            x = w;
            y = d - w;
        } else if (d < 2 * w + h) {
            // 下边：右 → 左
            x = w - (d - w - h);
            y = h;
        } else {
            // 左边：下 → 上
            x = 0;
            y = h - (d - 2 * w - h);
        }
        // 整环（含槽位自身 24px）在轮盘区居中
        int offsetX = (PAGE_WIDTH - (w + SLOT_SIZE)) / 2;
        int offsetY = (WHEEL_H - (h + SLOT_SIZE)) / 2;
        return new int[] { offsetX + (int) Math.round(x), offsetY + (int) Math.round(y) };
    }

    /**
     * 槽位命中测试：取当前鼠标所在的槽位序号（未命中/空池返回 -1）。
     * <p>
     * 用「绝对鼠标坐标 − 交互层区域绝对坐标」换算到轮盘区相对坐标（
     * {@link GuiContext#getAbsMouseX()} 与 {@code Area.x/y} 同坐标系，
     * 与框架自身 hover 判定同口径），再与各槽位矩形逐一比对。
     *
     * @param widget 整区交互层 widget（提供区域与上下文）
     * @return 槽位序号；未命中返回 -1
     */
    private static int hitSlot(IWidget widget) {
        int n = wheelSlotCount();
        if (n <= 0 || widget == null || widget.getArea() == null) return -1;
        GuiContext context = widget.getContext();
        if (context == null) return -1;
        int mx = context.getAbsMouseX() - widget.getArea().x;
        int my = context.getAbsMouseY() - widget.getArea().y;
        for (int i = 0; i < n; i++) {
            int[] tl = slotTopLeft(i, n);
            if (mx >= tl[0] && mx < tl[0] + SLOT_SIZE && my >= tl[1] && my < tl[1] + SLOT_SIZE) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 绘制轮盘指针（16x16 固定朝下倒三角纹理，不旋转）。
     * <p>
     * 定位（v1.7.9 恢复固定朝向——v1.7.8 的随槽位方向旋转方案观感不佳回退）：
     * <ul>
     * <li>pointerX = 槽位中心x − 8（16px 宽纹理相对格心水平居中）</li>
     * <li>pointerY = 槽位中心y − {@code SLOT_SIZE/2} − 16 + {@link #POINTER_DROP_PX}
     * （指针底边贴着格子上边，再往下偏移一点点，观感更贴合）</li>
     * </ul>
     * 直接按坐标绘制，不动矩阵栈与 blend/texture 状态。
     *
     * @param litTl 点亮格左上角坐标（{@link #slotTopLeft} 结果）
     */
    private static void drawPointer(GuiContext context, WidgetTheme theme, int[] litTl) {
        int slotCx = litTl[0] + SLOT_SIZE / 2;
        int slotCy = litTl[1] + SLOT_SIZE / 2;
        int pointerX = slotCx - 8;
        int pointerY = slotCy - SLOT_SIZE / 2 - 16 + POINTER_DROP_PX;
        NekoGuiTextures.LOTTERY_POINTER.draw(context, pointerX, pointerY, 16, 16, theme);
    }

    /**
     * 槽位底纹理：按条目稀有度（COMMON/RARE→灰框，EPIC→金框，LEGENDARY→闪角框）。
     * <p>
     * v1.7.8 动态布局后槽位数恒等于条目数，条目缺失（理论不可达）时回退普通底图兜底。
     */
    private static IDrawable slotTexture(int index) {
        LotteryEntry entry = entryAt(index);
        if (entry == null) return NekoGuiTextures.LOTTERY_SLOT_NORMAL; // 防御兜底
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

    /** 稀有度角标纹理（RARE→蓝，EPIC→紫，LEGENDARY→橙，COMMON→空） */
    private static IDrawable cornerTexture(int index) {
        LotteryEntry entry = entryAt(index);
        if (entry == null) return IDrawable.EMPTY;
        switch (entry.getRarity()) {
            case RARE:
                return NekoGuiTextures.LOTTERY_CORNER_BLUE;
            case EPIC:
                return NekoGuiTextures.LOTTERY_CORNER_PURPLE;
            case LEGENDARY:
                return NekoGuiTextures.LOTTERY_CORNER_ORANGE;
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
     * <li>若 {@link LotteryClientData#hasUnconsumedResult()} 有新结果 → 启动动画</li>
     * <li>动画到达 {@link LotteryAnimationController#isFinished()} 后再调用
     * {@link LotteryClientData#consumeDrawResult()}，确保结果/物品提示在动画完全停止后输出</li>
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
            long resultTimeMs = LotteryClientData.getLastResultTimeMs();

            if (anim.isFinished() && anim.getAnimatingResultTimeMs() == resultTimeMs) {
                // v1.7.7 G3：动画已完整停止，且停格对应本次结果，才消费结果
                // 保持 FINISHED 态供结果展示区读取，consume 仅防止同一结果重复触发动画
                LotteryClientData.consumeDrawResult();
                // v1.7.8 门控：停格消费即揭示（展示窗口自此上升沿起算）
                markResultRevealed(resultTimeMs);
            } else if (!anim.isSpinning()) {
                // IDLE 或旧动画已停格：启动新动画（跨池/槽位无效时直接消费，避免无限挂起）
                if (slotCount > 0 && poolId.equals(LotteryClientData.getSelectedPoolId())) {
                    anim.startAnimation(poolId, target, slotCount, quick);
                    anim.setAnimatingResultTimeMs(resultTimeMs);
                } else {
                    // 无动画路径：直接消费并立即揭示（无剧透对象）
                    LotteryClientData.consumeDrawResult();
                    markResultRevealed(resultTimeMs);
                }
            }
            // SPINNING 期间不处理：等待动画自然结束，避免提前消费导致结果先出
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
     * <p>
     * v1.7.8 门控：成功结果未揭示（动画旋转中）时显示「抽取中...」，
     * 揭示后展示窗口自揭示时刻（{@link #resultRevealedAtMs}）起算；
     * 失败结果码无动画，窗口自结果到达时刻起算。
     */
    private static IWidget createResultMessage() {
        return new TextWidget<>(IKey.dynamic(() -> {
            int code = LotteryClientData.getLastResultCode();
            if (code == LotteryClientData.RESULT_NONE) return "";
            if (code == LotteryClientData.RESULT_SUCCESS) {
                // 成功：未揭示期间显示「抽取中...」（动画期间不设窗口限制）；
                // 揭示后窗口自揭示时刻起算
                if (isResultRevealed() && System.currentTimeMillis() - resultRevealedAtMs > RESULT_DISPLAY_MS) {
                    return "";
                }
                return successSummary();
            }
            // 失败码：无动画，窗口自结果到达时刻起算
            if (System.currentTimeMillis() - LotteryClientData.getLastResultTimeMs() > RESULT_DISPLAY_MS) return "";
            switch (code) {
                case LotteryClientData.RESULT_INSUFFICIENT:
                    return EnumChatFormatting.RED + "消耗不足（猫猫币或需求物品不够），无法抽取";
                case LotteryClientData.RESULT_POOL_MISSING:
                    return EnumChatFormatting.RED + "卡池不存在或暂不可用";
                case LotteryClientData.RESULT_ERROR:
                    return EnumChatFormatting.RED + "抽奖失败，请稍后再试";
                default:
                    return "";
            }
        })).pos(0, RESULT_Y)
            .size(PAGE_WIDTH, 12)
            .textAlign(Alignment.Center)
            .shadow(false);
    }

    /**
     * 成功结果汇总文本（单抽=中奖条目，10 连=最高稀有度条目）
     * <p>
     * v1.7.8 门控：结果未揭示（动画旋转中）时仅显示「抽取中...」，停格揭示后才展示条目。
     */
    private static String successSummary() {
        List<LotteryClientData.DrawResult> results = LotteryClientData.getLastResults();
        if (results.isEmpty()) return "";
        if (!isResultRevealed()) return EnumChatFormatting.YELLOW + "抽取中...";
        if (results.size() > 1) {
            // 10 连：揭示后提示最高奖（列表区已展示全部）
            LotteryClientData.DrawResult best = bestResult(results);
            return resultText(best, EnumChatFormatting.GOLD + "10 连最高奖：");
        }
        // 单抽：揭示后展示
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
     * <p>
     * v1.7.8 门控：{@link #multiResultAt(int)} 在结果揭示前一律返回 null
     * （动画旋转期间列表整体为空，修 10 连剧透），展示窗口自揭示时刻起算。
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

            // 高稀有度高亮框（揭示后 ≥EPIC 格金色描边；multiResultAt 未揭示返回 null 已含门控）
            list.child(new IDrawable.DrawableWidget((context, dx, dy, w, h, theme) -> {
                LotteryClientData.DrawResult result = multiResultAt(index);
                if (result == null || !result.isHighRarity) return;
                drawFrame(dx, dy, w, h, COLOR_FINISH_FRAME);
            }).pos(x - 1, y - 1)
                .size(LIST_CELL + 2, LIST_CELL + 2));
        }
        return list;
    }

    /**
     * 取第 index 条 10 连结果（非 10 连/越界/超时返回 null）
     * <p>
     * v1.7.8 门控：结果未揭示（动画旋转中）返回 null——修 10 连列表在轮盘
     * 停格前逐格展示奖品的剧透问题；展示窗口自揭示时刻（{@link #resultRevealedAtMs}）起算。
     */
    private static LotteryClientData.DrawResult multiResultAt(int index) {
        if (LotteryClientData.getLastResultCode() != LotteryClientData.RESULT_SUCCESS) return null;
        if (!isResultRevealed()) return null; // 未揭示：列表整体不展示（防剧透）
        if (System.currentTimeMillis() - resultRevealedAtMs > RESULT_DISPLAY_MS) return null;
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
     * <p>
     * v1.7.6 G4：文本主体自 GRAY 调深（0xFF3F3F3F，浅灰面板上 GRAY 过淡不可读），
     * 仅保底计数内嵌稀有度色，其后以 §r 恢复主体色（1.7.10 原版 §r→0x404040，
     * 与主体色一致；ModularUI2 富文本管线则回 widget color）。
     */
    private static IWidget createPityProgress() {
        ParentWidget<?> box = new ParentWidget<>().pos(0, PITY_Y)
            .size(PAGE_WIDTH, 12);

        // 文本行
        box.child(new TextWidget<>(IKey.dynamic(() -> {
            LotteryClientData.PoolSummary pool = LotteryClientData.getSelectedPool();
            if (pool == null) return "";
            if (!pool.pityEnabled || pool.hardPityThreshold <= 0) {
                return "本池无保底"; // 主体走 widget color（0xFF3F3F3F）
            }
            int pity = LotteryClientData.getPityCounter(pool.id);
            LotteryRarity guaranteed = LotteryRarity.fromString(pool.guaranteedRarity);
            // 主体不带格式码（走 widget color 深灰），仅保底计数内嵌稀有度色，§r 恢复主体色
            return "距保底 " + guaranteed.getColor()
                + pity
                + "/"
                + pool.hardPityThreshold
                + EnumChatFormatting.RESET
                + "（必出"
                + guaranteed.getDisplayName()
                + "+）";
        })).pos(0, 0)
            .size(PAGE_WIDTH, 8)
            .textAlign(Alignment.Center)
            .scale(0.75f)
            .color(0xFF3F3F3F)
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

        row.child(createDrawButton(machine, startX, 1, NekoGuiTextures.LOTTERY_BTN_DRAW, "抽1次", editCallback));
        row.child(
            createDrawButton(
                machine,
                startX + DRAW_BTN_W + DRAW_BTN_GAP,
                10,
                NekoGuiTextures.LOTTERY_BTN_DRAW10,
                "抽10次",
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
                // v1.7.6 costItems 口径：逐条列出消耗（货币条目带团队余额）
                t.addLine(IKey.str(EnumChatFormatting.YELLOW + label + " 消耗："));
                boolean anyCost = false;
                for (com.miaokatze.gtit.trade.v2.NekoBigItemStack cost : pool.costItems) {
                    if (cost == null || cost.getBaseStack() == null || cost.getStackSize() <= 0) continue;
                    anyCost = true;
                    int total = cost.getStackSize() * count;
                    String cid = NekoCurrencyRegistrar.getNekoCurrencyId(cost.getBaseStack());
                    if (cid != null) {
                        int balance = NekoClientBalances.getBalance(cid);
                        EnumChatFormatting color = balance < total ? EnumChatFormatting.RED : EnumChatFormatting.GRAY;
                        t.addLine(IKey.str(color + "  " + total + " " + currencyName(cid) + "（钱包 " + balance + "）"));
                    } else {
                        t.addLine(
                            IKey.str(
                                EnumChatFormatting.GRAY + "  "
                                    + total
                                    + " "
                                    + cost.getBaseStack()
                                        .getDisplayName()
                                    + EnumChatFormatting.DARK_GRAY
                                    + "（自输入槽）"));
                    }
                }
                if (!anyCost) {
                    t.addLine(IKey.str(EnumChatFormatting.GREEN + "  免费"));
                }
                if (!currencyAffordable(pool, count)) {
                    t.addLine(IKey.str(EnumChatFormatting.RED + "团队钱包余额不足"));
                } else {
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
                if (!currencyAffordable(pool, count)) {
                    // 货币不足：本地拦截（物品消耗由服务端 canAfford 二次校验兜底）
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

    /**
     * 抽奖按钮文本（选中池无数据时置灰；货币不足时红色警示）
     * <p>
     * v1.7.6 G4：按钮加宽至 72 且文案缩写（label 去空格、货币名单字缩写）后，
     * 仍按 {@link #DRAW_BTN_TEXT_BUDGET} 显示列预算截断兜底（超长加「…」），
     * 根除价格文本溢出与相邻按钮重叠的问题。
     */
    private static String drawButtonText(String label, int count) {
        LotteryClientData.PoolSummary pool = LotteryClientData.getSelectedPool();
        if (pool == null) return EnumChatFormatting.DARK_GRAY + label;
        EnumChatFormatting color = currencyAffordable(pool, count) ? EnumChatFormatting.WHITE : EnumChatFormatting.RED;
        return color + truncateToWidth(label + " " + costSummary(pool, count), DRAW_BTN_TEXT_BUDGET);
    }

    /**
     * 按显示列截断文本（全角字符计 2 列，半角计 1 列；超预算时截断并补「…」）
     * <p>
     * MC Unicode 字体全角字约 9px、半角约 4~5px，列是估算单位（约 4.5px/列）。
     * 输入文本不应含 § 格式码（调用方在截断后再拼接颜色码）。
     *
     * @param text      纯文本
     * @param maxColumn 最大显示列数（含省略号预算）
     * @return 截断后的文本
     */
    private static String truncateToWidth(String text, int maxColumn) {
        // 第一遍：总列数未超预算则不截断（避免临界长度被过度截断）
        int total = 0;
        for (int i = 0; i < text.length(); i++) {
            total += text.charAt(i) > 0xFF ? 2 : 1; // 全角（CJK 等）计 2 列，半角计 1 列
        }
        if (total <= maxColumn) return text;
        // 第二遍：截到 maxColumn-2 列（为「…」预留 2 列预算）
        int columns = 0;
        for (int i = 0; i < text.length(); i++) {
            int w = text.charAt(i) > 0xFF ? 2 : 1;
            if (columns + w > maxColumn - 2) {
                return text.substring(0, i) + "…";
            }
            columns += w;
        }
        return text; // 理论不可达（total > maxColumn 时必在循环内截断）
    }
}

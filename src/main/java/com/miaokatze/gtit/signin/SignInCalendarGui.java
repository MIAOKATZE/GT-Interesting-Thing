package com.miaokatze.gtit.signin;

import java.util.Calendar;
import java.util.List;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumChatFormatting;

import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.drawable.DynamicDrawable;
import com.cleanroommc.modularui.drawable.GuiDraw;
import com.cleanroommc.modularui.drawable.UITexture;
import com.cleanroommc.modularui.screen.RichTooltip;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.value.StringValue;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widget.Widget;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.PagedWidget;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;
import com.miaokatze.gtit.client.gui.NekoGuiTextures;
import com.miaokatze.gtit.trade.NekoCurrencyRegistrar;
import com.miaokatze.gtit.util.NbtBase64Util;

/**
 * 签到「活跃」大页 GUI（v1.7.6 G2③ 重构：4 sub-pages）
 * <p>
 * 作为 {@code NekoVMGuiV2} 主内容 {@code PagedWidget} 的「签到」页嵌入（页索引 1）。
 * 页面结构（v1.7.6 重构）：
 * <ul>
 * <li>顶部公共标题行（「每日活跃」+ 编辑模式标识）</li>
 * <li>内层 {@code PagedWidget}（绑定 {@code NekoVMGuiV2#signInPageController}，4 个 sub-page）：
 * <ol>
 * <li>每月签到：月历 6 行 7 列 + 状态行 + 签到按钮（连续奖励预览拆至页 2）</li>
 * <li>连续签到：连续进度文本/进度条 + 阶梯宝箱预览（自 v1.7.5 日历页底部拆出，
 * 根除原签到按钮底 296/阶梯预览底 254 被背包行遮挡的问题）</li>
 * <li>每日在线：今日在线时长 + 档位奖励领取（{@link OnlineTimeConfig} 档位，
 * 默认 30min/2h/5h = 5/20/50 猫猫币）</li>
 * <li>纪念日：首次入服时间（只读）+ 生日配置 + 自定义纪念日列表（增删，最多
 * {@value DailySignInManager#MAX_ANNIVERSARIES} 条）</li>
 * </ol>
 * 左侧 sub-tab 标签列由 {@code NekoVMGuiV2#createSubTabColumn} 提供（与本 PagedWidget 同控制器）。</li>
 * </ul>
 * <p>
 * <b>布局约束</b>：背包行顶部在主内容区 Y≈231（G1 用户裁决），本页全部交互内容
 * 收进 Y≤{@value #CONTENT_BOTTOM}（内层 PagedWidget 高 {@value #INNER_HEIGHT}），
 * 避免被背包行遮挡拦截点击。
 * <p>
 * <b>双端安全</b>：本类整条调用链仅客户端构建（{@code NekoVMGuiV2} 仅客户端创建主内容
 * PagedWidget）；所有动态 Supplier 仅在客户端渲染时求值，服务端构建时读取到的
 * {@link SignInClientData} 为默认值，不影响服务端逻辑——签到判定/在线累计/纪念日存取
 * 完全由 {@link DailySignInManager} 在服务端权威执行（玩家 UUID 维度）。
 * <p>
 * <b>配置口径</b>：阶梯奖励/在线档位预览统一读取 {@link SignInClientData}
 * （其内部优先使用服务端同步的配置快照，未同步时回退本地配置）。
 */
public class SignInCalendarGui {

    // ==================== 编辑模式回调（v1.7.0 目标 4） ====================

    /**
     * 签到页编辑模式回调接口
     * <p>
     * 由 {@code NekoVMGuiV2} 实现并传入，用于：
     * <ul>
     * <li>查询当前是否处于编辑模式（服务端权威，经同步值到客户端）</li>
     * <li>编辑模式下点击阶梯宝箱 → 打开阶梯编辑面板（连续/累计阶梯，null=新增槽）</li>
     * <li>编辑模式下点击每月签到日期格 → 打开逐日奖励编辑面板（v1.7.8 任务6）</li>
     * <li>编辑模式下点击「全局配置」按钮 → 打开每月全局编辑面板（递增开关/工作日/周末默认）</li>
     * </ul>
     * 编辑模式下签到按钮的签到交互被拦截（禁止常规操作）。
     */
    public interface SignInEditCallback {

        /**
         * 当前是否处于编辑模式
         *
         * @return true 表示处于编辑模式
         */
        boolean isEditMode();

        /**
         * 编辑模式下点击连续阶梯槽时触发
         *
         * @param tier 被点击的连续阶梯奖励；null 表示点击的是「+」新增槽（打开新增面板）
         */
        void onEditTierRequested(SignInRewardTier tier);

        /**
         * 编辑模式下点击累计阶梯槽时触发（v1.7.8 任务5）
         *
         * @param tier 被点击的累计阶梯奖励；null 表示点击的是「+」新增槽（打开新增面板）
         */
        void onEditCumulativeTierRequested(SignInRewardTier tier);

        /**
         * 编辑模式下点击每月签到日期格时触发（v1.7.8 任务6）
         *
         * @param date 被点击的日期（yyyy-MM-dd，当月有效日期）
         */
        void onEditDayRewardRequested(String date);

        /**
         * 编辑模式下点击「全局配置」按钮时触发
         */
        void onEditGlobalRequested();

        /**
         * 编辑模式下点击每日在线档位行时触发
         *
         * @param tier 被点击的在线奖励档位
         */
        void onEditOnlineTierRequested(OnlineTimeRewardTier tier);
    }

    // ==================== 布局常量 ====================

    /** 页面宽度（主内容区 = PANEL_WIDTH - 8） */
    private static final int PAGE_WIDTH = 170;
    /** 页面高度（主内容区 = PANEL_HEIGHT - 8） */
    private static final int PAGE_HEIGHT = 312;

    /** 交互内容底部上限（背包行顶部 Y≈231，留 2px 余量；G1 用户裁决） */
    private static final int CONTENT_BOTTOM = 229;
    /** 内层 PagedWidget Y（标题行之下） */
    private static final int INNER_Y = 16;
    /** 内层 PagedWidget 高度（底部不超过 CONTENT_BOTTOM） */
    private static final int INNER_HEIGHT = CONTENT_BOTTOM - INNER_Y;

    // ---- 页 1「每月签到」布局（内层页面局部坐标，170 x INNER_HEIGHT） ----

    /** 日期格边长（与 cell_*.png 素材一致） */
    private static final int CELL_SIZE = 20;
    /** 日期格间距 */
    private static final int CELL_GAP = 2;
    /** 日历列数（一周 7 天） */
    private static final int CAL_COLS = 7;
    /** 日历行数（最多 6 行覆盖所有月份） */
    private static final int CAL_ROWS = 6;
    /** 日期格总数 */
    private static final int CAL_CELL_COUNT = CAL_COLS * CAL_ROWS;
    /** 日历网格总宽：7*20 + 6*2 = 152 */
    private static final int CAL_WIDTH = CAL_COLS * CELL_SIZE + (CAL_COLS - 1) * CELL_GAP;
    /** 日历网格左上角 X（水平居中） */
    private static final int CAL_X = (PAGE_WIDTH - CAL_WIDTH) / 2;
    /** 星期表头 Y（页内局部） */
    private static final int WEEKDAY_Y = 24;
    /** 日历网格 Y（页内局部；6 行结束于 35+132=167，底部为签到按钮留位） */
    private static final int CAL_Y = 35;

    /** 签到结果提示 Y（页内局部） */
    private static final int RESULT_Y = 172;
    /** 签到结果提示显示时长（毫秒） */
    private static final long RESULT_DISPLAY_MS = 5000L;

    /** 签到按钮宽（与 btn_claim.png 一致） */
    private static final int BTN_W = 48;
    /** 签到按钮高 */
    private static final int BTN_H = 24;
    /** 签到按钮 Y（页内局部；底部 184+24=208 不超过 INNER_HEIGHT） */
    private static final int BTN_Y = 184;

    // ---- 页 2「连续签到」布局（内层页面局部坐标） ----

    /** 进度条宽度 */
    private static final int PROGRESS_W = 150;
    /** 进度条 X（水平居中） */
    private static final int PROGRESS_X = (PAGE_WIDTH - PROGRESS_W) / 2;
    /** 进度文本 Y */
    private static final int PROGRESS_LABEL_Y = 14;
    /** 进度条 Y（v1.7.8 任务5：自 28 上移至 24，为下方累计签到区块腾位） */
    private static final int PROGRESS_Y = 24;

    /** 连续阶梯预览区 Y（v1.7.8 任务5：自 44 上移至 34，为累计区块腾位） */
    private static final int TIER_Y = 34;
    /** v1.7.7 G5①：阶梯预览物品槽边长 */
    private static final int TIER_SLOT_SIZE = 18;
    /** v1.7.7 G5①：阶梯预览每行槽位数 */
    private static final int TIER_SLOTS_PER_ROW = 8;
    /** v1.7.7 G5①：阶梯预览行数 */
    private static final int TIER_ROWS = 2;
    /** v1.7.7 G5①：阶梯预览槽间距 */
    private static final int TIER_SLOT_GAP = 1;
    /** v1.7.7 G5①：阶梯预览总槽位数（8×2=16） */
    private static final int TIER_SLOT_COUNT = TIER_SLOTS_PER_ROW * TIER_ROWS;
    /** v1.7.7 G5①：阶梯预览网格总宽 */
    private static final int TIER_GRID_W = TIER_SLOTS_PER_ROW * TIER_SLOT_SIZE
        + (TIER_SLOTS_PER_ROW - 1) * TIER_SLOT_GAP;
    /** v1.7.7 G5①：阶梯预览网格左上角 X（水平居中） */
    private static final int TIER_GRID_X = (PAGE_WIDTH - TIER_GRID_W) / 2;
    /** v1.7.7 G5①：阶梯预览网格总高 */
    private static final int TIER_GRID_H = TIER_ROWS * TIER_SLOT_SIZE + (TIER_ROWS - 1) * TIER_SLOT_GAP;

    // ---- 页 1「累计签到」区块布局（v1.7.8 任务5：连续区块下方新增） ----

    /** 累计签到标题 Y（连续网格底 34+37=71 之下） */
    private static final int CUM_TITLE_Y = 76;
    /** 累计进度文本 Y */
    private static final int CUM_LABEL_Y = 88;
    /** 累计阶梯预览区 Y */
    private static final int CUM_TIER_Y = 98;
    /** 规则说明文字起始 Y（累计网格底 98+37=135 之下；连续/累计各一行） */
    private static final int NOTE_Y = CUM_TIER_Y + TIER_GRID_H + 4;

    // ---- 页 3「每日在线」布局（内层页面局部坐标） ----

    /** 在线状态行 Y */
    private static final int ONLINE_STATUS_Y = 14;
    /** 档位行起始 Y */
    private static final int ONLINE_ROW_Y = 30;
    /** 档位行行高 */
    private static final int ONLINE_ROW_H = 26;
    /** 档位最大显示行数（超出配置行数截断显示） */
    private static final int ONLINE_MAX_ROWS = 5;
    /** 档位领取按钮宽（与 mail btn_claim.png 素材一致） */
    private static final int ONLINE_CLAIM_W = 48;
    /** 档位领取按钮高 */
    private static final int ONLINE_CLAIM_H = 16;

    // ---- 页 4「纪念日」布局（内层页面局部坐标） ----

    /** 纪念日条目最大显示行数（与 {@link DailySignInManager#MAX_ANNIVERSARIES} 一致） */
    private static final int ANNIV_MAX_ROWS = DailySignInManager.MAX_ANNIVERSARIES;
    /** 条目行起始 Y */
    private static final int ANNIV_ROW_Y = 60;
    /** 条目行行高 */
    private static final int ANNIV_ROW_H = 18;
    /** 添加区 Y */
    private static final int ANNIV_ADD_Y = 156;
    /** 输入框高 */
    private static final int FIELD_H = 14;

    /** 生日/名称/日期/年份输入限长（防恶意包，服务端同校验） */
    private static final int MAX_BIRTHDAY_LENGTH = 5;
    private static final int MAX_ANNIV_NAME_LENGTH = 16;
    private static final int MAX_ANNIV_YEAR_LENGTH = 4;

    // ==================== 颜色常量（ARGB） ====================

    /** 日期数字-普通（浅色格底上的深灰） */
    private static final int COLOR_DAY_NORMAL = 0xFF3F3F3F;
    /** 日期数字-过去未签到（中灰，v1.7.6 G4 自 0xFF777777 调深：格底为浅色，过浅不可读） */
    private static final int COLOR_DAY_MISSED = 0xFF555555;
    /** 日期数字-未来（浅灰，v1.7.6 G4 自 0xFF9A9A9A 调深；保持 NORMAL<MISSED<FUTURE 层次） */
    private static final int COLOR_DAY_FUTURE = 0xFF707070;
    /** 日期数字-今天（深紫，与今日格高亮边框呼应） */
    private static final int COLOR_DAY_TODAY = 0xFF7A1FA2;
    /** 日期数字-已签到（深棕，压在小猫爪印章上仍可读） */
    private static final int COLOR_DAY_SIGNED = 0xFF4A2F0A;
    /** 进度条底色 */
    private static final int COLOR_PROGRESS_BG = 0xFF1E1E2E;
    /** 进度条填充（金色） */
    private static final int COLOR_PROGRESS_FILL = 0xFFFFC84A;

    /** 星期表头（周一开头） */
    private static final String[] WEEKDAY_LABELS = { "一", "二", "三", "四", "五", "六", "日" };

    /** 当月信息缓存（按 yyyy-MM 缓存，跨月自动重算） */
    private static MonthInfo monthInfoCache;

    // ==================== 纪念日页草稿（客户端本地状态，GUI 重开按同步数据重置生日） ====================

    /** 草稿：生日输入框（MM-dd；构建页面时按同步数据初始化） */
    private static String birthdayDraft = "";
    /** 草稿：新纪念日名称 */
    private static String annivNameDraft = "";
    /** 草稿：新纪念日日期（MM-dd） */
    private static String annivDateDraft = "";
    /** 草稿：新纪念日年份（可选，4 位数字） */
    private static String annivYearDraft = "";

    private SignInCalendarGui() {}

    // ==================== 页面构建入口 ====================

    /**
     * 构建签到「活跃」大页（供 {@code NekoVMGuiV2} 主内容 PagedWidget 添加为页 1）
     *
     * @param editCallback  编辑模式回调（v1.7.0 目标 4）；null 表示不支持编辑模式
     * @param subController 活跃 sub-page 分页控制器（{@code NekoVMGuiV2#signInPageController}，
     *                      左侧 sub-tab 标签列与本 PagedWidget 共用）
     * @return 活跃页根 Widget（170x312，绝对布局）
     */
    public static IWidget createSignInPage(SignInEditCallback editCallback, PagedWidget.Controller subController) {
        ParentWidget<?> page = new ParentWidget<>().size(PAGE_WIDTH, PAGE_HEIGHT);

        page.child(createContainerTitle(editCallback)); // 公共标题（「每日活跃」+ 编辑标识）
        page.child(createSubPagedWidget(editCallback, subController)); // 内层 4 sub-page

        return page;
    }

    /**
     * 内层 sub-page PagedWidget：页 0=每月签到，页 1=连续签到，页 2=每日在线，页 3=纪念日
     */
    private static IWidget createSubPagedWidget(SignInEditCallback editCallback, PagedWidget.Controller subController) {
        PagedWidget<?> subPaged = new PagedWidget<>().name("signInSubPaged")
            .pos(0, INNER_Y)
            .size(PAGE_WIDTH, INNER_HEIGHT)
            .controller(subController);

        subPaged.addPage(createCalendarPage(editCallback)); // 页 0：每月签到
        subPaged.addPage(createConsecutivePage(editCallback)); // 页 1：连续签到
        subPaged.addPage(createOnlinePage(editCallback)); // 页 2：每日在线（v1.7.7 G5② 支持编辑回调）
        subPaged.addPage(createAnniversaryPage()); // 页 3：纪念日

        return subPaged;
    }

    // ==================== 公共标题 ====================

    /** 公共标题行：「每日活跃」（金色居中；编辑模式附加红色标识） */
    private static IWidget createContainerTitle(SignInEditCallback editCallback) {
        return new TextWidget<>(IKey.dynamic(() -> {
            String text = EnumChatFormatting.GOLD + "每日活跃";
            if (editCallback != null && editCallback.isEditMode()) {
                text += EnumChatFormatting.RED + " [编辑]";
            }
            return text;
        })).pos(0, 2)
            .size(PAGE_WIDTH, 12)
            .textAlign(Alignment.Center)
            .shadow(false);
    }

    // ==================== 页 0：每月签到 ====================

    /**
     * 每月签到页：月历（6 行 7 列）+ 状态行 + 签到按钮 + 结果提示
     * <p>
     * v1.7.6 拆页后本页只保留日历与签到按钮（连续奖励预览移至页 1），
     * 全部内容收进页内 Y≤{@link #INNER_HEIGHT}，不再被背包行遮挡。
     */
    private static IWidget createCalendarPage(SignInEditCallback editCallback) {
        ParentWidget<?> view = new ParentWidget<>().size(PAGE_WIDTH, INNER_HEIGHT);

        view.child(createMonthTitle()); // 月份标题（yyyy年M月）
        view.child(createStatusInfo()); // 累计/连续天数
        view.child(createWeekdayHeader()); // 星期表头
        view.child(createCalendarGrid(editCallback)); // 42 日期格（编辑模式可点击逐日编辑）
        view.child(createResultMessage()); // 签到结果提示（限时）
        view.child(createSignInButton(editCallback)); // 签到按钮（编辑模式拦截）
        view.child(createGlobalEditButton(editCallback)); // 全局配置按钮（仅编辑模式显示）

        return view;
    }

    /** 月份标题：「签到日历 yyyy年M月」（随服务端日期跨月自动刷新） */
    private static IWidget createMonthTitle() {
        return new TextWidget<>(IKey.dynamic(() -> {
            MonthInfo mi = getMonthInfo();
            return EnumChatFormatting.YELLOW + "签到日历 " + mi.year + "年" + mi.month + "月";
        })).pos(0, 0)
            .size(PAGE_WIDTH, 10)
            .textAlign(Alignment.Center)
            .shadow(false);
    }

    /** 状态行：累计签到 X 天 · 连续签到 Y 天 */
    private static IWidget createStatusInfo() {
        return new TextWidget<>(
            IKey.dynamic(
                () -> EnumChatFormatting.AQUA + "累计签到 "
                    + SignInClientData.getTotalDays()
                    + " 天"
                    + EnumChatFormatting.DARK_GRAY
                    + " · "
                    + EnumChatFormatting.GOLD
                    + "连续 "
                    + SignInClientData.getConsecutiveDays()
                    + " 天")).pos(0, 12)
                        .size(PAGE_WIDTH, 10)
                        .textAlign(Alignment.Center)
                        .shadow(false);
    }

    /** 星期表头：一~日，逐列对齐日期格（v1.7.6 G4：深灰替代 GRAY，浅灰面板上 GRAY 过淡不可读） */
    private static IWidget createWeekdayHeader() {
        ParentWidget<?> header = new ParentWidget<>().pos(0, WEEKDAY_Y)
            .size(PAGE_WIDTH, 9);
        for (int i = 0; i < CAL_COLS; i++) {
            int x = CAL_X + i * (CELL_SIZE + CELL_GAP);
            header.child(
                new TextWidget<>(IKey.str(WEEKDAY_LABELS[i])).pos(x, 0)
                    .size(CELL_SIZE, 9)
                    .textAlign(Alignment.Center)
                    .scale(0.75f)
                    .color(0xFF3F3F3F)
                    .shadow(false));
        }
        return header;
    }

    /**
     * 日历网格：42 格（6 行 × 7 列）
     * <p>
     * 每格四层（按添加顺序绘制）：格底纹理（动态）→ 猫爪印章（已签到时叠加）→ 日期数字
     * → 交互热区（tooltip 奖励预览；v1.7.8 任务6 起编辑模式下点击打开逐日奖励编辑）。
     * 日期分配依赖当月 1 号的星期偏移（{@link MonthInfo#firstOffset}），
     * 不属于当月的格子渲染为空（无纹理、无数字、无热区）。
     */
    private static IWidget createCalendarGrid(SignInEditCallback editCallback) {
        ParentWidget<?> grid = new ParentWidget<>().pos(0, 0)
            .size(PAGE_WIDTH, CAL_Y + CAL_ROWS * (CELL_SIZE + CELL_GAP));
        for (int i = 0; i < CAL_CELL_COUNT; i++) {
            final int index = i;
            int col = index % CAL_COLS;
            int row = index / CAL_COLS;
            int x = CAL_X + col * (CELL_SIZE + CELL_GAP);
            int y = CAL_Y + row * (CELL_SIZE + CELL_GAP);

            // 第 1 层：格底（普通/已签/今日/今日可领阶梯 四种纹理动态切换）
            grid.child(
                new IDrawable.DrawableWidget(new DynamicDrawable(() -> cellTexture(index))).pos(x, y)
                    .size(CELL_SIZE, CELL_SIZE));

            // 第 2 层：猫爪印章（仅已签到格显示，16x16 居中压底）
            Widget<?> stamp = NekoGuiTextures.SIGNIN_PAW_STAMP.asWidget()
                .pos(x + 2, y + 2)
                .size(16, 16);
            stamp.setEnabledIf(w -> {
                String date = cellDate(index);
                return !date.isEmpty() && SignInClientData.hasSigned(date);
            });
            grid.child(stamp);

            // 第 3 层：日期数字（颜色随格状态变化，顶层保证可读）
            grid.child(new TextWidget<>(IKey.dynamic(() -> {
                String date = cellDate(index);
                // substring(8) 取 "dd"，转 int 去掉前导零（"09" → "9"）
                return date.isEmpty() ? "" : String.valueOf(Integer.parseInt(date.substring(8)));
            })).pos(x, y)
                .size(CELL_SIZE, CELL_SIZE)
                .textAlign(Alignment.Center)
                .scale(0.8f)
                .color(() -> cellTextColor(index))
                .shadow(false));

            // 第 4 层：交互热区（tooltip 展示当日奖励预览；编辑模式点击打开逐日编辑面板）
            ButtonWidget<?> cellHitbox = new ButtonWidget<>().pos(x, y)
                .size(CELL_SIZE, CELL_SIZE)
                .background(IDrawable.EMPTY)
                .tooltipBuilder(t -> buildDayCellTooltip(t, index, editCallback))
                .tooltipAutoUpdate(true)
                .onMouseTapped(mouse -> {
                    if (mouse == 0 && editCallback != null && editCallback.isEditMode()) {
                        String date = cellDate(index);
                        if (!date.isEmpty()) {
                            editCallback.onEditDayRewardRequested(date);
                            return true;
                        }
                    }
                    return false;
                });
            // 非当月空格无交互（折叠隐藏，避免遮挡相邻格的渲染与点击）
            cellHitbox.setEnabledIf(w -> !cellDate(index).isEmpty());
            grid.child(cellHitbox);
        }
        return grid;
    }

    /**
     * 构建日期格 Tooltip（v1.7.8 任务6：当日奖励预览 + 编辑提示）
     * <p>
     * 展示该日生效奖励（逐日覆盖优先，否则按工作日/周末默认）；
     * 默认奖励且递增开关开启时附递增说明；编辑模式附点击编辑提示。
     *
     * @param t            Tooltip 对象
     * @param index        格子索引（0..41）
     * @param editCallback 编辑模式回调
     */
    private static void buildDayCellTooltip(RichTooltip t, int index, SignInEditCallback editCallback) {
        String date = cellDate(index);
        if (date.isEmpty()) return;
        MonthInfo mi = getMonthInfo();
        int day = Integer.parseInt(date.substring(8));
        boolean weekend = DailySignInConfig.isWeekend(date);
        t.addLine(IKey.str("" + EnumChatFormatting.GOLD + mi.month + "月" + day + "日" + (weekend ? "（周末）" : "（工作日）")));
        // 奖励读客户端缓存（服务端同步快照优先）
        boolean overridden = SignInClientData.hasDayOverride(date);
        SignInReward reward = SignInClientData.getEffectiveDayReward(date);
        // v1.7.29 统一文案为「奖励：」，覆盖/默认状态改用颜色区分
        if (overridden) {
            t.addLine(IKey.str(EnumChatFormatting.AQUA + "奖励：" + buildRewardText(reward)));
        } else {
            t.addLine(IKey.str(EnumChatFormatting.GRAY + "奖励：" + buildRewardText(reward)));
            // 递增提示：仅默认奖励参与递增（覆盖天不递增）
            if (SignInClientData.isIncrementEnabled()) {
                t.addLine(IKey.str(EnumChatFormatting.DARK_GRAY + "（货币量随连续天数递增）"));
            }
        }
        // v1.7.29 物品奖励逐行列出明细
        addRewardItemLines(t, reward);
        if (editCallback != null && editCallback.isEditMode()) {
            t.addLine(IKey.str(EnumChatFormatting.YELLOW + "[编辑模式] 点击编辑此日奖励"));
        }
    }

    /** 签到结果提示（限时 {@link #RESULT_DISPLAY_MS} 毫秒，超时自动消失） */
    private static IWidget createResultMessage() {
        return new TextWidget<>(IKey.dynamic(() -> {
            int result = SignInClientData.getLastResult();
            if (result == SignInClientData.RESULT_NONE) return "";
            if (System.currentTimeMillis() - SignInClientData.getLastResultTimeMs() > RESULT_DISPLAY_MS) {
                return "";
            }
            switch (result) {
                case SignInClientData.RESULT_SUCCESS -> {
                    String msg = EnumChatFormatting.GREEN + "签到成功！+" + SignInClientData.getLastResultReward() + " 猫猫币";
                    if (SignInClientData.getLastResultTierDays() > 0) {
                        msg += EnumChatFormatting.GOLD + "（达成 " + SignInClientData.getLastResultTierDays() + " 天阶梯宝箱！）";
                    }
                    return msg;
                }
                case SignInClientData.RESULT_ALREADY_SIGNED -> {
                    return EnumChatFormatting.YELLOW + "今日已签到，明天再来吧";
                }
                default -> {
                    return EnumChatFormatting.RED + "签到失败，请稍后重试";
                }
            }
        })).pos(0, RESULT_Y)
            .size(PAGE_WIDTH, 10)
            .textAlign(Alignment.Center)
            .shadow(false);
    }

    /**
     * 签到按钮
     * <p>
     * 未签到时可点击：通过 {@link SignInNetworkManager#sendSignInRequest()} 向服务端发请求，
     * 结果由服务端同步包回推并刷新本页（含结果提示条）。
     * 已签到后按钮文字变灰且点击无效；tooltip 展示今日可得奖励预览。
     * <p>
     * <b>编辑模式</b>（v1.7.0 目标 4）：点击被拦截（禁止常规签到交互），
     * tooltip 提示处于编辑模式。
     *
     * @param editCallback 编辑模式回调；null 表示不支持编辑模式
     */
    private static IWidget createSignInButton(SignInEditCallback editCallback) {
        return new ButtonWidget<>().pos((PAGE_WIDTH - BTN_W) / 2, BTN_Y)
            .size(BTN_W, BTN_H)
            .background(NekoGuiTextures.SIGNIN_BTN_CLAIM)
            .overlay(
                IKey.dynamic(
                    () -> SignInClientData.hasSignedToday() ? EnumChatFormatting.GRAY + "已签到"
                        : EnumChatFormatting.WHITE + "签到"))
            .tooltipBuilder(t -> {
                // 编辑模式：仅提示，不展示奖励预览
                if (editCallback != null && editCallback.isEditMode()) {
                    t.addLine(IKey.str(EnumChatFormatting.RED + "[编辑模式] 签到交互已禁用"));
                    t.addLine(IKey.str(EnumChatFormatting.GRAY + "点击日期格/阶梯槽或「全局配置」按钮进行编辑"));
                    return;
                }
                if (SignInClientData.hasSignedToday()) {
                    t.addLine(IKey.str(EnumChatFormatting.GRAY + "今日已完成签到，明天再来吧"));
                } else {
                    int nextConsec = SignInClientData.getConsecutiveDays() + 1;
                    String today = SignInClientData.getToday();
                    // v1.7.8 任务6：每日货币量预览走新口径（覆盖天不递增；默认奖励按开关递增）
                    int reward = SignInClientData.calculateDayCurrency(today, nextConsec);
                    t.addLine(IKey.str(EnumChatFormatting.GREEN + "点击签到，领取今日奖励"));
                    if (reward > 0) {
                        t.addLine(
                            IKey.str(EnumChatFormatting.GRAY + "每日奖励：" + reward + " 猫猫币（连续第 " + nextConsec + " 天）"));
                    }
                    // 当日物品奖励预览（覆盖/默认奖励中的物品列表）
                    SignInReward dayReward = SignInClientData.getEffectiveDayReward(today);
                    addRewardItemLines(t, dayReward);
                    // 连续阶梯触发提示
                    SignInRewardTier tier = SignInClientData.getTriggeredTier(nextConsec);
                    if (tier != null && !SignInClientData.hasClaimedTier(tier.getRequiredDays())) {
                        t.addLine(
                            IKey.str(EnumChatFormatting.GOLD + "今天签到可额外领取连续 " + tier.getRequiredDays() + " 天阶梯宝箱！"));
                    }
                    // v1.7.8 任务5：累计阶梯触发提示（累计天数签到后 +1）
                    SignInRewardTier cumTier = SignInClientData
                        .getTriggeredCumulativeTier(SignInClientData.getTotalDays() + 1);
                    if (cumTier != null && !SignInClientData.hasClaimedCumulativeTier(cumTier.getRequiredDays())) {
                        t.addLine(
                            IKey.str(EnumChatFormatting.GOLD + "今天签到可额外领取累计 " + cumTier.getRequiredDays() + " 天奖励！"));
                    }
                }
            })
            .tooltipAutoUpdate(true)
            .onMouseTapped(mouse -> {
                // 编辑模式：拦截签到交互（禁止常规操作）
                if (editCallback != null && editCallback.isEditMode()) {
                    return mouse == 0;
                }
                // 仅左键且今日未签到时发请求；服务端兜底重复校验，双击安全
                if (mouse == 0 && !SignInClientData.hasSignedToday()) {
                    SignInNetworkManager.sendSignInRequest();
                    return true;
                }
                return false;
            });
    }

    /**
     * 「全局配置」按钮（仅编辑模式显示）
     * <p>
     * 点击通过 {@link SignInEditCallback#onEditGlobalRequested()} 打开每月全局编辑面板
     * （递增开关/系数 + 工作日/周末默认奖励）。非编辑模式下按钮隐藏（setEnabledIf 动态折叠）。
     *
     * @param editCallback 编辑模式回调；null 时按钮恒隐藏
     */
    private static IWidget createGlobalEditButton(SignInEditCallback editCallback) {
        ButtonWidget<?> button = new ButtonWidget<>().pos((PAGE_WIDTH - BTN_W) / 2 + BTN_W + 6, BTN_Y)
            .size(46, BTN_H)
            .background(NekoGuiTextures.TEXT_FIELD_BACKGROUND)
            .overlay(IKey.str(EnumChatFormatting.YELLOW + "全局配置"))
            .tooltipBuilder(t -> {
                t.addLine(IKey.str(EnumChatFormatting.GOLD + "编辑每月签到全局配置"));
                t.addLine(IKey.str(EnumChatFormatting.GRAY + "递增开关/系数与工作日/周末默认奖励"));
            })
            .tooltipAutoUpdate(true)
            .onMouseTapped(mouse -> {
                if (mouse == 0 && editCallback != null && editCallback.isEditMode()) {
                    editCallback.onEditGlobalRequested();
                    return true;
                }
                return false;
            });
        // 仅编辑模式显示（非编辑模式折叠隐藏，回调为 null 时恒隐藏）
        button.setEnabledIf(w -> editCallback != null && editCallback.isEditMode());
        return button;
    }

    // ==================== 页 1：连续签到 ====================

    /**
     * 连续签到页：连续进度文本/进度条 + 连续阶梯预览 + 累计签到区块（v1.7.8 任务5）
     * <p>
     * v1.7.6 自日历页底部拆出独立成页（原区域底 254 被背包行遮挡），
     * 领取逻辑不变：达成连续/累计天数后签到自动发放，无需手动领取。
     * <p>
     * 布局（页内局部 Y）：连续标题 0 → 连续进度文本 {@value #PROGRESS_LABEL_Y} → 进度条
     * {@value #PROGRESS_Y} → 连续阶梯网格 {@value #TIER_Y} → 累计标题 {@value #CUM_TITLE_Y}
     * → 累计进度文本 {@value #CUM_LABEL_Y} → 累计阶梯网格 {@value #CUM_TIER_Y}
     * → 规则说明 {@link #NOTE_Y} 起两行。
     */
    private static IWidget createConsecutivePage(SignInEditCallback editCallback) {
        ParentWidget<?> view = new ParentWidget<>().size(PAGE_WIDTH, INNER_HEIGHT);

        view.child(
            new TextWidget<>(IKey.str(EnumChatFormatting.GOLD + "连续签到奖励")).pos(0, 0)
                .size(PAGE_WIDTH, 10)
                .textAlign(Alignment.Center)
                .shadow(false));
        view.child(createProgressLabel()); // 连续进度文本
        view.child(createProgressBar()); // 连续进度条
        view.child(createTierGrid(editCallback, false)); // 连续阶梯预览（编辑模式可点击）

        // ---- v1.7.8 任务5：累计签到区块（连续区块下方） ----
        view.child(
            new TextWidget<>(IKey.str(EnumChatFormatting.GOLD + "累计签到奖励")).pos(0, CUM_TITLE_Y)
                .size(PAGE_WIDTH, 10)
                .textAlign(Alignment.Center)
                .shadow(false));
        view.child(createCumulativeProgressLabel()); // 累计进度文本
        view.child(createTierGrid(editCallback, true)); // 累计阶梯预览（编辑模式可点击）

        // 规则说明（静态；v1.7.6 G4 自 GRAY 调深为 0xFF555555，浅灰面板上 GRAY 过淡）
        view.child(
            new TextWidget<>(IKey.str("连续/累计阶梯达成后，签到时自动发放")).pos(0, NOTE_Y)
                .size(PAGE_WIDTH, 9)
                .textAlign(Alignment.Center)
                .scale(0.75f)
                .color(0xFF555555)
                .shadow(false));
        view.child(
            new TextWidget<>(IKey.str("连续阶梯每月重置，累计阶梯永久限领一次")).pos(0, NOTE_Y + 10)
                .size(PAGE_WIDTH, 9)
                .textAlign(Alignment.Center)
                .scale(0.75f)
                .color(0xFF555555)
                .shadow(false));

        return view;
    }

    /**
     * 累计进度文本：当前累计天数与下一累计阶梯差距（v1.7.8 任务5）
     * <p>
     * 配色与 {@link #createProgressLabel()} 一致（主体深灰 + 差距天数 GOLD 高亮）。
     */
    private static IWidget createCumulativeProgressLabel() {
        return new TextWidget<>(IKey.dynamic(() -> {
            int total = SignInClientData.getTotalDays();
            // 读客户端缓存（服务端同步快照优先）
            SignInRewardTier next = SignInClientData.getNextCumulativeTier(total);
            if (next == null) {
                return EnumChatFormatting.GOLD + "累计签到 " + total + " 天 · 已达成全部累计奖励";
            }
            return "累计签到 " + total
                + " 天 · 距 "
                + next.getRequiredDays()
                + " 天奖励还差 "
                + EnumChatFormatting.GOLD
                + (next.getRequiredDays() - total)
                + EnumChatFormatting.RESET
                + " 天";
        })).pos(0, CUM_LABEL_Y)
            .size(PAGE_WIDTH, 10)
            .textAlign(Alignment.Center)
            .color(0xFF3F3F3F)
            .shadow(false);
    }

    /**
     * 连续进度文本：当前连续天数与下一阶梯差距
     * <p>
     * v1.7.6 G4：主体自 GRAY 调深（0xFF3F3F3F，浅灰面板上 GRAY 过淡不可读）；
     * 差距天数高亮自 YELLOW 改 GOLD（亮黄在浅底上过淡）。
     */
    private static IWidget createProgressLabel() {
        return new TextWidget<>(IKey.dynamic(() -> {
            int consecutive = SignInClientData.getConsecutiveDays();
            // 读客户端缓存（服务端同步快照优先），不直接读本地配置
            SignInRewardTier next = SignInClientData.getNextTier(consecutive);
            if (next == null) {
                return EnumChatFormatting.GOLD + "连续签到 " + consecutive + " 天 · 已达成全部阶梯奖励";
            }
            // 主体不带格式码（走 widget color 深灰），仅差距天数内嵌 GOLD 高亮，§r 恢复主体色
            // （1.7.10 原版 §r→0x404040，与主体色 0xFF3F3F3F 一致；否则尾部「 天」会继承 GOLD）
            return "连续签到 " + consecutive
                + " 天 · 距 "
                + next.getRequiredDays()
                + " 天宝箱还差 "
                + EnumChatFormatting.GOLD
                + (next.getRequiredDays() - consecutive)
                + EnumChatFormatting.RESET
                + " 天";
        })).pos(0, PROGRESS_LABEL_Y)
            .size(PAGE_WIDTH, 10)
            .textAlign(Alignment.Center)
            .color(0xFF3F3F3F)
            .shadow(false);
    }

    /**
     * 连续进度条（GuiDraw 自绘：深色底 + 金色填充）
     * <p>
     * 全部阶梯达成后恒为满格。
     */
    private static IWidget createProgressBar() {
        return new IDrawable.DrawableWidget((context, x, y, w, h, theme) -> {
            int consecutive = SignInClientData.getConsecutiveDays();
            // 读客户端缓存（服务端同步快照优先）
            SignInRewardTier next = SignInClientData.getNextTier(consecutive);
            float progress = next == null ? 1f : Math.min(1f, consecutive / (float) next.getRequiredDays());
            GuiDraw.drawRect(x, y, w, h, COLOR_PROGRESS_BG);
            int fillW = Math.round((w - 2) * progress);
            if (fillW > 0) {
                GuiDraw.drawRect(x + 1, y + 1, fillW, h - 2, COLOR_PROGRESS_FILL);
            }
        }).pos(PROGRESS_X, PROGRESS_Y)
            .size(PROGRESS_W, 8);
    }

    /**
     * 阶梯奖励预览网格：8×2 共 16 个物品槽（v1.7.8 任务5 泛化：连续/累计共用）
     * <p>
     * 每个槽位实时读取 {@link SignInClientData#getRewardTiers()}（连续）或
     * {@link SignInClientData#getCumulativeTiers()}（累计）动态渲染对应阶梯的第一个物品奖励；
     * 无物品奖励的阶梯显示为空槽底（tooltip 列出全部奖励内容）。
     * <p>
     * <b>编辑模式</b>：已有阶梯槽点击打开编辑面板；空槽点击打开新增面板（null 回调）。
     *
     * @param editCallback 编辑模式回调
     * @param cumulative   true=累计阶梯网格（{@link #CUM_TIER_Y}）；false=连续阶梯网格（{@link #TIER_Y}）
     */
    private static IWidget createTierGrid(SignInEditCallback editCallback, boolean cumulative) {
        ParentWidget<?> box = new ParentWidget<>().pos(0, cumulative ? CUM_TIER_Y : TIER_Y)
            .size(PAGE_WIDTH, TIER_GRID_H);

        for (int i = 0; i < TIER_SLOT_COUNT; i++) {
            final int slotIndex = i;
            int col = slotIndex % TIER_SLOTS_PER_ROW;
            int row = slotIndex / TIER_SLOTS_PER_ROW;
            int x = TIER_GRID_X + col * (TIER_SLOT_SIZE + TIER_SLOT_GAP);
            int y = row * (TIER_SLOT_SIZE + TIER_SLOT_GAP);

            box.child(createTierSlot(x, y, slotIndex, editCallback, cumulative));
        }
        return box;
    }

    /**
     * 单个阶梯预览槽（v1.7.8 任务5 泛化：连续/累计共用 + 统一奖励模型）
     * <p>
     * 动态读取对应索引的阶梯，绘制槽位底框 + 首个物品图标 + 数量文字；
     * 编辑模式下点击已有阶梯打开编辑面板，点击空槽打开新增面板。
     *
     * @param x            槽位在页内局部 X
     * @param y            槽位在页内局部 Y
     * @param slotIndex    槽位索引（对应 tiers 列表索引）
     * @param editCallback 编辑模式回调
     * @param cumulative   true=累计阶梯槽；false=连续阶梯槽
     * @return 槽位 Widget
     */
    private static IWidget createTierSlot(int x, int y, final int slotIndex, SignInEditCallback editCallback,
        boolean cumulative) {
        ParentWidget<?> slot = new ParentWidget<>().pos(x, y)
            .size(TIER_SLOT_SIZE, TIER_SLOT_SIZE);

        // 槽位底框（动态：有阶梯且未领取 = 高亮边框，否则 = 普通暗框）
        slot.child(new IDrawable.DrawableWidget((context, sx, sy, sw, sh, theme) -> {
            SignInRewardTier tier = tierAt(slotIndex, cumulative);
            boolean claimed = tier != null && isTierClaimed(tier.getRequiredDays(), cumulative);
            // 外框
            int borderColor = tier == null ? 0xFF555555 : (claimed ? 0xFF888888 : 0xFFFFC84A);
            GuiDraw.drawRect(sx, sy, sw, sh, borderColor);
            // 内底
            GuiDraw.drawRect(sx + 1, sy + 1, sw - 2, sh - 2, 0xFF2A2A3A);
        }).pos(0, 0)
            .size(TIER_SLOT_SIZE, TIER_SLOT_SIZE));

        // 物品图标 + 数量（动态：随配置同步实时刷新；显示奖励中的首个物品）
        slot.child(new IDrawable.DrawableWidget((context, sx, sy, sw, sh, theme) -> {
            SignInRewardTier tier = tierAt(slotIndex, cumulative);
            if (tier == null) return;
            ItemStack stack = resolveFirstItemStack(tier.getReward());
            if (stack == null) return;
            float z = context.getCurrentDrawingZ();
            GuiDraw.drawItem(stack, sx + 1, sy + 1, 16, 16, (int) z);
            // 数量文字（>1 时显示）
            if (stack.stackSize > 1) {
                GuiDraw.drawText(String.valueOf(stack.stackSize), sx + 10, sy + 9, 0.75f, 0xFFFFFFFF, true);
            }
        }).pos(0, 0)
            .size(TIER_SLOT_SIZE, TIER_SLOT_SIZE));

        // 条件文字（所需天数，显示在槽位底部中央，小字）
        slot.child(new TextWidget<>(IKey.dynamic(() -> {
            SignInRewardTier tier = tierAt(slotIndex, cumulative);
            return tier == null ? "" : String.valueOf(tier.getRequiredDays());
        })).pos(0, TIER_SLOT_SIZE - 7)
            .size(TIER_SLOT_SIZE, 7)
            .textAlign(Alignment.Center)
            .scale(0.65f)
            .color(0xFFAAAAAA)
            .shadow(false));

        // 点击区域（编辑模式：已有阶梯→编辑面板，空槽→新增面板；非编辑模式仅 tooltip）
        ButtonWidget<?> hitbox = new ButtonWidget<>().pos(0, 0)
            .size(TIER_SLOT_SIZE, TIER_SLOT_SIZE)
            .background(IDrawable.EMPTY)
            .tooltipBuilder(t -> buildTierSlotTooltip(t, slotIndex, editCallback, cumulative))
            .tooltipAutoUpdate(true)
            .onMouseTapped(mouse -> {
                if (mouse == 0 && editCallback != null && editCallback.isEditMode()) {
                    SignInRewardTier tier = tierAt(slotIndex, cumulative);
                    // tier 为 null 时表示「+」新增槽（回调 null 打开新增面板）
                    if (cumulative) {
                        editCallback.onEditCumulativeTierRequested(tier);
                    } else {
                        editCallback.onEditTierRequested(tier);
                    }
                    return true;
                }
                return false;
            });
        slot.child(hitbox);
        return slot;
    }

    /**
     * 构建阶梯槽位的 Tooltip（v1.7.8 任务5 泛化：连续/累计共用 + 奖励明细逐行列出）
     *
     * @param slotIndex    槽位索引
     * @param editCallback 编辑模式回调
     * @param cumulative   true=累计阶梯槽；false=连续阶梯槽
     */
    private static void buildTierSlotTooltip(RichTooltip t, int slotIndex, SignInEditCallback editCallback,
        boolean cumulative) {
        SignInRewardTier tier = tierAt(slotIndex, cumulative);
        if (tier == null) {
            if (editCallback != null && editCallback.isEditMode()) {
                t.addLine(IKey.str(EnumChatFormatting.YELLOW + "[编辑模式] 点击新增" + (cumulative ? "累计" : "连续") + "阶梯"));
            } else {
                t.addLine(IKey.str(EnumChatFormatting.GRAY + "无阶梯奖励"));
            }
            return;
        }
        t.addLine(
            IKey.str(EnumChatFormatting.GOLD + (cumulative ? "累计签到 " : "连续签到 ") + tier.getRequiredDays() + " 天奖励"));
        // 奖励明细：货币一行 + 物品逐行（统一奖励模型）
        addRewardLines(t, tier.getReward());
        if (editCallback != null && editCallback.isEditMode()) {
            t.addLine(IKey.str(EnumChatFormatting.YELLOW + "[编辑模式] 点击编辑此阶梯奖励"));
        } else if (isTierClaimed(tier.getRequiredDays(), cumulative)) {
            t.addLine(IKey.str(EnumChatFormatting.GREEN + (cumulative ? "已领取（永久限领一次）" : "本月已领取")));
        } else {
            t.addLine(IKey.str(EnumChatFormatting.GRAY + "达成条件后签到自动发放"));
        }
    }

    /** 按索引取阶梯奖励（cumulative 选择连续/累计列表；越界返回 null） */
    private static SignInRewardTier tierAt(int index, boolean cumulative) {
        List<SignInRewardTier> tiers = cumulative ? SignInClientData.getCumulativeTiers()
            : SignInClientData.getRewardTiers();
        return index >= 0 && index < tiers.size() ? tiers.get(index) : null;
    }

    /** 指定阶梯天数是否已领取（连续=当月口径；累计=永久口径） */
    private static boolean isTierClaimed(int days, boolean cumulative) {
        return cumulative ? SignInClientData.hasClaimedCumulativeTier(days) : SignInClientData.hasClaimedTier(days);
    }

    /**
     * 将奖励中的首个有效物品解析为显示用 ItemStack（v1.7.8 统一奖励模型）
     * <p>
     * 遍历 {@link SignInReward#getItems()} 取第一个非空条目解析；无有效物品或解析失败返回 null。
     *
     * @param reward 统一奖励模型
     * @return 物品栈（含 NBT），无物品/解析失败返回 null
     */
    private static ItemStack resolveFirstItemStack(SignInReward reward) {
        if (reward == null) return null;
        for (RewardItem item : reward.getItems()) {
            if (item == null || item.isEmpty()) continue;
            ItemStack stack = resolveRewardItemStack(item);
            if (stack != null) return stack;
        }
        return null;
    }

    /**
     * 将奖励物品条目解析为显示用 ItemStack（物品 ID + meta + NBT）
     *
     * @param rewardItem 奖励物品条目
     * @return 物品栈（含 NBT），解析失败返回 null
     */
    private static ItemStack resolveRewardItemStack(RewardItem rewardItem) {
        if (rewardItem == null || rewardItem.isEmpty()) return null;
        String[] parts = rewardItem.getItemId()
            .split(":");
        if (parts.length != 2) return null;
        Item item = cpw.mods.fml.common.registry.GameRegistry.findItem(parts[0], parts[1]);
        if (item == null) return null;
        ItemStack stack = new ItemStack(item, Math.max(1, rewardItem.getAmount()), rewardItem.getMeta());
        NBTTagCompound nbt = NbtBase64Util.nbtFromBase64(rewardItem.getNbtBase64());
        if (nbt != null) {
            // v1.7.7 G5①：copy() 返回 NBTBase，需强转为 NBTTagCompound 再写入物品
            stack.setTagCompound((NBTTagCompound) nbt.copy());
        }
        return stack;
    }

    // ==================== 页 2：每日在线 ====================

    /**
     * 每日在线页：今日在线时长 + 档位奖励领取
     * <p>
     * 档位列表读取 {@link SignInClientData#getOnlineRewardTiers()}（服务端配置快照优先），
     * 最多显示 {@value #ONLINE_MAX_ROWS} 行；领取动作经
     * {@link SignInNetworkManager#sendClaimOnline(int)} 发服务端权威校验
     * （达成校验 + 防重），结果由同步包回推刷新。
     * <p>
     * <b>v1.7.7 G5②</b>：编辑模式下档位行整行可点击，通过
     * {@link SignInEditCallback#onEditOnlineTierRequested} 打开在线档位编辑面板。
     *
     * @param editCallback 编辑模式回调；null 表示不支持编辑模式
     */
    private static IWidget createOnlinePage(SignInEditCallback editCallback) {
        ParentWidget<?> view = new ParentWidget<>().size(PAGE_WIDTH, INNER_HEIGHT);

        view.child(
            new TextWidget<>(IKey.str(EnumChatFormatting.GOLD + "每日在线奖励")).pos(0, 0)
                .size(PAGE_WIDTH, 10)
                .textAlign(Alignment.Center)
                .shadow(false));

        // 今日在线时长（动态：服务端每分钟累计同步）
        view.child(
            new TextWidget<>(
                IKey.dynamic(
                    () -> EnumChatFormatting.AQUA + "今日已在线 "
                        + formatDuration(SignInClientData.getOnlineSecondsToday()))).pos(0, ONLINE_STATUS_Y)
                            .size(PAGE_WIDTH, 10)
                            .textAlign(Alignment.Center)
                            .shadow(false));

        // 档位行（固定 ONLINE_MAX_ROWS 行，超出配置数量的行整体隐藏）
        for (int i = 0; i < ONLINE_MAX_ROWS; i++) {
            view.child(createOnlineTierRow(i, editCallback));
        }

        // 规则说明（静态灰字）
        view.child(
            new TextWidget<>(IKey.str(EnumChatFormatting.GRAY + "在线时长每日零点重置，奖励每日可领一轮")).pos(0, INNER_HEIGHT - 12)
                .size(PAGE_WIDTH, 9)
                .textAlign(Alignment.Center)
                .scale(0.75f)
                .shadow(false));

        return view;
    }

    /**
     * 在线档位行：宝箱图标 + 条件/奖励文本 + 领取按钮
     * <p>
     * <b>v1.7.7 G5②</b>：编辑模式下整行变为可点击编辑入口；非编辑模式下领取按钮行为不变。
     *
     * @param index        档位索引（{@link SignInClientData#getOnlineRewardTiers()} 列表序）
     * @param editCallback 编辑模式回调；null 表示不支持编辑模式
     */
    private static IWidget createOnlineTierRow(final int index, SignInEditCallback editCallback) {
        ParentWidget<?> row = new ParentWidget<>().pos(0, ONLINE_ROW_Y + index * ONLINE_ROW_H)
            .size(PAGE_WIDTH, ONLINE_ROW_H);

        // 宝箱图标（按档位序选取小/中/大宝箱）
        row.child(
            new IDrawable.DrawableWidget(new DynamicDrawable(() -> onlineChestFor(index))).pos(10, 0)
                .size(20, 20)
                .setEnabledIf(w -> onlineTier(index) != null));

        // 条件文本（在线 X 时间）
        row.child(new TextWidget<>(IKey.dynamic(() -> {
            OnlineTimeRewardTier tier = onlineTier(index);
            return tier == null ? "" : "在线 " + formatDuration(tier.getRequiredSeconds());
        })).pos(34, 2)
            .size(80, 9)
            .scale(0.8f)
            .color(0xFF3F3F3F)
            .shadow(false)
            .setEnabledIf(w -> onlineTier(index) != null));

        // 奖励文本（+N 猫猫币 / +物品）
        row.child(new TextWidget<>(IKey.dynamic(() -> {
            OnlineTimeRewardTier tier = onlineTier(index);
            return tier == null ? "" : buildOnlineRewardText(tier);
        })).pos(34, 12)
            .size(100, 8)
            .scale(0.7f)
            .color(0xFFB8860B)
            .shadow(false)
            .setEnabledIf(w -> onlineTier(index) != null));

        // 领取按钮（达成且未领取时可点击；文字随状态变化）
        row.child(
            new ButtonWidget<>().pos(PAGE_WIDTH - ONLINE_CLAIM_W - 4, 2)
                .size(ONLINE_CLAIM_W, ONLINE_CLAIM_H)
                .background(NekoGuiTextures.MAIL_BTN_CLAIM)
                .disableHoverBackground()
                .overlay(IKey.dynamic(() -> onlineClaimButtonText(index)))
                .tooltipBuilder(t -> buildOnlineTierTooltip(t, index, editCallback))
                .tooltipAutoUpdate(true)
                .onMouseTapped(mouse -> {
                    OnlineTimeRewardTier tier = onlineTier(index);
                    // 编辑模式：点击领取按钮也打开编辑面板（与整行点击口径一致）
                    if (mouse == 0 && editCallback != null && editCallback.isEditMode()) {
                        if (tier != null) {
                            editCallback.onEditOnlineTierRequested(tier);
                            return true;
                        }
                        return false;
                    }
                    // 非编辑模式：仅左键 + 达成 + 未领取时发请求；服务端兜底重复校验，双击安全
                    if (mouse == 0 && tier != null
                        && !SignInClientData.hasClaimedOnlineTier(tier.getRequiredSeconds())
                        && SignInClientData.getOnlineSecondsToday() >= tier.getRequiredSeconds()) {
                        SignInNetworkManager.sendClaimOnline(index);
                        return true;
                    }
                    return false;
                })
                .setEnabledIf(w -> onlineTier(index) != null));

        // 编辑模式整行点击热区（覆盖在领取按钮之外的行区域）
        ButtonWidget<?> rowHitbox = new ButtonWidget<>().pos(0, 0)
            .size(PAGE_WIDTH - ONLINE_CLAIM_W - 4, ONLINE_ROW_H)
            .background(IDrawable.EMPTY)
            .tooltipBuilder(t -> buildOnlineTierTooltip(t, index, editCallback))
            .tooltipAutoUpdate(true)
            .onMouseTapped(mouse -> {
                if (mouse == 0 && editCallback != null && editCallback.isEditMode()) {
                    OnlineTimeRewardTier tier = onlineTier(index);
                    if (tier != null) {
                        editCallback.onEditOnlineTierRequested(tier);
                        return true;
                    }
                }
                return false;
            });
        rowHitbox.setEnabledIf(w -> editCallback != null && editCallback.isEditMode() && onlineTier(index) != null);
        row.child(rowHitbox);

        return row;
    }

    /**
     * 构建在线档位行 Tooltip（v1.7.7 G5②）
     *
     * @param t            Tooltip 对象
     * @param index        档位索引
     * @param editCallback 编辑模式回调
     */
    private static void buildOnlineTierTooltip(RichTooltip t, int index, SignInEditCallback editCallback) {
        OnlineTimeRewardTier tier = onlineTier(index);
        if (tier == null) return;
        t.addLine(IKey.str(EnumChatFormatting.GOLD + "在线 " + formatDuration(tier.getRequiredSeconds()) + " 奖励"));
        t.addLine(IKey.str(buildOnlineRewardText(tier)));
        if (editCallback != null && editCallback.isEditMode()) {
            t.addLine(IKey.str(EnumChatFormatting.YELLOW + "[编辑模式] 点击编辑此在线档位"));
            return;
        }
        if (SignInClientData.hasClaimedOnlineTier(tier.getRequiredSeconds())) {
            t.addLine(IKey.str(EnumChatFormatting.GREEN + "今日已领取"));
        } else if (SignInClientData.getOnlineSecondsToday() >= tier.getRequiredSeconds()) {
            t.addLine(IKey.str(EnumChatFormatting.YELLOW + "点击领取"));
        } else {
            t.addLine(IKey.str(EnumChatFormatting.GRAY + "尚未达成，继续在线即可解锁"));
        }
    }

    /** 构建在线档位奖励文本（货币 + 可选物品标记） */
    private static String buildOnlineRewardText(OnlineTimeRewardTier tier) {
        String text = "+" + tier.getCurrencyAmount() + " " + NekoCurrencyRegistrar.getDisplayName(tier.getCurrencyId());
        if (tier.hasItemReward()) {
            text += " +物品";
        }
        return text;
    }

    /** 领取按钮文字：已领取（灰）/ 领取（白）/ 未达成（暗灰） */
    private static String onlineClaimButtonText(int index) {
        OnlineTimeRewardTier tier = onlineTier(index);
        if (tier == null) return "";
        if (SignInClientData.hasClaimedOnlineTier(tier.getRequiredSeconds())) {
            return EnumChatFormatting.GRAY + "已领取";
        }
        if (SignInClientData.getOnlineSecondsToday() >= tier.getRequiredSeconds()) {
            return EnumChatFormatting.WHITE + "领取";
        }
        return EnumChatFormatting.DARK_GRAY + "未达成";
    }

    /** 按索引取在线档位（越界返回 null，供各行 setEnabledIf 折叠） */
    private static OnlineTimeRewardTier onlineTier(int index) {
        List<OnlineTimeRewardTier> tiers = SignInClientData.getOnlineRewardTiers();
        return index >= 0 && index < tiers.size() ? tiers.get(index) : null;
    }

    /** 按档位序选取宝箱素材（第 1 档小宝箱 / 第 2 档中宝箱 / 其余大宝箱） */
    private static UITexture onlineChestFor(int index) {
        if (index <= 0) return NekoGuiTextures.SIGNIN_CHEST_7;
        if (index == 1) return NekoGuiTextures.SIGNIN_CHEST_14;
        return NekoGuiTextures.SIGNIN_CHEST_30;
    }

    // ==================== 页 3：纪念日 ====================

    /**
     * 纪念日页：首次入服时间（只读）+ 生日配置 + 自定义纪念日列表（增删）
     * <p>
     * 生日与纪念日配置均经 {@link SignInNetworkManager} 发服务端权威校验后落盘
     * （玩家 UUID 维度），同步包回推刷新本页。生日草稿在页面构建时按同步数据初始化，
     * 避免旧草稿覆盖服务端权威值。
     */
    private static IWidget createAnniversaryPage() {
        // 页面构建时按服务端同步数据初始化生日草稿（GUI 重开即与服务端一致）
        birthdayDraft = SignInClientData.getBirthday();

        ParentWidget<?> view = new ParentWidget<>().size(PAGE_WIDTH, INNER_HEIGHT);

        view.child(
            new TextWidget<>(IKey.str(EnumChatFormatting.GOLD + "纪念日")).pos(0, 0)
                .size(PAGE_WIDTH, 10)
                .textAlign(Alignment.Center)
                .shadow(false));

        // 首次入服时间（只读，服务端首次登录自动记录）
        view.child(new TextWidget<>(IKey.dynamic(() -> {
            String firstJoin = SignInClientData.getFirstJoinDate();
            return EnumChatFormatting.GRAY + "首次入服时间："
                + EnumChatFormatting.YELLOW
                + (firstJoin.isEmpty() ? "未知" : firstJoin);
        })).pos(0, 14)
            .size(PAGE_WIDTH, 10)
            .textAlign(Alignment.Center)
            .shadow(false));

        view.child(createBirthdayRow()); // 生日配置行
        view.child(createAnniversaryList()); // 自定义纪念日列表（5 行 + 删除）
        view.child(createAnniversaryAddRow()); // 添加行（名称/日期/年份 + 添加按钮）

        // 格式说明（静态灰字）
        view.child(
            new TextWidget<>(IKey.str(EnumChatFormatting.GRAY + "日期格式 MM-dd（如 05-20），年份可选填")).pos(0, INNER_HEIGHT - 12)
                .size(PAGE_WIDTH, 9)
                .textAlign(Alignment.Center)
                .scale(0.75f)
                .shadow(false));

        return view;
    }

    /** 生日配置行：标签 + 输入框（MM-dd）+ 保存按钮 */
    private static IWidget createBirthdayRow() {
        ParentWidget<?> row = new ParentWidget<>().pos(0, 30)
            .size(PAGE_WIDTH, FIELD_H + 2);

        row.child(
            new TextWidget<>(IKey.str(EnumChatFormatting.GRAY + "我的生日:")).pos(4, 4)
                .size(52, 9)
                .scale(0.8f)
                .shadow(false));

        TextFieldWidget birthdayField = new TextFieldWidget()
            .value(new StringValue.Dynamic(() -> birthdayDraft, val -> birthdayDraft = val))
            .setMaxLength(MAX_BIRTHDAY_LENGTH);
        birthdayField.pos(56, 0)
            .size(44, FIELD_H);
        birthdayField.tooltipBuilder(t -> {
            t.addLine(IKey.str("生日（MM-dd 格式，如 05-20）"));
            t.addLine(IKey.str(EnumChatFormatting.GRAY + "用于生日祝福，保存后次年生效"));
        });
        birthdayField.tooltipAutoUpdate(true);
        row.child(birthdayField);

        row.child(
            new ButtonWidget<>().pos(106, 0)
                .size(40, FIELD_H)
                .background(NekoGuiTextures.TEXT_FIELD_BACKGROUND)
                .overlay(IKey.str(EnumChatFormatting.WHITE + "保存"))
                .tooltipBuilder(t -> t.addLine(IKey.str(EnumChatFormatting.GRAY + "保存生日到服务器")))
                .tooltipAutoUpdate(true)
                .onMouseTapped(mouse -> {
                    // 客户端预校验格式（服务端仍权威校验，双重防线）
                    if (mouse == 0 && isValidMonthDay(birthdayDraft.trim())) {
                        SignInNetworkManager.sendSetBirthday(birthdayDraft.trim());
                        return true;
                    }
                    return false;
                }));

        return row;
    }

    /**
     * 自定义纪念日列表：最多 {@value #ANNIV_MAX_ROWS} 行，每行「名称 · 日期」+ 删除按钮
     * <p>
     * 行数据按索引动态读取 {@link SignInClientData#getAnniversaries()}，
     * 空槽位整体隐藏（setEnabledIf 折叠）。
     */
    private static IWidget createAnniversaryList() {
        ParentWidget<?> list = new ParentWidget<>().pos(0, 48)
            .size(PAGE_WIDTH, ANNIV_ROW_Y - 48 + ANNIV_MAX_ROWS * ANNIV_ROW_H);

        list.child(
            new TextWidget<>(IKey.str(EnumChatFormatting.GRAY + "自定义纪念日（最多 " + ANNIV_MAX_ROWS + " 条）:")).pos(4, 0)
                .size(PAGE_WIDTH - 8, 9)
                .scale(0.8f)
                .shadow(false));

        for (int i = 0; i < ANNIV_MAX_ROWS; i++) {
            final int index = i;
            int y = (ANNIV_ROW_Y - 48) + index * ANNIV_ROW_H;

            // 条目文本：名称 · 日期（有年份带年份）
            list.child(new TextWidget<>(IKey.dynamic(() -> {
                AnniversaryEntry entry = anniversaryAt(index);
                if (entry == null) return "";
                String date = entry.getYear() > 0 ? entry.getYear() + "-" + entry.getMonthDay() : entry.getMonthDay();
                return EnumChatFormatting.AQUA + entry
                    .getName() + EnumChatFormatting.DARK_GRAY + " · " + EnumChatFormatting.GOLD + date;
            })).pos(8, y + 3)
                .size(140, 9)
                .scale(0.8f)
                .shadow(false)
                .setEnabledIf(w -> anniversaryAt(index) != null));

            // 删除按钮（垃圾桶图标，点击发服务端删除请求）
            list.child(
                new ButtonWidget<>().pos(PAGE_WIDTH - 18, y + 2)
                    .size(12, 12)
                    .background(NekoGuiTextures.MAIL_TRASH)
                    .disableHoverBackground()
                    .tooltipBuilder(t -> t.addLine(IKey.str(EnumChatFormatting.RED + "删除该纪念日")))
                    .tooltipAutoUpdate(true)
                    .onMouseTapped(mouse -> {
                        if (mouse == 0 && anniversaryAt(index) != null) {
                            SignInNetworkManager.sendRemoveAnniversary(index);
                            return true;
                        }
                        return false;
                    })
                    .setEnabledIf(w -> anniversaryAt(index) != null));
        }
        return list;
    }

    /** 添加行：名称输入 + 日期输入（MM-dd）+ 年份输入（可选）+ 添加按钮 */
    private static IWidget createAnniversaryAddRow() {
        ParentWidget<?> row = new ParentWidget<>().pos(0, ANNIV_ADD_Y)
            .size(PAGE_WIDTH, FIELD_H + 2);

        // 名称输入
        TextFieldWidget nameField = new TextFieldWidget()
            .value(new StringValue.Dynamic(() -> annivNameDraft, val -> annivNameDraft = val))
            .setMaxLength(MAX_ANNIV_NAME_LENGTH);
        nameField.pos(4, 0)
            .size(62, FIELD_H);
        nameField.tooltipBuilder(t -> {
            t.addLine(IKey.str("纪念日名称"));
            t.addLine(IKey.str(EnumChatFormatting.GRAY + "最长 " + MAX_ANNIV_NAME_LENGTH + " 字符"));
        });
        nameField.tooltipAutoUpdate(true);
        row.child(nameField);

        // 日期输入（MM-dd）
        TextFieldWidget dateField = new TextFieldWidget()
            .value(new StringValue.Dynamic(() -> annivDateDraft, val -> annivDateDraft = val))
            .setMaxLength(MAX_BIRTHDAY_LENGTH);
        dateField.pos(70, 0)
            .size(38, FIELD_H);
        dateField.tooltipBuilder(t -> t.addLine(IKey.str("日期（MM-dd，如 05-20）")));
        dateField.tooltipAutoUpdate(true);
        row.child(dateField);

        // 年份输入（可选，4 位数字）
        TextFieldWidget yearField = new TextFieldWidget()
            .value(new StringValue.Dynamic(() -> annivYearDraft, val -> annivYearDraft = val))
            .setMaxLength(MAX_ANNIV_YEAR_LENGTH);
        yearField.pos(112, 0)
            .size(30, FIELD_H);
        yearField.tooltipBuilder(t -> {
            t.addLine(IKey.str("年份（可选，如 2024）"));
            t.addLine(IKey.str(EnumChatFormatting.GRAY + "留空表示不记年份"));
        });
        yearField.tooltipAutoUpdate(true);
        row.child(yearField);

        // 添加按钮（客户端预校验 + 服务端权威校验）
        row.child(
            new ButtonWidget<>().pos(146, 0)
                .size(20, FIELD_H)
                .background(NekoGuiTextures.TEXT_FIELD_BACKGROUND)
                .overlay(IKey.str(EnumChatFormatting.GREEN + "+"))
                .tooltipBuilder(t -> {
                    t.addLine(IKey.str(EnumChatFormatting.GREEN + "添加纪念日"));
                    t.addLine(IKey.str(EnumChatFormatting.GRAY + "名称必填，日期 MM-dd，年份可选"));
                })
                .tooltipAutoUpdate(true)
                .onMouseTapped(mouse -> {
                    if (mouse != 0) return false;
                    String name = annivNameDraft.trim();
                    String date = annivDateDraft.trim();
                    int year = parseYear(annivYearDraft.trim());
                    // 客户端预校验：名称非空 + 日期合法 + 年份合法（留空=0）
                    if (name.isEmpty() || !isValidMonthDay(date) || year < 0) {
                        return false;
                    }
                    SignInNetworkManager.sendAddAnniversary(name, date, year);
                    // 清空草稿（服务端同步回推后列表刷新；失败时服务端聊天提示，草稿已清可重输）
                    annivNameDraft = "";
                    annivDateDraft = "";
                    annivYearDraft = "";
                    return true;
                }));

        return row;
    }

    /** 按索引取纪念日（越界返回 null，供各行 setEnabledIf 折叠） */
    private static AnniversaryEntry anniversaryAt(int index) {
        List<AnniversaryEntry> entries = SignInClientData.getAnniversaries();
        return index >= 0 && index < entries.size() ? entries.get(index) : null;
    }

    /** 解析年份输入（空串=0 不记年份；合法 4 位数字=年份；非法=-1 拒绝提交） */
    private static int parseYear(String text) {
        if (text.isEmpty()) return 0;
        if (text.length() != 4) return -1;
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** MM-dd 格式客户端预校验（与服务端 {@link AnniversaryEntry} 校验口径一致） */
    private static boolean isValidMonthDay(String text) {
        if (text == null || text.length() != 5 || text.charAt(2) != '-') return false;
        try {
            int month = Integer.parseInt(text.substring(0, 2));
            int day = Integer.parseInt(text.substring(3, 5));
            return month >= 1 && month <= 12 && day >= 1 && day <= 31;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    // ==================== 日期格状态判定 ====================

    /**
     * 计算指定格子对应的日期字符串（yyyy-MM-dd）
     *
     * @param index 格子序号（0..41，先行后列）
     * @return 日期字符串；格子不属于当月时返回空串
     */
    private static String cellDate(int index) {
        MonthInfo mi = getMonthInfo();
        int day = index - mi.firstOffset + 1;
        if (day < 1 || day > mi.daysInMonth) return "";
        return String.format("%04d-%02d-%02d", mi.year, mi.month, day);
    }

    /**
     * 按格子状态选择底纹理
     * <ul>
     * <li>不属于当月的空格位 → {@link NekoGuiTextures#SIGNIN_CELL_NORMAL}（空槽底图：
     * v1.7.6 G4 修复——空位原不渲染底图导致日历视觉上行列散乱（首行仅 5 格），
     * 改为 42 格恒显底图，每行恒 7 格、首行按当月 1 号星期缩进）</li>
     * <li>已签到 → {@link NekoGuiTextures#SIGNIN_CELL_SIGNED}</li>
     * <li>今天未签到且签到后触发未领取阶梯 → {@link NekoGuiTextures#SIGNIN_CELL_REWARD}（礼物格）</li>
     * <li>今天未签到 → {@link NekoGuiTextures#SIGNIN_CELL_TODAY}</li>
     * <li>其他 → {@link NekoGuiTextures#SIGNIN_CELL_NORMAL}</li>
     * </ul>
     */
    private static IDrawable cellTexture(int index) {
        String date = cellDate(index);
        if (date.isEmpty()) return NekoGuiTextures.SIGNIN_CELL_NORMAL;
        if (SignInClientData.hasSigned(date)) return NekoGuiTextures.SIGNIN_CELL_SIGNED;
        String today = SignInClientData.getToday();
        if (date.equals(today)) {
            int nextConsec = SignInClientData.getConsecutiveDays() + 1;
            // 读客户端缓存（服务端同步快照优先）
            SignInRewardTier tier = SignInClientData.getTriggeredTier(nextConsec);
            if (tier != null && !SignInClientData.hasClaimedTier(tier.getRequiredDays())) {
                return NekoGuiTextures.SIGNIN_CELL_REWARD;
            }
            return NekoGuiTextures.SIGNIN_CELL_TODAY;
        }
        return NekoGuiTextures.SIGNIN_CELL_NORMAL;
    }

    /**
     * 按格子状态选择日期数字颜色（已签/今天/过去未签/未来/普通）
     */
    private static int cellTextColor(int index) {
        String date = cellDate(index);
        if (date.isEmpty()) return COLOR_DAY_NORMAL;
        if (SignInClientData.hasSigned(date)) return COLOR_DAY_SIGNED;
        String today = SignInClientData.getToday();
        if (date.equals(today)) return COLOR_DAY_TODAY;
        // yyyy-MM-dd 定长格式可直接按字典序比较先后
        if (date.compareTo(today) > 0) return COLOR_DAY_FUTURE;
        return COLOR_DAY_MISSED;
    }

    // ==================== 辅助方法 ====================

    /**
     * 获取当月信息（按 yyyy-MM 缓存；月份变化时自动重算）
     */
    private static MonthInfo getMonthInfo() {
        String today = SignInClientData.getToday();
        String ym = today.length() >= 7 ? today.substring(0, 7) : today;
        if (monthInfoCache == null || !monthInfoCache.yearMonth.equals(ym)) {
            monthInfoCache = MonthInfo.compute(today, ym);
        }
        return monthInfoCache;
    }

    /**
     * 构建奖励内容的紧凑单行文本（v1.7.8 统一奖励模型：货币 + 物品条数标记）
     * <p>
     * 用于日期格 tooltip 等单行场景；阶梯 tooltip 需完整明细时请用 {@link #addRewardLines}。
     *
     * @param reward 统一奖励模型（null 按空奖励）
     * @return 紧凑文本（如 "+10 猫猫币 +物品×2"；空奖励返回 "无奖励"）
     */
    private static String buildRewardText(SignInReward reward) {
        if (reward == null) return "无奖励";
        String text = "";
        if (reward.hasCurrency()) {
            text = "+" + reward.getCurrencyAmount()
                + " "
                + NekoCurrencyRegistrar.getDisplayName(reward.getCurrencyId());
        }
        int itemCount = 0;
        for (RewardItem item : reward.getItems()) {
            if (item != null && !item.isEmpty()) itemCount++;
        }
        if (itemCount > 0) {
            text += (text.isEmpty() ? "" : " ") + "+物品×" + itemCount;
        }
        return text.isEmpty() ? "无奖励" : text;
    }

    /**
     * 向 Tooltip 追加奖励明细行（货币一行 + 物品逐行带显示名）
     * <p>
     * 用于阶梯槽 tooltip 等需要完整明细的场景；空奖励追加「无奖励」一行。
     *
     * @param t      Tooltip 对象
     * @param reward 统一奖励模型（null 按空奖励）
     */
    private static void addRewardLines(RichTooltip t, SignInReward reward) {
        if (reward == null || (!reward.hasCurrency() && !reward.hasItems())) {
            t.addLine(IKey.str(EnumChatFormatting.GRAY + "无奖励"));
            return;
        }
        if (reward.hasCurrency()) {
            t.addLine(
                IKey.str(
                    EnumChatFormatting.GRAY + "+"
                        + reward.getCurrencyAmount()
                        + " "
                        + NekoCurrencyRegistrar.getDisplayName(reward.getCurrencyId())));
        }
        addRewardItemLines(t, reward);
    }

    /**
     * 向 Tooltip 追加奖励中的物品明细行（逐行 "+数量 × 物品显示名"）
     * <p>
     * 物品无法解析时回退显示注册名 ID。
     *
     * @param t      Tooltip 对象
     * @param reward 统一奖励模型（null 或无物品时不追加任何行）
     */
    private static void addRewardItemLines(RichTooltip t, SignInReward reward) {
        if (reward == null) return;
        for (RewardItem item : reward.getItems()) {
            if (item == null || item.isEmpty()) continue;
            ItemStack stack = resolveRewardItemStack(item);
            String name = stack != null ? stack.getDisplayName() : item.getItemId();
            t.addLine(IKey.str(EnumChatFormatting.GRAY + "+" + Math.max(1, item.getAmount()) + " × " + name));
        }
    }

    /**
     * 秒数时长的人性化文本（在线页状态/档位条件用）
     * <p>
     * 整小时→「N 小时」；不足 1 小时→「M 分钟」；其余→「N 小时 M 分钟」。
     */
    private static String formatDuration(int seconds) {
        if (seconds <= 0) return "0 分钟";
        int hours = seconds / 3600;
        int minutes = seconds % 3600 / 60;
        if (hours <= 0) return minutes + " 分钟";
        if (minutes == 0) return hours + " 小时";
        return hours + " 小时 " + minutes + " 分钟";
    }

    // ==================== 当月信息 ====================

    /**
     * 当月信息（年/月/天数/1 号星期偏移）
     * <p>
     * 以「今天」（{@link SignInClientData#getToday()}，服务端口径）为基准计算，
     * 保证日历月份与服务端签到记录一致。
     */
    private static final class MonthInfo {

        /** 年月标识（yyyy-MM，缓存比对用） */
        final String yearMonth;
        final int year;
        final int month;
        /** 当月天数 */
        final int daysInMonth;
        /** 当月 1 号的星期偏移（周一=0 … 周日=6） */
        final int firstOffset;

        private MonthInfo(String yearMonth, int year, int month, int daysInMonth, int firstOffset) {
            this.yearMonth = yearMonth;
            this.year = year;
            this.month = month;
            this.daysInMonth = daysInMonth;
            this.firstOffset = firstOffset;
        }

        /**
         * 由「今天」日期串计算当月信息；解析失败时回退到本地日期的当月
         */
        static MonthInfo compute(String today, String yearMonth) {
            Calendar cal = Calendar.getInstance();
            try {
                int year = Integer.parseInt(today.substring(0, 4));
                int month = Integer.parseInt(today.substring(5, 7));
                cal.clear();
                cal.set(year, month - 1, 1);
            } catch (Exception e) {
                // 容错：日期串异常时按本地当月渲染（正常不会发生）
                cal.setTimeInMillis(System.currentTimeMillis());
                cal.set(Calendar.DAY_OF_MONTH, 1);
            }
            int year = cal.get(Calendar.YEAR);
            int month = cal.get(Calendar.MONTH) + 1;
            int days = cal.getActualMaximum(Calendar.DAY_OF_MONTH);
            // Calendar.DAY_OF_WEEK：周日=1…周六=7 → 转为周一=0…周日=6
            int firstOffset = (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7;
            return new MonthInfo(yearMonth, year, month, days, firstOffset);
        }
    }
}

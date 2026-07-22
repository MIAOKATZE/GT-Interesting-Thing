package com.miaokatze.gtit.signin;

import java.util.Calendar;
import java.util.List;

import net.minecraft.util.EnumChatFormatting;

import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.drawable.DynamicDrawable;
import com.cleanroommc.modularui.drawable.GuiDraw;
import com.cleanroommc.modularui.drawable.UITexture;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widget.Widget;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.miaokatze.gtit.client.gui.NekoGuiTextures;
import com.miaokatze.gtit.trade.NekoCurrencyRegistrar;

/**
 * 签到日历 GUI（v1.7.0 目标 D）
 * <p>
 * 作为 {@code NekoVMGuiV2} 主内容 {@code PagedWidget} 的「签到」页嵌入（页索引 1）。
 * 采用绝对布局（{@link ParentWidget} + pos/size），全部数据读取自客户端缓存
 * {@link SignInClientData}（服务端通过 {@link SignInSyncPacket} 推送刷新）：
 * <ul>
 * <li>月历：42 个日期格（6 行 × 7 列，周一开头），按当月 1 号星期偏移动态分配日期</li>
 * <li>状态行：累计/连续签到天数</li>
 * <li>进度条：连续天数距下一阶梯宝箱的进度（GuiDraw 自绘矩形条）</li>
 * <li>阶梯奖励预览：最多 3 个宝箱（7/14/30 天），含条件、奖励内容、领取状态</li>
 * <li>签到按钮：点击通过 {@link SignInNetworkManager#sendSignInRequest()} 向服务端发起签到</li>
 * </ul>
 * <p>
 * <b>双端安全</b>：所有动态 Supplier 仅在客户端渲染时求值；服务端构建时不渲染、
 * 读取到的 {@link SignInClientData} 为默认值，不影响服务端逻辑。
 * <p>
 * <b>配置口径（v1.7.0 目标 5）</b>：阶梯奖励与基础奖励预览统一读取
 * {@link SignInClientData}（其内部优先使用服务端同步的配置快照，未同步时回退本地
 * {@link DailySignInConfig}），专用服务器环境下客户端展示与服务端权威配置保持一致；
 * 签到结果反馈不受影响（由服务端同步包权威下发）。
 */
public class SignInCalendarGui {

    // ==================== 编辑模式回调（v1.7.0 目标 4） ====================

    /**
     * 签到页编辑模式回调接口
     * <p>
     * 由 {@code NekoVMGuiV2} 实现并传入，用于：
     * <ul>
     * <li>查询当前是否处于编辑模式（服务端权威，经同步值到客户端）</li>
     * <li>编辑模式下点击阶梯宝箱 → 打开阶梯编辑面板</li>
     * <li>编辑模式下点击「全局配置」按钮 → 打开全局编辑面板（基础奖励/连续系数）</li>
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
         * 编辑模式下点击阶梯宝箱时触发
         *
         * @param tier 被点击的阶梯奖励
         */
        void onEditTierRequested(SignInRewardTier tier);

        /**
         * 编辑模式下点击「全局配置」按钮时触发
         */
        void onEditGlobalRequested();
    }

    // ==================== 布局常量 ====================

    /** 页面宽度（主内容区 = PANEL_WIDTH - 8） */
    private static final int PAGE_WIDTH = 170;
    /** 页面高度（主内容区 = PANEL_HEIGHT - 8） */
    private static final int PAGE_HEIGHT = 312;

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
    /** 星期表头 Y */
    private static final int WEEKDAY_Y = 30;
    /** 日历网格 Y */
    private static final int CAL_Y = 41;

    /** 进度条宽度 */
    private static final int PROGRESS_W = 150;
    /** 进度条 X（水平居中） */
    private static final int PROGRESS_X = (PAGE_WIDTH - PROGRESS_W) / 2;
    /** 进度条 Y */
    private static final int PROGRESS_Y = 190;

    /** 阶梯预览区 Y */
    private static final int TIER_Y = 204;
    /** 阶梯预览单列宽 */
    private static final int TIER_COL_W = 54;
    /** 阶梯预览最大列数（对应 3 个宝箱素材） */
    private static final int TIER_MAX = 3;

    /** 签到结果提示 Y */
    private static final int RESULT_Y = 258;
    /** 签到结果提示显示时长（毫秒） */
    private static final long RESULT_DISPLAY_MS = 5000L;

    /** 签到按钮宽（与 btn_claim.png 一致） */
    private static final int BTN_W = 48;
    /** 签到按钮高 */
    private static final int BTN_H = 24;
    /** 签到按钮 Y */
    private static final int BTN_Y = 272;

    // ==================== 颜色常量（ARGB） ====================

    /** 日期数字-普通（浅色格底上的深灰） */
    private static final int COLOR_DAY_NORMAL = 0xFF3F3F3F;
    /** 日期数字-过去未签到（暗灰） */
    private static final int COLOR_DAY_MISSED = 0xFF777777;
    /** 日期数字-未来（浅灰） */
    private static final int COLOR_DAY_FUTURE = 0xFF9A9A9A;
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

    private SignInCalendarGui() {}

    // ==================== 页面构建入口 ====================

    /**
     * 构建签到页（供 {@code NekoVMGuiV2} 主内容 PagedWidget 添加为页 1）
     *
     * @param editCallback 编辑模式回调（v1.7.0 目标 4）；null 表示不支持编辑模式
     * @return 签到页根 Widget（170x312，绝对布局）
     */
    public static IWidget createSignInPage(SignInEditCallback editCallback) {
        ParentWidget<?> page = new ParentWidget<>().size(PAGE_WIDTH, PAGE_HEIGHT);

        page.child(createTitle(editCallback)); // 标题 + 当前年月（编辑模式附加标识）
        page.child(createStatusInfo()); // 累计/连续天数
        page.child(createWeekdayHeader()); // 星期表头
        page.child(createCalendarGrid()); // 42 日期格
        page.child(createProgressLabel()); // 连续进度文本
        page.child(createProgressBar()); // 连续进度条
        page.child(createTierPreview(editCallback)); // 阶梯宝箱预览（编辑模式可点击）
        page.child(createResultMessage()); // 签到结果提示（限时）
        page.child(createSignInButton(editCallback)); // 签到按钮（编辑模式拦截）
        page.child(createGlobalEditButton(editCallback)); // 全局配置按钮（仅编辑模式显示）

        return page;
    }

    // ==================== 各区域构建 ====================

    /** 标题行：「签到日历 yyyy年M月」（随服务端日期跨月自动刷新；编辑模式附加红色标识） */
    private static IWidget createTitle(SignInEditCallback editCallback) {
        return new TextWidget<>(IKey.dynamic(() -> {
            MonthInfo mi = getMonthInfo();
            String text = EnumChatFormatting.GOLD + "签到日历 " + EnumChatFormatting.YELLOW + mi.year + "年" + mi.month + "月";
            if (editCallback != null && editCallback.isEditMode()) {
                text += EnumChatFormatting.RED + " [编辑]";
            }
            return text;
        })).pos(0, 2)
            .size(PAGE_WIDTH, 12)
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
                    + " 天")).pos(0, 16)
                        .size(PAGE_WIDTH, 10)
                        .textAlign(Alignment.Center)
                        .shadow(false);
    }

    /** 星期表头：一~日，逐列对齐日期格 */
    private static IWidget createWeekdayHeader() {
        ParentWidget<?> header = new ParentWidget<>().pos(0, WEEKDAY_Y)
            .size(PAGE_WIDTH, 9);
        for (int i = 0; i < CAL_COLS; i++) {
            int x = CAL_X + i * (CELL_SIZE + CELL_GAP);
            header.child(
                new TextWidget<>(IKey.str(EnumChatFormatting.GRAY + WEEKDAY_LABELS[i])).pos(x, 0)
                    .size(CELL_SIZE, 9)
                    .textAlign(Alignment.Center)
                    .scale(0.75f)
                    .shadow(false));
        }
        return header;
    }

    /**
     * 日历网格：42 格（6 行 × 7 列）
     * <p>
     * 每格三层（按添加顺序绘制）：格底纹理（动态）→ 猫爪印章（已签到时叠加）→ 日期数字。
     * 日期分配依赖当月 1 号的星期偏移（{@link MonthInfo#firstOffset}），
     * 不属于当月的格子渲染为空（无纹理、无数字）。
     */
    private static IWidget createCalendarGrid() {
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
        }
        return grid;
    }

    /** 连续进度文本：当前连续天数与下一阶梯差距 */
    private static IWidget createProgressLabel() {
        return new TextWidget<>(IKey.dynamic(() -> {
            int consecutive = SignInClientData.getConsecutiveDays();
            // 目标 5：读客户端缓存（服务端同步快照优先），不再直接读本地配置
            SignInRewardTier next = SignInClientData.getNextTier(consecutive);
            if (next == null) {
                return EnumChatFormatting.GOLD + "连续签到 " + consecutive + " 天 · 已达成全部阶梯奖励";
            }
            return EnumChatFormatting.GRAY + "连续签到 "
                + consecutive
                + " 天 · 距 "
                + next.getRequiredDays()
                + " 天宝箱还差 "
                + EnumChatFormatting.YELLOW
                + (next.getRequiredDays() - consecutive)
                + EnumChatFormatting.GRAY
                + " 天";
        })).pos(0, 178)
            .size(PAGE_WIDTH, 10)
            .textAlign(Alignment.Center)
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
            // 目标 5：读客户端缓存（服务端同步快照优先）
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
     * 阶梯奖励预览：最多 3 列（宝箱 + 条件 + 奖励内容 + 领取状态）
     * <p>
     * 宝箱素材按所需天数选取（≤7 小宝箱 / ≤14 中宝箱 / 否则大宝箱）。
     * 领取状态读取 {@link SignInClientData#hasClaimedTier(int)}（服务端同步的当月领取记录）。
     * <p>
     * <b>编辑模式</b>（v1.7.0 目标 4）：宝箱改为 {@link ButtonWidget}，
     * 点击通过 {@link SignInEditCallback#onEditTierRequested} 打开阶梯编辑面板。
     */
    private static IWidget createTierPreview(SignInEditCallback editCallback) {
        ParentWidget<?> box = new ParentWidget<>().pos(0, TIER_Y)
            .size(PAGE_WIDTH, 50);
        // 目标 5：读客户端缓存（服务端同步快照优先，未同步回退本地配置）
        List<SignInRewardTier> tiers = SignInClientData.getRewardTiers();
        int count = Math.min(TIER_MAX, tiers.size());
        int startX = (PAGE_WIDTH - TIER_COL_W * count) / 2;
        for (int i = 0; i < count; i++) {
            final SignInRewardTier tier = tiers.get(i);
            int x = startX + i * TIER_COL_W;

            // 宝箱图标按钮（tooltip 详列奖励内容；编辑模式下点击打开编辑面板）
            box.child(
                new ButtonWidget<>()
                    .pos(x + (TIER_COL_W - 24) / 2, 0)
                    .size(24, 24)
                    .background(chestFor(tier.getRequiredDays()))
                    .tooltipBuilder(t -> {
                        t.addLine(IKey.str(EnumChatFormatting.GOLD + "连续签到 " + tier.getRequiredDays() + " 天宝箱"));
                        t.addLine(IKey.str(buildRewardText(tier)));
                        if (editCallback != null && editCallback.isEditMode()) {
                            t.addLine(IKey.str(EnumChatFormatting.YELLOW + "[编辑模式] 点击编辑此阶梯奖励"));
                        } else if (SignInClientData.hasClaimedTier(tier.getRequiredDays())) {
                            t.addLine(IKey.str(EnumChatFormatting.GREEN + "本月已领取"));
                        } else {
                            t.addLine(IKey.str(EnumChatFormatting.GRAY + "达成条件后签到自动发放"));
                        }
                    })
                    .tooltipAutoUpdate(true)
                    .onMouseTapped(mouse -> {
                        // 编辑模式：点击宝箱打开阶梯编辑面板
                        if (mouse == 0 && editCallback != null && editCallback.isEditMode()) {
                            editCallback.onEditTierRequested(tier);
                            return true;
                        }
                        return false;
                    }));

            // 条件文本（静态，配置加载后不变化）
            box.child(
                new TextWidget<>(IKey.str("连续" + tier.getRequiredDays() + "天")).pos(x, 25)
                    .size(TIER_COL_W, 8)
                    .textAlign(Alignment.Center)
                    .scale(0.7f)
                    .color(0xFF444444)
                    .shadow(false));

            // 奖励内容（静态）
            box.child(
                new TextWidget<>(IKey.str(buildRewardText(tier))).pos(x, 33)
                    .size(TIER_COL_W, 8)
                    .textAlign(Alignment.Center)
                    .scale(0.7f)
                    .color(0xFFB8860B)
                    .shadow(false));

            // 领取状态（动态：已领取/待达成）
            box.child(
                new TextWidget<>(
                    IKey.dynamic(
                        () -> SignInClientData.hasClaimedTier(tier.getRequiredDays()) ? EnumChatFormatting.GREEN + "已领取"
                            : EnumChatFormatting.GRAY + "未达成")).pos(x, 41)
                                .size(TIER_COL_W, 8)
                                .textAlign(Alignment.Center)
                                .scale(0.7f)
                                .shadow(false));
        }
        return box;
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
                    t.addLine(IKey.str(EnumChatFormatting.GRAY + "点击阶梯宝箱或「全局配置」按钮进行编辑"));
                    return;
                }
                if (SignInClientData.hasSignedToday()) {
                    t.addLine(IKey.str(EnumChatFormatting.GRAY + "今日已完成签到，明天再来吧"));
                } else {
                    int nextConsec = SignInClientData.getConsecutiveDays() + 1;
                    // 目标 5：奖励预览读客户端缓存（服务端同步快照优先）
                    int reward = SignInClientData.calculateBaseReward(nextConsec);
                    t.addLine(IKey.str(EnumChatFormatting.GREEN + "点击签到，领取今日奖励"));
                    t.addLine(IKey.str(EnumChatFormatting.GRAY + "基础奖励：" + reward + " 猫猫币（连续第 " + nextConsec + " 天）"));
                    SignInRewardTier tier = SignInClientData.getTriggeredTier(nextConsec);
                    if (tier != null && !SignInClientData.hasClaimedTier(tier.getRequiredDays())) {
                        t.addLine(
                            IKey.str(EnumChatFormatting.GOLD + "今天签到可额外领取 " + tier.getRequiredDays() + " 天阶梯宝箱！"));
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
     * 点击通过 {@link SignInEditCallback#onEditGlobalRequested()} 打开全局编辑面板
     * （每日基础奖励 / 连续递增系数）。非编辑模式下按钮隐藏（setEnabledIf 动态折叠）。
     *
     * @param editCallback 编辑模式回调；null 时按钮恒隐藏
     */
    private static IWidget createGlobalEditButton(SignInEditCallback editCallback) {
        ButtonWidget<?> button = new ButtonWidget<>().pos((PAGE_WIDTH - BTN_W) / 2 + BTN_W + 6, BTN_Y)
            .size(46, BTN_H)
            .background(NekoGuiTextures.TEXT_FIELD_BACKGROUND)
            .overlay(IKey.str(EnumChatFormatting.YELLOW + "全局配置"))
            .tooltipBuilder(t -> {
                t.addLine(IKey.str(EnumChatFormatting.GOLD + "编辑全局签到配置"));
                t.addLine(IKey.str(EnumChatFormatting.GRAY + "每日基础奖励与连续递增系数"));
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
     * <li>已签到 → {@link NekoGuiTextures#SIGNIN_CELL_SIGNED}</li>
     * <li>今天未签到且签到后触发未领取阶梯 → {@link NekoGuiTextures#SIGNIN_CELL_REWARD}（礼物格）</li>
     * <li>今天未签到 → {@link NekoGuiTextures#SIGNIN_CELL_TODAY}</li>
     * <li>其他 → {@link NekoGuiTextures#SIGNIN_CELL_NORMAL}</li>
     * </ul>
     */
    private static IDrawable cellTexture(int index) {
        String date = cellDate(index);
        if (date.isEmpty()) return IDrawable.EMPTY;
        if (SignInClientData.hasSigned(date)) return NekoGuiTextures.SIGNIN_CELL_SIGNED;
        String today = SignInClientData.getToday();
        if (date.equals(today)) {
            int nextConsec = SignInClientData.getConsecutiveDays() + 1;
            // 目标 5：读客户端缓存（服务端同步快照优先）
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
     * 按阶梯所需天数选取宝箱素材（≤7 小宝箱 / ≤14 中宝箱 / 否则大宝箱）
     */
    private static UITexture chestFor(int requiredDays) {
        if (requiredDays <= 7) return NekoGuiTextures.SIGNIN_CHEST_7;
        if (requiredDays <= 14) return NekoGuiTextures.SIGNIN_CHEST_14;
        return NekoGuiTextures.SIGNIN_CHEST_30;
    }

    /**
     * 构建阶梯奖励内容文本（货币 + 可选物品标记）
     */
    private static String buildRewardText(SignInRewardTier tier) {
        String text = "+" + tier.getCurrencyAmount() + " " + NekoCurrencyRegistrar.getDisplayName(tier.getCurrencyId());
        if (tier.hasItemReward()) {
            text += " +物品";
        }
        return text;
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

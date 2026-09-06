package com.miaokatze.gtit.terminal;

import java.util.List;

import net.minecraft.util.EnumChatFormatting;

import com.cleanroommc.modularui.api.IPanelHandler;
import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.value.StringValue;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.PagedWidget;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;
import com.miaokatze.gtit.client.gui.NekoConfirmationDialog;
import com.miaokatze.gtit.client.gui.NekoGuiTextures;

/**
 * 管理终端-签到页（T3 实装，纯客户端 MUI2）
 * <p>
 * 静态页面构建类（范式照抄 {@code gui/signin/SignInCalendarGui#createSignInPage}），
 * 由 {@link TerminalGui} 主内容 PagedWidget 添加为页 {@link TerminalGui#PAGE_SIGNIN}：
 * <ul>
 * <li>目标玩家选择：{@link TerminalClientData#getOnlinePlayers()} 打开快照逐行热区选中
 * （选中名存页内静态 volatile 字段，页面重建后保留）</li>
 * <li>查询摘要：ACTION_SIGNIN_QUERY → 服务端 sendData 推送
 * DATA_TYPE_SIGNIN_SUMMARY → {@link TerminalClientData#getSignInSummaryLines()}
 * 经 IKey.dynamic 逐行显示</li>
 * <li>设置连续天数：4 位数字输入（页内静态草稿，重建恢复）+ ACTION_SIGNIN_SET_DAYS</li>
 * <li>重置签到数据：{@link NekoConfirmationDialog} 二次确认后才发 ACTION_SIGNIN_RESET
 * （未确认绝不发包）</li>
 * <li>重载配置：ACTION_SIGNIN_RELOAD_CONFIGS</li>
 * </ul>
 * 动作统一走 {@link TerminalNetworkManager#sendAction}，服务端
 * {@link TerminalActionHandler} 五步校验链权威校验；1.5s 冷却 + 未选中目标时
 * 相关按钮禁用（{@code setEnabledIf} 折叠）。
 * <p>
 * <b>纯客户端约束</b>：无 MUI2 同步值/槽位（TextFieldWidget 用
 * {@link StringValue.Dynamic} 本地动态草稿，项目先例 MailGui/BlessingEditor 同款），
 * 不调用 {@code getSyncManager()}/{@code getContainer()}。
 */
public final class TerminalSignInPage {

    /** 页宽（与 TerminalGui 主内容区一致） */
    public static final int PAGE_WIDTH = TerminalGui.CONTENT_WIDTH;
    /** 页高 */
    public static final int PAGE_HEIGHT = TerminalGui.CONTENT_HEIGHT;

    // ==================== 布局常量（页内局部坐标，158x226） ====================

    /** 在线玩家列表可见行数（超出翻页；快照来自打开时刻） */
    private static final int PLAYER_ROWS = 5;
    /** 玩家行行高 */
    private static final int PLAYER_ROW_H = 12;
    /** 玩家列表起始 Y（标题 12 之下） */
    private static final int LIST_Y = 22;
    /** 翻页按钮宽/高 */
    private static final int PAGER_BTN_W = 16;
    private static final int PAGER_BTN_H = 11;
    /** 当前目标行 Y（列表含翻页行的总高之下） */
    private static final int TARGET_Y = LIST_Y + (PLAYER_ROWS + 1) * PLAYER_ROW_H + 3;
    /** 天数输入行 Y */
    private static final int DAYS_ROW_Y = 110;
    /** 天数输入框宽 */
    private static final int DAYS_FIELD_W = 40;
    /** 输入框高 */
    private static final int FIELD_H = 14;
    /** 按钮行 Y */
    private static final int BTN_ROW_Y = 128;
    /** 按钮高 */
    private static final int BTN_H = 16;
    /** 摘要区标题 Y */
    private static final int SUMMARY_LABEL_Y = 147;
    /** 摘要行数（与服务端摘要行数一致） */
    private static final int SUMMARY_ROWS = 8;
    /** 摘要行行高 */
    private static final int SUMMARY_ROW_H = 9;

    // ==================== 动作与冷却 ====================

    /** 动作冷却（毫秒，防连点重复发包；口径同 MailGui 撰写冷却） */
    private static final long ACTION_COOLDOWN_MS = 1500L;
    /** 上次发包时间戳（0=未发过） */
    private static volatile long lastActionSendMs = 0L;

    // ==================== 页内状态（静态，GUI 重建后保留） ====================

    /** 选中的目标玩家名（空串=未选择；打开快照内点击热区写入） */
    private static volatile String selectedPlayer = "";
    /** 玩家列表当前页码（0 起；打开快照内翻页） */
    private static volatile int playerListPage = 0;
    /** 天数输入草稿（TextFieldWidget 绑定，重建恢复） */
    private static String daysDraft = "";

    /** 重置二次确认文案（含不可恢复警示；%s=目标玩家名） */
    private static final String CONFIRM_RESET_FORMAT = "确认重置 %s 的签到数据？将清空目标签到数据，不可恢复。";

    private TerminalSignInPage() {
        // 静态工具类，禁止实例化
    }

    /**
     * 构建签到管理页
     *
     * @param controller 主内容分页控制器（签到页为单页，未用）
     * @return 固定尺寸页根 Widget
     */
    public static IWidget createPage(PagedWidget.Controller controller) {
        ParentWidget<?> page = new ParentWidget<>().size(PAGE_WIDTH, PAGE_HEIGHT);

        // 居中页名
        page.child(
            new TextWidget<>(IKey.str(EnumChatFormatting.GOLD + "签到管理")).pos(0, 0)
                .size(PAGE_WIDTH, 12)
                .textAlign(Alignment.CENTER)
                .shadow(false));

        page.child(createPlayerList()); // 在线玩家列表（逐行热区选中）
        page.child(createTargetLine()); // 当前目标回显行
        page.child(createDaysRow()); // 天数输入 + 设置按钮
        page.child(createActionButtonRow()); // 查询/重置/重载按钮行
        page.child(createSummaryArea()); // 摘要显示区（DATA_TYPE_SIGNIN_SUMMARY 推送后刷新）

        return page;
    }

    // ==================== 在线玩家列表 ====================

    /** 列表标题 + 行热区 + 翻页行（行数据动态读打开快照，越界行整体折叠） */
    private static IWidget createPlayerList() {
        ParentWidget<?> list = new ParentWidget<>().pos(0, 12)
            .size(PAGE_WIDTH, (PLAYER_ROWS + 1) * PLAYER_ROW_H + 10);

        list.child(
            new TextWidget<>(
                IKey.dynamic(
                    () -> EnumChatFormatting.GRAY + "在线玩家（点击选中目标，共 "
                        + TerminalClientData.getOnlinePlayers()
                            .size()
                        + " 人）:")).pos(2, 0)
                            .size(PAGE_WIDTH - 4, 9)
                            .scale(0.75f)
                            .shadow(false));

        for (int i = 0; i < PLAYER_ROWS; i++) {
            final int index = i;
            int y = 10 + index * PLAYER_ROW_H;

            // 玩家名文本（选中绿色带 ▶ 标记，未选中灰色）
            list.child(new TextWidget<>(IKey.dynamic(() -> {
                String name = playerAt(index);
                if (name == null) return "";
                return name.equals(selectedPlayer) ? EnumChatFormatting.GREEN + "▶ " + name
                    : EnumChatFormatting.GRAY + name;
            })).pos(6, y + 2)
                .size(PAGE_WIDTH - 10, 9)
                .scale(0.8f)
                .shadow(false)
                .setEnabledIf(w -> playerAt(index) != null));

            // 整行点击热区（左键选中目标；越界行折叠防误触）
            ButtonWidget<?> rowHitbox = new ButtonWidget<>().pos(2, y)
                .size(PAGE_WIDTH - 4, PLAYER_ROW_H)
                .background(IDrawable.EMPTY)
                .onMouseTapped(mouse -> {
                    String name = playerAt(index);
                    if (mouse == 0 && name != null) {
                        selectedPlayer = name;
                        return true;
                    }
                    return false;
                });
            rowHitbox.setEnabledIf(w -> playerAt(index) != null);
            list.child(rowHitbox);
        }

        // 翻页行：上一页 / 页码 / 下一页（范式同邮件页目标列表）
        int pagerY = 10 + PLAYER_ROWS * PLAYER_ROW_H;
        int pagerX = (PAGE_WIDTH - (PAGER_BTN_W * 2 + 22)) / 2;
        list.child(
            new ButtonWidget<>().pos(pagerX, pagerY)
                .size(PAGER_BTN_W, PAGER_BTN_H)
                .background(NekoGuiTextures.TEXT_FIELD_BACKGROUND)
                .disableHoverBackground()
                .overlay(IKey.str("<"))
                .onMouseTapped(mouse -> {
                    if (mouse == 0 && playerListPage > 0) {
                        playerListPage--;
                        return true;
                    }
                    return false;
                }));
        list.child(
            new TextWidget<>(
                IKey.dynamic(
                    () -> EnumChatFormatting.GRAY + String.valueOf(Math.min(playerListPage, maxPlayerPage()) + 1)
                        + "/"
                        + (maxPlayerPage() + 1))).pos(pagerX + PAGER_BTN_W + 2, pagerY + 2)
                            .size(18, 9)
                            .textAlign(Alignment.CENTER)
                            .scale(0.75f)
                            .shadow(false));
        list.child(
            new ButtonWidget<>().pos(pagerX + PAGER_BTN_W + 22, pagerY)
                .size(PAGER_BTN_W, PAGER_BTN_H)
                .background(NekoGuiTextures.TEXT_FIELD_BACKGROUND)
                .disableHoverBackground()
                .overlay(IKey.str(">"))
                .onMouseTapped(mouse -> {
                    if (mouse == 0 && playerListPage < maxPlayerPage()) {
                        playerListPage++;
                        return true;
                    }
                    return false;
                }));
        return list;
    }

    // ==================== 当前目标行 ====================

    /** 当前目标回显（未选择提示；选中但不在打开快照内附红色警示，服务端仍权威校验在线） */
    private static IWidget createTargetLine() {
        return new TextWidget<>(IKey.dynamic(() -> {
            if (!hasTarget()) return EnumChatFormatting.GRAY + "当前目标：未选择";
            boolean inSnapshot = TerminalClientData.getOnlinePlayers()
                .contains(selectedPlayer);
            return EnumChatFormatting.YELLOW + "当前目标："
                + selectedPlayer
                + (inSnapshot ? "" : EnumChatFormatting.RED + "（不在当前在线列表）");
        })).pos(2, TARGET_Y)
            .size(PAGE_WIDTH - 4, 10)
            .scale(0.8f)
            .shadow(false);
    }

    // ==================== 天数输入行 ====================

    /** 连续天数输入（4 位数字草稿，重建恢复）+ 设置连续天数按钮 */
    private static IWidget createDaysRow() {
        ParentWidget<?> row = new ParentWidget<>().pos(0, DAYS_ROW_Y)
            .size(PAGE_WIDTH, FIELD_H);

        row.child(
            new TextWidget<>(IKey.str(EnumChatFormatting.GRAY + "天数:")).pos(0, 3)
                .size(26, 9)
                .scale(0.8f)
                .shadow(false));

        TextFieldWidget daysField = new TextFieldWidget()
            .value(new StringValue.Dynamic(() -> daysDraft, val -> daysDraft = val))
            .setMaxLength(4);
        daysField.pos(28, 0)
            .size(DAYS_FIELD_W, FIELD_H);
        daysField.tooltipBuilder(t -> {
            t.addLine(IKey.str("连续签到天数（0-9999 整数）"));
            t.addLine(IKey.str(EnumChatFormatting.GRAY + "最长 4 位数字"));
        });
        daysField.tooltipAutoUpdate(true);
        row.child(daysField);

        // 设置连续天数（未选目标 / 冷却中折叠；客户端预校验数字格式，服务端权威校验范围）
        ButtonWidget<?> setDaysButton = new ButtonWidget<>().pos(76, 0)
            .size(64, FIELD_H)
            .background(NekoGuiTextures.TEXT_FIELD_BACKGROUND)
            .overlay(IKey.str(EnumChatFormatting.WHITE + "设置连续天数"))
            .tooltipBuilder(t -> {
                t.addLine(IKey.str(EnumChatFormatting.GREEN + "将选中玩家的连续签到天数设为输入值"));
                t.addLine(IKey.str(EnumChatFormatting.GRAY + "先在上方列表选中目标玩家"));
            })
            .tooltipAutoUpdate(true)
            .setEnabledIf(w -> hasTarget() && !isActionCoolingDown());
        setDaysButton.onMouseTapped(mouse -> {
            if (mouse != 0 || !hasTarget()) return false;
            Integer days = parseDaysDraft();
            if (days == null || !tryBeginAction()) return true;
            TerminalNetworkManager
                .sendAction(TerminalActionHandler.ACTION_SIGNIN_SET_DAYS, selectedPlayer, days, "", "", "");
            return true;
        });
        row.child(setDaysButton);

        return row;
    }

    // ==================== 查询/重置/重载按钮行 ====================

    private static IWidget createActionButtonRow() {
        ParentWidget<?> row = new ParentWidget<>().pos(0, BTN_ROW_Y)
            .size(PAGE_WIDTH, BTN_H);

        // 查询摘要（未选目标 / 冷却中折叠）
        ButtonWidget<?> queryButton = new ButtonWidget<>().pos(0, 0)
            .size(48, BTN_H)
            .background(NekoGuiTextures.TEXT_FIELD_BACKGROUND)
            .overlay(IKey.str(EnumChatFormatting.WHITE + "查询摘要"))
            .tooltipBuilder(t -> {
                t.addLine(IKey.str(EnumChatFormatting.GREEN + "查询选中玩家的签到摘要"));
                t.addLine(IKey.str(EnumChatFormatting.GRAY + "累计/连续/当月/今日在线等"));
            })
            .tooltipAutoUpdate(true)
            .setEnabledIf(w -> hasTarget() && !isActionCoolingDown());
        queryButton.onMouseTapped(mouse -> {
            if (mouse != 0 || !hasTarget() || !tryBeginAction()) return true;
            TerminalNetworkManager.sendAction(TerminalActionHandler.ACTION_SIGNIN_QUERY, selectedPlayer, 0, "", "", "");
            return true;
        });
        row.child(queryButton);

        // 重置签到数据（二次确认后才发包；未确认绝不发送）
        ButtonWidget<?> resetButton = new ButtonWidget<>().pos(52, 0)
            .size(64, BTN_H)
            .background(NekoGuiTextures.TEXT_FIELD_BACKGROUND)
            .overlay(IKey.str(EnumChatFormatting.RED + "重置签到数据"))
            .tooltipBuilder(t -> {
                t.addLine(IKey.str(EnumChatFormatting.RED + "清空选中玩家的全部签到记录（不可恢复）"));
                t.addLine(IKey.str(EnumChatFormatting.GRAY + "点击后需二次确认"));
            })
            .tooltipAutoUpdate(true)
            .setEnabledIf(w -> hasTarget() && !isActionCoolingDown());
        resetButton.onMouseTapped(mouse -> {
            if (mouse != 0 || !hasTarget()) return false;
            openResetConfirmDialog(resetButton);
            return true;
        });
        row.child(resetButton);

        // 重载配置（无目标动作，仅冷却折叠）
        ButtonWidget<?> reloadButton = new ButtonWidget<>().pos(PAGE_WIDTH - 40, 0)
            .size(40, BTN_H)
            .background(NekoGuiTextures.TEXT_FIELD_BACKGROUND)
            .overlay(IKey.str(EnumChatFormatting.WHITE + "重载配置"))
            .tooltipBuilder(t -> {
                t.addLine(IKey.str(EnumChatFormatting.GREEN + "热重载签到与每日在线时长配置"));
                t.addLine(IKey.str(EnumChatFormatting.GRAY + "已打开的玩家界面需重新打开后生效"));
            })
            .tooltipAutoUpdate(true)
            .setEnabledIf(w -> !isActionCoolingDown());
        reloadButton.onMouseTapped(mouse -> {
            if (mouse != 0 || !tryBeginAction()) return true;
            TerminalNetworkManager.sendAction(TerminalActionHandler.ACTION_SIGNIN_RELOAD_CONFIGS, "", 0, "", "", "");
            return true;
        });
        row.child(reloadButton);

        return row;
    }

    // ==================== 摘要显示区 ====================

    /** 查询结果摘要（IKey.dynamic 绑定 getSignInSummaryLines 逐行显示，推送后自动刷新） */
    private static IWidget createSummaryArea() {
        ParentWidget<?> area = new ParentWidget<>().pos(0, SUMMARY_LABEL_Y)
            .size(PAGE_WIDTH, PAGE_HEIGHT - SUMMARY_LABEL_Y);

        area.child(
            new TextWidget<>(IKey.str(EnumChatFormatting.GRAY + "签到摘要（点「查询摘要」后显示）:")).pos(2, 0)
                .size(PAGE_WIDTH - 4, 9)
                .scale(0.75f)
                .shadow(false));

        for (int i = 0; i < SUMMARY_ROWS; i++) {
            final int index = i;
            area.child(new TextWidget<>(IKey.dynamic(() -> {
                List<String> lines = TerminalClientData.getSignInSummaryLines();
                if (index >= lines.size()) return "";
                String text = lines.get(index);
                // 首行为 "===== 名字 的签到摘要 =====" 标题，黄色高亮
                return index == 0 ? EnumChatFormatting.YELLOW + text : EnumChatFormatting.GRAY + text;
            })).pos(4, 7 + index * SUMMARY_ROW_H)
                .size(PAGE_WIDTH - 8, SUMMARY_ROW_H)
                .scale(0.8f)
                .shadow(false));
        }
        return area;
    }

    // ==================== 重置二次确认 ====================

    /**
     * 打开重置二次确认弹框（确认回调内才检查冷却并发包，未确认绝不发送）
     *
     * @param anchor 触发按钮（用于取宿主面板构建弹框 handler）
     */
    private static void openResetConfirmDialog(ButtonWidget<?> anchor) {
        NekoConfirmationDialog dialog = new NekoConfirmationDialog("gtit_terminal:signin_reset_confirm");
        dialog.setButtonText("确认重置", "取消");
        dialog.setParams(String.format(CONFIRM_RESET_FORMAT, selectedPlayer), () -> {
            if (!tryBeginAction()) return;
            TerminalNetworkManager.sendAction(TerminalActionHandler.ACTION_SIGNIN_RESET, selectedPlayer, 0, "", "", "");
        });
        IPanelHandler.simple(anchor.getPanel(), (parent, player) -> dialog, true)
            .openPanel();
    }

    // ==================== 内部工具 ====================

    /** 按索引取打开快照在线玩家名（越界/异常返回 null，供 setEnabledIf 折叠） */
    /** 玩家列表最大页码（0 起；快照为空时恒 0） */
    private static int maxPlayerPage() {
        int size = TerminalClientData.getOnlinePlayers()
            .size();
        return size == 0 ? 0 : (size - 1) / PLAYER_ROWS;
    }

    /** 当前页第 index 行的玩家名（页码先 clamp 防越界；越界返回 null） */
    private static String playerAt(int index) {
        List<String> names = TerminalClientData.getOnlinePlayers();
        int page = Math.min(playerListPage, maxPlayerPage());
        int i = page * PLAYER_ROWS + index;
        return i >= 0 && i < names.size() ? names.get(i) : null;
    }

    /** 是否已选中目标玩家 */
    private static boolean hasTarget() {
        return selectedPlayer != null && !selectedPlayer.isEmpty();
    }

    /** 是否处于动作冷却期 */
    private static boolean isActionCoolingDown() {
        return System.currentTimeMillis() - lastActionSendMs < ACTION_COOLDOWN_MS;
    }

    /**
     * 尝试占用冷却：冷却期内返回 false，否则记录时间戳并返回 true
     */
    private static boolean tryBeginAction() {
        long now = System.currentTimeMillis();
        if (now - lastActionSendMs < ACTION_COOLDOWN_MS) return false;
        lastActionSendMs = now;
        return true;
    }

    /**
     * 解析天数草稿（客户端预校验：1-4 位纯数字；服务端仍权威校验 0-9999）
     *
     * @return 合法返回天数；空/非法返回 null（不发包）
     */
    private static Integer parseDaysDraft() {
        String text = daysDraft == null ? "" : daysDraft.trim();
        if (text.isEmpty() || !text.matches("\\d{1,4}")) return null;
        return Integer.valueOf(text);
    }
}

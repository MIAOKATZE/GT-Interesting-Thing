package com.miaokatze.gtit.terminal;

import java.util.List;

import net.minecraft.util.EnumChatFormatting;

import com.cleanroommc.modularui.api.IPanelHandler;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.screen.ModularScreen;
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
 * 管理终端-邮件运营页（T2 实现，纯客户端 MUI2 页面）
 * <p>
 * 静态页面构建类（范式照抄 {@code mail/MailGui} 列表+表单与
 * {@code signin/SignInCalendarGui} 静态页面类），由 {@link TerminalGui} 主内容
 * PagedWidget 添加为页 {@link TerminalGui#PAGE_MAIL}。页面结构（{@value #PAGE_WIDTH}x{@value #PAGE_HEIGHT}）：
 * <ul>
 * <li>目标玩家选择区：{@link TerminalClientData#getOnlinePlayers()} 打开快照列表
 * （4 行/页 + 翻页），点击行热区选中（选中态存页内静态字段，GUI 重建恢复）</li>
 * <li>表单区：标题（≤{@code MAX_TEXT1_LENGTH}）/ 正文（≤{@code MAX_TEXT2_LENGTH}）/
 * 奖励ID（≤{@code MAX_TEXT3_LENGTH}）输入框，草稿存页内静态字段</li>
 * <li>按钮区：发送邮件 / 查询模板 / 设置首登模板 / 清除首登模板（二次确认）/
 * 发布一次性奖励（二次确认，含不可撤回警示）</li>
 * <li>模板信息区：{@code IKey.dynamic} 绑定 {@link TerminalClientData} 首登模板缓存
 * （标题/正文首行/是否带附件，由 MAIL_FIRST_QUERY 服务端推送刷新）</li>
 * </ul>
 * <b>纯客户端约束</b>：无 MUI2 同步值/槽位；所有动作一律经
 * {@link TerminalNetworkManager#sendAction} 发 int 常量请求，权限/限长/白名单由服务端
 * {@link TerminalActionHandler} 五步校验链权威校验。<b>附件不经过客户端</b>——
 * 服务端读管理员执行者手持物品（MailOps 内完成），客户端不传物品数据。
 * <p>
 * <b>防抖</b>：全部动作按钮共享页内静态 {@code lastActionAt} + 1.5s 冷却
 * （{@code MailGui#COMPOSE_COOLDOWN_MS} 同款范式），冷却中忽略点击；
 * 二次确认按钮未点「确认」不发包。
 */
public final class TerminalMailPage {

    /** 页宽（与 TerminalGui 主内容区一致） */
    public static final int PAGE_WIDTH = TerminalGui.CONTENT_WIDTH;
    /** 页高 */
    public static final int PAGE_HEIGHT = TerminalGui.CONTENT_HEIGHT;

    // ==================== 布局常量（页内局部坐标） ====================

    /** 在线玩家列表每页可见行数 */
    private static final int PLAYERS_VISIBLE = 4;
    /** 玩家行高（含 1px 间距） */
    private static final int PLAYER_ROW_H = 11;
    /** 玩家列表区起始 Y */
    private static final int PLAYERS_Y = 10;
    /** 翻页行 Y（列表区之下） */
    private static final int PLAYER_PAGER_Y = 55;
    /** 翻页按钮宽/高 */
    private static final int PAGER_BTN_W = 18;
    private static final int PAGER_BTN_H = 10;

    /** 表单标签列宽 */
    private static final int LABEL_W = 34;
    /** 表单输入框 X */
    private static final int FIELD_X = 38;
    /** 表单输入框宽 */
    private static final int FIELD_W = PAGE_WIDTH - FIELD_X - 6;
    /** 输入框高 */
    private static final int FIELD_H = 12;

    /** 标签行 Y */
    private static final int TITLE_Y = 67;
    /** 正文行 Y */
    private static final int BODY_Y = 81;
    /** 奖励ID 行 Y */
    private static final int REWARD_ID_Y = 95;
    /** 附件说明行 Y */
    private static final int HINT_Y = 110;

    /** 按钮行 1 Y（发送邮件 / 查询模板） */
    private static final int BTN_ROW1_Y = 120;
    /** 按钮行 2 Y（设置首登模板 / 清除首登模板） */
    private static final int BTN_ROW2_Y = 138;
    /** 按钮行 3 Y（发布一次性奖励，整行） */
    private static final int BTN_ROW3_Y = 156;
    /** 常规按钮宽/高 */
    private static final int BTN_W = (PAGE_WIDTH - 18) / 2;
    private static final int BTN_H = 16;
    /** 整行按钮宽 */
    private static final int BTN_FULL_W = PAGE_WIDTH - 12;

    /** 模板信息区标题行 Y */
    private static final int TEMPLATE_HEADER_Y = 176;
    /** 模板信息-标题行 Y */
    private static final int TEMPLATE_TITLE_Y = 186;
    /** 模板信息-正文行 Y */
    private static final int TEMPLATE_BODY_Y = 195;
    /** 模板信息-附件行 Y */
    private static final int TEMPLATE_ATTACH_Y = 204;

    /** 动作发送冷却（毫秒，客户端防抖，防连点重复发包；MailGui 同款 1.5s） */
    private static final long ACTION_COOLDOWN_MS = 1500L;

    // ==================== 页面文案（页内局部常量，硬编码中文） ====================

    private static final String LABEL_TARGET = "目标:";
    private static final String HINT_NO_SELECTION = "（未选择，点击下方玩家名）";
    private static final String HINT_NO_PLAYERS = "（无在线玩家快照）";
    private static final String LABEL_TITLE = "标题:";
    private static final String LABEL_BODY = "正文:";
    private static final String LABEL_REWARD_ID = "奖励ID:";
    private static final String HINT_ATTACHMENT = "附件=服务端手持（空手无附件，不消耗）";

    private static final String BTN_SEND = "发送邮件";
    private static final String BTN_QUERY = "查询模板";
    private static final String BTN_FIRST_SET = "设置首登模板";
    private static final String BTN_FIRST_CLEAR = "清除首登模板";
    private static final String BTN_ONCE = "发布一次性奖励";

    /** 模板信息区标题（查询后由服务端推送刷新） */
    private static final String TEMPLATE_HEADER = "── 当前首登奖励模板 ──";
    private static final String TEMPLATE_EMPTY = "（未查询或暂无）";
    private static final String TEMPLATE_BODY_EMPTY = "（空）";

    /** MAIL_FIRST_CLEAR 二次确认文案 */
    private static final String CONFIRM_FIRST_CLEAR = "确认清除首登奖励模板？清除后新玩家首次登录将不再自动收到奖励（已发出的不撤回，可重新设置）。";

    /** MAIL_ONCE 二次确认文案（含全服一次性、不可撤回警示） */
    private static final String CONFIRM_ONCE = "确认发布一次性奖励？该奖励为全服一次性、发布后不可撤回，全体玩家各领取一次，奖励ID不可重复使用。";

    // ==================== 页内状态（GUI 重建恢复，MailGui 静态草稿先例） ====================

    /** 当前选中目标玩家名（空 = 未选择；渲染线程经 IKey.dynamic 读取，volatile） */
    private static volatile String selectedPlayer = "";
    /** 草稿：邮件标题 */
    private static String draftTitle = "";
    /** 草稿：邮件正文 */
    private static String draftBody = "";
    /** 草稿：一次性奖励 ID */
    private static String draftRewardId = "";
    /** 在线玩家列表当前页码（0 起） */
    private static int playerListPage = 0;
    /** 上次动作发包时间戳（1.5s 冷却防连点） */
    private static long lastActionAt = 0L;

    private TerminalMailPage() {
        // 静态页面类，禁止实例化
    }

    /**
     * 构建邮件运营页
     *
     * @param controller 主内容分页控制器（本页不使用子分页）
     * @return 固定尺寸页根 Widget
     */
    public static IWidget createPage(PagedWidget.Controller controller) {
        ParentWidget<?> page = new ParentWidget<>().size(PAGE_WIDTH, PAGE_HEIGHT);

        page.child(createTargetSection()); // 目标玩家选择区
        page.child(createFormSection()); // 标题/正文/奖励ID 输入 + 附件说明
        page.child(createButtonSection()); // 五个动作按钮
        page.child(createTemplateSection()); // 首登模板信息区

        return page;
    }

    // ==================== 目标玩家选择区 ====================

    /** 目标行 + 在线玩家列表（4 行热区）+ 翻页行 */
    private static IWidget createTargetSection() {
        ParentWidget<?> section = new ParentWidget<>().pos(0, 0)
            .size(PAGE_WIDTH, PLAYER_PAGER_Y + PAGER_BTN_H);

        // 目标标签 + 选中玩家显示
        section.child(
            new TextWidget<>(IKey.str(LABEL_TARGET)).pos(2, 1)
                .size(LABEL_W - 4, 9)
                .scale(0.8f)
                .shadow(false));
        section.child(
            new TextWidget<>(
                IKey.dynamic(
                    () -> selectedPlayer.isEmpty() ? EnumChatFormatting.GRAY + HINT_NO_SELECTION
                        : EnumChatFormatting.GREEN + selectedPlayer)).pos(FIELD_X, 1)
                            .size(FIELD_W, 9)
                            .scale(0.8f)
                            .shadow(false));

        // 空列表提示（仅快照为空时显示）
        section.child(
            new TextWidget<>(IKey.str(EnumChatFormatting.GRAY + HINT_NO_PLAYERS)).pos(0, 26)
                .size(PAGE_WIDTH, 9)
                .textAlign(Alignment.Center)
                .scale(0.8f)
                .shadow(false)
                .setEnabledIf(w -> onlinePlayers().isEmpty()));

        // 4 行玩家名热区按钮（点击选中；选中行绿字）
        for (int i = 0; i < PLAYERS_VISIBLE; i++) {
            final int index = i;
            int y = PLAYERS_Y + index * PLAYER_ROW_H;
            section.child(
                new ButtonWidget<>().pos(0, y)
                    .size(PAGE_WIDTH, PLAYER_ROW_H - 1)
                    .background(NekoGuiTextures.TEXT_FIELD_BACKGROUND)
                    .disableHoverBackground()
                    .overlay(IKey.dynamic(() -> {
                        String name = playerAt(index);
                        if (name == null) return "";
                        return name.equals(selectedPlayer) ? EnumChatFormatting.GREEN + name
                            : EnumChatFormatting.WHITE + name;
                    }))
                    .onMouseTapped(mouse -> {
                        String name = playerAt(index);
                        if (mouse == 0 && name != null) {
                            selectedPlayer = name;
                            return true;
                        }
                        return false;
                    })
                    .setEnabledIf(w -> playerAt(index) != null));
        }

        // 翻页行：上一页 / 页码 / 下一页
        int pagerX = (PAGE_WIDTH - (PAGER_BTN_W * 2 + 22)) / 2;
        section.child(
            new ButtonWidget<>().pos(pagerX, PLAYER_PAGER_Y)
                .size(PAGER_BTN_W, PAGER_BTN_H)
                .background(NekoGuiTextures.TEXT_FIELD_BACKGROUND)
                .overlay(IKey.str("<"))
                .onMouseTapped(mouse -> {
                    if (mouse == 0 && playerListPage > 0) {
                        playerListPage--;
                        return true;
                    }
                    return false;
                }));
        section.child(
            new TextWidget<>(
                IKey.dynamic(
                    () -> EnumChatFormatting.GRAY + String.valueOf(Math.min(playerListPage, maxPlayerPage()) + 1)
                        + "/"
                        + (maxPlayerPage() + 1))).pos(pagerX + PAGER_BTN_W + 2, PLAYER_PAGER_Y + 2)
                            .size(18, 9)
                            .textAlign(Alignment.Center)
                            .scale(0.75f)
                            .shadow(false));
        section.child(
            new ButtonWidget<>().pos(pagerX + PAGER_BTN_W + 22, PLAYER_PAGER_Y)
                .size(PAGER_BTN_W, PAGER_BTN_H)
                .background(NekoGuiTextures.TEXT_FIELD_BACKGROUND)
                .overlay(IKey.str(">"))
                .onMouseTapped(mouse -> {
                    if (mouse == 0 && playerListPage < maxPlayerPage()) {
                        playerListPage++;
                        return true;
                    }
                    return false;
                }));

        return section;
    }

    // ==================== 表单区 ====================

    /** 标题/正文/奖励ID 三行输入框（草稿绑定）+ 附件说明行 */
    private static IWidget createFormSection() {
        ParentWidget<?> section = new ParentWidget<>().pos(0, 0)
            .size(PAGE_WIDTH, PAGE_HEIGHT);

        // 标题
        section.child(
            new TextWidget<>(IKey.str(LABEL_TITLE)).pos(2, TITLE_Y + 2)
                .size(LABEL_W - 4, 9)
                .scale(0.8f)
                .shadow(false));
        TextFieldWidget titleField = new TextFieldWidget()
            .value(new StringValue.Dynamic(() -> draftTitle, val -> draftTitle = val))
            .setMaxLength(TerminalActionPacket.MAX_TEXT1_LENGTH);
        titleField.pos(FIELD_X, TITLE_Y)
            .size(FIELD_W, FIELD_H);
        titleField.tooltipBuilder(t -> {
            t.addLine(IKey.str("邮件标题（必填）"));
            t.addLine(IKey.str(EnumChatFormatting.GRAY + "最长 " + TerminalActionPacket.MAX_TEXT1_LENGTH + " 字符"));
        });
        titleField.tooltipAutoUpdate(true);
        section.child(titleField);

        // 正文
        section.child(
            new TextWidget<>(IKey.str(LABEL_BODY)).pos(2, BODY_Y + 2)
                .size(LABEL_W - 4, 9)
                .scale(0.8f)
                .shadow(false));
        TextFieldWidget bodyField = new TextFieldWidget()
            .value(new StringValue.Dynamic(() -> draftBody, val -> draftBody = val))
            .setMaxLength(TerminalActionPacket.MAX_TEXT2_LENGTH);
        bodyField.pos(FIELD_X, BODY_Y)
            .size(FIELD_W, FIELD_H);
        bodyField.tooltipBuilder(t -> {
            t.addLine(IKey.str("邮件正文（单行输入，可留空）"));
            t.addLine(IKey.str(EnumChatFormatting.GRAY + "最长 " + TerminalActionPacket.MAX_TEXT2_LENGTH + " 字符"));
        });
        bodyField.tooltipAutoUpdate(true);
        section.child(bodyField);

        // 奖励ID（发布一次性奖励用）
        section.child(
            new TextWidget<>(IKey.str(LABEL_REWARD_ID)).pos(2, REWARD_ID_Y + 2)
                .size(LABEL_W - 4, 9)
                .scale(0.8f)
                .shadow(false));
        TextFieldWidget rewardIdField = new TextFieldWidget()
            .value(new StringValue.Dynamic(() -> draftRewardId, val -> draftRewardId = val))
            .setMaxLength(TerminalActionPacket.MAX_TEXT3_LENGTH);
        rewardIdField.pos(FIELD_X, REWARD_ID_Y)
            .size(FIELD_W, FIELD_H);
        rewardIdField.tooltipBuilder(t -> {
            t.addLine(IKey.str("全服一次性奖励的防重 ID（发布时必填）"));
            t.addLine(IKey.str(EnumChatFormatting.GRAY + "最长 " + TerminalActionPacket.MAX_TEXT3_LENGTH + " 字符"));
        });
        rewardIdField.tooltipAutoUpdate(true);
        section.child(rewardIdField);

        // 附件说明（附件在服务端读取管理员手持物品，客户端不传物品数据）
        section.child(
            new TextWidget<>(IKey.str(EnumChatFormatting.GRAY + HINT_ATTACHMENT)).pos(2, HINT_Y)
                .size(PAGE_WIDTH - 4, 8)
                .scale(0.65f)
                .shadow(false));

        return section;
    }

    // ==================== 按钮区 ====================

    /** 五个动作按钮：发送 / 查询 / 设置首登 / 清除首登（确认）/ 发布一次性（确认） */
    private static IWidget createButtonSection() {
        ParentWidget<?> section = new ParentWidget<>().pos(0, 0)
            .size(PAGE_WIDTH, PAGE_HEIGHT);

        // 发送邮件（目标 = 选中玩家；未选中不发包）
        section.child(
            new ButtonWidget<>().pos(6, BTN_ROW1_Y)
                .size(BTN_W, BTN_H)
                .background(NekoGuiTextures.TEXT_FIELD_BACKGROUND)
                .overlay(IKey.str(BTN_SEND))
                .tooltipBuilder(t -> {
                    t.addLine(IKey.str("向选中玩家发送管理员邮件"));
                    t.addLine(IKey.str(EnumChatFormatting.GRAY + "标题必填；附件=服务端手持物品"));
                })
                .tooltipAutoUpdate(true)
                .onMouseTapped(
                    mouse -> trySendAction(
                        mouse,
                        TerminalActionHandler.ACTION_MAIL_SEND,
                        selectedPlayer,
                        draftTitle,
                        draftBody,
                        "")));

        // 查询模板（服务端推送首登模板到信息区）
        section.child(
            new ButtonWidget<>().pos(PAGE_WIDTH - BTN_W - 6, BTN_ROW1_Y)
                .size(BTN_W, BTN_H)
                .background(NekoGuiTextures.TEXT_FIELD_BACKGROUND)
                .overlay(IKey.str(BTN_QUERY))
                .tooltipBuilder(t -> t.addLine(IKey.str(EnumChatFormatting.GRAY + "刷新下方首登模板信息区")))
                .tooltipAutoUpdate(true)
                .onMouseTapped(
                    mouse -> trySendAction(mouse, TerminalActionHandler.ACTION_MAIL_FIRST_QUERY, "", "", "", "")));

        // 设置首登模板（覆盖旧的；标题必填）
        section.child(
            new ButtonWidget<>().pos(6, BTN_ROW2_Y)
                .size(BTN_W, BTN_H)
                .background(NekoGuiTextures.TEXT_FIELD_BACKGROUND)
                .overlay(IKey.str(BTN_FIRST_SET))
                .tooltipBuilder(t -> {
                    t.addLine(IKey.str("用当前标题/正文/附件覆盖首登奖励模板"));
                    t.addLine(IKey.str(EnumChatFormatting.GRAY + "新玩家首次登录自动投递（老玩家不补发）"));
                })
                .tooltipAutoUpdate(true)
                .onMouseTapped(
                    mouse -> trySendAction(
                        mouse,
                        TerminalActionHandler.ACTION_MAIL_FIRST_SET,
                        "",
                        draftTitle,
                        draftBody,
                        "")));

        // 清除首登模板（二次确认；未确认不发包）
        ButtonWidget<?> clearBtn = new ButtonWidget<>().pos(PAGE_WIDTH - BTN_W - 6, BTN_ROW2_Y)
            .size(BTN_W, BTN_H)
            .background(NekoGuiTextures.TEXT_FIELD_BACKGROUND)
            .overlay(IKey.str(BTN_FIRST_CLEAR));
        clearBtn.tooltipBuilder(t -> t.addLine(IKey.str(EnumChatFormatting.RED + "清除首登奖励模板（需二次确认）")));
        clearBtn.tooltipAutoUpdate(true);
        clearBtn.onMouseTapped(mouse -> {
            if (mouse != 0) return false;
            openConfirmDialog(
                clearBtn,
                CONFIRM_FIRST_CLEAR,
                () -> sendActionNow(TerminalActionHandler.ACTION_MAIL_FIRST_CLEAR, "", "", "", ""));
            return true;
        });
        section.child(clearBtn);

        // 发布一次性奖励（二次确认；未确认不发包；奖励ID 必填本地预检）
        ButtonWidget<?> onceBtn = new ButtonWidget<>().pos(6, BTN_ROW3_Y)
            .size(BTN_FULL_W, BTN_H)
            .background(NekoGuiTextures.TEXT_FIELD_BACKGROUND)
            .overlay(IKey.str(BTN_ONCE));
        onceBtn.tooltipBuilder(t -> {
            t.addLine(IKey.str("按奖励ID发布全服一次性奖励邮件"));
            t.addLine(IKey.str(EnumChatFormatting.GRAY + "使用当前标题/正文/附件；在线立即送达，离线登录补投"));
        });
        onceBtn.tooltipAutoUpdate(true);
        onceBtn.onMouseTapped(mouse -> {
            if (mouse != 0 || draftRewardId.trim()
                .isEmpty()) {
                return true;
            }
            openConfirmDialog(
                onceBtn,
                CONFIRM_ONCE,
                () -> sendActionNow(
                    TerminalActionHandler.ACTION_MAIL_ONCE,
                    "",
                    draftTitle,
                    draftBody,
                    draftRewardId.trim()));
            return true;
        });
        section.child(onceBtn);

        return section;
    }

    // ==================== 模板信息区 ====================

    /** 首登模板信息区：标题/正文首行/是否带附件（IKey.dynamic 绑定客户端缓存） */
    private static IWidget createTemplateSection() {
        ParentWidget<?> section = new ParentWidget<>().pos(0, 0)
            .size(PAGE_WIDTH, PAGE_HEIGHT);

        section.child(
            new TextWidget<>(IKey.str(EnumChatFormatting.GRAY + TEMPLATE_HEADER)).pos(0, TEMPLATE_HEADER_Y)
                .size(PAGE_WIDTH, 9)
                .textAlign(Alignment.Center)
                .scale(0.75f)
                .shadow(false));
        section.child(
            new TextWidget<>(
                IKey.dynamic(
                    () -> EnumChatFormatting.GRAY + "标题: "
                        + (TerminalClientData.getMailTemplateTitle()
                            .isEmpty() ? EnumChatFormatting.DARK_GRAY + TEMPLATE_EMPTY
                                : TerminalClientData.getMailTemplateTitle()))).pos(4, TEMPLATE_TITLE_Y)
                                    .size(PAGE_WIDTH - 8, 9)
                                    .scale(0.8f)
                                    .shadow(false));
        section.child(
            new TextWidget<>(
                IKey.dynamic(
                    () -> EnumChatFormatting.GRAY + "正文: "
                        + (TerminalClientData.getMailTemplateBody()
                            .isEmpty() ? EnumChatFormatting.DARK_GRAY + TEMPLATE_BODY_EMPTY
                                : firstBodyLine(TerminalClientData.getMailTemplateBody())))).pos(4, TEMPLATE_BODY_Y)
                                    .size(PAGE_WIDTH - 8, 9)
                                    .scale(0.8f)
                                    .shadow(false));
        section.child(
            new TextWidget<>(
                IKey.dynamic(
                    () -> EnumChatFormatting.GRAY + "附件: "
                        + (TerminalClientData.isMailTemplateHasAttachment() ? EnumChatFormatting.GREEN + "有"
                            : EnumChatFormatting.RED + "无"))).pos(4, TEMPLATE_ATTACH_Y)
                                .size(PAGE_WIDTH - 8, 9)
                                .scale(0.8f)
                                .shadow(false));

        return section;
    }

    // ==================== 动作发送与二次确认 ====================

    /**
     * 按钮点击统一入口：本地预检 + 1.5s 冷却 + 发包
     *
     * @return true 表示点击已消费（MUI2 约定阻止事件继续传播）
     */
    private static boolean trySendAction(int mouse, int action, String target, String text1, String text2,
        String text3) {
        if (mouse != 0) return false;
        sendActionNow(action, target, text1, text2, text3);
        return true;
    }

    /** 冷却检查后发包（冷却中静默忽略，防连点重复发包） */
    private static void sendActionNow(int action, String target, String text1, String text2, String text3) {
        long now = System.currentTimeMillis();
        if (now - lastActionAt < ACTION_COOLDOWN_MS) return;
        lastActionAt = now;
        TerminalNetworkManager.sendAction(action, target, 0, text1, text2, text3);
    }

    /**
     * 打开二次确认弹框（纯客户端 Dialog；范式参照 {@code gui/vm/edit/TradeEditor} 的
     * {@code NekoConfirmationDialog} + {@code IPanelHandler.simple} 用法，
     * 以触发按钮所在屏幕的主面板为宿主）
     */
    private static void openConfirmDialog(ButtonWidget<?> anchor, String message, Runnable onConfirm) {
        ModularScreen screen = anchor.getScreen();
        if (screen == null) return;
        NekoConfirmationDialog dialog = new NekoConfirmationDialog("gtit_terminal:mail_confirm");
        dialog.setParams(message, onConfirm);
        IPanelHandler.simple(screen.getMainPanel(), (parent, player) -> dialog, true)
            .openPanel();
    }

    // ==================== 数据辅助（动态 Supplier 调用） ====================

    /** 在线玩家名快照（不可变副本） */
    private static List<String> onlinePlayers() {
        return TerminalClientData.getOnlinePlayers();
    }

    /** 玩家列表最大页码（0 起；快照为空时 0） */
    private static int maxPlayerPage() {
        int size = onlinePlayers().size();
        return size == 0 ? 0 : (size - 1) / PLAYERS_VISIBLE;
    }

    /** 当前页第 index 行的玩家名（页码先 clamp 防越界；越界返回 null） */
    private static String playerAt(int index) {
        List<String> names = onlinePlayers();
        int page = Math.min(playerListPage, maxPlayerPage());
        int i = page * PLAYERS_VISIBLE + index;
        return i >= 0 && i < names.size() ? names.get(i) : null;
    }

    /** 正文首行（多行正文按 \n 取第一行，供信息区单行展示） */
    private static String firstBodyLine(String body) {
        String line = body.split("\n", 2)[0];
        return line.isEmpty() ? TEMPLATE_BODY_EMPTY : line;
    }
}

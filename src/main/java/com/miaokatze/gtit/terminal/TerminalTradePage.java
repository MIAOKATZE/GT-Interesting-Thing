package com.miaokatze.gtit.terminal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.util.EnumChatFormatting;

import com.cleanroommc.modularui.api.IPanelHandler;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.value.StringValue;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.ListWidget;
import com.cleanroommc.modularui.widgets.PagedWidget;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;
import com.miaokatze.gtit.client.gui.NekoConfirmationDialog;

/**
 * 管理终端-交易/冷却运维页（T4 实装，纯客户端 MUI2）
 * <p>
 * 静态页面构建类（范式照抄 {@code gui/signin/SignInCalendarGui#createSignInPage} 与
 * {@code mail/MailGui} 的输入框/冷却范式），由 {@link TerminalGui} 主内容 PagedWidget
 * 添加为页 {@link TerminalGui#PAGE_TRADE}。简单运维页布局：说明区 + 目标玩家选择
 * （在线列表点击选中 / 手动输入支持离线名）+ 三个固定运维动作按钮：
 * <ol>
 * <li>重载交易注册表（服务端 reload 成功后自动向全服玩家同步）</li>
 * <li>重载抽奖配置（服务端单调用 LotteryManager.loadConfig 重建卡池）</li>
 * <li>重置交易历史与冷却（{@link NekoConfirmationDialog} 二次确认后才发包；
 * 影响目标所在整个队伍，不可恢复）</li>
 * </ol>
 * 动作经 {@link TerminalNetworkManager#sendAction} 走 {@link TerminalActionHandler}
 * 五步校验链；结果由顶部共享回显区（{@code TerminalClientData#getResultDisplayLine}）显示。
 * <p>
 * <b>纯客户端约束</b>：无 MUI2 同步值/槽位（TextFieldWidget 绑定的
 * {@code StringValue.Dynamic} 为本地值，纯客户端 CustomModularScreen 无 Container，
 * 不产生任何同步包），不调用 {@code getSyncManager()}/{@code getContainer()}。
 */
public final class TerminalTradePage {

    /** 页宽（与 TerminalGui 主内容区一致） */
    public static final int PAGE_WIDTH = TerminalGui.CONTENT_WIDTH;
    /** 页高 */
    public static final int PAGE_HEIGHT = TerminalGui.CONTENT_HEIGHT;

    // ==================== 布局常量（页内局部坐标） ====================

    /** 目标玩家输入框 X */
    private static final int FIELD_X = 42;
    /** 目标玩家输入框 Y */
    private static final int FIELD_Y = 36;
    /** 在线玩家列表 Y */
    private static final int LIST_Y = 63;
    /** 在线玩家列表高（到按钮区上方） */
    private static final int LIST_H = 97;
    /** 在线玩家单行高 */
    private static final int LIST_ROW_H = 12;
    /** 动作按钮行 Y（自上而下：重载交易/重载抽奖/重置历史） */
    private static final int BTN1_Y = 166;
    /** 动作按钮行 2 Y */
    private static final int BTN2_Y = 186;
    /** 动作按钮行 3 Y */
    private static final int BTN3_Y = 206;
    /** 动作按钮尺寸 */
    private static final int BTN_W = PAGE_WIDTH - 8;
    /** 动作按钮高 */
    private static final int BTN_H = 16;

    /** 动作发送冷却（毫秒，客户端防抖防连点，三按钮共享） */
    private static final long ACTION_COOLDOWN_MS = 1500L;

    // ==================== 页内硬编码文案常量 ====================

    /** 页名 */
    private static final String TITLE = "交易管理";
    /** 说明行 1（结果说明区补充） */
    private static final String DESC_1 = "交易重载后所有在线玩家会收到同步包";
    /** 说明行 2 */
    private static final String DESC_2 = "重置交易历史会影响目标所在整个队伍";
    /** 目标玩家标签 */
    private static final String LABEL_TARGET = "目标玩家:";
    /** 在线列表标签 */
    private static final String LABEL_ONLINE = "在线玩家（点击选中，输入框支持离线名）";
    /** 在线列表为空提示 */
    private static final String EMPTY_LIST = "（打开时无在线玩家快照，请手动输入目标名）";
    /** 按钮：重载交易注册表 */
    private static final String BTN_TRADE_RELOAD = "重载交易注册表";
    /** 按钮：重载抽奖配置 */
    private static final String BTN_LOTTERY_RELOAD = "重载抽奖配置";
    /** 按钮：重置交易历史与冷却 */
    private static final String BTN_TIME_RESET = "重置交易历史与冷却";
    /** 重载交易按钮 tooltip（任务包指定文案） */
    private static final String TOOLTIP_TRADE_RELOAD_SYNC = "重载后自动向全服玩家同步";
    /** TIME_RESET 二次确认文案（任务包指定：必须含不可逆与队伍影响警示） */
    private static final String CONFIRM_TIME_RESET = "确认重置交易历史与冷却？将清空目标玩家的交易历史与冷却记录，若其属于团队则影响整个队伍，不可恢复";

    // ==================== 客户端本地状态（页内静态字段） ====================

    /** 目标玩家名（在线列表选中或手动输入；GUI 重开保留，重置动作发送参数） */
    private static volatile String targetPlayerName = "";

    /** 当前目标输入框实例（列表点击回填显示用；GUI 重建替换，仅客户端主线程访问） */
    private static TextFieldWidget targetNameField;

    /** 上次动作发送时间戳（三按钮共享 1.5s 冷却） */
    private static volatile long lastActionSendMs;

    private TerminalTradePage() {
        // 静态工具类，禁止实例化
    }

    /**
     * 构建交易/冷却运维页
     *
     * @param controller 主内容分页控制器（本页未用子分页）
     * @return 固定尺寸页根 Widget
     */
    public static IWidget createPage(PagedWidget.Controller controller) {
        ParentWidget<?> page = new ParentWidget<>().size(PAGE_WIDTH, PAGE_HEIGHT);

        // ---- 说明区（页名 + 两行静态说明；动作结果由顶部共享回显区承担） ----
        page.child(
            new TextWidget<>(IKey.str(TITLE)).pos(0, 0)
                .width(PAGE_WIDTH)
                .height(12)
                .textAlign(Alignment.CENTER));
        page.child(smallGrayLine(DESC_1, 13));
        page.child(smallGrayLine(DESC_2, 23));

        // ---- 目标玩家：手动输入（支持离线玩家名，重置动作用） ----
        page.child(
            new TextWidget<>(IKey.str(LABEL_TARGET)).pos(0, 38)
                .size(40, 10)
                .shadow(false));
        targetNameField = new TextFieldWidget().value(
            new StringValue.Dynamic(TerminalTradePage::getTargetPlayerName, TerminalTradePage::setTargetPlayerName));
        targetNameField.pos(FIELD_X, FIELD_Y)
            .size(PAGE_WIDTH - FIELD_X, 14);
        targetNameField.setMaxLength(TerminalActionPacket.MAX_TARGET_PLAYER_LENGTH);
        targetNameField.hintText("玩家名");
        targetNameField.tooltipBuilder(t -> {
            t.addLine(IKey.str("目标玩家名（重置交易历史动作用）"));
            t.addLine(IKey.str(EnumChatFormatting.GRAY + "支持离线玩家名（需在本服务器登录过），最长 16 字符"));
        });
        targetNameField.tooltipAutoUpdate(true);
        page.child(targetNameField);

        // ---- 目标玩家：在线列表快照点击选中（TerminalOpenPacket 打开时推送） ----
        page.child(smallGrayLine(LABEL_ONLINE, 53));
        page.child(createOnlinePlayerList());

        // ---- 三个固定运维动作按钮 ----
        page.child(createTradeReloadButton());
        page.child(createLotteryReloadButton());
        page.child(createTimeResetButton(page));

        return page;
    }

    // ==================== 子构件 ====================

    /** 小号灰色说明行 */
    private static TextWidget<?> smallGrayLine(String text, int y) {
        return new TextWidget<>(IKey.str(EnumChatFormatting.GRAY + text)).pos(0, y)
            .width(PAGE_WIDTH)
            .height(9)
            .scale(0.8f)
            .shadow(false);
    }

    /**
     * 在线玩家列表（可滚动；数据源为打开时刻快照，点击选中回填目标输入框）
     */
    private static ListWidget<IWidget, ?> createOnlinePlayerList() {
        ListWidget<IWidget, ?> playerList = new ListWidget<>().pos(0, LIST_Y)
            .size(PAGE_WIDTH, LIST_H);
        List<String> names = new ArrayList<>(TerminalClientData.getOnlinePlayers());
        Collections.sort(names);
        if (names.isEmpty()) {
            playerList.child(
                new TextWidget<>(IKey.str(EnumChatFormatting.DARK_GRAY + EMPTY_LIST)).pos(0, 0)
                    .width(PAGE_WIDTH - 6)
                    .height(9)
                    .scale(0.8f)
                    .shadow(false));
            return playerList;
        }
        for (String name : names) {
            playerList.child(createPlayerRow(name));
        }
        return playerList;
    }

    /** 单个在线玩家行按钮（选中态绿色高亮，动态绑定 volatile 选中字段） */
    private static ButtonWidget<?> createPlayerRow(String name) {
        ButtonWidget<?> row = new ButtonWidget<>().width(PAGE_WIDTH - 8)
            .height(LIST_ROW_H);
        row.overlay(IKey.dynamic(() -> name.equals(getTargetPlayerName()) ? EnumChatFormatting.GREEN + name : name));
        row.tooltipBuilder(t -> t.addLine(IKey.str("点击选中为目标玩家（重置交易历史动作用）")));
        row.onMouseTapped(mouse -> {
            if (mouse != 0) return false;
            setTargetPlayerName(name);
            syncFieldDisplay(name);
            return true;
        });
        return row;
    }

    /** 重载交易注册表按钮（无目标动作） */
    private static ButtonWidget<?> createTradeReloadButton() {
        return new ButtonWidget<>().pos(4, BTN1_Y)
            .size(BTN_W, BTN_H)
            .overlay(IKey.str(BTN_TRADE_RELOAD))
            .tooltipBuilder(t -> {
                t.addLine(IKey.str("重新读取交易配置与标签页并清空 BQ 触发器"));
                t.addLine(IKey.str(EnumChatFormatting.GRAY + TOOLTIP_TRADE_RELOAD_SYNC));
            })
            .tooltipAutoUpdate(true)
            .onMouseTapped(mouse -> {
                tryDispatchAction(mouse, TerminalActionHandler.ACTION_TRADE_RELOAD, "");
                return true;
            });
    }

    /** 重载抽奖配置按钮（无目标动作） */
    private static ButtonWidget<?> createLotteryReloadButton() {
        return new ButtonWidget<>().pos(4, BTN2_Y)
            .size(BTN_W, BTN_H)
            .overlay(IKey.str(BTN_LOTTERY_RELOAD))
            .tooltipBuilder(t -> {
                t.addLine(IKey.str("重新读取抽奖卡池配置并重建内存卡池"));
                t.addLine(IKey.str(EnumChatFormatting.GRAY + "无需重启服务器"));
            })
            .tooltipAutoUpdate(true)
            .onMouseTapped(mouse -> {
                tryDispatchAction(mouse, TerminalActionHandler.ACTION_LOTTERY_RELOAD, "");
                return true;
            });
    }

    /**
     * 重置交易历史与冷却按钮（目标动作；不可逆）
     * <p>
     * 未选/未填目标时禁用；点击先弹 {@link NekoConfirmationDialog} 二次确认，
     * 仅确认后才经 {@link #tryDispatchAction} 发 {@code ACTION_TRADE_TIME_RESET}——
     * 未确认（点取消/关弹框）不发包。
     *
     * @param page 页根 Widget（点击时取宿主面板打开确认弹框）
     */
    private static ButtonWidget<?> createTimeResetButton(ParentWidget<?> page) {
        return new ButtonWidget<>().pos(4, BTN3_Y)
            .size(BTN_W, BTN_H)
            .overlay(IKey.str(BTN_TIME_RESET))
            .setEnabledIf(w -> !getTargetPlayerName().isEmpty())
            .tooltipBuilder(t -> {
                t.addLine(IKey.str("清空目标玩家的全部交易历史与冷却记录"));
                t.addLine(IKey.str(EnumChatFormatting.GRAY + "影响目标所在整个队伍，不可恢复；需二次确认"));
            })
            .tooltipAutoUpdate(true)
            .onMouseTapped(mouse -> {
                if (mouse != 0) return false;
                String target = getTargetPlayerName();
                if (target.isEmpty()) return true; // 禁用态兜底：未选目标不弹框不发包
                openTimeResetConfirm(page, target);
                return true;
            });
    }

    /**
     * 打开重置二次确认弹框（NekoVMGuiV2 同款 IPanelHandler.simple 范式；
     * 点击时按钮已挂树，取宿主主面板打开）
     */
    private static void openTimeResetConfirm(ParentWidget<?> page, String target) {
        NekoConfirmationDialog dialog = new NekoConfirmationDialog("gtit_terminal:trade_time_reset_confirm");
        dialog.setParams(
            CONFIRM_TIME_RESET,
            () -> tryDispatchAction(0, TerminalActionHandler.ACTION_TRADE_TIME_RESET, target));
        IPanelHandler.simple(page.getPanel(), (parent, player) -> dialog, true)
            .openPanel();
    }

    // ==================== 状态与发送 ====================

    /** 目标玩家名（volatile，列表选中与输入框打字双源回写） */
    private static String getTargetPlayerName() {
        return targetPlayerName;
    }

    /** 目标玩家名写入（输入框 StringValue.Dynamic 打字回写路径） */
    private static void setTargetPlayerName(String name) {
        targetPlayerName = name == null ? "" : name;
    }

    /** 列表点击后回填输入框显示（TextFieldWidget.setText 仅改显示文本，值已由上方直接写入） */
    private static void syncFieldDisplay(String name) {
        TextFieldWidget field = targetNameField;
        if (field != null) {
            field.setText(name);
        }
    }

    /**
     * 动作发送（客户端防抖）：1.5s 共享冷却 + 经 {@link TerminalNetworkManager#sendAction}
     * 发往服务端五步校验链；冷却期内静默忽略
     *
     * @param mouse        鼠标键（非左键忽略）
     * @param action       动作常量（{@link TerminalActionHandler} ACTION_*）
     * @param targetPlayer 目标玩家名（无目标动作传空串）
     */
    private static void tryDispatchAction(int mouse, int action, String targetPlayer) {
        if (mouse != 0) return;
        long now = System.currentTimeMillis();
        if (now - lastActionSendMs < ACTION_COOLDOWN_MS) return;
        lastActionSendMs = now;
        TerminalNetworkManager.sendAction(action, targetPlayer, 0, "", "", "");
    }
}

package com.miaokatze.gtit.terminal;

import java.util.List;

import net.minecraft.util.EnumChatFormatting;

import com.cleanroommc.modularui.api.IPanelHandler;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.screen.ModularPanel;
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
 * 管理终端-礼包页（T5 实装，纯客户端 MUI2）
 * <p>
 * 静态页面构建类（范式照抄 {@code mail/MailGui} 列表页与 {@code gui/vm/edit/TradeEditor}
 * 二次确认弹框用法），由 {@link TerminalGui} 主内容 PagedWidget 添加为页
 * {@link TerminalGui#PAGE_GIFT}。页面结构：
 * <ul>
 * <li>查询区：「刷新领取名单」按钮（{@code ACTION_GIFT_CLAIM_LIST}）+ 总数行
 * （{@code IKey.dynamic} 绑 {@link TerminalClientData#getGiftTotalCount}）+
 * 列表区（{@value #LIST_ROWS} 行，逐行 {@code IKey.dynamic} 绑
 * {@link TerminalClientData#getGiftListLines()} 指定行，服务端限 ≤100 行）</li>
 * <li>重置区：目标玩家选择（在线列表循环选择器 + {@code TextFieldWidget} 手动输入
 * 离线名 ≤16，手动输入优先）+「重置领取状态」按钮（{@code ACTION_GIFT_CLAIM_RESET}，
 * {@link NekoConfirmationDialog} 二次确认后才发包）</li>
 * </ul>
 * <b>纯客户端约束</b>：无 MUI2 同步值/槽位调用；动态文本一律 {@code IKey.dynamic}
 * 惰性求值；页内状态为静态 {@code volatile} 字段（GUI 重开保留，渲染线程读取安全）；
 * 所有动作经 {@link TerminalNetworkManager#sendAction} 发往服务端五步校验链，
 * 未通过二次确认不发包。查询/重置按钮共享 {@value #SEND_COOLDOWN_MS}ms 客户端冷却。
 */
public final class TerminalGiftPage {

    /** 页宽（与 TerminalGui 主内容区一致） */
    public static final int PAGE_WIDTH = TerminalGui.CONTENT_WIDTH;
    /** 页高 */
    public static final int PAGE_HEIGHT = TerminalGui.CONTENT_HEIGHT;

    // ---- 布局常量（页面局部坐标，158x226） ----

    /** 列表可见行数（含首行汇总行） */
    private static final int LIST_ROWS = 10;
    /** 列表行高 */
    private static final int LIST_ROW_H = 10;
    /** 列表区起始 Y */
    private static final int LIST_Y = 34;
    /** 刷新按钮宽/高 */
    private static final int REFRESH_BTN_W = 72;
    private static final int BTN_H = 14;

    /** 手动输入框 X/Y/宽（标签列右侧） */
    private static final int FIELD_X = 28;
    private static final int FIELD_Y = 150;
    private static final int FIELD_W = PAGE_WIDTH - FIELD_X - 6;

    /** 在线选择行 Y（左右循环按钮 + 当前选中名） */
    private static final int ONLINE_ROW_Y = 168;
    /** 在线循环按钮边长 */
    private static final int ONLINE_BTN_SIZE = 12;

    /** 重置按钮宽/高/Y */
    private static final int RESET_BTN_W = 90;
    private static final int RESET_BTN_H = 16;
    private static final int RESET_BTN_Y = 194;

    /** 手动输入玩家名上限（MC 玩家名 ≤16，与包侧 clamp 一致） */
    private static final int MAX_NAME_LENGTH = TerminalActionPacket.MAX_TARGET_PLAYER_LENGTH;
    /** 查询/重置发包冷却（毫秒，客户端防抖防连点） */
    private static final long SEND_COOLDOWN_MS = 1500L;

    // ==================== 页内静态状态（GUI 重开保留；volatile 供渲染线程 IKey.dynamic 读取） ====================

    /** 手动输入的目标玩家名（TextFieldWidget 双向绑定；空=未手动指定，用在线选中目标） */
    private static volatile String manualTargetName = "";
    /** 在线列表循环选择器当前索引（对 {@link TerminalClientData#getOnlinePlayers()} 取模使用） */
    private static volatile int selectedOnlineIndex = 0;
    /** 上次刷新名单发包时刻（毫秒） */
    private static volatile long lastListSendMs;
    /** 上次重置发包时刻（毫秒） */
    private static volatile long lastResetSendMs;

    // ==================== 重置二次确认弹框（懒构建；宿主面板重建时换绑） ====================

    /** 确认弹框实例（复用同一实例，点击前 setParams 刷新文案） */
    private static NekoConfirmationDialog resetConfirmDialog;
    /** 弹框打开 handler（纯客户端 simple 面板） */
    private static IPanelHandler resetConfirmPanel;
    /** 弹框绑定的宿主面板（GUI 重开新面板时重建 handler，防跨 screen 打开） */
    private static ModularPanel resetConfirmParent;

    private TerminalGiftPage() {
        // 静态工具类，禁止实例化
    }

    /**
     * 构建礼包页
     *
     * @param controller 主内容分页控制器
     * @return 固定尺寸页根 Widget
     */
    public static IWidget createPage(PagedWidget.Controller controller) {
        ParentWidget<?> page = new ParentWidget<>().size(PAGE_WIDTH, PAGE_HEIGHT);

        // 居中页名
        page.child(
            new TextWidget<>(IKey.str(EnumChatFormatting.GOLD + "新手礼包管理")).pos(0, 2)
                .width(PAGE_WIDTH)
                .height(12)
                .textAlign(Alignment.CENTER)
                .shadow(false));

        page.child(createListSection());
        page.child(createResetSection());

        return page;
    }

    // ==================== 查询区：刷新按钮 + 总数行 + 列表 ====================

    private static IWidget createListSection() {
        ParentWidget<?> section = new ParentWidget<>().size(PAGE_WIDTH, RESET_BTN_Y);

        // 刷新按钮（1.5s 冷却；结果经顶部状态回显区 + 列表数据推送刷新）
        section.child(
            new ButtonWidget<>().pos(2, 16)
                .size(REFRESH_BTN_W, BTN_H)
                .background(NekoGuiTextures.TEXT_FIELD_BACKGROUND)
                .overlay(IKey.str(EnumChatFormatting.WHITE + "刷新领取名单"))
                .tooltipBuilder(t -> {
                    t.addLine(IKey.str("查询已领取新手礼包的玩家（在线+离线）"));
                    t.addLine(IKey.str(EnumChatFormatting.GRAY + "名单最多回传 100 行，完整总数见右侧"));
                })
                .tooltipAutoUpdate(true)
                .onMouseTapped(mouse -> {
                    if (mouse != 0) return false;
                    long now = System.currentTimeMillis();
                    if (now - lastListSendMs < SEND_COOLDOWN_MS) return true;
                    lastListSendMs = now;
                    TerminalNetworkManager.sendAction(TerminalActionHandler.ACTION_GIFT_CLAIM_LIST, "", 0, "", "", "");
                    return true;
                }));

        // 总数行（IKey.dynamic 绑 getGiftTotalCount，未查询时为 0）
        section.child(
            new TextWidget<>(
                IKey.dynamic(() -> EnumChatFormatting.YELLOW + "共 " + TerminalClientData.getGiftTotalCount() + " 人已领取"))
                    .pos(REFRESH_BTN_W + 4, 18)
                    .width(PAGE_WIDTH - REFRESH_BTN_W - 8)
                    .height(10)
                    .textAlign(Alignment.CENTER)
                    .scale(0.75f)
                    .shadow(false));

        // 未查询占位提示（已有数据时隐藏）
        section.child(
            new TextWidget<>(IKey.str(EnumChatFormatting.GRAY + "尚未查询，点击上方按钮刷新名单")).pos(0, LIST_Y + 3 * LIST_ROW_H)
                .width(PAGE_WIDTH)
                .height(10)
                .textAlign(Alignment.CENTER)
                .scale(0.75f)
                .shadow(false)
                .setEnabledIf(
                    w -> TerminalClientData.getGiftListLines()
                        .isEmpty()));

        // 列表行（预建 LIST_ROWS 行，IKey.dynamic 逐行绑 getGiftListLines()，越界行显示空串）
        for (int i = 0; i < LIST_ROWS; i++) {
            final int row = i;
            section.child(
                new TextWidget<>(IKey.dynamic(() -> giftListLine(row))).pos(4, LIST_Y + i * LIST_ROW_H)
                    .width(PAGE_WIDTH - 8)
                    .height(LIST_ROW_H)
                    .scale(0.75f)
                    .shadow(false));
        }

        return section;
    }

    // ==================== 重置区：目标选择 + 手动输入 + 二次确认重置 ====================

    private static IWidget createResetSection() {
        ParentWidget<?> section = new ParentWidget<>().size(PAGE_WIDTH, PAGE_HEIGHT);

        // 重置区标题
        section.child(
            new TextWidget<>(IKey.str(EnumChatFormatting.AQUA + "—— 重置领取状态 ——")).pos(0, 138)
                .width(PAGE_WIDTH)
                .height(10)
                .textAlign(Alignment.CENTER)
                .scale(0.75f)
                .shadow(false));

        // 手动输入行（离线玩家名；手动输入优先于在线选择）
        section.child(
            new TextWidget<>(IKey.str("目标:")).pos(2, FIELD_Y + 3)
                .size(FIELD_X - 4, 10)
                .shadow(false));
        TextFieldWidget nameField = new TextFieldWidget()
            .value(new StringValue.Dynamic(() -> manualTargetName, val -> manualTargetName = val))
            .setMaxLength(MAX_NAME_LENGTH);
        nameField.pos(FIELD_X, FIELD_Y)
            .size(FIELD_W, BTN_H);
        nameField.tooltipBuilder(t -> {
            t.addLine(IKey.str("手动输入目标玩家名（可输离线玩家，优先于在线选择）"));
            t.addLine(IKey.str(EnumChatFormatting.GRAY + "最长 " + MAX_NAME_LENGTH + " 字符"));
        });
        nameField.tooltipAutoUpdate(true);
        section.child(nameField);

        // 在线选择行：循环切换按钮 + 当前选中名（打开终端时的在线快照）
        section.child(
            new TextWidget<>(IKey.str("在线:")).pos(2, ONLINE_ROW_Y + 2)
                .size(FIELD_X - 4, 10)
                .shadow(false));
        section.child(
            new ButtonWidget<>().pos(FIELD_X, ONLINE_ROW_Y)
                .size(ONLINE_BTN_SIZE, ONLINE_BTN_SIZE)
                .background(NekoGuiTextures.TEXT_FIELD_BACKGROUND)
                .overlay(IKey.str("<"))
                .onMouseTapped(mouse -> {
                    if (mouse == 0) shiftOnlineSelection(-1);
                    return true;
                }));
        section.child(
            new TextWidget<>(IKey.dynamic(() -> EnumChatFormatting.WHITE + selectedOnlineName()))
                .pos(FIELD_X + ONLINE_BTN_SIZE + 2, ONLINE_ROW_Y + 1)
                .size(FIELD_W - ONLINE_BTN_SIZE * 2 - 4, 10)
                .textAlign(Alignment.CENTER)
                .scale(0.8f)
                .shadow(false)
                .setEnabledIf(
                    w -> !TerminalClientData.getOnlinePlayers()
                        .isEmpty()));
        section.child(
            new ButtonWidget<>().pos(FIELD_X + FIELD_W - ONLINE_BTN_SIZE, ONLINE_ROW_Y)
                .size(ONLINE_BTN_SIZE, ONLINE_BTN_SIZE)
                .background(NekoGuiTextures.TEXT_FIELD_BACKGROUND)
                .overlay(IKey.str(">"))
                .onMouseTapped(mouse -> {
                    if (mouse == 0) shiftOnlineSelection(1);
                    return true;
                }));

        // 输入优先级提示行
        section.child(
            new TextWidget<>(IKey.str(EnumChatFormatting.GRAY + "手动输入优先；留空则重置在线选中目标")).pos(0, ONLINE_ROW_Y + 16)
                .width(PAGE_WIDTH)
                .height(8)
                .textAlign(Alignment.CENTER)
                .scale(0.7f)
                .shadow(false));

        // 重置按钮：二次确认后才发包（未确认不发包；确认回调内再过冷却闸）
        ButtonWidget<?> resetBtn = new ButtonWidget<>().pos((PAGE_WIDTH - RESET_BTN_W) / 2, RESET_BTN_Y)
            .size(RESET_BTN_W, RESET_BTN_H)
            .background(NekoGuiTextures.TEXT_FIELD_BACKGROUND)
            .overlay(IKey.str(EnumChatFormatting.RED + "重置领取状态"))
            .tooltipBuilder(t -> {
                t.addLine(IKey.str(EnumChatFormatting.RED + "重置目标的领取标记（点击后二次确认）"));
                t.addLine(IKey.str(EnumChatFormatting.GRAY + "重置后目标可重新领取新手礼包"));
            })
            .tooltipAutoUpdate(true)
            .setEnabledIf(w -> !resolveTargetName().isEmpty());
        resetBtn.onMouseTapped(mouse -> {
            if (mouse != 0) return false;
            String target = resolveTargetName();
            if (target.isEmpty()) return true;
            long now = System.currentTimeMillis();
            if (now - lastResetSendMs < SEND_COOLDOWN_MS) return true;
            openResetConfirm(resetBtn, target);
            return true;
        });
        section.child(resetBtn);

        return section;
    }

    // ==================== 弹框与发包辅助 ====================

    /**
     * 打开重置二次确认弹框（范式照抄 TradeEditor 删除确认：复用实例 + setParams + openPanel）。
     * <p>
     * 懒构建；宿主面板换新（GUI 重开新 screen）时重建 handler，防跨 screen 打开。
     * 确认回调内才发 {@code ACTION_GIFT_CLAIM_RESET}（未确认不发包），并再过一次冷却闸。
     *
     * @param anchor     重置按钮（点击时已挂树，用于取宿主 ModularPanel）
     * @param targetName 弹框时刻确定的目标玩家名（弹框期间下层面板被禁用，目标不可再改）
     */
    private static void openResetConfirm(ButtonWidget<?> anchor, String targetName) {
        ModularPanel hostPanel = anchor.getPanel();
        if (resetConfirmDialog == null || resetConfirmPanel == null || resetConfirmParent != hostPanel) {
            resetConfirmDialog = new NekoConfirmationDialog("gtit_terminal:gift_reset_confirm");
            resetConfirmPanel = IPanelHandler.simple(hostPanel, (parent, player) -> resetConfirmDialog, true);
            resetConfirmParent = hostPanel;
        }
        resetConfirmDialog.setButtonText("确认重置", "取消");
        resetConfirmDialog.setParams(TerminalText.CONFIRM_GIFT_CLAIM_RESET + "\n目标：" + targetName, () -> {
            long now = System.currentTimeMillis();
            if (now - lastResetSendMs < SEND_COOLDOWN_MS) return;
            lastResetSendMs = now;
            TerminalNetworkManager.sendAction(TerminalActionHandler.ACTION_GIFT_CLAIM_RESET, targetName, 0, "", "", "");
        });
        resetConfirmPanel.openPanel();
    }

    /** 在线选择器循环移动（空列表/单元素归零不动作） */
    private static void shiftOnlineSelection(int delta) {
        List<String> names = TerminalClientData.getOnlinePlayers();
        if (names.size() <= 1) {
            selectedOnlineIndex = 0;
            return;
        }
        selectedOnlineIndex = Math.floorMod(selectedOnlineIndex + delta, names.size());
    }

    /** 在线选择器当前显示名（空列表显示占位） */
    private static String selectedOnlineName() {
        List<String> names = TerminalClientData.getOnlinePlayers();
        if (names.isEmpty()) return "无在线玩家";
        return names.get(Math.floorMod(selectedOnlineIndex, names.size()));
    }

    /** 最终目标名：手动输入优先，留空回退在线选中目标（两者皆空返回空串） */
    private static String resolveTargetName() {
        String manual = manualTargetName == null ? "" : manualTargetName.trim();
        if (!manual.isEmpty()) return manual;
        List<String> names = TerminalClientData.getOnlinePlayers();
        if (names.isEmpty()) return "";
        return names.get(Math.floorMod(selectedOnlineIndex, names.size()));
    }

    /** 名单第 index 行（越界返回空串；数据来自服务端限长推送，非客户端拼装） */
    private static String giftListLine(int index) {
        List<String> lines = TerminalClientData.getGiftListLines();
        return index >= 0 && index < lines.size() ? lines.get(index) : "";
    }
}

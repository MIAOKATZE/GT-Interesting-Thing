package com.miaokatze.gtit.mail;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
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
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.value.StringValue;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.PagedWidget;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;
import com.miaokatze.gtit.client.gui.NekoComposeTextEditor;
import com.miaokatze.gtit.client.gui.NekoGuiTextures;

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;

/**
 * 邮件 GUI（v1.7.6 G2② 类型分页 + 写邮件系统）
 * <p>
 * 作为 {@code NekoVMGuiV2} 主内容 {@code PagedWidget} 的「邮件」页嵌入（页索引 3）。
 * 页面结构（v1.7.6 重构）：
 * <ul>
 * <li>顶部公共标题行（「猫猫邮件」+ 未读数）</li>
 * <li>内层 {@code PagedWidget}（绑定 {@code NekoVMGuiV2#mailPageController}，5 个 sub-page）：
 * <ol>
 * <li>全部（{@link #FILTER_ALL}，不过滤）</li>
 * <li>系统（{@link Mail#TYPE_SYSTEM}）</li>
 * <li>玩家（{@link Mail#TYPE_PLAYER}）</li>
 * <li>管理员（{@link Mail#TYPE_ADMIN}）</li>
 * <li>写邮件（compose 视图，无列表）</li>
 * </ol>
 * 左侧 sub-tab 标签列由 {@code NekoVMGuiV2#createSubTabColumn} 提供（与本 PagedWidget 同控制器）。</li>
 * </ul>
 * 列表视图（前 4 页共用模板，仅过滤类型不同）：邮件列表（每页 {@value #ENTRY_COUNT} 条）+
 * 翻页行 + 详情区（标题/发件人时间/正文 {@value #CONTENT_LINES} 行/附件槽/领取删除按钮）。
 * <p>
 * <b>布局约束</b>：背包行顶部在主内容区 Y≈231（G1 用户裁决），本页全部交互内容
 * 收进 Y≤{@value #CONTENT_BOTTOM}（内层 PagedWidget 高 {@value #INNER_HEIGHT}），
 * 避免被背包行遮挡拦截点击。
 * <p>
 * <b>双端安全</b>：本类整条调用链仅客户端构建（{@code NekoVMGuiV2} 仅客户端创建主内容
 * PagedWidget）；写邮件页的 {@link NekoComposeTextEditor} 为纯客户端组件。
 * 邮件操作（已读/领取/删除/互寄）完全由 {@link MailManager} 服务端权威执行，
 * 本页仅通过 {@link MailNetworkManager} 发起请求。
 */
public class MailGui {

    // ==================== 布局常量 ====================

    /** 页面宽度（主内容区 = PANEL_WIDTH - 8） */
    private static final int PAGE_WIDTH = 170;
    /** 页面高度（主内容区 = PANEL_HEIGHT - 8） */
    private static final int PAGE_HEIGHT = 312;

    /** 交互内容底部上限（背包行顶部 Y≈231，留 2px 余量） */
    private static final int CONTENT_BOTTOM = 229;
    /** 内层 PagedWidget Y（标题行之下） */
    private static final int INNER_Y = 16;
    /** 内层 PagedWidget 高度（底部不超过 CONTENT_BOTTOM） */
    private static final int INNER_HEIGHT = CONTENT_BOTTOM - INNER_Y;

    // ---- 列表视图（内层页面局部坐标，170 x INNER_HEIGHT） ----

    /** 条目宽（页面左右各留 2px） */
    private static final int ENTRY_W = 166;
    /** 条目 X */
    private static final int ENTRY_X = 2;
    /** 条目高（与 entry_*.png 素材一致） */
    private static final int ENTRY_H = 27;
    /** 条目间距 */
    private static final int ENTRY_GAP = 2;
    /** 每页条目数（v1.7.6 压缩为 3 条，为详情区腾出空间） */
    private static final int ENTRY_COUNT = 3;

    /** 翻页行 Y（列表区之下） */
    private static final int PAGER_Y = ENTRY_COUNT * (ENTRY_H + ENTRY_GAP) + 2;
    /** 翻页按钮宽 */
    private static final int PAGER_BTN_W = 20;
    /** 翻页按钮高 */
    private static final int PAGER_BTN_H = 12;

    /** 详情区 X（页面左右各留 2px） */
    private static final int DETAIL_X = 2;
    /** 详情区 Y（翻页行之下） */
    private static final int DETAIL_Y = PAGER_Y + PAGER_BTN_H + 2;
    /** 详情区宽 */
    private static final int DETAIL_W = 166;
    /** 详情区高（铺满内层页面余下空间：103→213 共 110px） */
    private static final int DETAIL_H = INNER_HEIGHT - DETAIL_Y;

    /** 详情正文行数 */
    private static final int CONTENT_LINES = 4;
    /** 详情正文行高 */
    private static final int CONTENT_LINE_H = 9;
    /** 详情正文起始 Y（详情区内） */
    private static final int CONTENT_Y = 25;

    /** 附件槽边长（与 slot.png 素材一致） */
    private static final int SLOT_SIZE = 18;
    /** 附件槽间距 */
    private static final int SLOT_GAP = 2;
    /** 附件槽行 Y（详情区内） */
    private static final int SLOT_Y = 64;

    /** 领取按钮宽（与 btn_claim.png 素材一致） */
    private static final int CLAIM_W = 48;
    /** 领取按钮高 */
    private static final int CLAIM_H = 16;
    /** 按钮行 Y（详情区内） */
    private static final int BTN_ROW_Y = 86;

    // ---- 写邮件视图（内层页面局部坐标） ----

    /** 输入框高 */
    private static final int FIELD_H = 14;
    /** 输入框 X（标签列右侧） */
    private static final int FIELD_X = 44;
    /** 输入框宽 */
    private static final int FIELD_W = 122;
    /** 正文编辑器 Y */
    private static final int EDITOR_Y = 46;
    /** 正文编辑器高 */
    private static final int EDITOR_H = 110;
    /** 发送/清空按钮行 Y */
    private static final int COMPOSE_BTN_Y = 182;

    /** 发送冷却（毫秒，客户端防抖，防连点重复寄出） */
    private static final long COMPOSE_COOLDOWN_MS = 1500L;

    // ==================== 颜色常量（ARGB） ====================

    /** 选中条目高亮框色（半透明金） */
    private static final int COLOR_SELECTED_FRAME = 0xCCFFC84A;
    /** 详情底纹填充色（米白，替代 v1.7.2 的 paper.png 底图——高度压缩后底图不再适用） */
    private static final int COLOR_PAPER_FILL = 0xFFF5EBD7;
    /** 详情底纹边框色（棕） */
    private static final int COLOR_PAPER_FRAME = 0xFF8B6F47;
    /** 详情正文颜色（米白底上的深紫灰） */
    private static final int COLOR_CONTENT = 0xFF4A3F55;
    /** 详情次要信息颜色（发件人/时间） */
    private static final int COLOR_META = 0xFF777788;

    /** 列表过滤：全部（不过滤） */
    private static final String FILTER_ALL = null;

    /** 邮件时间格式（MM-dd HH:mm） */
    private static final SimpleDateFormat TIME_FORMAT = new SimpleDateFormat("MM-dd HH:mm", Locale.ROOT);

    // ==================== 写邮件草稿（客户端本地状态，GUI 重开保留） ====================

    /** 草稿：收件人名（TextFieldWidget 绑定） */
    private static String composeRecipient = "";
    /** 草稿：标题（TextFieldWidget 绑定） */
    private static String composeTitle = "";
    /** 草稿：正文（编辑器变化监听器回写，GUI 重建后据此恢复） */
    private static String composeContent = "";
    /** 当前正文编辑器实例（发送/清空按钮操作；随 GUI 重建替换） */
    private static NekoComposeTextEditor composeEditor;
    /** 上次发送时间戳（客户端发送冷却） */
    private static long lastComposeSendMs;

    private MailGui() {}

    // ==================== 页面构建入口 ====================

    /**
     * 构建邮件页（供 {@code NekoVMGuiV2} 主内容 PagedWidget 添加为页 3）
     *
     * @param machine       触发机器的 GT TileEntity（写邮件附件=机器输入槽定位；可为 null，
     *                      null 时写邮件发送按钮禁用）
     * @param subController 邮件 sub-page 分页控制器（{@code NekoVMGuiV2#mailPageController}，
     *                      左侧 sub-tab 标签列与本 PagedWidget 共用）
     * @return 邮件页根 Widget（170x312，绝对布局）
     */
    public static IWidget createMailPage(IGregTechTileEntity machine, PagedWidget.Controller subController) {
        ParentWidget<?> page = new ParentWidget<>().size(PAGE_WIDTH, PAGE_HEIGHT);

        page.child(createTitle()); // 标题 + 未读数（公共区域）
        page.child(createSubPagedWidget(machine, subController)); // 内层 5 sub-page

        return page;
    }

    /**
     * 内层 sub-page PagedWidget：页 0-3 = 类型过滤列表视图，页 4 = 写邮件视图
     */
    private static IWidget createSubPagedWidget(IGregTechTileEntity machine, PagedWidget.Controller subController) {
        PagedWidget<?> subPaged = new PagedWidget<>().name("mailSubPaged")
            .pos(0, INNER_Y)
            .size(PAGE_WIDTH, INNER_HEIGHT)
            .controller(subController);

        // 页 0-3：全部/系统/玩家/管理员（同一模板，仅过滤类型不同）
        subPaged.addPage(createMailListView(FILTER_ALL));
        subPaged.addPage(createMailListView(Mail.TYPE_SYSTEM));
        subPaged.addPage(createMailListView(Mail.TYPE_PLAYER));
        subPaged.addPage(createMailListView(Mail.TYPE_ADMIN));
        // 页 4：写邮件
        subPaged.addPage(createComposeView(machine));

        return subPaged;
    }

    // ==================== 标题 ====================

    /** 标题行：「猫猫邮件」（金色居中）+ 未读数（右侧灰色，无未读时不显示） */
    private static IWidget createTitle() {
        ParentWidget<?> bar = new ParentWidget<>().pos(0, 2)
            .size(PAGE_WIDTH, 12);
        bar.child(
            new TextWidget<>(IKey.str(EnumChatFormatting.GOLD + "猫猫邮件")).pos(0, 0)
                .size(PAGE_WIDTH, 12)
                .textAlign(Alignment.Center)
                .shadow(false));
        bar.child(
            new TextWidget<>(
                IKey.dynamic(
                    () -> MailClientData.getUnreadCount() > 0
                        ? EnumChatFormatting.YELLOW + "未读 " + MailClientData.getUnreadCount()
                        : "")).pos(0, 2)
                            .size(PAGE_WIDTH - 4, 9)
                            .textAlign(Alignment.CenterRight)
                            .scale(0.75f)
                            .shadow(false));
        return bar;
    }

    // ==================== 列表视图（页 0-3 共用模板） ====================

    /**
     * 列表视图：邮件列表 + 翻页行 + 详情区
     *
     * @param filterType 过滤类型（{@link #FILTER_ALL}=不过滤；否则只显示该类型邮件）
     */
    private static IWidget createMailListView(final String filterType) {
        ParentWidget<?> view = new ParentWidget<>().size(PAGE_WIDTH, INNER_HEIGHT);

        view.child(createMailList(filterType)); // 邮件列表（3 条/页）
        view.child(createPager(filterType)); // 翻页行
        view.child(createDetailArea(filterType)); // 详情区（正文 + 附件 + 按钮）

        return view;
    }

    /**
     * 邮件列表：每页 {@value #ENTRY_COUNT} 条，条目结构（按添加顺序绘制）：
     * 条目底按钮（未读/已读纹理 + 点击选中）→ 邮件图标 → 标题 → 发件人/日期 →
     * 未读点 → 选中金框。无邮件的槽位整体隐藏；过滤后为空时显示空列表提示。
     */
    private static IWidget createMailList(final String filterType) {
        ParentWidget<?> list = new ParentWidget<>().pos(0, 0)
            .size(PAGE_WIDTH, ENTRY_COUNT * (ENTRY_H + ENTRY_GAP));

        // 空列表提示（仅过滤后无邮件时显示）
        list.child(
            new TextWidget<>(IKey.str(EnumChatFormatting.GRAY + "该分类暂无邮件")).pos(0, 30)
                .size(PAGE_WIDTH, 12)
                .textAlign(Alignment.Center)
                .shadow(false)
                .setEnabledIf(w -> filteredMails(filterType).isEmpty()));

        for (int i = 0; i < ENTRY_COUNT; i++) {
            final int index = i;
            int y = index * (ENTRY_H + ENTRY_GAP);

            // 条目底按钮（点击选中 + 未读时发已读请求）
            list.child(
                new ButtonWidget<>().pos(ENTRY_X, y)
                    .size(ENTRY_W, ENTRY_H)
                    .background(new DynamicDrawable(() -> entryTexture(filterType, index)))
                    .disableHoverBackground()
                    .tooltipBuilder(t -> entryTooltip(t, filterType, index))
                    .tooltipAutoUpdate(true)
                    .onMouseTapped(mouse -> {
                        Mail mail = entryMail(filterType, index);
                        if (mouse == 0 && mail != null) {
                            MailClientData.setSelectedMailId(mail.getId());
                            if (!mail.isRead()) {
                                MailNetworkManager.sendActionToServer(MailActionPacket.ACTION_READ, mail.getId());
                            }
                            return true;
                        }
                        return false;
                    })
                    .setEnabledIf(w -> entryMail(filterType, index) != null));

            // 邮件图标（未读=闭合信封，已读=开封信封）
            list.child(
                new IDrawable.DrawableWidget(new DynamicDrawable(() -> entryIcon(filterType, index)))
                    .pos(ENTRY_X + 4, y + 5)
                    .size(16, 16)
                    .setEnabledIf(w -> entryMail(filterType, index) != null));

            // 标题（第一行，未读加粗白字，已读灰字）
            list.child(
                new TextWidget<>(IKey.dynamic(() -> entryTitle(filterType, index))).pos(ENTRY_X + 24, y + 4)
                    .size(ENTRY_W - 44, 10)
                    .scale(0.8f)
                    .shadow(false)
                    .setEnabledIf(w -> entryMail(filterType, index) != null));

            // 发件人 + 日期（第二行，灰色小字）
            list.child(
                new TextWidget<>(IKey.dynamic(() -> entryMeta(filterType, index))).pos(ENTRY_X + 24, y + 16)
                    .size(ENTRY_W - 44, 9)
                    .scale(0.7f)
                    .color(0xFF666677)
                    .shadow(false)
                    .setEnabledIf(w -> entryMail(filterType, index) != null));

            // 未读亮点（右侧，仅未读显示）
            list.child(
                NekoGuiTextures.MAIL_DOT.asWidget()
                    .pos(ENTRY_X + ENTRY_W - 14, y + 9)
                    .size(8, 8)
                    .setEnabledIf(w -> {
                        Mail mail = entryMail(filterType, index);
                        return mail != null && !mail.isRead();
                    }));

            // 选中高亮框（仅选中条目显示）
            list.child(new IDrawable.DrawableWidget((context, dx, dy, w, h, theme) -> {
                Mail mail = entryMail(filterType, index);
                if (mail == null || !mail.getId()
                    .equals(MailClientData.getSelectedMailId())) return;
                drawFrame(dx, dy, w, h, COLOR_SELECTED_FRAME);
            }).pos(ENTRY_X - 1, y - 1)
                .size(ENTRY_W + 2, ENTRY_H + 2));
        }
        return list;
    }

    /** 条目 tooltip：标题 + 类型 + 发件人 + 时间 + 附件提示 */
    private static void entryTooltip(com.cleanroommc.modularui.screen.RichTooltip t, String filterType, int index) {
        Mail mail = entryMail(filterType, index);
        if (mail == null) return;
        t.addLine(IKey.str(EnumChatFormatting.GOLD + mail.getTitle()));
        t.addLine(
            IKey.str(
                EnumChatFormatting.GRAY + typeDisplayName(mail.getType())
                    + " · 来自 "
                    + mail.getSender()
                    + " · "
                    + TIME_FORMAT.format(new Date(mail.getTimestamp()))));
        if (mail.hasUnclaimedAttachments()) {
            t.addLine(IKey.str(EnumChatFormatting.YELLOW + "含未领取附件"));
        }
        t.addLine(IKey.str(EnumChatFormatting.AQUA + "点击查看详情"));
    }

    // ==================== 翻页行 ====================

    /** 翻页行：上一页 / 页码 / 下一页（居中排列；页码按过滤后总数计算） */
    private static IWidget createPager(final String filterType) {
        ParentWidget<?> row = new ParentWidget<>().pos(0, PAGER_Y)
            .size(PAGE_WIDTH, PAGER_BTN_H);
        int totalW = PAGER_BTN_W * 2 + 30;
        int x = (PAGE_WIDTH - totalW) / 2;

        row.child(
            new ButtonWidget<>().pos(x, 0)
                .size(PAGER_BTN_W, PAGER_BTN_H)
                .background(NekoGuiTextures.TEXT_FIELD_BACKGROUND)
                .overlay(IKey.str("<"))
                .onMouseTapped(mouse -> {
                    if (mouse == 0 && MailClientData.getListPage() > 0) {
                        MailClientData.setListPage(MailClientData.getListPage() - 1);
                        return true;
                    }
                    return false;
                }));
        row.child(
            new TextWidget<>(
                IKey.dynamic(
                    () -> EnumChatFormatting.GRAY + String.valueOf(MailClientData.getListPage() + 1)
                        + "/"
                        + (maxPage(filterType) + 1))).pos(x + PAGER_BTN_W + 2, 2)
                            .size(26, 9)
                            .textAlign(Alignment.Center)
                            .scale(0.75f)
                            .shadow(false));
        row.child(
            new ButtonWidget<>().pos(x + PAGER_BTN_W + 30, 0)
                .size(PAGER_BTN_W, PAGER_BTN_H)
                .background(NekoGuiTextures.TEXT_FIELD_BACKGROUND)
                .overlay(IKey.str(">"))
                .onMouseTapped(mouse -> {
                    if (mouse == 0 && MailClientData.getListPage() < maxPage(filterType)) {
                        MailClientData.setListPage(MailClientData.getListPage() + 1);
                        return true;
                    }
                    return false;
                }));
        return row;
    }

    // ==================== 详情区 ====================

    /**
     * 详情区：米白底纹（绘制矩形替代 v1.7.2 的 paper.png——高度压缩为 110px 后底图不再适用）
     * + 选中邮件的完整内容。
     * <p>
     * 未选中邮件时仅显示提示文本；选中后展示标题、发件人/时间/类型、正文
     * （预建 {@value #CONTENT_LINES} 行动态取行，无需重建 widget）、
     * 附件槽与领取/删除按钮。
     */
    private static IWidget createDetailArea(final String filterType) {
        ParentWidget<?> detail = new ParentWidget<>().pos(DETAIL_X, DETAIL_Y)
            .size(DETAIL_W, DETAIL_H);

        // 米白底纹 + 棕色边框（自绘矩形）
        detail.child(new IDrawable.DrawableWidget((context, dx, dy, w, h, theme) -> {
            GuiDraw.drawRect(dx, dy, w, h, COLOR_PAPER_FILL);
            drawFrame(dx, dy, w, h, COLOR_PAPER_FRAME);
        }).pos(0, 0)
            .size(DETAIL_W, DETAIL_H));

        // 未选中提示（仅无选中邮件时显示）
        detail.child(
            new TextWidget<>(IKey.str(EnumChatFormatting.GRAY + "选择一封邮件查看详情")).pos(0, 48)
                .size(DETAIL_W, 12)
                .textAlign(Alignment.Center)
                .shadow(false)
                .setEnabledIf(w -> selectedMail() == null));

        // 详情标题（居中，深紫灰）
        detail.child(new TextWidget<>(IKey.dynamic(() -> {
            Mail mail = selectedMail();
            return mail == null ? "" : EnumChatFormatting.DARK_PURPLE + mail.getTitle();
        })).pos(4, 3)
            .size(DETAIL_W - 8, 10)
            .textAlign(Alignment.Center)
            .shadow(false)
            .setEnabledIf(w -> selectedMail() != null));

        // 发件人 + 时间 + 类型
        detail.child(new TextWidget<>(IKey.dynamic(() -> {
            Mail mail = selectedMail();
            if (mail == null) return "";
            return "来自 " + mail.getSender()
                + " · "
                + TIME_FORMAT.format(new Date(mail.getTimestamp()))
                + " · "
                + typeDisplayName(mail.getType());
        })).pos(4, 14)
            .size(DETAIL_W - 8, 8)
            .textAlign(Alignment.Center)
            .scale(0.7f)
            .color(COLOR_META)
            .shadow(false)
            .setEnabledIf(w -> selectedMail() != null));

        // 正文（预建行，动态取 split 结果；越界行显示空串）
        for (int i = 0; i < CONTENT_LINES; i++) {
            final int line = i;
            detail.child(
                new TextWidget<>(IKey.dynamic(() -> contentLine(line))).pos(8, CONTENT_Y + i * CONTENT_LINE_H)
                    .size(DETAIL_W - 16, CONTENT_LINE_H)
                    .scale(0.75f)
                    .color(COLOR_CONTENT)
                    .shadow(false)
                    .setEnabledIf(w -> selectedMail() != null));
        }

        // 附件槽（最多 MAX_ATTACHMENTS 格；槽底 + 物品图标叠加）
        int slotsW = MailManager.MAX_ATTACHMENTS * (SLOT_SIZE + SLOT_GAP) - SLOT_GAP;
        int slotX = (DETAIL_W - slotsW) / 2;
        for (int i = 0; i < MailManager.MAX_ATTACHMENTS; i++) {
            final int index = i;
            int x = slotX + i * (SLOT_SIZE + SLOT_GAP);
            detail.child(
                NekoGuiTextures.MAIL_SLOT.asWidget()
                    .pos(x, SLOT_Y)
                    .size(SLOT_SIZE, SLOT_SIZE)
                    .setEnabledIf(w -> selectedMail() != null));
            detail.child(
                new IDrawable.DrawableWidget(new DynamicDrawable(() -> attachmentItem(index))).pos(x + 1, SLOT_Y + 1)
                    .size(16, 16)
                    .tooltipBuilder(t -> attachmentTooltip(t, index))
                    .tooltipAutoUpdate(true)
                    .setEnabledIf(w -> attachmentStack(index) != null));
        }

        // 领取附件按钮（仅有待领取附件时可用）
        detail.child(
            new ButtonWidget<>().pos((DETAIL_W - CLAIM_W) / 2 - 12, BTN_ROW_Y)
                .size(CLAIM_W, CLAIM_H)
                .background(NekoGuiTextures.MAIL_BTN_CLAIM)
                .disableHoverBackground()
                .overlay(IKey.str(EnumChatFormatting.WHITE + "领取"))
                .tooltipBuilder(t -> {
                    Mail mail = selectedMail();
                    if (mail != null && mail.hasUnclaimedAttachments()) {
                        t.addLine(IKey.str(EnumChatFormatting.YELLOW + "领取全部附件"));
                    }
                })
                .tooltipAutoUpdate(true)
                .onMouseTapped(mouse -> {
                    Mail mail = selectedMail();
                    if (mouse == 0 && mail != null && mail.hasUnclaimedAttachments()) {
                        MailNetworkManager.sendActionToServer(MailActionPacket.ACTION_CLAIM, mail.getId());
                        return true;
                    }
                    return false;
                })
                .setEnabledIf(w -> {
                    Mail mail = selectedMail();
                    return mail != null && mail.hasUnclaimedAttachments();
                }));

        // 删除按钮（选中邮件即显示；有未领取附件时服务端会拒绝并提示）
        detail.child(
            new ButtonWidget<>().pos((DETAIL_W - CLAIM_W) / 2 + CLAIM_W - 2, BTN_ROW_Y + 1)
                .size(14, 14)
                .background(NekoGuiTextures.MAIL_TRASH)
                .disableHoverBackground()
                .tooltipBuilder(t -> {
                    t.addLine(IKey.str(EnumChatFormatting.RED + "删除邮件"));
                    Mail mail = selectedMail();
                    if (mail != null && mail.hasUnclaimedAttachments()) {
                        t.addLine(IKey.str(EnumChatFormatting.YELLOW + "需先领取附件"));
                    }
                })
                .tooltipAutoUpdate(true)
                .onMouseTapped(mouse -> {
                    Mail mail = selectedMail();
                    if (mouse == 0 && mail != null) {
                        MailNetworkManager.sendActionToServer(MailActionPacket.ACTION_DELETE, mail.getId());
                        return true;
                    }
                    return false;
                })
                .setEnabledIf(w -> selectedMail() != null));

        return detail;
    }

    // ==================== 写邮件视图（页 4） ====================

    /**
     * 写邮件视图：收件人/标题单行输入 + 正文多行编辑器 + 附件说明 + 发送/清空按钮。
     * <p>
     * 附件机制：寄出时服务端自动取触发机器输入槽的非空物品（≤
     * {@link MailManager#MAX_ATTACHMENTS} 格）作为附件，<b>仅投递成功后</b>才清除槽位——
     * 收件人不存在/邮箱满/空邮件时物品原样保留。
     * 草稿（收件人/标题/正文）存于客户端静态字段，GUI 关闭重开后保留。
     */
    private static IWidget createComposeView(final IGregTechTileEntity machine) {
        ParentWidget<?> view = new ParentWidget<>().size(PAGE_WIDTH, INNER_HEIGHT);

        // ---- 收件人 ----
        view.child(
            new TextWidget<>(IKey.str("收件人:")).pos(4, 3)
                .size(38, 10)
                .shadow(false));
        TextFieldWidget recipientField = new TextFieldWidget()
            .value(new StringValue.Dynamic(() -> composeRecipient, val -> composeRecipient = val))
            .setMaxLength(MailActionPacket.MAX_RECIPIENT_LENGTH);
        recipientField.pos(FIELD_X, 0)
            .size(FIELD_W, FIELD_H);
        recipientField.tooltipBuilder(t -> {
            t.addLine(IKey.str("收件玩家名（需在本服务器登录过）"));
            t.addLine(IKey.str(EnumChatFormatting.GRAY + "最长 " + MailActionPacket.MAX_RECIPIENT_LENGTH + " 字符"));
        });
        recipientField.tooltipAutoUpdate(true);
        view.child(recipientField);

        // ---- 标题 ----
        view.child(
            new TextWidget<>(IKey.str("标题:")).pos(4, 21)
                .size(38, 10)
                .shadow(false));
        TextFieldWidget titleField = new TextFieldWidget()
            .value(new StringValue.Dynamic(() -> composeTitle, val -> composeTitle = val))
            .setMaxLength(MailActionPacket.MAX_TITLE_LENGTH);
        titleField.pos(FIELD_X, 18)
            .size(FIELD_W, FIELD_H);
        titleField.tooltipBuilder(t -> {
            t.addLine(IKey.str("邮件标题（可留空，寄出后自动补默认标题）"));
            t.addLine(IKey.str(EnumChatFormatting.GRAY + "最长 " + MailActionPacket.MAX_TITLE_LENGTH + " 字符"));
        });
        titleField.tooltipAutoUpdate(true);
        view.child(titleField);

        // ---- 正文（多行编辑器） ----
        view.child(
            new TextWidget<>(IKey.str("正文:")).pos(4, 38)
                .size(38, 10)
                .shadow(false));
        composeEditor = new NekoComposeTextEditor();
        composeEditor.pos(4, EDITOR_Y)
            .size(162, EDITOR_H);
        // 恢复上次草稿 + 注册变化监听（实时回写草稿字段，GUI 重建后可恢复）
        composeEditor.setComposeText(composeContent);
        composeEditor.setChangeListener(text -> composeContent = text);
        composeEditor.hintText("在此输入正文…");
        composeEditor.tooltipBuilder(
            t -> t.addLine(IKey.str(EnumChatFormatting.GRAY + "最长 " + MailActionPacket.MAX_CONTENT_LENGTH + " 字符")));
        composeEditor.tooltipAutoUpdate(true);
        view.child(composeEditor);

        // ---- 附件说明 ----
        view.child(
            new TextWidget<>(
                IKey.str(EnumChatFormatting.GRAY + "附件：机器输入槽中的物品将随信寄出（≤" + MailManager.MAX_ATTACHMENTS + "格）"))
                    .pos(4, EDITOR_Y + EDITOR_H + 4)
                    .size(166, 8)
                    .scale(0.7f)
                    .shadow(false));
        view.child(
            new TextWidget<>(IKey.str(EnumChatFormatting.GRAY + "投递成功后才扣取物品；发送结果见聊天栏")).pos(4, EDITOR_Y + EDITOR_H + 13)
                .size(166, 8)
                .scale(0.7f)
                .shadow(false));

        // ---- 发送按钮 ----
        view.child(
            new ButtonWidget<>().pos(33, COMPOSE_BTN_Y)
                .size(48, 20)
                .background(NekoGuiTextures.MAIL_BTN_CLAIM)
                .disableHoverBackground()
                .overlay(IKey.str(EnumChatFormatting.WHITE + "发送"))
                .tooltipBuilder(t -> {
                    t.addLine(IKey.str(EnumChatFormatting.GREEN + "寄出邮件"));
                    t.addLine(IKey.str(EnumChatFormatting.GRAY + "输入槽中的物品将作为附件（≤5格）"));
                    if (machine == null) {
                        t.addLine(IKey.str(EnumChatFormatting.RED + "无法定位机器，发送不可用"));
                    }
                })
                .tooltipAutoUpdate(true)
                .onMouseTapped(mouse -> trySendCompose(machine, mouse))
                .setEnabledIf(w -> machine != null));

        // ---- 清空按钮 ----
        view.child(
            new ButtonWidget<>().pos(89, COMPOSE_BTN_Y)
                .size(48, 20)
                .background(NekoGuiTextures.TEXT_FIELD_BACKGROUND)
                .overlay(IKey.str(EnumChatFormatting.WHITE + "清空"))
                .tooltipBuilder(t -> t.addLine(IKey.str(EnumChatFormatting.GRAY + "清空收件人/标题/正文")))
                .tooltipAutoUpdate(true)
                .onMouseTapped(mouse -> {
                    if (mouse != 0) return false;
                    composeRecipient = "";
                    composeTitle = "";
                    composeContent = "";
                    if (composeEditor != null) {
                        composeEditor.clearText();
                    }
                    return true;
                }));

        return view;
    }

    /**
     * 发送按钮点击：本地校验 → 发送冷却检查 → 发 compose 包。
     * <p>
     * 不在点击后清空字段——发送结果由服务端聊天提示反馈（收件人不存在/邮箱满等失败时
     * 草稿仍在，玩家修正后可重发）；客户端 1.5s 冷却防连点重复寄出。
     */
    private static boolean trySendCompose(IGregTechTileEntity machine, int mouse) {
        if (mouse != 0 || machine == null) return false;
        // 本地校验：收件人必填（其余由服务端权威校验并聊天反馈）
        if (composeRecipient.trim()
            .isEmpty()) {
            return true;
        }
        // 发送冷却（防连点重复寄出）
        long now = System.currentTimeMillis();
        if (now - lastComposeSendMs < COMPOSE_COOLDOWN_MS) {
            return true;
        }
        lastComposeSendMs = now;

        int dim = machine.getWorld() != null ? machine.getWorld().provider.dimensionId : 0;
        MailNetworkManager.sendComposeToServer(
            composeRecipient,
            composeTitle,
            composeEditor != null ? composeEditor.getComposeText() : composeContent,
            machine.getXCoord(),
            machine.getYCoord(),
            machine.getZCoord(),
            dim);
        return true;
    }

    // ==================== 数据辅助（动态 Supplier 调用） ====================

    /**
     * 过滤后的邮件列表（{@link #FILTER_ALL} 返回全部；否则只含指定类型）
     * <p>
     * 每次求值新建小列表（邮箱上限 {@value MailManager#MAX_MAILS} 封，开销可忽略），
     * 供条目/翻页/空列表提示等动态绑定使用。
     */
    private static List<Mail> filteredMails(String filterType) {
        List<Mail> mails = MailClientData.getMails();
        if (filterType == null) return mails;
        List<Mail> filtered = new ArrayList<>();
        for (Mail mail : mails) {
            if (mail != null && filterType.equals(mail.getType())) {
                filtered.add(mail);
            }
        }
        return filtered;
    }

    /**
     * 当前页第 index 条的邮件（按过滤后列表 + 当前页码偏移取；越界返回 null）
     * <p>
     * 每次求值先对页码做 clamp（邮件删除导致页数收缩时防越界）。
     */
    private static Mail entryMail(String filterType, int index) {
        List<Mail> mails = filteredMails(filterType);
        int page = Math.min(MailClientData.getListPage(), maxPage(filterType));
        int i = page * ENTRY_COUNT + index;
        return i >= 0 && i < mails.size() ? mails.get(i) : null;
    }

    /** 最大页码（0 起，按过滤后总数；无邮件时为 0） */
    private static int maxPage(String filterType) {
        int size = filteredMails(filterType).size();
        return size == 0 ? 0 : (size - 1) / ENTRY_COUNT;
    }

    /** 当前选中的邮件（选中 ID 在列表中不存在时返回 null） */
    private static Mail selectedMail() {
        return MailClientData.findMail(MailClientData.getSelectedMailId());
    }

    /** 类型显示名（详情/悬浮提示用） */
    private static String typeDisplayName(String type) {
        if (Mail.TYPE_PLAYER.equals(type)) return "玩家";
        if (Mail.TYPE_ADMIN.equals(type)) return "管理员";
        return "系统";
    }

    /** 条目底纹理：未读/已读 */
    private static IDrawable entryTexture(String filterType, int index) {
        Mail mail = entryMail(filterType, index);
        if (mail == null) return IDrawable.EMPTY;
        return mail.isRead() ? NekoGuiTextures.MAIL_ENTRY_READ : NekoGuiTextures.MAIL_ENTRY_UNREAD;
    }

    /** 条目图标：未读=闭合信封，已读=开封信封 */
    private static IDrawable entryIcon(String filterType, int index) {
        Mail mail = entryMail(filterType, index);
        if (mail == null) return IDrawable.EMPTY;
        return mail.isRead() ? NekoGuiTextures.MAIL_ICON_READ : NekoGuiTextures.MAIL_ICON_UNREAD;
    }

    /** 条目标题文本：未读白色，已读灰色 */
    private static String entryTitle(String filterType, int index) {
        Mail mail = entryMail(filterType, index);
        if (mail == null) return "";
        EnumChatFormatting color = mail.isRead() ? EnumChatFormatting.GRAY : EnumChatFormatting.WHITE;
        return color + mail.getTitle();
    }

    /** 条目次行文本：发件人 + 日期 */
    private static String entryMeta(String filterType, int index) {
        Mail mail = entryMail(filterType, index);
        if (mail == null) return "";
        return mail.getSender() + " · " + TIME_FORMAT.format(new Date(mail.getTimestamp()));
    }

    /** 详情正文第 line 行（正文按 \n 拆行，越界返回空串） */
    private static String contentLine(int line) {
        Mail mail = selectedMail();
        if (mail == null) return "";
        String[] lines = mail.getContent()
            .split("\n");
        return line >= 0 && line < lines.length ? lines[line] : "";
    }

    /** 附件槽第 index 格的物品堆（无选中邮件/越界/空槽返回 null） */
    private static ItemStack attachmentStack(int index) {
        Mail mail = selectedMail();
        if (mail == null) return null;
        List<ItemStack> attachments = mail.getAttachments();
        if (index < 0 || index >= attachments.size()) return null;
        ItemStack stack = attachments.get(index);
        return stack != null && stack.getItem() != null ? stack : null;
    }

    /** 附件槽物品图标（空槽返回 EMPTY） */
    private static IDrawable attachmentItem(int index) {
        ItemStack stack = attachmentStack(index);
        return stack != null ? new ItemDrawable(stack) : IDrawable.EMPTY;
    }

    /** 附件槽 tooltip：物品名 + 数量 + 领取状态 */
    private static void attachmentTooltip(com.cleanroommc.modularui.screen.RichTooltip t, int index) {
        ItemStack stack = attachmentStack(index);
        if (stack == null) return;
        t.addLine(IKey.str(stack.getDisplayName() + " x" + stack.stackSize));
        Mail mail = selectedMail();
        if (mail != null) {
            t.addLine(
                IKey.str(
                    mail.isAttachmentClaimed() ? EnumChatFormatting.GRAY + "已领取" : EnumChatFormatting.YELLOW + "待领取"));
        }
    }

    /** 画矩形边框（1px，选中高亮/详情底纹边框用；与 LotteryGui 的 drawFrame 同款） */
    private static void drawFrame(int x, int y, int w, int h, int color) {
        GuiDraw.drawRect(x, y, w, 1, color);
        GuiDraw.drawRect(x, y + h - 1, w, 1, color);
        GuiDraw.drawRect(x, y + 1, 1, h - 2, color);
        GuiDraw.drawRect(x + w - 1, y + 1, 1, h - 2, color);
    }
}

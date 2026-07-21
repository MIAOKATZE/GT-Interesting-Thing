package com.miaokatze.gtit.mail;

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
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.miaokatze.gtit.client.gui.NekoGuiTextures;

/**
 * 邮件 GUI（v1.7.2 目标 3）
 * <p>
 * 作为 {@code NekoVMGuiV2} 主内容 {@code PagedWidget} 的「邮件」页嵌入（页索引 3），
 * 替换原 {@code createMailPagePlaceholder()} 占位。
 * 采用绝对布局（{@link ParentWidget} + pos/size），全部数据读取自客户端缓存
 * {@link MailClientData}（服务端通过 {@link MailSyncPacket} 推送刷新）——
 * 与签到页/抽奖页同属「S→C 全量同步 + 客户端静态缓存 + 动态绑定」范式：
 * <ul>
 * <li>顶部：标题 + 未读数</li>
 * <li>邮件列表：每页 {@value #ENTRY_COUNT} 条（条目底 + 图标 + 标题 + 发件人/日期 +
 * 未读点 + 选中金框），点击选中并向服务端发已读请求</li>
 * <li>翻页行：上一页/页码/下一页</li>
 * <li>详情区：信纸底纹 + 标题 + 发件人时间 + 正文（最多 {@value #CONTENT_LINES} 行）+
 * 附件槽（{@value MailManager#MAX_ATTACHMENTS} 格）+ 领取/删除按钮</li>
 * </ul>
 * <p>
 * <b>双端安全</b>：所有动态 Supplier 仅在客户端渲染时求值；服务端构建时读到
 * {@link MailClientData} 默认值（空列表），不渲染不影响服务端逻辑。
 * 邮件操作（已读/领取/删除）完全由 {@link MailManager} 服务端权威执行，
 * 本页仅通过 {@link MailNetworkManager#sendActionToServer} 发起请求。
 */
public class MailGui {

    // ==================== 布局常量 ====================

    /** 页面宽度（主内容区 = PANEL_WIDTH - 8） */
    private static final int PAGE_WIDTH = 170;
    /** 页面高度（主内容区 = PANEL_HEIGHT - 8） */
    private static final int PAGE_HEIGHT = 312;

    /** 邮件列表区 Y */
    private static final int LIST_Y = 16;
    /** 条目宽（页面左右各留 2px） */
    private static final int ENTRY_W = 166;
    /** 条目 X */
    private static final int ENTRY_X = 2;
    /** 条目高（与 entry_*.png 素材一致） */
    private static final int ENTRY_H = 27;
    /** 条目间距 */
    private static final int ENTRY_GAP = 2;
    /** 每页条目数 */
    private static final int ENTRY_COUNT = 4;

    /** 翻页行 Y */
    private static final int PAGER_Y = 132;
    /** 翻页按钮宽 */
    private static final int PAGER_BTN_W = 20;
    /** 翻页按钮高 */
    private static final int PAGER_BTN_H = 12;

    /** 信纸详情区 X */
    private static final int PAPER_X = 2;
    /** 信纸详情区 Y */
    private static final int PAPER_Y = 148;
    /** 信纸宽（与 paper.png 素材一致） */
    private static final int PAPER_W = 166;
    /** 信纸高（与 paper.png 素材一致） */
    private static final int PAPER_H = 158;

    /** 详情正文最大行数 */
    private static final int CONTENT_LINES = 6;
    /** 详情正文行高 */
    private static final int CONTENT_LINE_H = 9;
    /** 详情正文起始 Y（信纸内） */
    private static final int CONTENT_Y = PAPER_Y + 30;

    /** 附件槽边长（与 slot.png 素材一致） */
    private static final int SLOT_SIZE = 18;
    /** 附件槽间距 */
    private static final int SLOT_GAP = 2;
    /** 附件槽行 Y */
    private static final int SLOT_Y = PAPER_Y + 92;

    /** 领取按钮宽（与 btn_claim.png 素材一致） */
    private static final int CLAIM_W = 48;
    /** 领取按钮高 */
    private static final int CLAIM_H = 20;
    /** 按钮行 Y */
    private static final int BTN_ROW_Y = PAPER_Y + 118;

    // ==================== 颜色常量（ARGB） ====================

    /** 选中条目高亮框色（半透明金） */
    private static final int COLOR_SELECTED_FRAME = 0xCCFFC84A;
    /** 详情正文颜色（米白纸底上的深紫灰） */
    private static final int COLOR_CONTENT = 0xFF4A3F55;
    /** 详情次要信息颜色（发件人/时间） */
    private static final int COLOR_META = 0xFF777788;

    /** 邮件时间格式（MM-dd HH:mm） */
    private static final SimpleDateFormat TIME_FORMAT = new SimpleDateFormat("MM-dd HH:mm", Locale.ROOT);

    private MailGui() {}

    // ==================== 页面构建入口 ====================

    /**
     * 构建邮件页（供 {@code NekoVMGuiV2} 主内容 PagedWidget 添加为页 3）
     *
     * @return 邮件页根 Widget（170x312，绝对布局）
     */
    public static IWidget createMailPage() {
        ParentWidget<?> page = new ParentWidget<>().size(PAGE_WIDTH, PAGE_HEIGHT);

        page.child(createTitle()); // 标题 + 未读数
        page.child(createMailList()); // 邮件列表（4 条/页）
        page.child(createPager()); // 翻页行
        page.child(createDetailArea()); // 信纸详情区（正文 + 附件 + 按钮）

        return page;
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

    // ==================== 邮件列表 ====================

    /**
     * 邮件列表：每页 {@value #ENTRY_COUNT} 条，条目结构（按添加顺序绘制）：
     * 条目底按钮（未读/已读纹理 + 点击选中）→ 邮件图标 → 标题 → 发件人/日期 →
     * 未读点 → 选中金框。无邮件的槽位整体隐藏。
     */
    private static IWidget createMailList() {
        ParentWidget<?> list = new ParentWidget<>().pos(0, LIST_Y)
            .size(PAGE_WIDTH, ENTRY_COUNT * (ENTRY_H + ENTRY_GAP));
        for (int i = 0; i < ENTRY_COUNT; i++) {
            final int index = i;
            int y = index * (ENTRY_H + ENTRY_GAP);

            // 条目底按钮（点击选中 + 未读时发已读请求）
            list.child(
                new ButtonWidget<>().pos(ENTRY_X, y)
                    .size(ENTRY_W, ENTRY_H)
                    .background(new DynamicDrawable(() -> entryTexture(index)))
                    .disableHoverBackground()
                    .tooltipBuilder(t -> entryTooltip(t, index))
                    .tooltipAutoUpdate(true)
                    .onMouseTapped(mouse -> {
                        Mail mail = entryMail(index);
                        if (mouse == 0 && mail != null) {
                            MailClientData.setSelectedMailId(mail.getId());
                            if (!mail.isRead()) {
                                MailNetworkManager.sendActionToServer(MailActionPacket.ACTION_READ, mail.getId());
                            }
                            return true;
                        }
                        return false;
                    })
                    .setEnabledIf(w -> entryMail(index) != null));

            // 邮件图标（未读=闭合信封，已读=开封信封）
            list.child(
                new IDrawable.DrawableWidget(new DynamicDrawable(() -> entryIcon(index))).pos(ENTRY_X + 4, y + 5)
                    .size(16, 16)
                    .setEnabledIf(w -> entryMail(index) != null));

            // 标题（第一行，未读加粗白字，已读灰字）
            list.child(
                new TextWidget<>(IKey.dynamic(() -> entryTitle(index))).pos(ENTRY_X + 24, y + 4)
                    .size(ENTRY_W - 44, 10)
                    .scale(0.8f)
                    .shadow(false)
                    .setEnabledIf(w -> entryMail(index) != null));

            // 发件人 + 日期（第二行，灰色小字）
            list.child(
                new TextWidget<>(IKey.dynamic(() -> entryMeta(index))).pos(ENTRY_X + 24, y + 16)
                    .size(ENTRY_W - 44, 9)
                    .scale(0.7f)
                    .color(0xFF666677)
                    .shadow(false)
                    .setEnabledIf(w -> entryMail(index) != null));

            // 未读亮点（右侧，仅未读显示）
            list.child(
                NekoGuiTextures.MAIL_DOT.asWidget()
                    .pos(ENTRY_X + ENTRY_W - 14, y + 9)
                    .size(8, 8)
                    .setEnabledIf(w -> {
                        Mail mail = entryMail(index);
                        return mail != null && !mail.isRead();
                    }));

            // 选中高亮框（仅选中条目显示）
            list.child(new IDrawable.DrawableWidget((context, dx, dy, w, h, theme) -> {
                Mail mail = entryMail(index);
                if (mail == null || !mail.getId()
                    .equals(MailClientData.getSelectedMailId())) return;
                drawFrame(dx, dy, w, h, COLOR_SELECTED_FRAME);
            }).pos(ENTRY_X - 1, y - 1)
                .size(ENTRY_W + 2, ENTRY_H + 2));
        }
        return list;
    }

    /** 条目 tooltip：标题 + 发件人 + 时间 + 附件提示 */
    private static void entryTooltip(com.cleanroommc.modularui.screen.RichTooltip t, int index) {
        Mail mail = entryMail(index);
        if (mail == null) return;
        t.addLine(IKey.str(EnumChatFormatting.GOLD + mail.getTitle()));
        t.addLine(
            IKey.str(
                EnumChatFormatting.GRAY + "来自 "
                    + mail.getSender()
                    + " · "
                    + TIME_FORMAT.format(new Date(mail.getTimestamp()))));
        if (mail.hasUnclaimedAttachments()) {
            t.addLine(IKey.str(EnumChatFormatting.YELLOW + "含未领取附件"));
        }
        t.addLine(IKey.str(EnumChatFormatting.AQUA + "点击查看详情"));
    }

    // ==================== 翻页行 ====================

    /** 翻页行：上一页 / 页码 / 下一页（居中排列，仅一页时按钮禁用） */
    private static IWidget createPager() {
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
                        + (maxPage() + 1))).pos(x + PAGER_BTN_W + 2, 2)
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
                    if (mouse == 0 && MailClientData.getListPage() < maxPage()) {
                        MailClientData.setListPage(MailClientData.getListPage() + 1);
                        return true;
                    }
                    return false;
                }));
        return row;
    }

    // ==================== 详情区 ====================

    /**
     * 详情区：信纸底纹 + 选中邮件的完整内容。
     * <p>
     * 未选中邮件时仅显示提示文本；选中后展示标题、发件人时间、正文
     * （预建 {@value #CONTENT_LINES} 行动态取行，无需重建 widget）、
     * 附件槽与领取/删除按钮。
     */
    private static IWidget createDetailArea() {
        ParentWidget<?> detail = new ParentWidget<>().pos(PAPER_X, PAPER_Y)
            .size(PAPER_W, PAPER_H);

        // 信纸底纹
        detail.child(NekoGuiTextures.MAIL_PAPER.asWidget()
            .pos(0, 0)
            .size(PAPER_W, PAPER_H));

        // 未选中提示（仅无选中邮件时显示）
        detail.child(
            new TextWidget<>(IKey.str(EnumChatFormatting.GRAY + "选择一封邮件查看详情")).pos(0, 70)
                .size(PAPER_W, 12)
                .textAlign(Alignment.Center)
                .shadow(false)
                .setEnabledIf(w -> selectedMail() == null));

        // 详情标题（居中，深紫灰）
        detail.child(
            new TextWidget<>(IKey.dynamic(() -> {
                Mail mail = selectedMail();
                return mail == null ? "" : EnumChatFormatting.DARK_PURPLE + mail.getTitle();
            })).pos(4, 6)
                .size(PAPER_W - 8, 11)
                .textAlign(Alignment.Center)
                .shadow(false)
                .setEnabledIf(w -> selectedMail() != null));

        // 发件人 + 时间
        detail.child(
            new TextWidget<>(IKey.dynamic(() -> {
                Mail mail = selectedMail();
                if (mail == null) return "";
                return "来自 " + mail.getSender() + " · " + TIME_FORMAT.format(new Date(mail.getTimestamp()));
            })).pos(4, 18)
                .size(PAPER_W - 8, 9)
                .textAlign(Alignment.Center)
                .scale(0.7f)
                .color(COLOR_META)
                .shadow(false)
                .setEnabledIf(w -> selectedMail() != null));

        // 正文（预建行，动态取 split 结果；越界行显示空串）
        for (int i = 0; i < CONTENT_LINES; i++) {
            final int line = i;
            detail.child(
                new TextWidget<>(IKey.dynamic(() -> contentLine(line))).pos(10, CONTENT_Y - PAPER_Y + i * CONTENT_LINE_H)
                    .size(PAPER_W - 20, CONTENT_LINE_H)
                    .scale(0.75f)
                    .color(COLOR_CONTENT)
                    .shadow(false)
                    .setEnabledIf(w -> selectedMail() != null));
        }

        // 附件槽（最多 MAX_ATTACHMENTS 格；槽底 + 物品图标叠加）
        int slotsW = MailManager.MAX_ATTACHMENTS * (SLOT_SIZE + SLOT_GAP) - SLOT_GAP;
        int slotX = (PAPER_W - slotsW) / 2;
        for (int i = 0; i < MailManager.MAX_ATTACHMENTS; i++) {
            final int index = i;
            int x = slotX + i * (SLOT_SIZE + SLOT_GAP);
            int y = SLOT_Y - PAPER_Y;
            detail.child(
                NekoGuiTextures.MAIL_SLOT.asWidget()
                    .pos(x, y)
                    .size(SLOT_SIZE, SLOT_SIZE)
                    .setEnabledIf(w -> selectedMail() != null));
            detail.child(
                new IDrawable.DrawableWidget(new DynamicDrawable(() -> attachmentItem(index))).pos(x + 1, y + 1)
                    .size(16, 16)
                    .tooltipBuilder(t -> attachmentTooltip(t, index))
                    .tooltipAutoUpdate(true)
                    .setEnabledIf(w -> attachmentStack(index) != null));
        }

        // 领取附件按钮（仅有待领取附件时可用）
        detail.child(
            new ButtonWidget<>().pos((PAPER_W - CLAIM_W) / 2 - 10, BTN_ROW_Y - PAPER_Y)
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
            new ButtonWidget<>().pos((PAPER_W - CLAIM_W) / 2 + CLAIM_W - 4, BTN_ROW_Y - PAPER_Y + 3)
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

    // ==================== 数据辅助（动态 Supplier 调用） ====================

    /**
     * 当前页第 index 条的邮件（按当前页码偏移取；越界返回 null）
     * <p>
     * 每次求值先对页码做 clamp（邮件删除导致页数收缩时防越界）。
     */
    private static Mail entryMail(int index) {
        List<Mail> mails = MailClientData.getMails();
        int page = Math.min(MailClientData.getListPage(), maxPage());
        int i = page * ENTRY_COUNT + index;
        return i >= 0 && i < mails.size() ? mails.get(i) : null;
    }

    /** 最大页码（0 起；无邮件时为 0） */
    private static int maxPage() {
        int size = MailClientData.getMails()
            .size();
        return size == 0 ? 0 : (size - 1) / ENTRY_COUNT;
    }

    /** 当前选中的邮件（选中 ID 在列表中不存在时返回 null） */
    private static Mail selectedMail() {
        return MailClientData.findMail(MailClientData.getSelectedMailId());
    }

    /** 条目底纹理：未读/已读 */
    private static IDrawable entryTexture(int index) {
        Mail mail = entryMail(index);
        if (mail == null) return IDrawable.EMPTY;
        return mail.isRead() ? NekoGuiTextures.MAIL_ENTRY_READ : NekoGuiTextures.MAIL_ENTRY_UNREAD;
    }

    /** 条目图标：未读=闭合信封，已读=开封信封 */
    private static IDrawable entryIcon(int index) {
        Mail mail = entryMail(index);
        if (mail == null) return IDrawable.EMPTY;
        return mail.isRead() ? NekoGuiTextures.MAIL_ICON_READ : NekoGuiTextures.MAIL_ICON_UNREAD;
    }

    /** 条目标题文本：未读白色，已读灰色 */
    private static String entryTitle(int index) {
        Mail mail = entryMail(index);
        if (mail == null) return "";
        EnumChatFormatting color = mail.isRead() ? EnumChatFormatting.GRAY : EnumChatFormatting.WHITE;
        return color + mail.getTitle();
    }

    /** 条目次行文本：发件人 + 日期 */
    private static String entryMeta(int index) {
        Mail mail = entryMail(index);
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
                    mail.isAttachmentClaimed() ? EnumChatFormatting.GRAY + "已领取"
                        : EnumChatFormatting.YELLOW + "待领取"));
        }
    }

    /** 画矩形边框（2px，选中高亮用；与 LotteryGui 的 drawFrame 同款） */
    private static void drawFrame(int x, int y, int w, int h, int color) {
        GuiDraw.drawRect(x, y, w, 1, color);
        GuiDraw.drawRect(x, y + h - 1, w, 1, color);
        GuiDraw.drawRect(x, y + 1, 1, h - 2, color);
        GuiDraw.drawRect(x + w - 1, y + 1, 1, h - 2, color);
    }
}

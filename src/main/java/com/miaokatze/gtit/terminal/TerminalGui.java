package com.miaokatze.gtit.terminal;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.screen.CustomModularScreen;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widgets.PageButton;
import com.cleanroommc.modularui.widgets.PagedWidget;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.miaokatze.gtit.client.gui.utility.UtilityUiStyle;
import com.miaokatze.gtit.main.GTInterestingThing;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * 服务器管理终端主屏幕（纯客户端 MUI2 面板）
 * <p>
 * 由 {@link TerminalOpenPacket} 客户端 handler 经 {@code ClientGUI.open} 打开
 * （GuiScreenWrapper 路径，无 Container）：
 * <ul>
 * <li>顶部状态回显区：{@code IKey.dynamic} 绑定 {@link TerminalClientData#getResultDisplayLine}
 * （成功绿/失败红/其他黄，按 status 着色）</li>
 * <li>左侧 4 个原生 PageButton 标签按钮：
 * 邮件/签到/交易/礼包</li>
 * <li>主内容 {@code PagedWidget} 四个管理页</li>
 * </ul>
 * <b>纯客户端模式约束</b>：无 MUI2 同步值/槽位，不得调用
 * {@code getSyncManager()}/{@code getContainer()}；所有 widget 本地回调
 * （标签切换等）在纯客户端路径下可用。
 * <p>
 * 构建范式参照 MUI2 官方 {@code test/TestGui}（CustomModularScreen 用法）与
 * {@code NekoVMGuiV2#createMainContentPagedWidget}（PagedWidget+Controller 分页）。
 */
@SideOnly(Side.CLIENT)
public class TerminalGui extends CustomModularScreen {

    /** 面板宽（ModularPanel 构造自动 center） */
    public static final int PANEL_WIDTH = 224;
    /** 面板高 */
    public static final int PANEL_HEIGHT = 256;
    /** 左侧标签列顶部 Y */
    private static final int TAB_COLUMN_TOP = 24;
    /** 主内容区起点 X（标签列右侧留出 6px 间距） */
    private static final int CONTENT_X = 42;
    /** 主内容区起点 Y */
    private static final int CONTENT_Y = 24;
    /** 主内容区宽 */
    public static final int CONTENT_WIDTH = PANEL_WIDTH - CONTENT_X - 8;
    /** 主内容区高 */
    public static final int CONTENT_HEIGHT = PANEL_HEIGHT - CONTENT_Y - 6;

    /** 页索引：邮件 */
    public static final int PAGE_MAIL = 0;
    /** 页索引：签到 */
    public static final int PAGE_SIGNIN = 1;
    /** 页索引：交易 */
    public static final int PAGE_TRADE = 2;
    /** 页索引：礼包 */
    public static final int PAGE_GIFT = 3;

    public TerminalGui() {
        super(GTInterestingThing.MODID);
    }

    @Override
    public ModularPanel buildUI(ModularGuiContext context) {
        ParentWidget<?> content = new ParentWidget<>().size(PANEL_WIDTH, PANEL_HEIGHT);

        // 主内容分页控制器（标签列与 PagedWidget 共用）
        PagedWidget.Controller controller = new PagedWidget.Controller();

        // 顶部状态回显区：最近一次动作结果（成功绿/失败红/其他黄，着色在动态文本内）
        content.child(
            UtilityUiStyle.section(6, 4, PANEL_WIDTH - 30, 16)
                .background(UtilityUiStyle.INPUT)
                .child(
                    new TextWidget<>(
                        IKey.dynamic(() -> fitLine(TerminalClientData.getResultDisplayLine(), PANEL_WIDTH - 40, 0.75f)))
                            .pos(4, 3)
                            .size(PANEL_WIDTH - 38, 10)
                            .scale(0.75f)
                            .shadow(false)
                            .tooltipBuilder(t -> t.addLine(IKey.str(TerminalClientData.getResultDisplayLine())))
                            .tooltipAutoUpdate(true)));
        content.child(UtilityUiStyle.section(CONTENT_X - 2, CONTENT_Y - 2, CONTENT_WIDTH + 4, CONTENT_HEIGHT + 2));

        // 左侧 4 标签列（邮件/签到/交易/礼包）
        content.child(createTabColumn(controller));

        // 主内容 PagedWidget：四个管理页
        PagedWidget<?> paged = new PagedWidget<>().name("terminalPaged")
            .pos(CONTENT_X, CONTENT_Y)
            .size(CONTENT_WIDTH, CONTENT_HEIGHT)
            .controller(controller);
        paged.addPage(TerminalMailPage.createPage(controller)); // 页 0：邮件
        paged.addPage(TerminalSignInPage.createPage(controller)); // 页 1：签到
        paged.addPage(TerminalTradePage.createPage(controller)); // 页 2：交易
        paged.addPage(TerminalGiftPage.createPage(controller)); // 页 3：礼包
        content.child(paged);

        return UtilityUiStyle.scrollable("gtit_terminal", PANEL_WIDTH, PANEL_HEIGHT, content);
    }

    /**
     * 原生 PageButton 保留主题提供的 hover 与选中态，文字直接标识管理分类。
     */
    private ParentWidget<?> createTabColumn(PagedWidget.Controller controller) {
        Flow tabColumn = Flow.column()
            .coverChildren()
            .left(6)
            .top(TAB_COLUMN_TOP)
            .childPadding(4);

        // 标签顺序与 PAGE_* 常量一致。
        String[] tabs = { "邮件", "签到", "交易", "礼包" };

        for (int index = 0; index < tabs.length; index++) {
            String name = tabs[index];
            PageButton tabButton = new PageButton(index, controller).size(30, 24)
                .overlay(IKey.str(name));
            tabButton.tooltipBuilder(t -> t.addLine(IKey.str(name)));
            tabColumn.child(tabButton);
        }
        return tabColumn;
    }

    /** Keep a single-line preview within its actual font budget; tooltips retain the full text. */
    static String fitLine(String text, int width, float scale) {
        text = text.replace('\n', ' ')
            .replace('\r', ' ');
        FontRenderer font = Minecraft.getMinecraft().fontRenderer;
        if (font == null || font.getStringWidth(text) * scale <= width) return text;
        int budget = Math.max(1, (int) (width / scale) - font.getStringWidth("..."));
        return font.trimStringToWidth(text, budget) + "...";
    }
}

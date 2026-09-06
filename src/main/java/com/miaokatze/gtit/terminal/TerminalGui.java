package com.miaokatze.gtit.terminal;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.screen.CustomModularScreen;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widgets.PagedWidget;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.miaokatze.gtit.client.gui.NekoGuiTextures;
import com.miaokatze.gtit.client.gui.NekoMainTabButton;
import com.miaokatze.gtit.main.GTInterestingThing;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * 服务器管理终端主屏幕（T1 骨架，纯客户端 MUI2 面板）
 * <p>
 * 由 {@link TerminalOpenPacket} 客户端 handler 经 {@code ClientGUI.open} 打开
 * （GuiScreenWrapper 路径，无 Container）：
 * <ul>
 * <li>顶部状态回显区：{@code IKey.dynamic} 绑定 {@link TerminalClientData#getResultDisplayLine}
 * （成功绿/失败红/其他黄，按 status 着色）</li>
 * <li>左侧 4 个标签按钮（NekoMainTabButton + NekoGuiTextures 已注册图标，不新增贴图）：
 * 邮件/签到/交易/礼包</li>
 * <li>主内容 {@code PagedWidget} 四页（T1 均为占位页，后续切片填充）</li>
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

    /** 面板宽（约 200x256 居中，ModularPanel 构造自动 center） */
    public static final int PANEL_WIDTH = 200;
    /** 面板高 */
    public static final int PANEL_HEIGHT = 256;
    /** 左侧标签列顶部 Y */
    private static final int TAB_COLUMN_TOP = 24;
    /** 主内容区起点 X（标签列右侧；标签列占 x4..36，内容区自 36 起避免 8px 重叠） */
    private static final int CONTENT_X = 36;
    /** 主内容区起点 Y */
    private static final int CONTENT_Y = 24;
    /** 主内容区宽 */
    public static final int CONTENT_WIDTH = PANEL_WIDTH - CONTENT_X - 6;
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
        ModularPanel panel = ModularPanel.defaultPanel("gtit_terminal", PANEL_WIDTH, PANEL_HEIGHT);
        panel.background(NekoGuiTextures.TRADE_BACKGROUND);

        // 主内容分页控制器（标签列与 PagedWidget 共用）
        PagedWidget.Controller controller = new PagedWidget.Controller();

        // 顶部状态回显区：最近一次动作结果（成功绿/失败红/其他黄，着色在动态文本内）
        panel.child(
            new TextWidget<>(IKey.dynamic(TerminalClientData::getResultDisplayLine)).top(6)
                .left(CONTENT_X)
                .right(6)
                .height(12));

        // 左侧 4 标签列（邮件/签到/交易/礼包）
        panel.child(createTabColumn(controller));

        // 主内容 PagedWidget：四页（T1 占位）
        PagedWidget<?> paged = new PagedWidget<>().name("terminalPaged")
            .pos(CONTENT_X, CONTENT_Y)
            .size(CONTENT_WIDTH, CONTENT_HEIGHT)
            .controller(controller);
        paged.addPage(TerminalMailPage.createPage(controller)); // 页 0：邮件
        paged.addPage(TerminalSignInPage.createPage(controller)); // 页 1：签到
        paged.addPage(TerminalTradePage.createPage(controller)); // 页 2：交易
        paged.addPage(TerminalGiftPage.createPage(controller)); // 页 3：礼包
        panel.child(paged);

        return panel;
    }

    /**
     * 左侧标签列（贴图 icons 复用 NekoGuiTextures 已注册的 main_tabs 素材，不新增贴图注册）
     */
    private ParentWidget<?> createTabColumn(PagedWidget.Controller controller) {
        Flow tabColumn = Flow.column()
            .coverChildren()
            .left(4)
            .top(TAB_COLUMN_TOP)
            .childPadding(2);

        // 标签定义：index → (图标, 中文名)
        Object[][] tabs = new Object[][] { { PAGE_MAIL, NekoGuiTextures.MAIN_TAB_MAIL, "邮件" },
            { PAGE_SIGNIN, NekoGuiTextures.MAIN_TAB_SIGNIN, "签到" },
            { PAGE_TRADE, NekoGuiTextures.MAIN_TAB_TRADE, "交易" }, { PAGE_GIFT, NekoGuiTextures.MAIN_TAB_EDIT, "礼包" }, };

        for (Object[] tabDef : tabs) {
            final int index = (Integer) tabDef[0];
            final com.cleanroommc.modularui.drawable.UITexture icon = (com.cleanroommc.modularui.drawable.UITexture) tabDef[1];
            final String name = (String) tabDef[2];

            NekoMainTabButton tabButton = new NekoMainTabButton(index, controller, icon);
            tabButton.tab(NekoGuiTextures.TAB_LEFT, -1);
            tabButton.tooltipBuilder(t -> t.addLine(IKey.str(name)));
            tabColumn.child(tabButton);
        }
        return tabColumn;
    }
}

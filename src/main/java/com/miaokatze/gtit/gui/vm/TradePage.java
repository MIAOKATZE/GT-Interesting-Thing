package com.miaokatze.gtit.gui.vm;

import static com.miaokatze.gtit.gui.vm.NekoVMGuiV2.LIST_ITEM_HEIGHT;
import static com.miaokatze.gtit.gui.vm.NekoVMGuiV2.MAX_TRADES;
import static com.miaokatze.gtit.gui.vm.NekoVMGuiV2.PANEL_WIDTH;
import static com.miaokatze.gtit.gui.vm.NekoVMGuiV2.TILE_ITEMS_PER_ROW;
import static com.miaokatze.gtit.gui.vm.NekoVMGuiV2.TILE_ITEM_HEIGHT;
import static com.miaokatze.gtit.gui.vm.NekoVMGuiV2.TRADE_ROW_WIDTH;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.api.widget.Interactable;
import com.cleanroommc.modularui.drawable.DynamicDrawable;
import com.cleanroommc.modularui.drawable.ItemDrawable;
import com.cleanroommc.modularui.value.sync.BooleanSyncValue;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.StringSyncValue;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.ListWidget;
import com.cleanroommc.modularui.widgets.PagedWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.cleanroommc.modularui.widgets.layout.Grid;
import com.miaokatze.gtit.client.gui.NekoCoinDisplayV2;
import com.miaokatze.gtit.client.gui.NekoDisplayType;
import com.miaokatze.gtit.client.gui.NekoGuiTextures;
import com.miaokatze.gtit.client.gui.NekoMainTabButton;
import com.miaokatze.gtit.client.gui.NekoMusicTrack;
import com.miaokatze.gtit.client.gui.NekoPageButtonV2;
import com.miaokatze.gtit.client.gui.NekoSearchBar;
import com.miaokatze.gtit.client.gui.NekoSortMode;
import com.miaokatze.gtit.client.gui.NekoSubTabButton;
import com.miaokatze.gtit.client.gui.NekoTradeItemDisplay;
import com.miaokatze.gtit.client.gui.NekoTradeItemDisplayWidget;
import com.miaokatze.gtit.client.gui.NekoTradeRow;
import com.miaokatze.gtit.client.gui.NekoVolumeControlGui;
import com.miaokatze.gtit.client.gui.NekoWalletMode;
import com.miaokatze.gtit.common.machine.neko.NekoMusicEventHandler;
import com.miaokatze.gtit.currency.NekoCurrencyRegistrar;
import com.miaokatze.gtit.lottery.LotteryClientData;
import com.miaokatze.gtit.mail.MailClientData;
import com.miaokatze.gtit.trade.NekoPageEntry;
import com.miaokatze.gtit.trade.NekoPageRegistry;
import com.miaokatze.gtit.trade.v2.NekoTradeCategory;
import com.miaokatze.gtit.trade.v2.NekoTradeDatabase;
import com.miaokatze.gtit.trade.v2.NekoTradeGroup;

/**
 * 贸易主标签页（A01 蓝图 G6 抽取自 NekoVMGuiV2，方法体逐字搬移）
 * <p>
 * 持有页面状态镜像（当前标签/主标签/搜索文本/排序/显示模式）与五个 C2S 通道的自注册、
 * 标签列四件套（主标签列/分类列/sub-tab 列/QoL 列）、贸易内容列（标题/搜索/列表分页/
 * 货币余额行）、分类列表构建与五个分页控制器的接线。三 sub-tab 页壳（签到/抽奖/邮件）
 * 由 {@link #createSubTabColumn(int)} 参数化复用承担（内容页已由 SignInCalendarGui/
 * LotteryGui/MailGui 独立类承担，无独立页壳状态）。
 * <p>
 * <b>双端镜像构建</b>：控制器与页面构建在服务端同样执行（v1.7.17），类内不得有客户端
 * 专属 API 的静态引用；仅客户端分支的构建由宿主 build() 的 isClient 块守卫。
 */
public final class TradePage {

    /** 宿主引用（跨域触点：编辑器/ME 状态/货币显示/交易结果消息/预分配池） */
    private final NekoVMGuiV2 gui;

    // ==================== v1.7.0 主标签常量 ====================

    /** 主标签-贸易（默认） */
    public static final int MAIN_TAB_TRADE = 0;
    /** 主标签-签到 */
    public static final int MAIN_TAB_SIGNIN = 1;
    /** 主标签-抽奖 */
    public static final int MAIN_TAB_LOTTERY = 2;
    /** 主标签-邮件 */
    public static final int MAIN_TAB_MAIL = 3;
    /** 主标签总数 */
    public static final int MAIN_TAB_COUNT = 4;

    // ==================== 同步值字段（页面状态，G6 分域下沉） ====================

    /** 当前标签页索引（C2S：客户端切换标签时发送到服务端） */
    private IntSyncValue currentTabSync;
    /** v1.7.0 主标签索引（C2S：客户端切换主标签时发送到服务端） */
    private IntSyncValue mainTabSync;
    /** 搜索文本（C2S：客户端输入时发送到服务端） */
    private StringSyncValue searchTextSync;
    /** 排序模式（C2S：0=SMART, 1=ALPHABET） */
    private IntSyncValue sortModeSync;
    /** 显示模式（C2S：0=TILE, 1=LIST） */
    private IntSyncValue displayTypeSync;

    // ==================== UI 状态镜像（客户端+服务端共享） ====================

    /** 当前标签页索引（默认 0=FAVOURITES 分类） */
    private int currentTabId = 0;
    /** 搜索文本 */
    private String searchText = "";
    /** 排序模式：0=SMART, 1=ALPHABET */
    private int sortMode = 0;
    /** 显示模式：0=TILE, 1=LIST */
    private int displayType = 0;
    /** v1.7.0 当前主标签索引（默认 0=贸易） */
    private int mainTabId = MAIN_TAB_TRADE;
    /** 搜索栏组件（客户端） */
    private NekoSearchBar searchBar;
    /** 高亮标签页集合（搜索匹配时高亮对应标签） */
    private final Set<NekoTradeCategory> highlightedTabs = new HashSet<>();
    /** 交易分类列表（决定标签页顺序：FAVOURITES, ALL, ...其他） */
    private final List<NekoTradeCategory> tradeCategories = new ArrayList<>();

    // ==================== 分页控制器（build 期接线） ====================

    /** 分页控制器（客户端，管理标签页切换） */
    private PagedWidget.Controller tabController;
    /** v1.7.0 主标签分页控制器（客户端，管理贸易/签到/抽奖/邮件切换） */
    private PagedWidget.Controller mainTabController;
    /** v1.7.6 G1 签到「活跃」sub-page 分页控制器 */
    private PagedWidget.Controller signInPageController;
    /** v1.7.6 G1 抽奖池 sub-page 分页控制器 */
    private PagedWidget.Controller lotteryPageController;
    /** v1.7.6 G1 邮件 sub-page 分页控制器 */
    private PagedWidget.Controller mailPageController;
    /** 池标签列预分配按钮上限（池数量超出后不渲染；显隐由 setEnabledIf + collapseDisabledChild 每帧驱动） */
    private static final int MAX_POOL_TABS = 12;

    // ==================== v1.7.33 T2/T3（QoL 块左移 + 标签翻页） ====================

    /**
     * T2：QoL 2x2 块左锚定值 left(-60)。
     * <p>
     * 几何（各列均为 panel 直接 child，绝对锚点不受 panel.padding(4) 影响，MUI2
     * {@code DimensionSizer.applyMarginAndPaddingToPos} 对 PIXEL 锚点将 padding 归零）：
     * 块宽 29（14+1+14，minElementMargin 仅作用于元素间），left(-60) → x ∈ [-60, -31)；
     * 贸易分类/sub-tab 标签列 left(-29)（TAB_LEFT 标签宽 32）→ x ∈ [-29, +3)，
     * 与块右缘间隙 = -29 - (-31) = 2px，水平完全脱开且 ≥2px。
     * 主标签列 left(-57) → x ∈ [-57, -25)：与块 x 投影虽重叠 26px，但块 y ∈ [1, 30)、
     * 主列 y ∈ [40, 158)，y 向错开 10px，渲染像素零碰撞；若要求与主列也纯水平脱开
     * 需 left ≤ -88（= -57 - 2 - 29），超出既定 -54~-60 区间且视觉上脱离 GUI，不取。
     */
    private static final int QOL_GRID_LEFT = -60;

    /** T3：标签列每页最大标签数（size ≥ 此值才创建翻页行；页内为全局索引 [tabPage*PAGE_SIZE, ...)） */
    public static final int PAGE_SIZE = 11;

    /** T3：标签列起始 y（原 top(40) 为让位 QoL 块 top(1)+高29+间隙10；T2 左移后该约束解除，上移释放 32px） */
    private static final int TAB_COLUMN_TOP = 8;

    /** T3：标签列子项间距（原 2 压缩为 1，配合按钮压扁使 11 槽+翻页行落入面板可视高度） */
    private static final int TAB_COLUMN_CHILD_PADDING = 1;

    /**
     * T3：分类标签按钮高度。
     * <p>
     * TAB_LEFT（{@code NekoGuiTextures#TAB_LEFT}）原生 32x28（PageButton.tab 按纹理定尺寸），
     * 压扁至 25（纹理纵向拉伸 10.7%，4px 边框视觉约 3.6px；图标 16x16 经 Icon.center()
     * 居中后 y ∈ [4.5, 20.5]，仍在按钮内）。依据：PANEL_HEIGHT=320（NekoVMGuiV2:111）、
     * panel 内缘 316——11 槽 × 28 = 308 仅按钮已超 316，放不下为既定事实，压扁是最小可行调整。
     */
    private static final int TAB_BUTTON_HEIGHT = 25;

    /** T3：标签按钮宽度（TAB_LEFT 原生值，不变） */
    private static final int TAB_BUTTON_WIDTH = 32;

    /** T3：翻页按钮边长（与 QoL 14x14 按钮习语一致） */
    private static final int PAGER_BUTTON_SIZE = 14;

    /** T3：当前标签翻页页码（0 起；本页标签为全局索引 [tabPage*PAGE_SIZE, min(tabPage*PAGE_SIZE+PAGE_SIZE, size))） */
    private int tabPage = 0;

    /** T3：翻页最大页码（=(size-1)/PAGE_SIZE，随标签集合数量在每次重建时收敛，参照 restoreSettings 钳制模式） */
    private int maxTabPage = 0;

    /** T3：标签列引用（仅客户端 build 后非 null；翻页后经 ParentWidget 公有 removeAll/child 原位重建子树） */
    private Flow tabColumn;

    public TradePage(NekoVMGuiV2 gui) {
        this.gui = gui;
        // 初始化交易分类列表：FAVOURITES 始终第一位，其余按 NekoPageRegistry 动态生成
        tradeCategories.add(NekoTradeCategory.FAVOURITES);

        List<NekoPageEntry> pages = NekoPageRegistry.getAllPages();
        if (pages != null && !pages.isEmpty()) {
            // 从 NekoPageRegistry 加载标签页配置，与 V1 保持一致的标签页顺序
            for (NekoPageEntry page : pages) {
                if (page != null) {
                    tradeCategories.add(NekoTradeCategory.ofTabId(page.getId()));
                }
            }
        } else {
            // 向后兼容：若 NekoPageRegistry 未初始化，则扫描交易数据库使用到的分类
            Set<NekoTradeCategory> usedCategories = new TreeSet<>();
            for (NekoTradeGroup group : NekoTradeDatabase.INSTANCE.getAllTradeGroups()
                .values()) {
                NekoTradeCategory cat = group.getCategory();
                if (cat != null && !cat.isFavourites() && !cat.isUnknown()) {
                    usedCategories.add(cat);
                }
            }
            tradeCategories.addAll(usedCategories);
            // 至少保留 UNKNOWN 兜底
            if (tradeCategories.size() <= 1) {
                tradeCategories.add(NekoTradeCategory.UNKNOWN);
            }
        }
    }

    /**
     * 注册页面状态通道（A01 蓝图 G6 分域下沉，注册体逐字搬移；原注册位顺序不变）
     *
     * @param syncManager 面板同步管理器
     * @param playerId    玩家 UUID（与宿主同源）
     */
    public void registerSyncValues(PanelSyncManager syncManager, UUID playerId) {
        // --- 当前标签页索引（C2S）---
        currentTabSync = new IntSyncValue(() -> currentTabId, val -> { currentTabId = val; });
        currentTabSync.allowC2S();
        syncManager.syncValue("nekoV2CurrentTab", currentTabSync);

        // --- v1.7.0 主标签索引（C2S）---
        mainTabSync = new IntSyncValue(() -> mainTabId, val -> { mainTabId = val; });
        mainTabSync.allowC2S();
        syncManager.syncValue("nekoV2MainTab", mainTabSync);

        // --- 搜索文本（C2S）---
        searchTextSync = new StringSyncValue(() -> searchText, val -> { searchText = val == null ? "" : val; });
        searchTextSync.allowC2S();
        syncManager.syncValue("nekoV2SearchText", searchTextSync);

        // --- 排序模式（C2S：0=SMART, 1=ALPHABET）---
        sortModeSync = new IntSyncValue(() -> sortMode, val -> { sortMode = val; });
        sortModeSync.allowC2S();
        syncManager.syncValue("nekoV2SortMode", sortModeSync);

        // --- 显示模式（C2S：0=TILE, 1=LIST）---
        displayTypeSync = new IntSyncValue(() -> displayType, val -> { displayType = val; });
        displayTypeSync.allowC2S();
        syncManager.syncValue("nekoV2DisplayType", displayTypeSync);

    }

    /**
     * build 期创建五个分页控制器（宿主 build() 在原控制器创建位点调用；双端）
     */
    public void createControllers() {
        tabController = new PagedWidget.Controller();
        // v1.7.0 主标签控制器（贸易/签到/抽奖/邮件）
        mainTabController = new PagedWidget.Controller();
        // v1.7.6 G1 三页 sub-page 控制器（v1.7.17 改为双端创建；G1 阶段不绑定 PagedWidget，
        // 标签按钮靠 NekoSubTabButton 防崩守卫兜底，G2 建对应页面后绑定接管）
        signInPageController = new PagedWidget.Controller();
        lotteryPageController = new PagedWidget.Controller();
        mailPageController = new PagedWidget.Controller();
    }

    /** 主标签控制器（宿主 createMainContentPagedWidget 消费） */
    public PagedWidget.Controller getMainTabController() {
        return mainTabController;
    }

    /** 签到 sub-page 控制器（宿主 createMainContentPagedWidget 消费） */
    public PagedWidget.Controller getSignInPageController() {
        return signInPageController;
    }

    /** 抽奖 sub-page 控制器（宿主 createMainContentPagedWidget 消费） */
    public PagedWidget.Controller getLotteryPageController() {
        return lotteryPageController;
    }

    /** 邮件 sub-page 控制器（宿主 createMainContentPagedWidget 消费） */
    public PagedWidget.Controller getMailPageController() {
        return mailPageController;
    }

    /** 交易分类列表（宿主预分配 Widget 池初始化消费） */
    public List<NekoTradeCategory> getTradeCategories() {
        return tradeCategories;
    }

    /** 搜索文本（宿主 PanelCallback 委托） */
    public String getSearchText() {
        return searchText;
    }

    /** 搜索栏焦点（宿主 PanelCallback 委托） */
    public boolean isSearchBarFocused() {
        return searchBar != null && searchBar.isFocused();
    }

    /** 显示模式（宿主 PanelCallback 委托） */
    public NekoDisplayType getDisplayType() {
        return displayType == 0 ? NekoDisplayType.TILE : NekoDisplayType.LIST;
    }

    /** 排序模式（宿主 PanelCallback 委托） */
    public NekoSortMode getSortMode() {
        return sortMode == 0 ? NekoSortMode.SMART : NekoSortMode.ALPHABET;
    }

    /** 当前激活分类（宿主 PanelCallback 委托，方法体逐字搬移） */
    public NekoTradeCategory getActiveCategory() {
        if (currentTabId >= 0 && currentTabId < tradeCategories.size()) {
            return tradeCategories.get(currentTabId);
        }
        return NekoTradeCategory.UNKNOWN;
    }

    /** 恢复上次会话的标签位置与搜索文本（原宿主 onRestoreSettings 方法体逐字搬移） */
    public void restoreSettings() { // 恢复上次的主标签位置（v1.7.0）
        if (mainTabController != null) {
            int page = Math.min(NekoMainTabButton.lastMainTab, MAIN_TAB_COUNT - 1);
            if (page < 0) page = MAIN_TAB_TRADE;
            mainTabController.setPage(page);
            mainTabId = page;
            // v1.7.5 修复：同步 mainTabSync 使服务端 mainTabId 与客户端一致
            // （否则服务端恒 0，背包行 enabled 状态双端可能不一致）
            if (mainTabSync != null) mainTabSync.setValue(page);
        }
        // 恢复上次的标签页位置
        if (tabController != null) {
            int maxPage = tradeCategories.size();
            int page = Math.min(NekoPageButtonV2.lastPage, maxPage - 1);
            if (page < 0) page = 0; // 默认 FAVOURITES 分类
            tabController.setPage(page);
            currentTabId = page;
            // v1.7.33 T3：恢复的标签可能不在第 0 页——翻页页码随恢复位置收敛并重建标签列，
            // 否则激活标签按钮不在可见页内（onOpen 晚于 build，late 重建走 MUI2 官方路径）
            if (tabPage != page / PAGE_SIZE) {
                tabPage = page / PAGE_SIZE;
                rebuildTabColumnChildren();
            }
        }
        // 恢复搜索文本
        if (searchBar != null && !searchText.isEmpty()) {
            searchBar.setText(searchText);
        }

    }

    // ==================== UI 组件创建 ====================

    /**
     * v1.7.0 创建主标签列（贸易/签到/抽奖/邮件）
     * <p>
     * 位于贸易分类标签列的更左边（left(-57)，贸易分类列在 left(-29)），
     * 使用 {@link NekoMainTabButton} + {@link #mainTabController} 切换主内容面板。
     * <p>
     * 切换主标签时同时通过 {@link #mainTabSync} 同步到服务端，
     * 便于服务端感知当前玩家查看的主标签（后续用于编辑模式权限控制）。
     *
     * @return 主标签列 Widget
     */
    public IWidget createMainTabColumn() {
        Flow mainTabColumn = Flow.column()
            .coverChildren()
            .left(-57)
            .top(40)
            .childPadding(2);

        // 主标签定义：index → (图标, 名称)
        Object[][] mainTabs = new Object[][] { { MAIN_TAB_TRADE, NekoGuiTextures.MAIN_TAB_TRADE, "贸易" },
            { MAIN_TAB_SIGNIN, NekoGuiTextures.MAIN_TAB_SIGNIN, "签到" },
            { MAIN_TAB_LOTTERY, NekoGuiTextures.MAIN_TAB_LOTTERY, "抽奖" },
            { MAIN_TAB_MAIL, NekoGuiTextures.MAIN_TAB_MAIL, "邮件" }, };

        for (Object[] tabDef : mainTabs) {
            final int index = (Integer) tabDef[0];
            final com.cleanroommc.modularui.drawable.UITexture icon = (com.cleanroommc.modularui.drawable.UITexture) tabDef[1];
            final String name = (String) tabDef[2];

            NekoMainTabButton tabButton = new NekoMainTabButton(index, mainTabController, icon);
            tabButton.tab(NekoGuiTextures.TAB_LEFT, -1);
            tabButton.tooltipBuilder(t -> { t.addLine(IKey.str(name)); });
            // 点击时同步主标签索引到服务端（使用 onSelected 钩子，避免与 PageButton.onMousePressed 冲突）
            tabButton.onSelected(() -> {
                if (mainTabSync != null) {
                    mainTabSync.setValue(index);
                }
            });

            mainTabColumn.child(tabButton);
        }

        return mainTabColumn.excludeAreaInRecipeViewer();
    }

    /**
     * 创建左侧标签列
     * <p>
     * 为交易分类创建 {@link NekoPageButtonV2} 标签按钮，使用物品图标作为标签页标识。
     * <p>
     * v1.7.7 G2③：仅在主标签为贸易时显示（{@link #mainTabController} 当前页 == {@link #MAIN_TAB_TRADE}）。
     * <p>
     * v1.7.33 T3 标签翻页：本列每次只为本页标签 [tabPage*{@link #PAGE_SIZE},
     * min(tabPage*PAGE_SIZE+PAGE_SIZE, size)) 创建按钮（全局索引传 {@link NekoPageButtonV2}，
     * tabController/lastPage/currentTabSync 语义不变）；tradeCategories.size() ≥ {@link #PAGE_SIZE}
     * 时在第 11 槽正下方固定一行 ◀/▶ 翻页行（短页以透明占位保持行位固定）。
     * build 与翻页共用 {@link #rebuildTabColumnChildren()} 同一条子树填充路径
     * （翻页重建走 MUI2 ParentWidget 公有 removeAll/child + late initialise 官方路径；
     * 既有 setForceRefresh→updateGui 仅刷新交易显示数据、不重建 widget 树，故不可复用）。
     * <p>
     * 几何（T3 配套，使每页 11 槽 + 翻页行落入面板 320 可视高度）：top 40→{@link #TAB_COLUMN_TOP}
     * （QoL 块已按 T2 左移出本列 x 带，原让位空间释放）、childPadding 2→{@link #TAB_COLUMN_CHILD_PADDING}、
     * 标签按钮 32x28→32x{@link #TAB_BUTTON_HEIGHT}；
     * 11 槽 + 翻页行底缘 = 8 + 11×25 + 10×1 + 1 + 14 = 308 ≤ 316（内缘）。
     *
     * @return 标签列 Widget
     */
    public IWidget createTabColumn() {
        Flow tabColumn = Flow.column()
            .coverChildren()
            .left(-29)
            .top(TAB_COLUMN_TOP)
            .childPadding(TAB_COLUMN_CHILD_PADDING);
        this.tabColumn = tabColumn;

        // v1.7.33 T3：build 与翻页共用同一填充路径（含数量变化时的页码收敛）
        rebuildTabColumnChildren();

        // 非编辑模式时「新建 page」按钮不占位（列自动收紧）——列属性，翻页重建不丢失
        tabColumn.collapseDisabledChild(true);

        // v1.7.7 G2③：读取处以 controller 当前页为准，避免 mainTabId 滞后
        tabColumn
            .setEnabledIf(w -> mainTabController != null && mainTabController.getActivePageIndex() == MAIN_TAB_TRADE);

        // 在 NEI/HEI 中排除标签列区域，避免配方查看器遮挡标签页
        return tabColumn.excludeAreaInRecipeViewer();
    }

    /**
     * v1.7.33 T3：填充/重建标签列子树（build 与翻页共用的唯一填充路径）
     * <p>
     * 顺序：本页标签按钮 → 短页透明占位 → 翻页行（size ≥ PAGE_SIZE 时）→「新建 page」按钮（编辑模式）。
     * 入口先按标签集合数量收敛页码（maxTabPage=(size-1)/PAGE_SIZE，tabPage=Math.min(tabPage, maxTabPage)，
     * 参照 {@link #restoreSettings()} 的钳制恢复模式），故标签集合数量变化后重建总是落在合法页。
     * <p>
     * 仅客户端调用：tabColumn 由 {@link #createTabColumn()} 在 isClient 块内赋值，服务端为 null 直接返回；
     * 本列按钮均为非 ISynced 客户端 Widget，运行时增删不影响双端 auto_sync ID 分配。
     */
    private void rebuildTabColumnChildren() {
        Flow column = this.tabColumn;
        if (column == null) return;

        // 标签集合数量变化时收敛页码（参照 restoreSettings 钳制模式）：maxTabPage=(size-1)/PAGE_SIZE
        int size = tradeCategories.size();
        this.maxTabPage = size > 0 ? (size - 1) / PAGE_SIZE : 0;
        if (this.tabPage > this.maxTabPage) this.tabPage = this.maxTabPage;
        if (this.tabPage < 0) this.tabPage = 0;

        // 翻页重建：清空旧子树（MUI2 ParentWidget 公有 API；dispose 后全部新建，无复用悬空引用）
        column.removeAll();

        // 仅为本页标签创建按钮：全局索引 [tabPage*PAGE_SIZE, min(tabPage*PAGE_SIZE+PAGE_SIZE, size))
        int start = tabPage * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, size);
        for (int i = start; i < end; i++) {
            final int index = i;
            NekoTradeCategory category = tradeCategories.get(i);
            ItemStack icon = StatusCodec.getCategoryIcon(category);
            String name = StatusCodec.getCategoryName(category);

            // v1.7.6 G3④：匿名子类包一层点击拦截——编辑模式下 Shift+点击 page 标签
            // 打开 page 编辑面板（不切页、不更新 lastPage）；普通点击维持原切页逻辑。
            // 收藏（tabId=-1）/未知（tabId=0）为虚拟分类，无对应 page 条目，不拦截。
            NekoPageButtonV2 tabButton = new NekoPageButtonV2(index, tabController, category, highlightedTabs, icon) {

                @Override
                public Interactable.Result onMousePressed(int mouseButton) {
                    if (gui.isEditModeActive() && Interactable.hasShiftDown() && category.getTabId() > 0) {
                        gui.pageEditor.beginEdit(category.getTabId());
                        return Interactable.Result.SUCCESS;
                    }
                    return super.onMousePressed(mouseButton);
                }
            };
            tabButton.tab(NekoGuiTextures.TAB_LEFT, -1);
            // v1.7.33 T3：TAB_LEFT 原生 32x28，压扁至 32x25 使 11 槽+翻页行落入面板可视高度（计算见类常量注释）
            tabButton.size(TAB_BUTTON_WIDTH, TAB_BUTTON_HEIGHT);
            tabButton.tooltipBuilder(t -> {
                t.addLine(IKey.str(name));
                // 编辑模式下追加操作提示（随编辑模式动态刷新）
                if (gui.isEditModeActive() && category.getTabId() > 0) {
                    t.addLine(IKey.str(EnumChatFormatting.YELLOW + "Shift+点击 编辑标签页"));
                }
            });
            tabButton.tooltipAutoUpdate(true);

            column.child(tabButton);
        }

        // v1.7.33 T3：翻页行——仅 size >= PAGE_SIZE 时创建（=PAGE_SIZE 时按钮可见但点击原地不动，
        // size < PAGE_SIZE 完全不创建），固定在第 11 槽正下方；短页（末页不足 11 槽）以透明占位
        // 撑住行位，使翻页行不随页内容浮动。
        if (size >= PAGE_SIZE) {
            int filledSpan = (end - start) * TAB_BUTTON_HEIGHT
                + Math.max(end - start - 1, 0) * TAB_COLUMN_CHILD_PADDING;
            // 扣除补位块与翻页行之间的一个 childPadding：该间距在满页由第 11 槽后的列间距承担，
            // 短页若不扣则翻页行整体比满页低 1px（如末页 1 项时 295 vs 满页 294）
            int spacer = PAGE_SIZE * TAB_BUTTON_HEIGHT + (PAGE_SIZE - 1) * TAB_COLUMN_CHILD_PADDING
                - TAB_COLUMN_CHILD_PADDING
                - filledSpan;
            if (spacer > 0) {
                column.child(
                    Flow.row()
                        .height(spacer));
            }
            Flow pagerRow = Flow.row()
                .coverChildren()
                .childPadding(2);
            pagerRow.child(createPagerButton("<", -1));
            pagerRow.child(createPagerButton(">", 1));
            column.child(pagerRow);
        }

        // v1.7.6 G3④：「新建 page」按钮——仅编辑模式显示（列尾），点击打开空白 page 编辑面板。
        // 复用 NekoSubTabButton 的 externalMode（永不选中、点击走 onSelected 不切页），
        // index 取列尾位置（externalMode 下不参与切页，仅作标识）。
        // v1.7.33 T3：归入本重建路径（翻页 removeAll 后原位重建），按钮高度与标签槽一致。
        NekoSubTabButton newPageButton = new NekoSubTabButton(
            tradeCategories.size(),
            tabController,
            IKey.str(EnumChatFormatting.GREEN + "+"));
        newPageButton.tab(NekoGuiTextures.TAB_LEFT, -1);
        newPageButton.size(TAB_BUTTON_WIDTH, TAB_BUTTON_HEIGHT);
        newPageButton.externalMode(() -> false);
        newPageButton.onSelected(gui.pageEditor::beginNew);
        newPageButton.tooltipBuilder(t -> {
            t.addLine(IKey.str("新建标签页"));
            t.addLine(IKey.str(EnumChatFormatting.GRAY + "创建空白标签页（保存时自动分配 ID ≥ 4）"));
        });
        newPageButton.setEnabledIf(w -> gui.isEditModeActive());
        column.child(newPageButton);

        // 子树变更后调度重排（MUI2 官方 late-add 路径：child(...) 内部已 late-initialise，
        // scheduleResize 确保本列 coverChildren 尺寸与子项布局下帧重算）
        column.scheduleResize();
    }

    /**
     * v1.7.33 T3：创建 ◀/▶ 翻页按钮（14x14，仓内「&lt; / &gt;」文案习语，同祝福预设编辑器目标切换行；
     * 构造习语同 QoL 音量按钮 :608-626 ButtonWidget.size(14).overlay(...).onMouseTapped(...)）。
     * <p>
     * 点击 tabPage±1 后钳制 [0, maxTabPage]：到头（含 maxTabPage=0，即 size==PAGE_SIZE 时）
     * 点击原地不动、绝不循环；翻页成功经 {@link #rebuildTabColumnChildren()} 重建标签列。
     * 未用 NekoSubTabButton：其 .tab(TAB_LEFT) 恒定 32x28（翻页行需 ≤14 高）且携带
     * 不必要的 Controller 语义，ButtonWidget 更贴合纯动作按钮。
     *
     * @param label 按钮文案（"&lt;" 上一页 / "&gt;" 下一页）
     * @param step  翻页步进（-1 / +1）
     * @return 翻页按钮 Widget
     */
    private ButtonWidget<?> createPagerButton(String label, final int step) {
        ButtonWidget<?> button = new ButtonWidget<>().size(PAGER_BUTTON_SIZE, PAGER_BUTTON_SIZE)
            .overlay(IKey.str(EnumChatFormatting.WHITE + label))
            .onMouseTapped(mouse -> {
                flipTabPage(step);
                return true;
            })
            .tooltipBuilder(t -> {
                t.addLine(IKey.str(step < 0 ? "上一页" : "下一页"));
                t.addLine(IKey.dynamic(() -> {
                    if (step < 0 && tabPage <= 0) return EnumChatFormatting.GRAY + "已是第一页";
                    if (step > 0 && tabPage >= maxTabPage) return EnumChatFormatting.GRAY + "已是最后一页";
                    return EnumChatFormatting.GRAY + "第 " + (tabPage + 1) + " / " + (maxTabPage + 1) + " 页";
                }));
            });
        button.tooltipAutoUpdate(true);
        return button;
    }

    /**
     * v1.7.33 T3：翻页 tabPage±1，钳制 [0, maxTabPage]；越界（到头）直接无操作，绝不循环；
     * 页码变化后经 {@link #rebuildTabColumnChildren()} 原位重建标签列（复用 build 同一填充路径）。
     *
     * @param step 翻页步进（-1 上一页 / +1 下一页）
     */
    private void flipTabPage(int step) {
        if (tabColumn == null) return;
        int next = tabPage + step;
        if (next < 0 || next > maxTabPage) return; // 到头不动、绝不循环（含 size==PAGE_SIZE 时 maxTabPage=0）
        tabPage = next;
        rebuildTabColumnChildren();
    }

    /**
     * v1.7.6 G1 创建指定主标签页的 sub-page 标签列（签到/抽奖/邮件）
     * <p>
     * 与贸易分类列 {@link #createTabColumn()} 同位（left(-29)；v1.7.33 T3 后分类列上移至
     * top(8)/childPadding(1)，本列维持 top(40)/childPadding(2) 原样），
     * 通过 {@code setEnabledIf} 按主标签互斥显示，不与贸易分类列/QoL 列叠放。
     * <p>
     * 按钮内容（v1.7.6）：
     * <ul>
     * <li>签到「活跃」4 按钮：每月签到/连续签到/每日在线/纪念日</li>
     * <li>邮件 5 按钮：全部/系统/玩家/管理员/写邮件</li>
     * <li>抽奖（G2①）：动态池按钮列——预分配 {@link #MAX_POOL_TABS} 个按钮，数据源
     * {@link LotteryClientData#getPools()} 每帧驱动图标/显隐（池自配图标优先，缺省按货币回退币图标），
     * 外部模式点击写 {@code selectedPoolId}（抽奖页单页动态读选中池）；编辑模式 Shift+点击开池编辑面板，
     * 列尾「+」按钮（仅编辑模式）开新建池面板；抽奖页顶部旧双池切换按钮行已随 G2① 移除</li>
     * </ul>
     * 按钮统一使用 {@link NekoSubTabButton}——G1 阶段三个 sub-page Controller 尚未绑定 PagedWidget，
     * 按钮内置防崩守卫（未绑定时点击直接忽略），G2 建对应 PagedWidget 页面后自动接管切换。
     * <p>
     * 双端安全：纯按钮无槽位，仅客户端创建（与 createTabColumn 一致）。
     *
     * @param mainTab 主标签索引（{@link #MAIN_TAB_SIGNIN}/{@link #MAIN_TAB_LOTTERY}/{@link #MAIN_TAB_MAIL}）
     * @return sub-page 标签列 Widget
     */
    public IWidget createSubTabColumn(final int mainTab) {
        Flow subTabColumn = Flow.column()
            .coverChildren()
            .left(-29)
            .top(40)
            .childPadding(2);

        if (mainTab == MAIN_TAB_LOTTERY) {
            // --- 抽奖页：动态池按钮列（v1.7.6 G2①，每池一按钮）---
            // 预分配 MAX_POOL_TABS 个按钮：图标/选中态/显隐全部每帧动态求值（数据源
            // LotteryClientData.getPools()），池同步包晚于 GUI 打开到达时按钮随缓存更新自动出现，
            // 解决 G1 过渡态「列在 build 时一次性生成、同步晚到则空列」的问题。
            // 隐藏按钮由列尾 collapseDisabledChild 压缩占位；纯按钮无槽位，setEnabledIf 只挡绘制足够。
            for (int i = 0; i < MAX_POOL_TABS; i++) {
                final int index = i;
                // 图标动态求值：池自配图标（iconItem）优先 → 缺省按池货币回退币图标
                DynamicDrawable icon = new DynamicDrawable(() -> {
                    LotteryClientData.PoolSummary pool = poolAt(index);
                    if (pool != null) {
                        ItemStack iconStack = pool.toIconItemStack();
                        if (iconStack != null) {
                            return new ItemDrawable(iconStack);
                        }
                        return NekoCurrencyRegistrar.SHIMMERING_NEKO_ID.equals(pool.currencyId)
                            ? NekoGuiTextures.LOTTERY_COIN_SHIMMER
                            : NekoGuiTextures.LOTTERY_COIN_NEKO;
                    }
                    // 隐藏态返回值（不绘制，仅占位防空指针）
                    return NekoGuiTextures.LOTTERY_COIN_NEKO;
                });
                NekoSubTabButton poolButton = new NekoSubTabButton(index, lotteryPageController, icon);
                poolButton.tab(NekoGuiTextures.TAB_LEFT, -1);
                // 外部模式：选中态=当前选中池（LotteryClientData.selectedPoolId），
                // 点击=切换选中池（抽奖页单页动态读 getSelectedPool，无需每池一页 PagedWidget）；
                // 编辑模式下 Shift+点击=打开池编辑面板
                poolButton.externalMode(() -> {
                    LotteryClientData.PoolSummary pool = poolAt(index);
                    return pool != null && pool.id != null && pool.id.equals(LotteryClientData.getSelectedPoolId());
                });
                poolButton.onSelected(() -> {
                    LotteryClientData.PoolSummary pool = poolAt(index);
                    if (pool == null) return;
                    if (gui.isEditModeActive() && Interactable.hasShiftDown()) {
                        gui.lotteryPoolEditor.beginEdit(pool);
                    } else {
                        LotteryClientData.setSelectedPoolId(pool.id);
                    }
                });
                poolButton.tooltipBuilder(t -> {
                    LotteryClientData.PoolSummary pool = poolAt(index);
                    if (pool != null) {
                        t.addLine(IKey.str(pool.name));
                        if (gui.isEditModeActive()) {
                            t.addLine(IKey.str(EnumChatFormatting.YELLOW + "Shift+点击 编辑卡池"));
                        }
                    }
                });
                poolButton.tooltipAutoUpdate(true);
                // 池数量不足时隐藏本按钮（不占位，列尾 collapseDisabledChild 压缩）
                poolButton.setEnabledIf(w -> poolAt(index) != null);
                subTabColumn.child(poolButton);
            }
            // --- 「新建池」按钮：仅编辑模式显示（列尾），点击打开空白池编辑面板 ---
            NekoSubTabButton newPoolButton = new NekoSubTabButton(
                MAX_POOL_TABS,
                lotteryPageController,
                IKey.str(EnumChatFormatting.GREEN + "+"));
            newPoolButton.tab(NekoGuiTextures.TAB_LEFT, -1);
            // 外部模式：永不选中；点击直接打开新建池编辑面板
            newPoolButton.externalMode(() -> false);
            newPoolButton.onSelected(gui.lotteryPoolEditor::beginNew);
            newPoolButton.tooltipBuilder(t -> {
                t.addLine(IKey.str("新建卡池"));
                t.addLine(IKey.str(EnumChatFormatting.GRAY + "创建空白卡池（含 1 条种子奖品）"));
            });
            newPoolButton.setEnabledIf(w -> gui.isEditModeActive());
            subTabColumn.child(newPoolButton);
            // 隐藏按钮不占位（池数量 < MAX_POOL_TABS / 非编辑模式时列自动收紧）
            subTabColumn.collapseDisabledChild(true);
        } else {
            // --- 签到/邮件页：静态按钮定义（index → 图标, 中文名）---
            // 图标选用 NekoGuiTextures 已注册材质中语义最接近者，不完全贴合的图标见 G1 报告的补做清单
            final Object[][] subTabs;
            final PagedWidget.Controller controller;
            if (mainTab == MAIN_TAB_SIGNIN) {
                // 签到「活跃」4 页（与 G2③ 页面顺序一致）：每月签到/连续签到/每日在线/纪念日
                controller = signInPageController;
                subTabs = new Object[][] { { 0, NekoGuiTextures.SIGNIN_CELL_SIGNED, "每月签到" },
                    { 1, NekoGuiTextures.SIGNIN_CHEST_30, "连续签到" }, { 2, NekoGuiTextures.SIGNIN_CELL_REWARD, "每日在线" },
                    { 3, NekoGuiTextures.FAVOURITE_SPRITE, "纪念日" }, };
            } else {
                // 邮件 5 页（与 G2② 页面顺序一致）：全部/系统/玩家/管理员 + 写邮件入口
                controller = mailPageController;
                subTabs = new Object[][] { { 0, NekoGuiTextures.MAIL_ICON_READ, "全部" },
                    { 1, NekoGuiTextures.MAIL_ICON_UNREAD, "系统" }, { 2, NekoGuiTextures.WALLET_PERSONAL, "玩家" },
                    { 3, NekoGuiTextures.MAIN_TAB_EDIT, "管理员" }, { 4, NekoGuiTextures.MAIL_WRITE, "写邮件" }, };
            }
            for (Object[] tabDef : subTabs) {
                final int index = (Integer) tabDef[0];
                final com.cleanroommc.modularui.drawable.UITexture icon = (com.cleanroommc.modularui.drawable.UITexture) tabDef[1];
                final String name = (String) tabDef[2];
                NekoSubTabButton subTabButton = new NekoSubTabButton(index, controller, icon);
                subTabButton.tab(NekoGuiTextures.TAB_LEFT, -1);
                subTabButton.tooltipBuilder(t -> { t.addLine(IKey.str(name)); });
                // v1.7.6 G2②：邮件页切换 sub-tab（全部/系统/玩家/管理员）时重置列表页码——
                // 各类型过滤后总页数不同，不重置则翻页行可能短暂显示「当前页 > 总页数」。
                // 写邮件页（index 4）无列表，同回调重置无副作用；签到页（G2③）如需同逻辑自行挂接。
                if (mainTab == MAIN_TAB_MAIL) {
                    subTabButton.onSelected(() -> MailClientData.setListPage(0));
                }
                subTabColumn.child(subTabButton);
            }
            // --- v1.7.6 G5：「祝福预设」入口按钮——仅邮件页 + 编辑模式显示（列尾）---
            // 外部模式：永不选中（入口按钮不切换 sub-page，点击直接弹出祝福预设编辑面板）
            if (mainTab == MAIN_TAB_MAIL) {
                NekoSubTabButton blessingButton = new NekoSubTabButton(5, controller, NekoGuiTextures.MAIL_PAPER);
                blessingButton.tab(NekoGuiTextures.TAB_LEFT, -1);
                blessingButton.externalMode(() -> false);
                blessingButton.onSelected(() -> gui.blessingEditor.beginEdit("birthday"));
                blessingButton.tooltipBuilder(t -> {
                    t.addLine(IKey.str("祝福预设"));
                    t.addLine(IKey.str(EnumChatFormatting.GRAY + "查看/编辑自动祝福邮件模板"));
                });
                blessingButton.tooltipAutoUpdate(true);
                blessingButton.setEnabledIf(w -> gui.isEditModeActive());
                subTabColumn.child(blessingButton);
                // 非编辑模式时入口按钮不占位（列自动收紧）
                subTabColumn.collapseDisabledChild(true);
            }
        }

        // v1.7.7 G2③：以 controller 当前页为权威，避免与 mainTabId 不同步
        subTabColumn.setEnabledIf(w -> mainTabController != null && mainTabController.getActivePageIndex() == mainTab);

        // 在 NEI/HEI 中排除标签列区域，避免配方查看器遮挡标签页
        return subTabColumn.excludeAreaInRecipeViewer();
    }

    /**
     * 创建 QoL 按钮列（BGM/音量、显示模式、货币显示开关、排序模式）
     * <p>
     * 位于标签列左侧，使用 2x2 Grid 布局提供快速操作按钮。
     * <p>
     * v1.7.33 T2：左锚 left(-33)→left(-60)（{@link #QOL_GRID_LEFT}）——原 -33 与贸易分类
     * 标签列（left(-29)，x ∈ [-29, +3)）的 x 投影重叠 25px；左移后块 x ∈ [-60, -31)，
     * 与标签列水平完全脱开且间隙 2px。主标签列（left(-57)）与块 y 向错开 10px
     * （块 y ∈ [1, 30)、主列 y ∈ [40, 158)），渲染像素零碰撞，计算式见常量注释。
     * <p>
     * 按钮顺序完全模仿 V1（VM 的 MTEVendingMachineGui.createQolButtonColumn）：
     * <ul>
     * <li>左上：音量/BGM 按钮。单击切换 BGM 曲目播放/停止；Shift+点击打开音量控制面板。</li>
     * <li>右上：显示模式切换按钮（TILE ↔ LIST）。</li>
     * <li>左下：货币余额行显示/隐藏开关。</li>
     * <li>右下：排序模式切换按钮（SMART ↔ ALPHABET）。</li>
     * </ul>
     * 同时在此方法内创建 {@link #gui.volumePanel}，将本按钮作为 parent 传给
     * {@link NekoVolumeControlGui#createPanel}，修复旧版传 null 导致 NPE 的问题。
     *
     * @return QoL 按钮列 Widget
     */
    public IWidget createQolButtonColumn() {
        // --- 音量/BGM 按钮（左上）---
        // 单击切换 BGM 播放/停止；Shift+点击打开音量面板
        // v1.6.24: gui.volumeButton 改为赋值类字段（移除 final 局部变量），以便 build() 在 client 块外部注册 syncedPanel
        gui.volumeButton = new ButtonWidget<>().size(14, 14)
            .overlay(new DynamicDrawable(() -> {
                NekoMusicEventHandler handler = NekoMusicEventHandler.getInstance();
                boolean isPlaying = handler != null && handler.isPlaying();
                return (isPlaying ? NekoMusicTrack.LUNCH_BREAK.getTexture() : NekoMusicTrack.NONE.getTexture())
                    .size(14);
            }))
            .onMousePressed(btn -> {
                if (Interactable.hasShiftDown()) {
                    // Shift+点击：打开/关闭音量控制面板
                    if (gui.volumePanel != null) {
                        gui.volumePanel.togglePanel();
                    }
                } else {
                    // 单击：切换 BGM 播放
                    NekoMusicEventHandler handler = NekoMusicEventHandler.getInstance();
                    if (handler != null) {
                        if (handler.isPlaying()) {
                            handler.forceStopBGM();
                        } else {
                            handler.forceStartBGM();
                        }
                    }
                }
                return true;
            })
            .tooltipBuilder(t -> {
                NekoMusicEventHandler handler = NekoMusicEventHandler.getInstance();
                boolean isPlaying = handler != null && handler.isPlaying();
                t.addLine(IKey.str(isPlaying ? "BGM: 开启" : "BGM: 关闭"));
                t.addLine(IKey.str("音量: " + NekoVolumeControlGui.getVolumeAsString() + "%"));
                t.addLine(IKey.str("Shift+点击 打开音量控制"));
            });
        gui.volumeButton.tooltipAutoUpdate(true);

        // --- 显示模式切换按钮（右上）---
        ButtonWidget<?> displayModeButton = new ButtonWidget<>().size(14, 14)
            .overlay(
                new DynamicDrawable(
                    () -> gui.getDisplayType()
                        .getTexture()
                        .size(14)))
            .onMousePressed(btn -> {
                // 切换显示模式：TILE ↔ LIST
                displayTypeSync.setValue(displayType == 0 ? 1 : 0);
                return true;
            })
            .tooltipBuilder(t -> {
                t.addLine(
                    IKey.str(
                        "当前: " + gui.getDisplayType()
                            .getLocalizedName()));
                t.addLine(IKey.str("点击切换显示模式"));
            });
        displayModeButton.tooltipAutoUpdate(true);

        // --- 货币显示开关按钮（左下）---
        ButtonWidget<?> showCoinsButton = new ButtonWidget<>().size(14, 14)
            .overlay(
                new DynamicDrawable(
                    () -> (gui.showCoins ? NekoGuiTextures.SHOW_COINS : NekoGuiTextures.HIDE_COINS).asIcon()
                        .size(14)))
            .onMousePressed(btn -> {
                // 切换货币余额行的显示/隐藏
                gui.showCoinsSync.setValue(!gui.showCoins);
                return true;
            })
            .tooltipBuilder(t -> { t.addLine(IKey.str(gui.showCoins ? "隐藏猫猫币" : "显示猫猫币")); });
        showCoinsButton.tooltipAutoUpdate(true);

        // --- 排序模式切换按钮（右下）---
        ButtonWidget<?> sortModeButton = new ButtonWidget<>().size(14, 14)
            .overlay(
                new DynamicDrawable(
                    () -> gui.getSortMode()
                        .getTexture()
                        .size(14)))
            .onMousePressed(btn -> {
                // 切换排序模式：SMART ↔ ALPHABET
                sortModeSync.setValue(sortMode == 0 ? 1 : 0);
                return true;
            })
            .tooltipBuilder(t -> {
                t.addLine(
                    IKey.str(
                        "当前: " + gui.getSortMode()
                            .getLocalizedName()));
                t.addLine(IKey.str("点击切换排序模式"));
            });
        sortModeButton.tooltipAutoUpdate(true);

        // v1.6.24: gui.volumePanel 注册已移至 build() 的 client 块外部（与 VM 原版一致），
        // 确保服务端也注册同步通道，否则客户端 togglePanel() 静默失败

        // 2x2 网格：左上音量、右上显示模式、左下显示硬币、右下排序
        // v1.7.33 T2：left(-33)→left(-60)，使 QoL 块（宽 29）与标签列 x ∈ [-29, +3) 水平脱开 2px
        // 在 NEI/HEI 中排除 QoL 按钮列区域，避免配方查看器遮挡快捷按钮
        Grid qolGrid = new Grid().left(QOL_GRID_LEFT)
            .top(1)
            .minElementMargin(1, 1)
            .coverChildren()
            .grid(
                Arrays.asList(
                    Arrays.asList(gui.volumeButton, displayModeButton),
                    Arrays.asList(showCoinsButton, sortModeButton)));
        // v1.7.7 G2③：以 controller 当前页为权威
        qolGrid
            .setEnabledIf(w -> mainTabController != null && mainTabController.getActivePageIndex() == MAIN_TAB_TRADE);
        return qolGrid.excludeAreaInRecipeViewer();
    }

    /**
     * 创建贸易主内容列（v1.7.0 前为 createMainColumn）
     * <p>
     * 布局从上到下：标题、搜索栏、交易列表（PagedWidget）、猫猫币显示、交易结果、玩家背包。
     * <p>
     * 输入槽和输出槽已迁移到 {@link #createIOColumn()}（与 V1 的 IO 列布局一致），
     * 主列不再承载 IO 元素，以保持 UI 与 V1 一致。
     *
     * @param syncManager 面板同步管理器
     * @return 主列 Widget
     */
    public IWidget createTradeMainColumn(PanelSyncManager syncManager) {
        Flow mainColumn = Flow.column()
            .width(PANEL_WIDTH - 8);

        // --- 标题（编辑模式下附加红色「编辑模式」标识，v1.7.0 目标 4 视觉标识）---
        mainColumn.child(
            IKey.dynamic(
                () -> gui.isEditModeActive()
                    ? EnumChatFormatting.DARK_GRAY + "猫猫售货机 " + EnumChatFormatting.RED + "[编辑模式]"
                    : EnumChatFormatting.DARK_GRAY + "猫猫售货机")
                .asWidget()
                .height(12)
                .fullWidth()
                .marginBottom(2));

        // --- 团队钱包标识 + ME 输出模式切换按钮（同一行平行显示）---
        // [团队钱包]在左侧（仅 TEAM 模式时显示内容），[自动输入ME: 开/关]在右侧（仅 uplink 在线时显示）
        // 两者平行排列在标题下方同一行，互不影响显示
        mainColumn.child(
            Flow.row()
                .height(12)
                .fullWidth()
                .marginBottom(2)
                // [团队钱包]动态文本（仅 TEAM 模式时显示，否则折叠隐藏）
                .child(IKey.dynamic(() -> {
                    NekoWalletMode mode = gui.getWalletMode();
                    if (mode == NekoWalletMode.TEAM) {
                        return EnumChatFormatting.AQUA + "[团队钱包]";
                    }
                    return "";
                })
                    .asWidget()
                    .height(10)
                    .left(0)
                    .setEnabledIf(w -> gui.getWalletMode() == NekoWalletMode.TEAM))
                // [自动输入ME: 开/关]切换按钮（仅 uplink 在线时显示）
                .child(
                    new ButtonWidget<>().size(120, 12)
                        .overlay(IKey.dynamic(() -> {
                            boolean mode = gui.meOutputModeSync != null && gui.meOutputModeSync.getValue();
                            return mode ? EnumChatFormatting.LIGHT_PURPLE + "[自动输入ME: 开]"
                                : EnumChatFormatting.GRAY + "[自动输入ME: 关]";
                        }))
                        .tooltipBuilder(t -> {
                            t.addLine(IKey.str("切换产出路径：本地出货槽 ↔ ME 网络"));
                            if (gui.hasUplinkSync != null && gui.hasUplinkSync.getValue()) {
                                t.addLine(IKey.str(EnumChatFormatting.GRAY + "点击弹出确认对话框"));
                            }
                        })
                        .tooltipAutoUpdate(true)
                        .onMouseTapped(mouse -> {
                            if (gui.meModeConfirmDialog != null && gui.meModeConfirmPanel != null) {
                                boolean currentMode = gui.meOutputModeSync != null && gui.meOutputModeSync.getValue();
                                String message = currentMode ? "确认关闭 ME 自动输入模式？新产出将走本地出货槽"
                                    : "确认开启 ME 自动输入模式？新产出将通过 Uplink 发送到 ME 网络";
                                gui.meModeConfirmDialog.setParams(message, () -> {
                                    if (gui.meOutputModeSync != null) {
                                        gui.meOutputModeSync.setValue(!currentMode);
                                    }
                                });
                                gui.meModeConfirmPanel.openPanel();
                            }
                            return true;
                        })
                        .right(0)
                        .setEnabledIf(w -> gui.hasUplinkSync != null && gui.hasUplinkSync.getValue())));

        if (syncManager.isClient()) {
            // --- 搜索栏 ---
            searchBar = new NekoSearchBar("搜索...");
            searchBar.size(PANEL_WIDTH - 12, 14)
                .fullWidth()
                .marginBottom(2);
            searchBar.setSearchListener(newText -> { searchTextSync.setValue(newText); });
            mainColumn.child(searchBar);

            // --- 交易列表（PagedWidget + 预分配 Widget）---
            mainColumn.child(createTradePagedWidget());

            // 注：gui.volumePanel 已在 build() 的 client 块外部创建（v1.6.24 修复，与 VM 原版一致）
        }

        // --- 猫猫币余额显示行 ---
        mainColumn.child(createCoinDisplayRow(syncManager));

        // --- 交易结果消息（v1.6.23：背包栏已底部锚定，消息高度不影响布局）---
        mainColumn.child(
            IKey.dynamic(
                () -> gui.tradeResultMessage.isEmpty() ? "" : EnumChatFormatting.YELLOW + gui.tradeResultMessage)
                .asWidget()
                .height(12)
                .fullWidth()
                .marginBottom(2)
                .setEnabledIf(w -> !gui.tradeResultMessage.isEmpty()));

        return mainColumn;
    }

    /**
     * 创建交易列表分页 Widget
     * <p>
     * 使用 {@link PagedWidget} 为每个交易分类创建一个页面，
     * 每页包含一个 {@link ListWidget}，内含预分配的 TILE 和 LIST 模式 Widget。
     * <p>
     * 通过 {@link PagedWidget.Controller} 控制页面切换，
     * 与 {@link NekoPageButtonV2} 标签按钮联动。
     *
     * @return 交易列表 Widget
     */
    private IWidget createTradePagedWidget() {
        PagedWidget<?> paged = new PagedWidget<>().name("nekoV2Paged")
            .width(PANEL_WIDTH - 12)
            .controller(tabController)
            .background(NekoGuiTextures.TRADE_BACKGROUND);
        // 交易列表高度与 V1 保持一致
        paged.height(146);

        // 为每个分类创建一个页面
        for (NekoTradeCategory category : tradeCategories) {
            paged.addPage(createTradeListPage(category));
        }

        // 如果没有页面，添加占位页面防止崩溃
        if (tradeCategories.isEmpty()) {
            paged.addPage(
                IKey.str("无可用交易")
                    .asWidget());
        }

        return paged;
    }

    /**
     * 创建单个分类的交易列表页面
     * <p>
     * 每页包含一个 {@link ListWidget}，内含：
     * <ol>
     * <li>顶部间距</li>
     * <li>结构不完整提示行（机器未激活时显示）</li>
     * <li>TILE 模式行（3个 Widget 一行，共 100 行 = 300 个）</li>
     * <li>LIST 模式行（1个 Widget 一行，共 300 行）</li>
     * <li>底部间距</li>
     * </ol>
     * <p>
     * TILE 和 LIST 模式的 Widget 同时存在于列表中，
     * 通过 setEnabledIf 根据当前显示模式控制可见性。
     *
     * @param category 交易分类
     * @return 列表页面 Widget
     */
    private IWidget createTradeListPage(NekoTradeCategory category) {
        ListWidget<IWidget, ?> tradeList = new ListWidget<>().name("items_" + category.getKey())
            .width(PANEL_WIDTH - 14)
            .top(1)
            .collapseDisabledChild(true);
        // 交易列表页面高度与外层 PagedWidget 保持一致
        tradeList.height(146);

        // 顶部间距
        tradeList.child(
            Flow.row()
                .height(2));

        // 结构不完整提示行
        Flow statusRow = Flow.row()
            .height(10)
            .width(TRADE_ROW_WIDTH)
            .marginLeft(2)
            .child(
                IKey.str(EnumChatFormatting.RED + "结构不完整")
                    .asWidget());
        statusRow.setEnabledIf(w -> !gui.isMachineActive());
        tradeList.child(statusRow);

        // --- TILE 模式行（3个 Widget 一行）---
        List<NekoTradeItemDisplayWidget> tileWidgets = gui.displayedTradesTiles.get(category);
        if (tileWidgets != null) {
            NekoTradeRow row = new NekoTradeRow();
            row.height(TILE_ITEM_HEIGHT + 4);
            row.width(TRADE_ROW_WIDTH);
            row.marginLeft(2);

            for (int i = 0; i < MAX_TRADES; i++) {
                final int index = i;
                NekoTradeItemDisplayWidget widget = tileWidgets.get(i);
                widget.margin(2);
                widget.setEnabledIf(w -> {
                    // 编辑模式：绕过结构完整性检查，显示所有有条目的 Widget
                    if (gui.isEditModeActive()) {
                        return gui.getDisplayType() == NekoDisplayType.TILE && widget.getDisplay() != null;
                    }
                    if (!gui.isMachineActive()) return false;
                    return gui.getDisplayType() == NekoDisplayType.TILE && widget.getDisplay() != null;
                });
                row.child(widget);

                // 每 TILE_ITEMS_PER_ROW 个换行
                if (i % TILE_ITEMS_PER_ROW == TILE_ITEMS_PER_ROW - 1) {
                    tradeList.child(row);
                    row = new NekoTradeRow();
                    row.height(TILE_ITEM_HEIGHT + 4);
                    row.width(TRADE_ROW_WIDTH);
                    row.marginLeft(2);
                }
            }
            // 添加最后一行（如果有剩余）
            if (row.getChildren() != null && !row.getChildren()
                .isEmpty()) {
                tradeList.child(row);
            }
        }

        // --- LIST 模式行（1个 Widget 一行）---
        List<NekoTradeItemDisplayWidget> listWidgets = gui.displayedTradesList.get(category);
        if (listWidgets != null) {
            for (int i = 0; i < MAX_TRADES; i++) {
                NekoTradeItemDisplayWidget widget = listWidgets.get(i);
                widget.setEnabledIf(w -> {
                    // 编辑模式：绕过结构完整性检查，显示所有有条目的 Widget
                    if (gui.isEditModeActive()) {
                        return gui.getDisplayType() == NekoDisplayType.LIST && widget.getDisplay() != null;
                    }
                    if (!gui.isMachineActive()) return false;
                    return gui.getDisplayType() == NekoDisplayType.LIST && widget.getDisplay() != null;
                });

                NekoTradeRow row = new NekoTradeRow();
                row.height(LIST_ITEM_HEIGHT);
                row.width(TRADE_ROW_WIDTH);
                row.marginLeft(2);
                row.child(widget);
                tradeList.child(row);
            }
        }

        // --- 「新建交易条目」按钮行：仅编辑模式显示（列表尾），点击打开空白交易编辑面板（v1.7.6 G3④）---
        // 收藏（tabId=-1）/未知（tabId=0）为虚拟分类，新建条目无处可挂，不显示按钮；
        // 非编辑模式时由 tradeList 的 collapseDisabledChild 压缩占位。
        NekoTradeRow newTradeRow = new NekoTradeRow();
        newTradeRow.height(LIST_ITEM_HEIGHT);
        newTradeRow.width(TRADE_ROW_WIDTH);
        newTradeRow.marginLeft(2);
        ButtonWidget<?> newTradeButton = new ButtonWidget<>().size(TRADE_ROW_WIDTH - 4, LIST_ITEM_HEIGHT - 2)
            .overlay(IKey.str(EnumChatFormatting.GREEN + "+ 新建交易条目"))
            .onMouseTapped(mouse -> {
                gui.tradeEditor.beginNewTrade(category.getTabId());
                return true;
            });
        newTradeButton.tooltipBuilder(t -> {
            t.addLine(IKey.str("新建交易条目"));
            t.addLine(IKey.str(EnumChatFormatting.GRAY + "在本标签页尾部追加一条空白交易"));
        });
        newTradeRow.child(newTradeButton);
        newTradeRow.setEnabledIf(w -> gui.isEditModeActive() && category.getTabId() > 0);
        tradeList.child(newTradeRow);

        // 底部间距
        tradeList.child(
            Flow.row()
                .height(2));

        return tradeList;
    }

    /**
     * 创建猫猫币余额显示行
     * <p>
     * 为每种猫猫币创建一个 {@link NekoCoinDisplayV2} 组件，
     * 含 serp 缓动动画和弹出按钮。
     *
     * @param syncManager 面板同步管理器
     * @return 猫猫币显示行 Widget
     */
    private IWidget createCoinDisplayRow(PanelSyncManager syncManager) {
        // v1.6.22：改造为单行布局，ME 导入按钮移入 NekoCoinDisplayV2 与弹出按钮同行
        Flow column = Flow.column()
            .fullWidth()
            .marginBottom(2);

        // === 余额行（含 ME 导入按钮）===
        Flow row = Flow.row()
            .height(22)
            .fullWidth()
            .marginBottom(2);

        int offset = 0;
        for (String currencyId : NekoCurrencyRegistrar.getNekoCurrencyIds()) {
            final String cid = currencyId;
            String displayName = NekoCurrencyRegistrar.getDisplayName(currencyId);
            NekoCoinDisplayV2 coinDisplay = new NekoCoinDisplayV2(syncManager, currencyId, displayName);
            // 注入 ME 余额查询器，使弹出按钮 tooltip 显示 ME 网络余额
            coinDisplay.setMeAmountSupplier(() -> gui.meCoinAmounts.getOrDefault(cid, 0));
            // v1.6.22：注入 ME 导入配置（替代原 importRow 独立行）
            coinDisplay.setMeImportConfig(
                () -> gui.meCoinAmounts.getOrDefault(cid, 0),
                () -> gui.hasUplinkSync != null && gui.hasUplinkSync.getValue(),
                () -> {
                    BooleanSyncValue sync = gui.coinOps.getImportMeCoinSync(cid);
                    if (sync != null) {
                        sync.setValue(true);
                    }
                });
            coinDisplay.left(offset);
            row.child(coinDisplay);
            offset += 79; // 组件宽度 76 + 间距 3 = 79
        }

        // 根据货币显示开关控制余额行的显示/隐藏
        row.setEnabledIf(w -> gui.showCoins)
            .collapseDisabledChild(true);
        column.child(row);

        return column;
    }

    /**
     * 打开抽奖条目编辑面板（客户端，编辑模式下点击轮盘槽位触发）
     * <p>
     * 数值字段（货币/数量区间/权重/稀有度）从客户端缓存 {@link LotteryClientData} 的条目
     * 直接填充（与轮盘展示同源）；物品奖品经 {@link #editLotteryTargetSync} 通知服务端
     * 从权威配置加载到编辑缓冲区（PhantomItemSlot 自动同步回客户端显示，含 NBT）。
     *
     * @param pool      条目所属卡池摘要
     * @param entry     被点击的抽奖条目
     * @param slotIndex 轮盘槽位序号（仅日志/展示用，定位靠 poolId+entryId）
     */
    /**
     * 服务端：加载抽奖条目物品奖品到编辑缓冲区
     * <p>
     * 解析 "&lt;poolId&gt;:&lt;entryId&gt;" 目标标识，从 {@code LotteryManager}（服务端权威配置）
     * 查找条目；物品奖品构建 ItemStack（数量固定 1，含 NBT）放入 slot 0，
     * 货币奖品或查找失败则清空。
     *
     * @param target "&lt;poolId&gt;:&lt;entryId&gt;" 格式的目标标识
     */
    /**
     * 构建抽奖条目编辑面板
     * <p>
     * 字段布局：货币 ID（非空 = 货币奖品）→ 物品 PhantomItemSlot（货币 ID 留空时生效）
     * → 最小/最大数量 → 权重 → 稀有度（点击循环切换）。
     * 保存时经 {@link com.miaokatze.gtit.trade.v2.NekoEditNetworkManager#sendSaveLotteryEntry}
     * 发送 JSON 到服务端（{@code NekoEditActionHandler#saveLotteryEntry} 落盘 + 热重载 +
     * 推送 {@code LotterySyncPacket} 刷新轮盘）。
     *
     * @return 编辑覆盖层面板（{@link ParentWidget}）
     */
    /**
     * 稀有度循环切换（COMMON→RARE→EPIC→LEGENDARY→COMMON）
     */
    /**
     * 稀有度按钮显示文本（带稀有度颜色）
     */
    /**
     * 保存抽奖编辑（客户端 → 服务端）
     * <p>
     * 序列化 {@code {nekoCurrencyId, item, meta, nbtBase64?, minAmount, maxAmount, weight, rarity}}
     * （物品取自 PhantomItemSlot，物品 ID 无法解析时发空串交由服务端按货币/校验处理）。
     * 经 {@link com.miaokatze.gtit.trade.v2.NekoEditNetworkManager#sendSaveLotteryEntry} 发送。
     */
    // ==================== 抽奖卡池编辑（v1.7.6 G2①） ====================

    /**
     * 按索引取客户端缓存的卡池摘要（越界返回 null）
     * <p>
     * 仅供池标签列按钮每帧动态求值（图标/选中态/显隐），纯客户端轻量读。
     *
     * @param index 池在 {@link LotteryClientData#getPools()} 中的序号
     * @return 池摘要，不存在返回 null
     */
    private LotteryClientData.PoolSummary poolAt(int index) {
        List<LotteryClientData.PoolSummary> pools = LotteryClientData.getPools();
        return index >= 0 && index < pools.size() ? pools.get(index) : null;
    }

    /**
     * 更新标签高亮
     * <p>
     * 当搜索文本非空时，高亮所有包含匹配交易的分类标签。
     *
     * @param trades 按分类组织的交易显示数据
     */
    public void updateTabHighlighting(Map<NekoTradeCategory, List<NekoTradeItemDisplay>> trades) {
        highlightedTabs.clear();
        if (searchText == null || searchText.isEmpty()) {
            return;
        }
        for (Map.Entry<NekoTradeCategory, List<NekoTradeItemDisplay>> entry : trades.entrySet()) {
            if (entry.getValue() != null && !entry.getValue()
                .isEmpty()) {
                highlightedTabs.add(entry.getKey());
            }
        }
    }
}

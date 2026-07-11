package com.miaokatze.gtit.common.machine.v2;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.ISound;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.drawable.ItemDrawable;
import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.BooleanSyncValue;
import com.cleanroommc.modularui.value.sync.DynamicSyncHandler;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.StringSyncValue;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.DynamicSyncedWidget;
import com.cleanroommc.modularui.widgets.ItemDisplayWidget;
import com.cleanroommc.modularui.widgets.SlotGroupWidget;
import com.cleanroommc.modularui.widgets.ToggleButton;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;
import com.miaokatze.gtit.main.GTInterestingThing;
import com.miaokatze.gtit.trade.NekoCurrencyRegistrar;
import com.miaokatze.gtit.trade.NekoWallet;
import com.miaokatze.gtit.trade.NekoWalletManager;
import com.miaokatze.gtit.trade.v2.NekoBigItemStack;
import com.miaokatze.gtit.trade.v2.NekoHistoryManager;
import com.miaokatze.gtit.trade.v2.NekoTrade;
import com.miaokatze.gtit.trade.v2.NekoTradeDatabase;
import com.miaokatze.gtit.trade.v2.NekoTradeGroup;
import com.miaokatze.gtit.trade.v2.NekoTradeHistory;
import com.miaokatze.gtit.trade.v2.NekoTradeResult;

import gregtech.api.metatileentity.implementations.MTEHatchInputBus;
import gregtech.api.modularui2.GTGuiTextures;
import gregtech.common.gui.modularui.multiblock.base.MTEMultiBlockBaseGui;
import gregtech.common.modularui2.factory.GTBaseGuiBuilder;

/**
 * 猫猫售货机 V2 GUI
 * <p>
 * 继承 GT5U 的 {@link MTEMultiBlockBaseGui}，复用标准多方块 GUI 框架
 * （同步值注册、电源开关、结构更新按钮等），
 * 同时实现 V2 版本的 12 项功能等价性：
 * <ul>
 * <li>动态标签页系统（按交易数据库的 tabId 自动生成标签）</li>
 * <li>猫猫币余额显示（neko + shimmeringNeko 两种货币）</li>
 * <li>猫猫币自动导入（扫描输入总线，将猫猫币导入钱包）</li>
 * <li>弹出猫猫币按钮（弹出所有猫猫币到机器旁）</li>
 * <li>交易列表显示（DynamicSyncedWidget 动态构建交易图标网格）</li>
 * <li>输入槽/输出槽（V2 使用总线 I/O，通过导入按钮替代直接输入槽）</li>
 * <li>排序功能（按 orderId 或产物名排序）</li>
 * <li>搜索功能（按产物名、需求物品名、猫猫币名称过滤）</li>
 * <li>BQ 锁定状态显示（金色 LOCKED 文字 + tooltip）</li>
 * <li>冷却时间显示（青色秒数文字 + tooltip）</li>
 * <li>BGM 播放（独立于 VM，使用 MC 原生音频系统）</li>
 * <li>可交易状态绿色边框（可交易时在物品周围绘制绿色边框）</li>
 * </ul>
 * <p>
 * 不依赖任何 VM mod 类（com.cubefury.vendingmachine.*），
 * 所有交易数据通过 V2 交易系统 API 获取。
 *
 * @see MTEMultiBlockBaseGui
 * @see NekoTradeDisplayWidgetV2
 * @see NekoTradeDatabase
 */
public class NekoVMGuiV2 extends MTEMultiBlockBaseGui<MTENekoVendingMachineV2> {

    // ==================== 常量 ====================

    /** BGM 资源路径（对应 sounds.json 中的 track.neko_bgm） */
    private static final ResourceLocation NEKO_BGM = new ResourceLocation("gtit", "track.neko_bgm");

    /** 面板宽度 */
    private static final int PANEL_WIDTH = 178;
    /** 面板高度 */
    private static final int PANEL_HEIGHT = 320;
    /** 交易列表每行的列数 */
    private static final int TRADE_GRID_COLUMNS = 9;
    /** 交易图标尺寸 */
    private static final int ICON_SIZE = 18;

    // ==================== 同步值字段 ====================

    /** 当前标签页 ID（C2S：客户端切换标签时发送到服务端） */
    private IntSyncValue currentTabSync;
    /** 搜索文本（C2S：客户端输入时发送到服务端） */
    private StringSyncValue searchTextSync;
    /** 排序模式（C2S：0=按orderId, 1=按名称） */
    private IntSyncValue sortModeSync;
    /** 交易请求（C2S：Shift+Click 时发送 "groupId:tradeIndex"） */
    private StringSyncValue tradeRequestSync;
    /** 交易结果消息（S2C：服务端处理完交易后发送结果文本） */
    private StringSyncValue tradeResultSync;
    /** BQ 锁定状态字符串（S2C：服务端构建 "groupId:true/false,..." 发送到客户端） */
    private StringSyncValue bqLockStatusSync;
    /** 冷却状态字符串（S2C：服务端构建 "groupId:tradeIndex:seconds,..." 发送到客户端） */
    private StringSyncValue cooldownStatusSync;
    /** 弹出所有猫猫币（C2S：按钮点击时发送） */
    private BooleanSyncValue ejectAllCoinsSync;
    /** 导入猫猫币（C2S：按钮点击时发送） */
    private BooleanSyncValue importCoinsSync;
    /** 交易列表动态同步处理器（通过 notifyUpdate 触发 widget 重建） */
    private DynamicSyncHandler tradeListHandler;
    /** 各货币余额同步值映射 */
    private final Map<String, IntSyncValue> coinAmountSyncs = new HashMap<>();

    // ==================== UI 状态镜像（客户端+服务端共享） ====================

    /** 当前标签页 ID（默认 1=猫猫币） */
    private int currentTabId = 1;
    /** 搜索文本 */
    private String searchText = "";
    /** 排序模式：0=按orderId, 1=按名称 */
    private int sortMode = 0;

    // ==================== 客户端状态（S2C 同步填充） ====================

    /** BQ 锁定状态映射：groupId → 是否锁定（true=锁定） */
    private final Map<UUID, Boolean> bqLockStatusMap = new HashMap<>();
    /** 冷却状态映射："groupId:tradeIndex" → 剩余秒数 */
    private final Map<String, Long> cooldownStatusMap = new HashMap<>();

    // ==================== 服务端状态 ====================

    /** 弹出所有猫猫币标志（服务端处理后重置为 false） */
    private boolean nekoEjectAllCoins = false;
    /** 导入猫猫币标志（服务端处理后重置为 false） */
    private boolean nekoImportCoins = false;
    /** 弹出单种猫猫币标志映射 */
    private final Map<String, Boolean> nekoEjectSingleCoin = new HashMap<>();
    /** 交易结果消息（服务端设置，通过 tradeResultSync 同步到客户端） */
    private String tradeResultMessage = "";

    // ==================== 其他字段 ====================

    /** GUI 位置数据引用（build 时设置，供同步值 getter 使用） */
    private PosGuiData guiData;
    /** 当前正在播放的 BGM 声音实例（仅客户端） */
    private static ISound currentBgmSound = null;

    // ==================== 构造器 ====================

    /**
     * 构造猫猫售货机 V2 GUI
     *
     * @param machine 关联的猫猫售货机 V2 机器实例
     */
    public NekoVMGuiV2(MTENekoVendingMachineV2 machine) {
        super(machine);
        // 初始化各货币的弹出标志
        for (String currencyId : NekoCurrencyRegistrar.getNekoCurrencyIds()) {
            nekoEjectSingleCoin.put(currencyId, false);
        }
    }

    // ==================== build 方法 ====================

    /**
     * 构建 GUI 面板
     * <p>
     * 不调用 super.build()，而是手动调用 registerSyncValues() 注册同步值，
     * 然后使用 GTBaseGuiBuilder 构建 178x320 的面板，
     * 手动添加标签列、主列和 IO 列。
     * <p>
     * 这样做的原因：super.build() 会创建标准的 GT5U 布局（终端行+背包行），
     * 不适合猫猫售货机的自定义布局需求。
     *
     * @param guiData     GUI 位置数据
     * @param syncManager 面板同步管理器
     * @param uiSettings  UI 设置
     * @return ModularPanel 实例
     */
    @Override
    public ModularPanel build(PosGuiData guiData, PanelSyncManager syncManager, UISettings uiSettings) {
        this.guiData = guiData;
        // 注册所有同步值（包含 GT5U 标准 + 猫猫机自定义）
        registerSyncValues(syncManager);

        // 使用 GTBaseGuiBuilder 构建面板，禁用标题、覆盖层标签和 GT Logo
        ModularPanel panel = new GTBaseGuiBuilder(multiblock, guiData, syncManager, uiSettings).setWidth(PANEL_WIDTH)
            .setHeight(PANEL_HEIGHT)
            .doesBindPlayerInventory(false)
            .doesAddTitle(false)
            .doesAddCoverTabs(false)
            .doesAddGregTechLogo(false)
            .build();

        // 客户端：启动 BGM 并注册关闭回调
        if (syncManager.isClient()) {
            startBgm();
            panel.onCloseAction(() -> stopBgm());
        }

        // 左侧标签列
        panel.child(createTabColumn());

        // 主内容列
        panel.child(createMainColumn(syncManager));

        // 右侧 IO 列
        panel.child(createIOColumn());

        return panel;
    }

    // ==================== 同步值注册 ====================

    /**
     * 注册同步值
     * <p>
     * 先调用 super.registerSyncValues() 注册 GT5U 标准同步值
     * （机器状态、维护信息、电源开关、结构更新等），
     * 然后注册猫猫机 V2 的自定义同步值。
     *
     * @param syncManager 面板同步管理器
     */
    @Override
    protected void registerSyncValues(PanelSyncManager syncManager) {
        // 注册 GT5U 标准同步值
        super.registerSyncValues(syncManager);

        // 获取玩家 UUID（服务端用于查询钱包余额、BQ 状态等）
        final UUID playerId = getPlayerId();

        // --- 当前标签页（C2S）---
        currentTabSync = new IntSyncValue(() -> currentTabId, val -> {
            currentTabId = val;
            // 标签切换时重建交易列表
            notifyTradeListUpdate();
        });
        currentTabSync.allowC2S();
        syncManager.syncValue("nekoV2CurrentTab", currentTabSync);

        // --- 搜索文本（C2S）---
        searchTextSync = new StringSyncValue(() -> searchText, val -> {
            searchText = val == null ? "" : val;
            // 搜索文本变化时重建交易列表
            notifyTradeListUpdate();
        });
        searchTextSync.allowC2S();
        syncManager.syncValue("nekoV2SearchText", searchTextSync);

        // --- 排序模式（C2S）---
        sortModeSync = new IntSyncValue(() -> sortMode, val -> {
            sortMode = val;
            // 排序模式变化时重建交易列表
            notifyTradeListUpdate();
        });
        sortModeSync.allowC2S();
        syncManager.syncValue("nekoV2SortMode", sortModeSync);

        // --- 交易请求（C2S，Shift+Click 触发）---
        tradeRequestSync = new StringSyncValue(() -> "", val -> {
            if (val != null && !val.isEmpty()) {
                processTradeRequest(val, playerId);
            }
        });
        tradeRequestSync.allowC2S();
        syncManager.syncValue("nekoV2TradeRequest", tradeRequestSync);

        // --- 交易结果消息（S2C）---
        tradeResultSync = new StringSyncValue(
            () -> tradeResultMessage,
            val -> tradeResultMessage = val == null ? "" : val);
        syncManager.syncValue("nekoV2TradeResult", tradeResultSync);

        // --- BQ 锁定状态（S2C）---
        bqLockStatusSync = new StringSyncValue(() -> buildBqLockStatusString(playerId), val -> {
            parseBqLockStatus(val);
            // BQ 状态更新后重建交易列表
            notifyTradeListUpdate();
        });
        syncManager.syncValue("nekoV2BqLockStatus", bqLockStatusSync);

        // --- 冷却状态（S2C）---
        cooldownStatusSync = new StringSyncValue(() -> buildCooldownStatusString(playerId), val -> {
            parseCooldownStatus(val);
            // 冷却状态更新后重建交易列表
            notifyTradeListUpdate();
        });
        syncManager.syncValue("nekoV2CooldownStatus", cooldownStatusSync);

        // --- 各货币余额（S2C）---
        for (String currencyId : NekoCurrencyRegistrar.getNekoCurrencyIds()) {
            final String cid = currencyId;
            IntSyncValue coinAmountSyncer = new IntSyncValue(() -> {
                // 仅服务端查询实际余额，客户端通过同步获取
                if (syncManager.isClient() || playerId == null) return 0;
                NekoWallet wallet = NekoWalletManager.INSTANCE.getWallet(playerId);
                return wallet == null ? 0 : wallet.getCount(cid);
            });
            syncManager.syncValue("nekoV2CoinAmount_" + currencyId, coinAmountSyncer);
            coinAmountSyncs.put(currencyId, coinAmountSyncer);
        }

        // --- 弹出单种猫猫币（C2S）---
        for (String currencyId : NekoCurrencyRegistrar.getNekoCurrencyIds()) {
            final String cid = currencyId;
            BooleanSyncValue ejectCoinSyncer = new BooleanSyncValue(
                () -> nekoEjectSingleCoin.getOrDefault(cid, false),
                val -> {
                    nekoEjectSingleCoin.put(cid, val);
                    if (val) {
                        doNekoEjectCoin(cid, playerId);
                    }
                });
            ejectCoinSyncer.allowC2S();
            syncManager.syncValue("nekoV2EjectCoin_" + currencyId, ejectCoinSyncer);
        }

        // --- 弹出所有猫猫币（C2S）---
        ejectAllCoinsSync = new BooleanSyncValue(() -> nekoEjectAllCoins, val -> {
            nekoEjectAllCoins = val;
            if (val) {
                doNekoEjectAllCoins(playerId);
            }
        });
        ejectAllCoinsSync.allowC2S();
        syncManager.syncValue("nekoV2EjectAllCoins", ejectAllCoinsSync);

        // --- 导入猫猫币（C2S）---
        importCoinsSync = new BooleanSyncValue(() -> nekoImportCoins, val -> {
            nekoImportCoins = val;
            if (val) {
                doNekoImportCoins(playerId);
            }
        });
        importCoinsSync.allowC2S();
        syncManager.syncValue("nekoV2ImportCoins", importCoinsSync);

        // --- 交易列表动态同步处理器 ---
        // widgetProvider 在客户端被调用，构建交易图标网格
        tradeListHandler = new DynamicSyncHandler().widgetProvider((sm, packet) -> buildTradeListContent());
    }

    /**
     * 通知交易列表重建
     * <p>
     * 当标签页、搜索文本、排序模式、BQ 状态或冷却状态变化时调用，
     * 触发 DynamicSyncHandler 的 widgetProvider 重新执行。
     */
    private void notifyTradeListUpdate() {
        if (tradeListHandler != null) {
            tradeListHandler.notifyUpdate(packet -> {});
        }
    }

    // ==================== UI 组件创建 ====================

    /**
     * 创建左侧标签列
     * <p>
     * 从交易数据库获取所有已使用的 tabId，按升序排列，
     * 为每个 tabId 创建一个带物品图标的按钮。
     * <p>
     * 标签图标：
     * <ul>
     * <li>Tab 1：猫猫币物品</li>
     * <li>Tab 2：闪烁猫猫币物品</li>
     * <li>Tab 3+：默认物品</li>
     * </ul>
     *
     * @return 标签列 Widget
     */
    private IWidget createTabColumn() {
        Flow tabColumn = Flow.column()
            .coverChildren()
            .left(-29)
            .top(40)
            .childPadding(2);

        // 获取所有已使用的 tabId，按升序排列
        Set<Integer> tabIds = new TreeSet<>();
        tabIds.addAll(NekoTradeDatabase.INSTANCE.getUsedTabIds());
        // 确保默认标签页存在
        tabIds.add(1);
        tabIds.add(2);
        tabIds.add(3);

        for (int tabId : tabIds) {
            final int tid = tabId;
            ItemStack icon = getTabIcon(tabId);
            String name = getTabName(tabId);

            // 创建标签按钮，使用物品图标作为 overlay
            ButtonWidget<?> tabButton = new ButtonWidget<>().size(ICON_SIZE, ICON_SIZE)
                .overlay(icon != null ? new ItemDrawable(icon) : IKey.str(String.valueOf(tabId)))
                .onMousePressed(btn -> {
                    // 切换标签页
                    currentTabSync.setValue(tid);
                    return true;
                })
                .tooltipBuilder(t -> t.addLine(IKey.str(name)));

            tabColumn.child(tabButton);
        }

        return tabColumn;
    }

    /**
     * 创建主内容列
     * <p>
     * 布局从上到下：
     * <ol>
     * <li>标题文字 "猫猫售货机"</li>
     * <li>搜索栏 + 排序按钮</li>
     * <li>交易列表（DynamicSyncedWidget，动态构建）</li>
     * <li>猫猫币余额显示行</li>
     * <li>交易结果消息</li>
     * <li>玩家背包栏</li>
     * </ol>
     *
     * @param syncManager 面板同步管理器
     * @return 主列 Widget
     */
    private IWidget createMainColumn(PanelSyncManager syncManager) {
        Flow mainColumn = Flow.column()
            .padding(4)
            .width(PANEL_WIDTH - 8);

        // --- 标题 ---
        mainColumn.child(
            IKey.str(EnumChatFormatting.DARK_GRAY + "猫猫售货机")
                .asWidget()
                .height(12)
                .fullWidth()
                .marginBottom(2));

        // --- 搜索栏 + 排序按钮 ---
        Flow searchRow = Flow.row()
            .width(PANEL_WIDTH - 12)
            .height(14)
            .marginBottom(2);

        // 搜索文本框
        TextFieldWidget searchField = new TextFieldWidget().value(searchTextSync)
            .hintText("搜索...")
            .autoUpdateOnChange(true)
            .size(PANEL_WIDTH - 34, 14);
        searchRow.child(searchField);

        // 排序切换按钮
        ButtonWidget<?> sortButton = new ButtonWidget<>().size(14, 14)
            .overlay(IKey.dynamic(() -> sortMode == 0 ? "O" : "A"))
            .onMousePressed(btn -> {
                // 切换排序模式：0=orderId, 1=名称
                sortModeSync.setValue(sortMode == 0 ? 1 : 0);
                return true;
            })
            .tooltipBuilder(t -> {
                t.addLine(IKey.str(sortMode == 0 ? "当前: 按序号排序" : "当前: 按名称排序"));
                t.addLine(IKey.str("点击切换排序模式"));
            });
        searchRow.child(sortButton);

        mainColumn.child(searchRow);

        // --- 交易列表（DynamicSyncedWidget 动态构建）---
        mainColumn.child(
            new DynamicSyncedWidget<>().widthRel(1f)
                .height(150)
                .syncHandler(tradeListHandler));

        // --- 猫猫币余额显示行 ---
        mainColumn.child(createCoinDisplayRow());

        // --- 交易结果消息 ---
        mainColumn.child(
            IKey.dynamic(() -> tradeResultMessage.isEmpty() ? "" : EnumChatFormatting.YELLOW + tradeResultMessage)
                .asWidget()
                .height(12)
                .fullWidth()
                .marginBottom(2));

        // --- 玩家背包栏 ---
        mainColumn.child(
            SlotGroupWidget.playerInventory(false)
                .marginLeft(4));

        return mainColumn;
    }

    /**
     * 创建猫猫币余额显示行
     * <p>
     * 为每种猫猫币创建一个图标 + 余额文字的组合，
     * 余额通过 IKey.dynamic 动态更新（依赖 coinAmountSyncs 的值）。
     *
     * @return 猫猫币显示行 Widget
     */
    private IWidget createCoinDisplayRow() {
        Flow row = Flow.row()
            .height(ICON_SIZE + 4)
            .fullWidth()
            .marginBottom(2);

        for (String currencyId : NekoCurrencyRegistrar.getNekoCurrencyIds()) {
            final String cid = currencyId;
            ItemStack coinIcon = NekoCurrencyRegistrar.getItemStack(cid, 1);
            String displayName = NekoCurrencyRegistrar.getDisplayName(cid);
            IntSyncValue amountSync = coinAmountSyncs.get(cid);

            // 货币图标
            if (coinIcon != null) {
                row.child(
                    new ItemDisplayWidget().item(coinIcon)
                        .size(ICON_SIZE, ICON_SIZE)
                        .tooltipBuilder(t -> t.addLine(IKey.str(displayName))));
            }

            // 余额数字（动态更新）
            row.child(IKey.dynamic(() -> {
                int amount = amountSync != null ? amountSync.getIntValue() : 0;
                return EnumChatFormatting.WHITE + String.valueOf(amount);
            })
                .asWidget()
                .height(ICON_SIZE)
                .marginLeft(2)
                .marginRight(8)
                .verticalCenter());
        }

        return row;
    }

    /**
     * 创建右侧 IO 列
     * <p>
     * 包含以下按钮：
     * <ol>
     * <li>导入猫猫币按钮（从输入总线导入猫猫币到钱包）</li>
     * <li>弹出所有猫猫币按钮（将钱包中的猫猫币弹出至机器旁）</li>
     * <li>电源开关按钮（GT5U 标准）</li>
     * <li>结构更新按钮（GT5U 标准）</li>
     * </ol>
     *
     * @return IO 列 Widget
     */
    private IWidget createIOColumn() {
        ParentWidget<?> ioColumn = new ParentWidget<>().size(50, 214)
            .right(-48)
            .top(40)
            .background(GTGuiTextures.BACKGROUND_STANDARD);

        // 导入猫猫币按钮
        ButtonWidget<?> importButton = new ButtonWidget<>().size(18, 18)
            .pos(4, 4)
            .overlay(IKey.str("入"))
            .onMousePressed(btn -> {
                importCoinsSync.setValue(true);
                return true;
            })
            .tooltipBuilder(t -> {
                t.addLine(IKey.str("导入猫猫币"));
                t.addLine(IKey.str("从输入总线提取猫猫币到钱包"));
            });
        ioColumn.child(importButton);

        // 弹出所有猫猫币按钮
        ButtonWidget<?> ejectAllButton = new ButtonWidget<>().size(18, 18)
            .pos(4, 26)
            .overlay(IKey.str("出"))
            .onMousePressed(btn -> {
                ejectAllCoinsSync.setValue(true);
                return true;
            })
            .tooltipBuilder(t -> {
                t.addLine(IKey.str("弹出所有猫猫币"));
                t.addLine(IKey.str("将钱包中的猫猫币弹出至机器旁"));
            });
        ioColumn.child(ejectAllButton);

        // 电源开关按钮（GT5U 标准方法）
        ToggleButton powerSwitch = createPowerSwitchButton();
        powerSwitch.pos(4, 48);
        ioColumn.child(powerSwitch);

        // 结构更新按钮（GT5U 标准方法，返回 IWidget 实际为 ToggleButton）
        IWidget structureUpdate = createStructureUpdateButton(null);
        if (structureUpdate != null) {
            ((ToggleButton) structureUpdate).pos(4, 70);
            ioColumn.child(structureUpdate);
        }

        return ioColumn;
    }

    // ==================== 交易列表构建 ====================

    /**
     * 构建交易列表内容
     * <p>
     * 由 DynamicSyncHandler.widgetProvider 调用，在客户端执行。
     * 根据当前标签页、搜索文本和排序模式，
     * 从交易数据库获取交易数据并构建交易图标网格。
     * <p>
     * 每个交易图标使用 {@link NekoTradeDisplayWidgetV2}，
     * 配置 BQ 锁定状态、可交易状态、冷却时间和交易请求同步值。
     *
     * @return 交易列表 Widget
     */
    private IWidget buildTradeListContent() {
        try {
            // 获取过滤和排序后的交易条目列表
            List<TradeEntry> entries = getFilteredAndSortedTrades();

            if (entries.isEmpty()) {
                // 无交易时显示提示文字
                return IKey.str(EnumChatFormatting.GRAY + "无可用交易")
                    .asWidget()
                    .fullWidth()
                    .marginBottom(4);
            }

            // 构建交易图标网格
            Flow container = Flow.column()
                .coverChildren()
                .childPadding(1);

            int col = 0;
            Flow row = Flow.row()
                .coverChildren()
                .childPadding(1);

            for (TradeEntry entry : entries) {
                // 创建交易显示 Widget
                NekoTradeDisplayWidgetV2 widget = new NekoTradeDisplayWidgetV2(entry.group, entry.tradeIndex);
                widget.size(ICON_SIZE, ICON_SIZE);

                // 设置 BQ 锁定状态
                boolean bqLocked = bqLockStatusMap.getOrDefault(entry.group.getId(), false);
                widget.setBqLocked(bqLocked);

                // 设置冷却时间
                String cdKey = entry.group.getId()
                    .toString() + ":" + entry.tradeIndex;
                long cooldown = cooldownStatusMap.getOrDefault(cdKey, 0L);
                widget.setCooldownRemaining(cooldown);

                // 设置可交易状态（非锁定且非冷却中）
                widget.setTradeable(!bqLocked && cooldown <= 0);

                // 设置交易请求同步值（Shift+Click 时发送）
                widget.setTradeRequestSync(tradeRequestSync);

                row.child(widget);
                col++;

                // 每 TRADE_GRID_COLUMNS 个换行
                if (col >= TRADE_GRID_COLUMNS) {
                    container.child(row);
                    row = Flow.row()
                        .coverChildren()
                        .childPadding(1);
                    col = 0;
                }
            }

            // 添加最后一行（如果有剩余）
            if (col > 0) {
                container.child(row);
            }

            return container;
        } catch (Throwable t) {
            GTInterestingThing.LOG.error("[NekoVMV2] buildTradeListContent() 异常!", t);
            return IKey.str(EnumChatFormatting.RED + "交易列表加载失败")
                .asWidget()
                .fullWidth();
        }
    }

    /**
     * 获取过滤和排序后的交易条目列表
     * <p>
     * 流程：
     * <ol>
     * <li>从交易数据库获取当前标签页的所有交易组</li>
     * <li>遍历每个交易组的所有交易</li>
     * <li>按搜索文本过滤（匹配产物名、需求物品名、货币名称）</li>
     * <li>按排序模式排序（orderId 或产物名）</li>
     * </ol>
     *
     * @return 过滤和排序后的交易条目列表
     */
    private List<TradeEntry> getFilteredAndSortedTrades() {
        List<TradeEntry> entries = new ArrayList<>();

        // 获取当前标签页的交易组列表
        List<NekoTradeGroup> groups = NekoTradeDatabase.INSTANCE.getTradeGroupsByTab(currentTabId);

        for (NekoTradeGroup group : groups) {
            List<NekoTrade> trades = group.getTrades();
            for (int i = 0; i < trades.size(); i++) {
                NekoTrade trade = trades.get(i);

                // 搜索过滤
                if (!searchText.isEmpty() && !matchesSearch(trade, searchText)) {
                    continue;
                }

                entries.add(new TradeEntry(group, i));
            }
        }

        // 排序
        if (sortMode == 0) {
            // 按 orderId 排序（BQ 锁定排在后面）
            entries.sort(Comparator.comparingInt(e -> e.group.getOrderId()));
        } else {
            // 按产物名排序
            entries.sort((a, b) -> {
                String nameA = getTradeDisplayName(a);
                String nameB = getTradeDisplayName(b);
                return nameA.compareToIgnoreCase(nameB);
            });
        }

        return entries;
    }

    /**
     * 检查交易是否匹配搜索文本
     * <p>
     * 匹配范围：产物名、需求物品名、猫猫币名称
     *
     * @param trade      交易
     * @param searchText 搜索文本（已转为小写）
     * @return 匹配返回 true
     */
    private boolean matchesSearch(NekoTrade trade, String searchText) {
        String searchLower = searchText.toLowerCase();

        // 检查产物名
        for (NekoBigItemStack toItem : trade.getToItems()) {
            if (toItem.getBaseStack() != null) {
                String name = toItem.getBaseStack()
                    .getDisplayName();
                if (name != null && name.toLowerCase()
                    .contains(searchLower)) {
                    return true;
                }
            }
        }

        // 检查需求物品名
        for (NekoBigItemStack fromItem : trade.getFromItems()) {
            if (fromItem.getBaseStack() != null) {
                String name = fromItem.getBaseStack()
                    .getDisplayName();
                if (name != null && name.toLowerCase()
                    .contains(searchLower)) {
                    return true;
                }
            }
        }

        // 检查猫猫币名称
        if (trade.hasCurrencyCost()) {
            String currencyName = NekoCurrencyRegistrar.getDisplayName(trade.getCurrencyId());
            if (currencyName != null && currencyName.toLowerCase()
                .contains(searchLower)) {
                return true;
            }
        }

        return false;
    }

    /**
     * 获取交易的显示名称（用于排序）
     *
     * @param entry 交易条目
     * @return 显示名称，无法获取时返回空字符串
     */
    private String getTradeDisplayName(TradeEntry entry) {
        try {
            List<NekoTrade> trades = entry.group.getTrades();
            if (entry.tradeIndex >= 0 && entry.tradeIndex < trades.size()) {
                NekoTrade trade = trades.get(entry.tradeIndex);
                NekoBigItemStack displayItem = trade.getDisplayItem();
                if (displayItem != null && displayItem.getBaseStack() != null) {
                    return displayItem.getBaseStack()
                        .getDisplayName();
                }
            }
        } catch (Exception ignored) {}
        return "";
    }

    // ==================== 猫猫币操作 ====================

    /**
     * 弹出单种猫猫币
     * <p>
     * 从钱包中取出指定货币的全部余额，
     * 以 64 个为一组生成物品栈，在机器旁弹出。
     * 弹出后重置钱包余额为 0。
     *
     * @param currencyId 货币 ID
     * @param playerId   玩家 UUID
     */
    private void doNekoEjectCoin(String currencyId, UUID playerId) {
        try {
            if (playerId == null) {
                nekoEjectSingleCoin.put(currencyId, false);
                return;
            }
            NekoWallet wallet = NekoWalletManager.INSTANCE.getWallet(playerId);
            if (wallet == null || wallet.getCount(currencyId) <= 0) {
                nekoEjectSingleCoin.put(currencyId, false);
                return;
            }

            int count = wallet.getCount(currencyId);
            while (count > 0) {
                int stackSize = Math.min(count, 64);
                ItemStack stack = NekoCurrencyRegistrar.getItemStack(currencyId, stackSize);
                if (stack != null) {
                    dropItemsNearMachine(stack);
                }
                count -= stackSize;
            }
            wallet.resetCount(currencyId);
            NekoWalletManager.INSTANCE.saveWallet(playerId);
        } catch (Throwable t) {
            GTInterestingThing.LOG.error("[NekoVMV2] doNekoEjectCoin 异常!", t);
        } finally {
            nekoEjectSingleCoin.put(currencyId, false);
        }
    }

    /**
     * 弹出所有猫猫币
     * <p>
     * 遍历所有猫猫币类型，依次弹出每种货币的全部余额。
     *
     * @param playerId 玩家 UUID
     */
    private void doNekoEjectAllCoins(UUID playerId) {
        try {
            if (playerId == null) {
                nekoEjectAllCoins = false;
                return;
            }
            NekoWallet wallet = NekoWalletManager.INSTANCE.getWallet(playerId);
            if (wallet == null) {
                nekoEjectAllCoins = false;
                return;
            }

            for (String currencyId : NekoCurrencyRegistrar.getNekoCurrencyIds()) {
                int count = wallet.getCount(currencyId);
                while (count > 0) {
                    int stackSize = Math.min(count, 64);
                    ItemStack stack = NekoCurrencyRegistrar.getItemStack(currencyId, stackSize);
                    if (stack != null) {
                        dropItemsNearMachine(stack);
                    }
                    count -= stackSize;
                }
                wallet.resetCount(currencyId);
            }
            NekoWalletManager.INSTANCE.saveWallet(playerId);
        } catch (Throwable t) {
            GTInterestingThing.LOG.error("[NekoVMV2] doNekoEjectAllCoins 异常!", t);
        } finally {
            nekoEjectAllCoins = false;
        }
    }

    /**
     * 导入猫猫币
     * <p>
     * 扫描所有输入总线（mInputBusses）中的物品，
     * 将猫猫币物品导入到玩家钱包，并从总线中移除。
     * <p>
     * V2 使用总线 I/O 系统（而非直接 GUI 输入槽），
     * 玩家将猫猫币放入输入总线后，点击"导入"按钮即可将其存入钱包。
     *
     * @param playerId 玩家 UUID
     */
    private void doNekoImportCoins(UUID playerId) {
        try {
            if (playerId == null) {
                nekoImportCoins = false;
                return;
            }
            NekoWallet wallet = NekoWalletManager.INSTANCE.getWallet(playerId);
            if (wallet == null) {
                nekoImportCoins = false;
                return;
            }

            // 遍历所有输入总线，查找猫猫币物品
            int totalImported = 0;
            for (MTEHatchInputBus bus : multiblock.mInputBusses) {
                if (bus == null || bus.getBaseMetaTileEntity() == null) {
                    continue;
                }
                ItemStack[] inv = bus.mInventory;
                for (int s = 0; s < inv.length; s++) {
                    if (inv[s] == null) {
                        continue;
                    }
                    String currencyId = NekoCurrencyRegistrar.getNekoCurrencyId(inv[s]);
                    if (currencyId != null) {
                        // 是猫猫币，导入到钱包
                        wallet.addCount(currencyId, inv[s].stackSize);
                        totalImported += inv[s].stackSize;
                        inv[s] = null; // 从总线中移除
                    }
                }
            }

            if (totalImported > 0) {
                NekoWalletManager.INSTANCE.saveWallet(playerId);
                tradeResultMessage = "成功导入 " + totalImported + " 个猫猫币";
            } else {
                tradeResultMessage = "输入总线中未找到猫猫币";
            }
        } catch (Throwable t) {
            GTInterestingThing.LOG.error("[NekoVMV2] doNekoImportCoins 异常!", t);
            tradeResultMessage = "导入猫猫币失败";
        } finally {
            nekoImportCoins = false;
        }
    }

    /**
     * 在机器旁弹出物品
     * <p>
     * 创建 EntityItem 并在机器位置生成，
     * 给予随机速度使物品向四周散开。
     *
     * @param stack 要弹出的物品栈
     */
    private void dropItemsNearMachine(ItemStack stack) {
        if (stack == null || baseMetaTileEntity == null) return;
        World world = baseMetaTileEntity.getWorld();
        if (world == null || world.isRemote) return;

        double x = baseMetaTileEntity.getXCoord() + 0.5;
        double y = baseMetaTileEntity.getYCoord() + 0.5;
        double z = baseMetaTileEntity.getZCoord() + 0.5;

        EntityItem entity = new EntityItem(world, x, y, z, stack);
        entity.motionX = world.rand.nextDouble() * 0.2 - 0.1;
        entity.motionY = 0.2;
        entity.motionZ = world.rand.nextDouble() * 0.2 - 0.1;
        world.spawnEntityInWorld(entity);
    }

    // ==================== 交易请求处理 ====================

    /**
     * 处理交易请求
     * <p>
     * 解析 "groupId:tradeIndex" 格式的请求字符串，
     * 调用机器的 processTrade 方法执行交易，
     * 根据结果更新交易结果消息。
     *
     * @param request  请求字符串 "groupId:tradeIndex"
     * @param playerId 玩家 UUID
     */
    private void processTradeRequest(String request, UUID playerId) {
        try {
            if (playerId == null) {
                tradeResultMessage = "无法确定玩家身份";
                return;
            }

            // 解析请求字符串
            String[] parts = request.split(":");
            if (parts.length != 2) {
                tradeResultMessage = "交易请求格式错误";
                return;
            }

            UUID groupId;
            int tradeIndex;
            try {
                groupId = UUID.fromString(parts[0]);
                tradeIndex = Integer.parseInt(parts[1]);
            } catch (Exception e) {
                tradeResultMessage = "交易请求参数解析失败";
                return;
            }

            // 调用机器执行交易
            NekoTradeResult result = multiblock.processTrade(playerId, groupId, tradeIndex);

            // 根据结果设置消息
            if (result.isSuccess()) {
                tradeResultMessage = EnumChatFormatting.GREEN + "交易成功!";
            } else {
                tradeResultMessage = EnumChatFormatting.RED + getTradeResultMessage(result);
            }
        } catch (Throwable t) {
            GTInterestingThing.LOG.error("[NekoVMV2] processTradeRequest 异常!", t);
            tradeResultMessage = "交易处理异常";
        }
    }

    /**
     * 获取交易结果的本地化消息
     *
     * @param result 交易结果
     * @return 消息文本
     */
    private String getTradeResultMessage(NekoTradeResult result) {
        switch (result.getStatus()) {
            case SUCCESS:
                return "交易成功";
            case TRADE_GROUP_NOT_FOUND:
                return "交易组不存在";
            case TRADE_INDEX_OUT_OF_BOUNDS:
                return "交易索引越界";
            case ON_COOLDOWN:
                return "冷却中，请稍后再试";
            case MAX_TRADES_REACHED:
                return "已达到最大交易次数";
            case CONDITION_NOT_SATISFIED:
                return "前置条件未满足";
            case INSUFFICIENT_CURRENCY:
                return "猫猫币不足";
            case INSUFFICIENT_ITEMS:
                return "输入物品不足";
            case OUTPUT_FULL:
                return "输出总线已满";
            default:
                return result.getMessage() != null ? result.getMessage() : "交易失败";
        }
    }

    // ==================== BQ 锁定状态同步 ====================

    /**
     * 构建 BQ 锁定状态字符串（服务端）
     * <p>
     * 遍历当前标签页的所有交易组，
     * 检查每个组的前置条件是否满足，
     * 构建 "groupId:true/false,..." 格式的字符串。
     *
     * @param playerId 玩家 UUID
     * @return BQ 锁定状态字符串
     */
    private String buildBqLockStatusString(UUID playerId) {
        if (playerId == null) return "";
        StringBuilder sb = new StringBuilder();
        List<NekoTradeGroup> groups = NekoTradeDatabase.INSTANCE.getTradeGroupsByTab(currentTabId);
        for (NekoTradeGroup group : groups) {
            if (sb.length() > 0) sb.append(",");
            sb.append(
                group.getId()
                    .toString())
                .append(":")
                .append(!group.isConditionsSatisfied(playerId));
        }
        return sb.toString();
    }

    /**
     * 解析 BQ 锁定状态字符串（客户端）
     * <p>
     * 将 "groupId:true/false,..." 解析为 bqLockStatusMap。
     * true 表示锁定（前置条件未满足）。
     *
     * @param status BQ 锁定状态字符串
     */
    private void parseBqLockStatus(String status) {
        bqLockStatusMap.clear();
        if (status == null || status.isEmpty()) return;
        String[] entries = status.split(",");
        for (String entry : entries) {
            String[] parts = entry.split(":");
            if (parts.length == 2) {
                try {
                    UUID groupId = UUID.fromString(parts[0]);
                    boolean locked = Boolean.parseBoolean(parts[1]);
                    bqLockStatusMap.put(groupId, locked);
                } catch (Exception ignored) {}
            }
        }
    }

    // ==================== 冷却状态同步 ====================

    /**
     * 构建冷却状态字符串（服务端）
     * <p>
     * 遍历当前标签页的所有交易组，
     * 获取每个组内每笔交易的冷却剩余时间，
     * 构建 "groupId:tradeIndex:seconds,..." 格式的字符串。
     *
     * @param playerId 玩家 UUID
     * @return 冷却状态字符串
     */
    private String buildCooldownStatusString(UUID playerId) {
        if (playerId == null) return "";
        StringBuilder sb = new StringBuilder();
        List<NekoTradeGroup> groups = NekoTradeDatabase.INSTANCE.getTradeGroupsByTab(currentTabId);
        for (NekoTradeGroup group : groups) {
            int cooldown = group.getCooldown();
            if (cooldown <= 0) continue; // 无冷却的交易组跳过

            NekoTradeHistory history = NekoHistoryManager.INSTANCE.getHistory(playerId, group.getId());
            long remaining = history.getCooldownRemaining(cooldown);

            if (remaining > 0) {
                // 为组内每笔交易记录冷却（组级别冷却，所有交易共享）
                for (int i = 0; i < group.getTrades()
                    .size(); i++) {
                    if (sb.length() > 0) sb.append(",");
                    sb.append(
                        group.getId()
                            .toString())
                        .append(":")
                        .append(i)
                        .append(":")
                        .append(remaining);
                }
            }
        }
        return sb.toString();
    }

    /**
     * 解析冷却状态字符串（客户端）
     * <p>
     * 将 "groupId:tradeIndex:seconds,..." 解析为 cooldownStatusMap。
     *
     * @param status 冷却状态字符串
     */
    private void parseCooldownStatus(String status) {
        cooldownStatusMap.clear();
        if (status == null || status.isEmpty()) return;
        String[] entries = status.split(",");
        for (String entry : entries) {
            String[] parts = entry.split(":");
            if (parts.length == 3) {
                try {
                    String key = parts[0] + ":" + parts[1];
                    long seconds = Long.parseLong(parts[2]);
                    cooldownStatusMap.put(key, seconds);
                } catch (Exception ignored) {}
            }
        }
    }

    // ==================== BGM 播放 ====================

    /**
     * 启动 BGM
     * <p>
     * 使用 MC 原生音频系统播放猫猫售货机 BGM，
     * 独立于 VM 的音乐管理器。
     * 防止叠加播放：先停止已有 BGM 再播放新的。
     */
    private void startBgm() {
        try {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc == null || mc.getSoundHandler() == null) return;

            // 停止已有 BGM（防止叠加）
            if (currentBgmSound != null) {
                mc.getSoundHandler()
                    .stopSound(currentBgmSound);
                currentBgmSound = null;
            }

            // 播放新 BGM
            currentBgmSound = PositionedSoundRecord.func_147673_a(NEKO_BGM);
            mc.getSoundHandler()
                .playSound(currentBgmSound);
        } catch (Throwable t) {
            GTInterestingThing.LOG.warn("[NekoVMV2] 启动 BGM 失败: {}", t.getMessage());
        }
    }

    /**
     * 停止 BGM
     * <p>
     * 停止当前正在播放的 BGM，清理引用。
     */
    private void stopBgm() {
        try {
            if (currentBgmSound != null) {
                Minecraft mc = Minecraft.getMinecraft();
                if (mc != null && mc.getSoundHandler() != null) {
                    mc.getSoundHandler()
                        .stopSound(currentBgmSound);
                }
                currentBgmSound = null;
            }
        } catch (Throwable t) {
            GTInterestingThing.LOG.warn("[NekoVMV2] 停止 BGM 失败: {}", t.getMessage());
        }
    }

    // ==================== 辅助方法 ====================

    /**
     * 获取玩家 UUID
     * <p>
     * 从 guiData 获取打开 GUI 的玩家 UUID。
     *
     * @return 玩家 UUID，无法获取时返回 null
     */
    private UUID getPlayerId() {
        if (guiData == null) return null;
        try {
            EntityPlayer player = guiData.getPlayer();
            return player != null ? player.getUniqueID() : null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 获取标签页名称
     * <p>
     * 硬编码标签页名称，不依赖 NekoPageRegistry（避免 VM 依赖）。
     * <ul>
     * <li>Tab 1: 猫猫币</li>
     * <li>Tab 2: 闪烁猫猫币</li>
     * <li>Tab 3: 其他</li>
     * <li>Tab 4+: 自定义 #id</li>
     * </ul>
     *
     * @param tabId 标签页 ID
     * @return 标签页名称
     */
    private String getTabName(int tabId) {
        switch (tabId) {
            case 1:
                return "猫猫币";
            case 2:
                return "闪烁猫猫币";
            case 3:
                return "其他";
            default:
                return "自定义 #" + tabId;
        }
    }

    /**
     * 获取标签页图标
     * <p>
     * 硬编码标签页图标，不依赖 NekoPageRegistry（避免 VM 依赖）。
     * <ul>
     * <li>Tab 1: 猫猫币物品</li>
     * <li>Tab 2: 闪烁猫猫币物品</li>
     * <li>Tab 3+: null（显示数字）</li>
     * </ul>
     *
     * @param tabId 标签页 ID
     * @return 标签页图标 ItemStack，无图标时返回 null
     */
    private ItemStack getTabIcon(int tabId) {
        switch (tabId) {
            case 1:
                return NekoCurrencyRegistrar.getItemStack(NekoCurrencyRegistrar.NEKO_ID, 1);
            case 2:
                return NekoCurrencyRegistrar.getItemStack(NekoCurrencyRegistrar.SHIMMERING_NEKO_ID, 1);
            default:
                return null;
        }
    }

    // ==================== 内部类 ====================

    /**
     * 交易条目
     * <p>
     * 封装交易组引用和交易在组内的索引，
     * 用于交易列表的过滤、排序和 Widget 构建。
     */
    private static class TradeEntry {

        /** 关联的交易组 */
        final NekoTradeGroup group;
        /** 交易在组内的索引（0-based） */
        final int tradeIndex;

        TradeEntry(NekoTradeGroup group, int tradeIndex) {
            this.group = group;
            this.tradeIndex = tradeIndex;
        }
    }
}

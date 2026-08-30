package com.miaokatze.gtit.gui.vm.edit;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.cleanroommc.modularui.api.IPanelHandler;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.drawable.GuiTextures;
import com.cleanroommc.modularui.utils.item.ItemStackHandler;
import com.cleanroommc.modularui.value.StringValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.StringSyncValue;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.cleanroommc.modularui.widgets.slot.ItemSlot;
import com.cleanroommc.modularui.widgets.slot.ModularSlot;
import com.cleanroommc.modularui.widgets.slot.PhantomItemSlot;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;
import com.miaokatze.gtit.client.gui.NekoConfirmationDialog;
import com.miaokatze.gtit.client.gui.NekoDraggableEditPanel;
import com.miaokatze.gtit.client.gui.NekoGuiTextures;
import com.miaokatze.gtit.client.gui.NekoTradeItemDisplay;
import com.miaokatze.gtit.gui.vm.edit.EditOverlayController.EditOverlayType;
import com.miaokatze.gtit.trade.v2.NekoBigItemStack;
import com.miaokatze.gtit.trade.v2.NekoEditNetworkManager;
import com.miaokatze.gtit.trade.v2.NekoEditPacket;
import com.miaokatze.gtit.trade.v2.NekoTrade;
import com.miaokatze.gtit.trade.v2.NekoTradeDatabase;
import com.miaokatze.gtit.trade.v2.NekoTradeGroup;

/**
 * 交易条目编辑器（A01 蓝图 G2 抽取自 NekoVMGuiV2，方法体逐字搬移）
 * <p>
 * 持有交易编辑的全部本地状态：32 槽物品缓冲区（slot 0-15 需求 / 16-31 产物）、
 * 参数字段（冷却/BQ 绑定/NBT 匹配等）、编辑目标同步值与编辑/新建模式标志。
 * 面板经 {@link #buildEditPanel} 注册到 {@link EditOverlayController}（TRADE 位，顺序冻结）。
 * <p>
 * <b>双端镜像构建</b>：本类与面板构建在服务端同样执行（v1.7.17），类内不得有
 * 客户端专属 API 的静态引用；客户端专属调用（如聊天提示）仅在按钮回调内运行时触达。
 */
public final class TradeEditor {

    private static final Logger LOG = LogManager.getLogger("gtit");

    /** 覆盖层控制器（TRADE 位注册与显隐状态） */
    private final EditOverlayController overlay;
    /** 请求宿主执行完整关闭流程（closeEditOverlay，含 TRADE 型残留清理） */
    private final Runnable requestClose;
    /** 请求宿主强制刷新主面板（保存成功后） */
    private final Runnable requestMainPanelRefresh;
    /** 删除确认弹框引用（客户端，宿主 build 客户端块经 {@link #setDeleteConfirm} 注入；服务端保持 null） */
    private NekoConfirmationDialog deleteConfirmDialog;
    /** 删除确认面板 handler（客户端，宿主 build 客户端块注入；服务端保持 null） */
    private IPanelHandler deleteConfirmPanel;

    /** 当前正在编辑的交易显示数据（客户端，打开编辑面板时设置） */
    private NekoTradeItemDisplay editingDisplay;

    /** 编辑：冷却时间（秒） */
    private int editCooldown = 0;
    /** 编辑：最大交易次数（-1=无限制） */
    private int editMaxTrades = -1;
    /** 编辑：BQ 任务绑定 ID */
    private String editBqQuestId = "";
    /** 编辑：标签页 ID */
    private int editTabId = 1;
    /** 编辑：顺序 ID */
    private int editOrderId = 0;
    /** 编辑：是否严格匹配 NBT（v1.7.6 G3⑤，统一默认 false=仅按物品匹配） */
    private boolean editRecordNBT = false;
    /** 编辑：是否新建交易模式（v1.7.6 G3④，true=保存时走 createTrade，false=走 saveTrade） */
    private boolean editTradeIsNew = false;

    /** 编辑目标同步值特殊标记：新建交易模式（v1.7.6 G3④，服务端仅清空编辑缓冲区） */
    private static final String TRADE_TARGET_NEW = "@new";
    /** 编辑目标同步值（C2S：客户端设置 "groupId:tradeIndex" 或 {@link #TRADE_TARGET_NEW}，服务端据此加载交易数据到编辑缓冲区） */
    private StringSyncValue editTargetSync;
    /** 编辑物品缓冲区（双端共享：slot 0-15=需求物品两行，slot 16-31=产物物品两行，v1.7.6 G3① 8→32） */
    private final ItemStackHandler editItemHandler = new ItemStackHandler(32);
    /** 交易编辑 PhantomItemSlot 引用（服务端加载同一条目时强制回传槽位，避免客户端取消后的空缓存残留） */
    private final List<ItemSlot> editTradeSlotRefs = new ArrayList<>();

    public TradeEditor(EditOverlayController overlay, Runnable requestClose, Runnable requestMainPanelRefresh) {
        this.overlay = overlay;
        this.requestClose = requestClose;
        this.requestMainPanelRefresh = requestMainPanelRefresh;
    }

    /**
     * 编辑请求回调入口（编辑模式下左键点击交易时触发，宿主 PanelCallback 委托至此）
     * <p>
     * 打开交易编辑面板，显示当前交易数据供编辑。
     * 先通过 editTargetSync 告知服务端加载交易数据到编辑缓冲区，
     * 再打开编辑面板（PhantomItemSlot 自动同步缓冲区内容到客户端）。
     *
     * @param display 被点击的交易显示数据（宿主已做空判）
     */
    public void beginEdit(NekoTradeItemDisplay display) {
        this.editingDisplay = display;
        this.editTradeIsNew = false;
        // v1.7.6 G3③ 格子残留修复（重置点①）：客户端立即清空 32 槽+重置全部编辑字段，
        // 防止连续切换编辑不同条目时 PhantomItemSlot 客户端缓存残留上一条内容
        // （服务端缓冲区随后由 loadTradeIntoEditBuffer 重置点②覆盖并同步回客户端）
        clearTradeEditState();
        // 填充编辑参数（从显示数据中提取）
        populateEditFields(display);
        // 通知服务端加载交易数据到编辑缓冲区
        if (editTargetSync != null) {
            editTargetSync.setValue(
                display.getGroupId()
                    .toString() + ":"
                    + display.getTradeIndex());
        }
        // 打开编辑面板
        overlay.open(EditOverlayType.TRADE);
    }

    /**
     * 打开新建交易编辑面板（客户端，编辑模式下点击交易列表尾「新建交易条目」按钮触发，v1.7.6 G3④）
     * <p>
     * 字段全部置默认值（{@link #clearTradeEditState}），editTabId 固定为当前所在标签页
     * （新建条目挂到该页，orderId 由服务端取页内最大+1）；
     * 通知服务端清空编辑缓冲区（{@link #TRADE_TARGET_NEW} 标记）。
     * 保存时走 {@code NekoEditNetworkManager.sendCreateTrade}（服务端分配 UUID 追加到该 page）。
     *
     * @param tabId 当前标签页 ID（新建条目挂到该页）
     */
    public void beginNewTrade(int tabId) {
        this.editingDisplay = null;
        this.editTradeIsNew = true;
        // 重置点①同款：客户端立即清空 32 槽+重置全部编辑字段
        clearTradeEditState();
        this.editTabId = tabId;
        // 通知服务端清空编辑缓冲区（新建模式无既有物品可加载）
        if (editTargetSync != null) {
            editTargetSync.setValue(TRADE_TARGET_NEW);
        }
        overlay.open(EditOverlayType.TRADE);
    }

    /**
     * 注册编辑目标同步值（由宿主 registerSyncValues 在原位调用，命名通道不受树序影响）
     *
     * @param syncManager 面板同步管理器
     */
    public void registerSyncValues(PanelSyncManager syncManager) {
        // --- 编辑目标（C2S：客户端设置交易位置，服务端加载到编辑缓冲区）---
        editTargetSync = new StringSyncValue(() -> "", val -> {
            if (syncManager != null && !syncManager.isClient() && val != null && !val.isEmpty()) {
                loadTradeIntoEditBuffer(val);
            }
        });
        editTargetSync.allowC2S();
        syncManager.syncValue("nekoV2EditTarget", editTargetSync);
    }

    /**
     * 从交易显示数据填充编辑字段（客户端）
     * <p>
     * 将 {@link NekoTradeItemDisplay} 中的数据提取到编辑面板本地字段，
     * 供编辑面板的 TextFieldWidget 显示和编辑。
     * <p>
     * v1.7.6 G3② 货币解绑：不再提取货币类型/数量到独立字段（货币由需求格物品条目表达）；
     * v1.7.6 G3⑤：从交易读取 recordNBT 填充「严格匹配NBT」开关。
     *
     * @param display 交易显示数据
     */
    private void populateEditFields(NekoTradeItemDisplay display) {
        // 从 NekoTradeDatabase 获取配置信息（冷却、BQ 绑定、NBT 匹配开关等）
        NekoTradeGroup group = NekoTradeDatabase.INSTANCE.getTradeGroup(display.getGroupId());
        if (group != null) {
            editCooldown = group.getCooldown();
            editMaxTrades = group.getMaxTrades();
            editBqQuestId = group.getBqQuestId() != null ? group.getBqQuestId() : "";
            editTabId = group.getTabId();
            editOrderId = group.getOrderId();
            // v1.7.6 G3⑤：recordNBT 为交易级字段（非组级），按索引取当前交易
            int tradeIndex = display.getTradeIndex();
            if (tradeIndex >= 0 && tradeIndex < group.getTrades()
                .size()) {
                editRecordNBT = group.getTrades()
                    .get(tradeIndex)
                    .isRecordNBT();
            }
        }
    }

    /**
     * 服务端：加载交易数据到编辑缓冲区（v1.7.6 G3① 扩 32 槽）
     * <p>
     * 解析 "groupId:tradeIndex" 格式的目标标识，从 {@link NekoTradeDatabase}
     * 查找交易，将需求物品加载到 editItemHandler 的 slot 0-15（两行），
     * 产物物品加载到 slot 16-31（两行）。
     * <p>
     * v1.7.6 G3③ 格子残留修复（重置点②）：每次加载前先整体清空 32 槽；
     * {@link #TRADE_TARGET_NEW}（新建模式，G3④）或查找失败时仅清空不加载。
     *
     * @param target "groupId:tradeIndex" 格式的目标标识，或 {@link #TRADE_TARGET_NEW}
     */
    private void loadTradeIntoEditBuffer(String target) {
        try {
            // 先整体清空 32 槽（重置点②：防上一条编辑内容残留）
            for (int i = 0; i < editItemHandler.getSlots(); i++) {
                editItemHandler.setStackInSlot(i, null);
            }
            // 新建模式：无既有交易可加载，保持空缓冲区
            if (TRADE_TARGET_NEW.equals(target)) {
                forceSyncTradeEditSlots();
                return;
            }

            String[] parts = target.split(":");
            if (parts.length != 2) {
                forceSyncTradeEditSlots();
                return;
            }
            UUID groupId = UUID.fromString(parts[0]);
            int tradeIndex = Integer.parseInt(parts[1]);

            NekoTradeGroup group = NekoTradeDatabase.INSTANCE.getTradeGroup(groupId);
            if (group == null || tradeIndex < 0
                || tradeIndex >= group.getTrades()
                    .size()) {
                forceSyncTradeEditSlots();
                return;
            }

            NekoTrade trade = group.getTrades()
                .get(tradeIndex);

            // 加载需求物品到 slot 0-15（v1.7.6 G3① 上限 4→16）
            List<NekoBigItemStack> fromItems = trade.getFromItems();
            for (int i = 0; i < Math.min(fromItems.size(), 16); i++) {
                NekoBigItemStack bigStack = fromItems.get(i);
                if (bigStack != null && bigStack.getBaseStack() != null) {
                    ItemStack stack = bigStack.getBaseStack()
                        .copy();
                    stack.stackSize = bigStack.getStackSize();
                    editItemHandler.setStackInSlot(i, stack);
                }
            }

            // 加载产物物品到 slot 16-31（v1.7.6 G3① 上限 4→16，槽位偏移 4+i→16+i）
            List<NekoBigItemStack> toItems = trade.getToItems();
            for (int i = 0; i < Math.min(toItems.size(), 16); i++) {
                NekoBigItemStack bigStack = toItems.get(i);
                if (bigStack != null && bigStack.getBaseStack() != null) {
                    ItemStack stack = bigStack.getBaseStack()
                        .copy();
                    stack.stackSize = bigStack.getStackSize();
                    editItemHandler.setStackInSlot(16 + i, stack);
                }
            }

            // ItemSlotSH 仅在检测到服务端槽位内容变化时自动发包。
            // 取消编辑会先清空客户端缓存；再次打开同一条目时服务端缓冲区可能未变化，
            // 因而不会自动回传，表现为物品/猫猫币消失。每次加载后强制回传 32 槽，
            // 让客户端缓存与服务端编辑缓冲区重新对齐。
            forceSyncTradeEditSlots();

            LOG.info("[NekoEdit] 已加载交易到编辑缓冲区: {}", target);
        } catch (Exception e) {
            LOG.error("[NekoEdit] 加载交易到编辑缓冲区失败: {}", target, e);
        }
    }

    /** 强制同步交易编辑 32 个 PhantomItemSlot（仅服务端加载路径调用）。 */
    private void forceSyncTradeEditSlots() {
        for (ItemSlot slot : editTradeSlotRefs) {
            if (slot != null && slot.getSyncHandler() != null) {
                slot.getSyncHandler()
                    .forceSyncItem();
            }
        }
    }

    /**
     * 构建交易编辑面板（v1.7.6 G3① 重构）
     * <p>
     * 创建包含 PhantomItemSlot（物品拖放配置）和 TextFieldWidget（参数编辑）
     * 的编辑面板。slot 0-15 为需求物品（两行×8），slot 16-31 为产物物品（两行×8）。
     * <p>
     * v1.7.6 G3② 货币解绑：面板不再设「猫猫币类型/数量」输入框——货币需求=需求格中的
     * 猫猫币物品条目（购买时扣钱包），货币产出=产物格中的猫猫币物品条目（购买后入钱包）。
     * v1.7.6 G3⑤：新增「严格匹配NBT」开关（recordNBT，统一默认不勾=仅按物品匹配）。
     * v1.7.6 G3④：新建模式（{@link #editTradeIsNew}）下标题切换为「新建交易」，保存走 createTrade。
     *
     * @return 编辑覆盖层面板
     */
    public NekoDraggableEditPanel buildEditPanel() {
        NekoDraggableEditPanel editPanel = new NekoDraggableEditPanel();
        editPanel.size(250, 190);
        // v1.7.7 G2 迁移为主面板内嵌 ParentWidget 覆盖层后无默认背景，需手动补上 MC 风格背景
        editPanel.background(GuiTextures.MC_BACKGROUND);
        editPanel.leftRel(0.5f)
            .topRel(0.5f)
            .anchorLeft(0.5f)
            .anchorTop(0.5f);
        editPanel.setEnabledIf(w -> overlay.isCurrent(EditOverlayType.TRADE));

        // 标题（新建 / 编辑动态切换，v1.7.6 G3④）
        editPanel.child(
            new TextWidget<>(IKey.dynamic(() -> EnumChatFormatting.GOLD + (editTradeIsNew ? "新建交易" : "编辑交易"))).top(5)
                .horizontalCenter());

        // --- 需求物品区（slot 0-15，两行×8；v1.7.6 G3①）---
        // 货币解绑提示：需求格放猫猫币物品 = 货币需求（购买时扣钱包）
        editPanel.child(
            IKey.str(EnumChatFormatting.WHITE + "需求:")
                .asWidget()
                .left(8)
                .top(20));
        for (int i = 0; i < 16; i++) {
            ItemSlot slot = new PhantomItemSlot().slot(new ModularSlot(editItemHandler, i))
                .left(40 + (i % 8) * 20)
                .top(18 + (i / 8) * 20);
            editTradeSlotRefs.add(slot);
            editPanel.child(slot);
        }

        // --- 产物物品区（slot 16-31，两行×8；v1.7.6 G3①）---
        // 货币解绑提示：产物格放猫猫币物品 = 货币产出（购买后入钱包）
        editPanel.child(
            IKey.str(EnumChatFormatting.WHITE + "产物:")
                .asWidget()
                .left(8)
                .top(62));
        for (int i = 0; i < 16; i++) {
            ItemSlot slot = new PhantomItemSlot().slot(new ModularSlot(editItemHandler, 16 + i))
                .left(40 + (i % 8) * 20)
                .top(60 + (i / 8) * 20);
            editTradeSlotRefs.add(slot);
            editPanel.child(slot);
        }

        // --- 参数编辑区（v1.7.6 G3②：原「猫猫币类型/数量」两行已删除）---
        int fieldY = 105;
        int fieldHeight = 14;
        int labelWidth = 70;
        int fieldWidth = 160;
        int spacing = 17;

        // 冷却时间
        editPanel.child(
            IKey.str(EnumChatFormatting.WHITE + "冷却(秒):")
                .asWidget()
                .left(8)
                .top(fieldY + 2));
        editPanel.child(new TextFieldWidget().value(new StringValue.Dynamic(() -> String.valueOf(editCooldown), val -> {
            try {
                editCooldown = Integer.parseInt(val);
            } catch (NumberFormatException ignored) {}
        }))
            .setNumbers(-1, Integer.MAX_VALUE)
            .left(labelWidth)
            .top(fieldY)
            .size(fieldWidth, fieldHeight));

        // BQ 绑定 ID
        fieldY += spacing;
        editPanel.child(
            IKey.str(EnumChatFormatting.WHITE + "BQ绑定ID:")
                .asWidget()
                .left(8)
                .top(fieldY + 2));
        editPanel.child(
            new TextFieldWidget().value(new StringValue.Dynamic(() -> editBqQuestId, val -> editBqQuestId = val))
                .setMaxLength(60)
                .left(labelWidth)
                .top(fieldY)
                .size(fieldWidth, fieldHeight));

        // 严格匹配 NBT（v1.7.6 G3⑤，点击切换；统一默认不勾=仅按物品匹配）
        fieldY += spacing;
        editPanel.child(
            IKey.str(EnumChatFormatting.WHITE + "严格匹配NBT:")
                .asWidget()
                .left(8)
                .top(fieldY + 2));
        ButtonWidget<?> recordNbtToggle = new ButtonWidget<>().left(labelWidth)
            .top(fieldY)
            .size(fieldWidth, fieldHeight)
            .background(NekoGuiTextures.TEXT_FIELD_BACKGROUND)
            .overlay(
                IKey.dynamic(() -> editRecordNBT ? EnumChatFormatting.GREEN + "启用" : EnumChatFormatting.RED + "停用"))
            .onMouseTapped(mouse -> {
                editRecordNBT = !editRecordNBT;
                return true;
            });
        recordNbtToggle.tooltipBuilder(t -> {
            t.addLine(IKey.str("点击切换需求物品的 NBT 匹配严格度"));
            t.addLine(IKey.str(EnumChatFormatting.GRAY + "启用：需求物品按物品+NBT 精确匹配"));
            t.addLine(IKey.str(EnumChatFormatting.GRAY + "停用：仅按物品匹配（忽略 NBT 差异）"));
        });
        recordNbtToggle.tooltipAutoUpdate(true);
        editPanel.child(recordNbtToggle);

        // --- 保存 / 删除 / 取消按钮 ---
        editPanel.child(
            new ButtonWidget<>().size(50, 16)
                .left(30)
                .bottom(8)
                .overlay(IKey.str("保存"))
                .onMouseTapped(mouse -> {
                    saveTradeEdit();
                    requestClose.run();
                    return true;
                }));
        // 删除按钮（v1.7.7 编辑模式删除交易条目）：几何居中（面板宽 250：保存 30-80 / 删除 100-150 / 取消 170-220）；
        // 新建模式禁用（无既有条目可删）；点击弹出宿主二次确认弹框，确认后才发 ACTION_DELETE_TRADE
        editPanel.child(
            new ButtonWidget<>().size(50, 16)
                .left(100)
                .bottom(8)
                .overlay(IKey.str("删除"))
                .setEnabledIf(w -> !editTradeIsNew)
                .tooltipBuilder(t -> t.addLine(IKey.str("删除该交易条目（不可恢复）")))
                .onMouseTapped(mouse -> {
                    if (deleteConfirmDialog == null || deleteConfirmPanel == null || editingDisplay == null)
                        return true;
                    deleteConfirmDialog.setButtonText("是", "否");
                    deleteConfirmDialog.setParams("是否确认删除该条目", () -> {
                        sendDeleteTrade(
                            editingDisplay.getGroupId()
                                .toString());
                        requestClose.run();
                    });
                    deleteConfirmPanel.openPanel();
                    return true;
                }));
        editPanel.child(
            new ButtonWidget<>().size(50, 16)
                .right(30)
                .bottom(8)
                .overlay(IKey.str("取消"))
                .onMouseTapped(mouse -> {
                    requestClose.run();
                    return true;
                }));

        return editPanel;
    }

    /**
     * 注入删除确认弹框（宿主 build 客户端块调用）
     * <p>
     * 与宿主 {@code meModeConfirmDialog}/{@code meModeConfirmPanel} 同批初始化；
     * 服务端不注入（保持 null），删除按钮回调内的 null 守卫使服务端触达无效。
     *
     * @param dialog 删除确认弹框
     * @param panel  删除确认面板 handler
     */
    public void setDeleteConfirm(NekoConfirmationDialog dialog, IPanelHandler panel) {
        this.deleteConfirmDialog = dialog;
        this.deleteConfirmPanel = panel;
    }

    /**
     * 发送删除交易条目请求（客户端 → 服务端）
     * <p>
     * 经 {@link NekoEditNetworkManager#sendToServer} 直发
     * {@link NekoEditPacket#ACTION_DELETE_TRADE}（targetId=交易组 UUID 字符串，
     * 无载荷）；服务端按 groupId 定位并删除条目，走「落盘 → 热重载 → 全服广播」
     * 同一权威链。编辑模式统一闸（processAction）之外无需额外校验。
     *
     * @param groupId 交易组 UUID 字符串
     */
    private void sendDeleteTrade(String groupId) {
        NekoEditNetworkManager.sendToServer(NekoEditPacket.ACTION_DELETE_TRADE, groupId, 0, "");
    }

    /**
     * 清空交易编辑面板客户端状态（v1.7.6 G3③ 格子残留修复）
     * <p>
     * 清空客户端编辑物品缓冲区 32 槽并重置全部编辑字段为默认值。
     * 在打开编辑面板（{@link #beginEdit}/{@link #beginNewTrade}，重置点①）
     * 与面板关闭回调（重置点③）中调用，防止连续编辑不同条目时
     * PhantomItemSlot 客户端缓存残留上一条内容。
     * <p>
     * 仅客户端调用：直接改客户端 handler 不发包，服务端缓冲区随后由
     * {@link #loadTradeIntoEditBuffer}（重置点②）覆盖并经 widget 层同步回客户端。
     */
    public void clearTradeEditState() {
        for (int i = 0; i < editItemHandler.getSlots(); i++) {
            editItemHandler.setStackInSlot(i, null);
        }
        editCooldown = 0;
        editMaxTrades = -1;
        editBqQuestId = "";
        editTabId = 1;
        editOrderId = 0;
        editRecordNBT = false;
    }

    /**
     * 保存交易编辑（客户端 → 服务端）
     * <p>
     * 将编辑面板的物品缓冲区内容（需求 slot 0-15 / 产物 slot 16-31，v1.7.6 G3①）和
     * 参数字段序列化为 JSON，发送到服务端。
     * <p>
     * v1.7.6 G3② 货币解绑：不再发送 currencyType/currencyAmount——货币需求/产出由
     * fromItems/toItems 中的猫猫币物品条目表达（服务端保存时无条件清除旧 currency 字段）。
     * v1.7.6 G3④：新建模式（{@link #editTradeIsNew}）走
     * {@link NekoEditNetworkManager#sendCreateTrade}（tabId 定位），
     * 编辑现有交易走 {@link NekoEditNetworkManager#sendSaveTrade}。
     */
    private void saveTradeEdit() {
        // 新建模式无 editingDisplay（无现有交易可定位）；编辑模式必须有
        if (!editTradeIsNew && editingDisplay == null) return;

        try {
            com.google.gson.JsonObject json = new com.google.gson.JsonObject();

            // 基础参数
            json.addProperty("tabId", editTabId);
            json.addProperty("orderId", editOrderId);
            json.addProperty("cooldown", editCooldown);
            json.addProperty("maxTrades", editMaxTrades);
            json.addProperty("bqQuestId", editBqQuestId);
            // v1.7.6 G3⑤ NBT 选框
            json.addProperty("recordNBT", editRecordNBT);

            // 需求物品（slot 0-15，跳过空槽；猫猫币条目 = 货币需求，G3②）
            com.google.gson.JsonArray fromItems = new com.google.gson.JsonArray();
            for (int i = 0; i < 16; i++) {
                ItemStack stack = editItemHandler.getStackInSlot(i);
                if (stack != null) {
                    fromItems.add(EditJsonCodec.itemStackToEditJson(stack));
                }
            }
            json.add("fromItems", fromItems);

            // 产物物品（slot 16-31，跳过空槽；猫猫币条目 = 产出入钱包，G3②）
            com.google.gson.JsonArray toItems = new com.google.gson.JsonArray();
            for (int i = 16; i < 32; i++) {
                ItemStack stack = editItemHandler.getStackInSlot(i);
                if (stack != null) {
                    toItems.add(EditJsonCodec.itemStackToEditJson(stack));
                }
            }
            json.add("toItems", toItems);

            // 客户端防御性校验：编辑现有交易时，若 toItems 为空则阻止发送
            // 原因：toItems 为空的交易会被服务端跳过注册，导致交易"消失"
            // （v1.7.33 修复交易条目保存丢失：客户端不发送会导致交易丢失的空数据）
            if (!editTradeIsNew && toItems.size() == 0) {
                LOG.warn("[NekoEdit] 客户端阻止保存：编辑模式下 toItems 为空（fromItems={}），疑似物品同步未完成，跳过发送", fromItems.size());
                // 向玩家显示提示（客户端本地聊天消息）
                try {
                    net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getMinecraft();
                    if (mc.thePlayer != null) {
                        mc.thePlayer.addChatMessage(
                            new net.minecraft.util.ChatComponentText(
                                net.minecraft.util.EnumChatFormatting.RED + "[编辑模式] 保存失败：产物数据为空，请等待物品显示后再试"));
                    }
                } catch (Exception ignored) {
                    // 客户端环境异常时不阻塞
                }
                return;
            }

            // 发送到服务端（新建 / 编辑分流，v1.7.6 G3④）
            if (editTradeIsNew) {
                NekoEditNetworkManager.sendCreateTrade(String.valueOf(editTabId), json.toString());
            } else {
                NekoEditNetworkManager.sendSaveTrade(
                    editingDisplay.getGroupId()
                        .toString(),
                    editingDisplay.getTradeIndex(),
                    json.toString());
            }

            // 发送成功后强制刷新主面板，确保客户端显示与最新配置同步
            requestMainPanelRefresh.run();
            LOG.info(
                "[NekoEdit] 客户端发送保存: group={}, index={}, new={}, fromItems={}, toItems={}",
                editTradeIsNew ? String.valueOf(editTabId)
                    : editingDisplay.getGroupId()
                        .toString(),
                editTradeIsNew ? -1 : editingDisplay.getTradeIndex(),
                editTradeIsNew,
                fromItems.size(),
                toItems.size());

        } catch (Exception e) {
            LOG.error("[NekoEdit] 保存交易编辑失败", e);
        }
    }
}

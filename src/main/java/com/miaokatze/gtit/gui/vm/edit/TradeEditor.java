package com.miaokatze.gtit.gui.vm.edit;

import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.cleanroommc.modularui.api.IPanelHandler;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.drawable.GuiTextures;
import com.cleanroommc.modularui.value.StringValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.TextWidget;
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
 * 交易条目编辑器，保存完整多重匹配与矿词草稿。
 * <p>
 * 持有交易编辑的全部本地状态：32 槽物品缓冲区（slot 0-15 需求 / 16-31 产物）、
 * 参数字段（冷却/BQ 绑定/NBT 匹配等）与编辑/新建模式标志。
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
    /**
     * 默认贸易条目保存警告弹框引用（客户端，v1.8.17，宿主 build 客户端块经
     * {@link #setDefaultSaveConfirm} 注入；服务端保持 null）
     */
    private NekoConfirmationDialog defaultSaveConfirmDialog;
    /** 默认贸易条目保存警告面板 handler（客户端，服务端保持 null） */
    private IPanelHandler defaultSaveConfirmPanel;

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

    private final NekoBigItemStack[] choices = new NekoBigItemStack[32];
    private final boolean[] appendMode = new boolean[32];
    private boolean oversizedChoices;
    private TradeOreDialog oreDialog;
    private IPanelHandler orePanel;

    public TradeEditor(EditOverlayController overlay, Runnable requestClose, Runnable requestMainPanelRefresh) {
        this.overlay = overlay;
        this.requestClose = requestClose;
        this.requestMainPanelRefresh = requestMainPanelRefresh;
    }

    /**
     * 编辑请求回调入口（编辑模式下左键点击交易时触发，宿主 PanelCallback 委托至此）
     * <p>
     * 打开交易编辑面板，显示当前交易数据供编辑。
     * 从已同步数据库复制完整交易草稿，编辑期间不通过物品槽回包覆盖。
     *
     * @param display 被点击的交易显示数据（宿主已做空判）
     */
    public void beginEdit(NekoTradeItemDisplay display) {
        this.editingDisplay = display;
        this.editTradeIsNew = false;
        // v1.7.6 G3③ 格子残留修复（重置点①）：客户端立即清空 32 槽+重置全部编辑字段，
        // 防止连续切换编辑不同条目时草稿残留上一条内容
        // 从数据库复制独立草稿，避免修改正在展示的交易。
        clearTradeEditState();
        // 填充编辑参数（从显示数据中提取）
        populateEditFields(display);
        NekoTradeGroup source = NekoTradeDatabase.INSTANCE.getTradeGroup(display.getGroupId());
        if (source != null && display.getTradeIndex() >= 0
            && display.getTradeIndex() < source.getTrades()
                .size()) {
            NekoTrade original = source.getTrades()
                .get(display.getTradeIndex());
            loadChoices(original.getFromItems(), 0);
            loadChoices(original.getToItems(), 16);
        }
        overlay.open(EditOverlayType.TRADE);
    }

    /**
     * 打开新建交易编辑面板（客户端，编辑模式下点击交易列表尾「新建交易条目」按钮触发，v1.7.6 G3④）
     * <p>
     * 字段全部置默认值（{@link #clearTradeEditState}），editTabId 固定为当前所在标签页
     * （新建条目挂到该页，orderId 由服务端取页内最大+1）；
     * 草稿缓冲区独立于服务端交易数据库。
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
        overlay.open(EditOverlayType.TRADE);
    }

    /**
     * 保留宿主注册入口；完整交易仅在保存时发送。
     *
     * @param syncManager 面板同步管理器
     */
    public void registerSyncValues(PanelSyncManager syncManager) {
        // Draft slots are client-local; the validated save packet is the only C2S update.
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
     * 双端构建相同的 16 个需求格与 16 个产出格，具体编辑事件仅客户端触达。
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
            com.cleanroommc.modularui.widgets.ItemDisplayWidget slot = new TradeChoiceSlot(this, i)
                .left(40 + (i % 8) * 20)
                .top(18 + (i / 8) * 20);

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
            com.cleanroommc.modularui.widgets.ItemDisplayWidget slot = new TradeChoiceSlot(this, 16 + i)
                .left(40 + (i % 8) * 20)
                .top(60 + (i / 8) * 20);

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
        // 保存（v1.8.17：默认贸易条目先弹警告弹框，确认"仍要保存"后才真正保存）
        editPanel.child(
            new ButtonWidget<>().size(50, 16)
                .left(30)
                .bottom(8)
                .overlay(IKey.str("保存"))
                .onMouseTapped(mouse -> {
                    if (isEditingDefaultTrade() && defaultSaveConfirmDialog != null
                        && defaultSaveConfirmPanel != null) {
                        // v1.8.20 ID 隔离：修改默认条目 = 原位覆盖（MIAO 语义 ID 保持），提示明确"这是默认任务"——
                        // 下次默认贸易组更新时启动会自动覆盖回新版
                        defaultSaveConfirmDialog.setButtonText("仍要保存", "取消");
                        defaultSaveConfirmDialog.setParams(
                            "这是默认任务（系统内置条目）：修改将原位覆盖默认条目本身，下次默认贸易组更新时会自动覆盖回新版。仍要保存吗？",
                            () -> { if (saveTradeEdit()) requestClose.run(); });
                        defaultSaveConfirmPanel.openPanel();
                    } else {
                        if (saveTradeEdit()) requestClose.run();
                    }
                    return true;
                }));
        // 删除按钮（v1.7.7 编辑模式删除交易条目）：几何居中（面板宽 250：保存 30-80 / 删除 100-150 / 取消 170-220）；
        // 新建模式禁用（无既有条目可删）；点击弹出宿主二次确认弹框，确认后才发 ACTION_DELETE_TRADE。
        // v1.8.17：默认贸易条目的确认文案切换为默认贸易组删除警告
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
                    // v1.8.20：默认条目删除提示明确"更新时会自动恢复"（启动自动覆盖按 MIAO 语义 ID 重新注入）
                    deleteConfirmDialog
                        .setParams(isEditingDefaultTrade() ? "这是默认任务（系统内置条目）：删除后下次默认贸易组更新时会自动恢复。" : "是否确认删除该条目", () -> {
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
     * 注入默认贸易条目保存警告弹框（v1.8.17，宿主 build 客户端块调用）
     * <p>
     * 服务端不注入（保持 null），保存按钮回调内的 null 守卫使服务端触达无效
     * （服务端直接走普通保存分支）。
     *
     * @param dialog 保存警告弹框
     * @param panel  保存警告面板 handler
     */
    public void setDefaultSaveConfirm(NekoConfirmationDialog dialog, IPanelHandler panel) {
        this.defaultSaveConfirmDialog = dialog;
        this.defaultSaveConfirmPanel = panel;
    }

    /**
     * 当前编辑目标是否为默认贸易条目（v1.8.17）
     * <p>
     * 仅编辑模式（非新建）且有编辑目标时，从交易数据库查询组级
     * {@code defaultEntry} 标记判定；服务端同样可判（服务端保存分支不弹框，
     * 判定结果仅用于客户端警告流程）。
     *
     * @return true=默认贸易条目
     */
    private boolean isEditingDefaultTrade() {
        if (editTradeIsNew || editingDisplay == null) return false;
        NekoTradeGroup group = NekoTradeDatabase.INSTANCE.getTradeGroup(editingDisplay.getGroupId());
        return group != null && group.isDefaultEntry();
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
     * 草稿缓存残留上一条内容。
     * <p>
     * 仅客户端调用：清除本地草稿，不更改服务端交易数据。
     */
    public void clearTradeEditState() {
        for (int i = 0; i < choices.length; i++) {
            choices[i] = null;
            appendMode[i] = false;
        }
        oversizedChoices = false;
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
    private boolean saveTradeEdit() {
        // 新建模式无 editingDisplay（无现有交易可定位）；编辑模式必须有
        if (!editTradeIsNew && editingDisplay == null) return false;
        if (oversizedChoices) {
            net.minecraft.client.Minecraft.getMinecraft().thePlayer
                .addChatMessage(new net.minecraft.util.ChatComponentText("§c原交易超过单侧 16 格，请使用 JSON 编辑，GUI 拒绝截断保存"));
            return false;
        }

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
                NekoBigItemStack stack = choices[i];
                if (stack != null) {
                    fromItems.add(new com.google.gson.Gson().toJsonTree(stack.toItemEntry(true)));
                }
            }
            json.add("fromItems", fromItems);

            // 产物物品（slot 16-31，跳过空槽；猫猫币条目 = 产出入钱包，G3②）
            com.google.gson.JsonArray toItems = new com.google.gson.JsonArray();
            for (int i = 16; i < 32; i++) {
                NekoBigItemStack stack = choices[i];
                if (stack != null) {
                    toItems.add(new com.google.gson.Gson().toJsonTree(stack.toItemEntry(true)));
                }
            }
            json.add("toItems", toItems);

            // 客户端防御性校验：编辑现有交易时，若 toItems 为空则阻止发送
            // 原因：toItems 为空的交易会被服务端跳过注册，导致交易"消失"
            // （v1.7.33 修复交易条目保存丢失：客户端不发送会导致交易丢失的空数据）
            if (toItems.size() == 0) {
                LOG.warn("[NekoEdit] 客户端阻止保存：编辑模式下 toItems 为空（fromItems={}），疑似物品同步未完成，跳过发送", fromItems.size());
                // 向玩家显示提示（客户端本地聊天消息）
                try {
                    net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getMinecraft();
                    if (mc.thePlayer != null) {
                        mc.thePlayer.addChatMessage(
                            new net.minecraft.util.ChatComponentText(
                                net.minecraft.util.EnumChatFormatting.RED + "[编辑模式] 保存失败：交易必须至少包含一个产出"));
                    }
                } catch (Exception ignored) {
                    // 客户端环境异常时不阻塞
                }
                return false;
            }

            String payload = json.toString();
            int limit = com.miaokatze.gtit.trade.v2.NekoEditActionHandler.MAX_JSON_PAYLOAD_LENGTH;
            if (payload.length() > limit) {
                net.minecraft.client.Minecraft.getMinecraft().thePlayer.addChatMessage(
                    new net.minecraft.util.ChatComponentText(
                        "§c保存失败：交易数据共 " + payload.length() + " 字符，超过 " + limit + " 字符上限；请减少候选或物品 NBT，草稿已保留"));
                return false;
            }

            // 发送到服务端（新建 / 编辑分流，v1.7.6 G3④）
            if (editTradeIsNew) {
                NekoEditNetworkManager.sendCreateTrade(String.valueOf(editTabId), payload);
            } else {
                NekoEditNetworkManager.sendSaveTrade(
                    editingDisplay.getGroupId()
                        .toString(),
                    editingDisplay.getTradeIndex(),
                    payload);
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

            return true;
        } catch (Exception e) {
            LOG.error("[NekoEdit] 保存交易编辑失败", e);
            return false;
        }
    }

    public void initOreDialog(com.cleanroommc.modularui.screen.ModularPanel parent) {
        oreDialog = new TradeOreDialog(this);
        orePanel = IPanelHandler.simple(parent, (p, player) -> oreDialog, true);
    }

    private void loadChoices(List<NekoBigItemStack> items, int offset) {
        oversizedChoices |= items.size() > 16;
        for (int i = 0; i < Math.min(16, items.size()); i++) {
            choices[offset + i] = items.get(i)
                .copy();
            oversizedChoices |= choices[offset + i].getAlternatives()
                .size() > 15;
            appendMode[offset + i] = !choices[offset + i].getAlternatives()
                .isEmpty();
        }
    }

    NekoBigItemStack choice(int index) {
        return choices[index];
    }

    boolean isAppend(int index) {
        return appendMode[index];
    }

    void toggleAppend(int index) {
        appendMode[index] = !appendMode[index];
    }

    void putChoice(int index, ItemStack stack) {
        if (stack == null || stack.stackSize <= 0) return;
        NekoBigItemStack next = new NekoBigItemStack(stack);
        if (appendMode[index] && choices[index] != null) {
            if (choices[index].getAlternatives()
                .size() < 15)
                choices[index].getAlternatives()
                    .add(next);
            else net.minecraft.client.Minecraft.getMinecraft().thePlayer
                .addChatMessage(new net.minecraft.util.ChatComponentText("§c每格最多 16 个候选，请先右键移除选项"));
        } else choices[index] = next;
    }

    void removeChoice(int index, boolean all) {
        NekoBigItemStack entry = choices[index];
        if (entry == null) return;
        if (!all && !entry.getAlternatives()
            .isEmpty()) {
            entry.getAlternatives()
                .remove(
                    entry.getAlternatives()
                        .size() - 1);
        } else {
            choices[index] = null;
            appendMode[index] = false;
        }
    }

    void openOre(int index) {
        if (oreDialog != null && orePanel != null) {
            oreDialog.edit(index);
            orePanel.openPanel();
        }
    }

    void applyOre(int index, String ore, int amount) {
        ore = ore.trim();
        if (amount <= 0 || (!ore.isEmpty() && net.minecraftforge.oredict.OreDictionary.getOres(ore)
            .isEmpty())) {
            throw new IllegalArgumentException("矿词必须至少包含一种物品，数量必须为正数");
        }
        NekoBigItemStack root = choices[index];
        NekoBigItemStack target = root;
        if (root != null && appendMode[index]
            && !root.getAlternatives()
                .isEmpty()) {
            target = root.getAlternatives()
                .get(
                    root.getAlternatives()
                        .size() - 1);
        }
        if (target == null) {
            if (ore.isEmpty()) return;
            target = new NekoBigItemStack(
                net.minecraftforge.oredict.OreDictionary.getOres(ore)
                    .get(0));
            choices[index] = target;
        }
        target.setOreDict(ore);
        target.setStackSize(amount);
    }

}

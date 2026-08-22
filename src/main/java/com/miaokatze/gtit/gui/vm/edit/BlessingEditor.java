package com.miaokatze.gtit.gui.vm.edit;

import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.drawable.GuiTextures;
import com.cleanroommc.modularui.utils.item.ItemStackHandler;
import com.cleanroommc.modularui.value.StringValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.StringSyncValue;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.cleanroommc.modularui.widgets.slot.ModularSlot;
import com.cleanroommc.modularui.widgets.slot.PhantomItemSlot;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;
import com.miaokatze.gtit.client.gui.NekoDraggableEditPanel;
import com.miaokatze.gtit.gui.vm.edit.EditOverlayController.EditOverlayType;

/**
 * 祝福预设编辑器（v1.7.6 G5；A01 蓝图 G4 抽取自 NekoVMGuiV2，方法体逐字搬移）
 * <p>
 * 节日表/生日模板/发件人三分派编辑：5 附件槽缓冲区 + 货币字段 + 目标循环切换。
 * 面板经 {@link #buildEditPanel} 注册到 {@link EditOverlayController}（BLESSING 位，顺序冻结）。
 * <p>
 * <b>双端镜像构建</b>：面板在服务端同样构建（v1.7.17），类内不得有客户端专属 API 的静态引用。
 */
public final class BlessingEditor {

    private static final Logger LOG = LogManager.getLogger("gtit");

    /** 覆盖层控制器（BLESSING 位注册与显隐状态） */
    private final EditOverlayController overlay;
    /** 请求宿主执行完整关闭流程（closeEditOverlay） */
    private final Runnable requestClose;

    public BlessingEditor(EditOverlayController overlay, Runnable requestClose) {
        this.overlay = overlay;
        this.requestClose = requestClose;
    }

    /**
     * 注册祝福编辑目标同步值（由宿主 registerSyncValues 在原位调用，命名通道不受树序影响）
     *
     * @param syncManager 面板同步管理器
     */
    public void registerSyncValues(PanelSyncManager syncManager) {
        // --- 祝福编辑目标（C2S：客户端设置 "festival:<index>"/"birthday"，服务端加载附件物品到编辑缓冲区，v1.7.6 G5）---
        editBlessingTargetSync = new StringSyncValue(() -> "", val -> {
            if (syncManager != null && !syncManager.isClient() && val != null && !val.isEmpty()) {
                loadBlessingIntoEditBuffer(val);
            }
        });
        editBlessingTargetSync.allowC2S();
        syncManager.syncValue("nekoV2EditBlessingTarget", editBlessingTargetSync);
    }

    // --- 祝福预设编辑（v1.7.6 G5：节日表 + 生日模板 + 发件人编辑） ---
    /** 祝福编辑附件槽位数（与邮件附件上限一致；猫猫币不入槽，由货币字段表达） */
    private static final int BLESSING_ITEM_SLOTS = 5;
    /** 祝福编辑目标同步值（C2S："festival:<index>"/"birthday"，服务端据此加载附件物品到编辑缓冲区） */
    private StringSyncValue editBlessingTargetSync;
    /** 祝福编辑物品缓冲区（双端共享：slot 0-4=附件物品） */
    private final ItemStackHandler editBlessingItemHandler = new ItemStackHandler(BLESSING_ITEM_SLOTS);
    /** 祝福编辑：当前目标标识（"birthday" / "festival:<index>"） */
    private String editBlessingTarget = "birthday";
    /** 祝福编辑：发件人显示名（面板内随任意保存一并提交） */
    private String editBlessingSender = "猫猫售货机";
    /** 祝福编辑：节日名称（仅节日目标） */
    private String editBlessingName = "";
    /** 祝福编辑：触发日期 "MM-dd"（仅节日目标） */
    private String editBlessingMonthDay = "";
    /** 祝福编辑：邮件标题 */
    private String editBlessingTitle = "";
    /** 祝福编辑：邮件正文 */
    private String editBlessingContent = "";
    /** 祝福编辑：猫猫币 ID（空串 = 无币附件） */
    private String editBlessingCurrency = "";
    /** 祝福编辑：猫猫币数量（作为附件物品发放） */
    private int editBlessingCurrencyAmount = 0;

    public void beginEdit(String target) {
        fillBlessingFieldsFromConfig(target);
        // 通知服务端加载该目标的附件物品到编辑缓冲区（用归一化后的目标，索引越界时已回退 birthday）
        if (editBlessingTargetSync != null) {
            editBlessingTargetSync.setValue(editBlessingTarget);
        }
        overlay.open(EditOverlayType.BLESSING);
    }

    private void cycleBlessingTarget(int delta) {
        java.util.List<String> targets = new java.util.ArrayList<>();
        targets.add("birthday");
        int festivalCount = com.miaokatze.gtit.mail.BlessingConfig.getFestivals()
            .size();
        for (int i = 0; i < festivalCount; i++) {
            targets.add("festival:" + i);
        }
        int index = targets.indexOf(editBlessingTarget);
        if (index < 0) index = 0;
        index = Math.floorMod(index + delta, targets.size());
        beginEdit(targets.get(index));
    }

    private void fillBlessingFieldsFromConfig(String target) {
        editBlessingSender = com.miaokatze.gtit.mail.BlessingConfig.getSender();
        if (target != null && target.startsWith("festival:")) {
            int index;
            try {
                index = Integer.parseInt(target.substring("festival:".length()));
            } catch (NumberFormatException e) {
                index = -1;
            }
            java.util.List<com.miaokatze.gtit.mail.BlessingConfig.FestivalBlessing> festivals = com.miaokatze.gtit.mail.BlessingConfig
                .getFestivals();
            if (index >= 0 && index < festivals.size()) {
                com.miaokatze.gtit.mail.BlessingConfig.FestivalBlessing festival = festivals.get(index);
                editBlessingTarget = target;
                editBlessingName = festival.name;
                editBlessingMonthDay = festival.monthDay;
                editBlessingTitle = festival.title;
                editBlessingContent = festival.content;
                editBlessingCurrency = festival.currency;
                editBlessingCurrencyAmount = festival.currencyAmount;
                return;
            }
            // 索引越界（配置被外部热改）：回退生日模板
            target = "birthday";
        }
        com.miaokatze.gtit.mail.BlessingConfig.BirthdayBlessing birthday = com.miaokatze.gtit.mail.BlessingConfig
            .getBirthday();
        editBlessingTarget = "birthday";
        editBlessingName = "";
        editBlessingMonthDay = "";
        editBlessingTitle = birthday.title;
        editBlessingContent = birthday.content;
        editBlessingCurrency = birthday.currency;
        editBlessingCurrencyAmount = birthday.currencyAmount;
    }

    private void loadBlessingIntoEditBuffer(String target) {
        try {
            // 先清空全部槽位（目标切换/物品减少时防残留）
            for (int i = 0; i < BLESSING_ITEM_SLOTS; i++) {
                editBlessingItemHandler.setStackInSlot(i, null);
            }
            java.util.List<com.miaokatze.gtit.mail.BlessingConfig.BlessingItem> specs;
            if ("birthday".equals(target)) {
                specs = com.miaokatze.gtit.mail.BlessingConfig.getBirthday().items;
            } else if (target != null && target.startsWith("festival:")) {
                int index = Integer.parseInt(target.substring("festival:".length()));
                java.util.List<com.miaokatze.gtit.mail.BlessingConfig.FestivalBlessing> festivals = com.miaokatze.gtit.mail.BlessingConfig
                    .getFestivals();
                if (index < 0 || index >= festivals.size()) return;
                specs = festivals.get(index).items;
            } else {
                return;
            }
            int slot = 0;
            for (com.miaokatze.gtit.mail.BlessingConfig.BlessingItem spec : specs) {
                if (spec == null || slot >= BLESSING_ITEM_SLOTS) continue;
                ItemStack stack = spec.toItemStack();
                if (stack != null) {
                    editBlessingItemHandler.setStackInSlot(slot++, stack);
                }
            }
            LOG.info("[NekoEdit] 已加载祝福预设到编辑缓冲区: {}", target);
        } catch (Exception e) {
            LOG.error("[NekoEdit] 加载祝福预设到编辑缓冲区失败: {}", target, e);
        }
    }

    private String blessingTargetDisplayName() {
        if (editBlessingTarget != null && editBlessingTarget.startsWith("festival:")) {
            return "节日：" + editBlessingName + "（" + editBlessingMonthDay + "）";
        }
        return "生日模板";
    }

    public NekoDraggableEditPanel buildEditPanel() {
        NekoDraggableEditPanel editPanel = new NekoDraggableEditPanel();
        editPanel.size(210, 205);
        // v1.7.7 G2 迁移为主面板内嵌 ParentWidget 覆盖层后无默认背景，需手动补上 MC 风格背景
        editPanel.background(GuiTextures.MC_BACKGROUND);
        editPanel.leftRel(0.5f)
            .topRel(0.5f)
            .anchorLeft(0.5f)
            .anchorTop(0.5f);
        editPanel.setEnabledIf(w -> overlay.isCurrent(EditOverlayType.BLESSING));

        // 标题（随目标切换）
        editPanel.child(
            new TextWidget<>(IKey.dynamic(() -> EnumChatFormatting.GOLD + "编辑祝福预设 - " + blessingTargetDisplayName()))
                .top(5)
                .horizontalCenter());

        // ---- 目标切换行：< 目标名 > ----
        editPanel.child(
            new ButtonWidget<>().size(16, 14)
                .left(30)
                .top(16)
                .overlay(IKey.str("<"))
                .tooltipBuilder(t -> {
                    t.addLine(IKey.str("上一个预设"));
                    t.addLine(IKey.str(EnumChatFormatting.GRAY + "切换前请先保存当前修改"));
                })
                .tooltipAutoUpdate(true)
                .onMouseTapped(mouse -> {
                    if (mouse == 0) {
                        cycleBlessingTarget(-1);
                        return true;
                    }
                    return false;
                }));
        editPanel.child(
            new TextWidget<>(IKey.dynamic(this::blessingTargetDisplayName)).left(50)
                .top(19)
                .size(110, 9)
                .textAlign(com.cleanroommc.modularui.utils.Alignment.Center)
                .scale(0.8f)
                .shadow(false));
        editPanel.child(
            new ButtonWidget<>().size(16, 14)
                .left(164)
                .top(16)
                .overlay(IKey.str(">"))
                .tooltipBuilder(t -> {
                    t.addLine(IKey.str("下一个预设"));
                    t.addLine(IKey.str(EnumChatFormatting.GRAY + "切换前请先保存当前修改"));
                })
                .tooltipAutoUpdate(true)
                .onMouseTapped(mouse -> {
                    if (mouse == 0) {
                        cycleBlessingTarget(1);
                        return true;
                    }
                    return false;
                }));

        int labelWidth = 58;
        int fieldWidth = 138;
        int fieldHeight = 14;
        int fieldY = 34;
        int spacing = 17;

        // ---- 发件人（全目标共用，随保存一并提交）----
        editPanel.child(
            new TextWidget<>(IKey.str("发件人:")).left(8)
                .top(fieldY + 2));
        TextFieldWidget senderField = new TextFieldWidget()
            .value(new StringValue.Dynamic(() -> editBlessingSender, val -> editBlessingSender = val))
            .setMaxLength(30);
        senderField.left(labelWidth)
            .top(fieldY)
            .size(fieldWidth, fieldHeight);
        // v1.7.19：TextFieldWidget 延迟到 PhantomItemSlot 之后添加（z-index 置顶）

        // ---- 节日名称（仅节日目标可见）----
        fieldY += spacing;
        TextWidget<?> nameLabel = new TextWidget<>(IKey.str("节日名称:"));
        nameLabel.left(8)
            .top(fieldY + 2);
        nameLabel.setEnabledIf(w -> editBlessingTarget != null && editBlessingTarget.startsWith("festival:"));
        editPanel.child(nameLabel);
        TextFieldWidget nameField = new TextFieldWidget()
            .value(new StringValue.Dynamic(() -> editBlessingName, val -> editBlessingName = val))
            .setMaxLength(20);
        nameField.left(labelWidth)
            .top(fieldY)
            .size(fieldWidth, fieldHeight);
        nameField.setEnabledIf(w -> editBlessingTarget != null && editBlessingTarget.startsWith("festival:"));
        // v1.7.19：TextFieldWidget 延迟到 PhantomItemSlot 之后添加（z-index 置顶）

        // ---- 触发日期 MM-dd（仅节日目标可见）----
        fieldY += spacing;
        TextWidget<?> dateLabel = new TextWidget<>(IKey.str("触发日期:"));
        dateLabel.left(8)
            .top(fieldY + 2);
        dateLabel.setEnabledIf(w -> editBlessingTarget != null && editBlessingTarget.startsWith("festival:"));
        editPanel.child(dateLabel);
        TextFieldWidget dateField = new TextFieldWidget()
            .value(new StringValue.Dynamic(() -> editBlessingMonthDay, val -> editBlessingMonthDay = val))
            .setMaxLength(5);
        dateField.left(labelWidth)
            .top(fieldY)
            .size(fieldWidth, fieldHeight);
        dateField.tooltipBuilder(t -> {
            t.addLine(IKey.str("固定公历日期，格式 MM-dd（如 01-01）"));
            t.addLine(IKey.str(EnumChatFormatting.GRAY + "农历节日请按当年农历自行换算"));
        });
        dateField.tooltipAutoUpdate(true);
        dateField.setEnabledIf(w -> editBlessingTarget != null && editBlessingTarget.startsWith("festival:"));
        // v1.7.19：TextFieldWidget 延迟到 PhantomItemSlot 之后添加（z-index 置顶）

        // ---- 邮件标题 ----
        fieldY += spacing;
        editPanel.child(
            new TextWidget<>(IKey.str("邮件标题:")).left(8)
                .top(fieldY + 2));
        TextFieldWidget titleField = new TextFieldWidget()
            .value(new StringValue.Dynamic(() -> editBlessingTitle, val -> editBlessingTitle = val))
            .setMaxLength(60);
        titleField.left(labelWidth)
            .top(fieldY)
            .size(fieldWidth, fieldHeight);
        // v1.7.19：TextFieldWidget 延迟到 PhantomItemSlot 之后添加（z-index 置顶）

        // ---- 邮件正文 ----
        fieldY += spacing;
        editPanel.child(
            new TextWidget<>(IKey.str("邮件正文:")).left(8)
                .top(fieldY + 2));
        TextFieldWidget contentField = new TextFieldWidget()
            .value(new StringValue.Dynamic(() -> editBlessingContent, val -> editBlessingContent = val))
            .setMaxLength(200);
        contentField.left(labelWidth)
            .top(fieldY)
            .size(fieldWidth, fieldHeight);
        // v1.7.19：TextFieldWidget 延迟到 PhantomItemSlot 之后添加（z-index 置顶）

        // ---- 猫猫币类型/数量 ----
        fieldY += spacing;
        editPanel.child(
            new TextWidget<>(IKey.str("猫猫币:")).left(8)
                .top(fieldY + 2));
        TextFieldWidget currencyField = new TextFieldWidget()
            .value(new StringValue.Dynamic(() -> editBlessingCurrency, val -> editBlessingCurrency = val))
            .setMaxLength(30);
        currencyField.left(labelWidth)
            .top(fieldY)
            .size(86, fieldHeight);
        currencyField.tooltipBuilder(t -> {
            t.addLine(IKey.str("货币 ID：neko / shimmeringNeko"));
            t.addLine(IKey.str(EnumChatFormatting.GRAY + "留空 = 无猫猫币附件"));
        });
        currencyField.tooltipAutoUpdate(true);
        // v1.7.19：TextFieldWidget 延迟到 PhantomItemSlot 之后添加（z-index 置顶）
        TextFieldWidget currencyAmountField = new TextFieldWidget()
            .value(new StringValue.Dynamic(() -> String.valueOf(editBlessingCurrencyAmount), val -> {
                try {
                    editBlessingCurrencyAmount = Integer.parseInt(val);
                } catch (NumberFormatException ignored) {}
            }))
            .setNumbers(0, Integer.MAX_VALUE);
        currencyAmountField.left(labelWidth + 90)
            .top(fieldY)
            .size(48, fieldHeight);
        currencyAmountField.tooltipBuilder(t -> t.addLine(IKey.str("数量（作为附件物品发放）")));
        currencyAmountField.tooltipAutoUpdate(true);
        // v1.7.19：TextFieldWidget 延迟到 PhantomItemSlot 之后添加（z-index 置顶）

        // ---- 附件物品槽（PhantomItemSlot 拖入配置；留空 = 无物品附件）----
        fieldY += spacing + 2;
        editPanel.child(
            new TextWidget<>(IKey.str("附件物品:")).left(8)
                .top(fieldY + 2));
        for (int i = 0; i < BLESSING_ITEM_SLOTS; i++) {
            PhantomItemSlot slot = new PhantomItemSlot().slot(new ModularSlot(editBlessingItemHandler, i));
            slot.left(labelWidth + i * 18)
                .top(fieldY - 2);
            editPanel.child(slot);
        }

        // v1.7.19：延迟添加的 TextFieldWidget（在所有 PhantomItemSlot 之后，确保渲染层位于物品槽之上）
        editPanel.child(senderField);
        editPanel.child(nameField);
        editPanel.child(dateField);
        editPanel.child(titleField);
        editPanel.child(contentField);
        editPanel.child(currencyField);
        editPanel.child(currencyAmountField);

        // ---- 保存 / 取消按钮 ----
        editPanel.child(
            new ButtonWidget<>().size(50, 16)
                .left(40)
                .bottom(8)
                .overlay(IKey.str("保存"))
                .onMouseTapped(mouse -> {
                    saveBlessingEdit();
                    requestClose.run();
                    return true;
                }));
        editPanel.child(
            new ButtonWidget<>().size(50, 16)
                .right(40)
                .bottom(8)
                .overlay(IKey.str("取消"))
                .onMouseTapped(mouse -> {
                    requestClose.run();
                    return true;
                }));

        return editPanel;
    }

    private void saveBlessingEdit() {
        try {
            // ---- 1. 发件人（随任意保存一并提交）----
            com.google.gson.JsonObject senderJson = new com.google.gson.JsonObject();
            senderJson.addProperty("sender", editBlessingSender);
            com.miaokatze.gtit.trade.v2.NekoEditNetworkManager.sendSaveBlessing("sender", senderJson.toString());

            // ---- 2. 当前祝福目标 ----
            com.google.gson.JsonObject json = new com.google.gson.JsonObject();
            if (editBlessingTarget != null && editBlessingTarget.startsWith("festival:")) {
                json.addProperty("name", editBlessingName);
                json.addProperty("monthDay", editBlessingMonthDay);
            }
            json.addProperty("title", editBlessingTitle);
            json.addProperty("content", editBlessingContent);
            json.addProperty("currency", editBlessingCurrency);
            json.addProperty("currencyAmount", editBlessingCurrencyAmount);
            com.google.gson.JsonArray items = new com.google.gson.JsonArray();
            for (int i = 0; i < BLESSING_ITEM_SLOTS; i++) {
                ItemStack stack = editBlessingItemHandler.getStackInSlot(i);
                if (stack == null || stack.getItem() == null) continue;
                com.google.gson.JsonObject itemJson = new com.google.gson.JsonObject();
                itemJson.addProperty("item", net.minecraft.item.Item.itemRegistry.getNameForObject(stack.getItem()));
                itemJson.addProperty("meta", stack.getItemDamage());
                itemJson.addProperty("amount", stack.stackSize);
                items.add(itemJson);
            }
            json.add("items", items);
            com.miaokatze.gtit.trade.v2.NekoEditNetworkManager
                .sendSaveBlessing(editBlessingTarget == null ? "birthday" : editBlessingTarget, json.toString());
        } catch (Exception e) {
            LOG.error("[NekoEdit] 保存祝福预设编辑失败", e);
        }
    }
}

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
import com.miaokatze.gtit.client.gui.NekoGuiTextures;
import com.miaokatze.gtit.gui.vm.edit.EditOverlayController.EditOverlayType;
import com.miaokatze.gtit.lottery.LotteryClientData;
import com.miaokatze.gtit.lottery.LotteryRarity;
import com.miaokatze.gtit.trade.v2.NekoBigItemStack;

/**
 * 抽奖卡池编辑器（v1.7.6 G2①；A01 蓝图 G4 抽取自 NekoVMGuiV2，方法体逐字搬移）
 * <p>
 * 池级编辑（新建/更新/删除）：图标槽 + 4 消耗需求槽缓冲区、保底阈值与保证稀有度字段。
 * 面板经 {@link #buildEditPanel} 注册到 {@link EditOverlayController}（LOTTERY_POOL 位，顺序冻结）。
 * <p>
 * <b>双端镜像构建</b>：面板在服务端同样构建（v1.7.17），类内不得有客户端专属 API 的静态引用。
 */
public final class LotteryPoolEditor {

    private static final Logger LOG = LogManager.getLogger("gtit");

    /** 覆盖层控制器（LOTTERY_POOL 位注册与显隐状态） */
    private final EditOverlayController overlay;
    /** 请求宿主执行完整关闭流程（closeEditOverlay） */
    private final Runnable requestClose;

    public LotteryPoolEditor(EditOverlayController overlay, Runnable requestClose) {
        this.overlay = overlay;
        this.requestClose = requestClose;
    }

    /**
     * 注册池编辑目标同步值（由宿主 registerSyncValues 在原位调用，命名通道不受树序影响）
     *
     * @param syncManager 面板同步管理器
     */
    public void registerSyncValues(PanelSyncManager syncManager) {
        // --- 池编辑目标（C2S：客户端设置池 id 或 "@new"，服务端加载图标/消耗需求物品到编辑缓冲区）---
        editPoolTargetSync = new StringSyncValue(() -> "", val -> {
            if (syncManager != null && !syncManager.isClient() && val != null && !val.isEmpty()) {
                loadPoolIntoEditBuffer(val);
            }
        });
        editPoolTargetSync.allowC2S();
        syncManager.syncValue("nekoV2EditPoolTarget", editPoolTargetSync);
    }

    /** 池编辑消耗需求槽位数（图标槽除外） */
    private static final int POOL_COST_SLOTS = 4;
    /** 池编辑目标同步值特殊标记：新建池模式（服务端清空编辑缓冲区） */
    private static final String POOL_TARGET_NEW = "@new";
    /** 池编辑目标同步值（C2S：池 id 或 {@link #POOL_TARGET_NEW}，服务端据此加载图标/消耗物品到编辑缓冲区） */
    private StringSyncValue editPoolTargetSync;
    /** 池编辑物品缓冲区（双端共享：slot 0=page 图标，slot 1-4=消耗需求物品） */
    private final ItemStackHandler editPoolItemHandler = new ItemStackHandler(1 + POOL_COST_SLOTS);
    /** 池编辑：卡池 ID（新建模式下可编辑，保存时作新池 id；现有池仅展示不可改） */
    private String editPoolId = "";
    /** 池编辑：是否新建模式（true=保存时创建新池，false=保存时更新现有池） */
    private boolean editPoolIsNew = false;
    /** 池编辑：显示名称 */
    private String editPoolName = "";
    /** 池编辑：保底是否启用 */
    private boolean editPoolPityEnabled = true;
    /** 池编辑：软保底阈值 */
    private int editPoolSoftPity = 30;
    /** 池编辑：硬保底阈值 */
    private int editPoolHardPity = 50;
    /** 池编辑：硬保底保证稀有度名（COMMON/RARE/EPIC/LEGENDARY） */
    private String editPoolGuaranteedRarity = "EPIC";

    public void beginEdit(LotteryClientData.PoolSummary pool) {
        if (pool == null || pool.id == null || pool.id.isEmpty()) return;
        editPoolIsNew = false;
        // 卡池 ID 仅定位用，编辑面板禁止修改（防保底记录/中奖历史悬空，见计划风险点）
        editPoolId = pool.id;
        editPoolName = pool.name != null ? pool.name : "";
        editPoolPityEnabled = pool.pityEnabled;
        editPoolSoftPity = pool.softPityThreshold;
        editPoolHardPity = pool.hardPityThreshold;
        editPoolGuaranteedRarity = pool.guaranteedRarity != null && !pool.guaranteedRarity.isEmpty()
            ? pool.guaranteedRarity
            : "EPIC";
        // 通知服务端加载该池的图标/消耗需求物品到编辑缓冲区
        if (editPoolTargetSync != null) {
            editPoolTargetSync.setValue(pool.id);
        }
        overlay.open(EditOverlayType.LOTTERY_POOL);
    }

    public void beginNew() {
        editPoolIsNew = true;
        editPoolId = "";
        editPoolName = "";
        editPoolPityEnabled = true;
        editPoolSoftPity = 30;
        editPoolHardPity = 50;
        editPoolGuaranteedRarity = "EPIC";
        // 通知服务端清空编辑缓冲区（新建模式无既有物品可加载）
        if (editPoolTargetSync != null) {
            editPoolTargetSync.setValue(POOL_TARGET_NEW);
        }
        overlay.open(EditOverlayType.LOTTERY_POOL);
    }

    private void loadPoolIntoEditBuffer(String target) {
        try {
            // 先整体清空（slot 0=图标，slot 1-4=消耗需求）
            for (int i = 0; i < editPoolItemHandler.getSlots(); i++) {
                editPoolItemHandler.setStackInSlot(i, null);
            }
            if (POOL_TARGET_NEW.equals(target)) return;
            com.miaokatze.gtit.lottery.LotteryPool pool = com.miaokatze.gtit.lottery.LotteryManager.INSTANCE
                .getPool(target);
            if (pool == null) return;
            // page 图标（数量固定 1，展示用）
            ItemStack icon = pool.toIconItemStack();
            if (icon != null) {
                editPoolItemHandler.setStackInSlot(0, icon);
            }
            // 消耗需求物品（stackSize=单次抽取消耗量；槽位有限，超出截断）
            int slot = 1;
            for (NekoBigItemStack cost : pool.getCostItems()) {
                if (slot > POOL_COST_SLOTS) break;
                if (cost == null || cost.getBaseStack() == null || cost.getStackSize() <= 0) continue;
                ItemStack stack = cost.getBaseStack()
                    .copy();
                stack.stackSize = cost.getStackSize();
                editPoolItemHandler.setStackInSlot(slot++, stack);
            }
            LOG.info("[NekoEdit] 已加载抽奖卡池到编辑缓冲区: {}", target);
        } catch (Exception e) {
            LOG.error("[NekoEdit] 加载抽奖卡池到编辑缓冲区失败: {}", target, e);
        }
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
        editPanel.setEnabledIf(w -> overlay.isCurrent(EditOverlayType.LOTTERY_POOL));

        // 标题（新建 / 编辑 + 池 id 动态切换）
        editPanel.child(
            new TextWidget<>(
                IKey.dynamic(() -> EnumChatFormatting.GOLD + (editPoolIsNew ? "新建抽奖卡池" : "编辑抽奖卡池（" + editPoolId + "）")))
                    .top(5)
                    .horizontalCenter());

        int fieldY = 22;
        int fieldHeight = 14;
        int labelWidth = 62;
        int fieldWidth = 132;

        // ---- 卡池 ID（新建模式可编辑；现有池只读展示，防保底记录/中奖历史悬空）----
        editPanel.child(
            new TextWidget<>(IKey.str("卡池ID:")).left(8)
                .top(fieldY + 2));

        TextFieldWidget idField = new TextFieldWidget()
            .value(new StringValue.Dynamic(() -> editPoolId, val -> editPoolId = val.trim()))
            .setMaxLength(30);
        idField.left(labelWidth)
            .top(fieldY)
            .size(fieldWidth, fieldHeight);
        idField.tooltipBuilder(t -> {
            t.addLine(IKey.str("卡池唯一标识（仅字母/数字/下划线/连字符）"));
            t.addLine(IKey.str(EnumChatFormatting.GRAY + "保存后不可修改"));
        });
        idField.tooltipAutoUpdate(true);
        // 仅新建模式显示输入框（现有池改走下方只读文本）
        idField.setEnabledIf(w -> editPoolIsNew);
        // v1.7.19：TextFieldWidget 延迟到 PhantomItemSlot 之后添加（z-index 置顶）
        // 现有池：只读文本展示 id（与输入框互斥显示）
        editPanel.child(
            new TextWidget<>(IKey.dynamic(() -> EnumChatFormatting.GRAY + editPoolId)).left(labelWidth)
                .top(fieldY + 2)
                .setEnabledIf(w -> !editPoolIsNew));

        // ---- 名称 ----
        fieldY += 17;
        editPanel.child(
            new TextWidget<>(IKey.str("名称:")).left(8)
                .top(fieldY + 2));

        TextFieldWidget nameField = new TextFieldWidget()
            .value(new StringValue.Dynamic(() -> editPoolName, val -> editPoolName = val))
            .setMaxLength(40);
        nameField.left(labelWidth)
            .top(fieldY)
            .size(fieldWidth, fieldHeight);
        nameField.tooltipBuilder(t -> t.addLine(IKey.str("卡池显示名称（留空则保持原名）")));
        nameField.tooltipAutoUpdate(true);
        // v1.7.19：TextFieldWidget 延迟到 PhantomItemSlot 之后添加（z-index 置顶）

        // ---- page 图标（PhantomItemSlot 拖入配置，支持 NBT；空槽 = 回退货币图标）----
        fieldY += 17;
        editPanel.child(
            new TextWidget<>(IKey.str("图标:")).left(8)
                .top(fieldY + 2));

        PhantomItemSlot iconSlot = new PhantomItemSlot().slot(new ModularSlot(editPoolItemHandler, 0));
        iconSlot.left(labelWidth)
            .top(fieldY - 2);
        iconSlot.tooltipBuilder(t -> {
            t.addLine(IKey.str("拖入物品作为池标签图标（支持 NBT）"));
            t.addLine(IKey.str(EnumChatFormatting.GRAY + "留空 = 按池消耗货币显示缺省币图标"));
        });
        iconSlot.tooltipAutoUpdate(true);
        editPanel.child(iconSlot);

        // ---- 消耗需求（PhantomItemSlot×4：货币物品扣团队钱包、普通物品扣机器输入槽）----
        fieldY += 21; // 图标槽高 18，多留间距
        editPanel.child(
            new TextWidget<>(IKey.str("消耗需求:")).left(8)
                .top(fieldY + 2));

        for (int i = 0; i < POOL_COST_SLOTS; i++) {
            PhantomItemSlot costSlot = new PhantomItemSlot().slot(new ModularSlot(editPoolItemHandler, 1 + i));
            costSlot.left(labelWidth + i * 20)
                .top(fieldY - 2);
            costSlot.tooltipBuilder(t -> {
                t.addLine(IKey.str("单次抽取消耗（数量=槽内堆叠数）"));
                t.addLine(IKey.str(EnumChatFormatting.GRAY + "猫猫币物品 → 团队钱包扣除"));
                t.addLine(IKey.str(EnumChatFormatting.GRAY + "普通物品 → 机器输入槽扣除"));
                t.addLine(IKey.str(EnumChatFormatting.DARK_GRAY + "留空全部 = 免费"));
            });
            costSlot.tooltipAutoUpdate(true);
            editPanel.child(costSlot);
        }

        // v1.7.19：延迟添加的 TextFieldWidget（在所有 PhantomItemSlot 之后，确保渲染层位于物品槽之上）
        editPanel.child(idField);
        editPanel.child(nameField);

        // ---- 保底启用（点击切换）----
        fieldY += 21; // 消耗槽高 18，多留间距
        editPanel.child(
            new TextWidget<>(IKey.str("保底启用:")).left(8)
                .top(fieldY + 2));

        ButtonWidget<?> pityToggle = new ButtonWidget<>().left(labelWidth)
            .top(fieldY)
            .size(fieldWidth, fieldHeight)
            .background(NekoGuiTextures.TEXT_FIELD_BACKGROUND)
            .overlay(
                IKey.dynamic(
                    () -> editPoolPityEnabled ? EnumChatFormatting.GREEN + "启用" : EnumChatFormatting.RED + "停用"))
            .tooltipBuilder(t -> t.addLine(IKey.str("点击切换保底机制启用/停用")))
            .onMouseTapped(mouse -> {
                editPoolPityEnabled = !editPoolPityEnabled;
                return true;
            });
        pityToggle.tooltipAutoUpdate(true);
        editPanel.child(pityToggle);

        // ---- 软保底阈值 ----
        fieldY += 17;
        editPanel.child(
            new TextWidget<>(IKey.str("软保底:")).left(8)
                .top(fieldY + 2));

        TextFieldWidget softPityField = new TextFieldWidget()
            .value(new StringValue.Dynamic(() -> String.valueOf(editPoolSoftPity), val -> {
                try {
                    editPoolSoftPity = Integer.parseInt(val);
                } catch (NumberFormatException ignored) {}
            }))
            .setNumbers(0, Integer.MAX_VALUE);
        softPityField.left(labelWidth)
            .top(fieldY)
            .size(fieldWidth, fieldHeight);
        softPityField.tooltipBuilder(t -> t.addLine(IKey.str("软保底起始抽数（此后高稀有概率逐抽递增）")));
        softPityField.tooltipAutoUpdate(true);
        editPanel.child(softPityField);

        // ---- 硬保底阈值 ----
        fieldY += 17;
        editPanel.child(
            new TextWidget<>(IKey.str("硬保底:")).left(8)
                .top(fieldY + 2));

        TextFieldWidget hardPityField = new TextFieldWidget()
            .value(new StringValue.Dynamic(() -> String.valueOf(editPoolHardPity), val -> {
                try {
                    editPoolHardPity = Integer.parseInt(val);
                } catch (NumberFormatException ignored) {}
            }))
            .setNumbers(0, Integer.MAX_VALUE);
        hardPityField.left(labelWidth)
            .top(fieldY)
            .size(fieldWidth, fieldHeight);
        hardPityField.tooltipBuilder(t -> t.addLine(IKey.str("硬保底抽数（达到后必出保证稀有度，0=关闭）")));
        hardPityField.tooltipAutoUpdate(true);
        editPanel.child(hardPityField);

        // ---- 保证稀有度（点击循环切换：普通→稀有→史诗→传说）----
        fieldY += 17;
        editPanel.child(
            new TextWidget<>(IKey.str("保证稀有度:")).left(8)
                .top(fieldY + 2));

        ButtonWidget<?> rarityButton = new ButtonWidget<>().left(labelWidth)
            .top(fieldY)
            .size(fieldWidth, fieldHeight)
            .background(NekoGuiTextures.TEXT_FIELD_BACKGROUND)
            .overlay(IKey.dynamic(this::poolGuaranteedRarityDisplay))
            .tooltipBuilder(t -> t.addLine(IKey.str("硬保底触发时保证的稀有度（点击循环切换）")))
            .onMouseTapped(mouse -> {
                cyclePoolGuaranteedRarity();
                return true;
            });
        rarityButton.tooltipAutoUpdate(true);
        editPanel.child(rarityButton);

        // ---- 保存 / 删除 / 取消按钮 ----
        editPanel.child(
            new ButtonWidget<>().size(50, 16)
                .left(14)
                .bottom(8)
                .overlay(IKey.str("保存"))
                .onMouseTapped(mouse -> {
                    saveLotteryPoolEdit();
                    requestClose.run();
                    return true;
                }));
        // 删除按钮：仅编辑现有池时显示（新建模式无池可删）
        ButtonWidget<?> deleteButton = new ButtonWidget<>().size(50, 16)
            .left(80)
            .bottom(8)
            .overlay(IKey.str(EnumChatFormatting.RED + "删除"))
            .onMouseTapped(mouse -> {
                deleteLotteryPoolEdit();
                requestClose.run();
                return true;
            });
        deleteButton.tooltipBuilder(t -> {
            t.addLine(IKey.str(EnumChatFormatting.RED + "删除本卡池"));
            t.addLine(IKey.str(EnumChatFormatting.GRAY + "至少保留一个卡池（服务端校验）"));
        });
        deleteButton.tooltipAutoUpdate(true);
        deleteButton.setEnabledIf(w -> !editPoolIsNew);
        editPanel.child(deleteButton);
        editPanel.child(
            new ButtonWidget<>().size(50, 16)
                .right(14)
                .bottom(8)
                .overlay(IKey.str("取消"))
                .onMouseTapped(mouse -> {
                    requestClose.run();
                    return true;
                }));

        return editPanel;
    }

    private void cyclePoolGuaranteedRarity() {
        LotteryRarity[] values = LotteryRarity.values();
        LotteryRarity current = LotteryRarity.fromString(editPoolGuaranteedRarity);
        editPoolGuaranteedRarity = values[(current.ordinal() + 1) % values.length].name();
    }

    private String poolGuaranteedRarityDisplay() {
        LotteryRarity rarity = LotteryRarity.fromString(editPoolGuaranteedRarity);
        return rarity.getColor() + rarity.getDisplayName() + EnumChatFormatting.GRAY + "（" + rarity.name() + "）";
    }

    private void saveLotteryPoolEdit() {
        try {
            com.google.gson.JsonObject json = new com.google.gson.JsonObject();
            if (editPoolIsNew) {
                String id = editPoolId.trim();
                // 客户端提前拦截空 id（合法性/唯一性由服务端最终校验）
                if (id.isEmpty()) return;
                json.addProperty("id", id);
            } else if (editPoolId.isEmpty()) {
                return;
            }
            json.addProperty("name", editPoolName);
            // page 图标（slot 0；空槽不发 icon 键 = 服务端清空图标，GUI 回退货币图标）
            ItemStack iconStack = editPoolItemHandler.getStackInSlot(0);
            if (iconStack != null && iconStack.getItem() != null) {
                json.add("icon", EditJsonCodec.itemStackToEditJson(iconStack));
            }
            // 消耗需求（slot 1-4，跳过空槽）
            com.google.gson.JsonArray costArray = new com.google.gson.JsonArray();
            for (int i = 1; i <= POOL_COST_SLOTS; i++) {
                ItemStack stack = editPoolItemHandler.getStackInSlot(i);
                if (stack != null && stack.getItem() != null && stack.stackSize > 0) {
                    costArray.add(EditJsonCodec.itemStackToEditJson(stack));
                }
            }
            json.add("costItems", costArray);
            // 保底字段
            json.addProperty("pityEnabled", editPoolPityEnabled);
            json.addProperty("softPityThreshold", editPoolSoftPity);
            json.addProperty("hardPityThreshold", editPoolHardPity);
            json.addProperty("guaranteedRarity", editPoolGuaranteedRarity);
            if (editPoolIsNew) {
                com.miaokatze.gtit.trade.v2.NekoEditNetworkManager.sendCreateLotteryPool(json.toString());
            } else {
                com.miaokatze.gtit.trade.v2.NekoEditNetworkManager.sendSaveLotteryPool(editPoolId, json.toString());
            }
        } catch (Exception e) {
            LOG.error("[NekoEdit] 保存抽奖卡池编辑失败", e);
        }
    }

    private void deleteLotteryPoolEdit() {
        if (editPoolIsNew || editPoolId.isEmpty()) return;
        com.miaokatze.gtit.trade.v2.NekoEditNetworkManager.sendDeleteLotteryPool(editPoolId);
    }
}

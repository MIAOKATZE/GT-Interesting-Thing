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
import com.miaokatze.gtit.lottery.LotteryEntry;
import com.miaokatze.gtit.lottery.LotteryRarity;

/**
 * 轮盘条目编辑器（v1.7.0 目标 4；A01 蓝图 G4 抽取自 NekoVMGuiV2，方法体逐字搬移）
 * <p>
 * 单槽物品奖品缓冲区 + 货币/数量区间/权重/稀有度字段，稀有度循环切换。
 * 面板经 {@link #buildEditPanel} 注册到 {@link EditOverlayController}（LOTTERY 位，顺序冻结）。
 * <p>
 * <b>双端镜像构建</b>：面板在服务端同样构建（v1.7.17），类内不得有客户端专属 API 的静态引用。
 */
public final class LotteryEntryEditor {

    private static final Logger LOG = LogManager.getLogger("gtit");

    /** 覆盖层控制器（LOTTERY 位注册与显隐状态） */
    private final EditOverlayController overlay;
    /** 请求宿主执行完整关闭流程（closeEditOverlay） */
    private final Runnable requestClose;

    public LotteryEntryEditor(EditOverlayController overlay, Runnable requestClose) {
        this.overlay = overlay;
        this.requestClose = requestClose;
    }

    /**
     * 注册抽奖条目编辑目标同步值（由宿主 registerSyncValues 在原位调用，命名通道不受树序影响）
     *
     * @param syncManager 面板同步管理器
     */
    public void registerSyncValues(PanelSyncManager syncManager) {
        // --- 抽奖编辑目标（C2S：客户端设置 "<poolId>:<entryId>"，服务端加载条目物品到编辑缓冲区）---
        editLotteryTargetSync = new StringSyncValue(() -> "", val -> {
            if (syncManager != null && !syncManager.isClient() && val != null && !val.isEmpty()) {
                loadLotteryEntryIntoEditBuffer(val);
            }
        });
        editLotteryTargetSync.allowC2S();
        syncManager.syncValue("nekoV2EditLotteryTarget", editLotteryTargetSync);
    }

    // --- 抽奖编辑（v1.7.0 目标 4：轮盘条目编辑） ---
    /** 抽奖编辑目标同步值（C2S："<poolId>:<entryId>"，服务端据此加载条目物品到编辑缓冲区） */
    private StringSyncValue editLotteryTargetSync;
    /** 抽奖编辑物品缓冲区（双端共享：slot 0=物品奖品） */
    private final ItemStackHandler editLotteryItemHandler = new ItemStackHandler(1);
    /** 抽奖编辑：条目标识（"<poolId>:<entryId>"，保存时原样发回服务端定位） */
    private String editLotteryEntryKey = "";
    /** 抽奖编辑：货币 ID（非空 = 货币奖品，保存时忽略物品槽） */
    private String editLotteryCurrency = "";
    /** 抽奖编辑：最小数量 */
    private int editLotteryMinAmount = 1;
    /** 抽奖编辑：最大数量 */
    private int editLotteryMaxAmount = 1;
    /** 抽奖编辑：权重（0 = 永不中出） */
    private int editLotteryWeight = 1;
    /** 抽奖编辑：稀有度名（COMMON/RARE/EPIC/LEGENDARY） */
    private String editLotteryRarity = "COMMON";

    public void beginEntry(LotteryClientData.PoolSummary pool, LotteryEntry entry, int slotIndex) {
        if (pool == null || entry == null || entry.getId() == null) return;
        // 记录条目标识（保存时原样发回服务端定位）
        editLotteryEntryKey = pool.id + ":" + entry.getId();
        // 数值字段从客户端缓存条目填充
        editLotteryCurrency = entry.getNekoCurrencyId() != null ? entry.getNekoCurrencyId() : "";
        editLotteryMinAmount = entry.getMinAmount();
        editLotteryMaxAmount = entry.getMaxAmount();
        editLotteryWeight = entry.getWeight();
        editLotteryRarity = entry.getRarity() != null ? entry.getRarity()
            .name() : "COMMON";
        // 通知服务端加载该条目的物品奖品到编辑缓冲区
        if (editLotteryTargetSync != null) {
            editLotteryTargetSync.setValue(editLotteryEntryKey);
        }
        overlay.open(EditOverlayType.LOTTERY);
    }

    private void loadLotteryEntryIntoEditBuffer(String target) {
        try {
            int sep = target.indexOf(':');
            if (sep <= 0 || sep >= target.length() - 1) return;
            String poolId = target.substring(0, sep);
            String entryId = target.substring(sep + 1);
            editLotteryItemHandler.setStackInSlot(0, null);
            com.miaokatze.gtit.lottery.LotteryPool pool = com.miaokatze.gtit.lottery.LotteryManager.INSTANCE
                .getPool(poolId);
            if (pool == null) return;
            LotteryEntry entry = pool.getEntryById(entryId);
            if (entry == null || entry.isNekoPrize()) return;
            // 数量固定 1（展示用）；实际出货数量由 minAmount/maxAmount 字段决定
            ItemStack stack = entry.toItemStack(1);
            if (stack != null) {
                editLotteryItemHandler.setStackInSlot(0, stack);
            }
            LOG.info("[NekoEdit] 已加载抽奖条目到编辑缓冲区: {}", target);
        } catch (Exception e) {
            LOG.error("[NekoEdit] 加载抽奖条目到编辑缓冲区失败: {}", target, e);
        }
    }

    public NekoDraggableEditPanel buildEditPanel() {
        NekoDraggableEditPanel editPanel = new NekoDraggableEditPanel();
        editPanel.size(200, 160);
        // v1.7.7 G2 迁移为主面板内嵌 ParentWidget 覆盖层后无默认背景，需手动补上 MC 风格背景
        editPanel.background(GuiTextures.MC_BACKGROUND);
        editPanel.leftRel(0.5f)
            .topRel(0.5f)
            .anchorLeft(0.5f)
            .anchorTop(0.5f);
        editPanel.setEnabledIf(w -> overlay.isCurrent(EditOverlayType.LOTTERY));

        // 标题（显示条目标识 "<poolId>:<entryId>"）
        editPanel.child(
            new TextWidget<>(IKey.dynamic(() -> EnumChatFormatting.GOLD + "编辑抽奖条目（" + editLotteryEntryKey + "）")).top(5)
                .horizontalCenter());

        int fieldY = 24;
        int fieldHeight = 14;
        int labelWidth = 65;
        int fieldWidth = 120;

        // ---- 货币 ID（非空 = 货币奖品，保存时忽略物品槽）----
        editPanel.child(
            new TextWidget<>(IKey.str(EnumChatFormatting.WHITE + "货币ID:")).left(8)
                .top(fieldY + 2));

        TextFieldWidget currencyField = new TextFieldWidget()
            .value(new StringValue.Dynamic(() -> editLotteryCurrency, val -> editLotteryCurrency = val.trim()))
            .setMaxLength(30);
        currencyField.left(labelWidth)
            .top(fieldY)
            .size(fieldWidth, fieldHeight);
        currencyField.tooltipBuilder(t -> {
            t.addLine(IKey.str("货币 ID（如 neko / shimmeringNeko）"));
            t.addLine(IKey.str(EnumChatFormatting.YELLOW + "非空 = 货币奖品，保存时忽略下方物品"));
            t.addLine(IKey.str(EnumChatFormatting.GRAY + "留空 = 物品奖品（需放入物品）"));
        });
        currencyField.tooltipAutoUpdate(true);
        // v1.7.19：TextFieldWidget 延迟到 PhantomItemSlot 之后添加（z-index 置顶）

        // ---- 物品奖品（PhantomItemSlot 拖入配置，支持 NBT）----
        fieldY += 17;
        editPanel.child(
            new TextWidget<>(IKey.str(EnumChatFormatting.WHITE + "物品:")).left(8)
                .top(fieldY + 2));

        PhantomItemSlot itemSlot = new PhantomItemSlot().slot(new ModularSlot(editLotteryItemHandler, 0));
        itemSlot.left(labelWidth)
            .top(fieldY - 2);
        itemSlot.tooltipBuilder(t -> {
            t.addLine(IKey.str("拖入物品作为奖品（支持 NBT）"));
            t.addLine(IKey.str(EnumChatFormatting.GRAY + "货币 ID 非空时本项被忽略"));
        });
        itemSlot.tooltipAutoUpdate(true);
        editPanel.child(itemSlot);

        // v1.7.19：延迟添加的 TextFieldWidget（在所有 PhantomItemSlot 之后，确保渲染层位于物品槽之上）
        editPanel.child(currencyField);

        // 物品槽旁状态提示（随货币 ID 是否填写动态切换）
        editPanel.child(
            new TextWidget<>(
                IKey.dynamic(
                    () -> editLotteryCurrency.isEmpty() ? EnumChatFormatting.GREEN + "物品奖品"
                        : EnumChatFormatting.YELLOW + "已忽略（货币奖品）")).left(labelWidth + 22)
                            .top(fieldY + 2));

        // ---- 最小数量 ----
        fieldY += 21; // 物品槽高 18，多留间距
        editPanel.child(
            new TextWidget<>(IKey.str(EnumChatFormatting.WHITE + "最小数量:")).left(8)
                .top(fieldY + 2));

        TextFieldWidget minField = new TextFieldWidget()
            .value(new StringValue.Dynamic(() -> String.valueOf(editLotteryMinAmount), val -> {
                try {
                    editLotteryMinAmount = Integer.parseInt(val);
                } catch (NumberFormatException ignored) {}
            }))
            .setNumbers(0, Integer.MAX_VALUE);
        minField.left(labelWidth)
            .top(fieldY)
            .size(fieldWidth, fieldHeight);
        editPanel.child(minField);

        // ---- 最大数量 ----
        fieldY += 17;
        editPanel.child(
            new TextWidget<>(IKey.str(EnumChatFormatting.WHITE + "最大数量:")).left(8)
                .top(fieldY + 2));

        TextFieldWidget maxField = new TextFieldWidget()
            .value(new StringValue.Dynamic(() -> String.valueOf(editLotteryMaxAmount), val -> {
                try {
                    editLotteryMaxAmount = Integer.parseInt(val);
                } catch (NumberFormatException ignored) {}
            }))
            .setNumbers(0, Integer.MAX_VALUE);
        maxField.left(labelWidth)
            .top(fieldY)
            .size(fieldWidth, fieldHeight);
        editPanel.child(maxField);

        // ---- 权重 ----
        fieldY += 17;
        editPanel.child(
            new TextWidget<>(IKey.str(EnumChatFormatting.WHITE + "权重:")).left(8)
                .top(fieldY + 2));

        TextFieldWidget weightField = new TextFieldWidget()
            .value(new StringValue.Dynamic(() -> String.valueOf(editLotteryWeight), val -> {
                try {
                    editLotteryWeight = Integer.parseInt(val);
                } catch (NumberFormatException ignored) {}
            }))
            .setNumbers(0, Integer.MAX_VALUE);
        weightField.left(labelWidth)
            .top(fieldY)
            .size(fieldWidth, fieldHeight);
        weightField.tooltipBuilder(t -> {
            t.addLine(IKey.str("抽取权重（相对值）"));
            t.addLine(IKey.str(EnumChatFormatting.GRAY + "0 = 永不中出"));
        });
        weightField.tooltipAutoUpdate(true);
        editPanel.child(weightField);

        // ---- 稀有度（点击循环切换：普通→稀有→史诗→传说）----
        fieldY += 17;
        editPanel.child(
            new TextWidget<>(IKey.str(EnumChatFormatting.WHITE + "稀有度:")).left(8)
                .top(fieldY + 2));

        ButtonWidget<?> rarityButton = new ButtonWidget<>().left(labelWidth)
            .top(fieldY)
            .size(fieldWidth, fieldHeight)
            .background(NekoGuiTextures.TEXT_FIELD_BACKGROUND)
            .overlay(IKey.dynamic(this::lotteryRarityDisplay))
            .tooltipBuilder(t -> t.addLine(IKey.str("点击循环切换稀有度（普通→稀有→史诗→传说）")))
            .onMouseTapped(mouse -> {
                cycleLotteryRarity();
                return true;
            });
        rarityButton.tooltipAutoUpdate(true);
        editPanel.child(rarityButton);

        // ---- 保存 / 取消按钮 ----
        editPanel.child(
            new ButtonWidget<>().size(50, 16)
                .left(30)
                .bottom(8)
                .overlay(IKey.str("保存"))
                .onMouseTapped(mouse -> {
                    saveLotteryEdit();
                    requestClose.run();
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

    private void cycleLotteryRarity() {
        LotteryRarity[] values = LotteryRarity.values();
        LotteryRarity current = LotteryRarity.fromString(editLotteryRarity);
        editLotteryRarity = values[(current.ordinal() + 1) % values.length].name();
    }

    private String lotteryRarityDisplay() {
        LotteryRarity rarity = LotteryRarity.fromString(editLotteryRarity);
        return rarity.getColor() + rarity.getDisplayName() + EnumChatFormatting.GRAY + "（" + rarity.name() + "）";
    }

    private void saveLotteryEdit() {
        try {
            if (editLotteryEntryKey.isEmpty()) return;
            com.google.gson.JsonObject json = new com.google.gson.JsonObject();
            json.addProperty("nekoCurrencyId", editLotteryCurrency);
            // 物品奖品字段：取自 PhantomItemSlot（货币 ID 非空时服务端忽略并清空）
            ItemStack stack = editLotteryItemHandler.getStackInSlot(0);
            if (stack != null && stack.getItem() != null) {
                com.google.gson.JsonObject itemJson = EditJsonCodec.itemStackToEditJson(stack);
                json.addProperty(
                    "item",
                    itemJson.get("item")
                        .getAsString());
                json.addProperty(
                    "meta",
                    itemJson.get("meta")
                        .getAsInt());
                if (itemJson.has("nbtBase64")) {
                    json.addProperty(
                        "nbtBase64",
                        itemJson.get("nbtBase64")
                            .getAsString());
                }
            } else {
                json.addProperty("item", "");
                json.addProperty("meta", 0);
            }
            // 数量区间（出货数量在 [min, max] 均匀随机；服务端会再做下限钳制）
            json.addProperty("minAmount", editLotteryMinAmount);
            json.addProperty("maxAmount", editLotteryMaxAmount);
            json.addProperty("weight", editLotteryWeight);
            json.addProperty("rarity", editLotteryRarity);
            com.miaokatze.gtit.trade.v2.NekoEditNetworkManager
                .sendSaveLotteryEntry(editLotteryEntryKey, json.toString());
        } catch (Exception e) {
            LOG.error("[NekoEdit] 保存抽奖编辑失败", e);
        }
    }
}

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
 * 每日在线档位编辑器（v1.7.7 G5②；A01 蓝图 G3 抽取自 NekoVMGuiV2，方法体逐字搬移）
 * <p>
 * 档位三态编辑（add/update/remove）+ 单槽物品缓冲区与货币字段。
 * 面板经 {@link #buildEditPanel} 注册到 {@link EditOverlayController}（ONLINE_TIER 位，顺序冻结）。
 * <p>
 * <b>双端镜像构建</b>：面板在服务端同样构建（v1.7.17），类内不得有客户端专属 API 的静态引用。
 */
public final class OnlineTierEditor {

    private static final Logger LOG = LogManager.getLogger("gtit");

    /** 覆盖层控制器（ONLINE_TIER 位注册与显隐状态） */
    private final EditOverlayController overlay;
    /** 请求宿主执行完整关闭流程（closeEditOverlay） */
    private final Runnable requestClose;

    public OnlineTierEditor(EditOverlayController overlay, Runnable requestClose) {
        this.overlay = overlay;
        this.requestClose = requestClose;
    }

    // --- 每日在线档位编辑（v1.7.7 G5②） ---
    /** 在线档位编辑目标同步值（C2S："<seconds>"，服务端据此加载档位物品奖励到编辑缓冲区） */
    private StringSyncValue editOnlineTargetSync;
    /** 在线档位编辑物品缓冲区（双端共享：slot 0=档位物品奖励） */
    private final ItemStackHandler editOnlineItemHandler = new ItemStackHandler(1);
    /** 在线档位编辑：原档位所需秒数（定位用，0=新建模式） */
    private int editOnlineOriginalSeconds = 0;
    /** 在线档位编辑：当前编辑的所需秒数 */
    private int editOnlineSeconds = 1800;
    /** 在线档位编辑：货币 ID */
    private String editOnlineCurrency = "neko";
    /** 在线档位编辑：货币数量 */
    private int editOnlineAmount = 0;

    /**
     * 注册在线档位编辑目标同步值（由宿主 registerSyncValues 在原位调用，命名通道不受树序影响）
     *
     * @param syncManager 面板同步管理器
     */
    public void registerSyncValues(PanelSyncManager syncManager) {
        // --- 在线档位编辑目标（C2S：客户端设置 "<seconds>"，服务端加载档位物品奖励到编辑缓冲区，v1.7.7 G5②）---
        editOnlineTargetSync = new StringSyncValue(() -> "", val -> {
            if (syncManager != null && !syncManager.isClient() && val != null && !val.isEmpty()) {
                loadOnlineTierIntoEditBuffer(val);
            }
        });
        editOnlineTargetSync.allowC2S();
        syncManager.syncValue("nekoV2EditOnlineTarget", editOnlineTargetSync);
    }

    public void beginTier(com.miaokatze.gtit.signin.OnlineTimeRewardTier tier) {
        if (tier == null) return;
        editOnlineOriginalSeconds = tier.getRequiredSeconds();
        editOnlineSeconds = tier.getRequiredSeconds();
        editOnlineCurrency = tier.getCurrencyId() != null ? tier.getCurrencyId() : "neko";
        editOnlineAmount = tier.getCurrencyAmount();
        // 通知服务端加载该档位的物品奖励到编辑缓冲区
        if (editOnlineTargetSync != null) {
            editOnlineTargetSync.setValue(String.valueOf(editOnlineOriginalSeconds));
        }
        overlay.open(EditOverlayType.ONLINE_TIER);
    }

    private void loadOnlineTierIntoEditBuffer(String target) {
        try {
            int seconds = Integer.parseInt(target);
            editOnlineItemHandler.setStackInSlot(0, null);
            for (com.miaokatze.gtit.signin.OnlineTimeRewardTier tier : com.miaokatze.gtit.signin.OnlineTimeConfig
                .getTiers()) {
                if (tier.getRequiredSeconds() != seconds) continue;
                if (tier.hasItemReward()) {
                    String[] parts = tier.getItemRewardId()
                        .split(":");
                    if (parts.length == 2) {
                        net.minecraft.item.Item itemObj = cpw.mods.fml.common.registry.GameRegistry
                            .findItem(parts[0], parts[1]);
                        if (itemObj != null) {
                            ItemStack stack = new ItemStack(
                                itemObj,
                                Math.max(1, tier.getItemRewardAmount()),
                                tier.getItemRewardMeta());
                            net.minecraft.nbt.NBTTagCompound nbt = com.miaokatze.gtit.util.NbtBase64Util
                                .nbtFromBase64(tier.getItemNbt());
                            if (nbt != null) {
                                // v1.7.7 G5②：copy() 返回 NBTBase，需强转为 NBTTagCompound 再写入物品
                                stack.setTagCompound((net.minecraft.nbt.NBTTagCompound) nbt.copy());
                            }
                            editOnlineItemHandler.setStackInSlot(0, stack);
                        }
                    }
                }
                break;
            }
            LOG.info("[NekoEdit] 已加载在线档位到编辑缓冲区: {}s", seconds);
        } catch (Exception e) {
            LOG.error("[NekoEdit] 加载在线档位到编辑缓冲区失败: {}", target, e);
        }
    }

    public NekoDraggableEditPanel buildEditPanel() {
        NekoDraggableEditPanel editPanel = new NekoDraggableEditPanel();
        editPanel.size(220, 150);
        // v1.7.7 G2 迁移为主面板内嵌 ParentWidget 覆盖层后无默认背景，需手动补上 MC 风格背景
        editPanel.background(GuiTextures.MC_BACKGROUND);
        editPanel.leftRel(0.5f)
            .topRel(0.5f)
            .anchorLeft(0.5f)
            .anchorTop(0.5f);
        editPanel.setEnabledIf(w -> overlay.isCurrent(EditOverlayType.ONLINE_TIER));

        // 标题
        editPanel.child(
            new TextWidget<>(IKey.str(EnumChatFormatting.GOLD + "编辑在线档位")).top(5)
                .horizontalCenter());

        int fieldY = 24;
        int fieldHeight = 14;
        int labelWidth = 75;
        int fieldWidth = 120;
        int spacing = 18;

        // 所需秒数
        TextWidget<?> secondsLabel = new TextWidget<>(IKey.str(EnumChatFormatting.WHITE + "所需秒数:"));
        secondsLabel.left(8)
            .top(fieldY + 2);
        editPanel.child(secondsLabel);

        TextFieldWidget secondsField = new TextFieldWidget()
            .value(new StringValue.Dynamic(() -> String.valueOf(editOnlineSeconds), val -> {
                try {
                    editOnlineSeconds = Integer.parseInt(val);
                } catch (NumberFormatException ignored) {}
            }))
            .setNumbers(1, Integer.MAX_VALUE);
        secondsField.left(labelWidth)
            .top(fieldY)
            .size(fieldWidth, fieldHeight);
        // v1.7.19：TextFieldWidget 延迟到 PhantomItemSlot 之后添加（z-index 置顶）

        // 货币类型
        fieldY += spacing;
        TextWidget<?> currencyLabel = new TextWidget<>(IKey.str(EnumChatFormatting.WHITE + "货币类型:"));
        currencyLabel.left(8)
            .top(fieldY + 2);
        editPanel.child(currencyLabel);

        TextFieldWidget currencyField = new TextFieldWidget()
            .value(new StringValue.Dynamic(() -> editOnlineCurrency, val -> editOnlineCurrency = val))
            .setMaxLength(30);
        currencyField.left(labelWidth)
            .top(fieldY)
            .size(fieldWidth, fieldHeight);
        // v1.7.19：TextFieldWidget 延迟到 PhantomItemSlot 之后添加（z-index 置顶）

        // 货币数量
        fieldY += spacing;
        TextWidget<?> amountLabel = new TextWidget<>(IKey.str(EnumChatFormatting.WHITE + "货币数量:"));
        amountLabel.left(8)
            .top(fieldY + 2);
        editPanel.child(amountLabel);

        TextFieldWidget amountField = new TextFieldWidget()
            .value(new StringValue.Dynamic(() -> String.valueOf(editOnlineAmount), val -> {
                try {
                    editOnlineAmount = Integer.parseInt(val);
                } catch (NumberFormatException ignored) {}
            }))
            .setNumbers(0, Integer.MAX_VALUE);
        amountField.left(labelWidth)
            .top(fieldY)
            .size(fieldWidth, fieldHeight);
        // v1.7.19：TextFieldWidget 延迟到 PhantomItemSlot 之后添加（z-index 置顶）

        // 物品奖励槽（PhantomItemSlot 拖入配置；留空 = 无物品奖励）
        fieldY += spacing;
        TextWidget<?> itemLabel = new TextWidget<>(IKey.str(EnumChatFormatting.WHITE + "物品奖励:"));
        itemLabel.left(8)
            .top(fieldY + 2);
        editPanel.child(itemLabel);

        PhantomItemSlot itemSlot = new PhantomItemSlot().slot(new ModularSlot(editOnlineItemHandler, 0));
        itemSlot.left(labelWidth)
            .top(fieldY - 2);
        editPanel.child(itemSlot);

        // v1.7.19：延迟添加的 TextFieldWidget（在所有 PhantomItemSlot 之后，确保渲染层位于物品槽之上）
        editPanel.child(secondsField);
        editPanel.child(currencyField);
        editPanel.child(amountField);

        // 保存 / 删除 / 取消 / 新增按钮
        editPanel.child(
            new ButtonWidget<>().size(40, 16)
                .left(14)
                .bottom(8)
                .overlay(IKey.str("保存"))
                .onMouseTapped(mouse -> {
                    saveOnlineTierEdit("update");
                    requestClose.run();
                    return true;
                }));
        editPanel.child(
            new ButtonWidget<>().size(40, 16)
                .left(62)
                .bottom(8)
                .overlay(IKey.str(EnumChatFormatting.RED + "删除"))
                .onMouseTapped(mouse -> {
                    saveOnlineTierEdit("remove");
                    requestClose.run();
                    return true;
                }));
        editPanel.child(
            new ButtonWidget<>().size(40, 16)
                .left(110)
                .bottom(8)
                .overlay(IKey.str("取消"))
                .onMouseTapped(mouse -> {
                    requestClose.run();
                    return true;
                }));
        editPanel.child(
            new ButtonWidget<>().size(40, 16)
                .left(158)
                .bottom(8)
                .overlay(IKey.str(EnumChatFormatting.GREEN + "新增"))
                .onMouseTapped(mouse -> {
                    saveOnlineTierEdit("add");
                    requestClose.run();
                    return true;
                }));

        return editPanel;
    }

    private void saveOnlineTierEdit(String operation) {
        try {
            com.google.gson.JsonObject json = new com.google.gson.JsonObject();
            json.addProperty("operation", operation);
            json.addProperty("seconds", editOnlineSeconds);

            ItemStack stack = editOnlineItemHandler.getStackInSlot(0);
            if (stack != null && stack.getItem() != null) {
                json.addProperty("item", net.minecraft.item.Item.itemRegistry.getNameForObject(stack.getItem()));
                json.addProperty("itemAmount", stack.stackSize);
                json.addProperty("itemMeta", stack.getItemDamage());
                json.addProperty("itemNbt", com.miaokatze.gtit.util.NbtBase64Util.nbtToBase64(stack.getTagCompound()));
            } else {
                json.addProperty("item", "");
                json.addProperty("itemAmount", 0);
                json.addProperty("itemMeta", 0);
                json.addProperty("itemNbt", "");
            }

            json.addProperty("currency", editOnlineCurrency);
            json.addProperty("amount", editOnlineAmount);

            com.miaokatze.gtit.trade.v2.NekoEditNetworkManager
                .sendSaveOnlineTier(String.valueOf(editOnlineOriginalSeconds), json.toString());
        } catch (Exception e) {
            LOG.error("[NekoEdit] 保存在线档位编辑失败", e);
        }
    }
}

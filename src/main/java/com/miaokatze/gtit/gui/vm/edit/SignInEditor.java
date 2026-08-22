package com.miaokatze.gtit.gui.vm.edit;

import java.util.List;

import net.minecraft.item.Item;
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
import com.miaokatze.gtit.signin.RewardItem;
import com.miaokatze.gtit.signin.SignInClientData;
import com.miaokatze.gtit.signin.SignInReward;

/**
 * 签到编辑器（A01 蓝图 G3 抽取自 NekoVMGuiV2，方法体逐字搬移）
 * <p>
 * 单面板三模式（tier/cumtier 阶梯 + monthly 每月全局）+ 逐日覆盖子面板（SIGNIN_DAY 覆盖层）：
 * 4 槽物品缓冲区、货币字段、递增开关/系数与工作日/周末子模式暂存互换。
 * 面板经 {@link #buildEditPanel}（SIGNIN 位）与 {@link #buildDayEditPanel}（SIGNIN_DAY 位）
 * 注册到 {@link EditOverlayController}，顺序冻结。
 * <p>
 * <b>双端镜像构建</b>：面板在服务端同样构建（v1.7.17），类内不得有客户端专属 API 的静态引用。
 */
public final class SignInEditor {

    private static final Logger LOG = LogManager.getLogger("gtit");

    /** 覆盖层控制器（SIGNIN/SIGNIN_DAY 位注册与显隐状态） */
    private final EditOverlayController overlay;
    /** 请求宿主执行完整关闭流程（closeEditOverlay） */
    private final Runnable requestClose;

    public SignInEditor(EditOverlayController overlay, Runnable requestClose) {
        this.overlay = overlay;
        this.requestClose = requestClose;
    }

    // --- 签到编辑（v1.7.8 任务5+6：连续/累计阶梯增删改 + 逐日覆盖 + 每月全局配置） ---
    /** 签到编辑目标同步值（C2S："tier:<days>"/"cumtier:<days>"，服务端据此加载阶梯物品奖励到编辑缓冲区） */
    private StringSyncValue editSignInTargetSync;
    /** 签到编辑物品缓冲区（双端共享：v1.7.8 任务6 由 1 槽扩为 4 槽，对应每奖励最多 4 个物品；SIGNIN 面板阶梯/每月模式共用） */
    private final ItemStackHandler editSignInItemHandler = new ItemStackHandler(4);
    /** 逐日覆盖编辑物品缓冲区（双端共享：4 槽；与 SIGNIN 面板缓冲区独立，避免同一 handler 槽位重复注册） */
    private final ItemStackHandler editSignInDayItemHandler = new ItemStackHandler(4);
    /** 签到编辑模式："tier"=连续阶梯 / "cumtier"=累计阶梯 / "monthly"=每月全局配置（逐日覆盖走 SIGNIN_DAY 覆盖层） */
    private String editSignInMode = "tier";
    /** 签到编辑：原阶梯天数（定位用；>0=编辑已有阶梯，-1=新增模式） */
    private int editSignInOriginalDays = -1;
    /** 签到编辑：当前编辑的阶梯天数（update 允许改天数；add 时即新阶梯天数） */
    private int editSignInDays = 7;
    /** 签到编辑：货币 ID（阶梯/逐日模式） */
    private String editSignInCurrency = "neko";
    /** 签到编辑：货币数量（阶梯/逐日模式） */
    private int editSignInAmount = 0;
    /** 签到编辑：逐日覆盖目标日期（"yyyy-MM-dd"，SIGNIN_DAY 覆盖层；日号即覆盖键） */
    private String editSignInDayDate = "";
    /** 签到编辑：逐日覆盖目标日号（1..31） */
    private int editSignInDay = 1;
    /** 签到编辑：每月全局-递增开关（v1.7.8 起默认 false=不递增） */
    private boolean editSignInIncrementEnabled = false;
    /** 签到编辑：每月全局-连续递增系数（字符串暂存，保存时解析） */
    private String editSignInIncrement = "1.0";
    /** 签到编辑：每月全局-工作日默认货币 ID */
    private String editSignInWeekdayCurrency = "neko";
    /** 签到编辑：每月全局-工作日默认货币数量 */
    private int editSignInWeekdayAmount = 10;
    /** 签到编辑：每月全局-周末默认货币 ID */
    private String editSignInWeekendCurrency = "neko";
    /** 签到编辑：每月全局-周末默认货币数量 */
    private int editSignInWeekendAmount = 20;
    /** 签到编辑：每月全局-物品子模式（false=编辑工作日默认奖励 / true=编辑周末默认奖励，共用 4 物品槽） */
    private boolean editSignInMonthlyWeekend = false;
    /** 签到编辑：每月全局-非激活子模式的物品暂存（4 槽；激活子模式直接读写编辑缓冲区） */
    private final ItemStack[] editSignInMonthlyStash = new ItemStack[4];

    /**
     * 注册签到编辑目标同步值（由宿主 registerSyncValues 在原位调用，命名通道不受树序影响）
     *
     * @param syncManager 面板同步管理器
     */
    public void registerSyncValues(PanelSyncManager syncManager) {
        // --- 签到编辑目标（C2S：客户端设置 "tier:<days>"/"cumtier:<days>"，服务端加载连续/累计阶梯物品奖励到编辑缓冲区）---
        editSignInTargetSync = new StringSyncValue(() -> "", val -> {
            if (syncManager != null && !syncManager.isClient() && val != null && !val.isEmpty()) {
                loadSignInTierIntoEditBuffer(val);
            }
        });
        editSignInTargetSync.allowC2S();
        syncManager.syncValue("nekoV2EditSignInTarget", editSignInTargetSync);
    }

    public void beginTier(com.miaokatze.gtit.signin.SignInRewardTier tier, boolean cumulative) {
        editSignInMode = cumulative ? "cumtier" : "tier";
        if (tier != null) {
            // 编辑模式：记录原天数（定位用）并填充货币字段
            editSignInOriginalDays = tier.getRequiredDays();
            editSignInDays = tier.getRequiredDays();
            SignInReward reward = tier.getReward();
            editSignInCurrency = reward != null && !reward.getCurrencyId()
                .isEmpty() ? reward.getCurrencyId() : "neko";
            editSignInAmount = reward != null ? reward.getCurrencyAmount() : 0;
            // 通知服务端加载该阶梯的物品奖励到编辑缓冲区
            if (editSignInTargetSync != null) {
                editSignInTargetSync.setValue(editSignInMode + ":" + editSignInOriginalDays);
            }
        } else {
            // 新增模式：清空字段与物品缓冲区（客户端清空后经 PhantomItemSlot 同步到服务端）
            editSignInOriginalDays = -1;
            editSignInDays = 7;
            editSignInCurrency = "neko";
            editSignInAmount = 0;
            clearSignInItemBuffer(editSignInItemHandler);
        }
        overlay.open(EditOverlayType.SIGNIN);
    }

    public void beginDay(String date) {
        int day;
        try {
            day = Integer.parseInt(date.substring(8));
        } catch (Exception e) {
            LOG.warn("[NekoEdit] 逐日覆盖日期非法: {}", date);
            return;
        }
        editSignInDayDate = date;
        editSignInDay = day;
        SignInReward effective = SignInClientData.getEffectiveDayReward(date);
        editSignInCurrency = effective != null && !effective.getCurrencyId()
            .isEmpty() ? effective.getCurrencyId() : "neko";
        editSignInAmount = effective != null ? effective.getCurrencyAmount() : 0;
        fillSignInItemBuffer(editSignInDayItemHandler, effective);
        overlay.open(EditOverlayType.SIGNIN_DAY);
    }

    public void beginGlobal() {
        editSignInMode = "monthly";
        editSignInIncrementEnabled = SignInClientData.isIncrementEnabled();
        editSignInIncrement = String.valueOf(SignInClientData.getConsecutiveIncrement());
        SignInReward weekday = SignInClientData.getWeekdayDefault();
        SignInReward weekend = SignInClientData.getWeekendDefault();
        editSignInWeekdayCurrency = weekday != null && !weekday.getCurrencyId()
            .isEmpty() ? weekday.getCurrencyId() : "neko";
        editSignInWeekdayAmount = weekday != null ? weekday.getCurrencyAmount() : 0;
        editSignInWeekendCurrency = weekend != null && !weekend.getCurrencyId()
            .isEmpty() ? weekend.getCurrencyId() : "neko";
        editSignInWeekendAmount = weekend != null ? weekend.getCurrencyAmount() : 0;
        // 物品子模式默认工作日：周末物品进暂存，工作日物品进编辑缓冲区
        editSignInMonthlyWeekend = false;
        stashSignInMonthlyItems(weekend);
        fillSignInItemBuffer(editSignInItemHandler, weekday);
        overlay.open(EditOverlayType.SIGNIN);
    }

    private void loadSignInTierIntoEditBuffer(String target) {
        try {
            boolean cumulative;
            String daysStr;
            if (target.startsWith("tier:")) {
                cumulative = false;
                daysStr = target.substring("tier:".length());
            } else if (target.startsWith("cumtier:")) {
                cumulative = true;
                daysStr = target.substring("cumtier:".length());
            } else {
                return;
            }
            int days = Integer.parseInt(daysStr);
            List<com.miaokatze.gtit.signin.SignInRewardTier> tiers = cumulative
                ? com.miaokatze.gtit.signin.DailySignInConfig.getCumulativeTiers()
                : com.miaokatze.gtit.signin.DailySignInConfig.getRewardTiers();
            SignInReward reward = null;
            for (com.miaokatze.gtit.signin.SignInRewardTier tier : tiers) {
                if (tier.getRequiredDays() == days) {
                    reward = tier.getReward();
                    break;
                }
            }
            fillSignInItemBuffer(editSignInItemHandler, reward);
            LOG.info("[NekoEdit] 已加载签到{}阶梯到编辑缓冲区: {}", cumulative ? "累计" : "连续", target);
        } catch (Exception e) {
            LOG.error("[NekoEdit] 加载签到阶梯到编辑缓冲区失败: {}", target, e);
        }
    }

    /** 清空签到编辑物品缓冲区（4 槽；客户端清空经 PhantomItemSlot 同步到服务端） */
    private void clearSignInItemBuffer(ItemStackHandler handler) {
        for (int i = 0; i < 4; i++) {
            handler.setStackInSlot(i, null);
        }
    }

    private void fillSignInItemBuffer(ItemStackHandler handler, SignInReward reward) {
        clearSignInItemBuffer(handler);
        if (reward == null) return;
        int slot = 0;
        for (RewardItem item : reward.getItems()) {
            if (slot >= 4) break;
            if (item == null || item.isEmpty()) continue;
            ItemStack stack = resolveSignInRewardStack(item);
            if (stack != null) {
                handler.setStackInSlot(slot, stack);
                slot++;
            }
        }
    }

    private void stashSignInMonthlyItems(SignInReward reward) {
        for (int i = 0; i < 4; i++) {
            editSignInMonthlyStash[i] = null;
        }
        if (reward == null) return;
        int slot = 0;
        for (RewardItem item : reward.getItems()) {
            if (slot >= 4) break;
            if (item == null || item.isEmpty()) continue;
            ItemStack stack = resolveSignInRewardStack(item);
            if (stack != null) {
                editSignInMonthlyStash[slot] = stack;
                slot++;
            }
        }
    }

    private void switchSignInMonthlySubMode(boolean weekend) {
        if (editSignInMonthlyWeekend == weekend) return;
        for (int i = 0; i < 4; i++) {
            ItemStack bufferStack = editSignInItemHandler.getStackInSlot(i);
            editSignInItemHandler.setStackInSlot(i, editSignInMonthlyStash[i]);
            editSignInMonthlyStash[i] = bufferStack;
        }
        editSignInMonthlyWeekend = weekend;
    }

    private ItemStack[] signInBufferSnapshot() {
        ItemStack[] stacks = new ItemStack[4];
        for (int i = 0; i < 4; i++) {
            stacks[i] = editSignInItemHandler.getStackInSlot(i);
        }
        return stacks;
    }

    private static ItemStack resolveSignInRewardStack(RewardItem rewardItem) {
        String[] parts = rewardItem.getItemId()
            .split(":");
        if (parts.length != 2) return null;
        Item itemObj = cpw.mods.fml.common.registry.GameRegistry.findItem(parts[0], parts[1]);
        if (itemObj == null) return null;
        ItemStack stack = new ItemStack(itemObj, Math.max(1, rewardItem.getAmount()), rewardItem.getMeta());
        net.minecraft.nbt.NBTTagCompound nbt = com.miaokatze.gtit.util.NbtBase64Util
            .nbtFromBase64(rewardItem.getNbtBase64());
        if (nbt != null) {
            // copy() 返回 NBTBase，需强转为 NBTTagCompound 再写入物品
            stack.setTagCompound((net.minecraft.nbt.NBTTagCompound) nbt.copy());
        }
        return stack;
    }

    public NekoDraggableEditPanel buildEditPanel() {
        NekoDraggableEditPanel editPanel = new NekoDraggableEditPanel();
        editPanel.size(200, 180);
        // v1.7.7 G2 迁移为主面板内嵌 ParentWidget 覆盖层后无默认背景，需手动补上 MC 风格背景
        editPanel.background(GuiTextures.MC_BACKGROUND);
        editPanel.leftRel(0.5f)
            .topRel(0.5f)
            .anchorLeft(0.5f)
            .anchorTop(0.5f);
        editPanel.setEnabledIf(w -> overlay.isCurrent(EditOverlayType.SIGNIN));

        int fieldHeight = 14;
        int labelWidth = 65;
        int fieldWidth = 120;

        // 标题（随模式/新增状态切换）
        editPanel.child(new TextWidget<>(IKey.dynamic(() -> {
            if ("monthly".equals(editSignInMode)) {
                return EnumChatFormatting.GOLD + "编辑每月签到全局配置";
            }
            String label = "cumtier".equals(editSignInMode) ? "累计" : "连续";
            return editSignInOriginalDays > 0
                ? EnumChatFormatting.GOLD + "编辑" + label + "阶梯（" + editSignInOriginalDays + " 天）"
                : EnumChatFormatting.GOLD + "新增" + label + "阶梯";
        })).top(5)
            .horizontalCenter());

        // ==================== 阶梯模式专属：所需天数 ====================

        TextWidget<?> daysLabel = new TextWidget<>(IKey.str(EnumChatFormatting.WHITE + "所需天数:"));
        daysLabel.left(8)
            .top(26);
        daysLabel.setEnabledIf(w -> !"monthly".equals(editSignInMode));
        editPanel.child(daysLabel);

        TextFieldWidget daysField = new TextFieldWidget()
            .value(new StringValue.Dynamic(() -> String.valueOf(editSignInDays), val -> {
                try {
                    editSignInDays = Integer.parseInt(val);
                } catch (NumberFormatException ignored) {}
            }))
            .setNumbers(1, Integer.MAX_VALUE);
        daysField.left(labelWidth)
            .top(24)
            .size(fieldWidth, fieldHeight);
        daysField.setEnabledIf(w -> !"monthly".equals(editSignInMode));
        // v1.7.19：TextFieldWidget 延迟到 PhantomItemSlot 之后添加（z-index 置顶，避免被物品槽覆盖）

        // ==================== 每月全局模式专属：递增开关 / 编辑目标 / 递增系数 ====================

        // 递增开关（v1.7.8 起默认 false=不递增；递增仅作用工作日/周末默认货币量，逐日覆盖天不递增）
        TextWidget<?> incToggleLabel = new TextWidget<>(IKey.str(EnumChatFormatting.WHITE + "递增开关:"));
        incToggleLabel.left(8)
            .top(26);
        incToggleLabel.setEnabledIf(w -> "monthly".equals(editSignInMode));
        editPanel.child(incToggleLabel);

        ButtonWidget<?> incToggle = new ButtonWidget<>().left(labelWidth)
            .top(24)
            .size(fieldWidth, fieldHeight)
            .background(NekoGuiTextures.TEXT_FIELD_BACKGROUND)
            .overlay(
                IKey.dynamic(
                    () -> editSignInIncrementEnabled ? EnumChatFormatting.GREEN + "启用" : EnumChatFormatting.RED + "停用"))
            .tooltipBuilder(t -> {
                t.addLine(IKey.str("点击切换每日默认奖励是否随连续天数递增"));
                t.addLine(IKey.str(EnumChatFormatting.GRAY + "递增仅作用工作日/周末默认货币量（逐日覆盖天不递增）"));
            })
            .tooltipAutoUpdate(true)
            .onMouseTapped(mouse -> {
                editSignInIncrementEnabled = !editSignInIncrementEnabled;
                return true;
            });
        incToggle.setEnabledIf(w -> "monthly".equals(editSignInMode));
        editPanel.child(incToggle);

        // 编辑目标（工作日/周末默认奖励子模式切换；货币字段与 4 物品槽随之整体切换）
        TextWidget<?> subModeLabel = new TextWidget<>(IKey.str(EnumChatFormatting.WHITE + "编辑目标:"));
        subModeLabel.left(8)
            .top(44);
        subModeLabel.setEnabledIf(w -> "monthly".equals(editSignInMode));
        editPanel.child(subModeLabel);

        ButtonWidget<?> subModeToggle = new ButtonWidget<>().left(labelWidth)
            .top(42)
            .size(fieldWidth, fieldHeight)
            .background(NekoGuiTextures.TEXT_FIELD_BACKGROUND)
            .overlay(
                IKey.dynamic(
                    () -> editSignInMonthlyWeekend ? EnumChatFormatting.AQUA + "周末默认奖励"
                        : EnumChatFormatting.YELLOW + "工作日默认奖励"))
            .tooltipBuilder(t -> {
                t.addLine(IKey.str("点击切换编辑工作日/周末默认奖励"));
                t.addLine(IKey.str(EnumChatFormatting.GRAY + "货币字段与 4 物品槽整体切换（切换不丢失内容）"));
            })
            .tooltipAutoUpdate(true)
            .onMouseTapped(mouse -> {
                switchSignInMonthlySubMode(!editSignInMonthlyWeekend);
                return true;
            });
        subModeToggle.setEnabledIf(w -> "monthly".equals(editSignInMode));
        editPanel.child(subModeToggle);

        // 递增系数（字符串暂存，保存时解析，非法回退 0）
        TextWidget<?> incLabel = new TextWidget<>(IKey.str(EnumChatFormatting.WHITE + "递增系数:"));
        incLabel.left(8)
            .top(62);
        incLabel.setEnabledIf(w -> "monthly".equals(editSignInMode));
        editPanel.child(incLabel);

        TextFieldWidget incField = new TextFieldWidget()
            .value(new StringValue.Dynamic(() -> editSignInIncrement, val -> editSignInIncrement = val))
            .setMaxLength(12);
        incField.left(labelWidth)
            .top(60)
            .size(fieldWidth, fieldHeight);
        incField.setEnabledIf(w -> "monthly".equals(editSignInMode));
        // v1.7.19：TextFieldWidget 延迟到 PhantomItemSlot 之后添加（z-index 置顶）

        // ==================== 公共区：货币 + 4 物品槽（每月模式随子模式分支绑定） ====================

        // 货币类型
        editPanel.child(
            new TextWidget<>(IKey.str(EnumChatFormatting.WHITE + "货币类型:")).left(8)
                .top(80));
        TextFieldWidget currencyField = new TextFieldWidget().value(new StringValue.Dynamic(() -> {
            if ("monthly".equals(editSignInMode)) {
                return editSignInMonthlyWeekend ? editSignInWeekendCurrency : editSignInWeekdayCurrency;
            }
            return editSignInCurrency;
        }, val -> {
            if ("monthly".equals(editSignInMode)) {
                if (editSignInMonthlyWeekend) {
                    editSignInWeekendCurrency = val;
                } else {
                    editSignInWeekdayCurrency = val;
                }
            } else {
                editSignInCurrency = val;
            }
        }))
            .setMaxLength(30);
        currencyField.left(labelWidth)
            .top(78)
            .size(fieldWidth, fieldHeight);
        // v1.7.19：TextFieldWidget 延迟到 PhantomItemSlot 之后添加（z-index 置顶）

        // 货币数量
        editPanel.child(
            new TextWidget<>(IKey.str(EnumChatFormatting.WHITE + "货币数量:")).left(8)
                .top(98));
        TextFieldWidget amountField = new TextFieldWidget().value(new StringValue.Dynamic(() -> {
            if ("monthly".equals(editSignInMode)) {
                return String.valueOf(editSignInMonthlyWeekend ? editSignInWeekendAmount : editSignInWeekdayAmount);
            }
            return String.valueOf(editSignInAmount);
        }, val -> {
            try {
                int parsed = Integer.parseInt(val);
                if ("monthly".equals(editSignInMode)) {
                    if (editSignInMonthlyWeekend) {
                        editSignInWeekendAmount = parsed;
                    } else {
                        editSignInWeekdayAmount = parsed;
                    }
                } else {
                    editSignInAmount = parsed;
                }
            } catch (NumberFormatException ignored) {}
        }))
            .setNumbers(0, Integer.MAX_VALUE);
        amountField.left(labelWidth)
            .top(96)
            .size(fieldWidth, fieldHeight);
        // v1.7.19：TextFieldWidget 延迟到 PhantomItemSlot 之后添加（z-index 置顶）

        // 物品奖励（4 槽，PhantomItemSlot 拖入配置；留空 = 无物品奖励）
        editPanel.child(
            new TextWidget<>(IKey.str(EnumChatFormatting.WHITE + "物品奖励:")).left(8)
                .top(118));
        for (int i = 0; i < 4; i++) {
            final int slotIndex = i;
            PhantomItemSlot slot = new PhantomItemSlot().slot(new ModularSlot(editSignInItemHandler, slotIndex));
            slot.left(labelWidth + slotIndex * 20)
                .top(114);
            editPanel.child(slot);
        }

        // v1.7.19：延迟添加的 TextFieldWidget（在所有 PhantomItemSlot 之后，确保渲染层位于物品槽之上）
        editPanel.child(daysField);
        editPanel.child(incField);
        editPanel.child(currencyField);
        editPanel.child(amountField);

        // ---- 阶梯模式：保存 / 删除 / 取消 / 新增（增删改三件套，照搬在线档位面板） ----
        ButtonWidget<?> saveBtn = new ButtonWidget<>().size(40, 16)
            .left(14)
            .bottom(8)
            .overlay(IKey.str("保存"))
            .onMouseTapped(mouse -> {
                saveSignInEdit("update");
                requestClose.run();
                return true;
            });
        // 新增模式（originalDays=-1）无原档可定位，隐藏保存/删除
        saveBtn.setEnabledIf(w -> !"monthly".equals(editSignInMode) && editSignInOriginalDays > 0);
        editPanel.child(saveBtn);

        ButtonWidget<?> deleteBtn = new ButtonWidget<>().size(40, 16)
            .left(62)
            .bottom(8)
            .overlay(IKey.str(EnumChatFormatting.RED + "删除"))
            .onMouseTapped(mouse -> {
                saveSignInEdit("remove");
                requestClose.run();
                return true;
            });
        deleteBtn.setEnabledIf(w -> !"monthly".equals(editSignInMode) && editSignInOriginalDays > 0);
        editPanel.child(deleteBtn);

        ButtonWidget<?> tierCancelBtn = new ButtonWidget<>().size(40, 16)
            .left(110)
            .bottom(8)
            .overlay(IKey.str("取消"))
            .onMouseTapped(mouse -> {
                requestClose.run();
                return true;
            });
        tierCancelBtn.setEnabledIf(w -> !"monthly".equals(editSignInMode));
        editPanel.child(tierCancelBtn);

        ButtonWidget<?> addBtn = new ButtonWidget<>().size(40, 16)
            .left(158)
            .bottom(8)
            .overlay(IKey.str(EnumChatFormatting.GREEN + "新增"))
            .tooltipBuilder(t -> t.addLine(IKey.str(EnumChatFormatting.GRAY + "以当前天数/奖励追加为新阶梯")))
            .tooltipAutoUpdate(true)
            .onMouseTapped(mouse -> {
                saveSignInEdit("add");
                requestClose.run();
                return true;
            });
        addBtn.setEnabledIf(w -> !"monthly".equals(editSignInMode));
        editPanel.child(addBtn);

        // ---- 每月全局模式：保存 / 取消 ----
        ButtonWidget<?> monthlySaveBtn = new ButtonWidget<>().size(50, 16)
            .left(30)
            .bottom(8)
            .overlay(IKey.str("保存"))
            .onMouseTapped(mouse -> {
                saveSignInEdit("update");
                requestClose.run();
                return true;
            });
        monthlySaveBtn.setEnabledIf(w -> "monthly".equals(editSignInMode));
        editPanel.child(monthlySaveBtn);

        ButtonWidget<?> monthlyCancelBtn = new ButtonWidget<>().size(50, 16)
            .right(30)
            .bottom(8)
            .overlay(IKey.str("取消"))
            .onMouseTapped(mouse -> {
                requestClose.run();
                return true;
            });
        monthlyCancelBtn.setEnabledIf(w -> "monthly".equals(editSignInMode));
        editPanel.child(monthlyCancelBtn);

        return editPanel;
    }

    private void saveSignInEdit(String operation) {
        try {
            com.google.gson.JsonObject json = new com.google.gson.JsonObject();
            if ("monthly".equals(editSignInMode)) {
                // 每月全局：递增参数 + 工作日/周末默认奖励（字符串解析，非法回退 0）
                double increment;
                try {
                    increment = Double.parseDouble(editSignInIncrement);
                } catch (NumberFormatException e) {
                    increment = 0.0;
                }
                json.addProperty("incrementEnabled", editSignInIncrementEnabled);
                json.addProperty("consecutiveIncrement", increment);
                // 激活子模式物品在编辑缓冲区，非激活子模式物品在暂存
                ItemStack[] bufferSnap = signInBufferSnapshot();
                ItemStack[] weekdayStacks = editSignInMonthlyWeekend ? editSignInMonthlyStash : bufferSnap;
                ItemStack[] weekendStacks = editSignInMonthlyWeekend ? bufferSnap : editSignInMonthlyStash;
                json.add(
                    "weekday",
                    buildSignInRewardJson(editSignInWeekdayCurrency, editSignInWeekdayAmount, weekdayStacks));
                json.add(
                    "weekend",
                    buildSignInRewardJson(editSignInWeekendCurrency, editSignInWeekendAmount, weekendStacks));
                com.miaokatze.gtit.trade.v2.NekoEditNetworkManager.sendSaveSignInReward("monthly", json.toString());
            } else {
                // 连续/累计阶梯：增删改（days 字段携带新天数，targetId 用原天数定位）
                json.addProperty("operation", operation);
                json.addProperty("days", editSignInDays);
                json.add("reward", buildSignInRewardJson(editSignInCurrency, editSignInAmount, signInBufferSnapshot()));
                com.miaokatze.gtit.trade.v2.NekoEditNetworkManager
                    .sendSaveSignInReward(editSignInMode + ":" + editSignInOriginalDays, json.toString());
            }
        } catch (Exception e) {
            LOG.error("[NekoEdit] 保存签到编辑失败", e);
        }
    }

    private static com.google.gson.JsonObject buildSignInRewardJson(String currency, int amount, ItemStack[] stacks) {
        com.google.gson.JsonObject reward = new com.google.gson.JsonObject();
        reward.addProperty("currency", currency == null ? "" : currency);
        reward.addProperty("amount", Math.max(0, amount));
        com.google.gson.JsonArray items = new com.google.gson.JsonArray();
        if (stacks != null) {
            for (ItemStack stack : stacks) {
                if (stack == null || stack.getItem() == null) continue;
                com.google.gson.JsonObject itemJson = new com.google.gson.JsonObject();
                itemJson.addProperty("item", net.minecraft.item.Item.itemRegistry.getNameForObject(stack.getItem()));
                itemJson.addProperty("amount", stack.stackSize);
                itemJson.addProperty("meta", stack.getItemDamage());
                // nbtToBase64 对 null 返回 null，统一归一空串防止 Gson 写出 JsonNull 导致解析异常
                String nbt = com.miaokatze.gtit.util.NbtBase64Util.nbtToBase64(stack.getTagCompound());
                itemJson.addProperty("nbt", nbt == null ? "" : nbt);
                items.add(itemJson);
            }
        }
        reward.add("items", items);
        return reward;
    }

    public NekoDraggableEditPanel buildDayEditPanel() {
        NekoDraggableEditPanel editPanel = new NekoDraggableEditPanel();
        editPanel.size(200, 132);
        // v1.7.7 G2 迁移为主面板内嵌 ParentWidget 覆盖层后无默认背景，需手动补上 MC 风格背景
        editPanel.background(GuiTextures.MC_BACKGROUND);
        editPanel.leftRel(0.5f)
            .topRel(0.5f)
            .anchorLeft(0.5f)
            .anchorTop(0.5f);
        editPanel.setEnabledIf(w -> overlay.isCurrent(EditOverlayType.SIGNIN_DAY));

        int fieldHeight = 14;
        int labelWidth = 65;
        int fieldWidth = 120;

        // 标题（目标日号；覆盖按月内日号生效）
        editPanel.child(
            new TextWidget<>(IKey.dynamic(() -> EnumChatFormatting.GOLD + "编辑每日奖励（每月 " + editSignInDay + " 日）")).top(5)
                .horizontalCenter());

        // 货币类型
        editPanel.child(
            new TextWidget<>(IKey.str(EnumChatFormatting.WHITE + "货币类型:")).left(8)
                .top(26));
        TextFieldWidget currencyField = new TextFieldWidget()
            .value(new StringValue.Dynamic(() -> editSignInCurrency, val -> editSignInCurrency = val))
            .setMaxLength(30);
        currencyField.left(labelWidth)
            .top(24)
            .size(fieldWidth, fieldHeight);
        // v1.7.19：TextFieldWidget 延迟到 PhantomItemSlot 之后添加（z-index 置顶）

        // 货币数量
        editPanel.child(
            new TextWidget<>(IKey.str(EnumChatFormatting.WHITE + "货币数量:")).left(8)
                .top(44));
        TextFieldWidget amountField = new TextFieldWidget()
            .value(new StringValue.Dynamic(() -> String.valueOf(editSignInAmount), val -> {
                try {
                    editSignInAmount = Integer.parseInt(val);
                } catch (NumberFormatException ignored) {}
            }))
            .setNumbers(0, Integer.MAX_VALUE);
        amountField.left(labelWidth)
            .top(42)
            .size(fieldWidth, fieldHeight);
        // v1.7.19：TextFieldWidget 延迟到 PhantomItemSlot 之后添加（z-index 置顶）

        // 物品奖励（4 槽，独立缓冲区；留空 = 无物品奖励）
        editPanel.child(
            new TextWidget<>(IKey.str(EnumChatFormatting.WHITE + "物品奖励:")).left(8)
                .top(64));
        for (int i = 0; i < 4; i++) {
            final int slotIndex = i;
            PhantomItemSlot slot = new PhantomItemSlot().slot(new ModularSlot(editSignInDayItemHandler, slotIndex));
            slot.left(labelWidth + slotIndex * 20)
                .top(60);
            editPanel.child(slot);
        }

        // v1.7.19：延迟添加的 TextFieldWidget（在所有 PhantomItemSlot 之后，确保渲染层位于物品槽之上）
        editPanel.child(currencyField);
        editPanel.child(amountField);

        // ---- 保存 / 清除覆盖 / 取消 ----
        editPanel.child(
            new ButtonWidget<>().size(50, 16)
                .left(14)
                .bottom(8)
                .overlay(IKey.str("保存"))
                .onMouseTapped(mouse -> {
                    saveSignInDayEdit("update");
                    requestClose.run();
                    return true;
                }));
        editPanel.child(
            new ButtonWidget<>().size(58, 16)
                .left(71)
                .bottom(8)
                .overlay(IKey.str(EnumChatFormatting.RED + "清除覆盖"))
                .tooltipBuilder(t -> t.addLine(IKey.str(EnumChatFormatting.GRAY + "删除该日号覆盖，回退工作日/周末默认")))
                .tooltipAutoUpdate(true)
                .onMouseTapped(mouse -> {
                    saveSignInDayEdit("remove");
                    requestClose.run();
                    return true;
                }));
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

    private void saveSignInDayEdit(String operation) {
        try {
            com.google.gson.JsonObject json = new com.google.gson.JsonObject();
            json.addProperty("operation", operation);
            if (!"remove".equals(operation)) {
                ItemStack[] stacks = new ItemStack[4];
                for (int i = 0; i < 4; i++) {
                    stacks[i] = editSignInDayItemHandler.getStackInSlot(i);
                }
                json.add("reward", buildSignInRewardJson(editSignInCurrency, editSignInAmount, stacks));
            }
            com.miaokatze.gtit.trade.v2.NekoEditNetworkManager
                .sendSaveSignInReward("day:" + editSignInDay, json.toString());
        } catch (Exception e) {
            LOG.error("[NekoEdit] 保存逐日覆盖编辑失败", e);
        }
    }
}

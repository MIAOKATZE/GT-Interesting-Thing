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
import com.miaokatze.gtit.trade.NekoPageEntry;
import com.miaokatze.gtit.trade.NekoPageRegistry;
import com.miaokatze.gtit.trade.v2.NekoEditNetworkManager;

/**
 * 标签页编辑器（A01 蓝图 G2 抽取自 NekoVMGuiV2，方法体逐字搬移）
 * <p>
 * 持有标签页编辑的本地状态：名称/图标缓冲区、目标 pageId 与新建模式标志。
 * 面板经 {@link #buildEditPanel} 注册到 {@link EditOverlayController}（PAGE 位，顺序冻结）。
 * <p>
 * <b>双端镜像构建</b>：面板在服务端同样构建（v1.7.17），类内不得有客户端专属 API 的静态引用。
 */
public final class PageEditor {

    private static final Logger LOG = LogManager.getLogger("gtit");

    /** 覆盖层控制器（PAGE 位注册与显隐状态） */
    private final EditOverlayController overlay;
    /** 请求宿主执行完整关闭流程（closeEditOverlay） */
    private final Runnable requestClose;

    // --- 标签页 page 编辑（v1.7.6 G3④：shift+点击 page 标签 / 列尾「+」新建 page） ---
    /** page 编辑目标同步值特殊标记：新建 page 模式（服务端仅清空编辑缓冲区） */
    private static final String PAGE_TARGET_NEW = "@new";
    /** page 编辑目标同步值（C2S：pageId 字符串或 {@link #PAGE_TARGET_NEW}，服务端据此加载图标到编辑缓冲区） */
    private StringSyncValue editPageTargetSync;
    /** page 编辑物品缓冲区（双端共享：slot 0=page 图标） */
    private final ItemStackHandler editPageItemHandler = new ItemStackHandler(1);
    /** page 编辑：目标 pageId（新建模式下为 -1，保存时由服务端分配 id≥4） */
    private int editPageId = -1;
    /** page 编辑：是否新建模式（true=保存时走 createPage，false=走 savePage） */
    private boolean editPageIsNew = false;
    /** page 编辑：显示名称 */
    private String editPageName = "";

    public PageEditor(EditOverlayController overlay, Runnable requestClose) {
        this.overlay = overlay;
        this.requestClose = requestClose;
    }

    /**
     * 打开标签页编辑面板（客户端，编辑模式下 shift+点击 page 标签触发）
     * <p>
     * 名称从 {@link NekoPageRegistry} 读取（与同步包同源）；图标经 {@link #editPageTargetSync}
     * 通知服务端加载到编辑缓冲区并同步回客户端。
     *
     * @param pageId 被点击的标签页 ID
     */
    public void beginEdit(int pageId) {
        NekoPageEntry page = NekoPageRegistry.getPage(pageId);
        if (page == null) return;
        editPageIsNew = false;
        editPageId = pageId;
        editPageName = page.getName() != null ? page.getName() : "";
        // 客户端先清空图标槽（防上一页残留；服务端随后加载权威图标并同步回客户端）
        editPageItemHandler.setStackInSlot(0, null);
        if (editPageTargetSync != null) {
            editPageTargetSync.setValue(String.valueOf(pageId));
        }
        overlay.open(EditOverlayType.PAGE);
    }

    /**
     * 打开新建 page 编辑面板（客户端，编辑模式下点击标签列尾「+」按钮触发）
     * <p>
     * 字段全部置默认值；通知服务端清空编辑缓冲区（{@link #PAGE_TARGET_NEW} 标记）。
     * 保存时走 {@code NekoEditNetworkManager.sendCreatePage}（服务端分配 id≥4）。
     */
    public void beginNew() {
        editPageIsNew = true;
        editPageId = -1;
        editPageName = "";
        editPageItemHandler.setStackInSlot(0, null);
        if (editPageTargetSync != null) {
            editPageTargetSync.setValue(PAGE_TARGET_NEW);
        }
        overlay.open(EditOverlayType.PAGE);
    }

    /**
     * 注册 page 编辑目标同步值（由宿主 registerSyncValues 在原位调用，命名通道不受树序影响）
     *
     * @param syncManager 面板同步管理器
     */
    public void registerSyncValues(PanelSyncManager syncManager) {
        // --- page 编辑目标（C2S：客户端设置 pageId 或 "@new"，服务端加载 page 图标到编辑缓冲区，v1.7.6 G3④）---
        editPageTargetSync = new StringSyncValue(() -> "", val -> {
            if (syncManager != null && !syncManager.isClient() && val != null && !val.isEmpty()) {
                loadPageIntoEditBuffer(val);
            }
        });
        editPageTargetSync.allowC2S();
        syncManager.syncValue("nekoV2EditPageTarget", editPageTargetSync);
    }

    /**
     * 服务端：加载 page 图标到编辑缓冲区（slot 0）
     * <p>
     * 每次加载前先清空（防上一页残留）；{@link #PAGE_TARGET_NEW}（新建模式）或
     * 查找失败时仅清空不加载。图标按条目配置的 iconItem 转换（不含默认页回退图标——
     * 编辑的是配置字段本身）。
     *
     * @param target pageId 字符串或 {@link #PAGE_TARGET_NEW}
     */
    private void loadPageIntoEditBuffer(String target) {
        try {
            editPageItemHandler.setStackInSlot(0, null);
            if (PAGE_TARGET_NEW.equals(target)) return;
            int pageId = Integer.parseInt(target);
            NekoPageEntry page = NekoPageRegistry.getPage(pageId);
            if (page == null) return;
            ItemStack icon = page.toIconItemStack();
            if (icon != null) {
                editPageItemHandler.setStackInSlot(0, icon);
            }
            LOG.info("[NekoEdit] 已加载标签页到编辑缓冲区: {}", target);
        } catch (Exception e) {
            LOG.error("[NekoEdit] 加载标签页到编辑缓冲区失败: {}", target, e);
        }
    }

    /**
     * 构建标签页 page 编辑面板（v1.7.6 G3④）
     * <p>
     * 字段布局：page ID（新建=分配提示 / 现有=只读展示）→ 名称 → 图标 PhantomItemSlot×1。
     * 保存按 {@link #editPageIsNew} 分流：新建走
     * {@link NekoEditNetworkManager#sendCreatePage}（服务端分配 id≥4），
     * 现有页走 {@link NekoEditNetworkManager#sendSavePage}；
     * 删除按钮仅编辑现有页时显示（默认页 1-3 由服务端拦截）。
     *
     * @return 编辑覆盖层面板
     */
    public NekoDraggableEditPanel buildEditPanel() {
        NekoDraggableEditPanel editPanel = new NekoDraggableEditPanel();
        editPanel.size(210, 130);
        // v1.7.7 G2 迁移为主面板内嵌 ParentWidget 覆盖层后无默认背景，需手动补上 MC 风格背景
        editPanel.background(GuiTextures.MC_BACKGROUND);
        editPanel.leftRel(0.5f)
            .topRel(0.5f)
            .anchorLeft(0.5f)
            .anchorTop(0.5f);
        editPanel.setEnabledIf(w -> overlay.isCurrent(EditOverlayType.PAGE));

        // 标题（新建 / 编辑 + pageId 动态切换）
        editPanel.child(
            new TextWidget<>(
                IKey.dynamic(() -> EnumChatFormatting.GOLD + (editPageIsNew ? "新建标签页" : "编辑标签页（#" + editPageId + "）")))
                    .top(5)
                    .horizontalCenter());

        int fieldY = 24;
        int fieldHeight = 14;
        int labelWidth = 62;
        int fieldWidth = 132;

        // ---- page ID（新建：服务端分配提示；现有：只读展示，id 不可改）----
        editPanel.child(
            new TextWidget<>(IKey.str("ID:")).left(8)
                .top(fieldY + 2));
        editPanel.child(
            new TextWidget<>(
                IKey.dynamic(
                    () -> EnumChatFormatting.GRAY + (editPageIsNew ? "（保存时自动分配 ≥4）" : String.valueOf(editPageId))))
                        .left(labelWidth)
                        .top(fieldY + 2));

        // ---- 名称 ----
        fieldY += 17;
        editPanel.child(
            new TextWidget<>(IKey.str("名称:")).left(8)
                .top(fieldY + 2));
        TextFieldWidget nameField = new TextFieldWidget()
            .value(new StringValue.Dynamic(() -> editPageName, val -> editPageName = val))
            .setMaxLength(40);
        nameField.left(labelWidth)
            .top(fieldY)
            .size(fieldWidth, fieldHeight);
        nameField.tooltipBuilder(t -> t.addLine(IKey.str("标签页显示名称（留空则保持原名）")));
        nameField.tooltipAutoUpdate(true);
        // v1.7.19：TextFieldWidget 延迟到 PhantomItemSlot 之后添加（z-index 置顶）

        // ---- 图标（PhantomItemSlot 拖入配置，支持 NBT；空槽 = 清空图标回退默认）----
        fieldY += 17;
        editPanel.child(
            new TextWidget<>(IKey.str("图标:")).left(8)
                .top(fieldY + 2));
        PhantomItemSlot iconSlot = new PhantomItemSlot().slot(new ModularSlot(editPageItemHandler, 0));
        iconSlot.left(labelWidth)
            .top(fieldY - 2);
        iconSlot.tooltipBuilder(t -> {
            t.addLine(IKey.str("拖入物品作为标签页图标（支持 NBT）"));
            t.addLine(IKey.str(EnumChatFormatting.GRAY + "留空 = 清空图标（默认页回退默认图标）"));
        });
        iconSlot.tooltipAutoUpdate(true);
        editPanel.child(iconSlot);

        // v1.7.19：延迟添加的 TextFieldWidget（在所有 PhantomItemSlot 之后，确保渲染层位于物品槽之上）
        editPanel.child(nameField);

        // ---- 保存 / 删除 / 取消按钮 ----
        editPanel.child(
            new ButtonWidget<>().size(50, 16)
                .left(14)
                .bottom(8)
                .overlay(IKey.str("保存"))
                .onMouseTapped(mouse -> {
                    savePageEdit();
                    requestClose.run();
                    return true;
                }));
        // 删除按钮：仅编辑现有页时显示（新建模式无页可删；默认页 1-3 由服务端拦截）
        ButtonWidget<?> deleteButton = new ButtonWidget<>().size(50, 16)
            .left(80)
            .bottom(8)
            .overlay(IKey.str(EnumChatFormatting.RED + "删除"))
            .onMouseTapped(mouse -> {
                deletePageEdit();
                requestClose.run();
                return true;
            });
        deleteButton.tooltipBuilder(t -> {
            t.addLine(IKey.str(EnumChatFormatting.RED + "删除本标签页"));
            t.addLine(IKey.str(EnumChatFormatting.GRAY + "页内交易移至「其他」页"));
            t.addLine(IKey.str(EnumChatFormatting.GRAY + "默认页（ID 1-3）不可删除"));
        });
        deleteButton.tooltipAutoUpdate(true);
        deleteButton.setEnabledIf(w -> !editPageIsNew);
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

    /**
     * 保存 page 编辑（客户端 → 服务端）
     * <p>
     * 序列化 {@code {name, icon?}}（图标取自 PhantomItemSlot，空槽不发 icon 键 = 服务端清空图标，
     * 格式与服务端 {@code NekoEditActionHandler#applyPageEditJson} 对应）。
     * 新建模式走 create（服务端分配 id≥4），现有页走 save（pageId 仅定位，不可改）。
     */
    private void savePageEdit() {
        try {
            com.google.gson.JsonObject json = new com.google.gson.JsonObject();
            json.addProperty("name", editPageName);
            // page 图标（slot 0；空槽不发 icon 键 = 服务端清空图标）
            ItemStack iconStack = editPageItemHandler.getStackInSlot(0);
            if (iconStack != null && iconStack.getItem() != null) {
                json.add("icon", EditJsonCodec.itemStackToEditJson(iconStack));
            }
            if (editPageIsNew) {
                NekoEditNetworkManager.sendCreatePage(json.toString());
            } else {
                if (editPageId < 0) return;
                NekoEditNetworkManager.sendSavePage(String.valueOf(editPageId), json.toString());
            }
        } catch (Exception e) {
            LOG.error("[NekoEdit] 保存标签页编辑失败", e);
        }
    }

    /**
     * 删除 page（客户端 → 服务端）
     * <p>
     * 仅编辑现有页时可触发（新建模式删除按钮已隐藏）；「默认页 1-3 不可删」由服务端校验。
     */
    private void deletePageEdit() {
        if (editPageIsNew || editPageId < 0) return;
        NekoEditNetworkManager.sendDeletePage(String.valueOf(editPageId));
    }
}

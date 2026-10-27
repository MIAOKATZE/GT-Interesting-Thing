package com.miaokatze.gtit.gift.ui;

import java.io.IOException;
import java.util.function.Consumer;
import java.util.function.Supplier;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.PacketBuffer;

import com.cleanroommc.modularui.api.IPanelHandler;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.factory.GuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.BooleanSyncValue;
import com.cleanroommc.modularui.value.sync.GenericSyncValue;
import com.cleanroommc.modularui.value.sync.InteractionSyncHandler;
import com.cleanroommc.modularui.value.sync.PanelSyncHandler;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.StringSyncValue;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widgets.Dialog;
import com.cleanroommc.modularui.widgets.ItemDisplayWidget;
import com.cleanroommc.modularui.widgets.PagedWidget;
import com.cleanroommc.modularui.widgets.SlotGroupWidget;
import com.cleanroommc.modularui.widgets.slot.ModularSlot;
import com.cleanroommc.modularui.widgets.slot.PhantomItemSlot;
import com.miaokatze.gtit.client.gui.NekoSingleLineTextWidget;
import com.miaokatze.gtit.client.gui.NekoVendingTheme;
import com.miaokatze.gtit.client.gui.NekoWorkbenchStyle;
import com.miaokatze.gtit.client.gui.utility.UtilityControls;
import com.miaokatze.gtit.client.gui.utility.UtilityUiStyle;
import com.miaokatze.gtit.gift.GiftDefinition;
import com.miaokatze.gtit.gift.GiftIconCatalog;
import com.miaokatze.gtit.gift.GiftNbt;
import com.miaokatze.gtit.gift.GiftRegistry;
import com.miaokatze.gtit.gift.GiftService;
import com.miaokatze.gtit.gift.GiftShellCatalog;
import com.miaokatze.gtit.gui.vm.edit.NekoGhostSlot;
import com.miaokatze.gtit.util.ServerTaskScheduler;

import io.netty.buffer.Unpooled;

/** Fixed sync skeleton on both sides; all server mutations enter the existing tick queue. */
final class GiftPanel {

    private GiftPanel() {}

    static ModularPanel build(GuiData data, PanelSyncManager sm, UISettings settings) {
        GiftEditorSession session = new GiftEditorSession(data.getPlayer());
        sm.bindPlayerInventory(data.getPlayer());
        BooleanSyncValue admin = new BooleanSyncValue(session::admin);
        sm.syncValue("gift_admin", admin);
        StringSyncValue message = read(sm, "gift_result", () -> session.message);
        StringSyncValue selectedId = read(sm, "gift_selected_id", () -> session.selectedId);
        StringSyncValue selection = read(sm, "gift_selection", () -> {
            GiftDefinition gift = session.selected();
            return gift == null ? "" : gift.name + "\n" + gift.id + "  r" + gift.revision;
        });
        StringSyncValue trigger = read(sm, "gift_selected_trigger", () -> {
            GiftDefinition gift = session.selected();
            return gift == null ? "" : "gtit.gift.trigger." + gift.trigger.name();
        });
        StringSyncValue selectedStatus = read(sm, "gift_selected_status", () -> session.status(session.selected()));
        StringSyncValue selectedQuest = read(sm, "gift_selected_quest", () -> {
            GiftDefinition gift = session.selected();
            return gift == null ? "" : gift.questId == null ? "" : gift.questId;
        });
        StringSyncValue rewards = read(sm, "gift_selected_rewards", () -> {
            GiftDefinition gift = session.selected();
            return gift == null ? ""
                : gift.rewards.size() + " / "
                    + gift.randomRewards.size()
                    + " \u00d7 "
                    + gift.randomCount
                    + "     "
                    + gift.wirelessEU
                    + " EU";
        });
        BooleanSyncValue claimable = new BooleanSyncValue(
            () -> GiftEditorSession.canClaim(session.selected(), session.status(session.selected())));
        sm.syncValue("gift_claimable", claimable);
        PagedWidget.Controller navigation = new PagedWidget.Controller();
        BooleanSyncValue editing = new BooleanSyncValue(() -> session.editing);
        editing.setChangeListener(() -> {
            // Controllers are client widgets: the server only owns the synchronized page state.
            if (sm.isClient() && navigation.isInitialised()) navigation.setPage(editing.getBoolValue() ? 1 : 0);
        });
        sm.syncValue("gift_editing", editing);
        GiftLibraryPanel window = new GiftLibraryPanel();
        BooleanSyncValue close = new BooleanSyncValue(() -> session.closeRequested);
        close.setChangeListener(() -> { if (sm.isClient() && close.getBoolValue()) window.closeApproved(); });
        sm.syncValue("gift_close_approved", close);
        IPanelHandler discard = sm.syncedPanel(
            "gift_discard_confirmation",
            true,
            (child, handler) -> discardConfirmation(child, session, handler));
        InteractionSyncHandler requestClose = new InteractionSyncHandler().setOnMousePressed(mouse -> {
            if (!sm.isClient() && mouse.mouseButton == 0) action(sm, session, false, () -> {
                if (session.editing && session.dirty) {
                    session.leavingWindow = true;
                    open(discard);
                } else session.closeRequested = true;
            });
        });
        sm.syncValue("gift_request_close", requestClose);
        window.requestClose(() -> requestClose.onMousePressed(0));
        IPanelHandler preview = sm.syncedPanel("gift_preview", true, (child, handler) -> preview(child, session));
        IPanelHandler delete = sm.syncedPanel(
            "gift_delete_confirmation",
            true,
            (child, handler) -> deleteConfirmation(child, session, handler));
        IPanelHandler applyDefaults = sm.syncedPanel(
            "gift_default_confirmation",
            true,
            (child, handler) -> defaultConfirmation(child, session, handler));
        sm.addOpenListener(player -> {
            if (!sm.isClient() && session.admin()) {
                session.refresh();
                if ("ASK".equals(session.updatePrompt))
                    action(sm, session, true, () -> { if (session.prepareDefaultUpdate()) open(applyDefaults); });
            }
        });
        window.child(window.place(NekoWorkbenchStyle.section(0, 0, 768, 34), l -> l.rect(0, 0, l.width, 34)));
        window.child(
            IKey.lang("gtit.gift.ui.title")
                .color(NekoVendingTheme.INK)
                .asWidget()
                .pos(16, 10)
                .size(240, 18));
        window.child(
            window.place(
                IKey.str("NEKO / GIFTS · 独立礼包与进度")
                    .color(NekoVendingTheme.MUTED)
                    .asWidget(),
                l -> l.rect(l.width - 320, 12, 270, 13)));
        window.child(
            window
                .place(NekoVendingTheme.button(() -> "×", window::closeIfOpen), l -> l.rect(l.width - 38, 7, 24, 22)));
        window.child(
            IKey.lang("gtit.gift.ui.subtitle")
                .color(NekoVendingTheme.MUTED)
                .asWidget()
                .pos(16, 52)
                .size(600, 15)
                .scale(.9f));
        ParentWidget<?> panel = new ParentWidget<>().sizeRel(1f);
        ParentWidget<?> library = NekoWorkbenchStyle.section(0, 0, 238, 300)
            .heightRel(1f);
        ParentWidget<?> detail = NekoWorkbenchStyle.section(246, 0, 250, 300)
            .widthRelOffset(1f, -492)
            .heightRel(1f);
        ParentWidget<?> rewardPreview = NekoWorkbenchStyle.section(0, 0, 238, 300)
            .right(0)
            .heightRel(1f);
        panel.child(library);
        panel.child(detail);
        panel.child(rewardPreview);
        library.child(
            IKey.str("礼包目录 / GIFT LIBRARY")
                .color(NekoVendingTheme.GOLD)
                .asWidget()
                .pos(10, 9)
                .size(218, 15));
        for (int i = 0; i < 6; i++) {
            final int row = i;
            StringSyncValue name = read(sm, "gift_row_name_" + i, () -> {
                GiftDefinition gift = session.row(row);
                return gift == null ? "" : gift.name;
            });
            StringSyncValue id = read(sm, "gift_row_id_" + i, () -> {
                GiftDefinition gift = session.row(row);
                return gift == null ? "" : gift.id;
            });
            StringSyncValue status = read(sm, "gift_row_status_" + i, () -> session.status(session.row(row)));
            GenericSyncValue<net.minecraft.item.ItemStack, ?> item = GiftItemSync
                .readOnly(() -> displayItem(session.row(row)));
            sm.syncValue("gift_row_item_" + i, item);
            int y = 32 + i * 40;
            UtilityControls.ActionButton selectionButton = button(
                sm,
                session,
                "gift_select_" + i,
                "",
                7,
                y,
                224,
                false,
                () -> session.select(session.row(row))).height(36)
                    .selected(
                        () -> !id.getStringValue()
                            .isEmpty() && id.getStringValue()
                                .equals(selectedId.getStringValue()))
                    .available(
                        () -> !name.getStringValue()
                            .isEmpty());
            library.child(selectionButton);
            library.child(
                new UtilityControls.DecorativeItem().item(item)
                    .pos(13, y + 8)
                    .size(20)
                    .background(NekoVendingTheme.SLOT));
            library.child(
                new NekoSingleLineTextWidget(IKey.dynamic(name::getStringValue)).color(
                    () -> id.getStringValue()
                        .equals(selectedId.getStringValue()) ? NekoVendingTheme.LIGHT : NekoVendingTheme.INK)
                    .shadow(false)
                    .pos(40, y + 5)
                    .size(184, 12)
                    .scale(.9f));
            library.child(
                new NekoSingleLineTextWidget(IKey.dynamic(() -> translatedStatus(status)))
                    .color(
                        () -> id.getStringValue()
                            .equals(selectedId.getStringValue()) ? NekoVendingTheme.LIGHT : NekoVendingTheme.MUTED)
                    .shadow(false)
                    .pos(40, y + 21)
                    .size(184, 10)
                    .scale(.75f));
        }
        library.child(
            button(
                sm,
                session,
                "gift_list_prev",
                "gtit.gift.ui.previous",
                8,
                0,
                65,
                false,
                () -> session.listPage = Math.max(0, session.listPage - 1)).bottom(10)
                    .height(22));
        library.child(button(sm, session, "gift_list_next", "gtit.gift.ui.next", 165, 0, 65, false, () -> {
            session.refresh();
            session.listPage = Math.min(Math.max(0, (session.definitions.size() - 1) / 6), session.listPage + 1);
        }).bottom(10)
            .height(22));
        StringSyncValue listPage = read(sm, "gift_list_page", () -> {
            session.refresh();
            return (session.listPage + 1) + " / " + Math.max(1, (session.definitions.size() + 5) / 6);
        });
        library.child(
            new NekoSingleLineTextWidget(IKey.dynamic(listPage::getStringValue)).left(80)
                .bottom(15)
                .size(78, 12)
                .scale(.85f));
        detail.child(
            IKey.lang("gtit.gift.ui.empty_selection")
                .color(NekoVendingTheme.MUTED)
                .asWidget()
                .pos(12, 46)
                .widthRelOffset(1f, -24)
                .height(36)
                .setEnabledIf(
                    w -> selection.getStringValue()
                        .isEmpty()));
        GenericSyncValue<net.minecraft.item.ItemStack, ?> selectedItem = GiftItemSync
            .readOnly(() -> displayItem(session.selected()));
        sm.syncValue("gift_selected_item", selectedItem);
        detail.child(
            new ItemDisplayWidget().item(selectedItem)
                .pos(12, 12)
                .size(26)
                .background(NekoVendingTheme.SLOT));
        detail.child(
            new NekoSingleLineTextWidget(IKey.dynamic(() -> selectionLine(selection.getStringValue(), 0))).pos(46, 10)
                .widthRelOffset(1f, -58)
                .height(14)
                .scale(.9f));
        detail.child(
            new NekoSingleLineTextWidget(IKey.dynamic(() -> selectionLine(selection.getStringValue(), 1)))
                .color(NekoVendingTheme.MUTED)
                .pos(46, 27)
                .widthRelOffset(1f, -58)
                .height(12)
                .scale(.75f));
        StringSyncValue selectedShell = read(sm, "gift_selected_shell", () -> {
            GiftDefinition gift = session.selected();
            return gift == null ? "" : GiftShellCatalog.registryName(GiftShellCatalog.effectiveKey(gift));
        });
        detail.child(
            new NekoSingleLineTextWidget(IKey.dynamic(selectedShell::getStringValue)).color(NekoVendingTheme.MUTED)
                .pos(12, 52)
                .widthRelOffset(1f, -24)
                .height(20)
                .scale(.75f));
        detail.child(
            IKey.lang(trigger::getStringValue)
                .asWidget()
                .pos(12, 79)
                .widthRelOffset(1f, -24)
                .height(16)
                .scale(.9f));
        detail.child(
            new NekoSingleLineTextWidget(IKey.dynamic(() -> translatedStatus(selectedStatus)))
                .color(NekoVendingTheme.GREEN)
                .pos(12, 102)
                .widthRelOffset(1f, -24)
                .height(16)
                .scale(.9f));
        detail.child(
            IKey.lang("gtit.gift.ui.quest")
                .asWidget()
                .pos(12, 132)
                .size(80, 12)
                .scale(.85f));
        detail.child(
            new NekoSingleLineTextWidget(IKey.dynamic(selectedQuest::getStringValue)).pos(12, 151)
                .widthRelOffset(1f, -24)
                .height(24)
                .scale(.85f));
        detail.child(
            IKey.lang("gtit.gift.ui.reward_summary")
                .asWidget()
                .pos(12, 185)
                .widthRelOffset(1f, -24)
                .height(13)
                .scale(.85f));
        detail.child(
            new NekoSingleLineTextWidget(IKey.dynamic(rewards::getStringValue)).pos(12, 205)
                .widthRelOffset(1f, -24)
                .height(25)
                .scale(.85f));
        detail.child(button(sm, session, "gift_edit", "gtit.gift.ui.edit", 12, 0, 106, true, () -> {
            if (session.selected() != null) {
                session.select(session.selected());
                session.editing = true;
            }
        }).bottom(45)
            .height(23)
            .available(
                () -> admin.getBoolValue() && !selection.getStringValue()
                    .isEmpty()));
        detail.child(button(sm, session, "gift_new", "gtit.gift.ui.new", 0, 0, 106, true, () -> {
            session.create();
            session.editing = true;
        }).right(12)
            .bottom(45)
            .height(23)
            .available(admin::getBoolValue));
        StringSyncValue defaults = read(sm, "gift_defaults_state", () -> {
            session.refresh();
            return session.defaultsEnabled ? "gtit.gift.ui.defaults_on" : "gtit.gift.ui.defaults_off";
        });
        detail.child(button(sm, session, "gift_defaults_toggle", "", 12, 0, 200, true, () -> {
            GiftRegistry.INSTANCE.setDefaultsEnabled(!session.defaultsEnabled, session.registryRevision);
            session.invalidate();
        }).bottom(12)
            .height(23)
            .widthRelOffset(1f, -24)
            .overlay(IKey.lang(defaults::getStringValue))
            .available(admin::getBoolValue));
        rewardPreview.child(
            IKey.str("奖励预览 / REWARDS")
                .color(NekoVendingTheme.GOLD)
                .asWidget()
                .pos(10, 10)
                .size(218, 15));
        for (int i = 0; i < 6; i++) {
            final int row = i;
            GenericSyncValue<net.minecraft.item.ItemStack, ?> item = GiftItemSync.readOnly(() -> {
                com.miaokatze.gtit.config.GiftConfig.ItemEntry entry = previewEntry(session, row);
                return entry == null ? null : entry.toItemStack();
            });
            sm.syncValue("gift_inline_preview_item_" + i, item);
            StringSyncValue description = read(sm, "gift_inline_preview_text_" + i, () -> {
                com.miaokatze.gtit.config.GiftConfig.ItemEntry entry = previewEntry(session, row);
                if (entry == null) return "";
                net.minecraft.item.ItemStack stack = entry.toItemStack();
                return (stack == null ? entry.itemId : stack.getDisplayName()) + " × " + entry.amount;
            });
            rewardPreview.child(
                new ItemDisplayWidget().item(item)
                    .pos(12, 38 + i * 28)
                    .size(20)
                    .background(NekoVendingTheme.SLOT));
            rewardPreview.child(
                new NekoSingleLineTextWidget(IKey.dynamic(description::getStringValue)).pos(40, 42 + i * 28)
                    .size(186, 17)
                    .scale(.8f));
        }
        rewardPreview.child(
            new UtilityControls.ActionButton().kraft()
                .left(12)
                .bottom(77)
                .size(214, 23)
                .overlay(IKey.lang("gtit.gift.ui.preview"))
                .available(
                    () -> !selection.getStringValue()
                        .isEmpty())
                .onMousePressed(mouse -> {
                    if (mouse != 0) return false;
                    preview.openPanel();
                    return true;
                }));
        rewardPreview.child(button(sm, session, "gift_claim", "gtit.gift.ui.claim", 12, 0, 214, false, () -> {
            session.message = GiftService.INSTANCE.claim((EntityPlayerMP) session.player, session.selectedId);
            session.invalidate();
        }).bottom(44)
            .height(25)
            .available(claimable::getBoolValue)
            .addTooltipLine(IKey.lang("gtit.gift.ui.claim_hint")));
        rewardPreview.child(
            IKey.str("领取只发放礼包；不会自动开包")
                .color(NekoVendingTheme.MUTED)
                .asWidget()
                .left(12)
                .bottom(12)
                .size(214, 23)
                .scale(.8f));
        window.child(
            new PagedWidget<>().sizeRel(1f)
                .controller(navigation)
                .addPage(window.place(panel, l -> l.rect(12, 80, l.width - 24, l.height - 116)))
                .addPage(window.place(editor(sm, session, discard, delete), l -> l.editor())));
        window.child(window.place(NekoWorkbenchStyle.section(0, 0, 1, 1), l -> l.rect(0, l.height - 28, l.width, 28)));
        window.child(
            window.place(
                new NekoSingleLineTextWidget(IKey.dynamic(() -> messageText(message.getStringValue()))),
                l -> l.rect(14, l.height - 20, l.width - 28, 14)));
        window.catalog(() -> editing.getBoolValue() && admin.getBoolValue());
        NekoWorkbenchStyle.style(window);
        return window;
    }

    private static String selectionLine(String text, int line) {
        int split = text.indexOf('\n');
        if (split < 0) return line == 0 ? text : "";
        return line == 0 ? text.substring(0, split) : text.substring(split + 1);
    }

    private static net.minecraft.item.ItemStack displayItem(GiftDefinition gift) {
        return gift == null ? null
            : gift.icon == null ? GiftShellCatalog.stack(GiftShellCatalog.effectiveKey(gift)) : gift.icon.toItemStack();
    }

    private static void open(IPanelHandler panel) {
        panel.openPanel();
        // A server-initiated open requires an explicit SYNC_OPEN in MUI2 2.3.92.
        ((PanelSyncHandler) panel).syncToClient(PanelSyncHandler.SYNC_OPEN);
    }

    private static ParentWidget<?> editor(PanelSyncManager sm, GiftEditorSession session, IPanelHandler discard,
        IPanelHandler delete) {
        ParentWidget<?> panel = new ParentWidget<>().sizeRel(1f)
            .background(NekoVendingTheme.PANEL)
            .excludeAreaInRecipeViewer();
        panel.child(button(sm, session, "gift_back", "gtit.gift.ui.back", 0, 8, 100, true, () -> {
            if (session.dirty) {
                session.leavingWindow = false;
                open(discard);
            } else session.editing = false;
        }).right(12)
            .height(22));
        panel.child(
            IKey.lang("gtit.gift.ui.editor")
                .asWidget()
                .pos(12, 12)
                .widthRelOffset(1f, -136)
                .height(16));
        BooleanSyncValue dirty = new BooleanSyncValue(() -> session.dirty);
        sm.syncValue("gift_draft_dirty", dirty);
        panel.child(
            IKey.lang(() -> dirty.getBoolValue() ? "gtit.gift.ui.unsaved" : "gtit.gift.ui.draft_hint")
                .color(NekoVendingTheme.MUTED)
                .asWidget()
                .left(12)
                .bottom(50)
                .size(270, 23)
                .scale(.8f));
        PagedWidget.Controller pages = new PagedWidget.Controller();
        String[] titles = { "general", "required", "random" };
        for (int i = 0; i < 3; i++) panel.child(
            new UtilityControls.Page(i, pages).kraft()
                .leftRelOffset(i / 3f, 12)
                .top(34)
                .widthRelOffset(1f / 3f, -16)
                .height(24)
                .overlay(IKey.lang("gtit.gift.ui." + titles[i])));
        panel.child(
            new PagedWidget<>().pos(12, 64)
                .widthRelOffset(1f, -24)
                .heightRelOffset(1f, -150)
                .controller(pages)
                .addPage(general(sm, session))
                .addPage(items(sm, session, false))
                .addPage(items(sm, session, true)));
        panel.child(
            SlotGroupWidget.playerInventory(false)
                .right(12)
                .bottom(12));
        panel.child(
            button(sm, session, "gift_save", "gtit.gift.ui.save", 12, 0, 126, true, session::save).bottom(12)
                .height(25)
                .selected(() -> true));
        BooleanSyncValue creating = new BooleanSyncValue(() -> session.creating);
        sm.syncValue("gift_draft_creating", creating);
        panel.child(button(sm, session, "gift_request_delete", "gtit.gift.ui.delete", 146, 0, 126, true, () -> {
            session.prepareDelete();
            open(delete);
        }).bottom(12)
            .height(25)
            .available(() -> !creating.getBoolValue()));
        return panel;
    }

    private static ModularPanel discardConfirmation(PanelSyncManager sm, GiftEditorSession session,
        IPanelHandler handler) {
        ModularPanel dialog = new Dialog<>("gift_discard_confirmation", null).setDisablePanelsBelow(true)
            .setCloseOnOutOfBoundsClick(false)
            .size(330, 136)
            .background(NekoVendingTheme.PANEL);
        dialog.child(
            IKey.lang("gtit.gift.ui.unsaved")
                .asWidget()
                .pos(12, 14)
                .size(304, 14));
        dialog.child(
            IKey.lang("gtit.gift.ui.discard_hint")
                .asWidget()
                .pos(12, 38)
                .size(304, 38)
                .scale(0.8f));
        dialog.child(
            new UtilityControls.ActionButton().kraft()
                .pos(12, 102)
                .size(144, 18)
                .overlay(IKey.lang("gtit.gift.ui.continue_editing"))
                .onMousePressed(mouse -> {
                    if (mouse != 0) return false;
                    handler.closePanel();
                    return true;
                }));
        dialog.child(button(sm, session, "gift_discard", "gtit.gift.ui.discard", 166, 102, 150, false, () -> {
            boolean closeWindow = session.leavingWindow;
            session.discard();
            handler.closePanel();
            if (closeWindow) session.closeRequested = true;
        }));
        NekoWorkbenchStyle.style(dialog);
        return dialog;
    }

    private static ModularPanel defaultConfirmation(PanelSyncManager sm, GiftEditorSession session,
        IPanelHandler handler) {
        ModularPanel dialog = new Dialog<>("gift_default_confirmation", null).setDisablePanelsBelow(true)
            .setCloseOnOutOfBoundsClick(false)
            .size(270, 90)
            .background(NekoVendingTheme.PANEL);
        dialog.child(
            IKey.lang("gtit.gift.ui.apply_confirm")
                .asWidget()
                .pos(12, 16)
                .size(246, 14));
        dialog.child(button(sm, session, "gift_default_skip", "gtit.gift.ui.skip", 12, 52, 116, true, () -> {
            try {
                session.resolveDefaultUpdate(false);
            } finally {
                handler.closePanel();
            }
        }));
        dialog.child(button(sm, session, "gift_apply_confirmed", "gtit.gift.ui.apply", 142, 52, 116, true, () -> {
            try {
                session.resolveDefaultUpdate(true);
            } finally {
                handler.closePanel();
            }
        }));
        NekoWorkbenchStyle.style(dialog);
        return dialog;
    }

    private static ModularPanel deleteConfirmation(PanelSyncManager sm, GiftEditorSession session,
        IPanelHandler handler) {
        ModularPanel dialog = new Dialog<>("gift_delete_confirmation", null).setDisablePanelsBelow(true)
            .setCloseOnOutOfBoundsClick(false)
            .size(286, 150)
            .background(NekoVendingTheme.PANEL);
        StringSyncValue target = read(
            sm,
            "gift_delete_target",
            () -> session.deleteTargetName + " [" + session.deleteTargetId + "]");
        dialog.child(
            IKey.lang("gtit.gift.ui.delete_confirm")
                .color(NekoVendingTheme.INK)
                .asWidget()
                .pos(12, 12)
                .size(252, 14));
        dialog.child(
            new NekoSingleLineTextWidget(IKey.dynamic(target::getStringValue)).color(NekoVendingTheme.GOLD)
                .pos(12, 33)
                .size(262, 24)
                .scale(0.8f));
        dialog.child(
            IKey.lang("gtit.gift.ui.delete_hint")
                .color(NekoVendingTheme.MUTED)
                .asWidget()
                .pos(12, 62)
                .size(262, 40)
                .scale(0.8f));
        StringSyncValue message = read(sm, "gift_delete_result", () -> session.message);
        dialog.child(
            new NekoSingleLineTextWidget(IKey.dynamic(() -> messageText(message.getStringValue()))).pos(12, 102)
                .size(262, 12)
                .scale(0.65f));
        dialog.child(button(sm, session, "gift_delete", "gtit.gift.ui.delete", 12, 117, 126, true, () -> {
            session.confirmDelete();
            handler.closePanel();
        }));
        dialog.child(
            new UtilityControls.ActionButton().kraft()
                .pos(146, 117)
                .size(126, 18)
                .overlay(IKey.lang("gtit.gift.ui.cancel"))
                .onMousePressed(mouse -> {
                    if (mouse != 0) return false;
                    dialog.closeIfOpen();
                    return true;
                }));
        dialog.child(
            UtilityUiStyle.managerCloseButton(dialog)
                .right(6)
                .top(6));
        NekoWorkbenchStyle.style(dialog);
        return dialog;
    }

    private static ModularPanel preview(PanelSyncManager sm, GiftEditorSession session) {
        ModularPanel dialog = new Dialog<>("gift_preview", null).setDisablePanelsBelow(true)
            .setCloseOnOutOfBoundsClick(false)
            .size(350, 306)
            .background(NekoVendingTheme.PANEL);
        ParentWidget<?> panel = new ParentWidget<>().size(350, 306);
        panel.child(
            IKey.lang("gtit.gift.ui.preview")
                .asWidget()
                .pos(12, 11));
        for (int i = 0; i < 8; i++) {
            final int row = i;
            GenericSyncValue<net.minecraft.item.ItemStack, ?> item = GiftItemSync.readOnly(() -> {
                com.miaokatze.gtit.config.GiftConfig.ItemEntry entry = previewEntry(session, row);
                return entry == null ? null : entry.toItemStack();
            });
            sm.syncValue("gift_preview_item_" + i, item);
            StringSyncValue label = read(sm, "gift_preview_label_" + i, () -> {
                com.miaokatze.gtit.config.GiftConfig.ItemEntry entry = previewEntry(session, row);
                return entry == null ? "" : entry.itemId + " × " + entry.amount;
            });
            StringSyncValue category = read(sm, "gift_preview_category_" + i, () -> {
                GiftDefinition gift = session.selected();
                if (gift == null || previewEntry(session, row) == null) return "";
                return session.previewPage * 8 + row < gift.rewards.size() ? "gtit.gift.ui.required"
                    : "gtit.gift.ui.random";
            });
            int y = 36 + i * 28;
            panel.child(
                new ItemDisplayWidget().item(item)
                    .displayAmount(true)
                    .size(22)
                    .pos(12, y)
                    .background(NekoVendingTheme.SLOT)
                    .tooltipBuilder(t -> {
                        net.minecraft.item.ItemStack stack = item.getValue();
                        if (stack != null) {
                            t.addLine(stack.getDisplayName());
                            if (stack.hasTagCompound()) t.addLine(
                                "NBT: " + stack.getTagCompound()
                                    .toString());
                        }
                    })
                    .tooltipAutoUpdate(true));
            panel.child(
                new NekoSingleLineTextWidget(IKey.dynamic(label::getStringValue)).pos(42, y)
                    .size(294, 12)
                    .scale(0.8f));
            panel.child(
                IKey.lang(category::getStringValue)
                    .asWidget()
                    .pos(42, y + 13)
                    .size(294, 10)
                    .scale(0.65f));
        }
        panel.child(
            button(
                sm,
                session,
                "gift_preview_prev",
                "gtit.gift.ui.previous",
                12,
                272,
                99,
                false,
                () -> session.previewPage = Math.max(0, session.previewPage - 1)));
        panel.child(button(sm, session, "gift_preview_next", "gtit.gift.ui.next", 239, 272, 99, false, () -> {
            GiftDefinition gift = session.selected();
            if (gift != null) session.previewPage = Math
                .min(Math.max(0, (gift.rewards.size() + gift.randomRewards.size() - 1) / 8), session.previewPage + 1);
        }));
        StringSyncValue page = read(sm, "gift_preview_page", () -> {
            GiftDefinition gift = session.selected();
            int count = gift == null ? 0 : gift.rewards.size() + gift.randomRewards.size();
            return (session.previewPage + 1) + " / " + Math.max(1, (count + 7) / 8);
        });
        panel.child(
            new NekoSingleLineTextWidget(IKey.dynamic(page::getStringValue)).pos(146, 277)
                .size(64, 10)
                .scale(0.8f));
        return UtilityUiStyle.managerScrollable(dialog, 350, 306, panel);
    }

    private static com.miaokatze.gtit.config.GiftConfig.ItemEntry previewEntry(GiftEditorSession session, int row) {
        GiftDefinition gift = session.selected();
        if (gift == null) return null;
        int index = session.previewPage * 8 + row;
        if (index < gift.rewards.size()) return gift.rewards.get(index);
        index -= gift.rewards.size();
        return index < gift.randomRewards.size() ? gift.randomRewards.get(index) : null;
    }

    private static ParentWidget<?> general(PanelSyncManager sm, GiftEditorSession session) {
        ParentWidget<?> root = new ParentWidget<>().sizeRel(1f);
        field(
            root,
            sm,
            session,
            "gift_id",
            "gtit.gift.ui.id",
            0,
            () -> session.draft.id,
            value -> { if (session.creating) session.draft.id = value; },
            64);
        field(
            root,
            sm,
            session,
            "gift_name",
            "gtit.gift.ui.name",
            23,
            () -> session.draft.name,
            value -> session.draft.name = value,
            128);
        field(
            root,
            sm,
            session,
            "gift_quest",
            "gtit.gift.ui.quest",
            46,
            () -> session.draft.questId,
            value -> session.draft.questId = value,
            128);
        field(
            root,
            sm,
            session,
            "gift_eu",
            "gtit.gift.ui.eu",
            69,
            () -> session.draft.wirelessEU,
            value -> session.draft.wirelessEU = value,
            128);
        field(
            root,
            sm,
            session,
            "gift_random_count",
            "gtit.gift.ui.random_count",
            92,
            () -> Integer.toString(session.draft.randomCount),
            value -> {
                int count = Integer.parseInt(value);
                if (count < 0 || count > 128) throw new IllegalArgumentException("Random count must be 0..128");
                session.draft.randomCount = count;
            },
            3);
        root.child(button(sm, session, "gift_trigger", "", 0, 116, 106, true, () -> {
            GiftDefinition.Trigger[] values = GiftDefinition.Trigger.values();
            session.draft.trigger = values[(session.draft.trigger.ordinal() + 1) % values.length];
            session.dirty = true;
        }).overlay(
            IKey.lang(
                read(
                    sm,
                    "gift_trigger_text",
                    () -> "gtit.gift.trigger." + session.draft.trigger.name())::getStringValue)));
        root.child(button(sm, session, "gift_policy", "", 112, 116, 106, true, () -> {
            session.draft.updatePolicy = session.draft.updatePolicy == GiftDefinition.UpdatePolicy.FORCED
                ? GiftDefinition.UpdatePolicy.RECOMMENDED
                : GiftDefinition.UpdatePolicy.FORCED;
            session.dirty = true;
        }).overlay(
            IKey.lang(
                read(
                    sm,
                    "gift_policy_text",
                    () -> "gtit.gift.policy." + session.draft.updatePolicy.name())::getStringValue)));
        root.child(button(sm, session, "gift_enabled", "", 0, 142, 82, true, () -> {
            session.draft.enabled = !session.draft.enabled;
            session.dirty = true;
        }).overlay(
            IKey.lang(
                read(
                    sm,
                    "gift_enabled_text",
                    () -> session.draft.enabled ? "gtit.gift.ui.enabled" : "gtit.gift.ui.disabled")::getStringValue)));
        root.child(
            IKey.lang("gtit.gift.ui.shell")
                .asWidget()
                .leftRelOffset(.5f, 8)
                .top(2)
                .size(148, 12)
                .scale(0.75f));
        StringSyncValue boundShell = read(sm, "gift_bound_shell_key", session::shellKey);
        GenericSyncValue<net.minecraft.item.ItemStack, ?> shellItem = GiftItemSync.readOnly(session::shellStack);
        sm.syncValue("gift_bound_shell_item", shellItem);
        root.child(
            new ItemDisplayWidget().item(shellItem)
                .right(6)
                .top(0)
                .size(18)
                .background(NekoVendingTheme.SLOT)
                .addTooltipLine(IKey.lang("gtit.gift.ui.shell_hint")));
        String[] icons = GiftIconCatalog.keys();
        // Stable, fixed identities for every registered carrier, independent of the custom list icon.
        for (int i = 0; i < icons.length; i++) {
            final String icon = icons[i];
            int x = 236 + i % 8 * 27;
            int y = 24 + i / 8 * 25;
            root.child(
                button(sm, session, "gift_icon_" + i, "", x, y, 24, true, () -> session.bindShell(icon))
                    .leftRelOffset(.5f, 8 + i % 8 * 27)
                    .height(22)
                    .selected(() -> icon.equals(boundShell.getStringValue()))
                    .tooltipBuilder(t -> {
                        t.addLine(IKey.lang(GiftIconCatalog.labelKey(icon)));
                        t.addLine(GiftShellCatalog.registryName(icon));
                        t.addLine(IKey.lang("gtit.gift.ui.shell_hint"));
                    }));
            GenericSyncValue<net.minecraft.item.ItemStack, ?> carrier = GiftItemSync
                .readOnly(() -> GiftShellCatalog.stack(icon));
            sm.syncValue("gift_shell_choice_item_" + i, carrier);
            root.child(
                new UtilityControls.DecorativeItem().item(carrier)
                    .leftRelOffset(.5f, 11 + i % 8 * 27)
                    .top(y + 2)
                    .size(18)
                    .background(com.cleanroommc.modularui.api.drawable.IDrawable.EMPTY));
        }
        root.child(
            IKey.lang("gtit.gift.ui.list_icon")
                .asWidget()
                .leftRelOffset(.5f, 8)
                .top(143)
                .size(78, 12)
                .scale(0.75f));
        root.child(
            ghost(sm, session, 0).leftRelOffset(.5f, 90)
                .top(139)
                .addTooltipLine(IKey.lang("gtit.gift.ui.icon_hint")));
        root.child(
            button(
                sm,
                session,
                "gift_reset_icon",
                "gtit.gift.ui.reset_icon",
                344,
                139,
                107,
                true,
                () -> session.clear(0)).right(5));
        return root;
    }

    private static ParentWidget<?> items(PanelSyncManager sm, GiftEditorSession session, boolean random) {
        String prefix = random ? "gift_random" : "gift_required";
        ParentWidget<?> root = new ParentWidget<>().sizeRel(1f);
        root.child(
            IKey.lang("gtit.gift.ui.ghost_hint")
                .asWidget()
                .pos(0, 0)
                .scale(0.75f));
        for (int row = 0; row < GiftEditorSession.PAGE_SIZE; row++) {
            final int slot = 1 + row + (random ? GiftEditorSession.PAGE_SIZE : 0);
            int y = 16 + row * 29;
            root.child(
                ghost(sm, session, slot).left(0)
                    .topRelOffset(row * .19f, 16));
            StringSyncValue amount = write(
                sm,
                session,
                prefix + "_amount_" + row,
                () -> session.amount(slot),
                value -> session.amount(slot, value),
                4);
            root.child(
                new UtilityControls.Field().kraft()
                    .left(24)
                    .topRelOffset(row * .19f, 16)
                    .size(49, 18)
                    .value(amount)
                    .setMaxLength(4)
                    .background(NekoVendingTheme.INPUT)
                    .addTooltipLine(IKey.lang("gtit.gift.ui.amount")));
            StringSyncValue nbt = write(
                sm,
                session,
                prefix + "_nbt_" + row,
                () -> session.nbt(slot),
                value -> session.nbt(slot, value),
                16384);
            root.child(
                new UtilityControls.Field().kraft()
                    .left(79)
                    .topRelOffset(row * .19f, 16)
                    .widthRelOffset(1f, -104)
                    .height(18)
                    .value(nbt)
                    .setMaxLength(16384)
                    .background(NekoVendingTheme.INPUT)
                    .addTooltipLine(IKey.lang("gtit.gift.ui.nbt")));
            root.child(
                button(sm, session, prefix + "_clear_" + row, "", 434, y, 22, true, () -> session.clear(slot)).right(0)
                    .topRelOffset(row * .19f, 16)
                    .overlay(IKey.str("×")));
            StringSyncValue description = read(
                sm,
                prefix + "_item_description_" + row,
                () -> session.itemDescription(slot));
            root.child(
                new NekoSingleLineTextWidget(IKey.dynamic(description::getStringValue)).left(24)
                    .topRelOffset(row * .19f, 35)
                    .widthRelOffset(1f, -24)
                    .height(9)
                    .scale(0.6f));
        }
        root.child(button(sm, session, prefix + "_prev", "gtit.gift.ui.previous", 0, 140, 83, true, () -> {
            session.editGeneration++;
            if (random) session.randomPage = Math.max(0, session.randomPage - 1);
            else session.requiredPage = Math.max(0, session.requiredPage - 1);
        }).bottom(5));
        StringSyncValue page = read(
            sm,
            prefix + "_page",
            () -> Integer.toString(1 + (random ? session.randomPage : session.requiredPage)) + "/32");
        root.child(
            new NekoSingleLineTextWidget(IKey.dynamic(page::getStringValue)).leftRel(.5f)
                .bottom(10)
                .size(60, 12));
        root.child(button(sm, session, prefix + "_next", "gtit.gift.ui.next", 373, 140, 83, true, () -> {
            session.editGeneration++;
            if (random) session.randomPage = Math.min(31, session.randomPage + 1);
            else session.requiredPage = Math.min(31, session.requiredPage + 1);
        }).right(0)
            .bottom(5));
        return root;
    }

    private static PhantomItemSlot ghost(PanelSyncManager sm, GiftEditorSession session, int slot) {
        GiftGhostSlotSync handler = new GiftGhostSlotSync(new ModularSlot(session.visibleItems, slot) {

            @Override
            public boolean isItemValid(net.minecraft.item.ItemStack stack) {
                // The base probe temporarily clears/restores the slot, which mutates our draft.
                return stack != null && (sm.isClient() || session.admin());
            }
        }.ignoreMaxStackSize(true)) {

            @Override
            public void readOnServer(int id, PacketBuffer buffer) throws IOException {
                if (buffer.readableBytes() > GiftNbt.MAX_BYTES + 32) {
                    action(
                        sm,
                        session,
                        true,
                        () -> { throw new IllegalArgumentException("Oversized ghost item; original retained"); });
                    return;
                }
                byte[] payload = new byte[buffer.readableBytes()];
                buffer.readBytes(payload);
                final long generation = session.editGeneration;
                final int targetSlot = session.mapped(slot);
                action(sm, session, true, () -> {
                    if (generation != session.editGeneration || targetSlot != session.mapped(slot)) {
                        throw new IllegalStateException("Editor page changed; retry the item operation");
                    }
                    PacketBuffer copy = new PacketBuffer(Unpooled.wrappedBuffer(payload));
                    try {
                        if (id == REJECT_ITEM)
                            throw new IllegalArgumentException("Invalid or oversized item NBT; original retained");
                        super.readOnServer(id, copy);
                    } catch (IOException e) {
                        throw new IllegalArgumentException("Invalid ghost item payload", e);
                    } finally {
                        copy.release();
                    }
                });
            }
        };
        sm.syncValue("gift_ghost_" + slot, handler);
        PhantomItemSlot widget = new NekoGhostSlot() {

            @Override
            public boolean handleDragAndDrop(net.minecraft.item.ItemStack stack, int button) {
                try {
                    GiftNbt.encode(stack.getTagCompound());
                    if (stack.stackSize < 1 || stack.stackSize > 4096)
                        throw new IllegalArgumentException("Invalid item count");
                } catch (IllegalArgumentException e) {
                    handler.syncToServer(GiftGhostSlotSync.REJECT_ITEM);
                    return false;
                }
                return super.handleDragAndDrop(stack, button);
            }
        }.syncHandler(handler);
        widget.background(NekoVendingTheme.SLOT);
        return widget;
    }

    private static String translatedStatus(StringSyncValue status) {
        String value = status.getStringValue();
        return value.isEmpty() ? ""
            : IKey.lang("gtit.gift.status." + value)
                .get();
    }

    private static String messageText(String message) {
        if (message.isEmpty()) return IKey.lang("gtit.gift.ui.ready")
            .get();
        if (message.startsWith("gtit.")) return IKey.lang(message)
            .get();
        if ("issued".equals(message)) return IKey.lang("gtit.gift.status.ISSUED")
            .get();
        if ("pending".equals(message)) return IKey.lang("gtit.gift.status.PENDING")
            .get();
        return message;
    }

    private static StringSyncValue read(PanelSyncManager sm, String key, Supplier<String> getter) {
        StringSyncValue value = new StringSyncValue(() -> {
            String text = getter.get();
            return text == null ? "" : text;
        });
        sm.syncValue(key, value);
        return value;
    }

    private static StringSyncValue write(PanelSyncManager sm, GiftEditorSession session, String key,
        Supplier<String> getter, Consumer<String> setter, int limit) {
        StringSyncValue value = new GiftTextSync(() -> {
            String text = getter.get();
            return text == null ? "" : text;
        }, text -> {
            if (sm.isClient()) return;
            final long generation = session.editGeneration;
            action(sm, session, true, () -> {
                if (generation != session.editGeneration) {
                    throw new IllegalStateException("Editor page changed; retry the edit");
                }
                if (text == null || text.length() > limit) throw new IllegalArgumentException("Input is too long");
                setter.accept(text);
                session.dirty = true;
            });
        }, limit) {

            @Override
            public void readOnServer(int id, PacketBuffer buffer) throws IOException {
                try {
                    super.readOnServer(id, buffer);
                } catch (IOException | RuntimeException e) {
                    action(
                        sm,
                        session,
                        true,
                        () -> { throw new IllegalArgumentException("Invalid or oversized input; original retained"); });
                }
            }
        }.allowC2S();
        sm.syncValue(key, value);
        return value;
    }

    private static void field(ParentWidget<?> root, PanelSyncManager sm, GiftEditorSession session, String key,
        String label, int y, Supplier<String> getter, Consumer<String> setter, int limit) {
        root.child(
            IKey.lang(label)
                .asWidget()
                .pos(0, y + 5)
                .scale(0.8f)
                .size(64, 12));
        UtilityControls.Field input = new UtilityControls.Field().kraft();
        root.child(
            input.pos(68, y)
                .widthRelOffset(.5f, -78)
                .height(18)
                .value(write(sm, session, key, getter, setter, limit))
                .setMaxLength(limit)
                .background(NekoVendingTheme.INPUT));
        if ("gift_id".equals(key)) {
            BooleanSyncValue editable = new BooleanSyncValue(() -> session.creating);
            sm.syncValue("gift_id_editable", editable);
            input.setEnabledIf(w -> editable.getBoolValue());
            StringSyncValue savedId = read(sm, "gift_id_read_only", () -> session.draft.id);
            root.child(
                new NekoSingleLineTextWidget(IKey.dynamic(savedId::getStringValue)).pos(72, y + 4)
                    .widthRelOffset(.5f, -86)
                    .height(12)
                    .scale(0.8f)
                    .setEnabledIf(w -> !editable.getBoolValue()));
        }
    }

    private static UtilityControls.ActionButton button(PanelSyncManager sm, GiftEditorSession session, String key,
        String label, int x, int y, int width, boolean admin, Runnable task) {
        InteractionSyncHandler handler = new InteractionSyncHandler().setOnMousePressed(mouse -> {
            if (!sm.isClient() && mouse.mouseButton == 0) {
                final long generation = session.editGeneration;
                action(sm, session, admin, () -> {
                    if (admin && generation != session.editGeneration) {
                        throw new IllegalStateException("Editor page changed; retry the action");
                    }
                    task.run();
                });
            }
        });
        sm.syncValue(key, handler);
        return new UtilityControls.ActionButton().kraft()
            .pos(x, y)
            .size(width, 18)
            .overlay(label.isEmpty() ? IKey.str("") : IKey.lang(label))
            .syncHandler(handler);
    }

    private static void action(PanelSyncManager sm, GiftEditorSession session, boolean admin, Runnable task) {
        if (!(session.player instanceof EntityPlayerMP)) return;
        EntityPlayerMP player = (EntityPlayerMP) session.player;
        final net.minecraft.inventory.Container container = player.openContainer;
        final int windowId = container.windowId;
        ServerTaskScheduler.scheduleServerTask(() -> {
            net.minecraft.server.MinecraftServer server = net.minecraft.server.MinecraftServer.getServer();
            if (server == null || server.getConfigurationManager() == null
                || server.getConfigurationManager()
                    .func_152612_a(player.getCommandSenderName()) != player
                || player.isDead
                || player.playerNetServerHandler == null
                || player.openContainer != container
                || container.windowId != windowId
                || container != sm.getContainer()) return;
            try {
                if (admin) session.requireAdmin();
                session.message = "";
                task.run();
                if (session.message.isEmpty()) session.message = "gtit.gift.ui.done";
            } catch (RuntimeException e) {
                session.message = e.getMessage() == null ? e.getClass()
                    .getSimpleName() : e.getMessage();
            }
        });
    }
}

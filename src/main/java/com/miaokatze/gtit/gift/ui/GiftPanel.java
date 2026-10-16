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
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.PhantomItemSlotSH;
import com.cleanroommc.modularui.value.sync.StringSyncValue;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.Dialog;
import com.cleanroommc.modularui.widgets.ItemDisplayWidget;
import com.cleanroommc.modularui.widgets.PageButton;
import com.cleanroommc.modularui.widgets.PagedWidget;
import com.cleanroommc.modularui.widgets.SlotGroupWidget;
import com.cleanroommc.modularui.widgets.slot.ModularSlot;
import com.cleanroommc.modularui.widgets.slot.PhantomItemSlot;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;
import com.miaokatze.gtit.gift.GiftDefinition;
import com.miaokatze.gtit.gift.GiftIconCatalog;
import com.miaokatze.gtit.gift.GiftRegistry;
import com.miaokatze.gtit.gift.GiftService;
import com.miaokatze.gtit.gui.pocket.PocketGuiTextures;
import com.miaokatze.gtit.trade.preset.ui.TradePresetUI;
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
                    + " × "
                    + gift.randomCount
                    + "\n"
                    + gift.wirelessEU
                    + " EU";
        });
        StringSyncValue update = read(sm, "gift_default_prompt", () -> {
            session.refresh();
            return session.updatePrompt;
        });
        PagedWidget.Controller navigation = new PagedWidget.Controller();
        IPanelHandler preview = sm.syncedPanel("gift_preview", true, (child, handler) -> preview(child, session));
        ParentWidget<?> panel = new ParentWidget<>().size(406, 360);
        panel.child(
            IKey.lang("gtit.gift.ui.title")
                .asWidget()
                .pos(12, 11));
        panel.child(
            button(
                sm,
                session,
                "gift_to_presets",
                "gtit.gift.ui.presets",
                284,
                9,
                93,
                false,
                () -> TradePresetUI.open((EntityPlayerMP) session.player)));
        for (int i = 0; i < 6; i++) {
            final int row = i;
            StringSyncValue name = read(sm, "gift_row_name_" + i, () -> {
                GiftDefinition gift = session.row(row);
                return gift == null ? "" : gift.name;
            });
            StringSyncValue status = read(sm, "gift_row_status_" + i, () -> session.status(session.row(row)));
            StringSyncValue icon = read(sm, "gift_row_icon_" + i, () -> {
                GiftDefinition gift = session.row(row);
                return gift == null || gift.icon != null ? "" : gift.iconKey;
            });
            GenericSyncValue<net.minecraft.item.ItemStack, ?> item = GiftItemSync.readOnly(() -> {
                GiftDefinition gift = session.row(row);
                return gift == null || gift.icon == null ? null : gift.icon.toItemStack();
            });
            sm.syncValue("gift_row_item_" + i, item);
            int y = 39 + i * 32;
            panel.child(
                button(sm, session, "gift_select_" + i, "", 12, y, 174, false, () -> session.select(session.row(row)))
                    .height(29)
                    .overlay(IKey.str("")));
            panel.child(
                IKey.dynamic(name::getStringValue)
                    .asWidget()
                    .pos(42, y + 4)
                    .size(138, 12)
                    .scale(0.8f));
            panel.child(
                new GiftCatalogDrawable(icon::getStringValue).asWidget()
                    .pos(17, y + 5)
                    .size(18)
                    .setEnabledIf(
                        w -> !icon.getStringValue()
                            .isEmpty()));
            panel.child(
                new ItemDisplayWidget().item(item)
                    .pos(17, y + 5)
                    .size(18));
            panel.child(
                IKey.dynamic(() -> translatedStatus(status))
                    .asWidget()
                    .pos(42, y + 18)
                    .scale(0.65f)
                    .size(138, 9));
        }
        panel.child(
            button(
                sm,
                session,
                "gift_list_prev",
                "gtit.gift.ui.previous",
                12,
                235,
                65,
                false,
                () -> session.listPage = Math.max(0, session.listPage - 1)));
        panel.child(button(sm, session, "gift_list_next", "gtit.gift.ui.next", 121, 235, 65, false, () -> {
            session.refresh();
            session.listPage = Math.min(Math.max(0, (session.definitions.size() - 1) / 6), session.listPage + 1);
        }));
        panel.child(
            IKey.dynamic(selection::getStringValue)
                .asWidget()
                .pos(200, 40)
                .size(192, 28));
        panel.child(
            IKey.dynamic(
                () -> IKey.lang(trigger.getStringValue())
                    .get())
                .asWidget()
                .pos(200, 77)
                .size(190, 12));
        panel.child(
            IKey.dynamic(() -> translatedStatus(selectedStatus))
                .asWidget()
                .pos(200, 95)
                .size(190, 12));
        panel.child(
            IKey.lang("gtit.gift.ui.quest")
                .asWidget()
                .pos(200, 115)
                .scale(0.75f));
        panel.child(
            IKey.dynamic(selectedQuest::getStringValue)
                .asWidget()
                .pos(264, 115)
                .size(126, 12));
        panel.child(
            IKey.lang("gtit.gift.ui.reward_summary")
                .asWidget()
                .pos(200, 137)
                .scale(0.8f));
        panel.child(
            IKey.dynamic(rewards::getStringValue)
                .asWidget()
                .pos(200, 151)
                .size(190, 20)
                .scale(0.75f));
        panel.child(
            new ButtonWidget<>().pos(200, 174)
                .size(190, 18)
                .background(PocketGuiTextures.BUTTON)
                .overlay(IKey.lang("gtit.gift.ui.preview"))
                .onMousePressed(mouse -> {
                    preview.openPanel();
                    return true;
                }));
        panel.child(button(sm, session, "gift_claim", "gtit.gift.ui.claim", 200, 196, 190, false, () -> {
            session.message = GiftService.INSTANCE.claim((EntityPlayerMP) session.player, session.selectedId);
            session.invalidate();
        }));
        panel.child(
            new PageButton(1, navigation).pos(200, 220)
                .size(92, 18)
                .background(PocketGuiTextures.BUTTON)
                .overlay(IKey.lang("gtit.gift.ui.edit"))
                .setEnabledIf(w -> admin.getBoolValue()));
        panel.child(
            button(sm, session, "gift_new", "gtit.gift.ui.new", 298, 220, 92, true, () -> { session.create(); })
                .onMousePressed(mouse -> {
                    navigation.setPage(1);
                    return false;
                })
                .setEnabledIf(w -> admin.getBoolValue()));
        StringSyncValue defaults = read(sm, "gift_defaults_state", () -> {
            session.refresh();
            return session.defaultsEnabled ? "gtit.gift.ui.defaults_on" : "gtit.gift.ui.defaults_off";
        });
        panel.child(button(sm, session, "gift_defaults_toggle", "", 200, 241, 190, true, () -> {
            GiftRegistry.INSTANCE.setDefaultsEnabled(!session.defaultsEnabled, session.registryRevision);
            session.invalidate();
        }).overlay(IKey.lang(defaults::getStringValue))
            .setEnabledIf(w -> admin.getBoolValue()));
        panel.child(
            IKey.lang(
                () -> update.getStringValue()
                    .isEmpty() ? "" : "gtit.gift.ui.update." + update.getStringValue())
                .asWidget()
                .pos(12, 263)
                .size(378, 22)
                .scale(0.75f)
                .setEnabledIf(w -> admin.getBoolValue()));
        String[] decisions = { "APPLY", "SKIP", "DISABLE_NOTICE", "ACK" };
        for (int i = 0; i < decisions.length; i++) {
            final String decision = decisions[i];
            panel.child(
                button(
                    sm,
                    session,
                    "gift_default_" + decision,
                    "gtit.gift.ui." + decision.toLowerCase(java.util.Locale.ROOT),
                    12 + i * 96,
                    289,
                    91,
                    true,
                    () -> {
                        GiftRegistry.INSTANCE.resolveDefaultUpdate(decision, session.registryRevision);
                        session.invalidate();
                    }).setEnabledIf(w -> admin.getBoolValue()));
        }
        panel.child(
            IKey.dynamic(() -> messageText(message.getStringValue()))
                .asWidget()
                .pos(12, 315)
                .size(378, 30)
                .scale(0.8f));
        return base("gift_library", 406, 360).child(
            new PagedWidget<>().size(406, 360)
                .controller(navigation)
                .addPage(panel)
                .addPage(editor(sm, session, navigation)))
            .child(
                ButtonWidget.panelCloseButton()
                    .right(7)
                    .top(7));
    }

    private static ParentWidget<?> editor(PanelSyncManager sm, GiftEditorSession session,
        PagedWidget.Controller navigation) {
        ParentWidget<?> panel = new ParentWidget<>().size(406, 360);
        panel.child(
            new PageButton(0, navigation).pos(288, 8)
                .size(82, 18)
                .background(PocketGuiTextures.BUTTON)
                .overlay(IKey.lang("gtit.gift.ui.back")));
        panel.child(
            IKey.lang("gtit.gift.ui.editor")
                .asWidget()
                .pos(12, 10)
                .size(270, 13)
                .scale(0.8f));
        PagedWidget.Controller pages = new PagedWidget.Controller();
        String[] titles = { "general", "required", "random" };
        for (int i = 0; i < 3; i++) {
            panel.child(
                new PageButton(i, pages).pos(12 + i * 126, 29)
                    .size(120, 18)
                    .background(PocketGuiTextures.BUTTON)
                    .overlay(IKey.lang("gtit.gift.ui." + titles[i])));
        }
        panel.child(
            new PagedWidget<>().pos(12, 53)
                .size(382, 185)
                .controller(pages)
                .addPage(general(sm, session))
                .addPage(items(sm, session, false))
                .addPage(items(sm, session, true)));
        panel.child(
            SlotGroupWidget.playerInventory(false)
                .pos(122, 244)
                .background(PocketGuiTextures.SLOT));
        panel.child(button(sm, session, "gift_save", "gtit.gift.ui.save", 12, 244, 99, true, session::save));
        panel.child(button(sm, session, "gift_delete", "gtit.gift.ui.delete", 12, 269, 99, true, session::delete));
        StringSyncValue message = read(sm, "gift_editor_result", () -> session.message);
        panel.child(
            IKey.dynamic(() -> messageText(message.getStringValue()))
                .asWidget()
                .pos(12, 324)
                .size(378, 24)
                .scale(0.75f));
        return panel;
    }

    private static ModularPanel preview(PanelSyncManager sm, GiftEditorSession session) {
        ModularPanel panel = new Dialog<>("gift_preview", null).setDisablePanelsBelow(true)
            .setCloseOnOutOfBoundsClick(false)
            .size(350, 306)
            .background(PocketGuiTextures.CLOTH, PocketGuiTextures.PANEL);
        panel.child(
            ButtonWidget.panelCloseButton()
                .top(7)
                .right(7));
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
                    .background(PocketGuiTextures.SLOT)
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
                IKey.dynamic(label::getStringValue)
                    .asWidget()
                    .pos(42, y)
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
        return panel;
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
        ParentWidget<?> root = new ParentWidget<>().size(382, 185);
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
        root.child(button(sm, session, "gift_trigger", "", 0, 116, 183, true, () -> {
            GiftDefinition.Trigger[] values = GiftDefinition.Trigger.values();
            session.draft.trigger = values[(session.draft.trigger.ordinal() + 1) % values.length];
        }).overlay(
            IKey.lang(
                read(
                    sm,
                    "gift_trigger_text",
                    () -> "gtit.gift.trigger." + session.draft.trigger.name())::getStringValue)));
        root.child(button(sm, session, "gift_policy", "", 191, 116, 191, true, () -> {
            session.draft.updatePolicy = session.draft.updatePolicy == GiftDefinition.UpdatePolicy.FORCED
                ? GiftDefinition.UpdatePolicy.RECOMMENDED
                : GiftDefinition.UpdatePolicy.FORCED;
        }).overlay(
            IKey.lang(
                read(
                    sm,
                    "gift_policy_text",
                    () -> "gtit.gift.policy." + session.draft.updatePolicy.name())::getStringValue)));
        root.child(
            button(
                sm,
                session,
                "gift_enabled",
                "",
                0,
                140,
                105,
                true,
                () -> session.draft.enabled = !session.draft.enabled).overlay(
                    IKey.dynamic(
                        read(sm, "gift_enabled_text", () -> session.draft.enabled ? "ON" : "OFF")::getStringValue)));
        root.child(ghost(sm, session, 0).pos(113, 140));
        root.child(
            IKey.lang("gtit.gift.ui.icon_hint")
                .asWidget()
                .pos(139, 144)
                .scale(0.7f));
        String[] icons = GiftIconCatalog.keys();
        // The catalog has a bounded fixed grid and never registers handlers dynamically.
        for (int i = 0; i < 12; i++) {
            final int index = i;
            StringSyncValue icon = read(sm, "gift_icon_key_" + i, () -> {
                int entry = session.iconPage * 12 + index;
                return entry < icons.length ? icons[entry] : "";
            });
            root.child(button(sm, session, "gift_icon_" + i, "", 26 + i * 27, 165, 25, true, () -> {
                int entry = session.iconPage * 12 + index;
                if (entry >= icons.length) return;
                session.draft.iconKey = icons[entry];
                session.items.setStackInSlot(0, null);
                session.unresolved[0] = null;
            }).height(19)
                .overlay(new GiftCatalogDrawable(icon::getStringValue))
                .setEnabledIf(
                    w -> !icon.getStringValue()
                        .isEmpty())
                .tooltipBuilder(t -> t.addLine(IKey.lang(GiftIconCatalog.labelKey(icon.getStringValue())))));
        }
        root.child(
            button(
                sm,
                session,
                "gift_icon_prev",
                "",
                0,
                165,
                22,
                true,
                () -> session.iconPage = Math.max(0, session.iconPage - 1)).overlay(IKey.str("<")));
        root.child(
            button(
                sm,
                session,
                "gift_icon_next",
                "",
                354,
                165,
                28,
                true,
                () -> session.iconPage = Math.min((icons.length - 1) / 12, session.iconPage + 1))
                    .overlay(IKey.str(">")));
        return root;
    }

    private static ParentWidget<?> items(PanelSyncManager sm, GiftEditorSession session, boolean random) {
        String prefix = random ? "gift_random" : "gift_required";
        ParentWidget<?> root = new ParentWidget<>().size(382, 185);
        root.child(
            IKey.lang("gtit.gift.ui.ghost_hint")
                .asWidget()
                .pos(0, 0)
                .scale(0.75f));
        for (int row = 0; row < GiftEditorSession.PAGE_SIZE; row++) {
            final int slot = 1 + row + (random ? GiftEditorSession.PAGE_SIZE : 0);
            int y = 20 + row * 31;
            root.child(ghost(sm, session, slot).pos(0, y));
            StringSyncValue amount = write(
                sm,
                session,
                prefix + "_amount_" + row,
                () -> session.amount(slot),
                value -> session.amount(slot, value),
                4);
            root.child(
                new TextFieldWidget().pos(24, y)
                    .size(49, 18)
                    .value(amount)
                    .setMaxLength(4)
                    .background(PocketGuiTextures.SLOT)
                    .addTooltipLine(IKey.lang("gtit.gift.ui.amount")));
            StringSyncValue nbt = write(
                sm,
                session,
                prefix + "_nbt_" + row,
                () -> session.nbt(slot),
                value -> session.nbt(slot, value),
                16384);
            root.child(
                new TextFieldWidget().pos(79, y)
                    .size(278, 18)
                    .value(nbt)
                    .setMaxLength(16384)
                    .background(PocketGuiTextures.SLOT)
                    .addTooltipLine(IKey.lang("gtit.gift.ui.nbt")));
            root.child(
                button(sm, session, prefix + "_clear_" + row, "", 360, y, 22, true, () -> session.clear(slot))
                    .overlay(IKey.str("×")));
            StringSyncValue description = read(
                sm,
                prefix + "_item_description_" + row,
                () -> session.itemDescription(slot));
            root.child(
                IKey.dynamic(description::getStringValue)
                    .asWidget()
                    .pos(24, y + 19)
                    .size(358, 10)
                    .scale(0.6f));
        }
        root.child(button(sm, session, prefix + "_prev", "gtit.gift.ui.previous", 0, 150, 83, true, () -> {
            session.editGeneration++;
            if (random) session.randomPage = Math.max(0, session.randomPage - 1);
            else session.requiredPage = Math.max(0, session.requiredPage - 1);
        }));
        StringSyncValue page = read(
            sm,
            prefix + "_page",
            () -> Integer.toString(1 + (random ? session.randomPage : session.requiredPage)) + "/32");
        root.child(
            IKey.dynamic(page::getStringValue)
                .asWidget()
                .pos(166, 155));
        root.child(button(sm, session, prefix + "_next", "gtit.gift.ui.next", 299, 150, 83, true, () -> {
            session.editGeneration++;
            if (random) session.randomPage = Math.min(31, session.randomPage + 1);
            else session.requiredPage = Math.min(31, session.requiredPage + 1);
        }));
        return root;
    }

    private static PhantomItemSlot ghost(PanelSyncManager sm, GiftEditorSession session, int slot) {
        PhantomItemSlotSH handler = new PhantomItemSlotSH(
            new ModularSlot(session.visibleItems, slot).ignoreMaxStackSize(true)
                .filter(stack -> sm.isClient() || session.admin())) {

            @Override
            public void readOnServer(int id, PacketBuffer buffer) throws IOException {
                if (buffer.readableBytes() > 131072) throw new IOException("Oversized ghost item payload");
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
        PhantomItemSlot widget = new PhantomItemSlot().syncHandler(handler);
        widget.background(PocketGuiTextures.SLOT);
        return widget;
    }

    private static ModularPanel base(String name, int width, int height) {
        return new ModularPanel(name).size(width, height)
            .background(PocketGuiTextures.CLOTH, PocketGuiTextures.PANEL);
    }

    private static String translatedStatus(StringSyncValue status) {
        String value = status.getStringValue();
        return value.isEmpty() ? ""
            : IKey.lang("gtit.gift.status." + value)
                .get();
    }

    private static String messageText(String message) {
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
        StringSyncValue value = new StringSyncValue(() -> {
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
            });
        }).allowC2S();
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
                .size(93, 12));
        root.child(
            new TextFieldWidget().pos(98, y)
                .size(284, 18)
                .value(write(sm, session, key, getter, setter, limit))
                .setMaxLength(limit)
                .background(PocketGuiTextures.SLOT));
    }

    private static ButtonWidget<?> button(PanelSyncManager sm, GiftEditorSession session, String key, String label,
        int x, int y, int width, boolean admin, Runnable task) {
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
        return new ButtonWidget<>().pos(x, y)
            .size(width, 18)
            .background(PocketGuiTextures.BUTTON)
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

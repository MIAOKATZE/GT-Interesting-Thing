package com.miaokatze.gtit.trade.preset.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.ChatComponentText;
import net.minecraft.world.World;

import com.cleanroommc.modularui.api.IGuiHolder;
import com.cleanroommc.modularui.api.IPanelHandler;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.factory.AbstractUIFactory;
import com.cleanroommc.modularui.factory.GuiData;
import com.cleanroommc.modularui.factory.GuiManager;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.utils.item.ItemStackHandler;
import com.cleanroommc.modularui.value.sync.BooleanSyncValue;
import com.cleanroommc.modularui.value.sync.InteractionSyncHandler;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.PhantomItemSlotSH;
import com.cleanroommc.modularui.value.sync.StringSyncValue;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widgets.Dialog;
import com.cleanroommc.modularui.widgets.ListWidget;
import com.cleanroommc.modularui.widgets.PagedWidget;
import com.cleanroommc.modularui.widgets.SlotGroupWidget;
import com.cleanroommc.modularui.widgets.slot.ModularSlot;
import com.cleanroommc.modularui.widgets.slot.PhantomItemSlot;
import com.miaokatze.gtit.client.gui.utility.UtilityControls;
import com.miaokatze.gtit.client.gui.utility.UtilityUiStyle;
import com.miaokatze.gtit.trade.preset.TradePresetManager;
import com.miaokatze.gtit.trade.preset.TradePresetManager.Icon;
import com.miaokatze.gtit.trade.preset.TradePresetManager.PresetSummary;
import com.miaokatze.gtit.trade.preset.TradePresetManager.Result;
import com.miaokatze.gtit.trade.preset.TradePresetManager.Snapshot;
import com.miaokatze.gtit.trade.preset.TradePresetManager.TabSummary;

import io.netty.buffer.Unpooled;

/** Server snapshot defines identical widget identities on both sides. Drafts never mutate live trades. */
public final class TradePresetUI extends AbstractUIFactory<TradePresetUI.Data> {

    private static final TradePresetUI FACTORY = new TradePresetUI();
    private static boolean registered;

    private TradePresetUI() {
        super("gtit:trade_presets");
    }

    public static synchronized void init() {
        if (!registered) {
            GuiManager.registerFactory(FACTORY);
            registered = true;
        }
    }

    public static void open(EntityPlayerMP player) {
        open(player, "");
    }

    private static void open(EntityPlayerMP player, String editId) {
        if (!TradePresetAccessHandler.authorized(player)) return;
        open(player, editId, !editId.isEmpty(), TradePresetManager.snapshot(player), null, "");
    }

    private static void open(EntityPlayerMP player, String editId, boolean editing, Snapshot snapshot,
        List<String> pending, String feedback) {
        if (!TradePresetAccessHandler.authorized(player)) return;
        init();
        snapshot.editorOpen = editing;
        snapshot.pendingSelection = pending;
        snapshot.feedback = feedback;
        GuiManager.open(FACTORY, new Data(player, snapshot, editId), player);
    }

    @Override
    public boolean canInteractWith(EntityPlayer player, Data data) {
        return player == data.getPlayer()
            && (!(player instanceof EntityPlayerMP server) || TradePresetAccessHandler.authorized(server));
    }

    @Override
    public IGuiHolder<Data> getGuiHolder(Data data) {
        return TradePresetUI::build;
    }

    @Override
    public void writeGuiData(Data data, PacketBuffer buffer) {
        TradePresetPayloadCodec.writePayload(data.snapshot, data.editId, buffer);
    }

    @Override
    public Data readGuiData(EntityPlayer player, PacketBuffer buffer) {
        // This factory is server-open only. A forged MUI factory request cannot supply its own catalog.
        if (player instanceof EntityPlayerMP) throw new IllegalArgumentException("Use the authorized preset request");
        TradePresetPayloadCodec.Payload payload = TradePresetPayloadCodec.readPayload(buffer);
        return new Data(player, payload.snapshot, payload.editId);
    }

    public static final class Data extends GuiData {

        final Snapshot snapshot;
        final String editId;

        Data(EntityPlayer player, Snapshot snapshot, String editId) {
            super(player);
            this.snapshot = snapshot;
            this.editId = editId;
        }
    }

    private static ModularPanel build(Data data, PanelSyncManager sync, UISettings settings) {
        Session session = new Session(data, sync);
        sync.bindPlayerInventory(data.getPlayer());
        ParentWidget<?> content = new ParentWidget<>().size(480, 330);
        session.label(content, IKey.lang("gtit.preset.title"), 12, 8, 252, 15, 1f);
        session.label(content, IKey.lang("gtit.preset.subtitle"), 12, 25, 444, 11, 0.65f);
        content.child(
            session.button("choose", "gtit.preset.choose", () -> session.navigate("", false))
                .selected(() -> !data.snapshot.editorOpen)
                .pos(12, 39)
                .size(108, 19));
        content.child(
            session.button("create", "gtit.preset.create", () -> session.navigate("", true))
                .selected(() -> data.snapshot.editorOpen)
                .pos(124, 39)
                .size(108, 19));
        session.label(
            content,
            IKey.dynamic(
                () -> session.dirty() ? IKey.lang("gtit.preset.unsaved")
                    .get() : ""),
            242,
            42,
            216,
            12,
            0.7f);
        content.child(
            new PagedWidget<>().pos(12, 64)
                .size(456, 228)
                .addPage(session.chooser())
                .addPage(session.editor())
                .initialPage(data.snapshot.editorOpen ? 1 : 0));
        content.child(UtilityUiStyle.managerSection(8, 298, 464, 26));
        StringSyncValue status = new StringSyncValue(() -> session.status);
        sync.syncValue("result", status);
        session.label(content, IKey.dynamic(status::getStringValue), 13, 301, 454, 20, 0.7f);
        ModularPanel panel = new ModularPanel("gtit.trade.presets") {

            private boolean approved;

            @Override
            public void closeIfOpen() {
                if (!approved && sync.isClient() && session.dirty()) {
                    session.guard(() -> {
                        approved = true;
                        super.closeIfOpen();
                    });
                } else super.closeIfOpen();
            }
        };
        session.panel = panel;
        return UtilityUiStyle.managerScrollable(panel, 480, 330, content);
    }

    private static final class Session {

        final Data data;
        final PanelSyncManager sync;
        final World world;
        final int dimension;
        final Set<String> selected = new ConcurrentSkipListSet<>();
        final Set<Integer> tabs = new ConcurrentSkipListSet<>();
        final ItemStackHandler icon = new ItemStackHandler(1);
        volatile String name = "";
        volatile String id = "";
        volatile String conflicts = "";
        volatile String description = "";
        volatile String status = "";
        volatile boolean replaceTabs;
        boolean replace;
        ModularPanel panel;
        String baseline;
        ItemStack baselineIcon;
        Runnable pendingLeave;
        IPanelHandler leaveDialog;

        Session(Data data, PanelSyncManager sync) {
            this.data = data;
            this.sync = sync;
            this.world = data.getWorld();
            this.dimension = data.getPlayer().dimension;
            this.replaceTabs = data.editId.isEmpty();
            for (PresetSummary preset : data.snapshot.presets) {
                if (preset.selected) selected.add(preset.id);
                if (!preset.id.equals(data.editId)) continue;
                name = preset.name;
                description = preset.description;
                id = preset.builtin ? preset.id + "_copy" : preset.id;
                replace = !preset.builtin;
                conflicts = String.join(",", preset.mutuallyExclusiveIds);
                tabs.addAll(preset.tabIds);
                icon.setStackInSlot(0, preset.icon == null ? null : preset.icon.toStack());
            }
            if (data.snapshot.pendingSelection != null) {
                selected.clear();
                // A missing or invalid catalog entry can be deselected explicitly; applying validates all selected IDs.
                selected.addAll(data.snapshot.pendingSelection);
            } else selected.addAll(data.snapshot.appliedIds);
            status = data.snapshot.feedback;
            if (!data.snapshot.errors.isEmpty())
                status += (status.isEmpty() ? "" : " | ") + String.join("; ", data.snapshot.errors);
            if (status.isEmpty()) status = IKey.lang("gtit.preset.ready")
                .get();
            baseline = draftFingerprint();
            baselineIcon = icon.getStackInSlot(0) == null ? null
                : icon.getStackInSlot(0)
                    .copy();
        }

        EntityPlayerMP player() {
            return (EntityPlayerMP) data.getPlayer();
        }

        /** Every draft edit and action uses the originating MUI container, never the current arbitrary screen. */
        void dispatch(Runnable action) {
            if (sync.isClient()) {
                action.run();
                return;
            }
            final Container expected = sync.getContainer();
            if (expected == null) return;
            final int windowId = expected.windowId;
            TradePresetAccessHandler.enqueue(() -> {
                if (player().openContainer != expected || expected != sync.getContainer()
                    || expected.windowId != windowId
                    || player().worldObj != world
                    || player().dimension != dimension
                    || player().playerNetServerHandler == null
                    || !TradePresetAccessHandler.authorized(player())) return;
                try {
                    action.run();
                } catch (RuntimeException failure) {
                    status = failure.getMessage() == null ? failure.getClass()
                        .getSimpleName() : failure.getMessage();
                }
            });
        }

        void label(ParentWidget<?> parent, IKey text, int x, int y, int width, int height, float scale) {
            parent.child(
                text.color(UtilityUiStyle.MANAGER_TEXT)
                    .asWidget()
                    .pos(x, y)
                    .size(width, height)
                    .scale(scale));
        }

        String draftFingerprint() {
            return name + "\n" + id + "\n" + conflicts + "\n" + description + "\n" + replaceTabs + "\n" + tabs;
        }

        boolean dirty() {
            return data.snapshot.editorOpen && baseline != null
                && (!baseline.equals(draftFingerprint())
                    || !ItemStack.areItemStacksEqual(baselineIcon, icon.getStackInSlot(0)));
        }

        void navigate(String editId, boolean editing) {
            open(player(), editId, editing, TradePresetManager.snapshot(player()), new ArrayList<>(selected), "");
        }

        void guard(Runnable action) {
            if (!dirty()) {
                action.run();
                return;
            }
            pendingLeave = action;
            if (leaveDialog == null) leaveDialog = IPanelHandler.simple(panel, (parent, player) -> {
                ModularPanel dialog = new Dialog<>("gtit_preset_unsaved", null).setDisablePanelsBelow(true)
                    .setCloseOnOutOfBoundsClick(false)
                    .size(264, 126)
                    .background(UtilityUiStyle.MANAGER_PANEL);
                dialog.child(
                    IKey.lang("gtit.preset.leave_title")
                        .asWidget()
                        .pos(12, 12)
                        .size(240, 16));
                dialog.child(
                    IKey.lang("gtit.preset.leave_hint")
                        .asWidget()
                        .pos(12, 35)
                        .size(240, 36)
                        .scale(0.8f));
                dialog.child(
                    new UtilityControls.ActionButton().manager()
                        .pos(12, 91)
                        .size(116, 21)
                        .overlay(IKey.lang("gtit.preset.stay"))
                        .onMousePressed(mouse -> {
                            if (mouse != 0) return false;
                            pendingLeave = null;
                            dialog.closeIfOpen();
                            return true;
                        }));
                dialog.child(
                    new UtilityControls.ActionButton().manager()
                        .pos(136, 91)
                        .size(116, 21)
                        .overlay(IKey.lang("gtit.preset.discard"))
                        .onMousePressed(mouse -> {
                            if (mouse != 0) return false;
                            Runnable next = pendingLeave;
                            pendingLeave = null;
                            dialog.closeIfOpen();
                            if (next != null) next.run();
                            return true;
                        }));
                UtilityUiStyle.styleManagerContent(dialog);
                return dialog;
            }, true);
            leaveDialog.openPanel();
        }

        UtilityControls.ActionButton button(String key, String label, Runnable action) {
            InteractionSyncHandler handler = new InteractionSyncHandler().setOnMousePressed(mouse -> {
                if (sync.isClient() || mouse.mouseButton != 0) return;
                dispatch(action);
            });
            sync.syncValue("action_" + key, handler);
            return new UtilityControls.ActionButton().manager()
                .overlay(
                    IKey.lang(label)
                        .scale(0.75f))
                .syncHandler(handler)
                .onMousePressed(mouse -> {
                    if (mouse != 0) return true;
                    if (!"save".equals(key) && dirty()) guard(() -> handler.onMousePressed(mouse));
                    else {
                        if ("reload".equals(key)) status = IKey.lang("gtit.preset.reloading")
                            .get();
                        handler.onMousePressed(mouse);
                    }
                    return true;
                });
        }

        IWidget chooser() {
            ParentWidget<?> page = new ParentWidget<>().sizeRel(1f);
            page.child(UtilityUiStyle.managerSection(0, 0, 270, 198));
            page.child(UtilityUiStyle.managerSection(276, 0, 180, 198));
            label(
                page,
                IKey.lang("gtit.preset.catalog", data.snapshot.presets.size(), data.snapshot.revision),
                6,
                5,
                258,
                12,
                0.7f);
            ListWidget<IWidget, ?> list = new ListWidget<>().pos(5, 21)
                .size(260, 172);
            int ordinal = 0;
            for (PresetSummary preset : data.snapshot.presets) {
                BooleanSyncValue checked = new BooleanSyncValue(
                    () -> selected.contains(preset.id),
                    value -> dispatch(() -> {
                        if (value) selected.add(preset.id);
                        else selected.remove(preset.id);
                    })).allowC2S();
                sync.syncValue("preset_" + preset.id, checked);
                ParentWidget<?> row = new ParentWidget<>().size(252, 52)
                    .marginBottom(3)
                    .background(UtilityUiStyle.MANAGER_PANEL);
                row.child(
                    new UtilityControls.Toggle().manager()
                        .value(checked)
                        .pos(4, 7)
                        .size(14)
                        .overlay(false, IKey.str(""))
                        .overlay(true, IKey.str("x")));
                ItemStack stack = preset.icon == null ? null : preset.icon.toStack();
                if (stack != null) row.child(
                    new UtilityControls.DecorativeItem().item(stack)
                        .pos(22, 5)
                        .size(24));
                label(row, IKey.str(preset.name), 50, 3, 150, 12, 0.8f);
                label(row, IKey.str(preset.id), 50, 16, 150, 11, 0.6f);
                label(
                    row,
                    IKey.lang(
                        preset.selected ? "gtit.preset.applied"
                            : preset.builtin ? "gtit.preset.builtin" : "gtit.preset.custom"),
                    22,
                    29,
                    174,
                    9,
                    0.65f);
                label(
                    row,
                    preset.empty ? IKey.lang("gtit.preset.empty")
                        : IKey.lang("gtit.preset.contents", preset.tabCount, preset.tradeCount),
                    7,
                    40,
                    238,
                    10,
                    0.65f);
                row.tooltipBuilder(t -> {
                    t.addLine(preset.name);
                    t.addLine(preset.description);
                    t.addLine(IKey.lang("gtit.preset.stable_id", preset.id));
                    t.addLine(IKey.lang("gtit.preset.conflicts", String.join(", ", preset.mutuallyExclusiveIds)));
                    if (preset.empty) t.addLine(IKey.lang("gtit.preset.zero_reason"));
                });
                row.child(
                    button(
                        "edit_" + ordinal++,
                        preset.builtin ? "gtit.preset.copy" : "gtit.preset.edit",
                        () -> navigate(preset.id, true)).pos(203, 5)
                            .size(44, 22));
                list.child(row);
            }
            page.child(list);
            label(
                page,
                IKey.lang(
                    "gtit.preset.applied_title",
                    data.snapshot.appliedIds.size(),
                    data.snapshot.appliedTradeCount),
                282,
                5,
                168,
                14,
                0.7f);
            ListWidget<IWidget, ?> applied = new ListWidget<>().pos(282, 23)
                .size(168, 40);
            for (String id : data.snapshot.appliedIds) applied.child(
                IKey.str(presetName(id))
                    .asWidget()
                    .size(158, 13)
                    .scale(0.7f));
            if (data.snapshot.appliedIds.isEmpty()) applied.child(
                IKey.lang("gtit.preset.none_applied")
                    .asWidget()
                    .size(158, 28)
                    .scale(0.7f));
            page.child(applied);
            label(
                page,
                IKey.lang(
                    data.snapshot.appliedTradeCount == 0 ? "gtit.preset.zero_reason" : "gtit.preset.world_shared"),
                282,
                67,
                168,
                31,
                0.65f);
            label(page, IKey.lang("gtit.preset.pending"), 282, 103, 168, 12, 0.75f);
            ListWidget<IWidget, ?> pending = new ListWidget<>().pos(282, 119)
                .size(168, 33);
            // Fixed rows keep both ends' synchronization identities stable while selection changes.
            for (PresetSummary preset : data.snapshot.presets) {
                pending.child(IKey.dynamic(() -> {
                    boolean chosen = selected.contains(preset.id);
                    if (chosen == preset.selected) return "";
                    return (chosen ? "+ " : "- ") + preset.name;
                })
                    .asWidget()
                    .size(158, 13)
                    .scale(0.7f)
                    .setEnabledIf(w -> selected.contains(preset.id) != preset.selected));
            }
            pending.child(
                IKey.lang("gtit.preset.no_changes")
                    .asWidget()
                    .size(158, 14)
                    .scale(0.65f)
                    .setEnabledIf(w -> selected.equals(new java.util.HashSet<>(data.snapshot.appliedIds))));
            page.child(pending);
            label(page, IKey.lang("gtit.preset.refresh_hint"), 282, 157, 168, 35, 0.65f);
            label(
                page,
                IKey.dynamic(
                    () -> IKey.lang("gtit.preset.selection_hint", selected.size())
                        .get()),
                0,
                207,
                238,
                18,
                0.65f);
            page.child(
                button("reload", "gtit.preset.reload", () -> finish(TradePresetManager.reload(player()), true))
                    .pos(246, 207)
                    .size(100, 20));
            page.child(
                button(
                    "apply",
                    "gtit.preset.apply",
                    () -> finish(
                        TradePresetManager.select(player(), data.snapshot.revision, new ArrayList<>(selected)),
                        false)).selected(() -> true)
                            .pos(352, 207)
                            .size(104, 20));
            return page;
        }

        String presetName(String id) {
            for (PresetSummary preset : data.snapshot.presets) if (preset.id.equals(id)) return preset.name;
            return id;
        }

        IWidget editor() {
            ParentWidget<?> page = new ParentWidget<>().sizeRel(1f);
            page.child(UtilityUiStyle.managerSection(228, 0, 228, 146));
            field(page, "name", "gtit.preset.name", 0, () -> name, value -> name = value, 128);
            field(page, "id", "gtit.preset.id", 31, () -> id, value -> { if (!replace) id = value; }, 64);
            field(page, "conflicts", "gtit.preset.conflict_ids", 62, () -> conflicts, value -> conflicts = value, 1024);
            field(
                page,
                "description",
                "gtit.preset.description",
                93,
                () -> description,
                value -> description = value,
                512);
            BooleanSyncValue contentMode = new BooleanSyncValue(() -> replaceTabs, next -> dispatch(() -> {
                // New presets have no source content to preserve. Source identity is fixed by the server GUI data.
                if (!data.editId.isEmpty()) replaceTabs = next;
            })).allowC2S();
            sync.syncValue("draft_replace_tabs", contentMode);
            if (data.editId.isEmpty()) {
                page.child(
                    IKey.lang("gtit.preset.replace_content")
                        .color(UtilityUiStyle.MANAGER_TEXT)
                        .asWidget()
                        .background(UtilityUiStyle.MANAGER_INSET)
                        .pos(234, 0)
                        .size(216, 22)
                        .scale(0.7f));
            } else {
                page.child(
                    new UtilityControls.Toggle().manager()
                        .value(contentMode)
                        .pos(234, 0)
                        .size(216, 22)
                        .overlay(
                            false,
                            IKey.lang("gtit.preset.keep_content")
                                .scale(0.7f))
                        .overlay(
                            true,
                            IKey.lang("gtit.preset.replace_content")
                                .scale(0.7f))
                        .tooltipBuilder(t -> t.addLine(IKey.lang("gtit.preset.content_mode_hint"))));
            }
            page.child(
                IKey.lang("gtit.preset.tabs")
                    .asWidget()
                    .pos(234, 27)
                    .size(216, 13)
                    .setEnabledIf(w -> replaceTabs));
            page.child(
                IKey.lang("gtit.preset.keep_content_hint")
                    .asWidget()
                    .pos(234, 32)
                    .size(216, 104)
                    .scale(0.75f)
                    .setEnabledIf(w -> !replaceTabs));
            ListWidget<IWidget, ?> choices = new ListWidget<>().pos(234, 42)
                .size(216, 100)
                .setEnabledIf(w -> replaceTabs);
            for (TabSummary tab : data.snapshot.tabs) {
                BooleanSyncValue value = new BooleanSyncValue(() -> tabs.contains(tab.id), enabled -> {
                    dispatch(() -> {
                        if (enabled) tabs.add(tab.id);
                        else tabs.remove(tab.id);
                    });
                }).allowC2S();
                sync.syncValue("tab_" + tab.id, value);
                choices.child(
                    new UtilityControls.Toggle().manager()
                        .value(value)
                        .size(204, 22)
                        .marginBottom(3)
                        .overlay(
                            IKey.str(tab.name + " [" + tab.id + "]")
                                .scale(0.75f)));
            }
            page.child(choices);
            page.child(
                IKey.lang("gtit.preset.icon")
                    .asWidget()
                    .pos(0, 127)
                    .size(165, 12)
                    .scale(0.7f));
            PhantomItemSlotSH ghost = new PhantomItemSlotSH(new ModularSlot(icon, 0)) {

                @Override
                public void readOnServer(int action, PacketBuffer buffer) throws java.io.IOException {
                    if (buffer.readableBytes() > 131072) throw new java.io.IOException("Oversized preset icon payload");
                    byte[] payload = new byte[buffer.readableBytes()];
                    buffer.readBytes(payload);
                    dispatch(() -> {
                        PacketBuffer copy = new PacketBuffer(Unpooled.wrappedBuffer(payload));
                        try {
                            super.readOnServer(action, copy);
                        } catch (java.io.IOException failure) {
                            throw new IllegalArgumentException("Invalid preset icon payload", failure);
                        } finally {
                            copy.release();
                        }
                    });
                }
            };
            sync.syncValue("draft_icon", ghost);
            page.child(
                new PhantomItemSlot().syncHandler(ghost)
                    .background(UtilityUiStyle.MANAGER_SLOT)
                    .pos(198, 126));
            page.child(
                SlotGroupWidget.playerInventory(false)
                    .pos(0, 156));
            page.child(button("save", "gtit.preset.save", () -> {
                List<String> exclusions = new ArrayList<>();
                for (String value : conflicts.split(",")) if (!value.trim()
                    .isEmpty()) exclusions.add(value.trim());
                Result result = TradePresetManager.save(
                    player(),
                    data.snapshot.revision,
                    id,
                    name,
                    description,
                    Icon.fromStack(icon.getStackInSlot(0)),
                    exclusions,
                    new ArrayList<>(tabs),
                    replace,
                    data.editId,
                    replaceTabs);
                finish(result, true);
            }).pos(234, 156)
                .size(216, 24));
            page.child(
                IKey.lang("gtit.preset.save_hint")
                    .asWidget()
                    .pos(234, 187)
                    .size(216, 49)
                    .scale(0.7f));
            return page;
        }

        void field(ParentWidget<?> page, String key, String label, int y, java.util.function.Supplier<String> getter,
            java.util.function.Consumer<String> setter, int max) {
            StringSyncValue value = new StringSyncValue(
                getter,
                next -> { if (next != null && next.length() <= max) dispatch(() -> setter.accept(next)); }).allowC2S();
            sync.syncValue("draft_" + key, value);
            page.child(
                IKey.lang(label)
                    .color(UtilityUiStyle.MANAGER_TEXT)
                    .asWidget()
                    .pos(0, y)
                    .size(218, 11)
                    .scale(0.7f));
            if (replace && "id".equals(key)) {
                page.child(
                    IKey.str(data.editId)
                        .color(UtilityUiStyle.MANAGER_MUTED)
                        .asWidget()
                        .background(UtilityUiStyle.MANAGER_INSET)
                        .pos(0, y + 12)
                        .size(218, 16));
            } else {
                page.child(
                    new UtilityControls.Field().manager()
                        .value(value)
                        .setMaxLength(max)
                        .autoUpdateOnChange(true)
                        .background(UtilityUiStyle.MANAGER_INPUT)
                        .pos(0, y + 12)
                        .size(218, 16));
            }
        }

        void finish(Result result, boolean preserveSelection) {
            status = result.message;
            if (result.success) {
                player().addChatComponentMessage(new ChatComponentText(result.message));
                // Never reopen with a fresh blank status: transmit the completed result and keep pending checks.
                open(
                    player(),
                    "",
                    false,
                    result.snapshot,
                    preserveSelection ? new ArrayList<>(selected) : null,
                    result.message);
            }
        }
    }
}

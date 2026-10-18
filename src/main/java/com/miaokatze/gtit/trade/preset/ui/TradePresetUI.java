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
import net.minecraft.util.EnumChatFormatting;
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
import com.cleanroommc.modularui.utils.Alignment;
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
import com.cleanroommc.modularui.widgets.TextWidget;
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
        session.statusValue = new StringSyncValue(() -> session.status);
        session.statusKindValue = new StringSyncValue(() -> session.statusKind);
        sync.syncValue("result", session.statusValue);
        sync.syncValue("result_kind", session.statusKindValue);
        ParentWidget<?> content = new ParentWidget<>().size(480, 330);
        session.label(
            content,
            IKey.lang("gtit.preset.title")
                .style(EnumChatFormatting.BOLD),
            12,
            9,
            242,
            18,
            1.1f)
            .color(UtilityUiStyle.MANAGER_ACCENT)
            .tooltipBuilder(t -> t.addLine(IKey.lang("gtit.preset.subtitle")));
        content.child(
            session.button("choose", "gtit.preset.choose", () -> session.navigate("", false))
                .selected(() -> !data.snapshot.editorOpen)
                .pos(12, 35)
                .size(105, 22));
        content.child(
            session.button("create", "gtit.preset.create", () -> session.navigate("", true))
                .selected(() -> data.snapshot.editorOpen)
                .pos(122, 35)
                .size(105, 22));
        session.label(
            content,
            IKey.dynamic(
                () -> session.dirty() ? IKey.lang("gtit.preset.unsaved")
                    .get() : ""),
            242,
            39,
            214,
            14,
            0.9f)
            .color(UtilityUiStyle.MANAGER_WARNING);
        content.child(
            new PagedWidget<>().pos(12, 64)
                .size(456, 228)
                .addPage(session.chooser())
                .addPage(session.editor())
                .initialPage(data.snapshot.editorOpen ? 1 : 0));
        content.child(UtilityUiStyle.managerSection(8, 299, 464, 24));
        session.label(content, IKey.dynamic(session::statusLine), 15, 304, 367, 13, 0.9f)
            .color(session::statusColor);
        content.child(
            new UtilityControls.ActionButton().manager()
                .pos(394, 301)
                .size(74, 20)
                .overlay(
                    IKey.lang("gtit.preset.ui.details")
                        .scale(0.9f))
                .onMousePressed(mouse -> {
                    if (mouse != 0) return true;
                    session.showDetails();
                    return true;
                }));
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
        volatile String statusKind = "ready";
        StringSyncValue statusValue;
        StringSyncValue statusKindValue;
        IPanelHandler detailsDialog;
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
            statusKind = !data.snapshot.errors.isEmpty() ? "notices" : status.isEmpty() ? "ready" : "updated";
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
                    statusKind = "error";
                    status = failure.getMessage() == null ? failure.getClass()
                        .getSimpleName() : failure.getMessage();
                }
            });
        }

        TextWidget<?> label(ParentWidget<?> parent, IKey text, int x, int y, int width, int height, float scale) {
            // StyledText defaults to Center; apply alignment on the actual widget explicitly.
            TextWidget<?> widget = new TextWidget<>(text).alignment(Alignment.CenterLeft)
                .color(UtilityUiStyle.MANAGER_TEXT)
                .shadow(false)
                .pos(x, y)
                .size(width, height)
                .scale(scale);
            parent.child(widget);
            return widget;
        }

        String statusLine() {
            String kind = statusKindValue.getStringValue();
            return IKey.lang("gtit.preset.ui.status." + kind, data.snapshot.errors.size())
                .get();
        }

        int statusColor() {
            return switch (statusKindValue.getStringValue()) {
                case "error" -> UtilityUiStyle.MANAGER_ERROR;
                case "notices" -> UtilityUiStyle.MANAGER_WARNING;
                case "updated" -> UtilityUiStyle.MANAGER_SUCCESS;
                default -> UtilityUiStyle.MANAGER_MUTED;
            };
        }

        String detailsText() {
            String feedback = statusValue.getStringValue();
            StringBuilder text = new StringBuilder();
            if (!feedback.isEmpty()) text.append(feedback)
                .append("\n\n");
            if (!data.snapshot.errors.isEmpty()) text.append(String.join("\n\n", data.snapshot.errors))
                .append("\n\n");
            return text.append(
                IKey.lang("gtit.preset.ui.help")
                    .get())
                .toString();
        }

        void showDetails() {
            if (detailsDialog == null) detailsDialog = IPanelHandler.simple(panel, (parent, player) -> {
                ModularPanel dialog = new Dialog<>("gtit_preset_details", null).setDisablePanelsBelow(true)
                    .setCloseOnOutOfBoundsClick(false)
                    .size(358, 224)
                    .background(UtilityUiStyle.MANAGER_PANEL);
                dialog.child(
                    new TextWidget<>(
                        IKey.lang("gtit.preset.ui.details_title")
                            .style(EnumChatFormatting.BOLD)).alignment(Alignment.CenterLeft)
                                .color(UtilityUiStyle.MANAGER_ACCENT)
                                .shadow(false)
                                .pos(12, 10)
                                .size(330, 16));
                dialog.child(
                    new ListWidget<>().pos(12, 34)
                        .size(334, 146)
                        .child(
                            new TextWidget<>(IKey.dynamic(this::detailsText)).alignment(Alignment.TopLeft)
                                .color(UtilityUiStyle.MANAGER_TEXT)
                                .shadow(false)
                                .width(322)
                                .padding(2)
                                .scale(0.9f)));
                dialog.child(
                    new UtilityControls.ActionButton().manager()
                        .pos(236, 190)
                        .size(110, 22)
                        .overlay(
                            IKey.lang("gtit.preset.ui.close")
                                .scale(0.9f))
                        .onMousePressed(mouse -> {
                            if (mouse != 0) return true;
                            dialog.closeIfOpen();
                            return true;
                        }));
                UtilityUiStyle.styleManagerContent(dialog);
                return dialog;
            }, true);
            detailsDialog.openPanel();
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
                        .scale(0.9f))
                .syncHandler(handler)
                .onMousePressed(mouse -> {
                    if (mouse != 0) return true;
                    if (!"save".equals(key) && dirty()) guard(() -> handler.onMousePressed(mouse));
                    else {
                        if ("reload".equals(key)) {
                            statusKind = "loading";
                            status = IKey.lang("gtit.preset.reloading")
                                .get();
                            statusKindValue.setStringValue(statusKind, false, false);
                            statusValue.setStringValue(status, false, false);
                        }
                        handler.onMousePressed(mouse);
                    }
                    return true;
                });
        }

        IWidget chooser() {
            ParentWidget<?> page = new ParentWidget<>().sizeRel(1f);
            page.child(UtilityUiStyle.managerSection(0, 0, 290, 198));
            page.child(UtilityUiStyle.managerSection(296, 0, 160, 198));
            label(
                page,
                IKey.lang("gtit.preset.ui.catalog", data.snapshot.presets.size())
                    .style(EnumChatFormatting.BOLD),
                8,
                5,
                274,
                14,
                1f);
            ListWidget<IWidget, ?> list = new ListWidget<>().pos(6, 24)
                .size(278, 168);
            int ordinal = 0;
            for (PresetSummary preset : data.snapshot.presets) {
                BooleanSyncValue checked = new BooleanSyncValue(
                    () -> selected.contains(preset.id),
                    value -> dispatch(() -> {
                        if (value) selected.add(preset.id);
                        else selected.remove(preset.id);
                    })).allowC2S();
                sync.syncValue("preset_" + preset.id, checked);
                ParentWidget<?> row = new ParentWidget<>().size(270, 52)
                    .marginBottom(4)
                    .background(
                        (context, x, y, width, height,
                            theme) -> (selected.contains(preset.id) ? UtilityUiStyle.MANAGER_ROW_SELECTED
                                : UtilityUiStyle.MANAGER_ROW).draw(context, x, y, width, height, theme));
                row.child(
                    new UtilityControls.Toggle().manager()
                        .value(checked)
                        .pos(8, 18)
                        .size(16)
                        .overlay(false, IKey.str(""))
                        .overlay(true, IKey.str("x")));
                ItemStack stack = preset.icon == null ? null : preset.icon.toStack();
                if (stack != null) row.child(
                    new UtilityControls.DecorativeItem().item(stack)
                        .pos(30, 14)
                        .size(24));
                label(
                    row,
                    IKey.str(compact(preset.name, 15))
                        .style(EnumChatFormatting.BOLD),
                    62,
                    5,
                    146,
                    14,
                    1f);
                label(
                    row,
                    preset.empty ? IKey.lang("gtit.preset.ui.empty")
                        : IKey.lang("gtit.preset.contents", preset.tabCount, preset.tradeCount),
                    62,
                    22,
                    146,
                    12,
                    0.9f);
                label(
                    row,
                    IKey.lang(
                        preset.selected ? "gtit.preset.applied"
                            : preset.builtin ? "gtit.preset.builtin" : "gtit.preset.custom"),
                    62,
                    37,
                    146,
                    11,
                    0.85f).color(preset.selected ? UtilityUiStyle.MANAGER_SUCCESS : UtilityUiStyle.MANAGER_MUTED);
                row.tooltipBuilder(t -> {
                    t.addLine(
                        IKey.str(preset.name)
                            .style(EnumChatFormatting.BOLD));
                    t.addLine(preset.description);
                    t.addLine(IKey.lang("gtit.preset.stable_id", preset.id));
                    t.addLine(IKey.lang("gtit.preset.conflicts", String.join(", ", preset.mutuallyExclusiveIds)));
                });
                row.child(
                    button(
                        "edit_" + ordinal++,
                        preset.builtin ? "gtit.preset.copy" : "gtit.preset.edit",
                        () -> navigate(preset.id, true)).pos(216, 15)
                            .size(46, 22));
                list.child(row);
            }
            page.child(list);
            label(
                page,
                IKey.lang("gtit.preset.ui.applied")
                    .style(EnumChatFormatting.BOLD),
                304,
                5,
                144,
                14,
                1f);
            label(
                page,
                IKey.lang(
                    "gtit.preset.ui.applied_count",
                    data.snapshot.appliedIds.size(),
                    data.snapshot.appliedTradeCount)
                    .style(EnumChatFormatting.BOLD),
                304,
                24,
                144,
                14,
                0.95f).color(UtilityUiStyle.MANAGER_SUCCESS);
            ListWidget<IWidget, ?> applied = new ListWidget<>().pos(304, 44)
                .size(144, 44);
            for (String id : data.snapshot.appliedIds) applied.child(
                new TextWidget<>(IKey.str(compact(presetName(id), 15))).alignment(Alignment.CenterLeft)
                    .color(UtilityUiStyle.MANAGER_TEXT)
                    .shadow(false)
                    .size(136, 15)
                    .scale(0.9f));
            if (data.snapshot.appliedIds.isEmpty()) applied.child(
                new TextWidget<>(IKey.lang("gtit.preset.ui.none")).alignment(Alignment.CenterLeft)
                    .color(UtilityUiStyle.MANAGER_MUTED)
                    .shadow(false)
                    .size(136, 15)
                    .scale(0.9f));
            page.child(applied);
            label(
                page,
                IKey.lang("gtit.preset.pending")
                    .style(EnumChatFormatting.BOLD),
                304,
                101,
                144,
                14,
                1f);
            ListWidget<IWidget, ?> pending = new ListWidget<>().pos(304, 121)
                .size(144, 49);
            for (PresetSummary preset : data.snapshot.presets) {
                pending.child(
                    new TextWidget<>(
                        IKey.dynamic(() -> (selected.contains(preset.id) ? "+ " : "- ") + compact(preset.name, 14)))
                            .alignment(Alignment.CenterLeft)
                            .color(
                                () -> selected.contains(preset.id) ? UtilityUiStyle.MANAGER_SUCCESS
                                    : UtilityUiStyle.MANAGER_WARNING)
                            .shadow(false)
                            .size(136, 15)
                            .scale(0.9f)
                            .setEnabledIf(w -> selected.contains(preset.id) != preset.selected));
            }
            pending.child(
                new TextWidget<>(IKey.lang("gtit.preset.ui.no_changes")).alignment(Alignment.CenterLeft)
                    .color(UtilityUiStyle.MANAGER_MUTED)
                    .shadow(false)
                    .size(136, 15)
                    .scale(0.9f)
                    .setEnabledIf(w -> selected.equals(new java.util.HashSet<>(data.snapshot.appliedIds))));
            page.child(pending);
            label(page, IKey.lang("gtit.preset.ui.apply_hint"), 304, 177, 144, 13, 0.85f)
                .color(UtilityUiStyle.MANAGER_MUTED);
            label(
                page,
                IKey.dynamic(
                    () -> IKey.lang("gtit.preset.ui.checked", selected.size())
                        .get()),
                8,
                209,
                280,
                15,
                0.9f).color(UtilityUiStyle.MANAGER_MUTED);
            page.child(
                button("reload", "gtit.preset.reload", () -> finish(TradePresetManager.reload(player()), true))
                    .pos(296, 205)
                    .size(76, 22)
                    .tooltipBuilder(t -> t.addLine(IKey.lang("gtit.preset.refresh_hint"))));
            page.child(
                button(
                    "apply",
                    "gtit.preset.apply",
                    () -> finish(
                        TradePresetManager.select(player(), data.snapshot.revision, new ArrayList<>(selected)),
                        false)).selected(() -> true)
                            .pos(378, 205)
                            .size(78, 22));
            return page;
        }

        private static String compact(String text, int characters) {
            if (text.codePointCount(0, text.length()) <= characters) return text;
            return text.substring(0, text.offsetByCodePoints(0, characters - 3)) + "...";
        }

        String presetName(String id) {
            for (PresetSummary preset : data.snapshot.presets) if (preset.id.equals(id)) return preset.name;
            return id;
        }

        IWidget editor() {
            ParentWidget<?> page = new ParentWidget<>().sizeRel(1f);
            page.child(UtilityUiStyle.managerSection(0, 0, 290, 228));
            page.child(UtilityUiStyle.managerSection(296, 0, 160, 198));
            label(
                page,
                IKey.lang("gtit.preset.ui.metadata")
                    .style(EnumChatFormatting.BOLD),
                8,
                5,
                274,
                14,
                1f);
            label(
                page,
                IKey.lang("gtit.preset.ui.content")
                    .style(EnumChatFormatting.BOLD),
                304,
                5,
                144,
                14,
                1f);
            field(page, "name", "gtit.preset.name", 24, () -> name, value -> name = value, 128);
            field(page, "id", "gtit.preset.ui.id", 48, () -> id, value -> { if (!replace) id = value; }, 64);
            field(page, "conflicts", "gtit.preset.ui.conflicts", 72, () -> conflicts, value -> conflicts = value, 1024);
            field(
                page,
                "description",
                "gtit.preset.description",
                96,
                () -> description,
                value -> description = value,
                512);
            BooleanSyncValue contentMode = new BooleanSyncValue(
                () -> replaceTabs,
                next -> dispatch(() -> { if (!data.editId.isEmpty()) replaceTabs = next; })).allowC2S();
            sync.syncValue("draft_replace_tabs", contentMode);
            if (data.editId.isEmpty()) {
                label(page, IKey.lang("gtit.preset.ui.select_tabs"), 304, 27, 144, 14, 0.9f)
                    .color(UtilityUiStyle.MANAGER_MUTED);
            } else {
                page.child(
                    new UtilityControls.Toggle().manager()
                        .value(contentMode)
                        .pos(304, 26)
                        .size(16)
                        .overlay(false, IKey.str(""))
                        .overlay(true, IKey.str("x"))
                        .tooltipBuilder(t -> t.addLine(IKey.lang("gtit.preset.content_mode_hint"))));
                label(page, IKey.lang("gtit.preset.ui.replace"), 326, 27, 122, 14, 0.9f);
            }
            label(page, IKey.lang("gtit.preset.ui.keep_content"), 304, 53, 144, 44, 0.9f).alignment(Alignment.TopLeft)
                .color(UtilityUiStyle.MANAGER_MUTED)
                .setEnabledIf(w -> !replaceTabs);
            ListWidget<IWidget, ?> choices = new ListWidget<>().pos(304, 50)
                .size(144, 126)
                .setEnabledIf(w -> replaceTabs);
            for (TabSummary tab : data.snapshot.tabs) {
                BooleanSyncValue value = new BooleanSyncValue(() -> tabs.contains(tab.id), enabled -> dispatch(() -> {
                    if (enabled) tabs.add(tab.id);
                    else tabs.remove(tab.id);
                })).allowC2S();
                sync.syncValue("tab_" + tab.id, value);
                ParentWidget<?> row = new ParentWidget<>().size(136, 21)
                    .marginBottom(2)
                    .background(
                        (context, x, y, width, height,
                            theme) -> (tabs.contains(tab.id) ? UtilityUiStyle.MANAGER_ROW_SELECTED
                                : UtilityUiStyle.MANAGER_ROW).draw(context, x, y, width, height, theme));
                row.child(
                    new UtilityControls.Toggle().manager()
                        .value(value)
                        .pos(4, 3)
                        .size(14)
                        .overlay(false, IKey.str(""))
                        .overlay(true, IKey.str("x")));
                label(row, IKey.str(compact(tab.name, 14)), 24, 4, 108, 13, 0.9f)
                    .tooltipBuilder(t -> t.addLine(tab.name + " [" + tab.id + "]"));
                choices.child(row);
            }
            if (data.snapshot.tabs.isEmpty()) choices.child(
                new TextWidget<>(IKey.lang("gtit.preset.ui.no_tabs")).alignment(Alignment.TopLeft)
                    .color(UtilityUiStyle.MANAGER_MUTED)
                    .shadow(false)
                    .size(136, 36)
                    .scale(0.9f));
            page.child(choices);
            label(page, IKey.lang("gtit.preset.ui.icon"), 8, 126, 54, 14, 0.9f);
            label(page, IKey.lang("gtit.preset.ui.ghost"), 92, 126, 190, 14, 0.85f).color(UtilityUiStyle.MANAGER_MUTED);
            label(page, IKey.lang("gtit.preset.ui.inventory"), 8, 151, 54, 14, 0.9f);
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
                    .pos(66, 123));
            page.child(
                SlotGroupWidget.playerInventory(false)
                    .pos(66, 147));
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
            }).selected(() -> true)
                .pos(296, 205)
                .size(160, 22)
                .tooltipBuilder(t -> t.addLine(IKey.lang("gtit.preset.save_hint"))));
            label(page, IKey.lang("gtit.preset.ui.save_hint"), 304, 181, 144, 13, 0.85f)
                .color(UtilityUiStyle.MANAGER_MUTED);
            return page;
        }

        void field(ParentWidget<?> page, String key, String label, int y, java.util.function.Supplier<String> getter,
            java.util.function.Consumer<String> setter, int max) {
            StringSyncValue value = new StringSyncValue(
                getter,
                next -> { if (next != null && next.length() <= max) dispatch(() -> setter.accept(next)); }).allowC2S();
            sync.syncValue("draft_" + key, value);
            label(page, IKey.lang(label), 8, y + 3, 54, 14, 0.9f).color(UtilityUiStyle.MANAGER_MUTED);
            if (replace && "id".equals(key)) {
                label(page, IKey.str(compact(data.editId, 35)), 66, y, 216, 18, 0.9f)
                    .background(UtilityUiStyle.MANAGER_INPUT)
                    .padding(4, 0)
                    .tooltipBuilder(t -> {
                        t.addLine(data.editId);
                        t.addLine(IKey.lang("gtit.preset.id"));
                    });
            } else {
                page.child(
                    new UtilityControls.Field().manager()
                        .value(value)
                        .setMaxLength(max)
                        .autoUpdateOnChange(true)
                        .background(UtilityUiStyle.MANAGER_INPUT)
                        .pos(66, y)
                        .size(216, 18)
                        .tooltipBuilder(t -> {
                            t.addLine(IKey.lang(label));
                            if ("id".equals(key)) t.addLine(IKey.lang("gtit.preset.id"));
                            if ("conflicts".equals(key)) t.addLine(IKey.lang("gtit.preset.conflict_ids"));
                        }));
            }
        }

        void finish(Result result, boolean preserveSelection) {
            status = result.message;
            statusKind = result.success ? "updated" : "error";
            if (result.success) {
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

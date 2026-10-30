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
import com.miaokatze.gtit.client.gui.NekoSingleLineTextWidget;
import com.miaokatze.gtit.client.gui.NekoVendingTheme;
import com.miaokatze.gtit.client.gui.NekoWorkbenchPanel;
import com.miaokatze.gtit.client.gui.NekoWorkbenchStyle;
import com.miaokatze.gtit.client.gui.utility.UtilityControls;
import com.miaokatze.gtit.client.gui.utility.UtilityUiStyle;
import com.miaokatze.gtit.gui.vm.edit.NekoGhostSlot;
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
        NekoWorkbenchPanel panel = new NekoWorkbenchPanel("gtit.trade.presets") {

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
        panel.child(panel.place(NekoWorkbenchStyle.section(0, 0, 768, 34), l -> l.rect(0, 0, l.width, 34)));
        panel.child(
            IKey.lang("gtit.preset.title")
                .style(EnumChatFormatting.BOLD)
                .color(NekoVendingTheme.INK)
                .asWidget()
                .pos(16, 10)
                .size(250, 18));
        panel.child(
            panel.place(
                IKey.str("NEKO / PROFILE WORKBENCH · 独立预设中心")
                    .color(NekoVendingTheme.MUTED)
                    .asWidget(),
                l -> l.rect(l.width - 330, 12, 280, 13)));
        panel.child(
            panel.place(NekoVendingTheme.button(() -> "×", panel::closeIfOpen), l -> l.rect(l.width - 38, 7, 24, 22)));
        panel.child(
            session.button("choose", "gtit.preset.choose", () -> session.navigate("", false))
                .selected(() -> !data.snapshot.editorOpen)
                .pos(12, 46)
                .size(138, 24));
        panel.child(
            session.button("create", "gtit.preset.create", () -> session.navigate("", true))
                .selected(() -> data.snapshot.editorOpen)
                .pos(158, 46)
                .size(138, 24));
        panel.child(
            IKey.dynamic(
                () -> session.dirty() ? IKey.lang("gtit.preset.unsaved")
                    .get() : "选择仅预览 · 明确应用后生效")
                .color(NekoVendingTheme.MUTED)
                .asWidget()
                .pos(310, 52)
                .size(320, 14));
        panel.child(
            new PagedWidget<>().sizeRel(1f)
                .addPage(panel.place(session.chooser(), l -> l.rect(12, 80, l.width - 24, l.height - 116)))
                .addPage(panel.place(session.editor(), l -> l.editor()))
                .initialPage(data.snapshot.editorOpen ? 1 : 0));
        panel.child(panel.place(NekoWorkbenchStyle.section(0, 0, 1, 1), l -> l.rect(0, l.height - 28, l.width, 28)));
        panel.child(
            panel.place(
                new NekoSingleLineTextWidget(IKey.dynamic(session::statusLine)).color(session::statusColor),
                l -> l.rect(14, l.height - 20, l.width - 130, 14)));
        panel.child(
            panel.place(
                new UtilityControls.ActionButton().kraft()
                    .overlay(IKey.lang("gtit.preset.ui.details"))
                    .onMousePressed(mouse -> {
                        if (mouse == 0) session.showDetails();
                        return true;
                    }),
                l -> l.rect(l.width - 104, l.height - 24, 90, 20)));
        panel.catalog(() -> data.snapshot.editorOpen);
        NekoWorkbenchStyle.style(panel);
        return panel;
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
        NekoWorkbenchPanel panel;
        String previewId = "";
        String baseline;
        ItemStack baselineIcon;
        Runnable pendingLeave;
        IPanelHandler leaveDialog;
        IPanelHandler defaultUpdateDialog;
        Runnable pendingDefaultApply;

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
            if (data.snapshot.presets.stream()
                .anyMatch(preset -> "gtit-base".equals(preset.id))) selected.add("gtit-base");
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
            TradePresetAccessHandler.enqueue(player(), () -> {
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
            TextWidget<?> widget = height <= 26 ? new NekoSingleLineTextWidget(text) : new TextWidget<>(text);
            widget.alignment(Alignment.CenterLeft)
                .color(NekoVendingTheme.INK)
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
                default -> NekoVendingTheme.MUTED;
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
                    .background(NekoVendingTheme.PANEL);
                dialog.child(
                    new TextWidget<>(
                        IKey.lang("gtit.preset.ui.details_title")
                            .style(EnumChatFormatting.BOLD)).alignment(Alignment.CenterLeft)
                                .color(NekoVendingTheme.GOLD)
                                .shadow(false)
                                .pos(12, 10)
                                .size(330, 16));
                dialog.child(
                    new ListWidget<>().pos(12, 34)
                        .size(334, 146)
                        .child(
                            new TextWidget<>(IKey.dynamic(this::detailsText)).alignment(Alignment.TopLeft)
                                .color(NekoVendingTheme.INK)
                                .shadow(false)
                                .width(322)
                                .padding(2)
                                .scale(0.9f)));
                dialog.child(
                    new UtilityControls.ActionButton().kraft()
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
                NekoWorkbenchStyle.style(dialog);
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
                    .background(NekoVendingTheme.PANEL);
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
                    new UtilityControls.ActionButton().kraft()
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
                    new UtilityControls.ActionButton().kraft()
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
                NekoWorkbenchStyle.style(dialog);
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
            return new UtilityControls.ActionButton().kraft()
                .overlay(
                    IKey.lang(label)
                        .scale(0.9f))
                .syncHandler(handler)
                .onMousePressed(mouse -> {
                    if (mouse != 0) return true;
                    Runnable perform = () -> {
                        if ("apply".equals(key) && data.snapshot.presets.stream()
                            .anyMatch(
                                preset -> selected.contains(preset.id) && preset.defaultUpdateAvailable
                                    && preset.overridden)) {
                            confirmDefaultUpdate(() -> handler.onMousePressed(mouse));
                            return;
                        }
                        if ("reload".equals(key)) {
                            statusKind = "loading";
                            status = IKey.lang("gtit.preset.reloading")
                                .get();
                            statusKindValue.setStringValue(statusKind, false, false);
                            statusValue.setStringValue(status, false, false);
                        }
                        handler.onMousePressed(mouse);
                    };
                    if (!"save".equals(key) && dirty()) guard(perform);
                    else perform.run();
                    return true;
                });
        }

        void confirmDefaultUpdate(Runnable apply) {
            pendingDefaultApply = apply;
            if (defaultUpdateDialog == null) defaultUpdateDialog = IPanelHandler.simple(panel, (parent, player) -> {
                ModularPanel dialog = new Dialog<>("gtit_default_trade_update", null).setDisablePanelsBelow(true)
                    .setCloseOnOutOfBoundsClick(false)
                    .size(264, 126)
                    .background(NekoVendingTheme.PANEL);
                dialog.child(
                    IKey.lang("gtit.trade.update.title")
                        .style(EnumChatFormatting.BOLD)
                        .asWidget()
                        .alignment(Alignment.CenterLeft)
                        .pos(12, 12)
                        .size(240, 16));
                dialog.child(
                    IKey.lang("gtit.trade.update.warning")
                        .asWidget()
                        .alignment(Alignment.TopLeft)
                        .pos(12, 39)
                        .size(240, 30)
                        .scale(0.9f));
                dialog.child(
                    new UtilityControls.ActionButton().kraft()
                        .pos(12, 91)
                        .size(116, 22)
                        .overlay(
                            IKey.lang("gtit.trade.update.later")
                                .scale(0.9f))
                        .onMousePressed(mouse -> {
                            if (mouse != 0) return true;
                            pendingDefaultApply = null;
                            dialog.closeIfOpen();
                            return true;
                        }));
                dialog.child(
                    new UtilityControls.ActionButton().kraft()
                        .selected(() -> true)
                        .pos(136, 91)
                        .size(116, 22)
                        .overlay(
                            IKey.lang("gtit.trade.update.apply")
                                .scale(0.9f))
                        .onMousePressed(mouse -> {
                            if (mouse != 0) return true;
                            Runnable confirmed = pendingDefaultApply;
                            pendingDefaultApply = null;
                            dialog.closeIfOpen();
                            if (confirmed != null) confirmed.run();
                            return true;
                        }));
                NekoWorkbenchStyle.style(dialog);
                return dialog;
            }, true);
            defaultUpdateDialog.openPanel();
        }

        PresetSummary preview() {
            for (PresetSummary preset : data.snapshot.presets) if (preset.id.equals(previewId)) return preset;
            return data.snapshot.presets.isEmpty() ? null : data.snapshot.presets.get(0);
        }

        String previewText() {
            PresetSummary p = preview();
            if (p == null) return IKey.lang("gtit.preset.ui.none")
                .get();
            return p.name + "\n\n"
                + p.description
                + "\n\n"
                + "PRESET / "
                + p.id
                + "\n来源: "
                + p.sourceKind
                + (p.overridden ? " · 本地覆盖" : "")
                + "\n\n"
                + IKey.lang("gtit.preset.contents", p.tabCount, p.tradeCount)
                    .get()
                + "\n\n"
                + IKey.lang("gtit.preset.conflicts", String.join(", ", p.mutuallyExclusiveIds))
                    .get()
                + "\n\n独立预设贸易页；不改写默认 / 自定义贸易。\n领取记录、库存和已结算记录保留。";
        }

        IWidget chooser() {
            ParentWidget<?> page = new ParentWidget<>().sizeRel(1f);
            ParentWidget<?> library = NekoWorkbenchStyle.section(0, 0, 280, 300)
                .heightRel(1f);
            ParentWidget<?> detail = NekoWorkbenchStyle.section(288, 0, 250, 300)
                .widthRelOffset(1f, -484)
                .heightRel(1f);
            ParentWidget<?> impact = NekoWorkbenchStyle.section(0, 0, 188, 300)
                .right(0)
                .heightRel(1f);
            page.child(library);
            page.child(detail);
            page.child(impact);
            label(
                library,
                IKey.lang("gtit.preset.ui.catalog", data.snapshot.presets.size())
                    .style(EnumChatFormatting.BOLD),
                10,
                9,
                260,
                16,
                1f);
            ListWidget<IWidget, ?> list = new ListWidget<>().pos(6, 32)
                .width(268)
                .heightRelOffset(1f, -65);
            int ordinal = 0;
            for (PresetSummary preset : data.snapshot.presets) {
                final boolean fixed = "gtit-base".equals(preset.id);
                BooleanSyncValue checked = new BooleanSyncValue(
                    () -> selected.contains(preset.id),
                    value -> dispatch(() -> {
                        if (value || fixed) selected.add(preset.id);
                        else selected.remove(preset.id);
                    })).allowC2S();
                sync.syncValue("preset_" + preset.id, checked);
                ParentWidget<?> row = new ParentWidget<>().size(260, 76)
                    .marginBottom(5)
                    .background(NekoVendingTheme.row(() -> preset == preview()));
                row.child(
                    new UtilityControls.ActionButton().kraft()
                        .pos(30, 4)
                        .size(222, 42)
                        .background(com.cleanroommc.modularui.api.drawable.IDrawable.EMPTY)
                        .overlay(IKey.str(""))
                        .onMousePressed(mouse -> {
                            if (mouse == 0) previewId = preset.id;
                            return true;
                        }));
                if (fixed) label(row, IKey.str("✓"), 7, 17, 16, 16, 1f)
                    .tooltipBuilder(t -> t.addLine(IKey.lang("gtit.trade.foundation")));
                else row.child(
                    new UtilityControls.Toggle().kraft()
                        .value(checked)
                        .pos(7, 17)
                        .size(16)
                        .overlay(false, IKey.str(""))
                        .overlay(true, IKey.str("✓")));
                ItemStack stack = preset.icon == null ? null : preset.icon.toStack();
                if (stack != null) row.child(
                    new UtilityControls.DecorativeItem().item(stack)
                        .pos(31, 12)
                        .size(22)
                        .background(NekoVendingTheme.SLOT));
                label(
                    row,
                    IKey.str(preset.name)
                        .style(EnumChatFormatting.BOLD),
                    59,
                    9,
                    188,
                    15,
                    1f);
                label(
                    row,
                    IKey.lang("gtit.preset.contents", preset.tabCount, preset.tradeCount),
                    59,
                    29,
                    188,
                    12,
                    .85f);
                label(row, IKey.str(preset.id), 8, 56, 173, 12, .75f).color(NekoVendingTheme.MUTED);
                row.child(
                    button(
                        "edit_" + ordinal++,
                        preset.builtin ? "gtit.preset.copy" : "gtit.preset.edit",
                        () -> navigate(preset.id, true)).pos(186, 51)
                            .size(66, 20));
                row.tooltipBuilder(t -> {
                    t.addLine(preset.name);
                    t.addLine(preset.description);
                    t.addLine("PRESET / " + preset.id);
                });
                list.child(row);
            }
            library.child(list);
            label(
                library,
                IKey.dynamic(
                    () -> IKey.lang("gtit.preset.ui.checked", selected.size())
                        .get()),
                10,
                0,
                260,
                16,
                .85f).bottom(9)
                    .color(NekoVendingTheme.MUTED);
            label(
                detail,
                IKey.str("定义预览 / SOURCE IDENTITY")
                    .style(EnumChatFormatting.BOLD),
                12,
                9,
                220,
                16,
                .9f).widthRelOffset(1f, -24);
            detail.child(
                new ListWidget<>().pos(10, 35)
                    .widthRelOffset(1f, -20)
                    .heightRelOffset(1f, -49)
                    .child(
                        new TextWidget<>(IKey.dynamic(this::previewText)).alignment(Alignment.TopLeft)
                            .color(NekoVendingTheme.INK)
                            .shadow(false)
                            .widthRelOffset(1f, -8)
                            .padding(2)
                            .scale(.95f)));
            label(
                impact,
                IKey.lang("gtit.preset.ui.applied")
                    .style(EnumChatFormatting.BOLD),
                10,
                9,
                168,
                16,
                1f);
            label(
                impact,
                IKey.lang(
                    "gtit.preset.ui.applied_count",
                    data.snapshot.appliedIds.size(),
                    data.snapshot.appliedTradeCount),
                10,
                31,
                168,
                26,
                .9f).color(UtilityUiStyle.MANAGER_SUCCESS);
            ListWidget<IWidget, ?> changes = new ListWidget<>().pos(10, 65)
                .width(168)
                .heightRelOffset(1f, -143);
            for (PresetSummary preset : data.snapshot.presets) changes.child(
                new NekoSingleLineTextWidget(
                    IKey.dynamic(
                        () -> (selected.contains(preset.id) ? "+ " : "− ") + preset.name
                            + (preset.defaultUpdateAvailable ? " · 更新" : ""))).alignment(Alignment.CenterLeft)
                                .color(
                                    () -> selected.contains(preset.id) ? UtilityUiStyle.MANAGER_SUCCESS
                                        : NekoVendingTheme.MUTED)
                                .shadow(false)
                                .size(160, 26)
                                .scale(.9f)
                                .setEnabledIf(w -> selected.contains(preset.id) || preset.selected));
            impact.child(changes);
            label(impact, IKey.lang("gtit.preset.ui.apply_hint"), 10, 0, 168, 26, .85f).bottom(49)
                .color(NekoVendingTheme.MUTED);
            impact.child(
                button("reload", "gtit.preset.reload", () -> finish(TradePresetManager.reload(player()), true)).left(10)
                    .bottom(12)
                    .size(76, 25)
                    .tooltipBuilder(t -> t.addLine(IKey.lang("gtit.preset.refresh_hint"))));
            impact.child(
                button(
                    "apply",
                    "gtit.preset.apply",
                    () -> finish(
                        TradePresetManager.select(player(), data.snapshot.revision, new ArrayList<>(selected)),
                        false)).selected(() -> true)
                            .right(10)
                            .bottom(12)
                            .size(84, 25));
            return page;
        }

        String presetName(String id) {
            for (PresetSummary preset : data.snapshot.presets) if (preset.id.equals(id)) return preset.name;
            return id;
        }

        IWidget editor() {
            ParentWidget<?> page = new ParentWidget<>().sizeRel(1f);
            page.child(
                NekoWorkbenchStyle.section(0, 0, 290, 228)
                    .widthRelOffset(1f, -166)
                    .heightRel(1f));
            page.child(
                NekoWorkbenchStyle.section(0, 0, 160, 198)
                    .right(0)
                    .heightRel(1f));
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
                1f).right(8);
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
                label(page, IKey.lang("gtit.preset.ui.select_tabs"), 304, 27, 144, 14, 0.9f).right(8)
                    .color(NekoVendingTheme.MUTED);
            } else {
                page.child(
                    new UtilityControls.Toggle().kraft()
                        .value(contentMode)
                        .right(136)
                        .top(26)
                        .size(16)
                        .overlay(false, IKey.str(""))
                        .overlay(true, IKey.str("x"))
                        .tooltipBuilder(t -> t.addLine(IKey.lang("gtit.preset.content_mode_hint"))));
                label(page, IKey.lang("gtit.preset.ui.replace"), 326, 27, 122, 14, 0.9f).right(8);
            }
            label(page, IKey.lang("gtit.preset.ui.keep_content"), 304, 53, 144, 44, 0.9f).right(8)
                .alignment(Alignment.TopLeft)
                .color(NekoVendingTheme.MUTED)
                .setEnabledIf(w -> !replaceTabs);
            ListWidget<IWidget, ?> choices = new ListWidget<>().right(8)
                .top(50)
                .width(144)
                .heightRelOffset(1f, -123)
                .setEnabledIf(w -> replaceTabs);
            for (TabSummary tab : data.snapshot.tabs) {
                BooleanSyncValue value = new BooleanSyncValue(() -> tabs.contains(tab.id), enabled -> dispatch(() -> {
                    if (enabled) tabs.add(tab.id);
                    else tabs.remove(tab.id);
                })).allowC2S();
                sync.syncValue("tab_" + tab.id, value);
                ParentWidget<?> row = new ParentWidget<>().size(136, 21)
                    .marginBottom(2)
                    .background(NekoVendingTheme.row(() -> tabs.contains(tab.id)));
                row.child(
                    new UtilityControls.Toggle().kraft()
                        .value(value)
                        .pos(4, 3)
                        .size(14)
                        .overlay(false, IKey.str(""))
                        .overlay(true, IKey.str("x")));
                label(row, IKey.str(tab.name), 24, 4, 108, 13, 0.9f)
                    .tooltipBuilder(t -> t.addLine(tab.name + " [" + tab.id + "]"));
                choices.child(row);
            }
            if (data.snapshot.tabs.isEmpty()) choices.child(
                new TextWidget<>(IKey.lang("gtit.preset.ui.no_tabs")).alignment(Alignment.TopLeft)
                    .color(NekoVendingTheme.MUTED)
                    .shadow(false)
                    .size(136, 36)
                    .scale(0.9f));
            page.child(choices);
            label(page, IKey.lang("gtit.preset.ui.icon"), 8, 126, 54, 14, 0.9f);
            label(page, IKey.lang("gtit.preset.ui.ghost"), 92, 126, 190, 14, 0.85f).color(NekoVendingTheme.MUTED);
            label(page, IKey.lang("gtit.preset.ui.inventory"), 8, 151, 54, 14, 0.9f).bottom(71);
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
                new NekoGhostSlot().syncHandler(ghost)
                    .background(NekoVendingTheme.SLOT)
                    .pos(66, 123));
            page.child(
                SlotGroupWidget.playerInventory(false)
                    .left(66)
                    .bottom(16));
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
                .right(8)
                .bottom(10)
                .size(144, 25)
                .tooltipBuilder(t -> t.addLine(IKey.lang("gtit.preset.save_hint"))));
            label(page, IKey.lang("gtit.preset.ui.save_hint"), 304, 181, 144, 24, 0.85f).right(8)
                .bottom(42)
                .color(NekoVendingTheme.MUTED);
            return page;
        }

        void field(ParentWidget<?> page, String key, String label, int y, java.util.function.Supplier<String> getter,
            java.util.function.Consumer<String> setter, int max) {
            StringSyncValue value = new StringSyncValue(
                getter,
                next -> { if (next != null && next.length() <= max) dispatch(() -> setter.accept(next)); }).allowC2S();
            sync.syncValue("draft_" + key, value);
            label(page, IKey.lang(label), 8, y + 3, 54, 14, 0.9f).color(NekoVendingTheme.MUTED);
            if (replace && "id".equals(key)) {
                label(page, IKey.str(data.editId), 66, y, 216, 18, 0.9f).background(NekoVendingTheme.INPUT)
                    .padding(4, 0)
                    .tooltipBuilder(t -> {
                        t.addLine(data.editId);
                        t.addLine(IKey.lang("gtit.preset.id"));
                    });
            } else {
                page.child(
                    new UtilityControls.Field().kraft()
                        .value(value)
                        .setMaxLength(max)
                        .autoUpdateOnChange(true)
                        .background(NekoVendingTheme.INPUT)
                        .pos(66, y)
                        .height(20)
                        .widthRelOffset(1f, -240)
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

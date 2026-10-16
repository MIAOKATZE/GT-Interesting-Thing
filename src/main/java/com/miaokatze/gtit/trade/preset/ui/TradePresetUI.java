package com.miaokatze.gtit.trade.preset.ui;

import java.nio.charset.StandardCharsets;
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
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.drawable.ItemDrawable;
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
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.ListWidget;
import com.cleanroommc.modularui.widgets.PageButton;
import com.cleanroommc.modularui.widgets.PagedWidget;
import com.cleanroommc.modularui.widgets.SlotGroupWidget;
import com.cleanroommc.modularui.widgets.ToggleButton;
import com.cleanroommc.modularui.widgets.slot.ModularSlot;
import com.cleanroommc.modularui.widgets.slot.PhantomItemSlot;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;
import com.google.gson.Gson;
import com.miaokatze.gtit.gift.ui.GiftGuiFactory;
import com.miaokatze.gtit.gui.pocket.PocketGuiTextures;
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
    private static final Gson JSON = new Gson();
    private static boolean registered;
    private static final int MAX_SNAPSHOT_BYTES = 1_048_576;

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
        init();
        GuiManager.open(FACTORY, new Data(player, TradePresetManager.snapshot(player), editId), player);
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
        byte[] bytes = JSON.toJson(data.snapshot)
            .getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_SNAPSHOT_BYTES) throw new IllegalArgumentException("Preset catalog is too large");
        buffer.writeInt(bytes.length);
        buffer.writeBytes(bytes);
        byte[] edit = data.editId.getBytes(StandardCharsets.UTF_8);
        buffer.writeByte(edit.length);
        buffer.writeBytes(edit);
    }

    @Override
    public Data readGuiData(EntityPlayer player, PacketBuffer buffer) {
        // This factory is server-open only. A forged MUI factory request cannot supply its own catalog.
        if (player instanceof EntityPlayerMP) throw new IllegalArgumentException("Use the authorized preset request");
        int length = buffer.readInt();
        if (length < 0 || length > MAX_SNAPSHOT_BYTES || length > buffer.readableBytes())
            throw new IllegalArgumentException("Invalid preset snapshot length");
        byte[] bytes = new byte[length];
        buffer.readBytes(bytes);
        Snapshot snapshot = JSON.fromJson(new String(bytes, StandardCharsets.UTF_8), Snapshot.class);
        int editLength = buffer.readUnsignedByte();
        if (editLength > 64 || editLength > buffer.readableBytes())
            throw new IllegalArgumentException("Invalid preset edit ID");
        byte[] edit = new byte[editLength];
        buffer.readBytes(edit);
        return new Data(player, snapshot, new String(edit, StandardCharsets.UTF_8));
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
        PagedWidget.Controller pages = new PagedWidget.Controller();
        ModularPanel panel = new ModularPanel("gtit.trade.presets").size(420, 330)
            .background(PocketGuiTextures.CLOTH, PocketGuiTextures.PANEL);
        panel.child(
            IKey.lang("gtit.preset.title")
                .asWidget()
                .pos(12, 10)
                .size(220, 16));
        panel.child(
            new PageButton(0, pages).pos(12, 31)
                .size(112, 18)
                .background(PocketGuiTextures.BUTTON)
                .overlay(IKey.lang("gtit.preset.choose")));
        panel.child(
            new PageButton(1, pages).pos(128, 31)
                .size(112, 18)
                .background(PocketGuiTextures.BUTTON)
                .overlay(IKey.lang("gtit.preset.create")));
        panel.child(
            session.button("gift", "gtit.preset.gifts", () -> GiftGuiFactory.open(session.player()))
                .pos(278, 10)
                .size(112, 18));
        panel.child(
            ButtonWidget.panelCloseButton()
                .right(5)
                .top(5));
        PagedWidget<?> body = new PagedWidget<>().controller(pages)
            .pos(12, 54)
            .size(396, 244)
            .addPage(session.chooser())
            .addPage(session.editor())
            .initialPage(data.editId.isEmpty() ? 0 : 1);
        panel.child(body);
        StringSyncValue status = new StringSyncValue(() -> session.status);
        sync.syncValue("result", status);
        panel.child(
            IKey.dynamic(status::getStringValue)
                .asWidget()
                .pos(12, 302)
                .size(396, 18)
                .scale(0.75f));
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
        volatile boolean replaceTabs;
        boolean replace;

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
            if (!data.snapshot.errors.isEmpty()) status = String.join("; ", data.snapshot.errors);
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

        ButtonWidget<?> button(String key, String label, Runnable action) {
            InteractionSyncHandler handler = new InteractionSyncHandler().setOnMousePressed(mouse -> {
                if (sync.isClient() || mouse.mouseButton != 0) return;
                dispatch(action);
            });
            sync.syncValue("action_" + key, handler);
            return new ButtonWidget<>().background(PocketGuiTextures.BUTTON)
                .overlay(
                    IKey.lang(label)
                        .scale(0.8f))
                .syncHandler(handler);
        }

        IWidget chooser() {
            ParentWidget<?> page = new ParentWidget<>().sizeRel(1f);
            ListWidget<IWidget, ?> list = new ListWidget<>().pos(0, 0)
                .size(396, 188);
            int ordinal = 0;
            for (PresetSummary preset : data.snapshot.presets) {
                BooleanSyncValue checked = new BooleanSyncValue(() -> selected.contains(preset.id), value -> {
                    dispatch(() -> {
                        if (value) selected.add(preset.id);
                        else selected.remove(preset.id);
                    });
                }).allowC2S();
                sync.syncValue("preset_" + preset.id, checked);
                ParentWidget<?> row = new ParentWidget<>().size(390, 58)
                    .marginBottom(4);
                row.child(
                    new ToggleButton().value(checked)
                        .pos(0, 0)
                        .size(330, 58)
                        .background(false, PocketGuiTextures.BUTTON)
                        .background(true, PocketGuiTextures.BUTTON_PRESSED)
                        .tooltipBuilder(t -> {
                            t.addLine(preset.name);
                            t.addLine(preset.description);
                            t.addLine(IKey.lang("gtit.preset.stable_id", preset.id));
                            t.addLine(
                                IKey.lang("gtit.preset.conflicts", String.join(", ", preset.mutuallyExclusiveIds)));
                            if (preset.empty) t.addLine(IKey.lang("gtit.preset.empty"));
                        }));
                ItemStack stack = preset.icon == null ? null : preset.icon.toStack();
                if (stack != null) row.child(
                    new ItemDrawable(stack).asWidget()
                        .pos(7, 8)
                        .size(20));
                row.child(
                    IKey.str(preset.name)
                        .asWidget()
                        .pos(34, 5)
                        .size(288, 13)
                        .scale(0.85f));
                row.child(
                    IKey.str(preset.description)
                        .asWidget()
                        .pos(34, 20)
                        .size(288, 16)
                        .scale(0.7f));
                row.child(
                    IKey.lang(
                        preset.empty ? "gtit.preset.empty" : "gtit.preset.conflicts",
                        String.join(", ", preset.mutuallyExclusiveIds))
                        .asWidget()
                        .pos(7, 39)
                        .size(315, 13)
                        .scale(0.7f));
                final String editId = preset.id;
                row.child(
                    button(
                        "edit_" + ordinal++,
                        preset.builtin ? "gtit.preset.copy" : "gtit.preset.edit",
                        () -> open(player(), editId)).pos(336, 0)
                            .size(52, 58));
                list.child(row);
            }
            page.child(list);
            page.child(
                IKey.lang("gtit.preset.world_shared")
                    .asWidget()
                    .pos(0, 191)
                    .size(396, 22)
                    .scale(0.75f));
            page.child(button("apply", "gtit.preset.apply", () -> {
                Result result = TradePresetManager.select(player(), data.snapshot.revision, new ArrayList<>(selected));
                finish(result, true);
            }).pos(0, 219)
                .size(190, 20));
            page.child(
                button("reload", "gtit.preset.reload", () -> finish(TradePresetManager.reload(player()), true))
                    .pos(202, 219)
                    .size(190, 20));
            return page;
        }

        IWidget editor() {
            ParentWidget<?> page = new ParentWidget<>().sizeRel(1f);
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
                        .asWidget()
                        .background(PocketGuiTextures.COIN_BAR)
                        .pos(202, 0)
                        .size(190, 22)
                        .scale(0.7f));
            } else {
                page.child(
                    new ToggleButton().value(contentMode)
                        .pos(202, 0)
                        .size(190, 22)
                        .background(false, PocketGuiTextures.BUTTON)
                        .background(true, PocketGuiTextures.BUTTON_PRESSED)
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
                    .pos(202, 27)
                    .size(190, 13)
                    .setEnabledIf(w -> replaceTabs));
            page.child(
                IKey.lang("gtit.preset.keep_content_hint")
                    .asWidget()
                    .pos(202, 32)
                    .size(190, 104)
                    .scale(0.75f)
                    .setEnabledIf(w -> !replaceTabs));
            ListWidget<IWidget, ?> choices = new ListWidget<>().pos(202, 42)
                .size(190, 100)
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
                    new ToggleButton().value(value)
                        .size(184, 22)
                        .marginBottom(3)
                        .background(false, PocketGuiTextures.BUTTON)
                        .background(true, PocketGuiTextures.BUTTON_PRESSED)
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
                    .background(PocketGuiTextures.SLOT)
                    .pos(174, 126));
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
            }).pos(202, 156)
                .size(190, 24));
            page.child(
                IKey.lang("gtit.preset.save_hint")
                    .asWidget()
                    .pos(202, 187)
                    .size(190, 49)
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
                    .asWidget()
                    .pos(0, y)
                    .size(194, 11)
                    .scale(0.7f));
            if (replace && "id".equals(key)) {
                page.child(
                    IKey.str(data.editId)
                        .asWidget()
                        .background(PocketGuiTextures.COIN_BAR)
                        .pos(0, y + 12)
                        .size(194, 16));
            } else {
                page.child(
                    new TextFieldWidget().value(value)
                        .setMaxLength(max)
                        .autoUpdateOnChange(true)
                        .background(PocketGuiTextures.COIN_BAR)
                        .pos(0, y + 12)
                        .size(194, 16));
            }
        }

        void finish(Result result, boolean reopen) {
            status = result.message;
            if (result.success && reopen) {
                player().addChatComponentMessage(new ChatComponentText(result.message));
                open(player());
            }
        }
    }
}

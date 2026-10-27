package com.miaokatze.gtit.client.nei;

import java.awt.Rectangle;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.common.MinecraftForge;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import com.cleanroommc.modularui.api.event.KeyboardInputEvent;
import com.cleanroommc.modularui.api.event.MouseInputEvent;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.api.widget.Interactable;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.ModularScreen;
import com.cleanroommc.modularui.screen.viewport.GuiViewportStack;
import com.cleanroommc.modularui.screen.viewport.LocatedWidget;
import com.cleanroommc.modularui.utils.HoveredWidgetList;
import com.cleanroommc.modularui.utils.ObjectList;
import com.miaokatze.gtit.gui.vm.edit.NekoGhostTarget;

import codechicken.nei.ItemPanel;
import codechicken.nei.ItemPanels;
import codechicken.nei.ItemsGrid;
import codechicken.nei.LayoutManager;
import codechicken.nei.NEIClientConfig;
import codechicken.nei.SearchField;
import codechicken.nei.VisiblityData;
import codechicken.nei.Widget;
import codechicken.nei.guihook.GuiContainerManager;
import codechicken.nei.guihook.IContainerDrawHandler;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * A scoped lease of NEI's actual item panel, search field and pagination, not an item-list replica.
 * Only the exact attached GUI can own the lease. The native layout mixin has no effect otherwise.
 * All public entry points run on the client thread. Never reference this class from mirrored server UI construction.
 */
@SideOnly(Side.CLIENT)
public final class NekoEmbeddedNeiSession {

    private static final Logger LOG = LogManager.getLogger("gtit");
    private static final List<WeakReference<NekoEmbeddedNeiSession>> SESSIONS = new ArrayList<>();
    private static NekoEmbeddedNeiSession owner;
    private static boolean installed;
    private static boolean restoring;

    private ModularScreen screen;
    private Supplier<Rectangle> boundsSupplier;
    private BooleanSupplier enabledSupplier;
    private boolean requested;
    private boolean disposed;
    private boolean failed;
    private Snapshot snapshot;
    private NekoEmbeddedNeiGeometry geometry;
    private String localSearch;
    private int localPage;
    private int capturedButton = -1;
    private ItemStack ownedGhost;
    private boolean searchFocused;
    private boolean nativeLayoutApplied;

    private NekoEmbeddedNeiSession(ModularScreen screen, Supplier<Rectangle> bounds, BooleanSupplier enabled) {
        this.screen = screen;
        this.boundsSupplier = bounds;
        this.enabledSupplier = enabled;
    }

    /** Idempotent per ModularScreen. The suppliers must not mutate the widget tree. */
    public static NekoEmbeddedNeiSession attach(ModularScreen screen, Supplier<Rectangle> bounds,
        BooleanSupplier enabled) {
        if (screen == null || bounds == null || enabled == null)
            throw new IllegalArgumentException("NEI host and bounds");
        for (Iterator<WeakReference<NekoEmbeddedNeiSession>> it = SESSIONS.iterator(); it.hasNext();) {
            NekoEmbeddedNeiSession session = it.next()
                .get();
            if (session == null || session.disposed) it.remove();
            else if (session.screen == screen) {
                session.boundsSupplier = bounds;
                session.enabledSupplier = enabled;
                return session;
            }
        }
        installBridge();
        NekoEmbeddedNeiSession session = new NekoEmbeddedNeiSession(screen, bounds, enabled);
        SESSIONS.add(new WeakReference<>(session));
        return session;
    }

    public void activate() {
        if (!disposed) requested = true;
    }

    public void deactivate() {
        requested = false;
        if (owner == this) release();
    }

    public void dispose() {
        if (disposed) return;
        deactivate();
        disposed = true;
        screen = null;
        boundsSupplier = null;
        enabledSupplier = null;
        geometry = null;
        localSearch = null;
        SESSIONS.removeIf(ref -> ref.get() == null || ref.get() == this);
    }

    public boolean isActive() {
        return owner == this && !disposed && !failed;
    }

    /** Capture native key/autofocus/filter changes before the normal sidebar geometry can clamp them. */
    public static void beforeNativeLayout(GuiContainer gui) {
        NekoEmbeddedNeiSession session = owner;
        if (restoring || session == null
            || !session.nativeLayoutApplied
            || session.host() != gui
            || Minecraft.getMinecraft().currentScreen != gui) return;
        try {
            session.searchFocused = LayoutManager.searchField.focused();
            session.rememberPage();
        } catch (RuntimeException | LinkageError failure) {
            session.fail(failure);
        }
    }

    /** Called only at LayoutManager.layout TAIL. The original NEI layout always runs first. */
    public static void afterNativeLayout(GuiContainer gui) {
        if (restoring || Minecraft.getMinecraft().currentScreen != gui) return;
        reconcile(gui);
        if (owner != null) {
            try {
                owner.layout(gui);
            } catch (RuntimeException | LinkageError failure) {
                owner.fail(failure);
            }
        }
    }

    private static void installBridge() {
        if (installed) return;
        installed = true;
        Bridge bridge = new Bridge();
        MinecraftForge.EVENT_BUS.register(bridge);
        FMLCommonHandler.instance()
            .bus()
            .register(bridge);
        GuiContainerManager.addDrawHandler(bridge);
    }

    private GuiContainer host() {
        if (screen == null || screen.getScreenWrapper() == null) return null;
        Object gui = screen.getScreenWrapper();
        return gui instanceof GuiContainer ? (GuiContainer) gui : null;
    }

    private boolean eligible(GuiContainer gui) {
        if (disposed || failed
            || !requested
            || gui == null
            || host() != gui
            || Minecraft.getMinecraft().currentScreen != gui
            || !enabledSupplier.getAsBoolean()
            || LayoutManager.searchField == null
            || LayoutManager.itemPanel != ItemPanels.itemPanel
            || ItemPanels.itemPanel.pagePrev == null
            || ItemPanels.itemPanel.pageNext == null
            || !NEIClientConfig.isEnabled()
            || NEIClientConfig.isHidden()) return false;
        // Modal panels keep their own input and must never be painted over by the catalog.
        if (screen.getPanelManager()
            .getTopMostPanel() != screen.getMainPanel() || screen.getContext()
                .hasDraggable())
            return false;
        NekoEmbeddedNeiGeometry next = NekoEmbeddedNeiGeometry
            .fit(boundsSupplier.get(), gui.width, gui.height, ItemsGrid.SLOT_SIZE);
        boolean moved = next != null && (geometry == null || !geometry.bounds.equals(next.bounds));
        geometry = next;
        // A resize/input event can precede the next rendered layout. Never query old native cells at new bounds.
        if (owner == this && nativeLayoutApplied && moved) layout(gui);
        return geometry != null;
    }

    private static void reconcile(GuiContainer gui) {
        if (restoring) return;
        if (owner != null) {
            try {
                if (owner.eligible(gui)) return;
                owner.release();
            } catch (RuntimeException | LinkageError failure) {
                if (owner != null) owner.fail(failure);
            }
        }
        for (Iterator<WeakReference<NekoEmbeddedNeiSession>> it = SESSIONS.iterator(); it.hasNext();) {
            NekoEmbeddedNeiSession session = it.next()
                .get();
            if (session == null || session.disposed) {
                it.remove();
                continue;
            }
            try {
                if (session.eligible(gui)) {
                    // Do not steal a ghost operation started by another GUI/mod.
                    if (ItemPanels.itemPanel.draggedStack != null || ItemPanels.bookmarkPanel.draggedStack != null
                        || ItemPanels.itemPanel.mouseDownSlot >= 0) return;
                    session.snapshot = new Snapshot();
                    owner = session;
                    session.nativeLayoutApplied = false;
                    LayoutManager.searchField.setFocus(false);
                    session.searchFocused = false;
                    if (session.localSearch != null && !session.localSearch.equals(LayoutManager.searchField.text())) {
                        LayoutManager.searchField.setText(session.localSearch);
                    }
                    ItemPanels.itemPanel.getGrid()
                        .setPage(session.localPage);
                    return;
                }
            } catch (RuntimeException | LinkageError failure) {
                session.fail(failure);
            }
        }
    }

    private void layout(GuiContainer gui) {
        ItemPanel panel = ItemPanels.itemPanel;
        place(panel, geometry.bounds);
        place(panel.pagePrev, geometry.previous);
        place(panel.pageNext, geometry.next);
        panel.pageLabel.x = geometry.bounds.x + geometry.bounds.width / 2;
        panel.pageLabel.y = geometry.bounds.y + 5;
        place(LayoutManager.searchField, geometry.search);
        Rectangle grid = geometry.grid;
        panel.getGrid()
            .setGridSize(grid.x, grid.y, grid.width, grid.height);
        panel.getGrid()
            .refresh(gui); // Keep every other mod's exclusion handler.
        // The normal sidebar layout runs first and can clamp its much smaller/fully masked grid to page zero.
        // Keep the editor page independently, then apply it only after the actual leased grid has been refreshed.
        panel.getGrid()
            .setPage(localPage);
        localPage = Math.max(0, panel.getPage() - 1);
        panel.pageLabel.text = panel.getLabelText();

        // Public API clears both native sets without touching private fields, focus or configuration.
        VisiblityData hidden = new VisiblityData();
        hidden.showNEI = false;
        LayoutManager.updateWidgetVisiblities(gui, hidden);
        LayoutManager.searchField.setVisible(true);
        // Normal NEI layout may hide the search field for a full-width MUI panel before this TAIL hook.
        LayoutManager.searchField.setFocus(searchFocused);
        LayoutManager.addWidget(panel);
        LayoutManager.addWidget(panel.pagePrev);
        LayoutManager.addWidget(panel.pageNext);
        LayoutManager.addWidget(panel.pageLabel);
        LayoutManager.addWidget(LayoutManager.searchField);
        nativeLayoutApplied = true;
    }

    private static void place(Widget widget, Rectangle rect) {
        widget.x = rect.x;
        widget.y = rect.y;
        widget.w = rect.width;
        widget.h = rect.height;
    }

    private void release() {
        Snapshot saved = snapshot;
        snapshot = null;
        owner = null; // Clear before rebuilding normal layout; never recursively reacquire.
        capturedButton = -1;
        nativeLayoutApplied = false;
        restoring = true;
        try {
            if (ownedGhost != null && ItemPanels.itemPanel.draggedStack == ownedGhost) {
                ItemPanels.itemPanel.draggedStack = null;
            }
            ItemPanels.itemPanel.mouseDownSlot = -1;
            ownedGhost = null;
            if (saved != null) {
                localPage = Math.max(0, ItemPanels.itemPanel.getPage() - 1);
                if (LayoutManager.searchField == saved.search) {
                    localSearch = saved.search.text();
                    saved.search.setFocus(false);
                    if (!saved.text.equals(saved.search.text())) saved.search.setText(saved.text);
                }
                saved.restoreGeometry(host());
                GuiScreen current = Minecraft.getMinecraft().currentScreen;
                if (current instanceof GuiContainer) LayoutManager.layout((GuiContainer) current);
                else {
                    VisiblityData hidden = new VisiblityData();
                    hidden.showNEI = false;
                    LayoutManager.updateWidgetVisiblities(host(), hidden);
                }
                ItemPanels.itemPanel.getGrid()
                    .setPage(saved.page);
                // Never leave a text field focused after the GUI itself has gone away.
                if (LayoutManager.searchField == saved.search && current == host()
                    && !requested
                    && screen.getMainPanel()
                        .isOpen()
                    && screen.getPanelManager()
                        .getTopMostPanel() == screen.getMainPanel()) {
                    saved.search.setFocus(saved.focused);
                }
            }
        } catch (RuntimeException | LinkageError failure) {
            LOG.error("[EmbeddedNEI] Could not restore native layout", failure);
            if (saved != null) {
                try {
                    saved.restoreGeometry(host());
                } catch (RuntimeException | LinkageError secondary) {
                    LOG.error("[EmbeddedNEI] Native geometry fallback also failed", secondary);
                }
            }
        } finally {
            restoring = false;
            ownedGhost = null;
        }
    }

    private void fail(Throwable failure) {
        failed = true;
        LOG.error("[EmbeddedNEI] Disabled this editor's NEI lease after an integration failure", failure);
        if (owner == this) release();
    }

    /** Item clicks use ONLY NEI's native stack preparation; never handleClickExt/mouseUp/cheatItem. */
    private boolean mouse(int x, int y, int button, boolean down, int wheel) {
        boolean inside = geometry.bounds.contains(x, y);
        if (wheel != 0) {
            if (!inside) return false;
            if (geometry.grid.contains(x, y) || y < geometry.grid.y) {
                ItemPanels.itemPanel.getGrid()
                    .shiftPage(wheel > 0 ? -1 : 1);
                rememberPage();
            }
            return true;
        }
        if (button < 0) return capturedButton >= 0 && inside;
        if (!down) {
            // A press that started in MUI must receive its release, even if the cursor crossed our rectangle.
            if (capturedButton != button) return false;
            capturedButton = -1;
            ItemPanels.itemPanel.mouseDownSlot = -1;
            if (!inside) {
                dropOnDraftSlot(x, y, button);
                clearOwnedGhost();
            }
            return true;
        }
        LayoutManager.searchField.onGuiClick(x, y);
        searchFocused = LayoutManager.searchField.focused();
        if (!inside) {
            if (ownedGhost == null || ItemPanels.itemPanel.draggedStack != ownedGhost) return false;
            // Owned catalog ghosts can reach only the explicitly allow-listed draft slots, never generic NEI drop/cheat
            // handlers.
            dropOnDraftSlot(x, y, button);
            clearOwnedGhost();
            return true;
        }
        capturedButton = button;
        screen.getContext()
            .removeFocus();
        if (geometry.search.contains(x, y)) {
            clearOwnedGhost();
            LayoutManager.searchField.handleClick(x, y, button);
            searchFocused = LayoutManager.searchField.focused();
            if (button == 1) {
                localPage = 0;
                ItemPanels.itemPanel.getGrid()
                    .setPage(0);
            }
        } else if (geometry.previous.contains(x, y)) {
            clearOwnedGhost();
            ItemPanels.itemPanel.pagePrev.handleClick(x, y, button);
            rememberPage();
        } else if (geometry.next.contains(x, y)) {
            clearOwnedGhost();
            ItemPanels.itemPanel.pageNext.handleClick(x, y, button);
            rememberPage();
        } else if (ItemPanels.itemPanel.getStackMouseOver(x, y) != null) {
            // A real cursor stack remains owned by vanilla/MUI; do not create a second ambiguous cursor.
            if (Minecraft.getMinecraft().thePlayer != null
                && Minecraft.getMinecraft().thePlayer.inventory.getItemStack() != null) return true;
            // A previous selection is replaced only if it is ours; no inventory item is ever touched.
            if (ItemPanels.itemPanel.draggedStack == ownedGhost) ItemPanels.itemPanel.draggedStack = null;
            // Calling handleClick(..., 2) is NOT safe: its handleClickExt first invokes arbitrary GUI drop handlers
            // for the player's real held stack. Borrow only the native stack/quantity preparation, then copy it.
            ItemStack selected = ItemPanels.itemPanel.getStackMouseOverWithQuantity(x, y);
            ownedGhost = selected == null ? null : selected.copy();
            ItemPanels.itemPanel.draggedStack = ownedGhost;
            ItemPanels.itemPanel.mouseDownSlot = -1;
        }
        return true;
    }

    private void rememberPage() {
        localPage = Math.max(0, ItemPanels.itemPanel.getPage() - 1);
    }

    private void dropOnDraftSlot(int mouseX, int mouseY, int button) {
        if (ownedGhost == null || ItemPanels.itemPanel.draggedStack != ownedGhost) return;
        // Re-run the exact public MUI hit-test path at this event's native coordinates. This respects
        // viewport clipping, scroll offsets, disabled pages and front-to-back sibling order, without
        // reading the previous frame's hover list (or the broken .92 getAllHovered iterator).
        ModularPanel panel = screen.getMainPanel();
        ObjectList<LocatedWidget> hit = ObjectList.create();
        HoveredWidgetList widgets = new HoveredWidgetList(hit);
        GuiViewportStack stack = new GuiViewportStack();
        try {
            stack.pushViewport(null, screen.getScreenArea());
            stack.pushViewport(panel, panel.getArea());
            panel.transform(stack);
            panel.getSelfAt(stack, widgets, mouseX, mouseY);
            panel.transformChildren(stack);
            panel.getWidgetsAt(stack, widgets, mouseX, mouseY);
            for (LocatedWidget located : hit) {
                IWidget target = located.getElement();
                if (!target.isValid() || !target.areAncestorsEnabled()) continue;
                if (target instanceof NekoGhostTarget) {
                    ((NekoGhostTarget) target).acceptNeiGhost(ownedGhost.copy(), button);
                    // Like MUI's own ghost route, rejection consumes this attempt; never try a covered slot.
                    clearOwnedGhost();
                    return;
                }
                if (target instanceof Interactable || !target.canClickThrough()) return;
            }
        } finally {
            stack.reset();
        }
    }

    private void clearOwnedGhost() {
        if (ownedGhost != null && ItemPanels.itemPanel.draggedStack == ownedGhost) {
            ItemPanels.itemPanel.draggedStack = null;
        }
        ownedGhost = null;
    }

    private static final class Snapshot {

        final SearchField search = LayoutManager.searchField;
        final String text = search.text();
        final boolean focused = search.focused();
        final int page = Math.max(0, ItemPanels.itemPanel.getPage() - 1);
        final List<WidgetState> widgets = new ArrayList<>();

        Snapshot() {
            widgets.add(new WidgetState(ItemPanels.itemPanel));
            widgets.add(new WidgetState(ItemPanels.itemPanel.pagePrev));
            widgets.add(new WidgetState(ItemPanels.itemPanel.pageNext));
            widgets.add(new WidgetState(ItemPanels.itemPanel.pageLabel));
            widgets.add(new WidgetState(search));
        }

        void restoreGeometry(GuiContainer gui) {
            for (WidgetState widget : widgets) widget.restore();
            if (gui != null) ItemPanels.itemPanel.resize(gui);
        }
    }

    private static final class WidgetState {

        final Widget widget;
        final Rectangle bounds;

        WidgetState(Widget widget) {
            this.widget = widget;
            bounds = new Rectangle(widget.x, widget.y, widget.w, widget.h);
        }

        void restore() {
            place(widget, bounds);
        }
    }

    /** Registered once; holds no screen/session references. All retained session references are weak. */
    public static final class Bridge implements IContainerDrawHandler {

        @SubscribeEvent(priority = EventPriority.HIGHEST)
        public void mouse(MouseInputEvent.Pre event) {
            GuiContainer gui = event.gui instanceof GuiContainer ? (GuiContainer) event.gui : null;
            reconcile(gui);
            NekoEmbeddedNeiSession session = owner;
            if (session == null) return;
            try {
                Minecraft mc = Minecraft.getMinecraft();
                int x = Mouse.getEventX() * event.gui.width / mc.displayWidth;
                int y = event.gui.height - Mouse.getEventY() * event.gui.height / mc.displayHeight - 1;
                if (session.mouse(x, y, Mouse.getEventButton(), Mouse.getEventButtonState(), Mouse.getEventDWheel())) {
                    event.setCanceled(true);
                }
            } catch (RuntimeException | LinkageError failure) {
                session.fail(failure);
                event.setCanceled(true); // Fail closed; never let a selected catalog item fall through to cheat
                                         // handling.
            }
        }

        @SubscribeEvent(priority = EventPriority.HIGHEST)
        public void key(KeyboardInputEvent.Pre event) {
            reconcile(event.gui instanceof GuiContainer ? (GuiContainer) event.gui : null);
            NekoEmbeddedNeiSession session = owner;
            if (session == null || !Keyboard.getEventKeyState()) return;
            try {
                // A native editor field owns its keystrokes even while the mouse rests over the catalog.
                // Clicking the NEI search explicitly removes MUI focus in mouse(), so its typing still works.
                if (session.screen.getContext()
                    .isFocused()) return;
                String oldSearch = LayoutManager.searchField.text();
                if (LayoutManager.searchField.focused()
                    && LayoutManager.searchField.handleKeyPress(Keyboard.getEventKey(), Keyboard.getEventCharacter())) {
                    session.searchFocused = LayoutManager.searchField.focused();
                    if (!oldSearch.equals(LayoutManager.searchField.text())) {
                        session.localPage = 0;
                        ItemPanels.itemPanel.getGrid()
                            .setPage(0);
                    }
                    event.setCanceled(true);
                } else if (!session.screen.getContext()
                    .isFocused() && NEIClientConfig.isKeyHashDown("gui.search")) {
                        LayoutManager.searchField.setFocus(true);
                        session.searchFocused = true;
                        event.setCanceled(true);
                    } else if (session.geometry.bounds.contains(
                        session.screen.getContext()
                            .getAbsMouseX(),
                        session.screen.getContext()
                            .getAbsMouseY())
                        && ItemPanels.itemPanel.handleKeyPress(Keyboard.getEventKey(), Keyboard.getEventCharacter())) {
                            session.rememberPage();
                            event.setCanceled(true);
                        }
            } catch (RuntimeException | LinkageError failure) {
                session.fail(failure);
                event.setCanceled(true);
            }
        }

        @SubscribeEvent(priority = EventPriority.LOWEST)
        public void opened(GuiOpenEvent event) {
            if (owner != null && event.gui != owner.host()) owner.release();
        }

        @SubscribeEvent
        public void tick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END) return;
            GuiScreen current = Minecraft.getMinecraft().currentScreen;
            reconcile(current instanceof GuiContainer ? (GuiContainer) current : null);
        }

        @Override
        public void postRenderObjects(GuiContainer gui, int mouseX, int mouseY) {
            NekoEmbeddedNeiSession session = owner;
            if (session == null || session.host() != gui
                || session.geometry == null
                || !session.geometry.bounds.contains(mouseX, mouseY)
                || !session.screen.getContext()
                    .isHovered())
                return;
            try {
                // MUI intentionally skips its native call when a MUI widget is hovered. Supply only that missing call.
                GuiContainerManager.getManager(gui)
                    .renderToolTips(mouseX, mouseY);
            } catch (RuntimeException | LinkageError failure) {
                session.fail(failure);
            }
        }

        @Override
        public void onPreDraw(GuiContainer gui) {}

        @Override
        public void renderObjects(GuiContainer gui, int mouseX, int mouseY) {}

        @Override
        public void renderSlotUnderlay(GuiContainer gui, Slot slot) {}

        @Override
        public void renderSlotOverlay(GuiContainer gui, Slot slot) {}
    }
}

package com.miaokatze.gtit.gui.vm;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import com.cleanroommc.modularui.api.widget.Interactable;
import com.cleanroommc.modularui.value.sync.BooleanSyncValue;
import com.miaokatze.gtit.client.gui.NekoTradeItemDisplay;
import com.miaokatze.gtit.client.gui.NekoTradeItemDisplayWidget;
import com.miaokatze.gtit.trade.NekoPageConfig;
import com.miaokatze.gtit.trade.NekoPageEntry;
import com.miaokatze.gtit.trade.NekoPageRegistry;
import com.miaokatze.gtit.trade.v2.NekoTrade;
import com.miaokatze.gtit.trade.v2.NekoTradeCategory;
import com.miaokatze.gtit.trade.v2.NekoTradeGroup;

/** Actual widget-pool rebuild and left-click routing, without a rendered screen or network connection. */
public final class TradeWidgetEditModeTest {

    private static final class FixtureGui extends NekoVMGuiV2 {

        private NekoTradeItemDisplay edited;

        private FixtureGui() {
            super(null);
        }

        @Override
        public void onEditRequested(NekoTradeItemDisplay display) {
            edited = display;
        }
    }

    public static void run() throws Exception {
        // Allocate only the GUI shell: constructing an unrelated GT machine requires the full mod lifecycle.
        Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
        Field unsafeField = unsafeClass.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        FixtureGui gui = (FixtureGui) unsafeClass.getMethod("allocateInstance", Class.class)
            .invoke(unsafeField.get(null), FixtureGui.class);
        set(gui, "displayedTradesTiles", new java.util.HashMap<>());
        set(gui, "displayedTradesList", new java.util.HashMap<>());
        BooleanSyncValue editMode = new BooleanSyncValue(() -> true);
        set(gui, "editModeSync", editMode);
        TradePage page = new TradePage(gui);
        set(gui, "tradePage", page);
        Field scope = TradePage.class.getDeclaredField("tradeScope");
        scope.setAccessible(true);
        Method reload = TradePage.class.getDeclaredMethod("loadTradeCategories");
        reload.setAccessible(true);
        Method allocate = NekoVMGuiV2.class.getDeclaredMethod("initPreAllocatedWidgets");
        allocate.setAccessible(true);
        Method propagate = NekoVMGuiV2.class.getDeclaredMethod("propagateEditModeToWidgets", boolean.class);
        propagate.setAccessible(true);

        NekoPageEntry defaults = new NekoPageEntry(-400001, "default", "minecraft:stone", 0, false);
        defaults.setPresetBuiltin(true);
        NekoPageEntry custom = new NekoPageEntry(7, "custom", "minecraft:stone", 0, false);
        NekoPageEntry added = new NekoPageEntry(8, "added", "minecraft:stone", 0, false);
        for (int phase = 0; phase < 5; phase++) {
            // Initial default pool, custom scope switch, page addition, page deletion, edit mode off.
            NekoPageConfig.NekoPageData pages = new NekoPageConfig.NekoPageData();
            pages.setPages(
                phase == 2 ? java.util.Arrays.asList(defaults, custom, added)
                    : java.util.Arrays.asList(defaults, custom));
            NekoPageRegistry.applySyncedPages(pages);
            scope.setInt(page, phase == 0 ? TradePage.SCOPE_DEFAULT : TradePage.SCOPE_CUSTOM);
            reload.invoke(page);
            if (phase == 4) editMode.setBoolValue(false, false, false);
            gui.displayedTradesTiles.clear();
            gui.displayedTradesList.clear();
            allocate.invoke(gui); // Shared allocator used by initial build and rebuildPreAllocatedWidgets.
            if (phase == 0) propagate.invoke(gui, true); // Initial S2C update reaches the original default pool.
            for (Map<NekoTradeCategory, List<NekoTradeItemDisplayWidget>> pool : java.util.Arrays
                .asList(gui.displayedTradesTiles, gui.displayedTradesList)) {
                check(pool.size() == (phase == 2 ? 3 : 2), "pool follows selected scope and page additions/deletions");
                for (List<NekoTradeItemDisplayWidget> widgets : pool.values()) {
                    for (NekoTradeItemDisplayWidget widget : widgets) {
                        check(
                            widget.isEditMode() == (phase != 4),
                            "rebuilt tile/list widgets inherit current edit mode");
                    }
                }
                if (phase != 4) {
                    NekoTradeGroup group = new NekoTradeGroup();
                    group.setTabId(phase == 0 ? -400001 : 7);
                    NekoTradeItemDisplay display = NekoTradeItemDisplay.fromNekoTrade(new NekoTrade(), group, 0);
                    NekoTradeItemDisplayWidget widget = pool.get(NekoTradeCategory.ofTabId(group.getTabId()))
                        .get(0);
                    widget.setDisplay(display);
                    gui.edited = null;
                    check(
                        widget.onMousePressed(0) == Interactable.Result.SUCCESS && gui.edited == display,
                        "existing trade left click routes its original identity to the edit callback");
                }
            }
        }
        System.out.println("TradeWidgetEditModeTest: 5 rebuild phases, tile/list state and edit clicks passed");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    private static void set(FixtureGui gui, String name, Object value) throws Exception {
        Field field = NekoVMGuiV2.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(gui, value);
    }
}

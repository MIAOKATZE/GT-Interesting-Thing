package com.miaokatze.gtit.client.nei;

import java.awt.Rectangle;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Set;

import net.minecraft.client.gui.inventory.GuiContainer;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import com.cleanroommc.modularui.api.event.KeyboardInputEvent;
import com.cleanroommc.modularui.api.event.MouseInputEvent;

import codechicken.nei.ItemPanel;
import codechicken.nei.ItemPanels;
import codechicken.nei.ItemsGrid;
import codechicken.nei.ItemsPanelGrid;
import codechicken.nei.Label;
import codechicken.nei.LayoutManager;
import codechicken.nei.PanelWidget;
import codechicken.nei.SearchField;
import codechicken.nei.TextField;
import codechicken.nei.VisiblityData;
import codechicken.nei.Widget;

/** Actual pinned NEI/MUI ABI, native grid geometry and adapter no-spawn bytecode probe. No graphical smoke claim. */
public final class NekoEmbeddedNeiProbeTest {

    public static void main(String[] args) throws Exception {
        geometry();
        nativeAbi(args.length == 0 ? "2.8.98" : args[0]);
        nativeWidgetRegistry();
        nativeGrid();
        noSpawnBytecode();
        focusedEditorOwnsKeys();
        System.out.println(
            "NekoEmbeddedNeiProbeTest: geometry, actual native ABI/grid and no-spawn calls passed; GL/input smoke not exercised");
    }

    private static void geometry() {
        NekoEmbeddedNeiGeometry layout = NekoEmbeddedNeiGeometry.fit(new Rectangle(400, 200, 290, 160), 800, 450, 18);
        check(layout != null && layout.bounds.equals(new Rectangle(400, 200, 290, 160)), "absolute region unchanged");
        check(layout.grid.y == 218 && layout.grid.height == 120, "header and search reserve native space");
        check(layout.search.equals(new Rectangle(400, 340, 290, 20)), "native search footer");
        check(!layout.previous.intersects(layout.next), "paging buttons never overlap");
        check(layout.bounds.contains(layout.grid) && layout.bounds.contains(layout.search), "children inside lease");
        check(!layout.grid.intersects(layout.search), "search never overlaps item grid");
        check(NekoEmbeddedNeiGeometry.fit(null, 800, 450, 18) == null, "missing anchor");
        check(NekoEmbeddedNeiGeometry.fit(new Rectangle(0, 0, 39, 80), 800, 450, 18) == null, "tiny width rejected");
        check(NekoEmbeddedNeiGeometry.fit(new Rectangle(0, 0, 80, 57), 800, 450, 18) == null, "tiny height rejected");
        check(NekoEmbeddedNeiGeometry.fit(new Rectangle(900, 0, 80, 80), 800, 450, 18) == null, "offscreen rejected");
        check(
            NekoEmbeddedNeiGeometry.fit(new Rectangle(0, 0, 80, 80), 0, 450, 18) == null,
            "uninitialized screen rejected");
        layout = NekoEmbeddedNeiGeometry.fit(new Rectangle(-10, -10, 150, 150), 100, 100, 18);
        check(layout != null && layout.bounds.equals(new Rectangle(0, 0, 100, 100)), "resize clips to logical screen");
        Rectangle source = new Rectangle(0, 0, 100, 100);
        layout = NekoEmbeddedNeiGeometry.fit(source, 100, 100, 18);
        source.x = 99;
        check(layout.bounds.x == 0, "supplier rectangle not retained or mutated");
    }

    private static void nativeAbi(String expectedVersion) throws Exception {
        check(
            Modifier.isFinal(
                ItemPanels.class.getField("itemPanel")
                    .getModifiers()),
            "actual singleton is final");
        check(
            Modifier.isStatic(
                LayoutManager.class.getMethod("layout", GuiContainer.class)
                    .getModifiers()),
            "mixin target is the native static layout method");
        LayoutManager.class.getMethod("updateWidgetVisiblities", GuiContainer.class, VisiblityData.class);
        LayoutManager.class.getMethod("addWidget", Widget.class);
        check(!Modifier.isStatic(LayoutManager.class.getMethod("mouseClicked", GuiContainer.class,
            int.class, int.class, int.class).getModifiers()), "disabled options click injection uses instance ABI");
        PanelWidget.class.getMethod("getStackMouseOverWithQuantity", int.class, int.class);
        ItemsGrid.class.getMethod("setGridSize", int.class, int.class, int.class, int.class);
        ItemsGrid.class.getMethod("refresh", GuiContainer.class);
        SearchField.class.getMethod("onTextChange", String.class);
        TextField.class.getMethod("focused");
        NekoEmbeddedNeiSession.Bridge.class.getMethod("mouse", MouseInputEvent.Pre.class);
        NekoEmbeddedNeiSession.Bridge.class.getMethod("key", KeyboardInputEvent.Pre.class);
        String origin = ItemPanel.class.getProtectionDomain()
            .getCodeSource()
            .getLocation()
            .toString();
        check(
            origin.contains("NotEnoughItems") && origin.contains(expectedVersion),
            "real requested NEI jar, not a fake class: " + origin);
        System.out.println("NEI probe artifact: " + origin);
    }

    private static void nativeWidgetRegistry() throws Exception {
        Field drawing = LayoutManager.class.getDeclaredField("drawWidgets");
        Field controls = LayoutManager.class.getDeclaredField("controlWidgets");
        drawing.setAccessible(true);
        controls.setAccessible(true);
        Object oldDrawing = drawing.get(null), oldControls = controls.get(null);
        try {
            VisiblityData hidden = new VisiblityData();
            hidden.showNEI = false;
            LayoutManager.updateWidgetVisiblities(null, hidden);
            Label label = new Label("native registry probe", true);
            LayoutManager.addWidget(label);
            // NEI WidgetZOrder intentionally never returns zero, so TreeSet.contains is not an identity check.
            check(
                ((Set<?>) drawing.get(null)).stream()
                    .anyMatch(widget -> widget == label)
                    && ((Set<?>) controls.get(null)).stream()
                        .anyMatch(widget -> widget == label),
                "native addWidget updates draw and input registries");
            LayoutManager.updateWidgetVisiblities(null, hidden);
            check(
                ((Set<?>) drawing.get(null)).isEmpty() && ((Set<?>) controls.get(null)).isEmpty(),
                "native hidden-visibility path clears both registries before any GUI/search access");
        } finally {
            drawing.set(null, oldDrawing);
            controls.set(null, oldControls);
        }
    }

    private static void nativeGrid() {
        // NEI 2.8.155 consults the renderer-cache option in refresh(). Select native uncached mode without GL/config
        // bootstrap; all tested geometry, paging and hit-test implementations remain those in the actual NEI jar.
        ItemsPanelGrid grid = new ItemsPanelGrid() {

            @Override
            protected int getGridRenderingCacheMode() {
                return 0;
            }
        };
        grid.setGridSize(410, 220, 290, 120);
        grid.refresh(null);
        check(
            grid.getColumns() == 16 && grid.getRows() == 6 && grid.getPerPage() == 96,
            "actual NEI native grid sizing");
        check(grid.getSlotRect(0, 0).x == 411 && grid.getSlotRect(0, 0).y == 220, "native horizontal centering");
        check(grid.contains(411, 220) && !grid.contains(410, 219), "native mouse bounds use absolute pixels");
        grid.setGridSize(30, 40, 90, 54);
        grid.refresh(null);
        check(
            grid.getColumns() == 5 && grid.getRows() == 3 && grid.getPerPage() == 15,
            "same grid resized without replacement");
        check(!grid.contains(411, 220) && grid.contains(30, 40), "old hit region removed after resize");
    }

    private static void noSpawnBytecode() throws Exception {
        final boolean[] nativeSelection = { false };
        final boolean[] nativeHitTest = { false };
        final boolean[] safeTarget = { false };
        try (InputStream input = NekoEmbeddedNeiSession.class.getResourceAsStream("NekoEmbeddedNeiSession.class")) {
            check(input != null, "adapter bytecode present");
            new ClassReader(input).accept(new ClassVisitor(Opcodes.ASM5) {

                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor, String signature,
                    String[] exceptions) {
                    return new MethodVisitor(Opcodes.ASM5) {

                        @Override
                        public void visitMethodInsn(int opcode, String owner, String method, String desc, boolean itf) {
                            check(
                                !method.equals("cheatItem") && !method.equals("windowClick")
                                    && !owner.contains("NEIClientPacketHandler"),
                                "no give/container packet path");
                            if (owner.equals("com/cleanroommc/modularui/screen/ModularPanel")
                                && method.equals("getWidgetsAt")) nativeHitTest[0] = true;
                            if (owner.equals("com/miaokatze/gtit/gui/vm/edit/NekoGhostTarget")
                                && method.equals("acceptNeiGhost")) safeTarget[0] = true;
                            if (owner.equals("codechicken/nei/ItemPanel")
                                || owner.equals("codechicken/nei/PanelWidget")) {
                                check(
                                    !method.equals("handleClick") && !method.equals("handleClickExt")
                                        && !method.equals("mouseUp"),
                                    "no native item cheat-click path");
                                if (method.equals("getStackMouseOverWithQuantity")) nativeSelection[0] = true;
                            }
                        }
                    };
                }
            }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        }
        check(nativeSelection[0], "selection comes from actual NEI panel quantity preparation");
        check(nativeHitTest[0], "drops re-run actual MUI viewport-aware event-time hit testing");
        check(safeTarget[0], "only explicit safe draft targets receive copied catalog items");
    }

    private static void focusedEditorOwnsKeys() throws Exception {
        final boolean[] focusedBeforeNativeKey = { false }, sawNativeKey = { false };
        try (InputStream stream = NekoEmbeddedNeiSession.Bridge.class
            .getResourceAsStream("NekoEmbeddedNeiSession$Bridge.class")) {
            check(stream != null, "bridge bytecode available");
            new ClassReader(stream).accept(new ClassVisitor(Opcodes.ASM5) {

                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor, String signature,
                    String[] exceptions) {
                    if (!name.equals("key")) return null;
                    return new MethodVisitor(Opcodes.ASM5) {

                        @Override
                        public void visitMethodInsn(int opcode, String owner, String name, String descriptor,
                            boolean isInterface) {
                            if (name.equals("isFocused") && !sawNativeKey[0]) focusedBeforeNativeKey[0] = true;
                            if (owner.startsWith("codechicken/nei/") && name.equals("handleKeyPress")) {
                                sawNativeKey[0] = true;
                                check(
                                    focusedBeforeNativeKey[0],
                                    "native text field focus precedes every NEI key route");
                            }
                        }
                    };
                }
            }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        }
        check(sawNativeKey[0], "native NEI keyboard routes remain present");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}

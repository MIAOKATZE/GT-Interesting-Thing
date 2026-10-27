package com.miaokatze.gtit.client.gui;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Function;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.layout.IViewportStack;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.ModularScreen;
import com.cleanroommc.modularui.screen.viewport.LocatedWidget;
import com.cleanroommc.modularui.screen.viewport.TransformationMatrix;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widget.Widget;
import com.miaokatze.gtit.client.nei.NekoEmbeddedNeiSession;

import cpw.mods.fml.common.Loader;

/** One responsive canvas. Render, native slot hit-testing and NEI bounds share the MUI viewport transform. */
public class NekoWorkbenchPanel extends ModularPanel {

    private static final class CatalogAnchor extends Widget<CatalogAnchor> {

        @Override
        public boolean canHover() {
            return false;
        }
    }

    private final NekoWorkbenchLayout layout = new NekoWorkbenchLayout();
    private final List<Runnable> bindings = new ArrayList<>();
    private BooleanSupplier catalogEnabled = () -> false;
    private NekoEmbeddedNeiSession nei;
    private Widget<?> catalogAnchor;

    public NekoWorkbenchPanel(String name) {
        super(name);
        pos(0, 0).size(layout.width, layout.height)
            .background(NekoVendingTheme.PAPER);
    }

    public NekoWorkbenchLayout layout() {
        return layout;
    }

    public <T extends IWidget> T place(T widget, Function<NekoWorkbenchLayout, Rectangle> bounds) {
        Runnable apply = () -> {
            Rectangle r = bounds.apply(layout);
            widget.resizer()
                .resetPosition();
            widget.resizer()
                .pos(r.x, r.y)
                .size(r.width, r.height);
        };
        bindings.add(apply);
        apply.run();
        return widget;
    }

    public void catalog(BooleanSupplier enabled) {
        catalogEnabled = enabled;
        // Deliberately not an NEI exclusion area: this is a visual paper frame around the real catalog.
        child(
            place(
                new ParentWidget<>().background(NekoVendingTheme.PANEL)
                    .setEnabledIf(w -> enabled.getAsBoolean()),
                l -> l.rect(l.catalogX(), 80, l.catalogWidth(), l.height - 116)));
        catalogAnchor = new CatalogAnchor();
        catalogAnchor.setEnabledIf(w -> enabled.getAsBoolean());
        child(place(catalogAnchor, NekoWorkbenchLayout::catalog));
        child(
            place(
                IKey.str("NEI · 物品目录 / 仅编辑草稿")
                    .color(NekoVendingTheme.MUTED)
                    .asWidget()
                    .setEnabledIf(w -> enabled.getAsBoolean()),
                l -> l.rect(l.catalogX() + 8, 84, l.catalogWidth() - 16, 14)));
        child(
            place(
                IKey.str("NEI 目录不可用（未启用或隐藏）\n可从背包复制物品到幽灵槽")
                    .color(NekoVendingTheme.MUTED)
                    .asWidget()
                    .setEnabledIf(w -> enabled.getAsBoolean() && (nei == null || !nei.isActive())),
                NekoWorkbenchLayout::catalog));
    }

    @Override
    public void beforeResize(boolean onOpen) {
        super.beforeResize(onOpen);
        if (getScreen() == null) return;
        if (layout.resize(getScreen().getScreenArea().width, getScreen().getScreenArea().height) || onOpen) {
            pos(0, 0).size(layout.width, layout.height);
            for (Runnable binding : bindings) binding.run();
        }
    }

    @Override
    public void transform(IViewportStack stack) {
        stack.scale(layout.scale, layout.scale);
    }

    @Override
    public boolean isExcludeAreaInRecipeViewer() {
        return false;
    }

    @Override
    public void onOpen(ModularScreen screen) {
        super.onOpen(screen);
        if (catalogAnchor != null && Loader.isModLoaded("NotEnoughItems")) {
            nei = NekoEmbeddedNeiSession.attach(screen, this::catalogBounds, catalogEnabled);
            nei.activate();
        }
    }

    private Rectangle catalogBounds() {
        if (catalogAnchor == null || !catalogAnchor.isValid()) return new Rectangle();
        TransformationMatrix matrix = LocatedWidget.of(catalogAnchor)
            .getTransformationMatrix();
        int x = matrix.transformX(0, 0), y = matrix.transformY(0, 0);
        int x1 = matrix.transformX(catalogAnchor.getArea().width, 0);
        int y1 = matrix.transformY(0, catalogAnchor.getArea().height);
        return new Rectangle(Math.min(x, x1), Math.min(y, y1), Math.abs(x1 - x), Math.abs(y1 - y));
    }

    @Override
    public void onClose() {
        if (nei != null) nei.deactivate();
        super.onClose();
    }

    @Override
    public void dispose() {
        if (nei != null) nei.dispose();
        nei = null;
        super.dispose();
    }
}

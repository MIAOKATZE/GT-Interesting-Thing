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
import com.cleanroommc.modularui.value.IntValue;
import com.cleanroommc.modularui.value.StringValue;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widget.Widget;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;
import com.miaokatze.gtit.client.nei.NekoEmbeddedNeiSession;

import cpw.mods.fml.common.Loader;

/** One responsive canvas. Render, native slot hit-testing and NEI bounds share the MUI viewport transform. */
public class NekoWorkbenchPanel extends ModularPanel {

    private static final class CatalogAnchor extends ParentWidget<CatalogAnchor> {

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
    private TextFieldWidget catalogSearchInput;
    private final IntValue catalogQuantity = new IntValue(64);

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
        CatalogAnchor catalog = new CatalogAnchor().name("nekoNativeNeiCatalog");
        catalogAnchor = catalog;
        catalog.setEnabledIf(w -> enabled.getAsBoolean());
        catalogSearchInput = new TextFieldWidget().name("nekoCatalogSearch")
            .value(
                new StringValue.Dynamic(
                    () -> nei == null ? "" : nei.getSearch(),
                    text -> { if (nei != null) nei.setSearch(text); }))
            .autoUpdateOnChange(true)
            .setMaxLength(256)
            .hintText("搜索物品 / @模组")
            .setTextColor(NekoVendingTheme.INK)
            .background(NekoVendingTheme.INPUT)
            .left(0)
            .bottom(0)
            .height(20)
            .widthRelOffset(1f, -76)
            .setEnabledIf(w -> nei != null && nei.isActive())
            .tooltipBuilder(
                t -> NekoVendingTheme.tooltip(t)
                    .addLine("NEI 原生搜索；右键清空；支持 Ctrl+A/C/V、中文输入")
                    .addLine("其他搜索前缀遵循当前 NEI 设置")
                    .addLine("左键选取或按住拖到编辑槽；右键选取 1 个；Esc 取消选取"));
        catalog.child(catalogSearchInput);
        catalog.child(
            new TextFieldWidget().name("nekoCatalogQuantity")
                .value(catalogQuantity)
                .setNumbers(1, Integer.MAX_VALUE)
                .setTextColor(NekoVendingTheme.INK)
                .background(NekoVendingTheme.INPUT)
                .right(0)
                .bottom(0)
                .height(20)
                .width(72)
                .setEnabledIf(w -> nei != null && nei.isActive())
                .tooltipBuilder(
                    t -> NekoVendingTheme.tooltip(t)
                        .addLine("选取数量：1～2147483647（离开输入框后生效）")
                        .addLine("交易槽使用此数量；抽奖幻影槽保留原堆叠上限和同物品增减规则")
                        .addLine("选取后可连续配置多个槽；点击空白或按 Esc 取消")));
        child(place(catalog, NekoWorkbenchLayout::catalog));
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
            nei = NekoEmbeddedNeiSession
                .attach(screen, this::catalogBounds, catalogEnabled, catalogSearchInput, catalogQuantity::getIntValue);
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

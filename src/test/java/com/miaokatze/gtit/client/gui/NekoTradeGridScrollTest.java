// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtit.client.gui;

import java.lang.reflect.Field;

import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.widget.AbstractWidget;
import com.cleanroommc.modularui.widget.Widget;
import com.cleanroommc.modularui.widget.WidgetTree;
import com.cleanroommc.modularui.widget.scroll.VerticalScrollData;
import com.cleanroommc.modularui.widget.sizer.Area;
import com.cleanroommc.modularui.widget.sizer.ResizeNode;
import com.cleanroommc.modularui.widget.sizer.StaticResizer;
import com.cleanroommc.modularui.widgets.ListWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;

/** Native MUI2 sizing/scroll activation, without a client screen, font, GL or world. */
public final class NekoTradeGridScrollTest {

    private static final class HeadlessList extends ListWidget<IWidget, HeadlessList> {

        @Override
        public void beforeResize(boolean onOpen) {
            // Theme lookup requires a real screen; geometry uses an explicit scrollbar thickness below.
        }
    }

    private static final class Fixture {

        final HeadlessList list = new HeadlessList();
        final Flow grid;

        Fixture(boolean fixed, int initialCount) throws Exception {
            grid = fixed ? new NekoTradeGrid()
                : Flow.row()
                    .wrap()
                    .coverChildrenHeight()
                    .collapseDisabledChild(true)
                    .crossAxisChildPadding(0);
            grid.widthRelOffset(1f, -4)
                .marginLeft(2);
            list.size(320, 200);
            list.scrollDirection(new VerticalScrollData(false, 4));
            for (int i = 0; i < 30; i++) {
                Widget<?> card = new Widget<>();
                card.widthRelOffset(.5f, -4)
                    .height(NekoTradeCardLayout.TILE_HEIGHT)
                    .margin(2);
                card.setEnabled(i < initialCount);
                grid.child(card);
            }
            list.child(grid);
            Widget<?> screen = new Widget<>();
            screen.getArea().width = 768;
            screen.getArea().height = 432;
            StaticResizer root = new StaticResizer() {

                @Override
                public Area getArea() {
                    return screen.getArea();
                }

                @Override
                public String getDebugDisplayName() {
                    return "trade-scroll-test";
                }

                @Override
                public String toString() {
                    return getDebugDisplayName();
                }
            };
            wire(list, screen, root, root);
            WidgetTree.resizeInternal(list.resizer(), false);
            check(
                list.resizer()
                    .isFullyCalculated(false),
                "initial native layout completes");
        }

        void show(int count) {
            for (int i = 0; i < grid.getChildren()
                .size(); i++)
                grid.getChildren()
                    .get(i)
                    .setEnabled(i < count);
            // MUI2 drawTree performs this resize only for a dirty subtree.
            if (grid.requiresResize()) WidgetTree.resizeInternal(grid.resizer(), false);
        }

        boolean scrollable() {
            return list.getScrollData()
                .isScrollBarActive(list.getScrollArea());
        }

        void dispose() throws Exception {
            // The fixture has no GUI-action registry to detach. Native disposal still tears down
            // every child and resizer; only screen listener cleanup is skipped.
            invalidate(list);
            list.dispose();
        }
    }

    /** Attach only native parent/resize links; unrelated screen and synchronisation services stay absent. */
    private static void wire(IWidget widget, IWidget parent, ResizeNode resizeParent, ResizeNode root)
        throws Exception {
        Field parentField = AbstractWidget.class.getDeclaredField("parent");
        parentField.setAccessible(true);
        parentField.set(widget, parent);
        Field validField = AbstractWidget.class.getDeclaredField("valid");
        validField.setAccessible(true);
        validField.setBoolean(widget, true);
        widget.resizer()
            .initialize(resizeParent, root);
        for (IWidget child : widget.getChildren()) wire(child, widget, widget.resizer(), root);
    }

    private static void invalidate(IWidget widget) throws Exception {
        Field validField = AbstractWidget.class.getDeclaredField("valid");
        validField.setAccessible(true);
        validField.setBoolean(widget, false);
        for (IWidget child : widget.getChildren()) invalidate(child);
    }

    public static void main(String[] args) throws Exception {
        Fixture original = new Fixture(false, 2);
        original.show(30);
        check(original.grid.getArea().height == 50, "original Flow retains the short measured height");
        check(
            original.list.getScrollData()
                .getScrollSize() == 50 && !original.scrollable(),
            "original scrollbar stays hidden");
        check(
            original.grid.getChildren()
                .get(29)
                .getArea().ry > 200,
            "original cards already extend outside the viewport");
        System.out.println("Original Flow reproduced: last card below viewport, height/range=50, scrollbar inactive");
        original.dispose();

        for (int opening = 0; opening < 3; opening++) {
            Fixture fixed = new Fixture(true, opening == 1 ? 30 : 2);
            fixed.show(30);
            check(fixed.grid.getArea().height == 750, "full grid measured on opening " + opening);
            check(
                fixed.list.getScrollData()
                    .getScrollSize() == 750 && fixed.scrollable(),
                "overflow activates scrollbar");
            fixed.list.getScrollData()
                .scrollTo(fixed.list.getScrollArea(), 9999);
            check(fixed.list.getScrollY() == 550, "native scroll reaches the last row");
            fixed.show(0);
            check(!fixed.scrollable(), "inactive structure/empty trades removes overflow");
            fixed.show(30);
            check(
                fixed.scrollable() && fixed.list.getScrollData()
                    .getScrollSize() == 750,
                "reactivation restores scroll range");
            fixed.dispose();
        }
        System.out.println(
            "NekoTradeGridScrollTest: late data, cached reopen, dispose/rebuild and structure visibility passed");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}

// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtit.client.gui;

import com.cleanroommc.modularui.api.GuiAxis;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;

/** Wrapping trade cards whose measured height also defines the parent list's scroll range. */
public final class NekoTradeGrid extends Flow {

    public NekoTradeGrid() {
        super(GuiAxis.X);
        wrap().coverChildrenHeight()
            .collapseDisabledChild(true)
            .crossAxisChildPadding(0);
    }

    @Override
    public void onChildChangeEnabled(IWidget child, boolean enabled) {
        // MUI2's immediate Flow layout only moves cards. Re-measure coverChildrenHeight and
        // the enclosing ListWidget together when synced trades/structure state change.
        scheduleResize();
    }
}

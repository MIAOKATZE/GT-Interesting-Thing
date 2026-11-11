// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtit.gift.ui;

import com.miaokatze.gtit.client.gui.NekoWorkbenchPanel;

/** All normal close routes, including Escape, pass through the same draft guard. */
final class GiftLibraryPanel extends NekoWorkbenchPanel {

    private Runnable requestClose = () -> {};

    GiftLibraryPanel() {
        super("gift_library");
    }

    void requestClose(Runnable requestClose) {
        this.requestClose = requestClose;
    }

    @Override
    public void closeIfOpen() {
        if (isOpen()) requestClose.run();
    }

    void closeApproved() {
        super.closeIfOpen();
    }
}

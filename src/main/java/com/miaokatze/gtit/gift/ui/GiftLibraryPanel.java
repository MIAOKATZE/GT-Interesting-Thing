package com.miaokatze.gtit.gift.ui;

import com.cleanroommc.modularui.screen.ModularPanel;

/** All normal close routes, including Escape, pass through the same draft guard. */
final class GiftLibraryPanel extends ModularPanel {

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

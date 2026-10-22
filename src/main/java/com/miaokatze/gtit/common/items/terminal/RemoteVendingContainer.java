package com.miaokatze.gtit.common.items.terminal;

import com.cleanroommc.modularui.screen.ModularContainer;

/** Standard MUI container with an explicit per-opening remote authority. */
public final class RemoteVendingContainer extends ModularContainer {

    private final RemoteVendingSession session;

    public RemoteVendingContainer(RemoteVendingSession session) {
        this.session = session;
    }

    public RemoteVendingSession getRemoteSession() {
        return session;
    }
}

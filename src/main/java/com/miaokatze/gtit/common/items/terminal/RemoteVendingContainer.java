// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

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

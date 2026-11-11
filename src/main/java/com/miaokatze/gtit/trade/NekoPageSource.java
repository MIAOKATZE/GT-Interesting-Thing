// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtit.trade;

/** Page navigation identity, independent of preset ownership and per-trade default overrides. */
public enum NekoPageSource {

    DEFAULT,
    PRESET,
    CUSTOM;

    public boolean canCreateTrade() {
        return this != PRESET;
    }
}

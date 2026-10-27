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

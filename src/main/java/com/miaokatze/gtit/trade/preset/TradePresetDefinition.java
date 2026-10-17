package com.miaokatze.gtit.trade.preset;

import java.util.ArrayList;
import java.util.List;

import com.miaokatze.gtit.trade.NekoPageEntry;
import com.miaokatze.gtit.trade.NekoTradeEntry;

/** Portable preset. Page ids are local to this file, never server filesystem paths. */
public final class TradePresetDefinition {

    public int schemaVersion = 1;
    public String id = "";
    public String name = "";
    public String description = "";
    public TradePresetManager.Icon icon = new TradePresetManager.Icon();
    public List<String> mutuallyExclusiveIds = new ArrayList<>();
    public List<Tab> tabs = new ArrayList<>();

    public static final class Tab {

        public NekoPageEntry page;
        public List<NekoTradeEntry> trades = new ArrayList<>();
    }
}

package com.miaokatze.gtit.client.nei;

import java.util.ArrayList;
import java.util.List;

/** Fixed-size pages preserve every exchange and every slot without drawing outside the NEI canvas. */
public final class NekoTradePages {

    public static final int SLOTS_PER_SIDE = 12;

    private NekoTradePages() {}

    public static final class Page {

        public final int tradeIndex;
        public final int slotOffset;

        Page(int tradeIndex, int slotOffset) {
            this.tradeIndex = tradeIndex;
            this.slotOffset = slotOffset;
        }
    }

    public static List<Page> create(int[] inputs, int[] outputs) {
        if (inputs.length != outputs.length) throw new IllegalArgumentException("Mismatched exchange counts");
        List<Page> pages = new ArrayList<>();
        for (int trade = 0; trade < inputs.length; trade++) {
            int slots = Math.max(inputs[trade], outputs[trade]);
            for (int offset = 0; offset < Math.max(1, slots); offset += SLOTS_PER_SIDE) {
                pages.add(new Page(trade, offset));
            }
        }
        return pages;
    }
}

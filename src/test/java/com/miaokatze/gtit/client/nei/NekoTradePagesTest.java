package com.miaokatze.gtit.client.nei;

import java.util.List;

/** Pure-Java coverage for exchange/slot pagination, including empty sides and oversized exchanges. */
public final class NekoTradePagesTest {

    public static void main(String[] args) {
        int[] inputs = { 0, 12, 13, 25, 1 };
        int[] outputs = { 0, 1, 0, 40, 13 };
        List<NekoTradePages.Page> pages = NekoTradePages.create(inputs, outputs);
        if (pages.size() != 10) throw new AssertionError("Page count: " + pages.size());
        for (int trade = 0; trade < inputs.length; trade++) {
            int expectedOffset = 0;
            for (NekoTradePages.Page page : pages) if (page.tradeIndex == trade) {
                if (page.slotOffset != expectedOffset) throw new AssertionError("Skipped/duplicated slots");
                expectedOffset += NekoTradePages.SLOTS_PER_SIDE;
            }
            if (expectedOffset < Math.max(1, Math.max(inputs[trade], outputs[trade]))) {
                throw new AssertionError("Unreachable exchange slots");
            }
        }
        if (!NekoTradePages.create(new int[0], new int[0])
            .isEmpty()) throw new AssertionError("Empty group");
        System.out.println("NekoTradePagesTest passed");
    }
}

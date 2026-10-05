package com.miaokatze.gtit.lottery;

import com.google.gson.JsonObject;

/** 编辑器与服务端共享的槽数规则，保留旧池的少量槽位。 */
public final class LotteryPoolSlots {

    public static final int DEFAULT_COUNT = 10;
    public static final int MIN_NEW_COUNT = 4;

    private LotteryPoolSlots() {}

    public static int requestedCount(JsonObject json, int existingCount, boolean creating) {
        if (!json.has("entryCount")) return creating ? DEFAULT_COUNT : existingCount;
        if (!json.get("entryCount")
            .isJsonPrimitive()
            || !json.getAsJsonPrimitive("entryCount")
                .isNumber()) {
            throw new IllegalArgumentException("奖池槽数必须为整数");
        }
        final int count;
        try {
            count = json.get("entryCount")
                .getAsBigDecimal()
                .intValueExact();
        } catch (ArithmeticException | NumberFormatException e) {
            throw new IllegalArgumentException("奖池槽数必须为整数");
        }
        int min = creating ? MIN_NEW_COUNT : Math.min(MIN_NEW_COUNT, Math.max(1, existingCount));
        if (count < min || count > LotteryPool.MAX_ENTRIES) {
            throw new IllegalArgumentException("奖池槽数范围为 " + min + ".." + LotteryPool.MAX_ENTRIES);
        }
        return count;
    }

    public static void resize(LotteryPool pool, int count) {
        if (count < 1 || count > LotteryPool.MAX_ENTRIES) throw new IllegalArgumentException("奖池槽数非法");
        java.util.List<LotteryEntry> entries = pool.getEntries();
        for (int i = count; i < entries.size(); i++) {
            LotteryEntry entry = entries.get(i);
            if (entry != null && !entry.isEmptyPlaceholder()) {
                throw new IllegalArgumentException("请先清空尾部奖品并将权重设为 0，再缩减槽数");
            }
        }
        while (entries.size() > count) entries.remove(entries.size() - 1);
        int suffix = 1;
        while (entries.size() < count) {
            while (pool.getEntryById("entry_" + suffix) != null) suffix++;
            LotteryEntry entry = new LotteryEntry();
            entry.setId("entry_" + suffix++);
            entry.setWeight(0);
            entries.add(entry);
        }
    }
}

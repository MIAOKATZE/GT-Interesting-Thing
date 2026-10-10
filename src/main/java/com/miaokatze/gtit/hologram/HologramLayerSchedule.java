package com.miaokatze.gtit.hologram;

import java.util.ArrayList;
import java.util.List;

/** Pure world-height schedule; CPU-limited layers may extend but never merge. */
final class HologramLayerSchedule {

    static final int LAYER_TICKS = 20;
    static final int GAP_TICKS = 10;

    private HologramLayerSchedule() {}

    static List<List<Integer>> layers(List<Integer> indices, int[] heights, boolean descending) {
        List<Integer> sorted = new ArrayList<>(indices);
        sorted.sort((a, b) -> {
            int order = Integer.compare(heights[a], heights[b]);
            return order == 0 ? Integer.compare(a, b) : descending ? -order : order;
        });
        List<List<Integer>> result = new ArrayList<>();
        Integer height = null;
        for (int index : sorted) {
            if (height == null || height != heights[index]) {
                result.add(new ArrayList<>());
                height = heights[index];
            }
            result.get(result.size() - 1)
                .add(index);
        }
        return result;
    }

    static long resumeDue(long due, long pausedAt, long resumedAt) {
        return due + Math.max(0, resumedAt - pausedAt);
    }

    static long nextLayerDue(long finalTick) {
        return finalTick + 1 + GAP_TICKS;
    }

    static int quota(int size, int elapsed) {
        if (elapsed < 0) return 0;
        return (int) Math.min(size, ((long) Math.min(LAYER_TICKS, elapsed + 1) * size + LAYER_TICKS - 1) / LAYER_TICKS);
    }
}

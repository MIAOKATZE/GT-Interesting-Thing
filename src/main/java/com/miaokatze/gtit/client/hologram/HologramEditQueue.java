package com.miaokatze.gtit.client.hologram;

import java.util.ArrayDeque;

/** Bounded client intent queue; resolves positions again after each server acknowledgment. */
final class HologramEditQueue {

    static final int LIMIT = 8;
    private final ArrayDeque<Entry> entries = new ArrayDeque<>();

    boolean offer(String action, String session, HologramState.Cell cell) {
        if (entries.size() >= LIMIT) return false;
        entries.addLast(new Entry(action, session, cell));
        return true;
    }

    Entry removeFirst(HologramState snapshot) {
        Entry entry = entries.removeFirst();
        if (entry.selectionIn(snapshot) == -2) {
            clear();
            return null;
        }
        return entry;
    }

    boolean isEmpty() {
        return entries.isEmpty();
    }

    int size() {
        return entries.size();
    }

    void clear() {
        entries.clear();
    }

    static final class Entry {

        final String action, session;
        final boolean hasCell;
        final int x, y, z;

        Entry(String action, String session, HologramState.Cell cell) {
            this.action = action;
            this.session = session;
            hasCell = cell != null;
            x = cell == null ? 0 : cell.x;
            y = cell == null ? 0 : cell.y;
            z = cell == null ? 0 : cell.z;
        }

        int selectionIn(HologramState snapshot) {
            if (!session.equals(snapshot.session)) return -2;
            if (!hasCell) return -1;
            for (HologramState.Cell cell : snapshot.cells)
                if (cell.x == x && cell.y == y && cell.z == z) return cell.index;
            return -2;
        }
    }
}

package com.miaokatze.gtit.hologram;

/** Stable face cycle: north, east, south, west, up, down; skip faces the target disallows. */
final class HologramCellRotation {

    private static final int[] FACES = { 2, 5, 3, 4, 1, 0 };

    private HologramCellRotation() {}

    static int nextFacing(int current, int[] legal) {
        int count = 0;
        for (int side : FACES) for (int allowed : legal) if (side == allowed) {
            count++;
            break;
        }
        if (count < 2) return -1;
        int start = -1;
        for (int i = 0; i < FACES.length; i++) if (FACES[i] == current) start = i;
        for (int offset = 1; offset <= FACES.length; offset++) {
            int candidate = FACES[(start + offset) % FACES.length];
            for (int allowed : legal) if (candidate == allowed) return candidate;
        }
        return -1;
    }
}

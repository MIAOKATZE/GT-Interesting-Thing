package com.miaokatze.gtit.testlab;

/** Shared overflow-safe canvas boundary used both for fixtures and recovery journals. */
final class TestLabBounds {

    private TestLabBounds() {}

    static boolean contains(int x, int y, int z) {
        return y >= 1 && y <= 255
            && Math.abs((long) z) <= 256
            && (Math.abs((long) x) <= 256 || Math.abs((long) x - 1048576) <= 256);
    }

    static int height(int offset) {
        long height = 64L + offset;
        if (height < 1 || height > 255) throw new IllegalArgumentException("Fixture height outside 1..255");
        return (int) height;
    }
}

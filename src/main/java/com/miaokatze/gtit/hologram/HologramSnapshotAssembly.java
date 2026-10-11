package com.miaokatze.gtit.hologram;

/** One bounded, ordered transport snapshot. Allocation follows received bytes, never declared cell counts. */
public final class HologramSnapshotAssembly {

    public static final int CHUNK_BYTES = 512 * 1024;
    public static final int MAX_BYTES = 128 * 1024 * 1024;
    private static final long TIMEOUT_NS = 30_000_000_000L;
    private long newest = -1;
    private long started;
    private int total, next, received;
    private byte[][] chunks;

    public byte[] accept(long sequence, int size, int index, byte[] bytes, long now) {
        if (sequence < 0 || size <= 0
            || size > MAX_BYTES
            || index < 0
            || bytes == null
            || bytes.length <= 0
            || bytes.length > CHUNK_BYTES) return null;
        int count = (size - 1) / CHUNK_BYTES + 1;
        int expected = index == count - 1 ? size - index * CHUNK_BYTES : CHUNK_BYTES;
        if (index >= count || bytes.length != expected || sequence < newest) return null;
        if (sequence > newest) {
            if (index != 0) return null;
            clear();
            newest = sequence;
            started = now;
            total = size;
            chunks = new byte[count][];
        }
        if (chunks == null || now - started > TIMEOUT_NS || size != total || index != next) {
            clear();
            return null;
        }
        chunks[next++] = bytes;
        received += bytes.length;
        if (next != chunks.length) return null;
        if (received != total) {
            clear();
            return null;
        }
        byte[] complete = new byte[total];
        int offset = 0;
        for (byte[] chunk : chunks) {
            System.arraycopy(chunk, 0, complete, offset, chunk.length);
            offset += chunk.length;
        }
        clear();
        return complete;
    }

    public void expire(long now) {
        if (chunks != null && now - started > TIMEOUT_NS) clear();
    }

    public void clear() {
        chunks = null;
        total = next = received = 0;
    }
}

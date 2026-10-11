package com.miaokatze.gtit.hologram;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Random;
import java.util.zip.GZIPOutputStream;

import net.minecraft.nbt.NBTTagCompound;

import com.miaokatze.gtit.testutil.SimpleAssert;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

/** Large NBT and actual raw message boundaries without a live channel or client world. */
public final class HologramSnapshotCodecRegression {

    private HologramSnapshotCodecRegression() {}

    public static void run() {
        try {
            byte[] payload = new byte[3 * 1024 * 1024];
            new Random(80).nextBytes(payload);
            NBTTagCompound source = new NBTTagCompound();
            source.setString("session", "mega");
            source.setString("generation", "mega");
            source.setLong("batchSeq", 37);
            source.setByteArray("payload", payload);
            byte[] compressed = HologramSnapshotCodec.compress(source);
            SimpleAssert.that(compressed.length > 2 * 1024 * 1024, "fixture exceeds Forge single-packet limit");
            HologramSnapshotAssembly assembly = new HologramSnapshotAssembly();
            byte[] joined = null;
            for (int offset = 0, index = 0; offset
                < compressed.length; offset += HologramSnapshotAssembly.CHUNK_BYTES, index++) {
                byte[] slice = Arrays.copyOfRange(
                    compressed,
                    offset,
                    Math.min(compressed.length, offset + HologramSnapshotAssembly.CHUNK_BYTES));
                ByteBuf buffer = Unpooled.buffer();
                try {
                    new HologramNetwork.Fragment(80, compressed.length, index, slice).toBytes(buffer);
                    SimpleAssert.that(buffer.readableBytes() < 2 * 1024 * 1024, "every actual encoded fragment fits");
                    HologramNetwork.Fragment decoded = new HologramNetwork.Fragment();
                    decoded.fromBytes(buffer);
                    buffer.clear();
                    decoded.toBytes(buffer);
                    long wireSequence = buffer.readLong();
                    int wireTotal = buffer.readInt(), wireIndex = buffer.readInt(), wireLength = buffer.readInt();
                    byte[] wireBytes = new byte[wireLength];
                    buffer.readBytes(wireBytes);
                    joined = assembly.accept(wireSequence, wireTotal, wireIndex, wireBytes, index);
                    if (offset + slice.length < compressed.length)
                        SimpleAssert.that(joined == null, "partial transport does not publish");
                } finally {
                    buffer.release();
                }
            }
            SimpleAssert.that(joined != null, "all fragments complete the snapshot");
            NBTTagCompound decoded = HologramSnapshotCodec.decode(joined);
            SimpleAssert.that(Arrays.equals(payload, decoded.getByteArray("payload")), "large decoded NBT is complete");
            SimpleAssert.eq("mega", decoded.getString("generation"), "generation retained");
            SimpleAssert.eq(37, (int) decoded.getLong("batchSeq"), "ACK batch retained");
            hostileArray();
            badFragment();
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    private static void hostileArray() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(new GZIPOutputStream(bytes))) {
            output.writeByte(10);
            output.writeUTF("");
            output.writeByte(7);
            output.writeUTF("hostile");
            output.writeInt(536870912); // 8 * length wraps to zero in vanilla size accounting.
        }
        boolean rejected = false;
        try {
            HologramSnapshotCodec.decode(bytes.toByteArray());
        } catch (IOException expected) {
            rejected = true;
        }
        SimpleAssert.that(rejected, "overflowing vanilla array length is rejected before allocation");
    }

    private static void badFragment() {
        ByteBuf buffer = Unpooled.buffer();
        try {
            buffer.writeLong(81);
            buffer.writeInt(HologramSnapshotAssembly.MAX_BYTES + 1);
            buffer.writeInt(0);
            buffer.writeInt(1);
            buffer.writeByte(0);
            boolean rejected = false;
            try {
                new HologramNetwork.Fragment().fromBytes(buffer);
            } catch (IllegalArgumentException expected) {
                rejected = true;
            }
            SimpleAssert.that(rejected, "declared transport budget rejects hostile packet");
        } finally {
            buffer.release();
        }
    }
}

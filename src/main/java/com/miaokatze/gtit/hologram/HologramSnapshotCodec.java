package com.miaokatze.gtit.hologram;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTSizeTracker;
import net.minecraft.nbt.NBTTagCompound;

/** Byte budgets are independent of structure dimensions and cell counts. */
public final class HologramSnapshotCodec {

    private static final int RAW_BYTES = 128 * 1024 * 1024;

    private HologramSnapshotCodec() {}

    public static byte[] compress(NBTTagCompound tag) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(
            new LimitedOutput(
                new BufferedOutputStream(
                    new GZIPOutputStream(new LimitedOutput(bytes, HologramSnapshotAssembly.MAX_BYTES))),
                RAW_BYTES))) {
            CompressedStreamTools.write(tag, output);
        }
        return bytes.toByteArray();
    }

    public static NBTTagCompound decode(byte[] bytes) throws IOException {
        return decode(bytes, RAW_BYTES, 512L * 1024 * 1024);
    }

    static NBTTagCompound decode(byte[] bytes, int rawLimit, long nbtLimit) throws IOException {
        // Vanilla 1.7 array-size accounting multiplies ints before casting to long. Validate lengths first.
        try (DataInputStream input = new DataInputStream(
            new BufferedInputStream(new GZIPInputStream(new ByteArrayInputStream(bytes))))) {
            Validator validator = new Validator(input, rawLimit);
            if (validator.byteValue() != 10) throw new IOException("snapshot root is not a compound");
            validator.string();
            validator.payload(10, 0);
            if (input.read() != -1) throw new IOException("snapshot trailing bytes");
        }
        return CompressedStreamTools.func_152457_a(bytes, new NBTSizeTracker(nbtLimit));
    }

    private static final class LimitedOutput extends FilterOutputStream {

        private final int limit;
        private int written;

        LimitedOutput(OutputStream output, int limit) {
            super(output);
            this.limit = limit;
        }

        public void write(int value) throws IOException {
            reserve(1);
            out.write(value);
        }

        public void write(byte[] bytes, int offset, int length) throws IOException {
            reserve(length);
            out.write(bytes, offset, length);
        }

        private void reserve(int length) throws IOException {
            if (length < 0 || length > limit - written) throw new IOException("snapshot byte budget");
            written += length;
        }
    }

    private static final class Validator {

        private final DataInputStream input;
        private final int limit;
        private int consumed;

        Validator(DataInputStream input, int limit) {
            this.input = input;
            this.limit = limit;
        }

        int byteValue() throws IOException {
            reserve(1);
            return input.readUnsignedByte();
        }

        int length() throws IOException {
            reserve(4);
            int length = input.readInt();
            if (length < 0 || length > limit) throw new IOException("snapshot NBT length");
            return length;
        }

        void string() throws IOException {
            reserve(2);
            skip(input.readUnsignedShort());
        }

        void skip(long bytes) throws IOException {
            if (bytes < 0 || bytes > limit - consumed) throw new IOException("snapshot expanded byte budget");
            reserve((int) bytes);
            int left = (int) bytes;
            while (left > 0) {
                int skipped = input.skipBytes(left);
                if (skipped == 0) {
                    input.readByte();
                    skipped = 1;
                }
                left -= skipped;
            }
        }

        void reserve(int bytes) throws IOException {
            if (bytes > limit - consumed) throw new IOException("snapshot expanded byte budget");
            consumed += bytes;
        }

        void payload(int type, int depth) throws IOException {
            if (depth > 512) throw new IOException("snapshot NBT depth");
            switch (type) {
                case 1:
                    skip(1);
                    break;
                case 2:
                    skip(2);
                    break;
                case 3:
                case 5:
                    skip(4);
                    break;
                case 4:
                case 6:
                    skip(8);
                    break;
                case 7:
                    skip(length());
                    break;
                case 8:
                    string();
                    break;
                case 9:
                    int child = byteValue(), count = length();
                    if ((child <= 0 && count != 0) || child > 11) throw new IOException("snapshot list type");
                    for (int i = 0; i < count; i++) payload(child, depth + 1);
                    break;
                case 10:
                    int member;
                    while ((member = byteValue()) != 0) {
                        string();
                        payload(member, depth + 1);
                    }
                    break;
                case 11:
                    skip(4L * length());
                    break;
                default:
                    throw new IOException("snapshot NBT type");
            }
        }
    }
}

package com.miaokatze.gtit.gift;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Base64;

import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTSizeTracker;
import net.minecraft.nbt.NBTTagCompound;

/** Rejects forged lengths before vanilla 1.7 NBT array allocation, including int-overflow lengths. */
public final class GiftNbt {

    public static final int MAX_BYTES = 24576;

    private GiftNbt() {}

    /** Gift-only strict codec: a failed serialization must never turn an item into an untagged one. */
    public static String encode(NBTTagCompound tag) {
        if (tag == null) return null;
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream() {

                @Override
                public synchronized void write(int value) {
                    requireCapacity(1);
                    super.write(value);
                }

                @Override
                public synchronized void write(byte[] value, int offset, int length) {
                    requireCapacity(length);
                    super.write(value, offset, length);
                }

                private void requireCapacity(int length) {
                    if (length > MAX_BYTES - count) throw new IllegalArgumentException("Item NBT exceeds 24 KiB");
                }
            };
            CompressedStreamTools.write(tag, new DataOutputStream(bytes));
            String encoded = Base64.getEncoder()
                .encodeToString(bytes.toByteArray());
            decode(encoded); // Apply the same depth, node and allocation limits in both directions.
            return encoded;
        } catch (IOException | StackOverflowError e) {
            throw new IllegalArgumentException("Invalid or oversized item NBT", e);
        }
    }

    public static NBTTagCompound decode(String base64) {
        try {
            if (base64.length() > 32768) throw new IOException("NBT too large");
            byte[] bytes = Base64.getDecoder()
                .decode(base64);
            if (bytes.length > MAX_BYTES) throw new IOException("NBT too large");
            DataInputStream input = new DataInputStream(new ByteArrayInputStream(bytes));
            if (input.readUnsignedByte() != 10) throw new IOException("NBT root must be a compound");
            input.readUTF();
            scan(input, 10, 0, new int[] { 0 });
            if (input.available() != 0) throw new IOException("Trailing NBT bytes");
            return CompressedStreamTools
                .func_152456_a(new DataInputStream(new ByteArrayInputStream(bytes)), new NBTSizeTracker(256 * 1024));
        } catch (IOException | RuntimeException e) {
            throw new IllegalArgumentException("Invalid or oversized item NBT", e);
        }
    }

    private static void scan(DataInputStream input, int type, int depth, int[] nodes) throws IOException {
        if (depth > 32 || ++nodes[0] > 8192) throw new IOException("NBT nesting or node limit exceeded");
        switch (type) {
            case 1:
                skip(input, 1);
                break;
            case 2:
                skip(input, 2);
                break;
            case 3:
            case 5:
                skip(input, 4);
                break;
            case 4:
            case 6:
                skip(input, 8);
                break;
            case 7:
                skip(input, boundedLength(input, 1));
                break;
            case 8:
                input.readUTF();
                break;
            case 9:
                int child = input.readUnsignedByte();
                int count = input.readInt();
                if (count < 0 || count > 8192 || child > 11 || (child == 0 && count != 0)) {
                    throw new IOException("Invalid NBT list");
                }
                for (int i = 0; i < count; i++) scan(input, child, depth + 1, nodes);
                break;
            case 10:
                for (int childType; (childType = input.readUnsignedByte()) != 0;) {
                    input.readUTF();
                    scan(input, childType, depth + 1, nodes);
                }
                break;
            case 11:
                skip(input, boundedLength(input, 4) * 4);
                break;
            default:
                throw new IOException("Invalid NBT type");
        }
    }

    private static int boundedLength(DataInputStream input, int elementBytes) throws IOException {
        int length = input.readInt();
        if (length < 0 || length > input.available() / elementBytes) throw new IOException("Invalid NBT array length");
        return length;
    }

    private static void skip(DataInputStream input, int count) throws IOException {
        if (count < 0 || count > input.available() || input.skipBytes(count) != count)
            throw new IOException("Truncated NBT");
    }
}

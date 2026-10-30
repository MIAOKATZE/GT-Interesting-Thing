package com.miaokatze.gtit.util;

import cpw.mods.fml.common.network.ByteBufUtils;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

/** Wire compatibility against Forge UTF-8 plus pre-allocation/Unicode business bounds. */
public final class PacketStringsTest {

    public static void main(String[] args) {
        for (String value : new String[] { "", "ascii", "猫猫终端", "\ud83d\ude3a" }) {
            ByteBuf old = Unpooled.buffer(), next = Unpooled.buffer();
            try {
                ByteBufUtils.writeUTF8String(old, value);
                check(
                    PacketStrings.read(old, 16)
                        .equals(value),
                    "legacy -> bounded reader");
                PacketStrings.write(next, value, 16);
                check(
                    ByteBufUtils.readUTF8String(next)
                        .equals(value),
                    "bounded writer -> legacy");
            } finally {
                old.release();
                next.release();
            }
        }
        ByteBuf over = Unpooled.buffer();
        try {
            ByteBufUtils.writeVarInt(over, 1000, 2);
            over.writeZero(1000);
            reject(() -> PacketStrings.read(over, 16), "business byte limit");
            check(over.readerIndex() == 2, "rejected before decoding/consuming payload");
        } finally {
            over.release();
        }
        ByteBuf chars = Unpooled.buffer();
        try {
            ByteBufUtils.writeUTF8String(chars, repeat("x", 17));
            reject(() -> PacketStrings.read(chars, 16), "character limit");
        } finally {
            chars.release();
        }
        ByteBuf shortField = Unpooled.buffer();
        try {
            ByteBufUtils.writeVarInt(shortField, 20, 2);
            shortField.writeByte(1);
            reject(() -> PacketStrings.read(shortField, 16), "truncated field");
        } finally {
            shortField.release();
        }
        ByteBuf malformed = Unpooled.wrappedBuffer(new byte[] { 2, (byte) 0xc3, 0x28 });
        try {
            reject(() -> PacketStrings.read(malformed, 16), "invalid UTF-8");
        } finally {
            malformed.release();
        }
        ByteBuf varint = Unpooled.wrappedBuffer(new byte[] { (byte) 0x80, (byte) 0x80, 0 });
        try {
            reject(() -> PacketStrings.read(varint, 16), "oversized VarInt");
        } finally {
            varint.release();
        }
        String chinese = repeat("猫", 6000);
        check(
            chinese.length() < 16384 && !PacketStrings.fits(chinese, 16384),
            "wire byte bound differs from characters");
        check(PacketStrings.fits(repeat("x", 16383), 16384), "maximum wire value accepted");
        check(!PacketStrings.fits(repeat("x", 16384), 16384), "one beyond wire rejected");
        System.out
            .println("PacketStringsTest: 17 wire, malformed input, allocation admission and Unicode checks passed");
    }

    private static String repeat(String s, int count) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < count; i++) b.append(s);
        return b.toString();
    }

    private static void reject(Runnable action, String message) {
        try {
            action.run();
        } catch (RuntimeException expected) {
            return;
        }
        throw new AssertionError(message);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}

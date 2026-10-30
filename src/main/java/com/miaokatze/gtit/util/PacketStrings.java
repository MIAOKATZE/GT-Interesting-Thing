package com.miaokatze.gtit.util;

import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

import cpw.mods.fml.common.network.ByteBufUtils;
import io.netty.buffer.ByteBuf;

/** Business bounds checked before decoding; wire format remains Forge's two-byte VarInt + UTF-8. */
public final class PacketStrings {

    public static final int MAX_UTF8_BYTES = 16383;

    private PacketStrings() {}

    public static boolean fits(String value, int maxCharacters) {
        return value != null && value.length() <= maxCharacters
            && value.getBytes(StandardCharsets.UTF_8).length <= MAX_UTF8_BYTES;
    }

    public static String read(ByteBuf buf, int maxCharacters) {
        int length = ByteBufUtils.readVarInt(buf, 2);
        if (length < 0 || length > Math.min(MAX_UTF8_BYTES, (long) maxCharacters * 3) || length > buf.readableBytes())
            throw new IllegalArgumentException("Invalid UTF-8 field length");
        final String value;
        try {
            value = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(buf.nioBuffer(buf.readerIndex(), length))
                .toString();
        } catch (CharacterCodingException invalid) {
            throw new IllegalArgumentException("Invalid UTF-8 field", invalid);
        }
        if (value.length() > maxCharacters) throw new IllegalArgumentException("UTF-8 field too long");
        buf.skipBytes(length);
        return value;
    }

    public static void write(ByteBuf buf, String value, int maxCharacters) {
        if (!fits(value, maxCharacters)) throw new IllegalArgumentException("UTF-8 field too long");
        ByteBufUtils.writeUTF8String(buf, value);
    }
}

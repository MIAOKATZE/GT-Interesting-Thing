package com.miaokatze.gtit.gift.ui;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;
import java.util.function.Supplier;

import net.minecraft.network.PacketBuffer;

import com.cleanroommc.modularui.value.sync.StringSyncValue;

/** MUI2's default string serializer truncates by UTF-8 bytes; gift edits must reject the whole edit. */
class GiftTextSync extends StringSyncValue {

    static final String REJECTED_TEXT = "\0Input is too long; original retained";
    private final int limit;

    GiftTextSync(Supplier<String> getter, Consumer<String> setter, int limit) {
        super(getter, setter);
        this.limit = limit;
    }

    @Override
    protected void serialize(PacketBuffer buffer, String text) throws IOException {
        byte[] bytes = text == null ? new byte[0] : text.getBytes(StandardCharsets.UTF_8);
        if (text == null || text.length() > limit || bytes.length > GiftEditorSession.MAX_TEXT_BYTES) {
            bytes = REJECTED_TEXT.getBytes(StandardCharsets.UTF_8);
        }
        buffer.writeVarIntToBuffer(bytes.length);
        buffer.writeBytes(bytes);
    }

    @Override
    protected String deserialize(PacketBuffer buffer) throws IOException {
        int length = buffer.readVarIntFromBuffer();
        if (length < 0 || length > GiftEditorSession.MAX_TEXT_BYTES || length != buffer.readableBytes())
            throw new IOException("Invalid gift text length; original retained");
        byte[] bytes = new byte[length];
        buffer.readBytes(bytes);
        try {
            String text = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString();
            if (text.length() > limit || text.indexOf('\0') >= 0)
                throw new IOException("Input is too long; original retained");
            return text;
        } catch (CharacterCodingException e) {
            throw new IOException("Invalid gift text encoding; original retained", e);
        }
    }
}

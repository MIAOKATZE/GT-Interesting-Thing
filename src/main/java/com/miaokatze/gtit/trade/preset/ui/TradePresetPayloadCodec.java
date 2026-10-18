package com.miaokatze.gtit.trade.preset.ui;

import java.nio.charset.StandardCharsets;

import net.minecraft.network.PacketBuffer;

import com.google.gson.Gson;
import com.miaokatze.gtit.trade.preset.TradePresetManager.Snapshot;

/** The production factory wire format, independent of GUI classes on a dedicated server. */
final class TradePresetPayloadCodec {

    private static final Gson JSON = new Gson();
    private static final int MAX_SNAPSHOT_BYTES = 1_048_576;

    private TradePresetPayloadCodec() {}

    static void writePayload(Snapshot snapshot, String editId, PacketBuffer buffer) {
        byte[] bytes = JSON.toJson(snapshot)
            .getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_SNAPSHOT_BYTES) throw new IllegalArgumentException("Preset catalog is too large");
        buffer.writeInt(bytes.length);
        buffer.writeBytes(bytes);
        byte[] edit = editId.getBytes(StandardCharsets.UTF_8);
        if (edit.length > 64) throw new IllegalArgumentException("Invalid preset edit ID");
        buffer.writeByte(edit.length);
        buffer.writeBytes(edit);
    }

    static Payload readPayload(PacketBuffer buffer) {
        int length = buffer.readInt();
        if (length < 0 || length > MAX_SNAPSHOT_BYTES || length > buffer.readableBytes())
            throw new IllegalArgumentException("Invalid preset snapshot length");
        byte[] bytes = new byte[length];
        buffer.readBytes(bytes);
        Snapshot snapshot = JSON.fromJson(new String(bytes, StandardCharsets.UTF_8), Snapshot.class);
        int editLength = buffer.readUnsignedByte();
        if (editLength > 64 || editLength > buffer.readableBytes())
            throw new IllegalArgumentException("Invalid preset edit ID");
        byte[] edit = new byte[editLength];
        buffer.readBytes(edit);
        return new Payload(snapshot, new String(edit, StandardCharsets.UTF_8));
    }

    static final class Payload {

        final Snapshot snapshot;
        final String editId;

        Payload(Snapshot snapshot, String editId) {
            this.snapshot = snapshot;
            this.editId = editId;
        }
    }
}

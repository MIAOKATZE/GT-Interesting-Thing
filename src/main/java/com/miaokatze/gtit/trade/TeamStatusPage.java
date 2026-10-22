package com.miaokatze.gtit.trade;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import net.minecraft.network.PacketBuffer;

/** Immutable, bounded view of exactly one team's member page. */
public final class TeamStatusPage {

    public static final int PAGE_SIZE = 8;
    public static final int MAX_TEXT = 128;

    public enum Source {
        BQ,
        NEKO
    }

    public enum State {
        READY,
        MISSING,
        NONE,
        DISABLED,
        ERROR,
        LOADING
    }

    public final Source source;
    public final State state;
    public final String teamName;
    public final int page;
    public final int total;
    public final List<Member> members;

    public TeamStatusPage(Source source, State state, String teamName, int page, int total, List<Member> members) {
        if (source == null || state == null
            || page < 0
            || total < 0
            || members == null
            || members.size() > PAGE_SIZE
            || members.size() > total
            || page > lastPage(total)
            || (state == State.READY && members.size() != Math.min(PAGE_SIZE, total - page * PAGE_SIZE))
            || (state != State.READY && (total != 0 || !members.isEmpty()))) {
            throw new IllegalArgumentException("Invalid team status page");
        }
        this.source = source;
        this.state = state;
        this.teamName = boundedText(teamName);
        this.page = page;
        this.total = total;
        this.members = Collections.unmodifiableList(new ArrayList<>(members));
    }

    public static TeamStatusPage empty(Source source, State state) {
        return new TeamStatusPage(source, state, "", 0, 0, Collections.emptyList());
    }

    public static int lastPage(int total) {
        return total <= 0 ? 0 : (total - 1) / PAGE_SIZE;
    }

    public static String boundedText(String text) {
        if (text == null) return "";
        String clean = text.replaceAll("[\\p{Cntrl}§]", "");
        return clean.length() <= MAX_TEXT ? clean : clean.substring(0, MAX_TEXT - 1) + "…";
    }

    public static final class Member {

        public final UUID id;
        /** Empty means no authentic cached name is known; the UI must explicitly label this. */
        public final String name;
        public final boolean online;

        public Member(UUID id, String name, boolean online) {
            if (id == null) throw new IllegalArgumentException("Missing member UUID");
            this.id = id;
            this.name = boundedText(name);
            this.online = online;
        }
    }

    public void write(PacketBuffer buffer) throws IOException {
        buffer.writeByte(source.ordinal());
        buffer.writeByte(state.ordinal());
        buffer.writeStringToBuffer(teamName);
        buffer.writeInt(page);
        buffer.writeInt(total);
        buffer.writeByte(members.size());
        for (Member member : members) {
            buffer.writeLong(member.id.getMostSignificantBits());
            buffer.writeLong(member.id.getLeastSignificantBits());
            buffer.writeStringToBuffer(member.name);
            buffer.writeBoolean(member.online);
        }
    }

    public static TeamStatusPage read(PacketBuffer buffer) throws IOException {
        try {
            int source = buffer.readUnsignedByte();
            int state = buffer.readUnsignedByte();
            if (source >= Source.values().length || state >= State.values().length) {
                throw new IOException("Invalid team status source/state");
            }
            String name = buffer.readStringFromBuffer(MAX_TEXT);
            int page = buffer.readInt();
            int total = buffer.readInt();
            int count = buffer.readUnsignedByte();
            if (count > PAGE_SIZE) throw new IOException("Too many team members in page");
            List<Member> members = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                members.add(
                    new Member(
                        new UUID(buffer.readLong(), buffer.readLong()),
                        buffer.readStringFromBuffer(MAX_TEXT),
                        buffer.readBoolean()));
            }
            if (buffer.isReadable()) throw new IOException("Trailing team status data");
            return new TeamStatusPage(Source.values()[source], State.values()[state], name, page, total, members);
        } catch (RuntimeException invalid) {
            throw new IOException("Invalid team status payload", invalid);
        }
    }
}

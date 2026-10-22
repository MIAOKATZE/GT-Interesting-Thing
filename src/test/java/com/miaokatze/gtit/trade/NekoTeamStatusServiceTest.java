package com.miaokatze.gtit.trade;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import net.minecraft.network.PacketBuffer;

import com.miaokatze.gtit.trade.TeamStatusPage.Member;
import com.miaokatze.gtit.trade.TeamStatusPage.Source;
import com.miaokatze.gtit.trade.TeamStatusPage.State;

import io.netty.buffer.Unpooled;

/** Pure read-model tests: membership isolation, pagination and hostile wire bounds. */
public final class NekoTeamStatusServiceTest {

    private static void check(boolean ok, String reason) {
        if (!ok) throw new AssertionError(reason);
    }

    private static void rejects(PacketBuffer buffer) throws Exception {
        try {
            TeamStatusPage.read(buffer);
            throw new AssertionError("Malformed status payload accepted");
        } catch (java.io.IOException expected) {}
    }

    public static void main(String[] args) throws Exception {
        UUID viewer = new UUID(0, 1);
        List<UUID> ids = new ArrayList<>();
        for (int i = 19; i >= 1; i--) ids.add(new UUID(0, i));
        List<UUID> original = new ArrayList<>(ids);
        AtomicInteger resolved = new AtomicInteger();
        java.util.function.Function<UUID, Member> resolver = id -> {
            resolved.incrementAndGet();
            return new Member(id, "RealPlayer" + id.getLeastSignificantBits(), id.equals(viewer));
        };
        TeamStatusPage first = NekoTeamStatusService.page(Source.BQ, viewer, "Actual BQ team", ids, -1, resolver);
        check(first.page == 0 && first.total == 19 && first.members.size() == 8, "Negative page clamps to first");
        check(
            first.members.get(0).id.equals(viewer) && first.members.get(0).online,
            "Stable order and true online state");
        check(first.members.get(0).name.equals("RealPlayer1"), "Authentic names preserved");
        check(resolved.get() == 8 && ids.equals(original), "Resolve only page members; never mutate source list");
        TeamStatusPage last = NekoTeamStatusService
            .page(Source.NEKO, viewer, "Actual neko team", ids, Integer.MAX_VALUE, resolver);
        check(
            last.page == 2 && last.members.size() == 3 && last.source == Source.NEKO,
            "Final page is reachable without truncation");
        check(
            last.teamName.equals("Actual neko team") && first.teamName.equals("Actual BQ team"),
            "Sources remain distinct");
        int before = resolved.get();
        TeamStatusPage denied = NekoTeamStatusService.page(Source.BQ, new UUID(5, 9), "Other team", ids, 0, resolver);
        check(
            denied.state == State.NONE && denied.members.isEmpty() && denied.teamName.isEmpty(),
            "Nonmember cannot view another roster");
        check(before == resolved.get(), "Nonmember cannot resolve any names");
        check(
            NekoTeamStatusService.page(Source.NEKO, viewer, "", Collections.emptyList(), 0, resolver).state
                == State.NONE,
            "No membership remains explicit");
        check(
            NekoTeamStatusService.query(null, Source.BQ, 0).state == State.ERROR,
            "No authenticated server player is rejected");
        try {
            first.members.clear();
            throw new AssertionError("Mutable roster snapshot");
        } catch (UnsupportedOperationException expected) {}
        PacketBuffer encoded = new PacketBuffer(Unpooled.buffer());
        first.write(encoded);
        check(encoded.readableBytes() < 4096, "Each roster packet is bounded");
        TeamStatusPage roundTrip = TeamStatusPage.read(encoded);
        check(
            roundTrip.source == Source.BQ && roundTrip.total == 19
                && roundTrip.members.get(0).online
                && roundTrip.members.get(0).name.equals("RealPlayer1"),
            "Wire preserves source/name/online state");
        for (State state : new State[] { State.MISSING, State.NONE, State.DISABLED, State.ERROR }) {
            PacketBuffer absent = new PacketBuffer(Unpooled.buffer());
            TeamStatusPage.empty(Source.BQ, state)
                .write(absent);
            check(TeamStatusPage.read(absent).state == state, "Absence states must not collapse into one");
        }
        check(
            new Member(viewer, null, false).name.isEmpty(),
            "Unknown name is explicit, never a fabricated player name");
        check(
            TeamStatusPage.boundedText(String.join("", Collections.nCopies(256, "a")))
                .length() == 128,
            "Team name wire length is bounded");
        PacketBuffer badSource = new PacketBuffer(Unpooled.buffer());
        badSource.writeByte(99);
        rejects(badSource);
        PacketBuffer excess = new PacketBuffer(Unpooled.buffer());
        excess.writeByte(0)
            .writeByte(0);
        excess.writeStringToBuffer("team");
        excess.writeInt(0)
            .writeInt(9)
            .writeByte(9);
        rejects(excess);
        PacketBuffer trailing = new PacketBuffer(Unpooled.buffer());
        first.write(trailing);
        trailing.writeByte(1);
        rejects(trailing);
        PacketBuffer truncated = new PacketBuffer(Unpooled.buffer());
        truncated.writeByte(0)
            .writeByte(0);
        rejects(truncated);
        PacketBuffer badPage = new PacketBuffer(Unpooled.buffer());
        badPage.writeByte(0)
            .writeByte(0);
        badPage.writeStringToBuffer("team");
        badPage.writeInt(Integer.MAX_VALUE)
            .writeInt(0)
            .writeByte(0);
        rejects(badPage);
        System.out.println(
            "NekoTeamStatusServiceTest: read-only scope, pagination, source separation and wire bounds passed");
    }
}

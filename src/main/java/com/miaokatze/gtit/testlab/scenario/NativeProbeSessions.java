package com.miaokatze.gtit.testlab.scenario;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.EnumConnectionState;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.common.util.FakePlayer;

import com.miaokatze.gtit.testlab.ProbeContext;
import com.miaokatze.gtit.util.PlayerLookup;

import io.netty.channel.Channel;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.ReferenceCountUtil;

/**
 * Explicit, short-lived server-session fixture for two owned remote-vending actors. No login events,
 * physical client, wire protocol, or rendering are claimed. The production session predicate is not
 * overridden: native channelActive binds an open channel and the exact actors enter the live list.
 */
final class NativeProbeSessions implements AutoCloseable {

    private final ProbeContext context;
    private final MinecraftServer server;
    private final List<EntityPlayerMP> online;
    private final List<Entry> entries = new ArrayList<>();
    private boolean closed;

    private NativeProbeSessions(ProbeContext context) {
        this.context = context;
        server = MinecraftServer.getServer();
        if (server == null || server.getConfigurationManager() == null)
            throw new IllegalStateException("No native server player list");
        online = server.getConfigurationManager().playerEntityList;
        // This specialized fixture must never be introduced alongside real players.
        if (!online.isEmpty()) throw new IllegalStateException("Native session probe requires an empty player list");
    }

    static NativeProbeSessions open(ProbeContext context, FakePlayer first, FakePlayer second) {
        if (first == second || context.player("remote-owner") != first || context.player("remote-stranger") != second)
            throw new IllegalArgumentException("Only the two exact run-owned remote-vending actors are accepted");
        NativeProbeSessions sessions = new NativeProbeSessions(context);
        // Register rollback before creating a channel, replacing a handler, or adding an actor.
        context.onCleanup(sessions::close);
        try {
            sessions.connect(first);
            sessions.connect(second);
            if (!PlayerLookup.isCurrentSession(first) || !PlayerLookup.isCurrentSession(second))
                throw new IllegalStateException("Native actor/channel/list identities failed production admission");
            return sessions;
        } catch (RuntimeException | Error failure) {
            try {
                sessions.close();
            } catch (RuntimeException cleanup) {
                failure.addSuppressed(cleanup);
            }
            throw failure;
        }
    }

    private void connect(FakePlayer actor) {
        if (actor.isDead || !actor.isEntityAlive()
            || PlayerLookup.getOnlinePlayerByUuid(actor.getUniqueID()) != null
            || actor.playerNetServerHandler != null && actor.playerNetServerHandler.netManager != null
                && actor.playerNetServerHandler.netManager.isChannelOpen())
            throw new IllegalStateException("Synthetic actor is not an unregistered, disconnected live fixture");
        Entry entry = new Entry(actor);
        entries.add(entry);
        entry.network = new NetworkManager(false);
        // Real native lifecycle, not reflection into NetworkManager.channel and not an isChannelOpen override.
        entry.channel = new EmbeddedChannel(entry.network);
        entry.network.setConnectionState(EnumConnectionState.PLAY);
        entry.connection = new NetHandlerPlayServer(MinecraftServer.getServer(), entry.network, actor) {

            @Override
            public void sendPacket(Packet packet) {
                // Deliberate socket-free sink, like SilentProbeConnection. This is not packet delivery proof.
            }
        };
        online.add(actor);
    }

    void expireChannel(FakePlayer actor) {
        for (Entry entry : entries) if (entry.actor == actor) {
            entry.channel.close()
                .syncUninterruptibly();
            return;
        }
        throw new IllegalArgumentException("Actor not owned by this native session scope");
    }

    @Override
    public void close() {
        if (closed) return;
        RuntimeException failure = null;
        for (Entry entry : entries) {
            // Independent phases: one transport/list failure cannot strand the other owned actor.
            failure = attempt(failure, () -> online.removeIf(value -> value == entry.actor));
            failure = attempt(failure, () -> {
                NetHandlerPlayServer current = entry.actor.playerNetServerHandler;
                if (current == entry.connection
                    || current != null && entry.network != null && current.netManager == entry.network)
                    entry.actor.playerNetServerHandler = entry.previous;
                else if (current != entry.previous)
                    throw new IllegalStateException("Synthetic actor handler identity changed outside its owned scope");
            });
            failure = attempt(failure, () -> {
                Channel channel = entry.ownedChannel();
                if (channel != null) channel.close()
                    .syncUninterruptibly();
            });
            failure = attempt(failure, () -> {
                Channel channel = entry.ownedChannel();
                if (channel instanceof EmbeddedChannel embedded) {
                    Object message;
                    while ((message = embedded.readInbound()) != null) ReferenceCountUtil.release(message);
                    while ((message = embedded.readOutbound()) != null) ReferenceCountUtil.release(message);
                }
            });
            failure = attempt(failure, () -> {
                Channel channel = entry.ownedChannel();
                if (entry.actor.playerNetServerHandler != entry.previous || online.stream()
                    .anyMatch(value -> value == entry.actor) || channel != null && channel.isOpen())
                    throw new IllegalStateException("Owned native session cleanup is incomplete");
            });
        }
        boolean unchanged = MinecraftServer.getServer() == server
            && server.getConfigurationManager().playerEntityList == online
            && online.isEmpty();
        context.check(
            "concurrent-native-session-cleanup",
            unchanged && failure == null,
            "Only exact owned actors removed; handlers/channels restored; original server/list and baseline empty contents; remaining="
                + online.size());
        // Retry on later task/context cleanup if any owned resource could not be restored.
        closed = failure == null;
        if (failure != null) throw failure;
    }

    private static RuntimeException attempt(RuntimeException previous, Runnable action) {
        try {
            action.run();
        } catch (RuntimeException | LinkageError error) {
            if (previous == null) return new IllegalStateException("Native session cleanup failed", error);
            previous.addSuppressed(error);
        }
        return previous;
    }

    private static final class Entry {

        final FakePlayer actor;
        final NetHandlerPlayServer previous;
        NetworkManager network;
        EmbeddedChannel channel;
        NetHandlerPlayServer connection;

        Entry(FakePlayer actor) {
            this.actor = actor;
            previous = actor.playerNetServerHandler;
        }

        Channel ownedChannel() {
            // Native Forge accessor also recovers a channel activated before EmbeddedChannel's constructor throws.
            return channel != null ? channel : network == null ? null : network.channel();
        }
    }
}

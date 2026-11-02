package com.miaokatze.gtit.gui.vm;

import java.util.UUID;

import net.minecraft.network.PacketBuffer;

import com.cleanroommc.modularui.value.sync.ModularSyncManager;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.StringSyncValue;
import com.cleanroommc.modularui.value.sync.ValueSyncHandler;
import com.miaokatze.gtit.testutil.MinecraftTestBootstrap;
import com.miaokatze.gtit.testutil.SimpleAssert;
import com.miaokatze.gtit.trade.v2.NekoHistoryManager;
import com.miaokatze.gtit.trade.v2.NekoTrade;
import com.miaokatze.gtit.trade.v2.NekoTradeDatabase;
import com.miaokatze.gtit.trade.v2.NekoTradeGroup;

import io.netty.buffer.Unpooled;

/** Real logical-client construction and MUI2 packet reads, without a game window or server world. */
public final class StatusCodecSyncTest {

    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID COOLDOWN_GROUP = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID ONE_TIME_GROUP = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final String[] CHANNELS = { "nekoV2BqLockStatus", "nekoV2CooldownStatus", "nekoV2TradeableStatus",
        "nekoV2TeamScale" };

    public static void main(String[] args) throws Exception {
        if (!MinecraftTestBootstrap.enter(StatusCodecSyncTest.class, args)) return;
        cpw.mods.fml.server.FMLServerHandler.instance();
        NekoTradeDatabase.INSTANCE.clear();
        try {
            addGroup(COOLDOWN_GROUP, 60);
            addGroup(ONE_TIME_GROUP, -1);
            historyRemainsUnavailable();
            clientConstruction();
            clientConditionIsolation();
            receivedStatuses();
            serverStillRequiresHistory();
            System.out.println("StatusCodecSyncTest: 4 cases passed");
        } finally {
            NekoTradeDatabase.INSTANCE.clear();
        }
    }

    private static void addGroup(UUID id, int cooldown) {
        NekoTradeGroup group = new NekoTradeGroup(id);
        group.setCooldown(cooldown);
        group.getTrades()
            .add(new NekoTrade());
        NekoTradeDatabase.INSTANCE.addTradeGroup(group);
    }

    private static void clientConstruction() {
        // The singleton deliberately has no bound server world, as on a remote client.
        // A null machine also makes an accidental client checkTrade call fail immediately.
        StatusCodec codec = new StatusCodec(
            null,
            null,
            () -> { throw new AssertionError("Construction must not invoke the receive callback"); });
        PanelSyncManager manager = manager(true);
        codec.registerSyncValues(manager, PLAYER);
        manager.initialize("status_codec_client");
        for (String channel : CHANNELS) {
            StringSyncValue value = manager.findSyncHandler(channel, StringSyncValue.class);
            SimpleAssert.eq("", value.getValue(), channel + " starts empty on the logical client");
            SimpleAssert.that(!value.isAllowC2S(), channel + " remains S2C only");
            value.updateCacheFromSource(true);
            SimpleAssert.eq("", value.getValue(), channel + " client getter stays safe after construction");
        }
        historyRemainsUnavailable();
    }

    private static void clientConditionIsolation() {
        NekoTradeGroup guarded = new NekoTradeGroup(UUID.randomUUID()) {

            @Override
            public boolean isConditionsSatisfied(UUID playerId) {
                throw new AssertionError("Client must not evaluate authoritative trade conditions");
            }
        };
        NekoTradeDatabase.INSTANCE.addTradeGroup(guarded);
        try {
            new StatusCodec(null, null, () -> {}).registerSyncValues(manager(true), PLAYER);
        } finally {
            NekoTradeDatabase.INSTANCE.removeTradeGroup(guarded.getId());
        }
    }

    private static void receivedStatuses() throws Exception {
        int[] refreshes = { 0 };
        StatusCodec codec = new StatusCodec(null, null, () -> refreshes[0]++);
        PanelSyncManager manager = manager(true);
        codec.registerSyncValues(manager, PLAYER);
        manager.initialize("status_codec_receive");
        String cooldownKey = COOLDOWN_GROUP + ":0";
        String oneTimeKey = ONE_TIME_GROUP + ":0";

        receive(manager, CHANNELS[0], COOLDOWN_GROUP + ":true," + ONE_TIME_GROUP + ":false");
        receive(manager, CHANNELS[1], cooldownKey + ":37," + oneTimeKey + ":-1");
        receive(manager, CHANNELS[2], cooldownKey + ":false," + oneTimeKey + ":true");
        receive(manager, CHANNELS[3], COOLDOWN_GROUP + ":4:2");
        SimpleAssert.that(codec.getBqLocked(COOLDOWN_GROUP), "S2C BQ lock reaches the client cache");
        SimpleAssert.that(!codec.getBqLocked(ONE_TIME_GROUP), "S2C unlocked group stays unlocked");
        SimpleAssert.eq(37L, codec.getCooldownRemaining(cooldownKey), "S2C positive cooldown survives decoding");
        SimpleAssert.that(codec.isOneTimeRedeemed(oneTimeKey), "S2C one-time redemption marker survives decoding");
        SimpleAssert.eq(0L, codec.getCooldownRemaining(oneTimeKey), "one-time marker is not a countdown");
        SimpleAssert.eq(Boolean.FALSE, codec.getTradeable(cooldownKey), "S2C unavailable trade reaches the client");
        SimpleAssert.eq(Boolean.TRUE, codec.getTradeable(oneTimeKey), "S2C available trade reaches the client");
        long[] team = codec.getTeamScale(COOLDOWN_GROUP);
        SimpleAssert
            .that(team != null && team[0] == 4L && team[1] == 2L, "S2C team capacity and usage survive decoding");
        SimpleAssert.eq(1, refreshes[0], "tradeable receive callback refreshes the host exactly once");

        for (String channel : CHANNELS) receive(manager, channel, "");
        SimpleAssert.that(!codec.getBqLocked(COOLDOWN_GROUP), "empty update clears old BQ locks");
        SimpleAssert.eq(0L, codec.getCooldownRemaining(cooldownKey), "empty update clears old cooldowns");
        SimpleAssert.that(!codec.isOneTimeRedeemed(oneTimeKey), "empty update clears old one-time markers");
        SimpleAssert.eq(null, codec.getTradeable(cooldownKey), "empty update clears old tradeable states");
        SimpleAssert.eq(null, codec.getTeamScale(COOLDOWN_GROUP), "empty update clears old team usage");
        SimpleAssert.eq(2, refreshes[0], "empty tradeable update also refreshes the host");
        historyRemainsUnavailable();
    }

    private static void receive(PanelSyncManager manager, String channel, String status) throws Exception {
        PacketBuffer packet = new PacketBuffer(Unpooled.buffer());
        try {
            new StringSyncValue(() -> status).write(packet);
            manager.receiveWidgetUpdate(
                PanelSyncManager.makeSyncKey(channel, 0),
                false,
                ValueSyncHandler.SYNC_VALUE,
                packet);
            SimpleAssert.that(!packet.isReadable(), channel + " consumes the encoded status packet");
        } finally {
            packet.release();
        }
    }

    private static void serverStillRequiresHistory() {
        try {
            new StatusCodec(null, null, () -> {}).registerSyncValues(manager(false), PLAYER);
            throw new AssertionError("Server construction must retain the history readiness check");
        } catch (IllegalStateException expected) {
            SimpleAssert.eq(
                "History storage is not ready for this world",
                expected.getMessage(),
                "server failure remains explicit");
        }
    }

    private static void historyRemainsUnavailable() {
        try {
            NekoHistoryManager.INSTANCE.getHistory(PLAYER, COOLDOWN_GROUP);
            throw new AssertionError("The remote-client fixture must not have a bound history store");
        } catch (IllegalStateException expected) {
            SimpleAssert.eq(
                "History storage is not ready for this world",
                expected.getMessage(),
                "unbound history stays guarded");
        }
    }

    private static PanelSyncManager manager(boolean client) {
        return new PanelSyncManager(new ModularSyncManager(client), true);
    }
}

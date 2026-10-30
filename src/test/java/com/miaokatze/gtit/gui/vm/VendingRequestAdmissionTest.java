package com.miaokatze.gtit.gui.vm;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import com.miaokatze.gtit.common.machine.v2.VendingServerActions;
import com.miaokatze.gtit.testutil.MinecraftTestBootstrap;
import com.miaokatze.gtit.testutil.RewardDeliveryTestPlayer;
import com.miaokatze.gtit.util.ServerTaskScheduler;

import cpw.mods.fml.common.gameevent.TickEvent;

/** Real vanilla actor/channel identity; no machine, UI, file or inventory obligation is simulated here. */
public final class VendingRequestAdmissionTest {

    private static int checks;

    public static void main(String[] args) throws Exception {
        if (!MinecraftTestBootstrap.enter(VendingRequestAdmissionTest.class, args)) return;
        AtomicInteger ran = new AtomicInteger(), completed = new AtomicInteger();
        try (RewardDeliveryTestPlayer actor = new RewardDeliveryTestPlayer(UUID.randomUUID(), false)) {
            ServerTaskScheduler.clear();
            check(
                !VendingServerActions
                    .schedulePlayerRequest(actor.player, ran::incrementAndGet, completed::incrementAndGet),
                "closed admission rejects");
            check(ran.get() == 0 && completed.get() == 1, "rejection cleans only its latch immediately");
            ServerTaskScheduler.startRequests();
            check(
                VendingServerActions
                    .schedulePlayerRequest(actor.player, ran::incrementAndGet, completed::incrementAndGet),
                "live request accepted");
            for (int machine = 0; machine < 100; machine++) VendingServerActions.drainServerActions();
            check(ran.get() == 0 && completed.get() == 1, "machine count cannot drain player requests");
            ServerTaskScheduler.INSTANCE.onServerTick(new TickEvent.ServerTickEvent(TickEvent.Phase.START));
            check(ran.get() == 0, "request waits until all machine ticks finish");
            tick();
            check(ran.get() == 1 && completed.get() == 2, "successful consumption cleans once");
            int accepted = 0;
            for (int i = 0; i < 10000; i++) {
                if (VendingServerActions
                    .schedulePlayerRequest(actor.player, ran::incrementAndGet, completed::incrementAndGet)) accepted++;
            }
            check(accepted == 16 && ran.get() == 1 && completed.get() == 9986, "per-actor admission is bounded");
            tick();
            check(ran.get() == 17 && completed.get() == 10002, "accepted requests and cleanup execute once");
            VendingServerActions.schedulePlayerRequest(actor.player, ran::incrementAndGet, completed::incrementAndGet);
            actor.disconnect();
            tick();
            check(ran.get() == 17 && completed.get() == 10003, "disconnected actor skips business but cleans latch");
            actor.connect();
            VendingServerActions.schedulePlayerRequest(actor.player, () -> {}, completed::incrementAndGet);
            VendingServerActions.schedulePlayerRequest(
                actor.player,
                () -> { throw new IllegalStateException("expected test failure"); },
                completed::incrementAndGet);
            VendingServerActions.schedulePlayerRequest(actor.player, ran::incrementAndGet, completed::incrementAndGet);
            tick();
            check(ran.get() == 18 && completed.get() == 10006, "skipped guard, failure and later success each clean");
            VendingServerActions.schedulePlayerRequest(actor.player, ran::incrementAndGet, completed::incrementAndGet);
            ServerTaskScheduler.clear();
            ServerTaskScheduler.startRequests();
            tick();
            check(ran.get() == 18 && completed.get() == 10006, "stop drops old GUI requests and latches, not replay");
            for (int i = 0; i < 1000; i++) ServerTaskScheduler.scheduleServerTask(ran::incrementAndGet);
            tick();
            check(ran.get() == 1018, "already-committed obligation queue remains uncapped");
        } finally {
            ServerTaskScheduler.clear();
        }
        System.out.println(
            "VendingRequestAdmissionTest: " + checks
                + " native-actor admission, completion and ordering checks passed");
    }

    private static void tick() {
        ServerTaskScheduler.INSTANCE.onServerTick(new TickEvent.ServerTickEvent(TickEvent.Phase.END));
    }

    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }
}

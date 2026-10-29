package com.miaokatze.gtit.lottery;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.world.World;
import net.minecraft.world.storage.ISaveHandler;

import com.miaokatze.gtit.testutil.MinecraftTestBootstrap;
import com.miaokatze.gtit.testutil.RewardDeliveryTestPlayer;

/** Persistent pity belongs to its world and unreadable existing records never become zero. */
public final class LotteryPersistenceTest {

    public static void main(String[] args) throws Exception {
        if (!Boolean.getBoolean("gtit.rewards.testScratch"))
            throw new IllegalStateException("Run only in an isolated scratch working directory with -Dgtit.rewards.testScratch=true");
        if (!MinecraftTestBootstrap.enter(LotteryPersistenceTest.class, args)) return;
        // These focused fixtures bind a temporary save directory instead of starting a server.
        try {
            Field ready = LotteryManager.class.getDeclaredField("sessionUnavailable");
            ready.setAccessible(true);
            ready.setBoolean(LotteryManager.INSTANCE, false);
        } catch (NoSuchFieldException legacyBaseline) { /* Pre-fence baseline comparison. */ }
        int failed = 0;
        for (String name : new String[] { "worldSwitchSavesBeforeClearing", "corruptPityFailsClosed", "failedWorldSwitchNeverServesOldPity" }) {
            try {
                LotteryPersistenceTest.class.getDeclaredMethod(name).invoke(null);
                System.out.println("PASS " + name);
            } catch (ReflectiveOperationException failure) {
                failed++;
                System.err.println("FAIL " + name + ": " + failure.getCause());
            }
        }
        if (failed > 0) throw new AssertionError(failed + " lottery persistence regressions failed");
    }

    private static void worldSwitchSavesBeforeClearing() throws Exception {
        LotteryManager manager = LotteryManager.INSTANCE;
        Path old = Files.createTempDirectory("gtit-lottery-old-");
        Path next = Files.createTempDirectory("gtit-lottery-new-");
        set(manager, "saveDir", old.toFile());
        Field field = LotteryManager.class.getDeclaredField("pityCounters");
        field.setAccessible(true);
        @SuppressWarnings("unchecked") Map<UUID, Map<String, Integer>> counters = (Map<UUID, Map<String, Integer>>) field.get(manager);
        UUID id = UUID.randomUUID();
        counters.put(id, new java.util.HashMap<>(java.util.Collections.singletonMap("pool", 87)));
        try (RewardDeliveryTestPlayer actor = new RewardDeliveryTestPlayer(UUID.randomUUID(), false)) {
            Object handler = java.lang.reflect.Proxy.newProxyInstance(LotteryPersistenceTest.class.getClassLoader(),
                new Class<?>[] { ISaveHandler.class }, (proxy, method, arguments) ->
                    method.getName().equals("getWorldDirectory") ? next.toFile() : null);
            Field save = World.class.getDeclaredField("saveHandler");
            save.setAccessible(true);
            save.set(actor.world, handler);
            manager.init(actor.world);
            check(Files.exists(old.resolve(id + ".dat")), "Old world's pending pity is saved before switch");
            check(manager.getPityCounter(id, "pool") == 0, "New world does not inherit prior pity");
            NBTTagCompound nbt = CompressedStreamTools.read(old.resolve(id + ".dat").toFile());
            check(nbt.getCompoundTag("pity").getInteger("pool") == 87, "Prior world record remains intact");
        }
    }

    private static void corruptPityFailsClosed() throws Exception {
        LotteryManager manager = LotteryManager.INSTANCE;
        Path root = Files.createTempDirectory("gtit-lottery-corrupt-");
        set(manager, "saveDir", root.toFile());
        UUID id = UUID.randomUUID();
        Path file = root.resolve(id + ".dat");
        byte[] original = { 1, 2, 3, 4, 5 };
        Files.write(file, original);
        boolean rejected = false;
        try { manager.loadTeamData(id); } catch (IllegalStateException expected) { rejected = true; }
        check(rejected, "Existing invalid data must not be treated as zero pity");
        rejected = false;
        try { manager.getPityCounter(id, "pool"); } catch (IllegalStateException expected) { rejected = true; }
        check(rejected, "Draw cannot continue using an unreadable team's zero fallback");
        try { manager.saveTeamData(id); } catch (IllegalStateException expected) { }
        check(java.util.Arrays.equals(original, Files.readAllBytes(file)), "Rejected load cannot rewrite original bytes");
    }

    private static void failedWorldSwitchNeverServesOldPity() throws Exception {
        LotteryManager manager = LotteryManager.INSTANCE;
        Path old = Files.createTempDirectory("gtit-lottery-failed-old-");
        Path next = Files.createTempDirectory("gtit-lottery-unbound-new-");
        set(manager, "saveDir", old.toFile());
        Field field = LotteryManager.class.getDeclaredField("pityCounters");
        field.setAccessible(true);
        @SuppressWarnings("unchecked") Map<UUID, Map<String, Integer>> counters = (Map<UUID, Map<String, Integer>>) field.get(manager);
        UUID id = UUID.randomUUID();
        counters.put(id, new java.util.HashMap<>(java.util.Collections.singletonMap("pool", 87)));
        Path blocker = Files.createDirectory(old.resolve(id + ".dat"));
        Files.write(blocker.resolve("keep"), new byte[] { 1 });
        try (RewardDeliveryTestPlayer actor = new RewardDeliveryTestPlayer(UUID.randomUUID(), false)) {
            actor.setWorldDirectory(next);
            boolean failed = false;
            try { manager.init(actor.world); } catch (IllegalStateException expected) { failed = true; }
            check(failed, "Failed old-world write prevents rebinding");
            failed = false;
            try { manager.getPityCounter(id, "pool"); } catch (IllegalStateException expected) { failed = true; }
            check(failed, "Old pity cannot serve the next world after a failed transition");
            check(!Files.exists(next.resolve("gtit_lottery").resolve(id + ".dat")), "Old cache never overwrites next world");
            check(Files.exists(blocker.resolve("keep")), "Old record remains intact");
        }
    }

    private static void set(LotteryManager manager, String name, Object value) throws Exception {
        Field field = LotteryManager.class.getDeclaredField(name); field.setAccessible(true); field.set(manager, value);
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}

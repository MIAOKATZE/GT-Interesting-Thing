package com.miaokatze.gtit.trade.v2;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.UUID;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;

import com.miaokatze.gtit.testutil.SimpleAssert;
import com.miaokatze.gtit.testutil.TestRunner;
import com.miaokatze.gtit.trade.NekoTeamData;
import com.miaokatze.gtit.util.AtomicNbtFile;

/** Actual manager, NBT, files, team-data receipts and failed replacement paths; no model ledger. */
public final class HistoryPersistenceTest {

    private interface Case {

        void run(NekoHistoryManager manager, File directory, UUID player, UUID group) throws Exception;
    }

    public static void main(String[] args) {
        run();
    }

    public static void run() {
        // Standalone runs must not initialize or consult a live Teams registry. Harness facades may
        // intentionally omit this field; the tested manager/team-data implementations remain real.
        Field availability = null;
        Object previous = null;
        try {
            availability = com.miaokatze.gtit.trade.TeamDataProvider.class.getDeclaredField("available");
            availability.setAccessible(true);
            previous = availability.get(null);
            availability.set(null, Boolean.FALSE);
        } catch (NoSuchFieldException fixtureFacade) {
            // Narrow external facade used by the cached-NBT harness has no registry initialization.
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError(failure);
        }
        try {
            runCases();
        } finally {
            if (availability != null) try {
                availability.set(null, previous);
            } catch (ReflectiveOperationException failure) {
                throw new AssertionError(failure);
            }
        }
    }

    private static void runCases() {
        Map<String, Runnable> cases = new LinkedHashMap<>();
        cases.put("corruptBytesFailClosed", () -> isolated(HistoryPersistenceTest::corruptBytesFailClosed));
        cases.put("malformedRecordsFailClosed", () -> isolated(HistoryPersistenceTest::malformedRecordsFailClosed));
        cases.put("failedLogoutRetainsOwner", () -> isolated(HistoryPersistenceTest::failedLogoutRetainsOwner));
        cases.put("failedClearRetainsOwner", () -> isolated(HistoryPersistenceTest::failedClearRetainsOwner));
        cases.put("worldTransitionCannotLeak", () -> isolated(HistoryPersistenceTest::worldTransitionCannotLeak));
        cases.put(
            "oldConsumedArchiveAndFreshFallback",
            () -> isolated(HistoryPersistenceTest::oldConsumedArchiveAndFreshFallback));
        cases.put("failedJournalDoesNotConsume", () -> isolated(HistoryPersistenceTest::failedJournalDoesNotConsume));
        cases.put(
            "allSourcesValidatedBeforeMutation",
            () -> isolated(HistoryPersistenceTest::allSourcesValidatedBeforeMutation));
        cases.put("laggingTeamSaveReplaysOnce", () -> isolated(HistoryPersistenceTest::laggingTeamSaveReplaysOnce));
        cases.put(
            "markerFailureReplaysDurableIntent",
            () -> isolated(HistoryPersistenceTest::markerFailureReplaysDurableIntent));
        cases.put(
            "newMemberMigrationIsNotSkipped",
            () -> isolated(HistoryPersistenceTest::newMemberMigrationIsNotSkipped));
        cases.put(
            "receiptFollowsMergeAndMovement",
            () -> isolated(HistoryPersistenceTest::receiptFollowsMergeAndMovement));
        cases.put("resetKeepsReplayReceipt", () -> isolated(HistoryPersistenceTest::resetKeepsReplayReceipt));
        cases.put("corruptJournalFencesNewWorld", () -> isolated(HistoryPersistenceTest::corruptJournalFencesNewWorld));
        cases.put(
            "liveReferenceFollowsMigration",
            () -> isolated(HistoryPersistenceTest::liveReferenceFollowsMigration));
        cases.put("saveAndCompletionLockOrder", () -> isolated(HistoryPersistenceTest::saveAndCompletionLockOrder));
        cases.put("repeatedMovementNeverReopensOnce", HistoryPersistenceTest::repeatedMovementNeverReopensOnce);
        cases.put("historyCountersNeverWrap", HistoryPersistenceTest::historyCountersNeverWrap);
        cases.put("teamLoadIsTransactional", HistoryPersistenceTest::teamLoadIsTransactional);
        TestRunner.run(HistoryPersistenceTest.class, cases);
    }

    private static void isolated(Case test) {
        File directory = null;
        try {
            directory = Files.createTempDirectory("gtit-history-regression-")
                .toFile();
            NekoHistoryManager manager = manager(directory);
            test.run(manager, directory, UUID.randomUUID(), UUID.randomUUID());
        } catch (Exception failure) {
            throw new AssertionError(failure);
        } finally {
            try {
                if (directory != null) delete(directory);
            } catch (Exception failure) {
                throw new AssertionError("history fixture cleanup", failure);
            }
        }
    }

    private static NekoHistoryManager manager(File directory) throws Exception {
        Constructor<NekoHistoryManager> ctor = NekoHistoryManager.class.getDeclaredConstructor();
        ctor.setAccessible(true);
        NekoHistoryManager manager = ctor.newInstance();
        bind(manager, directory);
        return manager;
    }

    private static void bind(NekoHistoryManager manager, File directory) throws Exception {
        Method bind = NekoHistoryManager.class.getDeclaredMethod("bindSaveDirectory", File.class);
        bind.setAccessible(true);
        try {
            bind.invoke(manager, directory);
        } catch (InvocationTargetException failure) {
            if (failure.getCause() instanceof RuntimeException) throw (RuntimeException) failure.getCause();
            throw failure;
        }
    }

    private static void delete(File file) throws Exception {
        File[] children = file.listFiles();
        if (children != null) for (File child : children) delete(child);
        Files.deleteIfExists(file.toPath());
    }

    private static File file(File directory, UUID player) {
        return new File(directory, player + ".dat");
    }

    private static void rejected(Runnable action, String message) {
        boolean rejected = false;
        try {
            action.run();
        } catch (IllegalStateException | IllegalArgumentException expected) {
            rejected = true;
        }
        SimpleAssert.that(rejected, message);
    }

    private static NBTTagCompound record(UUID group, int count, boolean consumed) {
        NekoTradeHistory history = new NekoTradeHistory();
        for (int i = 0; i < count; i++) history.recordTrade(0);
        NBTTagCompound root = new NBTTagCompound(), entry = new NBTTagCompound();
        NBTTagList list = new NBTTagList();
        entry.setString("groupId", group.toString());
        entry.setTag("history", history.writeToNBT());
        list.appendTag(entry);
        root.setTag("histories", list);
        root.setBoolean("legacyHistoryConsumed", consumed);
        return root;
    }

    private static void corruptBytesFailClosed(NekoHistoryManager manager, File directory, UUID player, UUID group)
        throws Exception {
        byte[] original = { 31, 2, 11, 99, 0, 7 };
        Files.write(file(directory, player).toPath(), original);
        rejected(() -> manager.getHistory(player, group), "corrupt one-time history never becomes empty");
        manager.unloadPlayer(player);
        manager.clearAll();
        SimpleAssert.that(
            Arrays.equals(original, Files.readAllBytes(file(directory, player).toPath())),
            "corrupt bytes preserved across logout and clear");
    }

    private static void malformedRecordsFailClosed(NekoHistoryManager manager, File directory, UUID player, UUID group)
        throws Exception {
        for (int variant = 0; variant < 7; variant++) {
            NBTTagCompound root = record(group, 2, false);
            NBTTagCompound entry = root.getTagList("histories", 10)
                .getCompoundTagAt(0);
            if (variant == 0) root.removeTag("histories");
            if (variant == 1) {
                NBTTagList wrong = new NBTTagList();
                wrong.appendTag(new NBTTagString("bad"));
                root.setTag("histories", wrong);
            }
            if (variant == 2) entry.setString("groupId", "not-a-uuid");
            if (variant == 3) entry.removeTag("history");
            if (variant == 4) entry.getCompoundTag("history")
                .setLong("tradeCount", -1);
            if (variant == 5) entry.getCompoundTag("history")
                .setString("tradeCount", "2");
            if (variant == 6) root.getTagList("histories", 10)
                .appendTag(entry.copy());
            AtomicNbtFile.write(root, file(directory, player));
            byte[] original = Files.readAllBytes(file(directory, player).toPath());
            rejected(() -> manager.getHistory(player, group), "malformed entry fails closed variant " + variant);
            manager.unloadPlayer(player);
            SimpleAssert.that(
                Arrays.equals(original, Files.readAllBytes(file(directory, player).toPath())),
                "malformed original preserved");
        }
    }

    private static void block(File file) throws Exception {
        Files.deleteIfExists(file.toPath());
        Files.createDirectory(file.toPath());
        Files.write(new File(file, "blocker").toPath(), new byte[] { 1 });
    }

    private static void failedLogoutRetainsOwner(NekoHistoryManager manager, File directory, UUID player, UUID group)
        throws Exception {
        NekoTradeHistory history = manager.getHistory(player, group);
        history.recordTrade(0);
        manager.markDirty(player);
        history.recordTrade(0);
        block(file(directory, player));
        manager.unloadPlayer(player);
        SimpleAssert.that(manager.getHistory(player, group) == history, "failed logout retains canonical history");
        history.recordTrade(0); // A late successful completion must remain attached to the same owner.
        delete(file(directory, player));
        manager.saveAll();
        manager.clearAll();
        SimpleAssert.eq(
            3L,
            manager.getHistory(player, group)
                .getTradeCount(),
            "retry retains all completed redemptions");
    }

    private static void failedClearRetainsOwner(NekoHistoryManager manager, File directory, UUID player, UUID group)
        throws Exception {
        NekoTradeHistory history = manager.getHistory(player, group);
        history.recordTrade(0);
        block(file(directory, player));
        rejected(manager::clearAll, "clear refuses to drop unsaved history");
        SimpleAssert.that(manager.getHistory(player, group) == history, "failed clear retains canonical owner");
    }

    private static void worldTransitionCannotLeak(NekoHistoryManager manager, File directory, UUID player, UUID group)
        throws Exception {
        File a = new File(directory, "a"), b = new File(directory, "b");
        bind(manager, a);
        manager.getHistory(player, group)
            .recordTrade(0);
        Files.delete(a.toPath());
        Files.write(a.toPath(), new byte[] { 1 });
        manager.unloadAll();
        rejected(() -> manager.getHistory(player, group), "stopped histories unavailable");
        boolean blocked = false;
        try {
            bind(manager, b);
        } catch (IllegalStateException expected) {
            blocked = true;
        }
        SimpleAssert.that(blocked && !b.exists(), "new world never receives pending old data");
        Files.delete(a.toPath());
        Files.createDirectory(a.toPath());
        bind(manager, a);
        SimpleAssert.eq(
            1L,
            manager.getHistory(player, group)
                .getTradeCount(),
            "old world recovers pending record");
        bind(manager, b);
        SimpleAssert.eq(
            0L,
            manager.getHistory(player, group)
                .getTradeCount(),
            "new world reads only its own history");
    }

    private static void oldConsumedArchiveAndFreshFallback(NekoHistoryManager manager, File directory, UUID player,
        UUID group) throws Exception {
        AtomicNbtFile.write(record(group, 7, true), file(directory, player));
        SimpleAssert.eq(
            0L,
            manager.getHistory(player, group)
                .getTradeCount(),
            "old consumed records remain archival");
        manager.getHistory(player, group)
            .recordTrade(-1);
        manager.markDirty(player);
        manager.clearAll();
        SimpleAssert.eq(
            1L,
            manager.getHistory(player, group)
                .getTradeCount(),
            "new fallback activity is no longer ignored by consumed flag");
        SimpleAssert.that(
            !manager.getHistory(player, group)
                .canTrade(-1, 1, -1),
            "restart cannot re-open fresh one-time redemption");
    }

    private static void migrate(NekoHistoryManager manager, UUID team, UUID player, NekoTeamData data) {
        manager.migrateLegacyHistory(team, Collections.singleton(player), data, () -> {});
    }

    private static void failedJournalDoesNotConsume(NekoHistoryManager manager, File directory, UUID player, UUID group)
        throws Exception {
        manager.getHistory(player, group)
            .recordTrade(0);
        NekoTeamData team = new NekoTeamData();
        UUID teamId = UUID.randomUUID();
        block(new File(directory, "legacy-migrations.dat"));
        rejected(() -> migrate(manager, teamId, player, team), "journal installation failure is surfaced before merge");
        SimpleAssert.eq(
            0L,
            team.getTradeHistory(group)
                .getTradeCount(),
            "no partial team history added before journal commit");
        SimpleAssert.eq(
            1L,
            manager.getHistory(player, group)
                .getTradeCount(),
            "legacy source remains owned");
        delete(new File(directory, "legacy-migrations.dat"));
        migrate(manager, teamId, player, team);
        SimpleAssert.eq(
            1L,
            team.getTradeHistory(group)
                .getTradeCount(),
            "retry imports once");
    }

    private static void allSourcesValidatedBeforeMutation(NekoHistoryManager manager, File directory, UUID player,
        UUID group) throws Exception {
        UUID corrupt = UUID.randomUUID();
        manager.getHistory(player, group)
            .recordTrade(0);
        Files.write(file(directory, corrupt).toPath(), new byte[] { 1 });
        NekoTeamData team = new NekoTeamData();
        rejected(
            () -> manager.migrateLegacyHistory(
                UUID.randomUUID(),
                new LinkedHashSet<>(Arrays.asList(player, corrupt)),
                team,
                () -> {}),
            "late corrupt member rejects entire migration staging");
        SimpleAssert.eq(
            0L,
            team.getTradeHistory(group)
                .getTradeCount(),
            "earlier valid member is not partially merged");
        SimpleAssert.that(!new File(directory, "legacy-migrations.dat").exists(), "no partial journal committed");
    }

    private static void laggingTeamSaveReplaysOnce(NekoHistoryManager manager, File directory, UUID player, UUID group)
        throws Exception {
        manager.getHistory(player, group)
            .recordTrade(0);
        manager.getHistory(player, group)
            .recordTrade(0);
        UUID id = UUID.randomUUID();
        NekoTeamData team = new NekoTeamData();
        NBTTagCompound beforeTeamSave = new NBTTagCompound();
        team.writeToNBT(beforeTeamSave);
        migrate(manager, id, player, team);
        SimpleAssert.eq(
            2L,
            team.getTradeHistory(group)
                .getTradeCount(),
            "initial migration merges legacy counts");
        manager.unloadAll();
        manager = manager(directory);
        NekoTeamData lagging = new NekoTeamData();
        lagging.readFromNBT(beforeTeamSave);
        migrate(manager, id, player, lagging);
        migrate(manager, id, player, lagging);
        SimpleAssert.eq(
            2L,
            lagging.getTradeHistory(group)
                .getTradeCount(),
            "lagging team save replays once, not zero or double");
        NBTTagCompound afterTeamSave = new NBTTagCompound();
        lagging.writeToNBT(afterTeamSave);
        NekoTeamData reloaded = new NekoTeamData();
        reloaded.readFromNBT(afterTeamSave);
        migrate(manager, id, player, reloaded);
        SimpleAssert.eq(
            2L,
            reloaded.getTradeHistory(group)
                .getTradeCount(),
            "persisted receipt suppresses journal replay");
    }

    private static void markerFailureReplaysDurableIntent(NekoHistoryManager manager, File directory, UUID player,
        UUID group) throws Exception {
        manager.getHistory(player, group)
            .recordTrade(0);
        block(file(directory, player));
        UUID id = UUID.randomUUID();
        NekoTeamData team = new NekoTeamData();
        migrate(manager, id, player, team);
        SimpleAssert.eq(
            1L,
            team.getTradeHistory(group)
                .getTradeCount(),
            "durable intent permits completion despite marker failure");
        SimpleAssert
            .that(new File(directory, "legacy-migrations.dat").isFile(), "intent durable before marker attempt");
        delete(file(directory, player));
        // Simulate loss of unsaved in-memory/team data without calling a saving shutdown.
        NekoHistoryManager recovered = manager(directory);
        NekoTeamData lagging = new NekoTeamData();
        migrate(recovered, id, player, lagging);
        SimpleAssert.eq(
            1L,
            lagging.getTradeHistory(group)
                .getTradeCount(),
            "journal alone recovers missing marker and team save");
        SimpleAssert.eq(
            0L,
            recovered.getHistory(player, group)
                .getTradeCount(),
            "original legacy source is not resurrected personally");
    }

    private static void newMemberMigrationIsNotSkipped(NekoHistoryManager manager, File directory, UUID player,
        UUID group) throws Exception {
        NekoTeamData data = new NekoTeamData();
        UUID team = UUID.randomUUID();
        data.setLegacyHistoryMigrated(true);
        manager.getHistory(player, group)
            .recordTrade(0);
        migrate(manager, team, player, data);
        SimpleAssert.eq(
            1L,
            data.getTradeHistory(group)
                .getTradeCount(),
            "team-wide old migrated bit cannot skip new legacy member");
    }

    private static void receiptFollowsMergeAndMovement(NekoHistoryManager manager, File directory, UUID player,
        UUID group) throws Exception {
        manager.getHistory(player, group)
            .recordTrade(0);
        NekoTeamData source = new NekoTeamData();
        migrate(manager, UUID.randomUUID(), player, source);
        NekoTeamData survivor = new NekoTeamData();
        survivor.mergeData(null, null, source);
        migrate(manager, UUID.randomUUID(), player, survivor);
        SimpleAssert.eq(
            1L,
            survivor.getTradeHistory(group)
                .getTradeCount(),
            "merged receipt suppresses replay at survivor");
        NekoTeamData destination = new NekoTeamData();
        destination.transferData(null, null, player, survivor, null);
        migrate(manager, UUID.randomUUID(), player, destination);
        SimpleAssert.eq(
            1L,
            destination.getTradeHistory(group)
                .getTradeCount(),
            "member-movement receipt suppresses duplicated migration");
    }

    private static void resetKeepsReplayReceipt(NekoHistoryManager manager, File directory, UUID player, UUID group)
        throws Exception {
        manager.getHistory(player, group)
            .recordTrade(0);
        NekoTeamData data = new NekoTeamData();
        UUID team = UUID.randomUUID();
        migrate(manager, team, player, data);
        data.resetAllTradeHistories();
        NBTTagCompound tag = new NBTTagCompound();
        data.writeToNBT(tag);
        data = new NekoTeamData();
        data.readFromNBT(tag);
        migrate(manager, team, player, data);
        SimpleAssert.eq(
            0L,
            data.getTradeHistory(group)
                .getTradeCount(),
            "durable receipt prevents replay from undoing admin reset");
    }

    private static void corruptJournalFencesNewWorld(NekoHistoryManager manager, File directory, UUID player,
        UUID group) throws Exception {
        File next = new File(directory, "next");
        Files.createDirectory(next.toPath());
        File journal = new File(next, "legacy-migrations.dat");
        byte[] original = { 1, 2, 3 };
        Files.write(journal.toPath(), original);
        boolean blocked = false;
        try {
            bind(manager, next);
        } catch (IllegalStateException expected) {
            blocked = true;
        }
        SimpleAssert.that(blocked, "corrupt journal rejects new-world bind");
        rejected(() -> manager.getHistory(player, group), "cannot continue with empty migration state");
        SimpleAssert.that(Arrays.equals(original, Files.readAllBytes(journal.toPath())), "corrupt journal preserved");
    }

    private static void liveReferenceFollowsMigration(NekoHistoryManager manager, File directory, UUID player,
        UUID group) throws Exception {
        NekoTradeHistory captured = manager.getHistory(player, group);
        captured.recordTrade(0);
        NekoTeamData team = new NekoTeamData();
        migrate(manager, UUID.randomUUID(), player, team);
        captured.recordTrade(0);
        SimpleAssert.eq(
            2L,
            team.getTradeHistory(group)
                .getTradeCount(),
            "late captured completion follows migrated owner");
        NekoTeamData survivor = new NekoTeamData();
        survivor.mergeData(null, null, team);
        captured.recordTrade(0);
        SimpleAssert.eq(
            3L,
            survivor.getTradeHistory(group)
                .getTradeCount(),
            "captured legacy reference follows team merge too");
        SimpleAssert.eq(
            0L,
            captured.writeToNBT()
                .getLong("tradeCount"),
            "consumed source serializes no duplicated history");
    }

    private static void saveAndCompletionLockOrder(NekoHistoryManager manager, File directory, UUID player, UUID group)
        throws Exception {
        NekoTradeHistory history = manager.getHistory(player, group);
        java.util.concurrent.CountDownLatch held = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch finish = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(2);
        java.util.concurrent.atomic.AtomicReference<Throwable> failure = new java.util.concurrent.atomic.AtomicReference<>();
        Thread completion = new Thread(() -> {
            try {
                synchronized (history) {
                    held.countDown();
                    if (!finish.await(5, java.util.concurrent.TimeUnit.SECONDS))
                        throw new AssertionError("completion release");
                    history.recordTrade(0);
                }
                // Same ordering as executeTrade: persistence is deliberately outside its history monitor.
                manager.markDirty(player);
            } catch (Throwable error) {
                failure.set(error);
            } finally {
                done.countDown();
            }
        }, "history-completion");
        Thread saver = new Thread(() -> {
            try {
                manager.saveAll();
            } catch (Throwable error) {
                failure.set(error);
            } finally {
                done.countDown();
            }
        }, "history-save");
        completion.setDaemon(true);
        saver.setDaemon(true);
        completion.start();
        SimpleAssert.that(held.await(5, java.util.concurrent.TimeUnit.SECONDS), "completion owns history");
        saver.start();
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
        while (saver.getState() != Thread.State.BLOCKED && System.nanoTime() < deadline) Thread.yield();
        SimpleAssert
            .that(saver.getState() == Thread.State.BLOCKED, "saver holds manager while waiting for completion history");
        finish.countDown();
        SimpleAssert.that(
            done.await(5, java.util.concurrent.TimeUnit.SECONDS),
            "save and completion never invert manager/history monitors");
        SimpleAssert.eq(null, failure.get(), "both concurrent operations complete normally");
        manager.clearAll();
        SimpleAssert.eq(
            1L,
            manager.getHistory(player, group)
                .getTradeCount(),
            "concurrent completion persists exactly once");
    }

    private static void repeatedMovementNeverReopensOnce() {
        NekoTeamData a = new NekoTeamData(), b = new NekoTeamData();
        UUID group = UUID.randomUUID(), player = UUID.randomUUID();
        a.getTradeHistory(group)
            .recordTrade(-1);
        for (int i = 0; i < 200; i++) {
            NekoTeamData from = i % 2 == 0 ? a : b, to = i % 2 == 0 ? b : a;
            to.transferData(null, null, player, from, null);
            SimpleAssert.that(
                !to.getTradeHistory(group)
                    .canTrade(-1, 1, -1),
                "actual member-transfer accumulation cannot reopen one-time gate at movement " + i);
        }
        SimpleAssert.eq(
            Long.MAX_VALUE,
            a.getTradeHistory(group)
                .getTradeCount(),
            "repeated team copies saturate");
        SimpleAssert.eq(
            Long.MAX_VALUE,
            b.getTradeHistory(group)
                .getTradeCount(),
            "both team records remain denied");
    }

    private static void historyCountersNeverWrap() {
        NekoTradeHistory history = new NekoTradeHistory();
        history.recordTrade(0);
        NBTTagCompound tag = history.writeToNBT();
        tag.setLong("tradeCount", Long.MAX_VALUE);
        tag.setLong("cooldownTradeCount", Long.MAX_VALUE);
        history.loadFromNBT(tag);
        history.recordTrade(0);
        NekoTradeHistory more = new NekoTradeHistory();
        more.recordTrade(0);
        history.mergeFrom(more);
        SimpleAssert
            .eq(Long.MAX_VALUE, history.getTradeCount(), "lifetime counter saturates instead of wrapping negative");
        SimpleAssert.eq(Long.MAX_VALUE, history.getCooldownTradeCount(), "cooldown counter saturates");
        SimpleAssert.that(!history.canTrade(-1, 1, -1), "overflow cannot re-open one-time reward");
    }

    private static void teamLoadIsTransactional() {
        NekoTeamData data = new NekoTeamData();
        UUID group = UUID.randomUUID();
        data.getWallet()
            .addCount("neko", 19);
        data.getTradeHistory(group)
            .recordTrade(0);
        NBTTagCompound invalid = new NBTTagCompound();
        data.writeToNBT(invalid);
        invalid.getCompoundTag("wallet")
            .setInteger("neko", 0);
        invalid.getTagList("tradeHistory", 10)
            .getCompoundTagAt(0)
            .setString("groupId", "broken");
        rejected(() -> data.readFromNBT(invalid), "malformed team history rejected");
        SimpleAssert.eq(
            19,
            data.getWallet()
                .getCount("neko"),
            "failed history decode never partly replaces team wallet");
        SimpleAssert.eq(
            1L,
            data.getTradeHistory(group)
                .getTradeCount(),
            "previous team history retained transactionally");
    }
}

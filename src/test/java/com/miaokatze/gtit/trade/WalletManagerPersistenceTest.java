package com.miaokatze.gtit.trade;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.InvocationTargetException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;

import com.miaokatze.gtit.testutil.SimpleAssert;
import com.miaokatze.gtit.testutil.TestRunner;

/** Real manager/file/NBT tests; the parent suite has initialized Forge and disabled team routing. */
public final class WalletManagerPersistenceTest {

    private static final String COIN = "neko";

    private WalletManagerPersistenceTest() {}

    public static void run() {
        Map<String, Runnable> cases = new LinkedHashMap<>();
        cases.put("logoutRetainsLateRefundOwner", () -> isolated(WalletManagerPersistenceTest::logoutRetainsLateRefundOwner));
        cases.put("failedSaveIsRetainedForRetry", () -> isolated(WalletManagerPersistenceTest::failedSaveIsRetainedForRetry));
        cases.put("corruptFileCannotBeOverwritten", () -> isolated(WalletManagerPersistenceTest::corruptFileCannotBeOverwritten));
        cases.put("concurrentLookupHasOneLedger", () -> isolated(WalletManagerPersistenceTest::concurrentLookupHasOneLedger));
        cases.put("newerMutationKeepsDirtyMark", () -> isolated(WalletManagerPersistenceTest::newerMutationKeepsDirtyMark));
        cases.put("atomicWritePreservesOldFileOnFailure", () -> isolated(WalletManagerPersistenceTest::atomicWritePreservesOldFileOnFailure));
        cases.put("invalidBalanceTypePreservesFile", () -> isolated(WalletManagerPersistenceTest::invalidBalanceTypePreservesFile));
        cases.put("failedOldWorldSaveCannotContaminateNewWorld", () -> isolated(WalletManagerPersistenceTest::failedOldWorldSaveCannotContaminateNewWorld));
        TestRunner.run(WalletManagerPersistenceTest.class, cases);
    }

    public static void main(String[] args) {
        run();
    }

    private interface Case {
        void run(NekoWalletManager manager, UUID id, File directory) throws Exception;
    }

    @SuppressWarnings("unchecked")
    private static void isolated(Case test) {
        NekoWalletManager manager = NekoWalletManager.INSTANCE;
        File directory = null;
        UUID id = UUID.randomUUID();
        Object previous = null;
        boolean previousReady = false;
        Map<UUID, NekoWallet> previousWallets = new HashMap<>();
        Set<UUID> previousDirty = new HashSet<>();
        Set<String> previousBalanceKeys = new HashSet<>();
        try {
            Field path = field("saveDir");
            previous = path.get(manager);
            previousWallets.putAll((Map<UUID, NekoWallet>) field("personalWallets").get(manager));
            previousDirty.addAll((Set<UUID>) field("dirtyPersonalWallets").get(manager));
            previousBalanceKeys.addAll((Set<String>) field("dirtyBalanceKeys").get(manager));
            ((Map<UUID, NekoWallet>) field("personalWallets").get(manager)).clear();
            ((Set<UUID>) field("dirtyPersonalWallets").get(manager)).clear();
            ((Set<String>) field("dirtyBalanceKeys").get(manager)).clear();
            previousReady = field("acceptingWallets").getBoolean(manager);
            field("acceptingWallets").setBoolean(manager, true);
            directory = Files.createTempDirectory("gtit-wallet-regression-").toFile();
            path.set(manager, directory);
            test.run(manager, id, directory);
        } catch (Exception error) {
            throw new AssertionError(error);
        } finally {
            try {
                field("saveDir").set(manager, previous);
                field("acceptingWallets").setBoolean(manager, previousReady);
                Map<UUID, NekoWallet> cache = (Map<UUID, NekoWallet>) field("personalWallets").get(manager);
                cache.clear();
                cache.putAll(previousWallets);
                Set<UUID> dirty = (Set<UUID>) field("dirtyPersonalWallets").get(manager);
                dirty.clear();
                dirty.addAll(previousDirty);
                Set<String> keys = (Set<String>) field("dirtyBalanceKeys").get(manager);
                keys.clear();
                keys.addAll(previousBalanceKeys);
                if (directory != null) deleteTree(directory);
            } catch (Exception error) {
                throw new AssertionError("wallet regression cleanup", error);
            }
        }
    }

    private static void deleteTree(File file) throws Exception {
        File[] children = file.listFiles();
        if (children != null) for (File child : children) deleteTree(child);
        Files.deleteIfExists(file.toPath());
    }

    private static Field field(String name) throws Exception {
        Field field = NekoWalletManager.class.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static NekoWallet disk(UUID id, File directory) throws Exception {
        NekoWallet result = new NekoWallet();
        result.readFromNBT(CompressedStreamTools.read(new File(directory, id + ".dat")).getCompoundTag("wallet"));
        return result;
    }

    private static void logoutRetainsLateRefundOwner(NekoWalletManager manager, UUID id, File directory) throws Exception {
        NekoWallet wallet = manager.getWallet(id);
        wallet.addCount(COIN, Integer.MAX_VALUE);
        wallet.tryDeduct(COIN, 7);
        manager.unloadWallet(id);
        SimpleAssert.that(wallet == manager.getWallet(id), "logout never creates a second canonical ledger");
        wallet.addCount(COIN, 19);
        manager.saveDirtyWallets();
        SimpleAssert.eq(BigInteger.valueOf(Integer.MAX_VALUE).add(BigInteger.valueOf(12)), disk(id, directory).getExactCount(COIN),
            "late refund and overflow persist after logout");
    }

    private static void failedSaveIsRetainedForRetry(NekoWalletManager manager, UUID id, File directory) throws Exception {
        NekoWallet wallet = manager.getWallet(id);
        wallet.addCount(COIN, 83);
        File notDirectory = new File(directory, "not-a-directory");
        Files.write(notDirectory.toPath(), new byte[] { 1 });
        field("saveDir").set(manager, notDirectory);
        manager.unloadWallet(id);
        SimpleAssert.that(wallet == manager.getWallet(id), "failed save cannot evict owned money");
        field("saveDir").set(manager, directory);
        SimpleAssert.eq(1, manager.saveDirtyWallets(), "failed save remains dirty for retry");
        SimpleAssert.eq(83, disk(id, directory).getCount(COIN), "retry writes original ledger");
    }

    private static void corruptFileCannotBeOverwritten(NekoWalletManager manager, UUID id, File directory) throws Exception {
        File file = new File(directory, id + ".dat");
        byte[] original = { 31, 2, 11, 99, 0, 7 };
        Files.write(file.toPath(), original);
        boolean rejected = false;
        try {
            manager.getWallet(id);
        } catch (IllegalStateException expected) {
            rejected = true;
        }
        SimpleAssert.that(rejected, "existing corrupt file fails closed");
        manager.unloadWallet(id);
        SimpleAssert.that(Arrays.equals(original, Files.readAllBytes(file.toPath())), "logout preserves original corrupt bytes");
    }

    private static void invalidBalanceTypePreservesFile(NekoWalletManager manager, UUID id, File directory)
        throws Exception {
        File file = new File(directory, id + ".dat");
        NBTTagCompound root = new NBTTagCompound(), wallet = new NBTTagCompound();
        wallet.setString(COIN, "123"); // Valid outer NBT, invalid balance type: must not silently become zero.
        root.setTag("wallet", wallet);
        com.miaokatze.gtit.util.AtomicNbtFile.write(root, file);
        byte[] original = Files.readAllBytes(file.toPath());
        boolean rejected = false;
        try { manager.getWallet(id); } catch (IllegalStateException expected) { rejected = true; }
        SimpleAssert.that(rejected, "invalid legacy balance type fails closed");
        manager.unloadWallet(id);
        SimpleAssert.that(Arrays.equals(original, Files.readAllBytes(file.toPath())), "invalid balance file bytes unchanged");
    }

    private static void bind(NekoWalletManager manager, File directory) throws Exception {
        Method method = NekoWalletManager.class.getDeclaredMethod("bindSaveDirectory", File.class);
        method.setAccessible(true);
        try { method.invoke(manager, directory); }
        catch (InvocationTargetException failure) {
            if (failure.getCause() instanceof RuntimeException) throw (RuntimeException) failure.getCause();
            throw failure;
        }
    }

    private static void failedOldWorldSaveCannotContaminateNewWorld(NekoWalletManager manager, UUID id, File directory)
        throws Exception {
        File a = new File(directory, "world-a"), b = new File(directory, "world-b");
        bind(manager, a);
        NekoWallet original = manager.getWallet(id);
        original.addCount(COIN, 29);
        Files.delete(a.toPath());
        Files.write(a.toPath(), new byte[] { 1 }); // Deterministic old-directory write failure, no permissions tricks.
        manager.unloadAll();
        SimpleAssert.that(!field("acceptingWallets").getBoolean(manager), "world stop disables further wallet lookups");
        boolean blocked = false;
        try { bind(manager, b); } catch (IllegalStateException expected) { blocked = true; }
        SimpleAssert.that(blocked, "new world cannot bind while previous ledger remains unsaved");
        blocked = false;
        try { manager.getWallet(id); } catch (IllegalStateException expected) { blocked = true; }
        SimpleAssert.that(blocked, "continuing server cannot serve old balances as new-world data");
        manager.saveWallet(id);
        SimpleAssert.that(!b.exists(), "blocked transition never creates/writes the new wallet directory");
        Files.delete(a.toPath());
        Files.createDirectory(a.toPath());
        bind(manager, a); // Recover the old bound data before allowing a new session.
        SimpleAssert.eq(29, manager.getWallet(id).getCount(COIN), "old world recovered without losing pending funds");
        bind(manager, b);
        SimpleAssert.eq(0, manager.getWallet(id).getCount(COIN), "new world starts from its own file, not old cache");
        manager.getWallet(id).addCount(COIN, 11);
        manager.saveWallet(id);
        SimpleAssert.eq(29, disk(id, a).getCount(COIN), "world A remains unchanged by B writes");
        SimpleAssert.eq(11, disk(id, b).getCount(COIN), "world B owns only its new funds");
    }

    private static void atomicWritePreservesOldFileOnFailure(NekoWalletManager manager, UUID id, File directory)
        throws Exception {
        File file = new File(directory, id + ".dat");
        NBTTagCompound original = new NBTTagCompound();
        original.setInteger("owned", 61);
        com.miaokatze.gtit.util.AtomicNbtFile.write(original, file);
        byte[] saved = Files.readAllBytes(file.toPath());
        NBTTagCompound invalid = new NBTTagCompound();
        invalid.setString("too-long-for-NBT-UTF", new String(new char[70000]).replace('\0', 'x'));
        boolean failed = false;
        try {
            com.miaokatze.gtit.util.AtomicNbtFile.write(invalid, file);
        } catch (java.io.IOException expected) {
            failed = true;
        }
        SimpleAssert.that(failed, "serialization failure is reported");
        SimpleAssert.that(Arrays.equals(saved, Files.readAllBytes(file.toPath())), "failed replacement preserves old bytes");
        SimpleAssert.eq(1, directory.listFiles().length, "failed write removes only its own temporary file");
    }

    private static void concurrentLookupHasOneLedger(NekoWalletManager manager, UUID id, File directory) throws Exception {
        Set<NekoWallet> identities = Collections.synchronizedSet(Collections.newSetFromMap(new IdentityHashMap<>()));
        CountDownLatch start = new CountDownLatch(1), done = new CountDownLatch(8);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        for (int i = 0; i < 8; i++) {
            Thread worker = new Thread(() -> {
                try {
                    if (!start.await(5, TimeUnit.SECONDS)) throw new AssertionError("lookup start timeout");
                    NekoWallet wallet = manager.getWallet(id);
                    identities.add(wallet);
                    wallet.addCount(COIN, 11);
                } catch (Throwable error) {
                    failure.compareAndSet(null, error);
                } finally {
                    done.countDown();
                }
            }, "Server thread");
            worker.setDaemon(true);
            worker.start();
        }
        start.countDown();
        SimpleAssert.that(done.await(10, TimeUnit.SECONDS), "lookup workers finish");
        SimpleAssert.eq(null, failure.get(), "lookup workers succeed");
        SimpleAssert.eq(1, identities.size(), "only one ledger is published");
        SimpleAssert.eq(88, manager.getWallet(id).getCount(COIN), "all concurrent credits retained");
    }

    @SuppressWarnings("unchecked")
    private static void newerMutationKeepsDirtyMark(NekoWalletManager manager, UUID id, File directory) throws Exception {
        CountDownLatch snapshot = new CountDownLatch(1), release = new CountDownLatch(1), creditStarted = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        NekoWallet wallet = new NekoWallet() {
            private boolean first = true;

            @Override
            public synchronized NBTTagCompound writeToNBT() {
                NBTTagCompound value = super.writeToNBT();
                if (first) {
                    first = false;
                    snapshot.countDown();
                    try {
                        if (!release.await(5, TimeUnit.SECONDS)) throw new AssertionError("save release timeout");
                    } catch (InterruptedException error) {
                        Thread.currentThread().interrupt();
                        throw new AssertionError(error);
                    }
                }
                return value;
            }
        };
        wallet.setPlayerId(id);
        wallet.addCount(COIN, 5);
        ((Map<UUID, NekoWallet>) field("personalWallets").get(manager)).put(id, wallet);
        Thread saver = new Thread(() -> {
            try { manager.saveWallet(id); } catch (Throwable error) { failure.set(error); }
        }, "Server thread");
        Thread creditor = new Thread(() -> {
            creditStarted.countDown();
            try { wallet.addCount(COIN, 7); } catch (Throwable error) { failure.set(error); }
        }, "Server thread");
        saver.setDaemon(true);
        creditor.setDaemon(true);
        saver.start();
        try {
            SimpleAssert.that(snapshot.await(5, TimeUnit.SECONDS), "save reached captured snapshot");
            creditor.start();
            SimpleAssert.that(creditStarted.await(5, TimeUnit.SECONDS), "concurrent mutation attempted");
        } finally {
            release.countDown();
        }
        saver.join(5000);
        creditor.join(5000);
        SimpleAssert.that(!saver.isAlive() && !creditor.isAlive(), "save and mutation finish");
        SimpleAssert.eq(null, failure.get(), "save and mutation succeed");
        SimpleAssert.eq(1, manager.saveDirtyWallets(), "post-snapshot credit remains dirty");
        SimpleAssert.eq(12, disk(id, directory).getCount(COIN), "next save persists post-snapshot credit");
    }
}

package com.miaokatze.gtit.gui.vm;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import com.miaokatze.gtit.testutil.SimpleAssert;
import com.miaokatze.gtit.testutil.TestRunner;
import com.miaokatze.gtit.trade.NekoWallet;

/** Direct wallet API contracts, called after the parent suite initializes real Forge. */
public final class WalletCreditCapacityTest {

    private static final String COIN = "neko";

    private WalletCreditCapacityTest() {}

    public static void run() {
        Map<String, Runnable> cases = new LinkedHashMap<>();
        cases.put("saturationPreservesRemainder", WalletCreditCapacityTest::saturationPreservesRemainder);
        cases.put("nonpositiveCreditsAreNoOps", WalletCreditCapacityTest::nonpositiveCreditsAreNoOps);
        cases.put("partialExtractionCreditsExactly", WalletCreditCapacityTest::partialExtractionCreditsExactly);
        cases.put("concurrentCreditsRespectCapacity", WalletCreditCapacityTest::concurrentCreditsRespectCapacity);
        TestRunner.run(WalletCreditCapacityTest.class, cases);
    }

    private static void saturationPreservesRemainder() {
        NekoWallet wallet = new NekoWallet();
        wallet.addCount(COIN, Integer.MAX_VALUE - 10);
        SimpleAssert.eq(10, wallet.getCreditCapacity(COIN), "remaining wallet capacity");
        int accepted = wallet.creditUpToCapacity(COIN, 64);
        SimpleAssert.eq(10, accepted, "only available capacity accepted");
        SimpleAssert.eq(54, 64 - accepted, "physical remainder owed to caller");
        SimpleAssert.eq(Integer.MAX_VALUE, wallet.getCount(COIN), "no signed overflow");
        SimpleAssert.eq(0, wallet.creditUpToCapacity(COIN, 64), "full wallet accepts nothing");
        SimpleAssert.eq(0, wallet.getCreditCapacity(COIN), "full wallet has zero capacity");
    }

    private static void nonpositiveCreditsAreNoOps() {
        NekoWallet wallet = new NekoWallet();
        wallet.addCount(COIN, 17);
        SimpleAssert.eq(0, wallet.creditUpToCapacity(COIN, -1), "negative request rejected");
        SimpleAssert.eq(0, wallet.creditUpToCapacity(COIN, 0), "zero request rejected");
        SimpleAssert.eq(17, wallet.getCount(COIN), "invalid requests do not alter balance");
    }

    private static void partialExtractionCreditsExactly() {
        NekoWallet wallet = new NekoWallet();
        wallet.addCount(COIN, 2_000_000_000);
        int requested = Math.min(200_000_000, wallet.getCreditCapacity(COIN));
        SimpleAssert.eq(147_483_647, requested, "extraction request bounded before credit");
        int extracted = requested - 123;
        SimpleAssert.eq(extracted, wallet.creditUpToCapacity(COIN, extracted), "only extracted quantity credited");
        SimpleAssert.eq(Integer.MAX_VALUE - 123, wallet.getCount(COIN), "unextracted quantity not invented");
        SimpleAssert.eq(123, wallet.getCreditCapacity(COIN), "partial extraction leaves headroom");
    }

    private static void concurrentCreditsRespectCapacity() {
        NekoWallet wallet = new NekoWallet();
        wallet.setTeamId(UUID.randomUUID());
        wallet.addCount(COIN, Integer.MAX_VALUE - 1000);
        AtomicLong total = new AtomicLong();
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(8);
        List<Throwable> errors = Collections.synchronizedList(new ArrayList<>());
        Thread[] workers = new Thread[8];
        try {
            for (int i = 0; i < workers.length; i++) {
                workers[i] = new Thread(() -> {
                    try {
                        if (!start.await(5, TimeUnit.SECONDS)) throw new AssertionError("start barrier timed out");
                        for (int j = 0; j < 100; j++) total.addAndGet(wallet.creditUpToCapacity(COIN, 7));
                    } catch (Throwable failure) {
                        errors.add(failure);
                    } finally {
                        done.countDown();
                    }
                }, "wallet-credit-regression-" + i);
                workers[i].setDaemon(true);
                workers[i].start();
            }
            start.countDown();
            SimpleAssert.that(done.await(10, TimeUnit.SECONDS), "concurrent credits finish within deadline");
            SimpleAssert.that(errors.isEmpty(), "worker failures: " + errors);
            SimpleAssert.eq(1000L, total.get(), "total accepted equals original headroom");
            SimpleAssert.eq(Integer.MAX_VALUE, wallet.getCount(COIN), "shared wallet remains bounded");
        } catch (InterruptedException interrupted) {
            Thread.currentThread()
                .interrupt();
            throw new AssertionError("concurrent credit test interrupted", interrupted);
        } finally {
            start.countDown();
            for (Thread worker : workers) {
                if (worker != null && worker.isAlive()) worker.interrupt();
            }
        }
    }
}

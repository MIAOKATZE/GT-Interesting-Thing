package com.miaokatze.gtit.trade;

import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import net.minecraft.nbt.NBTTagCompound;

import com.miaokatze.gtit.testutil.SimpleAssert;
import com.miaokatze.gtit.testutil.TestRunner;
import com.miaokatze.gtit.trade.v2.NekoTradeHistory;

/** Exact ledger tests; called by VendingConcurrencyTest after its Forge bootstrap. */
public final class WalletConservationTest {

    private static final String COIN = "neko";
    private static final BigInteger MAX = BigInteger.valueOf(Integer.MAX_VALUE);

    private WalletConservationTest() {}

    public static void run() {
        Map<String, Runnable> cases = new LinkedHashMap<>();
        cases.put("committedRewardAndInterleavedRefund", WalletConservationTest::committedRewardAndInterleavedRefund);
        cases.put("exactNbtRoundTripAndLegacy", WalletConservationTest::exactNbtRoundTripAndLegacy);
        cases.put("legacyMetadataNamesRemainCurrency", WalletConservationTest::legacyMetadataNamesRemainCurrency);
        cases.put("malformedNbtDoesNotReplaceLedger", WalletConservationTest::malformedNbtDoesNotReplaceLedger);
        cases.put("consumedTeamForwardsLateRefund", WalletConservationTest::consumedTeamForwardsLateRefund);
        cases.put("opposingMergesAndCreditsConserve", WalletConservationTest::opposingMergesAndCreditsConserve);
        cases.put("teamMergeAndTransferSemantics", WalletConservationTest::teamMergeAndTransferSemantics);
        cases.put("teamHistoryFollowsConsumedOwner", WalletConservationTest::teamHistoryFollowsConsumedOwner);
        cases.put("teamHistoryOpposingMergesConserve", WalletConservationTest::teamHistoryOpposingMergesConserve);
        cases.put("readOnlySnapshotsDoNotMutate", WalletConservationTest::readOnlySnapshotsDoNotMutate);
        cases.put(
            "staleAliasImportAndWithdrawalInterleavings",
            WalletConservationTest::staleAliasImportAndWithdrawalInterleavings);
        TestRunner.run(WalletConservationTest.class, cases);
    }

    public static void main(String[] args) {
        run();
    }

    private static void committedRewardAndInterleavedRefund() {
        NekoWallet wallet = new NekoWallet();
        wallet.addCount(COIN, Integer.MAX_VALUE);
        SimpleAssert.that(wallet.tryDeduct(COIN, 40), "reserve purchase debit");
        wallet.addCount(COIN, 60); // Another team member earns a committed reward before rollback.
        wallet.addCount(COIN, 40);
        SimpleAssert.eq(MAX.add(BigInteger.valueOf(60)), wallet.getExactCount(COIN), "refund and reward both retained");
        SimpleAssert.eq(Integer.MAX_VALUE, wallet.getCount(COIN), "old protocol view remains a nonnegative int");
        SimpleAssert.eq(0, wallet.getCreditCapacity(COIN), "overflow funds do not reopen physical import capacity");
        SimpleAssert.that(wallet.tryDeduct(COIN, Integer.MAX_VALUE), "overflow remains spendable");
        SimpleAssert.eq(60, wallet.getCount(COIN), "all excess becomes visible after spending");
        wallet.addCount(COIN, Integer.MIN_VALUE);
        SimpleAssert.eq(BigInteger.ZERO, wallet.getExactCount(COIN), "negative legacy adjustment cannot wrap positive");
    }

    private static void exactNbtRoundTripAndLegacy() {
        NBTTagCompound old = new NBTTagCompound();
        old.setInteger(COIN, 41);
        NekoWallet wallet = new NekoWallet();
        wallet.readFromNBT(old);
        SimpleAssert.eq(41, wallet.getCount(COIN), "legacy int save loads unchanged");
        SimpleAssert.that(
            !wallet.writeToNBT()
                .hasKey("__gtit_exact_balances_v1"),
            "ordinary saves retain legacy shape");
        NBTTagCompound huge = new NBTTagCompound();
        BigInteger exact = BigInteger.ONE.shiftLeft(90)
            .add(BigInteger.valueOf(37));
        huge.setInteger(COIN, Integer.MAX_VALUE);
        huge.setInteger("__gtit_wallet_version", 1);
        NBTTagCompound overflow = new NBTTagCompound();
        overflow.setString(COIN, exact.toString());
        huge.setTag("__gtit_exact_balances_v1", overflow);
        wallet.readFromNBT(huge);
        NekoWallet reload = new NekoWallet();
        reload.readFromNBT(wallet.writeToNBT());
        SimpleAssert.eq(exact, reload.getExactCount(COIN), "beyond-long exact amount survives NBT roundtrip");
        SimpleAssert.eq(
            exact,
            reload.copy()
                .getExactCount(COIN),
            "copy never truncates to GUI projection");
        SimpleAssert.that(reload.tryDeduct(COIN, Integer.MAX_VALUE), "int payment debits arbitrarily large ledger");
        SimpleAssert.eq(exact.subtract(MAX), reload.getExactCount(COIN), "exact amount remains after payment");
        reload.addCount("future-coin", Integer.MAX_VALUE);
        reload.addCount("future-coin", 55);
        NekoWallet future = new NekoWallet();
        future.readFromNBT(reload.writeToNBT());
        SimpleAssert.eq(
            MAX.add(BigInteger.valueOf(55)),
            future.getExactCount("future-coin"),
            "temporarily unregistered denominations retain their exact funds");
        future.addCount("__gtit_wallet_version", 71);
        future.addCount("__gtit_exact_balances_v1", 53);
        NekoWallet reserved = new NekoWallet();
        reserved.readFromNBT(future.writeToNBT());
        SimpleAssert.eq(71, reserved.getCount("__gtit_wallet_version"), "metadata-like currency keys cannot collide");
        SimpleAssert.eq(53, reserved.getCount("__gtit_exact_balances_v1"), "exact compound name can be owned data");
    }

    private static void malformedNbtDoesNotReplaceLedger() {
        NekoWallet wallet = new NekoWallet();
        wallet.addCount(COIN, 71);
        NBTTagCompound bad = new NBTTagCompound();
        bad.setInteger("__gtit_wallet_version", 1);
        NBTTagCompound exact = new NBTTagCompound();
        exact.setString(COIN, "not-money");
        bad.setTag("__gtit_exact_balances_v1", exact);
        boolean rejected = false;
        try {
            wallet.readFromNBT(bad);
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }
        SimpleAssert.that(rejected, "corrupt exact balance rejected");
        SimpleAssert.eq(71, wallet.getCount(COIN), "failed load does not wipe existing ledger");
    }

    private static void legacyMetadataNamesRemainCurrency() {
        for (int versionBalance : new int[] { 1, 71 }) {
            NBTTagCompound legacy = new NBTTagCompound();
            legacy.setInteger(COIN, 41);
            legacy.setInteger("__gtit_wallet_version", versionBalance);
            legacy.setInteger("__gtit_exact_balances_v1", 53);
            NekoWallet wallet = new NekoWallet();
            wallet.readFromNBT(legacy);
            SimpleAssert.eq(
                versionBalance,
                wallet.getCount("__gtit_wallet_version"),
                "legacy numeric version key is money, not a metadata marker");
            SimpleAssert.eq(
                53,
                wallet.getCount("__gtit_exact_balances_v1"),
                "legacy numeric exact key is money, not a compound");
            NekoWallet reloaded = new NekoWallet();
            reloaded.readFromNBT(wallet.writeToNBT());
            SimpleAssert.eq(
                versionBalance,
                reloaded.getCount("__gtit_wallet_version"),
                "migrated metadata-like currency survives the exact writer");
            SimpleAssert.eq(53, reloaded.getCount("__gtit_exact_balances_v1"), "both legacy names survive migration");
            SimpleAssert.eq(41, reloaded.getCount(COIN), "ordinary legacy balance remains unchanged");
        }
        for (int version : new int[] { 0, 2 }) {
            NBTTagCompound invalid = new NBTTagCompound();
            invalid.setInteger("__gtit_wallet_version", version);
            invalid.setTag("__gtit_exact_balances_v1", new NBTTagCompound());
            NekoWallet wallet = new NekoWallet();
            wallet.addCount(COIN, 7);
            boolean rejected = false;
            try {
                wallet.readFromNBT(invalid);
            } catch (IllegalArgumentException expected) {
                rejected = true;
            }
            SimpleAssert.that(rejected, "compound exact metadata still requires the supported version");
            SimpleAssert.eq(7, wallet.getCount(COIN), "unsupported exact data cannot replace owned funds");
        }
    }

    private static void consumedTeamForwardsLateRefund() {
        NekoWallet source = new NekoWallet(), survivor = new NekoWallet(), next = new NekoWallet();
        source.addCount(COIN, Integer.MAX_VALUE);
        survivor.addCount(COIN, Integer.MAX_VALUE);
        SimpleAssert.that(source.tryDeduct(COIN, 25), "old in-flight purchase reserved before merge");
        source.transferAllTo(survivor);
        source.addCount(COIN, 25); // deterministic refund after the consumed team has disappeared
        SimpleAssert
            .eq(MAX.multiply(BigInteger.valueOf(2)), survivor.getExactCount(COIN), "late refund follows survivor");
        SimpleAssert.eq(
            0,
            source.writeToNBT()
                .getInteger(COIN),
            "consumed source persistence cannot clone funds");
        source.transferAllTo(survivor);
        source.transferAllTo(next);
        SimpleAssert.eq(BigInteger.ZERO, next.getExactCount(COIN), "stale source cannot move survivor money again");
        survivor.transferAllTo(next);
        source.addCount(COIN, 3);
        SimpleAssert.eq(
            MAX.multiply(BigInteger.valueOf(2))
                .add(BigInteger.valueOf(3)),
            next.getExactCount(COIN),
            "oldest reference follows a chain of team merges");
        next.transferAllTo(next);
        SimpleAssert.eq(next.getExactCount(COIN), source.getExactCount(COIN), "live reads resolve to survivor");
    }

    private static void opposingMergesAndCreditsConserve() {
        NekoWallet left = new NekoWallet(), right = new NekoWallet();
        left.addCount(COIN, Integer.MAX_VALUE);
        right.addCount(COIN, Integer.MAX_VALUE);
        CountDownLatch start = new CountDownLatch(1), done = new CountDownLatch(4);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Runnable[] actions = { () -> left.transferAllTo(right), () -> right.transferAllTo(left),
            () -> { for (int i = 0; i < 1000; i++) left.addCount(COIN, 7); },
            () -> { for (int i = 0; i < 1000; i++) right.addCount(COIN, 11); } };
        for (Runnable action : actions) {
            Thread worker = new Thread(() -> {
                try {
                    if (!start.await(5, TimeUnit.SECONDS)) throw new AssertionError("start barrier timed out");
                    action.run();
                } catch (Throwable problem) {
                    failure.compareAndSet(null, problem);
                } finally {
                    done.countDown();
                }
            }, "wallet-conservation-regression");
            worker.setDaemon(true);
            worker.start();
        }
        start.countDown();
        try {
            SimpleAssert.that(done.await(10, TimeUnit.SECONDS), "opposing merges do not deadlock");
        } catch (InterruptedException interrupted) {
            Thread.currentThread()
                .interrupt();
            throw new AssertionError(interrupted);
        }
        SimpleAssert.eq(null, failure.get(), "all workers complete without failures");
        BigInteger expected = MAX.multiply(BigInteger.valueOf(2))
            .add(BigInteger.valueOf(18000));
        SimpleAssert.eq(expected, left.getExactCount(COIN), "exact total after racing credits and merge");
        SimpleAssert.eq(expected, right.getExactCount(COIN), "both old references resolve same owner");
        NekoWallet a = new NekoWallet(), b = new NekoWallet();
        a.readFromNBT(left.writeToNBT());
        b.readFromNBT(right.writeToNBT());
        SimpleAssert.eq(
            expected,
            a.getExactCount(COIN)
                .add(b.getExactCount(COIN)),
            "persisted total counted once only");
    }

    private static void teamMergeAndTransferSemantics() {
        NekoTeamData source = new NekoTeamData(), survivor = new NekoTeamData(), separate = new NekoTeamData();
        source.getWallet()
            .addCount(COIN, Integer.MAX_VALUE);
        survivor.getWallet()
            .addCount(COIN, Integer.MAX_VALUE);
        survivor.mergeData(null, null, source);
        survivor.mergeData(null, null, source);
        SimpleAssert.eq(
            MAX.multiply(BigInteger.valueOf(2)),
            survivor.getWallet()
                .getExactCount(COIN),
            "team callback merges exact balance once");
        separate.transferData(null, null, null, survivor, null);
        SimpleAssert.eq(
            BigInteger.ZERO,
            separate.getWallet()
                .getExactCount(COIN),
            "member transfer never copies funds");
        NBTTagCompound nbt = new NBTTagCompound();
        survivor.writeToNBT(nbt);
        NekoTeamData reload = new NekoTeamData();
        reload.readFromNBT(nbt);
        SimpleAssert.eq(
            survivor.getWallet()
                .getExactCount(COIN),
            reload.getWallet()
                .getExactCount(COIN),
            "team owner persists exact funds");
        reload.readFromNBT(new NBTTagCompound());
        SimpleAssert.eq(
            BigInteger.ZERO,
            reload.getWallet()
                .getExactCount(COIN),
            "loading absent wallet clears stale world data");
    }

    private static void staleAliasImportAndWithdrawalInterleavings() {
        NekoWallet alias = new NekoWallet(), root = new NekoWallet();
        root.addCount(COIN, Integer.MAX_VALUE - 10);
        alias.transferAllTo(root);
        int requested = Math.min(64, alias.getCreditCapacity(COIN));
        concurrent(() -> root.addCount(COIN, 7)); // Root changes after the old GUI's preflight.
        alias.addCount(COIN, requested); // ME extraction has already consumed the returned receipt.
        SimpleAssert.eq(
            MAX.add(BigInteger.valueOf(7)),
            root.getExactCount(COIN),
            "stale alias exact credit never drops an extracted ME receipt");

        NekoWallet slots = new NekoWallet(), slotRoot = new NekoWallet();
        slotRoot.addCount(COIN, Integer.MAX_VALUE - 10);
        slots.transferAllTo(slotRoot);
        concurrent(() -> slotRoot.addCount(COIN, 7));
        int accepted = slots.creditUpToCapacity(COIN, 64);
        SimpleAssert.eq(3, accepted, "local import reports only the actually accepted count");
        SimpleAssert.eq(61, 64 - accepted, "unaccepted physical input stays outside the ledger");
        SimpleAssert
            .that(!slots.tryCreditWithinCapacity(COIN, 1), "full survivor atomically rejects all-or-nothing deposit");
        SimpleAssert.that(slotRoot.tryDeduct(COIN, 4), "open four slots of headroom");
        SimpleAssert.that(!slots.tryCreditWithinCapacity(COIN, 5), "oversize whole stack stays physical");
        SimpleAssert.that(slots.tryCreditWithinCapacity(COIN, 4), "old alias deposits a fitting whole stack exactly");

        NekoWallet oldWithdrawal = new NekoWallet(), spender = new NekoWallet();
        spender.addCount(COIN, 10);
        oldWithdrawal.transferAllTo(spender);
        int planned = oldWithdrawal.getCount(COIN);
        concurrent(() -> SimpleAssert.that(spender.tryDeduct(COIN, 7), "competing root purchase"));
        SimpleAssert.that(!oldWithdrawal.tryDeduct(COIN, planned), "failed stale withdrawal never commits outputs");
        SimpleAssert.eq(3, spender.getCount(COIN), "failed withdrawal preserves remaining funds");
        SimpleAssert.that(oldWithdrawal.tryDeduct(COIN, 3), "bounded retry can debit the true remainder");
        SimpleAssert.eq(BigInteger.ZERO, spender.getExactCount(COIN), "exact debit precedes physical output commit");

        NekoWallet finalTeam = new NekoWallet();
        finalTeam.addCount(COIN, Integer.MAX_VALUE);
        concurrent(() -> root.transferAllTo(finalTeam));
        alias.addCount(COIN, 11);
        SimpleAssert.eq(
            MAX.multiply(BigInteger.valueOf(2))
                .add(BigInteger.valueOf(18)),
            finalTeam.getExactCount(COIN),
            "stale GUI extraction/refund follows a second merge without losing value");
    }

    private static void teamHistoryFollowsConsumedOwner() {
        UUID group = UUID.randomUUID();
        NekoTeamData source = new NekoTeamData(), survivor = new NekoTeamData(), third = new NekoTeamData();
        NekoTradeHistory captured = source.getTradeHistory(group);
        survivor.mergeData(null, null, source);
        captured.recordTrade(-1); // Completion already captured the old owner's record before merge.
        captured.setNotificationQueued(true);
        SimpleAssert.eq(
            1L,
            survivor.getTradeHistory(group)
                .getTradeCount(),
            "late success belongs to surviving team");
        SimpleAssert.that(
            !survivor.getTradeHistory(group)
                .canTrade(-1, 1, -1),
            "one-time reward cannot be claimed twice");
        SimpleAssert.that(
            survivor.getTradeHistory(group)
                .isNotificationQueued(),
            "late notification follows history owner");
        third.getTradeHistory(group)
            .recordTrade(0);
        third.getWallet()
            .addCount(COIN, 9);
        source.mergeData(null, null, third); // The callback's destination is itself already consumed.
        SimpleAssert.eq(
            9,
            survivor.getWallet()
                .getCount(COIN),
            "stale callback wallet reaches active owner");
        SimpleAssert.eq(
            2L,
            survivor.getTradeHistory(group)
                .getTradeCount(),
            "history follows the same active owner");
        source.mergeData(null, null, third);
        source.mergeData(null, null, survivor); // Same-root/opposing replay cannot consume the root.
        SimpleAssert.eq(
            2L,
            survivor.getTradeHistory(group)
                .getTradeCount(),
            "replayed callbacks do not duplicate history");
        NekoTeamData next = new NekoTeamData();
        next.mergeData(null, null, survivor);
        captured.recordTrade(0);
        SimpleAssert.eq(
            3L,
            next.getTradeHistory(group)
                .getTradeCount(),
            "old captured record follows a merge chain");
        SimpleAssert.that(
            source.getTradeHistory(group) == next.getTradeHistory(group),
            "new stale-owner lookups route to root");
        NBTTagCompound consumed = new NBTTagCompound(), saved = new NBTTagCompound();
        source.writeToNBT(consumed);
        next.writeToNBT(saved);
        SimpleAssert.eq(
            0,
            consumed.getTagList("tradeHistory", 10)
                .tagCount(),
            "consumed team serializes no copied history");
        SimpleAssert.eq(
            0L,
            captured.writeToNBT()
                .getLong("tradeCount"),
            "consumed record itself has no second owned count");
        NekoTeamData reloaded = new NekoTeamData();
        reloaded.readFromNBT(saved);
        SimpleAssert.eq(
            3L,
            reloaded.getTradeHistory(group)
                .getTradeCount(),
            "surviving team persists every completed trade");
        NekoTeamData separate = new NekoTeamData();
        separate.transferData(null, null, null, source, null);
        SimpleAssert.eq(
            3L,
            separate.getTradeHistory(group)
                .getTradeCount(),
            "member movement copies current root history");
        separate.getTradeHistory(group)
            .recordTrade(0);
        SimpleAssert.eq(
            3L,
            next.getTradeHistory(group)
                .getTradeCount(),
            "member movement does not alias two live teams");
        source.transferData(null, null, null, next, null);
        SimpleAssert.eq(
            3L,
            next.getTradeHistory(group)
                .getTradeCount(),
            "same-root member transfer cannot copy to itself");
        source.resetAllTradeHistories();
        SimpleAssert.eq(
            0L,
            next.getTradeHistory(group)
                .getTradeCount(),
            "stale owner reset targets active history");
    }

    private static void teamHistoryOpposingMergesConserve() {
        UUID group = UUID.randomUUID();
        NekoTeamData left = new NekoTeamData(), right = new NekoTeamData();
        NekoTradeHistory leftHistory = left.getTradeHistory(group), rightHistory = right.getTradeHistory(group);
        CountDownLatch start = new CountDownLatch(1), done = new CountDownLatch(4);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Runnable[] actions = { () -> left.mergeData(null, null, right), () -> right.mergeData(null, null, left),
            () -> { for (int i = 0; i < 1000; i++) leftHistory.recordTrade(0); },
            () -> { for (int i = 0; i < 1000; i++) rightHistory.recordTrade(0); } };
        for (Runnable action : actions) {
            Thread worker = new Thread(() -> {
                try {
                    if (!start.await(5, TimeUnit.SECONDS)) throw new AssertionError("history start timeout");
                    action.run();
                } catch (Throwable error) {
                    failure.compareAndSet(null, error);
                } finally {
                    done.countDown();
                }
            }, "team-history-merge-regression");
            worker.setDaemon(true);
            worker.start();
        }
        start.countDown();
        try {
            SimpleAssert.that(done.await(10, TimeUnit.SECONDS), "opposing history merges do not deadlock");
        } catch (InterruptedException interrupted) {
            Thread.currentThread()
                .interrupt();
            throw new AssertionError(interrupted);
        }
        SimpleAssert.eq(null, failure.get(), "concurrent history operations succeed");
        SimpleAssert.eq(2000L, leftHistory.getTradeCount(), "all old left completions retained");
        SimpleAssert.eq(2000L, rightHistory.getTradeCount(), "all old right completions retained");
        NBTTagCompound a = new NBTTagCompound(), b = new NBTTagCompound();
        left.writeToNBT(a);
        right.writeToNBT(b);
        NekoTeamData first = new NekoTeamData(), second = new NekoTeamData();
        first.readFromNBT(a);
        second.readFromNBT(b);
        SimpleAssert.eq(
            2000L,
            first.getTradeHistory(group)
                .getTradeCount()
                + second.getTradeHistory(group)
                    .getTradeCount(),
            "both persisted owners count each success only once");
    }

    private static void concurrent(Runnable operation) {
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread worker = new Thread(() -> {
            try {
                operation.run();
            } catch (Throwable problem) {
                failure.set(problem);
            } finally {
                done.countDown();
            }
        }, "wallet-alias-interleaving");
        worker.setDaemon(true);
        worker.start();
        try {
            SimpleAssert
                .that(done.await(5, TimeUnit.SECONDS), "concurrent root operation finishes before actor resumes");
        } catch (InterruptedException interrupted) {
            Thread.currentThread()
                .interrupt();
            throw new AssertionError(interrupted);
        }
        SimpleAssert.eq(null, failure.get(), "concurrent root operation succeeds");
    }

    private static void readOnlySnapshotsDoNotMutate() {
        NekoWallet wallet = new NekoWallet();
        wallet.addCount(COIN, 33);
        wallet.getCurrencyIds()
            .clear();
        SimpleAssert.eq(33, wallet.getCount(COIN), "returned key set cannot erase owned money");
        NekoWallet copy = wallet.copy();
        copy.tryDeduct(COIN, 10);
        SimpleAssert.eq(33, wallet.getCount(COIN), "copy has independent balance storage");
    }
}

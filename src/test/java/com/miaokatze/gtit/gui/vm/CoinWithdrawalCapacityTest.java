package com.miaokatze.gtit.gui.vm;

import java.util.LinkedHashMap;
import java.util.Map;

import com.miaokatze.gtit.common.machine.v2.NekoOutputCapacity;
import com.miaokatze.gtit.testutil.SimpleAssert;
import com.miaokatze.gtit.testutil.TestRunner;

/** Production planning and reservation contracts; does not simulate a live machine or ME network. */
public final class CoinWithdrawalCapacityTest {

    private CoinWithdrawalCapacityTest() {}

    public static void run() {
        Map<String, Runnable> cases = new LinkedHashMap<>();
        cases.put("boundedHugeBalances", CoinWithdrawalCapacityTest::boundedHugeBalances);
        cases.put("invalidAndOverflowBounds", CoinWithdrawalCapacityTest::invalidAndOverflowBounds);
        cases.put("sharedCapacityConservesCoins", CoinWithdrawalCapacityTest::sharedCapacityConservesCoins);
        cases.put("failedReservationAndRelease", CoinWithdrawalCapacityTest::failedReservationAndRelease);
        TestRunner.run(CoinWithdrawalCapacityTest.class, cases);
    }

    private static void boundedHugeBalances() {
        SimpleAssert.eq(0, CoinWithdrawalPlan.amount(1_000_000_000, Integer.MAX_VALUE, 0, 64), "full output");
        SimpleAssert.eq(64, CoinWithdrawalPlan.amount(Integer.MAX_VALUE, Integer.MAX_VALUE, 1, 64), "one slot");
        SimpleAssert.eq(
            6400,
            CoinWithdrawalPlan.amount(Integer.MAX_VALUE, Integer.MAX_VALUE, 100, 64),
            "bounded batch");
        SimpleAssert.eq(64, CoinWithdrawalPlan.amount(1_000_000_000, 64, 100, 64), "one-stack request");
        SimpleAssert.eq(17, CoinWithdrawalPlan.amount(17, 64, 100, 64), "balance bound");
        SimpleAssert.eq(
            100,
            CoinWithdrawalPlan.amount(Integer.MAX_VALUE, Integer.MAX_VALUE, 100, 1),
            "item stack limit");
    }

    private static void invalidAndOverflowBounds() {
        SimpleAssert.eq(0, CoinWithdrawalPlan.amount(-1, 64, 1, 64), "negative balance");
        SimpleAssert.eq(0, CoinWithdrawalPlan.amount(64, 0, 1, 64), "zero request");
        SimpleAssert.eq(0, CoinWithdrawalPlan.amount(64, 64, -1, 64), "negative capacity");
        SimpleAssert.eq(0, CoinWithdrawalPlan.amount(64, 64, 1, 0), "invalid stack limit");
        SimpleAssert.eq(
            Integer.MAX_VALUE,
            CoinWithdrawalPlan.amount(Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, 64),
            "capacity multiplication uses long arithmetic");
        SimpleAssert.eq(0, CoinWithdrawalPlan.slots(0, 64), "empty batch");
        SimpleAssert.eq(2, CoinWithdrawalPlan.slots(65, 64), "partial last stack");
        SimpleAssert.eq(
            33554432,
            CoinWithdrawalPlan.slots(Integer.MAX_VALUE, 64),
            "ceiling division does not overflow");
        boolean rejected = false;
        try {
            CoinWithdrawalPlan.slots(1, 0);
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }
        SimpleAssert.that(rejected, "nonempty batch rejects invalid stack limit");
    }

    private static void sharedCapacityConservesCoins() {
        int[] pending = { 0 };
        NekoOutputCapacity capacity = new NekoOutputCapacity(() -> 3, () -> pending[0]);
        int[] balances = { 65, Integer.MAX_VALUE };
        long original = (long) balances[0] + balances[1];
        int paid = 0;
        for (int i = 0; i < balances.length; i++) {
            int amount = CoinWithdrawalPlan.amount(balances[i], Integer.MAX_VALUE, capacity.available(), 64);
            int slots = CoinWithdrawalPlan.slots(amount, 64);
            try (NekoOutputCapacity.Reservation reservation = capacity.reserve(slots)) {
                SimpleAssert.that(
                    reservation != null && capacity.owns(reservation) && reservation.canCommit(slots),
                    "planned batch owns sufficient reservation");
                balances[i] -= amount;
                paid += amount;
                for (int slot = 0; slot < slots; slot++) {
                    pending[0]++;
                    reservation.consume(1);
                }
            }
        }
        SimpleAssert.eq(0, capacity.available(), "currencies share the same capacity");
        SimpleAssert.eq(3, pending[0], "only three stacks queued");
        SimpleAssert.eq(129, paid, "partial first-currency stack still consumes a slot");
        SimpleAssert.eq(original, (long) balances[0] + balances[1] + paid, "wallet plus output is conserved");
    }

    private static void failedReservationAndRelease() {
        NekoOutputCapacity capacity = new NekoOutputCapacity(() -> 3, () -> 0);
        try (NekoOutputCapacity.Reservation reservation = capacity.reserve(2)) {
            SimpleAssert.eq(1, capacity.available(), "reserved slots unavailable to another request");
            SimpleAssert.that(capacity.reserve(2) == null, "competing reservation cannot oversell");
            SimpleAssert.that(!reservation.canCommit(3), "cannot commit beyond reservation");
            NekoOutputCapacity other = new NekoOutputCapacity(() -> 3, () -> 0);
            SimpleAssert.that(!other.owns(reservation), "reservation belongs to one machine");
        }
        SimpleAssert.eq(3, capacity.available(), "unused reservation released");
    }
}

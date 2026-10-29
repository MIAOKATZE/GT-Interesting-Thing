package com.miaokatze.gtit.gui.vm;

/** Pure arithmetic bounds applied before allocating physical coin stacks. */
final class CoinWithdrawalPlan {

    private CoinWithdrawalPlan() {}

    static int amount(int balance, int requested, int availableSlots, int stackLimit) {
        if (balance <= 0 || requested <= 0 || availableSlots <= 0 || stackLimit <= 0) return 0;
        return (int) Math.min(Math.min((long) balance, requested), (long) availableSlots * stackLimit);
    }

    static int slots(int amount, int stackLimit) {
        if (amount <= 0) return 0;
        if (stackLimit <= 0) throw new IllegalArgumentException("stack limit");
        return (int) (((long) amount + stackLimit - 1) / stackLimit);
    }
}

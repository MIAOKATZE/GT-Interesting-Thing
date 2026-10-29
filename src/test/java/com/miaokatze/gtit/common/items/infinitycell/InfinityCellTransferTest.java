package com.miaokatze.gtit.common.items.infinitycell;

import java.lang.reflect.Proxy;

import appeng.api.config.Actionable;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IItemList;

/** Exercises the production transfer kernel shared by legacy item/fluid and typed channels. */
public final class InfinityCellTransferTest {

    public interface Stack extends IAEStack<Stack> {}

    private static Stack stack(long size) {
        final long[] count = { size };
        return (Stack) Proxy.newProxyInstance(Stack.class.getClassLoader(), new Class<?>[] { Stack.class },
            (proxy, method, args) -> {
                switch (method.getName()) {
                    case "getStackSize": return count[0];
                    case "setStackSize": count[0] = (Long) args[0]; return proxy;
                    case "copy": return stack(count[0]);
                    case "toString": return "Stack(" + count[0] + ")";
                    default: throw new AssertionError("Unexpected stack operation " + method);
                }
            });
    }

    private static final class Cell {
        Stack value;
        int saves;

        @SuppressWarnings("unchecked")
        final IItemList<Stack> list = (IItemList<Stack>) Proxy.newProxyInstance(
            IItemList.class.getClassLoader(), new Class<?>[] { IItemList.class }, (proxy, method, args) -> {
                switch (method.getName()) {
                    case "findPrecise": return value;
                    case "add": value = (Stack) args[0]; return null;
                    default: throw new AssertionError("Unexpected list operation " + method);
                }
            });

        Stack inject(Stack input, Actionable mode, long capacity) {
            return InfinityCellTransfer.inject(list, input, mode, capacity, () -> saves++);
        }

        Stack extract(long amount, Actionable mode) {
            return InfinityCellTransfer.extract(list, stack(amount), mode, () -> saves++);
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        Cell cell = new Cell();
        Stack offered = stack(Long.MAX_VALUE);
        Stack simulated = cell.inject(offered, Actionable.SIMULATE, Long.MAX_VALUE - 8);
        check(simulated.getStackSize() == 8 && cell.value == null && cell.saves == 0,
            "new-key simulation must return an exact long remainder without writing");
        Stack remainder = cell.inject(offered, Actionable.MODULATE, Long.MAX_VALUE - 8);
        check(remainder.getStackSize() == 8 && cell.value.getStackSize() == Long.MAX_VALUE - 8,
            "new-key partial insertion must conserve Long.MAX_VALUE, not an int projection");
        check(offered.getStackSize() == Long.MAX_VALUE && cell.value != offered,
            "caller-owned stack must not alias stored content");

        Stack extra = stack(20);
        simulated = cell.inject(extra, Actionable.SIMULATE, Long.MAX_VALUE);
        check(simulated.getStackSize() == 12 && cell.value.getStackSize() == Long.MAX_VALUE - 8 && cell.saves == 1,
            "existing-key simulation must cap at signed-long headroom");
        remainder = cell.inject(extra, Actionable.MODULATE, Long.MAX_VALUE);
        check(remainder.getStackSize() == 12 && cell.value.getStackSize() == Long.MAX_VALUE && cell.saves == 2,
            "existing-key commit must not wrap negative");
        check(cell.inject(stack(1), Actionable.MODULATE, Long.MAX_VALUE).getStackSize() == 1 && cell.saves == 2,
            "full cell must reject without a dirty write");

        for (long bad : new long[] { Long.MIN_VALUE, -1, 0 }) {
            Stack invalid = stack(bad);
            check(cell.inject(invalid, Actionable.MODULATE, Long.MAX_VALUE) == invalid,
                "nonpositive offers must not change stored count");
            check(cell.extract(bad, Actionable.MODULATE) == null, "negative extraction must not manufacture stock");
        }
        check(cell.value.getStackSize() == Long.MAX_VALUE && cell.saves == 2, "invalid operations are inert");
        check(cell.extract(17, Actionable.SIMULATE).getStackSize() == 17 && cell.saves == 2,
            "extraction simulation must not mutate");
        Stack taken = cell.extract(17, Actionable.MODULATE);
        check(taken.getStackSize() == 17 && cell.value.getStackSize() == Long.MAX_VALUE - 17,
            "extraction conserves both sides");
        taken.setStackSize(1);
        check(cell.value.getStackSize() == Long.MAX_VALUE - 17, "returned stack must be detached");
        check(cell.extract(Long.MAX_VALUE, Actionable.MODULATE).getStackSize() == Long.MAX_VALUE - 17,
            "oversized positive extraction is capped at stock");
        check(cell.value.getStackSize() == 0 && cell.extract(1, Actionable.MODULATE) == null,
            "empty entries cannot produce zero-sized output");
        System.out.println("InfinityCellTransferTest: PASS (long boundaries, invalid requests, simulation, copy ownership)");
    }
}

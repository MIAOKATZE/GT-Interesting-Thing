package com.miaokatze.gtit.testlab.scenario;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidRegistry;

import com.miaokatze.gtit.common.items.pocket.PocketInventory;
import com.miaokatze.gtit.currency.NekoCurrencyRegistrar;
import com.miaokatze.gtit.testlab.ProbeContext;
import com.miaokatze.gtit.testlab.ProbeScenario;
import com.miaokatze.gtit.testlab.ProbeTask;
import com.miaokatze.gtit.trade.NekoWallet;

/** Native data operations, bounded per natural tick. Timings are observations, not an MSPT/TPS benchmark. */
final class NativeStressScenario implements ProbeScenario {

    private static final int BATCHES = 32;
    private static final int OPERATIONS_PER_TICK = 64;

    @Override
    public String id() {
        return "native-bounded-stress";
    }

    @Override
    public boolean stress() {
        return true;
    }

    @Override
    public ProbeTask start(ProbeContext context) {
        return new ProbeTask() {

            private PocketInventory inventory = PocketInventory.readFrom(new NBTTagCompound());
            private final NekoWallet wallet = new NekoWallet();
            private final ItemStack template = NativeFixtures.specialStack(context, 1);
            private int batch;
            private int completed;
            private boolean conserved = true;
            private String firstFailure = "none";
            private long elapsed;
            private long maximum;

            @Override
            public boolean tick() {
                long started = System.nanoTime();
                String coin = NekoCurrencyRegistrar.NEKO_ID;
                for (int i = 0; i < OPERATIONS_PER_TICK; i++) {
                    int tank = completed % PocketInventory.FLUID_TANK_COUNT;
                    int inserted = inventory.depositIntoStorage(template);
                    ItemStack extracted = inventory.storage()
                        .extractItem(0, 1, false);
                    long filled = inventory.fillOwnTankL(tank, FluidRegistry.WATER, 1001);
                    long drained = inventory.drainOwnTankL(tank, 1001);
                    wallet.addCount(coin, 7);
                    boolean spent = wallet.tryDeduct(coin, 7);
                    boolean rejected = !wallet.tryDeduct(coin, 1);
                    boolean good = inserted == 1 && NativeFixtures.sameStack(template, extracted)
                        && filled == 1001
                        && drained == 1001
                        && inventory.tankTruthAt(tank) == 0
                        && spent
                        && rejected
                        && wallet.getCount(coin) == 0;
                    if (!good && conserved) firstFailure = "operation=" + completed + ", tank=" + tank;
                    conserved &= good;
                    completed++;
                }
                NBTTagCompound checkpoint = new NBTTagCompound();
                inventory.writeTo(checkpoint);
                inventory = PocketInventory.readFrom(checkpoint);
                long duration = System.nanoTime() - started;
                elapsed += duration;
                maximum = Math.max(maximum, duration);
                if (++batch < BATCHES) return false;
                context.check(
                    "bounded-operation-count",
                    completed == BATCHES * OPERATIONS_PER_TICK,
                    "32 natural ticks, at most 64 native item/fluid/wallet cycles and one pocket codec checkpoint per tick");
                context.check(
                    "native-conservation-under-load",
                    conserved,
                    "Every insert/extract, fill/drain and wallet spend/reject conserved state; firstFailure="
                        + firstFailure);
                boolean empty = true;
                for (int slot = 0; slot < inventory.storage()
                    .getSlots(); slot++) empty &= inventory.storageStack(slot) == null;
                for (int tank = 0; tank < PocketInventory.FLUID_TANK_COUNT; tank++)
                    empty &= inventory.tankTruthAt(tank) == 0;
                context.check(
                    "native-final-empty",
                    empty && wallet.getCount(coin) == 0,
                    "All 135 native storage slots, 18 tank truths and test wallet finish empty after checkpoint reload");
                context.metric("operationCycles", completed);
                context.metric("nativeWorkMillis", elapsed / 1_000_000.0);
                context.metric("maxNativeBatchMillis", maximum / 1_000_000.0);
                context.skip(
                    "world-and-network-throughput",
                    "This is native in-memory data-path load; no claim of thousands of placed machines, packets, TPS or client FPS");
                return true;
            }
        };
    }
}

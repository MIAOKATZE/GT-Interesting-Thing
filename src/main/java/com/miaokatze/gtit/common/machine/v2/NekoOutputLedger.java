package com.miaokatze.gtit.common.machine.v2;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

/** Server-owned identities only. The output handler and paid pending queue remain the only inventories. */
public final class NekoOutputLedger {

    public static final int CAPACITY = 100;

    public static final class Token {

        public final long batchId, dropId, generation;
        public boolean rear;

        Token(long batch, long drop, long generation, boolean rear) {
            this.batchId = batch;
            this.dropId = drop;
            this.generation = generation;
            this.rear = rear;
        }

        NBTTagCompound write() {
            NBTTagCompound n = new NBTTagCompound();
            n.setLong("batchId", batchId);
            n.setLong("dropId", dropId);
            n.setLong("generation", generation);
            n.setBoolean("rear", rear);
            return n;
        }
    }

    public static final class Drop {

        public final long batchId, dropId, seed;
        public final ItemStack item;
        public boolean rear;
        public final List<Integer> slots = new ArrayList<>();

        Drop(Token t, ItemStack item) {
            batchId = t.batchId;
            dropId = t.dropId;
            seed = t.dropId * 6364136223846793005L + t.batchId;
            this.item = item.copy();
            this.item.stackSize = 1;
            rear = t.rear;
        }
    }

    private final Map<Long, Drop> drops = new LinkedHashMap<>();
    private final IdentityHashMap<ItemStack, Token> pending = new IdentityHashMap<>();
    private final Token[] slots = new Token[CAPACITY];
    private long nextBatch = 1, nextDrop = 1, nextGeneration = 1, activeBatch;
    private boolean activeRear;
    private int remainingInBatch;
    private long revision;

    public static int availableSlots(int emptySlots, int paidPendingStacks) {
        return Math.max(0, emptySlots - paidPendingStacks);
    }

    public long revision() {
        return revision;
    }

    public List<Drop> drops() {
        return new ArrayList<>(drops.values());
    }

    public Token slot(int index) {
        return index >= 0 && index < CAPACITY ? slots[index] : null;
    }

    public Token pending(ItemStack stack) {
        return pending.get(stack);
    }

    public void beginBatch(int stackCount) {
        activeBatch = nextBatch++;
        activeRear = stackCount > 16;
        remainingInBatch = stackCount;
    }

    public void skipQueuedStacks(int count) {
        remainingInBatch = Math.max(0, remainingInBatch - Math.max(0, count));
    }

    public boolean hasOpenBatch() {
        return remainingInBatch > 0;
    }

    public void endBatch() {
        activeBatch = 0;
        remainingInBatch = 0;
    }

    public Token enqueue(ItemStack stack, boolean legendaryRear) {
        if (stack == null || stack.stackSize <= 0) throw new IllegalArgumentException("empty output");
        long batch = remainingInBatch > 0 ? activeBatch : nextBatch++;
        boolean rear = activeRear && remainingInBatch > 0 || legendaryRear;
        Drop match = null;
        for (Drop d : drops.values()) if (d.batchId == batch && d.rear == rear && same(d.item, stack)) {
            match = d;
            break;
        }
        Token t = new Token(batch, match == null ? nextDrop++ : match.dropId, nextGeneration++, rear);
        if (match == null) drops.put(t.dropId, new Drop(t, stack));
        else if (t.rear) match.rear = true;
        pending.put(stack, t);
        if (remainingInBatch > 0) remainingInBatch--;
        revision++;
        return t;
    }

    public void delivered(ItemStack pendingStack, int index) {
        Token t = pending.remove(pendingStack);
        if (t == null) throw new IllegalStateException("missing enqueue identity");
        removeSlot(index);
        slots[index] = t;
        Drop d = drops.get(t.dropId);
        d.slots.add(index);
        revision++;
    }

    public void cancel(ItemStack pendingStack) {
        Token t = pending.remove(pendingStack);
        if (t != null) {
            prune(t.dropId);
            revision++;
        }
    }

    /** Called on every handler mutation; a quantity-only mutation never changes a drop identity. */
    public void changed(int index, ItemStack actual) {
        Token t = slots[index];
        if (t != null) {
            Drop d = drops.get(t.dropId);
            if (actual == null || actual.stackSize <= 0 || d == null || !same(d.item, actual)) removeSlot(index);
        }
        if (slots[index] == null && actual != null && actual.stackSize > 0) {
            // External automation / old save migration gets a distinct stable identity.
            Token fresh = new Token(nextBatch++, nextDrop++, nextGeneration++, false);
            Drop d = new Drop(fresh, actual);
            d.slots.add(index);
            drops.put(fresh.dropId, d);
            slots[index] = fresh;
        }
        revision++;
    }

    private void removeSlot(int index) {
        Token t = slots[index];
        if (t == null) return;
        slots[index] = null;
        Drop d = drops.get(t.dropId);
        if (d != null) d.slots.remove(Integer.valueOf(index));
        prune(t.dropId);
    }

    private void prune(long id) {
        Drop d = drops.get(id);
        if (d == null || !d.slots.isEmpty()) return;
        for (Token t : pending.values()) if (t.dropId == id) return;
        drops.remove(id);
    }

    public static boolean same(ItemStack a, ItemStack b) {
        return a != null && b != null && a.isItemEqual(b) && ItemStack.areItemStackTagsEqual(a, b);
    }

    public NBTTagCompound write() {
        NBTTagCompound n = new NBTTagCompound();
        n.setInteger("version", 1);
        n.setLong("nextBatch", nextBatch);
        n.setLong("nextDrop", nextDrop);
        n.setLong("nextGeneration", nextGeneration);
        NBTTagList list = new NBTTagList();
        for (int i = 0; i < CAPACITY; i++) if (slots[i] != null) {
            NBTTagCompound e = slots[i].write();
            e.setInteger("slot", i);
            list.appendTag(e);
        }
        n.setTag("slots", list);
        return n;
    }

    public NBTTagCompound pendingTag(ItemStack stack) {
        Token t = pending.get(stack);
        return t == null ? new NBTTagCompound() : t.write();
    }

    /** Restore metadata after real inventory. Invalid identities migrate, never alter inventory. */
    public void restore(NBTTagCompound n, ItemStack[] actual, List<ItemStack> queue, List<NBTTagCompound> tokens) {
        drops.clear();
        pending.clear();
        java.util.Arrays.fill(slots, null);
        nextBatch = nextDrop = nextGeneration = 1;
        endBatch();
        NBTTagList list = n.getTagList("slots", 10);
        boolean valid = n.getInteger("version") == 1 && list.tagCount() <= CAPACITY && tokens.size() == queue.size();
        Map<Integer, NBTTagCompound> saved = new LinkedHashMap<>();
        java.util.HashSet<Long> generations = new java.util.HashSet<>();
        if (valid) for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound e = list.getCompoundTagAt(i);
            int s = e.getInteger("slot");
            if (s < 0 || s >= CAPACITY || saved.containsKey(s) || actual[s] == null || !validToken(e, generations)) {
                valid = false;
                break;
            }
            saved.put(s, e);
        }
        if (valid) for (NBTTagCompound e : tokens) if (!validToken(e, generations)) {
            valid = false;
            break;
        }
        if (valid) {
            for (int i = 0; i < CAPACITY; i++) if (actual[i] != null && actual[i].stackSize > 0) {
                NBTTagCompound e = saved.get(i);
                if (e == null) {
                    valid = false;
                    break;
                }
                Token t = read(e);
                if (!restoreDrop(t, actual[i])) {
                    valid = false;
                    break;
                }
                slots[i] = t;
                drops.get(t.dropId).slots.add(i);
            }
            if (valid) for (int i = 0; i < queue.size(); i++) {
                Token t = read(tokens.get(i));
                if (!restoreDrop(t, queue.get(i))) {
                    valid = false;
                    break;
                }
                pending.put(queue.get(i), t);
            }
        }
        if (!valid) {
            drops.clear();
            pending.clear();
            java.util.Arrays.fill(slots, null);
            nextBatch = nextDrop = nextGeneration = 1;
            for (int i = 0; i < CAPACITY; i++) if (actual[i] != null && actual[i].stackSize > 0) changed(i, actual[i]);
            for (ItemStack stack : queue) enqueue(stack, false);
        } else {
            nextBatch = restoredCounter(nextBatch, n.getLong("nextBatch"));
            nextDrop = restoredCounter(nextDrop, n.getLong("nextDrop"));
            nextGeneration = restoredCounter(nextGeneration, n.getLong("nextGeneration"));
        }
        revision++;
    }

    /** Preserve valid high-water marks, but never trust malformed roots to overflow future identities. */
    private static long restoredCounter(long derived, long saved) {
        return saved > 0 && saved < Long.MAX_VALUE - 1000 ? Math.max(derived, saved) : derived;
    }

    private boolean restoreDrop(Token t, ItemStack item) {
        Drop d = drops.get(t.dropId);
        if (d != null && (d.batchId != t.batchId || !same(d.item, item))) return false;
        if (d == null) drops.put(t.dropId, new Drop(t, item));
        else d.rear |= t.rear;
        nextBatch = Math.max(nextBatch, t.batchId + 1);
        nextDrop = Math.max(nextDrop, t.dropId + 1);
        nextGeneration = Math.max(nextGeneration, t.generation + 1);
        return true;
    }

    private static Token read(NBTTagCompound e) {
        return new Token(e.getLong("batchId"), e.getLong("dropId"), e.getLong("generation"), e.getBoolean("rear"));
    }

    private static boolean validToken(NBTTagCompound e, java.util.Set<Long> generations) {
        long b = e.getLong("batchId"), d = e.getLong("dropId"), g = e.getLong("generation");
        return b > 0 && d > 0
            && g > 0
            && b < Long.MAX_VALUE - 1000
            && d < Long.MAX_VALUE - 1000
            && g < Long.MAX_VALUE - 1000
            && generations.add(g);
    }
}

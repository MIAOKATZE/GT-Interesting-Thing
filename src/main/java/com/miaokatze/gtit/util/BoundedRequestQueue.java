package com.miaokatze.gtit.util;

import java.util.ArrayDeque;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Queue;
import java.util.function.Consumer;

/** Pre-admission only: never use this queue for an already-committed delivery or inventory return. */
final class BoundedRequestQueue {

    private final int globalLimit;
    private final int ownerLimit;
    private final Queue<Entry> requests = new ArrayDeque<>();
    private final Map<Object, Integer> owners = new IdentityHashMap<>();
    private boolean active;
    private long generation;
    private int pending;

    BoundedRequestQueue(int globalLimit, int ownerLimit) {
        if (globalLimit <= 0 || ownerLimit <= 0) throw new IllegalArgumentException("positive limits required");
        this.globalLimit = globalLimit;
        this.ownerLimit = ownerLimit;
    }

    synchronized void start() {
        stop();
        active = true;
    }

    synchronized void stop() {
        active = false;
        generation++;
        requests.clear();
        owners.clear();
        pending = 0;
    }

    synchronized boolean offer(Object owner, Runnable task) {
        if (!active || owner == null || task == null || pending >= globalLimit) return false;
        int count = owners.getOrDefault(owner, 0);
        if (count >= ownerLimit) return false;
        requests.offer(new Entry(owner, task, generation));
        owners.put(owner, count + 1);
        pending++;
        return true;
    }

    void drain(int limit, Consumer<Throwable> onError) {
        final int budget;
        final long expected;
        synchronized (this) {
            budget = active ? Math.min(limit, requests.size()) : 0;
            expected = generation;
        }
        // Snapshot the budget: a task/network producer cannot keep this tick alive by replenishing it.
        for (int i = 0; i < budget; i++) {
            final Entry entry;
            synchronized (this) {
                if (!active || generation != expected) return;
                entry = requests.poll();
            }
            if (entry == null) return;
            try {
                entry.task.run();
            } catch (Throwable t) {
                onError.accept(t);
            } finally {
                synchronized (this) {
                    // A lifecycle restart inside a callback must not decrement the new generation's counts.
                    if (entry.generation == generation) {
                        int count = owners.get(entry.owner);
                        if (count == 1) owners.remove(entry.owner);
                        else owners.put(entry.owner, count - 1);
                        pending--;
                    }
                }
            }
        }
    }

    private static final class Entry {

        final Object owner;
        final Runnable task;
        final long generation;

        Entry(Object owner, Runnable task, long generation) {
            this.owner = owner;
            this.task = task;
            this.generation = generation;
        }
    }
}

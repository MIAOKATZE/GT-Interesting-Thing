package com.miaokatze.gtit.util;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

/** Deterministic admission/lifecycle tests; no Minecraft world or network is needed. */
public final class BoundedRequestQueueTest {

    public static void main(String[] args) throws Exception {
        BoundedRequestQueue queue = new BoundedRequestQueue(3, 2);
        Object a = new Object(), b = new Object();
        AtomicInteger ran = new AtomicInteger();
        check(!queue.offer(a, ran::incrementAndGet), "stopped admission");
        queue.start();
        check(queue.offer(a, ran::incrementAndGet) && queue.offer(a, ran::incrementAndGet), "owner allowance");
        check(!queue.offer(a, ran::incrementAndGet), "owner limit");
        check(queue.offer(b, ran::incrementAndGet) && !queue.offer(new Object(), ran::incrementAndGet), "global limit");
        queue.drain(1, t -> { throw new AssertionError(t); });
        check(ran.get() == 1 && queue.offer(a, ran::incrementAndGet), "bounded drain releases one slot");
        queue.stop();
        queue.start();
        queue.drain(100, t -> { throw new AssertionError(t); });
        check(ran.get() == 1, "old requests never survive restart");
        queue.offer(a, () -> {
            queue.start();
            check(queue.offer(a, ran::incrementAndGet) && queue.offer(a, ran::incrementAndGet), "restart new counts");
        });
        queue.offer(b, () -> { throw new AssertionError("old generation executed"); });
        queue.drain(100, t -> { throw new AssertionError(t); });
        check(ran.get() == 1 && !queue.offer(a, ran::incrementAndGet), "old completion does not alter new counts");
        queue.drain(100, t -> { throw new AssertionError(t); });
        check(ran.get() == 3, "new generation gets next tick");
        AtomicInteger errors = new AtomicInteger();
        queue.offer(a, () -> { throw new IllegalStateException("expected"); });
        queue.drain(1, t -> errors.incrementAndGet());
        check(errors.get() == 1 && queue.offer(a, ran::incrementAndGet), "failed requests release admission");
        queue.start();
        Runnable[] recurse = new Runnable[1];
        recurse[0] = () -> { ran.incrementAndGet(); queue.offer(a, recurse[0]); };
        queue.offer(a, recurse[0]);
        queue.drain(100, t -> { throw new AssertionError(t); });
        check(ran.get() == 4, "reentrant producer cannot extend tick snapshot");
        BoundedRequestQueue concurrent = new BoundedRequestQueue(32, 4);
        concurrent.start();
        AtomicInteger accepted = new AtomicInteger();
        CountDownLatch go = new CountDownLatch(1);
        Thread[] threads = new Thread[16];
        for (int i = 0; i < threads.length; i++) {
            threads[i] = new Thread(() -> {
                Object owner = new Object();
                try { go.await(); } catch (InterruptedException failure) { throw new AssertionError(failure); }
                for (int j = 0; j < 100; j++) if (concurrent.offer(owner, () -> {})) accepted.incrementAndGet();
            });
            threads[i].start();
        }
        go.countDown();
        for (Thread thread : threads) thread.join();
        check(accepted.get() == 32, "concurrent producers obey global bound");
        System.out.println("BoundedRequestQueueTest: 13 admission, tick, failure, generation and concurrent cases passed");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}

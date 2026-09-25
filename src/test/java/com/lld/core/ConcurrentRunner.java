package com.lld.core;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.IntFunction;

/**
 * Fires N tasks at the same instant (start-gate latch) and collects each outcome.
 * Use it to PROVE thread safety instead of claiming it:
 *
 *   var outcomes = ConcurrentRunner.run(50, i -> lot.park(new Vehicle("CAR-" + i, CAR)));
 *   assertEquals(2, outcomes.stream().filter(Outcome::succeeded).count());
 */
public final class ConcurrentRunner {

    private ConcurrentRunner() {
    }

    public static <T> List<Outcome<T>> run(int threads, IntFunction<T> task) throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<T>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < threads; i++) {
                final int index = i;
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    go.await();
                    return task.apply(index);
                }));
            }
            ready.await();
            go.countDown();

            List<Outcome<T>> outcomes = new ArrayList<>();
            for (Future<T> future : futures) {
                try {
                    outcomes.add(new Outcome<>(future.get(), null));
                } catch (ExecutionException e) {
                    outcomes.add(new Outcome<>(null, e.getCause()));
                }
            }
            return outcomes;
        } finally {
            pool.shutdownNow();
        }
    }

    public record Outcome<T>(T value, Throwable error) {
        public boolean succeeded() {
            return error == null;
        }
    }
}

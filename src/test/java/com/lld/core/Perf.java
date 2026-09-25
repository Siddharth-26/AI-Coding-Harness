package com.lld.core;

import java.util.Arrays;
import java.util.function.IntConsumer;

/**
 * In-memory smoke checks for latency / throughput NFRs. This is NOT a benchmark (no JMH, shared
 * laptop, JIT noise), so assert against generous budgets: latency <= 10x the stated target,
 * throughput >= target / 10. The point is to catch order-of-magnitude problems, like an O(n)
 * scan hiding in a "fast" path, not to prove the SLA.
 */
public final class Perf {

    private Perf() {
    }

    public record LatencyStats(long p50Micros, long p99Micros, long maxMicros) {
        @Override
        public String toString() {
            return "p50 " + p50Micros + " us, p99 " + p99Micros + " us, max " + maxMicros + " us";
        }
    }

    /** Runs op(i) `warmup` times (ignored, lets the JIT settle), then `iterations` measured times. */
    public static LatencyStats latency(int warmup, int iterations, IntConsumer op) {
        for (int i = 0; i < warmup; i++) {
            op.accept(i);
        }
        long[] micros = new long[iterations];
        for (int i = 0; i < iterations; i++) {
            long start = System.nanoTime();
            op.accept(warmup + i);
            micros[i] = (System.nanoTime() - start) / 1_000;
        }
        Arrays.sort(micros);
        return new LatencyStats(micros[index(iterations, 50)], micros[index(iterations, 99)], micros[iterations - 1]);
    }

    /**
     * Runs threads x opsPerThread operations concurrently and returns operations per second.
     * op receives a unique index. Every op must succeed; wrap expected failures inside op.
     */
    public static double throughput(int threads, int opsPerThread, IntConsumer op) throws InterruptedException {
        long start = System.nanoTime();
        var outcomes = ConcurrentRunner.run(threads, t -> {
            for (int i = 0; i < opsPerThread; i++) {
                op.accept(t * opsPerThread + i);
            }
            return null;
        });
        double seconds = (System.nanoTime() - start) / 1e9;
        for (var outcome : outcomes) {
            if (!outcome.succeeded()) {
                throw new AssertionError("operation failed under load", outcome.error());
            }
        }
        return threads * (double) opsPerThread / seconds;
    }

    private static int index(int n, int percentile) {
        return Math.max(0, Math.min(n - 1, (int) Math.ceil(percentile / 100.0 * n) - 1));
    }
}

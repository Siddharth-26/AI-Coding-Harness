package com.lld.core;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Request coalescing (a.k.a. single-flight). Concurrent calls for the SAME key share one
 * in-flight execution: the first caller (leader) runs the loader, everyone who arrives while it
 * is running (followers) waits for and receives the leader's result or exception.
 *
 *   SeatMap map = seatMapCache.get(showId);
 *   if (map == null) {
 *       map = seatMapLoads.execute(showId, () -> {
 *           SeatMap fresh = seatRepository.loadSeatMap(showId); // runs ONCE per stampede
 *           seatMapCache.put(showId, fresh);
 *           return fresh;
 *       });
 *   }
 *
 * The problem it solves: a hot key (the Friday 7pm show, a popular restaurant menu) expires from
 * cache and 1,000 requests miss at the same instant -> 1,000 identical DB queries (cache stampede).
 * With single-flight it's 1 query + 999 waiters.
 *
 * It does NOT cache: once the flight lands, the next call starts a new one. Pair it with a cache.
 * Why not ConcurrentHashMap.computeIfAbsent? It also coalesces, but runs the load while holding a
 * bin lock (can block unrelated keys, forbids touching the same map inside the loader) and it
 * cannot evict failures cleanly.
 * HLD equivalent: per-node single-flight in each app server + a short Redis lock / "stale while
 * revalidate" so only one node refreshes a hot key.
 */
public class SingleFlight<K, V> {

    private final Map<K, CompletableFuture<V>> inFlight = new ConcurrentHashMap<>();

    public V execute(K key, Supplier<V> loader) {
        CompletableFuture<V> mine = new CompletableFuture<>();
        CompletableFuture<V> existing = inFlight.putIfAbsent(key, mine);
        if (existing != null) {
            return await(existing); // follower
        }
        try { // leader
            V value = loader.get();
            mine.complete(value);
            return value;
        } catch (RuntimeException | Error e) {
            mine.completeExceptionally(e);
            throw e;
        } finally {
            inFlight.remove(key, mine);
        }
    }

    /** Number of keys currently being loaded (useful in tests and metrics). */
    public int inFlightCount() {
        return inFlight.size();
    }

    static <V> V await(CompletableFuture<V> future) {
        try {
            return future.join();
        } catch (CompletionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw e;
        }
    }
}

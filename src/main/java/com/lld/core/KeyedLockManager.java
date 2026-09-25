package com.lld.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * Fine-grained locking: lock ONE seat / account / ticket, not the whole service.
 *
 *   locks.withLock(ticketId, () -> checkout(ticket));              // single resource
 *   locks.withLocks(List.of("S-A1", "S-A2"), () -> holdSeats(...)); // multi-resource
 *
 * Multi-key locks are always acquired in sorted order, so two threads booking {A1, A2} and
 * {A2, A1} can never deadlock. That ordering rule is the interview talking point.
 *
 * Known trade-off: the lock map grows with distinct keys. Fix with lock striping
 * (fixed array of N locks, index = hash(key) % N) if asked.
 */
public class KeyedLockManager {

    private final Map<String, ReentrantLock> locks = new ConcurrentHashMap<>();

    public <T> T withLock(String key, Supplier<T> action) {
        return withLocks(List.of(key), action);
    }

    public void runWithLock(String key, Runnable action) {
        withLock(key, () -> {
            action.run();
            return null;
        });
    }

    public <T> T withLocks(Collection<String> keys, Supplier<T> action) {
        List<ReentrantLock> acquired = new ArrayList<>();
        try {
            for (String key : new TreeSet<>(keys)) { // sorted + de-duplicated = global lock order
                ReentrantLock lock = locks.computeIfAbsent(key, k -> new ReentrantLock());
                lock.lock();
                acquired.add(lock);
            }
            return action.get();
        } finally {
            for (int i = acquired.size() - 1; i >= 0; i--) {
                acquired.get(i).unlock();
            }
        }
    }
}

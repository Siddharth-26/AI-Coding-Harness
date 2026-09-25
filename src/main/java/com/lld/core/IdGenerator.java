package com.lld.core;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Thread-safe, human-readable ids: next("ORD") -> "ORD-1", "ORD-2", ...
 * Readable ids make demo output and debugging far easier than UUIDs.
 * (In a distributed system: Snowflake-style ids or DB sequences — say so in HLD.)
 */
public final class IdGenerator {

    private static final Map<String, AtomicLong> COUNTERS = new ConcurrentHashMap<>();

    private IdGenerator() {
    }

    public static String next(String prefix) {
        return prefix + "-" + COUNTERS.computeIfAbsent(prefix, p -> new AtomicLong()).incrementAndGet();
    }
}

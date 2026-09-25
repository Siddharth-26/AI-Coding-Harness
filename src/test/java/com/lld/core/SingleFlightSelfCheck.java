package com.lld.core;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SingleFlightSelfCheck {

    private static final int CALLERS = 100;

    @Test
    void hundredConcurrentMissesOnOneHotKeyTriggerOneLoad() throws InterruptedException {
        SingleFlight<String, String> flights = new SingleFlight<>();
        AtomicInteger loads = new AtomicInteger();
        AtomicInteger arrived = new AtomicInteger();

        var outcomes = ConcurrentRunner.run(CALLERS, i -> {
            arrived.incrementAndGet();
            return flights.execute("show:PVR-Forum-7pm", () -> {
                loads.incrementAndGet();
                awaitAll(arrived);          // keep the flight open until every caller has joined
                return "seat-map-v1";
            });
        });

        assertEquals(1, loads.get());
        assertTrue(outcomes.stream().allMatch(o -> "seat-map-v1".equals(o.value())));
        assertEquals(0, flights.inFlightCount());
    }

    @Test
    void failureReachesEveryWaiterAndTheNextCallRetries() throws InterruptedException {
        SingleFlight<String, String> flights = new SingleFlight<>();
        AtomicInteger arrived = new AtomicInteger();

        var outcomes = ConcurrentRunner.run(20, i -> {
            arrived.incrementAndGet();
            return flights.execute("menu:meghna-biryani", () -> {
                awaitAll(arrived, 20);
                throw new IllegalStateException("db timeout");
            });
        });
        outcomes.forEach(o -> assertInstanceOf(IllegalStateException.class, o.error()));

        assertEquals("menu-v2", flights.execute("menu:meghna-biryani", () -> "menu-v2"));
    }

    @Test
    void differentKeysDoNotShareAFlight() {
        SingleFlight<String, Integer> flights = new SingleFlight<>();
        AtomicInteger loads = new AtomicInteger();

        flights.execute("a", loads::incrementAndGet);
        flights.execute("b", loads::incrementAndGet);

        assertEquals(2, loads.get());
    }

    @Test
    void sequentialCallsAreNotCached() {
        SingleFlight<String, Integer> flights = new SingleFlight<>();
        AtomicInteger loads = new AtomicInteger();

        List.of(1, 2, 3).forEach(n -> flights.execute("k", loads::incrementAndGet));

        assertEquals(3, loads.get());
        assertThrows(IllegalStateException.class, () -> flights.execute("k", () -> {
            throw new IllegalStateException("boom");
        }));
    }

    private static void awaitAll(AtomicInteger arrived) {
        awaitAll(arrived, CALLERS);
    }

    /** Wait until all callers have entered execute(), then give them a moment to reach putIfAbsent. */
    private static void awaitAll(AtomicInteger arrived, int expected) {
        long deadline = System.nanoTime() + 5_000_000_000L;
        while (arrived.get() < expected && System.nanoTime() < deadline) {
            Thread.onSpinWait();
        }
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}

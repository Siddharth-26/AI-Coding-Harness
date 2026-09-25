package com.lld.core;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoreUtilitiesSelfCheck {

    @Test
    void multiKeyLocksInOppositeOrderDoNotDeadlock() {
        KeyedLockManager locks = new KeyedLockManager();
        AtomicInteger counter = new AtomicInteger();

        assertTimeoutPreemptively(Duration.ofSeconds(10), () -> {
            ConcurrentRunner.run(8, i -> {
                List<String> keys = (i % 2 == 0) ? List.of("SEAT-A1", "SEAT-A2") : List.of("SEAT-A2", "SEAT-A1");
                for (int n = 0; n < 2_000; n++) {
                    locks.withLocks(keys, counter::incrementAndGet);
                }
                return null;
            });
        });
        assertEquals(8 * 2_000, counter.get());
    }

    @Test
    void failingSubscriberDoesNotBlockOthers() {
        record Ping(String id) {
        }
        EventBus bus = new EventBus();
        List<String> received = new ArrayList<>();
        bus.subscribe(Ping.class, p -> {
            throw new IllegalStateException("boom");
        });
        bus.subscribe(Ping.class, p -> received.add(p.id()));

        bus.publish(new Ping("p1"));

        assertEquals(List.of("p1"), received);
    }

    @Test
    void idGeneratorIsUniqueUnderConcurrency() throws InterruptedException {
        var outcomes = ConcurrentRunner.run(32, i -> IdGenerator.next("UNIQ"));
        long distinct = outcomes.stream().map(ConcurrentRunner.Outcome::value).distinct().count();
        assertEquals(32, distinct);
        assertTrue(outcomes.stream().allMatch(o -> o.value().startsWith("UNIQ-")));
    }
}

package com.lld.core;

import com.lld.core.exception.ConflictException;
import com.lld.core.exception.ValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IdempotencyStoreSelfCheck {

    record ChargeRequest(String walletId, long amount) {
    }

    private MutableClock clock;
    private IdempotencyStore<String> store;
    private AtomicInteger debits;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-09-24T09:00:00Z"));
        store = new IdempotencyStore<>(clock, Duration.ofHours(24));
        debits = new AtomicInteger();
    }

    private String debit() {
        return "TXN-" + debits.incrementAndGet();
    }

    @Test
    void clientRetryReturnsOriginalResultWithoutSecondDebit() {
        var request = new ChargeRequest("W-1", 499);

        String first = store.execute("key-1", request, this::debit);
        String retry = store.execute("key-1", request, this::debit);

        assertEquals(first, retry);
        assertEquals(1, debits.get());
    }

    @Test
    void doubleTappedPayButtonDebitsOnce() throws InterruptedException {
        var request = new ChargeRequest("W-1", 499);

        var outcomes = ConcurrentRunner.run(20, i -> store.execute("key-1", request, () -> {
            sleepQuietly(50); // slow gateway: duplicates arrive while the first is in flight
            return debit();
        }));

        assertEquals(1, debits.get());
        assertTrue(outcomes.stream().allMatch(o -> "TXN-1".equals(o.value())));
    }

    @Test
    void sameKeyWithDifferentBodyIsRejected() {
        store.execute("key-1", new ChargeRequest("W-1", 499), this::debit);

        assertThrows(ConflictException.class,
                () -> store.execute("key-1", new ChargeRequest("W-1", 999), this::debit));
    }

    @Test
    void failedAttemptIsForgottenSoRetryExecutes() {
        var request = new ChargeRequest("W-1", 499);
        assertThrows(IllegalStateException.class, () -> store.execute("key-1", request, () -> {
            throw new IllegalStateException("gateway 503");
        }));

        assertEquals("TXN-1", store.execute("key-1", request, this::debit));
    }

    @Test
    void keyCanBeReusedAfterTtl() {
        var request = new ChargeRequest("W-1", 499);
        store.execute("key-1", request, this::debit);

        clock.advance(Duration.ofHours(25));

        assertEquals("TXN-2", store.execute("key-1", request, this::debit));
    }

    @Test
    void missingKeyIsAValidationError() {
        assertThrows(ValidationException.class, () -> store.execute(" ", new ChargeRequest("W-1", 1), this::debit));
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}

package com.lld.core;

import com.lld.core.exception.TransientException;
import com.lld.core.exception.ValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RetryPolicySelfCheck {

    private List<Duration> sleeps;
    private AtomicInteger attempts;

    @BeforeEach
    void setUp() {
        sleeps = new ArrayList<>();
        attempts = new AtomicInteger();
    }

    private RetryPolicy.Builder policy() {
        return RetryPolicy.builder()
                .maxAttempts(4)
                .baseDelay(Duration.ofMillis(100))
                .maxDelay(Duration.ofMillis(500))
                .sleeper(sleeps::add)
                .random(() -> 1.0); // take the ceiling so delays are deterministic
    }

    @Test
    void recoversAfterTwoTransientFailures() {
        String result = policy().build().execute(() -> {
            if (attempts.incrementAndGet() < 3) {
                throw new TransientException("gateway timeout");
            }
            return "PAID";
        });

        assertEquals("PAID", result);
        assertEquals(3, attempts.get());
        assertEquals(List.of(Duration.ofMillis(100), Duration.ofMillis(200)), sleeps);
    }

    @Test
    void businessErrorsAreNeverRetried() {
        assertThrows(ValidationException.class, () -> policy().build().execute(() -> {
            attempts.incrementAndGet();
            throw new ValidationException("card declined");
        }));

        assertEquals(1, attempts.get());
        assertTrue(sleeps.isEmpty());
    }

    @Test
    void givesUpAfterMaxAttemptsWithCappedBackoff() {
        assertThrows(TransientException.class, () -> policy().build().run(() -> {
            attempts.incrementAndGet();
            throw new TransientException("still down");
        }));

        assertEquals(4, attempts.get());
        assertEquals(List.of(Duration.ofMillis(100), Duration.ofMillis(200), Duration.ofMillis(400)), sleeps);
    }

    @Test
    void backoffIsCappedAtMaxDelay() {
        RetryPolicy retry = policy().build();

        assertEquals(Duration.ofMillis(500), retry.delayBeforeRetry(4)); // 800 capped to 500
        assertEquals(Duration.ofMillis(500), retry.delayBeforeRetry(40)); // no overflow on large attempts
    }

    @Test
    void jitterSpreadsDelaysBelowTheCeiling() {
        RetryPolicy retry = policy().random(() -> 0.25).build();

        assertEquals(Duration.ofMillis(50), retry.delayBeforeRetry(2)); // 0.25 * 200
    }

    @Test
    void customPredicateCanRetryOtherErrors() {
        String result = policy().retryIf(e -> e instanceof IllegalStateException).build().execute(() -> {
            if (attempts.incrementAndGet() == 1) {
                throw new IllegalStateException("lock wait timeout");
            }
            return "OK";
        });

        assertEquals("OK", result);
    }

    @Test
    void invalidConfigurationIsRejected() {
        assertThrows(ValidationException.class, () -> RetryPolicy.builder().maxAttempts(0).build());
        assertThrows(ValidationException.class,
                () -> RetryPolicy.builder().baseDelay(Duration.ofSeconds(5)).maxDelay(Duration.ofSeconds(1)).build());
    }
}

package com.lld.core;

import com.lld.core.exception.TransientException;
import com.lld.core.exception.ValidationException;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.DoubleSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Retry with exponential backoff and full jitter.
 *
 *   RetryPolicy gatewayRetry = RetryPolicy.builder()
 *           .maxAttempts(4).baseDelay(Duration.ofMillis(200)).maxDelay(Duration.ofSeconds(2))
 *           .build();                                   // retries TransientException only
 *   PaymentResult r = gatewayRetry.execute(() -> razorpay.charge(request));
 *
 * delay(attempt) = random(0 .. min(maxDelay, baseDelay * 2^(attempt-1)))
 * Full jitter spreads retries out so 10k clients that failed together don't retry together
 * (thundering herd).
 *
 * Talking points: retry only transient errors; cap attempts AND total delay; the callee must be
 * idempotent; add a circuit breaker when the dependency is down for long; in async flows the
 * retry becomes a delayed re-publish + DLQ after N attempts.
 * Sleeper and random are injectable, so tests assert exact delays without sleeping.
 */
public final class RetryPolicy {

    @FunctionalInterface
    public interface Sleeper {
        void sleep(Duration duration) throws InterruptedException;
    }

    private final int maxAttempts;
    private final Duration baseDelay;
    private final Duration maxDelay;
    private final Predicate<Throwable> retryable;
    private final Sleeper sleeper;
    private final DoubleSupplier random;

    private RetryPolicy(Builder builder) {
        this.maxAttempts = builder.maxAttempts;
        this.baseDelay = builder.baseDelay;
        this.maxDelay = builder.maxDelay;
        this.retryable = builder.retryable;
        this.sleeper = builder.sleeper;
        this.random = builder.random;
    }

    public static Builder builder() {
        return new Builder();
    }

    public <T> T execute(Supplier<T> action) {
        for (int attempt = 1; ; attempt++) {
            try {
                return action.get();
            } catch (RuntimeException e) {
                if (attempt >= maxAttempts || !retryable.test(e)) {
                    throw e;
                }
                pause(delayBeforeRetry(attempt));
            }
        }
    }

    public void run(Runnable action) {
        execute(() -> {
            action.run();
            return null;
        });
    }

    /** Delay after the given failed attempt (1-based). */
    Duration delayBeforeRetry(int failedAttempt) {
        long exponential = baseDelay.toMillis() << Math.min(failedAttempt - 1, 30);
        long ceiling = Math.min(exponential, maxDelay.toMillis());
        return Duration.ofMillis((long) (random.getAsDouble() * ceiling));
    }

    private void pause(Duration delay) {
        try {
            sleeper.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while backing off", e);
        }
    }

    public static final class Builder {
        private int maxAttempts = 3;
        private Duration baseDelay = Duration.ofMillis(100);
        private Duration maxDelay = Duration.ofSeconds(2);
        private Predicate<Throwable> retryable = e -> e instanceof TransientException;
        private Sleeper sleeper = d -> Thread.sleep(d.toMillis());
        private DoubleSupplier random = () -> ThreadLocalRandom.current().nextDouble();

        public Builder maxAttempts(int value) {
            maxAttempts = value;
            return this;
        }

        public Builder baseDelay(Duration value) {
            baseDelay = value;
            return this;
        }

        public Builder maxDelay(Duration value) {
            maxDelay = value;
            return this;
        }

        public Builder retryIf(Predicate<Throwable> value) {
            retryable = value;
            return this;
        }

        public Builder sleeper(Sleeper value) {
            sleeper = value;
            return this;
        }

        public Builder random(DoubleSupplier value) {
            random = value;
            return this;
        }

        public RetryPolicy build() {
            if (maxAttempts < 1) {
                throw new ValidationException("maxAttempts must be >= 1");
            }
            if (baseDelay.isNegative() || maxDelay.compareTo(baseDelay) < 0) {
                throw new ValidationException("need 0 <= baseDelay <= maxDelay");
            }
            return new RetryPolicy(this);
        }
    }
}

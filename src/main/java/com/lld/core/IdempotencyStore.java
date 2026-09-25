package com.lld.core;

import com.lld.core.exception.ConflictException;
import com.lld.core.exception.ValidationException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * "Retries deducting the balance multiple times" (actually asked at Tekion, Mar 2026) is fixed here.
 *
 *   Payment p = idempotency.execute(request.idempotencyKey(), request,
 *                                   () -> paymentService.charge(request));
 *
 * Guarantees for one Idempotency-Key:
 *  - the action runs at most once while it succeeds; later calls get the ORIGINAL result;
 *  - a duplicate arriving while the first is still running waits for it (coalesced);
 *  - the same key with a different request body is rejected (client bug, not a retry);
 *  - a failed attempt is forgotten, so the client can retry it;
 *  - entries expire after the TTL (checked lazily against the injected Clock).
 *
 * The request is compared with equals(), so pass a record or another value object.
 * HLD equivalent: idempotency table (key PK, request hash, status, response, created_at) written in
 * the SAME transaction as the side effect; or INSERT ... ON CONFLICT DO NOTHING as the gate.
 */
public class IdempotencyStore<R> {

    private record Entry<R>(Object request, CompletableFuture<R> result, Instant createdAt) {
    }

    private final Map<String, Entry<R>> entries = new ConcurrentHashMap<>();
    private final Clock clock;
    private final Duration ttl;

    public IdempotencyStore(Clock clock, Duration ttl) {
        this.clock = clock;
        this.ttl = ttl;
    }

    public R execute(String idempotencyKey, Object request, Supplier<R> action) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new ValidationException("Idempotency-Key is required");
        }
        while (true) {
            Entry<R> mine = new Entry<>(request, new CompletableFuture<>(), clock.instant());
            Entry<R> existing = entries.putIfAbsent(idempotencyKey, mine);
            if (existing == null) {
                return runFirst(idempotencyKey, mine, action);
            }
            if (isExpired(existing)) {
                if (entries.replace(idempotencyKey, existing, mine)) {
                    return runFirst(idempotencyKey, mine, action);
                }
                continue; // another thread replaced it first; re-evaluate
            }
            if (!Objects.equals(existing.request(), request)) {
                throw new ConflictException("Idempotency-Key " + idempotencyKey + " was already used with a different request");
            }
            return SingleFlight.await(existing.result()); // duplicate: same answer, no second side effect
        }
    }

    private R runFirst(String key, Entry<R> mine, Supplier<R> action) {
        try {
            R result = action.get();
            mine.result().complete(result);
            return result;
        } catch (RuntimeException | Error e) {
            entries.remove(key, mine); // forget failures so a retry re-executes
            mine.result().completeExceptionally(e);
            throw e;
        }
    }

    private boolean isExpired(Entry<R> entry) {
        // Never expire an in-flight entry: that would let a duplicate run concurrently.
        return entry.result().isDone() && !clock.instant().isBefore(entry.createdAt().plus(ttl));
    }
}

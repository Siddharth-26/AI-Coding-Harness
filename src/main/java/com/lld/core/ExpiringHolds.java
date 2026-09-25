package com.lld.core;

import com.lld.core.exception.ConflictException;
import com.lld.core.exception.NotFoundException;
import com.lld.core.exception.ValidationException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Temporary, all-or-nothing reservation of resources: seats while the user pays (BookMyShow),
 * a room during checkout (Booking.com), stock in a cart.
 *
 *   Hold hold = holds.hold(userId, List.of("S-A1", "S-A2"), seatId -> !bookedSeats.contains(seatId));
 *   ... user pays within 10 minutes ...
 *   holds.confirm(hold.id(), userId, h -> bookedSeats.addAll(h.resourceIds()));
 *
 * - hold(): every resource or none; conflicting holders get ConflictException.
 * - Expiry is lazy (an expired hold simply stops counting) + sweepExpired() for cleanup from a
 *   ScheduledExecutorService. Time comes from the injected Clock, so tests fast-forward.
 * - confirm(): runs YOUR commit (mark seats BOOKED, create Booking) under the same per-resource locks
 *   and only while the hold is still valid, so "hold expired at 10:00:00, payment landed at
 *   10:00:01" cannot sell the seat twice.
 * - Locks are per resource in sorted order (KeyedLockManager): no global lock, no deadlock.
 *
 * HLD equivalent: Redis SET seat:{show}:{seat} {holdId} NX PX 600000 per seat (or a Lua script for
 * all-or-nothing), OR a DB row with status=HELD, held_until, holder + conditional UPDATE; the final
 * booking is a DB transaction that re-checks the hold.
 */
public class ExpiringHolds {

    public record Hold(String id, String owner, Set<String> resourceIds, Instant expiresAt) {
        public boolean isActiveAt(Instant now) {
            return now.isBefore(expiresAt);
        }
    }

    private final Map<String, Hold> holdByResource = new ConcurrentHashMap<>();
    private final Map<String, Hold> holdsById = new ConcurrentHashMap<>();
    private final KeyedLockManager locks = new KeyedLockManager();
    private final Clock clock;
    private final Duration ttl;

    public ExpiringHolds(Clock clock, Duration ttl) {
        this.clock = clock;
        this.ttl = ttl;
    }

    public Hold hold(String owner, Collection<String> resourceIds, Predicate<String> isBookable) {
        Set<String> ids = Set.copyOf(resourceIds);
        if (ids.isEmpty()) {
            throw new ValidationException("nothing to hold");
        }
        return locks.withLocks(ids, () -> {
            Instant now = clock.instant();
            for (String id : ids) {
                if (!isBookable.test(id)) {
                    throw new ConflictException(id + " is not available");
                }
                Hold current = holdByResource.get(id);
                if (current != null && current.isActiveAt(now)) {
                    throw new ConflictException(id + " is held until " + current.expiresAt());
                }
            }
            Hold hold = new Hold(IdGenerator.next("HOLD"), owner, ids, now.plus(ttl));
            ids.forEach(id -> holdByResource.put(id, hold));
            holdsById.put(hold.id(), hold);
            return hold;
        });
    }

    public Hold hold(String owner, Collection<String> resourceIds) {
        return hold(owner, resourceIds, id -> true);
    }

    /** Runs commit while the hold is provably still valid and its resources are locked, then removes the hold. */
    public Hold confirm(String holdId, String owner, Consumer<Hold> commit) {
        Hold hold = ownedHold(holdId, owner);
        return locks.withLocks(hold.resourceIds(), () -> {
            Hold current = holdsById.get(holdId);
            if (current == null) {
                throw new ConflictException("hold " + holdId + " was released");
            }
            if (!current.isActiveAt(clock.instant())) {
                throw new ConflictException("hold " + holdId + " expired at " + current.expiresAt());
            }
            commit.accept(current);
            remove(current);
            return current;
        });
    }

    public void release(String holdId, String owner) {
        Hold hold = ownedHold(holdId, owner);
        locks.withLocks(hold.resourceIds(), () -> {
            remove(hold);
            return null;
        });
    }

    /** Call from a scheduler (e.g. every 30s). Correctness does not depend on it; memory does. */
    public int sweepExpired() {
        Instant now = clock.instant();
        int removed = 0;
        for (Hold hold : holdsById.values()) {
            if (!hold.isActiveAt(now)) {
                locks.withLocks(hold.resourceIds(), () -> {
                    remove(hold);
                    return null;
                });
                removed++;
            }
        }
        return removed;
    }

    public Optional<Hold> activeHoldOn(String resourceId) {
        Hold hold = holdByResource.get(resourceId);
        return hold != null && hold.isActiveAt(clock.instant()) ? Optional.of(hold) : Optional.empty();
    }

    private Hold ownedHold(String holdId, String owner) {
        Hold hold = holdsById.get(holdId);
        if (hold == null) {
            throw new NotFoundException("Hold", holdId);
        }
        if (!hold.owner().equals(owner)) {
            throw new ConflictException("hold " + holdId + " belongs to another user");
        }
        return hold;
    }

    private void remove(Hold hold) {
        holdsById.remove(hold.id(), hold);
        hold.resourceIds().forEach(id -> holdByResource.remove(id, hold)); // only if still pointing at THIS hold
    }
}

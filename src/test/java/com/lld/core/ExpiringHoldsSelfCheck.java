package com.lld.core;

import com.lld.core.ExpiringHolds.Hold;
import com.lld.core.exception.ConflictException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExpiringHoldsSelfCheck {

    private MutableClock clock;
    private ExpiringHolds holds;
    private Set<String> booked;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-09-24T18:00:00Z"));
        holds = new ExpiringHolds(clock, Duration.ofMinutes(10));
        booked = ConcurrentHashMap.newKeySet();
    }

    private Hold holdSeats(String user, String... seats) {
        return holds.hold(user, List.of(seats), seat -> !booked.contains(seat));
    }

    @Test
    void twentyUsersRacingForTheSameTwoSeatsProduceOneHold() throws InterruptedException {
        var outcomes = ConcurrentRunner.run(20, i -> holdSeats("user-" + i, "A1", "A2"));

        assertEquals(1, outcomes.stream().filter(ConcurrentRunner.Outcome::succeeded).count());
        outcomes.stream().filter(o -> !o.succeeded())
                .forEach(o -> assertInstanceOf(ConflictException.class, o.error()));
    }

    @Test
    void overlappingRequestsNeverHoldTheSameSeatTwice() throws InterruptedException {
        // even users want {A1,A2}, odd users want {A2,A3}: opposite overlap, must not deadlock
        var outcomes = ConcurrentRunner.run(20, i -> i % 2 == 0 ? holdSeats("u" + i, "A1", "A2") : holdSeats("u" + i, "A3", "A2"));

        long winners = outcomes.stream().filter(ConcurrentRunner.Outcome::succeeded).count();
        assertEquals(1, winners); // A2 is in every request, so exactly one can win
    }

    @Test
    void holdIsAllOrNothing() {
        holdSeats("riya", "A2");

        assertThrows(ConflictException.class, () -> holdSeats("amit", "A1", "A2"));
        assertTrue(holds.activeHoldOn("A1").isEmpty()); // A1 was not partially held
    }

    @Test
    void expiredHoldFreesTheSeats() {
        holdSeats("riya", "A1");
        clock.advance(Duration.ofMinutes(10));

        Hold amit = holdSeats("amit", "A1");

        assertEquals("amit", amit.owner());
    }

    @Test
    void confirmCommitsAndSeatIsNeverHeldAgain() {
        Hold hold = holdSeats("riya", "A1", "A2");

        holds.confirm(hold.id(), "riya", h -> booked.addAll(h.resourceIds()));

        assertEquals(Set.of("A1", "A2"), booked);
        assertThrows(ConflictException.class, () -> holdSeats("amit", "A1"));
    }

    @Test
    void paymentLandingAfterExpiryCannotConfirm() {
        Hold hold = holdSeats("riya", "A1");
        clock.advance(Duration.ofMinutes(10).plusSeconds(1));

        assertThrows(ConflictException.class, () -> holds.confirm(hold.id(), "riya", h -> booked.addAll(h.resourceIds())));
        assertTrue(booked.isEmpty());
    }

    @Test
    void onlyTheOwnerCanConfirmOrRelease() {
        Hold hold = holdSeats("riya", "A1");

        assertThrows(ConflictException.class, () -> holds.release(hold.id(), "amit"));
        holds.release(hold.id(), "riya");
        assertFalse(holds.activeHoldOn("A1").isPresent());
    }

    @Test
    void sweepRemovesOnlyExpiredHolds() {
        holdSeats("riya", "A1");
        clock.advance(Duration.ofMinutes(6));
        holdSeats("amit", "B1");
        clock.advance(Duration.ofMinutes(5)); // riya's expired, amit's has 1 minute left

        assertEquals(1, holds.sweepExpired());
        assertTrue(holds.activeHoldOn("B1").isPresent());
    }
}

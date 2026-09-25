package com.lld.core.realtime;

import com.lld.core.ConcurrentRunner;
import com.lld.core.EventBus;
import com.lld.core.MutableClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConnectionRegistrySelfCheck {

    private MutableClock clock;
    private List<PresenceChanged> presence;
    private ConnectionRegistry registry;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-09-24T09:00:00Z"));
        presence = new CopyOnWriteArrayList<>();
        EventBus events = new EventBus();
        events.subscribe(PresenceChanged.class, presence::add);
        registry = new ConnectionRegistry(events, clock, Duration.ofSeconds(30));
    }

    @Test
    void messageReachesEveryDeviceOfTheUser() {
        var phone = new InMemoryConnection("phone-1", "u-riya");
        var laptop = new InMemoryConnection("laptop-1", "u-riya");
        registry.connect(phone);
        registry.connect(laptop);

        assertEquals(2, registry.deliver("u-riya", "hi from #tekion-prep"));
        assertEquals(List.of("hi from #tekion-prep"), phone.received());
        assertEquals(List.of("hi from #tekion-prep"), laptop.received());
    }

    @Test
    void presenceFlipsOnlyOnFirstConnectAndLastDisconnect() {
        registry.connect(new InMemoryConnection("phone-1", "u-riya"));
        registry.connect(new InMemoryConnection("laptop-1", "u-riya"));
        registry.disconnect("phone-1");
        assertTrue(registry.isOnline("u-riya"));

        clock.advance(Duration.ofMinutes(5));
        registry.disconnect("laptop-1");

        assertEquals(List.of(true, false), presence.stream().map(PresenceChanged::online).toList());
        assertFalse(registry.isOnline("u-riya"));
        assertEquals(Optional.of(Instant.parse("2026-09-24T09:05:00Z")), registry.lastSeen("u-riya"));
    }

    @Test
    void fanOutReportsOfflineMembersForFallback() {
        registry.connect(new InMemoryConnection("c1", "u-amit"));
        registry.connect(new InMemoryConnection("c2", "u-neha"));

        Set<String> unreached = registry.deliverToAll(List.of("u-amit", "u-neha", "u-karan"), "standup in 5");

        assertEquals(Set.of("u-karan"), unreached);
    }

    @Test
    void deadSocketIsCleanedUpOnWrite() {
        var phone = new InMemoryConnection("phone-1", "u-riya");
        registry.connect(phone);
        phone.drop();

        assertEquals(0, registry.deliver("u-riya", "your order is out for delivery"));
        assertFalse(registry.isOnline("u-riya"));
    }

    @Test
    void connectionsThatStopHeartbeatingAreEvicted() {
        registry.connect(new InMemoryConnection("phone-1", "u-riya"));
        registry.connect(new InMemoryConnection("laptop-1", "u-riya"));

        clock.advance(Duration.ofSeconds(20));
        registry.heartbeat("laptop-1");
        clock.advance(Duration.ofSeconds(15)); // phone silent for 35s, laptop for 15s

        assertEquals(1, registry.evictStale());
        assertEquals(1, registry.deviceCount("u-riya"));
    }

    @Test
    void disconnectIsIdempotent() {
        registry.connect(new InMemoryConnection("phone-1", "u-riya"));
        registry.disconnect("phone-1");
        registry.disconnect("phone-1");

        assertEquals(2, presence.size()); // one ONLINE, one OFFLINE
    }

    @Test
    void fiftyDevicesConnectingAndLeavingAtOnceEmitExactlyOneOnlineAndOneOffline() throws InterruptedException {
        ConcurrentRunner.run(50, i -> {
            registry.connect(new InMemoryConnection("dev-" + i, "u-riya"));
            return null;
        });
        assertEquals(50, registry.deviceCount("u-riya"));

        ConcurrentRunner.run(50, i -> {
            registry.disconnect("dev-" + i);
            return null;
        });

        assertEquals(List.of(true, false), presence.stream().map(PresenceChanged::online).toList());
        assertFalse(registry.isOnline("u-riya"));
    }
}

package com.lld.core.realtime;

import com.lld.core.EventBus;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The WebSocket gateway's brain, in plain Java: who is connected on which devices, heartbeats,
 * presence (online / last seen), and message delivery with dead-socket cleanup.
 *
 *   registry.connect(new InMemoryConnection("phone-1", "u-riya"));   // socket opened
 *   registry.heartbeat("phone-1");                                  // client ping every ~15s
 *   int devices = registry.deliver("u-riya", message);              // 0 -> offline fallback
 *   registry.evictStale();                                          // scheduled every few seconds
 *
 * Concurrency: the per-user device map is updated with ConcurrentHashMap.compute, which is atomic
 * per user, so "first device -> ONLINE" and "last device -> OFFLINE" are detected exactly once
 * even when a user's devices connect and disconnect at the same instant.
 *
 * HLD equivalent: stateful gateway nodes hold sockets; Redis maps userId -> {gatewayNode}
 * with a TTL refreshed by heartbeats (that key IS presence); chat/notification services publish
 * to the user's node(s) via Redis pub/sub or a Kafka topic per node; last-seen persisted on
 * disconnect. Large channels: fan-out on read instead of pushing to every member.
 */
public class ConnectionRegistry implements PushGateway {

    private final Map<String, Map<String, Connection>> devicesByUser = new ConcurrentHashMap<>();
    private final Map<String, Connection> connectionsById = new ConcurrentHashMap<>();
    private final Map<String, Instant> lastHeartbeat = new ConcurrentHashMap<>();
    private final Map<String, Instant> lastSeenByUser = new ConcurrentHashMap<>();
    private final EventBus events;
    private final Clock clock;
    private final Duration heartbeatTimeout;

    public ConnectionRegistry(EventBus events, Clock clock, Duration heartbeatTimeout) {
        this.events = events;
        this.clock = clock;
        this.heartbeatTimeout = heartbeatTimeout;
    }

    public void connect(Connection connection) {
        Instant now = clock.instant();
        boolean[] cameOnline = {false};
        connectionsById.put(connection.id(), connection);
        lastHeartbeat.put(connection.id(), now);
        devicesByUser.compute(connection.userId(), (user, devices) -> {
            if (devices == null) {
                cameOnline[0] = true;
                devices = new ConcurrentHashMap<>();
            }
            devices.put(connection.id(), connection);
            return devices;
        });
        if (cameOnline[0]) {
            events.publish(new PresenceChanged(connection.userId(), true, now));
        }
    }

    /** Idempotent: closing an already-closed connection is a no-op. */
    public void disconnect(String connectionId) {
        Connection connection = connectionsById.remove(connectionId);
        if (connection == null) {
            return;
        }
        lastHeartbeat.remove(connectionId);
        Instant now = clock.instant();
        boolean[] wentOffline = {false};
        devicesByUser.computeIfPresent(connection.userId(), (user, devices) -> {
            devices.remove(connectionId);
            if (devices.isEmpty()) {
                wentOffline[0] = true;
                return null; // removes the user entry
            }
            return devices;
        });
        if (wentOffline[0]) {
            lastSeenByUser.put(connection.userId(), now);
            events.publish(new PresenceChanged(connection.userId(), false, now));
        }
    }

    public void heartbeat(String connectionId) {
        lastHeartbeat.computeIfPresent(connectionId, (id, previous) -> clock.instant());
    }

    /** Close connections that missed heartbeats (phone lost network without a clean close). */
    public int evictStale() {
        Instant cutoff = clock.instant().minus(heartbeatTimeout);
        List<String> stale = lastHeartbeat.entrySet().stream()
                .filter(e -> e.getValue().isBefore(cutoff))
                .map(Map.Entry::getKey)
                .toList();
        stale.forEach(this::disconnect);
        return stale.size();
    }

    @Override
    public int deliver(String userId, Object message) {
        Map<String, Connection> devices = devicesByUser.get(userId);
        if (devices == null) {
            return 0;
        }
        int reached = 0;
        for (Connection device : List.copyOf(devices.values())) {
            if (safeSend(device, message)) {
                reached++;
            } else {
                disconnect(device.id()); // dead socket found on write
            }
        }
        return reached;
    }

    @Override
    public Set<String> deliverToAll(Collection<String> userIds, Object message) {
        Set<String> unreached = new LinkedHashSet<>();
        for (String userId : userIds) {
            if (deliver(userId, message) == 0) {
                unreached.add(userId);
            }
        }
        return unreached;
    }

    @Override
    public boolean isOnline(String userId) {
        return devicesByUser.containsKey(userId);
    }

    /** "Last seen at" for offline users; empty while online or if never seen. */
    public Optional<Instant> lastSeen(String userId) {
        return isOnline(userId) ? Optional.empty() : Optional.ofNullable(lastSeenByUser.get(userId));
    }

    public int deviceCount(String userId) {
        Map<String, Connection> devices = devicesByUser.get(userId);
        return devices == null ? 0 : devices.size();
    }

    private static boolean safeSend(Connection connection, Object message) {
        try {
            return connection.send(message);
        } catch (RuntimeException e) {
            return false;
        }
    }
}

package com.lld.core.realtime;

import java.time.Instant;

/**
 * Published on the first device connecting (online=true) and the last device leaving (online=false).
 * Under a connect/disconnect race two events can arrive out of order: consumers should treat them
 * as hints and re-read PushGateway.isOnline().
 */
public record PresenceChanged(String userId, boolean online, Instant at) {
}

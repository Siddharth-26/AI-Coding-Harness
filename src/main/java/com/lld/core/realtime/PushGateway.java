package com.lld.core.realtime;

import java.util.Collection;
import java.util.Set;

/**
 * What domain services depend on to push to users in real time (chat message, order status,
 * delivery partner location). They never see sockets.
 */
public interface PushGateway {

    /** Push to every live device of the user. @return devices reached; 0 means the user is offline. */
    int deliver(String userId, Object message);

    /**
     * Fan-out to many users (channel message, group chat).
     * @return the users who were NOT reached -> caller falls back (unread inbox + mobile push).
     */
    Set<String> deliverToAll(Collection<String> userIds, Object message);

    boolean isOnline(String userId);
}

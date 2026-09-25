package com.lld.core.realtime;

/**
 * One open socket for one device (phone, laptop). In production this wraps a WebSocket session;
 * in core Java LLD it is an interface so the server side can be modelled without a framework.
 */
public interface Connection {

    String id();

    String userId();

    /** @return false if the socket is dead (closed, broken pipe). Must not throw. */
    boolean send(Object message);
}

package com.lld.core.realtime;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Stand-in for a WebSocket session in Demo and tests: records what it receives and can be
 * "dropped" to simulate a dead socket (phone went into a tunnel).
 */
public class InMemoryConnection implements Connection {

    private final String id;
    private final String userId;
    private final boolean printToConsole;
    private final List<Object> received = new CopyOnWriteArrayList<>();
    private volatile boolean alive = true;

    public InMemoryConnection(String id, String userId) {
        this(id, userId, false);
    }

    public InMemoryConnection(String id, String userId, boolean printToConsole) {
        this.id = id;
        this.userId = userId;
        this.printToConsole = printToConsole;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public String userId() {
        return userId;
    }

    @Override
    public boolean send(Object message) {
        if (!alive) {
            return false;
        }
        received.add(message);
        if (printToConsole) {
            System.out.println("  [ws -> " + userId + "/" + id + "] " + message);
        }
        return true;
    }

    public void drop() {
        alive = false;
    }

    public List<Object> received() {
        return List.copyOf(received);
    }
}

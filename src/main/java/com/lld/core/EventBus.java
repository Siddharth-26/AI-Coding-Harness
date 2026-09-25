package com.lld.core;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Observer / pub-sub in ~20 lines. Events are records; subscribers register by exact event class.
 *
 *   bus.subscribe(OrderPlaced.class, e -> notifier.send(e.userId(), "Order placed"));
 *   bus.publish(new OrderPlaced(orderId, userId));
 *
 * Delivery is synchronous and a failing subscriber never blocks the others.
 * HLD talking point: in production this becomes Kafka (durable, async, replayable).
 */
public class EventBus {

    private final Map<Class<?>, List<Consumer<?>>> subscribers = new ConcurrentHashMap<>();

    public <E> void subscribe(Class<E> eventType, Consumer<? super E> handler) {
        subscribers.computeIfAbsent(eventType, t -> new CopyOnWriteArrayList<>()).add(handler);
    }

    @SuppressWarnings("unchecked")
    public void publish(Object event) {
        for (Consumer<?> handler : subscribers.getOrDefault(event.getClass(), List.of())) {
            try {
                ((Consumer<Object>) handler).accept(event);
            } catch (RuntimeException e) {
                System.err.println("[EventBus] subscriber failed for " + event.getClass().getSimpleName() + ": " + e.getMessage());
            }
        }
    }
}

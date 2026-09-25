package com.lld.core;

import com.lld.core.exception.NotFoundException;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/**
 * Thread-safe in-memory store backed by ConcurrentHashMap.
 * Usage: Repository<Order> orders = new InMemoryRepository<>("Order");
 */
public class InMemoryRepository<T extends Identifiable> implements Repository<T> {

    private final String entityName;
    private final Map<String, T> store = new ConcurrentHashMap<>();

    public InMemoryRepository(String entityName) {
        this.entityName = entityName;
    }

    @Override
    public T save(T entity) {
        store.put(entity.getId(), entity);
        return entity;
    }

    @Override
    public Optional<T> findById(String id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public T getOrThrow(String id) {
        return findById(id).orElseThrow(() -> new NotFoundException(entityName, id));
    }

    @Override
    public List<T> findAll() {
        return List.copyOf(store.values());
    }

    @Override
    public List<T> findWhere(Predicate<? super T> filter) {
        return store.values().stream().filter(filter).toList();
    }

    @Override
    public void deleteById(String id) {
        store.remove(id);
    }
}

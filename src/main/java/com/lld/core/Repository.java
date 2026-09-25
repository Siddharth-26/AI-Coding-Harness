package com.lld.core;

import com.lld.core.exception.NotFoundException;

import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Storage abstraction. Services depend on THIS interface, never on InMemoryRepository
 * (Dependency Inversion: swap in a JDBC/Dynamo implementation without touching services).
 */
public interface Repository<T extends Identifiable> {

    T save(T entity);

    Optional<T> findById(String id);

    List<T> findAll();

    List<T> findWhere(Predicate<? super T> filter);

    void deleteById(String id);

    default T getOrThrow(String id) {
        return findById(id).orElseThrow(() -> new NotFoundException("Entity", id));
    }
}

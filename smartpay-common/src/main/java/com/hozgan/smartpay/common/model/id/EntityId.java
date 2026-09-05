package com.hozgan.smartpay.common.model.id;

/**
 * Marker interface for strongly-typed domain entity identifiers.
 *
 * @param <T> underlying identifier value type (e.g. UUID, String)
 */
public interface EntityId<T> {

    T value();

    default String asString() {
        return String.valueOf(value());
    }
}

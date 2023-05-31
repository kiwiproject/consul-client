package com.orbitz.consul.util.bookend;

import java.util.Optional;

import static java.util.Objects.isNull;

import java.util.HashMap;
import java.util.Map;

public class ConsulBookendContext {

    private Map<String, Object> data;

    ConsulBookendContext() {
        // package-private constructor
    }

    public void put(String key, Object value) {
        if (isNull(data)) {
            data = new HashMap<>();
        }

        data.put(key, value);
    }

    @SuppressWarnings("unchecked")
    public <T> Optional<T> get(String key, Class<T> klazz) {
        return Optional.ofNullable((T) data.get(key));
    }
}

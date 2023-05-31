package com.orbitz.consul.util.bookend;

import java.util.Optional;

import static com.google.common.base.Preconditions.checkState;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

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

    public <T> Optional<T> get(String key, Class<T> klazz) {
        if (isNull(data) || !data.containsKey(key)) {
            return Optional.empty();
        }

        var object = data.get(key);
        if (isNull(object)) {
            return Optional.empty();
        }

        checkState(klazz.isAssignableFrom(object.getClass()),
                "Data for key '%s' is not of type: %s", key, klazz.getName());

        T castObject = klazz.cast(object);
        return Optional.ofNullable(castObject);
    }
}

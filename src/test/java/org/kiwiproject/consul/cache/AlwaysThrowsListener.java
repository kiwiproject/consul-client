package org.kiwiproject.consul.cache;

import org.kiwiproject.consul.model.kv.Value;

import java.util.Map;

final class AlwaysThrowsListener implements ConsulCache.Listener<String, Value> {
    @Override
    public void notify(Map<String, Value> newValues) {
        throw new RuntimeException("This listener always throws an exception!");
    }
}

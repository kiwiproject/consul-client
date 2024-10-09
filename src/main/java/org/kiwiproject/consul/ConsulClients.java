package org.kiwiproject.consul;

import static java.util.Objects.nonNull;

import java.util.Map;

/**
 * A class containing shared utilities for Consul Client classes.
 * <p>
 * Note this is not part of the public API.
 */
class ConsulClients {

    private ConsulClients() {
        // utiltity class
    }

    static Map<String, String> dcQuery(String dc) {
        return nonNull(dc) ? Map.of("dc", dc) : Map.of();
    }
}

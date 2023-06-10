package org.kiwiproject.consul;

import org.kiwiproject.consul.config.ClientConfig;
import org.kiwiproject.consul.monitoring.ClientEventCallback;

/**
 * Allows tests to create KeyValueClient objects.
 */
public class KeyValueClientFactory {
    private KeyValueClientFactory() {
    }

    public static KeyValueClient create(KeyValueClient.Api api,
                                        ClientConfig config,
                                        ClientEventCallback eventCallback,
                                        Consul.NetworkTimeoutConfig networkTimeoutConfig) {
        return new KeyValueClient(api, config, eventCallback, networkTimeoutConfig);
    }
}

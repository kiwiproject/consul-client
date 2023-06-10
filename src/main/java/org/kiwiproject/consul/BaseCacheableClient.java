package org.kiwiproject.consul;

import org.kiwiproject.consul.config.ClientConfig;
import org.kiwiproject.consul.monitoring.ClientEventCallback;

abstract class BaseCacheableClient extends BaseClient {

    private final Consul.NetworkTimeoutConfig networkTimeoutConfig;

    protected BaseCacheableClient(String name, ClientConfig config, ClientEventCallback eventCallback,
                                  Consul.NetworkTimeoutConfig networkTimeoutConfig) {
        super(name, config, eventCallback);
        this.networkTimeoutConfig = networkTimeoutConfig;
    }

    public Consul.NetworkTimeoutConfig getNetworkTimeoutConfig() {
        return networkTimeoutConfig;
    }
}

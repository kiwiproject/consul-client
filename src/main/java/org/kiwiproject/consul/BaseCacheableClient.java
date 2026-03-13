package org.kiwiproject.consul;

import org.kiwiproject.consul.config.ClientConfig;
import org.kiwiproject.consul.monitoring.ClientEventCallback;

abstract class BaseCacheableClient extends BaseClient {

    private final NetworkTimeoutConfig networkTimeoutConfig;

    protected BaseCacheableClient(String name, ClientConfig config, ClientEventCallback eventCallback,
                                  NetworkTimeoutConfig networkTimeoutConfig) {
        super(name, config, eventCallback);
        this.networkTimeoutConfig = networkTimeoutConfig;
    }

    public NetworkTimeoutConfig getNetworkTimeoutConfig() {
        return networkTimeoutConfig;
    }
}

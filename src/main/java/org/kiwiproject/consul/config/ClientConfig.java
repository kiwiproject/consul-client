package org.kiwiproject.consul.config;

import static com.google.common.base.Preconditions.checkNotNull;

public class ClientConfig {

    private final CacheConfig cacheConfig;

    public ClientConfig() {
        this(CacheConfig.builder().build());
    }

    public ClientConfig(CacheConfig cacheConfig) {
        this.cacheConfig = checkNotNull(cacheConfig, "Cache configuration is mandatory");
    }

    public CacheConfig getCacheConfig() {
        return cacheConfig;
    }
}

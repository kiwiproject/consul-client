package org.kiwiproject.consul;

import org.kiwiproject.consul.config.ClientConfig;
import org.kiwiproject.consul.monitoring.ClientEventCallback;
import org.kiwiproject.consul.monitoring.ClientEventHandler;
import org.kiwiproject.consul.util.Http;

abstract class BaseClient {

    private final ClientConfig config;
    private final ClientEventHandler eventHandler;
    protected final Http http;

    protected BaseClient(String name, ClientConfig config, ClientEventCallback eventCallback) {
        this.config = config;
        this.eventHandler = new ClientEventHandler(name, eventCallback);
        this.http = new Http(eventHandler);
    }

    public ClientConfig getConfig() {
        return config;
    }

    public ClientEventHandler getEventHandler() { return eventHandler; }
}

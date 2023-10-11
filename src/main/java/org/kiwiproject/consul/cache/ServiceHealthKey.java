package org.kiwiproject.consul.cache;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.immutables.value.Value;
import org.kiwiproject.consul.model.health.ServiceHealth;

/**
 * Provides a unique key for a {@link ServiceHealth} entry in a {@link ServiceHealthCache}
 */
@Value.Immutable
@Value.Style(jakarta = true)
@JsonIgnoreProperties(ignoreUnknown = true)
public abstract class ServiceHealthKey {

    public abstract String getServiceId();

    public abstract String getHost();

    public abstract Integer getPort();

    public static ServiceHealthKey fromServiceHealth(ServiceHealth serviceHealth) {

        return ServiceHealthKey.of(
                serviceHealth.getService().getId()
                , serviceHealth.getNode().getAddress()
                , serviceHealth.getService().getPort()
        );
    }

    public static ServiceHealthKey of(String serviceId, String host, int port) {
        return ImmutableServiceHealthKey.builder()
                .serviceId(serviceId)
                .host(host)
                .port(port)
                .build();
    }
}

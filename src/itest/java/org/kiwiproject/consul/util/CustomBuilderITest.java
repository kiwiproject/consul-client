package org.kiwiproject.consul.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.kiwiproject.consul.BaseIntegrationTest;
import org.kiwiproject.consul.Consul;

import java.net.Proxy;

class CustomBuilderITest extends BaseIntegrationTest {

    @Test
    void shouldConnectWithCustomTimeouts() {
        var client = Consul.builder()
                .withHostAndPort(defaultClientHostAndPort)
                .withProxy(Proxy.NO_PROXY)
                .withConnectTimeoutMillis(10000)
                .withReadTimeoutMillis(3600000)
                .withWriteTimeoutMillis(900)
                .build();
        var agent = client.agentClient().getAgent();
        assertThat(agent).isNotNull();
    }

}

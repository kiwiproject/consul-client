package com.orbitz.consul.util;

import static org.assertj.core.api.Assertions.assertThat;

import com.orbitz.consul.BaseIntegrationTest;
import com.orbitz.consul.Consul;
import com.orbitz.consul.model.agent.Agent;
import org.junit.jupiter.api.Test;

import java.net.Proxy;
import java.net.UnknownHostException;

class CustomBuilderITest extends BaseIntegrationTest{

    @Test
    void shouldConnectWithCustomTimeouts() throws UnknownHostException {
        Consul client = Consul.builder()
                .withHostAndPort(defaultClientHostAndPort)
                .withProxy(Proxy.NO_PROXY)
                .withConnectTimeoutMillis(10000)
                .withReadTimeoutMillis(3600000)
                .withWriteTimeoutMillis(900)
                .build();
        Agent agent = client.agentClient().getAgent();
        assertThat(agent).isNotNull();
    }

}

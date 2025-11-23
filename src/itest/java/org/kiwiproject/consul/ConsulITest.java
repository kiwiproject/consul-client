package org.kiwiproject.consul;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ConsulITest extends BaseIntegrationTest {

    @Test
    void shouldDestroy() {
        client.destroy();

        assertThat(client.isDestroyed()).isTrue();
    }

    @Test
    void shouldBuildWithNoPing() {
        var consul = Consul.builder()
                .withHostAndPort(defaultClientHostAndPort)
                .withPing(false)
                .build();

        assertThat(consul).isNotNull();
    }

    @Test
    void shouldBuildWithPing() {
        var consul = Consul.builder()
                .withHostAndPort(defaultClientHostAndPort)
                .withPing(true)
                .build();

        assertThat(consul).isNotNull();
    }
}

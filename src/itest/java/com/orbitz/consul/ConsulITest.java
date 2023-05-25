package com.orbitz.consul;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ConsulITest extends BaseIntegrationTest {

    @Test
    void shouldDestroy() {
        client.destroy();

        assertThat(client.isDestroyed()).isTrue();
    }
}

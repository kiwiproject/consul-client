package org.kiwiproject.consul;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ConsulITest extends BaseIntegrationTest {

    @Test
    void shouldDestroy() {
        client.destroy();

        assertThat(client.isDestroyed()).isTrue();
    }
}

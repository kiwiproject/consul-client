package com.orbitz.consul;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ConsulITest extends BaseIntegrationTest {

    @Test
    void shouldDestroy() {
        client.destroy();

        assertTrue(client.isDestroyed());
    }
}

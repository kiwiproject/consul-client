package com.orbitz.consul;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ConsulITest extends BaseIntegrationTest {

    @Test
    public void shouldDestroy() {
        client.destroy();

        assertTrue(client.isDestroyed());
    }
}

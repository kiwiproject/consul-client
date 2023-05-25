package com.orbitz.consul.util.bookend;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ConsulBookendContextTest {

    private ConsulBookendContext context;

    @BeforeEach
    void setUp() {
        context = new ConsulBookendContext();
    }

    @Test
    void shouldPutAndGetTypedData() {
        context.put("elapsedMillis", 42L);
        context.put("name", "TheContext");

        var elapsedMillis = context.get("elapsedMillis", Long.class).orElseThrow();
        assertEquals(42L, elapsedMillis.longValue());

        var name = context.get("name", String.class).orElseThrow();
        assertEquals("TheContext", name);
    }
}

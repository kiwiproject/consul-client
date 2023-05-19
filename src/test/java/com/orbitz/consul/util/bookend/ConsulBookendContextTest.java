package com.orbitz.consul.util.bookend;

import static org.junit.Assert.assertEquals;

import org.junit.Before;
import org.junit.Test;

public class ConsulBookendContextTest {

    private ConsulBookendContext context;

    @Before
    public void setUp() {
        context = new ConsulBookendContext();
    }

    @Test
    public void shouldPutAndGetTypedData() {
        context.put("elapsedMillis", 42L);
        context.put("name", "TheContext");

        var elapsedMillis = context.get("elapsedMillis", Long.class).orElseThrow();
        assertEquals(42L, elapsedMillis.longValue());

        var name = context.get("name", String.class).orElseThrow();
        assertEquals("TheContext", name);
    }
}

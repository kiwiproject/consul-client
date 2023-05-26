package com.orbitz.consul.util.bookend;

import static org.assertj.core.api.Assertions.assertThat;
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
        assertThat(elapsedMillis.longValue()).isEqualTo(42L);

        var name = context.get("name", String.class).orElseThrow();
        assertThat(name).isEqualTo("TheContext");
    }
}

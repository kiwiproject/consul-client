package com.orbitz.consul.model.agent;

import static com.orbitz.consul.TestUtils.fixture;
import static org.assertj.core.api.Assertions.assertThat;

import com.orbitz.consul.util.Jackson;
import org.junit.jupiter.api.Test;

import java.io.IOException;

class DebugConfigTest {

    @Test
    void testDeserialization() throws IOException {
        var json = fixture("debug_config_test.json");
        var debugConfig = Jackson.MAPPER.readValue(json, DebugConfig.class);
        assertThat(debugConfig)
                .describedAs("DebugConfig should contains 167 items")
                .hasSize(167);
    }
}

package org.kiwiproject.consul.model.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.kiwiproject.consul.TestUtils.fixture;

import org.junit.jupiter.api.Test;
import org.kiwiproject.consul.util.Jackson;

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

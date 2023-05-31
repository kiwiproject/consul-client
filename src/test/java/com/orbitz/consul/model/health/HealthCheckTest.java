package com.orbitz.consul.model.health;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.List;

import org.junit.jupiter.api.Test;

class HealthCheckTest {

    @Test
    void serviceTagsAreNotNullWhenNotSpecified() {
        var healthCheck = ImmutableHealthCheck.builder()
                .name("name")
                .node("node")
                .checkId("id")
                .status("passing")
                .build();

        assertThat(healthCheck.getServiceTags()).isEqualTo(List.of());
    }

    @Test
    void serviceTagsCanBeAddedToHealthCheck() {
        var healthCheck = ImmutableHealthCheck.builder()
                .name("name")
                .node("node")
                .checkId("id")
                .status("passing")
                .addServiceTags("myTag")
                .build();

        assertThat(healthCheck.getServiceTags()).isEqualTo(List.of("myTag"));
    }
}

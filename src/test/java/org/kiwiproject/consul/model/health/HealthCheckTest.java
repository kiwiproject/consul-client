package org.kiwiproject.consul.model.health;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import java.util.List;

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

package com.orbitz.consul.model.health;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;

public class HealthCheckTest {

    @Test
    public void serviceTagsAreNotNullWhenNotSpecified() {
        HealthCheck check = ImmutableHealthCheck.builder()
                .name("name")
                .node("node")
                .checkId("id")
                .status("passing")
                .build();

        assertEquals(List.of(), check.getServiceTags());
    }

    @Test
    public void serviceTagsCanBeAddedToHealthCheck() {
        HealthCheck check = ImmutableHealthCheck.builder()
                .name("name")
                .node("node")
                .checkId("id")
                .status("passing")
                .addServiceTags("myTag")
                .build();

        assertEquals(List.of("myTag"), check.getServiceTags());
    }
}

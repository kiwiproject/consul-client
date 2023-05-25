package com.orbitz.consul.model.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.common.collect.Lists;

import org.junit.jupiter.api.Test;

import java.util.List;

class CheckTest {

    @Test
    void buildingCheckThrowsIfMissingMethod() {
        assertThrows(IllegalStateException.class, () -> {
            ImmutableCheck.builder()
                    .id("id")
                    .interval("10s")
                    .name("name")
                    .build();
        });
    }

    @Test
    void buildingCheckWithHttpThrowsIfMissingInterval() {
        assertThrows(IllegalStateException.class, () -> {
            ImmutableCheck.builder()
                    .id("id")
                    .http("http://foo.local:1337/health")
                    .name("name")
                    .build();
        });
    }

    @Test
    void buildingCheckWithGrpcThrowsIfMissingInterval() {
        assertThrows(IllegalStateException.class, () -> {
            ImmutableCheck.builder()
                    .id("id")
                    .grpc("localhost:12345")
                    .name("name")
                    .build();
        });
    }

    @Test
    void buildingCheckWithArgsThrowsIfMissingInterval() {
        assertThrows(IllegalStateException.class, () -> {
            ImmutableCheck.builder()
                    .id("id")
                    .args(List.of("/bin/echo \"hi\""))
                    .name("name")
                    .build();
        });
    }

    @Test
    void severalArgsCanBeAddedToCheck() {
        Check check = ImmutableCheck.builder()
                .id("id")
                .args(Lists.newArrayList("/bin/echo \"hi\"", "/bin/echo \"hello\""))
                .interval("1s")
                .name("name")
                .build();

        assertTrue(check.getArgs().isPresent(), "Args should be present in check");
        assertEquals(2, check.getArgs().get().size(), "Check should contain 2 args");
    }

    @Test
    void serviceTagsAreNotNullWhenNotSpecified() {
        Check check = ImmutableCheck.builder()
                .ttl("")
                .name("name")
                .id("id")
                .build();

        assertEquals(List.of(), check.getServiceTags());
    }

    @Test
    void serviceTagsCanBeAddedToCheck() {
        Check check = ImmutableCheck.builder()
                .ttl("")
                .name("name")
                .id("id")
                .addServiceTags("myTag")
                .build();

        assertEquals(List.of("myTag"), check.getServiceTags());
    }
}

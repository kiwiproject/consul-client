package org.kiwiproject.consul.model.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;

import com.google.common.collect.Lists;
import org.junit.jupiter.api.Test;

import java.util.List;

class CheckTest {

    @Test
    void buildingCheckThrowsIfMissingMethod() {
        // noinspection ResultOfMethodCallIgnored
        assertThatExceptionOfType(IllegalStateException.class).isThrownBy(() -> ImmutableCheck.builder()
                .id("id")
                .interval("10s")
                .name("name")
                .build());
    }

    @Test
    void buildingCheckWithHttpThrowsIfMissingInterval() {
        // noinspection ResultOfMethodCallIgnored
        assertThatExceptionOfType(IllegalStateException.class).isThrownBy(() -> ImmutableCheck.builder()
                .id("id")
                .http("http://foo.local:1337/health")
                .name("name")
                .build());
    }

    @Test
    void buildingCheckWithGrpcThrowsIfMissingInterval() {
        // noinspection ResultOfMethodCallIgnored
        assertThatExceptionOfType(IllegalStateException.class).isThrownBy(() -> ImmutableCheck.builder()
                .id("id")
                .grpc("localhost:12345")
                .name("name")
                .build());
    }

    @Test
    void buildingCheckWithArgsThrowsIfMissingInterval() {
        // noinspection ResultOfMethodCallIgnored
        assertThatExceptionOfType(IllegalStateException.class).isThrownBy(() -> ImmutableCheck.builder()
                .id("id")
                .args(List.of("/bin/echo \"hi\""))
                .name("name")
                .build());
    }

    @Test
    void severalArgsCanBeAddedToCheck() {
        var check = ImmutableCheck.builder()
                .id("id")
                .args(Lists.newArrayList("/bin/echo \"hi\"", "/bin/echo \"hello\""))
                .interval("1s")
                .name("name")
                .build();

        assertThat(check.getArgs()).describedAs("Args should be present in check").isPresent();
        assertThat(check.getArgs().orElseThrow()).as("Check should contain 2 args").hasSize(2);
    }

    @Test
    void serviceTagsAreNotNullWhenNotSpecified() {
        var check = ImmutableCheck.builder()
                .ttl("")
                .name("name")
                .id("id")
                .build();

        assertThat(check.getServiceTags()).isEqualTo(List.of());
    }

    @Test
    void serviceTagsCanBeAddedToCheck() {
        var check = ImmutableCheck.builder()
                .ttl("")
                .name("name")
                .id("id")
                .addServiceTags("myTag")
                .build();

        assertThat(check.getServiceTags()).isEqualTo(List.of("myTag"));
    }
}

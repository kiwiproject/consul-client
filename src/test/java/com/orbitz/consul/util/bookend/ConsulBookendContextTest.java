package com.orbitz.consul.util.bookend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

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

    @Test
    void shouldReturnEmpty_WhenNoDataHasBeenAdded() {
        assertThat(context.get("createdAt", Instant.class)).isEmpty();
    }

    @Test
    void shouldReturnEmpty_WhenThereIsNoElementWithSpecifiedKey() {
        context.put("elapsedMillis", 42L);

        assertThat(context.get("name", String.class)).isEmpty();
    }

    @Test
    void shouldReturnEmpty_WhenValueIsNull() {
        context.put("maybeNull", null);

        assertThat(context.get("maybeNull", String.class)).isEmpty();
    }

    @ParameterizedTest
    @MethodSource("badDataTypes")
    void shouldThrowIllegalStateException_WhenDataCannotBeCastToSpecifiedType(String key, Class<?> badType) {
        context.put("aLong", 42L);
        context.put("aString", "the value");
        context.put("anInstant", Instant.now());

        assertThatIllegalStateException()
                .isThrownBy(() -> context.get(key, badType))
                .withMessage("Data for key '%s' is not of type: %s", key, badType.getName());
    }

    static Stream<Arguments> badDataTypes() {
        return Stream.of(
            arguments("aLong", Double.class),
            arguments("aString", List.class),
            arguments("anInstant", LocalDate.class),
            arguments("aLong", Integer.class),
            arguments("aString", Map.class)
        );
    }
}

package org.kiwiproject.consul.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.commons.lang3.RandomStringUtils;
import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.Test;
import org.kiwiproject.consul.model.event.Event;
import org.kiwiproject.consul.model.event.ImmutableEvent;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

class Base64EncodingDeserializerTest {

    @Test
    void shouldDeserialize() throws IOException {
        var value = RandomStringUtils.secure().nextAlphabetic(12);
        var event = ImmutableEvent.builder()
                .id("1")
                .lTime(1L)
                .name("name")
                .version(1)
                .payload(Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8)))
                .build();

        String serializedEvent = Jackson.MAPPER.writeValueAsString(event);
        Event deserializedEvent = Jackson.MAPPER.readValue(serializedEvent, Event.class);

        assertThat(deserializedEvent.getPayload()).contains(value);
    }

    @Test
    void shouldDeserialize_WithNonAsciiCharacters() throws IOException {
        var value = "café naïve éàü";
        var encoded = Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
        var event = ImmutableEvent.builder()
                .id("1")
                .lTime(1L)
                .name("name")
                .version(1)
                .payload(encoded)
                .build();

        String serializedEvent = Jackson.MAPPER.writeValueAsString(event);
        Event deserializedEvent = Jackson.MAPPER.readValue(serializedEvent, Event.class);

        assertThat(deserializedEvent.getPayload()).contains(value);
    }

    @Test
    void shouldReturnEmpty_WhenPayloadIsNull() throws IOException {
        @Language("JSON") var json = """
                {
                  "ID": "1",
                  "LTime": 1,
                  "Name": "name",
                  "Version": 1,
                  "Payload": null
                }""";
        var event = Jackson.MAPPER.readValue(json, Event.class);
        assertThat(event.getPayload()).isEmpty();
    }

    @Test
    void shouldReturnEmpty_WhenPayloadIsEmptyString() throws IOException {
        @Language("JSON") var json = """
                {
                  "ID": "1",
                  "LTime": 1,
                  "Name": "name",
                  "Version": 1,
                  "Payload": ""
                }""";
        var event = Jackson.MAPPER.readValue(json, Event.class);
        assertThat(event.getPayload()).isEmpty();
    }
}

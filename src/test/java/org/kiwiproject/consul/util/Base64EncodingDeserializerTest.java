package org.kiwiproject.consul.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.Test;
import org.kiwiproject.consul.model.event.Event;
import org.kiwiproject.consul.model.event.ImmutableEvent;

import java.io.IOException;
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
                .payload(Base64.getEncoder().encodeToString(value.getBytes()))
                .build();

        String serializedEvent = Jackson.MAPPER.writeValueAsString(event);
        Event deserializedEvent = Jackson.MAPPER.readValue(serializedEvent, Event.class);

        assertThat(deserializedEvent.getPayload()).contains(value);
    }
}

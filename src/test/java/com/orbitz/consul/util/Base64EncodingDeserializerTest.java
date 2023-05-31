package com.orbitz.consul.util;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.io.BaseEncoding;
import com.orbitz.consul.model.event.Event;
import com.orbitz.consul.model.event.ImmutableEvent;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.Test;

import java.io.IOException;

class Base64EncodingDeserializerTest {

    @Test
    void shouldDeserialize() throws IOException {
        var value = RandomStringUtils.randomAlphabetic(12);
        var event = ImmutableEvent.builder()
                .id("1")
                .lTime(1L)
                .name("name")
                .version(1)
                .payload(BaseEncoding.base64().encode(value.getBytes()))
                .build();

        String serializedEvent = Jackson.MAPPER.writeValueAsString(event);
        Event deserializedEvent = Jackson.MAPPER.readValue(serializedEvent, Event.class);

        assertThat(deserializedEvent.getPayload()).contains(value);
    }
}
